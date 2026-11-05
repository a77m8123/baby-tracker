package com.babytracker.logic

import com.babytracker.model.Settings
import com.babytracker.model.Sex

/** Общие ориентиры педиатров и ВОЗ — не медицинские рекомендации. */
object Norms {

    data class Range(val min: Int, val max: Int) {
        val mid: Int get() = (min + max) / 2
    }

    /** Сон в сутки, минуты. */
    fun sleepPerDay(months: Double): Range = when {
        months < 4 -> Range(14 * 60, 17 * 60)
        months < 6 -> Range(12 * 60, 16 * 60)
        months < 9 -> Range(12 * 60, 15 * 60)
        months < 12 -> Range(11 * 60, 14 * 60)
        months < 18 -> Range(10 * 60, 13 * 60)
        months < 24 -> Range(11 * 60, 14 * 60)
        else -> Range(10 * 60, 13 * 60)
    }

    fun sleepGoal(settings: Settings, now: Long): Int =
        if (settings.sleepGoalMinutes > 0) settings.sleepGoalMinutes
        else sleepPerDay(ageMonths(settings.birthDate, now)).mid

    /** Окно бодрствования — сколько малыш может не спать до следующего сна, минуты. */
    fun wakeWindow(months: Double): Range = when {
        months < 1 -> Range(35, 60)
        months < 3 -> Range(60, 90)
        months < 5 -> Range(75, 120)
        months < 7 -> Range(120, 150)
        months < 10 -> Range(150, 180)
        months < 13 -> Range(180, 240)
        months < 18 -> Range(240, 300)
        else -> Range(300, 360)
    }

    /** Коридор ВОЗ: −2 SD, медиана, +2 SD для месяцев 0…24. */
    class GrowthTable(val minus2: DoubleArray, val median: DoubleArray, val plus2: DoubleArray) {
        val lastMonth: Int get() = median.size - 1

        /** Линейная интерполяция между месяцами. */
        fun at(months: Double): Triple<Double, Double, Double> {
            val m = months.coerceIn(0.0, lastMonth.toDouble())
            val i = m.toInt().coerceAtMost(lastMonth - 1)
            val t = m - i
            fun lerp(a: DoubleArray) = a[i] + (a[i + 1] - a[i]) * t
            return Triple(lerp(minus2), lerp(median), lerp(plus2))
        }
    }

    private fun d(vararg v: Double) = v

    /** Вес, кг. */
    fun weight(sex: Sex): GrowthTable = if (sex == Sex.GIRL) girlsWeight else boysWeight

    /** Рост (длина тела), см. */
    fun height(sex: Sex): GrowthTable = if (sex == Sex.GIRL) girlsHeight else boysHeight

