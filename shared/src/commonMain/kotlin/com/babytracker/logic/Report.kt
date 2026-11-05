package com.babytracker.logic

import com.babytracker.model.AppData
import kotlinx.serialization.Serializable

/**
 * Страница PDF в точках (A4 = 595×842), начало координат — левый верхний угол.
 * Раскладку считаем здесь, платформа только рисует: так отчёт одинаковый на Android и iOS.
 */
@Serializable
data class PdfPage(val width: Int = 595, val height: Int = 842, val items: List<PdfItem>)

/** [kind]: "text" (y — верх строки) или "rect". Цвет — 0xRRGGBB. */
@Serializable
data class PdfItem(
    val kind: String,
    val x: Float,
    val y: Float,
    val w: Float = 0f,
    val h: Float = 0f,
    val text: String = "",
    val size: Float = 10f,
    val bold: Boolean = false,
    val color: Long = 0x222222,
)

object Report {
    private const val MARGIN = 40f
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val INK = 0x22212BL
    private const val MUTED = 0x77748AL
    private const val ACCENT = 0x6C5FD3L
    private const val ZEBRA = 0xF3F1F8L

    private class Layout {
        val pages = mutableListOf<PdfPage>()
        private var items = mutableListOf<PdfItem>()
        var y = MARGIN

        fun ensure(height: Float) {
            if (y + height > PAGE_H - MARGIN) newPage()
        }

        fun newPage() {
            pages += PdfPage(items = items)
            items = mutableListOf()
            y = MARGIN
        }

        fun text(x: Float, text: String, size: Float = 10f, bold: Boolean = false, color: Long = INK, top: Float = y) {
            items += PdfItem("text", x, top, text = text, size = size, bold = bold, color = color)
        }

        fun rect(x: Float, top: Float, w: Float, h: Float, color: Long) {
            items += PdfItem("rect", x, top, w, h, color = color)
        }

        fun line(text: String, size: Float = 10f, bold: Boolean = false, color: Long = INK, gap: Float = 4f) {
            ensure(size + gap)
            text(MARGIN, text, size, bold, color)
            y += size + gap
        }

        fun heading(text: String) {
            ensure(40f)
            y += 10f
            text(MARGIN, text, 14f, bold = true, color = ACCENT)
            y += 20f
        }

        /** Таблица с повтором шапки на новой странице. [cols] — доли ширины. */
        fun table(header: List<String>, cols: List<Float>, rows: List<List<String>>) {
            val width = PAGE_W - 2 * MARGIN
            val xs = cols.runningFold(0f) { acc, c -> acc + c * width }.dropLast(1).map { MARGIN + it }
            val rowH = 16f
            fun drawHeader() {
                ensure(rowH * 2)
                header.forEachIndexed { i, h -> text(xs[i] + 4, h, 9f, bold = true, color = MUTED) }
                y += rowH
            }
            drawHeader()
            rows.forEachIndexed { r, row ->
                if (y + rowH > PAGE_H - MARGIN) {
                    newPage()
                    drawHeader()
                }
                if (r % 2 == 0) rect(MARGIN, y - 3, width, rowH, ZEBRA)
                row.forEachIndexed { i, cell ->
                    val maxChars = (cols[i] * width / 5.2f).toInt().coerceAtLeast(4)
                    text(xs[i] + 4, cell.ellipsize(maxChars), 9.5f)
                }
                y += rowH
            }
            if (rows.isEmpty()) line("Нет записей", 9.5f, color = MUTED)
            y += 4f
        }

        fun finish(): List<PdfPage> {
            if (items.isNotEmpty() || pages.isEmpty()) pages += PdfPage(items = items)
            return pages
        }
    }

    private fun String.ellipsize(max: Int) = if (length <= max) this else take(max - 1) + "…"

