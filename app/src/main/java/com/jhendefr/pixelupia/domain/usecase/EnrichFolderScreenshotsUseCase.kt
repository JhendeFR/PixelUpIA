package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class EnrichFolderScreenshotsUseCase(
    private val enrichScreenshotUseCase: EnrichScreenshotUseCase,
    private val smartGalleryDao: SmartGalleryDao
) {
    suspend operator fun invoke(
        photos: List<Photo>,
        forceReEnrich: Boolean = true,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val targets = if (forceReEnrich) {
            photos
        } else {
            photos.filter { photo ->
                val entity = smartGalleryDao.getPhotoById(photo.id)
                entity == null || !entity.isEnriched || entity.semanticEmbedding.isNullOrBlank()
            }
        }

        val total = targets.size
        targets.forEachIndexed { index, photo ->
            enrichScreenshotUseCase(photo)
            onProgress?.invoke(index + 1, total)
            delay(50)
        }
    }
}
