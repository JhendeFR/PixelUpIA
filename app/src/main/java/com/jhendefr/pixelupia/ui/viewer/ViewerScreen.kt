package com.jhendefr.pixelupia.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.Photo
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
    var showUI by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron fotos", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.initialIndex,
        pageCount = { uiState.photos.size }
    )

    // Estado de scroll del Pager (se desactiva si hay zoom)
    var isPagerEnabled by remember { mutableStateOf(true) }

    // BottomSheet para los metadatos (Info)
    if (showInfoSheet) {
        val currentPhoto = uiState.photos[pagerState.currentPage]
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PhotoMetadataPanel(photo = currentPhoto)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Pager Horizontal para deslizar entre imágenes (Ocupa toda la pantalla)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isPagerEnabled,
            pageSpacing = 16.dp
        ) { page ->
            val photo = uiState.photos[page]
            ZoomableImage(
                photo = photo,
                onTap = { showUI = !showUI },
                onSwipeUp = { showInfoSheet = true },
                onZoomChanged = { isPagerEnabled = it <= 1f }
            )
        }

        // 2. Top Bar como Overlay
        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            // Usamos Surface para darle el fondo semitransparente que antes daba el Scaffold
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Regresar",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        // 3. Barra Inferior Estilo Píldora
        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Favorito */ }) {
                        Icon(Icons.Default.FavoriteBorder, "Favorito", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { /* Editar */ }) {
                        Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(Icons.Default.Info, "Información", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { /* Eliminar */ }) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Más", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(text = { Text("Mover") }, onClick = { showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Copiar") }, onClick = { showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Compartir") }, onClick = { showMoreMenu = false })
                        }
                    }
                }
            }
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
                            launch { scale.animateTo(targetScale) }
                            launch { offsetX.animateTo(0f) }
                            launch { offsetY.animateTo(0f) }
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

                        // 1. Zoom/Pan: Solo consumimos si hay movimiento real
                        // Esto permite que detectTapGestures vea los eventos de "tap"
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
                        }
                        // 2. Swipe Up: Solo un dedo y escala 1
                        else if (pointers == 1 && scale.value <= 1.05f) {
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
        Text("Detalles de la imagen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        MetadataRow(Icons.Default.Image, "Nombre", photo.name)
        MetadataRow(Icons.Default.DateRange, "Fecha", dateString)
        MetadataRow(Icons.Default.Folder, "Carpeta", photo.folderName)
        MetadataRow(Icons.Default.Storage, "Tamaño", String.format(Locale.US, "%.2f MB", sizeInMb))
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
