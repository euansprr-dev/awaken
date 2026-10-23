"""Above module textures: Precursor plates (dark slate), cyan circuit glow, brass trim, amber warning lights.

Run: python3 tools/art/build_all.py above
"""
import math

from pixelart import *

SIG = ramp("signal")
PRE = ramp("precursor")
BRASS = ramp("brass")
AMB = ramp("amber")
DREAM = ramp("dream")
CLEAR = (0, 0, 0, 0)


def px(c: Canvas, x, y, col):
    c.set(x, y, col)


# ---------------------------------------------------------------------------------------------------------------------
# building blocks


def plating(seed="pl"):
    return circuits(plates("precursor", seed=seed), "signal", seed=seed, lines=2)


def panel(seed="pn"):
    c = plates("precursor", seed=seed, size=16, rivets=False)
    for i in range(16):
        c.set(i, 0, PRE[4])
        c.set(0, i, PRE[4])
        c.set(i, 15, PRE[0])
        c.set(15, i, PRE[0])
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, BRASS[4])
    return c


def trim_side():
    c = stone("brass", seed="trim", scale=2, contrast=0.4, lo=1, hi=3)
    for x in range(16):
        c.set(x, 0, BRASS[5])
        c.set(x, 1, BRASS[4])
        c.set(x, 14, BRASS[1])
        c.set(x, 15, BRASS[0])
    for x in range(1, 16, 4):
        for y in (5, 10):
            c.set(x, y, BRASS[1])
            c.set(x + 1, y, BRASS[5])
    return c


def trim_top():
    c = stone("brass", seed="trimt", scale=2, contrast=0.5, lo=2, hi=4)
    frame_border(c, "brass")
    for i in range(3, 13):
        c.set(i, 7, PRE[1])
        c.set(i, 8, SIG[3] if i % 3 == 0 else PRE[2])
    return c


def glow_panel():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            c.set(x, y, SIG[5] if d < 3 else SIG[4] if d < 6 else SIG[3])
    for i in range(16):
        c.set(i, 0, PRE[2])
        c.set(0, i, PRE[2])
        c.set(i, 15, PRE[1])
        c.set(15, i, PRE[1])
        c.set(i, 8, SIG[3])
        c.set(8, i, SIG[3])
    return c


def warning_light():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, PRE[1])
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.2:
                c.set(x, y, AMB[5] if d < 2 else AMB[4] if d < 3.6 else AMB[3])
    frame_border(c, "brass")
    for x in (1, 14):
        for y in (1, 14):
            c.set(x, y, PRE[4])
    return c


def void_glass():
    c = Canvas()
    rng = rng_for("voidglass")
    for y in range(16):
        for x in range(16):
            c.set(x, y, (*SIG[0][:3], 150))
    for _ in range(6):
        c.set(rng.randrange(1, 15), rng.randrange(1, 15), (*SIG[4][:3], 200))
    for i in range(16):
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            c.set(x, y, (*PRE[3][:3], 255))
    for i in range(2, 6):
        c.set(i + 1, i, (*SIG[2][:3], 170))
    return c


def membrane():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = 0.5 + 0.5 * math.sin((x + y * 0.5) * 0.9)
            c.set(x, y, (*SIG[4 if v > 0.75 else 3][:3], 70 + int(50 * v)))
    for i in range(16):
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            c.set(x, y, (*SIG[2][:3], 200))
    return c


def door():
    c = plates("precursor", seed="door", size=16, rivets=False)
    for y in range(16):
        c.set(0, y, PRE[1])
        c.set(15, y, PRE[1])
        c.set(7, y, PRE[0])
        c.set(8, y, PRE[4])
    for x in range(16):
        for y in (3, 12):
            c.set(x, y, BRASS[3])
            c.set(x, y + 1, BRASS[1])
    return c


def door_core():
    c = door()
    for y in range(4, 12):
        for x in range(4, 12):
            c.set(x, y, PRE[1])
    glyph(c, "amber", seed="door_core", size=5)
    frame_border(c, "brass")
    return c


