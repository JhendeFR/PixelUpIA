package com.jhendefr.pixelupia.data.ml

import android.content.Context
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
    private val imageLabelingDataSource: ImageLabelingDataSource
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

        val detectedObjects = suspendCancellableCoroutine<List<DetectedObject>> { continuation ->
            detector.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }

        // Obtener también etiquetas de ImageLabeler para enriquecer la clasificación
        val sceneLabels = imageLabelingDataSource.labelImage(uri)

        val items = detectedObjects.mapIndexed { index, obj ->
            val topLabel = obj.labels.maxByOrNull { it.confidence }
            val labelText: String
            val confidence: Float

            if (topLabel != null) {
                labelText = topLabel.text
                confidence = topLabel.confidence
            } else if (sceneLabels.isNotEmpty()) {
                val fallback = sceneLabels.getOrNull(index % sceneLabels.size)
                labelText = fallback?.text ?: "Objeto"
                confidence = fallback?.confidence ?: 0.75f
            } else {
                labelText = "Objeto"
                confidence = 0.70f
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

        // Si no se detectaron bounding boxes específicas pero ImageLabeler encontró objetos
        val finalItems = if (items.isEmpty() && sceneLabels.isNotEmpty()) {
            sceneLabels.mapIndexed { idx, item ->
                // Generar bounding box en área central visible
                val insetX = (imgWidth * 0.15f).toInt()
                val insetY = (imgHeight * 0.20f + (idx * 120)).toInt()
                val boxW = (imgWidth * 0.70f).toInt()
                val boxH = 100
                DetectedObjectBoxItem(
                    id = "label_$idx",
                    label = item.text,
                    confidence = item.confidence,
                    formatted = item.formatted,
                    box = Rect(insetX, insetY, insetX + boxW, (insetY + boxH).coerceAtMost(imgHeight))
                )
            }
        } else {
            items
        }

        ObjectDetectionResult(
            objects = finalItems,
            imageWidth = imgWidth,
            imageHeight = imgHeight
        )
    }
}
