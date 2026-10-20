package com.dexform.app.ui.gallery

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.data.media.MediaItem
import com.dexform.app.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    bucketId: Long,
    albumName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val mediaRepo = app.container.mediaRepository

    val albumItems = remember(bucketId) {
        mediaRepo.getItemsForAlbum(bucketId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        albumName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
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
                    "No files in $albumName",
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
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albumItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = item.mimeType
                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share File"))
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.mimeType.contains("pdf", ignoreCase = true)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Outlined.PictureAsPdf,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color(0xFFDC2626)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            item.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
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

                                // Size badge
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
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "Share",
                                    tint = MaterialTheme.colorScheme.primary,
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
