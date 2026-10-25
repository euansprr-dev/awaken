"""The Carillon wall kit's art (M4 Walls, docs/carillon/briefs/M4-walls-sonnet-1.md): arrow loops cut into the wall's own stone,
wall ivy, the gatehouse portcullis, braziers (floor and wall), drain spouts and the painted wall shields. Textures, models,
blockstates, lang and mineable tags, in the same register as the street kit (city_street.py): 16x16 per face, crisp, 4-6 tone
ramps, light from the top-left, colours anchored on real vanilla textures.

    python3 -I tools/art/build_all.py city_wall                  textures (animated flame and coals included)
    python3 -I tools/art/specs/city_wall.py content              blockstates, models, lang, mineable tags
    python3 -I tools/art/specs/city_wall.py check                every blockstate key covers the Java properties; models, textures resolve;
                                                                 every arrow loop tiles its cube exactly (no gap, no overlap)
    python3 -I tools/art/specs/city_wall.py sheet <out.png> [prefix...]   contact sheet of every new texture at 4x (frames side by side)
    python3 -I tools/art/specs/city_wall.py mock <out.png> [scene...]     assembled models, rendered from the real blockstate/model data
    python3 -I tools/art/specs/city_wall.py view <out.png> <block> [k=v ...]   one block (all states of one property with k=*)

Everything is deterministic (rng_for("city_wall", name)). Models are drawn for facing=north, the wall at z=16, and rotated by
the blockstate (rot_variant; the arrow loop and shield use uvlock so the stone pattern stays locked to the world).

The arrow loop is the one model that is not painted into an atlas: it is a cube of the wall's own vanilla stone (referenced by
id, never copied) with a pixel mask cut out of it. A small mesher turns the mask into quads: the cube's outer faces with the
hole removed (cullface kept), the reveals (the cut's sides, top and bottom, stone again) and the recess (the dark back, 12 px
in). Every quad's UV is the position-natural one a vanilla cube would have, so the pattern runs on seamlessly into the wall.
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
from city_kit import (R, rgb, lerp, mul, tint, put, noisy, fit, rot_variant, weighted, step, clamp8,  # noqa: E402,F401
                      IRON, DIRS, YROT, CUT, b)
import city_street as cs  # noqa: E402  (the model builder, the renderer and the district designs are shared)
from city_street import (World, MB, E, View, finish, fnum, FACE_NAMES, fillr, M_iron, M_solid, cloth_ramp_fine,  # noqa: E402,F401
                         FORGE, STEEL6, DARKOAK, OAK, WEATHERED, GILT, LAMP_BRONZE, DESIGN_COLOURS, EMBLEMS,
                         EMBLEM_OF, shaded_sprite, line, render, FNORM, LETTER, SHADE, _corners, _rotp, _rotv)

ROOT = ck.ROOT
ASSETS = ck.ASSETS
TEX = ck.TEX
MERGE = ck.MERGE
VANILLA = ck.VANILLA
PARENT = "minecraft:block/block"
SEED = "city_wall"
FACINGS = ["north", "east", "south", "west"]

# ---------------------------------------------------------------------------------------------------------------------
# palettes (dark -> light). Anchors: stone_bricks (122,121,122), deepslate_bricks (68,68,70), iron_bars (136,139,135),
# dark_oak_planks (66,43,20), oak_leaves / vine greens, campfire_fire's yellows.

LIME = R("#4a4a47", "#63635f", "#7d7d77", "#97968e", "#b0afa5", "#c9c8bc")        # weathered limestone, a touch warm
MOSS = R("#1f3320", "#2b4526", "#3a5a30", "#4f7440", "#678f50")
BRONZE = R("#2a1c0a", "#4a3010", "#6e4a18", "#946a24", "#bf8f38", "#e4bc62")
VERDI = R("#24574a", "#35796a", "#4c9a86", "#6db8a2")
COAL = R("#0e0908", "#1d1210", "#2e1a14", "#47261a", "#68341c")
WOODHI = R("#2f2012", "#463018", "#5f4220", "#7a5628")
STEM = R("#241810", "#35251a", "#4a3623", "#634a30", "#7c6040")
# leaf tones: (ramp dark->light, vein colour, gloss colour)
LEAF_GREEN = R("#0f2a16", "#173d1f", "#21562a", "#2f7233", "#418c3c", "#5aa84a")
LEAF_DARK = R("#040d12", "#091a20", "#0f292d", "#163a3b", "#204c48", "#2e6358")
LEAF_RED = R("#2e0a0c", "#4d1214", "#751c1a", "#9c2a20", "#bd4228", "#d9602f")
LEAF_RUST = R("#331a0c", "#52290f", "#76401a", "#9a5a24", "#bd7a31", "#d89a45")
TONE_BASE = {"green": 2, "dark": 3, "autumn": 3}
VEIN = {"green": rgb("#8fc462"), "dark": rgb("#5e9c88"), "autumn": rgb("#e8b866")}
GLOSS = {"green": rgb("#c4ec92"), "dark": rgb("#a8d4bc"), "autumn": rgb("#f6d08a")}


# ---------------------------------------------------------------------------------------------------------------------
# the world: the street kit's, plus a frame order for animations that should lick unevenly

class WallWorld(World):
    def __init__(self):
        super().__init__()
        self.timing = {}        # name -> [{"index": i, "time": t}, ...] when a texture plays its frames at uneven times

    def animated(self, name, frames, frametime, interpolate=False, timing=None):
        super().animated(name, frames, frametime, interpolate)
        if timing:
            self.timing[name] = [{"index": i, "time": t} for i, t in timing]


GENERATORS = []


def gen(fn):
    GENERATORS.append(fn)
    return fn


def vec(h):
    return rgb(h)


def uv_n(x0, y0, x1, y1):
    """UV of a north-facing rectangle at block x0..x1, y0..y1 (the texture is drawn as the viewer outside sees it: u runs
    towards -x, v towards -y; this is the position-natural mapping of a vanilla cube)."""
    return [fnum(16 - x1), fnum(16 - y1), fnum(16 - x0), fnum(16 - y0)]


def natural_uv(d, a, b_):
    """Position-natural UV (what a vanilla full cube has) of the rectangle a..b on the face of direction d."""
    x0, y0, z0 = a
    x1, y1, z1 = b_
    uv = {"N": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "S": [x0, 16 - y1, x1, 16 - y0],
          "E": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "W": [z0, 16 - y1, z1, 16 - y0],
          "U": [x0, z0, x1, z1], "D": [x0, 16 - z1, x1, 16 - z0]}[d]
    return [fnum(min(16, max(0, v))) for v in uv]


# ---------------------------------------------------------------------------------------------------------------------
# painting helpers (a material is a function (canvas, x, y, w, h, face, rng, dims) as in the street kit)

def M_bar(rp, hi=3, wear=0.2, knots=True):
    """A bar of dark weathered timber: the grain runs the long way, lit left/top edge, shaded right/bottom, a few knots and a grey wash."""
    def paint(c, x, y, w, h, face, rng, dims):
        vertical = h >= w
        n_across = w if vertical else h
        n_along = h if vertical else w
        streaks = [rng.random() < 0.45 for _ in range(n_across)]
        for ll in range(n_along):
            for cc in range(n_across):
                k = hi
                if n_across > 1:
                    if cc == 0:
                        k += 1
                    elif cc == n_across - 1:
                        k -= 1
                if streaks[cc]:
                    k -= 1
                if rng.random() < wear:
                    k += rng.choice([-1, 1])
                col = step(rp, k)
                if rng.random() < 0.07:
                    col = lerp(col, (128, 122, 108), 0.35)         # the grey of weather
                px, py = (cc, ll) if vertical else (ll, cc)
                c.set(x + px, y + py, col)
        if knots and n_along >= 8 and n_across >= 2 and rng.random() < 0.7:
            ll = rng.randrange(2, n_along - 2)
            cc = rng.randrange(0, n_across)
            px, py = (cc, ll) if vertical else (ll, cc)
            c.set(x + px, y + py, step(rp, 0))
    return paint


def M_plate(rp=FORGE, rivet=True):
    """A small iron plate: lit top-left edge, a bright rivet in the middle when it is 3 px or more."""
    def paint(c, x, y, w, h, face, rng, dims):
        for yy in range(h):
            for xx in range(w):
                k = 2
                if yy == 0 or xx == 0:
                    k = 3
                elif yy == h - 1 or xx == w - 1:
                    k = 1
                if rng.random() < 0.12:
                    k += rng.choice([-1, 1])
                c.set(x + xx, y + yy, step(rp, k))
        if rivet and w >= 3 and h >= 3:
            c.set(x + w // 2, y + h // 2, step(rp, 5))
    return paint


def M_limestone(wet=0.0, moss=0.0, tone=3, grain=0.2, stain=0):
    """Weathered pale limestone: soft mottling, a few pits, optional moss at the top and dark water streaks running down."""
    def paint(c, x, y, w, h, face, rng, dims):
        n = value_noise(max(w, 2), max(h, 2), rng, scale=3, octaves=2)
        cols = [rng.randrange(w) for _ in range(stain)]
        lens = [rng.uniform(0.5, 1.0) for _ in range(stain)]
        for yy in range(h):
            for xx in range(w):
                k = tone
                if n[yy][xx] > 0.62:
                    k += 1
                elif n[yy][xx] < 0.34:
                    k -= 1
                if w > 2 and h > 2:
                    if yy == 0 or xx == 0:
                        k += 1
                    elif yy == h - 1 or xx == w - 1:
                        k -= 1
                if rng.random() < grain:
                    k += rng.choice([-1, 1])
                col = step(LIME, k)
                if wet and rng.random() < wet:
                    col = mul(col, 0.62)
                for sc, sl in zip(cols, lens):                      # a dark streak from the top, thinning as it runs
                    if abs(xx - sc) <= (1 if h >= 6 and yy < h * 0.5 else 0) and yy < h * sl and (abs(xx - sc) == 0 or rng.random() < 0.55):
                        col = lerp(mul(col, 0.58), (46, 66, 52), 0.25 * (yy / max(1, h)))
                if moss and (yy < max(1, int(h * 0.5)) or face == "U") and rng.random() < moss * (1.0 - yy / max(1, h)) * 1.6:
                    col = step(MOSS, rng.choice([1, 2, 2, 3]))
                c.set(x + xx, y + yy, col)
    return paint


WET = R("#0c0f10", "#151b1d", "#202a2c", "#2d3a3c", "#425254", "#6a7c7c")


def M_wet(c, x, y, w, h, face, rng, dims):
    """The dark wet channel of a spout: slate-green stone, a pale sheen where the water runs."""
    n = value_noise(max(w, 2), max(h, 2), rng, scale=3, octaves=2)
    for yy in range(h):
        for xx in range(w):
            k = 2 + (1 if n[yy][xx] > 0.6 else -1 if n[yy][xx] < 0.3 else 0)
            if rng.random() < 0.15:
                k += rng.choice([-1, 1])
            if face == "U" and h >= 4 and (yy % 5 == 2 or (xx == w // 2 and rng.random() < 0.7)):
                k += 2                                              # a running glint down the middle
            c.set(x + xx, y + yy, step(WET, k))


# ---------------------------------------------------------------------------------------------------------------------
# generic helpers: greedy 2D rectangle merge

def merge_rects(cells):
    """Greedy merge of a set of (a, b) unit cells into rectangles (a0, b0, a1, b1), the half-open far corner."""
    cells = set(cells)
    out = []
    while cells:
        a0, b0 = min(cells, key=lambda p: (p[1], p[0]))
        a1 = a0
        while (a1 + 1, b0) in cells:
            a1 += 1
        b1 = b0
        while all((a, b1 + 1) in cells for a in range(a0, a1 + 1)):
            b1 += 1
        for a in range(a0, a1 + 1):
            for bb in range(b0, b1 + 1):
                cells.discard((a, bb))
        out.append((a0, b0, a1 + 1, b1 + 1))
    return out


# @@ GENERATORS BEGIN @@
# ---------------------------------------------------------------------------------------------------------------------
# 1. arrow_loop. A cube of the wall's own stone with a pixel mask cut through its north face, 12 px deep.
#    Image pixel (col, row) of a recess texture is block cell (15 - col, 15 - row): the texture is drawn as the viewer outside
#    sees it, and u runs towards -x on a north face.

STONES = {"stone_bricks": "minecraft:block/stone_bricks", "mossy": "minecraft:block/mossy_stone_bricks",
          "deepslate": "minecraft:block/deepslate_bricks", "calcite": "minecraft:block/calcite",
          "bricks": "minecraft:block/bricks", "tuff": "minecraft:block/tuff_bricks"}
STYLES = ["slit", "cross", "oillet", "window"]
PARTS3 = ["single", "lower", "upper"]
DEPTH = 12                                                  # px of opening before the recess


def _rows(spans):
    """{y: (x0, x1)} half-open spans -> the set of cut (x, y) cells."""
    return {(x, y) for y, (x0, x1) in spans.items() for x in range(x0, x1)}


def _slit(y0, y1, splay_foot=False, splay_head=False):
    spans = {y: (7, 9) for y in range(y0, y1)}
    if splay_foot:
        for y in (y0, y0 + 1):
            spans[y] = (6, 10)
    if splay_head:
        for y in (y1 - 1, y1 - 2):
            spans[y] = (6, 10)
    return spans


def _oillet_hole(spans, y0):
    """A round 4 px hole (rows 2, 4, 4, 2 wide) at the foot of the slit."""
    for i, (a, bb) in enumerate(((7, 9), (6, 10), (6, 10), (7, 9))):
        spans[y0 + i] = (a, bb)
    return spans


def _window(y0, y1, arch):
    spans = {y: (5, 11) for y in range(y0, y1)}
    if arch:
        spans[y1 - 1] = (7, 9)
        spans[y1 - 2] = (6, 10)
    return spans


def loop_mask(shape):
    """The cut cells (x, y) of one distinct model shape; y runs up from the block's floor."""
    if shape == "slit_single":
        return _rows(_slit(2, 14))
    if shape == "slit_lower":
        return _rows(_slit(3, 16, splay_foot=True))
    if shape == "slit_upper":
        return _rows(_slit(0, 13, splay_head=True))
    if shape == "cross_single":
        s = _slit(2, 14)
        s[7], s[8] = (4, 12), (4, 12)
        return _rows(s)
    if shape == "cross_upper":
        s = _slit(0, 13, splay_head=True)
        s[7], s[8] = (4, 12), (4, 12)
        return _rows(s)
    if shape == "oillet_single":
        return _rows(_oillet_hole(_slit(2, 14), 2))
    if shape == "oillet_lower":
        return _rows(_oillet_hole(_slit(3, 16), 3))
    if shape == "window_single":
        return _rows(_window(3, 13, True))
    if shape == "window_lower":
        return _rows(_window(3, 16, False))
    if shape == "window_upper":
        return _rows(_window(0, 13, True))
    raise KeyError(shape)


