/**
 * Protocolo entre el hilo principal y el worker de simulación.
 *
 * El worker no sabe nada de three.js ni de rondas: solo simula el combate y
 * devuelve un snapshot por tick. El buffer de instancias viaja como
 * *transferable* y el hilo principal lo devuelve para reciclarlo, así no se
 * genera basura por frame.
 */

import type { MeshKind, TeamId } from '../shared/config';

/** Floats por instancia en el buffer de unidades: x, y, z, rot, scale, phase, state, health. */
export const UNIT_STRIDE = 8;

/** Floats por proyectil: x, y, z, kind, life. */
export const PROJECTILE_STRIDE = 5;

export type GroupKey = `${MeshKind}_${TeamId}`;

export const GROUP_KEYS: GroupKey[] = [
  'humanoid_red',
  'humanoid_blue',
  'beast_red',
  'beast_blue',
  'dragon_red',
  'dragon_blue',
];

export interface UnitArchetypeWire {
  key: string;
  mesh: MeshKind;
  hp: number;
  damage: number;
  range: number;
  attackCooldown: number;
  speed: number;
  armor: number;
  scale: number;
  splash: number;
  flying: boolean;
  flyHeight: number;
  projectile: number; // 0 = cuerpo a cuerpo, 1 = flecha, 2 = orbe, 3 = fuego
  stackable: boolean;
}

export interface SimSettings {
  seed: number;
  fieldWidth: number;
  fieldDepth: number;
  riverWidth: number;
  riverSlowFactor: number;
  renderCapPerTeam: number;
  deployPerSecond: number;
  collisionRadius: number;
  separationStrength: number;
  cohesionStrength: number;
  targetRescanTicks: number;
  tickRate: number;
  difficulty: number;
  damageGlobalMultiplier: number;
  autoBalance: { enabled: boolean; triggerRatio: number; maxUnderdogDamageBonus: number };
  archetypes: UnitArchetypeWire[];
}

export interface UltimatePayload {
  key: string;
  durationSeconds: number;
  meteors?: number;
  damage?: number;
  radius?: number;
  troops?: number;
  unit?: string;
  damageMultiplier?: number;
  speedMultiplier?: number;
  damageTakenMultiplier?: number;
  revivePercent?: number;
  maxRevive?: number;
}

export type MainToWorker =
  | { t: 'init'; settings: SimSettings }
  | { t: 'reset'; seed: number; startingTroops: number }
  | { t: 'setPaused'; value: boolean }
  | { t: 'setRunning'; value: boolean }
  | { t: 'addTroops'; team: TeamId; troops: number; unit: string; count?: number }
  | { t: 'addChampion'; team: TeamId; championId: number }
  | { t: 'ultimate'; team: TeamId; payload: UltimatePayload }
  | { t: 'setDifficulty'; value: number }
  | { t: 'setSuddenDeath'; multiplier: number }
  | { t: 'recycle'; units: ArrayBuffer; projectiles: ArrayBuffer };

/** Muerte relevante para el hilo principal (sangre, cadáveres, killfeed). */
export interface DeathReport {
  x: number;
  y: number;
  z: number;
  rot: number;
  team: 0 | 1;
  /** Cuántos soldados representaba: escala el charco de sangre. */
  soldiers: number;
  /** -1 si no era campeón. */
  championId: number;
  /** Campeón que lo mató, o -1. */
  killerChampionId: number;
  mesh: number;
  scale: number;
}

export interface ImpactReport {
  x: number;
  y: number;
  z: number;
  kind: number; // 0 = golpe, 1 = explosión, 2 = meteoro
  power: number;
}

export interface MeteorReport {
  x: number;
  y: number;
  z: number;
  delay: number;
  radius: number;
}

export interface TeamStats {
  soldiers: number;
  entities: number;
  reserve: number;
  kills: number;
}

export interface Snapshot {
  t: 'snapshot';
  tick: number;
  time: number;
  units: ArrayBuffer;
  unitCount: number;
  groups: Array<{ key: GroupKey; start: number; count: number }>;
  projectiles: ArrayBuffer;
  projectileCount: number;
  deaths: DeathReport[];
  impacts: ImpactReport[];
  meteors: MeteorReport[];
  championDeaths: Array<{ championId: number; killerChampionId: number; kills: number; lifetime: number }>;
  /** Campeones vivos con su posición, para colocar los nametags en pantalla. */
  champions: Array<{ championId: number; x: number; y: number; z: number; hp: number; kills: number }>;
  stats: { red: TeamStats; blue: TeamStats };
  /** Centro de gravedad del combate: la cámara cinematográfica apunta ahí. */
  hotspot: { x: number; z: number; intensity: number };
  frontline: number;
}

export type WorkerToMain = Snapshot | { t: 'ready' };
