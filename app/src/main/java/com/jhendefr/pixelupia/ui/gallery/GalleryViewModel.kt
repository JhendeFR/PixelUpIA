package com.jhendefr.pixelupia.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.domain.usecase.GetAlbumsUseCase
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val getPhotosUseCase: GetPhotosUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadData()
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
            GalleryEvent.Refresh -> loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            combine(
                getPhotosUseCase(_uiState.value.sortOrder),
                getAlbumsUseCase(_uiState.value.sortOrder)
            ) { photos, albums ->
                Pair(photos, albums)
            }
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
                .collect { (photos, albums) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            photos = photos,
                            albums = albums
                        )
                    }
                }
        }
    }
}
/**
 * ViewModel para la pantalla de galería de fotos y álbumes.
 *
 * - Mantiene e expone el estado de la UI mediante StateFlow (GalleryUiState).
 * - Gestiona eventos de usuario como cambiar orden, seleccionar pestaña o refrescar.
 * - Combina GetPhotosUseCase y GetAlbumsUseCase para obtener fotos y álbumes ordenados.
 * - Controla estados de carga y error.
 *
 * Pertenece a la capa de presentación y coordina la lógica entre
 * la UI y la capa de dominio.
 **/