def shape_of(style, part):
    if style == "cross" and part == "lower":
        return "slit_lower"
    if style == "oillet" and part == "upper":
        return "slit_upper"
    return f"{style}_{part}"


SHAPES = sorted({shape_of(s, p) for s in STYLES for p in PARTS3})
SILLED = ("window_single", "window_lower")                  # a stone sill under the opening
RECESS_OF = {"window_single": "arrow_loop_recess_window", "window_lower": "arrow_loop_recess_window_lower",
             "window_upper": "arrow_loop_recess_window_upper"}
D6 = {"N": (0, 0, -1), "S": (0, 0, 1), "E": (1, 0, 0), "W": (-1, 0, 0), "U": (0, 1, 0), "D": (0, -1, 0)}


def mesh_cut_cube(mask):
    """Quads of a 16x16x16 cube with the mask (x, y) cut through z 0..DEPTH-1. Returns [(dir, kind, a, b)] with a/b the
    from/to corners (px), kind 'ext' (an outer face, culled against the neighbour), 'rev' (a reveal) or 'recess' (the dark back)."""
    cut = {(x, y, z) for (x, y) in mask for z in range(DEPTH)}
    planes = {}
    for x in range(16):
        for y in range(16):
            for z in range(16):
                if (x, y, z) in cut:
                    continue
                for d, (dx, dy, dz) in D6.items():
                    n = (x + dx, y + dy, z + dz)
                    if not all(0 <= v < 16 for v in n):
                        kind = "ext"
                    elif n in cut:
                        kind = "recess" if d == "N" else "rev"
                    else:
                        continue
                    if d in "NS":
                        plane, cell = z + (1 if d == "S" else 0), (x, y)
                    elif d in "EW":
                        plane, cell = x + (1 if d == "E" else 0), (z, y)
                    else:
                        plane, cell = y + (1 if d == "U" else 0), (x, z)
                    planes.setdefault((d, kind, plane), set()).add(cell)
    quads = []
    for (d, kind, plane), cells in sorted(planes.items()):
        for (a0, b0, a1, b1) in merge_rects(cells):
            if d in "NS":
                lo, hi = (a0, b0, plane), (a1, b1, plane)
            elif d in "EW":
                lo, hi = (plane, b0, a0), (plane, b1, a1)
            else:
                lo, hi = (a0, plane, b0), (a1, plane, b1)
            quads.append((d, kind, lo, hi))
    return quads


DEEP = R("#050403", "#0b0806", "#120e0a", "#1a140e", "#241c14", "#31261b")
IRONDIM = R("#0a0b0e", "#14161b", "#1e2128", "#2a2e37", "#3a3f4a")
GLASSDIM = R("#060a10", "#0e1520", "#16212f", "#22324a", "#304662", "#425e82")
LEAD = R("#020203", "#060608", "#0c0c10")


def recess_slit():
    """The dark back of a slit or cross, 12 px in: a shutter of vertical planks with seams, a dim iron strap or two catching a
    glint, deeper shadow towards the head. Never flat black."""
    rng = rng_for(SEED, "recess_slit")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            p = (x - 2) // 4
            edge = (x - 2) % 4
            k = 2 + (1 if p % 2 else 0) - (1 if y < 4 else 0) + (1 if y > 11 else 0)
            if edge == 0:
                k -= 2                                     # the seam between two planks
            elif edge == 1:
                k += 1
            elif edge == 3:
                k -= 1
            if rng.random() < 0.22:
                k += rng.choice([-1, 1])
            c.set(x, y, step(DEEP, k))
    for y in (5, 11):                                      # two straps across the shutter, dimmer than the wood is warm
        for x in range(16):
            c.set(x, y, step(IRONDIM, 2 if (x - 2) % 4 else 1))
            c.set(x, y + 1, step(IRONDIM, 1))
        for x in (3, 7, 11, 15):                           # rivets: one pixel of dull light each
            c.set(x, y, step(IRONDIM, 4))
    c.set(7, 8, rgb("#3e2a18")); c.set(8, 6, rgb("#33220f"))      # an old warm glint on a plank edge
    return c


