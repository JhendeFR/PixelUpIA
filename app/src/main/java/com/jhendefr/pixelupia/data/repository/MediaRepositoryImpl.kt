package com.jhendefr.pixelupia.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
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

    private fun getRelativePathForFolder(folderName: String): String {
        val clean = folderName.trim().trim('/')
        return when {
            clean.equals("Camera", ignoreCase = true) -> "DCIM/Camera/"
            clean.equals("Screenshots", ignoreCase = true) -> "Pictures/Screenshots/"
            clean.startsWith("DCIM", ignoreCase = true) -> "$clean/"
            clean.startsWith("Pictures", ignoreCase = true) -> "$clean/"
            else -> "Pictures/$clean/"
        }
    }

    override suspend fun deletePhoto(photo: Photo): MediaOperationResult {
        return deletePhotos(listOf(photo))
    }

    override suspend fun deletePhotos(photos: List<Photo>): MediaOperationResult = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext MediaOperationResult.Success
        val uris = photos.map { it.uri }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            return@withContext MediaOperationResult.RequiresIntentSender(pendingIntent.intentSender)
        } else {
            var recoverableException: RecoverableSecurityException? = null
            var deletedCount = 0
            for (photo in photos) {
                try {
                    val count = context.contentResolver.delete(photo.uri, null, null)
                    if (count > 0) deletedCount++
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        recoverableException = e
                        break
                    }
                } catch (e: Exception) {
                    // Continuar con los demas
                }
            }
            if (recoverableException != null) {
                return@withContext MediaOperationResult.RequiresIntentSender(recoverableException.userAction.actionIntent.intentSender)
            }
            if (deletedCount > 0) {
                MediaOperationResult.Success
            } else {
                MediaOperationResult.Failure("No se pudo eliminar el archivo")
            }
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun copyPhoto(photo: Photo, targetFolder: String): MediaOperationResult = withContext(Dispatchers.IO) {
        try {
            val relativePath = getRelativePathForFolder(targetFolder)
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Copia_${photo.name}")
                put(MediaStore.Images.Media.MIME_TYPE, photo.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val newUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext MediaOperationResult.Failure("No se pudo crear el archivo en $relativePath")

            context.contentResolver.openInputStream(photo.uri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("No se pudo leer el archivo de origen")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(newUri, updateValues, null, null)
            }
            MediaOperationResult.Success
        } catch (e: Exception) {
            MediaOperationResult.Failure(e.message ?: "Error al copiar la foto")
        }
    }

    override suspend fun copyPhotos(photos: List<Photo>, targetFolder: String): MediaOperationResult = withContext(Dispatchers.IO) {
        var failureCount = 0
        var lastErrorMessage = ""
        for (photo in photos) {
            val result = copyPhoto(photo, targetFolder)
            if (result is MediaOperationResult.Failure) {
                failureCount++
                lastErrorMessage = result.message
            }
        }
        if (failureCount == 0) {
            MediaOperationResult.Success
        } else {
            MediaOperationResult.Failure(if (lastErrorMessage.isNotEmpty()) lastErrorMessage else "Error al copiar algunas fotos")
        }
    }

    override suspend fun movePhoto(photo: Photo, targetFolder: String): MediaOperationResult = withContext(Dispatchers.IO) {
        // En Scoped Storage moderno, copiar a la nueva carpeta y luego solicitar la eliminacion del original es la estrategia mas compatible
        val copyResult = copyPhoto(photo, targetFolder)
        if (copyResult is MediaOperationResult.Success) {
            deletePhoto(photo)
        } else {
            copyResult
        }
    }

    override suspend fun movePhotos(photos: List<Photo>, targetFolder: String): MediaOperationResult = withContext(Dispatchers.IO) {
        var failureCount = 0
        var lastErrorMessage = ""
        val successfullyCopied = mutableListOf<Photo>()

        for (photo in photos) {
            val copyResult = copyPhoto(photo, targetFolder)
            if (copyResult is MediaOperationResult.Success) {
                successfullyCopied.add(photo)
            } else if (copyResult is MediaOperationResult.Failure) {
                failureCount++
                lastErrorMessage = copyResult.message
            }
        }

        if (successfullyCopied.isNotEmpty()) {
            val deleteResult = deletePhotos(successfullyCopied)
            if (deleteResult is MediaOperationResult.RequiresIntentSender) {
                return@withContext deleteResult
            }
        }

        if (failureCount == 0) {
            MediaOperationResult.Success
        } else {
            MediaOperationResult.Failure(if (lastErrorMessage.isNotEmpty()) lastErrorMessage else "Error al mover algunas fotos")
        }
    }
}