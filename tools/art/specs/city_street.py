"""The Carillon street kit's art (M4 B3a, docs/carillon/briefs/M4-B3a-sonnet-1.md): awnings, signs, hang lines, banners,
flagpoles, market goods and furniture, carts, casks, benches, planters, lanterns, nets and rings. Textures, models,
blockstates, lang and mineable tags, in the same register as the kit (city_kit.py): 16x16 per face, crisp, 4-6 tone ramps,
light from the top-left, colours anchored on real vanilla textures.

    python3 -I tools/art/build_all.py city_street                 textures (+ the laundry_* and street_lamp_glass_lit re-skins)
    python3 -I tools/art/specs/city_street.py content             blockstates, models, lang, mineable tags
    python3 -I tools/art/specs/city_street.py check               every blockstate key covers the table's properties; models, textures resolve
    python3 -I tools/art/specs/city_street.py sheet <out.png> [prefix...]   contact sheet of every new texture at 4x (frames side by side)
    python3 -I tools/art/specs/city_street.py mock <out.png>      assembled models, rendered from the real blockstate/model data
    python3 -I tools/art/specs/city_street.py view <out.png> <block> [k=v ...]   one block (all states of one property with k=*)

Everything is deterministic (rng_for("city_street", name)). Models are drawn for facing=north, the wall at z=16, and
rotated by the blockstate (rot_variant). Small things are a few real boxes whose faces are painted into an automatic atlas
at 1 texel per pixel (Atlas / MB below); the hero textures (awnings, signs, banners, laundry, bunting) are drawn by hand.
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, rng_for, value_noise  # noqa: E402
import city_kit as ck  # noqa: E402
from city_kit import (R, rgb, lerp, mul, tint, put, noisy, fit, face, rot_variant, weighted, step, clamp8,  # noqa: E402,F401
                      IRON, HEMP, CREAM, SPRUCE_DARK, TIMBER, DIRS, YROT, CUT, b)

ROOT = ck.ROOT
ASSETS = ck.ASSETS
TEX = ck.TEX
MERGE = ck.MERGE
VANILLA = ck.VANILLA
PARENT = "minecraft:block/block"

# ---------------------------------------------------------------------------------------------------------------------
# palettes (dark -> light, index 3 is the body colour). Anchors: spruce_planks (114,84,48), oak_planks (162,130,78),
# dark_oak_planks (66,43,20), stripped_spruce_log (116,90,52), iron_bars (136,139,135), hay_block (166,137,38).

SPRUCE = R("#2d2011", "#493219", "#664827", "#7a5a33", "#8f6c3d", "#a47f49")
OAK = R("#5c4727", "#7a5f33", "#957843", "#a78a50", "#bb9d60", "#cfb374")
DARKOAK = R("#1f1308", "#2f1e0e", "#432c14", "#573a1c", "#6c4a26", "#815c33")
WEATHERED = R("#3a3226", "#554a3a", "#6f6250", "#877966", "#9c8e79", "#b1a38c")
FORGE = R("#0d0c0f", "#17161a", "#25242a", "#363540", "#4e4d59", "#6c6b78")        # black wrought iron
STEEL6 = R("#1c1e22", "#2f3238", "#474b53", "#636872", "#868b95", "#aab0b9")        # lighter worked iron (hoops, tools)
GILT = R("#3d2b0c", "#6b4d17", "#9a7424", "#c49c3c", "#e4c466", "#f6e4a4")
BURLAP = R("#44341e", "#654e2c", "#876d3c", "#a08550", "#b99f66", "#d0b880")
HAY = R("#5e440c", "#8a6812", "#b08a1e", "#c9a42c", "#dfbd42", "#f0d56c")
LINEN = R("#7d7562", "#a49c85", "#c6bda3", "#dcd3ba", "#ece4cc", "#f8f2e0")
SOIL = R("#241810", "#33231a", "#43301f", "#573e29", "#6b4d33")
STONE = R("#4a4948", "#605f5e", "#787674", "#8f8d89", "#a8a6a0", "#c0beb6")        # pale moulded stone, a touch warm
LEAFG = R("#10321a", "#1a4d22", "#276b2b", "#378a37", "#55a645", "#7cc15c")


def cloth_ramp(base):
    """A six-step canvas ramp from one base colour: shadows cool and deep, lights warm (as citizens' mk())."""
    out = []
    for f in (0.40, 0.60, 0.80, 1.0, 1.17, 1.34):
        r, g, bl = (base[0] * f, base[1] * f, base[2] * f)
        if f < 1:
            r, g, bl = r * 0.95, g * 0.97, bl * 1.05
        elif f > 1:
            r, g, bl = r + 10 * (f - 1), g + 7 * (f - 1), bl + 2 * (f - 1)
        out.append((clamp8(r), clamp8(g), clamp8(bl), 255))
    return out


def cloth_ramp_fine(base, cool=0.04, warm=8):
    """A finer six-step canvas ramp (steps of ~10%): one step of noise reads as cloth weave, two as a fold."""
    out = []
    for f in (0.56, 0.73, 0.87, 1.0, 1.1, 1.2):
        r, g, bl = (base[0] * f, base[1] * f, base[2] * f)
        if f < 1:
            r, g, bl = r * (1 - cool * 0.5), g * (1 - cool * 0.2), bl * (1 + cool)
        elif f > 1:
            r, g, bl = r + warm * (f - 1) * 5, g + warm * (f - 1) * 3.5, bl + warm * (f - 1)
        out.append((clamp8(r), clamp8(g), clamp8(bl), 255))
    return out


# canvas colours: the wool dyes desaturated ~15% and warmed
CANVAS = {
    "red": cloth_ramp_fine((172, 60, 48)), "blue": cloth_ramp_fine((62, 88, 144)), "yellow": cloth_ramp_fine((222, 176, 60)),
    "green": cloth_ramp_fine((82, 114, 58)), "orange": cloth_ramp_fine((212, 122, 48)), "plum": cloth_ramp_fine((112, 60, 106)),
}
CREAMCANVAS = R("#8a7f67", "#aa9f84", "#c4b99c", "#d9cfb2", "#e7dec3", "#f3ecd6")     # undyed linen canvas, warm in the shade


# ---------------------------------------------------------------------------------------------------------------------
# the world: every texture, model and blockstate this spec makes, collected once

FACE_NAMES = {"N": "north", "S": "south", "E": "east", "W": "west", "U": "up", "D": "down"}


class World:
    def __init__(self):
        self.tex = {}        # name -> Canvas, or a list of Canvas (an animation)
        self.meta = {}       # name -> (frametime, interpolate)
        self.models = {}
        self.states = {}
        self.lang = {}
        self.axe = []
        self.pickaxe = []
        self.atlases = {}

    def atlas(self, name):
        if name not in self.atlases:
            self.atlases[name] = Atlas(self, name)
        return self.atlases[name]

    def texture(self, name, c):
        assert name not in self.tex, "duplicate texture " + name
        self.tex[name] = c

    def animated(self, name, frames, frametime, interpolate=False):
        assert name not in self.tex, "duplicate texture " + name
        self.tex[name] = frames
        self.meta[name] = (frametime, interpolate)

    def model(self, name, m):
        assert name not in self.models, "duplicate model " + name
        self.models[name] = m

    def state(self, block, variants):
        assert block not in self.states, "duplicate blockstate " + block
        self.states[block] = variants

    def frame0(self, name):
        t = self.tex[name]
        return t[0] if isinstance(t, list) else t


def fnum(v):
    v = round(v, 3)
    return int(v) if v == int(v) else v


class View:
    """A clipped window onto a canvas: painters draw in local coordinates and never spill into a neighbour's rectangle."""

    def __init__(self, c, x, y, w, h):
        self.c, self.x0, self.y0, self.w, self.h = c, x, y, w, h

    def set(self, x, y, col):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c.set(self.x0 + x, self.y0 + y, col)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.c.get(self.x0 + x, self.y0 + y)
        return (0, 0, 0, 0)

    def blend(self, x, y, col, a):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c.blend(self.x0 + x, self.y0 + y, col, a)


class Atlas:
    """A family's texture sheets (16x16 each): rectangles are handed out first-fit, so painted faces stay at 1 texel/px."""

    def __init__(self, world, name):
        self.world, self.name = world, name
        self.sheets = []        # (texture name, Canvas, occupancy)
        self.cache = {}         # shared face rectangles by key

    def alloc(self, w, h):
        w = max(1, min(16, int(math.ceil(w - 1e-6))))
        h = max(1, min(16, int(math.ceil(h - 1e-6))))
        for (n, c, occ) in self.sheets:
            for y in range(0, 16 - h + 1):
                for x in range(0, 16 - w + 1):
                    if all(not occ[yy][xx] for yy in range(y, y + h) for xx in range(x, x + w)):
                        for yy in range(y, y + h):
                            for xx in range(x, x + w):
                                occ[yy][xx] = True
                        return n, c, x, y, w, h
        n = self.name if not self.sheets else f"{self.name}_{len(self.sheets)}"
        c = Canvas()
        occ = [[False] * 16 for _ in range(16)]
        self.sheets.append((n, c, occ))
        self.world.texture(n, c)
        return self.alloc(w, h)


class MB:
    """A model under construction: boxes whose faces are painted into the atlas by a material, or explicit elements."""

    def __init__(self, atlas, name, ao=True):
        self.atlas, self.name, self.ao = atlas, name, ao
        self.els = []
        self.vars = {}          # texture name -> '#tN'
        self.n = 0
        self.extra = {}

    def var(self, texname):
        if texname not in self.vars:
            self.vars[texname] = "#t%d" % len(self.vars)
        return self.vars[texname]

    def box(self, a, b, mat, faces="NESWUD", rot=None, shade=True, emit=0, override=None, key=None, cull=""):
        x0, y0, z0 = a
        x1, y1, z1 = b
        dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
        sizes = {"N": (dx, dy), "S": (dx, dy), "E": (dz, dy), "W": (dz, dy), "U": (dx, dz), "D": (dx, dz)}
        rng = rng_for("city_street", self.name, self.n)
        self.n += 1
        groups = []
        for g in ("NS", "EW", "U", "D"):
            have = [f for f in g if f in faces]
            if have:
                groups.append((g, have))
        fdict = {}
        for g, have in groups:
            rep = have[0]
            w, h = sizes[rep]
            ck_ = (key, g, round(w, 2), round(h, 2)) if key else None
            if ck_ and ck_ in self.atlas.cache:
                tname, ux, uy = self.atlas.cache[ck_]
            else:
                m = mat.get(rep) or mat.get("*") if isinstance(mat, dict) else mat
                tname, canvas, ux, uy, wi, hi = self.atlas.alloc(w, h)
                m(View(canvas, ux, uy, wi, hi), 0, 0, wi, hi, rep, rng, (dx, dy, dz))
                if ck_:
                    self.atlas.cache[ck_] = (tname, ux, uy)
            v = self.var(tname)
            for f in have:
                fdict[f] = (v, [ux, uy, ux + min(w, 16), uy + min(h, 16)]) + ((True,) if f in cull else ())
        self.els.append(E(a, b, fdict, rot=rot, shade=shade, emit=emit, override=override))
        return self

    def raw(self, el):
        self.els.append(el)
        return self

    def tex(self, v, texname):
        """Bind an explicit texture (not from the atlas) and return its variable."""
        return self.var(texname)

    def build(self, cutout=None, ao=None, extra_textures=None, **kw):
        textures = {}
        for tn, v in self.vars.items():
            textures[v[1:]] = b(tn)
        first = next(iter(self.vars)) if self.vars else None
        d = {"parent": PARENT}
        d["textures"] = {"particle": b(first) if first else "minecraft:block/spruce_planks"}
        d["textures"].update(textures)
        if extra_textures:
            d["textures"].update(extra_textures)
        d["elements"] = self.els
        if (self.ao if ao is None else ao) is False:
            d["ambientocclusion"] = False
        d.update(kw)
        return d


def E(a, b_, faces, rot=None, shade=True, emit=0, override=None):
    """An element: faces maps a face letter to (texture variable, uv rect)."""
    el = {"from": [fnum(v) for v in a], "to": [fnum(v) for v in b_]}
    if rot:
        axis, angle, origin = rot[0], rot[1], rot[2]
        r = {"origin": [fnum(v) for v in origin], "axis": axis, "angle": fnum(angle)}
        if len(rot) > 3 and rot[3]:
            r["rescale"] = True
        el["rotation"] = r
    if not shade:
        el["shade"] = False
    if override:
        el["shade_direction_override"] = override
    if emit:
        el["light_emission"] = emit
    el["faces"] = {}
    for k, v in faces.items():
        tv, uv = v[0], v[1]
        fd = {"uv": [fnum(u) for u in uv], "texture": tv}
        if len(v) > 2 and v[2]:
            fd["cullface"] = FACE_NAMES[k]
        el["faces"][FACE_NAMES[k]] = fd
    return el


class _ImgCanvas:
    def __init__(self, im):
        self.im = im

    def get(self, x, y):
        return self.im.getpixel((x, y)) if 0 <= x < self.im.width and 0 <= y < self.im.height else (0, 0, 0, 0)


def needs_cutout(world, m):
    """True if any face of the model samples a texel that is not fully opaque."""
    texmap = m.get("textures", {})
    for el in m.get("elements", []):
        for fd in el["faces"].values():
            tv = fd["texture"]
            ref = texmap.get(tv[1:], tv) if tv.startswith("#") else tv
            name = ref.split("block/", 1)[1] if ref.startswith("awaken:block/") else None
            if name is None:
                continue
            if name in world.tex:
                frames = world.tex[name] if isinstance(world.tex[name], list) else [world.tex[name]]
            else:                                                  # a kit texture (bloom_*): read the real file
                from PIL import Image
                fp = os.path.join(TEX, name + ".png")
                if not os.path.exists(fp):
                    continue
                im = Image.open(fp).convert("RGBA")
                frames = [_ImgCanvas(im)]
            u0, v0, u1, v1 = fd["uv"]
            xs = sorted((int(math.floor(min(u0, u1) + 1e-6)), int(math.ceil(max(u0, u1) - 1e-6))))
            ys = sorted((int(math.floor(min(v0, v1) + 1e-6)), int(math.ceil(max(v0, v1) - 1e-6))))
            for fr in frames:
                for y in range(max(0, ys[0]), min(16, max(ys[1], ys[0] + 1))):
                    for x in range(max(0, xs[0]), min(16, max(xs[1], xs[0] + 1))):
                        if fr.get(x, y)[3] < 255:
                            return True
    return False


def finish(world, m, ao=None):
    """Mark the model cutout when it needs it (and drop AO on thin things when asked)."""
    if needs_cutout(world, m):
        m = {"parent": m["parent"], "render_type": CUT, **{k: v for k, v in m.items() if k != "parent"}}
    if ao is False:
        m["ambientocclusion"] = False
    return m


# ---------------------------------------------------------------------------------------------------------------------
# painting helpers. A material is a function (canvas, x, y, w, h, face, rng, dims) that fills the w x h rectangle at (x, y)
# as the viewer outside sees the face: columns left to right, rows top to bottom (the top face: x across, z down).


def fillr(c, x, y, w, h, col):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            c.set(xx, yy, col)


def ncol(rng, rp, i, p=0.15, spread=1):
    return noisy(rng, rp, i, p, spread)


def M_planks(rp, board=4, orient="h", nails=False, wear=0.12, joints=True, tone=(2, 3, 3, 3, 4)):
    """Boards laid side by side: a lit top edge, a dark seam under each, butt joints, grain streaks, now and then a nail."""

    def paint(c, x, y, w, h, face, rng, dims):
        horiz = orient == "h"
        along, across = (w, h) if horiz else (h, w)
        nb = max(1, int(round(across / board)))
        edges = [int(round(i * across / nb)) for i in range(nb + 1)]

        def put_(ll, cc, col):
            c.set(x + ll, y + cc, col) if horiz else c.set(x + cc, y + ll, col)
        for bi in range(nb):
            c0, c1 = edges[bi], edges[bi + 1]
            base = rng.choice(tone)
            jx = rng.randrange(3, along - 2) if (joints and along >= 8) else None
            for cc in range(c0, c1):
                for ll in range(along):
                    k = base
                    if nb > 1 and cc == c0:
                        k += 1
                    if nb > 1 and cc == c1 - 1:
                        k = 1
                    elif rng.random() < wear:
                        k += rng.choice([-1, 1])
                    if ll == 0 and nb > 0 and k > 1:
                        k += 1
                    put_(ll, cc, step(rp, k))
            for _ in range(1 + (c1 - c0) // 3):
                ls, cs, ln = rng.randrange(0, along), rng.randrange(c0, max(c0 + 1, c1 - 1)), rng.randrange(3, 7)
                for t in range(ln):
                    if ls + t < along and rng.random() < 0.85:
                        put_(ls + t, cs, step(rp, base - 1))
            if jx is not None:
                for cc in range(c0, c1):
                    put_(jx, cc, step(rp, 1))
            if nails and along >= 6 and c1 - c0 >= 2:
                for ll in (1, along - 2):
                    put_(ll, c0 + (c1 - c0) // 2, step(FORGE, 4))
        if face == "D":
            for yy in range(h):
                for xx in range(w):
                    c.set(x + xx, y + yy, mul(c.get(x + xx, y + yy), 0.78))
    return paint


def M_iron(rp=FORGE, hi=3, noise=0.14, lit=True):
    """Black wrought iron: near-flat with a lit top-left edge and rare scale."""

    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 2
                if lit and (yy == 0 or xx == 0):
                    k = hi
                elif lit and (yy == h - 1 or xx == w - 1) and w > 1 and h > 1:
                    k = 1
                if rng.random() < noise:
                    k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, k))
    return paint


def M_solid(rp, k=3, noise=0.12, edge=True):
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                kk = k
                if edge and w > 2 and h > 2:
                    if yy == 0 or xx == 0:
                        kk += 1
                    elif yy == h - 1 or xx == w - 1:
                        kk -= 1
                if rng.random() < noise:
                    kk += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, kk))
    return paint


GENERATORS = []


def gen(fn):
    GENERATORS.append(fn)
    return fn


# @@ GENERATORS BEGIN @@
# ---------------------------------------------------------------------------------------------------------------------
# shared iron: a dark wrought-iron swatch the thin iron parts sample (rods, hooks, rims)

def street_iron():
    rng = rng_for("city_street", "street_iron")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = 2
            if y == 0:
                k = 3
            elif y == 15:
                k = 1
            if x == 0 and y not in (0, 15):
                k = 3
            if rng.random() < 0.18:
                k += rng.choice([-1, 1])
            c.set(x, y, step(FORGE, k))
    for (x, y) in ((3, 5), (11, 9), (7, 12), (13, 3)):      # a few brighter scale highlights
        c.set(x, y, step(FORGE, 4))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# awnings. awning_<colour> is the canvas seen from above (row 0 the front hem, row 15 at the wall), stripes 4px wide and
# symmetric about the block so a row of awnings is one cloth; awning_<colour>_trim holds the scalloped valance (rows 0-3),
# the side strip (4-5) and the front end strip (6-7).

