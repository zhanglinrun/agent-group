import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import process from 'node:process'
import tailwindcss from '@tailwindcss/vite'
import { createToolProxyConfig } from './toolProxy.js'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_API_TARGET || env.SERVICE_BASE_URL || 'http://localhost:8080'
  const reactorToolBaseUrl = env.VITE_REACTOR_TOOL_BASE_URL || env.REACTOR_TOOL_BASE_URL || ''
  // 开发态走 Vite 同源代理，避免 5173 -> 8080 跨域 + Cookie 丢失
  const clientServiceBaseUrl = mode === 'development' ? '' : backendTarget

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src/reactor-ui')
      }
    },
    css: {
      preprocessorOptions: {
        less: { javascriptEnabled: true }
      }
    },
    optimizeDeps: {
      exclude: ['clsx', 'nanoid', 'radix-ui', 'lucide-react', 'tailwind-merge']
    },
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
          target: backendTarget,
          changeOrigin: true
        },
        '/agent': {
          target: backendTarget,
          changeOrigin: true
        },
        '/web': {
          target: backendTarget,
          changeOrigin: true
        },
        '/data': {
          target: backendTarget,
          changeOrigin: true
        },
        '/tool': createToolProxyConfig(reactorToolBaseUrl)
      }
    },
    define: {
      SERVICE_BASE_URL: JSON.stringify(clientServiceBaseUrl),
      REACTOR_TOOL_BASE_URL: JSON.stringify(reactorToolBaseUrl)
    }
  }
})
