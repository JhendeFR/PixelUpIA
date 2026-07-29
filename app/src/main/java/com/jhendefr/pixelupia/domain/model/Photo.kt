package com.jhendefr.pixelupia.domain.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,
    val size: Long,
    val folderName: String,
    val mimeType: String
)
/**
 * Representa el modelo de dominio para una foto dentro de la aplicación.
 *
 * Contiene los metadatos principales de una imagen obtenida del dispositivo:
 * - id: identificador único en MediaStore
 * - uri: referencia al recurso multimedia
 * - name: nombre del archivo
 * - dateTaken: fecha en que fue tomada (timestamp)
 * - size: tamaño en bytes
 * - folderName: carpeta/álbum donde está almacenada
 * - mimeType: tipo de archivo (ej. image/jpeg)
 *
 * Se utiliza en la capa de dominio para casos de uso relacionados con
 * la gestión y visualización de fotos.
 */
