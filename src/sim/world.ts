/**
 * Mundo de simulación: combate masivo con estructura de arrays (SoA).
 *
 * Idea central — el "stack":
 * TikTok pide ver "40,559 soldados", pero renderizar 40 mil mallas mata el
 * stream. Cada entidad simulada representa N soldados (`stack`): su vida y su
 * daño escalan con N, así que la batalla se resuelve al mismo ritmo que si
 * simuláramos individuos, pero solo dibujamos unos miles. El contador de la HUD
 * suma `stack * vida%`, por eso baja de forma continua y creíble.
 */

import { clamp, createRng, type Rng } from '../shared/math';
import { createTerrain, type Terrain } from '../shared/terrain';
import { SpatialGrid } from './grid';
import {
  GROUP_KEYS,
  PROJECTILE_STRIDE,
  UNIT_STRIDE,
  type DeathReport,
  type GroupKey,
  type ImpactReport,
  type MeteorReport,
  type SimSettings,
  type TeamStats,
  type UltimatePayload,
  type UnitArchetypeWire,
} from './protocol';

const ALIVE_FREE = 0;
const ALIVE_FIGHTING = 1;
const ALIVE_DYING = 2;

const DEATH_DURATION = 0.9;
const MAX_PROJECTILES = 3000;
const MAX_METEORS = 256;
const MESH_INDEX: Record<string, number> = { humanoid: 0, beast: 1, dragon: 2 };

interface TeamRuntime {
  /** Tropas encoladas por tipo de unidad: un regalo de arqueros despliega arqueros. */
  reserveByUnit: Map<number, number>;
  reserveTotal: number;
  kills: number;
  deaths: number;
  soldiersLostThisRound: number;
  damageMul: number;
  damageMulTimer: number;
  speedMul: number;
  speedMulTimer: number;
  damageTakenMul: number;
  damageTakenTimer: number;
  underdogBonus: number;
}

function newTeamRuntime(): TeamRuntime {
  return {
    reserveByUnit: new Map(),
    reserveTotal: 0,
    kills: 0,
    deaths: 0,
    soldiersLostThisRound: 0,
    damageMul: 1,
    damageMulTimer: 0,
    speedMul: 1,
    speedMulTimer: 0,
    damageTakenMul: 1,
    damageTakenTimer: 0,
    underdogBonus: 0,
  };
}

export class World {
  private settings!: SimSettings;
  private terrain!: Terrain;
  private rng!: Rng;
  private archetypes: UnitArchetypeWire[] = [];
  private archetypeByKey = new Map<string, number>();
  private capacity = 0;

  // --- Arrays de unidades (SoA) ---
  private px!: Float32Array;
  private py!: Float32Array;
  private pz!: Float32Array;
  private vx!: Float32Array;
  private vz!: Float32Array;
  private hp!: Float32Array;
  private maxHp!: Float32Array;
  private stack!: Float32Array;
  private rot!: Float32Array;
  private phase!: Float32Array;
  private cooldown!: Float32Array;
  private attackAnim!: Float32Array;
  private deathTimer!: Float32Array;
  private target!: Int32Array;
  private championId!: Int32Array;
  private kills!: Float32Array;
  private bornAt!: Float32Array;
  private team!: Uint8Array;
  private type!: Uint8Array;
  private alive!: Uint8Array;
  private rescanSlot!: Uint8Array;

  private freeList!: Int32Array;
  private freeCount = 0;
  private liveIndices!: Int32Array;
  private liveCount = 0;
  private members: [Int32Array, Int32Array] = [new Int32Array(0), new Int32Array(0)];
  private memberCounts: [number, number] = [0, 0];
  private grids: [SpatialGrid, SpatialGrid] | null = null;

  // --- Proyectiles ---
  private prX = new Float32Array(MAX_PROJECTILES);
  private prY = new Float32Array(MAX_PROJECTILES);
  private prZ = new Float32Array(MAX_PROJECTILES);
  private prTX = new Float32Array(MAX_PROJECTILES);
  private prTY = new Float32Array(MAX_PROJECTILES);
  private prTZ = new Float32Array(MAX_PROJECTILES);
  private prSpeed = new Float32Array(MAX_PROJECTILES);
  private prDamage = new Float32Array(MAX_PROJECTILES);
  private prSplash = new Float32Array(MAX_PROJECTILES);
  private prTeam = new Uint8Array(MAX_PROJECTILES);
  private prKind = new Uint8Array(MAX_PROJECTILES);
  private prActive = new Uint8Array(MAX_PROJECTILES);
  private prLife = new Float32Array(MAX_PROJECTILES);
  private projectileCount = 0;

  // --- Meteoros pendientes ---
  private mtX = new Float32Array(MAX_METEORS);
  private mtZ = new Float32Array(MAX_METEORS);
  private mtDelay = new Float32Array(MAX_METEORS);
  private mtDamage = new Float32Array(MAX_METEORS);
  private mtRadius = new Float32Array(MAX_METEORS);
  private mtTeam = new Uint8Array(MAX_METEORS);
  private mtActive = new Uint8Array(MAX_METEORS);

