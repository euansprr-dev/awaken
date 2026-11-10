"""The Fane family (SPEC 50.8): the Realms of the Overgrown Temple, first The Vine Seat. Pale sunlit limestone where every block reads as a cut stone,
moss running down the joints, green paving, root bark as thick as a house, and amber lantern light.

    python3 tools/art/build_all.py realm_fane        textures
    python3 tools/art/specs/realm_fane.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/realm_fane.py sheet PATH every texture at 6x, named

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
    "astone": ["#55593f", "#6d7253", "#898e6b", "#a6aa84", "#c1c39c", "#dcdab5"],
    "aworn": ["#2c3c20", "#3e5329", "#536d37", "#6b8848", "#87a45b", "#a5bf72"],
    "adeep": ["#14180f", "#1e2416", "#2a321e", "#374128", "#455133", "#55623f"],
    "apale": ["#7d7f5c", "#979972", "#b2b38a", "#cbcba4", "#e0dfbc", "#f2efd2"],
    "ahex": ["#2c3f22", "#3d5630", "#516f3f", "#688a50", "#82a563", "#9fbf79"],
    "aflag": ["#1f3a2c", "#2b4e3b", "#3a644b", "#4c7b5d", "#619372", "#7bac88"],
    "abrick": ["#3f4120", "#585a2b", "#747737", "#919346", "#adae58", "#c6c66d"],
    "aplast": ["#6f6a4a", "#88835e", "#a29c74", "#bbb58c", "#d2cca3", "#e6e0ba"],
    "atile": ["#21432a", "#2d5a34", "#3b7140", "#4d894e", "#62a260", "#7cba76"],
    "atile2": ["#1c3a2c", "#285039", "#366848", "#478159", "#5b9a6d", "#73b384"],
    "atimb": ["#1b140c", "#2a1f13", "#3a2b1b", "#4c3924", "#5f482e", "#745839"],
    "aplank": ["#241a10", "#33261a", "#443323", "#57432e", "#6b553a", "#806847"],
    "agilt": ["#5a3f0a", "#7c590f", "#a27716", "#c99820", "#ebba35", "#ffdd63"],
    "amoss": ["#1f3a12", "#2e501a", "#3f6823", "#53822e", "#6a9d3a", "#86b94a"],
    "abed": ["#17130c", "#231c12", "#302719", "#3e3321", "#4d402a", "#5d4e34"],
    "honey": ["#7a4d10", "#a66d16", "#d09023", "#efb23d", "#ffd369", "#fff0a8"],
    "rust": ["#27381a", "#354b22", "#45602c", "#587737", "#6d8f44", "#84a853"],
    "bark": ["#3d2d17", "#5a4424", "#7e6536", "#a2864c", "#c0a566", "#dcc587"],
    "agold": ["#143a1c", "#1e4e23", "#2b652b", "#3b7d33", "#4f963d", "#68ae49"],
    "aleaflit": ["#3a7527", "#4f8f2f", "#6aa83a", "#88c048", "#a9d55e", "#cde97d"],
    "aleafdeep": ["#0b2417", "#10321c", "#174224", "#1f522c", "#2a6335", "#37763f"],
    "afall": ["#7fa79c", "#9fc2b6", "#bcd8cc", "#d7ebe1", "#ecf6f0", "#ffffff"],
}

BLOCKS = {
    "fane_stone": ("Fane Stone", "pickaxe"),
    "fane_stone_worn": ("Mossed Fane Stone", "pickaxe"),
    "fane_stone_deep": ("Wet Fane Stone", "pickaxe"),
    "fane_stone_pale": ("Sunlit Fane Stone", "pickaxe"),
    "fane_hexstone": ("Ring Paver", "pickaxe"),
    "fane_flagstone": ("Green Flagstone", "pickaxe"),
    "fane_brick": ("Olive Brick", "pickaxe"),
    "fane_plaster": ("Limewash", "pickaxe"),
    "fane_plaster_band": ("Carved Limewash", "pickaxe"),
    "fane_tile": ("Temple Tile", "pickaxe"),
    "fane_tile_deep": ("Rain Tile", "pickaxe"),
    "fane_timber": ("Old Teak Beam", "axe"),
    "fane_planks": ("Old Teak Boards", "axe"),
    "fane_gilt": ("Temple Amber", "pickaxe"),
    "fane_window": ("Lantern Horn", "axe"),
    "fane_moss": ("Temple Moss", "hoe"),
    "fane_bed": ("Forest Loam", "shovel"),
    "fane_bark": ("Great Root Bark", "axe"),
    "fane_bark_moss": ("Mossed Root Bark", "axe"),
    "fane_leaf": ("Temple Tree Leaves", "hoe"),
    "fane_leaf_lit": ("Sunlit Temple Leaves", "hoe"),
    "fane_leaf_deep": ("Shaded Temple Leaves", "hoe"),
    "fane_fall": ("Fane Falls", None),
    "fane_thorn": ("Root Curtain", "axe"),
    "fane_relief": ("Carved Fane Stone", "pickaxe"),
}

CUTS = [("fane_stone", "Fane Stone", "pickaxe"), ("fane_stone_pale", "Sunlit Fane Stone", "pickaxe"), ("fane_flagstone", "Green Flagstone", "pickaxe"),
        ("fane_brick", "Olive Brick", "pickaxe"), ("fane_plaster", "Limewash", "pickaxe"), ("fane_tile", "Temple Tile", "pickaxe"),
        ("fane_tile_deep", "Rain Tile", "pickaxe"), ("fane_planks", "Old Teak Board", "axe")]

B = K.B


def _P():
    return K.px(RAMPS)


def tex_thorn(P):
    """A lattice of thin roots with the dark showing through: you can see there's a way behind it, and that it's shut."""
    rp = P.ramp("bark")
    rng = P.rng_for("thorn")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, (0, 0, 0, 0))
    for strand in range(7):
        x, y = rng.randrange(16), 0
        step = rng.choice((-1, 1))
        for k in range(32):
            c.set(x % 16, y % 16, rp[2 + (k % 3)])
            c.set((x + 1) % 16, y % 16, rp[1])
            y += 1
            if rng.random() < 0.55:
                x += step
            if rng.random() < 0.1:
                step = -step
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
        "fane_stone": N.tex_stone(P), "fane_stone_worn": N.tex_worn(P), "fane_stone_deep": N.tex_deep(P), "fane_stone_pale": N.tex_pale(P),
        "fane_hexstone": A.tex_hexstone(P), "fane_flagstone": N.tex_flag(P), "fane_brick": N.tex_brick(P), "fane_plaster": A.tex_plaster(P),
        "fane_plaster_band": N.tex_band(P), "fane_tile": A.scallops(P, "atile", "t"), "fane_tile_deep": A.scallops(P, "atile2", "d"),
        "fane_timber": K.bark(P, "atimb", "timber"), "fane_timber_end": K.rings(P, "atimb", "atimb", "timber"),
        "fane_planks": K.boards(P, "aplank", "planks"), "fane_gilt": A.tex_gilt(P), "fane_window": win, "fane_window_glow": win_g,
        "fane_moss_top": A.tex_moss(P), "fane_moss": N.stained(P, N.tex_stone(P), "amoss", "m", runs=6), "fane_bed": A.tex_bed(P),
        "fane_bark": K.bark(P, "bark", "root"), "fane_bark_moss": tex_bark_moss(P), "fane_bark_moss_top": A.tex_moss(P), "fane_leaf": A.tex_canopy(P), "fane_leaf_lit": A.tex_canopy(Tint(P, "aleaflit")), "fane_leaf_deep": A.tex_canopy(Tint(P, "aleafdeep")),
        "fane_fall": K.streaks(P, "afall", "fane-fall", frames=8, alpha=165),
        "fane_thorn": tex_thorn(P), "fane_relief": tex_relief(P),
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


def models():
    m = {}
    for n in BLOCKS:
        if n not in ("fane_window", "fane_moss", "fane_timber", "fane_bark_moss", "fane_leaf", "fane_leaf_lit", "fane_leaf_deep", "fane_thorn", "fane_fall"):
            m[n] = K.cube_model(n)
    m["fane_window"] = K.cube_model("fane_window", glow=True)
    m["fane_moss"] = K.cube_model("fane_moss", top="fane_moss_top", bottom="fane_stone")
    m["fane_bark_moss"] = K.cube_model("fane_bark_moss", top="fane_bark_moss_top", bottom="fane_bark")
    m["fane_timber"] = K.column_model("fane_timber", "fane_timber_end")
    m["fane_fall"] = K.fall_model("fane_fall", 6)
    for n in ("fane_leaf", "fane_leaf_lit", "fane_leaf_deep", "fane_thorn"):
        m[n] = {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped", "textures": {"all": B + n}}
    return m


def states():
    s = {}
    for n in BLOCKS:
        if n in ("fane_bed", "fane_bark", "fane_leaf", "fane_leaf_lit", "fane_leaf_deep"):
            s[n] = N.turned(n)
        elif n == "fane_timber":
            s[n] = K.axis_state(n)
        else:
            s[n] = K.simple_state(n)
    return s


def content():
    K.content("realm_fane", BLOCKS, models(), states(), {})
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
    K.content("realm_fane_cuts", cut, m, s, {})
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
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_fane.png")
    else:
        print(__doc__)
