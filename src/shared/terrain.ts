/**
 * Terreno procedural del campo de batalla.
 *
 * Este módulo es la ÚNICA fuente de verdad de la geometría del terreno: lo usan
 * tanto el worker de simulación (para pegar las unidades al suelo y frenarlas en
 * el río) como el renderer (para construir la malla). Al ser determinista y
 * puro, ambos hilos coinciden exactamente sin transferir el heightmap.
 */

import { clamp, fbm, smoothstep } from './math';

export interface TerrainParams {
  seed: number;
  /** Ancho del campo en el eje X (rojo en -X, azul en +X). */
  width: number;
  /** Profundidad del campo en el eje Z. */
  depth: number;
  /** Ancho del río que cruza el centro. */
  riverWidth: number;
}

export interface Terrain extends TerrainParams {
  /** Altura del suelo en (x, z). */
  height(x: number, z: number): number;
  /** Centro del río (posición X) a una profundidad Z dada — el río serpentea. */
  riverCenter(z: number): number;
  /** 0 = tierra firme, 1 = centro del río. */
  riverFactor(x: number, z: number): number;
  /** Multiplicador de velocidad por terreno (agua frena). */
  speedFactor(x: number, z: number, riverSlow: number): number;
  /** Mantiene una posición dentro de los límites jugables. */
  clampToField(out: { x: number; z: number }): void;
  waterLevel: number;
  halfWidth: number;
  halfDepth: number;
  /** Amplitud y frecuencia del serpenteo, para que el shader del agua lo replique. */
  meanderAmp: number;
  meanderFreq: number;
}

const RIVER_DEPTH = 4.2;
const HILL_AMPLITUDE = 13;

export function createTerrain(params: TerrainParams): Terrain {
  const { seed, width, depth, riverWidth } = params;
  const halfWidth = width / 2;
  const halfDepth = depth / 2;
  // Desplazamientos derivados de la semilla para que cada partida tenga relieve distinto.
  const ox = (seed % 997) * 0.137;
  const oz = (seed % 743) * 0.219;
  const meanderAmp = 12 + (seed % 11);
  const meanderFreq = 0.011 + ((seed % 7) * 0.0013);

  function riverCenter(z: number): number {
    return Math.sin(z * meanderFreq) * meanderAmp + Math.sin(z * meanderFreq * 2.7) * (meanderAmp * 0.3);
  }

  function riverFactor(x: number, z: number): number {
    const d = Math.abs(x - riverCenter(z));
    return 1 - smoothstep(riverWidth * 0.35, riverWidth * 0.85, d);
  }

  function height(x: number, z: number): number {
    const river = riverFactor(x, z);
    // Las colinas nunca bajan de cero: así el único punto del mapa por debajo del
    // nivel del agua es el cauce del río. Si el relieve pudiera ser negativo, el
    // plano de agua asomaría por medio campo y parecería una inundación.
    const hills = fbm(x * 0.006 + ox, z * 0.006 + oz, 4) * HILL_AMPLITUDE;
    // El terreno se aplana al acercarse al río para que el cauce quede marcado.
    const base = hills * (1 - river);
    // Detalle fino para que el suelo no se vea plano de cerca.
    const detail = (fbm(x * 0.045 + ox, z * 0.045 + oz, 2) - 0.5) * 1.4 * (1 - river);
    // Los bordes del mapa suben formando un anfiteatro natural que encierra la acción.
    const edgeX = smoothstep(halfWidth * 0.72, halfWidth * 1.05, Math.abs(x));
    const edgeZ = smoothstep(halfDepth * 0.72, halfDepth * 1.05, Math.abs(z));
    const rim = Math.max(edgeX, edgeZ) * 26 * (1 - river * 0.85);
    return base + detail + rim - river * RIVER_DEPTH;
  }

  function speedFactor(x: number, z: number, riverSlow: number): number {
    const r = riverFactor(x, z);
    return 1 - r * (1 - riverSlow);
  }

  function clampToField(out: { x: number; z: number }): void {
    out.x = clamp(out.x, -halfWidth * 0.98, halfWidth * 0.98);
    out.z = clamp(out.z, -halfDepth * 0.98, halfDepth * 0.98);
  }

  return {
    ...params,
    height,
    riverCenter,
    riverFactor,
    speedFactor,
    clampToField,
    waterLevel: -RIVER_DEPTH * 0.55,
    halfWidth,
    halfDepth,
    meanderAmp,
    meanderFreq,
  };
}
