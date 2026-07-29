package com.jhendefr.pixelupia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun PixelUpIATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
/**
 * Tema de la aplicación PixelUpIA basado en Material3.
 *
 * - Define esquemas de color para modo claro y oscuro.
 * - Selecciona automáticamente el esquema según la configuración del sistema.
 * - Aplica el tema a todo el contenido de la app mediante MaterialTheme.
 *
 * Pertenece a la capa de presentación y centraliza la configuración visual
 * de la interfaz de usuario.
 */
