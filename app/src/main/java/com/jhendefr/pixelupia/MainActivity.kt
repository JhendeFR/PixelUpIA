package com.jhendefr.pixelupia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.data.repository.MediaRepositoryImpl
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import com.jhendefr.pixelupia.ui.gallery.GalleryScreen
import com.jhendefr.pixelupia.ui.gallery.GalleryViewModel
import com.jhendefr.pixelupia.ui.theme.PixelUpIATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Inyección de dependencias manual (Grafo de objetos)
        val dataSource = MediaStoreLocalDataSource(applicationContext)
        val repository = MediaRepositoryImpl(dataSource)
        val getPhotosUseCase = GetPhotosUseCase(repository)

        // 2. Factory para instanciar el ViewModel con sus dependencias
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GalleryViewModel(getPhotosUseCase) as T
            }
        }

        // 3. Obtención del ViewModel vinculado al ciclo de vida de la Activity
        val galleryViewModel = ViewModelProvider(this, viewModelFactory)[GalleryViewModel::class.java]

        setContent {
            PixelUpIATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GalleryScreen(
                        viewModel = galleryViewModel,
                        onPhotoClick = { selectedPhoto ->
                            // Se implementará con la navegación (Navigation 3 / Jetpack Navigation)
                        }
                    )
                }
            }
        }
    }
}
/**
 * Actividad principal de la aplicación PixelUpIA.
 *
 * - Configura la inyección manual de dependencias (DataSource, Repository, UseCase, ViewModel).
 * - Crea un ViewModelFactory para instanciar GalleryViewModel con sus dependencias.
 * - Renderiza la UI con Jetpack Compose aplicando el tema PixelUpIATheme.
 * - Muestra la pantalla de galería (GalleryScreen) y gestiona la interacción inicial.
 *
 * Pertenece a la capa de presentación y actúa como punto de entrada de la aplicación.
 */
