"""Gate Worlds, Lantern Night Market (SPEC §38.11): the theme's 12 world blocks and its set piece, the Lucky Cat.

    python3 tools/art/build_all.py gate_worlds_market        textures
    python3 tools/art/specs/gate_worlds_market.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_market.py sheet PATH every texture at 6x, named

Worn boardwalk boards, wet cobbles, white plaster, red-lacquered timber posts, grey roof tiles, paper walls lit from
behind, round red paper lanterns, strings of little lights, red cloth awnings, stalls of goods, lamps that float on the
canals and the wish lantern you light. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "boards": ["#1e140e", "#2c1d14", "#3a271b", "#4a3222", "#5a3e2a", "#6a4a32"],
    "cobble": ["#16181e", "#20232a", "#2b2f38", "#373c46", "#444a56", "#525a66"],
    "plaster": ["#7a7468", "#948e80", "#aca596", "#c2bcac", "#d6d0c0", "#e6e0d0"],
    "lacquer": ["#2a0606", "#4a0c0a", "#6a1410", "#8a1e16", "#a82a1e", "#c43a28"],
    "roof": ["#14161c", "#1c2028", "#262b35", "#313744", "#3e4554", "#4c5466"],
    "paper": ["#c88a3a", "#e0a850", "#f0c46c", "#fadc92", "#fff0c4"],
    "lantern": ["#5a0a08", "#8a140e", "#b42014", "#dc3a1e", "#ff6a3a", "#ffa070"],
    "cloth": ["#3a0808", "#5a0e0c", "#7a1612", "#9a2018", "#b82c20"],
    "gold": ["#5a3a10", "#8a5a18", "#b88024", "#e0a838", "#ffd060", "#fff0a8"],
}
WARM = ("#ffb060", "#ffd890", "#fff4d8")


def _P():
    return K.px(RAMPS)


def tex_boards(P):
    c = K.boards(P, "boards", "market-boards", h=4)
    rng = P.rng_for("board-wet")
    for _ in range(6):
        c.blend(rng.randrange(16), rng.randrange(16), K.rgba("#ffb060"), 0.12)
    return c


def tex_cobbles(P):
    cp = P.ramp("cobble")
    rng = P.rng_for("market-cobbles")
    pts = [(rng.random() * 16, rng.random() * 16) for _ in range(9)]
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            ds = sorted(min(math.hypot(x - px + ox, y - py + oy) for ox in (-16, 0, 16) for oy in (-16, 0, 16)) for px, py in pts)
            if ds[1] - ds[0] < 0.9:
                c.set(x, y, cp[0])
            else:
                c.set(x, y, cp[2 + int(ds[0] < 1.8) + (1 if (x + y * 3) % 7 == 0 else 0)])
    # puddle shine from the lanterns
    for _ in range(5):
        c.blend(rng.randrange(16), rng.randrange(16), K.rgba("#ff9a50"), 0.3)
    return c


def tex_plaster(P):
    c = K.noisy(P, "plaster", "market-plaster", scale=4, lo=2, span=3, jitter=0.3)
    rng = P.rng_for("plaster-stain")
    for x in range(16):
        for y in range(13, 16):
            if rng.random() < 0.4:
                c.blend(x, y, K.rgba("#3a3020"), 0.3)
    return c


def tex_timber(P):
    lp = P.ramp("lacquer")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 2 + int(2 * math.sin((x + 0.5) / 16 * math.pi)) + (1 if x == 5 else 0)
            c.set(x, y, lp[min(5, v)])
    rng = P.rng_for("lacquer-chips")
    for _ in range(4):
        c.set(rng.randrange(16), rng.randrange(16), P.ramp("boards")[3])
    for x in range(16):
        c.set(x, 2, P.ramp("gold")[3])
    return c


def tex_timber_end(P):
    return K.rings(P, "boards", "lacquer", "market-timber")


def tex_roof(P):
    rp = P.ramp("roof")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            u = (x + (2 if (y // 4) % 2 else 0)) % 4
            v = y % 4
            c.set(x, y, rp[0] if v == 3 else rp[4] if u == 1 else rp[2] if u in (0, 3) else rp[3])
    return c


def tex_paper_wall(P):
    pp = P.ramp("paper")
    frame = P.ramp("lacquer")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15) or x in (5, 10) or y in (5, 10):
                c.set(x, y, frame[1 + (x + y) % 2])
            else:
                d = math.hypot(x - 7.5, y - 7.5) / 10
                col = pp[max(0, min(4, int(4 - d * 4)))]
                c.set(x, y, (*col[:3], 225))
                g.set(x, y, (*col[:3], 230))
    return c, g


def tex_lantern(P):
    lp = P.ramp("lantern")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            ribs = x % 4 == 0
            k = math.sin((x + 0.5) / 16 * math.pi)
            if y in (0, 1, 14, 15):
                c.set(x, y, P.ramp("boards")[1])
            elif ribs or y % 5 == 2:
                c.set(x, y, lp[1])
            else:
                c.set(x, y, lp[2 + int(k * 3)])
                g.set(x, y, lp[3 + int(k * 2)])
    return c, g


def tex_lights(P):
    c = P.Canvas(16, 16)
    for y in range(16):
        x = 7 + int(round(math.sin(y * 0.4) * 0.6))
        c.set(x, y, K.rgba("#1a1a1a"))
        if y % 5 == 2:
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                c.set(x + dx, y + dy, K.rgba(WARM[(y // 5) % 3]))
    return c, K.only(c, {K.rgba(h)[:3] for h in WARM})


def tex_cloth(P):
    cp = P.ramp("cloth")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            stripe = (x // 4) % 2
            c.set(x, y, cp[3 if stripe else 2] if y % 8 else cp[1])
            if not stripe and (x + y) % 6 == 0:
                c.set(x, y, P.ramp("gold")[3])
    return c


def tex_goods(P):
    """A stall's goods: a crate of fruit and a basket, from above and from the side."""
    rng = P.rng_for("goods")
    top = P.Canvas(16, 16)
    fruit = ("#e0602a", "#f0a030", "#c8283a", "#88b040", "#f0d050")
    for y in range(16):
        for x in range(16):
            top.set(x, y, P.ramp("boards")[2] if x in (0, 15) or y in (0, 15) else P.ramp("boards")[1])
    for _ in range(26):
        x, y = rng.randrange(1, 14), rng.randrange(1, 14)
        col = K.rgba(fruit[rng.randrange(len(fruit))])
        hi = tuple(min(255, v + 50) for v in col[:3]) + (255,)
        top.set(x, y, col)
        top.set(x + 1, y, col)
        top.set(x, y + 1, col)
        top.set(x + 1, y + 1, hi if rng.random() < 0.5 else col)
    side = K.boards(P, "boards", "goods-side", h=4, nails=False)
    return top, side


