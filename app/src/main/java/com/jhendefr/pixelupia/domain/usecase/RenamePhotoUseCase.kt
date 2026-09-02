package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.MediaRepository

class RenamePhotoUseCase(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(photo: Photo, newName: String): Result<Unit> {
        return repository.renamePhoto(photo, newName)
    }
}
