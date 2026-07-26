/**
 * Vegetación y rocas del escenario.
 *
 * Sin nada alrededor, un campo de hierba no tiene escala: los soldados parecen
 * flotar sobre un plano verde. Estas piezas dan referencia de tamaño y rompen la
 * uniformidad del suelo.
 *
 * Se colocan con el mismo generador determinista que el terreno (misma semilla →
 * mismo bosque), evitando el pasillo central donde ocurre la batalla, y se
 * dibujan con dos InstancedMesh: dos draw calls para todo el decorado.
 */

import * as THREE from 'three';
import { createRng } from '../shared/math';
import type { Terrain } from '../shared/terrain';

const PROP_VERTEX = /* glsl */ `
  attribute vec3 aOffset;
  attribute vec3 aScale;
  attribute float aRot;
  attribute float aTint;

  uniform float uTime;
  uniform vec2 uWind;
  uniform float uSway;

  varying vec3 vNormal;
  varying float vTint;
  varying float vHeight;
  varying float vFogDepth;

  void main() {
    float s = sin(aRot);
    float c = cos(aRot);
    vec3 scaled = position * aScale;
    vec3 rotated = vec3(scaled.x * c + scaled.z * s, scaled.y, -scaled.x * s + scaled.z * c);
    vec3 world = rotated + aOffset;

    // Balanceo: proporcional a la altura sobre el tronco, así la copa se mueve y
    // la base queda clavada. Cada árbol lleva su propia fase.
    float sway = uSway * max(0.0, position.y) * aScale.y;
    float phase = uTime * 1.1 + aOffset.x * 0.21 + aOffset.z * 0.17;
    world.xz += uWind * sway * (0.6 + sin(phase) * 0.4);

    vec3 n = normal;
    vec3 rn = vec3(n.x * c + n.z * s, n.y, -n.x * s + n.z * c);

    vec4 mv = modelViewMatrix * vec4(world, 1.0);
    vNormal = rn;
    vTint = aTint;
    // Altura relativa dentro de la pieza: oscurece la base de los árboles.
    vHeight = clamp(position.y + 0.5, 0.0, 1.0);
    vFogDepth = -mv.z;
    gl_Position = projectionMatrix * mv;
  }
`;

const PROP_FRAGMENT = /* glsl */ `
  precision highp float;

  uniform vec3 uColorA;
  uniform vec3 uColorB;
  uniform vec3 uLightDir;
  uniform vec3 uSunColor;
  uniform vec3 uSkyColor;
  uniform vec3 uGroundColor;
  uniform vec3 uFogColor;
  uniform float uFogDensity;
  uniform float uOcclusion;

  varying vec3 vNormal;
  varying float vTint;
  varying float vHeight;
  varying float vFogDepth;

  void main() {
    vec3 n = normalize(vNormal);
    vec3 base = mix(uColorA, uColorB, vTint);

    // Oclusión falsa en la base: sin esto los props parecen pegatinas flotando.
    base *= mix(1.0 - uOcclusion, 1.0, vHeight);

    float ndl = dot(n, normalize(uLightDir));
    float diffuse = pow(ndl * 0.5 + 0.5, 1.5);
    float hemi = n.y * 0.5 + 0.5;
    vec3 ambient = mix(uGroundColor, uSkyColor, hemi);
    vec3 color = base * (ambient * 0.5 + uSunColor * diffuse * 0.62);

    float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * vFogDepth * vFogDepth);
    color = mix(color, uFogColor, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(color, 1.0);
  }
`;

interface PropPlacement {
  x: number;
  y: number;
  z: number;
  sx: number;
  sy: number;
  sz: number;
  rot: number;
  tint: number;
}

/** Roca angulosa de pocos triángulos. */
function rockGeometry(): THREE.BufferGeometry {
  const geometry = new THREE.IcosahedronGeometry(0.6, 0);
  // Se aplasta y se deforma para que no parezca una pelota.
  const position = geometry.getAttribute('position') as THREE.BufferAttribute;
  const rng = createRng(4242);
  for (let i = 0; i < position.count; i++) {
    position.setXYZ(
      i,
      position.getX(i) * (0.8 + rng() * 0.6),
      position.getY(i) * (0.5 + rng() * 0.4),
      position.getZ(i) * (0.8 + rng() * 0.6),
    );
  }
  geometry.computeVertexNormals();
  return geometry;
}

