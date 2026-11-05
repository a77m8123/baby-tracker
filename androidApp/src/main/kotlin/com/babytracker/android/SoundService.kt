package com.babytracker.android

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.babytracker.NowPlaying
import com.babytracker.SoundPlayer
import com.babytracker.logic.nowMs
import com.babytracker.sound.SAMPLE_RATE
import com.babytracker.sound.SoundSession
import com.babytracker.sound.SoundType

class AndroidSoundPlayer(private val context: Context) : SoundPlayer() {

    @Volatile
    internal var session: SoundSession? = null

    override fun play(type: SoundType, timerMinutes: Int, volume: Float) {
        session = SoundSession(type, timerMinutes, volume)
        nowPlaying = NowPlaying(type, if (timerMinutes > 0) nowMs() + timerMinutes * 60_000L else null)
        val intent = Intent(context, SoundService::class.java).setAction(SoundService.ACTION_PLAY)
        context.startForegroundService(intent)
    }

    override fun setVolume(volume: Float) {
        session?.volume = volume
    }

    override fun stop() {
        session = null
        nowPlaying = null
        context.startService(Intent(context, SoundService::class.java).setAction(SoundService.ACTION_STOP))
    }

    /** Звук выключили из шторки — сервис уже останавливается сам. */
    internal fun clear() {
        session = null
        nowPlaying = null
    }

    internal fun onFinished(finished: SoundSession) {
        if (session === finished) {
            session = null
            nowPlaying = null
        }
    }
}

/** Играет синтезированный звук в фоне, пока экран выключен. */
class SoundService : Service() {

    @Volatile
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = AppGraph.platform(this).sound
        when (intent?.action) {
            ACTION_PLAY -> {
                val session = player.session ?: return stop()
                startAsForeground(session.type)
                startThread(session, player)
            }
            ACTION_STOP -> return stop()
            ACTION_STOP_FROM_NOTIFICATION -> {
                player.clear()
                return stop()
            }
            else -> return stop()
        }
        return START_NOT_STICKY
    }

    private fun stop(): Int {
        thread?.interrupt()
        thread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun startThread(session: SoundSession, player: AndroidSoundPlayer) {
        thread?.interrupt()
        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(minBuf * 2, 8192)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val floats = FloatArray(bufferSize / 4)
            val shorts = ShortArray(floats.size)
            try {
                track.play()
                while (!Thread.currentThread().isInterrupted && player.session === session && !session.finished) {
                    session.fill(floats)
                    for (i in floats.indices) shorts[i] = (floats[i] * 32767f).toInt().toShort()
                    track.write(shorts, 0, shorts.size)
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
            if (session.finished) {
                player.onFinished(session)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.apply {
            name = "malysh-sound"
            start()
        }
    }

    private fun startAsForeground(type: SoundType) {
        val notification = buildNotification(type)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        thread?.interrupt()
        super.onDestroy()
    }

    private fun buildNotification(type: SoundType): Notification {
        BabyApp.createChannels(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SoundService::class.java).setAction(ACTION_STOP_FROM_NOTIFICATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, BabyApp.CHANNEL_SOUNDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(type.title)
            .setContentText(type.subtitle)
            .setContentIntent(open)
            .addAction(0, "Выключить", stop)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_PLAY = "com.babytracker.sound.PLAY"
        const val ACTION_STOP = "com.babytracker.sound.STOP"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.babytracker.sound.STOP_NOTIF"
        private const val NOTIF_ID = 4242
    }
}