def _lead_panes(c, x0, y0, x1, y1, rng, pane_hi=4):
    """Diamond leaded panes in the box x0..x1, y0..y1 (inclusive): dim cold glass, black lead, a brighter pane or two."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            u, v = (x + y) % 4, (x - y) % 4
            if u == 0 or v == 0:
                c.set(x, y, step(LEAD, 1 + (x + y) % 2))
                continue
            sky = 1.0 - (y - y0) / max(1, y1 - y0)         # a little more sky towards the head
            k = 2 + (1 if sky > 0.55 else 0) + (1 if (x * 3 + y * 5) % 7 == 0 else 0)
            if rng.random() < 0.25:
                k += rng.choice([-1, 1])
            c.set(x, y, step(GLASSDIM, min(pane_hi, k)))


def recess_window():
    """The back of a single window (cols 5..10, rows 3..12 of the image): dim leaded glass far inside."""
    rng = rng_for(SEED, "recess_window")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, step(DEEP, 1 + (1 if (x + y) % 5 == 0 else 0)))
    _lead_panes(c, 4, 3, 11, 12, rng)
    for x in range(16):                                    # the frame: dark timber showing round the glass
        for y in (2, 13):
            c.set(x, y, step(DEEP, 0))
    return c


def recess_window_tall():
    """A 2-block window, drawn as one 16x32 design and cut into upper (rows 0-15) and lower (rows 16-31)."""
    rng = rng_for(SEED, "recess_window_tall")
    c = Canvas(16, 32)
    for y in range(32):
        for x in range(16):
            c.set(x, y, step(DEEP, 1 + (1 if (x + y) % 5 == 0 else 0)))
    _lead_panes(c, 4, 3, 11, 28, rng)
    for x in range(16):
        c.set(x, 2, step(DEEP, 0))
        c.set(x, 29, step(DEEP, 0))
    for x in range(4, 12):                                 # a transom bar where the two blocks meet is not drawn: the glass runs on
        pass
    return c


def _halves(c):
    top, bot = Canvas(), Canvas()
    for y in range(16):
        for x in range(16):
            top.set(x, y, c.get(x, y))
            bot.set(x, y, c.get(x, y + 16))
    return top, bot


def loop_model(shape, stone):
    mask = loop_mask(shape)
    recess = RECESS_OF.get(shape, "arrow_loop_recess")
    tex = {"particle": STONES[stone], "stone": STONES[stone], "recess": b(recess)}
    els = []
    for (d, kind, lo, hi) in mesh_cut_cube(mask):
        uv = natural_uv(d, lo, hi)
        faces = {d: ("#recess" if kind == "recess" else "#stone", uv)}
        if kind == "ext":
            faces[d] = ("#stone", uv, True)                 # cullface: the neighbour hides it
        els.append(E(lo, hi, faces))
    if shape in SILLED:                                     # a projecting sill under the opening, a pixel wider each side
        els.append(E((4, 1.5, -1), (12, 3, 0), {
            "N": ("#stone", uv_n(4, 1.5, 12, 3)), "U": ("#stone", [4, 0, 12, 1]), "D": ("#stone", [4, 15, 12, 16]),
            "E": ("#stone", [15, 13, 16, 14.5]), "W": ("#stone", [0, 13, 1, 14.5])}))
    return {"parent": PARENT, "textures": tex, "elements": els}


@gen
def g_arrow_loop(w):
    w.texture("arrow_loop_recess", recess_slit())
    w.texture("arrow_loop_recess_window", recess_window())
    up, low = _halves(recess_window_tall())
    w.texture("arrow_loop_recess_window_upper", up)
    w.texture("arrow_loop_recess_window_lower", low)
    for shape in SHAPES:
        for stone in STONES:
            w.model(f"arrow_loop_{shape}_{stone}", loop_model(shape, stone))
    variants = {}
    for facing in FACINGS:
        for style in STYLES:
            for stone in STONES:
                for part in PARTS3:
                    v = rot_variant(f"arrow_loop_{shape_of(style, part)}_{stone}", facing)
                    if "y" in v:
                        v["uvlock"] = True                  # the stone's courses stay level with the wall round the loop
                    variants[f"facing={facing},part={part},stone={stone},style={style}"] = v
    w.state("arrow_loop", variants)


# ---------------------------------------------------------------------------------------------------------------------
# 2. wall_ivy. Hedera grown flat over a wall: a plane 0.2 px off the face (z 15.8) with the leaf texture (cutout) and a few
#    small leaf clumps standing 1-1.8 px proud. 3 densities x 3 tones x 2 variants of the plane texture; the clumps are
#    placed on the densest leaf spots of that very texture, so they sit on leaves and never float over bare wall.

def _leaf_cells(ang, scale):
    """The pixels (dx, dy, a, p) of one 3-5-lobed ivy leaf, tip towards angle ang (radians, 0 = up the wall), base at (0, 0).
    a runs along the leaf, p across it, in leaf units (the leaf is ~3 units long, ~2.6 wide)."""
    ca, sa = math.cos(ang), math.sin(ang)
    out = []
    r = int(math.ceil(3.4 * scale)) + 1
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            x, y = dx / scale, dy / scale
            a = x * sa - y * ca                 # the tip points to (sin ang, -cos ang) in image space
            p = x * ca + y * sa
            inside = False
            if -0.2 <= a <= 3.1 and abs(p) <= 1.15 * (1 - max(0.0, a - 0.3) / 2.9):       # the long middle lobe
                inside = True
            for phi in (1.0, -1.0):                                                       # two side lobes, ~57 degrees out
                t = a * math.cos(phi) + p * math.sin(phi)
                u = -a * math.sin(phi) + p * math.cos(phi)
                if -0.1 <= t <= 2.2 and abs(u) <= 0.95 * (1 - max(0.0, t - 0.2) / 2.2):
                    inside = True
            if a * a + p * p <= 0.95 * 0.95 and a >= -0.7:                                  # the heart-shaped base
                inside = True
            if inside:
                out.append((dx, dy, a, p))
    return out


def paint_leaf(get, put_, cx, cy, ang, scale, rp, tone, rng, shadow=True, vein=True, base=None):
    """Draw one leaf: three tones from rp (a lit upper-left rim, the body, a shaded lower-right rim), a paler vein, a glossy
    highlight now and then, and a one-pixel shadow cast on whatever is already under it."""
    cells = _leaf_cells(ang, scale)
    own = {(dx, dy) for (dx, dy, a, p) in cells}
    if len(own) < 3:
        return
    if base is None:
        base = TONE_BASE[tone] + (1 if rng.random() < 0.28 else 0)
    hi = min(own, key=lambda q: q[0] + q[1] + rng.random() * 0.4)
    if shadow:
        for (dx, dy) in own:
            q = (dx + 1, dy + 1)
            if q not in own:
                px = get(cx + q[0], cy + q[1])
                if px[3] == 255:
                    put_(cx + q[0], cy + q[1], mul(px, 0.7))
    for (dx, dy, a, p) in cells:
        k = base
        if (dx + 1, dy) not in own or (dx, dy + 1) not in own:
            k -= 1                                              # the shaded lower-right rim
        elif (dx - 1, dy) not in own or (dx, dy - 1) not in own:
            k += 1                                              # the lit upper-left rim
        col = step(rp, k)
        if vein and scale >= 1.2:
            on_mid = abs(p) <= 0.22 and 0.0 <= a <= 2.5
            on_side = False
            for phi in (0.95, -0.95):
                t = a * math.cos(phi) + p * math.sin(phi)
                u = -a * math.sin(phi) + p * math.cos(phi)
                if abs(u) <= 0.17 and 0.2 <= t <= 1.7:
                    on_side = True
            if on_mid or on_side:
                col = lerp(col, VEIN[tone], 0.5)
        put_(cx + dx, cy + dy, col)
    if vein and scale >= 1.2 and rng.random() < 0.5:
        put_(cx + hi[0], cy + hi[1], lerp(step(rp, base + 1), GLOSS[tone], 0.55))


def _ivy_ramps(tone, rng):
    if tone == "green":
        return LEAF_GREEN
    if tone == "dark":
        return LEAF_DARK
    r = rng.random()
    return LEAF_RED if r < 0.45 else LEAF_RUST if r < 0.82 else LEAF_GREEN


def _stem_paths(rng, n_stems, w=16, h=16, branchy=0.6):
    """Woody runners climbing the tile (they wrap, so a runner leaving the top returns at the bottom)."""
    pts, leaf_sites = [], []
    for s in range(n_stems):
        x = rng.randrange(0, w)
        y = h - 1 - rng.randrange(0, 4)
        dirx = rng.choice([-1, 1])
        length = rng.randrange(h - 2, h + 6)
        for i in range(length):
            pts.append((x % w, y % h))
            if i % 3 == 2:
                leaf_sites.append((x % w, y % h, dirx))
            if rng.random() < 0.55:
                x += dirx
                if rng.random() < 0.35:
                    dirx = -dirx
            y -= 1
            if rng.random() < 0.09 and i > 2:                      # a side shoot
                bx, by, bd = x, y, rng.choice([-1, 1])
                for j in range(rng.randrange(3, 6)):
                    bx += bd
                    by -= 1 if j % 2 == 0 else 0
                    pts.append((bx % w, by % h))
                    if j == 2:
                        leaf_sites.append((bx % w, by % h, bd))
    return pts, leaf_sites


def ivy_plane(tone, density, variant):
    rng = rng_for(SEED, "ivy_plane", tone, density, variant)
    c = Canvas()
    W = 16

    def get(x, y):
        return c.get(x % W, y % W)

    def put_(x, y, col):
        c.set(x % W, y % W, col)

    stems, sites = _stem_paths(rng, (3, 2, 2)[density])
    for (x, y) in stems:
        c.set(x, y, step(STEM, 3 if (x + y) % 3 else 2))

    def new_leaf(x, y, big=None):
        ang = rng.choice([0, 1, 2, 3]) * math.pi / 2
        sc = rng.choice([1.45, 1.7, 1.95]) if big is None else big
        paint_leaf(get, put_, x, y, ang, sc, _ivy_ramps(tone, rng), tone, rng, shadow=(density > 0))
    if density == 0:                                    # the edge of a patch: runners with a few leaves
        rng.shuffle(sites)
        for (x, y, d) in sites[:5]:
            new_leaf(x, y, 1.45)
    else:
        step_ = (0, 5.2, 3.4)[density]
        pts = []
        gy = 0.0
        row = 0
        while gy < 16:
            gx = (row % 2) * step_ / 2
            while gx < 16:
                pts.append((int(gx + rng.uniform(-1.2, 1.2)), int(gy + rng.uniform(-1.2, 1.2))))
                gx += step_
            gy += step_ * 0.82
            row += 1
        rng.shuffle(pts)
        if density == 1:
            pts = pts[:int(len(pts) * 0.78)]
        for (x, y) in pts:
            new_leaf(x, y)
        if density == 2:                                  # a second layer over the gaps, then two or three pinholes
            for (x, y) in [(rng.randrange(16), rng.randrange(16)) for _ in range(40)]:
                if c.get(x % W, y % W)[3] == 0:
                    new_leaf(x, y, 1.45)
            holes = [(rng.randrange(16), rng.randrange(16)) for _ in range(3)]
            for (hx, hy) in holes:
                c.set(hx, hy, (0, 0, 0, 0))
    return c


def ivy_clump(tone):
    """16x16 opaque leaf mats in four 8x8 quadrants: front (0,0), top (8,0), side (0,8), underside (8,8). Low contrast: a clump is
    a mass of leaf, not a pattern."""
    rng = rng_for(SEED, "ivy_clump", tone)
    c = Canvas()
    for qi, (qx, qy, f) in enumerate(((0, 0, 1.0), (8, 0, 1.1), (0, 8, 0.78), (8, 8, 0.6))):
        sub = Canvas(8, 8)
        base_rp = _ivy_ramps(tone, rng) if tone != "autumn" else LEAF_RED
        for y in range(8):
            for x in range(8):
                sub.set(x, y, step(base_rp, 1 + (1 if (x * 7 + y * 3) % 5 == 0 else 0)))

        def get(x, y):
            return sub.get(x % 8, y % 8)

        def put_(x, y, col):
            sub.set(x % 8, y % 8, col)
        for _ in range(18):
            paint_leaf(get, put_, rng.randrange(8), rng.randrange(8), rng.choice([0, 1, 2, 3]) * math.pi / 2, rng.uniform(1.0, 1.3),
                       _ivy_ramps(tone, rng), tone, rng, shadow=False, vein=False, base=TONE_BASE[tone])
        for y in range(8):
            for x in range(8):
                c.set(qx + x, qy + y, mul(sub.get(x, y), f))
    return c


def ivy_model(tone, density, variant, plane_name, clump_name, plane_canvas):
    rng = rng_for(SEED, "ivy_model", tone, density, variant)
    # candidate spots: the centre of every 3x3 window, scored by how much of the plane texture is leaf there
    spots = []
    for cy in range(2, 14):
        for cx in range(2, 14):
            n = sum(1 for dy in (-1, 0, 1) for dx in (-1, 0, 1) if plane_canvas.get(cx + dx, cy + dy)[3])
            spots.append((n + rng.random() * 1.5, cx, cy))
    spots.sort(reverse=True)
    n_clumps = (6, 9, 12)[density]
    chosen = []
    for score, cx, cy in spots:
        if len(chosen) >= n_clumps:
            break
        if all(abs(cx - ox) + abs(cy - oy) >= 4 for ox, oy in chosen):
            chosen.append((cx, cy))
    els = [E((0, 0, 15.8), (16, 16, 15.8), {"N": ("#leaf", [0, 0, 16, 16])})]
    for (px, py) in chosen:
        w_, h_ = rng.choice([(3, 3), (3, 4), (4, 3), (4, 4), (2, 3)])
        depth = rng.choice([1.0, 1.4, 1.8])
        cx, cy = 16 - px, 16 - py                          # texture pixel -> block x, y (u runs towards -x, v towards -y)
        x0, x1 = cx - w_ / 2, cx + w_ / 2
        y0, y1 = cy - h_ / 2, cy + h_ / 2
        zf = 15.8 - depth
        def pick(qx, qy, w2, h2):
            ux = qx + rng.randrange(0, max(1, 9 - w2))
            uy = qy + rng.randrange(0, max(1, 9 - h2))
            return [ux, uy, ux + w2, uy + h2]
        faces = {"N": ("#clump", pick(0, 0, w_, h_)), "U": ("#clump", pick(8, 0, w_, 2)), "D": ("#clump", pick(8, 8, w_, 2)),
                 "E": ("#clump", pick(0, 8, 2, h_)), "W": ("#clump", pick(0, 8, 2, h_))}
        # the sides are (depth) deep: sample strips at 1:1 (rounded up) so they read as leaf, not as smear
        for L, ww in (("U", int(math.ceil(depth))), ("D", int(math.ceil(depth)))):
            u = faces[L][1]
            faces[L] = ("#clump", [u[0], u[1], u[0] + w_, u[1] + ww])
        for L in "EW":
            u = faces[L][1]
            faces[L] = ("#clump", [u[0], u[1], u[0] + int(math.ceil(depth)), u[1] + h_])
        els.append(E((x0, y0, zf), (x1, y1, 15.8), faces))
    return {"parent": PARENT, "ambientocclusion": False,
            "textures": {"particle": b(plane_name), "leaf": b(plane_name), "clump": b(clump_name)}, "elements": els}


@gen
def g_ivy(w):
    for tone in TONES:
        w.texture(f"wall_ivy_clump_{tone}", ivy_clump(tone))
    for tone in TONES:
        for density in range(3):
            for variant in range(2):
                pn = f"wall_ivy_{tone}_{density}_{variant}"
                pc = ivy_plane(tone, density, variant)
                w.texture(pn, pc)
                w.model(pn, finish(w, ivy_model(tone, density, variant, pn, f"wall_ivy_clump_{tone}", pc)))
    variants = {}
    for facing in FACINGS:
        for tone in TONES:
            for density in range(3):
                entries = []
                for variant in range(2):
                    e = {"model": f"wall_ivy_{tone}_{density}_{variant}"}
                    if YROT[facing]:
                        e["y"] = YROT[facing]
                    entries.append(e)
                variants[f"density={density},facing={facing},tone={tone}"] = weighted(entries)
    w.state("wall_ivy", variants)


# ---------------------------------------------------------------------------------------------------------------------
# 3. portcullis. A lattice of dark weathered oak bars (2 px, every 4 px; centres 2, 6, 10, 14) crossed by two rails (y 3 and
#    y 11), all 2 px thick, centred (z 7-9 for axis x), iron-strapped at every crossing. Top has a heavy iron-bound head beam,
#    bottom has no lower rail and its bars end in forged iron spikes.

VBARS = (1, 5, 9, 13)                       # left edge of each vertical bar
RAILS = (3, 11)                             # bottom edge of each rail
RAIL_SEGS = ((0, 1), (3, 5), (7, 9), (11, 13), (15, 16))      # the rail between the bars (and a pixel at each end to join the next block)


def portcullis_model(w, part):
    m = MB(w.atlas("portcullis"), f"portcullis_{part}", ao=False)
    wood = {"*": M_bar(DARKOAK, 3)}
    wood_h = {"*": M_bar(DARKOAK, 3)}
    iron = {"*": M_iron(FORGE, 4)}
    plate = {"*": M_plate(FORGE)}
    ztop = 7
    y_lo = {"top": 0, "middle": 0, "bottom": 4}[part]
    y_hi = {"top": 13, "middle": 16, "bottom": 16}[part]
    for i, x in enumerate(VBARS):
        m.box((x, y_lo, 7), (x + 2, y_hi, 9), wood, faces="NSEW" + ("U" if part != "top" else "") + ("D" if part != "bottom" else ""),
              key=f"vbar{i % 2}" if part == "top" else f"vbar{i % 2}_{part}")
    rails = [r for r in RAILS if not (part == "bottom" and r == 3)]
    for r in rails:
        for j, (x0, x1) in enumerate(RAIL_SEGS):
            m.box((x0, r, 7), (x1, r + 2, 9), wood_h, faces="NSUD" + ("W" if x0 == 0 and False else ""), key=f"rail{x1 - x0}_{j % 2}")
    for r in rails:                                        # the iron strap at every crossing, a half-pixel proud on both faces
        for x in VBARS:
            m.box((x - 0.3, r - 0.3, 6.6), (x + 2.3, r + 2.3, 9.4), plate, faces="NSEWUD", key="strap")
    if part == "bottom":
        for x in VBARS:
            cx = x + 1
            m.box((cx - 1.3, 3.2, 6.7), (cx + 1.3, 4.4, 9.3), iron, faces="NSEWUD", key="ferrule")       # the iron shoe on the bar
            for (y0, y1, hw) in ((2, 3.2, 0.9), (1, 2, 0.6), (0, 1, 0.3)):                                 # the forged point, 3 px
                m.box((cx - hw, y0, 8 - hw), (cx + hw, y1, 8 + hw), iron, faces="NSEWD" if y0 == 0 else "NSEW", key=f"spike{y0}")
    if part == "top":
        m.box((0, 13, 6), (16, 16, 10), wood, faces="NSEWUD", cull="", key="head")
        for (x0, x1) in ((1, 3), (7, 9), (13, 15)):
            m.box((x0 - 0.2, 12.8, 5.7), (x1 + 0.2, 16, 10.3), plate, faces="NSEWD", key="band")
        for x in (0.3, 15.3):                              # the beam's ends are shod
            m.box((x, 12.9, 5.8), (x + 0.4, 15.9, 10.2), iron, faces="NSUD", key="endcap")
    return finish(w, m.build(ao=False))


@gen
def g_portcullis(w):
    variants = {}
    for part in ("top", "middle", "bottom"):
        w.model(f"portcullis_{part}", portcullis_model(w, part))
    for axis in ("x", "z"):
        for part in ("top", "middle", "bottom"):
            v = {"model": b(f"portcullis_{part}")}
            if axis == "z":
                v["y"] = 90
            variants[f"axis={axis},part={part}"] = v
    w.state("portcullis", variants)


# ---------------------------------------------------------------------------------------------------------------------
# 4. brazier. An iron basket of coals on a black tripod (floor) or in a bracket off a wall plate (wall). The coals and the flame
#    are emissive and animated: 12 frames, closed loops of a few slow harmonics so the lick is irregular but never strobes. The
#    flame is two crossed 16x16 planes, its tongue rising ~10 px over the coals with a white-hot core, sparks above.

FLAME_N = 12
FLAME_RAMP = [rgb(h) for h in ("#fffbe6", "#fff0a0", "#ffd84a", "#ffb12a", "#f4801c", "#d8561a", "#a8341a")]
SPARK_RAMP = [rgb(h) for h in ("#fff6c0", "#ffd25a", "#ff9a30", "#e4631f", "#b0401a")]


def flame_frames():
    rng = rng_for(SEED, "flame")
    ph = [rng.uniform(0, math.tau) for _ in range(24)]
    sparks = [(rng.uniform(5, 10), rng.uniform(0, 1), rng.choice([1, 1, 2])) for _ in range(4)]
    frames = []
    for f in range(FLAME_N):
        t = math.tau * f / FLAME_N
        c = Canvas()
        # the main body leans with the draught; two side tongues and a tall thin one lick on their own slow beats
        lean = 1.7 * math.sin(t + ph[0]) + 0.8 * math.sin(2 * t + ph[1])
        H = 9.8 + 1.0 * math.sin(2 * t + ph[2]) + 0.6 * math.sin(3 * t + ph[3])
        tongues = [
            # (x offset at the root, lean factor, height, root half-width)
            (-2.3 + 0.5 * math.sin(t + ph[4]), 1.0 + 0.4 * math.sin(2 * t + ph[5]), 6.4 + 1.8 * math.sin(2 * t + ph[6]) + 0.8 * math.sin(3 * t + ph[7]), 2.0),
            (2.4 + 0.5 * math.sin(t + ph[8]), 1.0 + 0.4 * math.sin(2 * t + ph[9]), 6.2 + 1.8 * math.sin(3 * t + ph[10]) + 0.8 * math.sin(t + ph[11]), 1.9),
            (0.2 + 1.0 * math.sin(t + ph[12]), 1.4, 11.2 + 1.8 * math.sin(2 * t + ph[13]) + 0.9 * math.sin(3 * t + ph[14]), 1.4),
        ]
        for r in range(0, 14):
            y = 15 - r
            for x in range(1, 15):
                best = 0.0
                # main teardrop: widest low down, pointed at the tip
                frac = r / H
                if frac < 1.0:
                    cx = 7.5 + lean * frac ** 1.5
                    hw = 5.0 * math.sin(math.pi * min(1.0, (r + 2.6) / (H + 2.6))) ** 0.85 * (1.0 - 0.2 * frac)
                    hw += 0.55 * math.sin(1.9 * r + 2 * t + ph[15]) * frac
                    e = abs(x - cx) / max(hw, 0.01)
                    if e < 1.0:
                        best = max(best, ((1 - e) ** 0.75) * ((1 - frac) ** 0.55 * 0.55 + 0.45))
                for (ox, lf, th, rw) in tongues:
                    fr = r / th
                    if fr >= 1.0:
                        continue
                    cx = 7.5 + ox + lean * lf * fr ** 1.5 + 0.9 * math.sin(1.6 * r + t + ph[16]) * fr
                    hw = rw * (1 - fr) ** 0.8 * (0.5 + 0.5 * min(1.0, (r + 1.0) / 3.0))
                    e = abs(x - cx) / max(hw, 0.01)
                    if e < 1.0:
                        best = max(best, ((1 - e) ** 0.8) * ((1 - fr) ** 0.6) * 0.82)
                if best <= 0.04:
                    continue
                if best > 0.8:
                    i = 0
                elif best > 0.62:
                    i = 1
                elif best > 0.46:
                    i = 2
                elif best > 0.33:
                    i = 3
                elif best > 0.2:
                    i = 4
                elif best > 0.1:
                    i = 5
                else:
                    i = 6
                if i == 6 and rng_for(SEED, "flame_tip", x, r, f).random() < 0.55:
                    continue                                # the cool tips break up
                c.set(x, y, FLAME_RAMP[i])
        for si, (sx, s0, spd) in enumerate(sparks):          # sparks climb out of the tongue and cool as they go
            u = (f / FLAME_N * spd + s0) % 1.0
            if u < 0.1 or u > 0.92:
                continue
            xx = int(round(sx + 1.6 * math.sin(math.tau * (u * spd + s0))))
            yy = int(round(8 - 9 * u))
            if 0 <= yy < 6 and c.get(xx, yy)[3] == 0:
                c.set(xx, yy, SPARK_RAMP[min(4, int(u * 5))])
        frames.append(c)
    return frames


def coal_frames():
    """Heaped coals, seen from above and from the side (one 16x16 tile): dark lumps, the glow in the cracks between them
    breathing slowly, each crack on its own phase."""
    rng = rng_for(SEED, "coals")
    seeds = []
    for gy in range(4):
        for gx in range(4):
            seeds.append(((gx + 0.5 + rng.uniform(-0.38, 0.38)) * 4.0, (gy + 0.5 + rng.uniform(-0.38, 0.38)) * 4.0))
    cell = {}
    for y in range(16):
        for x in range(16):
            ds = []
            for i, (sx, sy) in enumerate(seeds):
                dxx = min(abs(x + 0.5 - sx), 16 - abs(x + 0.5 - sx))
                dyy = min(abs(y + 0.5 - sy), 16 - abs(y + 0.5 - sy))
                ds.append((math.hypot(dxx, dyy), i))
            ds.sort()
            cell[(x, y)] = (ds[0][1], ds[0][0], ds[1][0] - ds[0][0], ds[1][1])
    phase = {i: rng.uniform(0, math.tau) for i in range(len(seeds))}
    glow_ramp = [rgb(h) for h in ("#3a0d08", "#7a1c0a", "#b83a10", "#e8631a", "#ff9a2c", "#ffca50", "#fff0b0")]
    hotspots = [(rng.randrange(16), rng.randrange(16)) for _ in range(3)]
    frames = []
    for f in range(FLAME_N):
        t = math.tau * f / FLAME_N
        c = Canvas()
        for y in range(16):
            for x in range(16):
                i, d1, gap, j = cell[(x, y)]
                if gap < 1.75:                                # a crack: ember glow
                    base = 4.5 - gap * 1.35
                    pulse = 0.85 * math.sin(t + phase[i]) + 0.45 * math.sin(2 * t + phase[j])
                    k = base + pulse * 0.7
                    for (hx, hy) in hotspots:
                        if abs(hx - x) + abs(hy - y) <= 1:
                            k += 1.0 + 0.5 * math.sin(t + hx)
                    c.set(x, y, glow_ramp[max(0, min(6, int(round(k))))])
                else:                                          # a lump of charcoal, red where the glow beside it reaches
                    k = 2 + (1 if d1 < 1.2 else 0) + (1 if (x + y) % 5 == 0 and gap < 2.6 else 0)
                    if gap < 2.4:
                        k += 1
                    if rng.random() < 0.2:
                        k += rng.choice([-1, 1])
                    c.set(x, y, step(COAL, k))
        frames.append(c)
    return frames


def basket_lattice():
    """The side of an iron basket as a cutout: rows 0-2 the 10 px side, rows 4-6 the 8 px side. Vertical straps with gaps top and
    bottom, a solid riveted band between."""
    c = Canvas()
    for (y0, wd, straps) in ((0, 10, (0, 3, 6, 9)), (4, 8, (0, 3, 4, 7))):
        for x in range(wd):
            c.set(x, y0 + 1, step(FORGE, 3 if x % 3 else 4))               # the band
            if x in straps:
                for yy in (y0, y0 + 2):
                    c.set(x, yy, step(FORGE, 3 if x == straps[0] else 2))
        for x in straps:
            c.set(x, y0 + 1, step(FORGE, 5))                               # a rivet where a strap crosses the band
    return c


def _leg_boxes(m, cx, cz, y_top, r_top, r_bot, iron):
    """Three splayed legs 1 px square, stepped (a pixel diagonal): at 90, 210 and 330 degrees round the centre."""
    n = 6
    h = y_top / n
    for (dxn, dzn) in ((0.0, -1.0), (-0.866, 0.5), (0.866, 0.5)):
        for i in range(n):
            r = r_top + (r_bot - r_top) * (i + 0.5) / n
            x, z = cx + dxn * r, cz + dzn * r
            y1 = y_top - i * h
            m.box((x - 0.5, y1 - h, z - 0.5), (x + 0.5, y1, z + 0.5), iron, faces="NSEWUD", key="leg%d" % i)


def _ring(m, x0, x1, z0, z1, y0, y1, th, iron):
    for (a, b_) in (((x0, z0), (x1, z0 + th)), ((x0, z1 - th), (x1, z1)), ((x0, z0 + th), (x0 + th, z1 - th)),
                    ((x1 - th, z0 + th), (x1, z1 - th))):
        m.box((a[0], y0, a[1]), (b_[0], y1, b_[1]), iron, faces="NSEWUD", key=f"ring{round(b_[0] - a[0], 1)}_{round(b_[1] - a[1], 1)}")


def _coal_box(m, a, b_, name="brazier_coals", top_uv=None):
    cv = m.var(name)
    dx, dy, dz = b_[0] - a[0], b_[1] - a[1], b_[2] - a[2]
    tw, td = min(16, dx), min(16, dz)
    ox = 8 - tw / 2
    faces = {"N": (cv, [0, 0, min(16, dx), min(16, dy)]), "S": (cv, [0, 0, min(16, dx), min(16, dy)]),
             "E": (cv, [0, 0, min(16, dz), min(16, dy)]), "W": (cv, [0, 0, min(16, dz), min(16, dy)]),
             "U": (cv, [ox, ox, ox + tw, ox + td])}
    m.raw(E(a, b_, faces, emit=15, shade=False))


def _flame_planes(m, zc, y0, y1, xc=8):
    fv = m.var("brazier_flame")
    # one plane along x at z = zc, one along z at x = xc: both show the same animated tongue
    m.raw(E((1, y0, zc), (15, y1, zc), {"N": (fv, [1, 0, 15, 16]), "S": (fv, [1, 0, 15, 16])}, emit=15, shade=False))
    m.raw(E((xc, y0, zc - 7), (xc, y1, zc + 7), {"E": (fv, [16 - (zc + 7), 0, 16 - (zc - 7), 16]),
                                                "W": (fv, [zc - 7, 0, zc + 7, 16])}, emit=15, shade=False))


def brazier_floor_model(w):
    m = MB(w.atlas("brazier"), "brazier_floor", ao=False)
    iron = {"*": M_iron(FORGE, 4)}
    _leg_boxes(m, 8, 8, 7, 3.4, 5.3, iron)
    m.box((4, 7, 4), (12, 8, 12), iron, faces="NSEWUD", key="plate")
    bv = m.var("brazier_basket")
    m.raw(E((3, 8, 3), (13, 11, 13), {f: (bv, [0, 0, 10, 3]) for f in "NESW"}))
    _coal_box(m, (3.4, 8.3, 3.4), (12.6, 11.6, 12.6))
    _ring(m, 2, 14, 2, 14, 11, 12, 1.3, iron)
    _coal_box(m, (4.2, 11.6, 4.2), (11.8, 12.8, 11.8))
    _coal_box(m, (5.8, 12.8, 5.8), (10.2, 13.7, 10.2))
    _flame_planes(m, 8, 12.6, 28.6)
    return finish(w, m.build(ao=False))


def brazier_wall_model(w):
    m = MB(w.atlas("brazier"), "brazier_wall", ao=False)
    iron = {"*": M_iron(FORGE, 4)}
    plate = {"*": M_plate(FORGE)}
    m.box((5.5, 6, 15), (10.5, 13, 16), plate, faces="NEWUD", key="wallplate")
    for (x, y) in ((6.2, 12.0), (8.9, 12.0), (6.2, 6.7), (8.9, 6.7)):          # four bolt heads
        m.box((x, y, 14.6), (x + 0.9, y + 0.9, 15), iron, faces="NEWUD", key="bolt")
    m.box((7.4, 9, 8.6), (8.6, 10.2, 15), iron, faces="NEWUD", key="arm")       # the arm under the basket
    for i, (z0, y0) in enumerate(((14, 7.0), (13, 7.7), (12, 8.4), (11, 9.0))):  # the strut: a pixel diagonal from plate to arm
        m.box((7.4, y0, z0), (8.6, y0 + 1.0, z0 + 1.0), iron, faces="NEWUD", key="strut")
    m.box((5, 9.6, 5.6), (11, 10, 12.4), iron, faces="NSEWUD", key="wplate")
    bv = m.var("brazier_basket")
    m.raw(E((4, 10, 5), (12, 12, 13), {f: (bv, [0, 4, 8, 6]) for f in "NESW"}))
    _coal_box(m, (4.4, 10.2, 5.4), (11.6, 12.3, 12.6))
    _ring(m, 3, 13, 4, 14, 12, 13, 1.2, iron)
    _coal_box(m, (4.8, 12.3, 5.8), (11.2, 13.3, 12.2))
    _coal_box(m, (6.2, 13.3, 7.2), (9.8, 14.1, 10.8))
    _flame_planes(m, 9, 13.0, 29.0)
    return finish(w, m.build(ao=False))


@gen
def g_brazier(w):
    w.animated("brazier_flame", flame_frames(), 3, False,
               timing=[(i, t) for i, t in enumerate((3, 2, 3, 4, 3, 2, 3, 3, 4, 2, 3, 3))])
    w.animated("brazier_coals", coal_frames(), 4, True)
    w.texture("brazier_basket", basket_lattice())
    w.model("brazier_floor", brazier_floor_model(w))
    w.model("brazier_wall", brazier_wall_model(w))
    variants = {}
    for facing in FACINGS:
        variants[f"facing={facing},mount=floor"] = rot_variant("brazier_floor", facing)
        variants[f"facing={facing},mount=wall"] = rot_variant("brazier_wall", facing)
    w.state("brazier", variants)


# ---------------------------------------------------------------------------------------------------------------------
# 5. wall_spout. A carved limestone drain thrown clear of the wall: a trough 6 px wide (x 5-11), y 4-10, from the wall (z 16)
#    out to z 0, open on top over a dark wet channel, a stepped corbel under its root, ending in a snarling grotesque: brow,
#    flat nose, fangs in a two-pixel mouth the water pours through, two small horns.

TOOTH = R("#8a8878", "#b4b19e", "#d8d4be", "#ece8d4")


def spout_face():
    """Texture of the head's front, drawn as seen from outside (u runs towards -x, v towards -y): the skull face at
    cols 4..11 rows 5..8 (block x 11..4, y 10..7; row 5 doubles as the brow's front), the nose's front at cols 6..9 row 7,
    the chin at cols 5..10 rows 11..12 (block y 5..3), spare limestone below."""
    rng = rng_for(SEED, "spout_face")
    c = Canvas()
    paint = M_limestone(tone=2, grain=0.24)
    paint(View(c, 4, 5, 8, 4), 0, 0, 8, 4, "N", rng, None)
    paint(View(c, 5, 11, 6, 2), 0, 0, 6, 2, "N", rng, None)
    paint(View(c, 0, 13, 16, 3), 0, 0, 16, 3, "N", rng, None)
    deep = rgb("#0d0e10")
    for x in range(4, 12):                                               # row 5: forehead and the brow's lit front
        c.set(x, 5, step(LIME, 3 if x in (6, 9) else 4 if x < 8 else 3))
    c.set(7, 5, step(LIME, 1)); c.set(8, 5, step(LIME, 2))               # a crack down the brow
    for x, k in ((4, 2), (5, 0), (6, -1), (7, 3), (8, 3), (9, -1), (10, 0), (11, 2)):      # row 6: eyes, narrow slots under the brow
        c.set(x, 6, deep if k in (0, -1) else step(LIME, k))
    c.set(6, 6, rgb("#59625d")); c.set(9, 6, rgb("#59625d"))              # a cold glint in each
    for x, k in ((4, 2), (5, 1), (6, 3), (7, 4), (8, 4), (9, 3), (10, 1), (11, 2)):         # row 7: cheeks and the nose's back
        c.set(x, 7, step(LIME, k))
    for x in range(4, 12):                                               # row 8: the upper lip, a dark seam; fang bases pale
        c.set(x, 8, step(LIME, 0 if x not in (5, 10) else 1))
    c.set(5, 8, step(TOOTH, 1)); c.set(10, 8, step(TOOTH, 1))
    c.set(6, 7, deep); c.set(9, 7, deep)                                  # the nose's front is cols 6..9 of row 7: nostrils
    c.set(7, 7, step(LIME, 4)); c.set(8, 7, step(LIME, 3))
    for x in range(5, 11):                                               # the chin: a heavy jaw with a dark seam under the lip
        c.set(x, 11, step(LIME, 3 if x not in (7, 8) else 2))
        c.set(x, 12, step(LIME, 1 if x in (5, 10) else 2))
    c.set(7, 12, rgb("#2e3d37")); c.set(8, 12, rgb("#26332e"))            # a drip of green-black under the chin
    return c


def spout_model(w):
    m = MB(w.atlas("wall_spout"), "wall_spout", ao=True)
    stone = {"*": M_limestone(tone=2, stain=2), "U": M_limestone(tone=3, moss=0.16)}
    stone_plain = {"*": M_limestone(tone=2), "U": M_limestone(tone=3, moss=0.08)}
    wet = {"*": M_wet}
    tooth = {"*": lambda c, x, y, w_, h, face, rng, dims: [c.set(x + xx, y + yy, step(TOOTH, 2 + (1 if xx == 0 or yy == 0 else 0) - (1 if yy == h - 1 else 0)))
                                                         for yy in range(h) for xx in range(w_)]}
    # corbel: two steps under the root of the trough
    m.box((5, 2.5, 11), (11, 4, 16), stone, faces="NEWD", key="corbel1")
    m.box((5.5, 1.5, 13), (10.5, 2.5, 16), stone, faces="NEWD", key="corbel2")
    # the trough: floor, two walls (the inner faces wet), the dark end in the wall
    m.box((5, 4, 5.5), (11, 5, 16), stone_plain, faces="D", key="floor_d")
    m.box((5, 5, 5.5), (11, 5, 16), wet, faces="U", key="floor_u")
    m.box((5, 4, 5.5), (6.5, 10, 16), stone, faces="WU", key="wallL")
    m.box((9.5, 4, 5.5), (11, 10, 16), stone, faces="EU", key="wallR")
    m.box((6.5, 5, 5.5), (6.5, 10, 16), wet, faces="E", key="innerL")
    m.box((9.5, 5, 5.5), (9.5, 10, 16), wet, faces="W", key="innerR")
    m.box((6.5, 5, 15.9), (9.5, 10, 15.9), wet, faces="N", key="back")
    # the head: a lower jaw (its top is the tongue the water runs over), the skull over a two-pixel mouth, brow, nose, horns
    fv = m.var("wall_spout_face")
    m.box((5, 3, 0), (11, 5, 5.5), stone_plain, faces="EWD", key="jaw")
    m.raw(E((5, 3, 0), (11, 5, 0), {"N": (fv, uv_n(5, 3, 11, 5))}))
    m.box((5, 5, 0), (11, 5, 5.5), wet, faces="U", key="tongue")
    m.box((4, 7, 0), (12, 11, 5.5), stone, faces="EWUS", key="skull")
    m.raw(E((4, 7, 0), (12, 11, 0), {"N": (fv, uv_n(4, 7, 12, 11))}))
    m.box((4, 7, 0), (12, 7, 5.5), wet, faces="D", key="palate")
    m.box((4, 10, -0.8), (12, 11, 0), stone, faces="EWUD", key="brow")
    m.raw(E((4, 10, -0.8), (12, 11, -0.8), {"N": (fv, uv_n(4, 10, 12, 11))}))
    m.box((6, 8, -1), (10, 9, 0), stone, faces="EWUD", key="nose")
    m.raw(E((6, 8, -1), (10, 9, -1), {"N": (fv, uv_n(6, 8, 10, 9))}))
    for x in (5, 10):                                           # fangs hanging from the lip, and two rising from the jaw
        m.box((x + 0.1, 5.8, 0.2), (x + 0.9, 7, 1.0), tooth, faces="NSEW", key="fang_up")
    for x in (6.5, 8.5):
        m.box((x + 0.1, 5, 0.2), (x + 0.9, 6.0, 1.0), tooth, faces="NSEWU", key="fang_dn")
    for x0, x1 in ((4, 5.2), (10.8, 12)):                       # little horns, swept back
        m.box((x0, 11, 1.2), (x1, 12.2, 2.6), stone_plain, faces="NSEWU", key="horn1")
        m.box((x0 + (0.2 if x0 < 5 else 0), 12.2, 1.8), (x1 - (0.2 if x0 > 5 else 0), 13, 2.6), stone_plain, faces="NSEWU", key="horn2")
    return finish(w, m.build(ao=True))


@gen
def g_spout(w):
    w.texture("wall_spout_face", spout_face())
    w.model("wall_spout", spout_model(w))
    w.state("wall_spout", {f"facing={f}": rot_variant("wall_spout", f) for f in FACINGS})


# ---------------------------------------------------------------------------------------------------------------------
# 6. wall_shield. A painted heater shield 14 px wide (x 1-15), full height, standing 2 px off the wall: a raised bronze rim
#    (z 14-16) round a field (z 14.5-16), real stepped geometry so the silhouette is a shield and not a square. The arms are
#    the banner_cloth designs of city_street.py (same colours, same emblems, as a chief over the field), and the city's own: a
#    gold bell on deep blue over a white wavy band, the river. The paint is old: chipped to the bare timber, faded at the top,
#    dirtied at the point, scratched.

# half-open column spans of the heater, per image row (image col c is block x = 16 - c - 1; rows run top down)
SPANS = {**{r: (1, 15) for r in range(10)}, 10: (2, 14), 11: (2, 14), 12: (3, 13), 13: (4, 12), 14: (5, 11), 15: (7, 9)}
# the city's bell for its own arms: eight rows, flared lip and clapper, no blank row between (it sits over the river)
CARILLON_BELL = ["...oo...", "..oooo..", ".oooooo.", ".oooooo.", ".oooooo.", ".oooooo.", "oooooooo", "...oo..."]
SHIELD_BARE = R("#1f160d", "#32241a", "#483524", "#5d4630")           # the timber showing where the paint has gone
SHIELD_STEEL = R("#2b2d31", "#44484e", "#63676e")


def shield_mask():
    return {(x, r) for r, (x0, x1) in SPANS.items() for x in range(x0, x1)}


def shield_ring(mask):
    return {(x, y) for (x, y) in mask if any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def _rect(c, x0, y0, x1, y1, col):
    for y in range(y0, y1):
        for x in range(x0, x1):
            c.set(x, y, col)


def shield_field(arms, rng):
    """The painted field and emblem of one set of arms on a transparent 16x16 (the rim is added after)."""
    c = Canvas()
    mask = shield_mask()
    ring = shield_ring(mask)
    fieldpx = mask - ring
    if arms == "carillon":
        main, second = (30, 56, 122), (226, 222, 204)
        M = cloth_ramp_fine(main)
        W2 = cloth_ramp_fine(second, cool=0.03)
        for (x, y) in fieldpx:
            c.set(x, y, step(M, 3))
        tmp = Canvas()
        shaded_sprite(tmp, 4, 2, CARILLON_BELL, {"o": "e"}, {"e": GILT})
        for (x, y) in fieldpx:
            px = tmp.get(x, y)
            if px[3]:
                c.set(x, y, px)
        for x in range(3, 13):                                 # the river: a white wavy band under the bell, two rows thick
            yy = 10 if (x // 2) % 2 == 0 else 11
            for y in (yy, yy + 1):
                if (x, y) in fieldpx:
                    c.set(x, y, step(W2, 4 if y == yy else 2))
        return c, fieldpx
    main_c, sec_c, emb_c, acc_c = DESIGN_COLOURS[arms]
    M, S2, EM, AC = (cloth_ramp_fine(main_c), cloth_ramp_fine(sec_c, cool=0.03), cloth_ramp_fine(emb_c, cool=0.03),
                     cloth_ramp_fine(acc_c, cool=0.03))
    for (x, y) in fieldpx:
        c.set(x, y, step(M, 3))
    for (x, y) in fieldpx:                                      # the chief: the second colour in two rows across the top
        if y in (1, 2):
            c.set(x, y, step(S2, 3 if y == 1 else 2))
    em = EMBLEMS[EMBLEM_OF[arms]]
    ox, oy = 4, 4
    tmp = Canvas()
    shaded_sprite(tmp, ox, oy, em, {"o": "e", "k": "m", "a": "a", "h": "e", "g": "g", "w": "e"},
                  {"e": EM, "m": M, "a": AC, "g": cloth_ramp_fine((70, 130, 70))})
    px_of = {}
    for y in range(16):
        for x in range(16):
            px = tmp.get(x, y)
            if px[3] and (x, y) in fieldpx:
                px_of[(x, y)] = px
    if arms == "association":                                   # a bell over crossed blades, as on the banner
        for (x, y) in list(px_of):
            if y >= oy + 5:
                del px_of[(x, y)]
        for (x0, y0, x1, y1) in ((ox, oy + 5, ox + 7, oy + 8), (ox + 7, oy + 5, ox, oy + 8)):
            n = max(abs(x1 - x0), abs(y1 - y0))
            for t in range(n + 1):
                xx, yy = x0 + (x1 - x0) * t // n, y0 + (y1 - y0) * t // n
                if (xx, yy) in fieldpx:
                    px_of[(xx, yy)] = step(EM, 4 if t % 2 else 3)
        for (xx, yy) in ((ox, oy + 5), (ox + 7, oy + 5), (ox, oy + 8), (ox + 7, oy + 8)):
            if (xx, yy) in fieldpx:
                px_of[(xx, yy)] = step(AC, 4)
    for q, px in px_of.items():
        c.set(q[0], q[1], px)
    return c, fieldpx


def shield_texture(arms):
    rng = rng_for(SEED, "shield", arms)
    c, fieldpx = shield_field(arms, rng)
    mask = shield_mask()
    ring = shield_ring(mask)
    # weathering of the paint: a fade at the top, grime at the point, then chips and scratches
    for (x, y) in fieldpx:
        px = c.get(x, y)
        if y <= 4:
            px = lerp(px, (226, 220, 200), 0.07)
        if y >= 11:
            px = lerp(px, (60, 44, 30), 0.14)
        if (x * 5 + y * 3) % 7 == 0 and rng.random() < 0.5:
            px = mul(px, 0.93)
        c.set(x, y, px)
    for _ in range(9):                                          # chips: the paint gone to bare timber, mostly near the rim
        cx, cy = rng.choice(sorted(fieldpx))
        near_edge = any((cx + dx, cy + dy) in ring for dx in (-1, 0, 1) for dy in (-1, 0, 1))
        if not near_edge and rng.random() < 0.65:
            continue
        for (dx, dy) in rng.choice([[(0, 0)], [(0, 0), (1, 0)], [(0, 0), (0, 1)], [(0, 0), (1, 0), (0, 1)]]):
            q = (cx + dx, cy + dy)
            if q in fieldpx:
                c.set(q[0], q[1], step(SHIELD_BARE, rng.choice([1, 2, 2, 3])))
    for _ in range(3):                                          # scratches: a light line of exposed gesso, one pixel wide
        sx, sy = rng.choice(sorted(fieldpx))
        dx = rng.choice([-1, 1])
        for t in range(rng.randrange(3, 5)):
            q = (sx + t * dx, sy + t)
            if q in fieldpx and c.get(*q)[3]:
                c.set(q[0], q[1], lerp(c.get(*q), (232, 226, 206), 0.5))
    for (x, y) in ring:                                         # the bronze rim: lit top-left, shaded lower-right, a rivet at the corners
        k = 3
        if (x - 1, y) not in mask or (x, y - 1) not in mask:
            k += 1
        if (x + 1, y) not in mask or (x, y + 1) not in mask:
            k -= 1
        if rng.random() < 0.2:
            k += rng.choice([-1, 1])
        col = step(BRONZE, k)
        if rng.random() < 0.12:
            col = step(VERDI, rng.choice([0, 1, 1, 2]))
        c.set(x, y, col)
    for q in ((2, 0), (13, 0), (2, 9), (13, 9)):                # rivets: brighter than the rim
        if q in ring:
            c.set(q[0], q[1], step(BRONZE, 5))
    return c


def shield_rim_texture():
    rng = rng_for(SEED, "shield_rim")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = 3 + (1 if y % 5 == 0 else 0) - (1 if y % 5 == 3 else 0)
            if rng.random() < 0.22:
                k += rng.choice([-1, 1])
            col = step(BRONZE, k)
            if rng.random() < 0.1:
                col = step(VERDI, rng.choice([0, 1, 2]))
            c.set(x, y, col)
    return c


def shield_model(arms):
    mask = shield_mask()
    ring = shield_ring(mask)
    field = mask - ring
    tn = f"wall_shield_{arms}"
    els = []
    # image rect (col c0..c1, row r0..r1) -> block x 16-c1..16-c0, y 16-r1..16-r0
    def box(rect, z0, z1, faces):
        c0, r0, c1, r1 = rect
        x0, x1, y0, y1 = 16 - c1, 16 - c0, 16 - r1, 16 - r0
        f = {"N": ("#paint", [c0, r0, c1, r1])}
        if "E" in faces:
            f["E"] = ("#rim", [0, 0, z1 - z0, r1 - r0])
            f["W"] = ("#rim", [0, 0, z1 - z0, r1 - r0])
        if "U" in faces:
            f["U"] = ("#rim", [0, 0, c1 - c0, z1 - z0])
            f["D"] = ("#rim", [0, 0, c1 - c0, z1 - z0])
        els.append(E((x0, y0, z0), (x1, y1, z1), f))
    for rect in merge_rects(field):
        box(rect, 14.5, 16, "")
    for rect in merge_rects(ring):
        box(rect, 14, 16, "EWUD")
    return {"parent": PARENT, "ambientocclusion": False,
            "textures": {"particle": b(f"wall_shield_rim"), "paint": b(tn), "rim": b("wall_shield_rim")}, "elements": els}


@gen
def g_shield(w):
    w.texture("wall_shield_rim", shield_rim_texture())
    variants = {}
    for arms in ARMS:
        w.texture(f"wall_shield_{arms}", shield_texture(arms))
        w.model(f"wall_shield_{arms}", finish(w, shield_model(arms)))
        for facing in FACINGS:
            variants[f"arms={arms},facing={facing}"] = rot_variant(f"wall_shield_{arms}", facing)
    w.state("wall_shield", variants)


# @@ NEXT SECTION @@
# @@ GENERATORS END @@

# ---------------------------------------------------------------------------------------------------------------------
# registry

ARMS = ["carillon", "bellwatch", "quay", "market", "terrace", "works", "garden", "wallring", "association"]
TONES = ["green", "dark", "autumn"]
# the Java properties (WallBlocks.java); the blockstates must cover exactly their product
PROPS = {
    "arrow_loop": {"facing": FACINGS, "style": STYLES, "stone": list(STONES), "part": PARTS3},
    "wall_ivy": {"facing": FACINGS, "density": ["0", "1", "2"], "tone": TONES},
    "portcullis": {"axis": ["x", "z"], "part": ["top", "middle", "bottom"]},
    "brazier": {"facing": FACINGS, "mount": ["floor", "wall"]},
    "wall_spout": {"facing": FACINGS},
    "wall_shield": {"facing": FACINGS, "arms": ARMS},
}
LANG = {"arrow_loop": "Arrow Loop", "wall_ivy": "Wall Ivy", "portcullis": "Portcullis", "brazier": "Brazier",
        "wall_spout": "Wall Spout", "wall_shield": "Wall Shield"}
AXE = ["wall_ivy", "portcullis", "wall_shield"]
PICKAXE = ["arrow_loop", "brazier", "wall_spout"]

_WORLD = None


def everything():
    global _WORLD
    if _WORLD is None:
        w = WallWorld()
        for g in GENERATORS:
            g(w)
        _WORLD = w
    return _WORLD


def textures():
    return everything().tex


def mcmeta(w, name):
    ft, interp = w.meta[name]
    a = {"frametime": ft, "interpolate": interp}
    if name in w.timing:
        a["frames"] = w.timing[name]
    return {"animation": a}


def build(out):
    w = everything()
    for name, c in w.tex.items():
        if isinstance(c, list):
            ft, interp = w.meta[name]
            out.block(name, c, ft)
            path = os.path.join(out.root, "assets/awaken/textures/block", name + ".png.mcmeta")
            with open(path, "w") as m:
                json.dump(mcmeta(w, name), m)
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
    print(f"city_wall: content written ({len(w.models)} models, {len(w.states)} blockstates)")


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


def check_loops(errors, notes):
    """Every arrow loop shape tiles its cube exactly: on each outer face the quads plus the cut leave no gap and no overlap."""
    nq = 0
    for shape in SHAPES:
        mask = loop_mask(shape)
        quads = mesh_cut_cube(mask)
        nq += len(quads)
        cover = {d: {} for d in D6}
        rec = set()
        for (d, kind, lo, hi) in quads:
            if kind == "ext":
                if d in "NS":
                    cells = [(x, y) for x in range(int(lo[0]), int(hi[0])) for y in range(int(lo[1]), int(hi[1]))]
                elif d in "EW":
                    cells = [(z, y) for z in range(int(lo[2]), int(hi[2])) for y in range(int(lo[1]), int(hi[1]))]
                else:
                    cells = [(x, z) for x in range(int(lo[0]), int(hi[0])) for z in range(int(lo[2]), int(hi[2]))]
                for cell in cells:
                    cover[d][cell] = cover[d].get(cell, 0) + 1
            elif kind == "recess":
                rec |= {(x, y) for x in range(int(lo[0]), int(hi[0])) for y in range(int(lo[1]), int(hi[1]))}
        cut_n = set(mask)
        for d in "NS":
            for x in range(16):
                for y in range(16):
                    want = 0 if (d == "N" and (x, y) in cut_n) else 1
                    if cover[d].get((x, y), 0) != want:
                        errors.append(f"loop {shape}: face {d} cell {(x, y)} covered {cover[d].get((x, y), 0)}x, want {want}")
        for d, row in (("U", 15), ("D", 0)):
            hole = {x for (x, y) in cut_n if y == row}
            for x in range(16):
                for z in range(16):
                    want = 0 if (x in hole and z < DEPTH) else 1
                    if cover[d].get((x, z), 0) != want:
                        errors.append(f"loop {shape}: face {d} cell {(x, z)} covered {cover[d].get((x, z), 0)}x, want {want}")
        for d in "EW":
            for z in range(16):
                for y in range(16):
                    if cover[d].get((z, y), 0) != 1:
                        errors.append(f"loop {shape}: face {d} cell {(z, y)} covered {cover[d].get((z, y), 0)}x, want 1")
        if rec != cut_n:
            errors.append(f"loop {shape}: the recess is not exactly the cut")
    notes.append(f"arrow loops: {len(SHAPES)} shapes tile their cube exactly ({nq} quads in all)")


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
    for ref in sorted(used_models):
        ns, path = ref.split(":")
        p = os.path.join(ASSETS, "models", path + ".json")
        if not os.path.exists(p):
            errors.append(f"missing model {ref}")
            continue
        m = json.load(open(p))
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
                errors.append(f"{ref}: texture {t} not found (run build_all.py city_wall)")
        if "elements" not in m:
            errors.append(f"{ref}: no elements")
            continue
        for ei, el in enumerate(m["elements"]):
            for vec_ in (el["from"], el["to"]):
                if any(not (-16 <= v <= 32) for v in vec_):
                    errors.append(f"{ref}: element {ei} outside -16..32: {vec_}")
            if "rotation" in el:
                r = el["rotation"]
                if r.get("axis") not in ("x", "y", "z") or r.get("angle") not in (-45, -22.5, 0, 22.5, 45):
                    errors.append(f"{ref}: element {ei} bad rotation {r}")
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
    nanim = 0
    for name, c in w.tex.items():
        p = os.path.join(TEX, name + ".png")
        if not os.path.exists(p):
            errors.append(f"texture {name}.png not written (run build_all.py city_wall)")
            continue
        im = Image.open(p)
        if isinstance(c, list):
            nanim += 1
            if im.size != (16, 16 * len(c)):
                errors.append(f"animated texture {name} is {im.size}, expected 16x{16 * len(c)}")
            mp = p + ".mcmeta"
            if not os.path.exists(mp):
                errors.append(f"{name}: missing .mcmeta")
            elif json.load(open(mp)) != json.loads(json.dumps(mcmeta(w, name))):
                errors.append(f"{name}: .mcmeta is {json.load(open(mp))}, expected {mcmeta(w, name)}")
            for fr in c:
                for yy in range(16):
                    for xx in range(16):
                        if fr.get(xx, yy)[3] not in (0, 255):
                            errors.append(f"{name}: partial alpha at {(xx, yy)}")
                            break
        elif im.size != (16, 16):
            errors.append(f"texture {name} is {im.size}")
    notes.append(f"{len(w.tex)} textures ({nanim} animated)")
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
    check_loops(errors, notes)
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
# the renderer: the street kit's rasteriser (cs.render) fed from this spec's own world, with uvlock and per-item animation
# frames and weighted-variant picks added

_IMG = {}


def tex_image(ref, frame=0):
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


def resolve_state(block, props, pick=0):
    w = everything()
    want = {k: str(v) for k, v in props.items()}
    for k, v in w.states[block].items():
        have = dict(p.split("=") for p in k.split(",")) if k else {}
        if all(have.get(p) == q for p, q in want.items()) and set(have) == set(want):
            return v[pick % len(v)] if isinstance(v, list) else v
    raise KeyError(f"{block} {props}")


MOCK_MODELS = {}


def _uvlock_uv(d, yrot, uv):
    u, v = uv
    r = yrot % 360
    if r == 0:
        return uv
    if d == "U":
        return {90: (16 - v, u), 180: (16 - u, 16 - v), 270: (v, 16 - u)}[r]
    return {90: (v, 16 - u), 180: (16 - u, 16 - v), 270: (16 - v, u)}[r]


def model_quads(model_ref, xrot, yrot, offset, frame=0, uvlock=False):
    """Quads (4 corner points, 4 uv, image, shade, emit, normal) of a model, rotated by the blockstate and placed at offset (px)."""
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
            if "arrow_loop" in model_ref and 0.01 < pts[0][ax] < 15.99:
                sh *= 0.6                                # inside the cut: stands in for the game's ambient occlusion (mock only)
            ref = _resolve(texmap, f["texture"])
            img = tex_image(ref, frame)
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            if uvlock and yrot and L in "UD":
                uvs = [_uvlock_uv(L, yrot, p) for p in uvs]
            for _ in range(f.get("rotation", 0) // 90):
                uvs = [uvs[3], uvs[0], uvs[1], uvs[2]]
            pts = [(p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]) for p in pts]
            quads.append((pts, uvs, img, sh, emit, nrm))
    return quads


def placed_block(block, props, cell, frame=0, pick=0):
    v = resolve_state(block, props, pick)
    return model_quads(v["model"], v.get("x", 0), v.get("y", 0), (cell[0] * 16, cell[1] * 16, cell[2] * 16), frame,
                       bool(v.get("uvlock")))


def mock_box(cell, a, b_, tex, faces="NESWUD"):
    """A stand-in cuboid (vanilla texture) for scenery."""
    fd = {}
    for L in faces:
        fd[FACE_NAMES[L]] = {"uv": [0, 0, 16, 16], "texture": "#t"}
    m = {"textures": {"t": tex}, "elements": [{"from": list(a), "to": list(b_), "faces": fd}]}
    name = f"box{len(MOCK_MODELS)}"
    MOCK_MODELS[name] = m
    return model_quads("mock:" + name, 0, 0, (cell[0] * 16, cell[1] * 16, cell[2] * 16))


def blocks_quads(items, frame=0):
    q = []
    for it in items:
        if it[0] == "box":
            q += mock_box(it[1], it[2], it[3], it[4])
        else:
            opts = it[3] if len(it) > 3 else {}
            q += placed_block(it[0], it[1], it[2], opts.get("frame", frame), opts.get("pick", 0))
    return q


def view_cmd(out, block, kv):
    """One block, every value of the one property given as k=*, at the other properties' first values."""
    pr = PROPS[block]
    fixed = {k: v[0] for k, v in pr.items()}
    sweep = None
    opts = {"S": 5, "yaw": 28, "pitch": 26, "night": 1.0, "gap": 1.25, "frame": 0, "pick": 0}
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
        items.append((block, p, (i * opts["gap"], 0, 0), {"pick": opts["pick"]}))
    im = render(blocks_quads(items, opts["frame"]), yaw=opts["yaw"], pitch=opts["pitch"], S=opts["S"], night=opts["night"])
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    im.save(out)
    print(out, im.size)


# ----- scene library (stand-in scenery uses vanilla textures) ------------------------------------------------------------

SB = "minecraft:block/stone_bricks"
MSB = "minecraft:block/mossy_stone_bricks"
DSB = "minecraft:block/deepslate_bricks"
COB = "minecraft:block/cobblestone"
GRASS = "minecraft:block/dirt"


def cube(x, y, z, tex=SB):
    return ("box", (x, y, z), (0, 0, 0), (16, 16, 16), tex)


def wallx(x0, x1, y0, y1, z=0, tex=SB):
    """A wall running along x, one block thick at row z."""
    return [cube(x, y, z, tex) for x in range(x0, x1) for y in range(y0, y1)]


def wallz(z0, z1, y0, y1, x=0, tex=SB):
    return [cube(x, y, z, tex) for z in range(z0, z1) for y in range(y0, y1)]


def floor(x0, x1, z0, z1, y=-1, tex=COB):
    return [cube(x, y, z, tex) for x in range(x0, x1) for z in range(z0, z1)]


def hole(items, cells):
    """Drop the stand-in cubes at these cells (a block goes there instead)."""
    cs_ = set(cells)
    return [it for it in items if not (it[0] == "box" and it[1] in cs_)]


SCENES = {}
SCENE_ORDER = []


def scene(title):
    def deco(fn):
        SCENE_ORDER.append((title, fn))
        return fn
    return deco


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
    MAXW, GAP = 3100, 12
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
        d.text((px, py), title[:130], fill=(230, 232, 240, 255))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out, sheet.size)


