// Тесты перенесены из app/src/test (SleepNormTest, TimeFmtTest, SynthTest).

import { describe, expect, it } from 'vitest'
import { DEFAULT_SETTINGS, ageDaysAt, ageMonthsAt, cycleMinutes, midMinutes, normForAgeMonths, targetMinutes, wakeUpInMinutes } from './norms'
import { MELODY_SAMPLES, SAMPLE_RATE, createGenerator, renderLoop, toWav } from './synth'
import { ageText, duration, plural, relative } from './time'

const MIN = 60_000
const DAY = 86_400_000
const now = new Date(2026, 9, 21, 12).getTime()
const s = (patch = {}) => ({ ...DEFAULT_SETTINGS, ...patch })

describe('нормы сна', () => {
  it('для 2 месяцев — 14-17 часов', () => {
    expect(normForAgeMonths(2)).toEqual({ minMinutes: 14 * 60, maxMinutes: 17 * 60 })
  })
  it('убывают с возрастом', () => {
    const newborn = midMinutes(normForAgeMonths(1))
    expect(newborn).toBeGreaterThan(midMinutes(normForAgeMonths(7)))
    expect(midMinutes(normForAgeMonths(7))).toBeGreaterThan(midMinutes(normForAgeMonths(12)))
  })
  it('без ручной настройки берётся середина нормы, ручная её перекрывает', () => {
    const baby = s({ birthDate: now - 62 * DAY })
    expect(targetMinutes(baby, now)).toBe(midMinutes(normForAgeMonths(2)))
    expect(targetMinutes({ ...baby, sleepGoalMinutes: 600 }, now)).toBe(600)
  })
  it('возраст считается от даты рождения, будущая дата даёт ноль', () => {
    expect(ageDaysAt(s({ birthDate: now - 62 * DAY }), now)).toBe(62)
    expect(ageDaysAt(s({ birthDate: now + DAY }), now)).toBe(0)
    expect(ageMonthsAt(s({ birthDate: now - 29 * DAY }), now)).toBe(0)
    expect(ageMonthsAt(s({ birthDate: now - 30 * DAY }), now)).toBe(1)
    expect(ageMonthsAt(s({ birthDate: now - 60 * DAY }), now)).toBe(2)
  })
  it('цикл сна: 75 по умолчанию, ручной перекрывает', () => {
    expect(cycleMinutes(s(), now)).toBe(75)
    expect(cycleMinutes(s({ sleepCycleMinutes: 40 }), now)).toBe(40)
  })
  it('прогноз пробуждения', () => {
    expect(wakeUpInMinutes(now, now, s())).toBe(75)
    expect(wakeUpInMinutes(now - 20 * MIN, now, s())).toBe(55)
    expect(wakeUpInMinutes(now - 74 * MIN, now, s())).toBe(1)
    expect(wakeUpInMinutes(now - 75 * MIN, now, s())).toBe(75)
    expect(wakeUpInMinutes(now - 150 * MIN, now, s())).toBe(75)
    for (let e = 0; e < 400; e++) {
      const left = wakeUpInMinutes(now - e * MIN, now, s())
      expect(left).toBeGreaterThanOrEqual(1)
      expect(left).toBeLessThanOrEqual(75)
    }
  })
})

describe('форматирование', () => {
  it('длительность', () => {
    expect(duration(0)).toBe('0 мин')
    expect(duration(45)).toBe('45 мин')
    expect(duration(60)).toBe('1 ч')
    expect(duration(150)).toBe('2 ч 30 мин')
  })
  it('относительное время', () => {
    expect(relative(now - 3 * MIN, now)).toBe('3 мин назад')
    expect(relative(now + 25 * MIN, now)).toBe('через 25 мин')
  })
  it('склонения и возраст', () => {
    expect(plural(1, 'день', 'дня', 'дней')).toBe('день')
    expect(plural(3, 'день', 'дня', 'дней')).toBe('дня')
    expect(plural(11, 'день', 'дня', 'дней')).toBe('дней')
    expect(plural(21, 'день', 'дня', 'дней')).toBe('день')
    expect(ageText(62)).toBe('2 месяца')
    expect(ageText(5)).toBe('5 дней')
    expect(ageText(400)).toBe('1 год 1 месяц')
  })
})

describe('синтез', () => {
  it('все звуки в диапазоне и не тишина', () => {
    for (const type of ['WHITE_NOISE', 'RAIN', 'DROPS', 'LULLABY'] as const) {
      const gen = createGenerator(type)
      let peak = 0
      for (let i = 0; i < SAMPLE_RATE * 5; i++) peak = Math.max(peak, Math.abs(gen()))
      expect(peak, type).toBeGreaterThan(0.01)
      expect(peak, type).toBeLessThan(1.5)
    }
  })
  it('колыбельная — ровно одна мелодия, ~30 секунд', () => {
    const loop = renderLoop('LULLABY')
    expect(loop.length).toBe(MELODY_SAMPLES)
    expect(loop.length / SAMPLE_RATE).toBeGreaterThan(29)
    expect(loop.length / SAMPLE_RATE).toBeLessThan(31)
  })
  it('стык петли шума без скачка', () => {
    const loop = renderLoop('WHITE_NOISE')
    expect(Math.abs(loop[loop.length - 1] - loop[0])).toBeLessThan(0.2)
  })
  it('WAV-заголовок', () => {
    const wav = new DataView(toWav(new Float32Array(100)))
    expect(wav.byteLength).toBe(244)
    expect(String.fromCharCode(wav.getUint8(0), wav.getUint8(1), wav.getUint8(2), wav.getUint8(3))).toBe('RIFF')
    expect(wav.getUint32(24, true)).toBe(SAMPLE_RATE)
  })
})
