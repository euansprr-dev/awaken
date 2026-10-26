"""HEARTH module textures: the anchor, forge, meditation mat, role pads, Spire board, monolith, race gates, targets,
the trophy, townsfolk skins, the training ghost and hearth particles.

The Hearth is home: warm amber lantern light on dark slate, with signal blue for anything precursor."""
import hashlib
import math

from pixelart import *  # noqa: F401,F403
from pixelart import _outline

# Small local ramps (dark -> light) for things the shared palette has no colour for.
LOCAL = {
    "go":         ["#0f2a1a", "#17442a", "#22663e", "#349258", "#5fd08f", "#a8f0c6"],  # start-gate green
    "skin_tan":   ["#4a2c1c", "#6e432b", "#8e5a3a", "#ad7450", "#c48d66", "#d9a983"],
    "skin_light": ["#6e4a3c", "#9c6f5c", "#c4917a", "#deb096", "#ecc5aa", "#f6dcc6"],
    "skin_pale":  ["#7a6862", "#a08b84", "#c4aea5", "#dcc8be", "#ecdcd2", "#f8eee8"],
    "skin_brown": ["#33190f", "#4f2b1b", "#6b3d27", "#865133", "#a06842", "#b98056"],
    "hair_red":   ["#3a1009", "#5e1b0e", "#8a2a13", "#b33e1b", "#d65b2a", "#ef8a4c"],
    "hair_black": ["#08080c", "#111017", "#1b1924", "#272433", "#383447"],
    "book":       ["#2a120b", "#461f12", "#66301b", "#874528", "#a85e36"],
}

GHOST_EYE = "#1d5591"


def col(name, i):
    rp = LOCAL.get(name) or RAMPS[name]
    return hex_rgba(rp[max(0, min(len(rp) - 1, i))])


def h01(*parts) -> float:
    return int(hashlib.sha256("|".join(map(str, parts)).encode()).hexdigest()[:8], 16) / 4294967296.0


def sprite(c: Canvas, x0, y0, rows, legend):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in legend:
                c.set(x0 + i, y0 + j, legend[ch])


def plate(ramp_name, seed, lo=2, hi=3):
    """A calm bevelled plate: low-contrast noise, light top-left edge, dark bottom-right edge."""
    return stone(ramp_name, seed=seed, scale=2, contrast=0.6, lo=lo, hi=hi, bevel=True)


def wood_grain(seed, vertical=False, lo=1, hi=4, w=16, h=16) -> Canvas:
    rp = ramp("wood")
    rng = rng_for("grain", seed)
    lines = (w if vertical else h)
    base = [rng.uniform(0.25, 0.75) for _ in range(lines)]
    phase = [rng.uniform(0, math.tau) for _ in range(lines)]
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            ln, along = (x, y) if vertical else (y, x)
            v = base[ln] + 0.22 * math.sin(along * 0.55 + phase[ln]) + (rng.random() - 0.5) * 0.12
            c.set(x, y, rp[lo + quantize(max(0.0, min(0.999, v)), hi - lo + 1)])
    return c


def ring_blend(c: Canvas, r0, r1, colour, a, cx=7.5, cy=7.5):
    for y in range(c.h):
        for x in range(c.w):
            if r0 <= math.hypot(x - cx, y - cy) < r1:
                c.blend(x, y, colour, a)


# ---------------------------------------------------------------------------------------------------------------------
# Hearth anchor


def anchor_side() -> Canvas:
    ob, am, br = ramp("obsidian"), ramp("amber"), ramp("brass")
    c = stone("obsidian", seed="hearth_anchor_side", bevel=True)
    # inner panel line one pixel in from the bevel
    for i in range(2, 14):
        c.set(i, 1, ob[0]); c.set(i, 14, ob[3])
        c.set(1, i, ob[0]); c.set(14, i, ob[3])
    win = set()
    for y in range(2, 14):
        for x in range(6, 10):
            if (y in (2, 13)) and x in (6, 9):
                continue
            win.add((x, y))
    # warm spill on the frame, then a deep rim around the window
    for y in range(16):
        for x in range(16):
            if (x, y) in win:
                continue
            dmin = min(math.hypot(x - wx, y - wy) for wx, wy in win)
            if dmin < 1.5:
                c.set(x, y, ob[0])
            elif dmin < 2.6 and 1 < x < 14 and 1 < y < 14:
                c.blend(x, y, am[2], 0.24 * (1 - abs(y - 7.5) / 8))
    for (x, y) in win:
        hx = abs(x - 7.5)
        vy = abs(y - 7.5) / 6.0
        v = 1.0 - (hx - 0.5) * 0.28 - vy * vy * 0.6
        c.set(x, y, am[2 + quantize(max(0.0, min(0.999, v)), 4)])
    # brass fittings holding the core top and bottom
    for x in (7, 8):
        c.set(x, 1, br[4]); c.set(x, 14, br[2])
    return c


def anchor_top() -> Canvas:
    ob, am, br = ramp("obsidian"), ramp("amber"), ramp("brass")
    c = stone("obsidian", seed="hearth_anchor_top", bevel=True)
    for i in range(1, 15):
        c.set(i, 1, ob[3]); c.set(i, 14, ob[0])
        c.set(1, i, ob[3]); c.set(14, i, ob[0])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ang = math.atan2(y - 7.5, x - 7.5)
            if d <= 1.2:
                c.set(x, y, am[5])
            elif d <= 2.3:
                c.set(x, y, am[5] if (x + y) < 15 else am[4])
            elif d <= 3.3:
                c.set(x, y, am[4] if (x + y) < 15 else am[3])
            elif d <= 4.1:
                c.set(x, y, ob[0])
            elif d <= 5.3:
                k = (ang / (math.tau / 8)) % 1.0
                if k < 0.22 or k > 0.92:
                    c.set(x, y, am[3])
                else:
                    c.blend(x, y, am[1], 0.45)
            elif d <= 6.2:
                c.blend(x, y, am[2], 0.18)
    # tiny brass studs at the four corners of the rim
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, br[4] if x + y < 15 else br[2])
    return c


def anchor_bottom() -> Canvas:
    ob = ramp("obsidian")
    c = stone("obsidian", seed="hearth_anchor_bottom", bevel=True)
    for i in range(2, 14):
        c.set(i, 2, ob[0]); c.set(i, 13, ob[3])
        c.set(2, i, ob[0]); c.set(13, i, ob[3])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Meditation mat


def meditation_mat() -> Canvas:
    sp, dr, am = ramp("spirit"), ramp("dream"), ramp("amber")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if ((x // 2) + (y // 2)) % 2 == 0:  # weft over: two horizontal threads
                c.set(x, y, sp[2] if y % 2 == 0 else sp[1])
            else:  # warp over: two vertical threads
                c.set(x, y, dr[2] if x % 2 == 0 else dr[1])
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, am[2] if (x, y)[1] != 15 and (x, y)[0] != 15 else am[1])
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= x <= 14 and 1 <= y <= 14:
                t = (x + y) % 3
                c.set(x, y, am[4] if t == 0 else am[3] if t == 1 else am[2])
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            if 2 <= x <= 13 and 2 <= y <= 13:
                c.set(x, y, dr[0])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, am[5])
    # calm central diamond
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 1.0:
                c.set(x, y, am[4])
            elif d <= 2.0:
                c.set(x, y, dr[0])
            elif d <= 3.0:
                c.set(x, y, am[2] if (x + y) % 2 else am[3])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Forge


