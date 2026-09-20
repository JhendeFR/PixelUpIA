package com.jhendefr.pixelupia.ui.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.FaceCluster

@Composable
fun PersonPetAvatarRow(
    clusters: List<FaceCluster>,
    selectedClusterId: Long?,
    onSelectCluster: (FaceCluster?) -> Unit,
    onRenameCluster: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCluster by remember { mutableStateOf<FaceCluster?>(null) }
    var showAllClusters by remember { mutableStateOf(false) }

    val frequentClusters = remember(clusters) {
        clusters.filter { it.isFrequent }
    }

    val displayedClusters = if (showAllClusters || frequentClusters.isEmpty()) {
        clusters
    } else {
        frequentClusters
    }

    if (editingCluster != null) {
        RenameClusterDialog(
            cluster = editingCluster!!,
            onConfirm = { newName ->
                onRenameCluster(editingCluster!!.id, newName)
                editingCluster = null
            },
            onDismiss = { editingCluster = null }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Gente y Mascotas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (clusters.size > frequentClusters.size && frequentClusters.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showAllClusters) "Todos (${clusters.size})" else "Frecuentes (${frequentClusters.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { showAllClusters = !showAllClusters }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (selectedClusterId != null) {
                TextButton(onClick = { onSelectCluster(null) }) {
                    Text("Cerrar detalle")
                }
            } else if (clusters.size > frequentClusters.size && frequentClusters.isNotEmpty()) {
                TextButton(onClick = { showAllClusters = !showAllClusters }) {
                    Text(if (showAllClusters) "Solo frecuentes" else "Ver todos (${clusters.size})")
                }
            }
        }

        if (displayedClusters.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = "Aun no se han detectado personas ni mascotas con suficientes fotos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(displayedClusters, key = { it.id }) { cluster ->
                    val isSelected = cluster.id == selectedClusterId

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable {
                                if (isSelected) onSelectCluster(null) else onSelectCluster(cluster)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        ) {
                            AsyncImage(
                                model = cluster.coverUri,
                                contentDescription = cluster.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Insignia de Mascota o Persona
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (cluster.isPet) Icons.Default.Pets else Icons.Default.Face,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Nombre editable
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.clickable { editingCluster = cluster }
                        ) {
                            Text(
                                text = if (cluster.isUserNamed) cluster.name else "Agregar nombre",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (cluster.isUserNamed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre",
                                tint = if (cluster.isUserNamed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        Text(
                            text = "${cluster.photoCount} fotos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameClusterDialog(
    cluster: FaceCluster,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(if (cluster.isUserNamed) cluster.name else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (cluster.isPet) "Nombrar Mascota" else "Nombrar Persona")
        },
        text = {
            Column {
                Text(
                    text = "Asigna un nombre a esta agrupacion. Podras buscar las fotos de este grupo directamente por su nombre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) onConfirm(newName.trim())
                }
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
