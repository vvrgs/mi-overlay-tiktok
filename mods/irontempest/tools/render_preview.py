#!/usr/bin/env python3
"""Renderer 3D por software de los modelos REALES del mod (geometría de SPECS +
texturas pintadas por gen_models). Genera turntables y beauty shots fieles a lo
que dibuja el juego (cubos, UVs, rotaciones de partes, glow emisivo)."""
import math, os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_models as gm

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview")
os.makedirs(OUT, exist_ok=True)


def rot_matrix(rx, ry, rz):
    """Rz*Ry*Rx en el espacio de modelo (x derecha, y ABAJO, z atrás)."""
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def build_model(spec, model_name="preview"):
    """Devuelve lista de (tri_verts[3,3] en bloques Y-arriba, uv[3,2], glow_flag)
    por cada triángulo del modelo, con texturas ya asignadas."""
    gm.pack(spec)
    tex, glow_tex = gm.paint(spec, model_name)
    ss = spec.get("ss", 1)
    size = spec["tex_size"] * ss  # píxeles REALES del lienzo (atlas lógico × SS)
    parts = {p["name"]: p for p in spec["parts"]}

    def chain(part):
        """Lista de (R, pivot_abs) desde la raíz hasta esta parte."""
        seq = []
        while part is not None:
            seq.append((rot_matrix(*part["rot"]), np.array(part["pivot"], dtype=float)))
            parent = part.get("parent")
            if parent and parent.endswith("_rel"):
                parent = parent[:-4]
            part = parts.get(parent) if parent else None
        return list(reversed(seq))

    tris = []
    for part in spec["parts"]:
        transforms = chain(part)
        for cube in part["cubes"]:
            ox, oy, oz = cube["o"]
            w, h, d = cube["s"]
            u, v = cube["uv"]
            wi, hi, di = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
            rects = gm.face_rects(u, v, wi, hi, di)
            # 8 esquinas locales a la parte
            c = {}
            for i, (X, Y, Z) in enumerate([(ox, oy, oz), (ox + w, oy, oz), (ox + w, oy + h, oz),
                                            (ox, oy + h, oz), (ox, oy, oz + d), (ox + w, oy, oz + d),
                                            (ox + w, oy + h, oz + d), (ox, oy + h, oz + d)]):
                p = np.array([X, Y, Z], dtype=float)
                # transformar por la cadena: hijo primero, raíz al final
                for R, pivot in reversed(transforms):
                    # pivote del hijo es absoluto: convertir a offset dentro del padre
                    pass
                c[i] = p
            # aplicar cadena correctamente: v = R_root(... R_child(v) + (piv_child - piv_parent) ...) + piv_root
            pts = np.array([c[i] for i in range(8)])
            offs = []
            prev_piv = None
            for R, piv in transforms:  # raíz → hoja
                off = piv if prev_piv is None else piv - prev_piv
                offs.append((R, off))
                prev_piv = piv
            for i in range(8):
                p = pts[i]
                for R, off in reversed(offs):  # hoja primero
                    p = R @ p + off
                pts[i] = p
            # caras: (índices de esquinas en orden, rect, ejes uv)
            faces = [
                ("up",    [4, 5, 1, 0]), ("down", [3, 2, 6, 7]),
                ("front", [0, 1, 2, 3]), ("back", [5, 4, 7, 6]),
                ("side1", [4, 0, 3, 7]), ("side2", [1, 5, 6, 2]),
            ]
            for fname, idx in faces:
                fx, fy, fw, fh = rects[fname]
                if fw <= 0 or fh <= 0:
                    continue
                quad = pts[idx]
                uvq = np.array([[fx, fy], [fx + fw, fy], [fx + fw, fy + fh], [fx, fy + fh]],
                               dtype=float) * ss
                glow = False
                if glow_tex is not None:
                    reg = glow_tex[fy * ss:(fy + fh) * ss, fx * ss:(fx + fw) * ss]
                    glow = reg[..., 3].max() > 40
                for a, b_, cc in [(0, 1, 2), (0, 2, 3)]:
                    tris.append((quad[[a, b_, cc]].copy(), uvq[[a, b_, cc]].copy(), glow))
    return tris, tex, (glow_tex if spec["glow"] else None), size