# ---------------------------------------------------------------------------------------------------------------------
# scenes

STONE_TEX = {"stone_bricks": SB, "mossy": MSB, "deepslate": DSB, "calcite": "minecraft:block/calcite",
             "bricks": "minecraft:block/bricks", "tuff": "minecraft:block/tuff_bricks"}


def loop(items, x, y, z, style, part, stone="stone_bricks", facing="north"):
    items.append(("arrow_loop", {"facing": facing, "style": style, "stone": stone, "part": part}, (x, y, z)))
    return (x, y, z)


@scene("arrow loops in a stone-brick wall: every style, singles and stacked pairs, seen from outside (facing north), the pattern running on into the wall")
def scene_loops_front():
    items = floor(-1, 12, -2, 3) + wallx(-1, 12, 0, 4, z=0)
    cells = []
    for i, st in enumerate(STYLES):
        cells.append(loop(items, i * 2, 1, 0, st, "single"))
    for i, st in enumerate(STYLES):
        cells.append(loop(items, 9 - 0 * i, 0, 0, "slit", "single")) if False else None
    cells.append(loop(items, 9, 1, 0, "slit", "lower")); cells.append(loop(items, 9, 2, 0, "slit", "upper"))
    cells.append(loop(items, 10, 1, 0, "cross", "lower")); cells.append(loop(items, 10, 2, 0, "cross", "upper"))
    cells.append(loop(items, 11, 1, 0, "oillet", "lower")); cells.append(loop(items, 11, 2, 0, "oillet", "upper"))
    cells.append(loop(items, 8, 1, 0, "window", "lower")); cells.append(loop(items, 8, 2, 0, "window", "upper"))
    return hole(items, cells), dict(yaw=0, pitch=0, S=7)


