package com.jhendefr.pixelupia.domain.repository

import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.flow.Flow

interface SmartPhotoRepository {
    suspend fun indexPhoto(photo: Photo, textContent: String, boundingBoxes: String): Result<Unit>
    fun searchPhotos(query: String): Flow<List<Photo>>
    fun getAllSmartPhotos(): Flow<List<Photo>>
    fun getIndexedPhotoIds(): Flow<List<Long>>
    suspend fun isPhotoIndexed(photoId: Long): Boolean
}
