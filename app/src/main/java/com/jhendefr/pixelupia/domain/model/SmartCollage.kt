package com.jhendefr.pixelupia.domain.model

enum class CollageTemplate {
    GRID_2X2,
    TRIPTYCH,
    DOMINANT_WITH_THUMBS
}

data class SmartCollageItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val template: CollageTemplate,
    val photos: List<Photo>
)
