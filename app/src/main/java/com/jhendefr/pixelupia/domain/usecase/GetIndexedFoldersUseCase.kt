package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.IndexedFolderInfo
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetIndexedFoldersUseCase(
    private val smartPhotoRepository: SmartPhotoRepository
) {
    operator fun invoke(allPhotos: List<Photo>): Flow<List<IndexedFolderInfo>> {
        return smartPhotoRepository.getIndexedPhotoIds().map { indexedIds ->
            val indexedSet = indexedIds.toSet()

            // Filtramos las fotos de la carpeta Screenshots (por ahora la única carpeta indexada por IA)
            val screenshotPhotos = allPhotos.filter {
                it.folderName.equals("Screenshots", ignoreCase = true) ||
                        it.folderName.contains("screenshot", ignoreCase = true) ||
                        it.name.contains("screenshot", ignoreCase = true)
            }

            val totalScreenshots = screenshotPhotos.size
            val indexedScreenshots = screenshotPhotos.count { indexedSet.contains(it.id) }
            val coverUri = screenshotPhotos.firstOrNull()?.uri

            val screenshotFolderInfo = IndexedFolderInfo(
                name = "Screenshots",
                totalPhotos = totalScreenshots,
                indexedPhotos = indexedScreenshots,
                isProcessing = totalScreenshots > 0 && indexedScreenshots < totalScreenshots,
                coverUri = coverUri
            )

            listOf(screenshotFolderInfo)
        }
    }
}
