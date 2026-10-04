package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Photo
import com.example.ui.GalleryViewModel

enum class AlbumSortOption(val displayName: String) {
    SMART_FIRST("Smart First"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    COUNT_DESC("Most Photos"),
    COUNT_ASC("Fewest Photos")
}

enum class AlbumViewMode {
    GRID,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBrowseWithFilter: (String) -> Unit
) {
    val customLabels by viewModel.customLabels.collectAsState()
    val allPhotos by viewModel.allPhotos.collectAsState()
    val publicPhotos = remember(allPhotos) { allPhotos.filter { !it.isLocked && !it.isDeleted } }

    val isAiUpdatingAlbums by viewModel.isAiUpdatingAlbums.collectAsState()
    val aiAlbumProgress by viewModel.aiAlbumProgress.collectAsState()
    val aiAlbumStatus by viewModel.aiAlbumStatus.collectAsState()
    val autoAssignAlbumsOnAi by viewModel.autoAssignAlbumsOnAi.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(AlbumSortOption.SMART_FIRST) }
    var viewMode by remember { mutableStateOf(AlbumViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newLabelName by remember { mutableStateOf("") }
    var showAiOptions by remember { mutableStateOf(false) }

    var activeEditingLabel by remember { mutableStateOf<String?>(null) }
    var showManageDialog by remember { mutableStateOf(false) }
    var selectedPhotoIdsForActiveLabel by remember { mutableStateOf(setOf<Int>()) }

    // Filter and Sort Albums
    val filteredAndSortedLabels = remember(customLabels, publicPhotos, searchQuery, sortOption) {
        val filtered = customLabels.filter { label ->
            if (searchQuery.isBlank()) true
            else label.contains(searchQuery.trim(), ignoreCase = true)
        }

        when (sortOption) {
            AlbumSortOption.SMART_FIRST -> filtered.sortedWith(
                compareByDescending<String> { GalleryViewModel.SMART_ALBUM_CATEGORIES.containsKey(it) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
            )
            AlbumSortOption.NAME_ASC -> filtered.sortedWith(String.CASE_INSENSITIVE_ORDER)
            AlbumSortOption.NAME_DESC -> filtered.sortedWith(String.CASE_INSENSITIVE_ORDER.reversed())
            AlbumSortOption.COUNT_DESC -> filtered.sortedByDescending { label ->
                publicPhotos.count { viewModel.containsCustomLabelTag(it, label) }
            }
            AlbumSortOption.COUNT_ASC -> filtered.sortedBy { label ->
                publicPhotos.count { viewModel.containsCustomLabelTag(it, label) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newLabelName = ""
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("btn_create_album_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Album")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Sleek Search Box and Action Toolbar (Search, Sort, View Mode, AI Smart)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("album_search_input"),
                    placeholder = {
                        Text(
                            text = "Search albums",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
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
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                // Sort Dropdown Menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("album_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort Albums",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        AlbumSortOption.values().forEach { option ->
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
                                    sortOption = option
                                    showSortMenu = false
                                },
                                modifier = Modifier.testTag("album_sort_option_${option.name.lowercase()}")
                            )
                        }
                    }
                }

                // View Mode Toggle (Grid/List)
                IconButton(
                    onClick = {
                        viewMode = if (viewMode == AlbumViewMode.GRID) AlbumViewMode.LIST else AlbumViewMode.GRID
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("album_view_toggle")
                ) {
                    Icon(
                        imageVector = if (viewMode == AlbumViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // AI Smart Albums Button / Toggle
                IconButton(
                    onClick = { showAiOptions = !showAiOptions },
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (showAiOptions || isAiUpdatingAlbums) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .testTag("album_ai_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Smart Albums",
                        tint = if (showAiOptions || isAiUpdatingAlbums) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Compact Collapsible AI Smart Albums Card
            AnimatedVisibility(
                visible = showAiOptions || isAiUpdatingAlbums,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("ai_smart_albums_hero_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "AI Smart Albums",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { showAiOptions = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }

                        if (isAiUpdatingAlbums) {
                            val progressFraction = if (aiAlbumProgress.second > 0) {
                                aiAlbumProgress.first.toFloat() / aiAlbumProgress.second.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = aiAlbumStatus ?: "Organizing photos...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = { viewModel.cancelAiAlbumUpdate() },
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("btn_cancel_ai_albums")
                                ) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.autoUpdateAlbumsWithAi() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.testTag("btn_ai_auto_update_albums"),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Auto-Update Albums", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Auto-assign",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = autoAssignAlbumsOnAi,
                                        onCheckedChange = { viewModel.setAutoAssignAlbumsOnAi(it) },
                                        modifier = Modifier.testTag("switch_auto_assign_albums")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Album Count Header & Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${filteredAndSortedLabels.size} Albums",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (searchQuery.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "filtered",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        newLabelName = ""
                        showCreateDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_open_create_dialog")
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Album", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            // 4. Content: Grid or List
            if (filteredAndSortedLabels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No albums matching \"$searchQuery\"" else "No albums created yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try checking the spelling or clear search" else "Create a custom album or let AI auto-generate them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) searchQuery = ""
                                else {
                                    newLabelName = ""
                                    showCreateDialog = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (searchQuery.isNotBlank()) "Clear Search" else "Create Album")
                        }
                    }
                }
            } else if (viewMode == AlbumViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("albums_grid")
                ) {
                    items(filteredAndSortedLabels, key = { it }) { labelName ->
                        val albumPhotos = remember(publicPhotos, labelName) {
                            publicPhotos.filter { viewModel.containsCustomLabelTag(it, labelName) }
                        }
                        val count = albumPhotos.size
                        val coverPhoto = albumPhotos.firstOrNull()
                        val isSmart = GalleryViewModel.SMART_ALBUM_CATEGORIES.containsKey(labelName)

                        AlbumGridCard(
                            labelName = labelName,
                            count = count,
                            coverPhoto = coverPhoto,
                            isSmart = isSmart,
                            onOpen = { onNavigateToBrowseWithFilter(labelName) },
                            onManage = {
                                activeEditingLabel = labelName
                                selectedPhotoIdsForActiveLabel = albumPhotos.map { it.id }.toSet()
                                showManageDialog = true
                            },
                            onDelete = { viewModel.deleteLabel(labelName) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("albums_list")
                ) {
                    items(filteredAndSortedLabels, key = { it }) { labelName ->
                        val albumPhotos = remember(publicPhotos, labelName) {
                            publicPhotos.filter { viewModel.containsCustomLabelTag(it, labelName) }
                        }
                        val count = albumPhotos.size
                        val coverPhoto = albumPhotos.firstOrNull()
                        val isSmart = GalleryViewModel.SMART_ALBUM_CATEGORIES.containsKey(labelName)

                        AlbumListCard(
                            labelName = labelName,
                            count = count,
                            coverPhoto = coverPhoto,
                            isSmart = isSmart,
                            onOpen = { onNavigateToBrowseWithFilter(labelName) },
                            onManage = {
                                activeEditingLabel = labelName
                                selectedPhotoIdsForActiveLabel = albumPhotos.map { it.id }.toSet()
                                showManageDialog = true
                            },
                            onDelete = { viewModel.deleteLabel(labelName) }
                        )
                    }
                }
            }
        }
    }

    // Create New Album Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Create New Album", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a name for your custom album collection:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newLabelName,
                        onValueChange = { newLabelName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_label_name"),
                        placeholder = { Text("e.g. Travel, Family, Roadtrip") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLabelName.isNotBlank()) {
                            viewModel.createLabel(newLabelName)
                            newLabelName = ""
                            showCreateDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_create_label")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // High fidelity overlay dialog to manage photo multi-selection group mapping
    if (showManageDialog && activeEditingLabel != null) {
        val labelName = activeEditingLabel!!
        Dialog(onDismissRequest = { showManageDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .testTag("manage_label_photos_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Manage Album",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Select photos to include in '$labelName'",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        IconButton(onClick = { showManageDialog = false }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (publicPhotos.isEmpty()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("No photos available in library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // Grid of photographs
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 90.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(publicPhotos, key = { it.id }) { photo ->
                                val isChecked = selectedPhotoIdsForActiveLabel.contains(photo.id)
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isChecked) 3.dp else 1.dp,
                                            color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedPhotoIdsForActiveLabel = if (isChecked) {
                                                selectedPhotoIdsForActiveLabel - photo.id
                                            } else {
                                                selectedPhotoIdsForActiveLabel + photo.id
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(photo.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = photo.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color(0x7F000000))
                                                )
                                            )
                                    )

                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedPhotoIdsForActiveLabel = if (checked == true) {
                                                selectedPhotoIdsForActiveLabel + photo.id
                                            } else {
                                                selectedPhotoIdsForActiveLabel - photo.id
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .testTag("checkbox_photo_${photo.id}"),
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showManageDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updatePhotosForLabel(labelName, selectedPhotoIdsForActiveLabel)
                                showManageDialog = false
                            },
                            modifier = Modifier.testTag("btn_save_group_photos"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumGridCard(
    labelName: String,
    count: Int,
    coverPhoto: Photo?,
    isSmart: Boolean,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_label_card_$labelName"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Cover Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverPhoto != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverPhoto.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = labelName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Top badges row (Smart indicator & count)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSmart) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "✨ AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(1.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info and actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = labelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onOpen,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("btn_view_album_$labelName"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("View", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onManage,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_manage_photos_$labelName")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Manage Photos",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_delete_label_$labelName")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Album",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumListCard(
    labelName: String,
    count: Int,
    coverPhoto: Photo?,
    isSmart: Boolean,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_label_card_$labelName"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverPhoto != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverPhoto.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = labelName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Title and Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = labelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSmart) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "✨ AI Smart",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "$count photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("btn_view_album_$labelName")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open Album",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onManage,
                    modifier = Modifier.testTag("btn_manage_photos_$labelName")
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Manage Photos",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("btn_delete_label_$labelName")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Album",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
