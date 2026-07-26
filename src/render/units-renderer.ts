/**
 * Renderizado de las unidades.
 *
 * Un `InstancedMesh` por combinación (malla × equipo) — seis en total — y un
 * único buffer intercalado por grupo que se sube tal cual llega del worker.
 * La animación (caminar, golpear, caer) ocurre entera en el vertex shader, así
 * que dibujar 9.000 guerreros cuesta 6 draw calls y cero trabajo de CPU.
 */

import * as THREE from 'three';
import type { GroupKey } from '../sim/protocol';
import { GROUP_KEYS, UNIT_STRIDE } from '../sim/protocol';
import { SRGB_ENCODE } from './shader-chunks';
import { createBlobShadowGeometry, createUnitGeometries } from './soldier-geometry';

const UNIT_VERTEX_SHADER = /* glsl */ `
  attribute float aLimb;
  attribute vec3 aPivot;
  attribute float aShade;

  attribute vec3 aOffset;
  attribute float aRot;
  attribute float aScale;
  attribute float aPhase;
  attribute float aState;
  attribute float aHealth;

  varying vec3 vNormal;
  varying float vShade;
  varying float vHealth;
  varying float vDeath;
  varying float vFogDepth;

  vec3 rotX(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c); }
  vec3 rotY(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }
  vec3 rotZ(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z); }

  void main() {
    // aState codifica el estado: 0..1 marcha (valor = ritmo), 1..2 ataque, 2..3 caída.
    float mode = floor(aState);
    float sub = fract(aState);

    float walk = mode < 0.5 ? sub : 0.22;
    float swing = sin(aPhase * 3.0) * (0.18 + walk * 0.95);
    float strike = (mode > 0.5 && mode < 1.5) ? -1.9 * sub : 0.0;
    float death = mode > 1.5 ? sub : 0.0;

    vec3 local = position;
    vec3 nrm = normal;
    vec3 rel = local - aPivot;
    vec3 relN = nrm;

    if (aLimb == 6.0) {
      // Alas y cola: batido rápido sobre el eje Z.
      float flap = sin(aPhase * 9.0) * 0.55 * sign(local.x + 0.0001);
      rel = rotZ(rel, flap);
      relN = rotZ(relN, flap);
    } else if (aLimb > 0.5 && aLimb < 5.5) {
      float angle = 0.0;
      if (aLimb == 1.0) angle = swing;
      else if (aLimb == 2.0) angle = -swing;
      else if (aLimb == 3.0) angle = -swing * 0.55 + strike;
      else if (aLimb == 4.0) angle = swing * 0.55 + strike;
      else angle = sin(aPhase * 3.0) * 0.05;
      rel = rotX(rel, angle);
      relN = rotX(relN, angle);
    }
    local = aPivot + rel;
    nrm = relN;

    // Rebote del cuerpo al caminar.
    if (aLimb < 0.5 || aLimb == 5.0) local.y += abs(sin(aPhase * 3.0)) * 0.05 * walk;

    // Caída: el cuerpo rota hacia adelante desde los pies y se hunde un poco.
    if (death > 0.0) {
      float fall = death * death;
      local = rotX(local, fall * 1.5);
      nrm = rotX(nrm, fall * 1.5);
      local.y -= fall * 0.22;
    }

    local *= aScale;
    local = rotY(local, aRot);
    nrm = rotY(nrm, aRot);

    vec3 world = local + aOffset;
    vec4 mv = modelViewMatrix * vec4(world, 1.0);

    vNormal = nrm;
    vShade = aShade;
    vHealth = aHealth;
    vDeath = death;
    vFogDepth = -mv.z;
    gl_Position = projectionMatrix * mv;
  }
`;

const UNIT_FRAGMENT_SHADER = /* glsl */ `
  precision highp float;
${SRGB_ENCODE}

  uniform vec3 uTeamColor;
  uniform vec3 uTeamColorDark;
  uniform vec3 uSkinColor;
  uniform vec3 uMetalColor;
  uniform vec3 uLightDir;
  uniform vec3 uSunColor;
  uniform vec3 uSkyColor;
  uniform vec3 uGroundColor;
  uniform vec3 uFogColor;
  uniform float uFogDensity;

  varying vec3 vNormal;
  varying float vShade;
  varying float vHealth;
  varying float vDeath;
  varying float vFogDepth;

  void main() {
    vec3 n = normalize(vNormal);

    // Color base según la parte del cuerpo.
    vec3 base = uTeamColor;
    if (vShade < 0.5) base = uSkinColor;
    else if (vShade > 1.5) base = uMetalColor;

    // Heridas: la unidad se oscurece y enrojece conforme pierde vida.
    base = mix(mix(uTeamColorDark, vec3(0.32, 0.05, 0.05), 0.45), base, clamp(vHealth, 0.0, 1.0) * 0.75 + 0.25);
    // Los caídos se apagan.
    base *= 1.0 - vDeath * 0.55;

    // Iluminación "half-Lambert": las caras que no miran al sol se atenúan, pero
    // nunca llegan a negro. Con Lambert puro, media unidad quedaba en sombra dura
    // y el equipo rojo se leía como marrón oscuro en pantalla.
    float ndl = dot(n, normalize(uLightDir));
    float diffuse = pow(ndl * 0.5 + 0.5, 1.6);
    float hemi = n.y * 0.5 + 0.5;
    vec3 ambient = mix(uGroundColor, uSkyColor, hemi);
    // El ambiente del cielo es muy azul y apagaba al equipo rojo hasta dejarlo
    // marrón. Se desatura hacia el gris antes de multiplicar.
    float lum = dot(ambient, vec3(0.299, 0.587, 0.114));
    ambient = mix(ambient, vec3(lum), 0.7);
    vec3 lit = base * (ambient * 1.1 + uSunColor * diffuse * 0.95);
    // Suelo de color propio: a 60 metros de cámara, lo único que importa es
    // distinguir de un vistazo quién es rojo y quién es azul.
    vec3 color = mix(lit, base * 0.85, 0.3);

    // Niebla exponencial al cuadrado: iguala la del terreno y el cielo.
    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);
    color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(linearToSRGB(color), 1.0);
  }
`;

