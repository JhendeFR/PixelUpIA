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
    private val smartGalleryDao: SmartGalleryDao,
    private val faceDetectionDataSource: FaceDetectionDataSource? = null,
    private val objectDetectionDataSource: ObjectDetectionDataSource? = null
) {

    companion object {
        const val HUMAN_FACE_CLUSTER_THRESHOLD = 0.65f
        const val PET_CLUSTER_THRESHOLD = 0.72f

        // Retrocompatibilidad
        const val FACE_THRESHOLD = HUMAN_FACE_CLUSTER_THRESHOLD
        const val PET_THRESHOLD = PET_CLUSTER_THRESHOLD
    }

    suspend fun rebuildAllClustersFromScratch() = withContext(Dispatchers.IO) {
        smartGalleryDao.clearAllPhotoClusterCrossRefs()
        smartGalleryDao.clearAllFaceClusters()
        smartGalleryDao.markAllPhotosForReEnrichment()
    }

    suspend fun clusterPhotoFacesAndPets(
        photo: Photo,
        bitmap: Bitmap,
        detectedFaces: List<DetectedFaceItem>,
        petBoundingBoxes: List<Rect>
    ) = withContext(Dispatchers.Default) {
        val excludedClusterIds = smartGalleryDao.getExcludedClusterIdsForPhoto(photo.id).toSet()

        // 1. Agrupamiento de Rostros Humanos
        if (detectedFaces.isNotEmpty()) {
            val humanClusters = smartGalleryDao.getClustersByType(isPet = false).first().toMutableList()
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
                    threshold = HUMAN_FACE_CLUSTER_THRESHOLD,
                    defaultNamePrefix = "Persona"
                )
            }
        }

        // 2. Agrupamiento de Mascotas (MobileNetV3 1280D)
        if (petBoundingBoxes.isNotEmpty()) {
            val petClusters = smartGalleryDao.getClustersByType(isPet = true).first().toMutableList()
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
                    threshold = PET_CLUSTER_THRESHOLD,
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
        existingClusters: MutableList<FaceClusterEntity>,
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

            // Actualizacion incremental del centroide
            val centroidString = if (!alreadyInCluster) {
                val currentCentroid = FaceEmbeddingDataSource.stringToVector(bestCluster.centroidVector)
                val newCentroid = FaceEmbeddingDataSource.updateCentroidIncremental(currentCentroid, embedding)
                FaceEmbeddingDataSource.vectorToString(newCentroid)
            } else {
                bestCluster.centroidVector
            }

            // No sobrescribir coverUri
            val hasValidCrop = if (bestCluster.coverUri.contains("cluster_crops")) {
                val path = bestCluster.coverUri.removePrefix("file://")
                java.io.File(path).exists()
            } else false

            val newCoverUri = if (hasValidCrop) {
                bestCluster.coverUri
            } else {
                val savedCrop = if (isPet) {
                    petEmbeddingDataSource.savePetCrop(bitmap, box, bestCluster.clusterId)
                } else {
                    faceEmbeddingDataSource.saveFaceCrop(bitmap, box, bestCluster.clusterId)
                }
                if (savedCrop.isNotBlank()) savedCrop else bestCluster.coverUri
            }

            // Conteo de fotos del grupo
            val realPhotoCount = smartGalleryDao.getClusterPhotoCount(bestCluster.clusterId)

            val updatedCluster = bestCluster.copy(
                centroidVector = centroidString,
                coverUri = newCoverUri,
                photoCount = realPhotoCount,
                updatedAt = System.currentTimeMillis()
            )
            smartGalleryDao.updateCluster(updatedCluster)

            val idx = existingClusters.indexOfFirst { it.clusterId == bestCluster.clusterId }
            if (idx != -1) {
                existingClusters[idx] = updatedCluster
            }
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

            val newCluster = tempCluster.copy(
                clusterId = newClusterId,
                coverUri = finalCoverUri,
                photoCount = realCount
            )
            smartGalleryDao.updateCluster(newCluster)
            existingClusters.add(newCluster)
        }
    }

    // Agregar manualmente foto a un cluster
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
            val embedding = if (cluster.isPet) {
                val objectsResult = objectDetectionDataSource?.detectObjects(photo.uri)
                val petBox = objectsResult?.objects?.filter { obj ->
                    obj.label.contains("perro", ignoreCase = true) ||
                    obj.label.contains("gato", ignoreCase = true) ||
                    obj.label.contains("mascota", ignoreCase = true) ||
                    obj.label.contains("animal", ignoreCase = true) ||
                    obj.label.contains("dog", ignoreCase = true) ||
                    obj.label.contains("cat", ignoreCase = true) ||
                    obj.label.contains("pet", ignoreCase = true)
                }?.maxByOrNull { it.box.width() * it.box.height() }?.box

                val scaledBox = if (petBox != null) {
                    val imgW = if (objectsResult.imageWidth > 0) objectsResult.imageWidth else bitmap.width
                    val imgH = if (objectsResult.imageHeight > 0) objectsResult.imageHeight else bitmap.height
                    val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                    val scaleY = bitmap.height.toFloat() / imgH.toFloat()
                    Rect(
                        (petBox.left * scaleX).toInt().coerceIn(0, bitmap.width),
                        (petBox.top * scaleY).toInt().coerceIn(0, bitmap.height),
                        (petBox.right * scaleX).toInt().coerceIn(0, bitmap.width),
                        (petBox.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                    )
                } else null

                if (scaledBox != null) {
                    petEmbeddingDataSource.extractEmbedding(bitmap, scaledBox).getOrNull()
                } else null
            } else {
                val facesDetailed = faceDetectionDataSource?.detectFacesDetailed(photo.uri)
                val targetFace = facesDetailed?.faces?.maxByOrNull { it.box.width() * it.box.height() }
                if (targetFace != null) {
                    val imgW = if (facesDetailed.imageWidth > 0) facesDetailed.imageWidth else bitmap.width
                    val imgH = if (facesDetailed.imageHeight > 0) facesDetailed.imageHeight else bitmap.height
                    val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                    val scaleY = bitmap.height.toFloat() / imgH.toFloat()

                    val scaledBox = Rect(
                        (targetFace.box.left * scaleX).toInt().coerceIn(0, bitmap.width),
                        (targetFace.box.top * scaleY).toInt().coerceIn(0, bitmap.height),
                        (targetFace.box.right * scaleX).toInt().coerceIn(0, bitmap.width),
                        (targetFace.box.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                    )
                    val scaledLeftEye = targetFace.leftEye?.let { android.graphics.PointF(it.x * scaleX, it.y * scaleY) }
                    val scaledRightEye = targetFace.rightEye?.let { android.graphics.PointF(it.x * scaleX, it.y * scaleY) }

                    faceEmbeddingDataSource.extractEmbedding(
                        sourceBitmap = bitmap,
                        boundingBox = scaledBox,
                        leftEye = scaledLeftEye,
                        rightEye = scaledRightEye
                    ).getOrNull()
                } else null
            }

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

    // Remover foto de un cluster (retroalimentacion negativa)
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

        // Actualizar portada
        val remainingPhotos = smartGalleryDao.getPhotosForCluster(clusterId).first().filter { it.photoId != photoId }
        val newCover = if (remainingPhotos.isNotEmpty()) {
            remainingPhotos[0].uri
        } else {
            cluster.coverUri
        }

        if (bitmap != null) {
            val photoEntityForUri = smartGalleryDao.getPhotoById(photoId)
            val uri = photoEntityForUri?.let { android.net.Uri.parse(it.uri) }

            val embedding = if (uri != null) {
                if (cluster.isPet) {
                    val objectsResult = objectDetectionDataSource?.detectObjects(uri)
                    val petBox = objectsResult?.objects?.filter { obj ->
                        obj.label.contains("perro", ignoreCase = true) ||
                        obj.label.contains("gato", ignoreCase = true) ||
                        obj.label.contains("mascota", ignoreCase = true) ||
                        obj.label.contains("animal", ignoreCase = true) ||
                        obj.label.contains("dog", ignoreCase = true) ||
                        obj.label.contains("cat", ignoreCase = true) ||
                        obj.label.contains("pet", ignoreCase = true)
                    }?.maxByOrNull { it.box.width() * it.box.height() }?.box

                    val scaledBox = if (petBox != null) {
                        val imgW = if (objectsResult.imageWidth > 0) objectsResult.imageWidth else bitmap.width
                        val imgH = if (objectsResult.imageHeight > 0) objectsResult.imageHeight else bitmap.height
                        val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                        val scaleY = bitmap.height.toFloat() / imgH.toFloat()
                        Rect(
                            (petBox.left * scaleX).toInt().coerceIn(0, bitmap.width),
                            (petBox.top * scaleY).toInt().coerceIn(0, bitmap.height),
                            (petBox.right * scaleX).toInt().coerceIn(0, bitmap.width),
                            (petBox.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                        )
                    } else null

                    if (scaledBox != null) {
                        petEmbeddingDataSource.extractEmbedding(bitmap, scaledBox).getOrNull()
                    } else null
                } else {
                    val facesDetailed = faceDetectionDataSource?.detectFacesDetailed(uri)
                    val targetFace = facesDetailed?.faces?.maxByOrNull { it.box.width() * it.box.height() }
                    if (targetFace != null) {
                        val imgW = if (facesDetailed.imageWidth > 0) facesDetailed.imageWidth else bitmap.width
                        val imgH = if (facesDetailed.imageHeight > 0) facesDetailed.imageHeight else bitmap.height
                        val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                        val scaleY = bitmap.height.toFloat() / imgH.toFloat()

                        val scaledBox = Rect(
                            (targetFace.box.left * scaleX).toInt().coerceIn(0, bitmap.width),
                            (targetFace.box.top * scaleY).toInt().coerceIn(0, bitmap.height),
                            (targetFace.box.right * scaleX).toInt().coerceIn(0, bitmap.width),
                            (targetFace.box.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                        )
                        val scaledLeftEye = targetFace.leftEye?.let { android.graphics.PointF(it.x * scaleX, it.y * scaleY) }
                        val scaledRightEye = targetFace.rightEye?.let { android.graphics.PointF(it.x * scaleX, it.y * scaleY) }

                        faceEmbeddingDataSource.extractEmbedding(
                            sourceBitmap = bitmap,
                            boundingBox = scaledBox,
                            leftEye = scaledLeftEye,
                            rightEye = scaledRightEye
                        ).getOrNull()
                    } else null
                }
            } else null

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
            val regex = Regex("(?i)\\b${Regex.escape(cluster.name)}\\b")
            val cleanedSearchContent = photoEntity.searchContent.replace(regex, "").replace(Regex("\\s+"), " ").trim()
            val cleanedLabels = photoEntity.labels.filterNot { it.equals(cluster.name, ignoreCase = true) }
            smartGalleryDao.updatePhotoLabelsAndSearch(photoId, cleanedLabels, cleanedSearchContent)
        }
    }

    suspend fun setClusterCover(
        clusterId: Long,
        photo: Photo,
        bitmap: Bitmap?
    ) = withContext(Dispatchers.IO) {
        val cluster = smartGalleryDao.getClusterById(clusterId) ?: return@withContext
        val newCoverUri = if (bitmap != null) {
            val cropUri = if (cluster.isPet) {
                val objectsResult = objectDetectionDataSource?.detectObjects(photo.uri)
                val petBox = objectsResult?.objects?.filter { obj ->
                    obj.label.contains("perro", ignoreCase = true) ||
                    obj.label.contains("gato", ignoreCase = true) ||
                    obj.label.contains("mascota", ignoreCase = true) ||
                    obj.label.contains("animal", ignoreCase = true) ||
                    obj.label.contains("dog", ignoreCase = true) ||
                    obj.label.contains("cat", ignoreCase = true) ||
                    obj.label.contains("pet", ignoreCase = true)
                }?.maxByOrNull { it.box.width() * it.box.height() }?.box

                val scaledBox = if (petBox != null) {
                    val imgW = if (objectsResult.imageWidth > 0) objectsResult.imageWidth else bitmap.width
                    val imgH = if (objectsResult.imageHeight > 0) objectsResult.imageHeight else bitmap.height
                    val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                    val scaleY = bitmap.height.toFloat() / imgH.toFloat()
                    Rect(
                        (petBox.left * scaleX).toInt().coerceIn(0, bitmap.width),
                        (petBox.top * scaleY).toInt().coerceIn(0, bitmap.height),
                        (petBox.right * scaleX).toInt().coerceIn(0, bitmap.width),
                        (petBox.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                    )
                } else {
                    Rect(0, 0, bitmap.width, bitmap.height)
                }
                petEmbeddingDataSource.savePetCrop(bitmap, scaledBox, clusterId)
            } else {
                val facesDetailed = faceDetectionDataSource?.detectFacesDetailed(photo.uri)
                val targetFace = facesDetailed?.faces?.maxByOrNull { it.box.width() * it.box.height() }
                val scaledBox = if (targetFace != null) {
                    val imgW = if (facesDetailed.imageWidth > 0) facesDetailed.imageWidth else bitmap.width
                    val imgH = if (facesDetailed.imageHeight > 0) facesDetailed.imageHeight else bitmap.height
                    val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                    val scaleY = bitmap.height.toFloat() / imgH.toFloat()
                    Rect(
                        (targetFace.box.left * scaleX).toInt().coerceIn(0, bitmap.width),
                        (targetFace.box.top * scaleY).toInt().coerceIn(0, bitmap.height),
                        (targetFace.box.right * scaleX).toInt().coerceIn(0, bitmap.width),
                        (targetFace.box.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                    )
                } else {
                    Rect(0, 0, bitmap.width, bitmap.height)
                }
                faceEmbeddingDataSource.saveFaceCrop(bitmap, scaledBox, clusterId)
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

        val regex = Regex("(?i)\\b${Regex.escape(oldName)}\\b")
        val photos = smartGalleryDao.getPhotosForCluster(clusterId).first()
        for (photo in photos) {
            val updatedSearchContent = photo.searchContent.replace(regex, newName).replace(Regex("\\s+"), " ").trim()
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

