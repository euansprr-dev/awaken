"""Gate Worlds, Bell Crypt (SPEC §38.6): the theme's 15 world blocks, their textures and their content, and the sprites
of its worlds' small life (moths, bats, pale fish, rats; SPEC §38.9).

    python3 tools/art/build_all.py gate_worlds_bell        textures
    python3 tools/art/specs/gate_worlds_bell.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_bell.py sheet PATH every texture at 6x, named

Limestone in three states of wear, carved tracery, worn and wet flagstones, patina bronze, candle clusters (one to five,
lit or out, with wax drips), moon glass, rose glass in five colours, an ossuary niche, a drowned vestment that sways,
bell rope and floating lily candles. Drawn by code, nothing copied: the first drafts were the plan page's mock-ups.
"""
from __future__ import annotations

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge")


def _px():
    import pixelart as P
    P.RAMPS.update({
        "limestone": ["#2e2c2a", "#3f3c38", "#524e48", "#67625a", "#7d776d", "#958e82"],
        "limestone_dark": ["#1d1c1b", "#292725", "#36332f", "#44403b", "#524e47"],
        "flag": ["#1f1f22", "#2a2a2e", "#35353a", "#424146", "#504f53"],
        "algae": ["#16241c", "#1f3428", "#2a4634", "#365a40"],
        "bronze_p": ["#231a12", "#352819", "#4a3922", "#5f4b2d", "#76603a"],
        "verd": ["#1e3c33", "#2b5649", "#3b7462", "#4f927c", "#6cb09a"],
        "wax": ["#6e6656", "#8f8670", "#b0a68c", "#cdc3a6", "#e6dcc0", "#f6efdb"],
        "flame": ["#ff8a2a", "#ffb44a", "#ffd88a", "#fff2cc"],
        "moon": ["#5c78a8", "#7c96c4", "#9db4dc", "#bed0ee", "#dce8fb"],
        "lead": ["#0b0b0e", "#16161b"],
        "cloth": ["#0c1a1c", "#13262a", "#1b3439", "#244349", "#2f555b"],
        "gold_thread": ["#5a4316", "#86662a", "#b08c40", "#d4b462"],
        "rope": ["#3a2b18", "#55401f", "#715629", "#8e6c35", "#a98443"],
        "lily": ["#13261a", "#1c3824", "#264b2f", "#335f3c", "#43744a"],
        "bone_n": ["#4f4636", "#6e6450", "#918670", "#b6ac93", "#d6ceb6", "#f1ead6"],
    })
    return P


ROSE = [  # five colours of rose glass: deep blue, ruby, amber, sky, violet
    ["#14227a", "#1f34a8", "#2f4cd2", "#5274ee"],
    ["#5c0c12", "#8c141c", "#b8202a", "#e04a4a"],
    ["#6a4208", "#9c6410", "#cc8c1e", "#f0b84a"],
    ["#155a74", "#1f7ea0", "#33a4c8", "#66c8e6"],
    ["#3a1060", "#561a8a", "#7428b2", "#9a52d6"],
]


def _canvas(P, fill):
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, fill(x, y))
    return c


def _lime(P, seed, ramp="limestone", lo=1, span=4):
    rp = P.ramp(ramp)
    n = P.value_noise(16, 16, P.rng_for("crypt", seed), scale=3, octaves=3)
    return [[rp[max(0, min(len(rp) - 1, lo + int(n[y][x] * span)))] for x in range(16)] for y in range(16)]


