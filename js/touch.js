/* ============================================================
 * CONTROLES TÁCTILES — para jugar desde el celular:
 *  - tap: avanzar   - swipe: moverse en esa dirección
 *  - tap con el pollo muerto: reiniciar ya
 *  - botonera de eventos (visible en pantallas táctiles o con
 *    ?controls=1; se oculta con ?controls=0)
 * ============================================================ */
const TouchCtl = (() => {
  let sx, sy, active = false;

  const BTNS = [
    { action: 'superTruck', icon: '🚛' },
    { action: 'volcano', icon: '🌋' },
    { action: 'earthquake', icon: '🫨' },
    { action: 'tornado', icon: '🌪️' },
    { action: 'ufo', icon: '🛸' },
    { action: 'lightning', icon: '⚡' },
    { action: 'reset', icon: '💎' },
    { action: 'saveRun', icon: '🍓' },
    { action: 'moveLeft', icon: '⬅️' },
    { action: 'moveRight', icon: '➡️' },
  ];

  function wantButtons() {
    const p = new URLSearchParams(location.search);
    if (p.get('controls') === '0') return false;
    if (p.get('controls') === '1') return true;
    return ('ontouchstart' in window) || navigator.maxTouchPoints > 0;
  }

  function buildBar() {
    const bar = document.createElement('div');
    bar.id = 'touch-bar';
    for (const b of BTNS) {
      const el = document.createElement('button');
      el.className = 'touch-btn';
      el.textContent = b.icon;
      el.addEventListener('touchstart', (e) => { e.stopPropagation(); }, { passive: true });
      el.addEventListener('click', (e) => {
        e.preventDefault();
        AudioFX.unlock();
        Disasters.trigger(b.action, { user: 'táctil' });
      });
      bar.appendChild(el);
    }
    document.body.appendChild(bar);
  }

  function onStart(e) {
    if (e.target.closest && e.target.closest('#touch-bar')) return;
    AudioFX.unlock();
    Game.wake();
    const t = e.touches[0];
    sx = t.clientX; sy = t.clientY;
    active = true;
  }

  function onEnd(e) {
    if (!active) return;
    active = false;
    if (e.target.closest && e.target.closest('#touch-bar')) return;
    if (Game.state === 'dead') { Game.restart(); return; }
    const t = e.changedTouches[0];
    const dx = t.clientX - sx, dy = t.clientY - sy;
    const adx = Math.abs(dx), ady = Math.abs(dy);
    if (Math.max(adx, ady) < 18) Player.tryMove(0, 1);          // tap = avanzar
    else if (adx > ady) Player.tryMove(dx > 0 ? 1 : -1, 0);     // swipe lateral
    else Player.tryMove(0, dy < 0 ? 1 : -1);                    // swipe vertical
  }

  return {
    init() {
      window.addEventListener('touchstart', onStart, { passive: true });
      window.addEventListener('touchend', onEnd, { passive: true });
      if (wantButtons()) buildBar();
    },
  };
})();

window.addEventListener('DOMContentLoaded', () => TouchCtl.init());
