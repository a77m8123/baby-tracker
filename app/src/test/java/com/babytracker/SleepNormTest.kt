package com.babytracker

import com.babytracker.data.BabySettings
import com.babytracker.data.SleepNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepNormTest {

    private val now = 1_700_000_000_000L

    // ---------- нормы по возрасту ----------

    @Test
    fun `норма для 2 месяцев — 14-17 часов`() {
        val range = SleepNorm.forAgeMonths(2)
        assertEquals(14 * 60, range.minMinutes)
        assertEquals(17 * 60, range.maxMinutes)
    }

    @Test
    fun `нормы убывают с возрастом`() {
        val newborn = SleepNorm.forAgeMonths(0).midMinutes
        val halfYear = SleepNorm.forAgeMonths(6).midMinutes
        val year = SleepNorm.forAgeMonths(12).midMinutes
        assertTrue("новорождённый спит больше полугодовалого", newborn > halfYear)
        assertTrue("полугодовалый спит больше годовалого", halfYear > year)
    }

    @Test
    fun `границы возрастных групп`() {
        assertEquals(SleepNorm.forAgeMonths(3).minMinutes, SleepNorm.forAgeMonths(0).minMinutes)
        assertEquals(SleepNorm.forAgeMonths(6).minMinutes, SleepNorm.forAgeMonths(5).minMinutes)
    }

    // ---------- цель сна ----------

    @Test
    fun `без ручной настройки берётся середина нормы`() {
        val s = BabySettings(birthDate = now - 62L * DAY, sleepGoalMinutes = 0)
        val expected = SleepNorm.forAgeMonths(2).midMinutes
        assertEquals(expected, SleepNorm.targetMinutes(s, now))
    }

    @Test
    fun `ручная настройка перекрывает норму возраста`() {
        val s = BabySettings(birthDate = now - 62L * DAY, sleepGoalMinutes = 600)
        assertEquals(600, SleepNorm.targetMinutes(s, now))
    }

    @Test
    fun `без даты рождения цель считается по нулевому возрасту`() {
        val s = BabySettings(birthDate = 0L)
        assertEquals(0, s.ageDays)
        assertEquals(SleepNorm.forAgeMonths(0).midMinutes, SleepNorm.targetMinutes(s, now))
    }

    // ---------- возраст ----------

    @Test
    fun `возраст в днях считается от даты рождения`() {
        val s = BabySettings(birthDate = now - 62L * DAY)
        assertEquals(62, s.ageDaysAt(now))
    }

    @Test
    fun `два месяца дают возраст 2 месяца`() {
        val s = BabySettings(birthDate = now - 62L * DAY)
        assertEquals(2, s.ageMonthsAt(now))
    }

    @Test
    fun `будущая дата рождения даёт нулевой возраст`() {
        val s = BabySettings(birthDate = now + 10L * DAY)
        assertEquals(0, s.ageDaysAt(now))
    }

    // ---------- цикл и прогноз пробуждения ----------

    @Test
    fun `цикл сна для новорождённого 75 минут`() {
        val s = BabySettings(birthDate = now - 62L * DAY, sleepCycleMinutes = 0)
        assertEquals(75, SleepNorm.cycleMinutes(s, now))
    }

    @Test
    fun `ручная настройка цикла перекрывает возраст`() {
        val s = BabySettings(birthDate = now - 62L * DAY, sleepCycleMinutes = 40)
        assertEquals(40, SleepNorm.cycleMinutes(s, now))
    }

    @Test
    fun `прогноз в начале сна равен полному циклу`() {
        val s = BabySettings(sleepCycleMinutes = 75)
        assertEquals(75, SleepNorm.wakeUpInMinutes(now, now, s))
    }

    @Test
    fun `прогноз уменьшается по мере сна`() {
        val s = BabySettings(sleepCycleMinutes = 75)
        assertEquals(55, SleepNorm.wakeUpInMinutes(now - 20L * MIN, now, s))
        assertEquals(1, SleepNorm.wakeUpInMinutes(now - 74L * MIN, now, s))
    }

    @Test
    fun `прогноз никогда не отрицательный`() {
        val s = BabySettings(sleepCycleMinutes = 75)
        for (elapsed in 0..500) {
            val left = SleepNorm.wakeUpInMinutes(now - elapsed.toLong() * MIN, now, s)
            assertTrue("на $elapsed мин получено $left", left in 1..75)
        }
    }

    @Test
    fun `на границе цикла прогноз снова полный`() {
        val s = BabySettings(sleepCycleMinutes = 75)
        assertEquals(75, SleepNorm.wakeUpInMinutes(now - 75L * MIN, now, s))
        assertEquals(75, SleepNorm.wakeUpInMinutes(now - 150L * MIN, now, s))
    }

    // ---------- деление по месяцам ----------

    @Test
    fun `границы месяца считаются без перескока`() {
        assertEquals(0, BabySettings(birthDate = now - 29L * DAY).ageMonthsAt(now))
        assertEquals(1, BabySettings(birthDate = now - 30L * DAY).ageMonthsAt(now))
        assertEquals(1, BabySettings(birthDate = now - 59L * DAY).ageMonthsAt(now))
        assertEquals(2, BabySettings(birthDate = now - 60L * DAY).ageMonthsAt(now))
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MIN = 60_000L
    }
}
