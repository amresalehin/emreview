package com.example

import android.Manifest
import android.os.Build
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.Photo
import com.example.data.PhotoRepository
import kotlinx.coroutines.flow.collect
import com.example.ui.GalleryViewModel
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.LabelsScreen
import com.example.ui.screens.PhotoDetailDialog
import com.example.ui.screens.SecureFolderScreen
import com.example.ui.screens.SyncScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrashScreen
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.ui.theme.MyApplicationTheme
import android.widget.Toast
import android.content.Context
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    // Lazy initialization of Database and Repository as a clean singleton-like pattern
    private val database by lazy {
        AppDatabase.build(applicationContext)
    }

    private val repository by lazy {
        PhotoRepository(database.photoDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable video thumbnail frame decoding globally for all AsyncImages
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()
        )

        setContent {
            MyApplicationTheme {
                // Factory provider to inject application and repository into AndroidViewModel
                val factory = remember {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return GalleryViewModel(application, repository) as T
                        }
                    }
                }

                val viewModel: GalleryViewModel = viewModel(factory = factory)

                // Permission launcher to scan device local media files dynamically
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val imagesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
                    } else false
                    val videosGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
                    } else false
                    val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true

                    if (imagesGranted || videosGranted || storageGranted) {
                        viewModel.scanLocalMedia()
                    }
                }

                var currentTab by remember { mutableStateOf(GalleryTab.BROWSE) }
                val context = LocalContext.current
                val sharedPrefs = remember(context) { context.getSharedPreferences("aura_gallery_prefs", Context.MODE_PRIVATE) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var activeDetailPhoto by remember { mutableStateOf<Photo?>(null) }

                // Launch permissions request and start scan automatically on startup
                LaunchedEffect(Unit) {
                    val permissionsNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                        )
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(permissionsNeeded)

                    // Optional Check for latest version on startup (By-passed entirely in DEBUG builds to simplify USB debug flows!)
                    val hasDismissed = sharedPrefs.getBoolean("dismissed_upgrade_v110", false)
                    if (!BuildConfig.DEBUG && !hasDismissed) {
                        kotlinx.coroutines.delay(2200)
                        showUpdateDialog = true
                    }
                }

                // Auto-focus and view newly saved copies immediately
                LaunchedEffect(viewModel) {
                    viewModel.newlyCreatedPhotoId.collect { newId ->
                        var foundPhoto = viewModel.allPhotos.value.find { it.id == newId }
                        var attempts = 0
                        while (foundPhoto == null && attempts < 10) {
                            kotlinx.coroutines.delay(100)
                            foundPhoto = viewModel.allPhotos.value.find { it.id == newId }
                            attempts++
                        }
                        if (foundPhoto != null) {
                            activeDetailPhoto = foundPhoto
                        }
                    }
                }

                val isAnalyzing by viewModel.isAnalyzing.collectAsState()
                val isGDriveEnabled by viewModel.isGDriveEnabled.collectAsState()
                val gdriveConnectedEmail by viewModel.gdriveConnectedEmail.collectAsState()
                val customLabels by viewModel.customLabels.collectAsState()

                // Real-time synchronization of active dialog selection (in case of updates)
                val allPhotos by viewModel.allPhotos.collectAsState()
                val publicPhotos by viewModel.publicPhotos.collectAsState()
                val lockedPhotos by viewModel.lockedPhotos.collectAsState()

                val updatedDetailPhoto = remember(activeDetailPhoto, allPhotos) {
                    allPhotos.find { it.id == activeDetailPhoto?.id } ?: activeDetailPhoto
                }

                val configuration = LocalConfiguration.current
                val isWideScreen = configuration.screenWidthDp >= 600

                // Root safe display enclosing
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                modifier = Modifier.testTag("compact_bottom_nav"),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == GalleryTab.BROWSE,
                                    onClick = { currentTab = GalleryTab.BROWSE },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home Gallery Stream") },
                                    label = { Text("Stream", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("nav_btn_browse")
                                )
                                NavigationBarItem(
                                    selected = currentTab == GalleryTab.LABELS,
                                    onClick = { currentTab = GalleryTab.LABELS },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = "Custom albums") },
                                    label = { Text("Albums", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("nav_btn_labels")
                                )
                                NavigationBarItem(
                                    selected = currentTab == GalleryTab.SETTINGS || currentTab == GalleryTab.SYNC,
                                    onClick = { currentTab = GalleryTab.SETTINGS },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("nav_btn_settings")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Display tablet side Navigation Rail if screen width is extensive
                        if (isWideScreen) {
                            NavigationRail(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .testTag("tablet_side_nav"),
                                containerColor = MaterialTheme.colorScheme.surface,
                                header = {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = "emreview",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp).padding(vertical = 12.dp)
                                    )
                                }
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                NavigationRailItem(
                                    selected = currentTab == GalleryTab.BROWSE,
                                    onClick = { currentTab = GalleryTab.BROWSE },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Stream") },
                                    label = { Text("Stream") },
                                    modifier = Modifier.testTag("nav_btn_browse_rail")
                                )
                                NavigationRailItem(
                                    selected = currentTab == GalleryTab.LABELS,
                                    onClick = { currentTab = GalleryTab.LABELS },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = "Albums") },
                                    label = { Text("Albums") },
                                    modifier = Modifier.testTag("nav_btn_labels_rail")
                                )
                                NavigationRailItem(
                                    selected = currentTab == GalleryTab.SETTINGS || currentTab == GalleryTab.SYNC,
                                    onClick = { currentTab = GalleryTab.SETTINGS },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    modifier = Modifier.testTag("nav_btn_settings_rail")
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        // Primary App routing container
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            when (currentTab) {
                                GalleryTab.BROWSE -> {
                                    GalleryScreen(
                                        viewModel = viewModel,
                                        onNavigateToDetail = { activeDetailPhoto = it },
                                        onNavigateToSecureFolder = { currentTab = GalleryTab.SECURE },
                                        onNavigateToTrash = { currentTab = GalleryTab.TRASH },
                                        onNavigateToSettings = { currentTab = GalleryTab.SETTINGS }
                                    )
                                }
                                GalleryTab.LABELS -> {
                                    LabelsScreen(
                                        viewModel = viewModel,
                                        onNavigateToBrowseWithFilter = { labelName ->
                                            viewModel.clearAllFilters()
                                            viewModel.selectCustomLabel(labelName)
                                            currentTab = GalleryTab.BROWSE
                                        }
                                    )
                                }
                                GalleryTab.SETTINGS, GalleryTab.SYNC -> {
                                    SettingsScreen(viewModel = viewModel)
                                }
                                GalleryTab.SECURE -> {
                                    SecureFolderScreen(
                                        viewModel = viewModel,
                                        onNavigateToDetail = { activeDetailPhoto = it },
                                        onBack = { currentTab = GalleryTab.BROWSE }
                                    )
                                }
                                GalleryTab.TRASH -> {
                                    TrashScreen(
                                        viewModel = viewModel,
                                        onBack = { currentTab = GalleryTab.BROWSE }
                                    )
                                }
                            }
                        }
                    }

                    // Floating high-fidelity photo inspector dialog
                    updatedDetailPhoto?.let { photo ->
                        val browsablePhotos = if (photo.isLocked) lockedPhotos else publicPhotos
                        val initialIndex = browsablePhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)

                        PhotoDetailDialog(
                            photos = browsablePhotos,
                            initialIndex = initialIndex,
                            isAnalyzing = { targetPhoto -> isAnalyzing == targetPhoto.id },
                            onDismiss = { activeDetailPhoto = null },
                            onToggleLock = { targetPhoto ->
                                viewModel.togglePhotoLock(targetPhoto)
                            },
                            onDelete = { targetPhoto ->
                                viewModel.deletePhoto(targetPhoto)
                                if (browsablePhotos.size <= 1) {
                                    activeDetailPhoto = null
                                }
                            },
                            onTriggerAI = { targetPhoto ->
                                viewModel.triggerAiTagging(targetPhoto)
                            },
                            onToggleFavorite = { targetPhoto ->
                                viewModel.togglePhotoFavorite(targetPhoto)
                            },
                            showCloudSyncStatus = isGDriveEnabled && !gdriveConnectedEmail.isNullOrBlank(),
                            availableAlbums = customLabels,
                            onMoveToAlbum = { targetPhoto, albumName ->
                                viewModel.bulkMovePhotosToLabel(listOf(targetPhoto), albumName)
                            },
                            onMoveToFolder = { targetPhoto, folderName ->
                                viewModel.movePhotoToLocation(targetPhoto.id, folderName)
                            },
                            onUpdatePhoto = { targetPhoto ->
                                viewModel.updatePhoto(targetPhoto)
                            },
                            onSavePhotoCopy = { targetPhoto ->
                                viewModel.savePhotoCopy(targetPhoto)
                            },
                            onPhotoChanged = { targetPhoto ->
                                activeDetailPhoto = targetPhoto
                            }
                        )
                    }

                    if (showUpdateDialog) {
                        AlertDialog(
                            onDismissRequest = { showUpdateDialog = false },
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upgrade Available", fontWeight = FontWeight.Bold)
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        text = "A new version of Aura Gallery (v1.1.0) is available. Upgrading brings exciting additions:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "• Dynamic Grid/List toggles\n• Move folders/files within local filesystem\n• Open-with context shortcuts\n• Core database performance tweaks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        sharedPrefs.edit().putBoolean("dismissed_upgrade_v110", true).apply()
                                        showUpdateDialog = false
                                        Toast.makeText(context, "Initiating system upgrade to v1.1.0...", Toast.LENGTH_LONG).show()
                                    }
                                ) {
                                    Text("Upgrade Now")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    sharedPrefs.edit().putBoolean("dismissed_upgrade_v110", true).apply()
                                    showUpdateDialog = false
                                }) {
                                    Text("Later")
                                }
                            },
                            modifier = Modifier.testTag("startup_update_dialog")
                        )
                    }
                }
            }
        }
    }
}

enum class GalleryTab { BROWSE, LABELS, SETTINGS, SECURE, TRASH, SYNC }
