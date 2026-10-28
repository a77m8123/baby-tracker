package com.babytracker.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService

/** Тип будильника. */
enum class AlarmKind { FEED, WAKE, SOUND }

/**
 * Точные будильники через AlarmManager.
 * На Android 12+ для SCHEDULE_EXACT_ALARM может потребоваться разрешение;
 * на 13+ USE_EXACT_ALARM для будильников разрешён по умолчанию.
 */
object Alarms {

    private const val REQUEST_BASE = 1000

    private fun intentFor(context: Context, kind: AlarmKind, id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.babytracker.ALARM_${kind.name}"
            putExtra(AlarmReceiver.EXTRA_KIND, kind.name)
            putExtra(AlarmReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService<AlarmManager>() ?: return false
        return am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, kind: AlarmKind, id: Int, triggerAtMillis: Long) {
        val am = context.getSystemService<AlarmManager>() ?: return
        val pi = intentFor(context, kind, id)
        val exact = canScheduleExact(context)
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(context: Context, kind: AlarmKind, id: Int) {
        val am = context.getSystemService<AlarmManager>() ?: return
        am.cancel(intentFor(context, kind, id))
    }
}