  private teams: [TeamRuntime, TeamRuntime] = [newTeamRuntime(), newTeamRuntime()];
  private soldierCache: [number, number] = [0, 0];
  private entityCache: [number, number] = [0, 0];

  // --- Salidas del tick ---
  deaths: DeathReport[] = [];
  impacts: ImpactReport[] = [];
  meteorVisuals: MeteorReport[] = [];
  championDeaths: Array<{ championId: number; killerChampionId: number; kills: number; lifetime: number }> = [];
  hotspot = { x: 0, z: 0, intensity: 0 };
  frontline = 0;

  time = 0;
  tick = 0;
  running = false;
  private suddenDeathMultiplier = 1;
  private deployCarry = 0;
  private hotAccX = 0;
  private hotAccZ = 0;
  private hotAccW = 0;

  // -------------------------------------------------------------------- setup

  init(settings: SimSettings): void {
    this.settings = settings;
    this.archetypes = settings.archetypes;
    this.archetypeByKey.clear();
    settings.archetypes.forEach((a, i) => this.archetypeByKey.set(a.key, i));

    this.terrain = createTerrain({
      seed: settings.seed,
      width: settings.fieldWidth,
      depth: settings.fieldDepth,
      riverWidth: settings.riverWidth,
    });
    this.rng = createRng(settings.seed);

    // Margen extra sobre el tope de render para élites y campeones.
    this.capacity = settings.renderCapPerTeam * 2 + 1024;
    const n = this.capacity;
    this.px = new Float32Array(n);
    this.py = new Float32Array(n);
    this.pz = new Float32Array(n);
    this.vx = new Float32Array(n);
    this.vz = new Float32Array(n);
    this.hp = new Float32Array(n);
    this.maxHp = new Float32Array(n);
    this.stack = new Float32Array(n);
    this.rot = new Float32Array(n);
    this.phase = new Float32Array(n);
    this.cooldown = new Float32Array(n);
    this.attackAnim = new Float32Array(n);
    this.deathTimer = new Float32Array(n);
    this.target = new Int32Array(n);
    this.championId = new Int32Array(n);
    this.kills = new Float32Array(n);
    this.bornAt = new Float32Array(n);
    this.team = new Uint8Array(n);
    this.type = new Uint8Array(n);
    this.alive = new Uint8Array(n);
    this.rescanSlot = new Uint8Array(n);
    this.freeList = new Int32Array(n);
    this.liveIndices = new Int32Array(n);
    this.members = [new Int32Array(n), new Int32Array(n)];

    const cellSize = 6;
    this.grids = [
      new SpatialGrid(settings.fieldWidth + 40, settings.fieldDepth + 40, cellSize, n),
      new SpatialGrid(settings.fieldWidth + 40, settings.fieldDepth + 40, cellSize, n),
    ];

    this.reset(settings.seed, 0);
  }

  reset(seed: number, startingTroops: number): void {
    this.terrain = createTerrain({
      seed,
      width: this.settings.fieldWidth,
      depth: this.settings.fieldDepth,
      riverWidth: this.settings.riverWidth,
    });
    this.rng = createRng(seed);
    this.alive.fill(ALIVE_FREE);
    this.freeCount = 0;
    for (let i = this.capacity - 1; i >= 0; i--) this.freeList[this.freeCount++] = i;
    this.prActive.fill(0);
    this.mtActive.fill(0);
    this.projectileCount = 0;
    this.teams = [newTeamRuntime(), newTeamRuntime()];
    this.soldierCache = [0, 0];
    this.entityCache = [0, 0];
    this.time = 0;
    this.tick = 0;
    this.deployCarry = 0;
    this.suddenDeathMultiplier = 1;
    this.hotspot = { x: 0, z: 0, intensity: 0 };
    this.clearOutputs();
    if (startingTroops > 0) {
      this.addTroops(0, startingTroops, 'soldier');
      this.addTroops(1, startingTroops, 'soldier');
    }
  }

  setRunning(value: boolean): void {
    this.running = value;
  }

  setDifficulty(value: number): void {
    this.settings.difficulty = value;
  }

  setSuddenDeath(multiplier: number): void {
    this.suddenDeathMultiplier = multiplier;
  }

  private clearOutputs(): void {
    this.deaths.length = 0;
    this.impacts.length = 0;
    this.meteorVisuals.length = 0;
    this.championDeaths.length = 0;
  }

  // -------------------------------------------------------------- altas/bajas

  private archetypeIndex(key: string): number {
    return this.archetypeByKey.get(key) ?? this.archetypeByKey.get('soldier') ?? 0;
  }

  /**
   * Encola tropas. Se despliegan poco a poco desde la retaguardia, así se ven
   * llegar las oleadas en vez de aparecer 30.000 soldados de golpe.
   */
  addTroops(teamIdx: 0 | 1, troops: number, unitKey: string, count?: number): void {
    const typeIdx = this.archetypeIndex(unitKey);
    const arch = this.archetypes[typeIdx];
    if (!arch) return;
    const runtime = this.teams[teamIdx];

    // Unidades élite (gigantes, dragones): entran ya, una entidad por unidad.
    if (count && count > 0) {
      for (let i = 0; i < count; i++) this.spawn(teamIdx, typeIdx, 1, -1);
    } else if (!arch.stackable && troops > 0) {
      // Una unidad no apilable sin `count` explícito: al menos una entra al campo.
      this.spawn(teamIdx, typeIdx, 1, -1);
    }

    if (troops <= 0) return;
    // Solo lo apilable puede encolarse como masa; el resto suma como soldados.
    const queueType = arch.stackable ? typeIdx : this.archetypeIndex('soldier');
    runtime.reserveByUnit.set(queueType, (runtime.reserveByUnit.get(queueType) ?? 0) + troops);
    runtime.reserveTotal += troops;
  }

