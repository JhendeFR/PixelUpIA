package com.example.pixelupia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

class EsrganProcessor(context: Context, useGPU: Boolean = false) {

    private val TILE_SIZE = 50
    private val SCALE = 4
    private val CHANNELS = 3
    private val TILE_INPUT_SIZE = TILE_SIZE * TILE_SIZE
    private val TILE_OUTPUT_SIZE = (TILE_SIZE * SCALE) * (TILE_SIZE * SCALE)
    private val BYTES_PER_FLOAT = 4 // FLOAT32

    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer
    private var interpreter: Interpreter

    init {
        val model = loadModel(context, "ESRGAN.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors())
            if (useGPU) {
                try {
                    val gpuDelegate = GpuDelegate()
                    addDelegate(gpuDelegate)
                    Log.i("EsrganProcessor", "GPU Delegate activado.")
                } catch (e: Exception) {
                    Log.e("EsrganProcessor", "Fallo al iniciar GPU Delegate, usando CPU.", e)
                }
            }
        }
        interpreter = Interpreter(model, options)

        inputBuffer = ByteBuffer.allocateDirect(1 * TILE_SIZE * TILE_SIZE * CHANNELS * BYTES_PER_FLOAT)
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(1 * TILE_OUTPUT_SIZE * CHANNELS * BYTES_PER_FLOAT)
        outputBuffer.order(ByteOrder.nativeOrder())
    }

    private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetName)
        return FileInputStream(afd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    //Callback de progreso
    suspend fun enhance(
        bitmap: Bitmap,
        onProgress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) { // Asegura que corra en un hilo de fondo
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val outputWidth = originalWidth * SCALE
        val outputHeight = originalHeight * SCALE

        val outBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(outBitmap)

        val numTilesX = (originalWidth + TILE_SIZE - 1) / TILE_SIZE
        val numTilesY = (originalHeight + TILE_SIZE - 1) / TILE_SIZE
        val totalTiles = numTilesX * numTilesY

        // Arrays temporales
        val tilePixels = IntArray(TILE_INPUT_SIZE)
        val outputTilePixels = IntArray(TILE_OUTPUT_SIZE)
        var tilesProcessed = 0

        for (y in 0 until numTilesY) {
            for (x in 0 until numTilesX) {
                // 1. Crear tile de entrada
                val tileInputBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
                val tileCanvas = Canvas(tileInputBitmap)
                val srcX = x * TILE_SIZE
                val srcY = y * TILE_SIZE
                val tileW = min(TILE_SIZE, originalWidth - srcX)
                val tileH = min(TILE_SIZE, originalHeight - srcY)
                val srcRect = Rect(srcX, srcY, srcX + tileW, srcY + tileH)
                val dstRect = Rect(0, 0, tileW, tileH)
                tileCanvas.drawBitmap(bitmap, srcRect, dstRect, null)

                // 2. Pre-procesar
                tileInputBitmap.getPixels(tilePixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)
                inputBuffer.rewind()
                for (pixel in tilePixels) {
                    inputBuffer.putFloat(((pixel shr 16) and 0xFF).toFloat()) // R
                    inputBuffer.putFloat(((pixel shr 8) and 0xFF).toFloat())  // G
                    inputBuffer.putFloat((pixel and 0xFF).toFloat())          // B
                }

                // 3. Ejecutar Inferencia
                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)

                // 4. Post-procesar
                outputBuffer.rewind()
                for (i in 0 until TILE_OUTPUT_SIZE) {
                    val r = outputBuffer.float.toInt().coerceIn(0, 255)
                    val g = outputBuffer.float.toInt().coerceIn(0, 255)
                    val b = outputBuffer.float.toInt().coerceIn(0, 255)
                    outputTilePixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }

                // 5. Crear Bitmap de salida
                val outputTileBitmap = Bitmap.createBitmap(
                    outputTilePixels, TILE_SIZE * SCALE, TILE_SIZE * SCALE, Bitmap.Config.ARGB_8888
                )

                // 6. Recortar
                val outputTileW = tileW * SCALE
                val outputTileH = tileH * SCALE
                val croppedOutputTile = Bitmap.createBitmap(outputTileBitmap, 0, 0, outputTileW, outputTileH)

                // 7. Dibujar
                val finalX = srcX * SCALE
                val finalY = srcY * SCALE
                outCanvas.drawBitmap(croppedOutputTile, finalX.toFloat(), finalY.toFloat(), null)

                // 8. Reportar progreso
                tilesProcessed++
                onProgress(tilesProcessed.toFloat() / totalTiles.toFloat())

                // Liberar memoria (importante para dispositivos móviles)
                tileInputBitmap.recycle()
                outputTileBitmap.recycle()
                croppedOutputTile.recycle()
            }
        }
        outBitmap
    }

    fun close() {
        interpreter.close()
    }
}