def ring_frame():
    c = stone("brass", seed="ringf", scale=2, contrast=0.6, lo=1, hi=4)
    for y in range(16):
        for x in range(16):
            if 5 <= y <= 10:
                c.set(x, y, PRE[2] if (x + y) % 5 else PRE[3])
    for x in range(16):
        c.set(x, 7, SIG[4])
        c.set(x, 8, SIG[3])
    frame_border(c, "brass")
    return c


def pad_top():
    c = plates("precursor", seed="pad", size=8)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.5 < d < 6.5:
                c.set(x, y, SIG[5] if 4.5 < d < 5.5 else SIG[3])
            elif d <= 2:
                c.set(x, y, AMB[4])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# machines: shared casing + a screen with an icon per machine

ICONS = {
    "essence_reactor": [
        "...####...",
        "..#oooo#..",
        ".#oo++oo#.",
        ".#o++++o#.",
        ".#o++++o#.",
        ".#oo++oo#.",
        "..#oooo#..",
        "...####...",
    ],
    "fabricator": [
        "o.o.o.o.o.",
        "..........",
        ".########.",
        ".#..##..#.",
        ".#.####.#.",
        ".#..##..#.",
        ".########.",
        "..........",
    ],
    "gate_scanner": [
        "..######..",
        ".#....+.#.",
        "#....+...#",
        "#...+..o.#",
        "#...#....#",
        "#........#",
        ".#..o...#.",
        "..######..",
    ],
    "gate_stabiliser": [
        "##......##",
        "#.#....#.#",
        "#..#..#..#",
        "#...++...#",
        "#...++...#",
        "#..#..#..#",
        "#.#....#.#",
        "##......##",
    ],
    "waypoint_pylon": [
        "....++....",
        "....##....",
        "...#..#...",
        "..#.oo.#..",
        "..#.oo.#..",
        "...#..#...",
        "....##....",
        "...####...",
    ],
    "deep_lift": [
        ".#......#.",
        "..#....#..",
        "...#..#...",
        "....##....",
        ".#......#.",
        "..#....#..",
        "...#..#...",
        "....oo....",
    ],
    "frame_forge": [
        "...####...",
        "..#....#..",
        ".#......#.",
        ".#.oooo.#.",
        ".#.#..#.#.",
        ".#......#.",
        "..#....#..",
        ".##....##.",
    ],
    "clone_vat": [
        "#...++...#",
        "#..+..+..#",
        "#...++...#",
        "#..####..#",
        "#.#.##.#.#",
        "#...##...#",
        "#..#..#..#",
        "##########",
    ],
    "dream_recorder": [
        "..........",
        ".##....##.",
        "#..#..#..#",
        "....##....",
        "..........",
        "o.o.o.o.o.",
        "..........",
        "+.+.+.+.+.",
    ],
    "signal_relay": [
        ".#......#.",
        "#..#..#..#",
        "#.#.++.#.#",
        "#.#.++.#.#",
        "#..#..#..#",
        ".#......#.",
        "....##....",
        "...####...",
    ],
    "recalibrator": [
        "..........",
        ".#........",
        ".#..#.....",
        ".#..#...#.",
        ".#.##..##.",
        ".####.###.",
        ".#########",
        "oooooooooo",
    ],
    "observatory": [
        ".......+..",
        "......+++.",
        "..####.+..",
        ".#....#...",
        "#..++..#..",
        ".#....#...",
        "..####....",
        "..........",
    ],
    "launch_ring": [
        "...####...",
        "..#.++.#..",
        ".#.++++.#.",
        ".#..++..#.",
        ".#..++..#.",
        "..#.++.#..",
        "...####...",
        "..........",
    ],
}


