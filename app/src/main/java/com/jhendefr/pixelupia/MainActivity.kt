package com.jhendefr.pixelupia

import android.app.Activity
import android.net.Uri
import android.os.Build
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jhendefr.pixelupia.data.local.FavoritesLocalDataSource
import com.jhendefr.pixelupia.data.local.room.SnapVaultDatabase
import com.jhendefr.pixelupia.data.media.MediaStoreLocalDataSource
import com.jhendefr.pixelupia.data.ml.BarcodeDataSource
import com.jhendefr.pixelupia.data.ml.EntityExtractionDataSource
import com.jhendefr.pixelupia.data.ml.FaceDetectionDataSource
import com.jhendefr.pixelupia.data.ml.ImageLabelingDataSource
import com.jhendefr.pixelupia.data.ml.ObjectDetectionDataSource
import com.jhendefr.pixelupia.data.ocr.TextRecognitionDataSource
import com.jhendefr.pixelupia.data.repository.MediaRepositoryImpl
import com.jhendefr.pixelupia.data.repository.SmartPhotoRepositoryImpl
import com.jhendefr.pixelupia.data.worker.WorkManagerScheduler
import com.jhendefr.pixelupia.domain.usecase.*
import com.jhendefr.pixelupia.ui.gallery.AlbumDetailScreen
import com.jhendefr.pixelupia.ui.gallery.AlbumDetailViewModel
import com.jhendefr.pixelupia.ui.gallery.GalleryScreen
import com.jhendefr.pixelupia.ui.gallery.GalleryViewModel
import com.jhendefr.pixelupia.ui.theme.PixelUpIATheme
import com.jhendefr.pixelupia.ui.viewer.ViewerScreen
import com.jhendefr.pixelupia.ui.viewer.ViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var screenCaptureCallback: Activity.ScreenCaptureCallback? = null
    private lateinit var mediaStoreDataSource: MediaStoreLocalDataSource
    private lateinit var indexScreenshotUseCase: IndexScreenshotUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mediaStoreDataSource = MediaStoreLocalDataSource(applicationContext)
        val favoritesDataSource = FavoritesLocalDataSource(applicationContext)
        val repository = MediaRepositoryImpl(applicationContext, mediaStoreDataSource)

        val snapVaultDb = SnapVaultDatabase.getInstance(applicationContext)
        val smartPhotoRepository = SmartPhotoRepositoryImpl(snapVaultDb.smartGalleryDao())
        val textRecognitionDataSource = TextRecognitionDataSource(applicationContext)

        // ML Kit Multimodal Data Sources
        val barcodeDataSource = BarcodeDataSource(applicationContext)
        val faceDetectionDataSource = FaceDetectionDataSource(applicationContext)
        val imageLabelingDataSource = ImageLabelingDataSource(applicationContext)
        val objectDetectionDataSource = ObjectDetectionDataSource(applicationContext, imageLabelingDataSource, faceDetectionDataSource)
        val entityExtractionDataSource = EntityExtractionDataSource()

        val getPhotosUseCase = GetPhotosUseCase(repository)
        val getAlbumsUseCase = GetAlbumsUseCase(repository)
        val deletePhotosUseCase = DeletePhotosUseCase(repository)
        val movePhotosUseCase = MovePhotosUseCase(repository)
        val copyPhotosUseCase = CopyPhotosUseCase(repository)
        val getFavoritePhotoIdsUseCase = GetFavoritePhotoIdsUseCase(favoritesDataSource)
        val toggleFavoriteUseCase = ToggleFavoriteUseCase(favoritesDataSource)
        val searchSmartPhotosUseCase = SearchSmartPhotosUseCase(smartPhotoRepository)
        val getIndexedFoldersUseCase = GetIndexedFoldersUseCase(smartPhotoRepository)
        indexScreenshotUseCase = IndexScreenshotUseCase(textRecognitionDataSource, smartPhotoRepository)
        val processFolderOcrUseCase = ProcessFolderOcrUseCase(indexScreenshotUseCase, smartPhotoRepository)
        val runInteractiveOcrUseCase = RunInteractiveOcrUseCase(textRecognitionDataSource)

        val faceEmbeddingDataSource = com.jhendefr.pixelupia.data.ml.FaceEmbeddingDataSource(applicationContext)
        val petEmbeddingDataSource = com.jhendefr.pixelupia.data.ml.PetEmbeddingDataSource(applicationContext)
        val faceClusteringService = com.jhendefr.pixelupia.data.ml.FaceClusteringService(
            faceEmbeddingDataSource = faceEmbeddingDataSource,
            petEmbeddingDataSource = petEmbeddingDataSource,
            smartGalleryDao = snapVaultDb.smartGalleryDao()
        )
        val getFaceClustersUseCase = com.jhendefr.pixelupia.domain.usecase.GetFaceClustersUseCase(snapVaultDb.smartGalleryDao())
        val manageFaceClusterUseCase = com.jhendefr.pixelupia.domain.usecase.ManageFaceClusterUseCase(
            faceClusteringService = faceClusteringService,
            smartGalleryDao = snapVaultDb.smartGalleryDao()
        )
        val getPhotosForClusterUseCase = com.jhendefr.pixelupia.domain.usecase.GetPhotosForClusterUseCase(snapVaultDb.smartGalleryDao())
        val managePhotoTagsUseCase = com.jhendefr.pixelupia.domain.usecase.ManagePhotoTagsUseCase(snapVaultDb.smartGalleryDao())

        val enrichScreenshotUseCase = EnrichScreenshotUseCase(
            barcodeDataSource = barcodeDataSource,
            faceDetectionDataSource = faceDetectionDataSource,
            imageLabelingDataSource = imageLabelingDataSource,
            entityExtractionDataSource = entityExtractionDataSource,
            smartGalleryDao = snapVaultDb.smartGalleryDao(),
            textRecognitionDataSource = textRecognitionDataSource,
            context = applicationContext,
            faceClusteringService = faceClusteringService,
            objectDetectionDataSource = objectDetectionDataSource
        )
        val enrichFolderScreenshotsUseCase = EnrichFolderScreenshotsUseCase(
            enrichScreenshotUseCase = enrichScreenshotUseCase,
            smartGalleryDao = snapVaultDb.smartGalleryDao()
        )
        val getPhotoMultimodalMetadataUseCase = GetPhotoMultimodalMetadataUseCase(
            smartGalleryDao = snapVaultDb.smartGalleryDao()
        )
        val getSmartCollectionsUseCase = com.jhendefr.pixelupia.domain.usecase.GetSmartCollectionsUseCase(
            smartGalleryDao = snapVaultDb.smartGalleryDao()
        )
        val smartGalleryPreferencesDataSource = com.jhendefr.pixelupia.data.local.SmartGalleryPreferencesDataSource(applicationContext)
        val unindexPhotoUseCase = UnindexPhotoUseCase(smartPhotoRepository)

        // Programar enriquecimiento multimodal periodico con WorkManager
        WorkManagerScheduler.schedulePeriodicEnrichment(applicationContext)

        val galleryViewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GalleryViewModel(
                    getPhotosUseCase = getPhotosUseCase,
                    getAlbumsUseCase = getAlbumsUseCase,
                    deletePhotosUseCase = deletePhotosUseCase,
                    movePhotosUseCase = movePhotosUseCase,
                    copyPhotosUseCase = copyPhotosUseCase,
                    getFavoritePhotoIdsUseCase = getFavoritePhotoIdsUseCase,
                    toggleFavoriteUseCase = toggleFavoriteUseCase,
                    searchSmartPhotosUseCase = searchSmartPhotosUseCase,
                    getIndexedFoldersUseCase = getIndexedFoldersUseCase,
                    processFolderOcrUseCase = processFolderOcrUseCase,
                    enrichFolderScreenshotsUseCase = enrichFolderScreenshotsUseCase,
                    getSmartCollectionsUseCase = getSmartCollectionsUseCase,
                    preferencesDataSource = smartGalleryPreferencesDataSource,
                    unindexPhotoUseCase = unindexPhotoUseCase,
                    enrichScreenshotUseCase = enrichScreenshotUseCase,
                    getFaceClustersUseCase = getFaceClustersUseCase,
                    manageFaceClusterUseCase = manageFaceClusterUseCase,
                    getPhotosForClusterUseCase = getPhotosForClusterUseCase,
                    context = applicationContext
                ) as T
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
                        // 1. Galeria Principal
                        composable("gallery") {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                onPhotoClick = { selectedPhoto, fromAi, collectionId ->
                                    val route = buildString {
                                        append("viewer/${selectedPhoto.id}?fromAi=$fromAi")
                                        if (!collectionId.isNullOrEmpty()) {
                                            append("&collectionId=${Uri.encode(collectionId)}")
                                        }
                                    }
                                    navController.navigate(route)
                                },
                                onAlbumClick = { album ->
                                    navController.navigate("album_detail/${Uri.encode(album.name)}")
                                }
                            )
                        }

                        // 2. Detalle del Album
                        composable(
                            route = "album_detail/{albumName}",
                            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""

                            val detailViewModel: AlbumDetailViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return AlbumDetailViewModel(
                                            albumName = albumName,
                                            getPhotosUseCase = getPhotosUseCase,
                                            getAlbumsUseCase = getAlbumsUseCase,
                                            deletePhotosUseCase = deletePhotosUseCase,
                                            movePhotosUseCase = movePhotosUseCase,
                                            copyPhotosUseCase = copyPhotosUseCase
                                        ) as T
                                    }
                                }
                            )

                            AlbumDetailScreen(
                                viewModel = detailViewModel,
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { photo ->
                                    navController.navigate("viewer/${photo.id}?albumName=${Uri.encode(albumName)}&fromAi=false")
                                }
                            )
                        }

                        // 3. Visor de Fotos con Funciones IA
                        composable(
                            route = "viewer/{photoId}?albumName={albumName}&fromAi={fromAi}&collectionId={collectionId}",
                            arguments = listOf(
                                navArgument("photoId") { type = NavType.LongType },
                                navArgument("albumName") { type = NavType.StringType; nullable = true },
                                navArgument("fromAi") { type = NavType.BoolType; defaultValue = false },
                                navArgument("collectionId") { type = NavType.StringType; nullable = true }
                            )
                        ) { backStackEntry ->
                            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
                            val rawAlbumName = backStackEntry.arguments?.getString("albumName")
                            val albumName = if (rawAlbumName != null) Uri.decode(rawAlbumName) else null
                            val rawCollectionId = backStackEntry.arguments?.getString("collectionId")
                            val collectionId = if (rawCollectionId != null) Uri.decode(rawCollectionId) else null
                            val fromAi = backStackEntry.arguments?.getBoolean("fromAi") ?: false

                            val viewerViewModel: ViewerViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return ViewerViewModel(
                                            initialPhotoId = photoId,
                                            albumName = albumName,
                                            collectionId = collectionId,
                                            getPhotosUseCase = getPhotosUseCase,
                                            getAlbumsUseCase = getAlbumsUseCase,
                                            deletePhotosUseCase = deletePhotosUseCase,
                                            movePhotosUseCase = movePhotosUseCase,
                                            copyPhotosUseCase = copyPhotosUseCase,
                                            getFavoritePhotoIdsUseCase = getFavoritePhotoIdsUseCase,
                                            toggleFavoriteUseCase = toggleFavoriteUseCase,
                                            runInteractiveOcrUseCase = runInteractiveOcrUseCase,
                                            enrichScreenshotUseCase = enrichScreenshotUseCase,
                                            unindexPhotoUseCase = unindexPhotoUseCase,
                                            getPhotoMultimodalMetadataUseCase = getPhotoMultimodalMetadataUseCase,
                                            faceDetectionDataSource = faceDetectionDataSource,
                                            objectDetectionDataSource = objectDetectionDataSource,
                                            barcodeDataSource = barcodeDataSource,
                                            entityExtractionDataSource = entityExtractionDataSource,
                                            getSmartCollectionsUseCase = getSmartCollectionsUseCase,
                                            searchSmartPhotosUseCase = searchSmartPhotosUseCase,
                                            getPhotosForClusterUseCase = getPhotosForClusterUseCase,
                                            managePhotoTagsUseCase = managePhotoTagsUseCase
                                        ) as T
                                    }
                                }
                            )

                            ViewerScreen(
                                viewModel = viewerViewModel,
                                isFromAi = fromAi,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                screenCaptureCallback = Activity.ScreenCaptureCallback {
                    lifecycleScope.launch(Dispatchers.IO) {
                        delay(700)
                        val latestScreenshot = mediaStoreDataSource.fetchLatestScreenshot()
                        if (latestScreenshot != null) {
                            indexScreenshotUseCase(latestScreenshot)
                            WorkManagerScheduler.triggerOneTimeEnrichment(applicationContext)
                        }
                    }
                }
                registerScreenCaptureCallback(mainExecutor, screenCaptureCallback!!)
            } catch (e: SecurityException) {
                // Manejo de seguridad en caso de que el permiso no este disponible
                screenCaptureCallback = null
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                screenCaptureCallback?.let { callback ->
                    unregisterScreenCaptureCallback(callback)
                }
            } catch (e: Exception) {
                // Ignorar error al desregistrar
            } finally {
                screenCaptureCallback = null
            }
        }
    }
}
