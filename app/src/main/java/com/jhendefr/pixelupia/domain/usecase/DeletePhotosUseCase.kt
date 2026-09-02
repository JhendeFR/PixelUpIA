package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.MediaRepository

class DeletePhotosUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(photos: List<Photo>): MediaOperationResult {
        if (photos.isEmpty()) return MediaOperationResult.Success
        return repository.deletePhotos(photos)
    }

    suspend operator fun invoke(photo: Photo): MediaOperationResult {
        return repository.deletePhoto(photo)
    }
}
