package com.jhendefr.pixelupia.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.data.local.SmartGalleryPreferencesDataSource
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    private val processFolderOcrUseCase: ProcessFolderOcrUseCase,
    private val enrichFolderScreenshotsUseCase: EnrichFolderScreenshotsUseCase,
    private val getSmartCollectionsUseCase: GetSmartCollectionsUseCase,
    private val preferencesDataSource: SmartGalleryPreferencesDataSource,
    private val unindexPhotoUseCase: UnindexPhotoUseCase,
    private val enrichScreenshotUseCase: EnrichScreenshotUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")

    private var cachedAllAppCollections: List<SmartCollectionItem> = emptyList()
    private var cachedAllVisualCollections: List<SmartCollectionItem> = emptyList()
    private var indexedFoldersJob: Job? = null

    init {
        loadData()
        observeSmartPhotos()
        observePreferences()
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

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesDataSource.hiddenCollectionsFlow.collect { hiddenIds ->
                val allCollections = cachedAllAppCollections + cachedAllVisualCollections
                _uiState.update { state ->
                    state.copy(
                        hiddenCollectionIds = hiddenIds,
                        appCollections = cachedAllAppCollections.filterNot { hiddenIds.contains(it.id) },
                        visualCollections = cachedAllVisualCollections.filterNot { hiddenIds.contains(it.id) },
                        hiddenCollections = allCollections.filter { hiddenIds.contains(it.id) }
                    )
                }
            }
        }

        viewModelScope.launch {
            preferencesDataSource.chosenFoldersFlow.collect { chosenFolders ->
                _uiState.update { it.copy(chosenIndexFolders = chosenFolders) }
                if (_uiState.value.photos.isNotEmpty()) {
                    observeIndexedFolders(_uiState.value.photos, chosenFolders)
                }
            }
        }

        viewModelScope.launch {
            preferencesDataSource.showIndexedCountFlow.collect { show ->
                _uiState.update { it.copy(showIndexedCount = show) }
            }
        }
    }

    private fun observeIndexedFolders(photos: List<Photo>, chosenFolders: Set<String> = _uiState.value.chosenIndexFolders) {
        indexedFoldersJob?.cancel()
        indexedFoldersJob = viewModelScope.launch {
            getIndexedFoldersUseCase(photos, chosenFolders)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
                .collect { folders ->
                    _uiState.update { it.copy(indexedFolders = folders) }
                }
        }
    }

    private fun observeSmartCollections(photos: List<Photo>) {
        viewModelScope.launch {
            getSmartCollectionsUseCase(photos)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
                .collect { collections ->
                    cachedAllAppCollections = collections.appCollections
                    cachedAllVisualCollections = collections.visualCollections
                    val hiddenIds = _uiState.value.hiddenCollectionIds
                    val all = collections.appCollections + collections.visualCollections
                    _uiState.update {
                        it.copy(
                            appCollections = collections.appCollections.filterNot { item -> hiddenIds.contains(item.id) },
                            visualCollections = collections.visualCollections.filterNot { item -> hiddenIds.contains(item.id) },
                            hiddenCollections = all.filter { item -> hiddenIds.contains(item.id) }
                        )
                    }
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
            is GalleryEvent.SelectSmartCollection -> {
                _uiState.update { it.copy(selectedSmartCollection = event.collection) }
            }
            is GalleryEvent.TriggerBatchIndexFolder -> {
                val folderPhotos = _uiState.value.photos.filter {
                    it.folderName.equals(event.folderName, ignoreCase = true)
                }
                _uiState.update { it.copy(isBatchIndexing = true, activeIndexingFolder = event.folderName) }
                viewModelScope.launch {
                    processFolderOcrUseCase(folderPhotos) { current, total ->
                        _uiState.update { it.copy(batchIndexingProgress = Pair(current, total)) }
                    }
                    _uiState.update {
                        it.copy(
                            isBatchIndexing = false,
                            batchIndexingProgress = null,
                            activeIndexingFolder = null,
                            userMessage = "Indexacion OCR completada para ${event.folderName}"
                        )
                    }
                }
            }
            is GalleryEvent.TriggerFolderMultimodalEnrichment -> {
                val folderPhotos = _uiState.value.photos.filter {
                    it.folderName.equals(event.folderName, ignoreCase = true)
                }
                _uiState.update { it.copy(isFolderEnriching = true, activeEnrichingFolder = event.folderName) }
                viewModelScope.launch {
                    enrichFolderScreenshotsUseCase(folderPhotos) { current, total ->
                        _uiState.update { it.copy(folderEnrichingProgress = Pair(current, total)) }
                    }
                    _uiState.update {
                        it.copy(
                            isFolderEnriching = false,
                            folderEnrichingProgress = null,
                            activeEnrichingFolder = null,
                            userMessage = "Analisis inteligente completado para ${event.folderName}"
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

            // Gestion de Colecciones Ocultas
            is GalleryEvent.HideCollection -> {
                preferencesDataSource.hideCollection(event.collectionId)
                _uiState.update { it.copy(userMessage = "Coleccion ocultada") }
            }
            is GalleryEvent.UnhideCollection -> {
                preferencesDataSource.unhideCollection(event.collectionId)
                _uiState.update { it.copy(userMessage = "Coleccion restaurada") }
            }
            is GalleryEvent.ToggleHiddenCollectionsDialog -> {
                _uiState.update { it.copy(showHiddenCollectionsDialog = event.show) }
            }

            // Carpetas de Indexacion Dinamicas
            is GalleryEvent.ToggleFolderManagerDialog -> {
                _uiState.update { it.copy(showFolderManagerDialog = event.show) }
            }
            is GalleryEvent.ToggleFolderIndexing -> {
                preferencesDataSource.toggleFolderIndexing(event.folderName)
            }
            GalleryEvent.ToggleShowIndexedCount -> {
                val newState = preferencesDataSource.toggleShowIndexedCount()
                _uiState.update {
                    it.copy(userMessage = if (newState) "Conteo de indexadas visible" else "Conteo de indexadas oculto")
                }
            }

            // Indexar y Desindexar Individualmente
            is GalleryEvent.UnindexPhoto -> {
                viewModelScope.launch {
                    val result = unindexPhotoUseCase(event.photoId)
                    result.onSuccess {
                        _uiState.update { it.copy(userMessage = "Foto desindexada de IA con exito") }
                    }.onFailure { err ->
                        _uiState.update { it.copy(errorMessage = "Error al desindexar: ${err.message}") }
                    }
                }
            }
            is GalleryEvent.IndexPhoto -> {
                viewModelScope.launch {
                    val result = enrichScreenshotUseCase(event.photo)
                    result.onSuccess {
                        _uiState.update { it.copy(userMessage = "Foto indexada con IA con exito") }
                    }.onFailure { err ->
                        _uiState.update { it.copy(errorMessage = "Error al indexar: ${err.message}") }
                    }
                }
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
                    observeIndexedFolders(photos, _uiState.value.chosenIndexFolders)
                    observeSmartCollections(photos)
                }
        }
    }
}
