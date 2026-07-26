/**
 * Constructor de geometría de personajes.
 *
 * Las unidades ya no son cajas: la primitiva base es un **prisma de N lados con
 * sección elíptica y cónica**, con normales suaves alrededor del anillo. Eso es
 * lo que separa un brazo de un ladrillo — una caja tiene cuatro caras planas y
 * aristas de 90° que la luz delata al instante; un prisma de ocho lados que se
 * estrecha hacia la muñeca lee como un brazo aunque tenga los mismos triángulos.
 *
 * Cada vértice lleva cinco atributos además de posición y normal:
 *   - `aLimb`   : qué hueso lo mueve
 *   - `aPivot`  : su articulación (rodilla, codo, cuello...)
 *   - `aPivot2` : la articulación del hueso PADRE (cadera, hombro)
 *   - `aShade`  : de qué está hecho (piel, tela, metal, cuero, brillo)
 *   - `aOcc`    : oclusión ambiental horneada, 1 = expuesto, 0 = escondido
 *
 * El doble pivote es lo que permite cadenas de dos huesos —muslo→pantorrilla,
 * brazo→antebrazo— sin un sistema de esqueleto real: el shader rota primero
 * sobre la articulación propia y después sobre la del padre.
 */

import * as THREE from 'three';

export const LIMB = {
  BODY: 0,
  THIGH_L: 1,
  THIGH_R: 2,
  ARM_L: 3,
  ARM_R: 4,
  HEAD: 5,
  /** Alas, capas y colas: ondean sobre el eje Z. */
  CLOTH: 6,
  /** No se anima (montura, base). */
  STATIC: 7,
  SHIN_L: 8,
  SHIN_R: 9,
  FORE_L: 10,
  FORE_R: 11,
  PELVIS: 12,
} as const;

/** Material de cada pieza. El shader lo traduce a color y reflejo. */
export const SHADE = {
  SKIN: 0,
  TEAM: 1,
  METAL: 2,
  /** Emisivo: brilla y lo recoge el bloom. */
  GLOW: 3,
  /** Madera y cuero: arcos, astas, correas, mangos. */
  LEATHER: 4,
} as const;

export type Detail = 'high' | 'low' | 'simple';

type Vec3 = [number, number, number];
type Radius = [number, number];

interface Bone {
  limb: number;
  pivot: Vec3;
  /** Articulación del hueso padre. Si falta, se usa `pivot` (cadena de un hueso). */
  pivot2?: Vec3;
  shade: number;
}

class Builder {
  positions: number[] = [];
  normals: number[] = [];
  limbs: number[] = [];
  pivots: number[] = [];
  pivots2: number[] = [];
  shades: number[] = [];
  occlusion: number[] = [];
  indices: number[] = [];

