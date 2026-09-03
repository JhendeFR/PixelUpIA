package com.jhendefr.pixelupia.domain.usecase

import android.net.Uri
import com.jhendefr.pixelupia.data.local.room.SmartGalleryDao
import com.jhendefr.pixelupia.data.local.room.SmartPhotoEntity
import com.jhendefr.pixelupia.data.ml.BarcodeDataSource
import com.jhendefr.pixelupia.data.ml.EntityExtractionDataSource
import com.jhendefr.pixelupia.data.ml.FaceDetectionDataSource
import com.jhendefr.pixelupia.data.ml.ImageLabelingDataSource
import com.jhendefr.pixelupia.data.ocr.TextRecognitionDataSource
import com.jhendefr.pixelupia.domain.model.MultimodalMetadata
import com.jhendefr.pixelupia.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class EnrichScreenshotUseCase(
    private val barcodeDataSource: BarcodeDataSource,
    private val faceDetectionDataSource: FaceDetectionDataSource,
    private val imageLabelingDataSource: ImageLabelingDataSource,
    private val entityExtractionDataSource: EntityExtractionDataSource,
    private val smartGalleryDao: SmartGalleryDao,
    private val textRecognitionDataSource: TextRecognitionDataSource
) {
    suspend operator fun invoke(photo: Photo): Result<MultimodalMetadata> = withContext(Dispatchers.IO) {
        try {
            var existingEntity = smartGalleryDao.getPhotoById(photo.id)

            // Si la foto no está en Room o no tiene OCR previo, ejecutar OCR primero
            var textContent = existingEntity?.textContent ?: ""
            if (existingEntity == null) {
                val ocrResult = textRecognitionDataSource.processImage(photo.uri)
                textContent = ocrResult.text
                val entity = SmartPhotoEntity(
                    photoId = photo.id,
                    uri = photo.uri.toString(),
                    name = photo.name,
                    folderName = photo.folderName,
                    dateAdded = photo.dateTaken,
                    textContent = ocrResult.text,
                    boundingBoxes = ocrResult.boundingBoxesJson
                )
                smartGalleryDao.insertPhoto(entity)
                existingEntity = entity
            } else if (textContent.isBlank()) {
                val ocrResult = textRecognitionDataSource.processImage(photo.uri)
                textContent = ocrResult.text
                val updatedEntity = existingEntity.copy(
                    textContent = ocrResult.text,
                    boundingBoxes = ocrResult.boundingBoxesJson
                )
                smartGalleryDao.insertPhoto(updatedEntity)
                existingEntity = updatedEntity
            }

            // Ejecutar los 4 modelos de forma concurrente para máxima eficiencia
            val multimodalResult = coroutineScope {
                val barcodesDeferred = async { barcodeDataSource.scanBarcodes(photo.uri) }
                val facesDeferred = async { faceDetectionDataSource.detectFaces(photo.uri) }
                val labelsDeferred = async { imageLabelingDataSource.labelImage(photo.uri) }
                val entitiesDeferred = async { entityExtractionDataSource.extractEntities(textContent) }

                val barcodes = barcodesDeferred.await()
                val hasFaces = facesDeferred.await()
                val labelItems = labelsDeferred.await()
                val entityItems = entitiesDeferred.await()

                val formattedLabels = labelItems.map { it.formatted }
                val formattedEntities = entityItems.map { "${it.text} (${it.type})" }

                MultimodalMetadata(
                    photoId = photo.id,
                    hasFaces = hasFaces,
                    barcodes = barcodes,
                    labels = formattedLabels,
                    entities = formattedEntities,
                    isEnriched = true
                )
            }

            // Actualizar Room con los metadatos generados
            smartGalleryDao.updateEnrichment(
                photoId = photo.id,
                hasFaces = multimodalResult.hasFaces,
                barcodes = multimodalResult.barcodes,
                labels = multimodalResult.labels,
                entities = multimodalResult.entities
            )

            Result.success(multimodalResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enrichById(photoId: Long, uri: Uri, name: String, folderName: String, dateTaken: Long): Result<MultimodalMetadata> {
        val dummyPhoto = Photo(
            id = photoId,
            uri = uri,
            name = name,
            dateTaken = dateTaken,
            size = 0L,
            folderName = folderName,
            mimeType = "image/*"
        )
        return invoke(dummyPhoto)
    }
}
