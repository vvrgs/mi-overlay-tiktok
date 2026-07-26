/**
 * Renderizado de las unidades.
 *
 * Tres ideas sostienen esto:
 *
 * 1. **Un grupo por (arquetipo × equipo)**, con un buffer intercalado que se
 *    sube tal cual llega del worker.
 * 2. **Animación por esqueleto en el vertex shader.** Cada vértice sabe su hueso
 *    y las dos articulaciones de su cadena, así que el shader puede doblar
 *    rodillas y codos —rotar primero sobre la articulación propia y después
 *    sobre la del padre— sin ningún sistema de huesos en CPU.
 * 3. **LOD por distancia.** Las instancias se reparten en cada frame entre una
 *    malla detallada (las cercanas) y una reducida (el resto). Es lo que permite
 *    subir el detalle de los modelos sin multiplicar el coste: en pantalla solo
 *    unas pocas docenas de unidades están lo bastante cerca para notarlo.
 *
 * Los colores salen en espacio lineal sin codificar: el paso final a sRGB lo
 * hace el post-procesado.
 */

import * as THREE from 'three';
import { UNIT_STRIDE, type UnitGroup } from '../sim/protocol';
import { createArchetypeGeometry, createBlobShadowGeometry, type Detail } from './unit-geometry';

const UNIT_VERTEX_SHADER = /* glsl */ `
  attribute float aLimb;
  attribute vec3 aPivot;
  attribute vec3 aPivot2;
  attribute float aShade;
  attribute float aOcc;

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
  varying float vOcc;
  varying vec3 vViewDir;

  vec3 rotX(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c); }
  vec3 rotY(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }
  vec3 rotZ(vec3 p, float a) { float s = sin(a), c = cos(a); return vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z); }

  void main() {
    // aState codifica el estado: 0..1 marcha (valor = ritmo), 1..2 ataque, 2..3 caída.
    float mode = floor(aState);
    float sub = fract(aState);
    float walk = mode < 0.5 ? sub : 0.22;
    float strike = (mode > 0.5 && mode < 1.5) ? sub : 0.0;
    float death = mode > 1.5 ? sub : 0.0;

    // Semilla estable por instancia: rompe la uniformidad del ejército.
    vRandom = fract(sin(aPhase * 12.9898 + aOffset.x * 0.137 + aOffset.z * 0.531) * 43758.5453);

    float t = aPhase * 3.0;
    // Zancada. Un ángulo positivo lleva el miembro hacia ATRÁS (el modelo mira a +Z).
    float stride = sin(t) * (0.22 + walk * 1.0);
    float armSwing = stride * 0.55;
    // El golpe lanza el brazo derecho al frente y extiende el codo.
    float punch = -2.1 * strike;

    float angle = 0.0;   // rotación sobre la articulación propia
    float parent = 0.0;  // rotación sobre la articulación del padre
    float yaw = 0.0;

    if (aLimb == 1.0) {
      angle = stride;
    } else if (aLimb == 2.0) {
      angle = -stride;
    } else if (aLimb == 8.0) {
      // Rodilla: solo dobla hacia atrás, y sobre todo al despegar el pie.
      angle = max(0.0, stride) * 1.5 + 0.06;
      parent = stride;
    } else if (aLimb == 9.0) {
      angle = max(0.0, -stride) * 1.5 + 0.06;
      parent = -stride;
    } else if (aLimb == 3.0) {
      angle = -armSwing;
    } else if (aLimb == 4.0) {
      angle = armSwing + punch;
    } else if (aLimb == 10.0) {
      angle = 0.34 + max(0.0, armSwing) * 0.6;
      parent = -armSwing;
    } else if (aLimb == 11.0) {
      // El codo derecho se extiende en el impacto y se recoge al volver.
      angle = 0.4 - 0.34 * strike + max(0.0, -armSwing) * 0.6;
      parent = armSwing + punch;
    } else if (aLimb == 5.0) {
      // La cabeza compensa el giro del torso: mira al frente aunque el cuerpo rote.
      yaw = stride * 0.14;
      angle = -walk * 0.1;
    } else if (aLimb == 0.0) {
      // Contrarrotación del torso: es lo que da naturalidad al caminar.
      yaw = -stride * 0.12;
      angle = strike * -0.16;
    } else if (aLimb == 12.0) {
      yaw = stride * 0.16;
    }

    vec3 local = position;
    vec3 nrm = normal;

    if (aLimb == 6.0) {
      // Capas, alas y colas: ondean sobre Z y se abren hacia atrás con la marcha.
      float flap = sin(t * 3.0 + vRandom * 3.0) * (0.3 + walk * 0.45) * sign(local.x + 0.0001);
      vec3 rel = local - aPivot;
      local = aPivot + rotZ(rel, flap);
      nrm = rotZ(nrm, flap);
      local.z -= walk * 0.16;
    } else if (aLimb != 7.0) {
      // Primero la articulación propia (rodilla, codo, cuello)...
      vec3 rel = local - aPivot;
      local = aPivot + rotX(rel, angle);
      nrm = rotX(nrm, angle);
      // ...y después la del padre (cadera, hombro). Ese orden es el que hace
      // que la pantorrilla siga al muslo en vez de girar por su cuenta.
      if (parent != 0.0) {
        vec3 rel2 = local - aPivot2;
        local = aPivot2 + rotX(rel2, parent);
        nrm = rotX(nrm, parent);
      }
      if (yaw != 0.0) {
        vec3 rel3 = local - aPivot;
        local = aPivot + rotY(rel3, yaw);
        nrm = rotY(nrm, yaw);
      }
    }

    // Rebote vertical del paso.
    local.y += abs(sin(t)) * 0.045 * walk;
    // Inclinación hacia adelante al correr: el cuerpo persigue su centro de masa.
    if (walk > 0.01) {
      local = rotX(local, -walk * 0.11);
      nrm = rotX(nrm, -walk * 0.11);
    }

    // Caída: el cuerpo rota hacia adelante desde los pies y se hunde un poco.
    if (death > 0.0) {
      float fall = death * death;
      local = rotX(local, fall * 1.55);
      nrm = rotX(nrm, fall * 1.55);
      local.y -= fall * 0.2;
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
    vOcc = aOcc;
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
  uniform vec3 uLeatherColor;
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
  varying float vOcc;
  varying vec3 vViewDir;

  void main() {
    vec3 n = normalize(vNormal);

    // Color y acabado según el material de la pieza.
    vec3 base = uTeamColor;
    float gloss = 0.0;
    bool emissive = false;
    if (vShade < 0.5) { base = uSkinColor; gloss = 0.12; }
    else if (vShade > 2.5 && vShade < 3.5) { base = uGlowColor; emissive = true; }
    else if (vShade > 3.5) { base = uLeatherColor; gloss = 0.08; }
    else if (vShade > 1.5) { base = uMetalColor; gloss = 1.0; }

    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);

    if (emissive) {
      vec3 glow = base * uGlowStrength * (1.0 - vDeath * 0.9);
      gl_FragColor = vec4(mix(glow, uFogColor, clamp(fogFactor, 0.0, 1.0)), 1.0);
      return;
    }

    // Variación por instancia: sin ella el ejército parece una figura clonada.
    base *= 0.88 + vRandom * 0.24;
    // Oclusión horneada: oscurece axilas, entrepierna y bajo las hombreras. Es
    // lo que da sensación de volumen sin calcular sombras propias.
    base *= mix(0.42, 1.0, vOcc);

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

    // Reflejo especular proporcional al acabado: el metal destella al girar la
    // cámara, el cuero apenas y la tela nada.
    if (gloss > 0.0) {
      vec3 halfway = normalize(lightDir + viewDir);
      float spec = pow(max(dot(n, halfway), 0.0), mix(18.0, 58.0, gloss));
      lit += uSunColor * spec * gloss * 0.85 * (1.0 - vDeath) * vOcc;
    }

    // Borde iluminado por el cielo: despega la silueta del fondo.
    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    lit += uSkyColor * rim * 0.2 * vOcc;

    // Suelo de color propio: a 60 metros de cámara, lo único que importa es
    // distinguir de un vistazo quién es rojo y quién es azul.
    vec3 color = mix(lit, base * 0.5, 0.24);
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
    vec3 world = position * aScale * 1.25
               + vec3(aOffset.x + uLightOffset.x * aScale, aOffset.y + 0.04, aOffset.z + uLightOffset.y * aScale);
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
    float d = length(vLocal) / 0.42;
    gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * vAlpha * smoothstep(1.0, 0.2, d));
  }
`;

