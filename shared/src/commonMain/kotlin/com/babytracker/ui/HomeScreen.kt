package com.babytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.data.BabyStore
import com.babytracker.logic.DAY
import com.babytracker.logic.Event
import com.babytracker.logic.HOUR
import com.babytracker.logic.MINUTE
import com.babytracker.logic.Norms
import com.babytracker.logic.WakeState
import com.babytracker.logic.WindowPhase
import com.babytracker.logic.clock
import com.babytracker.logic.dayTitle
import com.babytracker.logic.daySummary
import com.babytracker.logic.diaperTitle
import com.babytracker.logic.duration
import com.babytracker.logic.durationMs
import com.babytracker.logic.feedingDetails
import com.babytracker.logic.feedingTitle
import com.babytracker.logic.lastFeeding
import com.babytracker.logic.localDate
import com.babytracker.logic.nextFeedAt
import com.babytracker.logic.recentEvents
import com.babytracker.logic.relative
import com.babytracker.logic.sideName
import com.babytracker.logic.suggestedSide
import com.babytracker.logic.timer
import com.babytracker.logic.wakeState
import com.babytracker.logic.windowPhase
import com.babytracker.model.FeedType
import com.babytracker.model.Side

@Composable
fun HomeScreen(store: BabyStore, open: (Sheet) -> Unit, openDiary: () -> Unit, openHealth: () -> Unit, openProfile: () -> Unit) {
    val data = store.data
    val now = rememberNow()
    ScreenColumn {
        if (data.settings.birthDate <= 0L) OnboardingCard(openProfile)
        WakeCard(store, now)
        data.activeFeed?.let { ActiveFeedCard(store, now) } ?: FeedStatusCard(store, now, open)
        QuickActions(open)
        TodayCard(store, now)
        HealthHints(store, now, openHealth)
        RecentCard(store, now, open, openDiary)
    }
}

@Composable
private fun OnboardingCard(openProfile: () -> Unit) {
    val c = Baby.colors
    BabyCard(color = c.sleep.soft(c), onClick = openProfile) {
        Text("Расскажите о малыше", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Имя и дата рождения нужны, чтобы считать нормы сна, окна бодрствования и рост по ВОЗ.",
            color = c.muted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Заполнить", c.sleep, openProfile)
    }
}

@Composable
private fun WakeCard(store: BabyStore, now: Long) {
    val c = Baby.colors
    when (val state = wakeState(store.data, now)) {
        is WakeState.Sleeping -> {
            val elapsed = now - state.since
            // кольцо заполняется за типичный дневной сон (1,5 ч); ночью просто идёт по кругу
            val progress = (elapsed % (90 * MINUTE)).toFloat() / (90 * MINUTE)
            BabyCard(color = c.sleep.soft(c)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress, c.sleep, Modifier.size(132.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Glyph.Moon, c.sleep, size = 26.dp)
                            Text(timer(elapsed), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Спит", color = c.sleep, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("уснул(а) в ${clock(state.since)}", color = c.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Проснулся", c.sleep, { store.endSleep() }, Modifier.fillMaxWidth(), glyph = Glyph.Sun)
                    }
                }
            }
        }
        is WakeState.Awake -> {
            val phase = windowPhase(state, now)
            val color = when (phase) {
                WindowPhase.EARLY, null -> c.good
                WindowPhase.READY -> c.warn
                WindowPhase.OVERDUE -> c.danger
            }
            val progress = if (state.since != null && state.windowEnd != null) {
                (now - state.since).toFloat() / (state.windowEnd - state.since)
            } else 0f
            BabyCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress, color, Modifier.size(132.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Glyph.Sun, c.warn, size = 26.dp)
                            val awake = state.since?.let { now - it }
                            Text(
                                when {
                                    awake == null -> "—"
                                    awake < HOUR -> "${awake / MINUTE} мин"
                                    else -> durationShort(awake)
                                },
                                color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Бодрствует", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                state.windowStart == null -> "Отметьте сон, чтобы видеть окно бодрствования"
                                phase == WindowPhase.EARLY -> "уложить в ${clock(state.windowStart)}–${clock(state.windowEnd!!)}"
                                phase == WindowPhase.READY -> "пора укладывать — до ${clock(state.windowEnd!!)}"
                                else -> "окно прошло ${relative(state.windowEnd!!, now)} — может капризничать"
                            },
                            color = if (phase == null || phase == WindowPhase.EARLY) c.muted else color,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Уснул", c.sleep, { store.startSleep() }, Modifier.fillMaxWidth(), glyph = Glyph.Moon)
                    }
                }
            }
        }
    }
}

