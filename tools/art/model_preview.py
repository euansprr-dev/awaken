"""Renders item models (cuboid elements, as the game bakes them) for look tests without the game.

    python3 tools/art/model_preview.py OUT.png awaken:item/<id> ...     a contact sheet of the listed models

The face corners and UVs follow net.minecraft.client.resources.model.cuboid (FaceBakery's defaults: each face's
texture upright as seen from outside it), element rotations are rotationZYX about their origin like CuboidRotation;
orthographic, depth-tested, back faces culled, cutout alpha, lit by two fixed lights like an entity (light_emission
parts full bright). What it shows is the model's geometry and atlas exactly; the game's lighting only roughly.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
BG = (24, 26, 34, 255)


def _path(ref, kind):
    ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return os.path.join(RES, "assets", ns, kind, p + (".json" if kind == "models" else ".png"))


def load(ref):
    """(elements, {texture key: PIL image}) for a model id, following parents for elements and textures."""
    elements, textures = None, {}
    while ref:
        path = _path(ref, "models")
        if not os.path.exists(path):
            break
        m = json.load(open(path))
        if elements is None and "elements" in m:
            elements = m["elements"]
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v["sprite"] if isinstance(v, dict) else v)
        ref = m.get("parent")
    images = {}
    for k, v in textures.items():
        while isinstance(v, str) and v.startswith("#"):
            v = textures.get(v[1:])
        if isinstance(v, str) and os.path.exists(_path(v, "textures")):
            images[k] = Image.open(_path(v, "textures")).convert("RGBA")
    return elements or [], images


def rotation_zyx(x, y, z):
    x, y, z = (math.radians(v) for v in (x, y, z))
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def element_rotation(el):
    r = el.get("rotation")
    if not r:
        return np.eye(3), np.zeros(3)
    if "axis" in r:
        v = {"x": 0.0, "y": 0.0, "z": 0.0}
        v[r["axis"]] = r["angle"]
        m = rotation_zyx(v["x"], v["y"], v["z"])
    else:
        m = rotation_zyx(r.get("x", 0), r.get("y", 0), r.get("z", 0))
    return m, np.array(r["origin"], dtype=float)


def corners(el, face):
    """The face's corners (top-left, top-right, bottom-right, bottom-left as seen from outside it)."""
    x0, y0, z0 = el["from"]
    x1, y1, z1 = el["to"]
    return {"south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[face]


L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


def render(ref, yaw=0.0, pitch=0.0, roll=0.0, scale=6.0, size=(200, 200), centre=(8.0, 8.0, 8.0)):
    """The model seen from the south (yaw 0: looking at -z, as the inventory does), turned by yaw, then pitched."""
    elements, images = load(ref)
    w, h = size
    colour = np.zeros((h, w, 4), dtype=np.uint8)
    depth = np.full((h, w), -1e9)
    view = rotation_zyx(pitch, 0, 0) @ rotation_zyx(0, yaw, 0) @ rotation_zyx(0, 0, roll)
    c = np.array(centre, dtype=float)
    for el in elements:
        m, origin = element_rotation(el)
        glow = el.get("light_emission", 0)
        for face, f in el.get("faces", {}).items():
            tex = images.get(f["texture"].lstrip("#"))
            if tex is None:
                continue
            pts = np.array([m @ (np.array(p, dtype=float) - origin) + origin for p in corners(el, face)])
            pv = (view @ (pts - c).T).T
            n = np.cross(pv[3] - pv[0], pv[1] - pv[0])  # outward: down the face, then across it
            if n[2] <= 1e-9:
                continue  # facing away
            nw = np.cross(pts[3] - pts[0], pts[1] - pts[0])
            nw = nw / (np.linalg.norm(nw) or 1)
            light = 1.0 if glow else min(1.0, 0.4 + 0.6 * (max(0, nw @ L0) + max(0, nw @ L1)))
            sx = w / 2 + pv[:, 0] * scale
            sy = h / 2 - pv[:, 1] * scale
            _quad(colour, depth, sx, sy, pv[:, 2], tex, f["uv"], light)
    return Image.fromarray(colour, "RGBA")


def _quad(colour, depth, sx, sy, sz, tex, uv, light):
    h, w = depth.shape
    bx0, bx1 = max(0, int(math.floor(sx.min()))), min(w - 1, int(math.ceil(sx.max())))
    by0, by1 = max(0, int(math.floor(sy.min()))), min(h - 1, int(math.ceil(sy.max())))
    if bx1 < bx0 or by1 < by0:
        return
    gx, gy = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
    # the parallelogram p = TL + a (TR - TL) + b (BL - TL)
    ax, ay = sx[1] - sx[0], sy[1] - sy[0]
    bxv, byv = sx[3] - sx[0], sy[3] - sy[0]
    det = ax * byv - ay * bxv
    if abs(det) < 1e-9:
        return
    dx, dy = gx - sx[0], gy - sy[0]
    a = (dx * byv - dy * bxv) / det
    b = (ax * dy - ay * dx) / det
    inside = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
    if not inside.any():
        return
    z = sz[0] + a * (sz[1] - sz[0]) + b * (sz[3] - sz[0])
    tw, th = tex.size
    u0, v0, u1, v1 = uv
    tu = np.clip(((u0 + a * (u1 - u0)) / 16 * tw).astype(int), 0, tw - 1)
    tv = np.clip(((v0 + b * (v1 - v0)) / 16 * th).astype(int), 0, th - 1)
    arr = np.asarray(tex)
    texel = arr[tv, tu]
    region = depth[by0:by1 + 1, bx0:bx1 + 1]
    ok = inside & (texel[..., 3] >= 128) & (z > region)
    region[ok] = z[ok]
    out = colour[by0:by1 + 1, bx0:bx1 + 1]
    lit = texel.astype(float)
    lit[..., :3] *= light
    out[ok] = lit[ok].astype(np.uint8)
    out[ok, 3] = 255


def flat(path, px):
    """A texture drawn at px per texel (an icon as the inventory shows it)."""
    im = Image.open(path).convert("RGBA")
    return im.resize((im.width * px, im.height * px), Image.NEAREST)


def on(img, bg=BG):
    base = Image.new("RGBA", img.size, bg)
    base.alpha_composite(img)
    return base


VIEWS = [("front", 0, 0, 0), ("three-quarter", 35, 20, 0), ("edge", 80, 10, 0), ("from above", 0, 70, 0)]


def sheet(weapons, out):
    """One row per (id, name): the icon at 1x and 6x, then the 3D model from four sides."""
    cell = 220
    rows = []
    for wid, name in weapons:
        tiles = [on(flat(_path("awaken:item/arms/" + wid + "_icon", "textures"), 1)), on(flat(_path("awaken:item/arms/" + wid + "_icon", "textures"), 6))]
        for _, yaw, pitch, roll in VIEWS:
            tiles.append(on(render("awaken:item/" + wid + "_in_hand", yaw, pitch, roll, scale=7.5, size=(cell, cell), centre=(10, 10, 8))))
        rows.append((name, tiles))
    widths = [24, 104] + [cell] * len(VIEWS)
    W = sum(widths) + 8 * (len(widths) + 1)
    H = len(rows) * (cell + 24) + 30
    img = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(img)
    labels = ["1x", "6x"] + [v[0] for v in VIEWS]
    x = 8
    for lab, wd in zip(labels, widths):
        d.text((x, 8), lab, fill=(150, 156, 170, 255))
        x += wd + 8
    y = 26
    for name, tiles in rows:
        x = 8
        for t, wd in zip(tiles, widths):
            img.paste(t, (x, y + (cell - t.height) // 2 if t.height < cell else y))
            x += wd + 8
        d.text((8, y + cell + 4), name, fill=(220, 224, 232, 255))
        y += cell + 24
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    img.save(out)
    return out


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    tiles = [on(render(ref, 35, 20, 0, scale=7.5, size=(220, 220), centre=(10, 10, 8))) for ref in sys.argv[2:]]
    img = Image.new("RGBA", (228 * len(tiles) + 8, 236), BG)
    for i, t in enumerate(tiles):
        img.paste(t, (8 + i * 228, 8))
    img.save(sys.argv[1])
    print(sys.argv[1])
