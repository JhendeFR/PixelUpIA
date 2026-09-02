package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository
import kotlinx.coroutines.flow.Flow

class SearchSmartPhotosUseCase(
    private val smartPhotoRepository: SmartPhotoRepository
) {
    operator fun invoke(query: String): Flow<List<Photo>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            smartPhotoRepository.getAllSmartPhotos()
        } else {
            smartPhotoRepository.searchPhotos(trimmed)
        }
    }
}
