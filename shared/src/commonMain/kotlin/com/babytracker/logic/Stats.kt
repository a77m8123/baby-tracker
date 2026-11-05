package com.babytracker.logic

import com.babytracker.model.AppData
import com.babytracker.model.Diaper
import com.babytracker.model.DiaperKind
import com.babytracker.model.FeedType
import com.babytracker.model.Feeding
import com.babytracker.model.Milk
import com.babytracker.model.Side
import com.babytracker.model.Sleep
import kotlinx.datetime.LocalDate

/** Сводка за сутки. Сон, переходящий через полночь, делится между днями. */
data class DaySummary(
    val date: LocalDate,
    val sleepMinutes: Int,
    val naps: Int,
    val feeds: Int,
    val bottleMl: Int,
    val breastMinutes: Int,
    val solids: Int,
    val diapers: Int,
    val wet: Int,
    val dirty: Int,
)

fun overlapMs(start: Long, end: Long, from: Long, to: Long): Long =
    (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)

fun daySummary(data: AppData, date: LocalDate, now: Long): DaySummary {
    val from = startOfDay(date)
    val to = endOfDay(date)
    val sleepMs = data.sleeps.sumOf { overlapMs(it.start, it.end ?: now, from, to) }
    val feeds = data.feedings.filter { it.time in from until to }
    val diapers = data.diapers.filter { it.time in from until to }
    return DaySummary(
        date = date,
        sleepMinutes = (sleepMs / MINUTE).toInt(),
        naps = data.sleeps.count { it.start in from until to },
        feeds = feeds.count { it.type != FeedType.SOLIDS },
        bottleMl = feeds.sumOf { it.amountMl ?: 0 },
        breastMinutes = feeds.sumOf { it.leftSec + it.rightSec } / 60,
        solids = feeds.count { it.type == FeedType.SOLIDS },
        diapers = diapers.size,
        wet = diapers.count { it.kind != DiaperKind.DIRTY },
        dirty = diapers.count { it.kind != DiaperKind.WET },
    )
}

/** Последние [days] суток, от старых к новым, включая сегодня. */
fun lastDays(data: AppData, days: Int, now: Long): List<DaySummary> {
    val today = localDate(now)
    return (days - 1 downTo 0).map { daySummary(data, today.minusDays(it), now) }
}

/** Состояние «бодрствует / спит» для главного экрана. */
sealed interface WakeState {
    data class Sleeping(val since: Long) : WakeState

    /**
     * [since] — когда проснулся (null, если снов ещё не было).
     * [windowStart]/[windowEnd] — когда укладывать по окну бодрствования.
     */
    data class Awake(val since: Long?, val windowStart: Long?, val windowEnd: Long?) : WakeState
}

enum class WindowPhase { EARLY, READY, OVERDUE }

fun wakeState(data: AppData, now: Long): WakeState {
    data.activeSleep?.let { return WakeState.Sleeping(it.start) }
    val lastEnd = data.sleeps.mapNotNull { it.end }.filter { it <= now }.maxOrNull()
        ?: return WakeState.Awake(null, null, null)
    val window = Norms.wakeWindow(ageMonths(data.settings.birthDate, now))
    return WakeState.Awake(lastEnd, lastEnd + window.min * MINUTE, lastEnd + window.max * MINUTE)
}

fun windowPhase(state: WakeState.Awake, now: Long): WindowPhase? = when {
    state.windowStart == null || state.windowEnd == null -> null
    now < state.windowStart -> WindowPhase.EARLY
    now <= state.windowEnd -> WindowPhase.READY
    else -> WindowPhase.OVERDUE
}

fun lastFeeding(data: AppData): Feeding? =
    data.feedings.filter { it.type != FeedType.SOLIDS }.maxByOrNull { it.time }

/** Когда следующее кормление: от начала последнего + интервал. */
fun nextFeedAt(data: AppData): Long? {
    if (data.activeFeed != null) return null
    val last = lastFeeding(data) ?: return null
    return last.time + data.settings.feedIntervalMinutes * MINUTE
}

/** С какой груди начинать: противоположная той, на которой закончили в прошлый раз. */
fun suggestedSide(data: AppData): Side? {
    val last = data.feedings.filter { it.type == FeedType.BREAST && it.lastSide != null }.maxByOrNull { it.time }
    return when (last?.lastSide) {
        Side.LEFT -> Side.RIGHT
        Side.RIGHT -> Side.LEFT
        null -> null
    }
}

fun sideName(side: Side): String = if (side == Side.LEFT) "левая" else "правая"

// Лента событий

sealed interface Event {
    val id: String
    val time: Long

    data class SleepEvent(val sleep: Sleep) : Event {
        override val id get() = sleep.id
        override val time get() = sleep.start
    }

    data class FeedEvent(val feeding: Feeding) : Event {
        override val id get() = feeding.id
        override val time get() = feeding.time
    }

    data class DiaperEvent(val diaper: Diaper) : Event {
        override val id get() = diaper.id
        override val time get() = diaper.time
    }
}

/** События, попадающие в сутки [date] (сон — если пересекает сутки), новые сверху. */
fun eventsOn(data: AppData, date: LocalDate, now: Long): List<Event> {
    val from = startOfDay(date)
    val to = endOfDay(date)
    val sleeps = data.sleeps.filter { overlapMs(it.start, it.end ?: now, from, to) > 0 || it.start in from until to }
    return (sleeps.map { Event.SleepEvent(it) } +
        data.feedings.filter { it.time in from until to }.map { Event.FeedEvent(it) } +
        data.diapers.filter { it.time in from until to }.map { Event.DiaperEvent(it) })
        .sortedByDescending { it.time }
}

fun recentEvents(data: AppData, limit: Int): List<Event> =
    (data.sleeps.map { Event.SleepEvent(it) } +
        data.feedings.map { Event.FeedEvent(it) } +
        data.diapers.map { Event.DiaperEvent(it) })
        .sortedByDescending { it.time }
        .take(limit)

fun feedingTitle(f: Feeding): String = when (f.type) {
    FeedType.BREAST -> "Грудь"
    FeedType.BOTTLE -> if (f.milk == Milk.BREAST_MILK) "Бутылочка · сцеженное" else "Бутылочка · смесь"
    FeedType.SOLIDS -> "Прикорм"
}

fun feedingDetails(f: Feeding): String = when (f.type) {
    FeedType.BREAST -> buildList {
        if (f.leftSec > 0) add("Л ${duration(roundMinutes(f.leftSec))}")
        if (f.rightSec > 0) add("П ${duration(roundMinutes(f.rightSec))}")
    }.joinToString(" · ").ifEmpty { "без таймера" }
    FeedType.BOTTLE -> "${f.amountMl ?: 0} мл"
    FeedType.SOLIDS -> f.note.ifBlank { "без описания" }
}

private fun roundMinutes(sec: Int): Int = if (sec in 1..59) 1 else (sec + 30) / 60

fun diaperTitle(kind: DiaperKind): String = when (kind) {
    DiaperKind.WET -> "Мокрый"
    DiaperKind.DIRTY -> "Грязный"
    DiaperKind.MIXED -> "Мокрый и грязный"
}
