/**
 * Modo prueba: un cajón táctil DENTRO del overlay para disparar a mano todo lo
 * que en un directo real llega de la audiencia, y para configurar la escena.
 *
 * Existe porque la demo web es una sola página sin servidor: el panel de
 * control clásico no puede conectarse. Aquí todo va directo — los eventos
 * entran por `bus.ingest()` (el MISMO camino que los reales, con normalizador,
 * combos y moderación) y los mandos usan `game.execute()` (los MISMOS comandos
 * que manda el panel). Nada de atajos que se comporten distinto que producción.
 *
 * Se activa con `?test=1`, con la tecla T, o siempre en el build de demo.
 */

import type { EventBus } from '../events/bus';
import type { EventSimulator } from '../events/simulator';
import type { ControlCommand } from '../events/types';
import type { Game } from '../game/game';
import type { GameRenderer } from '../render/renderer';
import type { GameConfig, TeamId } from '../shared/config';

export interface TestModeDeps {
  game: Game;
  bus: EventBus;
  renderer: GameRenderer;
  simulator: EventSimulator;
  config: GameConfig;
}

const NAMES = ['Sofi', 'ElPrimo', 'Karlita', 'Tito', 'MaruMaru', 'donpepe', 'Crazu', 'Yerferson', 'lucha_libre', 'PatoRey'];

/** Regalos de muestra: uno por tramo de la economía (ver gifts.tiers). */
const GIFTS: Array<{ icon: string; label: string; coins: number }> = [
  { icon: '🌹', label: 'Rosa', coins: 1 },
  { icon: '🏹', label: 'Perfume', coins: 20 },
  { icon: '🐎', label: 'Corona', coins: 150 },
  { icon: '🦣', label: 'León', coins: 600 },
  { icon: '🐉', label: 'Ballena', coins: 2500 },
];

