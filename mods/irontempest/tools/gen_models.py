#!/usr/bin/env python3
"""Iron Tempest — generador de geometría de entidades + texturas pintadas.

Fuente única de verdad: SPECS define cada modelo (partes, cubos, materiales).
Emite:
  - src/main/java/com/vvrgs/irontempest/client/model/geom/<Name>Geometry.java
  - src/main/resources/assets/irontempest/textures/entity/<tex>.png (+ _glow.png)
Convención: unidades de modelo vanilla (16 u = 1 bloque), +Y hacia abajo,
frente del vehículo = -Z, suelo en y=24 para vehículos terrestres.
"""
import os, math, json
import numpy as np
from PIL import Image

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
}

def draw_text(arr, x, y, text, color, scale=1):
    for ch in text:
        g = FONT.get(ch)
        if g is None:
            x += 4 * scale
            continue
        for r, row in enumerate(g):
            for c, bit in enumerate(row):
                if bit == "1":
                    ys, xs = y + r * scale, x + c * scale
                    arr[ys:ys + scale, xs:xs + scale, :3] = color
                    arr[ys:ys + scale, xs:xs + scale, 3] = 255
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
def _base(rect_shape, color, noise_amt, scale, seed):
    h, w = rect_shape
    n = value_noise(h, w, scale, seed) - 0.5
    col = np.zeros((h, w, 4))
    for i in range(3):
        col[:, :, i] = np.clip(color[i] + n * 255 * noise_amt, 0, 255)
    col[:, :, 3] = 255
    return col

def _ao_border(col):
    h, w = col.shape[:2]
    if h >= 3 and w >= 3:
        col[0, :, :3] *= 0.82; col[-1, :, :3] *= 0.72
        col[:, 0, :3] *= 0.85; col[:, -1, :3] *= 0.85
    return col

def _vgrad(col, top=1.06, bottom=0.78):
    h = col.shape[0]
    g = np.linspace(top, bottom, h)[:, None, None]
    col[:, :, :3] = np.clip(col[:, :, :3] * g, 0, 255)
    return col

def _rivets(col, spacing=6, color_mul=1.35, seed=1):
    h, w = col.shape[:2]
    for y in range(2, h - 1, spacing):
        for x in range(2, w - 1, spacing):
            col[y, x, :3] = np.clip(col[y, x, :3] * color_mul, 0, 255)
            if y + 1 < h:
                col[y + 1, x, :3] *= 0.8
    return col

def _panel_lines(col, step_y=8, step_x=10, dark=0.86):
    h, w = col.shape[:2]
    for y in range(step_y, h, step_y):
        col[y, :, :3] *= dark
    for x in range(step_x, w, step_x):
        col[:, x, :3] *= dark
    return col

