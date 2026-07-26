/**
 * Dirección de cámara cinematográfica.
 *
 * Nadie está detrás de los controles durante un directo de 8 horas, así que la
 * cámara se dirige sola: encuadra donde de verdad está muriendo gente (el
 * "hotspot" que calcula el worker), corta cada pocos segundos con planos
 * variados y se sacude cuando cae una ultimate.
 */

import * as THREE from 'three';
import type { GameConfig } from '../shared/config';
import { clamp, createRng, damp, type Rng } from '../shared/math';
import type { Terrain } from '../shared/terrain';

type ShotKind = 'wide' | 'low' | 'overhead' | 'closeup' | 'flank';

interface Shot {
  kind: ShotKind;
  angle: number;
  distance: number;
  height: number;
  orbitSpeed: number;
  duration: number;
}

export class CameraDirector {
  private rng: Rng;
  private shot: Shot;
  private shotTimer = 0;
  private focus = new THREE.Vector3();
  private desiredFocus = new THREE.Vector3();
  private position = new THREE.Vector3();
  private shakeAmount = 0;
  private shakeOffset = new THREE.Vector3();
  private championFocus: THREE.Vector3 | null = null;
  private manualMode: GameConfig['camera']['mode'];

  constructor(
    private camera: THREE.PerspectiveCamera,
    private terrain: Terrain,
    private config: GameConfig,
  ) {
    this.rng = createRng(config.simulator.seed + 7);
    this.manualMode = config.camera.mode;
    this.shot = this.pickShot();
    this.focus.set(0, 0, 0);
    this.position.set(-90, 40, 90);
    camera.position.copy(this.position);
    camera.lookAt(this.focus);
  }

  updateConfig(config: GameConfig): void {
    this.config = config;
    this.manualMode = config.camera.mode;
    this.camera.fov = config.camera.fov;
    this.camera.updateProjectionMatrix();
  }

  setMode(mode: GameConfig['camera']['mode']): void {
    this.manualMode = mode;
    this.shotTimer = 0;
    this.shot = this.pickShot();
  }

  private pickShot(): Shot {
    const cam = this.config.camera;
    const [minH, maxH] = cam.heightRange;
    const [minD, maxD] = cam.distanceRange;
    const duration = this.rng.range(cam.minCutSeconds, cam.maxCutSeconds);

    // Modos manuales: un solo tipo de plano, sin cortes.
    if (this.manualMode === 'orbit') {
      return { kind: 'wide', angle: this.shot?.angle ?? 0, distance: (minD + maxD) / 2, height: (minH + maxH) / 2, orbitSpeed: 0.12, duration: Infinity };
    }
    if (this.manualMode === 'follow') {
      return { kind: 'low', angle: this.shot?.angle ?? 0, distance: minD * 0.6, height: minH * 0.45, orbitSpeed: 0.05, duration: Infinity };
    }

    const roll = this.rng();
    let kind: ShotKind;
    if (roll < 0.28) kind = 'wide';
    else if (roll < 0.52) kind = 'low';
    else if (roll < 0.7) kind = 'flank';
    else if (roll < 0.86) kind = 'overhead';
    else kind = 'closeup';

    // Los planos cercanos solo tienen sentido si hay un campeón al que seguir.
    if (kind === 'closeup' && (!this.championFocus || this.rng() > this.config.camera.championCloseupChance + 0.3)) {
      kind = 'wide';
    }

    const angle = this.rng.range(0, Math.PI * 2);
    const orbitSpeed = this.rng.range(-0.09, 0.09);

    switch (kind) {
      case 'low':
        // Nunca tan cerca como para que un soldado del primer plano tape el campo.
        return { kind, angle, distance: this.rng.range(minD * 0.8, minD * 1.25), height: this.rng.range(6, 13), orbitSpeed: orbitSpeed * 1.6, duration };
      case 'overhead':
        return { kind, angle, distance: this.rng.range(minD * 0.5, maxD * 0.6), height: this.rng.range(maxH * 0.85, maxH * 1.3), orbitSpeed, duration };
      case 'flank':
        // Plano lateral, casi en paralelo a la línea de frente.
        return { kind, angle: (this.rng() < 0.5 ? 0 : Math.PI) + this.rng.range(-0.3, 0.3), distance: this.rng.range(minD, maxD * 0.8), height: this.rng.range(minH * 0.5, minH), orbitSpeed: orbitSpeed * 0.4, duration };
      case 'closeup':
        return { kind, angle, distance: this.rng.range(15, 26), height: this.rng.range(5, 10), orbitSpeed: orbitSpeed * 2.2, duration: duration * 0.6 };
      case 'wide':
      default:
        return { kind, angle, distance: this.rng.range(maxD * 0.7, maxD), height: this.rng.range(minH, maxH), orbitSpeed, duration };
    }
  }

