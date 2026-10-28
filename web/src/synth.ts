// Синтез звуков сэмплами. Порт sound/Synth.kt: никаких аудиофайлов, всё считается на лету.
// Результат — бесшовная петля в WAV (моно, 16 bit), которую крутит обычный <audio loop>:
// так звук на iPhone живёт дольше, чем через Web Audio, и управляется с экрана блокировки.

export type SoundType = 'WHITE_NOISE' | 'RAIN' | 'DROPS' | 'LULLABY'

export const SOUNDS: { type: SoundType; title: string; subtitle: string; icon: string }[] = [
  { type: 'WHITE_NOISE', title: 'Белый шум', subtitle: 'Мягкое шипение, приглушает малыша', icon: 'air' },
  { type: 'RAIN', title: 'Дождь', subtitle: 'Ровный стук капель по крыше', icon: 'drop' },
  { type: 'DROPS', title: 'Капли', subtitle: 'Редкие бульканье в тишине', icon: 'wave' },
  { type: 'LULLABY', title: 'Колыбельная', subtitle: 'Брамс, нежный голос', icon: 'note' },
]

export const SAMPLE_RATE = 44100
const BEAT_SAMPLES = Math.floor((SAMPLE_RATE * 60) / 74) // ~74 BPM
const TWO_PI = 2 * Math.PI

/** mulberry32 — маленький детерминированный ГПСЧ. */
function rng(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const FREQS: Record<string, number> = {
  C4: 261.63, D4: 293.66, E4: 329.63, F4: 349.23, G4: 392.0, A4: 440.0, B4: 493.88,
}

const MELODY: [string, number][] = [
  ['E4', 1], ['E4', 1], ['G4', 2],
  ['E4', 1], ['E4', 1], ['G4', 2],
  ['E4', 1], ['C4', 1], ['D4', 1], ['E4', 2],
  ['F4', 2], ['F4', 1], ['E4', 1], ['D4', 1], ['C4', 2],
  ['G4', 1], ['F4', 1], ['E4', 2],
  ['C4', 1], ['E4', 1], ['G4', 2],
  ['E4', 1], ['C4', 1], ['D4', 1], ['E4', 2],
  ['D4', 2], ['C4', 2],
]

export const MELODY_SAMPLES = MELODY.reduce((s, [, beats]) => s + beats * BEAT_SAMPLES, 0)

/** Генератор потока сэмплов в диапазоне примерно -1..1. */
export function createGenerator(type: SoundType, seed = 1234): () => number {
  const rnd = rng(seed)
  const randInt = (from: number, until: number) => from + Math.floor(rnd() * (until - from))
  const scheduleDrop = () => randInt(SAMPLE_RATE / 3, (SAMPLE_RATE * 3) / 2)

  let lp1 = 0
  let lp2 = 0
  let dropPhase = 0
  let dropEnv = 0
  let dropFreq = 0
  let nextDropIn = type === 'DROPS' ? scheduleDrop() : 0

  switch (type) {
    // Белый шум с лёгким низкочастотным фильтром — мягче, чем чистый белый.
    case 'WHITE_NOISE':
      return () => {
        lp1 = lp1 * 0.85 + (rnd() * 2 - 1) * 0.15
        return lp1 * 1.6
      }

    // Дождь: розовый шум + редкие капли.
    case 'RAIN':
      return () => {
        lp1 = lp1 * 0.92 + (rnd() * 2 - 1) * 0.08
        lp2 = lp2 * 0.75 + lp1 * 0.25
        if (nextDropIn > 0) {
          nextDropIn--
        } else {
          nextDropIn = scheduleDrop()
          dropEnv = 1
          dropFreq = 700 + rnd() * 1400
          dropPhase = 0
        }
        let drop = 0
        if (dropEnv > 0.001) {
          dropPhase += (TWO_PI * dropFreq) / SAMPLE_RATE
          drop = Math.sin(dropPhase) * dropEnv * 0.25
          dropEnv *= 0.9995
        }
        return lp2 * 2.2 + drop
      }

    // Редкие капли в тишине.
    case 'DROPS':
      return () => {
        if (dropEnv > 0.001) {
          dropPhase += (TWO_PI * dropFreq) / SAMPLE_RATE
          const out = Math.sin(dropPhase) * dropEnv * 0.55
          dropEnv *= 0.99985
          return out
        }
        if (nextDropIn > 0) {
          nextDropIn--
          return 0
        }
        nextDropIn = scheduleDrop()
        dropEnv = 1
        dropFreq = 500 + rnd() * 900
        dropPhase = 0
        return 0
      }

    // Колыбельная: мелодия синусом с мягкой огибающей.
    case 'LULLABY': {
      let index = 0
      let noteTotal = 1
      let noteLeft = 0
      let freq = 0
      let phase = 0
      return () => {
        if (noteLeft <= 0) {
          const [name, beats] = MELODY[index]
          index = (index + 1) % MELODY.length
          freq = FREQS[name]
          noteTotal = beats * BEAT_SAMPLES
          noteLeft = noteTotal
        }
        noteLeft--
        const progress = (noteTotal - noteLeft) / noteTotal
        const env = progress < 0.15 ? progress / 0.15 : Math.exp(-(progress - 0.15) * 3.2)
        phase += (TWO_PI * freq) / SAMPLE_RATE
        if (phase > TWO_PI) phase -= TWO_PI
        return (Math.sin(phase) + Math.sin(phase * 2) * 0.18) * env * 0.5
      }
    }
  }
}

/**
 * Петля длиной loopSamples. Для шумов конец плавно перетекает в начало
 * (кроссфейд), чтобы на стыке не было щелчка. Колыбельная — ровно одна мелодия.
 */
export function renderLoop(type: SoundType): Float32Array {
  const gen = createGenerator(type)
  if (type === 'LULLABY') {
    const out = new Float32Array(MELODY_SAMPLES)
    for (let i = 0; i < out.length; i++) out[i] = gen()
    return out
  }
  const length = SAMPLE_RATE * 40
  const fade = SAMPLE_RATE * 2
  const raw = new Float32Array(length + fade)
  for (let i = 0; i < raw.length; i++) raw[i] = gen()
  const out = raw.slice(0, length)
  for (let i = 0; i < fade; i++) {
    const t = i / fade
    // равномощный кроссфейд — громкость шума на стыке не проседает
    out[i] = raw[length + i] * Math.cos((t * Math.PI) / 2) + raw[i] * Math.sin((t * Math.PI) / 2)
  }
  return out
}

/** Упаковка сэмплов в WAV (PCM 16 bit, моно). */
export function toWav(samples: Float32Array, sampleRate = SAMPLE_RATE): ArrayBuffer {
  const buf = new ArrayBuffer(44 + samples.length * 2)
  const v = new DataView(buf)
  const str = (o: number, s: string) => [...s].forEach((c, i) => v.setUint8(o + i, c.charCodeAt(0)))
  str(0, 'RIFF')
  v.setUint32(4, 36 + samples.length * 2, true)
  str(8, 'WAVE')
  str(12, 'fmt ')
  v.setUint32(16, 16, true)
  v.setUint16(20, 1, true) // PCM
  v.setUint16(22, 1, true) // моно
  v.setUint32(24, sampleRate, true)
  v.setUint32(28, sampleRate * 2, true)
  v.setUint16(32, 2, true)
  v.setUint16(34, 16, true)
  str(36, 'data')
  v.setUint32(40, samples.length * 2, true)
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    v.setInt16(44 + i * 2, Math.round(s * 32767), true)
  }
  return buf
}
