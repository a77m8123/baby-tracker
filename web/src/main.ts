// Экраны приложения. Порт ui/*Screen.kt: главный, звуки, неделя, настройки.

import './style.css'
import { icon } from './icons'
import { ageDaysAt, ageMonthsAt, midMinutes, normForAgeMonths } from './norms'
import * as player from './player'
import * as store from './store'
import { SOUNDS, SoundType } from './synth'
import { ageText, clock, dayOfMonth, duration, relative, weekday } from './time'

type Screen = 'home' | 'sounds' | 'stats' | 'settings'

const app = document.getElementById('app')!

const esc = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)

const screen = (): Screen => {
  const h = location.hash.slice(1)
  return h === 'sounds' || h === 'stats' || h === 'settings' ? h : 'home'
}

// Таймер звука на экране «Звуки»; по умолчанию — из настроек.
let soundTimer = store.settings().soundTimerMinutes

// ---------- Главный ----------

function homeView(now: number): string {
  const st = store.homeState(now)
  const s = st.settings
  const sleeping = st.sleepStartedAt !== null
  const currentSleep = sleeping ? Math.floor((now - st.sleepStartedAt!) / 60_000) : 0
  const sinceFeed = st.lastFeedAt ? Math.floor((now - st.lastFeedAt) / 60_000) : 0
  const untilFeed = Math.max(0, s.feedIntervalMinutes - sinceFeed)
  const left = Math.max(0, st.sleepGoalMinutes - st.todaySleepMinutes)

  const feedLabel = st.feedsToday === 0 ? 'сегодня ещё не кормили' : untilFeed === 0 ? 'пора кормить' : 'следующее кормление'

  return `
  <header class="top">
    <div>
      <h1>${esc(s.name)}</h1>
      <p class="muted">${ageText(ageDaysAt(s, now))}</p>
    </div>
    <nav class="row gap8">
      <a class="btn outline" href="#stats">Неделя</a>
      <a class="btn outline" href="#settings">Настройки</a>
    </nav>
  </header>

  <section class="card ${sleeping ? 'card-primary' : ''}">
    <div class="card-title"><span class="ic primary">${icon.moon}</span>${sleeping ? 'Спит' : 'Не спит'}</div>
    ${sleeping
      ? `${bigStat(duration(currentSleep), 'текущий сон')}
         <p class="body">Проснётся примерно в ${clock(now + st.wakeUpInMinutes * 60_000)}</p>`
      : bigStat(duration(st.todaySleepMinutes), 'сон за сегодня')}
    <p class="muted mt12">Цель на сегодня: ${duration(st.sleepGoalMinutes)} · осталось ${duration(left)}</p>
    ${sleeping
      ? `<button class="btn big tertiary" data-action="wake">${icon.sun}Проснулся</button>`
      : `<button class="btn big primary" data-action="sleep">${icon.moon}Уснул</button>`}
  </section>

  <section class="card">
    <div class="card-title"><span class="ic secondary">${icon.baby}</span>Кормление</div>
    ${bigStat(st.feedsToday === 0 ? '—' : `через ${duration(untilFeed)}`, feedLabel)}
    <p class="muted mt10">Сегодня: ${st.feedsToday} раз · интервал ${duration(s.feedIntervalMinutes)}${
      st.feedAmountTodayMl > 0 ? ` · ${st.feedAmountTodayMl} мл` : ''}</p>
    <div class="feed-row">
      <button class="feed-btn" data-action="feed" data-type="BREAST">${icon.baby}<span>Грудь</span></button>
      <button class="feed-btn" data-action="feed" data-type="BOTTLE">${icon.bottle}<span>Бутылочка</span></button>
      <button class="feed-btn" data-action="feed" data-type="SOLIDS">${icon.spoon}<span>Прикорм</span></button>
    </div>
  </section>

  <div class="row gap12">
    <button class="btn outline wide" data-action="diaper">${icon.diaper}Подгузник</button>
    <a class="btn primary wide" href="#sounds">${icon.sound}Звуки</a>
  </div>

  <h2>История</h2>
  ${st.events.length === 0
    ? '<p class="muted">Пока пусто. Отмечай сон и кормления — здесь появится лента.</p>'
    : `<ul class="events">${st.events.map((e) => eventRow(e, now)).join('')}</ul>`}
  `
}

const bigStat = (value: string, label: string) =>
  `<div class="big-stat"><div class="value">${value}</div><div class="muted">${label}</div></div>`

const FEED_NAMES: Record<store.FeedType, string> = { BREAST: 'Грудь', BOTTLE: 'Бутылочка', SOLIDS: 'Прикорм' }