def machine_front(name, broken=False, scanned=False):
    """Returns (front, glow). The glow layer holds only the lit pixels (screen + icon + status lights)."""
    base = plates("precursor", seed="mf" + name, size=16, rivets=False)
    frame_border(base, "brass")
    glowc = Canvas()
    primary = DREAM[4] if name == "dream_recorder" else SIG[4]
    bright = DREAM[5] if name == "dream_recorder" else SIG[5]
    icon = ICONS.get(name, ICONS["gate_scanner"])
    # screen 12x10 at (2,2)
    for y in range(2, 12):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 11)
            base.set(x, y, PRE[3] if edge else SIG[0])
            if not edge and not broken:
                glowc.set(x, y, SIG[0] if not (x + y) % 7 == 0 else SIG[1])
    if not broken:
        for j, row in enumerate(icon):
            for i, ch in enumerate(row):
                col = {"#": primary, "+": bright, "o": AMB[4]}.get(ch)
                if col:
                    base.set(3 + i, 3 + j, col)
                    glowc.set(3 + i, 3 + j, col)
        for x, col in ((4, AMB[4]), (6, SIG[4]), (8, SIG[4]), (11, AMB[5])):
            base.set(x, 13, col)
            glowc.set(x, 13, col)
    else:
        rng = rng_for("crack", name)
        # dead screen: cracks and a few failing pixels
        cx, cy = rng.randrange(5, 11), rng.randrange(4, 9)
        for d in range(8):
            for (dx, dy) in ((1, 0), (-1, 1), (0, -1)):
                x, y = cx + dx * d // 2 + rng.randrange(-1, 2), cy + dy * d // 2
                if 3 <= x <= 12 and 3 <= y <= 10:
                    base.set(x, y, PRE[4])
        if not scanned:
            for _ in range(4):
                base.set(rng.randrange(3, 13), rng.randrange(3, 11), SIG[3])
            base.set(5, 13, AMB[3])
        overlay_noise(base, "amber", seed="rust" + name, density=0.04, idx=1)
    return base, glowc


def machine_side():
    c = plates("precursor", seed="ms", size=8)
    frame_border(c, "brass")
    circuits(c, "signal", seed="ms", lines=1)
    c.set(13, 2, AMB[4])
    return c


def machine_top():
    c = plates("precursor", seed="mt", size=16, rivets=False)
    frame_border(c, "brass")
    g = Canvas()
    for y in (4, 7, 10):
        for x in range(3, 13):
            c.set(x, y, SIG[3])
            g.set(x, y, SIG[4] if x % 2 else SIG[3])
            c.set(x, y + 1, PRE[0])
    return c, g


def machine_bottom():
    c = plates("precursor", seed="mb", size=8)
    frame_border(c, "brass")
    return c


def broken_side():
    c = plates("precursor", seed="bs", size=8)
    overlay_noise(c, "amber", seed="bs", density=0.08, idx=1)
    overlay_noise(c, "slate", seed="bs2", density=0.06, idx=0)
    return c


def broken_top():
    c = plates("precursor", seed="bt", size=16, rivets=False)
    frame_border(c, "brass")
    overlay_noise(c, "amber", seed="bt", density=0.07, idx=1)
    for x in range(3, 13):
        c.set(x, 7, PRE[0])
    return c


def terminal_screen():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, SIG[0])
    rng = rng_for("terminal")
    for y in range(3, 13, 2):
        n = rng.randrange(6, 12)
        for x in range(2, 2 + n):
            c.set(x, y, SIG[4] if rng.random() > 0.2 else SIG[2])
    frame_border(c, "brass")
    c.set(13, 13, AMB[4])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# items


def scrap():
    c = polygon([(2, 9), (6, 3), (9, 5), (13, 2), (14, 8), (10, 13), (5, 14)], "precursor")
    for (x, y) in ((6, 8), (7, 8), (8, 9), (9, 10)):
        c.set(x, y, BRASS[3])
    c.set(10, 6, SIG[4])
    c.set(7, 5, PRE[5])
    return c


def circuit():
    c = Canvas()
    for y in range(3, 13):
        for x in range(2, 14):
            c.set(x, y, PRE[2] if (x + y) % 2 else PRE[1])
    for x in range(3, 13):
        c.set(x, 6, SIG[3])
    for y in range(4, 12):
        c.set(5, y, SIG[3])
        c.set(10, y, SIG[3])
    for y in range(7, 10):
        for x in range(7, 9):
            c.set(x, y, AMB[4])
    for x in (3, 6, 9, 12):
        c.set(x, 13, BRASS[4])
        c.set(x, 2, BRASS[4])
    c.set(5, 4, SIG[5])
    c.set(10, 11, SIG[5])
    return outline(c)


