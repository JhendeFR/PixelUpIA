package com.jhendefr.pixelupia.ui.gallery

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder

data class AlbumDetailUiState(
    val albumName: String = "",
    val photos: List<Photo> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val errorMessage: String? = null
)

sealed interface AlbumDetailEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : AlbumDetailEvent
    object Refresh : AlbumDetailEvent
}
