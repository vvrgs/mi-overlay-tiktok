/**
 * Panel de control del streamer.
 *
 * Habla con el overlay por dos vías a la vez: BroadcastChannel (funciona entre
 * pestañas del mismo navegador, sin necesidad de servidor) y WebSocket (para
 * cuando el overlay corre dentro de OBS, que es un proceso aparte). Manda el
 * comando por las dos y el overlay lo recibe por la que esté disponible.
 *
 * Los mandos se declaran en el HTML, no aquí: un deslizador solo necesita
 * `data-cmd` (la acción), `data-key` (el campo) y `data-format` (cómo se
 * escribe su valor). Añadir un mando nuevo es añadir un `<input>`; este archivo
 * no se toca. Es lo que evita que el panel se convierta en cien manejadores
 * copiados.
 */

import './styles.css';
import { CHANNEL_NAME } from '../events/transports';
import type { ControlCommand, OverlayStatus } from '../events/types';
import { isOverlayStatus } from '../events/types';
import { loadConfig, resolveEndpoints, type GameConfig, type TeamId } from '../shared/config';
import { formatCount, formatTime } from '../shared/math';
import type { CountryEntry, DonorEntry, MatchRecord, WarriorEntry } from '../game/ranking';

const el: Record<string, HTMLElement> = {};
let config: GameConfig;
let channel: BroadcastChannel | null = null;
let socket: WebSocket | null = null;
let apiBase = '';
let lastStatusAt = 0;
const banned = new Set<string>();

function q<T extends HTMLElement = HTMLElement>(key: string): T {
  return el[key] as T;
}

function collect(): void {
  for (const node of document.querySelectorAll<HTMLElement>('[data-el]')) {
    el[node.dataset.el!] = node;
  }
}

// ------------------------------------------------------------------ envío

function send(command: ControlCommand): void {
  channel?.postMessage(command);
  if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(command));
}

/** Manda un evento de prueba tal cual lo haría la plataforma. */
function emitTestEvent(payload: Record<string, unknown>): void {
  channel?.postMessage(payload);
  if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(payload));
}

// ------------------------------------------------------------- conexiones

function connect(): void {
  if (typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel(CHANNEL_NAME);
    channel.onmessage = (ev) => {
      if (isOverlayStatus(ev.data)) applyStatus(ev.data);
    };
  }

  const { wsUrl } = resolveEndpoints(config);
  const open = () => {
    try {
      socket = new WebSocket(wsUrl);
      socket.addEventListener('open', () => socket?.send(JSON.stringify({ type: 'hello', role: 'control' })));
      socket.addEventListener('message', (ev) => {
        try {
          const data = JSON.parse(String(ev.data));
          if (isOverlayStatus(data)) applyStatus(data);
        } catch {
          /* mensaje no-JSON */
        }
      });
      socket.addEventListener('close', () => setTimeout(open, 2500));
      socket.addEventListener('error', () => socket?.close());
    } catch {
      setTimeout(open, 2500);
    }
  };
  open();
}

// -------------------------------------------------------------- formatos

const FORMATS: Record<string, (value: number) => string> = {
  x: (v) => `${v.toFixed(2)}×`,
  deg: (v) => `${Math.round(v)}°`,
  m: (v) => (v <= 0 ? 'nunca' : `${Math.round(v)} m`),
  // La densidad de niebla es un número minúsculo (0,0022): en el deslizador se
  // maneja en milésimas para que los pasos sean manejables con el dedo.
  milli: (v) => `${v.toFixed(1)}‰`,
  plain: (v) => String(Math.round(v)),
};

/** Valor que se manda al overlay a partir del que muestra el deslizador. */
function toWire(format: string, value: number): number {
  return format === 'milli' ? value / 1000 : value;
}

/** Valor del deslizador a partir del que reporta el overlay. */
function fromWire(format: string, value: number): number {
  return format === 'milli' ? value * 1000 : value;
}

function paintSlider(input: HTMLInputElement): void {
  const key = input.dataset.el!;
  const out = document.querySelector<HTMLOutputElement>(`[data-out="${key}"]`);
  if (!out) return;
  const format = input.dataset.format ?? 'plain';
  out.textContent = (FORMATS[format] ?? FORMATS.plain)(Number(input.value));
}