/** «1 ч 05» — компактно для кольца. */
private fun durationShort(ms: Long): String {
    val m = (ms / MINUTE).toInt()
    return "${m / 60} ч ${(m % 60).toString().padStart(2, '0')}"
}

@Composable
private fun ActiveFeedCard(store: BabyStore, now: Long) {
    val c = Baby.colors
    val feed = store.data.activeFeed ?: return
    var confirmCancel by remember { mutableStateOf(false) }
    BabyCard(color = c.feed.soft(c)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Кормление грудью", color = c.feed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(timer(feed.totalMs(now)), color = c.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Отменить", color = c.muted, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { confirmCancel = true }.padding(8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Side.entries.forEach { side ->
                SideTimer(side, feed.sideMs(side, now), active = side == feed.side, paused = feed.paused, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Glyph.Swap, c.feed, { store.switchSide() }, size = 52.dp)
            IconButton(if (feed.paused) Glyph.Play else Glyph.Pause, c.feed, { store.togglePause() }, size = 52.dp)
            PrimaryButton("Закончить", c.feed, { store.finishBreast() }, Modifier.weight(1f), glyph = Glyph.Check)
        }
    }
    if (confirmCancel) {
        ConfirmDialog("Отменить кормление?", "Таймер сбросится, запись не сохранится.", "Отменить кормление", { store.cancelBreast() }, { confirmCancel = false })
    }
}

@Composable
private fun FeedStatusCard(store: BabyStore, now: Long, open: (Sheet) -> Unit) {
    val c = Baby.colors
    val data = store.data
    val last = lastFeeding(data)
    val next = nextFeedAt(data)
    BabyCard(onClick = { if (last == null) open(Sheet.Breast) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (last?.type == FeedType.BOTTLE) Glyph.Bottle else Glyph.Breast, c.feed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (last == null) {
                    Text("Кормлений пока нет", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Нажмите «Грудь» или «Бутылочка» ниже", color = c.muted, fontSize = 13.sp)
                } else {
                    Text("Кормили ${relative(last.time, now)}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${feedingTitle(last)} · ${feedingDetails(last)}", color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (last != null && next != null) {
            Spacer(Modifier.height(14.dp))
            val interval = data.settings.feedIntervalMinutes * MINUTE
            val overdue = now > next
            ProgressBar((now - last.time).toFloat() / interval, if (overdue) c.danger else c.feed)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    if (overdue) "Пора кормить — с ${clock(next)}" else "Следующее ≈ ${clock(next)} (${relative(next, now)})",
                    color = if (overdue) c.danger else c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                suggestedSide(data)?.let {
                    Text("грудь: ${sideName(it)}", color = c.feed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(open: (Sheet) -> Unit) {
    val c = Baby.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Грудь", "таймер Л / П", Glyph.Breast, c.feed, { open(Sheet.Breast) }, Modifier.weight(1f))
            ActionTile("Бутылочка", "смесь, сцеженное", Glyph.Bottle, c.feed, { open(Sheet.Bottle) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Прикорм", "что и сколько", Glyph.Spoon, c.health, { open(Sheet.Solids) }, Modifier.weight(1f))
            ActionTile("Подгузник", "мокрый, грязный", Glyph.Diaper, c.diaper, { open(Sheet.DiaperQuick) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodayCard(store: BabyStore, now: Long) {
    val c = Baby.colors
    val data = store.data
    val today = daySummary(data, localDate(now), now)
    val goal = Norms.sleepGoal(data.settings, now)
    BabyCard {
        Text("Сегодня", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row {
            Text("Сон", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${duration(today.sleepMinutes)} из ${duration(goal)}", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(today.sleepMinutes.toFloat() / goal, c.sleep)
        Spacer(Modifier.height(14.dp))
        Row {
            StatCell("${today.feeds}", feedsLabel(today.feeds), c.feed, Modifier.weight(1f))
            StatCell(if (today.bottleMl > 0) "${today.bottleMl} мл" else "${today.breastMinutes} мин", if (today.bottleMl > 0) "из бутылочки" else "у груди", c.feed, Modifier.weight(1f))
            StatCell("${today.diapers}", "подгузн. · ${today.wet}м ${today.dirty}г", c.diaper, Modifier.weight(1f))
        }
    }
}

private fun feedsLabel(n: Int) = com.babytracker.logic.plural(n, "кормление", "кормления", "кормлений")

/** Лекарства с недавним приёмом и ближайшие прививки. */
@Composable
private fun HealthHints(store: BabyStore, now: Long, openHealth: () -> Unit) {
    val c = Baby.colors
    val data = store.data
    val meds = data.medicines.filter { !it.archived }.mapNotNull { med ->
        val last = data.doses.filter { it.medId == med.id }.maxOfOrNull { it.time } ?: return@mapNotNull null
        if (now - last > DAY) return@mapNotNull null
        med to last
    }
    val vaccine = data.vaccines.filter { it.doneAt == null && it.plannedAt in (now - DAY)..(now + 7 * DAY) }.minByOrNull { it.plannedAt }
    if (meds.isEmpty() && vaccine == null) return
    BabyCard(onClick = openHealth) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            meds.forEach { (med, last) ->
                val allowedAt = last + med.intervalHours * HOUR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Glyph.Pill, c.health, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(med.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "давали ${relative(last, now)}" + when {
                                med.intervalHours == 0 -> ""
                                allowedAt > now -> " · снова можно в ${clock(allowedAt)}"
                                else -> " · можно давать"
                            },
                            color = c.muted, fontSize = 13.sp,
                        )
                    }
                }
            }
            vaccine?.let { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Glyph.Syringe, c.health, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("прививка: ${dayTitle(localDate(v.plannedAt), localDate(now)).lowercase()}", color = c.muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(store: BabyStore, now: Long, open: (Sheet) -> Unit, openDiary: () -> Unit) {
    val c = Baby.colors
    val events = recentEvents(store.data, 5)
    SectionTitle("Последние записи") {
        Text(
            "Дневник", color = c.sleep, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = openDiary).padding(6.dp),
        )
    }
    BabyCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        if (events.isEmpty()) EmptyNote("Здесь появятся сон, кормления и подгузники")
        events.forEach { EventRow(it, now, showDay = true) { open(editSheet(it)) } }
    }
}

fun editSheet(event: Event): Sheet = when (event) {
    is Event.SleepEvent -> Sheet.EditSleep(event.sleep, isNew = false)
    is Event.FeedEvent -> Sheet.EditFeeding(event.feeding, isNew = false)
    is Event.DiaperEvent -> Sheet.EditDiaper(event.diaper, isNew = false)
}

@Composable
fun EventRow(event: Event, now: Long, showDay: Boolean = false, onClick: () -> Unit) {
    val c = Baby.colors
    val (glyph, color: Color, title, details) = when (event) {
        is Event.SleepEvent -> {
            val end = event.sleep.end
            Quad(
                Glyph.Moon, c.sleep, "Сон",
                if (end == null) "спит ${durationMs(now - event.sleep.start)}"
                else "${clock(event.sleep.start)}–${clock(end)} · ${durationMs(end - event.sleep.start)}",
            )
        }
        is Event.FeedEvent -> Quad(
            when (event.feeding.type) {
                FeedType.BREAST -> Glyph.Breast
                FeedType.BOTTLE -> Glyph.Bottle
                FeedType.SOLIDS -> Glyph.Spoon
            },
            if (event.feeding.type == FeedType.SOLIDS) c.health else c.feed,
            feedingTitle(event.feeding),
            feedingDetails(event.feeding),
        )
        is Event.DiaperEvent -> Quad(Glyph.Diaper, c.diaper, "Подгузник", diaperTitle(event.diaper.kind))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(glyph, color, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(clock(event.time), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (showDay) {
                val day = localDate(event.time)
                val today = localDate(now)
                if (day != today) Text(dayTitle(day, today), color = c.muted, fontSize = 11.sp)
            }
        }
    }
}

private data class Quad(val glyph: Glyph, val color: Color, val title: String, val details: String)
