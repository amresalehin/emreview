package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    fun getAllPhotos(): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Int): Photo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo): Long

    @Update
    suspend fun updatePhoto(photo: Photo)

    @Update
    suspend fun updatePhotos(photos: List<Photo>)

    @Delete
    suspend fun deletePhoto(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoById(id: Int)

    // System Configurations Key Value store
    @Query("SELECT * FROM app_config WHERE configKey = :key")
    suspend fun getConfig(key: String): KeyValueEntry?

    @Query("SELECT * FROM app_config WHERE configKey = :key")
    fun getConfigFlow(key: String): Flow<KeyValueEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(entry: KeyValueEntry)
}

@Database(entities = [Photo::class, KeyValueEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}
