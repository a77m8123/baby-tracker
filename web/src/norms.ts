// Настройки ребёнка и нормы сна по возрасту. Порт data/Settings.kt и data/SleepNorm.kt.
// Нормы — общие ориентиры педиатров, не медицинские рекомендации.

export interface BabySettings {
  name: string
  /** Дата рождения, мс epoch. 0 = не задана. */
  birthDate: number
  /** Целевой сон в сутки, минуты. 0 = брать норму по возрасту. */
  sleepGoalMinutes: number
  /** Интервал кормления, минуты. */
  feedIntervalMinutes: number
  /** Длительность цикла сна для прогноза пробуждения, минуты. */
  sleepCycleMinutes: number
  /** Объём бутылочки по умолчанию, мл. */
  defaultBottleMl: number
  /** Автовыключение звука, минуты. 0 = не выключать. */
  soundTimerMinutes: number
  notificationsEnabled: boolean
}

export const DEFAULT_SETTINGS: BabySettings = {
  name: 'Малыш',
  birthDate: 0,
  sleepGoalMinutes: 0,
  feedIntervalMinutes: 180,
  sleepCycleMinutes: 75,
  defaultBottleMl: 90,
  soundTimerMinutes: 30,
  notificationsEnabled: true,
}

export function ageDaysAt(s: BabySettings, now = Date.now()): number {
  if (s.birthDate <= 0 || now < s.birthDate) return 0
  return Math.floor((now - s.birthDate) / 86_400_000)
}

export const ageMonthsAt = (s: BabySettings, now = Date.now()) => Math.floor(ageDaysAt(s, now) / 30)

export interface NormRange { minMinutes: number; maxMinutes: number }

export const midMinutes = (r: NormRange) => Math.floor((r.minMinutes + r.maxMinutes) / 2)

const range = (minH: number, maxH: number): NormRange => ({ minMinutes: minH * 60, maxMinutes: maxH * 60 })

/** Возраст в месяцах -> норма сна в сутки. */
export function normForAgeMonths(months: number): NormRange {
  if (months < 4) return range(14, 17)   // 0-4 мес
  if (months < 6) return range(12, 16)   // 4-6 мес
  if (months < 9) return range(12, 15)   // 6-9 мес
  if (months < 12) return range(11, 14)  // 9-12 мес
  if (months < 18) return range(10, 13)  // 1-1.5 г
  if (months < 24) return range(11, 14)  // 1.5-2 г
  return range(10, 13)                   // 2-3 г
}

/** Целевой сон на сутки: ручная настройка или середина нормы для возраста. */
export function targetMinutes(s: BabySettings, now = Date.now()): number {
  return s.sleepGoalMinutes > 0 ? s.sleepGoalMinutes : midMinutes(normForAgeMonths(ageMonthsAt(s, now)))
}

export function cycleMinutes(s: BabySettings, now = Date.now()): number {
  if (s.sleepCycleMinutes > 0) return s.sleepCycleMinutes
  const months = ageMonthsAt(s, now)
  return months < 4 ? 75 : months < 12 ? 60 : 45
}

/** Через сколько минут ждать пробуждения, если сон начался в sleepStart. */
export function wakeUpInMinutes(sleepStart: number, now: number, s: BabySettings): number {
  const cycle = cycleMinutes(s, now)
  const elapsed = Math.floor((now - sleepStart) / 60_000)
  if (elapsed < cycle) return cycle - elapsed
  const overshoot = elapsed % cycle
  return overshoot === 0 ? cycle : cycle - overshoot
}
