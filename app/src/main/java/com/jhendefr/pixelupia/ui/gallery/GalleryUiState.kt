package com.jhendefr.pixelupia.ui.gallery

import com.jhendefr.pixelupia.domain.model.Album
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder

enum class GalleryTab {
    PHOTOS,
    ALBUMS
}

data class GalleryUiState(
    val isLoading: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val albums: List<Album> = emptyList(),
    val selectedTab: GalleryTab = GalleryTab.PHOTOS,
    val selectedAlbumName: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val errorMessage: String? = null,
    // Nuevas variables para la Selección Múltiple
    val selectedPhotoIds: Set<Long> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedPhotoIds.isNotEmpty()
}

sealed interface GalleryEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : GalleryEvent
    data class SelectTab(val tab: GalleryTab) : GalleryEvent
    data class SelectAlbum(val albumName: String?) : GalleryEvent
    // Nuevos eventos
    data class TogglePhotoSelection(val photoId: Long) : GalleryEvent
    object ClearSelection : GalleryEvent
    object Refresh : GalleryEvent
}
/**
 * Estado y eventos de la UI para la galería de fotos.
 *
 * - GalleryUiState: mantiene indicadores de carga, lista de fotos y álbumes,
 *   pestaña seleccionada, orden de clasificación y posibles errores.
 * - GalleryEvent: define interacciones de usuario como cambiar orden,
 *   seleccionar pestaña o refrescar la galería.
 *
 * Pertenece a la capa de presentación y centraliza el manejo del estado
 * y las acciones de la pantalla de galería.
 */