@scene("the same wall at an angle: reveals, the dark recess 12 px in, the projecting sill of the window (reveals are shown darker here: the game's ambient occlusion does that)")
def scene_loops_angle():
    items, kw = scene_loops_front()
    return items, dict(yaw=-34, pitch=20, S=6)


@scene("one cross loop and one window in each of the six stones: stone bricks, mossy, deepslate, calcite, bricks, tuff")
def scene_loops_stones():
    items = floor(-1, 13, -2, 3)
    cells = []
    for i, (stone, tex) in enumerate(STONE_TEX.items()):
        x0 = i * 2
        items += [cube(x, y, 0, tex) for x in range(x0, x0 + 2) for y in range(0, 3)]
        cells.append(loop(items, x0, 1, 0, "cross", "single", stone))
        cells.append(loop(items, x0 + 1, 1, 0, "window", "single", stone))
    return hole(items, cells), dict(yaw=-18, pitch=14, S=6)


@scene("a tower with a loop on each of its four faces (facing north, east, south, west: uvlock keeps the courses level) and a window under the parapet")
def scene_loops_tower():
    items = floor(-2, 7, -2, 7)
    cells = []
    for y in range(0, 5):
        for x in range(1, 5):
            for z in range(1, 5):
                if x in (1, 4) or z in (1, 4):
                    items.append(cube(x, y, z))
    cells.append(loop(items, 2, 2, 1, "slit", "lower", facing="north")); cells.append(loop(items, 2, 3, 1, "slit", "upper", facing="north"))
    cells.append(loop(items, 3, 2, 1, "cross", "single", facing="north"))
    cells.append(loop(items, 4, 2, 2, "oillet", "single", facing="east")); cells.append(loop(items, 4, 2, 3, "window", "lower", facing="east"))
    cells.append(loop(items, 4, 3, 3, "window", "upper", facing="east"))
    cells.append(loop(items, 3, 3, 4, "window", "single", facing="south")); cells.append(loop(items, 2, 3, 4, "cross", "single", facing="south"))
    cells.append(loop(items, 1, 2, 3, "slit", "single", facing="west")); cells.append(loop(items, 1, 2, 2, "oillet", "single", facing="west"))
    return hole(items, cells), dict(yaw=40, pitch=24, S=6)


