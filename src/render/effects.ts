/**
 * Efectos: sangre, cadáveres, proyectiles, meteoros y explosiones.
 *
 * Todos son sistemas instanciados con la animación resuelta en el shader a
 * partir del tiempo de nacimiento de cada partícula. La CPU solo escribe una
 * vez, al momento de crear el efecto; después no vuelve a tocarlo. Eso permite
 * miles de partículas simultáneas sin coste por frame.
 */

import * as THREE from 'three';
import { createRng, type Rng } from '../shared/math';

/** Quad centrado, base de todos los billboards. */
function quadGeometry(): THREE.BufferGeometry {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute([-0.5, -0.5, 0, 0.5, -0.5, 0, 0.5, 0.5, 0, -0.5, 0.5, 0], 3));
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute([0, 0, 1, 0, 1, 1, 0, 1], 2));
  geometry.setIndex([0, 1, 2, 0, 2, 3]);
  return geometry;
}

function instanced(base: THREE.BufferGeometry): THREE.InstancedBufferGeometry {
  const geometry = new THREE.InstancedBufferGeometry();
  geometry.index = base.index;
  geometry.setAttribute('position', base.getAttribute('position'));
  if (base.getAttribute('uv')) geometry.setAttribute('uv', base.getAttribute('uv'));
  geometry.instanceCount = 0;
  geometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);
  return geometry;
}

/** Buffer de instancias en anillo: al llenarse sobreescribe las más viejas. */
class RingAttribute {
  readonly attribute: THREE.InstancedBufferAttribute;
  private cursor = 0;

  constructor(readonly capacity: number, readonly itemSize: number) {
    this.attribute = new THREE.InstancedBufferAttribute(new Float32Array(capacity * itemSize), itemSize);
    this.attribute.setUsage(THREE.DynamicDrawUsage);
  }

  slot(): number {
    const slot = this.cursor;
    this.cursor = (this.cursor + 1) % this.capacity;
    return slot;
  }

  write(slot: number, values: number[]): void {
    const array = this.attribute.array as Float32Array;
    array.set(values, slot * this.itemSize);
    this.attribute.needsUpdate = true;
  }

  clear(): void {
    (this.attribute.array as Float32Array).fill(0);
    this.attribute.needsUpdate = true;
    this.cursor = 0;
  }
}

// ---------------------------------------------------------------- sangre

const BLOOD_VERTEX = /* glsl */ `
  attribute vec3 aOrigin;
  attribute vec3 aVel;
  attribute vec2 aMeta;      // x = nacimiento, y = tamaño
  uniform float uTime;
  varying float vLife;
  varying vec2 vLocal;

  void main() {
    float age = uTime - aMeta.x;
    float life = clamp(age / 1.1, 0.0, 1.0);
    vLife = life;
    vLocal = position.xy;
    // Balística simple con gravedad.
    vec3 world = aOrigin + aVel * age + vec3(0.0, -9.8 * 0.5 * age * age, 0.0);
    world.y = max(world.y, aOrigin.y - 0.4);
    vec4 mv = modelViewMatrix * vec4(world, 1.0);
    // Billboard: el quad se expande en el espacio de la cámara.
    float size = aMeta.y * (1.0 - life * 0.35);
    mv.xy += position.xy * size;
    gl_Position = projectionMatrix * mv;
  }
`;

const BLOOD_FRAGMENT = /* glsl */ `
  precision mediump float;
  varying float vLife;
  varying vec2 vLocal;
  void main() {
    if (vLife >= 1.0) discard;
    // Máscara radial: sin ella, cada partícula sería un cuadrado opaco.
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    float alpha = (1.0 - vLife) * 0.9 * smoothstep(1.0, 0.25, d);
    gl_FragColor = vec4(0.42, 0.03, 0.03, alpha);
  }
`;

// ------------------------------------------------------------- cadáveres

const CORPSE_VERTEX = /* glsl */ `
  attribute vec4 aPose;   // xyz = posición, w = rotación
  attribute vec2 aMeta;   // x = nacimiento, y = escala
  uniform float uTime;
  varying float vFade;
  varying vec2 vLocal;

  void main() {
    float age = uTime - aMeta.x;
    vFade = clamp(1.0 - age / 90.0, 0.0, 1.0);
    vLocal = position.xy;
    float s = sin(aPose.w), c = cos(aPose.w);
    vec3 local = vec3(position.x * aMeta.y * 1.6, 0.0, position.y * aMeta.y * 0.7);
    vec3 rotated = vec3(local.x * c + local.z * s, 0.0, -local.x * s + local.z * c);
    vec3 world = aPose.xyz + rotated + vec3(0.0, 0.08, 0.0);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(world, 1.0);
  }
`;

