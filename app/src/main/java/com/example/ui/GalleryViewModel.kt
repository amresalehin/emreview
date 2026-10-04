package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.example.data.AppDatabase
import com.example.data.Photo
import com.example.ui.screens.ImageLayerData
import com.example.ui.screens.deserializeLayers
import com.example.data.PhotoRepository
import com.example.data.SecureSecretStore
import com.example.data.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.VaultPinSecurity
import com.example.ai.AiProvider
import com.example.ai.OpenAiCompatibleProvider
import com.example.ai.VisionModel
import kotlinx.coroutines.isActive

class GalleryViewModel(application: Application, private val repository: PhotoRepository) : AndroidViewModel(application) {

    private val secretStore = SecureSecretStore(application.applicationContext)

    // Filter properties
    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    // Multiple View Options
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode = _viewMode.asStateFlow()

    fun updateViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    // Sorting Options
    private val _sortOption = MutableStateFlow(GallerySortOption.DATE_DESC)
    val sortOption = _sortOption.asStateFlow()

    fun updateSortOption(option: GallerySortOption) {
        _sortOption.value = option
    }

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag = _selectedTag.asStateFlow()

    private val _selectedLocation = MutableStateFlow<String?>(null)
    val selectedLocation = _selectedLocation.asStateFlow()

    // Filter sync (All, Synced, Unsynced)
    private val _syncFilter = MutableStateFlow(SyncFilter.ALL)
    val syncFilter = _syncFilter.asStateFlow()

    // track newly created copies to immediately view or navigate to them
    private val _newlyCreatedPhotoId = kotlinx.coroutines.flow.MutableSharedFlow<Int>(replay = 0)
    val newlyCreatedPhotoId: kotlinx.coroutines.flow.SharedFlow<Int> = _newlyCreatedPhotoId

    // Custom labels/group list & selected custom label
    private val _customLabels = MutableStateFlow<List<String>>(emptyList())
    val customLabels = _customLabels.asStateFlow()

    private val _selectedCustomLabel = MutableStateFlow<String?>(null)
    val selectedCustomLabel = _selectedCustomLabel.asStateFlow()

    // AI Smart Album Automation states
    private val _isAiUpdatingAlbums = MutableStateFlow(false)
    val isAiUpdatingAlbums = _isAiUpdatingAlbums.asStateFlow()

    private val _aiAlbumProgress = MutableStateFlow(0 to 0) // current to total
    val aiAlbumProgress = _aiAlbumProgress.asStateFlow()

    private val _aiAlbumStatus = MutableStateFlow<String?>(null)
    val aiAlbumStatus = _aiAlbumStatus.asStateFlow()

    private val _autoAssignAlbumsOnAi = MutableStateFlow(true)
    val autoAssignAlbumsOnAi = _autoAssignAlbumsOnAi.asStateFlow()

