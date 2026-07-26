/**
 * Traductor de payloads externos → eventos canónicos del juego.
 *
 * Acepta prácticamente cualquier forma de JSON. Las rutas del mapa de campos
 * admiten notación con puntos y alternativas separadas por `|`; se toma la
 * primera ruta que exista. Si tu plataforma cambia nombres, ajustas
 * `events.mapping` en la config y listo — no se toca código.
 */

import type { GameConfig } from '../shared/config';
import { normalizeText } from '../shared/math';
import type { LiveEvent, LiveEventType, LiveUser } from './types';

const DEFAULT_MAPPING: Record<string, string> = {
  type: 'type|event|eventType|name',
  userId: 'user.userId|user.id|userId|uniqueId|user.uniqueId',
  uniqueId: 'user.uniqueId|uniqueId|user.username|username|user.handle',
  nickname: 'user.nickname|nickname|user.displayName|displayName|user.name',
  avatarUrl: 'user.avatarUrl|user.profilePictureUrl|profilePictureUrl|user.avatar|avatarUrl',
  comment: 'comment|message|text|content',
  giftId: 'giftId|gift.id|gift.giftId|id',
  giftName: 'giftName|gift.name|gift.giftName|name',
  repeatCount: 'repeatCount|gift.repeatCount|count|amount|comboCount',
  repeatEnd: 'repeatEnd|gift.repeatEnd|isFinal|comboEnd',
  diamondCount: 'diamondCount|gift.diamondCount|coins|gift.coins|cost',
  likeCount: 'likeCount|likes|count|amount',
  teamHint: 'team|side|color',
};

const DEFAULT_ALIASES: Record<LiveEventType, string[]> = {
  gift: ['gift', 'gifts', 'ongift', 'sendgift', 'regalo'],
  chat: ['chat', 'comment', 'message', 'onchat', 'comentario'],
  like: ['like', 'likes', 'onlike', 'megusta'],
  follow: ['follow', 'onfollow', 'seguir'],
  share: ['share', 'onshare', 'compartir'],
  join: ['join', 'member', 'onmember', 'enter', 'unido'],
  subscribe: ['subscribe', 'sub', 'onsubscribe', 'suscripcion'],
};

/** Lee `a.b.c` dentro de un objeto arbitrario. */
function readPath(source: unknown, path: string): unknown {
  let cursor: unknown = source;
  for (const key of path.split('.')) {
    if (cursor === null || typeof cursor !== 'object') return undefined;
    cursor = (cursor as Record<string, unknown>)[key];
    if (cursor === undefined) return undefined;
  }
  return cursor;
}

/** Prueba varias rutas separadas por `|` y devuelve la primera con valor. */
function readAny(source: unknown, spec: string | undefined): unknown {
  if (!spec) return undefined;
  for (const path of spec.split('|')) {
    const value = readPath(source, path.trim());
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return undefined;
}

function toNumber(value: unknown, fallback: number): number {
  const n = typeof value === 'string' ? Number(value) : typeof value === 'number' ? value : NaN;
  return Number.isFinite(n) ? n : fallback;
}

function toBoolean(value: unknown): boolean | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') {
    const v = value.toLowerCase();
    if (v === 'true' || v === '1' || v === 'yes') return true;
    if (v === 'false' || v === '0' || v === 'no') return false;
  }
  return undefined;
}

function toText(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : value === undefined || value === null ? fallback : String(value);
}

export class EventNormalizer {
  private mapping: Record<string, string>;
  private aliasLookup = new Map<string, LiveEventType>();
  private anonCounter = 0;

  constructor(config: GameConfig) {
    this.mapping = { ...DEFAULT_MAPPING, ...(config.events.mapping ?? {}) };
    const aliases: Record<string, string[]> = { ...DEFAULT_ALIASES, ...(config.events.typeAliases ?? {}) };
    for (const [canonical, list] of Object.entries(aliases)) {
      if (!(canonical in DEFAULT_ALIASES)) continue;
      for (const alias of list) this.aliasLookup.set(normalizeText(alias).replace(/\s/g, ''), canonical as LiveEventType);
      this.aliasLookup.set(normalizeText(canonical).replace(/\s/g, ''), canonical as LiveEventType);
    }
  }