export interface ArchetypeVisual {
  key: string;
  mesh: string;
  /** Las unidades de masa necesitan buffers grandes; las élite, no. */
  stackable: boolean;
}

export interface UnitsRendererOptions {
  capacityPerGroup: number;
  detail: Detail;
  archetypes: ArchetypeVisual[];
  teamColors: Record<'red' | 'blue', { color: THREE.Color; dark: THREE.Color }>;
  shadows: boolean;
  fogColor: THREE.Color;
  fogDensity: number;
  /** Distancia a partir de la cual una unidad usa la malla reducida. */
  lodDistance: number;
}

/** Capacidad de los grupos de unidades élite (gigantes, dragones, campeones). */
const ELITE_CAPACITY = 768;

interface Layer {
  geometry: THREE.InstancedBufferGeometry;
  buffer: THREE.InstancedInterleavedBuffer;
  array: Float32Array;
  mesh: THREE.Mesh;
}

interface GroupRuntime {
  capacity: number;
  near: Layer;
  far: Layer;
  /** Buffer con TODAS las instancias; solo lo usa la sombra. */
  all: Layer | null;
}

export class UnitsRenderer {
  readonly root = new THREE.Group();
  private groups: Array<GroupRuntime | null> = [];
  private materials: THREE.ShaderMaterial[] = [];
  private materialsByTeam: Record<'red' | 'blue', THREE.ShaderMaterial[]> = { red: [], blue: [] };
  private shadowMaterial: THREE.ShaderMaterial;
  private baseGeometries: THREE.BufferGeometry[] = [];
  private shadowGeometry = createBlobShadowGeometry();
  private lodDistanceSq: number;

