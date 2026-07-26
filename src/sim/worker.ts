/**
 * Worker de simulación.
 *
 * Corre el combate a paso fijo en su propio hilo para que el render nunca se
 * atasque. Cada tick manda un snapshot con los buffers como *transferables*; el
 * hilo principal los devuelve con `recycle` y aquí se reutilizan, así no se
 * genera basura por frame.
 */

import { PROJECTILE_STRIDE, UNIT_STRIDE, type MainToWorker, type Snapshot } from './protocol';
import { World } from './world';

const world = new World();
let initialized = false;
let timer: ReturnType<typeof setInterval> | null = null;
let tickInterval = 1000 / 30;
let lastTime = 0;
let paused = false;

// Pools de buffers reciclados (evitan asignar ~200 KB por tick).
const unitPool: ArrayBuffer[] = [];
const projectilePool: ArrayBuffer[] = [];

function takeBuffer(pool: ArrayBuffer[], floats: number): ArrayBuffer {
  const bytes = floats * 4;
  while (pool.length > 0) {
    const buffer = pool.pop()!;
    // Un buffer transferido de vuelta puede haber quedado "detached": se descarta.
    if (buffer.byteLength >= bytes) return buffer;
  }
  return new ArrayBuffer(bytes);
}

function step(): void {
  if (!initialized) return;
  const now = performance.now();
  const dt = Math.min(0.1, (now - lastTime) / 1000);
  lastTime = now;

  if (!paused) world.step(dt);
  else world.step(0);

  const unitBuffer = takeBuffer(unitPool, world.requiredUnitFloats);
  const projectileBuffer = takeBuffer(projectilePool, world.requiredProjectileFloats);
  const unitView = new Float32Array(unitBuffer);
  const projectileView = new Float32Array(projectileBuffer);

  const { groups, total } = world.writeUnits(unitView);
  const projectileCount = world.writeProjectiles(projectileView);

  const snapshot: Snapshot = {
    t: 'snapshot',
    tick: world.tick,
    time: world.time,
    units: unitBuffer,
    unitCount: total,
    groups,
    projectiles: projectileBuffer,
    projectileCount,
    deaths: world.deaths.slice(),
    impacts: world.impacts.slice(),
    meteors: world.meteorVisuals.slice(),
    championDeaths: world.championDeaths.slice(),
    champions: world.championPositions(),
    stats: { red: world.stats(0), blue: world.stats(1) },
    hotspot: { ...world.hotspot },
    frontline: world.frontline,
  };

  (self as unknown as Worker).postMessage(snapshot, [unitBuffer, projectileBuffer]);
}

function startLoop(): void {
  if (timer) clearInterval(timer);
  lastTime = performance.now();
  timer = setInterval(step, tickInterval);
}

self.onmessage = (ev: MessageEvent<MainToWorker>) => {
  const msg = ev.data;
  switch (msg.t) {
    case 'init': {
      world.init(msg.settings);
      tickInterval = 1000 / Math.max(10, msg.settings.tickRate);
      initialized = true;
      startLoop();
      (self as unknown as Worker).postMessage({ t: 'ready' });
      break;
    }
    case 'reset':
      world.reset(msg.seed, msg.startingTroops);
      break;
    case 'setPaused':
      paused = msg.value;
      break;
    case 'setRunning':
      world.setRunning(msg.value);
      break;
    case 'addTroops':
      world.addTroops(msg.team === 'red' ? 0 : 1, msg.troops, msg.unit, msg.count);
      break;
    case 'addChampion':
      world.addChampion(msg.team === 'red' ? 0 : 1, msg.championId);
      break;
    case 'ultimate':
      world.castUltimate(msg.team === 'red' ? 0 : 1, msg.payload);
      break;
    case 'setDifficulty':
      world.setDifficulty(msg.value);
      break;
    case 'setSuddenDeath':
      world.setSuddenDeath(msg.multiplier);
      break;
    case 'recycle': {
      if (msg.units.byteLength >= UNIT_STRIDE * 4) unitPool.push(msg.units);
      if (msg.projectiles.byteLength >= PROJECTILE_STRIDE * 4) projectilePool.push(msg.projectiles);
      // El pool nunca necesita más de dos buffers en vuelo.
      if (unitPool.length > 3) unitPool.shift();
      if (projectilePool.length > 3) projectilePool.shift();
      break;
    }
  }
};
