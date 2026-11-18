package com.jhendefr.pixelupia.ui.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity
import com.jhendefr.pixelupia.domain.model.Photo

@Composable
fun EditStoryNameDialog(
    story: MemoryStoryEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleText by remember(story) { mutableStateOf(story.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Editar nombre del recuerdo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Asigna un nuevo titulo para este recuerdo o evento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Titulo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleText.isNotBlank()) {
                        onConfirm(titleText.trim())
                    }
                },
                enabled = titleText.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddPhotosToStoryDialog(
    story: MemoryStoryEntity,
    allPhotos: List<Photo>,
    onAddPhotos: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val existingIds = remember(story.photoIds) { story.photoIds.toSet() }
    val candidatePhotos = remember(allPhotos, existingIds) {
        allPhotos.filter { !existingIds.contains(it.id) }
    }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Añadir fotos a '${story.title}'",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (candidatePhotos.isEmpty()) {
                Text(
                    text = "No hay mas fotos disponibles en el dispositivo para anadir a este recuerdo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    Text(
                        text = "Selecciona una o mas fotos para incluirlas en este recuerdo (${selectedIds.size} seleccionadas).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(candidatePhotos, key = { it.id }) { photo ->
                            val isSelected = selectedIds.contains(photo.id)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedIds = if (isSelected) {
                                            selectedIds - photo.id
                                        } else {
                                            selectedIds + photo.id
                                        }
                                    }
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
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onAddPhotos(selectedIds.toList())
                    }
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Añadir (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ManageStoryPhotosDialog(
    story: MemoryStoryEntity,
    allPhotos: List<Photo>,
    onRemovePhoto: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val photoMap = remember(allPhotos) { allPhotos.associateBy { it.id } }
    val currentPhotos = remember(story.photoIds, photoMap) {
        story.photoIds.mapNotNull { photoMap[it] }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Fotos en '${story.title}'",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (currentPhotos.isEmpty()) {
                Text(
                    text = "Este recuerdo no contiene fotos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    Text(
                        text = "Pulsa sobre la 'X' en cualquier foto para quitarla de este recuerdo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 85.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentPhotos, key = { it.id }) { photo ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { onRemovePhoto(photo.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Quitar foto",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo")
            }
        }
    )
}

@Composable
fun CreateManualStoryDialog(
    allPhotos: List<Photo>,
    suggestedMemories: List<MemoryStoryEntity> = emptyList(),
    onCreateSuggestion: (MemoryStoryEntity) -> Unit = {},
    onCreate: (title: String, photoIds: List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (suggestedMemories.isNotEmpty()) 0 else 1) }
    var titleText by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Crear nuevo recuerdo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = if (suggestedMemories.isNotEmpty()) "Sugeridos IA (${suggestedMemories.size})" else "Sugeridos IA",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Personalizado",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                if (selectedTab == 0) {
                    // Vista de Recuerdos Sugeridos por IA
                    if (suggestedMemories.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay recuerdos adicionales sugeridos en este momento. Puedes crear uno propio en la pestana Personalizado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(suggestedMemories, key = { it.title }) { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = suggestion.coverUri,
                                            contentDescription = suggestion.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )

                                        Spacer(modifier = Modifier.size(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggestion.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = suggestion.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.size(8.dp))

                                        Button(
                                            onClick = { onCreateSuggestion(suggestion) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Crear",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Vista Manual de creacion
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            label = { Text("Nombre del recuerdo") },
                            placeholder = { Text("Ej. Viaje familiar, Cumpleaños...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Selecciona las fotos para este recuerdo (${selectedIds.size} seleccionadas):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 75.dp),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(allPhotos, key = { it.id }) { photo ->
                                val isSelected = selectedIds.contains(photo.id)
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedIds = if (isSelected) {
                                                selectedIds - photo.id
                                            } else {
                                                selectedIds + photo.id
                                            }
                                        }
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
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                Button(
                    onClick = {
                        if (titleText.isNotBlank() && selectedIds.isNotEmpty()) {
                            onCreate(titleText.trim(), selectedIds.toList())
                        }
                    },
                    enabled = titleText.isNotBlank() && selectedIds.isNotEmpty()
                ) {
                    Text("Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
