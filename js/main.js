/* ============================================================
 * MAIN — escena, cámara isométrica, luces, bucle del juego,
 * muerte/reinicio, águila por inactividad y teclado.
 * ============================================================ */
const Game = (() => {
  let renderer, scene, camera, dirLight, hemiLight;
  let clock = null;
  let focusRow = 0, focusX = 0;
  let restartTimer = 0;
  let eagleActive = null;

  const G = {
    state: 'playing',
    score: 0,
    record: 0,

    init() {
      const canvas = document.getElementById('game');
      renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.shadowMap.enabled = true;
      renderer.shadowMap.type = THREE.PCFSoftShadowMap;

      scene = new THREE.Scene();
      scene.background = new THREE.Color(0x6fd3f7);

      camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 100);
      this.resize();
      window.addEventListener('resize', () => this.resize());

      hemiLight = new THREE.HemisphereLight(0xffffff, 0x8fd444, 0.75);
      scene.add(hemiLight);
      dirLight = new THREE.DirectionalLight(0xffffff, 0.85);
      dirLight.castShadow = true;
      dirLight.shadow.mapSize.set(2048, 2048);
      const sc = dirLight.shadow.camera;
      sc.left = -16; sc.right = 16; sc.top = 16; sc.bottom = -16;
      sc.near = 1; sc.far = 60;
      scene.add(dirLight);
      scene.add(dirLight.target);

      World.init(scene);
      Effects.init(scene);
      Player.init(scene);
      Disasters.init(scene);
      UI.init();
      TikTok.init();

      this.record = parseInt(localStorage.getItem('crossy_record') || '0', 10);
      UI.setRecord(this.record);
      UI.setScore(0);

      this.bindKeys();

      clock = new THREE.Clock();
      renderer.setAnimationLoop(() => this.tick());
    },

    resize() {
      const w = window.innerWidth, h = window.innerHeight;
      renderer.setSize(w, h);
      const aspect = w / h;
      let halfW, halfH;
      if (aspect < 1) { halfW = 6.4; halfH = halfW / aspect; }
      else { halfH = 7.2; halfW = halfH * aspect; }
      camera.left = -halfW; camera.right = halfW;
      camera.top = halfH; camera.bottom = -halfH;
      camera.updateProjectionMatrix();
    },

    bindKeys() {
      window.addEventListener('keydown', (e) => {
        AudioFX.unlock();
        const k = e.key.toLowerCase();
        if (k === 'arrowup' || k === 'w') { e.preventDefault(); Player.tryMove(0, 1); }
        else if (k === 'arrowdown' || k === 's') { e.preventDefault(); Player.tryMove(0, -1); }
        else if (k === 'arrowleft' || k === 'a') { e.preventDefault(); Player.tryMove(-1, 0); }
        else if (k === 'arrowright' || k === 'd') { e.preventDefault(); Player.tryMove(1, 0); }
        else if (k === 'enter' && this.state === 'dead') this.restart();
        else if (k === 'm') AudioFX.toggleMute();
        // pruebas de acciones / desastres
        else if (k === '1') Disasters.trigger('reset', { user: 'teclado' });
        else if (k === '2') Disasters.trigger('saveRun', { user: 'teclado' });
        else if (k === '3') Disasters.trigger('moveLeft', { user: 'teclado' });
        else if (k === '4') Disasters.trigger('moveRight', { user: 'teclado' });
        else if (k === 't') Disasters.trigger('superTruck', { user: 'teclado' });
        else if (k === 'v') Disasters.trigger('volcano', { user: 'teclado' });
        else if (k === 'e') Disasters.trigger('earthquake', { user: 'teclado' });
        else if (k === 'n') Disasters.trigger('tornado', { user: 'teclado' });
        else if (k === 'u') Disasters.trigger('ufo', { user: 'teclado' });
        else if (k === 'l') Disasters.trigger('lightning', { user: 'teclado' });
      });
    },

    onRow(row) {
      if (row > this.score) {
        this.score = row;
        UI.setScore(this.score);
        if (this.score > 0 && this.score % 25 === 0) AudioFX.coin();
      }
    },

    /* --- Muerte (con posible salvación por escudo) --- */
    die(cause, opts) {
      opts = opts || {};
      if (this.state !== 'playing') return;

      if (Player.shield > 0 && !opts.noShield) {
        Player.shield--;
        this.rescue(cause);
        return;
      }

      this.state = 'dead';
      if (!opts.alreadyDead) Player.state = 'dead';
      else Player.state = 'dead';
      Player.playDeath(cause);
      AudioFX.gameOver();

      let isNew = false;
      if (this.score > this.record) {
        this.record = this.score;
        localStorage.setItem('crossy_record', String(this.record));
        UI.setRecord(this.record);
        isNew = true;
      }
      UI.showGameOver(cause, this.score, this.record, isNew);
      restartTimer = CONFIG.autoRestartSec;
    },

    /* El escudo te salva: te recoloca en pasto seguro unas filas atrás */
    rescue(cause) {
      AudioFX.shield();
      UI.banner('🛡️ ¡EL ESCUDO TE SALVÓ!');
      Effects.shockwave(Player.mesh.position.clone(), 0x7fe7ff);
      let row = Player.row;
      for (let tries = 0; tries < 12; tries++) {
        const lane = World.laneAt(row);
        if (lane && lane.type === 'grass') break;
        row--;
        if (row < 0) { row = 0; break; }
      }
      let col = 0;
      for (const c of [0, 1, -1, 2, -2, 3, -3]) {
        if (!World.isBlocked(row, c)) { col = c; break; }
      }
      Player.hop = null; Player.queue = []; Player.onLog = null;
      Player.forcedPush = null;
      Player.state = 'alive';
      Player.sinking = 0;
      Player.row = row; Player.rowF = row; Player.col = col; Player.x = col;
      Player.invulnUntil = performance.now() / 1000 + 2.5;
      Player.mesh.visible = true;
      Player.mesh.scale.set(1, 1, 1);
      Player.mesh.rotation.set(0, 0, 0);
      Player.syncMesh(World.topY(row));
      Player.lastMoveTime = performance.now() / 1000;
    },

    restart() {
      this.state = 'playing';
      this.score = 0;
      UI.setScore(0);
      UI.hideGameOver();
      Disasters.clear();
      Effects.clear();
      World.reset();
      Player.reset();
      if (eagleActive) { scene.remove(eagleActive.mesh); eagleActive = null; }
      focusRow = 0; focusX = 0;
    },

    /* --- Águila por inactividad --- */
    updateEagle(dt, now) {
      if (eagleActive) {
        const e = eagleActive;
        e.mesh.userData.wings[0].rotation.z = Math.sin(now * 14) * 0.5;
        e.mesh.userData.wings[1].rotation.z = -Math.sin(now * 14) * 0.5;
        if (e.phase === 'swoop') {
          const target = Player.mesh.position.clone().add(new THREE.Vector3(0, 0.35, 0));
          e.mesh.position.lerp(target, Math.min(1, 3.5 * dt));
          e.mesh.lookAt(target.x, e.mesh.position.y, target.z - 4);
          if (e.mesh.position.distanceTo(target) < 0.5) {
            e.phase = 'grab';
            if (Player.state === 'alive') Player.setCarried();
            AudioFX.eagle();
          }
        } else if (e.phase === 'grab') {
          e.mesh.position.y += 5 * dt;
          e.mesh.position.z -= 7 * dt;
          Player.mesh.position.copy(e.mesh.position).add(new THREE.Vector3(0, -0.55, 0));
          Player.mesh.rotation.y += 4 * dt;
          if (e.mesh.position.y > 8) {
            Player.state = 'dead';
            this.die('eagle', { alreadyDead: true });
            Player.mesh.visible = false;
            scene.remove(e.mesh);
            eagleActive = null;
          }
        }
        return;
      }
      if (this.state === 'playing' && Player.state === 'alive' &&
          now - Player.lastMoveTime > CONFIG.idleEagleSec) {
        const mesh = Models.eagle();
        const p = Player.mesh.position;
        mesh.position.set(p.x, 7, p.z + 10);
        scene.add(mesh);
        eagleActive = { mesh, phase: 'swoop' };
        AudioFX.eagle();
      }
    },

    tick() {
      const dt = Math.min(clock.getDelta(), 0.05);
      const now = performance.now() / 1000;

      if (this.state === 'playing') {
        // avance automático de cámara (más rápido con más puntos)
        const creep = Math.min(CONFIG.cameraCreepMax, CONFIG.cameraCreepBase + this.score * 0.006);
        const started = this.score > 0 || Player.rowF > 0.1 || now - Player.lastMoveTime < 8;
        if (started && this.score >= 1) focusRow += creep * dt;
        if (Player.rowF > focusRow) focusRow += (Player.rowF - focusRow) * Math.min(1, 6 * dt);
        // si un desastre te empuja muy atrás, la cámara te sigue (dramático)
        if (Player.rowF < focusRow - 4) focusRow += (Player.rowF + 2 - focusRow) * Math.min(1, 2.2 * dt);
        // pero si te quedas atrás de verdad, mueres
        if (Player.state === 'alive' && Player.rowF < focusRow - CONFIG.behindDeathRows) {
          this.die('behind');
        }
        this.updateEagle(dt, now);
      } else {
        // cuenta atrás de reinicio
        if (restartTimer > 0) {
          restartTimer -= dt;
          UI.setRestartCountdown(Math.ceil(restartTimer));
          if (restartTimer <= 0) this.restart();
        }
      }

      World.ensure(Math.ceil(focusRow) + 28);
      World.cull(Math.floor(focusRow) - 16);
      World.update(dt, focusRow, Player.row);
      Player.update(dt);
      Disasters.update(dt);
      Effects.update(dt);

      // cámara isométrica siguiendo el foco
      focusX += (Math.max(-2.5, Math.min(2.5, Player.mesh.position.x)) - focusX) * Math.min(1, 3 * dt);
      const fz = -focusRow;
      const shake = Effects.getShakeOffset();
      camera.position.set(focusX + 5.2 + shake.x, 11, fz + 7.2 + shake.y);
      camera.lookAt(focusX + shake.x, 0, fz - 1.2 + shake.y);

      dirLight.position.set(focusX - 5, 13, fz + 6);
      dirLight.target.position.set(focusX, 0, fz - 2);

      renderer.render(scene, camera);
    },
  };

  return G;
})();

window.addEventListener('DOMContentLoaded', () => Game.init());