  addChampion(teamIdx: 0 | 1, championId: number): number {
    const typeIdx = this.archetypeIndex('champion');
    return this.spawn(teamIdx, typeIdx, 1, championId);
  }

  private spawn(teamIdx: 0 | 1, typeIdx: number, stack: number, championId: number): number {
    if (this.freeCount === 0) return -1;
    const arch = this.archetypes[typeIdx];
    const idx = this.freeList[--this.freeCount];

    const half = this.terrain.halfWidth;
    const sign = teamIdx === 0 ? -1 : 1;
    // Aparecen escalonados en su mitad y avanzan, así se ven llegar las oleadas.
    // La franja no puede empezar muy atrás: cada segundo que los ejércitos tardan
    // en encontrarse es un segundo de directo sin nada que mirar.
    const x = sign * this.rng.range(half * 0.32, half * 0.74);
    const z = this.rng.range(-this.terrain.halfDepth * 0.88, this.terrain.halfDepth * 0.88);

    this.px[idx] = x;
    this.pz[idx] = z;
    this.py[idx] = this.terrain.height(x, z) + (arch.flying ? arch.flyHeight : 0);
    this.vx[idx] = 0;
    this.vz[idx] = 0;
    this.stack[idx] = stack;
    this.maxHp[idx] = arch.hp * stack;
    this.hp[idx] = this.maxHp[idx];
    this.rot[idx] = teamIdx === 0 ? Math.PI / 2 : -Math.PI / 2;
    this.phase[idx] = this.rng() * 6.283;
    this.cooldown[idx] = this.rng() * arch.attackCooldown;
    this.attackAnim[idx] = 0;
    this.deathTimer[idx] = 0;
    this.target[idx] = -1;
    this.championId[idx] = championId;
    this.kills[idx] = 0;
    this.bornAt[idx] = this.time;
    this.team[idx] = teamIdx;
    this.type[idx] = typeIdx;
    this.alive[idx] = ALIVE_FIGHTING;
    this.rescanSlot[idx] = (this.rng.int(0, 255) as number) & 0xff;
    return idx;
  }

  private killUnit(idx: number, killerIdx: number): void {
    if (this.alive[idx] !== ALIVE_FIGHTING) return;
    const teamIdx = this.team[idx] as 0 | 1;
    const arch = this.archetypes[this.type[idx]];
    const soldiers = Math.max(1, Math.round(this.stack[idx]));

    this.alive[idx] = ALIVE_DYING;
    this.deathTimer[idx] = 0;
    this.hp[idx] = 0;
    this.teams[teamIdx].deaths++;
    this.teams[teamIdx].soldiersLostThisRound += soldiers;

    const killerChampion = killerIdx >= 0 ? this.championId[killerIdx] : -1;

    this.deaths.push({
      x: this.px[idx],
      y: this.py[idx],
      z: this.pz[idx],
      rot: this.rot[idx],
      team: teamIdx,
      soldiers,
      championId: this.championId[idx],
      killerChampionId: killerChampion,
      mesh: MESH_INDEX[arch.mesh] ?? 0,
      scale: arch.scale,
    });

    if (this.championId[idx] >= 0) {
      this.championDeaths.push({
        championId: this.championId[idx],
        killerChampionId: killerChampion,
        kills: Math.round(this.kills[idx]),
        lifetime: this.time - this.bornAt[idx],
      });
    }

    // Acumulador para el "punto caliente" que sigue la cámara.
    const w = Math.min(soldiers, 200);
    this.hotAccX += this.px[idx] * w;
    this.hotAccZ += this.pz[idx] * w;
    this.hotAccW += w;
  }

  private freeSlot(idx: number): void {
    this.alive[idx] = ALIVE_FREE;
    this.freeList[this.freeCount++] = idx;
  }

  // ------------------------------------------------------------------- daño

  private applyDamage(idx: number, rawDamage: number, killerIdx: number): void {
    if (this.alive[idx] !== ALIVE_FIGHTING) return;
    const arch = this.archetypes[this.type[idx]];
    const teamIdx = this.team[idx] as 0 | 1;
    // La armadura da reducción porcentual con rendimientos decrecientes.
    const reduction = arch.armor / (arch.armor + 50);
    const dmg = Math.min(this.hp[idx], rawDamage * (1 - reduction) * this.teams[teamIdx].damageTakenMul);
    this.hp[idx] -= dmg;

    // Las bajas se acreditan por daño causado, no por el golpe final. Con unidades
    // que representan a decenas de soldados, el remate es casi aleatorio: un campeón
    // podía pelear un minuto entero y quedarse con el contador en cero.
    if (killerIdx >= 0) {
      const soldiersFelled = (dmg / (this.maxHp[idx] || 1)) * this.stack[idx];
      this.kills[killerIdx] += soldiersFelled;
      this.teams[this.team[killerIdx] as 0 | 1].kills += soldiersFelled;
    }

    if (this.hp[idx] <= 0) this.killUnit(idx, killerIdx);
  }

