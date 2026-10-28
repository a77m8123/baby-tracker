package com.babytracker

import com.babytracker.sound.SoundType
import com.babytracker.sound.Synth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SynthTest {

    private fun render(type: SoundType, samples: Int): FloatArray {
        val gen = Synth.Generator(type, seed = 42)
        return FloatArray(samples) { gen.nextSample() }
    }

    private fun rms(data: FloatArray): Double {
        var sum = 0.0
        for (v in data) sum += (v.toDouble() * v)
        return sqrt(sum / data.size)
    }

    @Test
    fun `все звуки дают сигнал в допустимом диапазоне`() {
        for (type in SoundType.entries) {
            val data = render(type, 44_100)
            for ((i, v) in data.withIndex()) {
                assertTrue(
                    "$type: сэмпл $i вышел за диапазон: $v",
                    v >= -1f && v <= 1f
                )
            }
        }
    }

    @Test
    fun `белый шум не тишина`() {
        val data = render(SoundType.WHITE_NOISE, 44_100)
        assertTrue("белый шум почти беззвучный: rms=${rms(data)}", rms(data) > 0.01)
    }

    @Test
    fun `дождь не тишина`() {
        val data = render(SoundType.RAIN, 44_100)
        assertTrue("дождь почти беззвучный: rms=${rms(data)}", rms(data) > 0.01)
    }

    @Test
    fun `колыбельная не тишина`() {
        val data = render(SoundType.LULLABY, 44_100 * 3)
        assertTrue("колыбельная почти беззвучная: rms=${rms(data)}", rms(data) > 0.001)
    }

    @Test
    fun `капли имеют тишину между каплями`() {
        // за 5 секунд должно быть и звучание, и пауза
        val data = render(SoundType.DROPS, 44_100 * 5)
        val loud = data.count { abs(it) > 0.05f }
        val quiet = data.count { abs(it) <= 0.001f }
        assertTrue("капли звучат непрерывно: громких $loud", loud < data.size / 2)
        assertTrue("капель совсем нет: громких $loud", loud > 0)
        assertTrue("нет тишины между каплями", quiet > 0)
    }

    @Test
    fun `генератор даёт разные сэмплы, а не константу`() {
        val data = render(SoundType.WHITE_NOISE, 1000)
        assertNotEquals("сигнал — константа", data[0], data[100])
        assertNotEquals("сигнал — константа", data[300], data[900])
    }

    @Test
    fun `два генератора с одним seed дают одинаковый сигнал`() {
        val a = Synth.Generator(SoundType.WHITE_NOISE, seed = 7)
        val b = Synth.Generator(SoundType.WHITE_NOISE, seed = 7)
        repeat(100) {
            assertEquals(a.nextSample(), b.nextSample(), 0.0001f)
        }
    }

    @Test
    fun `заполнение буфера не выходит за границы`() {
        val gen = Synth.Generator(SoundType.RAIN, seed = 3)
        val buf = ShortArray(4096)
        repeat(50) {
            assertTrue(gen.render(buf))
        }
        assertEquals(4096, buf.size)
    }

    @Test
    fun `звук можно опознать по имени`() {
        for (type in SoundType.entries) {
            assertEquals(type, SoundType.fromName(type.name))
        }
        assertEquals(null, SoundType.fromName("НЕТ_ТАКОГО"))
        assertEquals(null, SoundType.fromName(null))
    }
}
