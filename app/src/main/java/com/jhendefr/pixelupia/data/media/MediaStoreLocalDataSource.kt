package com.jhendefr.pixelupia.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreLocalDataSource (private val context: Context) {
    suspend fun fetchPhotos(): List<Photo> = withContext(Dispatchers.IO){
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        //Puedes modificar el orden del resultado de la consulta
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver.query(
            queryUri,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()){
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Sin_nombre"
                val dateTaken = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val folderName = cursor.getString(folderColumn) ?: "Raiz"
                val mimeType = cursor.getString(mimeColumn) ?: "image/*"
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                photos.add(
                    Photo(
                        id = id,
                        uri = contentUri,
                        name = name,
                        dateTaken = dateTaken,
                        size = size,
                        folderName = folderName,
                        mimeType = mimeType
                    )
                )
            }
        }
        photos
    }

    suspend fun fetchLatestScreenshot(): Photo? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE
        )
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%Screenshot%", "%Screenshot%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Captura"
                val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val folderName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)) ?: "Screenshots"
                val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/png"
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                Photo(
                    id = id,
                    uri = contentUri,
                    name = name,
                    dateTaken = if (dateTaken > 0) dateTaken else System.currentTimeMillis(),
                    size = size,
                    folderName = folderName,
                    mimeType = mimeType
                )
            } else {
                null
            }
        }
    }
}
/**
 * Fuente de datos local que accede al MediaStore de Android para obtener fotos.
 *
 * Utiliza el ContentResolver y consultas al MediaStore para:
 * - Recuperar metadatos de imágenes almacenadas en el dispositivo
 * - Construir objetos Photo a partir de los resultados
 *
 * Las operaciones se ejecutan en Dispatchers.IO mediante corutinas,
 * garantizando que las consultas de entrada/salida no bloqueen el hilo principal.
 *
 * Esta clase forma parte de la capa de datos y sirve como implementación
 * concreta del repositorio definido en la capa de dominio.
 */