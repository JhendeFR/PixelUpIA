package com.jhendefr.pixelupia.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jhendefr.pixelupia.data.ocr.DetailedOcrResult
import com.jhendefr.pixelupia.data.ocr.TextBlockItem
import com.jhendefr.pixelupia.ui.theme.PixelUpIAMotion

@Composable
fun OcrBoundingBoxOverlay(
    ocrResult: DetailedOcrResult,
    selectedBlockId: String?,
    onSelectBlock: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val imgWidth = if (ocrResult.imageWidth > 0) ocrResult.imageWidth.toFloat() else containerWidth
        val imgHeight = if (ocrResult.imageHeight > 0) ocrResult.imageHeight.toFloat() else containerHeight

        val scale = minOf(containerWidth / imgWidth, containerHeight / imgHeight)
        val renderedWidth = imgWidth * scale
        val renderedHeight = imgHeight * scale
        val offsetX = (containerWidth - renderedWidth) / 2f
        val offsetY = (containerHeight - renderedHeight) / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(ocrResult, selectedBlockId) {
                    detectTapGestures { tapOffset ->
                        var clickedBlock: TextBlockItem? = null
                        for (block in ocrResult.blocks) {
                            val left = offsetX + (block.box.left * scale)
                            val top = offsetY + (block.box.top * scale)
                            val right = offsetX + (block.box.right * scale)
                            val bottom = offsetY + (block.box.bottom * scale)

                            if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
                                clickedBlock = block
                                break
                            }
                        }
                        onSelectBlock(clickedBlock?.id)
                    }
                }
        ) {
            ocrResult.blocks.forEach { block ->
                val isSelected = block.id == selectedBlockId
                val left = offsetX + (block.box.left * scale)
                val top = offsetY + (block.box.top * scale)
                val right = offsetX + (block.box.right * scale)
                val bottom = offsetY + (block.box.bottom * scale)

                val boxWidth = right - left
                val boxHeight = bottom - top

                // Fondo semi-transparente del bloque
                drawRect(
                    color = if (isSelected) primaryColor.copy(alpha = 0.35f) else secondaryColor.copy(alpha = 0.18f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight)
                )

                // Borde del bloque
                drawRect(
                    color = if (isSelected) primaryColor else secondaryColor.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = if (isSelected) 3.dp.toPx() else 1.5.dp.toPx())
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrDetailsBottomSheet(
    ocrResult: DetailedOcrResult,
    selectedBlockId: String?,
    onSelectBlock: (String?) -> Unit,
    onDismiss: () -> Unit,
    onCloseOcr: () -> Unit
) {
    val context = LocalContext.current
    var copiedMessage by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, label: String = "Texto copiado") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR Text", text)
        clipboard.setPrimaryClip(clip)
        copiedMessage = label
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Texto Detectado (OCR)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ocrResult.blocks.size} bloques identificados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            copyToClipboard(ocrResult.fullText, "Todo el texto ha sido copiado")
                        },
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Todo", style = MaterialTheme.typography.labelMedium)
                    }

                    IconButton(
                        onClick = onCloseOcr,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar OCR")
                    }
                }
            }

            if (copiedMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = copiedMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Lista de Bloques Detectados
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(ocrResult.blocks, key = { it.id }) { block ->
                    val isSelected = block.id == selectedBlockId

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectBlock(if (isSelected) null else block.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    copyToClipboard(block.text, "Bloque copiado")
                                    onSelectBlock(block.id)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar bloque",
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
