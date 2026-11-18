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

class MobileClipEmbeddingDataSource(
    private val context: Context
) {

    private val tokenizer = ClipTokenizer(context)
    private val queryTranslator = LightweightQueryTranslator()
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

    suspend fun extractImageEmbedding(bitmap: Bitmap): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            val interpreter = getImageInterpreter()
            val inputShape = interpreter.getInputTensor(0).shape()
            val isNchw = inputShape.size == 4 && inputShape[1] == 3
            val targetSize = if (isNchw) inputShape[2] else inputShape[1]

            // Recorte cuadrado centrado
            val croppedBitmap = if (bitmap.width != bitmap.height) {
                val size = minOf(bitmap.width, bitmap.height)
                val x = (bitmap.width - size) / 2
                val y = (bitmap.height - size) / 2
                Bitmap.createBitmap(bitmap, x, y, size, size)
            } else {
                bitmap
            }

            val resized = Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true)
            val intValues = IntArray(targetSize * targetSize)
            resized.getPixels(intValues, 0, targetSize, 0, 0, targetSize, targetSize)

            val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * targetSize * targetSize * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Escalado [0.0f, 1.0f] esperado por el codificador visual FastViT de Apple MobileCLIP-S0
            if (isNchw) {
                val numPixels = targetSize * targetSize
                val rChannel = FloatArray(numPixels)
                val gChannel = FloatArray(numPixels)
                val bChannel = FloatArray(numPixels)

                for (i in intValues.indices) {
                    val pixel = intValues[i]
                    rChannel[i] = (pixel shr 16 and 0xFF) / 255.0f
                    gChannel[i] = (pixel shr 8 and 0xFF) / 255.0f
                    bChannel[i] = (pixel and 0xFF) / 255.0f
                }

                for (v in rChannel) inputBuffer.putFloat(v)
                for (v in gChannel) inputBuffer.putFloat(v)
                for (v in bChannel) inputBuffer.putFloat(v)
            } else {
                for (pixel in intValues) {
                    inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255.0f)
                    inputBuffer.putFloat((pixel shr 8 and 0xFF) / 255.0f)
                    inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
                }
            }

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
            val normalized = l2Normalize(rawEmbedding)
            android.util.Log.d("MobileCLIP", "Embedding de imagen generado exitosamente (dim: $outputDim)")
            Result.success(normalized)
        } catch (e: Exception) {
            android.util.Log.e("MobileCLIP", "Error extrayendo embedding de imagen: ${e.message}", e)
            Result.failure(e)
        }
    }

   //Embefing semantico
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
     * Genera embeddings normalizados para la consulta utilizando Prompt Ensembling.
     * Calcula los embeddings de los prompts generados, los promedia en un unico vector centroide
     * normalizado con L2, y devuelve dicho centroide junto con el prompt principal.
     */
    suspend fun extractQueryEmbeddings(query: String): List<FloatArray> = withContext(Dispatchers.Default) {
        val prompts = queryTranslator.translateAndBuildClipPrompts(query)
        android.util.Log.d("MobileCLIP", "Prompts generados para consulta '$query': $prompts")
        val embeddings = mutableListOf<FloatArray>()
        for (prompt in prompts) {
            val embResult = extractTextEmbedding(prompt)
            embResult.getOrNull()?.let { embeddings.add(it) }
        }
        if (embeddings.isEmpty()) return@withContext emptyList()

        val dim = embeddings.first().size
        val centroid = FloatArray(dim)
        for (emb in embeddings) {
            for (i in 0 until dim) {
                centroid[i] += emb[i]
            }
        }
        for (i in 0 until dim) {
            centroid[i] /= embeddings.size.toFloat()
        }
        val normalizedCentroid = l2Normalize(centroid)
        val primaryEmb = embeddings.first()
        listOf(normalizedCentroid, primaryEmb)
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
        "a photo of a dog, canine, or puppy" to "Perro",
        "a photo of a cat, feline, or kitten" to "Gato",
        "a photo of a person or portrait" to "Persona",
        "a photo of nature, trees, and landscape" to "Naturaleza",
        "a flat scan of a printed white paper page or contract" to "Documento",
        "a photo of an invoice, receipt, or payment bill" to "Factura / Recibo",
        "a photo of a party, concert, or celebration" to "Celebracion",
        "a photo of a beach, ocean, and sea" to "Playa",
        "a photo of mountains, hills, and scenic landscape" to "Montana",
        "a photo of clothing, dress, outfit, or fashion" to "Moda",
        "a photo of a building, house, or urban architecture" to "Arquitectura",
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
        threshold: Float = 0.22f
    ): List<Pair<String, Float>> {
        val concepts = getZeroShotConceptEmbeddings()
        val matches = mutableListOf<Pair<String, Float>>()
        for ((textVec, label) in concepts) {
            val sim = cosineSimilarity(imageEmbedding, textVec)
            if (sim >= threshold) {
                matches.add(Pair(label, sim))
            }
        }

        // Exclusion mutua entre "Perro" y "Gato" si ambos superan el umbral con diferencia >= 0.02f
        val dogMatch = matches.find { it.first == "Perro" }
        val catMatch = matches.find { it.first == "Gato" }
        if (dogMatch != null && catMatch != null) {
            val diff = kotlin.math.abs(dogMatch.second - catMatch.second)
            if (diff >= 0.02f) {
                if (dogMatch.second > catMatch.second) {
                    matches.remove(catMatch)
                } else {
                    matches.remove(dogMatch)
                }
            }
        }

        return matches.sortedByDescending { it.second }.take(topK)
    }

    private data class LatentSuggestionEntry(
        val clipPrompt: String,
        val englishQuery: String,
        val fallbackSpanish: String
    )

    private val latentSuggestionCatalog = listOf(
        LatentSuggestionEntry("a photo of a car, automobile, or vehicle", "car", "auto"),
        LatentSuggestionEntry("a photo of a red car", "red car", "auto rojo"),
        LatentSuggestionEntry("a photo of a motorcycle", "motorcycle", "motocicleta"),
        LatentSuggestionEntry("a photo of a bicycle", "bicycle", "bicicleta"),
        LatentSuggestionEntry("a photo of delicious food, dish, or meal", "delicious food", "comida deliciosa"),
        LatentSuggestionEntry("a photo of coffee or dessert", "coffee and dessert", "café y postre"),
        LatentSuggestionEntry("a photo of a dog, canine, or puppy", "dog", "perro"),
        LatentSuggestionEntry("a photo of a cat, feline, or kitten", "cat", "gato"),
        LatentSuggestionEntry("a photo of a plush toy or stuffed animal", "plush toy", "peluche"),
        LatentSuggestionEntry("a photo of a smiling person or portrait", "smiling portrait", "retrato sonriendo"),
        LatentSuggestionEntry("a photo of friends or family gathering", "friends gathering", "reunión de amigos"),
        LatentSuggestionEntry("a photo of nature, green trees, and forest", "trees and forest", "árboles y bosque"),
        LatentSuggestionEntry("a photo of green bushes, leaves, and plants", "green plants", "plantas verdes"),
        LatentSuggestionEntry("a photo of outdoor park or garden", "outdoor garden", "jardín exterior"),
        LatentSuggestionEntry("a flat scan of a printed white paper page or contract", "paper document", "documento impreso"),
        LatentSuggestionEntry("a photo of an invoice, receipt, or payment bill", "payment receipt", "comprobante de pago"),
        LatentSuggestionEntry("a mobile screenshot of a chat conversation", "chat screenshot", "captura de chat"),
        LatentSuggestionEntry("a photo of a qr code or barcode", "qr code", "código qr"),
        LatentSuggestionEntry("a photo of a party, concert, or celebration", "party celebration", "fiesta o celebración"),
        LatentSuggestionEntry("a photo of a beach, ocean, and sea", "sunny beach", "playa y mar"),
        LatentSuggestionEntry("a photo of mountains, hills, and scenic landscape", "mountain landscape", "paisaje de montañas"),
        LatentSuggestionEntry("a photo of water, river, or lake", "river or lake", "río o lago"),
        LatentSuggestionEntry("a photo of clothing, outfit, or fashion", "fashion outfit", "ropa y moda"),
        LatentSuggestionEntry("a photo of sneakers or shoes", "sneakers", "zapatillas"),
        LatentSuggestionEntry("a photo of a building, house, or urban architecture", "building architecture", "edificio o casa"),
        LatentSuggestionEntry("a photo of a rural brick house, cabin, or shed", "brick house or cabin", "casa de ladrillo o cabaña"),
        LatentSuggestionEntry("a photo of graffiti or street art painted on a wall", "wall graffiti", "grafiti en pared"),
        LatentSuggestionEntry("a photo of an indoor room or home furniture", "home interior", "interior de casa"),
        LatentSuggestionEntry("a photo of flowers, garden, and blooming plants", "garden flowers", "flores de jardín"),
        LatentSuggestionEntry("a photo of a computer, laptop, or smartphone screen", "computer screen", "pantalla de computadora"),
        LatentSuggestionEntry("a photo of the sky, clouds, sunset, or sunrise", "sunset sky", "cielo al atardecer"),
        LatentSuggestionEntry("a photo of a dark night scene or night lights", "night scene", "escena nocturna"),
        LatentSuggestionEntry("a photo of books, study notes, or notebook", "study notes", "apuntes o libro"),
        LatentSuggestionEntry("a photo of people playing sports or exercising", "sports activity", "actividad deportiva")
    )

    private val latentSuggestionEmbeddings = mutableListOf<Pair<FloatArray, LatentSuggestionEntry>>()

    @Synchronized
    private fun getLatentSuggestionEmbeddings(): List<Pair<FloatArray, LatentSuggestionEntry>> {
        if (latentSuggestionEmbeddings.isEmpty()) {
            for (entry in latentSuggestionCatalog) {
                val emb = extractTextEmbeddingSync(entry.clipPrompt)
                if (emb != null) {
                    latentSuggestionEmbeddings.add(Pair(emb, entry))
                }
            }
        }
        return latentSuggestionEmbeddings
    }

    /**
     * Genera sugerencias de busqueda en lenguaje natural desde el espacio latente 512D
     * y las traduce inmediatamente al espanol mediante ML Kit Translate on-device.
     */
    suspend fun suggestSearchQueries(
        imageEmbedding: FloatArray,
        topK: Int = 4,
        threshold: Float = 0.22f
    ): List<com.jhendefr.pixelupia.domain.model.LatentSearchSuggestion> = withContext(Dispatchers.Default) {
        val entries = getLatentSuggestionEmbeddings()
        val allScored = entries.map { (textVec, entry) ->
            Pair(entry, cosineSimilarity(imageEmbedding, textVec))
        }.toMutableList()

        val dogMatch = allScored.find { it.first.englishQuery == "dog" }
        val catMatch = allScored.find { it.first.englishQuery == "cat" }
        if (dogMatch != null && catMatch != null && kotlin.math.abs(dogMatch.second - catMatch.second) >= 0.02f) {
            if (dogMatch.second > catMatch.second) allScored.remove(catMatch) else allScored.remove(dogMatch)
        }

        val sorted = allScored.sortedByDescending { it.second }
        val topScore = sorted.firstOrNull()?.second ?: 0f

        // Solo conservar sugerencias que superen el umbral y esten cerca del topScore (sin rellenar con ruido)
        val selected = sorted
            .filter { it.second >= threshold && it.second >= (topScore - 0.04f) }
            .take(topK)
            .ifEmpty {
                // Si ninguna supera 0.22f, devolver solo la #1 si al menos supera 0.18f
                sorted.take(1).filter { it.second >= 0.18f }
            }

        android.util.Log.d(
            "MobileCLIP",
            "Top sugerencias latentes: " + sorted.take(5).joinToString { "${it.first.englishQuery}=${"%.3f".format(it.second)}" }
        )

        selected.map { (entry, _) ->
            val mlKitSpanish = queryTranslator.translateEnToEs(entry.englishQuery)
            val isTranslated = !mlKitSpanish.isNullOrBlank()
            com.jhendefr.pixelupia.domain.model.LatentSearchSuggestion(
                queryEs = if (isTranslated) mlKitSpanish!! else entry.fallbackSpanish,
                queryEn = entry.englishQuery,
                isTranslatedByMlKit = isTranslated
            )
        }.distinctBy { it.queryEs.lowercase(java.util.Locale.ROOT) }
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
