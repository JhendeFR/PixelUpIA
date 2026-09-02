package com.jhendefr.pixelupia.ui.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.Album
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder
import com.jhendefr.pixelupia.ui.common.ConfirmDeleteDialog
import com.jhendefr.pixelupia.ui.common.FolderPickerDialog
import com.jhendefr.pixelupia.ui.theme.PixelUpIAMotion
import java.util.ArrayList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (Photo) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.onEvent(GalleryEvent.Refresh)
        }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEvent(GalleryEvent.OnIntentSenderCompleted)
        } else {
            viewModel.onEvent(GalleryEvent.OnIntentSenderDismissed)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permission)
    }

    LaunchedEffect(uiState.pendingIntentSender) {
        uiState.pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(GalleryEvent.ClearUserMessage)
        }
    }

    // Dialogos de Gestion
    if (uiState.showDeleteConfirm) {
        ConfirmDeleteDialog(
            count = uiState.selectedPhotoIds.size,
            onConfirm = { viewModel.onEvent(GalleryEvent.ConfirmDeleteSelected) },
            onDismiss = { viewModel.onEvent(GalleryEvent.DismissDeleteDialog) }
        )
    }

    if (uiState.showFolderPickerForMove) {
        FolderPickerDialog(
            title = "Mover ${uiState.selectedPhotoIds.size} fotos",
            existingFolders = uiState.existingFolderNames,
            onFolderSelected = { folder -> viewModel.onEvent(GalleryEvent.ConfirmMoveSelected(folder)) },
            onDismiss = { viewModel.onEvent(GalleryEvent.DismissMoveDialog) }
        )
    }

    if (uiState.showFolderPickerForCopy) {
        FolderPickerDialog(
            title = "Copiar ${uiState.selectedPhotoIds.size} fotos",
            existingFolders = uiState.existingFolderNames,
            onFolderSelected = { folder -> viewModel.onEvent(GalleryEvent.ConfirmCopySelected(folder)) },
            onDismiss = { viewModel.onEvent(GalleryEvent.DismissCopyDialog) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = uiState.isSelectionMode,
                transitionSpec = {
                    (fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                            slideInVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it / 2 })
                        .togetherWith(
                            fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                                    slideOutVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it / 2 }
                        )
                },
                label = "TopBarAnimation"
            ) { isSelection ->
                if (isSelection) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${uiState.selectedPhotoIds.size} seleccionadas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.onEvent(GalleryEvent.ClearSelection) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar seleccion")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.onEvent(GalleryEvent.SelectAll) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todo")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = "PixelUp IA",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Ordenar"
                                )
                            }
                            SortMenu(
                                expanded = showSortMenu,
                                currentOrder = uiState.sortOrder,
                                onDismiss = { showSortMenu = false },
                                onSortChange = { viewModel.onEvent(GalleryEvent.ChangeSortOrder(it)) }
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!hasPermission) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.padding(24.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Permiso requerido",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "PixelUp IA necesita acceso a tus fotos para organizarlas y visualizarlas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(permission) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("Conceder Permiso", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                } else if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else if (uiState.errorMessage != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.errorMessage ?: "Error desconocido",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewModel.onEvent(GalleryEvent.Refresh) }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                } else {
                    // Selector de Pestañas con 3 Opciones (Fotos, Albumes, Favoritos)
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SegmentedButton(
                            selected = uiState.selectedTab == GalleryTab.PHOTOS,
                            onClick = { viewModel.onEvent(GalleryEvent.SelectTab(GalleryTab.PHOTOS)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = uiState.selectedTab == GalleryTab.PHOTOS)
                            }
                        ) {
                            Text("Fotos (${uiState.photos.size})", fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                        SegmentedButton(
                            selected = uiState.selectedTab == GalleryTab.ALBUMS,
                            onClick = { viewModel.onEvent(GalleryEvent.SelectTab(GalleryTab.ALBUMS)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = uiState.selectedTab == GalleryTab.ALBUMS)
                            }
                        ) {
                            Text("Albumes (${uiState.albums.size})", fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                        SegmentedButton(
                            selected = uiState.selectedTab == GalleryTab.FAVORITES,
                            onClick = { viewModel.onEvent(GalleryEvent.SelectTab(GalleryTab.FAVORITES)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = uiState.selectedTab == GalleryTab.FAVORITES)
                            }
                        ) {
                            Text("Favoritos (${uiState.favoritePhotos.size})", fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }

                    // Contenido segun la pestana seleccionada
                    when (uiState.selectedTab) {
                        GalleryTab.PHOTOS -> {
                            PhotoGrid(
                                photos = uiState.photos,
                                selectedIds = uiState.selectedPhotoIds,
                                isSelectionMode = uiState.isSelectionMode,
                                onPhotoClick = onPhotoClick,
                                onToggleSelection = { viewModel.onEvent(GalleryEvent.TogglePhotoSelection(it)) }
                            )
                        }
                        GalleryTab.ALBUMS -> {
                            AlbumGrid(
                                albums = uiState.albums,
                                onAlbumClick = onAlbumClick
                            )
                        }
                        GalleryTab.FAVORITES -> {
                            if (uiState.favoritePhotos.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No tienes fotos favoritas",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Toca el icono de corazon en el visor para guardar tus fotos preferidas.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                PhotoGrid(
                                    photos = uiState.favoritePhotos,
                                    selectedIds = uiState.selectedPhotoIds,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onPhotoClick = onPhotoClick,
                                    onToggleSelection = { viewModel.onEvent(GalleryEvent.TogglePhotoSelection(it)) }
                                )
                            }
                        }
                    }
                }
            }

            // BARRA INFERIOR FLOTANTE TIPO PILDORA (MATERIAL 3 EXPRESSIVE)
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it } +
                        fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring),
                exit = slideOutVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it } +
                        fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mover
                        IconButton(
                            onClick = { viewModel.onEvent(GalleryEvent.RequestMoveSelected) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = "Mover",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Copiar
                        IconButton(
                            onClick = { viewModel.onEvent(GalleryEvent.RequestCopySelected) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.FileCopy,
                                contentDescription = "Copiar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Compartir
                        IconButton(
                            onClick = {
                                sharePhotos(context, uiState.selectedPhotos)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Eliminar
                        IconButton(
                            onClick = { viewModel.onEvent(GalleryEvent.RequestDeleteSelected) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    photos: List<Photo>,
    selectedIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    onPhotoClick: (Photo) -> Unit,
    onToggleSelection: (Long) -> Unit = {}
) {
    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No hay fotos disponibles",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos, key = { it.id }) { photo ->
            val isSelected = selectedIds.contains(photo.id)

            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onToggleSelection(photo.id)
                            else onPhotoClick(photo)
                        },
                        onLongClick = { onToggleSelection(photo.id) }
                    )
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Seleccionada",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No hay albumes creados",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name }) { album ->
            Card(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                onClick = { onAlbumClick(album) }
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        AsyncImage(
                            model = album.coverUri,
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "${album.photoCount}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SortMenu(
    expanded: Boolean,
    currentOrder: SortOrder,
    onDismiss: () -> Unit,
    onSortChange: (SortOrder) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        SortMenuItem(
            label = "Fecha",
            categoryIcon = Icons.Default.DateRange,
            isSelected = currentOrder == SortOrder.DATE_DESC || currentOrder == SortOrder.DATE_ASC,
            isDesc = currentOrder == SortOrder.DATE_DESC,
            onClick = {
                val next = if (currentOrder == SortOrder.DATE_DESC) SortOrder.DATE_ASC else SortOrder.DATE_DESC
                onSortChange(next)
                onDismiss()
            }
        )
        SortMenuItem(
            label = "Nombre",
            categoryIcon = Icons.Default.SortByAlpha,
            isSelected = currentOrder == SortOrder.NAME_ASC || currentOrder == SortOrder.NAME_DESC,
            isDesc = currentOrder == SortOrder.NAME_DESC,
            onClick = {
                val next = if (currentOrder == SortOrder.NAME_ASC) SortOrder.NAME_DESC else SortOrder.NAME_ASC
                onSortChange(next)
                onDismiss()
            }
        )
        SortMenuItem(
            label = "Tamaño",
            categoryIcon = Icons.Default.Storage,
            isSelected = currentOrder == SortOrder.SIZE_DESC || currentOrder == SortOrder.SIZE_ASC,
            isDesc = currentOrder == SortOrder.SIZE_DESC,
            onClick = {
                val next = if (currentOrder == SortOrder.SIZE_DESC) SortOrder.SIZE_ASC else SortOrder.SIZE_DESC
                onSortChange(next)
                onDismiss()
            }
        )
    }
}

@Composable
fun SortMenuItem(
    label: String,
    categoryIcon: ImageVector,
    isSelected: Boolean,
    isDesc: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent

    DropdownMenuItem(
        modifier = Modifier
            .background(backgroundColor)
            .minimumInteractiveComponentSize(),
        text = {
            Text(text = label, color = contentColor, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
        },
        leadingIcon = {
            Icon(imageVector = categoryIcon, contentDescription = null, tint = contentColor)
        },
        trailingIcon = {
            if (isSelected) {
                val rotation by animateFloatAsState(
                    targetValue = if (isDesc) 0f else 180f,
                    animationSpec = PixelUpIAMotion.spatialFloatSpring,
                    label = "arrowRotation"
                )
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = contentColor
                )
            }
        },
        onClick = onClick
    )
}

fun sharePhotos(context: Context, photos: List<Photo>) {
    if (photos.isEmpty()) return
    if (photos.size == 1) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = photos.first().mimeType
            putExtra(Intent.EXTRA_STREAM, photos.first().uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir foto"))
    } else {
        val uris = ArrayList<Uri>().apply {
            photos.forEach { add(it.uri) }
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ${photos.size} fotos"))
    }
}