def render(tris, tex, glow_tex, tex_size, cam_yaw, cam_pitch, dist, W=720, H=560,
           ground_y=None, fov=42.0, center=(0.0, 0.0, 0.0), bg=None):
    """Rasterizador con z-buffer y sombreado direccional. Modelo en unidades /16,
    Y-arriba tras el flip. cam_yaw/pitch en grados."""
    img = np.zeros((H, W, 3), dtype=float)
    if bg is not None:
        img[:] = bg
    else:
        # cielo degradado + suelo
        for y in range(H):
            t = y / H
            img[y, :] = np.array([26, 30, 40]) * (1 - t) + np.array([48, 44, 38]) * t
    zbuf = np.full((H, W), 1e9)

    cy, sy = math.cos(math.radians(cam_yaw)), math.sin(math.radians(cam_yaw))
    cp, sp = math.cos(math.radians(cam_pitch)), math.sin(math.radians(cam_pitch))
    cam_pos = np.array([sy * cp * dist, sp * dist, cy * cp * dist]) + np.array(center)
    fwd = (np.array(center) - cam_pos)
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = (W / 2) / math.tan(math.radians(fov / 2))
    light1 = np.array([0.5, 0.8, 0.3]); light1 /= np.linalg.norm(light1)
    light2 = np.array([-0.6, 0.2, -0.75]); light2 /= np.linalg.norm(light2)

    def to_view(p):
        # modelo (Y abajo, unidades) → mundo (bloques, Y arriba). Negar X ADEMÁS
        # de Y replica el ZP(180) del juego (rotación propia, no espejo): los
        # decals se leen exactamente como en el juego.
        q = np.array([-p[0], -(p[1] - (ground_y if ground_y is not None else 0.0)), p[2]]) / 16.0
        rel = q - cam_pos
        return np.array([rel @ right, rel @ up, rel @ fwd])

    order = []
    for i, (tri, uvq, glow) in enumerate(tris):
        vs = np.array([to_view(v) for v in tri])
        if np.all(vs[:, 2] < 0.1):
            continue
        order.append((vs[:, 2].mean(), i, vs))
    for _, i, vs in order:
        tri, uvq, glow = tris[i]
        # normal en mundo para sombrear
        a = tri[1] - tri[0]; b = tri[2] - tri[0]
        n = np.cross([a[0], -a[1], a[2]], [b[0], -b[1], b[2]])
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        n /= ln
        shade = 0.42 + 0.5 * max(0.0, n @ light1) + 0.25 * max(0.0, n @ light2)
        if glow:
            shade = 1.35
        # proyección
        pts2 = []
        ok = True
        for v in vs:
            if v[2] < 0.1:
                ok = False
                break
            pts2.append([W / 2 + v[0] * f / v[2], H / 2 - v[1] * f / v[2], v[2]])
        if not ok:
            continue
        pts2 = np.array(pts2)
        minx = max(0, int(pts2[:, 0].min())); maxx = min(W - 1, int(pts2[:, 0].max()) + 1)
        miny = max(0, int(pts2[:, 1].min())); maxy = min(H - 1, int(pts2[:, 1].max()) + 1)
        if minx >= maxx or miny >= maxy:
            continue
        xs, ys = np.meshgrid(np.arange(minx, maxx), np.arange(miny, maxy))
        x0, y0 = pts2[0, 0], pts2[0, 1]
        x1, y1 = pts2[1, 0], pts2[1, 1]
        x2, y2 = pts2[2, 0], pts2[2, 1]
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9:
            continue
        w0 = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den
        w1 = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den
        w2 = 1 - w0 - w1
        mask = (w0 >= -0.001) & (w1 >= -0.001) & (w2 >= -0.001)
        if not mask.any():
            continue
        # perspectiva-correcta con 1/z
        iz = w0 / pts2[0, 2] + w1 / pts2[1, 2] + w2 / pts2[2, 2]
        z = 1.0 / np.maximum(iz, 1e-9)
        uu = (w0 * uvq[0, 0] / pts2[0, 2] + w1 * uvq[1, 0] / pts2[1, 2] + w2 * uvq[2, 0] / pts2[2, 2]) * z
        vv = (w0 * uvq[0, 1] / pts2[0, 2] + w1 * uvq[1, 1] / pts2[1, 2] + w2 * uvq[2, 1] / pts2[2, 2]) * z
        ui = np.clip(uu.astype(int), 0, tex_size - 1)
        vi = np.clip(vv.astype(int), 0, tex_size - 1)
        col = tex[vi, ui, :3] * shade
        if glow and glow_tex is not None:
            g = glow_tex[vi, ui]
            gm_mask = g[..., 3] > 40
            col = np.where(gm_mask[..., None], g[..., :3] * 1.15, col)
        zsub = zbuf[miny:maxy, minx:maxx]
        write = mask & (z < zsub)
        zsub[write] = z[write]
        img[miny:maxy, minx:maxx][write] = np.clip(col[write], 0, 255)
    return img


