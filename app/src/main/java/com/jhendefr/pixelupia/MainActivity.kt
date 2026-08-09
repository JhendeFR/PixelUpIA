package com.jhendefr.pixelupia

import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.data.repository.MediaRepositoryImpl
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import com.jhendefr.pixelupia.ui.gallery.GalleryScreen
import com.jhendefr.pixelupia.ui.gallery.GalleryViewModel
import com.jhendefr.pixelupia.ui.theme.PixelUpIATheme
import com.jhendefr.pixelupia.ui.viewer.ViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dataSource = MediaStoreLocalDataSource(applicationContext)
        val repository = MediaRepositoryImpl(dataSource)
        val getPhotosUseCase = GetPhotosUseCase(repository)

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GalleryViewModel(getPhotosUseCase) as T
            }
        }

        val galleryViewModel = ViewModelProvider(this, viewModelFactory)[GalleryViewModel::class.java]

        setContent {
            PixelUpIATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "gallery"
                    ) {
                        // Pantalla 1: Cuadrícula de la Galería
                        composable("gallery") {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                onPhotoClick = { selectedPhoto ->
                                    val encodedUri = Uri.encode(selectedPhoto.uri.toString())
                                    navController.navigate("viewer/$encodedUri")
                                }
                            )
                        }

                        // Pantalla 2: Visor de Fotos con Zoom
                        composable(
                            route = "viewer/{photoUri}",
                            arguments = listOf(navArgument("photoUri") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val uriString = backStackEntry.arguments?.getString("photoUri") ?: ""
                            val photoUri = Uri.parse(Uri.decode(uriString))

                            ViewerScreen(
                                photoUri = photoUri,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
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