def tex_limestone(P, wear=0):
    """Ashlar courses: 8-high blocks, staggered joints; wear 0 cut, 1 worn (rounded arrises, stains), 2 crumbling."""
    g = _lime(P, f"lime{wear}")
    dark = P.ramp("limestone_dark")
    alg = P.ramp("algae")
    rng = P.rng_for("crypt", "joints", wear)
    stain = P.value_noise(16, 16, P.rng_for("crypt", "stain", wear), scale=2, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            course = y // 8
            jx = (x + course * 8) % 16
            col = g[y][x]
            joint = y % 8 == 7 or jx == 15
            if joint:
                col = dark[1 if wear < 2 else 0]
            elif y % 8 == 0 or jx == 0:
                col = tuple(min(255, v + 10) for v in col[:3]) + (255,)  # the lit upper arris
            if wear >= 1 and not joint and y > 9 and stain[y][x] > 0.58:
                col = alg[1 + int((stain[y][x] - 0.58) * 6) % 3]
            if wear >= 1 and joint and rng.random() < 0.25:
                col = dark[0]
            c.set(x, y, col)
    if wear >= 2:
        # chunks fallen out of the face and a crack running down it
        for _ in range(5):
            cx, cy = rng.randrange(16), rng.randrange(16)
            for dx in range(-1, 2):
                for dy in range(-1, 1):
                    if rng.random() < 0.7:
                        c.set(cx + dx, cy + dy, dark[0])
        x = rng.randrange(4, 12)
        for y in range(16):
            x += rng.choice((-1, 0, 0, 1))
            c.set(x % 16, y, dark[0])
    return c


def tex_limestone_top(P, wear=0):
    g = _lime(P, f"limetop{wear}", lo=1, span=3)
    dark = P.ramp("limestone_dark")
    return _canvas(P, lambda x, y: dark[1] if (x in (0, 15) or y in (0, 15)) and wear < 2 else g[y][x])


def tex_tracery(P):
    """A carved trefoil arch in the face: dark recess, lit upper lip. Paler than the walls, so ribs and lintels read."""
    g = _lime(P, "tracery", lo=2, span=3)
    dark = P.ramp("limestone_dark")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            arch = ((x - 7.5) ** 2 / 30 + (y - 8) ** 2 / 44 < 1) and y > 2
            inner = ((x - 7.5) ** 2 / 13 + (y - 8.5) ** 2 / 28 < 1) and y > 3
            lobe = (x - 5) ** 2 + (y - 5) ** 2 < 3 or (x - 10) ** 2 + (y - 5) ** 2 < 3 or (x - 7.5) ** 2 + (y - 3) ** 2 < 3
            rec = (arch and not inner) or (lobe and arch)
            col = g[y][x]
            if rec:
                col = dark[2]
            if y in (0, 15):
                col = dark[3]
            c.set(x, y, col)
    # the upper lip of every recess catches the light
    for y in range(1, 16):
        for x in range(16):
            if c.get(x, y) == tuple(dark[2]) and c.get(x, y - 1) != tuple(dark[2]):
                c.set(x, y - 1, tuple(min(255, v + 44) for v in g[y - 1][x][:3]))
    return c


def tex_flag(P, wet=False):
    rp = P.ramp("flag")
    n = P.value_noise(16, 16, P.rng_for("crypt", "flag", wet), scale=3, octaves=3)
    alg = P.ramp("algae")
    rng = P.rng_for("crypt", "crack", wet)
    # four worn stones of unequal size: joints along the edges and one cross joint, offset
    c = _canvas(P, lambda x, y: rp[0] if x == 0 or y == 0 or (y == 8 and x < 10) or (x == 10 and y >= 1) or (x == 5 and y > 8)
                else rp[1 + int(n[y][x] * 3.5)])
    x = 3
    for y in range(1, 8):
        x += rng.choice((0, 1))
        c.set(x, y, rp[0])
    if wet:
        # a sheen along the stones and green at the joints where the water stands
        for y in range(16):
            for x in range(16):
                if c.get(x, y) == tuple(rp[0]) and rng.random() < 0.6:
                    c.set(x, y, alg[1 + rng.randrange(2)])
                elif (x + y * 3) % 11 == 0:
                    c.set(x, y, rp[4])
    return c


def tex_patina(P):
    """Old bronze: brown under the green, verdigris run down from the top in drips, the lip worn bright."""
    br, vg = P.ramp("bronze_p"), P.ramp("verd")
    n = P.value_noise(16, 16, P.rng_for("crypt", "patina"), scale=3, octaves=3)
    rnd = P.rng_for("crypt", "drips")
    drip = [rnd.randrange(4, 14) if rnd.random() < 0.45 else 0 for _ in range(16)]

    def f(x, y):
        if y > 13:
            return br[4] if (x + y) % 3 else br[3]
        if y < drip[x]:
            return vg[min(4, 1 + int(n[y][x] * 2 + (1 - y / drip[x]) * 1.6))]
        if n[y][x] > 0.74:
            return vg[1]
        return br[1 + int(n[y][x] * 3)]
    return _canvas(P, f)


def tex_bell_prop(P):
    """A swinging bell's skin, 64 px so a bell five blocks wide keeps a block's detail: brown bronze under green,
    verdigris run down from the shoulder in long drips, the sound bow worn bright, a cast band near the lip."""
    S = 64
    br, vg = P.ramp("bronze_p"), P.ramp("verd")
    n = P.value_noise(S, S, P.rng_for("crypt", "bell_prop"), scale=6, octaves=4)
    rnd = P.rng_for("crypt", "bell_prop_drips")
    drip = [rnd.randrange(10, 50) if rnd.random() < 0.5 else rnd.randrange(0, 8) for _ in range(S)]
    for i in range(1, S - 1):  # drips run in threads two or three wide
        if drip[i - 1] > 20 and drip[i + 1] > 20:
            drip[i] = max(drip[i], (drip[i - 1] + drip[i + 1]) // 2 - 3)
    c = P.Canvas(S, S)
    for y in range(S):
        for x in range(S):
            if y >= 56:  # the sound bow, worn bright where hands and ropes touched it
                col = br[4] if (x * 3 + y) % 7 else br[3]
            elif 49 <= y <= 51:  # a cast band
                col = br[3] if y == 49 else br[1]
            elif y < drip[x]:
                col = vg[min(4, 1 + int(n[y][x] * 2 + (1 - y / max(1, drip[x])) * 1.8))]
            elif n[y][x] > 0.72:
                col = vg[1]
            else:
                col = br[1 + int(n[y][x] * 3)]
            c.set(x, y, col)
    return c


def tex_bell_mouth(P):
    """Looking up into a bell: dark bronze rings round a lamp (the glow is its own layer)."""
    S = 32
    br = P.ramp("bronze_p")
    c, g = P.Canvas(S, S), P.Canvas(S, S)
    for y in range(S):
        for x in range(S):
            d = ((x - 15.5) ** 2 + (y - 15.5) ** 2) ** 0.5
            c.set(x, y, br[0] if d > 13 else br[1] if int(d) % 4 else br[2])
            if d < 6:
                a = int(255 * min(1, (6 - d) / 3))
                g.set(x, y, (168, 214, 255, a) if d > 2.5 else (236, 246, 255, 255))
    return c, g


def tex_wax(P):
    wx = P.ramp("wax")
    return _canvas(P, lambda x, y: wx[5] if x % 4 == 0 else wx[4] if x % 4 == 1 else wx[3] if (y * 7 + x) % 9 else wx[2])


def tex_flame(P):
    fl = P.ramp("flame")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) / 3.5 + max(0, (y - 8) / 8)
            t = 1 - (y / 16)
            if abs(x - 7.5) < 4 * (1 - abs(y - 9) / 10) and 2 <= y <= 15:
                c.set(x, y, fl[min(3, int((1 - d) * 3.8))] if d < 1 else (0, 0, 0, 0))
    return c


def tex_moon(P):
    mo, ld = P.ramp("moon"), P.ramp("lead")
    n = P.value_noise(16, 16, P.rng_for("crypt", "moon"), scale=4, octaves=2)
    base = _canvas(P, lambda x, y: ld[0] if x % 8 == 0 or y % 5 == 0 else mo[1 + int(n[y][x] * 3.9)])
    glow = _canvas(P, lambda x, y: (0, 0, 0, 0) if x % 8 == 0 or y % 5 == 0 else mo[2 + int(n[y][x] * 2.9)])
    return base, glow


def tex_rose(P, k):
    ld = P.ramp("lead")
    rng = P.rng_for("crypt", "rose", k)
    pal = [P.hex_rgba(h) for h in ROSE[k]]
    cells = {}
    base, glow = P.Canvas(16, 16), P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            lead = x % 4 == 0 or y % 4 == 0
            key = (x // 4, y // 4)
            if key not in cells:
                cells[key] = pal[rng.randrange(1, 4)] if rng.random() < 0.8 else P.hex_rgba(ROSE[(k + 2) % 5][2])
            base.set(x, y, ld[0] if lead else cells[key])
            glow.set(x, y, (0, 0, 0, 0) if lead else cells[key])
    return base, glow


def tex_niche(P):
    """A round-headed alcove cut in the limestone, a skull inside, a candle stub burning beside it."""
    g = _lime(P, "niche")
    dark, bone, fl = P.ramp("limestone_dark"), P.ramp("bone_n"), P.ramp("flame")
    c, glow = P.Canvas(16, 16), P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            inside = 3 <= x <= 12 and 4 <= y <= 14 and ((x - 7.5) ** 2 + (y - 8) ** 2 < 22 or y >= 8)
            c.set(x, y, dark[0] if inside else g[y][x])
    skull = ["..####..", ".######.", "##o##o##", "##o##o##", ".###n##.", "..#tt#.."]
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            x, y = 4 + i, 8 + j
            if ch == "#":
                c.set(x, y, bone[3 + (i in (2, 3) and j < 2) - (j > 3)])
            elif ch == "t":
                c.set(x, y, bone[2])
            elif ch in "on":
                c.set(x, y, dark[0])
    for y in (12, 13, 14):
        c.set(11, y, P.ramp("wax")[4])
    for y, k in ((10, 3), (11, 1)):
        c.set(11, y, fl[k])
        glow.set(11, y, fl[k])
    # the stub's glow on the alcove wall
    for (x, y) in ((10, 10), (12, 10), (11, 9), (10, 11)):
        c.set(x, y, (60, 44, 26, 255))
    return c, glow


def tex_vestment(P):
    """A drowned vestment hung from a rod: dark teal cloth, gold thread at the hem, wet and ragged. Animated sway."""
    cl, gd = P.ramp("cloth"), P.ramp("gold_thread")
    frames = []
    for f in range(8):
        c = P.Canvas(16, 16)
        ph = f / 8 * 2 * math.pi
        for y in range(16):
            sway = math.sin(ph + y * 0.35) * (y / 15) * 1.2
            for x in range(16):
                xs = x - sway
                if xs < 1 or xs > 14:
                    continue
                rag = y > 13 and (int(xs * 3) % 5 == 0 or (y == 15 and int(xs) % 3 == 1))
                if rag:
                    continue
                fold = math.sin(xs * 1.3 + ph * 0.5) * 0.5 + 0.5
                col = cl[1 + int(fold * 3)]
                if y in (11, 12) or (2 <= xs <= 3 or 12 <= xs <= 13) and y > 2:
                    col = gd[1 + (int(xs + y) % 3)]
                if y < 1:
                    col = P.ramp("rope")[2]
                c.set(x, y, col)
        frames.append(c)
    return frames


def tex_rope(P):
    rp = P.ramp("rope")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in (6, 7, 8, 9):
            twist = (x + y) % 4
            c.set(x, y, rp[1 + twist] if x in (7, 8) else rp[0 + twist % 2])
    return c


def tex_lily(P):
    lp = P.ramp("lily")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ang = math.atan2(y - 7.5, x - 7.5)
            if d < 7.2 and not (abs(ang - 0.6) < 0.25 and d > 2):
                vein = abs(math.sin(ang * 5)) < 0.15
                c.set(x, y, lp[4] if vein else lp[1 + int((7.2 - d) / 2.6)])
    return c


# ---- the worlds' small life (SPEC §38.9): drawn in their own colours, a few frames each -----------------------------

def _sprite(P, rows, pal):
    """A 16x16 sprite from rows of characters, each a palette key ('.' clear)."""
    c = P.Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in pal:
                c.set(x, y, pal[ch])
    return c


MOTH = {"b": (92, 78, 62, 255), "w": (214, 204, 178, 255), "v": (176, 164, 138, 255), "e": (140, 126, 104, 255)}
MOTH_FRAMES = [
    ["................", "................", "................", "................",
     "...ww......ww...", "..wwvw....wvww..", "..wvwwe..ewwvw..", "...wwwwbbwwww...",
     "....vwwbbwwv....", ".....wwbbww.....", "......vbbv......", ".......bb.......",
     "................", "................", "................", "................"],
    ["................", "................", "................", "................",
     "................", "................", ".....w....w.....", ".....wwbbww.....",
     "......wbbw......", "......vbbv......", ".......bb.......", ".......bb.......",
     "................", "................", "................", "................"],
]
BAT = {"k": (28, 22, 24, 255), "m": (54, 42, 44, 255), "f": (74, 60, 58, 255), "e": (150, 40, 34, 255)}
BAT_FRAMES = [
    ["................", "................", "m.............m.", "mm...........mm.", "fmm..........mmf",
     ".fmm..k.k..mmf..", "..fmm.kkk.mmf...", "...fmmkekmmf....", "....fmkkkmf.....", ".....mkkkm......",
     "......kkk.......", ".......k........", "................", "................", "................", "................"],
    ["................", "................", "................", "................", "................",
     "......k.k.......", "mmm...kkk...mmm.", "fmmmmmkekmmmmmf.", ".ffmmmkkkmmmff..", "...ff.kkk.ff....",
     "......kkk.......", ".......k........", "................", "................", "................", "................"],
    ["................", "................", "................", "................", "................",
     "......k.k.......", "......kkk.......", ".....mkekm......", "....mmkkkmm.....", "...mmf.kk.fmm...",
     "..mmf..kk..fmm..", ".mmf...k....fmm.", "mmf..........fmm", "mf............fm", "................", "................"],
]
FISH = {"p": (206, 222, 228, 255), "s": (160, 182, 194, 255), "d": (118, 138, 152, 255), "e": (40, 46, 56, 255),
        "f": (186, 206, 216, 170)}
FISH_FRAMES = [
    ["................", "................", "................", "................", "................",
     "f...............", "ff....sssss.....", ".fspppppppppss..", ".fdddddddddppep.", ".fspppppppppss..",
     "ff....sssss.....", "f...............", "................", "................", "................", "................"],
    ["................", "................", "................", "................", ".f..............",
     ".ff.............", "..f...sssss.....", "..fsppppppppss..", "..fddddddddppep.", "..fsppppppppss..",
     "......sssss.....", "................", "................", "................", "................", "................"],
]
RAT = {"g": (92, 84, 78, 255), "h": (122, 112, 102, 255), "d": (62, 56, 52, 255), "t": (176, 132, 124, 255),
       "e": (20, 16, 16, 255), "n": (196, 150, 142, 255)}
RAT_FRAMES = [
    ["................", "................", "................", "................", "................",
     "................", "................", "...........hh...", "....hhhhhhhhgeh.", "...hgggggggggggn",
     "ttdgggggggggggd.", "t..dgggggggggd..", "....d.d...d.d...", "................", "................", "................"],
    ["................", "................", "................", "................", "................",
     "................", "................", "...........hh...", "....hhhhhhhhgeh.", "...hgggggggggggn",
     ".tdgggggggggggd.", "tt.dgggggggggd..", "...d...d.d...d..", "................", "................", "................"],
]
LIFE = {"gate_world_moth": (MOTH_FRAMES, MOTH), "gate_world_bat": (BAT_FRAMES, BAT), "gate_world_fish": (FISH_FRAMES, FISH),
        "gate_world_rat": (RAT_FRAMES, RAT)}


def textures():
    """Texture name -> Canvas (a list of Canvases for an animation)."""
    P = _px()
    t = {}
    for wear, suffix in ((0, ""), (1, "_worn"), (2, "_crumbling")):
        t["crypt_limestone" + suffix] = tex_limestone(P, wear)
        t["crypt_limestone" + suffix + "_top"] = tex_limestone_top(P, wear)
    t["crypt_tracery"] = tex_tracery(P)
    t["crypt_flagstone"] = tex_flag(P)
    t["crypt_flagstone_wet"] = tex_flag(P, wet=True)
    t["crypt_patina"] = tex_patina(P)
    t["crypt_wax"] = tex_wax(P)
    t["crypt_flame"] = tex_flame(P)
    t["crypt_moon_glass"], t["crypt_moon_glass_glow"] = tex_moon(P)
    for k in range(5):
        t[f"crypt_rose_glass_{k}"], t[f"crypt_rose_glass_{k}_glow"] = tex_rose(P, k)
    t["crypt_niche"], t["crypt_niche_glow"] = tex_niche(P)
    t["crypt_vestment"] = tex_vestment(P)
    t["crypt_bell_rope"] = tex_rope(P)
    t["crypt_lily_pad"] = tex_lily(P)
    t["crypt_bell_prop"] = tex_bell_prop(P)
    t["crypt_bell_mouth"], t["crypt_bell_mouth_glow"] = tex_bell_mouth(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, frametime=5 if isinstance(c, list) else None)
    P = _px()
    for name, (frames, pal) in LIFE.items():
        for i, rows in enumerate(frames):
            out.particle(f"{name}_{i}", _sprite(P, rows, pal))


# =====================================================================================================================
# Content

BLOCKS = {
    # name: (English name, mining tool or None)
    "crypt_limestone": ("Crypt Limestone", "pickaxe"),
    "crypt_limestone_worn": ("Worn Limestone", "pickaxe"),
    "crypt_limestone_crumbling": ("Crumbling Limestone", "pickaxe"),
    "crypt_tracery": ("Carved Tracery", "pickaxe"),
    "crypt_flagstone": ("Worn Flagstones", "pickaxe"),
    "crypt_flagstone_wet": ("Wet Flagstones", "pickaxe"),
    "crypt_patina": ("Patina Bronze", "pickaxe"),
    "crypt_candles": ("Candle Cluster", None),
    "crypt_moon_glass": ("Moon Glass", None),
    "crypt_rose_glass": ("Rose Glass", None),
    "crypt_niche": ("Ossuary Niche", "pickaxe"),
    "crypt_vestment": ("Drowned Vestment", None),
    "crypt_bell_rope": ("Bell Rope", None),
    "crypt_lily_candle": ("Lily Candle", None),
    "crypt_handbell": ("Handbell", "pickaxe"),
}
SIDES = ("down", "up", "north", "south", "west", "east")
# where up to five candles stand in a cluster (x, z, height) and where each one's wax runs
CANDLES = [(7, 7, 9), (3, 9, 6), (10, 4, 7), (11, 10, 4), (4, 3, 5)]


def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    if "values" in data and isinstance(cur.get("values"), list):
        cur["values"] += [v for v in data["values"] if v not in cur["values"]]
    else:
        cur.update(data)
    _write(path, cur)


def _faces(tex, cull=True):
    return {d: dict({"texture": tex}, **({"cullface": d} if cull else {})) for d in SIDES}


def _cube(tex, emission=0, cull=True):
    e = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": _faces(tex, cull)}
    if emission:
        e["light_emission"] = emission
        e["shade_direction_override"] = "up"
    return e


def _box(a, b, tex, emission=0, uv=None):
    faces = {}
    for d in SIDES:
        f = {"texture": tex}
        if uv:
            f["uv"] = uv
        faces[d] = f
    e = {"from": a, "to": b, "faces": faces}
    if emission:
        e["light_emission"] = emission
    return e


def _plane(a, b, tex, emission=0):
    """A flat cross-plane (two faces back to back) between two corners, one axis flat."""
    e = {"from": a, "to": b, "faces": {}}
    flat = [i for i in range(3) if a[i] == b[i]][0]
    pair = {0: ("west", "east"), 1: ("down", "up"), 2: ("north", "south")}[flat]
    for d in pair:
        e["faces"][d] = {"texture": tex}
    if emission:
        e["light_emission"] = emission
    return e


def _candles_model(n, lit):
    els = []
    for i in range(n):
        x, z, h = CANDLES[i]
        els.append(_box([x, 0, z], [x + 2, h, z + 2], "#wax", uv=[0, 16 - h, 2, 16]))
        # a run of wax down one side
        els.append(_box([x + 2, max(0, h - 4), z], [x + 2.5, h - 1, z + 1], "#wax", uv=[4, 0, 5, 3]))
        if lit:
            els.append(_plane([x + 1, h, z], [x + 1, h + 3, z + 2], "#flame", 15))
            els.append(_plane([x, h, z + 1], [x + 2, h + 3, z + 1], "#flame", 15))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "awaken:block/crypt_wax", "wax": "awaken:block/crypt_wax", "flame": "awaken:block/crypt_flame"},
            "elements": els}


