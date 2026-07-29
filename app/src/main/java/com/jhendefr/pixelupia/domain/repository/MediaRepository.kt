package com.jhendefr.pixelupia.domain.repository

import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getPhotos(): Flow<List<Photo>>
    suspend fun deletePhoto(photo: Photo): Result<Unit>
    suspend fun renamePhoto(photo: Photo, newName: String): Result<Unit>
    suspend fun movePhoto(photo: Photo, targetFolder: String): Result<Unit>
    suspend fun copyPhoto(photo: Photo, targetFolder: String): Result<Unit>
}
/**
 * Contrato de repositorio para la gestión de fotos en la aplicación.
 *
 * Define las operaciones disponibles sobre el recurso Photo:
 * - getPhotos(): obtiene un flujo reactivo de la lista de fotos
 * - deletePhoto(): elimina una foto
 * - renamePhoto(): renombra una foto
 * - movePhoto(): mueve una foto a otra carpeta
 * - copyPhoto(): copia una foto a otra carpeta
 *
 * Esta interfaz pertenece a la capa de dominio y abstrae la lógica
 * de acceso a datos, permitiendo que la implementación concreta
 * (local o remota) se defina en la capa de datos.
 */
