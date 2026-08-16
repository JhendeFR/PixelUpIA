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
import com.jhendefr.pixelupia.ui.viewer.ViewerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dataSource = MediaStoreLocalDataSource(applicationContext)
        val repository = MediaRepositoryImpl(applicationContext, dataSource)
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
                        // 1. Galería
                        composable("gallery") {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                onPhotoClick = { selectedPhoto ->
                                    navController.navigate("viewer/${selectedPhoto.id}")
                                },
                                onAlbumClick = { album ->
                                    navController.navigate("album_detail/${Uri.encode(album.name)}")
                                }
                            )
                        }

                        // 2. Detalle del Álbum
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
                                    // Pasamos el ID y el nombre del álbum
                                    navController.navigate("viewer/${photo.id}?albumName=${Uri.encode(albumName)}")
                                }
                            )
                        }

                        // 3. VISOR ACTUALIZADO
                        composable(
                            route = "viewer/{photoId}?albumName={albumName}",
                            arguments = listOf(
                                navArgument("photoId") { type = NavType.LongType },
                                navArgument("albumName") { type = NavType.StringType; nullable = true }
                            )
                        ) { backStackEntry ->
                            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
                            val rawAlbumName = backStackEntry.arguments?.getString("albumName")
                            val albumName = if (rawAlbumName != null) Uri.decode(rawAlbumName) else null

                            val viewerViewModel: ViewerViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return ViewerViewModel(photoId, albumName, getPhotosUseCase) as T
                                    }
                                }
                            )

                            ViewerScreen(
                                viewModel = viewerViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