  private push(x: number, y: number, z: number, nx: number, ny: number, nz: number, bone: Bone, occ: number): number {
    const index = this.positions.length / 3;
    this.positions.push(x, y, z);
    this.normals.push(nx, ny, nz);
    this.limbs.push(bone.limb);
    this.pivots.push(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
    const parent = bone.pivot2 ?? bone.pivot;
    this.pivots2.push(parent[0], parent[1], parent[2]);
    this.shades.push(bone.shade);
    this.occlusion.push(occ);
    return index;
  }

  /**
   * Prisma cónico de sección elíptica: la primitiva de todo el cuerpo.
   * Las normales laterales se inclinan según la conicidad, así que un miembro
   * que se estrecha refleja la luz como un cono y no como un cilindro.
   */
  prism(
    bone: Bone,
    opts: {
      x?: number;
      y: number;
      z?: number;
      height: number;
      bottom: Radius;
      top: Radius;
      sides?: number;
      occBottom?: number;
      occTop?: number;
      capBottom?: boolean;
      capTop?: boolean;
      /** Desplazamiento del centro del anillo superior: da inclinación al miembro. */
      leanX?: number;
      leanZ?: number;
      /** Gira la sección sobre Y (para orientar un prisma de 4 lados como caja). */
      spin?: number;
    },
  ): void {
    const {
      x = 0,
      y,
      z = 0,
      height,
      bottom,
      top,
      sides = 8,
      occBottom = 1,
      occTop = 1,
      capBottom = true,
      capTop = true,
      leanX = 0,
      leanZ = 0,
      spin = 0,
    } = opts;

    const ringBottom: number[] = [];
    const ringTop: number[] = [];
    // La pendiente media determina cuánto se inclina la normal lateral.
    const slope = (Math.abs(bottom[0] - top[0]) + Math.abs(bottom[1] - top[1])) * 0.5 / Math.max(height, 1e-4);

    for (let i = 0; i < sides; i++) {
      const angle = spin + (i / sides) * Math.PI * 2;
      const cos = Math.cos(angle);
      const sin = Math.sin(angle);
      // Normal radial de una elipse: se escala por el inverso de cada semieje.
      let nx = cos / Math.max(bottom[0], 1e-4);
      let nz = sin / Math.max(bottom[1], 1e-4);
      const inv = 1 / Math.hypot(nx, nz, slope) || 1;
      nx *= inv;
      nz *= inv;
      const ny = slope * inv;

      ringBottom.push(this.push(x + cos * bottom[0], y, z + sin * bottom[1], nx, ny, nz, bone, occBottom));
      ringTop.push(
        this.push(x + leanX + cos * top[0], y + height, z + leanZ + sin * top[1], nx, ny, nz, bone, occTop),
      );
    }

    for (let i = 0; i < sides; i++) {
      const j = (i + 1) % sides;
      this.indices.push(ringBottom[i], ringBottom[j], ringTop[i]);
      this.indices.push(ringTop[i], ringBottom[j], ringTop[j]);
    }

    if (capTop && (top[0] > 1e-4 || top[1] > 1e-4)) {
      const center = this.push(x + leanX, y + height, z + leanZ, 0, 1, 0, bone, occTop);
      const cap: number[] = [];
      for (let i = 0; i < sides; i++) {
        const angle = spin + (i / sides) * Math.PI * 2;
        cap.push(
          this.push(x + leanX + Math.cos(angle) * top[0], y + height, z + leanZ + Math.sin(angle) * top[1], 0, 1, 0, bone, occTop),
        );
      }
      for (let i = 0; i < sides; i++) this.indices.push(center, cap[i], cap[(i + 1) % sides]);
    }

    if (capBottom && (bottom[0] > 1e-4 || bottom[1] > 1e-4)) {
      const center = this.push(x, y, z, 0, -1, 0, bone, occBottom);
      const cap: number[] = [];
      for (let i = 0; i < sides; i++) {
        const angle = spin + (i / sides) * Math.PI * 2;
        cap.push(this.push(x + Math.cos(angle) * bottom[0], y, z + Math.sin(angle) * bottom[1], 0, -1, 0, bone, occBottom));
      }
      for (let i = 0; i < sides; i++) this.indices.push(center, cap[(i + 1) % sides], cap[i]);
    }
  }

  /**
   * Caja cónica de caras planas: para hojas de espada, escudos y tablones,
   * donde una arista viva es exactamente lo que se quiere.
   */
  slab(
    bone: Bone,
    opts: {
      x?: number;
      y: number;
      z?: number;
      height: number;
      bottom: Radius;
      top: Radius;
      occBottom?: number;
      occTop?: number;
      spin?: number;
      leanX?: number;
      leanZ?: number;
      tiltZ?: number;
    },
  ): void {
    const { tiltZ = 0, ...rest } = opts;
    const before = this.positions.length / 3;
    // Un prisma de 4 lados girado 45° es exactamente una caja.
    this.prism(bone, { ...rest, sides: 4, spin: (rest.spin ?? 0) + Math.PI / 4 });
    if (tiltZ !== 0) {
      const cos = Math.cos(tiltZ);
      const sin = Math.sin(tiltZ);
      const px = opts.x ?? 0;
      const py = opts.y;
      for (let i = before; i < this.positions.length / 3; i++) {
        const dx = this.positions[i * 3] - px;
        const dy = this.positions[i * 3 + 1] - py;
        this.positions[i * 3] = px + dx * cos - dy * sin;
        this.positions[i * 3 + 1] = py + dx * sin + dy * cos;
        const nx = this.normals[i * 3];
        const ny = this.normals[i * 3 + 1];
        this.normals[i * 3] = nx * cos - ny * sin;
        this.normals[i * 3 + 1] = nx * sin + ny * cos;
      }
    }
  }

  /** Cúpula: media elipsoide en anillos. Cascos, hombreras, cráneos. */
  dome(bone: Bone, opts: { x?: number; y: number; z?: number; radius: Radius; height: number; sides?: number; rings?: number; occ?: number }): void {
    const { x = 0, y, z = 0, radius, height, sides = 8, rings = 3, occ = 1 } = opts;
    for (let r = 0; r < rings; r++) {
      const t0 = r / rings;
      const t1 = (r + 1) / rings;
      // Perfil de círculo: da la curvatura correcta en lugar de un cono a tramos.
      const s0 = Math.cos((t0 * Math.PI) / 2);
      const s1 = Math.cos((t1 * Math.PI) / 2);
      this.prism(bone, {
        x,
        y: y + t0 * height,
        z,
        height: (t1 - t0) * height,
        bottom: [radius[0] * s0, radius[1] * s0],
        top: [radius[0] * s1, radius[1] * s1],
        sides,
        occBottom: occ,
        occTop: occ,
        capBottom: r === 0,
        capTop: r === rings - 1,
      });
    }
  }

  /**
   * Ejecuta `build` y transforma todo lo que haya añadido dentro.
   *
   * Hace falta porque `prism` solo crece a lo largo de Y: un caballo o el cuerpo
   * de un dragón necesitan el mismo volumen tumbado a lo largo de Z. Rota
   * también los pivotes, que si no dejarían de coincidir con la geometría y las
   * articulaciones girarían por el sitio equivocado.
   */
  part(build: () => void, options: { rotX?: number; move?: Vec3 } = {}): void {
    const start = this.positions.length / 3;
    build();
    const { rotX = 0, move } = options;
    const cos = Math.cos(rotX);
    const sin = Math.sin(rotX);

    for (let i = start; i < this.positions.length / 3; i++) {
      if (rotX !== 0) {
        for (const array of [this.positions, this.pivots, this.pivots2]) {
          const y = array[i * 3 + 1];
          const z = array[i * 3 + 2];
          array[i * 3 + 1] = y * cos - z * sin;
          array[i * 3 + 2] = y * sin + z * cos;
        }
        const ny = this.normals[i * 3 + 1];
        const nz = this.normals[i * 3 + 2];
        this.normals[i * 3 + 1] = ny * cos - nz * sin;
        this.normals[i * 3 + 2] = ny * sin + nz * cos;
      }
      if (move) {
        for (const array of [this.positions, this.pivots, this.pivots2]) {
          array[i * 3] += move[0];
          array[i * 3 + 1] += move[1];
          array[i * 3 + 2] += move[2];
        }
      }
    }
  }

  toGeometry(): THREE.BufferGeometry {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(this.positions, 3));
    geometry.setAttribute('normal', new THREE.Float32BufferAttribute(this.normals, 3));
    geometry.setAttribute('aLimb', new THREE.Float32BufferAttribute(this.limbs, 1));
    geometry.setAttribute('aPivot', new THREE.Float32BufferAttribute(this.pivots, 3));
    geometry.setAttribute('aPivot2', new THREE.Float32BufferAttribute(this.pivots2, 3));
    geometry.setAttribute('aShade', new THREE.Float32BufferAttribute(this.shades, 1));
    geometry.setAttribute('aOcc', new THREE.Float32BufferAttribute(this.occlusion, 1));
    geometry.setIndex(this.indices);
    geometry.computeBoundingSphere();
    // Radio generoso: las instancias se desplazan en el shader, así que el
    // culling por bounding sphere de three.js no puede verlas venir.
    geometry.boundingSphere!.radius = 4;
    return geometry;
  }
}

// ---------------------------------------------------------- proporciones

/**
 * Esqueleto humano de referencia, en unidades de mundo (~1.85 de estatura).
 * Proporciones heroicas: hombros anchos, cintura marcada, piernas largas. Es lo
 * que separa una figura de un muñeco de bloques.
 */
const H = {
  ankle: 0.1,
  knee: 0.5,
  hip: 0.92,
  waist: 1.06,
  chest: 1.34,
  shoulder: 1.5,
  neck: 1.58,
  chin: 1.66,
  crown: 1.88,
  hipX: 0.115,
  shoulderX: 0.235,
  elbow: 1.16,
  wrist: 0.92,
};