def forge_front() -> Canvas:
    st, em = ramp("stone"), ramp("ember")
    c = bricks("stone", seed="forge_front")
    hole = set()
    for y in range(7, 15):
        x0, x1 = (6, 9) if y == 7 else (5, 10) if y == 8 else (4, 11)
        for x in range(x0, x1 + 1):
            hole.add((x, y))
    rng = rng_for("forge_front_embers")
    # warm light spilling on the brick face
    for y in range(16):
        for x in range(16):
            if (x, y) in hole:
                continue
            d = min(math.hypot(x - hx, y - hy) for hx, hy in hole)
            if d < 1.5:
                c.set(x, y, st[4] if (y < 9 or x < 7) else st[3])
            elif d < 3.5:
                c.blend(x, y, em[3], 0.32 * (3.5 - d) / 2)
    c.set(7, 6, st[5]); c.set(8, 6, st[4])  # keystone
    for x in range(3, 13):
        c.set(x, 15, st[2])  # hearth sill
    for x in range(4, 12):
        c.blend(x, 15, em[3], 0.35)
    for (x, y) in hole:
        t = (y - 7) / 7.0
        centre = 1 - abs(x - 7.5) / 4.5
        v = t * t * 0.85 + centre * 0.25 + (rng.random() - 0.5) * 0.25
        c.set(x, y, em[quantize(max(0.0, min(0.999, v)), 6)])
    for x in range(4, 12):  # the coal bed
        c.set(x, 14, em[5] if rng.random() < 0.45 else em[4])
        if rng.random() < 0.5:
            c.set(x, 13, em[4])
    for x, y in ((6, 12), (9, 11), (7, 10)):
        c.set(x, y, em[4])
    return c


def forge_side() -> Canvas:
    st, br = ramp("stone"), ramp("brass")
    c = bricks("stone", seed="forge_side")
    for x in range(16):
        c.set(x, 6, br[4]); c.set(x, 7, br[3]); c.set(x, 8, br[1])
        c.blend(x, 9, st[0], 0.35)
    for x in (2, 7, 12):
        c.set(x, 7, br[5]); c.set(x + 1, 7, br[2])
    return c


def forge_top() -> Canvas:
    st, ob, em = ramp("stone"), ramp("obsidian"), ramp("ember")
    c = bricks("stone", seed="forge_top")
    for y in range(4, 12):
        for x in range(4, 12):
            if x in (4, 11) or y in (4, 11):
                c.set(x, y, st[4] if (x == 4 or y == 4) else st[2])
            else:
                c.set(x, y, ob[0])
    for i in range(5, 11):
        c.set(i, 5, hex_rgba("#050409")); c.set(5, i, hex_rgba("#050409"))
        c.set(i, 10, st[1]); c.set(10, i, st[1])
    c.set(5, 10, ob[1]); c.set(10, 5, ob[1])
    for x, y in ((7, 7), (8, 8)):
        c.set(x, y, em[1])
    c.set(8, 7, em[0]); c.set(7, 8, em[0])
    return c


def trophy_mount() -> Canvas:
    wd, br = ramp("wood"), ramp("brass")
    c = wood_grain("trophy_mount", lo=2, hi=4)
    frame_border(c, "brass")
    for i in range(1, 15):
        c.set(i, 1, wd[5]); c.set(1, i, wd[5])
        c.set(i, 14, wd[0]); c.set(14, i, wd[0])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, br[5] if x + y < 15 else br[2])
    # raised oval medallion the trophy hangs on
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 4.6, (y - 7.5) / 5.2)
            if d <= 1.0:
                e = math.hypot((x - 7.5) / 3.6, (y - 7.5) / 4.2)
                if e > 1.0:
                    c.set(x, y, wd[5] if (x + y) < 15 else wd[1])
                else:
                    c.set(x, y, wd[4] if c.get(x, y)[:3] == wd[4][:3] else wd[3])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Role pads


def role_pad_side() -> Canvas:
    sl, sp = ramp("slate"), ramp("spirit")
    c = stone("slate", seed="role_pad_side", scale=2, contrast=0.5, lo=2, hi=3)
    for x in range(16):
        c.set(x, 0, sl[4]); c.set(x, 15, sl[1])
        c.blend(x, 7, sp[2], 0.35); c.blend(x, 8, sp[2], 0.35)
    return c


def shaded_icon(c, x0, y0, mask, rp, outline=OUTLINE, hi=5, mid=4, lo_=3, dark=2):
    """Fill a mask with a ramp, light from top-left, and outline it."""
    pts = {(i, j) for j, row in enumerate(mask) for i, ch in enumerate(row) if ch != "."}
    w = max(i for i, _ in pts) + 1
    h = max(j for _, j in pts) + 1
    for (i, j) in pts:
        top = (i, j - 1) not in pts
        left = (i - 1, j) not in pts
        bot = (i, j + 1) not in pts
        right = (i + 1, j) not in pts
        if top or left:
            k = hi
        elif bot or right:
            k = dark
        else:
            k = mid if (i / w + j / h) < 1.0 else lo_
        c.set(x0 + i, y0 + j, rp[k])
    oc = hex_rgba(outline) if isinstance(outline, str) else outline
    for (i, j) in pts:
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + di, j + dj) not in pts:
                c.set(x0 + i + di, y0 + j + dj, oc)
    return c


def role_pad(name, icon) -> Canvas:
    sp = ramp("spirit")
    c = plate("slate", "role_pad_" + name)
    ring_blend(c, 5.7, 6.7, sp[3], 0.42)
    ring_blend(c, 6.7, 7.2, sp[1], 0.25)
    icon(c)
    return c


def icon_shield(c):
    cr, bn = ramp("crimson"), ramp("bone")
    shaded_icon(c, 4, 4, ["########",
                          "########",
                          "########",
                          "########",
                          "########",
                          ".######.",
                          "..####..",
                          "...##..."], cr)
    c.set(7, 6, bn[5]); c.set(8, 6, bn[4]); c.set(7, 7, bn[4]); c.set(8, 7, bn[2])


def icon_crate(c):
    br, am = ramp("brass"), ramp("amber")
    o = hex_rgba(OUTLINE)
    sprite(c, 4, 4, ["OOOOOOOO",
                     "O555544O",
                     "O444433O",
                     "OddddddO",
                     "O333322O",
                     "O333322O",
                     "O222211O",
                     "OOOOOOOO"],
           {"O": o, "5": br[5], "4": br[4], "3": br[3], "2": br[2], "1": br[1], "d": br[0]})
    for y in range(5, 11):
        c.set(5, y, br[5] if y < 7 else br[4]); c.set(10, y, br[3] if y < 7 else br[2])
    c.set(5, 7, br[0]); c.set(10, 7, br[0])
    c.set(7, 7, am[5]); c.set(8, 7, am[4]); c.set(7, 8, am[3]); c.set(8, 8, br[0])


def icon_wheat(c):
    mo, lf = ramp("moss"), ramp("leaf")
    o = hex_rgba(OUTLINE)
    rows = ["...54...",
            "...43...",
            "54.43.54",
            "43.33.43",
            "33.s..33",
            ".s.s..s.",
            "..ss.s..",
            "...tt...",
            "...ss...",
            "...s...."]
    pts = {(i, j) for j, r in enumerate(rows) for i, ch in enumerate(r) if ch != "."}
    for (i, j) in pts:
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + di, j + dj) not in pts:
                c.set(4 + i + di, 3 + j + dj, o)
    sprite(c, 4, 3, rows, {"5": mo[5], "4": lf[4], "3": lf[3], "s": mo[4], "t": ramp("brass")[4]})


def icon_pick(c):
    st, am = ramp("stone"), ramp("amber")
    o = hex_rgba(OUTLINE)
    rows = [".OOOOO...",
            "O54445OO.",
            ".OOO3445O",
            "...Oa335O",
            "..Oab.O4O",
            ".Oab.O4O.",
            "Oab.O.OO.",
            "Ob.O.....",
            ".O......."]
    rows = [r.replace(".", " ") for r in rows]
    sprite(c, 3, 3, rows, {"O": o, "5": st[5], "4": st[4], "3": st[2], "a": am[4], "b": am[2]})