  private areaDamage(x: number, z: number, radius: number, damage: number, enemyTeam: 0 | 1, killerIdx: number): void {
    const grid = this.grids![enemyTeam];
    const r2 = radius * radius;
    const cxMin = grid.cellX(x - radius);
    const cxMax = grid.cellX(x + radius);
    const czMin = grid.cellZ(z - radius);
    const czMax = grid.cellZ(z + radius);
    for (let cz = czMin; cz <= czMax; cz++) {
      for (let cx = cxMin; cx <= cxMax; cx++) {
        const cell = cz * grid.cols + cx;
        const end = grid.cellEnd(cell);
        for (let i = grid.cellStart(cell); i < end; i++) {
          const idx = grid.items[i];
          const dx = this.px[idx] - x;
          const dz = this.pz[idx] - z;
          const d2 = dx * dx + dz * dz;
          if (d2 > r2) continue;
          // El daño decae del centro al borde.
          const falloff = 1 - Math.sqrt(d2) / radius;
          this.applyDamage(idx, damage * (0.35 + 0.65 * falloff), killerIdx);
        }
      }
    }
  }

  // ------------------------------------------------------------------ ataques

  private spawnProjectile(from: number, targetIdx: number, damage: number, splash: number, kind: number): void {
    let slot = -1;
    for (let i = 0; i < MAX_PROJECTILES; i++) {
      const probe = (this.projectileCount + i) % MAX_PROJECTILES;
      if (!this.prActive[probe]) {
        slot = probe;
        break;
      }
    }
    if (slot < 0) return;
    this.projectileCount = (slot + 1) % MAX_PROJECTILES;

    const arch = this.archetypes[this.type[from]];
    this.prX[slot] = this.px[from];
    this.prY[slot] = this.py[from] + arch.scale * 1.4;
    this.prZ[slot] = this.pz[from];
    this.prTX[slot] = this.px[targetIdx];
    this.prTY[slot] = this.py[targetIdx] + 0.8;
    this.prTZ[slot] = this.pz[targetIdx];
    this.prSpeed[slot] = kind === 1 ? 55 : kind === 2 ? 30 : 42;
    this.prDamage[slot] = damage;
    this.prSplash[slot] = splash;
    this.prTeam[slot] = this.team[from];
    this.prKind[slot] = kind;
    this.prActive[slot] = 1;
    this.prLife[slot] = 0;
  }

  private updateProjectiles(dt: number): void {
    for (let i = 0; i < MAX_PROJECTILES; i++) {
      if (!this.prActive[i]) continue;
      this.prLife[i] += dt;
      const dx = this.prTX[i] - this.prX[i];
      const dy = this.prTY[i] - this.prY[i];
      const dz = this.prTZ[i] - this.prZ[i];
      const dist = Math.hypot(dx, dy, dz);
      const step = this.prSpeed[i] * dt;

      if (dist <= step || this.prLife[i] > 4) {
        this.prActive[i] = 0;
        const enemy = (1 - this.prTeam[i]) as 0 | 1;
        const splash = this.prSplash[i];
        if (splash > 0) {
          this.areaDamage(this.prTX[i], this.prTZ[i], splash, this.prDamage[i], enemy, -1);
          this.impacts.push({ x: this.prTX[i], y: this.prTY[i], z: this.prTZ[i], kind: 1, power: splash });
        } else {
          const hit = this.grids![enemy].findNearest(this.prTX[i], this.prTZ[i], 2.5, this.px, this.pz);
          if (hit >= 0) this.applyDamage(hit, this.prDamage[i], -1);
          this.impacts.push({ x: this.prTX[i], y: this.prTY[i], z: this.prTZ[i], kind: 0, power: 1 });
        }
        continue;
      }

      const inv = step / (dist || 1);
      this.prX[i] += dx * inv;
      this.prY[i] += dy * inv + (this.prKind[i] === 1 ? Math.sin(this.prLife[i] * 3) * 0.06 : 0);
      this.prZ[i] += dz * inv;
    }
  }

  // ----------------------------------------------------------------- ultimates

  castUltimate(teamIdx: 0 | 1, payload: UltimatePayload): void {
    const runtime = this.teams[teamIdx];
    const enemy = (1 - teamIdx) as 0 | 1;

    if (payload.damageMultiplier) {
      runtime.damageMul = payload.damageMultiplier;
      runtime.damageMulTimer = payload.durationSeconds;
    }
    if (payload.speedMultiplier) {
      runtime.speedMul = payload.speedMultiplier;
      runtime.speedMulTimer = payload.durationSeconds;
    }
    if (payload.damageTakenMultiplier) {
      runtime.damageTakenMul = payload.damageTakenMultiplier;
      runtime.damageTakenTimer = payload.durationSeconds;
    }
    if (payload.troops) {
      this.addTroops(teamIdx, payload.troops, payload.unit ?? 'soldier');
    }
    if (payload.revivePercent) {
      const revived = Math.min(payload.maxRevive ?? Infinity, Math.floor(runtime.soldiersLostThisRound * payload.revivePercent));
      if (revived > 0) {
        runtime.soldiersLostThisRound -= revived;
        this.addTroops(teamIdx, revived, 'soldier');
      }
    }
    if (payload.meteors && payload.damage && payload.radius) {
      this.scheduleMeteors(enemy, payload.meteors, payload.damage, payload.radius, payload.durationSeconds);
    }
  }