// -------------------------------------------------------------- piezas

function legs(b: Builder, shade: number, boots = SHADE.LEATHER, sides = 8): void {
  for (const side of [-1, 1]) {
    const isLeft = side < 0;
    const thigh: Bone = { limb: isLeft ? LIMB.THIGH_L : LIMB.THIGH_R, pivot: [side * H.hipX, H.hip, 0], shade };
    const shin: Bone = {
      limb: isLeft ? LIMB.SHIN_L : LIMB.SHIN_R,
      pivot: [side * H.hipX, H.knee, 0],
      pivot2: [side * H.hipX, H.hip, 0],
      shade,
    };
    const boot: Bone = { ...shin, shade: boots };

    // Muslo: grueso arriba, se estrecha en la rodilla.
    b.prism(thigh, {
      x: side * H.hipX,
      y: H.knee,
      height: H.hip - H.knee,
      bottom: [0.082, 0.09],
      top: [0.115, 0.125],
      sides,
      occBottom: 0.72,
      occTop: 0.55,
      capTop: false,
    });
    // Rótula: una esfera en la articulación. Sin ella, al doblar la rodilla se
    // abre un hueco entre muslo y pantorrilla y se ve el interior del prisma.
    b.dome(shin, { x: side * H.hipX, y: H.knee - 0.052, radius: [0.086, 0.092], height: 0.105, sides, rings: 2, occ: 0.6 });
    // Pantorrilla: vientre del gemelo y tobillo fino.
    b.prism(shin, {
      x: side * H.hipX,
      y: H.ankle,
      height: H.knee - H.ankle,
      bottom: [0.058, 0.062],
      top: [0.088, 0.095],
      sides,
      occBottom: 0.6,
      occTop: 0.8,
      capTop: false,
    });
    // Bota: se extiende hacia adelante formando el pie.
    b.slab(boot, {
      x: side * H.hipX,
      y: 0,
      z: 0.03,
      height: H.ankle,
      bottom: [0.085, 0.15],
      top: [0.072, 0.1],
      occBottom: 0.42,
      occTop: 0.6,
    });
  }
}

function torso(b: Builder, shade: number, sides = 8): void {
  const body: Bone = { limb: LIMB.BODY, pivot: [0, H.waist, 0], shade };
  const pelvis: Bone = { limb: LIMB.PELVIS, pivot: [0, H.hip, 0], shade };

  // Pelvis: une las piernas con la cintura.
  b.prism(pelvis, {
    y: H.hip - 0.04,
    height: H.waist - H.hip + 0.04,
    bottom: [0.155, 0.11],
    top: [0.145, 0.1],
    sides,
    occBottom: 0.62,
    occTop: 0.85,
    capBottom: false,
  });
  // Caja torácica: se ensancha de la cintura al pecho.
  b.prism(body, {
    y: H.waist,
    height: H.chest - H.waist,
    bottom: [0.145, 0.1],
    top: [0.205, 0.132],
    sides,
    occBottom: 0.85,
    occTop: 1,
    capBottom: false,
    capTop: false,
  });
  // Hombros: se cierra de nuevo hacia el cuello.
  b.prism(body, {
    y: H.chest,
    height: H.shoulder - H.chest,
    bottom: [0.205, 0.132],
    top: [0.18, 0.115],
    sides,
    occBottom: 1,
    occTop: 0.9,
    capBottom: false,
  });
}

function head(b: Builder, sides = 8): void {
  const neck: Bone = { limb: LIMB.HEAD, pivot: [0, H.neck - 0.08, 0], shade: SHADE.SKIN };
  b.prism(neck, {
    y: H.shoulder - 0.02,
    height: H.chin - H.shoulder + 0.02,
    bottom: [0.062, 0.058],
    top: [0.072, 0.068],
    sides,
    occBottom: 0.45,
    occTop: 0.75,
    capBottom: false,
    capTop: false,
  });
  // Cráneo: mandíbula estrecha, sienes anchas, coronilla redondeada.
  b.prism(neck, {
    y: H.chin,
    height: 0.1,
    bottom: [0.078, 0.085],
    top: [0.098, 0.105],
    sides,
    occBottom: 0.8,
    occTop: 1,
    capBottom: false,
    capTop: false,
  });
  b.dome(neck, { y: H.chin + 0.1, radius: [0.098, 0.105], height: H.crown - H.chin - 0.1, sides, rings: 2 });
}

/** Brazo completo con hombrera, antebrazo y guantelete. */
function arm(b: Builder, side: number, shade: number, sides = 8, pauldron = true): void {
  const isLeft = side < 0;
  const shoulderPivot: Vec3 = [side * H.shoulderX, H.shoulder - 0.04, 0];
  const upper: Bone = { limb: isLeft ? LIMB.ARM_L : LIMB.ARM_R, pivot: shoulderPivot, shade };
  const fore: Bone = {
    limb: isLeft ? LIMB.FORE_L : LIMB.FORE_R,
    pivot: [side * H.shoulderX, H.elbow, 0],
    pivot2: shoulderPivot,
    shade,
  };
  const glove: Bone = { ...fore, shade: SHADE.LEATHER };

  if (pauldron) {
    // Hombrera: el volumen que da presencia a la silueta desde arriba.
    b.dome(upper, {
      x: side * (H.shoulderX + 0.012),
      y: H.shoulder - 0.055,
      radius: [0.098, 0.1],
      height: 0.1,
      sides,
      rings: 2,
      occ: 1,
    });
  }
  b.prism(upper, {
    x: side * H.shoulderX,
    y: H.elbow,
    height: H.shoulder - 0.04 - H.elbow,
    bottom: [0.052, 0.055],
    top: [0.072, 0.075],
    sides,
    occBottom: 0.7,
    occTop: 0.85,
    capTop: false,
  });
  // Codo: misma solución que la rodilla.
  b.dome(fore, { x: side * H.shoulderX, y: H.elbow - 0.045, radius: [0.055, 0.058], height: 0.075, sides: 6, rings: 2, occ: 0.62 });
  b.prism(fore, {
    x: side * H.shoulderX,
    y: H.wrist,
    height: H.elbow - H.wrist,
    bottom: [0.045, 0.048],
    top: [0.056, 0.06],
    sides,
    occBottom: 0.72,
    occTop: 0.8,
    capTop: false,
  });
  b.prism(glove, {
    x: side * H.shoulderX,
    y: H.wrist - 0.1,
    height: 0.1,
    bottom: [0.048, 0.05],
    top: [0.056, 0.058],
    sides: 6,
    occBottom: 0.6,
    occTop: 0.72,
  });
}

