#!/usr/bin/env python3
"""Iron Tempest — generador de geometría de entidades + texturas pintadas.

Fuente única de verdad: SPECS define cada modelo (partes, cubos, materiales).
Emite:
  - src/main/java/com/vvrgs/irontempest/client/model/geom/<Name>Geometry.java
  - src/main/resources/assets/irontempest/textures/entity/<tex>.png (+ _glow.png)
Convención: unidades de modelo vanilla (16 u = 1 bloque), +Y hacia abajo,
frente del vehículo = -Z, suelo en y=24 para vehículos terrestres.
"""
import os, math, zlib
import numpy as np
from PIL import Image

# Supersampling ACTUAL (por-spec, fijado por paint()): el atlas lógico (UVs,
# texOffs, LayerDefinition) NO cambia — solo el lienzo de píxeles es size*SS.
# Los painters leen SS para que el detalle escale (grosores, pasos, ruido).
SS = 1

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_DIR = os.path.join(ROOT, "src/main/java/com/vvrgs/irontempest/client/model/geom")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/irontempest/textures/entity")
MISC_DIR = os.path.join(ROOT, "src/main/resources/assets/irontempest/textures/misc")

# ---------------------------------------------------------------- pixel font 3x5
FONT = {
    "0": ["111","101","101","101","111"], "1": ["010","110","010","010","111"],
    "2": ["111","001","111","100","111"], "3": ["111","001","111","001","111"],
    "4": ["101","101","111","001","001"], "5": ["111","100","111","001","111"],
    "7": ["111","001","010","010","010"], "9": ["111","101","111","001","111"],
    "A": ["010","101","111","101","101"], "E": ["111","100","111","100","111"],
    "G": ["111","100","101","101","111"], "I": ["111","010","010","010","111"],
    "M": ["101","111","111","101","101"], "N": ["101","111","111","111","101"],
    "O": ["111","101","101","101","111"], "P": ["111","101","111","100","100"],
    "R": ["111","101","111","110","101"], "S": ["111","100","111","001","111"],
    "T": ["111","010","010","010","010"], "V": ["101","101","101","101","010"],
    "-": ["000","000","111","000","000"],
    "6": ["111","100","111","101","111"],
    "8": ["111","101","111","101","111"], "B": ["110","101","110","101","110"],
    "C": ["111","100","100","100","111"], "D": ["110","101","101","101","110"],
    "F": ["111","100","111","100","100"], "H": ["101","101","111","101","101"],
    "K": ["101","110","100","110","101"], "L": ["100","100","100","100","111"],
    "U": ["101","101","101","101","111"], "W": ["101","101","111","111","101"],
    "X": ["101","101","010","101","101"], "Y": ["101","101","010","010","010"],
    "Z": ["111","001","010","100","111"],
}

# Estencils premium: estrella táctica y triángulo de eyección/peligro.
STAR = [
    "000010000",
    "000111000",
    "001111100",
    "111111111",
    "011111110",
    "001111100",
    "011101110",
    "110000011",
]
TRI = ["00100", "01110", "11111"]

def draw_bitmap(arr, x, y, rows, color, scale=1):
    h, w = arr.shape[:2]
    for r, row in enumerate(rows):
        for c, bit in enumerate(row):
            if bit == "1":
                ys, xs = y + r * scale, x + c * scale
                if 0 <= ys and ys + scale <= h and 0 <= xs and xs + scale <= w:
                    arr[ys:ys + scale, xs:xs + scale, :3] = color
                    arr[ys:ys + scale, xs:xs + scale, 3] = 255

def draw_text(arr, x, y, text, color, scale=1):
    for ch in text:
        g = FONT.get(ch)
        if g is not None:
            draw_bitmap(arr, x, y, g, color, scale)
        x += 4 * scale

# ---------------------------------------------------------------- value noise
def value_noise(h, w, scale, seed, octaves=2):
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        gh, gw = max(2, int(h / scale) << o), max(2, int(w / scale) << o)
        grid = rng.random((gh + 1, gw + 1))
        ys = np.linspace(0, gh, h, endpoint=False)
        xs = np.linspace(0, gw, w, endpoint=False)
        y0 = ys.astype(int); x0 = xs.astype(int)
        fy = (ys - y0)[:, None]; fx = (xs - x0)[None, :]
        fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
        a = grid[y0][:, x0]; b = grid[y0][:, x0 + 1]
        c = grid[y0 + 1][:, x0]; d = grid[y0 + 1][:, x0 + 1]
        out += amp * ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy)
        total += amp; amp *= 0.5
    return out / total

# ---------------------------------------------------------------- painters
# Todos los painters leen el SS global: los grosores/pasos están en unidades
# LÓGICAS y se multiplican por SS para conservar la escala visual en HD.
def _base(rect_shape, color, noise_amt, scale, seed):
    h, w = rect_shape
    n = value_noise(h, w, scale * SS, seed) - 0.5
    col = np.zeros((h, w, 4))
    for i in range(3):
        col[:, :, i] = np.clip(color[i] + n * 255 * noise_amt, 0, 255)
    col[:, :, 3] = 255
    return col

def _ao_soft(col):
    """AO horneada suave: sombra asimétrica (más abajo) con falloff smoothstep."""
    h, w = col.shape[:2]
    r = max(1, 2 * SS)
    if h < 2 * r + 1 or w < 2 * r + 1:
        if h >= 3 and w >= 3:  # caras diminutas: borde clásico de 1 px
            col[0, :, :3] *= 0.82; col[-1, :, :3] *= 0.72
            col[:, 0, :3] *= 0.85; col[:, -1, :3] *= 0.85
        return col
    yy = np.arange(h, dtype=float)[:, None]
    xx = np.arange(w, dtype=float)[None, :]
    dt = _smoothstep(0.0, r, yy)
    db = _smoothstep(0.0, r, (h - 1) - yy)
    dl = _smoothstep(0.0, r, xx)
    dr = _smoothstep(0.0, r, (w - 1) - xx)
    shade = (0.84 + 0.16 * dt) * (0.68 + 0.32 * db) * (0.86 + 0.14 * dl) * (0.86 + 0.14 * dr)
    col[:, :, :3] *= shade[:, :, None]
    return col

def _vgrad(col, top=1.06, bottom=0.78):
    h = col.shape[0]
    g = np.linspace(top, bottom, h)[:, None, None]
    col[:, :, :3] = np.clip(col[:, :, :3] * g, 0, 255)
    return col

def _rivets(col, spacing=6, color_mul=1.35, seed=1):
    """Remaches como domos: brillo arriba, sombra abajo (tamaño SS)."""
    h, w = col.shape[:2]
    sp = spacing * SS
    r = max(1, SS)
    for y in range(2 * SS, h - 2 * r, sp):
        for x in range(2 * SS, w - r, sp):
            col[y:y + r, x:x + r, :3] = np.clip(col[y:y + r, x:x + r, :3] * color_mul, 0, 255)
            col[y + r:y + 2 * r, x:x + r, :3] *= 0.8
    return col

def _panel_lines(col, step_y=8, step_x=10, dark=0.86, seed=0):
    """Paneles BISELADOS (línea oscura + línea clara) con rejilla pseudoaleatoria
    y remaches en las intersecciones — se lee como chapa real, no como rejilla."""
    h, w = col.shape[:2]
    rng = np.random.default_rng(seed * 7 + 5)
    light = 1.0 + (1.0 - dark) * 0.6
    ys = []
    y = step_y * SS
    while y < h - SS:
        ys.append(y)
        y += max(SS * 2, int(step_y * SS * (0.75 + rng.random() * 0.6)))
    xs = []
    x = step_x * SS
    while x < w - SS:
        xs.append(x)
        x += max(SS * 2, int(step_x * SS * (0.75 + rng.random() * 0.6)))
    for y in ys:
        col[y:y + SS, :, :3] *= dark
        if y + 2 * SS <= h:
            col[y + SS:y + 2 * SS, :, :3] = np.clip(col[y + SS:y + 2 * SS, :, :3] * light, 0, 255)
    for x in xs:
        col[:, x:x + SS, :3] *= dark
        if x + 2 * SS <= w:
            col[:, x + SS:x + 2 * SS, :3] = np.clip(col[:, x + SS:x + 2 * SS, :3] * light, 0, 255)
    for y in ys:
        for x in xs:
            if y - SS >= 0 and x - SS >= 0:
                col[y - SS:y, x - SS:x, :3] = np.clip(col[y - SS:y, x - SS:x, :3] * 1.3, 0, 255)
    return col