    private val boysWeight = GrowthTable(
        d(2.5, 3.4, 4.3, 5.0, 5.6, 6.0, 6.4, 6.7, 6.9, 7.1, 7.4, 7.6, 7.7, 7.9, 8.1, 8.3, 8.4, 8.6, 8.8, 8.9, 9.1, 9.2, 9.4, 9.5, 9.7),
        d(3.3, 4.5, 5.6, 6.4, 7.0, 7.5, 7.9, 8.3, 8.6, 8.9, 9.2, 9.4, 9.6, 9.9, 10.1, 10.3, 10.5, 10.7, 10.9, 11.1, 11.3, 11.5, 11.8, 12.0, 12.2),
        d(4.4, 5.8, 7.1, 8.0, 8.7, 9.3, 9.8, 10.3, 10.7, 11.0, 11.4, 11.7, 12.0, 12.3, 12.6, 12.8, 13.1, 13.4, 13.7, 13.9, 14.2, 14.5, 14.7, 15.0, 15.3),
    )
    private val girlsWeight = GrowthTable(
        d(2.4, 3.2, 3.9, 4.5, 5.0, 5.4, 5.7, 6.0, 6.3, 6.5, 6.7, 6.9, 7.0, 7.2, 7.4, 7.6, 7.7, 7.9, 8.1, 8.2, 8.4, 8.6, 8.7, 8.9, 9.0),
        d(3.2, 4.2, 5.1, 5.8, 6.4, 6.9, 7.3, 7.6, 7.9, 8.2, 8.5, 8.7, 8.9, 9.2, 9.4, 9.6, 9.8, 10.0, 10.2, 10.4, 10.6, 10.9, 11.1, 11.3, 11.5),
        d(4.2, 5.5, 6.6, 7.5, 8.2, 8.8, 9.3, 9.8, 10.2, 10.5, 10.9, 11.2, 11.5, 11.8, 12.1, 12.4, 12.6, 12.9, 13.2, 13.5, 13.7, 14.0, 14.3, 14.6, 14.8),
    )
    private val boysHeight = GrowthTable(
        d(46.1, 50.8, 54.4, 57.3, 59.7, 61.7, 63.3, 64.8, 66.2, 67.5, 68.7, 69.9, 71.0, 72.1, 73.1, 74.1, 75.0, 76.0, 76.9, 77.7, 78.6, 79.4, 80.2, 81.0, 81.7),
        d(49.9, 54.7, 58.4, 61.4, 63.9, 65.9, 67.6, 69.2, 70.6, 72.0, 73.3, 74.5, 75.7, 76.9, 78.0, 79.1, 80.2, 81.2, 82.3, 83.2, 84.2, 85.1, 86.0, 86.9, 87.8),
        d(53.7, 58.6, 62.4, 65.5, 68.0, 70.1, 71.9, 73.5, 75.0, 76.5, 77.9, 79.2, 80.5, 81.8, 83.0, 84.2, 85.4, 86.5, 87.7, 88.8, 89.8, 90.9, 91.9, 92.9, 93.9),
    )
    private val girlsHeight = GrowthTable(
        d(45.4, 49.8, 53.0, 55.6, 57.8, 59.6, 61.2, 62.7, 64.0, 65.3, 66.5, 67.7, 68.9, 70.0, 71.0, 72.0, 73.0, 74.0, 74.9, 75.8, 76.7, 77.5, 78.4, 79.2, 80.0),
        d(49.1, 53.7, 57.1, 59.8, 62.1, 64.0, 65.7, 67.3, 68.7, 70.1, 71.5, 72.8, 74.0, 75.2, 76.4, 77.5, 78.6, 79.7, 80.7, 81.7, 82.7, 83.7, 84.6, 85.5, 86.4),
        d(52.9, 57.6, 61.1, 64.0, 66.4, 68.5, 70.3, 71.9, 73.5, 75.0, 76.4, 77.8, 79.2, 80.5, 81.7, 83.0, 84.2, 85.4, 86.5, 87.6, 88.7, 89.8, 90.8, 91.9, 92.9),
    )

    enum class Position(val text: String) {
        BELOW("ниже нормы ВОЗ"),
        LOW("ближе к нижней границе"),
        NORMAL("в пределах нормы"),
        HIGH("ближе к верхней границе"),
        ABOVE("выше нормы ВОЗ"),
    }

    fun position(value: Double, table: GrowthTable, months: Double): Position {
        val (lo, mid, hi) = table.at(months)
        return when {
            value < lo -> Position.BELOW
            value > hi -> Position.ABOVE
            value < mid - (mid - lo) * 0.66 -> Position.LOW
            value > mid + (hi - mid) * 0.66 -> Position.HIGH
            else -> Position.NORMAL
        }
    }
}

/** Национальный календарь прививок РФ (без групп риска). Даты — ориентир, уточняйте у педиатра. */
object VaccineCalendar {
    data class Item(val name: String, val months: Double, val days: Int = 0)

    val items = listOf(
        Item("Гепатит B — 1-я", 0.0),
        Item("БЦЖ (туберкулёз)", 0.0, days = 3),
        Item("Гепатит B — 2-я", 1.0),
        Item("Пневмококк — 1-я", 2.0),
        Item("АКДС, полиомиелит, Хиб — 1-я", 3.0),
        Item("АКДС, полиомиелит, Хиб — 2-я", 4.5),
        Item("Пневмококк — 2-я", 4.5),
        Item("АКДС, полиомиелит, Хиб — 3-я", 6.0),
        Item("Гепатит B — 3-я", 6.0),
        Item("Корь, краснуха, паротит", 12.0),
        Item("Пневмококк — ревакцинация", 15.0),
        Item("АКДС, полиомиелит, Хиб — ревакцинация", 18.0),
        Item("Полиомиелит — 2-я ревакцинация", 20.0),
    )

    fun dateFor(item: Item, birthDate: Long): Long {
        val birth = localDate(birthDate)
        val days = (item.months * 30.4375).toInt() + item.days
        return at(birth.plusDays(days), 10, 0)
    }
}
