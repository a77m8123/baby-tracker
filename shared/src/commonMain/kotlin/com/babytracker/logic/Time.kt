package com.babytracker.logic

import kotlin.random.Random
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE
const val DAY = 24 * HOUR

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

fun newId(): String = nowMs().toString(36) + Random.nextInt(0, 36 * 36 * 36).toString(36).padStart(3, '0')

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

fun localDateTime(ms: Long, tz: TimeZone = zone): LocalDateTime =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)

fun localDate(ms: Long, tz: TimeZone = zone): LocalDate = localDateTime(ms, tz).date

fun startOfDay(date: LocalDate, tz: TimeZone = zone): Long = date.atStartOfDayIn(tz).toEpochMilliseconds()

fun startOfDay(ms: Long, tz: TimeZone = zone): Long = startOfDay(localDate(ms, tz), tz)

fun endOfDay(date: LocalDate, tz: TimeZone = zone): Long = startOfDay(date.plus(1, DateTimeUnit.DAY), tz)

fun at(date: LocalDate, hour: Int, minute: Int, tz: TimeZone = zone): Long =
    date.atTime(LocalTime(hour, minute)).toInstant(tz).toEpochMilliseconds()

fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)

fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)

/** Возраст в днях на момент [now] (0, если дата рождения не задана или в будущем). */
fun ageDays(birthDate: Long, now: Long): Int =
    if (birthDate <= 0L || now < birthDate) 0 else localDate(birthDate).daysUntil(localDate(now))

/** Возраст в месяцах (дробный: 30.4375 дня в месяце) — для норм ВОЗ и окон бодрствования. */
fun ageMonths(birthDate: Long, now: Long): Double = ageDays(birthDate, now) / 30.4375

fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** «07:05» */
fun clock(ms: Long): String {
    val t = localDateTime(ms)
    return "${pad2(t.hour)}:${pad2(t.minute)}"
}

/** «2 ч 30 мин» */
fun duration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    return when {
        h > 0 && r > 0 -> "$h ч $r мин"
        h > 0 -> "$h ч"
        else -> "$r мин"
    }
}

fun durationMs(ms: Long): String = duration((ms / MINUTE).toInt())

/** Секундомер: «4:07» или «1:04:07». */
fun timer(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
}

/** «25 мин назад» / «через 1 ч» / «только что» */
fun relative(ts: Long, now: Long): String {
    val diff = now - ts
    val minutes = (kotlin.math.abs(diff) / MINUTE).toInt()
    if (minutes == 0) return "только что"
    val text = duration(minutes)
    return if (diff >= 0) "$text назад" else "через $text"
}

private val monthsGenitive = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val monthsShort = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val weekdaysShort = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

/** «5 ноября» */
fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${monthsGenitive[date.monthNumber - 1]}"

/** «5 ноя» */
fun dayMonthShort(date: LocalDate): String = "${date.dayOfMonth} ${monthsShort[date.monthNumber - 1]}"

fun weekdayShort(date: LocalDate): String = weekdaysShort[date.dayOfWeek.ordinal]

/** «05.11.2026» */
fun dateNumeric(date: LocalDate): String = "${pad2(date.dayOfMonth)}.${pad2(date.monthNumber)}.${date.year}"

/** «Сегодня», «Вчера», «Завтра» или «5 ноября, чт». */
fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    today.plusDays(1) -> "Завтра"
    else -> {
        val base = "${dayMonth(date)}, ${weekdayShort(date)}"
        if (date.year != today.year) "${dayMonth(date)} ${date.year}" else base
    }
}

/** «2 месяца 5 дней», «1 год 3 месяца» */
fun ageText(days: Int): String = when {
    days < 0 -> "не указан"
    days < 31 -> "$days ${plural(days, "день", "дня", "дней")}"
    days < 365 -> {
        val m = (days / 30.4375).toInt()
        val d = days - (m * 30.4375).toInt()
        val months = "$m ${plural(m, "месяц", "месяца", "месяцев")}"
        if (d > 0) "$months $d ${plural(d, "день", "дня", "дней")}" else months
    }
    else -> {
        val y = days / 365
        val m = ((days % 365) / 30.4375).toInt()
        val years = "$y ${plural(y, "год", "года", "лет")}"
        if (m == 0) years else "$years $m ${plural(m, "месяц", "месяца", "месяцев")}"
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    if (mod100 in 11..14) return many
    return when (n % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}

/** «3,2» — один знак после запятой, без лишнего нуля. */
fun oneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    val whole = tenths / 10
    val frac = kotlin.math.abs(tenths % 10)
    return if (frac == 0L) "$whole" else "$whole,$frac"
}

/** «4 250 г» → «4,25 кг» */
fun weightText(grams: Int): String {
    if (grams < 1000) return "$grams г"
    val kg = grams / 1000.0
    val hundredths = kotlin.math.round(kg * 100).toLong()
    val whole = hundredths / 100
    val frac = hundredths % 100
    val fracText = when {
        frac == 0L -> ""
        frac % 10 == 0L -> ",${frac / 10}"
        else -> "," + frac.toString().padStart(2, '0')
    }
    return "$whole$fracText кг"
}

/** Разбор «3,5» / «3.5». */
fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