/** Casco con nasal y cimera opcional. */
function helmet(b: Builder, sides = 8, crest = 0): void {
  const bone: Bone = { limb: LIMB.HEAD, pivot: [0, H.neck - 0.08, 0], shade: SHADE.METAL };
  b.prism(bone, {
    y: H.chin + 0.06,
    height: 0.09,
    bottom: [0.104, 0.111],
    top: [0.108, 0.115],
    sides,
    occBottom: 0.85,
    occTop: 1,
    capBottom: false,
    capTop: false,
  });
  b.dome(bone, { y: H.chin + 0.15, radius: [0.108, 0.115], height: H.crown - H.chin - 0.13, sides, rings: 2 });
  // Nasal: la pieza que hace que se lea "casco" y no "gorro".
  b.slab(bone, { y: H.chin + 0.02, z: 0.1, height: 0.12, bottom: [0.022, 0.02], top: [0.026, 0.02], occBottom: 0.7, occTop: 0.95 });
  if (crest > 0) {
    b.slab({ ...bone, shade: SHADE.GLOW }, {
      y: H.crown - 0.02,
      height: crest,
      bottom: [0.016, 0.075],
      top: [0.01, 0.045],
      occBottom: 1,
      occTop: 1,
    });
  }
}

// ---------------------------------------------------------- arquetipos

function soldier(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  legs(b, SHADE.TEAM, SHADE.LEATHER, sides);
  torso(b, SHADE.TEAM, sides);
  head(b, sides);

  if (detail === 'simple') return b.toGeometry();

  arm(b, -1, SHADE.TEAM, sides, detail === 'high');
  arm(b, 1, SHADE.TEAM, sides, detail === 'high');
  helmet(b, sides);

  const right: Bone = {
    limb: LIMB.FORE_R,
    pivot: [H.shoulderX, H.elbow, 0],
    pivot2: [H.shoulderX, H.shoulder - 0.04, 0],
    shade: SHADE.METAL,
  };
  const left: Bone = {
    limb: LIMB.FORE_L,
    pivot: [-H.shoulderX, H.elbow, 0],
    pivot2: [-H.shoulderX, H.shoulder - 0.04, 0],
    shade: SHADE.TEAM,
  };

  // Espada: empuñadura, guarda y hoja que se afila hacia la punta.
  b.slab({ ...right, shade: SHADE.LEATHER }, { x: H.shoulderX, y: H.wrist - 0.16, z: 0.08, height: 0.16, bottom: [0.022, 0.022], top: [0.026, 0.026], occBottom: 0.7, occTop: 0.8 });
  b.slab(right, { x: H.shoulderX, y: H.wrist, z: 0.08, height: 0.035, bottom: [0.085, 0.03], top: [0.085, 0.03], occBottom: 0.9, occTop: 0.9 });
  b.slab(right, { x: H.shoulderX, y: H.wrist + 0.035, z: 0.08, height: 0.62, bottom: [0.032, 0.014], top: [0.008, 0.006], occBottom: 0.95, occTop: 1 });

  // Escudo: cara ligeramente convexa y umbo metálico en el centro.
  b.slab(left, { x: -H.shoulderX - 0.08, y: H.wrist - 0.16, z: 0.02, height: 0.46, bottom: [0.028, 0.16], top: [0.03, 0.19], occBottom: 0.75, occTop: 0.95 });
  b.slab(left, { x: -H.shoulderX - 0.08, y: H.wrist + 0.3, z: 0.02, height: 0.16, bottom: [0.03, 0.19], top: [0.02, 0.13], occBottom: 0.95, occTop: 1 });
  b.dome({ ...left, shade: SHADE.METAL }, { x: -H.shoulderX - 0.105, y: H.wrist + 0.14, z: 0.02, radius: [0.05, 0.05], height: 0.05, sides: 6, rings: 1 });
  return b.toGeometry();
}

function archer(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  legs(b, SHADE.LEATHER, SHADE.LEATHER, sides);
  torso(b, SHADE.TEAM, sides);
  head(b, sides);
  if (detail === 'simple') return b.toGeometry();

  arm(b, -1, SHADE.TEAM, sides, false);
  arm(b, 1, SHADE.TEAM, sides, false);

  // Capucha: cubre la coronilla y cae por la nuca.
  const hood: Bone = { limb: LIMB.HEAD, pivot: [0, H.neck - 0.08, 0], shade: SHADE.TEAM };
  b.dome(hood, { y: H.chin + 0.04, z: -0.012, radius: [0.115, 0.125], height: H.crown - H.chin, sides, rings: 2 });
  b.slab(hood, { y: H.chin - 0.12, z: -0.09, height: 0.2, bottom: [0.1, 0.05], top: [0.115, 0.06], occBottom: 0.5, occTop: 0.8 });

  const left: Bone = {
    limb: LIMB.FORE_L,
    pivot: [-H.shoulderX, H.elbow, 0],
    pivot2: [-H.shoulderX, H.shoulder - 0.04, 0],
    shade: SHADE.LEATHER,
  };
  // Arco: cuatro tramos inclinados que describen la curva completa.
  const bowX = -H.shoulderX - 0.09;
  b.slab(left, { x: bowX, y: H.wrist + 0.02, z: 0.1, height: 0.34, bottom: [0.014, 0.014], top: [0.012, 0.012], tiltZ: -0.22, occBottom: 1, occTop: 1 });
  b.slab(left, { x: bowX + 0.035, y: H.wrist + 0.34, z: 0.1, height: 0.3, bottom: [0.012, 0.012], top: [0.008, 0.008], tiltZ: -0.55, occBottom: 1, occTop: 1 });
  b.slab(left, { x: bowX, y: H.wrist - 0.32, z: 0.1, height: 0.34, bottom: [0.012, 0.012], top: [0.014, 0.014], tiltZ: 0.22, occBottom: 1, occTop: 1 });
  b.slab(left, { x: bowX + 0.035, y: H.wrist - 0.62, z: 0.1, height: 0.3, bottom: [0.008, 0.008], top: [0.012, 0.012], tiltZ: 0.55, occBottom: 1, occTop: 1 });

  // Carcaj cruzado a la espalda con flechas asomando.
  const back: Bone = { limb: LIMB.BODY, pivot: [0, H.waist, 0], shade: SHADE.LEATHER };
  b.prism(back, { x: 0.11, y: H.waist - 0.02, z: -0.14, height: 0.36, bottom: [0.05, 0.05], top: [0.055, 0.055], sides: 6, leanX: 0.05, occBottom: 0.5, occTop: 0.8 });
  b.slab({ ...back, shade: SHADE.METAL }, { x: 0.17, y: H.waist + 0.34, z: -0.14, height: 0.16, bottom: [0.012, 0.012], top: [0.01, 0.01], occBottom: 0.9, occTop: 1 });
  return b.toGeometry();
}

