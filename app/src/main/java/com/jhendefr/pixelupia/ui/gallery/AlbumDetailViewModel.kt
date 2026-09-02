package com.jhendefr.pixelupia.ui.gallery

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder
import com.jhendefr.pixelupia.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val albumName: String = "",
    val isLoading: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val existingFolders: List<String> = emptyList(),
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
}

sealed interface AlbumDetailEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : AlbumDetailEvent
    data class TogglePhotoSelection(val photoId: Long) : AlbumDetailEvent
    object SelectAll : AlbumDetailEvent
    object ClearSelection : AlbumDetailEvent
    object Refresh : AlbumDetailEvent

    object RequestDeleteSelected : AlbumDetailEvent
    object ConfirmDeleteSelected : AlbumDetailEvent
    object DismissDeleteDialog : AlbumDetailEvent

    object RequestMoveSelected : AlbumDetailEvent
    data class ConfirmMoveSelected(val targetFolder: String) : AlbumDetailEvent
    object DismissMoveDialog : AlbumDetailEvent

    object RequestCopySelected : AlbumDetailEvent
    data class ConfirmCopySelected(val targetFolder: String) : AlbumDetailEvent
    object DismissCopyDialog : AlbumDetailEvent

    object OnIntentSenderCompleted : AlbumDetailEvent
    object OnIntentSenderDismissed : AlbumDetailEvent
    object ClearUserMessage : AlbumDetailEvent
}

class AlbumDetailViewModel(
    private val albumName: String,
    private val getPhotosUseCase: GetPhotosUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val deletePhotosUseCase: DeletePhotosUseCase,
    private val movePhotosUseCase: MovePhotosUseCase,
    private val copyPhotosUseCase: CopyPhotosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState(albumName = albumName))
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
        loadAlbums()
    }

    fun onEvent(event: AlbumDetailEvent) {
        when (event) {
            is AlbumDetailEvent.ChangeSortOrder -> {
                _uiState.update { it.copy(sortOrder = event.newOrder) }
                loadPhotos()
            }
            is AlbumDetailEvent.TogglePhotoSelection -> {
                val currentSelection = _uiState.value.selectedPhotoIds.toMutableSet()
                if (currentSelection.contains(event.photoId)) {
                    currentSelection.remove(event.photoId)
                } else {
                    currentSelection.add(event.photoId)
                }
                _uiState.update { it.copy(selectedPhotoIds = currentSelection) }
            }
            AlbumDetailEvent.SelectAll -> {
                val allIds = _uiState.value.photos.map { it.id }.toSet()
                _uiState.update { it.copy(selectedPhotoIds = allIds) }
            }
            AlbumDetailEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedPhotoIds = emptySet()) }
            }
            AlbumDetailEvent.Refresh -> {
                loadPhotos()
                loadAlbums()
            }

            // Eliminar
            AlbumDetailEvent.RequestDeleteSelected -> {
                if (_uiState.value.selectedPhotoIds.isNotEmpty()) {
                    _uiState.update { it.copy(showDeleteConfirm = true) }
                }
            }
            AlbumDetailEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(showDeleteConfirm = false) }
            }
            AlbumDetailEvent.ConfirmDeleteSelected -> {
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
                            loadPhotos()
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
            AlbumDetailEvent.RequestMoveSelected -> {
                if (_uiState.value.selectedPhotoIds.isNotEmpty()) {
                    _uiState.update { it.copy(showFolderPickerForMove = true) }
                }
            }
            AlbumDetailEvent.DismissMoveDialog -> {
                _uiState.update { it.copy(showFolderPickerForMove = false) }
            }
            is AlbumDetailEvent.ConfirmMoveSelected -> {
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
                            loadPhotos()
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
            AlbumDetailEvent.RequestCopySelected -> {
                if (_uiState.value.selectedPhotoIds.isNotEmpty()) {
                    _uiState.update { it.copy(showFolderPickerForCopy = true) }
                }
            }
            AlbumDetailEvent.DismissCopyDialog -> {
                _uiState.update { it.copy(showFolderPickerForCopy = false) }
            }
            is AlbumDetailEvent.ConfirmCopySelected -> {
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
                            loadPhotos()
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

            AlbumDetailEvent.OnIntentSenderCompleted -> {
                _uiState.update {
                    it.copy(
                        pendingIntentSender = null,
                        selectedPhotoIds = emptySet(),
                        userMessage = "Operacion completada con exito"
                    )
                }
                loadPhotos()
            }
            AlbumDetailEvent.OnIntentSenderDismissed -> {
                _uiState.update { it.copy(pendingIntentSender = null) }
            }
            AlbumDetailEvent.ClearUserMessage -> {
                _uiState.update { it.copy(userMessage = null, errorMessage = null) }
            }
        }
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            getAlbumsUseCase().collect { albums ->
                val names = albums.map { it.name }.distinct()
                _uiState.update { it.copy(existingFolders = names) }
            }
        }
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getPhotosUseCase(_uiState.value.sortOrder)
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
                .collect { allPhotos ->
                    val filteredPhotos = allPhotos.filter { it.folderName == albumName }
                    _uiState.update { it.copy(isLoading = false, photos = filteredPhotos) }
                }
        }
    }
}
