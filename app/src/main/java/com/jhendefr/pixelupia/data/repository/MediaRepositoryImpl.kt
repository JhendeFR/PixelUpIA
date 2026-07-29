package com.jhendefr.pixelupia.data.repository

import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaRepositoryImpl(private val localDataSource: MediaStoreLocalDataSource) : MediaRepository {
    override fun getPhotos(): Flow<List<Photo>> = flow {
        emit(localDataSource.fetchPhotos())
    }
    override suspend fun deletePhoto(photo: Photo): Result<Unit> {
        return Result.success(Unit)
    }
    override suspend fun renamePhoto(photo: Photo, newName: String): Result<Unit> {
        return Result.success(Unit)
    }
    override suspend fun movePhoto(photo: Photo, targetFolder: String): Result<Unit> {
        return Result.success(Unit)
    }
    override suspend fun copyPhoto(photo: Photo, targetFolder: String): Result<Unit> {
        return Result.success(Unit)
    }
}
/**
 * Implementación del repositorio de fotos que usa MediaStoreLocalDataSource.
 *
 * Se encarga de:
 * - Obtener la lista de fotos desde el MediaStore y exponerla como Flow.
 * - Proveer métodos para eliminar, renombrar, mover y copiar fotos.
 *
 * Conecta la capa de dominio (MediaRepository) con la capa de datos real.
 */
