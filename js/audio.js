/* ============================================================
 * AUDIO — todos los sonidos se sintetizan con WebAudio,
 * no hace falta ningún archivo de sonido.
 * ============================================================ */
const AudioFX = (() => {
  let ctx = null;
  let master = null;
  let muted = false;
  let noiseBuf = null;

  function ensure() {
    if (!CONFIG.audio.enabled) return null;
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      ctx = new AC();
      master = ctx.createGain();
      master.gain.value = CONFIG.audio.volume;
      master.connect(ctx.destination);
      noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
      const d = noiseBuf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    if (ctx.state === 'suspended') ctx.resume();
    return ctx;
  }

  function tone(type, f0, f1, t0, dur, vol, curve) {
    if (!ensure() || muted) return;
    const t = ctx.currentTime + (t0 || 0);
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.type = type;
    o.frequency.setValueAtTime(f0, t);
    if (f1 && f1 !== f0) {
      if (curve === 'exp') o.frequency.exponentialRampToValueAtTime(Math.max(1, f1), t + dur);
      else o.frequency.linearRampToValueAtTime(f1, t + dur);
    }
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g).connect(master);
    o.start(t); o.stop(t + dur + 0.05);
  }

  function noise(t0, dur, vol, filterType, f0, f1) {
    if (!ensure() || muted) return;
    const t = ctx.currentTime + (t0 || 0);
    const src = ctx.createBufferSource();
    src.buffer = noiseBuf; src.loop = true;
    const g = ctx.createGain();
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    let node = src;
    if (filterType) {
      const fl = ctx.createBiquadFilter();
      fl.type = filterType;
      fl.frequency.setValueAtTime(f0 || 800, t);
      if (f1) fl.frequency.exponentialRampToValueAtTime(f1, t + dur);
      src.connect(fl); node = fl;
    }
    node.connect(g).connect(master);
    src.start(t); src.stop(t + dur + 0.05);
  }

  return {
    unlock() { ensure(); },
    toggleMute() { muted = !muted; return muted; },
    get muted() { return muted; },

    hop()     { tone('square', 260, 380, 0, 0.07, 0.12); },
    blocked() { tone('square', 140, 100, 0, 0.08, 0.10); },
    squash()  { tone('sine', 130, 30, 0, 0.25, 0.5, 'exp'); noise(0, 0.18, 0.25, 'lowpass', 900, 200); },
    splash()  { noise(0, 0.45, 0.35, 'lowpass', 1400, 250); tone('sine', 300, 90, 0, 0.3, 0.15, 'exp'); },
    coin()    { tone('square', 880, 880, 0, 0.06, 0.08); tone('square', 1320, 1320, 0.06, 0.09, 0.08); },
    horn() {
      for (const dt of [0, 0.45]) {
        tone('sawtooth', 220, 220, dt, 0.35, 0.22);
        tone('sawtooth', 277, 277, dt, 0.35, 0.22);
        tone('sawtooth', 110, 110, dt, 0.35, 0.18);
      }
    },
    trainBell() {
      for (let i = 0; i < 4; i++) tone('sine', 1245, 1245, i * 0.35, 0.28, 0.16);
    },
    trainPass() { noise(0, 1.6, 0.3, 'bandpass', 300, 500); },
    boom()    { noise(0, 0.7, 0.5, 'lowpass', 1200, 80); tone('sine', 120, 35, 0, 0.6, 0.6, 'exp'); },
    thunder() { noise(0, 1.4, 0.55, 'lowpass', 3000, 120); tone('sine', 90, 40, 0, 0.9, 0.4, 'exp'); },
    rumble(dur) { noise(0, dur || 3, 0.4, 'lowpass', 160, 90); },
    wind()    { noise(0, 2.5, 0.3, 'bandpass', 500, 900); },
    ufo() {
      for (let i = 0; i < 6; i++) tone('sine', 620 + (i % 2) * 160, 620 + ((i + 1) % 2) * 160, i * 0.18, 0.2, 0.10);
    },
    beam()    { tone('sine', 300, 900, 0, 0.8, 0.12); },
    shield()  { tone('sine', 523, 784, 0, 0.15, 0.15); tone('sine', 784, 1046, 0.15, 0.2, 0.15); },
    eagle()   { tone('sawtooth', 1800, 900, 0, 0.5, 0.15, 'exp'); },
    gameOver() {
      const notes = [392, 370, 349, 330];
      notes.forEach((f, i) => tone('square', f, f, i * 0.18, 0.16, 0.14));
    },
    alarm() { for (let i = 0; i < 3; i++) tone('square', 660, 440, i * 0.3, 0.25, 0.12, 'exp'); },
    /* sting corto de peligro antes de un desastre */
    warn() {
      tone('square', 880, 880, 0, 0.10, 0.14);
      tone('square', 880, 880, 0.16, 0.10, 0.14);
      tone('square', 587, 587, 0.32, 0.22, 0.14);
    },
    /* jingle de inicio de partida */
    jingle() {
      const notes = [523, 659, 784, 1046];
      notes.forEach((f, i) => tone('square', f, f, i * 0.09, 0.10, 0.10));
    },
    /* fanfarria de récord nuevo */
    fanfare() {
      const seq = [[523, 0], [659, 0.12], [784, 0.24], [1046, 0.36], [784, 0.52], [1046, 0.62]];
      for (const [f, t] of seq) { tone('square', f, f, t, 0.14, 0.13); tone('triangle', f / 2, f / 2, t, 0.16, 0.10); }
    },
  };
})();
