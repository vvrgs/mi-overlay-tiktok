#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
preview_render.py — rasterizador por software (z-buffer) para el mod Cataclismo.

Consume la MISMA geometria y las MISMAS texturas que ve el juego:
importa tools/specs.py (SPECS + face_layout + SS) y muestrea los PNG de
src/main/resources/assets/cataclysm/textures/entity/ (pintados a
supersampleo SS=2: las UVs logicas se multiplican por SS al muestrear).

Genera en previews/:
  - beauty_<entidad>.png       (640x640, 3/4 ligeramente elevado)
  - turntable_<entidad>.gif    (320x320, 24 frames, ~80 ms, loop infinito)
  - secuencia_impacto.png      (tira de 5 frames de la coreografia /impacto)
  - contact_sheet.png          (cuadricula con los 5 beauty shots etiquetados)

REGLA DE QUIRALIDAD (bug real, aprendido a golpes):
  El modelo se define con la convencion de Minecraft: y=0 en la CIMA y la y
  creciendo hacia ABAJO. El renderer del juego (estilo BoatRenderer) aplica
  ademas un flip equivalente a rotar 180 grados alrededor de Z — un
  scale(-1,-1,1) — que voltea a la vez X e Y. Si el preview solo invirtiera
  la Y (para que "y del modelo hacia abajo" se pinte hacia arriba en
  pantalla) las texturas y las rotaciones quedarian ESPEJADAS respecto al
  juego. Por eso aqui la proyeccion niega TAMBIEN la X:

      pantalla_x  proporcional a  -x_modelo
      pantalla_y  proporcional a  -y_modelo   (y del modelo crece hacia
                                               abajo => sube en pantalla)

  Implementado como matriz F = diag(-1,-1,1) aplicada tras la transformacion
  de parte + giro de entidad, antes de la camara. Verificacion canonica con
  el tornado: seg7 (boca ancha, pivot y=0) debe verse ARRIBA y seg0 (punta
  fina, pivot y=84) ABAJO tocando el suelo — main() lo comprueba midiendo el
  ancho del embudo por filas en beauty_tornado.png.

