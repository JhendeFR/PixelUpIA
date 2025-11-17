package com.example.pixelupia

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.CharacterIterator
import java.text.StringCharacterIterator

data class ImageDetails(
    val name: String?,
    val size: Long?,
    val resolution: String,
    val path: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorScreen(
    uri: Uri,
    isLoading: Boolean,
    progress: Float,
    snackbarHostState: SnackbarHostState,
    onEnhance: (Bitmap, EnhanceMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageDetails by remember { mutableStateOf<ImageDetails?>(null) }
    var showEnhanceDialog by remember { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState()
    var showInfoSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // Para el bottom sheet

    // --- NUEVOS ESTADOS PARA EL ZOOM Y PANEO ---
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val bitmap = try {
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
            }?.copy(Bitmap.Config.ARGB_8888, true)

            originalBitmap = bitmap

            // Cargar detalles
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATA
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                    val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    imageDetails = ImageDetails(name, size, "${width}x$height", path)
                }
            }
        }
    }

    if (showEnhanceDialog) {
        EnhanceOptionsDialog(
            resolution = imageDetails?.resolution ?: "N/A",
            onDismiss = { showEnhanceDialog = false },
            onConfirm = { mode ->
                showEnhanceDialog = false
                originalBitmap?.let { onEnhance(it, mode) }
            }
        )
    }
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = bottomSheetState
        ) {
            ImageDetailsSheet(details = imageDetails)
        }
    }

    // --- MANEJO DE GESTOS "ATRÁS" MODIFICADO ---
    val isZoomedOrPanned = scale != 1f || offset != Offset.Zero

    // 1. Prioridad: Ocultar el Bottom Sheet
    BackHandler(enabled = bottomSheetState.isVisible) {
        scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
            if (!bottomSheetState.isVisible) {
                showInfoSheet = false
            }
        }
    }

    // 2. Prioridad: Resetear el zoom si la imagen está zoomeada o paneada
    BackHandler(enabled = isZoomedOrPanned && !bottomSheetState.isVisible && !isLoading) {
        // Reseteamos el estado
        scale = 1f
        offset = Offset.Zero
    }

    // 3. Prioridad: Salir de la pantalla (solo si no hay nada abierto o zoomeado)
    BackHandler(enabled = !isZoomedOrPanned && !bottomSheetState.isVisible && !isLoading) {
        onBack()
    }
    // --- FIN DE LA MODIFICACIÓN DE GESTOS "ATRÁS" ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mejorar Imagen") },
                navigationIcon = {
                    IconButton(onClick = {
                        // --- MODIFICADO ---
                        // Si está zoomeado, el botón de atrás resetea el zoom
                        if (isZoomedOrPanned) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        //Posicionamiento de botones (sin cambios)
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = { showInfoSheet = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(" i ", fontWeight = FontWeight.Bold)
                }

                FloatingActionButton(
                    onClick = {
                        originalBitmap?.let {
                            if (it.width > 720 || it.height > 720) {
                                showEnhanceDialog = true
                            } else {
                                onEnhance(it, EnhanceMode.FULL)
                            }
                        }
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Text("Mejorar")
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(bottom = 100.dp)
                    // --- NUEVO: AÑADIMOS EL MODIFICADOR PARA DETECTAR GESTOS ---
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            scale *= zoom
                            // Limitamos la escala para que no sea muy pequeña o muy grande
                            scale = scale.coerceIn(0.5f, 5f)

                            if (scale > 1f) {
                                // Si hay zoom, permitimos el paneo (arrastrar)
                                offset += pan
                            } else {
                                // Si la escala vuelve a ser 1f o menos, reseteamos todo
                                scale = 1f
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                originalBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Imagen original",
                        // --- MODIFICADO: AÑADIMOS graphicsLayer PARA APLICAR EL ZOOM/PANEO ---
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit // El ContentScale se mantiene
                    )
                }
            }
        }
    }
}

@Composable
fun EnhanceOptionsDialog(
    resolution: String,
    onDismiss: () -> Unit,
    onConfirm: (EnhanceMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opciones de Mejora") },
        text = { Text("Tu imagen tiene una resolución de $resolution. ¿Cómo deseas mejorarla?") },
        confirmButton = {
            Button(onClick = { onConfirm(EnhanceMode.FULL) }) {
                Text("Mejorar x4")
            }
        },
        dismissButton = {
            Button(onClick = { onConfirm(EnhanceMode.SIMPLE) }) {
                Text("Mejora Simple")
            }
        }
    )
}

@Composable
fun ImageDetailsSheet(details: ImageDetails?) {
    if (details == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 48.dp)
    ) {
        Text("Detalles de la Imagen", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        DetailRow("Nombre:", details.name ?: "N/A")
        DetailRow("Tamaño:", details.size?.let { humanReadableByteCount(it) } ?: "N/A")
        DetailRow("Resolución:", details.resolution)
        DetailRow("Ruta:", details.path ?: "N/A")
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}

fun humanReadableByteCount(bytes: Long): String {
    val unit = 1024
    if (bytes < unit) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}