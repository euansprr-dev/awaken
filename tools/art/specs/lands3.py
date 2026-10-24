"""Awakened Lands, the third pair (docs/modules/lands.md): Frost Spires (Frostwild) and Mirror Marsh (Mire).
Bluepine (scaled blue-grey bark under a silver frost, needles the blue of deep ice with frosted tips), frostspar (ice
grown into crystal, the aurora's green, cyan and violet caught in its seams), its slivers, hoarfrost (frozen ground
feathered with frost); weepwood (smooth pale silver bark, narrow silver-sage leaves, tendrils ending in pale catkins),
moonbloom (a pale lily on the black water, its petals glowing), mirepeat (wet peat threaded with silver moss); the
snow and mist that drift over them.

  python3 tools/art/build_all.py lands3          writes the textures
  python3 tools/art/specs/lands3.py preview <dir>  tiles each block 3x3 (as it sits in the world) into a contact sheet

Emissive parts are tiny solid model faces (tools/content/lands3_content.py turns each run of glow pixels into one face
sampling a solid glow tile), so nothing here needs alpha-tested overdraw beyond the leaves and plants themselves.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from pixelart import Canvas, hex_rgba, quantize, rng_for, value_noise  # noqa: E402

RAMPS = {
    # Frost Spires: blue-grey scaled bark, ice-pale heartwood, deep ice-blue needles, frostspar, the aurora, hoarfrost
    "bluepine_bark": ["#1c2633", "#273546", "#354659", "#46596f", "#5b7188", "#7d93aa", "#b9cad8"],
    "bluepine_wood": ["#566a78", "#738897", "#91a6b3", "#afc2cc", "#cbdbe1", "#e4eef1"],
    "bluepine_needle": ["#0c1e31", "#122c46", "#193d5e", "#22517a", "#316995", "#4f86b0", "#86b2d3"],
    "frostspar": ["#5f8fb7", "#7eaad0", "#9fc6e5", "#c0dcf2", "#ddeefa", "#f6fbff"],
    "aurora": ["#3dffc4", "#56eeff", "#9b8bff", "#d6fff2", "#7affd9"],
    "hoarfrost": ["#6f97b0", "#88aec5", "#a3c4d7", "#bdd8e6", "#d5e8f1", "#ecf6fb"],
    # Mirror Marsh: pale silver bark, cream-green heartwood, silver-sage leaves, tendrils, peat and its moss, moonbloom
    "weepwood_bark": ["#2b302d", "#454b46", "#666d67", "#8a918a", "#adb3ab", "#cbd0c7", "#e3e6dd"],
    "weepwood_wood": ["#666a53", "#858970", "#a3a68b", "#bfc1a7", "#d6d7c1"],
    "weepwood_leaf": ["#1c281f", "#2c3c30", "#3f5343", "#566b59", "#728674", "#93a590", "#c1cebc"],
    "tendril": ["#36443a", "#536455", "#788a76", "#a3b19d", "#d0d9c7"],
    "peat": ["#130f0c", "#1d1611", "#281e17", "#34281e", "#433427"],
    "peat_moss": ["#46554a", "#617266", "#849585", "#adbbad", "#cdd8cc"],
    "lily_pad": ["#0f1d17", "#183024", "#244533", "#335c45", "#477658"],
    "moonbloom": ["#b9c6dc", "#d6e0f0", "#eef3fb", "#ffffff"],
    "moon_glow": ["#d8e4ff", "#eef3ff", "#c3d4ff", "#ffffff"],
}


def rp(name):
    return [hex_rgba(c) for c in RAMPS[name]]


def shade(c, f):
    return (max(0, min(255, round(c[0] * f))), max(0, min(255, round(c[1] * f))), max(0, min(255, round(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def cells(rng, n):
    """Tileable Voronoi seeds on a 16 x 16 tile."""
    return [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(n)]


def nearest(x, y, pts, metric):
    """The nearest and second-nearest seed distances (wrapping) under `metric(dx, dy)`, and the nearest seed's index."""
    best, second, bi = 1e9, 1e9, 0
    for i, (px, py) in enumerate(pts):
        dx = (x + 0.5 - px + 8) % 16 - 8
        dy = (y + 0.5 - py + 8) % 16 - 8
        d = metric(dx, dy)
        if d < best:
            second, best, bi = best, d, i
        elif d < second:
            second = d
    return best, second, bi


