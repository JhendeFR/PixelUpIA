package com.jhendefr.pixelupia.ui.gallery

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.IndexedFolderInfo
import com.jhendefr.pixelupia.domain.model.Photo

@Composable
fun SmartGalleryView(
    searchQuery: String,
    smartPhotos: List<Photo>,
    allPhotos: List<Photo>,
    indexedFolders: List<IndexedFolderInfo>,
    selectedIndexedFolder: String?,
    isBatchIndexing: Boolean,
    batchIndexingProgress: Pair<Int, Int>?,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onTriggerBatchIndex: (String) -> Unit,
    onPhotoClick: (Photo) -> Unit,
    onToggleSelection: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        if (selectedIndexedFolder == null) {
            // Vista General de IA: Carpetas Indexadas + Buscador Global
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Barra de Búsqueda Inteligente FTS5
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar texto en capturas (FTS5)...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar"
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Motor IA",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }

                // Sección: Carpetas Indexadas por IA
                item {
                    Text(
                        text = "Carpetas Indexadas con IA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }

                items(indexedFolders, key = { it.name }) { folder ->
                    IndexedFolderCard(
                        folder = folder,
                        isBatchIndexing = isBatchIndexing,
                        batchIndexingProgress = batchIndexingProgress,
                        onClick = { onSelectFolder(folder.name) },
                        onTriggerBatchIndex = { onTriggerBatchIndex(folder.name) }
                    )
                }

                // Sección: Resultados de Búsqueda o Fotos Indexadas
                if (searchQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "Resultados para '$searchQuery' (${smartPhotos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    if (smartPhotos.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No se encontraron coincidencias para '$searchQuery'.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Vista de Detalle de Carpeta Indexada
            val folderPhotos = allPhotos.filter {
                it.folderName.equals(selectedIndexedFolder, ignoreCase = true) ||
                        it.folderName.contains(selectedIndexedFolder, ignoreCase = true)
            }

            val displayedPhotos = if (searchQuery.isEmpty()) {
                folderPhotos
            } else {
                smartPhotos.filter {
                    it.folderName.equals(selectedIndexedFolder, ignoreCase = true) ||
                            it.folderName.contains(selectedIndexedFolder, ignoreCase = true)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelectFolder(null) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                    Text(
                        text = "$selectedIndexedFolder (${displayedPhotos.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Buscar en $selectedIndexedFolder...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )

                PhotoGrid(
                    photos = displayedPhotos,
                    selectedIds = selectedIds,
                    isSelectionMode = isSelectionMode,
                    onPhotoClick = onPhotoClick,
                    onToggleSelection = onToggleSelection
                )
            }
        }
    }
}

@Composable
fun IndexedFolderCard(
    folder: IndexedFolderInfo,
    isBatchIndexing: Boolean,
    batchIndexingProgress: Pair<Int, Int>?,
    onClick: () -> Unit,
    onTriggerBatchIndex: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura o Icono de Carpeta
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (folder.coverUri != null) {
                    AsyncImage(
                        model = folder.coverUri,
                        contentDescription = folder.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Información y Estado de Indexación
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.indexedPhotos} de ${folder.totalPhotos} imágenes indexadas (OCR)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isBatchIndexing && batchIndexingProgress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (batchIndexingProgress.second > 0)
                                batchIndexingProgress.first.toFloat() / batchIndexingProgress.second.toFloat()
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Círculo de Progreso o Botón de Acción
            Box(contentAlignment = Alignment.Center) {
                if (isBatchIndexing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!folder.isCompleted && folder.totalPhotos > 0) {
                    IconButton(
                        onClick = onTriggerBatchIndex,
                        modifier = Modifier.size(40.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { folder.progress },
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Indexar todo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Indexado",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
