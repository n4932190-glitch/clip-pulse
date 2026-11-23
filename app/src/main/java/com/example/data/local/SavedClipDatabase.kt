package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_clips")
data class SavedClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourcePlatform: String,
    val sourceUrl: String,
    val mediaStoreUri: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val viralityScore: Int,
    val subtitleStyleName: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface SavedClipDao {
    @Query("SELECT * FROM saved_clips ORDER BY createdAtMs DESC")
    fun observeAllClips(): Flow<List<SavedClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: SavedClipEntity): Long

    @Query("DELETE FROM saved_clips WHERE id = :id")
    suspend fun deleteClipById(id: Long)
}

@Database(entities = [SavedClipEntity::class], version = 1, exportSchema = false)
abstract class SavedClipDatabase : RoomDatabase() {
    abstract fun savedClipDao(): SavedClipDao

    companion object {
        @Volatile
        private var INSTANCE: SavedClipDatabase? = null

        fun getInstance(context: Context): SavedClipDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SavedClipDatabase::class.java,
                    "clippulse_saved.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class SavedClipRepository(private val dao: SavedClipDao) {
    val savedClips: Flow<List<SavedClipEntity>> = dao.observeAllClips()

    suspend fun recordSavedClip(clip: SavedClipEntity): Long = dao.insertClip(clip)

    suspend fun deleteClip(id: Long) = dao.deleteClipById(id)
}
