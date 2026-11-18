package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class DetectedFaceItem(
    val id: String,
    val box: Rect,
    val leftEye: android.graphics.PointF? = null,
    val rightEye: android.graphics.PointF? = null
)

data class FaceDetectionResult(
    val faces: List<DetectedFaceItem>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

class FaceDetectionDataSource(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .build()
    )

    suspend fun detectFaces(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        detectFacesDetailed(uri).faces.isNotEmpty()
    }

    suspend fun detectFacesDetailed(uri: Uri): FaceDetectionResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val imgWidth = image.width
        val imgHeight = image.height

        suspendCancellableCoroutine { continuation ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val minDimension = minOf(imgWidth, imgHeight)
                    val minBoxSize = maxOf(75, (minDimension * 0.06f).toInt())

                    val validFaces = faces.filter { face ->
                        val box = face.boundingBox

                        val hasValidSize = box.width() >= minBoxSize && box.height() >= minBoxSize

                        // Validacion anatomica
                        val hasLeftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE) != null
                        val hasRightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE) != null
                        val hasNose = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE) != null
                        val hasMouth = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM) != null ||
                                face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT) != null

                        val hasAnatomy = (hasLeftEye && hasRightEye) ||
                                (hasNose && (hasLeftEye || hasRightEye || hasMouth)) ||
                                (face.allLandmarks.size >= 4)

                        hasValidSize && hasAnatomy
                    }

                    val list = validFaces.mapIndexed { index, face ->
                        val leftEyePos = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
                        val rightEyePos = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position

                        DetectedFaceItem(
                            id = "face_$index",
                            box = face.boundingBox,
                            leftEye = leftEyePos,
                            rightEye = rightEyePos
                        )
                    }
                    continuation.resume(
                        FaceDetectionResult(
                            faces = list,
                            imageWidth = imgWidth,
                            imageHeight = imgHeight
                        )
                    )
                }
                .addOnFailureListener {
                    continuation.resume(
                        FaceDetectionResult(
                            faces = emptyList(),
                            imageWidth = imgWidth,
                            imageHeight = imgHeight
                        )
                    )
                }
        }
    }
}
