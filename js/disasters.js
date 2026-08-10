/* ============================================================
 * DESASTRES Y ACCIONES DE REGALOS (v4 — máximo espectáculo)
 *  - Cada evento es una secuencia con fases, clima, cámara,
 *    física y sonido propios.
 *  - Si coinciden 2+ desastres a la vez: ¡APOCALIPSIS!
 * ============================================================ */
const Disasters = (() => {
  let scene = null;
  const active = [];

  function add(d) { active.push(d); return d; }
  function playerPos() { return Player.mesh.position.clone(); }
  function wp(obj) { const v = new THREE.Vector3(); obj.getWorldPosition(v); return v; }
  function disposeAll(mesh) { mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); }); }

  /* cono de luz translúcido (faros, focos) */
  function lightCone(r1, r2, len, color, opacity) {
    return new THREE.Mesh(
      new THREE.CylinderGeometry(r1, r2, len, 10, 1, true),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity, side: THREE.DoubleSide, depthWrite: false })
    );
  }

  /* ============ 🚛 SUPER CAMIÓN (con doble pasada) ============ */
  function superTruck(user) {
    UI.banner('🚛 ¡SUPER CAMIÓN!', user, '#ff5a3c');
    UI.warn();
    AudioFX.warn();
    AudioFX.horn();
    let phase = 'warn', t = 0, mesh = null, x = 0, dir = 1, row = 0, pass = 1;
    let crackT = 0, fireT = 0, skidT = 0, lastTrailX = 999;
    const trail = [];   // llamas que quedan ardiendo en el asfalto

    function launch() {
      row = Player.row;
      x = -dir * (CONFIG.spawnX + 9);
      mesh.rotation.y = dir > 0 ? 0 : Math.PI;
      mesh.position.set(x, World.topY(row), -row);
      Effects.shockwave(new THREE.Vector3(x + dir * 4, 0, -row), 0xff5a3c);
      AudioFX.trainPass();
      AudioFX.horn();
      Game.punch(0.10);
      UI.flashRed();
      lastTrailX = 999;
    }

    add({
      major: true,
      update(dt) {
        t += dt;
        // el rastro de fuego sigue ardiendo
        for (let i = trail.length - 1; i >= 0; i--) {
          const f = trail[i];
          f.ttl -= dt;
          if (Math.random() < dt * 7) Effects.fire(new THREE.Vector3(f.x, 0.12, -f.row), 1);
          if (f.ttl <= 0) trail.splice(i, 1);
        }
        if (phase === 'burnout') return trail.length > 0;
        if (phase === 'warn') {
          Effects.sustainShake(0.05 + t * 0.04);
          crackT -= dt;
          if (crackT <= 0) {
            crackT = 0.22;
            const cx = -6 + Math.random() * 12;
            Effects.crack(new THREE.Vector3(cx, World.topY(Player.row), -Player.row + (Math.random() - 0.5) * 0.8), Math.random() * 0.6 - 0.3, 1.5 + Math.random() * 1.5);
            Effects.dust(new THREE.Vector3(cx, 0.1, -Player.row));
          }
          if (t > 1.6) {
            phase = 'run';
            dir = Math.random() < 0.5 ? 1 : -1;
            mesh = Models.superTruck();
            // faros encendidos
            for (const z of [0.4, -0.4]) {
              const cone = lightCone(0.12, 0.65, 3.8, 0xfff6c8, 0.15);
              cone.rotation.z = -Math.PI / 2;
              cone.position.set(4.5, 0.8, z);
              mesh.add(cone);
            }
            scene.add(mesh);
            launch();
          }
          return true;
        }
        if (phase === 'turn') {
          // frena fuera de pantalla, da la vuelta y vuelve a por ti
          Effects.sustainShake(0.04);
          if (t > 1.1) {
            phase = 'run';
            dir = -dir;
            pass = 2;
            UI.toast('¡VUELVE A POR TI!');
            AudioFX.warn();
            launch();
          }
          return true;
        }
        // phase run
        x += 13.5 * dir * dt;
        mesh.position.x = x;
        Effects.sustainShake(0.15);
        Effects.sustainRoll(Math.sin(t * 30) * 0.012);
        for (const w of mesh.userData.wheels) w.rotation.z -= dir * 16 * dt;
        fireT -= dt;
        if (fireT <= 0) {
          fireT = 0.05;
          for (const ex of mesh.userData.exhausts) {
            const p = wp(ex); p.y += 0.5;
            Effects.fire(p, 2);
          }
          Effects.sparks(new THREE.Vector3(x + dir * 3.1, 0.12, -row), 4);
          Effects.dust(new THREE.Vector3(x - dir * 3, 0.1, -row + (Math.random() - 0.5) * 1.2));
        }
        // deja el asfalto en llamas
        if (Math.abs(x - lastTrailX) > 0.7 && Math.abs(x) < CONFIG.spawnX) {
          lastTrailX = x;
          trail.push({ x: x - dir * 2.5, row, ttl: 2.2 });
          if (trail.length > 36) trail.shift();
        }
        skidT -= dt;
        if (skidT <= 0) { skidT = 0.3; Effects.skid(new THREE.Vector3(x - dir * 2, 0, -row)); }
        const c = Math.round(x);
        for (let dr = -1; dr <= 1; dr++) {
          for (let dc = -3; dc <= 3; dc++) World.crushObstacle(row + dr, c + dc);
        }
        World.knockVehicles(row, x + dir * 3, 2.4, dir);
        if (Player.state === 'alive' &&
            Math.abs(Player.rowF - row) <= 0.9 &&
            Math.abs(Player.x - x) < mesh.userData.len / 2 + 0.4) {
          Game.slowmoHit(0.35);
          Game.die('supertruck');
        }
        if (Math.abs(x) > CONFIG.spawnX + 11) {
          Effects.shockwave(new THREE.Vector3(x, 0, -row), 0xff5a3c);
          if (pass === 1 && Game.state === 'playing') {
            phase = 'turn';
            t = 0;
            AudioFX.horn();
            return true;
          }
          scene.remove(mesh);
          disposeAll(mesh);
          phase = 'burnout';
          return trail.length > 0;
        }
        return true;
      },
      abort() { if (mesh) scene.remove(mesh); },
    });
  }

  /* ============ 🌋 VOLCÁN (con mega-bomba final y ceniza) ============ */
  function volcano(user) {
    UI.banner('🌋 ¡VOLCÁN!', user, '#ff8a3c');
    UI.warn();
    AudioFX.warn();
    AudioFX.rumble(8);
    const col = -2 + ((Math.random() * 5) | 0);
    const baseRow = Player.row + 7;
    const basePos = new THREE.Vector3(col, 0, -baseRow);
    const mesh = Models.volcano();
    mesh.position.set(col, -3.6, -baseRow);
    const light = new THREE.PointLight(0xff5a1a, 0, 10);
    light.position.set(col, 4, -baseRow);
    let phase = 'cracks', t = 0, bombT = 0, eruptT = 6.5, crackT = 0, smokeT = 0;
    let marker = null, mega = null;
    const bombs = [];
    add({
      major: true,
      update(dt) {
        t += dt;
        if (phase === 'cracks') {
          Game.setStorm(0.25);
          Effects.sustainShake(0.07);
          crackT -= dt;
          if (crackT <= 0) {
            crackT = 0.18;
            const a = Math.random() * Math.PI * 2;
            const r = Math.random() * 2.5;
            Effects.crack(new THREE.Vector3(col + Math.cos(a) * r, 0, -baseRow + Math.sin(a) * r), a, 1.5 + Math.random() * 2, true);
            Effects.dust(new THREE.Vector3(col + Math.cos(a) * r, 0.1, -baseRow + Math.sin(a) * r));
          }
          if (t > 1.6) {
            phase = 'rise';
            scene.add(mesh); scene.add(light);
            Effects.explosion(basePos, 1.6);
            Effects.shockwave(basePos);
            Effects.setAsh(true);
            AudioFX.boom();
            Game.punch(0.15);
            for (let i = 0; i < 3; i++) {
              const b = Models.boulder();
              b.position.copy(basePos);
              Effects.tumble(b, new THREE.Vector3((Math.random() - 0.5) * 8, 7 + Math.random() * 4, (Math.random() - 0.5) * 8));
            }
            Player.forcePush(3, 0.32);
            UI.toast('¡La erupción te empuja hacia atrás!');
          }
          return true;
        }
        if (phase === 'rise') {
          mesh.position.y = Math.min(0, mesh.position.y + 6 * dt);
          light.intensity = Math.min(1.4, light.intensity + 2.5 * dt);
          Effects.sustainShake(0.12);
          if (mesh.position.y >= 0) phase = 'erupt';
          return true;
        }
        if (phase === 'erupt') {
          eruptT -= dt;
          Effects.sustainShake(0.09);
          light.intensity = 1.1 + Math.sin(performance.now() / 80) * 0.5;
          smokeT -= dt;
          if (smokeT <= 0) {
            smokeT = 0.07;
            Effects.smoke(new THREE.Vector3(col + (Math.random() - 0.5) * 0.4, mesh.userData.craterY, -baseRow), 1, Math.random() < 0.5);
            if (Math.random() < 0.4) Effects.fire(new THREE.Vector3(col, mesh.userData.craterY, -baseRow), 2);
          }
          bombT -= dt;
          if (bombT <= 0) {
            bombT = 0.36;
            const b = Models.lavaBomb();
            const from = new THREE.Vector3(col, mesh.userData.craterY, -baseRow);
            const tr = Math.round(Player.row - 2 + Math.random() * 8);
            const tc = -CONFIG.cols + ((Math.random() * (CONFIG.cols * 2 + 1)) | 0);
            const to = new THREE.Vector3(tc, Math.max(0, World.topY(tr)), -tr);
            b.position.copy(from);
            scene.add(b);
            bombs.push({ mesh: b, from, to, t: 0, dur: 0.9, trailT: 0 });
          }
          if (eruptT <= 0) {
            // GRAN FINAL: mega-bomba teledirigida
            phase = 'aim';
            t = 0;
            marker = Models.targetMarker();
            scene.add(marker);
            AudioFX.alarm();
            UI.toast('☄️ ¡MEGA BOMBA!');
          }
        } else if (phase === 'aim') {
          const p = Player.mesh.position;
          marker.position.set(Math.round(p.x), Math.max(0, World.topY(Player.row)) + 0.12, Math.round(p.z));
          marker.scale.setScalar(1.4 + Math.sin(performance.now() / 60) * 0.3);
          Effects.sustainShake(0.08);
          if (t > 1.1) {
            phase = 'mega';
            t = 0;
            mega = Models.boulder();
            mega.scale.setScalar(1.9);
            mega.userData.from = new THREE.Vector3(col, mesh.userData.craterY, -baseRow);
            mega.userData.to = marker.position.clone().setY(Math.max(0, marker.position.y - 0.1));
            mega.position.copy(mega.userData.from);
            scene.add(mega);
            AudioFX.boom();
          }
        } else if (phase === 'mega') {
          const k = Math.min(1, t / 1.1);
          mega.position.lerpVectors(mega.userData.from, mega.userData.to, k);
          mega.position.y += Math.sin(k * Math.PI) * 6.5;
          mega.rotation.x += 5 * dt; mega.rotation.z += 4 * dt;
          if (Math.random() < dt * 20) Effects.fire(mega.position.clone(), 2);
          if (k >= 1) {
            const ip = mega.userData.to.clone();
            Game.slowmoHit(0.4);
            Game.punch(0.2);
            UI.flashWhite();
            AudioFX.boom(); AudioFX.thunder();
            Effects.explosion(ip, 2.0);
            Effects.shockwave(ip);
            Effects.lavaPool(ip, 1.0);
            Effects.scorch(ip, 1.2);
            Effects.shake(0.5, 0.6);
            if (Player.state === 'alive' &&
                Math.abs(Player.x - ip.x) < 1.35 && Math.abs(-Player.rowF - ip.z) < 1.35) {
              Game.die('volcano');
            }
            scene.remove(mega); disposeAll(mega);
            scene.remove(marker); marker.geometry.dispose(); marker.material.dispose();
            marker = null; mega = null;
            phase = 'collapse';
            Effects.shockwave(basePos, 0xd8cfa8);
            Effects.smoke(basePos.clone().setY(1.5), 10, true);
          }
        } else if (phase === 'collapse') {
          mesh.position.y -= 3 * dt;
          light.intensity = Math.max(0, light.intensity - 2 * dt);
          Game.setStorm(0);
          Effects.setAsh(false);
          if (mesh.position.y < -3.7 && bombs.length === 0) {
            scene.remove(mesh); scene.remove(light);
            disposeAll(mesh);
            return false;
          }
        }
        for (let i = bombs.length - 1; i >= 0; i--) {
          const b = bombs[i];
          b.t += dt;
          const k = Math.min(1, b.t / b.dur);
          b.mesh.position.lerpVectors(b.from, b.to, k);
          b.mesh.position.y += Math.sin(k * Math.PI) * 4.5;
          b.mesh.rotation.x += 6 * dt; b.mesh.rotation.z += 5 * dt;
          b.trailT -= dt;
          if (b.trailT <= 0) { b.trailT = 0.06; Effects.fire(b.mesh.position.clone(), 1); }
          if (k >= 1) {
            const p = b.to.clone();
            Effects.explosion(p, 0.9);
            Effects.lavaPool(p, 0.45 + Math.random() * 0.2);
            Effects.fire(p, 4);
            AudioFX.boom();
            Effects.shake(0.15, 0.25);
            if (Player.state === 'alive' &&
                Math.abs(Player.x - p.x) < 0.85 && Math.abs(-Player.rowF - p.z) < 0.85) {
              Game.die('volcano');
            }
            scene.remove(b.mesh);
            disposeAll(b.mesh);
            bombs.splice(i, 1);
          }
        }
        return true;
      },
      abort() {
        Game.setStorm(0); Effects.setAsh(false);
        scene.remove(mesh); scene.remove(light);
        if (marker) scene.remove(marker);
        if (mega) scene.remove(mega);
        for (const b of bombs) scene.remove(b.mesh);
      },
    });
  }

  /* ============ 🫨 TERREMOTO (fisuras, hundimiento y réplica) ============ */
  function earthquake(user) {
    UI.banner('🫨 ¡TERREMOTO!', user, '#d8b23c');
    UI.warn();
    AudioFX.warn();
    AudioFX.rumble(4.5);
    let phase = 'main', t = 6, hopT = 0.7, hops = 4, dustT = 0, fissureX = -7, rumbleT = 3, toppleT = 0.5, boulderT = 0.6;
    const fissureRows = [Player.row + 2, Player.row - 1];
    const boulders = [];
    const falling = [];
    // un carril entero se hunde durante el temblor
    const sinkLane = World.laneAt(Player.row + 1);
    const canSink = sinkLane && sinkLane.type !== 'water';
    let elapsed = 0;

    function spawnBoulder() {
      const m = Models.boulder();
      const side = Math.random() < 0.5 ? 1 : -1;
      const b = { mesh: m, x: -side * 13, y: 3, row: Player.row + (Math.random() * 4 - 2), vx: side * (4.5 + Math.random() * 2), vy: 0 };
      m.position.set(b.x, b.y, -b.row);
      scene.add(m);
      boulders.push(b);
    }

    add({
      major: true,
      update(dt) {
        elapsed += dt;
        const now = performance.now();

        // rocas rebotando (activas en todas las fases)
        for (let i = boulders.length - 1; i >= 0; i--) {
          const b = boulders[i];
          b.vy -= 16 * dt;
          b.y += b.vy * dt;
          b.x += b.vx * dt;
          b.mesh.rotation.z -= b.vx * dt * 1.4;
          const ground = Math.max(0, World.topY(Math.round(b.row))) + 0.45;
          if (b.y < ground && b.vy < 0) {
            b.y = ground;
            b.vy = 6.5 + Math.random() * 2;
            AudioFX.boom();
            Effects.shake(0.2, 0.2);
            Effects.dust(new THREE.Vector3(b.x, 0.2, -b.row));
            Effects.crack(new THREE.Vector3(b.x, 0, -b.row), Math.random(), 1.5);
            World.crushObstacle(Math.round(b.row), Math.round(b.x));
          }
          b.mesh.position.set(b.x, b.y, -b.row);
          if (Player.state === 'alive' &&
              Math.abs(Player.x - b.x) < 0.9 && Math.abs(Player.rowF - b.row) < 0.9 && b.y < 1.2) {
            Game.slowmoHit(0.3);
            Game.die('boulder');
          }
          if (Math.abs(b.x) > 15) {
            scene.remove(b.mesh); disposeAll(b.mesh);
            boulders.splice(i, 1);
          }
        }
        // árboles cayendo
        for (let i = falling.length - 1; i >= 0; i--) {
          const f = falling[i];
          f.t += dt;
          f.mesh.rotation.z = f.dir * Math.min(Math.PI / 2, f.t * f.t * 4);
          if (f.t > 0.8) {
            const p = wp(f.mesh);
            Effects.poof(p, 0x3cb04e);
            Effects.dust(p);
            f.mesh.parent && f.mesh.parent.remove(f.mesh);
            disposeAll(f.mesh);
            falling.splice(i, 1);
          }
        }

        if (phase === 'main') {
          t -= dt;
          const k = Math.min(1, t / 2 + 0.25);
          Game.setStorm(0.2);
          Effects.sustainShake(0.26 * k);
          Effects.sustainRoll(Math.sin(now / 120) * 0.05 * k);
          World.setQuake(k * 1.25);
          // carril que se hunde con la sacudida
          if (canSink) sinkLane.sinkY = -0.5 * Math.sin(Math.min(1, elapsed / 1.5) * Math.PI * 0.5) * k;
          rumbleT -= dt;
          if (rumbleT <= 0) { rumbleT = 99; AudioFX.rumble(3); }
          if (fissureX < 7) {
            fissureX += 9 * dt;
            for (const fr of fissureRows) {
              if (Math.random() < 0.5) {
                Effects.crack(new THREE.Vector3(fissureX, World.topY(fr), -fr + (Math.random() - 0.5) * 0.5), (Math.random() - 0.5) * 0.5, 1.2 + Math.random());
                Effects.dust(new THREE.Vector3(fissureX, 0.1, -fr));
              }
            }
          }
          dustT -= dt;
          if (dustT <= 0) {
            dustT = 0.18;
            const p = playerPos();
            p.x += (Math.random() - 0.5) * 8;
            p.z += (Math.random() - 0.5) * 8;
            p.y = 0.1;
            Effects.dust(p);
          }
          toppleT -= dt;
          if (toppleT <= 0) {
            toppleT = 0.6;
            const fr = Math.round(Player.row + (Math.random() - 0.5) * 10);
            const lane = World.laneAt(fr);
            if (lane && lane.obstacles && lane.obstacles.size) {
              const cols = Array.from(lane.obstacles.keys());
              const c = cols[(Math.random() * cols.length) | 0];
              falling.push({ mesh: lane.obstacles.get(c), row: fr, col: c, t: 0, dir: Math.random() < 0.5 ? 1 : -1 });
              lane.obstacles.delete(c);
            }
          }
          boulderT -= dt;
          if (boulderT <= 0 && boulders.length < 4) { boulderT = 1.1; spawnBoulder(); }
          hopT -= dt;
          if (hopT <= 0 && hops > 0 && Player.state === 'alive') {
            Player.tryMove(0, -1, true);
            hops--;
            hopT = 1.1;
          }
          if (t <= 0) {
            phase = 'calm'; t = 1.0;
            if (canSink) sinkLane.sinkY = 0;
            Game.setStorm(0);
          }
        } else if (phase === 'calm') {
          // calma tensa... y RÉPLICA
          t -= dt;
          if (t <= 0) {
            phase = 'after'; t = 1.4;
            AudioFX.rumble(1.6);
            AudioFX.boom();
            UI.toast('¡RÉPLICA!');
            Game.punch(0.12);
            spawnBoulder();
          }
        } else if (phase === 'after') {
          t -= dt;
          Effects.sustainShake(0.3);
          Effects.sustainRoll(Math.sin(now / 90) * 0.06);
          World.setQuake(1.4);
          if (Math.random() < dt * 6) {
            const p = playerPos();
            p.x += (Math.random() - 0.5) * 7;
            p.z += (Math.random() - 0.5) * 7;
            p.y = 0.1;
            Effects.dust(p);
          }
          if (t <= 0 && boulders.length === 0) return false;
          if (t <= -3) { // por si una roca se queda dando vueltas
            for (const b of boulders) { scene.remove(b.mesh); disposeAll(b.mesh); }
            return false;
          }
        }
        return true;
      },
      abort() {
        Game.setStorm(0);
        if (canSink) sinkLane.sinkY = 0;
        for (const b of boulders) scene.remove(b.mesh);
      },
    });
  }

  /* ============ 🌪️ TORNADO (con tornado bebé) ============ */
  function tornado(user) {
    UI.banner('🌪️ ¡TORNADO!', user, '#9ab8d8');
    UI.warn();
    AudioFX.warn();
    AudioFX.wind();
    const mesh = Models.tornado();
    const baby = Models.tornado();
    baby.scale.setScalar(0.45);
    const dir = Math.random() < 0.5 ? 1 : -1;
    let x = -dir * (CONFIG.spawnX - 2);
    let rowF = Player.row;
    let babyX = -dir * (CONFIG.spawnX + 2), babyRow = Player.row - 2, babyPh = Math.random() * 6;
    let phase = 'sweep', carryT = 0, dustT = 0, windT = 2.2, suckT = 0, flashT = 1.5;
    const sucked = [];
    mesh.position.set(x, 0, -rowF);
    scene.add(mesh);
    scene.add(baby);
    Game.setStorm(0.4);
    Effects.setWind(dir);

    function spinFunnel(m, dt2, mult) {
      const layers = m.userData.layers;
      const now = performance.now();
      for (let i = 0; i < layers.length; i++) {
        layers[i].rotation.y += (i % 2 ? -1 : 1) * (3 + i) * dt2 * (mult || 1);
        layers[i].position.x = Math.sin(now / 260 + i * 0.55) * 0.05 * i;
        layers[i].position.z = Math.cos(now / 310 + i * 0.5) * 0.04 * i;
      }
      for (const d of m.userData.debris) {
        d.userData.ang += 4.5 * dt2;
        d.position.x = Math.cos(d.userData.ang) * d.userData.r;
        d.position.z = Math.sin(d.userData.ang) * d.userData.r;
      }
    }

    add({
      major: true,
      update(dt) {
        const now = performance.now();
        spinFunnel(mesh, dt, 1);
        spinFunnel(baby, dt, 1.6);
        // el bebé deambula haciendo el caos
        babyPh += dt;
        babyX += dir * 2.6 * dt;
        babyRow = rowF - 2.5 + Math.sin(babyPh * 1.3) * 2.5;
        baby.position.set(babyX, 0, -babyRow);
        World.crushObstacle(Math.round(babyRow), Math.round(babyX));
        if (Math.random() < dt * 4) Effects.dust(new THREE.Vector3(babyX, 0.1, -babyRow));
        // relámpagos lejanos
        flashT -= dt;
        if (flashT <= 0) { flashT = 1.2 + Math.random() * 1.4; UI.flashSoft(); }
        // anillo de polvo del grande
        dustT -= dt;
        if (dustT <= 0) {
          dustT = 0.1;
          const a = Math.random() * Math.PI * 2;
          Effects.dust(new THREE.Vector3(mesh.position.x + Math.cos(a) * 0.9, 0.1, mesh.position.z + Math.sin(a) * 0.9));
        }
        suckT -= dt;
        if (suckT <= 0) {
          suckT = 0.12;
          const colors = [0x2f9e41, 0xe8552d, 0xf0c229, 0x8a5a33];
          const m = Models.box(0.14, 0.14, 0.14, colors[(Math.random() * 4) | 0]);
          const a = Math.random() * Math.PI * 2;
          sucked.push({ mesh: m, a, r: 3.2, y: 0.1 });
          scene.add(m);
        }
        for (let i = sucked.length - 1; i >= 0; i--) {
          const s = sucked[i];
          s.a += 7 * dt; s.r -= 3.2 * dt; s.y += 3.4 * dt;
          s.mesh.position.set(mesh.position.x + Math.cos(s.a) * s.r, s.y, mesh.position.z + Math.sin(s.a) * s.r);
          if (s.r <= 0.2) { scene.remove(s.mesh); s.mesh.geometry.dispose(); sucked.splice(i, 1); }
        }
        windT -= dt;
        if (windT <= 0) { windT = 2.2; AudioFX.wind(); }
        World.crushObstacle(Math.round(rowF), Math.round(x));
        World.knockVehicles(Math.round(rowF), mesh.position.x, 1.7, dir);

        if (phase === 'sweep') {
          x += dir * 3.4 * dt;
          rowF += (Player.rowF - rowF) * Math.min(1, 1.2 * dt);
          mesh.position.set(x, 0, -rowF);
          Effects.sustainShake(0.06);
          Effects.sustainRoll(Math.sin(now / 200) * 0.02);
          if (Player.state === 'alive' &&
              Math.abs(Player.x - x) < 1.1 && Math.abs(Player.rowF - rowF) < 1.1) {
            phase = 'carry';
            carryT = 3.0;
            Player.setCarried();
            AudioFX.eagle();
            Game.punch(0.12);
            Game.slowmoHit(0.3);
            UI.toast('¡El tornado te arrastra!');
          }
          if (Math.abs(x) > CONFIG.spawnX + 4) return this.finish();
        } else if (phase === 'carry') {
          carryT -= dt;
          rowF -= 3.0 * dt;
          x += Math.sin(now / 150) * 2 * dt;
          x = Math.max(-CONFIG.cols, Math.min(CONFIG.cols, x));
          mesh.position.set(x, 0, -rowF);
          const lift = 1.6 + Math.sin(now / 180) * 0.7;
          Player.mesh.position.set(x + Math.cos(now / 110) * 0.6, lift, -rowF + Math.sin(now / 110) * 0.6);
          Player.mesh.rotation.y += 14 * dt;
          Player.mesh.rotation.z = Math.sin(now / 90) * 0.4;
          Player.rowF = rowF; Player.x = x;
          Effects.sustainShake(0.11);
          Effects.sustainRoll(Math.sin(now / 150) * 0.035);
          if (carryT <= 0) {
            phase = 'exit';
            let dropRow = Math.max(0, Math.round(rowF));
            let tries = 0;
            while (tries < 6) {
              const lane = World.laneAt(dropRow);
              if (lane && lane.type === 'grass') break;
              dropRow--; tries++;
              if (dropRow < 0) { dropRow = 0; break; }
            }
            const dropCol = Math.max(-CONFIG.cols, Math.min(CONFIG.cols, Math.round(x)));
            World.crushObstacle(dropRow, dropCol);
            Player.mesh.rotation.z = 0;
            Player.releaseCarried(dropRow, dropCol);
            Effects.dust(playerPos());
            UI.toast('El tornado te soltó ' + Math.max(0, Game.score - dropRow) + ' filas atrás');
          }
        } else if (phase === 'exit') {
          x += dir * 5 * dt;
          rowF -= 1.5 * dt;
          mesh.position.set(x, 0, -rowF);
          mesh.position.y += 0.5 * dt;
          if (Math.abs(x) > CONFIG.spawnX + 4) return this.finish();
        }
        return true;
      },
      finish() {
        // al irse, llueven los escombros que se llevó
        for (let i = 0; i < 12; i++) {
          const colors = [0x2f9e41, 0x8a5a33, 0x9a9aa4, 0xe8552d];
          const m = Models.box(0.16, 0.16, 0.16, colors[i % 4]);
          m.position.set(Player.x + (Math.random() - 0.5) * 8, 6 + Math.random() * 3, -Player.rowF + (Math.random() - 0.5) * 8);
          Effects.tumble(m, new THREE.Vector3((Math.random() - 0.5) * 2, -2, (Math.random() - 0.5) * 2));
        }
        Game.setStorm(0);
        Effects.setWind(0);
        for (const s of sucked) scene.remove(s.mesh);
        scene.remove(mesh); disposeAll(mesh);
        scene.remove(baby); disposeAll(baby);
        return false;
      },
      abort() {
        Game.setStorm(0); Effects.setWind(0);
        for (const s of sucked) scene.remove(s.mesh);
        scene.remove(mesh); scene.remove(baby);
        if (Player.state === 'carried') Player.releaseCarried(Math.max(0, Math.round(rowF)), 0);
      },
    });
  }

  /* ============ 🛸 OVNI (flota, abduce un árbol primero) ============ */
  function ufo(user) {
    UI.banner('🛸 ¡OVNI!', user, '#7fe7ff');
    UI.warn();
    AudioFX.warn();
    AudioFX.ufo();
    UI.setGlitch(true);
    const mesh = Models.ufo();
    const scouts = [Models.ufo(), Models.ufo()];
    for (const s of scouts) { s.scale.setScalar(0.35); scene.add(s); }
    let phase = 'scan', t = 0, liftT = 0, abductPos = null, soundT = 1.4, suckT = 0, orbitA = 0;
    let treeUp = null, treeTried = false;
    const pp = playerPos();
    mesh.position.set(pp.x + 5, 8.5, pp.z + 3);
    scouts[0].position.copy(mesh.position).add(new THREE.Vector3(2, 0.5, 0));
    scouts[1].position.copy(mesh.position).add(new THREE.Vector3(-2, 0.8, 1));
    scene.add(mesh);
    Game.setStorm(0.8);
    const sucked = [];
    add({
      major: true,
      update(dt) {
        t += dt;
        const now = performance.now();
        for (const l of mesh.userData.lights) {
          l.material.emissiveIntensity = 0.5 + Math.sin(now / 100 + l.position.x * 7) * 0.5;
        }
        mesh.rotation.y += 1.6 * dt;
        // drones escolta orbitando
        for (let i = 0; i < scouts.length; i++) {
          const s = scouts[i];
          const a = now / 400 + i * Math.PI;
          if (phase === 'scan' || phase === 'descend') {
            s.position.set(mesh.position.x + Math.cos(a) * 2.4, mesh.position.y + 0.6 + Math.sin(a * 2) * 0.3, mesh.position.z + Math.sin(a) * 2.4);
            s.rotation.y += 4 * dt;
          } else {
            // cuando empieza la abducción, los drones se largan
            s.position.y += 7 * dt;
            s.position.x += (i ? 6 : -6) * dt;
          }
        }
        soundT -= dt;
        if (soundT <= 0) { soundT = 1.4; AudioFX.ufo(); }
        const target = Player.mesh.position;

        if (phase === 'scan') {
          orbitA += 1.1 * dt;
          const goal = new THREE.Vector3(target.x + Math.cos(orbitA) * 4, 7.5, target.z + Math.sin(orbitA) * 4);
          mesh.position.lerp(goal, Math.min(1, 3 * dt));
          mesh.userData.search.material.opacity = Math.min(0.30, mesh.userData.search.material.opacity + dt * 0.5);
          // primero se lleva un árbol de muestra
          if (t > 0.8 && !treeTried) {
            treeTried = true;
            for (let dr = -1; dr <= 5 && !treeUp; dr++) {
              const lane = World.laneAt(Player.row + dr);
              if (lane && lane.obstacles && lane.obstacles.size) {
                const cols = Array.from(lane.obstacles.keys());
                const c = cols[(Math.random() * cols.length) | 0];
                const m = lane.obstacles.get(c);
                const world = wp(m);
                lane.obstacles.delete(c);
                lane.group.remove(m);
                m.position.copy(world);
                scene.add(m);
                treeUp = { mesh: m };
                AudioFX.beam();
              }
            }
          }
          if (treeUp) {
            const m = treeUp.mesh;
            m.position.y += 2.4 * dt;
            m.rotation.y += 6 * dt;
            m.scale.multiplyScalar(Math.max(0.3, 1 - 0.8 * dt));
            m.position.x += (mesh.position.x - m.position.x) * Math.min(1, 2 * dt);
            m.position.z += (mesh.position.z - m.position.z) * Math.min(1, 2 * dt);
            if (m.position.y > mesh.position.y - 0.6) {
              Effects.sparkle(m.position.clone());
              scene.remove(m); disposeAll(m);
              treeUp = null;
            }
          }
          if (t > 2.6) phase = 'descend';
        } else if (phase === 'descend') {
          mesh.userData.search.material.opacity = Math.max(0, mesh.userData.search.material.opacity - dt * 1.2);
          const goal = new THREE.Vector3(target.x, target.y + 3.4, target.z);
          mesh.position.lerp(goal, Math.min(1, 2.6 * dt));
          if (mesh.position.distanceTo(goal) < 0.35) {
            phase = 'beam';
            liftT = 1.7;
            AudioFX.beam();
            abductPos = target.clone();
            Game.punch(0.10);
            Game.slowmoHit(0.25);
            if (Player.state === 'alive') Player.setCarried();
          }
        } else if (phase === 'beam') {
          const beam = mesh.userData.beam;
          const glow = mesh.userData.glow;
          beam.material.opacity = Math.min(0.45, beam.material.opacity + dt * 1.2);
          glow.material.opacity = Math.min(0.5, glow.material.opacity + dt * 1.2) * (0.7 + Math.sin(now / 90) * 0.3);
          suckT -= dt;
          if (suckT <= 0) {
            suckT = 0.09;
            const colors = [0x2f9e41, 0xf0c229, 0x9a9aa4, 0xd94f8a];
            const m = Models.box(0.12, 0.12, 0.12, colors[(Math.random() * 4) | 0]);
            m.position.set(mesh.position.x + (Math.random() - 0.5) * 1.6, 0.1, mesh.position.z + (Math.random() - 0.5) * 1.6);
            scene.add(m);
            sucked.push({ mesh: m, sp: 2.5 + Math.random() * 2 });
          }
          liftT -= dt;
          if (Player.state === 'carried') {
            Player.mesh.position.x += (mesh.position.x - Player.mesh.position.x) * Math.min(1, 4 * dt);
            Player.mesh.position.z += (mesh.position.z - Player.mesh.position.z) * Math.min(1, 4 * dt);
            Player.mesh.position.y += 1.8 * dt;
            Player.mesh.rotation.y += 9 * dt;
            Player.mesh.scale.multiplyScalar(Math.max(0.4, 1 - 0.4 * dt));
          }
          if (liftT <= 0) {
            phase = 'leave';
            mesh.userData.beam.material.opacity = 0;
            mesh.userData.glow.material.opacity = 0;
            if (abductPos) {
              Effects.scorch(new THREE.Vector3(abductPos.x, 0, abductPos.z), 0.9);
              Effects.shockwave(new THREE.Vector3(abductPos.x, 0, abductPos.z), 0x7fe7ff);
            }
            if (Player.state === 'carried') {
              Player.state = 'dead';
              Game.die('ufo', { alreadyDead: true });
            }
          }
        } else if (phase === 'leave') {
          mesh.position.y += 10 * dt;
          mesh.position.x += 9 * dt;
          mesh.scale.x = Math.min(2.6, mesh.scale.x + 5 * dt);
          mesh.scale.y = Math.max(0.4, mesh.scale.y - 1.5 * dt);
          if (Player.state === 'dead' && Player.mesh.visible) {
            Player.mesh.position.copy(mesh.position).add(new THREE.Vector3(0, -0.8, 0));
          }
          if (mesh.position.y > 16) {
            UI.flashWhite();
            UI.setGlitch(false);
            Player.mesh.visible = false;
            Game.setStorm(0);
            if (treeUp) scene.remove(treeUp.mesh);
            for (const s of sucked) scene.remove(s.mesh);
            for (const s of scouts) { scene.remove(s); disposeAll(s); }
            scene.remove(mesh); disposeAll(mesh);
            return false;
          }
        }
        for (let i = sucked.length - 1; i >= 0; i--) {
          const s = sucked[i];
          s.mesh.position.y += s.sp * dt;
          s.mesh.rotation.y += 6 * dt;
          s.mesh.position.x += (mesh.position.x - s.mesh.position.x) * Math.min(1, 2 * dt);
          s.mesh.position.z += (mesh.position.z - s.mesh.position.z) * Math.min(1, 2 * dt);
          if (s.mesh.position.y > mesh.position.y - 0.4) {
            scene.remove(s.mesh); s.mesh.geometry.dispose();
            sucked.splice(i, 1);
          }
        }
        return true;
      },
      abort() {
        UI.setGlitch(false);
        Game.setStorm(0);
        if (treeUp) scene.remove(treeUp.mesh);
        for (const s of sucked) scene.remove(s.mesh);
        for (const s of scouts) scene.remove(s);
        scene.remove(mesh);
      },
    });
  }

  /* ============ ⚡ TORMENTA (triple rayo perseguidor) ============ */
  function lightning(user) {
    UI.banner('⚡ ¡TORMENTA!', user, '#ffe14d');
    UI.warn();
    AudioFX.warn();
    AudioFX.alarm();
    const marker = Models.targetMarker();
    scene.add(marker);
    let phase = 'preludio', t = 0, strikes = 3, chaseT = 0, flashT = 0.8;
    let preStrikes = [0.35, 0.7, 1.05, 1.4];
    const stray = [];
    Game.setStorm(1);
    Effects.setRain(true);
    marker.visible = false;

    function strikeAt(pos, killRadius, big) {
      const b = Models.lightningBolt();
      b.position.copy(pos);
      scene.add(b);
      stray.push({ mesh: b, t: 0.22 });
      AudioFX.thunder();
      Effects.explosion(pos, big ? 1.1 : 0.8);
      Effects.scorch(pos, big ? 0.65 : 0.5);
      Effects.fire(pos, big ? 6 : 4);
      Effects.shake(big ? 0.4 : 0.28, 0.35);
      UI.flashWhite();
      if (big) Game.punch(0.13);
      if (killRadius && Player.state === 'alive' &&
          Math.abs(Player.mesh.position.x - pos.x) < killRadius &&
          Math.abs(Player.mesh.position.z - pos.z) < killRadius) {
        Game.slowmoHit(0.3);
        Game.die('lightning');
      }
    }

    add({
      major: true,
      update(dt) {
        t += dt;
        // relámpagos ambiente
        flashT -= dt;
        if (flashT <= 0) { flashT = 0.9 + Math.random() * 1.2; UI.flashSoft(); }
        for (let i = stray.length - 1; i >= 0; i--) {
          stray[i].t -= dt;
          if (stray[i].t <= 0) {
            scene.remove(stray[i].mesh);
            disposeAll(stray[i].mesh);
            stray.splice(i, 1);
          }
        }
        if (phase === 'preludio') {
          if (preStrikes.length && t >= preStrikes[0]) {
            preStrikes.shift();
            const p = playerPos();
            p.x = Math.max(-CONFIG.cols, Math.min(CONFIG.cols, p.x + (Math.random() - 0.5) * 6));
            p.z += (Math.random() - 0.5) * 6;
            p.y = 0;
            strikeAt(p, 0.9, false);
          }
          if (t > 1.7) { phase = 'track'; t = 0; marker.visible = true; }
        } else if (phase === 'track') {
          const p = Player.mesh.position;
          marker.position.set(Math.round(p.x), Math.max(0, World.topY(Player.row)) + 0.12, Math.round(p.z));
          marker.scale.setScalar(1 + Math.sin(performance.now() / 90) * 0.15);
          if (t > 1.0) { phase = 'chase'; chaseT = 0.15; }
        } else if (phase === 'chase') {
          // TRES rayos seguidos que te persiguen
          const p = Player.mesh.position;
          marker.position.set(Math.round(p.x), Math.max(0, World.topY(Player.row)) + 0.12, Math.round(p.z));
          marker.scale.setScalar(1 + Math.sin(performance.now() / 40) * 0.25);
          chaseT -= dt;
          if (chaseT <= 0) {
            strikes--;
            strikeAt(marker.position.clone(), 0.95, strikes === 0);
            chaseT = 0.55;
            marker.material.color.setHex(strikes === 1 ? 0xffffff : 0xff2222);
            if (strikes <= 0) { phase = 'calma'; t = 0; marker.visible = false; }
          }
        } else if (phase === 'calma') {
          if (t > 0.8) {
            Game.setStorm(0);
            Effects.setRain(false);
            scene.remove(marker); marker.geometry.dispose(); marker.material.dispose();
            for (const s of stray) { scene.remove(s.mesh); disposeAll(s.mesh); }
            return false;
          }
        }
        return true;
      },
      abort() {
        Game.setStorm(0); Effects.setRain(false);
        scene.remove(marker);
        for (const s of stray) scene.remove(s.mesh);
      },
    });
  }

  /* ============ 💎 RESET (diamante con sombra creciente) ============ */
  function reset(user) {
    if (Game.state !== 'playing') return;
    UI.banner('💎 RESET', user, '#7fd8ff');
    UI.warn();
    AudioFX.warn();
    Game.setStorm(0.5);
    const gem = Models.diamond();
    const start = playerPos().add(new THREE.Vector3(0, 11, 0));
    gem.position.copy(start);
    scene.add(gem);
    // sombra que crece bajo el jugador
    const shadow = new THREE.Mesh(
      new THREE.CircleGeometry(1, 24),
      new THREE.MeshBasicMaterial({ color: 0x08202e, transparent: true, opacity: 0.15, depthWrite: false })
    );
    shadow.rotation.x = -Math.PI / 2;
    scene.add(shadow);
    let t = 0, sparkT = 0, done = false;
    add({
      update(dt) {
        if (done) return false;
        t += dt;
        const k = Math.min(1, t / 1.1);
        const p = Player.mesh.position;
        gem.position.x += (p.x - gem.position.x) * Math.min(1, 3 * dt);
        gem.position.z += (p.z - gem.position.z) * Math.min(1, 3 * dt);
        gem.position.y = 11 - k * k * 10.2;
        gem.rotation.y += 5 * dt;
        shadow.position.set(gem.position.x, 0.12, gem.position.z);
        shadow.scale.setScalar(0.3 + k * 1.1);
        shadow.material.opacity = 0.1 + k * 0.4;
        if (k > 0.75) Game.slowmoHit(0.15);
        sparkT -= dt;
        if (sparkT <= 0) { sparkT = 0.1; Effects.sparkle(gem.position.clone()); }
        if (k >= 1) {
          done = true;
          const ip = gem.position.clone();
          UI.flashWhite();
          AudioFX.boom();
          Effects.shockwave(ip, 0x7fd8ff);
          Effects.explosion(ip, 1.0);
          for (let i = 0; i < 14; i++) {
            const s = Models.box(0.16, 0.16, 0.16, 0x7fd8ff, 0, 0, 0, { transparent: true, opacity: 0.95 });
            s.position.copy(ip);
            Effects.tumble(s, new THREE.Vector3((Math.random() - 0.5) * 9, 3 + Math.random() * 6, (Math.random() - 0.5) * 9));
          }
          Game.punch(0.16);
          Game.setStorm(0);
          scene.remove(gem); disposeAll(gem);
          scene.remove(shadow); shadow.geometry.dispose(); shadow.material.dispose();
          Game.die('reset', { noShield: true });
          return false;
        }
        return true;
      },
      abort() { Game.setStorm(0); scene.remove(gem); scene.remove(shadow); },
    });
  }

  /* ============ 🍓 SAVE THE RUN ============ */
  function saveRun(user) {
    if (Player.shield >= CONFIG.maxShields) { UI.toast('Escudo al máximo'); return; }
    Player.shield++;
    AudioFX.shield();
    UI.banner('🍓 SAVE THE RUN', user, '#7fffa8');
    UI.toast('🛡️ Escudos: ' + Player.shield);
    const pillar = new THREE.Mesh(
      new THREE.CylinderGeometry(0.55, 0.75, 5, 14, 1, true),
      new THREE.MeshBasicMaterial({ color: 0xffe89a, transparent: true, opacity: 0.35, side: THREE.DoubleSide, depthWrite: false })
    );
    const halo = new THREE.Mesh(
      new THREE.RingGeometry(0.5, 0.75, 24),
      new THREE.MeshBasicMaterial({ color: 0xffd23b, transparent: true, opacity: 0.9, side: THREE.DoubleSide, depthWrite: false })
    );
    halo.rotation.x = -Math.PI / 2;
    scene.add(pillar); scene.add(halo);
    let t = 0, sparkT = 0;
    add({
      update(dt) {
        t += dt;
        const p = Player.mesh.position;
        pillar.position.set(p.x, 2.5, p.z);
        pillar.material.opacity = Math.max(0, 0.35 * (1 - t / 1.2));
        halo.position.set(p.x, 3.2 - t * 2.4, p.z);
        halo.rotation.z += 3 * dt;
        halo.material.opacity = Math.max(0, 0.9 * (1 - t / 1.3));
        sparkT -= dt;
        if (sparkT <= 0) { sparkT = 0.18; Effects.sparkle(p.clone()); }
        if (t > 1.3) {
          scene.remove(pillar); pillar.geometry.dispose(); pillar.material.dispose();
          scene.remove(halo); halo.geometry.dispose(); halo.material.dispose();
          return false;
        }
        return true;
      },
      abort() { scene.remove(pillar); scene.remove(halo); },
    });
  }

  function moveLeft(user, count) {
    for (let i = 0; i < Math.min(count || 1, 8); i++) Player.tryMove(-1, 0);
    UI.toast('🪽 MOVE LEFT' + (user ? ' — ' + user : ''));
  }

  function moveRight(user, count) {
    for (let i = 0; i < Math.min(count || 1, 8); i++) Player.tryMove(1, 0);
    UI.toast('💗 MOVE RIGHT' + (user ? ' — ' + user : ''));
  }

  const ACTIONS = { superTruck, volcano, earthquake, tornado, ufo, lightning, reset, saveRun, moveLeft, moveRight };

  return {
    init(sc) { scene = sc; },

    trigger(name, opts) {
      const fn = ACTIONS[name];
      if (!fn) return false;
      opts = opts || {};
      AudioFX.unlock();
      UI.bumpCounter(name);
      Game.wake();
      fn(opts.user, opts.count);
      // ¿se han juntado 2+ desastres grandes? ¡APOCALIPSIS!
      const majors = active.filter(a => a.major).length;
      if (majors >= 2) {
        let apoT = 0;
        add({
          update(dt) {
            apoT += dt;
            if (apoT > 1.4) {
              UI.banner('☠️ ¡APOCALIPSIS!', null, '#ff4a4a');
              AudioFX.boom();
              Game.punch(0.18);
              Effects.shake(0.3, 0.6);
              return false;
            }
            return true;
          },
          abort() {},
        });
      }
      return true;
    },

    clear() {
      for (const d of active) { if (d.abort) d.abort(); }
      active.length = 0;
      Game.setStorm(0);
      Effects.setRain(false);
      Effects.setWind(0);
      Effects.setAsh(false);
      UI.setGlitch(false);
    },

    update(dt) {
      for (let i = active.length - 1; i >= 0; i--) {
        if (!active[i].update(dt)) active.splice(i, 1);
      }
    },
  };
})();
