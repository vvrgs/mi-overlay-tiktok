/**
 * Generador de eventos falsos.
 *
 * Sirve para desarrollar, grabar clips y probar el balance sin estar en vivo.
 * Produce comentarios, regalos (con combos reales), likes y follows a las tasas
 * configuradas en `simulator` dentro de game.config.json.
 */

import type { GameConfig } from '../shared/config';
import { createRng, type Rng } from '../shared/math';
import type { EventBus } from './bus';

const NICKNAMES = [
  'creatura13', 'tapia04', 'Marycita', 'Alecito', 'Crazu', 'bluelok', 'DEMIAN', 'AXEL',
  'Yerferson', 'ElJefe', 'Sofi', 'Karlita', 'donpepe', 'Nico', 'lavaquita', 'Rambo_77',
  'ximena.gg', 'ChinoLoco', 'MaruMaru', 'zZeus', 'PatoRey', 'lucha_libre', 'Fer', 'Mich',
  'brayan__', 'Tavo', 'Ana', 'ElPrimo', 'catrina', 'muñeco', 'JoseK', 'Vale', 'Tito',
];

const COMMENTS_RED = ['rojo', 'ROJO 🔴', 'vamos rojo', 'rojo siempre', 'equipo rojo', '1', 'méxico', 'rojo gana'];
const COMMENTS_BLUE = ['azul', 'AZUL 🔵', 'vamos azul', 'azul gana', 'equipo azul', '2', 'costa rica', 'azules'];
const COMMENTS_NEUTRAL = [
  'que brutal 😱', 'siii', 'jajaja', 'esto está épico', 'no manches', 'primera vez aquí',
  'saludos desde Lima', 'suban tropas', 'wow', 'quiero un dragón', 'dale like',
];

const SMALL_GIFTS: Array<[string, number]> = [
  ['Rosa', 1], ['TikTok', 1], ['GG', 1], ['Me gusta', 1], ['Dedo arriba', 5], ['Corazón', 10],
];
const MID_GIFTS: Array<[string, number]> = [
  ['Osito Mishka', 10], ['Guantes', 10], ['Perfume', 20], ['Sombrero y bigote', 10],
];
const BIG_GIFTS: Array<[string, number]> = [
  ['Corona', 199], ['Cohete', 1000], ['Ballena', 1500], ['Yate', 2988], ['León', 29999], ['Universo', 34999],
];

interface Ticker {
  perMinute: number;
  accumulator: number;
}

export class EventSimulator {
  private rng: Rng;
  private timer: ReturnType<typeof setInterval> | null = null;
  private lastTime = 0;
  private tickers: Record<'chat' | 'gift' | 'like' | 'follow', Ticker>;
  private users: Array<{ uniqueId: string; nickname: string; userId: string }>;
  running = false;

  constructor(private bus: EventBus, private config: GameConfig) {
    this.rng = createRng(config.simulator.seed || 1337);
    this.users = NICKNAMES.map((name, i) => ({
      uniqueId: name.toLowerCase().replace(/[^a-z0-9_.]/g, ''),
      nickname: name,
      userId: `sim-${i}`,
    }));
    this.tickers = {
      chat: { perMinute: config.simulator.chatsPerMinute, accumulator: 0 },
      gift: { perMinute: config.simulator.giftsPerMinute, accumulator: 0 },
      like: { perMinute: config.simulator.likesPerMinute, accumulator: 0 },
      follow: { perMinute: config.simulator.followsPerMinute, accumulator: 0 },
    };
  }

  updateConfig(config: GameConfig): void {
    this.config = config;
    this.tickers.chat.perMinute = config.simulator.chatsPerMinute;
    this.tickers.gift.perMinute = config.simulator.giftsPerMinute;
    this.tickers.like.perMinute = config.simulator.likesPerMinute;
    this.tickers.follow.perMinute = config.simulator.followsPerMinute;
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    this.lastTime = performance.now();
    this.timer = setInterval(() => this.tick(), 100);
  }

  stop(): void {
    this.running = false;
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  toggle(enabled: boolean): void {
    if (enabled) this.start();
    else this.stop();
  }

  private tick(): void {
    const now = performance.now();
    const dt = Math.min(0.5, (now - this.lastTime) / 1000);
    this.lastTime = now;

    this.advance(this.tickers.chat, dt, () => this.emitChat());
    this.advance(this.tickers.gift, dt, () => this.emitGift());
    this.advance(this.tickers.like, dt, () => this.emitLike());
    this.advance(this.tickers.follow, dt, () => this.emitFollow());
  }

  private advance(ticker: Ticker, dt: number, emit: () => void): void {
    ticker.accumulator += (ticker.perMinute / 60) * dt;
    let guard = 0;
    while (ticker.accumulator >= 1 && guard++ < 200) {
      ticker.accumulator -= 1;
      emit();
    }
  }

  private user() {
    return this.rng.pick(this.users);
  }

  private emitChat(): void {
    const roll = this.rng();
    const comment =
      roll < 0.35 ? this.rng.pick(COMMENTS_RED) : roll < 0.7 ? this.rng.pick(COMMENTS_BLUE) : this.rng.pick(COMMENTS_NEUTRAL);
    this.bus.ingest({ type: 'chat', user: this.user(), comment });
  }

  private emitLike(): void {
    this.bus.ingest({ type: 'like', user: this.user(), likeCount: this.rng.int(1, 15) });
  }

  private emitFollow(): void {
    this.bus.ingest({ type: this.rng() < 0.6 ? 'follow' : 'share', user: this.user() });
  }

  private emitGift(): void {
    const roll = this.rng();
    const big = roll < this.config.simulator.bigGiftChance;
    const mid = !big && roll < 0.32;
    const [giftName, diamondCount] = big ? this.rng.pick(BIG_GIFTS) : mid ? this.rng.pick(MID_GIFTS) : this.rng.pick(SMALL_GIFTS);
    const repeatCount = big ? 1 : this.rng.int(1, mid ? 11 : 30);
    const user = this.user();

    // Regalos baratos llegan en combo: se emiten parciales y luego el cierre,
    // igual que TikTok, para ejercitar la lógica de combos del bus.
    if (!big && repeatCount > 3) {
      let sent = 0;
      const step = Math.max(1, Math.floor(repeatCount / 3));
      const pushPartial = () => {
        sent = Math.min(repeatCount, sent + step);
        if (sent < repeatCount) {
          this.bus.ingest({ type: 'gift', user, giftName, diamondCount, repeatCount: sent, repeatEnd: false });
          setTimeout(pushPartial, 260);
        } else {
          this.bus.ingest({ type: 'gift', user, giftName, diamondCount, repeatCount, repeatEnd: true });
        }
      };
      pushPartial();
      return;
    }

    this.bus.ingest({ type: 'gift', user, giftName, diamondCount, repeatCount, repeatEnd: true });
  }
}