  private scheduleMeteors(targetTeam: 0 | 1, count: number, damage: number, radius: number, duration: number): void {
    // Los meteoros caen donde de verdad hay enemigos, no en huecos vacíos.
    const grid = this.grids![targetTeam];
    let scheduled = 0;
    for (let i = 0; i < MAX_METEORS && scheduled < count; i++) {
      if (this.mtActive[i]) continue;
      let x: number;
      let z: number;
      if (grid.size > 0) {
        const pick = grid.items[this.rng.int(0, grid.size - 1)];
        x = this.px[pick] + this.rng.range(-14, 14);
        z = this.pz[pick] + this.rng.range(-14, 14);
      } else {
        const sign = targetTeam === 0 ? -1 : 1;
        x = sign * this.rng.range(10, this.terrain.halfWidth * 0.9);
        z = this.rng.range(-this.terrain.halfDepth * 0.9, this.terrain.halfDepth * 0.9);
      }
      const point = { x, z };
      this.terrain.clampToField(point);
      this.mtX[i] = point.x;
      this.mtZ[i] = point.z;
      this.mtDelay[i] = this.rng.range(0.25, Math.max(0.3, duration));
      this.mtDamage[i] = damage;
      this.mtRadius[i] = radius;
      this.mtTeam[i] = targetTeam;
      this.mtActive[i] = 1;
      this.meteorVisuals.push({ x: point.x, y: this.terrain.height(point.x, point.z), z: point.z, delay: this.mtDelay[i], radius });
      scheduled++;
    }
  }

  private updateMeteors(dt: number): void {
    for (let i = 0; i < MAX_METEORS; i++) {
      if (!this.mtActive[i]) continue;
      this.mtDelay[i] -= dt;
      if (this.mtDelay[i] > 0) continue;
      this.mtActive[i] = 0;
      this.areaDamage(this.mtX[i], this.mtZ[i], this.mtRadius[i], this.mtDamage[i], this.mtTeam[i] as 0 | 1, -1);
      this.impacts.push({ x: this.mtX[i], y: this.terrain.height(this.mtX[i], this.mtZ[i]), z: this.mtZ[i], kind: 2, power: this.mtRadius[i] });
    }
  }

  // -------------------------------------------------------------- despliegue

  private deploy(dt: number): void {
    const cap = this.settings.renderCapPerTeam;
    this.deployCarry += this.settings.deployPerSecond * dt;
    if (this.deployCarry < 1) return;
    const budget = Math.floor(this.deployCarry);
    this.deployCarry -= budget;

    // Cada equipo despliega con su propio presupuesto: si no, el primero en
    // iterar se comería todo y el otro nunca recibiría refuerzos.
    for (let t = 0 as 0 | 1; t < 2; t = (t + 1) as 0 | 1) {
      const runtime = this.teams[t];
      if (runtime.reserveTotal <= 0) continue;
      const totalTroops = this.soldierCache[t] + runtime.reserveTotal;
      // Cuántos soldados representa cada entidad para no pasar del tope de render.
      const targetStack = Math.max(1, Math.ceil(totalTroops / cap));
      let spent = 0;

      while (runtime.reserveTotal > 0 && spent < budget) {
        // Se atiende primero la cola más grande, para que ningún tipo de unidad
        // se quede esperando eternamente detrás de otro.
        let queueType = -1;
        let queued = 0;
        for (const [type, amount] of runtime.reserveByUnit) {
          if (amount > queued) {
            queued = amount;
            queueType = type;
          }
        }
        if (queueType < 0 || queued <= 0) break;

        if (this.entityCache[t] + 1 <= cap && this.freeCount > 0) {
          const chunk = Math.min(queued, targetStack);
          const idx = this.spawn(t, queueType, chunk, -1);
          if (idx < 0) break;
          this.consumeReserve(runtime, queueType, chunk);
          spent += chunk;
          this.entityCache[t]++;
        } else {
          // Tope de entidades alcanzado: los refuerzos engordan a las que ya pelean.
          const reinforced = this.reinforceExisting(t, Math.min(queued, budget - spent));
          if (reinforced <= 0) break;
          this.consumeReserve(runtime, queueType, reinforced);
          spent += reinforced;
        }
      }
    }
  }

  private consumeReserve(runtime: TeamRuntime, typeIdx: number, amount: number): void {
    const left = (runtime.reserveByUnit.get(typeIdx) ?? 0) - amount;
    if (left > 0) runtime.reserveByUnit.set(typeIdx, left);
    else runtime.reserveByUnit.delete(typeIdx);
    runtime.reserveTotal = Math.max(0, runtime.reserveTotal - amount);
  }

