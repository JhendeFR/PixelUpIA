package com.jhendefr.pixelupia.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smart_photos")
data class SmartPhotoEntity(
    @PrimaryKey
    val photoId: Long,
    val uri: String,
    val name: String,
    val folderName: String,
    val dateAdded: Long,
    val textContent: String = "",
    val boundingBoxes: String = "[]"
)
