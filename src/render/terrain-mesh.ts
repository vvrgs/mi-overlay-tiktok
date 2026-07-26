/**
 * Terreno, río y mapa de sangre.
 *
 * La malla se desplaza en CPU una sola vez usando la misma función de altura que
 * el worker (`shared/terrain.ts`), así que lo que ves y lo que se simula son el
 * mismo relieve. La sangre no son decals: es una textura que se va pintando con
 * cada muerte y que el shader del suelo y el del agua leen. Por eso el campo —y
 * el río— se van tiñendo de rojo conforme avanza la batalla.
 */

import * as THREE from 'three';
import { clamp } from '../shared/math';
import type { Terrain } from '../shared/terrain';
import { NOISE_2D } from './shader-chunks';

export interface TerrainPalette {
  grass: THREE.Color;
  grassDry: THREE.Color;
  dirt: THREE.Color;
  rock: THREE.Color;
  sand: THREE.Color;
  water: THREE.Color;
  waterDeep: THREE.Color;
  fog: THREE.Color;
  sky: THREE.Color;
  horizon: THREE.Color;
  sun: THREE.Color;
  ground: THREE.Color;
  lightDir: THREE.Vector3;
}

export const PALETTES: Record<'day' | 'sunset' | 'night', TerrainPalette> = {
  day: {
    grass: new THREE.Color('#5f8a3a'),
    grassDry: new THREE.Color('#8ca14e'),
    dirt: new THREE.Color('#6b5637'),
    rock: new THREE.Color('#736f68'),
    sand: new THREE.Color('#a89466'),
    water: new THREE.Color('#2f6f9e'),
    waterDeep: new THREE.Color('#123a5c'),
    fog: new THREE.Color('#b9d3e8'),
    sky: new THREE.Color('#7fb4e8'),
    horizon: new THREE.Color('#d6e9f7'),
    sun: new THREE.Color('#fff3da'),
    ground: new THREE.Color('#4a4030'),
    lightDir: new THREE.Vector3(0.45, 0.82, 0.35),
  },
  sunset: {
    grass: new THREE.Color('#6b6a34'),
    grassDry: new THREE.Color('#9a7c40'),
    dirt: new THREE.Color('#6a4a2e'),
    rock: new THREE.Color('#6d5f56'),
    sand: new THREE.Color('#a88a5c'),
    water: new THREE.Color('#7a5a70'),
    waterDeep: new THREE.Color('#2a2340'),
    fog: new THREE.Color('#e8a878'),
    sky: new THREE.Color('#c96a4e'),
    horizon: new THREE.Color('#ffd9a0'),
    sun: new THREE.Color('#ffcb8a'),
    ground: new THREE.Color('#3a2a24'),
    lightDir: new THREE.Vector3(-0.75, 0.28, 0.2),
  },
  night: {
    grass: new THREE.Color('#2b3a2c'),
    grassDry: new THREE.Color('#39412c'),
    dirt: new THREE.Color('#33291f'),
    rock: new THREE.Color('#3b3a3d'),
    sand: new THREE.Color('#4a4232'),
    water: new THREE.Color('#1b3450'),
    waterDeep: new THREE.Color('#0a1626'),
    fog: new THREE.Color('#1a2438'),
    sky: new THREE.Color('#0d1526'),
    horizon: new THREE.Color('#2b3f5e'),
    sun: new THREE.Color('#9fb6e0'),
    ground: new THREE.Color('#12161f'),
    lightDir: new THREE.Vector3(-0.3, 0.7, -0.5),
  },
};

const TERRAIN_VERTEX_SHADER = /* glsl */ `
  varying vec3 vWorld;
  varying vec3 vNormal;
  varying float vFogDepth;

  void main() {
    vWorld = position;
    vNormal = normal;
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    vFogDepth = -mv.z;
    gl_Position = projectionMatrix * mv;
  }
`;

