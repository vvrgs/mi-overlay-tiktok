/**
 * Carga y tipado de `public/config/game.config.json`.
 *
 * El archivo se lee en tiempo de ejecución (no se compila dentro del bundle), así
 * que puedes editarlo en `dist/config/game.config.json` y solo recargar el overlay.
 * Los parámetros de la URL pueden sobreescribir cualquier valor: `?battle.difficulty=1.5`
 */

export type TeamId = 'red' | 'blue';
export type MeshKind = 'humanoid' | 'beast' | 'dragon';

export interface TeamConfig {
  name: string;
  shortName: string;
  color: string;
  colorDark: string;
  colorLight: string;
  country: string;
  keywords: string[];
}

export interface CountryConfig {
  name: string;
  flag: string;
  demonym: string;
}

export interface UnitConfig {
  label: string;
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
  weight: number;
  /** Si es `true`, una entidad puede representar a muchos soldados (ver "stack" en sim/world.ts). */
  stackable: boolean;
  projectile?: 'arrow' | 'orb' | 'fire';
  flyHeight?: number;
  nametag?: boolean;
}

export interface GiftRule {
  troops?: number;
  troopsPerCoin?: number;
  unit?: string;
  count?: number;
  label?: string;
  ultimate?: string;
}

export interface GiftTier extends GiftRule {
  minCoins: number;
}

export interface UltimateDef {
  label: string;
  icon: string;
  description: string;
  weight: number;
  durationSeconds: number;
  meteors?: number;
  damage?: number;
  radius?: number;
  shake?: number;
  troops?: number;
  unit?: string;
  damageMultiplier?: number;
  speedMultiplier?: number;
  damageTakenMultiplier?: number;
  revivePercent?: number;
  maxRevive?: number;
}

export interface GameConfig {
  version: number;
  identity: { title: string; subtitle: string; callToAction: string; watermark: string };
  series: {
    target: number;
    autoStart: boolean;
    countdownSeconds: number;
    roundEndSeconds: number;
    maxRoundSeconds: number;
    suddenDeathAtSeconds: number;
    suddenDeathDamageMultiplier: number;
    rotateCountriesOnRoundEnd: boolean;
    resetSeriesOnComplete: boolean;
  };
  world: {
    season: 'spring' | 'summer' | 'autumn' | 'winter';
    weather: 'clear' | 'rain' | 'snow' | 'fog' | 'storm';
    cycleSeasonEachRound: boolean;
    randomWeatherEachRound: boolean;
  };
  teams: Record<TeamId, TeamConfig>;
  countries: Record<string, CountryConfig>;
  countryRotation: Record<TeamId, string[]>;
  battle: {
    startingTroops: number;
    maxTroopsPerTeam: number;
    renderCapPerTeam: number;
    deployPerSecond: number;
    reinforceFromBehind: boolean;
    difficulty: number;
    damageGlobalMultiplier: number;
    fieldWidth: number;
    fieldDepth: number;
    riverWidth: number;
    riverSlowFactor: number;
    collisionRadius: number;
    separationStrength: number;
    cohesionStrength: number;
    targetRescanTicks: number;
    tickRate: number;
    autoBalance: { enabled: boolean; triggerRatio: number; maxUnderdogDamageBonus: number };
  };
  units: Record<string, UnitConfig>;
  gifts: {
    coinsToTroops: number;
    rageFromCoins: number;
    comboWindowSeconds: number;
    byId: Record<string, GiftRule>;
    byName: Record<string, GiftRule>;
    tiers: GiftTier[];
  };
  likes: {
    enabled: boolean;
    ragePerLike: number;
    rageMax: number;
    autoCastAtFull: boolean;
    castCooldownSeconds: number;
    decayPerSecond: number;
    troopsPerLike: number;
  };
  ultimates: { pool: string[]; defs: Record<string, UltimateDef> };
  chat: {
    joinEnabled: boolean;
    autoAssignUnknown: boolean;
    championCooldownSeconds: number;
    maxChampionsPerTeam: number;
    showNametags: boolean;
    maxNametags: number;
    nametagMaxDistance: number;
    announceJoins: boolean;
    bannedUsers: string[];
    bannedWords: string[];
  };
  camera: {
    mode: 'cinematic' | 'orbit' | 'fixed' | 'follow';
    minCutSeconds: number;
    maxCutSeconds: number;
    heightRange: [number, number];
    distanceRange: [number, number];
    fov: number;
    focusHottestZone: boolean;
    championCloseupChance: number;
    ultimateZoom: boolean;
    shakeEnabled: boolean;
  };
  graphics: {
    quality: 'low' | 'medium' | 'high' | 'ultra';
    pixelRatioCap: number;
    shadows: boolean;
    gore: boolean;
    bloodDecals: boolean;
    bloodTextureSize: number;
    corpseLimit: number;
    particleLimit: number;
    fogDensity: number;
    timeOfDay: 'day' | 'sunset' | 'night';
    targetFps: number;
    postProcessing: boolean;
    bloomThreshold: number;
    bloomIntensity: number;
    vignette: number;
    saturation: number;
    contrast: number;
    exposure: number;
    sharpen: number;
    /** 0 = cielo despejado, 1 = muy nublado. */
    clouds: number;
    /** Densidad de rocas y árboles; 0 los desactiva. */
    propDensity: number;
    /** Distancia (en unidades) a partir de la cual se usa la malla reducida. */
    lodDistance: number;
    /** Distancia hasta la que se calcula el detalle de material (tejido, malla, cuero). */
    textureDistance: number;
    /** Partículas de lluvia/nieve a calidad 'high'. Escala con la calidad. */
    precipitationParticles: number;
  };
  hud: {
    showScoreboard: boolean;
    showSoldierCounters: boolean;
    showRageBars: boolean;
    showKillfeed: boolean;
    showTopDonors: boolean;
    showCallToAction: boolean;
    showRoundTimer: boolean;
    safeAreaTop: number;
    safeAreaBottom: number;
    counterFormat: string;
    scale: number;
  };
  events: {
    websocketUrl: string;
    websocketReconnectMs: number;
    acceptPostMessage: boolean;
    acceptGlobalApi: boolean;
    dedupeWindowMs: number;
    mapping: Record<string, string>;
    typeAliases: Record<string, string[]>;
    rewards: Record<string, { troops?: number; rage?: number; unit?: string }>;
  };
  simulator: {
    enabled: boolean;
    chatsPerMinute: number;
    giftsPerMinute: number;
    likesPerMinute: number;
    followsPerMinute: number;
    bigGiftChance: number;
    seed: number;
  };
  persistence: {
    enabled: boolean;
    apiBase: string;
    localFallback: boolean;
    topDonorsShown: number;
    trackSurvivalRanking: boolean;
  };
  audio: { enabled: boolean; masterVolume: number };
}

