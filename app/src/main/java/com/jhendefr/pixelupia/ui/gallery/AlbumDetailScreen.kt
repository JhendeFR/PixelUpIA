package com.jhendefr.pixelupia.ui.gallery

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jhendefr.pixelupia.domain.model.Photo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: AlbumDetailViewModel,
    onBackClick: () -> Unit,
    onPhotoClick: (Photo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedPhotoIds.size} seleccionadas") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(AlbumDetailEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar selección")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.albumName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "Error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Reutilizamos el PhotoGrid que está en GalleryScreen.kt
                PhotoGrid(
                    photos = uiState.photos,
                    selectedIds = uiState.selectedPhotoIds,
                    isSelectionMode = uiState.isSelectionMode,
                    onPhotoClick = onPhotoClick,
                    onToggleSelection = { viewModel.onEvent(AlbumDetailEvent.TogglePhotoSelection(it)) }
                )
            }

            // BARRA INFERIOR TIPO PÍLDORA (MATERIAL EXPRESSIVE)
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Mover */ }) {
                        Icon(Icons.Default.DriveFileMove, "Mover", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { /* Copiar */ }) {
                        Icon(Icons.Default.FileCopy, "Copiar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { /* Compartir */ }) {
                        Icon(Icons.Default.Share, "Compartir", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { /* Eliminar */ }) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
