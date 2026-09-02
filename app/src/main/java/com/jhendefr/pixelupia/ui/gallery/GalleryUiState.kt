package com.jhendefr.pixelupia.ui.gallery

import android.content.IntentSender
import com.jhendefr.pixelupia.domain.model.Album
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder

enum class GalleryTab {
    PHOTOS,
    ALBUMS,
    FAVORITES
}

data class GalleryUiState(
    val isLoading: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val albums: List<Album> = emptyList(),
    val favoritePhotoIds: Set<Long> = emptySet(),
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val selectedAlbumName: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val selectedPhotoIds: Set<Long> = emptySet(),
    val showDeleteConfirm: Boolean = false,
    val showFolderPickerForMove: Boolean = false,
    val showFolderPickerForCopy: Boolean = false,
    val isProcessingAction: Boolean = false,
    val pendingIntentSender: IntentSender? = null
) {
    val isSelectionMode: Boolean get() = selectedPhotoIds.isNotEmpty()
    val selectedPhotos: List<Photo> get() = photos.filter { selectedPhotoIds.contains(it.id) }
    val favoritePhotos: List<Photo> get() = photos.filter { favoritePhotoIds.contains(it.id) }
    val existingFolderNames: List<String> get() = albums.map { it.name }.distinct()
}

sealed interface GalleryEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : GalleryEvent
    data class SelectTab(val tab: GalleryTab) : GalleryEvent
    data class SelectAlbum(val albumName: String?) : GalleryEvent
    data class TogglePhotoSelection(val photoId: Long) : GalleryEvent
    data class ToggleFavorite(val photoId: Long) : GalleryEvent
    object SelectAll : GalleryEvent
    object ClearSelection : GalleryEvent
    object Refresh : GalleryEvent

    // Acciones por lote
    object RequestDeleteSelected : GalleryEvent
    object ConfirmDeleteSelected : GalleryEvent
    object DismissDeleteDialog : GalleryEvent

    object RequestMoveSelected : GalleryEvent
    data class ConfirmMoveSelected(val targetFolder: String) : GalleryEvent
    object DismissMoveDialog : GalleryEvent

    object RequestCopySelected : GalleryEvent
    data class ConfirmCopySelected(val targetFolder: String) : GalleryEvent
    object DismissCopyDialog : GalleryEvent

    object OnIntentSenderCompleted : GalleryEvent
    object OnIntentSenderDismissed : GalleryEvent
    object ClearUserMessage : GalleryEvent
}
