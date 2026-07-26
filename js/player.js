/* ============================================================
 * JUGADOR — el pollo: saltos con squash & stretch, troncos,
 * colisiones, escudo y empujones de los desastres.
 * ============================================================ */
const Player = (() => {
  const HOP_DUR = 0.16;
  const HOP_H = 0.55;

  let scene = null;

  const P = {
    mesh: null, bubble: null,
    col: 0, row: 0, x: 0, rowF: 0,
    state: 'alive', // alive | carried | dead
    hop: null,
    queue: [],
    onLog: null, logRel: 0,
    shield: 0, invulnUntil: 0,
    lastMoveTime: 0,
    sinking: 0,
    squashT: 0,

    init(sc) {
      scene = sc;
      this.mesh = Models.chicken();
      this.bubble = Models.shieldBubble();
      this.bubble.visible = false;
      this.mesh.add(this.bubble);
      scene.add(this.mesh);
      this.reset();
    },

    reset() {
      this.col = 0; this.row = 0; this.x = 0; this.rowF = 0;
      this.state = 'alive';
      this.hop = null; this.queue = [];
      this.onLog = null; this.logRel = 0;
      this.shield = 0; this.invulnUntil = 0;
      this.sinking = 0; this.squashT = 0;
      this.lastMoveTime = performance.now() / 1000;
      this.mesh.visible = true;
      this.mesh.scale.set(1, 1, 1);
      this.mesh.rotation.set(0, 0, 0);
      this.bubble.visible = false;
      this.forcedPush = null;
      this.peckT = 2 + Math.random() * 3;
      this.peckAnim = 0;
      if (this.mesh.userData.head) this.mesh.userData.head.rotation.set(0, 0, 0);
      this.syncMesh(World.topY(0));
    },

    syncMesh(y) {
      this.mesh.position.set(this.x, y, -this.rowF);
    },

    groundY() {
      return this.onLog ? 0.10 : World.topY(this.row);
    },

    tryMove(dx, dz, forced) {
      if (this.state !== 'alive') return;
      if (this.hop) {
        if (this.queue.length < 2) this.queue.push({ dx, dz, forced });
        return;
      }
      const fromX = this.x;
      const baseCol = Math.round(Math.max(-CONFIG.cols, Math.min(CONFIG.cols, this.x)));
      let toCol = baseCol + dx;
      const toRow = this.row + dz;

      if (Math.abs(toCol) > CONFIG.cols) {
        if (!forced) { this.bounce(dx, dz); return; }
        toCol = Math.max(-CONFIG.cols, Math.min(CONFIG.cols, toCol));
      }
      if (World.isBlocked(toRow, toCol) && !forced) {
        this.bounce(dx, dz);
        return;
      }

      this.lastMoveTime = performance.now() / 1000;
      const wasLog = this.onLog;
      this.onLog = null;

      const targetLane = World.laneAt(toRow);
      const toY = (targetLane && targetLane.type === 'water') ? 0.10 : World.topY(toRow);
      this.hop = {
        t: 0, dur: HOP_DUR,
        fromX, toX: toCol,
        fromRow: this.rowF, toRow,
        fromY: wasLog ? 0.10 : World.topY(this.row),
        toY,
        forced: !!forced,
        rotTo: dz > 0 ? 0 : (dz < 0 ? Math.PI : (dx > 0 ? -Math.PI / 2 : Math.PI / 2)),
      };
      AudioFX.hop();
    },

    bounce(dx, dz) {
      AudioFX.blocked();
      this.squashT = 0.12;
      const ry = dz > 0 ? 0 : (dz < 0 ? Math.PI : (dx > 0 ? -Math.PI / 2 : Math.PI / 2));
      this.mesh.rotation.y = ry;
    },

    /* Empuje forzado hacia atrás (desastres): n saltos separados por interval seg */
    forcePush(rows, interval) {
      this.forcedPush = { remaining: rows, timer: 0, interval: interval || 0.30 };
    },

    land() {
      const h = this.hop;
      this.row = h.toRow;
      this.rowF = h.toRow;
      this.x = h.toX;
      this.col = h.toX;
      this.hop = null;
      this.squashT = 0.10;
      Game.onRow(this.row);

      const lane = World.laneAt(this.row);
      if (h.forced) World.crushObstacle(this.row, this.col);

      if (lane && (lane.type === 'grass' || lane.type === 'road')) {
        Effects.dust(new THREE.Vector3(this.x, World.topY(this.row), -this.row));
      }
      if (World.collectCoin(this.row, this.col)) {
        AudioFX.coin();
        Game.addCoin();
      }

      if (lane && lane.type === 'water') {
        const log = World.logAt(this.row, this.x);
        if (log) {
          this.onLog = log;
          this.logRel = this.x - log.x;
        } else {
          Game.die('water');
          return;
        }
      }
      if (this.queue.length) {
        const q = this.queue.shift();
        this.tryMove(q.dx, q.dz, q.forced);
      }
    },

    setCarried() {
      this.state = 'carried';
      this.hop = null; this.queue = [];
      this.onLog = null;
      this.forcedPush = null;
    },

    releaseCarried(row, col) {
      this.row = row; this.rowF = row;
      this.col = col; this.x = col;
      this.state = 'alive';
      this.lastMoveTime = performance.now() / 1000;
      this.mesh.rotation.set(0, 0, 0);
      this.mesh.scale.set(1, 1, 1);
      const lane = World.laneAt(row);
      if (lane && lane.type === 'water') {
        const log = World.logAt(row, this.x);
        if (log) { this.onLog = log; this.logRel = this.x - log.x; }
        else { this.syncMesh(0); Game.die('water'); return; }
      }
      this.syncMesh(this.groundY());
      this.squashT = 0.12;
    },

    playDeath(cause) {
      if (cause === 'car' || cause === 'supertruck' || cause === 'train') {
        this.mesh.scale.set(1.35, 0.12, 1.35);
        AudioFX.squash();
        const p = this.mesh.position.clone();
        Effects.feathers(p, 18);
      } else if (cause === 'water' || cause === 'fell') {
        this.sinking = 0.7;
        AudioFX.splash();
        Effects.splash(this.mesh.position.clone());
      } else if (cause === 'lightning' || cause === 'volcano' || cause === 'reset') {
        const p = this.mesh.position.clone();
        Effects.feathers(p, 22);
        Effects.explosion(p, 0.8);
        this.mesh.visible = false;
      } else if (cause === 'behind') {
        Effects.feathers(this.mesh.position.clone(), 14);
        this.mesh.visible = false;
      }
      // eagle / ufo: la animación la controla el propio desastre
    },

    update(dt) {
      if (this.state === 'dead') {
        if (this.sinking > 0) {
          this.sinking -= dt;
          this.mesh.position.y -= 1.6 * dt;
        }
        return;
      }
      if (this.state === 'carried') return;

      // empujones pendientes
      if (this.forcedPush && this.forcedPush.remaining > 0) {
        this.forcedPush.timer -= dt;
        if (!this.hop && this.forcedPush.timer <= 0) {
          this.tryMove(0, -1, true);
          this.forcedPush.remaining--;
          this.forcedPush.timer = this.forcedPush.interval;
        }
      }

      if (this.hop) {
        const h = this.hop;
        h.t += dt;
        const k = Math.min(1, h.t / h.dur);
        this.x = h.fromX + (h.toX - h.fromX) * k;
        this.rowF = h.fromRow + (h.toRow - h.fromRow) * k;
        const y = h.fromY + (h.toY - h.fromY) * k + Math.sin(k * Math.PI) * HOP_H;
        // rotación suave hacia la dirección del salto
        let d = h.rotTo - this.mesh.rotation.y;
        while (d > Math.PI) d -= Math.PI * 2;
        while (d < -Math.PI) d += Math.PI * 2;
        this.mesh.rotation.y += d * Math.min(1, 14 * dt);
        // estirar en el aire
        const stretch = 1 + Math.sin(k * Math.PI) * 0.15;
        this.mesh.scale.set(1, stretch, 1);
        this.syncMesh(y);
        if (k >= 1) {
          this.mesh.scale.set(1, 1, 1);
          this.land();
          if (this.state !== 'alive') return;
        }
      } else {
        if (this.onLog) {
          // el tronco me arrastra
          this.x = this.onLog.x + this.logRel;
          this.col = Math.round(this.x);
          if (Math.abs(this.x) > CONFIG.cols + 2.2) { Game.die('fell'); return; }
        }
        // squash al aterrizar + respiración en reposo
        const tnow = performance.now() / 1000;
        if (this.squashT > 0) {
          this.squashT -= dt;
          const k = Math.max(0, this.squashT / 0.12);
          this.mesh.scale.set(1 + 0.18 * k, 1 - 0.25 * k, 1 + 0.18 * k);
        } else {
          this.mesh.scale.set(1, 1 + Math.sin(tnow * 3.2) * 0.018, 1);
        }
        // picotea de vez en cuando si está quieto
        const head = this.mesh.userData.head;
        if (head) {
          if (this.peckAnim > 0) {
            this.peckAnim -= dt;
            const k = Math.max(0, this.peckAnim / 0.35);
            head.rotation.x = Math.sin((1 - k) * Math.PI) * 0.55;
            if (this.peckAnim <= 0) head.rotation.x = 0;
          } else {
            this.peckT -= dt;
            if (this.peckT <= 0) {
              this.peckAnim = 0.35;
              this.peckT = 2.5 + Math.random() * 4;
            }
          }
        }
        this.syncMesh(this.groundY());
      }

      // parpadeo si invulnerable
      const now = performance.now() / 1000;
      if (now < this.invulnUntil) {
        this.mesh.visible = (Math.floor(now * 12) % 2) === 0;
      } else if (this.state === 'alive') {
        this.mesh.visible = true;
      }

      this.bubble.visible = this.shield > 0;
      if (this.bubble.visible) {
        this.bubble.material.opacity = 0.2 + Math.sin(now * 5) * 0.08;
      }

      // colisiones con coches / trenes
      if (now >= this.invulnUntil) {
        const rowsToCheck = this.hop ? [this.hop.fromRow, this.hop.toRow] : [this.row];
        for (const r of rowsToCheck) {
          const ri = Math.round(r);
          if (Math.abs(this.rowF - ri) > 0.7) continue;
          const cause = World.lethalAt(ri, this.x);
          if (cause) { Game.die(cause); return; }
        }
      }
    },
  };

  return P;
})();
