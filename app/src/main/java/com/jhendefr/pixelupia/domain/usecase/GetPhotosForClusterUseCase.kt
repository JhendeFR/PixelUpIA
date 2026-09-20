package com.jhendefr.pixelupia.domain.usecase

import android.net.Uri
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetPhotosForClusterUseCase(
    private val smartGalleryDao: SmartGalleryDao
) {
    operator fun invoke(clusterId: Long): Flow<List<Photo>> {
        return smartGalleryDao.getPhotosForCluster(clusterId).map { entities ->
            entities.map { entity ->
                Photo(
                    id = entity.photoId,
                    uri = Uri.parse(entity.uri),
                    name = entity.name,
                    dateTaken = entity.dateAdded,
                    size = 0L,
                    folderName = entity.folderName,
                    mimeType = "image/*",
                    sourceApp = entity.sourceApp
                )
            }
        }
    }
}
