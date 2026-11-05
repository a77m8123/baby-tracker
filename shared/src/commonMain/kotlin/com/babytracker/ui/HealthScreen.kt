package com.babytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.data.BabyStore
import com.babytracker.logic.DAY
import com.babytracker.logic.HOUR
import com.babytracker.logic.Norms
import com.babytracker.logic.ageDays
import com.babytracker.logic.ageMonths
import com.babytracker.logic.ageText
import com.babytracker.logic.clock
import com.babytracker.logic.dateNumeric
import com.babytracker.logic.dayTitle
import com.babytracker.logic.localDate
import com.babytracker.logic.newId
import com.babytracker.logic.nowMs
import com.babytracker.logic.oneDecimal
import com.babytracker.logic.parseDecimal
import com.babytracker.logic.relative
import com.babytracker.logic.weightText
import com.babytracker.model.Growth
import com.babytracker.model.Medicine
import com.babytracker.model.Sex
import com.babytracker.model.Vaccine

sealed interface HealthSheet {
    data class EditGrowth(val growth: Growth, val isNew: Boolean) : HealthSheet
    data class EditMedicine(val medicine: Medicine, val isNew: Boolean) : HealthSheet
    data class EditVaccine(val vaccine: Vaccine, val isNew: Boolean) : HealthSheet
}

@Composable
fun HealthScreen(store: BabyStore, openProfile: () -> Unit) {
    var sheet by remember { mutableStateOf<HealthSheet?>(null) }
    val now = rememberNow(60_000)
    ScreenColumn {
        GrowthSection(store, now, { sheet = it }, openProfile)
        MedicinesSection(store, now) { sheet = it }
        VaccinesSection(store, now, { sheet = it }, openProfile)
    }
    when (val s = sheet) {
        is HealthSheet.EditGrowth -> GrowthEditor(s.growth, s.isNew, store) { sheet = null }
        is HealthSheet.EditMedicine -> MedicineEditor(s.medicine, s.isNew, store) { sheet = null }
        is HealthSheet.EditVaccine -> VaccineEditor(s.vaccine, s.isNew, store) { sheet = null }
        null -> {}
    }
}

private enum class GrowthMetric { Weight, Height }

