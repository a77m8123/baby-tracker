package com.babytracker.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Синтез звуков сэмплами. Никаких аудиофайлов: всё считается на лету.
 * Формат — моно PCM 16 bit, 44100 Гц.
 */
object Synth {

    const val SAMPLE_RATE = 44100

    /** Простой ГПСЧ с плавным шумом (значения в диапазоне -1..1). */
    private class ValueNoise(seed: Int) {
        private val rnd = Random(seed)
        private var last = 0f

        fun next(): Float {
            last = (last + (rnd.nextFloat() * 2f - 1f) * 0.02f).coerceIn(-1f, 1f)
            return last
        }
    }

    /**
     * Генератор потока звука. Вызывается [render] пока не вернёт false.
     */
    class Generator(val type: SoundType, seed: Int = 1234) {
        private val rnd = Random(seed)
        private val noise = ValueNoise(seed)

        // состояние фильтров
        private var lp1 = 0f
        private var lp2 = 0f
        private var hpPrevIn = 0f
        private var hpPrevOut = 0f

        // для капель
        private var dropPhase = 0.0
        private var dropEnv = 0f
        private var dropFreq = 0f
        private var nextDropInSamples = 0

        // для дождя
        private var rainEnv = 0f

        // для колыбельной
        private val melody = Melody()
        private var melodyPhase = 0.0
        private var melodyNoteSamplesLeft = 0
        private var currentFreq = 0.0

        init {
            if (type == SoundType.DROPS) scheduleDrop()
        }

        private fun scheduleDrop() {
            nextDropInSamples = rnd.nextInt(SAMPLE_RATE / 3, SAMPLE_RATE * 3 / 2)
        }

        /** Заполняет буфер. Возвращает false, когда генератор исчерпан (для разовых звуков). */
        fun render(buffer: ShortArray): Boolean {
            val n = buffer.size
            var i = 0
            while (i < n) {
                val s = nextSample()
                buffer[i] = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                i++
            }
            return true
        }

        fun nextSample(): Float = when (type) {
            SoundType.WHITE_NOISE -> whiteNoise()
            SoundType.RAIN -> rain()
            SoundType.DROPS -> drops()
            SoundType.LULLABY -> lullaby()
        }

        /** Белый шум с лёгким низкочастотным фильтром — мягче, чем чистый белый. */
        private fun whiteNoise(): Float {
            val raw = rnd.nextFloat() * 2f - 1f
            lp1 = lp1 * 0.85f + raw * 0.15f
            return lp1 * 1.6f
        }

        /** Дождь: розовый шум + редкие капли. */
        private fun rain(): Float {
            val raw = rnd.nextFloat() * 2f - 1f
            lp1 = lp1 * 0.92f + raw * 0.08f
            lp2 = lp2 * 0.75f + lp1 * 0.25f

            if (nextDropInSamples > 0) {
                nextDropInSamples--
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

        /** Редкие капли в тишине — почти полная тишина между ними. */
        private fun drops(): Float {
            // капля звучит — дорисовываем её хвост
            if (dropEnv > 0.001f) {
                dropPhase += 2.0 * PI * dropFreq / SAMPLE_RATE
                val out = sin(dropPhase).toFloat() * dropEnv * 0.55f
                dropEnv *= 0.99985f
                return out
            }
            // тишина до следующей капли
            if (nextDropInSamples > 0) {
                nextDropInSamples--
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
            if (melodyNoteSamplesLeft <= 0) {
                val note = melody.next() ?: return 0f
                currentFreq = note.freq
                melodyNoteSamplesLeft = (note.beats * BEAT_SAMPLES).toInt()
            }
            melodyNoteSamplesLeft--

            val total = (melody.currentBeats * BEAT_SAMPLES).toInt().coerceAtLeast(1)
            val pos = total - melodyNoteSamplesLeft
            val progress = pos.toFloat() / total

            // огибающая: мягкая атака и затухание
            val env = if (progress < 0.15f) progress / 0.15f
            else exp(-(progress - 0.15f) * 3.2f).toFloat()

            melodyPhase += 2.0 * PI * currentFreq / SAMPLE_RATE
            if (melodyPhase > 2.0 * PI) melodyPhase -= 2.0 * PI

            val fundamental = sin(melodyPhase).toFloat()
            val overtone = (sin(melodyPhase * 2.0) * 0.18f).toFloat()
            return (fundamental + overtone) * env * 0.5f
        }

        private companion object {
            const val BEAT_SAMPLES = SAMPLE_RATE * 60 / 74 // ~74 BPM
        }
    }

    private class Melody {
        data class Note(val freq: Double, val beats: Double)

        private val notes = listOf(
            "E4" to 1.0, "E4" to 1.0, "G4" to 2.0,
            "E4" to 1.0, "E4" to 1.0, "G4" to 2.0,
            "E4" to 1.0, "C4" to 1.0, "D4" to 1.0, "E4" to 2.0,
            "F4" to 2.0, "F4" to 1.0, "E4" to 1.0, "D4" to 1.0, "C4" to 2.0,
            "G4" to 1.0, "F4" to 1.0, "E4" to 2.0,
            "C4" to 1.0, "E4" to 1.0, "G4" to 2.0,
            "E4" to 1.0, "C4" to 1.0, "D4" to 1.0, "E4" to 2.0,
            "D4" to 2.0, "C4" to 2.0,
        )
        private val freqs = mapOf(
            "C4" to 261.63, "D4" to 293.66, "E4" to 329.63,
            "F4" to 349.23, "G4" to 392.00, "A4" to 440.00, "B4" to 493.88,
        )

        var index = 0
        var currentBeats = 1.0

        fun next(): Note? {
            if (index >= notes.size) {
                index = 0
            }
            val (name, beats) = notes[index]
            index++
            currentBeats = beats
            return Note(freqs[name] ?: 261.63, beats)
        }
    }

    /** Простая Фурье-образная волна для произвольной частоты. */
    fun sine(freq: Double, phase: Double): Float = sin(phase).toFloat()

    /** Плавное нарастание/затухание (экспонента). */
    fun decay(x: Float, k: Float): Float = exp(-x * k).toFloat()

    /** Мощность для нормализации. */
    fun power(x: Float): Float = x.pow(2)
}
