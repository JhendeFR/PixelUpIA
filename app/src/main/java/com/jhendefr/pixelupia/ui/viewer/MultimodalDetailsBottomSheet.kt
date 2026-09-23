package com.jhendefr.pixelupia.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jhendefr.pixelupia.domain.model.MultimodalMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultimodalDetailsBottomSheet(
    metadata: MultimodalMetadata?,
    isEnriching: Boolean,
    onTriggerEnrichment: () -> Unit,
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onViewFaceBoundingBox: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    var newTagInput by remember { mutableStateOf("") }
    var isAddingTag by remember { mutableStateOf(false) }

    fun copyText(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Data", text)
        clipboard.setPrimaryClip(clip)
        copiedNotice = label
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
                        text = "Análisis Multimodal IA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (metadata?.isEnriched == true) "Metadatos generados con ML Kit" else "Pendiente de análisis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onTriggerEnrichment,
                    enabled = !isEnriching,
                    shape = CircleShape
                ) {
                    if (isEnriching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizando...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (metadata?.isEnriched == true) "Reanalizar" else "Analizar Ahora")
                    }
                }
            }

            if (copiedNotice != null) {
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
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = copiedNotice ?: "",
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Detección de Rostros
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Presencia de Rostros",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (metadata?.hasFaces == true) Icons.Default.Face else Icons.Default.FaceRetouchingOff,
                                        contentDescription = null,
                                        tint = if (metadata?.hasFaces == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (metadata?.hasFaces == true) "Rostro detectado en la imagen" else "No se detectaron rostros",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (metadata?.hasFaces == true && onViewFaceBoundingBox != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onViewFaceBoundingBox()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CropFree,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Ver encuadre del rostro analizado")
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Comprensión Semántica MobileCLIP-S0 (Dual-Encoder Visión y Texto)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Comprensión Semántica MobileCLIP-S0",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (metadata?.hasSemanticEmbedding == true) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "512D Activo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Espacio latente continuo para búsqueda libre de lenguaje natural",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (metadata?.hasSemanticEmbedding == true) Icons.Default.Hub else Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = if (metadata?.hasSemanticEmbedding == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (metadata?.hasSemanticEmbedding == true) {
                                            "Vector latente de 512 floats generado y normalizado L2"
                                        } else {
                                            "Sin vector semántico calculado"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (!metadata?.clipTopPredictions.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Afinidad Semántica Zero-Shot detectada:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        metadata?.clipTopPredictions?.forEach { (concept, score) ->
                                            val pct = (score * 100).toInt()
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = "$concept $pct%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Escenas y Etiquetas Visuales con Porcentaje de Precisión (Image Labeler)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Escenas y Etiquetas Visuales",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Clasificación de contexto, ambiente y rasgos visuales",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (metadata?.labels.isNullOrEmpty()) {
                            Text(
                                text = "Sin etiquetas visuales detectadas (>70% de confianza)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            metadata?.labels?.forEach { label ->
                                val displayText = if (label.contains(" - ")) {
                                    val term = label.substringBeforeLast(" - ")
                                    val conf = label.substringAfterLast(" - ")
                                    "${com.jhendefr.pixelupia.data.ml.LabelTranslator.translate(term)} - $conf"
                                } else {
                                    com.jhendefr.pixelupia.data.ml.LabelTranslator.translate(label)
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Category,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onRemoveTag(label) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Eliminar etiqueta",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isAddingTag) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newTagInput,
                                        onValueChange = { newTagInput = it },
                                        placeholder = { Text("Nueva etiqueta (ej. Perro, Ropa)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (newTagInput.isNotBlank()) {
                                                onAddTag(newTagInput.trim())
                                                newTagInput = ""
                                                isAddingTag = false
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Guardar etiqueta",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isAddingTag = false
                                            newTagInput = ""
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancelar"
                                        )
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { isAddingTag = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Agregar etiqueta / categoria manual")
                                }
                            }
                        }
                    }
                }
            }

                // 3. Códigos QR y de Barras
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Codigos QR y de Barras",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (metadata?.barcodes.isNullOrEmpty()) {
                            Text(
                                text = "No se detectaron codigos de barras ni QR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                metadata?.barcodes?.forEach { barcode ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCode,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = barcode,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            IconButton(
                                                onClick = { copyText(barcode, "Codigo copiado") },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copiar codigo",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Entidades Lingüísticas (URLs, Fechas, Teléfonos)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Entidades Estructuradas",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (metadata?.entities.isNullOrEmpty()) {
                            Text(
                                text = "No se detectaron entidades estructuradas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                metadata?.entities?.forEach { entity ->
                                    val entityText = if (entity.contains(" (")) entity.substringBeforeLast(" (") else entity
                                    val entityType = if (entity.contains(" (")) entity.substringAfterLast(" (").removeSuffix(")") else ""

                                    val isUrl = entityType.equals("URL", ignoreCase = true) ||
                                            entityText.startsWith("http://", ignoreCase = true) ||
                                            entityText.startsWith("https://", ignoreCase = true) ||
                                            entityText.startsWith("www.", ignoreCase = true)
                                    val isPhone = entityType.equals("Telefono", ignoreCase = true)
                                    val isEmail = entityType.equals("Email", ignoreCase = true)

                                    val entityIcon = when {
                                        isUrl -> Icons.Default.Language
                                        isPhone -> Icons.Default.Phone
                                        isEmail -> Icons.Default.Email
                                        entityType.equals("Tarjeta", ignoreCase = true) -> Icons.Default.CreditCard
                                        else -> Icons.Default.Link
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = entityIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = entity,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Boton de App Intent Contextual
                                                if (isUrl) {
                                                    IconButton(
                                                        onClick = {
                                                            val targetUrl = if (!entityText.startsWith("http://") && !entityText.startsWith("https://")) {
                                                                "https://$entityText"
                                                            } else {
                                                                entityText
                                                            }
                                                            runCatching {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl))
                                                                context.startActivity(intent)
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                            contentDescription = "Abrir enlace",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else if (isPhone) {
                                                    IconButton(
                                                        onClick = {
                                                            runCatching {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$entityText"))
                                                                context.startActivity(intent)
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Phone,
                                                            contentDescription = "Llamar",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else if (isEmail) {
                                                    IconButton(
                                                        onClick = {
                                                            runCatching {
                                                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$entityText"))
                                                                context.startActivity(intent)
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Email,
                                                            contentDescription = "Enviar correo",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { copyText(entityText, "Copiado al portapapeles") },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copiar entidad",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
