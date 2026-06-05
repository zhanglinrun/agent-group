import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import process from 'node:process'
import { createToolProxyConfig } from './toolProxy.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/tool': createToolProxyConfig(
        process.env.VITE_REACTOR_TOOL_BASE_URL || process.env.REACTOR_TOOL_BASE_URL
      )
    }
  }
})