/** Árbol: tronco y dos volúmenes de copa, en cajas. */
function treeGeometry(): THREE.BufferGeometry {
  const trunk = new THREE.BoxGeometry(0.34, 2.4, 0.34);
  trunk.translate(0, 1.2, 0);
  const canopyLow = new THREE.BoxGeometry(2.6, 1.5, 2.6);
  canopyLow.translate(0, 3.0, 0);
  const canopyHigh = new THREE.BoxGeometry(1.7, 1.4, 1.7);
  canopyHigh.translate(0, 4.1, 0);

  const merged = mergeGeometries([trunk, canopyLow, canopyHigh]);
  trunk.dispose();
  canopyLow.dispose();
  canopyHigh.dispose();
  return merged;
}

/** Fusión mínima de geometrías no indexadas (evita depender de los addons). */
function mergeGeometries(parts: THREE.BufferGeometry[]): THREE.BufferGeometry {
  const positions: number[] = [];
  const normals: number[] = [];
  for (const part of parts) {
    const nonIndexed = part.index ? part.toNonIndexed() : part;
    const p = nonIndexed.getAttribute('position');
    const n = nonIndexed.getAttribute('normal');
    for (let i = 0; i < p.count; i++) {
      positions.push(p.getX(i), p.getY(i), p.getZ(i));
      normals.push(n.getX(i), n.getY(i), n.getZ(i));
    }
    if (nonIndexed !== part) nonIndexed.dispose();
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3));
  return geometry;
}

export interface PropsOptions {
  density: number;
  fogColor: THREE.Color;
  fogDensity: number;
}

export class Props {
  readonly root = new THREE.Group();
  private materials: THREE.ShaderMaterial[] = [];
  private geometries: THREE.BufferGeometry[] = [];
  /** Solo la capa de árboles cambia de color con la estación. */
  private foliageMaterial: THREE.ShaderMaterial | null = null;

  constructor(terrain: Terrain, options: PropsOptions) {
    if (options.density <= 0) return;

    const rng = createRng(terrain.seed * 31 + 7);
    const rocks: PropPlacement[] = [];
    const trees: PropPlacement[] = [];

    // Se muestrea generosamente y se descarta lo que no encaja: es más simple
    // que resolver analíticamente dónde cabe cada pieza.
    const attempts = Math.round(1400 * options.density);
    for (let i = 0; i < attempts; i++) {
      const x = rng.range(-terrain.halfWidth * 1.45, terrain.halfWidth * 1.45);
      const z = rng.range(-terrain.halfDepth * 1.45, terrain.halfDepth * 1.45);
      const y = terrain.height(x, z);

      // Nada dentro del cauce ni en la orilla inmediata.
      if (terrain.riverFactor(x, z) > 0.12) continue;
      if (y < terrain.waterLevel + 1.2) continue;

      // El corredor central es zona de combate: los props estorbarían la lectura.
      const insideField = Math.abs(x) < terrain.halfWidth && Math.abs(z) < terrain.halfDepth;
      const edgeDistance = Math.min(terrain.halfWidth - Math.abs(x), terrain.halfDepth - Math.abs(z));
      if (insideField && edgeDistance > terrain.halfWidth * 0.16) continue;

      const rot = rng.range(0, Math.PI * 2);
      const tint = rng();
      if (rng() < 0.45) {
        const s = rng.range(0.7, 2.4);
        rocks.push({ x, y: y - 0.15, z, sx: s, sy: s * rng.range(0.6, 1.1), sz: s, rot, tint });
      } else {
        const s = rng.range(0.75, 1.9);
        trees.push({ x, y, z, sx: s, sy: s * rng.range(0.85, 1.45), sz: s, rot, tint });
      }
    }

    this.addLayer(rockGeometry(), rocks, new THREE.Color('#6e6a63'), new THREE.Color('#4c4a45'), 0.25, options);
    this.addLayer(treeGeometry(), trees, new THREE.Color('#3d6b2c'), new THREE.Color('#2b5220'), 0.45, options, 0.055);
    this.foliageMaterial = this.materials[this.materials.length - 1] ?? null;
  }