  /** Reparte tropas entre unidades vivas subiendo su stack (y su vida). */
  private reinforceExisting(teamIdx: 0 | 1, troops: number): number {
    const count = this.memberCounts[teamIdx];
    if (count === 0 || troops <= 0) return 0;
    const picks = Math.min(24, count);
    const per = troops / picks;
    let applied = 0;
    for (let i = 0; i < picks; i++) {
      const idx = this.members[teamIdx][this.rng.int(0, count - 1)];
      if (this.alive[idx] !== ALIVE_FIGHTING) continue;
      const arch = this.archetypes[this.type[idx]];
      if (!arch.stackable) continue;
      const add = Math.max(1, Math.round(per));
      this.stack[idx] += add;
      this.maxHp[idx] += arch.hp * add;
      this.hp[idx] += arch.hp * add;
      applied += add;
    }
    return applied;
  }

  // ------------------------------------------------------------------- tick

  step(dt: number): void {
    this.clearOutputs();
    if (!this.running) {
      this.refreshIndices();
      return;
    }

    this.time += dt;
    this.tick++;

    this.refreshIndices();
    this.buildGrids();
    this.updateTeamModifiers(dt);
    this.updateAutoBalance();
    this.deploy(dt);
    this.updateUnits(dt);
    this.updateProjectiles(dt);
    this.updateMeteors(dt);
    this.updateHotspot(dt);
  }

  /** Recolecta índices vivos y miembros por equipo; libera slots de agonizantes. */
  private refreshIndices(): void {
    this.liveCount = 0;
    this.memberCounts = [0, 0];
    this.soldierCache = [0, 0];
    this.entityCache = [0, 0];

    for (let i = 0; i < this.capacity; i++) {
      const state = this.alive[i];
      if (state === ALIVE_FREE) continue;
      this.liveIndices[this.liveCount++] = i;
      if (state !== ALIVE_FIGHTING) continue;
      const t = this.team[i] as 0 | 1;
      this.members[t][this.memberCounts[t]++] = i;
      this.entityCache[t]++;
      this.soldierCache[t] += this.stack[i] * (this.hp[i] / (this.maxHp[i] || 1));
    }
  }

  private buildGrids(): void {
    this.grids![0].build(this.members[0], this.memberCounts[0], this.px, this.pz);
    this.grids![1].build(this.members[1], this.memberCounts[1], this.px, this.pz);
  }

  private updateTeamModifiers(dt: number): void {
    for (const runtime of this.teams) {
      if (runtime.damageMulTimer > 0) {
        runtime.damageMulTimer -= dt;
        if (runtime.damageMulTimer <= 0) runtime.damageMul = 1;
      }
      if (runtime.speedMulTimer > 0) {
        runtime.speedMulTimer -= dt;
        if (runtime.speedMulTimer <= 0) runtime.speedMul = 1;
      }
      if (runtime.damageTakenTimer > 0) {
        runtime.damageTakenTimer -= dt;
        if (runtime.damageTakenTimer <= 0) runtime.damageTakenMul = 1;
      }
    }
  }

  /**
   * Si un equipo aplasta al otro, la ronda se vuelve aburrida y la gente deja de
   * regalar. El bando en desventaja recibe un bonus de daño proporcional para
   * que la pelea siga viéndose reñida sin quitarle el mérito al que va ganando.
   */
  private updateAutoBalance(): void {
    const cfg = this.settings.autoBalance;
    if (!cfg.enabled) {
      this.teams[0].underdogBonus = 0;
      this.teams[1].underdogBonus = 0;
      return;
    }
    const a = this.soldierCache[0] + this.teams[0].reserveTotal;
    const b = this.soldierCache[1] + this.teams[1].reserveTotal;
    if (a <= 0 || b <= 0) return;
    const ratio = a > b ? a / b : b / a;
    const loser = a > b ? 1 : 0;
    const winner = 1 - loser;
    const t = clamp((ratio - cfg.triggerRatio) / (cfg.triggerRatio * 2), 0, 1);
    this.teams[loser].underdogBonus = t * cfg.maxUnderdogDamageBonus;
    this.teams[winner].underdogBonus = 0;
  }