const TERRAIN_FRAGMENT_SHADER = /* glsl */ `
  precision highp float;
${NOISE_2D}

  uniform vec3 uGrass;
  uniform vec3 uGrassDry;
  uniform vec3 uDirt;
  uniform vec3 uRock;
  uniform vec3 uSand;
  uniform vec3 uFogColor;
  uniform vec3 uSkyColor;
  uniform vec3 uGroundColor;
  uniform vec3 uSunColor;
  uniform vec3 uLightDir;
  uniform float uFogDensity;
  uniform float uWaterLevel;
  uniform vec2 uFieldHalf;
  uniform sampler2D uBlood;

  varying vec3 vWorld;
  varying vec3 vNormal;
  varying float vFogDepth;

  void main() {
    vec3 n = normalize(vNormal);
    float slope = 1.0 - clamp(n.y, 0.0, 1.0);

    // Mezcla de suelos en tres escalas: manchas grandes de pradera, praderas
    // secas y calvas de tierra. Con una sola escala el campo se ve de plástico.
    float macro = fbm2(vWorld.xz * 0.014, 3);
    float blotch = noise2(vWorld.xz * 0.08);
    // OJO: mix() no recorta. Con un factor > 1 extrapola más allá del color de
    // destino y el campo salía amarillo fluorescente. El clamp es obligatorio.
    vec3 color = mix(uGrass, uGrassDry, clamp(blotch * 0.55 + macro * 0.5, 0.0, 1.0));
    color = mix(color, uDirt, smoothstep(0.52, 0.88, macro) * 0.5);
    color = mix(color, uDirt, smoothstep(0.16, 0.42, slope));
    color = mix(color, uRock, smoothstep(0.45, 0.72, slope));
    float shore = 1.0 - smoothstep(uWaterLevel, uWaterLevel + 2.6, vWorld.y);
    color = mix(color, uSand, shore * 0.85);
    // Barro húmedo justo en la ribera.
    color *= mix(1.0, 0.72, smoothstep(uWaterLevel + 1.6, uWaterLevel, vWorld.y));

    // Hebras de hierba: ruido muy fino y anisótropo que insinúa textura vegetal.
    float blades = noise2(vWorld.xz * vec2(2.6, 9.0));
    color *= 0.82 + noise2(vWorld.xz * 0.9) * 0.16 + blades * 0.1;

    // Sangre acumulada: se lee del mapa que se pinta con cada muerte.
    vec2 bloodUv = vWorld.xz / (uFieldHalf * 2.0) + 0.5;
    float blood = texture2D(uBlood, clamp(bloodUv, 0.0, 1.0)).r;
    blood *= step(0.0, bloodUv.x) * step(bloodUv.x, 1.0) * step(0.0, bloodUv.y) * step(bloodUv.y, 1.0);
    vec3 bloodColor = mix(vec3(0.28, 0.02, 0.02), vec3(0.55, 0.05, 0.04), noise2(vWorld.xz * 0.5));
    color = mix(color, bloodColor, clamp(blood, 0.0, 1.0) * 0.92);

    float diffuse = max(dot(n, normalize(uLightDir)), 0.0);
    float hemi = n.y * 0.5 + 0.5;
    vec3 ambient = mix(uGroundColor, uSkyColor, hemi);
    // Se apunta a que una superficie bien iluminada quede sobre 0.5 en lineal,
    // no cerca de 1.0: así queda margen para que el tonemapping module las altas
    // luces y solo el fuego real llegue al bloom.
    color = color * (ambient * 0.45 + uSunColor * diffuse * 0.6);

    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);
    color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(color, 1.0);
  }
`;

const WATER_VERTEX_SHADER = /* glsl */ `
  varying vec3 vWorld;
  varying float vFogDepth;
  void main() {
    vWorld = position;
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    vFogDepth = -mv.z;
    gl_Position = projectionMatrix * mv;
  }
`;

