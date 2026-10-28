"""The binoculars (SPEC §39.4): the pair the hands raise and other players hold at their eyes, and the lens edge you see
through them.

    python3 tools/art/specs/binoculars.py            write them into src/main/resources/assets/awaken/
    python3 tools/art/specs/binoculars.py preview D  the same, plus a contact sheet in D

    item/binoculars      32x32: dark leather grain, brass, coated glass with a blue-violet sheen, black rubber eyecups,
                         a ribbed focus wheel and the graphite bridge
    models/item/...      two octagonal barrels (a box and a box turned 45 degrees), brass rings at the objectives, the
                         eyepieces with their cups, the bridge and the focus wheel; eyepieces toward +z (south)
    gui/binoculars/      the lens edge, our own: a soft dark outside, a slight darkening near the rim, a thin brass ring,
      edge_twin.png      a faint colour fringe and a hint of glass at the top left. Twin: two fields side by side, each
      edge_single.png    rim clipped off the other's field so no ring crosses the middle. Single: one round field.

Nothing is random: the same pixels every time. No copied assets.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "../../../src/main/resources/assets/awaken"))


def rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


LEATHER = ["#141010", "#1d1714", "#272019", "#33291f", "#403327"]
BRASS = ["#4a3418", "#76552a", "#a07a40", "#c9a15c", "#ecd08e"]
GLASS = ["#07081a", "#11143a", "#232a6a", "#3d3f9a", "#6c5cc4", "#b9b0ff"]
RUBBER = ["#0b0b0c", "#141416", "#1d1d20", "#2a2a2e"]
GRAPHITE = ["#16191f", "#20242c", "#2c313b", "#3c4350"]


# ---- the texture: 32x32, one region per material -------------------------------------------------------------------
#   leather  (0..16, 0..16)   brass (16..32, 0..4)    glass (16..24, 4..12)   rubber (24..32, 4..12)
#   wheel    (16..32, 12..16) graphite (0..16, 16..24) eyepiece glass (16..24, 16..24)

def hash01(x, y, s=0):
    """A fixed pseudo-random value in 0..1 for a pixel (the same every run)."""
    n = (x * 374761393 + y * 668265263 + s * 2246822519) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def texture():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    # leather: a pebbled grain, darker pits, a few lighter tops
    for x in range(16):
        for y in range(16):
            h = hash01(x, y, 1)
            c = LEATHER[2]
            if h < 0.18:
                c = LEATHER[1]
            elif h > 0.86:
                c = LEATHER[3]
            if (x + 2 * y) % 7 == 0 and h > 0.5:
                c = LEATHER[4] if h > 0.93 else LEATHER[3]
            px[x, y] = rgba(c)
    # brass: a lit top line, the body, a dark bottom (a band seen edge on)
    for x in range(16, 32):
        for y in range(4):
            c = [BRASS[4], BRASS[3], BRASS[2], BRASS[1]][y]
            if y == 1 and (x % 5 == 0):
                c = BRASS[4]
            px[x, y] = rgba(c)
    # objective glass: deep blue at the edge, violet sheen across the middle, one highlight
    for x in range(16, 24):
        for y in range(4, 12):
            u, v = (x - 19.5) / 4.0, (y - 7.5) / 4.0
            r = math.hypot(u, v)
            c = GLASS[1] if r > 0.9 else GLASS[2]
            if abs(u + v) < 0.35 and r < 0.85:
                c = GLASS[4]
            elif abs(u + v) < 0.7 and r < 0.85:
                c = GLASS[3]
            if (x, y) in ((18, 6), (19, 6), (18, 7)):
                c = GLASS[5]
            px[x, y] = rgba(c)
    # rubber: near black, a faint ring of wear
    for x in range(24, 32):
        for y in range(4, 12):
            c = RUBBER[1]
            if y in (5, 10):
                c = RUBBER[2]
            if hash01(x, y, 2) > 0.9:
                c = RUBBER[3]
            px[x, y] = rgba(c)
    # focus wheel: ribs across
    for x in range(16, 32):
        for y in range(12, 16):
            px[x, y] = rgba(GRAPHITE[3] if x % 2 == 0 else GRAPHITE[1])
    # graphite bridge: brushed
    for x in range(16):
        for y in range(16, 24):
            c = GRAPHITE[2] if (y + (x // 4)) % 3 else GRAPHITE[1]
            if y == 16:
                c = GRAPHITE[3]
            px[x, y] = rgba(c)
    # eyepiece glass: small, darker, a green-violet coat
    for x in range(16, 24):
        for y in range(16, 24):
            u, v = (x - 19.5) / 4.0, (y - 19.5) / 4.0
            r = math.hypot(u, v)
            c = GLASS[0] if r > 0.8 else GLASS[1]
            if abs(u - v) < 0.4 and r < 0.7:
                c = GLASS[3]
            px[x, y] = rgba(c)
    return im


# ---- the model --------------------------------------------------------------------------------------------------------

T = "#t"


def faces(uv_side, uv_end, ends=("north", "south"), end_uvs=None):
    f = {}
    for side in ("up", "down", "east", "west"):
        f[side] = {"uv": uv_side, "texture": T}
    for i, e in enumerate(ends):
        f[e] = {"uv": (end_uvs[i] if end_uvs else uv_end), "texture": T}
    return f


def box(frm, to, fc, rot=None):
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": fc}
    if rot:
        e["rotation"] = rot
    return e


def model():
    els = []
    LEATHER_UV, BRASS_UV, GLASS_UV, RUBBER_UV = [0, 0, 8, 8], [8, 0, 16, 2], [8, 2, 12, 6], [12, 2, 16, 6]
    WHEEL_UV, GRAPH_UV, EYE_UV = [8, 6, 16, 8], [0, 8, 8, 12], [8, 8, 12, 12]
    for side in (-1, 1):
        cx, cy = 8 + side * 3.3, 8.0
        # the barrel: a 4.4 box and the same turned 45 degrees about z (an octagon seen end on)
        h = 2.2
        els.append(box([cx - h, cy - h, 3.6], [cx + h, cy + h, 12.2], faces(LEATHER_UV, GRAPH_UV)))
        hb = 1.85
        els.append(box([cx - hb, cy - hb, 3.62], [cx + hb, cy + hb, 12.18], faces(LEATHER_UV, GRAPH_UV),
                       {"origin": [cx, cy, 8], "axis": "z", "angle": 45}))
        # the objective's brass ring and its glass (toward -z, north)
        r = 2.45
        els.append(box([cx - r, cy - r, 2.9], [cx + r, cy + r, 3.7], faces(BRASS_UV, GLASS_UV, end_uvs=[GLASS_UV, GLASS_UV])))
        # the eyepiece and its rubber cup (toward +z, south: the eye)
        e = 1.55
        els.append(box([cx - e, cy - e, 12.1], [cx + e, cy + e, 13.6], faces(GRAPH_UV, EYE_UV)))
        c = 1.8
        els.append(box([cx - c, cy - c, 13.5], [cx + c, cy + c, 14.6], faces(RUBBER_UV, EYE_UV, end_uvs=[RUBBER_UV, EYE_UV])))
        # a brass band where the eyepiece meets the barrel
        b = 2.28
        els.append(box([cx - b, cy - b, 11.6], [cx + b, cy + b, 12.1], faces(BRASS_UV, GRAPH_UV)))
        # the strap lug on the outside
        lx = cx + side * 2.2
        els.append(box([min(lx, lx + side * 0.6), cy + 0.6, 10.0], [max(lx, lx + side * 0.6), cy + 1.4, 11.0], faces(GRAPH_UV, GRAPH_UV)))
    # the bridge between the barrels and the focus wheel on top of it
    els.append(box([6.5, 8.6, 5.5], [9.5, 10.0, 11.4], faces(GRAPH_UV, GRAPH_UV)))
    els.append(box([7.0, 7.0, 6.5], [9.0, 8.6, 10.5], faces(GRAPH_UV, GRAPH_UV)))
    els.append(box([7.1, 10.0, 8.6], [8.9, 11.2, 10.8], faces(WHEEL_UV, WHEEL_UV, ends=("north", "south", "east", "west"))))
    for el in els:
        for f in el["faces"].values():
            f["uv"] = [v for v in f["uv"]]
    return {
        "textures": {"t": "awaken:item/binoculars", "particle": "awaken:item/binoculars"},
        "elements": els,
        "display": {
            "gui": {"rotation": [30, 135, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [80, 0, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_lefthand": {"rotation": [80, 0, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        },
    }


# ---- the lens edge ----------------------------------------------------------------------------------------------------

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def field(xx, yy, cx, cy, R):
    """One eyepiece's field: (opacity of the dark outside 0..1, ring rgb add, ring alpha) per pixel."""
    r = np.hypot(xx - cx, yy - cy) / R
    ang = np.arctan2(yy - cy, xx - cx)
    # a slight darkening toward the rim, then the field stop: dark past it
    dark = 0.30 * smooth(0.78, 0.965, r) + 0.70 * smooth(0.955, 1.0, r)
    dark = np.maximum(dark, smooth(0.965, 1.005, r))
    col = np.zeros(xx.shape + (3,))
    a = np.zeros(xx.shape)
    # the thin brass ring just inside the stop
    ring = np.exp(-((r - 0.982) / 0.0065) ** 2)
    brass = np.array([0.66, 0.50, 0.26])
    col += ring[..., None] * brass
    a += ring * 0.42
    # the colour fringe: blue inside the ring, warm outside it
    blue = np.exp(-((r - 0.955) / 0.010) ** 2) * 0.10
    warm = np.exp(-((r - 0.996) / 0.006) ** 2) * 0.12
    col += blue[..., None] * np.array([0.35, 0.45, 1.0]) + warm[..., None] * np.array([1.0, 0.55, 0.25])
    a += blue + warm
    # a hint of glass: a faint arc at the top left
    glass = np.exp(-((r - 0.90) / 0.02) ** 2) * smooth(0.0, 1.0, np.cos(ang + 2.25)) * 0.06
    col += glass[..., None] * np.array([1.0, 1.0, 1.0])
    a += glass
    return dark, col, a