def icon_target(c):
    sg = ramp("signal")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            lit = (x + y) < 15
            if d <= 1.0:
                c.set(x, y, sg[5])
            elif d <= 1.9:
                c.set(x, y, sg[0])
            elif d <= 2.9:
                c.set(x, y, sg[4] if lit else sg[3])
            elif d <= 3.6:
                c.set(x, y, sg[0])
            elif d <= 4.6:
                c.set(x, y, sg[3] if lit else sg[2])
            elif d <= 5.2:
                c.blend(x, y, sg[0], 0.6)


# ---------------------------------------------------------------------------------------------------------------------
# Spire board, monolith


def spire_board() -> Canvas:
    sl, sg, br = ramp("slate"), ramp("signal"), ramp("brass")
    c = stone("slate", seed="spire_board", scale=2, contrast=0.5, lo=0, hi=1)
    for y in (4, 7, 10, 13):
        for x in range(3, 13):
            c.blend(x, y, sg[2], 0.32)
        c.blend(2, y, sg[3], 0.45)
    frame_border(c, "brass")
    for i in range(1, 15):
        c.set(i, 1, sl[0]); c.set(1, i, sl[0])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, br[5] if (x, y) == (0, 0) else br[2])
    return c


def spire_board_post() -> Canvas:
    br, wd = ramp("brass"), ramp("wood")
    c = wood_grain("spire_board_post", vertical=True, lo=1, hi=4)
    for y0 in (3, 11):
        for x in range(16):
            c.set(x, y0, br[4]); c.set(x, y0 + 1, br[2])
            c.blend(x, y0 + 2, wd[0], 0.4)
        for x in (3, 11):
            c.set(x, y0, br[5]); c.set(x, y0 + 1, br[3])
    return c


def monolith_side() -> Canvas:
    ob, sg = ramp("obsidian"), ramp("signal")
    c = stone("obsidian", seed="monolith_side")
    for y in range(16):
        c.set(0, y, ob[3]); c.set(15, y, ob[0])
    circuits(c, "signal", seed="monolith_side", lines=2)
    for x, nodes in ((4, (5, 12)), (11, (2, 9))):
        for y in range(16):
            c.set(x, y, sg[3])
            for ax in (x - 1, x + 1):
                if c.get(ax, y)[:3] != sg[3][:3]:
                    c.blend(ax, y, sg[2], 0.3)
        for ny in nodes:
            c.set(x, ny, sg[5])
            c.blend(x - 1, ny, sg[3], 0.5); c.blend(x + 1, ny, sg[3], 0.5)
    for x in range(5, 11):  # one cross trace joining the spines
        c.set(x, 9 if x > 7 else 5, sg[2])
    for y in range(5, 10):
        c.set(7, y, sg[2])
    return c


def monolith_top() -> Canvas:
    c = stone("obsidian", seed="monolith_top", bevel=True)
    ring_blend(c, 4.2, 5.0, ramp("signal")[1], 0.5)
    return glyph(c, "signal", seed="monolith_top", size=5)


# ---------------------------------------------------------------------------------------------------------------------
# Race gates, checkpoint ring, target dummy


def start_gate() -> Canvas:
    g = [hex_rgba(h) for h in LOCAL["go"]]
    c = plate("slate", "start_gate")
    for i in (2, 13):
        for j in range(2, 14):
            c.set(i, j, ramp("slate")[1])
    for apex, (top, bot) in ((2, (5, 4)), (7, (4, 3)), (12, (3, 2))):
        for k in range(5):
            y = apex + k
            for x in (7 - k, 8 + k):
                if y < 15:
                    c.set(x, y, g[top] if x <= 7 else g[top - 1])
                if y + 1 < 15:
                    c.set(x, y + 1, g[bot - 1])
    return c


