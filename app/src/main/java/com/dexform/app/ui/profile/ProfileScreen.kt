package com.dexform.app.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.data.db.DocRecordEntity
import com.dexform.app.data.db.FieldDefEntity
import com.dexform.app.data.db.FieldType
import com.dexform.app.data.db.FieldValueDao
import com.dexform.app.data.db.FieldValueEntity
import com.dexform.app.data.db.Origin
import com.dexform.app.data.db.Sensitivity
import com.dexform.app.data.db.Tier
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onOpenAiChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val profileDao = app.container.userProfileDao
    val fieldDefDao = app.container.fieldDefDao
    val fieldValueDao = app.container.fieldValueDao
    val docRecordDao = app.container.docRecordDao
    val scope = rememberCoroutineScope()

    val profilesList by profileDao.getAllProfiles().collectAsState(initial = emptyList())
    val selectedProfile = profilesList.firstOrNull { it.isSelected } ?: profilesList.firstOrNull()

    val allDefs by fieldDefDao.getAllDefs().collectAsState(initial = emptyList())
    val bioValues by if (selectedProfile != null) {
        fieldValueDao.getBioValues(selectedProfile.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<FieldValueEntity>()) }
    }
    val docRecords by if (selectedProfile != null) {
        docRecordDao.getDocsForProfile(selectedProfile.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<DocRecordEntity>()) }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddCustomBioDialog by remember { mutableStateOf(false) }
    var customBioLabel by remember { mutableStateOf("") }
    var customBioValue by remember { mutableStateOf("") }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileImageUri by remember { mutableStateOf<Uri?>(null) }

    // Inline edit states
    var editingFieldValue by remember { mutableStateOf<FieldValueEntity?>(null) }
    var editFieldValueText by remember { mutableStateOf("") }
    var editingDocRecord by remember { mutableStateOf<DocRecordEntity?>(null) }
    var editDocTitleText by remember { mutableStateOf("") }
    var addingSubBioForDoc by remember { mutableStateOf<DocRecordEntity?>(null) }
    var newSubBioLabel by remember { mutableStateOf("") }
    var newSubBioValue by remember { mutableStateOf("") }

    // Auto-select single profile
    LaunchedEffect(profilesList) {
        if (profilesList.size == 1 && !profilesList.first().isSelected) {
            profileDao.setSelectedProfile(profilesList.first().id)
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) newProfileImageUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profiles & Form Bio-Data", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        newProfileName = ""
                        newProfileImageUri = null
                        showCreateDialog = true
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAiChat,
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI Scanner")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Doc Extractor", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Profiles List Header / Chips
            if (profilesList.isNotEmpty()) {
                Text("Select Active Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(profilesList) { prof ->
                        val isSel = prof.id == selectedProfile?.id
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    profileDao.clearSelections()
                                    profileDao.setSelectedProfile(prof.id)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (prof.imageUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(prof.imageUri).crossfade(true).build(),
                                        contentDescription = prof.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(24.dp).clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(prof.name, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (selectedProfile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No User Profile Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Create a profile to save form bio-data & documents", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            newProfileName = ""
                            newProfileImageUri = null
                            showCreateDialog = true
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Profile")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Profile Header Card
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selectedProfile.imageUri != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(selectedProfile.imageUri).crossfade(true).build(),
                                                contentDescription = selectedProfile.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(selectedProfile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("Active Profile for Form Autofill", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    IconButton(onClick = { showEditDialog = true }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile")
                                    }

                                    IconButton(onClick = {
                                        scope.launch {
                                            profileDao.deleteProfile(selectedProfile)
                                        }
                                    }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete Profile", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Personal Bio Data Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Personal Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))

                                InfoRow("Father's Name", selectedProfile.fatherName)
                                InfoRow("Mother's Name", selectedProfile.motherName)
                                InfoRow("DOB", selectedProfile.dob)
                                InfoRow("Gender", selectedProfile.gender)
                                InfoRow("ID Number (Aadhaar/PAN)", selectedProfile.idNumber)
                            }
                        }
                    }

                    // 10th Educational Details Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("10th / Matriculation Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                InfoRow("Roll No", selectedProfile.tenthRollNo)
                                InfoRow("Registration No", selectedProfile.tenthRegNo)
                                InfoRow("Passing Year", selectedProfile.tenthPassingYear)
                                InfoRow("Board / Council", selectedProfile.tenthBoard)
                                InfoRow("Marks / %", selectedProfile.tenthMarks)
                                InfoRow("Division", selectedProfile.tenthDivision)
                            }
                        }
                    }

                    // 12th Educational Details Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("12th / Intermediate Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                 InfoRow("Roll No", selectedProfile.twelfthRollNo)
                                InfoRow("Registration No", selectedProfile.twelfthRegNo)
                                InfoRow("Passing Year", selectedProfile.twelfthPassingYear)
                                InfoRow("Board / Council", selectedProfile.twelfthBoard)
                                InfoRow("Marks / %", selectedProfile.twelfthMarks)
                                InfoRow("Division", selectedProfile.twelfthDivision)
                            }
                        }
                    }

                    // Additional Bio & Career Details Card (Dynamic AI & User Fields)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Additional Bio & Career Details",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            customBioLabel = ""
                                            customBioValue = ""
                                            showAddCustomBioDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "Add Custom Field",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (bioValues.isEmpty()) {
                                    Text(
                                        text = "No custom bio fields yet. Fields like Experience Certificate, Company Name, Industry, Designation, or License added via AI Scanner will appear here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    bioValues.forEach { valEntity ->
                                        val def = allDefs.firstOrNull { it.key == valEntity.fieldKey }
                                        val label = def?.label ?: valEntity.fieldKey.substringAfterLast('.')
                                            .replace('_', ' ')
                                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        label,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    if (def?.origin == Origin.AI) {
                                                        Surface(
                                                            shape = RoundedCornerShape(3.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                "AI",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    valEntity.valueEnc,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        editingFieldValue = valEntity
                                                        editFieldValueText = valEntity.valueEnc
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Edit,
                                                        contentDescription = "Edit Value",
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            fieldValueDao.deleteValue(valEntity)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Delete,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
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

                    // Saved Documents & Certificates Section
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Saved Documents & Certificates (${docRecords.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (docRecords.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "No documents saved for this profile.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Scan certificates, ID cards, and marksheets using AI Doc Extractor to save sub-bio details here.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    docRecords.forEach { doc ->
                                        DocRecordCard(
                                            doc = doc,
                                            fieldValueDao = fieldValueDao,
                                            allDefs = allDefs,
                                            onEditDoc = {
                                                editingDocRecord = doc
                                                editDocTitleText = doc.title
                                            },
                                            onEditValue = { valEntity ->
                                                editingFieldValue = valEntity
                                                editFieldValueText = valEntity.valueEnc
                                            },
                                            onDeleteValue = { valEntity ->
                                                scope.launch { fieldValueDao.deleteValue(valEntity) }
                                            },
                                            onAddField = {
                                                addingSubBioForDoc = doc
                                                newSubBioLabel = ""
                                                newSubBioValue = ""
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    docRecordDao.deleteDoc(doc)
                                                }
                                            }
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

    // Create Profile Dialog
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Create User Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Image picker avatar
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { avatarPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (newProfileImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(newProfileImageUri).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = "Pick Image", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCreateDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (newProfileName.isNotBlank()) {
                                    scope.launch {
                                        profileDao.clearSelections()
                                        profileDao.insertProfile(
                                            UserProfileEntity(
                                                name = newProfileName.trim(),
                                                imageUri = newProfileImageUri?.toString(),
                                                isSelected = true
                                            )
                                        )
                                        showCreateDialog = false
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter a profile name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Create") }
                    }
                }
            }
        }
    }

    // Edit Profile Modal Dialog
    if (showEditDialog && selectedProfile != null) {
        var editName by remember { mutableStateOf(selectedProfile.name) }
        var editFather by remember { mutableStateOf(selectedProfile.fatherName) }
        var editMother by remember { mutableStateOf(selectedProfile.motherName) }
        var editDob by remember { mutableStateOf(selectedProfile.dob) }
        var editIdNum by remember { mutableStateOf(selectedProfile.idNumber) }
        var edit10Roll by remember { mutableStateOf(selectedProfile.tenthRollNo) }
        var edit10Reg by remember { mutableStateOf(selectedProfile.tenthRegNo) }
        var edit10Year by remember { mutableStateOf(selectedProfile.tenthPassingYear) }
        var edit12Roll by remember { mutableStateOf(selectedProfile.twelfthRollNo) }
        var edit12Reg by remember { mutableStateOf(selectedProfile.twelfthRegNo) }
        var edit12Year by remember { mutableStateOf(selectedProfile.twelfthPassingYear) }

        Dialog(onDismissRequest = { showEditDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Edit Profile & Bio-Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editFather, onValueChange = { editFather = it }, label = { Text("Father's Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editMother, onValueChange = { editMother = it }, label = { Text("Mother's Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editDob, onValueChange = { editDob = it }, label = { Text("DOB") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editIdNum, onValueChange = { editIdNum = it }, label = { Text("ID Number (Aadhaar/PAN)") }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("10th Details", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = edit10Roll, onValueChange = { edit10Roll = it }, label = { Text("10th Roll No") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = edit10Reg, onValueChange = { edit10Reg = it }, label = { Text("10th Reg No") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = edit10Year, onValueChange = { edit10Year = it }, label = { Text("10th Passing Year") }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("12th Details", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = edit12Roll, onValueChange = { edit12Roll = it }, label = { Text("12th Roll No") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = edit12Reg, onValueChange = { edit12Reg = it }, label = { Text("12th Reg No") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = edit12Year, onValueChange = { edit12Year = it }, label = { Text("12th Passing Year") }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEditDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                scope.launch {
                                    profileDao.updateProfile(
                                        selectedProfile.copy(
                                            name = editName.trim(),
                                            fatherName = editFather.trim(),
                                            motherName = editMother.trim(),
                                            dob = editDob.trim(),
                                            idNumber = editIdNum.trim(),
                                            tenthRollNo = edit10Roll.trim(),
                                            tenthRegNo = edit10Reg.trim(),
                                            tenthPassingYear = edit10Year.trim(),
                                            twelfthRollNo = edit12Roll.trim(),
                                            twelfthRegNo = edit12Reg.trim(),
                                            twelfthPassingYear = edit12Year.trim()
                                        )
                                    )
                                    showEditDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save Changes") }
                    }
                }
            }
        }
    }

    // Add Custom Bio Field Dialog
    if (showAddCustomBioDialog && selectedProfile != null) {
        Dialog(onDismissRequest = { showAddCustomBioDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Add Custom Bio Field", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add custom personal or career details (e.g. Company Name, Industry, Designation, Driving License, Blood Group).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = customBioLabel,
                        onValueChange = { customBioLabel = it },
                        label = { Text("Field Label (e.g. Company Name)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customBioValue,
                        onValueChange = { customBioValue = it },
                        label = { Text("Field Value (e.g. Google India)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAddCustomBioDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (customBioLabel.isNotBlank() && customBioValue.isNotBlank()) {
                                    scope.launch {
                                        val slugKey = "bio." + customBioLabel.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
                                        val def = FieldDefEntity(
                                            key = slugKey,
                                            label = customBioLabel.trim(),
                                            group = "Personal/Career",
                                            tier = Tier.BIO,
                                            type = FieldType.TEXT,
                                            sensitivity = Sensitivity.NORMAL,
                                            synonymsJson = "[\"${customBioLabel.trim().lowercase()}\"]",
                                            origin = Origin.USER,
                                            approved = true
                                        )
                                        fieldDefDao.insertDef(def)

                                        fieldValueDao.insertValue(
                                            FieldValueEntity(
                                                profileId = selectedProfile.id,
                                                fieldKey = def.key,
                                                docId = null,
                                                valueEnc = customBioValue.trim(),
                                                confidence = "HIGH",
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                        showAddCustomBioDialog = false
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter both label and value", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Field")
                        }
                    }
                }
            }
        }
    }

    // Inline Edit Field Value Dialog (Bio or Sub-Bio)
    editingFieldValue?.let { currentField ->
        val def = allDefs.firstOrNull { it.key == currentField.fieldKey }
        val label = def?.label ?: currentField.fieldKey.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

        Dialog(onDismissRequest = { editingFieldValue = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Edit Field Value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Field: $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editFieldValueText,
                        onValueChange = { editFieldValueText = it },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingFieldValue = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    fieldValueDao.updateValue(
                                        currentField.copy(
                                            valueEnc = editFieldValueText.trim(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    editingFieldValue = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Update")
                        }
                    }
                }
            }
        }
    }

    // Edit Document Title Dialog
    editingDocRecord?.let { currentDoc ->
        Dialog(onDismissRequest = { editingDocRecord = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Edit Document Title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editDocTitleText,
                        onValueChange = { editDocTitleText = it },
                        label = { Text("Document Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingDocRecord = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (editDocTitleText.isNotBlank()) {
                                    scope.launch {
                                        docRecordDao.updateDoc(currentDoc.copy(title = editDocTitleText.trim()))
                                        editingDocRecord = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Add Sub-Bio Field to Document Dialog
    addingSubBioForDoc?.let { targetDoc ->
        Dialog(onDismissRequest = { addingSubBioForDoc = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Add Document Detail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Adding to: ${targetDoc.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newSubBioLabel,
                        onValueChange = { newSubBioLabel = it },
                        label = { Text("Field Name (e.g. Center Code, Serial No)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newSubBioValue,
                        onValueChange = { newSubBioValue = it },
                        label = { Text("Field Value") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { addingSubBioForDoc = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (newSubBioLabel.isNotBlank() && newSubBioValue.isNotBlank()) {
                                    scope.launch {
                                        val slugKey = "${targetDoc.docType.lowercase().replace(Regex("""[^a-z0-9]"""), "_")}." +
                                                newSubBioLabel.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
                                        val def = FieldDefEntity(
                                            key = slugKey,
                                            label = newSubBioLabel.trim(),
                                            group = targetDoc.docType,
                                            tier = Tier.SUB,
                                            type = FieldType.TEXT,
                                            sensitivity = Sensitivity.NORMAL,
                                            synonymsJson = "[\"${newSubBioLabel.trim().lowercase()}\"]",
                                            origin = Origin.USER,
                                            approved = true
                                        )
                                        fieldDefDao.insertDef(def)

                                        fieldValueDao.insertValue(
                                            FieldValueEntity(
                                                profileId = targetDoc.profileId,
                                                fieldKey = def.key,
                                                docId = targetDoc.id,
                                                valueEnc = newSubBioValue.trim(),
                                                confidence = "HIGH",
                                                sourceDocId = targetDoc.id,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                        addingSubBioForDoc = null
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter both label and value", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Field")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(if (value.isBlank()) "Not Set" else value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DocRecordCard(
    doc: DocRecordEntity,
    fieldValueDao: FieldValueDao,
    allDefs: List<FieldDefEntity>,
    onEditDoc: () -> Unit,
    onEditValue: (FieldValueEntity) -> Unit,
    onDeleteValue: (FieldValueEntity) -> Unit,
    onAddField: () -> Unit,
    onDelete: () -> Unit
) {
    val docValues by fieldValueDao.getDocValues(doc.profileId, doc.id).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail or Icon
                if (doc.savedImageUri != null && File(doc.savedImageUri).exists()) {
                    AsyncImage(
                        model = File(doc.savedImageUri),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(onClick = onEditDoc, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit Title",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "Saved on ${dateFormat.format(Date(doc.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Document",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(6.dp))

            // Sub-Bio Section Header with + Add Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sub-Bio Details (${docValues.size})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onAddField,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Detail", fontSize = 11.sp)
                }
            }

            // Sub-Bio Fields
            if (docValues.isNotEmpty()) {
                docValues.forEach { valEntity ->
                    val def = allDefs.firstOrNull { it.key == valEntity.fieldKey }
                    val label = def?.label ?: valEntity.fieldKey.substringAfterLast('.')
                        .replace('_', ' ')
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = valEntity.valueEnc,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { onEditValue(valEntity) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Edit Value",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            IconButton(onClick = { onDeleteValue(valEntity) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete Value",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No sub-bio fields recorded yet. Tap '+ Add Detail' to add certificate or roll details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
