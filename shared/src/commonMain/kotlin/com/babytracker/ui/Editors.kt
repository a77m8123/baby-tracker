package com.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.data.BabyStore
import com.babytracker.logic.MINUTE
import com.babytracker.logic.diaperTitle
import com.babytracker.logic.duration
import com.babytracker.logic.newId
import com.babytracker.logic.nowMs
import com.babytracker.logic.sideName
import com.babytracker.logic.suggestedSide
import com.babytracker.model.Diaper
import com.babytracker.model.DiaperKind
import com.babytracker.model.FeedType
import com.babytracker.model.Feeding
import com.babytracker.model.Milk
import com.babytracker.model.Side
import com.babytracker.model.Sleep

/** Какая шторка открыта. */
sealed interface Sheet {
    data object Breast : Sheet
    data object Bottle : Sheet
    data object Solids : Sheet
    data object DiaperQuick : Sheet
    data class AddMenu(val time: Long) : Sheet
    data class EditSleep(val sleep: Sleep, val isNew: Boolean) : Sheet
    data class EditFeeding(val feeding: Feeding, val isNew: Boolean) : Sheet
    data class EditDiaper(val diaper: Diaper, val isNew: Boolean) : Sheet
}

@Composable
fun EventSheets(sheet: Sheet, store: BabyStore, open: (Sheet?) -> Unit) {
    val close = { open(null) }
    when (sheet) {
        Sheet.Breast -> BreastStartSheet(store, close, onManual = {
            open(Sheet.EditFeeding(Feeding(newId(), nowMs() - 15 * MINUTE, FeedType.BREAST, leftSec = 600, rightSec = 0, lastSide = Side.LEFT), isNew = true))
        })
        Sheet.Bottle -> BottleQuickSheet(store, close)
        Sheet.Solids -> SolidsQuickSheet(store, close)
        Sheet.DiaperQuick -> DiaperQuickSheet(store, close)
        is Sheet.AddMenu -> AddMenuSheet(sheet.time, store, open)
        is Sheet.EditSleep -> SleepEditor(sheet.sleep, sheet.isNew, store, close)
        is Sheet.EditFeeding -> FeedingEditor(sheet.feeding, sheet.isNew, store, close)
        is Sheet.EditDiaper -> DiaperEditor(sheet.diaper, sheet.isNew, store, close)
    }
}

fun newBottle(store: BabyStore, time: Long = nowMs()): Feeding {
    val lastMilk = store.data.feedings.filter { it.type == FeedType.BOTTLE }.maxByOrNull { it.time }?.milk
    return Feeding(newId(), time, FeedType.BOTTLE, amountMl = store.data.settings.defaultBottleMl, milk = lastMilk ?: Milk.FORMULA)
}

fun newSolids(time: Long = nowMs()) = Feeding(newId(), time, FeedType.SOLIDS)