def finish_gate() -> Canvas:
    am, sl = ramp("amber"), ramp("slate")
    rng = rng_for("finish_gate")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            lx, ly = x % 4, y % 4
            if ((x // 4) + (y // 4)) % 2 == 0:
                k = 4 if (lx == 0 or ly == 0) else 2 if (lx == 3 or ly == 3) else 3
                c.set(x, y, am[k])
            else:
                k = 2 if (lx == 0 or ly == 0) else 0 if (lx == 3 or ly == 3) else 1
                c.set(x, y, sl[k] if rng.random() > 0.1 else sl[max(0, k - 1)])
    frame_border(c, "brass")
    return c


def checkpoint_ring() -> Canvas:
    sg = ramp("signal")
    c = Canvas()
    for y in range(16):
        dist = abs(y - 7.5)
        k = 5 if dist < 1 else 4 if dist < 2.5 else 3 if dist < 4.5 else 2 if dist < 6.5 else 1
        for x in range(16):
            c.set(x, y, sg[k])
    for x in range(0, 16, 4):  # energy pulses travelling along the band
        c.set(x, 5, sg[5]); c.set(x + 2, 10, sg[5])
        c.set(x + 1, 5, sg[4]); c.set(x + 3, 10, sg[4])
    return c


def straw(seed) -> Canvas:
    sd = ramp("sand")
    rng = rng_for("straw", seed)
    c = Canvas()
    cols = [rng.choice((2, 3, 3, 4, 4, 5)) for _ in range(16)]
    for x in range(16):
        for y in range(16):
            k = cols[x]
            r = rng.random()
            if r < 0.12:
                k -= 1
            elif r > 0.94:
                k = 1
            c.set(x, y, sd[max(0, min(5, k))])
    return c


def target_dummy() -> Canvas:
    cr, bn, wd = ramp("crimson"), ramp("bone"), ramp("wood")
    c = straw("target_dummy")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            lit = ((7.5 - x) + (7.5 - y)) / 10.6
            sh = 1 if lit > 0.35 else (-1 if lit < -0.45 else 0)
            if d <= 1.6:
                c.set(x, y, cr[4 + max(0, sh)])
            elif d <= 3.0:
                c.set(x, y, bn[4 + sh])
            elif d <= 4.4:
                c.set(x, y, cr[3 + sh])
            elif d <= 5.8:
                c.set(x, y, bn[4 + sh])
            elif d <= 7.0:
                c.set(x, y, cr[3 + sh])
            elif d <= 7.8:
                c.set(x, y, wd[1])
    return c


def target_dummy_side() -> Canvas:
    wd = ramp("wood")
    c = straw("target_dummy_side")
    for y0 in (3, 11):
        for x in range(16):
            twist = (x + (0 if y0 == 3 else 1)) % 3
            c.set(x, y0, wd[4] if twist == 0 else wd[3])
            c.set(x, y0 + 1, wd[2] if twist != 2 else wd[1])
            c.blend(x, y0 + 2, wd[0], 0.35)
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Item


def trophy_item() -> Canvas:
    br, wd, bn, cr = ramp("brass"), ramp("wood"), ramp("bone"), ramp("crimson")
    c = Canvas()
    plaque = set()
    for y in range(1, 15):
        x0, x1 = {11: (3, 12), 12: (4, 11), 13: (5, 10), 14: (6, 9)}.get(y, (2, 13))
        for x in range(x0, x1 + 1):
            plaque.add((x, y))
    grain = wood_grain("trophy_item", lo=1, hi=3)
    for (x, y) in plaque:
        edge = any((x + dx, y + dy) not in plaque for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            c.set(x, y, br[5] if (x + y) < 13 else br[4] if (x + y) < 17 else br[2])
        else:
            c.set(x, y, grain.get(x, y))
    skull = set()
    for y in range(2, 13):
        for x in range(3, 13):
            if math.hypot((x - 7.5) / 3.7, (y - 6.0) / 3.6) <= 1.0 and y <= 9:
                skull.add((x, y))
            elif 9 < y <= 11 and 5 <= x <= 10:
                skull.add((x, y))
    for (x, y) in skull:
        c.set(x, y, bn[2 + quantize(max(0.0, min(0.999, 1.05 - (x + y) / 22)), 4)])
    for (x, y) in skull:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in skull and (x + dx, y + dy) in plaque:
                c.set(x + dx, y + dy, hex_rgba(OUTLINE))
    for x, y in ((5, 6), (6, 6), (5, 7), (6, 7), (9, 6), (10, 6), (9, 7), (10, 7)):
        c.set(x, y, bn[0])
    c.set(5, 6, cr[5]); c.set(10, 6, cr[5]); c.set(6, 7, cr[3]); c.set(9, 7, cr[3])
    c.set(7, 8, bn[0]); c.set(8, 8, bn[0])
    for x in (6, 8, 10):
        c.set(x, 10, bn[1])
    c.set(5, 3, bn[5]); c.set(6, 3, bn[5])
    return _outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# Particles


def echo_wisp(frame) -> Canvas:
    radius = (3.7, 3.1, 2.4, 1.7)[frame]
    fade = (1.0, 0.82, 0.62, 0.42)[frame]
    ab, sp, dr = ramp("abyss"), ramp("spirit"), ramp("dream")
    c = Canvas(8, 8)
    cx, cy = 3.5, 4.0 + frame * 0.15
    for y in range(8):
        for x in range(8):
            dy = (y - cy)
            # teardrop: narrower above the centre so the wisp trails upward
            sx = 1.0 + max(0.0, -dy) * 0.18
            d = math.hypot((x - cx) * sx, dy * 0.9) / radius
            if d >= 1:
                continue
            if d < 0.55:
                base = ab[2] if d < 0.3 else ab[3]
                a = 0.95
            elif d < 0.8:
                base = sp[2] if (x + y) % 2 else dr[2]
                a = 0.85 * (1 - (d - 0.55) / 0.45)
            else:
                base = sp[3]
                a = 0.55 * (1 - d) / 0.2
            r, g, b, _ = base
            c.set(x, y, (r, g, b, int(255 * a * fade)))
    return c


def hearth_ember(frame) -> Canvas:
    radius = (2.7, 2.0, 1.3)[frame]
    am, em = ramp("amber"), ramp("ember")
    c = Canvas(8, 8)
    m = 3.5
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - m, y - m) / radius
            if d >= 1:
                continue
            a = (1 - d) ** 0.8
            base = am[5] if d < 0.35 else am[4] if d < 0.7 else em[4]
            r, g, b, _ = base
            c.set(x, y, (r, g, b, int(255 * min(1.0, a * 1.3))))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# The carried flame (W1): the Hearth Ember's lantern, the Hearthfire, and their particles.
# Bell-gold coal light against black iron and soot-dark river stone: warm, small, hand-made.

BELL = ["#3b1a06", "#7a3a0c", "#c2661a", "#f0a23a", "#ffd27a", "#fff1c6"]  # the coal: ember red into bell-gold white
IRON = ["#0e0d10", "#1a181d", "#26232b", "#35313b", "#4a4552", "#655f6f"]


def bell(i):
    return hex_rgba(BELL[max(0, min(len(BELL) - 1, i))])


def iron(i):
    return hex_rgba(IRON[max(0, min(len(IRON) - 1, i))])


def ember_lantern() -> Canvas:
    """The Ember's lantern, one 16x16 atlas for its model: the cage's side (u0-6 v0-7), its cap (u6-12 v0-6), plain
    iron for small faces (u12-16 v0-4), the bare coal (u0-4 v8-12) and the glow between the bars (u4-8 v8-12)."""
    c = Canvas(16, 16)
    # the cage side: an iron frame round a pane of glass lit gold from the coal low inside it (brightest at the foot,
    # a white-hot spot where the coal sits), the way a lantern reads at night
    for y in range(7):
        for x in range(6):
            frame = x == 0 or x == 5 or y == 0 or y == 6
            if frame:
                c.set(x, y, iron(3 if y == 0 else 2 if x == 0 else 1))
            else:
                heat = (y - 1) / 4  # 0 at the top of the pane, 1 at its foot
                c.set(x, y, bell(3 + round(heat * 2)))
    c.set(2, 5, bell(5)); c.set(3, 5, bell(5)); c.set(2, 4, bell(5))
    c.set(1, 1, bell(2)); c.set(4, 1, bell(2))  # the pane's top corners in the frame's shadow
    # the cap: a riveted iron plate, a vent ring in the middle with the glow coming through
    for y in range(6):
        for x in range(6, 12):
            lx, ly = x - 6, y
            edge = lx in (0, 5) or ly in (0, 5)
            c.set(x, y, iron(3 if edge and (lx + ly) % 2 == 0 else 2 if edge else 1))
    for x, y in ((8, 2), (9, 2), (8, 3), (9, 3)):
        c.set(x, y, bell(3))
    for x, y in ((7, 1), (10, 1), (7, 4), (10, 4)):
        c.set(x, y, iron(4))
    # plain iron with a worn highlight
    for y in range(4):
        for x in range(12, 16):
            c.set(x, y, iron(2 if (x + y) % 3 else 3))
    # the bare coal: cracked, bell-gold at its heart
    rng = rng_for("ember_coal")
    for y in range(8, 12):
        for x in range(0, 4):
            d = math.hypot(x - 1.5, y - 9.5)
            c.set(x, y, bell(5 if d < 0.8 else 4 if d < 1.5 else 3 if rng.random() < 0.7 else 2))
    # the glow between the bars (inner faces)
    for y in range(8, 12):
        for x in range(4, 8):
            c.set(x, y, bell(4 if (x + y) % 2 else 3))
    return c


def hearthfire_stones() -> Canvas:
    """River stones laid in a ring: rounded grey stones, soot in the joints, their tops warmed by the fire."""
    st = ramp("stone")
    rng = rng_for("hearthfire_stones")
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, iron(1))
    # pack rounded stones of a few sizes into the square
    stones = []
    for _ in range(140):
        cx, cy = rng.uniform(0, 16), rng.uniform(0, 16)
        r = rng.uniform(1.6, 3.2)
        if all(math.hypot(cx - sx, cy - sy) > (r + sr) * 0.82 for sx, sy, sr in stones):
            stones.append((cx, cy, r))
    for sx, sy, r in stones:
        tone = rng.choice((2, 3, 3, 4))
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - sx, y + 0.5 - sy) / r
                if d >= 1:
                    continue
                i = tone + (1 if d < 0.45 and y + 0.5 < sy else 0) - (1 if d > 0.8 or y + 0.5 > sy + r * 0.5 else 0)
                col = st[max(0, min(5, i))]
                # the fire's warmth on the upper faces
                if y < 7 and d < 0.85:
                    col = tuple(round(col[k] + (bell(3)[k] - col[k]) * (0.18 - y * 0.02)) for k in range(3)) + (255,)
                c.set(x, y, col)
    return c


def hearthfire_ash() -> Canvas:
    """The fire's bed: dark ash with a few embers still alive in it."""
    rng = rng_for("hearthfire_ash")
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            c.set(x, y, iron(1 + quantize(v, 3)))
            if rng.random() < 0.05:
                c.set(x, y, bell(2 + rng.randrange(2)))
    return c


def hearthfire_coal() -> Canvas:
    """The Ember's coal in the ring: a black crust split into plates, bell-gold heat showing through the cracks, the
    plates themselves faintly red where they're thinnest (the middle)."""
    rng = rng_for("hearthfire_coal")
    seeds = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(9)]
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            ds = sorted(math.hypot(px - sx, py - sy) for sx, sy in seeds)
            edge = ds[1] - ds[0]
            centre = math.hypot(px - 8, py - 8) / 8
            if edge < 0.55:
                c.set(x, y, bell(5 if centre < 0.45 else 4))
            elif edge < 1.1:
                c.set(x, y, bell(3 if centre < 0.6 else 2))
            else:
                c.set(x, y, bell(1) if centre < 0.35 and rng.random() < 0.5 else iron(1 + (1 if rng.random() < 0.3 else 0)))
    return c