function mage(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  const robe: Bone = { limb: LIMB.BODY, pivot: [0, H.waist, 0], shade: SHADE.TEAM };

  // Túnica: se abre hacia el suelo y oculta las piernas.
  b.prism(robe, { y: 0.02, height: H.hip - 0.02, bottom: [0.26, 0.2], top: [0.17, 0.13], sides, occBottom: 0.35, occTop: 0.7 });
  b.prism(robe, { y: H.hip, height: H.chest - H.hip, bottom: [0.17, 0.13], top: [0.19, 0.13], sides, occBottom: 0.7, occTop: 0.95, capBottom: false, capTop: false });
  b.prism(robe, { y: H.chest, height: H.shoulder - H.chest, bottom: [0.19, 0.13], top: [0.165, 0.11], sides, occBottom: 0.95, occTop: 0.9, capBottom: false });
  head(b, sides);
  if (detail === 'simple') return b.toGeometry();

  arm(b, -1, SHADE.TEAM, sides, false);
  arm(b, 1, SHADE.TEAM, sides, false);

  // Sombrero cónico: ala ancha y punta afilada.
  const hat: Bone = { limb: LIMB.HEAD, pivot: [0, H.neck - 0.08, 0], shade: SHADE.TEAM };
  b.prism(hat, { y: H.crown - 0.06, height: 0.03, bottom: [0.24, 0.24], top: [0.235, 0.235], sides, occBottom: 0.6, occTop: 1 });
  b.prism(hat, { y: H.crown - 0.03, height: 0.34, bottom: [0.115, 0.115], top: [0.028, 0.028], sides, leanZ: 0.05, occBottom: 1, occTop: 1, capBottom: false });

  // Báculo con orbe encendido.
  const right: Bone = {
    limb: LIMB.FORE_R,
    pivot: [H.shoulderX, H.elbow, 0],
    pivot2: [H.shoulderX, H.shoulder - 0.04, 0],
    shade: SHADE.LEATHER,
  };
  b.prism(right, { x: H.shoulderX + 0.06, y: H.wrist - 0.7, z: 0.08, height: 1.5, bottom: [0.022, 0.022], top: [0.018, 0.018], sides: 6, occBottom: 0.7, occTop: 1 });
  b.dome({ ...right, shade: SHADE.GLOW }, { x: H.shoulderX + 0.06, y: H.wrist + 0.76, z: 0.08, radius: [0.075, 0.075], height: 0.12, sides, rings: 2 });
  return b.toGeometry();
}

function champion(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  legs(b, SHADE.METAL, SHADE.LEATHER, sides);
  torso(b, SHADE.TEAM, sides);
  head(b, sides);
  if (detail === 'simple') return b.toGeometry();

  arm(b, -1, SHADE.METAL, sides, true);
  arm(b, 1, SHADE.METAL, sides, true);
  helmet(b, sides, 0.16);

  // Peto sobre la túnica: distingue al campeón del soldado raso.
  const body: Bone = { limb: LIMB.BODY, pivot: [0, H.waist, 0], shade: SHADE.METAL };
  b.prism(body, { y: H.waist + 0.04, height: H.chest - H.waist, bottom: [0.155, 0.112], top: [0.215, 0.142], sides, occBottom: 0.9, occTop: 1, capBottom: false, capTop: false });

  // Capa: cae desde los hombros y ondea al correr.
  const cape: Bone = { limb: LIMB.CLOTH, pivot: [0, H.shoulder, -0.1], shade: SHADE.TEAM };
  b.slab(cape, { y: H.hip - 0.3, z: -0.15, height: H.shoulder - H.hip + 0.34, bottom: [0.22, 0.02], top: [0.19, 0.02], occBottom: 0.55, occTop: 0.9 });

  // Espadón.
  const right: Bone = {
    limb: LIMB.FORE_R,
    pivot: [H.shoulderX, H.elbow, 0],
    pivot2: [H.shoulderX, H.shoulder - 0.04, 0],
    shade: SHADE.METAL,
  };
  b.slab({ ...right, shade: SHADE.LEATHER }, { x: H.shoulderX, y: H.wrist - 0.2, z: 0.08, height: 0.2, bottom: [0.026, 0.026], top: [0.03, 0.03], occBottom: 0.7, occTop: 0.8 });
  b.slab(right, { x: H.shoulderX, y: H.wrist, z: 0.08, height: 0.04, bottom: [0.115, 0.034], top: [0.115, 0.034], occBottom: 0.9, occTop: 0.9 });
  b.slab(right, { x: H.shoulderX, y: H.wrist + 0.04, z: 0.08, height: 0.86, bottom: [0.04, 0.017], top: [0.01, 0.007], occBottom: 0.95, occTop: 1 });
  return b.toGeometry();
}

