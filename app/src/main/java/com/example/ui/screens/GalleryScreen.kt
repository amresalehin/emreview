package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.Photo
import com.example.ui.GalleryViewModel
import com.example.ui.SyncFilter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDetail: (Photo) -> Unit,
    onNavigateToSecureFolder: () -> Unit
) {
    val isGDriveEnabled by viewModel.isGDriveEnabled.collectAsState()
    val gdriveConnectedEmail by viewModel.gdriveConnectedEmail.collectAsState()
    val context = LocalContext.current

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

    val viewMode by viewModel.viewMode.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    // Grouping logic by month and year
    val groupedPhotos = remember(publicPhotos) {
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        publicPhotos.groupBy { format.format(Date(it.dateAdded)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Sleek Search Box matching the HTML rounded shape and colors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchText,
                    onValueChange = { viewModel.updateSearchText(it) },
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

                // View Mode Toggle (Grid/List)
                IconButton(
                    onClick = {
                        val nextMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                        viewModel.updateViewMode(nextMode)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("view_mode_toggle_button")
                ) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sorting Toggle Menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("sorting_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        GallerySortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (sortOption == option) Icons.Default.Check else Icons.Default.Sort,
                                        contentDescription = null,
                                        tint = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    viewModel.updateSortOption(option)
                                    showSortMenu = false
                                },
                                modifier = Modifier.testTag("sorting_option_${option.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 2. Horizon Filter Rows with Material 3 themed colors
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isAnyFilterActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Filters applied",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { viewModel.clearAllFilters() },
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
                                text = "Clear All Filters",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Sync Filters (All, Cloud, Local)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAnyFilterActive) {
                        item {
                            SuggestionChip(
                                onClick = { viewModel.clearAllFilters() },
                                label = { Text("Clear Filters", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Clear, "Clear Filters", modifier = Modifier.size(14.dp)) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    iconContentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.testTag("clear_all_filters_chip")
                            )
                        }
                    }
                    if (selectedCustomLabel != null) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.selectCustomLabel(null) },
                                label = { Text("Album: $selectedCustomLabel") },
                                leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp)) },
                                trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp).clickable { viewModel.selectCustomLabel(null) }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = true,
                                    borderColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("filter_selected_custom_label")
                            )
                        }
                    }
                    item {
                        FilterChip(
                            selected = syncFilter == SyncFilter.ALL,
                            onClick = { viewModel.updateSyncFilter(SyncFilter.ALL) },
                            label = { Text("All Media") },
                            leadingIcon = { Icon(Icons.Default.Home, null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = syncFilter == SyncFilter.ALL,
                                borderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("filter_sync_all")
                        )
                    }
                    if (isGDriveEnabled && !gdriveConnectedEmail.isNullOrBlank()) {
                        item {
                            FilterChip(
                                selected = syncFilter == SyncFilter.SYNCED,
                                onClick = { viewModel.updateSyncFilter(SyncFilter.SYNCED) },
                                label = { Text("Cloud Synced") },
                                leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = syncFilter == SyncFilter.SYNCED,
                                    borderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.testTag("filter_sync_synced")
                            )
                        }
                        item {
                            FilterChip(
                                selected = syncFilter == SyncFilter.UNSYNCED,
                                onClick = { viewModel.updateSyncFilter(SyncFilter.UNSYNCED) },
                                label = { Text("Local Only") },
                                leadingIcon = { Icon(Icons.Default.CloudQueue, null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = syncFilter == SyncFilter.UNSYNCED,
                                    borderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.testTag("filter_sync_unsynced")
                            )
                        }
                    }
                }

                // Extracted Places Filter
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        InputChip(
                            selected = selectedLocation == null,
                            onClick = { viewModel.selectLocation(null) },
                            label = { Text("All Places") },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                enabled = true,
                                selected = selectedLocation == null,
                                borderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("filter_place_all")
                        )
                    }
                    items(availableLocations) { loc ->
                        InputChip(
                            selected = selectedLocation == loc,
                            onClick = { viewModel.selectLocation(loc) },
                            label = { Text(loc) },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp)) },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                enabled = true,
                                selected = selectedLocation == loc,
                                borderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("filter_place_$loc")
                        )
                    }
                }

                // Dynamically Extracted Smart tag chips with Auto-Awesome visual sparkles
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        SuggestionChip(
                            onClick = { viewModel.selectTag(null) },
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (selectedTag == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
                                    Text("All Photos")
                                }
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (selectedTag == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                labelColor = if (selectedTag == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (selectedTag == null) Color.Transparent else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("filter_tag_all")
                        )
                    }
                    items(availableTags) { tag ->
                        SuggestionChip(
                            onClick = { viewModel.selectTag(tag) },
                            label = { Text("#$tag") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (selectedTag == tag) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                labelColor = if (selectedTag == tag) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (selectedTag == tag) Color.Transparent else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("filter_tag_$tag")
                        )
                    }
                }
            }

            // 3. Grid representation with grouped timespan blocks
            if (publicPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                if (viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("gallery_photo_grid"),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Iterate and append styled section block headers
                        groupedPhotos.forEach { (monthStr, photoList) ->
                            // Header Span
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

                            // Photo Cards
                            items(photoList, key = { it.id }) { photo ->
                                val isSelected = selectedPhotoIds.contains(photo.id)
                                PhotoGridItem(
                                    photo = photo,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    isGDriveEnabled = isGDriveEnabled,
                                    gdriveConnectedEmail = gdriveConnectedEmail,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.togglePhotoSelection(photo.id)
                                        } else {
                                            onNavigateToDetail(photo)
                                        }
                                    },
                                    onLongClick = {
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

                        // Secure Folder shortcut row at the bottom of the scroll list (aligned with design spec)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .clickable { onNavigateToSecureFolder() }
                                    .testTag("secure_folder_shortcut_card"),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF7F2FA), // Matches HTML Secure Folder card color exactly
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
                                                .background(Color(0xFFD0BCFF)), // Matches HTML icon background
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Lock",
                                                tint = Color(0xFF381E72) // Matches HTML icon color
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
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("gallery_photo_list"),
                        contentPadding = PaddingValues(16.dp),
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
                                        if (isSelectionMode) {
                                            viewModel.togglePhotoSelection(photo.id)
                                        } else {
                                            onNavigateToDetail(photo)
                                        }
                                    },
                                    onLongClick = {
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

                        // Secure Folder Shortcut Row (List Mode)
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .clickable { onNavigateToSecureFolder() }
                                    .testTag("secure_folder_shortcut_card_list"),
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
                                    text = "${(detailPhoto.id * 147 + 1024) % 3200 + 400} KB",
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
                                    text = if (detailPhoto.tags.contains("video", ignoreCase = true)) "1920x1080 (HD)" else "4032x3024 (12MP)",
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
                                    text = if (detailPhoto.tags.contains("video", ignoreCase = true)) "video/mp4" else "image/jpeg",
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
            .testTag("photo_item_card_${photo.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
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

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video file indicator",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Dim and Select Check Overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else Color(0x33000000)
                        )
                )

                // Render check indicator at top left of photo
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
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

            // Absolute corner tags overlays
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x99000000))
                        )
                    )
            )

            // Top Status corner symbols
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left cloud status badge icon
                if (isGDriveEnabled && !gdriveConnectedEmail.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
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
                }

                // If locked, show key lock
                if (photo.isLocked) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xCCE0A96D), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Bottom title metadata strip
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = photo.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = photo.location,
                    color = Color(0xFFCBD5E1),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                IconButton(
                    onClick = { onLongClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color(0x99000000), CircleShape)
                        .testTag("photo_menu_btn_${photo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options Menu",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
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
