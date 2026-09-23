package com.jhendefr.pixelupia.data.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.jhendefr.pixelupia.data.local.room.FaceClusterEntity
import com.jhendefr.pixelupia.data.local.room.PhotoClusterCrossRefEntity
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FaceClusteringService(
    private val faceEmbeddingDataSource: FaceEmbeddingDataSource,
    private val petEmbeddingDataSource: PetEmbeddingDataSource,
    private val smartGalleryDao: SmartGalleryDao
) {

    companion object {
        const val FACE_THRESHOLD = 0.65f
        const val PET_THRESHOLD = 0.75f
    }

    suspend fun clusterPhotoFacesAndPets(
        photo: Photo,
        bitmap: Bitmap,
        detectedFaces: List<DetectedFaceItem>,
        petBoundingBoxes: List<Rect>
    ) = withContext(Dispatchers.Default) {
        val excludedClusterIds = smartGalleryDao.getExcludedClusterIdsForPhoto(photo.id).toSet()

        // 1. Agrupamiento de Rostros Humanos (FaceNet 512D con alineacion afin)
        if (detectedFaces.isNotEmpty()) {
            val humanClusters = smartGalleryDao.getClustersByType(isPet = false).first()
            for (face in detectedFaces) {
                val embeddingResult = faceEmbeddingDataSource.extractEmbedding(
                    sourceBitmap = bitmap,
                    boundingBox = face.box,
                    leftEye = face.leftEye,
                    rightEye = face.rightEye
                )
                val embedding = embeddingResult.getOrNull() ?: continue

                processEmbedding(
                    photo = photo,
                    bitmap = bitmap,
                    box = face.box,
                    embedding = embedding,
                    isPet = false,
                    existingClusters = humanClusters,
                    excludedClusterIds = excludedClusterIds,
                    threshold = FACE_THRESHOLD,
                    defaultNamePrefix = "Persona"
                )
            }
        }

        // 2. Agrupamiento de Mascotas (MobileNetV3 1280D)
        if (petBoundingBoxes.isNotEmpty()) {
            val petClusters = smartGalleryDao.getClustersByType(isPet = true).first()
            for (box in petBoundingBoxes) {
                val embeddingResult = petEmbeddingDataSource.extractEmbedding(
                    sourceBitmap = bitmap,
                    boundingBox = box
                )
                val embedding = embeddingResult.getOrNull() ?: continue

                processEmbedding(
                    photo = photo,
                    bitmap = bitmap,
                    box = box,
                    embedding = embedding,
                    isPet = true,
                    existingClusters = petClusters,
                    excludedClusterIds = excludedClusterIds,
                    threshold = PET_THRESHOLD,
                    defaultNamePrefix = "Mascota"
                )
            }
        }
    }

    private suspend fun processEmbedding(
        photo: Photo,
        bitmap: Bitmap,
        box: Rect,
        embedding: FloatArray,
        isPet: Boolean,
        existingClusters: List<FaceClusterEntity>,
        excludedClusterIds: Set<Long>,
        threshold: Float,
        defaultNamePrefix: String
    ) {
        var bestCluster: FaceClusterEntity? = null
        var bestSimilarity = -1f

        for (cluster in existingClusters) {
            if (excludedClusterIds.contains(cluster.clusterId)) continue
            val centroid = FaceEmbeddingDataSource.stringToVector(cluster.centroidVector)
            val sim = FaceEmbeddingDataSource.cosineSimilarity(embedding, centroid)
            if (sim > bestSimilarity) {
                bestSimilarity = sim
                bestCluster = cluster
            }
        }

        if (bestCluster != null && bestSimilarity >= threshold) {
            // Asignar al cluster existente
            val alreadyInCluster = smartGalleryDao.hasPhotoInCluster(bestCluster.clusterId, photo.id)

            smartGalleryDao.insertCrossRef(
                PhotoClusterCrossRefEntity(
                    photoId = photo.id,
                    clusterId = bestCluster.clusterId,
                    isExcluded = false,
                    similarity = bestSimilarity
                )
            )

            // Actualizacion incremental del centroide solo si la foto es nueva en el cluster
            val centroidString = if (!alreadyInCluster) {
                val currentCentroid = FaceEmbeddingDataSource.stringToVector(bestCluster.centroidVector)
                val newCentroid = FaceEmbeddingDataSource.updateCentroidIncremental(currentCentroid, embedding)
                FaceEmbeddingDataSource.vectorToString(newCentroid)
            } else {
                bestCluster.centroidVector
            }

            // Regenerar y optimizar el recorte local centrado para el avatar si hace falta
            val savedCrop = if (isPet) {
                petEmbeddingDataSource.savePetCrop(bitmap, box, bestCluster.clusterId)
            } else {
                faceEmbeddingDataSource.saveFaceCrop(bitmap, box, bestCluster.clusterId)
            }
            val newCoverUri = if (savedCrop.isNotBlank()) savedCrop else bestCluster.coverUri

            // Usar conteo real de fotos unicas vinculadas al cluster para evitar sobreconteo al reanalizar
            val realPhotoCount = smartGalleryDao.getClusterPhotoCount(bestCluster.clusterId)

            smartGalleryDao.updateCluster(
                bestCluster.copy(
                    centroidVector = centroidString,
                    coverUri = newCoverUri,
                    photoCount = realPhotoCount,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // Crear un nuevo cluster
            val clusterCount = existingClusters.size + 1
            val centroidString = FaceEmbeddingDataSource.vectorToString(embedding)

            val tempCluster = FaceClusterEntity(
                name = "$defaultNamePrefix $clusterCount",
                isPet = isPet,
                centroidVector = centroidString,
                coverUri = photo.uri.toString(),
                photoCount = 1,
                isUserNamed = false,
                isHidden = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newClusterId = smartGalleryDao.insertCluster(tempCluster)

            // Guardar el recorte centrado para el avatar circular
            val cropUri = if (isPet) {
                petEmbeddingDataSource.savePetCrop(bitmap, box, newClusterId)
            } else {
                faceEmbeddingDataSource.saveFaceCrop(bitmap, box, newClusterId)
            }
            val finalCoverUri = if (cropUri.isNotBlank()) cropUri else photo.uri.toString()

            smartGalleryDao.insertCrossRef(
                PhotoClusterCrossRefEntity(
                    photoId = photo.id,
                    clusterId = newClusterId,
                    isExcluded = false,
                    similarity = 1.0f
                )
            )

            val realCount = smartGalleryDao.getClusterPhotoCount(newClusterId).coerceAtLeast(1)

            smartGalleryDao.updateCluster(
                tempCluster.copy(
                    clusterId = newClusterId,
                    coverUri = finalCoverUri,
                    photoCount = realCount
                )
            )
        }
    }

    // Aprendizaje Activo: Agregar manualmente foto a un cluster
    suspend fun addPhotoToClusterManually(
        photo: Photo,
        clusterId: Long,
        bitmap: Bitmap?
    ) = withContext(Dispatchers.IO) {
        val cluster = smartGalleryDao.getClusterById(clusterId) ?: return@withContext

        smartGalleryDao.insertCrossRef(
            PhotoClusterCrossRefEntity(
                photoId = photo.id,
                clusterId = clusterId,
                isExcluded = false,
                similarity = 1.0f
            )
        )

        if (bitmap != null) {
            val embeddingResult = if (cluster.isPet) {
                petEmbeddingDataSource.extractEmbedding(bitmap)
            } else {
                faceEmbeddingDataSource.extractEmbedding(bitmap)
            }
            val embedding = embeddingResult.getOrNull()

            if (embedding != null) {
                val currentCentroid = FaceEmbeddingDataSource.stringToVector(cluster.centroidVector)
                val updatedCentroid = FaceEmbeddingDataSource.updateCentroidAddSample(currentCentroid, embedding)
                val centroidStr = FaceEmbeddingDataSource.vectorToString(updatedCentroid)

                smartGalleryDao.updateCluster(
                    cluster.copy(
                        centroidVector = centroidStr,
                        photoCount = cluster.photoCount + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val photoEntity = smartGalleryDao.getPhotoById(photo.id)
        if (photoEntity != null && !photoEntity.searchContent.contains(cluster.name, ignoreCase = true)) {
            val newSearchContent = "${photoEntity.searchContent} ${cluster.name}".trim()
            val newLabels = (photoEntity.labels + cluster.name).distinct()
            smartGalleryDao.updatePhotoLabelsAndSearch(photo.id, newLabels, newSearchContent)
        }
    }

    // Aprendizaje Activo: Remover foto de un cluster (retroalimentacion negativa)
    suspend fun removePhotoFromCluster(
        photoId: Long,
        clusterId: Long,
        bitmap: Bitmap?
    ) = withContext(Dispatchers.IO) {
        val cluster = smartGalleryDao.getClusterById(clusterId) ?: return@withContext

        smartGalleryDao.insertCrossRef(
            PhotoClusterCrossRefEntity(
                photoId = photoId,
                clusterId = clusterId,
                isExcluded = true,
                similarity = 0f
            )
        )

        // Actualizar portada si la foto removida era la portada
        val remainingPhotos = smartGalleryDao.getPhotosForCluster(clusterId).first().filter { it.photoId != photoId }
        val newCover = if (remainingPhotos.isNotEmpty()) {
            remainingPhotos[0].uri
        } else {
            cluster.coverUri
        }

        if (bitmap != null) {
            val embeddingResult = if (cluster.isPet) {
                petEmbeddingDataSource.extractEmbedding(bitmap)
            } else {
                faceEmbeddingDataSource.extractEmbedding(bitmap)
            }
            val embedding = embeddingResult.getOrNull()

            if (embedding != null) {
                val currentCentroid = FaceEmbeddingDataSource.stringToVector(cluster.centroidVector)
                val penalizedCentroid = FaceEmbeddingDataSource.updateCentroidRemoveSample(currentCentroid, embedding)
                val centroidStr = FaceEmbeddingDataSource.vectorToString(penalizedCentroid)

                smartGalleryDao.updateCluster(
                    cluster.copy(
                        coverUri = newCover,
                        centroidVector = centroidStr,
                        photoCount = maxOf(0, cluster.photoCount - 1),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                smartGalleryDao.updateCluster(
                    cluster.copy(
                        coverUri = newCover,
                        photoCount = maxOf(0, cluster.photoCount - 1),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            smartGalleryDao.updateCluster(
                cluster.copy(
                    coverUri = newCover,
                    photoCount = maxOf(0, cluster.photoCount - 1),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        val photoEntity = smartGalleryDao.getPhotoById(photoId)
        if (photoEntity != null) {
            val cleanedSearchContent = photoEntity.searchContent.replace(cluster.name, "", ignoreCase = true).trim()
            val cleanedLabels = photoEntity.labels.filterNot { it.equals(cluster.name, ignoreCase = true) }
            smartGalleryDao.updatePhotoLabelsAndSearch(photoId, cleanedLabels, cleanedSearchContent)
        }
    }

    // Asignar manualmente la foto de portada de un cluster
    suspend fun setClusterCover(
        clusterId: Long,
        photo: Photo,
        bitmap: Bitmap?
    ) = withContext(Dispatchers.IO) {
        val cluster = smartGalleryDao.getClusterById(clusterId) ?: return@withContext
        val newCoverUri = if (bitmap != null) {
            val cropUri = if (cluster.isPet) {
                val box = Rect(0, 0, bitmap.width, bitmap.height)
                petEmbeddingDataSource.savePetCrop(bitmap, box, clusterId)
            } else {
                val box = Rect(0, 0, bitmap.width, bitmap.height)
                faceEmbeddingDataSource.saveFaceCrop(bitmap, box, clusterId)
            }
            if (cropUri.isNotBlank()) cropUri else photo.uri.toString()
        } else {
            photo.uri.toString()
        }

        smartGalleryDao.updateCluster(
            cluster.copy(
                coverUri = newCoverUri,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // Renombrar cluster y propagar a FTS5
    suspend fun renameCluster(
        clusterId: Long,
        newName: String
    ) = withContext(Dispatchers.IO) {
        val oldCluster = smartGalleryDao.getClusterById(clusterId) ?: return@withContext
        val oldName = oldCluster.name
        smartGalleryDao.updateClusterName(clusterId, newName)

        val photos = smartGalleryDao.getPhotosForCluster(clusterId).first()
        for (photo in photos) {
            val updatedSearchContent = photo.searchContent.replace(oldName, newName, ignoreCase = true)
            val finalSearchContent = if (!updatedSearchContent.contains(newName, ignoreCase = true)) {
                "$updatedSearchContent $newName"
            } else {
                updatedSearchContent
            }.trim()

            val updatedLabels = photo.labels.map {
                if (it.equals(oldName, ignoreCase = true)) newName else it
            }

            smartGalleryDao.updatePhotoLabelsAndSearch(photo.photoId, updatedLabels, finalSearchContent)
        }
    }

    // Fusionar dos clusters de personas o mascotas
    suspend fun mergeClusters(
        targetClusterId: Long,
        sourceClusterId: Long
    ) = withContext(Dispatchers.IO) {
        if (targetClusterId == sourceClusterId) return@withContext
        val target = smartGalleryDao.getClusterById(targetClusterId) ?: return@withContext
        val source = smartGalleryDao.getClusterById(sourceClusterId) ?: return@withContext

        // Validar que ambos compartan el mismo tipo (ambos humanos o ambos mascotas)
        if (target.isPet != source.isPet) return@withContext

        val targetCentroid = FaceEmbeddingDataSource.stringToVector(target.centroidVector)
        val sourceCentroid = FaceEmbeddingDataSource.stringToVector(source.centroidVector)
        if (targetCentroid.size != sourceCentroid.size) return@withContext

        val totalCount = target.photoCount + source.photoCount
        val targetWeight = if (totalCount > 0) target.photoCount.toFloat() / totalCount else 0.5f
        val sourceWeight = if (totalCount > 0) source.photoCount.toFloat() / totalCount else 0.5f

        val mergedCentroid = FloatArray(targetCentroid.size) { i ->
            targetCentroid[i] * targetWeight + sourceCentroid[i] * sourceWeight
        }
        var norm = 0.0
        for (v in mergedCentroid) norm += (v * v).toDouble()
        norm = Math.sqrt(norm)
        if (norm > 1e-6) {
            for (i in mergedCentroid.indices) mergedCentroid[i] = (mergedCentroid[i] / norm).toFloat()
        }

        // Mover las fotos asociadas de source a target
        val sourceCrossRefs = smartGalleryDao.getCrossRefsForCluster(sourceClusterId)
        for (ref in sourceCrossRefs) {
            smartGalleryDao.insertCrossRef(
                PhotoClusterCrossRefEntity(
                    photoId = ref.photoId,
                    clusterId = targetClusterId,
                    isExcluded = ref.isExcluded,
                    similarity = ref.similarity
                )
            )
        }
        smartGalleryDao.deleteCrossRefsForCluster(sourceClusterId)
        smartGalleryDao.deleteCluster(sourceClusterId)

        val isTargetNamed = target.isUserNamed
        val isSourceNamed = source.isUserNamed
        val finalName = if (!isTargetNamed && isSourceNamed) source.name else target.name
        val finalIsUserNamed = isTargetNamed || isSourceNamed

        val centroidStr = FaceEmbeddingDataSource.vectorToString(mergedCentroid)

        smartGalleryDao.updateCluster(
            target.copy(
                name = finalName,
                isUserNamed = finalIsUserNamed,
                centroidVector = centroidStr,
                photoCount = totalCount,
                updatedAt = System.currentTimeMillis()
            )
        )

        if (finalName != target.name) {
            renameCluster(targetClusterId, finalName)
        }
    }
}

