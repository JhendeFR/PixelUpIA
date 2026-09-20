package com.jhendefr.pixelupia.domain.model

import android.net.Uri

data class FaceCluster(
    val id: Long,
    val name: String,
    val isPet: Boolean,
    val coverUri: Uri,
    val photoCount: Int
) {
    val isCustomNamed: Boolean get() = !name.startsWith("Persona") && !name.startsWith("Mascota")
    val isUserNamed: Boolean get() = isCustomNamed
    val isFrequent: Boolean get() = isPet || photoCount >= 3 || isCustomNamed
}
