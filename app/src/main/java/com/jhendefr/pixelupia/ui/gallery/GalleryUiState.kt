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
    val faceClusters: List<com.jhendefr.pixelupia.domain.model.FaceCluster> = emptyList(),
    val selectedFaceCluster: com.jhendefr.pixelupia.domain.model.FaceCluster? = null,
    val selectedFaceClusterPhotos: List<Photo> = emptyList(),
    val showAddPhotosToClusterDialog: Boolean = false,
    val showMergeClusterDialog: Boolean = false,
    val showChooseCoverDialog: Boolean = false,
    val smartCollages: List<com.jhendefr.pixelupia.domain.model.SmartCollageItem> = emptyList(),
    val selectedFilterType: String = "Todos",
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
    val pendingIntentSender: IntentSender? = null,
    val memoryStories: List<com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity> = emptyList(),
    val activeStory: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity? = null,
    val activeStoryPhotos: List<com.jhendefr.pixelupia.data.local.room.SmartPhotoEntity> = emptyList(),
    val storyToEditName: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity? = null,
    val storyToAddPhotos: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity? = null,
    val storyToManagePhotos: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity? = null,
    val showCreateManualStoryDialog: Boolean = false,
    val showMomentsCollageView: Boolean = false,
    val suggestedMemories: List<com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity> = emptyList(),
    val showCustomizeFilterChipsDialog: Boolean = false,
    val activeFilterChips: List<String> = listOf("Lugares", "Documentos", "Ropa", "Eventos", "Pagos", "Mascotas", "Codigos QR"),
    val semanticPhotos: List<com.jhendefr.pixelupia.data.local.room.SmartPhotoEntity> = emptyList(),
    val showSemanticPhotosDialog: Boolean = false,
    val showAddPhotosToCollectionDialog: Boolean = false
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
    data class SelectFaceCluster(val cluster: com.jhendefr.pixelupia.domain.model.FaceCluster?) : GalleryEvent
    data class RenameFaceCluster(val clusterId: Long, val newName: String) : GalleryEvent
    data class AddPhotoToFaceCluster(val clusterId: Long, val photo: Photo) : GalleryEvent
    data class RemovePhotoFromFaceCluster(val clusterId: Long, val photoId: Long) : GalleryEvent
    data class SetClusterCover(val clusterId: Long, val photo: Photo) : GalleryEvent
    data class MergeFaceClusters(val targetClusterId: Long, val sourceClusterId: Long) : GalleryEvent
    data class HideFaceCluster(val clusterId: Long) : GalleryEvent
    data class ToggleAddPhotosToClusterDialog(val show: Boolean) : GalleryEvent
    data class ToggleMergeClusterDialog(val show: Boolean) : GalleryEvent
    data class ToggleChooseCoverDialog(val show: Boolean) : GalleryEvent
    data class SelectFilterType(val filterType: String) : GalleryEvent
    data class RemoveCollagePhoto(val collage: com.jhendefr.pixelupia.domain.model.SmartCollageItem, val photo: Photo) : GalleryEvent
    data class AddCollagePhoto(val collage: com.jhendefr.pixelupia.domain.model.SmartCollageItem) : GalleryEvent
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
    data class RemovePhotosFromSmartCollection(val collection: SmartCollectionItem, val photoIds: List<Long>) : GalleryEvent
    data class AddPhotoToSmartCollection(val collection: SmartCollectionItem, val photo: Photo) : GalleryEvent
    data class ToggleAddPhotosToCollectionDialog(val show: Boolean) : GalleryEvent

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

    // Recuerdos Inteligentes (Smart Moments)
    data class OpenStory(val story: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) : GalleryEvent
    object CloseStory : GalleryEvent
    object GenerateMemories : GalleryEvent
    data class OpenEditStoryName(val story: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity?) : GalleryEvent
    data class RenameStory(val storyId: Long, val newTitle: String) : GalleryEvent
    data class OpenAddPhotosToStory(val story: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity?) : GalleryEvent
    data class AddPhotosToStory(val storyId: Long, val newPhotoIds: List<Long>) : GalleryEvent
    data class OpenManageStoryPhotos(val story: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity?) : GalleryEvent
    data class RemovePhotoFromStory(val storyId: Long, val photoId: Long) : GalleryEvent
    data class ToggleStoryPinned(val storyId: Long, val isPinned: Boolean) : GalleryEvent
    data class DeleteStory(val storyId: Long) : GalleryEvent
    data class ToggleCreateManualStoryDialog(val show: Boolean) : GalleryEvent
    data class CreateManualStory(val title: String, val photoIds: List<Long>) : GalleryEvent
    data class CreateStoryFromSuggestion(val suggestion: com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) : GalleryEvent
    object LoadSuggestedMemories : GalleryEvent
    data class ToggleMomentsCollageView(val show: Boolean) : GalleryEvent
    data class ToggleCustomizeFilterChipsDialog(val show: Boolean) : GalleryEvent
    data class SaveActiveFilterChips(val chips: List<String>) : GalleryEvent
    object ResetFilterChipsToDefault : GalleryEvent
    data class ToggleSemanticPhotosDialog(val show: Boolean) : GalleryEvent
}
