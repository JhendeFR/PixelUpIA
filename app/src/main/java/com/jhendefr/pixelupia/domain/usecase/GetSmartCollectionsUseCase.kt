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
                        visualCategoryMap.getOrOrPut("Personas") { mutableListOf() }.add(photo)
                    }

                    // Categorias extraidas de las etiquetas de ML Kit
                    for (rawLabel in entity.labels) {
                        val labelName = rawLabel.substringBefore(" - ").trim()
                        if (labelName.isNotBlank() &&
                            !labelName.equals("Objeto", ignoreCase = true) &&
                            !labelName.equals("Objeto detectado", ignoreCase = true) &&
                            !labelName.equals("Persona", ignoreCase = true) &&
                            !labelName.equals("Personas", ignoreCase = true)
                        ) {
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
}