def paint_face(mat, face, hw, seed, model):
    """face: up/down/front/back/side1/side2 ; hw: (h_px, w_px). Devuelve (col, glow|None)."""
    h, w = hw
    glow = None
    if mat == "camo":
        col = _base((h, w), (86, 96, 62), 0.10, 5, seed)
        n2 = value_noise(h, w, 4, seed + 77, 3)
        col[:, :, :3][n2 > 0.62] = np.array([58, 71, 44]) * (1 + (n2[n2 > 0.62][:, None] - 0.62))
        col[:, :, :3][n2 < 0.34] = (94, 82, 58)
        if face == "up": col[:, :, :3] = np.clip(col[:, :, :3] * 1.12, 0, 255)
        if face == "down": col[:, :, :3] *= 0.55
        _rivets(col, 7, seed=seed); _ao_border(col)
    elif mat == "metal_dark":
        col = _base((h, w), (52, 54, 58), 0.08, 4, seed); _vgrad(col); _ao_border(col)
    elif mat == "track":
        col = _base((h, w), (44, 42, 40), 0.10, 3, seed)
        if w >= h:  # caras largas: barras de rodadura verticales
            for x in range(0, w, 3):
                col[:, x, :3] *= 0.55
                if x + 1 < w: col[:, x + 1, :3] *= 1.25
        else:
            for y in range(0, h, 3):
                col[y, :, :3] *= 0.55
        _ao_border(col)
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
        for x in range(0, w, 8): col[:, x, :3] *= 0.8
        _vgrad(col, 1.0, 0.7); _ao_border(col)
    elif mat == "barrel":
        col = _base((h, w), (40, 42, 46), 0.06, 3, seed); _vgrad(col, 1.1, 0.85)
        if face in ("side1", "side2", "up", "down") and w > 12:
            col[:, 2:4, :3] = (200, 200, 195)   # anillos de derribo
            col[:, 5:7, :3] = (200, 200, 195)
            col[:, w // 2:w // 2 + 4, :3] *= 0.8  # funda térmica
        _ao_border(col)
    elif mat == "glass_glow":
        col = _base((h, w), (255, 244, 180), 0.04, 2, seed)
        glow = col.copy()
    elif mat == "drum":
        col = _base((h, w), (72, 78, 52), 0.08, 3, seed)
        if w > 6:
            col[:, w // 3, :3] *= 0.7; col[:, 2 * w // 3, :3] *= 0.7
        _ao_border(col)
    elif mat == "missile_body":
        col = _base((h, w), (198, 200, 204), 0.05, 5, seed)
        _panel_lines(col, 8, 10, 0.92); _vgrad(col, 1.05, 0.88); _ao_border(col)
    elif mat == "nose":
        col = _base((h, w), (48, 50, 56), 0.05, 3, seed); _ao_border(col)
    elif mat == "fin":
        col = _base((h, w), (170, 172, 178), 0.06, 3, seed)
        col[0:1, :, :3] = (200, 60, 50)  # borde de ataque rojo
        _ao_border(col)
    elif mat == "nozzle":
        col = _base((h, w), (60, 56, 52), 0.10, 2, seed); _ao_border(col)
    elif mat == "nozzle_glow":
        col = _base((h, w), (255, 160, 70), 0.06, 2, seed)
        glow = col.copy()
    elif mat == "rocket_body":
        col = _base((h, w), (96, 104, 70), 0.08, 3, seed)
        if h > 6: col[1:3, :, :3] = (208, 172, 60)  # banda de ojiva
        _ao_border(col)
    elif mat == "ship_hull":
        col = _base((h, w), (58, 66, 82), 0.07, 6, seed)
        _panel_lines(col, 6, 8, 0.88)
        n2 = value_noise(h, w, 6, seed + 5)
        col[:, :, :3][n2 > 0.72] *= 1.15
        _vgrad(col, 1.05, 0.85); _ao_border(col)
    elif mat == "ship_dark":
        col = _base((h, w), (36, 40, 52), 0.06, 4, seed); _panel_lines(col, 5, 7, 0.85); _ao_border(col)
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
        if h >= 3:
            col[h // 3, :, :3] = (150, 230, 255)
            if w > 4:
                for x in range(0, w, 3): col[h // 3, x, :3] = (40, 46, 60)
        glow = np.zeros((h, w, 4))
        mask = (col[:, :, 2] > 200)
        glow[mask] = col[mask]
        _ao_border(col)
    elif mat == "brass":
        col = _base((h, w), (168, 132, 62), 0.08, 2, seed)
        if w > 4: col[:, -2:, :3] = (150, 90, 50)  # banda de forzamiento
        _ao_border(col)
    else:
        col = _base((h, w), (120, 120, 120), 0.05, 3, seed)
    # decals específicos
    if model == "tank" and mat == "camo" and face in ("side1", "side2") and w >= 20 and h >= 7:
        draw_text(col, w // 2 - 3, h // 2 - 2, "07", (230, 228, 220))
    if model == "cruise_missile" and mat == "missile_body" and face in ("side1", "side2", "up") and w >= 34:
        col[:, 4:7, :3] = (190, 55, 45)  # banda roja
        draw_text(col, 10, max(0, h // 2 - 2), "VRGS-1", (40, 40, 44))
    if model == "warship" and mat == "ship_hull" and face in ("side1", "side2") and w >= 40 and h >= 8:
        draw_text(col, 6, h // 2 - 2, "TEMPEST", (140, 200, 230))
    return col, glow

# ---------------------------------------------------------------- specs
def P(name, pivot, cubes, parent=None, rot=(0, 0, 0)):
    return {"name": name, "pivot": pivot, "rot": rot, "cubes": cubes, "parent": parent}

def C(o, s, mat):
    return {"o": o, "s": s, "mat": mat}

SPECS = {
    "tank": {
        "tex": "tank", "tex_size": 512, "glow": True,
        "parts": [
            P("hull", (0, 24, 0), [
                C((-13, -17, -28), (26, 9, 56), "camo"),
                C((-13, -19, 4), (26, 2, 22), "camo"),
                C((-4, -20, 14), (3, 1, 8), "metal_dark"),   # escape L
                C((1, -20, 14), (3, 1, 8), "metal_dark"),    # escape R
            ]),
            P("glacis", (0, 8, -28), [C((-13, -1, -14), (26, 2, 14), "camo")], parent="hull", rot=(38, 0, 0)),
            P("rear_plate", (0, 7, 28), [C((-13, -1, 0), (26, 2, 10), "camo")], rot=(-30, 0, 0)),
            P("plow", (0, 18, -30), [C((-11, -6, -2), (22, 8, 2), "metal_dark")], rot=(-15, 0, 0)),
            P("headlight_l", (9, 8.5, -29), [C((-1.5, -1, -1), (3, 2, 1), "glass_glow")]),
            P("headlight_r", (-9, 8.5, -29), [C((-1.5, -1, -1), (3, 2, 1), "glass_glow")]),
            P("drum_l", (-8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")]),
            P("drum_l_x", (-8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")], rot=(0, 0, 45)),
            P("drum_r", (8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")]),
            P("drum_r_x", (8, 6, 30), [C((-3, -3, 0), (6, 6, 8), "drum")], rot=(0, 0, 45)),
            P("track_l", (0, 24, 0), [
                C((14, -13, -30), (11, 13, 60), "track"),
                C((25, -15, -26), (1, 7, 52), "skirt"),
                C((15, -11, -33), (9, 9, 3), "metal_dark"),
            ]),
            P("wheel_l0", (19.5, 18, -22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l1", (19.5, 18, -11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l2", (19.5, 18, 0), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l3", (19.5, 18, 11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("wheel_l4", (19.5, 18, 22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_l_rel"),
            P("track_r", (0, 24, 0), [
                C((-25, -13, -30), (11, 13, 60), "track"),
                C((-26, -15, -26), (1, 7, 52), "skirt"),
                C((-24, -11, -33), (9, 9, 3), "metal_dark"),
            ]),
            P("wheel_r0", (-19.5, 18, -22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r1", (-19.5, 18, -11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r2", (-19.5, 18, 0), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r3", (-19.5, 18, 11), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("wheel_r4", (-19.5, 18, 22), [C((-3.5, -3.5, -1.5), (7, 7, 3), "wheel")], parent="track_r_rel"),
            P("turret", (0, 7, -2), [
                C((-11, -7, -13), (22, 7, 26), "camo"),
                C((-9, -2, 13), (18, 4, 4), "metal_dark"),   # cesta trasera
            ]),
            P("barrel", (0, -3.5, -13), [
                C((-3, -2.5, -3), (6, 5, 3), "metal_dark"),
                C((-1.5, -1.5, -27), (3, 3, 24), "barrel"),
                C((-2, -2, -31), (4, 4, 4), "barrel"),
            ], parent="turret"),
            P("cupola", (5, -7, 6), [
                C((-3.5, -2, -3.5), (7, 2, 7), "camo"),
                C((-2.5, -3, -2.5), (5, 1, 5), "metal_dark"),
            ], parent="turret"),
            P("mg", (5, -9, 4), [
                C((-0.5, -0.5, -7), (1, 1, 8), "metal_dark"),
                C((-1, -1.5, -3), (2, 2, 3), "metal_dark"),
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
        "tex": "cruise_missile", "tex_size": 128, "glow": False,
        "parts": [
            P("body", (0, 0, 0), [
                C((-3, -3, -22), (6, 6, 40), "missile_body"),
                C((-2, 3, 4), (4, 2, 10), "nose"),
            ]),
            P("body_oct", (0, 0, 0), [C((-3, -3, -22), (6, 6, 40), "missile_body")], rot=(0, 0, 45)),
            P("nose", (0, 0, -22), [
                C((-2.5, -2.5, -5), (5, 5, 5), "missile_body"),
                C((-1.5, -1.5, -9), (3, 3, 4), "nose"),
                C((-0.5, -0.5, -12), (1, 1, 3), "nose"),
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
        "tex": "mlrs_rocket", "tex_size": 64, "glow": False,
        "parts": [
            P("body", (0, 0, 0), [C((-1.5, -1.5, -7), (3, 3, 14), "rocket_body")]),
            P("nose", (0, 0, -7), [
                C((-1, -1, -3), (2, 2, 3), "nose"),
                C((-0.5, -0.5, -5), (1, 1, 2), "nose"),
            ]),
            P("rfin0", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")]),
            P("rfin1", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")], rot=(0, 0, 90)),
            P("rfin2", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")], rot=(0, 0, 180)),
            P("rfin3", (0, 0, 4), [C((-0.5, -4, 0), (1, 4, 3), "fin")], rot=(0, 0, 270)),
            P("rnozzle", (0, 0, 7), [C((-1, -1, 0), (2, 2, 2), "nozzle")]),
        ],
    },
    "warship": {
        "tex": "warship", "tex_size": 512, "glow": True,
        "parts": [
            P("hull", (0, 0, 0), [
                C((-14, -6, -30), (28, 14, 60), "ship_hull"),
                C((-12, -4, 30), (24, 12, 28), "ship_hull"),
                C((-8, -2, 58), (16, 8, 10), "ship_dark"),
                C((-6, -10, -34), (12, 4, 74), "ship_dark"),
                C((-4, 8, -20), (8, 3, 44), "ship_dark"),
                C((-3, -13, -16), (6, 3, 6), "ship_dark"),
            ]),
            P("prow", (0, 0, -30), [
                C((-11, -5, -14), (22, 11, 14), "ship_hull"),
                C((-7, -3, -26), (14, 7, 12), "ship_hull"),
                C((-3, -1, -34), (6, 4, 8), "ship_dark"),
            ]),
            P("bridge", (0, -10, 18), [
                C((-7, -8, -6), (14, 8, 12), "ship_hull"),
                C((-5, -12, -4), (10, 4, 8), "window_glow"),
                C((-3, -20, 2), (1, 8, 1), "ship_dark"),
                C((2, -18, 3), (1, 6, 1), "ship_dark"),
            ]),
            P("wing_l", (14, -2, 6), [
                C((0, 0, -8), (30, 2, 24), "ship_hull"),
                C((28, -6, 4), (2, 8, 12), "ship_dark"),
            ], rot=(0, 22, 8)),
            P("wing_r", (-14, -2, 6), [
                C((-30, 0, -8), (30, 2, 24), "ship_hull"),
                C((-30, -6, 4), (2, 8, 12), "ship_dark"),
            ], rot=(0, -22, -8)),
            P("nacelle_l", (16, 2, 26), [
                C((-4, -4, -10), (8, 8, 26), "ship_dark"),
                C((-5, -5, -12), (10, 10, 2), "ship_dark"),
                C((-3, -3, 16), (6, 6, 2), "engine_glow"),
            ]),
            P("nacelle_r", (-16, 2, 26), [
                C((-4, -4, -10), (8, 8, 26), "ship_dark"),
                C((-5, -5, -12), (10, 10, 2), "ship_dark"),
                C((-3, -3, 16), (6, 6, 2), "engine_glow"),
            ]),
            P("pod_l", (15, -8, -8), [C((0, 0, -6), (3, 4, 12), "ship_dark")]),
            P("pod_r", (-18, -8, -8), [C((0, 0, -6), (3, 4, 12), "ship_dark")]),
            P("cannon", (0, 8, -6), [C((-4, -1, -4), (8, 3, 8), "ship_dark")]),
            P("cannon_barrel", (0, 2, 0), [
                C((-2, 0, -2), (4, 12, 4), "ship_dark"),
                C((-2.5, 12, -2.5), (5, 2, 5), "engine_glow"),
            ], parent="cannon"),
        ],
    },
    "tank_shell": {
        "tex": "tank_shell", "tex_size": 32, "glow": False,
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

def paint(spec, model_name):
    size = spec["tex_size"]
    tex = np.zeros((size, size, 4))
    glow_tex = np.zeros((size, size, 4)) if spec["glow"] else None
    seed = 0
    for part in spec["parts"]:
        for cube in part["cubes"]:
            w, h, d = [int(math.ceil(x)) for x in cube["s"]]
            u, v = cube["uv"]
            for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
                if fw <= 0 or fh <= 0:
                    continue
                seed += 1
                col, glow = paint_face(cube["mat"], face, (fh, fw), seed, model_name)
                tex[fy:fy + fh, fx:fx + fw] = col
                if glow is not None and glow_tex is not None:
                    glow_tex[fy:fy + fh, fx:fx + fw] = glow
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
