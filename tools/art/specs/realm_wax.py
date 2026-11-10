"""The Wax family (SPEC 50.8): what the Hive's deep places add to Amber's. Cream and yellowed tallow run over soot-black rock, comb lit from inside, molten wax.

    python3 tools/art/build_all.py realm_wax        textures
    python3 tools/art/specs/realm_wax.py content    blockstates, models, item definitions, loot, lang, tags (lang and tags into src/main/merge/gates_wax)
    python3 tools/art/tone_check.py wax_            the family must stay lightable
    python3 tools/art/specs/realm_wax.py sheet PATH every texture at 6x, named

Drawn by code, nothing copied.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_depth as D  # noqa: E402

# Mid-toned on purpose (SPEC 50.8, "Textures that can be lit"): the wax reads pale because candles are on it, not because its
# texture is near white. tools/art/tone_check.py wax_ holds the family's middle between 95 and 150.
RAMPS = {
    "tallow": ["#6a5c3a", "#7f704a", "#94845c", "#aa9a70", "#c0b086", "#d6c8a0"],
    "yellowed": ["#5c4620", "#73592a", "#8a6d36", "#a08244", "#b59756", "#c8ac6c"],
    "soot": ["#1c1610", "#261e16", "#30271c", "#3b3024", "#473a2c", "#544536"],
    "sootb": ["#2a1e12", "#38291a", "#473523", "#57422d", "#684f38", "#7a5e44"],
    "molten": ["#8a3c04", "#b65a08", "#df7e12", "#f8a428", "#ffc95a", "#ffeba0"],
    "cellg": ["#a85c08", "#d07c10", "#f0a020", "#ffc548", "#ffe288", "#fff6cc"],
    "beads": ["#c08a30", "#dca848", "#f2c666", "#ffdf90", "#fff0bc", "#fffbe6"],
}

BLOCKS = {
    "wax_cold": ("Cold Wax", "hoe"),
    "wax_old": ("Yellowed Wax", "hoe"),
    "wax_run": ("Run Wax", "hoe"),
    "wax_comb": ("Comb Wall", "hoe"),
    "wax_cell": ("Lit Comb Cell", "hoe"),
    "wax_deck": ("Wax Tile", "pickaxe"),
    "wax_soot": ("Soot Rock", "pickaxe"),
    "wax_soot_band": ("Banded Soot Rock", "pickaxe"),
    "wax_bead_drip": ("Wax Beads", None),
    "wax_drip": ("Wax Drip", None),
    "wax_arch": ("Wax Arch", "hoe"),
    "wax_candle_bank": ("Bank of Candle Stubs", "hoe"),
    "wax_comb_cell": ("Open Comb Cell", "hoe"),
    "wax_comb_cell_lit": ("Honey Comb Cell", "hoe"),
    "wax_fall": ("Falling Wax", None),
    "wax_cold_slab": ("Cold Wax Slab", "hoe"),
    "wax_cold_stairs": ("Cold Wax Stairs", "hoe"),
}

B = K.B


def _P():
    return K.px(RAMPS)


def clear(P):
    return P.Canvas(16, 16)


def mottle(P, ramp, seed, lo=1.0, span=3.0, flecks=8):
    """Soft blotches of a ramp with a few paler flecks: set wax, old stone."""
    c = K.noisy(P, ramp, seed, scale=4, octaves=2, lo=0, span=1, jitter=0.0)
    rp = P.ramp(ramp)
    rng = P.rng_for("mottle", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            c.set(x, y, rp[max(0, min(5, int(lo + n[y][x] * span)))])
    for _ in range(flecks):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[min(5, int(lo + span) + 1)])
    return c


def tex_cell_back(P, lit):
    """What you see down a comb cell: a hexagon of old wax, or of honey with the light in it."""
    rim, inner = P.ramp("tallow"), P.ramp("cellg" if lit else "yellowed")
    rng = P.rng_for("cellback", lit)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = _hex_dist(x + 0.5, y + 0.5, 8, 8)
            if d > 5.2:
                c.set(x, y, rim[2 + rng.randrange(2)])
            elif d > 4.2:
                c.set(x, y, rim[4])
            else:
                v = int((4.6 if lit else 2.4) - d * (0.5 if lit else 0.3) + rng.random() * 0.8)
                c.set(x, y, inner[max(0, min(5, v))])
    return c


def tex_flame(P):
    """A candle flame, all of it lit: the bank's five small boxes show a pixel or two of this each."""
    rp = P.ramp("cellg")
    rng = P.rng_for("flame")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, rp[max(2, min(5, 5 - (y // 6) - (1 if rng.random() < 0.2 else 0)))])
    return c


def tex_run(P):
    """Long runs of wax that started somewhere above and set on the way down."""
    rp = P.ramp("tallow")
    rng = P.rng_for("run")
    c = P.Canvas(16, 16)
    x = 0
    while x < 16:
        w = 2 + rng.randrange(3)
        base = 1 + rng.randrange(4)
        for i in range(w):
            if x + i >= 16:
                break
            for y in range(16):
                v = base + (1 if i == 0 else 0) - (1 if i == w - 1 else 0)
                c.set(x + i, y, rp[max(0, min(5, v + (1 if rng.random() < 0.08 else 0)))])
        x += w
    return c


def _hex_dist(x, y, cx, cy):
    dx, dy = abs(x - cx), abs(y - cy)
    return max(dy, 0.866 * dx + 0.5 * dy)


def tex_comb(P, ramp="tallow"):
    """Comb wall: pale ridges, shallow darker cells, every block the same so the cells line up down a wall."""
    rp = P.ramp(ramp)
    rng = P.rng_for("comb" + ramp)
    c = P.Canvas(16, 16)
    centres = [(4, 3), (12, 3), (0, 11), (8, 11), (16, 11), (4, 19), (12, 19), (4, -5), (12, -5)]
    for y in range(16):
        for x in range(16):
            d = min(_hex_dist(x + 0.5, y + 0.5, cx, cy) for cx, cy in centres)
            v = 5 if d > 3.3 else 3 if d > 2.6 else 1 + (1 if rng.random() < 0.25 else 0)
            c.set(x, y, rp[v])
    return c


def tex_cell(P):
    """A comb cell full of light: a pale wall round a lit middle. The glow layer is the middle alone."""
    wall, gl = P.ramp("tallow"), P.ramp("cellg")
    rng = P.rng_for("cell")
    c, g = P.Canvas(16, 16), clear(P)
    for y in range(16):
        for x in range(16):
            d = _hex_dist(x + 0.5, y + 0.5, 8, 8)
            if d > 6.6:
                c.set(x, y, wall[3 + rng.randrange(2)])
            else:
                v = max(0, min(5, int(5.4 - d * 0.75 + rng.random() * 0.8)))
                c.set(x, y, gl[v])
                g.set(x, y, gl[v])
    return c, g


def tex_warm(P):
    """Molten wax with a skin just forming on it: pale veins in a loose hex net. All of it glows."""
    rp = P.ramp("molten")
    rng = P.rng_for("warm")
    n = P.value_noise(16, 16, rng, scale=5, octaves=2)
    centres = [(4, 3), (12, 3), (0, 11), (8, 11), (16, 11), (4, 19), (12, 19), (4, -5), (12, -5)]
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = min(_hex_dist(x + 0.5, y + 0.5, cx, cy) for cx, cy in centres)
            v = 4 if d > 3.5 else int(1 + n[y][x] * 2.4)
            c.set(x, y, rp[max(0, min(5, v))])
    return c


def tex_deck(P):
    """Laid tile: small hexes of cold wax, the odd one yellowed."""
    rp, old = P.ramp("tallow"), P.ramp("yellowed")
    rng = P.rng_for("deck")
    centres = [(4, 3), (12, 3), (0, 11), (8, 11), (16, 11), (4, 19), (12, 19), (4, -5), (12, -5)]
    tint = {cc: (old if rng.random() < 0.3 else rp, 2 + rng.randrange(3)) for cc in centres}
    tint[(16, 11)] = tint[(0, 11)]
    tint[(4, 19)], tint[(12, 19)] = tint[(4, 3)], tint[(12, 3)]
    tint[(4, -5)], tint[(12, -5)] = tint[(4, 3)], tint[(12, 3)]
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            cc = min(centres, key=lambda q: _hex_dist(x + 0.5, y + 0.5, q[0], q[1]))
            d = _hex_dist(x + 0.5, y + 0.5, cc[0], cc[1])
            r, v = tint[cc]
            c.set(x, y, P.ramp("soot")[5] if d > 3.5 else r[v])
    return c


def tex_beads(P):
    """A string of wax beads, each lit from inside."""
    rp = P.ramp("beads")
    c = clear(P)
    for y in range(16):
        c.set(8, y, rp[0])
        if y % 5 == 1:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if 0 <= y + dy < 16:
                        c.set(8 + dx, y + dy, rp[3 if dx or dy else 5])
    return c


def textures():
    P = _P()
    cell, cell_g = tex_cell(P)
    return {
        "wax_cold": mottle(P, "tallow", "cold", lo=1.6, span=2.6, flecks=8),
        "wax_old": mottle(P, "yellowed", "old", lo=1.0, span=3.2, flecks=10),
        "wax_run": tex_run(P),
        "wax_comb": tex_comb(P),
        "wax_cell": cell, "wax_cell_glow": cell_g,
        "wax_deck": tex_deck(P),
        "wax_soot": mottle(P, "soot", "soot", lo=0.8, span=3.4, flecks=12),
        "wax_soot_band": mottle(P, "sootb", "sootb", lo=0.8, span=3.4, flecks=12),
        "wax_bead_drip": tex_beads(P), "wax_bead_drip_glow": tex_beads(P),
        "wax_warm": tex_warm(P), "wax_warm_glow": tex_warm(P),
        "wax_comb_cell": tex_cell_back(P, False),
        "wax_comb_cell_lit": tex_cell_back(P, True),
        "wax_flame": tex_flame(P),
        "wax_fall": K.streaks(P, "molten", "wax-fall", frames=8, alpha=215),
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c)


COLD, RUN = B + "wax_cold", B + "wax_run"


def _drip(parts, dx=0, dz=0):
    """One drip from boxes of (half width, y0, y1), moved off the middle of its block."""
    return [K.box((8 - w + dx, y0, 8 - w + dz), (8 + w + dx, y1, 8 + w + dz), "#wax") for w, y0, y1 in parts]


# strand (carries on below), short, middle and long tips: 6 px across at the top, 2 at the tip
DRIP = {0: [(3, 0, 16)], 1: [(3, 13, 16), (1, 11, 13)], 2: [(3, 12, 16), (2, 9, 12), (1, 6, 9)], 3: [(3, 10, 16), (2, 5, 10), (1, 0, 5)]}


def drip_models():
    """Three looks for every length, so a lip of them isn't a row of the same drip: in the middle, off to one side, and two side by side."""
    m = {}
    for n, parts in DRIP.items():
        shorter = DRIP[max(1, n - 1)] if n else [(2, 0, 16)]
        m[f"wax_drip_{n}"] = K.model({"wax": RUN}, _drip(parts), ao=False)
        m[f"wax_drip_{n}_b"] = K.model({"wax": RUN}, _drip(parts, -3, 2), ao=False)
        m[f"wax_drip_{n}_c"] = K.model({"wax": RUN}, _drip(parts, 3, -3) + _drip(shorter, -4, 3), ao=False)
    return m


def arch_model():
    """A stepped quarter curve closing a top corner of an opening; the pier is to the north of it, the wall runs through east to west."""
    steps = [((0, 12, 0), (16, 16, 16)), ((0, 8, 0), (16, 12, 11)), ((0, 4, 0), (16, 8, 6)), ((0, 0, 0), (16, 4, 3))]
    return K.model({"wax": COLD, "run": RUN}, [K.box(a, b, "#wax") for a, b in steps[:2]] + [K.box(a, b, "#run") for a, b in steps[2:]])


def bank_model():
    """A puddle of wax and five stubs of different heights, each with its flame."""
    stubs = [(2, 2, 9), (10, 3, 6), (6, 7, 11), (2, 10, 5), (11, 10, 8)]
    els = [K.box((1, 0, 1), (15, 1, 15), "#wax")]
    for x, z, top in stubs:
        els.append(K.box((x, 1, z), (x + 3, top, z + 3), "#run"))
        els.append(K.box((x + 1, top, z + 1), (x + 2, top + 2, z + 2), "#flame", emission=15, sides=("up", "north", "south", "west", "east")))
    return K.model({"wax": COLD, "run": RUN, "flame": B + "wax_flame"}, els, ao=False, particle=COLD)


def cell_model(lit):
    """A comb cell on every face: a frame 3 px proud round a back 3 px in. Only the faces that can be seen are drawn."""
    back = B + ("wax_comb_cell_lit" if lit else "wax_comb_cell")
    els = [K.box((3, 3, 3), (13, 13, 13), "#back", emission=15 if lit else 0)]
    for ax in range(3):  # the twelve edges, four along each axis
        long_sides = {0: ("down", "up", "north", "south"), 1: ("north", "south", "west", "east"), 2: ("down", "up", "west", "east")}[ax]
        for u in (0, 13):
            for w in (0, 13):
                a, b = [0, 0, 0], [16, 16, 16]
                o = [i for i in range(3) if i != ax]
                a[o[0]], b[o[0]], a[o[1]], b[o[1]] = u, u + 3, w, w + 3
                els.append(K.box(a, b, "#frame", sides=long_sides))
    return K.model({"frame": COLD, "back": back}, els, particle=COLD)


def models():
    simple = ("wax_cold", "wax_old", "wax_run", "wax_comb", "wax_deck", "wax_soot", "wax_soot_band")
    m = {n: K.cube_model(n) for n in simple}
    m["wax_cell"] = K.cube_model("wax_cell", glow=True)
    m["wax_bead_drip"] = K.cross_model("wax_bead_drip", glow=True)
    m["wax_warm"] = K.cube_model("wax_warm", glow=True)
    m.update(drip_models())
    m["wax_arch"] = arch_model()
    m["wax_candle_bank"] = bank_model()
    m["wax_comb_cell"] = cell_model(False)
    m["wax_comb_cell_lit"] = cell_model(True)
    m["wax_fall"] = K.fall_model("wax_fall", 12)
    tex = {"bottom": COLD, "top": COLD, "side": COLD}
    m["wax_cold_slab"] = {"parent": "minecraft:block/slab", "textures": tex}
    m["wax_cold_slab_top"] = {"parent": "minecraft:block/slab_top", "textures": tex}
    m["wax_cold_stairs"] = {"parent": "minecraft:block/stairs", "textures": tex}
    m["wax_cold_stairs_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": tex}
    m["wax_cold_stairs_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": tex}
    return m


def states():
    cut = ("wax_drip", "wax_arch", "wax_cold_slab", "wax_cold_stairs")
    s = {n: K.simple_state(n) for n in BLOCKS if n not in cut}
    s["wax_warm"] = K.simple_state("wax_warm")
    s["wax_drip"] = {"variants": {f"length={n}": [{"model": B + f"wax_drip_{n}{v}"} for v in ("", "_b", "_c")] for n in DRIP}}
    s["wax_arch"] = {"variants": {f"facing={f}": {"model": B + "wax_arch", **({"y": y} if y else {})} for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
    s["wax_cold_slab"] = {"variants": {"type=bottom": {"model": B + "wax_cold_slab"}, "type=top": {"model": B + "wax_cold_slab_top"}, "type=double": {"model": COLD}}}
    s["wax_cold_stairs"] = D.stairs_state("wax_cold_stairs")
    return s


ITEM_MODEL = {"wax_drip": "wax_drip_3"}


def content():
    """As K.content, but the shared words and tags go to this gate's own merge folder, so three builders never write one file."""
    for name, data in models().items():
        K.write(os.path.join(K.ASSETS, f"models/block/{name}.json"), data)
    for name, data in states().items():
        K.write(os.path.join(K.ASSETS, f"blockstates/{name}.json"), data)
    for name in BLOCKS:
        K.write(os.path.join(K.ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": B + ITEM_MODEL.get(name, name)}})
        K.loot(name)
    D.slab_loot("wax_cold_slab")
    own = os.path.join(K.MERGE, "gates_wax")
    lang = {f"block.awaken.{k}": v[0] for k, v in BLOCKS.items()}
    lang["block.awaken.wax_warm"] = "Warm Wax"
    K.write(os.path.join(own, "assets/awaken/lang/en_us.json"), lang)
    for tool, tag in K.TOOLS.items():
        vals = [f"awaken:{k}" for k, v in BLOCKS.items() if v[1] == tool]
        if vals:
            K.write(os.path.join(own, f"data/minecraft/tags/block/{tag}.json"), {"values": vals})
    for kind, tag in (("_slab", "slabs"), ("_stairs", "stairs")):
        for what in ("block", "item"):
            K.write(os.path.join(own, f"data/minecraft/tags/{what}/{tag}.json"), {"values": [f"awaken:wax_cold{kind}"]})
    print(f"realm_wax: content written for {len(BLOCKS)} blocks")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_wax.png")
    else:
        print(__doc__)
