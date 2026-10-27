"""Forms (SPEC 21.11): the five forms a creature wears in land of rank D to S (rungs 1 to 5), painted for the vanilla bodies.

For each layout (a vanilla texture layout: pig, cow, sheep, chicken, zombie, skeleton, spider, creeper), each rung and each biome
family (0 green, 1 sun, 2 frost) this writes:
  textures/entity/forms/<layout>/o<rung>_<family>.png   the overlay, on the vanilla UV layout (scars, plates, rags, weathering); drawn over the body
  textures/entity/forms/<layout>/k<rung>_<family>.png   the kit's texture: tusks, horns, helms, spines, crowns (own UV, see kit.json)
  textures/entity/forms/<layout>/g<rung>.png            a white mask of what glows (eyes, cracks), tinted in the game by the rank's colour
and assets/awaken/forms/kit.json: the cubes of each rung's kit (part, box, pose, UV corner), read by living.client.FormKit, so the painter
and the model builder cannot disagree. Vanilla textures are never copied: only their layout (cube sizes and UV corners) is known here.
Axes are the game's: x to the creature's left, y DOWN (up is -y), the front is -z; rotation order is Z, Y, X (matrix Rz.Ry.Rx).
Run: python3 tools/art/build_all.py forms     then preview with tools/art/forms_preview.py
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
from pixelart import Canvas, hex_rgba, rng_for  # noqa: E402

# ---------------------------------------------------------------------------------------------------------------------
# Materials, by biome family: 4-step ramps dark to light

def _r(*hexes):
    return [hex_rgba(h) for h in hexes]

FAMILIES = {
    0: dict(bone=_r("#4a3f26", "#85734a", "#c2b283", "#ece0b8"), plate=_r("#23282a", "#434b48", "#67726c", "#94a398"),
            cloth=_r("#241f12", "#3a3220", "#56492c", "#77653b"), dark=_r("#0f0d09", "#1a160f", "#292217", "#3a3022"),
            weather=hex_rgba("#4f7a3a")),
    1: dict(bone=_r("#6a5638", "#a89066", "#dac793", "#f6ebc6"), plate=_r("#33241a", "#62432a", "#94673a", "#c9955a"),
            cloth=_r("#4d3a22", "#6f5632", "#98794a", "#c3a56e"), dark=_r("#1c140d", "#2b2015", "#3e2f1f", "#54412c"),
            weather=hex_rgba("#d8bd7c")),
    2: dict(bone=_r("#6b7e91", "#a9bccd", "#dde9f3", "#ffffff"), plate=_r("#222d39", "#3f5368", "#6989a3", "#a4c2d8"),
            cloth=_r("#27323f", "#3f4f63", "#5f7690", "#88a0b8"), dark=_r("#0d131a", "#16202b", "#223040", "#314357"),
            weather=hex_rgba("#e9f4fb")),
}
SILK = _r("#8d94a0", "#b4bcc8", "#dde2ea", "#ffffff")

# ---------------------------------------------------------------------------------------------------------------------
# The kits: per layout, rung -> cubes, each in the frame of one vanilla part and standing on a surface of it. The placement rules
# (tools/art/forms_geom.py) fail the build when a piece is not attached, sits over a face, hides in the body or reaches too far.
from forms_geom import BODIES, aim, apply, centre_x, cube, curve, rot, spike, validate  # noqa: E402


def sym(fn):
    """Both sides: fn(s, tag) for s = +1 (the creature's left, +x) and -1."""
    out = []
    for s, tag in ((1, "l"), (-1, "r")):
        out += fn(s, tag)
    return out


def ring(n, radius, y, tilt=0.35):
    """n points round a circle (x, y, z, outward lean x, outward lean z)."""
    return [(math.sin(a) * radius, y, math.cos(a) * radius, math.sin(a) * tilt, math.cos(a) * tilt) for a in (k * 2 * math.pi / n for k in range(n))]


def pig(r):
    c = []
    L = [2.2, 2.6, 3.0, 3.4, 3.8][r - 1]
    # tusks come from the cheeks, out and up beside the snout, never across it
    c += sym(lambda s, t: curve("head", f"tusk{t}", (s * 3.5, 2.6, -6.0), [(L, (s * 0.85, -0.3, -0.2)), (L * 0.9, (s * 0.35, -1.0, -0.2))], 1 if r < 4 else 2))
    h = [2, 3, 4, 5, 6][r - 1]
    for i, yy in enumerate(range(-3, 6, 3)):
        c.append(cube("body", f"ridge{i}", (-0.5, yy, 1, 1, 2, h), mat="bone" if r >= 4 else "cloth"))
    if r >= 2:
        for i, (y0, y1) in enumerate(((-6, -1), (1, 5))):
            c += sym(lambda s, t: [cube("body", f"plate{t}{i}", (1.5 if s > 0 else -4.5, y0, 1, 3, y1 - y0, 1), mat="plate")])
    if r >= 3:
        c.append(cube("head", "brow", (-4.2, -4.3, -8.8, 8.4, 1.2, 1.0), mat="plate"))
    if r >= 4:
        for i, x in enumerate((-2.5, 0, 2.5)):
            c += spike("head", f"crown{i}", x, -4, -3.5, 4 if x == 0 else 3, (0, -1, -0.1))
        for i, x in enumerate((-3, -1.5, 0, 1.5, 3)):
            c += spike("head", f"mane{i}", x, -4, -0.8, 3 + (i % 2), (0, -1, 0.3))
    if r >= 5:
        for i, (x, y, z) in enumerate(((-3.5, -4, 6), (3.5, -4, 7), (-3.5, 2, 6.5), (3.5, 2, 7))):
            c.append(cube("body", f"shard{i}", (x - 1, y, z, 2, 1.5, 1), (0, 0, 0, 0.3 * (i % 2 - 0.5), 0, 0.3), "bone", float_=True))
    return c


def cow(r):
    c = []
    L = [2.4, 2.8, 3.2, 3.6, 4.0][r - 1]
    def horn(s, t):
        segs = [(L, (s * 1.0, -0.25, -0.1)), (L, (s * 0.45, -1.0, -0.25))]
        if r >= 3:
            segs.append((L * 0.7, (-s * 0.1, -0.5, -1.0)))
        return curve("head", f"horn{t}", (s * 3.8, -3.4, -4.4), segs, 1 if r < 3 else 2)
    c += sym(horn)
    if r >= 2:
        c.append(cube("body", "hump", (-3.5, -8, 3, 7, 7, 3), mat="plate" if r == 2 else "cloth"))
    if r >= 3:
        for i, yy in enumerate((-7, -4, -1.5)):
            c.append(cube("body", f"ridge{i}", (-0.5, yy, 6, 1, 2, 2 + r), mat="bone"))
        c.append(cube("head", "brow", (-4.2, -4.3, -6.8, 8.4, 1.2, 1.0), mat="plate"))
    if r >= 4:
        for i, x in enumerate((-2, 0, 2)):
            c += spike("head", f"crown{i}", x, -4, -2.5, 3, (0, -1, -0.1))
    if r >= 5:
        for i, (x, y, z) in enumerate(((-4, -6, 10), (4, -3, 11), (0, 1, 10))):
            c.append(cube("body", f"shard{i}", (x - 1, y, z, 2, 1.5, 1), (0, 0, 0, 0.3, 0, 0.3 * (i - 1)), "bone", float_=True))
    return c


def sheep(r):
    c = []
    def horn(s, t):
        segs = [(1.8, (s * 1.0, -0.15, 0.45)), (1.8, (s * 0.35, 0.7, 0.55))]
        if r >= 2:
            segs.append((1.6, (-s * 0.35, 0.65, -0.3)))
        return curve("head", f"horn{t}", (s * 2.9, -2.6, -2.0), segs, 2)
    c += sym(horn)
    if r >= 2:
        for i, yy in enumerate(range(-7, 5, 3)):
            c.append(cube("body", f"ridge{i}", (-0.5, yy, -1.2, 1, 2, [3, 4, 5, 6, 7][r - 1])), )
    if r >= 3:
        c.append(cube("head", "brow", (-3.2, -4.3, -6.8, 6.4, 1.2, 1.0), mat="plate"))
    if r >= 4:
        for i, x in enumerate((-2, 0, 2)):
            c += spike("head", f"crown{i}", x, -4, -2, 3, (0, -1, -0.1))
    if r >= 5:
        for i, (x, y, z) in enumerate(((-4, -5, 4), (4, -1, 4.5), (0, 3, 4))):
            c.append(cube("body", f"shard{i}", (x - 1, y, z, 2, 1.5, 1), (0, 0, 0, 0.3, 0, 0.3 * (i - 1)), "bone", float_=True))
    return c


def chicken(r):
    c = []
    h = [1.5, 2, 2.5, 3, 3.5][r - 1]
    c.append(cube("head", "crest", (-0.5, -6 - h, -1.5, 1, h, 3), mat="plate" if r >= 2 else "bone"))
    for part, tag in (("right_leg", "r"), ("left_leg", "l")):
        c.append(cube(part, f"spur{tag}", (0, 2.2, 0, 1, 2 + (r > 2), 1), mat="bone"))
    if r >= 3:
        for i, x in enumerate((-2, 0, 2)):
            c.append(cube("body", f"plume{i}", (x - 0.5, 2.5, 3, 1, 1.5, 2 + r), mat="cloth"))
    if r >= 4:
        c += sym(lambda s, t: spike("head", f"crown{t}", s * 1.2, -6, -0.5, 2.5, (s * 0.15, -1, -0.1)))
    if r >= 5:
        for i, (x, y, z) in enumerate(((-3, -2, 5), (3, -2, 5), (0, 3, 5.5))):
            c.append(cube("body", f"shard{i}", (x - 0.5, y, z, 1, 1, 1), (0, 0, 0, 0.3, 0, 0.3), "bone", float_=True))
    return c


def zombie(r):
    # rags hang from the waist in strips, front and back; a bone shows on the right forearm
    c = [cube("body", "rag0", (-3.5, 9.5, -3, 2, 4, 1), mat="cloth"), cube("body", "rag1", (-1, 10, -3, 2, 3, 1), mat="cloth"), cube("body", "rag2", (1.5, 9.5, -3, 2, 4, 1), mat="cloth"),
         cube("body", "rag3", (-2.5, 9.5, 2, 2, 4, 1), mat="cloth"), cube("body", "rag4", (0.5, 10, 2, 2, 3, 1), mat="cloth"),
         cube("right_arm", "bone", (-1.5, 5, 2, 1, 3, 1), mat="bone")]
    if r >= 2:
        c.append(cube("head", "helm", (-4.5, -9, -4.5, 9, 3.4, 9), mat="plate"))
        for part, cx in (("right_arm", centre_x("zombie", "right_arm")), ("left_arm", centre_x("zombie", "left_arm"))):
            c += [cube(part, "pauldron", (cx - 2.6, -2.8, -2.6, 5.2, 3, 5.2), mat="plate"), cube(part, "bracer", (cx - 2.4, 5.5, -2.4, 4.8, 3, 4.8), mat="plate")]
    if r >= 3:
        for part, cx, tag in (("right_arm", centre_x("zombie", "right_arm"), "r"), ("left_arm", centre_x("zombie", "left_arm"), "l")):
            for i, dx in enumerate((-1.7, -0.5, 0.7)):
                c.append(cube(part, f"claw{tag}{i}", (cx + dx, 10, -0.5, 1, 2.5, 1), mat="bone"))
        for i in range(4):
            c.append(cube("body", f"spine{i}", (-0.5, 1 + i * 2.6, 2, 1, 1.4, 1.5 + i % 2), mat="bone"))
    if r >= 4:
        c += sym(lambda s, t: curve("head", f"horn{t}", (s * 3.4, -9, -0.5), [(2.4, (s * 0.6, -1, 0)), (2.0, (s * 0.1, -1, -0.1))], 1, "bone"))
        for part, cx, tag in (("right_arm", centre_x("zombie", "right_arm"), "r"), ("left_arm", centre_x("zombie", "left_arm"), "l")):
            c += spike(part, f"spike{tag}", cx, -2.8, 0, 2.2, (0, -1, 0))
    if r >= 5:
        for i, (x, y, z, ox, oz) in enumerate(ring(5, 3.0, -9)):
            c += spike("head", f"thorn{i}", x, y, z, 5 if i == 0 else 4, (ox, -1, oz))
        for i, (x, y, z) in enumerate(((-6, 3, 5), (6, 5, 5), (0, 7, 7))):
            c.append(cube("body", f"shard{i}", (x - 1, y, z, 2, 2, 1), (0, 0, 0, 0.3, 0.3, 0.3), "bone", float_=True))
    return c


def skeleton(r):
    c = [cube("body", "quiver", (1.2, 1, 2, 3, 9, 2), mat="cloth"), cube("body", "fletch", (2, -0.8, 2.4, 1, 2, 1), mat="bone")]
    top = -8.2
    if r == 1:
        c.append(cube("head", "band", (-4.5, -8.2, -4.5, 9, 1.2, 9), mat="cloth"))
    if r >= 2:
        c += [cube("head", "hood0", (-4.5, -9, -0.5, 9, 9, 5), mat="cloth"), cube("head", "hood1", (-4.5, -9.8, -4.5, 9, 1.8, 9), mat="cloth")]
        c += [cube("right_arm", "bracer", (-1.5, 6, -1.5, 3, 3, 3), mat="plate"), cube("left_arm", "bracer", (-1.5, 6, -1.5, 3, 3, 3), mat="plate")]
        top = -9.8
    if r >= 3:
        c += sym(lambda s, t: curve("head", f"horn{t}", (s * 3.5, top, -0.5), [(2.2, (s * 0.7, -1, 0)), (1.8, (s * 0.15, -1, -0.1))], 1, "bone"))
        c += [cube("right_arm", "pad", (-2, -2.8, -1.8, 4, 1.6, 3.6), mat="plate"), cube("left_arm", "pad", (-2, -2.8, -1.8, 4, 1.6, 3.6), mat="plate")]
    if r >= 4:
        for i in range(3):
            c.append(cube("body", f"cloak{i}", (-3 + i * 2.6, 0, 2, 2, 12 + (i % 2) * 2, 1), mat="cloth"))
    if r >= 5:
        for i, (x, y, z, ox, oz) in enumerate(ring(6, 8.5, 3, 0)):
            c.append(cube("body", f"bone{i}", (x - 0.5, y + (i % 3) * 3, z - 0.5, 1, 4, 1), (0, 0, 0, 0, 0, 0.3 * (i % 2 - 0.5)), "bone", float_=True))
        for i, (x, y, z, ox, oz) in enumerate(ring(5, 3.0, top)):
            c += spike("head", f"thorn{i}", x, y, z, 4 + (i == 0), (ox, -1, oz))
    return c


def spider(r):
    c = sym(lambda s, t: [cube("head", f"fang{t}", (s * 1.6 - 0.5, 2.2, -8.6, 1, 2.2 + (r > 2), 1.2), mat="bone")])
    c += [cube("body1", "spinneret", (-1.5, -1.5, 6, 3, 3, 3), mat="silk"), cube("body1", "thread", (-0.5, 0, 9, 1, 1, 3 + r), mat="silk")]
    hh = 2 if r < 3 else 3
    for i in range(4 + r):
        c.append(cube("body1", f"hair{i}", (-3.5 + (i % 3) * 3.5 - 0.5, -4 - hh, -4 + (i // 3) * 4, 1, hh, 1), mat="cloth"))
    top = -4
    if r >= 2:
        c.append(cube("head", "plate", (-4.5, -5, -8.5, 9, 1, 9), mat="plate"))
        for i, zz in enumerate((-4, 0, 4)):
            c.append(cube("body1", f"dorsal{i}", (-0.5, -7, zz, 1, 3, 2), mat="plate"))
        top = -5
    if r >= 3:
        c.append(cube("body1", "sac", (-4, -6.5, -1, 8, 3, 6), mat="dark"))
    if r >= 4:
        for i, (x, zz) in enumerate(((-3, -3), (3, 0), (-3, 4), (3, 6.5))):
            c.append(cube("body1", f"egg{i}", (x - 1.5, 3.6, zz - 1.5, 3, 3, 3), mat="silk"))
    if r >= 5:
        for i, x in enumerate((-3, -1, 1, 3)):
            c += spike("head", f"thorn{i}", x, top, -3, 3, (x * 0.05, -1, -0.1))
        for i, (x, y, z) in enumerate(((-6, -8, 0), (6, -9, 3), (0, -11, 6))):
            c.append(cube("body1", f"shard{i}", (x - 1, y, z, 2, 1, 2), (0, 0, 0, 0.3, 0.3, 0), "bone", float_=True))
    return c


def creeper(r):
    c = []
    for i, x in enumerate((-3.5, -0.5, 2.5)):
        c.append(cube("body", f"frill{i}", (x, 1.5 + i * 0.5, 2, 1, 4, 1 + r * 0.6), mat="bone"))
    c.append(cube("head", "frillh", (-3.5, -7, 4, 7, 2, 1), mat="plate"))
    if r >= 2:
        c += sym(lambda s, t: spike("head", f"rod{t}", s * 2.5, -8, 0, 3 + r, (s * 0.12, -1, 0), 1, "plate"))
    if r >= 3:
        c += sym(lambda s, t: [cube("body", f"vent{t}", (s * 2.5 - 1, 0.5, 2, 2, 4, 2), mat="dark")])
        c.append(cube("head", "frillh2", (-3.5, -4.5, 4, 7, 2, 1), mat="plate"))
    if r >= 4:
        c.append(cube("body", "belt", (-4.5, 6, -2.5, 9, 2, 5), mat="plate"))
        c += [cube("body", "packr", (-7, 1, -1.5, 3, 5, 3), mat="plate"), cube("body", "packl", (4, 1, -1.5, 3, 5, 3), mat="plate")]
    if r >= 5:
        for i, x in enumerate((-3, -1.5, 0, 1.5, 3)):
            c += spike("head", f"bolt{i}", x, -8, 0, 5 + (i == 2) * 3, (x * 0.07, -1, 0), 1, "plate")
    return c


KITS = {"pig": pig, "cow": cow, "sheep": sheep, "chicken": chicken, "zombie": zombie, "skeleton": skeleton, "spider": spider, "creeper": creeper}

# ---------------------------------------------------------------------------------------------------------------------
# Vanilla layouts: texture size, the cubes of the vanilla body (u, v, w, h, d, role) and the eyes (u, v, w, h)

LAYOUT = {
    "pig": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 4, 3, 1, "head"), (28, 8, 10, 16, 8, "body"), (0, 16, 4, 6, 4, "leg")], eyes=[(8, 11, 2, 1), (14, 11, 2, 1)]),
    "cow": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 6, "head"), (18, 4, 12, 18, 10, "body"), (0, 16, 4, 12, 4, "leg")], eyes=[(6, 9, 2, 1), (12, 9, 2, 1)]),
    "sheep": dict(size=(64, 32), cubes=[(0, 0, 6, 6, 8, "head"), (28, 8, 8, 16, 6, "body"), (0, 16, 4, 12, 4, "leg")], eyes=[(8, 10, 2, 1), (12, 10, 2, 1)]),
    "chicken": dict(size=(64, 32), cubes=[(0, 0, 4, 6, 3, "head"), (0, 9, 6, 8, 6, "body"), (26, 0, 3, 5, 3, "leg"), (24, 13, 1, 4, 6, "limb")], eyes=[(3, 4, 1, 1), (6, 4, 1, 1)]),
    "zombie": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (40, 16, 4, 12, 4, "limb"), (0, 16, 4, 12, 4, "leg"), (32, 48, 4, 12, 4, "limb"), (16, 48, 4, 12, 4, "leg")], eyes=[(9, 12, 2, 1), (13, 12, 2, 1)]),
    "skeleton": dict(size=(64, 32), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (40, 16, 2, 12, 2, "limb"), (0, 16, 2, 12, 2, "leg")], eyes=[(9, 11, 2, 2), (13, 11, 2, 2)]),
    "spider": dict(size=(64, 32), cubes=[(32, 4, 8, 8, 8, "head"), (0, 0, 6, 6, 6, "body"), (0, 12, 10, 8, 12, "body"), (18, 0, 16, 2, 2, "leg")], eyes=[(41, 14, 2, 1), (45, 14, 2, 1), (42, 16, 1, 1), (45, 16, 1, 1), (40, 13, 1, 1), (47, 13, 1, 1)]),
    "creeper": dict(size=(64, 32), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (0, 16, 4, 6, 4, "leg")], eyes=[(9, 10, 2, 2), (13, 10, 2, 2)]),
}

