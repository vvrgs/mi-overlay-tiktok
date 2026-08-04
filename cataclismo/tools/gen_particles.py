#!/usr/bin/env python3
"""
gen_particles.py — Generador procedural de sprites de partículas del mod "Cataclismo".

Genera todos los sprites de partículas custom del mod como PNG RGBA de 64x64 en
    src/main/resources/assets/cataclysm/textures/particle/

Técnica: cada sprite se dibuja a 256x256 (supersampleo x4) y se reduce a 64x64
con remuestreo LANCZOS. La reducción se hace en espacio premultiplicado en coma
flotante y después se des-premultiplica, rellenando los píxeles de alpha casi
nula con el color puro base del sprite. Así el alpha tiene falloff suave y los
colores quedan "premultiplicables limpios": sin halos oscuros en los bordes.

Determinismo: la semilla de cada sprite se deriva del hash CRC32 de su nombre
(o del nombre base de la animación cuando los frames deben compartir la misma
estructura, p. ej. la voluta de humo que gira o el salpicón que se abre). El
ruido usa un hash entero propio, así que el resultado es idéntico entre
ejecuciones y plataformas. El script es re-ejecutable: sobreescribe los PNG.

Sprites generados (39 archivos):
  ash_0..3        copos de ceniza gris claro con bordes rotos
  ember_0..3      brasa con núcleo blanco-amarillo pulsante y halo naranja
  dust_0..3       motas de polvo marrón-grisáceas con textura ruidosa
  plasma_0..3     bola de plasma blanco->naranja con halo aditivo y lenguas
  smoke_0..3      volutas de humo gris con ruido fractal, giradas por frame
  raindrop_0      gota: trazo vertical azul-blanco con brillo arriba
  spray_0..3      espuma blanca con gotitas satélite abriéndose
  spark_0..1      chispa eléctrica de 4 puntas, frame 1 rotado 45 grados
  telegraph_0..1  rombo dorado brillante con halo, frame 1 más abierto
  lava_glob_0..3  globo de lava con grietas oscuras y núcleo amarillo
  debris_0..3     escombro poligonal sólido iluminado desde arriba-izquierda
  pop_flash_0..1  destello de 8 puntas (frame 0 dorado, frame 1 verde)

Uso:  python3 tools/gen_particles.py
"""

import math
import random
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

BIG = 256          # resolución de dibujo (supersampleo x4)
SIZE = 64          # resolución final
TWO_PI = 2.0 * math.pi

OUT_DIR = (
    Path(__file__).resolve().parent.parent
    / "src" / "main" / "resources" / "assets" / "cataclysm" / "textures" / "particle"
)

# ---------------------------------------------------------------------------
# Utilidades básicas
# ---------------------------------------------------------------------------

def seed_for(name: str) -> int:
    """Semilla determinista derivada del nombre (CRC32)."""
    return zlib.crc32(name.encode("utf-8"))


def grid():
    """Coordenadas normalizadas [-1, 1] centradas, a resolución BIG."""
    ys, xs = np.mgrid[0:BIG, 0:BIG].astype(np.float64)
    x = (xs + 0.5) / BIG * 2.0 - 1.0
    y = (ys + 0.5) / BIG * 2.0 - 1.0
    return x, y


def smoothstep(e0, e1, x):
    """Interpolación suave clásica; admite e0 > e1 (rampa descendente)."""
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def gauss(r, sigma):
    return np.exp(-(r / sigma) ** 2)


def ramp(t, stops):
    """Rampa de color por tramos. stops = [(pos, (r,g,b)), ...] con pos creciente."""
    ps = np.array([p for p, _ in stops], dtype=np.float64)
    cs = np.array([c for _, c in stops], dtype=np.float64)
    out = np.empty(t.shape + (3,))
    for i in range(3):
        out[..., i] = np.interp(t, ps, cs[:, i])
    return out


# ---------------------------------------------------------------------------
# Ruido determinista (value noise + fBm) basado en hash entero
# ---------------------------------------------------------------------------

_M32 = np.uint64(0xFFFFFFFF)


def _hash01(ix, iy, seed: int):
    """Hash entero 2D -> [0,1). Determinista entre plataformas."""
    a = (ix.astype(np.uint64) * np.uint64(374761393)) & _M32
    b = (iy.astype(np.uint64) * np.uint64(668265263)) & _M32
    n = (a + b + np.uint64((seed & 0xFFFFFFFF)) * np.uint64(2246822519)) & _M32
    n = n ^ (n >> np.uint64(13))
    n = (n * np.uint64(1274126177)) & _M32
    n = n ^ (n >> np.uint64(16))
    return n.astype(np.float64) / float(0xFFFFFFFF)