def edge(twin):
    H = 640
    W = 1024 if twin else 640
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64) + 0.5
    R = H * 0.5 * 0.985
    if twin:
        off = 0.30 * 2 * R * 0.97
        centres = [(W / 2 - off, H / 2), (W / 2 + off, H / 2)]
    else:
        centres = [(W / 2, H / 2)]
    fields = [field(xx, yy, cx, cy, R) for cx, cy in centres]
    dark = np.min([f[0] for f in fields], axis=0)
    rgb = np.zeros((H, W, 3))
    add_a = np.zeros((H, W))
    for i, (d, col, a) in enumerate(fields):
        # each rim only where the other glass doesn't see: no ring crosses the middle
        keep = np.ones((H, W))
        for j, f in enumerate(fields):
            if j != i:
                keep *= smooth(0.55, 0.97, f[0])
        rgb += col * keep[..., None]
        add_a += a * keep
    # composite: black at 'dark', plus the ring's light over it
    alpha = np.clip(dark + add_a * (1 - dark), 0, 1)
    light = np.clip(rgb, 0, 1)
    out_rgb = np.where(alpha[..., None] > 1e-4, light * (add_a[..., None] / np.maximum(alpha[..., None], 1e-4)), 0)
    out = np.dstack([np.clip(out_rgb, 0, 1) * 255, alpha * 255]).round().astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def build():
    out = {}

    def save(rel, im):
        p = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p, optimize=True)
        out[rel] = im

    save("textures/item/binoculars.png", texture())
    save("textures/gui/binoculars/edge_twin.png", edge(True))
    save("textures/gui/binoculars/edge_single.png", edge(False))
    for name in ("edge_twin", "edge_single"):
        write_json(os.path.join(ROOT, "textures/gui/binoculars/%s.png.mcmeta" % name), {"texture": {"blur": True}})
    write_json(os.path.join(ROOT, "models/item/binoculars.json"), model())
    write_json(os.path.join(ROOT, "items/binoculars.json"), {"model": {"type": "minecraft:model", "model": "awaken:item/binoculars"}})
    return out


if __name__ == "__main__":
    ims = build()
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        d = sys.argv[2]
        os.makedirs(d, exist_ok=True)
        tex = ims["textures/item/binoculars.png"].resize((384, 384), Image.NEAREST)
        sky = Image.new("RGBA", (1024, 640), (120, 160, 200, 255))
        for y in range(640):
            for x in range(0, 1024, 64):
                if (x // 64 + y // 64) % 2:
                    sky.paste((96, 140, 90, 255), (x, y, x + 64, y + 1))
        twin = Image.alpha_composite(sky, ims["textures/gui/binoculars/edge_twin.png"])
        single = Image.alpha_composite(sky.crop((0, 0, 640, 640)), ims["textures/gui/binoculars/edge_single.png"])
        sheet = Image.new("RGBA", (384 + 1024 + 640 + 64, 660), (60, 62, 68, 255))
        sheet.paste(tex, (16, 16))
        sheet.paste(twin, (416, 10))
        sheet.paste(single, (1456, 10))
        sheet.save(os.path.join(d, "binoculars_art.png"))
        print(os.path.join(d, "binoculars_art.png"))
    print("wrote", ", ".join(ims))