/** Mueve un deslizador desde fuera sin pisar al streamer si lo está arrastrando. */
function syncSlider(key: string, value: number): void {
  const input = el[key] as HTMLInputElement | undefined;
  if (!input || document.activeElement === input) return;
  const next = fromWire(input.dataset.format ?? 'plain', value);
  if (Math.abs(Number(input.value) - next) < 1e-6) return;
  input.value = String(next);
  paintSlider(input);
}

function syncPills(group: string, value: string): void {
  const host = document.querySelector<HTMLElement>(`[data-pills="${group}"]`);
  if (!host) return;
  for (const pill of host.querySelectorAll<HTMLElement>('.pill')) {
    pill.classList.toggle('is-active', pill.dataset.value === value);
  }
}

// ---------------------------------------------------------------- estado

const PHASES: Record<string, string> = {
  lobby: 'En espera',
  countdown: 'Cuenta atrás',
  battle: 'Batalla',
  roundEnd: 'Fin de ronda',
  seriesEnd: 'Fin de serie',
};
const SEASONS: Record<string, string> = { spring: '🌱 Primavera', summer: '☀️ Verano', autumn: '🍂 Otoño', winter: '❄️ Invierno' };
const WEATHERS: Record<string, string> = { clear: '☀️ Despejado', rain: '🌧 Lluvia', snow: '❄️ Nieve', fog: '🌫 Niebla', storm: '⛈ Tormenta' };

function applyStatus(status: OverlayStatus): void {
  lastStatusAt = performance.now();

  q('phase').textContent = (PHASES[status.phase] ?? status.phase) + (status.paused ? ' · pausa' : '');
  q('score').textContent = `R${status.round} · ${status.wins.red}–${status.wins.blue} (a ${status.seriesTarget})`;
  q('elapsed').textContent = formatTime(status.roundElapsed);
  q('fps').textContent = `${status.fps} fps · ${status.entities.red + status.entities.blue} figuras`;

  q('redSoldiers').textContent = formatCount(status.soldiers.red);
  q('blueSoldiers').textContent = formatCount(status.soldiers.blue);
  const total = Math.max(1, status.soldiers.red + status.soldiers.blue);
  q('redBar').style.width = `${(status.soldiers.red / total) * 100}%`;

  const rageMax = config.likes.rageMax || 100;
  for (const team of ['red', 'blue'] as const) {
    const pct = Math.round((status.rage[team] / rageMax) * 100);
    q(`${team}Rage`).textContent = `${pct}%`;
    q(`${team}RageBar`).style.width = `${Math.min(100, pct)}%`;
  }

  for (const team of ['red', 'blue'] as const) {
    const select = q<HTMLSelectElement>(`${team}Country`);
    if (document.activeElement !== select) select.value = status.countries[team];
  }

  const simToggle = q<HTMLInputElement>('simToggle');
  if (document.activeElement !== simToggle) simToggle.checked = status.simulator;

  q('atmosphere').textContent = `${SEASONS[status.season] ?? status.season ?? '—'} · ${WEATHERS[status.weather] ?? status.weather ?? '—'}`;
  syncPills('season', status.season);
  syncPills('weather', status.weather);

  // Ajustes vivos: el overlay es la fuente de verdad, así que el panel se pone
  // al día solo aunque se haya abierto a mitad del directo o en otro dispositivo.
  if (status.camera) {
    syncPills('cameraMode', status.camera.mode);
    syncSlider('camDistance', status.camera.distance);
    syncSlider('camHeight', status.camera.height);
    syncSlider('camFov', status.camera.fov);
    syncSlider('camCut', status.camera.cutSpeed);
    syncSlider('camOrbit', status.camera.orbit);
    syncSlider('camShake', status.camera.shake);
    const follow = q<HTMLInputElement>('camFollow');
    if (document.activeElement !== follow) follow.checked = status.camera.followAction;
  }
  if (status.graphics) {
    syncPills('quality', status.graphics.quality);
    syncSlider('gExposure', status.graphics.exposure);
    syncSlider('gContrast', status.graphics.contrast);
    syncSlider('gSaturation', status.graphics.saturation);
    syncSlider('gBloom', status.graphics.bloomIntensity);
    syncSlider('gVignette', status.graphics.vignette);
    syncSlider('gLod', status.graphics.lodDistance);
    syncSlider('gTexture', status.graphics.textureDistance);
    syncSlider('gFog', status.graphics.fogDensity);
  }
  if (typeof status.difficulty === 'number') syncSlider('difficulty', status.difficulty);
}