def _brushed(col, seed, strength=0.10):
    """Metal cepillado: vetas anisotrópicas a lo largo del eje mayor de la cara."""
    h, w = col.shape[:2]
    if h < 3 or w < 3:
        return col
    if w >= h:
        n = value_noise(h, 1, max(2, 2 * SS), seed + 808, 2)
        n = np.repeat(n, w, axis=1)
    else:
        n = value_noise(1, w, max(2, 2 * SS), seed + 808, 2)
        n = np.repeat(n, h, axis=0)
    jit = value_noise(h, w, max(2, SS), seed + 809, 1)
    band = (n - 0.5) * 2.0 * strength + (jit - 0.5) * strength * 0.5
    col[:, :, :3] = np.clip(col[:, :, :3] * (1.0 + band[:, :, None]), 0, 255)
    return col

# ---------------------------------------------------------------- weathering v2
def _smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / max(1e-6, (edge1 - edge0)), 0.0, 1.0)
    return t * t * (3 - 2 * t)

def _edge_wear(col, seed, amount=0.35):
    """Metal claro asomando en bordes/aristas donde la pintura se desgasta."""
    h, w = col.shape[:2]
    if h < 4 * SS or w < 4 * SS:
        return col
    rng = np.random.default_rng(seed * 31 + 7)
    n = value_noise(h, w, 3 * SS, seed + 991, 2)
    border = np.zeros((h, w), dtype=bool)
    t = max(1, SS)
    border[:t, :] = border[-t:, :] = True
    border[:, :t] = border[:, -t:] = True
    wear = border & (n > 1.0 - amount * 0.55)
    col[:, :, :3][wear] = np.array([148, 150, 152]) + rng.normal(0, 8, (wear.sum(), 3))
    return col

def _rust(col, seed, amount=0.12):
    """Óxido multi-escala: mancha grande + picado fino + borde oscurecido."""
    h, w = col.shape[:2]
    if h < 5 * SS:
        return col
    big = value_noise(h, w, 6 * SS, seed + 1234, 2)
    fine = value_noise(h, w, max(2, 2 * SS), seed + 4321, 2)
    bias = np.linspace(0.0, 1.0, h)[:, None]  # más abajo, más óxido
    mask = (big * (0.4 + 0.6 * bias)) > (1.0 - amount)
    pit = mask & (fine > 0.62)
    rim = np.zeros_like(mask)
    for dy in (-SS, 0, SS):
        for dx in (-SS, 0, SS):
            rim |= np.roll(np.roll(mask, dy, 0), dx, 1)
    rim &= ~mask
    tint = np.array([96, 52, 30])
    col[:, :, :3][mask] = col[:, :, :3][mask] * 0.35 + tint * 0.65
    col[:, :, :3][pit] = col[:, :, :3][pit] * 0.5 + np.array([60, 30, 18]) * 0.5
    col[:, :, :3][rim] *= 0.82
    return col

def _dirt_gradient(col, strength=0.22):
    """Suciedad marrón acumulada hacia abajo (solo caras laterales)."""
    h = col.shape[0]
    g = _smoothstep(0.45, 1.0, np.linspace(0.0, 1.0, h))[:, None, None]
    dirt = np.array([70, 58, 40])
    col[:, :, :3] = col[:, :, :3] * (1 - g * strength) + dirt * (g * strength)
    return col

