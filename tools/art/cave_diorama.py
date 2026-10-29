"""Renders cave corners dressed with the Living Deep's skin (SPEC §42, the style sheet): a floor, two walls and a shelf of
one rock, with rubble at the wall's foot, stalagmites and stalactites, pebbles, a puddle in the cave's water colour,
overlays on the walls, a web, and the region's particles and creatures, lit by a torch as the game lights blocks
(block light falling one level a block, vanilla's lightmap curve and colour, the faces' fixed shading).

  python3 tools/art/cave_diorama.py OUT_DIR [--res DIR]     every scene, lit by the torch and in flat light

Textures come from --res first (a scratch build of tools/art/specs), then src/main/resources, then the game's own; models
from tools/art/specs/deep_skin.py's models(). Orthographic, nearest-neighbour; not the game: no AO, no Lens.
"""
import argparse
import importlib.util
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
REF = "/Volumes/MEDIA/Awaken/.ref/src"
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
ROOTS = [RES, REF]
BRIGHTNESS = 1.0  # the game's brightness setting (0.5 is its default, 1.0 'Bright'): the sheet shows the bright end
_tex = {}


def texture(ident):
    if ident in _tex:
        return _tex[ident]
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    for root in ROOTS:
        p = os.path.join(root, "assets", ns, "textures", path + ".png")
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            arr = np.asarray(im.crop((0, 0, im.width, min(im.width, im.height))), dtype=float)
            _tex[ident] = arr
            return arr
    raise FileNotFoundError(ident)


def spec(name):
    s = importlib.util.spec_from_file_location(name, os.path.join(HERE, "specs", name + ".py"))
    m = importlib.util.module_from_spec(s)
    s.loader.exec_module(m)
    return m


SKIN = spec("deep_skin")
MODELS = SKIN.model_dict()


def face_quad(frm, to, d):
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


