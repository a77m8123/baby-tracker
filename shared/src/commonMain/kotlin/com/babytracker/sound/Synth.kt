package com.babytracker.sound

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Доступные звуки. Всё синтезируется на лету — аудиофайлов в приложении нет. */
enum class SoundType(val title: String, val subtitle: String) {
    WHITE_NOISE("Белый шум", "Мягкое шипение, приглушает всё вокруг"),
    HEARTBEAT("Сердцебиение", "Как у мамы в животике"),
    HAIRDRYER("Фен", "Ровный гул — многие засыпают за минуту"),
    RAIN("Дождь", "Ровный стук капель по крыше"),
    OCEAN("Волны", "Медленный прибой"),
    DROPS("Капли", "Редкие капли в тишине"),
    LULLABY("Колыбельная", "Нежная мелодия по кругу"),
    ;

    companion object {
        fun fromName(name: String?): SoundType? = entries.firstOrNull { it.name == name }
    }
}

const val SAMPLE_RATE = 44100

/** Бесконечный генератор сэмплов в диапазоне примерно −1…1. */
class Generator(val type: SoundType, seed: Int = 1234) {
    private val rnd = Random(seed)

    // фильтры
    private var lp1 = 0f
    private var lp2 = 0f
    private var lp3 = 0f
    private var brown = 0f

    // капли
    private var dropPhase = 0.0
    private var dropEnv = 0f
    private var dropFreq = 0f
    private var nextDropIn = 0

    // сердце, волны, фен
    private var t = 0L
    private var beatPhase = 0.0
    private var wavePeriod = 9.0
    private var waveStart = 0L
    private var humPhase = 0.0

    // колыбельная
    private var noteIndex = 0
    private var noteLeft = 0
    private var noteTotal = 1
    private var noteFreq = 0.0
    private var melodyPhase = 0.0

    init {
        if (type == SoundType.DROPS || type == SoundType.RAIN) scheduleDrop()
    }

    private fun white() = rnd.nextFloat() * 2f - 1f

    private fun scheduleDrop() {
        nextDropIn = rnd.nextInt(SAMPLE_RATE / 3, SAMPLE_RATE * 3 / 2)
    }

    fun next(): Float {
        t++
        return when (type) {
            SoundType.WHITE_NOISE -> whiteNoise()
            SoundType.HEARTBEAT -> heartbeat()
            SoundType.HAIRDRYER -> hairdryer()
            SoundType.RAIN -> rain()
            SoundType.OCEAN -> ocean()
            SoundType.DROPS -> drops()
            SoundType.LULLABY -> lullaby()
        }
    }

    /** Белый шум с лёгким фильтром — мягче чистого. */
    private fun whiteNoise(): Float {
        lp1 = lp1 * 0.85f + white() * 0.15f
        return lp1 * 1.6f
    }

    /** «Тук-тук» около 70 ударов в минуту поверх глухого шума, как в утробе. */
    private fun heartbeat(): Float {
        brown = (brown + white() * 0.02f) * 0.998f
        val womb = brown * 0.9f
        val beatSamples = SAMPLE_RATE * 60.0 / 70.0
        beatPhase += 1.0
        if (beatPhase >= beatSamples) beatPhase -= beatSamples
        val sec = beatPhase / SAMPLE_RATE
        fun thump(at: Double, gain: Float): Float {
            val x = sec - at
            if (x < 0 || x > 0.25) return 0f
            val env = exp(-x * 22.0).toFloat() * (1f - exp(-x * 400.0).toFloat())
            return (sin(2 * PI * 52.0 * x).toFloat() + 0.4f * sin(2 * PI * 95.0 * x).toFloat()) * env * gain
        }
        return womb + thump(0.0, 0.9f) + thump(0.3, 0.6f)
    }

    /** Фен: широкий шум и гул моторчика с лёгким дрожанием. */
    private fun hairdryer(): Float {
        lp1 = lp1 * 0.6f + white() * 0.4f
        lp2 = lp2 * 0.97f + lp1 * 0.03f
        val wobble = 1.0 + 0.004 * sin(2 * PI * 0.7 * t / SAMPLE_RATE)
        humPhase += 2 * PI * 118.0 * wobble / SAMPLE_RATE
        if (humPhase > 2 * PI) humPhase -= 2 * PI
        val hum = sin(humPhase).toFloat() * 0.12f + sin(humPhase * 2).toFloat() * 0.06f + sin(humPhase * 3).toFloat() * 0.03f
        return (lp1 - lp2) * 0.9f + lp2 * 1.4f + hum
    }

    /** Дождь: «розовый» шум и редкие капли. */
    private fun rain(): Float {
        lp1 = lp1 * 0.92f + white() * 0.08f
        lp2 = lp2 * 0.75f + lp1 * 0.25f
        if (nextDropIn > 0) {
            nextDropIn--
        } else {
            scheduleDrop()
            dropEnv = 1f
            dropFreq = 700f + rnd.nextFloat() * 1400f
            dropPhase = 0.0
        }
        var drop = 0f
        if (dropEnv > 0.001f) {
            dropPhase += 2.0 * PI * dropFreq / SAMPLE_RATE
            drop = sin(dropPhase).toFloat() * dropEnv * 0.25f
            dropEnv *= 0.9995f
        }
        return lp2 * 2.2f + drop
    }

