package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ProcessFolderOcrUseCase(
    private val indexScreenshotUseCase: IndexScreenshotUseCase,
    private val smartPhotoRepository: SmartPhotoRepository
) {
    suspend operator fun invoke(photos: List<Photo>, onProgress: ((current: Int, total: Int) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val unindexed = photos.filter { !smartPhotoRepository.isPhotoIndexed(it.id) }
        val total = unindexed.size

        unindexed.forEachIndexed { index, photo ->
            indexScreenshotUseCase(photo)
            onProgress?.invoke(index + 1, total)
            delay(100)
        }
    }
}
