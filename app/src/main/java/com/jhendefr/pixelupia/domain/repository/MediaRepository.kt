package com.jhendefr.pixelupia.domain.repository

import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getPhotos(): Flow<List<Photo>>
    suspend fun deletePhoto(photo: Photo): MediaOperationResult
    suspend fun deletePhotos(photos: List<Photo>): MediaOperationResult
    suspend fun renamePhoto(photo: Photo, newName: String): Result<Unit>
    suspend fun movePhoto(photo: Photo, targetFolder: String): MediaOperationResult
    suspend fun movePhotos(photos: List<Photo>, targetFolder: String): MediaOperationResult
    suspend fun copyPhoto(photo: Photo, targetFolder: String): MediaOperationResult
    suspend fun copyPhotos(photos: List<Photo>, targetFolder: String): MediaOperationResult
}