  private addLayer(
    base: THREE.BufferGeometry,
    placements: PropPlacement[],
    colorA: THREE.Color,
    colorB: THREE.Color,
    occlusion: number,
    options: PropsOptions,
    sway = 0,
  ): void {
    if (placements.length === 0) {
      base.dispose();
      return;
    }

    const geometry = new THREE.InstancedBufferGeometry();
    if (base.index) geometry.setIndex(base.index);
    geometry.setAttribute('position', base.getAttribute('position'));
    geometry.setAttribute('normal', base.getAttribute('normal'));

    const offsets = new Float32Array(placements.length * 3);
    const scales = new Float32Array(placements.length * 3);
    const rotations = new Float32Array(placements.length);
    const tints = new Float32Array(placements.length);
    placements.forEach((p, i) => {
      offsets[i * 3] = p.x;
      offsets[i * 3 + 1] = p.y;
      offsets[i * 3 + 2] = p.z;
      scales[i * 3] = p.sx;
      scales[i * 3 + 1] = p.sy;
      scales[i * 3 + 2] = p.sz;
      rotations[i] = p.rot;
      tints[i] = p.tint;
    });

    geometry.setAttribute('aOffset', new THREE.InstancedBufferAttribute(offsets, 3));
    geometry.setAttribute('aScale', new THREE.InstancedBufferAttribute(scales, 3));
    geometry.setAttribute('aRot', new THREE.InstancedBufferAttribute(rotations, 1));
    geometry.setAttribute('aTint', new THREE.InstancedBufferAttribute(tints, 1));
    geometry.instanceCount = placements.length;
    geometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);

    const material = new THREE.ShaderMaterial({
      vertexShader: PROP_VERTEX,
      fragmentShader: PROP_FRAGMENT,
      uniforms: {
        uColorA: { value: colorA },
        uColorB: { value: colorB },
        uLightDir: { value: new THREE.Vector3(0.45, 0.82, 0.35).normalize() },
        uSunColor: { value: new THREE.Color('#fff2d8') },
        uSkyColor: { value: new THREE.Color('#8fb6e8') },
        uGroundColor: { value: new THREE.Color('#4a4030') },
        uFogColor: { value: options.fogColor.clone() },
        uFogDensity: { value: options.fogDensity },
        uOcclusion: { value: occlusion },
        uTime: { value: 0 },
        uWind: { value: new THREE.Vector2() },
        // Las rocas no se mecen; los árboles sí.
        uSway: { value: sway },
      },
    });

    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;
    mesh.renderOrder = 1;
    this.root.add(mesh);
    this.materials.push(material);
    this.geometries.push(geometry, base);
  }

  setLighting(sky: THREE.Color, ground: THREE.Color, sun: THREE.Color, dir: THREE.Vector3, fog: THREE.Color, fogDensity: number): void {
    for (const material of this.materials) {
      material.uniforms.uSkyColor.value.copy(sky);
      material.uniforms.uGroundColor.value.copy(ground);
      material.uniforms.uSunColor.value.copy(sun);
      material.uniforms.uLightDir.value.copy(dir).normalize();
      material.uniforms.uFogColor.value.copy(fog);
      material.uniforms.uFogDensity.value = fogDensity;
    }
  }

  /** Color del follaje según la estación. */
  setFoliage(colorA: THREE.Color, colorB: THREE.Color): void {
    if (!this.foliageMaterial) return;
    this.foliageMaterial.uniforms.uColorA.value.copy(colorA);
    this.foliageMaterial.uniforms.uColorB.value.copy(colorB);
  }

  /** Reloj y viento: mecen las copas. */
  setTime(time: number, wind: THREE.Vector2): void {
    for (const material of this.materials) {
      material.uniforms.uTime.value = time;
      material.uniforms.uWind.value.copy(wind);
    }
  }

  dispose(): void {
    for (const material of this.materials) material.dispose();
    for (const geometry of this.geometries) geometry.dispose();
    this.materials.length = 0;
    this.geometries.length = 0;
  }
}