@scene("wall ivy: density 0, 1, 2 across, green, dark and autumn down; the two texture variants alternate so a patch does not tile")
def scene_ivy():
    items = floor(-1, 9, -2, 3) + wallx(-1, 9, 0, 5, z=1)
    tones = ("green", "dark", "autumn")
    for r, tone in enumerate(tones):
        for d in range(3):
            for k in range(2):
                items.append(("wall_ivy", {"facing": "north", "density": d, "tone": tone}, (d * 2 + k, 3 - r, 0), {"pick": (k + r) % 2}))
    for d in range(3):
        items.append(("wall_ivy", {"facing": "north", "density": 2 - d, "tone": "green"}, (7, d, 0), {"pick": d % 2}))
    return items, dict(yaw=-22, pitch=14, S=6)


@scene("ivy over a tower corner and a gate: a patch growing out (density 2, 1, 0), dark old growth on the shaded side")
def scene_ivy_patch():
    items = floor(-2, 8, -2, 4) + wallx(-2, 8, 0, 5, z=1) + wallz(1, 4, 0, 5, x=8)
    patch = {(2, 0): 2, (3, 0): 2, (4, 0): 2, (2, 1): 2, (3, 1): 2, (4, 1): 1, (2, 2): 2, (3, 2): 1, (4, 2): 0, (3, 3): 0,
             (1, 0): 1, (5, 0): 1, (1, 1): 0, (5, 1): 0, (2, 3): 0, (6, 0): 0}
    for (x, y), d in patch.items():
        items.append(("wall_ivy", {"facing": "north", "density": d, "tone": "green"}, (x, y, 0), {"pick": (x * 3 + y) % 2}))
    for y, d in ((0, 2), (1, 2), (2, 1), (3, 0)):
        items.append(("wall_ivy", {"facing": "north", "density": d, "tone": "dark"}, (-1, y, 0), {"pick": y % 2}))
    return items, dict(yaw=-26, pitch=18, S=6)


