package com.dexform.app.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.core.ai.ModelStatus
import com.dexform.app.core.ai.ProfileExtractorEngine
import kotlinx.coroutines.launch

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val imageUris: List<Uri> = emptyList(),
    val isToolStep: Boolean = false
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

    val aiStatus by localAi.status.collectAsState()
    val isModelInRam = aiStatus is ModelStatus.LoadedInRam

    val profilesList by profileDao.getAllProfiles().collectAsState(initial = emptyList())
    val selectedProfile = profilesList.firstOrNull { it.isSelected } ?: profilesList.firstOrNull()

    // RAM Management Lifecycle: Auto-load on enter, Auto-unload on leave
    DisposableEffect(Unit) {
        scope.launch {
            localAi.loadModelIntoRam()
        }
        onDispose {
            localAi.unloadFromRam()
        }
    }

    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Hello! Upload study document images (10th/12th marksheets, Aadhaar, PAN). I will perform 4-stage smart reasoning, extract details, and save them to your profile without duplicating existing data!"
            )
        )
    }

    val selectedImages = remember { mutableStateListOf<Uri>() }
    var userPromptText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var liveToolStep by remember { mutableStateOf<String?>(null) }

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
        val prof = selectedProfile
        if (prof == null) {
            Toast.makeText(context, "Please create a user profile first!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isModelInRam) {
            Toast.makeText(context, "AI Model is loading into RAM. Please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImages.isEmpty() && userPromptText.isBlank()) return

        val imagesToProcess = selectedImages.toList()
        val textPrompt = userPromptText.trim()

        chatMessages.add(
            ChatMessage(
                isUser = true,
                text = textPrompt.ifBlank { "Extract document details & update bio-data" },
                imageUris = imagesToProcess
            )
        )

        selectedImages.clear()
        userPromptText = ""
        isProcessing = true

        scope.launch {
            try {
                if (imagesToProcess.isNotEmpty()) {
                    // Tool Step 1
                    liveToolStep = "⚙️ Step 1/4: Image Preprocessing & Multi-Angle OCR Scan..."
                    kotlinx.coroutines.delay(400)

                    // Tool Step 2
                    liveToolStep = "🧠 Step 2/4: Gemma 4 E2B Pattern Extraction & Analysis..."
                    kotlinx.coroutines.delay(400)

                    // Tool Step 3
                    liveToolStep = "🔍 Step 3/4: Logical Verification & Duplicate Checking..."

                    val result = ProfileExtractorEngine.extractAndMergeDocuments(
                        context = context,
                        imageUris = imagesToProcess,
                        targetProfile = prof,
                        userQuery = textPrompt,
                        profileDao = profileDao,
                        localAiManager = localAi
                    )

                    // Tool Step 4
                    liveToolStep = "✅ Step 4/4: Updating Profile Bio-Data..."
                    kotlinx.coroutines.delay(300)

                    val replyMsg = buildString {
                        append("✅ Extracted Details Updated for Profile '${result.updatedProfile.name}':\n")
                        if (result.newFieldsAdded.isNotEmpty()) {
                            append("\n➕ Added Fields:\n")
                            result.newFieldsAdded.forEach { append(" • $it\n") }
                        }
                        if (result.duplicatesSkipped.isNotEmpty()) {
                            append("\n⚠️ Duplicates Prevented (Already Exists):\n")
                            result.duplicatesSkipped.forEach { append(" • $it\n") }
                        }
                        append("\n💡 AI Analysis:\n${result.aiResponseText}")
                    }

                    chatMessages.add(ChatMessage(isUser = false, text = replyMsg))
                } else {
                    liveToolStep = "🧠 Generating On-Device Response..."
                    val res = localAi.generateResponse("User prompt for profile '${prof.name}': $textPrompt")
                    chatMessages.add(ChatMessage(isUser = false, text = res))
                }
            } catch (e: Exception) {
                chatMessages.add(ChatMessage(isUser = false, text = "Error during extraction: ${e.message}"))
            } finally {
                isProcessing = false
                liveToolStep = null
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
                                    model = ImageRequest.Builder(context).data(selectedProfile?.imageUri).crossfade(true).build(),
                                    contentDescription = selectedProfile?.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("DexForm AI Extractor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isModelInRam) Color(0xFF16A34A) else Color(0xFFF59E0B)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isModelInRam) "Model Loaded in RAM (~1.4 GB)" else "Loading Model into RAM...",
                                    fontSize = 11.sp,
                                    color = if (isModelInRam) Color(0xFF16A34A) else Color(0xFFF59E0B),
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
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // RAM Loading Banner if model not ready
                    if (!isModelInRam) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Loading AI Model into RAM for processing... Send button will unlock shortly.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Live Tool Execution Banner
                    AnimatedVisibility(visible = liveToolStep != null) {
                        liveToolStep?.let { step ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(step, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    // Selected images preview strip
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
                        IconButton(onClick = { multiImagePicker.launch("image/*") }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Documents", tint = MaterialTheme.colorScheme.primary)
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
                                    Text("Ask AI or add marksheets...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                BasicTextField(
                                    value = userPromptText,
                                    onValueChange = { userPromptText = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { handleSend() },
                            enabled = isModelInRam && !isProcessing && (selectedImages.isNotEmpty() || userPromptText.isNotBlank())
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    Icons.Outlined.Send,
                                    contentDescription = "Send",
                                    tint = if (isModelInRam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages) { msg ->
                val align = if (msg.isUser) Alignment.End else Alignment.Start
                val bg = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (msg.imageUris.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
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
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