const SHADOW_VERTEX_SHADER = /* glsl */ `
  attribute vec3 aOffset;
  attribute float aScale;
  attribute float aState;
  varying float vAlpha;

  void main() {
    float mode = floor(aState);
    float death = mode > 1.5 ? fract(aState) : 0.0;
    vAlpha = 1.0 - death * 0.8;
    vec3 world = position * aScale * 1.05 + vec3(aOffset.x, aOffset.y + 0.05, aOffset.z);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(world, 1.0);
  }
`;

const SHADOW_FRAGMENT_SHADER = /* glsl */ `
  precision mediump float;
  uniform float uOpacity;
  varying float vAlpha;
  void main() {
    gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * vAlpha);
  }
`;

export interface UnitsRendererOptions {
  capacityPerGroup: number;
  detail: 'full' | 'simple';
  teamColors: Record<'red' | 'blue', { color: THREE.Color; dark: THREE.Color }>;
  shadows: boolean;
  fogColor: THREE.Color;
  fogDensity: number;
}

interface GroupRuntime {
  mesh: THREE.Mesh;
  shadow: THREE.Mesh | null;
  buffer: THREE.InstancedInterleavedBuffer;
  array: Float32Array;
  geometry: THREE.InstancedBufferGeometry;
}

export class UnitsRenderer {
  readonly root = new THREE.Group();
  private groups = new Map<GroupKey, GroupRuntime>();
  private materials: THREE.ShaderMaterial[] = [];
  private shadowMaterial: THREE.ShaderMaterial;
  private baseGeometries: ReturnType<typeof createUnitGeometries>;
  private shadowGeometry = createBlobShadowGeometry();

  constructor(private options: UnitsRendererOptions) {
    this.baseGeometries = createUnitGeometries(options.detail);
    this.shadowMaterial = new THREE.ShaderMaterial({
      vertexShader: SHADOW_VERTEX_SHADER,
      fragmentShader: SHADOW_FRAGMENT_SHADER,
      uniforms: { uOpacity: { value: options.shadows ? 0.32 : 0.0 } },
      transparent: true,
      depthWrite: false,
      polygonOffset: true,
      polygonOffsetFactor: -2,
    });

    for (const key of GROUP_KEYS) {
      this.createGroup(key);
    }
    this.root.frustumCulled = false;
  }

  private createGroup(key: GroupKey): void {
    const [meshKind, teamName] = key.split('_') as ['humanoid' | 'beast' | 'dragon', 'red' | 'blue'];
    const base = this.baseGeometries[meshKind];
    const capacity = this.options.capacityPerGroup;

    const array = new Float32Array(capacity * UNIT_STRIDE);
    const buffer = new THREE.InstancedInterleavedBuffer(array, UNIT_STRIDE);
    buffer.setUsage(THREE.DynamicDrawUsage);

    const geometry = new THREE.InstancedBufferGeometry();
    geometry.index = base.index;
    geometry.setAttribute('position', base.getAttribute('position'));
    geometry.setAttribute('normal', base.getAttribute('normal'));
    geometry.setAttribute('aLimb', base.getAttribute('aLimb'));
    geometry.setAttribute('aPivot', base.getAttribute('aPivot'));
    geometry.setAttribute('aShade', base.getAttribute('aShade'));
    this.attachInstanceAttributes(geometry, buffer);
    geometry.instanceCount = 0;
    geometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);