function giant(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  // Mismo esqueleto, proporciones de bruto: tronco enorme y piernas cortas.
  for (const side of [-1, 1]) {
    const isLeft = side < 0;
    const thigh: Bone = { limb: isLeft ? LIMB.THIGH_L : LIMB.THIGH_R, pivot: [side * 0.17, 0.86, 0], shade: SHADE.TEAM };
    const shin: Bone = { limb: isLeft ? LIMB.SHIN_L : LIMB.SHIN_R, pivot: [side * 0.17, 0.46, 0], pivot2: [side * 0.17, 0.86, 0], shade: SHADE.TEAM };
    b.prism(thigh, { x: side * 0.17, y: 0.46, height: 0.4, bottom: [0.12, 0.13], top: [0.165, 0.175], sides, occBottom: 0.7, occTop: 0.5, capTop: false });
    b.dome(shin, { x: side * 0.17, y: 0.4, radius: [0.13, 0.14], height: 0.15, sides, rings: 2, occ: 0.6 });
    b.prism(shin, { x: side * 0.17, y: 0.06, height: 0.4, bottom: [0.095, 0.1], top: [0.125, 0.135], sides, occBottom: 0.6, occTop: 0.8, capTop: false });
    b.slab({ ...shin, shade: SHADE.LEATHER }, { x: side * 0.17, y: 0, z: 0.04, height: 0.08, bottom: [0.13, 0.2], top: [0.11, 0.15], occBottom: 0.4, occTop: 0.6 });
  }

  const body: Bone = { limb: LIMB.BODY, pivot: [0, 1.0, 0], shade: SHADE.SKIN };
  b.prism(body, { y: 0.86, height: 0.24, bottom: [0.24, 0.17], top: [0.25, 0.18], sides, occBottom: 0.6, occTop: 0.85 });
  b.prism(body, { y: 1.1, height: 0.42, bottom: [0.25, 0.18], top: [0.33, 0.22], sides, occBottom: 0.85, occTop: 1, capBottom: false, capTop: false });
  b.prism(body, { y: 1.52, height: 0.16, bottom: [0.33, 0.22], top: [0.28, 0.19], sides, occBottom: 1, occTop: 0.9, capBottom: false });

  const headBone: Bone = { limb: LIMB.HEAD, pivot: [0, 1.62, 0], shade: SHADE.SKIN };
  b.prism(headBone, { y: 1.66, height: 0.14, bottom: [0.115, 0.12], top: [0.15, 0.155], sides, occBottom: 0.7, occTop: 1, capBottom: false, capTop: false });
  b.dome(headBone, { y: 1.8, radius: [0.15, 0.155], height: 0.16, sides, rings: 2 });
  if (detail === 'simple') return b.toGeometry();

  for (const side of [-1, 1]) {
    const isLeft = side < 0;
    const shoulderPivot: Vec3 = [side * 0.36, 1.5, 0];
    const upper: Bone = { limb: isLeft ? LIMB.ARM_L : LIMB.ARM_R, pivot: shoulderPivot, shade: SHADE.SKIN };
    const fore: Bone = { limb: isLeft ? LIMB.FORE_L : LIMB.FORE_R, pivot: [side * 0.36, 1.12, 0], pivot2: shoulderPivot, shade: SHADE.SKIN };
    b.dome({ ...upper, shade: SHADE.METAL }, { x: side * 0.37, y: 1.46, radius: [0.16, 0.165], height: 0.16, sides, rings: 2 });
    b.prism(upper, { x: side * 0.36, y: 1.12, height: 0.38, bottom: [0.085, 0.09], top: [0.115, 0.12], sides, occBottom: 0.7, occTop: 0.85, capTop: false });
    b.dome(fore, { x: side * 0.36, y: 1.06, radius: [0.098, 0.104], height: 0.12, sides, rings: 2, occ: 0.62 });
    b.prism(fore, { x: side * 0.36, y: 0.78, height: 0.34, bottom: [0.078, 0.082], top: [0.095, 0.1], sides, occBottom: 0.72, occTop: 0.8, capTop: false });
  }

  // Maza: mango largo con la cabeza ARRIBA, no arrastrando por el suelo.
  const right: Bone = { limb: LIMB.FORE_R, pivot: [0.36, 1.12, 0], pivot2: [0.36, 1.5, 0], shade: SHADE.LEATHER };
  b.prism(right, { x: 0.36, y: 0.62, z: 0.18, height: 1.0, bottom: [0.036, 0.036], top: [0.044, 0.044], sides: 6, occBottom: 0.6, occTop: 0.9 });
  b.prism({ ...right, shade: SHADE.METAL }, { x: 0.36, y: 1.6, z: 0.18, height: 0.26, bottom: [0.1, 0.1], top: [0.115, 0.115], sides, occBottom: 0.85, occTop: 1 });
  // Pinchos radiales: sin ellos la cabeza se lee como un cubo.
  for (let i = 0; i < 6; i++) {
    const angle = (i / 6) * Math.PI * 2;
    b.prism({ ...right, shade: SHADE.METAL }, {
      x: 0.36 + Math.cos(angle) * 0.11,
      y: 1.68,
      z: 0.18 + Math.sin(angle) * 0.11,
      height: 0.1,
      bottom: [0.035, 0.035],
      top: [0.008, 0.008],
      sides: 4,
      occBottom: 1,
      occTop: 1,
    });
  }
  return b.toGeometry();
}

