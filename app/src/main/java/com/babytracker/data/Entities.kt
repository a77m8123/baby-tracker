package com.babytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тип события в ленте. */
enum class FeedType { BREAST, BOTTLE, SOLIDS }

/** Событие ленты: кормление. */
@Entity(tableName = "feedings")
data class FeedingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: FeedType,
    val amountMl: Int? = null,
    val note: String? = null,
)

/** Сессия сна. */
@Entity(tableName = "sleeps")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long? = null,
)

/** Смена подгузника. */
@Entity(tableName = "diapers")
data class DiaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val withStool: Boolean = false,
)
