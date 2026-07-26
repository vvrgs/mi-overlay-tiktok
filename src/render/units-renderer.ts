/**
 * Renderizado de las unidades.
 *
 * Un `InstancedMesh` por combinación (arquetipo × equipo), y un único buffer
 * intercalado por grupo que se sube tal cual llega del worker. La animación
 * —caminar, golpear, caer, ondear la capa— ocurre entera en el vertex shader,
 * así que dibujar miles de guerreros cuesta un puñado de draw calls y cero
 * trabajo de CPU.
 *
 * Los colores salen en espacio lineal sin codificar: el paso final a sRGB lo
 * hace el post-procesado. Las piezas marcadas como emisivas (orbe del mago,
 * penacho del campeón, fauces del dragón) emiten por encima de 1.0 para que el
 * bloom las recoja.
 */

import * as THREE from 'three';
import { UNIT_STRIDE, type UnitGroup } from '../sim/protocol';
import { createArchetypeGeometry, createBlobShadowGeometry, type Detail } from './soldier-geometry';

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
  varying float vRandom;
  varying vec3 vViewDir;

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

    // Semilla estable por instancia: rompe la uniformidad del ejército.
    vRandom = fract(sin(aPhase * 12.9898 + aOffset.x * 0.137 + aOffset.z * 0.531) * 43758.5453);

    vec3 local = position;
    vec3 nrm = normal;
    vec3 rel = local - aPivot;
    vec3 relN = nrm;

    if (aLimb == 6.0) {
      // Alas, cola y capa: ondeo sobre el eje Z, más marcado al correr.
      float flap = sin(aPhase * 9.0 + vRandom * 3.0) * (0.35 + walk * 0.4) * sign(local.x + 0.0001);
      rel = rotZ(rel, flap);
      relN = rotZ(relN, flap);
      // La capa además se abre hacia atrás con la velocidad.
      rel.z -= walk * 0.18;
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

    // Ligera variación de estatura: dos soldados idénticos delatan la copia.
    local *= aScale * (0.94 + vRandom * 0.12);
    local = rotY(local, aRot);
    nrm = rotY(nrm, aRot);

    vec3 world = local + aOffset;
    vec4 mv = modelViewMatrix * vec4(world, 1.0);

    vNormal = nrm;
    vShade = aShade;
    vHealth = aHealth;
    vDeath = death;
    vFogDepth = -mv.z;
    vViewDir = normalize(-mv.xyz);
    gl_Position = projectionMatrix * mv;
  }
`;

const UNIT_FRAGMENT_SHADER = /* glsl */ `
  precision highp float;

  uniform vec3 uTeamColor;
  uniform vec3 uTeamColorDark;
  uniform vec3 uSkinColor;
  uniform vec3 uMetalColor;
  uniform vec3 uWoodColor;
  uniform vec3 uGlowColor;
  uniform float uGlowStrength;
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
  varying float vRandom;
  varying vec3 vViewDir;

  void main() {
    vec3 n = normalize(vNormal);

    // Color base según el material de la pieza.
    vec3 base = uTeamColor;
    bool emissive = false;
    if (vShade < 0.5) base = uSkinColor;
    else if (vShade > 2.5 && vShade < 3.5) { base = uGlowColor; emissive = true; }
    else if (vShade > 3.5) base = uWoodColor;
    else if (vShade > 1.5) base = uMetalColor;

    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);

    if (emissive) {
      // Las piezas emisivas se saltan la iluminación y emiten por encima del
      // blanco: son el enganche del bloom.
      vec3 glow = base * uGlowStrength * (1.0 - vDeath * 0.9);
      gl_FragColor = vec4(mix(glow, uFogColor, clamp(fogFactor, 0.0, 1.0)), 1.0);
      return;
    }

    // Variación por instancia: sin ella el ejército parece una sola figura clonada.
    base *= 0.86 + vRandom * 0.28;

    // Heridas: la unidad se oscurece y enrojece conforme pierde vida.
    base = mix(mix(uTeamColorDark, vec3(0.32, 0.05, 0.05), 0.45), base, clamp(vHealth, 0.0, 1.0) * 0.75 + 0.25);
    base *= 1.0 - vDeath * 0.55;

    // Iluminación "half-Lambert": las caras que no miran al sol se atenúan, pero
    // nunca llegan a negro. Con Lambert puro, media unidad quedaba en sombra dura
    // y el equipo rojo se leía como marrón oscuro en pantalla.
    vec3 lightDir = normalize(uLightDir);
    vec3 viewDir = normalize(vViewDir);
    float ndl = dot(n, lightDir);
    float diffuse = pow(ndl * 0.5 + 0.5, 1.6);
    float hemi = n.y * 0.5 + 0.5;
    vec3 ambient = mix(uGroundColor, uSkyColor, hemi);
    // El ambiente del cielo es muy azul y apagaba al equipo rojo hasta dejarlo
    // marrón. Se desatura hacia el gris antes de multiplicar.
    float lum = dot(ambient, vec3(0.299, 0.587, 0.114));
    ambient = mix(ambient, vec3(lum), 0.7);
    vec3 lit = base * (ambient * 0.75 + uSunColor * diffuse * 0.7);

    // Reflejo especular: solo el metal lo tiene, y es lo que hace que las armas
    // destellen al girar la cámara sobre el campo.
    if (vShade > 1.5 && vShade < 2.5) {
      vec3 halfway = normalize(lightDir + viewDir);
      float spec = pow(max(dot(n, halfway), 0.0), 42.0);
      lit += uSunColor * spec * 0.9 * (1.0 - vDeath);
    }

    // Borde iluminado por el cielo: despega la silueta del fondo.
    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    lit += uSkyColor * rim * 0.22;

    // Suelo de color propio: a 60 metros de cámara, lo único que importa es
    // distinguir de un vistazo quién es rojo y quién es azul.
    vec3 color = mix(lit, base * 0.5, 0.26);
    color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(color, 1.0);
  }