  /** Actualiza el mapeo sin recrear la instancia (recarga de config en caliente). */
  update(config: GameConfig): void {
    this.mapping = { ...DEFAULT_MAPPING, ...(config.events.mapping ?? {}) };
  }

  private resolveType(payload: unknown): LiveEventType | null {
    const raw = readAny(payload, this.mapping.type);
    if (raw === undefined) {
      // Sin campo de tipo: se infiere por la forma del payload.
      if (readAny(payload, this.mapping.giftName) !== undefined || readAny(payload, this.mapping.giftId) !== undefined) return 'gift';
      if (readAny(payload, this.mapping.comment) !== undefined) return 'chat';
      if (readAny(payload, this.mapping.likeCount) !== undefined) return 'like';
      return null;
    }
    return this.aliasLookup.get(normalizeText(toText(raw)).replace(/\s/g, '')) ?? null;
  }

  private resolveUser(payload: unknown): LiveUser {
    const uniqueId = toText(readAny(payload, this.mapping.uniqueId));
    const nickname = toText(readAny(payload, this.mapping.nickname));
    const userId = toText(readAny(payload, this.mapping.userId));
    const avatarUrl = readAny(payload, this.mapping.avatarUrl);
    const handle = uniqueId || nickname || userId || `anon${++this.anonCounter}`;
    const user: LiveUser = {
      userId: userId || handle,
      uniqueId: handle,
      nickname: nickname || handle,
    };
    if (typeof avatarUrl === 'string') user.avatarUrl = avatarUrl;
    return user;
  }

  /** Traduce un payload externo. Devuelve `null` si no es un evento reconocible. */
  normalize(payload: unknown): LiveEvent | null {
    if (typeof payload !== 'object' || payload === null) return null;
    const type = this.resolveType(payload);
    if (!type) return null;

    const user = this.resolveUser(payload);
    const ts = toNumber(readAny(payload, 'ts|timestamp|createTime|time'), Date.now());
    const teamHintRaw = readAny(payload, this.mapping.teamHint);
    const teamHint = teamHintRaw === undefined ? undefined : toText(teamHintRaw);

    switch (type) {
      case 'gift': {
        const giftIdRaw = readAny(payload, this.mapping.giftId);
        const event: LiveEvent = {
          type: 'gift',
          user,
          ts,
          raw: payload,
          giftName: toText(readAny(payload, this.mapping.giftName), 'Regalo'),
          repeatCount: Math.max(1, Math.round(toNumber(readAny(payload, this.mapping.repeatCount), 1))),
          diamondCount: Math.max(0, toNumber(readAny(payload, this.mapping.diamondCount), 0)),
        };
        if (giftIdRaw !== undefined) event.giftId = toText(giftIdRaw);
        const repeatEnd = toBoolean(readAny(payload, this.mapping.repeatEnd));
        if (repeatEnd !== undefined) event.repeatEnd = repeatEnd;
        if (teamHint) event.teamHint = teamHint;
        return event;
      }
      case 'chat': {
        const comment = toText(readAny(payload, this.mapping.comment));
        if (!comment) return null;
        return { type: 'chat', user, ts, raw: payload, comment, ...(teamHint ? { teamHint } : {}) };
      }
      case 'like': {
        return {
          type: 'like',
          user,
          ts,
          raw: payload,
          likeCount: Math.max(1, Math.round(toNumber(readAny(payload, this.mapping.likeCount), 1))),
          ...(teamHint ? { teamHint } : {}),
        };
      }
      default:
        return { type, user, ts, raw: payload, ...(teamHint ? { teamHint } : {}) };
    }
  }
}
