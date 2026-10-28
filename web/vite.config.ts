import { defineConfig } from 'vite'

// base './' — чтобы одна и та же сборка работала и на GitHub Pages (в подпапке),
// и внутри iOS-обёртки (Capacitor грузит файлы локально).
export default defineConfig({
  base: './',
  build: { outDir: 'dist', target: 'es2020' },
})