def hearthfire_flame(frame, frames=8) -> Canvas:
    """One frame of the Hearthfire's living flame, drawn for crossed planes: three tongues that sway and lick up,
    bell-gold white at the root, orange, then red at the tips, fading out above."""
    c = Canvas(16, 16)
    t = frame / frames * math.tau
    tongues = ((5.0, 0.0, 9.5), (8.5, 2.1, 12.5), (11.5, 4.2, 8.0))
    for y in range(16):
        h = 15 - y  # height above the root
        for x in range(16):
            best = 0.0
            for bx, ph, top in tongues:
                height = top + 1.6 * math.sin(t * 2 + ph)
                if h > height:
                    continue
                sway = 1.2 * math.sin(t + ph + h * 0.35) * (h / max(1.0, height))
                width = 2.6 * (1 - (h / max(1.0, height)) ** 1.6) + 0.4
                d = abs(x + 0.5 - (bx + sway)) / width
                if d < 1:
                    best = max(best, (1 - d) * (1 - 0.55 * h / max(1.0, height)))
            if best <= 0.02:
                continue
            i = 5 if best > 0.72 else 4 if best > 0.5 else 3 if best > 0.32 else 2
            r, g, b, _ = bell(i)
            c.set(x, y, (r, g, b, int(255 * min(1.0, best * 1.6))))
    return c


def warmth_mote(frame) -> Canvas:
    """The warmth on the ground: a soft round glow, near white (tinted gold in game), a little different each frame."""
    c = Canvas(16, 16)
    rng = rng_for("warmth", frame)
    r = 7.2 - frame * 0.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5 + rng.uniform(-0.15, 0.15), y - 7.5) / r
            if d >= 1:
                continue
            a = (1 - d) ** 1.8
            c.set(x, y, (255, 246, 226, int(255 * a)))
    return c


def flame_tongue(frame) -> Canvas:
    """A tongue of flame for the Hearthfire's particles: tall and bright at first, short and red as it dies."""
    c = Canvas(8, 8)
    life = frame / 5
    height = 7.5 - life * 4
    for y in range(8):
        h = 7 - y
        for x in range(8):
            if h > height:
                continue
            width = 2.4 * (1 - (h / height) ** 1.5) + 0.3
            d = abs(x + 0.5 - 4) / width
            if d >= 1:
                continue
            v = (1 - d) * (1 - 0.6 * h / height) * (1 - 0.5 * life)
            i = 5 if v > 0.6 else 4 if v > 0.4 else 3 if v > 0.25 else 2
            r, g, b, _ = bell(i)
            c.set(x, y, (r, g, b, int(255 * min(1.0, v * 1.8))))
    return c


def firefly(frame) -> Canvas:
    """A firefly: a white-hot point in a soft halo (tinted green-gold in game)."""
    c = Canvas(8, 8)
    r = (2.6, 1.9)[frame]
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5) / r
            if d >= 1:
                continue
            a = (1 - d) ** 1.4
            c.set(x, y, (255, 255, 240, int(255 * min(1.0, a * 1.4))))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# 64x64 player skins (classic wide arms)

PARTS = {
    # name: (u, v, w, h, d)
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4),
    "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4),
    "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4),
}
BASE_PARTS = ("head", "body", "rarm", "larm", "rleg", "lleg")


def _faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


class Face:
    """One pixel on one face of a skin box. fd = depth from the front edge on side faces (0 = next to the front)."""

    def __init__(self, part, face, x, y, w, h, d):
        self.part, self.face, self.x, self.y = part, face, x, y
        self.w, self.h, self.d = w, h, d
        self.side = part[0] if part[0] in "rl" else None
        self.fd = {"right": d - 1 - x, "left": x, "front": 0, "back": d}.get(face)

    @property
    def outer(self):
        return (self.side == "r" and self.face == "right") or (self.side == "l" and self.face == "left")

    @property
    def inner(self):
        return (self.side == "r" and self.face == "left") or (self.side == "l" and self.face == "right")

    @property
    def wall(self):
        return self.face in ("front", "back", "left", "right")

    def bx(self):
        """x across the whole body front (0..7) for a limb's front face; back faces likewise across the body back."""
        if self.face == "front":
            return self.x + (4 if self.side == "l" else 0)
        if self.face == "back":
            return self.x + (0 if self.side == "l" else 4)
        return None


def paint(c: Canvas, part, fn):
    u, v, w, h, d = PARTS[part]
    for face, (fx, fy, fw, fh) in _faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                px = fn(Face(part, face, x, y, w, h, d))
                if px is not None:
                    c.set(fx + x, fy + y, px)


def tex(name, idx, f, key, lo=0.05, hi=0.0, folds=0.25):
    """Cloth: a few vertical fold lines starting at varied heights, and rare darker specks."""
    n = h01(key, f.part, f.face, f.x, f.y)
    fold = False
    if f.face not in ("top", "bottom"):
        fc = h01(key, "fold", f.part, f.face, f.x)
        fold = fc < folds and f.y >= 2 + int(h01(key, "foldstart", f.part, f.face, f.x) * 7)
    if fold or n < lo:
        idx -= 1
    elif n > 1 - hi:
        idx += 1
    return col(name, idx)


def hair(name, base, f, key):
    n = h01(key, "strand", f.face, f.x if f.face != "top" else f.x + f.y)
    m = h01(key, "hairpx", f.part, f.face, f.x, f.y)
    idx = base + (1 if n > 0.72 else -1 if n < 0.18 else 0)
    if m < 0.1:
        idx -= 1
    return col(name, idx)


def head_map(f, rows, legend):
    v = legend[rows[f.y][f.x]]
    return v(f) if callable(v) else v


def standard_head(f, key, front, legend, hair_name, hair_base, skin, side_rows, back_rows, ears=True):
    if f.face == "front":
        return head_map(f, front, legend)
    if f.face == "top":
        return hair(hair_name, hair_base + (1 if f.x < 3 and f.y > 4 else 0), f, key)
    if f.face == "bottom":
        return col(skin, 1)
    if f.face == "back":
        return hair(hair_name, hair_base - (1 if f.y > back_rows - 2 else 0), f, key) if f.y < back_rows \
            else col(skin, 2)
    if f.y < side_rows[f.fd]:
        return hair(hair_name, hair_base, f, key)
    if ears and f.fd in (3, 4) and f.y in (4, 5):
        return col(skin, 1 if (f.fd == 4 and f.y == 5) else 2)
    return col(skin, 3 if f.fd < 2 else 2)


def finish_skin(c: Canvas, name: str) -> Canvas:
    for part in BASE_PARTS:
        u, v, w, h, d = PARTS[part]
        for face, (fx, fy, fw, fh) in _faces(u, v, w, h, d).items():
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    assert c.get(x, y)[3] == 255, f"{name}: {part}.{face} not opaque at {x},{y}"
    for y in range(64):
        for x in range(64):
            a = c.get(x, y)[3]
            assert a in (0, 255), f"{name}: partial alpha at {x},{y}"
    return c


def make_skin(name, fns) -> Canvas:
    c = Canvas(64, 64)
    for part, fn in fns.items():
        if fn is not None:
            paint(c, part, fn)
    return finish_skin(c, name)


EYE_WHITE = hex_rgba("#ece8e0")


# --- Oren, the blacksmith -------------------------------------------------------------------------------------------