/** El overlay late cada 500 ms: si deja de hacerlo, se marca como desconectado. */
function watchConnection(): void {
  setInterval(() => {
    const alive = performance.now() - lastStatusAt < 2500;
    q('connDot').classList.toggle('is-online', alive);
    q('connText').textContent = alive ? 'en vivo' : 'sin overlay';
  }, 800);
}

// --------------------------------------------------------------- rankings

async function refreshBoards(): Promise<void> {
  let data: { donors?: DonorEntry[]; warriors?: WarriorEntry[]; countries?: CountryEntry[]; matches?: MatchRecord[] } = {};
  try {
    const res = await fetch(`${apiBase}/leaderboard`, { cache: 'no-store' });
    if (res.ok) data = await res.json();
  } catch {
    // Sin servidor: se lee el respaldo local que guarda el overlay.
    try {
      data = JSON.parse(localStorage.getItem('guerra-de-naciones:leaderboards') ?? '{}');
    } catch {
      data = {};
    }
  }

  fillBoard('boardDonors', (data.donors ?? []).sort((a, b) => b.coins - a.coins).slice(0, 10).map((d) => [d.nickname, `${formatCount(d.coins)} 🪙`]));
  fillBoard('boardWarriors', (data.warriors ?? []).sort((a, b) => b.bestSurvival - a.bestSurvival).slice(0, 10).map((w) => [w.nickname, `${Math.round(w.bestSurvival)}s`]));
  fillBoard('boardCountries', (data.countries ?? []).sort((a, b) => b.wins - a.wins).slice(0, 10).map((c) => [`${config.countries[c.code]?.flag ?? ''} ${config.countries[c.code]?.name ?? c.code}`, `${c.wins}V / ${c.losses}D`]));
  fillBoard('boardMatches', (data.matches ?? []).slice(0, 10).map((m) => {
    const winner = m.winner === 'draw' ? 'Empate' : m.winner === 'red' ? config.teams.red.shortName : config.teams.blue.shortName;
    return [`R${m.round} · ${winner}`, `${formatCount(Math.max(m.redSoldiers, m.blueSoldiers))}`];
  }));
}

function fillBoard(key: string, rows: Array<[string, string]>): void {
  const list = q(key);
  list.textContent = '';
  for (const [label, value] of rows) {
    const item = document.createElement('li');
    item.textContent = label;
    const span = document.createElement('span');
    span.textContent = value;
    item.appendChild(span);
    list.appendChild(item);
  }
}

// ------------------------------------------------------------------ setup

function populateSelectors(): void {
  for (const key of ['redCountry', 'blueCountry']) {
    const select = q<HTMLSelectElement>(key);
    select.textContent = '';
    for (const [code, country] of Object.entries(config.countries)) {
      const option = document.createElement('option');
      option.value = code;
      option.textContent = `${country.flag} ${country.name}`;
      select.appendChild(option);
    }
  }

  const unitSelect = q<HTMLSelectElement>('troopUnit');
  unitSelect.textContent = '';
  for (const [key, unit] of Object.entries(config.units)) {
    const option = document.createElement('option');
    option.value = key;
    option.textContent = unit.label;
    unitSelect.appendChild(option);
  }

  const ultSelect = q<HTMLSelectElement>('ultKey');
  ultSelect.textContent = '';
  for (const [key, def] of Object.entries(config.ultimates.defs)) {
    const option = document.createElement('option');
    option.value = key;
    option.textContent = `${def.icon} ${def.label}`;
    ultSelect.appendChild(option);
  }

  // Valores de arranque desde la config. En cuanto llegue el primer estado del
  // overlay, mandan los suyos.
  const g = config.graphics;
  const starts: Record<string, number> = {
    difficulty: config.battle.difficulty,
    camFov: config.camera.fov,
    gExposure: g.exposure,
    gContrast: g.contrast,
    gSaturation: g.saturation,
    gBloom: g.bloomIntensity,
    gVignette: g.vignette,
    gLod: g.lodDistance,
    gTexture: g.textureDistance,
    gFog: g.fogDensity,
  };
  for (const [key, value] of Object.entries(starts)) syncSlider(key, value);
  for (const input of document.querySelectorAll<HTMLInputElement>('input[type="range"]')) paintSlider(input);

  syncPills('cameraMode', config.camera.mode);
  syncPills('quality', g.quality);
  syncPills('season', config.world?.season ?? 'summer');
  syncPills('weather', config.world?.weather ?? 'clear');
  q('overlayUrl').textContent = new URL('index.html', location.href).href;
}

