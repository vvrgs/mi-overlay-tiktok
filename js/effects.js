/* ============================================================
 * EFECTOS — partículas (plumas, agua, fuego, explosiones),
 * clima (lluvia, ráfagas de viento), grietas, lava, marcas,
 * sacudida e inclinación de cámara.
 * ============================================================ */
const Effects = (() => {
  let scene = null;
  const particles = [];
  const decals = [];
  let shakeMag = 0, shakeUntil = 0, shakeSustain = 0;
  let rollSustain = 0;
  let focusX = 0, focusRow = 0;

  // clima
  let rainOn = false, rainPool = null;
  let windOn = 0, windPool = null;
  let ashOn = false, ashPool = null;

  function spawnParticle(mesh, vel, opts) {
    opts = opts || {};
    scene.add(mesh);
    particles.push({
      mesh, vel,
      rot: opts.rot || new THREE.Vector3((Math.random() - 0.5) * 8, (Math.random() - 0.5) * 8, (Math.random() - 0.5) * 8),
      gravity: opts.gravity !== undefined ? opts.gravity : 12,
      life: opts.life || 1.0,
      maxLife: opts.life || 1.0,
      grow: opts.grow || 0,
      ringTo: opts.ringTo || 0,
      shrink: opts.shrink || false,
    });
  }

  function basicMat(color) {
    return new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 1 });
  }

  function disposeDecal(d) {
    scene.remove(d.mesh);
    if (d.isGroup) d.mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); if (o.material) o.material.dispose(); });
    else { d.mesh.geometry.dispose(); d.mesh.material.dispose(); }
  }

  function addDecal(mesh, life, opts) {
    scene.add(mesh);
    decals.push(Object.assign({ mesh, life, maxLife: life, baseOpacity: mesh.material.opacity }, opts || {}));
    if (decals.length > 40) disposeDecal(decals.shift());
  }

  function initRain() {
    rainPool = [];
    const geo = new THREE.BoxGeometry(0.035, 0.6, 0.035);
    for (let i = 0; i < 80; i++) {
      const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: 0xbfe6ff, transparent: true, opacity: 0.45 }));
      m.visible = false;
      scene.add(m);
      rainPool.push({ mesh: m, x: 0, y: Math.random() * 11, z: 0, vx: -1.5 });
      rainPool[i].x = (Math.random() - 0.5) * 18;
      rainPool[i].z = (Math.random() - 0.5) * 22;
    }
  }

  function initAsh() {
    ashPool = [];
    const geo = new THREE.BoxGeometry(0.09, 0.09, 0.09);
    for (let i = 0; i < 50; i++) {
      const gray = Math.random() < 0.7 ? 0x8a8a92 : 0x55555d;
      const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: gray, transparent: true, opacity: 0.7 }));
      m.visible = false;
      scene.add(m);
      ashPool.push({ mesh: m, x: (Math.random() - 0.5) * 18, y: Math.random() * 10, z: (Math.random() - 0.5) * 22, ph: Math.random() * 6 });
    }
  }

  function initWind() {
    windPool = [];
    const geo = new THREE.BoxGeometry(1.1, 0.04, 0.04);
    for (let i = 0; i < 36; i++) {
      const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: 0xdfe9f2, transparent: true, opacity: 0.35 }));
      m.visible = false;
      scene.add(m);
      windPool.push({ mesh: m, x: (Math.random() - 0.5) * 24, y: 0.4 + Math.random() * 3.2, z: (Math.random() - 0.5) * 22, sp: 9 + Math.random() * 7 });
    }
  }

  return {
    init(sc) { scene = sc; },

    clear() {
      for (const p of particles) {
        scene.remove(p.mesh);
        if (p.isGroup) p.mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
        else { p.mesh.geometry.dispose(); p.mesh.material.dispose(); }
      }
      particles.length = 0;
      for (const d of decals) disposeDecal(d);
      decals.length = 0;
      shakeMag = 0; shakeSustain = 0; rollSustain = 0;
      this.setRain(false); this.setWind(0); this.setAsh(false);
    },

    /* ---------- partículas ---------- */

    feathers(pos, n) {
      n = n || 16;
      for (let i = 0; i < n; i++) {
        const m = new THREE.Mesh(new THREE.PlaneGeometry(0.16, 0.16), basicMat(0xffffff));
        m.material.side = THREE.DoubleSide;
        m.position.copy(pos).add(new THREE.Vector3(0, 0.4, 0));
        const vel = new THREE.Vector3((Math.random() - 0.5) * 5, 2 + Math.random() * 4, (Math.random() - 0.5) * 5);
        spawnParticle(m, vel, { gravity: 6, life: 0.9 + Math.random() * 0.6 });
      }
    },

    splash(pos) {
      for (let i = 0; i < 14; i++) {
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.12, 0.12), basicMat(0x9fe4ff));
        m.position.copy(pos);
        const vel = new THREE.Vector3((Math.random() - 0.5) * 4, 3 + Math.random() * 3.5, (Math.random() - 0.5) * 4);
        spawnParticle(m, vel, { gravity: 14, life: 0.7 });
      }
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(0.2, 0.35, 20),
        new THREE.MeshBasicMaterial({ color: 0xdff6ff, transparent: true, opacity: 0.9, side: THREE.DoubleSide, depthWrite: false })
      );
      ring.rotation.x = -Math.PI / 2;
      ring.position.set(pos.x, World.TOP_Y.water + 0.02, pos.z);
      spawnParticle(ring, new THREE.Vector3(0, 0, 0), { gravity: 0, life: 0.5, ringTo: 3.5, rot: new THREE.Vector3(0, 0, 0) });
    },

    explosion(pos, scale) {
      scale = scale || 1;
      const colors = [0xff6a1a, 0xffd23b, 0x555560, 0x333338];
      for (let i = 0; i < 22; i++) {
        const c = colors[(Math.random() * colors.length) | 0];
        const s = (0.12 + Math.random() * 0.18) * scale;
        const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), basicMat(c));
        m.position.copy(pos);
        const vel = new THREE.Vector3((Math.random() - 0.5) * 7 * scale, (2 + Math.random() * 6) * scale, (Math.random() - 0.5) * 7 * scale);
        spawnParticle(m, vel, { gravity: 13, life: 0.8 + Math.random() * 0.5 });
      }
    },

    poof(pos, color) {
      for (let i = 0; i < 10; i++) {
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.14, 0.14), basicMat(color || 0xcccccc));
        m.position.copy(pos).add(new THREE.Vector3(0, 0.3, 0));
        const vel = new THREE.Vector3((Math.random() - 0.5) * 4, 1.5 + Math.random() * 3, (Math.random() - 0.5) * 4);
        spawnParticle(m, vel, { gravity: 8, life: 0.6 });
      }
    },

    smoke(pos, n, dark) {
      for (let i = 0; i < (n || 6); i++) {
        const s = 0.2 + Math.random() * 0.25;
        const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), basicMat(dark ? 0x4a4a52 : 0x8a8a92));
        m.material.opacity = 0.7;
        m.position.copy(pos).add(new THREE.Vector3((Math.random() - 0.5) * 0.5, 0, (Math.random() - 0.5) * 0.5));
        const vel = new THREE.Vector3((Math.random() - 0.5) * 1, 1.4 + Math.random() * 1.8, (Math.random() - 0.5) * 1);
        spawnParticle(m, vel, { gravity: -1.5, life: 1.2, grow: 1.5 });
      }
    },

    /* llamas que suben y se encogen */
    fire(pos, n) {
      for (let i = 0; i < (n || 3); i++) {
        const c = Math.random() < 0.5 ? 0xff6a1a : 0xffd23b;
        const s = 0.14 + Math.random() * 0.14;
        const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), basicMat(c));
        m.position.copy(pos).add(new THREE.Vector3((Math.random() - 0.5) * 0.3, 0, (Math.random() - 0.5) * 0.3));
        const vel = new THREE.Vector3((Math.random() - 0.5) * 0.8, 1.6 + Math.random() * 1.6, (Math.random() - 0.5) * 0.8);
        spawnParticle(m, vel, { gravity: -2, life: 0.45 + Math.random() * 0.3, shrink: true });
      }
    },

    /* chispas amarillas rasantes */
    sparks(pos, n) {
      for (let i = 0; i < (n || 6); i++) {
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.07, 0.07, 0.07), basicMat(0xffe14d));
        m.position.copy(pos);
        const vel = new THREE.Vector3((Math.random() - 0.5) * 8, 0.5 + Math.random() * 2.5, (Math.random() - 0.5) * 8);
        spawnParticle(m, vel, { gravity: 9, life: 0.35 });
      }
    },

    dust(pos) {
      for (let i = 0; i < 5; i++) {
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.09, 0.09, 0.09), basicMat(0xd8cfa8));
        m.material.opacity = 0.8;
        m.position.copy(pos).add(new THREE.Vector3((Math.random() - 0.5) * 0.4, 0.06, (Math.random() - 0.5) * 0.4));
        const vel = new THREE.Vector3((Math.random() - 0.5) * 1.6, 0.7 + Math.random() * 0.8, (Math.random() - 0.5) * 1.6);
        spawnParticle(m, vel, { gravity: 3, life: 0.4, grow: 1.2 });
      }
    },

    sparkle(pos) {
      for (let i = 0; i < 10; i++) {
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.1, 0.1), basicMat(i % 2 ? 0xffe14d : 0xfff6a8));
        m.position.copy(pos).add(new THREE.Vector3(0, 0.3, 0));
        const a = (i / 10) * Math.PI * 2;
        const vel = new THREE.Vector3(Math.cos(a) * 2.2, 2.5 + Math.random() * 1.5, Math.sin(a) * 2.2);
        spawnParticle(m, vel, { gravity: 7, life: 0.55 });
      }
    },

    confetti(center) {
      const colors = [0xe8552d, 0xf0c229, 0x35b34a, 0x3b7bd9, 0xd94f8a, 0x8a5cd9];
      for (let i = 0; i < 70; i++) {
        const m = new THREE.Mesh(new THREE.PlaneGeometry(0.14, 0.2), basicMat(colors[i % colors.length]));
        m.material.side = THREE.DoubleSide;
        m.position.set(
          center.x + (Math.random() - 0.5) * 10,
          6 + Math.random() * 3,
          center.z + (Math.random() - 0.5) * 10
        );
        const vel = new THREE.Vector3((Math.random() - 0.5) * 1.5, -1 - Math.random() * 1.2, (Math.random() - 0.5) * 1.5);
        spawnParticle(m, vel, { gravity: 0.4, life: 2.5 + Math.random() * 1.5 });
      }
    },

    tumble(mesh, vel) {
      scene.add(mesh);
      particles.push({
        mesh, vel,
        rot: new THREE.Vector3((Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12),
        gravity: 11,
        life: 1.6, maxLife: 1.6,
        grow: 0, ringTo: 0,
        isGroup: true,
      });
    },

    /* ---------- marcas en el suelo ---------- */

    scorch(pos, radius) {
      const m = new THREE.Mesh(
        new THREE.CircleGeometry(radius || 0.55, 16),
        new THREE.MeshBasicMaterial({ color: 0x1a1a1a, transparent: true, opacity: 0.55, depthWrite: false })
      );
      m.rotation.x = -Math.PI / 2;
      m.position.set(pos.x, Math.max(pos.y, 0) + 0.09, pos.z);
      addDecal(m, 8);
    },

    /* charco de lava brillante que palpita */
    lavaPool(pos, radius) {
      const m = new THREE.Mesh(
        new THREE.CircleGeometry(radius || 0.5, 16),
        new THREE.MeshBasicMaterial({ color: 0xff6a1a, transparent: true, opacity: 0.85, depthWrite: false })
      );
      m.rotation.x = -Math.PI / 2;
      m.position.set(pos.x, Math.max(pos.y, 0) + 0.1, pos.z);
      addDecal(m, 5, { pulse: true });
    },

    /* grieta: línea oscura (o incandescente) en el suelo */
    crack(pos, angle, len, glow) {
      const g = new THREE.Group();
      const segs = 3 + ((Math.random() * 3) | 0);
      let x = 0;
      const segLen = (len || 2) / segs;
      for (let i = 0; i < segs; i++) {
        const b = new THREE.Mesh(
          new THREE.BoxGeometry(segLen, 0.02, 0.12 + Math.random() * 0.08),
          new THREE.MeshBasicMaterial({ color: glow ? 0xff4400 : 0x181820, transparent: true, opacity: glow ? 0.95 : 0.8, depthWrite: false })
        );
        b.position.set(x + segLen / 2, 0, (Math.random() - 0.5) * 0.25);
        g.add(b);
        x += segLen;
      }
      g.rotation.y = angle;
      g.position.set(pos.x, Math.max(pos.y, 0) + 0.1, pos.z);
      // envolver en un mesh "fantasma" para addDecal (usa material/geometry del primer hijo)
      scene.add(g);
      decals.push({ mesh: g, life: glow ? 4 : 7, maxLife: glow ? 4 : 7, baseOpacity: 0.9, isGroup: true, pulse: glow });
      if (decals.length > 40) disposeDecal(decals.shift());
      return g;
    },

    /* marca de derrape */
    skid(pos, dir) {
      const m = new THREE.Mesh(
        new THREE.BoxGeometry(1.6, 0.02, 0.14),
        new THREE.MeshBasicMaterial({ color: 0x222228, transparent: true, opacity: 0.5, depthWrite: false })
      );
      m.position.set(pos.x, 0.085, pos.z + (Math.random() < 0.5 ? 0.3 : -0.3));
      addDecal(m, 6);
    },

    shockwave(pos, color) {
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(0.3, 0.6, 28),
        new THREE.MeshBasicMaterial({ color: color || 0xffaa44, transparent: true, opacity: 0.85, side: THREE.DoubleSide, depthWrite: false })
      );
      ring.rotation.x = -Math.PI / 2;
      ring.position.set(pos.x, 0.15, pos.z);
      spawnParticle(ring, new THREE.Vector3(0, 0, 0), { gravity: 0, life: 0.7, ringTo: 8, rot: new THREE.Vector3(0, 0, 0) });
    },

    /* ---------- clima ---------- */

    setRain(on) {
      rainOn = on;
      if (on && !rainPool) initRain();
      if (rainPool) for (const r of rainPool) r.mesh.visible = on;
    },

    setWind(dir) {
      windOn = dir;
      if (dir && !windPool) initWind();
      if (windPool) for (const w of windPool) w.mesh.visible = !!dir;
    },

    setAsh(on) {
      ashOn = on;
      if (on && !ashPool) initAsh();
      if (ashPool) for (const a of ashPool) a.mesh.visible = on;
    },

    /* ---------- cámara ---------- */

    shake(mag, dur) {
      shakeMag = Math.max(shakeMag, mag);
      shakeUntil = performance.now() / 1000 + dur;
    },
    sustainShake(mag) { shakeSustain = Math.max(shakeSustain, mag); },
    sustainRoll(rad) { rollSustain = rad; },

    getShakeOffset() {
      const now = performance.now() / 1000;
      let mag = 0;
      if (now < shakeUntil) mag = shakeMag * Math.min(1, (shakeUntil - now));
      else shakeMag = 0;
      mag = Math.max(mag, shakeSustain);
      shakeSustain = 0;
      if (mag <= 0) return { x: 0, y: 0 };
      return { x: (Math.random() - 0.5) * mag, y: (Math.random() - 0.5) * mag };
    },

    getRoll() {
      const r = rollSustain;
      rollSustain = 0;
      return r;
    },

    /* ---------- bucle ---------- */

    update(dt, fx, fr) {
      focusX = fx !== undefined ? fx : focusX;
      focusRow = fr !== undefined ? fr : focusRow;
      const now = performance.now() / 1000;

      // lluvia
      if (rainPool && rainOn) {
        for (const r of rainPool) {
          r.y -= 15 * dt;
          r.x += r.vx * dt;
          if (r.y < 0) {
            r.y = 9 + Math.random() * 3;
            r.x = focusX + (Math.random() - 0.5) * 18;
            r.z = -focusRow + (Math.random() - 0.5) * 22;
          }
          r.mesh.position.set(r.x, r.y, r.z);
        }
      }
      // ceniza volcánica cayendo con vaivén
      if (ashPool && ashOn) {
        for (const a of ashPool) {
          a.y -= 1.6 * dt;
          a.x += Math.sin(now * 1.5 + a.ph) * 0.5 * dt;
          if (a.y < 0) {
            a.y = 8 + Math.random() * 3;
            a.x = focusX + (Math.random() - 0.5) * 18;
            a.z = -focusRow + (Math.random() - 0.5) * 22;
          }
          a.mesh.position.set(a.x, a.y, a.z);
          a.mesh.rotation.x += dt; a.mesh.rotation.z += 0.7 * dt;
        }
      }
      // ráfagas de viento
      if (windPool && windOn) {
        for (const w of windPool) {
          w.x += w.sp * windOn * dt;
          if (Math.abs(w.x - focusX) > 14) {
            w.x = focusX - windOn * 14;
            w.y = 0.4 + Math.random() * 3.2;
            w.z = -focusRow + (Math.random() - 0.5) * 22;
          }
          w.mesh.position.set(w.x, w.y, w.z);
          w.mesh.material.opacity = 0.18 + Math.random() * 0.22;
        }
      }

      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.life -= dt;
        if (p.life <= 0) {
          scene.remove(p.mesh);
          if (p.isGroup) p.mesh.traverse(o => { if (o.geometry) o.geometry.dispose(); });
          else { p.mesh.geometry.dispose(); p.mesh.material.dispose(); }
          particles.splice(i, 1);
          continue;
        }
        p.vel.y -= p.gravity * dt;
        p.mesh.position.addScaledVector(p.vel, dt);
        p.mesh.rotation.x += p.rot.x * dt;
        p.mesh.rotation.y += p.rot.y * dt;
        p.mesh.rotation.z += p.rot.z * dt;
        if (p.ringTo) {
          const k = 1 - p.life / p.maxLife;
          p.mesh.scale.setScalar(1 + (p.ringTo - 1) * k);
          p.mesh.material.opacity = Math.max(0, (p.life / p.maxLife)) * 0.85;
          continue;
        }
        if (p.grow) p.mesh.scale.multiplyScalar(1 + p.grow * dt);
        if (p.shrink) p.mesh.scale.multiplyScalar(Math.max(0.01, 1 - 2.2 * dt));
        if (!p.isGroup) p.mesh.material.opacity = Math.min(1, p.life / (p.maxLife * 0.5));
        else if (p.life < 0.3) p.mesh.scale.multiplyScalar(Math.max(0.01, p.life / 0.3));
      }

      for (let i = decals.length - 1; i >= 0; i--) {
        const d = decals[i];
        d.life -= dt;
        if (d.life <= 0) {
          disposeDecal(d);
          decals.splice(i, 1);
          continue;
        }
        let op = d.baseOpacity !== undefined ? d.baseOpacity : 0.55;
        if (d.pulse) op *= 0.65 + Math.sin(now * 6 + i) * 0.35;
        if (d.life < 2) op *= d.life / 2;
        if (d.isGroup) d.mesh.traverse(o => { if (o.material) o.material.opacity = op; });
        else d.mesh.material.opacity = op;
      }
    },
  };
})();
