/* ============================================================
 * MAIN — escena, cámara isométrica, luces, bucle del juego,
 * portada, cámara lenta al morir, tormenta, monedas, récord
 * con confeti, águila por inactividad y teclado.
 * ============================================================ */
const Game = (() => {
  let renderer, scene, camera, dirLight, hemiLight;
  let clock = null;
  let focusRow = 0, focusX = 0;
  let restartTimer = 0;
  let eagleActive = null;
  let slowmo = 0;
  let zoomCur = 1;
  let punchVal = 0;
  let stormTarget = 0, stormCur = 0;
  const SKY = new THREE.Color(0x6fd3f7);
  const SKY_STORM = new THREE.Color(0x46586e);
  const skyNow = new THREE.Color(0x6fd3f7);

  const G = {
    state: 'title',
    score: 0,
    record: 0,
    coins: 0,
    timeScale: 1,

    init() {
      const canvas = document.getElementById('game');
      renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.shadowMap.enabled = true;
      renderer.shadowMap.type = THREE.PCFSoftShadowMap;

      scene = new THREE.Scene();
      scene.background = skyNow;
      scene.fog = new THREE.Fog(skyNow, 24, 46);

      camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 100);
      this.resize();
      window.addEventListener('resize', () => this.resize());

      hemiLight = new THREE.HemisphereLight(0xffffff, 0x8fd444, 0.75);
      scene.add(hemiLight);
      dirLight = new THREE.DirectionalLight(0xfff4e0, 0.85);
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
      this.coins = parseInt(localStorage.getItem('crossy_coins') || '0', 10);
      UI.setRecord(this.record);
      UI.setScore(0);
      UI.setCoins(this.coins);

      this.bindKeys();
      UI.showTitle();
      setTimeout(() => this.wake(), 5000);   // la portada se quita sola

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

    /* saca de la portada (primer input o primer regalo) */
    wake() {
      if (this.state !== 'title') return;
      this.state = 'playing';
      UI.hideTitle();
      AudioFX.jingle();
      Player.lastMoveTime = performance.now() / 1000;
    },

    setStorm(v) { stormTarget = v; },

    /* golpe de zoom para momentos de impacto */
    punch(v) { punchVal = Math.max(punchVal, v); },

    addCoin() {
      this.coins++;
      UI.setCoins(this.coins);
      localStorage.setItem('crossy_coins', String(this.coins));
    },

    bindKeys() {
      window.addEventListener('keydown', (e) => {
        AudioFX.unlock();
        const k = e.key.toLowerCase();
        const isMove = ['arrowup', 'arrowdown', 'arrowleft', 'arrowright', 'w', 'a', 's', 'd'].includes(k);
        if (isMove) this.wake();
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
      Player.state = 'dead';
      Player.playDeath(cause);
      slowmo = 0.55;                       // momento dramático

      let isNew = false;
      if (this.score > this.record) {
        this.record = this.score;
        localStorage.setItem('crossy_record', String(this.record));
        UI.setRecord(this.record);
        isNew = true;
      }
      if (isNew && this.score > 3) {
        AudioFX.fanfare();
        Effects.confetti(new THREE.Vector3(focusX, 0, -focusRow));
      } else {
        AudioFX.gameOver();
      }
      UI.showGameOver(cause, this.score, this.record, isNew);
      restartTimer = CONFIG.autoRestartSec;
    },

    /* El escudo te salva: te recoloca en pasto seguro unas filas atrás */
    rescue(cause) {
      AudioFX.shield();
      UI.banner('🛡️ ¡EL ESCUDO TE SALVÓ!', null, '#7fe7ff');
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
      this.clearEagle();
      focusRow = 0; focusX = 0;
      stormTarget = 0;
      slowmo = 0;
      punchVal = 0;
      AudioFX.jingle();
    },

    /* --- Águila por inactividad (rodea con su sombra y ataca) --- */
    updateEagle(dt, now) {
      if (eagleActive) {
        const e = eagleActive;
        e.mesh.userData.wings[0].rotation.z = Math.sin(now * 14) * 0.5;
        e.mesh.userData.wings[1].rotation.z = -Math.sin(now * 14) * 0.5;
        // sombra proyectada en el suelo
        if (e.shadow) {
          e.shadow.position.set(e.mesh.position.x, 0.11, e.mesh.position.z);
          const k = Math.min(1, e.t / 1.5);
          e.shadow.scale.setScalar(0.5 + k * 0.7);
          e.shadow.material.opacity = 0.12 + k * 0.25;
        }
        if (e.phase === 'circle') {
          e.t += dt;
          const p = Player.mesh.position;
          const a = e.t * 2.6;
          const goal = new THREE.Vector3(p.x + Math.cos(a) * 4.2, 5.5, p.z + Math.sin(a) * 4.2);
          e.mesh.position.lerp(goal, Math.min(1, 4 * dt));
          e.mesh.lookAt(goal.x - Math.sin(a) * 3, 5.2, goal.z + Math.cos(a) * 3);
          if (e.t > 0.9 && !e.cried) { e.cried = true; AudioFX.eagle(); }
          if (e.t > 2.3) { e.phase = 'swoop'; AudioFX.eagle(); }
        } else if (e.phase === 'swoop') {
          const target = Player.mesh.position.clone().add(new THREE.Vector3(0, 0.35, 0));
          e.mesh.position.lerp(target, Math.min(1, 3.8 * dt));
          e.mesh.lookAt(target.x, e.mesh.position.y, target.z - 4);
          if (e.mesh.position.distanceTo(target) < 0.5) {
            e.phase = 'grab';
            if (Player.state === 'alive') Player.setCarried();
            Effects.feathers(target, 8);
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
            this.clearEagle();
          }
        }
        return;
      }
      if (this.state === 'playing' && Player.state === 'alive' &&
          now - Player.lastMoveTime > CONFIG.idleEagleSec) {
        const mesh = Models.eagle();
        const p = Player.mesh.position;
        mesh.position.set(p.x + 4, 6, p.z + 8);
        scene.add(mesh);
        const shadow = new THREE.Mesh(
          new THREE.CircleGeometry(0.9, 20),
          new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.1, depthWrite: false })
        );
        shadow.rotation.x = -Math.PI / 2;
        scene.add(shadow);
        eagleActive = { mesh, shadow, phase: 'circle', t: 0, cried: false };
        AudioFX.eagle();
      }
    },

    clearEagle() {
      if (!eagleActive) return;
      scene.remove(eagleActive.mesh);
      if (eagleActive.shadow) {
        scene.remove(eagleActive.shadow);
        eagleActive.shadow.geometry.dispose();
        eagleActive.shadow.material.dispose();
      }
      eagleActive = null;
    },

    tick() {
      const rawDt = Math.min(clock.getDelta(), 0.05);
      let dt = rawDt;
      if (slowmo > 0) { slowmo -= rawDt; dt = rawDt * 0.3; }
      const now = performance.now() / 1000;

      if (this.state === 'playing') {
        const creep = Math.min(CONFIG.cameraCreepMax, CONFIG.cameraCreepBase + this.score * 0.006);
        if (this.score >= 1) focusRow += creep * dt;
        if (Player.rowF > focusRow) focusRow += (Player.rowF - focusRow) * Math.min(1, 6 * dt);
        if (Player.rowF < focusRow - 4) focusRow += (Player.rowF + 2 - focusRow) * Math.min(1, 2.2 * dt);
        if (Player.state === 'alive' && Player.rowF < focusRow - CONFIG.behindDeathRows) {
          this.die('behind');
        }
        this.updateEagle(dt, now);
      } else if (this.state === 'dead') {
        if (restartTimer > 0) {
          restartTimer -= rawDt;
          UI.setRestartCountdown(Math.ceil(restartTimer));
          if (restartTimer <= 0) this.restart();
        }
      }

      // tormenta: cielo y luces se oscurecen
      stormCur += (stormTarget - stormCur) * Math.min(1, 3 * rawDt);
      skyNow.copy(SKY).lerp(SKY_STORM, stormCur);
      dirLight.intensity = 0.85 * (1 - 0.62 * stormCur);
      hemiLight.intensity = 0.75 * (1 - 0.55 * stormCur);

      World.ensure(Math.ceil(focusRow) + 28);
      World.cull(Math.floor(focusRow) - 16);
      World.update(dt, focusRow, Player.row);
      Player.update(dt);
      Disasters.update(dt);
      Effects.update(dt, focusX, focusRow);

      // cámara isométrica con zoom dramático (muerte + golpes de impacto)
      focusX += (Math.max(-2.5, Math.min(2.5, Player.mesh.position.x)) - focusX) * Math.min(1, 3 * dt);
      punchVal *= Math.max(0, 1 - 3.5 * rawDt);
      const zoomTarget = (slowmo > 0 ? 1.14 : 1) + punchVal;
      if (Math.abs(zoomCur - zoomTarget) > 0.001) {
        zoomCur += (zoomTarget - zoomCur) * Math.min(1, 6 * rawDt);
        camera.zoom = zoomCur;
        camera.updateProjectionMatrix();
      }
      const fz = -focusRow;
      const shake = Effects.getShakeOffset();
      const roll = Effects.getRoll();
      camera.up.set(Math.sin(roll), Math.cos(roll), 0);
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
