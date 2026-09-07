package com.jhendefr.pixelupia.ui.gallery

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jhendefr.pixelupia.domain.model.IndexedFolderInfo
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.usecase.SmartCollectionItem
import com.jhendefr.pixelupia.domain.usecase.SmartCollectionType

@Composable
fun SmartGalleryView(
    searchQuery: String,
    smartPhotos: List<Photo>,
    allPhotos: List<Photo>,
    indexedFolders: List<IndexedFolderInfo>,
    selectedIndexedFolder: String?,
    appCollections: List<SmartCollectionItem> = emptyList(),
    visualCollections: List<SmartCollectionItem> = emptyList(),
    selectedSmartCollection: SmartCollectionItem? = null,
    hiddenCollections: List<SmartCollectionItem> = emptyList(),
    showHiddenCollectionsDialog: Boolean = false,
    showFolderManagerDialog: Boolean = false,
    showIndexedCount: Boolean = true,
    allDeviceFolders: List<String> = emptyList(),
    chosenIndexFolders: Set<String> = setOf("Screenshots"),
    isBatchIndexing: Boolean,
    batchIndexingProgress: Pair<Int, Int>?,
    activeIndexingFolder: String? = null,
    isFolderEnriching: Boolean = false,
    folderEnrichingProgress: Pair<Int, Int>? = null,
    activeEnrichingFolder: String? = null,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onSelectSmartCollection: (SmartCollectionItem?) -> Unit = {},
    onHideCollection: (String) -> Unit = {},
    onUnhideCollection: (String) -> Unit = {},
    onToggleHiddenCollectionsDialog: (Boolean) -> Unit = {},
    onToggleFolderManagerDialog: (Boolean) -> Unit = {},
    onToggleFolderIndexing: (String) -> Unit = {},
    onToggleShowIndexedCount: () -> Unit = {},
    onTriggerBatchIndex: (String) -> Unit,
    onTriggerFolderEnrichment: (String) -> Unit = {},
    onPhotoClick: (Photo) -> Unit,
    onToggleSelection: (Long) -> Unit
) {
    var showSmartOptionsMenu by remember { mutableStateOf(false) }

    // Dialogo de Colecciones Ocultas
    if (showHiddenCollectionsDialog) {
        HiddenCollectionsDialog(
            hiddenCollections = hiddenCollections,
            onUnhide = onUnhideCollection,
            onDismiss = { onToggleHiddenCollectionsDialog(false) }
        )
    }

    // Dialogo de Gestion de Carpetas a Indexar
    if (showFolderManagerDialog) {
        FolderManagerDialog(
            allFolders = allDeviceFolders,
            chosenFolders = chosenIndexFolders,
            onToggleFolder = onToggleFolderIndexing,
            onDismiss = { onToggleFolderManagerDialog(false) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        if (selectedSmartCollection != null) {
            // 1. Vista de Detalle de Coleccion Inteligente (App o Categoria Visual)
            val collectionPhotos = if (searchQuery.isBlank()) {
                selectedSmartCollection.photos
            } else {
                selectedSmartCollection.photos.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.sourceApp?.contains(searchQuery, ignoreCase = true) == true)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelectSmartCollection(null) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedSmartCollection.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedSmartCollection.type == SmartCollectionType.SOURCE_APP)
                                "Coleccion de Aplicacion - ${collectionPhotos.size} fotos"
                            else
                                "Categoria Visual IA - ${collectionPhotos.size} fotos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Buscar en ${selectedSmartCollection.title}...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )

                PhotoGrid(
                    photos = collectionPhotos,
                    selectedIds = selectedIds,
                    isSelectionMode = isSelectionMode,
                    onPhotoClick = onPhotoClick,
                    onToggleSelection = onToggleSelection
                )
            }
        } else if (selectedIndexedFolder != null) {
            // 2. Vista de Detalle de Carpeta Indexada
            val folderPhotos = allPhotos.filter {
                it.folderName.equals(selectedIndexedFolder, ignoreCase = true) ||
                        it.folderName.contains(selectedIndexedFolder, ignoreCase = true)
            }

            val displayedPhotos = if (searchQuery.isEmpty()) {
                folderPhotos
            } else {
                smartPhotos.filter {
                    it.folderName.equals(selectedIndexedFolder, ignoreCase = true) ||
                            it.folderName.contains(selectedIndexedFolder, ignoreCase = true)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelectFolder(null) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                    Text(
                        text = "$selectedIndexedFolder (${displayedPhotos.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Buscar en $selectedIndexedFolder...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )

                PhotoGrid(
                    photos = displayedPhotos,
                    selectedIds = selectedIds,
                    isSelectionMode = isSelectionMode,
                    onPhotoClick = onPhotoClick,
                    onToggleSelection = onToggleSelection
                )
            }
        } else {
            // 3. Vista General: Buscador Inteligente + Carruseles de Colecciones + Carpetas
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Buscador General Inteligente con Menu de 3 Puntos (Mas Opciones)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Buscar en capturas...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Limpiar"
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Motor IA",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )

                        // Boton de 3 Puntos (Mas Opciones)
                        Box {
                            IconButton(
                                onClick = { showSmartOptionsMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Mas opciones",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = showSmartOptionsMenu,
                                onDismissRequest = { showSmartOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text("Colecciones")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Collections, contentDescription = null)
                                    },
                                    onClick = {
                                        showSmartOptionsMenu = false
                                        onToggleHiddenCollectionsDialog(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Gestionar carpetas a indexar") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                    },
                                    onClick = {
                                        showSmartOptionsMenu = false
                                        onToggleFolderManagerDialog(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text("Detalles")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (showIndexedCount) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSmartOptionsMenu = false
                                        onToggleShowIndexedCount()
                                    }
                                )
                            }
                        }
                    }
                }

                if (searchQuery.isNotEmpty()) {
                    // Resultados en tiempo real
                    item {
                        Text(
                            text = "Resultados para '$searchQuery' (${smartPhotos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    if (smartPhotos.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No se encontraron coincidencias para '$searchQuery'.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        // Renderizar las fotos encontradas en cuadrícula
                        smartPhotos.chunked(3).forEach { rowPhotos ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (photo in rowPhotos) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onPhotoClick(photo) }
                                        ) {
                                            AsyncImage(
                                                model = photo.uri,
                                                contentDescription = photo.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    repeat(3 - rowPhotos.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // SECCION 1: Cabecera General de Colecciones Inteligentes
                    if (appCollections.isNotEmpty() || visualCollections.isNotEmpty() || hiddenCollections.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "Colecciones Inteligentes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Apps de origen y categorias visuales",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // CARRUSEL 1: Apps de Origen
                    if (appCollections.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Apps de Origen",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(appCollections, key = { it.id }) { collection ->
                                        SmartCollectionCarouselCard(
                                            collection = collection,
                                            onClick = { onSelectSmartCollection(collection) },
                                            onHide = { onHideCollection(collection.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CARRUSEL 2: Categorias Visuales
                    if (visualCollections.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Categorias Visuales",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(visualCollections, key = { it.id }) { collection ->
                                        SmartCollectionCarouselCard(
                                            collection = collection,
                                            onClick = { onSelectSmartCollection(collection) },
                                            onHide = { onHideCollection(collection.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECCION 2: Carpetas Indexadas con IA
                    item {
                        Column {
                            Text(
                                text = "Carpetas Indexadas con IA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Carpetas seleccionadas para procesar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(indexedFolders, key = { it.name }) { folder ->
                        val folderIsBatchIndexing = isBatchIndexing && (activeIndexingFolder == folder.name)
                        val folderIsEnriching = isFolderEnriching && (activeEnrichingFolder == folder.name)

                        IndexedFolderCard(
                            folder = folder,
                            showIndexedCount = showIndexedCount,
                            isBatchIndexing = folderIsBatchIndexing,
                            batchIndexingProgress = if (folderIsBatchIndexing) batchIndexingProgress else null,
                            isFolderEnriching = folderIsEnriching,
                            folderEnrichingProgress = if (folderIsEnriching) folderEnrichingProgress else null,
                            onClick = { onSelectFolder(folder.name) },
                            onTriggerBatchIndex = { onTriggerBatchIndex(folder.name) },
                            onTriggerFolderEnrichment = { onTriggerFolderEnrichment(folder.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartCollectionCarouselCard(
    collection: SmartCollectionItem,
    onClick: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .width(135.dp)
            .height(180.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                if (collection.coverUri != null) {
                    AsyncImage(
                        model = collection.coverUri,
                        contentDescription = collection.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (collection.type == SmartCollectionType.SOURCE_APP) Icons.Default.Apps else Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Insignia de conteo de fotos
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "${collection.count}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Boton de Opciones / Ocultar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ocultar coleccion") },
                            leadingIcon = {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onHide()
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (collection.type == SmartCollectionType.SOURCE_APP) "App" else "Visual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HiddenCollectionsDialog(
    hiddenCollections: List<SmartCollectionItem>,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Colecciones Ocultas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (hiddenCollections.isEmpty()) {
                Text(
                    text = "No tienes colecciones ocultas en este momento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(hiddenCollections, key = { it.id }) { collection ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = collection.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${if (collection.type == SmartCollectionType.SOURCE_APP) "App" else "Visual"} - ${collection.count} fotos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onUnhide(collection.id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mostrar", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun FolderManagerDialog(
    allFolders: List<String>,
    chosenFolders: Set<String>,
    onToggleFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Carpetas a Indexar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Elige las carpetas del dispositivo que deseas analizar e indexar con IA:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (allFolders.isEmpty()) {
                    Text(
                        text = "No se encontraron carpetas.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allFolders, key = { it }) { folderName ->
                            val isSelected = chosenFolders.contains(folderName)
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFolder(folderName) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleFolder(folderName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo")
            }
        }
    )
}

@Composable
fun IndexedFolderCard(
    folder: IndexedFolderInfo,
    showIndexedCount: Boolean = true,
    isBatchIndexing: Boolean,
    batchIndexingProgress: Pair<Int, Int>?,
    isFolderEnriching: Boolean = false,
    folderEnrichingProgress: Pair<Int, Int>? = null,
    onClick: () -> Unit,
    onTriggerBatchIndex: () -> Unit,
    onTriggerFolderEnrichment: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miniatura o Icono de Carpeta
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (folder.coverUri != null) {
                        AsyncImage(
                            model = folder.coverUri,
                            contentDescription = folder.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Informacion y Estado de Indexacion
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    // Mostrar u ocultar el conteo de imagenes segun preferencia
                    val countText = if (showIndexedCount) {
                        "${folder.indexedPhotos} de ${folder.totalPhotos} fotos indexadas"
                    } else {
                        if (folder.isCompleted) "Indexacion completa" else "Indexacion disponible"
                    }

                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isBatchIndexing && batchIndexingProgress != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (batchIndexingProgress.second > 0)
                                    batchIndexingProgress.first.toFloat() / batchIndexingProgress.second.toFloat()
                                else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    } else if (isFolderEnriching && folderEnrichingProgress != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (folderEnrichingProgress.second > 0)
                                    folderEnrichingProgress.first.toFloat() / folderEnrichingProgress.second.toFloat()
                                else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Circulo de Progreso o Boton de Accion
                Box(contentAlignment = Alignment.Center) {
                    if (isBatchIndexing || isFolderEnriching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (!folder.isCompleted && folder.totalPhotos > 0) {
                        IconButton(
                            onClick = onTriggerBatchIndex,
                            modifier = Modifier.size(40.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { folder.progress },
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Indexar todo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Indexado",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Barra inferior de acciones IA de la carpeta
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onTriggerFolderEnrichment,
                    enabled = !isFolderEnriching,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFolderEnriching) "Analizando..." else "Analisis Inteligente Completo",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
