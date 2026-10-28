package com.babytracker.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * После перезагрузки телефона точные будильники сбрасываются.
 * Здесь только отмечаем факт: перепланирование произойдёт при следующем открытии приложения.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
            .edit().putLong("reboot_at", System.currentTimeMillis()).apply()
    }
}
