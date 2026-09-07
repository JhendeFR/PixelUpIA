package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository

class UnindexPhotoUseCase(
    private val smartPhotoRepository: SmartPhotoRepository
) {
    suspend operator fun invoke(photoId: Long): Result<Unit> {
        return smartPhotoRepository.unindexPhoto(photoId)
    }
}