    /** Волны: шум, громкость которого медленно накатывает и отступает. */
    private fun ocean(): Float {
        lp1 = lp1 * 0.9f + white() * 0.1f
        lp3 = lp3 * 0.995f + lp1 * 0.005f
        val pos = (t - waveStart).toDouble() / SAMPLE_RATE / wavePeriod
        if (pos >= 1.0) {
            waveStart = t
            wavePeriod = 7.0 + rnd.nextDouble() * 5.0
        }
        val p = pos.coerceIn(0.0, 1.0)
        // быстрый накат, долгий откат
        val swell = if (p < 0.35) sin(p / 0.35 * PI / 2) else exp(-(p - 0.35) * 3.5)
        val level = (0.15 + 0.85 * swell).toFloat()
        return (lp1 * 1.6f + lp3 * 6f) * level
    }

    /** Редкие капли — почти полная тишина между ними. */
    private fun drops(): Float {
        if (dropEnv > 0.001f) {
            dropPhase += 2.0 * PI * dropFreq / SAMPLE_RATE
            val out = sin(dropPhase).toFloat() * dropEnv * 0.55f
            dropEnv *= 0.99985f
            return out
        }
        if (nextDropIn > 0) {
            nextDropIn--
            return 0f
        }
        scheduleDrop()
        dropEnv = 1f
        dropFreq = 500f + rnd.nextFloat() * 900f
        dropPhase = 0.0
        return 0f
    }

    /** Колыбельная: простая мелодия синусом с мягкой огибающей. */
    private fun lullaby(): Float {
        if (noteLeft <= 0) {
            val (name, beats) = MELODY[noteIndex]
            noteIndex = (noteIndex + 1) % MELODY.size
            noteFreq = FREQS[name] ?: 261.63
            noteTotal = (beats * BEAT_SAMPLES).toInt().coerceAtLeast(1)
            noteLeft = noteTotal
        }
        noteLeft--
        val progress = (noteTotal - noteLeft).toFloat() / noteTotal
        val env = if (progress < 0.15f) progress / 0.15f else exp(-(progress - 0.15f) * 3.2f)
        melodyPhase += 2.0 * PI * noteFreq / SAMPLE_RATE
        if (melodyPhase > 2.0 * PI) melodyPhase -= 2.0 * PI
        val tone = sin(melodyPhase).toFloat() + (sin(melodyPhase * 2.0) * 0.18).toFloat()
        return tone * env * 0.5f
    }

    private companion object {
        const val BEAT_SAMPLES = SAMPLE_RATE * 60 / 74
        val MELODY = listOf(
            "E4" to 1.0, "E4" to 1.0, "G4" to 2.0,
            "E4" to 1.0, "E4" to 1.0, "G4" to 2.0,
            "E4" to 1.0, "C4" to 1.0, "D4" to 1.0, "E4" to 2.0,
            "F4" to 2.0, "F4" to 1.0, "E4" to 1.0, "D4" to 1.0, "C4" to 2.0,
            "G4" to 1.0, "F4" to 1.0, "E4" to 2.0,
            "C4" to 1.0, "E4" to 1.0, "G4" to 2.0,
            "E4" to 1.0, "C4" to 1.0, "D4" to 1.0, "E4" to 2.0,
            "D4" to 2.0, "C4" to 4.0,
        )
        val FREQS = mapOf(
            "C4" to 261.63, "D4" to 293.66, "E4" to 329.63,
            "F4" to 349.23, "G4" to 392.00, "A4" to 440.00, "B4" to 493.88,
        )
    }
}

/**
 * Один сеанс звука: плавное начало и, если задан таймер, плавное затухание за последнюю минуту.
 * Платформа просто забирает сэмплы через [fill], пока [finished] не станет true.
 */
class SoundSession(val type: SoundType, timerMinutes: Int, volume: Float) {
    private val generator = Generator(type)
    private val totalSamples: Long = if (timerMinutes > 0) timerMinutes * 60L * SAMPLE_RATE else Long.MAX_VALUE
    private var played = 0L

    @Volatile
    var volume: Float = volume.coerceIn(0f, 1f)

    val finished: Boolean get() = played >= totalSamples

    fun fill(out: FloatArray, count: Int = out.size) {
        val vol = volume
        for (i in 0 until count) {
            out[i] = if (played >= totalSamples) 0f else (generator.next() * gain(played) * vol).coerceIn(-1f, 1f)
            played++
        }
    }

    private fun gain(sample: Long): Float {
        val fadeIn = (sample.toFloat() / FADE_IN).coerceAtMost(1f)
        val left = totalSamples - sample
        val fadeOut = if (left < FADE_OUT) left.toFloat() / FADE_OUT else 1f
        return fadeIn * fadeOut
    }

    private companion object {
        const val FADE_IN = SAMPLE_RATE * 3L
        const val FADE_OUT = SAMPLE_RATE * 60L
    }
}
