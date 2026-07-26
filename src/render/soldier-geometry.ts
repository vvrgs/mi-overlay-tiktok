/**
 * Geometría procedural de las unidades.
 *
 * Cada arquetipo tiene su propia silueta —el soldado lleva espada y escudo, el
 * arquero un arco, el mago un báculo con orbe, el campeón capa— porque a la
 * distancia de cámara del directo la silueta es lo único que distingue una
 * unidad de otra: el color ya lo ocupa el equipo.
 *
 * Todo se construye con cajas y se fusiona en una BufferGeometry indexada. Cada
 * vértice lleva tres atributos extra:
 *   - `aLimb`  : a qué parte del cuerpo pertenece (torso, pierna, brazo, ala...)
 *   - `aPivot` : el punto sobre el que gira esa parte
 *   - `aShade` : de qué está hecho (piel, tela del equipo, metal, madera, brillo)
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

/** Material de cada pieza. El shader lo traduce a color. */
export const SHADE = {
  SKIN: 0,
  TEAM: 1,
  METAL: 2,
  /** Emisivo: brilla y lo recoge el bloom (orbe del mago, ojos del dragón). */
  GLOW: 3,
  /** Madera y cuero: arcos, astas, correas. */
  WOOD: 4,
} as const;

export type Detail = 'full' | 'simple';

interface BoxSpec {
  w: number;
  h: number;
  d: number;
  x: number;
  y: number;
  z: number;
  limb: number;
  pivot?: [number, number, number];
  shade: number;
  /** Inclinación sobre el eje Z, en radianes (para escudos, arcos, lanzas). */
  tilt?: number;
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
  const tilt = spec.tilt ?? 0;
  const cosT = Math.cos(tilt);
  const sinT = Math.sin(tilt);
  const hw = w / 2;
  const hh = h / 2;
  const hd = d / 2;

  // La inclinación gira la pieza alrededor de su propio centro, sobre el eje Z.
  const rot = (px: number, py: number, pz: number): [number, number, number] => {
    if (tilt === 0) return [px, py, pz];
    const dx = px - x;
    const dy = py - y;
    return [x + dx * cosT - dy * sinT, y + dx * sinT + dy * cosT, pz];
  };