    fun pdf(data: AppData, days: Int, now: Long): List<PdfPage> {
        val s = data.settings
        val today = localDate(now)
        val from = startOfDay(today.minusDays(days - 1))
        val summaries = lastDays(data, days, now)
        val l = Layout()

        l.text(MARGIN, "Отчёт для педиатра", 22f, bold = true)
        l.y += 30f
        l.line(s.name, 13f, bold = true)
        if (s.birthDate > 0) {
            l.line("Дата рождения: ${dateNumeric(localDate(s.birthDate))} · возраст: ${ageText(ageDays(s.birthDate, now))}", 10f, color = MUTED)
        }
        l.line("Период: ${dateNumeric(today.minusDays(days - 1))} — ${dateNumeric(today)} ($days дн.)", 10f, color = MUTED)

        l.heading("Средние за сутки")
        val full = summaries.dropLast(1).ifEmpty { summaries } // сегодняшний день ещё не закончился
        fun avg(f: (DaySummary) -> Int) = full.sumOf(f).toDouble() / full.size
        l.line("Сон: ${duration(avg { it.sleepMinutes }.toInt())} (норма для возраста — ${normText(data, now)})")
        l.line("Кормлений: ${oneDecimal(avg { it.feeds })}, из бутылочки ${avg { it.bottleMl }.toInt()} мл, грудь ${avg { it.breastMinutes }.toInt()} мин")
        l.line("Подгузников: ${oneDecimal(avg { it.diapers })} (мокрых ${oneDecimal(avg { it.wet })}, грязных ${oneDecimal(avg { it.dirty })})")

        l.heading("По дням")
        l.table(
            listOf("Дата", "Сон", "Засып.", "Кормл.", "Бут., мл", "Грудь, мин", "Подгуз. (м/г)"),
            listOf(0.16f, 0.16f, 0.1f, 0.1f, 0.14f, 0.14f, 0.2f),
            summaries.reversed().map {
                listOf(
                    "${dayMonthShort(it.date)}, ${weekdayShort(it.date)}",
                    duration(it.sleepMinutes),
                    "${it.naps}",
                    "${it.feeds}",
                    "${it.bottleMl}",
                    "${it.breastMinutes}",
                    "${it.diapers} (${it.wet}/${it.dirty})",
                )
            },
        )

        l.heading("Рост и вес")
        l.table(
            listOf("Дата", "Возраст", "Вес", "Рост", "Голова"),
            listOf(0.18f, 0.28f, 0.18f, 0.18f, 0.18f),
            data.growth.sortedByDescending { it.time }.map { g ->
                listOf(
                    dateNumeric(localDate(g.time)),
                    if (s.birthDate > 0) ageText(ageDays(s.birthDate, g.time)) else "—",
                    g.weightG?.let(::weightText) ?: "—",
                    g.heightCm?.let { "${oneDecimal(it)} см" } ?: "—",
                    g.headCm?.let { "${oneDecimal(it)} см" } ?: "—",
                )
            },
        )

        val doses = data.doses.filter { it.time >= from }.sortedByDescending { it.time }
        if (doses.isNotEmpty()) {
            l.heading("Лекарства за период")
            val meds = data.medicines.associateBy { it.id }
            l.table(
                listOf("Дата и время", "Лекарство", "Доза"),
                listOf(0.25f, 0.45f, 0.3f),
                doses.map { d ->
                    val med = meds[d.medId]
                    listOf("${dateNumeric(localDate(d.time))} ${clock(d.time)}", med?.name ?: "—", med?.dose ?: "")
                },
            )
        }

        if (data.vaccines.isNotEmpty()) {
            l.heading("Прививки")
            l.table(
                listOf("Дата", "Прививка", "Статус"),
                listOf(0.18f, 0.57f, 0.25f),
                data.vaccines.sortedBy { it.plannedAt }.map { v ->
                    listOf(
                        dateNumeric(localDate(v.doneAt ?: v.plannedAt)),
                        v.name,
                        if (v.doneAt != null) "сделана" else "запланирована",
                    )
                },
            )
        }

        l.y += 10f
        l.line("Данные внесены родителями в приложение «Малыш». Нормы — общие ориентиры ВОЗ.", 8.5f, color = MUTED)
        l.line("Сформировано ${dateNumeric(today)} в ${clock(now)}", 8.5f, color = MUTED)
        return l.finish()
    }

    private fun normText(data: AppData, now: Long): String {
        val r = Norms.sleepPerDay(ageMonths(data.settings.birthDate, now))
        return "${r.min / 60}–${r.max / 60} ч"
    }

    /** CSV для Excel: разделитель «;», в начале BOM, чтобы кириллица открылась без кракозябр. */
    fun csv(data: AppData): String {
        val rows = mutableListOf(listOf("Дата", "Время", "Событие", "Подробности", "Длительность, мин"))
        val events = recentEvents(data, Int.MAX_VALUE).reversed()
        events.forEach { e ->
            val d = dateNumeric(localDate(e.time))
            val t = clock(e.time)
            rows += when (e) {
                is Event.SleepEvent -> listOf(
                    d, t, "Сон",
                    e.sleep.end?.let { "до ${dateNumeric(localDate(it))} ${clock(it)}" } ?: "ещё спит",
                    e.sleep.end?.let { "${(it - e.sleep.start) / MINUTE}" } ?: "",
                )
                is Event.FeedEvent -> listOf(
                    d, t, feedingTitle(e.feeding), feedingDetails(e.feeding),
                    if (e.feeding.leftSec + e.feeding.rightSec > 0) "${(e.feeding.leftSec + e.feeding.rightSec) / 60}" else "",
                )
                is Event.DiaperEvent -> listOf(d, t, "Подгузник", diaperTitle(e.diaper.kind), "")
            }
        }
        data.growth.forEach { g ->
            rows += listOf(
                dateNumeric(localDate(g.time)), clock(g.time), "Замер",
                listOfNotNull(
                    g.weightG?.let { "вес ${weightText(it)}" },
                    g.heightCm?.let { "рост ${oneDecimal(it)} см" },
                    g.headCm?.let { "голова ${oneDecimal(it)} см" },
                ).joinToString(", "),
                "",
            )
        }
        val meds = data.medicines.associateBy { it.id }
        data.doses.forEach { dose ->
            val med = meds[dose.medId]
            rows += listOf(dateNumeric(localDate(dose.time)), clock(dose.time), "Лекарство", listOfNotNull(med?.name, med?.dose).joinToString(" "), "")
        }
        return "﻿" + rows.joinToString("\r\n") { row -> row.joinToString(";") { csvCell(it) } }
    }

    private fun csvCell(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