const CORPSE_FRAGMENT = /* glsl */ `
  precision mediump float;
  uniform vec3 uColor;
  varying float vFade;
  varying vec2 vLocal;
  void main() {
    if (vFade <= 0.0) discard;
    // Silueta ovalada tumbada, no un rectángulo.
    float d = length(vLocal * vec2(1.9, 2.4));
    if (d > 1.0) discard;
    gl_FragColor = vec4(uColor, 0.72 * vFade * smoothstep(1.0, 0.55, d));
  }
`;

// ----------------------------------------------------------- proyectiles

const PROJECTILE_VERTEX = /* glsl */ `
  attribute vec4 aData;  // xyz = posición, w = tipo
  varying float vKind;
  varying vec2 vLocal;
  void main() {
    vKind = aData.w;
    vLocal = position.xy;
    float size = aData.w > 1.5 ? 1.1 : aData.w > 0.5 ? 0.55 : 0.3;
    vec4 mv = modelViewMatrix * vec4(aData.xyz, 1.0);
    mv.xy += position.xy * size;
    gl_Position = projectionMatrix * mv;
  }
`;

const PROJECTILE_FRAGMENT = /* glsl */ `
  precision mediump float;
  varying float vKind;
  varying vec2 vLocal;
  void main() {
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    vec3 color = vKind > 2.5 ? vec3(1.0, 0.45, 0.12)
               : vKind > 1.5 ? vec3(0.55, 0.75, 1.0)
               : vec3(0.9, 0.85, 0.7);
    gl_FragColor = vec4(color, 0.95 * smoothstep(1.0, 0.4, d));
  }
`;

// ------------------------------------------------- meteoros y explosiones

const METEOR_VERTEX = /* glsl */ `
  attribute vec4 aTarget;  // xyz = punto de impacto, w = nacimiento
  attribute vec2 aMeta;    // x = retardo, y = radio
  uniform float uTime;
  varying float vGlow;
  varying vec2 vLocal;

  void main() {
    vLocal = position.xy;
    float age = uTime - aTarget.w;
    float t = clamp(age / max(aMeta.x, 0.05), 0.0, 1.0);
    vGlow = (t < 1.0 && age >= 0.0) ? 1.0 : 0.0;
    // Cae desde el cielo describiendo una diagonal.
    vec3 from = aTarget.xyz + vec3(38.0, 190.0, -26.0);
    vec3 world = mix(from, aTarget.xyz, t * t);
    vec4 mv = modelViewMatrix * vec4(world, 1.0);
    mv.xy += position.xy * aMeta.y * 0.55;
    gl_Position = projectionMatrix * mv;
  }
`;

const METEOR_FRAGMENT = /* glsl */ `
  precision mediump float;
  varying float vGlow;
  varying vec2 vLocal;
  void main() {
    if (vGlow <= 0.0) discard;
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    // Núcleo blanco incandescente que se degrada a naranja.
    vec3 color = mix(vec3(1.0, 0.95, 0.75), vec3(1.0, 0.4, 0.05), smoothstep(0.0, 0.7, d));
    gl_FragColor = vec4(color, 0.95 * smoothstep(1.0, 0.15, d));
  }
`;

const EXPLOSION_VERTEX = /* glsl */ `
  attribute vec4 aBurst;  // xyz = centro, w = nacimiento
  attribute vec2 aMeta;   // x = radio, y = tipo
  uniform float uTime;
  varying float vLife;
  varying float vKind;
  varying vec2 vLocal;

  void main() {
    vLocal = position.xy;
    float age = uTime - aBurst.w;
    float life = clamp(age / 0.65, 0.0, 1.0);
    vLife = life;
    vKind = aMeta.y;
    float size = aMeta.x * (0.35 + life * 1.5);
    vec4 mv = modelViewMatrix * vec4(aBurst.xyz, 1.0);
    mv.xy += position.xy * size;
    gl_Position = projectionMatrix * mv;
  }
`;

const EXPLOSION_FRAGMENT = /* glsl */ `
  precision mediump float;
  varying float vLife;
  varying float vKind;
  varying vec2 vLocal;
  void main() {
    if (vLife >= 1.0) discard;
    // Bola radial con núcleo caliente; sin esta máscara se ven cuadrados enormes.
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    float core = smoothstep(1.0, 0.0, d);
    float alpha = (1.0 - vLife) * 0.85 * core * core;
    vec3 hot = mix(vec3(1.0, 0.95, 0.7), vec3(1.0, 0.35, 0.05), vLife);
    vec3 color = vKind > 1.5 ? hot : mix(vec3(1.0, 0.8, 0.4), vec3(0.6, 0.15, 0.05), vLife);
    color = mix(color * 0.55, color, core);
    gl_FragColor = vec4(color, alpha);
  }
`;

export interface EffectsOptions {
  particleLimit: number;
  corpseLimit: number;
  gore: boolean;
  teamColors: Record<'red' | 'blue', THREE.Color>;
}

