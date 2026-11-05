package com.babytracker.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.babytracker.data.BabyStore
import com.babytracker.data.SharedPrefsStorage
import com.babytracker.logic.nowMs
import com.babytracker.sync

class BabyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_SOUNDS = "sounds"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDERS, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Кормление, сон, лекарства и прививки"
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SOUNDS, "Звуки для сна", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Управление звуком в шторке"
                    setSound(null, null)
                },
            )
        }
    }
}

/**
 * Одно хранилище и одна платформа на весь процесс: экран, виджет и будильники
 * видят одни и те же данные, поэтому нажатие на виджете сразу видно в открытом приложении.
 */
object AppGraph {
    @Volatile
    private var storeInstance: BabyStore? = null

    @Volatile
    private var platformInstance: AndroidPlatform? = null

    fun store(context: Context): BabyStore = storeInstance ?: synchronized(this) {
        storeInstance ?: BabyStore(SharedPrefsStorage(context.applicationContext)).also { storeInstance = it }
    }

    fun platform(context: Context): AndroidPlatform = platformInstance ?: synchronized(this) {
        platformInstance ?: AndroidPlatform(context.applicationContext).also { platformInstance = it }
    }

    /** Перепланировать напоминания и обновить виджет — из ресивера, когда экрана может не быть. */
    fun sync(context: Context) {
        platform(context).sync(store(context).data, nowMs())
    }
}