`;

const SHADOW_VERTEX_SHADER = /* glsl */ `
  attribute vec3 aOffset;
  attribute float aScale;
  attribute float aState;
  uniform vec2 uLightOffset;
  varying float vAlpha;
  varying vec2 vLocal;

  void main() {
    float mode = floor(aState);
    float death = mode > 1.5 ? fract(aState) : 0.0;
    vAlpha = 1.0 - death * 0.8;
    vLocal = position.xz;
    // La sombra se desplaza en el sentido contrario al sol, no bajo los pies.
    vec3 world = position * aScale * 1.15
               + vec3(aOffset.x + uLightOffset.x * aScale, aOffset.y + 0.05, aOffset.z + uLightOffset.y * aScale);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(world, 1.0);
  }
`;

const SHADOW_FRAGMENT_SHADER = /* glsl */ `
  precision mediump float;
  uniform float uOpacity;
  varying float vAlpha;
  varying vec2 vLocal;
  void main() {
    // Borde difuminado: una elipse dura se ve como una calcomanía pegada.
    float d = length(vLocal) / 0.62;
    gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * vAlpha * smoothstep(1.0, 0.35, d));
  }
`;

export interface ArchetypeVisual {
  key: string;
  mesh: string;
}

export interface UnitsRendererOptions {
  capacityPerGroup: number;
  detail: Detail;
  archetypes: ArchetypeVisual[];
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
  private groups: Array<GroupRuntime | null> = [];
  private materials: THREE.ShaderMaterial[] = [];
  private materialsByTeam: Record<'red' | 'blue', THREE.ShaderMaterial[]> = { red: [], blue: [] };
  private shadowMaterial: THREE.ShaderMaterial;
  private baseGeometries: THREE.BufferGeometry[] = [];
  private shadowGeometry = createBlobShadowGeometry();

  constructor(private options: UnitsRendererOptions) {
    this.shadowMaterial = new THREE.ShaderMaterial({
      vertexShader: SHADOW_VERTEX_SHADER,
      fragmentShader: SHADOW_FRAGMENT_SHADER,
      uniforms: {
        uOpacity: { value: options.shadows ? 0.34 : 0.0 },
        uLightOffset: { value: new THREE.Vector2(-0.25, -0.2) },
      },
      transparent: true,
      depthWrite: false,
      polygonOffset: true,
      polygonOffsetFactor: -2,
    });

    options.archetypes.forEach((archetype, index) => {
      this.baseGeometries[index] = createArchetypeGeometry(archetype.key, archetype.mesh, options.detail);
      this.createGroup(index, 'red');
      this.createGroup(index, 'blue');
    });
    this.root.frustumCulled = false;
  }

  /** Índice plano de un grupo dentro del array: arquetipo × 2 + equipo. */
  private slot(archetype: number, team: 0 | 1 | 'red' | 'blue'): number {
    const teamIndex = team === 'red' ? 0 : team === 'blue' ? 1 : team;
    return archetype * 2 + teamIndex;
  }

  private createGroup(archetype: number, teamName: 'red' | 'blue'): void {
    const base = this.baseGeometries[archetype];
    const capacity = this.options.capacityPerGroup;

    const array = new Float32Array(capacity * UNIT_STRIDE);
    const buffer = new THREE.InstancedInterleavedBuffer(array, UNIT_STRIDE);
    buffer.setUsage(THREE.DynamicDrawUsage);

    const geometry = new THREE.InstancedBufferGeometry();
    geometry.setIndex(base.index);
    geometry.setAttribute('position', base.getAttribute('position'));
    geometry.setAttribute('normal', base.getAttribute('normal'));
    geometry.setAttribute('aLimb', base.getAttribute('aLimb'));
    geometry.setAttribute('aPivot', base.getAttribute('aPivot'));
    geometry.setAttribute('aShade', base.getAttribute('aShade'));
    geometry.setAttribute('aOffset', new THREE.InterleavedBufferAttribute(buffer, 3, 0));
    geometry.setAttribute('aRot', new THREE.InterleavedBufferAttribute(buffer, 1, 3));
    geometry.setAttribute('aScale', new THREE.InterleavedBufferAttribute(buffer, 1, 4));
    geometry.setAttribute('aPhase', new THREE.InterleavedBufferAttribute(buffer, 1, 5));
    geometry.setAttribute('aState', new THREE.InterleavedBufferAttribute(buffer, 1, 6));
    geometry.setAttribute('aHealth', new THREE.InterleavedBufferAttribute(buffer, 1, 7));
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
        uMetalColor: { value: new THREE.Color('#b7c0cc') },
        uWoodColor: { value: new THREE.Color('#6b4a2c') },
        uGlowColor: { value: teamColor.color.clone().lerp(new THREE.Color('#ffffff'), 0.45) },
        // Contenido a propósito: hay miles de unidades en pantalla y un valor
        // alto convierte cada penacho y cada orbe en una mancha blanca de bloom.
        uGlowStrength: { value: 1.9 },
        uLightDir: { value: new THREE.Vector3(0.45, 0.82, 0.35).normalize() },
        uSunColor: { value: new THREE.Color('#fff2d8') },
        uSkyColor: { value: new THREE.Color('#8fb6e8') },
        uGroundColor: { value: new THREE.Color('#4a4030') },
        uFogColor: { value: this.options.fogColor.clone() },
        uFogDensity: { value: this.options.fogDensity },
      },
    });
    this.materials.push(material);
    this.materialsByTeam[teamName].push(material);

    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;
    mesh.renderOrder = 2;
    this.root.add(mesh);

    // Los que vuelan no llevan sombra de contacto: se vería flotando con ellos.
    let shadow: THREE.Mesh | null = null;
    if (this.options.archetypes[archetype].mesh !== 'dragon') {
      const shadowGeometry = new THREE.InstancedBufferGeometry();
      shadowGeometry.setIndex(this.shadowGeometry.index);
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

    this.groups[this.slot(archetype, teamName)] = { mesh, shadow, buffer, array, geometry };
  }

  /** Sube el snapshot del worker a la GPU. */
  update(source: Float32Array, groups: UnitGroup[]): void {
    // Todo grupo que no venga en el snapshot se queda sin instancias.
    for (const runtime of this.groups) {
      if (!runtime) continue;
      runtime.geometry.instanceCount = 0;
      if (runtime.shadow) (runtime.shadow.geometry as THREE.InstancedBufferGeometry).instanceCount = 0;
    }

    for (const { archetype, team, start, count } of groups) {
      const runtime = this.groups[this.slot(archetype, team)];
      if (!runtime) continue;
      const clamped = Math.min(count, this.options.capacityPerGroup);
      if (clamped > 0) {
        runtime.array.set(source.subarray(start * UNIT_STRIDE, (start + clamped) * UNIT_STRIDE));
        runtime.buffer.needsUpdate = true;
      }
      runtime.geometry.instanceCount = clamped;
      if (runtime.shadow) (runtime.shadow.geometry as THREE.InstancedBufferGeometry).instanceCount = clamped;
    }
  }

  setTeamColors(team: 'red' | 'blue', color: THREE.Color, dark: THREE.Color): void {
    for (const material of this.materialsByTeam[team]) {
      material.uniforms.uTeamColor.value.copy(color);
      material.uniforms.uTeamColorDark.value.copy(dark);
      material.uniforms.uGlowColor.value.copy(color).lerp(new THREE.Color('#ffffff'), 0.45);
    }
  }

  setShadowsEnabled(enabled: boolean): void {
    this.shadowMaterial.uniforms.uOpacity.value = enabled ? 0.34 : 0;
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
    // La sombra se proyecta al lado opuesto de la luz, proporcional a su altura.
    const length = 0.9 / Math.max(0.25, dir.y);
    this.shadowMaterial.uniforms.uLightOffset.value.set(-dir.x * length, -dir.z * length);
  }

  dispose(): void {
    for (const runtime of this.groups) {
      if (!runtime) continue;
      runtime.geometry.dispose();
      runtime.shadow?.geometry.dispose();
    }
    for (const material of this.materials) material.dispose();
    this.shadowMaterial.dispose();
    for (const geometry of this.baseGeometries) geometry.dispose();
    this.shadowGeometry.dispose();
    this.groups.length = 0;
  }
}
