package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
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

    @Query("DELETE FROM photos WHERE isDeleted = 1 AND deletedTimestamp > 0 AND deletedTimestamp <= :cutoff")
    suspend fun permanentlyDeleteDeletedBefore(cutoff: Long): Int

    @Query("SELECT * FROM photos WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedPhotos(): List<Photo>

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

    companion object {
        const val DATABASE_NAME = "emreview.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
    }
}