Orden de rotacion por parte: ZYX como ModelPart (primero Z, luego Y, luego
X): v' = Rx @ Ry @ Rz @ v. Proyeccion perspectiva (fov ~50), z-buffer por
pixel, SIN back-face culling (hay partes semitransparentes), alpha blending
pintando triangulos ordenados por z de vista decreciente (lejos -> cerca).
Iluminacion direccional desde arriba-izquierda-frente con ambiente 0.45.
Render a 2x y reduccion LANCZOS. Todo determinista y re-ejecutable.
"""

import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import specs  # noqa: E402  (fuente unica de verdad)
from specs import SPECS, SS, face_layout  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, ".."))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources",
                       "assets", "cataclysm", "textures", "entity")
OUT_DIR = os.path.join(ROOT, "previews")

ENTITIES = ["tornado", "bolide", "volcanic_bomb", "tsunami_wall", "impactor"]
DISASTER_OF = {
    "tornado": "/tornado",
    "bolide": "/meteoros",
    "volcanic_bomb": "/volcan",
    "tsunami_wall": "/tsunami",
    "impactor": "/impacto",
}

FOV_DEG = 50.0
AMBIENT = 0.45
# Luz desde arriba (+Y mundo), izquierda de pantalla (-X mundo, ya que
# pantalla_x crece con +x_mundo tras el flip) y frente (+Z, lado de camara).
LIGHT_DIR = None  # se normaliza abajo


def _norm(v):
    v = np.asarray(v, dtype=np.float64)
    return v / max(1e-12, float(np.linalg.norm(v)))


LIGHT_DIR = _norm([-0.55, 1.0, 0.75])

ZENITH = np.array([16.0, 26.0, 66.0])      # azul oscuro arriba
HORIZON = np.array([222.0, 156.0, 102.0])  # horizonte calido
UNDER = np.array([34.0, 26.0, 30.0])       # bajo el horizonte
GROUND_BASE = np.array([48.0, 42.0, 48.0])
GRID_CELL = 16.0  # 16 unidades de modelo = 1 bloque


# ============================ algebra ============================

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def part_rotation(rx, ry, rz):
    """Orden ZYX de ModelPart: primero Z, luego Y, luego X."""
    return rot_x(rx) @ rot_y(ry) @ rot_z(rz)


FLIP = np.diag([-1.0, -1.0, 1.0])  # la regla de quiralidad (ver docstring)


# ============================ geometria ============================

def box_quads(x, y, z, w, h, d):
    """Quads de las 6 caras. Vertices en orden (u0,v0),(u1,v0),(u1,v1),(u0,v1).
    y del modelo crece hacia abajo => y0 es el borde SUPERIOR (v0 arriba)."""
    x0, x1 = x, x + w
    y0, y1 = y, y + h
    z0, z1 = z, z + d
    return {
        "north": [(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)],
        "south": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
        "west": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
        "east": [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)],
        "top": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
        "bottom": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
    }


def part_faces(p, extra_yrot=0.0, extra_off=(0.0, 0.0, 0.0)):
    """Caras de una parte en espacio de MODELO (antes de flip/entidad).
    Cada cara: (verts (4,3), uv_px (4,2), bounds_px (u0,u1,v0,v1))."""
    x, y, z, w, h, d = p["box"]
    wi, hi, di = p["dims_int"]
    u, v = p["texoffs"]
    layout = face_layout(u, v, wi, hi, di)
    rx, ry, rz = p["rot"]
    M = part_rotation(rx, ry + extra_yrot, rz)
    piv = np.asarray(p["pivot"], dtype=np.float64) + np.asarray(extra_off, np.float64)
    out = []
    for face, quad in box_quads(x, y, z, w, h, d).items():
        verts = (M @ np.asarray(quad, np.float64).T).T + piv
        fu, fv, fw, fh = layout[face]
        uvpx = np.array([[fu, fv], [fu + fw, fv],
                         [fu + fw, fv + fh], [fu, fv + fh]], np.float64) * SS
        bounds = (fu * SS, (fu + fw) * SS, fv * SS, (fv + fh) * SS)
        out.append((verts, uvpx, bounds))
    return out


def entity_faces(name, yaw=0.0, part_mods=None, tilt=None):
    """Caras de la entidad en espacio de MUNDO (con quiralidad aplicada).
    part_mods(i) -> (extra_yrot, (dx,dy,dz)) por parte. tilt: matriz 3x3
    aplicada en mundo (despues del flip)."""
    spec = SPECS[name]
    world = (tilt if tilt is not None else np.eye(3)) @ FLIP @ rot_y(yaw)
    faces = []
    for i, p in enumerate(spec["parts"]):
        ey, eoff = part_mods(i) if part_mods else (0.0, (0.0, 0.0, 0.0))
        for verts, uvpx, bounds in part_faces(p, ey, eoff):
            faces.append(((world @ verts.T).T, uvpx, bounds))
    return faces


# ============================ camara ============================

class Camera:
    __slots__ = ("eye", "right", "up", "fwd", "f", "cx", "cy")


def make_camera(eye, center, W, H):
    cam = Camera()
    cam.eye = np.asarray(eye, np.float64)
    cam.fwd = _norm(np.asarray(center, np.float64) - cam.eye)
    cam.right = _norm(np.cross(cam.fwd, np.array([0.0, 1.0, 0.0])))
    cam.up = np.cross(cam.right, cam.fwd)
    cam.f = (H / 2.0) / math.tan(math.radians(FOV_DEG) / 2.0)
    cam.cx, cam.cy = W / 2.0, H / 2.0
    return cam


def fit_camera(all_verts, W, H, elev=0.34, azim=0.0, margin=1.28, center_bias=0.0):
    """Camara que encuadra todos los vertices dados (bounding sphere)."""
    v = np.asarray(all_verts, np.float64).reshape(-1, 3)
    lo, hi = v.min(axis=0), v.max(axis=0)
    center = (lo + hi) / 2.0
    radius = max(1.0, float(np.linalg.norm(v - center, axis=1).max()))
    dist = radius * margin / math.sin(math.radians(FOV_DEG) / 2.0)
    direc = np.array([math.sin(azim) * math.cos(elev),
                      math.sin(elev),
                      math.cos(azim) * math.cos(elev)])
    center = center + np.array([0.0, center_bias * radius, 0.0])
    eye = center + dist * direc
    return make_camera(eye, center, W, H), radius


# ============================ fondo (cielo + suelo) ============================

def background(cam, W, H, extent, ground=True):
    """Gradiente de cielo consistente con la camara + suelo raycast con
    cuadricula tenue. Devuelve (rgb float (H,W,3), zbuf (H,W))."""
    xs = (np.arange(W) + 0.5 - cam.cx) / cam.f
    ys = (cam.cy - (np.arange(H) + 0.5)) / cam.f
    dw = (xs[None, :, None] * cam.right[None, None, :]
          + ys[:, None, None] * cam.up[None, None, :]
          + cam.fwd[None, None, :])                      # dir de rayo (H,W,3)
    dn_y = dw[..., 1] / np.linalg.norm(dw, axis=-1)
    up_t = np.clip(dn_y, 0.0, 1.0) ** 0.62
    img = HORIZON[None, None, :] * (1.0 - up_t[..., None]) + ZENITH[None, None, :] * up_t[..., None]
    down_t = np.clip(-dn_y, 0.0, 1.0) ** 0.55
    img = img * (1.0 - down_t[..., None]) + UNDER[None, None, :] * down_t[..., None]

    zbuf = np.full((H, W), np.inf)
    if ground and cam.eye[1] > 1e-6:
        dwy = dw[..., 1]
        hit = dwy < -1e-6
        tt = np.where(hit, -cam.eye[1] / np.where(hit, dwy, -1.0), 0.0)
        gx = cam.eye[0] + tt * dw[..., 0]
        gz = cam.eye[2] + tt * dw[..., 2]
        r = np.hypot(gx, gz)
        fx = np.abs(gx / GRID_CELL - np.round(gx / GRID_CELL))
        fz = np.abs(gz / GRID_CELL - np.round(gz / GRID_CELL))
        line = np.clip(1.0 - np.minimum(fx, fz) / 0.045, 0.0, 1.0)  # antialias suave
        gcol = GROUND_BASE[None, None, :] * (1.0 + 0.55 * line[..., None])
        fade = np.clip(np.exp(-(r / max(1e-6, extent)) ** 2) * 1.25, 0.0, 1.0)
        col = img * (1.0 - fade[..., None]) + gcol * fade[..., None]
        img = np.where(hit[..., None], col, img)
        zbuf = np.where(hit, tt, np.inf)  # z de vista == t (fwd componente 1)
    return img, zbuf


# ============================ texturas ============================

_TEX_CACHE = {}


def load_texture(name):
    if name not in _TEX_CACHE:
        path = os.path.join(TEX_DIR, f"{name}.png")
        arr = np.asarray(Image.open(path).convert("RGBA"), np.float64) / 255.0
        _TEX_CACHE[name] = arr
    return _TEX_CACHE[name]


def sample_bilinear(tex, u, v, bounds):
    """Muestreo bilinear en coords de pixel, clampeado a la region de la cara
    (evita sangrado entre caras vecinas del atlas)."""
    u0, u1, v0, v1 = bounds
    uc = np.clip(u - 0.5, u0, u1 - 1.0001)
    vc = np.clip(v - 0.5, v0, v1 - 1.0001)
    x0 = np.floor(uc).astype(np.int64)
    y0 = np.floor(vc).astype(np.int64)
    fx = (uc - x0)[..., None]
    fy = (vc - y0)[..., None]
    x1 = np.minimum(x0 + 1, int(round(u1)) - 1)
    y1 = np.minimum(y0 + 1, int(round(v1)) - 1)
    t00 = tex[y0, x0]
    t10 = tex[y0, x1]
    t01 = tex[y1, x0]
    t11 = tex[y1, x1]
    return (t00 * (1 - fx) * (1 - fy) + t10 * fx * (1 - fy)
            + t01 * (1 - fx) * fy + t11 * fx * fy)


# ============================ rasterizador ============================

def _face_shade(verts, cam):
    """Sombreado plano: direccional arriba-izquierda-frente + ambiente 0.45.
    Sin culling: la normal se orienta hacia la camara (doble cara)."""
    n = np.cross(verts[1] - verts[0], verts[3] - verts[0])
    ln = np.linalg.norm(n)
    if ln < 1e-12:
        return AMBIENT
    n = n / ln
    to_cam = cam.eye - verts.mean(axis=0)
    if float(np.dot(n, to_cam)) < 0.0:
        n = -n
    diff = max(0.0, float(np.dot(n, LIGHT_DIR)))
    return AMBIENT + (1.0 - AMBIENT) * diff


def _edge(ax, ay, bx, by, px, py):
    return (px - ax) * (by - ay) - (py - ay) * (bx - ax)


def raster_triangle(prgb, pal, zbuf, sx, sy, zv, uvp, bounds, shade, tex):
    """Un triangulo: barycentricas, UV con correccion de perspectiva,
    z-test contra el z-buffer y alpha blending 'over' (premultiplicado)."""
    H, W = zbuf.shape
    minx = max(0, int(math.floor(sx.min())))
    maxx = min(W - 1, int(math.ceil(sx.max())))
    miny = max(0, int(math.floor(sy.min())))
    maxy = min(H - 1, int(math.ceil(sy.max())))
    if minx > maxx or miny > maxy:
        return
    area = _edge(sx[0], sy[0], sx[1], sy[1], sx[2], sy[2])
    if abs(area) < 1e-9:
        return
    px = np.arange(minx, maxx + 1, dtype=np.float64) + 0.5
    py = np.arange(miny, maxy + 1, dtype=np.float64) + 0.5
    PX = px[None, :]
    PY = py[:, None]
    w0 = _edge(sx[1], sy[1], sx[2], sy[2], PX, PY)
    w1 = _edge(sx[2], sy[2], sx[0], sy[0], PX, PY)
    w2 = _edge(sx[0], sy[0], sx[1], sy[1], PX, PY)
    if area < 0.0:
        area, w0, w1, w2 = -area, -w0, -w1, -w2
    inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
    if not inside.any():
        return
    b0, b1, b2 = w0 / area, w1 / area, w2 / area
    invz = b0 / zv[0] + b1 / zv[1] + b2 / zv[2]
    zf = 1.0 / np.maximum(invz, 1e-12)
    uu = (b0 * uvp[0, 0] / zv[0] + b1 * uvp[1, 0] / zv[1] + b2 * uvp[2, 0] / zv[2]) * zf
    vv = (b0 * uvp[0, 1] / zv[0] + b1 * uvp[1, 1] / zv[1] + b2 * uvp[2, 1] / zv[2]) * zf

    zsub = zbuf[miny:maxy + 1, minx:maxx + 1]
    rgba = sample_bilinear(tex, uu, vv, bounds)
    a = rgba[..., 3]
    ok = inside & (zf < zsub - 1e-3) & (a > 0.004)
    if not ok.any():
        return
    aa = a[ok][:, None]
    src = rgba[..., :3][ok] * shade * 255.0
    psub = prgb[miny:maxy + 1, minx:maxx + 1]
    asub = pal[miny:maxy + 1, minx:maxx + 1]
    psub[ok] = psub[ok] * (1.0 - aa) + src * aa
    asub[ok] = asub[ok] * (1.0 - aa[:, 0]) + aa[:, 0]
    zsub[ok] = zf[ok]


def draw_faces(prgb, pal, zbuf, faces, cam, tex):
    """Proyecta, ordena por z de vista decreciente (lejos->cerca) y pinta."""
    tris = []
    for verts, uvpx, bounds in faces:
        rel = verts - cam.eye[None, :]
        zv = rel @ cam.fwd
        if (zv < 0.5).any():
            continue  # detras del plano cercano (no ocurre con fit_camera)
        sx = cam.cx + cam.f * (rel @ cam.right) / zv
        sy = cam.cy - cam.f * (rel @ cam.up) / zv
        shade = _face_shade(verts, cam)
        for i0, i1, i2 in ((0, 1, 2), (0, 2, 3)):
            idx = [i0, i1, i2]
            depth = float(zv[idx].mean())
            tris.append((depth, sx[idx], sy[idx], zv[idx], uvpx[idx], bounds, shade))
    tris.sort(key=lambda t: -t[0])  # z decreciente: primero lo lejano
    for _, sx, sy, zv, uvp, bounds, shade in tris:
        raster_triangle(prgb, pal, zbuf, sx, sy, zv, uvp, bounds, shade, tex)


# ============================ escenas de entidad ============================

def _lift_units(name):
    """Elevacion sobre el suelo (unidades de modelo) para los que 'vuelan'."""
    return {"bolide": 10.0, "volcanic_bomb": 7.0, "impactor": 16.0}.get(name, 0.0)


def _placement_dy(frames_faces, lift):
    min_y = min(float(f[0][:, 1].min()) for faces in frames_faces for f in faces)
    return -min_y + lift


def _all_verts(frames_faces, dy):
    out = []
    for faces in frames_faces:
        for verts, _, _ in faces:
            v = verts.copy()
            v[:, 1] += dy
            out.append(v)
    return np.concatenate(out, axis=0)


def render_scene(name, faces_list, W, H, elev, margin=1.28, azim=0.0):
    """Renderiza frames de una entidad sobre cielo+suelo con camara comun.
    faces_list: lista de listas de caras (una por frame, en mundo, sin
    colocar). Devuelve lista de arrays uint8 (H,W,3)."""
    tex = load_texture(name)
    dy = _placement_dy(faces_list, _lift_units(name))
    verts = _all_verts(faces_list, dy)
    cam, radius = fit_camera(verts, W, H, elev=elev, margin=margin, azim=azim)
    bg, zbg = background(cam, W, H, extent=radius * 5.0)
    frames = []
    for faces in faces_list:
        prgb = bg.copy()
        pal = np.ones((H, W))
        zbuf = zbg.copy()
        placed = [(v + np.array([0.0, dy, 0.0]), uv, b) for v, uv, b in faces]
        draw_faces(prgb, pal, zbuf, placed, cam, tex)
        frames.append(np.clip(prgb, 0, 255).astype(np.uint8))
    return frames


def render_entity_rgba(name, W, H, yaw=0.0, tilt=None, part_mods=None, margin=1.06):
    """Render de la entidad sola sobre fondo transparente (para compositar)."""
    tex = load_texture(name)
    faces = entity_faces(name, yaw=yaw, part_mods=part_mods, tilt=tilt)
    verts = np.concatenate([f[0] for f in faces], axis=0)
    cam, _ = fit_camera(verts, W, H, elev=0.18, margin=margin)
    prgb = np.zeros((H, W, 3))
    pal = np.zeros((H, W))
    zbuf = np.full((H, W), np.inf)
    draw_faces(prgb, pal, zbuf, faces, cam, tex)
    rgb = prgb / np.maximum(pal, 1e-6)[..., None]
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(pal * 255.0, 0, 255)])
    return out.astype(np.uint8)


# ============================ mods por entidad ============================

def tornado_beauty_mods(i):
    return i * 1.1, (0.0, 0.0, 0.0)


def tornado_turntable_mods(frame):
    def mods(i):
        yrot = frame * 0.55 - 0.045 * i + i * 1.1  # spin + fase por segmento
        amp = 1.5 + 0.6 * i
        off = (math.sin(frame * 0.13 + i * 0.7) * amp, 0.0,
               math.cos(frame * 0.11 + i * 0.7) * amp)
        return yrot, off
    return mods


def beauty_tilt(name):
    """bolide/impactor inclinados como si volaran en diagonal."""
    if name in ("bolide", "impactor"):
        return rot_z(-0.45) @ rot_x(0.22)
    return None


# ============================ salidas ============================

def downscale(arr, size):
    return Image.fromarray(arr).resize((size, size), Image.LANCZOS)


def gen_beauty(name):
    """Beauty shot 640 (render 1280 + LANCZOS). Devuelve (final, fondo) PIL."""
    W = H = 1280
    mods = tornado_beauty_mods if name == "tornado" else None
    faces = entity_faces(name, yaw=0.75, part_mods=(lambda i: mods(i)) if mods else None,
                         tilt=beauty_tilt(name))
    frames = render_scene(name, [faces], W, H, elev=0.34)
    final = downscale(frames[0], 640)
    # fondo solo (misma camara determinista): re-render sin entidad
    tex_dummy = None  # noqa: F841
    dy = _placement_dy([faces], _lift_units(name))
    verts = _all_verts([faces], dy)
    cam, radius = fit_camera(verts, W, H, elev=0.34)
    bg, _ = background(cam, W, H, extent=radius * 5.0)
    bg_img = downscale(np.clip(bg, 0, 255).astype(np.uint8), 640)
    path = os.path.join(OUT_DIR, f"beauty_{name}.png")
    final.save(path)
    return path, final, bg_img


def gen_turntable(name):
    """GIF 320, 24 frames, loop infinito, ~80 ms/frame (render 640 + LANCZOS)."""
    W = H = 640
    n_frames = 24
    faces_list = []
    for fr in range(n_frames):
        yaw = 2.0 * math.pi * fr / n_frames
        mods = tornado_turntable_mods(fr) if name == "tornado" else None
        faces_list.append(entity_faces(name, yaw=yaw, part_mods=mods))
    frames = render_scene(name, faces_list, W, H, elev=0.30)
    imgs = [downscale(f, 320).quantize(colors=256, method=Image.Quantize.MEDIANCUT,
                                       dither=Image.Dither.FLOYDSTEINBERG)
            for f in frames]
    path = os.path.join(OUT_DIR, f"turntable_{name}.gif")
    imgs[0].save(path, save_all=True, append_images=imgs[1:],
                 duration=80, loop=0, disposal=2)
    return path


# ---------- secuencia de impacto ----------

def _panel_sky(P, warm=0.0):
    t = (np.arange(P) / P)[:, None, None] ** 1.15
    top = np.array([10.0, 14.0, 42.0]) + warm * np.array([30.0, 8.0, 0.0])
    bot = np.array([88.0, 58.0, 86.0]) + warm * np.array([70.0, 30.0, 0.0])
    return np.ascontiguousarray(np.broadcast_to(top * (1 - t) + bot * t, (P, P, 3)))


def _glow(img, cx, cy, r, color, strength=1.0):
    P = img.shape[0]
    yy, xx = np.mgrid[0:P, 0:P]
    d2 = ((xx - cx) ** 2 + (yy - cy) ** 2) / max(1e-6, r * r)
    img += np.exp(-d2)[..., None] * np.asarray(color, np.float64) * strength


def _blob(img, cx, cy, r, color, a=0.8):
    P = img.shape[0]
    yy, xx = np.mgrid[0:P, 0:P]
    d2 = ((xx - cx) ** 2 + (yy - cy) ** 2) / max(1e-6, r * r)
    A = np.clip(np.exp(-d2) * a, 0.0, 1.0)[..., None]
    img[:] = img * (1 - A) + np.asarray(color, np.float64) * A


def gen_impact_sequence():
    """Tira horizontal de 5 frames de la coreografia del /impacto."""
    P = 300
    panels = []

    # (1) T+0s: punto de luz pequeno en el cielo
    p1 = _panel_sky(P)
    _glow(p1, P * 0.62, P * 0.24, 10, [255, 240, 210], 0.9)
    _glow(p1, P * 0.62, P * 0.24, 2.6, [255, 255, 255], 1.0)
    panels.append(p1)

    # (2) T+3s: punto mas grande y brillante, halo calido
    p2 = _panel_sky(P, warm=0.35)
    _glow(p2, P * 0.55, P * 0.30, 34, [255, 170, 90], 0.85)
    _glow(p2, P * 0.55, P * 0.30, 13, [255, 235, 200], 1.0)
    _glow(p2, P * 0.55, P * 0.30, 5, [255, 255, 255], 1.2)
    panels.append(p2)

    # (3) T+8s: el impactor REAL cruzando en diagonal con estela pintada
    p3 = _panel_sky(P, warm=0.6)
    x0, y0 = P * 0.12, P * 0.16     # cola de la estela
    x1, y1 = P * 0.64, P * 0.55     # cabeza (posicion de la entidad)
    n_tr = 16
    for k in range(n_tr):
        t = k / (n_tr - 1)
        cx = x0 + (x1 - x0) * t
        cy = y0 + (y1 - y0) * t
        r = 5 + 26 * t
        col = (np.array([255, 250, 235]) * t + np.array([255, 140, 60]) * (1 - t))
        _glow(p3, cx, cy, r, col, 0.16 + 0.5 * t)
    ent = render_entity_rgba("impactor", 360, 360,
                             yaw=0.6, tilt=rot_z(-0.5) @ rot_x(0.2))
    ent_img = Image.fromarray(ent).resize((168, 168), Image.LANCZOS)
    frame3 = Image.fromarray(np.clip(p3, 0, 255).astype(np.uint8)).convert("RGBA")
    frame3.alpha_composite(ent_img, (int(x1) - 84, int(y1) - 84))
    panels.append(np.asarray(frame3.convert("RGB"), np.float64))

    # (4) T+10s: flash blanco casi total con silueta
    p4 = np.full((P, P, 3), 248.0)
    _glow(p4, P / 2, P / 2, P * 0.5, [7, -6, -18], 1.0)  # centro apenas calido
    sil = np.asarray(Image.fromarray(ent[..., 3]).resize((190, 190), Image.LANCZOS),
                     np.float64) / 255.0
    ox = oy = (P - 190) // 2
    p4[oy:oy + 190, ox:ox + 190] *= (1.0 - 0.20 * sil[..., None])
    yy, xx = np.mgrid[0:P, 0:P]
    vig = np.clip(np.hypot(xx - P / 2, yy - P / 2) / (P * 0.72), 0, 1) ** 2
    p4 *= (1.0 - 0.10 * vig[..., None])
    panels.append(p4)

    # (5) T+30s: hongo de humo sobre el crater
    rng = specs.stable_rng("preview", "impacto", "hongo")
    p5 = _panel_sky(P, warm=0.5)
    gy = int(P * 0.78)
    grad = (np.arange(P - gy) / max(1, P - gy))[:, None, None]
    p5[gy:] = np.array([30.0, 24.0, 26.0]) * (1 - 0.4 * grad) + 8.0 * grad
    # crater
    cx, cy = P * 0.5, P * 0.84
    yy, xx = np.mgrid[0:P, 0:P]
    ell = ((xx - cx) / 74.0) ** 2 + ((yy - cy) / 15.0) ** 2
    p5[ell < 1.0] = p5[ell < 1.0] * 0.25
    rim = (ell >= 1.0) & (ell < 1.45)
    p5[rim] = p5[rim] * 0.6 + np.array([90.0, 60.0, 40.0]) * 0.4
    # resplandor interno del crater
    _glow(p5, cx, cy - 6, 30, [255, 120, 40], 0.55)
    # tallo del hongo
    for k in range(14):
        t = k / 13.0
        jy = float(rng.normal(0, 3))
        jx = float(rng.normal(0, 4))
        y = P * 0.80 - t * P * 0.42 + jy
        g = 60 + 55 * t
        _blob(p5, cx + jx + math.sin(t * 5.1) * 6, y, 20 + 12 * t,
              [g, g * 0.94, g * 0.92], 0.75)
    # sombrero
    capy = P * 0.335
    for k in range(12):
        ang = 2 * math.pi * k / 12
        bx = cx + math.cos(ang) * 58 + float(rng.normal(0, 3))
        by = capy + math.sin(ang) * 17 + float(rng.normal(0, 2))
        _blob(p5, bx, by, 30, [128, 120, 118], 0.8)
    _blob(p5, cx, capy, 46, [148, 140, 138], 0.85)
    _glow(p5, cx, P * 0.72, 22, [255, 110, 40], 0.4)
    panels.append(p5)

    # montaje de la tira con etiquetas de tiempo
    labels = ["T+0s", "T+3s", "T+8s", "T+10s", "T+30s"]
    gap, band = 10, 34
    Wt = gap + 5 * (P + gap)
    Ht = P + band + 2 * gap
    strip = Image.new("RGB", (Wt, Ht), (12, 12, 18))
    draw = ImageDraw.Draw(strip)
    try:
        font = ImageFont.load_default(size=18)
    except TypeError:  # Pillow < 10.1
        font = ImageFont.load_default()
    for i, (panel, lab) in enumerate(zip(panels, labels)):
        x = gap + i * (P + gap)
        strip.paste(Image.fromarray(np.clip(panel, 0, 255).astype(np.uint8)), (x, gap))
        tw = draw.textlength(lab, font=font)
        draw.text((x + (P - tw) / 2, gap + P + 7), lab, fill=(235, 225, 205), font=font)
    path = os.path.join(OUT_DIR, "secuencia_impacto.png")
    strip.save(path)
    return path


def gen_contact_sheet(beauties):
    """Cuadricula 3x2 con los 5 beauty shots etiquetados por desastre."""
    tile, band, gap = 340, 40, 14
    cols, rows = 3, 2
    Wt = gap + cols * (tile + gap)
    Ht = 56 + rows * (tile + band + gap)
    sheet = Image.new("RGB", (Wt, Ht), (14, 13, 20))
    draw = ImageDraw.Draw(sheet)
    try:
        font_t = ImageFont.load_default(size=26)
        font_l = ImageFont.load_default(size=20)
    except TypeError:
        font_t = font_l = ImageFont.load_default()
    title = "CATACLISMO - previews de entidades"
    tw = draw.textlength(title, font=font_t)
    draw.text(((Wt - tw) / 2, 14), title, fill=(240, 220, 190), font=font_t)
    for i, name in enumerate(ENTITIES):
        col, row = i % cols, i // cols
        x = gap + col * (tile + gap)
        y = 56 + row * (tile + band + gap)
        img = beauties[name].resize((tile, tile), Image.LANCZOS)
        sheet.paste(img, (x, y))
        lab = f"{DISASTER_OF[name]}  ({name})"
        lw = draw.textlength(lab, font=font_l)
        draw.rectangle([x, y + tile, x + tile, y + tile + band], fill=(24, 22, 32))
        draw.text((x + (tile - lw) / 2, y + tile + 9), lab,
                  fill=(225, 215, 195), font=font_l)
    path = os.path.join(OUT_DIR, "contact_sheet.png")
    sheet.save(path)
    return path


# ============================ verificaciones ============================

def funnel_widths(final_img, bg_img):
    """Ancho del embudo por fila: columnas cuyo color difiere del fondo."""
    a = np.asarray(final_img, np.int64)
    b = np.asarray(bg_img, np.int64)
    diff = np.abs(a - b).sum(axis=2) > 24
    return diff.sum(axis=1)  # ancho por fila


def check_funnel(final_img, bg_img):
    widths = funnel_widths(final_img, bg_img)
    H = len(widths)
    top = widths[:H // 2]
    bot = widths[H // 2:]
    top_rows = top[top > 0]
    bot_rows = bot[bot > 0]
    assert len(top_rows) > 0 and len(bot_rows) > 0, "tornado no visible"
    mean_top = float(top_rows.mean())
    mean_bot = float(bot_rows.mean())
    assert mean_top > mean_bot, (
        f"QUIRALIDAD ROTA: embudo mas ancho abajo ({mean_bot:.1f}) que arriba "
        f"({mean_top:.1f}) - la boca (seg7) debe quedar ARRIBA")
    return mean_top, mean_bot


def gif_frame_count(path):
    with Image.open(path) as im:
        return im.n_frames


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    generated = []
    checks = []

    beauties = {}
    tornado_pair = None
    for name in ENTITIES:
        path, final, bg = gen_beauty(name)
        beauties[name] = final
        generated.append(path)
        if name == "tornado":
            tornado_pair = (path, bg)
        print(f"[beauty] {path}")

    for name in ENTITIES:
        path = gen_turntable(name)
        generated.append(path)
        print(f"[turntable] {path}")

    generated.append(gen_impact_sequence())
    print(f"[secuencia] {generated[-1]}")
    generated.append(gen_contact_sheet(beauties))
    print(f"[contact] {generated[-1]}")

    # --- verificaciones finales ---
    for path in generated:
        assert os.path.isfile(path) and os.path.getsize(path) > 0, f"falta {path}"
    checks.append(f"{len(generated)} archivos existen y no estan vacios")

    for name in ENTITIES:
        gp = os.path.join(OUT_DIR, f"turntable_{name}.gif")
        n = gif_frame_count(gp)
        assert n == 24, f"{gp}: {n} frames (se esperaban 24)"
    checks.append("los 5 GIF tienen exactamente 24 frames (loop infinito, 80 ms)")

    tpath, tbg = tornado_pair
    saved = Image.open(tpath).convert("RGB")  # relee lo GUARDADO en disco
    mean_top, mean_bot = check_funnel(saved, tbg)
    checks.append(
        f"quiralidad OK: embudo del tornado mas ancho en la mitad superior "
        f"({mean_top:.1f} px) que en la inferior ({mean_bot:.1f} px)")

    print("\n=== VERIFICACIONES ===")
    for c in checks:
        print(" -", c)
    print("\n=== GENERADOS ===")
    for p in generated:
        print(" -", os.path.abspath(p))


if __name__ == "__main__":
    main()
