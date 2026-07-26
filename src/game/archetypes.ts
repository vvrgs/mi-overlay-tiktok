/**
 * Traducción de las unidades de la config al formato plano del worker.
 *
 * Vive en su propio módulo porque lo necesitan dos sitios que no deben
 * conocerse entre sí: el juego (para configurar la simulación) y el renderer
 * (para saber qué silueta toca a cada arquetipo). El ORDEN del array es el
 * contrato: el snapshot agrupa las unidades por índice de arquetipo.
 */

import type { GameConfig } from '../shared/config';
import type { UnitArchetypeWire } from '../sim/protocol';

const PROJECTILE_KIND: Record<string, number> = { arrow: 1, orb: 2, fire: 3 };

export function buildArchetypes(config: GameConfig): UnitArchetypeWire[] {
  return Object.entries(config.units).map(([key, unit]) => ({
    key,
    mesh: unit.mesh,
    hp: unit.hp,
    damage: unit.damage,
    range: unit.range,
    attackCooldown: unit.attackCooldown,
    speed: unit.speed,
    armor: unit.armor,
    scale: unit.scale,
    splash: unit.splash,
    flying: Boolean(unit.flying),
    flyHeight: unit.flyHeight ?? 0,
    projectile: unit.projectile ? PROJECTILE_KIND[unit.projectile] ?? 0 : 0,
    stackable: unit.stackable !== false,
  }));
}
