package com.jhendefr.pixelupia.domain.usecase

import android.net.Uri
import com.jhendefr.pixelupia.data.ocr.DetailedOcrResult
import com.jhendefr.pixelupia.data.ocr.TextRecognitionDataSource

class RunInteractiveOcrUseCase(
    private val textRecognitionDataSource: TextRecognitionDataSource
) {
    suspend operator fun invoke(uri: Uri): Result<DetailedOcrResult> {
        return try {
            val result = textRecognitionDataSource.recognizeTextDetailed(uri)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
