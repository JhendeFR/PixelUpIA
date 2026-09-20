package com.jhendefr.pixelupia.data.local.room

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "photo_cluster_cross_ref",
    primaryKeys = ["photoId", "clusterId"],
    indices = [
        Index(value = ["clusterId"]),
        Index(value = ["photoId"])
    ]
)
data class PhotoClusterCrossRefEntity(
    val photoId: Long,
    val clusterId: Long,
    val isExcluded: Boolean = false,
    val similarity: Float = 0f
)
