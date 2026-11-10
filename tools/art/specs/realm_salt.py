"""The Salt family (SPEC 50.8): the Realms of the dry Hive, first Saltcomb Flats. Bone-coloured dry wax and ochre stone with blue-black shade in every cell,
white salt crust, golden dune sand, black paving, brass and ochre cloth.

    python3 tools/art/build_all.py realm_salt        textures
    python3 tools/art/specs/realm_salt.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/realm_salt.py sheet PATH every texture at 6x, named

Drawn by code, nothing copied. Same table as the Fane part of ThemeBlocks.DEFS and DepthBlocks.DEFS. Its stone is Knell's worked stone in other colours.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_depth as D  # noqa: E402
import realm_amber as A  # noqa: E402
import realm_knell as N  # noqa: E402

RAMPS = {
    "astone": ["#6d5431", "#8a6d42", "#a88955", "#c4a569", "#d9bf83", "#ebd6a0"],
    "aworn": ["#5a4226", "#745832", "#8f7140", "#a98a51", "#c1a265", "#d6b97e"],
    "adeep": ["#1b2036", "#252c46", "#303a58", "#3d4a6b", "#4d5c80", "#617196"],
    "apale": ["#a8997a", "#c0b18f", "#d5c8a7", "#e6dbbd", "#f2ead1", "#fbf5e4"],
    "ahex": ["#7a5a28", "#9a7535", "#b89045", "#d1aa5a", "#e4c273", "#f2d892"],
    "aflag": ["#1a1b20", "#25262d", "#31333b", "#40424b", "#51535d", "#666872"],
    "abrick": ["#7a4a20", "#96602b", "#b27838", "#ca9048", "#dea85b", "#edbf72"],
    "aplast": ["#b8a274", "#cdb78a", "#dfcaa0", "#ecdcb6", "#f4e8c9", "#faf1dc"],
    "atile": ["#0f5a5e", "#177478", "#239094", "#3babad", "#5cc4c2", "#86dad6"],
    "atile2": ["#6a3a14", "#864c1b", "#a36023", "#bf752e", "#d68b3c", "#e7a350"],
    "atimb": ["#3a2a18", "#4f3a22", "#65492c", "#7b5a37", "#916c44", "#a67f52"],
    "aplank": ["#5a4630", "#735a3f", "#8c7150", "#a48863", "#bb9f78", "#d0b68e"],
    "agilt": ["#5a3f10", "#7a5816", "#9c741e", "#bf9128", "#deaf3a", "#f4cb5c"],
    "amoss": ["#6a6a2a", "#7f7f33", "#95953f", "#abaa4e", "#c0bf60", "#d3d277"],
    "abed": ["#6b4e24", "#836030", "#9c753d", "#b58a4c", "#cba05e", "#dcb672"],
    "honey": ["#7a4d10", "#a66d16", "#d09023", "#efb23d", "#ffd369", "#fff0a8"],
    "rust": ["#6a5228", "#826433", "#9b7a42", "#b39054", "#c9a668", "#dcbc80"],
    "bark": ["#a8987a", "#c0b193", "#d4c7aa", "#e4d9c0", "#f0e8d4", "#f9f3e6"],
    "agold": ["#7a6a4a", "#8c7c5a", "#9f8f6c", "#b2a27f", "#c4b593", "#d5c8a8"],
    "afall": ["#b0925a", "#c5a76d", "#d6bb82", "#e4cc97", "#efdcae", "#f7e9c6"],
    "aban": ["#6a2f10", "#8a4016", "#aa541e", "#c66b2a", "#da8338", "#ea9c4c"],
    "acrust": ["#b9c2c4", "#cdd4d4", "#dde3e1", "#e9eeeb", "#f3f6f2", "#fdfefb"],
    "acrack": ["#7f8b90", "#939ea2", "#a7b0b3", "#bac1c3", "#cbd1d2", "#dbdfdf"],
    "asand": ["#a67b34", "#bd913f", "#d1a64c", "#e0b95d", "#ecca70", "#f5da87"],
}

BLOCKS = {
    "salt_stone": ("Dry Comb Stone", "pickaxe"),
    "salt_stone_worn": ("Scoured Comb Stone", "pickaxe"),
    "salt_stone_deep": ("Shaded Comb Stone", "pickaxe"),
    "salt_stone_pale": ("Bleached Comb Stone", "pickaxe"),
    "salt_hexstone": ("Hive Paver", "pickaxe"),
    "salt_flagstone": ("Black Causeway Stone", "pickaxe"),
    "salt_brick": ("Sand Brick", "pickaxe"),
    "salt_plaster": ("Wax Plaster", "pickaxe"),
    "salt_plaster_band": ("Patterned Wax Plaster", "pickaxe"),
    "salt_tile": ("Brine Glaze", "pickaxe"),
    "salt_tile_deep": ("Ochre Glaze", "pickaxe"),
    "salt_timber": ("Bleached Post", "axe"),
    "salt_planks": ("Bleached Boards", "axe"),
    "salt_gilt": ("Desert Brass", "pickaxe"),
    "salt_window": ("Wax Lamp Pane", "axe"),
    "salt_moss": ("Dry Lichen", "hoe"),
    "salt_bed": ("Packed Sand", "shovel"),
    "salt_bark": ("Sun-White Bone", "pickaxe"),
    "salt_bark_moss": ("Weathered Bone", "pickaxe"),
    "salt_fall": ("Falling Sand", None),
    "salt_relief": ("Carved Comb Stone", "pickaxe"),
    "salt_banner": ("Ochre Cloth", "hoe"),
    "salt_crust": ("Salt Crust", "pickaxe"),
    "salt_crust_cracked": ("Cracked Salt Crust", "pickaxe"),
    "salt_sand": ("Dune Sand", "shovel"),
    "salt_sand_ripple": ("Rippled Dune Sand", "shovel"),
    "salt_comb": ("Dry Comb", "pickaxe"),
}

CUTS = [("salt_stone", "Dry Comb Stone", "pickaxe"), ("salt_stone_pale", "Bleached Comb Stone", "pickaxe"), ("salt_flagstone", "Black Causeway Stone", "pickaxe"),
        ("salt_brick", "Sand Brick", "pickaxe"), ("salt_plaster", "Wax Plaster", "pickaxe"), ("salt_tile", "Brine Glaze", "pickaxe"),
        ("salt_tile_deep", "Ochre Glaze", "pickaxe"), ("salt_planks", "Bleached Board", "axe")]

B = K.B


def _P():
    return K.px(RAMPS)


def tex_banner(P):
    """Heavy woven cloth, darker at the folds, and rotting in the rain."""
    rp = P.ramp("aban")
    rng = P.rng_for("banner")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            fold = (x * 2) % 7
            v = 2 + (1 if fold < 3 else 0) + (1 if (x + y * 3) % 5 == 0 else 0) - (1 if rng.random() < 0.22 else 0)
            c.set(x, y, rp[max(0, min(5, v))])
    return c


def tex_crust(P, cracked=False):
    """White salt with a sparkle, and on the cracked sort the dark seams it dries along."""
    rp = P.ramp("acrust")
    rng = P.rng_for("crust" + str(cracked))
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, rp[max(2, min(5, 3 + rng.randrange(-1, 3)))])
    if cracked:
        dk = P.ramp("acrack")
        for start in ((0, 4), (0, 11), (5, 0), (11, 0)):
            x, y = start
            for k in range(14):
                c.set(x % 16, y % 16, dk[1 + rng.randrange(0, 2)])
                if start[1] == 0:
                    y += 1
                    x += rng.choice((-1, 0, 1)) if rng.random() < 0.5 else 0
                else:
                    x += 1
                    y += rng.choice((-1, 0, 1)) if rng.random() < 0.5 else 0
    return c


def tex_sand(P, ripple=False):
    """Fine gold sand. The rippled sort carries the marks the wind leaves."""
    rp = P.ramp("asand")
    rng = P.rng_for("sand" + str(ripple))
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 2 + rng.randrange(0, 3)
            if ripple and (y + (x // 5)) % 5 < 2:
                v -= 2
            c.set(x, y, rp[max(0, min(5, v))])
    return c


def tex_bark_moss(P):
    c = K.bark(P, "bark", "root")
    mo = P.ramp("amoss")
    rng = P.rng_for("bark-moss")
    for x in range(16):
        d = 3 + rng.randrange(4)
        for y in range(d):
            c.set(x, y, mo[max(1, min(5, 4 - y + rng.randrange(-1, 2)))])
    return c


def tex_relief(P):
    return N.key_band(P, N.cut_block(P, "apale", "r", lo=2, span=2, chips=2), "adeep")


class Tint:
    """The same painter with another ramp in place of the leaf ramp, so all three leaf blocks share a shape."""
    def __init__(self, P, ramp):
        self.P, self.name = P, ramp

    def __getattr__(self, k):
        return getattr(self.P, k)

    def ramp(self, n):
        return self.P.ramp(self.name if n == "agold" else n)

    def rng_for(self, *a):
        return self.P.rng_for(*a, self.name)


def textures():
    P = _P()
    win, win_g = A.tex_window(P)
    return {
        "salt_stone": N.tex_stone(P), "salt_stone_worn": N.tex_worn(P), "salt_stone_deep": N.tex_deep(P), "salt_stone_pale": N.tex_pale(P),
        "salt_hexstone": A.tex_hexstone(P), "salt_flagstone": N.tex_flag(P), "salt_brick": N.tex_brick(P), "salt_plaster": A.tex_plaster(P),
        "salt_plaster_band": N.tex_band(P), "salt_tile": A.scallops(P, "atile", "t"), "salt_tile_deep": A.scallops(P, "atile2", "d"),
        "salt_timber": K.bark(P, "atimb", "timber"), "salt_timber_end": K.rings(P, "atimb", "atimb", "timber"),
        "salt_planks": K.boards(P, "aplank", "planks"), "salt_gilt": A.tex_gilt(P), "salt_window": win, "salt_window_glow": win_g,
        "salt_moss_top": A.tex_moss(P), "salt_moss": N.stained(P, N.tex_stone(P), "amoss", "m", runs=6), "salt_bed": A.tex_bed(P),
        "salt_bark": K.bark(P, "bark", "root"), "salt_bark_moss": tex_bark_moss(P), "salt_bark_moss_top": A.tex_moss(P), 
        "salt_fall": K.streaks(P, "afall", "fane-fall", frames=8, alpha=165),
        "salt_relief": tex_relief(P), "salt_banner": tex_banner(P), "salt_crust": tex_crust(P), "salt_crust_cracked": tex_crust(P, True), "salt_sand": tex_sand(P), "salt_sand_ripple": tex_sand(P, True), "salt_comb": A.tex_hexstone(P),
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


def models():
    m = {}
    for n in BLOCKS:
        if n not in ("salt_window", "salt_moss", "salt_timber", "salt_bark_moss", "salt_fall"):
            m[n] = K.cube_model(n)
    m["salt_window"] = K.cube_model("salt_window", glow=True)
    m["salt_moss"] = K.cube_model("salt_moss", top="salt_moss_top", bottom="salt_stone")
    m["salt_bark_moss"] = K.cube_model("salt_bark_moss", top="salt_bark_moss_top", bottom="salt_bark")
    m["salt_timber"] = K.column_model("salt_timber", "salt_timber_end")
    m["salt_fall"] = K.fall_model("salt_fall", 6)
    return m


def states():
    s = {}
    for n in BLOCKS:
        if n in ("salt_bed", "salt_bark", "salt_sand", "salt_crust"):
            s[n] = N.turned(n)
        elif n == "salt_timber":
            s[n] = K.axis_state(n)
        else:
            s[n] = K.simple_state(n)
    return s


def content():
    K.content("realm_salt", BLOCKS, models(), states(), {})
    cut, m, s = {}, {}, {}
    for base, en, tool in CUTS:
        cut[base + "_slab"] = (en + " Slab", tool)
        cut[base + "_stairs"] = (en + " Stairs", tool)
        t = B + base
        tex = {"bottom": t, "top": t, "side": t}
        m[base + "_slab"] = {"parent": "minecraft:block/slab", "textures": tex}
        m[base + "_slab_top"] = {"parent": "minecraft:block/slab_top", "textures": tex}
        s[base + "_slab"] = {"variants": {"type=bottom": {"model": B + base + "_slab"}, "type=top": {"model": B + base + "_slab_top"}, "type=double": {"model": t}}}
        m[base + "_stairs"] = {"parent": "minecraft:block/stairs", "textures": tex}
        m[base + "_stairs_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": tex}
        m[base + "_stairs_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": tex}
        s[base + "_stairs"] = D.stairs_state(base + "_stairs")
    K.content("realm_salt_cuts", cut, m, s, {})
    for base, _, _ in CUTS:
        D.slab_loot(base + "_slab")
    for kind, tag in (("_slab", "slabs"), ("_stairs", "stairs")):
        vals = [f"awaken:{b}{kind}" for b, _, _ in CUTS]
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/block/{tag}.json"), {"values": vals})
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/item/{tag}.json"), {"values": vals})


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_salt.png")
    else:
        print(__doc__)