  constructor(private options: UnitsRendererOptions) {
    this.lodDistanceSq = options.lodDistance * options.lodDistance;
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
      // En calidad baja no hay LOD: una sola silueta simplificada para todo.
      const near = createArchetypeGeometry(archetype.key, archetype.mesh, options.detail);
      const far = options.detail === 'high' ? createArchetypeGeometry(archetype.key, archetype.mesh, 'low') : near;
      this.baseGeometries.push(near);
      if (far !== near) this.baseGeometries.push(far);
      this.createGroup(index, 'red', near, far);
      this.createGroup(index, 'blue', near, far);
    });
    this.root.frustumCulled = false;
  }

  /** Índice plano de un grupo dentro del array: arquetipo × 2 + equipo. */
  private slot(archetype: number, team: 0 | 1 | 'red' | 'blue'): number {
    const teamIndex = team === 'red' ? 0 : team === 'blue' ? 1 : team;
    return archetype * 2 + teamIndex;
  }

  private makeLayer(
    base: THREE.BufferGeometry,
    capacity: number,
    material: THREE.Material,
    renderOrder: number,
    animated: boolean,
  ): Layer {
    const array = new Float32Array(capacity * UNIT_STRIDE);
    const buffer = new THREE.InstancedInterleavedBuffer(array, UNIT_STRIDE);
    buffer.setUsage(THREE.DynamicDrawUsage);

    const geometry = new THREE.InstancedBufferGeometry();
    geometry.setIndex(base.index);
    geometry.setAttribute('position', base.getAttribute('position'));
    geometry.setAttribute('aOffset', new THREE.InterleavedBufferAttribute(buffer, 3, 0));
    geometry.setAttribute('aScale', new THREE.InterleavedBufferAttribute(buffer, 1, 4));
    geometry.setAttribute('aState', new THREE.InterleavedBufferAttribute(buffer, 1, 6));
    // La sombra es un disco plano: no necesita normales, huesos ni vida.
    if (animated) {
      geometry.setAttribute('normal', base.getAttribute('normal'));
      geometry.setAttribute('aLimb', base.getAttribute('aLimb'));
      geometry.setAttribute('aPivot', base.getAttribute('aPivot'));
      geometry.setAttribute('aPivot2', base.getAttribute('aPivot2'));
      geometry.setAttribute('aShade', base.getAttribute('aShade'));
      geometry.setAttribute('aOcc', base.getAttribute('aOcc'));
      geometry.setAttribute('aRot', new THREE.InterleavedBufferAttribute(buffer, 1, 3));
      geometry.setAttribute('aPhase', new THREE.InterleavedBufferAttribute(buffer, 1, 5));
      geometry.setAttribute('aHealth', new THREE.InterleavedBufferAttribute(buffer, 1, 7));
    }
    geometry.instanceCount = 0;
    geometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);

    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;
    mesh.renderOrder = renderOrder;
    this.root.add(mesh);
    return { geometry, buffer, array, mesh };
  }

  private createGroup(
    archetype: number,
    teamName: 'red' | 'blue',
    nearBase: THREE.BufferGeometry,
    farBase: THREE.BufferGeometry,
  ): void {
    const visual = this.options.archetypes[archetype];
    const capacity = visual.stackable ? this.options.capacityPerGroup : ELITE_CAPACITY;
    const teamColor = this.options.teamColors[teamName];

    const material = new THREE.ShaderMaterial({
      vertexShader: UNIT_VERTEX_SHADER,
      fragmentShader: UNIT_FRAGMENT_SHADER,
      uniforms: {
        uTeamColor: { value: teamColor.color.clone() },
        uTeamColorDark: { value: teamColor.dark.clone() },
        uSkinColor: { value: new THREE.Color('#c58f6a') },
        uMetalColor: { value: new THREE.Color('#aeb7c4') },
        uLeatherColor: { value: new THREE.Color('#6b4a2c') },
        uGlowColor: { value: teamColor.color.clone().lerp(new THREE.Color('#ffffff'), 0.45) },
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

    const near = this.makeLayer(nearBase, capacity, material, 2, true);
    const far = this.makeLayer(farBase, capacity, material, 2, true);

    // Los que vuelan no llevan sombra de contacto: se vería flotando con ellos.
    let all: Layer | null = null;
    if (visual.mesh !== 'dragon') {
      all = this.makeLayer(this.shadowGeometry, capacity, this.shadowMaterial, 1, false);
    }

    this.groups[this.slot(archetype, teamName)] = { capacity, near, far, all };
  }

  /**
   * Sube el snapshot del worker a la GPU, repartiendo cada instancia entre la
   * malla detallada y la reducida según su distancia a la cámara.
   */
  update(source: Float32Array, groups: UnitGroup[], cameraPosition: THREE.Vector3): void {
    for (const runtime of this.groups) {
      if (!runtime) continue;
      runtime.near.geometry.instanceCount = 0;
      runtime.far.geometry.instanceCount = 0;
      if (runtime.all) runtime.all.geometry.instanceCount = 0;
    }

    const cx = cameraPosition.x;
    const cy = cameraPosition.y;
    const cz = cameraPosition.z;

    for (const { archetype, team, start, count } of groups) {
      const runtime = this.groups[this.slot(archetype, team)];
      if (!runtime) continue;
      const total = Math.min(count, runtime.capacity);
      if (total <= 0) continue;

      const nearArray = runtime.near.array;
      const farArray = runtime.far.array;
      let nearCount = 0;
      let farCount = 0;

      for (let i = 0; i < total; i++) {
        const src = (start + i) * UNIT_STRIDE;
        const dx = source[src] - cx;
        const dy = source[src + 1] - cy;
        const dz = source[src + 2] - cz;
        const near = dx * dx + dy * dy + dz * dz <= this.lodDistanceSq;
        const target = near ? nearArray : farArray;
        const dst = (near ? nearCount++ : farCount++) * UNIT_STRIDE;
        for (let k = 0; k < UNIT_STRIDE; k++) target[dst + k] = source[src + k];
      }

      runtime.near.geometry.instanceCount = nearCount;
      runtime.far.geometry.instanceCount = farCount;
      if (nearCount > 0) runtime.near.buffer.needsUpdate = true;
      if (farCount > 0) runtime.far.buffer.needsUpdate = true;

      // La sombra usa el rango entero sin partir: es un disco de doce vértices,
      // no merece la pena separarlo por distancia.
      if (runtime.all) {
        runtime.all.array.set(source.subarray(start * UNIT_STRIDE, (start + total) * UNIT_STRIDE));
        runtime.all.buffer.needsUpdate = true;
        runtime.all.geometry.instanceCount = total;
      }
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

  setLodDistance(distance: number): void {
    this.lodDistanceSq = distance * distance;
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
      runtime.near.geometry.dispose();
      runtime.far.geometry.dispose();
      runtime.all?.geometry.dispose();
    }
    for (const material of this.materials) material.dispose();
    this.shadowMaterial.dispose();
    for (const geometry of this.baseGeometries) geometry.dispose();
    this.shadowGeometry.dispose();
    this.groups.length = 0;
  }
}
