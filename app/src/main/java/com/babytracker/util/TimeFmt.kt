package com.babytracker.util

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFmt {

    fun clock(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return String.format(
            Locale.US, "%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    /** «2 ч 30 мин» */
    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            else -> "$m мин"
        }
    }

    /** «через 25 мин» / «3 мин назад» */
    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - ts
        val abs = TimeUnit.MILLISECONDS.toMinutes(kotlin.math.abs(diff)).toInt()
        val text = duration(abs)
        return if (diff >= 0) "$text назад" else "через $text"
    }

    fun startOfToday(ts: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    /** Возраст: «2 месяца» */
    fun ageText(days: Int): String = when {
        days < 0 -> "не указан"
        days < 31 -> "$days ${plural(days, "день", "дня", "дней")}"
        days < 365 -> {
            val m = days / 30
            "$m ${plural(m, "месяц", "месяца", "месяцев")}"
        }
        else -> {
            val y = days / 365
            val m = (days % 365) / 30
            if (m == 0) "$y ${plural(y, "год", "года", "лет")}"
            else "$y ${plural(y, "год", "года", "лет")} $m ${plural(m, "месяц", "месяца", "месяцев")}"
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
}
