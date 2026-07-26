/**
 * Efectos: sangre, cadáveres, proyectiles, meteoros, explosiones, ondas de
 * choque, humo y chispas.
 *
 * Todos son sistemas instanciados con la animación resuelta en el shader a
 * partir del tiempo de nacimiento de cada partícula. La CPU solo escribe una
 * vez, al momento de crear el efecto; después no vuelve a tocarlo. Eso permite
 * miles de partículas simultáneas sin coste por frame.
 *
 * Los colores salen en espacio LINEAL: el paso a sRGB lo hace el post-procesado.
 * Lo que debe brillar (fuego, chispas, meteoros) emite muy por encima de 1.0
 * para que el bloom lo recoja; el humo se queda por debajo para que no brille.
 */

import * as THREE from 'three';
import { createRng, type Rng } from '../shared/math';
import { SRGB_TO_LINEAR } from './shader-chunks';

/** Quad centrado, base de todos los billboards. */
function quadGeometry(): THREE.BufferGeometry {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute([-0.5, -0.5, 0, 0.5, -0.5, 0, 0.5, 0.5, 0, -0.5, 0.5, 0], 3));
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute([0, 0, 1, 0, 1, 1, 0, 1], 2));
  geometry.setIndex([0, 1, 2, 0, 2, 3]);
  return geometry;
}

/** Anillo plano tumbado en el suelo, para las ondas de choque. */
function ringGeometry(): THREE.BufferGeometry {
  // Anillo fino: uno grueso se lee como un disco y tapa el suelo.
  const geometry = new THREE.RingGeometry(0.88, 1.0, 44, 1);
  geometry.rotateX(-Math.PI / 2);
  return geometry;
}

function instanced(base: THREE.BufferGeometry): THREE.InstancedBufferGeometry {
  const geometry = new THREE.InstancedBufferGeometry();
  if (base.index) geometry.setIndex(base.index);
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
    (this.attribute.array as Float32Array).set(values, slot * this.itemSize);
    this.attribute.needsUpdate = true;
  }

  clear(): void {
    (this.attribute.array as Float32Array).fill(0);
    this.attribute.needsUpdate = true;
    this.cursor = 0;
  }
}

/**
 * Conjunto de atributos que avanzan con un cursor común.
 *
 * El cursor compartido es lo importante: si cada atributo avanzase por su
 * cuenta, una partícula podría acabar mezclando la posición de un evento con la
 * velocidad de otro.
 */
class ParticleSystem {
  readonly geometry: THREE.InstancedBufferGeometry;
  readonly mesh: THREE.Mesh;
  private attributes: RingAttribute[] = [];
  private cursor = 0;

  constructor(
    base: THREE.BufferGeometry,
    readonly capacity: number,
    layout: Array<{ name: string; size: number }>,
    material: THREE.ShaderMaterial,
    renderOrder: number,
  ) {
    this.geometry = instanced(base);
    for (const { name, size } of layout) {
      const attribute = new RingAttribute(capacity, size);
      this.attributes.push(attribute);
      this.geometry.setAttribute(name, attribute.attribute);
    }
    this.geometry.instanceCount = capacity;
    this.mesh = new THREE.Mesh(this.geometry, material);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = renderOrder;
  }

  /** Escribe una partícula: un array de valores por atributo, en el mismo orden. */
  emit(values: number[][]): void {
    const slot = this.cursor;
    this.cursor = (this.cursor + 1) % this.capacity;
    for (let i = 0; i < this.attributes.length; i++) {
      this.attributes[i].write(slot, values[i]);
    }
  }

  clear(): void {
    for (const attribute of this.attributes) attribute.clear();
    this.cursor = 0;
  }

