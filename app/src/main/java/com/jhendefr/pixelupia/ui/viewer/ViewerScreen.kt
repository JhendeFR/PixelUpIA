package com.jhendefr.pixelupia.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.ui.common.ConfirmDeleteDialog
import com.jhendefr.pixelupia.ui.common.FolderPickerDialog
import com.jhendefr.pixelupia.ui.gallery.sharePhotos
import com.jhendefr.pixelupia.ui.theme.PixelUpIAMotion
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    isFromAi: Boolean = false,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showUI by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Retroceso nativo escalonado: sheet multimodal -> sheet ocr -> overlay bounding boxes -> modo IA
    BackHandler(enabled = uiState.showMultimodalDetailsSheet) {
        viewModel.onEvent(ViewerEvent.DismissMultimodalDetailsSheet)
    }

    BackHandler(enabled = !uiState.showMultimodalDetailsSheet && uiState.showOcrDetailsSheet) {
        viewModel.onEvent(ViewerEvent.ToggleOcrDetailsSheet)
    }

    BackHandler(enabled = !uiState.showMultimodalDetailsSheet && !uiState.showOcrDetailsSheet && uiState.activeAiMode != ActiveAiMode.NONE) {
        viewModel.onEvent(ViewerEvent.CloseDetectionOverlay)
    }

    BackHandler(enabled = !uiState.showMultimodalDetailsSheet && !uiState.showOcrDetailsSheet && uiState.activeAiMode == ActiveAiMode.NONE && uiState.isAiModeActive) {
        viewModel.onEvent(ViewerEvent.CloseAiMode)
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEvent(ViewerEvent.OnIntentSenderCompleted)
        } else {
            viewModel.onEvent(ViewerEvent.OnIntentSenderDismissed)
        }
    }

    LaunchedEffect(uiState.pendingIntentSender) {
        uiState.pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    LaunchedEffect(uiState.isPhotoDeleted) {
        if (uiState.isPhotoDeleted) {
            onBackClick()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onEvent(ViewerEvent.ClearUserMessage)
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No se encontraron fotos",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.initialIndex.coerceIn(0, (uiState.photos.size - 1).coerceAtLeast(0)),
        pageCount = { uiState.photos.size }
    )

    val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage) ?: uiState.photos.first()

    LaunchedEffect(pagerState.currentPage) {
        val photo = uiState.photos.getOrNull(pagerState.currentPage)
        if (photo != null) {
            viewModel.onEvent(ViewerEvent.LoadPhotoMetadata(photo.id))
            viewModel.onEvent(ViewerEvent.CloseDetectionOverlay)
        }
    }

    // Dialogos de Gestion
    if (uiState.showDeleteConfirm) {
        ConfirmDeleteDialog(
            count = 1,
            onConfirm = { viewModel.onEvent(ViewerEvent.ConfirmDelete) },
            onDismiss = { viewModel.onEvent(ViewerEvent.DismissDeleteDialog) }
        )
    }

    if (uiState.showFolderPickerForMove) {
        FolderPickerDialog(
            title = "Mover foto",
            existingFolders = uiState.existingFolders,
            onFolderSelected = { folder -> viewModel.onEvent(ViewerEvent.ConfirmMove(folder)) },
            onDismiss = { viewModel.onEvent(ViewerEvent.DismissMoveDialog) }
        )
    }

    if (uiState.showFolderPickerForCopy) {
        FolderPickerDialog(
            title = "Copiar foto",
            existingFolders = uiState.existingFolders,
            onFolderSelected = { folder -> viewModel.onEvent(ViewerEvent.ConfirmCopy(folder)) },
            onDismiss = { viewModel.onEvent(ViewerEvent.DismissCopyDialog) }
        )
    }

    // BottomSheet para Metadatos Estandar
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PhotoMetadataPanel(photo = currentPhoto)
        }
    }

    // BottomSheet para Detalles y Copia de OCR
    if (uiState.showOcrDetailsSheet && uiState.ocrResult != null) {
        OcrDetailsBottomSheet(
            ocrResult = uiState.ocrResult!!,
            selectedBlockId = uiState.selectedBlockId,
            onSelectBlock = { viewModel.onEvent(ViewerEvent.SelectTextBlock(it)) },
            onDismiss = { viewModel.onEvent(ViewerEvent.ToggleOcrDetailsSheet) },
            onCloseOcr = { viewModel.onEvent(ViewerEvent.CloseOcrOverlay) }
        )
    }

    // BottomSheet para Analisis Multimodal Completo (Toda la Imagen)
    if (uiState.showMultimodalDetailsSheet) {
        MultimodalDetailsBottomSheet(
            metadata = uiState.multimodalMetadata,
            isEnriching = uiState.isEnriching,
            onTriggerEnrichment = {
                viewModel.onEvent(ViewerEvent.TriggerFullMultimodalEnrichment(currentPhoto))
            },
            onAddTag = { tag ->
                viewModel.onEvent(ViewerEvent.AddTag(currentPhoto.id, tag))
            },
            onRemoveTag = { tag ->
                viewModel.onEvent(ViewerEvent.RemoveTag(currentPhoto.id, tag))
            },
            onDismiss = {
                viewModel.onEvent(ViewerEvent.DismissMultimodalDetailsSheet)
            }
        )
    }

    var isPagerEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Pager Horizontal de Fotos
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isPagerEnabled && uiState.activeAiMode == ActiveAiMode.NONE,
            pageSpacing = 16.dp
        ) { page ->
            val photo = uiState.photos[page]
            ZoomableImage(
                photo = photo,
                onTap = { showUI = !showUI },
                onSwipeUp = { showInfoSheet = true },
                onZoomChanged = { isPagerEnabled = it <= 1.05f }
            )
        }

        // Overlay Universal de Bounding Boxes (para cualquier modelo de IA activo)
        if (uiState.activeAiMode != ActiveAiMode.NONE && uiState.activeBoundingBoxes.isNotEmpty()) {
            UniversalAiOverlay(
                boxes = uiState.activeBoundingBoxes,
                imageWidth = uiState.activeImageDimensions.first,
                imageHeight = uiState.activeImageDimensions.second,
                selectedId = uiState.selectedDetectionId,
                onSelectItem = { viewModel.onEvent(ViewerEvent.SelectDetectionBox(it)) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Indicador de Análisis de Modelo Independiente en Progreso
        if (uiState.isAnalyzingModel) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.modelAnalysisTitle ?: "Analizando...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Indicador de Enriquecimiento Multimodal Completo en Progreso
        if (uiState.isEnriching) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Ejecutando analisis completo de todos los modelos...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Top Bar como Overlay Tonal
        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                    slideInVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it },
            exit = fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                    slideOutVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = {
                            Text(
                                text = currentPhoto.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Regresar",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            if (uiState.activeAiMode != ActiveAiMode.NONE) {
                                FilledTonalButton(
                                    onClick = { viewModel.onEvent(ViewerEvent.CloseDetectionOverlay) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ocultar Cajas",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        // Barra Inferior Flotante Estilo Píldora M3 Expressive
        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                    slideInVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it },
            exit = fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                    slideOutVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 12.dp, end = 12.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                AnimatedContent(
                    targetState = uiState.isAiModeActive,
                    transitionSpec = {
                        (fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                                slideInHorizontally(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it / 3 })
                            .togetherWith(
                                fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                                        slideOutHorizontally(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it / 3 }
                            )
                    },
                    label = "BottomBarPillTransition"
                ) { isAiActive ->
                    if (isAiActive) {
                        // Píldora de Funciones IA: Modelos Independientes + Análisis Completo Separado
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. TEXTO (OCR) INDEPENDIENTE
                            val isOcrActive = uiState.activeAiMode == ActiveAiMode.OCR
                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.TriggerInteractiveOcr(currentPhoto.uri)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = "Texto OCR",
                                    tint = if (isOcrActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 2. ROSTROS INDEPENDIENTE
                            val isFacesActive = uiState.activeAiMode == ActiveAiMode.FACES
                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.TriggerFaceDetection(currentPhoto.uri)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Rostros",
                                    tint = if (isFacesActive) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 3. OBJETOS Y PRECISIÓN INDEPENDIENTE
                            val isObjectsActive = uiState.activeAiMode == ActiveAiMode.OBJECTS
                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.TriggerObjectDetection(currentPhoto.uri)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = "Objetos y precision",
                                    tint = if (isObjectsActive) Color(0xFF00B0FF) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 4. CÓDIGOS DE BARRAS / QR INDEPENDIENTE
                            val isBarcodesActive = uiState.activeAiMode == ActiveAiMode.BARCODES
                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.TriggerBarcodeScanning(currentPhoto.uri)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Codigos QR",
                                    tint = if (isBarcodesActive) Color(0xFFFF9100) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 5. ENTIDADES INDEPENDIENTE
                            val isEntitiesActive = uiState.activeAiMode == ActiveAiMode.ENTITIES
                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.TriggerEntityExtraction(currentPhoto.uri)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Entidades",
                                    tint = if (isEntitiesActive) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Separador Vertical
                            VerticalDivider(
                                modifier = Modifier
                                    .height(26.dp)
                                    .padding(horizontal = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )

                            // 6. BOTÓN SEPARADO: ANÁLISIS MULTIMODAL COMPLETO (TODA LA IMAGEN)
                            FilledTonalButton(
                                onClick = {
                                    if (uiState.multimodalMetadata?.isEnriched == true) {
                                        viewModel.onEvent(ViewerEvent.ToggleMultimodalDetailsSheet)
                                    } else {
                                        viewModel.onEvent(ViewerEvent.TriggerFullMultimodalEnrichment(currentPhoto))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.multimodalMetadata?.isEnriched == true) "Ver Datos" else "Completo",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // 7. BOTÓN PARA INDEXAR O DESINDEXAR IMAGEN INDIVIDUAL
                            FilledTonalButton(
                                onClick = {
                                    if (uiState.isPhotoIndexed) {
                                        viewModel.onEvent(ViewerEvent.UnindexCurrentPhoto(currentPhoto.id))
                                    } else {
                                        viewModel.onEvent(ViewerEvent.IndexCurrentPhoto(currentPhoto))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = CircleShape,
                                colors = if (uiState.isPhotoIndexed) {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPhotoIndexed) Icons.Default.LayersClear else Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isPhotoIndexed) "Desindexar" else "Indexar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // 8. BOTÓN PARA SALIR DEL MODO IA
                            FilledTonalIconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.CloseAiMode) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar modo IA",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        // Barra Estándar (Favorito, Compartir, Activar Modo IA, Eliminar, Más Opciones)
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isFavorite = uiState.favoritePhotoIds.contains(currentPhoto.id)
                            val favScale by animateFloatAsState(
                                targetValue = if (isFavorite) 1.2f else 1f,
                                animationSpec = PixelUpIAMotion.spatialFloatSpring,
                                label = "FavScale"
                            )

                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.ToggleFavorite(currentPhoto.id)) },
                                modifier = Modifier
                                    .scale(favScale)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { sharePhotos(context, listOf(currentPhoto)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Compartir",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Botón de IA: Al presionarlo, transmuta la píldora por las funciones de IA
                            if (isFromAi) {
                                IconButton(
                                    onClick = { viewModel.onEvent(ViewerEvent.ToggleAiMode) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Activar funciones IA",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { showInfoSheet = true },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Informacion",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.onEvent(ViewerEvent.RequestDelete(currentPhoto)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Mas opciones",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Mover a carpeta") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.onEvent(ViewerEvent.RequestMove(currentPhoto))
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copiar a carpeta") },
                                        leadingIcon = { Icon(Icons.Default.FileCopy, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.onEvent(ViewerEvent.RequestCopy(currentPhoto))
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                    HorizontalDivider()
                                    if (uiState.isPhotoIndexed) {
                                        DropdownMenuItem(
                                            text = { Text("Desindexar de IA") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.LayersClear,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.onEvent(ViewerEvent.UnindexCurrentPhoto(currentPhoto.id))
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Indexar con IA") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.onEvent(ViewerEvent.IndexCurrentPhoto(currentPhoto))
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }
}

@Composable
fun ZoomableImage(
    photo: Photo,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onZoomChanged: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        coroutineScope.launch {
                            val targetScale = if (scale.value > 1f) 1f else 3f
                            onZoomChanged(targetScale)
                            launch { scale.animateTo(targetScale, animationSpec = PixelUpIAMotion.spatialFloatSpring) }
                            launch { offsetX.animateTo(0f, animationSpec = PixelUpIAMotion.spatialFloatSpring) }
                            launch { offsetY.animateTo(0f, animationSpec = PixelUpIAMotion.spatialFloatSpring) }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.size
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (pointers >= 2 || scale.value > 1.05f) {
                            if (zoom != 1f || pan != Offset.Zero) {
                                event.changes.forEach { it.consume() }

                                coroutineScope.launch {
                                    val newScale = (scale.value * zoom).coerceIn(1f, 5f)
                                    scale.snapTo(newScale)
                                    onZoomChanged(newScale)

                                    if (newScale > 1f) {
                                        val scaledWidth = containerSize.width * newScale
                                        val scaledHeight = containerSize.height * newScale
                                        val maxOffsetX = (scaledWidth - containerSize.width).coerceAtLeast(0f) / 2f
                                        val maxOffsetY = (scaledHeight - containerSize.height).coerceAtLeast(0f) / 2f

                                        offsetX.snapTo((offsetX.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX))
                                        offsetY.snapTo((offsetY.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY))
                                    } else {
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }
                                }
                            }
                        } else if (pointers == 1 && scale.value <= 1.05f) {
                            val change = event.changes.first()
                            val dragY = change.position.y - change.previousPosition.y
                            val dragX = change.position.x - change.previousPosition.x

                            if (dragY < -25f && Math.abs(dragY) > Math.abs(dragX) * 2) {
                                onSwipeUp()
                                change.consume()
                                break
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)

        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offsetX.value,
                    translationY = offsetY.value
                )
        )
    }
}

@Composable
fun PhotoMetadataPanel(photo: Photo) {
    val dateString = remember(photo.dateTaken) {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        dateFormatter.format(Date(photo.dateTaken))
    }
    val sizeInMb = photo.size / (1024f * 1024f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Detalles de la imagen",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        MetadataRow(Icons.Default.Image, "Nombre", photo.name)
        MetadataRow(Icons.Default.DateRange, "Fecha", dateString)
        MetadataRow(Icons.Default.Folder, "Carpeta", photo.folderName)
        MetadataRow(Icons.Default.Storage, "Tamaño", String.format(Locale.US, "%.2f MB", sizeInMb))
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
