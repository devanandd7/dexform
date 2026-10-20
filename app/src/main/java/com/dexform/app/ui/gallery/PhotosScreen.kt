package com.dexform.app.ui.gallery

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.R
import com.dexform.app.data.media.MediaItem
import com.dexform.app.util.formatDuration
import com.dexform.app.util.formatFileSize

import com.dexform.app.data.media.MediaManager
import com.dexform.app.data.media.RenameResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Edit

enum class FilterType {
    ALL, UNDER_50KB, UNDER_200KB, OVER_1MB
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    onNavigateToSettings: () -> Unit = {},
    onOpenBrowser: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val mediaRepo = app.container.mediaRepository

    val mediaItems by mediaRepo.mediaItems.collectAsState()

    var activeFilter by remember { mutableStateOf(FilterType.ALL) }
    var selectedItemIds by remember { mutableStateOf(setOf<Long>()) }
    var viewingImageIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var itemsToDelete by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var showRenameFileDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<MediaItem?>(null) }
    var renameFileInput by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isSelectionMode = selectedItemIds.isNotEmpty()

    // Activity Result Launcher for Deletions and Renames
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingAction?.invoke()
            pendingAction = null
            mediaRepo.refresh()
            selectedItemIds = emptySet()
            if (viewingImageIndex != null) {
                viewingImageIndex = null
            }
            Toast.makeText(context, "Completed successfully", Toast.LENGTH_SHORT).show()
        } else {
            pendingAction = null
            Toast.makeText(context, "Action cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun executeDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val intentRequest = MediaManager.createDeleteIntentRequest(context, items)
        if (intentRequest != null) {
            intentSenderLauncher.launch(intentRequest)
        } else {
            val count = MediaManager.directDelete(context, items)
            if (count > 0) {
                mediaRepo.refresh()
                selectedItemIds = emptySet()
                if (viewingImageIndex != null) {
                    viewingImageIndex = null
                }
                Toast.makeText(context, "Deleted $count photo(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeRenameFile(item: MediaItem, newBaseName: String) {
        if (newBaseName.isBlank()) return
        when (val result = MediaManager.prepareRenameFile(context, item, newBaseName)) {
            is RenameResult.Success -> {
                mediaRepo.refresh()
                Toast.makeText(context, "Photo renamed to ${result.newName}", Toast.LENGTH_SHORT).show()
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

    // Permissions State (Images only)
    var isGranted by remember { mutableStateOf(false) }
    var isPartialGranted by remember { mutableStateOf(false) }

    fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fullImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val selected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

            isGranted = fullImages
            isPartialGranted = !isGranted && selected
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val fullImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            isGranted = fullImages
            isPartialGranted = false
        } else {
            isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            isPartialGranted = false
        }
        if (isGranted || isPartialGranted) {
            mediaRepo.refresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissions()
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    val permissionsToRequest = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val filteredList = remember(mediaItems, activeFilter) {
        when (activeFilter) {
            FilterType.ALL -> mediaItems
            FilterType.UNDER_50KB -> mediaItems.filter { it.sizeBytes < 50 * 1024 }
            FilterType.UNDER_200KB -> mediaItems.filter { it.sizeBytes < 200 * 1024 }
            FilterType.OVER_1MB -> mediaItems.filter { it.sizeBytes > 1024 * 1024 }
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
                            text = stringResource(R.string.app_display_name),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedItemIds.size == 1) {
                            val item = mediaItems.firstOrNull { it.id in selectedItemIds }
                            if (item != null) {
                                IconButton(onClick = {
                                    itemToRename = item
                                    renameFileInput = item.displayName.substringBeforeLast('.')
                                    showRenameFileDialog = true
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename Photo")
                                }
                            }
                        }
                        IconButton(onClick = {
                            val selectedItems = mediaItems.filter { it.id in selectedItemIds }
                            if (selectedItems.isNotEmpty()) {
                                itemsToDelete = selectedItems
                                showDeleteConfirmDialog = true
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = {
                            val selectedUris = mediaItems.filter { it.id in selectedItemIds }.map { it.uri }
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selectedUris))
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Selected"))
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { selectedItemIds = emptySet() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel selection")
                        }
                    } else {
                        IconButton(onClick = { mediaRepo.refresh() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Denied View
            if (!isGranted && !isPartialGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Photo,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.permission_denied_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { permissionLauncher.launch(permissionsToRequest) }) {
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    }
                }
            } else {
                // Partial Permission Banner (Android 14+)
                if (isPartialGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.permission_partial_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }) {
                                Text(stringResource(R.string.allow_all))
                            }
                        }
                    }
                }

                // Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = activeFilter == FilterType.ALL,
                            onClick = { activeFilter = FilterType.ALL },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == FilterType.UNDER_50KB,
                            onClick = { activeFilter = FilterType.UNDER_50KB },
                            label = { Text(stringResource(R.string.filter_under_50kb)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == FilterType.UNDER_200KB,
                            onClick = { activeFilter = FilterType.UNDER_200KB },
                            label = { Text(stringResource(R.string.filter_under_200kb)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == FilterType.OVER_1MB,
                            onClick = { activeFilter = FilterType.OVER_1MB },
                            label = { Text(stringResource(R.string.filter_over_1mb)) }
                        )
                    }
                }

                // Empty state
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.no_photos),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onOpenBrowser) {
                                Text(stringResource(R.string.open_browser))
                            }
                        }
                    }
                } else {
                    // Media Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredList) { item ->
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
                                                viewingImageIndex = filteredList.indexOf(item)
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
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Selection checkmark overlay
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
        }
    }

    viewingImageIndex?.let { index ->
        ImageViewer(
            items = filteredList,
            initialIndex = index,
            onDismiss = { viewingImageIndex = null },
            onDelete = { item ->
                itemsToDelete = listOf(item)
                showDeleteConfirmDialog = true
            },
            onRename = { item ->
                itemToRename = item
                renameFileInput = item.displayName.substringBeforeLast('.')
                showRenameFileDialog = true
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

    if (showDeleteConfirmDialog && itemsToDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                itemsToDelete = emptyList()
            },
            title = {
                Text(
                    text = if (itemsToDelete.size == 1) "Delete Photo" else "Delete ${itemsToDelete.size} Photos"
                )
            },
            text = {
                Text(
                    text = if (itemsToDelete.size == 1)
                        "Are you sure you want to delete this photo permanently?"
                    else
                        "Are you sure you want to delete ${itemsToDelete.size} selected photos permanently?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = itemsToDelete
                        showDeleteConfirmDialog = false
                        itemsToDelete = emptyList()
                        executeDelete(toDelete)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        itemsToDelete = emptyList()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


