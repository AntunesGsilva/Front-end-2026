import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Em desenvolvimento, as chamadas /api vão para a API Java (java -cp "bin:lib/*" Main --api).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: { '/api': { target: process.env.VAGACERTA_API ?? 'http://localhost:8080', changeOrigin: true } },
  },
});
