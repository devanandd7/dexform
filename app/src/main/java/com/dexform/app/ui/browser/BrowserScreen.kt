package com.dexform.app.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.R
import com.dexform.app.data.db.HistoryEntity
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val viewModel = remember { BrowserViewModel(app.container) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickLinks by app.container.quickLinkDao.getAll().collectAsState(initial = emptyList())
    val historyList by app.container.historyDao.getAllHistory().collectAsState(initial = emptyList())
    val profilesList by app.container.userProfileDao.getAllProfiles().collectAsState(initial = emptyList())
    val activeProfile = profilesList.firstOrNull { it.isSelected } ?: profilesList.firstOrNull()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://duckduckgo.com") }
    var inputUrl by remember { mutableStateOf("") }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var showQuickLinksHome by remember { mutableStateOf(false) }
    var showHistoryOverlay by remember { mutableStateOf(false) }
    var showPickerOverlay by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val reqNow = (state as? UploadState.Choosing)?.req
        if (ok && cameraUri != null && reqNow != null) {
            viewModel.onFilesPicked(context, listOf(cameraUri!!), reqNow)
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val reqNow = (state as? UploadState.Choosing)?.req
        if (uris.isNotEmpty() && reqNow != null) {
            viewModel.onFilesPicked(context, uris, reqNow)
        }
    }

    fun loadUrl(urlStr: String) {
        val trimmed = urlStr.trim()
        val targetUrl = when {
            trimmed.isBlank() -> "https://duckduckgo.com"
            !trimmed.contains(" ") && trimmed.contains(".") -> {
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            }
            else -> "https://duckduckgo.com/?q=${Uri.encode(trimmed)}"
        }
        showQuickLinksHome = false
        showHistoryOverlay = false
        currentUrl = targetUrl
        inputUrl = targetUrl
        webViewInstance?.loadUrl(targetUrl)
    }

    BackHandler(enabled = state !is UploadState.Idle || showPickerOverlay || showHistoryOverlay || webViewInstance?.canGoBack() == true) {
        when {
            showHistoryOverlay -> showHistoryOverlay = false
            showPickerOverlay -> showPickerOverlay = false
            state !is UploadState.Idle -> viewModel.cancel()
            webViewInstance?.canGoBack() == true -> webViewInstance?.goBack()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(21.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (currentUrl.startsWith("https")) Icons.Outlined.Lock else Icons.Outlined.Public,
                                    contentDescription = null,
                                    tint = if (currentUrl.startsWith("https")) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                BasicTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { loadUrl(inputUrl) }),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = @Composable { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (inputUrl.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.search_or_type_url),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                if (inputUrl.isNotEmpty()) {
                                    IconButton(
                                        onClick = { inputUrl = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Cancel,
                                            contentDescription = "Clear URL",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                if (isLoading) {
                                    IconButton(
                                        onClick = { webViewInstance?.stopLoading() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = { webViewInstance?.reload() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // User Profile Avatar Action Button
                        IconButton(onClick = onNavigateToProfile) {
                            if (activeProfile?.imageUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(activeProfile.imageUri).crossfade(true).build(),
                                    contentDescription = "User Profile",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.AccountCircle,
                                    contentDescription = "User Profile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Outlined.Person, contentDescription = null)
                                },
                                text = { Text(activeProfile?.let { "Profile: ${it.name}" } ?: "Create / Switch Profile") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToProfile()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Outlined.History, contentDescription = null)
                                },
                                text = { Text("Browsing History") },
                                onClick = {
                                    showMenu = false
                                    showHistoryOverlay = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Browsing Data") },
                                onClick = {
                                    showMenu = false
                                    webViewInstance?.clearCache(true)
                                    webViewInstance?.clearHistory()
                                    CookieManager.getInstance().removeAllCookies(null)
                                    scope.launch {
                                        app.container.historyDao.clearHistory()
                                    }
                                    Toast.makeText(context, "Browsing data and history cleared", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    if (isLoading && pageProgress < 1f) {
                        LinearProgressIndicator(
                            progress = pageProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = webViewInstance?.canGoBack() == true
                    ) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = webViewInstance?.canGoForward() == true
                    ) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = {
                        showQuickLinksHome = true
                    }) {
                        Icon(Icons.Outlined.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // WebView Host
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportMultipleWindows(false)
                            allowFileAccess = false
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript(UPLOAD_HOOK_JS, null)
                                url?.let {
                                    if (it.isNotBlank() && !it.startsWith("about:")) {
                                        currentUrl = it
                                        inputUrl = it
                                        val pageTitle = view.title?.ifBlank { it } ?: it
                                        scope.launch {
                                            app.container.historyDao.insertHistory(
                                                HistoryEntity(title = pageTitle, url = it)
                                            )
                                        }
                                    }
                                }
                                isLoading = false
                            }

                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: android.net.http.SslError
                            ) {
                                handler.cancel()
                                Toast.makeText(ctx, "SSL Connection Blocked for Security", Toast.LENGTH_SHORT).show()
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                pageProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }

                            override fun onShowFileChooser(
                                view: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams
                            ): Boolean {
                                viewModel.attachChooser(
                                    callback = filePathCallback,
                                    accept = fileChooserParams.acceptTypes.joinToString(","),
                                    multiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                                    host = Uri.parse(view.url ?: "").host ?: ""
                                )
                                view.evaluateJavascript("JSON.stringify(window.__dexform && window.__dexform.last)") { raw ->
                                    viewModel.onHints(raw)
                                }
                                return true
                            }
                        }

                        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                            if (url.startsWith("http")) {
                                try {
                                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                                        setMimeType(mimetype)
                                        addRequestHeader("User-Agent", userAgent)
                                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "DexForm_${System.currentTimeMillis()}")
                                    }
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    dm.enqueue(request)
                                    Toast.makeText(ctx, "Download Started", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(ctx, "This download type isn't supported yet", Toast.LENGTH_SHORT).show()
                            }
                        }

                        webViewInstance = this
                        loadUrl("https://duckduckgo.com")
                    }
                }
            )

            // Quick Links Home Overlay
            if (showQuickLinksHome) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Text("Browser Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (quickLinks.isEmpty()) {
                            Text("No saved quick links. Type a web address above to search or navigate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(quickLinks) { link ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { loadUrl(link.url) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(link.title, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Browsing History Overlay
            if (showHistoryOverlay) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showHistoryOverlay = false }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close History")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Browsing History",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (historyList.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            app.container.historyDao.clearHistory()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (historyList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No history found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items = historyList, key = { it.id }) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                loadUrl(item.url)
                                                showHistoryOverlay = false
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.url,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

            // Upload State Overlays
            when (val s = state) {
                is UploadState.Choosing -> UploadSheet(
                    req = s.req,
                    needsManual = s.needsManual,
                    onEditRequirement = viewModel::updateRequirement,
                    onGallery = { showPickerOverlay = true },
                    onCamera = {
                        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(camDir, "shot_${System.currentTimeMillis()}.jpg")
                        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraLauncher.launch(cameraUri!!)
                    },
                    onFiles = { filesLauncher.launch(arrayOf("image/*", "application/pdf")) },
                    onDismiss = viewModel::cancel
                )
                is UploadState.Processing -> ProcessingDialog(s.message)
                is UploadState.Preview -> PreviewDialog(
                    items = s.items,
                    req = s.req,
                    onUse = viewModel::confirm,
                    onCancel = viewModel::cancel
                )
                is UploadState.Error -> ErrorDialog(s.message, onCancel = viewModel::cancel)
                UploadState.Idle -> Unit
            }

            // Gallery Pick Mode Dialog Overlay
            if (showPickerOverlay) {
                val req = (state as? UploadState.Choosing)?.req
                Dialog(
                    onDismissRequest = { showPickerOverlay = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    GalleryPickMode(
                        requirement = req,
                        multiple = (state as? UploadState.Choosing)?.multiple == true,
                        onDone = { uris ->
                            showPickerOverlay = false
                            if (req != null) viewModel.onFilesPicked(context, uris, req)
                        },
                        onClose = { showPickerOverlay = false }
                    )
                }
            }
        }
    }
}
