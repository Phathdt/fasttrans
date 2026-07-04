import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      // Alias @/* points to the src directory (required by shadcn/ui)
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // FE calls the API cross-origin at VITE_API_BASE_URL (default http://localhost:4000);
    // Traefik handles CORS, so no dev proxy is needed.
    port: 3000,
  },
})