  private updateUnits(dt: number): void {
    const { collisionRadius, separationStrength, targetRescanTicks, riverSlowFactor } = this.settings;
    const globalDamage = this.settings.damageGlobalMultiplier * this.settings.difficulty * this.suddenDeathMultiplier;
    const rescanPhase = this.tick % targetRescanTicks;

    for (let i = 0; i < this.liveCount; i++) {
      const idx = this.liveIndices[i];
      const state = this.alive[idx];

      if (state === ALIVE_DYING) {
        this.deathTimer[idx] += dt;
        if (this.deathTimer[idx] >= DEATH_DURATION) this.freeSlot(idx);
        continue;
      }

      const teamIdx = this.team[idx] as 0 | 1;
      const runtime = this.teams[teamIdx];
      const arch = this.archetypes[this.type[idx]];
      const enemyTeam = (1 - teamIdx) as 0 | 1;

      // --- Adquisición de objetivo (escalonada entre ticks para repartir el costo) ---
      let targetIdx = this.target[idx];
      const needsRescan =
        targetIdx < 0 ||
        this.alive[targetIdx] !== ALIVE_FIGHTING ||
        this.team[targetIdx] !== enemyTeam ||
        this.rescanSlot[idx] % targetRescanTicks === rescanPhase;

      if (needsRescan) {
        const searchRadius = Math.max(arch.range * 1.5, 45);
        targetIdx = this.grids![enemyTeam].findNearest(this.px[idx], this.pz[idx], searchRadius, this.px, this.pz);
        this.target[idx] = targetIdx;
      }

      let moveX = 0;
      let moveZ = 0;
      let inRange = false;

      if (targetIdx >= 0) {
        const dx = this.px[targetIdx] - this.px[idx];
        const dz = this.pz[targetIdx] - this.pz[idx];
        const dist = Math.hypot(dx, dz) || 1e-4;
        if (dist <= arch.range) {
          inRange = true;
        } else {
          moveX = dx / dist;
          moveZ = dz / dist;
        }
        this.rot[idx] = Math.atan2(dx, dz);
      } else {
        // Sin enemigos cerca: avanzar hacia el territorio rival.
        moveX = teamIdx === 0 ? 1 : -1;
        moveZ = Math.sin((this.pz[idx] + this.time) * 0.05) * 0.15;
        this.rot[idx] = Math.atan2(moveX, moveZ);
      }

      // --- Separación: evita que las unidades se apilen en el mismo punto ---
      const grid = this.grids![teamIdx];
      const cell = grid.cellZ(this.pz[idx]) * grid.cols + grid.cellX(this.px[idx]);
      const end = Math.min(grid.cellEnd(cell), grid.cellStart(cell) + 8);
      let sepX = 0;
      let sepZ = 0;
      for (let n = grid.cellStart(cell); n < end; n++) {
        const other = grid.items[n];
        if (other === idx) continue;
        const dx = this.px[idx] - this.px[other];
        const dz = this.pz[idx] - this.pz[other];
        const d2 = dx * dx + dz * dz;
        const minDist = collisionRadius * (arch.scale + this.archetypes[this.type[other]].scale);
        if (d2 > minDist * minDist || d2 < 1e-6) continue;
        const d = Math.sqrt(d2);
        const push = (minDist - d) / minDist;
        sepX += (dx / d) * push;
        sepZ += (dz / d) * push;
      }
      moveX += sepX * separationStrength * 0.2;
      moveZ += sepZ * separationStrength * 0.2;

      // --- Movimiento ---
      const terrainSpeed = arch.flying ? 1 : this.terrain.speedFactor(this.px[idx], this.pz[idx], riverSlowFactor);
      const speed = arch.speed * runtime.speedMul * terrainSpeed;
      const moveLen = Math.hypot(moveX, moveZ);
      if (moveLen > 1e-4 && !inRange) {
        const inv = speed / moveLen;
        this.vx[idx] = moveX * inv;
        this.vz[idx] = moveZ * inv;
      } else if (moveLen > 1e-4) {
        // En rango: solo se aplica la separación, para no romper la línea de combate.
        this.vx[idx] = (sepX * speed) * 0.5;
        this.vz[idx] = (sepZ * speed) * 0.5;
      } else {
        this.vx[idx] = 0;
        this.vz[idx] = 0;
      }

      this.px[idx] += this.vx[idx] * dt;
      this.pz[idx] += this.vz[idx] * dt;
      const point = { x: this.px[idx], z: this.pz[idx] };
      this.terrain.clampToField(point);
      this.px[idx] = point.x;
      this.pz[idx] = point.z;
      this.py[idx] = this.terrain.height(point.x, point.z) + (arch.flying ? arch.flyHeight : 0);

      // Fase de animación de caminata proporcional a la velocidad real.
      const movedSpeed = Math.hypot(this.vx[idx], this.vz[idx]);
      this.phase[idx] += movedSpeed * dt * 2.2;

      // --- Combate ---
      this.cooldown[idx] -= dt;
      this.attackAnim[idx] = Math.max(0, this.attackAnim[idx] - dt * 3.2);

      if (inRange && targetIdx >= 0 && this.cooldown[idx] <= 0) {
        this.cooldown[idx] = arch.attackCooldown;
        this.attackAnim[idx] = 1;
        // El daño escala con los soldados que quedan vivos en el stack (Lanchester).
        const effectiveSoldiers = this.stack[idx] * (this.hp[idx] / (this.maxHp[idx] || 1));
        const damage =
          arch.damage * effectiveSoldiers * globalDamage * runtime.damageMul * (1 + runtime.underdogBonus);

        if (arch.projectile > 0) {
          this.spawnProjectile(idx, targetIdx, damage, arch.splash, arch.projectile);
        } else if (arch.splash > 0) {
          this.areaDamage(this.px[targetIdx], this.pz[targetIdx], arch.splash, damage, enemyTeam, idx);
          this.impacts.push({ x: this.px[targetIdx], y: this.py[targetIdx], z: this.pz[targetIdx], kind: 1, power: arch.splash });
        } else {
          this.applyDamage(targetIdx, damage, idx);
        }
      }
    }
  }

