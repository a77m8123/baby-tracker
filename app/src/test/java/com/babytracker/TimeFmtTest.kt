package com.babytracker

import com.babytracker.util.TimeFmt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimeFmtTest {

    @Test
    fun `длительность без часов`() {
        assertEquals("0 мин", TimeFmt.duration(0))
        assertEquals("45 мин", TimeFmt.duration(45))
        assertEquals("59 мин", TimeFmt.duration(59))
    }

    @Test
    fun `длительность с часами`() {
        assertEquals("1 ч", TimeFmt.duration(60))
        assertEquals("1 ч 1 мин", TimeFmt.duration(61))
        assertEquals("2 ч 30 мин", TimeFmt.duration(150))
        assertEquals("15 ч 30 мин", TimeFmt.duration(930))
    }

    @Test
    fun `склонение дней`() {
        assertEquals("день", TimeFmt.plural(1, "день", "дня", "дней"))
        assertEquals("дня", TimeFmt.plural(2, "день", "дня", "дней"))
        assertEquals("дней", TimeFmt.plural(5, "день", "дня", "дней"))
        assertEquals("дней", TimeFmt.plural(11, "день", "дня", "дней"))
        assertEquals("дней", TimeFmt.plural(14, "день", "дня", "дней"))
        assertEquals("день", TimeFmt.plural(21, "день", "дня", "дней"))
        assertEquals("дня", TimeFmt.plural(22, "день", "дня", "дней"))
    }

    @Test
    fun `возраст в днях`() {
        assertEquals("5 дней", TimeFmt.ageText(5))
        assertEquals("1 день", TimeFmt.ageText(1))
        assertEquals("21 день", TimeFmt.ageText(21))
    }

    @Test
    fun `возраст в месяцах`() {
        assertEquals("2 месяца", TimeFmt.ageText(62))
        assertEquals("1 месяц", TimeFmt.ageText(31))
        assertEquals("5 месяцев", TimeFmt.ageText(150))
    }

    @Test
    fun `возраст больше года`() {
        assertEquals("1 год", TimeFmt.ageText(365))
        assertEquals("1 год 1 месяц", TimeFmt.ageText(395))
        assertEquals("2 года", TimeFmt.ageText(730))
    }

    @Test
    fun `относительное время назад`() {
        val now = System.currentTimeMillis()
        assertEquals("30 мин назад", TimeFmt.relative(now - 30L * 60_000L, now))
        assertEquals("2 ч назад", TimeFmt.relative(now - 120L * 60_000L, now))
    }

    @Test
    fun `относительное время вперёд`() {
        val now = System.currentTimeMillis()
        assertEquals("через 30 мин", TimeFmt.relative(now + 30L * 60_000L, now))
    }

    @Test
    fun `начало дня — полночь`() {
        val start = TimeFmt.startOfToday()
        val c = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.SECOND))
        assertEquals(0, c.get(Calendar.MILLISECOND))
    }

    @Test
    fun `начало дня не в будущем`() {
        val start = TimeFmt.startOfToday()
        val now = System.currentTimeMillis()
        assertEquals(true, start <= now)
        assertEquals(true, now - start < 86_400_000L)
    }

    @Test
    fun `часы форматируются с ведущим нулём`() {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 5)
        }
        assertEquals("09:05", TimeFmt.clock(c.timeInMillis))
    }

    @Test
    fun `полночь форматируется как 00 00`() {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("00:00", TimeFmt.clock(c.timeInMillis))
    }
}
