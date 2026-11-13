import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端部署在 /CareNexus/，后端在 /v2/api
export default defineConfig({
  base: '/CareNexus/',
  plugins: [vue()],
  server: {
    port: 5192,
    proxy: {
      // 只代理真正的后端接口；/CareNexus/ 是前端 base，不能被 /v2 前缀误伤
      '/v2/api': { target: process.env.V2_BACKEND || 'http://127.0.0.1:18093', changeOrigin: true }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router'],
          'vendor-http': ['axios'],
          'vendor-ui': ['element-plus'],
          'vendor-motion': ['gsap']
        }
      }
    }
  }
})
