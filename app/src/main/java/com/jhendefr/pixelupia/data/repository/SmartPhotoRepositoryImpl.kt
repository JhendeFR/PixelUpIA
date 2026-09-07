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
    private val dao: SmartGalleryDao
) : SmartPhotoRepository {

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
        val sanitized = query.trim().replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim()
        val tokens = sanitized.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val ftsQuery = if (tokens.isEmpty()) "" else tokens.joinToString(" ") { "$it*" }
        return dao.searchPhotosByText(ftsQuery).map { entities ->
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
