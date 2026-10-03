package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GalleryViewModel
import java.text.DecimalFormat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.widget.Toast

@Composable
fun SyncScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val allPhotos by viewModel.allPhotos.collectAsState()
    val syncingState by viewModel.syncingState.collectAsState()

    val isGDriveEnabled by viewModel.isGDriveEnabled.collectAsState()
    val gdriveConnectedEmail by viewModel.gdriveConnectedEmail.collectAsState()
    val gdriveSelectedFolder by viewModel.gdriveSelectedFolder.collectAsState()

    val isSyncScheduled by viewModel.isSyncScheduled.collectAsState()
    val syncScheduleInterval by viewModel.syncScheduleInterval.collectAsState()
    val syncScheduleTime by viewModel.syncScheduleTime.collectAsState()

    val context = LocalContext.current
    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    var showLoginDialog by remember { mutableStateOf(false) }
    var loginErrorDetail by remember { mutableStateOf("") }
    var showFolderDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account?.email ?: ""
                if (email.isNotEmpty()) {
                    viewModel.loginToGDrive(email)
                    Toast.makeText(context, "Connected as $email", Toast.LENGTH_SHORT).show()
                    showLoginDialog = false
                    loginErrorDetail = ""
                } else {
                    loginErrorDetail = "The authentication succeeded, but we could not read a valid Google account email. Please make sure Google Profile scope is approved."
                    showLoginDialog = true
                }
            } catch (e: ApiException) {
                val statusText = when (e.statusCode) {
                    7 -> "Network Error: No active internet connection found or Google servers are unreachable."
                    10 -> "Developer Configuration Mismatch (Code 10):\nThis app's package name and SHA-1 certificate must be registered in your Google Cloud Platform (GCP) Credentials Console under an active Android client ID."
                    12500 -> "Sign-In Failed (Code 12500):\nGoogle Play Services failed to complete the authentication handshake. This usually means the app is not registered in the GCP developer console, or Google Drive API has not been enabled for your project."
                    12501 -> "Sign-In Cancelled (Code 12501):\nThe account chooser dialog was explicitly cancelled or closed by the user."
                    12502 -> "Sign-In in Progress: An existing authentication process is already running."
                    else -> "Play Services Error (Code ${e.statusCode}): ${e.localizedMessage ?: "Unknown authentication handshake failure."}"
                }
                loginErrorDetail = statusText
                showLoginDialog = true
            }
        } else {
            loginErrorDetail = "The Google account chooser was cancelled or closed (Result code: ${result.resultCode}).\n\nNo authentication data was returned."
            showLoginDialog = true
        }
    }

    val authRecoveryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.clearRecoverableIntent()
            Toast.makeText(context, "Authorization approved! Ready to sync.", Toast.LENGTH_SHORT).show()
            viewModel.runCloudSyncAction()
        } else {
            viewModel.clearRecoverableIntent()
            Toast.makeText(context, "Authorization rejected. Sync failed.", Toast.LENGTH_LONG).show()
        }
    }

    val pendingIntent by viewModel.recoverableAuthIntent.collectAsState()
    LaunchedEffect(pendingIntent) {
        pendingIntent?.let { intent ->
            authRecoveryLauncher.launch(intent)
        }
    }

    val customLabels by viewModel.customLabels.collectAsState()
    val syncSelectedAlbumsOnly by viewModel.syncSelectedAlbumsOnly.collectAsState()
    val selectedAlbumsToSync by viewModel.selectedAlbumsToSync.collectAsState()

    val filteredPhotos = remember(allPhotos, syncSelectedAlbumsOnly, selectedAlbumsToSync) {
        if (syncSelectedAlbumsOnly) {
            val albumsLower = selectedAlbumsToSync.map { it.lowercase() }.toSet()
            allPhotos.filter { photo ->
                val tags = photo.tags.split(",").map { it.trim().lowercase() }
                tags.any { tag ->
                    tag.startsWith("group:") && albumsLower.contains(tag.substringAfter("group:"))
                }
            }
        } else {
            allPhotos
        }
    }

    val syncedCount = remember(filteredPhotos) { filteredPhotos.count { it.isSynced } }
    val totalCount = remember(filteredPhotos) { filteredPhotos.size }

    val syncPercentage = remember(syncedCount, totalCount) {
        if (totalCount == 0) 100f else (syncedCount.toFloat() / totalCount.toFloat()) * 100f
    }

    val df = remember { DecimalFormat("#0.0") }
    val scrollState = rememberScrollState()

    // Smooth pulsing glow infinite spacer during syncs
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Scroll diagnostic console automatically as logs append
    LaunchedEffect(syncingState.diagnosticLog.size) {
        if (syncingState.isSyncing) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    var aiBaseUrl by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }
    var aiModelId by remember { mutableStateOf("") }
    var aiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiStatus by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cfg = viewModel.getAiProviderConfig()
        aiBaseUrl = cfg.baseUrl
        aiApiKey = cfg.apiKey
        aiModelId = cfg.modelId
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("ai_provider_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI VISION PROVIDER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("Use any OpenAI-compatible vision endpoint. Gemini is optional, not required.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = aiBaseUrl, onValueChange = { aiBaseUrl = it }, label = { Text("Base URL") }, placeholder = { Text("https://api.example.com/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_base_url"))
            OutlinedTextField(value = aiApiKey, onValueChange = { aiApiKey = it }, label = { Text("API key") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_api_key"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    if (aiBaseUrl.isBlank()) aiStatus = "Enter a base URL first." else {
                        aiLoading = true
                        aiStatus = "Discovering vision-capable models..."
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            viewModel.discoverAiModels(aiBaseUrl, aiApiKey).fold(
                                onSuccess = { found ->
                                    aiModels = found.map { it.id }
                                    aiModelId = aiModels.firstOrNull() ?: aiModelId
                                    aiStatus = "Found ${aiModels.size} vision-capable models."
                                },
                                onFailure = { error -> aiStatus = error.message ?: "Model discovery failed." }
                            )
                            aiLoading = false
                        }
                    }
                }, enabled = !aiLoading, modifier = Modifier.weight(1f)) { Text(if (aiLoading) "Discovering..." else "Fetch Models") }
                OutlinedButton(onClick = { viewModel.saveAiProviderConfig(aiBaseUrl, aiApiKey, aiModelId); aiStatus = "AI provider settings saved." }, enabled = aiModelId.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Save") }
            }
            OutlinedTextField(value = aiModelId, onValueChange = { aiModelId = it }, label = { Text("Vision model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai_model_id"))
            if (aiStatus.isNotBlank()) Text(aiStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title Header Block
            Text(
                text = "Cloud Synchronization Console",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Dynamic Sync Mode Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_storage_destination_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BACKUP TYPE & DESTINATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Option A: Local Media Device Vault Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setGDriveEnabled(false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isGDriveEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = if (!isGDriveEnabled) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isGDriveEnabled,
                                onClick = { viewModel.setGDriveEnabled(false) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Local Media Device ONLY", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Local scanning, local indexing, no cloud connectivity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option B: Google Drive Integration Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setGDriveEnabled(true) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGDriveEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = if (isGDriveEnabled) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isGDriveEnabled,
                                onClick = { viewModel.setGDriveEnabled(true) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Google Drive Cloud Space", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Back up and sync assets securely to your Google Drive", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Google Drive Interactive Detail Flow (if enabled)
                    if (isGDriveEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        if (gdriveConnectedEmail == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("⚠️ Account Not Connected", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Authentication is required to target cloud directories.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        try {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        } catch (e: Exception) {
                                            loginErrorDetail = e.localizedMessage ?: "Failed to launch Google Sign-In"
                                            showLoginDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Connect GDrive", fontSize = 11.sp)
                                }
                            }
                        } else {
                            // Logged In status card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Drive Active Connected", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(gdriveConnectedEmail!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        TextButton(onClick = {
                                             viewModel.logoutFromGDrive()
                                             googleSignInClient.signOut()
                                         }) {
                                            Text("Sign Out", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Folder Mapping Configuration
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Backup Destination Folder", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                            Text(
                                                text = if (gdriveSelectedFolder.isNullOrEmpty()) "⚠️ Folder unselected"
                                                       else "My Drive → /$gdriveSelectedFolder",
                                                color = if (gdriveSelectedFolder.isNullOrEmpty()) MaterialTheme.colorScheme.error
                                                        else MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Button(
                                            onClick = { showFolderDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (gdriveSelectedFolder.isNullOrEmpty()) "Choose" else "Change", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Show other cloud options ONLY when Google Drive sync is selected and logged in
            if (isGDriveEnabled && gdriveConnectedEmail != null) {
                // 1. Interactive Album Scope Selection layout
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_scope_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYNCHRONIZATION SCOPE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selection Row 1: All Photos
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSyncSelectedAlbumsOnly(false) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !syncSelectedAlbumsOnly,
                                onClick = { viewModel.setSyncSelectedAlbumsOnly(false) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Sync All Media Assets", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Backup all offline items detected across storage.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Selection Row 2: Selected Albums Only
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSyncSelectedAlbumsOnly(true) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = syncSelectedAlbumsOnly,
                                onClick = { viewModel.setSyncSelectedAlbumsOnly(true) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Sync Selected Albums Only", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Choose specific categories to restrict backup scope.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (syncSelectedAlbumsOnly) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "Choose custom albums to backup:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            if (customLabels.isEmpty()) {
                                Text(
                                    "No custom labels/albums found. Assign photos on the Albums screen to begin.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                customLabels.forEach { label ->
                                    val isChecked = selectedAlbumsToSync.contains(label)
                                    val countInLabel = remember(allPhotos) {
                                        allPhotos.count { photo ->
                                            val tags = photo.tags.split(",").map { it.trim().lowercase() }
                                            tags.contains("group:${label.lowercase()}")
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleAlbumToSync(label) }
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { viewModel.toggleAlbumToSync(label) }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "$countInLabel files",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Media summary counters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered Sync Volume:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${syncedCount}/${totalCount} synced",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${df.format(syncPercentage)}%)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Schedule Section
                        Text(
                            text = "SCHEDULED SYNCHRONIZATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automate Backups",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isSyncScheduled) "Sync scheduled: $syncScheduleInterval at $syncScheduleTime" else "Turn on to automatically sync your gallery",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isSyncScheduled,
                                onCheckedChange = { viewModel.updateSyncSchedule(it) },
                                modifier = Modifier.testTag("schedule_sync_switch")
                            )
                        }

                        if (isSyncScheduled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Interval radio choices row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val intervals = listOf("daily", "weekly", "monthly")
                                intervals.forEach { interval ->
                                    val isSelected = syncScheduleInterval == interval
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.updateSyncScheduleInterval(interval) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = interval.replaceFirstChar { it.uppercase() },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom time field selection
                            var isEditingTime by remember { mutableStateOf(false) }
                            var editTimeText by remember { mutableStateOf(syncScheduleTime) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Scheduled Execution Time:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isEditingTime) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        BasicTextField(
                                            value = editTimeText,
                                            onValueChange = { editTimeText = it },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier
                                                .width(60.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                .testTag("schedule_time_input")
                                        )
                                        IconButton(
                                            onClick = {
                                                if (Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(editTimeText)) {
                                                    viewModel.updateSyncScheduleTime(editTimeText)
                                                    isEditingTime = false
                                                } else {
                                                    Toast.makeText(context, "Please use valid HH:mm format", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Save Time",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clickable { 
                                            editTimeText = syncScheduleTime
                                            isEditingTime = true 
                                        }
                                    ) {
                                        Text(
                                            text = syncScheduleTime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = TextDecoration.Underline
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Time",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Active Sync speed stats panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_active_panel"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TRANSFER SPEED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = syncingState.bandwidthSpeed,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (syncingState.isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CONNECTION STATUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (syncingState.isSyncing) MaterialTheme.colorScheme.primary.copy(alpha = alphaPulse)
                                            else MaterialTheme.colorScheme.primary
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (syncingState.isSyncing) "Synchronizing" else "Ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 3. Status bar progress message text during syncing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = syncingState.syncMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        if (syncingState.isSyncing && syncingState.totalBytesToTransfer > 0L) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val progressFraction = syncingState.currentTransferredBytes.toFloat() / syncingState.totalBytesToTransfer.toFloat()
                            
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${df.format(progressFraction * 100f)}% (${syncingState.currentTransferredBytes / 1000000} MB of ${syncingState.totalBytesToTransfer / 1000000} MB uploaded)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. BIG SYNC TACTILE BUTTON ACTION trigger
                Button(
                    onClick = {
                        if (syncingState.isSyncing) {
                            viewModel.cancelCloudSyncAction()
                        } else {
                            viewModel.runCloudSyncAction()
                        }
                    },
                    modifier = Modifier
                        .fillOfScaleWidth(0.9f)
                        .height(56.dp)
                        .testTag("force_sync_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (syncingState.isSyncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (syncingState.isSyncing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (syncingState.isSyncing) Icons.Default.Close else Icons.Default.CloudSync,
                            contentDescription = if (syncingState.isSyncing) "Cancel Sync Now" else "Sync Now Trigger",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (syncingState.isSyncing) "CANCEL SYNCHRONIZATION" else "INITIATE CLOUD SYNC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        val appSha1 = remember(context) { getActiveAppSha1(context) }
        val appPackage = context.packageName
        var showDiagnosticsDetails by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { 
                showLoginDialog = false 
                loginErrorDetail = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google Account", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Smart Gallery Backup uses the official secure Google Identity protocol to perform genuine backups of your photographs straight to your private Google Drive vault.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            try {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                loginErrorDetail = e.localizedMessage ?: "Failed to launch intent"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Google Account Picker", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (loginErrorDetail.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Google Play Exception Status:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = loginErrorDetail,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showDiagnosticsDetails = !showDiagnosticsDetails }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Developer Diagnostics",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (showDiagnosticsDetails) "Collapse ▴" else "Expand ▾",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showDiagnosticsDetails) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Why am I getting sign-in errors?",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Text(
                                    text = "Google Play Services mandates that every application's package name and compiling signature SHA-1 fingerprint be registered inside your Google Cloud Platform (GCP) or Firebase Developer console under an active Android Client ID. For defense-in-depth, Play Services automatically aborts/cancels the native popup if there is a mismatch on the backend.",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "Active App Credentials to Register in console:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Package Name:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = appPackage,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Certificate SHA-1 Signature:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = appSha1,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "Registration Steps:\n1. Open your Google Cloud / Firebase console.\n2. Proceed to APIs & Services > Credentials > Create Android Client ID.\n3. Input the Package Name and SHA-1 values shown exactly above.\n4. Enable 'Google Drive API' for this GCP project.\n\nNote: This diagnostic panel is a temporary utility to assist with environment configuration and is collapsed by default for general users.",
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showLoginDialog = false 
                    loginErrorDetail = ""
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        var folderList by remember { mutableStateOf(listOf("Smart Gallery Backups", "My Camera Archive", "Drive Photos Backup", "Personal Space")) }

        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Backup Folder", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose mapped target directory on Google Drive for files storage.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        folderList.forEach { folder ->
                            val isSelected = gdriveSelectedFolder == folder
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectGDriveFolder(folder) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Or Create New Google Drive Folder:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("folder name...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    val cleaned = newFolderName.trim()
                                    folderList = folderList + cleaned
                                    viewModel.selectGDriveFolder(cleaned)
                                    newFolderName = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

// Custom responsive padding extensions to avoid hardcoded screen widths
fun Modifier.fillOfScaleWidth(fraction: Float): Modifier = this.then(
    Modifier
        .fillMaxWidth(fraction)
        .widthIn(max = 480.dp)
)

fun getActiveAppSha1(context: android.content.Context): String {
    try {
        val pm = context.packageManager
        val packageName = context.packageName
        
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures
        }
        
        if (signatures != null && signatures.isNotEmpty()) {
            val cert = signatures[0].toByteArray()
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val publicKey = md.digest(cert)
            val hexString = StringBuilder()
            for (i in publicKey.indices) {
                val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                if (appendString.length == 1) hexString.append("0")
                hexString.append(appendString.uppercase())
                if (i < publicKey.size - 1) hexString.append(":")
            }
            return hexString.toString()
        }
    } catch (e: Exception) {
        return "ERROR: ${e.localizedMessage ?: "Unknown certificate retrieval error"}"
    }
    return ""
}
