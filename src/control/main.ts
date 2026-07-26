/**
 * Panel de control del streamer.
 *
 * Habla con el overlay por dos vías a la vez: BroadcastChannel (funciona entre
 * pestañas del mismo navegador, sin necesidad de servidor) y WebSocket (para
 * cuando el overlay corre dentro de OBS, que es un proceso aparte). Manda el
 * comando por las dos y el overlay lo recibe por la que esté disponible.
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

function applyStatus(status: OverlayStatus): void {
  lastStatusAt = performance.now();
  const phases: Record<string, string> = {
    lobby: 'En espera',
    countdown: 'Cuenta atrás',
    battle: 'Batalla',
    roundEnd: 'Fin de ronda',
    seriesEnd: 'Fin de serie',
  };
  q('phase').textContent = (phases[status.phase] ?? status.phase) + (status.paused ? ' (pausa)' : '');
  q('round').textContent = String(status.round);
  q('score').textContent = `${status.wins.red} — ${status.wins.blue} (a ${status.seriesTarget})`;
  q('elapsed').textContent = formatTime(status.roundElapsed);
  q('fps').textContent = String(status.fps);
  q('entities').textContent = `${status.entities.red + status.entities.blue}`;

  q('redSoldiers').textContent = formatCount(status.soldiers.red);
  q('blueSoldiers').textContent = formatCount(status.soldiers.blue);

  const total = Math.max(1, status.soldiers.red + status.soldiers.blue);
  (q('redBar') as HTMLElement).style.width = `${(status.soldiers.red / total) * 100}%`;
  (q('blueBar') as HTMLElement).style.width = `${(status.soldiers.blue / total) * 100}%`;

  const rageMax = config.likes.rageMax || 100;
  q('redRage').textContent = String(Math.round((status.rage.red / rageMax) * 100));
  q('blueRage').textContent = String(Math.round((status.rage.blue / rageMax) * 100));

  q('redLabel').textContent = `${config.countries[status.countries.red]?.flag ?? ''} ${config.teams.red.shortName}`;
  q('blueLabel').textContent = `${config.countries[status.countries.blue]?.flag ?? ''} ${config.teams.blue.shortName}`;

  const redSelect = q<HTMLSelectElement>('redCountry');
  const blueSelect = q<HTMLSelectElement>('blueCountry');
  if (document.activeElement !== redSelect) redSelect.value = status.countries.red;
  if (document.activeElement !== blueSelect) blueSelect.value = status.countries.blue;

  const simToggle = q<HTMLInputElement>('simToggle');
  if (document.activeElement !== simToggle) simToggle.checked = status.simulator;

  const seasons: Record<string, string> = { spring: '🌱 Primavera', summer: '☀️ Verano', autumn: '🍂 Otoño', winter: '❄️ Invierno' };
  const weathers: Record<string, string> = { clear: '☀️ Despejado', rain: '🌧️ Lluvia', snow: '❄️ Nieve', fog: '🌫️ Niebla', storm: '⛈️ Tormenta' };
  q('atmosphere').textContent = `${seasons[status.season] ?? status.season ?? '—'} · ${weathers[status.weather] ?? status.weather ?? '—'}`;
  const seasonSelect = q<HTMLSelectElement>('season');
  const weatherSelect = q<HTMLSelectElement>('weather');
  if (document.activeElement !== seasonSelect && status.season) seasonSelect.value = status.season;
  if (document.activeElement !== weatherSelect && status.weather) weatherSelect.value = status.weather;
}

/** El overlay late cada 500 ms: si deja de hacerlo, se marca como desconectado. */
function watchConnection(): void {
  setInterval(() => {
    const alive = performance.now() - lastStatusAt < 2500;
    q('connDot').classList.toggle('is-online', alive);
    q('connText').textContent = alive ? 'overlay conectado' : 'overlay no detectado';
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

  q<HTMLSelectElement>('season').value = config.world?.season ?? 'summer';
  q<HTMLSelectElement>('weather').value = config.world?.weather ?? 'clear';
  q<HTMLSelectElement>('camera').value = config.camera.mode;
  q<HTMLSelectElement>('quality').value = config.graphics.quality;
  q<HTMLInputElement>('difficulty').value = String(config.battle.difficulty);
  q('difficultyOut').textContent = `${config.battle.difficulty.toFixed(2)}×`;
  q('overlayUrl').textContent = new URL('index.html', location.href).href;
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

  q('redCountry').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setCountry', team: 'red', country: (ev.target as HTMLSelectElement).value }),
  );
  q('blueCountry').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setCountry', team: 'blue', country: (ev.target as HTMLSelectElement).value }),
  );

  q('difficulty').addEventListener('input', (ev) => {
    const value = Number((ev.target as HTMLInputElement).value);
    q('difficultyOut').textContent = `${value.toFixed(2)}×`;
    send({ type: 'control', action: 'setDifficulty', value });
  });

  q('simToggle').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setSimulator', enabled: (ev.target as HTMLInputElement).checked }),
  );

  q('camera').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setCamera', mode: (ev.target as HTMLSelectElement).value }),
  );

  q('quality').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setGraphics', quality: (ev.target as HTMLSelectElement).value }),
  );

  q('season').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setSeason', season: (ev.target as HTMLSelectElement).value }),
  );

  q('weather').addEventListener('change', (ev) =>
    send({ type: 'control', action: 'setWeather', weather: (ev.target as HTMLSelectElement).value }),
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
  bindActions();
  connect();
  watchConnection();
  void refreshBoards();
  setInterval(() => void refreshBoards(), 10000);
}

void boot();