@Composable
private fun GrowthSection(store: BabyStore, now: Long, open: (HealthSheet) -> Unit, openProfile: () -> Unit) {
    val c = Baby.colors
    val data = store.data
    val s = data.settings
    var metric by rememberSaveable { mutableStateOf(GrowthMetric.Weight) }
    SectionTitle("Рост и вес") {
        SoftButton("Замер", c.health, { open(HealthSheet.EditGrowth(Growth(newId(), nowMs()), isNew = true)) }, glyph = Glyph.Plus)
    }
    val lastWeight = data.growth.filter { it.weightG != null }.maxByOrNull { it.time }
    val lastHeight = data.growth.filter { it.heightCm != null }.maxByOrNull { it.time }
    BabyCard {
        Row {
            StatCell(lastWeight?.weightG?.let(::weightText) ?: "—", "вес", c.health, Modifier.weight(1f))
            StatCell(lastHeight?.heightCm?.let { "${oneDecimal(it)} см" } ?: "—", "рост", c.health, Modifier.weight(1f))
        }
        if (s.birthDate > 0 && s.sex != Sex.UNKNOWN) {
            lastWeight?.let { g ->
                val pos = Norms.position(g.weightG!! / 1000.0, Norms.weight(s.sex), ageMonths(s.birthDate, g.time))
                Spacer(Modifier.height(6.dp))
                Text("Вес ${pos.text}", color = if (pos == Norms.Position.NORMAL) c.good else c.warn, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(listOf(GrowthMetric.Weight to "Вес", GrowthMetric.Height to "Рост"), metric, { metric = it }, color = c.health)
        Spacer(Modifier.height(12.dp))
        if (s.birthDate <= 0L || s.sex == Sex.UNKNOWN) {
            Text(
                "Укажите дату рождения и пол — покажем коридор нормы ВОЗ.",
                color = c.muted, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = openProfile).padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        GrowthChart(store, metric, now)
        Spacer(Modifier.height(6.dp))
        Text("Полоса — от −2 до +2 стандартных отклонений по ВОЗ, линия — медиана.", color = c.muted, fontSize = 11.sp)
    }
    val rows = data.growth.sortedByDescending { it.time }
    if (rows.isNotEmpty()) {
        BabyCard(padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            rows.take(6).forEach { g ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable { open(HealthSheet.EditGrowth(g, isNew = false)) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Glyph.Ruler, c.health, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(
                                g.weightG?.let(::weightText),
                                g.heightCm?.let { "${oneDecimal(it)} см" },
                                g.headCm?.let { "голова ${oneDecimal(it)}" },
                            ).joinToString(" · "),
                            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (s.birthDate > 0) Text(ageText(ageDays(s.birthDate, g.time)), color = c.muted, fontSize = 13.sp)
                    }
                    Text(dateNumeric(localDate(g.time)), color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GrowthChart(store: BabyStore, metric: GrowthMetric, now: Long) {
    val c = Baby.colors
    val s = store.data.settings
    val hasNorm = s.birthDate > 0 && s.sex != Sex.UNKNOWN
    val table = if (metric == GrowthMetric.Weight) Norms.weight(s.sex) else Norms.height(s.sex)
    val points = store.data.growth.mapNotNull { g ->
        val v = if (metric == GrowthMetric.Weight) g.weightG?.div(1000.0) else g.heightCm
        v?.let { (if (s.birthDate > 0) ageMonths(s.birthDate, g.time) else (g.time - (store.data.growth.minOf { it.time })) / (30.4375 * DAY)) to it }
    }.sortedBy { it.first }
    if (points.isEmpty() && !hasNorm) {
        EmptyNote("Добавьте первый замер")
        return
    }
    val ageNow = if (s.birthDate > 0) ageMonths(s.birthDate, now) else points.maxOfOrNull { it.first } ?: 1.0
    val xMax = maxOf(3.0, kotlin.math.ceil(maxOf(ageNow, points.maxOfOrNull { it.first } ?: 0.0) + 1.0)).coerceAtMost(24.0)
    val normRange = if (hasNorm) (0..(xMax.toInt())).map { table.at(it.toDouble()) } else emptyList()
    val yValues = points.map { it.second } + normRange.flatMap { listOf(it.first, it.third) }
    val yMin = (yValues.minOrNull() ?: 0.0) * 0.95
    val yMax = (yValues.maxOrNull() ?: 1.0) * 1.03
    val unit = if (metric == GrowthMetric.Weight) "кг" else "см"

    Row {
        Column(Modifier.height(170.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("${oneDecimal(yMax)}", color = c.muted, fontSize = 10.sp)
            Text(unit, color = c.muted, fontSize = 10.sp)
            Text("${oneDecimal(yMin)}", color = c.muted, fontSize = 10.sp)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(170.dp)) {
            fun x(m: Double) = (m / xMax * size.width).toFloat()
            fun y(v: Double) = (size.height - (v - yMin) / (yMax - yMin) * size.height).toFloat()
            if (hasNorm) {
                val band = Path()
                val steps = (xMax * 4).toInt()
                for (i in 0..steps) {
                    val m = i / 4.0
                    val (lo, _, _) = table.at(m)
                    if (i == 0) band.moveTo(x(m), y(lo)) else band.lineTo(x(m), y(lo))
                }
                for (i in steps downTo 0) {
                    val m = i / 4.0
                    band.lineTo(x(m), y(table.at(m).third))
                }
                band.close()
                drawPath(band, c.good.copy(alpha = 0.14f))
                val median = Path()
                for (i in 0..steps) {
                    val m = i / 4.0
                    if (i == 0) median.moveTo(x(m), y(table.at(m).second)) else median.lineTo(x(m), y(table.at(m).second))
                }
                drawPath(median, c.good.copy(alpha = 0.6f), style = Stroke(2.dp.toPx()))
            }
            if (points.size > 1) {
                val line = Path()
                points.forEachIndexed { i, (m, v) -> if (i == 0) line.moveTo(x(m), y(v)) else line.lineTo(x(m), y(v)) }
                drawPath(line, c.health, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            points.forEach { (m, v) ->
                drawCircle(c.card, 6.dp.toPx(), Offset(x(m), y(v)))
                drawCircle(c.health, 4.5.dp.toPx(), Offset(x(m), y(v)))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
        Text("0 мес", color = c.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("${xMax.toInt()} мес", color = c.muted, fontSize = 10.sp)
    }
}

@Composable
private fun MedicinesSection(store: BabyStore, now: Long, open: (HealthSheet) -> Unit) {
    val c = Baby.colors
    val data = store.data
    val meds = data.medicines.filter { !it.archived }
    SectionTitle("Лекарства и витамины") {
        SoftButton("Добавить", c.health, { open(HealthSheet.EditMedicine(Medicine(newId(), ""), isNew = true)) }, glyph = Glyph.Plus)
    }
    if (meds.isEmpty()) {
        BabyCard { EmptyNote("Например, витамин D раз в сутки или жаропонижающее не чаще чем раз в 6 часов") }
        return
    }
    meds.forEach { med ->
        val last = data.doses.filter { it.medId == med.id }.maxByOrNull { it.time }
        val allowedAt = last?.let { it.time + med.intervalHours * HOUR }
        val tooEarly = allowedAt != null && med.intervalHours > 0 && allowedAt > now
        var confirmEarly by remember { mutableStateOf(false) }
        BabyCard(onClick = { open(HealthSheet.EditMedicine(med, isNew = false)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Glyph.Pill, c.health)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(med.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            med.dose.ifBlank { null },
                            if (med.intervalHours > 0) "не чаще раза в ${med.intervalHours} ч" else null,
                        ).joinToString(" · ").ifEmpty { "без ограничений" },
                        color = c.muted, fontSize = 13.sp,
                    )
                    Text(
                        when {
                            last == null -> "ещё не давали"
                            tooEarly -> "давали ${relative(last.time, now)} · снова в ${clock(allowedAt!!)}"
                            else -> "давали ${relative(last.time, now)}"
                        },
                        color = if (tooEarly) c.warn else c.muted, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Дали", if (tooEarly) c.muted else c.health, {
                    if (tooEarly) confirmEarly = true else store.giveDose(med.id)
                })
            }
        }
        if (confirmEarly) {
            ConfirmDialog(
                "Интервал ещё не прошёл",
                "С прошлого приёма прошло меньше ${med.intervalHours} ч. Всё равно отметить?",
                "Отметить",
                { store.giveDose(med.id) },
                { confirmEarly = false },
            )
        }
    }
}

@Composable
private fun VaccinesSection(store: BabyStore, now: Long, open: (HealthSheet) -> Unit, openProfile: () -> Unit) {
    val c = Baby.colors
    val data = store.data
    val today = localDate(now)
    SectionTitle("Прививки") {
        SoftButton("Добавить", c.health, {
            open(HealthSheet.EditVaccine(Vaccine(newId(), "", nowMs() + 7 * DAY), isNew = true))
        }, glyph = Glyph.Plus)
    }
    if (data.vaccines.isEmpty()) {
        BabyCard {
            Text(
                "Можно заполнить по национальному календарю — даты рассчитаются от дня рождения. Уточняйте график у педиатра.",
                color = c.muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            if (data.settings.birthDate > 0) {
                PrimaryButton("Заполнить по календарю", c.health, { store.fillVaccineCalendar() }, Modifier.fillMaxWidth(), glyph = Glyph.Syringe)
            } else {
                SoftButton("Сначала укажите дату рождения", c.health, openProfile, Modifier.fillMaxWidth())
            }
        }
        return
    }
    val upcoming = data.vaccines.filter { it.doneAt == null }.sortedBy { it.plannedAt }
    val done = data.vaccines.filter { it.doneAt != null }.sortedByDescending { it.doneAt }
    BabyCard(padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        (upcoming + done).forEach { v ->
            val isDone = v.doneAt != null
            val overdue = !isDone && v.plannedAt < now - DAY
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable { open(HealthSheet.EditVaccine(v, isNew = false)) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(if (isDone) c.good else c.health.soft(c))
                        .clickable { store.toggleVaccineDone(v.id) },
                    contentAlignment = Alignment.Center,
                ) { if (isDone) Icon(Glyph.Check, c.onAccent, size = 18.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, color = if (isDone) c.muted else c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isDone) "сделана ${dateNumeric(localDate(v.doneAt!!))}"
                        else "${dayTitle(localDate(v.plannedAt), today)}${if (overdue) " · пропущена?" else ""}",
                        color = if (overdue) c.warn else c.muted, fontSize = 13.sp,
                    )
                }
            }
        }
    }
    if (data.settings.birthDate > 0) {
        Text(
            "Добавить недостающие из календаря",
            color = c.health, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { store.fillVaccineCalendar() }.padding(8.dp),
        )
    }
}

@Composable
private fun EditorFooter(isNew: Boolean, canSave: Boolean, onSave: () -> Unit, onDelete: () -> Unit, deleteText: String = "Это действие нельзя отменить.") {
    val c = Baby.colors
    var confirm by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!isNew) IconButton(Glyph.Trash, c.danger, { confirm = true }, size = 52.dp)
        PrimaryButton(if (isNew) "Добавить" else "Сохранить", c.health, onSave, Modifier.weight(1f), enabled = canSave)
    }
    if (confirm) ConfirmDialog("Удалить?", deleteText, "Удалить", onDelete, { confirm = false })
}

@Composable
private fun GrowthEditor(initial: Growth, isNew: Boolean, store: BabyStore, onDismiss: () -> Unit) {
    var time by remember { mutableLongStateOf(initial.time) }
    var weight by remember { mutableStateOf(initial.weightG?.let { oneDecimalKg(it) } ?: "") }
    var height by remember { mutableStateOf(initial.heightCm?.let(::oneDecimal) ?: "") }
    var head by remember { mutableStateOf(initial.headCm?.let(::oneDecimal) ?: "") }
    val w = parseDecimal(weight)
    val h = parseDecimal(height)
    val hd = parseDecimal(head)
    // вес можно ввести и в кг (4,25), и в граммах (4250)
    val grams = w?.let { if (it < 50) (it * 1000).toInt() else it.toInt() }
    val valid = (grams != null || h != null || hd != null) &&
        (grams == null || grams in 500..40000) && (h == null || h in 30.0..130.0) && (hd == null || hd in 20.0..60.0)
    BabySheet(if (isNew) "Новый замер" else "Замер", onDismiss) {
        DateField("Дата", time, { time = it })
        FieldLabel("Вес")
        Field(weight, { weight = it.take(7) }, "например 4,25", keyboard = KeyboardType.Decimal, suffix = "кг")
        FieldLabel("Рост")
        Field(height, { height = it.take(5) }, "например 56,5", keyboard = KeyboardType.Decimal, suffix = "см")
        FieldLabel("Окружность головы")
        Field(head, { head = it.take(4) }, "необязательно", keyboard = KeyboardType.Decimal, suffix = "см")
        EditorFooter(isNew, valid, onSave = {
            store.saveGrowth(Growth(initial.id, time, grams, h, hd))
            onDismiss()
        }, onDelete = { store.deleteGrowth(initial.id); onDismiss() })
    }
}

private fun oneDecimalKg(grams: Int): String = weightText(grams).removeSuffix(" кг").removeSuffix(" г")

@Composable
private fun MedicineEditor(initial: Medicine, isNew: Boolean, store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var interval by remember { mutableIntStateOf(initial.intervalHours) }
    var remind by remember { mutableStateOf(initial.remind) }
    BabySheet(if (isNew) "Новое лекарство" else initial.name, onDismiss) {
        FieldLabel("Название")
        Field(name, { name = it.take(40) }, "Например, Витамин D")
        FieldLabel("Доза")
        Field(dose, { dose = it.take(40) }, "Например, 1 капля или 2,5 мл")
        FieldLabel("Не чаще, чем раз в")
        Stepper(interval, { interval = it }, 1, 0..72, c.health) { if (it == 0) "без ограничения" else "$it ч" }
        SettingRow("Напомнить, когда можно снова", "уведомление через ${if (interval > 0) "$interval ч" else "интервал"} после приёма", trailing = {
            BabySwitch(remind && interval > 0) { remind = it }
        })
        if (!isNew) {
            val doses = store.data.doses.filter { it.medId == initial.id }.sortedByDescending { it.time }
            if (doses.isNotEmpty()) {
                FieldLabel("Последние приёмы")
                doses.take(5).forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${dayTitle(localDate(d.time), localDate(nowMs()))}, ${clock(d.time)}", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            "удалить", color = c.danger, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { store.deleteDose(d.id) }.padding(6.dp),
                        )
                    }
                }
            }
        }
        EditorFooter(
            isNew, name.isNotBlank(),
            onSave = {
                store.saveMedicine(initial.copy(name = name.trim(), dose = dose.trim(), intervalHours = interval, remind = remind && interval > 0))
                onDismiss()
            },
            onDelete = { store.deleteMedicine(initial.id); onDismiss() },
            deleteText = "Лекарство удалится вместе с историей приёмов.",
        )
    }
}

@Composable
private fun VaccineEditor(initial: Vaccine, isNew: Boolean, store: BabyStore, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var planned by remember { mutableLongStateOf(initial.plannedAt) }
    var done by remember { mutableStateOf(initial.doneAt) }
    BabySheet(if (isNew) "Новая прививка" else "Прививка", onDismiss) {
        FieldLabel("Название")
        Field(name, { name = it.take(60) }, "Например, Ротавирус")
        DateField("Плановая дата", planned, { planned = it })
        SettingRow("Сделана", trailing = { BabySwitch(done != null) { done = if (it) nowMs() else null } })
        done?.let { d -> DateField("Когда сделали", d, { done = it }) }
        EditorFooter(isNew, name.isNotBlank(), onSave = {
            store.saveVaccine(initial.copy(name = name.trim(), plannedAt = planned, doneAt = done))
            onDismiss()
        }, onDelete = { store.deleteVaccine(initial.id); onDismiss() })
    }
}
