package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class DetectedObjectBoxItem(
    val id: String,
    val label: String,
    val confidence: Float,
    val formatted: String,
    val box: Rect
)

data class ObjectDetectionResult(
    val objects: List<DetectedObjectBoxItem>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

class ObjectDetectionDataSource(
    private val context: Context,
    private val imageLabelingDataSource: ImageLabelingDataSource,
    private val faceDetectionDataSource: FaceDetectionDataSource
) {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun detectObjects(uri: Uri): ObjectDetectionResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val imgWidth = image.width
        val imgHeight = image.height

        // 1. Deteccion espacial de objetos
        val detectedObjects = suspendCancellableCoroutine<List<DetectedObject>> { continuation ->
            detector.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }

        if (detectedObjects.isEmpty()) {
            return@withContext ObjectDetectionResult(
                objects = emptyList(),
                imageWidth = imgWidth,
                imageHeight = imgHeight
            )
        }

        // 2. Deteccion de rostros para clasificacion certera de personas
        val facesResult = runCatching { faceDetectionDataSource.detectFacesDetailed(uri) }.getOrNull()
        val faceBoxes = facesResult?.faces?.map { it.box } ?: emptyList()

        // 3. Cargar bitmap optimizado para clasificar recortes de objetos no identificados
        val sourceBitmap = runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            var sample = 1
            while (maxDim / sample > 2048) {
                sample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()

        val scaleX = if (sourceBitmap != null && imgWidth > 0) sourceBitmap.width.toFloat() / imgWidth.toFloat() else 1f
        val scaleY = if (sourceBitmap != null && imgHeight > 0) sourceBitmap.height.toFloat() / imgHeight.toFloat() else 1f

        val items = detectedObjects.mapIndexed { index, obj ->
            val topNativeLabel = obj.labels.maxByOrNull { it.confidence }
            var labelText: String
            var confidence: Float

            // Comprobar si el objeto corresponde a una persona (solapamiento con rostro detectado)
            val isPerson = faceBoxes.any { faceBox ->
                Rect.intersects(faceBox, obj.boundingBox) ||
                        obj.boundingBox.contains(faceBox.centerX(), faceBox.centerY())
            }

            if (isPerson) {
                labelText = "Persona"
                confidence = 0.95f
            } else if (topNativeLabel != null &&
                topNativeLabel.confidence >= 0.60f &&
                topNativeLabel.text.isNotBlank() &&
                !topNativeLabel.text.equals("Unknown", ignoreCase = true)
            ) {
                labelText = LabelTranslator.translate(topNativeLabel.text)
                confidence = topNativeLabel.confidence
            } else {
                // Si ML Kit detector no tiene etiqueta nativa, clasificar el recorte del objeto con ImageLabeler
                var foundCropLabel: String? = null
                var foundCropConfidence = 0.70f

                if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                    val cropLeft = (obj.boundingBox.left * scaleX).toInt().coerceIn(0, sourceBitmap.width - 1)
                    val cropTop = (obj.boundingBox.top * scaleY).toInt().coerceIn(0, sourceBitmap.height - 1)
                    val cropWidth = (obj.boundingBox.width() * scaleX).toInt().coerceIn(1, sourceBitmap.width - cropLeft)
                    val cropHeight = (obj.boundingBox.height() * scaleY).toInt().coerceIn(1, sourceBitmap.height - cropTop)

                    if (cropWidth > 25 && cropHeight > 25) {
                        try {
                            val cropped = Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                            val cropLabels = imageLabelingDataSource.labelBitmap(cropped)
                            cropped.recycle()

                            val bestLabel = cropLabels.maxByOrNull { it.confidence }
                            if (bestLabel != null && bestLabel.confidence >= 0.70f) {
                                foundCropLabel = bestLabel.text
                                foundCropConfidence = bestLabel.confidence
                            }
                        } catch (_: Exception) {
                            // En caso de fallo de memoria en el recorte, mantener etiqueta segura
                        }
                    }
                }

                if (foundCropLabel != null) {
                    labelText = foundCropLabel
                    confidence = foundCropConfidence
                } else {
                    labelText = "Objeto detectado"
                    confidence = 0.70f
                }
            }

            val percentage = (confidence * 100).toInt()
            val formatted = "$labelText - $percentage%"

            DetectedObjectBoxItem(
                id = "obj_$index",
                label = labelText,
                confidence = confidence,
                formatted = formatted,
                box = obj.boundingBox
            )
        }

        sourceBitmap?.recycle()

        ObjectDetectionResult(
            objects = items,
            imageWidth = imgWidth,
            imageHeight = imgHeight
        )
    }
}
