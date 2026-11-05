package com.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.logic.at
import com.babytracker.logic.clock
import com.babytracker.logic.dateNumeric
import com.babytracker.logic.dayTitle
import com.babytracker.logic.localDate
import com.babytracker.logic.localDateTime
import com.babytracker.logic.nowMs
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Нижняя шторка с заголовком. Содержимое прокручивается. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabySheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Baby.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        contentColor = c.text,
        scrimColor = Color.Black.copy(alpha = 0.45f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun utcMillisToDate(ms: Long): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date

@Composable
private fun PickerPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Baby.colors
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(text, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

/** Дата и время: две «таблетки», каждая открывает свой выбор. */
@Composable
fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dt = localDateTime(value)
    Column {
        FieldLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerPill(dayTitle(dt.date, localDate(nowMs())), { pickDate = true }, Modifier.weight(1f))
            PickerPill(clock(value), { pickTime = true })
        }
    }
    if (pickDate) {
        DateDialog(dt.date, onDismiss = { pickDate = false }) { date -> onChange(at(date, dt.hour, dt.minute)) }
    }
    if (pickTime) {
        TimeDialog(dt.hour, dt.minute, onDismiss = { pickTime = false }) { h, m -> onChange(at(dt.date, h, m)) }
    }
}

/** Только дата (день рождения, замер, прививка). [value] 0 — не выбрана. */
@Composable
fun DateField(label: String, value: Long, onChange: (Long) -> Unit, placeholder: String = "Выбрать") {
    var pick by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        PickerPill(if (value > 0) dateNumeric(localDate(value)) else placeholder, { pick = true }, Modifier.fillMaxWidth())
    }
    if (pick) {
        val initial = if (value > 0) localDate(value) else localDate(nowMs())
        DateDialog(initial, onDismiss = { pick = false }) { date ->
            val keepTime = if (value > 0) localDateTime(value) else null
            onChange(at(date, keepTime?.hour ?: 10, keepTime?.minute ?: 0))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = Baby.colors
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
    val colors = DatePickerDefaults.colors(
        containerColor = c.card,
        titleContentColor = c.muted,
        headlineContentColor = c.text,
        weekdayContentColor = c.muted,
        dayContentColor = c.text,
        selectedDayContainerColor = c.sleep,
        selectedDayContentColor = c.onAccent,
        todayContentColor = c.sleep,
        todayDateBorderColor = c.sleep,
        navigationContentColor = c.text,
        yearContentColor = c.text,
        selectedYearContainerColor = c.sleep,
        selectedYearContentColor = c.onAccent,
        currentYearContentColor = c.sleep,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcMillisToDate(it)) }
                onDismiss()
            }) { Text("Готово", color = c.sleep, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.text) } },
        colors = colors,
    ) {
        DatePicker(state = state, colors = colors, showModeToggle = false, title = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val c = Baby.colors
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) {
                Text("Готово", color = c.sleep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.text) } },
        containerColor = c.card,
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.cardSoft,
                    clockDialSelectedContentColor = c.onAccent,
                    clockDialUnselectedContentColor = c.text,
                    selectorColor = c.sleep,
                    containerColor = c.card,
                    timeSelectorSelectedContainerColor = c.sleep.soft(c),
                    timeSelectorUnselectedContainerColor = c.cardSoft,
                    timeSelectorSelectedContentColor = c.sleep,
                    timeSelectorUnselectedContentColor = c.text,
                ),
            )
        },
    )
}
