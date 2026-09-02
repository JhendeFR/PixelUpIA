package com.jhendefr.pixelupia.ui.viewer

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showUI by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

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

    // BottomSheet para Metadatos con Superficie Tonal M3E
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
            userScrollEnabled = isPagerEnabled && !uiState.showOcrOverlay,
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

        // Overlay de Bounding Boxes de OCR (Si está activo)
        if (uiState.showOcrOverlay && uiState.ocrResult != null) {
            OcrBoundingBoxOverlay(
                ocrResult = uiState.ocrResult!!,
                selectedBlockId = uiState.selectedBlockId,
                onSelectBlock = { viewModel.onEvent(ViewerEvent.SelectTextBlock(it)) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Indicador de Escaneo OCR en Progreso
        if (uiState.isOcrScanning) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
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
                        text = "Analizando con OCR...",
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
                            // Pildora para Probar OCR
                            FilledTonalButton(
                                onClick = {
                                    if (uiState.showOcrOverlay) {
                                        viewModel.onEvent(ViewerEvent.ToggleOcrDetailsSheet)
                                    } else {
                                        viewModel.onEvent(ViewerEvent.TriggerInteractiveOcr(currentPhoto.uri))
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.showOcrOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                    contentColor = if (uiState.showOcrOverlay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.showOcrOverlay) "Ver Texto" else "Probar OCR",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorito con Animacion de Rebote
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

                    // Compartir
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

                    // Informacion
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

                    // Eliminar
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

                    // Mas Opciones (Mover / Copiar)
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
