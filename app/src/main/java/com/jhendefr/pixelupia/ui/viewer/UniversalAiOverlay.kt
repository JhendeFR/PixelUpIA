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
    val type: AiOverlayType,
    val cornerPoints: List<android.graphics.Point> = emptyList()
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
        val offsetX = (containerWidth - (imgW * scale)) / 2f
        val offsetY = (containerHeight - (imgH * scale)) / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boxes, selectedId) {
                    detectTapGestures { tapOffset ->
                        var clicked: AiBoundingBoxItem? = null
                        for (item in boxes) {
                            val mappedLeft = (item.box.left * scale) + offsetX
                            val mappedTop = (item.box.top * scale) + offsetY
                            val mappedRight = (item.box.right * scale) + offsetX
                            val mappedBottom = (item.box.bottom * scale) + offsetY

                            if (tapOffset.x in mappedLeft..mappedRight && tapOffset.y in mappedTop..mappedBottom) {
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
                    AiOverlayType.ENTITY -> Color(0xFF7C4DFF)       // Purpura
                }

                if (item.cornerPoints.size >= 4) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        val p0 = item.cornerPoints[0]
                        moveTo((p0.x * scale) + offsetX, (p0.y * scale) + offsetY)
                        for (i in 1 until item.cornerPoints.size) {
                            val p = item.cornerPoints[i]
                            lineTo((p.x * scale) + offsetX, (p.y * scale) + offsetY)
                        }
                        close()
                    }

                    drawPath(
                        path = path,
                        color = if (isSelected) strokeColor.copy(alpha = 0.35f) else strokeColor.copy(alpha = 0.15f)
                    )
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = if (isSelected) 4f else 2.5f)
                    )
                } else {
                    val mappedLeft = (item.box.left * scale) + offsetX
                    val mappedTop = (item.box.top * scale) + offsetY
                    val mappedRight = (item.box.right * scale) + offsetX
                    val mappedBottom = (item.box.bottom * scale) + offsetY

                    val boxWidth = mappedRight - mappedLeft
                    val boxHeight = mappedBottom - mappedTop

                    // Fondo semi-transparente
                    drawRect(
                        color = if (isSelected) strokeColor.copy(alpha = 0.35f) else strokeColor.copy(alpha = 0.15f),
                        topLeft = Offset(mappedLeft, mappedTop),
                        size = Size(boxWidth, boxHeight)
                    )

                    // Borde del rectangulo
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(mappedLeft, mappedTop),
                        size = Size(boxWidth, boxHeight),
                        style = Stroke(width = if (isSelected) 4f else 2.5f)
                    )
                }
            }
        }

        // Elemento seleccionado: Floating chip sobre la caja o tarjeta de accion
        if (selectedItem != null) {
            val mappedLeft = (selectedItem.box.left * scale) + offsetX
            val mappedTop = (selectedItem.box.top * scale) + offsetY
            val mappedBottom = (selectedItem.box.bottom * scale) + offsetY

            val tagY = if (mappedTop > 80f) mappedTop - 45f else mappedBottom + 8f

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            mappedLeft.coerceIn(16f, containerWidth - 280f).toInt(),
                            tagY.coerceIn(60f, containerHeight - 160f).toInt()
                        )
                    }
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
