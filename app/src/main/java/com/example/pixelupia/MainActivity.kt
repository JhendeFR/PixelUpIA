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

                        // --- Para el mensaje de "Guardado" ---
                        val scope = rememberCoroutineScope()
                        val snackbarHostState = remember { SnackbarHostState() }

                        if (selectedUri == null) {
                            GaleriaScreen { uri -> selectedUri = uri }
                        } else {
                            // --- Lógica de UI actualizada ---
                            VisorScreen(
                                uri = selectedUri!!,
                                isLoading = isLoading,
                                snackbarHostState = snackbarHostState, // 👈 Pasar el state
                                onEnhance = { bitmap ->
                                    if (!isLoading) {
                                        scope.launch { // 👈 Usar el scope de compose
                                            isLoading = true

                                            // 1. Procesar en hilo Default
                                            val result = withContext(Dispatchers.Default) {
                                                processor.enhance(bitmap) // Tiling code
                                            }

                                            // 2. Guardar en hilo IO
                                            val savedUri = withContext(Dispatchers.IO) {
                                                saveBitmapToGallery(this@MainActivity, result, "enhanced_pixelupia")
                                            }

                                            isLoading = false

                                            // 3. Mostrar resultado en hilo Main
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

    // --- NUEVA FUNCIÓN PARA GUARDAR EN GALERÍA ---
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
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            return uri
        } catch (e: Exception) {
            // Si algo falla (ej. uri es null), eliminar la entrada
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