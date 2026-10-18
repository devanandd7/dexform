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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dexform.app.App
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onOpenAiChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val profileDao = app.container.userProfileDao
    val scope = rememberCoroutineScope()

    val profilesList by profileDao.getAllProfiles().collectAsState(initial = emptyList())
    val selectedProfile = profilesList.firstOrNull { it.isSelected } ?: profilesList.firstOrNull()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileImageUri by remember { mutableStateOf<Uri?>(null) }

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
