/* ============================================================
 * MUNDO — generación infinita de carriles estilo Crossy Road.
 * v2: suelo casilla a casilla con variación de color, flores,
 * matas, monedas, nenúfares, espuma y destellos en el agua,
 * barreras de tren, taxis/policía/bus, nubes con sombra y
 * dificultad progresiva.
 * ============================================================ */
const World = (() => {
  const T = 1; // tamaño de casilla

  const COLORS = {
    grassA: 0xa5e34f,
    grassB: 0x8fd444,
    road: 0x47475a,
    rail: 0x6e6e76,
    water: 0x3fc9f7,
  };

  const TOP_Y = { grass: 0.08, road: 0.0, rail: 0.0, water: -0.18 };
  const LANE_H = { grass: 0.5, road: 0.42, rail: 0.42, water: 0.3 };

  let scene = null;
  let group = null;
  const lanes = new Map();
  let topRow = -1;
  let genQueue = [];
  let clouds = [];
  let quakeAmp = 0;

  const tileGeo = new THREE.BoxGeometry(1, 1, 1);

  function fullWidth() { return (CONFIG.cols * 2 + 1) + CONFIG.sideCols * 2; }

  function difficulty() {
    const s = (typeof Game !== 'undefined') ? Game.score : 0;
    return 1 + Math.min(0.55, s * 0.004);
  }

  /* Suelo casilla a casilla con jitter de color (1 draw call por carril) */
  function laneGround(type, row) {
    const w = fullWidth();
    const h = LANE_H[type];
    const top = TOP_Y[type];
    const baseHex = (type === 'grass') ? ((row % 2 === 0) ? COLORS.grassA : COLORS.grassB) : COLORS[type];
    const mesh = new THREE.InstancedMesh(tileGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), w);
    const m4 = new THREE.Matrix4();
    const col = new THREE.Color();
    let i = 0;
    for (let c = -(w - 1) / 2; c <= (w - 1) / 2; c++) {
      m4.makeScale(1, h, 1);
      m4.setPosition(c, top - h / 2, 0);
      mesh.setMatrixAt(i, m4);
      col.setHex(baseHex);
      const inside = Math.abs(c) <= CONFIG.cols;
      // sombreado lateral + variación sutil por casilla
      const jitter = (type === 'water') ? 0 : (Math.random() - 0.5) * 0.05;
      col.multiplyScalar((inside ? 1 : 0.55) + jitter);
      mesh.setColorAt(i, col);
      i++;
    }
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    mesh.receiveShadow = true;
    return mesh;
  }

  function laneBase(type, row) {
    const g = new THREE.Group();
    g.add(laneGround(type, row));
    return g;
  }

  function addWaterDetails(lane) {
    const w = fullWidth();
    // espuma en los bordes
    for (const z of [-0.47, 0.47]) {
      const foam = Models.box(w, 0.03, 0.07, 0xdff6ff, 0, TOP_Y.water + 0.015, z, { transparent: true, opacity: 0.55 });
      foam.castShadow = false; foam.receiveShadow = false;
      lane.group.add(foam);
    }
    // destellos que se mueven con la corriente
    lane.sparkles = [];
    for (let i = 0; i < 5; i++) {
      const s = Models.box(0.5 + Math.random() * 0.5, 0.02, 0.08, 0x8fe0ff, 0, TOP_Y.water + 0.012, (Math.random() - 0.5) * 0.6, { transparent: true, opacity: 0.5 });
      s.castShadow = false; s.receiveShadow = false;
      s.position.x = -CONFIG.spawnX + Math.random() * CONFIG.spawnX * 2;
      lane.group.add(s);
      lane.sparkles.push(s);
    }
  }

  function addRailDetails(lane) {
    const g = lane.group;
    const w = fullWidth();
    const gravel = Models.box(w, 0.04, 0.92, 0x5a5a64, 0, 0.02, 0);
    gravel.castShadow = false;
    g.add(gravel);
    for (let x = -w / 2 + 0.4; x < w / 2; x += 0.8) {
      const tie = Models.box(0.5, 0.08, 0.86, 0x5a4a3a, x, 0.045, 0);
      tie.castShadow = false;
      g.add(tie);
    }
    g.add(Models.box(w, 0.07, 0.09, 0x3a3a42, 0, 0.10, -0.26));
    g.add(Models.box(w, 0.07, 0.09, 0x3a3a42, 0, 0.10, 0.26));
    const s1 = Models.railSignal(); s1.position.set(-3.5, 0.05, 0.62);
    const s2 = Models.railSignal(); s2.position.set(3.5, 0.05, 0.62);
    g.add(s1, s2);
    lane.signals = [s1, s2];
  }

  function addRoadDashes(lane, row) {
    const prev = lanes.get(row - 1);
    if (!prev || prev.type !== 'road') return;
    const w = fullWidth();
    for (let x = -w / 2 + 0.6; x < w / 2; x += 1.6) {
      const d = Models.box(0.7, 0.03, 0.12, 0xe8e8e8, x, 0.02, 0.5);
      d.castShadow = false; d.receiveShadow = false;
      lane.group.add(d);
    }
  }

  /* línea del arcén cuando el asfalto termina */
  function addRoadEdges(lane, row) {
    const prev = lanes.get(row - 1);
    const w = fullWidth();
    if (lane.type === 'road' && prev && prev.type !== 'road') {
      const e = Models.box(w, 0.025, 0.08, 0xb8b8c2, 0, 0.02, 0.44);
      e.castShadow = false; e.receiveShadow = false;
      lane.group.add(e);
    }
    if (lane.type !== 'road' && prev && prev.type === 'road') {
      const e = Models.box(w, 0.025, 0.08, 0xb8b8c2, 0, 0.02, -0.44);
      e.castShadow = false; e.receiveShadow = false;
      prev.group.add(e);
    }
  }

  function makeGrass(lane, row) {
    lane.obstacles = new Map();
    lane.coins = new Map();
    const density = Math.min(0.30, 0.10 + row * 0.002);
    const free = [];
    for (let c = -CONFIG.cols; c <= CONFIG.cols; c++) free.push(c);
    const shuffled = free.slice().sort(() => Math.random() - 0.5);
    let placed = 0;
    const maxObs = free.length - 4;
    const used = new Set();
    for (const c of shuffled) {
      if (placed >= maxObs) break;
      if (row <= CONFIG.startSafeRows && Math.abs(c) <= 1) continue;
      if (Math.random() < density) {
        const m = Math.random() < 0.75 ? Models.tree() : Models.rock();
        m.position.set(c, TOP_Y.grass, 0);
        lane.group.add(m);
        lane.obstacles.set(c, m);
        used.add(c);
        placed++;
      }
    }
    // decoración pequeña y monedas en casillas libres
    for (const c of free) {
      if (used.has(c)) continue;
      const r = Math.random();
      if (r < 0.07 && row > CONFIG.startSafeRows) {
        const coin = Models.coin();
        coin.position.set(c + (Math.random() - 0.5) * 0.1, TOP_Y.grass, 0);
        lane.group.add(coin);
        lane.coins.set(c, coin);
      } else if (r < 0.16) {
        const f = Models.flower();
        f.position.set(c + (Math.random() - 0.5) * 0.5, TOP_Y.grass, (Math.random() - 0.5) * 0.5);
        lane.group.add(f);
      } else if (r < 0.30) {
        const t = Models.tuft();
        t.position.set(c + (Math.random() - 0.5) * 0.5, TOP_Y.grass, (Math.random() - 0.5) * 0.5);
        lane.group.add(t);
      }
    }
    // decoración en los laterales oscuros
    for (let side = -1; side <= 1; side += 2) {
      for (let i = 1; i <= CONFIG.sideCols; i++) {
        const r = Math.random();
        if (r < 0.30) {
          const c = side * (CONFIG.cols + i);
          const m = r < 0.24 ? Models.tree() : (r < 0.27 ? Models.bush() : Models.rock());
          m.position.set(c, TOP_Y.grass, 0);
          m.traverse(o => { if (o.material && o.material.color) { o.material = o.material.clone(); o.material.color.multiplyScalar(0.62); } });
          lane.group.add(m);
        }
      }
    }
  }

  function pickVehicle() {
    const r = Math.random();
    if (r < 0.52) return Models.car();
    if (r < 0.62) return Models.taxi();
    if (r < 0.70) return Models.police();
    if (r < 0.82) return Models.bus();
    return Models.truck();
  }

  function makeRoad(lane) {
    lane.vehicles = [];
    const dir = Math.random() < 0.5 ? 1 : -1;
    const speed = 1.8 + Math.random() * 2.8;
    const n = 2 + ((Math.random() * 2) | 0);
    const span = CONFIG.spawnX * 2;
    for (let i = 0; i < n; i++) {
      const mesh = pickVehicle();
      mesh.rotation.y = dir > 0 ? 0 : Math.PI;
      const x = -CONFIG.spawnX + (i / n) * span + Math.random() * (span / n) * 0.4;
      mesh.position.set(x, TOP_Y.road, 0);
      lane.group.add(mesh);
      lane.vehicles.push({ mesh, x, speed, dir, len: mesh.userData.len, smokeT: Math.random() * 2 });
    }
  }

  function makeWater(lane) {
    lane.logs = [];
    addWaterDetails(lane);
    const dir = Math.random() < 0.5 ? 1 : -1;
    const speed = 1.1 + Math.random() * 1.6;
    const n = 3;
    const span = CONFIG.spawnX * 2;
    for (let i = 0; i < n; i++) {
      const len = 2 + ((Math.random() * 3) | 0);
      const mesh = Models.log(len);
      const x = -CONFIG.spawnX + (i / n) * span + Math.random() * 2;
      mesh.position.set(x, TOP_Y.water, 0);
      lane.group.add(mesh);
      lane.logs.push({ mesh, x, speed, dir, len, bobPhase: Math.random() * 6 });
    }
    // a veces, un nenúfar quieto como apoyo extra
    if (Math.random() < 0.35) {
      const c = -CONFIG.cols + 1 + ((Math.random() * (CONFIG.cols * 2 - 1)) | 0);
      const mesh = Models.lilypad();
      mesh.position.set(c, TOP_Y.water, 0);
      lane.group.add(mesh);
      lane.logs.push({ mesh, x: c, speed: 0, dir: 1, len: 0.9, bobPhase: Math.random() * 6 });
    }
  }

  function makeRail(lane) {
    addRailDetails(lane);
    lane.train = {
      phase: 'idle',
      t: 3 + Math.random() * 6,
      dir: Math.random() < 0.5 ? 1 : -1,
      x: 0,
      speed: 20,
      mesh: null,
      len: 0,
      bellPlayed: false,
    };
    const tr = Models.train(6);
    tr.visible = false;
    tr.position.y = TOP_Y.rail + 0.05;
    lane.group.add(tr);
    lane.train.mesh = tr;
    lane.train.len = tr.userData.len;
  }

  function nextTypeBatch() {
    const r = Math.random();
    if (r < 0.32) return ['grass', Math.random() < 0.4 ? 'grass' : null].filter(Boolean);
    if (r < 0.70) {
      const n = 1 + ((Math.random() * 3) | 0);
      return new Array(n).fill('road');
    }
    if (r < 0.87) {
      const n = 1 + (Math.random() < 0.35 ? 1 : 0);
      return new Array(n).fill('water');
    }
    return ['rail'];
  }

  function makeLane(row) {
    let type;
    if (row <= CONFIG.startSafeRows) type = 'grass';
    else {
      if (genQueue.length === 0) genQueue = nextTypeBatch();
      type = genQueue.shift();
    }
    const lane = { row, type, group: laneBase(type, row), obstacles: null, vehicles: null, logs: null, train: null, signals: null, coins: null, sparkles: null };
    lane.group.position.z = -row * T;
    if (type === 'grass') makeGrass(lane, row);
    else if (type === 'road') { makeRoad(lane); addRoadDashes(lane, row); }
    else if (type === 'water') makeWater(lane);
    else if (type === 'rail') makeRail(lane);
    addRoadEdges(lane, row);
    group.add(lane.group);
    lanes.set(row, lane);
    return lane;
  }

  function disposeLane(lane) {
    lane.group.traverse(o => { if (o.geometry && o.geometry !== tileGeo) o.geometry.dispose(); });
    group.remove(lane.group);
  }

  function initClouds() {
    for (const c of clouds) scene.remove(c.mesh);
    clouds = [];
    for (let i = 0; i < 5; i++) {
      const mesh = Models.cloud();
      mesh.position.set(-10 + Math.random() * 20, 6.5 + Math.random() * 1.5, -Math.random() * 24);
      scene.add(mesh);
      clouds.push({ mesh, speed: 0.25 + Math.random() * 0.35 });
    }
  }

  return {
    TOP_Y, COLORS, T,

    init(sc) {
      scene = sc;
      group = new THREE.Group();
      scene.add(group);
      this.reset();
    },

    reset() {
      for (const lane of lanes.values()) disposeLane(lane);
      lanes.clear();
      genQueue = [];
      topRow = -1;
      quakeAmp = 0;
      for (let r = -13; r <= 20; r++) makeLane(r);
      topRow = 20;
      initClouds();
    },

    ensure(upTo) {
      while (topRow < upTo) { topRow++; makeLane(topRow); }
    },

    cull(below) {
      for (const [row, lane] of lanes) {
        if (row < below) { disposeLane(lane); lanes.delete(row); }
      }
    },

    laneAt(row) { return lanes.get(row); },

    topY(row) {
      const lane = lanes.get(row);
      return lane ? TOP_Y[lane.type] : TOP_Y.grass;
    },

    isBlocked(row, col) {
      const lane = lanes.get(row);
      return !!(lane && lane.type === 'grass' && lane.obstacles && lane.obstacles.has(col));
    },

    crushObstacle(row, col) {
      const lane = lanes.get(row);
      if (lane && lane.obstacles && lane.obstacles.has(col)) {
        const m = lane.obstacles.get(col);
        const wp = new THREE.Vector3();
        m.getWorldPosition(wp);
        Effects.poof(wp, 0x3cb04e);
        m.traverse(o => { if (o.geometry) o.geometry.dispose(); });
        lane.group.remove(m);
        lane.obstacles.delete(col);
      }
    },

    collectCoin(row, col) {
      const lane = lanes.get(row);
      if (lane && lane.coins && lane.coins.has(col)) {
        const c = lane.coins.get(col);
        const wp = new THREE.Vector3();
        c.getWorldPosition(wp);
        Effects.sparkle(wp);
        c.traverse(o => { if (o.geometry && o.geometry !== tileGeo) o.geometry.dispose(); });
        lane.group.remove(c);
        lane.coins.delete(col);
        return true;
      }
      return false;
    },

    logAt(row, x) {
      const lane = lanes.get(row);
      if (!lane || lane.type !== 'water') return null;
      for (const log of lane.logs) {
        if (Math.abs(log.x - x) < log.len / 2 + 0.45) return log;
      }
      return null;
    },

    /* El super camión manda por los aires a los vehículos normales */
    knockVehicles(row, x, range, dir) {
      for (let dr = -1; dr <= 1; dr++) {
        const lane = lanes.get(row + dr);
        if (!lane || lane.type !== 'road') continue;
        for (let i = lane.vehicles.length - 1; i >= 0; i--) {
          const v = lane.vehicles[i];
          if (Math.abs(v.x - x) < range) {
            const wp = new THREE.Vector3();
            v.mesh.getWorldPosition(wp);
            lane.group.remove(v.mesh);
            v.mesh.position.copy(wp);
            Effects.tumble(v.mesh, new THREE.Vector3(dir * 5 + (Math.random() - 0.5) * 3, 6 + Math.random() * 3, (Math.random() - 0.5) * 5));
            Effects.explosion(wp, 0.6);
            lane.vehicles.splice(i, 1);
          }
        }
      }
    },

    setQuake(amp) { quakeAmp = Math.max(quakeAmp, amp); },

    update(dt, focusRow, playerRow) {
      const lo = Math.floor(focusRow - 14);
      const hi = Math.ceil(focusRow + 26);
      const now = performance.now() / 1000;
      const diff = difficulty();

      // nubes a la deriva
      for (const c of clouds) {
        c.mesh.position.x += c.speed * dt;
        if (c.mesh.position.x > 14) c.mesh.position.x = -14;
        const rel = c.mesh.position.z - (-focusRow);
        if (rel > 14) c.mesh.position.z -= 30;
        if (rel < -26) c.mesh.position.z += 30;
      }

      // temblor de terremoto
      const q = quakeAmp;
      quakeAmp = 0;

      for (const [row, lane] of lanes) {
        if (row < lo || row > hi) continue;

        lane.group.position.y = (q > 0 ? Math.sin(now * 26 + row * 1.35) * 0.07 * q : 0) + (lane.sinkY || 0);

        if (lane.type === 'road') {
          for (const v of lane.vehicles) {
            v.x += v.speed * diff * v.dir * dt;
            if (v.dir > 0 && v.x > CONFIG.spawnX) v.x = this._respawnX(lane, -1);
            if (v.dir < 0 && v.x < -CONFIG.spawnX) v.x = this._respawnX(lane, 1);
            v.mesh.position.x = v.x;
            // luces de la patrulla
            if (v.mesh.userData.beacons) {
              const on = Math.floor(now * 5) % 2;
              v.mesh.userData.beacons[0].material.emissiveIntensity = on ? 1 : 0.1;
              v.mesh.userData.beacons[1].material.emissiveIntensity = on ? 0.1 : 1;
            }
            // humo del camión
            if (v.mesh.userData.smokes && Math.abs(row - playerRow) < 9) {
              v.smokeT -= dt;
              if (v.smokeT <= 0) {
                v.smokeT = 1.2 + Math.random();
                const wp = new THREE.Vector3();
                v.mesh.getWorldPosition(wp);
                wp.y += 1.1; wp.x -= v.dir * 1.2;
                Effects.smoke(wp, 1);
              }
            }
          }
        } else if (lane.type === 'water') {
          for (const log of lane.logs) {
            if (log.speed > 0) {
              log.x += log.speed * log.dir * dt;
              if (log.dir > 0 && log.x > CONFIG.spawnX) log.x = -CONFIG.spawnX;
              if (log.dir < 0 && log.x < -CONFIG.spawnX) log.x = CONFIG.spawnX;
              log.mesh.position.x = log.x;
            }
            log.mesh.position.y = TOP_Y.water + Math.sin(now * 1.8 + log.bobPhase) * 0.018;
          }
          if (lane.sparkles) {
            for (const s of lane.sparkles) {
              s.position.x += 0.35 * dt;
              if (s.position.x > CONFIG.spawnX) s.position.x = -CONFIG.spawnX;
              s.material.opacity = 0.3 + Math.sin(now * 2 + s.position.z * 20) * 0.2;
            }
          }
        } else if (lane.type === 'rail') {
          const tr = lane.train;
          const wantArmDown = (tr.phase === 'warn' || tr.phase === 'run');
          for (const s of lane.signals) {
            const arm = s.userData.arm;
            const target = wantArmDown ? 0 : Math.PI / 2;
            arm.rotation.z += (target - arm.rotation.z) * Math.min(1, 5 * dt);
          }
          if (tr.phase === 'idle') {
            tr.t -= dt;
            if (tr.t <= 0) {
              tr.phase = 'warn';
              tr.t = 1.7;
              tr.bellPlayed = false;
            }
          } else if (tr.phase === 'warn') {
            if (!tr.bellPlayed && Math.abs(row - playerRow) < 10) {
              AudioFX.trainBell();
              tr.bellPlayed = true;
            }
            const blink = (Math.floor(performance.now() / 250) % 2) === 0;
            for (const s of lane.signals) {
              s.userData.lights[0].material.color.setHex(blink ? 0xff2222 : 0x550000);
              s.userData.lights[1].material.color.setHex(blink ? 0x550000 : 0xff2222);
            }
            tr.t -= dt;
            if (tr.t <= 0) {
              tr.phase = 'run';
              const start = CONFIG.spawnX + tr.len;
              tr.x = tr.dir > 0 ? -start : start;
              tr.mesh.visible = true;
              tr.mesh.rotation.y = tr.dir > 0 ? 0 : Math.PI;
              if (Math.abs(row - playerRow) < 10) AudioFX.trainPass();
            }
          } else if (tr.phase === 'run') {
            tr.x += tr.speed * tr.dir * dt;
            tr.mesh.position.x = tr.x;
            const limit = CONFIG.spawnX + tr.len + 2;
            if (Math.abs(tr.x) > limit) {
              tr.phase = 'idle';
              tr.t = Math.max(2.5, (4 + Math.random() * 7) / diff);
              tr.mesh.visible = false;
              for (const s of lane.signals) {
                s.userData.lights[0].material.color.setHex(0x550000);
                s.userData.lights[1].material.color.setHex(0x550000);
              }
            }
          }
        } else if (lane.type === 'grass' && lane.coins) {
          for (const c of lane.coins.values()) {
            c.rotation.y += 2.5 * dt;
            c.children[0].position.y = 0.45 + Math.sin(now * 3 + c.position.x) * 0.05;
          }
        }
      }
    },

    /* reaparecer detrás del vehículo más rezagado, sin solaparse */
    _respawnX(lane, side) {
      let edge = side * CONFIG.spawnX;
      for (const o of lane.vehicles) {
        if (side < 0) edge = Math.min(edge, o.x);
        else edge = Math.max(edge, o.x);
      }
      return edge + side * (2.6 + Math.random() * 2);
    },

    lethalAt(row, x) {
      const lane = lanes.get(row);
      if (!lane) return null;
      if (lane.type === 'road') {
        for (const v of lane.vehicles) {
          if (Math.abs(v.x - x) < v.len / 2 + 0.38) return 'car';
        }
      } else if (lane.type === 'rail') {
        const tr = lane.train;
        if (tr.phase === 'run') {
          const head = tr.x;
          const tail = tr.x - tr.dir * tr.len;
          const lo = Math.min(head, tail) - 0.5;
          const hi = Math.max(head, tail) + 0.5;
          if (x > lo && x < hi) return 'train';
        }
      }
      return null;
    },
  };
})();
