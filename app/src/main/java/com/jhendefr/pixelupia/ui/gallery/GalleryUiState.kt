package com.jhendefr.pixelupia.ui.gallery

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.SortOrder

data class GalleryUiState(
    val isLoading: Boolean = true,
    val photos: List<Photo> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val errorMessage: String? = null
)
sealed interface GalleryEvent {
    data class ChangeSortOrder(val newOrder: SortOrder) : GalleryEvent
    object Refresh : GalleryEvent
}
/**
 * Define el estado de la UI para la galería de fotos.
 *
 * - GalleryUiState: contiene indicadores de carga, lista de fotos,
 *   orden de clasificación y posibles mensajes de error.
 * - GalleryEvent: representa eventos de la UI como cambiar el orden
 *   de las fotos o refrescar la galería.
 *
 * Pertenece a la capa de presentación y se usa para manejar el estado
 * y las interacciones del usuario en la pantalla de galería.
 */