function eventRow(e: store.FeedEvent, now: number): string {
  let ic: string, tint: string, title: string, sub: string
  if (e.kind === 'sleep') {
    const { start, end } = e.entity
    ic = icon.moon
    tint = 'primary'
    title = `Сон ${clock(start)}`
    sub = end !== null ? `${duration(Math.floor((end - start) / 60_000))} сна` : 'спит сейчас'
  } else if (e.kind === 'feed') {
    const f = e.entity
    ic = f.type === 'BOTTLE' ? icon.bottle : f.type === 'SOLIDS' ? icon.spoon : icon.baby
    tint = 'secondary'
    title = `${FEED_NAMES[f.type]} ${clock(f.timestamp)}`
    sub = relative(f.timestamp, now) + (f.amountMl ? ` · ${f.amountMl} мл` : '')
  } else {
    ic = icon.diaper
    tint = 'tertiary'
    title = `Подгузник ${clock(e.timestamp)}`
    sub = e.entity.withStool ? 'со стулом' : relative(e.timestamp, now)
  }
  return `<li class="event"><span class="dot ${tint}">${ic}</span><div><div class="title">${title}</div><div class="muted">${sub}</div></div></li>`
}

// ---------- Звуки ----------

const SOUND_ICONS: Record<string, string> = { air: icon.air, drop: icon.drop, wave: icon.wave, note: icon.note }

function soundsView(now: number): string {
  const playing = player.currentSound()
  const stopAt = player.stopAtTime()
  return `
  ${screenHeader('Звуки')}
  <h3>Выключится через</h3>
  <div class="chips">
    ${[0, 15, 30, 60].map((t) =>
      `<button class="chip ${soundTimer === t ? 'on' : ''}" data-action="timer" data-min="${t}">${t === 0 ? 'бесконечно' : `${t} мин`}</button>`).join('')}
  </div>
  ${playing && stopAt > 0 ? `<p class="muted">Выключится в ${clock(stopAt)} · ${relative(stopAt, now)}</p>` : ''}
  ${SOUNDS.map((snd) => {
    const on = playing === snd.type
    return `
    <button class="card sound ${on ? 'card-primary' : ''}" data-action="${on ? 'stop' : 'play'}" data-sound="${snd.type}">
      <span class="sound-ic">${SOUND_ICONS[snd.icon]}</span>
      <span class="grow"><span class="title">${snd.title}</span><span class="muted block">${snd.subtitle}</span></span>
      ${on ? `<span class="btn primary small">${icon.stop}Стоп</span>` : ''}
    </button>`
  }).join('')}
  <p class="muted small-note">Чтобы звук не прерывался, не закрывай приложение свайпом. Экран можно выключить.</p>
  `
}

const screenHeader = (title: string) =>
  `<header class="top"><h1>${title}</h1><a class="link" href="#">${icon.back}назад</a></header>`

// ---------- Неделя ----------

function statsView(): string {
  const st = store.weekStats()
  const maxSleep = Math.max(1, ...st.days.map((d) => d.sleepMinutes))
  return `
  ${screenHeader('Неделя')}
  <div class="row gap12">
    ${miniStat('Сон в среднем', duration(st.avgSleepMinutes))}
    ${miniStat('Кормлений в день', st.avgFeeds.toFixed(1).replace('.', ','))}
  </div>
  <h2>Сон по дням</h2>
  ${st.days.map((d) => `
    <div class="card day">
      <div class="day-name"><span class="muted">${weekday(d.dayStart)}</span><b>${dayOfMonth(d.dayStart)}</b></div>
      <div class="bar"><div style="width:${(d.sleepMinutes / maxSleep) * 100}%"></div></div>
      <div class="day-val"><div class="title">${duration(d.sleepMinutes)}</div><div class="muted tiny">${d.feedCount} еда · ${d.sleepSegments} сон</div></div>
    </div>`).join('')}
  `
}

const miniStat = (label: string, value: string) =>
  `<div class="card card-primary mini grow"><div>${label}</div><div class="value">${value}</div></div>`

// ---------- Настройки ----------

function toDateInput(ts: number): string {
  if (ts <= 0) return ''
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function settingsView(): string {
  const s = store.settings()
  const days = ageDaysAt(s)
  const norm = normForAgeMonths(ageMonthsAt(s))
  return `
  ${screenHeader('Настройки')}
  ${section('Ребёнок', `
    <label class="field"><span>Имя</span><input id="name" value="${esc(s.name)}" maxlength="40" autocomplete="off"></label>
    <label class="field"><span>Дата рождения${s.birthDate > 0 ? ` · ${ageText(days)}` : ''}</span>
      <input id="birth" type="date" value="${toDateInput(s.birthDate)}" max="${toDateInput(Date.now())}"></label>
  `)}
  ${section('Сон', `
    <p class="muted">Норма для ${ageText(days)}: ${duration(norm.minMinutes)} – ${duration(norm.maxMinutes)}</p>
    ${stepper('Цель сна в сутки', s.sleepGoalMinutes > 0 ? duration(s.sleepGoalMinutes) : 'по возрасту', 'goal')}
    ${stepper('Цикл сна (прогноз пробуждения)', duration(s.sleepCycleMinutes), 'cycle')}
    <button class="btn outline" data-action="reset-sleep">Сбросить к норме по возрасту</button>
  `)}
  ${section('Кормление', `
    ${stepper('Интервал между кормлениями', duration(s.feedIntervalMinutes), 'interval')}
    ${stepper('Объём бутылочки по умолчанию', `${s.defaultBottleMl} мл`, 'bottle')}
  `)}
  ${section('Звуки', stepper('Таймер выключения', s.soundTimerMinutes > 0 ? `${s.soundTimerMinutes} мин` : 'не выключать', 'sound'))}
  ${section('Уведомления', `
    <p class="muted">В веб-версии напоминания о кормлении и пробуждении не приходят — iPhone не даёт сайтам
    ставить будильники. Они будут в iOS-приложении.</p>
  `)}
  <p class="muted footnote">Нормы сна — общие ориентиры, а не медицинские рекомендации. Ориентируйтесь на своего педиатра.</p>
  `
}

const section = (title: string, body: string) => `<section class="card"><h3>${title}</h3>${body}</section>`

const stepper = (title: string, value: string, key: string) => `
  <div class="stepper">
    <div class="grow"><div>${title}</div><div class="accent">${value}</div></div>
    <button class="btn outline sq" data-action="step" data-key="${key}" data-dir="-1" aria-label="Меньше">−</button>
    <button class="btn outline sq" data-action="step" data-key="${key}" data-dir="1" aria-label="Больше">+</button>
  </div>`

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))

