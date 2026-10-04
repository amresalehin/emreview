package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.Job
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.example.ui.ViewMode
import com.example.ui.GallerySortOption
import android.widget.Toast
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ai.VisionModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.Photo
import com.example.ui.GalleryViewModel
import com.example.ui.SyncFilter
import com.example.ui.buildMediaImageRequest
import com.example.ui.isVideoAsset
import com.example.ui.VideoThumbnailPlaceholder
import com.example.ui.screens.GalleryFastScrollBar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDetail: (Photo) -> Unit,
    onNavigateToSecureFolder: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val isGDriveEnabled by viewModel.isGDriveEnabled.collectAsState()
    val gdriveConnectedEmail by viewModel.gdriveConnectedEmail.collectAsState()
    val context = LocalContext.current

    val allPhotos by viewModel.allPhotos.collectAsState()
    val lockedCount = remember(allPhotos) { allPhotos.count { it.isLocked && !it.isDeleted } }
    val deletedCount = remember(allPhotos) { allPhotos.count { it.isDeleted } }
    val isAiUpdatingAlbums by viewModel.isAiUpdatingAlbums.collectAsState()
    val aiAlbumProgress by viewModel.aiAlbumProgress.collectAsState()
    val aiAlbumStatus by viewModel.aiAlbumStatus.collectAsState()

    val publicPhotos by viewModel.publicPhotos.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val syncFilter by viewModel.syncFilter.collectAsState()
    val selectedCustomLabel by viewModel.selectedCustomLabel.collectAsState()
    val customLabels by viewModel.customLabels.collectAsState()

    val availableTags by viewModel.availableTags.collectAsState()
    val availableLocations by viewModel.availableLocations.collectAsState()

    val selectedPhotoIds by viewModel.selectedPhotoIds.collectAsState()
    val isSelectionMode = selectedPhotoIds.isNotEmpty()

    val isAnyFilterActive = searchText.isNotEmpty() ||
            selectedTag != null ||
            selectedLocation != null ||
            syncFilter != SyncFilter.ALL ||
            selectedCustomLabel != null

    var showAddDialog by remember { mutableStateOf(false) }
    var showMoveToAlbumDialog by remember { mutableStateOf(false) }
    var showMoveToDirectoryDialog by remember { mutableStateOf(false) }
    var showDirectoryPickerForSinglePhoto by remember { mutableStateOf<Photo?>(null) }
    var activeLongPressedPhoto by remember { mutableStateOf<Photo?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showSingleFileDetailsDialog by remember { mutableStateOf<Photo?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showViewModeDialog by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }

    var isFilterFlyoutOpen by remember { mutableStateOf(false) }
    var filterFlyoutInteractionTimestamp by remember { mutableStateOf(0L) }
    val gridState = rememberLazyGridState()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val listState = rememberLazyListState()
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val activeFilterCount = (if (selectedTag != null) 1 else 0) +
            (if (selectedLocation != null) 1 else 0) +
            (if (syncFilter != SyncFilter.ALL) 1 else 0) +
            (if (selectedCustomLabel != null) 1 else 0)

    // Autohide flyer whenever the user scrolls in any view mode (Grid, Cozy, Compact, Masonry, List)
    LaunchedEffect(gridState, staggeredGridState, listState) {
        var lastGridIndex = gridState.firstVisibleItemIndex
        var lastGridOffset = gridState.firstVisibleItemScrollOffset
        var lastStaggeredIndex = staggeredGridState.firstVisibleItemIndex
        var lastStaggeredOffset = staggeredGridState.firstVisibleItemScrollOffset
        var lastListIndex = listState.firstVisibleItemIndex
        var lastListOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow {
            Triple(
                Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset),
                Pair(staggeredGridState.firstVisibleItemIndex, staggeredGridState.firstVisibleItemScrollOffset),
                Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            )
        }.collect { (gridPos, stagPos, listPos) ->
            val hasScrolled = gridPos.first != lastGridIndex || gridPos.second != lastGridOffset ||
                    stagPos.first != lastStaggeredIndex || stagPos.second != lastStaggeredOffset ||
                    listPos.first != lastListIndex || listPos.second != lastListOffset
            if (hasScrolled) {
                lastGridIndex = gridPos.first
                lastGridOffset = gridPos.second
                lastStaggeredIndex = stagPos.first
                lastStaggeredOffset = stagPos.second
                lastListIndex = listPos.first
                lastListOffset = listPos.second
                if (isFilterFlyoutOpen) {
                    isFilterFlyoutOpen = false
                }
            }
        }
    }

    LaunchedEffect(gridState.isScrollInProgress, staggeredGridState.isScrollInProgress, listState.isScrollInProgress) {
        if ((gridState.isScrollInProgress || staggeredGridState.isScrollInProgress || listState.isScrollInProgress) && isFilterFlyoutOpen) {
            isFilterFlyoutOpen = false
        }
    }

    // Inactivity autohide: if open and untouched for 10 seconds, autohide
    LaunchedEffect(isFilterFlyoutOpen, filterFlyoutInteractionTimestamp) {
        if (isFilterFlyoutOpen) {
            delay(10000)
            if (isFilterFlyoutOpen) {
                isFilterFlyoutOpen = false
            }
        }
    }

    // Close flyout on system back press
    BackHandler(enabled = isFilterFlyoutOpen) {
        isFilterFlyoutOpen = false
    }

    val viewMode by viewModel.viewMode.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var viewModeHudMessage by remember { mutableStateOf<String?>(null) }
    var viewModeHudJob by remember { mutableStateOf<Job?>(null) }

    val cycleViewMode: (Boolean) -> Unit = { zoomIn ->
        val nextMode = if (zoomIn) {
            when (viewMode) {
                ViewMode.COMPACT -> ViewMode.GRID
                ViewMode.GRID -> ViewMode.COZY
                ViewMode.COZY -> ViewMode.MASONRY
                ViewMode.MASONRY -> ViewMode.LIST
                ViewMode.LIST -> null
            }
        } else {
            when (viewMode) {
                ViewMode.LIST -> ViewMode.MASONRY
                ViewMode.MASONRY -> ViewMode.COZY
                ViewMode.COZY -> ViewMode.GRID
                ViewMode.GRID -> ViewMode.COMPACT
                ViewMode.COMPACT -> null
            }
        }
        if (nextMode != null) {
            viewModel.updateViewMode(nextMode)
            viewModeHudMessage = nextMode.label
            viewModeHudJob?.cancel()
            viewModeHudJob = coroutineScope.launch {
                delay(1500)
                viewModeHudMessage = null
            }
        }
    }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var aiBaseUrl by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }
    var aiModelId by remember { mutableStateOf("") }
    var aiModels by remember { mutableStateOf<List<VisionModel>>(emptyList()) }
    var aiDiscoveryError by remember { mutableStateOf<String?>(null) }
    var aiDiscovering by remember { mutableStateOf(false) }

    LaunchedEffect(showAiSettingsDialog) {
        if (showAiSettingsDialog) {
            val config = viewModel.getAiProviderConfig()
            aiBaseUrl = config.baseUrl
            aiApiKey = config.apiKey
            aiModelId = config.modelId
            aiDiscoveryError = null
        }
    }

    // Grouping logic by month and year
    val groupedPhotos = remember(publicPhotos) {
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        publicPhotos.groupBy { format.format(Date(it.dateAdded)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Sleek Search Box and Action Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchText,
                    onValueChange = {
                        viewModel.updateSearchText(it)
                        if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("gallery_search_input"),
                    placeholder = { 
                        Text(
                            text = "Search your photos", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = "Search", 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchText("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close, 
                                    contentDescription = "Clear Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true
                )

                // Top Three-Dot Overflow Menu (Contains Filters, Sort, View Mode, AI, Settings)
                Box {
                    IconButton(
                        onClick = { showTopOverflowMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (activeFilterCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .testTag("gallery_overflow_menu_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("$activeFilterCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTopOverflowMenu,
                        onDismissRequest = { showTopOverflowMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        // 1. View Mode Item
                        DropdownMenuItem(
                            text = { Text("View: ${viewMode.label}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (viewMode) {
                                        ViewMode.GRID -> Icons.Default.GridView
                                        ViewMode.MASONRY -> Icons.Default.Dashboard
                                        ViewMode.COZY -> Icons.Default.ViewAgenda
                                        ViewMode.COMPACT -> Icons.Default.Apps
                                        ViewMode.LIST -> Icons.Default.ViewList
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showTopOverflowMenu = false
                                showViewModeDialog = true
                            },
                            modifier = Modifier.testTag("view_mode_toggle_button")
                        )

                        // 2. Sort Item
                        DropdownMenuItem(
                            text = { Text("Sort: ${sortOption.displayName}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showTopOverflowMenu = false
                                showSortDialog = true
                            },
                            modifier = Modifier.testTag("sorting_toggle_button")
                        )

                        // 3. Filters Item
                        DropdownMenuItem(
                            text = {
                                Text(if (activeFilterCount > 0) "Filters ($activeFilterCount active)" else "Filters")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showTopOverflowMenu = false
                                isFilterFlyoutOpen = !isFilterFlyoutOpen
                                if (isFilterFlyoutOpen) {
                                    filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                }
                            },
                            modifier = Modifier.testTag("filter_toggle_button")
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // 4. Auto-Update Albums with AI
                        DropdownMenuItem(
                            text = { Text("Auto-Update Albums with AI") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showTopOverflowMenu = false
                                viewModel.autoUpdateAlbumsWithAi()
                            },
                            modifier = Modifier.testTag("menu_ai_auto_update_albums")
                        )

                        // 5. Select Photos
                        DropdownMenuItem(
                            text = { Text("Select Photos") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showTopOverflowMenu = false
                                val firstPhoto = publicPhotos.firstOrNull()
                                if (firstPhoto != null) {
                                    viewModel.togglePhotoSelection(firstPhoto.id)
                                }
                            },
                            modifier = Modifier.testTag("menu_select_photos")
                        )

                        // 6. Settings
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showTopOverflowMenu = false
                                onNavigateToSettings()
                            },
                            modifier = Modifier.testTag("gallery_settings_button")
                        )

                        if (activeFilterCount > 0) {
                            DropdownMenuItem(
                                text = { Text("Clear All Filters") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    viewModel.clearAllFilters()
                                },
                                modifier = Modifier.testTag("menu_clear_filters")
                            )
                        }
                    }
                }
            }

            // 2. Animated Filter Flyout (Autohiding on scroll, on select, on click outside, on apply, or on inactivity)
            AnimatedVisibility(
                visible = isFilterFlyoutOpen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("filter_flyout_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header: Title, Active Badge, Reset All, and Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Filter Stream",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (activeFilterCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            text = "$activeFilterCount active",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isAnyFilterActive) {
                                    TextButton(
                                        onClick = {
                                            viewModel.clearAllFilters()
                                            filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                        },
                                        modifier = Modifier.testTag("clear_all_filters_button"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Filters",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Reset All",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { isFilterFlyoutOpen = false },
                                    modifier = Modifier.size(32.dp).testTag("close_filter_flyout_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Filters",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Category 1: Cloud & Storage Sync
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "STORAGE & CLOUD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = syncFilter == SyncFilter.ALL,
                                        onClick = {
                                            viewModel.updateSyncFilter(SyncFilter.ALL)
                                            filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                        },
                                        label = { Text("All Media") },
                                        leadingIcon = { Icon(Icons.Default.Home, null, modifier = Modifier.size(14.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("filter_sync_all")
                                    )
                                }
                                if (isGDriveEnabled && !gdriveConnectedEmail.isNullOrBlank()) {
                                    item {
                                        FilterChip(
                                            selected = syncFilter == SyncFilter.SYNCED,
                                            onClick = {
                                                viewModel.updateSyncFilter(SyncFilter.SYNCED)
                                                filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                            },
                                            label = { Text("Cloud Synced") },
                                            leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(14.dp)) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("filter_sync_synced")
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = syncFilter == SyncFilter.UNSYNCED,
                                            onClick = {
                                                viewModel.updateSyncFilter(SyncFilter.UNSYNCED)
                                                filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                            },
                                            label = { Text("Local Only") },
                                            leadingIcon = { Icon(Icons.Default.CloudQueue, null, modifier = Modifier.size(14.dp)) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("filter_sync_unsynced")
                                        )
                                    }
                                }
                                if (selectedCustomLabel != null) {
                                    item {
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                viewModel.selectCustomLabel(null)
                                                filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                            },
                                            label = { Text("Album: $selectedCustomLabel") },
                                            leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp)) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    null,
                                                    modifier = Modifier.size(14.dp).clickable {
                                                        viewModel.selectCustomLabel(null)
                                                        filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                                    }
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("filter_selected_custom_label")
                                        )
                                    }
                                }
                            }
                        }

                        // Category 2: Locations
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "LOCATIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                item {
                                    InputChip(
                                        selected = selectedLocation == null,
                                        onClick = {
                                            viewModel.selectLocation(null)
                                            filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                        },
                                        label = { Text("All Places") },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("filter_place_all")
                                    )
                                }
                                items(availableLocations) { loc ->
                                    InputChip(
                                        selected = selectedLocation == loc,
                                        onClick = {
                                            viewModel.selectLocation(if (selectedLocation == loc) null else loc)
                                            filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                        },
                                        label = { Text(loc) },
                                        leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp)) },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("filter_place_$loc")
                                    )
                                }
                            }
                        }

                        // Category 3: Smart AI Tags
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SMART TAGS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                item {
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.selectTag(null)
                                            filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                        },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(imageVector = Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Text("All Photos")
                                            }
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (selectedTag == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            labelColor = if (selectedTag == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("filter_tag_all")
                                    )
                                }
                                items(availableTags) { tag ->
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.selectTag(if (selectedTag == tag) null else tag)
                                            filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                                        },
                                        label = { Text("#$tag") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (selectedTag == tag) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            labelColor = if (selectedTag == tag) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("filter_tag_$tag")
                                    )
                                }
                            }
                        }

                        // Footer with Apply & Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { isFilterFlyoutOpen = false },
                                modifier = Modifier.testTag("apply_filters_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Compact Active Filters Bar (Shown ONLY when flyout is closed AND filters are applied)
            AnimatedVisibility(
                visible = !isFilterFlyoutOpen && (activeFilterCount > 0),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        SuggestionChip(
                            onClick = { viewModel.clearAllFilters() },
                            label = { Text("Clear All", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Clear, contentDescription = "Clear All", modifier = Modifier.size(12.dp)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                iconContentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.testTag("clear_all_filters_chip")
                        )
                    }
                    if (selectedCustomLabel != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.selectCustomLabel(null) },
                                label = { Text("Album: $selectedCustomLabel", fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove album filter",
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    if (syncFilter != SyncFilter.ALL) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.updateSyncFilter(SyncFilter.ALL) },
                                label = { Text(if (syncFilter == SyncFilter.SYNCED) "Cloud" else "Local", fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove sync filter",
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    if (selectedLocation != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.selectLocation(null) },
                                label = { Text(selectedLocation!!, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove place filter",
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    if (selectedTag != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.selectTag(null) },
                                label = { Text("#$selectedTag", fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag filter",
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                isFilterFlyoutOpen = true
                                filterFlyoutInteractionTimestamp = System.currentTimeMillis()
                            },
                            label = { Text("+ More", fontSize = 12.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Home Hub: Private Vault & Trash quick access cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Private Vault Card
                Surface(
                    onClick = {
                        if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                        onNavigateToSecureFolder()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_vault_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Private Vault",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vault",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lockedCount > 0) "$lockedCount private" else "Protected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Trash Bin Card
                Surface(
                    onClick = {
                        if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                        onNavigateToTrash()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_trash_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (deletedCount > 0) MaterialTheme.colorScheme.errorContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Trash",
                                tint = if (deletedCount > 0) MaterialTheme.colorScheme.onErrorContainer 
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trash",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (deletedCount > 0) "$deletedCount deleted" else "Empty",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deletedCount > 0) MaterialTheme.colorScheme.error 
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // AI Smart Album Organizing Banner (Shown when AI album update is active)
            AnimatedVisibility(
                visible = isAiUpdatingAlbums,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("home_ai_album_update_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "AI is auto-updating albums...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(
                                onClick = { viewModel.cancelAiAlbumUpdate() },
                                modifier = Modifier.height(28.dp).testTag("home_btn_cancel_ai_albums")
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        val frac = if (aiAlbumProgress.second > 0) aiAlbumProgress.first.toFloat() / aiAlbumProgress.second.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = aiAlbumStatus ?: "Categorizing photos...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Grid representation with grouped timespan blocks
            if (publicPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty Grid",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No photographs matching filter.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reset tags, search criteria, or insert high-resolution Web presets using the '+' action below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.clearAllFilters()
                            }
                        ) {
                            Text("Clear Filters")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(viewMode) {
                            awaitEachGesture {
                                var startSpan = 0f
                                var currentSpan = 0f
                                var isPinching = false

                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val activePointers = event.changes.filter { it.pressed }

                                    if (activePointers.size >= 2) {
                                        val p1 = activePointers[0].position
                                        val p2 = activePointers[1].position
                                        val span = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)

                                        if (!isPinching) {
                                            isPinching = true
                                            startSpan = span
                                            currentSpan = span
                                        } else {
                                            currentSpan = span
                                            val ratio = currentSpan / (startSpan.coerceAtLeast(1f))
                                            if (ratio > 1.25f) {
                                                // Pinch OUT -> zoom in to larger view
                                                cycleViewMode(true)
                                                startSpan = currentSpan
                                                activePointers.forEach { it.consume() }
                                            } else if (ratio < 0.75f) {
                                                // Pinch IN -> zoom out to denser view
                                                cycleViewMode(false)
                                                startSpan = currentSpan
                                                activePointers.forEach { it.consume() }
                                            }
                                        }
                                    } else {
                                        isPinching = false
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    when (viewMode) {
                        ViewMode.GRID, ViewMode.COZY, ViewMode.COMPACT -> {
                            val columnsCount = when (viewMode) {
                                ViewMode.COZY -> 2
                                ViewMode.COMPACT -> 4
                                else -> 3
                            }
                            val spacing = when (viewMode) {
                                ViewMode.COZY -> 8.dp
                                ViewMode.COMPACT -> 3.dp
                                else -> 4.dp
                            }
                            val paddingH = when (viewMode) {
                                ViewMode.COZY -> 12.dp
                                ViewMode.COMPACT -> 6.dp
                                else -> 8.dp
                            }

                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(columnsCount),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("gallery_photo_grid"),
                                contentPadding = PaddingValues(start = paddingH, end = paddingH, top = 4.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                verticalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                groupedPhotos.forEach { (monthStr, photoList) ->
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = if (monthStr == SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())) "Today" else monthStr,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 20.sp,
                                                letterSpacing = 0.2.sp
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 16.dp, bottom = 8.dp)
                                                .testTag("header_$monthStr")
                                        )
                                    }

                                    items(photoList, key = { it.id }) { photo ->
                                        val isSelected = selectedPhotoIds.contains(photo.id)
                                        PhotoGridItem(
                                            photo = photo,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            isGDriveEnabled = isGDriveEnabled,
                                            gdriveConnectedEmail = gdriveConnectedEmail,
                                            onClick = {
                                                if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                                                if (isSelectionMode) {
                                                    viewModel.togglePhotoSelection(photo.id)
                                                } else {
                                                    onNavigateToDetail(photo)
                                                }
                                            },
                                            onLongClick = {
                                                if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                                                if (!isSelectionMode) {
                                                    activeLongPressedPhoto = photo
                                                    showContextMenu = true
                                                } else {
                                                    viewModel.togglePhotoSelection(photo.id)
                                                }
                                            }
                                        )
                                    }
                                }

                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SecureFolderShortcutCard(onClick = onNavigateToSecureFolder)
                                }
                            }
                        }

                        ViewMode.MASONRY -> {
                            LazyVerticalStaggeredGrid(
                                state = staggeredGridState,
                                columns = StaggeredGridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("gallery_photo_grid"),
                                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                groupedPhotos.forEach { (monthStr, photoList) ->
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(
                                            text = if (monthStr == SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())) "Today" else monthStr,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 20.sp,
                                                letterSpacing = 0.2.sp
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 16.dp, bottom = 8.dp)
                                                .testTag("header_$monthStr")
                                        )
                                    }

                                    items(photoList, key = { it.id }) { photo ->
                                        val isSelected = selectedPhotoIds.contains(photo.id)
                                        PhotoMasonryItem(
                                            photo = photo,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            isGDriveEnabled = isGDriveEnabled,
                                            gdriveConnectedEmail = gdriveConnectedEmail,
                                            onClick = {
                                                if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                                                if (isSelectionMode) {
                                                    viewModel.togglePhotoSelection(photo.id)
                                                } else {
                                                    onNavigateToDetail(photo)
                                                }
                                            },
                                            onLongClick = {
                                                if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                                                if (!isSelectionMode) {
                                                    activeLongPressedPhoto = photo
                                                    showContextMenu = true
                                                } else {
                                                    viewModel.togglePhotoSelection(photo.id)
                                                }
                                            }
                                        )
                                    }
                                }

                                item(span = StaggeredGridItemSpan.FullLine) {
                                    SecureFolderShortcutCard(onClick = onNavigateToSecureFolder)
                                }
                            }
                        }

                        ViewMode.LIST -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("gallery_photo_list"),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                groupedPhotos.forEach { (monthStr, photoList) ->
                                    item {
                                        Text(
                                            text = if (monthStr == SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())) "Today" else monthStr,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 20.sp,
                                                letterSpacing = 0.2.sp
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 16.dp, bottom = 8.dp)
                                                .testTag("list_header_$monthStr")
                                        )
                                    }

                                    items(photoList, key = { it.id }) { photo ->
                                        val isSelected = selectedPhotoIds.contains(photo.id)
                                        PhotoListItem(
                                            photo = photo,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            isGDriveEnabled = isGDriveEnabled,
                                            gdriveConnectedEmail = gdriveConnectedEmail,
                                            onClick = {
                                                if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                                                if (isSelectionMode) {
                                                    viewModel.togglePhotoSelection(photo.id)
                                                } else {
                                                    onNavigateToDetail(photo)
                                                }
                                            },
                                            onLongClick = {
                                                if (isFilterFlyoutOpen) isFilterFlyoutOpen = false
                                                if (!isSelectionMode) {
                                                    activeLongPressedPhoto = photo
                                                    showContextMenu = true
                                                } else {
                                                    viewModel.togglePhotoSelection(photo.id)
                                                }
                                            }
                                        )
                                    }
                                }

                                item {
                                    SecureFolderShortcutCard(onClick = onNavigateToSecureFolder)
                                }
                            }
                        }
                    }

                    // Floating HUD indicator when pinch or view changes
                    androidx.compose.animation.AnimatedVisibility(
                        visible = viewModeHudMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            shadowElevation = 6.dp,
                            modifier = Modifier.testTag("view_mode_hud_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (viewMode) {
                                        ViewMode.GRID -> Icons.Default.GridView
                                        ViewMode.MASONRY -> Icons.Default.Dashboard
                                        ViewMode.COZY -> Icons.Default.ViewAgenda
                                        ViewMode.COMPACT -> Icons.Default.Apps
                                        ViewMode.LIST -> Icons.Default.ViewList
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = viewModeHudMessage ?: "",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Premium Create/Import ActionFAB
        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("gallery_add_photo_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add smart photograph")
            }
        }

        // Animated Contextual Bulk Actions Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .testTag("bulk_actions_bar")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: Selection Status and Compact Cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${selectedPhotoIds.size} items selected",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        TextButton(
                            onClick = { viewModel.clearPhotoSelection() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Row 2: Equally spaced actions ensuring maximum accessibility and no clipping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bulk Lock (Vault)
                        SelectionActionBarItem(
                            icon = Icons.Default.Lock,
                            label = "Vault",
                            onClick = {
                                val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                viewModel.bulkToggleLockPhotos(selectedPhotos)
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("bulk_vault_btn")
                        )

                        // Bulk Move to Album
                        SelectionActionBarItem(
                            icon = Icons.Default.Folder,
                            label = "Album",
                            onClick = { showMoveToAlbumDialog = true },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("bulk_album_btn")
                        )

                        // Bulk Move to Directory
                        SelectionActionBarItem(
                            icon = Icons.Default.FolderOpen,
                            label = "Directory",
                            onClick = { showMoveToDirectoryDialog = true },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("bulk_directory_btn")
                        )

                        // Bulk Native Share
                        SelectionActionBarItem(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {
                                val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                sharePhotosNatively(context, selectedPhotos)
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("bulk_share_btn")
                        )

                        // Bulk Delete
                        SelectionActionBarItem(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            onClick = {
                                val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                viewModel.bulkDeletePhotos(selectedPhotos)
                            },
                            contentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("bulk_delete_btn")
                        )
                    }
                }
            }
        }
    }

    // Add Image Dialog setup
    if (showAddDialog) {
        ImportPhotoDialog(
            onDismiss = { showAddDialog = false },
            onAddPhoto = { url, title, location ->
                viewModel.addWebPhoto(url, title, location)
                showAddDialog = false
            }
        )
    }

    // Move to Custom Album / Collections Dialog
    if (showMoveToAlbumDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { showMoveToAlbumDialog = false },
            contentAlignment = Alignment.Center
        ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .testTag("bulk_move_album_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        
                        Text(
                            text = "Move to Custom Album",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Assign ${selectedPhotoIds.size} selected photographs to one of your custom collections:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        var customAlbumNameInput by remember { mutableStateOf("") }

                        // Scrollable collection list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            customLabels.forEach { label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                            viewModel.bulkMovePhotosToLabel(selectedPhotos, label)
                                            showMoveToAlbumDialog = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Option to enter a brand-new custom album
                        OutlinedTextField(
                            value = customAlbumNameInput,
                            onValueChange = { customAlbumNameInput = it },
                            label = { Text("Or create brand new album") },
                            placeholder = { Text("e.g. Summer 2026") },
                            modifier = Modifier.fillMaxWidth().testTag("new_collection_input_bulk"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showMoveToAlbumDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customAlbumNameInput.trim().isNotEmpty()) {
                                        val newLabel = customAlbumNameInput.trim()
                                        viewModel.createLabel(newLabel)
                                        val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                        viewModel.bulkMovePhotosToLabel(selectedPhotos, newLabel)
                                        showMoveToAlbumDialog = false
                                    }
                                },
                                enabled = customAlbumNameInput.isNotBlank(),
                                modifier = Modifier.testTag("bulk_collection_create_confirm")
                            ) {
                                Text("Create & Move")
                            }
                        }
                    }
                }
            }
        }
        // Long Press Context Menu Dialog
        if (showContextMenu && activeLongPressedPhoto != null) {
            val photo = activeLongPressedPhoto!!
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showContextMenu = false },
                contentAlignment = Alignment.Center
            ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .testTag("photo_longpress_context_menu")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header / Name
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (photo.tags.contains("video", ignoreCase = true)) Icons.Default.PlayArrow else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = photo.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = photo.location.ifBlank { "Unassigned Local Folder" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showContextMenu = false }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // 1. OPEN WITH (Intent actions chooser)
                        ContextMenuItemRow(
                            icon = Icons.Default.OpenInNew,
                            title = "Open With...",
                            subtitle = "Launch implicit system companion application",
                            onClick = {
                                showContextMenu = false
                                try {
                                    val mimeType = if (photo.tags.contains("video", ignoreCase = true)) "video/*" else "image/*"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(photo.imageUrl), mimeType)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = android.content.Intent.createChooser(intent, "Open file with")
                                    context.startActivity(chooser)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No compatible applications found to open this item.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            tag = "context_menu_open_with"
                        )

                        // NATIVE SHARE FILE Option
                        ContextMenuItemRow(
                            icon = Icons.Default.Share,
                            title = "Share File Natively",
                            subtitle = "Show system share panel to send this file",
                            onClick = {
                                showContextMenu = false
                                sharePhotoNatively(context, photo)
                            },
                            tag = "context_menu_native_share"
                        )

                        // 2. MOVE TO DIRECTORY
                        ContextMenuItemRow(
                            icon = Icons.Default.FolderOpen,
                            title = "Move to local directory...",
                            subtitle = "Re-route directory classification",
                            onClick = {
                                showContextMenu = false
                                showDirectoryPickerForSinglePhoto = photo
                            },
                            tag = "context_menu_move_to"
                        )

                        // 3. PHYSICAL STORAGE DETAILS
                        ContextMenuItemRow(
                            icon = Icons.Default.Description,
                            title = "Local File Details...",
                            subtitle = "Display full resolved local metrics & sizing",
                            onClick = {
                                showContextMenu = false
                                showSingleFileDetailsDialog = photo
                            },
                            tag = "context_menu_local_details"
                        )

                        // 4. ACTIVATE MULTI-SELECT
                        ContextMenuItemRow(
                            icon = Icons.Default.CheckCircle,
                            title = "Multi-select Photo",
                            subtitle = "Include in bulk operations workspace",
                            onClick = {
                                showContextMenu = false
                                viewModel.togglePhotoSelection(photo.id)
                            },
                            tag = "context_menu_select"
                        )
                    }
                }
            }
        }

        // Move to Local Folder Directory Dialog
        val directoryTargetPhoto = showDirectoryPickerForSinglePhoto
        val isBulkDirectoryMove = showMoveToDirectoryDialog

        if (isBulkDirectoryMove || directoryTargetPhoto != null) {
            Dialog(
                onDismissRequest = {
                    showMoveToDirectoryDialog = false
                    showDirectoryPickerForSinglePhoto = null
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                var customDirectoryInput by remember { mutableStateOf("") }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { 
                            showMoveToDirectoryDialog = false 
                            showDirectoryPickerForSinglePhoto = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .testTag("move_directory_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        
                        Text(
                            text = if (isBulkDirectoryMove) "Move Selected to Folder" else "Move to Folder",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Choose an existing local directory path or key in a brand new folder segment destination:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        val presetDirs = listOf("Camera", "Downloads", "Screenshots", "Web Presets", "Cloud Archive")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetDirs.forEach { dir ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isBulkDirectoryMove) {
                                                val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                                viewModel.bulkMovePhotosToLocation(selectedPhotos, dir)
                                                showMoveToDirectoryDialog = false
                                            } else if (directoryTargetPhoto != null) {
                                                viewModel.movePhotoToLocation(directoryTargetPhoto.id, dir)
                                                showDirectoryPickerForSinglePhoto = null
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = dir,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customDirectoryInput,
                            onValueChange = { customDirectoryInput = it },
                            label = { Text("New Folder Name") },
                            placeholder = { Text("e.g. Vacation_2026") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("directory_name_field"),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { 
                                showMoveToDirectoryDialog = false 
                                showDirectoryPickerForSinglePhoto = null
                            }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customDirectoryInput.trim().isNotEmpty()) {
                                        val dir = customDirectoryInput.trim()
                                        if (isBulkDirectoryMove) {
                                            val selectedPhotos = publicPhotos.filter { selectedPhotoIds.contains(it.id) }
                                            viewModel.bulkMovePhotosToLocation(selectedPhotos, dir)
                                            showMoveToDirectoryDialog = false
                                        } else if (directoryTargetPhoto != null) {
                                            viewModel.movePhotoToLocation(directoryTargetPhoto.id, dir)
                                            showDirectoryPickerForSinglePhoto = null
                                        }
                                    }
                                },
                                enabled = customDirectoryInput.isNotBlank(),
                                modifier = Modifier.testTag("directory_confirm_btn")
                            ) {
                                Text("Move")
                            }
                        }
                    }
                }
            }
        }
    }

        // Single File Storage Details Dialog
        if (showAiSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showAiSettingsDialog = false },
                icon = {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                },
                title = {
                    Column {
                        Text("AI Setup", fontWeight = FontWeight.Bold)
                        Text(
                            "Connect your vision provider",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    "Works with OpenAI-compatible vision APIs. Enter the provider URL and key, then discover its available models.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        OutlinedTextField(
                            value = aiBaseUrl,
                            onValueChange = { aiBaseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = aiApiKey,
                            onValueChange = { aiApiKey = it },
                            label = { Text("API key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )

                        OutlinedTextField(
                            value = aiModelId,
                            onValueChange = { aiModelId = it },
                            label = { Text("Vision model") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                enabled = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && !aiDiscovering,
                                onClick = {
                                    coroutineScope.launch {
                                        aiDiscovering = true
                                        aiDiscoveryError = null
                                        val result = viewModel.discoverAiModels(aiBaseUrl, aiApiKey)
                                        result.fold(
                                            onSuccess = { aiModels = it },
                                            onFailure = { aiDiscoveryError = it.message ?: "Model discovery failed" }
                                        )
                                        aiDiscovering = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (aiDiscovering) "Discovering…" else "Discover models")
                            }
                            OutlinedButton(
                                onClick = {
                                    aiModels = emptyList()
                                    aiModelId = ""
                                }
                            ) {
                                Text("Clear")
                            }
                        }

                        aiDiscoveryError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (aiModels.isNotEmpty()) {
                            Text(
                                "Discovered models",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                aiModels.forEach { model ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable { aiModelId = model.id },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (model.id == aiModelId) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = if (model.id == aiModelId)
                                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                        else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(model.name, fontWeight = if (model.id == aiModelId) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    if (model.capabilityKnown) "Vision capability verified" else "Capability not verified",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (model.id == aiModelId) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModelId.isNotBlank(),
                        onClick = {
                            viewModel.saveAiProviderConfig(aiBaseUrl, aiApiKey, aiModelId)
                            showAiSettingsDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAiSettingsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showViewModeDialog) {
            AlertDialog(
                onDismissRequest = { showViewModeDialog = false },
                modifier = Modifier.testTag("view_mode_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Choose View Style",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Tip: You can also pinch in/out anywhere in the gallery to quickly switch views!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        ViewMode.values().forEach { mode ->
                            val isSelected = viewMode == mode
                            Surface(
                                onClick = {
                                    viewModel.updateViewMode(mode)
                                    showViewModeDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("view_mode_option_${mode.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = when (mode) {
                                                ViewMode.GRID -> Icons.Default.GridView
                                                ViewMode.MASONRY -> Icons.Default.Dashboard
                                                ViewMode.COZY -> Icons.Default.ViewAgenda
                                                ViewMode.COMPACT -> Icons.Default.Apps
                                                ViewMode.LIST -> Icons.Default.ViewList
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = when (mode) {
                                                    ViewMode.GRID -> "Standard 3-column square grid"
                                                    ViewMode.MASONRY -> "Pinterest-style staggered aspect ratios"
                                                    ViewMode.COZY -> "Large 2-column spacious view"
                                                    ViewMode.COMPACT -> "Dense 4-column overview"
                                                    ViewMode.LIST -> "Detailed rows with file info"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.updateViewMode(mode)
                                            showViewModeDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showViewModeDialog = false },
                        modifier = Modifier.testTag("close_view_mode_dialog")
                    ) {
                        Text("Done")
                    }
                }
            )
        }

        if (showSortDialog) {
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                modifier = Modifier.testTag("sort_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Sort Photos By",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GallerySortOption.values().forEach { option ->
                            val isSelected = sortOption == option
                            Surface(
                                onClick = {
                                    viewModel.updateSortOption(option)
                                    showSortDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sort_option_${option.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.updateSortOption(option)
                                            showSortDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showSortDialog = false },
                        modifier = Modifier.testTag("close_sort_dialog")
                    ) {
                        Text("Done")
                    }
                }
            )
        }

        if (showSingleFileDetailsDialog != null) {
            Dialog(
                onDismissRequest = { showSingleFileDetailsDialog = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val detailPhoto = showSingleFileDetailsDialog!!
                val dateText = remember(detailPhoto.dateAdded) {
                    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy - HH:mm", Locale.getDefault())
                    sdf.format(Date(detailPhoto.dateAdded))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showSingleFileDetailsDialog = null },
                    contentAlignment = Alignment.Center
                ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .testTag("local_file_details_dialog"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LOCAL SYSTEM DETAILS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { showSingleFileDetailsDialog = null }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column {
                            Text(
                                text = "FILE RESOLVED PATH",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detailPhoto.imageUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = "RELATIVE DIRECTORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detailPhoto.location.ifBlank { "Unassigned Local Folder" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FILE SIZE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatFileSize(detailPhoto.sizeBytes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RESOLUTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (detailPhoto.width > 0 && detailPhoto.height > 0) "${detailPhoto.width}x${detailPhoto.height}" else "Unknown",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MIME TYPE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = detailPhoto.mimeType.ifBlank { if (detailPhoto.tags.contains("video", ignoreCase = true)) "video/*" else "image/*" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SYSTEM ENCODING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (detailPhoto.tags.contains("video", ignoreCase = true)) "H.264 Encoder" else "sRGB Baseline",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "FILE IMPORT TIMESTAMP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { showSingleFileDetailsDialog = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecureFolderShortcutCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clickable { onClick() }
            .testTag("secure_folder_shortcut_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F2FA)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEADDFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD0BCFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = Color(0xFF381E72)
                    )
                }
                Column {
                    Text(
                        text = "Secure Folder",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D1B20),
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = "Protected by local safety credentials",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF49454F),
                            fontSize = 12.sp
                        )
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = Color(0xFF49454F)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoMasonryItem(
    photo: Photo,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isGDriveEnabled: Boolean = false,
    gdriveConnectedEmail: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isVideo = remember(photo.tags) {
        photo.tags.split(",").map { it.trim().lowercase() }.contains("video")
    }
    val aspectRatio = remember(photo.id, photo.width, photo.height) {
        if (photo.width > 0 && photo.height > 0) {
            (photo.width.toFloat() / photo.height.toFloat()).coerceIn(0.65f, 1.55f)
        } else {
            val variants = listOf(0.75f, 1.0f, 1.33f, 0.82f, 1.25f, 0.68f)
            val index = Math.abs(photo.id.hashCode()) % variants.size
            variants[index]
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 3.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_item_card_${photo.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val cropRect = getCropRectFromTags(photo.tags)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (cropRect != null) {
                            val cropW = cropRect.right - cropRect.left
                            val cropH = cropRect.bottom - cropRect.top
                            scaleX = 1f / cropW
                            scaleY = 1f / cropH
                            translationX = -((cropRect.left + cropRect.right) / 2f - 0.5f) * size.width * (1f / cropW)
                            translationY = -((cropRect.top + cropRect.bottom) / 2f - 0.5f) * size.height * (1f / cropH)
                        }
                        rotationZ = getRotationFromTags(photo.tags)
                    },
                colorFilter = getColorMatrixFromTags(photo.tags)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) },
                contentScale = if (cropRect != null) ContentScale.FillBounds else ContentScale.Crop
            )

            // Video indicator: Clean, small badge at bottom-start
            if (isVideo) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Favorite indicator: Clean small heart at bottom-end
            if (photo.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // Top Status corner symbols (Cloud & Lock)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGDriveEnabled && !gdriveConnectedEmail.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                if (photo.isSynced) Color(0xCC0F766E) else Color(0xCC7C2D12),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (photo.isSynced) Icons.Default.Check else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                if (photo.isLocked) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Dim and Select Check Overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color(0x33000000)
                        )
                )

                // Render check indicator at top left of photo
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color(0x66000000)
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected status",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    photo: Photo,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isGDriveEnabled: Boolean = false,
    gdriveConnectedEmail: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isVideo = remember(photo.tags) {
        photo.tags.split(",").map { it.trim().lowercase() }.contains("video")
    }
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 3.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_item_card_${photo.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val cropRect = getCropRectFromTags(photo.tags)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (cropRect != null) {
                            val cropW = cropRect.right - cropRect.left
                            val cropH = cropRect.bottom - cropRect.top
                            scaleX = 1f / cropW
                            scaleY = 1f / cropH
                            translationX = -((cropRect.left + cropRect.right) / 2f - 0.5f) * size.width * (1f / cropW)
                            translationY = -((cropRect.top + cropRect.bottom) / 2f - 0.5f) * size.height * (1f / cropH)
                        }
                        rotationZ = getRotationFromTags(photo.tags)
                    },
                colorFilter = getColorMatrixFromTags(photo.tags)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) },
                contentScale = if (cropRect != null) ContentScale.FillBounds else ContentScale.Crop
            )

            // Video indicator: Clean, small badge at bottom-start
            if (isVideo) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Favorite indicator: Clean small heart at bottom-end
            if (photo.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // Top Status corner symbols (Cloud & Lock)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGDriveEnabled && !gdriveConnectedEmail.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                if (photo.isSynced) Color(0xCC0F766E) else Color(0xCC7C2D12),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (photo.isSynced) Icons.Default.Check else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                if (photo.isLocked) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Dim and Select Check Overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color(0x33000000)
                        )
                )

                // Render check indicator at top left of photo
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color(0x66000000)
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected status",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// Dialog that allows entering single-tap presets or manual URLs
@Composable
fun ImportPhotoDialog(
    onDismiss: () -> Unit,
    onAddPhoto: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }

    // Pre-configured elegant landscapes and human portraits to tap and import instantly
    val presets = listOf(
        Triple("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800", "Classic Female Portrait", "Studio, LA"),
        Triple("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800", "Professional Male Portrait", "Boston, USA"),
        Triple("https://images.unsplash.com/photo-1472214222541-d510753a4707?w=800", "Sunset Valley", "Napa Valley, USA"),
        Triple("https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=800", "Deep Cedar Forest", "Kyoto, Japan"),
        Triple("https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=800", "Fresh Pizza Art", "Napoli, Italy")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("import_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Import Smart Photograph",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "CHOOSE HIGH-FIDELITY PRESET (SINGLE-TAP)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )

                // Render preset columns
                presets.forEach { (url, label, site) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddPhoto(url, label, site) }
                            .testTag("preset_row_$label"),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(site, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Text(
                    text = "OR ENTER CUSTOM URL ENDPOINT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Photo Title") },
                    modifier = Modifier.fillMaxWidth().testTag("custom_title_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth().testTag("custom_loc_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = { Text("Image web URL link") },
                    modifier = Modifier.fillMaxWidth().testTag("custom_url_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAddPhoto(customUrl, title, location) },
                        enabled = customUrl.isNotBlank() && title.isNotBlank() && location.isNotBlank(),
                        modifier = Modifier.testTag("custom_add_confirm_button")
                    ) {
                        Text("Import Raw")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoListItem(
    photo: Photo,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isGDriveEnabled: Boolean = false,
    gdriveConnectedEmail: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isVideo = remember(photo.tags) {
        photo.tags.split(",").map { it.trim().lowercase() }.contains("video")
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_item_list_card_${photo.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val cropRect = getCropRectFromTags(photo.tags)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photo.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (cropRect != null) {
                                val cropW = cropRect.right - cropRect.left
                                val cropH = cropRect.bottom - cropRect.top
                                scaleX = 1f / cropW
                                scaleY = 1f / cropH
                                translationX = -((cropRect.left + cropRect.right) / 2f - 0.5f) * size.width * (1f / cropW)
                                translationY = -((cropRect.top + cropRect.bottom) / 2f - 0.5f) * size.height * (1f / cropH)
                            }
                            rotationZ = getRotationFromTags(photo.tags)
                        },
                    colorFilter = getColorMatrixFromTags(photo.tags)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) },
                    contentScale = if (cropRect != null) ContentScale.FillBounds else ContentScale.Crop
                )

                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video marker",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = photo.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (photo.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite icon",
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = photo.location.ifBlank { "Local Root" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = photo.description.ifBlank { "No detailed description provided." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isSelectionMode) {
                IconButton(
                    onClick = { onLongClick() },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("photo_list_menu_btn_${photo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ContextMenuItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            maxLines = 1
        )
    }
}


private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "Unknown"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
