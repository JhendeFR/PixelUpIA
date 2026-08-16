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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.data.repository.MediaRepositoryImpl
import com.jhendefr.pixelupia.domain.usecase.GetAlbumsUseCase
import com.jhendefr.pixelupia.domain.usecase.GetPhotosUseCase
import com.jhendefr.pixelupia.ui.gallery.AlbumDetailScreen
import com.jhendefr.pixelupia.ui.gallery.AlbumDetailViewModel
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
        val getAlbumsUseCase = GetAlbumsUseCase(repository)

        val galleryViewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GalleryViewModel(getPhotosUseCase, getAlbumsUseCase) as T
            }
        }

        val galleryViewModel = ViewModelProvider(this, galleryViewModelFactory)[GalleryViewModel::class.java]

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
                                },
                                onAlbumClick = { album ->
                                    navController.navigate("album_detail/${Uri.encode(album.name)}")
                                }
                            )
                        }

                        // Pantalla 2: Detalle de Álbum
                        composable(
                            route = "album_detail/{albumName}",
                            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                            
                            val detailViewModel: AlbumDetailViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return AlbumDetailViewModel(albumName, getPhotosUseCase) as T
                                    }
                                }
                            )

                            AlbumDetailScreen(
                                viewModel = detailViewModel,
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { photo ->
                                    val encodedUri = Uri.encode(photo.uri.toString())
                                    navController.navigate("viewer/$encodedUri")
                                }
                            )
                        }

                        // Pantalla 3: Visor de Fotos con Zoom
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
