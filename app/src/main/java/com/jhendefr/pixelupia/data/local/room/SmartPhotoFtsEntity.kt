package com.jhendefr.pixelupia.data.local.room

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "smart_photos_fts")
@Fts4(contentEntity = SmartPhotoEntity::class)
data class SmartPhotoFtsEntity(
    val textContent: String,
    val boundingBoxes: String
)
