#!/usr/bin/env python3
"""
gen_particles.py — Genera los sprite sheets de particulas custom de irontempest.

Un PNG RGBA de 32x32 por frame en:
  src/main/resources/assets/irontempest/textures/particle/

Tecnica: renderizado procedural en float a 128x128 (supersampling 4x) con
campos radiales + value noise fBm casero (rejilla aleatoria interpolada
bilinealmente, 2-3 octavas), rampas de color con gamma, y downsample por
promedio de area (antialiasing implicito). Nada de pixel-art plano.

Sprites aditivos (fireball, flash, spark, tracer, muzzle_flash, charge_mote,
warp_flash, ember): nucleo casi blanco, halo saturado, caida suave a alpha 0.
Sprites alpha-blend (smoke, debris, shockwave): shading interno.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "irontempest",
                   "textures", "particle")
os.makedirs(OUT, exist_ok=True)

# ---------------------------------------------------------------- lienzo
SS = 4                 # supersampling
N = 32                 # tamano final
H = N * SS             # tamano de trabajo

_ys, _xs = np.mgrid[0:H, 0:H]
X = (_xs + 0.5) / H * 2.0 - 1.0        # -1..1, +x derecha
Y = (_ys + 0.5) / H * 2.0 - 1.0        # -1..1, +y abajo (fila 0 = arriba)
R = np.hypot(X, Y)
TH = np.arctan2(Y, X)


def smoothstep(e0, e1, x):
    """Funciona tambien con e0 > e1 (rampa descendente)."""
    t = np.clip((x - e0) / (e1 - e0 + 1e-12), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def angdiff(a, b):
    return (a - b + np.pi) % (2.0 * np.pi) - np.pi


# ---------------------------------------------------------------- value noise
class NoiseField:
    """Rejilla aleatoria con muestreo bilineal suavizado y wraparound."""

    def __init__(self, seed, size=64):
        rng = np.random.RandomState(seed)
        self.g = rng.rand(size, size)
        self.size = size

    def sample(self, x, y):
        s = self.size
        x = np.mod(x, s)
        y = np.mod(y, s)
        x0 = np.floor(x).astype(np.int64)
        y0 = np.floor(y).astype(np.int64)
        x1 = (x0 + 1) % s
        y1 = (y0 + 1) % s
        fx = x - x0
        fy = y - y0
        fx = fx * fx * (3.0 - 2.0 * fx)
        fy = fy * fy * (3.0 - 2.0 * fy)
        g = self.g
        v00 = g[y0, x0]
        v10 = g[y0, x1]
        v01 = g[y1, x0]
        v11 = g[y1, x1]
        return (v00 * (1 - fx) + v10 * fx) * (1 - fy) + \
               (v01 * (1 - fx) + v11 * fx) * fy


def fbm(field, x, y, freq=3.0, octaves=3, gain=0.55, lac=2.0):
    """fBm de value noise, resultado ~0..1."""
    tot = np.zeros_like(np.asarray(x, dtype=float))
    amp = 1.0
    norm = 0.0
    f = freq
    for i in range(octaves):
        tot = tot + amp * field.sample((x + 1.0) * f + i * 13.71,
                                       (y + 1.0) * f + i * 7.37)
        norm += amp
        amp *= gain
        f *= lac
    return tot / norm


# ---------------------------------------------------------------- color
def ramp(t, stops):
    t = np.clip(t, 0.0, 1.0)
    pos = np.array([s[0] for s in stops], dtype=float)
    cols = np.array([s[1] for s in stops], dtype=float)
    out = np.empty(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, pos, cols[:, c])
    return out


FIRE = [(0.00, (0.08, 0.00, 0.00)),
        (0.15, (0.55, 0.07, 0.02)),
        (0.35, (1.00, 0.38, 0.06)),
        (0.60, (1.00, 0.72, 0.22)),
        (0.85, (1.00, 0.94, 0.72)),
        (1.00, (1.00, 1.00, 0.97))]

EMBER = [(0.00, (0.12, 0.01, 0.00)),
         (0.30, (0.72, 0.12, 0.02)),
         (0.60, (1.00, 0.45, 0.08)),
         (0.85, (1.00, 0.80, 0.40)),
         (1.00, (1.00, 0.97, 0.88))]

CYAN = [(0.00, (0.00, 0.04, 0.14)),
        (0.20, (0.04, 0.30, 0.70)),
        (0.45, (0.12, 0.65, 1.00)),
        (0.70, (0.55, 0.90, 1.00)),
        (1.00, (1.00, 1.00, 1.00))]

WARPC = [(0.00, (0.04, 0.05, 0.24)),
         (0.25, (0.18, 0.30, 0.90)),
         (0.50, (0.42, 0.60, 1.00)),
         (0.75, (0.78, 0.87, 1.00)),
         (1.00, (1.00, 1.00, 1.00))]

FLASHC = [(0.00, (0.62, 0.34, 0.12)),
          (0.30, (1.00, 0.74, 0.42)),
          (0.60, (1.00, 0.92, 0.78)),
          (1.00, (1.00, 1.00, 1.00))]

TRACERC = [(0.00, (0.08, 0.18, 0.38)),
           (0.30, (0.18, 0.60, 0.92)),
           (0.60, (0.70, 0.95, 1.00)),
           (0.85, (1.00, 0.98, 0.90)),
           (1.00, (1.00, 1.00, 1.00))]

SHOCKC = [(0.00, (0.55, 0.28, 0.10)),
          (0.40, (1.00, 0.70, 0.38)),
          (0.80, (1.00, 0.92, 0.78)),
          (1.00, (1.00, 0.98, 0.93))]


# ---------------------------------------------------------------- guardado
FRAMES = {}   # nombre -> np.uint8 (32,32,4)


def finish(name, rgb, a, amax=None):
    """Envolvente de borde + normalizacion de alpha + downsample + registro."""
    env = smoothstep(1.03, 0.85, R)          # mata bordes/esquinas, suave
    a = np.clip(a, 0.0, 1.0) * env
    if amax is not None and a.max() > 1e-6:
        a = a / a.max() * amax
    rgb = np.clip(rgb, 0.0, 1.0)
    arr = np.concatenate([rgb, a[..., None]], axis=-1)
    small = arr.reshape(N, SS, N, SS, 4).mean(axis=(1, 3))
    img = (np.clip(small, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)
    FRAMES[name] = img
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, name + ".png"))


# ================================================================ 1. FIREBALL
def gen_fireball():
    fld = NoiseField(7)
    amaxs = [1.00, 1.00, 0.98, 0.92, 0.82, 0.66, 0.52, 0.40]
    colamp = [1.20, 1.05, 0.92, 0.76, 0.55, 0.42, 0.34, 0.30]
    for t in range(8):
        p = t / 7.0
        pe = 1.0 - (1.0 - p) ** 2                     # ease-out
        Rb = 0.30 + 0.50 * pe                          # radio base
        # lobulos: mismo campo, desplazado con el tiempo -> evolucion coherente
        ang = fbm(fld, np.cos(TH) * 0.9 + p * 0.85,
                  np.sin(TH) * 0.9 + p * 0.55, freq=2.5, octaves=2)
        lobe = 0.06 + 0.26 * pe
        Rmod = np.maximum(Rb * (1.0 + (ang - 0.5) * 2.0 * lobe), 1e-3)
        rr = R / Rmod
        body = smoothstep(1.0, max(0.62 - 0.30 * p, 0.15), rr)
        # erosion interna: el mismo campo drifteado se come el centro
        er = fbm(fld, X * 1.05 + p * 0.95 + 3.0, Y * 1.05 - p * 1.30,
                 freq=3.5, octaves=3)
        eth = 0.62 - 0.42 * p                          # umbral baja -> mas huecos
        holes = smoothstep(eth, eth + 0.30, er) * np.clip(p * 1.7 - 0.15, 0, 1)
        holes = holes * smoothstep(1.15, 0.35, rr)     # comer sobre todo el centro
        dens = body * (1.0 - 0.92 * holes)
        # nucleo caliente en los primeros frames
        core = np.exp(-(R / (0.15 + 0.10 * p)) ** 2) * max(0.0, 1.15 - 1.55 * p)
        I = dens * (1.15 - 0.62 * p) + core
        col = ramp(np.clip(I * colamp[t], 0, 1) ** 0.9, FIRE)
        a = np.clip(I, 0, 1) ** 0.85
        finish("fireball_%d" % t, col, a, amaxs[t])


# ================================================================ 2. FLASH
def gen_flash():
    spik = np.abs(np.cos(3.0 * TH)) ** 6
    for t, (L, amp, amax) in enumerate([(0.58, 1.00, 1.00),
                                        (0.78, 0.70, 0.80),
                                        (0.00, 0.55, 0.50)]):
        if L > 0:
            reach = 0.13 + L * spik
            rays = np.exp(-(R / reach) ** 2 * 1.7)
        else:
            rays = 0.0
        core = np.exp(-(R / 0.17) ** 2) * 1.25
        halo = np.exp(-(R / 0.55) ** 2) * 0.55
        I = (rays + core + halo) * amp
        col = ramp(np.clip(I, 0, 1) ** 0.85, FLASHC)
        a = np.clip(I, 0, 1) ** 0.9
        finish("flash_%d" % t, col, a, amax)


# ================================================================ 3. SHOCKWAVE
def gen_shockwave():
    fld = NoiseField(31)
    Rr = 0.66
    n = fbm(fld, np.cos(TH) * 0.8, np.sin(TH) * 0.8, freq=3.0, octaves=2)
    Rmod = Rr * (1.0 + (n - 0.5) * 0.06)               # ondulacion muy sutil
    d = R - Rmod
    outer = np.exp(-np.clip(d / 0.045, 0, 20) ** 2)     # borde exterior definido
    inner = np.exp(-np.clip(-d / 0.16, 0, 20) ** 1.25) * 0.85  # estela difusa
    I = np.where(d > 0, outer, inner)
    I = I * (0.92 + 0.16 * (n - 0.5))
    col = ramp(np.clip(I * 1.05, 0, 1) ** 0.9, SHOCKC)
    a = np.clip(I, 0, 1) ** 0.95
    finish("shockwave_0", col, a, 0.85)


# ================================================================ 4. SMOKE
def gen_smoke():
    fld = NoiseField(21)
    amaxs = [0.95, 0.90, 0.85, 0.78, 0.70, 0.60, 0.50, 0.42]
    for t in range(8):
        p = t / 7.0
        Rb = 0.55 + 0.33 * p
        ang = fbm(fld, np.cos(TH) * 0.85 + 1.7, np.sin(TH) * 0.85 + p * 0.6,
                  freq=2.2, octaves=2)
        lobe = 0.16 + 0.20 * p
        Rmod = np.maximum(Rb * (1.0 + (ang - 0.5) * 2.0 * lobe), 1e-3)
        rr = R / Rmod
        body = smoothstep(1.0, 0.28, rr)               # borde muy suave
        # 3 octavas, drift vertical (el humo sube) + huecos crecientes
        n = fbm(fld, X * 1.15 + 0.25 * p, Y * 1.15 + p * 1.05 + 5.0,
                freq=3.0, octaves=3)
        cut = smoothstep(0.05 + 0.45 * p, 0.70,
                         n * 0.60 + (1.0 - np.clip(rr, 0, 1)) * 0.40)
        hole = 0.45 + 0.55 * p                         # 0: denso, 7: comido
        dens = body * ((1.0 - hole) + hole * cut)
        # GRAYSCALE: valor = luz desde arriba + textura de ruido
        v = (1.0 - 0.45 * (Y + 1.0) * 0.5) * (0.82 + 0.30 * (n - 0.5))
        v = np.clip(v, 0.0, 1.0)
        rgb = np.stack([v, v, v], axis=-1)
        finish("smoke_%d" % t, rgb, dens ** 0.8, amaxs[t])


# ================================================================ 5. SPARK
def gen_spark():
    angs = [38.0, 128.0, 244.0, 312.0]
    sizes = [1.00, 0.84, 0.68, 0.54]
    amaxs = [1.00, 0.95, 0.90, 0.82]
    for t in range(4):
        a_r = np.deg2rad(angs[t])
        s = sizes[t]
        u = X * np.cos(a_r) + Y * np.sin(a_r)
        v = -X * np.sin(a_r) + Y * np.cos(a_r)
        # streak corto tipo cometa (asimetrico sobre u)
        uu = np.where(u > 0, u / (0.46 * s), u / (0.16 * s))
        streak = np.exp(-uu ** 2 - (v / (0.055 * s)) ** 2) * 0.95
        core = np.exp(-(R / (0.085 * s)) ** 2) * 1.30
        halo = np.exp(-(R / (0.34 * s)) ** 2) * 0.55
        I = streak + core + halo
        col = ramp(np.clip(I, 0, 1) ** 0.85, FIRE)
        a = np.clip(I, 0, 1) ** 0.9
        finish("spark_%d" % t, col, a, amaxs[t])


# ================================================================ 6. DEBRIS
def _shift(mask, dy, dx):
    out = np.zeros_like(mask)
    h, w = mask.shape
    ys0, ys1 = max(dy, 0), min(h + dy, h)
    xs0, xs1 = max(dx, 0), min(w + dx, w)
    out[ys0:ys1, xs0:xs1] = mask[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
    return out


def gen_debris():
    rng = np.random.RandomState(99)
    fld = NoiseField(55)
    for i in range(8):
        k = rng.randint(7, 11)
        base = np.sort(rng.uniform(0, 2 * np.pi, k))
        rads = rng.uniform(0.30, 0.68, k)
        rads = 0.6 * rads + 0.4 * np.roll(rads, 1)      # convex-ish
        cxo = rng.uniform(-0.05, 0.05)
        cyo = rng.uniform(-0.05, 0.05)
        pts = [((np.cos(a) * rd + cxo + 1.0) * 0.5 * H,
                (np.sin(a) * rd + cyo + 1.0) * 0.5 * H)
               for a, rd in zip(base, rads)]
        im = Image.new("L", (H, H), 0)
        ImageDraw.Draw(im).polygon(pts, fill=255)
        mask = np.asarray(im, dtype=float) / 255.0
        # dos tonos gris-marron oscuros separados por una cara
        mixg = rng.uniform(0.25, 0.75)
        brown = np.array([0.35, 0.27, 0.20])
        gray = np.array([0.30, 0.29, 0.28])
        tone = (brown * mixg + gray * (1 - mixg)) * rng.uniform(0.85, 1.15)
        na = rng.uniform(0, 2 * np.pi)
        sd = (X - cxo) * np.cos(na) + (Y - cyo) * np.sin(na)
        face = smoothstep(-0.05, 0.05, sd)
        col = (tone * 0.68)[None, None, :] * (1 - face[..., None]) + \
              (tone * 1.30)[None, None, :] * face[..., None]
        # textura sutil
        n = fbm(fld, X * 1.3 + i * 2.13, Y * 1.3 - i * 1.71, freq=4.0, octaves=2)
        col = col * (0.88 + 0.24 * (n - 0.5))[..., None]
        # borde iluminado arriba-izquierda / sombra abajo-derecha
        s = 4
        rim_tl = mask * (1.0 - _shift(mask, s, s))
        rim_br = mask * (1.0 - _shift(mask, -s, -s))
        lit = np.clip(tone * 1.9 + 0.18, 0, 1)
        col = col * (1.0 - 0.45 * rim_br[..., None]) + \
              lit[None, None, :] * rim_tl[..., None]
        col = np.clip(col, 0, 1) * mask[..., None]
        finish("debris_%d" % i, col, mask, 1.0)


# ================================================================ 7. TRACER
def gen_tracer():
    x0, x1 = -0.66, 0.715
    rad = 0.1875                                        # 6 px de alto
    u = np.clip(X, x0, x1)
    d = np.hypot(X - u, Y)
    fade = 0.18 + 0.82 * smoothstep(x0 - rad, x1, X)    # cola hacia la izquierda
    prof = np.exp(-(d / (rad * 0.72)) ** 2) * fade
    core = np.exp(-(d / (rad * 0.34)) ** 2) * smoothstep(x0 + 0.35, x1, X) * 1.1
    glow = np.exp(-(np.hypot((X - 0.55) * 0.8, Y) / 0.30) ** 2) * 0.35
    I = prof + core + glow
    col = ramp(np.clip(I, 0, 1) ** 0.85, TRACERC)
    a = np.clip(I, 0, 1) ** 0.9
    finish("tracer_0", col, a, 1.0)


# ================================================================ 8. MUZZLE FLASH
def gen_muzzle_flash():
    rng = np.random.RandomState(5)
    k = 5
    phis = np.sort(rng.uniform(0, 2 * np.pi, k))
    lens = rng.uniform(0.38, 0.82, k)
    wids = rng.uniform(0.20, 0.40, k)
    rots = np.deg2rad([0.0, 38.0, 74.0, 112.0])
    scs = [1.00, 0.84, 0.66, 0.48]
    amaxs = [1.00, 0.92, 0.78, 0.60]
    for t in range(4):
        Sr = np.zeros_like(TH)
        for phi, L, w in zip(phis, lens, wids):
            dd = angdiff(TH, phi + rots[t])
            Sr = np.maximum(Sr, L * np.exp(-(dd / w) ** 2))   # petalos de fuego
        reach = (0.10 + Sr) * scs[t]
        I = np.exp(-(R / np.maximum(reach, 1e-3)) ** 2 * 1.9)
        I = I + np.exp(-(R / (0.14 * scs[t])) ** 2) * 1.2
        col = ramp(np.clip(I * (0.95 + 0.25 * scs[t]), 0, 1) ** 0.85, FIRE)
        a = np.clip(I, 0, 1) ** 0.9
        finish("muzzle_flash_%d" % t, col, a, amaxs[t])


# ================================================================ 9. CHARGE MOTE
def gen_charge_mote():
    pulses = [0.50, 0.68, 0.86, 1.05]
    stars = [0.00, 0.00, 0.15, 0.38]
    amaxs = [0.58, 0.72, 0.86, 1.00]
    for t in range(4):
        pu = pulses[t]
        orb = np.exp(-(R / 0.26) ** 2) * 1.15 * pu
        ring = np.exp(-((R - 0.52) / 0.035) ** 2) * 0.55 * pu
        cross = (np.exp(-(X / 0.035) ** 2) + np.exp(-(Y / 0.035) ** 2)) * \
            np.exp(-(R / 0.52) ** 2) * stars[t]
        halo = np.exp(-(R / 0.42) ** 2) * 0.30 * pu
        I = orb + ring + cross + halo
        col = ramp(np.clip(I, 0, 1) ** 0.85, CYAN)
        a = np.clip(I, 0, 1) ** 0.9
        finish("charge_mote_%d" % t, col, a, amaxs[t])


# ================================================================ 10. WARP FLASH
def gen_warp_flash():
    for t in range(6):
        if t <= 1:                                       # linea vertical creciente
            hh = 0.34 if t == 0 else 0.66
            amp = 0.85 if t == 0 else 1.05
            line = np.exp(-(X / 0.040) ** 2) * np.exp(-(Y / hh) ** 4) * amp
            glow = np.exp(-(R / 0.28) ** 2) * (0.15 + 0.25 * t)
            I = line + glow
            amax = 0.70 if t == 0 else 0.88
        elif t == 2:                                     # flash total
            disc = np.exp(-(R / 0.50) ** 2) * 1.25
            hstar = np.exp(-(Y / 0.045) ** 2) * \
                np.exp(-(np.abs(X) / 0.80) ** 1.6) * 0.95
            vline = np.exp(-(X / 0.045) ** 2) * np.exp(-(Y / 0.75) ** 4) * 0.55
            I = disc + hstar + vline
            amax = 1.00
        else:                                            # colapso a anillo azul
            j = t - 3
            Rr = [0.56, 0.44, 0.33][j]
            amp = [0.85, 0.60, 0.40][j]
            ring = np.exp(-((R - Rr) / 0.055) ** 2) * amp
            cglow = np.exp(-(R / 0.22) ** 2) * (0.35 - 0.11 * j)
            I = ring + cglow
            amax = [0.75, 0.55, 0.38][j]
        col = ramp(np.clip(I, 0, 1) ** 0.85, WARPC)
        a = np.clip(I, 0, 1) ** 0.9
        finish("warp_flash_%d" % t, col, a, amax)


# ================================================================ 11. EMBER
def gen_ember():
    rng = np.random.RandomState(13)
    pulses = [0.62, 1.00, 0.78, 0.90]
    amaxs = [0.78, 1.00, 0.85, 0.92]
    for t in range(4):
        pu = pulses[t]
        cs = rng.uniform(0.055, 0.085)
        ta = rng.uniform(np.pi * 0.55, np.pi * 0.95)     # cola abajo-izquierda
        tl = rng.uniform(0.16, 0.30)
        u = X * np.cos(ta) + Y * np.sin(ta)
        v = -X * np.sin(ta) + Y * np.cos(ta)
        uu = np.where(u > 0, u / tl, u / (cs * 1.4))
        tail = np.exp(-uu ** 2 - (v / (cs * 0.9)) ** 2) * 0.55 * pu
        core = np.exp(-(R / cs) ** 2) * 1.25 * pu
        halo = np.exp(-(R / (0.24 + 0.06 * pu)) ** 2) * 0.55 * pu
        I = core + halo + tail
        col = ramp(np.clip(I * 0.92, 0, 1) ** 0.85, EMBER)
        a = np.clip(I, 0, 1) ** 0.9
        finish("ember_%d" % t, col, a, amaxs[t])


# ================================================================ orden y preview
ORDER = (["fireball_%d" % i for i in range(8)] +
         ["flash_%d" % i for i in range(3)] +
         ["shockwave_0"] +
         ["smoke_%d" % i for i in range(8)] +
         ["spark_%d" % i for i in range(4)] +
         ["debris_%d" % i for i in range(8)] +
         ["tracer_0"] +
         ["muzzle_flash_%d" % i for i in range(4)] +
         ["charge_mote_%d" % i for i in range(4)] +
         ["warp_flash_%d" % i for i in range(6)] +
         ["ember_%d" % i for i in range(4)])


def contact_sheet():
    cols = 8
    rows = (len(ORDER) + cols - 1) // cols
    cw, ch = 96, 88                                     # celda
    sp = 64                                             # sprite escalado 2x
    W = cols * cw
    Hh = rows * ch + 8
    sheet = Image.new("RGB", (W, Hh), (16, 16, 16))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for idx, name in enumerate(ORDER):
        cx = (idx % cols) * cw
        cy = (idx // cols) * ch + 4
        ox = cx + (cw - sp) // 2
        # fondo a cuadros gris/negro
        chk = Image.new("RGBA", (sp, sp))
        cd = ImageDraw.Draw(chk)
        for by in range(0, sp, 8):
            for bx in range(0, sp, 8):
                c = 62 if ((bx // 8 + by // 8) % 2 == 0) else 26
                cd.rectangle([bx, by, bx + 7, by + 7], fill=(c, c, c, 255))
        spr = Image.fromarray(FRAMES[name], "RGBA").resize((sp, sp),
                                                           Image.NEAREST)
        cell = Image.alpha_composite(chk, spr)
        sheet.paste(cell.convert("RGB"), (ox, cy))
        draw.text((cx + 4, cy + sp + 2), name, fill=(190, 190, 190), font=font)
    sheet.save(os.path.join(TOOLS, "preview_particles.png"))


def validate():
    print("%-16s %-6s %-5s %-9s %s" %
          ("nombre", "dims", "modo", "alpha_max", "alpha_esquinas(0,0/0,31/31,0/31,31)"))
    print("-" * 78)
    ok = True
    for name in ORDER:
        path = os.path.join(OUT, name + ".png")
        im = Image.open(path)
        arr = np.asarray(im)
        good = (im.size == (32, 32) and im.mode == "RGBA")
        a = arr[..., 3]
        corners = [int(a[0, 0]), int(a[0, 31]), int(a[31, 0]), int(a[31, 31])]
        amax = int(a.max())
        good = good and all(c <= 8 for c in corners) and amax > 60
        ok = ok and good
        print("%-16s %-6s %-5s %-9d %-2d/%-2d/%-2d/%-2d %s" %
              (name, "%dx%d" % im.size, im.mode, amax,
               corners[0], corners[1], corners[2], corners[3],
               "OK" if good else "FAIL"))
    print("-" * 78)
    print("TOTAL: %d frames %s" % (len(ORDER), "TODOS OK" if ok else "HAY FALLOS"))
    return ok


def main():
    gen_fireball()
    gen_flash()
    gen_shockwave()
    gen_smoke()
    gen_spark()
    gen_debris()
    gen_tracer()
    gen_muzzle_flash()
    gen_charge_mote()
    gen_warp_flash()
    gen_ember()
    contact_sheet()
    ok = validate()
    raise SystemExit(0 if ok else 1)


if __name__ == "__main__":
    main()
