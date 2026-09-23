package com.jhendefr.pixelupia.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Fuente de datos para inferencia de embeddings multimodales con MobileCLIP-S0 (Vision y Texto).
 * Genera representaciones vectoriales normalizadas L2 de 512 dimensiones para imagenes y consultas textuales.
 */
class MobileClipEmbeddingDataSource(
    private val context: Context
) {

    private val tokenizer = ClipTokenizer(context)
    private var imageInterpreter: Interpreter? = null
    private var textInterpreter: Interpreter? = null

    private val imageInputSize = 224

    @Synchronized
    private fun getImageInterpreter(): Interpreter {
        if (imageInterpreter == null) {
            imageInterpreter = loadModel("mobileclip_s0_image.tflite")
        }
        return imageInterpreter!!
    }

    @Synchronized
    private fun getTextInterpreter(): Interpreter {
        if (textInterpreter == null) {
            textInterpreter = loadModel("mobileclip_s0_text.tflite")
        }
        return textInterpreter!!
    }

    private fun loadModel(modelName: String): Interpreter {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        return Interpreter(modelBuffer, options)
    }

    /**
     * Extrae el embedding semantico de una imagen bitmap redimensionada a 224x224.
     * Soporta ordenes de tensores NHWC y NCHW de forma dinamica.
     */
    suspend fun extractImageEmbedding(bitmap: Bitmap): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            val resized = Bitmap.createScaledBitmap(bitmap, imageInputSize, imageInputSize, true)
            val intValues = IntArray(imageInputSize * imageInputSize)
            resized.getPixels(intValues, 0, imageInputSize, 0, 0, imageInputSize, imageInputSize)

            val interpreter = getImageInterpreter()
            val inputShape = interpreter.getInputTensor(0).shape()
            val isNchw = inputShape.size == 4 && inputShape[1] == 3

            // Buffer de entrada en Float32 (4 bytes por valor)
            val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * imageInputSize * imageInputSize * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Normalizacion de MobileCLIP (media y std de ImageNet/OpenAI CLIP)
            val meanR = 0.48145466f
            val meanG = 0.45782750f
            val meanB = 0.40821073f
            val stdR = 0.26862954f
            val stdG = 0.26130258f
            val stdB = 0.27577711f

            if (isNchw) {
                val numPixels = imageInputSize * imageInputSize
                val rChannel = FloatArray(numPixels)
                val gChannel = FloatArray(numPixels)
                val bChannel = FloatArray(numPixels)

                for (i in intValues.indices) {
                    val pixel = intValues[i]
                    val r = (pixel shr 16 and 0xFF) / 255.0f
                    val g = (pixel shr 8 and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f

                    rChannel[i] = (r - meanR) / stdR
                    gChannel[i] = (g - meanG) / stdG
                    bChannel[i] = (b - meanB) / stdB
                }

                for (v in rChannel) inputBuffer.putFloat(v)
                for (v in gChannel) inputBuffer.putFloat(v)
                for (v in bChannel) inputBuffer.putFloat(v)
            } else {
                for (pixel in intValues) {
                    val r = (pixel shr 16 and 0xFF) / 255.0f
                    val g = (pixel shr 8 and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f

                    val normR = (r - meanR) / stdR
                    val normG = (g - meanG) / stdG
                    val normB = (b - meanB) / stdB

                    inputBuffer.putFloat(normR)
                    inputBuffer.putFloat(normG)
                    inputBuffer.putFloat(normB)
                }
            }

            val outputDim = interpreter.getOutputTensor(0).shape().let { shape ->
                if (shape.size > 1) shape[1] else shape[0]
            }
            // Buffer de salida en Float32 (4 bytes por dimension)
            val outputBuffer = ByteBuffer.allocateDirect(outputDim * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            synchronized(this@MobileClipEmbeddingDataSource) {
                inputBuffer.rewind()
                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)
            }

            outputBuffer.rewind()
            val rawEmbedding = FloatArray(outputDim) {
                outputBuffer.float
            }
            val normalized = l2Normalize(rawEmbedding)
            android.util.Log.d("MobileCLIP", "Embedding de imagen generado exitosamente (dim: $outputDim)")
            Result.success(normalized)
        } catch (e: Exception) {
            android.util.Log.e("MobileCLIP", "Error extrayendo embedding de imagen: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Extrae el embedding semantico de una consulta textual utilizando el tokenizer BPE y el Text Transformer.
     */
    suspend fun extractTextEmbedding(query: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            val tokenIds = tokenizer.tokenize(query)

            val inputBuffer = ByteBuffer.allocateDirect(1 * ClipTokenizer.CONTEXT_LENGTH * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            for (token in tokenIds) {
                inputBuffer.putInt(token)
            }

            val interpreter = getTextInterpreter()
            val outputDim = interpreter.getOutputTensor(0).shape().let { shape ->
                if (shape.size > 1) shape[1] else shape[0]
            }
            // Salida de Text Transformer en Float32 (4 bytes por dimension)
            val outputBuffer = ByteBuffer.allocateDirect(outputDim * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            synchronized(this@MobileClipEmbeddingDataSource) {
                inputBuffer.rewind()
                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)
            }

            outputBuffer.rewind()
            val rawEmbedding = FloatArray(outputDim) {
                outputBuffer.float
            }
            val normalized = l2Normalize(rawEmbedding)
            android.util.Log.d("MobileCLIP", "Embedding de texto generado exitosamente (dim: $outputDim)")
            Result.success(normalized)
        } catch (e: Exception) {
            android.util.Log.e("MobileCLIP", "Error extrayendo embedding de texto: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Genera embeddings normalizados para todas las variantes linguisticas y sintacticas de una consulta.
     * Utiliza QuerySemanticExpander para traducir y estructurar la consulta en ingles optimo para MobileCLIP.
     */
    suspend fun extractQueryEmbeddings(query: String): List<FloatArray> = withContext(Dispatchers.Default) {
        val prompts = QuerySemanticExpander.generateClipPrompts(query)
        android.util.Log.d("MobileCLIP", "Prompts generados para consulta '$query': $prompts")
        val embeddings = mutableListOf<FloatArray>()
        for (prompt in prompts) {
            val embResult = extractTextEmbedding(prompt)
            embResult.getOrNull()?.let { embeddings.add(it) }
        }
        embeddings
    }

    private fun extractTextEmbeddingSync(query: String): FloatArray? {
        return try {
            val tokenIds = tokenizer.tokenize(query)
            val inputBuffer = ByteBuffer.allocateDirect(1 * ClipTokenizer.CONTEXT_LENGTH * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            for (token in tokenIds) {
                inputBuffer.putInt(token)
            }
            val interpreter = getTextInterpreter()
            val outputDim = interpreter.getOutputTensor(0).shape().let { shape ->
                if (shape.size > 1) shape[1] else shape[0]
            }
            val outputBuffer = ByteBuffer.allocateDirect(outputDim * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            synchronized(this@MobileClipEmbeddingDataSource) {
                inputBuffer.rewind()
                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)
            }
            outputBuffer.rewind()
            val rawEmbedding = FloatArray(outputDim) {
                outputBuffer.float
            }
            l2Normalize(rawEmbedding)
        } catch (e: Exception) {
            android.util.Log.e("MobileCLIP", "Error extrayendo embedding de texto sync: ${e.message}", e)
            null
        }
    }

    // Catalogo canonico para clasificacion Zero-Shot de imagenes optimizado en ingles para MobileCLIP
    private val canonicalZeroShotConcepts = listOf(
        "a photo of a car, automobile, or vehicle" to "Vehiculo",
        "a photo of a red car" to "Auto Rojo",
        "a photo of delicious food, dish, or meal" to "Comida",
        "a photo of a dog, puppy, or pet canine" to "Perro",
        "a photo of a cat, kitten, or pet feline" to "Gato",
        "a photo of a person or portrait" to "Persona",
        "a photo of nature, trees, and landscape" to "Naturaleza",
        "a photo of a document, paper, or text contract" to "Documento",
        "a photo of an invoice, receipt, or payment bill" to "Factura / Recibo",
        "a photo of a party, concert, or celebration" to "Celebracion",
        "a photo of a beach, ocean, and sea" to "Playa",
        "a photo of mountains, hills, and scenic landscape" to "Montana",
        "a photo of clothing, dress, outfit, or fashion" to "Moda",
        "a photo of a building, architecture, or city street" to "Arquitectura",
        "a photo of flowers, garden, and blooming plants" to "Flores",
        "a photo of a computer, laptop, screen, or smartphone" to "Tecnologia",
        "a photo of the sky, clouds, sunset, or sunrise" to "Cielo / Atardecer"
    )

    private val zeroShotConceptEmbeddings = mutableListOf<Pair<FloatArray, String>>()

    @Synchronized
    private fun getZeroShotConceptEmbeddings(): List<Pair<FloatArray, String>> {
        if (zeroShotConceptEmbeddings.isEmpty()) {
            for ((query, label) in canonicalZeroShotConcepts) {
                val emb = extractTextEmbeddingSync(query)
                if (emb != null) {
                    zeroShotConceptEmbeddings.add(Pair(emb, label))
                }
            }
        }
        return zeroShotConceptEmbeddings
    }

    /**
     * Realiza clasificacion Zero-Shot sobre un embedding de imagen contra el catalogo de conceptos canonicos.
     */
    fun predictZeroShotLabels(
        imageEmbedding: FloatArray,
        topK: Int = 4,
        threshold: Float = 0.16f
    ): List<Pair<String, Float>> {
        val concepts = getZeroShotConceptEmbeddings()
        val matches = mutableListOf<Pair<String, Float>>()
        for ((textVec, label) in concepts) {
            val sim = cosineSimilarity(imageEmbedding, textVec)
            if (sim >= threshold) {
                matches.add(Pair(label, sim))
            }
        }
        return matches.sortedByDescending { it.second }.take(topK)
    }

    companion object {
        /**
         * Aplica normalizacion L2 euclidiana a un vector.
         */
        fun l2Normalize(vector: FloatArray): FloatArray {
            var sumSq = 0.0f
            for (v in vector) {
                sumSq += v * v
            }
            val norm = sqrt(sumSq)
            if (norm < 1e-12f) return vector
            val result = FloatArray(vector.size)
            for (i in vector.indices) {
                result[i] = vector[i] / norm
            }
            return result
        }

        /**
         * Calcula la similitud coseno entre dos vectores normalizados L2 (producto punto).
         */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
            }
            return dot
        }

        /**
         * Serializa un vector FloatArray a cadena Base64 compacta y rapida sin perdida de precision.
         */
        fun embeddingToBase64(embedding: FloatArray): String {
            val byteBuffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in embedding) {
                byteBuffer.putFloat(f)
            }
            return Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        }

        /**
         * Deserializa una cadena Base64 a un vector FloatArray.
         */
        fun base64ToEmbedding(base64: String): FloatArray {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                floats[i] = byteBuffer.getFloat()
            }
            return floats
        }
    }
}
