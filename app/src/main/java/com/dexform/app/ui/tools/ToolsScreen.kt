package com.dexform.app.ui.tools

import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dexform.app.App
import com.dexform.app.R
import com.dexform.app.core.imaging.BitmapLoader
import com.dexform.app.core.imaging.FitMode
import com.dexform.app.core.imaging.Fmt
import com.dexform.app.core.imaging.KB
import com.dexform.app.core.imaging.compressToMax
import com.dexform.app.core.imaging.ensureMin
import com.dexform.app.core.imaging.imagesToPdf
import com.dexform.app.core.imaging.pdfFirstPageToBitmap
import com.dexform.app.core.imaging.readInfo
import com.dexform.app.core.imaging.resizeExact
import com.dexform.app.core.imaging.rotate
import com.dexform.app.core.imaging.shrinkPdf
import com.dexform.app.core.imaging.toJpeg
import com.dexform.app.core.imaging.toPng
import com.dexform.app.data.media.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ToolItem(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector
)

data class ToolPreset(
    val labelRes: Int,
    val maxKb: Long? = null,
    val minKb: Long? = null,
    val fmt: Fmt = Fmt.JPG
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var selectedToolId by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var processing by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Form parameters
    var maxKbInput by remember { mutableStateOf("20") }
    var minKbInput by remember { mutableStateOf("") }
    var widthPxInput by remember { mutableStateOf("") }
    var heightPxInput by remember { mutableStateOf("") }
    var selectedFmt by remember { mutableStateOf(Fmt.JPG) }
    var fitMode by remember { mutableStateOf(FitMode.CROP_FILL) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            resultFile = null
            errorMessage = null
        }
    }

    val toolsList = remember {
        listOf(
            ToolItem("compress", R.string.tool_compress_title, R.string.tool_compress_desc, Icons.Outlined.Compress),
            ToolItem("resize", R.string.tool_resize_title, R.string.tool_resize_desc, Icons.Outlined.Transform),
            ToolItem("convert", R.string.tool_convert_title, R.string.tool_convert_desc, Icons.Outlined.Refresh),
            ToolItem("increase", R.string.tool_increase_size_title, R.string.tool_increase_size_desc, Icons.Outlined.ZoomIn),
            ToolItem("img_to_pdf", R.string.tool_img_to_pdf_title, R.string.tool_img_to_pdf_desc, Icons.Outlined.PictureAsPdf),
            ToolItem("shrink_pdf", R.string.tool_shrink_pdf_title, R.string.tool_shrink_pdf_desc, Icons.Outlined.Description),
            ToolItem("pdf_to_img", R.string.tool_pdf_to_img_title, R.string.tool_pdf_to_img_desc, Icons.Outlined.Image),
            ToolItem("crop_rotate", R.string.tool_crop_title, R.string.tool_crop_desc, Icons.Outlined.Crop),
            ToolItem("checker", R.string.tool_file_checker_title, R.string.tool_file_checker_desc, Icons.Outlined.Info)
        )
    }

    val presets = remember {
        listOf(
            ToolPreset(R.string.preset_photo_20kb, maxKb = 20),
            ToolPreset(R.string.preset_photo_50kb, maxKb = 50),
            ToolPreset(R.string.preset_photo_20_50kb, maxKb = 50, minKb = 20),
            ToolPreset(R.string.preset_sig_20kb, maxKb = 20),
            ToolPreset(R.string.preset_sig_10_20kb, maxKb = 20, minKb = 10),
            ToolPreset(R.string.preset_pdf_100kb, maxKb = 100, fmt = Fmt.PDF),
            ToolPreset(R.string.preset_pdf_200kb, maxKb = 200, fmt = Fmt.PDF),
            ToolPreset(R.string.preset_pdf_500kb, maxKb = 500, fmt = Fmt.PDF)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Star, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.hero_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.hero_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Button(
                            onClick = {
                                selectedToolId = "compress"
                                maxKbInput = "20"
                                selectedUri = null
                                resultFile = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(stringResource(R.string.start))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }

            // Quick Presets Row
            item {
                Column {
                    Text(
                        text = stringResource(R.string.presets_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { preset ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    selectedToolId = if (preset.fmt == Fmt.PDF) "shrink_pdf" else "compress"
                                    maxKbInput = preset.maxKb?.toString() ?: ""
                                    minKbInput = preset.minKb?.toString() ?: ""
                                    selectedFmt = preset.fmt
                                    selectedUri = null
                                    resultFile = null
                                },
                                label = { Text(stringResource(preset.labelRes)) }
                            )
                        }
                    }
                }
            }

            // Selected Tool Execution Area
            if (selectedToolId != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val currentTool = toolsList.firstOrNull { it.id == selectedToolId }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                currentTool?.icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentTool?.let { stringResource(it.titleRes) } ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // File picker section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val mime = if (selectedToolId == "shrink_pdf" || selectedToolId == "pdf_to_img") "application/pdf" else "image/*"
                                        filePickerLauncher.launch(mime)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.pick_from_gallery))
                                }
                            }

                            if (selectedUri != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val fileInfo = remember(selectedUri) { readInfo(context, selectedUri!!) }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Selected file", fontWeight = FontWeight.Medium)
                                        Text("Size: ${fileInfo.sizeBytes / 1024} KB | Format: ${fileInfo.format} ${fileInfo.width?.let { "| ${it}x${fileInfo.height}" } ?: ""}")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tool parameters based on tool id
                            if (selectedToolId in listOf("compress", "increase")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = maxKbInput,
                                        onValueChange = { maxKbInput = it },
                                        label = { Text(stringResource(R.string.target_kb)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = minKbInput,
                                        onValueChange = { minKbInput = it },
                                        label = { Text(stringResource(R.string.min_kb)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (selectedToolId == "resize") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = widthPxInput,
                                        onValueChange = { widthPxInput = it },
                                        label = { Text(stringResource(R.string.width_px)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = heightPxInput,
                                        onValueChange = { heightPxInput = it },
                                        label = { Text(stringResource(R.string.height_px)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.fit_mode), style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = fitMode == FitMode.CROP_FILL,
                                        onClick = { fitMode = FitMode.CROP_FILL },
                                        label = { Text(stringResource(R.string.crop_fill)) }
                                    )
                                    FilterChip(
                                        selected = fitMode == FitMode.FIT_WHITE_PADDING,
                                        onClick = { fitMode = FitMode.FIT_WHITE_PADDING },
                                        label = { Text(stringResource(R.string.fit_padding)) }
                                    )
                                    FilterChip(
                                        selected = fitMode == FitMode.STRETCH,
                                        onClick = { fitMode = FitMode.STRETCH },
                                        label = { Text(stringResource(R.string.stretch)) }
                                    )
                                }
                            }

                            if (selectedToolId == "shrink_pdf") {
                                OutlinedTextField(
                                    value = maxKbInput,
                                    onValueChange = { maxKbInput = it },
                                    label = { Text(stringResource(R.string.target_kb)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (selectedUri == null) {
                                        Toast.makeText(context, "Please select a file first", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        processing = true
                                        resultFile = null
                                        errorMessage = null
                                        try {
                                            val outDir = File(context.cacheDir, "processed").apply { mkdirs() }
                                            val maxKb = maxKbInput.toLongOrNull()?.times(KB)
                                            val minKb = minKbInput.toLongOrNull()?.times(KB)

                                            val file = withContext(Dispatchers.Default) {
                                                when (selectedToolId) {
                                                    "compress" -> {
                                                        val bmp = BitmapLoader.load(context, selectedUri!!)
                                                        val enc = compressToMax(bmp, maxKb ?: (20 * KB))
                                                        File(outDir, "compressed_${enc.quality}q.jpg").apply { writeBytes(enc.bytes) }
                                                    }
                                                    "increase" -> {
                                                        val bmp = BitmapLoader.load(context, selectedUri!!)
                                                        val bytes = ensureMin(bmp, minKb ?: (20 * KB), maxKb ?: (200 * KB), false)
                                                        File(outDir, "increased.jpg").apply { writeBytes(bytes) }
                                                    }
                                                    "resize" -> {
                                                        val tw = widthPxInput.toIntOrNull() ?: 200
                                                        val th = heightPxInput.toIntOrNull() ?: 200
                                                        val bmp = BitmapLoader.load(context, selectedUri!!)
                                                        val resized = resizeExact(bmp, tw, th, fitMode)
                                                        val bytes = toJpeg(resized)
                                                        File(outDir, "resized_${tw}x${th}.jpg").apply { writeBytes(bytes) }
                                                    }
                                                    "convert" -> {
                                                        val bmp = BitmapLoader.load(context, selectedUri!!)
                                                        val bytes = if (selectedFmt == Fmt.PNG) toPng(bmp) else toJpeg(bmp)
                                                        val ext = if (selectedFmt == Fmt.PNG) "png" else "jpg"
                                                        File(outDir, "converted.$ext").apply { writeBytes(bytes) }
                                                    }
                                                    "img_to_pdf" -> {
                                                        val pdfBytes = imagesToPdf(context, listOf(selectedUri!!), maxKb)
                                                        File(outDir, "doc.pdf").apply { writeBytes(pdfBytes) }
                                                    }
                                                    "shrink_pdf" -> {
                                                        val pdfBytes = shrinkPdf(context, selectedUri!!, maxKb ?: (200 * KB))
                                                        File(outDir, "shrunk.pdf").apply { writeBytes(pdfBytes) }
                                                    }
                                                    "pdf_to_img" -> {
                                                        val bmp = pdfFirstPageToBitmap(context, selectedUri!!)
                                                        val bytes = toJpeg(bmp)
                                                        File(outDir, "page_1.jpg").apply { writeBytes(bytes) }
                                                    }
                                                    "crop_rotate" -> {
                                                        val bmp = BitmapLoader.load(context, selectedUri!!)
                                                        val rotated = rotate(bmp, 90f)
                                                        val bytes = toJpeg(rotated)
                                                        File(outDir, "rotated.jpg").apply { writeBytes(bytes) }
                                                    }
                                                    else -> null
                                                }
                                            }
                                            resultFile = file
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Processing failed"
                                        } finally {
                                            processing = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !processing && selectedUri != null
                            ) {
                                if (processing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.processing))
                                } else {
                                    Text(stringResource(R.string.convert_button))
                                }
                            }

                            // Result Section
                            AnimatedVisibility(visible = resultFile != null) {
                                resultFile?.let { res ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    stringResource(R.string.result_success),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF16A34A)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("${stringResource(R.string.new_size)}: ${res.length() / 1024} KB")

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val savedUri = MediaSaver.saveToDexFormAlbum(context, res)
                                                            if (savedUri != null) {
                                                                app.container.mediaRepository.refresh()
                                                                Toast.makeText(context, context.getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Saved to DexForm album", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.save_to_gallery))
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val resUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", res)
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "*/*"
                                                            putExtra(Intent.EXTRA_STREAM, resUri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Share Result"))
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Outlined.Share, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.share))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Error section
                            AnimatedVisibility(visible = errorMessage != null) {
                                errorMessage?.let { msg ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = msg,
                                            modifier = Modifier.padding(16.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tool List Grid / Items
            item {
                Text(
                    text = stringResource(R.string.tools_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(toolsList) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedToolId = tool.id
                            selectedUri = null
                            resultFile = null
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedToolId == tool.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(tool.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(tool.descRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
