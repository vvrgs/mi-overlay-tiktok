/* ============================================================
 * EFECTOS — partículas (plumas, agua, explosiones), marcas en
 * el suelo y sacudida de cámara.
 * ============================================================ */
const Effects = (() => {
  let scene = null;
  const particles = [];
  const decals = [];
  let shakeMag = 0, shakeUntil = 0, shakeSustain = 0;

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
    });
  }

  function basicMat(color) {
    return new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 1 });
  }

  return {
    init(sc) { scene = sc; },

    clear() {
      for (const p of particles) { scene.remove(p.mesh); p.mesh.geometry.dispose(); p.mesh.material.dispose(); }
      particles.length = 0;
      for (const d of decals) { scene.remove(d.mesh); d.mesh.geometry.dispose(); d.mesh.material.dispose(); }
      decals.length = 0;
      shakeMag = 0; shakeSustain = 0;
    },

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
      spawnParticle(ring, new THREE.Vector3(0, 0, 0), { gravity: 0, life: 0.5, grow: 5, rot: new THREE.Vector3(0, 0, 0) });
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

    smoke(pos, n) {
      for (let i = 0; i < (n || 6); i++) {
        const s = 0.2 + Math.random() * 0.25;
        const m = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), basicMat(0x8a8a92));
        m.material.opacity = 0.7;
        m.position.copy(pos).add(new THREE.Vector3((Math.random() - 0.5) * 0.5, 0, (Math.random() - 0.5) * 0.5));
        const vel = new THREE.Vector3((Math.random() - 0.5) * 1, 1.2 + Math.random() * 1.5, (Math.random() - 0.5) * 1);
        spawnParticle(m, vel, { gravity: -1.5, life: 1.2, grow: 1.5 });
      }
    },

    scorch(pos, radius) {
      const m = new THREE.Mesh(
        new THREE.CircleGeometry(radius || 0.55, 16),
        new THREE.MeshBasicMaterial({ color: 0x1a1a1a, transparent: true, opacity: 0.55, depthWrite: false })
      );
      m.rotation.x = -Math.PI / 2;
      m.position.set(pos.x, Math.max(pos.y, 0) + 0.09, pos.z);
      scene.add(m);
      decals.push({ mesh: m, life: 8 });
      if (decals.length > 24) {
        const old = decals.shift();
        scene.remove(old.mesh); old.mesh.geometry.dispose(); old.mesh.material.dispose();
      }
    },

    shockwave(pos, color) {
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(0.3, 0.6, 28),
        new THREE.MeshBasicMaterial({ color: color || 0xffaa44, transparent: true, opacity: 0.85, side: THREE.DoubleSide, depthWrite: false })
      );
      ring.rotation.x = -Math.PI / 2;
      ring.position.set(pos.x, 0.15, pos.z);
      spawnParticle(ring, new THREE.Vector3(0, 0, 0), { gravity: 0, life: 0.8, grow: 12, rot: new THREE.Vector3(0, 0, 0) });
    },

    shake(mag, dur) {
      shakeMag = Math.max(shakeMag, mag);
      shakeUntil = performance.now() / 1000 + dur;
    },
    /* sacudida continua mientras se llame cada frame */
    sustainShake(mag) { shakeSustain = Math.max(shakeSustain, mag); },

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

    update(dt) {
      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.life -= dt;
        if (p.life <= 0) {
          scene.remove(p.mesh); p.mesh.geometry.dispose(); p.mesh.material.dispose();
          particles.splice(i, 1);
          continue;
        }
        p.vel.y -= p.gravity * dt;
        p.mesh.position.addScaledVector(p.vel, dt);
        p.mesh.rotation.x += p.rot.x * dt;
        p.mesh.rotation.y += p.rot.y * dt;
        p.mesh.rotation.z += p.rot.z * dt;
        if (p.grow) {
          const s = 1 + p.grow * dt;
          p.mesh.scale.multiplyScalar(s);
        }
        p.mesh.material.opacity = Math.min(1, p.life / (p.maxLife * 0.5));
      }
      for (let i = decals.length - 1; i >= 0; i--) {
        const d = decals[i];
        d.life -= dt;
        if (d.life <= 0) {
          scene.remove(d.mesh); d.mesh.geometry.dispose(); d.mesh.material.dispose();
          decals.splice(i, 1);
        } else if (d.life < 2) {
          d.mesh.material.opacity = 0.55 * (d.life / 2);
        }
      }
    },
  };
})();
