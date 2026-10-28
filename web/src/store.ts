// Хранилище событий и настроек. Всё локально в браузере (localStorage), без сервера.
// Порт Room-базы (data/Entities.kt, data/BabyDao.kt) и вьюмоделей.

import { BabySettings, DEFAULT_SETTINGS, targetMinutes, wakeUpInMinutes } from './norms'
import { startOfDay } from './time'

export type FeedType = 'BREAST' | 'BOTTLE' | 'SOLIDS'

export interface Feeding { id: number; timestamp: number; type: FeedType; amountMl: number | null }
export interface Sleep { id: number; start: number; end: number | null }
export interface Diaper { id: number; timestamp: number; withStool: boolean }

interface Data {
  version: 1
  nextId: number
  feedings: Feeding[]
  sleeps: Sleep[]
  diapers: Diaper[]
  settings: BabySettings
}

const KEY = 'baby-tracker'

function emptyData(): Data {
  return { version: 1, nextId: 1, feedings: [], sleeps: [], diapers: [], settings: { ...DEFAULT_SETTINGS } }
}

function load(): Data {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return emptyData()
    const parsed = JSON.parse(raw) as Data
    return { ...emptyData(), ...parsed, settings: { ...DEFAULT_SETTINGS, ...parsed.settings } }
  } catch {
    return emptyData()
  }
}

let data = load()
const listeners = new Set<() => void>()

function commit() {
  try {
    localStorage.setItem(KEY, JSON.stringify(data))
  } catch {
    // переполнение или приватный режим — данные останутся до перезагрузки
  }
  listeners.forEach((l) => l())
}

export function subscribe(l: () => void) {
  listeners.add(l)
  return () => listeners.delete(l)
}

// Просим браузер не вычищать данные при нехватке места.
navigator.storage?.persist?.().catch(() => {})

export const settings = () => data.settings

/** silent — сохранить без перерисовки (поле ввода уже показывает новое значение). */
export function updateSettings(patch: Partial<BabySettings>, silent = false) {
  data.settings = { ...data.settings, ...patch }
  if (silent) {
    try { localStorage.setItem(KEY, JSON.stringify(data)) } catch { /* см. commit */ }
  } else {
    commit()
  }
}

const nextId = () => data.nextId++

export const activeSleep = (): Sleep | undefined =>
  data.sleeps.filter((s) => s.end === null).sort((a, b) => b.start - a.start)[0]

export function logFeeding(type: FeedType, amountMl: number | null = null) {
  data.feedings.push({ id: nextId(), timestamp: Date.now(), type, amountMl })
  commit()
}

export function logDiaper(withStool: boolean) {
  data.diapers.push({ id: nextId(), timestamp: Date.now(), withStool })
  commit()
}

export function startSleep() {
  if (activeSleep()) return
  data.sleeps.push({ id: nextId(), start: Date.now(), end: null })
  commit()
}

export function wakeUp() {
  const active = activeSleep()
  if (!active) return
  active.end = Date.now()
  commit()
}

export type FeedEvent =
  | { kind: 'sleep'; timestamp: number; entity: Sleep }
  | { kind: 'feed'; timestamp: number; entity: Feeding }
  | { kind: 'diaper'; timestamp: number; entity: Diaper }

export interface HomeState {
  settings: BabySettings
  sleepStartedAt: number | null
  todaySleepMinutes: number
  sleepGoalMinutes: number
  wakeUpInMinutes: number
  lastFeedAt: number | null
  feedsToday: number
  feedAmountTodayMl: number
  events: FeedEvent[]
}

const minutesBetween = (a: number, b: number) => Math.floor((b - a) / 60_000)

export function homeState(now = Date.now()): HomeState {
  const s = data.settings
  const dayStart = startOfDay(now)
  const active = activeSleep()

  const finishedToday = data.sleeps
    .filter((x) => x.end !== null && x.start >= dayStart)
    .reduce((sum, x) => sum + minutesBetween(x.start, x.end!), 0)
  const activeMinutes = active ? minutesBetween(active.start, now) : 0

  const feedings = [...data.feedings].sort((a, b) => b.timestamp - a.timestamp)
  const todayFeeds = feedings.filter((f) => f.timestamp >= dayStart)

  const events: FeedEvent[] = [
    ...data.sleeps.map((e) => ({ kind: 'sleep' as const, timestamp: e.start, entity: e })),
    ...data.feedings.map((e) => ({ kind: 'feed' as const, timestamp: e.timestamp, entity: e })),
    ...data.diapers.map((e) => ({ kind: 'diaper' as const, timestamp: e.timestamp, entity: e })),
  ].sort((a, b) => b.timestamp - a.timestamp)

  return {
    settings: s,
    sleepStartedAt: active?.start ?? null,
    todaySleepMinutes: finishedToday + activeMinutes,
    sleepGoalMinutes: targetMinutes(s, now),
    wakeUpInMinutes: active ? wakeUpInMinutes(active.start, now, s) : 0,
    lastFeedAt: feedings[0]?.timestamp ?? null,
    feedsToday: todayFeeds.length,
    feedAmountTodayMl: todayFeeds.reduce((sum, f) => sum + (f.amountMl ?? 0), 0),
    events: events.slice(0, 60),
  }
}

export interface DayStat { dayStart: number; sleepMinutes: number; feedCount: number; sleepSegments: number }

export function weekStats(now = Date.now()) {
  const days: DayStat[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dayStart = startOfDay(d.getTime())
    const next = new Date(dayStart)
    next.setDate(next.getDate() + 1)
    const dayEnd = next.getTime()

    const sleeps = data.sleeps.filter((x) => x.end !== null && x.start >= dayStart && x.end < dayEnd)
    const feeds = data.feedings.filter((f) => f.timestamp >= dayStart && f.timestamp < dayEnd)
    days.push({
      dayStart,
      sleepMinutes: sleeps.reduce((sum, x) => sum + minutesBetween(x.start, x.end!), 0),
      feedCount: feeds.length,
      sleepSegments: sleeps.length,
    })
  }
  return {
    days,
    avgSleepMinutes: Math.floor(days.reduce((s, d) => s + d.sleepMinutes, 0) / days.length),
    avgFeeds: days.reduce((s, d) => s + d.feedCount, 0) / days.length,
  }
}
