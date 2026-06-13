import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import process from 'node:process'
import { createToolProxyConfig } from './toolProxy.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // 第三方依赖单独分包：业务迭代不影响 vendor 块的浏览器缓存
        manualChunks(id) {
          if (id.includes('node_modules')) {
            return 'vendor'
          }
        }
      }
    }
  },
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/agent': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/tool': createToolProxyConfig(
        process.env.VITE_REACTOR_TOOL_BASE_URL || process.env.REACTOR_TOOL_BASE_URL
      )
    }
  }
})
