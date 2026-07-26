/* ============================================================
 * MUNDO — generación infinita de carriles estilo Crossy Road:
 * pasto con árboles/rocas, carreteras con coches y camiones,
 * ríos con troncos y vías de tren con señales.
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

  let scene = null;
  let group = null;
  const lanes = new Map();
  let topRow = -1;
  let genQueue = [];

  function darker(hex, f) {
    const c = new THREE.Color(hex).multiplyScalar(f);
    return c.getHex();
  }

  function fullWidth() { return (CONFIG.cols * 2 + 1) + CONFIG.sideCols * 2; }

  function laneBase(type, row) {
    const g = new THREE.Group();
    const playW = CONFIG.cols * 2 + 1;
    const sideW = CONFIG.sideCols;
    let color, h, top;
    if (type === 'grass') { color = (row % 2 === 0) ? COLORS.grassA : COLORS.grassB; h = 0.5; }
    else if (type === 'road') { color = COLORS.road; h = 0.42; }
    else if (type === 'rail') { color = COLORS.rail; h = 0.42; }
    else { color = COLORS.water; h = 0.3; }
    top = TOP_Y[type];

    const mid = Models.box(playW, h, T, color, 0, top - h / 2, 0);
    mid.castShadow = false;
    g.add(mid);
    const dark = darker(color, 0.55);
    const sL = Models.box(sideW, h, T, dark, -(playW / 2 + sideW / 2), top - h / 2, 0);
    const sR = Models.box(sideW, h, T, dark, (playW / 2 + sideW / 2), top - h / 2, 0);
    sL.castShadow = false; sR.castShadow = false;
    g.add(sL, sR);
    return g;
  }

  function addRailDetails(lane) {
    const g = lane.group;
    const w = fullWidth();
    for (let x = -w / 2 + 0.4; x < w / 2; x += 0.8) {
      const tie = Models.box(0.5, 0.08, 0.86, 0x5a4a3a, x, 0.03, 0);
      tie.castShadow = false;
      g.add(tie);
    }
    g.add(Models.box(w, 0.07, 0.09, 0x3a3a42, 0, 0.08, -0.26));
    g.add(Models.box(w, 0.07, 0.09, 0x3a3a42, 0, 0.08, 0.26));
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

  function makeGrass(lane, row) {
    lane.obstacles = new Map();
    const density = Math.min(0.30, 0.10 + row * 0.002);
    const free = [];
    for (let c = -CONFIG.cols; c <= CONFIG.cols; c++) free.push(c);
    // baraja y deja al menos 4 casillas libres
    const shuffled = free.slice().sort(() => Math.random() - 0.5);
    let placed = 0;
    const maxObs = free.length - 4;
    for (const c of shuffled) {
      if (placed >= maxObs) break;
      if (row <= CONFIG.startSafeRows && Math.abs(c) <= 1) continue;
      if (Math.random() < density) {
        const m = Math.random() < 0.75 ? Models.tree() : Models.rock();
        m.position.set(c, TOP_Y.grass, 0);
        lane.group.add(m);
        lane.obstacles.set(c, m);
        placed++;
      }
    }
    // decoración en los laterales oscuros
    for (let side = -1; side <= 1; side += 2) {
      for (let i = 1; i <= CONFIG.sideCols; i++) {
        if (Math.random() < 0.30) {
          const c = side * (CONFIG.cols + i);
          const m = Math.random() < 0.85 ? Models.tree() : Models.rock();
          m.position.set(c, TOP_Y.grass, 0);
          m.traverse(o => { if (o.material && o.material.color) { o.material = o.material.clone(); o.material.color.multiplyScalar(0.62); } });
          lane.group.add(m);
        }
      }
    }
  }

  function makeRoad(lane) {
    lane.vehicles = [];
    const dir = Math.random() < 0.5 ? 1 : -1;
    const speed = 1.8 + Math.random() * 2.8;
    const n = 2 + ((Math.random() * 2) | 0);
    const span = CONFIG.spawnX * 2;
    for (let i = 0; i < n; i++) {
      const isTruck = Math.random() < 0.3;
      const mesh = isTruck ? Models.truck() : Models.car();
      mesh.rotation.y = dir > 0 ? 0 : Math.PI;
      const x = -CONFIG.spawnX + (i / n) * span + Math.random() * (span / n) * 0.5;
      mesh.position.set(x, TOP_Y.road, 0);
      lane.group.add(mesh);
      lane.vehicles.push({ mesh, x, speed, dir, len: mesh.userData.len });
    }
  }

  function makeWater(lane) {
    lane.logs = [];
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
      lane.logs.push({ mesh, x, speed, dir, len });
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
    const lane = { row, type, group: laneBase(type, row), obstacles: null, vehicles: null, logs: null, train: null, signals: null };
    lane.group.position.z = -row * T;
    if (type === 'grass') makeGrass(lane, row);
    else if (type === 'road') { makeRoad(lane); addRoadDashes(lane, row); }
    else if (type === 'water') makeWater(lane);
    else if (type === 'rail') makeRail(lane);
    group.add(lane.group);
    lanes.set(row, lane);
    return lane;
  }

  function disposeLane(lane) {
    lane.group.traverse(o => { if (o.geometry) o.geometry.dispose(); });
    group.remove(lane.group);
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
      for (let r = -13; r <= 20; r++) makeLane(r);
      topRow = 20;
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

    /* Devuelve el tronco bajo la posición x en esa fila (o null) */
    logAt(row, x) {
      const lane = lanes.get(row);
      if (!lane || lane.type !== 'water') return null;
      for (const log of lane.logs) {
        if (Math.abs(log.x - x) < log.len / 2 + 0.45) return log;
      }
      return null;
    },

    update(dt, focusRow, playerRow) {
      const lo = Math.floor(focusRow - 14);
      const hi = Math.ceil(focusRow + 26);
      for (const [row, lane] of lanes) {
        if (row < lo || row > hi) continue;

        if (lane.type === 'road') {
          for (const v of lane.vehicles) {
            v.x += v.speed * v.dir * dt;
            if (v.dir > 0 && v.x > CONFIG.spawnX) v.x = -CONFIG.spawnX;
            if (v.dir < 0 && v.x < -CONFIG.spawnX) v.x = CONFIG.spawnX;
            v.mesh.position.x = v.x;
          }
        } else if (lane.type === 'water') {
          for (const log of lane.logs) {
            log.x += log.speed * log.dir * dt;
            if (log.dir > 0 && log.x > CONFIG.spawnX) log.x = -CONFIG.spawnX;
            if (log.dir < 0 && log.x < -CONFIG.spawnX) log.x = CONFIG.spawnX;
            log.mesh.position.x = log.x;
          }
        } else if (lane.type === 'rail') {
          const tr = lane.train;
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
              s.userData.lights[0].material.emissive && s.userData.lights[0].material.emissive.setHex(blink ? 0xff0000 : 0x000000);
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
              tr.t = 4 + Math.random() * 7;
              tr.mesh.visible = false;
              for (const s of lane.signals) {
                s.userData.lights[0].material.color.setHex(0x550000);
                s.userData.lights[1].material.color.setHex(0x550000);
              }
            }
          }
        }
      }
    },

    /* ¿Hay algo letal tocando la casilla (row, x)? Devuelve la causa o null */
    lethalAt(row, x, rowF) {
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
