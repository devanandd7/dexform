package com.dexform.app.ui.gallery

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.data.media.MediaItem
import com.dexform.app.data.media.MediaManager
import com.dexform.app.data.media.RenameResult
import com.dexform.app.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    bucketId: Long,
    albumName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val mediaRepo = app.container.mediaRepository

    var currentAlbumName by remember { mutableStateOf(albumName) }
    val mediaItems by mediaRepo.mediaItems.collectAsState()
    val albumItems = remember(mediaItems, bucketId) {
        mediaRepo.getItemsForAlbum(bucketId)
    }

    var viewingImageIndex by remember { mutableStateOf<Int?>(null) }
    var selectedItemIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedItemIds.isNotEmpty()

    // Dialog States
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var renameFolderInput by remember { mutableStateOf("") }

    var showDeleteFolderDialog by remember { mutableStateOf(false) }

    var showRenameFileDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<MediaItem?>(null) }
    var renameFileInput by remember { mutableStateOf("") }

    var showDeleteFilesDialog by remember { mutableStateOf(false) }
    var itemsToDelete by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isDeletingEntireAlbum by remember { mutableStateOf(false) }

    // Pending callback after system write/delete permission
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingAction?.invoke()
            pendingAction = null
            mediaRepo.refresh()
            selectedItemIds = emptySet()
            if (isDeletingEntireAlbum) {
                isDeletingEntireAlbum = false
                onBack()
            }
            Toast.makeText(context, "Completed successfully", Toast.LENGTH_SHORT).show()
        } else {
            pendingAction = null
            isDeletingEntireAlbum = false
            Toast.makeText(context, "Action cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun executeDeleteItems(items: List<MediaItem>, isEntireAlbum: Boolean = false) {
        if (items.isEmpty()) return
        isDeletingEntireAlbum = isEntireAlbum

        val intentRequest = MediaManager.createDeleteIntentRequest(context, items)
        if (intentRequest != null) {
            intentSenderLauncher.launch(intentRequest)
        } else {
            val count = MediaManager.directDelete(context, items)
            if (count > 0) {
                mediaRepo.refresh()
                selectedItemIds = emptySet()
                if (viewingImageIndex != null) viewingImageIndex = null
                Toast.makeText(context, "Deleted $count item(s)", Toast.LENGTH_SHORT).show()
                if (isEntireAlbum) {
                    onBack()
                }
            }
        }
    }

    fun executeRenameFolder(newName: String) {
        if (newName.isBlank() || newName == currentAlbumName) return
        when (val result = MediaManager.prepareRenameAlbum(context, albumItems, newName)) {
            is RenameResult.Success -> {
                currentAlbumName = newName
                mediaRepo.refresh()
                Toast.makeText(context, "Folder renamed to $newName", Toast.LENGTH_SHORT).show()
            }
            is RenameResult.RequiresPermission -> {
                pendingAction = {
                    currentAlbumName = newName
                    result.pendingAction.invoke()
                }
                intentSenderLauncher.launch(result.request)
            }
            is RenameResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeRenameFile(item: MediaItem, newBaseName: String) {
        if (newBaseName.isBlank()) return
        when (val result = MediaManager.prepareRenameFile(context, item, newBaseName)) {
            is RenameResult.Success -> {
                mediaRepo.refresh()
                Toast.makeText(context, "File renamed to ${result.newName}", Toast.LENGTH_SHORT).show()
            }
            is RenameResult.RequiresPermission -> {
                pendingAction = result.pendingAction
                intentSenderLauncher.launch(result.request)
            }
            is RenameResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedItemIds.size} selected")
                    } else {
                        Text(
                            currentAlbumName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            selectedItemIds = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Outlined.Close else Icons.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // Rename single selected item
                        if (selectedItemIds.size == 1) {
                            val selectedItem = albumItems.firstOrNull { it.id in selectedItemIds }
                            if (selectedItem != null) {
                                IconButton(onClick = {
                                    itemToRename = selectedItem
                                    renameFileInput = selectedItem.displayName.substringBeforeLast('.')
                                    showRenameFileDialog = true
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename File")
                                }
                            }
                        }

                        // Share selected items
                        IconButton(onClick = {
                            val selectedUris = albumItems.filter { it.id in selectedItemIds }.map { it.uri }
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selectedUris))
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Selected"))
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }

                        // Delete selected items
                        IconButton(onClick = {
                            val items = albumItems.filter { it.id in selectedItemIds }
                            if (items.isNotEmpty()) {
                                itemsToDelete = items
                                showDeleteFilesDialog = true
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // Rename Folder
                        IconButton(onClick = {
                            renameFolderInput = currentAlbumName
                            showRenameFolderDialog = true
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Rename Folder")
                        }

                        // Delete Entire Folder
                        IconButton(onClick = {
                            showDeleteFolderDialog = true
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Folder",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        // Refresh
                        IconButton(onClick = { mediaRepo.refresh() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (albumItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No files in $currentAlbumName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(albumItems) { item ->
                    val isSelected = item.id in selectedItemIds

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedItemIds = if (isSelected) {
                                            selectedItemIds - item.id
                                        } else {
                                            selectedItemIds + item.id
                                        }
                                    } else {
                                        if (item.mimeType.contains("pdf", ignoreCase = true)) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = item.mimeType
                                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share File"))
                                        } else {
                                            viewingImageIndex = albumItems.indexOf(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    selectedItemIds = if (isSelected) {
                                        selectedItemIds - item.id
                                    } else {
                                        selectedItemIds + item.id
                                    }
                                }
                            )
                    ) {
                        if (item.mimeType.contains("pdf", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = Color(0xFFDC2626)
                                    )
                                    Text(
                                        item.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Selection Checkmark Overlay
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(24.dp)
                                )
                            }
                        }

                        // Size badge (Bottom-Left)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatFileSize(item.sizeBytes),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Format badge (Bottom-Right)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val fmtText = when {
                                item.mimeType.contains("pdf", true) -> "PDF"
                                item.mimeType.contains("png", true) -> "PNG"
                                item.mimeType.contains("webp", true) -> "WEBP"
                                else -> "JPG"
                            }
                            Text(
                                text = fmtText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive View Mode (Tap-to-view, Left/Right swipe, Rename, Delete)
    viewingImageIndex?.let { index ->
        ImageViewer(
            items = albumItems,
            initialIndex = index,
            onDismiss = { viewingImageIndex = null },
            onDelete = { item ->
                itemsToDelete = listOf(item)
                showDeleteFilesDialog = true
            },
            onRename = { item ->
                itemToRename = item
                renameFileInput = item.displayName.substringBeforeLast('.')
                showRenameFileDialog = true
            }
        )
    }

    // Rename Folder Dialog
    if (showRenameFolderDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text("Rename Folder") },
            text = {
                Column {
                    Text(
                        "Enter new name for folder '$currentAlbumName':",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameFolderInput,
                        onValueChange = { renameFolderInput = it },
                        singleLine = true,
                        label = { Text("Folder Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameFolderInput.trim()
                        showRenameFolderDialog = false
                        if (newName.isNotBlank()) {
                            executeRenameFolder(newName)
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Entire Folder Confirmation Dialog
    if (showDeleteFolderDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = false },
            title = { Text("Delete Folder") },
            text = {
                Text("Are you sure you want to permanently delete folder '$currentAlbumName' and all ${albumItems.size} photo(s) inside it?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteFolderDialog = false
                        executeDeleteItems(albumItems, isEntireAlbum = true)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename File Dialog
    if (showRenameFileDialog && itemToRename != null) {
        val item = itemToRename!!
        val ext = item.displayName.substringAfterLast('.', "")
        AlertDialog(
            onDismissRequest = {
                showRenameFileDialog = false
                itemToRename = null
            },
            title = { Text("Rename Photo") },
            text = {
                Column {
                    Text(
                        "Enter new name for this photo:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameFileInput,
                        onValueChange = { renameFileInput = it },
                        singleLine = true,
                        label = { Text("File Name") },
                        trailingIcon = {
                            if (ext.isNotEmpty()) {
                                Text(
                                    text = ".$ext",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = renameFileInput.trim()
                        showRenameFileDialog = false
                        val target = itemToRename
                        itemToRename = null
                        if (target != null && input.isNotBlank()) {
                            executeRenameFile(target, input)
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameFileDialog = false
                        itemToRename = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Files Confirmation Dialog
    if (showDeleteFilesDialog && itemsToDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showDeleteFilesDialog = false
                itemsToDelete = emptyList()
            },
            title = {
                Text(if (itemsToDelete.size == 1) "Delete Photo" else "Delete ${itemsToDelete.size} Photos")
            },
            text = {
                Text(
                    if (itemsToDelete.size == 1)
                        "Are you sure you want to permanently delete this photo?"
                    else
                        "Are you sure you want to permanently delete ${itemsToDelete.size} selected photos?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = itemsToDelete
                        showDeleteFilesDialog = false
                        itemsToDelete = emptyList()
                        executeDeleteItems(toDelete, isEntireAlbum = false)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteFilesDialog = false
                        itemsToDelete = emptyList()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
