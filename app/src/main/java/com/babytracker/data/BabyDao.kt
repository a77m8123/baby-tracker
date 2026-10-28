package com.babytracker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {

    @Insert
    suspend fun insertFeeding(f: FeedingEntity): Long

    @Insert
    suspend fun insertSleep(s: SleepEntity): Long

    @Insert
    suspend fun insertDiaper(d: DiaperEntity): Long

    @Query("SELECT * FROM feedings ORDER BY timestamp DESC")
    fun feedings(): Flow<List<FeedingEntity>>

    @Query("SELECT * FROM sleeps ORDER BY start DESC")
    fun sleeps(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM diapers ORDER BY timestamp DESC")
    fun diapers(): Flow<List<DiaperEntity>>

    /** Текущая (незакрытая) сессия сна, если малыш спит. */
    @Query("SELECT * FROM sleeps WHERE end IS NULL ORDER BY start DESC LIMIT 1")
    fun activeSleep(): Flow<SleepEntity?>

    @Query("SELECT * FROM sleeps WHERE end IS NULL ORDER BY start DESC LIMIT 1")
    suspend fun activeSleepOnce(): SleepEntity?

    @Query("UPDATE sleeps SET end = :end WHERE id = :id")
    suspend fun closeSleep(id: Long, end: Long)

    @Query("SELECT * FROM sleeps WHERE end IS NOT NULL AND start >= :since")
    suspend fun finishedSleepsSince(since: Long): List<SleepEntity>

    @Query("SELECT * FROM feedings WHERE timestamp >= :since")
    suspend fun feedingsSince(since: Long): List<FeedingEntity>

    @Query("DELETE FROM feedings")
    suspend fun clearFeedings()

    @Query("DELETE FROM sleeps")
    suspend fun clearSleeps()
}

@Database(
    entities = [FeedingEntity::class, SleepEntity::class, DiaperEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BabyDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
}
