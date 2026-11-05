@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.babytracker.platform

import com.babytracker.NowPlaying
import com.babytracker.Platform
import com.babytracker.SoundPlayer
import com.babytracker.data.AppDataCodec
import com.babytracker.logic.PdfPage
import com.babytracker.logic.Reminder
import com.babytracker.logic.WidgetStatus
import com.babytracker.logic.nowMs
import com.babytracker.sound.SAMPLE_RATE
import com.babytracker.sound.SoundSession
import com.babytracker.sound.SoundType
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.serialization.builtins.ListSerializer
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy

/**
 * То, что удобнее написать на Swift (виджет, Live Activity, «Поделиться», выбор файла, PDF),
 * реализует AppBridge в iosApp. В Swift это протокол IosBridge.
 */
interface IosBridge {
    fun statusChanged(json: String)
    fun shareFile(name: String, data: NSData)
    fun pickTextFile(callback: (String?) -> Unit)
    fun renderPdf(json: String): NSData
}

class IosPlatform(private val bridge: IosBridge) : Platform {
    override val isIos = true

    override val sound = IosSoundPlayer()

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun scheduleReminders(reminders: List<Reminder>) {
        center.removeAllPendingNotificationRequests()
        val now = nowMs()
        reminders.forEach { r ->
            val seconds = (r.atMs - now) / 1000.0
            if (seconds < 1) return@forEach
            val content = UNMutableNotificationContent()
            content.setTitle(r.title)
            content.setBody(r.text)
            content.setSound(UNNotificationSound.defaultSound)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier("reminder-${r.id}", content, trigger)
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }

    override fun requestNotifications() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }

    override fun publishStatus(status: WidgetStatus) {
        bridge.statusChanged(AppDataCodec.json.encodeToString(WidgetStatus.serializer(), status))
    }

    override fun shareFile(name: String, mime: String, bytes: ByteArray) {
        bridge.shareFile(name, bytes.toNSData())
    }

    override fun pickTextFile(onResult: (String?) -> Unit) {
        bridge.pickTextFile { text -> dispatch_async(dispatch_get_main_queue()) { onResult(text) } }
    }

    override fun renderPdf(pages: List<PdfPage>): ByteArray {
        val json = AppDataCodec.json.encodeToString(ListSerializer(PdfPage.serializer()), pages)
        return bridge.renderPdf(json).toByteArray()
    }
}

/**
 * Звук через AVAudioEngine: генерируем буферы по ~0,4 с и ставим в очередь плееру.
 * Когда буфер доигрывает, на отдельной очереди готовим следующий. Режим «Воспроизведение»
 * и фоновый режим audio в Info.plist — чтобы играло при выключенном экране.
 */
class IosSoundPlayer : SoundPlayer() {
    private val engine = AVAudioEngine()
    private val node = AVAudioPlayerNode()
    private val format = AVAudioFormat(standardFormatWithSampleRate = SAMPLE_RATE.toDouble(), channels = 1u)
    private val queue = dispatch_queue_create("malysh.sound", null)
    private var attached = false
    private val scratch = FloatArray(FRAMES)

    @kotlin.concurrent.Volatile
    private var session: SoundSession? = null

    override fun play(type: SoundType, timerMinutes: Int, volume: Float) {
        halt()
        val audio = AVAudioSession.sharedInstance()
        audio.setCategory(AVAudioSessionCategoryPlayback, error = null)
        audio.setActive(true, error = null)
        if (!attached) {
            engine.attachNode(node)
            engine.connect(node, engine.mainMixerNode, format)
            attached = true
        }
        val s = SoundSession(type, timerMinutes, volume)
        session = s
        engine.startAndReturnError(null)
        repeat(3) { dispatch_async(queue) { scheduleNext(s) } }
        node.play()
        nowPlaying = NowPlaying(type, if (timerMinutes > 0) nowMs() + timerMinutes * 60_000L else null)
    }

    private fun scheduleNext(s: SoundSession) {
        if (session !== s) return
        if (s.finished) {
            dispatch_async(dispatch_get_main_queue()) { if (session === s) stop() }
            return
        }
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = FRAMES.toUInt()) ?: return
        buffer.frameLength = FRAMES.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return
        s.fill(scratch)
        for (i in 0 until FRAMES) channel[i] = scratch[i]
        node.scheduleBuffer(buffer) { dispatch_async(queue) { scheduleNext(s) } }
    }

    override fun setVolume(volume: Float) {
        session?.volume = volume
    }

    override fun stop() {
        halt()
        nowPlaying = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    private fun halt() {
        session = null
        node.stop()
        engine.stop()
    }

    private companion object {
        const val FRAMES = SAMPLE_RATE * 2 / 5
    }
}

fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