/** Encuadres listos para usar, por si no apetece tocar seis deslizadores en vivo. */
const CAMERA_PRESETS: Record<string, Record<string, number | boolean>> = {
  epic: { distance: 1.6, height: 1.5, fov: 46, cutSpeed: 1.6, orbit: 0.6, shake: 1 },
  action: { distance: 0.55, height: 0.45, fov: 62, cutSpeed: 0.6, orbit: 1.6, shake: 1.5 },
  tactic: { distance: 1.3, height: 2.1, fov: 40, cutSpeed: 2, orbit: 0.25, shake: 0.4 },
  default: { distance: 1, height: 1, fov: 52, cutSpeed: 1, orbit: 1, shake: 1 },
};

function bindTabs(): void {
  document.addEventListener('click', (ev) => {
    const tab = (ev.target as HTMLElement).closest<HTMLElement>('[data-tab]');
    if (!tab) return;
    for (const node of document.querySelectorAll('.tab')) node.classList.toggle('is-active', node === tab);
    for (const page of document.querySelectorAll<HTMLElement>('.page')) {
      page.classList.toggle('is-active', page.dataset.page === tab.dataset.tab);
    }
  });
}

/** Un solo manejador para todos los deslizadores declarados en el HTML. */
function bindSliders(): void {
  for (const input of document.querySelectorAll<HTMLInputElement>('input[type="range"][data-cmd]')) {
    input.addEventListener('input', () => {
      paintSlider(input);
      const format = input.dataset.format ?? 'plain';
      send({
        type: 'control',
        action: input.dataset.cmd,
        [input.dataset.key!]: toWire(format, Number(input.value)),
      } as unknown as ControlCommand);
    });
  }
  for (const box of document.querySelectorAll<HTMLInputElement>('input[type="checkbox"][data-cmd]')) {
    box.addEventListener('change', () => {
      send({ type: 'control', action: box.dataset.cmd, [box.dataset.key!]: box.checked } as unknown as ControlCommand);
    });
  }
}

/** Grupos de botones excluyentes (modo de cámara, calidad, estación, clima). */
const PILL_COMMANDS: Record<string, (value: string) => ControlCommand> = {
  cameraMode: (mode) => ({ type: 'control', action: 'setCamera', mode }),
  quality: (quality) => ({ type: 'control', action: 'setGraphics', quality }),
  season: (season) => ({ type: 'control', action: 'setSeason', season }),
  weather: (weather) => ({ type: 'control', action: 'setWeather', weather }),
};

function bindPills(): void {
  document.addEventListener('click', (ev) => {
    const pill = (ev.target as HTMLElement).closest<HTMLElement>('.pill');
    const host = pill?.closest<HTMLElement>('[data-pills]');
    if (!pill || !host) return;
    const group = host.dataset.pills!;
    const value = pill.dataset.value!;
    syncPills(group, value);
    const build = PILL_COMMANDS[group];
    if (build) send(build(value));
  });
}

