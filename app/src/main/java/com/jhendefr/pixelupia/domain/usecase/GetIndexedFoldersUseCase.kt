package com.jhendefr.pixelupia.domain.usecase

import com.jhendefr.pixelupia.domain.model.IndexedFolderInfo
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.repository.SmartPhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetIndexedFoldersUseCase(
    private val smartPhotoRepository: SmartPhotoRepository
) {
    operator fun invoke(
        allPhotos: List<Photo>,
        chosenFolderNames: Set<String> = setOf("Screenshots")
    ): Flow<List<IndexedFolderInfo>> {
        return smartPhotoRepository.getIndexedPhotoIds().map { indexedIds ->
            val indexedSet = indexedIds.toSet()
            val foldersToProcess = if (chosenFolderNames.isEmpty()) setOf("Screenshots") else chosenFolderNames

            foldersToProcess.map { folderName ->
                val folderPhotos = allPhotos.filter {
                    it.folderName.equals(folderName, ignoreCase = true) ||
                            it.folderName.contains(folderName, ignoreCase = true)
                }

                val total = folderPhotos.size
                val indexed = folderPhotos.count { indexedSet.contains(it.id) }
                val coverUri = folderPhotos.firstOrNull()?.uri

                IndexedFolderInfo(
                    name = folderName,
                    totalPhotos = total,
                    indexedPhotos = indexed,
                    isProcessing = total > 0 && indexed < total,
                    coverUri = coverUri
                )
            }
        }
    }
}
