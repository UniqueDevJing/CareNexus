import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// CareNexus 工作台统一部署在 /CareNexus/，避免同一份前端产生两套入口与路由事实。
export default defineConfig(() => ({
  base: '/CareNexus/',
  plugins: [vue()],
  server: {
    port: 5192,
    proxy: {
      // 只代理真正的后端接口；/CareNexus/ 是前端 base，不能被 /v2 前缀误伤
      '/v2/api': { target: process.env.V2_BACKEND || 'http://127.0.0.1:18092', changeOrigin: true }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router'],
          'vendor-http': ['axios'],
          'vendor-motion': ['gsap']
        }
      }
    }
  }
}))
