import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'IMAGECLOUD_')
  const proxy = {
    // 保留 /api 前缀，与 Spring Boot 控制器路径一致。
    '/api': env.IMAGECLOUD_API_TARGET || 'http://127.0.0.1:8080',
  }

  return {
    plugins: [vue()],
    server: { proxy },
    preview: { proxy },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})
