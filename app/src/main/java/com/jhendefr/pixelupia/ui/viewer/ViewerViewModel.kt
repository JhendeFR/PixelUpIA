package com.jhendefr.pixelupia.ui.viewer

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
    val photos: List<Photo> = emptyList(),
    val initialIndex: Int = 0,
    val isLoading: Boolean = true,
    val favoritePhotoIds: Set<Long> = emptySet(),
    val photoPendingAction: Photo? = null,
    val showDeleteConfirm: Boolean = false,
    val showFolderPickerForMove: Boolean = false,
    val showFolderPickerForCopy: Boolean = false,
    val existingFolders: List<String> = emptyList(),
    val userMessage: String? = null,
    val isPhotoDeleted: Boolean = false,
    val pendingIntentSender: IntentSender? = null,
    val isDeletePendingApproval: Boolean = false
)

sealed interface ViewerEvent {
    data class ToggleFavorite(val photoId: Long) : ViewerEvent
    data class RequestDelete(val photo: Photo) : ViewerEvent
    object ConfirmDelete : ViewerEvent
    object DismissDeleteDialog : ViewerEvent
    data class RequestMove(val photo: Photo) : ViewerEvent
    data class ConfirmMove(val targetFolder: String) : ViewerEvent
    object DismissMoveDialog : ViewerEvent
    data class RequestCopy(val photo: Photo) : ViewerEvent
    data class ConfirmCopy(val targetFolder: String) : ViewerEvent
    object DismissCopyDialog : ViewerEvent
    object OnIntentSenderCompleted : ViewerEvent
    object OnIntentSenderDismissed : ViewerEvent
    object ClearUserMessage : ViewerEvent
}

class ViewerViewModel(
    private val initialPhotoId: Long,
    private val albumName: String?,
    private val getPhotosUseCase: GetPhotosUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val deletePhotosUseCase: DeletePhotosUseCase,
    private val movePhotosUseCase: MovePhotosUseCase,
    private val copyPhotosUseCase: CopyPhotosUseCase,
    private val getFavoritePhotoIdsUseCase: GetFavoritePhotoIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
        loadAlbums()
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            getFavoritePhotoIdsUseCase().collect { favs ->
                _uiState.update { it.copy(favoritePhotoIds = favs) }
            }
        }
    }

    fun onEvent(event: ViewerEvent) {
        when (event) {
            is ViewerEvent.ToggleFavorite -> {
                val isFav = toggleFavoriteUseCase(event.photoId)
                _uiState.update {
                    it.copy(userMessage = if (isFav) "Añadida a favoritos" else "Eliminada de favoritos")
                }
            }
            is ViewerEvent.RequestDelete -> {
                _uiState.update { it.copy(photoPendingAction = event.photo, showDeleteConfirm = true) }
            }
            ViewerEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(showDeleteConfirm = false, photoPendingAction = null) }
            }
            ViewerEvent.ConfirmDelete -> {
                val photo = _uiState.value.photoPendingAction ?: return
                _uiState.update { it.copy(showDeleteConfirm = false) }
                viewModelScope.launch {
                    when (val result = deletePhotosUseCase(photo)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update { it.copy(isPhotoDeleted = true, userMessage = "Foto eliminada") }
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    pendingIntentSender = result.intentSender,
                                    isDeletePendingApproval = true
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update { it.copy(userMessage = result.message) }
                        }
                    }
                }
            }
            is ViewerEvent.RequestMove -> {
                _uiState.update { it.copy(photoPendingAction = event.photo, showFolderPickerForMove = true) }
            }
            ViewerEvent.DismissMoveDialog -> {
                _uiState.update { it.copy(showFolderPickerForMove = false, photoPendingAction = null) }
            }
            is ViewerEvent.ConfirmMove -> {
                val photo = _uiState.value.photoPendingAction ?: return
                _uiState.update { it.copy(showFolderPickerForMove = false) }
                viewModelScope.launch {
                    when (val result = movePhotosUseCase(photo, event.targetFolder)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update { it.copy(userMessage = "Foto movida a ${event.targetFolder}") }
                            loadPhotos()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    pendingIntentSender = result.intentSender,
                                    isDeletePendingApproval = true
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update { it.copy(userMessage = result.message) }
                        }
                    }
                }
            }
            is ViewerEvent.RequestCopy -> {
                _uiState.update { it.copy(photoPendingAction = event.photo, showFolderPickerForCopy = true) }
            }
            ViewerEvent.DismissCopyDialog -> {
                _uiState.update { it.copy(showFolderPickerForCopy = false, photoPendingAction = null) }
            }
            is ViewerEvent.ConfirmCopy -> {
                val photo = _uiState.value.photoPendingAction ?: return
                _uiState.update { it.copy(showFolderPickerForCopy = false) }
                viewModelScope.launch {
                    when (val result = copyPhotosUseCase(photo, event.targetFolder)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update { it.copy(userMessage = "Copia guardada en ${event.targetFolder}") }
                            loadPhotos()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update { it.copy(pendingIntentSender = result.intentSender) }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update { it.copy(userMessage = result.message) }
                        }
                    }
                }
            }
            ViewerEvent.OnIntentSenderCompleted -> {
                val wasDelete = _uiState.value.isDeletePendingApproval
                _uiState.update {
                    it.copy(
                        pendingIntentSender = null,
                        isDeletePendingApproval = false,
                        isPhotoDeleted = wasDelete,
                        userMessage = "Operacion completada"
                    )
                }
                loadPhotos()
            }
            ViewerEvent.OnIntentSenderDismissed -> {
                _uiState.update {
                    it.copy(pendingIntentSender = null, isDeletePendingApproval = false)
                }
            }
            ViewerEvent.ClearUserMessage -> {
                _uiState.update { it.copy(userMessage = null) }
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
            getPhotosUseCase().collect { allPhotos ->
                val displayPhotos = if (!albumName.isNullOrEmpty()) {
                    allPhotos.filter { it.folderName == albumName }
                } else {
                    allPhotos
                }

                if (displayPhotos.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, photos = emptyList()) }
                    return@collect
                }

                val index = displayPhotos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        photos = displayPhotos,
                        initialIndex = index,
                        isLoading = false
                    )
                }
            }
        }
    }
}
