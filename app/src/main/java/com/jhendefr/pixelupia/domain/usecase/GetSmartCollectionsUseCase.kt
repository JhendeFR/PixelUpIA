package com.jhendefr.pixelupia.domain.usecase

import android.net.Uri
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

enum class SmartCollectionType {
    SOURCE_APP,
    VISUAL_CATEGORY
}

data class SmartCollectionItem(
    val id: String,
    val title: String,
    val count: Int,
    val coverUri: Uri?,
    val type: SmartCollectionType,
    val photos: List<Photo>
)

data class SmartCollectionsResult(
    val appCollections: List<SmartCollectionItem> = emptyList(),
    val visualCollections: List<SmartCollectionItem> = emptyList()
)

class GetSmartCollectionsUseCase(
    private val smartGalleryDao: SmartGalleryDao
) {

    operator fun invoke(allPhotos: List<Photo>): Flow<SmartCollectionsResult> {
        val photoMap = allPhotos.associateBy { it.id }

        return smartGalleryDao.getAllSmartPhotos().map { entities ->
            withContext(Dispatchers.Default) {
                // 1. Colecciones por App de Origen
                // Agrupar fotos que tengan un sourceApp identificado
                val appMap = mutableMapOf<String, MutableList<Photo>>()

                // Primero desde allPhotos (incluye capturas de MediaStore)
                for (photo in allPhotos) {
                    val app = photo.sourceApp
                    if (!app.isNullOrBlank() && !app.equals("Desconocido", ignoreCase = true)) {
                        appMap.getOrOrPut(app) { mutableListOf() }.add(photo)
                    }
                }

                // Asegurar fotos indexadas en Room que tengan sourceApp
                for (entity in entities) {
                    val app = entity.sourceApp
                    if (!app.isNullOrBlank() && !app.equals("Desconocido", ignoreCase = true)) {
                        val photo = photoMap[entity.photoId] ?: Photo(
                            id = entity.photoId,
                            uri = Uri.parse(entity.uri),
                            name = entity.name,
                            dateTaken = entity.dateAdded,
                            size = 0L,
                            folderName = entity.folderName,
                            mimeType = "image/*",
                            sourceApp = entity.sourceApp
                        )
                        val list = appMap.getOrOrPut(app) { mutableListOf() }
                        if (list.none { it.id == photo.id }) {
                            list.add(photo)
                        }
                    }
                }

                val appCollections = appMap.map { (appName, photos) ->
                    val sortedPhotos = photos.sortedByDescending { it.dateTaken }
                    SmartCollectionItem(
                        id = "app_$appName",
                        title = appName,
                        count = sortedPhotos.size,
                        coverUri = sortedPhotos.firstOrNull()?.uri,
                        type = SmartCollectionType.SOURCE_APP,
                        photos = sortedPhotos
                    )
                }.sortedByDescending { it.count }

                // 2. Colecciones por Categorias Visuales
                val visualCategoryMap = mutableMapOf<String, MutableList<Photo>>()

                for (entity in entities) {
                    val photo = photoMap[entity.photoId] ?: Photo(
                        id = entity.photoId,
                        uri = Uri.parse(entity.uri),
                        name = entity.name,
                        dateTaken = entity.dateAdded,
                        size = 0L,
                        folderName = entity.folderName,
                        mimeType = "image/*",
                        sourceApp = entity.sourceApp
                    )

                    // Categoria: Personas / Rostros
                    if (entity.hasFaces || entity.labels.any { it.startsWith("Persona", ignoreCase = true) }) {
                        val list = visualCategoryMap.getOrOrPut("Personas") { mutableListOf() }
                        if (list.none { it.id == photo.id }) {
                            list.add(photo)
                        }
                    }

                    // Categoria: Codigos QR y Barras
                    if (entity.barcodes.isNotEmpty()) {
                        val list = visualCategoryMap.getOrOrPut("Codigos QR") { mutableListOf() }
                        if (list.none { it.id == photo.id }) {
                            list.add(photo)
                        }
                    }

                    // Categoria: Pagos y Facturas
                    val hasPaymentEntities = entity.entities.any {
                        it.startsWith("Dinero:", ignoreCase = true) ||
                        it.startsWith("Tarjeta:", ignoreCase = true) ||
                        it.startsWith("IBAN:", ignoreCase = true)
                    }
                    val hasPaymentLabel = entity.labels.any {
                        it.startsWith("Recibo", ignoreCase = true) ||
                        it.startsWith("Dinero", ignoreCase = true)
                    }
                    val hasPaymentText = containsPaymentKeywords(entity.textContent)

                    if (hasPaymentEntities || hasPaymentLabel || hasPaymentText) {
                        val list = visualCategoryMap.getOrOrPut("Pagos y Facturas") { mutableListOf() }
                        if (list.none { it.id == photo.id }) {
                            list.add(photo)
                        }
                    }

                    // Categoria: Documentos Personales y Credenciales
                    val hasDocumentLabel = entity.labels.any {
                        it.startsWith("Pasaporte", ignoreCase = true) ||
                        it.startsWith("Papel", ignoreCase = true) ||
                        it.startsWith("Documento", ignoreCase = true)
                    }
                    val hasDocumentText = containsDocumentKeywords(entity.textContent)

                    if (hasDocumentLabel || hasDocumentText) {
                        val list = visualCategoryMap.getOrOrPut("Documentos") { mutableListOf() }
                        if (list.none { it.id == photo.id }) {
                            list.add(photo)
                        }
                    }

                    // Categorias extraidas de las etiquetas de ML Kit
                    val isDocOrPaymentPhoto = hasPaymentEntities || hasPaymentLabel || hasPaymentText || hasDocumentLabel || hasDocumentText
                    val excludedLowerSet = entity.excludedTags.map { it.lowercase(Locale.ROOT).trim() }.toSet()

                    for (rawLabel in entity.labels) {
                        val labelName = rawLabel.substringBefore(" - ").trim()
                        val lower = labelName.lowercase(Locale.ROOT)

                        // 1. Respetar exclusiones explicitas del usuario
                        if (excludedLowerSet.contains(lower) || excludedLowerSet.any { lower.contains(it) }) {
                            continue
                        }

                        // 2. Si la foto es documento/pago, no permitir categorias de mascotas o comida
                        if (isDocOrPaymentPhoto) {
                            val isAnimal = lower.contains("perro") || lower.contains("gato") || lower.contains("mascota") || lower.contains("animal")
                            val isFood = lower.contains("comida") || lower.contains("alimento")
                            if (isAnimal || isFood) continue
                        }

                        // 3. Excluir categorias no deseadas o con alta tasa de falsos positivos
                        val isNoisyCategory = lower == "objeto" ||
                                lower == "objeto detectado" ||
                                lower == "persona" ||
                                lower == "personas" ||
                                lower == "recibo" ||
                                lower == "dinero" ||
                                lower == "papel" ||
                                lower == "pasaporte" ||
                                lower.contains("insect") ||
                                lower.contains("artropod") ||
                                lower.contains("invertebrad") ||
                                lower.contains("escarabajo") ||
                                lower.contains("arana") ||
                                lower.contains("plaga")

                        if (labelName.isNotBlank() && !isNoisyCategory) {
                            val categoryTitle = pluralizeCategory(labelName)
                            val list = visualCategoryMap.getOrOrPut(categoryTitle) { mutableListOf() }
                            if (list.none { it.id == photo.id }) {
                                list.add(photo)
                            }
                        }
                    }
                }

                val visualCollections = visualCategoryMap.map { (categoryName, photos) ->
                    val sortedPhotos = photos.sortedByDescending { it.dateTaken }
                    SmartCollectionItem(
                        id = "visual_$categoryName",
                        title = categoryName,
                        count = sortedPhotos.size,
                        coverUri = sortedPhotos.firstOrNull()?.uri,
                        type = SmartCollectionType.VISUAL_CATEGORY,
                        photos = sortedPhotos
                    )
                }.sortedByDescending { it.count }

                SmartCollectionsResult(
                    appCollections = appCollections,
                    visualCollections = visualCollections
                )
            }
        }
    }

    private fun containsPaymentKeywords(text: String): Boolean {
        if (text.isBlank()) return false
        return PAYMENT_KEYWORDS_REGEX.containsMatchIn(text)
    }

    private fun containsDocumentKeywords(text: String): Boolean {
        if (text.isBlank()) return false
        return DOCUMENT_KEYWORDS_REGEX.containsMatchIn(text)
    }

    private fun pluralizeCategory(name: String): String {
        val trimmed = name.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        return when {
            lower == "automovil" || lower == "vehiculo" -> "Automoviles"
            lower == "gato" -> "Gatos"
            lower == "perro" -> "Perros"
            lower == "comida" -> "Comida"
            lower == "captura de pantalla" -> "Capturas"
            lower == "dispositivo" || lower == "telefono inteligente" -> "Dispositivos"
            lower == "documento" -> "Documentos"
            lower == "arbol" || lower == "planta" -> "Naturaleza"
            lower.endsWith("s") -> trimmed.replaceFirstChar { it.titlecase(Locale.ROOT) }
            lower.endsWith("a") || lower.endsWith("e") || lower.endsWith("o") || lower.endsWith("u") ->
                (trimmed + "s").replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> (trimmed + "es").replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private fun <K, V> MutableMap<K, V>.getOrOrPut(key: K, defaultValue: () -> V): V {
        val value = get(key)
        return if (value == null) {
            val answer = defaultValue()
            put(key, answer)
            answer
        } else {
            value
        }
    }

    companion object {
        private val PAYMENT_KEYWORDS_REGEX = Regex(
            "(?i)\\b(factura|recibo|invoice|comprobante|transferencia|voucher|subtotal|total a pagar|pago exitoso|pago realizado|importe total|cuenta bancaria)\\b"
        )

        private val DOCUMENT_KEYWORDS_REGEX = Regex(
            "(?i)\\b(dni|cedula|c[eé]dula|pasaporte|identificacion|identificaci[oó]n|licencia de conducir|curp|rut|acta de nacimiento|certificado|contrato|constancia)\\b"
        )
    }
}
