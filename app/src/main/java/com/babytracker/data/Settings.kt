package com.babytracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("baby_settings")

/**
 * Настройки ребёнка и приложения.
 * [birthDate] — дата рождения в миллисекундах epoch.
 */
data class BabySettings(
    val name: String = "Малыш",
    val birthDate: Long = 0L,
    /** Целевой сон в сутки, минуты. 0 = брать норму по возрасту. */
    val sleepGoalMinutes: Int = 0,
    /** Интервал кормления, минуты. */
    val feedIntervalMinutes: Int = 180,
    /** Длительность цикла сна для прогноза пробуждения, минуты. */
    val sleepCycleMinutes: Int = 75,
    /** Оценка объёма бутылочки по умолчанию, мл. */
    val defaultBottleMl: Int = 90,
    /** Автовыключение звука, минуты. 0 = не выключать. */
    val soundTimerMinutes: Int = 30,
    val soundsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
) {
    /** Возраст в днях (0, если дата рождения не задана). */
    val ageDays: Int get() = ageDaysAt(System.currentTimeMillis())

    val ageMonths: Int get() = ageMonthsAt(System.currentTimeMillis())

    /** Возраст в днях на момент [now]. Вынесено, чтобы можно было тестировать. */
    fun ageDaysAt(now: Long): Int =
        if (birthDate <= 0L || now < birthDate) 0
        else ((now - birthDate) / 86_400_000L).toInt()

    fun ageMonthsAt(now: Long): Int = ageDaysAt(now) / 30
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val BIRTH = longPreferencesKey("birth_date")
        val SLEEP_GOAL = intPreferencesKey("sleep_goal_minutes")
        val FEED_INTERVAL = intPreferencesKey("feed_interval_minutes")
        val SLEEP_CYCLE = intPreferencesKey("sleep_cycle_minutes")
        val BOTTLE_ML = intPreferencesKey("default_bottle_ml")
        val SOUND_TIMER = intPreferencesKey("sound_timer_minutes")
        val SOUNDS_ON = booleanPreferencesKey("sounds_enabled")
        val NOTIF_ON = booleanPreferencesKey("notifications_enabled")
    }

    val settings: Flow<BabySettings> = context.dataStore.data.map { p: Preferences ->
        BabySettings(
            name = p[Keys.NAME] ?: "Малыш",
            birthDate = p[Keys.BIRTH] ?: 0L,
            sleepGoalMinutes = p[Keys.SLEEP_GOAL] ?: 0,
            feedIntervalMinutes = p[Keys.FEED_INTERVAL] ?: 180,
            sleepCycleMinutes = p[Keys.SLEEP_CYCLE] ?: 75,
            defaultBottleMl = p[Keys.BOTTLE_ML] ?: 90,
            soundTimerMinutes = p[Keys.SOUND_TIMER] ?: 30,
            soundsEnabled = p[Keys.SOUNDS_ON] ?: true,
            notificationsEnabled = p[Keys.NOTIF_ON] ?: true,
        )
    }

    suspend fun setName(v: String) = context.dataStore.edit { it[Keys.NAME] = v }
    suspend fun setBirthDate(v: Long) = context.dataStore.edit { it[Keys.BIRTH] = v }
    suspend fun setSleepGoal(v: Int) = context.dataStore.edit { it[Keys.SLEEP_GOAL] = v }
    suspend fun setFeedInterval(v: Int) = context.dataStore.edit { it[Keys.FEED_INTERVAL] = v }
    suspend fun setSleepCycle(v: Int) = context.dataStore.edit { it[Keys.SLEEP_CYCLE] = v }
    suspend fun setDefaultBottleMl(v: Int) = context.dataStore.edit { it[Keys.BOTTLE_ML] = v }
    suspend fun setSoundTimer(v: Int) = context.dataStore.edit { it[Keys.SOUND_TIMER] = v }
    suspend fun setSoundsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SOUNDS_ON] = v }
    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.NOTIF_ON] = v }
}
