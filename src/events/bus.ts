/**
 * Bus central de eventos.
 *
 * Todo lo que entra al juego pasa por aquí: normaliza, filtra baneados, resuelve
 * combos de regalos y reparte a los suscriptores. Es la única puerta de entrada,
 * así que el resto del juego nunca ve payloads crudos.
 */

import type { GameConfig } from '../shared/config';
import { normalizeText } from '../shared/math';
import { EventNormalizer } from './normalize';
import type { ControlCommand, GiftEvent, LiveEvent, LiveEventType } from './types';
import { isControlCommand } from './types';

type EventListener = (event: LiveEvent) => void;
type CommandListener = (command: ControlCommand) => void;

interface PendingCombo {
  event: GiftEvent;
  awardedCount: number;
  lastSeen: number;
  timer: ReturnType<typeof setTimeout>;
}

export class EventBus {
  private normalizer: EventNormalizer;
  private listeners = new Map<LiveEventType | '*', Set<EventListener>>();
  private commandListeners = new Set<CommandListener>();
  private pendingCombos = new Map<string, PendingCombo>();
  private banned = new Set<string>();
  private bannedWords: string[] = [];
  private comboWindowMs: number;

  /** Contadores para diagnóstico (se muestran en el panel de control). */
  readonly stats = { received: 0, accepted: 0, rejected: 0, gifts: 0, chats: 0, likes: 0, coins: 0 };

  constructor(config: GameConfig) {
    this.normalizer = new EventNormalizer(config);
    this.comboWindowMs = Math.max(500, config.gifts.comboWindowSeconds * 1000);
    this.applyModeration(config);
  }

  updateConfig(config: GameConfig): void {
    this.normalizer.update(config);
    this.comboWindowMs = Math.max(500, config.gifts.comboWindowSeconds * 1000);
    this.applyModeration(config);
  }

  private applyModeration(config: GameConfig): void {
    this.banned = new Set((config.chat.bannedUsers ?? []).map((u) => normalizeText(u)));
    this.bannedWords = (config.chat.bannedWords ?? []).map((w) => normalizeText(w)).filter(Boolean);
  }

  ban(uniqueId: string): void {
    this.banned.add(normalizeText(uniqueId));
  }

  unban(uniqueId: string): void {
    this.banned.delete(normalizeText(uniqueId));
  }

  isBanned(uniqueId: string): boolean {
    return this.banned.has(normalizeText(uniqueId));
  }

  on(type: LiveEventType | '*', listener: EventListener): () => void {
    let set = this.listeners.get(type);
    if (!set) {
      set = new Set();
      this.listeners.set(type, set);
    }
    set.add(listener);
    return () => set!.delete(listener);
  }

  onCommand(listener: CommandListener): () => void {
    this.commandListeners.add(listener);
    return () => this.commandListeners.delete(listener);
  }

  /** Punto de entrada público: acepta payloads crudos de cualquier transporte. */
  ingest(payload: unknown): void {
    this.stats.received++;

    if (isControlCommand(payload)) {
      for (const listener of this.commandListeners) listener(payload);
      return;
    }

    // Algunas plataformas mandan lotes: [{...}, {...}]
    if (Array.isArray(payload)) {
      for (const item of payload) this.ingest(item);
      return;
    }

    const event = this.normalizer.normalize(payload);
    if (!event) {
      this.stats.rejected++;
      return;
    }

    if (this.isBanned(event.user.uniqueId)) {
      this.stats.rejected++;
      return;
    }

    if (event.type === 'chat' && this.bannedWords.length > 0) {
      const text = normalizeText(event.comment);
      if (this.bannedWords.some((word) => text.includes(word))) {
        this.stats.rejected++;
        return;
      }
    }

    if (event.type === 'gift') {
      this.handleGift(event);
      return;
    }

    this.dispatch(event);
  }

  /**
   * Los combos de TikTok llegan como una ráfaga de eventos con `repeatEnd:false` y
   * un evento final con `repeatEnd:true`. Contabilizar cada uno multiplicaría las
   * tropas, así que se acumula y se paga al cerrar el combo. Si la plataforma nunca
   * manda el cierre, un temporizador paga igual para no perder el regalo.
   */
  private handleGift(event: GiftEvent): void {
    // La plataforma ya agrega los combos: se paga al instante.
    if (event.repeatEnd === undefined) {
      this.dispatch(event);
      return;
    }

    const key = `${event.user.userId}:${event.giftId ?? event.giftName}`;

    if (event.repeatEnd === true) {
      const pending = this.pendingCombos.get(key);
      if (pending) {
        clearTimeout(pending.timer);
        this.pendingCombos.delete(key);
        const remaining = Math.max(0, event.repeatCount - pending.awardedCount);
        if (remaining === 0) return;
        this.dispatch({ ...event, repeatCount: remaining });
        return;
      }
      this.dispatch(event);
      return;
    }

    // Combo en curso: se guarda el progreso y se arma el pago diferido.
    const existing = this.pendingCombos.get(key);
    if (existing) clearTimeout(existing.timer);
    const awardedCount = existing?.awardedCount ?? 0;
    const timer = setTimeout(() => this.flushCombo(key), this.comboWindowMs);
    this.pendingCombos.set(key, { event, awardedCount, lastSeen: Date.now(), timer });
  }

  private flushCombo(key: string): void {
    const pending = this.pendingCombos.get(key);
    if (!pending) return;
    this.pendingCombos.delete(key);
    const remaining = Math.max(0, pending.event.repeatCount - pending.awardedCount);
    if (remaining <= 0) return;
    this.dispatch({ ...pending.event, repeatCount: remaining, repeatEnd: true });
  }

  /** Paga inmediatamente todos los combos pendientes (fin de ronda, reset...). */
  flushAll(): void {
    for (const key of [...this.pendingCombos.keys()]) {
      const pending = this.pendingCombos.get(key);
      if (pending) clearTimeout(pending.timer);
      this.flushCombo(key);
    }
  }

  private dispatch(event: LiveEvent): void {
    this.stats.accepted++;
    if (event.type === 'gift') {
      this.stats.gifts++;
      this.stats.coins += event.diamondCount * event.repeatCount;
    } else if (event.type === 'chat') this.stats.chats++;
    else if (event.type === 'like') this.stats.likes += event.likeCount;

    for (const listener of this.listeners.get(event.type) ?? []) listener(event);
    for (const listener of this.listeners.get('*') ?? []) listener(event);
  }

  dispose(): void {
    for (const pending of this.pendingCombos.values()) clearTimeout(pending.timer);
    this.pendingCombos.clear();
    this.listeners.clear();
    this.commandListeners.clear();
  }
}
