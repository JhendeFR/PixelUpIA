package com.jhendefr.pixelupia.domain.model

import android.net.Uri

data class FaceCluster(
    val id: Long,
    val name: String,
    val isPet: Boolean,
    val coverUri: Uri,
    val photoCount: Int,
    val isUserNamed: Boolean = false
) {
    val isCustomNamed: Boolean get() = isUserNamed || (!name.startsWith("Persona") && !name.startsWith("Mascota"))
    val isFrequent: Boolean get() = isPet || photoCount >= 3 || isUserNamed
}
