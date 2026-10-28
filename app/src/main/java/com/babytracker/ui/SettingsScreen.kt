package com.babytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babytracker.data.BabySettings
import com.babytracker.data.SleepNorm
import com.babytracker.util.TimeFmt
import java.util.Calendar

@Composable
fun SettingsScreen(
    settings: BabySettings,
    canScheduleExact: Boolean,
    onName: (String) -> Unit,
    onBirthDate: (Long) -> Unit,
    onSleepGoal: (Int) -> Unit,
    onFeedInterval: (Int) -> Unit,
    onSleepCycle: (Int) -> Unit,
    onDefaultBottle: (Int) -> Unit,
    onSoundTimer: (Int) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "назад",
                    Modifier.clickable(onClick = onBack).padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SectionCard("Ребёнок") {
                OutlinedTextField(
                    value = settings.name,
                    onValueChange = onName,
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Дата рождения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(
                            if (settings.birthDate > 0)
                                TimeFmt.ageText(settings.ageDays)
                            else "выбрать дату"
                        )
                    }
                    if (settings.birthDate > 0) {
                        OutlinedButton(onClick = { onBirthDate(0L) }) { Text("Сбросить") }
                    }
                }
            }
        }

        item {
            val norm = SleepNorm.forAgeMonths(settings.ageMonths)
            SectionCard("Сон") {
                Text(
                    "Норма для ${TimeFmt.ageText(settings.ageDays)}: " +
                        "${TimeFmt.duration(norm.minMinutes)} – ${TimeFmt.duration(norm.maxMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                StepperRow(
                    title = "Цель сна в сутки",
                    value = if (settings.sleepGoalMinutes > 0)
                        TimeFmt.duration(settings.sleepGoalMinutes) else "по возрасту",
                    onDecrease = {
                        onSleepGoal(
                            if (settings.sleepGoalMinutes <= 0) norm.midMinutes
                            else (settings.sleepGoalMinutes - 15).coerceAtLeast(60)
                        )
                    },
                    onIncrease = {
                        onSleepGoal(
                            if (settings.sleepGoalMinutes <= 0) norm.midMinutes + 15
                            else settings.sleepGoalMinutes + 15
                        )
                    },
                )
                StepperRow(
                    title = "Цикл сна (прогноз пробуждения)",
                    value = TimeFmt.duration(settings.sleepCycleMinutes),
                    onDecrease = { onSleepCycle((settings.sleepCycleMinutes - 15).coerceAtLeast(15)) },
                    onIncrease = { onSleepCycle((settings.sleepCycleMinutes + 15).coerceAtMost(180)) },
                )
                OutlinedButton(onClick = { onSleepGoal(0); onSleepCycle(75) }) {
                    Text("Сбросить к норме по возрасту")
                }
            }
        }

        item {
            SectionCard("Кормление") {
                StepperRow(
                    title = "Интервал между кормлениями",
                    value = TimeFmt.duration(settings.feedIntervalMinutes),
                    onDecrease = { onFeedInterval((settings.feedIntervalMinutes - 15).coerceAtLeast(15)) },
                    onIncrease = { onFeedInterval((settings.feedIntervalMinutes + 15).coerceAtMost(360)) },
                )
                StepperRow(
                    title = "Объём бутылочки по умолчанию",
                    value = "${settings.defaultBottleMl} мл",
                    onDecrease = { onDefaultBottle((settings.defaultBottleMl - 10).coerceAtLeast(10)) },
                    onIncrease = { onDefaultBottle((settings.defaultBottleMl + 10).coerceAtMost(300)) },
                )
            }
        }

        item {
            SectionCard("Звуки") {
                StepperRow(
                    title = "Таймер выключения",
                    value = if (settings.soundTimerMinutes > 0)
                        "${settings.soundTimerMinutes} мин" else "не выключать",
                    onDecrease = {
                        onSoundTimer(
                            when {
                                settings.soundTimerMinutes <= 0 -> 0
                                settings.soundTimerMinutes <= 15 -> 0
                                else -> settings.soundTimerMinutes - 15
                            }
                        )
                    },
                    onIncrease = {
                        onSoundTimer(
                            when {
                                settings.soundTimerMinutes <= 0 -> 15
                                settings.soundTimerMinutes >= 60 -> 60
                                else -> settings.soundTimerMinutes + 15
                            }
                        )
                    },
                )
            }
        }

        item {
            SectionCard("Уведомления") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Напоминания", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = settings.notificationsEnabled, onCheckedChange = onNotifications)
                }
                if (!canScheduleExact) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Система не даёт ставить точные будильники. Разреши «Точные будильники» " +
                            "в настройках Android, иначе напоминание сработает с задержкой.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            Text(
                "Нормы сна — общие ориентиры, а не медицинские рекомендации. " +
                    "Ориентируйтесь на своего педиатра.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    if (showDatePicker) {
        BirthDatePicker(
            initial = settings.birthDate,
            onDismiss = { showDatePicker = false },
            onPick = { ts ->
                onBirthDate(ts)
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePicker(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.takeIf { it > 0 }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(millis) else onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedButton(onClick = onDecrease) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onIncrease) { Text("+") }
    }
}
