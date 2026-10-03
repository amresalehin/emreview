package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imageUrl: String,          // Web link, local asset pathway, or storage uri
    val title: String,
    val description: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val location: String = "Unknown Location",
    val isSynced: Boolean = false,
    val isLocked: Boolean = false,
    val tags: String = "",          // Comma-separated list of tags
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = ""
)