def end_grain(bark, wood, seed, rim=True):
    """A log's end: rings round the heart, the bark's rim, one check from the heart."""
    c = Canvas()
    rng = rng_for("end", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, (bark[3] if rng.random() < 0.6 else bark[2]) if rim else (wood[1] if rng.random() < 0.7 else wood[0]))
                continue
            d = math.hypot(x - 7.5, y - 7.5) + n[y][x] * 1.3
            ring = int(d) % 3 == 0
            idx = max(1, min(len(wood) - 1, len(wood) - 1 - int(d / 2.6) - (1 if ring else 0)))
            c.set(x, y, wood[idx])
    a = rng.random() * math.tau
    for k in range(2, 7):
        c.set(int(round(7.5 + math.cos(a) * k)), int(round(7.5 + math.sin(a) * k)), wood[0])
    return c


def stripped_wood(wood, seed):
    """Stripped: smooth heartwood with long grain."""
    c = Canvas()
    rng = rng_for("stripped", seed)
    cols = [rng.random() for _ in range(16)]
    n = value_noise(16, 16, rng, scale=2, octaves=2)
    for y in range(16):
        for x in range(16):
            c.set(x, y, wood[1 + quantize(0.6 * cols[x] + 0.4 * n[y][x], len(wood) - 1)])
    for _ in range(3):
        x, y0, ln = rng.randrange(16), rng.randrange(16), rng.randrange(4, 10)
        for y in range(y0, y0 + ln):
            c.set(x, y % 16, wood[0])
    return c


def boards(wood, seed):
    """Planks: four boards, lit top edges, dark bottom seams, a butt joint in each."""
    c = Canvas()
    rng = rng_for("boards", seed)
    for board in range(4):
        y0 = board * 4
        offset = rng.randrange(16)
        grain = value_noise(16, 4, rng_for("grain", seed, board), scale=2, octaves=2)
        for y in range(y0, y0 + 4):
            for x in range(16):
                idx = 1 + quantize(grain[y - y0][(x + offset) % 16], len(wood) - 1)
                if y == y0 + 3:
                    idx = 0
                elif y == y0:
                    idx = min(len(wood) - 1, idx + 1)
                c.set(x, y, wood[idx])
        jx = (offset + 6) % 16
        for y in range(y0, y0 + 3):
            c.set(jx, y, wood[0])
    return c


def glow_tile(ramp, weights, seed):
    """A solid glow tile the emissive faces sample (every mip level keeps its colour)."""
    c = Canvas()
    g = rp(ramp)
    rng = rng_for("glowtile", seed)
    for y in range(16):
        for x in range(16):
            c.set(x, y, g[rng.choices(range(len(weights)), weights=weights)[0]])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Frost Spires


