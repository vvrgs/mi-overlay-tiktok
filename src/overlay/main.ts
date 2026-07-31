/**
 * Punto de entrada del overlay.
 *
 * Esta es la página que metes en OBS como Browser Source. Carga la config,
 * levanta la escena, abre los canales de eventos y arranca el bucle.
 */

import './styles.css';
import { EventBus } from '../events/bus';
import { EventSimulator } from '../events/simulator';
import { TransportManager } from '../events/transports';
import { Game } from '../game/game';
import { RankingStore } from '../game/ranking';
import { GameRenderer } from '../render/renderer';
import { loadConfig, resolveEndpoints } from '../shared/config';
import { Hud } from '../ui/hud';

async function boot(): Promise<void> {
  const config = await loadConfig();
  const params = new URLSearchParams(location.search);

  const canvas = document.getElementById('scene') as HTMLCanvasElement;
  const hudRoot = document.getElementById('hud') as HTMLElement;
  const nametagRoot = document.getElementById('nametags') as HTMLElement;
  if (!canvas || !hudRoot || !nametagRoot) throw new Error('Faltan nodos del overlay en index.html');

  const { apiBase, wsUrl } = resolveEndpoints(config);

  const bus = new EventBus(config);
  const transports = new TransportManager(bus, {
    wsUrl,
    reconnectMs: config.events.websocketReconnectMs,
    acceptPostMessage: config.events.acceptPostMessage,
    acceptGlobalApi: config.events.acceptGlobalApi,
  });

  const simulator = new EventSimulator(bus, config);
  const ranking = new RankingStore(apiBase, config.persistence.enabled, config.persistence.localFallback);

  const hud = new Hud(hudRoot, config);
  const renderer = new GameRenderer(canvas, config, nametagRoot);
  renderer.setTimeOfDay(config.graphics.timeOfDay);
  renderer.setQuality(config.graphics.quality);

  const game = new Game({ config, bus, renderer, hud, ranking, transports, simulator });

  transports.start();
  void ranking.start();

  // El simulador se puede forzar con ?sim=1 sin tocar la config.
  if (config.simulator.enabled || params.get('sim') === '1' || import.meta.env.VITE_FORCE_SIM === '1') simulator.start();

  window.addEventListener('resize', () => renderer.resize());
  // OBS puede redimensionar la fuente sin disparar 'resize' en la ventana.
  new ResizeObserver(() => renderer.resize()).observe(canvas);

  let last = performance.now();
  function frame(now: number): void {
    // Nunca negativo: el timestamp del primer rAF puede ser ANTERIOR al
    // performance.now() del arranque, y un dt negativo convierte el damp de la
    // cámara en anti-amortiguación: se dispara a miles de unidades y la escena
    // queda en niebla hasta que vuelve.
    const raw = Math.max(0, (now - last) / 1000);
    last = now;
    // `dt` va acotado para que un frame largo no dé saltos en animaciones y cámara.
    // `realDt` conserva el tiempo real (con tope por si la pestaña se suspendió):
    // los relojes de ronda deben avanzar aunque el render pase un mal momento.
    const dt = Math.min(0.05, raw);
    const realDt = Math.min(1, raw);
    // Un fallo puntual no puede matar el bucle: en un directo eso congela el overlay.
    try {
      game.update(dt, realDt);
      renderer.render(dt);
    } catch (err) {
      console.error('[overlay] Error en el frame:', err);
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  window.addEventListener('beforeunload', () => {
    ranking.stop();
    transports.dispose();
    game.dispose();
  });

  // Atajos útiles mientras montas la escena en OBS.
  window.addEventListener('keydown', (ev) => {
    if (ev.key === 's') simulator.toggle(!simulator.running);
    if (ev.key === 'p') game.setPaused(!game.paused);
    if (ev.key === 'n') game.startRound();
  });

  // Expuesto a propósito para depurar en directo desde la consola del navegador:
  // inspeccionar el estado, forzar eventos o mover la cámara sin recompilar.
  Object.assign(window as unknown as Record<string, unknown>, {
    __game: game,
    __config: config,
    __bus: bus,
    __renderer: renderer,
    __simulator: simulator,
  });
}

void boot().catch((err) => {
  console.error('[overlay] Fallo al arrancar:', err);
  const message = document.createElement('pre');
  message.style.cssText = 'position:fixed;inset:1rem;color:#ff8a96;font:14px monospace;white-space:pre-wrap;z-index:99';
  message.textContent = `No se pudo iniciar el overlay:\n${String(err)}`;
  document.body.appendChild(message);
});
