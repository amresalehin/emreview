package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class KeyValueEntry(
    @PrimaryKey val configKey: String,
    val configValue: String
)