function step(key: string, dir: number) {
  const s = store.settings()
  switch (key) {
    case 'goal': {
      const mid = midMinutes(normForAgeMonths(ageMonthsAt(s)))
      const v = s.sleepGoalMinutes <= 0 ? (dir < 0 ? mid : mid + 15) : Math.max(60, s.sleepGoalMinutes + dir * 15)
      return store.updateSettings({ sleepGoalMinutes: v })
    }
    case 'cycle':
      return store.updateSettings({ sleepCycleMinutes: clamp(s.sleepCycleMinutes + dir * 15, 15, 180) })
    case 'interval':
      return store.updateSettings({ feedIntervalMinutes: clamp(s.feedIntervalMinutes + dir * 15, 15, 360) })
    case 'bottle':
      return store.updateSettings({ defaultBottleMl: clamp(s.defaultBottleMl + dir * 10, 10, 300) })
    case 'sound': {
      const v = clamp(s.soundTimerMinutes + dir * 15, 0, 60)
      soundTimer = v
      return store.updateSettings({ soundTimerMinutes: v })
    }
  }
}

// ---------- Отрисовка и события ----------

let lastScreen: Screen | null = null
let lastHtml = ''

function render() {
  const sc = screen()
  const now = Date.now()
  const html =
    sc === 'sounds' ? soundsView(now) : sc === 'stats' ? statsView() : sc === 'settings' ? settingsView() : homeView(now)
  // Подменяем DOM только когда что-то поменялось: иначе ежесекундный тик
  // пересоздавал бы кнопку прямо под пальцем, и нажатие терялось.
  if (html !== lastHtml) {
    app.innerHTML = html
    lastHtml = html
  }
  if (sc !== lastScreen) {
    window.scrollTo(0, 0)
    lastScreen = sc
  }
}

/** Короткий отклик на нажатие, где это поддерживается. */
const buzz = () => navigator.vibrate?.(15)

app.addEventListener('click', (ev) => {
  const el = (ev.target as HTMLElement).closest<HTMLElement>('[data-action]')
  if (!el) return
  const d = el.dataset
  switch (d.action) {
    case 'sleep': buzz(); return store.startSleep()
    case 'wake': buzz(); return store.wakeUp()
    case 'feed': {
      buzz()
      const type = d.type as store.FeedType
      return store.logFeeding(type, type === 'BOTTLE' ? store.settings().defaultBottleMl : null)
    }
    case 'diaper': buzz(); return store.logDiaper(false)
    case 'play': return player.play(d.sound as SoundType, soundTimer)
    case 'stop': return player.stop()
    case 'timer':
      soundTimer = Number(d.min)
      return player.setTimer(soundTimer)
    case 'step': return step(d.key!, Number(d.dir))
    case 'reset-sleep': return store.updateSettings({ sleepGoalMinutes: 0, sleepCycleMinutes: 75 })
  }
})

// Имя сохраняем на каждый символ, но без перерисовки: иначе сбивался бы курсор,
// а перерисовка на blur съедала бы нажатие кнопки, ради которого поле потеряло фокус.
app.addEventListener('input', (ev) => {
  const el = ev.target as HTMLInputElement
  if (el.id === 'name') store.updateSettings({ name: el.value.trim() || 'Малыш' }, true)
})

app.addEventListener('change', (ev) => {
  const el = ev.target as HTMLInputElement
  if (el.id === 'birth') {
    const [y, m, d] = el.value.split('-').map(Number)
    store.updateSettings({ birthDate: el.value ? new Date(y, m - 1, d).getTime() : 0 })
  }
})

store.subscribe(render)
player.onPlayerChange(render)
window.addEventListener('hashchange', render)
document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && render())

// Живые таймеры на главном и в звуках. Настройки не трогаем — там поля ввода.
setInterval(() => {
  if (document.visibilityState === 'visible' && (screen() === 'home' || screen() === 'sounds')) render()
}, 1000)

render()

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  navigator.serviceWorker.register('./sw.js').catch(() => {})
}
