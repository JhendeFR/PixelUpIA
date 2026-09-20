package com.jhendefr.pixelupia.data.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jhendefr.pixelupia.data.local.room.SnapVaultDatabase
import com.jhendefr.pixelupia.data.ml.BarcodeDataSource
import com.jhendefr.pixelupia.data.ml.EntityExtractionDataSource
import com.jhendefr.pixelupia.data.ml.FaceDetectionDataSource
import com.jhendefr.pixelupia.data.ml.ImageLabelingDataSource
import com.jhendefr.pixelupia.data.ocr.TextRecognitionDataSource
import com.jhendefr.pixelupia.domain.usecase.EnrichScreenshotUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MultimodalEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = SnapVaultDatabase.getInstance(applicationContext)
        val dao = database.smartGalleryDao()

        val unenrichedScreenshots = dao.getUnenrichedScreenshots()
        if (unenrichedScreenshots.isEmpty()) {
            return@withContext Result.success()
        }

        val barcodeDataSource = BarcodeDataSource(applicationContext)
        val faceDetectionDataSource = FaceDetectionDataSource(applicationContext)
        val imageLabelingDataSource = ImageLabelingDataSource(applicationContext)
        val entityExtractionDataSource = EntityExtractionDataSource()
        val textRecognitionDataSource = TextRecognitionDataSource(applicationContext)
        val objectDetectionDataSource = com.jhendefr.pixelupia.data.ml.ObjectDetectionDataSource(
            context = applicationContext,
            imageLabelingDataSource = imageLabelingDataSource,
            faceDetectionDataSource = faceDetectionDataSource
        )
        val faceEmbeddingDataSource = com.jhendefr.pixelupia.data.ml.FaceEmbeddingDataSource(applicationContext)
        val petEmbeddingDataSource = com.jhendefr.pixelupia.data.ml.PetEmbeddingDataSource(applicationContext)
        val faceClusteringService = com.jhendefr.pixelupia.data.ml.FaceClusteringService(
            faceEmbeddingDataSource = faceEmbeddingDataSource,
            petEmbeddingDataSource = petEmbeddingDataSource,
            smartGalleryDao = dao
        )

        val enrichUseCase = EnrichScreenshotUseCase(
            barcodeDataSource = barcodeDataSource,
            faceDetectionDataSource = faceDetectionDataSource,
            imageLabelingDataSource = imageLabelingDataSource,
            entityExtractionDataSource = entityExtractionDataSource,
            smartGalleryDao = dao,
            textRecognitionDataSource = textRecognitionDataSource,
            context = applicationContext,
            faceClusteringService = faceClusteringService,
            objectDetectionDataSource = objectDetectionDataSource
        )

        for (photoEntity in unenrichedScreenshots) {
            if (isStopped) {
                return@withContext Result.retry()
            }
            try {
                enrichUseCase.enrichById(
                    photoId = photoEntity.photoId,
                    uri = Uri.parse(photoEntity.uri),
                    name = photoEntity.name,
                    folderName = photoEntity.folderName,
                    dateTaken = photoEntity.dateAdded
                )
            } catch (e: Exception) {
                // Continuar con la siguiente captura para no detener el worker
            }
        }

        Result.success()
    }
}
