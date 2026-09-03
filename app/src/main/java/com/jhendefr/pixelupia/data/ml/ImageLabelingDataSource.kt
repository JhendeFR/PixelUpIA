package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class DetectedLabelItem(
    val text: String,
    val confidence: Float
) {
    val confidencePercentage: Int get() = (confidence * 100).toInt()
    val formatted: String get() = "$text - $confidencePercentage%"
}

class ImageLabelingDataSource(private val context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build()
    )

    suspend fun labelImage(uri: Uri): List<DetectedLabelItem> = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)

        suspendCancellableCoroutine { continuation ->
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val result = labels.map { label ->
                        DetectedLabelItem(
                            text = label.text,
                            confidence = label.confidence
                        )
                    }
                    continuation.resume(result)
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }
    }
}
