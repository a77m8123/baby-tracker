package com.babytracker.sound

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Проигрывает синтезированный звук в фоне, пока экран выключен.
 * Плюс таймер автовыключения.
 */
class SoundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioJob: Job? = null
    private var timerJob: Job? = null
    private var currentType: SoundType? = null
    private var timerMinutes: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val type = SoundType.fromName(intent.getStringExtra(EXTRA_TYPE)) ?: SoundType.WHITE_NOISE
                val minutes = intent.getIntExtra(EXTRA_TIMER_MINUTES, 0)
                startPlayback(type, minutes)
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    private fun startPlayback(type: SoundType, minutes: Int) {
        currentType = type
        timerMinutes = minutes
        startForeground(NOTIF_ID, buildNotification(type, minutes))

        audioJob?.cancel()
        audioJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                Synth.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = maxOf(minBuf * 2, 4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(Synth.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val generator = Synth.Generator(type)
            val buffer = ShortArray(bufferSize / 2)
            try {
                track.play()
                while (scope.isActive) {
                    generator.render(buffer)
                    track.write(buffer, 0, buffer.size)
                }
            } finally {
                try {
                    track.stop()
                } catch (_: IllegalStateException) {
                }
                track.release()
            }
        }

        timerJob?.cancel()
        if (minutes > 0) {
            timerJob = scope.launch {
                delay(minutes * 60_000L)
                stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        timerJob?.cancel()
        timerJob = null
        audioJob?.cancel()
        audioJob = null
        currentType = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(type: SoundType, minutes: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val timerText = if (minutes > 0) " · выключится через $minutes мин" else ""

        return NotificationCompat.Builder(this, Notifications.CHANNEL_SOUNDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(type.title)
            .setContentText("${type.subtitle}$timerText")
            .setContentIntent(open)
            .addAction(0, "Стоп", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 200
        const val ACTION_PLAY = "com.babytracker.PLAY_SOUND"
        const val ACTION_STOP = "com.babytracker.STOP_SOUND"
        const val EXTRA_TYPE = "type"
        const val EXTRA_TIMER_MINUTES = "timer_minutes"

        fun play(context: Context, type: SoundType, timerMinutes: Int) {
            val intent = Intent(context, SoundService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_TIMER_MINUTES, timerMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SoundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
