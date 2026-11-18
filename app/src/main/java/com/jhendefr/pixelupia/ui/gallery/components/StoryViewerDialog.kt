package com.jhendefr.pixelupia.ui.gallery.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity
import com.jhendefr.pixelupia.data.local.room.SmartPhotoEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StoryViewerDialog(
    story: MemoryStoryEntity,
    photos: List<SmartPhotoEntity>,
    onDismiss: () -> Unit,
    onEditName: (MemoryStoryEntity) -> Unit = {},
    onAddPhotos: (MemoryStoryEntity) -> Unit = {},
    onRemoveCurrentPhoto: (MemoryStoryEntity, Long) -> Unit = { _, _ -> },
    onTogglePin: (MemoryStoryEntity) -> Unit = {},
    onDeleteStory: (MemoryStoryEntity) -> Unit = {}
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val currentPhoto = photos.getOrNull(currentIndex) ?: photos.first()

    LaunchedEffect(currentIndex) {
        progress.snapTo(0f)
    }

    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused) {
            val remainingMs = ((1f - progress.value) * 4000L).toLong().coerceAtLeast(0L)
            if (remainingMs > 0) {
                val result = progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingMs.toInt(),
                        easing = LinearEasing
                    )
                )
                if (result.endReason == AnimationEndReason.Finished) {
                    if (currentIndex < photos.size - 1) {
                        currentIndex++
                    } else {
                        onDismiss()
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(photos.size, currentIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width / 3f) {
                                if (currentIndex > 0) {
                                    currentIndex--
                                } else {
                                    scope.launch { progress.snapTo(0f) }
                                }
                            } else {
                                if (currentIndex < photos.size - 1) {
                                    currentIndex++
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    )
                }
        ) {
            // Imagen de fondo con escala para ver completa
            AsyncImage(
                model = currentPhoto.uri,
                contentDescription = currentPhoto.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Gradiente superior para contraste de controles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Controles superiores: barras de progreso e informacion
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 12.dp, end = 12.dp)
            ) {
                // Barras de progreso segmentadas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    photos.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            val segmentProgress = when {
                                index < currentIndex -> 1f
                                index == currentIndex -> progress.value
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(segmentProgress)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Encabezado con datos de la historia y botones de accion
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = story.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val dateFormatted = remember(currentPhoto.dateAdded) {
                            SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                .format(Date(currentPhoto.dateAdded))
                        }
                        Text(
                            text = "$dateFormatted - ${currentIndex + 1} de ${photos.size}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // Boton de 3 puntos
                    Box {
                        IconButton(
                            onClick = {
                                isPaused = true
                                showOptionsMenu = true
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = {
                                showOptionsMenu = false
                                isPaused = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar nombre") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    isPaused = false
                                    onEditName(story)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Añadir fotos") },
                                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    isPaused = false
                                    onAddPhotos(story)
                                }
                            )
                            if (photos.size > 1) {
                                DropdownMenuItem(
                                    text = { Text("Quitar foto del recuerdo") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showOptionsMenu = false
                                        isPaused = false
                                        onRemoveCurrentPhoto(story, currentPhoto.photoId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (story.isPinned) "Desfijar recuerdo" else "Fijar recuerdo") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                onClick = {
                                    showOptionsMenu = false
                                    isPaused = false
                                    onTogglePin(story)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar recuerdo", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOptionsMenu = false
                                    isPaused = false
                                    onDismiss()
                                    onDeleteStory(story)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
