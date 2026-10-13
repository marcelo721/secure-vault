import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Em desenvolvimento, o Vite encaminha /api para o backend. Assim o navegador enxerga uma única origem
// (http://localhost:5173), o que é compatível com cookies SameSite=Strict e dispensa CORS no fluxo normal.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
