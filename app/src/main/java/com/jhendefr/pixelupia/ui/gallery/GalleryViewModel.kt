package com.jhendefr.pixelupia.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.data.local.SmartGalleryPreferencesDataSource
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
    private val enrichScreenshotUseCase: EnrichScreenshotUseCase,
    private val getFaceClustersUseCase: com.jhendefr.pixelupia.domain.usecase.GetFaceClustersUseCase? = null,
    private val manageFaceClusterUseCase: com.jhendefr.pixelupia.domain.usecase.ManageFaceClusterUseCase? = null,
    private val getPhotosForClusterUseCase: com.jhendefr.pixelupia.domain.usecase.GetPhotosForClusterUseCase? = null,
    private val smartCollageGenerator: com.jhendefr.pixelupia.domain.usecase.SmartCollageGenerator = com.jhendefr.pixelupia.domain.usecase.SmartCollageGenerator(),
    private val context: android.content.Context? = null,
    private val smartGalleryDao: com.jhendefr.pixelupia.data.local.room.SmartGalleryDao? = null,
    private val generateSmartMemoriesUseCase: com.jhendefr.pixelupia.domain.usecase.GenerateSmartMemoriesUseCase? = null,
    private val managePhotoTagsUseCase: com.jhendefr.pixelupia.domain.usecase.ManagePhotoTagsUseCase? = null
) : ViewModel() {

    private val photoTagsManager = managePhotoTagsUseCase ?: smartGalleryDao?.let { com.jhendefr.pixelupia.domain.usecase.ManagePhotoTagsUseCase(it) }

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")
    private val _searchRefreshTrigger = MutableStateFlow(0L)
    private var lastBannedSearchBatch: Pair<String, List<Long>>? = null

    private var cachedAllAppCollections: List<SmartCollectionItem> = emptyList()
    private var cachedAllVisualCollections: List<SmartCollectionItem> = emptyList()
    private var indexedFoldersJob: Job? = null
    private var clusterPhotosJob: Job? = null
    private var smartCollectionsJob: Job? = null

    init {
        loadData()
        observeSmartPhotos()
        observePreferences()
        observeFaceClusters()
        observeMemoryStories()
        observeSemanticPhotos()
        generateSmartMemories()
    }

    private fun observeSemanticPhotos() {
        if (smartGalleryDao != null) {
            viewModelScope.launch {
                smartGalleryDao.getPhotosWithEmbeddingFlow().collect { list ->
                    _uiState.update { it.copy(semanticPhotos = list) }
                }
            }
        }
    }

    private fun observeMemoryStories() {
        if (smartGalleryDao != null) {
            viewModelScope.launch {
                smartGalleryDao.getAllStories().collect { stories ->
                    _uiState.update { it.copy(memoryStories = stories) }
                }
            }
        }
    }

    private fun generateSmartMemories() {
        val useCase = generateSmartMemoriesUseCase ?: return
        viewModelScope.launch {
            try {
                useCase()
            } catch (_: Exception) {}
        }
    }

    private fun observeSmartPhotos() {
        viewModelScope.launch {
            combine(
                _searchQueryFlow
                    .debounce(280L)
                    .distinctUntilChanged(),
                _searchRefreshTrigger
            ) { query, _ -> query }
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

        viewModelScope.launch {
            preferencesDataSource.activeFilterChipsFlow.collect { chips ->
                _uiState.update { it.copy(activeFilterChips = chips) }
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
        smartCollectionsJob?.cancel()
        smartCollectionsJob = viewModelScope.launch {
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
                _uiState.update {
                    it.copy(
                        selectedTab = event.tab,
                        selectedPhotoIds = emptySet()
                    )
                }
            }
            is GalleryEvent.SelectAlbum -> {
                _uiState.update { it.copy(selectedAlbumName = event.albumName) }
            }
            is GalleryEvent.SelectIndexedFolder -> {
                _uiState.update {
                    it.copy(
                        selectedIndexedFolderName = event.folderName,
                        selectedPhotoIds = emptySet()
                    )
                }
            }
            is GalleryEvent.SelectSmartCollection -> {
                _uiState.update {
                    it.copy(
                        selectedSmartCollection = event.collection,
                        selectedPhotoIds = emptySet()
                    )
                }
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
                    generateSmartMemories()
                }
            }
            GalleryEvent.RebuildAllClustersAndReEnrich -> {
                rebuildAllClustersAndReEnrich()
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
                val changed = _uiState.value.smartSearchQuery != event.query
                _uiState.update {
                    if (changed) {
                        it.copy(
                            smartSearchQuery = event.query,
                            selectedPhotoIds = emptySet()
                        )
                    } else {
                        it.copy(smartSearchQuery = event.query)
                    }
                }
                _searchQueryFlow.value = event.query
            }
            GalleryEvent.SelectAll -> {
                val state = _uiState.value
                val allIds = when (state.selectedTab) {
                    GalleryTab.PHOTOS -> state.photos.map { it.id }.toSet()
                    GalleryTab.ALBUMS -> emptySet()
                    GalleryTab.SMART_AI -> when {
                        state.selectedFaceCluster != null -> state.selectedFaceClusterPhotos.map { it.id }.toSet()
                        state.selectedSmartCollection != null -> {
                            val q = state.smartSearchQuery
                            val list = if (q.isBlank()) {
                                state.selectedSmartCollection.photos
                            } else {
                                state.selectedSmartCollection.photos.filter {
                                    it.name.contains(q, ignoreCase = true) ||
                                        (it.sourceApp?.contains(q, ignoreCase = true) == true)
                                }
                            }
                            list.map { it.id }.toSet()
                        }
                        state.selectedIndexedFolderName != null -> {
                            val folder = state.selectedIndexedFolderName
                            val base = if (state.smartSearchQuery.isEmpty()) state.photos else state.smartPhotos
                            base.filter {
                                it.folderName.equals(folder, ignoreCase = true) ||
                                    it.folderName.contains(folder, ignoreCase = true)
                            }.map { it.id }.toSet()
                        }
                        else -> state.smartPhotos.map { it.id }.toSet()
                    }
                }
                _uiState.update { it.copy(selectedPhotoIds = allIds) }
            }
            GalleryEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedPhotoIds = emptySet()) }
            }
            GalleryEvent.BanSelectedPhotosFromSearch -> {
                banSelectedPhotosFromCurrentSearch()
            }
            GalleryEvent.UndoLastSearchBan -> {
                undoLastSearchBan()
            }
            is GalleryEvent.SelectFaceCluster -> {
                clusterPhotosJob?.cancel()
                if (event.cluster != null) {
                    _uiState.update {
                        it.copy(
                            selectedFaceCluster = event.cluster,
                            selectedSmartCollection = null,
                            selectedIndexedFolderName = null
                        )
                    }
                    clusterPhotosJob = viewModelScope.launch {
                        getPhotosForClusterUseCase?.invoke(event.cluster.id)?.collect { photos ->
                            _uiState.update { it.copy(selectedFaceClusterPhotos = photos) }
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            selectedFaceCluster = null,
                            selectedFaceClusterPhotos = emptyList()
                        )
                    }
                }
            }
            is GalleryEvent.RenameFaceCluster -> {
                viewModelScope.launch {
                    manageFaceClusterUseCase?.renameCluster(event.clusterId, event.newName)
                    _uiState.update { it.copy(userMessage = "Grupo renombrado con exito") }
                }
            }
            is GalleryEvent.AddPhotoToFaceCluster -> {
                viewModelScope.launch {
                    val bitmap = context?.let { ctx ->
                        com.jhendefr.pixelupia.data.ml.ImageUtils.loadCorrectlyOrientedBitmap(ctx, event.photo.uri)
                    }
                    manageFaceClusterUseCase?.addPhotoToCluster(event.photo, event.clusterId, bitmap)
                    _uiState.update { it.copy(userMessage = "Foto agregada al grupo") }
                }
            }
            is GalleryEvent.RemovePhotoFromFaceCluster -> {
                _uiState.update {
                    it.copy(
                        selectedFaceClusterPhotos = it.selectedFaceClusterPhotos.filterNot { p -> p.id == event.photoId }
                    )
                }
                viewModelScope.launch {
                    val photo = _uiState.value.photos.find { it.id == event.photoId }
                    val bitmap = photo?.let { p ->
                        context?.let { ctx ->
                            com.jhendefr.pixelupia.data.ml.ImageUtils.loadCorrectlyOrientedBitmap(ctx, p.uri)
                        }
                    }
                    manageFaceClusterUseCase?.removePhotoFromCluster(event.photoId, event.clusterId, bitmap)
                    _uiState.update { it.copy(userMessage = "Foto quitada del grupo") }
                }
            }
            is GalleryEvent.SetClusterCover -> {
                viewModelScope.launch {
                    val bitmap = context?.let { ctx ->
                        com.jhendefr.pixelupia.data.ml.ImageUtils.loadCorrectlyOrientedBitmap(ctx, event.photo.uri)
                    }
                    manageFaceClusterUseCase?.setClusterCover(event.clusterId, event.photo, bitmap)
                    _uiState.update { state ->
                        val updatedCluster = if (state.selectedFaceCluster?.id == event.clusterId) {
                            state.selectedFaceCluster.copy(coverUri = event.photo.uri)
                        } else {
                            state.selectedFaceCluster
                        }
                        state.copy(
                            selectedFaceCluster = updatedCluster,
                            showChooseCoverDialog = false,
                            userMessage = "Foto de portada actualizada"
                        )
                    }
                }
            }
            is GalleryEvent.MergeFaceClusters -> {
                viewModelScope.launch {
                    manageFaceClusterUseCase?.mergeClusters(event.targetClusterId, event.sourceClusterId)
                    _uiState.update {
                        it.copy(
                            showMergeClusterDialog = false,
                            selectedFaceCluster = null,
                            selectedFaceClusterPhotos = emptyList(),
                            userMessage = "Grupos combinados con exito"
                        )
                    }
                    _searchQueryFlow.value = _uiState.value.smartSearchQuery
                }
            }
            is GalleryEvent.HideFaceCluster -> {
                viewModelScope.launch {
                    manageFaceClusterUseCase?.hideCluster(event.clusterId)
                    _uiState.update {
                        it.copy(
                            selectedFaceCluster = null,
                            selectedFaceClusterPhotos = emptyList(),
                            userMessage = "Grupo ocultado"
                        )
                    }
                    _searchQueryFlow.value = _uiState.value.smartSearchQuery
                }
            }
            is GalleryEvent.ToggleAddPhotosToClusterDialog -> {
                _uiState.update { it.copy(showAddPhotosToClusterDialog = event.show) }
            }
            is GalleryEvent.ToggleMergeClusterDialog -> {
                _uiState.update { it.copy(showMergeClusterDialog = event.show) }
            }
            is GalleryEvent.ToggleChooseCoverDialog -> {
                _uiState.update { it.copy(showChooseCoverDialog = event.show) }
            }
            is GalleryEvent.SelectFilterType -> {
                _uiState.update { it.copy(selectedFilterType = event.filterType) }
            }
            is GalleryEvent.RemoveCollagePhoto -> {
                _uiState.update { state ->
                    val updatedCollages = state.smartCollages.map { c ->
                        if (c.id == event.collage.id) {
                            c.copy(photos = c.photos.filterNot { it.id == event.photo.id })
                        } else c
                    }
                    state.copy(smartCollages = updatedCollages)
                }
            }
            is GalleryEvent.AddCollagePhoto -> {
                _uiState.update { state ->
                    val existingIds = event.collage.photos.map { it.id }.toSet()
                    val candidate = state.photos.firstOrNull { !existingIds.contains(it.id) }
                    if (candidate != null) {
                        val updatedCollages = state.smartCollages.map { c ->
                            if (c.id == event.collage.id) {
                                c.copy(photos = c.photos + candidate)
                            } else c
                        }
                        state.copy(smartCollages = updatedCollages)
                    } else state
                }
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
            is GalleryEvent.RemovePhotosFromSmartCollection -> {
                viewModelScope.launch {
                    val manager = photoTagsManager
                    for (photoId in event.photoIds) {
                        manager?.removeTag(photoId, event.collection.title)
                        if (event.collection.type == com.jhendefr.pixelupia.domain.usecase.SmartCollectionType.SOURCE_APP) {
                            smartGalleryDao?.updatePhotoSourceApp(photoId, "")
                        }
                    }
                    _uiState.update {
                        it.copy(
                            userMessage = if (event.photoIds.size == 1) "Foto quitada del álbum" else "${event.photoIds.size} fotos quitadas del álbum",
                            selectedPhotoIds = emptySet()
                        )
                    }
                }
            }
            is GalleryEvent.AddPhotoToSmartCollection -> {
                viewModelScope.launch {
                    val manager = photoTagsManager
                    manager?.addTag(event.photo.id, event.collection.title)
                    if (event.collection.type == com.jhendefr.pixelupia.domain.usecase.SmartCollectionType.SOURCE_APP) {
                        smartGalleryDao?.updatePhotoSourceApp(event.photo.id, event.collection.title)
                    }
                    _uiState.update {
                        it.copy(
                            userMessage = "Foto añadida a '${event.collection.title}'",
                            showAddPhotosToCollectionDialog = false
                        )
                    }
                }
            }
            is GalleryEvent.ToggleAddPhotosToCollectionDialog -> {
                _uiState.update { it.copy(showAddPhotosToCollectionDialog = event.show) }
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
                _uiState.update { it.copy(userMessage = null, errorMessage = null, canUndoLastSearchBan = false) }
            }

            // Recuerdos Inteligentes (Smart Moments)
            is GalleryEvent.OpenStory -> {
                viewModelScope.launch {
                    val photos = smartGalleryDao?.getPhotosByIds(event.story.photoIds) ?: emptyList()
                    _uiState.update {
                        it.copy(
                            activeStory = event.story,
                            activeStoryPhotos = photos
                        )
                    }
                }
            }
            GalleryEvent.CloseStory -> {
                _uiState.update {
                    it.copy(
                        activeStory = null,
                        activeStoryPhotos = emptyList()
                    )
                }
            }
            GalleryEvent.GenerateMemories -> {
                generateSmartMemories()
            }
            is GalleryEvent.OpenEditStoryName -> {
                _uiState.update { it.copy(storyToEditName = event.story) }
            }
            is GalleryEvent.RenameStory -> {
                viewModelScope.launch {
                    smartGalleryDao?.updateStoryTitle(event.storyId, event.newTitle)
                    _uiState.update { it.copy(storyToEditName = null, userMessage = "Nombre del recuerdo actualizado") }
                }
            }
            is GalleryEvent.OpenAddPhotosToStory -> {
                _uiState.update { it.copy(storyToAddPhotos = event.story) }
            }
            is GalleryEvent.AddPhotosToStory -> {
                viewModelScope.launch {
                    val story = smartGalleryDao?.getStoryById(event.storyId) ?: return@launch
                    val currentPhotos = story.photoIds.toMutableList()
                    event.newPhotoIds.forEach { if (!currentPhotos.contains(it)) currentPhotos.add(it) }
                    val subtitle = "${currentPhotos.size} fotos destacadas"
                    smartGalleryDao.updateStoryPhotos(event.storyId, currentPhotos, subtitle, story.coverUri)
                    _uiState.update { it.copy(storyToAddPhotos = null, userMessage = "Fotos añadidas al recuerdo") }
                    if (_uiState.value.activeStory?.storyId == event.storyId) {
                        val photos = smartGalleryDao.getPhotosByIds(currentPhotos)
                        val updatedStory = smartGalleryDao.getStoryById(event.storyId)
                        _uiState.update { it.copy(activeStory = updatedStory, activeStoryPhotos = photos) }
                    }
                }
            }
            is GalleryEvent.OpenManageStoryPhotos -> {
                _uiState.update { it.copy(storyToManagePhotos = event.story) }
            }
            is GalleryEvent.RemovePhotoFromStory -> {
                viewModelScope.launch {
                    val story = smartGalleryDao?.getStoryById(event.storyId) ?: return@launch
                    val currentPhotos = story.photoIds.toMutableList()
                    currentPhotos.remove(event.photoId)
                    if (currentPhotos.isEmpty()) {
                        smartGalleryDao.deleteStoryById(event.storyId)
                        _uiState.update { it.copy(activeStory = null, activeStoryPhotos = emptyList(), storyToManagePhotos = null, userMessage = "Recuerdo eliminado por falta de fotos") }
                    } else {
                        val newCoverUri = if (story.coverUri.contains(event.photoId.toString())) {
                            val firstPhoto = smartGalleryDao.getPhotoById(currentPhotos.first())
                            firstPhoto?.uri ?: story.coverUri
                        } else {
                            story.coverUri
                        }
                        val subtitle = "${currentPhotos.size} fotos destacadas"
                        smartGalleryDao.updateStoryPhotos(event.storyId, currentPhotos, subtitle, newCoverUri)
                        _uiState.update { it.copy(userMessage = "Foto eliminada del recuerdo") }
                        if (_uiState.value.activeStory?.storyId == event.storyId) {
                            val photos = smartGalleryDao.getPhotosByIds(currentPhotos)
                            val updatedStory = smartGalleryDao.getStoryById(event.storyId)
                            _uiState.update { it.copy(activeStory = updatedStory, activeStoryPhotos = photos) }
                        }
                    }
                }
            }
            is GalleryEvent.ToggleStoryPinned -> {
                viewModelScope.launch {
                    smartGalleryDao?.updateStoryPinned(event.storyId, event.isPinned)
                    val msg = if (event.isPinned) "Recuerdo fijado" else "Recuerdo desfijado"
                    _uiState.update { it.copy(userMessage = msg) }
                }
            }
            is GalleryEvent.DeleteStory -> {
                viewModelScope.launch {
                    smartGalleryDao?.deleteStoryById(event.storyId)
                    _uiState.update {
                        if (it.activeStory?.storyId == event.storyId) {
                            it.copy(activeStory = null, activeStoryPhotos = emptyList(), userMessage = "Recuerdo eliminado")
                        } else {
                            it.copy(userMessage = "Recuerdo eliminado")
                        }
                    }
                }
            }
            is GalleryEvent.ToggleCreateManualStoryDialog -> {
                _uiState.update { it.copy(showCreateManualStoryDialog = event.show) }
                if (event.show) {
                    loadSuggestedMemories()
                }
            }
            GalleryEvent.LoadSuggestedMemories -> {
                loadSuggestedMemories()
            }
            is GalleryEvent.CreateStoryFromSuggestion -> {
                viewModelScope.launch {
                    val storyToSave = event.suggestion.copy(
                        isPinned = true,
                        isManual = true
                    )
                    smartGalleryDao?.insertStory(storyToSave)
                    _uiState.update { state ->
                        state.copy(
                            suggestedMemories = state.suggestedMemories.filterNot { it.title == event.suggestion.title },
                            showCreateManualStoryDialog = false,
                            userMessage = "Recuerdo '${event.suggestion.title}' creado"
                        )
                    }
                }
            }
            is GalleryEvent.CreateManualStory -> {
                viewModelScope.launch {
                    if (event.photoIds.isNotEmpty()) {
                        val firstPhoto = smartGalleryDao?.getPhotoById(event.photoIds.first())
                        val cover = firstPhoto?.uri ?: ""
                        val newStory = com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity(
                            title = event.title.ifBlank { "Mi Recuerdo" },
                            subtitle = "${event.photoIds.size} fotos",
                            coverUri = cover,
                            photoIds = event.photoIds,
                            storyType = "EVENT",
                            isPinned = true,
                            isManual = true
                        )
                        smartGalleryDao?.insertStory(newStory)
                        _uiState.update { it.copy(showCreateManualStoryDialog = false, userMessage = "Recuerdo creado con exito") }
                    }
                }
            }
            is GalleryEvent.ToggleMomentsCollageView -> {
                _uiState.update { it.copy(showMomentsCollageView = event.show) }
            }
            is GalleryEvent.ToggleCustomizeFilterChipsDialog -> {
                _uiState.update { it.copy(showCustomizeFilterChipsDialog = event.show) }
            }
            is GalleryEvent.SaveActiveFilterChips -> {
                preferencesDataSource.saveActiveFilterChips(event.chips)
                _uiState.update { it.copy(showCustomizeFilterChipsDialog = false, userMessage = "Filtros personalizados actualizados") }
            }
            GalleryEvent.ResetFilterChipsToDefault -> {
                preferencesDataSource.resetFilterChipsToDefault()
                _uiState.update { it.copy(showCustomizeFilterChipsDialog = false, userMessage = "Filtros restablecidos") }
            }
            is GalleryEvent.ToggleSemanticPhotosDialog -> {
                _uiState.update { it.copy(showSemanticPhotosDialog = event.show) }
            }
            GalleryEvent.ClearAllInferences -> {
                clearAllInferences()
            }
        }
    }

    private fun loadSuggestedMemories() {
        val useCase = generateSmartMemoriesUseCase ?: return
        viewModelScope.launch {
            try {
                val candidates = useCase.generateCandidateMemories()
                _uiState.update { it.copy(suggestedMemories = candidates) }
            } catch (_: Exception) {}
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
                    val collages = smartCollageGenerator.generateSuggestedCollages(photos)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            photos = photos,
                            albums = albums,
                            favoritePhotoIds = favorites,
                            smartCollages = collages
                        )
                    }
                    observeIndexedFolders(photos, _uiState.value.chosenIndexFolders)
                    observeSmartCollections(photos)
                }
        }
    }

    private fun observeFaceClusters() {
        if (getFaceClustersUseCase == null) return
        viewModelScope.launch {
            getFaceClustersUseCase().collect { clusters ->
                _uiState.update { it.copy(faceClusters = clusters) }
            }
        }
    }

    fun rebuildAllClustersAndReEnrich() {
        viewModelScope.launch {
            manageFaceClusterUseCase?.rebuildAllClustersFromScratch()
                ?: smartGalleryDao?.let { dao ->
                    dao.clearAllPhotoClusterCrossRefs()
                    dao.clearAllFaceClusters()
                    dao.markAllPhotosForReEnrichment()
                }
            context?.let { ctx ->
                com.jhendefr.pixelupia.data.worker.WorkManagerScheduler.triggerOneTimeEnrichment(ctx)
            }
            _uiState.update { it.copy(userMessage = "Re-indexado y clustering reiniciado") }
        }
    }

    private fun banSelectedPhotosFromCurrentSearch() {
        val state = _uiState.value
        val selectedIds = state.selectedPhotoIds.toList()
        val cleanQuery = state.smartSearchQuery.trim()
        if (selectedIds.isEmpty() || cleanQuery.isBlank()) return

        val selectedIdSet = selectedIds.toSet()
        lastBannedSearchBatch = cleanQuery to selectedIds

        // Actualizar optimista en memoria para desaparicion inmediata en la grilla
        _uiState.update {
            it.copy(
                smartPhotos = it.smartPhotos.filterNot { photo -> selectedIdSet.contains(photo.id) },
                selectedPhotoIds = emptySet()
            )
        }

        viewModelScope.launch {
            val result = photoTagsManager?.banPhotosFromSearch(selectedIds, cleanQuery)
            if (result == null || result.isFailure) {
                lastBannedSearchBatch = null
                _searchRefreshTrigger.value += 1
                _uiState.update {
                    it.copy(
                        canUndoLastSearchBan = false,
                        errorMessage = "No se pudieron excluir las imágenes seleccionadas"
                    )
                }
            } else {
                _searchRefreshTrigger.value += 1
                val msg = if (selectedIds.size == 1) {
                    "1 imagen ocultada al buscar '$cleanQuery'"
                } else {
                    "${selectedIds.size} imágenes ocultadas al buscar '$cleanQuery'"
                }
                _uiState.update {
                    it.copy(
                        canUndoLastSearchBan = true,
                        userMessage = msg
                    )
                }
            }
        }
    }

    private fun undoLastSearchBan() {
        val batch = lastBannedSearchBatch ?: return
        lastBannedSearchBatch = null
        val (query, ids) = batch
        viewModelScope.launch {
            photoTagsManager?.unbanPhotosFromSearch(ids, query)
            _searchRefreshTrigger.value += 1
            _uiState.update {
                it.copy(
                    canUndoLastSearchBan = false,
                    userMessage = "Exclusión revertida para '$query'"
                )
            }
        }
    }

    fun clearAllInferences() {
        lastBannedSearchBatch = null
        viewModelScope.launch(Dispatchers.IO) {
            smartGalleryDao?.let { dao ->
                dao.clearAllSmartPhotos()
                dao.clearAllSmartPhotosFts()
                dao.clearAllPhotoClusterCrossRefs()
                dao.clearAllFaceClusters()
                dao.clearAllStories()
            }
            context?.let { ctx ->
                androidx.work.WorkManager.getInstance(ctx).cancelAllWork()
            }
            _uiState.update {
                it.copy(
                    smartPhotos = emptyList(),
                    faceClusters = emptyList(),
                    memoryStories = emptyList(),
                    semanticPhotos = emptyList(),
                    smartSearchQuery = "",
                    selectedPhotoIds = emptySet(),
                    canUndoLastSearchBan = false,
                    userMessage = "Se han limpiado todas las inferencias y datos de la base de datos"
                )
            }
            if (_uiState.value.photos.isNotEmpty()) {
                observeIndexedFolders(_uiState.value.photos, _uiState.value.chosenIndexFolders)
            }
        }
    }
}