def oren() -> Canvas:
    K, SK, HR = "oren", "skin_tan", "mirror"
    soot = hex_rgba("#2b2420")
    S = lambda i: col(SK, i)  # noqa: E731
    legend = {"h": col(HR, 2), "H": col(HR, 3), "b": col(HR, 1), "S": S(3), "L": S(4), "s": S(2), "n": S(1),
              "w": EYE_WHITE, "e": hex_rgba("#3a2414"), "m": hex_rgba("#3a1d16"), "g": col(HR, 2), "G": col(HR, 3),
              "x": hex_rgba("#6a4a36"), "B": col(HR, 4)}
    front = ["hHHhhHHh",
             "hSLLLLSh",
             "SxSSSSSs",
             "bbbSSbbb",
             "SweSSews",
             "gSSnnSsg",
             "gGgmmgGg",
             "gGBGGBGg"]

    def head(f):
        if f.face == "bottom":
            return col(HR, 2 if f.y >= 4 else 1) if f.y >= 3 else S(1)
        if f.wall and f.face != "front" and f.face != "back" and f.fd <= 2 and f.y >= 5:
            return col(HR, 2 + (f.y == 6))
        return standard_head(f, K, front, legend, HR, 2, SK, [2, 2, 2, 3, 3, 4, 5, 5], 6)

    def hat(f):  # a beard that stands proud of the face
        if f.face == "front":
            if f.y == 7 or (f.y == 6 and f.x in (0, 1, 6, 7)):
                return col(HR, 3 if f.x % 3 else 2)
        if f.face in ("left", "right") and f.fd <= 1 and f.y >= 6:
            return col(HR, 2)
        if f.face == "bottom" and f.y >= 5:
            return col(HR, 2 if (f.x + f.y) % 2 else 1)
        return None

    def apron_front(x, y):
        return (y >= 5 and 1 <= x <= 6) or (1 <= y < 5 and 2 <= x <= 5)

    def body(f):
        shirt = tex("slate", 2, f, K)
        if f.face == "top":
            return col("wood", 2) if f.x in (2, 5) else shirt
        if f.face == "bottom":
            return col("stone", 1)
        if f.face == "front":
            x, y = f.x, f.y
            if y == 0 and x in (3, 4):
                return S(2)
            if y == 0 and x in (2, 5):
                return col("wood", 2)
            if (x, y) in ((2, 1), (5, 1)):
                return col("brass", 4)
            if apron_front(x, y):
                if h01(K, "soot", x, y) < 0.09:
                    return soot
                if y == 8 and 2 <= x <= 5:
                    return col("wood", 1)  # pocket mouth
                left_edge = not apron_front(x - 1, y)
                right_edge = not apron_front(x + 1, y)
                if y == 11:
                    return col("wood", 2)
                return col("wood", 4 if left_edge or (y == 1) else 2 if right_edge else 3)
            return shirt
        if f.face == "back":
            if f.y == 7:
                return col("wood", 2)
            if f.y == 8 and f.x in (3, 4):
                return col("wood", 3)
            if f.x in (2, 5) and f.y < 7:
                return col("wood", 2)
            return shirt
        return col("wood", 2) if f.y == 7 else shirt

    def arm(f):
        if f.face == "top":
            return tex("slate", 3, f, K)
        if f.face == "bottom":
            return col("wood", 1)
        y = f.y
        if y <= 3:
            return tex("slate", 2, f, K)
        if y == 4:
            return col("slate", 4 if f.face in ("front", "right") else 3)
        if y >= 10:
            return col("wood", 2 if y == 10 else 1)
        if h01(K, "armsoot", f.part, f.face, f.x, y) < 0.13:
            return S(1)
        return S(3 if f.face in ("front", "right") and f.x < 3 else 2)

    def leg(f):
        trou = tex("stone", 1, f, K)
        if f.face == "top":
            return trou
        if f.face == "bottom":
            return col("obsidian", 1)
        y = f.y
        if y >= 8:
            if y == 11:
                return col("obsidian", 1)
            if y == 8:
                return col("wood", 2)
            return col("wood", 3 if (f.face == "front" and y == 9) else 1)
        if f.face == "front":
            bx = f.bx()
            if 1 <= bx <= 6 and y <= 5:
                if h01(K, "legsoot", bx, y) < 0.08:
                    return soot
                return col("wood", 2 if y == 5 else 4 if bx == 1 else 2 if bx == 6 else 3)
        return trou

    return make_skin("oren", {"head": head, "hat": hat, "body": body, "rarm": arm, "larm": arm,
                              "rleg": leg, "lleg": leg})


# --- Mira, the scout ------------------------------------------------------------------------------------------------


def mira() -> Canvas:
    K, SK, HR, CL = "mira", "skin_light", "hair_red", "moss"
    S = lambda i: col(SK, i)  # noqa: E731
    legend = {"h": col(HR, 2), "H": col(HR, 4), "k": col(HR, 1), "S": S(3), "L": S(4), "s": S(2), "n": S(2),
              "w": EYE_WHITE, "e": col("leaf", 4), "m": hex_rgba("#b8655a"), "f": hex_rgba("#c0846a")}
    front = ["hhhhhhhh",
             "hHHhHHhh",
             "hHhLLLSh",
             "hkkSSkkS",
             "SweSSews",
             "SfSnnSfs",
             "SSSmmSSs",
             "sSSSSSss"]

    def head(f):
        return standard_head(f, K, front, legend, HR, 2, SK, [2, 3, 3, 3, 4, 4, 5, 5], 6)

    def hood(f):
        dark = f.face == "front" or (f.face in ("left", "right") and f.fd == 0)
        if f.face == "front":
            if f.y == 0:
                return col(CL, 4)
            if f.x in (0, 7):
                return col(CL, 3 if f.x == 0 else 2)
            if f.y == 1 and f.x in (1, 6):
                return col(CL, 1)
            return None
        if f.face == "bottom":
            return col(CL, 1) if (f.x in (0, 7) or f.y <= 1) else None
        if f.face == "top":
            return tex(CL, 4 if f.x < 4 else 3, f, K)
        if f.face == "back" and f.x in (3, 4) and f.y < 6:
            return col(CL, 2)  # seam
        return tex(CL, 3 if f.y < 4 else 2, f, K) if not dark else col(CL, 3)

    def strap_front(x, y):
        return y <= 9 and x == round(6 - y * 5 / 9)

    def body(f):
        tunic = tex("sand", 3, f, K)
        if f.face == "top":
            return tunic
        if f.face == "bottom":
            return col("moss", 1)
        if f.y == 7:
            if f.face == "front" and f.x in (3, 4):
                return col("brass", 4 if f.x == 3 else 3)
            return col("wood", 1)
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return S(2)
        return tunic if f.y < 7 else tex("sand", 2, f, K)

    def cloak(f):
        x, y = f.x, f.y
        if f.face == "front":
            if strap_front(x, y):
                return col("wood", 3)
            if x <= 2 and 8 <= y <= 11:  # satchel on the right hip, hung from the strap
                if y == 8:
                    return col("wood", 4)
                if (x, y) == (1, 9):
                    return col("brass", 4)
                return col("wood", 3 if y < 11 and x < 2 else 2)
            if x in (0, 7):
                return col(CL, 3 if x == 0 else 2)
            if y == 0 and x in (1, 6):
                return col(CL, 2)
            if y == 0 and x in (3, 4):
                return col("brass", 5 if x == 3 else 4)  # cloak clasp
            return None
        if f.face == "top":
            return col(CL, 4 if f.x < 4 else 3)
        if f.face == "bottom":
            return None
        if f.face == "right" and 8 <= y <= 11 and x >= 2:  # satchel wraps round the hip
            return col("wood", 4 if y == 8 else 2)
        if f.face == "back":
            sx = round(1 + y * 5 / 9)
            if y <= 9 and x == sx:
                return col("wood", 3)
            if x in (3, 4) and y < 3:
                return col(CL, 2)
        return tex(CL, 3 if y < 3 else 2, f, K)

    def arm(f):
        if f.face == "top":
            return tex("sand", 3, f, K)
        if f.face == "bottom":
            return S(2)
        y = f.y
        if y >= 10:
            return S(3 if f.face in ("front", "right") else 2)
        if 6 <= y <= 9:
            if f.face == "front" and y in (7, 9) and f.x in (1, 2):
                return col("wood", 1)
            return col("wood", 4 if y == 6 else 3)
        return tex("sand", 3, f, K)

    def sleeve(f):  # cloak drape over the shoulders
        if f.face == "top":
            return col(CL, 3)
        if f.face == "bottom" or f.inner:
            return None
        if f.y <= 3 - (1 if f.face == "back" else 0):
            return col(CL, 2 if f.y == 3 or f.face == "back" else 3)
        return None

    def leg(f):
        trou = tex("moss", 1, f, K)
        if f.face == "top":
            return trou
        if f.face == "bottom":
            return col("wood", 1)
        y = f.y
        if y >= 6:
            if y == 11:
                return col("wood", 1)
            if y == 6:
                return col("wood", 4)
            if f.face == "front" and f.x in (1, 2) and y in (7, 9):
                return col("wood", 2)
            return col("wood", 3 if f.face in ("front", "right") else 2)
        return trou

    return make_skin("mira", {"head": head, "hat": hood, "body": body, "jacket": cloak, "rarm": arm, "larm": arm,
                              "rsleeve": sleeve, "lsleeve": sleeve, "rleg": leg, "lleg": leg})


