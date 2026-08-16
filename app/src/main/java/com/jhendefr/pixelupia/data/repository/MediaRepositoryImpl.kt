package com.jhendefr.pixelupia.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MediaRepositoryImpl(
    private val context: Context,
    private val localDataSource: MediaStoreLocalDataSource
) : MediaRepository {

    override fun getPhotos(): Flow<List<Photo>> = flow {
        emit(localDataSource.fetchPhotos())
    }

    override suspend fun deletePhoto(photo: Photo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deletedRows = context.contentResolver.delete(photo.uri, null, null)
            if (deletedRows > 0) Result.success(Unit)
            else Result.failure(Exception("No se encontró el archivo para eliminar"))
        } catch (e: SecurityException) {
            // En Android 10+, esto arrojará error si intentas borrar una foto que no creó tu app
            Result.failure(e)
        }
    }

    override suspend fun renamePhoto(photo: Photo, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            }
            val updatedRows = context.contentResolver.update(photo.uri, values, null, null)
            if (updatedRows > 0) Result.success(Unit)
            else Result.failure(Exception("No se pudo renombrar la foto"))
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    override suspend fun movePhoto(photo: Photo, targetFolder: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // A partir de Android 10, podemos mover archivos cambiando su ruta relativa
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$targetFolder")
                }
                val updatedRows = context.contentResolver.update(photo.uri, values, null, null)
                if (updatedRows > 0) Result.success(Unit)
                else Result.failure(Exception("No se pudo mover la foto"))
            } else {
                // En versiones antiguas, hay que copiar el archivo y borrar el original
                val copyResult = copyPhoto(photo, targetFolder)
                if (copyResult.isSuccess) {
                    deletePhoto(photo)
                } else {
                    copyResult
                }
            }
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    override suspend fun copyPhoto(photo: Photo, targetFolder: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Copia_${photo.name}")
                put(MediaStore.Images.Media.MIME_TYPE, photo.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$targetFolder")
                }
            }

            // 1. Creamos el espacio vacío en el MediaStore
            val newUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Error al crear el espacio para la copia"))

            // 2. Leemos los bytes de la foto original y los escribimos en el nuevo espacio
            context.contentResolver.openInputStream(photo.uri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}