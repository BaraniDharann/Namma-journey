import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.VITE_API_BASE_URL?.replace('/api', '') || 'http://localhost:8080'

  return {
    plugins: [react(), tailwindcss()],
    publicDir: 'public',

    server: {
      port: 5173,
      // Fail loudly instead of silently moving to 5174. The E2E suite targets 5173 by
      // hard-coded baseURL, so a silent fallback leaves it testing whatever else happens
      // to hold that port — which reads as dozens of unrelated frontend failures.
      strictPort: true,
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
        },
        // SockJS opens /ws/tracking on the page origin, so the dev server has to carry it
        // through to the backend as well — ws:true covers the upgrade, and the SockJS
        // XHR-polling fallback rides the same rule.
        '/ws': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
          ws: true,
        },
        '/uploads': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
        }
      }
    },

    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
      minify: 'esbuild',
      chunkSizeWarningLimit: 600,
      target: 'es2020',
      cssCodeSplit: true,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['react', 'react-dom', 'react-router-dom'],
            http: ['axios'],
            maps: ['leaflet', 'react-leaflet'],
            pdf: ['jspdf', 'jspdf-autotable'],
            motion: ['framer-motion'],
            three: ['three', '@react-three/fiber', '@react-three/drei'],
          }
        }
      }
    },

    preview: {
      port: 4173,
    }
  }
})
