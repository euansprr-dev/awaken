"""The Keep family (SPEC 50.8): the Realms of the Overgrown Temple's strongholds, first Kingsroot Keep. Cold wet slate ashlar, verdigris tile, pale roots
as thick as a house, rust-red cloth, and warm firelight on brick.

    python3 tools/art/build_all.py realm_keep        textures
    python3 tools/art/specs/realm_keep.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/realm_keep.py sheet PATH every texture at 6x, named

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
    "astone": ["#23292e", "#32393f", "#434b51", "#576066", "#6d767b", "#868e92"],
    "aworn": ["#17231b", "#223225", "#2f4430", "#3f573c", "#526c4a", "#6a845c"],
    "adeep": ["#0c0f13", "#13181c", "#1c2227", "#262e33", "#323a40", "#40484e"],
    "apale": ["#4a4e50", "#5f6365", "#767a7a", "#8f9290", "#a8aaa5", "#c2c2ba"],
    "ahex": ["#1a2024", "#262e33", "#343d43", "#444e54", "#566167", "#6b767b"],
    "aflag": ["#12171c", "#1b2228", "#262e35", "#333c43", "#424c53", "#555f66"],
    "abrick": ["#2a1610", "#3f2116", "#572e1e", "#713d28", "#8c4f34", "#a66645"],
    "aplast": ["#3f4344", "#535758", "#696d6d", "#818482", "#999b97", "#b2b3ad"],
    "atile": ["#0f2d2a", "#17403a", "#20564c", "#2d6d5f", "#3f8673", "#589f8a"],
    "atile2": ["#0a211f", "#11312d", "#19433d", "#23564d", "#316a5e", "#438070"],
    "atimb": ["#120e0a", "#1d1610", "#2a2017", "#382b1f", "#473729", "#574535"],
    "aplank": ["#17120d", "#231b14", "#31261c", "#413326", "#524131", "#65503d"],
    "agilt": ["#2e2412", "#46361a", "#624c23", "#82652e", "#a5823c", "#c8a350"],
    "amoss": ["#132416", "#1d331f", "#29462a", "#375a35", "#477042", "#5b8752"],
    "abed": ["#0f0d0a", "#181510", "#231e17", "#2f281f", "#3c3328", "#4a4032"],
    "honey": ["#7a3d10", "#a65616", "#d07423", "#ef963d", "#ffba69", "#ffe0a8"],
    "rust": ["#1c2a1c", "#283a26", "#354d31", "#44603c", "#567549", "#6b8b58"],
    "bark": ["#4a483f", "#646155", "#807c6c", "#9c9884", "#b6b19c", "#d0cbb6"],
    "agold": ["#0d2519", "#143423", "#1c452d", "#265738", "#326a44", "#417e52"],
    "aleaflit": ["#1e4a30", "#285e3a", "#347346", "#428854", "#559c65", "#6db17a"],
    "aleafdeep": ["#050f0b", "#0a1a12", "#10261a", "#173322", "#1f412b", "#285034"],
    "afall": ["#6d8894", "#8aa4ae", "#a6bdc6", "#c2d4da", "#dce8ec", "#f4fafc"],
    "aban": ["#250a08", "#3f120d", "#601b12", "#842818", "#a33a20", "#bf552d"],
}

BLOCKS = {
    "keep_stone": ("Keep Ashlar", "pickaxe"),
    "keep_stone_worn": ("Mossed Ashlar", "pickaxe"),
    "keep_stone_deep": ("Rain-Black Ashlar", "pickaxe"),
    "keep_stone_pale": ("Dressed Ashlar", "pickaxe"),
    "keep_hexstone": ("Bailey Setts", "pickaxe"),
    "keep_flagstone": ("Wet Flagstone", "pickaxe"),
    "keep_brick": ("Hearth Brick", "pickaxe"),
    "keep_plaster": ("Grey Limewash", "pickaxe"),
    "keep_plaster_band": ("Painted Limewash", "pickaxe"),
    "keep_tile": ("Verdigris Tile", "pickaxe"),
    "keep_tile_deep": ("Black Verdigris Tile", "pickaxe"),
    "keep_timber": ("Black Oak Beam", "axe"),
    "keep_planks": ("Black Oak Boards", "axe"),
    "keep_gilt": ("Old Bronze", "pickaxe"),
    "keep_window": ("Arrow-Slit Glow", "axe"),
    "keep_moss": ("Keep Moss", "hoe"),
    "keep_bed": ("Rain Mud", "shovel"),
    "keep_bark": ("Pale Root", "axe"),
    "keep_bark_moss": ("Mossed Pale Root", "axe"),
    "keep_leaf": ("Keep Leaves", "hoe"),
    "keep_leaf_lit": ("High Keep Leaves", "hoe"),
    "keep_leaf_deep": ("Dark Keep Leaves", "hoe"),
    "keep_fall": ("Keep Falls", None),
    "keep_thorn": ("Root Grille", "axe"),
    "keep_relief": ("Carved Keystone", "pickaxe"),
    "keep_banner": ("Rust Banner Cloth", "hoe"),
    "keep_vines": ("Keep Vines", "hoe"),
    "keep_climb_root": ("Climbing Root", "axe"),
}

CUTS = [("keep_stone", "Keep Ashlar", "pickaxe"), ("keep_stone_pale", "Dressed Ashlar", "pickaxe"), ("keep_flagstone", "Wet Flagstone", "pickaxe"),
        ("keep_brick", "Hearth Brick", "pickaxe"), ("keep_plaster", "Grey Limewash", "pickaxe"), ("keep_tile", "Verdigris Tile", "pickaxe"),
        ("keep_tile_deep", "Black Verdigris Tile", "pickaxe"), ("keep_planks", "Black Oak Board", "axe")]

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


def tex_vines(P):
    """A thin hanging strand with dark cold leaves on it, nothing that glows."""
    lf = P.ramp("aleaflit")
    st = P.ramp("bark")
    rng = P.rng_for("keep-vines")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, (0, 0, 0, 0))
    for sx in (3, 8, 12):
        x = sx
        for y in range(16):
            c.set(x % 16, y, st[1 + (y % 2)])
            if rng.random() < 0.38:
                x += rng.choice((-1, 1))
            if rng.random() < 0.55:
                c.set((x + rng.choice((-1, 1))) % 16, y, lf[rng.randrange(1, 5)])
    return c


def tex_climb(P):
    """A thick net of pale roots with gaps you can get a hand in: the one texture in this land that says "up"."""
    rp = P.ramp("bark")
    lf = P.ramp("aleaflit")
    rng = P.rng_for("climb")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, (0, 0, 0, 0))
    for sx in (1, 6, 11):
        x = sx
        for y in range(16):
            for k in range(3):
                c.set((x + k) % 16, y, rp[[2, 4, 3][k] + (y % 3 == 0)])
            if rng.random() < 0.3:
                x += rng.choice((-1, 1))
    for y in (2, 7, 12):
        for x in range(16):
            c.set(x, y, rp[1 + (x % 3)])
            if rng.random() < 0.22:
                c.set(x, y - 1, lf[rng.randrange(1, 5)])
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
        "keep_stone": N.tex_stone(P), "keep_stone_worn": N.tex_worn(P), "keep_stone_deep": N.tex_deep(P), "keep_stone_pale": N.tex_pale(P),
        "keep_hexstone": A.tex_hexstone(P), "keep_flagstone": N.tex_flag(P), "keep_brick": N.tex_brick(P), "keep_plaster": A.tex_plaster(P),
        "keep_plaster_band": N.tex_band(P), "keep_tile": A.scallops(P, "atile", "t"), "keep_tile_deep": A.scallops(P, "atile2", "d"),
        "keep_timber": K.bark(P, "atimb", "timber"), "keep_timber_end": K.rings(P, "atimb", "atimb", "timber"),
        "keep_planks": K.boards(P, "aplank", "planks"), "keep_gilt": A.tex_gilt(P), "keep_window": win, "keep_window_glow": win_g,
        "keep_moss_top": A.tex_moss(P), "keep_moss": N.stained(P, N.tex_stone(P), "amoss", "m", runs=6), "keep_bed": A.tex_bed(P),
        "keep_bark": K.bark(P, "bark", "root"), "keep_bark_moss": tex_bark_moss(P), "keep_bark_moss_top": A.tex_moss(P), "keep_leaf": A.tex_canopy(P), "keep_leaf_lit": A.tex_canopy(Tint(P, "aleaflit")), "keep_leaf_deep": A.tex_canopy(Tint(P, "aleafdeep")),
        "keep_fall": K.streaks(P, "afall", "fane-fall", frames=8, alpha=165),
        "keep_thorn": tex_thorn(P), "keep_relief": tex_relief(P), "keep_banner": tex_banner(P), "keep_vines": tex_vines(P), "keep_climb_root": tex_climb(P),
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


def models():
    m = {}
    for n in BLOCKS:
        if n not in ("keep_window", "keep_moss", "keep_timber", "keep_bark_moss", "keep_leaf", "keep_leaf_lit", "keep_leaf_deep", "keep_thorn", "keep_fall", "keep_vines", "keep_climb_root"):
            m[n] = K.cube_model(n)
    m["keep_window"] = K.cube_model("keep_window", glow=True)
    m["keep_moss"] = K.cube_model("keep_moss", top="keep_moss_top", bottom="keep_stone")
    m["keep_bark_moss"] = K.cube_model("keep_bark_moss", top="keep_bark_moss_top", bottom="keep_bark")
    m["keep_timber"] = K.column_model("keep_timber", "keep_timber_end")
    m["keep_fall"] = K.fall_model("keep_fall", 6)
    m["keep_vines"] = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": B + "keep_vines"}}
    for n in ("keep_leaf", "keep_leaf_lit", "keep_leaf_deep", "keep_thorn", "keep_climb_root"):
        m[n] = {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped", "textures": {"all": B + n}}
    return m


def states():
    s = {}
    for n in BLOCKS:
        if n in ("keep_bed", "keep_bark", "keep_leaf", "keep_leaf_lit", "keep_leaf_deep"):
            s[n] = N.turned(n)
        elif n == "keep_timber":
            s[n] = K.axis_state(n)
        else:
            s[n] = K.simple_state(n)
    return s


def content():
    K.content("realm_keep", BLOCKS, models(), states(), {})
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
    K.content("realm_keep_cuts", cut, m, s, {})
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
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_keep.png")
    else:
        print(__doc__)