@scene("the gatehouse portcullis: top (iron-bound head beam), middle, bottom (spiked); axis x and axis z")
def scene_portcullis():
    items = floor(-2, 9, -3, 4)
    items += wallx(-2, 6, 0, 4, z=0)
    items = hole(items, [(x, y, 0) for x in range(0, 4) for y in range(0, 4)])
    for x in range(0, 4):
        items.append(("portcullis", {"axis": "x", "part": "top"}, (x, 3, 0)))
        items.append(("portcullis", {"axis": "x", "part": "middle"}, (x, 2, 0)))
        items.append(("portcullis", {"axis": "x", "part": "middle"}, (x, 1, 0)))
        items.append(("portcullis", {"axis": "x", "part": "bottom"}, (x, 0, 0)))
    # a second gate, axis z, raised a block, in the wall running the other way
    items += wallz(-3, 4, 0, 4, x=7)
    items = hole(items, [(7, y, z) for z in range(0, 2) for y in range(0, 4)])
    for z in range(0, 2):
        items.append(("portcullis", {"axis": "z", "part": "top"}, (7, 3, z)))
        items.append(("portcullis", {"axis": "z", "part": "middle"}, (7, 2, z)))
        items.append(("portcullis", {"axis": "z", "part": "bottom"}, (7, 1, z)))
    return items, dict(yaw=-30, pitch=24, S=6)


