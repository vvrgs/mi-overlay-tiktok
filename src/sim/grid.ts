/**
 * Rejilla espacial uniforme (counting sort) para búsqueda de vecinos.
 *
 * Se reconstruye entera cada tick: con decenas de miles de unidades moviéndose,
 * reconstruir es más barato que actualizar celdas individuales, y deja los
 * índices contiguos en memoria (mucho mejor para la caché).
 */

export class SpatialGrid {
  readonly cols: number;
  readonly rows: number;
  readonly cellSize: number;
  private readonly minX: number;
  private readonly minZ: number;
  private counts: Int32Array;
  private starts: Int32Array;
  private cursor: Int32Array;
  items: Int32Array;
  private itemCount = 0;

  constructor(width: number, depth: number, cellSize: number, capacity: number) {
    this.cellSize = cellSize;
    this.minX = -width / 2;
    this.minZ = -depth / 2;
    this.cols = Math.max(1, Math.ceil(width / cellSize) + 2);
    this.rows = Math.max(1, Math.ceil(depth / cellSize) + 2);
    const cells = this.cols * this.rows;
    this.counts = new Int32Array(cells);
    this.starts = new Int32Array(cells + 1);
    this.cursor = new Int32Array(cells);
    this.items = new Int32Array(capacity);
  }

  cellX(x: number): number {
    const cx = Math.floor((x - this.minX) / this.cellSize) + 1;
    return cx < 0 ? 0 : cx >= this.cols ? this.cols - 1 : cx;
  }

  cellZ(z: number): number {
    const cz = Math.floor((z - this.minZ) / this.cellSize) + 1;
    return cz < 0 ? 0 : cz >= this.rows ? this.rows - 1 : cz;
  }

  /**
   * Reconstruye la rejilla con los índices indicados.
   * `members` contiene índices de unidades; `px`/`pz` sus posiciones.
   */
  build(members: Int32Array, memberCount: number, px: Float32Array, pz: Float32Array): void {
    this.counts.fill(0);
    this.itemCount = memberCount;
    if (this.items.length < memberCount) this.items = new Int32Array(memberCount * 2);

    for (let i = 0; i < memberCount; i++) {
      const idx = members[i];
      const cell = this.cellZ(pz[idx]) * this.cols + this.cellX(px[idx]);
      this.counts[cell]++;
    }

    let running = 0;
    for (let c = 0; c < this.counts.length; c++) {
      this.starts[c] = running;
      this.cursor[c] = running;
      running += this.counts[c];
    }
    this.starts[this.counts.length] = running;

    for (let i = 0; i < memberCount; i++) {
      const idx = members[i];
      const cell = this.cellZ(pz[idx]) * this.cols + this.cellX(px[idx]);
      this.items[this.cursor[cell]++] = idx;
    }
  }

  cellStart(cell: number): number {
    return this.starts[cell];
  }

  cellEnd(cell: number): number {
    return this.starts[cell + 1];
  }

  get size(): number {
    return this.itemCount;
  }

  /**
   * Busca el índice más cercano dentro de `maxRadius` alrededor de (x, z),
   * explorando anillos de celdas de dentro hacia fuera y cortando en cuanto
   * el anillo actual ya no puede contener nada mejor.
   */
  findNearest(x: number, z: number, maxRadius: number, px: Float32Array, pz: Float32Array): number {
    if (this.itemCount === 0) return -1;
    const cx = this.cellX(x);
    const cz = this.cellZ(z);
    const maxRing = Math.min(Math.max(this.cols, this.rows), Math.ceil(maxRadius / this.cellSize) + 1);
    let best = -1;
    let bestDist = maxRadius * maxRadius;

    for (let ring = 0; ring <= maxRing; ring++) {
      // Si el borde interno del anillo ya está más lejos que el mejor hallazgo, terminamos.
      if (best >= 0) {
        const ringInner = (ring - 1) * this.cellSize;
        if (ringInner > 0 && ringInner * ringInner > bestDist) break;
      }
      const x0 = cx - ring;
      const x1 = cx + ring;
      const z0 = cz - ring;
      const z1 = cz + ring;
      for (let gz = z0; gz <= z1; gz++) {
        if (gz < 0 || gz >= this.rows) continue;
        const onZEdge = gz === z0 || gz === z1;
        const step = onZEdge ? 1 : x1 - x0 || 1;
        for (let gx = x0; gx <= x1; gx += step) {
          if (gx < 0 || gx >= this.cols) continue;
          const cell = gz * this.cols + gx;
          const end = this.starts[cell + 1];
          for (let i = this.starts[cell]; i < end; i++) {
            const idx = this.items[i];
            const dx = px[idx] - x;
            const dz = pz[idx] - z;
            const d2 = dx * dx + dz * dz;
            if (d2 < bestDist) {
              bestDist = d2;
              best = idx;
            }
          }
        }
      }
    }
    return best;
  }
}
