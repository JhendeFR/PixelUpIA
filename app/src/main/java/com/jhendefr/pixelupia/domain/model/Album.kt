package com.jhendefr.pixelupia.domain.model

import android.net.Uri

data class Album(
    val name: String,
    val coverUri: Uri,
    val photoCount: Int,
    val lastModified: Long,
    val totalSize: Long
)
/**
 * Modelo de dominio que representa un álbum de fotos.
 *
 * - name: nombre del álbum.
 * - coverUri: URI de la imagen de portada.
 * - photoCount: cantidad de fotos que contiene.
 * - lastModified: timestamp de la foto más reciente.
 * - totalSize: tamaño total en bytes de todas las fotos del álbum.
 *
 * Se utiliza en la capa de dominio para organizar y mostrar colecciones
 * de fotos dentro de la aplicación.
 */