  for (const [nx, ny, nz] of FACE_DIRS) {
    const base = acc.positions.length / 3;
    const tangent: [number, number, number] = nx !== 0 ? [0, 0, 1] : [1, 0, 0];
    const bitangent: [number, number, number] = ny !== 0 ? [0, 0, 1] : [0, 1, 0];
    const cx = x + nx * hw;
    const cy = y + ny * hh;
    const cz = z + nz * hd;
    const su = nx !== 0 ? hd : hw;
    const sv = ny !== 0 ? hd : hh;
    const [rnx, rny] = tilt === 0 ? [nx, ny] : [nx * cosT - ny * sinT, nx * sinT + ny * cosT];

    for (const [u, v] of [
      [-1, -1],
      [1, -1],
      [1, 1],
      [-1, 1],
    ] as Array<[number, number]>) {
      const [px, py, pz] = rot(
        cx + tangent[0] * u * su + bitangent[0] * v * sv,
        cy + tangent[1] * u * su + bitangent[1] * v * sv,
        cz + tangent[2] * u * su + bitangent[2] * v * sv,
      );
      acc.positions.push(px, py, pz);
      acc.normals.push(rnx, rny, nz);
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

// ------------------------------------------------------------ piezas comunes

/** Torso, cabeza y piernas: la base de cualquier humanoide. */
function torsoAndLegs(shade = SHADE.TEAM): BoxSpec[] {
  return [
    { w: 0.52, h: 0.72, d: 0.3, x: 0, y: 1.16, z: 0, limb: LIMB.BODY, shade },
    { w: 0.3, h: 0.3, d: 0.3, x: 0, y: 1.66, z: 0, limb: LIMB.HEAD, pivot: [0, 1.52, 0], shade: SHADE.SKIN },
    { w: 0.2, h: 0.78, d: 0.22, x: -0.15, y: 0.39, z: 0, limb: LIMB.LEG_L, pivot: [-0.15, 0.78, 0], shade },
    { w: 0.2, h: 0.78, d: 0.22, x: 0.15, y: 0.39, z: 0, limb: LIMB.LEG_R, pivot: [0.15, 0.78, 0], shade },
  ];
}

function arms(shade = SHADE.TEAM): BoxSpec[] {
  return [
    { w: 0.16, h: 0.64, d: 0.18, x: -0.35, y: 1.16, z: 0, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade },
    { w: 0.16, h: 0.64, d: 0.18, x: 0.35, y: 1.16, z: 0, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade },
  ];
}

// -------------------------------------------------------------- arquetipos

/** Soldado: casco, espada al frente y escudo en el brazo izquierdo. */
function soldier(detail: Detail): THREE.BufferGeometry {
  const specs = [...torsoAndLegs()];
  if (detail === 'simple') return build(specs);
  specs.push(
    ...arms(),
    // Casco: una franja metálica sobre la cabeza. Rompe la silueta redonda.
    { w: 0.34, h: 0.14, d: 0.34, x: 0, y: 1.82, z: 0, limb: LIMB.HEAD, pivot: [0, 1.52, 0], shade: SHADE.METAL },
    // Espada.
    { w: 0.07, h: 0.9, d: 0.07, x: 0.35, y: 1.34, z: 0.3, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: SHADE.METAL },
    { w: 0.2, h: 0.07, d: 0.07, x: 0.35, y: 0.92, z: 0.3, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: SHADE.WOOD },
    // Escudo: la pieza que más se ve desde arriba, va del color del equipo.
    { w: 0.08, h: 0.62, d: 0.5, x: -0.46, y: 1.16, z: 0.1, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade: SHADE.TEAM },
    { w: 0.05, h: 0.2, d: 0.16, x: -0.51, y: 1.16, z: 0.1, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade: SHADE.METAL },
  );
  return build(specs);
}

/** Arquero: capucha, arco curvado y carcaj a la espalda. */
function archer(detail: Detail): THREE.BufferGeometry {
  const specs = [...torsoAndLegs()];
  if (detail === 'simple') return build(specs);
  specs.push(
    ...arms(),
    { w: 0.34, h: 0.2, d: 0.36, x: 0, y: 1.78, z: -0.03, limb: LIMB.HEAD, pivot: [0, 1.52, 0], shade: SHADE.TEAM },
    // Arco: tres segmentos inclinados que insinúan la curva.
    { w: 0.06, h: 0.5, d: 0.06, x: -0.44, y: 1.4, z: 0.12, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade: SHADE.WOOD, tilt: 0.32 },
    { w: 0.06, h: 0.42, d: 0.06, x: -0.5, y: 1.06, z: 0.12, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade: SHADE.WOOD },
    { w: 0.06, h: 0.5, d: 0.06, x: -0.44, y: 0.72, z: 0.12, limb: LIMB.ARM_L, pivot: [-0.35, 1.46, 0], shade: SHADE.WOOD, tilt: -0.32 },
    // Carcaj con flechas asomando.
    { w: 0.16, h: 0.42, d: 0.16, x: 0.14, y: 1.26, z: -0.22, limb: LIMB.BODY, shade: SHADE.WOOD, tilt: 0.35 },
    { w: 0.04, h: 0.28, d: 0.04, x: 0.2, y: 1.6, z: -0.24, limb: LIMB.BODY, shade: SHADE.METAL, tilt: 0.35 },
  );
  return build(specs);
}

/** Mago: túnica larga, sombrero puntiagudo y báculo con orbe brillante. */
function mage(detail: Detail): THREE.BufferGeometry {
  const specs: BoxSpec[] = [
    // Túnica: se ensancha hacia abajo en dos tramos, sin piernas visibles.
    { w: 0.5, h: 0.6, d: 0.3, x: 0, y: 1.24, z: 0, limb: LIMB.BODY, shade: SHADE.TEAM },
    { w: 0.62, h: 0.7, d: 0.42, x: 0, y: 0.58, z: 0, limb: LIMB.BODY, shade: SHADE.TEAM },
    { w: 0.28, h: 0.28, d: 0.28, x: 0, y: 1.68, z: 0, limb: LIMB.HEAD, pivot: [0, 1.54, 0], shade: SHADE.SKIN },
  ];
  if (detail === 'simple') return build(specs);
  specs.push(
    ...arms(),
    // Sombrero cónico aproximado con tres cajas cada vez menores.
    { w: 0.46, h: 0.08, d: 0.46, x: 0, y: 1.86, z: 0, limb: LIMB.HEAD, pivot: [0, 1.54, 0], shade: SHADE.TEAM },
    { w: 0.28, h: 0.2, d: 0.28, x: 0, y: 1.98, z: 0, limb: LIMB.HEAD, pivot: [0, 1.54, 0], shade: SHADE.TEAM },
    { w: 0.12, h: 0.22, d: 0.12, x: 0, y: 2.16, z: 0, limb: LIMB.HEAD, pivot: [0, 1.54, 0], shade: SHADE.TEAM },
    // Báculo: el orbe es emisivo y lo recoge el bloom.
    { w: 0.06, h: 1.5, d: 0.06, x: 0.4, y: 1.1, z: 0.16, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: SHADE.WOOD },
    { w: 0.22, h: 0.22, d: 0.22, x: 0.4, y: 1.95, z: 0.16, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: SHADE.GLOW },
  );
  return build(specs);
}

/** Caballería: montura de cuatro patas con jinete y lanza. */
function cavalry(detail: Detail): THREE.BufferGeometry {
  const specs: BoxSpec[] = [
    { w: 0.5, h: 0.5, d: 1.2, x: 0, y: 0.95, z: 0, limb: LIMB.BODY, shade: SHADE.SKIN },
    { w: 0.28, h: 0.3, d: 0.5, x: 0, y: 1.2, z: 0.75, limb: LIMB.HEAD, pivot: [0, 1.05, 0.55], shade: SHADE.SKIN },
    { w: 0.16, h: 0.72, d: 0.16, x: -0.2, y: 0.36, z: 0.42, limb: LIMB.LEG_L, pivot: [-0.2, 0.72, 0.42], shade: SHADE.SKIN },
    { w: 0.16, h: 0.72, d: 0.16, x: 0.2, y: 0.36, z: 0.42, limb: LIMB.LEG_R, pivot: [0.2, 0.72, 0.42], shade: SHADE.SKIN },
    { w: 0.16, h: 0.72, d: 0.16, x: -0.2, y: 0.36, z: -0.42, limb: LIMB.LEG_R, pivot: [-0.2, 0.72, -0.42], shade: SHADE.SKIN },
    { w: 0.16, h: 0.72, d: 0.16, x: 0.2, y: 0.36, z: -0.42, limb: LIMB.LEG_L, pivot: [0.2, 0.72, -0.42], shade: SHADE.SKIN },
  ];
  if (detail === 'simple') return build(specs);
  specs.push(
    // Gualdrapa del color del equipo: lo que identifica al bando de lejos.
    { w: 0.56, h: 0.34, d: 0.8, x: 0, y: 0.82, z: -0.1, limb: LIMB.BODY, shade: SHADE.TEAM },
    // Jinete.
    { w: 0.4, h: 0.6, d: 0.28, x: 0, y: 1.5, z: -0.1, limb: LIMB.BODY, shade: SHADE.TEAM },
    { w: 0.26, h: 0.26, d: 0.26, x: 0, y: 1.92, z: -0.1, limb: LIMB.HEAD, pivot: [0, 1.8, -0.1], shade: SHADE.SKIN },
    { w: 0.3, h: 0.12, d: 0.3, x: 0, y: 2.06, z: -0.1, limb: LIMB.HEAD, pivot: [0, 1.8, -0.1], shade: SHADE.METAL },
    // Lanza calada hacia adelante.
    { w: 0.06, h: 0.06, d: 1.8, x: 0.3, y: 1.6, z: 0.35, limb: LIMB.ARM_R, pivot: [0.3, 1.72, -0.1], shade: SHADE.WOOD },
    { w: 0.1, h: 0.1, d: 0.28, x: 0.3, y: 1.6, z: 1.32, limb: LIMB.ARM_R, pivot: [0.3, 1.72, -0.1], shade: SHADE.METAL },
  );
  return build(specs);
}

/** Gigante: corpulento, hombreras y una maza enorme. */
function giant(detail: Detail): THREE.BufferGeometry {
  const specs: BoxSpec[] = [
    { w: 0.78, h: 0.82, d: 0.46, x: 0, y: 1.2, z: 0, limb: LIMB.BODY, shade: SHADE.SKIN },
    { w: 0.36, h: 0.36, d: 0.36, x: 0, y: 1.78, z: 0, limb: LIMB.HEAD, pivot: [0, 1.6, 0], shade: SHADE.SKIN },
    { w: 0.28, h: 0.8, d: 0.3, x: -0.2, y: 0.4, z: 0, limb: LIMB.LEG_L, pivot: [-0.2, 0.8, 0], shade: SHADE.TEAM },
    { w: 0.28, h: 0.8, d: 0.3, x: 0.2, y: 0.4, z: 0, limb: LIMB.LEG_R, pivot: [0.2, 0.8, 0], shade: SHADE.TEAM },
  ];
  if (detail === 'simple') return build(specs);
  specs.push(
    { w: 0.22, h: 0.78, d: 0.24, x: -0.5, y: 1.14, z: 0, limb: LIMB.ARM_L, pivot: [-0.5, 1.5, 0], shade: SHADE.SKIN },
    { w: 0.22, h: 0.78, d: 0.24, x: 0.5, y: 1.14, z: 0, limb: LIMB.ARM_R, pivot: [0.5, 1.5, 0], shade: SHADE.SKIN },
    // Hombreras: le dan la silueta ancha que lo distingue a lo lejos.
    { w: 0.34, h: 0.22, d: 0.36, x: -0.5, y: 1.52, z: 0, limb: LIMB.ARM_L, pivot: [-0.5, 1.5, 0], shade: SHADE.METAL },
    { w: 0.34, h: 0.22, d: 0.36, x: 0.5, y: 1.52, z: 0, limb: LIMB.ARM_R, pivot: [0.5, 1.5, 0], shade: SHADE.METAL },
    { w: 0.5, h: 0.24, d: 0.5, x: 0, y: 1.62, z: 0, limb: LIMB.BODY, shade: SHADE.TEAM },
    // Maza.
    { w: 0.12, h: 1.1, d: 0.12, x: 0.5, y: 0.9, z: 0.3, limb: LIMB.ARM_R, pivot: [0.5, 1.5, 0], shade: SHADE.WOOD },
    { w: 0.36, h: 0.4, d: 0.36, x: 0.5, y: 0.32, z: 0.3, limb: LIMB.ARM_R, pivot: [0.5, 1.5, 0], shade: SHADE.METAL },
  );
  return build(specs);
}

/** Campeón (viewer del chat): capa al viento, penacho y espada larga. */
function champion(detail: Detail): THREE.BufferGeometry {
  const specs = [...torsoAndLegs()];
  if (detail === 'simple') return build(specs);
  specs.push(
    ...arms(),
    { w: 0.34, h: 0.16, d: 0.34, x: 0, y: 1.84, z: 0, limb: LIMB.HEAD, pivot: [0, 1.52, 0], shade: SHADE.METAL },
    // Penacho: identifica al campeón de un vistazo entre miles de soldados.
    { w: 0.1, h: 0.3, d: 0.1, x: 0, y: 2.02, z: 0, limb: LIMB.HEAD, pivot: [0, 1.52, 0], shade: SHADE.GLOW },
    // Capa: ondea con el mismo balanceo que las alas del dragón.
    { w: 0.5, h: 0.9, d: 0.07, x: 0, y: 1.06, z: -0.22, limb: LIMB.WING, pivot: [0, 1.5, -0.18], shade: SHADE.TEAM },
    { w: 0.1, h: 1.1, d: 0.1, x: 0.36, y: 1.3, z: 0.3, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: SHADE.METAL },
    { w: 0.24, h: 0.08, d: 0.08, x: 0.36, y: 0.8, z: 0.3, limb: LIMB.ARM_R, pivot: [0.35, 1.46, 0], shade: SHADE.METAL },
  );
  return build(specs);
}

/** Dragón: cuerpo alargado, alas que baten, cola y fauces encendidas. */
function dragon(): THREE.BufferGeometry {
  return build([
    { w: 0.6, h: 0.5, d: 1.8, x: 0, y: 1.2, z: 0, limb: LIMB.BODY, shade: SHADE.TEAM },
    { w: 0.42, h: 0.4, d: 0.7, x: 0, y: 1.4, z: 1.15, limb: LIMB.HEAD, pivot: [0, 1.3, 0.9], shade: SHADE.TEAM },
    // Fauces encendidas: el punto emisivo que hace que el dragón brille.
    { w: 0.2, h: 0.16, d: 0.2, x: 0, y: 1.32, z: 1.5, limb: LIMB.HEAD, pivot: [0, 1.3, 0.9], shade: SHADE.GLOW },
    // Cresta dorsal.
    { w: 0.08, h: 0.28, d: 0.9, x: 0, y: 1.6, z: 0.1, limb: LIMB.BODY, shade: SHADE.METAL },
    { w: 0.24, h: 0.2, d: 1.3, x: 0, y: 1.15, z: -1.4, limb: LIMB.WING, pivot: [0, 1.2, -0.9], shade: SHADE.TEAM },
    { w: 2.1, h: 0.08, d: 0.9, x: -1.2, y: 1.35, z: -0.1, limb: LIMB.WING, pivot: [-0.2, 1.35, -0.1], shade: SHADE.TEAM },
    { w: 2.1, h: 0.08, d: 0.9, x: 1.2, y: 1.35, z: -0.1, limb: LIMB.WING, pivot: [0.2, 1.35, -0.1], shade: SHADE.TEAM },
    { w: 0.18, h: 0.5, d: 0.18, x: -0.22, y: 0.75, z: 0.3, limb: LIMB.LEG_L, pivot: [-0.22, 1.0, 0.3], shade: SHADE.METAL },
    { w: 0.18, h: 0.5, d: 0.18, x: 0.22, y: 0.75, z: 0.3, limb: LIMB.LEG_R, pivot: [0.22, 1.0, 0.3], shade: SHADE.METAL },
  ]);
}

const BUILDERS: Record<string, (detail: Detail) => THREE.BufferGeometry> = {
  soldier,
  archer,
  mage,
  cavalry,
  giant,
  champion,
  dragon: () => dragon(),
};

/** Silueta de reserva cuando un arquetipo de la config no tiene modelo propio. */
const FALLBACK: Record<string, (detail: Detail) => THREE.BufferGeometry> = {
  humanoid: soldier,
  beast: cavalry,
  dragon: () => dragon(),
};

/**
 * Devuelve la geometría de un arquetipo. Si añades una unidad nueva a la config
 * sin modelo propio, cae en la silueta genérica de su tipo de malla en vez de
 * romperse.
 */
export function createArchetypeGeometry(key: string, mesh: string, detail: Detail): THREE.BufferGeometry {
  const builder = BUILDERS[key] ?? FALLBACK[mesh] ?? soldier;
  return builder(detail);
}

/** Disco plano para la sombra de contacto bajo cada unidad. */
export function createBlobShadowGeometry(): THREE.BufferGeometry {
  const geometry = new THREE.CircleGeometry(0.62, 10);
  geometry.rotateX(-Math.PI / 2);
  geometry.computeBoundingSphere();
  geometry.boundingSphere!.radius = 4;
  return geometry;
}
