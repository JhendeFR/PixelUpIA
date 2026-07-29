package com.jhendefr.pixelupia.ui.gallery

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.Photo

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (Photo) -> Unit = {}
) {
    //Observación del estado
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    //Determinar el permiso segun la version de Android
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

    //Renderizado de la UI
    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Se requiere permiso para ver la galería")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { launcher.launch(permission) }) {
                    Text("Conceder Permiso")
                }
            }
        } else if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage ?: "Error desconocido",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            PhotoGrid(
                photos = uiState.photos,
                onPhotoClick = onPhotoClick
            )
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
/**
 * Pantalla de galería implementada con Jetpack Compose.
 *
 * - Solicita permisos de lectura de imágenes según la versión de Android.
 * - Observa el estado del ViewModel (GalleryUiState) y renderiza la UI:
 *   - Mensaje y botón si falta permiso.
 *   - Indicador de carga mientras se obtienen fotos.
 *   - Mensaje de error si ocurre un fallo.
 *   - Grid de fotos cuando los datos están disponibles.
 *
 * Incluye el componente PhotoGrid para mostrar las imágenes en una cuadrícula
 * adaptable y manejar clics sobre cada foto.
 *
 * Pertenece a la capa de presentación y conecta la lógica del ViewModel
 * con la interfaz gráfica.
 */
