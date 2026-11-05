package com.babytracker.model

import kotlinx.serialization.Serializable

@Serializable
enum class Sex { UNKNOWN, BOY, GIRL }

@Serializable
enum class FeedType { BREAST, BOTTLE, SOLIDS }

@Serializable
enum class Side { LEFT, RIGHT }

/** Что в бутылочке. */
@Serializable
enum class Milk { FORMULA, BREAST_MILK }

@Serializable
enum class DiaperKind { WET, DIRTY, MIXED }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Ночной режим — тёмно-красная тема, чтобы не слепить ночью. AUTO — с 21:00 до 7:00. */
@Serializable
enum class NightMode { OFF, ON, AUTO }

@Serializable
data class Settings(
    val name: String = "Малыш",
    /** Дата рождения, epoch ms (полночь по местному времени). 0 — не указана. */
    val birthDate: Long = 0L,
    val sex: Sex = Sex.UNKNOWN,
    /** Цель сна в сутки, минуты. 0 — норма по возрасту. */
    val sleepGoalMinutes: Int = 0,
    val feedIntervalMinutes: Int = 180,
    val defaultBottleMl: Int = 90,
    /** Автовыключение звука, минуты. 0 — не выключать. */
    val soundTimerMinutes: Int = 30,
    val volume: Float = 0.7f,
    val remindFeeding: Boolean = true,
    val remindSleep: Boolean = true,
    val remindMeds: Boolean = true,
    val remindVaccines: Boolean = true,
    /** iOS: таймер сна и кормления на экране блокировки и в Dynamic Island. */
    val liveActivity: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val night: NightMode = NightMode.AUTO,
)

@Serializable
data class Sleep(val id: String, val start: Long, val end: Long? = null)

@Serializable
data class Feeding(
    val id: String,
    val time: Long,
    val type: FeedType,
    /** Грудь: сколько секунд на каждой стороне и на какой закончили. */
    val leftSec: Int = 0,
    val rightSec: Int = 0,
    val lastSide: Side? = null,
    /** Бутылочка. */
    val amountMl: Int? = null,
    val milk: Milk? = null,
    /** Прикорм: что ели. */
    val note: String = "",
)

@Serializable
data class Diaper(val id: String, val time: Long, val kind: DiaperKind)

@Serializable
data class Growth(
    val id: String,
    val time: Long,
    val weightG: Int? = null,
    val heightCm: Double? = null,
    val headCm: Double? = null,
)

@Serializable
data class Medicine(
    val id: String,
    val name: String,
    val dose: String = "",
    /** Минимальный интервал между приёмами, часы. 0 — без ограничения. */
    val intervalHours: Int = 0,
    /** Напомнить, когда пройдёт интервал. */
    val remind: Boolean = false,
    val archived: Boolean = false,
)

@Serializable
data class MedDose(val id: String, val medId: String, val time: Long)

@Serializable
data class Vaccine(val id: String, val name: String, val plannedAt: Long, val doneAt: Long? = null)

/** Идущее кормление грудью с секундомером. Переживает закрытие приложения. */
@Serializable
data class ActiveFeed(
    val startedAt: Long,
    val side: Side,
    /** Когда начался текущий отрезок на [side]; null — на паузе. */
    val segmentStart: Long?,
    val leftMs: Long = 0,
    val rightMs: Long = 0,
) {
    val paused: Boolean get() = segmentStart == null

    fun sideMs(s: Side, now: Long): Long {
        val base = if (s == Side.LEFT) leftMs else rightMs
        val running = if (s == side && segmentStart != null) (now - segmentStart).coerceAtLeast(0) else 0
        return base + running
    }

    fun totalMs(now: Long): Long = sideMs(Side.LEFT, now) + sideMs(Side.RIGHT, now)
}

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val sleeps: List<Sleep> = emptyList(),
    val feedings: List<Feeding> = emptyList(),
    val diapers: List<Diaper> = emptyList(),
    val growth: List<Growth> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val doses: List<MedDose> = emptyList(),
    val vaccines: List<Vaccine> = emptyList(),
    val activeFeed: ActiveFeed? = null,
) {
    val activeSleep: Sleep? get() = sleeps.filter { it.end == null }.maxByOrNull { it.start }
}
