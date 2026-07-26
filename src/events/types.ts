/**
 * Contrato canónico de eventos del juego.
 *
 * Tu plataforma (prizvo.live) NO necesita emitir exactamente esta forma: el
 * normalizador (`normalize.ts`) traduce payloads arbitrarios usando el mapa de
 * campos de `events.mapping` en la config. Este archivo define lo que el juego
 * consume internamente una vez traducido.
 */

export type LiveEventType = 'gift' | 'chat' | 'like' | 'follow' | 'share' | 'join' | 'subscribe';

export interface LiveUser {
  /** Identificador estable. Si tu plataforma no lo manda, se deriva de uniqueId. */
  userId: string;
  /** Handle sin @ (ej. "yosbinsito"). */
  uniqueId: string;
  /** Nombre visible. */
  nickname: string;
  /** URL del avatar (opcional; se usa en killfeed y nametags). */
  avatarUrl?: string;
}

interface BaseEvent {
  user: LiveUser;
  /** Timestamp en ms. Se rellena solo si no viene. */
  ts: number;
  /** Payload original, por si necesitas depurar. */
  raw?: unknown;
}

export interface GiftEvent extends BaseEvent {
  type: 'gift';
  giftId?: string;
  giftName: string;
  /** Cantidad del combo. */
  repeatCount: number;
  /**
   * `true` = el combo terminó y ya se puede contabilizar.
   * `undefined` = la plataforma ya agrega los combos; se contabiliza al instante.
   */
  repeatEnd?: boolean;
  /** Costo unitario en monedas/diamantes. */
  diamondCount: number;
  /** Bando explícito, si tu plataforma ya lo decide. */
  teamHint?: string;
}

export interface ChatEvent extends BaseEvent {
  type: 'chat';
  comment: string;
  teamHint?: string;
}

export interface LikeEvent extends BaseEvent {
  type: 'like';
  likeCount: number;
  teamHint?: string;
}

export interface SimpleEvent extends BaseEvent {
  type: 'follow' | 'share' | 'join' | 'subscribe';
  teamHint?: string;
}

export type LiveEvent = GiftEvent | ChatEvent | LikeEvent | SimpleEvent;

/** Comandos que el panel de control envía al overlay (por WebSocket o postMessage). */
export type ControlCommand =
  | { type: 'control'; action: 'start' }
  | { type: 'control'; action: 'pause' }
  | { type: 'control'; action: 'resume' }
  | { type: 'control'; action: 'reset' }
  | { type: 'control'; action: 'skipRound' }
  | { type: 'control'; action: 'resetSeries' }
  | { type: 'control'; action: 'reloadConfig' }
  | { type: 'control'; action: 'setCountry'; team: 'red' | 'blue'; country: string }
  | { type: 'control'; action: 'setDifficulty'; value: number }
  | { type: 'control'; action: 'addTroops'; team: 'red' | 'blue'; troops: number; unit?: string }
  | { type: 'control'; action: 'castUltimate'; team: 'red' | 'blue'; ultimate: string }
  | { type: 'control'; action: 'setRage'; team: 'red' | 'blue'; value: number }
  | { type: 'control'; action: 'ban'; uniqueId: string }
  | { type: 'control'; action: 'unban'; uniqueId: string }
  | { type: 'control'; action: 'setSimulator'; enabled: boolean }
  | { type: 'control'; action: 'setCamera'; mode: string }
  /**
   * Ajustes finos de cámara en vivo. Solo se aplican los campos presentes, así
   * que el panel puede mandar un único deslizador sin arrastrar el resto.
   */
  | {
      type: 'control';
      action: 'tuneCamera';
      distance?: number;
      height?: number;
      fov?: number;
      cutSpeed?: number;
      orbit?: number;
      shake?: number;
      followAction?: boolean;
    }
  | { type: 'control'; action: 'setGraphics'; quality: string }
  /** Opciones gráficas sueltas que el streamer puede mover sin recargar. */
  | {
      type: 'control';
      action: 'tuneGraphics';
      bloomIntensity?: number;
      saturation?: number;
      contrast?: number;
      exposure?: number;
      vignette?: number;
      lodDistance?: number;
      textureDistance?: number;
      fogDensity?: number;
    }
  | { type: 'control'; action: 'setSeason'; season: string }
  | { type: 'control'; action: 'setWeather'; weather: string };

/** Estado que el overlay publica hacia el panel de control. */
export interface OverlayStatus {
  type: 'status';
  phase: string;
  round: number;
  seriesTarget: number;
  wins: { red: number; blue: number };
  soldiers: { red: number; blue: number };
  rage: { red: number; blue: number };
  countries: { red: string; blue: string };
  season: string;
  weather: string;
  entities: { red: number; blue: number };
  champions: { red: number; blue: number };
  /** Ajustes vivos, para que el panel arranque mostrando los valores reales. */
  camera: {
    mode: string;
    distance: number;
    height: number;
    fov: number;
    cutSpeed: number;
    orbit: number;
    shake: number;
    followAction: boolean;
  };
  graphics: {
    quality: string;
    bloomIntensity: number;
    saturation: number;
    contrast: number;
    exposure: number;
    vignette: number;
    lodDistance: number;
    textureDistance: number;
    fogDensity: number;
  };
  difficulty: number;
  fps: number;
  roundElapsed: number;
  simulator: boolean;
  paused: boolean;
  connected: boolean;
}

export type BusMessage = LiveEvent | ControlCommand | OverlayStatus;

export function isControlCommand(msg: unknown): msg is ControlCommand {
  return typeof msg === 'object' && msg !== null && (msg as { type?: string }).type === 'control';
}

export function isOverlayStatus(msg: unknown): msg is OverlayStatus {
  return typeof msg === 'object' && msg !== null && (msg as { type?: string }).type === 'status';
}
