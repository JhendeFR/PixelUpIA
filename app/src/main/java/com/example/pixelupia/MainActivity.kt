package com.example.pixelupia

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.pixelupia.ui.theme.PixelUpIATheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Enum para el modo de mejora
enum class EnhanceMode {
    FULL, // Mejora x4
    SIMPLE // Mejora de detalles (revisa documentacion para mas detalles)
}

class MainActivity : ComponentActivity() {

    private lateinit var processor: EsrganProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        processor = EsrganProcessor(this, useGPU = false)

        val requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                setContent {
                    PixelUpIATheme {
                        var selectedUri by remember { mutableStateOf<Uri?>(null) }
                        var isLoading by remember { mutableStateOf(false) }
                        var progress by remember { mutableStateOf(0f) }

                        val scope = rememberCoroutineScope()
                        val snackbarHostState = remember { SnackbarHostState() }

                        if (selectedUri == null) {
                            GaleriaScreen { uri -> selectedUri = uri }
                        } else {
                            VisorScreen(
                                uri = selectedUri!!,
                                isLoading = isLoading,
                                progress = progress,
                                snackbarHostState = snackbarHostState,
                                onEnhance = { bitmap, mode ->
                                    if (!isLoading) {
                                        scope.launch {
                                            isLoading = true
                                            progress = 0f

                                            val result = withContext(Dispatchers.Default) {
                                                when (mode) {
                                                    EnhanceMode.FULL -> enhanceFull(bitmap) { progress = it }
                                                    EnhanceMode.SIMPLE -> enhanceSimple(bitmap) { progress = it }
                                                }
                                            }
                                            val savedUri = withContext(Dispatchers.IO) {
                                                saveBitmapToGallery(this@MainActivity, result, "enhanced_pixelupia")
                                            }

                                            isLoading = false
                                            progress = 0f

                                            if (savedUri != null) {
                                                snackbarHostState.showSnackbar("Imagen guardada en la galería")
                                            } else {
                                                snackbarHostState.showSnackbar("Error al guardar la imagen")
                                            }
                                        }
                                    }
                                },
                                onBack = {
                                    selectedUri = null
                                    isLoading = false
                                    progress = 0f
                                }
                            )
                        }
                    }
                }
            } else {
                setContent {
                    PixelUpIATheme {
                        Text("Permiso denegado, no se pueden mostrar imágenes")
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            requestPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Lógica de Mejora x4
    private suspend fun enhanceFull(bitmap: Bitmap, onProgress: (Float) -> Unit): Bitmap {
        return processor.enhance(bitmap, onProgress)
    }

    // Lógica de Mejora Simple
    private suspend fun enhanceSimple(bitmap: Bitmap, onProgress: (Float) -> Unit): Bitmap {
        // 1. Reducir a 320x320
        val downscaledBitmap = withContext(Dispatchers.Default) {
            Bitmap.createScaledBitmap(bitmap, 320, 320, true)
        }
        onProgress(0.2f)

        // 2. Pasar a ESRGAN (saldrá en 1280x1280)
        val enhancedBitmap = processor.enhance(downscaledBitmap) {
            onProgress(0.2f + (it * 0.6f)) // Progreso de 0.2 a 0.8
        }

        // 3. Estirar a 1080p (cuadrado)
        val finalBitmap = withContext(Dispatchers.Default) {
            Bitmap.createScaledBitmap(enhancedBitmap, 1080, 1080, true)
        }
        onProgress(1.0f)
        return finalBitmap
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(collection, values)
            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    if (outputStream == null) {
                        throw Exception("No se pudo abrir OutputStream")
                    }
                    // Comprimir como JPEG con calidad 95
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            return uri
        } catch (e: Exception) {
            uri?.let { resolver.delete(it, null, null) }
            e.printStackTrace()
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processor.close()
    }
}