package com.babytracker.logic

import com.babytracker.model.AppData
import kotlinx.serialization.Serializable

/** Одно уведомление. [id] стабилен для одного и того же повода — так его проще заменить. */
data class Reminder(val id: Int, val atMs: Long, val title: String, val text: String)

object ReminderPlanner {
    const val ID_FEED = 1
    const val ID_SLEEP = 2
    private const val ID_MED_BASE = 100
    private const val ID_VACCINE_BASE = 500

    /** Все будущие напоминания по текущим данным. Платформа заменяет ими запланированные. */
    fun plan(data: AppData, now: Long): List<Reminder> {
        val s = data.settings
        val name = s.name
        val out = mutableListOf<Reminder>()

        if (s.remindFeeding) {
            nextFeedAt(data)?.takeIf { it > now }?.let {
                out += Reminder(ID_FEED, it, "Пора кормить", "$name: прошло ${duration(s.feedIntervalMinutes)} с прошлого кормления")
            }
        }

        if (s.remindSleep) {
            val state = wakeState(data, now)
            if (state is WakeState.Awake && state.windowStart != null && state.windowStart > now) {
                out += Reminder(ID_SLEEP, state.windowStart, "Пора укладывать", "$name не спит уже ${durationMs(state.windowStart - (state.since ?: now))}")
            }
        }

        if (s.remindMeds) {
            data.medicines.filter { it.remind && !it.archived && it.intervalHours > 0 }.forEachIndexed { i, med ->
                val last = data.doses.filter { it.medId == med.id }.maxOfOrNull { it.time } ?: return@forEachIndexed
                val at = last + med.intervalHours * HOUR
                if (at > now) out += Reminder(ID_MED_BASE + i, at, med.name, "Прошло ${med.intervalHours} ч с прошлого приёма")
            }
        }

        if (s.remindVaccines) {
            data.vaccines.filter { it.doneAt == null }.forEachIndexed { i, v ->
                // накануне в 19:00
                val at = at(localDate(v.plannedAt).minusDays(1), 19, 0)
                if (at > now && i < 60) out += Reminder(ID_VACCINE_BASE + i, at, "Завтра прививка", v.name)
            }
        }
        return out
    }
}

/** Что показывать на виджете и в Live Activity. Уходит в Swift как JSON. */
@Serializable
data class WidgetStatus(
    val name: String,
    val sleeping: Boolean,
    /** Начало сна или момент пробуждения. */
    val sinceMs: Long? = null,
    val lastFeedMs: Long? = null,
    val nextFeedMs: Long? = null,
    val feeding: Boolean = false,
    val feedStartedMs: Long? = null,
    val feedSide: String? = null,
    val liveActivity: Boolean = true,
) {
    companion object {
        fun from(data: AppData, now: Long): WidgetStatus {
            val state = wakeState(data, now)
            val feed = data.activeFeed
            return WidgetStatus(
                name = data.settings.name,
                sleeping = state is WakeState.Sleeping,
                sinceMs = when (state) {
                    is WakeState.Sleeping -> state.since
                    is WakeState.Awake -> state.since
                },
                lastFeedMs = lastFeeding(data)?.time,
                nextFeedMs = nextFeedAt(data),
                feeding = feed != null,
                // для живого секундомера: момент, от которого отсчитывать общее время кормления
                feedStartedMs = feed?.let { now - it.totalMs(now) },
                feedSide = feed?.side?.let(::sideName),
                liveActivity = data.settings.liveActivity,
            )
        }
    }
}