def save(img, name):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").save(os.path.join(OUT, name))
    print("→", name)


def main():
    # TANQUE: turntable GIF + beauty shot
    tris, tex, glow, size = build_model(gm.SPECS["tank"], "tank")
    frames = []
    for k in range(24):
        img = render(tris, tex, glow, size, cam_yaw=k * 15 + 30, cam_pitch=18, dist=7.2,
                     ground_y=24, center=(0, 1.05, 0))
        frames.append(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB"))
        print(f"tank frame {k+1}/24")
    frames[0].save(os.path.join(OUT, "tank_turntable.gif"), save_all=True,
                   append_images=frames[1:], duration=110, loop=0)
    print("→ tank_turntable.gif")
    save(render(tris, tex, glow, size, cam_yaw=35, cam_pitch=14, dist=6.8,
                ground_y=24, center=(0, 1.0, 0), W=960, H=640), "tank_beauty.png")

    # NAVE: beauty shot con haz compuesto
    tris, tex, glow, size = build_model(gm.SPECS["warship"], "warship")
    img = render(tris, tex, glow, size, cam_yaw=140, cam_pitch=-12, dist=13.5,
                 ground_y=0, center=(0, -0.4, 0.6), W=960, H=720)
    # Haz orbital compuesto (usando la dirección visual del cañón ventral)
    H_, W_ = img.shape[:2]
    bx, by = int(W_ * 0.485), int(H_ * 0.585)  # boca del cañón en pantalla (aprox del render)
    beam_w = 26
    for y in range(by, H_):
        t = (y - by) / max(1, H_ - by)
        wq = beam_w * (1.0 + 0.15 * math.sin(y * 0.15))
        x0, x1 = int(bx - wq), int(bx + wq)
        xs = np.arange(max(0, x0), min(W_, x1))
        fall = np.clip(1 - np.abs(xs - bx) / max(1e-6, wq), 0.0, 1.0)
        glow_col = np.array([120, 200, 255])
        core = np.array([255, 255, 255])
        col = glow_col[None, :] * fall[:, None] ** 1.5 * 0.9 + core[None, :] * (fall[:, None] ** 6)
        img[y, xs] = np.clip(img[y, xs] + col * (0.8 + 0.2 * (1 - t)), 0, 255)
    # flash de impacto al pie
    yy, xx = np.mgrid[0:H_, 0:W_]
    r = np.sqrt((xx - bx) ** 2 + ((yy - (H_ - 30)) * 1.8) ** 2)
    flash = np.clip(1 - r / 120, 0, 1) ** 2
    img += flash[..., None] * np.array([255, 210, 150]) * 0.9
    save(img, "warship_beam.png")
    save(render(tris, tex, glow, size, cam_yaw=210, cam_pitch=22, dist=14.0,
                ground_y=0, center=(0, 0, 0.6), W=960, H=640), "warship_beauty.png")

    # MISIL y COHETE
    tris, tex, glow, size = build_model(gm.SPECS["cruise_missile"], "cruise_missile")
    save(render(tris, tex, glow, size, cam_yaw=115, cam_pitch=15, dist=5.2,
                ground_y=0, center=(0, 0, -0.2), W=960, H=480), "missile_beauty.png")
    tris, tex, glow, size = build_model(gm.SPECS["mlrs_rocket"], "mlrs_rocket")
    save(render(tris, tex, glow, size, cam_yaw=120, cam_pitch=18, dist=2.6,
                ground_y=0, center=(0, 0, 0), W=720, H=420), "rocket_beauty.png")


if __name__ == "__main__":
    main()
