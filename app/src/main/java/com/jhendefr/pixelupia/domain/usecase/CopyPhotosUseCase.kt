package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.MediaRepository

class CopyPhotosUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(photos: List<Photo>, targetFolder: String): MediaOperationResult {
        if (photos.isEmpty()) return MediaOperationResult.Success
        return repository.copyPhotos(photos, targetFolder)
    }

    suspend operator fun invoke(photo: Photo, targetFolder: String): MediaOperationResult {
        return repository.copyPhoto(photo, targetFolder)
    }
}
