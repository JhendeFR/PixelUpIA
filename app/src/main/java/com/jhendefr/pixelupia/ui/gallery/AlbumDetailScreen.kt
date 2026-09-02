package com.jhendefr.pixelupia.ui.gallery

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jhendefr.pixelupia.domain.model.Photo
import com.jhendefr.pixelupia.ui.common.ConfirmDeleteDialog
import com.jhendefr.pixelupia.ui.common.FolderPickerDialog
import com.jhendefr.pixelupia.ui.theme.PixelUpIAMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: AlbumDetailViewModel,
    onBackClick: () -> Unit,
    onPhotoClick: (Photo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEvent(AlbumDetailEvent.OnIntentSenderCompleted)
        } else {
            viewModel.onEvent(AlbumDetailEvent.OnIntentSenderDismissed)
        }
    }

    LaunchedEffect(uiState.pendingIntentSender) {
        uiState.pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onEvent(AlbumDetailEvent.ClearUserMessage)
        }
    }

    // Dialogos de Gestion
    if (uiState.showDeleteConfirm) {
        ConfirmDeleteDialog(
            count = uiState.selectedPhotoIds.size,
            onConfirm = { viewModel.onEvent(AlbumDetailEvent.ConfirmDeleteSelected) },
            onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissDeleteDialog) }
        )
    }

    if (uiState.showFolderPickerForMove) {
        FolderPickerDialog(
            title = "Mover ${uiState.selectedPhotoIds.size} fotos",
            existingFolders = uiState.existingFolders,
            onFolderSelected = { folder -> viewModel.onEvent(AlbumDetailEvent.ConfirmMoveSelected(folder)) },
            onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissMoveDialog) }
        )
    }

    if (uiState.showFolderPickerForCopy) {
        FolderPickerDialog(
            title = "Copiar ${uiState.selectedPhotoIds.size} fotos",
            existingFolders = uiState.existingFolders,
            onFolderSelected = { folder -> viewModel.onEvent(AlbumDetailEvent.ConfirmCopySelected(folder)) },
            onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissCopyDialog) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = uiState.isSelectionMode,
                transitionSpec = {
                    (fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                            slideInVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it / 2 })
                        .togetherWith(
                            fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring) +
                                    slideOutVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { -it / 2 }
                        )
                },
                label = "AlbumTopBarAnimation"
            ) { isSelection ->
                if (isSelection) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${uiState.selectedPhotoIds.size} seleccionadas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.onEvent(AlbumDetailEvent.ClearSelection) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar seleccion")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.onEvent(AlbumDetailEvent.SelectAll) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todo")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = uiState.albumName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            } else if (uiState.errorMessage != null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Error",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.onEvent(AlbumDetailEvent.Refresh) }) {
                            Text("Reintentar")
                        }
                    }
                }
            } else {
                PhotoGrid(
                    photos = uiState.photos,
                    selectedIds = uiState.selectedPhotoIds,
                    isSelectionMode = uiState.isSelectionMode,
                    onPhotoClick = onPhotoClick,
                    onToggleSelection = { viewModel.onEvent(AlbumDetailEvent.TogglePhotoSelection(it)) }
                )
            }

            // BARRA INFERIOR TIPO PILDORA (MATERIAL 3 EXPRESSIVE)
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it } +
                        fadeIn(animationSpec = PixelUpIAMotion.effectsFloatSpring),
                exit = slideOutVertically(animationSpec = PixelUpIAMotion.spatialIntOffsetSpring) { it } +
                        fadeOut(animationSpec = PixelUpIAMotion.effectsFloatSpring),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mover
                        IconButton(
                            onClick = { viewModel.onEvent(AlbumDetailEvent.RequestMoveSelected) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = "Mover",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Copiar
                        IconButton(
                            onClick = { viewModel.onEvent(AlbumDetailEvent.RequestCopySelected) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.FileCopy,
                                contentDescription = "Copiar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Compartir
                        IconButton(
                            onClick = { sharePhotos(context, uiState.selectedPhotos) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Eliminar
                        IconButton(
                            onClick = { viewModel.onEvent(AlbumDetailEvent.RequestDeleteSelected) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