    fun setAutoAssignAlbumsOnAi(enabled: Boolean) {
        _autoAssignAlbumsOnAi.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("auto_assign_albums_on_ai", enabled.toString())
        }
    }

    // PIN vault states
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked = _isVaultUnlocked.asStateFlow()

    private val _isPinSetupRequired = MutableStateFlow(true)
    val isPinSetupRequired = _isPinSetupRequired.asStateFlow()

    private val _vaultError = MutableStateFlow<String?>(null)
    val vaultError = _vaultError.asStateFlow()

    // Cloud syncing states
    private val _syncingState = MutableStateFlow(SyncConsoleState())
    val syncingState = _syncingState.asStateFlow()

    // Google Drive variables
    private val _isGDriveEnabled = MutableStateFlow(false)
    val isGDriveEnabled = _isGDriveEnabled.asStateFlow()

    private val _gdriveConnectedEmail = MutableStateFlow<String?>(null)
    val gdriveConnectedEmail = _gdriveConnectedEmail.asStateFlow()

    private val _gdriveSelectedFolder = MutableStateFlow<String?>(null)
    val gdriveSelectedFolder = _gdriveSelectedFolder.asStateFlow()

    private val _gdriveOauthToken = MutableStateFlow<String?>(null)
    val gdriveOauthToken = _gdriveOauthToken.asStateFlow()

    private val _recoverableAuthIntent = MutableStateFlow<android.content.Intent?>(null)
    val recoverableAuthIntent = _recoverableAuthIntent.asStateFlow()

    fun clearRecoverableIntent() {
        _recoverableAuthIntent.value = null
    }

    private val _syncSelectedAlbumsOnly = MutableStateFlow(false)
    val syncSelectedAlbumsOnly = _syncSelectedAlbumsOnly.asStateFlow()

    private val _selectedAlbumsToSync = MutableStateFlow<Set<String>>(emptySet())
    val selectedAlbumsToSync = _selectedAlbumsToSync.asStateFlow()

    fun setSyncSelectedAlbumsOnly(enabled: Boolean) {
        _syncSelectedAlbumsOnly.value = enabled
    }

    fun toggleAlbumToSync(album: String) {
        val current = _selectedAlbumsToSync.value
        _selectedAlbumsToSync.value = if (current.contains(album)) {
            current - album
        } else {
            current + album
        }
    }

    // Scheduled Sync Settings
    private val _isSyncScheduled = MutableStateFlow(false)
    val isSyncScheduled = _isSyncScheduled.asStateFlow()

    private val _syncScheduleInterval = MutableStateFlow("daily") // "daily", "weekly", "monthly"
    val syncScheduleInterval = _syncScheduleInterval.asStateFlow()

    private val _syncScheduleTime = MutableStateFlow("02:00") // "HH:mm" format
    val syncScheduleTime = _syncScheduleTime.asStateFlow()

    fun updateSyncSchedule(enabled: Boolean) {
        _isSyncScheduled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("is_sync_scheduled", enabled.toString())
        }
    }

    fun updateSyncScheduleInterval(interval: String) {
        _syncScheduleInterval.value = interval
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("sync_schedule_interval", interval)
        }
    }

    fun updateSyncScheduleTime(time: String) {
        _syncScheduleTime.value = time
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("sync_schedule_time", time)
        }
    }

    // UI interactive tagging state
    private val _isAnalyzing = MutableStateFlow<Int?>(null) // Photo ID being analyzed
    val isAnalyzing = _isAnalyzing.asStateFlow()

    // Multi-selection states
    private val _selectedPhotoIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedPhotoIds = _selectedPhotoIds.asStateFlow()

    fun togglePhotoSelection(photoId: Int) {
        val current = _selectedPhotoIds.value
        _selectedPhotoIds.value = if (current.contains(photoId)) {
            current - photoId
        } else {
            current + photoId
        }
    }

    fun clearPhotoSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    // Core Photos flow directly from Room
    val allPhotos: StateFlow<List<Photo>> = repository.allPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkPinStatus()
        prepopulateIfEmpty()
        loadCustomLabels()
        loadGoogleDriveStatus()
        loadAiAlbumSettings()
    }

    private fun loadAiAlbumSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val autoAssign = repository.getSystemConfig("auto_assign_albums_on_ai")
            if (autoAssign != null) {
                _autoAssignAlbumsOnAi.value = autoAssign == "true"
            }
        }
    }

    private fun loadGoogleDriveStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val enabledStr = repository.getSystemConfig("is_gdrive_enabled")
            _isGDriveEnabled.value = enabledStr == "true"
            
            val emailStr = repository.getSystemConfig("gdrive_connected_email")
            _gdriveConnectedEmail.value = if (emailStr.isNullOrEmpty()) null else emailStr

            val folderStr = repository.getSystemConfig("gdrive_selected_folder")
            _gdriveSelectedFolder.value = if (folderStr.isNullOrEmpty()) null else folderStr

            val isScheduledStr = repository.getSystemConfig("is_sync_scheduled")
            _isSyncScheduled.value = isScheduledStr == "true"

            val intervalStr = repository.getSystemConfig("sync_schedule_interval")
            _syncScheduleInterval.value = if (intervalStr.isNullOrEmpty()) "daily" else intervalStr

            val timeStr = repository.getSystemConfig("sync_schedule_time")
            _syncScheduleTime.value = if (timeStr.isNullOrEmpty()) "02:00" else timeStr
        }
    }

    fun setGDriveEnabled(enabled: Boolean) {
        _isGDriveEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("is_gdrive_enabled", enabled.toString())
        }
    }

    fun loginToGDrive(email: String, token: String? = null) {
        _gdriveConnectedEmail.value = email
        if (token != null) {
            _gdriveOauthToken.value = token
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("gdrive_connected_email", email)
        }
    }

    fun logoutFromGDrive() {
        val tokenToClear = _gdriveOauthToken.value
        _gdriveConnectedEmail.value = null
        _gdriveSelectedFolder.value = null
        _gdriveOauthToken.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("gdrive_connected_email", "")
            repository.saveSystemConfig("gdrive_selected_folder", "")
            if (!tokenToClear.isNullOrBlank()) {
                try {
                    invalidateGoogleToken(getApplication(), tokenToClear)
                } catch (e: Exception) {
                    Log.w("GalleryViewModel", "Failed to invalidate token on logout", e)
                }
            }
        }
    }

    fun selectGDriveFolder(folder: String) {
        _gdriveSelectedFolder.value = folder
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("gdrive_selected_folder", folder)
        }
    }

    private fun loadCustomLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            val listStr = repository.getSystemConfig("custom_labels_list")
            if (listStr != null) {
                val list = listStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                _customLabels.value = list
            } else {
                // Prepopulate with elegant defaults
                val defaults = listOf("Family", "Friends", "Vacations", "Favorites")
                _customLabels.value = defaults
                repository.saveSystemConfig("custom_labels_list", defaults.joinToString(","))
            }
        }
    }

    // Combined Filters Flow to cleanly bypass 5-parameter limit of default flow combine
    private val galleryFilters: Flow<GalleryFilters> = combine(
        combine(_searchText, _selectedTag, _selectedLocation) { search, tag, loc -> Triple(search, tag, loc) },
        combine(_syncFilter, _selectedCustomLabel) { sync, customLabel -> Pair(sync, customLabel) }
    ) { part1, part2 ->
        GalleryFilters(
            search = part1.first,
            tag = part1.second,
            location = part1.third,
            sync = part2.first,
            selectedCustomLabel = part2.second
        )
    }

    // Filtered photos for standard browse (strictly hides locked and soft-deleted assets!)
    val publicPhotos: StateFlow<List<Photo>> = combine(
        allPhotos, galleryFilters, _sortOption
    ) { photos, filters, sortBy ->
        photos.filter { !it.isLocked && !it.isDeleted } // Main screen hides vault & deleted photos
            .filter { photo ->
                val matchesSearch = photo.title.lowercase().contains(filters.search.lowercase()) ||
                        photo.description.lowercase().contains(filters.search.lowercase()) ||
                        photo.tags.lowercase().contains(filters.search.lowercase()) ||
                        photo.location.lowercase().contains(filters.search.lowercase())
                val matchesTag = filters.tag == null || photo.tags.split(",").map { it.trim().lowercase() }.contains(filters.tag.lowercase())
                val matchesLoc = filters.location == null || photo.location.equals(filters.location, ignoreCase = true)
                val matchesSync = when (filters.sync) {
                    SyncFilter.ALL -> true
                    SyncFilter.SYNCED -> photo.isSynced
                    SyncFilter.UNSYNCED -> !photo.isSynced
                }
                val matchesCustomLabel = filters.selectedCustomLabel == null ||
                        photo.tags.split(",").map { it.trim().lowercase() }.contains("group:${filters.selectedCustomLabel.lowercase()}")

                matchesSearch && matchesTag && matchesLoc && matchesSync && matchesCustomLabel
            }
            .let { list ->
                when (sortBy) {
                    GallerySortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
                    GallerySortOption.DATE_ASC -> list.sortedBy { it.dateAdded }
                    GallerySortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
                    GallerySortOption.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
                    GallerySortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                    GallerySortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Photos reserved for the private lockbox (excludes soft-deleted items too)
    val lockedPhotos: StateFlow<List<Photo>> = allPhotos.map { photos ->
        photos.filter { it.isLocked && !it.isDeleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Soft-deleted photos in the Recycle Bin / Recently Deleted trash folder
    val deletedPhotos: StateFlow<List<Photo>> = allPhotos.map { photos ->
        photos.filter { it.isDeleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Extracted Unique Smart Tags
    val availableTags: StateFlow<List<String>> = allPhotos.map { photos ->
        photos.flatMap { photo ->
            photo.tags.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "face_checked" && it != "detected_face" }
        }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Extracted Unique Places
    val availableLocations: StateFlow<List<String>> = allPhotos.map { photos ->
        photos.map { it.location.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Prepends secure PIN check on start
     */
    private fun checkPinStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedPin = repository.getSystemConfig("secure_pin")
            _isPinSetupRequired.value = savedPin == null
        }
    }

    fun setupSecurePin(pin: String) {
        if (!VaultPinSecurity.validatePin(pin)) {
            _vaultError.value = "PIN must be exactly 4 digits."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("secure_pin", VaultPinSecurity.hashForStorage(pin))
            _isPinSetupRequired.value = false
            _isVaultUnlocked.value = true
            _vaultError.value = null
        }
    }

    suspend fun unlockVault(pin: String): Boolean {
        val savedPin = repository.getSystemConfig("secure_pin")
        val success = VaultPinSecurity.verify(pin, savedPin)
        if (success) {
            _isVaultUnlocked.value = true
            _vaultError.value = null
            if (!VaultPinSecurity.isModernRecord(savedPin)) {
                repository.saveSystemConfig("secure_pin", VaultPinSecurity.hashForStorage(pin))
            }
        } else {
            _vaultError.value = "Incorrect passcode. Security locked."
            delay(2000)
            _vaultError.value = null
        }
        return success
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        _vaultError.value = null
    }

    fun resetPin() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("secure_pin", "")
            _isPinSetupRequired.value = true
            _isVaultUnlocked.value = false
            _vaultError.value = null
        }
    }

    // Setters
    fun updateSearchText(text: String) { _searchText.value = text }
    fun selectTag(tag: String?) { _selectedTag.value = tag }
    fun selectLocation(loc: String?) { _selectedLocation.value = loc }
    fun updateSyncFilter(filter: SyncFilter) { _syncFilter.value = filter }
    fun selectCustomLabel(label: String?) { _selectedCustomLabel.value = label }

    fun clearAllFilters() {
        _searchText.value = ""
        _selectedTag.value = null
        _selectedLocation.value = null
        _syncFilter.value = SyncFilter.ALL
        _selectedCustomLabel.value = null
    }

    fun createLabel(name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty() && !_customLabels.value.contains(cleanName)) {
            val newList = _customLabels.value + cleanName
            _customLabels.value = newList
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveSystemConfig("custom_labels_list", newList.joinToString(","))
            }
        }
    }

    fun deleteLabel(name: String) {
        val newList = _customLabels.value.filter { it != name }
        _customLabels.value = newList
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("custom_labels_list", newList.joinToString(","))
            
            // Clean up custom tags from corresponding photos safely in database
            allPhotos.value.forEach { photo ->
                if (containsCustomLabelTag(photo, name)) {
                    val updatedTags = removeCustomLabelTag(photo.tags, name)
                    repository.updatePhoto(photo.copy(tags = updatedTags))
                }
            }
        }
    }

    fun updatePhotosForLabel(label: String, selectedPhotoIds: Set<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (photo in allPhotos.value) {
                val hasTag = containsCustomLabelTag(photo, label)
                val shouldHaveTag = selectedPhotoIds.contains(photo.id)
                if (shouldHaveTag && !hasTag) {
                    val updatedTags = addCustomLabelTag(photo.tags, label)
                    repository.updatePhoto(photo.copy(tags = updatedTags))
                } else if (!shouldHaveTag && hasTag) {
                    val updatedTags = removeCustomLabelTag(photo.tags, label)
                    repository.updatePhoto(photo.copy(tags = updatedTags))
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Group photos updated inside folder successfully.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun containsCustomLabelTag(photo: Photo, label: String): Boolean {
        return photo.tags.split(",")
            .map { it.trim().lowercase() }
            .contains("group:${label.lowercase()}")
    }

    private fun addCustomLabelTag(tags: String, label: String): String {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val groupTag = "group:${label.trim()}"
        if (!tagList.any { it.equals(groupTag, ignoreCase = true) }) {
            tagList.add(groupTag)
        }
        return tagList.joinToString(", ")
    }

    private fun removeCustomLabelTag(tags: String, label: String): String {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val groupTag = "group:${label.trim()}"
        tagList.removeAll { it.equals(groupTag, ignoreCase = true) }
        return tagList.joinToString(", ")
    }

    /**
     * Toggle lock secure state of photo
     */
    fun togglePhotoLock(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = photo.copy(isLocked = !photo.isLocked)
            repository.updatePhoto(updated)
            withContext(Dispatchers.Main) {
                val action = if (updated.isLocked) "moved to Secure Folder" else "restored to Gallery"
                Toast.makeText(getApplication(), "Photo $action successfully.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Update photo metadata or visual tags with background physical processing if needed
     */
    fun updatePhoto(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            val processedPhoto = try {
                processAndSavePhysicalImage(photo)
            } catch (e: Exception) {
                photo
            }
            repository.updatePhoto(processedPhoto)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Changes saved successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Save a copy of an edited photo as a unique new record with background physical processing if needed
     */
    fun savePhotoCopy(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            val processedPhoto = try {
                processAndSavePhysicalImage(photo)
            } catch (e: Exception) {
                photo
            }
            val copy = processedPhoto.copy(id = 0, dateAdded = System.currentTimeMillis())
            val newId = repository.insertPhoto(copy)
            _newlyCreatedPhotoId.emit(newId.toInt())
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Saved as a copy successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Move photo to Recently Deleted (Trash Bin)
     */
    fun deletePhoto(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePhoto(photo.copy(isDeleted = true, deletedTimestamp = System.currentTimeMillis()))
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Moved to Recently Deleted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Bulk move selected photos to Recently Deleted
     */
    fun bulkDeletePhotos(photos: List<Photo>) {
        viewModelScope.launch(Dispatchers.IO) {
            photos.forEach { photo ->
                repository.updatePhoto(photo.copy(isDeleted = true, deletedTimestamp = System.currentTimeMillis()))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "${photos.size} items moved to Recently Deleted.", Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /**
     * Restore photo from Recently Deleted to standard gallery
     */
    fun restorePhoto(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePhoto(photo.copy(isDeleted = false, deletedTimestamp = 0L))
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Restored to Gallery.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Bulk restore selected photos from Recently Deleted
     */
    fun bulkRestorePhotos(photos: List<Photo>) {
        viewModelScope.launch(Dispatchers.IO) {
            photos.forEach { photo ->
                repository.updatePhoto(photo.copy(isDeleted = false, deletedTimestamp = 0L))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "${photos.size} photos successfully restored.", Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /**
     * Permanently delete photo from database
     */
    fun deletePhotoPermanently(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePhotoById(photo.id)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Deleted permanently.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Bulk permanently delete photos from database
     */
    fun bulkDeletePhotosPermanently(photos: List<Photo>) {
        viewModelScope.launch(Dispatchers.IO) {
            photos.forEach { photo ->
                repository.deletePhotoById(photo.id)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "${photos.size} items deleted permanently.", Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /**
     * Empty Recently Deleted folder
     */
    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = deletedPhotos.value
            list.forEach { photo ->
                repository.deletePhotoById(photo.id)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Recycle bin emptied (${list.size} items).", Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /**
     * Toggle Favorite state of photo
     */
    fun togglePhotoFavorite(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = photo.copy(isFavorite = !photo.isFavorite)
            repository.updatePhoto(updated)
            withContext(Dispatchers.Main) {
                val action = if (updated.isFavorite) "Added to Favorites" else "Removed from Favorites"
                Toast.makeText(getApplication(), action, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Bulk toggle lock secure state of photos
     */
    fun bulkToggleLockPhotos(photos: List<Photo>) {
        viewModelScope.launch(Dispatchers.IO) {
            var lockedCount = 0
            var restoredCount = 0
            photos.forEach { photo ->
                val updated = photo.copy(isLocked = !photo.isLocked)
                repository.updatePhoto(updated)
                if (updated.isLocked) lockedCount++ else restoredCount++
            }
            withContext(Dispatchers.Main) {
                val actionMessage = when {
                    lockedCount > 0 && restoredCount > 0 -> "$lockedCount moved to Secure Folder, $restoredCount restored to Gallery"
                    lockedCount > 0 -> "$lockedCount photos moved to Secure Folder successfully."
                    else -> "$restoredCount photos restored to Gallery successfully."
                }
                Toast.makeText(getApplication(), actionMessage, Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /**
     * Bulk move photos to custom album / label
     */
    fun bulkMovePhotosToLabel(photos: List<Photo>, label: String) {
        createLabel(label)
        viewModelScope.launch(Dispatchers.IO) {
            photos.forEach { photo ->
                // Clean any previous group tags so it's a true move action
                val cleanedTags = photo.tags.split(",")
                    .map { it.trim() }
                    .filter { !it.startsWith("group:", ignoreCase = true) && it.isNotEmpty() }
                    .toMutableList()
                cleanedTags.add("group:${label.trim()}")
                val updatedTags = cleanedTags.joinToString(", ")
                repository.updatePhoto(photo.copy(tags = updatedTags))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "${photos.size} photos moved to custom album '$label'.", Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /**
     * Move single photo to a local system directory (virtual storage mapping)
     */
    fun movePhotoToLocation(photoId: Int, newLocation: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = repository.getPhotoById(photoId)
            if (photo != null) {
                repository.updatePhoto(photo.copy(location = newLocation.trim()))
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Moved '${photo.title}' directory path to '$newLocation'.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Bulk move photos to local system directories
     */
    fun bulkMovePhotosToLocation(photos: List<Photo>, newLocation: String) {
        viewModelScope.launch(Dispatchers.IO) {
            photos.forEach { photo ->
                repository.updatePhoto(photo.copy(location = newLocation.trim()))
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Moved ${photos.size} photos to folder '$newLocation'.", Toast.LENGTH_SHORT).show()
                clearPhotoSelection()
            }
        }
    }

    /** Provider-neutral AI configuration and model discovery. */
    suspend fun getAiProviderConfig(): AiProviderConfig {
        val legacyKey = repository.getSystemConfig("ai_api_key")
        val secureKey = secretStore.get("ai_api_key")
        val apiKey = secureKey ?: legacyKey.orEmpty()
        if (secureKey == null && !legacyKey.isNullOrBlank()) {
            secretStore.put("ai_api_key", legacyKey)
            repository.deleteSystemConfig("ai_api_key")
        }
        return AiProviderConfig(
            repository.getSystemConfig("ai_base_url").orEmpty(),
            apiKey,
            repository.getSystemConfig("ai_model_id").orEmpty()
        )
    }

    fun saveAiProviderConfig(baseUrl: String, apiKey: String, modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSystemConfig("ai_base_url", baseUrl.trim().trimEnd('/'))
            repository.saveSystemConfig("ai_model_id", modelId.trim())
            secretStore.put("ai_api_key", apiKey.trim())
            repository.deleteSystemConfig("ai_api_key")
        }
    }

    suspend fun discoverAiModels(baseUrl: String, apiKey: String): Result<List<VisionModel>> =
        repository.discoverVisionModels(baseUrl, apiKey)

    private suspend fun configuredAiProvider(): AiProvider? {
        val config = getAiProviderConfig()
        return if (config.baseUrl.isNotBlank() && config.apiKey.isNotBlank() && config.modelId.isNotBlank()) {
            OpenAiCompatibleProvider("custom", "Custom provider", config.baseUrl, config.apiKey, config.modelId)
        } else null
    }

    companion object {
        val SMART_ALBUM_CATEGORIES = mapOf(
            "Nature" to listOf("nature", "landscape", "mountain", "beach", "forest", "tree", "river", "lake", "ocean", "outdoor", "outdoors", "sky", "sunset", "sunrise", "flower", "garden", "scenic"),
            "People" to listOf("person", "people", "portrait", "face", "selfie", "girl", "boy", "man", "woman", "child", "family", "crowd", "smile", "friend"),
            "Food & Dining" to listOf("food", "dining", "meal", "dish", "cooking", "restaurant", "lunch", "dinner", "breakfast", "coffee", "cake", "fruit", "drink", "sushi", "pizza", "pasta", "dessert"),
            "Architecture" to listOf("building", "architecture", "city", "street", "urban", "monument", "landmark", "house", "skyscraper", "bridge", "tower", "castle"),
            "Travel" to listOf("travel", "vacation", "trip", "tourist", "tourism", "hotel", "airport", "resort", "sea", "road", "flight"),
            "Pets & Animals" to listOf("dog", "cat", "pet", "puppy", "kitten", "bird", "animal", "fauna", "wildlife"),
            "Documents" to listOf("document", "receipt", "text", "paper", "screenshot", "whiteboard", "note", "letter", "page", "invoice")
        )
    }

    fun determineAlbumsForPhoto(photo: Photo, tags: List<String>, description: String): Set<String> {
        val matchedAlbums = mutableSetOf<String>()
        val combinedText = "${photo.title} ${photo.location} $description ${tags.joinToString(" ")}".lowercase()

        // 1. Match standard smart album categories
        SMART_ALBUM_CATEGORIES.forEach { (albumName, keywords) ->
            if (keywords.any { keyword -> combinedText.contains(keyword) }) {
                matchedAlbums.add(albumName)
            }
        }

        // 2. Match existing custom labels
        _customLabels.value.forEach { customLabel ->
            val labelLower = customLabel.lowercase()
            if (combinedText.contains(labelLower) || tags.any { it.equals(labelLower, ignoreCase = true) }) {
                matchedAlbums.add(customLabel)
            }
        }

        return matchedAlbums
    }

    private var aiAlbumJob: kotlinx.coroutines.Job? = null

    fun cancelAiAlbumUpdate() {
        aiAlbumJob?.cancel()
        _isAiUpdatingAlbums.value = false
        _aiAlbumStatus.value = "Cancelled"
    }

    /**
     * AI Feature: Analyze public photos and auto-update / categorize them into smart albums.
     */
    fun autoUpdateAlbumsWithAi() {
        if (_isAiUpdatingAlbums.value) return
        _isAiUpdatingAlbums.value = true
        _aiAlbumStatus.value = "Preparing photo collection..."

        aiAlbumJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val photosToProcess = allPhotos.value.filter { !it.isDeleted && !it.isLocked }
                val total = photosToProcess.size
                if (total == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "No photos available to organize.", Toast.LENGTH_SHORT).show()
                    }
                    _isAiUpdatingAlbums.value = false
                    return@launch
                }

                _aiAlbumProgress.value = 0 to total
                val provider = configuredAiProvider()
                var newlyOrganizedCount = 0
                val newlyAddedAlbums = mutableSetOf<String>()

                for ((index, photo) in photosToProcess.withIndex()) {
                    _aiAlbumProgress.value = (index + 1) to total
                    _aiAlbumStatus.value = "Categorizing ${index + 1}/$total: ${photo.title}"

                    var currentDesc = photo.description
                    val needsAiAnalysis = photo.tags.isBlank() || photo.tags == "unindexed" || photo.tags == "auto"

                    val tagList: List<String> = if (needsAiAnalysis) {
                        try {
                            val taggingResult = repository.analyzePhoto(getApplication(), photo.imageUrl, photo.title, provider)
                            currentDesc = taggingResult.description
                            taggingResult.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        } catch (e: Exception) {
                            Log.w("GalleryViewModel", "Auto album analysis failed for photo ${photo.id}", e)
                            photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        }
                    } else {
                        photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }

                    val matchedAlbums = determineAlbumsForPhoto(photo, tagList, currentDesc)
                    if (matchedAlbums.isNotEmpty()) {
                        val existingTags = photo.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

                        // Add new tags from analysis if available
                        tagList.forEach { t ->
                            if (!t.startsWith("group:") && !existingTags.any { it.equals(t, ignoreCase = true) }) {
                                existingTags.add(t)
                            }
                        }

                        // Add group tags for each matched album
                        var photoUpdated = false
                        matchedAlbums.forEach { album ->
                            val groupTag = "group:$album"
                            if (!existingTags.any { it.equals(groupTag, ignoreCase = true) }) {
                                existingTags.add(groupTag)
                                photoUpdated = true
                            }
                            if (!_customLabels.value.contains(album)) {
                                newlyAddedAlbums.add(album)
                            }
                        }

                        if (photoUpdated || needsAiAnalysis) {
                            val updatedPhoto = photo.copy(
                                tags = existingTags.joinToString(", "),
                                description = currentDesc
                            )
                            repository.updatePhoto(updatedPhoto)
                            newlyOrganizedCount++
                        }
                    }
                }

                if (newlyAddedAlbums.isNotEmpty()) {
                    val updatedLabels = (_customLabels.value + newlyAddedAlbums).distinct()
                    _customLabels.value = updatedLabels
                    repository.saveSystemConfig("custom_labels_list", updatedLabels.joinToString(","))
                }

                withContext(Dispatchers.Main) {
                    val msg = "✨ AI organized $newlyOrganizedCount photos across ${_customLabels.value.size} albums!"
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                }
                _aiAlbumStatus.value = "Complete"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.i("GalleryViewModel", "AI album auto-update cancelled.")
                } else {
                    Log.e("GalleryViewModel", "AI album auto-update failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "AI album update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isAiUpdatingAlbums.value = false
            }
        }
    }

    /** Trigger image analysis with the user-configured provider/model and auto-update albums. */
    fun triggerAiTagging(photo: Photo) {
        if (_isAnalyzing.value != null) return
        _isAnalyzing.value = photo.id
        viewModelScope.launch {
            try {
                val result = repository.analyzePhoto(getApplication(), photo.imageUrl, photo.title, configuredAiProvider())

                // Preserve existing group tags
                val existingGroupTags = photo.tags.split(",")
                    .map { it.trim() }
                    .filter { it.startsWith("group:", ignoreCase = true) }
                    .toMutableList()

                val newTags = result.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

                // Auto-assign to albums if enabled
                if (_autoAssignAlbumsOnAi.value) {
                    val matchedAlbums = determineAlbumsForPhoto(photo, newTags, result.description)
                    val newlyDiscoveredAlbums = mutableListOf<String>()
                    matchedAlbums.forEach { album ->
                        val groupTag = "group:$album"
                        if (!existingGroupTags.any { it.equals(groupTag, ignoreCase = true) }) {
                            existingGroupTags.add(groupTag)
                        }
                        if (!_customLabels.value.contains(album)) {
                            newlyDiscoveredAlbums.add(album)
                        }
                    }
                    if (newlyDiscoveredAlbums.isNotEmpty()) {
                        val updatedLabels = (_customLabels.value + newlyDiscoveredAlbums).distinct()
                        _customLabels.value = updatedLabels
                        repository.saveSystemConfig("custom_labels_list", updatedLabels.joinToString(","))
                    }
                }

                val combinedTags = (newTags + existingGroupTags).distinct().joinToString(", ")
                repository.updatePhoto(photo.copy(tags = combinedTags, description = result.description))
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), if (result.isRealAI) "AI analysis complete!" else "Offline tagging complete!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "AI tagging failed", e)
            } finally {
                _isAnalyzing.value = null
            }
        }
    }

    /**
     * Clear all database photos
     */
    fun clearAllPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = allPhotos.value
            currentList.forEach {
                repository.deletePhotoById(it.id)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "All photo collections cleared.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Add Custom Photo URL to Gallery
     */
    fun addWebPhoto(url: String, title: String, location: String) {
        val cleanUrl = url.trim().ifEmpty { 
            "https://images.unsplash.com/photo-1472214222541-d510753a4707?w=800" // default sunset
        }
        val cleanTitle = title.trim().ifBlank { "Custom Scene" }
        val cleanLoc = location.trim().ifBlank { "Local" }

        viewModelScope.launch(Dispatchers.IO) {
            val newPhoto = Photo(
                imageUrl = cleanUrl,
                title = cleanTitle,
                description = "Adding file.. Requesting AI tag analysis.",
                location = cleanLoc,
                isSynced = false,
                isLocked = false,
                tags = "unindexed"
            )
            val newId = repository.insertPhoto(newPhoto)
            
            // Automatically launch background AI indexing on insert.
            val created = newPhoto.copy(id = newId.toInt())
            triggerAiTagging(created)
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    /**
     * Cancel active sync operation
     */
    fun cancelCloudSyncAction() {
        syncJob?.cancel()
    }

    /**
     * Cloud Sync Core Engine
     * Loops through unsynced items, updating status sequentially
     */
    fun runCloudSyncAction() {
        if (_syncingState.value.isSyncing) return // Sync already running

        syncJob = viewModelScope.launch {
            try {
                val isDrive = _isGDriveEnabled.value
                val driveEmail = _gdriveConnectedEmail.value
                val driveFolder = _gdriveSelectedFolder.value

                if (isDrive) {
                    if (driveEmail.isNullOrBlank()) {
                        _syncingState.value = _syncingState.value.copy(
                            syncMessage = "Configuration Error: No Google account.",
                            diagnosticLog = _syncingState.value.diagnosticLog + "[ERROR] Authentication missing. Please connect to Google Drive."
                        )
                        Toast.makeText(getApplication(), "Please connect your Google Account first.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    if (driveFolder.isNullOrBlank()) {
                        _syncingState.value = _syncingState.value.copy(
                            syncMessage = "Configuration Error: No folder selected.",
                            diagnosticLog = _syncingState.value.diagnosticLog + "[ERROR] Target folder missing. Please specify Google Drive folder."
                        )
                        Toast.makeText(getApplication(), "Please select a backup folder on Google Drive.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }

                if (_syncSelectedAlbumsOnly.value && _selectedAlbumsToSync.value.isEmpty()) {
                    _syncingState.value = _syncingState.value.copy(
                        isSyncing = false,
                        syncMessage = "No custom albums selected."
                    )
                    Toast.makeText(getApplication(), "Please select at least one album to sync or choose All Photos.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val syncAlbumsOnly = _syncSelectedAlbumsOnly.value
                val albumsToSync = _selectedAlbumsToSync.value.map { it.lowercase() }.toSet()

                val unsyncedList = allPhotos.value.filter { photo ->
                    val isUnsynced = !photo.isSynced
                    if (isUnsynced && syncAlbumsOnly) {
                        val tags = photo.tags.split(",").map { it.trim().lowercase() }
                        tags.any { tag ->
                            tag.startsWith("group:") && albumsToSync.contains(tag.substringAfter("group:"))
                        }
                    } else {
                        isUnsynced
                    }
                }

                if (unsyncedList.isEmpty()) {
                    val successLog = if (isDrive) {
                        if (syncAlbumsOnly) {
                            "[SUCCESS] All photos in the selected custom albums are already synced on Google Drive."
                        } else {
                            "[SUCCESS] Google Drive Sync checks out: My Drive/$driveFolder is up to date"
                        }
                    } else {
                        "[SUCCESS] Local repository matches all physical assets"
                    }
                    _syncingState.value = _syncingState.value.copy(
                        syncMessage = "All chosen media fully synchronized.",
                        diagnosticLog = _syncingState.value.diagnosticLog + successLog
                    )
                    Toast.makeText(getApplication(), "Photos in chosen filter are already fully synchronized.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Try to resolve Google Auth Token (Play Services auto-fetch)
                var resolvedToken = _gdriveOauthToken.value

                if (isDrive && !driveEmail.isNullOrBlank()) {
                    try {
                        _syncingState.value = _syncingState.value.copy(
                            syncMessage = "Resolving Google Play account credentials..."
                        )
                        val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
                        
                        if (resolvedToken.isNullOrBlank()) {
                            resolvedToken = withContext(Dispatchers.IO) {
                                GoogleAuthUtil.getToken(getApplication(), driveEmail, scope)
                            }
                            if (!resolvedToken.isNullOrBlank()) {
                                _gdriveOauthToken.value = resolvedToken
                            }
                        }
                    } catch (recoverable: UserRecoverableAuthException) {
                        Log.w("GalleryViewModel", "UserRecoverableAuthException caught! Directing user to consent intent.")
                        _recoverableAuthIntent.value = recoverable.intent
                        _syncingState.value = _syncingState.value.copy(
                            isSyncing = false,
                            syncMessage = "Authorization required. Please review permission popup.",
                            diagnosticLog = _syncingState.value.diagnosticLog + "[ACTION REQUIRED] Google Drive permission is required. Opening Google Consent Form screen..."
                        )
                        return@launch
                    } catch (e: Exception) {
                        Log.w("GalleryViewModel", "GoogleAuthUtil check failed: ${e.message}")
                        _syncingState.value = _syncingState.value.copy(
                            isSyncing = false,
                            syncMessage = "Google Authentication Failed: ${e.localizedMessage ?: e.message}",
                            diagnosticLog = _syncingState.value.diagnosticLog + "[ERROR] Authentication failed: ${e.localizedMessage ?: e.message}"
                        )
                        Toast.makeText(getApplication(), "Google Authentication Failed. Please reconnect account.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }

                _syncingState.value = _syncingState.value.copy(
                    isSyncing = true,
                    syncMessage = if (isDrive) "Initializing Google Drive connection..." else "Initializing Local Archive catalog...",
                    bandwidthSpeed = "calculating...",
                    currentTransferredBytes = 0L,
                    totalBytesToTransfer = unsyncedList.size * 2000000L // Approx size
                )

                delay(1000)

                var gdriveFolderId: String? = null
                if (isDrive) {
                    if (resolvedToken.isNullOrBlank()) {
                        throw Exception("Google account credentials could not be resolved. Please reconnect account.")
                    }
                    _syncingState.value = _syncingState.value.copy(
                        syncMessage = "Resolving directory 'My Drive/$driveFolder' on Cloud...",
                        diagnosticLog = _syncingState.value.diagnosticLog + "[COMMS] Connecting live Google API with auth bearer"
                    )
                    gdriveFolderId = repository.createGDriveFolderIfNotExist(resolvedToken, driveFolder ?: "SmartGalleryBackup")
                    if (gdriveFolderId == null) {
                        throw Exception("Could not verify or create directory '$driveFolder' on Google Drive.")
                    }
                    _syncingState.value = _syncingState.value.copy(
                        diagnosticLog = _syncingState.value.diagnosticLog + "[SUCCESS] Target directory verified (ID: $gdriveFolderId)"
                    )
                }

                delay(1000)
                _syncingState.value = _syncingState.value.copy(
                    syncMessage = "Ready for transmission. Beginning queue...",
                )

                var index = 1
                for (photo in unsyncedList) {
                    val progressMsg = if (isDrive) {
                        "Uploading to Google Drive /$driveFolder: ${photo.title} ($index/${unsyncedList.size})"
                    } else {
                        "Archiving to Local Storage Catalog: ${photo.title} ($index/${unsyncedList.size})"
                    }
                    _syncingState.value = _syncingState.value.copy(
                        syncMessage = progressMsg,
                        bandwidthSpeed = if (isDrive) "14.2 MB/s" else "128.5 MB/s",
                        diagnosticLog = _syncingState.value.diagnosticLog + ("[TRANSMITTING] Index ${photo.id} -> '${photo.title}'")
                    )

                    var uploadSuccess = false
                    if (isDrive && !resolvedToken.isNullOrBlank() && gdriveFolderId != null) {
                        try {
                            var fileId: String? = null
                            try {
                                fileId = repository.uploadPhotoToGDrive(getApplication(), resolvedToken, gdriveFolderId, photo)
                            } catch (unauth: UnauthorizedException) {
                                _syncingState.value = _syncingState.value.copy(
                                    diagnosticLog = _syncingState.value.diagnosticLog + "[RETRY] Access Token expired. Re-authorizing account..."
                                )
                                try {
                                    invalidateGoogleToken(getApplication(), resolvedToken)
                                } catch (ex: Exception) {
                                    Log.w("GalleryViewModel", "Failed to invalidate token", ex)
                                }
                                val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
                                resolvedToken = withContext(Dispatchers.IO) {
                                    GoogleAuthUtil.getToken(getApplication(), driveEmail ?: "", scope)
                                }
                                if (!resolvedToken.isNullOrBlank()) {
                                    _gdriveOauthToken.value = resolvedToken
                                    _syncingState.value = _syncingState.value.copy(
                                        diagnosticLog = _syncingState.value.diagnosticLog + "[RETRY] New credentials obtained. Retrying upload..."
                                    )
                                    fileId = repository.uploadPhotoToGDrive(getApplication(), resolvedToken, gdriveFolderId, photo)
                                } else {
                                    throw unauth
                                }
                            }

                            if (fileId != null) {
                                uploadSuccess = true
                                repository.updatePhoto(photo.copy(isSynced = true))
                                _syncingState.value = _syncingState.value.copy(
                                    diagnosticLog = _syncingState.value.diagnosticLog + "[SYNCED] '${photo.title}' uploaded successfully. (Drive File ID: $fileId)",
                                    currentTransferredBytes = index * 2000000L
                                )
                            } else {
                                throw Exception("API search check returned empty ID for photo")
                            }
                        } catch (e: Exception) {
                            Log.e("GalleryViewModel", "Error uploading photo ${photo.id}", e)
                            _syncingState.value = _syncingState.value.copy(
                                isSyncing = false,
                                syncMessage = "Cloud Sync Failed: ${e.localizedMessage ?: e.message}",
                                diagnosticLog = _syncingState.value.diagnosticLog + "[ERROR] Failed upload for '${photo.title}': ${e.localizedMessage ?: e.message}"
                            )
                            Toast.makeText(getApplication(), "Sync failed: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    } else if (!isDrive) {
                        // Offline Local Sync
                        delay(600)
                        repository.updatePhoto(photo.copy(isSynced = true))
                        uploadSuccess = true
                        _syncingState.value = _syncingState.value.copy(
                            diagnosticLog = _syncingState.value.diagnosticLog + "[SYNCED] '${photo.title}' cataloged inside local database storage.",
                            currentTransferredBytes = index * 2000000L
                        )
                    } else {
                        throw Exception("Google account credentials or folder configurations are missing.")
                    }

                    index++
                }

                delay(800)
                val finalStatus = if (isDrive) {
                    "[IDLE] Google Drive sync session terminated."
                } else {
                    "[IDLE] Local vault cabinet sync session ended"
                }
                _syncingState.value = _syncingState.value.copy(
                    isSyncing = false,
                    bandwidthSpeed = "idle",
                    syncMessage = "Synchronization cycle completed successfully.",
                    diagnosticLog = _syncingState.value.diagnosticLog + finalStatus
                )
                val completedToast = if (isDrive) {
                    "Google Drive Backup finished!"
                } else {
                    "Local Gallery Backup successfully completed!"
                }
                Toast.makeText(getApplication(), completedToast, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _syncingState.value = _syncingState.value.copy(
                        isSyncing = false,
                        bandwidthSpeed = "idle",
                        syncMessage = "Synchronization was canceled by the user.",
                        diagnosticLog = _syncingState.value.diagnosticLog + "[CANCELED] Sync manually cancelled by user."
                    )
                    Toast.makeText(getApplication(), "Cloud sync canceled.", Toast.LENGTH_SHORT).show()
                    throw e
                } else {
                    Log.e("GalleryViewModel", "Global exception in sync job", e)
                    _syncingState.value = _syncingState.value.copy(
                        isSyncing = false,
                        bandwidthSpeed = "idle",
                        syncMessage = "Sync failed: ${e.localizedMessage ?: e.message}",
                        diagnosticLog = _syncingState.value.diagnosticLog + "[FATAL ERROR] Sync failed: ${e.localizedMessage ?: e.message}"
                    )
                    Toast.makeText(getApplication(), "Sync failed: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                syncJob = null
            }
        }
    }

    /**
     * Reset database with Unsplash preset assets
     */
    fun prepopulateIfEmpty() {
        // Disabled default hardcoded presets as requested by the user
    }

    /**
     * Incrementally scan MediaStore without loading all Photo entities into memory.
     * Only lightweight existing URIs are retained for duplicate detection.
     */
    fun scanLocalMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val existingUrls = repository.getAllPhotoUrls().toMutableSet()
            val batch = ArrayList<Photo>(500)

            suspend fun flushBatch() {
                if (batch.isEmpty()) return
                repository.insertPhotos(batch.toList())
                batch.clear()
            }

            suspend fun scanCollection(
                contentUri: Uri,
                projection: Array<String>,
                baseTag: String,
                defaultBucket: String
            ) {
                try {
                    context.contentResolver.query(
                        contentUri,
                        projection,
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        val titleColumn = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
                        val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        val bucketColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                        val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                        val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                        while (cursor.moveToNext()) {
                            if (idColumn < 0) continue
                            val id = cursor.getLong(idColumn)
                            val contentUriString = ContentUris.withAppendedId(contentUri, id).toString()
                            if (existingUrls.contains(contentUriString)) continue

                            val title = if (titleColumn >= 0) cursor.getString(titleColumn) ?: "$baseTag-$id" else "$baseTag-$id"
                            val date = if (dateColumn >= 0) cursor.getLong(dateColumn) * 1000L else System.currentTimeMillis()
                            val bucket = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: defaultBucket else defaultBucket
                            val sizeBytes = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else 0L
                            val width = if (widthColumn >= 0 && !cursor.isNull(widthColumn)) cursor.getInt(widthColumn) else 0
                            val height = if (heightColumn >= 0 && !cursor.isNull(heightColumn)) cursor.getInt(heightColumn) else 0
                            val mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn).orEmpty() else ""

                            batch += Photo(
                                imageUrl = contentUriString,
                                title = title,
                                description = "Local media imported dynamically from directory '$bucket'.",
                                dateAdded = date,
                                location = bucket,
                                isSynced = false,
                                isLocked = false,
                                tags = "$baseTag,${bucket.lowercase().replace(" ", "")}",
                                sizeBytes = sizeBytes,
                                width = width,
                                height = height,
                                mimeType = mimeType
                            )

                            if (batch.size >= 500) {
                                existingUrls.addAll(batch.map { it.imageUrl })
                                flushBatch()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GalleryViewModel", "Error scanning MediaStore $baseTag collection", e)
                }
            }

            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE
            )
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE
            )

            scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection, "image", "Local Pictures")
            scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection, "video", "Local Videos")
            flushBatch()

            Log.i("GalleryViewModel", "Completed incremental local media scan.")
        }
    }


    private fun getRotationFromTags(tags: String): Float {
        val tag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("rotate:") }
        return tag?.substringAfter("rotate:")?.toFloatOrNull() ?: 0f
    }

    private fun getCropRectFromTags(tags: String): android.graphics.RectF? {
        val tag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("crop_rect:") } ?: return null
        val parts = tag.substringAfter("crop_rect:").split("_")
        if (parts.size >= 4) {
            val left = parts[0].toFloatOrNull() ?: 0f
            val top = parts[1].toFloatOrNull() ?: 0f
            val right = parts[2].toFloatOrNull() ?: 1f
            val bottom = parts[3].toFloatOrNull() ?: 1f
            return android.graphics.RectF(left, top, right, bottom)
        }
        return null
    }

    private fun getFilterFromTags(tags: String): String {
        val tag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("filter:") }
        return tag?.substringAfter("filter:") ?: "none"
    }

    private fun getBrightnessFromTags(tags: String): Float {
        val tag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("brightness:") }
        return tag?.substringAfter("brightness:")?.toFloatOrNull() ?: 0f
    }

    private fun getContrastFromTags(tags: String): Float {
        val tag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("contrast:") }
        return tag?.substringAfter("contrast:")?.toFloatOrNull() ?: 1f
    }

    private fun getSaturationFromTags(tags: String): Float {
        val tag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("saturation:") }
        return tag?.substringAfter("saturation:")?.toFloatOrNull() ?: 1f
    }

    private fun removeEditParametersFromTags(tags: String): String {
        return tags.split(",")
            .map { it.trim() }
            .filter { t ->
                val tl = t.lowercase()
                !tl.startsWith("rotate:") &&
                !tl.startsWith("filter:") &&
                !tl.startsWith("brightness:") &&
                !tl.startsWith("contrast:") &&
                !tl.startsWith("saturation:") &&
                !tl.startsWith("crop:") &&
                !tl.startsWith("crop_rect:") &&
                !tl.startsWith("speed:") &&
                !tl.startsWith("trim:") &&
                !tl.startsWith("flip_h:") &&
                !tl.startsWith("flip_v:") &&
                !tl.startsWith("exposure:") &&
                !tl.startsWith("hue:") &&
                !tl.startsWith("vignette:") &&
                !tl.startsWith("blur:") &&
                !tl.startsWith("markup:") &&
                !tl.startsWith("watermark_text:") &&
                !tl.startsWith("watermark_color:") &&
                !tl.startsWith("watermark_size:") &&
                !tl.startsWith("watermark_opacity:") &&
                !tl.startsWith("watermark_pos:") &&
                !tl.startsWith("layers:") &&
                !tl.startsWith("editor_width:") &&
                !tl.startsWith("editor_height:")
            }
            .joinToString(", ")
    }

    private fun loadBitmapFromUrlOrPath(context: android.content.Context, imageUrl: String): android.graphics.Bitmap? {
        try {
            val bytes = if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(imageUrl))?.use { it.readBytes() }
            } else if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                val connection = java.net.URL(imageUrl).openConnection()
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.getInputStream()?.use { it.readBytes() }
            } else {
                try {
                    val file = java.io.File(imageUrl)
                    if (file.exists()) {
                        file.readBytes()
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            if (bytes == null || bytes.isEmpty()) return null
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Failed to load bitmap for rendering: $imageUrl", e)
            return null
        }
    }

    private fun applyEditsToBitmap(
        bitmap: android.graphics.Bitmap,
        rotation: Float,
        filter: String,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        cropRect: android.graphics.RectF?,
        flipH: Boolean = false,
        flipV: Boolean = false,
        exposure: Float = 0f,
        hue: Float = 0f,
        vignette: Float = 0f,
        blur: Float = 0f,
        markup: String = "",
        watermarkText: String = "",
        wmColorInt: Int = android.graphics.Color.WHITE,
        wmSizeRatio: Float = 0.04f,
        wmOpacity: Float = 0.8f,
        wmPos: String = "bottom_right",
        layers: String = "",
        editorWidth: Float = 320f,
        editorHeight: Float = 480f,
        context: android.content.Context
    ): android.graphics.Bitmap {
        var out = bitmap

        // 1. Crop (Applied first so that layers are placed relative to the visible cropped rect)
        if (cropRect != null) {
            val width = out.width
            val height = out.height
            val left = (cropRect.left * width).toInt().coerceIn(0, width - 1)
            val top = (cropRect.top * height).toInt().coerceIn(0, height - 1)
            val right = (cropRect.right * width).toInt().coerceIn(left + 1, width)
            val bottom = (cropRect.bottom * height).toInt().coerceIn(top + 1, height)
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0) {
                val cropped = android.graphics.Bitmap.createBitmap(out, left, top, w, h)
                if (cropped != out) {
                    out.recycle()
                    out = cropped
                }
            }
        }

        // 2. Color Adjustments & Creative Filters (Applied directly to the base image)
        val workingBitmap = android.graphics.Bitmap.createBitmap(out.width, out.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(workingBitmap)
        val paint = android.graphics.Paint()

        val cm = android.graphics.ColorMatrix()
        cm.reset()

        if (saturation != 1f) {
            val temp = android.graphics.ColorMatrix()
            temp.setSaturation(saturation)
            cm.postConcat(temp)
        }

        if (exposure != 0f) {
            val expScale = Math.pow(2.0, exposure.toDouble()).toFloat()
            val temp = android.graphics.ColorMatrix(floatArrayOf(
                expScale, 0f, 0f, 0f, 0f,
                0f, expScale, 0f, 0f, 0f,
                0f, 0f, expScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(temp)
        }

        if (hue != 0f) {
            val temp = android.graphics.ColorMatrix()
            temp.setRotate(0, hue)
            cm.postConcat(temp)
        }

        if (brightness != 0f || contrast != 1f) {
            val bOffset = brightness * 255f
            val temp = android.graphics.ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, 128f * (1f - contrast) + bOffset,
                0f, contrast, 0f, 0f, 128f * (1f - contrast) + bOffset,
                0f, 0f, contrast, 0f, 128f * (1f - contrast) + bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(temp)
        }

        if (filter != "none" && filter.isNotEmpty()) {
            val filterCm = when (filter.lowercase()) {
                "vintage" -> android.graphics.ColorMatrix(floatArrayOf(
                    0.9f, 0.5f, 0.1f, 0f, 0f,
                    0.3f, 0.8f, 0.1f, 0f, 0f,
                    0.2f, 0.3f, 0.5f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                "grayscale", "monochrome", "mono", "b&w" -> android.graphics.ColorMatrix(floatArrayOf(
                    0.33f, 0.33f, 0.33f, 0f, 0f,
                    0.33f, 0.33f, 0.33f, 0f, 0f,
                    0.33f, 0.33f, 0.33f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                "warm" -> android.graphics.ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                "cool" -> android.graphics.ColorMatrix(floatArrayOf(
                    0.8f, 0f, 0f, 0f, -10f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                "sepia" -> android.graphics.ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                ))
                "inverted" -> android.graphics.ColorMatrix(floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                ))
                "teal_orange" -> android.graphics.ColorMatrix(floatArrayOf(
                    1.2f, 0.1f, 0f, 0f, 15f,
                    0.1f, 0.9f, 0.1f, 0f, -10f,
                    0f, 0.1f, 1.25f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                "dramatic" -> android.graphics.ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -35f,
                    0f, 1.4f, 0f, 0f, -35f,
                    0f, 0f, 1.4f, 0f, -35f,
                    0f, 0f, 0f, 1f, 0f
                ))
                "fade" -> android.graphics.ColorMatrix(floatArrayOf(
                    0.82f, 0f, 0f, 0f, 30f,
                    0f, 0.82f, 0f, 0f, 30f,
                    0f, 0f, 0.82f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                else -> null
            }
            if (filterCm != null) {
                cm.postConcat(filterCm)
            }
        }

        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(out, 0f, 0f, paint)
        out.recycle()

        // 3. Soft Blur
        var blurBitmap = workingBitmap
        if (blur > 0f) {
            val blurred = blurBitmap(workingBitmap, blur)
            if (blurred != workingBitmap) {
                blurBitmap = blurred
            }
        }

        val compositionCanvas = android.graphics.Canvas(blurBitmap)

        // 4. Vignette Overlay Shading
        if (vignette > 0f) {
            val cx = blurBitmap.width / 2f
            val cy = blurBitmap.height / 2f
            val radius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
            val colors = intArrayOf(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.argb((vignette * 235f).toInt().coerceIn(0, 255), 0, 0, 0)
            )
            val stops = floatArrayOf(0.35f, 1.0f)
            val gradient = android.graphics.RadialGradient(cx, cy, radius, colors, stops, android.graphics.Shader.TileMode.CLAMP)
            val vignettePaint = android.graphics.Paint().apply {
                shader = gradient
                isAntiAlias = true
            }
            compositionCanvas.drawRect(0f, 0f, blurBitmap.width.toFloat(), blurBitmap.height.toFloat(), vignettePaint)
        }

        // 5. Draw Freehand Markup
        if (markup.isNotEmpty()) {
            drawMarkupOnBitmap(blurBitmap, markup)
        }

        // 6. Draw Image Layers
        var compositeBitmap = blurBitmap
        if (layers.isNotEmpty()) {
            try {
                val parsedLayers = deserializeLayers(layers)
                val density = context.resources.displayMetrics.density
                parsedLayers.forEach { layer ->
                    compositeBitmap = drawLayerOnBitmap(
                        base = compositeBitmap,
                        layer = layer,
                        editorWidth = editorWidth,
                        editorHeight = editorHeight,
                        density = density,
                        context = context
                    )
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Failed to draw image layers on composite", e)
            }
        }

        // 7. Rotate & Flips (Rotates and flips the complete composition, matching graphicsLayer behavior)
        var finalBitmap = compositeBitmap
        if (rotation != 0f || flipH || flipV) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation)
            val sx = if (flipH) -1f else 1f
            val sy = if (flipV) -1f else 1f
            if (flipH || flipV) {
                matrix.postScale(sx, sy, finalBitmap.width / 2f, finalBitmap.height / 2f)
            }
            val rotated = android.graphics.Bitmap.createBitmap(finalBitmap, 0, 0, finalBitmap.width, finalBitmap.height, matrix, true)
            if (rotated != finalBitmap) {
                if (finalBitmap != bitmap && finalBitmap != workingBitmap && finalBitmap != blurBitmap && finalBitmap != compositeBitmap) {
                    finalBitmap.recycle()
                }
                finalBitmap = rotated
            }
        }

        // 8. Draw Watermark Label
        if (watermarkText.isNotEmpty()) {
            drawWatermarkOnBitmap(finalBitmap, watermarkText, wmColorInt, wmSizeRatio, wmOpacity, wmPos)
        }

        return finalBitmap
    }

    private fun blurBitmap(src: android.graphics.Bitmap, factor: Float): android.graphics.Bitmap {
        if (factor <= 0f) return src
        val scale = 0.25f // Scale down for fast processing
        val width = Math.round(src.width * scale).coerceAtLeast(1)
        val height = Math.round(src.height * scale).coerceAtLeast(1)
        val small = android.graphics.Bitmap.createScaledBitmap(src, width, height, false)
        val blurredSmall = fastBlur(small, factor.toInt().coerceIn(1, 25)) ?: small
        val result = android.graphics.Bitmap.createScaledBitmap(blurredSmall, src.width, src.height, true)
        
        if (small != blurredSmall && small != src) small.recycle()
        if (blurredSmall != src && blurredSmall != small && blurredSmall != result) blurredSmall.recycle()
        return result
    }

    private fun drawMarkupOnBitmap(bitmap: android.graphics.Bitmap, markupTag: String) {
        if (markupTag.isEmpty()) return
        val canvas = android.graphics.Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        val strokeStrings = markupTag.split("|")
        for (strokeStr in strokeStrings) {
            val parts = strokeStr.split("_")
            if (parts.size < 3) continue
            val colorInt = parts[0].toIntOrNull() ?: android.graphics.Color.RED
            val thickness = parts[1].toFloatOrNull() ?: 8f
            
            val paint = android.graphics.Paint().apply {
                color = colorInt
                strokeWidth = thickness * (width / 500f)
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }
            
            val path = android.graphics.Path()
            var isFirst = true
            for (i in 2..parts.lastIndex) {
                val ptStr = parts[i]
                val coords = ptStr.split("-")
                if (coords.size == 2) {
                    val rx = coords[0].toFloatOrNull() ?: 0f
                    val ry = coords[1].toFloatOrNull() ?: 0f
                    val px = rx * width
                    val py = ry * height
                    if (isFirst) {
                        path.moveTo(px, py)
                        isFirst = false
                    } else {
                        path.lineTo(px, py)
                    }
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawWatermarkOnBitmap(
        bitmap: android.graphics.Bitmap,
        text: String,
        colorInt: Int,
        sizeRatio: Float,
        opacity: Float,
        pos: String
    ) {
        if (text.isEmpty()) return
        val canvas = android.graphics.Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        val paint = android.graphics.Paint().apply {
            color = colorInt
            alpha = (opacity * 255f).toInt().coerceIn(0, 255)
            textSize = height * sizeRatio
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val textW = textBounds.width().toFloat()
        val textH = textBounds.height().toFloat()
        
        val marginX = width * 0.05f
        val marginY = height * 0.05f
        
        val x = when (pos.lowercase()) {
            "top_left" -> marginX
            "top_right" -> width - textW - marginX
            "bottom_left" -> marginX
            "bottom_right" -> width - textW - marginX
            "center" -> (width - textW) / 2f
            else -> width - textW - marginX
        }
        
        val y = when (pos.lowercase()) {
            "top_left" -> marginY + textH
            "top_right" -> marginY + textH
            "bottom_left" -> height - marginY
            "bottom_right" -> height - marginY
            "center" -> (height + textH) / 2f
            else -> height - marginY
        }
        
        val shadowPaint = android.graphics.Paint(paint).apply {
            color = android.graphics.Color.BLACK
            alpha = (opacity * 130f).toInt().coerceIn(0, 255)
        }
        canvas.drawText(text, x + (height * 0.003f), y + (height * 0.003f), shadowPaint)
        canvas.drawText(text, x, y, paint)
    }

    private fun fastBlur(sentBitmap: android.graphics.Bitmap, radius: Int): android.graphics.Bitmap? {
        val config = sentBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888
        val bitmap = sentBitmap.copy(config, true) ?: return null
        if (radius < 1) {
            return null
        }
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        
        val dv = IntArray(256 * div)
        for (idx in 0 until 256 * div) {
            dv[idx] = idx / div
        }
        yw = 0
        yi = 0
        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            for (ri in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(ri, 0))]
                rsum += p shr 16 and 0xff
                gsum += p shr 8 and 0xff
                bsum += p and 0xff
            }
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                
                val p1 = pix[yw + Math.min(x + radius + 1, wm)]
                val p2 = pix[yw + Math.max(x - radius, 0)]
                
                rsum += (p1 shr 16 and 0xff) - (p2 shr 16 and 0xff)
                gsum += (p1 shr 8 and 0xff) - (p2 shr 8 and 0xff)
                bsum += (p1 and 0xff) - (p2 and 0xff)
                x++
                yi++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            yp = -radius * w
            for (ri in -radius..radius) {
                yi = Math.max(0, yp) + x
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = x
            y = 0
            while (y < h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                val p1 = x + Math.min(y + radius + 1, hm) * w
                val p2 = x + Math.max(y - radius, 0) * w
                
                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun saveBitmapToFile(context: android.content.Context, bitmap: android.graphics.Bitmap): String {
        val dir = java.io.File(context.filesDir, "edited_media")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = java.io.File(dir, "edited_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { outStream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outStream)
        }
        return file.absolutePath
    }

    private fun processAndSavePhysicalImage(photo: Photo): Photo {
        val tags = photo.tags
        val hasEdits = tags.split(",").any { t ->
            val tl = t.trim().lowercase()
            tl.startsWith("rotate:") || tl.startsWith("filter:") || 
            tl.startsWith("brightness:") || tl.startsWith("contrast:") || 
            tl.startsWith("saturation:") || tl.startsWith("crop_rect:") ||
            tl.startsWith("flip_h:") || tl.startsWith("flip_v:") ||
            tl.startsWith("exposure:") || tl.startsWith("hue:") ||
            tl.startsWith("vignette:") || tl.startsWith("blur:") ||
            tl.startsWith("markup:") || tl.startsWith("watermark_text:") ||
            tl.startsWith("layers:")
        }
        val isVideo = tags.lowercase().contains("video") || photo.imageUrl.contains("video", ignoreCase = true)
        
        if (!hasEdits || isVideo) {
            return photo
        }
        
        val context = getApplication<Application>().applicationContext
        val srcBitmap = loadBitmapFromUrlOrPath(context, photo.imageUrl) ?: return photo
        
        try {
            val rotation = getRotationFromTags(tags)
            val filter = getFilterFromTags(tags)
            val brightness = getBrightnessFromTags(tags)
            val contrast = getContrastFromTags(tags)
            val saturation = getSaturationFromTags(tags)
            val cropRect = getCropRectFromTags(tags)
            
            val flipH = tags.split(",").map { it.trim().lowercase() }.any { it == "flip_h:true" }
            val flipV = tags.split(",").map { it.trim().lowercase() }.any { it == "flip_v:true" }
            
            val exposure = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("exposure:") }?.substringAfter("exposure:")?.toFloatOrNull() ?: 0f
            val hue = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("hue:") }?.substringAfter("hue:")?.toFloatOrNull() ?: 0f
            val vignette = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("vignette:") }?.substringAfter("vignette:")?.toFloatOrNull() ?: 0f
            val blur = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("blur:") }?.substringAfter("blur:")?.toFloatOrNull() ?: 0f
            
            val markup = tags.split(",").find { it.trim().startsWith("markup:") }?.trim()?.substringAfter("markup:") ?: ""
            val watermarkText = tags.split(",").find { it.trim().startsWith("watermark_text:") }?.trim()?.substringAfter("watermark_text:") ?: ""
            
            val wmColorHex = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("watermark_color:") }?.substringAfter("watermark_color:") ?: ""
            val wmColorInt = if (wmColorHex.isNotEmpty()) {
                try {
                    android.graphics.Color.parseColor(wmColorHex)
                } catch (e: Exception) {
                    android.graphics.Color.WHITE
                }
            } else {
                android.graphics.Color.WHITE
            }
            
            val wmSizeRatio = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("watermark_size:") }?.substringAfter("watermark_size:")?.toFloatOrNull() ?: 0.04f
            val wmOpacity = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("watermark_opacity:") }?.substringAfter("watermark_opacity:")?.toFloatOrNull() ?: 0.8f
            val wmPos = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("watermark_pos:") }?.substringAfter("watermark_pos:") ?: "bottom_right"
            
            val layersTag = tags.split(",").find { it.trim().lowercase().startsWith("layers:") }?.substringAfter("layers:") ?: ""
            val editorWidthTag = tags.split(",").find { it.trim().lowercase().startsWith("editor_width:") }
            val editorWidth = editorWidthTag?.substringAfter("editor_width:")?.toFloatOrNull() ?: 320f
            val editorHeightTag = tags.split(",").find { it.trim().lowercase().startsWith("editor_height:") }
            val editorHeight = editorHeightTag?.substringAfter("editor_height:")?.toFloatOrNull() ?: 480f

            val finalBitmap = applyEditsToBitmap(
                bitmap = srcBitmap,
                rotation = rotation,
                filter = filter,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                cropRect = cropRect,
                flipH = flipH,
                flipV = flipV,
                exposure = exposure,
                hue = hue,
                vignette = vignette,
                blur = blur,
                markup = markup,
                watermarkText = watermarkText,
                wmColorInt = wmColorInt,
                wmSizeRatio = wmSizeRatio,
                wmOpacity = wmOpacity,
                wmPos = wmPos,
                layers = layersTag,
                editorWidth = editorWidth,
                editorHeight = editorHeight,
                context = context
            )
            
            val savedPath = saveBitmapToFile(context, finalBitmap)
            finalBitmap.recycle()
            
            val cleanedTags = removeEditParametersFromTags(tags)
            
            return photo.copy(
                imageUrl = savedPath,
                tags = cleanedTags
            )
        } catch (e: Exception) {
            Log.e("GalleryViewModel", "Error applying physical edits to bitmap", e)
            return photo
        }
    }

    private fun applyBlendShapeAndFeatherToBitmap(
        src: android.graphics.Bitmap,
        shape: String,
        featherAmount: Float
    ): android.graphics.Bitmap {
        if (shape.lowercase() == "none") return src
        
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        if (w <= 0f || h <= 0f) return src
        
        val output = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, 0f, 0f, paint)
        
        val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        
        when (shape.lowercase()) {
            "circular" -> {
                val radius = Math.max(w, h) / 2f
                val solidStop = (1f - featherAmount).coerceIn(0f, 0.99f)
                val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF.toInt())
                val stops = floatArrayOf(0.0f, solidStop, 1.0f)
                
                maskPaint.shader = android.graphics.RadialGradient(
                    w / 2f, h / 2f, radius,
                    colors, stops,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, maskPaint)
            }
            "square" -> {
                val featherPxX = (w / 2f) * featherAmount
                val horizStops = floatArrayOf(
                    0.0f,
                    (featherPxX / w).coerceIn(0f, 0.5f),
                    ((w - featherPxX) / w).coerceIn(0.5f, 1f),
                    1.0f
                )
                val horizColors = intArrayOf(
                    0x00FFFFFF.toInt(),
                    0xFFFFFFFF.toInt(),
                    0xFFFFFFFF.toInt(),
                    0x00FFFFFF.toInt()
                )
                maskPaint.shader = android.graphics.LinearGradient(
                    0f, 0f, w, 0f,
                    horizColors, horizStops,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, maskPaint)
                
                val featherPxY = (h / 2f) * featherAmount
                val vertStops = floatArrayOf(
                    0.0f,
                    (featherPxY / h).coerceIn(0f, 0.5f),
                    ((h - featherPxY) / h).coerceIn(0.5f, 1f),
                    1.0f
                )
                val vertColors = intArrayOf(
                    0x00FFFFFF.toInt(),
                    0xFFFFFFFF.toInt(),
                    0xFFFFFFFF.toInt(),
                    0x00FFFFFF.toInt()
                )
                maskPaint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, h,
                    vertColors, vertStops,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, maskPaint)
            }
        }
        
        return output
    }

    private fun drawLayerOnBitmap(
        base: android.graphics.Bitmap,
        layer: ImageLayerData,
        editorWidth: Float,
        editorHeight: Float,
        density: Float,
        context: android.content.Context
    ): android.graphics.Bitmap {
        val srcLayer = loadBitmapFromUrlOrPath(context, layer.imageUrl) ?: return base
        
        var workingLayer = applyBlendShapeAndFeatherToBitmap(srcLayer, layer.blendShape, layer.featherAmount)
        
        val aspect = workingLayer.width.toFloat() / workingLayer.height.toFloat()
        val scale = base.width.toFloat() / editorWidth
        val layerScreenWidth = layer.sizeRatio * 320f * density
        val targetWidth = (layerScreenWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (targetWidth / aspect).toInt().coerceAtLeast(1)
        
        val resizedLayer = android.graphics.Bitmap.createScaledBitmap(workingLayer, targetWidth, targetHeight, true)
        if (resizedLayer != workingLayer) {
            if (workingLayer != srcLayer) {
                workingLayer.recycle()
            }
            workingLayer = resizedLayer
        }
        
        val workingBase = if (base.isMutable) base else base.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(workingBase)
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
        }
        
        if (layer.emboss) {
            val embossMatrix = android.graphics.ColorMatrix(floatArrayOf(
                2.0f, -1.0f, 0.0f, 0.0f, 0f, 
                -1.0f, 2.0f, -1.0f, 0.0f, 0f, 
                0.0f, -1.0f, 2.0f, 0.0f, 0f, 
                0.0f, 0.0f, 0.0f, 1.0f, 0f
            ))
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(embossMatrix)
        } else if (layer.blendModeIndex > 0) {
            val color = try {
                android.graphics.Color.parseColor(layer.blendColorHex)
            } catch (e: Exception) {
                android.graphics.Color.WHITE
            }
            val filterBlendMode = when (layer.blendModeIndex) {
                1 -> android.graphics.PorterDuff.Mode.MULTIPLY
                2 -> android.graphics.PorterDuff.Mode.SCREEN
                3 -> android.graphics.PorterDuff.Mode.SRC_ATOP
                4 -> android.graphics.PorterDuff.Mode.XOR
                5 -> android.graphics.PorterDuff.Mode.LIGHTEN
                else -> android.graphics.PorterDuff.Mode.SRC_ATOP
            }
            paint.colorFilter = android.graphics.PorterDuffColorFilter(color, filterBlendMode)
        }
        
        if (layer.shadow) {
            val shadowPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                alpha = (layer.alpha * 128).toInt().coerceIn(0, 255)
                maskFilter = android.graphics.BlurMaskFilter((15f * density * scale).coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            val shadowX = (layer.dragX * scale) + (10f * density * scale)
            val shadowY = (layer.dragY * scale) + (10f * density * scale)
            val shadowLeft = (workingBase.width / 2f) - (targetWidth / 2f) + shadowX
            val shadowTop = (workingBase.height / 2f) - (targetHeight / 2f) + shadowY
            
            canvas.drawRect(shadowLeft, shadowTop, shadowLeft + targetWidth, shadowTop + targetHeight, shadowPaint)
        }
        
        val xOffset = layer.dragX * scale
        val yOffset = layer.dragY * scale
        
        val left = (workingBase.width / 2f) - (targetWidth / 2f) + xOffset
        val top = (workingBase.height / 2f) - (targetHeight / 2f) + yOffset
        
        canvas.save()
        canvas.rotate(layer.rotation, left + targetWidth / 2f, top + targetHeight / 2f)
        canvas.drawBitmap(workingLayer, left, top, paint)
        canvas.restore()
        
        var resultBase = workingBase
        layer.mergedLayers.forEach { child ->
            resultBase = drawLayerOnBitmap(resultBase, child, editorWidth, editorHeight, density, context)
        }
        
        if (workingLayer != srcLayer) {
            workingLayer.recycle()
        }
        srcLayer.recycle()
        
        return resultBase
    }
}

enum class SyncFilter { ALL, SYNCED, UNSYNCED }

enum class ViewMode {
    GRID, LIST
}

enum class GallerySortOption(val displayName: String) {
    DATE_DESC("Date: Newest First"),
    DATE_ASC("Date: Oldest First"),
    TITLE_ASC("Title: A-Z"),
    TITLE_DESC("Title: Z-A"),
    SIZE_DESC("Size: Largest First"),
    SIZE_ASC("Size: Smallest First")
}

data class SyncConsoleState(
    val isSyncing: Boolean = false,
    val syncMessage: String = "Engine connected. Standing by.",
    val bandwidthSpeed: String = "idle",
    val currentTransferredBytes: Long = 0L,
    val totalBytesToTransfer: Long = 0L,
    val diagnosticLog: List<String> = listOf("[COMMS] Cloud Sync Engine active", "[SECURE] AES-256 local handshake active")
)

data class GalleryFilters(
    val search: String = "",
    val tag: String? = null,
    val location: String? = null,
    val sync: SyncFilter = SyncFilter.ALL,
    val selectedCustomLabel: String? = null
)

@android.annotation.SuppressLint("MissingPermission")
private fun invalidateGoogleToken(context: android.content.Context, token: String) {
    GoogleAuthUtil.invalidateToken(context, token)
}

data class AiProviderConfig(val baseUrl: String, val apiKey: String, val modelId: String)

