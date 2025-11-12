package com.example.pixelupia

import android.graphics.Bitmap
import android.graphics.ImageDecoder // 👈 NUEVO IMPORT
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.ArrowBack // 👈 DEPRECADO
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 👈 CORREGIDO
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorScreen(
    uri: Uri,
    isLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    onEnhance: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Cargar la imagen seleccionada (CORREGIDO para deprecación)
    LaunchedEffect(uri) {
        originalBitmap = try {
            if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }?.copy(Bitmap.Config.ARGB_8888, true) // Asegurarse que sea mutable y ARGB
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mejorar Imagen") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, // 👈 CORREGIDO
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    originalBitmap?.let { original ->
                        onEnhance(original)
                    }
                },
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Text("Mejorar")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            originalBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Imagen original",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}