function bindActions(): void {
  document.addEventListener('click', (ev) => {
    const button = (ev.target as HTMLElement).closest<HTMLElement>('[data-action]');
    if (!button) return;
    const action = button.dataset.action!;

    switch (action) {
      case 'start':
      case 'pause':
      case 'resume':
      case 'reset':
      case 'skipRound':
      case 'resetSeries':
      case 'reloadConfig':
        send({ type: 'control', action } as ControlCommand);
        break;

      case 'camPreset': {
        const preset = CAMERA_PRESETS[button.dataset.preset ?? 'default'];
        send({ type: 'control', action: 'tuneCamera', ...preset } as unknown as ControlCommand);
        for (const [key, slider] of Object.entries({
          distance: 'camDistance',
          height: 'camHeight',
          fov: 'camFov',
          cutSpeed: 'camCut',
          orbit: 'camOrbit',
          shake: 'camShake',
        })) {
          if (typeof preset[key] === 'number') syncSlider(slider, preset[key]);
        }
        break;
      }

      case 'addTroops': {
        const preset = button.dataset.amount;
        const troops = Number(preset ?? q<HTMLInputElement>('troopAmount').value) || 0;
        if (troops <= 0) return;
        send({
          type: 'control',
          action: 'addTroops',
          team: q<HTMLSelectElement>('troopTeam').value as TeamId,
          troops,
          unit: q<HTMLSelectElement>('troopUnit').value,
        });
        break;
      }

      case 'castUltimate':
        send({
          type: 'control',
          action: 'castUltimate',
          team: q<HTMLSelectElement>('ultTeam').value as TeamId,
          ultimate: q<HTMLSelectElement>('ultKey').value,
        });
        break;

      case 'fillRage':
        send({ type: 'control', action: 'setRage', team: button.dataset.team as TeamId, value: config.likes.rageMax });
        break;

      case 'testGift':
        emitTestEvent({
          type: 'gift',
          uniqueId: q<HTMLInputElement>('testUser').value || 'tester',
          nickname: q<HTMLInputElement>('testUser').value || 'tester',
          giftName: q<HTMLInputElement>('testGift').value || 'Rosa',
          diamondCount: Number(q<HTMLInputElement>('testCoins').value) || 0,
          repeatCount: Number(q<HTMLInputElement>('testCount').value) || 1,
          repeatEnd: true,
        });
        break;

      case 'testChat':
        emitTestEvent({
          type: 'chat',
          uniqueId: q<HTMLInputElement>('testUser').value || 'tester',
          nickname: q<HTMLInputElement>('testUser').value || 'tester',
          comment: q<HTMLInputElement>('testChat').value || 'rojo',
        });
        break;

      case 'testLike':
        emitTestEvent({
          type: 'like',
          uniqueId: q<HTMLInputElement>('testUser').value || 'tester',
          nickname: q<HTMLInputElement>('testUser').value || 'tester',
          likeCount: 50,
        });
        break;

      case 'ban':
      case 'unban': {
        const uniqueId = q<HTMLInputElement>('banUser').value.trim().replace(/^@/, '');
        if (!uniqueId) return;
        send({ type: 'control', action, uniqueId });
        if (action === 'ban') banned.add(uniqueId);
        else banned.delete(uniqueId);
        renderBanList();
        q<HTMLInputElement>('banUser').value = '';
        break;
      }
    }
  });

  for (const team of ['red', 'blue'] as const) {
    q(`${team}Country`).addEventListener('change', (ev) =>
      send({ type: 'control', action: 'setCountry', team, country: (ev.target as HTMLSelectElement).value }),
    );
  }

  q('simToggle').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setSimulator', enabled: (ev.target as HTMLInputElement).checked }),
  );
}

function renderBanList(): void {
  const list = q('banList');
  list.textContent = '';
  for (const uniqueId of banned) {
    const item = document.createElement('li');
    item.textContent = `@${uniqueId}`;
    list.appendChild(item);
  }
}

async function boot(): Promise<void> {
  collect();
  config = await loadConfig();
  apiBase = resolveEndpoints(config).apiBase;
  populateSelectors();
  bindTabs();
  bindSliders();
  bindPills();
  bindActions();
  connect();
  watchConnection();
  void refreshBoards();
  setInterval(() => void refreshBoards(), 10000);
}

void boot();
