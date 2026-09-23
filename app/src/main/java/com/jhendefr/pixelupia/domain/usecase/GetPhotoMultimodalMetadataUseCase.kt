package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.MultimodalMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetPhotoMultimodalMetadataUseCase(
    private val smartGalleryDao: SmartGalleryDao,
    private val mobileClipDataSource: com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource? = null
) {
    suspend operator fun invoke(photoId: Long): MultimodalMetadata? = withContext(Dispatchers.IO) {
        val entity = smartGalleryDao.getPhotoById(photoId) ?: return@withContext null

        val hasEmbedding = !entity.semanticEmbedding.isNullOrBlank()
        val clipPredictions = if (hasEmbedding && mobileClipDataSource != null) {
            try {
                val vec = com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.base64ToEmbedding(entity.semanticEmbedding!!)
                mobileClipDataSource.predictZeroShotLabels(vec)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        MultimodalMetadata(
            photoId = entity.photoId,
            hasFaces = entity.hasFaces,
            barcodes = entity.barcodes,
            labels = entity.labels,
            entities = entity.entities,
            isEnriched = entity.isEnriched,
            hasSemanticEmbedding = hasEmbedding,
            clipTopPredictions = clipPredictions
        )
    }
}