def tex_boat_lamp(P):
    pp = P.ramp("paper")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            petal = r < 5.5 + math.cos(a * 6) * 1.6
            if r < 2.2:
                c.set(x, y, K.rgba(WARM[2]))
                g.set(x, y, K.rgba(WARM[2]))
            elif petal:
                col = pp[3] if r < 4 else pp[1]
                c.set(x, y, col)
                g.set(x, y, (*K.rgba(WARM[0])[:3], 160))
    return c, g


def tex_wish(P):
    lp = P.ramp("lantern")
    paper = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            paper.set(x, y, lp[3 if (x // 3 + y // 5) % 2 else 2] if x % 5 else lp[1])
    glow = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x % 5:
                glow.set(x, y, K.rgba(WARM[1] if 4 < y < 12 else WARM[0]))
    post = K.bark(P, "boards", "wish-post")
    return paper, glow, post


def tex_cat(P):
    """The Lucky Cat: white glaze, a red collar with a gold bell, one paw raised; the coin it holds glows."""
    body = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            body.set(x, y, K.rgba("#f2eee4") if (x + y) % 5 else K.rgba("#e2ddd0"))
    face = P.Canvas(16, 16)
    rows = ["................",
            ".KK..........KK.",
            ".KPK........KPK.",
            ".KWWWWWWWWWWWWK.",
            "..WWWWWWWWWWWW..",
            "..WWKKWWWWKKWW..",
            "..WWWWWWWWWWWW..",
            "..WWWWWPPWWWWW..",
            "..WWKWWWWWWKWW..",
            "..WWWKKKKKKWWW..",
            "..RRRRRRRRRRRR..",
            "..RRRRRGGRRRRR..",
            "..WWWWWGGWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "................"]
    face = K.sprite(rows, {"K": "#1a1a1a", "P": "#f0a0a8", "W": "#f2eee4", "R": "#c42a20", "G": "#ffd060"})
    coin = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7:
                coin.set(x, y, P.ramp("gold")[5 if d < 3 else 4 if d < 5.5 else 3])
    return body, face, coin


def textures():
    P = _P()
    t = {}
    t["market_boards"] = tex_boards(P)
    t["market_cobbles"] = tex_cobbles(P)
    t["market_plaster"] = tex_plaster(P)
    t["market_timber"] = tex_timber(P)
    t["market_timber_top"] = tex_timber_end(P)
    t["market_roof"] = tex_roof(P)
    t["market_paper_wall"], t["market_paper_wall_glow"] = tex_paper_wall(P)
    t["market_lantern"], t["market_lantern_glow"] = tex_lantern(P)
    t["market_lights"], t["market_lights_glow"] = tex_lights(P)
    t["market_cloth"] = tex_cloth(P)
    t["market_goods"], t["market_goods_side"] = tex_goods(P)
    t["market_boat_lamp"], t["market_boat_lamp_glow"] = tex_boat_lamp(P)
    t["market_wish_lantern"], t["market_wish_lantern_glow"], t["market_wish_post"] = tex_wish(P)
    t["lucky_cat"], t["lucky_cat_face"], t["lucky_cat_coin"] = tex_cat(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "market_boards": ("Boardwalk Boards", "axe"),
    "market_cobbles": ("Market Cobbles", "pickaxe"),
    "market_plaster": ("White Plaster", "pickaxe"),
    "market_timber": ("Red Lacquered Post", "axe"),
    "market_roof": ("Market Roof Tiles", "pickaxe"),
    "market_paper_wall": ("Lit Paper Wall", "axe"),
    "market_lantern": ("Red Paper Lantern", None),
    "market_lights": ("String of Lights", None),
    "market_cloth": ("Awning Cloth", None),
    "market_goods": ("Stall Goods", "axe"),
    "market_boat_lamp": ("Floating Lamp", None),
    "market_wish_lantern": ("Wish Lantern", "axe"),
    "lucky_cat": ("The Lucky Cat", "pickaxe"),
}
FLAT = ("market_lights",)
B = K.B


def models():
    m = {}
    m["market_boards"] = K.cube_model("market_boards")
    m["market_cobbles"] = K.cube_model("market_cobbles")
    m["market_plaster"] = K.cube_model("market_plaster")
    m["market_timber"] = K.column_model("market_timber", "market_timber_top")
    m["market_roof"] = K.cube_model("market_roof")
    m["market_paper_wall"] = K.cube_model("market_paper_wall", glow=True, translucent=True)
    m["market_cloth"] = K.cube_model("market_cloth")
    m["market_lights"] = K.cross_model("market_lights", glow=True)
    m["market_boat_lamp"] = K.carpet_model("market_boat_lamp", glow=True)
    # the paper lantern: a round red lantern on a cord, lit from inside
    m["market_lantern"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                           "textures": {"particle": B + "market_lantern", "paper": B + "market_lantern",
                                        "glow": B + "market_lantern_glow", "cap": B + "market_timber_top"},
                           "elements": [K.box([3, 3, 3], [13, 12, 13], "#paper", uv=[0, 2, 16, 14]),
                                        K.box([2.98, 3, 2.98], [13.02, 12, 13.02], "#glow", 15,
                                              sides=("north", "south", "west", "east"), uv=[0, 2, 16, 14]),
                                        K.box([5, 12, 5], [11, 13, 11], "#cap"), K.box([5, 2, 5], [11, 3, 11], "#cap"),
                                        K.box([7.5, 13, 7.5], [8.5, 16, 8.5], "#cap")]}
    # stall goods: a low crate heaped with fruit
    m["market_goods"] = {"parent": "minecraft:block/block",
                         "textures": {"particle": B + "market_goods_side", "top": B + "market_goods", "side": B + "market_goods_side"},
                         "elements": [{"from": [1, 0, 1], "to": [15, 12, 15],
                                       "faces": {"up": {"texture": "#top"}, "down": {"texture": "#side"},
                                                 **{d: {"texture": "#side", "uv": [0, 4, 16, 16]} for d in ("north", "south", "west", "east")}}}]}
    # the wish lantern: a tall paper lantern on a short post; lit, it glows gold
    for lit in (False, True):
        tex = {"particle": B + "market_wish_lantern", "paper": B + "market_wish_lantern", "post": B + "market_wish_post"}
        els = [K.box([6.5, 0, 6.5], [9.5, 4, 9.5], "#post"), K.box([4, 4, 4], [12, 15, 12], "#paper"),
               K.box([5, 15, 5], [11, 16, 11], "#post")]
        if lit:
            tex["glow"] = B + "market_wish_lantern_glow"
            els.append(K.box([3.98, 4, 3.98], [12.02, 15, 12.02], "#glow", 15, sides=("north", "south", "west", "east")))
        m["market_wish_lantern" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Lucky Cat: a white glazed cat with one paw up; the gold coin it holds shines while it has a gift
    for full in (True, False):
        tex = {"particle": B + "lucky_cat", "body": B + "lucky_cat", "face": B + "lucky_cat_face", "coin": B + "lucky_cat_coin"}
        els = [K.box([4, 0, 5], [12, 8, 11], "#body"), K.box([4.5, 8, 5.5], [11.5, 13, 10.5], "#body"),
               K.box([4.49, 8, 5.49], [11.51, 13, 5.49], "#face", sides=("north",), uv=[2, 1, 14, 15]),
               K.box([10.5, 9, 7], [12.5, 13, 9], "#body"), K.box([5, 2, 4.5], [11, 6, 5], "#coin", 15 if full else 0, sides=("north",))]
        m["lucky_cat" + ("" if full else "_empty")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("market_timber", "market_wish_lantern", "lucky_cat")}
    s["market_timber"] = K.axis_state("market_timber")
    s["market_wish_lantern"] = K.lit_state("market_wish_lantern")
    s["lucky_cat"] = {"variants": {"full=true": {"model": B + "lucky_cat"}, "full=false": {"model": B + "lucky_cat_empty"}}}
    return s


def content():
    K.content("gate_worlds_market", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_market.png")
    else:
        print(__doc__)
