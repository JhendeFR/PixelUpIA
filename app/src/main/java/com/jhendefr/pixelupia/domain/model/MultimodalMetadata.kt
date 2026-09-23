package com.jhendefr.pixelupia.domain.model

data class MultimodalMetadata(
    val photoId: Long,
    val hasFaces: Boolean = false,
    val barcodes: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val isEnriched: Boolean = false,
    val hasSemanticEmbedding: Boolean = false,
    val clipTopPredictions: List<Pair<String, Float>> = emptyList()
) {
    val hasAnyMetadata: Boolean
        get() = hasFaces || barcodes.isNotEmpty() || labels.isNotEmpty() || entities.isNotEmpty() || hasSemanticEmbedding
}
