package com.jhendefr.pixelupia.ui.viewer

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    photoUri: Uri,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Variables Animatable para una transición fluida en el Doble Tap
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // 1. Detección del Doble Toque
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            coroutineScope.launch {
                                if (scale.value > 1f) {
                                    // Restaurar a la normalidad fluidamente
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                } else {
                                    // Hacer zoom rápido (x3)
                                    launch { scale.animateTo(3f) }
                                }
                            }
                        }
                    )
                }
                // 2. Detección de Gestos manuales (Pellizcar y Arrastrar)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        coroutineScope.launch {
                            scale.snapTo((scale.value * zoom).coerceIn(1f, 5f))
                            if (scale.value > 1f) {
                                offsetX.snapTo(offsetX.value + pan.x)
                                offsetY.snapTo(offsetY.value + pan.y)
                            } else {
                                offsetX.snapTo(0f)
                                offsetY.snapTo(0f)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Foto ampliada",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
            )
        }
    }
}
/**
 * Pantalla de visor de fotos implementada con Jetpack Compose.
 *
 * - Muestra una imagen en pantalla completa con soporte de gestos.
 * - Permite zoom (pinch) y desplazamiento (drag) sobre la foto.
 * - Incluye una barra superior con botón de retroceso.
 *
 * Pertenece a la capa de presentación y ofrece la experiencia
 * de visualización detallada de una foto seleccionada.
 */
