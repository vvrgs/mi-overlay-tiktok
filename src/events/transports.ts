/**
 * Transportes de entrada/salida.
 *
 * El overlay escucha eventos por cuatro vías simultáneas, para que puedas
 * conectar prizvo.live por la que te resulte más cómoda:
 *   1. WebSocket       → el servidor incluido (`npm run server`) o el tuyo.
 *   2. postMessage     → si embebes el overlay en un <iframe> dentro de tu app.
 *   3. API global      → window.GuerraDeNaciones.emit(evento) desde la misma página.
 *   4. HTTP POST       → tu backend hace POST /api/event y el servidor lo reenvía por WS.
 *   5. BroadcastChannel→ comunicación entre pestañas del mismo origen; es lo que
 *                        permite que el panel de control funcione sin servidor.
 */

import type { EventBus } from './bus';
import type { OverlayStatus } from './types';

export interface TransportOptions {
  wsUrl: string;
  reconnectMs: number;
  acceptPostMessage: boolean;
  acceptGlobalApi: boolean;
  onConnectionChange?: (connected: boolean) => void;
}

/** Canal compartido entre el overlay y el panel de control en el mismo navegador. */
export const CHANNEL_NAME = 'guerra-de-naciones';

export class TransportManager {
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private disposed = false;
  private messageHandler: ((ev: MessageEvent) => void) | null = null;
  private channel: BroadcastChannel | null = null;
  connected = false;

  constructor(private bus: EventBus, private options: TransportOptions) {}

  start(): void {
    this.connectSocket();
    this.attachBroadcastChannel();
    if (this.options.acceptPostMessage) this.attachPostMessage();
    if (this.options.acceptGlobalApi) this.attachGlobalApi();
  }

  private attachBroadcastChannel(): void {
    if (typeof BroadcastChannel === 'undefined') return;
    this.channel = new BroadcastChannel(CHANNEL_NAME);
    this.channel.onmessage = (ev) => {
      // El overlay ignora sus propios latidos de estado.
      if (typeof ev.data === 'object' && ev.data !== null && (ev.data as { type?: string }).type === 'status') return;
      this.ingestRaw(ev.data);
    };
  }

  // ---------------------------------------------------------------- WebSocket

  private connectSocket(): void {
    if (this.disposed || !this.options.wsUrl) return;
    try {
      const socket = new WebSocket(this.options.wsUrl);
      this.socket = socket;

      socket.addEventListener('open', () => {
        this.setConnected(true);
        socket.send(JSON.stringify({ type: 'hello', role: 'overlay' }));
      });

      socket.addEventListener('message', (ev) => {
        this.ingestRaw(ev.data);
      });

      socket.addEventListener('close', () => {
        this.setConnected(false);
        this.scheduleReconnect();
      });

      socket.addEventListener('error', () => {
        // 'close' se dispara justo después; la reconexión se maneja allí.
        socket.close();
      });
    } catch (err) {
      console.warn('[transport] No se pudo abrir el WebSocket:', err);
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.disposed || this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connectSocket();
    }, Math.max(500, this.options.reconnectMs));
  }

  private setConnected(value: boolean): void {
    if (this.connected === value) return;
    this.connected = value;
    this.options.onConnectionChange?.(value);
  }

  // -------------------------------------------------------------- postMessage

  private attachPostMessage(): void {
    this.messageHandler = (ev: MessageEvent) => {
      // Se ignoran los mensajes internos de Vite en desarrollo.
      if (typeof ev.data === 'object' && ev.data !== null && 'vite' in (ev.data as object)) return;
      this.ingestRaw(ev.data);
    };
    window.addEventListener('message', this.messageHandler);
  }

  // -------------------------------------------------------------- API global

  private attachGlobalApi(): void {
    const api = {
      /** Envía un evento al juego. Acepta objeto o JSON string. */
      emit: (payload: unknown) => this.bus.ingest(payload),
      /** Alias cómodos. */
      gift: (uniqueId: string, giftName: string, diamondCount: number, repeatCount = 1) =>
        this.bus.ingest({ type: 'gift', uniqueId, nickname: uniqueId, giftName, diamondCount, repeatCount }),
      chat: (uniqueId: string, comment: string) => this.bus.ingest({ type: 'chat', uniqueId, nickname: uniqueId, comment }),
      like: (uniqueId: string, likeCount = 1) => this.bus.ingest({ type: 'like', uniqueId, nickname: uniqueId, likeCount }),
      version: 1,
    };
    (window as unknown as Record<string, unknown>).GuerraDeNaciones = api;
    // Alias en inglés por conveniencia al integrar.
    (window as unknown as Record<string, unknown>).WarOfNations = api;
  }

  // -------------------------------------------------------------------- utils

  private ingestRaw(data: unknown): void {
    if (typeof data === 'string') {
      const trimmed = data.trim();
      if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return;
      try {
        this.bus.ingest(JSON.parse(trimmed));
      } catch {
        /* payload no-JSON: se ignora en silencio */
      }
      return;
    }
    this.bus.ingest(data);
  }

  /** Publica el estado del overlay hacia el panel de control. */
  publishStatus(status: OverlayStatus): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(status));
    }
    this.channel?.postMessage(status);
    if (window.parent !== window) {
      window.parent.postMessage(status, '*');
    }
  }

  dispose(): void {
    this.disposed = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.messageHandler) window.removeEventListener('message', this.messageHandler);
    this.channel?.close();
    this.socket?.close();
  }
}
