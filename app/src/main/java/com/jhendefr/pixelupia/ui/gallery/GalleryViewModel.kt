package com.jhendefr.pixelupia.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.usecase.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    private val getPhotosUseCase: GetPhotosUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val deletePhotosUseCase: DeletePhotosUseCase,
    private val movePhotosUseCase: MovePhotosUseCase,
    private val copyPhotosUseCase: CopyPhotosUseCase,
    private val getFavoritePhotoIdsUseCase: GetFavoritePhotoIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val searchSmartPhotosUseCase: SearchSmartPhotosUseCase,
    private val getIndexedFoldersUseCase: GetIndexedFoldersUseCase,
    private val processFolderOcrUseCase: ProcessFolderOcrUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")

    init {
        loadData()
        observeSmartPhotos()
    }

    private fun observeSmartPhotos() {
        viewModelScope.launch {
            _searchQueryFlow
                .flatMapLatest { query ->
                    searchSmartPhotosUseCase(query)
                }
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
                .collect { smartPhotos ->
                    _uiState.update { it.copy(smartPhotos = smartPhotos) }
                }
        }
    }

    private fun observeIndexedFolders(photos: List<com.jhendefr.pixelupia.domain.model.Photo>) {
        viewModelScope.launch {
            getIndexedFoldersUseCase(photos)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
                .collect { folders ->
                    _uiState.update { it.copy(indexedFolders = folders) }
                }
        }
    }

    fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.ChangeSortOrder -> {
                _uiState.update { it.copy(sortOrder = event.newOrder) }
                loadData()
            }
            is GalleryEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is GalleryEvent.SelectAlbum -> {
                _uiState.update { it.copy(selectedAlbumName = event.albumName) }
            }
            is GalleryEvent.SelectIndexedFolder -> {
                _uiState.update { it.copy(selectedIndexedFolderName = event.folderName) }
            }
            is GalleryEvent.TriggerBatchIndexFolder -> {
                val folderPhotos = _uiState.value.photos.filter {
                    it.folderName.equals(event.folderName, ignoreCase = true) ||
                            it.folderName.contains(event.folderName, ignoreCase = true)
                }
                _uiState.update { it.copy(isBatchIndexing = true) }
                viewModelScope.launch {
                    processFolderOcrUseCase(folderPhotos) { current, total ->
                        _uiState.update { it.copy(batchIndexingProgress = Pair(current, total)) }
                    }
                    _uiState.update {
                        it.copy(
                            isBatchIndexing = false,
                            batchIndexingProgress = null,
                            userMessage = "Indexacion OCR completada"
                        )
                    }
                }
            }
            is GalleryEvent.TogglePhotoSelection -> {
                val currentSelection = _uiState.value.selectedPhotoIds.toMutableSet()
                if (currentSelection.contains(event.photoId)) {
                    currentSelection.remove(event.photoId)
                } else {
                    currentSelection.add(event.photoId)
                }
                _uiState.update { it.copy(selectedPhotoIds = currentSelection) }
            }
            is GalleryEvent.ToggleFavorite -> {
                val isFav = toggleFavoriteUseCase(event.photoId)
                _uiState.update {
                    it.copy(userMessage = if (isFav) "Añadida a favoritos" else "Eliminada de favoritos")
                }
            }
            is GalleryEvent.UpdateSmartSearchQuery -> {
                _uiState.update { it.copy(smartSearchQuery = event.query) }
                _searchQueryFlow.value = event.query
            }
            GalleryEvent.SelectAll -> {
                val allIds = when (_uiState.value.selectedTab) {
                    GalleryTab.PHOTOS -> _uiState.value.photos.map { it.id }.toSet()
                    GalleryTab.SMART_AI -> _uiState.value.smartPhotos.map { it.id }.toSet()
                    GalleryTab.ALBUMS -> emptySet()
                }
                _uiState.update { it.copy(selectedPhotoIds = allIds) }
            }
            GalleryEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedPhotoIds = emptySet()) }
            }
            GalleryEvent.Refresh -> {
                loadData()
                _searchQueryFlow.value = _uiState.value.smartSearchQuery
            }

            // Eliminacion
            GalleryEvent.RequestDeleteSelected -> {
                if (_uiState.value.selectedPhotoIds.isNotEmpty()) {
                    _uiState.update { it.copy(showDeleteConfirm = true) }
                }
            }
            GalleryEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(showDeleteConfirm = false) }
            }
            GalleryEvent.ConfirmDeleteSelected -> {
                val photosToDelete = _uiState.value.selectedPhotos
                _uiState.update { it.copy(showDeleteConfirm = false, isProcessingAction = true) }
                viewModelScope.launch {
                    when (val result = deletePhotosUseCase(photosToDelete)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    selectedPhotoIds = emptySet(),
                                    userMessage = "${photosToDelete.size} fotos eliminadas"
                                )
                            }
                            loadData()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    pendingIntentSender = result.intentSender
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                    }
                }
            }

            // Mover
            GalleryEvent.RequestMoveSelected -> {
                if (_uiState.value.selectedPhotoIds.isNotEmpty()) {
                    _uiState.update { it.copy(showFolderPickerForMove = true) }
                }
            }
            GalleryEvent.DismissMoveDialog -> {
                _uiState.update { it.copy(showFolderPickerForMove = false) }
            }
            is GalleryEvent.ConfirmMoveSelected -> {
                val photosToMove = _uiState.value.selectedPhotos
                _uiState.update { it.copy(showFolderPickerForMove = false, isProcessingAction = true) }
                viewModelScope.launch {
                    when (val result = movePhotosUseCase(photosToMove, event.targetFolder)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    selectedPhotoIds = emptySet(),
                                    userMessage = "${photosToMove.size} fotos movidas a ${event.targetFolder}"
                                )
                            }
                            loadData()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    pendingIntentSender = result.intentSender
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                    }
                }
            }

            // Copiar
            GalleryEvent.RequestCopySelected -> {
                if (_uiState.value.selectedPhotoIds.isNotEmpty()) {
                    _uiState.update { it.copy(showFolderPickerForCopy = true) }
                }
            }
            GalleryEvent.DismissCopyDialog -> {
                _uiState.update { it.copy(showFolderPickerForCopy = false) }
            }
            is GalleryEvent.ConfirmCopySelected -> {
                val photosToCopy = _uiState.value.selectedPhotos
                _uiState.update { it.copy(showFolderPickerForCopy = false, isProcessingAction = true) }
                viewModelScope.launch {
                    when (val result = copyPhotosUseCase(photosToCopy, event.targetFolder)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    selectedPhotoIds = emptySet(),
                                    userMessage = "${photosToCopy.size} fotos copiadas a ${event.targetFolder}"
                                )
                            }
                            loadData()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    pendingIntentSender = result.intentSender
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isProcessingAction = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                    }
                }
            }

            GalleryEvent.OnIntentSenderCompleted -> {
                _uiState.update {
                    it.copy(
                        pendingIntentSender = null,
                        selectedPhotoIds = emptySet(),
                        userMessage = "Operacion completada con exito"
                    )
                }
                loadData()
            }
            GalleryEvent.OnIntentSenderDismissed -> {
                _uiState.update {
                    it.copy(pendingIntentSender = null)
                }
            }
            GalleryEvent.ClearUserMessage -> {
                _uiState.update { it.copy(userMessage = null, errorMessage = null) }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            combine(
                getPhotosUseCase(_uiState.value.sortOrder),
                getAlbumsUseCase(_uiState.value.sortOrder),
                getFavoritePhotoIdsUseCase()
            ) { photos, albums, favorites ->
                Triple(photos, albums, favorites)
            }
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
                .collect { (photos, albums, favorites) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            photos = photos,
                            albums = albums,
                            favoritePhotoIds = favorites
                        )
                    }
                    observeIndexedFolders(photos)
                }
        }
    }
}
