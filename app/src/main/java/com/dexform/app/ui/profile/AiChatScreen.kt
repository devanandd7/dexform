package com.dexform.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dexform.app.R
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.dexform.app.App
import com.dexform.app.core.ai.BioExtractionParser
import com.dexform.app.core.ai.DocumentProposal
import com.dexform.app.core.ai.GenerationChunk
import com.dexform.app.core.ai.ImageAiHelper
import com.dexform.app.core.ai.LocalAiManager
import com.dexform.app.core.ai.ModelState
import com.dexform.app.core.ai.SaveToBioEngine
import com.dexform.app.AppContainer
import com.dexform.app.data.db.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val imageFile: File? = null,
    val isStreaming: Boolean = false,
    val backend: String = "GPU",
    val isDivider: Boolean = false,
    val dividerText: String? = null,
    val proposal: DocumentProposal? = null,
    val isSavedToBio: Boolean = false,
    val isSavingToBio: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val aiSession = app.container.aiSession
    val localAi = app.container.localAiManager
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val modelState by aiSession.state.collectAsState()
    val isReady = modelState is ModelState.Ready
    val isLoading = modelState is ModelState.Loading
    val isError = modelState is ModelState.Error

    val messages = remember { mutableStateListOf<ChatItem>() }
    var inputText by remember { mutableStateOf("") }
    var attachedImageFile by remember { mutableStateOf<File?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Screen Lifecycle: onEnter when opening screen, onLeave when exiting
    DisposableEffect(Unit) {
        aiSession.onEnter()
        onDispose {
            generationJob?.cancel()
            localAi.cancelGeneration()
            aiSession.onLeave()
            ImageAiHelper.clearAiCache(context)
        }
    }

    // New image attachment logic: resets active conversation and adds divider if conversation was already active
    fun handleNewImageAttached(file: File) {
        if (messages.any { !it.isDivider }) {
            scope.launch {
                localAi.resetConversation()
            }
            messages.add(
                ChatItem(
                    isUser = false,
                    text = "",
                    isDivider = true,
                    dividerText = context.getString(R.string.new_image_divider)
                )
            )
        }
        attachedImageFile = file
    }

    // Activity Launchers for Gallery and Camera
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val prepared = ImageAiHelper.prepareImageForAi(context, uri)
                    handleNewImageAttached(prepared)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val uri = tempCameraUri
            if (uri != null) {
                scope.launch {
                    try {
                        val prepared = ImageAiHelper.prepareImageForAi(context, uri)
                        handleNewImageAttached(prepared)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to capture photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Auto-scroll tracking
    val isScrolledToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index >= totalItems - 1
            }
        }
    }

    // Safe auto-scroll to bottom on streaming or new message
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty() && (isScrolledToBottom || isGenerating)) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // Theme Colors
    val screenBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val userBubbleBg = if (isDark) Color(0xFF1E3A6B) else Color(0xFFD3E3FD)
    val userTextColor = if (isDark) Color.White else Color(0xFF041E49)
    val modelTextColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val titleAccent = Color(0xFFE5533D)
    val sendButtonBg = Color(0xFF5C2B2B)
    val inputContainerBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF0F4F9)
    val inputBorderColor = if (isDark) Color(0xFF333333) else Color(0xFFD3D3D3)

    Scaffold(
        containerColor = screenBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = null,
                                tint = titleAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ask_image_title),
                                color = titleAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        // Model Chip (Gemma-4-E2B-it) with status dot
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF252525) else Color(0xFFEAEAEA),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusDotColor = when (modelState) {
                                    is ModelState.Ready -> Color(0xFF16A34A) // Green
                                    is ModelState.Loading, is ModelState.Busy -> Color(0xFFF59E0B) // Amber
                                    is ModelState.Error -> Color(0xFFDC2626) // Red
                                    ModelState.Unloaded -> Color.Gray
                                }
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = LocalAiManager.MODEL_DISPLAY_NAME,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color(0xFFCCCCCC) else Color(0xFF444444)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings sheet wired in Step 4 */ }) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.settings_dialog_title),
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Menu",
                                tint = if (isDark) Color.White else Color.Black
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_chat)) },
                                onClick = {
                                    showOverflowMenu = false
                                    messages.clear()
                                    attachedImageFile = null
                                    scope.launch {
                                        localAi.resetConversation()
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = screenBg)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Thin loading banner when model is loading
                if (isLoading) {
                    val loadingStep = (modelState as? ModelState.Loading)?.step ?: stringResource(R.string.model_loading)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF1F5F9))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = loadingStep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = titleAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = titleAccent,
                            trackColor = titleAccent.copy(alpha = 0.2f)
                        )
                    }
                }

                // Error Banner with Retry button
                if (isError) {
                    val errorMsg = (modelState as? ModelState.Error)?.message ?: "Error"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMsg,
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { aiSession.retryLoad() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.retry), fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }

                // Empty chat placeholder
                if (messages.isEmpty() && !isLoading && !isError) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.empty_chat_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFE3E3E3) else Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.empty_chat_subtitle),
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF888888) else Color(0xFF666666)
                            )
                        }
                    }
                } else {
                    // Message List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            if (message.isDivider) {
                                // Thin "New image" divider
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Divider(
                                        modifier = Modifier.weight(1f),
                                        color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) Color(0xFF222222) else Color(0xFFEEEEEE),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = message.dividerText ?: stringResource(R.string.new_image_divider),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Divider(
                                        modifier = Modifier.weight(1f),
                                        color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
                                    )
                                }
                            } else if (message.isUser) {
                                // User Message: Right-aligned rounded container
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = stringResource(R.string.you),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666),
                                        modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(userBubbleBg)
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Column {
                                            if (message.imageFile != null) {
                                                AsyncImage(
                                                    model = message.imageFile,
                                                    contentDescription = "Attached photo",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 220.dp)
                                                        .clip(RoundedCornerShape(12.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                            Text(
                                                text = message.text,
                                                color = userTextColor,
                                                fontSize = 15.sp,
                                                lineHeight = 21.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Model Message: Full width, plain Markdown text
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    // Caption: "Model on GPU" (or CPU)
                                    val backendCaption = if (message.backend.equals("CPU", ignoreCase = true)) {
                                        stringResource(R.string.model_on_cpu)
                                    } else {
                                        stringResource(R.string.model_on_gpu)
                                    }
                                    Text(
                                        text = backendCaption,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = titleAccent,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    // Markdown Rendering
                                    MarkdownText(
                                        markdown = message.text,
                                        isStreaming = message.isStreaming,
                                        textColor = modelTextColor
                                    )

                                    // Interactive Bio / Document Proposal Card
                                    if (!message.isStreaming && message.proposal != null && message.proposal.fields.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        DocumentProposalCard(
                                            proposal = message.proposal,
                                            isDark = isDark,
                                            isSaved = message.isSavedToBio,
                                            isSaving = message.isSavingToBio,
                                            onToggleField = { fieldId, isSelected ->
                                                val p = message.proposal
                                                val updatedFields = p.fields.map {
                                                    if (it.id == fieldId) it.copy(isSelected = isSelected) else it
                                                }
                                                val msgIdx = messages.indexOfFirst { it.id == message.id }
                                                if (msgIdx != -1) {
                                                    messages[msgIdx] = message.copy(proposal = p.copy(fields = updatedFields))
                                                }
                                            },
                                            onToggleTier = { fieldId ->
                                                val p = message.proposal
                                                val updatedFields = p.fields.map {
                                                    if (it.id == fieldId) {
                                                        val newTier = if (it.tier == Tier.BIO) Tier.SUB else Tier.BIO
                                                        it.copy(tier = newTier)
                                                    } else it
                                                }
                                                val msgIdx = messages.indexOfFirst { it.id == message.id }
                                                if (msgIdx != -1) {
                                                    messages[msgIdx] = message.copy(proposal = p.copy(fields = updatedFields))
                                                }
                                            },
                                            onSave = {
                                                val msgIdx = messages.indexOfFirst { it.id == message.id }
                                                if (msgIdx != -1) {
                                                    messages[msgIdx] = message.copy(isSavingToBio = true)
                                                    scope.launch {
                                                        try {
                                                            val result = SaveToBioEngine.saveProposal(
                                                                context = context,
                                                                container = app.container,
                                                                proposal = message.proposal,
                                                                sourceImageFile = message.imageFile
                                                            )
                                                            val currentMsgIdx = messages.indexOfFirst { it.id == message.id }
                                                            if (currentMsgIdx != -1) {
                                                                messages[currentMsgIdx] = messages[currentMsgIdx].copy(
                                                                    isSavingToBio = false,
                                                                    isSavedToBio = true
                                                                )
                                                            }
                                                            Toast.makeText(
                                                                context,
                                                                "Saved! Added ${result.savedBioFields} bio fields & ${result.savedSubFields} doc fields",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } catch (e: Exception) {
                                                            val currentMsgIdx = messages.indexOfFirst { it.id == message.id }
                                                            if (currentMsgIdx != -1) {
                                                                messages[currentMsgIdx] = messages[currentMsgIdx].copy(isSavingToBio = false)
                                                            }
                                                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    // Copy Icon row (when answer finished)
                                    if (!message.isStreaming && message.text.isNotBlank()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", message.text))
                                                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.ContentCopy,
                                                    contentDescription = stringResource(R.string.copy_text),
                                                    tint = if (isDark) Color(0xFF888888) else Color(0xFF666666),
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

                // Scroll to Bottom Floating Button
                AnimatedVisibility(
                    visible = !isScrolledToBottom && messages.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        containerColor = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE2E8F0)
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom Input Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, inputBorderColor, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        color = inputContainerBg
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            // Attached Image Thumbnail with delete (X) button
                            AnimatedVisibility(visible = attachedImageFile != null) {
                                attachedImageFile?.let { file ->
                                    Box(
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .size(68.dp)
                                    ) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = "Attached Image",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC), RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Delete X button on top-right
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    try { file.delete() } catch (_: Exception) {}
                                                    attachedImageFile = null
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Close,
                                                    contentDescription = stringResource(R.string.remove_image),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Text Input Field
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp, max = 120.dp),
                                textStyle = TextStyle(
                                    color = if (isDark) Color.White else Color.Black,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                ),
                                cursorBrush = SolidColor(titleAccent),
                                enabled = isReady && !isGenerating,
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.type_prompt),
                                            color = if (isDark) Color(0xFF666666) else Color(0xFF999999),
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bottom Row inside Input Container: (+) button on left, Send/Stop on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // (+) Attach button with DropdownMenu (Photo library & Camera)
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(
                                            onClick = { showAttachMenu = true },
                                            enabled = isReady && !isGenerating,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription = stringResource(R.string.attach_photo),
                                                tint = if (isDark) Color.White else Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showAttachMenu,
                                        onDismissRequest = { showAttachMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.photo_library)) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                                            },
                                            onClick = {
                                                showAttachMenu = false
                                                galleryLauncher.launch("image/*")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.camera)) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                                            },
                                            onClick = {
                                                showAttachMenu = false
                                                try {
                                                    val cameraDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
                                                    val photoFile = File(cameraDir, "camera_${System.currentTimeMillis()}.jpg")
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        photoFile
                                                    )
                                                    tempCameraUri = uri
                                                    cameraLauncher.launch(uri)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }

                                // Send / Stop Button
                                if (isGenerating) {
                                    // Stop Button
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(sendButtonBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(
                                            onClick = {
                                                localAi.cancelGeneration()
                                                generationJob?.cancel()
                                                isGenerating = false
                                                aiSession.setReady()
                                                val lastMsg = messages.lastOrNull()
                                                if (lastMsg != null && lastMsg.isStreaming) {
                                                    val idx = messages.indexOf(lastMsg)
                                                    messages[idx] = lastMsg.copy(isStreaming = false)
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Stop,
                                                contentDescription = stringResource(R.string.stop),
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // Send Button
                                    val canSend = isReady && !isGenerating && (inputText.isNotBlank() || attachedImageFile != null)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (canSend) sendButtonBg else sendButtonBg.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (canSend) {
                                                    val rawText = inputText.trim()
                                                    val imageToSend = attachedImageFile
                                                    attachedImageFile = null
                                                    inputText = ""

                                                    // Add user message
                                                    messages.add(
                                                        ChatItem(
                                                            isUser = true,
                                                            text = rawText.ifBlank { "Extract details from document" },
                                                            imageFile = imageToSend
                                                        )
                                                    )

                                                    // Add placeholder model response
                                                    val assistantItem = ChatItem(
                                                        isUser = false,
                                                        text = "",
                                                        imageFile = imageToSend,
                                                        isStreaming = true,
                                                        backend = localAi.realBackend
                                                    )
                                                    val assistantId = assistantItem.id
                                                    messages.add(assistantItem)

                                                    isGenerating = true
                                                    aiSession.setBusy()

                                                    generationJob = scope.launch {
                                                        try {
                                                            val profileContext = buildProfileContextPrompt(app.container)
                                                            val promptToSend = if (imageToSend != null) {
                                                                val extractionGuide = if (rawText.isBlank()) {
                                                                    "Please analyze this document thoroughly and extract all details accurately. Follow these instructions carefully:\n" +
                                                                    "1. TIER CLASSIFICATION:\n" +
                                                                    "   - Academic Qualifications (10th/Matriculation, 12th/Intermediate, Degree, Diploma marksheet/certificate) and National IDs (PAN Card, Aadhaar, Voter ID, Driving License) MUST be classified as Primary Bio (Tier.BIO).\n" +
                                                                    "   - Only single-use event or examination entry passes (Admit Cards, Hall Tickets, Exam Scorecards with expiration) belong to Sub-Bio (Tier.SUB).\n" +
                                                                    "2. CONTEXTUAL FIELD NAMING:\n" +
                                                                    "   - Do not use generic field labels like 'Number' or 'ID'. Use specific labels (e.g. 'PAN Card Number' for PAN card, '10th Roll Number' for 10th marksheet).\n" +
                                                                    "3. MATHEMATICAL REASONING FOR MARKS:\n" +
                                                                    "   - When Total/Max Marks and Obtained Marks are present, compute Percentage (obtained / total * 100) and Division (1st if >= 60%, 2nd if >= 45%, 3rd otherwise) if not already stated.\n\n" +
                                                                    "Organize the output clearly as:\n" +
                                                                    "- Document Type: (e.g. 10th Marksheet, PAN Card, Admit Card, Degree)\n" +
                                                                    "- Primary Bio (Permanent personal or academic info):\n" +
                                                                    "  * Field: Value\n" +
                                                                    "- Sub-Bio (Document specific temporary slip info):\n" +
                                                                    "  * Field: Value"
                                                                } else {
                                                                    "$rawText\n\n[Please extract all detected details like Document Type, Name, ID Number, Marks, Board, Roll No, etc. Marksheets, Degrees and National IDs belong in Primary Bio (Tier.BIO).]"
                                                                }
                                                                if (profileContext.isNotBlank()) {
                                                                    "$profileContext\n$extractionGuide"
                                                                } else {
                                                                    extractionGuide
                                                                }
                                                            } else {
                                                                if (profileContext.isNotBlank()) {
                                                                    "$profileContext\nUser Query: $rawText\n\nAnswer the user's question accurately, smartly, and helpfully using their personal and sub-personal profile data provided above."
                                                                } else {
                                                                    rawText
                                                                }
                                                            }

                                                            localAi.sendMessageStream(
                                                                text = promptToSend,
                                                                imageFile = imageToSend,
                                                                enableThinking = true
                                                            ).collect { chunk ->
                                                                val idx = messages.indexOfFirst { it.id == assistantId }
                                                                if (idx != -1) {
                                                                    when (chunk) {
                                                                        is GenerationChunk.Token -> {
                                                                            val current = messages[idx]
                                                                            messages[idx] = current.copy(
                                                                                text = current.text + chunk.text
                                                                            )
                                                                        }
                                                                        is GenerationChunk.Done -> {
                                                                            val current = messages[idx]
                                                                            val selectedProfile = app.container.userProfileDao.getSelectedProfile()
                                                                            val associatedImage = current.imageFile ?: imageToSend ?: messages.take(idx).lastOrNull { it.isUser && it.imageFile != null }?.imageFile
                                                                            val proposal = BioExtractionParser.parse(
                                                                                aiText = current.text,
                                                                                currentProfile = selectedProfile,
                                                                                imagePath = associatedImage?.absolutePath
                                                                            )
                                                                            messages[idx] = current.copy(
                                                                                isStreaming = false,
                                                                                imageFile = associatedImage,
                                                                                proposal = proposal
                                                                            )
                                                                            isGenerating = false
                                                                            aiSession.setReady()
                                                                        }
                                                                        is GenerationChunk.Error -> {
                                                                            val current = messages[idx]
                                                                            val errMsg = if (chunk.throwable.message?.contains("taking too long") == true) {
                                                                                context.getString(R.string.taking_too_long)
                                                                            } else {
                                                                                "Error: ${chunk.throwable.message}"
                                                                            }
                                                                            messages[idx] = current.copy(
                                                                                text = (current.text + "\n\n$errMsg").trim(),
                                                                                isStreaming = false
                                                                            )
                                                                            isGenerating = false
                                                                            aiSession.setReady()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            val idx = messages.indexOfFirst { it.id == assistantId }
                                                            if (idx != -1) {
                                                                val current = messages[idx]
                                                                messages[idx] = current.copy(
                                                                    text = (current.text + "\n\nError: ${e.message}").trim(),
                                                                    isStreaming = false
                                                                )
                                                            }
                                                            isGenerating = false
                                                            aiSession.setReady()
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = canSend,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Send,
                                                contentDescription = stringResource(R.string.send),
                                                tint = if (canSend) Color.White else Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
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

/**
 * Builds a comprehensive context window containing all user personal (Tier.BIO)
 * and sub-personal / document (Tier.SUB) data so the AI can answer user queries
 * accurately and cross-reference information.
 */
private suspend fun buildProfileContextPrompt(container: AppContainer): String = withContext(Dispatchers.IO) {
    try {
        val profile = container.userProfileDao.getSelectedProfile() ?: return@withContext ""
        val defMap = container.fieldDefDao.getApprovedDefs().associateBy { it.key }

        val sb = StringBuilder()
        sb.append("[USER PERSONAL & SUB-PERSONAL PROFILE DATA CONTEXT]\n")
        sb.append("You are DexForm AI, an intelligent personal assistant and form-filling thinker. You have authorized and verified access to the user's active personal profile, academic records, and document details below.\n")
        sb.append("When the user asks any question about their personal details, marks, IDs, qualifications, or documents, ALWAYS answer accurately and directly using this context. Think step-by-step and perform any required mathematical reasoning.\n\n")

        sb.append("=== PRIMARY PERSONAL BIO (Tier.BIO) ===\n")
        sb.append("• Full Name: ").append(profile.name).append("\n")
        if (profile.fatherName.isNotBlank()) sb.append("• Father's Name: ").append(profile.fatherName).append("\n")
        if (profile.motherName.isNotBlank()) sb.append("• Mother's Name: ").append(profile.motherName).append("\n")
        if (profile.dob.isNotBlank()) sb.append("• Date of Birth (DOB): ").append(profile.dob).append("\n")
        if (profile.gender.isNotBlank()) sb.append("• Gender: ").append(profile.gender).append("\n")
        if (profile.category.isNotBlank()) sb.append("• Category/Caste: ").append(profile.category).append("\n")
        if (profile.mobile.isNotBlank()) sb.append("• Mobile Number: ").append(profile.mobile).append("\n")
        if (profile.email.isNotBlank()) sb.append("• Email Address: ").append(profile.email).append("\n")
        if (profile.address.isNotBlank()) sb.append("• Permanent Address: ").append(profile.address).append("\n")
        if (profile.idNumber.isNotBlank()) sb.append("• National ID (Aadhaar/PAN): ").append(profile.idNumber).append("\n")

        if (profile.tenthRollNo.isNotBlank() || profile.tenthMarks.isNotBlank() || profile.tenthBoard.isNotBlank()) {
            sb.append("• 10th / Matriculation Details:\n")
            if (profile.tenthRollNo.isNotBlank()) sb.append("  - Roll No: ").append(profile.tenthRollNo).append("\n")
            if (profile.tenthRegNo.isNotBlank()) sb.append("  - Reg No: ").append(profile.tenthRegNo).append("\n")
            if (profile.tenthBoard.isNotBlank()) sb.append("  - Board: ").append(profile.tenthBoard).append("\n")
            if (profile.tenthPassingYear.isNotBlank()) sb.append("  - Passing Year: ").append(profile.tenthPassingYear).append("\n")
            if (profile.tenthMarks.isNotBlank()) sb.append("  - Marks / Percentage: ").append(profile.tenthMarks).append("\n")
            if (profile.tenthDivision.isNotBlank()) sb.append("  - Division: ").append(profile.tenthDivision).append("\n")
        }

        if (profile.twelfthRollNo.isNotBlank() || profile.twelfthMarks.isNotBlank() || profile.twelfthBoard.isNotBlank()) {
            sb.append("• 12th / Intermediate Details:\n")
            if (profile.twelfthRollNo.isNotBlank()) sb.append("  - Roll No: ").append(profile.twelfthRollNo).append("\n")
            if (profile.twelfthRegNo.isNotBlank()) sb.append("  - Reg No: ").append(profile.twelfthRegNo).append("\n")
            if (profile.twelfthBoard.isNotBlank()) sb.append("  - Board: ").append(profile.twelfthBoard).append("\n")
            if (profile.twelfthPassingYear.isNotBlank()) sb.append("  - Passing Year: ").append(profile.twelfthPassingYear).append("\n")
            if (profile.twelfthMarks.isNotBlank()) sb.append("  - Marks / Percentage: ").append(profile.twelfthMarks).append("\n")
            if (profile.twelfthDivision.isNotBlank()) sb.append("  - Division: ").append(profile.twelfthDivision).append("\n")
        }

        val bioValues = container.fieldValueDao.getBioValuesOnce(profile.id)
        if (bioValues.isNotEmpty()) {
            sb.append("• Additional Permanent Bio Attributes:\n")
            for (bv in bioValues) {
                val label = defMap[bv.fieldKey]?.label ?: bv.fieldKey.substringAfterLast('.')
                sb.append("  - ").append(label).append(": ").append(bv.valueEnc).append("\n")
            }
        }

        val docRecords = container.docRecordDao.getDocsForProfileOnce(profile.id)
        if (docRecords.isNotEmpty()) {
            sb.append("\n=== SUB-PERSONAL & DOCUMENT RECORDS (Tier.SUB) ===\n")
            for (doc in docRecords) {
                sb.append("• Document [").append(doc.docType).append("] ").append(doc.title).append(" (Tier: ").append(doc.tier.name).append("):\n")
                val docVals = container.fieldValueDao.getDocValuesOnce(profile.id, doc.id)
                for (dv in docVals) {
                    val label = defMap[dv.fieldKey]?.label ?: dv.fieldKey.substringAfterLast('.')
                    sb.append("  - ").append(label).append(": ").append(dv.valueEnc).append("\n")
                }
            }
        }

        sb.append("=== END OF PROFILE DATA CONTEXT ===\n\n")
        sb.toString()
    } catch (e: Exception) {
        ""
    }
}

