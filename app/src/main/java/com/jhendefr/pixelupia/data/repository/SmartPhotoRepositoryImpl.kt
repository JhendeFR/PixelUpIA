package com.jhendefr.pixelupia.data.repository

import android.net.Uri
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.data.local.room.SmartPhotoEntity
import com.jhendefr.pixelupia.data.local.room.SmartPhotoFtsEntity
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SmartPhotoRepositoryImpl(
    private val dao: SmartGalleryDao,
    private val mobileClipDataSource: com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource? = null
) : SmartPhotoRepository {

    private val queryEmbeddingCache = android.util.LruCache<String, List<FloatArray>>(30)

    override suspend fun indexPhoto(
        photo: Photo,
        textContent: String,
        boundingBoxes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceApp = photo.sourceApp ?: com.jhendefr.pixelupia.domain.classifier.ScreenshotSourceClassifier.extractSourceApp(photo.name)
            val searchContent = if (!sourceApp.isNullOrBlank()) {
                "$textContent $sourceApp"
            } else {
                textContent
            }.trim()

            val entity = SmartPhotoEntity(
                photoId = photo.id,
                uri = photo.uri.toString(),
                name = photo.name,
                folderName = photo.folderName,
                dateAdded = photo.dateTaken,
                textContent = textContent,
                boundingBoxes = boundingBoxes,
                sourceApp = sourceApp,
                searchContent = searchContent
            )
            dao.insertPhoto(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun searchPhotos(query: String): Flow<List<Photo>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return dao.searchPhotosByText("").map { emptyList() }
        }

        val sanitized = cleanQuery.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim()
        val tokens = sanitized.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val alternativesList = tokens.map { token ->
            val synonyms = com.jhendefr.pixelupia.data.ml.SynonymRegistry.getSearchQuerySynonyms(token).take(8)
            val alternatives = (listOf(token) + synonyms).distinct()
            if (alternatives.size == 1) {
                "${alternatives.first()}*"
            } else {
                alternatives.joinToString(separator = " OR ", prefix = "(", postfix = ")") { "$it*" }
            }
        }
        val ftsAndQuery = alternativesList.joinToString(" ")
        val ftsOrQuery = alternativesList.joinToString(" OR ")

        val ftsFlow = if (ftsAndQuery.isNotBlank()) {
            dao.searchPhotosByText(ftsAndQuery)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }

        return ftsFlow.map { ftsEntities ->
            val clip = mobileClipDataSource
            val effectiveFtsEntities = if (ftsEntities.isEmpty() && tokens.size > 1 && ftsOrQuery.isNotBlank()) {
                try {
                    dao.searchPhotosByTextList(ftsOrQuery)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                ftsEntities
            }

            if (clip == null) {
                return@map effectiveFtsEntities.map { toPhoto(it) }
            }

            // Inferencia semantica de texto con MobileCLIP-S0 expandida bilingue y cacheada
            val queryEmbeddings = queryEmbeddingCache.get(cleanQuery) ?: run {
                val generated = clip.extractQueryEmbeddings(cleanQuery)
                if (generated.isNotEmpty()) {
                    queryEmbeddingCache.put(cleanQuery, generated)
                }
                generated
            }
            if (queryEmbeddings.isEmpty()) {
                return@map effectiveFtsEntities.map { toPhoto(it) }
            }

            val allWithEmbedding = dao.getPhotosWithEmbedding()
            val semanticMatches = mutableListOf<Pair<SmartPhotoEntity, Float>>()

            val queryLower = cleanQuery.lowercase(java.util.Locale.ROOT)
            val isSearchingDog = (queryLower.contains("perro") || queryLower.contains("canino") ||
                    queryLower.contains("cachorro") || queryLower.contains("dog") ||
                    queryLower.contains("puppy")) &&
                    !queryLower.contains("gato") && !queryLower.contains("cat") &&
                    !queryLower.contains("felino") && !queryLower.contains("michi")

            val isSearchingCat = (queryLower.contains("gato") || queryLower.contains("felino") ||
                    queryLower.contains("gatito") || queryLower.contains("cat") ||
                    queryLower.contains("kitten") || queryLower.contains("michi")) &&
                    !queryLower.contains("perro") && !queryLower.contains("dog") &&
                    !queryLower.contains("canino") && !queryLower.contains("cachorro")

            for (photoEntity in allWithEmbedding) {
                val isExcluded = photoEntity.excludedTags.any { exc ->
                    cleanQuery.contains(exc, ignoreCase = true) || exc.contains(cleanQuery, ignoreCase = true)
                }
                if (isExcluded) continue

                val base64 = photoEntity.semanticEmbedding ?: continue
                try {
                    val photoEmbedding = com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.base64ToEmbedding(base64)
                    var maxSimilarity = 0f
                    for (qEmb in queryEmbeddings) {
                        val similarity = com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.cosineSimilarity(qEmb, photoEmbedding)
                        if (similarity > maxSimilarity) {
                            maxSimilarity = similarity
                        }
                    }
                    if (maxSimilarity >= 0.20f) {
                        val hasDogTag = photoEntity.labels.any { l ->
                            val clean = if (l.contains(" - ")) l.substringBeforeLast(" - ") else l
                            clean.contains("perro", ignoreCase = true) ||
                            clean.contains("canino", ignoreCase = true) ||
                            clean.contains("cachorro", ignoreCase = true) ||
                            clean.contains("dog", ignoreCase = true)
                        }
                        val hasCatTag = photoEntity.labels.any { l ->
                            val clean = if (l.contains(" - ")) l.substringBeforeLast(" - ") else l
                            clean.contains("gato", ignoreCase = true) ||
                            clean.contains("felino", ignoreCase = true) ||
                            clean.contains("gatito", ignoreCase = true) ||
                            clean.contains("cat", ignoreCase = true)
                        }

                        val isContradiction = (isSearchingDog && hasCatTag && !hasDogTag) ||
                                              (isSearchingCat && hasDogTag && !hasCatTag)

                        if (!isContradiction) {
                            semanticMatches.add(Pair(photoEntity, maxSimilarity))
                        }
                    }
                } catch (e: Exception) {
                    // Ignorar errores de deserializacion individual
                }
            }

            // Ordenar por relevancia semantica
            semanticMatches.sortByDescending { it.second }

            // Hybrid Score Fusion:
            // 1. Fotos que coinciden tanto en effectiveFtsEntities como en semanticMatches (ordenadas por similitud)
            // 2. Fotos de semanticMatches con similitud >= 0.20f
            // 3. Resto de coincidencias lexicas de effectiveFtsEntities
            val seenIds = HashSet<Long>()
            val combined = mutableListOf<Photo>()
            val ftsPhotoIds = effectiveFtsEntities.map { it.photoId }.toSet()

            // 1. Fotos que coinciden tanto en effectiveFtsEntities como en semanticMatches
            for ((entity, _) in semanticMatches) {
                if (ftsPhotoIds.contains(entity.photoId)) {
                    if (seenIds.add(entity.photoId)) {
                        combined.add(toPhoto(entity))
                    }
                }
            }

            // 2. Fotos de semanticMatches con similitud >= 0.20f restantes
            for ((entity, _) in semanticMatches) {
                if (seenIds.add(entity.photoId)) {
                    combined.add(toPhoto(entity))
                }
            }

            // 3. Resto de coincidencias lexicas de effectiveFtsEntities
            for (entity in effectiveFtsEntities) {
                val isExcluded = entity.excludedTags.any { exc ->
                    cleanQuery.contains(exc, ignoreCase = true) || exc.contains(cleanQuery, ignoreCase = true)
                }
                if (isExcluded) continue

                if (seenIds.add(entity.photoId)) {
                    combined.add(toPhoto(entity))
                }
            }

            combined
        }
    }

    private fun toPhoto(entity: SmartPhotoEntity): Photo {
        return Photo(
            id = entity.photoId,
            uri = Uri.parse(entity.uri),
            name = entity.name,
            dateTaken = entity.dateAdded,
            size = 0L,
            folderName = entity.folderName,
            mimeType = "image/*",
            sourceApp = entity.sourceApp
        )
    }

    override fun getAllSmartPhotos(): Flow<List<Photo>> {
        return dao.getAllSmartPhotos().map { entities ->
            entities.map { entity ->
                Photo(
                    id = entity.photoId,
                    uri = Uri.parse(entity.uri),
                    name = entity.name,
                    dateTaken = entity.dateAdded,
                    size = 0L,
                    folderName = entity.folderName,
                    mimeType = "image/*",
                    sourceApp = entity.sourceApp
                )
            }
        }
    }

    override fun getIndexedPhotoIds(): Flow<List<Long>> {
        return dao.getIndexedPhotoIds()
    }

    override suspend fun isPhotoIndexed(photoId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.getPhotoById(photoId) != null
    }

    override suspend fun unindexPhoto(photoId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deletePhotoById(photoId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