def vnoise(x, y, seed: int):
    """Value noise 2D con interpolación smoothstep, muestreable en coords arbitrarias."""
    ix = np.floor(x).astype(np.int64)
    iy = np.floor(y).astype(np.int64)
    fx = x - ix
    fy = y - iy
    u = fx * fx * (3.0 - 2.0 * fx)
    v = fy * fy * (3.0 - 2.0 * fy)
    n00 = _hash01(ix, iy, seed)
    n10 = _hash01(ix + 1, iy, seed)
    n01 = _hash01(ix, iy + 1, seed)
    n11 = _hash01(ix + 1, iy + 1, seed)
    return (n00 * (1 - u) + n10 * u) * (1 - v) + (n01 * (1 - u) + n11 * u) * v


def fbm(x, y, octaves: int, seed: int, gain=0.5, lacunarity=2.0):
    """Ruido fractal (varias octavas de value noise), normalizado a ~[0,1]."""
    total = np.zeros_like(x)
    amp, freq, norm = 1.0, 1.0, 0.0
    for o in range(octaves):
        total += amp * vnoise(x * freq, y * freq, seed + o * 101)
        norm += amp
        amp *= gain
        freq *= lacunarity
    return total / norm


def spike(x, y, ang, length, width, sharp=1.8):
    """Púa fina de estrella a lo largo del eje 'ang' (cubre ambos sentidos)."""
    ca, sa = math.cos(ang), math.sin(ang)
    u = x * ca + y * sa
    v = -x * sa + y * ca
    along = np.clip(1.0 - np.abs(u) / length, 0.0, 1.0) ** sharp
    w = width * (0.2 + 0.8 * along)          # se afila hacia la punta
    return along * np.exp(-(v / w) ** 2)


# ---------------------------------------------------------------------------
# Guardado: 256 -> 64 con LANCZOS en espacio premultiplicado (sin halos)
# ---------------------------------------------------------------------------

def save_sprite(name, rgb, alpha, fill):
    """Reduce a 64x64 con LANCZOS y guarda PNG RGBA con color puro en alpha baja."""
    rgb = np.clip(rgb, 0.0, 1.0)
    alpha = np.clip(alpha, 0.0, 1.0)
    premul = rgb * alpha[..., None]

    def resize_f(ch):
        img = Image.fromarray(ch.astype(np.float32), mode="F")
        return np.asarray(img.resize((SIZE, SIZE), Image.Resampling.LANCZOS), dtype=np.float64)

    a64 = np.clip(resize_f(alpha), 0.0, 1.0)
    out = np.empty((SIZE, SIZE, 3))
    for i in range(3):
        out[..., i] = resize_f(premul[..., i])
    safe = np.maximum(a64, 1e-4)[..., None]
    col = np.clip(out / safe, 0.0, 1.0)
    low = a64 < 0.01                       # píxeles casi transparentes: color puro
    col[low] = np.asarray(fill, dtype=np.float64)

    rgba = np.dstack([col, a64])
    img = Image.fromarray((rgba * 255.0 + 0.5).astype(np.uint8), mode="RGBA")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    img.save(OUT_DIR / name)


# ---------------------------------------------------------------------------
# Generadores de sprites
# ---------------------------------------------------------------------------

def gen_ash(name, frame):
    """Copo de ceniza: silueta irregular gris clara con bordes rotos."""
    s = seed_for(name)                      # cada frame es un copo distinto
    x, y = grid()
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    ang = fbm(np.cos(th) * 1.6 + 7.3, np.sin(th) * 1.6 + 3.1, 3, s)
    radius = 0.40 + 0.34 * (ang - 0.5)
    shape = smoothstep(radius, radius - 0.10, r)
    n = fbm(x * 4.5 + 11.0, y * 4.5 + 5.0, 4, s + 17)
    holes = smoothstep(0.34, 0.48, n)       # agujeros que rompen el borde
    rim = smoothstep(radius - 0.28, radius, r)
    alpha = shape * holes * (1.0 - 0.65 * rim * smoothstep(0.55, 0.42, n))
    alpha = np.clip(alpha * 1.15, 0.0, 1.0) * 0.92
    v = 0.80 + 0.14 * (fbm(x * 3.0 + 23.0, y * 3.0 + 9.0, 3, s + 29) - 0.5)
    v *= 1.0 - 0.22 * smoothstep(0.62, 0.75, fbm(x * 6.0 + 41.0, y * 6.0 + 2.0, 3, s + 31))
    v = np.clip(v, 0.0, 1.0)
    rgb = np.dstack([v, v, v * 0.97])
    return rgb, alpha, (0.80, 0.80, 0.78)


