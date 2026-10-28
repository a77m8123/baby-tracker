package com.babytracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.dao
import com.babytracker.util.TimeFmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class DayStat(
    val dayStart: Long,
    val sleepMinutes: Int,
    val feedCount: Int,
    val feedMl: Int,
    val sleepSegments: Int,
)

data class StatsUiState(
    val days: List<DayStat> = emptyList(),
    val avgSleepMinutes: Int = 0,
    val avgFeeds: Double = 0.0,
    val loaded: Boolean = false,
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = app.dao
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    fun load() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val days = mutableListOf<DayStat>()
            for (i in 6 downTo 0) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = c.timeInMillis
                val dayEnd = dayStart + 86_400_000L

                val sleeps = dao.finishedSleepsSince(dayStart).filter { it.end!! < dayEnd }
                val feeds = dao.feedingsSince(dayStart).filter { it.timestamp < dayEnd }

                val sleepMinutes = sleeps.sumOf { (it.end!! - it.start) / 60_000L }.toInt()
                days += DayStat(
                    dayStart = dayStart,
                    sleepMinutes = sleepMinutes,
                    feedCount = feeds.size,
                    feedMl = feeds.sumOf { it.amountMl ?: 0 },
                    sleepSegments = sleeps.size,
                )
            }
            _state.value = StatsUiState(
                days = days,
                avgSleepMinutes = if (days.isEmpty()) 0 else days.sumOf { it.sleepMinutes } / days.size,
                avgFeeds = if (days.isEmpty()) 0.0 else days.sumOf { it.feedCount }.toDouble() / days.size,
                loaded = true,
            )
        }
    }

    companion object {
        fun weekday(ts: Long): String {
            val names = listOf("вс", "пн", "вт", "ср", "чт", "пт", "сб")
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return names[c.get(Calendar.DAY_OF_WEEK) - 1]
        }

        fun dayOfMonth(ts: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.DAY_OF_MONTH).toString()
        }
    }
}
