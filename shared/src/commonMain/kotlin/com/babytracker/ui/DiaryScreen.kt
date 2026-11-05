package com.babytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.data.BabyStore
import com.babytracker.logic.DaySummary
import com.babytracker.logic.Norms
import com.babytracker.logic.at
import com.babytracker.logic.daySummary
import com.babytracker.logic.dayTitle
import com.babytracker.logic.duration
import com.babytracker.logic.eventsOn
import com.babytracker.logic.lastDays
import com.babytracker.logic.localDate
import com.babytracker.logic.localDateTime
import com.babytracker.logic.minusDays
import com.babytracker.logic.oneDecimal
import com.babytracker.logic.plusDays
import com.babytracker.logic.weekdayShort
import kotlinx.datetime.LocalDate

private enum class DiaryMode { Day, Week }

@Composable
fun DiaryScreen(store: BabyStore, open: (Sheet) -> Unit) {
    val c = Baby.colors
    var mode by rememberSaveable { mutableStateOf(DiaryMode.Day) }
    val now = rememberNow(30_000)
    ScreenColumn {
        Segmented(listOf(DiaryMode.Day to "День", DiaryMode.Week to "Неделя"), mode, { mode = it }, color = c.sleep)
        when (mode) {
            DiaryMode.Day -> DayView(store, now, open)
            DiaryMode.Week -> WeekView(store, now)
        }
    }
}

@Composable
private fun DayView(store: BabyStore, now: Long, open: (Sheet) -> Unit) {
    val c = Baby.colors
    val today = localDate(now)
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateText)
    var pickDate by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(Glyph.Left, c.text, { dateText = date.minusDays(1).toString() })
        Text(
            dayTitle(date, today),
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clickable { pickDate = true }.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        if (date < today) IconButton(Glyph.Right, c.text, { dateText = date.plusDays(1).toString() })
        else Spacer(Modifier.width(40.dp))
    }

    val summary = daySummary(store.data, date, now)
    BabyCard {
        Row {
            StatCell(duration(summary.sleepMinutes), "сон · ${summary.naps} засып.", c.sleep, Modifier.weight(1.3f))
            StatCell("${summary.feeds}", "кормлений", c.feed, Modifier.weight(1f))
            StatCell("${summary.diapers}", "подгузников", c.diaper, Modifier.weight(1f))
        }
        if (summary.bottleMl > 0 || summary.breastMinutes > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                listOfNotNull(
                    summary.bottleMl.takeIf { it > 0 }?.let { "бутылочка $it мл" },
                    summary.breastMinutes.takeIf { it > 0 }?.let { "у груди ${duration(it)}" },
                    summary.solids.takeIf { it > 0 }?.let { "прикорм ×$it" },
                ).joinToString(" · "),
                color = c.muted, fontSize = 13.sp,
            )
        }
    }

    val events = eventsOn(store.data, date, now)
    BabyCard(padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        if (events.isEmpty()) EmptyNote("За этот день записей нет")
        events.forEach { EventRow(it, now) { open(editSheet(it)) } }
    }

    val addTime = if (date == today) now else {
        val t = localDateTime(now)
        at(date, t.hour, t.minute)
    }
    PrimaryButton("Добавить запись", c.sleep, { open(Sheet.AddMenu(addTime)) }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
    if (pickDate) DateDialog(date, { pickDate = false }) { dateText = minOf(it, today).toString() }
}

@Composable
private fun WeekView(store: BabyStore, now: Long) {
    val c = Baby.colors
    val days = lastDays(store.data, 7, now)
    val full = days.dropLast(1)
    fun avg(f: (DaySummary) -> Int) = if (full.isEmpty()) 0.0 else full.sumOf(f).toDouble() / full.size
    val goal = Norms.sleepGoal(store.data.settings, now)

    BabyCard {
        Text("Средние за сутки", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("по шести полным дням", color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row {
            StatCell(duration(avg { it.sleepMinutes }.toInt()), "сон", c.sleep, Modifier.weight(1.3f))
            StatCell(oneDecimal(avg { it.feeds }), "кормлений", c.feed, Modifier.weight(1f))
            StatCell(oneDecimal(avg { it.diapers }), "подгузников", c.diaper, Modifier.weight(1f))
        }
    }

    ChartCard("Сон, часы", "пунктир — цель ${duration(goal)}", days, c.sleep, goal / 60.0, { it.sleepMinutes / 60.0 }) { oneDecimal(it) }
    ChartCard("Кормления", null, days, c.feed, null, { it.feeds.toDouble() }) { it.toInt().toString() }
    if (days.any { it.bottleMl > 0 }) {
        ChartCard("Бутылочка, мл", null, days, c.feed, null, { it.bottleMl.toDouble() }) { it.toInt().toString() }
    }
    ChartCard("Подгузники", null, days, c.diaper, null, { it.diapers.toDouble() }) { it.toInt().toString() }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String?,
    days: List<DaySummary>,
    color: Color,
    goal: Double?,
    value: (DaySummary) -> Double,
    label: (Double) -> String,
) {
    val c = Baby.colors
    BabyCard {
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        BarChart(days.map(value), color, goal, days.map { it.date }, label)
    }
}

@Composable
private fun BarChart(values: List<Double>, color: Color, goal: Double?, dates: List<LocalDate>, label: (Double) -> String) {
    val c = Baby.colors
    val max = maxOf(values.maxOrNull() ?: 0.0, goal ?: 0.0, 1.0) * 1.1
    Column {
        Row(Modifier.fillMaxWidth()) {
            values.forEach { v ->
                Text(
                    if (v > 0) label(v) else "", color = c.muted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / values.size
            val barW = slot * 0.56f
            values.forEachIndexed { i, v ->
                val h = (v / max * size.height).toFloat()
                val isToday = i == values.lastIndex
                drawRoundRect(
                    c.cardSoft, Offset(i * slot + (slot - barW) / 2, 0f), Size(barW, size.height), CornerRadius(barW / 2.5f),
                )
                if (h > 0) {
                    drawRoundRect(
                        if (isToday) color.copy(alpha = 0.55f) else color,
                        Offset(i * slot + (slot - barW) / 2, size.height - h), Size(barW, h), CornerRadius(barW / 2.5f),
                    )
                }
            }
            goal?.let {
                val y = size.height - (it / max * size.height).toFloat()
                drawLine(c.text.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            dates.forEachIndexed { i, d ->
                Text(
                    if (i == dates.lastIndex) "сег." else weekdayShort(d),
                    color = c.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
