package com.jhendefr.pixelupia.ui.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity

@Composable
fun SmartMomentsRow(
    stories: List<MemoryStoryEntity>,
    onStoryClick: (MemoryStoryEntity) -> Unit,
    onEditName: (MemoryStoryEntity) -> Unit = {},
    onAddPhotos: (MemoryStoryEntity) -> Unit = {},
    onManagePhotos: (MemoryStoryEntity) -> Unit = {},
    onTogglePin: (MemoryStoryEntity) -> Unit = {},
    onDeleteStory: (MemoryStoryEntity) -> Unit = {},
    onCreateManualStory: () -> Unit = {},
    onOpenCollageView: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Recuerdos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Boton para ver momentos en collage
            if (stories.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onOpenCollageView,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewQuilt,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ver en collage",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(stories, key = { it.storyId }) { story ->
                StoryCard(
                    story = story,
                    onClick = { onStoryClick(story) },
                    onEditName = { onEditName(story) },
                    onAddPhotos = { onAddPhotos(story) },
                    onManagePhotos = { onManagePhotos(story) },
                    onTogglePin = { onTogglePin(story) },
                    onDeleteStory = { onDeleteStory(story) }
                )
            }

            item {
                CreateStoryCard(onClick = onCreateManualStory)
            }
        }
    }
}

@Composable
private fun StoryCard(
    story: MemoryStoryEntity,
    onClick: () -> Unit,
    onEditName: () -> Unit,
    onAddPhotos: () -> Unit,
    onManagePhotos: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteStory: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val badgeText = when (story.storyType) {
        "PERSON" -> "Persona"
        "PET" -> "Mascota"
        "EVENT" -> "Evento"
        "FLASHBACK" -> "Recuerdo"
        else -> "Especial"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 3.dp,
        modifier = Modifier
            .width(115.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = story.coverUri,
                contentDescription = story.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradiente oscuro inferior y superior tenue
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Badge tematico e icono de pin en la esquina superior izquierda
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (story.isPinned) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Fijado",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Boton de 3 puntos en la esquina superior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones del recuerdo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar nombre") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEditName()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir fotos") },
                        leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onAddPhotos()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gestionar fotos") },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onManagePhotos()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (story.isPinned) "Desfijar recuerdo" else "Fijar recuerdo") },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar recuerdo", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteStory()
                        }
                    )
                }
            }

            // Titulo y Subtitulo en la parte inferior
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = story.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                Text(
                    text = story.subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CreateStoryCard(
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .width(115.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Crear recuerdo",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Crear recuerdo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
