/**
 * Geometría procedural de las unidades.
 *
 * Todo se construye con cajas y se fusiona en una sola BufferGeometry indexada.
 * Cada vértice lleva dos atributos extra:
 *   - `aLimb`  : a qué parte del cuerpo pertenece (torso, pierna, brazo, ala...)
 *   - `aPivot` : el punto sobre el que gira esa parte
 * Con eso el vertex shader anima el ciclo de caminata, el golpe y la caída sin
 * que la CPU toque un solo hueso. Es lo que permite miles de unidades animadas.
 */

import * as THREE from 'three';

export const LIMB = {
  BODY: 0,
  LEG_L: 1,
  LEG_R: 2,
  ARM_L: 3,
  ARM_R: 4,
  HEAD: 5,
  WING: 6,
  STATIC: 7,
} as const;

interface BoxSpec {
  w: number;
  h: number;
  d: number;
  x: number;
  y: number;
  z: number;
  limb: number;
  pivot?: [number, number, number];
  /** 0 = piel, 1 = armadura/tela (recibe el color del equipo), 2 = metal. */
  shade: number;
}

interface Accumulator {
  positions: number[];
  normals: number[];
  limbs: number[];
  pivots: number[];
  shades: number[];
  indices: number[];
}

const FACE_DIRS: Array<[number, number, number]> = [
  [1, 0, 0],
  [-1, 0, 0],
  [0, 1, 0],
  [0, -1, 0],
  [0, 0, 1],
  [0, 0, -1],
];

/** Genera una caja indexada (24 vértices, 12 triángulos) con normales por cara. */
function addBox(acc: Accumulator, spec: BoxSpec): void {
  const { w, h, d, x, y, z, limb, shade } = spec;
  const pivot = spec.pivot ?? [x, y, z];
  const hw = w / 2;
  const hh = h / 2;
  const hd = d / 2;

  for (const [nx, ny, nz] of FACE_DIRS) {
    const base = acc.positions.length / 3;
    // Dos vectores tangentes ortogonales a la normal de la cara.
    const tangent: [number, number, number] = nx !== 0 ? [0, 0, 1] : [1, 0, 0];
    const bitangent: [number, number, number] = ny !== 0 ? [0, 0, 1] : [0, 1, 0];
    const cx = x + nx * hw;
    const cy = y + ny * hh;
    const cz = z + nz * hd;
    const su = nx !== 0 ? hd : hw;
    const sv = ny !== 0 ? hd : hh;

    for (const [u, v] of [
      [-1, -1],
      [1, -1],
      [1, 1],
      [-1, 1],
    ] as Array<[number, number]>) {
      acc.positions.push(
        cx + tangent[0] * u * su + bitangent[0] * v * sv,
        cy + tangent[1] * u * su + bitangent[1] * v * sv,
        cz + tangent[2] * u * su + bitangent[2] * v * sv,
      );
      acc.normals.push(nx, ny, nz);
      acc.limbs.push(limb);
      acc.pivots.push(pivot[0], pivot[1], pivot[2]);
      acc.shades.push(shade);
    }
    // El orden de los triángulos se invierte en las caras negativas para que
    // el winding quede consistente y el backface culling funcione.
    const flip = nx + ny + nz < 0;
    if (flip) acc.indices.push(base, base + 2, base + 1, base, base + 3, base + 2);
    else acc.indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }
}

function build(specs: BoxSpec[]): THREE.BufferGeometry {
  const acc: Accumulator = { positions: [], normals: [], limbs: [], pivots: [], shades: [], indices: [] };
  for (const spec of specs) addBox(acc, spec);

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(acc.positions, 3));
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(acc.normals, 3));
  geometry.setAttribute('aLimb', new THREE.Float32BufferAttribute(acc.limbs, 1));
  geometry.setAttribute('aPivot', new THREE.Float32BufferAttribute(acc.pivots, 3));
  geometry.setAttribute('aShade', new THREE.Float32BufferAttribute(acc.shades, 1));
  geometry.setIndex(acc.indices);
  geometry.computeBoundingSphere();
  // Radio generoso: las instancias se desplazan en el shader, así que el culling
  // por bounding sphere de three.js no puede verlas venir.
  geometry.boundingSphere!.radius = 4;
  return geometry;
}