def _streaks(col, seed, amount=0.5):
    """Chorretones verticales de lluvia/hollín desde el borde superior."""
    h, w = col.shape[:2]
    if h < 6 * SS or w < 4 * SS:
        return col
    rng = np.random.default_rng(seed * 17 + 3)
    for _ in range(max(1, int(w * amount / (6 * SS)))):
        x = int(rng.integers(1, max(2, w - SS)))
        length = int(rng.integers(h // 3, h - 1))
        fade = np.linspace(0.78, 1.0, length)[:, None, None]
        col[0:length, x:x + SS, :3] = col[0:length, x:x + SS, :3] * fade
    return col

def _chips(col, seed, amount=0.05):
    """Pintura saltada: mota oscura con centro de metal brillante (tamaño SS)."""
    h, w = col.shape[:2]
    if h < 4 * SS or w < 4 * SS:
        return col
    rng = np.random.default_rng(seed * 13 + 29)
    n_chips = int(h * w * amount / (12 * SS * SS))
    for _ in range(n_chips):
        y, x = int(rng.integers(1, h - SS)), int(rng.integers(1, w - SS))
        col[y:y + SS, x:x + SS, :3] *= 0.45
        if rng.random() < 0.5:
            col[y:y + SS, x:x + SS, :3] = (150, 152, 155)
    return col

def _weather_armor(col, face, seed):
    """Pipeline completo para blindaje pintado (camo/skirt/metal)."""
    _chips(col, seed)
    _edge_wear(col, seed)
    if face in ("side1", "side2", "front", "back"):
        _streaks(col, seed, 0.4)
        _dirt_gradient(col, 0.20)
        _rust(col, seed, 0.10)
    if face == "down":
        col[:, :, :3] *= 0.6
    return col

def paint_face(mat, face, hw, seed, model):
    """face: up/down/front/back/side1/side2 ; hw: (h_px, w_px). Devuelve (col, glow|None)."""
    h, w = hw
    glow = None
    if mat == "camo":
        # Camo 3 tonos con máscaras SUAVES (smoothstep) — nada de bordes duros.
        col = _base((h, w), (86, 96, 62), 0.08, 5, seed)
        n2 = value_noise(h, w, 4, seed + 77, 3)
        dark = np.array([56, 69, 43], dtype=float)
        brown = np.array([97, 84, 58], dtype=float)
        m_dark = _smoothstep(0.58, 0.70, n2)[:, :, None]
        m_brown = _smoothstep(0.40, 0.28, n2)[:, :, None]
        col[:, :, :3] = col[:, :, :3] * (1 - m_dark) + dark * m_dark
        col[:, :, :3] = col[:, :, :3] * (1 - m_brown) + brown * m_brown
        if face == "up":
            col[:, :, :3] = np.clip(col[:, :, :3] * 1.12, 0, 255)
        _rivets(col, 7, seed=seed)
        _weather_armor(col, face, seed)
        _ao_soft(col)
    elif mat == "metal_dark":
        col = _base((h, w), (52, 54, 58), 0.08, 4, seed); _vgrad(col)
        _brushed(col, seed, 0.08)
        _chips(col, seed, 0.03); _edge_wear(col, seed, 0.25); _ao_soft(col)
    elif mat == "track":
        # Caras largas: el detalle completo (ruido+barra+barro) se pinta en UNA
        # tira de EXACTAMENTE 3·SS px y se tesela → la textura es 3-periódica en
        # u y el scroll sawtooth de la cinta wrappea SIN shimmer. AO/desgaste
        # solo en el eje v (los bordes u romperían la periodicidad).
        if w >= h:
            period = 3 * SS
            strip = _base((h, period), (44, 42, 40), 0.10, 3, seed)
            strip[:, 0:SS, :3] *= 0.55       # barra de rodadura
            strip[:, SS:2 * SS, :3] *= 1.25  # brillo tras la barra
            n3 = value_noise(h, period, max(2, 2 * SS), seed + 555, 2)
            bias = np.linspace(0.0, 1.0, h)[:, None]
            mud = (n3 * bias) > 0.55
            strip[:, :, :3][mud] = strip[:, :, :3][mud] * 0.4 + np.array([84, 68, 46]) * 0.6
            reps = w // period + 1
            col = np.tile(strip, (1, reps, 1))[:, :w, :].copy()
            col[0:SS, :, :3] *= 0.82
            col[h - SS:h, :, :3] *= 0.72
        else:
            col = _base((h, w), (44, 42, 40), 0.10, 3, seed)
            for y in range(0, h, 3 * SS):
                col[y:y + SS, :, :3] *= 0.55
            n3 = value_noise(h, w, 3 * SS, seed + 555, 2)
            bias = np.linspace(0.0, 1.0, h)[:, None]
            mud = (n3 * bias) > 0.55
            col[:, :, :3][mud] = col[:, :, :3][mud] * 0.4 + np.array([84, 68, 46]) * 0.6
            _edge_wear(col, seed, 0.4)
            _ao_soft(col)
    elif mat == "wheel":
        col = _base((h, w), (58, 60, 56), 0.08, 3, seed)
        yy, xx = np.mgrid[0:h, 0:w]
        cy, cx = (h - 1) / 2, (w - 1) / 2
        r = np.sqrt(((yy - cy) / max(cy, 1)) ** 2 + ((xx - cx) / max(cx, 1)) ** 2)
        col[:, :, :3][r < 0.35] = (30, 30, 30)
        col[:, :, :3][(r > 0.8) & (r <= 1.0)] = (38, 38, 36)
        col[:, :, 3] = 255
    elif mat == "skirt":
        col = _base((h, w), (74, 82, 55), 0.12, 4, seed)
        for x in range(0, w, 8 * SS):
            col[:, x:x + SS, :3] *= 0.8
        _vgrad(col, 1.0, 0.7)
        _weather_armor(col, "side1", seed)
        _ao_soft(col)
    elif mat == "barrel":
        col = _base((h, w), (40, 42, 46), 0.06, 3, seed); _vgrad(col, 1.1, 0.85)
        _brushed(col, seed, 0.09)
        if face in ("side1", "side2", "up", "down") and w > 12 * SS:
            col[:, 2 * SS:4 * SS, :3] = (200, 200, 195)   # anillos de derribo
            col[:, 5 * SS:7 * SS, :3] = (200, 200, 195)
            col[:, w // 2:w // 2 + 4 * SS, :3] *= 0.8  # funda térmica
        _ao_soft(col)
    elif mat == "glass_glow":
        col = _base((h, w), (255, 244, 180), 0.04, 2, seed)
        glow = col.copy()
    elif mat == "drum":
        col = _base((h, w), (72, 78, 52), 0.08, 3, seed)
        if w > 6 * SS:
            col[:, w // 3:w // 3 + SS, :3] *= 0.7
            col[:, 2 * w // 3:2 * w // 3 + SS, :3] *= 0.7
        _ao_soft(col)
    elif mat == "missile_body":
        col = _base((h, w), (198, 200, 204), 0.05, 5, seed)
        _panel_lines(col, 8, 10, 0.92, seed)
        # Remaches finos a lo largo de las líneas de panel + quemado leve de cola.
        _rivets(col, 8, 1.15, seed)
        if w > 16 * SS:
            burn = _smoothstep(0.75, 1.0, np.linspace(0.0, 1.0, w))[None, :, None]
            col[:, :, :3] = col[:, :, :3] * (1 - burn * 0.35)
        _vgrad(col, 1.05, 0.88); _ao_soft(col)
    elif mat == "nose":
        col = _base((h, w), (48, 50, 56), 0.05, 3, seed)
        _brushed(col, seed, 0.07); _ao_soft(col)
    elif mat == "fin":
        col = _base((h, w), (170, 172, 178), 0.06, 3, seed)
        col[0:SS, :, :3] = (200, 60, 50)  # borde de ataque rojo
        _ao_soft(col)
    elif mat == "nozzle":
        col = _base((h, w), (60, 56, 52), 0.10, 2, seed); _ao_soft(col)
    elif mat == "nozzle_glow":
        col = _base((h, w), (255, 160, 70), 0.06, 2, seed)
        glow = col.copy()
    elif mat == "rocket_body":
        col = _base((h, w), (96, 104, 70), 0.08, 3, seed)
        if h > 6 * SS:
            col[SS:3 * SS, :, :3] = (208, 172, 60)  # banda de ojiva
        _ao_soft(col)
    elif mat == "ship_hull":
        col = _base((h, w), (58, 66, 82), 0.07, 6, seed)
        _panel_lines(col, 6, 8, 0.88, seed)
        # Sub-paneles aleatorios con valor distinto (casco por placas).
        n2 = value_noise(h, w, 6 * SS, seed + 5)
        col[:, :, :3][n2 > 0.72] *= 1.15
        col[:, :, :3][n2 < 0.25] *= 0.88
        # Quemaduras de reentrada/micrometeoritos: motas oscuras dispersas.
        _chips(col, seed, 0.03)
        _streaks(col, seed, 0.25)
        _vgrad(col, 1.05, 0.85); _ao_soft(col)
    elif mat == "ship_dark":
        col = _base((h, w), (36, 40, 52), 0.06, 4, seed)
        _panel_lines(col, 5, 7, 0.85, seed)
        _brushed(col, seed, 0.05)
        _ao_soft(col)
    elif mat == "engine_glow":
        yy, xx = np.mgrid[0:h, 0:w]
        cy, cx = (h - 1) / 2, (w - 1) / 2
        r = np.sqrt(((yy - cy) / max(cy, 1)) ** 2 + ((xx - cx) / max(cx, 1)) ** 2)
        col = np.zeros((h, w, 4))
        core = np.clip(1.2 - r, 0, 1) ** 1.5
        col[:, :, 0] = 120 + 135 * core
        col[:, :, 1] = 210 + 45 * core
        col[:, :, 2] = 255
        col[:, :, 3] = 255
        glow = col.copy()
    elif mat == "window_glow":
        col = _base((h, w), (40, 46, 60), 0.05, 3, seed)
        if h >= 3 * SS:
            row = h // 3
            col[row:row + SS, :, :3] = (150, 230, 255)
            if w > 4 * SS:
                for x in range(0, w, 3 * SS):
                    col[row:row + SS, x:x + SS, :3] = (40, 46, 60)
        glow = np.zeros((h, w, 4))
        mask = (col[:, :, 2] > 200)
        glow[mask] = col[mask]
        _ao_soft(col)
    elif mat == "brass":
        col = _base((h, w), (168, 132, 62), 0.08, 2, seed)
        _brushed(col, seed, 0.12)
        if w > 4 * SS:
            col[:, -2 * SS:, :3] = (150, 90, 50)  # banda de forzamiento
        _ao_soft(col)
    elif mat == "era":
        # Ladrillo de blindaje reactivo: placa con correas en X y tornillos.
        col = _base((h, w), (66, 76, 50), 0.06, 2, seed)
        if h >= 4 * SS and w >= 4 * SS:
            m = np.zeros((h, w), dtype=bool)
            for i in range(min(h, w)):
                y1 = int(i * (h - 1) / max(1, min(h, w) - 1))
                x1 = int(i * (w - 1) / max(1, min(h, w) - 1))
                m[y1:min(h, y1 + SS), x1:min(w, x1 + SS)] = True
                y2 = h - 1 - y1
                m[max(0, y2 - SS + 1):y2 + 1, x1:min(w, x1 + SS)] = True
            col[:, :, :3][m] *= 0.7
            for cy, cx in ((0, 0), (0, w - SS), (h - SS, 0), (h - SS, w - SS)):
                col[cy:cy + SS, cx:cx + SS, :3] = (140, 142, 138)
        _chips(col, seed, 0.04)
        _ao_soft(col)
    elif mat == "grille":
        col = _base((h, w), (40, 42, 44), 0.05, 2, seed)
        step = 2 * SS
        if w >= h:
            for x in range(0, w, step):
                col[:, x:x + SS, :3] *= 0.45
        else:
            for y in range(0, h, step):
                col[y:y + SS, :, :3] *= 0.45
        _ao_soft(col)
    elif mat == "glass_dark":
        col = _base((h, w), (28, 36, 44), 0.04, 2, seed)
        if h >= 2 * SS and w >= 2 * SS:
            col[0:SS, :, :3] = np.clip(col[0:SS, :, :3] * 1.9, 0, 255)  # reflejo superior
        _ao_soft(col)
    elif mat == "rubber":
        col = _base((h, w), (30, 30, 32), 0.06, 2, seed)
        _dirt_gradient(col, 0.3)
        _ao_soft(col)
    elif mat == "cable":
        col = _base((h, w), (46, 44, 40), 0.10, 1, seed)
        if w >= 4 * SS:
            for x in range(0, w, 2 * SS):
                col[:, x:x + SS, :3] *= 0.7  # trenzado
        _ao_soft(col)
    elif mat == "can":
        col = _base((h, w), (88, 96, 62), 0.07, 2, seed)
        if h >= 5 * SS and w >= 5 * SS:
            cy, cx2 = h // 2, w // 2
            r2 = min(h, w) // 3
            m = np.zeros((h, w), dtype=bool)
            for i in range(-r2, r2 + 1):  # estampado en X del bidón
                if 0 <= cy + i < h - SS + 1 and 0 <= cx2 + i < w - SS + 1:
                    m[cy + i:cy + i + SS, cx2 + i:cx2 + i + SS] = True
                if 0 <= cy + i < h - SS + 1 and 0 <= cx2 - i < w - SS + 1:
                    m[cy + i:cy + i + SS, cx2 - i:cx2 - i + SS] = True
            col[:, :, :3][m] *= 0.75
        _chips(col, seed, 0.05)
        _ao_soft(col)
    elif mat == "hazard":
        # Franjas diagonales amarillas/negras.
        col = np.zeros((h, w, 4))
        yy2, xx2 = np.mgrid[0:h, 0:w]
        stripe = ((yy2 + xx2) // (3 * SS)) % 2 == 0
        col[:, :, :3][stripe] = (208, 172, 60)
        col[:, :, :3][~stripe] = (34, 34, 36)
        col[:, :, 3] = 255
        _dirt_gradient(col, 0.15)
        _ao_soft(col)
    elif mat == "missile_tiny":
        col = _base((h, w), (176, 178, 182), 0.05, 2, seed)
        if w >= 6 * SS:
            col[:, 0:2 * SS, :3] = (190, 55, 45)  # punta roja
        _ao_soft(col)
    elif mat == "sensor_glow":
        # Punta de sensor del misil: rojo encendido (emisivo).
        col = _base((h, w), (225, 60, 50), 0.05, 2, seed)
        glow = col.copy()
    elif mat == "tail_glow":
        # Luces traseras del tanque: rojo intenso (emisivo).
        col = _base((h, w), (210, 40, 35), 0.05, 2, seed)
        glow = col.copy()
    else:
        col = _base((h, w), (120, 120, 120), 0.05, 3, seed)
    # decals específicos (guards en píxeles REALES: los umbrales lógicos ×SS)
    if model == "tank" and mat == "camo" and face in ("side1", "side2") and w >= 20 * SS and h >= 7 * SS:
        draw_text(col, w // 2 - 3 * SS, h // 2 - 2 * SS, "07", (230, 228, 220), SS)
        if w >= 34 * SS and h >= 12 * SS:
            draw_bitmap(col, w // 2 + 8 * SS, h // 2 - 3 * SS, STAR, (200, 60, 50), SS)
    if model == "tank" and mat == "camo" and face == "up" and w >= 20 * SS and h >= 20 * SS:
        # Triángulos de eyección junto a las escotillas.
        draw_bitmap(col, 3 * SS, 3 * SS, TRI, (208, 172, 60), SS)
        draw_bitmap(col, w - 8 * SS, 3 * SS, TRI, (208, 172, 60), SS)
    if model == "cruise_missile" and mat == "missile_body" and face in ("side1", "side2", "up") and w >= 34 * SS:
        col[:, 4 * SS:7 * SS, :3] = (190, 55, 45)  # banda roja
        draw_text(col, 10 * SS, max(0, h // 2 - 2 * SS), "VRGS-1", (40, 40, 44), SS)
        draw_bitmap(col, w - 10 * SS, max(0, h // 2 - SS), TRI, (208, 172, 60), SS)
    if model == "warship" and mat == "ship_hull" and face in ("side1", "side2") and w >= 40 * SS and h >= 8 * SS:
        draw_text(col, 6 * SS, h // 2 - 2 * SS, "TEMPEST", (140, 200, 230), SS)
    if model == "warship" and mat == "ship_hull" and face == "up" and w >= 30 * SS and h >= 14 * SS:
        # Marcas de cubierta en las alas: numeral + NO STEP.
        draw_text(col, 3 * SS, 3 * SS, "IT-77", (140, 200, 230), SS)
        draw_text(col, 3 * SS, h - 9 * SS, "NO STEP", (208, 172, 60), SS)
    return col, glow

# ---------------------------------------------------------------- specs
def P(name, pivot, cubes, parent=None, rot=(0, 0, 0)):
    return {"name": name, "pivot": pivot, "rot": rot, "cubes": cubes, "parent": parent}

def C(o, s, mat):
    return {"o": o, "s": s, "mat": mat}

SPECS = {
    "tank": {
        "tex": "tank", "tex_size": 512, "ss": 2, "glow": True,
        "parts": [
            P("hull", (0, 24, 0), [
                C((-13, -17, -28), (26, 9, 56), "camo"),
                C((-13, -19, 4), (26, 2, 22), "camo"),
                C((-4, -20, 14), (3, 1, 8), "metal_dark"),   # escape L
                C((1, -20, 14), (3, 1, 8), "metal_dark"),    # escape R
                C((-14, -15, -24), (1, 5, 48), "camo"),      # bisel lateral L
                C((13, -15, -24), (1, 5, 48), "camo"),       # bisel lateral R
                C((-10, -19.4, 6), (8, 1, 8), "grille"),     # rejilla motor L
                C((2, -19.4, 6), (8, 1, 8), "grille"),       # rejilla motor R
                C((-9, -19.4, 16), (18, 1, 6), "grille"),    # rejilla radiador
                C((-3, -17.6, -24), (6, 1, 6), "metal_dark"),# escotilla conductor
                C((-10, -14, -29.5), (2, 3, 2), "metal_dark"),# gancho remolque L
                C((8, -14, -29.5), (2, 3, 2), "metal_dark"), # gancho remolque R
                C((-25, -15.5, -33), (11, 1, 4), "rubber"),  # guardabarros FL
                C((14, -15.5, -33), (11, 1, 4), "rubber"),   # guardabarros FR
                C((-25, -15.5, 29), (11, 1, 4), "rubber"),   # guardabarros RL
                C((14, -15.5, 29), (11, 1, 4), "rubber"),    # guardabarros RR
                C((13.2, -13, -6), (2, 4, 14), "metal_dark"),# caja de herramientas
                C((-13.8, -15, -20), (1, 1, 34), "cable"),   # cable de remolque
                C((-12, -16, 27.5), (2, 1, 1), "tail_glow"), # luz trasera L
                C((10, -16, 27.5), (2, 1, 1), "tail_glow"),  # luz trasera R
            ]),
            P("cans", (-14, 8, 18), [
                C((-1.5, -3, -3), (3, 6, 4), "can"),
                C((-1.5, -3, 2), (3, 6, 4), "can"),
            ], rot=(0, 4, 0)),
            P("glacis", (0, 8, -28), [
                C((-13, -1, -14), (26, 2, 14), "camo"),
                # Ladrillos ERA en dos filas sobre el glacis
                C((-12, -3, -13), (4, 2, 5), "era"), C((-7, -3, -13), (4, 2, 5), "era"),
                C((-2, -3, -13), (4, 2, 5), "era"), C((3, -3, -13), (4, 2, 5), "era"),
                C((8, -3, -13), (4, 2, 5), "era"),
                C((-12, -3, -7), (4, 2, 5), "era"), C((-7, -3, -7), (4, 2, 5), "era"),
                C((-2, -3, -7), (4, 2, 5), "era"), C((3, -3, -7), (4, 2, 5), "era"),
                C((8, -3, -7), (4, 2, 5), "era"),
                # Eslabones de oruga de repuesto
                C((-10, -4.2, -1.5), (6, 1, 4), "track"),
                C((4, -4.2, -1.5), (6, 1, 4), "track"),
            ], parent="hull", rot=(38, 0, 0)),
            P("rear_plate", (0, 7, 28), [C((-13, -1, 0), (26, 2, 10), "camo")], rot=(-30, 0, 0)),
            P("plow", (0, 18, -30), [
                C((-11, -6, -2), (22, 8, 2), "metal_dark"),
                C((-10, 1.5, -3), (2, 1, 2), "metal_dark"), C((-5.5, 1.5, -3), (2, 1, 2), "metal_dark"),
                C((-1, 1.5, -3), (2, 1, 2), "metal_dark"), C((3.5, 1.5, -3), (2, 1, 2), "metal_dark"),
                C((8, 1.5, -3), (2, 1, 2), "metal_dark"),
            ], rot=(-15, 0, 0)),
            P("headlight_l", (9, 8.5, -29), [C((-1.5, -1, -1), (3, 2, 1), "glass_glow")]),
            P("headlight_r", (-9, 8.5, -29), [C((-1.5, -1, -1), (3, 2, 1), "glass_glow")]),
            P("drum_l", (-8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")]),
            P("drum_l_x", (-8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")], rot=(0, 0, 45)),
            P("drum_r", (8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")]),
            P("drum_r_x", (8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")], rot=(0, 0, 45)),
            P("track_l", (0, 24, 0), [
                C((25, -15, -26), (1, 7, 52), "skirt"),
                C((15, -11, -33), (9, 9, 3), "metal_dark"),   # rueda motriz
                C((15, -10, 28), (9, 9, 3), "metal_dark"),    # rueda tensora
                C((17, -9, -16), (5, 3, 3), "metal_dark"),    # rodillo de retorno 1
                C((17, -9, -1), (5, 3, 3), "metal_dark"),     # rodillo de retorno 2
                C((17, -9, 13), (5, 3, 3), "metal_dark"),     # rodillo de retorno 3
                C((25.4, -16, -20), (1, 2, 2), "metal_dark"), # bisagras del faldón
                C((25.4, -16, -8), (1, 2, 2), "metal_dark"),
                C((25.4, -16, 4), (1, 2, 2), "metal_dark"),
                C((25.4, -16, 16), (1, 2, 2), "metal_dark"),
            ]),
            # Cinta de oruga en parte PROPIA: el renderer la desplaza en Z para
            # el scroll fake (el material track es periódico cada 3 unidades).
            P("belt_l", (0, 24, 0), [C((14, -13, -30), (11, 13, 60), "track")], parent="track_l_rel"),
            P("wheel_l0", (19.5, 18, -22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l1", (19.5, 18, -11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l2", (19.5, 18, 0), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l3", (19.5, 18, 11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l4", (19.5, 18, 22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("track_r", (0, 24, 0), [
                C((-26, -15, -26), (1, 7, 52), "skirt"),
                C((-24, -11, -33), (9, 9, 3), "metal_dark"),
                C((-24, -10, 28), (9, 9, 3), "metal_dark"),
                C((-22, -9, -16), (5, 3, 3), "metal_dark"),
                C((-22, -9, -1), (5, 3, 3), "metal_dark"),
                C((-22, -9, 13), (5, 3, 3), "metal_dark"),
                C((-26.4, -16, -20), (1, 2, 2), "metal_dark"),
                C((-26.4, -16, -8), (1, 2, 2), "metal_dark"),
                C((-26.4, -16, 4), (1, 2, 2), "metal_dark"),
                C((-26.4, -16, 16), (1, 2, 2), "metal_dark"),
            ]),
            P("belt_r", (0, 24, 0), [C((-25, -13, -30), (11, 13, 60), "track")], parent="track_r_rel"),
            P("wheel_r0", (-19.5, 18, -22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r1", (-19.5, 18, -11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r2", (-19.5, 18, 0), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r3", (-19.5, 18, 11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r4", (-19.5, 18, 22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("turret", (0, 7, -2), [
                C((-11, -7, -13), (22, 7, 26), "camo"),
                C((-9, -2, 13), (18, 4, 4), "metal_dark"),   # cesta trasera
                C((-9, -6, -15), (18, 5, 2), "camo"),        # cuña frontal
                # ERA lateral: 3 bloques por lado
                C((-12, -6, -10), (1, 4, 6), "era"), C((-12, -6, -3), (1, 4, 6), "era"),
                C((-12, -6, 4), (1, 4, 6), "era"),
                C((11, -6, -10), (1, 4, 6), "era"), C((11, -6, -3), (1, 4, 6), "era"),
                C((11, -6, 4), (1, 4, 6), "era"),
                C((-7, -9, -11), (4, 2, 5), "metal_dark"),   # mira del artillero
                C((2, -10, -12), (5, 3, 4), "metal_dark"),   # foco IR
                C((0, -13, 10), (1, 4, 1), "metal_dark"),    # sensor de viento
                C((-10, -8, -2), (1, 1, 3), "metal_dark"),   # gancho de izado L
                C((9, -8, -2), (1, 1, 3), "metal_dark"),     # gancho de izado R
                C((-11, -5, 9), (2, 4, 6), "metal_dark"),    # estiba L
                C((9, -5, 9), (2, 4, 6), "metal_dark"),      # estiba R
                C((-4, -6, 17.2), (8, 8, 1), "wheel"),       # rueda de repuesto
            ]),
            P("snorkel", (9, 3, 10), [C((-1, -14, -1), (2, 14, 2), "metal_dark")],
              parent="turret", rot=(-12, 0, -6)),
            P("barrel", (0, -3.5, -13), [
                C((-3, -2.5, -3), (6, 5, 3), "metal_dark"),
                C((-1.5, -1.5, -27), (3, 3, 24), "barrel"),
                C((-2, -2, -31), (4, 4, 4), "barrel"),
                C((-2, -2, -17), (4, 4, 6), "barrel"),       # extractor de humos
                C((-2, -2, -10), (4, 4, 1), "metal_dark"),   # anillo funda térmica 1
                C((-2, -2, -23), (4, 4, 1), "metal_dark"),   # anillo funda térmica 2
                C((1.5, -2.8, -29), (1, 1, 2), "metal_dark"),# sensor de referencia
            ], parent="turret"),
            P("cupola", (5, -7, 6), [
                C((-3.5, -2, -3.5), (7, 2, 7), "camo"),
                C((-2.5, -3, -2.5), (5, 1, 5), "metal_dark"),
                C((-1, -1.6, -4.2), (2, 1, 1), "glass_dark"),  # mirillas periscópicas
                C((-1, -1.6, 3.2), (2, 1, 1), "glass_dark"),
                C((-4.2, -1.6, -1), (1, 1, 2), "glass_dark"),
                C((3.2, -1.6, -1), (1, 1, 2), "glass_dark"),
            ], parent="turret"),
            P("mg", (5, -9, 4), [
                C((-0.5, -0.5, -7), (1, 1, 8), "metal_dark"),
                C((-1, -1.5, -3), (2, 2, 3), "metal_dark"),
                C((1, -1.5, -1), (2, 2, 3), "metal_dark"),    # caja de munición
            ], parent="turret"),
            P("ant0", (-8, -7, 11), [C((-0.5, -12, -0.5), (1, 12, 1), "metal_dark")], parent="turret", rot=(0, 0, 6)),
            P("ant1", (8, -7, 12), [C((-0.5, -10, -0.5), (1, 10, 1), "metal_dark")], parent="turret", rot=(-8, 0, 0)),
            P("smoke_l", (11, -4, -6), [
                C((0, -1, -4), (2, 2, 4), "metal_dark"),
                C((0, -1, -0.5), (2, 2, 4), "metal_dark"),
                C((0, -1, 3), (2, 2, 4), "metal_dark"),
            ], parent="turret", rot=(-10, -25, 0)),
            P("smoke_r", (-13, -4, -6), [
                C((0, -1, -4), (2, 2, 4), "metal_dark"),
                C((0, -1, -0.5), (2, 2, 4), "metal_dark"),
                C((0, -1, 3), (2, 2, 4), "metal_dark"),
            ], parent="turret", rot=(-10, 25, 0)),
        ],
    },
    "cruise_missile": {
        "tex": "cruise_missile", "tex_size": 256, "ss": 4, "glow": True,
        "parts": [
            P("body", (0, 0, 0), [
                C((-3, -3, -22), (6, 6, 40), "missile_body"),
                C((-2, 3, 4), (4, 2, 10), "nose"),
                C((-0.5, -3.8, -16), (1, 1, 28), "cable"),     # canaleta de cables
                C((-3.5, -3.5, -8), (7, 7, 1), "metal_dark"),  # cuaderna 1
                C((-3.5, -3.5, 8), (7, 7, 1), "metal_dark"),   # cuaderna 2
                C((2.8, -1, 2), (1, 2, 3), "metal_dark"),      # puerto umbilical
                # Toberas RCS junto a la nariz (4 caras)
                C((-3.4, -0.5, -20), (1, 1, 2), "metal_dark"),
                C((2.4, -0.5, -20), (1, 1, 2), "metal_dark"),
                C((-0.5, -3.4, -20), (1, 1, 2), "metal_dark"),
                C((-0.5, 2.4, -20), (1, 1, 2), "metal_dark"),
            ]),
            P("body_oct", (0, 0, 0), [C((-3, -3, -22), (6, 6, 40), "missile_body")], rot=(0, 0, 45)),
            P("nose", (0, 0, -22), [
                C((-2.5, -2.5, -5), (5, 5, 5), "missile_body"),
                C((-1.5, -1.5, -9), (3, 3, 4), "nose"),
                C((-0.5, -0.5, -12), (1, 1, 3), "sensor_glow"),
            ]),
            P("fin0", (0, 0, 14), [C((-0.5, -10, 0), (1, 10, 5), "fin")]),
            P("fin1", (0, 0, 14), [C((-0.5, -10, 0), (1, 10, 5), "fin")], rot=(0, 0, 90)),
            P("fin2", (0, 0, 14), [C((-0.5, -10, 0), (1, 10, 5), "fin")], rot=(0, 0, 180)),
            P("fin3", (0, 0, 14), [C((-0.5, -10, 0), (1, 10, 5), "fin")], rot=(0, 0, 270)),
            P("midfin0", (0, 0, -6), [C((-0.5, -7, 0), (1, 7, 4), "fin")], rot=(0, 0, 45)),
            P("midfin1", (0, 0, -6), [C((-0.5, -7, 0), (1, 7, 4), "fin")], rot=(0, 0, 135)),
            P("midfin2", (0, 0, -6), [C((-0.5, -7, 0), (1, 7, 4), "fin")], rot=(0, 0, 225)),
            P("midfin3", (0, 0, -6), [C((-0.5, -7, 0), (1, 7, 4), "fin")], rot=(0, 0, 315)),
            P("nozzle", (0, 0, 18), [
                C((-2.5, -2.5, 0), (5, 5, 3), "nozzle"),
                C((-1.5, -1.5, 2.5), (3, 3, 1), "nozzle_glow"),
            ]),
        ],
    },
    "mlrs_rocket": {
        "tex": "mlrs_rocket", "tex_size": 64, "ss": 4, "glow": True,
        "parts": [
            P("body", (0, 0, 0), [
                C((-1.5, -1.5, -7), (3, 3, 14), "rocket_body"),
                C((-0.5, -2.2, -4), (1, 1, 2), "metal_dark"),  # tetón de riel 1
                C((-0.5, -2.2, 2), (1, 1, 2), "metal_dark"),   # tetón de riel 2
            ]),
            P("nose", (0, 0, -7), [
                C((-1, -1, -3), (2, 2, 3), "nose"),
                C((-0.5, -0.5, -5), (1, 1, 2), "nose"),
            ]),
            P("rfin0", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")]),
            P("rfin1", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")], rot=(0, 0, 90)),
            P("rfin2", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")], rot=(0, 0, 180)),
            P("rfin3", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")], rot=(0, 0, 270)),
            P("rnozzle", (0, 0, 7), [C((-1, -1, 0), (2, 2, 2), "nozzle_glow")]),
        ],
    },
    "warship": {
        "tex": "warship", "tex_size": 512, "ss": 2, "glow": True,
        "parts": [
            P("hull", (0, 0, 0), [
                C((-14, -6, -30), (28, 14, 60), "ship_hull"),
                C((-12, -4, 30), (24, 12, 28), "ship_hull"),
                C((-8, -2, 58), (16, 8, 10), "ship_dark"),
                C((-6, -10, -34), (12, 4, 74), "ship_dark"),
                C((-4, 8, -20), (8, 3, 44), "ship_dark"),
                C((-3, -13, -16), (6, 3, 6), "ship_dark"),
                # Torretas dorsales (proa y popa del lomo)
                C((-4, -12, -6), (8, 2, 8), "ship_dark"),
                C((-2.5, -12.6, -14), (1, 1, 8), "ship_dark"),
                C((1.5, -12.6, -14), (1, 1, 8), "ship_dark"),
                C((-4, -12, 14), (8, 2, 8), "ship_dark"),
                C((-2.5, -12.6, 6), (1, 1, 8), "ship_dark"),
                C((1.5, -12.6, 6), (1, 1, 8), "ship_dark"),
                # Greebles de superficie (placas sobresalientes)
                C((-14.5, -4, -20), (1, 5, 10), "ship_dark"),
                C((13.5, -4, -20), (1, 5, 10), "ship_dark"),
                C((-14.5, -3, 2), (1, 4, 12), "ship_dark"),
                C((13.5, -3, 2), (1, 4, 12), "ship_dark"),
                C((-8, -10.6, 22), (4, 1, 14), "ship_dark"),
                C((4, -6.6, -28), (4, 1, 12), "ship_dark"),
                # Luces de posición (tiras glow a lo largo del casco)
                C((-14.3, -1, -24), (1, 1, 46), "window_glow"),
                C((13.3, -1, -24), (1, 1, 46), "window_glow"),
                # Marco del hangar ventral con franjas de peligro
                C((-6, 7.6, -12), (12, 1, 2), "hazard"),
                C((-6, 7.6, 6), (12, 1, 2), "hazard"),
            ]),
            P("prow", (0, 0, -30), [
                C((-11, -5, -14), (22, 11, 14), "ship_hull"),
                C((-7, -3, -26), (14, 7, 12), "ship_hull"),
                C((-3, -1, -34), (6, 4, 8), "ship_dark"),
                C((-2, 4, -22), (4, 3, 5), "ship_dark"),       # sensor de barbilla
                C((-1, -3, -39), (2, 6, 6), "ship_dark"),      # espolón
                C((-6, 0, -29), (3, 3, 4), "glass_dark"),      # tubo lanzatorpedos L
                C((3, 0, -29), (3, 3, 4), "glass_dark"),       # tubo lanzatorpedos R
            ]),
            P("bridge", (0, -10, 18), [
                C((-7, -8, -6), (14, 8, 12), "ship_hull"),
                C((-5, -12, -4), (10, 4, 8), "window_glow"),
                C((-3, -20, 2), (1, 8, 1), "ship_dark"),
                C((2, -18, 3), (1, 6, 1), "ship_dark"),
                C((-6, -16, 5), (4, 4, 1), "ship_dark"),       # antena de plato
                C((-4.5, -13, 4), (1, 3, 1), "ship_dark"),     # brazo del plato
                C((-6, -3.4, -6.3), (12, 1, 1), "window_glow"),# ventanal inferior
            ]),
            P("wing_l", (14, -2, 6), [
                C((0, 0, -8), (30, 2, 24), "ship_hull"),
                C((28, -6, 4), (2, 8, 12), "ship_dark"),
                C((0, -0.4, -8.6), (30, 1, 1), "ship_dark"),   # slat borde de ataque
                C((8, 2, 2), (1, 2, 3), "ship_dark"),          # pilón interior
                C((18, 2, 2), (1, 2, 3), "ship_dark"),         # pilón exterior
                C((7.5, 4, -3), (2, 2, 10), "missile_tiny"),   # misil colgado 1
                C((17.5, 4, -3), (2, 2, 10), "missile_tiny"),  # misil colgado 2
                C((27, -2, -4), (2, 2, 6), "ship_dark"),       # cañón de punta
            ], rot=(0, 22, 8)),
            P("wing_r", (-14, -2, 6), [
                C((-30, 0, -8), (30, 2, 24), "ship_hull"),
                C((-30, -6, 4), (2, 8, 12), "ship_dark"),
                C((-30, -0.4, -8.6), (30, 1, 1), "ship_dark"),
                C((-9, 2, 2), (1, 2, 3), "ship_dark"),
                C((-19, 2, 2), (1, 2, 3), "ship_dark"),
                C((-9.5, 4, -3), (2, 2, 10), "missile_tiny"),
                C((-19.5, 4, -3), (2, 2, 10), "missile_tiny"),
                C((-29, -2, -4), (2, 2, 6), "ship_dark"),
            ], rot=(0, -22, -8)),
            P("nacelle_l", (16, 2, 26), [
                C((-4, -4, -10), (8, 8, 26), "ship_dark"),
                C((-5, -5, -12), (10, 10, 2), "ship_dark"),
                C((-3, -3, 16), (6, 6, 2), "engine_glow"),
                C((-5.5, -3, -6), (1, 6, 2), "ship_dark"),     # aletas refrigeración
                C((-5.5, -3, 0), (1, 6, 2), "ship_dark"),
                C((-5.5, -3, 6), (1, 6, 2), "ship_dark"),
                C((-1, -1, -16), (2, 2, 5), "ship_dark"),      # pincho de admisión
            ]),
            P("nacelle_r", (-16, 2, 26), [
                C((-4, -4, -10), (8, 8, 26), "ship_dark"),
                C((-5, -5, -12), (10, 10, 2), "ship_dark"),
                C((-3, -3, 16), (6, 6, 2), "engine_glow"),
                C((4.5, -3, -6), (1, 6, 2), "ship_dark"),
                C((4.5, -3, 0), (1, 6, 2), "ship_dark"),
                C((4.5, -3, 6), (1, 6, 2), "ship_dark"),
                C((-1, -1, -16), (2, 2, 5), "ship_dark"),
            ]),
            P("pod_l", (15, -8, -8), [C((0, 0, -6), (3, 4, 12), "ship_dark")]),
            P("pod_r", (-18, -8, -8), [C((0, 0, -6), (3, 4, 12), "ship_dark")]),
            P("cannon", (0, 8, -6), [C((-4, -1, -4), (8, 3, 8), "ship_dark")]),
            P("cannon_barrel", (0, 2, 0), [
                C((-2, 0, -2), (4, 12, 4), "ship_dark"),
                C((-2.5, 12, -2.5), (5, 2, 5), "engine_glow"),
                C((-3, 3, -3), (6, 1, 6), "engine_glow"),      # anillo de enfoque 1
                C((-3, 7, -3), (6, 1, 6), "ship_dark"),        # anillo de enfoque 2
            ], parent="cannon"),
        ],
    },
    "tank_shell": {
        "tex": "tank_shell", "tex_size": 32, "ss": 4, "glow": False,
        "parts": [
            P("shell", (0, 0, 0), [
                C((-1, -1, -3), (2, 2, 7), "brass"),
                C((-0.5, -0.5, -5), (1, 1, 2), "nose"),
            ]),
        ],
    },
}

# ---------------------------------------------------------------- UV packing
def pack(spec):
    """Asigna texOffs por cubo (shelf packing). Devuelve lista de (part, cube, u, v)."""
    size = spec["tex_size"]
    items = []
    for part in spec["parts"]:
        for cube in part["cubes"]:
            w, h, d = [int(math.ceil(x)) for x in cube["s"]]
            fw, fh = 2 * (w + d), d + h
            items.append((fw, fh, part, cube))
    items.sort(key=lambda it: -it[1])
    x = y = shelf = 0
    for fw, fh, part, cube in items:
        if x + fw > size:
            x = 0; y += shelf + 1; shelf = 0
        if y + fh > size:
            raise SystemExit(f"UV overflow en {spec['tex']}: sube tex_size")
        cube["uv"] = (x, y)
        x += fw + 1
        shelf = max(shelf, fh)
    return size

# ---------------------------------------------------------------- painting
FACES = ["up", "down", "side1", "front", "side2", "back"]

def face_rects(u, v, w, h, d):
    return {
        "up":    (u + d, v, w, d),
        "down":  (u + d + w, v, w, d),
        "side1": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "side2": (u + d + w, v + d, d, h),
        "back":  (u + d + w + d, v + d, w, h),
    }

def _face_projection(face, A, B):
    """Proyección del cubo B sobre el plano de una cara del cubo A (misma parte).
    Devuelve (gap, (u0,u1), (v0,v1)) en unidades LÓGICAS relativas al rect de la
    cara, o None si B no está delante. Los ejes u/v y sus espejos replican
    exactamente el mapeo de render_preview (orden de esquinas por cara)."""
    ax, ay, az, aw, ah, ad = A
    bx, by, bz, bw, bh, bd = B
    if face == "front":      # z = az, normal -Z ; u=x-ax, v=y-ay
        if not bz < az:
            return None
        gap = max(0.0, az - (bz + bd))
        u0, u1 = bx - ax, bx + bw - ax
        v0, v1 = by - ay, by + bh - ay
    elif face == "back":     # z = az+ad, normal +Z ; u espejado en x
        if not bz + bd > az + ad:
            return None
        gap = max(0.0, bz - (az + ad))
        u0, u1 = (ax + aw) - (bx + bw), (ax + aw) - bx
        v0, v1 = by - ay, by + bh - ay
    elif face == "up":       # y = ay, normal -Y ; v espejado en z
        if not by < ay:
            return None
        gap = max(0.0, ay - (by + bh))
        u0, u1 = bx - ax, bx + bw - ax
        v0, v1 = (az + ad) - (bz + bd), (az + ad) - bz
    elif face == "down":     # y = ay+ah, normal +Y
        if not by + bh > ay + ah:
            return None
        gap = max(0.0, by - (ay + ah))
        u0, u1 = bx - ax, bx + bw - ax
        v0, v1 = bz - az, bz + bd - az
    elif face == "side1":    # x = ax, normal -X ; u espejado en z
        if not bx < ax:
            return None
        gap = max(0.0, ax - (bx + bw))
        u0, u1 = (az + ad) - (bz + bd), (az + ad) - bz
        v0, v1 = by - ay, by + bh - ay
    else:                    # side2: x = ax+aw, normal +X
        if not bx + bw > ax + aw:
            return None
        gap = max(0.0, bx - (ax + aw))
        u0, u1 = bz - az, bz + bd - az
        v0, v1 = by - ay, by + bh - ay
    if gap > 2.0:
        return None
    return gap, (u0, u1), (v0, v1)

def _contact_ao(spec, tex):
    """AO de CONTACTO horneada: cada cubo proyecta sombra suave sobre las caras
    de sus vecinos de la misma parte (ERA sobre camo, greebles sobre el casco…).
    Solo albedo — el glow no recibe sombra."""
    ss = spec.get("ss", 1)
    for part in spec["parts"]:
        cubes = part["cubes"]
        if len(cubes) < 2:
            continue
        for a in cubes:
            ax, ay, az = a["o"]
            aw2, ah2, ad2 = a["s"]
            wi, hi, di = int(math.ceil(aw2)), int(math.ceil(ah2)), int(math.ceil(ad2))
            u, v = a["uv"]
            for face, (fx, fy, fw, fh) in face_rects(u, v, wi, hi, di).items():
                if fw <= 0 or fh <= 0:
                    continue
                for b in cubes:
                    if b is a:
                        continue
                    proj = _face_projection(face, (ax, ay, az, aw2, ah2, ad2),
                                            (b["o"][0], b["o"][1], b["o"][2],
                                             b["s"][0], b["s"][1], b["s"][2]))
                    if proj is None:
                        continue
                    gap, (u0, u1), (v0, v1) = proj
                    strength = 0.35 * (1.0 - (gap / 2.0) * (gap / 2.0) * (3 - 2 * (gap / 2.0)))
                    if strength <= 0.02:
                        continue
                    hpx, wpx = fh * ss, fw * ss
                    uu = np.arange(wpx, dtype=float)[None, :] / ss
                    vv = np.arange(hpx, dtype=float)[:, None] / ss
                    du = np.maximum(np.maximum(u0 - uu, uu - u1), 0.0)
                    dv = np.maximum(np.maximum(v0 - vv, vv - v1), 0.0)
                    dist = np.sqrt(du * du + dv * dv)
                    fade = 1.0 - _smoothstep(0.0, 3.0, dist)
                    shade = 1.0 - strength * fade
                    tex[fy * ss:fy * ss + hpx, fx * ss:fx * ss + wpx, :3] *= shade[:, :, None]

def paint(spec, model_name):
    global SS
    SS = spec.get("ss", 1)
    size = spec["tex_size"]
    px = size * SS
    tex = np.zeros((px, px, 4))
    glow_tex = np.zeros((px, px, 4)) if spec["glow"] else None
    for part in spec["parts"]:
        for cube in part["cubes"]:
            w, h, d = [int(math.ceil(x)) for x in cube["s"]]
            u, v = cube["uv"]
            for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
                if fw <= 0 or fh <= 0:
                    continue
                # Seed ESTABLE por contenido: añadir/reordenar cubos re-pinta
                # solo lo suyo, no re-aleatoriza el modelo entero.
                key = f"{model_name}|{part['name']}|{cube['mat']}|{cube['o']}|{cube['s']}|{face}"
                seed = zlib.crc32(key.encode()) & 0x7FFFFFFF
                col, glow = paint_face(cube["mat"], face, (fh * SS, fw * SS), seed, model_name)
                tex[fy * SS:(fy + fh) * SS, fx * SS:(fx + fw) * SS] = col
                if glow is not None and glow_tex is not None:
                    glow_tex[fy * SS:(fy + fh) * SS, fx * SS:(fx + fw) * SS] = glow
    _contact_ao(spec, tex)
    return tex, glow_tex

def save_png(arr, path):
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    img.save(path)

# ---------------------------------------------------------------- java emission
def fmt(x):
    return f"{float(x):.4f}F"

def emit_java(spec, model_name, class_name):
    size = spec["tex_size"]
    lines = [
        "// GENERADO por tools/gen_models.py — NO editar a mano; edita el spec y regenera.",
        "package com.vvrgs.irontempest.client.model.geom;",
        "",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.LayerDefinition;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "",
        f"public final class {class_name} {{",
        f"    private {class_name}() {{}}",
        "",
        "    public static LayerDefinition createBodyLayer() {",
        "        MeshDefinition mesh = new MeshDefinition();",
        "        PartDefinition root = mesh.getRoot();",
    ]
    declared = {}
    for part in spec["parts"]:
        parent_key = part.get("parent")
        if parent_key and parent_key.endswith("_rel"):
            parent_name = parent_key[:-4]
        else:
            parent_name = parent_key
        parent_var = declared.get(parent_name, "root")
        var = f"p_{part['name']}"
        cb = ["CubeListBuilder.create()"]
        for cube in part["cubes"]:
            u, v = cube["uv"]
            ox, oy, oz = cube["o"]
            w, h, d = cube["s"]
            cb.append(f".texOffs({u}, {v}).addBox({fmt(ox)}, {fmt(oy)}, {fmt(oz)}, {fmt(w)}, {fmt(h)}, {fmt(d)})")
        rx, ry, rz = part["rot"]
        px, py, pz = part["pivot"]
        # pivots hijos: relativos al padre
        if parent_name and parent_name in declared:
            pp = next(p for p in spec["parts"] if p["name"] == parent_name)
            px -= pp["pivot"][0]; py -= pp["pivot"][1]; pz -= pp["pivot"][2]
        if rx or ry or rz:
            pose = (f"PartPose.offsetAndRotation({fmt(px)}, {fmt(py)}, {fmt(pz)}, "
                    f"{fmt(math.radians(rx))}, {fmt(math.radians(ry))}, {fmt(math.radians(rz))})")
        else:
            pose = f"PartPose.offset({fmt(px)}, {fmt(py)}, {fmt(pz)})"
        joined = "\n                ".join(cb)
        lines.append(f"        PartDefinition {var} = {parent_var}.addOrReplaceChild(\"{part['name']}\",")
        lines.append(f"                {joined},")
        lines.append(f"                {pose});")
        declared[part["name"]] = var
    lines += [
        f"        return LayerDefinition.create(mesh, {size}, {size});",
        "    }",
        "}",
    ]
    return "\n".join(lines) + "\n"

# ---------------------------------------------------------------- misc textures
def gen_misc():
    os.makedirs(MISC_DIR, exist_ok=True)
    # beam.png: tira 64x256 para el haz (V = a lo largo del haz, scroll)
    h, w = 256, 64
    n = value_noise(h, w, 8, 4242, 3)
    streaks = value_noise(h, 8, 6, 777, 2)
    streaks = np.repeat(streaks, w // 8, axis=1)
    xx = np.abs(np.linspace(-1, 1, w))[None, :]
    core = np.clip(1.15 - xx * 1.15, 0, 1) ** 2
    val = np.clip(core * (0.55 + 0.45 * (0.6 * n + 0.4 * streaks)), 0, 1)
    beam = np.zeros((h, w, 4))
    beam[:, :, 0] = 255 * np.clip(val * 1.15, 0, 1)
    beam[:, :, 1] = 255 * np.clip(val * 0.75 + core * 0.35, 0, 1)
    beam[:, :, 2] = 255 * np.clip(val * 0.45 + core * 0.55, 0, 1)
    beam[:, :, 3] = 255 * np.clip(val * 1.6, 0, 1)
    save_png(beam, os.path.join(MISC_DIR, "beam.png"))
    # white.png
    white = np.full((16, 16, 4), 255.0)
    save_png(white, os.path.join(MISC_DIR, "white.png"))
    print("misc: beam.png, white.png")

# ---------------------------------------------------------------- main
CLASS_NAMES = {
    "tank": "TankGeometry",
    "cruise_missile": "CruiseMissileGeometry",
    "mlrs_rocket": "MlrsRocketGeometry",
    "warship": "WarshipGeometry",
    "tank_shell": "TankShellGeometry",
}

def main():
    os.makedirs(JAVA_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    report = []
    for model_name, spec in SPECS.items():
        pack(spec)
        tex, glow = paint(spec, model_name)
        save_png(tex, os.path.join(TEX_DIR, f"{spec['tex']}.png"))
        if glow is not None:
            save_png(glow, os.path.join(TEX_DIR, f"{spec['tex']}_glow.png"))
        java = emit_java(spec, model_name, CLASS_NAMES[model_name])
        with open(os.path.join(JAVA_DIR, f"{CLASS_NAMES[model_name]}.java"), "w") as f:
            f.write(java)
        n_parts = len(spec["parts"])
        n_cubes = sum(len(p["cubes"]) for p in spec["parts"])
        report.append((model_name, n_parts, n_cubes, spec["tex_size"], glow is not None))
    gen_misc()
    print(f"{'modelo':<16}{'partes':>7}{'cubos':>7}{'tex':>6}{'glow':>6}")
    for r in report:
        print(f"{r[0]:<16}{r[1]:>7}{r[2]:>7}{r[3]:>6}{'si' if r[4] else 'no':>6}")

if __name__ == "__main__":
    main()
