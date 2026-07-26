/**
 * Resolución de regalos → recompensa en el juego.
 *
 * Orden de búsqueda: por `giftId`, luego por nombre normalizado y, si no está
 * en el catálogo, por tramo de monedas. Ese último paso es el importante:
 * garantiza que un regalo nuevo de TikTok —o uno que no listaste— siga dando
 * tropas en vez de no hacer nada en directo.
 */

import type { GameConfig, GiftRule } from '../shared/config';
import { normalizeText } from '../shared/math';
import type { GiftEvent } from '../events/types';

export interface GiftReward {
  /** Tropas totales (ya multiplicadas por el combo). */
  troops: number;
  /** Unidad que se genera. */
  unit: string;
  /** Cuántas unidades élite se generan (0 para tropa normal). */
  count: number;
  /** Furia que suma al equipo. */
  rage: number;
  /** Ultimate que dispara el regalo, si aplica. */
  ultimate?: string;
  /** Nombre bonito para el killfeed. */
  label: string;
  /** Monedas totales gastadas. */
  coins: number;
}

export class GiftResolver {
  private byName = new Map<string, GiftRule>();

  constructor(private config: GameConfig) {
    this.rebuild(config);
  }

  rebuild(config: GameConfig): void {
    this.config = config;
    this.byName.clear();
    for (const [name, rule] of Object.entries(config.gifts.byName ?? {})) {
      this.byName.set(normalizeText(name), rule);
    }
  }

  /** Encuentra el tramo de monedas más alto que el regalo alcanza. */
  private tierFor(coins: number): GiftRule {
    const tiers = this.config.gifts.tiers ?? [];
    let match: GiftRule = { troopsPerCoin: this.config.gifts.coinsToTroops, unit: 'soldier' };
    for (const tier of tiers) {
      if (coins >= tier.minCoins) match = tier;
    }
    return match;
  }

  resolve(event: GiftEvent): GiftReward {
    const repeat = Math.max(1, event.repeatCount);
    const unitCoins = Math.max(0, event.diamondCount);
    const coins = unitCoins * repeat;

    const rule =
      (event.giftId ? this.config.gifts.byId?.[event.giftId] : undefined) ??
      this.byName.get(normalizeText(event.giftName)) ??
      this.tierFor(unitCoins);

    // `troops` es un valor fijo por unidad de regalo; `troopsPerCoin` escala con el precio.
    const perGift =
      rule.troops !== undefined
        ? rule.troops
        : Math.round(unitCoins * (rule.troopsPerCoin ?? this.config.gifts.coinsToTroops));

    // Un regalo sin precio conocido no puede quedarse en cero: siempre suma algo.
    const troops = Math.max(perGift * repeat, repeat);
    const unit = rule.unit ?? 'soldier';
    const eliteCount = rule.count ? rule.count * repeat : 0;
    const rage = coins * this.config.gifts.rageFromCoins;

    const reward: GiftReward = {
      troops,
      unit,
      count: eliteCount,
      rage,
      label: rule.label ?? event.giftName,
      coins,
    };
    if (rule.ultimate) reward.ultimate = rule.ultimate;
    return reward;
  }
}