const WATER_FRAGMENT_SHADER = /* glsl */ `
  precision highp float;
${NOISE_2D}

  uniform vec3 uWater;
  uniform vec3 uWaterDeep;
  uniform vec3 uFogColor;
  uniform vec3 uSunColor;
  uniform float uFogDensity;
  uniform float uTime;
  uniform vec2 uFieldHalf;
  uniform sampler2D uBlood;
  uniform float uMeanderAmp;
  uniform float uMeanderFreq;
  uniform float uRiverHalf;

  varying vec3 vWorld;
  varying float vFogDepth;

  void main() {
    // La corriente baja siguiendo el cauce: el ruido se desplaza en +Z.
    float flow = uTime * 0.9;
    float ripple = noise2(vWorld.xz * 0.35 + vec2(uTime * 0.25, flow * 0.5))
                 + noise2(vWorld.xz * 0.9 - vec2(uTime * 0.15, flow)) * 0.5;
    ripple /= 1.5;

    vec3 color = mix(uWaterDeep, uWater, ripple);
    // Destello solo en las crestas más marcadas. Con un exponente bajo brillaba
    // media superficie y el río parecía rápidos de agua blanca.
    color += uSunColor * pow(ripple, 16.0) * 1.1;

    // Distancia normalizada al centro del cauce: 0 en el eje, 1 en la orilla.
    float center = sin(vWorld.z * uMeanderFreq) * uMeanderAmp
                 + sin(vWorld.z * uMeanderFreq * 2.7) * (uMeanderAmp * 0.3);
    float bank = clamp(abs(vWorld.x - center) / max(uRiverHalf, 0.001), 0.0, 1.0);

    // Espuma en la orilla: es lo que hace que el agua "toque" la tierra en vez
    // de terminar en un borde recortado.
    float foamNoise = noise2(vWorld.xz * 1.6 + vec2(0.0, flow * 1.6));
    float foam = smoothstep(0.84, 1.0, bank) * (0.3 + foamNoise * 0.5);
    color = mix(color, vec3(0.7, 0.78, 0.82), clamp(foam, 0.0, 0.65));

    // La sangre corriente abajo: el río se tiñe igual que en los streams reales.
    vec2 bloodUv = vWorld.xz / (uFieldHalf * 2.0) + 0.5;
    float blood = texture2D(uBlood, clamp(bloodUv, 0.0, 1.0)).r;
    color = mix(color, vec3(0.38, 0.03, 0.03), clamp(blood * 1.15, 0.0, 0.9));

    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);
    color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(color, 0.92);
  }
`;

/**
 * Construye la superficie del río: una cinta de quads que sigue el serpenteo del
 * cauce, un poco más ancha que el canal para que las orillas queden cubiertas.
 */