def models():
    """Model name -> json."""
    m = {}
    b = "awaken:block/"
    for wear in ("", "_worn", "_crumbling"):
        m["crypt_limestone" + wear] = {"parent": "minecraft:block/cube_column",
                                       "textures": {"side": b + "crypt_limestone" + wear, "end": b + "crypt_limestone" + wear + "_top"}}
    m["crypt_tracery"] = {"parent": "minecraft:block/cube_column", "textures": {"side": b + "crypt_tracery", "end": b + "crypt_limestone_top"}}
    m["crypt_flagstone"] = {"parent": "minecraft:block/cube_bottom_top",
                            "textures": {"top": b + "crypt_flagstone", "bottom": b + "crypt_limestone_top", "side": b + "crypt_limestone_worn"}}
    m["crypt_flagstone_wet"] = {"parent": "minecraft:block/cube_bottom_top",
                                "textures": {"top": b + "crypt_flagstone_wet", "bottom": b + "crypt_limestone_top", "side": b + "crypt_limestone_worn"}}
    m["crypt_patina"] = {"parent": "minecraft:block/cube_all", "textures": {"all": b + "crypt_patina"}}
    for n in range(1, 6):
        m[f"crypt_candles_{n}"] = _candles_model(n, False)
        m[f"crypt_candles_{n}_lit"] = _candles_model(n, True)
    m["crypt_moon_glass"] = {"parent": "minecraft:block/block",
                             "textures": {"particle": b + "crypt_moon_glass", "base": {"sprite": b + "crypt_moon_glass", "force_translucent": True},
                                          "glow": {"sprite": b + "crypt_moon_glass_glow", "force_translucent": True}},
                             "elements": [_cube("#base"), _cube("#glow", 15)]}
    for k in range(5):
        t = b + f"crypt_rose_glass_{k}"
        m[f"crypt_rose_glass_{k}"] = {"parent": "minecraft:block/block",
                                      "textures": {"particle": t, "base": {"sprite": t, "force_translucent": True},
                                                   "glow": {"sprite": t + "_glow", "force_translucent": True}},
                                      "elements": [_cube("#base"), _cube("#glow", 15)]}
    side = b + "crypt_limestone_worn"
    faces = {d: {"texture": "#side", "cullface": d} for d in SIDES}
    faces["north"] = {"texture": "#front", "cullface": "north"}
    m["crypt_niche"] = {"parent": "minecraft:block/block",
                        "textures": {"particle": side, "side": side, "front": b + "crypt_niche", "glow": b + "crypt_niche_glow"},
                        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces},
                                     {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                                      "faces": {"north": {"texture": "#glow", "cullface": "north"}}}]}
    # a cloth panel hanging a pixel off the wall it's against (north face toward you when it faces north)
    m["crypt_vestment"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                           "textures": {"particle": b + "crypt_vestment", "cloth": b + "crypt_vestment"},
                           "elements": [{"from": [0, 0, 14.5], "to": [16, 16, 14.5],
                                         "faces": {"north": {"texture": "#cloth"}, "south": {"texture": "#cloth"}}}]}
    m["crypt_bell_rope"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                            "textures": {"particle": b + "crypt_bell_rope", "rope": b + "crypt_bell_rope"},
                            "elements": [_plane([8, 0, 6], [8, 16, 10], "#rope"), _plane([6, 0, 8], [10, 16, 8], "#rope")]}
    m["crypt_lily_candle"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                              "textures": {"particle": b + "crypt_lily_pad", "pad": b + "crypt_lily_pad", "wax": b + "crypt_wax",
                                           "flame": b + "crypt_flame"},
                              "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16],
                                            "faces": {"up": {"texture": "#pad"}, "down": {"texture": "#pad"}}},
                                           _box([7, 0.25, 7], [9, 5, 9], "#wax", uv=[0, 11, 2, 16]),
                                           _plane([8, 5, 7], [8, 8, 9], "#flame", 15), _plane([7, 5, 8], [9, 8, 8], "#flame", 15)]}
    # a handbell hung in a stone yoke (posts and a beam across x when it faces north), standing on the floor
    m["crypt_handbell"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                           "textures": {"particle": b + "crypt_patina", "bronze": b + "crypt_patina", "yoke": b + "crypt_tracery"},
                           "elements": [_box([0, 0, 5.5], [3.5, 1.5, 10.5], "#yoke"), _box([12.5, 0, 5.5], [16, 1.5, 10.5], "#yoke"),
                                        _box([1, 1.5, 6.5], [2.5, 14, 9.5], "#yoke"), _box([13.5, 1.5, 6.5], [15, 14, 9.5], "#yoke"),
                                        _box([0.5, 14, 6], [15.5, 16, 10], "#yoke"),
                                        _box([7.5, 11.5, 7.5], [8.5, 14, 8.5], "#bronze"),
                                        _box([6.5, 10.5, 6.5], [9.5, 11.5, 9.5], "#bronze"),
                                        _box([5.5, 8, 5.5], [10.5, 10.5, 10.5], "#bronze"),
                                        _box([4.75, 5, 4.75], [11.25, 8, 11.25], "#bronze"),
                                        _box([4, 3.75, 4], [12, 5, 12], "#bronze"),
                                        _box([7.4, 2.25, 7.4], [8.6, 3.75, 8.6], "#bronze")]}
    # a hung bell the live layer swings (a block display scaled to the bell, 38.6): stepped like a cast bell, its
    # skin's drips running on from tier to tier, the mouth dark round a lamp that glows
    tiers = [(0, 2, 0), (2, 6, 1), (6, 10, 2), (10, 13, 3), (13, 15, 4.5), (15, 16, 6.5)]
    els = []
    for y0, y1, inset in tiers:
        faces = {d: {"texture": "#bronze", "uv": [0, 16 - y1, 16, 16 - y0]} for d in ("north", "south", "west", "east")}
        faces["up"] = {"texture": "#bronze", "uv": [inset, 0, 16 - inset, 2]}
        if y0 == 0:
            faces["down"] = {"texture": "#mouth"}
        els.append({"from": [inset, y0, inset], "to": [16 - inset, y1, 16 - inset], "faces": faces})
    els.append({"from": [3, -0.05, 3], "to": [13, -0.05, 13], "faces": {"down": {"texture": "#glow"}}, "light_emission": 15})
    m["bell_prop"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                      "textures": {"particle": b + "crypt_patina", "bronze": b + "crypt_bell_prop", "mouth": b + "crypt_bell_mouth",
                                   "glow": b + "crypt_bell_mouth_glow"},
                      "elements": els}
    # the Bell Crypt's set piece (38.8 "Sets"): a little stone belfry on a plinth, its bell hung under the cap, the mouth lit
    tower = [_box([1, 0, 1], [15, 3, 15], "#stone"),
             _box([1.5, 3, 1.5], [3.5, 13, 3.5], "#post"), _box([12.5, 3, 1.5], [14.5, 13, 3.5], "#post"),
             _box([1.5, 3, 12.5], [3.5, 13, 14.5], "#post"), _box([12.5, 3, 12.5], [14.5, 13, 14.5], "#post"),
             _box([1, 13, 1], [15, 14.5, 15], "#stone"), _box([3, 14.5, 3], [13, 16, 13], "#roof"),
             _box([4.5, 5.5, 4.5], [11.5, 7, 11.5], "#bronze"), _box([5, 7, 5], [11, 9.5, 11], "#bronze"),
             _box([5.75, 9.5, 5.75], [10.25, 11.5, 10.25], "#bronze"), _box([6.75, 11.5, 6.75], [9.25, 12.5, 9.25], "#bronze"),
             _box([7.5, 12.5, 7.5], [8.5, 13, 8.5], "#bronze"), _box([7.5, 4.25, 7.5], [8.5, 5.5, 8.5], "#bronze"),
             {"from": [5, 5.45, 5], "to": [11, 5.45, 11], "faces": {"down": {"texture": "#glow"}}, "light_emission": 15}]
    m["bell_tower"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                       "textures": {"particle": b + "crypt_limestone", "stone": b + "crypt_limestone_worn", "post": b + "crypt_tracery",
                                    "roof": b + "crypt_patina", "bronze": b + "crypt_bell_prop", "glow": b + "crypt_bell_mouth_glow"},
                       "elements": tower}
    return m


