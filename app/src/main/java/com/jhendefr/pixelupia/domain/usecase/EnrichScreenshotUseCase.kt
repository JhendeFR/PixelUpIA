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
    private val textRecognitionDataSource: TextRecognitionDataSource,
    private val context: android.content.Context? = null,
    private val faceClusteringService: com.jhendefr.pixelupia.data.ml.FaceClusteringService? = null,
    private val objectDetectionDataSource: com.jhendefr.pixelupia.data.ml.ObjectDetectionDataSource? = null
) {
    suspend operator fun invoke(photo: Photo): Result<MultimodalMetadata> = withContext(Dispatchers.IO) {
        try {
            var existingEntity = smartGalleryDao.getPhotoById(photo.id)

            // Si la foto no esta en Room o no tiene OCR previo, ejecutar OCR primero
            var textContent = existingEntity?.textContent ?: ""
            if (existingEntity == null) {
                val ocrResult = textRecognitionDataSource.processImage(photo.uri)
                textContent = ocrResult.text
                val sourceApp = photo.sourceApp ?: com.jhendefr.pixelupia.domain.classifier.ScreenshotSourceClassifier.extractSourceApp(photo.name)
                val entity = SmartPhotoEntity(
                    photoId = photo.id,
                    uri = photo.uri.toString(),
                    name = photo.name,
                    folderName = photo.folderName,
                    dateAdded = photo.dateTaken,
                    textContent = ocrResult.text,
                    boundingBoxes = ocrResult.boundingBoxesJson,
                    sourceApp = sourceApp,
                    searchContent = "$textContent ${sourceApp ?: ""}".trim()
                )
                smartGalleryDao.insertPhoto(entity)
                existingEntity = entity
            } else if (textContent.isBlank()) {
                val ocrResult = textRecognitionDataSource.processImage(photo.uri)
                textContent = ocrResult.text
                val updatedEntity = existingEntity.copy(
                    textContent = ocrResult.text,
                    boundingBoxes = ocrResult.boundingBoxesJson,
                    searchContent = "${ocrResult.text} ${existingEntity.sourceApp ?: ""}".trim()
                )
                smartGalleryDao.insertPhoto(updatedEntity)
                existingEntity = updatedEntity
            }

            // Ejecutar los modelos de forma concurrente para maxima eficiencia
            val facesDetailedDeferred = async { faceDetectionDataSource.detectFacesDetailed(photo.uri) }
            val barcodesDeferred = async { barcodeDataSource.scanBarcodes(photo.uri) }
            val labelsDeferred = async { imageLabelingDataSource.labelImage(photo.uri) }
            val entitiesDeferred = async { entityExtractionDataSource.extractEntities(textContent) }
            val objectsDeferred = if (objectDetectionDataSource != null) {
                async { objectDetectionDataSource.detectObjects(photo.uri) }
            } else null

            val facesDetailed = facesDetailedDeferred.await()
            val barcodes = barcodesDeferred.await()
            val labelItems = labelsDeferred.await()
            val entityItems = entitiesDeferred.await()
            val objectsResult = objectsDeferred?.await()

            val hasFaces = facesDetailed.faces.isNotEmpty()
            val formattedLabels = labelItems.map { it.formatted }
            val formattedEntities = entityItems.map { "${it.text} (${it.type})" }

            val multimodalResult = MultimodalMetadata(
                photoId = photo.id,
                hasFaces = hasFaces,
                barcodes = barcodes,
                labels = formattedLabels,
                entities = formattedEntities,
                isEnriched = true
            )

            // Deteccion de Mascotas y eliminacion estricta de falsos positivos en documentos/facturas
            val ocrBlockCount = existingEntity?.boundingBoxes?.let { json ->
                Regex("""\{"text":""").findAll(json).count()
            } ?: 0

            val hasMonetaryOrBankingEntity = entityItems.any { entity ->
                val type = entity.type.lowercase(java.util.Locale.ROOT)
                val text = entity.text.lowercase(java.util.Locale.ROOT)
                type.contains("money") || type.contains("iban") || type.contains("payment") ||
                        text.contains("tarjeta") || text.contains("iban") || text.contains("bs") || text.contains("usd")
            }

            val hasInvoiceKeywords = listOf("factura", "recibo", "total", "subtotal", "nit", "pago", "bs", "usd", "cambio").any { kw ->
                textContent.contains(kw, ignoreCase = true)
            }

            val isDocumentOrInvoice = ocrBlockCount > 12 || hasMonetaryOrBankingEntity || hasInvoiceKeywords

            val isScreenshot = photo.folderName.contains("screenshot", ignoreCase = true) ||
                    photo.folderName.equals("Screenshots", ignoreCase = true) ||
                    photo.name.contains("screenshot", ignoreCase = true) ||
                    photo.sourceApp != null

            val isPetLabelDetected = !isDocumentOrInvoice && !isScreenshot && (
                labelItems.any { labelItem ->
                    val lower = labelItem.text.lowercase(java.util.Locale.ROOT)
                    lower.contains("perro") || lower.contains("gato") || lower.contains("mascota") ||
                            lower.contains("canino") || lower.contains("felino") || lower.contains("cachorro") ||
                            lower.contains("gatito") || lower.contains("dog") || lower.contains("cat") ||
                            lower.contains("pet") || lower.contains("puppy") || lower.contains("kitten") ||
                            lower.contains("canidae") || lower.contains("felidae") || lower.contains("raza canina") ||
                            lower.contains("hound") || lower.contains("terrier") || lower.contains("retriever") ||
                            lower.contains("bulldog") || lower.contains("shepherd") || lower.contains("siam") ||
                            lower.contains("persa") || lower.contains("animal") || lower.contains("fauna") ||
                            lower.contains("carnivore") || lower.contains("mammal") ||
                            lower.contains("carnivoro") || lower.contains("mamifero") || lower.contains("vertebrado") ||
                            lower.contains("snout") || lower.contains("hocico") || lower.contains("whiskers") ||
                            lower.contains("bigotes") || lower.contains("fur") || lower.contains("pelaje") ||
                            lower.contains("paw") || lower.contains("pata") || lower.contains("tail") ||
                            lower.contains("cola") || lower.contains("ave") || lower.contains("pajaro") ||
                            lower.contains("conejo") || lower.contains("rabbit") || lower.contains("hamster")
                } || (
                    facesDetailed.faces.isEmpty() && (
                        photo.folderName.contains("mascota", ignoreCase = true) ||
                        photo.folderName.contains("pet", ignoreCase = true) ||
                        photo.folderName.contains("perro", ignoreCase = true) ||
                        photo.folderName.contains("gato", ignoreCase = true) ||
                        photo.folderName.contains("animal", ignoreCase = true) ||
                        photo.name.contains("perro", ignoreCase = true) ||
                        photo.name.contains("gato", ignoreCase = true) ||
                        photo.name.contains("dog", ignoreCase = true) ||
                        photo.name.contains("cat", ignoreCase = true) ||
                        photo.name.contains("pet", ignoreCase = true) ||
                        photo.name.contains("animal", ignoreCase = true)
                    )
                )
            )

            // Deteccion robusta de mascotas: objetos identificados o encuadre central si la foto es de un animal
            val petBoxes = if (!isDocumentOrInvoice && !isScreenshot && isPetLabelDetected) {
                val faceBoxes = facesDetailed.faces.map { it.box }
                val detectedPetObjects = objectsResult?.objects?.filter { obj ->
                    val isAnimal = (
                            obj.label.contains("perro", ignoreCase = true) ||
                            obj.label.contains("gato", ignoreCase = true) ||
                            obj.label.contains("mascota", ignoreCase = true) ||
                            obj.label.contains("animal", ignoreCase = true) ||
                            obj.label.contains("dog", ignoreCase = true) ||
                            obj.label.contains("cat", ignoreCase = true) ||
                            obj.label.contains("pet", ignoreCase = true)
                    )
                    val doesNotOverlapHuman = faceBoxes.none { fBox -> android.graphics.Rect.intersects(fBox, obj.box) }
                    isAnimal && doesNotOverlapHuman
                }?.map { it.box } ?: emptyList()

                if (detectedPetObjects.isNotEmpty()) {
                    detectedPetObjects
                } else {
                    val imgW = facesDetailed.imageWidth
                    val imgH = facesDetailed.imageHeight
                    if (imgW > 50 && imgH > 50) {
                        val padW = (imgW * 0.10f).toInt()
                        val padH = (imgH * 0.10f).toInt()
                        listOf(android.graphics.Rect(padW, padH, imgW - padW, imgH - padH))
                    } else {
                        emptyList()
                    }
                }
            } else {
                emptyList()
            }

            val isPetDetected = petBoxes.isNotEmpty()
            // Las capturas de pantalla NO deben asociarse a grupos de personas
            val faceCandidates = if (isScreenshot) emptyList() else facesDetailed.faces

            // Agrupamiento de Rostros y Mascotas con FaceClusteringService
            if (faceClusteringService != null && context != null) {
                try {
                    if (faceCandidates.isNotEmpty() || petBoxes.isNotEmpty()) {
                        val bitmap = com.jhendefr.pixelupia.data.ml.ImageUtils.loadCorrectlyOrientedBitmap(context, photo.uri)
                        if (bitmap != null) {
                            val imgW = if (facesDetailed.imageWidth > 0) facesDetailed.imageWidth else bitmap.width
                            val imgH = if (facesDetailed.imageHeight > 0) facesDetailed.imageHeight else bitmap.height

                            val scaleX = bitmap.width.toFloat() / imgW.toFloat()
                            val scaleY = bitmap.height.toFloat() / imgH.toFloat()

                            val scaledFaces = faceCandidates.map { face ->
                                val scaledBox = android.graphics.Rect(
                                    (face.box.left * scaleX).toInt().coerceIn(0, bitmap.width),
                                    (face.box.top * scaleY).toInt().coerceIn(0, bitmap.height),
                                    (face.box.right * scaleX).toInt().coerceIn(0, bitmap.width),
                                    (face.box.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                                )
                                val scaledLeftEye = face.leftEye?.let {
                                    android.graphics.PointF(it.x * scaleX, it.y * scaleY)
                                }
                                val scaledRightEye = face.rightEye?.let {
                                    android.graphics.PointF(it.x * scaleX, it.y * scaleY)
                                }
                                face.copy(box = scaledBox, leftEye = scaledLeftEye, rightEye = scaledRightEye)
                            }

                            val scaledPetBoxes = petBoxes.map { box ->
                                android.graphics.Rect(
                                    (box.left * scaleX).toInt().coerceIn(0, bitmap.width),
                                    (box.top * scaleY).toInt().coerceIn(0, bitmap.height),
                                    (box.right * scaleX).toInt().coerceIn(0, bitmap.width),
                                    (box.bottom * scaleY).toInt().coerceIn(0, bitmap.height)
                                )
                            }

                            faceClusteringService.clusterPhotoFacesAndPets(
                                photo = photo,
                                bitmap = bitmap,
                                detectedFaces = scaledFaces,
                                petBoundingBoxes = scaledPetBoxes
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Continuar si ocurre error al clusterizar imagen individual
                }
            }

            // Construir indice concatenado enriquecido con expansion de sinonimos para busqueda FTS5
            val cleanLabels = multimodalResult.labels.map {
                if (it.contains(" - ")) it.substringBeforeLast(" - ") else it
            }
            val expandedLabelsContent = cleanLabels.joinToString(" ") { label ->
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent(label)
            }

            val faceTerms = if (multimodalResult.hasFaces) {
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent("persona") + " rostro cara"
            } else ""

            val petTerms = if (isPetDetected) {
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent("perro") + " " +
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent("gato") + " mascota animal"
            } else ""

            val sourceAppTerm = existingEntity?.sourceApp ?: photo.sourceApp ?: ""
            val barcodeTerms = if (multimodalResult.barcodes.isNotEmpty()) {
                "QR Codigo Barcode " + multimodalResult.barcodes.joinToString(" ")
            } else ""

            val entityTerms = multimodalResult.entities.map {
                if (it.contains(" (")) it.substringBeforeLast(" (") else it
            }.joinToString(" ")

            val paymentTerms = if (multimodalResult.entities.any {
                it.startsWith("Dinero:", ignoreCase = true) ||
                it.startsWith("Tarjeta:", ignoreCase = true) ||
                it.startsWith("IBAN:", ignoreCase = true)
            } || multimodalResult.labels.any { it.contains("Recibo", ignoreCase = true) || it.contains("Dinero", ignoreCase = true) }) {
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent("factura") + " " +
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent("dinero")
            } else ""

            val documentTerms = if (multimodalResult.labels.any {
                it.contains("Pasaporte", ignoreCase = true) || it.contains("Papel", ignoreCase = true)
            }) {
                com.jhendefr.pixelupia.data.ml.SynonymRegistry.expandForSearchContent("documento")
            } else ""

            val concatenatedSearchContent = buildString {
                append(textContent)
                if (sourceAppTerm.isNotBlank()) append(" $sourceAppTerm")
                if (faceTerms.isNotBlank()) append(" $faceTerms")
                if (petTerms.isNotBlank()) append(" $petTerms")
                if (expandedLabelsContent.isNotBlank()) append(" $expandedLabelsContent")
                if (barcodeTerms.isNotBlank()) append(" $barcodeTerms")
                if (entityTerms.isNotBlank()) append(" $entityTerms")
                if (paymentTerms.isNotBlank()) append(" $paymentTerms")
                if (documentTerms.isNotBlank()) append(" $documentTerms")
            }.trim()

            // Actualizar Room con los metadatos generados y sincronizar FTS5
            smartGalleryDao.updateEnrichment(
                photoId = photo.id,
                hasFaces = multimodalResult.hasFaces,
                barcodes = multimodalResult.barcodes,
                labels = multimodalResult.labels,
                entities = multimodalResult.entities,
                searchContent = concatenatedSearchContent
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
