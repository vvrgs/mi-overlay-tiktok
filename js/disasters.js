/* ============================================================
 * DESASTRES Y ACCIONES DE REGALOS
 *  - superTruck : un camión gigante barre la fila del jugador
 *  - volcano    : brota un volcán y llueven bombas de lava
 *  - earthquake : terremoto que te empuja hacia atrás
 *  - tornado    : te atrapa y te arrastra muchas filas atrás
 *  - ufo        : te abduce (reset)
 *  - lightning  : rayo teledirigido
 *  - reset / saveRun / moveLeft / moveRight
 * ============================================================ */
const Disasters = (() => {
  let scene = null;
  const active = [];

  function add(d) { active.push(d); }

  function playerPos() { return Player.mesh.position.clone(); }

  /* ---------- SUPER CAMIÓN ---------- */
  function superTruck(user) {
    UI.banner('🚛 ¡SUPER CAMIÓN!', user);
    UI.flashRed();
    AudioFX.horn();
    let warned = 1.4, mesh = null, x = 0, dir = 1, row = 0, done = false;
    add({
      update(dt) {
        if (warned > 0) {
          warned -= dt;
          Effects.sustainShake(0.06);
          if (warned <= 0) {
            row = Player.row;
            dir = Math.random() < 0.5 ? 1 : -1;
            mesh = Models.superTruck();
            mesh.rotation.y = dir > 0 ? 0 : Math.PI;
            x = -dir * (CONFIG.spawnX + 8);
            mesh.position.set(x, World.topY(row), -row);
            scene.add(mesh);
            AudioFX.trainPass();
          }
          return true;
        }
        x += 15 * dir * dt;
        mesh.position.x = x;
        Effects.sustainShake(0.12);
        // aplasta árboles y rocas a su paso
        for (let dr = -1; dr <= 1; dr++) {
          const c = Math.round(x);
          for (let dc = -2; dc <= 2; dc++) World.crushObstacle(row + dr, c + dc);
        }
        // aplasta al pollo
        if (Player.state === 'alive' &&
            Math.abs(Player.rowF - row) <= 0.9 &&
            Math.abs(Player.x - x) < mesh.userData.len / 2 + 0.4) {
          Game.die('supertruck');
        }
        if (Math.abs(x) > CONFIG.spawnX + 10) {
          scene.remove(mesh);
          mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
          done = true;
        }
        return !done;
      },
      abort() { if (mesh) { scene.remove(mesh); } },
    });
  }

  /* ---------- VOLCÁN ---------- */
  function volcano(user) {
    UI.banner('🌋 ¡VOLCÁN!', user);
    AudioFX.rumble(7);
    const col = -2 + ((Math.random() * 5) | 0);
    const baseRow = Player.row + 7;
    const mesh = Models.volcano();
    mesh.position.set(col, -3.4, -baseRow);
    scene.add(mesh);
    let phase = 'rise', t = 0, bombT = 0, eruptT = 6, pushed = false;
    const bombs = [];
    add({
      update(dt) {
        t += dt;
        Effects.sustainShake(phase === 'erupt' ? 0.10 : 0.05);
        if (phase === 'rise') {
          mesh.position.y = Math.min(0, -3.4 + t * 3.5);
          if (mesh.position.y >= 0) {
            phase = 'erupt';
            Effects.shockwave(new THREE.Vector3(col, 0, -baseRow));
            AudioFX.boom();
            if (!pushed) { pushed = true; Player.forcePush(3, 0.32); UI.toast('¡La erupción te empuja hacia atrás!'); }
          }
          return true;
        }
        if (phase === 'erupt') {
          eruptT -= dt;
          Effects.smoke(new THREE.Vector3(col, mesh.userData.craterY, -baseRow), 1);
          bombT -= dt;
          if (bombT <= 0) {
            bombT = 0.42;
            const b = Models.lavaBomb();
            const from = new THREE.Vector3(col, mesh.userData.craterY, -baseRow);
            const tr = Math.round(Player.row - 2 + Math.random() * 8);
            const tc = -CONFIG.cols + ((Math.random() * (CONFIG.cols * 2 + 1)) | 0);
            const to = new THREE.Vector3(tc, Math.max(0, World.topY(tr)), -tr);
            b.position.copy(from);
            scene.add(b);
            bombs.push({ mesh: b, from, to, t: 0, dur: 0.9 });
          }
          if (eruptT <= 0) phase = 'sink';
        } else if (phase === 'sink') {
          mesh.position.y -= 2.5 * dt;
          if (mesh.position.y < -3.5 && bombs.length === 0) {
            scene.remove(mesh);
            mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
            return false;
          }
        }
        // bombas en vuelo
        for (let i = bombs.length - 1; i >= 0; i--) {
          const b = bombs[i];
          b.t += dt;
          const k = Math.min(1, b.t / b.dur);
          b.mesh.position.lerpVectors(b.from, b.to, k);
          b.mesh.position.y += Math.sin(k * Math.PI) * 4.5;
          b.mesh.rotation.x += 6 * dt; b.mesh.rotation.z += 5 * dt;
          if (k >= 1) {
            const p = b.to.clone();
            Effects.explosion(p, 0.9);
            Effects.scorch(p, 0.5 + Math.random() * 0.25);
            AudioFX.boom();
            Effects.shake(0.15, 0.25);
            if (Player.state === 'alive' &&
                Math.abs(Player.x - p.x) < 0.85 && Math.abs(-Player.rowF - p.z) < 0.85) {
              Game.die('volcano');
            }
            scene.remove(b.mesh);
            b.mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
            bombs.splice(i, 1);
          }
        }
        return true;
      },
      abort() {
        scene.remove(mesh);
        for (const b of bombs) scene.remove(b.mesh);
      },
    });
  }

  /* ---------- TERREMOTO ---------- */
  function earthquake(user) {
    UI.banner('🫨 ¡TERREMOTO!', user);
    AudioFX.rumble(4.5);
    let t = 4.5, hopT = 0.6, hops = 4;
    add({
      update(dt) {
        t -= dt;
        Effects.sustainShake(0.22 * Math.min(1, t / 1.5 + 0.3));
        hopT -= dt;
        if (hopT <= 0 && hops > 0 && Player.state === 'alive') {
          Player.tryMove(0, -1, true);
          hops--;
          hopT = 0.9;
          if (Math.random() < 0.6) {
            const p = playerPos();
            p.x += (Math.random() - 0.5) * 3;
            p.z += (Math.random() - 0.5) * 3;
            Effects.scorch(p, 0.4);
          }
        }
        return t > 0;
      },
      abort() {},
    });
  }

  /* ---------- TORNADO ---------- */
  function tornado(user) {
    UI.banner('🌪️ ¡TORNADO!', user);
    AudioFX.wind();
    const mesh = Models.tornado();
    const dir = Math.random() < 0.5 ? 1 : -1;
    let x = -dir * (CONFIG.spawnX - 2);
    let rowF = Player.row;
    let phase = 'sweep', carryT = 0;
    mesh.position.set(x, 0, -rowF);
    scene.add(mesh);
    add({
      update(dt) {
        // animación del embudo
        const layers = mesh.userData.layers;
        for (let i = 0; i < layers.length; i++) {
          layers[i].rotation.y += (i % 2 ? -1 : 1) * (3 + i) * dt;
          layers[i].position.x = Math.sin(performance.now() / 300 + i) * 0.12;
        }
        for (const d of mesh.userData.debris) {
          d.userData.ang += 4 * dt;
          d.position.x = Math.cos(d.userData.ang) * d.userData.r;
          d.position.z = Math.sin(d.userData.ang) * d.userData.r;
        }

        if (phase === 'sweep') {
          x += dir * 3.4 * dt;
          rowF += (Player.rowF - rowF) * Math.min(1, 1.2 * dt);
          mesh.position.set(x, 0, -rowF);
          Effects.sustainShake(0.05);
          if (Player.state === 'alive' &&
              Math.abs(Player.x - x) < 1.1 && Math.abs(Player.rowF - rowF) < 1.1) {
            phase = 'carry';
            carryT = 2.4;
            Player.setCarried();
            AudioFX.eagle();
            UI.toast('¡El tornado te arrastra!');
          }
          if (Math.abs(x) > CONFIG.spawnX + 4) {
            scene.remove(mesh);
            mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
            return false;
          }
        } else if (phase === 'carry') {
          carryT -= dt;
          rowF -= 3.2 * dt;                       // arrastra hacia atrás
          x += Math.sin(performance.now() / 150) * 2 * dt;
          x = Math.max(-CONFIG.cols, Math.min(CONFIG.cols, x));
          mesh.position.set(x, 0, -rowF);
          // el pollo gira dentro del embudo
          Player.mesh.position.set(x + Math.cos(performance.now() / 120) * 0.5, 1.4 + Math.sin(performance.now() / 200) * 0.5, -rowF + Math.sin(performance.now() / 120) * 0.5);
          Player.mesh.rotation.y += 12 * dt;
          // que la cámara siga el arrastre
          Player.rowF = rowF; Player.x = x;
          Effects.sustainShake(0.10);
          if (carryT <= 0) {
            phase = 'exit';
            // suelta al pollo en la fila donde quedó (o la más cercana con pasto)
            let dropRow = Math.max(0, Math.round(rowF));
            let tries = 0;
            while (tries < 6) {
              const lane = World.laneAt(dropRow);
              if (lane && lane.type === 'grass') break;
              dropRow--; tries++;
              if (dropRow < 0) { dropRow = 0; break; }
            }
            let dropCol = Math.max(-CONFIG.cols, Math.min(CONFIG.cols, Math.round(x)));
            World.crushObstacle(dropRow, dropCol);
            Player.releaseCarried(dropRow, dropCol);
            UI.toast('El tornado te soltó ' + Math.max(0, Game.score - dropRow) + ' filas atrás');
          }
        } else if (phase === 'exit') {
          x += dir * 5 * dt;
          rowF -= 1.5 * dt;
          mesh.position.set(x, 0, -rowF);
          mesh.position.y += 0.5 * dt;
          if (Math.abs(x) > CONFIG.spawnX + 4) {
            scene.remove(mesh);
            mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
            return false;
          }
        }
        return true;
      },
      abort() { scene.remove(mesh); if (Player.state === 'carried') Player.releaseCarried(Math.max(0, Math.round(rowF)), 0); },
    });
  }

  /* ---------- OVNI ---------- */
  function ufo(user) {
    UI.banner('🛸 ¡OVNI!', user);
    AudioFX.ufo();
    const mesh = Models.ufo();
    let phase = 'descend', t = 0, liftT = 0;
    const pp = playerPos();
    mesh.position.set(pp.x + 6, 9, pp.z + 4);
    scene.add(mesh);
    add({
      update(dt) {
        t += dt;
        for (const l of mesh.userData.lights) {
          l.material.emissiveIntensity = 0.5 + Math.sin(performance.now() / 100 + l.position.x * 7) * 0.5;
        }
        mesh.rotation.y += 1.5 * dt;
        const target = Player.mesh.position;

        if (phase === 'descend') {
          const goal = new THREE.Vector3(target.x, target.y + 3.4, target.z);
          mesh.position.lerp(goal, Math.min(1, 2.2 * dt));
          if (mesh.position.distanceTo(goal) < 0.35 && t > 1.2) {
            phase = 'beam';
            liftT = 1.6;
            AudioFX.beam();
            if (Player.state === 'alive') Player.setCarried();
          }
        } else if (phase === 'beam') {
          const beam = mesh.userData.beam;
          beam.material.opacity = Math.min(0.45, beam.material.opacity + dt * 1.2);
          mesh.position.x += (target.x - mesh.position.x) * 0; // se queda fijo
          liftT -= dt;
          if (Player.state === 'carried') {
            Player.mesh.position.x += (mesh.position.x - Player.mesh.position.x) * Math.min(1, 4 * dt);
            Player.mesh.position.z += (mesh.position.z - Player.mesh.position.z) * Math.min(1, 4 * dt);
            Player.mesh.position.y += 1.8 * dt;
            Player.mesh.rotation.y += 8 * dt;
            Player.mesh.scale.multiplyScalar(Math.max(0.4, 1 - 0.4 * dt));
          }
          if (liftT <= 0) {
            phase = 'leave';
            mesh.userData.beam.material.opacity = 0;
            if (Player.state === 'carried') {
              Player.state = 'dead';
              Game.die('ufo', { alreadyDead: true });
            }
          }
        } else if (phase === 'leave') {
          mesh.position.y += 8 * dt;
          mesh.position.x += 6 * dt;
          if (Player.state === 'dead' && Player.mesh.visible) {
            Player.mesh.position.copy(mesh.position).add(new THREE.Vector3(0, -0.8, 0));
          }
          if (mesh.position.y > 20) {
            Player.mesh.visible = false;
            scene.remove(mesh);
            mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
            return false;
          }
        }
        return true;
      },
      abort() { scene.remove(mesh); },
    });
  }

  /* ---------- RAYO ---------- */
  function lightning(user) {
    UI.banner('⚡ ¡RAYO!', user);
    AudioFX.alarm();
    const marker = Models.targetMarker();
    scene.add(marker);
    let phase = 'track', t = 1.2, lock = null, bolt = null, boltT = 0;
    add({
      update(dt) {
        if (phase === 'track') {
          t -= dt;
          const p = Player.mesh.position;
          marker.position.set(Math.round(p.x), Math.max(0, World.topY(Player.row)) + 0.12, Math.round(p.z));
          marker.scale.setScalar(1 + Math.sin(performance.now() / 90) * 0.15);
          if (t <= 0) {
            phase = 'lock'; t = 0.45;
            lock = marker.position.clone();
            marker.material.color.setHex(0xffffff);
          }
        } else if (phase === 'lock') {
          t -= dt;
          marker.scale.setScalar(1 + Math.sin(performance.now() / 40) * 0.25);
          if (t <= 0) {
            phase = 'strike';
            bolt = Models.lightningBolt();
            bolt.position.copy(lock);
            scene.add(bolt);
            boltT = 0.18;
            AudioFX.thunder();
            Effects.explosion(lock, 0.9);
            Effects.scorch(lock, 0.6);
            Effects.shake(0.35, 0.4);
            UI.flashWhite();
            if (Player.state === 'alive' &&
                Math.abs(Player.mesh.position.x - lock.x) < 0.95 &&
                Math.abs(Player.mesh.position.z - lock.z) < 0.95) {
              Game.die('lightning');
            }
          }
        } else if (phase === 'strike') {
          boltT -= dt;
          if (boltT <= 0) {
            scene.remove(marker); marker.geometry.dispose(); marker.material.dispose();
            scene.remove(bolt);
            bolt.traverse(o => { if (o.geometry) o.geometry.dispose(); });
            return false;
          }
        }
        return true;
      },
      abort() { scene.remove(marker); if (bolt) scene.remove(bolt); },
    });
  }

  /* ---------- Acciones simples ---------- */
  function reset(user) {
    if (Game.state !== 'playing') return;
    UI.banner('💎 RESET', user);
    Game.die('reset', { noShield: true });
  }

  function saveRun(user) {
    if (Player.shield >= CONFIG.maxShields) { UI.toast('Escudo al máximo'); return; }
    Player.shield++;
    AudioFX.shield();
    UI.banner('🍓 SAVE THE RUN', user);
    UI.toast('🛡️ Escudos: ' + Player.shield);
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
      fn(opts.user, opts.count);
      return true;
    },

    clear() {
      for (const d of active) { if (d.abort) d.abort(); }
      active.length = 0;
    },

    update(dt) {
      for (let i = active.length - 1; i >= 0; i--) {
        if (!active[i].update(dt)) active.splice(i, 1);
      }
    },
  };
})();