FACE_SHADE = {"top": 3, "front": 3, "right": 2, "left": 2, "back": 2, "bottom": 1}


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def footprint(w, h, d):
    return int(2 * d + 2 * w), int(d + h)

# ---------------------------------------------------------------------------------------------------------------------
# Painting


def paint_cube(canvas, corner, dims, ramp, rng):
    """Fills a cube's six faces from a 4-step ramp: top lightest, sides mid, bottom darkest, noise and a darker edge."""
    u, v = corner
    w, h, d = (max(1, int(round(x))) for x in dims)
    for name, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        base = FACE_SHADE[name]
        for yy in range(y, y + fh):
            for xx in range(x, x + fw):
                k = base + (1 if rng.random() < 0.18 else 0) - (1 if rng.random() < 0.18 else 0)
                edge = xx in (x, x + fw - 1) or yy in (y, y + fh - 1)
                if edge and fw > 2 and fh > 2:
                    k -= 1
                canvas.set(xx, yy, ramp[max(0, min(3, k))])


def pack(items, size):
    """Shelf-packs (w, h) rects into a canvas; returns corners, or raises if they do not fit."""
    W, H = size
    order = sorted(range(len(items)), key=lambda i: -items[i][1])
    corners = [None] * len(items)
    x = y = shelf = 0
    for i in order:
        w, h = items[i]
        if x + w > W:
            x, y, shelf = 0, y + shelf, 0
        if y + h > H:
            raise ValueError(f"kit does not fit a {W}x{H} texture")
        corners[i] = (x, y)
        x += w
        shelf = max(shelf, h)
    return corners


