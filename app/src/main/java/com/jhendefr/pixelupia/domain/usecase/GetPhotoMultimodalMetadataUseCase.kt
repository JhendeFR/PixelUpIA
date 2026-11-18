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
        var clipPredictions: List<Pair<String, Float>> = emptyList()
        var latentSuggestions: List<com.jhendefr.pixelupia.domain.model.LatentSearchSuggestion> = emptyList()
        if (hasEmbedding && mobileClipDataSource != null) {
            try {
                val vec = com.jhendefr.pixelupia.data.ml.MobileClipEmbeddingDataSource.base64ToEmbedding(entity.semanticEmbedding!!)
                clipPredictions = mobileClipDataSource.predictZeroShotLabels(vec)
                latentSuggestions = mobileClipDataSource.suggestSearchQueries(vec)
            } catch (_: Exception) {
            }
        }

        MultimodalMetadata(
            photoId = entity.photoId,
            hasFaces = entity.hasFaces,
            barcodes = entity.barcodes,
            labels = entity.labels,
            entities = entity.entities,
            isEnriched = entity.isEnriched,
            hasSemanticEmbedding = hasEmbedding,
            clipTopPredictions = clipPredictions,
            latentSearchSuggestions = latentSuggestions
        )
    }
}
