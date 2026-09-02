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
            val entity = SmartPhotoEntity(
                photoId = photo.id,
                uri = photo.uri.toString(),
                name = photo.name,
                folderName = photo.folderName,
                dateAdded = photo.dateTaken,
                textContent = textContent,
                boundingBoxes = boundingBoxes
            )
            val ftsEntity = SmartPhotoFtsEntity(
                textContent = textContent,
                boundingBoxes = boundingBoxes
            )
            dao.insertPhoto(entity)
            dao.insertPhotoFts(ftsEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun searchPhotos(query: String): Flow<List<Photo>> {
        val sanitizedQuery = query.trim()
        val ftsQuery = if (sanitizedQuery.isEmpty()) "" else "*$sanitizedQuery*"
        return dao.searchPhotosByText(ftsQuery).map { entities ->
            entities.map { entity ->
                Photo(
                    id = entity.photoId,
                    uri = Uri.parse(entity.uri),
                    name = entity.name,
                    dateTaken = entity.dateAdded,
                    size = 0L,
                    folderName = entity.folderName,
                    mimeType = "image/*"
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
                    mimeType = "image/*"
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
}
