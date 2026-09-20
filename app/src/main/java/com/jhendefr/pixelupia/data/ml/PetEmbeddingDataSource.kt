package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PetEmbeddingDataSource(
    private val context: Context
) {

    private var interpreter: Interpreter? = null
    private val inputSize = 224

    @Synchronized
    private fun getInterpreter(): Interpreter {
        if (interpreter == null) {
            val assetFileDescriptor = context.assets.openFd("mobilenet_v3_large.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        }
        return interpreter!!
    }

    suspend fun extractEmbedding(
        sourceBitmap: Bitmap,
        boundingBox: Rect? = null
    ): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            val cropBitmap = if (boundingBox != null) {
                cropSafe(sourceBitmap, boundingBox)
            } else {
                sourceBitmap
            }

            val resized = Bitmap.createScaledBitmap(cropBitmap, inputSize, inputSize, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val intValues = IntArray(inputSize * inputSize)
            resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

            for (pixel in intValues) {
                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)

                // Normalizacion MobileNetV3 [0.0, 1.0]
                inputBuffer.putFloat(r / 255.0f)
                inputBuffer.putFloat(g / 255.0f)
                inputBuffer.putFloat(b / 255.0f)
            }

            val currentInterpreter = getInterpreter()
            // Obtener dimension dinamica de salida (1280 floats)
            val outputDim = currentInterpreter.getOutputTensor(0).shape().let { shape ->
                if (shape.size > 1) shape[1] else shape[0]
            }

            val outputBuffer = Array(1) { FloatArray(outputDim) }
            synchronized(this@PetEmbeddingDataSource) {
                currentInterpreter.run(inputBuffer, outputBuffer)
            }

            val rawEmbedding = outputBuffer[0]
            val normalizedEmbedding = l2Normalize(rawEmbedding)
            Result.success(normalizedEmbedding)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cropSafe(bitmap: Bitmap, box: Rect): Bitmap {
        val padX = (box.width() * 0.10f).toInt()
        val padY = (box.height() * 0.10f).toInt()

        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = min(bitmap.width, box.right + padX)
        val bottom = min(bitmap.height, box.bottom + padY)
        val width = right - left
        val height = bottom - top

        return if (width > 10 && height > 10) {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } else {
            bitmap
        }
    }

    fun savePetCrop(bitmap: Bitmap, box: Rect, clusterId: Long): String {
        return try {
            val centerX = box.centerX()
            val centerY = box.centerY()
            val petSize = maxOf(box.width(), box.height())
            val cropSize = (petSize * 1.25f).toInt().coerceAtLeast(80)
            val halfSize = cropSize / 2

            val left = (centerX - halfSize).coerceIn(0, bitmap.width - 1)
            val top = (centerY - halfSize).coerceIn(0, bitmap.height - 1)
            val right = (centerX + halfSize).coerceIn(left + 1, bitmap.width)
            val bottom = (centerY + halfSize).coerceIn(top + 1, bitmap.height)

            val width = right - left
            val height = bottom - top
            val dim = minOf(width, height)
            if (dim <= 10) return ""

            val crop = Bitmap.createBitmap(bitmap, left, top, dim, dim)
            val scaledAvatar = Bitmap.createScaledBitmap(crop, 240, 240, true)

            val dir = java.io.File(context.filesDir, "cluster_crops")
            if (!dir.exists()) dir.mkdirs()
            val cropFile = java.io.File(dir, "cluster_${clusterId}.jpg")
            java.io.FileOutputStream(cropFile).use { out ->
                scaledAvatar.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            android.net.Uri.fromFile(cropFile).toString()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        fun l2Normalize(vector: FloatArray): FloatArray {
            var sumSquare = 0.0
            for (v in vector) {
                sumSquare += v * v
            }
            val norm = sqrt(sumSquare).toFloat().coerceAtLeast(1e-10f)
            val normalized = FloatArray(vector.size)
            for (i in vector.indices) {
                normalized[i] = vector[i] / norm
            }
            return normalized
        }
    }
}
