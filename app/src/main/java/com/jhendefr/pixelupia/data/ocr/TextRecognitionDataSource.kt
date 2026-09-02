package com.jhendefr.pixelupia.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BoundingBoxRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class TextBlockItem(
    val id: String,
    val text: String,
    val box: BoundingBoxRect,
    val lines: List<String> = emptyList()
)

data class DetailedOcrResult(
    val fullText: String,
    val blocks: List<TextBlockItem>,
    val imageWidth: Int,
    val imageHeight: Int
)

data class OcrResult(
    val text: String,
    val boundingBoxesJson: String
)

class TextRecognitionDataSource(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)

        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text.trim()
                    val boxesBuilder = StringBuilder("[")

                    visionText.textBlocks.forEachIndexed { index, block ->
                        val box = block.boundingBox
                        if (box != null) {
                            if (index > 0) boxesBuilder.append(",")
                            boxesBuilder.append("{\"text\":\"${block.text.replace("\"", "\\\"").replace("\n", " ")}\",")
                            boxesBuilder.append("\"left\":${box.left},\"top\":${box.top},\"right\":${box.right},\"bottom\":${box.bottom}}")
                        }
                    }
                    boxesBuilder.append("]")

                    continuation.resume(
                        OcrResult(
                            text = fullText,
                            boundingBoxesJson = boxesBuilder.toString()
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    suspend fun recognizeTextDetailed(uri: Uri): DetailedOcrResult = withContext(Dispatchers.IO) {
        // Obtener dimensiones de la imagen original
        var width = 0
        var height = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                width = options.outWidth
                height = options.outHeight
            }
        } catch (e: Exception) {
            width = 1080
            height = 1920
        }

        val image = InputImage.fromFilePath(context, uri)

        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text.trim()
                    val blocks = mutableListOf<TextBlockItem>()

                    visionText.textBlocks.forEachIndexed { blockIndex, block ->
                        val box = block.boundingBox
                        if (box != null && block.text.isNotBlank()) {
                            val lineTexts = block.lines.map { it.text.trim() }.filter { it.isNotEmpty() }
                            blocks.add(
                                TextBlockItem(
                                    id = "block_$blockIndex",
                                    text = block.text.trim(),
                                    box = BoundingBoxRect(
                                        left = box.left,
                                        top = box.top,
                                        right = box.right,
                                        bottom = box.bottom
                                    ),
                                    lines = lineTexts
                                )
                            )
                        }
                    }

                    continuation.resume(
                        DetailedOcrResult(
                            fullText = fullText,
                            blocks = blocks,
                            imageWidth = if (width > 0) width else image.width,
                            imageHeight = if (height > 0) height else image.height
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }
}
