/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // La API se consume por /api y se reenvía al backend local (sin CORS en desarrollo).
    // API_PROXY_TARGET permite apuntar a otra instancia (p. ej., una API de pruebas en otro puerto).
    // changeOrigin: false conserva el Host del navegador y xfwd añade X-Forwarded-*: así la API construye
    // las URL de regreso del login externo con la dirección del frontend, no con la suya.
    proxy: {
      '/api': { target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080', changeOrigin: false, xfwd: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
})
