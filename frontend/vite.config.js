import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({ plugins: [react()], server: { host: '0.0.0.0', proxy: { '/api': 'http://localhost:8080', '/v3/api-docs': 'http://localhost:8080', '/swagger-ui': 'http://localhost:8080' } }, test: { environment: 'jsdom', setupFiles: './src/testSetup.js' } });
