package com.jhendefr.pixelupia.ui.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity
import com.jhendefr.pixelupia.domain.model.Photo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsCollageScreen(
    stories: List<MemoryStoryEntity>,
    allPhotos: List<Photo>,
    onBack: () -> Unit,
    onStoryClick: (MemoryStoryEntity) -> Unit,
    onPhotoClick: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoMap = remember(allPhotos) { allPhotos.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Momentos y Recuerdos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
    ) { paddingValues ->
        if (stories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay recuerdos disponibles en este momento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 80.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(stories, key = { it.storyId }) { story ->
                    val storyPhotos = remember(story.photoIds, photoMap) {
                        story.photoIds.mapNotNull { photoMap[it] }
                    }

                    MomentCollageCard(
                        story = story,
                        photos = storyPhotos,
                        onPlayStory = { onStoryClick(story) },
                        onPhotoClick = onPhotoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentCollageCard(
    story: MemoryStoryEntity,
    photos: List<Photo>,
    onPlayStory: () -> Unit,
    onPhotoClick: (Photo) -> Unit
) {
    val badgeText = when (story.storyType) {
        "PERSON" -> "Persona"
        "PET" -> "Mascota"
        "EVENT" -> "Evento"
        "FLASHBACK" -> "Recuerdo"
        else -> "Especial"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Cabecera del Momento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (story.isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Fijado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = story.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onPlayStory,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Ver", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas de Fotografias en formato Collage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                if (photos.isEmpty()) {
                    AsyncImage(
                        model = story.coverUri,
                        contentDescription = story.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPlayStory() }
                    )
                } else {
                    when {
                        photos.size == 1 -> {
                            AsyncImage(
                                model = photos.first().uri,
                                contentDescription = photos.first().name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onPhotoClick(photos.first()) }
                            )
                        }
                        photos.size == 2 -> {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                photos.take(2).forEach { photo ->
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = photo.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { onPhotoClick(photo) }
                                    )
                                }
                            }
                        }
                        photos.size == 3 -> {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AsyncImage(
                                    model = photos[0].uri,
                                    contentDescription = photos[0].name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .fillMaxHeight()
                                        .clickable { onPhotoClick(photos[0]) }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    photos.drop(1).take(2).forEach { photo ->
                                        AsyncImage(
                                            model = photo.uri,
                                            contentDescription = photo.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .clickable { onPhotoClick(photo) }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // 4 o mas fotos: Cuadricula 2x2
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    photos.take(2).forEach { photo ->
                                        AsyncImage(
                                            model = photo.uri,
                                            contentDescription = photo.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable { onPhotoClick(photo) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    photos.drop(2).take(2).forEach { photo ->
                                        AsyncImage(
                                            model = photo.uri,
                                            contentDescription = photo.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable { onPhotoClick(photo) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