function cavalry(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  const horse: Bone = { limb: LIMB.STATIC, pivot: [0, 0.95, 0], shade: SHADE.SKIN };

  // Barril del caballo: se construye como prisma vertical y se TUMBA sobre Z.
  // La primitiva solo crece en Y; sin este giro el caballo sería un tonel de pie.
  b.part(
    () => {
      b.prism(horse, { y: 0, height: 0.72, bottom: [0.27, 0.3], top: [0.3, 0.34], sides, occBottom: 0.85, occTop: 1, capBottom: false });
      b.prism(horse, { y: 0.72, height: 0.62, bottom: [0.3, 0.34], top: [0.24, 0.27], sides, occBottom: 1, occTop: 0.9, capBottom: false });
      b.prism(horse, { y: -0.62, height: 0.62, bottom: [0.2, 0.22], top: [0.27, 0.3], sides, occBottom: 0.8, occTop: 0.9, capTop: false });
    },
    { rotX: Math.PI / 2, move: [0, 1.02, -0.1] },
  );

  // Cuello: sube hacia adelante desde la cruz.
  b.prism(horse, { y: 1.16, z: 0.52, height: 0.42, bottom: [0.13, 0.16], top: [0.09, 0.11], sides, leanZ: 0.22, occBottom: 0.9, occTop: 1 });
  // Cabeza alargada, tumbada igual que el cuerpo.
  b.part(
    () => b.prism(horse, { y: 0, height: 0.34, bottom: [0.075, 0.085], top: [0.06, 0.07], sides: 6, occBottom: 1, occTop: 1 }),
    { rotX: Math.PI / 2.6, move: [0, 1.58, 0.72] },
  );
  // Orejas.
  for (const side of [-1, 1]) {
    b.prism(horse, { x: side * 0.05, y: 1.66, z: 0.66, height: 0.09, bottom: [0.022, 0.022], top: [0.004, 0.004], sides: 4, occBottom: 1, occTop: 1 });
  }
  // Crin y cola.
  b.slab({ ...horse, shade: SHADE.LEATHER }, { y: 1.3, z: 0.42, height: 0.1, bottom: [0.03, 0.3], top: [0.026, 0.26], occBottom: 1, occTop: 1 });
  b.part(
    () => b.prism({ ...horse, shade: SHADE.LEATHER }, { y: 0, height: 0.5, bottom: [0.03, 0.03], top: [0.075, 0.075], sides: 6, occBottom: 0.7, occTop: 0.9 }),
    { rotX: -Math.PI / 2.4, move: [0, 1.06, -0.78] },
  );

  // Patas: dos huesos como en un bípedo, alternando la fase para que trote.
  for (const [sx, sz, thighLimb, shinLimb] of [
    [-1, 0.42, LIMB.THIGH_L, LIMB.SHIN_L],
    [1, 0.42, LIMB.THIGH_R, LIMB.SHIN_R],
    [-1, -0.5, LIMB.THIGH_R, LIMB.SHIN_R],
    [1, -0.5, LIMB.THIGH_L, LIMB.SHIN_L],
  ] as Array<[number, number, number, number]>) {
    const hip: Vec3 = [sx * 0.19, 0.84, sz];
    const thigh: Bone = { limb: thighLimb, pivot: hip, shade: SHADE.SKIN };
    const shin: Bone = { limb: shinLimb, pivot: [sx * 0.19, 0.44, sz], pivot2: hip, shade: SHADE.SKIN };
    b.prism(thigh, { x: sx * 0.19, y: 0.44, z: sz, height: 0.4, bottom: [0.055, 0.058], top: [0.095, 0.105], sides: 6, occBottom: 0.65, occTop: 0.8, capTop: false });
    b.prism(shin, { x: sx * 0.19, y: 0.08, z: sz, height: 0.36, bottom: [0.04, 0.042], top: [0.055, 0.058], sides: 6, occBottom: 0.5, occTop: 0.75, capTop: false });
    b.prism({ ...shin, shade: SHADE.LEATHER }, { x: sx * 0.19, y: 0, z: sz, height: 0.08, bottom: [0.055, 0.06], top: [0.048, 0.052], sides: 6, occBottom: 0.35, occTop: 0.55 });
  }

  // Gualdrapa: la tela del equipo, lo que identifica el bando desde lejos.
  b.slab({ ...horse, shade: SHADE.TEAM }, { y: 0.66, z: -0.1, height: 0.44, bottom: [0.3, 0.44], top: [0.31, 0.46], occBottom: 0.5, occTop: 0.9 });
  if (detail === 'simple') return b.toGeometry();

  // Silla y jinete erguido.
  b.slab({ ...horse, shade: SHADE.LEATHER }, { y: 1.32, z: -0.06, height: 0.07, bottom: [0.22, 0.26], top: [0.2, 0.24], occBottom: 0.9, occTop: 1 });
  const rider: Bone = { limb: LIMB.BODY, pivot: [0, 1.52, -0.06], shade: SHADE.TEAM };
  b.prism(rider, { y: 1.38, z: -0.06, height: 0.34, bottom: [0.135, 0.105], top: [0.19, 0.128], sides, occBottom: 0.6, occTop: 1 });
  b.prism(rider, { y: 1.72, z: -0.06, height: 0.15, bottom: [0.19, 0.128], top: [0.165, 0.11], sides, occBottom: 1, occTop: 0.9, capBottom: false });
  // Muslos del jinete, abiertos sobre la montura.
  for (const side of [-1, 1]) {
    b.prism({ limb: LIMB.STATIC, pivot: [0, 1.4, 0], shade: SHADE.METAL }, {
      x: side * 0.17, y: 1.14, z: 0.04, height: 0.3, bottom: [0.06, 0.07], top: [0.085, 0.1], sides: 6, occBottom: 0.5, occTop: 0.75,
    });
  }

  const riderHead: Bone = { limb: LIMB.HEAD, pivot: [0, 1.84, -0.06], shade: SHADE.SKIN };
  b.prism(riderHead, { y: 1.87, z: -0.06, height: 0.1, bottom: [0.07, 0.076], top: [0.09, 0.096], sides, occBottom: 0.8, occTop: 1, capBottom: false, capTop: false });
  b.dome({ ...riderHead, shade: SHADE.METAL }, { y: 1.97, z: -0.06, radius: [0.098, 0.104], height: 0.14, sides, rings: 2 });

  // Lanza calada hacia adelante: tumbada sobre Z, como el cuerpo.
  const lance: Bone = { limb: LIMB.ARM_R, pivot: [0.21, 1.62, -0.06], shade: SHADE.LEATHER };
  b.part(
    () => {
      b.prism(lance, { y: 0, height: 2.0, bottom: [0.026, 0.026], top: [0.02, 0.02], sides: 6, occBottom: 0.85, occTop: 1 });
      b.slab({ ...lance, shade: SHADE.METAL }, { y: 2.0, height: 0.26, bottom: [0.05, 0.05], top: [0.006, 0.006], occBottom: 1, occTop: 1 });
    },
    { rotX: Math.PI / 2, move: [0.21, 1.56, -0.7] },
  );
  return b.toGeometry();
}

