package com.example.pixelupia

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

// --- NUEVA Data Class para Álbumes ---
data class Album(
    val id: Long,
    val name: String,
    val thumbnailUri: Uri,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaleriaScreen(onImageClick: (Uri) -> Unit) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedAlbumId by remember { mutableStateOf<Long?>(null) }

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    // --- Cargar Álbumes ---
    LaunchedEffect(Unit) {
        val albumMap = mutableMapOf<Long, Album>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"

        context.contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "Desconocido"
                val uri = ContentUris.withAppendedId(collection, id)

                val album = albumMap[bucketId]
                if (album == null) {
                    albumMap[bucketId] = Album(bucketId, bucketName, uri, 1) // Usa la 1ra foto como thumbnail
                } else {
                    albumMap[bucketId] = album.copy(count = album.count + 1)
                }
            }
        }
        albums = albumMap.values.toList()
    }

    // --- Cargar Imágenes (cuando se selecciona un álbum) ---
    LaunchedEffect(selectedAlbumId) {
        if (selectedAlbumId == null) {
            images = emptyList() // Limpiar imágenes si volvemos a álbumes
            return@LaunchedEffect
        }

        val imageList = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(selectedAlbumId.toString())
        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                imageList.add(uri)
            }
        }
        images = imageList
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedAlbumId == null) "Galería de Álbumes" else "Fotos")
                },
                navigationIcon = {
                    if (selectedAlbumId != null) {
                        IconButton(onClick = { selectedAlbumId = null }) { // Botón para volver a álbumes
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedAlbumId == null) {
            // --- Mostrar Álbumes ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(albums) { album ->
                    AlbumItem(album = album, onClick = { selectedAlbumId = album.id })
                }
            }
        } else {
            // --- Mostrar Imágenes ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(images) { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize()
                            .aspectRatio(1f)
                            .clickable { onImageClick(uri) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

// --- Composable para el item del Álbum ---
@Composable
fun AlbumItem(album: Album, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Image(
                painter = rememberAsyncImagePainter(album.thumbnailUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "${album.count} fotos",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}