    const teamColor = this.options.teamColors[teamName];
    const material = new THREE.ShaderMaterial({
      vertexShader: UNIT_VERTEX_SHADER,
      fragmentShader: UNIT_FRAGMENT_SHADER,
      uniforms: {
        uTeamColor: { value: teamColor.color.clone() },
        uTeamColorDark: { value: teamColor.dark.clone() },
        uSkinColor: { value: new THREE.Color('#c58f6a') },
        uMetalColor: { value: new THREE.Color('#9aa2ad') },
        uLightDir: { value: new THREE.Vector3(0.45, 0.82, 0.35).normalize() },
        uSunColor: { value: new THREE.Color('#fff2d8') },
        uSkyColor: { value: new THREE.Color('#8fb6e8') },
        uGroundColor: { value: new THREE.Color('#4a4030') },
        uFogColor: { value: this.options.fogColor.clone() },
        uFogDensity: { value: this.options.fogDensity },
      },
    });
    this.materials.push(material);

    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;
    mesh.renderOrder = 2;
    this.root.add(mesh);

    // Los dragones vuelan: una sombra pegada a su altura se vería flotando, así
    // que solo las unidades terrestres llevan sombra de contacto.
    let shadow: THREE.Mesh | null = null;
    if (meshKind !== 'dragon') {
      const shadowGeometry = new THREE.InstancedBufferGeometry();
      shadowGeometry.index = this.shadowGeometry.index;
      shadowGeometry.setAttribute('position', this.shadowGeometry.getAttribute('position'));
      shadowGeometry.setAttribute('aOffset', new THREE.InterleavedBufferAttribute(buffer, 3, 0));
      shadowGeometry.setAttribute('aScale', new THREE.InterleavedBufferAttribute(buffer, 1, 4));
      shadowGeometry.setAttribute('aState', new THREE.InterleavedBufferAttribute(buffer, 1, 6));
      shadowGeometry.instanceCount = 0;
      shadowGeometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);
      shadow = new THREE.Mesh(shadowGeometry, this.shadowMaterial);
      shadow.frustumCulled = false;
      shadow.renderOrder = 1;
      this.root.add(shadow);
    }

    this.groups.set(key, { mesh, shadow, buffer, array, geometry });
  }

  private attachInstanceAttributes(geometry: THREE.InstancedBufferGeometry, buffer: THREE.InstancedInterleavedBuffer): void {
    geometry.setAttribute('aOffset', new THREE.InterleavedBufferAttribute(buffer, 3, 0));
    geometry.setAttribute('aRot', new THREE.InterleavedBufferAttribute(buffer, 1, 3));
    geometry.setAttribute('aScale', new THREE.InterleavedBufferAttribute(buffer, 1, 4));
    geometry.setAttribute('aPhase', new THREE.InterleavedBufferAttribute(buffer, 1, 5));
    geometry.setAttribute('aState', new THREE.InterleavedBufferAttribute(buffer, 1, 6));
    geometry.setAttribute('aHealth', new THREE.InterleavedBufferAttribute(buffer, 1, 7));
  }

  /** Sube el snapshot del worker a la GPU. */
  update(source: Float32Array, groups: Array<{ key: GroupKey; start: number; count: number }>): void {
    for (const { key, start, count } of groups) {
      const runtime = this.groups.get(key);
      if (!runtime) continue;
      const capacity = this.options.capacityPerGroup;
      const clamped = Math.min(count, capacity);
      if (clamped > 0) {
        runtime.array.set(source.subarray(start * UNIT_STRIDE, (start + clamped) * UNIT_STRIDE));
        runtime.buffer.needsUpdate = true;
      }
      runtime.geometry.instanceCount = clamped;
      if (runtime.shadow) (runtime.shadow.geometry as THREE.InstancedBufferGeometry).instanceCount = clamped;
    }
  }

  setTeamColors(team: 'red' | 'blue', color: THREE.Color, dark: THREE.Color): void {
    for (const key of GROUP_KEYS) {
      if (!key.endsWith(team)) continue;
      const runtime = this.groups.get(key);
      if (!runtime) continue;
      const material = runtime.mesh.material as THREE.ShaderMaterial;
      material.uniforms.uTeamColor.value.copy(color);
      material.uniforms.uTeamColorDark.value.copy(dark);
    }
  }

  setShadowsEnabled(enabled: boolean): void {
    this.shadowMaterial.uniforms.uOpacity.value = enabled ? 0.32 : 0;
  }

  setFog(color: THREE.Color, density: number): void {
    for (const material of this.materials) {
      material.uniforms.uFogColor.value.copy(color);
      material.uniforms.uFogDensity.value = density;
    }
  }

  setLighting(sky: THREE.Color, ground: THREE.Color, sun: THREE.Color, dir: THREE.Vector3): void {
    for (const material of this.materials) {
      material.uniforms.uSkyColor.value.copy(sky);
      material.uniforms.uGroundColor.value.copy(ground);
      material.uniforms.uSunColor.value.copy(sun);
      material.uniforms.uLightDir.value.copy(dir).normalize();
    }
  }

  dispose(): void {
    for (const runtime of this.groups.values()) {
      runtime.geometry.dispose();
      runtime.shadow?.geometry.dispose();
    }
    for (const material of this.materials) material.dispose();
    this.shadowMaterial.dispose();
    for (const geometry of Object.values(this.baseGeometries)) geometry.dispose();
    this.shadowGeometry.dispose();
    this.groups.clear();
  }
}