def bluepine_bark(seed):
    """Scaled bark: plates of uneven size climbing the trunk, each a little offset from its neighbours, dark fissures
    breaking up between them (never a full grid), a silver frost caught on the lips of some."""
    c = Canvas()
    r = rp("bluepine_bark")
    rng = rng_for("bpbark", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    for y in range(16):
        for x in range(16):
            c.set(x, y, r[2 + quantize(n[y][x], 3)])
    x0 = 0
    while x0 < 16:
        w = rng.choice((2, 3, 3, 4, 5))
        y = rng.randrange(16)
        for _ in range(4):
            h = rng.choice((3, 4, 5, 6))
            tone = rng.random()
            for yy in range(y, y + h):
                for xx in range(x0, min(16, x0 + w)):
                    c.set(xx, yy % 16, r[2 + quantize(0.55 * tone + 0.45 * n[yy % 16][xx], 3)])
            # the fissure under the plate, broken; one down its side now and then
            for xx in range(x0, min(16, x0 + w)):
                if rng.random() < 0.75:
                    c.set(xx, (y + h - 1) % 16, r[0])
            if rng.random() < 0.5:
                for yy in range(y, y + h - 1):
                    c.set(x0, yy % 16, r[1])
            if rng.random() < 0.45:
                for xx in range(x0 + 1, min(16, x0 + w)):
                    c.set(xx, y % 16, r[6] if rng.random() < 0.6 else r[5])  # frost on the lip
            y += h
        x0 += w
    return c


def bluepine_needles(seed, holes=0.26):
    """Needles: short strokes leaning every way in deep ice-blue, lit from the top-left, frosted white at their tips,
    gaps to the sky."""
    c = Canvas()
    r = rp("bluepine_needle")
    frost = hex_rgba("#e4f3ff")
    rng = rng_for("bpneedle", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    filled = [[False] * 16 for _ in range(16)]
    for _ in range(70):
        x, y = rng.randrange(16), rng.randrange(16)
        dx, dy = rng.choice(((1, 1), (-1, 1), (1, 0), (0, 1), (1, -1)))
        ln = rng.choice((2, 3, 3, 4))
        base = 1 + quantize(n[y][x], len(r) - 2)
        for k in range(ln):
            px, py = (x + dx * k) % 16, (y + dy * k) % 16
            idx = base + (1 if k == 0 else 0)
            c.set(px, py, r[max(0, min(len(r) - 1, idx))])
            filled[py][px] = True
        if rng.random() < 0.35:
            c.set((x + dx * (ln - 1)) % 16, (y + dy * (ln - 1)) % 16, frost)
    for y in range(16):
        for x in range(16):
            if not filled[y][x]:
                c.set(x, y, r[0])
    gaps = sorted(((n[y][x] + rng.random() * 0.35, x, y) for y in range(16) for x in range(16) if not filled[y][x]))
    for _, x, y in gaps[: int(256 * holes)]:
        c.set(x, y, (0, 0, 0, 0))
    return c


def bluepine_sapling(seed):
    """A little tiered pine: a dark stem and three flat blue tiers, frosted on top."""
    c = Canvas()
    r, bk = rp("bluepine_needle"), rp("bluepine_bark")
    for y in range(15, 3, -1):
        c.set(7, y, bk[3] if y % 3 else bk[2])
    for ty, half in ((12, 5), (8, 4), (5, 2)):
        for x in range(7 - half, 8 + half):
            c.set(x, ty, r[5] if abs(x - 7) < half - 1 else r[4])
            c.set(x, ty + 1, r[3])
        c.set(7 - half, ty + 1, r[2])
        c.set(7 + half, ty + 1, r[2])
        for x in range(7 - half + 1, 7 + half, 2):
            c.set(x, ty - 1, hex_rgba("#e4f3ff"))
    c.set(7, 3, r[6])
    return c


def frostspar(seed):
    """Frostspar: ice grown into crystal. Long facets with straight edges in pale ice-blue, cool white where they meet,
    and two seams of the aurora's light running through it (the glow pixels: green, cyan and violet faces)."""
    c = Canvas()
    r = rp("frostspar")
    au = rp("aurora")
    rng = rng_for("frostspar", seed)
    pts = cells(rng, 6)
    tone = [rng.random() for _ in pts]
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            # an upright, stretched metric: facets run up the crystal (spires read tall even up close)
            best, second, bi = nearest(x, y, pts, lambda dx, dy: max(abs(dx), abs(dy) * 0.65))
            if second - best < 0.4:
                c.set(x, y, r[4] if (x + y) % 3 else hex_rgba("#f8fdff"))
                continue
            sheen = max(0.0, 1.0 - abs(((x - y) % 16) - 8.0) / 3.0)
            c.set(x, y, r[quantize(0.25 + 0.45 * tone[bi] + 0.12 * n[y][x] + 0.25 * sheen, len(r))])
    pix = []
    seen = set()
    for k in range(2):
        x = rng.randrange(2, 14)
        y = 0 if k == 0 else rng.randrange(4, 12)
        while y < 16 and len(pix) < 30:
            run = rng.randrange(3, 6)
            for _ in range(run):
                if y >= 16:
                    break
                if (x, y) not in seen:
                    seen.add((x, y))
                    pix.append((x, y))
                y += 1
            x = max(1, min(14, x + rng.choice((-1, 1))))
    for i, (x, y) in enumerate(pix):
        c.set(x, y, shade(au[(i // 4) % 3], 0.55))  # the seam dim in the base: it still reads where the faces are culled
    return c, pix


def frostspar_cluster(seed):
    """Slivers of frostspar standing in the snow (cross model): four thin needles splayed from one spot, ice-white, the
    aurora at their tips. Returns (base, glow layer)."""
    c, g = Canvas(), Canvas()
    r = rp("frostspar")
    au = rp("aurora")
    for bx, h, lean, w, tip in ((7, 13, 0.05, 2, 0), (4, 9, -0.4, 1, 1), (11, 10, 0.38, 1, 2), (9, 6, 0.2, 1, 0)):
        for i in range(h):
            y = 15 - i
            t = i / h
            half = max(0.5, w * (1 - t) * 0.5 + 0.3)
            cx = bx + lean * i
            for x in range(16):
                dx = x + 0.5 - cx
                if abs(dx) <= half:
                    c.set(x, y, r[5] if dx < 0 else r[3] if t < 0.6 else r[4])
            if i == h - 1:
                xi = int(cx)
                g.set(xi, y, au[tip])
                c.set(xi, y, shade(au[tip], 0.7))
    return c, g


def hoarfrost(seed):
    """Hoarfrost: frozen ground under feathers of frost. A pale ice-blue crust, darker where it lies thin, and two or
    three frost ferns (a stem with barbs at 45 degrees) in white, the brightest pixel at each stem's end."""
    c = Canvas()
    r = rp("hoarfrost")
    rng = rng_for("hoarfrost", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    for y in range(16):
        for x in range(16):
            c.set(x, y, r[quantize(0.75 * n[y][x] + 0.25 * rng.random(), len(r) - 1)])
    white = hex_rgba("#fbfeff")
    for _ in range(3):
        x, y = rng.randrange(16), rng.randrange(16)
        dx, dy = rng.choice(((1, 0), (0, 1), (1, 1), (1, -1)))
        ln = rng.randrange(5, 9)
        for k in range(ln):
            px, py = (x + dx * k) % 16, (y + dy * k) % 16
            c.set(px, py, r[5] if k < ln - 1 else white)
            if k % 2 == 1 and k < ln - 1:
                # barbs either side, swept toward the stem's end
                for side in (-1, 1):
                    bx, by = (px + (dy * side) + dx) % 16, (py - (dx * side) + dy) % 16
                    c.set(bx, by, r[4])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Mirror Marsh


def weepwood_bark(seed):
    """Smooth pale bark, silver going green in the damp: soft vertical tone, dark horizontal lenticels, one or two
    dark seams where it has split."""
    c = Canvas()
    r = rp("weepwood_bark")
    rng = rng_for("wwbark", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    cols = [rng.random() for _ in range(16)]
    moss = hex_rgba("#6f7f62")
    for y in range(16):
        for x in range(16):
            v = 0.55 * n[y][x] + 0.45 * cols[x]
            col = r[2 + quantize(v, len(r) - 2)]
            if n[y][x] < 0.22:
                col = mix(col, moss, 0.35)  # damp toward the foot of the tree
            c.set(x, y, col)
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        for dx in range(rng.choice((2, 3, 3))):
            c.set((x + dx) % 16, y, r[1])
        c.set(x % 16, (y + 1) % 16, r[5])
    for _ in range(rng.choice((1, 2))):
        x = rng.randrange(16)
        for y in range(16):
            if rng.random() < 0.18:
                x = (x + rng.choice((-1, 1))) % 16
            c.set(x, y, r[0])
    return c


def weepwood_leaves(seed, holes=0.27):
    """Long narrow leaves hanging down, silver-sage, the lit side pale, the shade under them deep; gaps between the
    strands."""
    c = Canvas()
    r = rp("weepwood_leaf")
    rng = rng_for("wwleaf", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    filled = [[False] * 16 for _ in range(16)]
    for _ in range(46):
        x, y = rng.randrange(16), rng.randrange(16)
        ln = rng.choice((3, 4, 4, 5))
        base = 1 + quantize(n[y][x], len(r) - 2)
        drift = rng.choice((0, 0, 1, -1))
        for k in range(ln):
            px, py = (x + (drift if k == ln - 1 else 0)) % 16, (y + k) % 16
            idx = base + (1 if k == 0 else 0) - (1 if k == ln - 1 else 0)
            c.set(px, py, r[max(0, min(len(r) - 1, idx))])
            filled[py][px] = True
        if rng.random() < 0.3:
            c.set(x % 16, y % 16, r[6])  # a silver underside catching the light
    for y in range(16):
        for x in range(16):
            if not filled[y][x]:
                c.set(x, y, r[0])
    gaps = sorted(((n[y][x] + rng.random() * 0.35, x, y) for y in range(16) for x in range(16) if not filled[y][x]))
    for _, x, y in gaps[: int(256 * holes)]:
        c.set(x, y, (0, 0, 0, 0))
    return c


def weepwood_sapling(seed):
    """A little weeping tree: a leaning pale stem, a low crown, strands hanging from it."""
    c = Canvas()
    r, bk, td = rp("weepwood_leaf"), rp("weepwood_bark"), rp("tendril")
    x = 6.0
    for y in range(15, 6, -1):
        x += 0.25
        c.set(int(round(x)), y, bk[4] if y % 2 else bk[3])
    top = int(round(x))
    for yy in range(4, 9):
        for xx in range(16):
            dx, dy = xx - top, (yy - 6) * 2.0
            if math.hypot(dx, dy) <= 5.2:
                c.set(xx, yy, r[5] if dy < 0 else r[3])
    for sx in range(top - 4, top + 5, 2):
        for k in range(2, 7 - abs(sx - top) // 2):
            c.set(sx, 8 + k, td[2] if k % 2 else td[3])
    return c


def tendrils(seed, tip=False):
    """Weepwood tendrils (cross model): thin strands with tiny paired leaflets; at the tip each ends in a pale catkin."""
    c = Canvas()
    td = rp("tendril")
    rng = rng_for("tendril", seed)
    for sx in (3, 7, 10, 13):
        x = sx
        end = 16 if not tip else rng.randrange(9, 13)
        for y in range(0, end):
            if rng.random() < 0.12:
                x = max(1, min(14, x + rng.choice((-1, 1))))
            c.set(x, y, td[1] if y % 3 else td[2])
            if y % 3 == 1:
                c.set(x - 1, y, td[3])
                c.set(x + 1, y + 1, td[2])
        if tip:
            for k in range(3):
                c.set(x, end + k, td[4] if k < 2 else td[3])
                if k == 1:
                    c.set(x - 1, end + k, td[3])
                    c.set(x + 1, end + k, td[3])
    return c


def moonbloom(seed):
    """A moonbloom from above: a round dark pad notched to its middle, a pale flower on it. Returns (base, glow layer:
    the petals)."""
    c, g = Canvas(), Canvas()
    pad, petal, glow = rp("lily_pad"), rp("moonbloom"), rp("moon_glow")
    rng = rng_for("moonbloom", seed)
    notch = rng.random() * math.tau
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            if d > 7.2 or (d > 1.5 and abs(((a - notch + math.pi) % math.tau) - math.pi) < 0.22):
                continue
            vein = abs(((a * 7 / math.tau) % 1) - 0.5) < 0.08 and d > 2
            c.set(x, y, pad[1] if vein else pad[2 + quantize(1 - d / 7.2 + 0.2 * rng.random(), 3)])
    # the flower: six petals round a pale heart
    for k in range(6):
        a = k * math.tau / 6 + 0.3
        for t in (1.2, 2.2, 3.0):
            px, py = int(round(7.5 + math.cos(a) * t)), int(round(7.5 + math.sin(a) * t))
            col = petal[3] if t < 2.5 else petal[2]
            c.set(px, py, col)
            g.set(px, py, glow[1] if t < 2.5 else glow[0])
    c.set(7, 7, petal[1])
    c.set(8, 8, petal[1])
    g.set(7, 7, glow[3])
    g.set(8, 8, glow[3])
    return c, g


def mirepeat_top(seed):
    """Mirepeat from above: dark wet peat, long threads of silver moss wandering over it (each keeps its heading and
    turns now and then, thinning at its ends), a few wet glints."""
    c = Canvas()
    p, m = rp("peat"), rp("peat_moss")
    rng = rng_for("peattop", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    for y in range(16):
        for x in range(16):
            c.set(x, y, p[quantize(0.8 * n[y][x] + 0.2 * rng.random(), len(p))])
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(16)
        dx, dy = rng.choice(((1, 0), (0, 1), (-1, 0), (0, -1)))
        ln = rng.randrange(8, 15)
        for k in range(ln):
            end = k < 2 or k > ln - 3
            c.set(x % 16, y % 16, m[1] if end else m[2 + (1 if rng.random() < 0.3 else 0)])
            if rng.random() < 0.3:
                dx, dy = (dy, dx) if rng.random() < 0.5 else (-dy, -dx)
            x += dx
            y += dy
    for _ in range(3):
        c.set(rng.randrange(16), rng.randrange(16), hex_rgba("#8fa3a8"))
    return c


def mirepeat_side(seed, moss=True):
    """Mirepeat's side: fibrous dark layers, the moss lip at the top (none on its bottom face)."""
    c = Canvas()
    p, m = rp("peat"), rp("peat_moss")
    rng = rng_for("peatside", seed)
    n = value_noise(16, 16, rng, scale=2, octaves=2)
    for y in range(16):
        for x in range(16):
            v = 0.6 * n[y][x] + 0.4 * ((y * 3 + rng.randrange(3)) % 5) / 5
            c.set(x, y, p[quantize(v, len(p))])
        if rng.random() < 0.3:
            for x in range(rng.randrange(16), 16, 3):
                c.set(x, y, p[0])
    if moss:
        for x in range(16):
            c.set(x, 0, m[2 if rng.random() < 0.6 else 1])
            if rng.random() < 0.5:
                c.set(x, 1, m[1])
            if rng.random() < 0.15:
                c.set(x, 2, m[0])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# What drifts in the air (white, tinted at spawn)


def snow_sprite(big):
    """A fleck of snow: a bright core, a soft edge."""
    c = Canvas(8, 8)
    rad = 2.4 if big else 1.6
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if d < rad:
                c.set(x, y, (255, 255, 255, round(255 * min(1.0, (1.0 - d / rad) * 1.8))))
    return c


def mist_sprite(seed):
    """A wisp of mist: a broad soft blob, uneven, its alpha falling off slowly (the particle multiplies it down)."""
    c = Canvas()
    rng = rng_for("mist", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 7.5, (y - 7.5) / 5.5)
            if d < 1.0:
                a = (1.0 - d) ** 1.6 * (0.6 + 0.4 * n[y][x])
                c.set(x, y, (255, 255, 255, round(255 * min(1.0, a * 1.4))))
    return c


# ---------------------------------------------------------------------------------------------------------------------

def textures():
    """Every texture: name -> (canvas, glow pixels or None). Particles are prefixed p_."""
    t = {}
    bb, bw = rp("bluepine_bark"), rp("bluepine_wood")
    t["bluepine_log"] = (bluepine_bark("bluepine"), None)
    t["bluepine_log_top"] = (end_grain(bb, bw, "bluepine"), None)
    t["stripped_bluepine_log"] = (stripped_wood(bw, "bluepine"), None)
    t["stripped_bluepine_log_top"] = (end_grain(bb, bw, "bluepine_s", rim=False), None)
    t["bluepine_planks"] = (boards(bw, "bluepine"), None)
    t["bluepine_leaves"] = (bluepine_needles("bluepine"), None)
    t["bluepine_sapling"] = (bluepine_sapling("bluepine"), None)
    for i in range(2):
        base, pix = frostspar(f"frostspar{i}")
        t[f"frostspar_{i}"] = (base, pix)
    t["frostspar_glow"] = (glow_tile("aurora", (4, 4, 3, 1, 2), "aurora"), None)
    base, glow = frostspar_cluster("cluster")
    t["frostspar_cluster"] = (base, None)
    t["frostspar_cluster_glow"] = (glow, None)
    for i in range(3):
        t[f"hoarfrost_{i}"] = (hoarfrost(f"hoarfrost{i}"), None)
    wb, ww = rp("weepwood_bark"), rp("weepwood_wood")
    t["weepwood_log"] = (weepwood_bark("weepwood"), None)
    t["weepwood_log_top"] = (end_grain(wb, ww, "weepwood"), None)
    t["stripped_weepwood_log"] = (stripped_wood(ww, "weepwood"), None)
    t["stripped_weepwood_log_top"] = (end_grain(wb, ww, "weepwood_s", rim=False), None)
    t["weepwood_planks"] = (boards(ww, "weepwood"), None)
    t["weepwood_leaves"] = (weepwood_leaves("weepwood"), None)
    t["weepwood_sapling"] = (weepwood_sapling("weepwood"), None)
    t["weepwood_tendrils"] = (tendrils("tendrils"), None)
    t["weepwood_tendrils_tip"] = (tendrils("tendrils_tip", tip=True), None)
    base, glow = moonbloom("moonbloom")
    t["moonbloom"] = (base, None)
    t["moonbloom_glow"] = (glow, None)
    for i in range(2):
        t[f"mirepeat_top_{i}"] = (mirepeat_top(f"peat{i}"), None)
    t["mirepeat"] = (mirepeat_side("peat", moss=False), None)
    t["mirepeat_bottom"] = (mirepeat_side("peat_b", moss=False), None)
    t["p_snow_0"] = (snow_sprite(True), None)
    t["p_snow_1"] = (snow_sprite(False), None)
    t["p_mist_0"] = (mist_sprite(0), None)
    t["p_mist_1"] = (mist_sprite(1), None)
    return t


_CACHE = {}


def glow_pixels(name):
    """The emissive pixels of a texture (x, y), for the content generator."""
    if not _CACHE:
        _CACHE.update(textures())
    return _CACHE[name][1] or []


def build(out):
    for name, (canvas, _) in textures().items():
        if name.startswith("p_"):
            out.particle(name[2:], canvas)
        else:
            out.block(name, canvas)


def preview(dest):
    """Each block tiled 3x3 at 8x, its glow pixels lit, on a mid-grey card; one sheet."""
    from PIL import Image, ImageDraw

    tex = textures()
    names = [k for k in tex if not k.startswith("p_") and not k.endswith("_glow")]
    tile = 16 * 3 * 4
    cols = 6
    rows = math.ceil(len(names) / cols)
    sheet = Image.new("RGBA", (cols * (tile + 16) + 16, rows * (tile + 34) + 16), (60, 64, 72, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        canvas, pix = tex[name]
        img = canvas.image()
        if pix:
            au = rp("aurora")
            for k, (x, y) in enumerate(pix):
                img.putpixel((x, y), au[(k // 4) % 3])
        if name + "_glow" in tex:
            img = Image.alpha_composite(img, tex[name + "_glow"][0].image())
        t = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
        for ty in range(3):
            for tx in range(3):
                t.paste(img, (tx * 16, ty * 16))
        t = t.resize((tile, tile), Image.NEAREST)
        x, y = 16 + (i % cols) * (tile + 16), 16 + (i // cols) * (tile + 34)
        sheet.alpha_composite(t, (x, y))
        d.text((x, y + tile + 4), name, fill=(255, 230, 120, 255))
    os.makedirs(dest, exist_ok=True)
    path = os.path.join(dest, "lands3_blocks.png")
    sheet.save(path)
    print(path)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print(__doc__)