@Composable
private fun BreastStartSheet(store: BabyStore, onDismiss: () -> Unit, onManual: () -> Unit) {
    val c = Baby.colors
    val suggested = suggestedSide(store.data)
    BabySheet("Кормление грудью", onDismiss) {
        Text(
            if (suggested != null) "В прошлый раз закончили на другой — сейчас начните с ${if (suggested == Side.LEFT) "левой" else "правой"}."
            else "Выберите, с какой груди начинаете. Таймер можно переключать по ходу.",
            color = c.muted, fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Side.entries.forEach { side ->
                val highlight = side == suggested
                Column(
                    Modifier
                        .weight(1f)
                        .clip(TileShape)
                        .background(if (highlight) c.feed else c.feed.soft(c))
                        .clickable { store.startBreast(side); onDismiss() }
                        .padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = if (highlight) c.onAccent else c.feed
                    Text(if (side == Side.LEFT) "Л" else "П", color = fg, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (side == Side.LEFT) "Левая" else "Правая", color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (highlight) Text("рекомендуем", color = fg.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
        SoftButton("Записать без таймера", c.muted, onManual, Modifier.fillMaxWidth(), glyph = Glyph.Clock)
    }
}

@Composable
private fun DiaperQuickSheet(store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    var time by remember { mutableLongStateOf(nowMs()) }
    BabySheet("Подгузник", onDismiss) {
        DateTimeField("Когда", time) { time = it }
        DiaperKind.entries.forEach { kind ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(TileShape)
                    .background(c.diaper.soft(c))
                    .clickable { store.addDiaper(kind, time); onDismiss() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DiaperDots(kind)
                Text(diaperTitle(kind), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Значок вида подгузника: капля (мокрый) и/или точка (грязный). */
@Composable
fun DiaperDots(kind: DiaperKind) {
    val c = Baby.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (kind != DiaperKind.DIRTY) Box(Modifier.padding(2.dp)) { Icon(Glyph.Breast, c.sound, size = 20.dp) }
        if (kind != DiaperKind.WET) Box(Modifier.padding(2.dp)) {
            Box(Modifier.padding(2.dp).clip(CircleShape).background(c.warn).padding(8.dp))
        }
    }
}

@Composable
private fun AddMenuSheet(time: Long, store: BabyStore, open: (Sheet?) -> Unit) {
    val c = Baby.colors
    BabySheet("Добавить запись", { open(null) }) {
        Text("Например, если забыли отметить вовремя.", color = c.muted, fontSize = 14.sp)
        val items = listOf(
            Triple("Сон", Glyph.Moon, c.sleep) to { open(Sheet.EditSleep(Sleep(newId(), time - 60 * MINUTE, time), isNew = true)) },
            Triple("Грудь", Glyph.Breast, c.feed) to {
                open(Sheet.EditFeeding(Feeding(newId(), time, FeedType.BREAST, leftSec = 600, lastSide = Side.LEFT), isNew = true))
            },
            Triple("Бутылочка", Glyph.Bottle, c.feed) to { open(Sheet.EditFeeding(newBottle(store, time), isNew = true)) },
            Triple("Прикорм", Glyph.Spoon, c.feed) to { open(Sheet.EditFeeding(newSolids(time), isNew = true)) },
            Triple("Подгузник", Glyph.Diaper, c.diaper) to { open(Sheet.EditDiaper(Diaper(newId(), time, DiaperKind.WET), isNew = true)) },
        )
        items.forEach { (meta, action) ->
            val (title, glyph, color) = meta
            Row(
                Modifier.fillMaxWidth().clip(TileShape).background(color.soft(c)).clickable(onClick = action).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge(glyph, color, 36.dp)
                Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EditorButtons(isNew: Boolean, color: Color, canSave: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    val c = Baby.colors
    var confirm by remember { mutableStateOf(false) }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!isNew) IconButton(Glyph.Trash, c.danger, { confirm = true }, size = 52.dp)
        PrimaryButton(if (isNew) "Добавить" else "Сохранить", color, onSave, Modifier.weight(1f), enabled = canSave)
    }
    if (confirm) {
        ConfirmDialog("Удалить запись?", "Это действие нельзя отменить.", "Удалить", onDelete, { confirm = false })
    }
}

@Composable
private fun SleepEditor(initial: Sleep, isNew: Boolean, store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    var start by remember { mutableLongStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    val valid = end == null || end!! >= start
    BabySheet(if (isNew) "Новый сон" else "Сон", onDismiss) {
        DateTimeField("Уснул", start) { start = it }
        SettingRow("Ещё спит", trailing = {
            BabySwitch(end == null) { stillSleeping -> end = if (stillSleeping) null else maxOf(nowMs(), start) }
        })
        end?.let { e -> DateTimeField("Проснулся", e) { end = it } }
        val e = end
        Text(
            when {
                !valid -> "Пробуждение раньше, чем засыпание"
                e != null -> "Длительность: ${duration(((e - start) / MINUTE).toInt())}"
                else -> "Сон продолжается"
            },
            color = if (valid) c.muted else c.danger, fontSize = 14.sp,
        )
        EditorButtons(isNew, c.sleep, valid, onSave = {
            store.saveSleep(initial.copy(start = start, end = end))
            onDismiss()
        }, onDelete = { store.deleteSleep(initial.id); onDismiss() })
    }
}

@Composable
private fun FeedingEditor(initial: Feeding, isNew: Boolean, store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    var type by remember { mutableStateOf(initial.type) }
    var time by remember { mutableLongStateOf(initial.time) }
    var leftMin by remember { mutableIntStateOf((initial.leftSec + 30) / 60) }
    var rightMin by remember { mutableIntStateOf((initial.rightSec + 30) / 60) }
    var lastSide by remember { mutableStateOf(initial.lastSide ?: Side.LEFT) }
    var ml by remember { mutableIntStateOf(initial.amountMl ?: store.data.settings.defaultBottleMl) }
    var milk by remember { mutableStateOf(initial.milk ?: Milk.FORMULA) }
    var note by remember { mutableStateOf(initial.note) }

    BabySheet(if (isNew) "Новое кормление" else "Кормление", onDismiss) {
        Segmented(
            listOf(FeedType.BREAST to "Грудь", FeedType.BOTTLE to "Бутылочка", FeedType.SOLIDS to "Прикорм"),
            type, { type = it }, color = c.feed,
        )
        DateTimeField("Начало", time) { time = it }
        when (type) {
            FeedType.BREAST -> {
                FieldLabel("Левая грудь")
                Stepper(leftMin, { leftMin = it }, 1, 0..120, c.feed) { "$it мин" }
                FieldLabel("Правая грудь")
                Stepper(rightMin, { rightMin = it }, 1, 0..120, c.feed) { "$it мин" }
                FieldLabel("Закончили на")
                Segmented(listOf(Side.LEFT to "Левой", Side.RIGHT to "Правой"), lastSide, { lastSide = it }, color = c.feed)
            }
            FeedType.BOTTLE -> {
                Stepper(ml, { ml = it }, 10, 10..400, c.feed) { "$it мл" }
                ChipRow(listOf(60 to "60", 90 to "90", 120 to "120", 150 to "150", 180 to "180"), ml, { ml = it }, c.feed)
                Segmented(listOf(Milk.FORMULA to "Смесь", Milk.BREAST_MILK to "Сцеженное"), milk, { milk = it }, color = c.feed)
            }
            FeedType.SOLIDS -> {
                FieldLabel("Что ели")
                Field(note, { note = it.take(80) }, "Например, кабачок 30 г")
            }
        }
        val valid = type != FeedType.BREAST || leftMin + rightMin > 0
        EditorButtons(isNew, c.feed, valid, onSave = {
            val feeding = when (type) {
                FeedType.BREAST -> Feeding(initial.id, time, type, leftSec = leftMin * 60, rightSec = rightMin * 60, lastSide = lastSide)
                FeedType.BOTTLE -> Feeding(initial.id, time, type, amountMl = ml, milk = milk)
                FeedType.SOLIDS -> Feeding(initial.id, time, type, note = note.trim())
            }
            store.saveFeeding(feeding)
            onDismiss()
        }, onDelete = { store.deleteFeeding(initial.id); onDismiss() })
    }
}

@Composable
private fun DiaperEditor(initial: Diaper, isNew: Boolean, store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    var time by remember { mutableLongStateOf(initial.time) }
    var kind by remember { mutableStateOf(initial.kind) }
    BabySheet(if (isNew) "Новый подгузник" else "Подгузник", onDismiss) {
        DateTimeField("Когда", time) { time = it }
        Segmented(
            listOf(DiaperKind.WET to "Мокрый", DiaperKind.DIRTY to "Грязный", DiaperKind.MIXED to "Оба"),
            kind, { kind = it }, color = c.diaper,
        )
        EditorButtons(isNew, c.diaper, true, onSave = {
            store.saveDiaper(initial.copy(time = time, kind = kind))
            onDismiss()
        }, onDelete = { store.deleteDiaper(initial.id); onDismiss() })
    }
}

/** Бутылочка в одно касание: объём и что в ней. */
@Composable
fun BottleQuickSheet(store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    val draft = remember { newBottle(store) }
    var ml by remember { mutableIntStateOf(draft.amountMl ?: 90) }
    var milk by remember { mutableStateOf(draft.milk ?: Milk.FORMULA) }
    var time by remember { mutableLongStateOf(draft.time) }
    BabySheet("Бутылочка", onDismiss) {
        Stepper(ml, { ml = it }, 10, 10..400, c.feed) { "$it мл" }
        ChipRow(listOf(60 to "60", 90 to "90", 120 to "120", 150 to "150", 180 to "180"), ml, { ml = it }, c.feed)
        Segmented(listOf(Milk.FORMULA to "Смесь", Milk.BREAST_MILK to "Сцеженное"), milk, { milk = it }, color = c.feed)
        DateTimeField("Когда", time) { time = it }
        PrimaryButton("Записать", c.feed, { store.addBottle(ml, milk, time); onDismiss() }, Modifier.fillMaxWidth(), glyph = Glyph.Check)
    }
}

@Composable
fun SolidsQuickSheet(store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    var note by remember { mutableStateOf("") }
    var time by remember { mutableLongStateOf(nowMs()) }
    val recent = store.data.feedings
        .filter { it.type == FeedType.SOLIDS && it.note.isNotBlank() }
        .sortedByDescending { it.time }
        .map { it.note }
        .distinct()
        .take(4)
    BabySheet("Прикорм", onDismiss) {
        Field(note, { note = it.take(80) }, "Что ели, например «пюре из кабачка 30 г»")
        if (recent.isNotEmpty()) {
            FieldLabel("Недавнее")
            recent.forEach { r ->
                Text(
                    r, color = c.feed, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.feed.soft(c), RoundedCornerShape(12.dp))
                        .clickable { note = r }.padding(12.dp),
                )
            }
        }
        DateTimeField("Когда", time) { time = it }
        PrimaryButton("Записать", c.feed, { store.addSolids(note, time); onDismiss() }, Modifier.fillMaxWidth(), glyph = Glyph.Check)
    }
}

/** Крупные «Л» / «П» с секундомером внутри карточки идущего кормления. */
@Composable
fun SideTimer(side: Side, ms: Long, active: Boolean, paused: Boolean, modifier: Modifier = Modifier) {
    val c = Baby.colors
    Column(
        modifier
            .clip(TileShape)
            .background(if (active) c.feed else c.feed.soft(c))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg = if (active) c.onAccent else c.feed
        Text(sideName(side).replaceFirstChar { it.uppercase() }, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            com.babytracker.logic.timer(ms),
            color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        if (active) Text(if (paused) "пауза" else "идёт", color = fg.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}
