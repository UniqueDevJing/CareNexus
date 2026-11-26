import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 普通工作台部署在 /CareNexus/；赛事作品使用根路径静态发布。
export default defineConfig(({ mode }) => ({
  base: mode === 'contest' ? '/' : '/CareNexus/',
  define: {
    'import.meta.env.VITE_CONTEST_BUILD': JSON.stringify(mode === 'contest' ? 'true' : 'false')
  },
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
          'vendor-ui': ['element-plus'],
          'vendor-motion': ['gsap']
        }
      }
    }
  }
}))