function dragon(detail: Detail): THREE.BufferGeometry {
  const b = new Builder();
  const sides = detail === 'high' ? 8 : 5;
  const body: Bone = { limb: LIMB.BODY, pivot: [0, 1.2, 0], shade: SHADE.TEAM };

  // Cuerpo fusiforme tumbado sobre Z: ancho en el pecho y afilado hacia la cola.
  b.part(
    () => {
      b.prism(body, { y: 0, height: 0.8, bottom: [0.17, 0.18], top: [0.27, 0.28], sides, occBottom: 0.85, occTop: 1 });
      b.prism(body, { y: 0.8, height: 0.7, bottom: [0.27, 0.28], top: [0.17, 0.17], sides, occBottom: 1, occTop: 0.9, capBottom: false });
    },
    { rotX: Math.PI / 2, move: [0, 1.2, -0.75] },
  );

  // Cuello y cabeza, inclinados hacia arriba y adelante.
  b.part(
    () => b.prism(body, { y: 0, height: 0.55, bottom: [0.17, 0.17], top: [0.115, 0.12], sides, occBottom: 1, occTop: 1 }),
    { rotX: Math.PI / 2.5, move: [0, 1.3, 0.72] },
  );
  b.part(
    () => {
      b.prism(body, { y: 0, height: 0.42, bottom: [0.12, 0.115], top: [0.085, 0.075], sides, occBottom: 1, occTop: 1 });
      b.prism({ ...body, shade: SHADE.GLOW }, { y: 0.42, height: 0.05, bottom: [0.06, 0.05], top: [0.03, 0.026], sides: 6, occBottom: 1, occTop: 1 });
    },
    { rotX: Math.PI / 2.15, move: [0, 1.52, 1.06] },
  );
  // Cuernos.
  for (const side of [-1, 1]) {
    b.prism({ ...body, shade: SHADE.METAL }, { x: side * 0.07, y: 1.62, z: 1.08, height: 0.2, bottom: [0.022, 0.022], top: [0.004, 0.004], sides: 4, leanZ: -0.08, occBottom: 1, occTop: 1 });
  }

  // Cola: tres tramos que se afilan, animados como tela.
  const tail: Bone = { limb: LIMB.CLOTH, pivot: [0, 1.2, -0.75], shade: SHADE.TEAM };
  b.part(
    () => {
      b.prism(tail, { y: 0, height: 0.7, bottom: [0.13, 0.13], top: [0.19, 0.185], sides: 6, occBottom: 0.9, occTop: 1, capTop: false });
      b.prism(tail, { y: -0.7, height: 0.7, bottom: [0.03, 0.03], top: [0.13, 0.13], sides: 6, occBottom: 0.9, occTop: 1, capTop: false });
    },
    { rotX: -Math.PI / 2, move: [0, 1.22, -0.72] },
  );

  if (detail !== 'simple') {
    // Cresta dorsal: una hilera de púas que quiebra la silueta.
    for (let i = 0; i < 6; i++) {
      b.slab({ ...body, shade: SHADE.METAL }, {
        y: 1.44 + Math.sin((i / 5) * Math.PI) * 0.1,
        z: 0.55 - i * 0.3,
        height: 0.14 + Math.sin((i / 5) * Math.PI) * 0.08,
        bottom: [0.02, 0.09],
        top: [0.005, 0.04],
        occBottom: 1,
        occTop: 1,
      });
    }
    // Garras replegadas bajo el cuerpo.
    for (const side of [-1, 1]) {
      const leg: Bone = { limb: side < 0 ? LIMB.THIGH_L : LIMB.THIGH_R, pivot: [side * 0.2, 1.05, 0.3], shade: SHADE.METAL };
      b.prism(leg, { x: side * 0.22, y: 0.72, z: 0.34, height: 0.34, bottom: [0.045, 0.045], top: [0.08, 0.085], sides: 6, occBottom: 0.55, occTop: 0.85 });
      b.slab({ ...leg, shade: SHADE.METAL }, { x: side * 0.22, y: 0.66, z: 0.42, height: 0.06, bottom: [0.05, 0.08], top: [0.04, 0.06], occBottom: 0.5, occTop: 0.7 });
    }
  }

  // Alas. Dos detalles las separan de una tabla plana: **diedro** —la punta va
  // más alta que la raíz, como en cualquier ala real— y una membrana que se
  // estrecha y retrasa hacia afuera en lugar de ser un rectángulo.
  for (const side of [-1, 1]) {
    const wing: Bone = { limb: LIMB.CLOTH, pivot: [side * 0.22, 1.34, -0.1], shade: SHADE.TEAM };

    // Húmero y antebrazo del ala: el borde de ataque, grueso y algo elevado.
    b.prism({ ...wing, shade: SHADE.METAL }, { x: side * 0.7, y: 1.36, z: 0.16, height: 0.07, bottom: [0.62, 0.05], top: [0.6, 0.045], sides: 4, spin: Math.PI / 4, occBottom: 1, occTop: 1 });
    b.prism({ ...wing, shade: SHADE.METAL }, { x: side * 1.75, y: 1.62, z: 0.0, height: 0.06, bottom: [0.5, 0.045], top: [0.48, 0.04], sides: 4, spin: Math.PI / 4, occBottom: 1, occTop: 1 });

    // Dedos alares: las varillas que tensan la membrana y se abren en abanico.
    for (let i = 0; i < 3; i++) {
      const t = i / 2;
      b.prism({ ...wing, shade: SHADE.METAL }, {
        x: side * (1.6 - t * 0.35),
        y: 1.5 - t * 0.06,
        z: -0.35 - t * 0.42,
        height: 0.05,
        bottom: [0.5 + t * 0.22, 0.04],
        top: [0.48 + t * 0.2, 0.035],
        sides: 4,
        spin: Math.PI / 4,
        occBottom: 0.9,
        occTop: 1,
      });
    }

    // Membrana en tres paños, cada uno más alto y más retrasado que el anterior.
    b.slab(wing, { x: side * 0.72, y: 1.3, z: -0.24, height: 0.03, bottom: [0.52, 0.44], top: [0.5, 0.42], occBottom: 0.75, occTop: 0.95 });
    b.slab(wing, { x: side * 1.5, y: 1.44, z: -0.5, height: 0.028, bottom: [0.46, 0.5], top: [0.45, 0.48], occBottom: 0.8, occTop: 1 });
    b.slab(wing, { x: side * 2.15, y: 1.6, z: -0.72, height: 0.026, bottom: [0.3, 0.38], top: [0.29, 0.36], occBottom: 0.85, occTop: 1 });
  }
  return b.toGeometry();
}

const BUILDERS: Record<string, (detail: Detail) => THREE.BufferGeometry> = {
  soldier,
  archer,
  mage,
  cavalry,
  giant,
  champion,
  dragon,
};

/** Silueta de reserva cuando un arquetipo de la config no tiene modelo propio. */
const FALLBACK: Record<string, (detail: Detail) => THREE.BufferGeometry> = {
  humanoid: soldier,
  beast: cavalry,
  dragon,
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
  const geometry = new THREE.CircleGeometry(0.42, 12);
  geometry.rotateX(-Math.PI / 2);
  geometry.computeBoundingSphere();
  geometry.boundingSphere!.radius = 4;
  return geometry;
}
