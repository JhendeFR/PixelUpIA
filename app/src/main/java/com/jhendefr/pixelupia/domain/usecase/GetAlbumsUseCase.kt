package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.Album
import com.jhendefr.pixelupia.domain.model.SortOrder
import com.jhendefr.pixelupia.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetAlbumsUseCase(
    private val repository: MediaRepository
) {
    operator fun invoke(sortOrder: SortOrder = SortOrder.NAME_ASC): Flow<List<Album>> {
        return repository.getPhotos().map { photos ->
            photos.groupBy { it.folderName }
                .map { (folderName, photosInFolder) ->
                    Album(
                        name = folderName,
                        coverUri = photosInFolder.first().uri,
                        photoCount = photosInFolder.size,
                        lastModified = photosInFolder.maxOfOrNull { it.dateTaken } ?: 0L,
                        totalSize = photosInFolder.sumOf { it.size }
                    )
                }
                .let { albums ->
                    when (sortOrder) {
                        SortOrder.DATE_DESC -> albums.sortedByDescending { it.lastModified }
                        SortOrder.DATE_ASC -> albums.sortedBy { it.lastModified }
                        SortOrder.NAME_ASC -> albums.sortedBy { it.name.lowercase() }
                        SortOrder.NAME_DESC -> albums.sortedByDescending { it.name.lowercase() }
                        SortOrder.SIZE_DESC -> albums.sortedByDescending { it.totalSize }
                        SortOrder.SIZE_ASC -> albums.sortedBy { it.totalSize }
                    }
                }
        }
    }
}
/**
 * Caso de uso para obtener y organizar álbumes de fotos.
 *
 * - Agrupa las fotos del repositorio según su carpeta (folderName).
 * - Construye objetos Album con nombre, portada, cantidad de fotos, fecha y tamaño.
 * - Devuelve un Flow<List<Album>> ordenado según el criterio recibido.
 *
 * Pertenece a la capa de dominio y encapsula la lógica de negocio
 * para mostrar colecciones de fotos como álbumes.
 */
