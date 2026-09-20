package com.jhendefr.pixelupia.domain.usecase

import android.graphics.Bitmap
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.data.ml.FaceClusteringService
import com.jhendefr.pixelupia.domain.model.Photo

class ManageFaceClusterUseCase(
    private val faceClusteringService: FaceClusteringService,
    private val smartGalleryDao: SmartGalleryDao
) {
    suspend fun renameCluster(clusterId: Long, newName: String) {
        faceClusteringService.renameCluster(clusterId, newName)
    }

    suspend fun addPhotoToCluster(photo: Photo, clusterId: Long, bitmap: Bitmap?) {
        faceClusteringService.addPhotoToClusterManually(photo, clusterId, bitmap)
    }

    suspend fun removePhotoFromCluster(photoId: Long, clusterId: Long, bitmap: Bitmap?) {
        faceClusteringService.removePhotoFromCluster(photoId, clusterId, bitmap)
    }

    suspend fun deleteCluster(clusterId: Long) {
        smartGalleryDao.deleteCluster(clusterId)
    }

    suspend fun mergeClusters(targetClusterId: Long, sourceClusterId: Long) {
        faceClusteringService.mergeClusters(targetClusterId, sourceClusterId)
    }

    suspend fun hideCluster(clusterId: Long) {
        smartGalleryDao.hideCluster(clusterId)
    }

    suspend fun setClusterCover(clusterId: Long, photo: Photo, bitmap: Bitmap?) {
        faceClusteringService.setClusterCover(clusterId, photo, bitmap)
    }
}