/** Guerrero humanoide: torso, cabeza, dos piernas, dos brazos y un arma. */
function humanoid(detail: 'full' | 'simple'): THREE.BufferGeometry {
  const specs: BoxSpec[] = [
    { w: 0.52, h: 0.72, d: 0.3, x: 0, y: 1.16, z: 0, limb: LIMB.BODY, shade: 1 },
    { w: 0.32, h: 0.32, d: 0.32, x: 0, y: 1.68, z: 0, limb: LIMB.HEAD, pivot: [0, 1.52, 0], shade: 0 },
    { w: 0.2, h: 0.78, d: 0.22, x: -0.15, y: 0.39, z: 0, limb: LIMB.LEG_L, pivot: [-0.15, 0.78, 0], shade: 1 },
    { w: 0.2, h: 0.78, d: 0.22, x: 0.15, y: 0.39, z: 0, limb: LIMB.LEG_R, pivot: [0.15, 0.78, 0], shade: 1 },
  ];
  if (detail === 'full') {
    specs.push(
      { w: 0.16, h: 0.64, d: 0.18, x: -0.35, y: 1.16, z: 0, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade: 1 },
      { w: 0.16, h: 0.64, d: 0.18, x: 0.35, y: 1.16, z: 0, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: 1 },
      // Arma: cuelga del brazo derecho y hereda su rotación.
      { w: 0.07, h: 0.86, d: 0.07, x: 0.35, y: 1.32, z: 0.26, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: 2 },
    );
  }
  return build(specs);
}

/** Bestia de monta: cuadrúpedo con jinete. */
function beast(detail: 'full' | 'simple'): THREE.BufferGeometry {
  const specs: BoxSpec[] = [
    { w: 0.5, h: 0.5, d: 1.2, x: 0, y: 0.95, z: 0, limb: LIMB.BODY, shade: 1 },
    { w: 0.3, h: 0.32, d: 0.5, x: 0, y: 1.2, z: 0.75, limb: LIMB.HEAD, pivot: [0, 1.05, 0.55], shade: 0 },
    { w: 0.16, h: 0.72, d: 0.16, x: -0.2, y: 0.36, z: 0.42, limb: LIMB.LEG_L, pivot: [-0.2, 0.72, 0.42], shade: 0 },
    { w: 0.16, h: 0.72, d: 0.16, x: 0.2, y: 0.36, z: 0.42, limb: LIMB.LEG_R, pivot: [0.2, 0.72, 0.42], shade: 0 },
    { w: 0.16, h: 0.72, d: 0.16, x: -0.2, y: 0.36, z: -0.42, limb: LIMB.LEG_R, pivot: [-0.2, 0.72, -0.42], shade: 0 },
    { w: 0.16, h: 0.72, d: 0.16, x: 0.2, y: 0.36, z: -0.42, limb: LIMB.LEG_L, pivot: [0.2, 0.72, -0.42], shade: 0 },
  ];
  if (detail === 'full') {
    specs.push(
      { w: 0.4, h: 0.6, d: 0.28, x: 0, y: 1.5, z: -0.1, limb: LIMB.BODY, shade: 1 },
      { w: 0.26, h: 0.26, d: 0.26, x: 0, y: 1.92, z: -0.1, limb: LIMB.HEAD, pivot: [0, 1.8, -0.1], shade: 0 },
      { w: 0.07, h: 0.95, d: 0.07, x: 0.3, y: 1.6, z: 0.2, limb: LIMB.ARM_R, pivot: [0.3, 1.72, -0.1], shade: 2 },
    );
  }
  return build(specs);
}

/** Dragón: cuerpo alargado, alas que baten y cola. */
function dragon(): THREE.BufferGeometry {
  return build([
    { w: 0.6, h: 0.5, d: 1.8, x: 0, y: 1.2, z: 0, limb: LIMB.BODY, shade: 1 },
    { w: 0.42, h: 0.4, d: 0.7, x: 0, y: 1.4, z: 1.15, limb: LIMB.HEAD, pivot: [0, 1.3, 0.9], shade: 2 },
    { w: 0.24, h: 0.2, d: 1.3, x: 0, y: 1.15, z: -1.4, limb: LIMB.WING, pivot: [0, 1.2, -0.9], shade: 1 },
    { w: 2.1, h: 0.08, d: 0.9, x: -1.2, y: 1.35, z: -0.1, limb: LIMB.WING, pivot: [-0.2, 1.35, -0.1], shade: 1 },
    { w: 2.1, h: 0.08, d: 0.9, x: 1.2, y: 1.35, z: -0.1, limb: LIMB.WING, pivot: [0.2, 1.35, -0.1], shade: 1 },
    { w: 0.18, h: 0.5, d: 0.18, x: -0.22, y: 0.75, z: 0.3, limb: LIMB.LEG_L, pivot: [-0.22, 1.0, 0.3], shade: 2 },
    { w: 0.18, h: 0.5, d: 0.18, x: 0.22, y: 0.75, z: 0.3, limb: LIMB.LEG_R, pivot: [0.22, 1.0, 0.3], shade: 2 },
  ]);
}

export function createUnitGeometries(detail: 'full' | 'simple'): Record<'humanoid' | 'beast' | 'dragon', THREE.BufferGeometry> {
  return {
    humanoid: humanoid(detail),
    beast: beast(detail),
    dragon: dragon(),
  };
}

/** Disco plano para la sombra de contacto bajo cada unidad. */
export function createBlobShadowGeometry(): THREE.BufferGeometry {
  const geometry = new THREE.CircleGeometry(0.62, 8);
  geometry.rotateX(-Math.PI / 2);
  geometry.computeBoundingSphere();
  geometry.boundingSphere!.radius = 4;
  return geometry;
}