def alloy():
    c = Canvas()
    for y in range(5, 12):
        inset = (11 - y) // 2 if y < 8 else 0
        for x in range(2 + inset, 14 - inset):
            t = (x + y) / 26
            c.set(x, y, BRASS[4] if y == 5 else BRASS[3] if t < 0.5 else PRE[3])
    for x in range(4, 12):
        c.set(x, 8, SIG[3])
    c.set(5, 6, BRASS[5])
    return outline(c)


def cell():
    c = Canvas()
    for y in range(2, 14):
        for x in range(5, 11):
            if y in (2, 3, 12, 13):
                c.set(x, y, BRASS[4] if y in (2, 12) else BRASS[2])
            else:
                edge = x in (5, 10)
                c.set(x, y, SIG[2] if edge else SIG[4 if y > 6 else 3])
    c.set(6, 5, SIG[5])
    c.set(6, 6, SIG[5])
    c.set(7, 1, BRASS[5])
    c.set(8, 1, BRASS[5])
    return outline(c)


def frame_piece(kind):
    shapes = {
        "helmet": [(3, 4), (12, 4), (13, 10), (11, 12), (4, 12), (2, 10)],
        "chestplate": [(2, 2), (6, 2), (8, 4), (10, 2), (14, 2), (14, 7), (12, 7), (12, 14), (4, 14), (4, 7), (2, 7)],
        "leggings": [(4, 2), (12, 2), (12, 14), (9, 14), (8, 7), (7, 14), (4, 14)],
        "boots": [(3, 6), (7, 6), (7, 11), (13, 11), (13, 14), (3, 14)],
    }
    c = polygon(shapes[kind], "precursor")
    rows = {"helmet": [8], "chestplate": [5, 10], "leggings": [5, 10], "boots": [12]}
    for y in rows[kind]:
        for x in range(16):
            if c.get(x, y)[3] > 0 and c.get(x, y)[:3] != hex_rgba(OUTLINE)[:3]:
                c.set(x, y, SIG[4])
    for x in range(16):
        for y in range(16):
            if c.get(x, y)[3] > 0 and c.get(x, y)[:3] != hex_rgba(OUTLINE)[:3] and c.get(x, y - 1)[:3] == hex_rgba(OUTLINE)[:3]:
                c.set(x, y, BRASS[4])
    if kind == "helmet":
        for x in range(5, 11):
            c.set(x, 8, SIG[5])
    return c


MODULE_COLOURS = {
    "jet_boots": ("ember", "^"),
    "shield_emitter": ("signal", "o"),
    "stealth_field": ("spirit", "~"),
    "magnet": ("crimson", "u"),
    "night_vision": ("moss", "e"),
    "step_assist": ("amber", "s"),
}


