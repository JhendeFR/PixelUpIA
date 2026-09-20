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

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Matrix

class FaceEmbeddingDataSource(
    private val context: Context
) {

    private var interpreter: Interpreter? = null
    private val inputSize = 160

    @Synchronized
    private fun getInterpreter(): Interpreter {
        if (interpreter == null) {
            val assetFileDescriptor = context.assets.openFd("facenet.tflite")
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
        boundingBox: Rect? = null,
        leftEye: PointF? = null,
        rightEye: PointF? = null
    ): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            val cropBitmap = if (boundingBox != null) {
                if (leftEye != null && rightEye != null) {
                    alignAndCropFace(sourceBitmap, boundingBox, leftEye, rightEye)
                } else {
                    cropSafe(sourceBitmap, boundingBox)
                }
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

                // Normalizacion estandar FaceNet [-1.0, 1.0] con 127.5 / 128.0
                inputBuffer.putFloat((r - 127.5f) / 128.0f)
                inputBuffer.putFloat((g - 127.5f) / 128.0f)
                inputBuffer.putFloat((b - 127.5f) / 128.0f)
            }

            val currentInterpreter = getInterpreter()
            // Resolver dinamicamente la dimension del vector de salida (512 floats)
            val outputDim = currentInterpreter.getOutputTensor(0).shape().let { shape ->
                if (shape.size > 1) shape[1] else shape[0]
            }

            val outputBuffer = Array(1) { FloatArray(outputDim) }
            synchronized(this@FaceEmbeddingDataSource) {
                currentInterpreter.run(inputBuffer, outputBuffer)
            }

            val rawEmbedding = outputBuffer[0]
            val normalizedEmbedding = l2Normalize(rawEmbedding)
            Result.success(normalizedEmbedding)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun alignAndCropFace(
        sourceBitmap: Bitmap,
        box: Rect,
        leftEye: PointF,
        rightEye: PointF
    ): Bitmap {
        return try {
            val eyeLeft = if (leftEye.x < rightEye.x) leftEye else rightEye
            val eyeRight = if (leftEye.x < rightEye.x) rightEye else leftEye
            val dx = (eyeRight.x - eyeLeft.x).toDouble()
            val dy = (eyeRight.y - eyeLeft.y).toDouble()
            val angle = Math.toDegrees(kotlin.math.atan2(dy, dx)).toFloat()

            val midX = (eyeLeft.x + eyeRight.x) / 2f
            val midY = (eyeLeft.y + eyeRight.y) / 2f

            val matrix = Matrix().apply {
                setRotate(-angle, midX, midY)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                0,
                0,
                sourceBitmap.width,
                sourceBitmap.height,
                matrix,
                true
            )

            val rectF = RectF(box)
            matrix.mapRect(rectF)
            val rotatedBox = Rect(
                rectF.left.toInt().coerceAtLeast(0),
                rectF.top.toInt().coerceAtLeast(0),
                rectF.right.toInt().coerceAtMost(rotatedBitmap.width),
                rectF.bottom.toInt().coerceAtMost(rotatedBitmap.height)
            )

            cropSafe(rotatedBitmap, rotatedBox)
        } catch (_: Exception) {
            cropSafe(sourceBitmap, box)
        }
    }

    private fun cropSafe(bitmap: Bitmap, box: Rect): Bitmap {
        // Aplicar 15% de padding para abarcar frente, menton y contornos faciales
        val padX = (box.width() * 0.15f).toInt()
        val padY = (box.height() * 0.15f).toInt()

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

    fun saveFaceCrop(bitmap: Bitmap, box: Rect, clusterId: Long): String {
        return try {
            val centerX = box.centerX()
            val centerY = box.centerY()
            val faceSize = maxOf(box.width(), box.height())
            // Margen generoso (1.45x) centrado en el rostro para que el avatar circular muestre la cara completa
            val cropSize = (faceSize * 1.45f).toInt().coerceAtLeast(60)
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

        fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
            if (v1.size != v2.size) return 0f
            var dot = 0f
            for (i in v1.indices) {
                dot += v1[i] * v2[i]
            }
            return dot.coerceIn(-1f, 1f)
        }

        fun vectorToString(vector: FloatArray): String {
            return vector.joinToString(",")
        }

        fun stringToVector(str: String): FloatArray {
            if (str.isBlank()) return FloatArray(0)
            val parts = str.split(",")
            val vector = FloatArray(parts.size)
            for (i in parts.indices) {
                vector[i] = parts[i].toFloatOrNull() ?: 0f
            }
            return vector
        }

        fun updateCentroidIncremental(centroid: FloatArray, sample: FloatArray, alpha: Float = 0.08f): FloatArray {
            val updated = FloatArray(centroid.size)
            for (i in centroid.indices) {
                updated[i] = centroid[i] + alpha * sample[i]
            }
            return l2Normalize(updated)
        }

        fun updateCentroidAddSample(centroid: FloatArray, sample: FloatArray, alpha: Float = 0.30f): FloatArray {
            val updated = FloatArray(centroid.size)
            for (i in centroid.indices) {
                updated[i] = centroid[i] + alpha * sample[i]
            }
            return l2Normalize(updated)
        }

        fun updateCentroidRemoveSample(centroid: FloatArray, sample: FloatArray, beta: Float = 0.20f): FloatArray {
            val updated = FloatArray(centroid.size)
            for (i in centroid.indices) {
                updated[i] = centroid[i] - beta * sample[i]
            }
            return l2Normalize(updated)
        }
    }
}
