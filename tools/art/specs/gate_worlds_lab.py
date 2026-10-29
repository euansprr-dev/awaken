"""Gate Worlds, Precursor Lab (SPEC §38.12): the theme's 13 world blocks and its set piece, the Humming Cube.

    python3 tools/art/build_all.py gate_worlds_lab        textures
    python3 tools/art/specs/gate_worlds_lab.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_lab.py sheet PATH every texture at 6x, named

White wall panels and panels with a strip of light, grey floor plates, black frame, cyan glass, server racks with their
lights, green tanks glowing with something asleep in them, hanging cables and work lights, holograms of sprouts, a green
spill, the Simulation's sketch (lines on dark blue until you look; then filled in), and the console you log in at. Drawn
by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "panel": ["#8a949c", "#a2acb4", "#b8c2c8", "#ccd4d8", "#dde3e6", "#eceff0"],
    "plate": ["#2a2e34", "#363b42", "#434951", "#515862", "#606873"],
    "frame": ["#0a0c10", "#14171c", "#1e2229", "#2a2f38", "#38404a"],
    "cyan": ["#0a3a48", "#106070", "#1a8c9c", "#2ab8c8", "#5ae0ec", "#b0fcff"],
    "tank": ["#0a2a14", "#12401e", "#1c5a2a", "#2a7a3a", "#3e9e4e", "#6ad070", "#b0ffb0"],
    "navy": ["#040a16", "#08122a", "#0c1a38", "#122448"],
    "drawn": ["#9aa4ae", "#aeb8c0", "#c0c8ce", "#d0d6da", "#dee2e4"],
}


def _P():
    return K.px(RAMPS)


def tex_panel(P, lit):
    pp = P.ramp("panel")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 3 + (1 if x < 8 else 0) - (1 if y > 13 else 0)
            c.set(x, y, pp[v])
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, pp[1])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, pp[0])
    if lit:
        for y in (6, 7, 8, 9):
            for x in range(1, 15):
                c.set(x, y, K.rgba("#e8fcff"))
                g.set(x, y, K.rgba("#f0ffff" if y in (7, 8) else "#a8f0ff"))
        return c, g
    return c


def tex_floor(P):
    pp = P.ramp("plate")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            # anti-slip diamonds pressed in the plate
            d = (x + y) % 4 == 0 and (x - y) % 4 == 0
            c.set(x, y, pp[3] if d else pp[1] if x in (0, 15) or y in (0, 15) else pp[2])
    for x in range(2, 14):
        c.set(x, 14, K.rgba("#c8a020"))
    return c


def tex_frame(P):
    fp = P.ramp("frame")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, fp[3] if x in (1, 14) else fp[1] if x in (0, 15) else fp[2])
            if y % 8 == 3 and x in (4, 11):
                c.set(x, y, fp[4])
    return c


def tex_frame_end(P):
    fp = P.ramp("frame")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, fp[1] if 3 < x < 12 and 3 < y < 12 else fp[3])
    return c


def tex_glass(P):
    cp = P.ramp("cyan")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c.set(x, y, P.ramp("frame")[2] if edge else (*cp[4][:3], 60))
    for i in range(2, 7):
        c.set(i + 4, 13 - i, (*cp[5][:3], 140))
    return c


def tex_rack(P):
    """A server rack's front: slots of black, a row of lights blinking in each."""
    fp = P.ramp("frame")
    rng = P.rng_for("lab-rack")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, fp[0] if y % 4 == 3 else fp[2] if x in (0, 15) else fp[1])
    for row in range(4):
        y = row * 4 + 1
        for x in range(2, 14, 2):
            if rng.random() < 0.55:
                col = ("#40ff80", "#40e0ff", "#ffb040")[rng.randrange(3)]
                c.set(x, y, K.rgba(col))
                g.set(x, y, K.rgba(col))
    return c, g


def tex_tank(P):
    """A tank of green: liquid glowing from inside, bubbles rising, a dark shape asleep in the middle."""
    tp = P.ramp("tank")
    rng = P.rng_for("lab-tank")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = 3 + int(1.6 * math.sin((x + 0.5) / 16 * math.pi))
            body = math.hypot((x - 7.5) / 3, (y - 8) / 5.5) < 1
            if x in (0, 15):
                c.set(x, y, P.ramp("frame")[2])
            elif body:
                c.set(x, y, (*tp[1][:3], 235))
            else:
                c.set(x, y, (*tp[k][:3], 190))
                g.set(x, y, (*tp[k + 1][:3], 160))
    for _ in range(9):
        x, y = rng.randrange(2, 14), rng.randrange(16)
        c.set(x, y, (*tp[6][:3], 220))
        g.set(x, y, tp[6])
    return c, g