export class Effects {
  readonly root = new THREE.Group();
  private time = 0;
  private rng: Rng = createRng(99);

  private blood: { geometry: THREE.InstancedBufferGeometry; origin: RingAttribute; vel: RingAttribute; meta: RingAttribute; material: THREE.ShaderMaterial };
  private corpses: Array<{ geometry: THREE.InstancedBufferGeometry; pose: RingAttribute; meta: RingAttribute; material: THREE.ShaderMaterial }> = [];
  private projectiles: { geometry: THREE.InstancedBufferGeometry; data: THREE.InstancedBufferAttribute; material: THREE.ShaderMaterial };
  private meteors: { geometry: THREE.InstancedBufferGeometry; target: RingAttribute; meta: RingAttribute; material: THREE.ShaderMaterial };
  private explosions: { geometry: THREE.InstancedBufferGeometry; burst: RingAttribute; meta: RingAttribute; material: THREE.ShaderMaterial };
  private materials: THREE.ShaderMaterial[] = [];

  constructor(private options: EffectsOptions) {
    const base = quadGeometry();

    // --- Sangre ---
    {
      const capacity = Math.max(256, options.particleLimit);
      const geometry = instanced(base);
      const origin = new RingAttribute(capacity, 3);
      const vel = new RingAttribute(capacity, 3);
      const meta = new RingAttribute(capacity, 2);
      geometry.setAttribute('aOrigin', origin.attribute);
      geometry.setAttribute('aVel', vel.attribute);
      geometry.setAttribute('aMeta', meta.attribute);
      geometry.instanceCount = capacity;
      const material = new THREE.ShaderMaterial({
        vertexShader: BLOOD_VERTEX,
        fragmentShader: BLOOD_FRAGMENT,
        uniforms: { uTime: { value: 0 } },
        transparent: true,
        depthWrite: false,
      });
      this.materials.push(material);
      const mesh = new THREE.Mesh(geometry, material);
      mesh.frustumCulled = false;
      mesh.renderOrder = 5;
      this.root.add(mesh);
      this.blood = { geometry, origin, vel, meta, material };
    }

    // --- Cadáveres (uno por equipo, para conservar su color) ---
    for (const team of ['red', 'blue'] as const) {
      const capacity = Math.max(128, Math.floor(options.corpseLimit / 2));
      const geometry = instanced(base);
      const pose = new RingAttribute(capacity, 4);
      const meta = new RingAttribute(capacity, 2);
      geometry.setAttribute('aPose', pose.attribute);
      geometry.setAttribute('aMeta', meta.attribute);
      geometry.instanceCount = capacity;
      const material = new THREE.ShaderMaterial({
        vertexShader: CORPSE_VERTEX,
        fragmentShader: CORPSE_FRAGMENT,
        uniforms: {
          uTime: { value: 0 },
          uColor: { value: options.teamColors[team].clone().multiplyScalar(0.35) },
        },
        transparent: true,
        depthWrite: false,
        polygonOffset: true,
        polygonOffsetFactor: -1,
      });
      this.materials.push(material);
      const mesh = new THREE.Mesh(geometry, material);
      mesh.frustumCulled = false;
      mesh.renderOrder = 3;
      this.root.add(mesh);
      this.corpses.push({ geometry, pose, meta, material });
    }

    // --- Proyectiles (se reescriben enteros cada snapshot) ---
    {
      const capacity = 3000;
      const geometry = instanced(base);
      const data = new THREE.InstancedBufferAttribute(new Float32Array(capacity * 4), 4);
      data.setUsage(THREE.DynamicDrawUsage);
      geometry.setAttribute('aData', data);
      geometry.instanceCount = 0;
      const material = new THREE.ShaderMaterial({
        vertexShader: PROJECTILE_VERTEX,
        fragmentShader: PROJECTILE_FRAGMENT,
        transparent: true,
        depthWrite: false,
      });
      this.materials.push(material);
      const mesh = new THREE.Mesh(geometry, material);
      mesh.frustumCulled = false;
      mesh.renderOrder = 6;
      this.root.add(mesh);
      this.projectiles = { geometry, data, material };
    }

    // --- Meteoros ---
    {
      const capacity = 256;
      const geometry = instanced(base);
      const target = new RingAttribute(capacity, 4);
      const meta = new RingAttribute(capacity, 2);
      geometry.setAttribute('aTarget', target.attribute);
      geometry.setAttribute('aMeta', meta.attribute);
      geometry.instanceCount = capacity;
      const material = new THREE.ShaderMaterial({
        vertexShader: METEOR_VERTEX,
        fragmentShader: METEOR_FRAGMENT,
        uniforms: { uTime: { value: 0 } },
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
      });
      this.materials.push(material);
      const mesh = new THREE.Mesh(geometry, material);
      mesh.frustumCulled = false;
      mesh.renderOrder = 7;
      this.root.add(mesh);
      this.meteors = { geometry, target, meta, material };
    }

    // --- Explosiones ---
    {
      const capacity = 512;
      const geometry = instanced(base);
      const burst = new RingAttribute(capacity, 4);
      const meta = new RingAttribute(capacity, 2);
      geometry.setAttribute('aBurst', burst.attribute);
      geometry.setAttribute('aMeta', meta.attribute);
      geometry.instanceCount = capacity;
      const material = new THREE.ShaderMaterial({
        vertexShader: EXPLOSION_VERTEX,
        fragmentShader: EXPLOSION_FRAGMENT,
        uniforms: { uTime: { value: 0 } },
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
      });
      this.materials.push(material);
      const mesh = new THREE.Mesh(geometry, material);
      mesh.frustumCulled = false;
      mesh.renderOrder = 8;
      this.root.add(mesh);
      this.explosions = { geometry, burst, meta, material };
    }

    base.dispose();
  }