@scene("braziers by day: floor (facings differ) and wall, frames 0, 3, 6, 9 of the 12-frame lick")
def scene_braziers_day():
    items = floor(-1, 7, -1, 3) + wallx(-1, 7, 0, 3, z=2)
    for i, fr in enumerate((0, 3, 6, 9)):
        items.append(("brazier", {"facing": ("north", "east", "south", "west")[i], "mount": "floor"}, (i, 0, 0), {"frame": fr}))
    for i, fr in enumerate((1, 5, 8)):
        items.append(("brazier", {"facing": "north", "mount": "wall"}, (4 + (i * 0.0), 1, 1), {"frame": fr})) if i == 0 else None
    items.append(("brazier", {"facing": "north", "mount": "wall"}, (4, 1, 1), {"frame": 2}))
    items.append(("brazier", {"facing": "north", "mount": "wall"}, (5, 1, 1), {"frame": 7}))
    return items, dict(yaw=-24, pitch=20, S=7)


@scene("braziers at night: the flame and coals stay full bright, the wall and ground go dark")
def scene_braziers_night():
    items, kw = scene_braziers_day()
    return items, dict(yaw=-24, pitch=20, S=7, night=0.22)


@scene("wall spouts under a parapet and the nine shields (carillon, bellwatch, quay, market, terrace, works, garden, wallring, association)")
def scene_spouts_shields():
    items = floor(-1, 10, -2, 3) + wallx(-1, 10, 0, 5, z=1)
    for i, arms in enumerate(ARMS):
        items.append(("wall_shield", {"facing": "north", "arms": arms}, (i, 2, 0)))
    for x in (1, 4, 7):
        items.append(("wall_spout", {"facing": "north"}, (x, 4, 0)))
    for x in range(-1, 10):                                   # a parapet course above
        items.append(cube(x, 5, 1))
    return items, dict(yaw=-20, pitch=16, S=6)


@scene("the shields close up, in a row on a wall, seen square on")
def scene_shields_front():
    items = wallx(-1, 10, 0, 2, z=1)
    for i, arms in enumerate(ARMS):
        items.append(("wall_shield", {"facing": "north", "arms": arms}, (i, 0, 0)))
    items.append(("wall_shield", {"facing": "north", "arms": "carillon"}, (4, 1, 0)))
    return items, dict(yaw=0, pitch=0, S=8)


@scene("closeup: a floor brazier, a wall brazier and a spout, at 14x")
def scene_closeups():
    items = [cube(0, 0, 1), cube(1, 0, 1), cube(2, 0, 1), cube(0, 1, 1), cube(1, 1, 1), cube(2, 1, 1)]
    items.append(("brazier", {"facing": "north", "mount": "floor"}, (-1.1, 0, 0), {"frame": 2}))
    items.append(("brazier", {"facing": "north", "mount": "wall"}, (0, 1, 0), {"frame": 6}))
    items.append(("wall_spout", {"facing": "north"}, (1, 1, 0)))
    items.append(("wall_shield", {"facing": "north", "arms": "carillon"}, (2, 0, 0)))
    return items, dict(yaw=-28, pitch=24, S=14, night=0.7)


@scene("a gate tower at dusk: loops, windows, braziers, shields, ivy, spouts and the portcullis together")
def scene_gate_tower():
    items = floor(-3, 13, -5, 4)
    # the curtain wall along z=0 from x -3..12, 5 high; the gate x 4..6 (3 wide), 3 high; towers x 0..3 and 7..10, 2 thick in front
    items += wallx(-3, 13, 0, 5, z=0)
    cells = [(x, y, 0) for x in range(4, 7) for y in range(0, 4)]
    for tx in (0, 7):
        for x in range(tx, tx + 3):
            for y in range(0, 8):
                for z in (-1, -2):
                    items.append(cube(x, y, z, SB if y < 6 else SB))
    for x in range(-3, 13):                                    # the wall walk parapet
        if (x % 2 == 0) and not (0 <= x < 3 or 7 <= x < 10):
            items.append(cube(x, 5, 0))
    items = hole(items, cells)
    for x in range(4, 7):
        items.append(("portcullis", {"axis": "x", "part": "top"}, (x, 3, 0)))
        items.append(("portcullis", {"axis": "x", "part": "middle"}, (x, 2, 0)))
        items.append(("portcullis", {"axis": "x", "part": "middle"}, (x, 1, 0)))
        items.append(("portcullis", {"axis": "x", "part": "bottom"}, (x, 0, 0)))
    cl = []
    for tx in (0, 7):
        cl.append(loop(items, tx + 1, 2, -2, "slit", "lower")); cl.append(loop(items, tx + 1, 3, -2, "slit", "upper"))
        cl.append(loop(items, tx + 1, 5, -2, "window", "lower")); cl.append(loop(items, tx + 1, 6, -2, "window", "upper"))
        cl.append(loop(items, tx, 4, -2, "cross", "single")); cl.append(loop(items, tx + 2, 4, -2, "oillet", "single"))
    items = hole(items, cl)
    items.append(("wall_shield", {"facing": "north", "arms": "carillon"}, (5, 4, -1)))
    items.append(("wall_shield", {"facing": "north", "arms": "wallring"}, (3, 2, -3)))
    items.append(("wall_shield", {"facing": "north", "arms": "association"}, (7, 2, -3)))
    for (x, fr) in ((3, 2), (7, 6)):
        items.append(("brazier", {"facing": "north", "mount": "floor"}, (x - 0 if x == 3 else x, 0, -3), {"frame": fr}))
    items.append(("brazier", {"facing": "north", "mount": "wall"}, (4, 2, -1), {"frame": 4}))
    items.append(("brazier", {"facing": "north", "mount": "wall"}, (6, 2, -1), {"frame": 9}))
    for x in (4, 6):
        items.append(("wall_spout", {"facing": "north"}, (x, 4, -1)))
    patch = {(10, 0): 2, (10, 1): 2, (10, 2): 1, (11, 0): 2, (11, 1): 1, (11, 2): 0, (12, 0): 1, (12, 1): 0, (9, 0): 0}
    for (x, y), d in patch.items():
        items.append(("wall_ivy", {"facing": "north", "density": d, "tone": "green"}, (x, y, -1), {"pick": (x + y) % 2}))
    return items, dict(yaw=-24, pitch=22, S=5, night=0.45)


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
        mock(sys.argv[2] if len(sys.argv) > 2 else "city_wall_mock.png")
    elif cmd == "view":
        view_cmd(sys.argv[2], sys.argv[3], sys.argv[4:])
    else:
        print(__doc__)