def gen_ember(name, frame):
    """Brasa: núcleo blanco-amarillo pulsante con halo naranja radial."""
    s = seed_for("ember")
    x, y = grid()
    r = np.hypot(x, y)
    pulse = math.sin(TWO_PI * frame / 4.0)
    core = 0.15 + 0.05 * pulse
    halo = 0.42 + 0.03 * pulse
    alpha = np.clip(
        1.05 * gauss(r, halo) ** 1.6 + 0.9 * gauss(r, core * 1.4), 0.0, 1.0
    )
    flick = 0.92 + 0.16 * (fbm(x * 3.0 + frame * 7.0, y * 3.0 + 1.0, 3, s) - 0.5)
    alpha = np.clip(alpha * flick, 0.0, 1.0)
    rgb = ramp(r, [
        (0.00, (1.00, 0.98, 0.88)),
        (core, (1.00, 0.85, 0.35)),
        (0.32, (1.00, 0.55, 0.12)),
        (0.60, (0.96, 0.38, 0.06)),
        (1.50, (0.92, 0.32, 0.05)),
    ])
    return rgb, alpha, (1.0, 0.55, 0.12)


def gen_dust(name, frame):
    """Mota de polvo: mancha marrón-grisácea suave con textura ruidosa."""
    s = seed_for(name)
    rl = random.Random(s)
    x, y = grid()
    a = np.zeros_like(x)
    for _ in range(3):
        ox, oy = rl.uniform(-0.18, 0.18), rl.uniform(-0.18, 0.18)
        sg = rl.uniform(0.28, 0.40)
        a += 0.55 * np.exp(-(((x - ox) ** 2 + (y - oy) ** 2) / sg ** 2))
    n = fbm(x * 3.2 + 5.0, y * 3.2 + 13.0, 4, s + 3)
    alpha = np.clip(a, 0.0, 1.0) * (0.35 + 0.65 * n) * 0.72
    alpha *= smoothstep(1.0, 0.70, np.hypot(x, y))
    base = (0.55, 0.48, 0.40)
    m = 0.88 + 0.30 * (fbm(x * 5.0 + 9.0, y * 5.0 + 21.0, 3, s + 7) - 0.5)
    rgb = np.clip(np.asarray(base)[None, None, :] * m[..., None], 0.0, 1.0)
    return rgb, alpha, base


def gen_plasma(name, frame):
    """Plasma: bola blanco->naranja con halo aditivo y pequeñas lenguas."""
    s = seed_for("plasma")                  # estructura compartida entre frames
    x, y = grid()
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    cs, sn = np.cos(th), np.sin(th)
    tongues = fbm(cs * 2.3 + 5.0 + frame * 0.8, sn * 2.3 + 2.0 + frame * 0.5, 3, s)
    small = fbm(cs * 5.0 + 9.0 + frame * 1.1, sn * 5.0 + 4.0 + frame * 0.7, 2, s + 13)
    radius = 0.34 + 0.22 * (tongues - 0.45) + 0.07 * (small - 0.5)
    body = smoothstep(radius, radius * 0.35, r)
    halo = 0.50 * gauss(r, 0.55)            # halo aditivo
    alpha = np.clip(body + halo, 0.0, 1.0)
    rgb = ramp(r, [
        (0.00, (1.00, 1.00, 0.96)),
        (0.12, (1.00, 0.93, 0.65)),
        (0.30, (1.00, 0.68, 0.25)),
        (0.55, (1.00, 0.45, 0.10)),
        (1.50, (0.95, 0.40, 0.08)),
    ])
    return rgb, alpha, (1.0, 0.50, 0.12)