def kit_texture(layout, r, fam):
    """The kit's texture and its cubes. Cubes of one size and material share a face set (a row of spines is one painting)."""
    size = LAYOUT[layout]["size"]
    cubes = KITS[layout](r)
    keys = [(tuple(max(1, round(v)) for v in cb["box"][3:]), cb["mat"]) for cb in cubes]
    unique = sorted(set(keys))
    corners = dict(zip(unique, pack([footprint(*k[0]) for k in unique], size)))
    canvas = Canvas(*size)
    rng = rng_for("forms-k", layout, r, fam)
    pal = FAMILIES[fam]
    for key in unique:
        dims, mat = key
        paint_cube(canvas, corners[key], dims, SILK if mat == "silk" else pal[mat], rng)
    placed = [dict(part=cb["part"], name=cb["name"], uv=list(corners[k]), box=[round(v, 3) for v in cb["box"]],
                   pose=[round(v, 4) for v in cb["pose"]]) for cb, k in zip(cubes, keys)]
    return canvas, placed


def scatter(canvas, rect, colour, rng, density):
    x, y, w, h = rect
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if rng.random() < density:
                canvas.set(xx, yy, colour)


def slash(canvas, rect, rng, colours):
    """A scar: a short diagonal line inside a face, dark with a pale lip."""
    x, y, w, h = rect
    if w < 2 or h < 2:
        return
    sx, sy = x + rng.randrange(w), y + rng.randrange(max(1, h // 2))
    dx = rng.choice((-1, 0, 1))
    for i in range(rng.randrange(3, min(7, h + 2))):
        px, py = sx + dx * i // 2, sy + i
        if x <= px < x + w and y <= py < y + h:
            canvas.set(px, py, colours[0])
            if x <= px + 1 < x + w and rng.random() < 0.6:
                canvas.set(px + 1, py, colours[1])


def crack(canvas, rect, rng, colour, glow=None):
    """A crack: a jagged line down a face, in `colour`, and the same pixels in `glow` (the glow mask) when given."""
    x, y, w, h = rect
    if w < 3 or h < 4:
        return
    px, py = x + rng.randrange(1, w - 1), y + rng.randrange(0, max(1, h // 3))
    for _ in range(rng.randrange(h // 2, h)):
        if not (x <= px < x + w and y <= py < y + h):
            break
        canvas.set(px, py, colour)
        if glow is not None:
            glow.set(px, py, (255, 255, 255, 255))
            if px + 1 < x + w:
                glow.set(px + 1, py, (255, 255, 255, 255))
        px += rng.choice((-1, 0, 0, 1))
        py += 1


def overlay_and_glow(layout, r, fam):
    size = LAYOUT[layout]["size"]
    ov, gl = Canvas(*size), Canvas(*size)
    rng = rng_for("forms-o", layout, r, fam)
    pal = FAMILIES[fam]
    for (u, v, w, h, d, role) in LAYOUT[layout]["cubes"]:
        for name, rect in faces(u, v, w, h, d).items():
            # weathering of the family on what faces up and back (moss, sand dust, rime)
            if name in ("top", "back"):
                scatter(ov, rect, pal["weather"], rng, 0.05 + 0.015 * r)
            # marks never go on a face: a head carries them on its top, sides and back only
            if role in ("body", "limb", "head") and name in ("front", "left", "right", "back") and not (role == "head" and name == "front"):
                for _ in range(1 + r // 2):
                    slash(ov, rect, rng, (pal["dark"][1], pal["bone"][2]))
            if r >= 2 and role in ("body", "limb") and name in ("front", "back", "left", "right") and rect[2] >= 4 and rect[3] >= 4:
                # plate patches in the family's metal, with a light top edge
                px, py = rect[0] + rng.randrange(0, max(1, rect[2] - 3)), rect[1] + rng.randrange(0, max(1, rect[3] - 3))
                for yy in range(py, min(py + 3, rect[1] + rect[3])):
                    for xx in range(px, min(px + 3, rect[0] + rect[2])):
                        ov.set(xx, yy, pal["plate"][3 if yy == py else 1 + (xx + yy) % 2])
            if r >= 3 and name == "front" and role in ("body", "limb"):
                # a few thin veins: the body's front most, a limb's one from the fourth rung (never the face)
                n = {"body": r - 2, "limb": 1 if r >= 4 else 0}[role]
                for _ in range(n):
                    crack(ov, rect, rng, pal["dark"][0], gl)
    for (u, v, w, h) in LAYOUT[layout]["eyes"]:
        for yy in range(v, v + h):
            for xx in range(u, u + w):
                gl.set(xx, yy, (255, 255, 255, 255))
    return ov, gl

# ---------------------------------------------------------------------------------------------------------------------


def build(out):
    problems = []
    for layout in LAYOUT:
        for r in range(1, 6):
            for line in validate(layout, r, KITS[layout](r)):
                problems.append(f"{layout} rung {r}: {line}")
    if problems:
        raise SystemExit("the forms' kits are not where they should be:\n  " + "\n  ".join(problems))
    kit = {"schema": 1, "layouts": {}}
    for layout in LAYOUT:
        ranks = {}
        for r in range(1, 6):
            for fam in range(3):
                kc, placed = kit_texture(layout, r, fam)
                out.entity(f"forms/{layout}/k{r}_{fam}", kc)
                ov, gl = overlay_and_glow(layout, r, fam)
                out.entity(f"forms/{layout}/o{r}_{fam}", ov)
                if fam == 0:
                    out.entity(f"forms/{layout}/g{r}", gl)
                    ranks[str(r)] = placed
        kit["layouts"][layout] = {"size": list(LAYOUT[layout]["size"]), "ranks": ranks}
    path = os.path.join(out.root, "assets/awaken/forms/kit.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(kit, f, indent=1)
