package com.babytracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.dao
import com.babytracker.data.BabySettings
import com.babytracker.data.DiaperEntity
import com.babytracker.data.FeedingEntity
import com.babytracker.data.FeedType
import com.babytracker.data.SleepNorm
import com.babytracker.data.SleepEntity
import com.babytracker.settingsStore
import com.babytracker.util.TimeFmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Событие ленты: сон, кормление, подгузник. */
sealed interface FeedEvent {
    val timestamp: Long

    data class Sleep(val entity: SleepEntity) : FeedEvent {
        override val timestamp: Long get() = entity.start
    }

    data class Feed(val entity: FeedingEntity) : FeedEvent {
        override val timestamp: Long get() = entity.timestamp
    }

    data class Diaper(val entity: DiaperEntity) : FeedEvent {
        override val timestamp: Long get() = entity.timestamp
    }
}

data class MainUiState(
    val settings: BabySettings = BabySettings(),
    val isSleeping: Boolean = false,
    val sleepStartedAt: Long? = null,
    val currentSleepMinutes: Int = 0,
    val todaySleepMinutes: Int = 0,
    val sleepGoalMinutes: Int = 0,
    val wakeUpInMinutes: Int = 0,
    val lastFeedAt: Long? = null,
    val lastFeedType: FeedType? = null,
    val nextFeedAt: Long? = null,
    val feedIntervalMinutes: Int = 180,
    val feedsToday: Int = 0,
    val feedAmountTodayMl: Int = 0,
    val diapersToday: Int = 0,
    val events: List<FeedEvent> = emptyList(),
    val loaded: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = app.dao
    private val store = app.settingsStore

    val state: StateFlow<MainUiState> = combine(
        store.settings,
        dao.activeSleep(),
        dao.feedings(),
        dao.sleeps(),
        dao.diapers(),
    ) { settings, activeSleep, feedings, sleeps, diapers ->
        val now = System.currentTimeMillis()
        val startOfDay = TimeFmt.startOfToday(now)

        val finishedToday = sleeps.filter { it.end != null && it.start >= startOfDay }
            .sumOf { (it.end!! - it.start) / 60_000L }.toInt()
        val activeMinutes = activeSleep?.let { ((now - it.start) / 60_000L).toInt() } ?: 0

        val goal = SleepNorm.targetMinutes(settings)
        val todayFeeds = feedings.filter { it.timestamp >= startOfDay }

        val events = buildList {
            sleeps.forEach { add(FeedEvent.Sleep(it)) }
            feedings.forEach { add(FeedEvent.Feed(it)) }
            diapers.forEach { add(FeedEvent.Diaper(it)) }
        }.sortedByDescending { it.timestamp }

        MainUiState(
            settings = settings,
            isSleeping = activeSleep != null,
            sleepStartedAt = activeSleep?.start,
            currentSleepMinutes = activeMinutes,
            todaySleepMinutes = finishedToday + activeMinutes,
            sleepGoalMinutes = goal,
            wakeUpInMinutes = activeSleep?.let {
                SleepNorm.wakeUpInMinutes(it.start, now, settings)
            } ?: 0,
            lastFeedAt = feedings.firstOrNull()?.timestamp,
            lastFeedType = feedings.firstOrNull()?.type,
            nextFeedAt = feedings.firstOrNull()?.let { it.timestamp + settings.feedIntervalMinutes * 60_000L },
            feedIntervalMinutes = settings.feedIntervalMinutes,
            feedsToday = todayFeeds.size,
            feedAmountTodayMl = todayFeeds.sumOf { it.amountMl ?: 0 },
            diapersToday = diapers.count { it.timestamp >= startOfDay },
            events = events.take(60),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun logFeeding(type: FeedType, amountMl: Int? = null) {
        viewModelScope.launch {
            dao.insertFeeding(
                FeedingEntity(
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    amountMl = amountMl,
                )
            )
        }
    }

    fun logDiaper(withStool: Boolean) {
        viewModelScope.launch {
            dao.insertDiaper(DiaperEntity(timestamp = System.currentTimeMillis(), withStool = withStool))
        }
    }

    fun startSleep() {
        viewModelScope.launch {
            dao.insertSleep(SleepEntity(start = System.currentTimeMillis()))
        }
    }

    fun setName(v: String) { viewModelScope.launch { store.setName(v) } }
    fun setBirthDate(v: Long) { viewModelScope.launch { store.setBirthDate(v) } }
    fun setSleepGoal(v: Int) { viewModelScope.launch { store.setSleepGoal(v) } }
    fun setFeedInterval(v: Int) { viewModelScope.launch { store.setFeedInterval(v) } }
    fun setSleepCycle(v: Int) { viewModelScope.launch { store.setSleepCycle(v) } }
    fun setDefaultBottleMl(v: Int) { viewModelScope.launch { store.setDefaultBottleMl(v) } }
    fun setSoundTimer(v: Int) { viewModelScope.launch { store.setSoundTimer(v) } }
    fun setNotificationsEnabled(v: Boolean) { viewModelScope.launch { store.setNotificationsEnabled(v) } }

    fun wakeUp() {
        viewModelScope.launch {
            val active = dao.activeSleepOnce() ?: return@launch
            dao.closeSleep(active.id, System.currentTimeMillis())
        }
    }

    suspend fun activeSleepEntity(): SleepEntity? = dao.activeSleepOnce()
}
