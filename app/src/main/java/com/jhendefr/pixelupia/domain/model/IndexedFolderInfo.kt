package com.jhendefr.pixelupia.domain.model

import android.net.Uri

data class IndexedFolderInfo(
    val name: String,
    val totalPhotos: Int,
    val indexedPhotos: Int,
    val isProcessing: Boolean = false,
    val coverUri: Uri? = null
) {
    val progress: Float
        get() = if (totalPhotos > 0) (indexedPhotos.toFloat() / totalPhotos.toFloat()).coerceIn(0f, 1f) else 1f

    val isCompleted: Boolean
        get() = totalPhotos > 0 && indexedPhotos >= totalPhotos
}
