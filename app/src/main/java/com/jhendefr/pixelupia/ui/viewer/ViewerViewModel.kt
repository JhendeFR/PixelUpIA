package com.jhendefr.pixelupia.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
    val photos: List<Photo> = emptyList(),
    val initialIndex: Int = 0,
    val isLoading: Boolean = true
)

class ViewerViewModel(
    private val initialPhotoId: Long,
    private val albumName: String?, // Nulo si venimos de la galería principal
    private val getPhotosUseCase: GetPhotosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            getPhotosUseCase().collect { allPhotos ->
                // Filtramos si venimos de un álbum en específico
                val displayPhotos = if (!albumName.isNullOrEmpty()) {
                    allPhotos.filter { it.folderName == albumName }
                } else {
                    allPhotos
                }

                if (displayPhotos.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, photos = emptyList()) }
                    return@collect
                }

                // Buscamos el índice de la foto que el usuario tocó
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
