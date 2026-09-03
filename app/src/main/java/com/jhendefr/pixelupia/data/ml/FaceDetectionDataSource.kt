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
    val box: Rect
)

data class FaceDetectionResult(
    val faces: List<DetectedFaceItem>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

class FaceDetectionDataSource(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
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
                    val list = faces.mapIndexed { index, face ->
                        DetectedFaceItem(
                            id = "face_$index",
                            box = face.boundingBox
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
