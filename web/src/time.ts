// Форматирование времени и склонения. Порт util/TimeFmt.kt.

const pad = (n: number) => String(n).padStart(2, '0')

export function clock(ts: number): string {
  const d = new Date(ts)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** «2 ч 30 мин» */
export function duration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0 && m > 0) return `${h} ч ${m} мин`
  if (h > 0) return `${h} ч`
  return `${m} мин`
}

/** «через 25 мин» / «3 мин назад» */
export function relative(ts: number, now = Date.now()): string {
  const diff = now - ts
  const text = duration(Math.floor(Math.abs(diff) / 60_000))
  return diff >= 0 ? `${text} назад` : `через ${text}`
}

export function startOfDay(ts = Date.now()): number {
  const d = new Date(ts)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

/** Возраст: «2 месяца» */
export function ageText(days: number): string {
  if (days < 0) return 'не указан'
  if (days < 31) return `${days} ${plural(days, 'день', 'дня', 'дней')}`
  if (days < 365) {
    const m = Math.floor(days / 30)
    return `${m} ${plural(m, 'месяц', 'месяца', 'месяцев')}`
  }
  const y = Math.floor(days / 365)
  const m = Math.floor((days % 365) / 30)
  const years = `${y} ${plural(y, 'год', 'года', 'лет')}`
  return m === 0 ? years : `${years} ${m} ${plural(m, 'месяц', 'месяца', 'месяцев')}`
}

export function plural(n: number, one: string, few: string, many: string): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return many
  switch (n % 10) {
    case 1: return one
    case 2: case 3: case 4: return few
    default: return many
  }
}

const WEEKDAYS = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб']

export const weekday = (ts: number) => WEEKDAYS[new Date(ts).getDay()]
export const dayOfMonth = (ts: number) => String(new Date(ts).getDate())