export function mountTestMode(deps: TestModeDeps): void {
  const { game, bus, renderer, simulator, config } = deps;
  let team: TeamId | 'random' = 'random';
  let userCount = 0;

  const pickTeam = (): TeamId => (team === 'random' ? (Math.random() < 0.5 ? 'red' : 'blue') : team);
  const user = () => {
    const name = NAMES[userCount++ % NAMES.length];
    return { uniqueId: `${name.toLowerCase()}_test`, nickname: name };
  };
  const run = (command: ControlCommand) => game.execute(command);

  // ---------------------------------------------------------------- armado
  const root = document.createElement('div');
  root.className = 'test';
  root.innerHTML = `
    <button class="test__fab" data-t="fab" aria-label="Modo prueba">🧪</button>
    <div class="test__drawer" data-t="drawer" hidden>
      <header class="test__head">
        <b>MODO PRUEBA</b>
        <button class="test__close" data-t="close">✕</button>
      </header>

      <section>
        <h3>Bando de los eventos</h3>
        <div class="test__pills" data-group="team">
          <button data-team="red">🔴 Rojo</button>
          <button data-team="random" class="is-on">🎲 Azar</button>
          <button data-team="blue">🔵 Azul</button>
        </div>
      </section>

      <section>
        <h3>Eventos de la audiencia</h3>
        <div class="test__grid" data-t="gifts"></div>
        <div class="test__grid">
          <button data-act="champion">💬 Campeón</button>
          <button data-act="likes">❤️ +200 likes</button>
          <button data-act="follow">⭐ Follow</button>
          <button data-act="share">🔁 Share</button>
          <button data-act="subscribe">💎 Sub</button>
        </div>
      </section>

      <section>
        <h3>Tropas</h3>
        <div class="test__pills" data-group="unit" data-t="units"></div>
        <div class="test__grid">
          <button data-troops="1000">+1.000</button>
          <button data-troops="10000">+10.000</button>
          <button data-troops="50000">+50.000</button>
        </div>
      </section>

      <section>
        <h3>Ultimates</h3>
        <div class="test__grid" data-t="ults"></div>
      </section>

      <section>
        <h3>Partida</h3>
        <div class="test__grid">
          <button data-cmd="start">▶ Iniciar</button>
          <button data-act="pause">⏸ Pausa</button>
          <button data-cmd="skipRound">⏭ Fin de ronda</button>
          <button data-cmd="resetSeries">⟲ Serie</button>
        </div>
        <label class="test__slider">Dificultad
          <input type="range" min="0.3" max="3" step="0.05" value="${config.battle.difficulty}" data-t="difficulty" />
          <output data-t="difficultyOut">${config.battle.difficulty.toFixed(2)}×</output>
        </label>
        <label class="test__check"><input type="checkbox" data-t="sim" ${simulator.running ? 'checked' : ''} /> Eventos automáticos (simulador)</label>
      </section>

      <section>
        <h3>Hora del día</h3>
        <div class="test__pills" data-group="time">
          <button data-time="day" class="is-on">☀️ Día</button>
          <button data-time="sunset">🌇 Atardecer</button>
          <button data-time="night">🌙 Noche</button>
        </div>
      </section>

      <section>
        <h3>Estación</h3>
        <div class="test__pills" data-group="season">
          <button data-season="spring">🌱</button>
          <button data-season="summer" class="is-on">☀️</button>
          <button data-season="autumn">🍂</button>
          <button data-season="winter">❄️</button>
        </div>
        <h3>Clima</h3>
        <div class="test__pills" data-group="weather">
          <button data-weather="clear" class="is-on">☀️</button>
          <button data-weather="rain">🌧️</button>
          <button data-weather="snow">❄️</button>
          <button data-weather="fog">🌫️</button>
          <button data-weather="storm">⛈️</button>
        </div>
      </section>

      <section>
        <h3>Cámara</h3>
        <div class="test__pills" data-group="camera">
          <button data-camera="cinematic" class="is-on">🎬 Cine</button>
          <button data-camera="orbit">🔄 Órbita</button>
          <button data-camera="follow">🎯 Seguir</button>
          <button data-camera="fixed">📌 Fija</button>
        </div>
        <div class="test__grid">
          <button data-preset="epic">🏔 Épica</button>
          <button data-preset="action">💥 Al ras</button>
          <button data-preset="tactic">🗺 Táctica</button>
          <button data-preset="default">↺ Normal</button>
        </div>
      </section>

      <section>
        <h3>Calidad gráfica</h3>
        <div class="test__pills" data-group="quality">
          <button data-quality="low">Baja</button>
          <button data-quality="medium">Media</button>
          <button data-quality="high" class="is-on">Alta</button>
          <button data-quality="ultra">Ultra</button>
        </div>
      </section>
    </div>
  `;
  document.body.appendChild(root);
  const q = <T extends HTMLElement = HTMLElement>(sel: string): T => root.querySelector(sel) as T;

  // Regalos, unidades y ultimates salen de la config real, no de una lista fija.
  const giftsBox = q('[data-t="gifts"]');
  for (const gift of GIFTS) {
    const btn = document.createElement('button');
    btn.dataset.coins = String(gift.coins);
    btn.dataset.gift = gift.label;
    btn.textContent = `${gift.icon} ${gift.coins}🪙`;
    giftsBox.appendChild(btn);
  }
  const unitsBox = q('[data-t="units"]');
  Object.entries(config.units).forEach(([key, unit], index) => {
    const btn = document.createElement('button');
    btn.dataset.unit = key;
    if (index === 0) btn.classList.add('is-on');
    btn.textContent = unit.label;
    unitsBox.appendChild(btn);
  });
  const ultsBox = q('[data-t="ults"]');
  for (const [key, def] of Object.entries(config.ultimates.defs)) {
    const btn = document.createElement('button');
    btn.dataset.ult = key;
    btn.textContent = `${def.icon} ${def.label}`;
    ultsBox.appendChild(btn);
  }

  const CAMERA_PRESETS: Record<string, Record<string, number>> = {
    epic: { distance: 1.6, height: 1.5, fov: 46, cutSpeed: 1.6, orbit: 0.6, shake: 1 },
    action: { distance: 0.55, height: 0.45, fov: 62, cutSpeed: 0.6, orbit: 1.6, shake: 1.5 },
    tactic: { distance: 1.3, height: 2.1, fov: 40, cutSpeed: 2, orbit: 0.25, shake: 0.4 },
    default: { distance: 1, height: 1, fov: 52, cutSpeed: 1, orbit: 1, shake: 1 },
  };

  // ---------------------------------------------------------------- lógica
  const toggleDrawer = (open?: boolean) => {
    const drawer = q('[data-t="drawer"]');
    drawer.hidden = open === undefined ? !drawer.hidden : !open;
  };
  q('[data-t="fab"]').addEventListener('click', () => toggleDrawer());
  q('[data-t="close"]').addEventListener('click', () => toggleDrawer(false));
  window.addEventListener('keydown', (ev) => {
    if (ev.key.toLowerCase() === 't' && !(ev.target instanceof HTMLInputElement)) toggleDrawer();
  });

  root.addEventListener('click', (ev) => {
    const btn = (ev.target as HTMLElement).closest<HTMLElement>('button');
    if (!btn) return;
    const d = btn.dataset;

    // Pills excluyentes: marca visual dentro de su grupo.
    const group = btn.closest<HTMLElement>('[data-group]');
    if (group) {
      for (const sibling of group.querySelectorAll('button')) sibling.classList.toggle('is-on', sibling === btn);
    }

    if (d.team) team = d.team as TeamId | 'random';
    else if (d.coins) {
      bus.ingest({
        type: 'gift',
        ...user(),
        giftName: d.gift,
        diamondCount: Number(d.coins),
        repeatCount: 1,
        repeatEnd: true,
        teamHint: pickTeam(),
      });
    } else if (d.act === 'champion') {
      bus.ingest({ type: 'chat', ...user(), comment: pickTeam() === 'red' ? 'rojo' : 'azul' });
    } else if (d.act === 'likes') {
      bus.ingest({ type: 'like', ...user(), likeCount: 200, teamHint: pickTeam() });
    } else if (d.act === 'follow' || d.act === 'share' || d.act === 'subscribe') {
      bus.ingest({ type: d.act, ...user(), teamHint: pickTeam() });
    } else if (d.troops) {
      const unit = unitsBox.querySelector<HTMLElement>('.is-on')?.dataset.unit ?? 'soldier';
      run({ type: 'control', action: 'addTroops', team: pickTeam(), troops: Number(d.troops), unit });
    } else if (d.ult) {
      run({ type: 'control', action: 'castUltimate', team: pickTeam(), ultimate: d.ult });
    } else if (d.cmd) {
      run({ type: 'control', action: d.cmd } as ControlCommand);
    } else if (d.act === 'pause') {
      game.setPaused(!game.paused);
      btn.textContent = game.paused ? '⏵ Seguir' : '⏸ Pausa';
    } else if (d.time) {
      renderer.setTimeOfDay(d.time as 'day' | 'sunset' | 'night');
    } else if (d.season) {
      run({ type: 'control', action: 'setSeason', season: d.season });
    } else if (d.weather) {
      run({ type: 'control', action: 'setWeather', weather: d.weather });
    } else if (d.camera) {
      run({ type: 'control', action: 'setCamera', mode: d.camera });
    } else if (d.preset) {
      run({ type: 'control', action: 'tuneCamera', ...CAMERA_PRESETS[d.preset] } as ControlCommand);
    } else if (d.quality) {
      run({ type: 'control', action: 'setGraphics', quality: d.quality });
    }
  });

  q<HTMLInputElement>('[data-t="difficulty"]').addEventListener('input', (ev) => {
    const value = Number((ev.target as HTMLInputElement).value);
    q('[data-t="difficultyOut"]').textContent = `${value.toFixed(2)}×`;
    run({ type: 'control', action: 'setDifficulty', value });
  });
  q<HTMLInputElement>('[data-t="sim"]').addEventListener('change', (ev) => {
    simulator.toggle((ev.target as HTMLInputElement).checked);
  });
}