/** Config mínima de emergencia: si el JSON no carga, el overlay igual arranca. */
const EMERGENCY_CONFIG = {
  version: 0,
  identity: { title: 'GUERRA DE NACIONES', subtitle: '', callToAction: '', watermark: '' },
  series: {
    target: 10,
    autoStart: true,
    countdownSeconds: 5,
    roundEndSeconds: 8,
    maxRoundSeconds: 420,
    suddenDeathAtSeconds: 300,
    suddenDeathDamageMultiplier: 3,
    rotateCountriesOnRoundEnd: false,
    resetSeriesOnComplete: true,
  },
  world: { season: 'summer', weather: 'clear', cycleSeasonEachRound: true, randomWeatherEachRound: true },
  teams: {
    red: { name: 'Equipo Rojo', shortName: 'ROJO', color: '#ff2f45', colorDark: '#7a0d18', colorLight: '#ff8a96', country: 'MX', keywords: ['rojo', '1'] },
    blue: { name: 'Equipo Azul', shortName: 'AZUL', color: '#2f7bff', colorDark: '#0b2f7a', colorLight: '#8ab6ff', country: 'CR', keywords: ['azul', '2'] },
  },
  countries: { MX: { name: 'México', flag: '🇲🇽', demonym: 'México' }, CR: { name: 'Costa Rica', flag: '🇨🇷', demonym: 'Costa Rica' } },
  countryRotation: { red: ['MX'], blue: ['CR'] },
  battle: {
    startingTroops: 3000,
    maxTroopsPerTeam: 400000,
    renderCapPerTeam: 4200,
    deployPerSecond: 900,
    reinforceFromBehind: true,
    difficulty: 1,
    damageGlobalMultiplier: 1,
    fieldWidth: 260,
    fieldDepth: 200,
    riverWidth: 26,
    riverSlowFactor: 0.55,
    collisionRadius: 0.85,
    separationStrength: 5.5,
    cohesionStrength: 0.6,
    targetRescanTicks: 12,
    tickRate: 30,
    autoBalance: { enabled: true, triggerRatio: 2.5, maxUnderdogDamageBonus: 0.6 },
  },
  units: {
    soldier: { label: 'Soldado', mesh: 'humanoid', hp: 100, damage: 13, range: 1.8, attackCooldown: 0.85, speed: 3.4, armor: 0, scale: 1, splash: 0, flying: false, weight: 1, stackable: true },
  },
  gifts: { coinsToTroops: 30, rageFromCoins: 0.25, comboWindowSeconds: 4, byId: {}, byName: {}, tiers: [{ minCoins: 0, troopsPerCoin: 30, unit: 'soldier' }] },
  likes: { enabled: true, ragePerLike: 0.4, rageMax: 100, autoCastAtFull: true, castCooldownSeconds: 20, decayPerSecond: 0, troopsPerLike: 2 },
  ultimates: { pool: [], defs: {} },
  chat: {
    joinEnabled: true,
    autoAssignUnknown: true,
    championCooldownSeconds: 45,
    maxChampionsPerTeam: 80,
    showNametags: true,
    maxNametags: 36,
    nametagMaxDistance: 90,
    announceJoins: true,
    bannedUsers: [],
    bannedWords: [],
  },
  camera: {
    mode: 'cinematic',
    minCutSeconds: 6,
    maxCutSeconds: 13,
    heightRange: [18, 62],
    distanceRange: [55, 130],
    fov: 52,
    focusHottestZone: true,
    championCloseupChance: 0.22,
    ultimateZoom: true,
    shakeEnabled: true,
  },
  graphics: {
    quality: 'high',
    pixelRatioCap: 1.75,
    shadows: true,
    gore: true,
    bloodDecals: true,
    bloodTextureSize: 512,
    corpseLimit: 6000,
    particleLimit: 4000,
    fogDensity: 0.0022,
    timeOfDay: 'day',
    targetFps: 60,
    postProcessing: true,
    bloomThreshold: 1.15,
    bloomIntensity: 0.6,
    vignette: 0.5,
    saturation: 1.14,
    contrast: 1.05,
    exposure: 1.05,
    sharpen: 0.22,
    clouds: 0.85,
    propDensity: 1,
    lodDistance: 70,
    textureDistance: 45,
    precipitationParticles: 9000,
  },
  hud: {
    showScoreboard: true,
    showSoldierCounters: true,
    showRageBars: true,
    showKillfeed: true,
    showTopDonors: true,
    showCallToAction: true,
    showRoundTimer: true,
    safeAreaTop: 0,
    safeAreaBottom: 0,
    counterFormat: 'es-MX',
    scale: 1,
  },
  events: {
    websocketUrl: 'auto',
    websocketReconnectMs: 2000,
    acceptPostMessage: true,
    acceptGlobalApi: true,
    dedupeWindowMs: 1500,
    mapping: {},
    typeAliases: {},
    rewards: {},
  },
  simulator: { enabled: false, chatsPerMinute: 90, giftsPerMinute: 45, likesPerMinute: 700, followsPerMinute: 8, bigGiftChance: 0.06, seed: 1337 },
  persistence: { enabled: true, apiBase: 'auto', localFallback: true, topDonorsShown: 5, trackSurvivalRanking: true },
  audio: { enabled: false, masterVolume: 0.5 },
} as unknown as GameConfig;

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

