// Проигрыватель звуков с таймером автовыключения. Аналог sound/SoundService.kt.

import { SOUNDS, SoundType, renderLoop, toWav } from './synth'

const audio = new Audio()
audio.loop = true
audio.preload = 'auto'

const urls = new Map<SoundType, string>()
let current: SoundType | null = null
let stopAt = 0
const listeners = new Set<() => void>()

const notify = () => listeners.forEach((l) => l())

export function onPlayerChange(l: () => void) {
  listeners.add(l)
}

export const currentSound = () => current
export const stopAtTime = () => stopAt

function urlFor(type: SoundType): string {
  let url = urls.get(type)
  if (!url) {
    url = URL.createObjectURL(new Blob([toWav(renderLoop(type))], { type: 'audio/wav' }))
    urls.set(type, url)
  }
  return url
}

/** minutes = 0 — играть бесконечно. Вызывать из обработчика нажатия (требование iOS). */
export function play(type: SoundType, minutes: number) {
  stopAt = minutes > 0 ? Date.now() + minutes * 60_000 : 0
  if (current !== type) {
    current = type
    audio.src = urlFor(type)
  }
  audio.play().catch(() => stop())
  const meta = SOUNDS.find((s) => s.type === type)!
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({ title: meta.title, artist: 'Малыш' })
    navigator.mediaSession.playbackState = 'playing'
  }
  notify()
}

/** Сменить таймер у уже играющего звука. */
export function setTimer(minutes: number) {
  if (current) stopAt = minutes > 0 ? Date.now() + minutes * 60_000 : 0
  notify()
}

export function stop() {
  audio.pause()
  current = null
  stopAt = 0
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'none'
  notify()
}

function checkTimer() {
  if (current && stopAt > 0 && Date.now() >= stopAt) stop()
}

// setInterval в фоне на iPhone засыпает, а timeupdate приходит, пока играет звук.
audio.addEventListener('timeupdate', checkTimer)
setInterval(checkTimer, 1000)

// Пауза с экрана блокировки / наушников.
audio.addEventListener('pause', () => {
  if (current && !audio.ended) {
    current = null
    stopAt = 0
    notify()
  }
})

if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('pause', stop)
  navigator.mediaSession.setActionHandler('stop', stop)
}