def _striped(x):
    return ((x + 2) // 4) % 2 == 1


def awning_top(colour):
    rp, cp = CANVAS[colour], CREAMCANVAS
    rng = rng_for("city_street", "awning_top", colour)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            P = rp if _striped(x) else cp
            k = 3
            o = (x + 2) % 4
            hi_, lo_ = (0.60, 0.40) if _striped(x) else (0.64, 0.30)
            if n[y][x] > hi_:
                k += 1                      # soft patches of sun and shade, never single dark specks
            elif n[y][x] < lo_:
                k -= 1
            if o == 0:
                k += 1                      # each panel's left seam catches the light
            elif o == 3:
                k -= 1                      # and its right seam falls in shade
            if rng.random() < 0.07:
                k += rng.choice([-1, 1])    # the weave
            if y == 0:
                k -= 2                      # the hem's fold, in shadow
            elif y == 1:
                k += 1                      # its rolled edge, lit
            elif y == 2 and x % 2 == 0:
                k -= 1                      # running stitch
            if y >= 14:
                k -= 1 if y == 14 else 2    # where it tucks under the hood
            c.set(x, y, step(P, k))
    return c


def awning_trim(colour):
    rp, cp = CANVAS[colour], CREAMCANVAS
    rng = rng_for("city_street", "awning_trim", colour)
    c = Canvas()
    for y in range(4):                       # the valance, front view, scalloped
        for x in range(16):
            o = (x + 2) % 4
            if y == 3 and o not in (1, 2):
                continue
            P = rp if _striped(x) else cp
            k = (2, 3, 3, 3)[y]
            if o == 0:
                k += 1
            elif o == 3:
                k -= 1
            if y == 2 and o in (0, 3):
                k -= 1                       # the curve of the scallop turns away
            elif y == 3:
                k = 2 if _striped(x) else 3  # the scallop's lower hem
            elif rng.random() < 0.07:
                k += rng.choice([-1, 1])
            c.set(x, y, step(P, k))
    for x in range(16):                      # side strip: the cut edge of the canvas, a stitched hem
        for y in (4, 5):
            k = 4 if y == 4 else 2
            if rng.random() < 0.12:
                k += rng.choice([-1, 1])
            c.set(x, y, step(cp, k))
    for x in range(16):                      # front end strip: stripes again, hem roll above its shadow
        P = rp if _striped(x) else cp
        c.set(x, 6, step(P, 4 if x % 4 else 3))
        c.set(x, 7, step(P, 1 if x % 4 else 2))
    return c


def awning_model(shape, colour):
    drop = {"slope": 7, "upper": 4, "lower": 4, "flat": 0}[shape]
    base = 12 if shape == "lower" else 16
    tex = {"particle": b(f"awning_{colour}"), "top": b(f"awning_{colour}"), "trim": b(f"awning_{colour}_trim"),
           "iron": b("street_iron")}
    els = []
    if shape == "flat":
        els.append(E((0, 14, 0), (16, 16, 16), {"U": ("#top", [0, 0, 16, 16]), "D": ("#top", [0, 16, 16, 0]),
                                                "N": ("#trim", [0, 6, 16, 8]), "E": ("#trim", [0, 4, 16, 6]),
                                                "W": ("#trim", [0, 4, 16, 6])}))
    else:
        th = math.degrees(math.atan2(drop, 16))
        L = math.hypot(16, drop)
        faces = {"U": ("#top", [0, 0, 16, 16]), "D": ("#top", [0, 16, 16, 0]), "E": ("#trim", [0, 4, 16, 6]),
                 "W": ("#trim", [0, 4, 16, 6])}
        if shape == "upper":
            faces["N"] = ("#trim", [0, 6, 16, 8])
        els.append(E((0, base - 2, 16 - L), (16, base, 16), faces, rot=("x", -th, (8, base, 16))))
        if shape in ("slope", "lower"):
            y0 = base - drop - 4
            els.append(E((0, y0, 0), (16, y0 + 4, 1), {"N": ("#trim", [0, 0, 16, 4]), "S": ("#trim", [0, 0, 16, 4])}))
            for x in (2.5, 12.5):
                ang = math.degrees(math.atan2(2, 15))
                Lr = math.hypot(15, 2)
                els.append(E((x, 5.5, 16 - Lr), (x + 1, 6.5, 16),
                             {"U": ("#iron", [2, 0, 3, 15]), "D": ("#iron", [4, 0, 5, 15]), "N": ("#iron", [0, 1, 1, 2]),
                              "E": ("#iron", [0, 2, 15, 3]), "W": ("#iron", [0, 6, 15, 7])},
                             rot=("x", ang, (x + 0.5, 6, 16))))
    return {"parent": PARENT, "ambientocclusion": False, "textures": tex, "elements": els}


@gen
def g_awning(w):
    w.texture("street_iron", street_iron())
    for colour in AWN_COLOURS:
        w.texture(f"awning_{colour}", awning_top(colour))
        w.texture(f"awning_{colour}_trim", awning_trim(colour))
    variants = {}
    for colour in AWN_COLOURS:
        for shape in AWN_SHAPES:
            name = f"awning_{colour}_{shape}"
            w.model(name, finish(w, awning_model(shape, colour)))
            for facing in FACINGS:
                variants[f"colour={colour},facing={facing},shape={shape}"] = rot_variant(name, facing)
    w.state("awning", variants)


# ---------------------------------------------------------------------------------------------------------------------
# a small sprite kit: shaded blobs and ASCII sprites, for icons and goods (light from the top-left)

def blob(c, cx, cy, rx, ry, rp, base=3, spread=1.7, noise=None, rng=None, rim=True):
    """A shaded ellipse (a fruit, a loaf, a jug's belly): lit top-left, a darker rim bottom-right."""
    x0, x1 = int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 1
    y0, y1 = int(math.floor(cy - ry)) - 1, int(math.ceil(cy + ry)) + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = nx * nx + ny * ny
            if d > 1.0:
                continue
            z = math.sqrt(max(0.0, 1 - d))
            lum = -0.62 * nx - 0.78 * ny
            lum = lum * 0.75 + z * 0.35 - 0.12
            k = base + int(round(lum * spread * 1.6))
            if rim and d > 0.5:
                k -= 1
                if (nx + ny) > 0.35:
                    k -= 1
            if noise and rng and rng.random() < noise:
                k += rng.choice([-1, 1])
            c.set(x, y, step(rp, k))


def sprite(c, x0, y0, rows, legend):
    """ASCII rows -> pixels; '.' and ' ' are transparent. legend: char -> colour."""
    w = len(rows[0])
    for j, row in enumerate(rows):
        assert len(row) == w, f"sprite row {j} is {len(row)} wide, expected {w}: {row!r}"
        for i, ch in enumerate(row):
            if ch not in ". ":
                c.set(x0 + i, y0 + j, legend[ch])


def line(c, x0, y0, x1, y1, col):
    dx, dy = abs(x1 - x0), abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx - dy
    while True:
        c.set(x0, y0, col)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x0 += sx
        if e2 < dx:
            err += dx
            y0 += sy


def rgbt(h):
    return rgb(h)


# ---------------------------------------------------------------------------------------------------------------------
# trade icons, 9 wide x 7 tall, 2-4 colours with a highlight and a shade each

def _icon_bread():
    c = Canvas(9, 7)
    L = {"d": rgb("#6a3a16"), "m": rgb("#a8672a"), "l": rgb("#d39646"), "s": rgb("#efd7a0"), "k": rgb("#4a2810")}
    sprite(c, 0, 0, ["..ddddd..",
                     ".dmlllmd.",
                     "dmsmlsmmd",
                     "dmmsmmsmd",
                     "dmmmmmmmd",
                     ".dmmmmmkd",
                     "..kkkkk.."], L)
    return c


def _icon_fish():
    c = Canvas(9, 7)
    L = {"b": rgb("#33465c"), "s": rgb("#8aa0b6"), "l": rgb("#cddae6"), "e": rgb("#0e1216"), "f": rgb("#56708c"),
         "w": rgb("#f0f4f8")}
    sprite(c, 0, 0, ["..bbbb...",
                     ".bllllbf.",
                     "belsssbff",
                     "bsssssfff",
                     ".bwwwsbff",
                     "..bbbb.f.",
                     "........."], L)
    return c


def _icon_produce():
    c = Canvas(9, 7)
    rp = R("#4a0d10", "#7e1a1d", "#b02a2a", "#d04436", "#ec6a52", "#ffb09a")
    blob(c, 3.9, 4.0, 3.4, 3.0, rp, 2)
    blob(c, 5.4, 4.1, 2.9, 2.8, rp, 2)
    put(c, [(4, 1), (4, 0)], rgb("#5a3a1a"))
    put(c, [(5, 0), (6, 0), (6, 1)], rgb("#4f9a3c"))
    c.set(5, 0, rgb("#7cc15c"))
    c.set(3, 3, rp[5])
    return c


def _icon_cloth():
    c = Canvas(9, 7)
    S = R("#2a2c32", "#5a606c", "#8e96a2", "#c4cad2", "#eaeef2")
    Hd = R("#2e1e10", "#5a3c1c", "#8a5e2c")
    for (x, y, k) in ((0, 0, 3), (1, 1, 2), (2, 2, 2), (3, 3, 0), (2, 4, 2), (1, 5, 1), (4, 0, 4), (3, 1, 3), (4, 2, 2), (4, 4, 2), (5, 5, 1)):
        c.set(x, y, S[k] if k else S[0])
    c.set(3, 3, S[0])
    put(c, [(0, 5), (0, 6), (1, 6), (5, 6), (6, 6), (6, 5)], Hd[2])
    put(c, [(1, 5), (5, 5)], Hd[1])
    stack = [(R("#4a1410", "#7e2a24", "#b04030", "#d2604a"), 3), (R("#6e6450", "#a49a80", "#d0c6aa", "#ece4cc"), 3),
             (R("#14264a", "#244074", "#3a5ea0", "#5a82c4"), 3), (R("#4a1410", "#7e2a24", "#b04030", "#d2604a"), 2)]
    for j, (rp, k) in enumerate(stack):
        for x in range(5, 9):
            c.set(x, 2 + j, rp[3 if x == 5 else k])
        c.set(8, 2 + j, rp[1])
    for x in range(5, 9):
        c.set(x, 6, stack[3][0][0])
    return c


def _icon_pottery():
    c = Canvas(9, 7)
    rp = R("#3e1c10", "#6e3220", "#a2502e", "#c4703e", "#df9358", "#f2bd88")
    blob(c, 4.0, 4.3, 3.1, 2.6, rp, 3)
    for x in (3, 4, 5):
        c.set(x, 1, rp[2]); c.set(x, 0, rp[3] if x < 5 else rp[2])
    put(c, [(2, 0), (6, 0)], rp[4]); c.set(4, 0, rp[1])
    put(c, [(7, 3), (8, 3), (8, 4), (7, 5)], rp[2])      # the handle
    c.set(2, 4, rp[5]); c.set(3, 3, rp[5])
    put(c, [(2, 6), (3, 6), (4, 6), (5, 6)], rp[1])
    return c


def _icon_candles():
    c = Canvas(9, 7)
    cr = R("#7a705a", "#a89c80", "#d0c4a6", "#ece2c6", "#faf3dc")
    br = R("#3b2a12", "#6e4f1c", "#a07a2c", "#d0a844")
    for y in range(3, 6):
        c.set(3, y, cr[4 if y == 3 else 3]); c.set(4, y, cr[3]); c.set(5, y, cr[1 if y > 3 else 2])
    c.set(4, 2, rgb("#2a2018"))
    put(c, [(4, 1), (4, 0), (3, 1), (5, 1)], rgb("#f6c236"))
    c.set(4, 1, rgb("#fff4b0"))
    c.set(4, 0, rgb("#f09a24"))
    for x in range(1, 8):
        c.set(x, 6, br[3 if x < 4 else 2])
    put(c, [(0, 6), (8, 6)], br[1])
    for x in range(2, 7):
        c.set(x, 5 if x in (2, 6) else 5, c.get(x, 5) if c.get(x, 5)[3] else br[2])
    return c


def _icon_flowers():
    c = Canvas(9, 7)
    G = [rgb("#1c4a24"), rgb("#2f7a34"), rgb("#4fa046")]
    for (x0, x1) in ((2, 4), (4, 4), (6, 4)):
        for y in range(3, 6):
            c.set(x0 if y == 3 else (3 if x0 == 2 else 5 if x0 == 6 else 4), y, G[1])
    for x in (3, 4, 5):
        c.set(x, 5, G[1])
    put(c, [(2, 3), (3, 4), (4, 4), (4, 3), (5, 4), (6, 3)], G[1])
    rib = rgb("#c43a3a")
    put(c, [(3, 6), (4, 6), (5, 6)], rib)
    for (cx, cy, rp) in ((2, 1, R("#8a2a58", "#c44a86", "#ec7db0", "#ffb4d2")), (6, 1, R("#a8220e", "#d44a1c", "#f08a2c", "#ffc860")),
                         (4, 1, R("#9a1c20", "#cf2c30", "#ec5448", "#ff9a86"))):
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            c.set(cx + dx, cy + dy, rp[1])
        c.set(cx, cy, rp[3] if cx != 4 else rp[3])
        c.set(cx - 1, cy - 1, rp[2]) if cy - 1 >= 0 and cx - 1 >= 0 else None
    c.set(4, 2, rgb("#2f7a34")); c.set(2, 2, rgb("#2f7a34")); c.set(6, 2, rgb("#2f7a34"))
    return c


def _icon_spice():
    c = Canvas(9, 7)
    M = R("#34383e", "#555b64", "#7d848e", "#a7aeb8", "#d2d8df")
    sprite(c, 0, 0, ["........h",
                     ".......h.",
                     "..mmmm.h.",
                     ".mMMMMmh.",
                     ".mmmmmm..",
                     "..mmmm...",
                     "...ss...."],
           {"m": M[2], "M": M[4], "s": M[1], "h": rgb("#b08a4a")})
    for x in range(2, 6):
        c.set(x, 3, rgb("#7a3a1a") if x in (3, 4) else M[3])
    put(c, [(2, 4), (3, 4)], M[3])
    return c


def _icon_tools():
    c = Canvas(9, 7)
    Hd = R("#2a2c32", "#4a4e58", "#7a808c", "#a8aeba", "#d4d9e0")
    Wd = R("#3a2814", "#5e4222", "#8a6234", "#aa804a")
    for i in range(7):                      # a hammer, handle lower-left to head upper-right
        c.set(i + 1, 6 - i if 6 - i >= 0 else 0, Wd[2 if i % 2 else 3])
    c.set(0, 6, Wd[1])
    put(c, [(5, 0), (6, 0), (7, 0), (8, 0), (6, 1), (7, 1), (8, 1)], Hd[3])
    put(c, [(5, 1), (4, 0)], Hd[2]); c.set(8, 1, Hd[1]); c.set(7, 0, Hd[4])
    for (x, y) in ((1, 0), (2, 1), (3, 2), (5, 4), (6, 5)):
        c.set(x, y, Hd[2])
    put(c, [(0, 0), (1, 1), (7, 6), (8, 6)], Hd[1])
    c.set(4, 3, Hd[4])
    return c


def _icon_cheese():
    c = Canvas(9, 7)
    Y = R("#7a5410", "#b08418", "#dcab2a", "#f2cb48", "#ffe586")
    sprite(c, 0, 0, ["......ww.",
                     "....wwyyw",
                     "..wwyyyyw",
                     ".wyyyyyyw",
                     "wyyyyyyyd",
                     "wddddddd.",
                     "........."],
           {"w": Y[4], "y": Y[3], "d": Y[1]})
    c.set(8, 4, Y[2]); put(c, [(5, 3), (4, 4)], Y[1]); c.set(3, 3, Y[2]); put(c, [(6, 2)], Y[1])
    put(c, [(5, 5), (6, 5), (7, 5)], Y[2]); put(c, [(1, 5), (2, 5), (3, 5), (4, 5)], Y[1])
    return c


def _icon_tavern():
    c = Canvas(9, 7)
    W = R("#4a3016", "#6e4a22", "#946636", "#b8884a", "#d4a868")
    F = rgb("#f6f1e2"), rgb("#d9d2bd")
    for y in range(2, 7):
        for x in range(1, 6):
            c.set(x, y, W[3] if x < 3 else W[2] if x < 5 else W[1])
    for x in range(1, 6):
        c.set(x, 4, W[1])
    put(c, [(6, 3), (7, 3), (7, 4), (7, 5), (6, 5)], W[2])
    put(c, [(0, 2), (1, 1), (2, 1), (3, 0), (4, 1), (5, 0), (6, 1), (5, 2), (1, 2)], F[0])
    put(c, [(2, 2), (3, 2), (4, 2), (5, 2)], F[0]); c.set(4, 0, F[0]); c.set(2, 0, F[1]); c.set(6, 2, F[1])
    c.set(1, 3, W[4]); c.set(1, 2, F[1])
    return c


def _icon_boots():
    c = Canvas(9, 7)
    Lh = R("#2c1a0e", "#4a2e18", "#704826", "#98663a", "#bc8a56")
    sprite(c, 0, 0, ["..bbb....",
                     "..bmb....",
                     "..bmb....",
                     "..bmbb...",
                     "..bmmmbb.",
                     ".bmmmmmmb",
                     ".bddddddb"],
           {"b": Lh[1], "m": Lh[3], "d": Lh[0]})
    put(c, [(3, 1), (3, 2), (3, 3)], Lh[4]); put(c, [(4, 5), (5, 5)], Lh[4]); put(c, [(2, 0), (3, 0), (4, 0)], Lh[2])
    put(c, [(2, 6), (3, 6)], Lh[1])
    return c


def _icon_books():
    c = Canvas(9, 7)
    Rd = R("#3a0e10", "#681a1c", "#98282a", "#b83c38")
    Pg = rgb("#eee6cc"), rgb("#bdb396")
    for y in range(0, 7):
        for x in range(1, 8):
            c.set(x, y, Rd[2] if x > 1 else Rd[3])
    for y in range(1, 6):
        c.set(8, y, Pg[0] if y % 2 else Pg[1])
    for x in range(1, 8):
        c.set(x, 6, Rd[0])
    for y in range(0, 6):
        c.set(1, y, Rd[3])
        c.set(2, y, Rd[1])
    for y in (1, 4):
        for x in range(3, 8):
            c.set(x, y, GILT[4 if x < 5 else 3])
    put(c, [(4, 2), (5, 2), (4, 3), (5, 3)], GILT[2])
    return c


def _icon_keys():
    c = Canvas(9, 7)
    K = R("#3d2b0c", "#7a5a1c", "#b88c30", "#e0b84a", "#f8e290")
    sprite(c, 0, 1, [".kkk.....",
                     "k...k....",
                     "k...kkkkk",
                     "k...k.k.k",
                     ".kkk..k.."],
           {"k": K[3]})
    put(c, [(1, 1), (2, 1), (3, 1), (0, 2), (0, 3)], K[4])
    put(c, [(1, 5), (2, 5), (3, 5), (4, 4), (4, 3)], K[2])
    put(c, [(8, 3), (6, 5), (8, 4)], K[1])
    put(c, [(5, 3), (6, 3), (7, 3)], K[4])
    return c


ICONS = {"bread": _icon_bread, "fish": _icon_fish, "produce": _icon_produce, "cloth": _icon_cloth, "pottery": _icon_pottery,
         "candles": _icon_candles, "flowers": _icon_flowers, "spice": _icon_spice, "tools": _icon_tools,
         "cheese": _icon_cheese, "tavern": _icon_tavern, "boots": _icon_boots, "books": _icon_books, "keys": _icon_keys}

# board paint per trade (the sign's ground), contrasting with its icon
BOARDS = {
    "bread": ("oxblood", (96, 34, 34)), "fish": ("navy", (34, 50, 92)), "produce": ("green", (36, 72, 50)),
    "cloth": ("ochre", (168, 122, 44)), "pottery": ("green", (36, 72, 50)), "candles": ("black", (30, 30, 36)),
    "flowers": ("navy", (34, 50, 92)), "spice": ("oxblood", (96, 34, 34)), "tools": ("black", (30, 30, 36)),
    "cheese": ("navy", (34, 50, 92)), "tavern": ("green", (36, 72, 50)), "boots": ("navy", (34, 50, 92)),
    "books": ("green", (36, 72, 50)), "keys": ("black", (30, 30, 36)),
}


def _paint_ramp(rgbv):
    return cloth_ramp_fine(rgbv, cool=0.02, warm=4)


def sign_hang_texture(trade):
    """12x9 painted board at (0,0): a gilt frame, a recessed panel, the icon; edge strips at x 12..14."""
    pr = _paint_ramp(BOARDS[trade][1])
    rng = rng_for("city_street", "sign_hang", trade)
    c = Canvas()
    for y in range(9):
        for x in range(12):
            k = 3
            if x == 1 or y == 1:
                k = 2                       # the panel's shadowed top and left, under the raised frame
            elif x == 10 or y == 7:
                k = 4
            elif rng.random() < 0.10:
                k += rng.choice([-1, 1])
            c.set(x, y, step(pr, k))
    for x in range(12):
        c.set(x, 0, step(GILT, 4 if x % 5 else 5))
        c.set(x, 8, step(GILT, 2))
    for y in range(9):
        c.set(0, y, step(GILT, 4 if y % 4 else 5))
        c.set(11, y, step(GILT, 2))
    for (x, y) in ((0, 0), (11, 0), (0, 8), (11, 8)):
        c.set(x, y, step(GILT, 5 if y == 0 else 1))
    ic = ICONS[trade]()
    ox, oy = 1 + (10 - 9 + 1) // 2, 1
    for y in range(7):
        for x in range(9):
            p = ic.get(x, y)
            if p[3]:
                c.set(ox + x, oy + y, p)
    for y in range(9):                       # edge strips: gilt at the frame (x 12 = N/S ends, x 13/14 = top/bottom)
        c.set(12, y, step(GILT, 3))
    for y in range(12):
        c.set(13, y, step(GILT, 4))
        c.set(14, y, step(GILT, 1))
    return c


def sign_flat_texture(trade):
    """16x8 fascia board at (0,0): gilt end bars with a curl, the icon in the middle; rows 8-9 the top/bottom edges."""
    pr = _paint_ramp(BOARDS[trade][1])
    rng = rng_for("city_street", "sign_flat", trade)
    c = Canvas()
    for y in range(8):
        for x in range(16):
            k = 3
            if y == 0:
                k = 4
            elif y == 7:
                k = 1
            elif rng.random() < 0.10:
                k += rng.choice([-1, 1])
            c.set(x, y, step(pr, k))
    for y in range(8):                       # the end bars and their scrolls
        c.set(0, y, step(GILT, 4 if y % 4 else 5))
        c.set(1, y, step(GILT, 2))
        c.set(15, y, step(GILT, 2))
        c.set(14, y, step(GILT, 4 if y % 4 else 5))
    for (x, y) in ((2, 3), (2, 4), (3, 4), (13, 3), (13, 4), (12, 3)):
        c.set(x, y, step(GILT, 3))
    for x in range(4, 12):                   # a thin gilt line each side of the icon
        pass
    ic = ICONS[trade]()
    ox, oy = 4 + (8 - 9 + 1) // 2 + 0, 0
    for y in range(7):
        for x in range(9):
            p = ic.get(x, y)
            if p[3]:
                c.set(ox + x + 0, oy + y, p)
    for x in range(16):
        c.set(x, 8, step(pr, 1))
        c.set(x, 9, step(pr, 2))
    return c


def street_chain():
    """A 1px chain: column 0 the links face-on, column 1 edge-on."""
    c = Canvas()
    for y in range(16):
        c.set(0, y, step(STEEL6, (4, 3, 1, 2)[y % 4]))
        c.set(1, y, step(STEEL6, (2, 1, 3, 1)[y % 4]))
    return c


def bracket_scroll():
    """The brace under the arm, seen from the east (the wall at the left): 10x9 at (0,0) - a curved strut from the wall's foot up to
    the bar, with a small curl in its crook; the end's finial curl 5x6 at (0,10)."""
    I = FORGE
    c = Canvas()
    pts = []
    for i in range(0, 41):
        t = i / 40.0 * math.pi / 2
        pts.append((int(round(9.4 * math.sin(t))), int(round(8.4 * math.cos(t)))))
    for (x, y) in pts:
        c.set(x, y, step(I, 4 if (x + y) % 3 == 0 else 3))
        c.set(x, y + 1, step(I, 3))
    for (x, y) in pts:
        if c.get(x + 1, y + 1)[3] == 0:
            c.set(x + 1, y + 1, step(I, 2))
    for (x, y) in ((1, 0), (2, 0), (0, 1), (0, 2)):                      # the strut's feet into the wall plate and the bar
        c.set(x, y, step(I, 3))
    for (x, y) in ((2, 5), (3, 4), (3, 5), (4, 4)):                      # a little curl in the crook
        c.set(x, y, step(I, 4 if x + y < 8 else 2))
    curl = [".###.",
            "#...#",
            "#.#.#",
            "#..##",
            ".###.",
            "....."]
    for j, row in enumerate(curl):
        for i, ch in enumerate(row):
            if ch == "#":
                c.set(i, 10 + j, step(I, 4 if (i + j) < 4 else 3))
    return c


def sign_bracket_models():
    arm_tex = {"particle": b("street_iron"), "iron": b("street_iron"), "scroll": b("sign_bracket_scroll"), "chain": b("street_chain")}
    I = ("#iron")

    def bar(z0, z1):
        n = z1 - z0
        return E((7, 13, z0), (9, 15, z1), {"U": ("#iron", [4, 0, 6, n]), "D": ("#iron", [6, 0, 8, n]),
                                            "E": ("#iron", [0, 4, n, 6]), "W": ("#iron", [0, 8, n, 10]),
                                            "N": ("#iron", [4, 4, 6, 6])})
    plate = E((5, 10, 15), (11, 16, 16), {"N": ("#iron", [2, 2, 8, 8]), "E": ("#iron", [0, 0, 1, 6]), "W": ("#iron", [1, 0, 2, 6]),
                                          "U": ("#iron", [0, 0, 6, 1]), "D": ("#iron", [0, 1, 6, 2])})
    rivets = [E((6, 11, 14.8), (7, 12, 15), {"N": ("#iron", [11, 5, 12, 6])}), E((9, 14, 14.8), (10, 15, 15), {"N": ("#iron", [11, 5, 12, 6])})]
    brace = E((8, 4, 6), (8, 13, 16), {"E": ("#scroll", [0, 0, 10, 9]), "W": ("#scroll", [10, 0, 0, 9])})
    arm = {"parent": PARENT, "render_type": CUT, "ambientocclusion": False, "textures": arm_tex,
           "elements": [bar(0, 16), plate, brace]}
    # the end: the bar stops at z 3 in a curl and a ball; two chains drop from it to the cell floor (the sign's chains meet them)
    ball = E((6.5, 12.5, 1.5), (9.5, 15.5, 3.5), {"N": ("#iron", [2, 2, 5, 5]), "E": ("#iron", [2, 2, 4, 5]), "W": ("#iron", [2, 6, 4, 9]),
                                                   "U": ("#iron", [4, 4, 7, 6]), "D": ("#iron", [8, 4, 11, 6])})
    curl = E((8, 9, 0), (8, 15, 5), {"E": ("#scroll", [0, 10, 5, 16]), "W": ("#scroll", [5, 10, 0, 16])})
    hooks = []
    for zc in (4.5, 11.5):
        hooks.append(E((7.5, 0, zc - 0.5), (8.5, 13, zc + 0.5), {"N": ("#chain", [0, 0, 1, 13]), "S": ("#chain", [0, 0, 1, 13]),
                                                              "E": ("#chain", [1, 0, 2, 13]), "W": ("#chain", [1, 0, 2, 13])}))
        hooks.append(E((7, 12, zc - 1), (9, 13, zc + 1), {"N": ("#iron", [1, 0, 3, 1]), "S": ("#iron", [1, 0, 3, 1]),
                                                       "E": ("#iron", [1, 0, 3, 1]), "W": ("#iron", [1, 0, 3, 1]),
                                                       "U": ("#iron", [2, 2, 4, 4])}))
    end = {"parent": PARENT, "render_type": CUT, "ambientocclusion": False, "textures": arm_tex,
           "elements": [bar(3, 16), ball, curl] + hooks}
    return arm, end


def signboard_models(trade):
    t = {"particle": b(f"sign_{trade}_hang"), "h": b(f"sign_{trade}_hang"), "f": b(f"sign_{trade}_flat"),
         "chain": b("street_chain")}
    hang = [E((7.5, 2, 2), (8.5, 11, 14), {"E": ("#h", [0, 0, 12, 9]), "W": ("#h", [12, 0, 0, 9]),
                                          "N": ("#h", [12, 0, 13, 9]), "S": ("#h", [12, 0, 13, 9])})]
    hang[0]["faces"]["up"] = {"uv": [13, 0, 14, 12], "texture": "#h"}
    hang[0]["faces"]["down"] = {"uv": [14, 0, 15, 12], "texture": "#h"}
    for zc in (4.5, 11.5):
        hang.append(E((7.5, 11, zc - 0.5), (8.5, 16, zc + 0.5), {"N": ("#chain", [0, 0, 1, 5]), "S": ("#chain", [0, 0, 1, 5]),
                                                              "E": ("#chain", [1, 0, 2, 5]), "W": ("#chain", [1, 0, 2, 5])}))
    flat = [E((0, 4, 15), (16, 12, 16), {"N": ("#f", [0, 0, 16, 8]), "E": ("#f", [15, 0, 16, 8]), "W": ("#f", [0, 0, 1, 8]),
                                        "U": ("#f", [0, 8, 16, 9]), "D": ("#f", [0, 9, 16, 10])})]
    return ({"parent": PARENT, "ambientocclusion": False, "textures": t, "elements": hang},
            {"parent": PARENT, "ambientocclusion": False, "textures": t, "elements": flat})


@gen
def g_signs(w):
    w.texture("street_chain", street_chain())
    w.texture("sign_bracket_scroll", bracket_scroll())
    arm, end = sign_bracket_models()
    w.model("sign_bracket_arm", finish(w, arm))
    w.model("sign_bracket_end", finish(w, end))
    w.state("sign_bracket", {f"facing={f},part={p}": rot_variant(f"sign_bracket_{p}", f) for f in FACINGS for p in ("arm", "end")})
    variants = {}
    for trade in TRADES:
        w.texture(f"sign_{trade}_hang", sign_hang_texture(trade))
        w.texture(f"sign_{trade}_flat", sign_flat_texture(trade))
        h, f = signboard_models(trade)
        w.model(f"signboard_{trade}_hanging", finish(w, h))
        w.model(f"signboard_{trade}_flat", finish(w, f))
        for facing in FACINGS:
            variants[f"facing={facing},mount=hanging,trade={trade}"] = rot_variant(f"signboard_{trade}_hanging", facing)
            variants[f"facing={facing},mount=flat,trade={trade}"] = rot_variant(f"signboard_{trade}_flat", facing)
    w.state("signboard", variants)


# ---------------------------------------------------------------------------------------------------------------------
# hang lines: a hemp rope along the axis, and on it nothing / laundry / bunting / lanterns. The cloth is a 16x16 plane in the
# middle of the cell, animated (8 frames, a breeze running along the line; each set starts its cycle at another frame).

def hang_rope():
    """Twisted hemp laid along x. Row 0 the lit top, row 1 the front (N/S), row 2 the shaded underside, row 3 the cut end."""
    rng = rng_for("city_street", "hang_rope")
    c = Canvas()
    for x in range(16):
        twist = (x // 2) % 2
        c.set(x, 0, step(HEMP, 4 if twist == 0 else 3))
        c.set(x, 1, step(HEMP, 3 if twist == 0 else 2))
        c.set(x, 2, step(HEMP, 2 if twist == 0 else 1))
        if rng.random() < 0.12:
            c.set(x, 1, step(HEMP, 4))
    c.set(0, 3, step(HEMP, 2)); c.set(1, 3, step(HEMP, 3))
    return c


PEG = R("#3a2a18", "#5c4426", "#7e6038", "#a07c4a", "#b8955e")

# garment sprites, drawn from texture row 1 (row 0 is the rope's own row). b body, s sleeve, c neck (shade), t trim,
# h hem, x a button/pocket, f fringe. 'pegs' are x offsets of the wooden clips (drawn over the top two rows); 'folds' are
# the sprite columns where the cloth creases (they drift a pixel either way through the cycle).
GARMENTS = {
    "shirt": dict(pegs=[1, 6], folds=[3, 5], rows=[
        "bbb..bbb",
        "bbbccbbb",
        "bbbbbbbb",
        "hbbbbbbh",
        ".bbbbbb.",
        ".bbbbbb.",
        ".bbbbbb.",
        ".bbbbbb.",
        ".hhhhhh."]),
    "apron": dict(pegs=[1, 4], folds=[2, 4], rows=[
        ".b..b.",
        ".bbbb.",
        ".bbbb.",
        "tttttt",
        "bbbbbb",
        "bbbbbb",
        "bxxxbb",
        "bxxxbb",
        "bbbbbb",
        "hhhhhh"]),
    "stockings": dict(pegs=[1, 5], folds=[], rows=[
        "ttt.ttt",
        "ttt.ttt",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbbbbbb",
        "xbbb.xb"]),
    "towel": dict(pegs=[1, 4], folds=[3], rows=[
        "bbbbbb",
        "bbbbbb",
        "tttttt",
        "bbbbbb",
        "bbbbbb",
        "tttttt",
        "bbbbbb",
        "f.f.f."]),
    "sheet": dict(pegs=[2, 11], folds=[4, 8, 11], rows=
        ["bbbbbbbbbbbbbb"] * 6 + ["tttttttttttttt"] * 2 + ["bbbbbbbbbbbbbb", "hhhhhhhhhhhhhh"]),
    "dress": dict(pegs=[1, 5], folds=[2, 5], rows=[
        ".b...b.",
        ".bbbbb.",
        ".ttttt.",
        "..bbb..",
        ".bbbbb.",
        ".bbbbb.",
        "bbbbbbb",
        "bbbbbbb",
        "hhhhhhh"]),
    "trousers": dict(pegs=[1, 5], folds=[1, 5], rows=[
        "ttttttt",
        "bbbbbbb",
        "bbbbbbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "bbb.bbb",
        "hhh.hhh"]),
}


def draw_garment(c, kind, x0, ramps, phase, tag):
    """Draw one garment on c at column x0 for the cycle phase (radians). ramps: main, trim (colour ramps). Only the lower rows
    swing (a pixel either way), the folds drift with them, the top stays pinned under its pegs."""
    g = GARMENTS[kind]
    rows = g["rows"]
    h = len(rows)
    main, trim = ramps
    rng = rng_for("city_street", "garment", tag, kind)
    shifts = [int(round(1.45 * (j / max(1, h - 1)) ** 2 * math.sin(phase))) for j in range(h)]
    solid = {}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch not in ". ":
                solid[(i + shifts[j], j)] = (ch, i)
    for (xi, j), (ch, i) in solid.items():
        k = 3
        if (xi - 1, j) not in solid:
            k += 1
        if (xi + 1, j) not in solid:
            k -= 1
        if (xi, j + 1) not in solid and ch != "f":
            k -= 1
        if j >= 3:
            for fx in g["folds"]:
                fp = fx + int(round(math.sin(phase + 0.3 * j) * 0.9))
                if i == fp:
                    k -= 1
                elif i == fp - 1:
                    k += 1
        if rng.random() < 0.04:
            k += rng.choice([-1, 1])
        rp = main
        if ch == "t":
            rp = trim
        elif ch == "h":
            rp = trim if kind in ("dress", "towel") else main
            k -= 1
        elif ch == "c":
            k -= 2
        elif ch == "x":
            k += 1 if kind in ("apron", "shirt") else -1
            rp = trim if kind == "apron" else main
        elif ch == "f":
            rp, k = trim, 3
        c.set(x0 + xi, 1 + j, step(rp, k))
    for px in g["pegs"]:                      # wooden pegs clipped over the top edge
        c.set(x0 + px, 1, step(PEG, 4))
        c.set(x0 + px, 2, step(PEG, 2))
        c.set(x0 + px + 1, 1, step(PEG, 3))


# linen whites, faded blues, ochres, a faded red, a dark trouser brown
LINENR = R("#948a72", "#b8ae94", "#d8cfb4", "#ece5cd", "#f8f3e2", "#fffdf0")
BLUEF = cloth_ramp_fine((88, 118, 160), cool=0.05)
BLUEDK = cloth_ramp_fine((58, 78, 124), cool=0.05)
OCHRE = cloth_ramp_fine((200, 152, 70), cool=0.05)
REDF = cloth_ramp_fine((172, 84, 72), cool=0.05)
BROWNF = cloth_ramp_fine((120, 98, 76), cool=0.05)
PINKF = cloth_ramp_fine((214, 150, 160), cool=0.05)
TEALF = cloth_ramp_fine((70, 140, 138), cool=0.05)
GREYF = cloth_ramp_fine((138, 138, 142), cool=0.05)

LAUNDRY_SETS = [
    [("shirt", 0, (LINENR, BLUEF)), ("apron", 9, (BLUEF, LINENR))],
    [("sheet", 1, (LINENR, BLUEF))],
    [("dress", 0, (OCHRE, LINENR)), ("towel", 9, (LINENR, OCHRE))],
    [("trousers", 0, (BROWNF, BROWNF)), ("shirt", 8, (REDF, LINENR))],
]
WASHERS = [("shirt", 4, (LINENR, LINENR)), ("sheet", 1, (LINENR, BLUEF)), ("apron", 5, (REDF, LINENR)),
           ("stockings", 4, (GREYF, REDF)), ("towel", 5, (LINENR, TEALF)), ("dress", 4, (PINKF, LINENR))]
FRAMES = 8


def laundry_frames(garments, set_no, tag):
    out = []
    for f in range(FRAMES):
        c = Canvas()
        phase = 2 * math.pi * (f + 2 * set_no) / FRAMES
        for (kind, x0, ramps) in garments:
            draw_garment(c, kind, x0, ramps, phase + (0.9 if x0 > 6 else 0.0), tag)
        out.append(c)
    return out


# ----- bunting ---------------------------------------------------------------------------------------------------------

BUNTING_SETS = [
    [(cloth_ramp_fine((184, 56, 48)), cloth_ramp_fine((238, 234, 220))), (cloth_ramp_fine((232, 184, 60)), cloth_ramp_fine((60, 88, 150)))],
    [(cloth_ramp_fine((56, 150, 148)), cloth_ramp_fine((238, 190, 64))), (cloth_ramp_fine((196, 70, 130)), cloth_ramp_fine((232, 128, 50)))],
    [(cloth_ramp_fine((70, 120, 66)), cloth_ramp_fine((238, 232, 214))), (cloth_ramp_fine((200, 152, 66)), cloth_ramp_fine((70, 120, 66)))],
    [(cloth_ramp_fine((40, 56, 98)), cloth_ramp_fine((184, 138, 70))), (cloth_ramp_fine((232, 222, 190)), cloth_ramp_fine((40, 56, 98)))],
]


def bunting_frames(set_no):
    """Two pennants a block, each a 6x6 triangle hung by its top hem; the tips swing a pixel either way in a travelling breeze."""
    out = []
    pairs = BUNTING_SETS[set_no]
    for f in range(FRAMES):
        c = Canvas()
        phase = 2 * math.pi * (f + 2 * set_no) / FRAMES
        for pi, (cx, (body, hem)) in enumerate(zip((4, 12), pairs)):
            ph = phase + 1.1 * pi
            rng = rng_for("city_street", "bunting_px", set_no, pi)
            for j in range(6):
                wd = 6 - j
                sh = int(round(math.sin(ph) * (0, 0, 0.4, 0.8, 1.2, 1.5)[j]))
                left = cx - 3 + (j + 1) // 2 + sh
                for i in range(wd):
                    k = 3
                    rp = hem if j == 0 else body
                    if i == 0:
                        k += 1
                    elif i == wd - 1:
                        k -= 1
                    if j == 0:
                        k = 4 if i < 3 else 3
                    elif j == 1:
                        k -= 1 if i > 1 else 0
                    wave = math.sin(ph + j * 0.8 + i * 0.6)
                    if j >= 2 and wave > 0.75:
                        k += 1
                    elif j >= 2 and wave < -0.75:
                        k -= 1
                    if j >= 4:
                        k -= 1
                    if rng.random() < 0.05:
                        k += rng.choice([-1, 1])
                    c.set(left + i, 1 + j, step(rp, k))
        out.append(c)
    return out


# ----- lanterns --------------------------------------------------------------------------------------------------------
# texture layout (base and glow share it): the 4x5 side at (0,0) for N/S/E/W, the 4x4 bottom at (5,0), the cap's side strip
# 3x1 at (0,6) and its top 3x3 at (4,6)

LANTERNS = {
    0: dict(kind="glass", frame=R("#3b2a12", "#6e4f1c", "#a07a2c", "#c49c3c", "#e4c466"), glassamp=1.0),
    1: dict(kind="paper", paper=cloth_ramp_fine((196, 52, 44)), rib=R("#2a120c", "#4a2418", "#6e3a22"), glassamp=0.95),
    2: dict(kind="paper", paper=cloth_ramp_fine((236, 226, 196)), rib=R("#3a2e1c", "#5c4a2c", "#7e6838"), glassamp=1.0),
    3: dict(kind="glass", frame=FORGE, glassamp=1.0),
}
GLOW = {"amber": ((255, 214, 130), (232, 140, 48)), "red": ((255, 168, 120), (220, 70, 40)), "cream": ((255, 244, 200), (240, 190, 110))}
GLOW_OF = {0: "amber", 1: "red", 2: "cream", 3: "amber"}
FLICKER = [1.0, 0.92, 1.06, 0.89, 1.05, 0.96]
HOT = [(1, 2), (2, 2), (2, 3), (1, 3), (1, 2), (2, 2)]


def _glass_px(kind):
    """Which pixels of the 4x5 side are glass/paper (the rest is frame)."""
    if kind == "glass":
        return {(x, y) for x in (1, 2) for y in (1, 2, 3)}
    return {(x, y) for x in range(4) for y in (1, 2, 3)}


def lantern_base(s):
    d = LANTERNS[s]
    rng = rng_for("city_street", "hang_lantern", s)
    c = Canvas()
    if d["kind"] == "glass":
        fr = d["frame"]
        for y in range(5):
            for x in range(4):
                if (x, y) in _glass_px("glass"):
                    c.set(x, y, mul(rgb("#8a5a1c"), 0.55 + 0.1 * (x == 1)))        # dim amber glass when the glow is off
                else:
                    c.set(x, y, step(fr, 3 if (x == 0 or y == 0) else 1 if (x == 3 or y == 4) else 2))
        for x in range(4):
            for y in range(4):
                c.set(5 + x, y, step(fr, 2) if (x in (0, 3) or y in (0, 3)) else mul(rgb("#8a5a1c"), 0.5))
    else:
        pr, rb = d["paper"], d["rib"]
        for y in range(5):
            for x in range(4):
                if y in (0, 4):
                    c.set(x, y, step(rb, 1 if y == 4 else 2))
                else:
                    k = 3 - (1 if x == 3 else 0) + (1 if x == 0 else 0) - (1 if y == 3 else 0)
                    c.set(x, y, mul(step(pr, k), 0.74))
        for x in range(4):
            for y in range(4):
                c.set(5 + x, y, step(rb, 1) if (x in (0, 3) or y in (0, 3)) else mul(step(pr, 2), 0.6))
    fr = d.get("frame", d.get("rib"))
    for x in range(3):
        c.set(x, 6, step(FORGE, 3 if d["kind"] == "glass" else 2))
    for x in range(3):
        for y in range(3):
            c.set(4 + x, 6 + y, step(FORGE, 3 if (x == 0 or y == 0) else 2))
    c.set(5, 7, step(FORGE, 4))
    return c


def lantern_glow_frames(s):
    d = LANTERNS[s]
    hi, lo = GLOW[GLOW_OF[s]]
    out = []
    for f in range(len(FLICKER)):
        c = Canvas()
        amp = FLICKER[f] * d["glassamp"]
        hx, hy = HOT[f]
        for (x, y) in _glass_px(d["kind"]):
            t = min(1.0, math.hypot(x - 1.5, y - 2.0) / 2.2)
            col = lerp(hi, lo, t * 0.8)
            if (x, y) == (hx, hy):
                col = lerp(col, (255, 252, 220), 0.55)
            c.set(x, y, mul(col, amp))
        for x in range(4):
            for y in range(4):
                if d["kind"] == "paper" or (x in (1, 2) and y in (1, 2)):
                    c.set(5 + x, y, mul(lerp(hi, lo, 0.6), amp * 0.9))
        out.append(c)
    return out


def hang_models(w):
    rope_t = b("hang_rope")
    res = {}
    for sag in range(3):
        y0 = 9 - sag
        rope = E((0, y0, 7.5), (16, y0 + 1, 8.5), {"U": ("#rope", [0, 0, 16, 1]), "N": ("#rope", [0, 1, 16, 2]),
                                                  "S": ("#rope", [0, 1, 16, 2]), "D": ("#rope", [0, 2, 16, 3]),
                                                  "E": ("#rope", [0, 3, 1, 4]), "W": ("#rope", [1, 3, 2, 4])})
        base = {"parent": PARENT, "ambientocclusion": False}
        res[("rope", 0, sag)] = {**base, "textures": {"particle": rope_t, "rope": rope_t}, "elements": [rope]}
        for s in range(4):
            lt = b(f"hang_laundry_{s}")
            res[("laundry", s, sag)] = {**base, "textures": {"particle": lt, "rope": rope_t, "cloth": lt}, "elements": [
                rope, E((0, y0 + 1 - 16, 8), (16, y0 + 1, 8), {"N": ("#cloth", [0, 0, 16, 16]), "S": ("#cloth", [16, 0, 0, 16])})]}
            bt = b(f"hang_bunting_{s}")
            res[("bunting", s, sag)] = {**base, "textures": {"particle": bt, "rope": rope_t, "cloth": bt}, "elements": [
                rope, E((0, y0 + 1 - 16, 8), (16, y0 + 1, 8), {"N": ("#cloth", [0, 0, 16, 16]), "S": ("#cloth", [16, 0, 0, 16])})]}
            ln, gl = b(f"hang_lantern_{s}"), b(f"hang_lantern_{s}_glow")
            top = y0 - 2                      # the body's top, two pixels under the rope
            bot = top - 5
            side = {f_: ("#lan", [0, 0, 4, 5]) for f_ in "NSEW"}
            els = [rope,
                   E((7.75, top, 7.75), (8.25, y0, 8.25), {"N": ("#rope", [0, 1, 1, 3]), "E": ("#rope", [0, 1, 1, 3]),
                                                           "S": ("#rope", [0, 1, 1, 3]), "W": ("#rope", [0, 1, 1, 3])}),
                   E((6.5, top, 6.5), (9.5, top + 1, 9.5), {"N": ("#lan", [0, 6, 3, 7]), "S": ("#lan", [0, 6, 3, 7]),
                                                            "E": ("#lan", [0, 6, 3, 7]), "W": ("#lan", [0, 6, 3, 7]),
                                                            "U": ("#lan", [4, 6, 7, 9])}),
                   E((6, bot, 6), (10, top, 10), {**side, "D": ("#lan", [5, 0, 9, 4])}),
                   E((6, bot, 6), (10, top, 10), {**{f_: ("#glow", [0, 0, 4, 5]) for f_ in "NSEW"}, "D": ("#glow", [5, 0, 9, 4])},
                     emit=15, override="up")]
            res[("lanterns", s, sag)] = {**base, "textures": {"particle": ln, "rope": rope_t, "lan": ln, "glow": gl}, "elements": els}
    return res


@gen
def g_hang(w):
    w.texture("hang_rope", hang_rope())
    for s in range(4):
        w.animated(f"hang_laundry_{s}", laundry_frames(LAUNDRY_SETS[s], s, f"set{s}"), 4)
        w.animated(f"hang_bunting_{s}", bunting_frames(s), 4)
        w.texture(f"hang_lantern_{s}", lantern_base(s))
        w.animated(f"hang_lantern_{s}_glow", lantern_glow_frames(s), 3, True)
    for i, (kind, x0, ramps) in enumerate(WASHERS):
        w.animated(f"laundry_{i + 1}", laundry_frames([(kind, x0, ramps)], i, f"wash{i}"), 4)
    variants = {}
    models = hang_models(w)
    for (hang, s, sag), m in models.items():
        name = f"hang_line_{hang}_{s}_{sag}" if hang != "rope" else f"hang_line_rope_{sag}"
        w.model(name, finish(w, m))
    for axis in ("x", "z"):
        for hang in ("rope", "laundry", "bunting", "lanterns"):
            for s in range(4):
                for sag in range(3):
                    name = f"hang_line_{hang}_{s}_{sag}" if hang != "rope" else f"hang_line_rope_{sag}"
                    v = {"model": b(name)}
                    if axis == "z":
                        v["y"] = 90
                    variants[f"axis={axis},hang={hang},sag={sag},set={s}"] = v
    w.state("hang_line", variants)


# ---------------------------------------------------------------------------------------------------------------------
# banners: a cloth three blocks tall (top over middle over bottom), animated (8 frames, a ripple travelling down the cloth, the
# swallowtail's tails swaying a pixel). Cloth is x 2..13 of each 16x16 frame; the rod, knobs and pole are boxes.

def shaded_sprite(c, x0, y0, rows, legend, ramps):
    """ASCII sprite shaded from the top-left: legend maps a char to a ramp name in ramps; lit left edge, shaded right/bottom."""
    solid = {}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch not in ". ":
                solid[(i, j)] = ch
    for (i, j), ch in solid.items():
        rp = ramps[legend.get(ch, "o")]
        k = 3
        if (i - 1, j) not in solid or (i, j - 1) not in solid:
            k += 1
        if (i + 1, j) not in solid or (i, j + 1) not in solid:
            k -= 1
        if ch == "k":
            k = 1
        elif ch == "w":
            k = 5
        c.set(x0 + i, y0 + j, step(rp, k))


EMBLEMS = {
    "bell": ["...oo...", "..oooo..", ".oooooo.", ".oooooo.", ".oooooo.", "oooooooo", "oooooooo", "........", "...oo..."],
    "lantern": ["...ooo.", "..ooooo", ".ooooooo"[:7], ".okkkko", ".okaako", ".okaako", ".okkkko", ".ooooo.", "..ooo.."][:0] or
               ["..ooo..", ".ooooo.", "ooooooo", "okkkkko", "okaaako", "okaaako", "okkkkko", "ooooooo", "..ooo.."],
    "scales": ["...oo...", "oooooooo", "o.o..o.o", ".o....o.", "ooo..ooo", "........", "...oo...", "...oo...", "..oooo.."],
    "laurel": ["..oooo..", ".oo..oo.", "oo....oo", "o......o", "oo....oo", ".oo..oo.", "..o..o..", "...oo...", "........"],
    "hammer": [".oooooo.", "oooooooo", ".oooooo.", "...hh...", "...hh...", "...hh...", "...hh...", "...hh...", "...hh..."],
    "blossom": ["..oooo..", ".oooooo.", "oooaaooo", "oooaaooo", ".oooooo.", "..oooo..", "...gg...", "..gggg..", "...gg..."],
    "tower": ["oo.oo.oo", "oooooooo", ".oooooo.", ".ookkoo.", ".oooooo.", ".ookkoo.", ".oooooo.", ".oooooo.", "oooooooo"],
    "association": ["..oooo..", ".oooooo.", ".oooooo.", "oooooooo", "...oo...", "........", "........", "........", "........"],
}
EMBLEM_OF = {"bellwatch": "bell", "quay": "lantern", "market": "scales", "terrace": "laurel", "works": "hammer",
             "garden": "blossom", "wallring": "tower", "association": "association"}
# (main cloth, second colour, emblem colour, accent)
DESIGN_COLOURS = {
    "bellwatch": ((82, 104, 140), (226, 218, 190), (238, 232, 210), (226, 218, 190)),
    "quay": ((150, 62, 46), (40, 56, 96), (236, 226, 196), (240, 184, 70)),
    "market": ((226, 166, 44), (178, 50, 42), (178, 50, 42), (178, 50, 42)),
    "terrace": ((234, 230, 216), (52, 92, 162), (52, 92, 162), (52, 92, 162)),
    "works": ((40, 38, 40), (216, 100, 34), (216, 100, 34), (140, 100, 70)),
    "garden": ((66, 120, 68), (210, 112, 134), (226, 140, 160), (240, 214, 120)),
    "wallring": ((128, 130, 134), (160, 78, 42), (160, 78, 42), (160, 78, 42)),
    "association": ((34, 48, 90), (184, 138, 64), (200, 154, 76), (200, 154, 76)),
}


def banner_frames(design, f):
    """Return (top, middle, bottom) canvases of frame f."""
    main_c, sec_c, emb_c, acc_c = DESIGN_COLOURS[design]
    M, S2, EM, AC = cloth_ramp_fine(main_c), cloth_ramp_fine(sec_c, cool=0.03), cloth_ramp_fine(emb_c, cool=0.03), cloth_ramp_fine(acc_c, cool=0.03)
    rng = rng_for("city_street", "banner", design)
    noise = [[rng.random() for _ in range(16)] for _ in range(48)]
    # the 48-row cloth: x 2..13, rows 2..47 (rows 0-1 are the rod)
    cloth = {}                                        # (x, row) -> (ramp, k)
    for g in range(2, 48):
        for x in range(2, 14):
            cloth[(x, g)] = (M, 3)
    # bottom: the swallowtail
    tail_top = 38
    for g in range(tail_top, 48):
        r = g - tail_top
        gap = 1 + int(r * 0.9)                      # the notch widens towards the tips
        for x in range(2, 14):
            if abs(x - 7.5) < gap * 0.5 + 0.2 and r >= 1:
                cloth.pop((x, g), None)
    # trims: gilt lines inset from each side, a band under the rod, a band over the tails
    for g in range(4, 47):
        if (3, g) in cloth:
            cloth[(3, g)] = (S2, 3)
        if (12, g) in cloth:
            cloth[(12, g)] = (S2, 3)
    for x in range(2, 14):
        for g in (4, 5):
            if (x, g) in cloth:
                cloth[(x, g)] = (S2, 3 if g == 4 else 2)
        for g in (35, 36):
            if (x, g) in cloth:
                cloth[(x, g)] = (S2, 3 if g == 35 else 2)
    # the emblem on the middle: centred at rows 16..31
    em = EMBLEMS[EMBLEM_OF[design]]
    ew, eh = len(em[0]), len(em)
    ox, oy = 8 - ew // 2, 16 + (16 - eh) // 2 + 1
    emblem_px = {}
    tmp = Canvas(16, 48)
    shaded_sprite(tmp, ox, oy, em, {"o": "e", "k": "m", "a": "a", "h": "e", "g": "g", "w": "e"},
                  {"e": EM, "m": M, "a": AC, "g": cloth_ramp_fine((70, 130, 70))})
    for y in range(48):
        for x in range(16):
            px = tmp.get(x, y)
            if px[3]:
                emblem_px[(x, y)] = px
    if design == "association":                      # a bell over crossed blades: the lower half drawn as lines
        for (x, y) in list(emblem_px):
            if y >= oy + 5:
                del emblem_px[(x, y)]
        for t in range(0, 4):
            for (x, y, k) in ((4 - t, oy + 5 + t, 4), (4 + t, oy + 5 + t, 4)):
                pass
        line(tmp, 0, 0, 0, 0, (0, 0, 0, 0))
        for (x0, y0, x1, y1) in ((4, oy + 5, 11, oy + 8), (11, oy + 5, 4, oy + 8)):
            n = max(abs(x1 - x0), abs(y1 - y0))
            for t in range(n + 1):
                xx = x0 + (x1 - x0) * t // n
                yy = y0 + (y1 - y0) * t // n
                emblem_px[(xx, yy)] = step(EM, 4 if t % 2 else 3)
        for (x, y) in ((4, oy + 5), (11, oy + 5), (4, oy + 8), (11, oy + 8)):
            emblem_px[(x, y)] = step(AC, 4)
    phase = 2 * math.pi * f / FRAMES
    out = []
    for part in range(3):
        c = Canvas()
        for (x, g), (rp, k) in cloth.items():
            gy = g - 16 * part
            if not (0 <= gy < 16):
                continue
            shx = 0
            if g >= tail_top + 1:
                shx = int(round(math.sin(phase) * min(1.0, (g - tail_top) / 8.0) * 1.2))
            kk = k
            if x in (2, 13):
                kk += 1 if x == 2 else -1
            w = (g - f + x // 3) % 8
            amp = min(1.0, max(0.0, (g - 3) / 10.0))
            if amp > 0.5 and w in (0, 1):
                kk += 1
            elif amp > 0.5 and w in (4, 5):
                kk -= 1
            if noise[g][x] < 0.06:
                kk -= 1
            elif noise[g][x] > 0.94:
                kk += 1
            if g == tail_top - 1:
                kk -= 1
            px = step(rp, kk)
            if (x, g) in emblem_px:
                e = emblem_px[(x, g)]
                px = mul(e, 1.0 + (0.1 if (w in (0, 1) and amp > 0.5) else -0.1 if (w in (4, 5) and amp > 0.5) else 0))
            c.set(x + shx, gy, px)
        if part == 0:                                 # the cloth is looped over the rod: two rows of a darker fold
            for x in range(2, 14):
                c.set(x, 2, mul(c.get(x, 2), 0.82))
        if part == 2:                                 # fringe on the tails' last row
            for x in range(16):
                if c.get(x, 15)[3] and x % 2 == 0:
                    c.set(x, 15, step(S2, 3))
        out.append(c)
    return out


def banner_rod():
    rng = rng_for("city_street", "banner_rod")
    c = Canvas()
    for x in range(16):
        c.set(x, 0, step(DARKOAK, 4 if x % 5 else 3))
        c.set(x, 1, step(DARKOAK, 2))
        c.set(x, 2, step(DARKOAK, 3 if x % 3 else 4))
        c.set(x, 3, step(DARKOAK, 1))
    for y in range(4, 7):
        for x in range(0, 2):
            c.set(x, y, step(DARKOAK, 2))
    for y in range(4, 7):
        for x in range(4, 8):
            c.set(x, y, step(GILT, 3 if (x + y) % 4 else 4))
    c.set(4, 4, step(GILT, 5)); c.set(7, 6, step(GILT, 1))
    return c


def banner_model(design, part, mount):
    t = {"particle": b(f"banner_{design}_{part}"), "cloth": b(f"banner_{design}_{part}"), "rod": b("banner_rod")}
    els = []
    v0 = 2 if part == "top" else 0
    y1 = 14 if part == "top" else 16
    if mount == "wall":
        els.append(E((2, 0 if part != "top" else 0, 15.5), (14, y1, 15.5), {"N": ("#cloth", [2, v0, 14, 16])}))
    else:
        els.append(E((8, 0, 2), (8, y1, 14), {"E": ("#cloth", [2, v0, 14, 16]), "W": ("#cloth", [2, v0, 14, 16])}))
    if part == "top":
        if mount == "wall":
            els.append(E((1, 14, 14), (15, 16, 16), {"N": ("#rod", [0, 0, 14, 2]), "U": ("#rod", [0, 2, 14, 4]), "D": ("#rod", [0, 2, 14, 4]),
                                                     "E": ("#rod", [0, 4, 2, 6]), "W": ("#rod", [0, 4, 2, 6])}))
            for x0 in (0, 15):
                els.append(E((x0, 13.5, 14.5), (x0 + 1, 16.5, 16), {"N": ("#rod", [4, 4, 5, 7]), "E": ("#rod", [4, 4, 6, 7]), "W": ("#rod", [4, 4, 6, 7]),
                                                                    "U": ("#rod", [4, 4, 5, 6])}))
        else:
            els.append(E((7, 14, 1), (9, 16, 15), {"E": ("#rod", [0, 0, 14, 2]), "W": ("#rod", [0, 0, 14, 2]), "U": ("#rod", [0, 2, 14, 4]),
                                                   "D": ("#rod", [0, 2, 14, 4]), "N": ("#rod", [0, 4, 2, 6]), "S": ("#rod", [0, 4, 2, 6])}))
            for z0 in (0, 15):
                els.append(E((6.5, 13.5, z0), (9.5, 16.5, z0 + 1), {"N": ("#rod", [4, 4, 7, 7]), "S": ("#rod", [4, 4, 7, 7]),
                                                                  "E": ("#rod", [4, 4, 5, 7]), "W": ("#rod", [4, 4, 5, 7]), "U": ("#rod", [4, 4, 7, 5])}))
    return {"parent": PARENT, "ambientocclusion": False, "textures": t, "elements": els}


@gen
def g_banners(w):
    w.texture("banner_rod", banner_rod())
    variants = {}
    for design in DESIGNS:
        frames = [banner_frames(design, f) for f in range(FRAMES)]
        for pi, part in enumerate(("top", "middle", "bottom")):
            w.animated(f"banner_{design}_{part}", [fr[pi] for fr in frames], 4)
        for part in ("top", "middle", "bottom"):
            for mount in ("wall", "pole"):
                name = f"banner_cloth_{design}_{part}_{mount}"
                w.model(name, finish(w, banner_model(design, part, mount)))
                for facing in FACINGS:
                    variants[f"design={design},facing={facing},mount={mount},part={part}"] = rot_variant(name, facing)
    w.state("banner_cloth", variants)


# ---------------------------------------------------------------------------------------------------------------------
# flagpole and line pole (boxes painted into their own atlases)

def M_bark(rp, ring=False):
    """Weathered timber with the grain running the long way; lit left, shaded right (a round pole)."""
    def paint(c, x, y, w, h, face, rng, dims):
        vertical = h >= w
        for yy in range(h):
            for xx in range(w):
                u = xx if vertical else yy
                n = w if vertical else h
                k = 3 + (1 if u == 0 else -1 if u == n - 1 else 0)
                if rng.random() < 0.22:
                    k += rng.choice([-1, 1])
                if ring and ((yy % 5 == 4) if vertical else (xx % 5 == 4)):
                    k = 1
                c.set(x + xx, y + yy, step(rp, k))
    return paint


def M_brass(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3 + (1 if (xx == 0 or yy == 0) else -1 if (xx == w - 1 or yy == h - 1) else 0)
            if rng.random() < 0.15:
                k += rng.choice([-1, 1])
            c.set(x + xx, y + yy, step(GILT, k))


def M_ring(c, x, y, w, h, face, rng, dims):
    """A hollow iron ring seen face-on (transparent inside)."""
    for yy in range(h):
        for xx in range(w):
            if xx in (0, w - 1) or yy in (0, h - 1):
                c.set(x + xx, y + yy, step(FORGE, 4 if (xx == 0 or yy == 0) else 2))


@gen
def g_poles(w):
    # flagpole: a dark timber pole from a wall plate rising 20 degrees to a brass finial out over the street
    at = w.atlas("flagpole")
    m = MB(at, "flagpole", ao=False)
    m.box((5, 0, 15), (11, 6, 16), {"*": M_iron(FORGE, 3)}, faces="NEWUD")
    m.box((6.5, 4.5, 14.5), (7.5, 5.5, 15), {"*": M_brass}, faces="N")
    m.box((8.5, 1.5, 14.5), (9.5, 2.5, 15), {"*": M_brass}, faces="N")
    L = math.hypot(25, 9)
    ang = math.degrees(math.atan2(9, 25))
    m.box((7, 2, 15 - L), (9, 4, 15), {"*": M_bark(DARKOAK)}, faces="NEWUD", rot=("x", ang, (8, 3, 15)))
    m.box((6.5, 10.5, -12), (9.5, 13.5, -9), {"*": M_brass}, faces="NESWUD")
    w.model("flagpole", finish(w, m.build(ao=False)))
    w.state("flagpole", {f"facing={f}": rot_variant("flagpole", f) for f in FACINGS})
    # line pole: a round-ish 4x4 timber post, with an iron cap and two hook rings when it ends the run
    for top in (False, True):
        at = w.atlas("line_pole")
        m = MB(at, "line_pole_%s" % top, ao=False)
        H = 12 if top else 16
        bark = {"*": M_bark(WEATHERED, ring=False)}
        m.box((6, 0, 7), (10, H, 9), bark, faces="NSEWD" if not top else "NSEWD")
        m.box((7, 0, 6), (9, H, 10), bark, faces="NSEWD")
        if not top:
            m.box((6, 15.99, 7), (10, 16, 9), bark, faces="U")
        if top:
            iron = {"*": M_iron(FORGE, 4)}
            m.box((5.5, 12, 5.5), (10.5, 13.5, 10.5), iron, faces="NESWUD")
            m.box((6.5, 13.5, 6.5), (9.5, 14.5, 9.5), iron, faces="NESWU")
            for (x0, x1) in ((10.5, 13), (3, 5.5)):
                m.box((x0, 9.5, 7.5), (x1, 12.5, 8.5), {"N": M_ring, "S": M_ring, "*": M_iron(FORGE, 3)}, faces="NSUD")
        w.model("line_pole_top" if top else "line_pole", finish(w, m.build(ao=False)))
    w.state("line_pole", {"top=false": {"model": b("line_pole")}, "top=true": {"model": b("line_pole_top")}})


# ---------------------------------------------------------------------------------------------------------------------
# the goods kit: painters that fill a rectangle with heaped produce (staggered items, shaded blobs, dark gaps between)

RED_APPLE = R("#3c0a0e", "#6e1519", "#a02424", "#c83a30", "#e45a46", "#ffa890")
GREEN_APPLE = R("#1e340e", "#365e18", "#538a28", "#76b238", "#9ed455", "#d0f090")
CABBAGE = R("#14301a", "#25502a", "#3e7a3a", "#5ea04c", "#82c068", "#b0dc94")
PUMPKIN = R("#4e2406", "#7e3c0a", "#b45c16", "#d8801e", "#f0a23c", "#ffd488")
POTATO = R("#30220f", "#54401f", "#7c5e34", "#a28050", "#c4a572", "#e0cc9c")
CARROT = R("#6a2204", "#a2400c", "#d86a1a", "#f08a28", "#ffb04e")
SILVER = R("#34444e", "#5e7684", "#92aab8", "#bfd0dc", "#e6eef4")
BOTG = R("#0e2a18", "#1a4a2a", "#2a6e42", "#46a066", "#7acc96")
BOTB = R("#24160a", "#44290f", "#6e4418", "#9c6a28", "#c89850")
BLUEG = R("#122a4a", "#1e4878", "#2e6aa8", "#4c90d0", "#86bcee")


def scatter(v, w, h, rng, sx, sy, draw, jitter=1, margin=0):
    for ry in range(-1, h // sy + 2):
        y = ry * sy + sy // 2 + margin
        off = (sx // 2) if ry % 2 else 0
        for rx in range(-1, w // sx + 2):
            x = rx * sx + off + sx // 2 + margin
            draw(v, x + (rng.randint(-jitter, jitter) if jitter else 0), y + (rng.randint(-jitter, jitter) if jitter else 0), rng)


def under(rp, k=0):
    def f(v, w, h):
        for yy in range(h):
            for xx in range(w):
                v.set(xx, yy, step(rp, k))
    return f


def draw_apple(v, cx, cy, rng):
    rp = GREEN_APPLE if rng.random() < 0.15 else RED_APPLE
    blob(v, cx, cy, 2.3, 2.1, rp, 3, spread=1.0)
    v.set(cx, cy - 2, step(rp, 0))
    if rng.random() < 0.7:
        v.set(cx - 1, cy - 1, step(rp, 4))


def draw_cabbage(v, cx, cy, rng):
    blob(v, cx, cy, 3.0, 2.8, CABBAGE, 2, spread=1.0)
    for t in range(-1, 3):
        v.set(cx - 1 + t, cy + (t % 2) - 1, step(CABBAGE, 4))
    v.set(cx, cy + 1, step(CABBAGE, 1))
    v.set(cx + 1, cy - 1, step(CABBAGE, 2))


def draw_potato(v, cx, cy, rng):
    blob(v, cx, cy, 2.5, 1.9, POTATO, 3, noise=0.12, rng=rng, spread=1.0)
    if rng.random() < 0.6:
        v.set(cx + rng.choice([-1, 0, 1]), cy, step(POTATO, 1))


def draw_pumpkin(v, cx, cy, rng):
    blob(v, cx, cy, 3.4, 2.7, PUMPKIN, 3, spread=1.0)
    for dx in (-1, 1):
        for dy in range(-1, 2):
            v.set(cx + dx, cy + dy, step(PUMPKIN, 2 if dx < 0 else 1))      # ribs
    v.set(cx, cy - 3, step(R("#2a3a10", "#3e5a1a", "#587e26"), 1))
    v.set(cx, cy - 2, step(R("#2a3a10", "#3e5a1a", "#587e26"), 2))


def draw_bottle_top(v, cx, cy, rng):
    rp = rng.choice([BOTG, BOTG, BOTB])
    blob(v, cx, cy, 1.7, 1.7, rp, 2, spread=0.9)
    v.set(cx, cy, step(R("#4a3018", "#7a5430", "#a07a48"), 2))     # the cork
    v.set(cx - 1, cy - 1, step(rp, 4))


def draw_fish(v, cx, cy, rng):
    sgn = -1 if rng.random() < 0.5 else 1
    for i in range(-3, 3):
        for j in (-1, 0, 1):
            if abs(j) == 1 and i in (-3, 2):
                continue
            x = cx + i * sgn
            k = 2 if j == -1 else 3 if j == 0 else 4
            if j == 0 and i == 0:
                k = 4
            v.set(x, cy + j, step(SILVER, k))
    v.set(cx - 2 * sgn, cy, step(SILVER, 0))                                    # the eye
    v.set(cx + 3 * sgn, cy - 1, step(SILVER, 1))                                # the tail fin
    v.set(cx + 3 * sgn, cy + 1, step(SILVER, 1))
    v.set(cx + 2 * sgn, cy, step(SILVER, 2))


UNDER = {"apples": (RED_APPLE, 0), "cabbages": (CABBAGE, 0), "potatoes": (POTATO, 0), "pumpkins": (PUMPKIN, 0),
         "bottles": (HAY, 1), "fish": (HAY, 1)}
ITEMS = {"apples": (draw_apple, 4, 4), "cabbages": (draw_cabbage, 5, 5), "potatoes": (draw_potato, 4, 3),
         "pumpkins": (draw_pumpkin, 6, 5), "bottles": (draw_bottle_top, 4, 4), "fish": (draw_fish, 7, 4)}


def M_heap(kind):
    draw, sx, sy = ITEMS[kind]
    ur, uk = UNDER[kind]

    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                c.set(x + xx, y + yy, step(ur, uk + (1 if rng.random() < 0.15 else 0)))
        scatter(View(c, x, y, w, h) if not isinstance(c, View) else View(c.c, c.x0 + x, c.y0 + y, w, h), w, h, rng, sx, sy, draw,
                jitter=1 if kind not in ("bottles",) else 0)
    return paint


def M_straw(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            c.set(x + xx, y + yy, step(HAY, rng.choice([2, 3, 3, 4])))
    for _ in range(max(3, w * h // 7)):
        sx_, sy_ = rng.randrange(0, w), rng.randrange(0, h)
        ln = rng.randrange(3, 7)
        d = rng.choice([(1, 0), (1, 1), (1, -1), (2, 1)])
        for t in range(ln):
            px, py = sx_ + t * d[0] // (2 if d[0] == 2 else 1), sy_ + t * d[1] // (2 if d[0] == 2 else 1)
            if 0 <= px < w and 0 <= py < h:
                c.set(x + px, y + py, step(HAY, 5 if t == 0 else 4 if t % 2 else 1))


# ---------------------------------------------------------------------------------------------------------------------
# crates: market crates, lighter than the cargo crate, oak and spruce slats in a dark frame, two cross-nailed battens

def crate_side(variant):
    rp = OAK if variant == 0 else SPRUCE
    fr = SPRUCE_DARK if variant == 0 else DARKOAK
    rng = rng_for("city_street", "crate_side", variant)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c.set(x, y, step(fr, 3 if edge else 2))
    for y in range(1, 15):                      # slats: three boards of four, seams between
        for x in range(1, 15):
            r = (y - 1) % 5
            if r == 4:
                c.set(x, y, step(rp, 1))
                continue
            k = 3 + (1 if r == 0 else 0)
            if rng.random() < 0.18:
                k += rng.choice([-1, 1])
            c.set(x, y, step(rp, k))
    for x in range(1, 15):
        c.set(x, 14, step(fr, 1))
    for y in range(16):                         # the frame's lit top-left edge and shaded bottom-right
        c.set(0, y, step(fr, 4))
        c.set(15, y, step(fr, 1))
    for x in range(16):
        c.set(x, 0, step(fr, 4))
        c.set(x, 15, step(fr, 1))
    for t in range(1, 15):                      # two cross-nailed battens
        for (x, y) in ((t, t), (15 - t, t)):
            c.set(x, y, step(fr, 4))
            for (dx, dy) in ((1, 0),) if x > 7 else ((1, 0),):
                c.set(x + dx, y + dy, step(fr, 2))
        pass
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 7), (8, 8), (8, 7), (7, 8)):
        c.set(x, y, step(IRON, 4))
    return c


def crate_top(content):
    rng = rng_for("city_street", "crate_top", content)
    c = Canvas()
    rim = OAK
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                c.set(x, y, step(rim, 4 if (x == 0 or y == 0) else 2))
    inner = View(c, 1, 1, 14, 14)
    if content == "lid":
        M_planks(OAK, board=4, orient="h", nails=True)(inner, 0, 0, 14, 14, "U", rng, (14, 0, 14))
    elif content == "straw":
        M_straw(inner, 0, 0, 14, 14, "U", rng, (14, 0, 14))
    else:
        M_heap({"apples": "apples", "cabbages": "cabbages", "fish": "fish", "bottles": "bottles"}[content])(inner, 0, 0, 14, 14, "U", rng, (14, 0, 14))
    for y in range(14):                         # the rim casts a little shade on the contents
        inner.set(0, y, mul(inner.get(0, y), 0.82))
    for x in range(1, 15):
        c.set(x, 1, mul(c.get(x, 1), 0.85))
    return c


CONTENTS = ["lid", "apples", "cabbages", "fish", "straw", "bottles"]


@gen
def g_crate(w):
    w.texture("crate_side_0", crate_side(0))
    w.texture("crate_side_1", crate_side(1))
    variants = {}
    for ct in CONTENTS:
        w.texture(f"crate_top_{ct}", crate_top(ct))
        for v in (0, 1):
            t = {"particle": b(f"crate_side_{v}"), "s": b(f"crate_side_{v}"), "t": b(f"crate_top_{ct}")}
            faces = {f_: ("#s", [0, 0, 16, 16], True) for f_ in "NESW"}
            faces["U"] = ("#t", [0, 0, 16, 16], True)
            faces["D"] = ("#s", [0, 0, 16, 16], True)
            w.model(f"crate_{ct}_{v}", {"parent": PARENT, "textures": t, "elements": [E((0, 0, 0), (16, 16, 16), faces)]})
        ents = [{"model": f"crate_{ct}_0", "weight": 3, "y": 0}, {"model": f"crate_{ct}_0", "weight": 2, "y": 90},
                {"model": f"crate_{ct}_1", "weight": 3, "y": 0}, {"model": f"crate_{ct}_1", "weight": 2, "y": 90}]
        variants[f"content={ct}"] = weighted(ents)
    w.state("crate", variants)


# ---------------------------------------------------------------------------------------------------------------------
# more materials: burlap, fleece, crust, cloth layers, clay, wax, blooms, cheese, ice, melon

FLOUR = R("#8e866e", "#b2aa8e", "#d2cab0", "#e8e0c8", "#f4eedc", "#fffcee")
FLEECE = R("#7e7e78", "#a4a49c", "#c6c6be", "#dedcd4", "#eeece6", "#fbfaf6")
CRUST = R("#4e2a0e", "#774618", "#a0652a", "#c4883c", "#e0aa58", "#f2cc84")
CLAY = R("#401c10", "#6e3320", "#a2502e", "#c4703e", "#df9358", "#f2bd88")
ICE = R("#6e8aa0", "#9ab4c8", "#c4d8e6", "#e2eef6", "#f6fbff")
MELON = R("#1c3a14", "#2e5e22", "#4a8a38", "#6cb050", "#94d072")
WAX = R("#8a806a", "#b0a68c", "#d4cab0", "#ece4cc", "#faf4e0")
CHEESE = R("#7a5410", "#b08418", "#d6a428", "#eec440", "#fddf74")


def M_burlap(rp, mark=None, tie=False):
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 3 + (1 if (xx + yy) % 2 == 0 else 0) - (1 if yy % 4 == 3 else 0)
                if xx % 5 == 2 and yy % 3 != 0:
                    k -= 1
                if rng.random() < 0.10:
                    k += rng.choice([-1, 1])
                if xx == 0:
                    k += 1
                elif xx == w - 1:
                    k -= 1
                if face == "U":
                    k += 1
                c.set(x + xx, y + yy, step(rp, k))
        if mark and face in "NESW" and w >= 6 and h >= 5:
            cx, cy = w // 2, h // 2
            for (dx, dy, kk) in ((0, -1, 2), (-1, 0, 2), (1, 0, 2), (0, 1, 2), (0, 0, 4), (-1, -1, 3), (1, 1, 3)):
                c.set(x + cx + dx, y + cy + dy, step(mark, kk))
    return paint


def M_fleece(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3
            tuft = math.sin(xx * 1.7 + yy * 0.9) + math.sin(xx * 0.8 - yy * 1.9)
            if tuft > 1.1:
                k += 1
            elif tuft < -1.1:
                k -= 1
            if rng.random() < 0.12:
                k += rng.choice([-1, 1])
            if xx == w - 1 or yy == h - 1:
                k -= 1
            c.set(x + xx, y + yy, step(FLEECE, k))


def M_fleece_band(c, x, y, w, h, face, rng, dims):
    M_fleece(c, x, y, w, h, face, rng, dims)
    if face in "NESW" and h >= 5:
        for yy in (h // 3, h - 1 - h // 3):
            for xx in range(w):
                c.set(x + xx, y + yy, step(HEMP, 3 if xx % 2 else 2))


def M_crust(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3 + (1 if (xx + yy < 3) else 0) - (1 if (xx == w - 1 or yy == h - 1) else 0)
            if rng.random() < 0.18:
                k += rng.choice([-1, 1])
            c.set(x + xx, y + yy, step(CRUST, k))
    if face == "U" and w >= 3:
        for t in range(0, w, 2):                       # scored top: pale slashes
            c.set(x + t, y + (t // 2) % max(1, h), step(CRUST, 5))
            if h > 2:
                c.set(x + t + 1, y + (t // 2) % max(1, h) + 1, step(CRUST, 4))


def M_layers(rp, stripe=None):
    """Folded bolts seen from the side: horizontal layers every 2px, darker between."""
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 3 + (1 if yy % 2 == 0 else -1)
                if face == "U":
                    k = 3 + (1 if (xx + yy) % 2 == 0 else 0)
                if rng.random() < 0.08:
                    k += rng.choice([-1, 1])
                if xx == 0:
                    k += 1
                c.set(x + xx, y + yy, step(rp, k))
        if stripe and face in "NSEW" and h >= 2:
            for xx in range(w):
                c.set(x + xx, y + h // 2, step(stripe, 3))
    return paint


def M_clay(rp, band=None):
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 3 + (1 if xx == 0 else -1 if xx >= w - 1 else 0)
                if face == "U":
                    k = 3 + (1 if xx + yy < 2 else 0)
                if rng.random() < 0.1:
                    k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, k))
        if band and face in "NESW" and h >= 4:
            for xx in range(w):
                c.set(x + xx, y + 1, step(band, 3))
    return paint


def M_ice(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            c.set(x + xx, y + yy, step(ICE, rng.choice([2, 3, 3, 4, 4])))
    for _ in range(max(1, w * h // 14)):
        c.set(x + rng.randrange(w), y + rng.randrange(h), step(ICE, 1))


def M_melon(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3 if (xx % 3) != 1 else 1
            if rng.random() < 0.1:
                k += rng.choice([-1, 1])
            if yy == 0 and face != "U":
                k += 1
            c.set(x + xx, y + yy, step(MELON, k))


def M_wax(c, x, y, w, h, face, rng, dims):
    """A bundle of candles: pale sticks side by side, a wick on top."""
    for yy in range(h):
        for xx in range(w):
            k = 3 if xx % 2 == 0 else 2
            if face == "U":
                k = 3 + (1 if (xx + yy) % 2 == 0 else 0)
            if rng.random() < 0.07:
                k += rng.choice([-1, 1])
            c.set(x + xx, y + yy, step(WAX, k))
    if face == "U":
        c.set(x + w // 2, y + h // 2, rgb("#3a2a1a"))
    else:
        c.set(x + w // 2, y, rgb("#6a4a2a"))


BLOOM_COLS = [R("#8a2a58", "#c44a86", "#ec7db0", "#ffb4d2"), R("#a8420e", "#e0861a", "#f6b430", "#ffe27a"),
              R("#7a0c14", "#b81c24", "#e0403c", "#ff8a78"), R("#1c2f7a", "#3a5cc0", "#6a8cf0", "#b4c8ff"),
              R("#a0a2ac", "#d4d6dc", "#f2f2f4", "#ffffff")]


def M_bloomheap_of(main, second):
    """A heap of cut blooms: mostly one colour in 2x2 blossoms with a few of a second, green showing between."""
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                c.set(x + xx, y + yy, step(LEAFG, rng.choice([1, 2, 2, 3])))
        for ry in range(0, h, 2):
            for rx in range(0, w, 2):
                if rng.random() < 0.12:
                    continue
                cp = BLOOM_COLS[main if rng.random() < 0.75 else second]
                ox = rx + (1 if (ry // 2) % 2 else 0)
                c.set(x + ox, y + ry, cp[3])
                c.set(x + ox + 1, y + ry, cp[2])
                c.set(x + ox, y + ry + 1, cp[2])
                c.set(x + ox + 1, y + ry + 1, cp[1])
    return paint


M_bloomheap = M_bloomheap_of(0, 1)


def M_cheese(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3 + (1 if (xx == 0 or yy == 0) else 0) - (1 if (xx == w - 1 or yy == h - 1) else 0)
            if face == "U":
                k = 3 + (1 if xx + yy < 3 else 0)
            c.set(x + xx, y + yy, step(CHEESE, k))
    for _ in range(max(1, w * h // 14)):
        px, py = rng.randrange(1, max(2, w - 1)), rng.randrange(0, h)
        c.set(x + px, y + py, step(CHEESE, 1))
    if face in "NESW" and h >= 3:
        for xx in range(w):
            c.set(x + xx, y + h - 1, step(R("#6e4a10", "#8e6418", "#a87e22"), 1))
            c.set(x + xx, y, step(R("#6e4a10", "#8e6418", "#a87e22"), 2))


def M_spice(rp):
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 3 + (1 if xx == 0 else -1 if xx == w - 1 else 0)
                if face == "U":
                    k = 4 if (xx + yy) % 2 == 0 else 3
                if rng.random() < 0.1:
                    k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, k))
        if face in "NESW" and h >= 3:
            for xx in range(w):
                c.set(x + xx, y, step(BURLAP, 3))                 # the sack's rolled-down top in hessian
    return paint


def M_nails(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            c.set(x + xx, y + yy, step(FORGE, 1))
    for yy in range(0, h):
        for xx in range(0, w):
            if (xx + yy * 2) % 3 == 0:
                c.set(x + xx, y + yy, step(STEEL6, 4))


def M_tray_top(fill):
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                edge = xx in (0, w - 1) or yy in (0, h - 1)
                if edge:
                    c.set(x + xx, y + yy, step(SPRUCE, 4 if (xx == 0 or yy == 0) else 2))
                else:
                    fill(c, x + xx, y + yy, rng)
    return paint


def fill_dark(rp, k=1):
    return lambda c, x, y, rng: c.set(x, y, step(rp, k))


def fill_ice(c, x, y, rng):
    c.set(x, y, step(ICE, rng.choice([2, 3, 3, 4])))


def fill_straw(c, x, y, rng):
    c.set(x, y, step(HAY, rng.choice([2, 3, 3, 4])))


def fill_cloth(c, x, y, rng):
    c.set(x, y, step(LINEN, rng.choice([2, 3, 3])))


# ---------------------------------------------------------------------------------------------------------------------
# sacks. count 1 one plump sack (8x10 footprint, 10 tall); 2 two leaning together; 3 a heap of three, one lying on two.

SACK_FILL = {"grain": dict(rp=BURLAP, mark=None), "flour": dict(rp=FLOUR, mark=R("#1c2c50", "#2c4478", "#44649e", "#6e8cc4", "#9db4e0")),
             "wool": dict(rp=None, mark=None)}


def sack_mat(fill):
    if fill == "wool":
        return M_fleece
    d = SACK_FILL[fill]
    return M_burlap(d["rp"], mark=d["mark"])


def add_sack(m, fill, ox=0, oz=0, rot=None, tag="", lying=False, y0=0):
    """One plump sack: a chamfered body (two crossed boxes), a narrower shoulder, a tied neck, a ruffled top. (ox, oz) shift it,
    rot=('z', deg, origin) tips it. Faces are shared between all sacks of an atlas (keys), so a heap costs no more texture."""
    mat = sack_mat(fill)
    if fill == "wool":
        mat_n = M_fleece_band
    else:
        mat_n = mat
    tie = {"*": M_solid(HEMP, 3)}
    k = "sack" + fill
    if lying:
        m.box((2 + ox, y0 + 0.5, 4.5 + oz), (14 + ox, y0 + 5.5, 11.5 + oz), mat_n, faces="NESWU", rot=rot, key=k + "L1")
        m.box((2 + ox, y0, 5.5 + oz), (14 + ox, y0 + 6, 10.5 + oz), mat_n, faces="NESWU", rot=rot, key=k + "L2")
        m.box((1 + ox, y0 + 1.5, 6 + oz), (2 + ox, y0 + 4.5, 10 + oz), mat, faces="NWU", rot=rot, key=k + "L3")
        m.box((14 + ox, y0 + 1.5, 6.5 + oz), (15 + ox, y0 + 4.5, 9.5 + oz), tie, faces="NESWU", rot=rot, key=k + "L4")
        return
    m.box((4 + ox, y0, 4.5 + oz), (12 + ox, y0 + 6, 11.5 + oz), mat_n, faces="NESWU", rot=rot, key=k + "B1")
    m.box((5 + ox, y0, 3 + oz), (11 + ox, y0 + 6, 13 + oz), mat_n, faces="NESWU", rot=rot, key=k + "B2")
    m.box((5 + ox, y0 + 6, 5 + oz), (11 + ox, y0 + 8, 11 + oz), mat, faces="NESWU", rot=rot, key=k + "S1")
    m.box((6 + ox, y0 + 6, 4 + oz), (10 + ox, y0 + 8, 12 + oz), mat, faces="NESWU", rot=rot, key=k + "S2")
    m.box((6.5 + ox, y0 + 8, 6.5 + oz), (9.5 + ox, y0 + 9, 9.5 + oz), tie, faces="NESWU", rot=rot, key=k + "T")
    m.box((5.6 + ox, y0 + 9, 5.6 + oz), (10.4 + ox, y0 + 10.4, 10.4 + oz), mat, faces="NESWU", rot=rot, key=k + "R")


@gen
def g_sack(w):
    variants = {}
    for fill in ("grain", "flour", "wool"):
        at = w.atlas(f"sack_{fill}")
        for count in (1, 2, 3):
            m = MB(at, f"sack_{fill}_{count}")
            if count == 1:
                add_sack(m, fill)
            elif count == 2:
                add_sack(m, fill, ox=-3.2, oz=0)
                add_sack(m, fill, ox=3.6, oz=0, rot=("z", -13, (12 + 3.6, 0, 8)))
            else:
                add_sack(m, fill, ox=-3.6, oz=-3)
                add_sack(m, fill, ox=3.6, oz=-2)
                add_sack(m, fill, lying=True, oz=1.5, y0=7)
            w.model(f"sack_{fill}_{count}", finish(w, m.build()))
            variants[f"count={count},fill={fill}"] = {"model": b(f"sack_{fill}_{count}")}
    w.state("sack", variants)


# ---------------------------------------------------------------------------------------------------------------------
# wares: goods laid out on a counter. A shallow tray (y 0..2) and the goods in small painted boxes up to ~7px tall.

PEAR = R("#3a4a10", "#5e7a1c", "#8aa62c", "#b0c640", "#d2de64", "#f0f4a0")
TRAY_SIDE = M_planks(SPRUCE, board=2, orient="h")


def draw_pear(v, cx, cy, rng):
    blob(v, cx, cy + 0.5, 2.2, 2.3, PEAR, 3, spread=1.0)
    blob(v, cx, cy - 1.6, 1.3, 1.4, PEAR, 3, spread=1.0)
    v.set(cx, cy - 3, step(R("#3a2810", "#5a3e1c"), 1))


ITEMS["pears"] = (draw_pear, 4, 4)
UNDER["pears"] = (PEAR, 0)


def tray(m, fill, x0=1, x1=15, z0=3, z1=13):
    m.box((x0, 0, z0), (x1, 2, z1), {"U": M_tray_top(fill), "*": TRAY_SIDE}, faces="NESWU", key="tray_" + m.name)


def yrot(deg, cx, cz):
    return ("y", deg, (cx, 0, cz))


def M_fishbody(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3 if face != "U" else 2 + (1 if yy == h - 1 else 0)
            if xx == 0 and face == "U":
                k = 1                                                       # the head end, darker
            if rng.random() < 0.1:
                k += rng.choice([-1, 1])
            c.set(x + xx, y + yy, step(SILVER, k))
    if face == "U":
        c.set(x, y + h // 2, step(SILVER, 0))
        for yy in range(h):
            c.set(x + w - 1, y + yy, step(SILVER, 1))


def M_mat_fix(c, x, y, w, h, face, rng, dims):
    pass


def wares_models(w):
    out = {}

    def new(good):
        at = w.atlas("wares")
        return MB(at, f"wares_{good}", ao=True)
    # --- bread: three loaves, a boule and rolls on a straw-lined tray
    m = new("bread")
    tray(m, fill_straw)
    for (x, z, wd) in ((2, 3.8, 5.2), (7.8, 3.8, 5.2)):
        m.box((x, 2, z), (x + wd, 3.6, z + 3.4), M_crust, faces="NESWU", key="loaf")
        m.box((x + 0.6, 3.6, z + 0.5), (x + wd - 0.6, 4.6, z + 2.9), M_crust, faces="NESWU", key="loaft")
    m.box((13, 2, 4), (14.6, 3.4, 7.4), M_crust, faces="NESWU", key="stick")
    for i, x in enumerate((2, 5.6, 9.2, 12.4)):
        m.box((x, 2, 8.4), (x + 2.8, 3.8, 11.2), M_crust, faces="NESWU", key="roll", rot=yrot(15 * (i - 1.5), x + 1.4, 9.8))
    m.box((12.4, 3.8, 9), (14.4, 4.8, 11), M_crust, faces="NESWU", key="rollt")
    out["bread"] = m
    # --- fish: on crushed ice
    m = new("fish")
    tray(m, fill_ice)
    for (x, z, ang) in ((1.8, 3.8, 6), (7.5, 4.5, -8), (2.5, 8, -5), (8.8, 8.6, 9)):
        m.box((x, 2, z), (x + 6.4, 3.6, z + 3), M_fishbody, faces="NESWU", key="fish", rot=yrot(ang, x + 3, z + 1.5))
    m.box((13.2, 2, 4.4), (14.6, 3.2, 6), {"*": M_solid(R("#4a2808", "#a07a1c", "#e0c240", "#f6e47a"), 2)}, faces="NESWU")   # a lemon
    out["fish"] = m
    # --- produce: apples, pears, a melon
    m = new("produce")
    tray(m, fill_dark(R("#1c1408", "#2c200e"), 1))
    m.box((2, 2, 3.6), (8.6, 4.2, 9.6), M_heap("apples"), faces="NESWU", key="ap1")
    m.box((3, 4.2, 4.6), (7.6, 5.8, 8.6), M_heap("apples"), faces="NESWU", key="ap2")
    m.box((9, 2, 4.4), (14, 6.2, 9.4), M_melon, faces="NESWU", key="melon")
    m.box((2.4, 2, 9.8), (7.6, 4, 12.4), M_heap("pears"), faces="NESWU", key="pe1")
    m.box((8.4, 2, 10), (13.6, 4, 12.6), M_heap("pears"), faces="NESWU", key="pe2")
    out["produce"] = m
    # --- cloth: folded bolts in stacks
    m = new("cloth")
    tray(m, fill_cloth)
    reds, blues, ochres, creams, greens = [cloth_ramp_fine(c_) for c_ in ((176, 58, 48), (60, 86, 146), (206, 154, 62), (232, 222, 196), (78, 116, 68))]
    for (rp, x, y, z, ww, dd) in ((reds, 1.8, 2, 3.4, 6, 4), (creams, 2, 3.6, 3.7, 5.4, 3.5), (blues, 8.4, 2, 3.4, 5.6, 4), (ochres, 8.8, 3.6, 3.8, 5, 3.4),
                                  (greens, 2, 2, 8.2, 6.4, 4), (reds, 9, 2, 8.4, 5.4, 3.6), (blues, 9.4, 3.6, 8.6, 4.4, 3)):
        m.box((x, y, z), (x + ww, y + 1.6, z + dd), M_layers(rp), faces="NESWU")
    out["cloth"] = m
    # --- pottery: jugs and bowls
    m = new("pottery")
    tray(m, fill_straw)
    for (x, z, ht) in ((2, 4, 4.4), (6.4, 4.6, 5.4), (10.8, 3.8, 4.8)):
        m.box((x, 2, z), (x + 3.8, 2 + ht - 1.4, z + 3.8), M_clay(CLAY, CLAY), faces="NESWU", key=f"jug{ht}")
        m.box((x + 0.9, 2 + ht - 1.4, z + 0.9), (x + 2.9, 2 + ht, z + 2.9), M_clay(CLAY), faces="NESWU", key=f"jugn{ht}")
        m.box((x + 3.8, 2 + ht - 3, z + 1.4), (x + 4.7, 2 + ht - 1, z + 2.4), M_clay(CLAY), faces="NESWU", key=f"jugh{ht}")
    for (x, z) in ((2, 8.6), (6.8, 8.8), (11, 8.4)):
        m.box((x, 2, z), (x + 3.4, 3, z + 3.4), M_clay(CLAY), faces="NESWU", key="bowl")
        m.box((x - 0.4, 3, z - 0.4), (x + 3.8, 4, z + 3.8), M_clay(CLAY, R("#1e3a5a", "#2e5a8a", "#4a82bc", "#78aedc")), faces="NESWU", key="bowlr")
    out["pottery"] = m
    # --- candles: bunches, standing and laid
    m = new("candles")
    tray(m, fill_cloth)
    for (x, z, hh) in ((2, 4, 5), (5.6, 4.4, 4), (9.2, 4.2, 5.4), (12.4, 4.6, 4.4)):
        m.box((x, 2, z), (x + 2.8, 2 + hh, z + 2.8), M_wax, faces="NESWU", key=f"cb{hh}")
    m.box((2, 2, 8.6), (9.2, 3.6, 10.2), M_wax, faces="NESWU", key="cl1")
    m.box((2.4, 3.6, 8.8), (8.8, 5, 10), M_wax, faces="NESWU", key="cl2")
    m.box((10, 2, 8.4), (13.6, 4.2, 11.6), {"*": M_solid(WAX, 3), "U": M_solid(WAX, 4)}, faces="NESWU")
    out["candles"] = m
    # --- flowers: three buckets of cut blooms
    m = new("flowers")
    tray(m, fill_dark(SPRUCE, 2))
    for i, (x, z) in enumerate(((1.8, 3.8), (8.4, 3.4), (5, 8.2), (11, 8.4))):
        m.box((x, 2, z), (x + 4, 4.4, z + 4), M_planks(SPRUCE, board=2, orient="v", nails=False), faces="NESWU", key="bucket")
        m.box((x - 0.2, 3.1, z - 0.2), (x + 4.2, 3.9, z + 4.2), M_iron(FORGE, 3), faces="NESW", key="hoop")
        m.box((x + 0.2, 4.4, z + 0.2), (x + 3.8, 6.8, z + 3.8), M_bloomheap_of((0, 1, 2, 3)[i], 4), faces="NESWU", key=f"blooms{i}")
    out["flowers"] = m
    # --- spice: little sacks in rows
    m = new("spice")
    tray(m, fill_dark(SPRUCE, 1))
    cols = [R("#4a0e0a", "#7e1a14", "#b02c20", "#d44a30"), R("#6a4a08", "#a07410", "#d0a020", "#f0c838"), R("#14301a", "#245028", "#3e7a3a", "#62a456"),
            R("#2a1a0e", "#4a2e16", "#74502a", "#9a7444"), R("#5a3a10", "#8a5c1a", "#b48426", "#d8a842"), R("#3a2018", "#6a3a28", "#9a5a3c", "#c47e58")]
    for i, (x, z) in enumerate(((2, 3.8), (5.6, 3.8), (9.2, 3.8), (12.6, 4), (2, 8.4), (5.6, 8.4), (9.2, 8.4), (12.6, 8.2))):
        m.box((x, 2, z), (x + 2.8, 5, z + 2.8), M_spice(cols[i % 6]), faces="NESWU", key=f"sp{i % 6}")
    out["spice"] = m
    # --- tools: a hammer, tongs, a chisel and a tray of nails
    m = new("tools")
    tray(m, fill_dark(SPRUCE, 1))
    m.box((2, 2, 4), (9.5, 3.2, 5.2), M_planks(SPRUCE, board=2), faces="NESWU", key="hh")
    m.box((8.6, 2, 3.2), (11.6, 4.4, 6), M_iron(STEEL6, 4), faces="NESWU", key="hd")
    m.box((2, 2, 8), (8.4, 2.9, 9), M_iron(STEEL6, 3), faces="NESWU", key="t1", rot=yrot(8, 2, 8.5))
    m.box((2, 2, 9.6), (8.4, 2.9, 10.6), M_iron(STEEL6, 3), faces="NESWU", key="t2", rot=yrot(-8, 2, 10))
    m.box((9.4, 2, 8), (14.4, 3.8, 12), M_nails, faces="NESWU", key="nails")
    m.box((11, 2, 4), (14, 2.8, 5), M_iron(STEEL6, 3), faces="NESWU", key="chis")
    out["tools"] = m
    # --- cheese: two wheels and a wedge
    m = new("cheese")
    tray(m, fill_cloth)
    for (x, z, wd, hh) in ((2, 3.6, 6.4, 3), (8.4, 3.8, 5.4, 2.6)):
        m.box((x, 2, z + 0.9), (x + wd, 2 + hh, z + wd - 0.9), M_cheese, faces="NESWU", key=f"cw{wd}")
        m.box((x + 0.9, 2, z), (x + wd - 0.9, 2 + hh, z + wd), M_cheese, faces="NESWU", key=f"cx{wd}")
    m.box((2.4, 5, 4.8), (7.4, 6.2, 8.6), M_cheese, faces="NESWU", key="cu")
    m.box((3.6, 2, 9.4), (9.6, 3.6, 12.4), M_cheese, faces="NESWU", key="wedge1")
    m.box((4.6, 3.6, 9.8), (8.8, 4.6, 12), M_cheese, faces="NESWU", key="wedge2")
    m.box((10.2, 2, 8.8), (13.8, 4, 12.4), M_cheese, faces="NESWU", key="cwb")
    out["cheese"] = m
    return out


@gen
def g_wares(w):
    variants = {}
    for good, m in wares_models(w).items():
        w.model(f"wares_{good}", finish(w, m.build()))
        variants[f"good={good}"] = {"model": b(f"wares_{good}")}
    w.state("wares", variants)


# ---------------------------------------------------------------------------------------------------------------------
# stall counter, market table, goods shelf, produce basket, cask

SKIRT = cloth_ramp_fine((150, 70, 52), cool=0.05)


def M_skirt(c, x, y, w, h, face, rng, dims):
    """A cloth skirt hung in two-pixel pleats, a hem along the bottom, gathered under the counter's lip."""
    for yy in range(h):
        for xx in range(w):
            pleat = (xx // 2) % 2
            k = 3 + (1 if (xx % 2 == 0 and pleat == 0) else -1 if (xx % 2 == 1 and pleat == 1) else 0)
            if yy < 2:
                k -= 1
            if yy >= h - 2:
                k += 1 if yy == h - 2 else -1
            if rng.random() < 0.08:
                k += rng.choice([-1, 1])
            c.set(x + xx, y + yy, step(SKIRT, k))
    if h >= 6:
        for xx in range(w):                             # a woven braid trim above the hem
            c.set(x + xx, y + h - 4, step(LINEN, 3 if xx % 2 else 2))
            c.set(x + xx, y + h - 5, step(SKIRT, 1))


def M_board_front(c, x, y, w, h, face, rng, dims):
    """The folded-up flap: dark spruce boards, two iron strap hinges at the top, a hasp at the middle."""
    M_planks(SPRUCE, board=4, orient="v", tone=(1, 2, 2))(c, x, y, w, h, face, rng, dims)
    for yy in (1, h - 3):
        for xx in range(w):
            c.set(x + xx, y + yy, step(FORGE, 3))
            c.set(x + xx, y + yy + 1, step(FORGE, 1))
    for xx in range(w // 2 - 1, w // 2 + 2):
        for yy in range(h // 2 - 2, h // 2 + 2):
            c.set(x + xx, y + yy, step(FORGE, 3 if (xx + yy) % 2 else 2))
    c.set(x + w // 2, y + h // 2, step(STEEL6, 4))


def counter_models(w):
    at = w.atlas("stall_counter")
    res = {}
    for open_ in (True, False):
        m = MB(at, f"stall_counter_{open_}")
        top = M_planks(OAK, board=4, orient="h", nails=False)
        m.box((0, 14, 0), (16, 16, 16), {"U": top, "*": M_planks(OAK, board=2, orient="h")}, faces="NESWUD", cull="ESWD")
        m.box((1, 0, 1), (15, 14, 15), M_planks(DARKOAK, board=4, orient="v"), faces="ESW", key="body", cull="")
        if open_:
            m.box((1, 2, 0), (15, 14, 1), {"N": M_skirt, "*": M_solid(SKIRT, 2)}, faces="NEW", key="skirt")
        else:
            m.box((1, 0, 0), (15, 14, 1), M_planks(DARKOAK, board=4, orient="v"), faces="E", key="body2")
            m.box((0.5, 1, -0.7), (15.5, 14, 0.3), {"N": M_board_front, "*": M_solid(SPRUCE, 2)}, faces="NEWU", key="flap")
        res[open_] = m
    return res


def M_runner(c, x, y, w, h, face, rng, dims):
    """The table top: boards along x, a linen runner across the middle with a red stripe at each edge."""
    M_planks(OAK, board=4, orient="h")(c, x, y, w, h, face, rng, dims)
    if face == "U":
        for yy in range(4, 12):
            for xx in range(w):
                edge = yy in (4, 11)
                k = 3 + (1 if (xx + yy) % 2 == 0 else 0)
                c.set(x + xx, y + yy, step(LINEN, 4 if edge else k))
        for xx in range(w):
            c.set(x + xx, y + 5, step(cloth_ramp_fine((170, 56, 46)), 3))
            c.set(x + xx, y + 10, step(cloth_ramp_fine((170, 56, 46)), 3))


def table_model(w):
    at = w.atlas("market_table")
    m = MB(at, "market_table")
    m.box((0, 14, 0), (16, 16, 16), {"U": M_runner, "*": M_planks(OAK, board=2, orient="h")}, faces="NESWUD", cull="ESW")
    ang = math.degrees(math.atan2(13, 14))
    L = math.hypot(13, 14)
    for zc in (2.4, 13.6):
        for sgn in (-1, 1):
            m.box((7.2, 7 - L / 2, zc - 0.8), (8.8, 7 + L / 2, zc + 0.8), M_planks(SPRUCE, board=2, orient="v", tone=(2, 3)),
                  faces="NESW", rot=("z", sgn * ang, (8, 7, zc)), key="leg")
    for zc in (2.4, 13.6):
        m.box((0.8, 11.5, zc - 0.8), (15.2, 12.8, zc + 0.8), M_planks(SPRUCE, board=2, orient="h", tone=(2, 3)), faces="NESWU", key="rail")
    return m


def M_shelf_row(kind):
    """The front of a shelf's goods, drawn row-wise (N face 13x4): jars, cloth, loaves, pots, books, tools."""
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                c.set(x + xx, y + yy, step(SPRUCE, 1))
        if face != "N":
            for yy in range(h):
                for xx in range(w):
                    c.set(x + xx, y + yy, step(SPRUCE, 2 if face != "U" else 3))
            return
        if kind == "jars":
            cols = [R("#4a0e0a", "#7e1a14", "#b02c20", "#d44a30"), R("#6a4a08", "#a07410", "#d0a020", "#f0c838"),
                    R("#14301a", "#245028", "#3e7a3a", "#62a456"), R("#3a1a30", "#6a2e58", "#984a82", "#c070aa")]
            for i in range(0, w - 2, 3):
                cp = cols[(i // 3) % 4]
                for yy in range(1, h):
                    for xx in range(i, min(w, i + 3)):
                        k = 2 + (1 if xx == i else -1 if xx == i + 2 else 0) + (1 if yy == 1 else 0)
                        c.set(x + xx, y + yy, step(cp, 2 if yy == 1 and False else k))
                for xx in range(i, min(w, i + 3)):
                    c.set(x + xx, y, step(WEATHERED, 3))              # the paper lid
        elif kind == "cloth":
            cols = [cloth_ramp_fine(q) for q in ((176, 58, 48), (60, 86, 146), (206, 154, 62), (232, 222, 196), (78, 116, 68))]
            for i in range(0, w, 3):
                cp = cols[(i // 3) % 5]
                for yy in range(0, h):
                    for xx in range(i, min(w, i + 3)):
                        c.set(x + xx, y + yy, step(cp, 3 + (1 if yy % 2 == 0 else -1)))
        elif kind == "bread":
            for i in range(0, w, 4):
                for yy in range(1, h):
                    for xx in range(i, min(w, i + 4)):
                        edge = xx in (i, i + 3) or yy == h - 1
                        c.set(x + xx, y + yy, step(CRUST, 2 if edge else 4 if yy == 1 else 3))
                if i + 2 < w:
                    c.set(x + i + 1, y + 2, step(CRUST, 5))
        elif kind == "pots":
            for i in range(0, w - 2, 4):
                for yy in range(0, h):
                    wd = 3 if yy in (0, h - 1) else 4
                    for xx in range(i, min(w, i + wd)):
                        c.set(x + xx, y + yy, step(CLAY, 3 + (1 if xx == i else -1 if xx == i + wd - 1 else 0)))
        elif kind == "books":
            cols = [R("#3a0e10", "#681a1c", "#98282a", "#b83c38"), R("#142a4a", "#244070", "#3a5ea0", "#5a82c4"), R("#14301a", "#245028", "#3e7a3a", "#62a456"),
                    R("#3a2a10", "#6a4a1c", "#9a7430", "#c49c4c"), R("#2a1a30", "#4a2e58", "#6e4a84", "#9470b0")]
            xx = 0
            i = 0
            while xx < w:
                bw = 2 + (i % 2)
                cp = cols[i % 5]
                bh = h - (1 if i % 3 == 2 else 0)
                for dx in range(bw):
                    if xx + dx >= w:
                        break
                    for yy in range(h - bh, h):
                        c.set(x + xx + dx, y + yy, step(cp, 3 + (1 if dx == 0 else -1 if dx == bw - 1 else 0)))
                    c.set(x + xx + dx, y + h - bh + 1, step(GILT, 3)) if bh >= 3 else None
                xx += bw
                i += 1
        else:                                                          # tools: hung and laid
            for i in range(0, w - 1, 4):
                for yy in range(0, h):
                    c.set(x + i, y + yy, step(SPRUCE, 4 if yy < h - 1 else 2))
                for xx in range(i - 1, i + 2):
                    c.set(x + xx, y, step(STEEL6, 4)) if 0 <= xx < w else None
                    c.set(x + xx, y + 1, step(STEEL6, 3)) if 0 <= xx < w else None
    return paint


def shelf_model(w, goods):
    at = w.atlas("goods_shelf")
    m = MB(at, f"goods_shelf_{goods}")
    wood = M_planks(SPRUCE, board=4, orient="v")
    m.box((0, 0, 8), (1.5, 16, 16), wood, faces="NEWU", key="post")
    m.box((14.5, 0, 8), (16, 16, 16), wood, faces="NEWU", key="post")
    m.box((1.5, 0, 15), (14.5, 16, 16), {"N": M_planks(DARKOAK, board=4, orient="v"), "*": M_solid(DARKOAK, 2)}, faces="N", key="back")
    for (y0, y1) in ((4.5, 6), (10.5, 12), (15, 16)):
        m.box((1.5, y0, 8), (14.5, y1, 15.5), M_planks(SPRUCE, board=4, orient="h"), faces="NU" if y0 < 14 else "NUE", key=f"shelf{y0}")
    m.box((1.5, 0, 8.5), (14.5, 2, 15), M_planks(DARKOAK, board=2), faces="N", key="plinth")
    row = M_shelf_row(goods)
    for (y0) in (6, 12):
        m.box((2, y0, 9.5), (14, y0 + 4, 14.5), {"N": row, "*": M_shelf_row("none")}, faces="NU", key=f"row{y0}")
    m.box((2, 2, 9.5), (14, 4.5, 14.5), {"N": row, "*": M_shelf_row("none")}, faces="NU", key="rowlow")
    return m


def M_slats(rp, gap_dark=True):
    """A slatted basket side: horizontal slats with dark gaps."""
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                if yy % 3 == 2:
                    k = 0
                else:
                    k = 3 + (1 if yy % 3 == 0 else 0) + (1 if xx == 0 else 0)
                    if rng.random() < 0.15:
                        k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, k))
    return paint


def M_carrots(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            c.set(x + xx, y + yy, step(R("#10200c", "#1a3010", "#2a4a1a"), 0))
    for cx in range(0, w, 2):
        for yy in range(h):
            top = yy < max(2, h // 3)
            if top:
                c.set(x + cx, y + yy, step(LEAFG, 3 if (yy + cx) % 2 else 4))
                c.set(x + cx + 1, y + yy, step(LEAFG, 2))
            else:
                taper = yy > h - 3
                c.set(x + cx, y + yy, step(CARROT, 2 if yy % 3 else 3))
                if not taper:
                    c.set(x + cx + 1, y + yy, step(CARROT, 3 if yy % 2 else 4))


def produce_model(w, kind):
    at = w.atlas("produce")
    m = MB(at, f"produce_{kind}")
    if kind == "flowers":
        for i, (x, z) in enumerate(((1.5, 2.5), (8.8, 2.2), (4.8, 8.4), (11.4, 8.8))):
            bl = M_bloomheap_of((0, 1, 2, 3)[i], 4)
            m.box((x, 0, z), (x + 4.4, 5, z + 4.4), M_planks(SPRUCE, board=2, orient="v", tone=(2, 3, 3)), faces="NESWU", key="bucket")
            m.box((x - 0.2, 1.2, z - 0.2), (x + 4.6, 2.2, z + 4.6), M_iron(FORGE, 3), faces="NESW", key="hoop1")
            m.box((x - 0.2, 3.4, z - 0.2), (x + 4.6, 4.4, z + 4.6), M_iron(FORGE, 3), faces="NESW", key="hoop2")
            m.box((x + 0.3, 5, z + 0.3), (x + 4.1, 8.5, z + 4.1), bl, faces="NESWU", key=f"pbl{i}")
            m.box((x + 1, 8.5, z + 1), (x + 3.4, 11, z + 3.4), bl, faces="NESWU", key=f"pbm{i}")
        return m
    basket = M_slats(SPRUCE)
    m.box((1.5, 0, 2), (14.5, 6, 14), {"*": basket, "U": M_solid(R("#10200c", "#1a3010", "#2a4a1a"), 0)}, faces="NESWU", key="bas")
    m.box((1, 5, 1.5), (15, 6.4, 2.5), M_planks(OAK, board=2), faces="NEWU", key="r1")
    m.box((1, 5, 13.5), (15, 6.4, 14.5), M_planks(OAK, board=2), faces="SEWU", key="r2")
    if kind == "carrots":
        m.box((2.4, 6, 3), (13.6, 10.5, 13), M_carrots, faces="NESWU", key="carrots")
        m.box((3.6, 10.5, 4.4), (12.4, 12, 11.6), M_carrots, faces="NESWU", key="carrots2")
        return m
    heap = M_heap({"apples": "apples", "cabbages": "cabbages", "pumpkins": "pumpkins", "potatoes": "potatoes"}[kind])
    m.box((2.4, 6, 3), (13.6, 8.6, 13), heap, faces="NESWU", key="h1" + kind)
    m.box((3.6, 8.6, 4.4), (12.4, 10.4, 11.6), heap, faces="NESWU", key="h2" + kind)
    m.box((5.6, 10.4, 6), (10.4, 12, 10), heap, faces="NESWU", key="h3" + kind)
    return m


# ----- cask: bulging staves, two iron hoops, a round lid with a bung ----------------------------------------------------

def _lid(c, x, y, w, h, rng, bung=True):
    """A round cask end: boards across, a darker chime, the corners outside the circle left as the iron hoop's shadow."""
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    r = min(w, h) / 2.0
    for yy in range(h):
        for xx in range(w):
            d = math.hypot(xx - cx, yy - cy)
            if d > r + 0.25:
                c.set(x + xx, y + yy, step(SPRUCE, 1))
                continue
            k = 3 + (1 if (xx + yy) < cx + 1 else 0)
            if (yy + 1) % 3 == 0:
                k -= 1
            if rng.random() < 0.14:
                k += rng.choice([-1, 1])
            if d > r - 0.9:
                k = 1 if (xx + yy) > cx + cy else 2
            c.set(x + xx, y + yy, step(OAK, k))
    if bung and w >= 6:
        bx, by = int(cx + 1), int(cy)
        c.set(x + bx, y + by, step(FORGE, 1))
        c.set(x + bx + 1, y + by, step(FORGE, 2))


def M_staves_for(axis):
    """Oak staves with a lit edge and a dark seam, two iron hoops; axis is the way the cask's length runs (y upright, z lying)."""
    def paint(c, x, y, w, h, face, rng, dims):
        lid = (axis == "y" and face in "UD") or (axis == "z" and face in "NS")
        if lid:
            _lid(c, x, y, w, h, rng, bung=(face in "UN"))
            return
        for yy in range(h):
            for xx in range(w):
                u = xx if (axis == "y" or face in "UD") else yy
                st = u % 4
                k = 3 + (1 if st == 0 else -1 if st == 3 else 0)
                if rng.random() < 0.18:
                    k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(OAK, k))
        length = h if (axis == "y" or face in "UD") else w
        for hp in (1, length - 2):                                    # the hoops
            for t in range(w if (axis == "y" or face in "UD") else h):
                for dd in range(2):
                    if axis == "y" or face in "UD":
                        px, py = t, hp + dd
                    else:
                        px, py = hp + dd, t
                    if dd == 0:
                        c.set(x + px, y + py, step(FORGE, 4 if t % 3 else 3))
                    else:
                        c.set(x + px, y + py, step(FORGE, 1))
    return paint


M_staves = M_staves_for("y")


def cask_models(w):
    at = w.atlas("cask")
    res = {}
    m = MB(at, "cask_upright")
    m.box((2, 0, 2), (14, 3, 14), M_staves, faces="NESWUD", key="end")
    m.box((1, 3, 1), (15, 13, 15), M_staves, faces="NESWUD", key="mid")
    m.box((2, 13, 2), (14, 16, 14), M_staves, faces="NESWU", key="endt")
    res["upright"] = m
    # lying along z, on two chocks
    ms = M_staves_for("z")
    m = MB(at, "cask_lying")
    m.box((1, 2.5, 2), (15, 14.5, 14), ms, faces="NSEWUD", key="lm")
    m.box((2, 4, 0), (14, 13, 2), ms, faces="NEWUD", key="le")
    m.box((2, 4, 14), (14, 13, 16), ms, faces="SEWUD", key="le2")
    m.box((3, 0, 3), (5, 2.5, 5), M_planks(SPRUCE, board=2), faces="NESWU", key="ck")
    m.box((11, 0, 3), (13, 2.5, 5), M_planks(SPRUCE, board=2), faces="NESWU", key="ck")
    m.box((3, 0, 11), (5, 2.5, 13), M_planks(SPRUCE, board=2), faces="NESWU", key="ck")
    m.box((11, 0, 11), (13, 2.5, 13), M_planks(SPRUCE, board=2), faces="NESWU", key="ck")
    res["lying"] = m
    return res


@gen
def g_furniture(w):
    cm = counter_models(w)
    vs = {}
    for open_, m in cm.items():
        name = f"stall_counter_{'open' if open_ else 'closed'}"
        w.model(name, finish(w, m.build()))
        for f in FACINGS:
            vs[f"facing={f},open={'true' if open_ else 'false'}"] = rot_variant(name, f)
    w.state("stall_counter", vs)
    w.model("market_table", finish(w, table_model(w).build()))
    w.state("market_table", {"axis=x": {"model": b("market_table")}, "axis=z": {"model": b("market_table"), "y": 90}})
    vs = {}
    for goods in SHELF_GOODS:
        w.model(f"goods_shelf_{goods}", finish(w, shelf_model(w, goods).build()))
        for f in FACINGS:
            vs[f"facing={f},goods={goods}"] = rot_variant(f"goods_shelf_{goods}", f)
    w.state("goods_shelf", vs)
    vs = {}
    for kind in ("apples", "cabbages", "carrots", "pumpkins", "potatoes", "flowers"):
        w.model(f"produce_{kind}", finish(w, produce_model(w, kind).build()))
        vs[f"kind={kind}"] = {"model": b(f"produce_{kind}")}
    w.state("produce", vs)
    cs = cask_models(w)
    w.model("cask_upright", finish(w, cs["upright"].build()))
    w.model("cask_lying", finish(w, cs["lying"].build()))
    w.state("cask", {"orient=upright": {"model": b("cask_upright")}, "orient=z": {"model": b("cask_lying")},
                     "orient=x": {"model": b("cask_lying"), "y": 90}})


# ---------------------------------------------------------------------------------------------------------------------
# carts: a two-wheeled market cart, two blocks long (body, shafts). The wheel is a 14px disc with a dark iron rim.

def cart_wheel(spokes=8):
    """14px across, centred on (7.5, 7.5): an iron tyre, a timber felloe, spokes (half as many lines through the hub), a hub."""
    c = Canvas()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 7.1:
                if d > 6.0:
                    k = 3 + (1 if (x + y) < 14 else -1) + (1 if (x * 7 + y * 3) % 9 == 0 else 0)
                    c.set(x, y, step(STEEL6, k - 1))
                elif d > 5.0:
                    c.set(x, y, step(SPRUCE, 3 if (x + y) < 14 else 2))
    n = spokes // 2
    for i in range(n):
        a = math.pi * i / n + 0.12
        for sgn in (-1, 1):
            for t in range(2, 11):
                r = t * 0.5
                x, y = int(round(cx + sgn * math.cos(a) * r)), int(round(cy + sgn * math.sin(a) * r))
                if math.hypot(x - cx, y - cy) < 5.6:
                    c.set(x, y, step(SPRUCE, 3 if (x + y) % 3 else 4))
    for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        c.set(7 + dx, 7 + dy, step(FORGE, 3 if dx + dy == 0 else 2))
    c.set(7, 7, step(GILT, 4))
    for (x, y) in ((6, 7), (6, 8), (9, 7), (9, 8), (7, 6), (8, 6), (7, 9), (8, 9)):
        c.set(x, y, step(FORGE, 3))
    return c


def wheel_elements(x0, tex="#wheel"):
    """A disc box (cutout faces east and west) with eight tyre segments round it."""
    els = [E((x0, 0, 1), (x0 + 2, 14, 15), {"E": (tex, [0, 1, 16, 15]), "W": (tex, [16, 1, 0, 15])})]
    return els


def cart_wheel_rim(m, x0):
    rim = M_iron(STEEL6, 3)
    for k in range(8):
        a = k * 45.0
        m.box((x0, 7 + 6.3, 8 - 2.7), (x0 + 2, 7 + 7.0, 8 + 2.7), {"U": rim, "*": rim}, faces="UNSEW", key="rim", rot=("x", a, (x0, 7, 8)))


def cart_model(w, load, cart_atlas):
    m = MB(cart_atlas, f"cart_{load}")
    wood = M_planks(OAK, board=4, orient="v")
    sidew = M_planks(OAK, board=2, orient="h")
    m.box((1, 6, 0), (15, 8, 16), {"U": M_planks(OAK, board=4, orient="v", nails=True), "*": M_planks(OAK, board=2, orient="h")}, faces="NESWUD", key="bed")
    m.box((1, 8, 0), (2, 12, 16), sidew, faces="NEUW", key="side")
    m.box((14, 8, 0), (15, 12, 16), sidew, faces="NEUW", key="side")
    m.box((2, 8, 14.5), (14, 12, 16), sidew, faces="NEUS", key="back")
    m.box((2, 8, 0), (14, 9.5, 1), sidew, faces="NU", key="front")
    m.box((0, 4.5, 7), (16, 6.5, 9), M_planks(DARKOAK, board=2, orient="h"), faces="NESWUD", key="axle")
    m.box((3, 6.5, 0.5), (4.5, 8, 15.5), M_planks(DARKOAK, board=2, orient="v"), faces="ND", key="rail")
    m.box((11.5, 6.5, 0.5), (13, 8, 15.5), M_planks(DARKOAK, board=2, orient="v"), faces="ND", key="rail")
    for x0 in (-1, 15):
        cart_wheel_rim(m, x0)
    # loads
    crate_t = {"s": b("crate_side_0"), "t": b("crate_top_lid")}
    if load == "crates":
        for (x, y, z, s_) in ((2.4, 9.5, 2.4, 6), (8.6, 9.5, 3, 5.4), (4.5, 15.5, 4, 5.2)):
            tv = lambda n: m.var(n)
            sv, tvr = m.var("crate_side_0"), m.var("crate_top_lid")
            m.raw(E((x, y, z), (x + s_, y + s_, z + s_), {"N": (sv, [0, 0, 16, 16]), "E": (sv, [0, 0, 16, 16]), "S": (sv, [0, 0, 16, 16]),
                                                           "W": (sv, [0, 0, 16, 16]), "U": (tvr, [0, 0, 16, 16])}))
    elif load == "sacks":
        add_sack(m, "grain", ox=-3.6, oz=-2, y0=8, tag="c")
        add_sack(m, "grain", ox=3.6, oz=-1, y0=8, tag="c")
        add_sack(m, "flour", ox=0, oz=3.4, y0=8, tag="c", rot=("z", 6, (8, 8, 8)))
    elif load == "casks":
        mz = M_staves_for("z")
        for (x, z) in ((2.6, 2), (8.6, 3)):
            m.box((x, 9.5, z), (x + 5, 14.5, z + 11), mz, faces="NESWU", key="cc1")
            m.box((x - 0.4, 10, z + 1), (x + 5.4, 14, z + 10), mz, faces="EWU", key="cc2")
    elif load == "produce":
        for (x, z, kind) in ((2.4, 1.6, "apples"), (8.6, 2.2, "cabbages"), (2.4, 8.4, "potatoes"), (8.8, 8.8, "apples")):
            m.box((x, 9.5, z), (x + 5, 11.5, z + 5), M_slats(SPRUCE), faces="NESWU", key="pb")
            m.box((x + 0.3, 11.5, z + 0.3), (x + 4.7, 13.4, z + 4.7), M_heap(kind), faces="NESWU", key="ph" + kind)
    wheel_t = b("cart_wheel")
    mod = m.build(extra_textures={"wheel": wheel_t})
    for x0 in (-1, 15):
        mod["elements"] += wheel_elements(x0)
    return mod


def cart_shafts_model(w, cart_atlas):
    m = MB(cart_atlas, "cart_shafts")
    ang = -math.degrees(math.atan2(6.75, 14))
    L = math.hypot(14, 6.75)
    for x0 in (3.2, 11.3):
        m.box((x0, 7, 16 - L), (x0 + 1.5, 8.5, 16), M_planks(OAK, board=2, orient="h", tone=(2, 3)), faces="NESWUD", key="shaft", rot=("x", ang, (x0 + 0.75, 7.75, 16)))
    m.box((2.5, 1, 3), (13.5, 2.6, 4.5), M_planks(DARKOAK, board=2, orient="h"), faces="NESWUD", key="xbar")
    return m.build()


def M_hay(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            c.set(x + xx, y + yy, step(HAY, rng.choice([2, 3, 3, 4])))
    for _ in range(max(4, w * h // 5)):
        sx_, sy_ = rng.randrange(0, w), rng.randrange(0, h)
        ln = rng.randrange(3, 8)
        for t in range(ln):
            if sx_ + t < w:
                c.set(x + sx_ + t, y + sy_ + (t // 4) * rng.choice([0, 1]), step(HAY, 5 if t == 0 else 4 if t % 2 else 1))


def hay_cart_model(w, cart_atlas):
    m = MB(cart_atlas, "hay_cart")
    sidew = M_planks(OAK, board=2, orient="h")
    m.box((1, 6, 0), (15, 8, 16), {"U": M_planks(OAK, board=4, orient="v"), "*": M_planks(OAK, board=2, orient="h")}, faces="NESWD", key="bed")
    m.box((1, 8, 0), (2, 12, 16), sidew, faces="NEUW", key="side")
    m.box((14, 8, 0), (15, 12, 16), sidew, faces="NEUW", key="side")
    m.box((0, 4.5, 7), (16, 6.5, 9), M_planks(DARKOAK, board=2, orient="h"), faces="NESWUD", key="axle")
    for x0 in (-1, 15):
        cart_wheel_rim(m, x0)
    m.box((0.6, 8, 0.4), (15.4, 13, 15.6), M_hay, faces="NESWU", key="hay1")
    m.box((1.4, 13, 1), (14.6, 15.4, 15), M_hay, faces="NESWU", key="hay2")
    m.box((3, 15.4, 2.4), (13, 16, 13.6), M_hay, faces="NESWU", key="hay3")
    rng = random.Random(7)
    for i in range(9):
        x, z = rng.uniform(1.5, 13.5), rng.uniform(1, 14)
        y = rng.choice([13, 14, 15, 11, 12])
        ang = rng.choice([-1, 1]) * rng.uniform(30, 55)
        m.box((x, y, z), (x + 0.6, y + 3.2, z + 0.6), M_hay, faces="NESW", key="straw", rot=(rng.choice("xz"), ang, (x, y, z)))
    mod = m.build(extra_textures={"wheel": b("cart_wheel")})
    for x0 in (-1, 15):
        mod["elements"] += wheel_elements(x0)
    return mod


@gen
def g_carts(w):
    w.texture("cart_wheel", cart_wheel())
    at = w.atlas("cart")
    vs = {}
    for load in ("empty", "crates", "sacks", "casks", "produce"):
        w.model(f"cart_body_{load}", finish(w, cart_model(w, load, at)))
    w.model("cart_shafts", finish(w, cart_shafts_model(w, at)))
    w.model("hay_cart_body", finish(w, hay_cart_model(w, at)))
    w.model("hay_cart_shafts", finish(w, cart_shafts_model_hay(w, at)))
    for f in FACINGS:
        for load in ("empty", "crates", "sacks", "casks", "produce"):
            vs[f"facing={f},load={load},part=body"] = rot_variant(f"cart_body_{load}", f)
            vs[f"facing={f},load={load},part=shafts"] = rot_variant("cart_shafts", f)
    w.state("cart", vs)
    w.state("hay_cart", {f"facing={f},part={p}": rot_variant("hay_cart_body" if p == "body" else "hay_cart_shafts", f)
                         for f in FACINGS for p in ("body", "shafts")})


def cart_shafts_model_hay(w, at):
    return cart_shafts_model(w, at)


# ---------------------------------------------------------------------------------------------------------------------
# bench, planter, wall lantern, notice board, fishing net, well windlass, mooring ring

def bench_model(w):
    at = w.atlas("bench")
    m = MB(at, "bench")
    wood = M_planks(WEATHERED, board=2, orient="h", tone=(2, 3, 3, 4), nails=True)
    leg = M_planks(WEATHERED, board=2, orient="v", tone=(1, 2, 3))
    m.box((0, 7, 4), (16, 9, 12), {"U": M_planks(WEATHERED, board=4, orient="h", nails=True, tone=(2, 3, 3, 4)), "*": wood}, faces="NESWUD")
    for x in (1.5, 12.5):
        m.box((x, 0, 4.5), (x + 2, 7, 6.5), leg, faces="NESW", key="lf")
        m.box((x, 0, 11), (x + 2, 7, 13), leg, faces="NESW", key="lb")
        m.box((x, 3.5, 6.5), (x + 2, 5, 11), leg, faces="NEWU", key="rail")
        m.box((x, 7, 12), (x + 2, 14, 14), leg, faces="NESWU", key="post")
    m.box((0, 10, 12), (16, 14, 14), {"S": wood, "N": wood, "*": M_planks(WEATHERED, board=2, orient="h")}, faces="NESWUD")
    return m.build()


def M_moulded(rp):
    """A pale moulded stone trough: a lipped top, a shadow under it, a recessed panel with a lit lower edge, a plinth."""
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 3
                if face in "NESW":
                    if yy < 2:
                        k = 5 if yy == 0 else 4
                    elif yy == 2:
                        k = 1
                    elif yy >= h - 2:
                        k = 2 if yy == h - 1 else 3
                    elif xx in (1, 2) and 3 <= yy < h - 2 and w > 6 and yy < h - 3:
                        k = 2
                    elif xx == w - 2 and 3 <= yy < h - 3:
                        k = 4
                    if xx == 0:
                        k += 1
                    elif xx == w - 1:
                        k -= 1
                if face == "U":
                    edge = xx in (0, w - 1) or yy in (0, h - 1)
                    k = 4 if edge else 3
                if rng.random() < 0.1:
                    k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, k))
    return paint


def M_soil_top(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            edge = xx in (0, w - 1) or yy in (0, h - 1)
            if edge:
                c.set(x + xx, y + yy, step(STONE, 4 if (xx == 0 or yy == 0) else 3))
            elif xx == 1 or yy == 1:
                c.set(x + xx, y + yy, step(SOIL, 0))
            else:
                c.set(x + xx, y + yy, step(SOIL, rng.choice([1, 2, 2, 3, 3])))


def M_wood_planter(c, x, y, w, h, face, rng, dims):
    M_planks(DARKOAK, board=3, orient="h", nails=False, tone=(2, 3, 3))(c, x, y, w, h, face, rng, dims)
    if face in "NESW":
        for bx in (2, w - 4):
            for yy in range(h):
                c.set(x + bx, y + yy, step(FORGE, 4 if yy % 5 == 0 else 3))
                c.set(x + bx + 1, y + yy, step(FORGE, 1))
    if face == "U":
        for xx in range(w):
            for yy in range(h):
                edge = xx in (0, w - 1) or yy in (0, h - 1)
                if edge:
                    c.set(x + xx, y + yy, step(DARKOAK, 4 if (xx == 0 or yy == 0) else 2))
                else:
                    c.set(x + xx, y + yy, step(SOIL, rng.choice([1, 2, 2, 3, 3]) if xx > 1 and yy > 1 else 0))


def planter_shrub():
    rng = rng_for("city_street", "planter_shrub")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, step(LEAFG, rng.choice([0, 1, 1, 2])))
    for _ in range(60):
        cx, cy = rng.randrange(16), rng.randrange(16)
        k = rng.choice([2, 3, 3, 4])
        for (dx, dy) in ((0, 0), (1, 0), (0, 1), (-1, 0)):
            c.set((cx + dx) % 16, (cy + dy) % 16, step(LEAFG, k))
        c.set((cx - 1) % 16, (cy - 1) % 16, step(LEAFG, min(5, k + 1)))
    return c


def planter_model(w, style, bloom):
    at = w.atlas(f"planter_{style}")
    m = MB(at, f"planter_{style}_{bloom}", ao=True)
    if style == "stone":
        m.box((1, 0, 2), (15, 10, 14), {"U": M_soil_top, "*": M_moulded(STONE)}, faces="NESWU", key="trough")
    else:
        m.box((1, 0, 2), (15, 10, 14), {"U": M_wood_planter, "*": M_wood_planter}, faces="NESWU", key="wood")
    extra = {}
    if bloom == "shrub":
        leaf = {"*": M_leafy}
        m.box((2, 10, 3.5), (14, 16, 12.5), leaf, faces="NESWU", key="hedge1")
        m.box((3.5, 16, 5), (12.5, 19, 11), leaf, faces="NESWU", key="hedge2")
    else:
        bt = b(f"bloom_{bloom}")
        extra["bloom"] = bt
        m.var(f"bloom_{bloom}")
        v = m.vars[f"bloom_{bloom}"]
        for zc in (5.5, 10.5):
            m.raw(E((1.5, 10, zc), (14.5, 22, zc), {"N": (v, [0, 6, 16, 16]), "S": (v, [16, 6, 0, 16])}, override="up"))
        for xc in (5.5, 10.5):
            m.raw(E((xc, 10, 2.5), (xc, 22, 13.5), {"E": (v, [2, 6, 14, 16]), "W": (v, [14, 6, 2, 16])}, override="up"))
    mod = m.build(ao=False)
    return mod


def M_leafy(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = rng.choice([1, 2, 2, 3])
            c.set(x + xx, y + yy, step(LEAFG, k))
    for _ in range(max(6, w * h // 3)):
        cx, cy = rng.randrange(w), rng.randrange(h)
        k = rng.choice([3, 3, 4]) + (1 if face == "U" else 0)
        for (dx, dy) in ((0, 0), (1, 0), (0, 1)):
            c.set(x + cx + dx, y + cy + dy, step(LEAFG, k))
        c.set(x + cx - 1, y + cy - 1, step(LEAFG, min(5, k + 1)))


# ----- wall lantern ----------------------------------------------------------------------------------------------------

def wall_lantern_glass():
    """4x8 side at (0,0), top/bottom 4x4 at (5,0) - the dim glass when the glow is off, with iron mullions."""
    c = Canvas()
    for y in range(8):
        for x in range(4):
            edge = x in (0, 3) or y in (0, 7)
            c.set(x, y, step(FORGE, 3 if (x == 0 or y == 0) else 1) if (x in (0, 3) and False) else mul(rgb("#8a5a1c"), 0.5 + 0.06 * (y % 2)))
    for y in range(8):
        c.set(0, y, mul(rgb("#8a5a1c"), 0.42)); c.set(3, y, mul(rgb("#8a5a1c"), 0.36))
    for x in range(4):
        c.set(5 + x, 0, mul(rgb("#8a5a1c"), 0.5))
        for y in range(4):
            c.set(5 + x, y, mul(rgb("#8a5a1c"), 0.45))
    return c


def wall_lantern_glow_frames():
    hi, lo = GLOW["amber"]
    out = []
    for f in range(6):
        c = Canvas()
        amp = FLICKER[f]
        hx, hy = ((1, 3), (2, 3), (2, 4), (1, 4), (1, 3), (2, 4))[f]
        for y in range(8):
            for x in range(4):
                t = min(1.0, math.hypot((x - 1.5) / 1.8, (y - 4.2) / 4.2))
                col = lerp(hi, lo, t * 0.85)
                if (x, y) == (hx, hy):
                    col = lerp(col, (255, 252, 214), 0.6)
                c.set(x, y, mul(col, amp))
        for x in range(4):
            for y in range(4):
                c.set(5 + x, y, mul(lerp(hi, lo, 0.55), amp * 0.9))
        out.append(c)
    return out


def wall_lantern_model(w):
    at = w.atlas("wall_lantern")
    m = MB(at, "wall_lantern", ao=False)
    iron = M_iron(FORGE, 4)
    m.box((6, 9, 15), (10, 15, 16), iron, faces="NEWUD", key="plate")
    m.box((7.4, 12.2, 8.5), (8.6, 13.6, 15), iron, faces="NEWUD", key="arm")
    m.box((7.4, 11, 8.5), (8.6, 12.2, 9.7), iron, faces="NEWS", key="drop")
    m.box((7.6, 10.4, 5.5), (8.4, 11.4, 9.5), iron, faces="NEWUD", key="hang")
    m.box((4.8, 11, 4.8), (11.2, 11.8, 11.2), iron, faces="NESWUD", key="cap1")
    m.box((6, 11.8, 6), (10, 12.6, 10), iron, faces="NESWU", key="cap2")
    m.box((7.2, 12.6, 7.2), (8.8, 13.4, 8.8), iron, faces="NESWU", key="cap3")
    m.box((4.8, 2.4, 4.8), (11.2, 3.2, 11.2), iron, faces="NESWUD", key="base")
    for (x, z) in ((4.8, 4.8), (10.2, 4.8), (4.8, 10.2), (10.2, 10.2)):
        m.box((x, 3.2, z), (x + 1, 11, z + 1), iron, faces="NESW", key="post")
    glass = m.var("wall_lantern_glass")
    glow = m.var("wall_lantern_glow")
    sides = {f_: (glass, [0, 0, 4, 8]) for f_ in "NESW"}
    m.raw(E((6, 3.2, 6), (10, 11, 10), {**sides, "D": (glass, [5, 0, 9, 4]), "U": (glass, [5, 0, 9, 4])}))
    m.raw(E((6, 3.2, 6), (10, 11, 10), {**{f_: (glow, [0, 0, 4, 8]) for f_ in "NESW"}, "D": (glow, [5, 0, 9, 4]), "U": (glow, [5, 0, 9, 4])},
            emit=15, override="up"))
    return m.build(ao=False)


# ----- notice board ----------------------------------------------------------------------------------------------------

def M_notices(c, x, y, w, h, face, rng, dims):
    """A framed board crowded with pinned papers (cream, white, one red seal)."""
    for yy in range(h):
        for xx in range(w):
            edge = xx in (0, w - 1) or yy in (0, h - 1)
            c.set(x + xx, y + yy, step(SPRUCE_DARK, 4 if (xx == 0 or yy == 0) else 2) if edge else step(SPRUCE, rng.choice([1, 2, 2])))
    papers = [(1, 1, 4, 5, 4), (6, 1, 3, 4, 5), (10, 2, 4, 4, 3), (2, 7, 3, 4, 3), (6, 6, 4, 4, 4), (11, 7, 3, 3, 5)]
    for i, (px, py, pw, ph, k) in enumerate(papers):
        if px + pw > w - 1 or py + ph > h - 1:
            pw, ph = min(pw, w - 1 - px), min(ph, h - 1 - py)
        for yy in range(py, py + ph):
            for xx in range(px, px + pw):
                kk = k + (1 if yy == py else 0) - (1 if (xx == px + pw - 1 or yy == py + ph - 1) else 0)
                c.set(x + xx, y + yy, step(LINEN, kk))
        for ln in range(py + 1, py + ph - 1, 2):                      # lines of writing as grey dashes
            for xx in range(px + 1, px + pw - 1):
                if (xx + ln + i) % 3:
                    c.set(x + xx, y + ln, step(WEATHERED, 1))
        c.set(x + px + pw // 2, y + py, rgb("#a82820"))               # the pin
    c.set(x + 7, y + 8, rgb("#a82820")); c.set(x + 8, y + 8, rgb("#c84438")); c.set(x + 7, y + 9, rgb("#8a1c18")); c.set(x + 8, y + 9, rgb("#a82820"))


def notice_model(w):
    at = w.atlas("notice_board")
    m = MB(at, "notice_board", ao=True)
    post = M_planks(SPRUCE, board=2, orient="v", tone=(2, 3, 3))
    m.box((1, 0, 7), (3, 15, 9), post, faces="NESWU", key="post")
    m.box((13, 0, 7), (15, 15, 9), post, faces="NESWU", key="post")
    m.box((2, 4, 7), (14, 15, 9), {"N": M_notices, "*": M_planks(SPRUCE, board=2, orient="h")}, faces="NESWU", key="board")
    m.box((0.5, 15, 6), (15.5, 16, 10), M_planks(DARKOAK, board=2, orient="h"), faces="NESWUD", key="roof")
    m.box((1.5, 16, 7), (14.5, 17, 9), M_planks(DARKOAK, board=2, orient="h"), faces="NESWU", key="roof2")
    return m.build()


# ----- fishing net -----------------------------------------------------------------------------------------------------

def fishing_net():
    rng = rng_for("city_street", "fishing_net")
    c = Canvas()
    cork = R("#4a3010", "#7a5420", "#a87a34", "#cca056")
    for x in range(16):                                       # the head rope
        c.set(x, 0, step(HEMP, 4 if x % 2 else 3))
        c.set(x, 1, step(HEMP, 2))
    for x in (1, 5, 9, 13):                                    # cork floats
        for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            c.set(x + dx, 1 + dy, step(cork, 3 if dx + dy == 0 else 2 if dx + dy == 1 else 1))
        c.set(x, 1, step(cork, 3))
    for y in range(2, 16):
        # the net sags: the bottom edge is a shallow curve and the mesh narrows
        for x in range(16):
            a = (x + y) % 4
            b_ = (x - y) % 4
            on = a == 0 or b_ == 0
            if not on:
                continue
            edge = y > 12 and (x < (y - 12) or x > 15 - (y - 12))
            if edge:
                continue
            k = 3 + (1 if (a == 0 and b_ == 0) else 0)
            if y > 12 and x % 3 == 0 and rng.random() < 0.5:
                continue                                         # ragged lower edge
            if rng.random() < 0.15:
                k += rng.choice([-1, 0])
            c.set(x, y, step(HEMP, k))
    for x in (4, 12):                                          # a lead weight pulled into the mesh
        c.set(x, 14, step(STEEL6, 2)); c.set(x, 15, step(STEEL6, 1))
    return c


# ----- windlass --------------------------------------------------------------------------------------------------------

def M_rope_wrap(c, x, y, w, h, face, rng, dims):
    for yy in range(h):
        for xx in range(w):
            k = 3 + (1 if (xx + yy) % 3 == 0 else -1 if (xx + yy) % 3 == 1 else 0)
            if face in "UD":
                k -= 1
            c.set(x + xx, y + yy, step(HEMP, k))


def windlass_model(w):
    at = w.atlas("well_windlass")
    m = MB(at, "well_windlass", ao=False)
    wood = M_planks(SPRUCE, board=2, orient="h", tone=(2, 3, 4))
    m.box((1, 2, 6), (15, 8, 10), wood, faces="NSUD", key="d1")
    m.box((1, 3, 5), (15, 7, 11), wood, faces="NSUD", key="d2")
    m.box((4, 1.6, 5.6), (12, 8.4, 10.4), M_rope_wrap, faces="NSUD", key="wrap")
    m.box((3.6, 2.6, 4.6), (12.4, 7.4, 11.4), M_rope_wrap, faces="NSU", key="wrap2")
    iron = M_iron(FORGE, 3)
    m.box((0, 3, 6.5), (1, 7, 9.5), iron, faces="NESWUD", key="capL")
    m.box((15, 3, 6.5), (16, 7, 9.5), iron, faces="NESWUD", key="capR")
    m.box((14.6, 4.6, 7.4), (16, 5.6, 8.6), iron, faces="NESWUD", key="crank0")
    m.box((14.4, 5.6, 7.2), (15.6, 11.2, 8.8), iron, faces="NESWUD", key="crank1")
    m.box((14.4, 10.2, 6.8), (16, 11.6, 9.2), M_planks(DARKOAK, board=2, orient="h"), faces="NESWUD", key="crank2")
    # the bucket hangs from the drum on a rope
    m.box((7.5, -2.5, 7.5), (8.5, 2.4, 8.5), M_solid(HEMP, 3), faces="NESW", key="rope")
    m.box((6, -6, 6), (10, -2.5, 10), M_planks(SPRUCE, board=2, orient="v", tone=(2, 3)), faces="NESWUD", key="bucket")
    m.box((5.8, -5, 5.8), (10.2, -4.2, 10.2), M_iron(FORGE, 3), faces="NESW", key="bh1")
    m.box((5.8, -3.4, 5.8), (10.2, -2.8, 10.2), M_iron(FORGE, 3), faces="NESW", key="bh2")
    return m.build(ao=False)


# ----- mooring ring ----------------------------------------------------------------------------------------------------

def mooring_ring_tex():
    c = Canvas()
    cx = cy = 3.5
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - cx, y - cy)
            if 2.7 <= d <= 4.1:
                lit = (x + y) < 7
                c.set(x, y, step(FORGE, 4 if lit and d < 3.4 else 3 if lit else 2 if d < 3.6 else 1))
    return c


def mooring_model(w):
    t = {"particle": b("mooring_ring"), "ring": b("mooring_ring"), "iron": b("street_iron")}
    els = [E((4, 4, 14.4), (12, 12, 15.6), {"N": ("#ring", [0, 0, 8, 8]), "S": ("#ring", [8, 0, 0, 8])}),
           E((7, 11.4, 15), (9, 13.6, 16), {"N": ("#iron", [0, 0, 2, 2]), "E": ("#iron", [0, 0, 1, 2]), "W": ("#iron", [1, 0, 2, 2]), "U": ("#iron", [0, 0, 2, 1])}),
           E((7.2, 11, 14.2), (8.8, 12.6, 15.2), {"N": ("#iron", [0, 4, 2, 6]), "E": ("#iron", [0, 4, 1, 6]), "W": ("#iron", [0, 4, 1, 6]), "U": ("#iron", [0, 0, 2, 1])})]
    return {"parent": PARENT, "ambientocclusion": False, "textures": t, "elements": els}


@gen
def g_rest(w):
    w.model("bench", finish(w, bench_model(w)))
    w.state("bench", {f"facing={f}": rot_variant("bench", f) for f in FACINGS})
    w.texture("planter_shrub", planter_shrub())
    vs = {}
    for style in ("wood", "stone"):
        for bloom in BLOOMS:
            name = f"planter_{style}_{bloom}"
            w.model(name, finish(w, planter_model(w, style, bloom)))
            vs[f"bloom={bloom},style={style}"] = {"model": b(name)}
    w.state("planter", vs)
    w.texture("wall_lantern_glass", wall_lantern_glass())
    w.animated("wall_lantern_glow", wall_lantern_glow_frames(), 3, True)
    w.model("wall_lantern", finish(w, wall_lantern_model(w)))
    w.state("wall_lantern", {f"facing={f}": rot_variant("wall_lantern", f) for f in FACINGS})
    w.model("notice_board", finish(w, notice_model(w)))
    w.state("notice_board", {f"facing={f}": rot_variant("notice_board", f) for f in FACINGS})
    w.texture("fishing_net", fishing_net())
    w.model("fishing_net", finish(w, {"parent": PARENT, "ambientocclusion": False, "textures": {"particle": b("fishing_net"), "net": b("fishing_net")},
                                      "elements": [E((0, 0, 8), (16, 16, 8), {"N": ("#net", [0, 0, 16, 16]), "S": ("#net", [16, 0, 0, 16])})]}))
    w.state("fishing_net", {"axis=x": {"model": b("fishing_net")}, "axis=z": {"model": b("fishing_net"), "y": 90}})
    w.model("well_windlass", finish(w, windlass_model(w)))
    w.state("well_windlass", {"axis=x": {"model": b("well_windlass")}, "axis=z": {"model": b("well_windlass"), "y": 90}})
    w.texture("mooring_ring", mooring_ring_tex())
    w.model("mooring_ring", finish(w, mooring_model(w)))
    w.state("mooring_ring", {f"facing={f}": rot_variant("mooring_ring", f) for f in FACINGS})


# ---------------------------------------------------------------------------------------------------------------------
# the citizens' street lamp glass (already in the game): the same design as citizens.py street_lamp_glass_lit, now with a
# six-frame flicker (brightness +-12%, the flame's hot pixel moving a pixel)

LAMP_BRONZE = R("#2c1e0c", "#4f3512", "#76501a", "#a07426", "#c8983a", "#e8c86a")
LAMP_AMBER = R("#4a2a06", "#7a460b", "#b26a12", "#e08f1f", "#f2a541", "#ffd28a")
LAMP_HOT = [(3, 4), (3, 3), (3, 4), (2, 4), (3, 4), (3, 3)]


def street_lamp_glass_frames():
    out = []
    for f in range(6):
        k = FLICKER[f]
        c = Canvas()
        for y in range(8):
            for x in range(6):
                if x in (0, 5) or y in (0, 7):
                    c.set(x, y, step(LAMP_BRONZE, 4 if (x == 0 or y == 0) else 2))
                    continue
                d = math.hypot(x - 2.5, y - 4.2)
                i = 5 if d < 1.2 else 4 if d < 2.2 else 3
                c.set(x, y, mul(LAMP_AMBER[i], k))
        for (x, y, h) in ((3, 3, "#fff2b0"), (2, 4, "#ffd870"), (3, 4, "#fffbe0"), (2, 5, "#ffb640"), (3, 5, "#ffe590"),
                          (2, 6, "#5a3a1a"), (3, 6, "#3a2410")):
            c.set(x, y, mul(rgb(h), 0.5 + 0.5 * k) if h not in ("#5a3a1a", "#3a2410") else rgb(h))
        hx, hy = LAMP_HOT[f]
        c.set(hx, hy, rgb("#fffdf0"))
        if (hx, hy) != (3, 4):
            c.set(3, 4, rgb("#ffe890"))
        c.set(1, 6, mul(LAMP_AMBER[4], k)); c.set(4, 6, mul(LAMP_AMBER[4], k))
        for y in range(6, 10):
            for x in range(6, 10):
                d = math.hypot(x - 7.5, y - 7.5)
                c.set(x, y, mul(LAMP_AMBER[5 if d < 1.4 else 4 if d < 2.2 else 3], k))
        for y in range(16):
            for x in range(16):
                if c.get(x, y)[3] == 0:
                    c.set(x, y, mul(LAMP_AMBER[3], k))
        out.append(c)
    return out


@gen
def g_lamp(w):
    w.animated("street_lamp_glass_lit", street_lamp_glass_frames(), 3, True)


# @@ GENERATORS END @@

# ---------------------------------------------------------------------------------------------------------------------
# registry

FACINGS = ["north", "east", "south", "west"]
AWN_COLOURS = ["red", "blue", "yellow", "green", "orange", "plum"]
AWN_SHAPES = ["slope", "upper", "lower", "flat"]
TRADES = ["bread", "fish", "produce", "cloth", "pottery", "candles", "flowers", "spice", "tools", "cheese", "tavern",
          "boots", "books", "keys"]
DESIGNS = ["bellwatch", "quay", "market", "terrace", "works", "garden", "wallring", "association"]
GOODS = ["bread", "fish", "produce", "cloth", "pottery", "candles", "flowers", "spice", "tools", "cheese"]
SHELF_GOODS = ["jars", "cloth", "bread", "pots", "books", "tools"]
BLOOMS = ["red", "yellow", "blue", "pink", "white", "herbs", "shrub"]
# the Java properties the builder writes (the brief's table); the blockstates must cover exactly their product
PROPS = {
    "awning": {"facing": FACINGS, "colour": AWN_COLOURS, "shape": AWN_SHAPES},
    "sign_bracket": {"facing": FACINGS, "part": ["arm", "end"]},
    "signboard": {"facing": FACINGS, "trade": TRADES, "mount": ["hanging", "flat"]},
    "hang_line": {"axis": ["x", "z"], "hang": ["rope", "laundry", "bunting", "lanterns"], "set": ["0", "1", "2", "3"],
                  "sag": ["0", "1", "2"]},
    "banner_cloth": {"facing": FACINGS, "design": DESIGNS, "part": ["top", "middle", "bottom"], "mount": ["wall", "pole"]},
    "flagpole": {"facing": FACINGS},
    "line_pole": {"top": ["false", "true"]},
    "crate": {"content": ["lid", "apples", "cabbages", "fish", "straw", "bottles"]},
    "sack": {"count": ["1", "2", "3"], "fill": ["grain", "flour", "wool"]},
    "wares": {"good": GOODS},
    "stall_counter": {"facing": FACINGS, "open": ["false", "true"]},
    "cask": {"orient": ["upright", "x", "z"]},
    "cart": {"facing": FACINGS, "part": ["body", "shafts"], "load": ["empty", "crates", "sacks", "casks", "produce"]},
    "hay_cart": {"facing": FACINGS, "part": ["body", "shafts"]},
    "bench": {"facing": FACINGS},
    "market_table": {"axis": ["x", "z"]},
    "goods_shelf": {"facing": FACINGS, "goods": SHELF_GOODS},
    "produce": {"kind": ["apples", "cabbages", "carrots", "pumpkins", "potatoes", "flowers"]},
    "planter": {"style": ["wood", "stone"], "bloom": BLOOMS},
    "wall_lantern": {"facing": FACINGS},
    "notice_board": {"facing": FACINGS},
    "fishing_net": {"axis": ["x", "z"]},
    "well_windlass": {"axis": ["x", "z"]},
    "mooring_ring": {"facing": FACINGS},
}
LANG = {
    "awning": "Awning", "sign_bracket": "Sign Bracket", "signboard": "Signboard", "hang_line": "Hang Line",
    "banner_cloth": "Banner Cloth", "flagpole": "Flagpole", "line_pole": "Line Pole", "crate": "Crate", "sack": "Sack",
    "wares": "Wares", "stall_counter": "Stall Counter", "cask": "Cask", "cart": "Cart", "hay_cart": "Hay Cart",
    "bench": "Bench", "market_table": "Market Table", "goods_shelf": "Goods Shelf", "produce": "Produce",
    "planter": "Planter", "wall_lantern": "Wall Lantern", "notice_board": "Notice Board", "fishing_net": "Fishing Net",
    "well_windlass": "Well Windlass", "mooring_ring": "Mooring Ring",
}
AXE = ["awning", "signboard", "hang_line", "banner_cloth", "flagpole", "line_pole", "crate", "sack", "wares",
       "stall_counter", "cask", "cart", "hay_cart", "bench", "market_table", "goods_shelf", "produce", "planter",
       "notice_board", "fishing_net", "well_windlass"]
PICKAXE = ["sign_bracket", "wall_lantern", "mooring_ring"]
RESKINS = ["laundry_1", "laundry_2", "laundry_3", "laundry_4", "laundry_5", "laundry_6", "street_lamp_glass_lit"]

_WORLD = None


def everything():
    global _WORLD
    if _WORLD is None:
        w = World()
        for g in GENERATORS:
            g(w)
        _WORLD = w
    return _WORLD


def textures():
    return everything().tex


def build(out):
    w = everything()
    for name, c in w.tex.items():
        if isinstance(c, list):
            ft, interp = w.meta[name]
            out.block(name, c, ft)
            path = os.path.join(out.root, "assets/awaken/textures/block", name + ".png.mcmeta")
            with open(path, "w") as m:
                json.dump({"animation": {"frametime": ft, "interpolate": interp}}, m)
        else:
            out.block(name, c)


# ---------------------------------------------------------------------------------------------------------------------
# content: blockstates, models, lang, tags

WRITTEN = ck.WRITTEN


def content():
    w = everything()
    for name, m in w.models.items():
        ck.model(name, m)
    for blk, variants in w.states.items():
        ck.blockstate(blk, variants)
    ck._merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v for k, v in LANG.items()})
    ck._merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/axe.json"), {"values": [f"awaken:{n}" for n in AXE]})
    ck._merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
              {"values": [f"awaken:{n}" for n in PICKAXE]})
    print(f"city_street: content written ({len(w.models)} models, {len(w.states)} blockstates)")


def manifest():
    content()
    w = everything()
    paths = []
    for n, c in w.tex.items():
        paths.append(os.path.join(TEX, n + ".png"))
        if isinstance(c, list):
            paths.append(os.path.join(TEX, n + ".png.mcmeta"))
    paths += [p for p in dict.fromkeys(WRITTEN)]
    for p in paths:
        print(os.path.relpath(p, ROOT))


# ---------------------------------------------------------------------------------------------------------------------
# check

def _combos(props):
    keys = sorted(props)
    out = [()]
    for k in keys:
        out = [c + ((k, v),) for c in out for v in props[k]]
    return {frozenset(c) for c in out}


def _vkey(k):
    return frozenset(tuple(p.split("=")) for p in k.split(",")) if k else frozenset()


def _resolve(texmap, t):
    d = 0
    while isinstance(t, str) and t.startswith("#") and d < 8:
        t = texmap.get(t[1:])
        d += 1
    return t


def check():
    from PIL import Image
    errors, notes = [], []
    w = everything()
    used_models = set()
    n_variants = 0
    for blk, pr in PROPS.items():
        p = os.path.join(ASSETS, "blockstates", blk + ".json")
        if not os.path.exists(p):
            errors.append(f"missing blockstate {blk}")
            continue
        variants = json.load(open(p))["variants"]
        want = _combos(pr)
        have = {_vkey(k) for k in variants}
        if len(have) != len(variants):
            errors.append(f"{blk}: duplicate variant keys")
        if want - have:
            errors.append(f"{blk}: {len(want - have)} property combinations without a variant, e.g. {sorted(next(iter(want - have)))}")
        if have - want:
            errors.append(f"{blk}: {len(have - want)} variants the block can't have, e.g. {sorted(next(iter(have - want)))}")
        for k, v in variants.items():
            n_variants += 1
            for e in (v if isinstance(v, list) else [v]):
                used_models.add(e["model"])
        notes.append(f"{blk}: {len(variants)} variants ({'x'.join(str(len(v)) for v in pr.values())} = {len(want)})")
    for blk in w.states:
        if blk not in PROPS:
            errors.append(f"blockstate {blk} has no entry in PROPS")
    cache = {}
    for ref in sorted(used_models):
        ns, path = ref.split(":")
        p = os.path.join(ASSETS, "models", path + ".json")
        if not os.path.exists(p):
            errors.append(f"missing model {ref}")
            continue
        m = json.load(open(p))
        cache[ref] = m
        parent = m.get("parent")
        if parent:
            pns, ppath = parent.split(":") if ":" in parent else ("minecraft", parent)
            pp = os.path.join(ASSETS if pns == "awaken" else VANILLA, "models", ppath + ".json")
            if not os.path.exists(pp):
                errors.append(f"{ref}: parent {parent} missing")
        texmap = dict(m.get("textures", {}))
        cut = m.get("render_type") == CUT
        for k in texmap:
            t = _resolve(texmap, texmap[k])
            if t is None or t.startswith("#"):
                errors.append(f"{ref}: texture key {k} does not resolve")
                continue
            tns, tpath = t.split(":")
            tp = os.path.join(ASSETS if tns == "awaken" else VANILLA, "textures", tpath + ".png")
            if not os.path.exists(tp):
                errors.append(f"{ref}: texture {t} not found (run build_all.py city_street)")
        if "elements" not in m:
            errors.append(f"{ref}: no elements")
            continue
        for ei, el in enumerate(m["elements"]):
            for vec in (el["from"], el["to"]):
                if any(not (-16 <= v <= 32) for v in vec):
                    errors.append(f"{ref}: element {ei} outside -16..32: {vec}")
            if "rotation" in el:
                r = el["rotation"]
                if r.get("axis") not in ("x", "y", "z"):
                    errors.append(f"{ref}: element {ei} bad rotation axis")
            for dname, f in el["faces"].items():
                t = _resolve(texmap, f["texture"])
                if t is None or t.startswith("#"):
                    errors.append(f"{ref}: element {ei} face {dname} texture {f['texture']} unresolved")
                    continue
                uv = f.get("uv", [0, 0, 16, 16])
                if any(not (0 <= u <= 16) for u in uv):
                    errors.append(f"{ref}: element {ei} face {dname} uv {uv} outside 0..16")
                tns, tpath = t.split(":")
                tp = os.path.join(ASSETS if tns == "awaken" else VANILLA, "textures", tpath + ".png")
                if os.path.exists(tp) and tns == "awaken" and not cut:
                    im = Image.open(tp).convert("RGBA")
                    fh = im.width
                    nfr = im.height // fh
                    x0, x1 = sorted((int(math.floor(uv[0] + 1e-6)), int(math.ceil(uv[2] - 1e-6))))
                    y0, y1 = sorted((int(math.floor(uv[1] + 1e-6)), int(math.ceil(uv[3] - 1e-6))))
                    bad = False
                    for fr in range(nfr):
                        for yy in range(y0, max(y1, y0 + 1)):
                            for xx in range(x0, max(x1, x0 + 1)):
                                if im.getpixel((min(xx, 15), fr * fh + min(yy, 15)))[3] < 255:
                                    bad = True
                    if bad:
                        errors.append(f"{ref}: element {ei} face {dname} samples a transparent texel of {t} but the model is not cutout")
    notes.append(f"{len(used_models)} models referenced, {n_variants} variants")
    # textures written, sizes, animations
    nanim = 0
    for name, c in w.tex.items():
        p = os.path.join(TEX, name + ".png")
        if not os.path.exists(p):
            errors.append(f"texture {name}.png not written (run build_all.py city_street)")
            continue
        im = Image.open(p)
        if isinstance(c, list):
            nanim += 1
            if im.size != (16, 16 * len(c)):
                errors.append(f"animated texture {name} is {im.size}, expected 16x{16 * len(c)}")
            mp = p + ".mcmeta"
            if not os.path.exists(mp):
                errors.append(f"{name}: missing .mcmeta")
            else:
                a = json.load(open(mp))["animation"]
                ft, interp = w.meta[name]
                if a.get("frametime") != ft or a.get("interpolate") != interp:
                    errors.append(f"{name}: .mcmeta is {a}, expected frametime {ft} interpolate {interp}")
        elif im.size != (16, 16):
            errors.append(f"texture {name} is {im.size}")
    notes.append(f"{len(w.tex)} textures ({nanim} animated)")
    # models on disk equal the generated ones
    for name, m in w.models.items():
        p = os.path.join(ASSETS, "models/block", name + ".json")
        if not os.path.exists(p):
            errors.append(f"model {name}.json not written (run content)")
        elif json.load(open(p)) != json.loads(json.dumps(m)):
            errors.append(f"model {name}.json differs from the generator (run content)")
    lang = json.load(open(os.path.join(MERGE, "assets/awaken/lang/en_us.json")))
    for k in LANG:
        if f"block.awaken.{k}" not in lang:
            errors.append(f"lang block.awaken.{k} missing")
    for tag, names in (("axe", AXE), ("pickaxe", PICKAXE)):
        vals = json.load(open(os.path.join(MERGE, f"data/minecraft/tags/block/mineable/{tag}.json")))["values"]
        for n in names:
            if f"awaken:{n}" not in vals:
                errors.append(f"tag {tag} lacks {n}")
    for n in notes:
        print("  ", n)
    if errors:
        print("CHECK FAILED:")
        for e in errors[:80]:
            print("  -", e)
        print(f"  ({len(errors)} problems)")
        return 1
    print("check: ok")
    return 0


# ---------------------------------------------------------------------------------------------------------------------
# contact sheet

def make_sheet(out, prefixes=()):
    from PIL import Image, ImageDraw
    w = everything()
    S, GAP, MAXW = int(os.environ.get("SHEET_S", "4")), 10, 1900
    items = []
    for name, c in w.tex.items():
        if prefixes and not any(name.startswith(p) for p in prefixes):
            continue
        frames = c if isinstance(c, list) else [c]
        items.append((name + (f"  x{len(frames)} @{w.meta[name][0]}" if isinstance(c, list) else ""), [f.image() for f in frames]))
    placed, x, y, rowh = [], GAP, GAP, 0
    for lab, ims in items:
        wd = (len(ims) * 16 + (len(ims) - 1) * 2) * S
        ht = 16 * S
        if x + wd + GAP > MAXW:
            x, y, rowh = GAP, y + rowh + GAP + 56, 0
        placed.append((lab, ims, x, y))
        x += wd + GAP
        rowh = max(rowh, ht)
    H = y + rowh + GAP + 60
    sheet = Image.new("RGBA", (MAXW, H), (36, 38, 48, 255))
    d = ImageDraw.Draw(sheet)
    for lab, ims, px, py in placed:
        for i, im in enumerate(ims):
            ox = px + i * 18 * S
            chk = Image.new("RGBA", (16 * S, 16 * S))
            cd = ImageDraw.Draw(chk)
            for yy in range(0, 16 * S, S):
                for xx in range(0, 16 * S, S):
                    v = 112 + (12 if ((xx // S + yy // S) % 2) else 0)
                    cd.rectangle([xx, yy, xx + S - 1, yy + S - 1], fill=(v, v, v + 6, 255))
            sheet.paste(chk, (ox, py))
            big = im.resize((16 * S, 16 * S), Image.NEAREST)
            sheet.paste(big, (ox, py), big)
        words = lab.split("  ")[0]
        parts = [words[i:i + 12] for i in range(0, min(len(words), 36), 12)]
        for li, part in enumerate(parts):
            d.text((px, py + 16 * S + 2 + li * 11), part, fill=(226, 228, 236, 255))
        if "  " in lab:
            d.text((px, py + 16 * S + 2 + len(parts) * 11), lab.split("  ", 1)[1].strip(), fill=(160, 200, 255, 255))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out, len(items), "textures")


# ---------------------------------------------------------------------------------------------------------------------
# the renderer: reads the real blockstate and model data (in memory), projects every element face orthographically and
# rasterises it with a depth buffer, MC's directional shading, cutout transparency and emissive elements at full bright

SHADE = {"U": 1.0, "D": 0.5, "N": 0.8, "S": 0.8, "E": 0.6, "W": 0.6}
FNORM = {"N": (0, 0, -1), "S": (0, 0, 1), "E": (1, 0, 0), "W": (-1, 0, 0), "U": (0, 1, 0), "D": (0, -1, 0)}
LETTER = {v: k for k, v in FACE_NAMES.items()}


def _corners(a, b_, f):
    x0, y0, z0 = a
    x1, y1, z1 = b_
    return {"N": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "S": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "W": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "E": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "U": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "D": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[f]


def _rotp(p, axis, deg, origin):
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    a = math.radians(deg)
    cs, sn = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * cs - z * sn, y * sn + z * cs
    elif axis == "y":
        x, z = x * cs + z * sn, -x * sn + z * cs
    else:
        x, y = x * cs - y * sn, x * sn + y * cs
    return (x + origin[0], y + origin[1], z + origin[2])


def _rotv(v, axis, deg):
    return _rotp(v, axis, deg, (0, 0, 0))


_IMG = {}


def tex_image(ref, frame=0):
    """RGBA PIL image of a texture reference ('awaken:block/x' from memory, 'minecraft:block/x' from the vanilla assets)."""
    from PIL import Image
    key = (ref, frame)
    if key in _IMG:
        return _IMG[key]
    ns, path = ref.split(":")
    if ns == "awaken":
        nm = path.split("block/", 1)[1]
        if nm in everything().tex:
            t = everything().tex[nm]
            c = t[frame % len(t)] if isinstance(t, list) else t
            im = c.image()
        else:
            im = Image.open(os.path.join(TEX, nm + ".png")).convert("RGBA")
            im = im.crop((0, 0, 16, 16))
    else:
        im = Image.open(os.path.join(VANILLA, "textures", path + ".png")).convert("RGBA")
        im = im.crop((0, 0, im.width, im.width))
        if im.width != 16:
            im = im.resize((16, 16), Image.NEAREST)
    _IMG[key] = im
    return im


def resolve_state(block, props):
    w = everything()
    want = {k: str(v) for k, v in props.items()}
    for k, v in w.states[block].items():
        have = dict(p.split("=") for p in k.split(",")) if k else {}
        if all(have.get(p) == q for p, q in want.items()) and set(have) == set(want):
            return v[0] if isinstance(v, list) else v
    raise KeyError(f"{block} {props}")


MOCK_MODELS = {}


def model_quads(model_ref, xrot, yrot, offset, frame=0):
    """Quads (4 corner points, 4 uv, image, shade, emit) of a model, rotated by the blockstate and placed at offset (px)."""
    w = everything()
    if model_ref.startswith("mock:"):
        m = MOCK_MODELS[model_ref[5:]]
    else:
        m = w.models[model_ref.split("block/", 1)[1]]
    texmap = m["textures"]
    quads = []
    for el in m["elements"]:
        a, b_ = el["from"], el["to"]
        emit = el.get("light_emission", 0)
        noshade = el.get("shade") is False or el.get("shade_direction_override")
        for dname, f in el["faces"].items():
            L = LETTER[dname]
            pts = _corners(a, b_, L)
            nrm = FNORM[L]
            if "rotation" in el:
                r = el["rotation"]
                pts = [_rotp(p, r["axis"], r["angle"], r["origin"]) for p in pts]
                nrm = _rotv(nrm, r["axis"], r["angle"])
            if xrot:
                pts = [_rotp(p, "x", -xrot, (8, 8, 8)) for p in pts]
                nrm = _rotv(nrm, "x", -xrot)
            if yrot:
                pts = [_rotp(p, "y", -yrot, (8, 8, 8)) for p in pts]
                nrm = _rotv(nrm, "y", -yrot)
            ax = max(range(3), key=lambda i: abs(nrm[i]))
            near = {(0, 1): "E", (0, -1): "W", (1, 1): "U", (1, -1): "D", (2, 1): "S", (2, -1): "N"}[(ax, 1 if nrm[ax] > 0 else -1)]
            sh = 1.0 if noshade else SHADE[near]
            ref = _resolve(texmap, f["texture"])
            img = tex_image(ref, frame)
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for _ in range(f.get("rotation", 0) // 90):
                uvs = [uvs[3], uvs[0], uvs[1], uvs[2]]
            pts = [(p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]) for p in pts]
            quads.append((pts, uvs, img, sh, emit, nrm))
    return quads


def placed_block(block, props, cell, frame=0):
    v = resolve_state(block, props)
    return model_quads(v["model"], v.get("x", 0), v.get("y", 0), (cell[0] * 16, cell[1] * 16, cell[2] * 16), frame)


def mock_box(cell, a, b_, tex, shade=True, faces="NESWUD"):
    """A stand-in cuboid (vanilla texture) for scenery: posts, walls."""
    fd = {}
    for L in faces:
        fd[FACE_NAMES[L]] = {"uv": [0, 0, 16, 16], "texture": "#t"}
    m = {"textures": {"t": tex}, "elements": [{"from": list(a), "to": list(b_), "faces": fd}]}
    name = f"box{len(MOCK_MODELS)}"
    MOCK_MODELS[name] = m
    return model_quads("mock:" + name, 0, 0, (cell[0] * 16, cell[1] * 16, cell[2] * 16))


def render(quads, yaw=32, pitch=30, S=6, bg=(148, 188, 226), night=1.0, margin=10, label=None):
    from PIL import Image, ImageDraw
    ya, pa = math.radians(yaw), math.radians(pitch)
    v = (math.sin(ya) * math.cos(pa), math.sin(pa), -math.cos(ya) * math.cos(pa))
    r = (-math.cos(ya), 0.0, -math.sin(ya))
    u = (v[1] * r[2] - v[2] * r[1], v[2] * r[0] - v[0] * r[2], v[0] * r[1] - v[1] * r[0])
    if u[1] < 0:
        u = (-u[0], -u[1], -u[2])

    def proj(p):
        return (p[0] * r[0] + p[1] * r[1] + p[2] * r[2], -(p[0] * u[0] + p[1] * u[1] + p[2] * u[2]),
                p[0] * v[0] + p[1] * v[1] + p[2] * v[2])
    sq = []
    for q in quads:
        pts, uvs, img, sh, emit, nrm = q
        if nrm[0] * v[0] + nrm[1] * v[1] + nrm[2] * v[2] <= 1e-6:
            continue
        sq.append(([proj(p) for p in pts], uvs, img, sh, emit))
    if not sq:
        return Image.new("RGBA", (64, 64), bg + (255,))
    xs = [p[0] for q in sq for p in q[0]]
    ys = [p[1] for q in sq for p in q[0]]
    ox, oy = -min(xs) * S + margin, -min(ys) * S + margin
    W = int((max(xs) - min(xs)) * S + 2 * margin) + 1
    H = int((max(ys) - min(ys)) * S + 2 * margin) + 1
    buf = [bg + (255,)] * (W * H)
    zb = [-1e9] * (W * H)
    for pts, uvs, img, sh, emit in sq:
        px = img.load()
        iw, ih = img.size
        A = (pts[0][0] * S + ox, pts[0][1] * S + oy)
        B = (pts[1][0] * S + ox, pts[1][1] * S + oy)
        C = (pts[3][0] * S + ox, pts[3][1] * S + oy)
        e1 = (B[0] - A[0], B[1] - A[1])
        e2 = (C[0] - A[0], C[1] - A[1])
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        dA, dB, dC = pts[0][2], pts[1][2], pts[3][2]
        allx = [pt[0] * S + ox for pt in pts]
        ally = [pt[1] * S + oy for pt in pts]
        x0, x1 = max(0, int(math.floor(min(allx)))), min(W - 1, int(math.ceil(max(allx))))
        y0, y1 = max(0, int(math.floor(min(ally)))), min(H - 1, int(math.ceil(max(ally))))
        (ua, va), (ub, vb), (ud, vd) = uvs[0], uvs[1], uvs[3]
        light = 1.0 if emit else sh * night
        for y in range(y0, y1 + 1):
            dy = y + 0.5 - A[1]
            for x in range(x0, x1 + 1):
                dx = x + 0.5 - A[0]
                s = (dx * e2[1] - dy * e2[0]) / det
                t = (e1[0] * dy - e1[1] * dx) / det
                if s < 0 or s >= 1 or t < 0 or t >= 1:
                    continue
                d = dA + s * (dB - dA) + t * (dC - dA)
                i = y * W + x
                if d < zb[i] - 1e-4:
                    continue
                tu = ua + s * (ub - ua) + t * (ud - ua)
                tv = va + s * (vb - va) + t * (vd - va)
                cx = min(iw - 1, max(0, int(tu * iw / 16.0)))
                cy = min(ih - 1, max(0, int(tv * ih / 16.0)))
                col = px[cx, cy]
                if col[3] < 128:
                    continue
                zb[i] = d
                buf[i] = (min(255, int(col[0] * light)), min(255, int(col[1] * light)), min(255, int(col[2] * light)), 255)
    im = Image.new("RGBA", (W, H))
    im.putdata(buf)
    if label:
        ImageDraw.Draw(im).text((4, 2), label, fill=(20, 20, 30, 255))
    return im


# ----- scenes ------------------------------------------------------------------------------------------------------

def blocks_quads(items, frame=0):
    q = []
    for it in items:
        if it[0] == "box":
            q += mock_box(it[1], it[2], it[3], it[4])
        else:
            q += placed_block(it[0], it[1], it[2], frame)
    return q


SCENES = {}     # name -> function returning (items, render kwargs)


def view_cmd(out, block, kv):
    """One block, every value of the one property given as k=*, at the other properties' first values."""
    from PIL import Image
    pr = PROPS[block]
    fixed = {k: v[0] for k, v in pr.items()}
    sweep = None
    opts = {"S": 5, "yaw": 28, "pitch": 26, "night": 1.0, "gap": 1.25, "frame": 0}
    for a in kv:
        k, v = a.split("=")
        if k in opts:
            opts[k] = float(v) if k in ("night", "gap") else int(v)
        elif v == "*":
            sweep = k
        else:
            fixed[k] = v
    items = []
    vals = pr[sweep] if sweep else [None]
    for i, val in enumerate(vals):
        p = dict(fixed)
        if sweep:
            p[sweep] = val
        items.append((block, p, (i * opts["gap"], 0, 0)))
    im = render(blocks_quads(items, opts["frame"]), yaw=opts["yaw"], pitch=opts["pitch"], S=opts["S"], night=opts["night"])
    im.save(out)
    print(out, im.size)


# ----- the scene library (stand-in scenery uses vanilla and kit textures) ----------------------------------------------

PLASTER = "awaken:block/plaster_cream_0"
ASH = "awaken:block/ashlar_0"
TIMB = "awaken:block/timber_dark_side_0"
COBBLE = "minecraft:block/stone_bricks"
PATH = "minecraft:block/cobblestone"


def wall(x0, x1, y0, y1, z=1, tex=PLASTER):
    return [("box", (x, y, z), (0, 0, 0), (16, 16, 16), tex) for x in range(x0, x1) for y in range(y0, y1)]


def floor(x0, x1, z0, z1, y=-1, tex=PATH):
    return [("box", (x, y, z), (0, 0, 0), (16, 16, 16), tex) for x in range(x0, x1) for z in range(z0, z1)]


def post(x, y, z, h=16, tex="minecraft:block/stripped_dark_oak_log", px=6.5, pz=6.5):
    return ("box", (x, y, z), (px, 0, pz), (px + 3, h, pz + 3), tex)


def scene_awnings():
    items = floor(-1, 7, -1, 2) + wall(-1, 7, 0, 4)
    items += [("box", (x, 0, 1), (0, 0, 0), (16, 16, 16), ASH) for x in range(-1, 7)]
    for i, colour in enumerate(AWN_COLOURS):
        items.append(("awning", {"facing": "north", "colour": colour, "shape": "slope"}, (i, 1, 0)))
    return items, dict(yaw=22, pitch=24, S=6)


def scene_signs():
    items = floor(-1, 6, -1, 2) + wall(-1, 6, 0, 4)
    items.append(("sign_bracket", {"facing": "north", "part": "arm"}, (0, 2, 0)))
    items.append(("sign_bracket", {"facing": "north", "part": "end"}, (0, 2, -1)))
    items.append(("signboard", {"facing": "north", "mount": "hanging", "trade": "bread"}, (0, 1, -1)))
    items.append(("sign_bracket", {"facing": "north", "part": "arm"}, (2, 2, 0)))
    items.append(("sign_bracket", {"facing": "north", "part": "end"}, (2, 2, -1)))
    items.append(("signboard", {"facing": "north", "mount": "hanging", "trade": "fish"}, (2, 1, -1)))
    for i, tr in enumerate(("tavern", "keys", "cloth")):
        items.append(("signboard", {"facing": "north", "mount": "flat", "trade": tr}, (3 if i == 0 else 4 if i == 1 else 5, 3, 0)))
    return items, dict(yaw=40, pitch=22, S=6)


def scene_lines(night=1.0, frame=0):
    """One run of sixteen cells: rope, laundry, bunting and lanterns in four sets each, sagging in the middle like a real line."""
    items = floor(-1, 17, -1, 3) + wall(-1, 17, 0, 2, z=3)
    for i in range(16):
        sag = min(2, int(round(2.6 * math.sin(math.pi * (i + 0.5) / 16))))
        hang = ("rope", "laundry", "bunting", "lanterns")[i // 4]
        items.append(("hang_line", {"axis": "x", "hang": hang, "set": i % 4, "sag": sag}, (i, 1, 1)))
    items.append(("line_pole", {"top": "true"}, (-1, 1, 1)))
    items.append(("line_pole", {"top": "true"}, (16, 1, 1)))
    items.append(("line_pole", {"top": "false"}, (-1, 0, 1)))
    items.append(("line_pole", {"top": "false"}, (16, 0, 1)))
    return items, dict(yaw=20, pitch=16, S=5, night=night, frame=frame)


def scene_lines_night():
    return scene_lines(0.3, 2)

LOG = "minecraft:block/stripped_dark_oak_log"


def scene_stall():
    items = floor(-2, 5, -2, 3) + [("box", (x, 0, 2), (0, 0, 0), (16, 16, 16), ASH) for x in range(-1, 4)] + wall(-1, 4, 1, 3, z=2)
    for x in (-1, 3):
        for z in (0, 1):
            for y in range(3):
                items.append(post(x, y, z))
    for x in range(0, 3):
        items.append(("stall_counter", {"facing": "north", "open": "true"}, (x, 0, 0)))
        items.append(("wares", {"good": ("bread", "produce", "cloth")[x]}, (x, 1, 0)))
        items.append(("awning", {"facing": "north", "colour": "red" if x != 1 else "yellow", "shape": "flat"}, (x, 2, 0)))
        items.append(("awning", {"facing": "north", "colour": "red" if x != 1 else "yellow", "shape": "flat"}, (x, 2, 1)))
        items.append(("awning", {"facing": "north", "colour": "red" if x != 1 else "yellow", "shape": "slope"}, (x, 2, -1)))
    items.append(("goods_shelf", {"facing": "north", "goods": "jars"}, (0, 0, 1)))
    items.append(("goods_shelf", {"facing": "north", "goods": "cloth"}, (2, 0, 1)))
    items.append(("market_table", {"axis": "x"}, (1, 0, 1)))
    items.append(("crate", {"content": "apples"}, (-1, 0, -1)))
    items.append(("sack", {"count": 2, "fill": "flour"}, (3, 0, -1)))
    return items, dict(yaw=28, pitch=22, S=6)


def scene_goods():
    items = floor(-1, 8, -1, 3) + [("box", (x, 0, 3), (0, 0, 0), (16, 16, 16), ASH) for x in range(-1, 8)] + wall(-1, 8, 1, 3, z=3)
    seq = [("crate", {"content": "apples"}), ("crate", {"content": "fish"}), ("crate", {"content": "bottles"}), ("crate", {"content": "cabbages"}),
           ("cask", {"orient": "upright"}), ("cask", {"orient": "z"}), ("sack", {"count": 3, "fill": "grain"})]
    for i, (n, p) in enumerate(seq):
        items.append((n, p, (i, 0, 2)))
    items.append(("crate", {"content": "straw"}, (0, 1, 2)))
    seq2 = [("produce", {"kind": "apples"}), ("produce", {"kind": "carrots"}), ("produce", {"kind": "pumpkins"}), ("produce", {"kind": "flowers"}),
            ("planter", {"style": "stone", "bloom": "red"}), ("planter", {"style": "wood", "bloom": "shrub"}), ("bench", {"facing": "north"})]
    for i, (n, p) in enumerate(seq2):
        items.append((n, p, (i, 0, 0)))
    items.append(("sack", {"count": 1, "fill": "wool"}, (5, 0, 2)))
    return items, dict(yaw=24, pitch=26, S=6)


def scene_carts():
    items = floor(-1, 7, -3, 3)
    for i, load in enumerate(("crates", "sacks", "casks")):
        items.append(("cart", {"facing": "north", "part": "body", "load": load}, (i * 2 + 0, 0, 1)))
        items.append(("cart", {"facing": "north", "part": "shafts", "load": load}, (i * 2 + 0, 0, 0)))
    items.append(("hay_cart", {"facing": "north", "part": "body"}, (6, 0, 1)))
    items.append(("hay_cart", {"facing": "north", "part": "shafts"}, (6, 0, 0)))
    return items, dict(yaw=36, pitch=28, S=6)


def scene_banners():
    items = floor(-1, 9, -2, 3) + wall(-1, 9, 0, 5, z=3)
    for i, d in enumerate(DESIGNS):
        for r, part in enumerate(("bottom", "middle", "top")):
            items.append(("banner_cloth", {"facing": "north", "design": d, "part": part, "mount": "wall"}, (i, 1 + r, 2)))
    return items, dict(yaw=18, pitch=14, S=5)


def scene_lanes():
    items = floor(-2, 8, -2, 4) + wall(-2, 8, 0, 3, z=4)
    for y_, tp in ((0, "false"), (1, "false"), (2, "true")):
        items.append(("line_pole", {"top": tp}, (-1, y_, 1)))
        items.append(("line_pole", {"top": tp}, (7, y_, 1)))
    for i in range(0, 7):
        sag = (0, 1, 2, 2, 2, 1, 0)[i]
        items.append(("hang_line", {"axis": "x", "hang": "laundry", "set": i % 4, "sag": sag}, (i, 2, 1)))
        items.append(("hang_line", {"axis": "x", "hang": "bunting" if i % 2 else "lanterns", "set": (i + 1) % 4, "sag": sag}, (i, 2, 2)))
    items.append(("bench", {"facing": "north"}, (1, 0, 3)))
    items.append(("sack", {"count": 2, "fill": "grain"}, (3, 0, 3)))
    items.append(("planter", {"style": "wood", "bloom": "yellow"}, (5, 0, 3)))
    return items, dict(yaw=24, pitch=20, S=5)


def scene_wall():
    items = floor(-2, 9, -2, 4) + wall(-2, 9, 0, 4, z=4)
    items.append(("wall_lantern", {"facing": "north"}, (0, 1, 3)))
    items.append(("mooring_ring", {"facing": "north"}, (1, 0, 3)))
    items.append(("notice_board", {"facing": "north"}, (2, 0, 3)))
    items.append(("flagpole", {"facing": "north"}, (4, 2, 3)))
    items.append(("banner_cloth", {"facing": "north", "design": "association", "part": "top", "mount": "pole"}, (4, 1, 2)))
    items.append(("banner_cloth", {"facing": "north", "design": "association", "part": "middle", "mount": "pole"}, (4, 0, 2)))
    items.append(("fishing_net", {"axis": "x"}, (6, 1, 3)))
    items += [post(2, y, 0) for y in range(2)] + [post(5, y, 0) for y in range(2)] + [post(8, y, 0) for y in range(2)]
    items.append(("well_windlass", {"axis": "x"}, (3, 1, 0)))
    items.append(("well_windlass", {"axis": "x"}, (4, 1, 0)))
    items.append(("signboard", {"facing": "north", "mount": "flat", "trade": "books"}, (7, 2, 3)))
    items.append(("market_table", {"axis": "x"}, (6, 0, 1)))
    items.append(("market_table", {"axis": "x"}, (7, 0, 1)))
    items.append(("wares", {"good": "tools"}, (6, 1, 1)))
    items.append(("wares", {"good": "spice"}, (7, 1, 1)))
    return items, dict(yaw=26, pitch=22, S=5)


SCENE_ORDER = [("a market stall: counters, wares, shelves, a flat and a sloped awning", scene_stall),
               ("awnings in six colours", scene_awnings),
               ("brackets, hanging and flat signs", scene_signs),
               ("hang lines (rope, laundry, bunting, lanterns), day", scene_lines),
               ("hang lines at night, frame 2: the lanterns glow", scene_lines_night),
               ("crates, casks, sacks, produce, planters, a bench", scene_goods),
               ("carts and a hay cart", scene_carts),
               ("banners of the eight districts", scene_banners),
               ("a lane: line poles with laundry, bunting and lantern lines, a bench, sacks, a planter", scene_lanes),
               ("wall furniture: wall lantern, mooring ring, notice board, flagpole with a banner, net, windlass, table with wares", scene_wall)]


def mock(out):
    from PIL import Image, ImageDraw
    sel = sys.argv[3:]
    ims = []
    for title, fn in SCENE_ORDER:
        if sel and not any(x in title for x in sel):
            continue
        items, kw = fn()
        frame = kw.pop("frame", 0)
        ims.append((title, render(blocks_quads(items, frame), **kw)))
    MAXW, GAP = 2300, 12
    placed, x, y, rowh = [], GAP, GAP, 0
    for title, im in ims:
        if x + im.width + GAP > MAXW and x > GAP:
            x, y, rowh = GAP, y + rowh + GAP + 16, 0
        placed.append((title, im, x, y))
        x += im.width + GAP
        rowh = max(rowh, im.height)
    sheet = Image.new("RGBA", (max(p[2] + p[1].width for p in placed) + GAP, y + rowh + GAP + 16), (30, 32, 40, 255))
    d = ImageDraw.Draw(sheet)
    for title, im, px, py in placed:
        sheet.paste(im, (px, py + 14))
        d.text((px, py), title[:110], fill=(230, 232, 240, 255))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "content":
        content()
    elif cmd == "check":
        sys.exit(check())
    elif cmd == "manifest":
        manifest()
    elif cmd == "sheet":
        make_sheet(sys.argv[2], sys.argv[3:])
    elif cmd == "mock":
        mock(sys.argv[2] if len(sys.argv) > 2 else "city_street_mock.png")
    elif cmd == "view":
        view_cmd(sys.argv[2], sys.argv[3], sys.argv[4:])
    else:
        print(__doc__)
