package com.jhendefr.pixelupia.domain.usecase

import android.net.Uri
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.FaceCluster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetFaceClustersUseCase(
    private val smartGalleryDao: SmartGalleryDao
) {
    operator fun invoke(): Flow<List<FaceCluster>> {
        return smartGalleryDao.getAllClusters().map { entities ->
            entities.map { entity ->
                FaceCluster(
                    id = entity.clusterId,
                    name = entity.name,
                    isPet = entity.isPet,
                    coverUri = Uri.parse(entity.coverUri),
                    photoCount = entity.photoCount
                )
            }.sortedByDescending { it.photoCount }
        }
    }
}
