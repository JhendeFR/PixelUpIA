package com.jhendefr.pixelupia.ui.viewer

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhendefr.pixelupia.data.ml.*
import com.jhendefr.pixelupia.data.ocr.DetailedOcrResult
import com.jhendefr.pixelupia.domain.model.MediaOperationResult
import com.jhendefr.pixelupia.domain.model.MultimodalMetadata
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveAiMode {
    NONE,
    OCR,
    FACES,
    OBJECTS,
    BARCODES,
    ENTITIES
}

data class ViewerUiState(
    val photos: List<Photo> = emptyList(),
    val initialIndex: Int = 0,
    val isLoading: Boolean = true,
    val favoritePhotoIds: Set<Long> = emptySet(),
    val photoPendingAction: Photo? = null,
    val showDeleteConfirm: Boolean = false,
    val showFolderPickerForMove: Boolean = false,
    val showFolderPickerForCopy: Boolean = false,
    val existingFolders: List<String> = emptyList(),
    val userMessage: String? = null,
    val isPhotoDeleted: Boolean = false,
    val pendingIntentSender: IntentSender? = null,
    val isDeletePendingApproval: Boolean = false,

    // Modo IA y Sustitución de Píldora
    val isAiModeActive: Boolean = false,
    val isEnriching: Boolean = false,
    val multimodalMetadata: MultimodalMetadata? = null,
    val showMultimodalDetailsSheet: Boolean = false,

    // Inspección Independiente de Modelos y Bounding Boxes
    val activeAiMode: ActiveAiMode = ActiveAiMode.NONE,
    val activeBoundingBoxes: List<AiBoundingBoxItem> = emptyList(),
    val activeImageDimensions: Pair<Int, Int> = Pair(0, 0),
    val selectedDetectionId: String? = null,
    val isAnalyzingModel: Boolean = false,
    val modelAnalysisTitle: String? = null,

    // Estado del Motor OCR Interactivo
    val isOcrScanning: Boolean = false,
    val ocrResult: DetailedOcrResult? = null,
    val selectedBlockId: String? = null,
    val showOcrOverlay: Boolean = false,
    val showOcrDetailsSheet: Boolean = false
)

sealed interface ViewerEvent {
    data class ToggleFavorite(val photoId: Long) : ViewerEvent
    data class RequestDelete(val photo: Photo) : ViewerEvent
    object ConfirmDelete : ViewerEvent
    object DismissDeleteDialog : ViewerEvent
    data class RequestMove(val photo: Photo) : ViewerEvent
    data class ConfirmMove(val targetFolder: String) : ViewerEvent
    object DismissMoveDialog : ViewerEvent
    data class RequestCopy(val photo: Photo) : ViewerEvent
    data class ConfirmCopy(val targetFolder: String) : ViewerEvent
    object DismissCopyDialog : ViewerEvent
    object OnIntentSenderCompleted : ViewerEvent
    object OnIntentSenderDismissed : ViewerEvent
    object ClearUserMessage : ViewerEvent

    // Modo IA y Navegación
    object ToggleAiMode : ViewerEvent
    object CloseAiMode : ViewerEvent
    object ToggleMultimodalDetailsSheet : ViewerEvent
    object DismissMultimodalDetailsSheet : ViewerEvent
    data class LoadPhotoMetadata(val photoId: Long) : ViewerEvent

    // Ejecución Independiente de Modelos de IA
    data class TriggerInteractiveOcr(val photoUri: Uri) : ViewerEvent
    data class TriggerFaceDetection(val photoUri: Uri) : ViewerEvent
    data class TriggerObjectDetection(val photoUri: Uri) : ViewerEvent
    data class TriggerBarcodeScanning(val photoUri: Uri) : ViewerEvent
    data class TriggerEntityExtraction(val photoUri: Uri) : ViewerEvent

    // Análisis Multimodal Completo (Toda la Imagen)
    data class TriggerFullMultimodalEnrichment(val photo: Photo) : ViewerEvent

    // Control de Bounding Boxes y Selección
    data class SelectDetectionBox(val id: String?) : ViewerEvent
    object CloseDetectionOverlay : ViewerEvent