class Scene:
    """Quads in block units: (corners, uv, texture, shade, normal, tint, glow)."""

    def __init__(self):
        self.solid = {}
        self.quads = []
        self.sprites = []
        self.lights = []

    def block(self, x, y, z, side, top=None):
        self.solid[(x, y, z)] = (side, top or side)

    def fill(self, x0, y0, z0, x1, y1, z1, side, top=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.block(x, y, z, side, top)

    def model(self, name, x, y, z, rot=0, tint=None):
        m = MODELS[name]
        tex = m.get("textures", {})
        els = m.get("elements")
        if els is None and m.get("parent", "").endswith("block/cross"):
            t = tex["cross"]
            for a in (45, -45):
                pts = [rot_y(p, (8, 8, 8), a) for p in face_quad((0.8, 0, 8), (15.2, 16, 8), "north")]
                self.add([(x + p[0] / 16, y + p[1] / 16, z + p[2] / 16) for p in pts], [0, 0, 16, 16], t, 0.8, None, None, 0, both=True)
            return
        for e in els:
            r = e.get("rotation")
            for d, f in e["faces"].items():
                pts = face_quad(e["from"], e["to"], d)
                n = NORMAL[d]
                if r and r.get("axis") == "y":
                    pts = [rot_y(p, r["origin"], r["angle"]) for p in pts]
                    n = rot_y(n, (0, 0, 0), r["angle"])
                if rot:
                    pts = [rot_y(p, (8, 8, 8), rot) for p in pts]
                    n = rot_y(n, (0, 0, 0), rot)
                t = f["texture"]
                while t.startswith("#"):
                    t = tex[t[1:]]
                self.add([(x + p[0] / 16, y + p[1] / 16, z + p[2] / 16) for p in pts], f.get("uv", [0, 0, 16, 16]), t, SHADE[d], n,
                         tint if "tintindex" in f else None, 0, both=len(e["faces"]) == 1)

    def overlay(self, tex, x, y, z, wall, glow=0.0):
        """A wall overlay in the air block (x, y, z), flat on its wall side ('north' = the wall at z - 1 ...)."""
        e = 0.1 / 16
        if wall == "north":
            pts = [(x, y + 1, z + e), (x + 1, y + 1, z + e), (x + 1, y, z + e), (x, y, z + e)]
            n = (0, 0, 1)
        elif wall == "west":
            pts = [(x + e, y + 1, z + 1), (x + e, y + 1, z), (x + e, y, z), (x + e, y, z + 1)]
            n = (1, 0, 0)
        else:
            raise ValueError(wall)
        self.add(pts, [0, 0, 16, 16], tex, 0.8 if wall == "north" else 0.6, n, None, glow, both=True)

    def add(self, pts, uv, tex, shade, n, tint, glow, both=False):
        self.quads.append((pts, uv, tex, shade, n, tint, glow, both))

    def sprite(self, img, x, y, z, size, glow=0.0, lit=True):
        """A camera-facing picture (a particle or a creature), size in blocks across."""
        self.sprites.append((img, (x, y, z), size, glow, lit))

    def light(self, x, y, z, level=14, colour=None):
        self.lights.append(((x, y, z), level, colour))

    def solid_faces(self):
        for (x, y, z), (side, top) in self.solid.items():
            for d, (dx, dy, dz) in NORMAL.items():
                if (x + dx, y + dy, z + dz) in self.solid:
                    continue
                t = top if d in ("up", "down") else side
                pts = [(x + p[0] / 16, y + p[1] / 16, z + p[2] / 16) for p in face_quad((0, 0, 0), (16, 16, 16), d)]
                self.add(pts, [0, 0, 16, 16], t, SHADE[d], (dx, dy, dz), None, 0)


def lightmap(level):
    """Vanilla's block light: brightness from the level (with the default 50% brightness), and its warm colour."""
    f = np.clip(level / 15.0, 0, 1)
    b = f / (4 - 3 * f)
    b = b + (1 - (1 - b) ** 4 - b) * BRIGHTNESS
    return np.stack([b, b * ((b * 0.6 + 0.4) * 0.6 + 0.4), b * (b * b * 0.6 + 0.4)], -1)


def render(scene, yaw=45, pitch=32, S=56, W=900, H=700, flat=False, centre=(3.5, 2.0, 3.5), ambient=0.035):
    img = np.zeros((H, W, 3))
    img[:] = (6, 6, 8)
    depth = np.full((H, W), -1e9)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p[0] - centre[0], p[1] - centre[1], p[2] - centre[2]
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return np.array([W / 2 + x * S, H * 0.55 - y * S, z])

    def view_dir():
        # the direction toward the camera, in world space (inverse of proj's rotations applied to +z)
        x, y, z = 0.0, 0.0, 1.0
        y, z = y * cp + z * sp, -y * sp + z * cp
        x, z = x * cy + z * sy, -x * sy + z * cy
        return np.array([x, y, z])

    V = view_dir()

    def shade_at(wp, n, base):
        if flat:
            return np.ones(wp.shape[:-1] + (3,)) * base
        total = np.zeros(wp.shape[:-1] + (3,))
        q = wp + np.array(n) * 0.5 if n is not None else wp
        for (lx, ly, lz), lv, col in scene.lights:
            d = np.abs(q[..., 0] - lx) + np.abs(q[..., 1] - ly) + np.abs(q[..., 2] - lz)
            c = lightmap(lv - d)
            if col is not None:
                c = c * np.array(col)
            total = np.maximum(total, c)
        return (total + ambient) * base

    scene.solid_faces()
    jobs = []
    for pts, uv, tex, shade, n, tint, glow, both in scene.quads:
        if n is not None and not both and np.dot(np.array(n, dtype=float), V) <= 1e-6:
            continue
        P = [proj(p) for p in pts]
        jobs.append((P, [np.array(p, dtype=float) for p in pts], uv, texture(tex), shade, n, tint, glow, sum(p[2] for p in P) / 4))

    def raster(P, Wp, uv, tx, shade, n, tint, glow, pass_):
        u0, v0, u1, v1 = uv
        cuv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (P[i] for i in tri)
            wa, wb, wc = (Wp[i] for i in tri)
            ta, tb, tc = (cuv[i] for i in tri)
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
            ti = np.clip(np.floor(v / 16 * th).astype(int), 0, th - 1)
            tj = np.clip(np.floor(u / 16 * tw).astype(int), 0, tw - 1)
            col = tx[ti, tj]
            alpha = col[..., 3] / 255
            region = depth[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (alpha > 0.03) & (z > region - 1e-4)
            ok &= (alpha > 0.99) if pass_ == 0 else (alpha <= 0.99)
            if not ok.any():
                continue
            wp = w0[..., None] * wa + w1[..., None] * wb + w2[..., None] * wc
            rgb = col[..., :3]
            if tint is not None:
                rgb = rgb * np.array(tint) / 255
            lit = shade_at(wp, n, shade)
            rgb = rgb * lit + (rgb * glow if glow else 0)
            dst = img[y0:y1 + 1, x0:x1 + 1]
            al = alpha[..., None]
            dst[ok] = (rgb * al + dst * (1 - al))[ok]
            if pass_ == 0:
                region[ok] = z[ok]

    for j in jobs:
        raster(*j[:8], 0)
    for j in sorted(jobs, key=lambda j: j[8]):
        raster(*j[:8], 1)
    # sprites, far to near, depth-tested against the blocks
    for im, pos, size, glow, lit in sorted(scene.sprites, key=lambda s: proj(s[1])[2]):
        p = proj(pos)
        arr = np.asarray(im.convert("RGBA"), dtype=float)
        px = max(1, int(round(size * S)))
        scale = px / max(arr.shape[0], arr.shape[1])
        h, w = max(1, int(arr.shape[0] * scale)), max(1, int(arr.shape[1] * scale))
        ys = np.clip((np.arange(h) / scale).astype(int), 0, arr.shape[0] - 1)
        xs = np.clip((np.arange(w) / scale).astype(int), 0, arr.shape[1] - 1)
        sub = arr[ys][:, xs]
        X0, Y0 = int(p[0] - w / 2), int(p[1] - h / 2)
        for yy in range(h):
            for xx in range(w):
                X, Y = X0 + xx, Y0 + yy
                if not (0 <= X < W and 0 <= Y < H) or depth[Y, X] > p[2]:
                    continue
                c = sub[yy, xx]
                a = c[3] / 255
                if a < 0.03:
                    continue
                k = shade_at(np.array(pos, dtype=float)[None], None, 1.0)[0] if lit and not flat else np.ones(3)
                rgb = c[:3] * k + c[:3] * glow
                if glow and not lit:
                    img[Y, X] = img[Y, X] + rgb * a  # additive, like the game's glow pass
                else:
                    img[Y, X] = rgb * a + img[Y, X] * (1 - a)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


# ---- the creatures, put together from the underlife sheet ------------------------------------------------------------

def underlife_cell(col, row):
    sheet = Image.open(os.path.join(ROOTS[0], "assets/awaken/textures/entity/caves/underlife.png")).convert("RGBA")
    return sheet.crop((col * 16, row * 16, col * 16 + 16, row * 16 + 16))


def moth(kind, spread=1.0):
    """A moth from above: its two wings either side of the body, a little tented."""
    wing = underlife_cell(kind, 0)
    body = underlife_cell(3, 0).crop((kind * 5 + 1, 0, kind * 5 + 4, 16))
    out = Image.new("RGBA", (34, 18))
    w = wing.resize((max(4, int(16 * spread)), 16), Image.NEAREST)
    out.alpha_composite(w, (17, 1))
    out.alpha_composite(w.transpose(Image.FLIP_LEFT_RIGHT), (17 - w.width, 1))
    out.alpha_composite(body, (16, 0))
    return out


# ---- scenes ----------------------------------------------------------------------------------------------------------

ROCK_BLOCK = {"stone": ("minecraft:block/stone", None), "deepslate": ("minecraft:block/deepslate", "minecraft:block/deepslate_top"),
              "tuff": ("minecraft:block/tuff", None), "rootstone": ("awaken:block/caves/rootstone", None),
              "ochre": ("awaken:block/caves/ochre_stone", None)}


def corner(rock, lichen, water, extras=None):
    """A cave corner 7 x 7: walls on the north and west, a shelf along the north wall's top with stalactites under it,
    a step up in the far corner, the skin laid where the game would lay it."""
    s = Scene()
    side, top = ROCK_BLOCK[rock]
    s.fill(-1, -1, -1, 7, 0, 7, side, top)        # floor
    s.fill(-1, 1, -1, 7, 5, -1, side, top)        # north wall
    s.fill(-1, 1, -1, -1, 5, 7, side, top)        # west wall
    s.fill(0, 4, 0, 6, 5, 1, side, top)           # the shelf
    s.fill(0, 1, 0, 1, 1, 2, side, top)           # a step in the corner
    # rubble where the floor meets a wall, and on the step's foot
    for x, z in ((2, 0), (3, 0), (5, 0), (0, 3), (0, 5), (2, 1), (2, 2)):
        s.model(f"{rock}_rubble_{(x + z) % 3}", x, 1, z, rot=0 if z == 0 else 90)  # the heap's high side to the wall
    s.model(f"{rock}_rubble_1", 0, 2, 0)
    # stalactites under the shelf, stalagmites under them
    for x, z, n in ((3, 1, 2), (5, 1, 3), (6, 1, 1)):
        parts = ("base", "frustum", "tip")[-n:] if n < 3 else ("base", "frustum", "tip")
        for i, t in enumerate(parts if n > 1 else ("tip",)):
            s.model(f"{rock}_formation_down_{t}", x, 3 - i, z)
    for x, z, parts in ((5, 2, ("base", "tip")), (3, 3, ("frustum", "tip")), (6, 4, ("tip",)), (4, 5, ("middle", "frustum", "tip"))):
        for i, t in enumerate(parts):
            s.model(f"{rock}_formation_up_{t}", x, 1 + i, z)
    # pebbles, a puddle under the drips
    for x, z, v in ((1, 4, 0), (4, 3, 1), (2, 6, 2), (6, 6, 0), (5, 5, 1)):
        s.model(f"{rock}_pebbles_{v}", x, 1, z, rot=90 * ((x + 2 * z) % 4))
    for x, z in ((3, 2), (4, 2), (4, 1)):
        s.model("puddle", x, 1, z, tint=water)
    # the walls' overlays
    for y in (1, 2, 3):
        s.overlay(f"awaken:block/caves/lichen_{lichen}", 0, y, 4 + (y % 2), "west")
    s.overlay(f"awaken:block/caves/lichen_{lichen}", 0, 3, 3, "west")
    s.overlay("awaken:block/caves/mineral_crust", 0, 2, 6, "west")
    for x in (2, 4):
        s.overlay("awaken:block/caves/wet_streaks", x, 2, 0, "north")
        s.overlay("awaken:block/caves/wet_streaks", x, 3, 0, "north")
    s.model("dusty_web", 0, 3, 2)
    s.light(4.5, 1.5, 4.5)                         # your torch
    if extras:
        extras(s)
    return s


def particles(s, name, count, seed, box=(0.5, 1.2, 0.5, 6.5, 3.8, 6.5), size=0.14, glow=0.0, lit=True):
    import random
    r = random.Random(seed)
    files = sorted(f for f in os.listdir(os.path.join(ROOTS[0], "assets/awaken/textures/particle/caves")) if f.startswith(name))
    for _ in range(count):
        f = r.choice(files)
        im = Image.open(os.path.join(ROOTS[0], "assets/awaken/textures/particle/caves", f)).convert("RGBA")
        im = im.crop((0, 0, im.width, im.width))
        s.sprite(im, r.uniform(box[0], box[3]), r.uniform(box[1], box[4]), r.uniform(box[2], box[5]), size * r.uniform(0.7, 1.2), glow, lit)


def rootways(s):
    particles(s, "spore", 14, 1, glow=0.35)
    particles(s, "pollen", 8, 2)
    for i, (x, y, z) in enumerate(((5.2, 2.6, 5.0), (6.1, 2.2, 5.9), (4.9, 3.0, 6.2))):
        s.sprite(moth(0, (1.0, 0.7, 0.85)[i]), x, y, z, 0.36)


def ochre(s):
    particles(s, "warm_dust", 22, 3, glow=0.25)
    beetle = underlife_cell(2, 1)
    s.sprite(beetle, 0.15, 1.6, 4.6, 0.32)
    s.sprite(beetle.rotate(70), 3.0, 1.12, 5.6, 0.3)


SCENES = {
    "stone": lambda: corner("stone", "shallows", (63, 118, 228)),
    "deepslate": lambda: corner("deepslate", "deep", (63, 118, 228)),
    "tuff": lambda: corner("tuff", "middle", (63, 118, 228)),
    "rootways": lambda: corner("rootstone", "shallows", (0x3b, 0x6f, 0x62), rootways),
    "ochre_deep": lambda: corner("ochre", "middle", (0xa8, 0x62, 0x2c), ochre),
}


def formations_plate():
    """The four thicknesses of each skin rock, seen almost level: stalactites hanging from a roof strip, stalagmites
    rising from a floor strip, one to four blocks long."""
    s = Scene()
    for i, rock in enumerate(("stone", "deepslate", "tuff")):
        x0 = i * 6
        side, top = ROCK_BLOCK[rock]
        s.fill(x0, 0, 0, x0 + 4, 0, 0, side, top)
        s.fill(x0, 5, 0, x0 + 4, 5, 0, side, top)
        for k, parts in enumerate((("tip",), ("frustum", "tip"), ("middle", "frustum", "tip"), ("base", "middle", "frustum", "tip"))):
            if k < 3:
                for j, t in enumerate(parts):
                    s.model(f"{rock}_formation_up_{t}", x0 + k + 1, 1 + j, 0)
            for j, t in enumerate(parts):
                s.model(f"{rock}_formation_down_{t}", x0 + k, 4 - j, 0)
    return s


def crop(im, pad=10):
    a = np.asarray(im).astype(int)
    m = (np.abs(a - np.array([6, 6, 8])).sum(-1) > 6)
    ys, xs = np.where(m)
    if not len(xs):
        return im
    return im.crop((max(0, xs.min() - pad), max(0, ys.min() - pad), min(im.width, xs.max() + pad), min(im.height, ys.max() + pad)))


def tile(ident, n=3, z=4):
    t = Image.fromarray(texture(ident).astype(np.uint8), "RGBA")
    out = Image.new("RGBA", (16 * n, 16 * n), (14, 14, 16, 255))
    for i in range(n):
        for j in range(n):
            out.alpha_composite(t, (i * 16, j * 16))
    return out.resize((16 * n * z, 16 * n * z), Image.NEAREST)


def enlarge(im, z, bg=(26, 28, 32, 255)):
    out = Image.new("RGBA", im.size, bg)
    out.alpha_composite(im.convert("RGBA"))
    return out.resize((im.width * z, im.height * z), Image.NEAREST)


def sheet(out):
    """Every picture the style sheet shows, into out/."""
    os.makedirs(out, exist_ok=True)
    save = lambda im, name: im.convert("RGB").save(os.path.join(out, name + ".png"), optimize=True)
    for name, make in SCENES.items():
        save(crop(render(make())), f"scene_{name}")
        save(crop(render(make(), flat=True)), f"scene_{name}_flat")
    save(crop(render(formations_plate(), yaw=28, pitch=10, S=44, W=1300, H=420, flat=True, centre=(8.5, 2.5, 0.5))), "formations")
    for rock in SKIN.ROCKS:
        ident = {"stone": "minecraft:block/stone", "deepslate": "minecraft:block/deepslate", "tuff": "minecraft:block/tuff",
                 "ochre": "awaken:block/caves/ochre_stone"}.get(rock, f"awaken:block/caves/{rock}")
        save(tile(ident), f"rock_{rock}")
    for rock in ("stone", "deepslate", "tuff", "rootstone", "ochre"):
        for piece in ("rubble", "formation", "formation_tip"):
            save(tile(f"awaken:block/caves/{rock}_{piece}", 2, 5), f"skin_{rock}_{piece}")
    for name in ("lichen_shallows", "lichen_middle", "lichen_deep", "mineral_crust", "wet_streaks", "dusty_web", "puddle"):
        t = Image.fromarray(texture(f"awaken:block/caves/{name}").astype(np.uint8), "RGBA")
        bg = Image.fromarray(texture("minecraft:block/stone").astype(np.uint8), "RGBA")
        if name == "puddle":
            for label, tint in (("plain", (63, 118, 228)), ("rootways", (0x3b, 0x6f, 0x62)), ("ochre", (0xa8, 0x62, 0x2c))):
                a = np.asarray(t, dtype=float)
                a[..., :3] *= np.array(tint) / 255
                b = bg.copy()
                b.alpha_composite(Image.fromarray(a.astype(np.uint8), "RGBA"))
                save(b.resize((160, 160), Image.NEAREST), f"overlay_puddle_{label}")
            continue
        b = bg.copy()
        b.alpha_composite(t)
        save(b.resize((160, 160), Image.NEAREST), f"overlay_{name}")
    part = os.path.join(ROOTS[0], "assets/awaken/textures/particle/caves")
    for f in sorted(os.listdir(part)):
        if f.endswith(".png"):
            im = Image.open(os.path.join(part, f)).convert("RGBA")
            save(enlarge(im.crop((0, 0, im.width, im.width)), 160 // im.width), "p_" + f[:-4])
    cells = {"moth_dusk": moth(0), "moth_glass": moth(1), "moth_ash": moth(2), "glow_worms": underlife_cell(0, 1),
             "cave_swift": underlife_cell(1, 1), "rust_beetle": underlife_cell(2, 1), "cave_beetle": underlife_cell(3, 1),
             "millipede": underlife_cell(0, 2), "crayfish": underlife_cell(1, 2), "blind_fish": underlife_cell(2, 2),
             "cricket": underlife_cell(3, 2), "bat_roosting": underlife_cell(0, 3), "chitin_crawler": underlife_cell(3, 3)}
    for name, im in cells.items():
        save(enlarge(im, 10 if im.width <= 16 else 6, (58, 56, 54, 255)), "c_" + name)
    wing = underlife_cell(1, 3)
    bat = Image.new("RGBA", (40, 16))
    bat.alpha_composite(wing, (20, 0))
    bat.alpha_composite(wing.transpose(Image.FLIP_LEFT_RIGHT), (4, 0))
    bat.alpha_composite(underlife_cell(2, 3).crop((4, 0, 12, 16)), (16, 0))
    save(enlarge(bat, 6, (58, 56, 54, 255)), "c_bat_flying")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--res")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--sheet", action="store_true", help="every picture of the style sheet")
    a = ap.parse_args()
    if a.res:
        ROOTS.insert(0, os.path.abspath(a.res))
    if a.sheet:
        sheet(a.out)
        return
    os.makedirs(a.out, exist_ok=True)
    for name, make in SCENES.items():
        if a.only and name not in a.only:
            continue
        render(make()).save(os.path.join(a.out, f"{name}_torch.png"))
        render(make(), flat=True).save(os.path.join(a.out, f"{name}_flat.png"))
        print(name)


if __name__ == "__main__":
    main()
