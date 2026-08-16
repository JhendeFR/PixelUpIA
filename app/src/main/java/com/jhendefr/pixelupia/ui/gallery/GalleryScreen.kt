package com.jhendefr.pixelupia.ui.gallery

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.Album
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (Photo) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.onEvent(GalleryEvent.Refresh)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PixelUpIA",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Se requiere permiso para ver la galería 🖼️")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { launcher.launch(permission) }) {
                            Text("Conceder Permiso")
                        }
                    }
                }
            } else if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // Pestañas de Navegación: Fotos y Álbumes
                TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                    Tab(
                        selected = uiState.selectedTab == GalleryTab.PHOTOS,
                        onClick = { viewModel.onEvent(GalleryEvent.SelectTab(GalleryTab.PHOTOS)) },
                        text = { Text("Fotos (${uiState.photos.size})") }
                    )
                    Tab(
                        selected = uiState.selectedTab == GalleryTab.ALBUMS,
                        onClick = { viewModel.onEvent(GalleryEvent.SelectTab(GalleryTab.ALBUMS)) },
                        text = { Text("Álbumes (${uiState.albums.size})") }
                    )
                }

                // Contenido según la pestaña seleccionada
                when (uiState.selectedTab) {
                    GalleryTab.PHOTOS -> {
                        PhotoGrid(
                            photos = uiState.photos,
                            onPhotoClick = onPhotoClick
                        )
                    }
                    GalleryTab.ALBUMS -> {
                        AlbumGrid(
                            albums = uiState.albums,
                            onAlbumClick = onAlbumClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoGrid(
    photos: List<Photo>,
    onPhotoClick: (Photo) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos, key = { it.id }) { photo ->
            Card(
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1f),
                onClick = { onPhotoClick(photo) }
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun AlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name }) { album ->
            Card(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth(),
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
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "${album.photoCount}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
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
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Opción: Fecha
        SortMenuItem(
            label = "Fecha",
            categoryIcon = Icons.Default.DateRange,
            isSelected = currentOrder == SortOrder.DATE_DESC || currentOrder == SortOrder.DATE_ASC,
            isDesc = currentOrder == SortOrder.DATE_DESC,
            onClick = {
                val next = if (currentOrder == SortOrder.DATE_DESC) SortOrder.DATE_ASC else SortOrder.DATE_DESC
                onSortChange(next)
            }
        )
        // Opción: Nombre
        SortMenuItem(
            label = "Nombre",
            categoryIcon = Icons.Default.SortByAlpha,
            isSelected = currentOrder == SortOrder.NAME_ASC || currentOrder == SortOrder.NAME_DESC,
            isDesc = currentOrder == SortOrder.NAME_DESC,
            onClick = {
                val next = if (currentOrder == SortOrder.NAME_ASC) SortOrder.NAME_DESC else SortOrder.NAME_ASC
                onSortChange(next)
            }
        )
        // Opción: Tamaño
        SortMenuItem(
            label = "Tamaño",
            categoryIcon = Icons.Default.Storage,
            isSelected = currentOrder == SortOrder.SIZE_DESC || currentOrder == SortOrder.SIZE_ASC,
            isDesc = currentOrder == SortOrder.SIZE_DESC,
            onClick = {
                val next = if (currentOrder == SortOrder.SIZE_DESC) SortOrder.SIZE_ASC else SortOrder.SIZE_DESC
                onSortChange(next)
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
    // Definimos colores dinámicos si está seleccionado
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    DropdownMenuItem(
        modifier = Modifier.background(backgroundColor),
        text = {
            Text(text = label, color = contentColor)
        },
        leadingIcon = {
            Icon(imageVector = categoryIcon, contentDescription = null, tint = contentColor)
        },
        trailingIcon = {
            if (isSelected) {
                // Animación de rotación: 0f (abajo/descendente) o 180f (arriba/ascendente)
                val rotation by animateFloatAsState(targetValue = if (isDesc) 0f else 180f, label = "arrowRotation")
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
/**
 * Pantalla de galería implementada con Jetpack Compose.
 *
 * - Solicita permisos de lectura de imágenes.
 * - Observa el estado del ViewModel (GalleryUiState) y renderiza la UI.
 * - Permite cambiar el orden de las fotos y álbumes mediante un menú.
 * - Navega a fotos individuales o a detalles de álbum.
 *
 * Pertenece a la capa de presentación.
 */