    // OCR Legacy Events
    data class SelectTextBlock(val blockId: String?) : ViewerEvent
    object ToggleOcrDetailsSheet : ViewerEvent
    object CloseOcrOverlay : ViewerEvent
}

class ViewerViewModel(
    private val initialPhotoId: Long,
    private val albumName: String?,
    private val getPhotosUseCase: GetPhotosUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val deletePhotosUseCase: DeletePhotosUseCase,
    private val movePhotosUseCase: MovePhotosUseCase,
    private val copyPhotosUseCase: CopyPhotosUseCase,
    private val getFavoritePhotoIdsUseCase: GetFavoritePhotoIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val runInteractiveOcrUseCase: RunInteractiveOcrUseCase,
    private val enrichScreenshotUseCase: EnrichScreenshotUseCase,
    private val getPhotoMultimodalMetadataUseCase: GetPhotoMultimodalMetadataUseCase,
    private val faceDetectionDataSource: FaceDetectionDataSource,
    private val objectDetectionDataSource: ObjectDetectionDataSource,
    private val barcodeDataSource: BarcodeDataSource,
    private val entityExtractionDataSource: EntityExtractionDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
        loadAlbums()
        observeFavorites()
        loadMultimodalMetadata(initialPhotoId)
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            getFavoritePhotoIdsUseCase().collect { favs ->
                _uiState.update { it.copy(favoritePhotoIds = favs) }
            }
        }
    }

    private fun loadMultimodalMetadata(photoId: Long) {
        viewModelScope.launch {
            val metadata = getPhotoMultimodalMetadataUseCase(photoId)
            _uiState.update { it.copy(multimodalMetadata = metadata) }
        }
    }

    fun onEvent(event: ViewerEvent) {
        when (event) {
            ViewerEvent.ToggleAiMode -> {
                _uiState.update { it.copy(isAiModeActive = !it.isAiModeActive) }
            }
            ViewerEvent.CloseAiMode -> {
                _uiState.update {
                    it.copy(
                        isAiModeActive = false,
                        activeAiMode = ActiveAiMode.NONE,
                        activeBoundingBoxes = emptyList(),
                        selectedDetectionId = null,
                        showOcrOverlay = false,
                        showOcrDetailsSheet = false,
                        showMultimodalDetailsSheet = false
                    )
                }
            }
            is ViewerEvent.LoadPhotoMetadata -> {
                loadMultimodalMetadata(event.photoId)
            }

            // 1. TEXTO (OCR) INDEPENDIENTE
            is ViewerEvent.TriggerInteractiveOcr -> {
                _uiState.update {
                    it.copy(
                        activeAiMode = ActiveAiMode.OCR,
                        isAnalyzingModel = true,
                        modelAnalysisTitle = "Analizando texto (OCR)...",
                        showOcrOverlay = true,
                        selectedBlockId = null,
                        selectedDetectionId = null
                    )
                }
                viewModelScope.launch {
                    val result = runInteractiveOcrUseCase(event.photoUri)
                    result.onSuccess { detailed ->
                        val boxes = detailed.blocks.map { block ->
                            AiBoundingBoxItem(
                                id = block.id,
                                label = block.text,
                                box = android.graphics.Rect(block.box.left, block.box.top, block.box.right, block.box.bottom),
                                type = AiOverlayType.OCR_TEXT
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isAnalyzingModel = false,
                                isOcrScanning = false,
                                ocrResult = detailed,
                                activeBoundingBoxes = boxes,
                                activeImageDimensions = Pair(detailed.imageWidth, detailed.imageHeight),
                                showOcrDetailsSheet = true,
                                userMessage = if (detailed.blocks.isNotEmpty()) "${detailed.blocks.size} bloques de texto detectados" else "No se detecto texto"
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isAnalyzingModel = false,
                                isOcrScanning = false,
                                showOcrOverlay = false,
                                activeAiMode = ActiveAiMode.NONE,
                                userMessage = "Error en OCR: ${err.message}"
                            )
                        }
                    }
                }
            }

            // 2. ROSTROS INDEPENDIENTE
            is ViewerEvent.TriggerFaceDetection -> {
                _uiState.update {
                    it.copy(
                        activeAiMode = ActiveAiMode.FACES,
                        isAnalyzingModel = true,
                        modelAnalysisTitle = "Detectando rostros...",
                        selectedDetectionId = null
                    )
                }
                viewModelScope.launch {
                    val result = faceDetectionDataSource.detectFacesDetailed(event.photoUri)
                    val boxes = result.faces.mapIndexed { index, face ->
                        AiBoundingBoxItem(
                            id = face.id,
                            label = "Rostro #${index + 1}",
                            box = face.box,
                            type = AiOverlayType.FACE
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isAnalyzingModel = false,
                            activeBoundingBoxes = boxes,
                            activeImageDimensions = Pair(result.imageWidth, result.imageHeight),
                            userMessage = if (boxes.isNotEmpty()) "${boxes.size} rostro(s) detectado(s)" else "No se detectaron rostros"
                        )
                    }
                }
            }

            // 3. OBJETOS INDEPENDIENTE CON PRECISIÓN %
            is ViewerEvent.TriggerObjectDetection -> {
                _uiState.update {
                    it.copy(
                        activeAiMode = ActiveAiMode.OBJECTS,
                        isAnalyzingModel = true,
                        modelAnalysisTitle = "Detectando objetos y precision...",
                        selectedDetectionId = null
                    )
                }
                viewModelScope.launch {
                    val result = objectDetectionDataSource.detectObjects(event.photoUri)
                    val boxes = result.objects.map { obj ->
                        AiBoundingBoxItem(
                            id = obj.id,
                            label = obj.label,
                            subLabel = "${(obj.confidence * 100).toInt()}% de precision",
                            box = obj.box,
                            type = AiOverlayType.OBJECT
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isAnalyzingModel = false,
                            activeBoundingBoxes = boxes,
                            activeImageDimensions = Pair(result.imageWidth, result.imageHeight),
                            userMessage = if (boxes.isNotEmpty()) "${boxes.size} objeto(s) detectado(s)" else "No se detectaron objetos en la imagen"
                        )
                    }
                }
            }

            // 4. CÓDIGOS DE BARRAS / QR INDEPENDIENTE
            is ViewerEvent.TriggerBarcodeScanning -> {
                _uiState.update {
                    it.copy(
                        activeAiMode = ActiveAiMode.BARCODES,
                        isAnalyzingModel = true,
                        modelAnalysisTitle = "Escaneando codigos QR / barras...",
                        selectedDetectionId = null
                    )
                }
                viewModelScope.launch {
                    val result = barcodeDataSource.scanBarcodesDetailed(event.photoUri)
                    val boxes = result.barcodes.mapIndexed { idx, barcode ->
                        val box = barcode.box ?: android.graphics.Rect(
                            (result.imageWidth * 0.1f).toInt(),
                            (result.imageHeight * 0.3f + idx * 100).toInt(),
                            (result.imageWidth * 0.9f).toInt(),
                            (result.imageHeight * 0.3f + idx * 100 + 80).toInt()
                        )
                        AiBoundingBoxItem(
                            id = barcode.id,
                            label = barcode.formatName,
                            subLabel = barcode.value,
                            box = box,
                            type = AiOverlayType.BARCODE
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isAnalyzingModel = false,
                            activeBoundingBoxes = boxes,
                            activeImageDimensions = Pair(result.imageWidth, result.imageHeight),
                            userMessage = if (boxes.isNotEmpty()) "${boxes.size} codigo(s) detectado(s)" else "No se detectaron codigos"
                        )
                    }
                }
            }

            // 5. ENTIDADES INDEPENDIENTE
            is ViewerEvent.TriggerEntityExtraction -> {
                _uiState.update {
                    it.copy(
                        activeAiMode = ActiveAiMode.ENTITIES,
                        isAnalyzingModel = true,
                        modelAnalysisTitle = "Extrayendo entidades estructuradas...",
                        selectedDetectionId = null
                    )
                }
                viewModelScope.launch {
                    val ocrResult = _uiState.value.ocrResult ?: runInteractiveOcrUseCase(event.photoUri).getOrNull()
                    if (ocrResult != null) {
                        val result = entityExtractionDataSource.extractEntitiesWithBoxes(ocrResult)
                        val boxes = result.entities.mapIndexed { idx, entity ->
                            val box = entity.box ?: android.graphics.Rect(
                                (result.imageWidth * 0.1f).toInt(),
                                (result.imageHeight * 0.2f + idx * 80).toInt(),
                                (result.imageWidth * 0.9f).toInt(),
                                (result.imageHeight * 0.2f + idx * 80 + 60).toInt()
                            )
                            AiBoundingBoxItem(
                                id = entity.id,
                                label = entity.type,
                                subLabel = entity.text,
                                box = box,
                                type = AiOverlayType.ENTITY
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isAnalyzingModel = false,
                                activeBoundingBoxes = boxes,
                                activeImageDimensions = Pair(result.imageWidth, result.imageHeight),
                                userMessage = if (boxes.isNotEmpty()) "${boxes.size} entidades encontradas" else "No se detectaron entidades"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isAnalyzingModel = false,
                                userMessage = "No se pudo extraer texto para buscar entidades"
                            )
                        }
                    }
                }
            }

            // 6. ANÁLISIS MULTIMODAL COMPLETO (TODA LA IMAGEN - BOTÓN SEPARADO)
            is ViewerEvent.TriggerFullMultimodalEnrichment -> {
                _uiState.update {
                    it.copy(
                        isEnriching = true,
                        activeAiMode = ActiveAiMode.NONE,
                        activeBoundingBoxes = emptyList()
                    )
                }
                viewModelScope.launch {
                    val result = enrichScreenshotUseCase(event.photo)
                    result.onSuccess { metadata ->
                        _uiState.update {
                            it.copy(
                                isEnriching = false,
                                multimodalMetadata = metadata,
                                showMultimodalDetailsSheet = true,
                                userMessage = "Analisis multimodal completado con exito"
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isEnriching = false,
                                userMessage = "Error en analisis multimodal: ${err.message}"
                            )
                        }
                    }
                }
            }

            is ViewerEvent.SelectDetectionBox -> {
                _uiState.update { it.copy(selectedDetectionId = event.id) }
            }
            ViewerEvent.CloseDetectionOverlay -> {
                _uiState.update {
                    it.copy(
                        activeAiMode = ActiveAiMode.NONE,
                        activeBoundingBoxes = emptyList(),
                        selectedDetectionId = null,
                        showOcrOverlay = false
                    )
                }
            }

            ViewerEvent.ToggleMultimodalDetailsSheet -> {
                _uiState.update { it.copy(showMultimodalDetailsSheet = !it.showMultimodalDetailsSheet) }
            }
            ViewerEvent.DismissMultimodalDetailsSheet -> {
                _uiState.update { it.copy(showMultimodalDetailsSheet = false) }
            }

            is ViewerEvent.SelectTextBlock -> {
                _uiState.update { it.copy(selectedBlockId = event.blockId, showOcrDetailsSheet = true) }
            }
            ViewerEvent.ToggleOcrDetailsSheet -> {
                _uiState.update { it.copy(showOcrDetailsSheet = !it.showOcrDetailsSheet) }
            }
            ViewerEvent.CloseOcrOverlay -> {
                _uiState.update {
                    it.copy(
                        showOcrOverlay = false,
                        showOcrDetailsSheet = false,
                        ocrResult = null,
                        selectedBlockId = null,
                        activeAiMode = ActiveAiMode.NONE,
                        activeBoundingBoxes = emptyList()
                    )
                }
            }

            is ViewerEvent.ToggleFavorite -> {
                val isFav = toggleFavoriteUseCase(event.photoId)
                _uiState.update {
                    it.copy(userMessage = if (isFav) "Añadida a favoritos" else "Eliminada de favoritos")
                }
            }
            is ViewerEvent.RequestDelete -> {
                _uiState.update { it.copy(photoPendingAction = event.photo, showDeleteConfirm = true) }
            }
            ViewerEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(showDeleteConfirm = false, photoPendingAction = null) }
            }
            ViewerEvent.ConfirmDelete -> {
                val photo = _uiState.value.photoPendingAction ?: return
                _uiState.update { it.copy(showDeleteConfirm = false) }
                viewModelScope.launch {
                    when (val result = deletePhotosUseCase(photo)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update { it.copy(isPhotoDeleted = true, userMessage = "Foto eliminada") }
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    pendingIntentSender = result.intentSender,
                                    isDeletePendingApproval = true
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update { it.copy(userMessage = result.message) }
                        }
                    }
                }
            }
            is ViewerEvent.RequestMove -> {
                _uiState.update { it.copy(photoPendingAction = event.photo, showFolderPickerForMove = true) }
            }
            ViewerEvent.DismissMoveDialog -> {
                _uiState.update { it.copy(showFolderPickerForMove = false, photoPendingAction = null) }
            }
            is ViewerEvent.ConfirmMove -> {
                val photo = _uiState.value.photoPendingAction ?: return
                _uiState.update { it.copy(showFolderPickerForMove = false) }
                viewModelScope.launch {
                    when (val result = movePhotosUseCase(photo, event.targetFolder)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update { it.copy(userMessage = "Foto movida a ${event.targetFolder}") }
                            loadPhotos()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update {
                                it.copy(
                                    pendingIntentSender = result.intentSender,
                                    isDeletePendingApproval = true
                                )
                            }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update { it.copy(userMessage = result.message) }
                        }
                    }
                }
            }
            is ViewerEvent.RequestCopy -> {
                _uiState.update { it.copy(photoPendingAction = event.photo, showFolderPickerForCopy = true) }
            }
            ViewerEvent.DismissCopyDialog -> {
                _uiState.update { it.copy(showFolderPickerForCopy = false, photoPendingAction = null) }
            }
            is ViewerEvent.ConfirmCopy -> {
                val photo = _uiState.value.photoPendingAction ?: return
                _uiState.update { it.copy(showFolderPickerForCopy = false) }
                viewModelScope.launch {
                    when (val result = copyPhotosUseCase(photo, event.targetFolder)) {
                        is MediaOperationResult.Success -> {
                            _uiState.update { it.copy(userMessage = "Copia guardada en ${event.targetFolder}") }
                            loadPhotos()
                        }
                        is MediaOperationResult.RequiresIntentSender -> {
                            _uiState.update { it.copy(pendingIntentSender = result.intentSender) }
                        }
                        is MediaOperationResult.Failure -> {
                            _uiState.update { it.copy(userMessage = result.message) }
                        }
                    }
                }
            }
            ViewerEvent.OnIntentSenderCompleted -> {
                val wasDelete = _uiState.value.isDeletePendingApproval
                _uiState.update {
                    it.copy(
                        pendingIntentSender = null,
                        isDeletePendingApproval = false,
                        isPhotoDeleted = wasDelete,
                        userMessage = "Operacion completada"
                    )
                }
                loadPhotos()
            }
            ViewerEvent.OnIntentSenderDismissed -> {
                _uiState.update {
                    it.copy(pendingIntentSender = null, isDeletePendingApproval = false)
                }
            }
            ViewerEvent.ClearUserMessage -> {
                _uiState.update { it.copy(userMessage = null) }
            }
        }
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            getAlbumsUseCase().collect { albums ->
                val names = albums.map { it.name }.distinct()
                _uiState.update { it.copy(existingFolders = names) }
            }
        }
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            getPhotosUseCase().collect { allPhotos ->
                val displayPhotos = if (!albumName.isNullOrEmpty()) {
                    allPhotos.filter { it.folderName == albumName }
                } else {
                    allPhotos
                }

                if (displayPhotos.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, photos = emptyList()) }
                    return@collect
                }

                val index = displayPhotos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        photos = displayPhotos,
                        initialIndex = index,
                        isLoading = false
                    )
                }
            }
        }
    }
}
