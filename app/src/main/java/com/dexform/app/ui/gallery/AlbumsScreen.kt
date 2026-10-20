package com.dexform.app.ui.gallery

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.R
import com.dexform.app.data.media.AlbumItem
import com.dexform.app.data.media.MediaManager
import com.dexform.app.data.media.RenameResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (bucketId: Long, albumName: String) -> Unit = { _, _ -> },
    onOpenTools: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val mediaRepo = app.container.mediaRepository

    val allAlbums by mediaRepo.albums.collectAsState()
    val dexFormAlbums by mediaRepo.dexFormAlbums.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }

    // Dialog & Action States
    var showRenameDialog by remember { mutableStateOf(false) }
    var albumToRename by remember { mutableStateOf<AlbumItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var albumToDelete by remember { mutableStateOf<AlbumItem?>(null) }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingAction?.invoke()
            pendingAction = null
            mediaRepo.refresh()
            Toast.makeText(context, "Completed successfully", Toast.LENGTH_SHORT).show()
        } else {
            pendingAction = null
            Toast.makeText(context, "Action cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun executeDeleteAlbum(album: AlbumItem) {
        val items = mediaRepo.getItemsForAlbum(album.bucketId)
        if (items.isEmpty()) {
            Toast.makeText(context, "Folder is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        val intentRequest = MediaManager.createDeleteIntentRequest(context, items)
        if (intentRequest != null) {
            intentSenderLauncher.launch(intentRequest)
        } else {
            val count = MediaManager.directDelete(context, items)
            if (count > 0) {
                mediaRepo.refresh()
                Toast.makeText(context, "Deleted folder and $count photo(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeRenameAlbum(album: AlbumItem, newName: String) {
        val items = mediaRepo.getItemsForAlbum(album.bucketId)
        if (items.isEmpty()) {
            Toast.makeText(context, "Folder is empty", Toast.LENGTH_SHORT).show()
            return
        }

        when (val result = MediaManager.prepareRenameAlbum(context, items, newName)) {
            is RenameResult.Success -> {
                mediaRepo.refresh()
                Toast.makeText(context, "Folder renamed to $newName", Toast.LENGTH_SHORT).show()
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

    val displayAlbums = if (selectedTabIndex == 0) allAlbums else dexFormAlbums

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_albums), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { mediaRepo.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
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
            // Tabs: All Folders vs DexForm Form-Ready
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "All Folders (${allAlbums.size})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "DexForm (${dexFormAlbums.size})",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            if (displayAlbums.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.height(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedTabIndex == 0) "No Photo Folders Found" else "No DexForm Albums Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedTabIndex == 0)
                                "Photos saved to your device will appear here organized in folders."
                            else
                                "Processed images, converted PDFs, and form outputs from DexForm tools will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (selectedTabIndex == 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onOpenTools) {
                                Text("Open Tools")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayAlbums) { album ->
                        var showMenu by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAlbumClick(album.bucketId, album.name) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(album.coverUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = album.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (album.isDexFormAlbum) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFFF59E0B),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Star,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.height(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.form_ready),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Three-dots menu button overlay
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Outlined.MoreVert,
                                                contentDescription = "Options",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename Folder") },
                                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    albumToRename = album
                                                    renameInput = album.name
                                                    showRenameDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Delete Folder",
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    albumToDelete = album
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = album.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${album.count} items",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Folder Dialog
    if (showRenameDialog && albumToRename != null) {
        val target = albumToRename!!
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                albumToRename = null
            },
            title = { Text("Rename Folder") },
            text = {
                Column {
                    Text(
                        "Enter new name for folder '${target.name}':",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        label = { Text("Folder Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInput.trim()
                        showRenameDialog = false
                        val al = albumToRename
                        albumToRename = null
                        if (al != null && newName.isNotBlank()) {
                            executeRenameAlbum(al, newName)
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        albumToRename = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Folder Confirmation Dialog
    if (showDeleteDialog && albumToDelete != null) {
        val target = albumToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                albumToDelete = null
            },
            title = { Text("Delete Folder") },
            text = {
                Text(
                    "Are you sure you want to permanently delete folder '${target.name}' and all ${target.count} photo(s) inside it?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val al = albumToDelete
                        albumToDelete = null
                        if (al != null) {
                            executeDeleteAlbum(al)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        albumToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
