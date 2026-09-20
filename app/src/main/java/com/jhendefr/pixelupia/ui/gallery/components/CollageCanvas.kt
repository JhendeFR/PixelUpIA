package com.jhendefr.pixelupia.ui.gallery.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.CollageTemplate
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SmartCollageItem

@Composable
fun CollageCanvasCard(
    collage: SmartCollageItem,
    onPhotoClick: (Photo) -> Unit,
    onRemovePhoto: (SmartCollageItem, Photo) -> Unit,
    onAddPhotoClick: (SmartCollageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Cabecera del Collage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collage.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = collage.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = "Editar collage",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas de Plantilla Adaptativa
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                when (collage.template) {
                    CollageTemplate.GRID_2X2 -> {
                        Grid2x2Layout(
                            photos = collage.photos,
                            isEditing = isEditing,
                            onPhotoClick = onPhotoClick,
                            onRemove = { onRemovePhoto(collage, it) }
                        )
                    }
                    CollageTemplate.TRIPTYCH -> {
                        TriptychLayout(
                            photos = collage.photos,
                            isEditing = isEditing,
                            onPhotoClick = onPhotoClick,
                            onRemove = { onRemovePhoto(collage, it) }
                        )
                    }
                    CollageTemplate.DOMINANT_WITH_THUMBS -> {
                        DominantWithThumbsLayout(
                            photos = collage.photos,
                            isEditing = isEditing,
                            onPhotoClick = onPhotoClick,
                            onRemove = { onRemovePhoto(collage, it) }
                        )
                    }
                }
            }

            // Opcion de anadir mas fotos si esta en modo edicion
            AnimatedVisibility(visible = isEditing) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onAddPhotoClick(collage) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Anadir imagen a este recuerdo")
                    }
                }
            }
        }
    }
}

@Composable
private fun Grid2x2Layout(
    photos: List<Photo>,
    isEditing: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onRemove: (Photo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val topRow = photos.take(2)
        val bottomRow = photos.drop(2).take(2)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            topRow.forEach { photo ->
                CollageImageTile(
                    photo = photo,
                    isEditing = isEditing,
                    onPhotoClick = onPhotoClick,
                    onRemove = onRemove,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        if (bottomRow.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                bottomRow.forEach { photo ->
                    CollageImageTile(
                        photo = photo,
                        isEditing = isEditing,
                        onPhotoClick = onPhotoClick,
                        onRemove = onRemove,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun TriptychLayout(
    photos: List<Photo>,
    isEditing: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onRemove: (Photo) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        photos.take(3).forEach { photo ->
            CollageImageTile(
                photo = photo,
                isEditing = isEditing,
                onPhotoClick = onPhotoClick,
                onRemove = onRemove,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun DominantWithThumbsLayout(
    photos: List<Photo>,
    isEditing: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onRemove: (Photo) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val dominantPhoto = photos.firstOrNull()
        val thumbPhotos = photos.drop(1).take(2)

        if (dominantPhoto != null) {
            CollageImageTile(
                photo = dominantPhoto,
                isEditing = isEditing,
                onPhotoClick = onPhotoClick,
                onRemove = onRemove,
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight()
            )
        }

        if (thumbPhotos.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                thumbPhotos.forEach { photo ->
                    CollageImageTile(
                        photo = photo,
                        isEditing = isEditing,
                        onPhotoClick = onPhotoClick,
                        onRemove = onRemove,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CollageImageTile(
    photo: Photo,
    isEditing: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onRemove: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onPhotoClick(photo) }
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isEditing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clickable { onRemove(photo) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quitar foto",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
    }
}
