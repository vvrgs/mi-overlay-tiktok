#!/usr/bin/env python3
"""
gen_particles.py — v2 PREMIUM. Genera los sprites de particulas de irontempest.

Un PNG RGBA de 64x64 por frame en:
  src/main/resources/assets/irontempest/textures/particle/

Cambios v2 respecto a v1 (32px):
  * Resolucion final 64x64 (supersampling 4x -> render a 256x256 float).
  * fireball: 16 frames. Nucleo con curva de temperatura blackbody-ish
    (blanco->amarillo->naranja->rojo), lobulos tallados con fBm 3-4 octavas
    ADVECTADO (mismo campo desplazado/escalado por frame, evolucion coherente),
    rim darkening (hollin envolviendo la bola antes de morir), chispas internas
    en frames 2-6, ultimos 4 frames = jirones rotos con huecos.
  * smoke: 16 frames, fBm 4 octavas, AUTO-SOMBREADO volumetrico calculado con
    el gradiente vertical del campo de densidad (luz desde arriba: tope de cada
    lobulo claro, panza en sombra), evolucion compacto->disperso, bordes suaves.
  * shockwave: anillo con textura de polvo (ruido fino), borde de ataque nitido,
    estela interior con streaks radiales, elipse de brillo calido.
  * flash: estrella de 6 puntas con halos de difraccion concentricos, nucleo
    saturado a blanco puro.
  * spark / tracer / muzzle_flash / charge_mote / warp_flash / ember: misma
    direccion de arte v1 re-renderizada a 64px con gradientes mas ricos y
    aberracion cromatica sutil (canal R mas extendido que B en los brillos).
  * debris: fragmentos grandes (~48-56px dentro del lienzo 64) con 3 caras
    sombreadas, borde de luz y textura de ruido en las caras.

El script tambien reescribe assets/irontempest/particles/fireball.json y
smoke.json (16 frames), borra PNG huerfanos, regenera preview_particles.png
y valida todo (dimensiones, esquinas, alpha, JSON <-> PNG).
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "irontempest")
OUT = os.path.join(RES, "textures", "particle")
PARTS = os.path.join(RES, "particles")
os.makedirs(OUT, exist_ok=True)

# ---------------------------------------------------------------- lienzo
SS = 4                 # supersampling
N = 64                 # tamano final
H = N * SS             # tamano de trabajo (256)

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


# ---------------------------------------------------------------- utilidades
def _box1(a, k, axis):
    pad = k // 2
    padw = [(0, 0), (0, 0)]
    padw[axis] = (pad, pad)
    ap = np.pad(a, padw, mode="edge")
    c = np.cumsum(ap, axis=axis, dtype=float)
    z = np.zeros_like(np.take(c, [0], axis=axis))
    c = np.concatenate([z, c], axis=axis)
    hi = np.take(c, range(k, c.shape[axis]), axis=axis)
    lo = np.take(c, range(0, c.shape[axis] - k), axis=axis)
    return (hi - lo) / float(k)


def blur(a, k=11, passes=2):
    """Box blur separable iterado (aprox. gaussiana)."""
    for _ in range(passes):
        a = _box1(a, k, 0)
        a = _box1(a, k, 1)
    return a


def _sample_ch(ch, ux, uy):
    """Muestreo bilineal de un canal 2D en coordenadas unitarias -1..1."""
    px = (ux + 1.0) * 0.5 * H - 0.5
    py = (uy + 1.0) * 0.5 * H - 0.5
    x0 = np.floor(px).astype(np.int64)
    y0 = np.floor(py).astype(np.int64)
    fx = px - x0
    fy = py - y0
    x0 = np.clip(x0, 0, H - 1)
    y0 = np.clip(y0, 0, H - 1)
    x1 = np.clip(x0 + 1, 0, H - 1)
    y1 = np.clip(y0 + 1, 0, H - 1)
    fx = np.clip(fx, 0.0, 1.0)
    fy = np.clip(fy, 0.0, 1.0)
    return (ch[y0, x0] * (1 - fx) + ch[y0, x1] * fx) * (1 - fy) + \
           (ch[y1, x0] * (1 - fx) + ch[y1, x1] * fx) * fy


def chroma(rgb, spread=0.025):
    """Aberracion cromatica radial sutil: R se extiende, B se contrae."""
    r = _sample_ch(rgb[..., 0], X / (1.0 + spread), Y / (1.0 + spread))
    b = _sample_ch(rgb[..., 2], X * (1.0 + spread), Y * (1.0 + spread))
    return np.stack([r, rgb[..., 1], b], axis=-1)


# ---------------------------------------------------------------- color
def ramp(t, stops):
    t = np.clip(t, 0.0, 1.0)
    pos = np.array([s[0] for s in stops], dtype=float)
    cols = np.array([s[1] for s in stops], dtype=float)
    out = np.empty(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, pos, cols[:, c])
    return out


# Curva de temperatura blackbody-ish (locus planckiano aproximado en sRGB):
# rescoldo rojo profundo -> ~1000K -> ~1800K -> ~2700K -> ~3800K -> ~5200K -> blanco.
BLACKBODY = [(0.00, (0.03, 0.00, 0.00)),
             (0.10, (0.34, 0.01, 0.00)),
             (0.25, (1.00, 0.22, 0.00)),
             (0.45, (1.00, 0.43, 0.06)),
             (0.62, (1.00, 0.62, 0.28)),
             (0.78, (1.00, 0.79, 0.55)),
             (0.90, (1.00, 0.93, 0.85)),
             (1.00, (1.00, 1.00, 1.00))]

FIRE = [(0.00, (0.07, 0.00, 0.00)),
        (0.12, (0.48, 0.04, 0.01)),
        (0.30, (0.98, 0.30, 0.03)),
        (0.50, (1.00, 0.55, 0.10)),
        (0.70, (1.00, 0.76, 0.28)),
        (0.88, (1.00, 0.94, 0.72)),
        (1.00, (1.00, 1.00, 0.98))]

EMBER = [(0.00, (0.10, 0.01, 0.00)),
         (0.22, (0.62, 0.08, 0.01)),
         (0.45, (1.00, 0.34, 0.04)),
         (0.68, (1.00, 0.58, 0.14)),
         (0.86, (1.00, 0.82, 0.44)),
         (1.00, (1.00, 0.97, 0.90))]

CYAN = [(0.00, (0.00, 0.03, 0.13)),
        (0.18, (0.02, 0.24, 0.62)),
        (0.40, (0.10, 0.58, 1.00)),
        (0.62, (0.40, 0.84, 1.00)),
        (0.82, (0.75, 0.95, 1.00)),
        (1.00, (1.00, 1.00, 1.00))]

WARPC = [(0.00, (0.03, 0.04, 0.22)),
         (0.22, (0.15, 0.25, 0.85)),
         (0.45, (0.36, 0.55, 1.00)),
         (0.68, (0.66, 0.80, 1.00)),
         (0.86, (0.86, 0.92, 1.00)),
         (1.00, (1.00, 1.00, 1.00))]

FLASHC = [(0.00, (0.55, 0.28, 0.09)),
          (0.25, (1.00, 0.66, 0.32)),
          (0.50, (1.00, 0.84, 0.58)),
          (0.75, (1.00, 0.95, 0.85)),
          (1.00, (1.00, 1.00, 1.00))]

TRACERC = [(0.00, (0.06, 0.15, 0.35)),
           (0.25, (0.14, 0.52, 0.90)),
           (0.50, (0.45, 0.85, 1.00)),
           (0.72, (0.80, 0.97, 1.00)),
           (0.88, (1.00, 0.99, 0.94)),
           (1.00, (1.00, 1.00, 1.00))]

SHOCKC = [(0.00, (0.42, 0.22, 0.09)),
          (0.30, (0.85, 0.52, 0.24)),
          (0.55, (1.00, 0.72, 0.42)),
          (0.80, (1.00, 0.90, 0.72)),
          (1.00, (1.00, 0.98, 0.92))]


# ---------------------------------------------------------------- guardado
FRAMES = {}   # nombre -> np.uint8 (64,64,4)


def finish(name, rgb, a, amax=None, envlo=0.85, envhi=1.03):
    """Envolvente de borde + normalizacion de alpha + downsample + registro."""
    env = smoothstep(envhi, envlo, R)          # mata bordes/esquinas, suave
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
    """16 frames. Un solo campo de ruido ADVECTADO (drift ascendente + escala
    de dominio) para que los lobulos y la erosion evolucionen con coherencia
    temporal. Color por curva blackbody: nucleo blanco -> amarillo -> naranja
    -> rojo al enfriarse. Rim darkening tipo hollin, chispas internas en los
    frames 2-6 y jirones rotos en los ultimos 4."""
    fld = NoiseField(7)
    F = 16
    amaxs = [1.00, 1.00, 1.00, 0.98, 0.94, 0.90, 0.84, 0.78,
             0.72, 0.66, 0.62, 0.56, 0.50, 0.46, 0.42, 0.38]
    for t in range(F):
        p = t / (F - 1.0)
        pe = 1.0 - (1.0 - p) ** 2                      # ease-out (expansion)
        Rb = 0.28 + 0.52 * pe                          # radio base
        sc = 1.0 + 0.55 * p                            # escala de dominio
        # lobulos: MISMO campo, dominio advectado (drift) y escalado por frame
        ang = fbm(fld, np.cos(TH) * 0.9 * sc + p * 0.90,
                  np.sin(TH) * 0.9 * sc - p * 0.70, freq=2.5, octaves=3)
        lobe = 0.07 + 0.30 * pe
        Rmod = np.maximum(Rb * (1.0 + (ang - 0.5) * 2.0 * lobe), 1e-3)
        rr = R / Rmod
        body = smoothstep(1.0, max(0.60 - 0.34 * p, 0.14), rr)
        # erosion: mismo campo advectado hacia arriba (el fuego sube), 4 octavas
        er = fbm(fld, X * 1.10 * sc + 3.0 + p * 0.35,
                 Y * 1.10 * sc - p * 1.45, freq=3.2, octaves=4)
        eth = 0.66 - 0.50 * p
        holes = smoothstep(eth, eth + 0.26, er) * np.clip(p * 1.9 - 0.10, 0, 1)
        holes = holes * smoothstep(1.20, 0.30, rr)
        dens = body * (1.0 - 0.95 * holes)
        # ultimos 4 frames: jirones rotos — solo sobreviven los grumos del campo
        if t >= F - 4:
            j = t - (F - 4)
            th0 = 0.48 + 0.05 * j
            shred = smoothstep(th0, th0 + 0.14, er)
            dens = dens * (0.12 + 0.88 * shred)
        # nucleo caliente que se apaga
        core = np.exp(-(R / (0.16 + 0.08 * p)) ** 2) * max(0.0, 1.30 - 2.0 * p)
        # intensidad -> temperatura (la bola entera se enfria con p, pero los
        # jirones finales conservan rescoldo naranja legible)
        I = dens * (1.05 - 0.40 * p) + core
        Tamp = 1.25 - 0.62 * p
        temp = np.clip(I * Tamp, 0, 1)
        temp = np.clip(temp + 0.35 * np.exp(-(rr / 0.45) ** 2)
                       * (1.0 - p) * dens, 0, 1)
        col = ramp(temp ** 0.88, BLACKBODY)
        # rim darkening: hollin envolviendo SOLO el borde exterior
        soot = smoothstep(0.75, 1.05, rr) * (0.22 + 0.58 * p)
        col = col * (1.0 - soot)[..., None]
        a = np.clip(dens * (1.0 - 0.15 * p) + core * 0.9, 0, 1) ** 0.85
        # chispas internas: pixeles saturados sueltos dentro del cuerpo (2-6)
        if 2 <= t <= 6:
            srng = np.random.RandomState(400 + t)
            gate = (dens > 0.25).astype(float)
            spark = np.zeros_like(R)
            for sx, sy in srng.uniform(-0.48, 0.48, size=(11, 2)):
                spark = spark + np.exp(-(((X - sx) ** 2 + (Y - sy) ** 2)
                                         / (0.022 ** 2)))
            spark = np.clip(spark, 0, 1) * gate
            col = col + np.array([1.0, 0.86, 0.45])[None, None, :] \
                * spark[..., None] * 1.5
            a = np.clip(a + spark * 0.9, 0, 1)
        finish("fireball_%d" % t, col, a, amaxs[t])


# ================================================================ 2. FLASH
def gen_flash():
    """Estrella de 6 puntas + halos de difraccion concentricos sutiles.
    Nucleo sobresaturado que clipea a blanco puro."""
    spik = np.abs(np.cos(3.0 * TH)) ** 10
    #        reach  rayamp core  halos (radio, amp)          amax
    P = [(0.55, 1.00, 1.80, [(0.40, 0.11), (0.55, 0.055)], 1.00),
         (0.78, 0.72, 1.30, [(0.52, 0.13), (0.70, 0.065)], 0.82),
         (0.42, 0.35, 0.85, [(0.62, 0.10), (0.80, 0.050)], 0.52)]
    for t, (L, amp, coreamp, rings, amax) in enumerate(P):
        reach = 0.12 + L * spik
        rays = np.exp(-(R / reach) ** 2 * 1.6) * amp
        core = np.exp(-(R / 0.16) ** 2) * coreamp        # clipea a blanco
        halo = np.exp(-(R / 0.55) ** 2) * 0.50 * amp
        diff = np.zeros_like(R)
        for rr0, ra in rings:                            # anillos de difraccion
            diff = diff + np.exp(-((R - rr0) / 0.035) ** 2) * ra
        I = rays + core + halo + diff
        col = ramp(np.clip(I, 0, 1) ** 0.82, FLASHC)
        a = np.clip(I, 0, 1) ** 0.9
        finish("flash_%d" % t, col, a, amax)


# ================================================================ 3. SHOCKWAVE
def gen_shockwave():
    """Anillo de polvo: borde de ataque nitido hacia fuera, estela interior
    con streaks radiales (ruido angular fino), textura de polvo 3 octavas y
    una elipse de brillo calido."""
    fld = NoiseField(31)
    fld2 = NoiseField(77)
    Rr = 0.62
    n = fbm(fld, np.cos(TH) * 0.8, np.sin(TH) * 0.8, freq=3.0, octaves=2)
    Rmod = Rr * (1.0 + (n - 0.5) * 0.05)
    d = R - Rmod
    lead = np.exp(-np.clip(d / 0.028, 0, 20) ** 2)       # frente nitido
    # streaks radiales: ruido que solo depende del angulo, fino
    streak = fbm(fld2, np.cos(TH) * 4.0 + 9.3, np.sin(TH) * 4.0 - 4.1,
                 freq=6.0, octaves=2)
    trail = np.exp(-np.clip(-d / 0.20, 0, 20) ** 1.3) * (0.40 + 0.70 * streak)
    I = np.where(d > 0, lead, np.maximum(trail * 0.85, lead))
    # textura de polvo fina sobre todo el anillo
    dust = fbm(fld2, X * 2.2, Y * 2.2, freq=7.0, octaves=3)
    I = I * (0.72 + 0.52 * dust)
    # elipse de brillo calido (flash rasante del suelo)
    I = I + np.exp(-(((X / 0.80) ** 2 + (Y / 0.58) ** 2)) ** 1.2) * 0.20
    col = ramp(np.clip(I * 1.05, 0, 1) ** 0.9, SHOCKC)
    a = np.clip(I, 0, 1) ** 0.95
    finish("shockwave_0", col, a, 0.85)


# ================================================================ 4. SMOKE
def gen_smoke():
    """16 frames, fBm 4 octavas advectado. AUTO-SOMBREADO volumetrico: la luz
    viene de arriba y el valor local se calcula con el gradiente VERTICAL del
    campo de densidad difuminado (tope de cada lobulo claro, panza en sombra),
    no con un gradiente global plano. Evolucion compacto -> disperso."""
    fld = NoiseField(21)
    F = 16
    amaxs = [0.95, 0.93, 0.90, 0.87, 0.84, 0.80, 0.76, 0.72,
             0.67, 0.62, 0.57, 0.52, 0.47, 0.42, 0.36, 0.30]
    for t in range(F):
        p = t / (F - 1.0)
        Rb = 0.44 + 0.42 * p
        sc = 1.0 + 0.45 * p
        ang = fbm(fld, np.cos(TH) * 0.85 * sc + 1.7,
                  np.sin(TH) * 0.85 * sc + p * 0.50, freq=2.2, octaves=3)
        lobe = 0.14 + 0.22 * p
        Rmod = np.maximum(Rb * (1.0 + (ang - 0.5) * 2.0 * lobe), 1e-3)
        rr = R / Rmod
        body = smoothstep(1.0, 0.20, rr)                 # borde muy suave
        # campo de densidad: 4 octavas, drift ascendente (el humo sube)
        n = fbm(fld, X * 1.15 * sc + 0.30 * p,
                Y * 1.15 * sc + p * 1.15 + 5.0, freq=2.8, octaves=4)
        cut = smoothstep(0.08 + 0.42 * p, 0.72,
                         n * 0.62 + (1.0 - np.clip(rr, 0, 1)) * 0.38)
        hole = 0.35 + 0.60 * p                           # compacto -> disperso
        dens = (body * ((1.0 - hole) + hole * cut)) ** 0.9
        # ---- auto-sombreado volumetrico por gradiente vertical del campo
        db = blur(dens, k=11, passes=2)
        ddy = np.gradient(db, axis=0)                    # +y hacia abajo
        norm = np.abs(ddy).max() * 0.60 + 1e-9
        gn = np.clip(ddy / norm, -1.0, 1.0)              # >0: cara superior
        v = 0.58 + 0.30 * gn
        v = v * (0.88 + 0.26 * (n - 0.5))                # textura interna
        v = v * (1.0 - 0.18 * db)                        # espesor absorbe luz
        v = np.clip(v, 0.10, 1.0)
        rgb = np.stack([v, v, v], axis=-1)
        finish("smoke_%d" % t, rgb, dens ** 0.85, amaxs[t])


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
        # streak principal tipo cometa (asimetrico sobre u)
        uu = np.where(u > 0, u / (0.46 * s), u / (0.16 * s))
        streak = np.exp(-uu ** 2 - (v / (0.050 * s)) ** 2) * 0.95
        # micro-rama secundaria, mas corta y desviada
        a2 = a_r + 0.55
        u2 = X * np.cos(a2) + Y * np.sin(a2)
        v2 = -X * np.sin(a2) + Y * np.cos(a2)
        uu2 = np.where(u2 > 0, u2 / (0.26 * s), u2 / (0.10 * s))
        branch = np.exp(-uu2 ** 2 - (v2 / (0.035 * s)) ** 2) * 0.40
        core = np.exp(-(R / (0.080 * s)) ** 2) * 1.35
        halo = np.exp(-(R / (0.34 * s)) ** 2) * 0.55
        I = streak + branch + core + halo
        col = ramp(np.clip(I, 0, 1) ** 0.85, FIRE)
        col = chroma(col)
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
    """Fragmentos grandes (~48-56px en lienzo 64): 3 caras sombreadas separadas
    por aristas (lit / media / oscura), borde de luz arriba-izquierda, sombra
    de contacto abajo-derecha y ruido fino en las caras."""
    rng = np.random.RandomState(99)
    fld = NoiseField(55)
    for i in range(8):
        k = rng.randint(7, 12)
        base = np.sort(rng.uniform(0, 2 * np.pi, k))
        rads = rng.uniform(0.58, 0.86, k)
        rads = 0.6 * rads + 0.4 * np.roll(rads, 1)       # convex-ish
        cxo = rng.uniform(-0.04, 0.04)
        cyo = rng.uniform(-0.04, 0.04)
        pts = [((np.cos(a) * rd + cxo + 1.0) * 0.5 * H,
                (np.sin(a) * rd + cyo + 1.0) * 0.5 * H)
               for a, rd in zip(base, rads)]
        im = Image.new("L", (H, H), 0)
        ImageDraw.Draw(im).polygon(pts, fill=255)
        mask = np.asarray(im, dtype=float) / 255.0
        # tono base gris-marron
        mixg = rng.uniform(0.25, 0.75)
        brown = np.array([0.36, 0.28, 0.20])
        gray = np.array([0.30, 0.29, 0.28])
        tone = (brown * mixg + gray * (1 - mixg)) * rng.uniform(0.85, 1.15)
        # ---- 3 caras: dos planos de corte con normales distintas
        na = rng.uniform(0, 2 * np.pi)
        nb = na + rng.uniform(1.6, 2.2)
        sd1 = (X - cxo) * np.cos(na) + (Y - cyo) * np.sin(na)
        sd2 = (X - cxo) * np.cos(nb) + (Y - cyo) * np.sin(nb)
        f1 = smoothstep(-0.035, 0.035, sd1)
        f2 = smoothstep(-0.035, 0.035, sd2)
        litc = np.clip(tone * 1.45 + 0.10, 0, 1)
        midc = tone
        darkc = tone * 0.52
        col = litc[None, None, :] * (1 - f1)[..., None] + \
            (midc[None, None, :] * (1 - f2)[..., None] +
             darkc[None, None, :] * f2[..., None]) * f1[..., None]
        # textura de ruido en las caras (3 octavas, distinta fase por cara)
        n1 = fbm(fld, X * 1.5 + i * 2.13, Y * 1.5 - i * 1.71,
                 freq=5.0, octaves=3)
        n2 = fbm(fld, X * 2.6 - i * 1.07, Y * 2.6 + i * 0.83,
                 freq=7.0, octaves=2)
        col = col * (0.84 + 0.30 * (n1 - 0.5) + 0.10 * (n2 - 0.5))[..., None]
        # borde de luz arriba-izquierda / sombra abajo-derecha
        s = 6
        rim_tl = mask * (1.0 - _shift(mask, s, s))
        rim_br = mask * (1.0 - _shift(mask, -s, -s))
        lit = np.clip(tone * 2.1 + 0.22, 0, 1)
        col = col * (1.0 - 0.50 * rim_br[..., None]) + \
            lit[None, None, :] * rim_tl[..., None]
        col = np.clip(col, 0, 1) * mask[..., None]
        finish("debris_%d" % i, col, mask, 1.0, envlo=0.93, envhi=1.03)


# ================================================================ 7. TRACER
def gen_tracer():
    """Capsula dentro del lienzo 64x64: nucleo blanco-caliente, glow cyan,
    cola que se desvanece hacia la izquierda. CA sutil en los bordes."""
    x0, x1 = -0.70, 0.70
    rad = 0.15
    u = np.clip(X, x0, x1)
    d = np.hypot(X - u, Y)
    fade = 0.15 + 0.85 * smoothstep(x0 - rad, x1, X)     # cola a la izquierda
    prof = np.exp(-(d / (rad * 0.75)) ** 2) * fade
    core = np.exp(-(d / (rad * 0.32)) ** 2) * \
        smoothstep(x0 + 0.30, x1 * 0.90, X) * 1.25
    glow = np.exp(-(np.hypot((X - 0.55) * 0.85, Y) / 0.30) ** 2) * 0.40
    I = prof + core + glow
    col = ramp(np.clip(I, 0, 1) ** 0.85, TRACERC)
    col = chroma(col)
    a = np.clip(I, 0, 1) ** 0.9
    finish("tracer_0", col, a, 1.0)


# ================================================================ 8. MUZZLE FLASH
def gen_muzzle_flash():
    rng = np.random.RandomState(5)
    fld = NoiseField(63)
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
            Sr = np.maximum(Sr, L * np.exp(-(dd / w) ** 2))   # petalos
        reach = (0.10 + Sr) * scs[t]
        I = np.exp(-(R / np.maximum(reach, 1e-3)) ** 2 * 1.9)
        # textura fina en los petalos para que no sean planos
        n = fbm(fld, X * 1.6 + t * 0.9, Y * 1.6 - t * 0.6, freq=4.5, octaves=3)
        I = I * (0.82 + 0.36 * n)
        I = I + np.exp(-(R / (0.14 * scs[t])) ** 2) * 1.30
        col = ramp(np.clip(I * (0.95 + 0.25 * scs[t]), 0, 1) ** 0.85, FIRE)
        col = chroma(col)
        a = np.clip(I, 0, 1) ** 0.9
        finish("muzzle_flash_%d" % t, col, a, amaxs[t])


# ================================================================ 9. CHARGE MOTE
def gen_charge_mote():
    pulses = [0.50, 0.68, 0.86, 1.05]
    stars = [0.00, 0.00, 0.15, 0.38]
    amaxs = [0.58, 0.72, 0.86, 1.00]
    for t in range(4):
        pu = pulses[t]
        orb = np.exp(-(R / 0.26) ** 2) * 1.20 * pu
        ring = np.exp(-((R - 0.52) / 0.030) ** 2) * 0.58 * pu
        ring2 = np.exp(-((R - 0.66) / 0.025) ** 2) * 0.18 * pu
        cross = (np.exp(-(X / 0.030) ** 2) + np.exp(-(Y / 0.030) ** 2)) * \
            np.exp(-(R / 0.52) ** 2) * stars[t]
        halo = np.exp(-(R / 0.42) ** 2) * 0.30 * pu
        I = orb + ring + ring2 + cross + halo
        col = ramp(np.clip(I, 0, 1) ** 0.85, CYAN)
        col = chroma(col)
        a = np.clip(I, 0, 1) ** 0.9
        finish("charge_mote_%d" % t, col, a, amaxs[t])


# ================================================================ 10. WARP FLASH
def gen_warp_flash():
    for t in range(6):
        if t <= 1:                                       # linea vertical
            hh = 0.34 if t == 0 else 0.66
            amp = 0.85 if t == 0 else 1.05
            line = np.exp(-(X / 0.036) ** 2) * np.exp(-(Y / hh) ** 4) * amp
            glow = np.exp(-(R / 0.28) ** 2) * (0.15 + 0.25 * t)
            I = line + glow
            amax = 0.70 if t == 0 else 0.88
        elif t == 2:                                     # flash total
            disc = np.exp(-(R / 0.50) ** 2) * 1.30
            hstar = np.exp(-(Y / 0.040) ** 2) * \
                np.exp(-(np.abs(X) / 0.80) ** 1.6) * 0.95
            vline = np.exp(-(X / 0.040) ** 2) * np.exp(-(Y / 0.75) ** 4) * 0.55
            I = disc + hstar + vline
            amax = 1.00
        else:                                            # colapso a anillo
            j = t - 3
            Rr = [0.56, 0.44, 0.33][j]
            amp = [0.85, 0.60, 0.40][j]
            ring = np.exp(-((R - Rr) / 0.048) ** 2) * amp
            cglow = np.exp(-(R / 0.22) ** 2) * (0.35 - 0.11 * j)
            I = ring + cglow
            amax = [0.75, 0.55, 0.38][j]
        col = ramp(np.clip(I, 0, 1) ** 0.85, WARPC)
        col = chroma(col)
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
        core = np.exp(-(R / cs) ** 2) * 1.30 * pu
        halo = np.exp(-(R / (0.24 + 0.06 * pu)) ** 2) * 0.55 * pu
        I = core + halo + tail
        col = ramp(np.clip(I * 0.92, 0, 1) ** 0.85, EMBER)
        col = chroma(col)
        a = np.clip(I, 0, 1) ** 0.9
        finish("ember_%d" % t, col, a, amaxs[t])


# ================================================================ frames / json
COUNTS = {"fireball": 16, "flash": 3, "shockwave": 1, "smoke": 16,
          "spark": 4, "debris": 8, "tracer": 1, "muzzle_flash": 4,
          "charge_mote": 4, "warp_flash": 6, "ember": 4}

ORDER = []
for _base in ["fireball", "flash", "shockwave", "smoke", "spark", "debris",
              "tracer", "muzzle_flash", "charge_mote", "warp_flash", "ember"]:
    ORDER += ["%s_%d" % (_base, i) for i in range(COUNTS[_base])]


def write_animated_jsons():
    """Actualiza SOLO fireball.json y smoke.json (ahora 16 frames)."""
    for base in ("fireball", "smoke"):
        data = {"textures": ["irontempest:%s_%d" % (base, i)
                             for i in range(COUNTS[base])]}
        path = os.path.join(PARTS, base + ".json")
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
    print("JSON actualizados: fireball.json (16), smoke.json (16)")


def clean_orphans():
    keep = set(n + ".png" for n in ORDER)
    removed = []
    for fn in sorted(os.listdir(OUT)):
        if fn.endswith(".png") and fn not in keep:
            os.remove(os.path.join(OUT, fn))
            removed.append(fn)
    if removed:
        print("PNG huerfanos borrados: %s" % ", ".join(removed))
    else:
        print("Sin PNG huerfanos.")


# ================================================================ preview
def contact_sheet():
    cols = 8
    rows = (len(ORDER) + cols - 1) // cols
    sp = 96                                              # sprite mostrado 1.5x
    cw, ch = 112, 116
    W = cols * cw
    Hh = rows * ch + 8
    sheet = Image.new("RGB", (W, Hh), (14, 14, 14))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for idx, name in enumerate(ORDER):
        cx = (idx % cols) * cw
        cy = (idx // cols) * ch + 4
        ox = cx + (cw - sp) // 2
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


# ================================================================ validacion
def validate():
    print("%-16s %-6s %-5s %-9s %s" %
          ("nombre", "dims", "modo", "alpha_max",
           "alpha_esquinas(0,0/0,63/63,0/63,63)"))
    print("-" * 78)
    ok = True
    for name in ORDER:
        path = os.path.join(OUT, name + ".png")
        im = Image.open(path)
        arr = np.asarray(im)
        good = (im.size == (64, 64) and im.mode == "RGBA")
        a = arr[..., 3]
        corners = [int(a[0, 0]), int(a[0, 63]), int(a[63, 0]), int(a[63, 63])]
        amax = int(a.max())
        good = good and all(c <= 8 for c in corners) and amax > 60
        ok = ok and good
        print("%-16s %-6s %-5s %-9d %-2d/%-2d/%-2d/%-2d %s" %
              (name, "%dx%d" % im.size, im.mode, amax,
               corners[0], corners[1], corners[2], corners[3],
               "OK" if good else "FAIL"))
    print("-" * 78)
    # JSON <-> PNG
    pngs = set(fn[:-4] for fn in os.listdir(OUT) if fn.endswith(".png"))
    jok = True
    for base, cnt in sorted(COUNTS.items()):
        path = os.path.join(PARTS, base + ".json")
        try:
            with open(path) as f:
                data = json.load(f)
            texs = data["textures"]
            names = [t.split(":", 1)[1] for t in texs]
            expect = ["%s_%d" % (base, i) for i in range(cnt)]
            good = (names == expect) and all(n in pngs for n in names)
        except Exception as e:
            good = False
            print("  %s.json ERROR: %s" % (base, e))
        jok = jok and good
        print("%-18s %2d frames %s" % (base + ".json",
                                       len(texs) if good else -1,
                                       "OK" if good else "FAIL"))
    extra = pngs - set(ORDER)
    if extra:
        jok = False
        print("PNG sin referenciar: %s" % ", ".join(sorted(extra)))
    print("-" * 78)
    allok = ok and jok
    print("TOTAL: %d frames, %d json %s" %
          (len(ORDER), len(COUNTS), "TODOS OK" if allok else "HAY FALLOS"))
    return allok


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
    write_animated_jsons()
    clean_orphans()
    contact_sheet()
    ok = validate()
    raise SystemExit(0 if ok else 1)


if __name__ == "__main__":
    main()
