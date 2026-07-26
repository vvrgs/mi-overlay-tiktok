/**
 * Servidor del juego.
 *
 * Hace tres cosas y nada más:
 *   1. Sirve el overlay y el panel ya compilados (`dist/`).
 *   2. Actúa de central telefónica: reparte por WebSocket los eventos que llegan
 *      de tu plataforma hacia el overlay, y el estado del overlay hacia el panel.
 *   3. Guarda los rankings en disco.
 *
 * Arranque:  npm start        (compila y levanta)
 *            npm run server   (solo levanta, si ya compilaste)
 */

import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';
import { Store } from './store.mjs';

const ROOT = resolve(fileURLToPath(new URL('..', import.meta.url)));
const DIST = join(ROOT, 'dist');
const PUBLIC = join(ROOT, 'public');
const DATA = join(ROOT, 'data');

const PORT = Number(process.env.PORT ?? 8787);
const HOST = process.env.HOST ?? '0.0.0.0';
/** Token opcional: si lo defines, /api/event exige la cabecera `x-api-key`. */
const API_KEY = process.env.API_KEY ?? '';
const MAX_BODY_BYTES = 1_000_000;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

const store = new Store(DATA);
await store.load();

// ---------------------------------------------------------------- utilidades

function send(res, status, body, headers = {}) {
  const payload = typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body);
  res.writeHead(status, {
    'access-control-allow-origin': '*',
    'access-control-allow-headers': 'content-type, x-api-key',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'cache-control': 'no-store',
    ...headers,
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolveBody, rejectBody) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        rejectBody(new Error('Cuerpo demasiado grande'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolveBody({});
      try {
        resolveBody(JSON.parse(raw));
      } catch {
        rejectBody(new Error('JSON inválido'));
      }
    });
    req.on('error', rejectBody);
  });
}

async function serveStatic(req, res, pathname) {
  const relative = normalize(decodeURIComponent(pathname)).replace(/^(\.\.[/\\])+/, '');
  const candidates = [
    join(DIST, relative),
    join(PUBLIC, relative),
    // Con `npm run dev` la config vive en public/, no en dist/.
    relative === '/' || relative === '\\' ? join(DIST, 'index.html') : null,
  ].filter(Boolean);

  for (const candidate of candidates) {
    // Nunca se sirve nada fuera de dist/ o public/.
    if (!candidate.startsWith(DIST) && !candidate.startsWith(PUBLIC)) continue;
    try {
      const info = await stat(candidate);
      const file = info.isDirectory() ? join(candidate, 'index.html') : candidate;
      const data = await readFile(file);
      return send(res, 200, data, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream' });
    } catch {
      /* siguiente candidato */
    }
  }

  send(res, 404, { error: 'No encontrado', hint: 'Ejecuta `npm run build` para generar dist/' });
}

// -------------------------------------------------------------------- HTTP

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
  const { pathname } = url;

  if (req.method === 'OPTIONS') return send(res, 204, '');

  if (pathname === '/api/health') {
    return send(res, 200, { ok: true, clients: wss?.clients.size ?? 0, uptime: process.uptime() });
  }

  if (pathname === '/api/leaderboard' && req.method === 'GET') {
    return send(res, 200, store.state);
  }

  if (req.method === 'POST') {
    let body;
    try {
      body = await readBody(req);
    } catch (err) {
      return send(res, 400, { error: err.message });
    }

    switch (pathname) {
      case '/api/event':
      case '/api/events': {
        if (API_KEY && req.headers['x-api-key'] !== API_KEY) {
          return send(res, 401, { error: 'x-api-key inválida' });
        }
        const events = Array.isArray(body) ? body : [body];
        for (const event of events) broadcast(event);
        return send(res, 200, { ok: true, delivered: events.length, overlays: countRole('overlay') });
      }
      case '/api/track':
        store.trackDonations(body.donations ?? []);
        store.trackWarriors(body.warriors ?? []);
        return send(res, 200, { ok: true });
      case '/api/match':
        store.trackMatch(body);
        return send(res, 200, { ok: true });
      default:
        return send(res, 404, { error: 'Ruta no encontrada' });
    }
  }

  if (req.method === 'GET') return serveStatic(req, res, pathname);
  send(res, 405, { error: 'Método no permitido' });
});

// --------------------------------------------------------------- WebSocket

const wss = new WebSocketServer({ server, path: '/events' });

function countRole(role) {
  let count = 0;
  for (const client of wss.clients) if (client.role === role) count++;
  return count;
}

/** Reenvía un mensaje a todos los clientes menos al que lo originó. */
function broadcast(payload, origin = null) {
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload);
  for (const client of wss.clients) {
    if (client === origin || client.readyState !== client.OPEN) continue;
    client.send(text);
  }
}

wss.on('connection', (socket) => {
  socket.role = 'unknown';
  socket.isAlive = true;
  socket.on('pong', () => {
    socket.isAlive = true;
  });

  socket.on('message', (raw) => {
    const text = raw.toString();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      return;
    }

    // Presentación: permite contar overlays y paneles conectados.
    if (data?.type === 'hello') {
      socket.role = data.role ?? 'unknown';
      return;
    }
    broadcast(text, socket);
  });
});

// Cierra conexiones zombis (OBS a veces desaparece sin avisar).
const heartbeat = setInterval(() => {
  for (const client of wss.clients) {
    if (!client.isAlive) {
      client.terminate();
      continue;
    }
    client.isAlive = false;
    client.ping();
  }
}, 30000);

// ------------------------------------------------------------------ arranque

server.listen(PORT, HOST, () => {
  const shown = HOST === '0.0.0.0' ? 'localhost' : HOST;
  console.log(`\n  ⚔  Guerra de Naciones`);
  console.log(`     Overlay  →  http://${shown}:${PORT}/index.html`);
  console.log(`     Panel    →  http://${shown}:${PORT}/control.html`);
  console.log(`     Eventos  →  ws://${shown}:${PORT}/events`);
  console.log(`     API      →  POST http://${shown}:${PORT}/api/event`);
  if (API_KEY) console.log('     Seguridad → API_KEY activa (cabecera x-api-key)');
  console.log('');
});

async function shutdown() {
  clearInterval(heartbeat);
  await store.flush();
  wss.close();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 2000).unref();
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
