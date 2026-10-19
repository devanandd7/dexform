package com.dexform.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.core.ai.ModelState
import com.dexform.app.core.ai.ProfileExtractorEngine
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val thinkingText: String? = null,
    val isThinkingExpanded: Boolean = false,
    val imageUris: List<Uri> = emptyList(),
    val proposals: List<ProposedChange> = emptyList(),
    val docTypeStr: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val profileDao = app.container.userProfileDao
    val localAi = app.container.localAiManager
    val aiSession = app.container.aiSession

    val modelState by aiSession.state.collectAsState()
    val isReady = modelState is ModelState.Ready
    val isLoading = modelState is ModelState.Loading
    val isError = modelState is ModelState.Error

    val profilesList by profileDao.getAllProfiles().collectAsState(initial = emptyList())
    val selectedProfile = profilesList.firstOrNull { it.isSelected } ?: profilesList.firstOrNull()

    // Configuration states
    var enableThinking by remember { mutableStateOf(false) } // Default OFF as per Edge Gallery
    var ocrPlusAi by remember { mutableStateOf(false) } // Default OFF: Bypass OCR for 10x faster direct vision
    var temperature by remember { mutableStateOf(0.8f) }
    var topK by remember { mutableStateOf(40) }
    var topP by remember { mutableStateOf(0.95f) }
    var systemPrompt by remember { mutableStateOf("") }
    var maxTokens by remember { mutableStateOf(2048) }
    var showConfigSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Screen Lifecycle: Sync state without force auto-loading
    DisposableEffect(Unit) {
        aiSession.onEnter()
        onDispose {
            aiSession.onLeave()
        }
    }

    val initialWelcomeMessage = remember {
        ChatMessage(
            isUser = false,
            text = "Hello! I am your on-device DexForm AI.\n\n⚡ Please tap 'Load to RAM' at the top to load the AI model into phone memory. Once loaded in RAM, the send button and configuration tools will be enabled."
        )
    }

    val chatMessages = remember {
        mutableStateListOf(initialWelcomeMessage)
    }

    val selectedImages = remember { mutableStateListOf<Uri>() }
    var userPromptText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var liveToolStep by remember { mutableStateOf<String?>(null) }
    var currentGenerationJob by remember { mutableStateOf<Job?>(null) }

    fun handleStop() {
        try {
            currentGenerationJob?.cancel()
            currentGenerationJob = null
            localAi.stopCurrentGeneration()
        } catch (_: Exception) {}
        isProcessing = false
        liveToolStep = null
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val limited = uris.take(3)
            selectedImages.clear()
            selectedImages.addAll(limited)
            if (uris.size > 3) {
                Toast.makeText(context, "Maximum 3 images allowed at a time", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSend() {
        if (!isReady) {
            Toast.makeText(context, "Please load AI Model into RAM from the top first!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImages.isEmpty() && userPromptText.isBlank()) return

        // Abort previous running job if active so it never leaks into background
        if (isProcessing || currentGenerationJob?.isActive == true) {
            handleStop()
        }

        val imagesToProcess = selectedImages.toList()
        val textPrompt = userPromptText.trim()
        val prof = selectedProfile

        // Add user message to UI
        chatMessages.add(
            ChatMessage(
                isUser = true,
                text = textPrompt.ifBlank { "Extract document details" },
                imageUris = imagesToProcess
            )
        )

        selectedImages.clear()
        userPromptText = ""
        isProcessing = true

        currentGenerationJob = scope.launch(Dispatchers.Main) {
            try {
                if (imagesToProcess.isNotEmpty()) {
                    val assistantIndex = chatMessages.size
                    chatMessages.add(ChatMessage(isUser = false, text = ""))

                    val targetProfile = prof ?: UserProfileEntity(name = "Default Profile")
                    val result = withContext(Dispatchers.Default) {
                        ProfileExtractorEngine.extractAndMergeDocuments(
                            context = context,
                            imageUris = imagesToProcess,
                            targetProfile = targetProfile,
                            userQuery = textPrompt,
                            profileDao = profileDao,
                            localAiManager = localAi,
                            useOcr = ocrPlusAi,
                            onStepUpdate = { step ->
                                liveToolStep = step
                            },
                            onTokenStream = { tokenStream ->
                                scope.launch(Dispatchers.Main) {
                                    if (assistantIndex < chatMessages.size) {
                                        chatMessages[assistantIndex] = ChatMessage(
                                            isUser = false,
                                            text = tokenStream
                                        )
                                    }
                                }
                            }
                        )
                    }

                    val proposalsList = result.newFieldsAdded.map { fieldStr ->
                        val parts = fieldStr.split(":", limit = 2)
                        ProposedChange(
                            fieldName = parts.firstOrNull() ?: "Field",
                            oldValue = null,
                            newValue = parts.getOrNull(1)?.trim() ?: "",
                            docType = result.detectedDocType.name,
                            sourceUri = imagesToProcess.first(),
                            conf = Conf.HIGH,
                            isConflict = false
                        )
                    }

                    if (assistantIndex < chatMessages.size) {
                        chatMessages[assistantIndex] = ChatMessage(
                            isUser = false,
                            text = result.aiResponseText,
                            proposals = proposalsList,
                            docTypeStr = result.detectedDocType.name
                        )
                    }
                } else {
                    val promptToSend = textPrompt
                    val assistantIndex = chatMessages.size
                    chatMessages.add(ChatMessage(isUser = false, text = ""))

                    val (finalAnswer, thinking) = withContext(Dispatchers.Default) {
                        localAi.generateResponse(
                            prompt = promptToSend,
                            enableThinking = enableThinking,
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            topK = topK,
                            topP = topP,
                            onTokenStream = { streamUpdate, streamThinking ->
                                scope.launch(Dispatchers.Main) {
                                    if (assistantIndex < chatMessages.size) {
                                        chatMessages[assistantIndex] = ChatMessage(
                                            isUser = false,
                                            text = streamUpdate,
                                            thinkingText = streamThinking
                                        )
                                    }
                                }
                            }
                        )
                    }
                    if (assistantIndex < chatMessages.size) {
                        chatMessages[assistantIndex] = ChatMessage(
                            isUser = false,
                            text = finalAnswer.ifBlank { "Done." },
                            thinkingText = thinking
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    chatMessages.add(ChatMessage(isUser = false, text = "AI Error: ${e.message}"))
                }
            } finally {
                isProcessing = false
                liveToolStep = null
                currentGenerationJob = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedProfile?.imageUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(selectedProfile.imageUri).crossfade(true).build(),
                                    contentDescription = selectedProfile.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("DexForm AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = when (modelState) {
                                        ModelState.Ready -> Color(0xFF16A34A)
                                        is ModelState.Error -> MaterialTheme.colorScheme.error
                                        is ModelState.Loading -> Color(0xFFF59E0B)
                                        ModelState.Busy -> MaterialTheme.colorScheme.primary
                                        ModelState.Unloaded -> MaterialTheme.colorScheme.outline
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val stateLabel = when (val s = modelState) {
                                    ModelState.Ready -> "Loaded in RAM"
                                    ModelState.Busy -> "Thinking..."
                                    is ModelState.Loading -> s.step
                                    is ModelState.Error -> "Load Failed"
                                    ModelState.Unloaded -> "In Storage (Not in RAM)"
                                }
                                Text(
                                    text = stateLabel,
                                    fontSize = 11.sp,
                                    color = when (modelState) {
                                        ModelState.Ready -> Color(0xFF16A34A)
                                        is ModelState.Error -> MaterialTheme.colorScheme.error
                                        is ModelState.Loading -> Color(0xFFF59E0B)
                                        ModelState.Busy -> MaterialTheme.colorScheme.primary
                                        ModelState.Unloaded -> MaterialTheme.colorScheme.outline
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Tool / Configurations Icon: Accessible after model is loaded in RAM
                    IconButton(
                        onClick = { showConfigSheet = true },
                        enabled = isReady
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "AI Settings",
                            tint = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    if (isReady) {
                        OutlinedButton(
                            onClick = { aiSession.unloadFromRam() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unload RAM", fontSize = 11.sp)
                        }
                    } else if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFF59E0B)
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { aiSession.loadModelToRam() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load to RAM", fontSize = 11.sp)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (selectedImages.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            items(selectedImages) { uri ->
                                Box(modifier = Modifier.size(56.dp)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = { selectedImages.remove(uri) },
                                        modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { multiImagePicker.launch("image/*") },
                            enabled = isReady && !isProcessing
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "Add Documents",
                                tint = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }

                        Surface(
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (userPromptText.isEmpty()) {
                                    val placeholder = when {
                                        isLoading -> "Loading AI into RAM, please wait..."
                                        !isReady -> "Tap 'Load to RAM' at top to start..."
                                        else -> "Ask AI (e.g. 'hlo') or attach document..."
                                    }
                                    Text(
                                        placeholder,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = userPromptText,
                                    onValueChange = { userPromptText = it },
                                    enabled = isReady && !isProcessing,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button / Stop Button with red center square & spinning loader
                        val canSend = isReady && !isProcessing && (selectedImages.isNotEmpty() || userPromptText.isNotBlank())
                        IconButton(
                            onClick = {
                                if (isProcessing) handleStop() else handleSend()
                            },
                            enabled = canSend || isProcessing
                        ) {
                            if (isProcessing) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    // Professional AI Stop button (red center stop square)
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color(0xFFEF4444), shape = RoundedCornerShape(2.dp))
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Outlined.Send,
                                    contentDescription = "Send",
                                    tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Error banner if model loading fails
            if (isError) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI Model Load Failed", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    (modelState as ModelState.Error).message,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { aiSession.loadModelToRam() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            items(chatMessages) { msg ->
                if (msg.isUser) {
                    // USER MESSAGE: WhatsApp-style Right-aligned, responsive bubble
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Card(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.widthIn(min = 40.dp, max = 290.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                if (msg.imageUris.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        items(msg.imageUris) { uri ->
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                } else {
                    // AI ASSISTANT MESSAGE: Professional DeepSeek / ChatGPT layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 1. SEPARATE SCROLLABLE THINKING BOX (Rendered ONLY when thinking text exists)
                        if (!msg.thinkingText.isNullOrBlank()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth(0.96f)
                                    .padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                val idx = chatMessages.indexOf(msg)
                                                if (idx != -1) {
                                                    chatMessages[idx] = msg.copy(isThinkingExpanded = !msg.isThinkingExpanded)
                                                }
                                            }
                                    ) {
                                        Icon(
                                            Icons.Outlined.Psychology,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Thinking Process",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                "Reasoning trace",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(
                                            if (msg.isThinkingExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Dedicated Scrollable Viewport for the Reasoning Trace
                                    if (msg.isThinkingExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                                .padding(10.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = msg.thinkingText,
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. SEPARATE CRISP ANSWER BOX
                        Card(
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.fillMaxWidth(0.96f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // AI Header: Badge + Copy Response Action
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "DexForm AI",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (msg.text.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("DexForm AI Response", msg.text)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy Response",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                // Answer Content
                                if (msg.text.isBlank() && isProcessing) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Generating on-device response...",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    FormattedAiMarkdown(text = msg.text)
                                }

                                if (msg.proposals.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ReviewProposalCard(
                                        docType = msg.docTypeStr,
                                        proposals = msg.proposals,
                                        onSaveAccepted = { accepted ->
                                            selectedProfile?.let { prof ->
                                                scope.launch {
                                                    var updated = prof
                                                    accepted.forEach { p ->
                                                        when (p.fieldName) {
                                                            "Full Name", "Name", "Candidate Name" -> updated = updated.copy(name = p.newValue)
                                                            "Father's Name", "Father Name" -> updated = updated.copy(fatherName = p.newValue)
                                                            "Mother's Name", "Mother Name" -> updated = updated.copy(motherName = p.newValue)
                                                            "DOB", "Date of Birth", "Birth Date" -> updated = updated.copy(dob = p.newValue)
                                                            "PAN Number", "Aadhaar Number", "ID Number", "PAN", "Aadhaar" -> updated = updated.copy(idNumber = p.newValue)
                                                            "10th Roll No" -> updated = updated.copy(tenthRollNo = p.newValue)
                                                            "10th Reg No" -> updated = updated.copy(tenthRegNo = p.newValue)
                                                            "10th Passing Year" -> updated = updated.copy(tenthPassingYear = p.newValue)
                                                            "12th Roll No" -> updated = updated.copy(twelfthRollNo = p.newValue)
                                                            "12th Reg No" -> updated = updated.copy(twelfthRegNo = p.newValue)
                                                            "12th Passing Year" -> updated = updated.copy(twelfthPassingYear = p.newValue)
                                                        }
                                                    }
                                                    profileDao.updateProfile(updated)
                                                    Toast.makeText(context, "Saved verified AI details to profile!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            liveToolStep ?: "AI is processing...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // AI Configuration Bottom Sheet (Accessible only after model loaded in RAM)
    if (showConfigSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConfigSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Model Configurations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Configure Gemma on-device inference parameters, temperature, and context memory",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. OCR + AI Mode Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OCR + AI Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (ocrPlusAi)
                                    "ON: OCR pre-extracts text and AI refines, corrects & validates fields."
                                else
                                    "OFF: Direct AI image processing without OCR pre-scan.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ocrPlusAi,
                            onCheckedChange = { ocrPlusAi = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Thinking Mode Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Thinking Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "When OFF, reasoning steps are completely omitted and only the direct answer is shown.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableThinking,
                            onCheckedChange = { enableThinking = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Temperature Setting
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Temperature", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    String.format("%.2f", temperature),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            when {
                                temperature <= 0.35f -> "Precise & Deterministic (Best for OCR & structured forms)"
                                temperature <= 1.05f -> "Balanced (Standard Gemma recommendations)"
                                else -> "Creative & Exploratory (Higher variation)"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..2.0f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Top-P and Top-K Sampling
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sampling: Top-P", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                String.format("%.2f", topP),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = topP,
                            onValueChange = { topP = it },
                            valueRange = 0.1f..1.0f,
                            steps = 17,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Sampling: Top-K", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(20, 40, 60, 100).forEach { k ->
                                FilterChip(
                                    selected = topK == k,
                                    onClick = { topK = k },
                                    label = { Text("$k", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Max Tokens Options
                Text("Max Generation Tokens", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(512, 1024, 2048, 4000).forEach { count ->
                        FilterChip(
                            selected = maxTokens == count,
                            onClick = { maxTokens = count },
                            label = { Text("$count", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Context Management (Clear History)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Context Management", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Clear conversation history to reset context memory.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                handleStop() // Instantly kills any active background generation session
                                chatMessages.clear()
                                chatMessages.add(initialWelcomeMessage)
                                showConfigSheet = false
                                Toast.makeText(context, "Context memory cleared", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. System Instructions Customization
                Text("System Instructions", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = { Text("E.g. Be direct, polite, and concise.", fontSize = 12.sp) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showConfigSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply & Close")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FormattedAiMarkdown(text: String, modifier: Modifier = Modifier) {
    // Strip raw technical JSON block from chat bubble display so output is clean
    val cleanText = text.replace(Regex("""(?s)```(?:json)?\s*\{.*?\}\s*```"""), "").trim()

    val lines = cleanText.lines()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Spacer(modifier = Modifier.height(2.dp))
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val bulletContent = trimmed.substring(2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        RenderMarkdownSpans(bulletContent)
                    }
                }
                else -> {
                    RenderMarkdownSpans(trimmed)
                }
            }
        }
    }
}

@Composable
fun RenderMarkdownSpans(content: String) {
    val annotated = remember(content) {
        androidx.compose.ui.text.buildAnnotatedString {
            var currentIndex = 0
            val boldRegex = Regex("""\*\*(.*?)\*\*""")
            val matches = boldRegex.findAll(content)

            for (m in matches) {
                if (m.range.first > currentIndex) {
                    append(content.substring(currentIndex, m.range.first))
                }
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(m.groupValues[1])
                pop()
                currentIndex = m.range.last + 1
            }
            if (currentIndex < content.length) {
                append(content.substring(currentIndex))
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

