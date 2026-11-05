package com.babytracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.babytracker.logic.PdfPage
import com.babytracker.logic.Reminder
import com.babytracker.logic.ReminderPlanner
import com.babytracker.logic.WidgetStatus
import com.babytracker.model.AppData
import com.babytracker.sound.SoundType

/** То, что каждая платформа делает по-своему. */
interface Platform {
    val isIos: Boolean

    val sound: SoundPlayer

    /** Заменяет все запланированные напоминания на [reminders]. */
    fun scheduleReminders(reminders: List<Reminder>)

    /** Спросить разрешение на уведомления (если ещё не спрашивали). */
    fun requestNotifications()

    /** Виджет на экране «Домой» и Live Activity. */
    fun publishStatus(status: WidgetStatus)

    /** Отдать файл в системное меню «Поделиться» (сохранить, отправить врачу и т. п.). */
    fun shareFile(name: String, mime: String, bytes: ByteArray)

    /** Выбрать текстовый файл (резервную копию). null — пользователь передумал. */
    fun pickTextFile(onResult: (String?) -> Unit)

    fun renderPdf(pages: List<PdfPage>): ByteArray
}

fun Platform.sync(data: AppData, now: Long) {
    scheduleReminders(ReminderPlanner.plan(data, now))
    publishStatus(WidgetStatus.from(data, now))
}

data class NowPlaying(val type: SoundType, val endsAt: Long?)

/** Фоновый звук. [nowPlaying] обновляет платформа — в том числе когда звук выключили из шторки. */
abstract class SoundPlayer {
    var nowPlaying: NowPlaying? by mutableStateOf(null)
        protected set

    abstract fun play(type: SoundType, timerMinutes: Int, volume: Float)

    abstract fun setVolume(volume: Float)

    abstract fun stop()
}

/** Для тестов и превью. */
class SilentPlatform : Platform {
    override val isIos = false
    override val sound = object : SoundPlayer() {
        override fun play(type: SoundType, timerMinutes: Int, volume: Float) {
            nowPlaying = NowPlaying(type, null)
        }
        override fun setVolume(volume: Float) {}
        override fun stop() {
            nowPlaying = null
        }
    }
    override fun scheduleReminders(reminders: List<Reminder>) {}
    override fun requestNotifications() {}
    override fun publishStatus(status: WidgetStatus) {}
    override fun shareFile(name: String, mime: String, bytes: ByteArray) {}
    override fun pickTextFile(onResult: (String?) -> Unit) = onResult(null)
    override fun renderPdf(pages: List<PdfPage>): ByteArray = ByteArray(0)
}
