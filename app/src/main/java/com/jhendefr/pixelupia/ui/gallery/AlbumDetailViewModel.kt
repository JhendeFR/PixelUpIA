package com.jhendefr.pixelupia.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 1. Estado actualizado
data class AlbumDetailUiState(
    val albumName: String = "",
    val isLoading: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val errorMessage: String? = null,
    val selectedPhotoIds: Set<Long> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedPhotoIds.isNotEmpty()
}

// 2. Eventos actualizados
sealed interface AlbumDetailEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : AlbumDetailEvent
    data class TogglePhotoSelection(val photoId: Long) : AlbumDetailEvent
    object ClearSelection : AlbumDetailEvent
    object Refresh : AlbumDetailEvent
}

// 3. ViewModel
class AlbumDetailViewModel(
    private val albumName: String,
    private val getPhotosUseCase: GetPhotosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState(albumName = albumName))
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
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
            AlbumDetailEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedPhotoIds = emptySet()) }
            }
            AlbumDetailEvent.Refresh -> loadPhotos()
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