def gen_smoke(name, frame):
    """Humo: voluta gris con ruido fractal (5 octavas), girada por frame."""
    s = seed_for("smoke")                   # misma voluta, rotada
    ang = frame * 0.45
    x, y = grid()
    ca, sa = math.cos(ang), math.sin(ang)
    xr = x * ca - y * sa
    yr = x * sa + y * ca
    n = fbm(xr * 2.6 + 31.7, yr * 2.6 + 17.3, 5, s)
    r = np.hypot(x, y)
    envelope = gauss(r, 0.62)
    alpha = envelope * smoothstep(0.34, 0.72, n)
    alpha = np.clip(alpha * 1.25, 0.0, 1.0) * 0.80 * smoothstep(1.0, 0.60, r)
    v = np.clip(0.50 + 0.35 * n, 0.0, 1.0)
    rgb = np.dstack([v * 0.98, v * 0.98, v])
    return np.clip(rgb, 0.0, 1.0), alpha, (0.62, 0.62, 0.64)


def gen_raindrop(name, frame):
    """Gota: trazo vertical alargado azul-blanco semitransparente, brillo arriba."""
    x, y = grid()
    w = 0.085 * (1.0 - 0.38 * smoothstep(-0.6, 0.6, y))   # más fina abajo
    a = np.exp(-(x / w) ** 2)
    envelope = smoothstep(-0.68, -0.42, y) * smoothstep(0.68, 0.45, y)
    hl = np.exp(-((x ** 2 + (y + 0.42) ** 2) / 0.14 ** 2))
    alpha = np.clip(a * envelope * 0.62 + 0.28 * hl * envelope, 0.0, 1.0)
    base = np.asarray((0.72, 0.84, 1.00))
    white = np.asarray((0.97, 0.99, 1.00))
    t = np.clip(hl * 1.3, 0.0, 1.0)[..., None]
    rgb = base[None, None, :] * (1.0 - t) + white[None, None, :] * t
    return rgb, alpha, (0.72, 0.84, 1.00)


def gen_spray(name, frame):
    """Espuma: salpicón blanco con gotitas satélite que se abren por frame."""
    s = seed_for("spray")                   # mismas trayectorias en todos los frames
    rl = random.Random(s)
    f = frame / 3.0
    x, y = grid()
    a = 0.95 * (1.0 - 0.35 * f) * gauss(np.hypot(x, y), 0.18 + 0.09 * f)
    for _ in range(10):
        ang = rl.uniform(0.0, TWO_PI)
        rad0 = rl.uniform(0.10, 0.20)
        speed = rl.uniform(0.35, 0.60)
        size = rl.uniform(0.05, 0.09) * (1.0 - 0.35 * f)
        amp = rl.uniform(0.6, 0.95) * (1.0 - 0.30 * f)
        cx = math.cos(ang) * (rad0 + speed * f)
        cy = math.sin(ang) * (rad0 + speed * f)
        a += amp * np.exp(-(((x - cx) ** 2 + (y - cy) ** 2) / size ** 2))
    alpha = np.clip(a, 0.0, 1.0) * 0.95
    r = np.hypot(x, y)
    rgb = ramp(r, [
        (0.00, (1.00, 1.00, 1.00)),
        (0.35, (0.95, 0.98, 1.00)),
        (1.50, (0.88, 0.94, 1.00)),
    ])
    return rgb, alpha, (0.95, 0.98, 1.00)


def gen_spark(name, frame):
    """Chispa eléctrica: estrella fina de 4 puntas con cruz de difracción."""
    x, y = grid()
    r = np.hypot(x, y)
    base = frame * math.pi / 4.0            # frame 1 rotado 45 grados
    a = spike(x, y, base, 0.82, 0.035) + spike(x, y, base + math.pi / 2, 0.82, 0.035)
    a += 0.40 * (spike(x, y, base + math.pi / 4, 0.46, 0.030)
                 + spike(x, y, base + 3 * math.pi / 4, 0.46, 0.030))
    a += 0.95 * gauss(r, 0.09) + 0.22 * gauss(r, 0.30)
    alpha = np.clip(a, 0.0, 1.0)
    rgb = ramp(r, [
        (0.00, (1.00, 1.00, 1.00)),
        (0.10, (0.92, 0.97, 1.00)),
        (0.35, (0.70, 0.85, 1.00)),
        (1.50, (0.55, 0.75, 1.00)),
    ])
    return rgb, alpha, (0.62, 0.80, 1.00)


