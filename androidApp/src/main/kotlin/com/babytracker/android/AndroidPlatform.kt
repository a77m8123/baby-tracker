package com.babytracker.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.babytracker.Platform
import com.babytracker.logic.PdfPage
import com.babytracker.logic.Reminder
import com.babytracker.logic.WidgetStatus
import java.io.ByteArrayOutputStream
import java.io.File

class AndroidPlatform(private val context: Context) : Platform {

    /** Текущий экран — нужен для выбора файла и запроса разрешений. */
    @Volatile
    var activity: MainActivity? = null

    override val isIos = false

    override val sound = AndroidSoundPlayer(context)

    private val prefs = context.getSharedPreferences("malysh_alarms", Context.MODE_PRIVATE)

    override fun scheduleReminders(reminders: List<Reminder>) {
        val am = context.getSystemService(AlarmManager::class.java)
        // снимаем всё, что ставили раньше
        prefs.getStringSet(KEY_IDS, emptySet())!!.mapNotNull { it.toIntOrNull() }.forEach { id ->
            reminderIntent(id, null)?.let { am.cancel(it) }
        }
        reminders.forEach { r ->
            val pi = reminderIntent(r.id, r)!!
            val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMs, pi)
        }
        prefs.edit().putStringSet(KEY_IDS, reminders.map { it.id.toString() }.toSet()).apply()
    }

    private fun reminderIntent(id: Int, reminder: Reminder?): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.babytracker.REMINDER_$id"
            if (reminder != null) {
                putExtra(ReminderReceiver.EXTRA_ID, id)
                putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
                putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
            }
        }
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun requestNotifications() {
        activity?.requestNotificationPermission()
    }

    override fun publishStatus(status: WidgetStatus) {
        BabyWidget.update(context, status)
    }

    override fun shareFile(name: String, mime: String, bytes: ByteArray) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, name)
        val host = activity
        if (host != null) host.startActivity(chooser)
        else context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun pickTextFile(onResult: (String?) -> Unit) {
        val host = activity ?: return onResult(null)
        host.pickTextFile(onResult)
    }

    override fun renderPdf(pages: List<PdfPage>): ByteArray {
        val doc = PdfDocument()
        val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        try {
            pages.forEachIndexed { index, page ->
                val p = doc.startPage(PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create())
                val canvas = p.canvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                page.items.forEach { item ->
                    paint.color = (0xFF000000 or item.color).toInt()
                    if (item.kind == "rect") {
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(item.x, item.y, item.x + item.w, item.y + item.h, paint)
                    } else {
                        paint.typeface = if (item.bold) bold else regular
                        paint.textSize = item.size
                        // y — верх строки, drawText ждёт базовую линию
                        canvas.drawText(item.text, item.x, item.y + item.size * 0.8f, paint)
                    }
                }
                doc.finishPage(p)
            }
            return ByteArrayOutputStream().use { out ->
                doc.writeTo(out)
                out.toByteArray()
            }
        } finally {
            doc.close()
        }
    }

    private companion object {
        const val KEY_IDS = "scheduled_ids"
    }
}
