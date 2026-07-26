/* ============================================================
 * MODELOS VOXEL — todo se construye con cajas (estilo Crossy Road)
 * con detalle fino: flores, matas, monedas, nubes, taxis, policía,
 * autobús, barreras de tren, nenúfares…
 * ============================================================ */
const Models = (() => {
  const matCache = new Map();

  function mat(color, opts) {
    if (opts) return new THREE.MeshLambertMaterial(Object.assign({ color }, opts));
    if (!matCache.has(color)) matCache.set(color, new THREE.MeshLambertMaterial({ color }));
    return matCache.get(color);
  }

  function box(w, h, d, color, x, y, z, opts) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color, opts));
    m.position.set(x || 0, y || 0, z || 0);
    m.castShadow = true;
    m.receiveShadow = true;
    return m;
  }

  /* --- El pollo protagonista (v2, con cabeza articulada) --- */
  function chicken() {
    const g = new THREE.Group();
    g.add(box(0.60, 0.46, 0.74, 0xffffff, 0, 0.42, 0.02));          // cuerpo
    g.add(box(0.62, 0.10, 0.60, 0xf4f4f4, 0, 0.66, 0.02));          // lomo
    // cabeza articulada (para picotear)
    const head = new THREE.Group();
    head.position.set(0, 0.64, -0.14);
    head.add(box(0.42, 0.46, 0.42, 0xffffff, 0, 0.22, 0));
    head.add(box(0.11, 0.13, 0.13, 0xd93b3b, 0, 0.52, -0.06));      // cresta (2 bultos)
    head.add(box(0.11, 0.11, 0.13, 0xd93b3b, 0, 0.49, 0.08));
    head.add(box(0.15, 0.09, 0.18, 0xf2a13a, 0, 0.20, -0.29));      // pico
    head.add(box(0.10, 0.13, 0.08, 0xd93b3b, 0, 0.08, -0.25));      // barba
    const e1 = box(0.05, 0.08, 0.08, 0x222222, 0.215, 0.26, -0.05);
    const e2 = e1.clone(); e2.position.x = -0.215;
    head.add(e1, e2);
    g.add(head);
    g.userData.head = head;
    g.add(box(0.10, 0.28, 0.44, 0xe8e8e8, 0.34, 0.44, 0.05));       // alas
    g.add(box(0.10, 0.28, 0.44, 0xe8e8e8, -0.34, 0.44, 0.05));
    g.add(box(0.30, 0.20, 0.14, 0xf4f4f4, 0, 0.56, 0.44));          // cola escalonada
    g.add(box(0.22, 0.16, 0.10, 0xf4f4f4, 0, 0.70, 0.50));
    g.add(box(0.09, 0.22, 0.11, 0xf2a13a, 0.13, 0.11, 0.02));       // patas
    g.add(box(0.09, 0.22, 0.11, 0xf2a13a, -0.13, 0.11, 0.02));
    g.add(box(0.15, 0.05, 0.16, 0xf2a13a, 0.13, 0.02, -0.03));      // pies
    g.add(box(0.15, 0.05, 0.16, 0xf2a13a, -0.13, 0.02, -0.03));
    return g;
  }

  /* --- Vehículos (viajan a lo largo del eje X) --- */
  const CAR_COLORS = [0xe8552d, 0x3b7bd9, 0x8a5cd9, 0x35b34a, 0xe8b93b, 0x50c8c8, 0xd94f8a, 0xf0f0f0];

  function wheels(g, xs, w) {
    for (const x of xs) {
      g.add(box(0.30, 0.30, 0.14, 0x222228, x, 0.15, (w / 2)));
      g.add(box(0.30, 0.30, 0.14, 0x222228, x, 0.15, -(w / 2)));
    }
  }

  function carBase(color) {
    const g = new THREE.Group();
    g.add(box(1.55, 0.42, 0.78, color, 0, 0.40, 0));
    g.add(box(0.78, 0.34, 0.70, 0xf4f4f4, -0.05, 0.78, 0));
    g.add(box(0.30, 0.30, 0.66, 0x9fd8e8, 0.30, 0.78, 0));           // parabrisas
    g.add(box(0.08, 0.10, 0.16, 0xfff6a8, 0.78, 0.44, 0.22));        // faros
    g.add(box(0.08, 0.10, 0.16, 0xfff6a8, 0.78, 0.44, -0.22));
    g.add(box(0.06, 0.09, 0.14, 0xd93b3b, -0.78, 0.44, 0.22));       // pilotos
    g.add(box(0.06, 0.09, 0.14, 0xd93b3b, -0.78, 0.44, -0.22));
    wheels(g, [0.48, -0.48], 0.72);
    g.userData.len = 1.7; g.userData.height = 1.0;
    return g;
  }

  function car(color) {
    return carBase(color || CAR_COLORS[(Math.random() * CAR_COLORS.length) | 0]);
  }

  function taxi() {
    const g = carBase(0xf0c229);
    g.add(box(0.34, 0.14, 0.30, 0xe8552d, -0.05, 1.02, 0));          // letrero
    for (let i = 0; i < 4; i++) {                                     // cuadros
      g.add(box(0.18, 0.10, 0.02, i % 2 ? 0x222222 : 0xffffff, -0.6 + i * 0.36, 0.40, 0.40));
      g.add(box(0.18, 0.10, 0.02, i % 2 ? 0xffffff : 0x222222, -0.6 + i * 0.36, 0.40, -0.40));
    }
    return g;
  }

  function police() {
    const g = carBase(0xf2f2f2);
    g.add(box(0.9, 0.16, 0.80, 0x28303e, 0, 0.44, 0));               // franja
    const lr = box(0.22, 0.14, 0.26, 0xd93b3b, -0.18, 1.02, 0, { emissive: 0xff0000, emissiveIntensity: 0.2 });
    const lb = box(0.22, 0.14, 0.26, 0x2b5be8, 0.10, 1.02, 0, { emissive: 0x0033ff, emissiveIntensity: 0.2 });
    g.add(lr, lb);
    g.userData.beacons = [lr, lb];
    return g;
  }

  function bus() {
    const g = new THREE.Group();
    const color = Math.random() < 0.5 ? 0x3b7bd9 : 0x35b34a;
    g.add(box(3.1, 0.95, 0.85, color, 0, 0.62, 0));
    g.add(box(3.1, 0.30, 0.87, 0xf4f4f4, 0, 0.95, 0));               // ventanas
    g.add(box(0.08, 0.12, 0.18, 0xfff6a8, 1.56, 0.45, 0.22));
    g.add(box(0.08, 0.12, 0.18, 0xfff6a8, 1.56, 0.45, -0.22));
    wheels(g, [1.05, -1.05], 0.78);
    g.userData.len = 3.2; g.userData.height = 1.3;
    return g;
  }

  function truck() {
    const color = CAR_COLORS[(Math.random() * CAR_COLORS.length) | 0];
    const g = new THREE.Group();
    g.add(box(0.72, 0.72, 0.82, color, 0.92, 0.55, 0));              // cabina
    g.add(box(0.10, 0.08, 0.60, 0x333338, 1.30, 0.30, 0));           // parrilla
    g.add(box(1.70, 0.90, 0.86, 0xe8e8e8, -0.35, 0.68, 0));          // caja
    g.add(box(1.70, 0.06, 0.88, 0xc8c8c8, -0.35, 1.15, 0));          // techo
    wheels(g, [0.95, -0.15, -0.95], 0.76);
    g.userData.len = 2.7; g.userData.height = 1.2;
    g.userData.smokes = true;
    return g;
  }

  /* --- SUPER CAMIÓN (desastre) --- */
  function superTruck() {
    const g = new THREE.Group();
    const s = new THREE.Group();
    s.add(box(1.3, 1.5, 1.5, 0x8e1616, 1.9, 0.95, 0));               // cabina
    s.add(box(1.3, 0.14, 1.54, 0x5e0e0e, 1.9, 1.72, 0));             // techo
    s.add(box(0.18, 0.55, 1.34, 0xd8d8e0, 2.55, 0.60, 0));           // parrilla cromada
    s.add(box(0.5, 0.16, 1.3, 0xfff6a8, 2.45, 1.05, 0));             // faros
    // pala quitanieves
    s.add(box(0.35, 0.8, 1.9, 0x3a3a42, 2.95, 0.45, 0));
    s.add(box(0.25, 0.55, 1.94, 0xe8552d, 3.10, 0.36, 0));
    s.add(box(0.18, 0.25, 1.98, 0xffd23b, 3.22, 0.20, 0));
    // escapes (guardados para echar fuego)
    const ex1 = box(0.16, 0.9, 0.16, 0x555560, 1.45, 2.1, 0.55);
    const ex2 = box(0.16, 0.9, 0.16, 0x555560, 1.45, 2.1, -0.55);
    s.add(ex1, ex2);
    s.add(box(0.24, 0.10, 0.24, 0x333338, 1.45, 2.6, 0.55));
    s.add(box(0.24, 0.10, 0.24, 0x333338, 1.45, 2.6, -0.55));
    s.add(box(3.6, 1.7, 1.6, 0x1c1c22, -0.85, 1.15, 0));             // remolque
    s.add(box(3.6, 0.30, 1.64, 0xe8552d, -0.85, 0.75, 0));           // franja de fuego
    s.add(box(3.0, 0.16, 1.62, 0xf0c229, -0.85, 0.95, 0));
    s.add(box(0.10, 1.2, 1.2, 0xd8d8e0, -2.68, 1.15, 0));            // trasera cromada
    s.add(box(0.8, 0.5, 0.05, 0xf2f2f2, 2.0, 1.15, 0.76));           // placa calavera
    s.add(box(0.18, 0.18, 0.03, 0x1c1c22, 1.85, 1.2, 0.79));
    s.add(box(0.18, 0.18, 0.03, 0x1c1c22, 2.15, 1.2, 0.79));
    const wheelList = [];
    for (const x of [1.9, 0.3, -0.7, -1.9]) {
      const w1 = box(0.62, 0.62, 0.24, 0x222228, x, 0.31, 0.78);
      const w2 = box(0.62, 0.62, 0.24, 0x222228, x, 0.31, -0.78);
      s.add(w1, w2);
      wheelList.push(w1, w2);
    }
    g.add(s);
    g.userData.len = 6.4; g.userData.height = 2.4;
    g.userData.wheels = wheelList;
    g.userData.exhausts = [ex1, ex2];
    return g;
  }

  /* --- Roca gigante que rebota (terremoto) --- */
  function boulder() {
    const g = new THREE.Group();
    g.add(box(0.85, 0.75, 0.8, 0x8a8a94, 0, 0, 0));
    g.add(box(0.55, 0.5, 0.6, 0x9a9aa4, 0.2, 0.3, 0.1));
    g.add(box(0.5, 0.45, 0.5, 0x7a7a84, -0.25, -0.2, -0.15));
    g.userData.r = 0.55;
    return g;
  }

  /* --- Diamante gigante (reset) --- */
  function diamond() {
    const g = new THREE.Group();
    const o = { emissive: 0x1aa8d8, emissiveIntensity: 0.55, transparent: true, opacity: 0.95 };
    const layers = [[0.35, 0], [0.8, 0.3], [1.25, 0.6], [0.8, 0.9], [0.35, 1.2]];
    for (const [w, y] of layers) g.add(box(w, 0.3, w, 0x7fd8ff, 0, y, 0, o));
    g.add(box(0.5, 0.12, 0.5, 0xffffff, -0.25, 0.72, -0.25, { transparent: true, opacity: 0.85 })); // brillo
    return g;
  }

  /* --- Tren --- */
  function train(nWagons) {
    const g = new THREE.Group();
    const spacing = 1.85;
    const engine = new THREE.Group();
    engine.add(box(1.7, 0.95, 0.9, 0x8e2222, 0, 0.65, 0));
    engine.add(box(0.5, 0.5, 0.8, 0x333338, -0.55, 1.30, 0));
    engine.add(box(0.25, 0.35, 0.25, 0x222228, 0.55, 1.25, 0));      // chimenea
    engine.add(box(0.1, 0.3, 0.7, 0xfff6a8, 0.86, 0.65, 0));         // faro
    engine.add(box(0.3, 0.2, 1.0, 0x333338, 0.75, 0.25, 0));         // quitapiedras
    g.add(engine);
    for (let i = 1; i <= nWagons; i++) {
      const w = new THREE.Group();
      const c = i % 2 ? 0x707078 : 0x8a8a92;
      w.add(box(1.65, 0.85, 0.88, c, 0, 0.62, 0));
      w.add(box(1.65, 0.22, 0.9, 0x4a4a52, 0, 1.08, 0));
      w.add(box(1.2, 0.28, 0.92, 0xd8d8e0, 0, 0.72, 0));             // ventanillas
      w.position.x = -i * spacing;
      g.add(w);
    }
    g.userData.len = nWagons * spacing + 2.0;
    return g;
  }

  /* --- Señal de tren con barrera --- */
  function railSignal() {
    const g = new THREE.Group();
    for (let i = 0; i < 5; i++)
      g.add(box(0.14, 0.24, 0.14, i % 2 ? 0xffffff : 0xd93b3b, 0, 0.12 + i * 0.24, 0));
    g.add(box(0.55, 0.16, 0.1, 0x222228, 0, 1.35, 0));
    const l1 = box(0.14, 0.14, 0.08, 0x550000, -0.16, 1.35, 0.06);
    const l2 = box(0.14, 0.14, 0.08, 0x550000, 0.16, 1.35, 0.06);
    l1.material = l1.material.clone(); l2.material = l2.material.clone();
    g.add(l1, l2);
    // barrera que baja cuando viene el tren
    const armPivot = new THREE.Group();
    armPivot.position.set(0.10, 1.02, 0);
    for (let i = 0; i < 5; i++)
      armPivot.add(box(0.26, 0.09, 0.09, i % 2 ? 0xd93b3b : 0xffffff, 0.16 + i * 0.26, 0, 0));
    armPivot.rotation.z = Math.PI / 2;   // en reposo apunta hacia arriba
    g.add(armPivot);
    g.userData.lights = [l1, l2];
    g.userData.arm = armPivot;
    return g;
  }

  /* --- Naturaleza --- */
  function tree() {
    const g = new THREE.Group();
    const h = 0.5 + Math.random() * 0.9;
    g.add(box(0.30, h, 0.30, 0x7a5230, 0, h / 2, 0));
    const greens = [0x2f9e41, 0x3cb04e, 0x27913a];
    const gc = greens[(Math.random() * greens.length) | 0];
    g.add(box(0.85, 0.55, 0.85, gc, 0, h + 0.25, 0));
    g.add(box(0.62, 0.45, 0.62, gc, 0, h + 0.70, 0));
    if (Math.random() < 0.5) g.add(box(0.4, 0.35, 0.4, gc, 0, h + 1.05, 0));
    g.userData.blocking = true;
    return g;
  }

  function rock() {
    const g = new THREE.Group();
    g.add(box(0.55, 0.35, 0.48, 0x9a9aa4, 0, 0.17, 0));
    g.add(box(0.3, 0.25, 0.3, 0xb0b0ba, 0.1, 0.4, 0.05));
    g.rotation.y = Math.random() * Math.PI;
    g.userData.blocking = true;
    return g;
  }

  function bush() {
    const g = new THREE.Group();
    const gc = Math.random() < 0.5 ? 0x2f9e41 : 0x27913a;
    g.add(box(0.55, 0.35, 0.55, gc, 0, 0.17, 0));
    g.add(box(0.35, 0.25, 0.35, gc, 0.12, 0.40, 0.06));
    g.userData.blocking = true;
    return g;
  }

  const FLOWER_COLORS = [0xf2f2f2, 0xf0c229, 0xe8552d, 0xd94f8a, 0x9a6ee8];
  function flower() {
    const g = new THREE.Group();
    const c = FLOWER_COLORS[(Math.random() * FLOWER_COLORS.length) | 0];
    g.add(box(0.05, 0.22, 0.05, 0x2f9e41, 0, 0.11, 0));
    g.add(box(0.16, 0.10, 0.16, c, 0, 0.26, 0));
    g.add(box(0.07, 0.06, 0.07, 0xf0c229, 0, 0.33, 0));
    return g;
  }

  function tuft() {
    const g = new THREE.Group();
    for (let i = 0; i < 3; i++)
      g.add(box(0.06, 0.14 + Math.random() * 0.1, 0.06, 0x6cc23e, (i - 1) * 0.09, 0.09, (Math.random() - 0.5) * 0.1));
    return g;
  }

  function log(len) {
    const g = new THREE.Group();
    g.add(box(len, 0.28, 0.80, 0x8a5a33, 0, 0.14, 0));
    g.add(box(len, 0.06, 0.66, 0x9a6a40, 0, 0.30, 0));               // veta clara
    g.add(box(0.12, 0.30, 0.82, 0x6e4526, (len / 2) - 0.05, 0.14, 0));
    g.add(box(0.12, 0.30, 0.82, 0x6e4526, -(len / 2) + 0.05, 0.14, 0));
    g.userData.len = len;
    return g;
  }

  function lilypad() {
    const g = new THREE.Group();
    g.add(box(0.78, 0.08, 0.78, 0x3cb04e, 0, 0.24, 0));
    g.add(box(0.30, 0.09, 0.30, 0x2f9e41, 0.18, 0.25, 0.18));
    if (Math.random() < 0.4) {
      const c = FLOWER_COLORS[(Math.random() * FLOWER_COLORS.length) | 0];
      g.add(box(0.16, 0.12, 0.16, c, -0.15, 0.34, -0.12));
    }
    g.userData.len = 0.9;
    return g;
  }

  function coin() {
    const m = new THREE.Mesh(
      new THREE.CylinderGeometry(0.26, 0.26, 0.08, 12),
      mat(0xf0c229, { emissive: 0x8a6a00, emissiveIntensity: 0.35 })
    );
    m.rotation.x = Math.PI / 2;
    m.castShadow = true;
    const g = new THREE.Group();
    g.add(m);
    m.position.y = 0.45;
    return g;
  }

  function cloud() {
    const g = new THREE.Group();
    const o = { transparent: true, opacity: 0.92 };
    const n = 3 + ((Math.random() * 3) | 0);
    for (let i = 0; i < n; i++) {
      const s = 0.8 + Math.random() * 1.4;
      const b = box(s, s * 0.5, s * 0.8, 0xffffff, i * 0.9 - n * 0.45, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.8, o);
      b.receiveShadow = false;
      g.add(b);
    }
    return g;
  }

  /* --- Desastres --- */
  function volcano() {
    const g = new THREE.Group();
    const browns = [0x5a3b2e, 0x6b4636, 0x4e3227];
    g.add(box(3.4, 0.8, 3.4, browns[0], 0, 0.4, 0));
    g.add(box(2.6, 0.8, 2.6, browns[1], 0, 1.2, 0));
    g.add(box(1.9, 0.8, 1.9, browns[2], 0, 2.0, 0));
    g.add(box(1.3, 0.6, 1.3, 0x3a251c, 0, 2.7, 0));
    // ríos de lava en las laderas
    g.add(box(0.25, 1.6, 0.25, 0xff6a1a, 0.75, 1.9, 0.75, { emissive: 0xff4400, emissiveIntensity: 0.8 }));
    g.add(box(0.2, 1.2, 0.2, 0xffd23b, -0.8, 2.1, -0.5, { emissive: 0xffaa00, emissiveIntensity: 0.8 }));
    const crater = box(0.9, 0.25, 0.9, 0xff6a1a, 0, 3.02, 0, { emissive: 0xff4400, emissiveIntensity: 0.9 });
    g.add(crater);
    g.userData.craterY = 3.1;
    return g;
  }

  function lavaBomb() {
    const g = new THREE.Group();
    g.add(box(0.42, 0.42, 0.42, 0xff6a1a, 0, 0, 0, { emissive: 0xff3300, emissiveIntensity: 1 }));
    g.add(box(0.26, 0.26, 0.26, 0xffd23b, 0, 0.1, 0.1, { emissive: 0xffaa00, emissiveIntensity: 1 }));
    return g;
  }

  function tornado() {
    const g = new THREE.Group();
    const layers = [];
    const grays = [0xd8dde2, 0xcfd4da, 0xbfc6cd, 0xaeb6bf, 0x9aa3ad, 0x8a939e, 0x7b848f, 0x6d7680];
    for (let i = 0; i < 8; i++) {
      const s = 0.45 + i * 0.44;
      const b = box(s, 0.52, s, grays[i], 0, 0.28 + i * 0.52, 0, { transparent: true, opacity: 0.85 });
      layers.push(b); g.add(b);
    }
    const debris = [];
    for (let i = 0; i < 12; i++) {
      const colors = [0x8a5a33, 0x2f9e41, 0x9a9aa4, 0xe8552d];
      const d = box(0.16, 0.16, 0.16, colors[i % 4], 0, 0.5 + Math.random() * 3.6, 0);
      d.userData.ang = Math.random() * Math.PI * 2;
      d.userData.r = 0.7 + Math.random() * 1.6;
      debris.push(d); g.add(d);
    }
    g.userData.layers = layers;
    g.userData.debris = debris;
    return g;
  }

  function ufo() {
    const g = new THREE.Group();
    g.add(box(1.5, 0.22, 1.5, 0x9aa0ad, 0, 0, 0));
    g.add(box(1.9, 0.20, 1.0, 0x848a98, 0, 0, 0));
    g.add(box(1.0, 0.20, 1.9, 0x848a98, 0, 0, 0));
    g.add(box(0.9, 0.30, 0.9, 0x6a7080, 0, 0.24, 0));
    g.add(box(0.6, 0.45, 0.6, 0x7fe7ff, 0, 0.55, 0, { transparent: true, opacity: 0.85, emissive: 0x2bb8d8, emissiveIntensity: 0.6 }));
    const lights = [];
    for (let i = 0; i < 4; i++) {
      const a = (i / 4) * Math.PI * 2;
      const l = box(0.18, 0.12, 0.18, 0xffe14d, Math.cos(a) * 0.75, -0.14, Math.sin(a) * 0.75, { emissive: 0xffcc00, emissiveIntensity: 1 });
      lights.push(l); g.add(l);
    }
    const beam = new THREE.Mesh(
      new THREE.CylinderGeometry(0.35, 1.0, 6, 12, 1, true),
      new THREE.MeshBasicMaterial({ color: 0x9af2ff, transparent: true, opacity: 0.0, side: THREE.DoubleSide, depthWrite: false })
    );
    beam.position.y = -3.1;
    g.add(beam);
    const glow = new THREE.Mesh(
      new THREE.CircleGeometry(1.1, 24),
      new THREE.MeshBasicMaterial({ color: 0x9af2ff, transparent: true, opacity: 0.0, depthWrite: false })
    );
    glow.rotation.x = -Math.PI / 2;
    glow.position.y = -3.3;
    g.add(glow);
    // foco de búsqueda (cono inclinado que barre el suelo al girar el ovni)
    const search = new THREE.Mesh(
      new THREE.CylinderGeometry(0.18, 1.3, 7, 12, 1, true),
      new THREE.MeshBasicMaterial({ color: 0xfff6c8, transparent: true, opacity: 0.0, side: THREE.DoubleSide, depthWrite: false })
    );
    search.position.set(1.4, -3.3, 0);
    search.rotation.z = 0.42;
    g.add(search);
    g.userData.beam = beam;
    g.userData.glow = glow;
    g.userData.search = search;
    g.userData.lights = lights;
    return g;
  }

  function eagle() {
    const g = new THREE.Group();
    g.add(box(0.7, 0.5, 1.2, 0x5a4030, 0, 0, 0));
    g.add(box(0.45, 0.42, 0.5, 0xf2f2f2, 0, 0.12, -0.75));
    g.add(box(0.16, 0.14, 0.3, 0xf2a13a, 0, 0.08, -1.1));
    g.add(box(0.5, 0.14, 0.6, 0xf2f2f2, 0, 0.05, 0.8));
    g.add(box(0.12, 0.22, 0.12, 0xf2a13a, 0.15, -0.3, -0.2));       // garras
    g.add(box(0.12, 0.22, 0.12, 0xf2a13a, -0.15, -0.3, -0.2));
    const wL = box(1.6, 0.12, 0.8, 0x6b4d39, 1.05, 0.15, 0);
    const wR = box(1.6, 0.12, 0.8, 0x6b4d39, -1.05, 0.15, 0);
    g.add(wL, wR);
    g.userData.wings = [wL, wR];
    return g;
  }

  function lightningBolt() {
    const g = new THREE.Group();
    let x = 0, z = 0;
    for (let i = 0; i < 7; i++) {
      const y = 6.5 - i;
      const b = box(0.28, 1.15, 0.28, 0xffffff, x, y, z, { emissive: 0xaaddff, emissiveIntensity: 1 });
      g.add(b);
      if (i === 3) g.add(box(0.18, 0.9, 0.18, 0xd8f2ff, x + 0.4, y - 0.3, z + 0.2, { emissive: 0xaaddff, emissiveIntensity: 1 }));
      x += (Math.random() - 0.5) * 0.7;
      z += (Math.random() - 0.5) * 0.7;
    }
    return g;
  }

  function shieldBubble() {
    const m = new THREE.Mesh(
      new THREE.SphereGeometry(0.75, 12, 10),
      new THREE.MeshBasicMaterial({ color: 0x7fe7ff, transparent: true, opacity: 0.28, depthWrite: false })
    );
    m.position.y = 0.55;
    return m;
  }

  function targetMarker() {
    const m = new THREE.Mesh(
      new THREE.RingGeometry(0.35, 0.55, 24),
      new THREE.MeshBasicMaterial({ color: 0xff2222, transparent: true, opacity: 0.9, side: THREE.DoubleSide, depthWrite: false })
    );
    m.rotation.x = -Math.PI / 2;
    return m;
  }

  return {
    mat, box, chicken, car, taxi, police, bus, truck, superTruck, train,
    railSignal, tree, rock, bush, flower, tuft, log, lilypad, coin, cloud,
    volcano, lavaBomb, tornado, ufo, eagle, lightningBolt, shieldBubble,
    targetMarker, boulder, diamond, CAR_COLORS,
  };
})();
