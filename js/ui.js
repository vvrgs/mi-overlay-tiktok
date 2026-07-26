/* ============================================================
 * UI — portada, marcador, monedas, panel de regalos, banners,
 * aviso de peligro, pantalla de muerte y estado de conexión.
 * ============================================================ */
const UI = (() => {
  const $ = (sel) => document.querySelector(sel);
  const counters = {};

  const CAUSE_TEXT = {
    car: '¡ATROPELLADO!',
    supertruck: '¡APLASTADO POR EL SUPER CAMIÓN!',
    train: '¡ARROLLADO POR EL TREN!',
    water: '¡AHOGADO!',
    fell: '¡ARRASTRADO POR EL RÍO!',
    volcano: '¡CALCINADO POR EL VOLCÁN!',
    boulder: '¡APLASTADO POR UNA ROCA!',
    lightning: '¡FULMINADO POR UN RAYO!',
    ufo: '¡ABDUCIDO!',
    eagle: '¡CAZADO POR EL ÁGUILA!',
    behind: '¡TE QUEDASTE ATRÁS!',
    reset: 'RESET DE LA RUN',
  };

  let bannerTimer = null;

  return {
    init() {
      const panel = $('#gift-panel');
      for (const item of PANEL_ITEMS) {
        const row = document.createElement('div');
        row.className = 'gift-row';
        row.innerHTML =
          '<span class="gift-icon">' + item.icon + '</span>' +
          '<span class="gift-label">' + item.label + '</span>' +
          '<span class="gift-count" id="count-' + item.action + '">0</span>';
        panel.appendChild(row);
        counters[item.action] = 0;
      }
    },

    showTitle() { $('#title').classList.add('show'); },
    hideTitle() { $('#title').classList.remove('show'); },

    setScore(n) { $('#score').textContent = n; },
    setRecord(n) { $('#record').textContent = 'RECORD: ' + n; },
    setCoins(n) { $('#coins-num').textContent = n; },

    bumpCounter(action) {
      if (counters[action] === undefined) return;
      counters[action]++;
      const el = $('#count-' + action);
      if (el) {
        el.textContent = counters[action];
        el.classList.remove('pop');
        void el.offsetWidth;
        el.classList.add('pop');
      }
    },

    banner(text, sub, color) {
      const b = $('#banner');
      const bt = $('#banner-text');
      bt.textContent = text;
      bt.style.color = color || '#fff';
      $('#banner-sub').textContent = sub ? 'de ' + sub : '';
      b.classList.remove('show');
      void b.offsetWidth;
      b.classList.add('show');
      clearTimeout(bannerTimer);
      bannerTimer = setTimeout(() => b.classList.remove('show'), 2600);
      this.shakeHUD();
    },

    /* marco de franjas de peligro parpadeante */
    warn() {
      const d = $('#danger');
      d.classList.remove('show');
      void d.offsetWidth;
      d.classList.add('show');
    },

    shakeHUD() {
      document.body.classList.remove('hud-shake');
      void document.body.offsetWidth;
      document.body.classList.add('hud-shake');
    },

    toast(text) {
      const box = $('#toasts');
      const t = document.createElement('div');
      t.className = 'toast';
      t.textContent = text;
      box.appendChild(t);
      setTimeout(() => t.classList.add('out'), 2400);
      setTimeout(() => t.remove(), 3000);
      while (box.children.length > 4) box.firstChild.remove();
    },

    flashRed() {
      const f = $('#flash');
      f.style.background = 'rgba(255,40,40,0.35)';
      f.classList.remove('show'); void f.offsetWidth; f.classList.add('show');
    },

    flashWhite() {
      const f = $('#flash');
      f.style.background = 'rgba(255,255,255,0.75)';
      f.classList.remove('show'); void f.offsetWidth; f.classList.add('show');
    },

    /* relámpago lejano, suave */
    flashSoft() {
      const f = $('#flash');
      f.style.background = 'rgba(255,255,255,0.22)';
      f.classList.remove('show'); void f.offsetWidth; f.classList.add('show');
    },

    /* interferencia del HUD (ovni) */
    setGlitch(on) { document.body.classList.toggle('glitch', !!on); },

    showGameOver(cause, score, record, isNewRecord) {
      $('#go-cause').textContent = CAUSE_TEXT[cause] || '¡GAME OVER!';
      $('#go-score').textContent = score;
      $('#go-record').textContent = isNewRecord ? '¡NUEVO RECORD!' : 'RECORD: ' + record;
      $('#go-record').classList.toggle('new-record', !!isNewRecord);
      $('#gameover').classList.add('show');
    },

    setRestartCountdown(sec) {
      $('#go-countdown').textContent = sec > 0 ? 'Reinicio en ' + sec + '...' : '';
    },

    hideGameOver() { $('#gameover').classList.remove('show'); },

    setConn(state) {
      const dot = $('#conn-dot');
      const label = $('#conn-label');
      dot.className = 'dot ' + state;
      label.textContent = state === 'on' ? 'TikTok conectado'
        : state === 'connecting' ? 'Conectando a TikTok…'
        : 'TikTok sin conexión (modo teclado)';
    },
  };
})();
