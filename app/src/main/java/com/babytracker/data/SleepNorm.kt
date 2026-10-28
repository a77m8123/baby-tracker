package com.babytracker.data

/**
 * Нормы сна по возрасту (общие ориентиры педиатров, не медицинские рекомендации).
 * Диапазон включительно, минуты в сутки.
 */
object SleepNorm {

    data class Range(val minMinutes: Int, val maxMinutes: Int) {
        val midMinutes: Int get() = (minMinutes + maxMinutes) / 2
    }

    /** Возраст в месяцах -> норма сна в сутки. */
    fun forAgeMonths(months: Int): Range = when {
        months < 1 -> Range(14 * 60, 17 * 60)      // 0-1 мес: 14-17 ч
        months < 4 -> Range(14 * 60, 17 * 60)      // 1-4 мес: 14-17 ч
        months < 6 -> Range(12 * 60, 16 * 60)      // 4-6 мес: 12-16 ч
        months < 9 -> Range(12 * 60, 15 * 60)      // 6-9 мес: 12-15 ч
        months < 12 -> Range(11 * 60, 14 * 60)     // 9-12 мес: 11-14 ч
        months < 18 -> Range(10 * 60, 13 * 60)     // 1-1.5 г: 10-13 ч
        months < 24 -> Range(11 * 60, 14 * 60)     // 1.5-2 г: 11-14 ч
        else -> Range(10 * 60, 13 * 60)            // 2-3 г: 10-13 ч
    }

    /**
     * Целевой сон на сутки с учётом ручной настройки.
     * 0 в настройках = берём среднее нормы для возраста.
     */
    fun targetMinutes(settings: BabySettings, now: Long = System.currentTimeMillis()): Int =
        if (settings.sleepGoalMinutes > 0) settings.sleepGoalMinutes
        else forAgeMonths(settings.ageMonthsAt(now)).midMinutes

    /** Длительность непрерывного сна для прогноза пробуждения. */
    fun cycleMinutes(settings: BabySettings, now: Long = System.currentTimeMillis()): Int =
        if (settings.sleepCycleMinutes > 0) settings.sleepCycleMinutes
        else when {
            settings.ageMonthsAt(now) < 4 -> 75
            settings.ageMonthsAt(now) < 12 -> 60
            else -> 45
        }

    /**
     * Прогноз: через сколько минут ждать пробуждения, если сон начался в [sleepStart].
     * Возвращает null, если цикл уже пройден — тогда ждать следующего (конец цикла + 1).
     */
    fun wakeUpInMinutes(sleepStart: Long, now: Long, settings: BabySettings): Int {
        val cycle = cycleMinutes(settings, now)
        val elapsed = ((now - sleepStart) / 60_000L).toInt()
        if (elapsed < cycle) return cycle - elapsed
        val overshoot = elapsed % cycle
        return if (overshoot == 0) cycle else cycle - overshoot
    }
}