function buildRiverRibbon(terrain: Terrain, depth: number): THREE.BufferGeometry {
  const segments = 180;
  const halfWidth = terrain.riverWidth * 0.62;
  const positions = new Float32Array((segments + 1) * 2 * 3);
  const indices: number[] = [];

  for (let i = 0; i <= segments; i++) {
    const z = -depth / 2 + (i / segments) * depth;
    const center = terrain.riverCenter(z);
    const offset = i * 6;
    positions[offset] = center - halfWidth;
    positions[offset + 1] = 0;
    positions[offset + 2] = z;
    positions[offset + 3] = center + halfWidth;
    positions[offset + 4] = 0;
    positions[offset + 5] = z;

    if (i < segments) {
      const a = i * 2;
      indices.push(a, a + 2, a + 1, a + 1, a + 2, a + 3);
    }
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geometry.setIndex(indices);
  geometry.computeBoundingSphere();
  return geometry;
}

export class TerrainRenderer {
  readonly root = new THREE.Group();
  private ground: THREE.Mesh;
  private water: THREE.Mesh;
  private groundMaterial: THREE.ShaderMaterial;
  private waterMaterial: THREE.ShaderMaterial;
  private bloodTexture: THREE.DataTexture;
  private bloodData: Uint8Array;
  private bloodSize: number;
  private bloodDirty = false;
  private bloodUploadTimer = 0;
  private halfExtent: THREE.Vector2;

  constructor(
    terrain: Terrain,
    palette: TerrainPalette,
    options: { fogDensity: number; bloodTextureSize: number; quality: 'low' | 'medium' | 'high' | 'ultra' },
  ) {
    this.bloodSize = Math.max(64, Math.min(1024, options.bloodTextureSize));
    this.bloodData = new Uint8Array(this.bloodSize * this.bloodSize * 4);
    this.bloodTexture = new THREE.DataTexture(this.bloodData, this.bloodSize, this.bloodSize, THREE.RGBAFormat);
    this.bloodTexture.minFilter = THREE.LinearFilter;
    this.bloodTexture.magFilter = THREE.LinearFilter;
    this.bloodTexture.needsUpdate = true;

    // El terreno se dibuja más grande que el campo jugable para que el horizonte
    // no termine en un corte seco.
    const overscan = 1.9;
    const width = terrain.width * overscan;
    const depth = terrain.depth * overscan;
    this.halfExtent = new THREE.Vector2(terrain.halfWidth, terrain.halfDepth);

    const segments = { low: 96, medium: 144, high: 200, ultra: 288 }[options.quality];
    const geometry = new THREE.PlaneGeometry(width, depth, segments, Math.round(segments * (depth / width)));
    geometry.rotateX(-Math.PI / 2);

    const position = geometry.getAttribute('position') as THREE.BufferAttribute;
    for (let i = 0; i < position.count; i++) {
      const x = position.getX(i);
      const z = position.getZ(i);
      position.setY(i, terrain.height(x, z));
    }
    position.needsUpdate = true;
    geometry.computeVertexNormals();
    geometry.computeBoundingSphere();

    this.groundMaterial = new THREE.ShaderMaterial({
      vertexShader: TERRAIN_VERTEX_SHADER,
      fragmentShader: TERRAIN_FRAGMENT_SHADER,
      uniforms: {
        uGrass: { value: palette.grass.clone() },
        uGrassDry: { value: palette.grassDry.clone() },
        uDirt: { value: palette.dirt.clone() },
        uRock: { value: palette.rock.clone() },
        uSand: { value: palette.sand.clone() },
        uFogColor: { value: palette.fog.clone() },
        uSkyColor: { value: palette.sky.clone() },
        uGroundColor: { value: palette.ground.clone() },
        uSunColor: { value: palette.sun.clone() },
        uLightDir: { value: palette.lightDir.clone().normalize() },
        uFogDensity: { value: options.fogDensity },
        uWaterLevel: { value: terrain.waterLevel },
        uFieldHalf: { value: this.halfExtent.clone() },
        uBlood: { value: this.bloodTexture },
      },
    });

    this.ground = new THREE.Mesh(geometry, this.groundMaterial);
    this.ground.frustumCulled = false;
    this.ground.renderOrder = 0;
    this.root.add(this.ground);

    // El agua NO es un plano infinito: es una cinta que sigue el meandro del río.
    // Con un plano completo, cualquier hondonada del mapa se vería inundada.
    const waterGeometry = buildRiverRibbon(terrain, depth);
    this.waterMaterial = new THREE.ShaderMaterial({
      vertexShader: WATER_VERTEX_SHADER,
      fragmentShader: WATER_FRAGMENT_SHADER,
      uniforms: {
        uWater: { value: palette.water.clone() },
        uWaterDeep: { value: palette.waterDeep.clone() },
        uFogColor: { value: palette.fog.clone() },
        uSunColor: { value: palette.sun.clone() },
        uFogDensity: { value: options.fogDensity },
        uTime: { value: 0 },
        uFieldHalf: { value: this.halfExtent.clone() },
        uBlood: { value: this.bloodTexture },
        uMeanderAmp: { value: terrain.meanderAmp },
        uMeanderFreq: { value: terrain.meanderFreq },
        uRiverHalf: { value: terrain.riverWidth * 0.62 },
      },
      transparent: true,
      depthWrite: false,
    });
    this.water = new THREE.Mesh(waterGeometry, this.waterMaterial);
    this.water.position.y = terrain.waterLevel;
    this.water.frustumCulled = false;
    this.water.renderOrder = 1;
    this.root.add(this.water);
  }

  /**
   * Pinta una mancha de sangre. El radio crece con los soldados que representaba
   * la unidad, así que una entidad con stack de 500 deja un charco enorme.
   */
  paintBlood(x: number, z: number, soldiers: number): void {
    const u = (x / (this.halfExtent.x * 2) + 0.5) * this.bloodSize;
    const v = (z / (this.halfExtent.y * 2) + 0.5) * this.bloodSize;
    if (u < 0 || v < 0 || u >= this.bloodSize || v >= this.bloodSize) return;

    // Radio amplio y aporte suave: si cada muerte saturase su texel de golpe, la
    // mancha se vería como un mosaico de cuadros en lugar de un charco.
    const radius = clamp(2.2 + Math.log10(1 + soldiers) * 3.1, 2.2, 13);
    const strength = clamp(14 + Math.log10(1 + soldiers) * 26, 14, 96);
    const r0 = Math.max(0, Math.floor(v - radius));
    const r1 = Math.min(this.bloodSize - 1, Math.ceil(v + radius));
    const c0 = Math.max(0, Math.floor(u - radius));
    const c1 = Math.min(this.bloodSize - 1, Math.ceil(u + radius));

    for (let py = r0; py <= r1; py++) {
      const dy = py + 0.5 - v;
      for (let px = c0; px <= c1; px++) {
        const dx = px + 0.5 - u;
        const d = Math.hypot(dx, dy);
        if (d > radius) continue;
        // Falloff suave (smoothstep) en vez de lineal: los bordes se funden.
        const t = 1 - d / radius;
        const add = strength * t * t * (3 - 2 * t) * 0.5;
        const idx = (py * this.bloodSize + px) * 4;
        const next = this.bloodData[idx] + add;
        this.bloodData[idx] = next > 255 ? 255 : next;
      }
    }
    this.bloodDirty = true;
  }

  clearBlood(): void {
    this.bloodData.fill(0);
    this.bloodTexture.needsUpdate = true;
    this.bloodDirty = false;
  }

  update(dt: number, elapsed: number): void {
    this.waterMaterial.uniforms.uTime.value = elapsed;
    // Subir 1 MB de textura cada frame sería un desperdicio: se agrupa a ~8 Hz.
    this.bloodUploadTimer += dt;
    if (this.bloodDirty && this.bloodUploadTimer >= 0.125) {
      this.bloodTexture.needsUpdate = true;
      this.bloodDirty = false;
      this.bloodUploadTimer = 0;
    }
  }

  setPalette(palette: TerrainPalette): void {
    const g = this.groundMaterial.uniforms;
    g.uGrass.value.copy(palette.grass);
    g.uGrassDry.value.copy(palette.grassDry);
    g.uDirt.value.copy(palette.dirt);
    g.uRock.value.copy(palette.rock);
    g.uSand.value.copy(palette.sand);
    g.uFogColor.value.copy(palette.fog);
    g.uSkyColor.value.copy(palette.sky);
    g.uGroundColor.value.copy(palette.ground);
    g.uSunColor.value.copy(palette.sun);
    g.uLightDir.value.copy(palette.lightDir).normalize();

    const w = this.waterMaterial.uniforms;
    w.uWater.value.copy(palette.water);
    w.uWaterDeep.value.copy(palette.waterDeep);
    w.uFogColor.value.copy(palette.fog);
    w.uSunColor.value.copy(palette.sun);
  }

  setFogDensity(density: number): void {
    this.groundMaterial.uniforms.uFogDensity.value = density;
    this.waterMaterial.uniforms.uFogDensity.value = density;
  }

  dispose(): void {
    this.ground.geometry.dispose();
    this.water.geometry.dispose();
    this.groundMaterial.dispose();
    this.waterMaterial.dispose();
    this.bloodTexture.dispose();
  }
}