def module(mid):
    rp, sym = MODULE_COLOURS[mid]
    r = ramp(rp)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            # hexagonal chip
            if abs(x - 7.5) + abs(y - 7.5) * 0.55 <= 7 and 2 <= y <= 13:
                c.set(x, y, PRE[2] if (x + y) % 3 else PRE[3])
    for y in range(4, 12):
        for x in range(4, 12):
            if abs(x - 7.5) + abs(y - 7.5) <= 4.5:
                c.set(x, y, r[3])
    marks = {
        "^": [(7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (7, 8), (8, 8), (7, 9), (8, 9)],
        "o": [(6, 6), (7, 5), (8, 5), (9, 6), (10, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 9), (5, 8), (5, 7)],
        "~": [(5, 7), (6, 6), (7, 7), (8, 8), (9, 7), (10, 6), (5, 9), (6, 8), (7, 9), (8, 10), (9, 9), (10, 8)],
        "u": [(5, 5), (5, 6), (5, 7), (6, 8), (7, 9), (8, 9), (9, 8), (10, 7), (10, 6), (10, 5)],
        "e": [(5, 8), (6, 7), (7, 6), (8, 6), (9, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 9), (7, 8), (8, 8)],
        "s": [(5, 10), (6, 10), (6, 9), (7, 9), (8, 8), (8, 7), (9, 7), (10, 6), (10, 5)],
    }
    for (x, y) in marks[sym]:
        c.set(x, y, r[5])
    for x in (3, 12):
        c.set(x, 7, BRASS[4])
        c.set(x, 8, BRASS[2])
    return outline(c)


def outline(c):
    # 1px dark outline, same rule as the toolkit's item shapes
    mask = [[c.get(x, y)[3] > 0 for x in range(c.w)] for y in range(c.h)]
    o = hex_rgba(OUTLINE)
    for y in range(c.h):
        for x in range(c.w):
            if not mask[y][x] and any(0 <= x + dx < c.w and 0 <= y + dy < c.h and mask[y + dy][x + dx]
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c.set(x, y, o)
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Frame equipment (64x32 humanoid armour layout)


def _box(c, u, v, w, h, d, face):
    """Paints the six faces of a model cube at texOffs (u, v) of size w x h x d with face(fx, fy, fw, fh, name)."""
    face(u + d, v, w, d, "top")
    face(u + d + w, v, w, d, "bottom")
    face(u, v + d, d, h, "right")
    face(u + d, v + d, w, h, "front")
    face(u + d + w, v + d, d, h, "left")
    face(u + d + w + d, v + d, w, h, "back")


def equipment(leggings=False):
    c = Canvas(64, 32)
    base = plates("precursor", seed="eq", size=8, w=64, h=32)

    def face(fx, fy, fw, fh, name):
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                col = base.get(x, y)
                if y == fy or x == fx or x == fx + fw - 1 or y == fy + fh - 1:
                    col = BRASS[3] if name in ("front", "back") else PRE[1]
                c.set(x, y, col)
        if name in ("front", "back") and fw >= 4 and fh >= 4:
            mid = fy + fh // 2
            for x in range(fx + 1, fx + fw - 1):
                c.set(x, mid, SIG[4])
            if fw >= 6:
                c.set(fx + fw // 2, fy + 1, AMB[4])

    if leggings:
        _box(c, 0, 16, 4, 12, 4, face)    # legs
        _box(c, 16, 16, 8, 12, 4, face)   # waist (body)
    else:
        _box(c, 0, 0, 8, 8, 8, face)      # head
        _box(c, 16, 16, 8, 12, 4, face)   # body
        _box(c, 40, 16, 4, 12, 4, face)   # arms
        _box(c, 0, 16, 4, 12, 4, face)    # boots (legs)
        # visor across the helmet front
        for x in range(9, 15):
            c.set(x, 12, SIG[5])
            c.set(x, 13, SIG[3])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# the Halo sky


def planet(size=128):
    c = Canvas(size, size)
    rng = rng_for("planet")
    land = value_noise(size, size, rng, scale=5, octaves=4)
    cloud = value_noise(size, size, rng_for("clouds"), scale=7, octaves=3)
    water, leaf, sand, cl = ramp("water"), ramp("leaf"), ramp("sand"), ramp("cloud")
    m = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - m, y - m) / (size / 2)
            if d > 1.0:
                continue
            light = max(0.0, min(1.0, 0.75 - (x - m) / size * 1.3 + 0.1))   # day side on the left
            v = land[y][x]
            if v > 0.56:
                col = sand[3] if v > 0.66 else leaf[3]
            else:
                col = water[2] if v < 0.45 else water[3]
            r, g, b = col[:3]
            if cloud[y][x] > 0.64:
                a = min(1.0, (cloud[y][x] - 0.64) * 6)
                r, g, b = int(r + (cl[4][0] - r) * a), int(g + (cl[4][1] - g) * a), int(b + (cl[4][2] - b) * a)
            k = 0.25 + 0.75 * light
            r, g, b = int(r * k), int(g * k), int(b * k)
            if light < 0.25 and v > 0.58 and rng.random() < 0.05:
                r, g, b = AMB[5][:3]   # city lights on the night side
            if d > 0.93:
                t = (d - 0.93) / 0.07
                r, g, b = int(r + (SIG[4][0] - r) * t), int(g + (SIG[4][1] - g) * t), int(b + (SIG[4][2] - b) * t)
            alpha = 255 if d < 0.97 else int(255 * (1 - (d - 0.97) / 0.03))
            c.set(x, y, (r, g, b, max(0, alpha)))
    return c


def leviathan(w=128, h=64):
    c = Canvas(w, h)
    body = ramp("abyss")
    for x in range(w):
        t = x / (w - 1)
        # a long tapering body: thick near the head (right), thin tail (left)
        thick = (math.sin(t * math.pi) ** 0.5) * 19 * (0.35 + 0.65 * t)
        cy = h / 2 + math.sin(t * 6.0) * 3
        for y in range(h):
            dy = abs(y - cy)
            if dy <= thick:
                shade = body[2 if y < cy else 1]
                c.set(x, y, (*shade[:3], 235))
        # fins
        if 0.45 < t < 0.65:
            for k in range(int((0.65 - abs(t - 0.55) * 4) * 18)):
                y = int(cy + thick + k)
                if 0 <= y < h:
                    c.set(x, y, (*body[3][:3], 150))
        if t < 0.08:
            for k in range(-10, 11):
                y = int(cy + k)
                if 0 <= y < h and abs(k) > (t * 120):
                    c.set(x, y, (*body[3][:3], 170))
    # glowing spots along the flank and an eye
    for x in range(14, w - 14, 7):
        t = x / (w - 1)
        cy = h / 2 + math.sin(t * 6.0) * 3
        c.set(x, int(cy + 2), (*SIG[4][:3], 255))
    c.set(int(w * 0.86), int(h / 2 - 3), (*SIG[5][:3], 255))
    return c


# ---------------------------------------------------------------------------------------------------------------------


MACHINES = ["essence_reactor", "gate_scanner", "gate_stabiliser", "waypoint_pylon", "deep_lift", "frame_forge", "clone_vat",
            "dream_recorder", "signal_relay", "recalibrator", "observatory", "launch_ring", "fabricator"]


def build(out):
    out.block("precursor_plating", plating())
    out.block("precursor_panel", panel())
    out.block("precursor_trim", trim_side())
    out.block("precursor_trim_top", trim_top())
    out.block("precursor_glow_panel", glow_panel())
    out.block("precursor_warning_light", warning_light())
    out.block("precursor_void_glass", void_glass())
    out.block("precursor_membrane", membrane())
    out.block("precursor_door", door())
    out.block("precursor_door_core", door_core())
    out.block("launch_ring_frame", ring_frame())
    out.block("halo_return_pad_top", pad_top())
    out.block("precursor_log_terminal_screen", terminal_screen())
    side = machine_side()
    top, top_glow = machine_top()
    out.block("precursor_machine_side", side)
    out.block("precursor_machine_top", top)
    out.block("precursor_machine_top_glow", top_glow)
    out.block("precursor_machine_bottom", machine_bottom())
    for m in MACHINES:
        f, g = machine_front(m)
        out.block(m + "_front", f)
        out.block(m + "_front_glow", g)
    f, _ = machine_front("broken", broken=True)
    out.block("broken_machine_front", f)
    f, _ = machine_front("broken", broken=True, scanned=True)
    out.block("broken_machine_front_scanned", f)
    out.block("broken_machine_side", broken_side())
    out.block("broken_machine_top", broken_top())

    out.item("precursor_scrap", scrap())
    out.item("precursor_circuit", circuit())
    out.item("precursor_alloy", alloy())
    out.item("essence_cell", cell())
    out.item("precursor_scanner", device("precursor", "signal", seed="scanner"))
    for kind in ("helmet", "chestplate", "leggings", "boots"):
        out.item("precursor_frame_" + kind, frame_piece(kind))
    for mid in MODULE_COLOURS:
        out.item("frame_module_" + mid, module(mid))

    out.entity("equipment/humanoid/precursor_frame", equipment(False))
    out.entity("equipment/humanoid_leggings/precursor_frame", equipment(True))
    out._save("misc/halo_planet", planet())
    out._save("misc/sky_leviathan", leviathan())