  spawnBlood(x: number, y: number, z: number, soldiers: number): void {
    if (!this.options.gore) return;
    const count = Math.min(10, 2 + Math.floor(Math.log10(1 + soldiers) * 3));
    const size = 0.25 + Math.min(1.6, Math.log10(1 + soldiers) * 0.5);
    for (let i = 0; i < count; i++) {
      const slot = this.blood.origin.slot();
      this.blood.vel.slot();
      this.blood.meta.slot();
      this.blood.origin.write(slot, [x, y + 0.9, z]);
      this.blood.vel.write(slot, [this.rng.range(-2.4, 2.4), this.rng.range(1.6, 4.6), this.rng.range(-2.4, 2.4)]);
      this.blood.meta.write(slot, [this.time, size * this.rng.range(0.7, 1.35)]);
    }
  }

  spawnCorpse(x: number, y: number, z: number, rot: number, team: 0 | 1, scale: number): void {
    if (!this.options.gore) return;
    const target = this.corpses[team];
    const slot = target.pose.slot();
    target.meta.slot();
    target.pose.write(slot, [x, y, z, rot]);
    target.meta.write(slot, [this.time, scale]);
  }

  spawnExplosion(x: number, y: number, z: number, radius: number, kind: number): void {
    const slot = this.explosions.burst.slot();
    this.explosions.meta.slot();
    this.explosions.burst.write(slot, [x, y + 1, z, this.time]);
    this.explosions.meta.write(slot, [Math.max(1.2, radius), kind]);
  }

  spawnMeteor(x: number, y: number, z: number, delay: number, radius: number): void {
    const slot = this.meteors.target.slot();
    this.meteors.meta.slot();
    this.meteors.target.write(slot, [x, y, z, this.time]);
    this.meteors.meta.write(slot, [Math.max(0.1, delay), Math.max(2, radius)]);
  }

  /** Sube las posiciones de proyectiles del snapshot actual. */
  updateProjectiles(source: Float32Array, count: number, stride: number): void {
    const array = this.projectiles.data.array as Float32Array;
    const capacity = array.length / 4;
    const n = Math.min(count, capacity);
    for (let i = 0; i < n; i++) {
      const o = i * stride;
      array[i * 4] = source[o];
      array[i * 4 + 1] = source[o + 1];
      array[i * 4 + 2] = source[o + 2];
      array[i * 4 + 3] = source[o + 3];
    }
    this.projectiles.data.needsUpdate = true;
    this.projectiles.geometry.instanceCount = n;
  }

  update(dt: number): void {
    this.time += dt;
    for (const material of this.materials) {
      if (material.uniforms.uTime) material.uniforms.uTime.value = this.time;
    }
  }

  /** Limpia todo lo visible: se llama al empezar una ronda nueva. */
  reset(): void {
    this.blood.origin.clear();
    this.blood.vel.clear();
    this.blood.meta.clear();
    for (const corpse of this.corpses) {
      corpse.pose.clear();
      corpse.meta.clear();
    }
    this.meteors.target.clear();
    this.meteors.meta.clear();
    this.explosions.burst.clear();
    this.explosions.meta.clear();
    this.projectiles.geometry.instanceCount = 0;
  }

  setTeamColor(team: 0 | 1, color: THREE.Color): void {
    this.corpses[team].material.uniforms.uColor.value.copy(color).multiplyScalar(0.35);
  }

  setGore(enabled: boolean): void {
    this.options.gore = enabled;
  }

  dispose(): void {
    for (const material of this.materials) material.dispose();
    this.blood.geometry.dispose();
    for (const corpse of this.corpses) corpse.geometry.dispose();
    this.projectiles.geometry.dispose();
    this.meteors.geometry.dispose();
    this.explosions.geometry.dispose();
  }
}
