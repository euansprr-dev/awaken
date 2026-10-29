"""Renders a block model JSON (elements, UVs, y rotations, animated textures' first frame) from several sides, for checking
a shaped block's look before the game: python3 tools/art/block_preview.py OUT.png awaken:block/hearth_anchor_t1 [...]

Each model gets a row: four corners at 30 degrees, straight on, and from above; then the same at night (unlit faces
dimmed, full-bright faces kept), which is how a light source should read. Orthographic, nearest-neighbour, faces shaded
the way the game shades them (up 1.0, north/south 0.8, east/west 0.6, down 0.5). Not the game: no AO, no world light.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
REF = "/Volumes/MEDIA/Awaken/.ref/src"
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
BG = (24, 26, 34)


def load_json(ident, kind):
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    for root in (RES, REF):
        p = os.path.join(root, "assets", ns, kind, path + ".json")
        if os.path.exists(p):
            return json.load(open(p))
    raise FileNotFoundError(ident)


def model(ident):
    m = load_json(ident, "models")
    tex, els = {}, None
    if "parent" in m and not m["parent"].endswith("block/block"):
        ptex, pels = model(m["parent"])
        tex.update(ptex)
        els = pels
    tex.update(m.get("textures", {}))
    return tex, m.get("elements", els) or []


def texture(ident, cache={}):
    if ident in cache:
        return cache[ident]
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    for root in (RES, REF):
        p = os.path.join(root, "assets", ns, "textures", path + ".png")
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            fw = im.width
            fh = fw
            meta = p + ".mcmeta"
            if os.path.exists(meta):
                a = json.load(open(meta)).get("animation", {})
                fw = a.get("width", im.width)
                fh = a.get("height", fw)
            arr = np.asarray(im.crop((0, 0, fw, min(fh, im.height))), dtype=float)
            cache[ident] = arr
            return arr
    raise FileNotFoundError(ident)


def resolve(tex, ref):
    seen = 0
    while ref.startswith("#") and seen < 8:
        ref = tex.get(ref[1:], "minecraft:block/stone")
        seen += 1
    return ref


def face_quad(frm, to, d):
    """The face's corners (top-left, top-right, bottom-right, bottom-left as seen from outside) and their (u, v) roles."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[d]


def rot_y(p, origin, angle):
    a = math.radians(angle)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    c, s = math.cos(a), math.sin(a)
    return (x * c + z * s + origin[0], y + origin[1], -x * s + z * c + origin[2])


def quads(ident):
    tex, els = model(ident)
    out = []
    for e in els:
        r = e.get("rotation")
        lit = e.get("light_emission", 0) > 0
        for d, f in e["faces"].items():
            pts = face_quad(e["from"], e["to"], d)
            n = NORMAL[d]
            if r and r.get("axis") == "y":
                pts = [rot_y(p, r["origin"], r["angle"]) for p in pts]
                n = rot_y(n, (0, 0, 0), r["angle"])
            uv = f.get("uv", [0, 0, 16, 16])
            out.append((pts, uv, texture(resolve(tex, f["texture"])), 1.0 if lit else SHADE[d], lit, n))
    return out


def render(qs, yaw, pitch, night, S=10, W=300, H=420):
    img = np.zeros((H, W, 3)) + BG
    depth = np.full((H, W), -1e9)
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return np.array([W / 2 + x * S, H * 0.62 - y * S, z])  # larger z: nearer the camera

    view = np.array([0.0, 0.0, 1.0])
    jobs = []
    for pts, uv, tx, shade, lit, n in qs:
        P = [proj(p) for p in pts]
        jobs.append((P, uv, tx, shade, lit, sum(p[2] for p in P) / 4))
    # opaque first, then the rest far to near
    for pass_ in (0, 1):
        order = sorted(jobs, key=lambda j: j[5]) if pass_ else jobs
        for P, uv, tx, shade, lit, _ in order:
            u0, v0, u1, v1 = uv
            corners_uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (P[i] for i in tri)
                ta, tb, tc = (corners_uv[i] for i in tri)
                xs, ys = [a[0], b[0], c[0]], [a[1], b[1], c[1]]
                x0, x1 = max(0, int(min(xs))), min(W - 1, int(max(xs)) + 1)
                y0, y1 = max(0, int(min(ys))), min(H - 1, int(max(ys)) + 1)
                den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(den) < 1e-9 or x0 > x1 or y0 > y1:
                    continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                w0 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
                w1 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                if not inside.any():
                    continue
                z = w0 * a[2] + w1 * b[2] + w2 * c[2]
                u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0]
                v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1]
                th, tw = tx.shape[0], tx.shape[1]
                ti = np.clip((v / 16 * th).astype(int), 0, th - 1)
                tj = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
                col = tx[ti, tj]
                alpha = col[..., 3] / 255
                region = depth[y0:y1 + 1, x0:x1 + 1]
                ok = inside & (alpha > 0.03) & (z > region)
                if pass_ == 0:
                    ok &= alpha > 0.99
                else:
                    ok &= alpha <= 0.99
                if not ok.any():
                    continue
                k = shade * (1.0 if lit or not night else 0.32)
                rgb = col[..., :3] * k
                dst = img[y0:y1 + 1, x0:x1 + 1]
                al = alpha[..., None]
                dst[ok] = (rgb * al + dst * (1 - al))[ok]
                if pass_ == 0:
                    region[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


VIEWS = [(45, 30, "front-left"), (135, 30, "front-right"), (225, 30, "back-right"), (315, 30, "back-left"),
         (0, 0, "straight on"), (30, 75, "from above")]


def main():
    out, idents = sys.argv[1], sys.argv[2:]
    tiles = []
    for ident in idents:
        qs = quads(ident)
        row = [(render(qs, y, p, False), f"{ident.split('/')[-1]} {name}") for y, p, name in VIEWS]
        row += [(render(qs, y, p, True), "night " + name) for y, p, name in VIEWS[:2]]
        tiles.append(row)
    tw, th = 300, 420
    cols = max(len(r) for r in tiles)
    sheet = Image.new("RGB", (cols * tw, len(tiles) * (th + 18)), BG)
    dr = ImageDraw.Draw(sheet)
    for j, row in enumerate(tiles):
        for i, (im, label) in enumerate(row):
            sheet.paste(im, (i * tw, j * (th + 18)))
            dr.text((i * tw + 6, j * (th + 18) + th + 2), label, fill=(200, 205, 215))
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