def gen_telegraph(name, frame):
    """Mota dorada: rombo brillante con halo; frame 1 más abierto (hueco)."""
    x, y = grid()
    d = np.abs(x) + np.abs(y)               # distancia rombo
    r = np.hypot(x, y)
    if frame == 0:
        radius, ew, hollow = 0.30, 0.12, 0.0
    else:
        radius, ew = 0.44, 0.16
        hollow = 0.55 * smoothstep(0.26, 0.06, d)   # el centro se vacía
    body = smoothstep(radius, radius - ew, d) * (1.0 - hollow)
    halo = 0.45 * gauss(r, 0.45)
    alpha = np.clip(body + halo, 0.0, 1.0)
    rgb = ramp(d, [
        (0.00, (1.00, 0.97, 0.80)),
        (0.15, (1.00, 0.85, 0.40)),
        (0.35, (1.00, 0.72, 0.20)),
        (1.50, (0.95, 0.60, 0.10)),
    ])
    return rgb, alpha, (1.0, 0.78, 0.25)


def gen_lava_glob(name, frame):
    """Globo de lava: blob naranja con grietas oscuras y núcleo amarillo."""
    s = seed_for("lava_glob")               # mismo blob, deformado por frame
    x, y = grid()
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    cs, sn = np.cos(th), np.sin(th)
    deform = fbm(cs * 1.7 + 4.0 + frame * 0.9, sn * 1.7 + 8.0 + frame * 0.6, 3, s)
    radius = 0.46 * (1.0 + 0.35 * (deform - 0.5))
    body = smoothstep(radius, radius - 0.07, r)
    c = fbm(x * 4.5 + frame * 1.3 + 3.0, y * 4.5 + frame * 0.7 + 19.0, 4, s + 11)
    crack = np.exp(-((c - 0.52) / 0.045) ** 2) * smoothstep(0.10, 0.22, r)
    t = r / np.maximum(radius, 1e-3)
    rgb = ramp(t, [
        (0.00, (1.00, 0.96, 0.50)),
        (0.35, (1.00, 0.72, 0.18)),
        (0.70, (1.00, 0.50, 0.08)),
        (1.00, (0.80, 0.30, 0.04)),
        (2.00, (0.70, 0.25, 0.04)),
    ])
    rgb = rgb * (1.0 - 0.72 * np.clip(crack, 0.0, 1.0))[..., None]
    alpha = np.clip(body, 0.0, 1.0)
    return rgb, alpha, (1.0, 0.55, 0.10)


_DEBRIS_BASES = [
    (0.52, 0.45, 0.38),
    (0.48, 0.46, 0.44),
    (0.56, 0.42, 0.33),
    (0.44, 0.42, 0.41),
]


def gen_debris(name, frame):
    """Escombro: polígono anguloso sólido de roca, luz desde arriba-izquierda.

    Es el único sprite con borde duro (sólido); el LANCZOS del reescalado
    aporta el antialias justo del contorno.
    """
    s = seed_for(name)                      # 4 formas distintas
    rl = random.Random(s)
    base = np.asarray(_DEBRIS_BASES[frame % 4])
    nv = rl.randint(6, 8)
    cx = cy = BIG / 2.0
    scale = BIG * 0.46
    verts = []
    for i in range(nv):
        a = i * TWO_PI / nv + rl.uniform(-0.35, 0.35) * TWO_PI / nv
        rad = rl.uniform(0.50, 0.95)
        verts.append((cx + math.cos(a) * rad * scale, cy + math.sin(a) * rad * scale))
    cxm = sum(v[0] for v in verts) / nv + rl.uniform(-10, 10)
    cym = sum(v[1] for v in verts) / nv + rl.uniform(-10, 10)

    light = (-0.62, -0.78)                  # hacia arriba-izquierda (y de pantalla)
    lnorm = math.hypot(*light)
    lx, ly = light[0] / lnorm, light[1] / lnorm

    img = Image.new("RGB", (BIG, BIG), tuple(int(b * 255 + 0.5) for b in base))
    draw = ImageDraw.Draw(img)
    mask = Image.new("L", (BIG, BIG), 0)
    ImageDraw.Draw(mask).polygon(verts, fill=255)
    for i in range(nv):                     # abanico de caras planas desde el centro
        v1, v2 = verts[i], verts[(i + 1) % nv]
        ex, ey = v2[0] - v1[0], v2[1] - v1[1]
        en = math.hypot(ex, ey) or 1.0
        nx, ny = ey / en, -ex / en
        mx, my = (v1[0] + v2[0]) / 2 - cxm, (v1[1] + v2[1]) / 2 - cym
        if nx * mx + ny * my < 0:
            nx, ny = -nx, -ny               # normal hacia fuera
        lit = max(0.0, nx * lx + ny * ly)
        shade = 0.50 + 0.55 * lit
        col = tuple(int(min(1.0, b * shade) * 255 + 0.5) for b in base)
        draw.polygon([v1, v2, (cxm, cym)], fill=col)

    rgb = np.asarray(img, dtype=np.float64) / 255.0
    alpha = np.asarray(mask, dtype=np.float64) / 255.0
    x, y = grid()
    tex = 0.90 + 0.20 * (fbm(x * 6.0 + 13.0, y * 6.0 + 7.0, 3, s + 5) - 0.5)
    rgb = np.clip(rgb * tex[..., None], 0.0, 1.0)
    return rgb, alpha, tuple(base)