# --- Teodor, the archivist ------------------------------------------------------------------------------------------


def teodor() -> Canvas:
    K, SK, HR = "teodor", "skin_pale", "cloud"
    S = lambda i: col(SK, i)  # noqa: E731
    G = col("brass", 4)
    legend = {"h": col(HR, 2), "H": col(HR, 4), "b": col(HR, 4), "S": S(3), "L": S(4), "s": S(2), "n": S(1),
              "w": hex_rgba("#d8e4ee"), "e": col("signal", 2), "m": hex_rgba("#8c5c58"), "G": G,
              "g": col("brass", 2)}
    front = ["HhHHHHhH",
             "hSLLLLSh",
             "hSsLLsSs",
             "SbbSSbbS",
             "GweGGewG",
             "SSSnnSSs",
             "SSsmmsSs",
             "sSSSSSss"]

    def head(f):
        if f.face in ("left", "right") and f.y == 4 and f.fd <= 3:
            return col("brass", 3)  # spectacle arms to the ears
        return standard_head(f, K, front, legend, HR, 3, SK, [1, 2, 3, 3, 4, 6, 7, 7], 8)

    def hat(f):
        if f.face == "front" and f.y == 4 and f.x in (0, 3, 4, 7):
            return col("brass", 4 if f.x in (0, 3) else 3)  # spectacle frame standing off the face
        return None

    def robe_col(f, k=1):
        return tex("signal", k, f, K)

    def body(f):
        if f.face == "top":
            return col("spirit", 3)
        if f.face == "bottom":
            return col("signal", 0)
        x, y = f.x, f.y
        if y == 7:
            if f.face == "front" and x in (5, 6):
                return col("book", 3)
            if f.face == "left" and f.x == 0:
                return col("book", 2)
            return col("wood", 2)
        if f.face == "front":
            if 8 <= y <= 9 and x in (5, 6):
                return col("book", 3 if y == 8 else 2) if not (y == 9 and x == 6) else col("bone", 5)
            if y == 0:
                return S(2) if x in (3, 4) else col("spirit", 4)
            if x == 3:
                return col("signal", 0)
            if x == 4:
                return col("spirit", 4)
            return robe_col(f, 2 if x < 3 else 1)
        if f.face == "left" and f.x == 0 and 8 <= y <= 9:
            return col("book", 2)
        if y == 0:
            return col("spirit", 3)
        return robe_col(f)

    def arm(f):
        if f.face == "top":
            return col("spirit", 3)
        if f.face == "bottom":
            return S(2)
        if f.y >= 10:
            return S(3 if f.face in ("front", "right") else 2)
        if f.y == 9:
            return col("spirit", 4)
        return robe_col(f, 2 if (f.face == "front" and f.x < 2) else 1)

    def sleeve(f):  # wide cuffs
        if f.face in ("top", "bottom"):
            return None
        if f.y == 8:
            return col("signal", 2)
        if f.y == 9:
            return col("spirit", 4)
        return None

    def leg(f):
        if f.face == "top":
            return robe_col(f)
        if f.face == "bottom":
            return col("wood", 0)
        y = f.y
        if y >= 10:
            return col("wood", 1 if y == 11 else 2)
        if y == 9:
            return col("spirit", 3)
        if f.face == "front":
            bx = f.bx()
            if bx == 3:
                return col("signal", 0)
            if bx == 4:
                return col("spirit", 4)
            return robe_col(f, 2 if bx < 3 else 1)
        return robe_col(f, 1 if y < 6 else 0)

    return make_skin("teodor", {"head": head, "hat": hat, "body": body, "rarm": arm, "larm": arm,
                                "rsleeve": sleeve, "lsleeve": sleeve, "rleg": leg, "lleg": leg})


# --- Sun, the healer ------------------------------------------------------------------------------------------------


def sun() -> Canvas:
    K, SK, HR = "sun", "skin_brown", "hair_black"
    S = lambda i: col(SK, i)  # noqa: E731
    legend = {"h": col(HR, 2), "H": col(HR, 4), "k": col(HR, 0), "S": S(3), "L": S(4), "s": S(2), "n": S(2),
              "w": EYE_WHITE, "e": hex_rgba("#2a160c"), "m": hex_rgba("#5e2a26")}
    front = ["hhhHHhhh",
             "hHhhhhHh",
             "hSLLLLSh",
             "SkkSSkkS",
             "SweSSews",
             "SSSnnSSs",
             "SSSmmSSs",
             "sSSSSSss"]

    def head(f):
        return standard_head(f, K, front, legend, HR, 2, SK, [2, 3, 3, 3, 4, 5, 6, 6], 7)

    def bun(f):
        if f.face == "top" and f.y <= 2 and 2 <= f.x <= 5:
            if (f.x, f.y) == (5, 1):
                return col("amber", 4)  # hairpin
            return col(HR, 3 if f.x < 4 else 2)
        if f.face == "back" and f.y <= 2 and 2 <= f.x <= 5:
            if (f.x, f.y) == (2, 1):
                return col("amber", 3)
            return col(HR, 2 if f.y < 2 else 1)
        return None

    def robe(f, k=4):
        return tex("bone", k, f, K, lo=0.02, folds=0.15)

    def body(f):
        if f.face == "top":
            return robe(f, 5)
        if f.face == "bottom":
            return col("bone", 2)
        x, y = f.x, f.y
        if y == 6:
            return col("amber", 4)
        if y == 7:
            return col("amber", 2 if f.face != "front" else 3)
        if f.face == "front":
            if y == 0 and x in (3, 4):
                return S(2)
            if y <= 4 and x == 2 + y:
                return col("bone", 2)  # robe wrap
            if 8 <= y <= 10 and x == 5:
                return col("amber", 3 if y < 10 else 2)
            if 8 <= y <= 9 and x == 6:
                return col("amber", 2)
            return robe(f, 4)
        return robe(f, 4)

    def arm(f):
        if f.face == "top":
            return robe(f, 5)
        if f.face == "bottom":
            return S(2)
        if f.y >= 10:
            return S(3 if f.face in ("front", "right") else 2)
        if f.y == 9:
            return col("amber", 3)
        return robe(f, 4 if f.face in ("front", "right") else 3)

    def leg(f):
        if f.face == "top":
            return robe(f, 3)
        if f.face == "bottom":
            return col("wood", 1)
        y = f.y
        if y >= 10:
            return col("wood", 2 if y == 10 else 1)
        if y == 9:
            return col("bone", 2)
        return robe(f, 4 if f.face == "front" else 3)

    return make_skin("sun", {"head": head, "hat": bun, "body": body, "rarm": arm, "larm": arm,
                             "rleg": leg, "lleg": leg})


# --- Vael, the rival ------------------------------------------------------------------------------------------------