def tex_cables(P):
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    rng = P.rng_for("lab-cables")
    for x0 in (2, 5, 9, 13):
        sway = rng.random() * 2
        for y in range(16):
            x = int(round(x0 + math.sin(y * 0.3 + sway) * 0.8))
            c.set(x, y, P.ramp("frame")[2 + (y % 3 == 0)])
        if rng.random() < 0.6:
            yy = rng.randrange(4, 14)
            c.set(x0, yy, K.rgba("#ff4040"))
            g.set(x0, yy, K.rgba("#ff6060"))
    return c, g


def tex_light(P):
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, P.ramp("frame")[2] if y < 12 else K.rgba("#f0fcff"))
            if y >= 12:
                g.set(x, y, K.rgba("#ffffff"))
    return c, g


def tex_hologram(P):
    rows = ["................",
            "......CCC.......",
            ".....C...C......",
            ".....C.L.C..CC..",
            "......CLC..C..C.",
            ".......L..C.L.C.",
            ".......L...CLC..",
            ".......L....L...",
            ".......L...L....",
            ".......LLLL.....",
            ".......L........",
            ".......L........",
            "..CCCCCCCCCCCC..",
            "..C..........C..",
            "..CCCCCCCCCCCC..",
            "................"]
    c = K.sprite(rows, {"C": "#5ae0ec", "L": "#b0fcff"})
    return c, c


def tex_moss(P):
    tp = P.ramp("tank")
    rng = P.rng_for("lab-moss")
    n = P.value_noise(16, 16, rng, scale=4, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if n[y][x] > 0.38:
                c.set(x, y, tp[2 + int(n[y][x] * 3)])
    return c


def tex_sketch(P):
    """The Simulation: cyan lines on dark blue (a blueprint of a block), and the same block once it's been drawn."""
    np, cp = P.ramp("navy"), P.ramp("cyan")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, np[1 + (x + y) % 2])
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, cp[4])
            elif x == y or x + y == 15:
                c.set(x, y, cp[2])
            elif x % 4 == 0 or y % 4 == 0:
                c.set(x, y, np[3])
    drawn = K.noisy(P, "drawn", "lab-drawn", scale=4, lo=1, span=3, jitter=0.15)
    for x in range(16):
        drawn.set(x, 15, P.ramp("drawn")[0])
        drawn.set(15, x, P.ramp("drawn")[0])
    return c, drawn


def tex_console(P):
    """The console: a dark screen in a grey desk; logged in, the screen lights with lines of text."""
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    rng = P.rng_for("lab-console")
    for y in range(16):
        for x in range(16):
            edge = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            c.set(x, y, P.ramp("plate")[3] if edge else P.ramp("frame")[1])
    for row in range(3, 13, 2):
        n = rng.randrange(4, 11)
        for x in range(3, 3 + n):
            g.set(x, row, K.rgba("#5ae0ec" if row > 3 else "#b0fcff"))
    side = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            side.set(x, y, P.ramp("plate")[2 + (y % 5 == 0)])
    return c, g, side


def tex_cube(P):
    """The Humming Cube: a black cube with seams of cyan light; powered, they shine."""
    fp, cp = P.ramp("frame"), P.ramp("cyan")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            seam = x in (4, 11) or y in (4, 11)
            c.set(x, y, cp[2] if seam else fp[2 + (x + y) % 2])
            if seam:
                g.set(x, y, cp[5])
    return c, g


