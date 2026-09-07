package com.jhendefr.pixelupia.ui.gallery

import android.content.IntentSender
import com.jhendefr.pixelupia.domain.model.Album
import com.jhendefr.pixelupia.domain.model.IndexedFolderInfo
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder
import com.jhendefr.pixelupia.domain.usecase.SmartCollectionItem

enum class GalleryTab {
    PHOTOS,
    ALBUMS,
    SMART_AI
}

data class GalleryUiState(
    val isLoading: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val albums: List<Album> = emptyList(),
    val smartPhotos: List<Photo> = emptyList(),
    val indexedFolders: List<IndexedFolderInfo> = emptyList(),
    val selectedIndexedFolderName: String? = null,
    val isBatchIndexing: Boolean = false,
    val batchIndexingProgress: Pair<Int, Int>? = null,
    val activeIndexingFolder: String? = null,
    val isFolderEnriching: Boolean = false,
    val folderEnrichingProgress: Pair<Int, Int>? = null,
    val activeEnrichingFolder: String? = null,
    val smartSearchQuery: String = "",
    val appCollections: List<SmartCollectionItem> = emptyList(),
    val visualCollections: List<SmartCollectionItem> = emptyList(),
    val selectedSmartCollection: SmartCollectionItem? = null,
    val hiddenCollectionIds: Set<String> = emptySet(),
    val hiddenCollections: List<SmartCollectionItem> = emptyList(),
    val showHiddenCollectionsDialog: Boolean = false,
    val chosenIndexFolders: Set<String> = setOf("Screenshots"),
    val showFolderManagerDialog: Boolean = false,
    val showIndexedCount: Boolean = true,
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
    val existingFolderNames: List<String> get() = albums.map { it.name }.distinct()
}

sealed interface GalleryEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : GalleryEvent
    data class SelectTab(val tab: GalleryTab) : GalleryEvent
    data class SelectAlbum(val albumName: String?) : GalleryEvent
    data class SelectIndexedFolder(val folderName: String?) : GalleryEvent
    data class SelectSmartCollection(val collection: SmartCollectionItem?) : GalleryEvent
    data class TriggerBatchIndexFolder(val folderName: String) : GalleryEvent
    data class TriggerFolderMultimodalEnrichment(val folderName: String) : GalleryEvent
    data class TogglePhotoSelection(val photoId: Long) : GalleryEvent
    data class ToggleFavorite(val photoId: Long) : GalleryEvent
    data class UpdateSmartSearchQuery(val query: String) : GalleryEvent
    object SelectAll : GalleryEvent
    object ClearSelection : GalleryEvent
    object Refresh : GalleryEvent

    // Gestion de Colecciones y Ocultos
    data class HideCollection(val collectionId: String) : GalleryEvent
    data class UnhideCollection(val collectionId: String) : GalleryEvent
    data class ToggleHiddenCollectionsDialog(val show: Boolean) : GalleryEvent

    // Gestion de Carpetas Indexadas Dinamicas y Visibilidad de Conteo
    data class ToggleFolderManagerDialog(val show: Boolean) : GalleryEvent
    data class ToggleFolderIndexing(val folderName: String) : GalleryEvent
    object ToggleShowIndexedCount : GalleryEvent

    // Indexar y Desindexar por Imagen Individual
    data class UnindexPhoto(val photoId: Long) : GalleryEvent
    data class IndexPhoto(val photo: Photo) : GalleryEvent

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