/** Merge profundo: `patch` gana, los arrays se reemplazan enteros. */
export function deepMerge<T>(base: T, patch: unknown): T {
  if (!isPlainObject(patch)) return (patch === undefined ? base : (patch as T));
  const out: Record<string, unknown> = isPlainObject(base) ? { ...(base as Record<string, unknown>) } : {};
  for (const [key, value] of Object.entries(patch)) {
    out[key] = isPlainObject(value) ? deepMerge(out[key], value) : value;
  }
  return out as T;
}

/** Convierte "1.5" → 1.5, "true" → true, "a,b" → ['a','b']. */
function coerce(raw: string): unknown {
  if (raw === 'true') return true;
  if (raw === 'false') return false;
  if (raw !== '' && !Number.isNaN(Number(raw))) return Number(raw);
  if (raw.includes(',')) return raw.split(',').map((s) => s.trim());
  return raw;
}

/** Aplica `?ruta.con.puntos=valor` de la URL sobre la config. */
export function applyUrlOverrides<T extends object>(config: T, search: string): T {
  const params = new URLSearchParams(search);
  const patch: Record<string, unknown> = {};
  for (const [key, raw] of params) {
    if (!key.includes('.')) continue;
    const path = key.split('.');
    let cursor = patch;
    for (let i = 0; i < path.length - 1; i++) {
      cursor[path[i]] = isPlainObject(cursor[path[i]]) ? cursor[path[i]] : {};
      cursor = cursor[path[i]] as Record<string, unknown>;
    }
    cursor[path[path.length - 1]] = coerce(raw);
  }
  return deepMerge(config, patch);
}

export const CONFIG_URL = 'config/game.config.json';

/** Descarga la config del servidor y la fusiona con la de emergencia. */
export async function loadConfig(url = CONFIG_URL): Promise<GameConfig> {
  let fetched: unknown = {};
  try {
    const res = await fetch(`${url}?t=${Date.now()}`, { cache: 'no-store' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    fetched = await res.json();
  } catch (err) {
    console.warn('[config] No se pudo cargar game.config.json, usando valores de emergencia.', err);
  }
  const merged = deepMerge(EMERGENCY_CONFIG, fetched);
  return applyUrlOverrides(merged, location.search);
}

/** Resuelve la URL base de la API/WebSocket cuando la config dice "auto". */
export function resolveEndpoints(config: GameConfig): { apiBase: string; wsUrl: string } {
  const params = new URLSearchParams(location.search);
  const host = params.get('server') ?? (location.port === '5173' ? `${location.hostname}:8787` : location.host);
  const httpProto = location.protocol === 'https:' ? 'https:' : 'http:';
  const wsProto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const apiBase = config.persistence.apiBase === 'auto' ? `${httpProto}//${host}/api` : config.persistence.apiBase;
  const wsUrl = config.events.websocketUrl === 'auto' ? `${wsProto}//${host}/events` : config.events.websocketUrl;
  return { apiBase, wsUrl };
}
