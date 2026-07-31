/**
 * Build de demo: TODO el juego en un único index.html (JS, CSS, fuentes y
 * worker embebidos) con el simulador encendido. Es lo que se publica como
 * página web de prueba para el teléfono; el build normal (vite.config.ts)
 * sigue siendo el de producción para OBS.
 */
import { readFileSync } from 'node:fs';
import { defineConfig } from 'vite';
import { viteSingleFile } from 'vite-plugin-singlefile';

const gameConfig = readFileSync('public/config/game.config.json', 'utf8');

export default defineConfig({
  base: './',
  plugins: [viteSingleFile()],
  define: {
    'import.meta.env.VITE_FORCE_SIM': JSON.stringify('1'),
    'import.meta.env.VITE_TEST_MODE': JSON.stringify('1'),
    // La config real del juego, embebida como cadena JSON.
    'import.meta.env.VITE_EMBEDDED_CONFIG': JSON.stringify(gameConfig),
  },
  build: {
    target: 'es2022',
    outDir: 'dist-demo',
    emptyOutDir: true,
    // Las fuentes woff2 entran como data URI dentro del CSS embebido.
    assetsInlineLimit: 100_000_000,
    rollupOptions: {
      input: { overlay: 'index.html' },
    },
  },
  worker: {
    format: 'es',
  },
});
