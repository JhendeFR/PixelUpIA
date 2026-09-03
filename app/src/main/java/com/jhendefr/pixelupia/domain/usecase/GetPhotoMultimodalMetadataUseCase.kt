package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.MultimodalMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetPhotoMultimodalMetadataUseCase(
    private val smartGalleryDao: SmartGalleryDao
) {
    suspend operator fun invoke(photoId: Long): MultimodalMetadata? = withContext(Dispatchers.IO) {
        val entity = smartGalleryDao.getPhotoById(photoId) ?: return@withContext null
        MultimodalMetadata(
            photoId = entity.photoId,
            hasFaces = entity.hasFaces,
            barcodes = entity.barcodes,
            labels = entity.labels,
            entities = entity.entities,
            isEnriched = entity.isEnriched
        )
    }
}
