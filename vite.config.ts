import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';
import tailwindcss from 'tailwindcss';
import autoprefixer from 'autoprefixer';
import { VitePWA } from 'vite-plugin-pwa';
import { resolve } from 'path';
import fs from 'fs';

export default defineConfig({
  root: '.',
  base: './',
  plugins: [
    preact(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Detetive Noir',
        short_name: 'Detetive',
        description: 'Terminal Noir Detective Study App',
        theme_color: '#0a0a0a',
        background_color: '#0a0a0a',
        display: 'standalone',
        orientation: 'landscape-primary',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{html,js,css,png,svg,woff2}'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024
      }
    })
  ],
  css: {
    postcss: {
      plugins: [tailwindcss(), autoprefixer()]
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    minify: 'terser',
    terserOptions: {
      compress: { drop_console: true, drop_debugger: true },
      format: { comments: false }
    },
    rollup:     rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html')
      },
      output: {
        manualChunks: {
          vendor: ['preact', 'preact/hooks'],
          tabs: [
            './src/tabs/tracker.ts',
            './src/tabs/protocol.ts',
            './src/tabs/persecution.ts',
            './src/tabs/detective.ts',
            './src/tabs/coroner.ts',
            './src/tabs/reading.ts',
            './src/tabs/academia.ts',
            './src/tabs/correcao.ts',
            './src/tabs/schedule.ts',
            './src/tabs/enigma.ts',
            './src/tabs/vocabulary.ts',
            './src/tabs/hiddenArchive.ts',
            './src/tabs/fitas.ts',
            './src/tabs/gaveta.ts',
            './src/tabs/datilografia.ts'
          ]
        }
      }
    }
  },
  server: {
    port: 5173,
    host: true,
    open: false
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@tabs': resolve(__dirname, 'src/tabs'),
      '@plugins': resolve(__dirname, 'src/plugins'),
      '@core': resolve(__dirname, 'src/core'),
      '@components': resolve(__dirname, 'src/components'),
      '@hooks': resolve(__dirname, 'src/hooks'),
      '@utils': resolve(__dirname, 'src/utils')
    }
  }
});