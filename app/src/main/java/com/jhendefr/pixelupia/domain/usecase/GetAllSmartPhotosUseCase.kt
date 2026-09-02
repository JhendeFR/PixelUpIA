package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository
import kotlinx.coroutines.flow.Flow

class GetAllSmartPhotosUseCase(
    private val smartPhotoRepository: SmartPhotoRepository
) {
    operator fun invoke(): Flow<List<Photo>> {
        return smartPhotoRepository.getAllSmartPhotos()
    }
}
