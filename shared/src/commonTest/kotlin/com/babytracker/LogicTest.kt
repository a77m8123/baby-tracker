package com.babytracker

import com.babytracker.data.AppDataCodec
import com.babytracker.data.BabyStore
import com.babytracker.data.InMemoryStorage
import com.babytracker.logic.HOUR
import com.babytracker.logic.MINUTE
import com.babytracker.logic.Norms
import com.babytracker.logic.ReminderPlanner
import com.babytracker.logic.Report
import com.babytracker.logic.WakeState
import com.babytracker.logic.WidgetStatus
import com.babytracker.logic.ageText
import com.babytracker.logic.at
import com.babytracker.logic.daySummary
import com.babytracker.logic.duration
import com.babytracker.logic.nextFeedAt
import com.babytracker.logic.plural
import com.babytracker.logic.plusDays
import com.babytracker.logic.suggestedSide
import com.babytracker.logic.timer
import com.babytracker.logic.wakeState
import com.babytracker.logic.weightText
import com.babytracker.model.DiaperKind
import com.babytracker.model.FeedType
import com.babytracker.model.Milk
import com.babytracker.model.Sex
import com.babytracker.model.Side
import com.babytracker.model.Sleep
import com.babytracker.sound.SAMPLE_RATE
import com.babytracker.sound.SoundSession
import com.babytracker.sound.SoundType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class LogicTest {
    private val day = LocalDate(2026, 11, 5)
    private val noon = at(day, 12, 0)

    private fun store() = BabyStore(InMemoryStorage())

    @Test
    fun formatting() {
        assertEquals("2 ч 30 мин", duration(150))
        assertEquals("45 мин", duration(45))
        assertEquals("1:04:07", timer((3600 + 4 * 60 + 7) * 1000L))
        assertEquals("4:07", timer((4 * 60 + 7) * 1000L))
        assertEquals("дней", plural(11, "день", "дня", "дней"))
        assertEquals("дня", plural(22, "день", "дня", "дней"))
        assertEquals("4,25 кг", weightText(4250))
        assertEquals("850 г", weightText(850))
        assertEquals("1 год", ageText(366))
    }

    @Test
    fun sleepIsSplitAcrossMidnight() {
        val s = store()
        s.saveSleep(Sleep("a", at(day, 22, 0), at(day.plusDays(1), 6, 0)))
        val today = daySummary(s.data, day, noon + 24 * HOUR)
        val tomorrow = daySummary(s.data, day.plusDays(1), noon + 24 * HOUR)
        assertEquals(120, today.sleepMinutes)
        assertEquals(360, tomorrow.sleepMinutes)
        assertEquals(1, today.naps)
        assertEquals(0, tomorrow.naps)
    }

    @Test
    fun onlyOneOpenSleep() {
        val s = store()
        s.startSleep(noon)
        s.startSleep(noon + HOUR) // второй раз ничего не делает
        assertEquals(1, s.data.sleeps.size)
        s.saveSleep(Sleep("b", noon + 2 * HOUR, null))
        assertEquals(1, s.data.sleeps.count { it.end == null })
        assertEquals(noon + 2 * HOUR, s.data.activeSleep?.start)
    }

    @Test
    fun wakeWindowFollowsAge() {
        val s = store()
        s.updateSettings { it.copy(birthDate = at(LocalDate(2026, 9, 5), 0, 0)) } // 2 месяца
        s.saveSleep(Sleep("a", noon - 2 * HOUR, noon))
        val state = wakeState(s.data, noon + 30 * MINUTE) as WakeState.Awake
        assertEquals(noon, state.since)
        assertEquals(noon + 60 * MINUTE, state.windowStart)
        assertEquals(noon + 90 * MINUTE, state.windowEnd)
        s.startSleep(noon + HOUR)
        assertTrue(wakeState(s.data, noon + 2 * HOUR) is WakeState.Sleeping)
    }

    @Test
    fun breastTimerSwitchesAndPauses() {
        val s = store()
        s.startBreast(Side.LEFT, noon)
        s.switchSide(noon + 5 * MINUTE)
        s.togglePause(noon + 8 * MINUTE)
        s.togglePause(noon + 20 * MINUTE) // 12 минут паузы не считаются
        s.finishBreast(noon + 22 * MINUTE)
        val f = s.data.feedings.single()
        assertEquals(FeedType.BREAST, f.type)
        assertEquals(5 * 60, f.leftSec)
        assertEquals(5 * 60, f.rightSec)
        assertEquals(Side.RIGHT, f.lastSide)
        assertNull(s.data.activeFeed)
        assertEquals(Side.LEFT, suggestedSide(s.data))
    }

    @Test
    fun nextFeedingAndReminders() {
        val s = store()
        s.updateSettings { it.copy(feedIntervalMinutes = 180, name = "Аня") }
        s.addBottle(120, Milk.FORMULA, noon)
        assertEquals(noon + 3 * HOUR, nextFeedAt(s.data))
        val reminders = ReminderPlanner.plan(s.data, noon + HOUR)
        val feed = reminders.single { it.id == ReminderPlanner.ID_FEED }
        assertEquals(noon + 3 * HOUR, feed.atMs)
        assertTrue(feed.text.startsWith("Аня"))
        s.updateSettings { it.copy(remindFeeding = false) }
        assertTrue(ReminderPlanner.plan(s.data, noon + HOUR).none { it.id == ReminderPlanner.ID_FEED })
    }

    @Test
    fun medicineReminderAfterInterval() {
        val s = store()
        s.saveMedicine(com.babytracker.model.Medicine("m", "Парацетамол", intervalHours = 6, remind = true))
        s.giveDose("m", noon)
        val r = ReminderPlanner.plan(s.data, noon + HOUR)
        assertTrue(r.any { it.atMs == noon + 6 * HOUR && it.title == "Парацетамол" })
        s.deleteMedicine("m")
        assertTrue(s.data.doses.isEmpty())
    }

    @Test
    fun vaccineCalendarNeedsBirthDate() {
        val s = store()
        s.fillVaccineCalendar()
        assertTrue(s.data.vaccines.isEmpty())
        s.updateSettings { it.copy(birthDate = at(day, 0, 0)) }
        s.fillVaccineCalendar()
        val count = s.data.vaccines.size
        assertTrue(count > 10)
        s.fillVaccineCalendar() // повторно не дублирует
        assertEquals(count, s.data.vaccines.size)
    }

    @Test
    fun growthNorms() {
        val table = Norms.weight(Sex.BOY)
        assertEquals(Norms.Position.NORMAL, Norms.position(5.6, table, 2.0))
        assertEquals(Norms.Position.BELOW, Norms.position(3.5, table, 2.0))
        assertEquals(Norms.Position.ABOVE, Norms.position(8.0, table, 2.0))
        val (_, mid, _) = table.at(1.5)
        assertTrue(abs(mid - 5.05) < 0.001)
    }

    @Test
    fun codecSurvivesGarbageAndRoundTrips() {
        assertEquals(com.babytracker.model.AppData(), AppDataCodec.decode("{not json"))
        assertNull(AppDataCodec.decodeOrNull("[]"))
        val s = store()
        s.addDiaper(DiaperKind.MIXED, noon)
        s.addSolids("кабачок", noon)
        val copy = BabyStore(InMemoryStorage())
        assertTrue(copy.importJson(s.exportJson()))
        assertEquals(s.data, copy.data)
        assertFalse(copy.importJson("hello"))
    }

    @Test
    fun storeSavesEveryChange() {
        val storage = InMemoryStorage()
        BabyStore(storage).addDiaper(DiaperKind.WET, noon)
        assertEquals(1, BabyStore(storage).data.diapers.size)
    }

    @Test
    fun widgetStatus() {
        val s = store()
        s.startSleep(noon)
        val st = WidgetStatus.from(s.data, noon + HOUR)
        assertTrue(st.sleeping)
        assertEquals(noon, st.sinceMs)
    }

    @Test
    fun reportHasPagesAndCsv() {
        val s = store()
        repeat(40) { i -> s.addBottle(90, Milk.FORMULA, noon - i * 3 * HOUR) }
        val pages = Report.pdf(s.data, 30, noon)
        assertTrue(pages.isNotEmpty())
        assertTrue(pages.all { p -> p.items.all { it.y <= p.height } })
        val csv = Report.csv(s.data)
        assertTrue(csv.startsWith("﻿Дата;"))
        assertEquals(41, csv.split("\r\n").size)
    }

    @Test
    fun soundFadesOutAndFinishes() {
        val session = SoundSession(SoundType.WHITE_NOISE, timerMinutes = 1, volume = 1f)
        val buf = FloatArray(SAMPLE_RATE)
        repeat(60) { session.fill(buf) }
        assertTrue(session.finished)
        assertTrue(buf.all { abs(it) < 0.05f }) // последняя секунда почти тишина
        SoundType.entries.forEach { type ->
            val s = SoundSession(type, 0, 1f)
            val out = FloatArray(SAMPLE_RATE * 4)
            s.fill(out)
            assertTrue(out.all { it.isFinite() && it in -1f..1f }, type.name)
            assertNotNull(out.firstOrNull { it != 0f }, type.name)
        }
    }

}
