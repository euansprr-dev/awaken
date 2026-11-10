"""The Knell family (SPEC 50.8): the Realms of the Bell Crypt, first The Hour Crypt. Its painting is dark blue masonry where every block reads
as its own cut stone, rust down the joints, a green bronze bell, candle gold and moonlight.

    python3 tools/art/build_all.py realm_knell        textures
    python3 tools/art/specs/realm_knell.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/realm_knell.py sheet PATH every texture at 6x, named
    python3 tools/art/specs/realm_knell.py java       the carved blocks' boxes, as ThemeBlocks.DEFS wants them

Drawn by code, nothing copied. Same table as the Knell part of ThemeBlocks.DEFS and DepthBlocks.DEFS. The ramps keep Amber's names so its
worked-stone drawing can be reused; the stone itself is drawn here, because the look is different.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_depth as D  # noqa: E402
import realm_amber as A  # noqa: E402

# As the painting has them. Never drawn as they are: see RAMPS below.
PAINTED = {
    "astone": ["#12141b", "#1b1e27", "#262a35", "#323744", "#414755", "#535b6b"],
    # worn stone is the same stone, greyer and a touch warmer (under 10% saturation, the stone's own brightness step for step): a rust-brown
    # block mixed one in four into blue stone reads as a checkerboard. Rust is the brick's alone.
    "aworn": ["#141415", "#1e1e20", "#292a2d", "#36373a", "#46474a", "#595b5f"],
    "adeep": ["#090a0e", "#101218", "#171a22", "#1f232c", "#282d37", "#323843"],
    "apale": ["#3a4050", "#495062", "#5a6275", "#6c7589", "#7f889c", "#949db0"],
    "ahex": ["#1a1c24", "#262933", "#333742", "#424755", "#525867", "#656c7d"],
    "aflag": ["#14171e", "#1d212a", "#282d38", "#353b48", "#444b5a", "#566071"],
    "abrick": ["#26170e", "#362113", "#492d19", "#5f3b20", "#774b29", "#8f5d33"],
    "aplast": ["#6c6759", "#817b6a", "#97907c", "#aca590", "#bfb8a2", "#d2cbb5"],
    "atile": ["#13151c", "#1c1f28", "#262a35", "#313642", "#3d4351", "#4a5161"],
    "atile2": ["#1a3632", "#254c46", "#31625b", "#3f7970", "#4f9085", "#63a79a"],
    "atimb": ["#0e0b08", "#18120d", "#231a13", "#2f231a", "#3c2d21", "#4a382a"],
    "aplank": ["#18120d", "#231a13", "#30241a", "#3e2f22", "#4d3b2b", "#5d4835"],
    "agilt": ["#4a3610", "#695015", "#8e6d1d", "#b48d27", "#d6ae3b", "#efce65"],
    "amoss": ["#1b2926", "#263834", "#334943", "#415b53", "#526e64", "#668277"],
    "abed": ["#0e0d0d", "#171513", "#211e1b", "#2b2723", "#36312b", "#423b34"],
    "honey": ["#2a4782", "#3c63a8", "#5785c9", "#80ace2", "#acceF0", "#d6ebff"],
    "bronze": ["#241609", "#39240f", "#533716", "#6f4c20", "#8e682d", "#ad843f"],
    "patina": ["#13200e", "#1c3014", "#27421c", "#355626", "#476b30", "#5e823c"],
    "iron": ["#0b0c0f", "#131519", "#1c1f25", "#272b32", "#343941", "#454b54"],
    "rust": ["#2a140a", "#41200f", "#5a2e15", "#74401d", "#8f5427", "#a96a34"],
}

# The game shows texture x light, so stone painted as dark as the painting's shadows stays black under any lamp (SPEC 50.8). The
# family is lifted by one exponent on brightness, hue kept, until tools/art/tone_check.py puts its median at 100; MORE lifts the
# structural ramps that were still under 62. Windows and gilt already glow and stay as painted. The dark comes from the light now.
TONE = 0.59
MORE = {"adeep": 0.96, "atimb": 0.96}
LEAVE = ("agilt", "honey")
RAMPS = K.lift(PAINTED, TONE, leave=LEAVE, more=MORE)

BLOCKS = {
    "knell_stone": ("Knell Stone", "pickaxe"),
    "knell_stone_worn": ("Rusted Knell Stone", "pickaxe"),
    "knell_stone_deep": ("Deep Knell Stone", "pickaxe"),
    "knell_stone_pale": ("Dressed Knell Stone", "pickaxe"),
    "knell_hexstone": ("Hour Paver", "pickaxe"),
    "knell_flagstone": ("Wet Flagstone", "pickaxe"),
    "knell_brick": ("Niche Brick", "pickaxe"),
    "knell_plaster": ("Bone Wash", "pickaxe"),
    "knell_plaster_band": ("Painted Bone Wash", "pickaxe"),
    "knell_tile": ("Grave Slate", "pickaxe"),
    "knell_tile_deep": ("Verdigris Slate", "pickaxe"),
    "knell_timber": ("Black Oak Beam", "axe"),
    "knell_planks": ("Black Oak Boards", "axe"),
    "knell_gilt": ("Tarnished Gilt", "pickaxe"),
    "knell_window": ("Moon Window", "axe"),
    "knell_moss": ("Grave Mould", "hoe"),
    "knell_bed": ("Grave Dust", "shovel"),
    "knell_bronze": ("Bell Bronze", "pickaxe"),
    "knell_bronze_dark": ("Dark Bell Bronze", "pickaxe"),
    "knell_patina": ("Bell Patina", "pickaxe"),
    "knell_bronze_key": ("Bell Bronze Key Band", "pickaxe"),
    "knell_iron": ("Black Iron", "pickaxe"),
}

CUTS = [("knell_stone", "Knell Stone", "pickaxe"), ("knell_stone_pale", "Dressed Knell Stone", "pickaxe"), ("knell_flagstone", "Wet Flagstone", "pickaxe"),
        ("knell_brick", "Niche Brick", "pickaxe"), ("knell_plaster", "Bone Wash", "pickaxe"), ("knell_tile", "Grave Slate", "pickaxe"),
        ("knell_tile_deep", "Verdigris Slate", "pickaxe"), ("knell_planks", "Black Oak Board", "axe")]

# Knell's carved blocks (SPEC 50.8): name -> (English, tool, base texture, turn, boxes). Drawn facing north or
# running north to south; the same boxes as the carved Defs in ThemeBlocks.DEFS (`realm_knell.py java` prints them for pasting).
CARVED = {
    # one top corner of an opening; it faces the opening. A quarter curve of radius 16 in 2-px steps, so two facing each other arch a gap
    "knell_arch": ("Knell Arch Stone", "pickaxe", "knell_stone_pale", "facing",
                   [(0, 6, 14, 16, 16, 16), (0, 10, 12, 16, 16, 14), (0, 12, 10, 16, 16, 12), (0, 14, 6, 16, 16, 10)]),
    # a pier's face: three grooves 2 px wide and 2 px deep down the face it turns to the room
    "knell_flute": ("Fluted Knell Stone", "pickaxe", "knell_stone_pale", "facing",
                    [(0, 0, 2, 16, 16, 16), (0, 0, 0, 2, 16, 2), (4, 0, 0, 7, 16, 2), (9, 0, 0, 12, 16, 2), (14, 0, 0, 16, 16, 2)]),
    # a vault rib hung from the top face: 6 px wide and 6 px deep, its lower corners stepped in 2 px
    "knell_rib": ("Knell Vault Rib", "pickaxe", "knell_stone_pale", "axis", [(5, 12, 0, 11, 16, 16), (7, 10, 0, 9, 12, 16)]),
    # a bracket under a ledge, 10 px wide: three steps out from the wall behind it, each 4 px deeper and 4 px higher
    "knell_corbel": ("Knell Corbel", "pickaxe", "knell_stone", "facing", [(3, 12, 4, 13, 16, 16), (3, 8, 8, 13, 12, 16), (3, 4, 12, 13, 8, 16)]),
    # one link of a great chain, hung from the top face: a ring 12 px tall, 8 px wide, 4 px thick, its bar 2 px
    "knell_link": ("Great Chain Link", "pickaxe", "knell_iron", "axis",
                   [(6, 14, 4, 10, 16, 12), (6, 4, 4, 10, 6, 12), (6, 6, 4, 10, 14, 6), (6, 6, 10, 10, 14, 12)]),
}

B = K.B


def _P():
    return K.px(RAMPS)


def cut_block(P, ramp, seed, lo=1, span=3, chips=5):
    """One dressed stone to a block: a lit upper-left edge, a shadowed lower-right one, soft grain between. A wall of these reads as stacked
    cubes, the look of the painting, where a joint drawn across the middle of the block would read as tiling."""
    rp = P.ramp(ramp)
    rng = P.rng_for("cut", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=3, octaves=3)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = lo + int(n[y][x] * span + (rng.random() - 0.5) * 0.7)
            if x == 15 or y == 15:
                k = 0
            elif x == 0 or y == 0:
                k = min(5, v + 1)
            elif x == 14 or y == 14:
                k = max(0, v - 1)
            else:
                k = max(0, min(5, v))
            c.set(x, y, rp[k])
    for _ in range(chips):
        x, y = rng.randrange(1, 14), rng.randrange(1, 14)
        c.set(x, y, rp[0])
        c.blend(x + 1, y, rp[0], 0.5)
        c.blend(x, y - 1, rp[min(5, lo + span)], 0.55)
    return c


def stained(P, base, ramp, seed, runs=4):
    """Rust running down from the top edge in thin streaks, the way the painting's walls weep."""
    rp = P.ramp(ramp)
    rng = P.rng_for("stain", ramp, seed)
    for _ in range(runs):
        x = rng.randrange(1, 15)
        length = rng.randrange(5, 14)
        for y in range(1, length):
            base.blend(x, y, rp[max(1, 4 - y // 4)], max(0.2, 0.75 - y * 0.05))
            if rng.random() < 0.25:
                x = max(1, min(14, x + rng.choice((-1, 1))))
    return base


def tex_worn(P):
    """Worn stone weeps dark, not rust: brown streaks down one block in five were most of what made the walls a checkerboard."""
    return stained(P, cut_block(P, "aworn", "w", lo=1, span=4, chips=8), "adeep", "w", runs=5)


def tex_stone(P):
    return stained(P, cut_block(P, "astone", "s", lo=1, span=3), "rust", "s", runs=1)


def tex_deep(P):
    return cut_block(P, "adeep", "d", lo=1, span=3, chips=3)


def tex_pale(P):
    return cut_block(P, "apale", "p", lo=1, span=3, chips=4)


def tex_brick(P):
    """Small rust-dark bricks with deep joints; the backs of the niches are what the candles light."""
    rp, joint = P.ramp("abrick"), P.ramp("adeep")
    rng = P.rng_for("brick")
    tone = {}
    c = P.Canvas(16, 16)
    for y in range(16):
        row = y // 4
        off = 0 if row % 2 == 0 else 4
        for x in range(16):
            i = (x + off) % 8
            b = ((x + off) // 8 % 2, row)
            if b not in tone:
                tone[b] = rng.randrange(1, 5)
            if y % 4 == 3 or i == 7:
                c.set(x, y, joint[1])
            elif y % 4 == 0:
                c.set(x, y, rp[min(5, tone[b] + 1)])
            else:
                c.set(x, y, rp[max(0, tone[b] - (1 if rng.random() < 0.2 else 0))])
    return c


def tex_flag(P):
    """Laid floor, wet: the low places hold the moonlight, a few pale glints among dark joints."""
    c = A.tex_flagstone(P)
    rp = P.ramp("apale")
    rng = P.rng_for("wet")
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        c.blend(x, y, rp[4], 0.55)
        c.blend((x + 1) % 16, y, rp[3], 0.35)
    return c


def tex_band(P):
    """A key pattern in gilt on dark stone: the frieze from the Lord's bell, carried round the crypt."""
    c = cut_block(P, "adeep", "b", lo=1, span=2, chips=1)
    return key_band(P, c, "agilt")


def key_band(P, c, ramp):
    g = P.ramp(ramp)
    for x in range(16):
        c.set(x, 3, g[2])
        c.set(x, 12, g[2])
    # one meander to the block, so the frieze joins up all the way round
    for x, y in [(1, 10), (1, 9), (1, 8), (1, 7), (1, 6), (2, 6), (3, 6), (4, 6), (5, 6), (5, 7), (5, 8), (4, 8), (3, 8),
                 (9, 10), (9, 9), (9, 8), (9, 7), (9, 6), (10, 6), (11, 6), (12, 6), (13, 6), (13, 7), (13, 8), (12, 8), (11, 8)]:
        c.set(x, y, g[3 + (x + y) % 2])
    for x in range(16):
        c.set(x, 10, g[1] if x not in (1, 9) else g[3])
    return c


def bronze(P, seed, green=0.0, dark=False):
    """The bell's metal: dull cast bronze with hammered dints, and verdigris in soft patches where the weather got to it."""
    br, gr = P.ramp("bronze"), P.ramp("patina")
    rng = P.rng_for("bronze", seed)
    n = P.value_noise(16, 16, rng, scale=3, octaves=3)
    m = P.value_noise(16, 16, rng, scale=2, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = n[y][x] * 3.2 + (0.4 if not dark else -0.5) + (rng.random() - 0.5) * 0.5
            if m[y][x] < green:
                c.set(x, y, gr[max(0, min(5, int(v + 1.0)))])
            else:
                c.set(x, y, br[max(0, min(5, int(v + (0.3 if not dark else 0))))])
    return c


def tex_iron(P):
    rp = P.ramp("iron")
    c = K.noisy(P, "iron", "iron", scale=2, octaves=3, lo=1, span=3, jitter=0.1)
    rng = P.rng_for("iron-pits")
    ru = P.ramp("rust")
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        c.blend(x, y, ru[3], 0.7)
    for k in range(16):
        c.set(k, 0, rp[0])
        c.set(0, k, rp[0])
    return c


def textures():
    P = _P()
    win, win_g = A.tex_window(P)
    key = key_band(P, bronze(P, "key", green=0.34), "agilt")
    return {
        "knell_stone": tex_stone(P), "knell_stone_worn": tex_worn(P), "knell_stone_deep": tex_deep(P), "knell_stone_pale": tex_pale(P),
        "knell_hexstone": A.tex_hexstone(P), "knell_flagstone": tex_flag(P), "knell_brick": tex_brick(P), "knell_plaster": A.tex_plaster(P),
        "knell_plaster_band": tex_band(P), "knell_tile": A.scallops(P, "atile", "t"), "knell_tile_deep": A.scallops(P, "atile2", "d"),
        "knell_timber": K.bark(P, "atimb", "timber"), "knell_timber_end": K.rings(P, "atimb", "atimb", "timber"),
        "knell_planks": K.boards(P, "aplank", "planks"), "knell_gilt": A.tex_gilt(P), "knell_window": win, "knell_window_glow": win_g,
        "knell_moss_top": A.tex_moss(P), "knell_moss": stained(P, tex_stone(P), "amoss", "m", runs=6), "knell_bed": A.tex_bed(P),
        "knell_bronze": bronze(P, "b", green=0.16), "knell_bronze_dark": bronze(P, "d", green=0.4, dark=True), "knell_patina": bronze(P, "g", green=0.72),
        "knell_bronze_key": key, "knell_iron": tex_iron(P),
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c)


def turned(name):
    m = B + name
    return {"variants": {"": [{"model": m}, {"model": m, "y": 90}, {"model": m, "y": 180}, {"model": m, "y": 270}]}}


def models():
    m = {}
    for n in BLOCKS:
        if n not in ("knell_window", "knell_moss", "knell_timber"):
            m[n] = K.cube_model(n)
    m["knell_window"] = K.cube_model("knell_window", glow=True)
    m["knell_moss"] = K.cube_model("knell_moss", top="knell_moss_top", bottom="knell_stone")
    m["knell_timber"] = K.column_model("knell_timber", "knell_timber_end")
    return m


def states():
    s = {}
    for n in BLOCKS:
        if n in ("knell_bed", "knell_bronze", "knell_bronze_dark", "knell_patina"):
            s[n] = turned(n)
        elif n == "knell_timber":
            s[n] = K.axis_state(n)
        else:
            s[n] = K.simple_state(n)
    return s


def content():
    K.content("realm_knell", BLOCKS, models(), states(), {})
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
    K.content("realm_knell_cuts", cut, m, s, {})
    for base, _, _ in CUTS:
        D.slab_loot(base + "_slab")
    for kind, tag in (("_slab", "slabs"), ("_stairs", "stairs")):
        vals = [f"awaken:{b}{kind}" for b, _, _ in CUTS]
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/block/{tag}.json"), {"values": vals})
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/item/{tag}.json"), {"values": vals})
    K.carved("realm_knell_shapes", CARVED, folder="gates_knell_shapes")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "java":
        K.carved_java(CARVED)
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_knell.png")
    else:
        print(__doc__)
