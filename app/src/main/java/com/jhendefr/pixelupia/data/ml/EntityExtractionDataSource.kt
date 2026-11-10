package com.jhendefr.pixelupia.data.ml

import android.graphics.Rect
import com.google.mlkit.nl.entityextraction.*
import com.jhendefr.pixelupia.data.ocr.DetailedOcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ExtractedEntityItem(
    val id: String = "",
    val text: String,
    val type: String,
    val box: Rect? = null
)

data class EntityExtractionResult(
    val entities: List<ExtractedEntityItem>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

class EntityExtractionDataSource {

    private val entityExtractor: EntityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH).build()
    )

    suspend fun extractEntitiesWithBoxes(
        ocrResult: DetailedOcrResult
    ): EntityExtractionResult = withContext(Dispatchers.IO) {
        val rawEntities = extractEntities(ocrResult.fullText)
        val entitiesWithBoxes = rawEntities.mapIndexed { index, entity ->
            val matchingBlock = ocrResult.blocks.find { block ->
                block.text.contains(entity.text, ignoreCase = true) ||
                        entity.text.contains(block.text, ignoreCase = true)
            }
            val rect = matchingBlock?.let {
                Rect(it.box.left, it.box.top, it.box.right, it.box.bottom)
            }
            entity.copy(
                id = "entity_$index",
                box = rect
            )
        }
        EntityExtractionResult(
            entities = entitiesWithBoxes,
            imageWidth = ocrResult.imageWidth,
            imageHeight = ocrResult.imageHeight
        )
    }

    suspend fun extractEntities(text: String): List<ExtractedEntityItem> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext emptyList()

        val isDownloaded = suspendCancellableCoroutine<Boolean> { cont ->
            entityExtractor.isModelDownloaded
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(false) }
        }

        if (!isDownloaded) {
            val downloadOk = suspendCancellableCoroutine<Boolean> { cont ->
                entityExtractor.downloadModelIfNeeded()
                    .addOnSuccessListener { cont.resume(true) }
                    .addOnFailureListener { cont.resume(false) }
            }
            if (!downloadOk) {
                return@withContext extractEntitiesRegexFallback(text)
            }
        }

        val allowedEntityTypes = setOf(
            Entity.TYPE_URL,
            Entity.TYPE_PHONE,
            Entity.TYPE_EMAIL,
            Entity.TYPE_IBAN,
            Entity.TYPE_ADDRESS,
            Entity.TYPE_PAYMENT_CARD,
            Entity.TYPE_MONEY,
            Entity.TYPE_TRACKING_NUMBER
        )

        val params = EntityExtractionParams.Builder(text)
            .setEntityTypesFilter(allowedEntityTypes)
            .build()

        suspendCancellableCoroutine { continuation ->
            entityExtractor.annotate(params)
                .addOnSuccessListener { annotations ->
                    val mlKitEntities = annotations.flatMapIndexed { aIdx, annotation ->
                        val annotatedText = annotation.annotatedText.trim()
                        annotation.entities
                            .filter { it.type != Entity.TYPE_DATE_TIME }
                            .mapIndexedNotNull { eIdx, entity ->
                                val typeName = when (entity.type) {
                                    Entity.TYPE_URL -> "URL"
                                    Entity.TYPE_PHONE -> "Telefono"
                                    Entity.TYPE_EMAIL -> "Email"
                                    Entity.TYPE_IBAN -> "IBAN"
                                    Entity.TYPE_ADDRESS -> "Direccion"
                                    Entity.TYPE_PAYMENT_CARD -> "Tarjeta"
                                    Entity.TYPE_MONEY -> "Dinero"
                                    Entity.TYPE_TRACKING_NUMBER -> "Seguimiento"
                                    else -> null
                                }
                                if (typeName != null && annotatedText.length >= 3) {
                                    ExtractedEntityItem(
                                        id = "entity_${aIdx}_$eIdx",
                                        text = annotatedText,
                                        type = typeName
                                    )
                                } else null
                            }
                    }

                    // Fusión sin duplicados: combinar ML Kit con Regex especializado de Bolivia
                    val regexEntities = extractEntitiesRegexFallback(text)
                    val existingNormalized = mlKitEntities.map { it.text.trim().lowercase(java.util.Locale.ROOT) }.toSet()
                    val nonDuplicateRegex = regexEntities.filterNot { reg ->
                        val regText = reg.text.trim().lowercase(java.util.Locale.ROOT)
                        existingNormalized.any { it.contains(regText) || regText.contains(it) }
                    }

                    val merged = (mlKitEntities + nonDuplicateRegex).mapIndexed { idx, item ->
                        item.copy(id = "entity_$idx")
                    }
                    continuation.resume(merged)
                }
                .addOnFailureListener {
                    continuation.resume(extractEntitiesRegexFallback(text))
                }
        }
    }

    private fun extractEntitiesRegexFallback(text: String): List<ExtractedEntityItem> {
        val list = mutableListOf<ExtractedEntityItem>()

        // 1. Dinero (Bs, BOB, $us) adaptado a Bolivia
        val moneyRegex = Regex("""(?:Bs\.?|BOB|\${'$'}us)\s*\d+(?:[.,]\d+)*|\d+(?:[.,]\d+)*\s*(?:Bs\.?|BOB)""", RegexOption.IGNORE_CASE)
        moneyRegex.findAll(text).forEachIndexed { idx, match ->
            list.add(ExtractedEntityItem(id = "fallback_money_$idx", text = match.value.trim(), type = "Dinero"))
        }

        // 2. Seguimiento (Nro. de transacción / referencia / autorización)
        val trackingRegex = Regex("""(?:transacci[oó]n|referencia|autorizaci[oó]n)[:\sN°º]*([0-9]{6,22})""", RegexOption.IGNORE_CASE)
        trackingRegex.findAll(text).forEachIndexed { idx, match ->
            val refNumber = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value.trim()
            list.add(ExtractedEntityItem(id = "fallback_tracking_$idx", text = refNumber.trim(), type = "Seguimiento"))
        }

        // 3. Teléfono (Celulares de 8 dígitos 6/7xxxxxxx, fijos 4-64xxxxx y +591)
        val phoneRegex = Regex("""(?:\(\+?591\)|\+591)?\s*(?:\(?\d{1,2}\)?[-\s]?)?\b(?:[67]\d{7}|\d{2}[-\s]\d{5})\b""")
        phoneRegex.findAll(text).forEachIndexed { idx, match ->
            val phone = match.value.trim()
            if (phone.length >= 7) {
                list.add(ExtractedEntityItem(id = "fallback_phone_$idx", text = phone, type = "Telefono"))
            }
        }

        // 4. URLs (incluyendo dominios .bo sin http)
        val urlRegex = Regex("""(?:https?://|www\.)[^\s]+|\b[a-zA-Z0-9.-]+\.(?:bo|com|org|net)(?:/[^\s]*)?""")
        urlRegex.findAll(text).forEachIndexed { idx, match ->
            val url = match.value.trim()
            if (!url.contains("@") && url.length >= 4) {
                list.add(ExtractedEntityItem(id = "fallback_url_$idx", text = url, type = "URL"))
            }
        }

        // 5. Emails
        val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
        emailRegex.findAll(text).forEachIndexed { idx, match ->
            list.add(ExtractedEntityItem(id = "fallback_email_$idx", text = match.value.trim(), type = "Email"))
        }

        return list
    }
}
