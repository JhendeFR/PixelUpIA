package com.jhendefr.pixelupia.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_clusters")
data class FaceClusterEntity(
    @PrimaryKey(autoGenerate = true)
    val clusterId: Long = 0L,
    val name: String,
    val isPet: Boolean = false,
    val centroidVector: String,
    val coverUri: String,
    val photoCount: Int = 0,
    val isUserNamed: Boolean = false,
    val isHidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
