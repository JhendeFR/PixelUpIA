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
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val unenriched = photos.filter { photo ->
            val entity = smartGalleryDao.getPhotoById(photo.id)
            entity == null || !entity.isEnriched
        }

        val total = unenriched.size
        unenriched.forEachIndexed { index, photo ->
            enrichScreenshotUseCase(photo)
            onProgress?.invoke(index + 1, total)
            delay(100)
        }
    }
}
