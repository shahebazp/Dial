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

@Entity(tableName = "call_log_entries")
data class CallLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val timestamp: Long,
    val duration: Long,
    val callType: String,
    val contactName: String,
    val simLabel: String = "SIM 1"
)

@Entity(tableName = "speed_dial_slots")
data class SpeedDialEntity(
    @PrimaryKey val digit: Int,
    val contactName: String,
    val phoneNumber: String
)

@Entity(tableName = "blocked_numbers_local")
data class BlockedNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val addedTimestampMs: Long
)

data class CallLogKeyTuple(
    val phoneNumber: String,
    val timestamp: Long
)

@Dao
interface DialDao {
    @Query("SELECT * FROM call_log_entries ORDER BY timestamp DESC")
    fun observeCallLogEntries(): Flow<List<CallLogEntryEntity>>

    @Query("SELECT * FROM call_log_entries ORDER BY timestamp DESC")
    suspend fun getAllCallLogEntries(): List<CallLogEntryEntity>

    @Query("SELECT phoneNumber, timestamp FROM call_log_entries")
    suspend fun getExistingCallKeys(): List<CallLogKeyTuple>

    @Query("SELECT * FROM call_log_entries WHERE phoneNumber = :phoneNumber AND ABS(timestamp - :timestamp) < 3000 LIMIT 1")
    suspend fun findDuplicateCallLog(phoneNumber: String, timestamp: Long): CallLogEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogEntry(entry: CallLogEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogEntries(entries: List<CallLogEntryEntity>)

    @Query("DELETE FROM call_log_entries WHERE id IN (:ids)")
    suspend fun deleteCallLogEntriesByIds(ids: List<Long>)

    @Query("SELECT * FROM speed_dial_slots ORDER BY digit ASC")
    fun observeSpeedDialSlots(): Flow<List<SpeedDialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpeedDialSlot(slot: SpeedDialEntity)

    @Query("SELECT * FROM blocked_numbers_local ORDER BY addedTimestampMs DESC")
    fun observeBlockedNumbers(): Flow<List<BlockedNumberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumber(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers_local WHERE phoneNumber = :number")
    suspend fun removeBlockedNumber(number: String)
}

@Database(
    entities = [
        CallLogEntryEntity::class,
        SpeedDialEntity::class,
        BlockedNumberEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DialDatabase : RoomDatabase() {
    abstract fun dialDao(): DialDao

    companion object {
        @Volatile
        private var INSTANCE: DialDatabase? = null

        fun getDatabase(context: Context): DialDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DialDatabase::class.java,
                    "dial_nothing_phone_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
