package com.jhendefr.pixelupia.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import androidx.compose.ui.graphics.Brush
import com.jhendefr.pixelupia.domain.model.FaceCluster
import com.jhendefr.pixelupia.domain.model.IndexedFolderInfo
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.domain.model.SmartCollageItem
import com.jhendefr.pixelupia.domain.usecase.SmartCollectionItem
import com.jhendefr.pixelupia.domain.usecase.SmartCollectionType
import com.jhendefr.pixelupia.ui.gallery.components.CollageCanvasCard
import com.jhendefr.pixelupia.ui.gallery.components.PersonPetAvatarRow
import com.jhendefr.pixelupia.ui.gallery.components.SmartMomentsRow

@OptIn(ExperimentalFoundationApi::class)
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
    faceClusters: List<FaceCluster> = emptyList(),
    memoryStories: List<com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity> = emptyList(),
    onStoryClick: (com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) -> Unit = {},
    onEditStoryName: (com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) -> Unit = {},
    onAddPhotosToStory: (com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) -> Unit = {},
    onManageStoryPhotos: (com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) -> Unit = {},
    onToggleStoryPin: (com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) -> Unit = {},
    onDeleteStory: (com.jhendefr.pixelupia.data.local.room.MemoryStoryEntity) -> Unit = {},
    onCreateManualStory: () -> Unit = {},
    onOpenCollageView: () -> Unit = {},
    showMomentsCollageView: Boolean = false,
    onCloseCollageView: () -> Unit = {},
    selectedFaceCluster: FaceCluster? = null,
    selectedFaceClusterPhotos: List<Photo> = emptyList(),
    showAddPhotosToClusterDialog: Boolean = false,
    showMergeClusterDialog: Boolean = false,
    showChooseCoverDialog: Boolean = false,
    onSelectFaceCluster: (FaceCluster?) -> Unit = {},
    onRenameFaceCluster: (Long, String) -> Unit = { _, _ -> },
    onToggleAddPhotosToClusterDialog: (Boolean) -> Unit = {},
    onToggleMergeClusterDialog: (Boolean) -> Unit = {},
    onToggleChooseCoverDialog: (Boolean) -> Unit = {},
    onSetClusterCover: (Long, Photo) -> Unit = { _, _ -> },
    onMergeFaceClusters: (Long, Long) -> Unit = { _, _ -> },
    onHideFaceCluster: (Long) -> Unit = {},
    onAddPhotoToFaceCluster: (Long, Photo) -> Unit = { _, _ -> },
    onRemovePhotoFromFaceCluster: (Long, Long) -> Unit = { _, _ -> },
    smartCollages: List<SmartCollageItem> = emptyList(),
    selectedFilterType: String = "Todos",
    activeFilterChips: List<String> = emptyList(),
    onSelectFilterType: (String) -> Unit = {},
    onToggleCustomizeFilterChips: (Boolean) -> Unit = {},
    onRemoveCollagePhoto: (SmartCollageItem, Photo) -> Unit = { _, _ -> },
    onAddCollagePhoto: (SmartCollageItem) -> Unit = {},
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
    onPhotoClick: (Photo, String?) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleSemanticPhotosDialog: (Boolean) -> Unit = {}
) {
    if (showMomentsCollageView) {
        com.jhendefr.pixelupia.ui.gallery.components.MomentsCollageScreen(
            stories = memoryStories,
            allPhotos = allPhotos,
            onBack = onCloseCollageView,
            onStoryClick = onStoryClick,
            onPhotoClick = { photo -> onPhotoClick(photo, null) }
        )
        return
    }

    var showSmartOptionsMenu by remember { mutableStateOf(false) }
    var showClusterMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

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

    // Dialogo para anadir fotos al cluster de persona / mascota
    if (showAddPhotosToClusterDialog && selectedFaceCluster != null) {
        AddPhotosToClusterDialog(
            clusterName = selectedFaceCluster.name,
            allPhotos = allPhotos,
            existingPhotoIds = selectedFaceClusterPhotos.map { it.id }.toSet(),
            chosenIndexFolders = chosenIndexFolders,
            onAddPhoto = { photo -> onAddPhotoToFaceCluster(selectedFaceCluster.id, photo) },
            onDismiss = { onToggleAddPhotosToClusterDialog(false) }
        )
    }

    // Dialogo para combinar clusters de personas / mascotas
    if (showMergeClusterDialog && selectedFaceCluster != null) {
        MergeClusterDialog(
            sourceCluster = selectedFaceCluster,
            availableClusters = faceClusters,
            onMerge = { targetClusterId -> onMergeFaceClusters(targetClusterId, selectedFaceCluster.id) },
            onDismiss = { onToggleMergeClusterDialog(false) }
        )
    }

    // Dialogo para elegir foto de portada del grupo
    if (showChooseCoverDialog && selectedFaceCluster != null) {
        ChooseCoverDialog(
            clusterName = selectedFaceCluster.name,
            photos = selectedFaceClusterPhotos,
            onSelectCover = { photo -> onSetClusterCover(selectedFaceCluster.id, photo) },
            onDismiss = { onToggleChooseCoverDialog(false) }
        )
    }

    // Dialogo para renombrar / agregar nombre al grupo
    if (showRenameDialog && selectedFaceCluster != null) {
        var newNameText by remember(selectedFaceCluster) {
            mutableStateOf(if (selectedFaceCluster.isUserNamed) selectedFaceCluster.name else "")
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(if (selectedFaceCluster.isUserNamed) "Cambiar nombre" else "Agregar nombre")
            },
            text = {
                Column {
                    Text(
                        text = "Asigna un nombre a este grupo de " + (if (selectedFaceCluster.isPet) "mascotas" else "personas") + " para identificarlo facilmente y buscarlo por su nombre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newNameText,
                        onValueChange = { newNameText = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameText.isNotBlank()) {
                            onRenameFaceCluster(selectedFaceCluster.id, newNameText.trim())
                        }
                        showRenameDialog = false
                    },
                    enabled = newNameText.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        if (selectedFaceCluster != null) {
            BackHandler(enabled = !isSelectionMode) {
                onSelectFaceCluster(null)
            }
            val clusterPhotos = selectedFaceClusterPhotos
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelectFaceCluster(null) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showRenameDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (selectedFaceCluster.isUserNamed) selectedFaceCluster.name else "Agregar nombre",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedFaceCluster.isUserNamed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre",
                                tint = if (selectedFaceCluster.isUserNamed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (selectedFaceCluster.isPet) "Mascota - ${clusterPhotos.size} fotos" else "Persona - ${clusterPhotos.size} fotos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { showClusterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones del grupo"
                            )
                        }
                        DropdownMenu(
                            expanded = showClusterMenu,
                            onDismissRequest = { showClusterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Añadir fotos") },
                                leadingIcon = {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                },
                                onClick = {
                                    showClusterMenu = false
                                    onToggleAddPhotosToClusterDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Combinar con otro grupo") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                                },
                                onClick = {
                                    showClusterMenu = false
                                    onToggleMergeClusterDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Elegir portada") },
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                },
                                onClick = {
                                    showClusterMenu = false
                                    onToggleChooseCoverDialog(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cambiar nombre") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showClusterMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ocultar grupo", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showClusterMenu = false
                                    onHideFaceCluster(selectedFaceCluster.id)
                                }
                            )
                        }
                    }
                }

                if (clusterPhotos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No hay fotos en esta agrupacion.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { onToggleAddPhotosToClusterDialog(true) }) {
                                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Añadir fotos")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(clusterPhotos, key = { it.id }) { photo ->
                            val isSelected = selectedIds.contains(photo.id)
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) onToggleSelection(photo.id)
                                            else onPhotoClick(photo, "cluster_${selectedFaceCluster.id}")
                                        },
                                        onLongClick = {
                                            onToggleSelection(photo.id)
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Seleccionada",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedSmartCollection != null) {
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
                    onPhotoClick = { photo -> onPhotoClick(photo, selectedSmartCollection.id) },
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
                    onPhotoClick = { photo -> onPhotoClick(photo, "folder_$selectedIndexedFolder") },
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
                                DropdownMenuItem(
                                    text = {
                                        Text("Vectores Semanticos (MobileCLIP)")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSmartOptionsMenu = false
                                        onToggleSemanticPhotosDialog(true)
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
                                                .clickable { onPhotoClick(photo, "search_$searchQuery") }
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
                    // SECCION 0: Recuerdos Inteligentes (Smart Moments)
                    item {
                        SmartMomentsRow(
                            stories = memoryStories,
                            onStoryClick = onStoryClick,
                            onEditName = onEditStoryName,
                            onAddPhotos = onAddPhotosToStory,
                            onManagePhotos = onManageStoryPhotos,
                            onTogglePin = onToggleStoryPin,
                            onDeleteStory = onDeleteStory,
                            onCreateManualStory = onCreateManualStory,
                            onOpenCollageView = onOpenCollageView
                        )
                    }

                    // SECCION 1: Acceso Rapido a Gente y Mascotas
                    item {
                        PersonPetAvatarRow(
                            clusters = faceClusters,
                            selectedClusterId = selectedFaceCluster?.id,
                            onSelectCluster = onSelectFaceCluster,
                            onRenameCluster = onRenameFaceCluster
                        )
                    }

                    // SECCION 2: Filtro Rapido por Tipo (Chips Dinamicos y Personalizables)
                    item {
                        val chipsToShow = listOf("Todos") + (if (activeFilterChips.isNotEmpty()) activeFilterChips else listOf("Lugares", "Documentos", "Ropa", "Eventos", "Pagos", "Mascotas", "Codigos QR"))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(chipsToShow) { filter ->
                                val isSelected = filter.equals(selectedFilterType, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectFilterType(filter) },
                                    label = { Text(filter) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }

                            item {
                                InputChip(
                                    selected = false,
                                    onClick = { onToggleCustomizeFilterChips(true) },
                                    label = { Text("+ Personalizar") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }

                    // SECCION 3: Cuadricula de Albumes Inteligentes (Aspecto Fotografico 4:3 y Conteo Dinamico)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Albumes Inteligentes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (selectedFilterType == "Todos") "Todas las colecciones" else "Filtro: $selectedFilterType",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val displayCollections = run {
                        val baseVisual = when (selectedFilterType) {
                            "Lugares" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("playa") || t.contains("montana") || t.contains("edificio") || t.contains("casa") || t.contains("naturaleza") || t.contains("flor")
                            }
                            "Documentos" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("documento") || t.contains("pasaporte") || t.contains("papel") || t.contains("credencial")
                            }
                            "Ropa" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("camisa") || t.contains("ropa") || t.contains("vestido") || t.contains("zapato") || t.contains("pantal")
                            }
                            "Eventos" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("fiesta") || t.contains("boda") || t.contains("concierto") || t.contains("celebraci") || t.contains("evento")
                            }
                            "Pagos" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("pago") || t.contains("factura") || t.contains("recibo") || t.contains("dinero") || t.contains("tarjeta")
                            }
                            "Mascotas" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("perro") || t.contains("gato") || t.contains("mascota") || t.contains("animal")
                            }
                            "Codigos QR" -> visualCollections.filter { c ->
                                val t = c.title.lowercase(java.util.Locale.ROOT)
                                t.contains("qr") || t.contains("codigo") || t.contains("barcode")
                            }
                            "Todos" -> visualCollections
                            else -> {
                                val q = selectedFilterType.lowercase(java.util.Locale.ROOT).trim()
                                visualCollections.filter { c ->
                                    val t = c.title.lowercase(java.util.Locale.ROOT)
                                    t.contains(q) || q.contains(t)
                                }
                            }
                        }
                        if (selectedFilterType == "Todos") {
                            appCollections + baseVisual
                        } else {
                            baseVisual
                        }
                    }

                    if (displayCollections.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Text(
                                    text = "No hay albumes para el filtro '$selectedFilterType'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        displayCollections.chunked(2).forEach { rowCollections ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (collection in rowCollections) {
                                        SmartCollectionGridCard(
                                            collection = collection,
                                            onClick = { onSelectSmartCollection(collection) },
                                            onHide = { onHideCollection(collection.id) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowCollections.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
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
fun SmartCollectionGridCard(
    collection: SmartCollectionItem,
    onClick: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Gradiente oscuro inferior para garantizar legibilidad del texto
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            startY = 80f
                        )
                    )
            )

            // Conteo dinamico superior
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${collection.count} fotos",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Menu de opciones superior derecho
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
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

            // Titulo inferior
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

@Composable
fun AddPhotosToClusterDialog(
    clusterName: String,
    allPhotos: List<Photo>,
    existingPhotoIds: Set<Long>,
    chosenIndexFolders: Set<String> = emptySet(),
    onAddPhoto: (Photo) -> Unit,
    onDismiss: () -> Unit
) {
    val candidatePhotos = remember(allPhotos, existingPhotoIds, chosenIndexFolders) {
        allPhotos.filter { photo ->
            !existingPhotoIds.contains(photo.id) &&
            (chosenIndexFolders.isEmpty() || chosenIndexFolders.any { folder ->
                photo.folderName.equals(folder, ignoreCase = true) || photo.folderName.contains(folder, ignoreCase = true)
            })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Añadir fotos a $clusterName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (candidatePhotos.isEmpty()) {
                Text(
                    text = "No hay mas fotos disponibles en las carpetas seleccionadas para indexar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    Text(
                        text = "Selecciona una foto para asociarla a este grupo y reforzar el aprendizaje del modelo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 85.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(candidatePhotos, key = { it.id }) { photo ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onAddPhoto(photo)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
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
fun MergeClusterDialog(
    sourceCluster: FaceCluster,
    availableClusters: List<FaceCluster>,
    onMerge: (targetClusterId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val compatibleClusters = remember(sourceCluster, availableClusters) {
        availableClusters.filter { it.id != sourceCluster.id && it.isPet == sourceCluster.isPet }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Combinar \"${sourceCluster.name}\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (compatibleClusters.isEmpty()) {
                Text(
                    text = "No hay otros grupos de " + (if (sourceCluster.isPet) "mascotas" else "personas") + " disponibles para combinar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    Text(
                        text = "Selecciona el grupo de destino con el cual deseas fusionar todas las fotos y promediar sus vectores caracteristicos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(compatibleClusters, key = { it.id }) { cluster ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onMerge(cluster.id)
                                        onDismiss()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = cluster.coverUri,
                                    contentDescription = cluster.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cluster.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${cluster.photoCount} fotos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ChooseCoverDialog(
    clusterName: String,
    photos: List<Photo>,
    onSelectCover: (Photo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Elegir portada para $clusterName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (photos.isEmpty()) {
                Text(
                    text = "No hay fotos en este grupo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    Text(
                        text = "Toca una foto para seleccionarla como la imagen de portada de este grupo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 85.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSelectCover(photo)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