def vael() -> Canvas:
    K, SK, HR = "vael", "skin_light", "mirror"
    S = lambda i: col(SK, i)  # noqa: E731
    legend = {"h": col(HR, 3), "H": col(HR, 5), "M": col(HR, 4), "k": col(HR, 2), "S": S(3), "L": S(4), "s": S(2),
              "n": S(2), "w": EYE_WHITE, "e": col("frost", 3), "m": hex_rgba("#8a4a46")}
    front = ["HHHHMMMh",
             "HHMMMMhh",
             "hMMMhLLh",
             "hkkSSSkS",
             "SweSSews",
             "SSSnnSns",
             "SSSSmmSs",
             "sSSSSSss"]

    def head(f):
        if f.face == "front" and (f.x, f.y) == (5, 2):
            return col(HR, 2)  # the raised brow
        return standard_head(f, K, front, legend, HR, 4, SK, [3, 3, 3, 3, 4, 5, 5, 6], 6)

    def hat(f):
        if f.face == "front":
            if f.y == 0 or (f.y == 1 and f.x <= 5) or (f.y == 2 and f.x <= 2):
                return col(HR, 5 if f.x < 4 else 4)
            if f.y == 7 and f.x in (0, 7):
                return col("slate", 1)
            return None
        if f.face == "top":
            return hair(HR, 4, f, K + "o")
        if f.face == "bottom":
            return col("slate", 0) if (f.x in (0, 7) or f.y <= 1) else None
        if f.y >= 6:  # the high collar
            return col("crimson", 3) if f.y == 6 else col("slate", 1)
        if f.y <= 1 or (f.face == "back" and f.y <= 3):
            return hair(HR, 4 if f.face != "back" else 3, f, K + "o")
        return None

    def coat(f, k=1):
        return tex("slate", k, f, K)

    def body(f):
        if f.face == "top":
            return coat(f, 2)
        if f.face == "bottom":
            return col("obsidian", 2)
        x, y = f.x, f.y
        if y == 7:
            if f.face == "front" and x in (2, 3):
                return col("mirror", 4 if x == 2 else 3)
            return col("obsidian", 0)
        if f.face == "front":
            if y == 0:
                return col("crimson", 2) if 2 <= x <= 5 else col("slate", 2)
            if y == 1 and x in (3, 4):
                return col("crimson", 1)
            if x == 5:
                return col("crimson", 3 if y < 7 else 2)
            if x == 6 and y in (2, 4, 9):
                return col("mirror", 3)  # buttons
            return coat(f, 2 if x < 2 else 1)
        if f.face in ("left", "right") and f.y < 2:
            return col("slate", 2)
        return coat(f, 1)

    def arm(f):
        if f.face == "top":
            return coat(f, 2)
        if f.face == "bottom":
            return col("obsidian", 1)
        if f.y >= 10:
            return col("obsidian", 2 if f.y == 10 else 1)
        if f.y == 9:
            return col("crimson", 3)
        if f.outer and f.x == (2 if f.side == "r" else 1) and f.y < 9:
            return col("crimson", 2)
        return coat(f, 2 if (f.face == "front" and f.x < 2) else 1)

    def leg(f):
        if f.face == "top":
            return col("obsidian", 2)
        if f.face == "bottom":
            return col("obsidian", 0)
        y = f.y
        if y >= 7:
            if y == 11:
                return col("obsidian", 0)
            if y == 8 and f.face == "front" and f.x == 1:
                return col("mirror", 4)
            return col("obsidian", 2 if y == 7 else 1)
        if f.face == "front" and f.x == 1:
            return col("slate", 2)  # trouser crease
        return tex("obsidian", 2, f, K)

    return make_skin("vael", {"head": head, "hat": hat, "body": body, "rarm": arm, "larm": arm,
                              "rleg": leg, "lleg": leg})


# --- Training ghost -------------------------------------------------------------------------------------------------


def training_ghost() -> Canvas:
    K = "training_ghost"
    sg = ramp("signal")
    eye = hex_rgba(GHOST_EYE)
    front = ["HHHHHHHH",
             "HhhhhhhH",
             "bbbbbbbb",
             "hhhhhhhh",
             "heehheeh",
             "hhhhhhhh",
             "hhhhhhhh",
             "hhhhhhhh"]
    legend = {"H": sg[5], "h": sg[4], "b": sg[3], "e": eye}

    def head(f):
        if f.face == "front":
            return head_map(f, front, legend)
        if f.face == "top":
            return sg[5]
        if f.face == "bottom":
            return sg[3]
        if f.y == 2:
            return sg[3]  # headband
        return sg[5] if f.y < 2 else sg[4]

    def body(f):
        if f.face in ("top", "bottom"):
            return sg[4] if f.face == "top" else sg[3]
        if f.face in ("left", "right") and f.x in (1, 2):
            return sg[5]  # racing stripes
        if f.face == "front" and f.y <= 9 and f.x in (f.y - 2, f.y - 1):
            return sg[5]  # runner's sash
        if f.face == "back" and f.y <= 9 and f.x in (9 - f.y, 8 - f.y):
            return sg[5]
        return sg[4] if f.y < 10 else sg[3]

    def arm(f):
        if f.face == "top":
            return sg[4]
        if f.face == "bottom":
            return sg[5]
        if f.y >= 10:
            return sg[5]
        return sg[4] if f.y > 0 else sg[3]

    def leg(f):
        if f.face == "top":
            return sg[3]
        if f.face == "bottom":
            return sg[2]
        if f.y >= 10:
            return sg[3] if f.y == 10 else sg[2]
        if f.y <= 4:
            return sg[3] if f.y == 4 else sg[4]
        return sg[5]

    return make_skin(K, {"head": head, "body": body, "rarm": arm, "larm": arm, "rleg": leg, "lleg": leg})


# ---------------------------------------------------------------------------------------------------------------------


def build(out):
    out.block("hearth_anchor_side", anchor_side())
    out.block("hearth_anchor_top", anchor_top())
    out.block("hearth_anchor_bottom", anchor_bottom())
    out.block("meditation_mat", meditation_mat())
    out.block("forge_front", forge_front())
    out.block("forge_side", forge_side())
    out.block("forge_top", forge_top())
    out.block("trophy_mount", trophy_mount())
    out.block("role_pad_side", role_pad_side())
    out.block("role_pad_guard", role_pad("guard", icon_shield))
    out.block("role_pad_haul", role_pad("haul", icon_crate))
    out.block("role_pad_farm", role_pad("farm", icon_wheat))
    out.block("role_pad_mine", role_pad("mine", icon_pick))
    out.block("role_pad_train", role_pad("train", icon_target))
    out.block("spire_board", spire_board())
    out.block("spire_board_post", spire_board_post())
    out.block("monolith_side", monolith_side())
    out.block("monolith_top", monolith_top())
    out.block("start_gate", start_gate())
    out.block("finish_gate", finish_gate())
    out.block("checkpoint_ring", checkpoint_ring())
    out.block("target_dummy", target_dummy())
    out.block("target_dummy_side", target_dummy_side())

    out.item("trophy", trophy_item())

    out.entity("townsfolk/oren", oren())
    out.entity("townsfolk/mira", mira())
    out.entity("townsfolk/teodor", teodor())
    out.entity("townsfolk/sun", sun())
    out.entity("townsfolk/vael", vael())
    out.entity("training_ghost", training_ghost())

    for i in range(4):
        out.particle(f"echo_wisp_{i}", echo_wisp(i))
    for i in range(3):
        out.particle(f"hearth_ember_{i}", hearth_ember(i))

    # the carried flame (W1)
    out.item("hearth_ember", ember_lantern())
    out.block("hearthfire_stones", hearthfire_stones())
    out.block("hearthfire_ash", hearthfire_ash())
    out.block("hearthfire_coal", hearthfire_coal())
    out.block("hearthfire_flame", [hearthfire_flame(i) for i in range(8)], frametime=2)
    for i in range(4):
        out.particle(f"hearth_warmth_{i}", warmth_mote(i))
    for i in range(6):
        out.particle(f"hearth_flame_{i}", flame_tongue(i))
    for i in range(2):
        out.particle(f"hearth_firefly_{i}", firefly(i))