  dispose(): void {
    this.geometry.dispose();
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
${SRGB_TO_LINEAR}
  varying float vLife;
  varying vec2 vLocal;
  void main() {
    if (vLife >= 1.0) discard;
    // Máscara radial: sin ella, cada partícula sería un cuadrado opaco.
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    float alpha = (1.0 - vLife) * 0.9 * smoothstep(1.0, 0.25, d);
    gl_FragColor = vec4(srgbToLinear(vec3(0.52, 0.05, 0.04)), alpha);
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
    float size = aData.w > 2.5 ? 1.4 : aData.w > 1.5 ? 0.7 : 0.34;
    vec4 mv = modelViewMatrix * vec4(aData.xyz, 1.0);
    mv.xy += position.xy * size;
    gl_Position = projectionMatrix * mv;
  }
`;

const PROJECTILE_FRAGMENT = /* glsl */ `
  precision mediump float;
${SRGB_TO_LINEAR}
  varying float vKind;
  varying vec2 vLocal;
  void main() {
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    // Intensidad > 1: es lo que hace que el proyectil "queme" en el bloom.
    vec3 color = vKind > 2.5 ? srgbToLinear(vec3(1.0, 0.55, 0.18)) * 7.0
               : vKind > 1.5 ? srgbToLinear(vec3(0.62, 0.82, 1.0)) * 4.5
               : srgbToLinear(vec3(0.95, 0.9, 0.76)) * 1.4;
    gl_FragColor = vec4(color, 0.95 * smoothstep(1.0, 0.4, d));
  }
`;

// ------------------------------------------------- meteoros y explosiones

const METEOR_VERTEX = /* glsl */ `
  attribute vec4 aTarget;  // xyz = punto de impacto, w = nacimiento
  attribute vec3 aMeta;    // x = retardo, y = radio, z = retraso de la estela
  uniform float uTime;
  varying float vGlow;
  varying vec2 vLocal;
  varying float vTrail;

  void main() {
    // aMeta.z > 0 marca los fragmentos de la estela: van rezagados y más pequeños.
    float age = uTime - aTarget.w - aMeta.z;
    float t = clamp(age / max(aMeta.x, 0.05), 0.0, 1.0);
    vGlow = (t < 1.0 && age >= 0.0) ? 1.0 : 0.0;
    vTrail = aMeta.z;
    vLocal = position.xy;
    // Cae desde el cielo describiendo una diagonal.
    vec3 from = aTarget.xyz + vec3(38.0, 190.0, -26.0);
    vec3 world = mix(from, aTarget.xyz, t * t);
    vec4 mv = modelViewMatrix * vec4(world, 1.0);
    float size = aMeta.y * 0.55 * (1.0 - aMeta.z * 4.5);
    mv.xy += position.xy * max(size, 0.08);
    gl_Position = projectionMatrix * mv;
  }
`;

const METEOR_FRAGMENT = /* glsl */ `
  precision mediump float;
${SRGB_TO_LINEAR}
  varying float vGlow;
  varying vec2 vLocal;
  varying float vTrail;
  void main() {
    if (vGlow <= 0.0) discard;
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    // La cabeza va blanca incandescente SOLO en el centro: con el blanco a
    // pantalla completa del billboard, el ACES lo aplastaba a moneda blanca.
    float heat = 1.0 - clamp(vTrail * 5.0, 0.0, 0.82);
    vec3 color = mix(srgbToLinear(vec3(1.0, 0.97, 0.85)) * 9.0,
                     srgbToLinear(vec3(1.0, 0.42, 0.06)) * 3.5,
                     smoothstep(0.08, 0.5, d));
    gl_FragColor = vec4(color * heat, pow(clamp(1.0 - d, 0.0, 1.0), 2.0) * heat);
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
${SRGB_TO_LINEAR}
  varying float vLife;
  varying float vKind;
  varying vec2 vLocal;
  void main() {
    if (vLife >= 1.0) discard;
    // Bola radial con núcleo caliente; sin esta máscara se ven cuadrados enormes.
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    float falloff = clamp(1.0 - d, 0.0, 1.0);
    float core = pow(falloff, 1.8);
    float alpha = (1.0 - vLife) * 0.85 * core;
    // El emisivo lleva SU PROPIO perfil radial: solo el núcleo (d < ~0.35)
    // supera el umbral del bloom; la falda queda naranja nítida y legible en
    // vez de fundirse en un pegote blanco.
    float emissive = mix(4.5, 0.6, vLife) * (0.25 + 0.75 * pow(falloff, 3.0));
    vec3 hot = mix(srgbToLinear(vec3(1.0, 0.96, 0.78)), srgbToLinear(vec3(1.0, 0.36, 0.06)), vLife);
    vec3 warm = mix(srgbToLinear(vec3(1.0, 0.82, 0.45)), srgbToLinear(vec3(0.62, 0.16, 0.05)), vLife);
    vec3 color = (vKind > 1.5 ? hot : warm) * emissive;
    gl_FragColor = vec4(color, alpha);
  }
`;

// --------------------------------------------------------- onda de choque

const SHOCKWAVE_VERTEX = /* glsl */ `
  attribute vec4 aBurst;  // xyz = centro, w = nacimiento
  attribute vec2 aMeta;   // x = radio final, y = duración
  uniform float uTime;
  varying float vLife;

  varying float vRad;

  void main() {
    float age = uTime - aBurst.w;
    float life = clamp(age / max(aMeta.y, 0.05), 0.0, 1.0);
    vLife = life;
    // Radio ANTES de escalar: el anillo base va de 0.88 a 1.0, y el fragmento
    // lo usa para suavizar los bordes de la banda.
    vRad = length(position.xz);
    // Se expande deprisa al principio y frena: eso es lo que la lee como onda
    // y no como un círculo que crece a velocidad constante.
    float radius = aMeta.x * (0.2 + 1.5 * sqrt(life));
    vec3 world = aBurst.xyz + position * radius;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(world, 1.0);
  }
`;

const SHOCKWAVE_FRAGMENT = /* glsl */ `
  precision mediump float;
${SRGB_TO_LINEAR}
  varying float vLife;
  varying float vRad;
  void main() {
    if (vLife >= 1.0) discard;
    // Perfil suave dentro de la banda: sin él, el anillo tiene bordes duros y
    // con varios meteoros a la vez el campo quedaba empapelado de calcomanías
    // amarillas. El frente (borde exterior) es lo más brillante, como en una
    // onda expansiva real, y la cola se desvanece.
    float band = smoothstep(0.88, 0.985, vRad) * smoothstep(1.0, 0.993, vRad);
    float fade = pow(1.0 - vLife, 1.6);
    gl_FragColor = vec4(srgbToLinear(vec3(1.0, 0.88, 0.66)) * 1.3, band * fade * 0.22);
  }
`;

// --------------------------------------------------------------- humo

const SMOKE_VERTEX = /* glsl */ `
  attribute vec4 aOrigin;  // xyz = origen, w = nacimiento
  attribute vec4 aMeta;    // x = tamaño, y = duración, z = deriva X, w = deriva Z
  uniform float uTime;
  varying float vLife;
  varying vec2 vLocal;
  varying float vSeed;

  void main() {
    float age = uTime - aOrigin.w;
    float life = clamp(age / max(aMeta.y, 0.1), 0.0, 1.0);
    vLife = life;
    vLocal = position.xy;
    vSeed = fract(aOrigin.w * 7.13);

    // Asciende frenándose y se deja llevar por el viento.
    vec3 world = aOrigin.xyz + vec3(aMeta.z * age, 3.2 * age - 0.35 * age * age, aMeta.w * age);
    vec4 mv = modelViewMatrix * vec4(world, 1.0);
    // Crece al disiparse, como el humo de verdad.
    mv.xy += position.xy * aMeta.x * (0.6 + life * 1.9);
    gl_Position = projectionMatrix * mv;
  }
`;

const SMOKE_FRAGMENT = /* glsl */ `
  precision mediump float;
${SRGB_TO_LINEAR}
  varying float vLife;
  varying vec2 vLocal;
  varying float vSeed;
  void main() {
    if (vLife >= 1.0 || vLife <= 0.0) discard;
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    float alpha = smoothstep(0.0, 0.12, vLife) * (1.0 - vLife) * 0.34 * smoothstep(1.0, 0.1, d);
    // Empieza oscuro (hollín) y aclara al enfriarse. Siempre por debajo de 1.0:
    // el humo no debe entrar nunca en el bloom.
    vec3 color = mix(srgbToLinear(vec3(0.18, 0.16, 0.15)), srgbToLinear(vec3(0.55, 0.53, 0.52)), vLife * 0.8 + vSeed * 0.2);
    gl_FragColor = vec4(color, alpha);
  }
`;

// ------------------------------------------------------------- chispas

const SPARK_VERTEX = /* glsl */ `
  attribute vec4 aOrigin;  // xyz = origen, w = nacimiento
  attribute vec4 aVel;     // xyz = velocidad, w = duración
  uniform float uTime;
  varying float vLife;
  varying vec2 vLocal;

  void main() {
    float age = uTime - aOrigin.w;
    float life = clamp(age / max(aVel.w, 0.05), 0.0, 1.0);
    vLife = life;
    vLocal = position.xy;
    vec3 world = aOrigin.xyz + aVel.xyz * age + vec3(0.0, -14.0 * 0.5 * age * age, 0.0);
    vec4 mv = modelViewMatrix * vec4(world, 1.0);
    mv.xy += position.xy * 0.32 * (1.0 - life * 0.6);
    gl_Position = projectionMatrix * mv;
  }
`;

const SPARK_FRAGMENT = /* glsl */ `
  precision mediump float;
${SRGB_TO_LINEAR}
  varying float vLife;
  varying vec2 vLocal;
  void main() {
    if (vLife >= 1.0) discard;
    float d = length(vLocal) * 2.0;
    if (d > 1.0) discard;
    // Muy por encima del blanco y de vida corta: puntos de luz que saltan.
    vec3 color = mix(srgbToLinear(vec3(1.0, 0.95, 0.7)), srgbToLinear(vec3(1.0, 0.4, 0.08)), vLife) * 12.0;
    gl_FragColor = vec4(color, (1.0 - vLife) * smoothstep(1.0, 0.2, d));
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

  private blood: ParticleSystem;
  private corpses: Array<{ system: ParticleSystem; material: THREE.ShaderMaterial }> = [];
  private smoke: ParticleSystem;
  private sparks: ParticleSystem;
  private meteors: ParticleSystem;
  private explosions: ParticleSystem;
  private shockwaves: ParticleSystem;

  private projectileGeometry: THREE.InstancedBufferGeometry;
  private projectileData: THREE.InstancedBufferAttribute;

  private materials: THREE.ShaderMaterial[] = [];
  private disposables: THREE.BufferGeometry[] = [];

  constructor(private options: EffectsOptions) {
    const quad = quadGeometry();
    const ring = ringGeometry();
    this.disposables.push(quad, ring);

    const material = (
      vertexShader: string,
      fragmentShader: string,
      extra: Partial<THREE.ShaderMaterialParameters> = {},
      uniforms: Record<string, THREE.IUniform> = {},
    ) => {
      const created = new THREE.ShaderMaterial({
        vertexShader,
        fragmentShader,
        uniforms: { uTime: { value: 0 }, ...uniforms },
        transparent: true,
        depthWrite: false,
        ...extra,
      });
      this.materials.push(created);
      return created;
    };

    const particleCap = Math.max(256, options.particleLimit);

    this.blood = new ParticleSystem(
      quad,
      particleCap,
      [{ name: 'aOrigin', size: 3 }, { name: 'aVel', size: 3 }, { name: 'aMeta', size: 2 }],
      material(BLOOD_VERTEX, BLOOD_FRAGMENT),
      5,
    );
    this.root.add(this.blood.mesh);

    for (const team of ['red', 'blue'] as const) {
      const corpseMaterial = material(
        CORPSE_VERTEX,
        CORPSE_FRAGMENT,
        { polygonOffset: true, polygonOffsetFactor: -1 },
        { uColor: { value: options.teamColors[team].clone().multiplyScalar(0.35) } },
      );
      const system = new ParticleSystem(
        quad,
        Math.max(128, Math.floor(options.corpseLimit / 2)),
        [{ name: 'aPose', size: 4 }, { name: 'aMeta', size: 2 }],
        corpseMaterial,
        3,
      );
      this.root.add(system.mesh);
      this.corpses.push({ system, material: corpseMaterial });
    }

    this.smoke = new ParticleSystem(
      quad,
      Math.max(256, Math.floor(particleCap * 0.7)),
      [{ name: 'aOrigin', size: 4 }, { name: 'aMeta', size: 4 }],
      material(SMOKE_VERTEX, SMOKE_FRAGMENT),
      4,
    );
    this.root.add(this.smoke.mesh);

    this.sparks = new ParticleSystem(
      quad,
      Math.max(256, particleCap),
      [{ name: 'aOrigin', size: 4 }, { name: 'aVel', size: 4 }],
      material(SPARK_VERTEX, SPARK_FRAGMENT, { blending: THREE.AdditiveBlending }),
      7,
    );
    this.root.add(this.sparks.mesh);

    this.shockwaves = new ParticleSystem(
      ring,
      256,
      [{ name: 'aBurst', size: 4 }, { name: 'aMeta', size: 2 }],
      material(SHOCKWAVE_VERTEX, SHOCKWAVE_FRAGMENT, { blending: THREE.AdditiveBlending, side: THREE.DoubleSide }),
      6,
    );
    this.root.add(this.shockwaves.mesh);

    // Cada meteoro escribe una cabeza más varios fragmentos de estela.
    this.meteors = new ParticleSystem(
      quad,
      2048,
      [{ name: 'aTarget', size: 4 }, { name: 'aMeta', size: 3 }],
      material(METEOR_VERTEX, METEOR_FRAGMENT, { blending: THREE.AdditiveBlending }),
      8,
    );
    this.root.add(this.meteors.mesh);

    this.explosions = new ParticleSystem(
      quad,
      512,
      [{ name: 'aBurst', size: 4 }, { name: 'aMeta', size: 2 }],
      material(EXPLOSION_VERTEX, EXPLOSION_FRAGMENT, { blending: THREE.AdditiveBlending }),
      9,
    );
    this.root.add(this.explosions.mesh);

    // Los proyectiles se reescriben enteros cada snapshot, no van en anillo.
    {
      const capacity = 3000;
      this.projectileGeometry = instanced(quad);
      this.projectileData = new THREE.InstancedBufferAttribute(new Float32Array(capacity * 4), 4);
      this.projectileData.setUsage(THREE.DynamicDrawUsage);
      this.projectileGeometry.setAttribute('aData', this.projectileData);
      this.projectileGeometry.instanceCount = 0;
      const mesh = new THREE.Mesh(this.projectileGeometry, material(PROJECTILE_VERTEX, PROJECTILE_FRAGMENT));
      mesh.frustumCulled = false;
      mesh.renderOrder = 6;
      this.root.add(mesh);
    }
  }

  // ------------------------------------------------------------- emisores

  spawnBlood(x: number, y: number, z: number, soldiers: number): void {
    if (!this.options.gore) return;
    const count = Math.min(10, 2 + Math.floor(Math.log10(1 + soldiers) * 3));
    const size = 0.25 + Math.min(1.6, Math.log10(1 + soldiers) * 0.5);
    for (let i = 0; i < count; i++) {
      this.blood.emit([
        [x, y + 0.9, z],
        [this.rng.range(-2.4, 2.4), this.rng.range(1.6, 4.6), this.rng.range(-2.4, 2.4)],
        [this.time, size * this.rng.range(0.7, 1.35)],
      ]);
    }
  }

  spawnCorpse(x: number, y: number, z: number, rot: number, team: 0 | 1, scale: number): void {
    if (!this.options.gore) return;
    this.corpses[team].system.emit([[x, y, z, rot], [this.time, scale]]);
  }

  /**
   * Una explosión no es un solo destello: es bola de fuego, onda de choque en el
   * suelo, chispas que saltan y humo que sube. Cada capa entra en un momento
   * distinto, y eso es lo que la hace leerse como explosión y no como fogonazo.
   */
  spawnExplosion(x: number, y: number, z: number, radius: number, kind: number): void {
    const power = Math.max(1.2, radius);
    const big = kind > 1.5;
    this.explosions.emit([[x, y + 1, z, this.time], [power, kind]]);

    // Onda de choque y humo solo en detonaciones de verdad. Los golpes de área
    // del combate cuerpo a cuerpo son constantes: si cada uno soltara humo, el
    // campo quedaría cubierto por una neblina permanente que tapa la batalla.
    const heavy = big || power >= 6;
    if (heavy) {
      this.shockwaves.emit([[x, y + 0.25, z, this.time], [power * 1.3, big ? 0.9 : 0.6]]);
    }

    const sparkCount = Math.min(22, Math.round(power * (big ? 2.4 : 0.8)));
    for (let i = 0; i < sparkCount; i++) {
      const angle = this.rng.range(0, Math.PI * 2);
      const speed = this.rng.range(4, 12) * (big ? 1.5 : 1);
      this.sparks.emit([
        [x, y + 0.8, z, this.time],
        [Math.cos(angle) * speed, this.rng.range(4, 13), Math.sin(angle) * speed, this.rng.range(0.5, 1.1)],
      ]);
    }

    const smokeCount = heavy ? Math.min(8, Math.round(power * (big ? 0.9 : 0.4))) : 0;
    for (let i = 0; i < smokeCount; i++) {
      this.smoke.emit([
        [
          x + this.rng.range(-power * 0.4, power * 0.4),
          y + 1.2,
          z + this.rng.range(-power * 0.4, power * 0.4),
          this.time + this.rng.range(0, 0.25),
        ],
        [power * this.rng.range(0.35, 0.75), this.rng.range(1.8, 3.4), this.rng.range(-1.2, 1.2), this.rng.range(-1.2, 1.2)],
      ]);
    }
  }

  /** Meteoro con estela: una cabeza brillante y varios fragmentos rezagados. */
  spawnMeteor(x: number, y: number, z: number, delay: number, radius: number): void {
    const trail = 8;
    for (let i = 0; i < trail; i++) {
      this.meteors.emit([[x, y, z, this.time], [Math.max(0.1, delay), Math.max(2, radius), i * 0.016]]);
    }
  }

  /** Sube las posiciones de proyectiles del snapshot actual. */
  updateProjectiles(source: Float32Array, count: number, stride: number): void {
    const array = this.projectileData.array as Float32Array;
    const capacity = array.length / 4;
    const n = Math.min(count, capacity);
    for (let i = 0; i < n; i++) {
      const o = i * stride;
      array[i * 4] = source[o];
      array[i * 4 + 1] = source[o + 1];
      array[i * 4 + 2] = source[o + 2];
      array[i * 4 + 3] = source[o + 3];
    }
    this.projectileData.needsUpdate = true;
    this.projectileGeometry.instanceCount = n;
  }

  update(dt: number): void {
    this.time += dt;
    for (const material of this.materials) {
      if (material.uniforms.uTime) material.uniforms.uTime.value = this.time;
    }
  }

  /** Limpia todo lo visible: se llama al empezar una ronda nueva. */
  reset(): void {
    for (const system of [this.blood, this.smoke, this.sparks, this.shockwaves, this.meteors, this.explosions]) {
      system.clear();
    }
    for (const corpse of this.corpses) corpse.system.clear();
    this.projectileGeometry.instanceCount = 0;
  }

  setTeamColor(team: 0 | 1, color: THREE.Color): void {
    this.corpses[team].material.uniforms.uColor.value.copy(color).multiplyScalar(0.35);
  }

  setGore(enabled: boolean): void {
    this.options.gore = enabled;
  }

  dispose(): void {
    for (const material of this.materials) material.dispose();
    for (const system of [this.blood, this.smoke, this.sparks, this.shockwaves, this.meteors, this.explosions]) {
      system.dispose();
    }
    for (const corpse of this.corpses) corpse.system.dispose();
    this.projectileGeometry.dispose();
    for (const geometry of this.disposables) geometry.dispose();
  }
}
