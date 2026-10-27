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
# Geometry helpers


def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(Rz, mul(Ry, Rx))


def apply(R, v):
    return [sum(R[i][k] * v[k] for k in range(3)) for i in range(3)]


def cube(part, name, box, pose=(0, 0, 0, 0, 0, 0), mat="bone"):
    return dict(part=part, name=name, box=tuple(box), pose=tuple(pose), mat=mat)


def curve(part, name, base, segs, thick, mat="bone"):
    """A bent horn or tusk: segments (length, rx, ry, rz) starting at base, each pointing along its own up (-y), each starting where the last ended."""
    out, pos = [], list(base)
    for i, (length, rx, ry, rz) in enumerate(segs):
        t = max(1, thick - (1 if i >= 2 else 0))
        out.append(cube(part, f"{name}{i}", (-t / 2, -length, -t / 2, t, length, t), (*pos, rx, ry, rz), mat))
        d = apply(rot(rx, ry, rz), (0, -length, 0))
        pos = [pos[k] + d[k] for k in range(3)]
    return out


def sym(fn):
    """Both sides: fn(s, tag) for s = +1 (the creature's left, +x) and -1."""
    out = []
    for s, tag in ((1, "l"), (-1, "r")):
        out += fn(s, tag)
    return out

# ---------------------------------------------------------------------------------------------------------------------
# The kits: per layout, rung -> cubes. Sizes grow with the rung; every rung is its own full list.


def pig(r):
    c = []
    L = [3, 4, 5, 6, 7][r - 1]
    c += sym(lambda s, t: curve("head", f"tusk{t}", (s * 2.6, 3.0, -8.6), [(L * 0.55, 0.3, 0, -s * 0.15), (L * 0.5, 1.0, 0, -s * 0.4)] if r >= 2 else [(L, 0.5, 0, -s * 0.2)], 1 if r < 4 else 2))
    h = [2, 3, 4, 5, 6][r - 1]
    for i, yy in enumerate(range(-9, 5, 3)):
        c.append(cube("body", f"ridge{i}", (-0.5, yy, 1, 1, 2, h), mat="bone" if r >= 4 else "cloth"))
    if r >= 2:
        for i, (y0, y1) in enumerate(((-8, -2), (0, 5))):
            c += sym(lambda s, t: [cube("body", f"plate{t}{i}", (1.5 if s > 0 else -4.5, y0, 1, 3, y1 - y0, 1), mat="plate")])
    if r >= 3:
        c.append(cube("head", "brow", (-4.3, -2.6, -8.5, 8.6, 1, 1), mat="plate"))
    if r >= 4:
        for i, x in enumerate((-2.5, 0, 2.5)):
            c += curve("head", f"crown{i}", (x, -4, -3.5), [(5 if x == 0 else 4, -0.15, 0, 0)], 1, "bone")
        for i, x in enumerate((-3, -1.5, 0, 1.5, 3)):
            c.append(cube("head", f"mane{i}", (x - 0.5, -5.5, -1, 1, 5, 2), mat="bone"))
    if r >= 5:
        for i, (x, yy, hz, a) in enumerate(((-3, -7, 7, 0.4), (2, -2, 9, -0.5), (-1, 3, 8, 0.7))):
            c.append(cube("body", f"shard{i}", (x - 1, yy, hz, 2, 1, 2), (0, 0, 0, a, a, 0), mat="bone"))
    return c


def cow(r):
    c = []
    L = [3, 4, 5, 6, 7][r - 1]
    c += sym(lambda s, t: curve("head", f"horn{t}", (s * 4.4, -3.5, -4.2), [(L * 0.55, 0, 0, -s * 0.9), (L * 0.55, -0.5, 0, -s * 0.2)] if r >= 2 else [(L * 0.8, 0, 0, -s * 0.7)], 1 if r < 4 else 2))
    if r >= 2:
        c.append(cube("body", "hump", (-4, -9, 3, 8, 6, 3), mat="plate" if r == 2 else "dark"))
    if r >= 3:
        for i, yy in enumerate(range(-9, 7, 4)):
            c.append(cube("body", f"ridge{i}", (-0.5, yy, 6 if r >= 2 else 3, 1, 2, 2 + r), mat="bone"))
        c.append(cube("head", "brow", (-4.3, -2.6, -6.4, 8.6, 1, 1), mat="plate"))
    if r >= 4:
        for i, x in enumerate((-2.5, 0, 2.5)):
            c += curve("head", f"crown{i}", (x, -4, -2.5), [(4, -0.15, 0, 0)], 1, "bone")
    if r >= 5:
        for i, (x, yy, hz, a) in enumerate(((-4, -6, 9, 0.4), (3, 0, 10, -0.5), (0, 5, 9, 0.7))):
            c.append(cube("body", f"shard{i}", (x - 1, yy, hz, 2, 1, 2), (0, 0, 0, a, a, 0), mat="bone"))
    return c


