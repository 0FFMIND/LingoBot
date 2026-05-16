import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    historyApiFallback: true,
    proxy: {
      // API 请求代理配置
      '/api': {
        // 将 /api 开头的请求代理到后端服务
        target: 'http://localhost:8080',
        // 修改请求头中的 Origin，解决跨域问题
        changeOrigin: true,
        // 自定义代理配置
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, _req, res) => {
            // 专门处理 SSE (Server-Sent Events) 流式响应
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              // 禁用缓存，确保 SSE 数据能够实时推送
              res.setHeader('cache-control', 'no-cache');
              // 禁用 Nginx 缓冲，确保 SSE 数据能够实时传输
              res.setHeader('x-accel-buffering', 'no');
            }
          });
        },
      }
    }
  }
})
