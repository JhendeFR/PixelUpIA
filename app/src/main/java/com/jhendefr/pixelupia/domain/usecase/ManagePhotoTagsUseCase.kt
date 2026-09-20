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

            val updatedLabels = (entity.labels + trimmedTag).distinct()
            val expandedSynonyms = SynonymRegistry.expandForSearchContent(trimmedTag)
            val updatedSearchContent = "${entity.searchContent} $expandedSynonyms".trim()

            smartGalleryDao.updatePhotoLabelsAndSearch(
                photoId = photoId,
                labels = updatedLabels,
                searchContent = updatedSearchContent
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
            val updatedLabels = entity.labels.filterNot {
                it.equals(trimmedTag, ignoreCase = true) ||
                it.substringBeforeLast(" - ").equals(trimmedTag, ignoreCase = true)
            }

            // Reconstruir searchContent eliminando el termino y sus sinonimos
            val synonymsToRemove = SynonymRegistry.getSynonyms(trimmedTag) + listOf(trimmedTag)
            var newSearchContent = entity.searchContent
            for (syn in synonymsToRemove) {
                newSearchContent = newSearchContent.replace(Regex("(?i)\\b${Regex.escape(syn)}\\b"), " ")
            }
            newSearchContent = newSearchContent.replace(Regex("\\s+"), " ").trim()

            smartGalleryDao.updatePhotoLabelsAndSearch(
                photoId = photoId,
                labels = updatedLabels,
                searchContent = newSearchContent
            )
            Result.success(updatedLabels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