def sheep(r):
    c = []
    n = r + 2
    def horn(s, t):
        segs = [(2, 0, 0, -s * 0.9), (2, 0.4, 0, -s * 1.7), (2, 0.9, 0, -s * 2.6)][: min(3, 1 + r)]
        return curve("head", f"horn{t}", (s * 2.2, -3.5, -2.0), segs, 2 if r >= 2 else 1)
    c += sym(horn)
    if r >= 2:
        c += sym(lambda s, t: [cube("head", f"band{t}", (s * 2.2 - 1.5, -4.5, -3.5, 3, 1, 3), mat="plate")])
    if r >= 3:
        c.append(cube("head", "brow", (-3.3, -2.0, -6.4, 6.6, 1, 1), mat="plate"))
    if r >= 4:
        for i, x in enumerate((-2, 0, 2)):
            c += curve("head", f"crown{i}", (x, -4, 0), [(4, -0.15, 0, 0)], 1, "bone")
    for i in range(n):
        c.append(cube("body", f"ridge{i}", (-0.5, -8 + i * 3, 0, 1, 2, 1 + r), mat="cloth" if r < 4 else "bone"))
    return c


def chicken(r):
    c = []
    c.append(cube("head", "crest0", (-0.5, -8 - (r // 2), -1.5, 1, 2 + (r // 2), 3), mat="plate" if r >= 2 else "bone"))
    if r >= 2:
        c.append(cube("head", "crest1", (-0.5, -7, -2.5, 1, 2, 1), mat="plate"))
    c += sym(lambda s, t: [cube("right_leg" if s < 0 else "left_leg", f"spur{t}", (0.5 if s > 0 else -0.5, 3, 0, 1, 2 + (r > 2), 1), (0, 0, 0, 0.5, 0, 0), "bone")])
    if r >= 3:
        for i, x in enumerate((-2, 0, 2)):
            c.append(cube("body", f"plume{i}", (x - 0.5, 2 + i % 2, -2, 1, 5 + r, 1), (0, 0, 0, 0, 0, 0), "plate"))
    if r >= 4:
        for i, x in enumerate((-1.5, 0, 1.5)):
            c += curve("head", f"crown{i}", (x, -6, -0.5), [(3, -0.1, 0, 0)], 1, "bone")
    return c


def zombie(r):
    c = []
    c += [cube("body", "rag0", (-4.5, 9, -2.5, 3, 3, 1), mat="cloth"), cube("body", "rag1", (1.5, 10, -2.5, 3, 3, 1), mat="cloth"),
          cube("body", "rag2", (-3, 9, 1.5, 6, 3, 1), mat="cloth")]
    c.append(cube("right_arm", "bone", (-0.5, 4, 2, 1, 3, 1), (0, 0, 0, -0.2, 0, 0), "bone"))
    if r >= 2:
        c += [cube("head", "helm", (-4.5, -9, -4.5, 9, 4, 9), mat="plate"), cube("head", "guard", (-0.5, -5, -5, 1, 4, 1), mat="plate"),
              cube("right_arm", "pauldron", (-3.5, -2.6, -2.6, 5, 3, 5), mat="plate"), cube("left_arm", "pauldron", (-1.5, -2.6, -2.6, 5, 3, 5), mat="plate"),
              cube("left_arm", "shield", (3.2, 1, -4, 1, 9, 8), mat="plate")]
    if r >= 3:
        for s, t, part in ((-1, "r", "right_arm"), (1, "l", "left_arm")):
            for i in range(3):
                c.append(cube(part, f"claw{t}{i}", (-1.5 + i * 1.4 + (0.5 if s > 0 else 0), 10, -0.5, 1, 3, 1), (0, 0, 0, 0.35, 0, 0), "bone"))
        for i in range(4):
            c.append(cube("body", f"spine{i}", (-0.5, 1 + i * 2.6, 2, 1, 1, 2 + i % 2), mat="bone"))
    if r >= 4:
        c += sym(lambda s, t: curve("head", f"horn{t}", (s * 3.6, -9, -1), [(3, 0, 0, -s * 0.5), (2, 0, 0, -s * 0.1)], 1, "bone"))
        c += sym(lambda s, t: [cube("body", f"spike{t}", (s * 4 - 0.5, -0.5, -1, 1, 1, 2), mat="bone")])
    if r >= 5:
        for i in range(5):
            a = (i - 2) * 0.55
            c += curve("head", f"thorn{i}", (math.sin(a) * 3.2, -9, math.cos(a) * 3.2 - 0.4), [(5 + (i == 2) * 2, 0.0, 0, math.sin(a) * 0.4)], 1, "bone")
        for i, (x, yy, z, a) in enumerate(((-6, 2, 3, 0.5), (6, 5, 3, -0.5), (0, -3, 4, 0.3))):
            c.append(cube("body", f"shard{i}", (x - 1, yy, z, 2, 2, 1), (0, 0, 0, a, a, a), "bone"))
    return c


def skeleton(r):
    c = [cube("head", "band", (-4.5, -6.5, -4.5, 9, 1, 9), mat="cloth"),
         cube("body", "quiver", (1.5, 1, 2, 3, 9, 2), (0, 0, 0, 0, 0, 0.2), "cloth")]
    for i in range(2):
        c.append(cube("body", f"fletch{i}", (2 + i, -2, 2.5, 1, 3, 1), (0, 0, 0, 0, 0, 0.2), "bone"))
    if r >= 2:
        c += [cube("head", "hood0", (-4.5, -9, -0.5, 9, 9, 5), mat="cloth"), cube("head", "hood1", (-4.5, -9.5, -4.5, 9, 1, 9), mat="cloth"),
              cube("right_arm", "bracer", (-1.5, 6, -1.5, 3, 3, 3), mat="plate"), cube("left_arm", "bracer", (-1.5, 6, -1.5, 3, 3, 3), mat="plate")]
    if r >= 3:
        c += sym(lambda s, t: [cube("head", f"horn{t}", (s * 4 - 0.5, -9, -1, 1, 3, 1), (0, 0, 0, 0, 0, -s * 0.4), "bone")])
        c += [cube("right_arm", "pad", (-2.5, -2.6, -1.8, 4, 2, 3.6), mat="plate"), cube("left_arm", "pad", (-1.5, -2.6, -1.8, 4, 2, 3.6), mat="plate")]
    if r >= 4:
        for i in range(3):
            c.append(cube("body", f"cloak{i}", (-3 + i * 2.2, 0, 2.5, 2, 13 + (i % 2) * 2, 1), mat="cloth"))
    if r >= 5:
        for i in range(6):
            a = i * math.pi / 3
            c.append(cube("body", f"bone{i}", (math.sin(a) * 8 - 0.5, 3 + (i % 3) * 3, math.cos(a) * 8 - 0.5, 1, 4, 1), (0, 0, 0, 0, 0, 0.3 * (i % 2 - 0.5)), "bone"))
        for i in range(5):
            a = (i - 2) * 0.55
            c += curve("head", f"thorn{i}", (math.sin(a) * 3.2, -8, math.cos(a) * 3.2), [(4 + (i == 2) * 2, 0, 0, math.sin(a) * 0.4)], 1, "bone")
    return c


def spider(r):
    c = []
    c += sym(lambda s, t: [cube("head", f"fang{t}", (s * 1.5 - 0.5, 2, -8.5, 1, 2 + (r > 2), 1), (0, 0, 0, 0.2, 0, 0), "bone")])
    c.append(cube("body1", "spinneret", (-1.5, -1.5, 6, 3, 3, 3), mat="silk"))
    c.append(cube("body1", "thread", (-0.5, 0, 9, 1, 1, 3 + r), mat="silk"))
    for i in range(4 + r):
        c.append(cube("body1", f"hair{i}", (-4 + (i % 3) * 3.5, -5.2, -5 + (i // 3) * 4, 1, 2 + (r > 2), 1), mat="cloth"))
    if r >= 2:
        c.append(cube("head", "plate", (-4.5, -5, -8.5, 9, 1, 9), mat="plate"))
        for i in range(3):
            c.append(cube("body1", f"dorsal{i}", (-0.5, -7, -5 + i * 4, 1, 3 + i % 2, 2), mat="plate"))
    if r >= 3:
        c.append(cube("body1", "sac", (-4, -6.5, 0, 8, 3, 6), mat="dark"))
    if r >= 4:
        for i, (x, y, z) in enumerate(((-3, 3.5, -3), (3, 3.5, 0), (-3, 3.5, 4), (3, 3.5, 6))):
            c.append(cube("body1", f"egg{i}", (x - 1.5, y, z, 3, 3, 3), mat="silk"))
    if r >= 5:
        for i, (x, y) in enumerate(((-3, -3), (3, -3), (-1.5, -4.5), (1.5, -4.5))):
            c.append(cube("head", f"eye{i}", (x - 1, y - 1, -8.6, 2, 2, 1), mat="bone"))
        for i, (x, yy, z, a) in enumerate(((-6, -8, 0, 0.5), (6, -9, 3, -0.5), (0, -11, 6, 0.3))):
            c.append(cube("body1", f"shard{i}", (x - 1, yy, z, 2, 1, 2), (0, 0, 0, a, a, 0), "bone"))
    return c


def creeper(r):
    c = []
    for i, x in enumerate((-3, 0, 3)):
        c.append(cube("body", f"frill{i}", (x - 0.5, 1 + i, 2, 1, 4, 1 + r), mat="bone"))
    c.append(cube("head", "frillh", (-3.5, -7, 4, 7, 2, 1), mat="plate"))
    if r >= 2:
        c += sym(lambda s, t: curve("head", f"rod{t}", (s * 2.5, -8, 0), [(4 + r, 0, 0, s * 0.15)], 1, "plate"))
    if r >= 3:
        for i, x in enumerate((-3, 3)):
            c.append(cube("body", f"vent{i}", (x - 1, 0, 2.2, 2, 4, 2), mat="dark"))
    if r >= 4:
        c.append(cube("body", "belt", (-4.5, 6, -2.5, 9, 2, 5), mat="plate"))
        c += sym(lambda s, t: [cube("body", f"pack{t}", (s * 4.2 - 1.5 + (1.5 if s > 0 else 0), 1, -1.5, 3, 5, 3), mat="plate")])
    if r >= 5:
        for i in range(5):
            a = (i - 2) * 0.5
            c += curve("head", f"bolt{i}", (math.sin(a) * 3.5, -8, 0), [(5 + (i == 2) * 3, 0, 0, math.sin(a) * 0.3)], 1, "plate")
    return c


KITS = {"pig": pig, "cow": cow, "sheep": sheep, "chicken": chicken, "zombie": zombie, "skeleton": skeleton, "spider": spider, "creeper": creeper}

# ---------------------------------------------------------------------------------------------------------------------
# Vanilla layouts: texture size, the cubes of the vanilla body (u, v, w, h, d, role) and the eyes (u, v, w, h)

LAYOUT = {
    "pig": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 4, 3, 1, "head"), (28, 8, 10, 16, 8, "body"), (0, 16, 4, 6, 4, "leg")], eyes=[(8, 11, 2, 1), (14, 11, 2, 1)]),
    "cow": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 6, "head"), (18, 4, 12, 18, 10, "body"), (0, 16, 4, 12, 4, "leg")], eyes=[(6, 9, 2, 1), (12, 9, 2, 1)]),
    "sheep": dict(size=(64, 32), cubes=[(0, 0, 6, 6, 8, "head"), (28, 8, 8, 16, 6, "body"), (0, 16, 4, 12, 4, "leg")], eyes=[(9, 11, 1, 1), (13, 11, 1, 1)]),
    "chicken": dict(size=(64, 32), cubes=[(0, 0, 4, 6, 3, "head"), (0, 9, 6, 8, 6, "body"), (26, 0, 3, 5, 3, "leg"), (24, 13, 1, 4, 6, "limb")], eyes=[(3, 5, 1, 1), (6, 5, 1, 1)]),
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
            if role in ("body", "limb", "head") and name in ("front", "left", "right", "back"):
                for _ in range(1 + r // 2):
                    slash(ov, rect, rng, (pal["dark"][1], pal["bone"][2]))
            if r >= 2 and role in ("body", "limb") and name in ("front", "back", "left", "right") and rect[2] >= 4 and rect[3] >= 4:
                # plate patches in the family's metal, with a light top edge
                px, py = rect[0] + rng.randrange(0, max(1, rect[2] - 3)), rect[1] + rng.randrange(0, max(1, rect[3] - 3))
                for yy in range(py, min(py + 3, rect[1] + rect[3])):
                    for xx in range(px, min(px + 3, rect[0] + rect[2])):
                        ov.set(xx, yy, pal["plate"][3 if yy == py else 1 + (xx + yy) % 2])
            if r >= 3 and name == "front" and role in ("body", "limb", "head"):
                # a few thin veins: the body's front most, a limb's one from the fourth rung, the head's at the fifth
                n = {"body": r - 2, "limb": 1 if r >= 4 else 0, "head": 1 if r >= 5 else 0}[role]
                for _ in range(n):
                    crack(ov, rect, rng, pal["dark"][0], gl)
    for (u, v, w, h) in LAYOUT[layout]["eyes"]:
        for yy in range(v, v + h):
            for xx in range(u, u + w):
                gl.set(xx, yy, (255, 255, 255, 255))
    return ov, gl

# ---------------------------------------------------------------------------------------------------------------------


def build(out):
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
