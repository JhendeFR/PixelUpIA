package com.jhendefr.pixelupia.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AiOverlayType {
    OCR_TEXT,
    FACE,
    OBJECT,
    BARCODE,
    ENTITY
}

data class AiBoundingBoxItem(
    val id: String,
    val label: String,
    val subLabel: String? = null,
    val box: Rect,
    val type: AiOverlayType
)

@Composable
fun UniversalAiOverlay(
    boxes: List<AiBoundingBoxItem>,
    imageWidth: Int,
    imageHeight: Int,
    selectedId: String?,
    onSelectItem: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    fun copyText(text: String, notice: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Detection", text)
        clipboard.setPrimaryClip(clip)
        copiedNotice = notice
    }

    LaunchedEffect(copiedNotice) {
        if (copiedNotice != null) {
            kotlinx.coroutines.delay(2000)
            copiedNotice = null
        }
    }

    val selectedItem = boxes.find { it.id == selectedId }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val imgW = if (imageWidth > 0) imageWidth.toFloat() else containerWidth
        val imgH = if (imageHeight > 0) imageHeight.toFloat() else containerHeight

        val scale = minOf(containerWidth / imgW, containerHeight / imgH)
        val renderedWidth = imgW * scale
        val renderedHeight = imgH * scale
        val offsetX = (containerWidth - renderedWidth) / 2f
        val offsetY = (containerHeight - renderedHeight) / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boxes, selectedId) {
                    detectTapGestures { tapOffset ->
                        var clicked: AiBoundingBoxItem? = null
                        for (item in boxes) {
                            val left = offsetX + (item.box.left * scale)
                            val top = offsetY + (item.box.top * scale)
                            val right = offsetX + (item.box.right * scale)
                            val bottom = offsetY + (item.box.bottom * scale)

                            if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
                                clicked = item
                                break
                            }
                        }
                        onSelectItem(clicked?.id)
                    }
                }
        ) {
            boxes.forEach { item ->
                val isSelected = item.id == selectedId

                val strokeColor = when (item.type) {
                    AiOverlayType.OCR_TEXT -> Color(0xFF388E3C)     // Verde
                    AiOverlayType.FACE -> Color(0xFFE91E63)         // Rosa / Magenta
                    AiOverlayType.OBJECT -> Color(0xFF00B0FF)       // Azul celeste
                    AiOverlayType.BARCODE -> Color(0xFFFF9100)      // Naranja
                    AiOverlayType.ENTITY -> Color(0xFF7C4DFF)       // Púrpura
                }

                val left = offsetX + (item.box.left * scale)
                val top = offsetY + (item.box.top * scale)
                val right = offsetX + (item.box.right * scale)
                val bottom = offsetY + (item.box.bottom * scale)

                val boxWidth = right - left
                val boxHeight = bottom - top

                // Fondo semi-transparente
                drawRect(
                    color = if (isSelected) strokeColor.copy(alpha = 0.35f) else strokeColor.copy(alpha = 0.15f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight)
                )

                // Borde del rectángulo
                drawRect(
                    color = strokeColor,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = if (isSelected) 4f else 2.5f)
                )
            }
        }

        // Elemento seleccionado: Floating chip sobre la caja o tarjeta de acción
        if (selectedItem != null) {
            val left = offsetX + (selectedItem.box.left * scale)
            val top = offsetY + (selectedItem.box.top * scale)
            val bottom = offsetY + (selectedItem.box.bottom * scale)

            val tagY = if (top > 80f) top - 45f else bottom + 8f

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .offset(
                        x = (left.coerceIn(16f, containerWidth - 260f)).dp / LocalContext.current.resources.displayMetrics.density,
                        y = (tagY.coerceIn(60f, containerHeight - 160f)).dp / LocalContext.current.resources.displayMetrics.density
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = selectedItem.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!selectedItem.subLabel.isNullOrBlank()) {
                            Text(
                                text = selectedItem.subLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val toCopy = selectedItem.subLabel ?: selectedItem.label
                            copyText(toCopy, "Copiado al portapapeles")
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Aviso de copia flotante
        if (copiedNotice != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = copiedNotice ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
