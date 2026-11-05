package com.babytracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.Platform
import com.babytracker.data.BabyStore
import com.babytracker.logic.Norms
import com.babytracker.logic.Report
import com.babytracker.logic.ageDays
import com.babytracker.logic.ageMonths
import com.babytracker.logic.ageText
import com.babytracker.logic.dateNumeric
import com.babytracker.logic.duration
import com.babytracker.logic.localDate
import com.babytracker.logic.nowMs
import com.babytracker.model.NightMode
import com.babytracker.model.Sex
import com.babytracker.model.ThemeMode

@Composable
fun MoreScreen(store: BabyStore, platform: Platform, openProfile: () -> Unit, toast: (String) -> Unit) {
    val c = Baby.colors
    val s = store.data.settings
    val now = nowMs()
    var editNorms by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var reportDays by remember { mutableIntStateOf(14) }

    ScreenColumn {
        BabyCard(onClick = openProfile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (s.sex == Sex.GIRL) Glyph.Heart else Glyph.Moon, c.sleep, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (s.birthDate > 0) "${ageText(ageDays(s.birthDate, now))} · родил${if (s.sex == Sex.GIRL) "ась" else "ся"} ${dateNumeric(localDate(s.birthDate))}"
                        else "Укажите дату рождения",
                        color = c.muted, fontSize = 13.sp,
                    )
                }
                Icon(Glyph.Right, c.muted, size = 20.dp)
            }
        }

        SectionTitle("Режим")
        BabyCard {
            val goal = Norms.sleepGoal(s, now)
            SettingRow("Сон в сутки", if (s.sleepGoalMinutes == 0) "${duration(goal)} — норма по возрасту" else duration(goal), { editNorms = true })
            Divider()
            SettingRow("Интервал кормлений", "каждые ${duration(s.feedIntervalMinutes)}", { editNorms = true })
            Divider()
            SettingRow("Бутылочка по умолчанию", "${s.defaultBottleMl} мл", { editNorms = true })
            Divider()
            val window = Norms.wakeWindow(ageMonths(s.birthDate, now))
            SettingRow("Окно бодрствования", "${duration(window.min)} – ${duration(window.max)} по возрасту")
        }

        SectionTitle("Напоминания")
        BabyCard {
            ReminderRow("Кормление", "когда пройдёт интервал", s.remindFeeding, platform) { v -> store.updateSettings { it.copy(remindFeeding = v) } }
            Divider()
            ReminderRow("Пора укладывать", "по окну бодрствования", s.remindSleep, platform) { v -> store.updateSettings { it.copy(remindSleep = v) } }
            Divider()
            ReminderRow("Лекарства", "для тех, где включено", s.remindMeds, platform) { v -> store.updateSettings { it.copy(remindMeds = v) } }
            Divider()
            ReminderRow("Прививки", "накануне в 19:00", s.remindVaccines, platform) { v -> store.updateSettings { it.copy(remindVaccines = v) } }
            if (platform.isIos) {
                Divider()
                SettingRow("Таймер на экране блокировки", "сон и кормление в Dynamic Island", trailing = {
                    BabySwitch(s.liveActivity) { v -> store.updateSettings { it.copy(liveActivity = v) } }
                })
            }
        }

        SectionTitle("Оформление")
        BabyCard {
            FieldLabel("Тема")
            Segmented(
                listOf(ThemeMode.SYSTEM to "Как в системе", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная"),
                s.theme, { v -> store.updateSettings { it.copy(theme = v) } },
            )
            Spacer(Modifier.height(14.dp))
            FieldLabel("Ночной режим — тёплые тёмные цвета без синего света")
            Segmented(
                listOf(NightMode.OFF to "Выкл", NightMode.AUTO to "21:00–7:00", NightMode.ON to "Всегда"),
                s.night, { v -> store.updateSettings { it.copy(night = v) } }, color = c.feed,
            )
        }

        SectionTitle("Для врача")
        BabyCard {
            Text("Отчёт со сводкой по дням, ростом, лекарствами и прививками.", color = c.muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            ChipRow(listOf(7 to "7 дней", 14 to "14 дней", 30 to "30 дней"), reportDays, { reportDays = it }, c.sleep)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("PDF-отчёт", c.sleep, {
                val time = nowMs()
                runCatching { platform.renderPdf(Report.pdf(store.data, reportDays, time)) }
                    .onSuccess { platform.shareFile("malysh-otchet-${localDate(time)}.pdf", "application/pdf", it) }
                    .onFailure { toast("Не получилось собрать PDF") }
            }, Modifier.fillMaxWidth(), glyph = Glyph.Doc)
            Spacer(Modifier.height(8.dp))
            SoftButton("Таблица CSV (Excel)", c.sleep, {
                platform.shareFile("malysh-${localDate(nowMs())}.csv", "text/csv", Report.csv(store.data).encodeToByteArray())
            }, Modifier.fillMaxWidth(), glyph = Glyph.Chart)
        }

        SectionTitle("Резервная копия")
        BabyCard {
            Text(
                "Все данные хранятся только на телефоне. Сохраните копию в файлы или облако — если удалить приложение, её можно будет восстановить.",
                color = c.muted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Сохранить копию", c.diaper, {
                platform.shareFile("malysh-backup-${localDate(nowMs())}.json", "application/json", store.exportJson().encodeToByteArray())
            }, Modifier.fillMaxWidth(), glyph = Glyph.Share)
            Spacer(Modifier.height(8.dp))
            SoftButton("Восстановить из файла", c.diaper, {
                platform.pickTextFile { text -> if (text != null) confirmImport = text }
            }, Modifier.fillMaxWidth(), glyph = Glyph.Download)
        }

        BabyCard {
            SettingRow("Очистить все записи", "сон, кормления, подгузники, замеры, лекарства, прививки", { confirmClear = true }, trailing = {
                Icon(Glyph.Trash, c.danger, size = 20.dp)
            })
        }

        Text(
            "Малыш 2.0 · нормы — общие ориентиры ВОЗ и педиатров, не замена консультации врача.",
            color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (editNorms) NormsEditor(store) { editNorms = false }
    confirmImport?.let { raw ->
        ConfirmDialog(
            "Восстановить копию?",
            "Текущие данные заменятся данными из файла.",
            "Восстановить",
            { toast(if (store.importJson(raw)) "Данные восстановлены" else "Это не похоже на копию «Малыша»") },
            { confirmImport = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            "Очистить все записи?",
            "Настройки останутся, а записи удалятся без возможности восстановления. Сначала можно сохранить копию.",
            "Очистить",
            { store.clearRecords(); toast("Записи удалены") },
            { confirmClear = false },
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Baby.colors.border, thickness = 1.dp)

@Composable
private fun ReminderRow(title: String, subtitle: String, checked: Boolean, platform: Platform, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, trailing = {
        BabySwitch(checked) {
            if (it) platform.requestNotifications()
            onChange(it)
        }
    })
}

@Composable
private fun NormsEditor(store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    val s = store.data.settings
    var goal by remember { mutableIntStateOf(s.sleepGoalMinutes) }
    var interval by remember { mutableIntStateOf(s.feedIntervalMinutes) }
    var bottle by remember { mutableIntStateOf(s.defaultBottleMl) }
    val norm = Norms.sleepPerDay(ageMonths(s.birthDate, nowMs()))
    BabySheet("Режим", onDismiss) {
        FieldLabel("Цель сна в сутки (норма для возраста ${norm.min / 60}–${norm.max / 60} ч)")
        Stepper(goal, { goal = it }, 30, 0..20 * 60, c.sleep) { if (it == 0) "по возрасту" else duration(it) }
        FieldLabel("Интервал между кормлениями")
        Stepper(interval, { interval = it }, 15, 30..8 * 60, c.feed) { duration(it) }
        FieldLabel("Бутылочка по умолчанию")
        Stepper(bottle, { bottle = it }, 10, 10..400, c.feed) { "$it мл" }
        PrimaryButton("Сохранить", c.sleep, {
            store.updateSettings { it.copy(sleepGoalMinutes = goal, feedIntervalMinutes = interval, defaultBottleMl = bottle) }
            onDismiss()
        }, Modifier.fillMaxWidth())
    }
}

@Composable
fun ProfileEditor(store: BabyStore, onDismiss: () -> Unit) {
    val c = Baby.colors
    val s = store.data.settings
    var name by remember { mutableStateOf(if (s.name == "Малыш" && s.birthDate == 0L) "" else s.name) }
    var birth by remember { mutableLongStateOf(s.birthDate) }
    var sex by remember { mutableStateOf(s.sex) }
    BabySheet("О малыше", onDismiss) {
        FieldLabel("Имя")
        Field(name, { name = it.take(30) }, "Как зовут малыша")
        DateField("Дата рождения", birth, { birth = minOf(it, nowMs()) })
        FieldLabel("Пол — для норм роста и веса ВОЗ")
        Segmented(listOf(Sex.BOY to "Мальчик", Sex.GIRL to "Девочка", Sex.UNKNOWN to "Не указан"), sex, { sex = it })
        PrimaryButton("Сохранить", c.sleep, {
            store.updateSettings { it.copy(name = name.trim().ifEmpty { "Малыш" }, birthDate = birth, sex = sex) }
            onDismiss()
        }, Modifier.fillMaxWidth())
    }
}
