package com.jhendefr.pixelupia.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(private val getPhotosUseCase: GetPhotosUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
    }

    fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.ChangeSortOrder -> {
                _uiState.update { it.copy(sortOrder = event.newOrder) }
                loadPhotos()
            }
            GalleryEvent.Refresh -> loadPhotos()
        }
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            getPhotosUseCase(_uiState.value.sortOrder)
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
                .collect { photoList ->
                    _uiState.update {
                        it.copy(isLoading = false, photos = photoList)
                    }
                }
        }
    }
}
/**
 * ViewModel para la pantalla de galería de fotos.
 *
 * - Mantiene y expone el estado de la UI mediante StateFlow (GalleryUiState).
 * - Gestiona eventos de usuario como cambiar el orden de las fotos o refrescar la galería.
 * - Usa GetPhotosUseCase para obtener y ordenar fotos desde el repositorio.
 * - Controla estados de carga y errores durante la consulta.
 *
 * Pertenece a la capa de presentación y coordina la lógica entre
 * la UI y la capa de dominio.
 */