  private updateHotspot(dt: number): void {
    if (this.hotAccW > 0) {
      const x = this.hotAccX / this.hotAccW;
      const z = this.hotAccZ / this.hotAccW;
      const blend = clamp(dt * 1.6, 0, 1);
      this.hotspot.x += (x - this.hotspot.x) * blend;
      this.hotspot.z += (z - this.hotspot.z) * blend;
      this.hotspot.intensity = this.hotAccW;
      this.frontline = this.hotspot.x;
    } else {
      this.hotspot.intensity *= 0.9;
    }
    this.hotAccX = 0;
    this.hotAccZ = 0;
    this.hotAccW = 0;
  }

  // ---------------------------------------------------------------- snapshot

  stats(teamIdx: 0 | 1): TeamStats {
    return {
      soldiers: Math.round(this.soldierCache[teamIdx] + this.teams[teamIdx].reserveTotal),
      entities: this.entityCache[teamIdx],
      reserve: Math.round(this.teams[teamIdx].reserveTotal),
      kills: Math.round(this.teams[teamIdx].kills),
    };
  }

  get requiredUnitFloats(): number {
    return this.capacity * UNIT_STRIDE;
  }

  get requiredProjectileFloats(): number {
    return MAX_PROJECTILES * PROJECTILE_STRIDE;
  }

  /**
   * Escribe todas las unidades en un único buffer intercalado, agrupadas por
   * (malla, equipo) para que el renderer suba un rango contiguo por
   * InstancedMesh sin reordenar nada.
   */
  writeUnits(out: Float32Array): { groups: Array<{ key: GroupKey; start: number; count: number }>; total: number } {
    const groupCount = GROUP_KEYS.length;
    const counts = new Int32Array(groupCount);

    for (let i = 0; i < this.liveCount; i++) {
      const idx = this.liveIndices[i];
      const arch = this.archetypes[this.type[idx]];
      counts[(MESH_INDEX[arch.mesh] ?? 0) * 2 + this.team[idx]]++;
    }

    const starts = new Int32Array(groupCount);
    const cursors = new Int32Array(groupCount);
    let running = 0;
    for (let g = 0; g < groupCount; g++) {
      starts[g] = running;
      cursors[g] = running;
      running += counts[g];
    }

    for (let i = 0; i < this.liveCount; i++) {
      const idx = this.liveIndices[i];
      const arch = this.archetypes[this.type[idx]];
      const g = (MESH_INDEX[arch.mesh] ?? 0) * 2 + this.team[idx];
      const o = cursors[g]++ * UNIT_STRIDE;

      // Codificación de estado: 0..1 marcha, 1..2 ataque, 2..3 muerte.
      let stateValue: number;
      if (this.alive[idx] === ALIVE_DYING) {
        stateValue = 2 + Math.min(0.999, this.deathTimer[idx] / DEATH_DURATION);
      } else if (this.attackAnim[idx] > 0) {
        stateValue = 1 + Math.min(0.999, this.attackAnim[idx]);
      } else {
        stateValue = Math.min(0.999, Math.hypot(this.vx[idx], this.vz[idx]) / (arch.speed || 1));
      }

      out[o] = this.px[idx];
      out[o + 1] = this.py[idx];
      out[o + 2] = this.pz[idx];
      out[o + 3] = this.rot[idx];
      // Las unidades apiladas se ven un poco más corpulentas: da lectura visual del stack.
      out[o + 4] = arch.scale * (arch.stackable ? 1 + Math.min(0.35, Math.log10(1 + this.stack[idx]) * 0.16) : 1);
      out[o + 5] = this.phase[idx];
      out[o + 6] = stateValue;
      out[o + 7] = clamp(this.hp[idx] / (this.maxHp[idx] || 1), 0, 1);
    }

    const groups = GROUP_KEYS.map((key, g) => ({ key, start: starts[g], count: counts[g] }));
    return { groups, total: running };
  }

  writeProjectiles(out: Float32Array): number {
    let n = 0;
    for (let i = 0; i < MAX_PROJECTILES; i++) {
      if (!this.prActive[i]) continue;
      const o = n * PROJECTILE_STRIDE;
      out[o] = this.prX[i];
      out[o + 1] = this.prY[i];
      out[o + 2] = this.prZ[i];
      out[o + 3] = this.prKind[i];
      out[o + 4] = this.prLife[i];
      n++;
    }
    return n;
  }

  /** Posiciones de los campeones vivos, para dibujar sus nametags. */
  championPositions(): Array<{ championId: number; x: number; y: number; z: number; hp: number; kills: number }> {
    const out: Array<{ championId: number; x: number; y: number; z: number; hp: number; kills: number }> = [];
    for (let i = 0; i < this.liveCount; i++) {
      const idx = this.liveIndices[i];
      if (this.championId[idx] < 0 || this.alive[idx] !== ALIVE_FIGHTING) continue;
      out.push({
        championId: this.championId[idx],
        x: this.px[idx],
        y: this.py[idx],
        z: this.pz[idx],
        hp: this.hp[idx] / (this.maxHp[idx] || 1),
        kills: Math.round(this.kills[idx]),
      });
    }
    return out;
  }
}
