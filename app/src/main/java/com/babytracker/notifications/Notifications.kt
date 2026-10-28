package com.babytracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.babytracker.MainActivity
import com.babytracker.R

object Notifications {

    const val CHANNEL_ALARMS = "alarms"
    const val CHANNEL_SOUNDS = "sounds"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Будильники", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминания о кормлении и пробуждении"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOUNDS, "Звуки", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Управление звуками"
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String, channel: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // нет разрешения на уведомления
        }
    }

    fun showFeedDue(context: Context) =
        notify(context, 101, "Пора кормить", "Малыш ждёт кормления", CHANNEL_ALARMS)

    fun showWakeUp(context: Context) =
        notify(context, 102, "Пора просыпаться", "Сон подошёл к концу", CHANNEL_ALARMS)

    fun showSoundPlaying(context: Context, title: String, timerMinutes: Int) {
        val text = if (timerMinutes > 0) "Выключится через $timerMinutes мин" else "Играет"
        notify(context, 200, title, text, CHANNEL_SOUNDS)
    }
}