def blockstates():
    b = "awaken:block/"
    s = {}
    for name in ("crypt_limestone", "crypt_limestone_worn", "crypt_limestone_crumbling", "crypt_tracery", "crypt_flagstone",
                 "crypt_flagstone_wet", "crypt_patina", "crypt_moon_glass", "crypt_bell_rope", "crypt_lily_candle", "bell_prop", "bell_tower"):
        s[name] = {"variants": {"": {"model": b + name}}}
    s["crypt_candles"] = {"variants": {f"candles={n},lit={'true' if lit else 'false'}": {"model": b + f"crypt_candles_{n}" + ("_lit" if lit else "")}
                                       for n in range(1, 6) for lit in (False, True)}}
    s["crypt_rose_glass"] = {"variants": {f"hue={k}": {"model": b + f"crypt_rose_glass_{k}"} for k in range(5)}}
    for name in ("crypt_niche", "crypt_vestment", "crypt_handbell"):
        s[name] = {"variants": {f"facing={f}": dict({"model": b + name}, **({"y": r} if r else {}))
                                for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
    return s


# the theme's set piece (38.8 "Sets"): placed at home, kept when broken
SET_BLOCKS = ("bell_tower",)

ITEM_MODEL = {"crypt_candles": "crypt_candles_3_lit", "crypt_rose_glass": "crypt_rose_glass_0"}


def content():
    for name, data in models().items():
        _write(os.path.join(ASSETS, f"models/block/{name}.json"), data)
    for name, data in blockstates().items():
        _write(os.path.join(ASSETS, f"blockstates/{name}.json"), data)
    for name in BLOCKS:
        _write(os.path.join(ASSETS, f"items/{name}.json"),
               {"model": {"type": "minecraft:model", "model": "awaken:block/" + ITEM_MODEL.get(name, name)}})
        _write(os.path.join(RES, f"data/awaken/loot_table/blocks/{name}.json"), {
            "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    _merge(os.path.join(MERGE, "gates/assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v[0] for k, v in BLOCKS.items()})
    _merge(os.path.join(MERGE, "gates/assets/awaken/lang/en_us.json"), {"block.awaken.bell_prop": "Swinging Bell",
                                                                        "block.awaken.bell_tower": "The Bell Tower"})
    for name in SET_BLOCKS:
        _write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": "awaken:block/" + name}})
        _write(os.path.join(RES, f"data/awaken/loot_table/blocks/{name}.json"), {
            "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    _merge(os.path.join(MERGE, "gates/data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [f"awaken:{k}" for k in SET_BLOCKS]})
    _merge(os.path.join(MERGE, "gates/data/minecraft/tags/block/mineable/pickaxe.json"),
           {"values": [f"awaken:{k}" for k, v in BLOCKS.items() if v[1] == "pickaxe"]})
    _merge(os.path.join(MERGE, "gates/data/minecraft/tags/block/climbable.json"), {"values": ["awaken:crypt_bell_rope"]})
    for name, (frames, _) in LIFE.items():
        _write(os.path.join(ASSETS, f"particles/{name}.json"), {"textures": [f"awaken:{name}_{i}" for i in range(len(frames))]})
    print("gate_worlds_bell: content written for", len(BLOCKS), "blocks")


def contact_sheet(path):
    from PIL import Image, ImageDraw
    items = []
    for name, c in textures().items():
        items.append((name, (c[0] if isinstance(c, list) else c).image()))
    S, cols = 96, 8
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (S + 16), rows * (S + 30) + 10), (40, 42, 50, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(items):
        x, y = (i % cols) * (S + 16) + 8, (i // cols) * (S + 30) + 8
        big = im.resize((S, S), Image.NEAREST)
        sheet.paste(big, (x, y), big)
        d.text((x, y + S + 2), name[6:][:16], fill=(220, 220, 230, 255))
    sheet.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        contact_sheet(sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_bell.png")
    else:
        print(__doc__)
