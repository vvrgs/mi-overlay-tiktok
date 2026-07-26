/* ============================================================
 * MODELOS VOXEL — todo se construye con cajas (estilo Crossy Road)
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

  /* --- El pollo protagonista --- */
  function chicken() {
    const g = new THREE.Group();
    g.add(box(0.58, 0.44, 0.72, 0xffffff, 0, 0.42, 0.02));        // cuerpo
    g.add(box(0.40, 0.44, 0.40, 0xffffff, 0, 0.80, -0.16));       // cabeza
    g.add(box(0.10, 0.16, 0.26, 0xd93b3b, 0, 1.08, -0.14));       // cresta
    g.add(box(0.14, 0.09, 0.16, 0xf2a13a, 0, 0.84, -0.42));       // pico
    g.add(box(0.10, 0.12, 0.08, 0xd93b3b, 0, 0.72, -0.38));       // barba
    const e1 = box(0.05, 0.08, 0.08, 0x222222, 0.21, 0.88, -0.20); // ojos
    const e2 = e1.clone(); e2.position.x = -0.21;
    g.add(e1, e2);
    g.add(box(0.10, 0.26, 0.42, 0xe8e8e8, 0.33, 0.46, 0.04));     // alas
    g.add(box(0.10, 0.26, 0.42, 0xe8e8e8, -0.33, 0.46, 0.04));
    g.add(box(0.30, 0.26, 0.16, 0xf4f4f4, 0, 0.58, 0.42));        // cola
    g.add(box(0.10, 0.22, 0.12, 0xf2a13a, 0.13, 0.11, 0.02));     // patas
    g.add(box(0.10, 0.22, 0.12, 0xf2a13a, -0.13, 0.11, 0.02));
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

  function car(color) {
    color = color || CAR_COLORS[(Math.random() * CAR_COLORS.length) | 0];
    const g = new THREE.Group();
    g.add(box(1.55, 0.42, 0.78, color, 0, 0.40, 0));
    g.add(box(0.78, 0.34, 0.70, 0xf4f4f4, -0.05, 0.78, 0));
    g.add(box(0.08, 0.10, 0.16, 0xfff6a8, 0.78, 0.44, 0.22));
    g.add(box(0.08, 0.10, 0.16, 0xfff6a8, 0.78, 0.44, -0.22));
    wheels(g, [0.48, -0.48], 0.72);
    g.userData.len = 1.7; g.userData.height = 1.0;
    return g;
  }

  function truck() {
    const color = CAR_COLORS[(Math.random() * CAR_COLORS.length) | 0];
    const g = new THREE.Group();
    g.add(box(0.72, 0.72, 0.82, color, 0.92, 0.55, 0));            // cabina
    g.add(box(0.10, 0.08, 0.60, 0x333338, 1.30, 0.30, 0));         // parrilla
    g.add(box(1.70, 0.90, 0.86, 0xe8e8e8, -0.35, 0.68, 0));        // caja
    wheels(g, [0.95, -0.15, -0.95], 0.76);
    g.userData.len = 2.7; g.userData.height = 1.2;
    return g;
  }

  /* --- SUPER CAMIÓN (desastre) --- */
  function superTruck() {
    const g = new THREE.Group();
    const s = new THREE.Group();
    s.add(box(1.3, 1.5, 1.5, 0x8e1616, 1.9, 0.95, 0));             // cabina
    s.add(box(0.18, 0.5, 1.3, 0xd8d8e0, 2.55, 0.62, 0));           // parrilla cromada
    s.add(box(0.5, 0.16, 1.3, 0xfff6a8, 2.45, 1.05, 0));           // faros
    s.add(box(0.16, 0.7, 0.16, 0x555560, 1.45, 2.0, 0.55));        // escapes
    s.add(box(0.16, 0.7, 0.16, 0x555560, 1.45, 2.0, -0.55));
    s.add(box(3.6, 1.7, 1.6, 0x1c1c22, -0.85, 1.15, 0));           // remolque
    s.add(box(3.6, 0.30, 1.64, 0xe8552d, -0.85, 0.75, 0));         // franja de fuego
    s.add(box(0.8, 0.5, 0.05, 0xf2f2f2, 2.0, 1.15, 0.76));         // calavera (placa)
    for (const x of [1.9, 0.3, -0.7, -1.9]) {
      s.add(box(0.55, 0.55, 0.22, 0x222228, x, 0.28, 0.78));
      s.add(box(0.55, 0.55, 0.22, 0x222228, x, 0.28, -0.78));
    }
    g.add(s);
    g.userData.len = 6.0; g.userData.height = 2.2;
    return g;
  }

  /* --- Tren --- */
  function train(nWagons) {
    const g = new THREE.Group();
    const spacing = 1.85;
    const engine = new THREE.Group();
    engine.add(box(1.7, 0.95, 0.9, 0x8e2222, 0, 0.65, 0));
    engine.add(box(0.5, 0.5, 0.8, 0x333338, -0.55, 1.30, 0));
    engine.add(box(0.25, 0.35, 0.25, 0x222228, 0.55, 1.25, 0));    // chimenea
    engine.add(box(0.1, 0.3, 0.7, 0xfff6a8, 0.86, 0.65, 0));       // faro
    engine.position.x = 0;
    g.add(engine);
    for (let i = 1; i <= nWagons; i++) {
      const w = new THREE.Group();
      const c = i % 2 ? 0x707078 : 0x8a8a92;
      w.add(box(1.65, 0.85, 0.88, c, 0, 0.62, 0));
      w.add(box(1.65, 0.22, 0.9, 0x4a4a52, 0, 1.08, 0));
      w.position.x = -i * spacing;
      g.add(w);
    }
    g.userData.len = nWagons * spacing + 2.0;
    return g;
  }

  /* --- Señal de tren --- */
  function railSignal() {
    const g = new THREE.Group();
    for (let i = 0; i < 5; i++)
      g.add(box(0.14, 0.24, 0.14, i % 2 ? 0xffffff : 0xd93b3b, 0, 0.12 + i * 0.24, 0));
    g.add(box(0.55, 0.16, 0.1, 0x222228, 0, 1.35, 0));
    const l1 = box(0.14, 0.14, 0.08, 0x550000, -0.16, 1.35, 0.06);
    const l2 = box(0.14, 0.14, 0.08, 0x550000, 0.16, 1.35, 0.06);
    l1.material = l1.material.clone(); l2.material = l2.material.clone();
    g.add(l1, l2);
    g.userData.lights = [l1, l2];
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

  function log(len) {
    const g = new THREE.Group();
    g.add(box(len, 0.28, 0.80, 0x8a5a33, 0, 0.14, 0));
    g.add(box(0.12, 0.30, 0.82, 0x6e4526, (len / 2) - 0.05, 0.14, 0));
    g.add(box(0.12, 0.30, 0.82, 0x6e4526, -(len / 2) + 0.05, 0.14, 0));
    g.userData.len = len;
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
    const grays = [0xcfd4da, 0xbfc6cd, 0xaeb6bf, 0x9aa3ad, 0x8a939e, 0x7b848f];
    for (let i = 0; i < 6; i++) {
      const s = 0.5 + i * 0.42;
      const b = box(s, 0.55, s, grays[i], 0, 0.3 + i * 0.55, 0, { transparent: true, opacity: 0.85 });
      layers.push(b); g.add(b);
    }
    const debris = [];
    for (let i = 0; i < 6; i++) {
      const d = box(0.16, 0.16, 0.16, 0x8a5a33, 0, 0.5 + Math.random() * 2.5, 0);
      d.userData.ang = Math.random() * Math.PI * 2;
      d.userData.r = 0.7 + Math.random() * 1.1;
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
    g.userData.beam = beam;
    g.userData.lights = lights;
    return g;
  }

  function eagle() {
    const g = new THREE.Group();
    g.add(box(0.7, 0.5, 1.2, 0x5a4030, 0, 0, 0));
    g.add(box(0.45, 0.42, 0.5, 0xf2f2f2, 0, 0.12, -0.75));
    g.add(box(0.16, 0.14, 0.3, 0xf2a13a, 0, 0.08, -1.1));
    g.add(box(0.5, 0.14, 0.6, 0xf2f2f2, 0, 0.05, 0.8));
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
    mat, box, chicken, car, truck, superTruck, train, railSignal,
    tree, rock, log, volcano, lavaBomb, tornado, ufo, eagle,
    lightningBolt, shieldBubble, targetMarker, CAR_COLORS,
  };
})();
