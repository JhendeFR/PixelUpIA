package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    SIZE_DESC
}

class GetPhotosUseCase(private val repository: MediaRepository) {
    operator fun invoke(sortOrder: SortOrder = SortOrder.DATE_DESC): Flow<List<Photo>> {
        return repository.getPhotos().map { photos ->
            when (sortOrder) {
                SortOrder.DATE_DESC -> photos.sortedByDescending { it.dateTaken }
                SortOrder.DATE_ASC -> photos.sortedBy { it.dateTaken }
                SortOrder.NAME_ASC -> photos.sortedBy { it.name.lowercase() }
                SortOrder.SIZE_DESC -> photos.sortedByDescending { it.size }
            }
        }
    }
}
/**
 * Caso de uso para obtener y ordenar fotos desde el repositorio.
 *
 * - Define criterios de ordenamiento (fecha, nombre, tamaño).
 * - Consume MediaRepository y devuelve un Flow<List<Photo>> ya ordenado.
 * - Por defecto ordena las fotos por fecha descendente (más recientes primero).
 *
 * Pertenece a la capa de dominio y encapsula la lógica de negocio
 * para la presentación de fotos en la aplicación.
 */