def textures():
    P = _P()
    t = {}
    t["lab_panel"] = tex_panel(P, False)
    t["lab_panel_lit"], t["lab_panel_lit_glow"] = tex_panel(P, True)
    t["lab_floor"] = tex_floor(P)
    t["lab_frame"] = tex_frame(P)
    t["lab_frame_top"] = tex_frame_end(P)
    t["lab_glass"] = tex_glass(P)
    t["lab_rack"], t["lab_rack_glow"] = tex_rack(P)
    t["lab_tank"], t["lab_tank_glow"] = tex_tank(P)
    t["lab_cables"], t["lab_cables_glow"] = tex_cables(P)
    t["lab_light"], t["lab_light_glow"] = tex_light(P)
    t["lab_hologram"], t["lab_hologram_glow"] = tex_hologram(P)
    t["lab_moss"] = tex_moss(P)
    t["lab_sketch"], t["lab_sketch_drawn"] = tex_sketch(P)
    t["lab_console"], t["lab_console_glow"], t["lab_console_side"] = tex_console(P)
    t["humming_cube"], t["humming_cube_glow"] = tex_cube(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "lab_panel": ("Lab Panel", "pickaxe"),
    "lab_panel_lit": ("Lit Lab Panel", "pickaxe"),
    "lab_floor": ("Floor Plate", "pickaxe"),
    "lab_frame": ("Black Frame", "pickaxe"),
    "lab_glass": ("Lab Glass", "pickaxe"),
    "lab_rack": ("Server Rack", "pickaxe"),
    "lab_tank": ("Specimen Tank", "pickaxe"),
    "lab_cables": ("Hanging Cables", None),
    "lab_light": ("Work Light", None),
    "lab_hologram": ("Hologram", None),
    "lab_moss": ("Green Spill", None),
    "lab_sketch": ("Sketched Block", "pickaxe"),
    "lab_console": ("Console", "pickaxe"),
    "humming_cube": ("The Humming Cube", "pickaxe"),
}
FLAT = ("lab_cables", "lab_hologram", "lab_moss")
B = K.B


def models():
    m = {}
    m["lab_panel"] = K.cube_model("lab_panel")
    m["lab_panel_lit"] = K.cube_model("lab_panel_lit", glow=True)
    m["lab_floor"] = K.cube_model("lab_floor")
    m["lab_frame"] = K.column_model("lab_frame", "lab_frame_top")
    m["lab_glass"] = K.cube_model("lab_glass", translucent=True)
    m["lab_rack"] = K.cube_model("lab_rack", glow=True)
    m["lab_tank"] = K.cube_model("lab_tank", glow=True, translucent=True)
    m["lab_cables"] = K.cross_model("lab_cables", glow=True)
    m["lab_hologram"] = K.cross_model("lab_hologram", glow=True)
    m["lab_moss"] = K.carpet_model("lab_moss")
    m["lab_sketch"] = K.cube_model("lab_sketch")
    m["lab_sketch_drawn"] = K.cube_model("lab_sketch_drawn")
    # the work light: a black shade on its cable, white light under it
    m["lab_light"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                      "textures": {"particle": B + "lab_light", "shade": B + "lab_light", "glow": B + "lab_light_glow",
                                   "cable": B + "lab_frame"},
                      "elements": [K.box([7.5, 8, 7.5], [8.5, 16, 8.5], "#cable"), K.box([4, 4, 4], [12, 8, 12], "#shade", uv=[0, 0, 16, 8]),
                                   K.box([4.5, 3.98, 4.5], [11.5, 3.98, 11.5], "#glow", 15, sides=("down",), uv=[0, 12, 16, 16])]}
    # the console: a desk with its screen tilted up; logged in, the screen lights
    for lit in (False, True):
        tex = {"particle": B + "lab_console_side", "side": B + "lab_console_side", "screen": B + "lab_console"}
        els = [K.box([2, 0, 2], [14, 9, 14], "#side"), K.box([2, 9, 8], [14, 14, 14], "#side"),
               K.box([2.5, 9.5, 7.98], [13.5, 13.5, 7.98], "#screen", sides=("north",), uv=[2, 2, 14, 14])]
        if lit:
            tex["glow"] = B + "lab_console_glow"
            els.append(K.box([2.5, 9.5, 7.96], [13.5, 13.5, 7.96], "#glow", 15, sides=("north",), uv=[2, 2, 14, 14]))
        m["lab_console" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Humming Cube: a black cube with seams of light; while someone's near, it powers redstone and shines
    for on in (False, True):
        tex = {"particle": B + "humming_cube", "cube": B + "humming_cube", "glow": B + "humming_cube_glow"}
        els = [K.box([4, 0, 4], [12, 8, 12], "#cube", uv=[4, 4, 12, 12]),
               K.box([3.98, -0.02, 3.98], [12.02, 8.02, 12.02], "#glow", 15 if on else 6, uv=[4, 4, 12, 12])]
        m["humming_cube" + ("_on" if on else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("lab_frame", "lab_sketch", "lab_console", "humming_cube")}
    s["lab_frame"] = K.axis_state("lab_frame")
    s["lab_sketch"] = {"variants": {"open=false": {"model": B + "lab_sketch"}, "open=true": {"model": B + "lab_sketch_drawn"}}}
    s["lab_console"] = K.lit_state("lab_console")
    s["humming_cube"] = {"variants": {"powered=false": {"model": B + "humming_cube"}, "powered=true": {"model": B + "humming_cube_on"}}}
    return s


def content():
    K.content("gate_worlds_lab", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_lab.png")
    else:
        print(__doc__)