def gen_pop_flash(name, frame):
    """Destello de pop de tótem: estrella de 8 puntas (dorada o verde)."""
    x, y = grid()
    r = np.hypot(x, y)
    a = spike(x, y, 0.0, 0.90, 0.050) + spike(x, y, math.pi / 2, 0.90, 0.050)
    a += 0.75 * (spike(x, y, math.pi / 4, 0.58, 0.045)
                 + spike(x, y, 3 * math.pi / 4, 0.58, 0.045))
    a += gauss(r, 0.14) + 0.30 * gauss(r, 0.45)
    alpha = np.clip(a, 0.0, 1.0)
    if frame == 0:
        stops = [
            (0.00, (1.00, 0.99, 0.90)),
            (0.12, (1.00, 0.92, 0.55)),
            (0.35, (1.00, 0.78, 0.25)),
            (1.50, (0.98, 0.62, 0.10)),
        ]
        fill = (1.00, 0.78, 0.25)
    else:
        stops = [
            (0.00, (0.93, 1.00, 0.92)),
            (0.12, (0.68, 1.00, 0.60)),
            (0.35, (0.40, 0.95, 0.38)),
            (1.50, (0.18, 0.85, 0.28)),
        ]
        fill = (0.40, 0.95, 0.38)
    rgb = ramp(r, stops)
    return rgb, alpha, fill


# ---------------------------------------------------------------------------
# Tabla de sprites y programa principal
# ---------------------------------------------------------------------------

SPRITES = (
    [(f"ash_{i}.png", gen_ash, i) for i in range(4)]
    + [(f"ember_{i}.png", gen_ember, i) for i in range(4)]
    + [(f"dust_{i}.png", gen_dust, i) for i in range(4)]
    + [(f"plasma_{i}.png", gen_plasma, i) for i in range(4)]
    + [(f"smoke_{i}.png", gen_smoke, i) for i in range(4)]
    + [("raindrop_0.png", gen_raindrop, 0)]
    + [(f"spray_{i}.png", gen_spray, i) for i in range(4)]
    + [(f"spark_{i}.png", gen_spark, i) for i in range(2)]
    + [(f"telegraph_{i}.png", gen_telegraph, i) for i in range(2)]
    + [(f"lava_glob_{i}.png", gen_lava_glob, i) for i in range(4)]
    + [(f"debris_{i}.png", gen_debris, i) for i in range(4)]
    + [(f"pop_flash_{i}.png", gen_pop_flash, i) for i in range(2)]
)


def main() -> int:
    print(f"Generando {len(SPRITES)} sprites en {OUT_DIR}")
    for name, fn, frame in SPRITES:
        rgb, alpha, fill = fn(name, frame)
        save_sprite(name, rgb, alpha, fill)
        print(f"  {name}")

    # Verificación: existen, 64x64, RGBA y no vacíos (algún pixel alpha > 0)
    ok = True
    for name, _, _ in SPRITES:
        p = OUT_DIR / name
        if not p.is_file():
            print(f"FALTA: {p}")
            ok = False
            continue
        with Image.open(p) as im:
            good = im.size == (SIZE, SIZE) and im.mode == "RGBA"
            visible = int(np.asarray(im)[..., 3].max()) > 0
        if not (good and visible):
            print(f"INVALIDO: {name} size={im.size} mode={im.mode} visible={visible}")
            ok = False
    print("Verificacion: OK" if ok else "Verificacion: FALLO")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
