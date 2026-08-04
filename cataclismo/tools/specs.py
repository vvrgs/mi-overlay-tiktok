#!/usr/bin/env python3
"""
SPECS: fuente unica de verdad de los modelos procedurales del mod Cataclismo.

Genera:
  - src/main/java/com/vvrgs/cataclysm/client/render/Geometry.java (LayerDefinitions)
  - src/main/resources/assets/cataclysm/textures/entity/*.png (atlas pintados)

Reglas aprendidas a golpes (ver PROMPT):
  - El atlas logico es 1 texel/unidad; el PNG se pinta supersampleado x2 SIN
    tocar el atlas logico (los UVs de LayerDefinition son normalizados).
  - Seeds ESTABLES por hash de (entidad|parte|material|cara) — jamas un
    contador global.
  - Texturas con scroll (lamina de agua del tsunami) EXACTAMENTE periodicas
    en el eje de scroll: se pinta una tira del periodo y se tesela.

El preview renderer (tools/preview_render.py) importa este modulo: la misma
geometria y las mismas texturas que ve el juego.
"""

import hashlib
import os

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_OUT = os.path.join(HERE, "..", "src", "main", "java",
                        "com", "vvrgs", "cataclysm", "client", "render", "Geometry.java")
TEX_OUT = os.path.join(HERE, "..", "src", "main", "resources",
                       "assets", "cataclysm", "textures", "entity")

SS = 2  # supersampleo del PNG (x2): el atlas logico NO cambia


def part(name, box, pivot=(0, 0, 0), rot=(0, 0, 0), material="rock"):
    """box = (x, y, z, w, h, d) en unidades de modelo (1/16 de bloque)."""
    return {"name": name, "box": box, "pivot": pivot, "rot": rot, "material": material}


# ============================ SPECS ============================
# Convencion de render (estilo BoatRenderer): el renderer hace
# translate(0, alturaEnBloques, 0) + scale(-1,-1,1): el modelo se define con
# y=0 en la CIMA y creciendo hacia abajo. Rotaciones de ModelPart en orden
# ZYX: los spins puros van SOLO en yRot (nada de composiciones que precesionen).

def tornado_parts():
    parts = []
    n = 8
    seg_h = 12
    for i in range(n):  # i=0 abajo (punta fina), i=7 arriba (boca ancha)
        hw = 2.0 + i * 1.5
        w = round(hw * 2)
        y_top = (n - 1 - i) * seg_h  # y del modelo: 0 arriba
        parts.append(part(
            f"seg{i}",
            (-hw, 0, -hw, w, seg_h, w),
            pivot=(0, y_top, 0),
            material="funnel",
        ))
    return parts


SPECS = {
    "tornado": {
        "tex": (128, 256),
        "parts": tornado_parts(),
        # el modelo mide 96 unidades (6 bloques): el renderer escala x2.33 => 14 bl
        "render_height_blocks": 6.0,
        "glow": False,
    },
    "bolide": {
        "tex": (64, 64),
        "parts": [
            part("core", (-5, -5, -5, 10, 10, 10), material="meteor"),
            part("shard0", (-2, -8, -2, 4, 6, 4), rot=(0.4, 0.3, 0.0), material="meteor"),
            part("shard1", (-8, -2, -2, 6, 4, 4), rot=(0.0, 0.5, 0.4), material="meteor"),
            part("shard2", (-2, -2, 3, 4, 4, 6), rot=(0.3, 0.0, 0.5), material="meteor"),
        ],
        "render_height_blocks": 0.8,
        "glow": True,
    },
    "volcanic_bomb": {
        "tex": (64, 64),
        "parts": [
            part("core", (-4, -4, -4, 8, 8, 8), material="magma"),
            part("plate0", (-3, -6, -3, 6, 2, 6), rot=(0.2, 0.6, 0.1), material="basalt"),
            part("plate1", (-3, 4, -3, 6, 2, 6), rot=(-0.15, 0.2, -0.2), material="basalt"),
        ],
        "render_height_blocks": 0.55,
        "glow": True,
    },
    "tsunami_wall": {
        "tex": (256, 128),
        "parts": [
            # mitad de escala: 60x80x12 => el renderer escala x2 (7.5 x 11 bl)
            part("body", (-30, 8, -6, 60, 80, 12), material="water"),
            part("curl", (-30, 0, -16, 60, 12, 12), rot=(-0.5, 0, 0), material="water"),
            part("foam", (-30, -2, -18, 60, 6, 6), rot=(-0.7, 0, 0), material="foam"),
        ],
        "render_height_blocks": 5.5,
        "glow": False,
    },
    "impactor": {
        "tex": (128, 128),
        "parts": [
            part("core", (-8, -8, -8, 16, 16, 16), material="meteor"),
            part("shell", (-10, -10, -10, 20, 20, 20), material="plasma_shell"),
        ],
        "render_height_blocks": 2.0,
        "glow": True,
    },
}