  /** Punto de interés que publica el worker (donde más bajas hay). */
  setHotspot(x: number, z: number, intensity: number): void {
    if (intensity <= 0 || !this.config.camera.focusHottestZone) return;
    this.desiredFocus.set(x, this.terrain.height(x, z), z);
  }

  setChampionFocus(point: THREE.Vector3 | null): void {
    this.championFocus = point;
  }

  /** Sacudida de cámara: la disparan los meteoros y las ultimates. */
  shake(amount: number): void {
    if (!this.config.camera.shakeEnabled) return;
    this.shakeAmount = Math.min(2.5, this.shakeAmount + amount);
  }

  /** Corta a un plano dramático (se usa al lanzar una ultimate). */
  cutToDramatic(x: number, z: number): void {
    if (!this.config.camera.ultimateZoom) return;
    this.desiredFocus.set(x, this.terrain.height(x, z), z);
    this.focus.copy(this.desiredFocus);
    this.shot = { kind: 'low', angle: this.rng.range(0, Math.PI * 2), distance: 34, height: 12, orbitSpeed: 0.14, duration: 5 };
    this.shotTimer = 0;
  }

  update(dt: number): void {
    if (this.manualMode === 'fixed') {
      this.camera.position.set(0, 95, 155);
      this.camera.lookAt(0, 0, 0);
      return;
    }

    this.shotTimer += dt;
    if (this.manualMode === 'cinematic' && this.shotTimer >= this.shot.duration) {
      this.shot = this.pickShot();
      this.shotTimer = 0;
    }

    // El plano cercano sigue a un campeón concreto si hay alguno vivo.
    const targetFocus =
      this.shot.kind === 'closeup' && this.championFocus ? this.championFocus : this.desiredFocus;

    this.focus.x = damp(this.focus.x, targetFocus.x, 1.5, dt);
    this.focus.y = damp(this.focus.y, targetFocus.y, 1.5, dt);
    this.focus.z = damp(this.focus.z, targetFocus.z, 1.5, dt);

    this.shot.angle += this.shot.orbitSpeed * dt;

    // TikTok es vertical: con un encuadre 9:16 el campo de visión HORIZONTAL es
    // mucho más estrecho que en 16:9, y con la misma distancia solo se vería una
    // rebanada del ejército. Se aleja la cámara en proporción al aspecto para que
    // el plano abarque lo mismo sin importar la forma del lienzo.
    const aspectBoost = clamp(1 / Math.max(0.35, this.camera.aspect), 1, 2.4);
    const distance = this.shot.distance * aspectBoost;
    const height = this.shot.height * (0.65 + aspectBoost * 0.45);

    const desiredX = this.focus.x + Math.cos(this.shot.angle) * distance;
    const desiredZ = this.focus.z + Math.sin(this.shot.angle) * distance;
    const groundY = this.terrain.height(desiredX, desiredZ);
    const desiredY = Math.max(groundY + 3, this.focus.y + height);

    // Al cortar, la cámara viaja rápido pero suavizada; dentro del plano se mueve lento.
    const lambda = this.shotTimer < 0.6 ? 6.5 : 2.2;
    this.position.x = damp(this.position.x, desiredX, lambda, dt);
    this.position.y = damp(this.position.y, desiredY, lambda, dt);
    this.position.z = damp(this.position.z, desiredZ, lambda, dt);

    if (this.shakeAmount > 0.001) {
      const s = this.shakeAmount;
      this.shakeOffset.set(
        (this.rng() - 0.5) * s * 2.2,
        (this.rng() - 0.5) * s * 1.6,
        (this.rng() - 0.5) * s * 2.2,
      );
      this.shakeAmount = damp(this.shakeAmount, 0, 3.2, dt);
    } else {
      this.shakeOffset.setScalar(0);
    }

    this.camera.position.copy(this.position).add(this.shakeOffset);
    this.camera.lookAt(this.focus.x, this.focus.y + 2.5, this.focus.z);
  }

  reset(): void {
    this.desiredFocus.set(0, this.terrain.height(0, 0), 0);
    this.focus.copy(this.desiredFocus);
    this.shot = this.pickShot();
    this.shotTimer = 0;
    this.shakeAmount = 0;
  }

  setTerrain(terrain: Terrain): void {
    this.terrain = terrain;
  }

  get currentFocus(): THREE.Vector3 {
    return this.focus;
  }

  /** Distancia normalizada al foco: la usa la HUD para atenuar los nametags. */
  focusDistanceRatio(): number {
    return clamp(this.camera.position.distanceTo(this.focus) / this.config.camera.distanceRange[1], 0, 2);
  }
}
