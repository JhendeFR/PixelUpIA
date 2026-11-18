package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.data.ml.SynonymRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ManagePhotoTagsUseCase(
    private val smartGalleryDao: SmartGalleryDao
) {

    suspend fun addTag(photoId: Long, newTag: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val entity = smartGalleryDao.getPhotoById(photoId)
                ?: return@withContext Result.failure(Exception("Foto no encontrada en base de datos"))

            val trimmedTag = newTag.trim()
            if (trimmedTag.isBlank()) return@withContext Result.success(entity.labels)

            val updatedLabels = if (entity.labels.any {
                it.equals(trimmedTag, ignoreCase = true) ||
                it.substringBeforeLast(" - ").equals(trimmedTag, ignoreCase = true)
            }) {
                entity.labels
            } else {
                entity.labels + trimmedTag
            }
            val updatedManualTags = if (entity.manualTags.any { it.equals(trimmedTag, ignoreCase = true) }) {
                entity.manualTags
            } else {
                entity.manualTags + trimmedTag
            }
            val updatedExclusions = entity.excludedTags.filterNot { it.equals(trimmedTag, ignoreCase = true) }
            val expandedSynonyms = SynonymRegistry.expandForSearchContent(trimmedTag)
            val updatedSearchContent = "${entity.searchContent} $expandedSynonyms".trim()

            smartGalleryDao.updatePhotoTagsExclusionsAndManualTags(
                photoId = photoId,
                labels = updatedLabels,
                manualTags = updatedManualTags,
                searchContent = updatedSearchContent,
                excludedTags = updatedExclusions
            )
            Result.success(updatedLabels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeTag(photoId: Long, tagToRemove: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val entity = smartGalleryDao.getPhotoById(photoId)
                ?: return@withContext Result.failure(Exception("Foto no encontrada en base de datos"))

            val trimmedTag = tagToRemove.trim()
            val baseTag = trimmedTag.substringBeforeLast(" - ").trim()
            val updatedLabels = entity.labels.filterNot {
                it.equals(trimmedTag, ignoreCase = true) ||
                it.equals(baseTag, ignoreCase = true) ||
                it.substringBeforeLast(" - ").equals(baseTag, ignoreCase = true)
            }
            val updatedManualTags = entity.manualTags.filterNot {
                it.equals(trimmedTag, ignoreCase = true) ||
                it.equals(baseTag, ignoreCase = true)
            }
            val updatedExclusions = if (entity.excludedTags.any { it.equals(baseTag, ignoreCase = true) }) {
                entity.excludedTags
            } else {
                entity.excludedTags + baseTag
            }

            // Reconstruir searchContent eliminando el termino y sus sinonimos
            val synonymsToRemove = SynonymRegistry.getSynonyms(baseTag) + listOf(baseTag)
            var newSearchContent = entity.searchContent
            for (syn in synonymsToRemove) {
                newSearchContent = newSearchContent.replace(Regex("(?i)\\b${Regex.escape(syn)}\\b"), " ")
            }
            newSearchContent = newSearchContent.replace(Regex("\\s+"), " ").trim()

            smartGalleryDao.updatePhotoTagsExclusionsAndManualTags(
                photoId = photoId,
                labels = updatedLabels,
                manualTags = updatedManualTags,
                searchContent = newSearchContent,
                excludedTags = updatedExclusions
            )

            // Si se quito etiqueta de mascota, remover de clusters de mascotas
            val lowerTag = trimmedTag.lowercase(java.util.Locale.ROOT)
            val isPetTag = lowerTag.contains("perro") || lowerTag.contains("gato") || lowerTag.contains("mascota") ||
                    lowerTag.contains("dog") || lowerTag.contains("cat") || lowerTag.contains("pet")
            if (isPetTag) {
                smartGalleryDao.excludePhotoFromAllPetClusters(photoId)
            }

            Result.success(updatedLabels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun banPhotosFromSearch(photoIds: List<Long>, searchQuery: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = searchQuery.trim()
            if (cleanQuery.isBlank() || photoIds.isEmpty()) return@withContext Result.success(0)

            val queryNorm = com.jhendefr.pixelupia.data.ml.SearchBanMatcher.normalize(cleanQuery)
            val entities = smartGalleryDao.getPhotosByIds(photoIds.distinct())
            var updatedCount = 0

            for (entity in entities) {
                val alreadyBanned = entity.bannedSearchQueries.any {
                    com.jhendefr.pixelupia.data.ml.SearchBanMatcher.normalize(it) == queryNorm
                }
                if (!alreadyBanned) {
                    val updated = entity.bannedSearchQueries + cleanQuery
                    smartGalleryDao.updateBannedSearchQueries(entity.photoId, updated)
                    updatedCount++
                }
            }
            Result.success(updatedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun removeBannedQueryFromList(current: List<String>, searchQuery: String): List<String> {
        val cleanQuery = searchQuery.trim()
        val queryNorm = com.jhendefr.pixelupia.data.ml.SearchBanMatcher.normalize(cleanQuery)
        return current.filterNot {
            com.jhendefr.pixelupia.data.ml.SearchBanMatcher.normalize(it) == queryNorm ||
                it.equals(cleanQuery, ignoreCase = true)
        }
    }

    suspend fun unbanPhotosFromSearch(photoIds: List<Long>, searchQuery: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = searchQuery.trim()
            if (cleanQuery.isBlank() || photoIds.isEmpty()) return@withContext Result.success(0)

            val entities = smartGalleryDao.getPhotosByIds(photoIds.distinct())
            var updatedCount = 0

            for (entity in entities) {
                val updated = removeBannedQueryFromList(entity.bannedSearchQueries, cleanQuery)
                if (updated.size != entity.bannedSearchQueries.size) {
                    smartGalleryDao.updateBannedSearchQueries(entity.photoId, updated)
                    updatedCount++
                }
            }
            Result.success(updatedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
