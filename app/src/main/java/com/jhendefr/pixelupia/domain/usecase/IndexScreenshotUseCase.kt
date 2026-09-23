package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.data.ocr.TextRecognitionDataSource
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository

class IndexScreenshotUseCase(
    private val ocrDataSource: TextRecognitionDataSource,
    private val smartPhotoRepository: SmartPhotoRepository
) {
    suspend operator fun invoke(photo: Photo): Result<Unit> {
        return try {
            val ocrResult = ocrDataSource.processImage(photo.uri)
            if (ocrResult.text.isNotBlank()) {
                smartPhotoRepository.indexPhoto(
                    photo = photo,
                    textContent = ocrResult.text,
                    boundingBoxes = ocrResult.boundingBoxesJson
                )
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
