package com.dexform.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dexform.app.App
import com.dexform.app.R
import com.dexform.app.core.ai.ModelStatus
import com.dexform.app.core.ai.MultimodalFormAnalyzer
import com.dexform.app.core.ai.StructuredFormRequirements
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val localAi = app.container.localAiManager

    val aiStatus by localAi.status.collectAsState()

    var showAiTestDialog by remember { mutableStateOf(false) }
    var testPromptInput by remember { mutableStateOf("Verify if photo requirements match max 20 KB limit") }
    var aiResponseText by remember { mutableStateOf("") }
    var aiGenerating by remember { mutableStateOf(false) }

    var ocrExtracting by remember { mutableStateOf(false) }
    var structuredResult by remember { mutableStateOf<StructuredFormRequirements?>(null) }

    var showFileInfoThumbnails by remember { mutableStateOf(true) }
    var saveConvertedToAlbum by remember { mutableStateOf(true) }

    val ocrImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                ocrExtracting = true
                structuredResult = null
                try {
                    val result = MultimodalFormAnalyzer.analyzeFormImage(context, uri, localAi)
                    structuredResult = result
                } catch (e: Exception) {
                    Toast.makeText(context, "Analysis Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    ocrExtracting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local AI Model Manager Card (Gemma 4 E2B)
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Local AI Model (Gemma 4 E2B)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "100% On-Device LiteRT Quantized Model",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (aiStatus) {
                                is ModelStatus.LoadedInRam -> Color(0xFF2563EB).copy(alpha = 0.15f)
                                is ModelStatus.Downloaded -> Color(0xFF16A34A).copy(alpha = 0.15f)
                                is ModelStatus.Downloading -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (aiStatus) {
                                        is ModelStatus.LoadedInRam -> Icons.Outlined.Memory
                                        is ModelStatus.Downloaded -> Icons.Outlined.CheckCircle
                                        is ModelStatus.Downloading -> Icons.Outlined.CloudDownload
                                        else -> Icons.Outlined.SdCard
                                    },
                                    contentDescription = null,
                                    tint = when (aiStatus) {
                                        is ModelStatus.LoadedInRam -> Color(0xFF2563EB)
                                        is ModelStatus.Downloaded -> Color(0xFF16A34A)
                                        is ModelStatus.Downloading -> Color(0xFFF59E0B)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    val statusText = when (val s = aiStatus) {
                                        is ModelStatus.LoadedInRam -> "Loaded in RAM (~${s.ramUsageMb} MB Allocated)"
                                        is ModelStatus.Downloaded -> "Downloaded (${s.fileSizeBytes / (1024 * 1024)} MB in App Folder)"
                                        is ModelStatus.Downloading -> "Downloading Model (${s.progressPct}%)"
                                        is ModelStatus.Error -> "Error: ${s.message}"
                                        ModelStatus.NotDownloaded -> "Not Downloaded (Required ~1.3 GB)"
                                    }
                                    Text(
                                        text = "Status: $statusText",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Stored in: app_files/models/gemma-4-E2B-it.litertlm",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Download Progress
                        if (aiStatus is ModelStatus.Downloading) {
                            val dl = aiStatus as ModelStatus.Downloading
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = dl.progressPct / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${dl.downloadedBytes / (1024 * 1024)} MB / ${dl.totalBytes / (1024 * 1024)} MB (${dl.progressPct}%)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (aiStatus) {
                                ModelStatus.NotDownloaded, is ModelStatus.Error -> {
                                    Button(
                                        onClick = { localAi.downloadModel() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download Model")
                                    }
                                }
                                is ModelStatus.Downloaded -> {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                localAi.loadModelIntoRam()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.Memory, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Load into RAM")
                                    }
                                    OutlinedButton(
                                        onClick = { localAi.deleteModel() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete")
                                    }
                                }
                                is ModelStatus.LoadedInRam -> {
                                    Button(
                                        onClick = { showAiTestDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.Psychology, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test AI Prompt")
                                    }
                                    OutlinedButton(
                                        onClick = { localAi.unloadFromRam() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Unload RAM")
                                    }
                                }
                                is ModelStatus.Downloading -> {
                                    OutlinedButton(
                                        onClick = { localAi.deleteModel() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cancel Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dual-Path Multimodal Analysis (OCR + AI Vision) Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multimodal Image Analysis (OCR + AI)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "2-Path processing: OCR text extraction + Gemma AI correction",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { ocrImagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !ocrExtracting
                        ) {
                            if (ocrExtracting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing Image (OCR + AI)...")
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Image for Multimodal Analysis")
                            }
                        }

                        AnimatedVisibility(visible = structuredResult != null) {
                            structuredResult?.let { res ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Structured Form Requirements",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Analysis", res.correctedText))
                                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Field Type: ${res.fieldType}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        Text("Max KB: ${res.maxKb?.let { "$it KB" } ?: "Not specified"}")
                                        Text("Min KB: ${res.minKb?.let { "$it KB" } ?: "None"}")
                                        Text("Dimensions: ${if (res.widthPx != null) "${res.widthPx}x${res.heightPx} px" else "Free"}")
                                        Text("Allowed Formats: ${res.formats.joinToString("/")}")

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Corrected Text (AI Typos Fixed):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = res.correctedText,
                                                modifier = Modifier.padding(8.dp),
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Raw OCR Text:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = res.rawOcrText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // General Preferences
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_show_file_info), fontWeight = FontWeight.Medium)
                                Text("Display file size & format on gallery items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = showFileInfoThumbnails,
                                onCheckedChange = { showFileInfoThumbnails = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_save_to_album), fontWeight = FontWeight.Medium)
                                Text("Automatically save processed files to Pictures/DexForm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = saveConvertedToAlbum,
                                onCheckedChange = { saveConvertedToAlbum = it }
                            )
                        }
                    }
                }
            }

            // Privacy Note Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_privacy_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Interactive Local AI Prompt Test Dialog
    if (showAiTestDialog) {
        Dialog(onDismissRequest = { showAiTestDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Local AI (Gemma 4 E2B)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = testPromptInput,
                        onValueChange = { testPromptInput = it },
                        label = { Text("Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                aiGenerating = true
                                aiResponseText = ""
                                try {
                                    val res = localAi.generateResponse(testPromptInput)
                                    aiResponseText = res
                                } catch (e: Exception) {
                                    aiResponseText = "Error: ${e.message}"
                                } finally {
                                    aiGenerating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !aiGenerating && testPromptInput.isNotBlank()
                    ) {
                        if (aiGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating On-Device...")
                        } else {
                            Icon(Icons.Outlined.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Local AI Prompt")
                        }
                    }

                    if (aiResponseText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Response:", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(aiResponseText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { showAiTestDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