# ============================ UV packing ============================

def face_layout(u, v, w, h, d):
    """Layout estandar de box UV de Minecraft. Devuelve dict cara->(u,v,w,h)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def pack(spec):
    """Shelf packer simple; asigna texOffs a cada parte. Muta el spec."""
    tex_w, tex_h = spec["tex"]
    cursor_u, cursor_v, shelf_h = 0, 0, 0
    for p in spec["parts"]:
        _, _, _, w, h, d = p["box"]
        w, h, d = int(round(w)), int(round(h)), int(round(d))
        fw, fh = 2 * (w + d), h + d
        if cursor_u + fw > tex_w:
            cursor_u = 0
            cursor_v += shelf_h
            shelf_h = 0
        if cursor_v + fh > tex_h:
            raise ValueError(f"atlas {spec['tex']} demasiado pequeno para {p['name']}")
        p["texoffs"] = (cursor_u, cursor_v)
        p["dims_int"] = (w, h, d)
        cursor_u += fw
        shelf_h = max(shelf_h, fh)


for _name, _spec in SPECS.items():
    pack(_spec)


# ============================ pintores ============================

def stable_rng(*keys):
    """Seed ESTABLE por hash del contenido (jamas contador global)."""
    digest = hashlib.sha256("|".join(str(k) for k in keys).encode()).digest()
    return np.random.default_rng(int.from_bytes(digest[:8], "big"))


def fractal_noise(rng, w, h, octaves=4, persistence=0.55):
    total = np.zeros((h, w))
    amplitude, freq_div = 1.0, 1
    for _ in range(octaves):
        gw, gh = max(2, w // (2 * freq_div)), max(2, h // (2 * freq_div))
        grid = rng.random((gh, gw))
        layer = np.array(Image.fromarray((grid * 255).astype(np.uint8))
                         .resize((w, h), Image.BILINEAR)) / 255.0
        total += layer * amplitude
        amplitude *= persistence
        freq_div *= 2
    return (total - total.min()) / max(1e-6, total.max() - total.min())


MATERIAL_BASE = {
    "rock": (110, 100, 92),
    "meteor": (72, 58, 50),
    "magma": (60, 38, 30),
    "basalt": (48, 46, 50),
    "funnel": (150, 150, 158),
    "water": (30, 90, 150),
    "foam": (225, 238, 245),
    "plasma_shell": (255, 190, 90),
}


def paint_face(img, rng, region, material, ss):
    u, v, w, h = region
    if w <= 0 or h <= 0:
        return
    W, H = w * ss, h * ss
    base = np.array(MATERIAL_BASE[material], dtype=float)
    noise = fractal_noise(rng, W, H, octaves=4)
    px = np.zeros((H, W, 4), dtype=float)

    if material == "water":
        # EXACTAMENTE periodica en Y (eje de scroll): tira del periodo teselada
        period = max(8, H // 4)
        yy = (np.arange(H) % period) / period
        stripe = 0.5 + 0.5 * np.sin(2 * np.pi * yy)
        n_small = fractal_noise(rng, W, period, octaves=3)
        tiled = np.tile(n_small, (H // period + 1, 1))[:H, :]
        tone = 0.55 + 0.45 * (0.6 * tiled + 0.4 * stripe[:, None])
        px[..., 0] = base[0] * tone + 40 * tiled
        px[..., 1] = base[1] * tone + 60 * tiled
        px[..., 2] = base[2] * tone + 80 * tiled
        px[..., 3] = 235
    elif material == "foam":
        px[..., :3] = base * (0.75 + 0.25 * noise[..., None])
        px[..., 3] = 200 + 55 * noise
    elif material == "funnel":
        # estrias diagonales PERIODICAS en X (el giro del embudo las mueve)
        xx = np.arange(W)[None, :] / W
        yy = np.arange(H)[:, None] / H
        swirl = 0.5 + 0.5 * np.sin(2 * np.pi * (xx * 3 + yy * 0.7))
        tone = 0.55 + 0.30 * noise + 0.25 * swirl
        px[..., :3] = base * tone[..., None]
        px[..., 3] = 165 + 60 * noise  # embudo semi-translucido
    elif material == "plasma_shell":
        cx, cy = W / 2, H / 2
        dist = np.sqrt(((np.arange(W) - cx) ** 2)[None, :]
                       + ((np.arange(H)[:, None] - cy) ** 2))
        glow = np.clip(1.2 - dist / max(cx, cy), 0, 1)
        px[..., 0] = 255
        px[..., 1] = 140 + 100 * glow
        px[..., 2] = 40 + 120 * glow * glow
        px[..., 3] = 120 + 135 * glow
    else:
        # roca / meteor / magma / basalt: multi-escala + vetas
        tone = 0.55 + 0.45 * noise
        px[..., :3] = base * tone[..., None]
        cracks = fractal_noise(rng, W, H, octaves=5) > (0.82 if material != "magma" else 0.72)
        if material == "magma":
            px[cracks] = [255, 120, 30, 255]  # vetas incandescentes
        else:
            px[cracks, :3] *= 0.45
        px[..., 3] = 255

    # AO de contacto en los bordes de la cara (oscurece el perimetro)
    edge = np.ones((H, W))
    fall = max(1, min(W, H) // 6)
    for i in range(fall):
        shade = 0.72 + 0.28 * (i / fall)
        edge[i, :] *= shade
        edge[H - 1 - i, :] *= shade
        edge[:, i] *= shade
        edge[:, W - 1 - i] *= shade
    px[..., :3] *= edge[..., None]

    img_region = Image.fromarray(np.clip(px, 0, 255).astype(np.uint8), "RGBA")
    img.paste(img_region, (u * ss, v * ss))


def paint_glow(img, rng, region, material, ss):
    """Textura emisiva _glow: solo vetas/nucleos brillantes, resto transparente."""
    u, v, w, h = region
    if w <= 0 or h <= 0:
        return
    W, H = w * ss, h * ss
    px = np.zeros((H, W, 4), dtype=float)
    if material in ("magma", "meteor"):
        cracks = fractal_noise(rng, W, H, octaves=5) > 0.78
        px[cracks] = [255, 140, 40, 255]
    elif material == "plasma_shell":
        cx, cy = W / 2, H / 2
        dist = np.sqrt(((np.arange(W) - cx) ** 2)[None, :]
                       + ((np.arange(H)[:, None] - cy) ** 2))
        glow = np.clip(1.1 - dist / max(cx, cy), 0, 1)
        px[..., 0] = 255
        px[..., 1] = 170 + 70 * glow
        px[..., 2] = 60 + 100 * glow
        px[..., 3] = 255 * glow
    img_region = Image.fromarray(np.clip(px, 0, 255).astype(np.uint8), "RGBA")
    img.paste(img_region, (u * ss, v * ss))


def paint_entity(name, spec):
    tex_w, tex_h = spec["tex"]
    img = Image.new("RGBA", (tex_w * SS, tex_h * SS), (0, 0, 0, 0))
    glow_img = Image.new("RGBA", (tex_w * SS, tex_h * SS), (0, 0, 0, 0)) if spec["glow"] else None
    for p in spec["parts"]:
        w, h, d = p["dims_int"]
        u, v = p["texoffs"]
        for face, region in face_layout(u, v, w, h, d).items():
            rng = stable_rng(name, p["name"], p["material"], face)
            paint_face(img, rng, region, p["material"], SS)
            if glow_img is not None:
                rng_glow = stable_rng(name, p["name"], p["material"], face, "glow")
                paint_glow(glow_img, rng_glow, region, p["material"], SS)
    img = img.filter(ImageFilter.GaussianBlur(0.4))
    os.makedirs(TEX_OUT, exist_ok=True)
    img.save(os.path.join(TEX_OUT, f"{name}.png"))
    if glow_img is not None:
        glow_img.save(os.path.join(TEX_OUT, f"{name}_glow.png"))


# ============================ emision de Geometry.java ============================

JAVA_HEADER = '''package com.vvrgs.cataclysm.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERADO por tools/specs.py — NO editar a mano.
 * Fuente unica de verdad: SPECS (python) => Geometry.java + atlas pintados.
 */
public final class Geometry {
'''


def emit_layer(name, spec):
    method = {
        "tornado": "tornadoLayer",
        "bolide": "bolideLayer",
        "volcanic_bomb": "bombLayer",
        "tsunami_wall": "tsunamiLayer",
        "impactor": "impactorLayer",
    }[name]
    tex_w, tex_h = spec["tex"]
    lines = [f"    public static LayerDefinition {method}() {{",
             "        MeshDefinition mesh = new MeshDefinition();",
             "        PartDefinition root = mesh.getRoot();"]
    for p in spec["parts"]:
        x, y, z, w, h, d = p["box"]
        u, v = p["texoffs"]
        px, py, pz = p["pivot"]
        rx, ry, rz = p["rot"]
        pose = (f"PartPose.offset({px:.1f}F, {py:.1f}F, {pz:.1f}F)"
                if (rx, ry, rz) == (0, 0, 0) else
                f"PartPose.offsetAndRotation({px:.1f}F, {py:.1f}F, {pz:.1f}F, "
                f"{rx:.3f}F, {ry:.3f}F, {rz:.3f}F)")
        lines.append(
            f"        root.addOrReplaceChild(\"{p['name']}\", CubeListBuilder.create()"
            f".texOffs({u}, {v}).addBox({x:.1f}F, {y:.1f}F, {z:.1f}F, "
            f"{float(w):.1f}F, {float(h):.1f}F, {float(d):.1f}F), {pose});")
    lines.append(f"        return LayerDefinition.create(mesh, {tex_w}, {tex_h});")
    lines.append("    }")
    return "\n".join(lines)


def paint_tsunami_sheet():
    """Textura dedicada de la lamina con scroll: TODO el lienzo es agua
    EXACTAMENTE periodica en Y (el fract() del shader tesela la textura
    completa — cualquier region muestreada debe ser agua sin costura)."""
    w, h = 256 * SS, 128 * SS
    rng = stable_rng("tsunami", "sheet", "water")
    period = h // 4
    n_small = fractal_noise(rng, w, period, octaves=4)
    tiled = np.tile(n_small, (h // period + 1, 1))[:h, :]
    yy = (np.arange(h) % period) / period
    stripe = 0.5 + 0.5 * np.sin(2 * np.pi * yy)
    tone = 0.5 + 0.5 * (0.65 * tiled + 0.35 * stripe[:, None])
    px = np.zeros((h, w, 4), dtype=float)
    px[..., 0] = 25 + 55 * tone
    px[..., 1] = 80 + 90 * tone
    px[..., 2] = 140 + 100 * tone
    px[..., 3] = 230
    # crestas de espuma tambien periodicas
    foam = tiled > 0.78
    px[foam] = [235, 245, 252, 245]
    img = Image.fromarray(np.clip(px, 0, 255).astype(np.uint8), "RGBA")
    img.save(os.path.join(TEX_OUT, "tsunami_sheet.png"))


def main():
    blocks = [JAVA_HEADER]
    for name, spec in SPECS.items():
        blocks.append(emit_layer(name, spec))
        blocks.append("")
        paint_entity(name, spec)
    paint_tsunami_sheet()
    blocks.append("    private Geometry() {\n    }\n}")
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(blocks) + "\n")
    print(f"Geometry.java + {len(SPECS)} texturas generadas")
    for name, spec in SPECS.items():
        print(f"  {name}: tex {spec['tex']}, {len(spec['parts'])} partes")


if __name__ == "__main__":
    main()
