/// <reference types="vitest/config" />
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The dev server proxies /api to the Spring Boot backend, so the browser sees ONE origin
// (localhost:5173). That keeps the SameSite=Strict refresh cookie working and avoids CORS in development.
// 127.0.0.1, not localhost: Node resolves localhost to IPv6 (::1) first, but Docker publishes the API
// on IPv4 127.0.0.1 only, which made every proxied call fail with ECONNREFUSED (502).
const BACKEND = process.env.STOCKFLOW_API ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Browser end-to-end tests live in e2e/ and run with Playwright, not Vitest.
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
})
