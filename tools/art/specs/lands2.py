"""Awakened Lands, the second pair (docs/modules/lands.md): Glass Steppe (Sunscorch) and Shattered Highlands (Crag).
Bleached glassbark with amber resin turned to glass, amber leaves, fused-glass ground with a crackle, sunshard crystal,
scorched glass for the Gate Scar's heart; faultstone split by light, the fault seam, riven shale; the glints, sparks and
dust that drift over them.

  python3 tools/art/build_all.py lands2          writes the textures
  python3 tools/art/specs/lands2.py preview <dir>  tiles each block 3x3 (as it sits in the world) into a contact sheet

Emissive parts are tiny solid model faces (tools/content/lands2_content.py turns each run of glow pixels into one face
sampling a solid glow tile), so nothing here needs alpha-tested overdraw.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from pixelart import Canvas, hex_rgba, quantize, rng_for, value_noise  # noqa: E402

RAMPS = {
    # Glass Steppe: bleached driftwood bark, honey heartwood, amber leaves, ivory fused glass, white-gold sunshard
    "glassbark_bark": ["#4f4a45", "#6e6862", "#918a82", "#b2aca2", "#cfcabf", "#e4e0d7", "#f4f2ec"],
    "glassbark_wood": ["#6b5434", "#8c7048", "#ad8f60", "#c8ab7a", "#dcc497", "#ecdab4"],
    "glassbark_leaf": ["#4a2205", "#6e3508", "#93500e", "#b86d16", "#d68b22", "#eaa93a", "#f8c95e"],
    "resin": ["#7a3c06", "#c8700f", "#f2a73a", "#ffd98a"],
    "fused": ["#a89c82", "#bdb197", "#cfc5ad", "#ddd5c1", "#e8e2d2", "#f2eee3"],
    "fused_crack": ["#6f5a3c", "#8a7250", "#a58c66"],
    "sunshard": ["#a07a3c", "#c9a05a", "#e2c68e", "#f1e2bf", "#f8f1e2", "#fdfbf6"],
    "sun_glow": ["#ff9a26", "#ffb648", "#ffd27a", "#fff0c4"],
    "scorched": ["#0f0a08", "#19110d", "#251a14", "#33251c", "#463428", "#5e4a3a"],
    # Shattered Highlands: violet-slate faultstone, the cyan-violet light in its cracks, blue-grey riven shale
    "faultstone": ["#1d1830", "#262040", "#302a50", "#3b3462", "#473f74", "#554c88"],
    "fault_glow": ["#5fe3f5", "#9ef4ff", "#d8fcff", "#b89cff", "#8a6cf0"],
    "shale": ["#1a1c24", "#232631", "#2d313e", "#383d4c", "#454b5b", "#545b6c"],
    "shale_rust": ["#3e2c27", "#553a31", "#6b4a3c"],
}


def rp(name):
    return [hex_rgba(c) for c in RAMPS[name]]


def shade(c, f):
    return (max(0, min(255, round(c[0] * f))), max(0, min(255, round(c[1] * f))), max(0, min(255, round(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ---------------------------------------------------------------------------------------------------------------------
# Glassbark


def glassbark(seed):
    """Bleached, sun-checked bark: long pale grain that wanders, short dark checks across it, and two or three beads of
    amber resin turned to glass (a lit pixel and a dark one under it)."""
    c = Canvas()
    r = rp("glassbark_bark")
    rng = rng_for("glassbark", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    cols = [rng.random() for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = 0.5 * n[y][x] + 0.5 * cols[(x + (1 if n[y][x] > 0.62 else 0)) % 16]
            c.set(x, y, r[1 + quantize(v, len(r) - 2)])
    # grain: thin darker lines running the length of the log, drifting a pixel now and then
    for gx in rng.sample(range(16), 5):
        x = gx
        for y in range(16):
            if rng.random() < 0.14:
                x = (x + rng.choice((-1, 1))) % 16
            c.set(x, y, r[1] if rng.random() < 0.55 else r[2])
            c.set((x - 1) % 16, y, shade(c.get((x - 1) % 16, y), 1.06))  # the lit lip left of each groove
    # sun checks: short horizontal splits
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(16)
        for dx in range(rng.choice((2, 3, 3, 4))):
            c.set((x + dx) % 16, y, r[0])
            c.set((x + dx) % 16, (y + 1) % 16, shade(r[4], 1.0))
    # amber resin beads, glassy
    res = rp("resin")
    for _ in range(3):
        x, y = rng.randrange(1, 15), rng.randrange(1, 14)
        c.set(x, y, res[3])
        c.set(x, y + 1, res[1])
        c.set(x + 1, y + 1, res[0])
    return c


def wood_top(bark_ramp, wood_ramp, seed, rim=True):
    """End grain: honey rings, a bleached rim."""
    c = Canvas()
    b, w = rp(bark_ramp), rp(wood_ramp)
    rng = rng_for("gtop", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, (b[4] if rng.random() < 0.6 else b[3]) if rim else (w[2] if rng.random() < 0.7 else w[1]))
                continue
            d = math.hypot(x - 7.5, y - 7.5) + n[y][x] * 1.3
            ring = int(d) % 3 == 0
            idx = len(w) - 1 - int(d / 2.6)
            idx = max(1, min(len(w) - 1, idx - (1 if ring else 0)))
            c.set(x, y, w[idx])
    # a radial check from the heart
    a = rng.random() * math.tau
    for k in range(2, 7):
        c.set(int(round(7.5 + math.cos(a) * k)), int(round(7.5 + math.sin(a) * k)), w[0])
    return c


def stripped(seed):
    c = Canvas()
    w = rp("glassbark_wood")
    rng = rng_for("gstripped", seed)
    cols = [rng.random() for _ in range(16)]
    n = value_noise(16, 16, rng, scale=2, octaves=2)
    for y in range(16):
        for x in range(16):
            v = 0.6 * cols[x] + 0.4 * n[y][x]
            c.set(x, y, w[2 + quantize(v, len(w) - 2)])
    for _ in range(3):
        x, y0, ln = rng.randrange(16), rng.randrange(16), rng.randrange(4, 10)
        for y in range(y0, y0 + ln):
            c.set(x, y % 16, w[1])
    res = rp("resin")
    x, y = rng.randrange(2, 14), rng.randrange(2, 14)
    c.set(x, y, res[2])
    c.set(x, y + 1, res[1])
    return c


def planks(seed):
    c = Canvas()
    w = rp("glassbark_wood")
    rng = rng_for("gplanks", seed)
    for board in range(4):
        y0 = board * 4
        offset = rng.randrange(16)
        grain = value_noise(16, 4, rng_for("ggrain", seed, board), scale=2, octaves=2)
        for y in range(y0, y0 + 4):
            for x in range(16):
                v = grain[y - y0][(x + offset) % 16]
                idx = 2 + quantize(v, len(w) - 2)
                if y == y0 + 3:
                    idx = 1
                elif y == y0:
                    idx = min(len(w) - 1, idx + 1)
                c.set(x, y, w[idx])
        jx = (offset + 6) % 16
        for y in range(y0, y0 + 3):
            c.set(jx, y, w[1])
    return c


def leaves(seed, holes=0.24):
    """Amber leaves: small clusters lit from the top-left, a few flakes catching the light like glass, gaps to the sky."""
    c = Canvas()
    r = rp("glassbark_leaf")
    rng = rng_for("gleaves", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    filled = [[False] * 16 for _ in range(16)]
    for _ in range(64):
        x, y = rng.randrange(16), rng.randrange(16)
        wdt, hgt = rng.choice(((1, 2), (2, 1), (2, 2), (2, 2), (3, 1)))
        base = 1 + quantize(n[y][x], len(r) - 2)
        for dy in range(hgt):
            for dx in range(wdt):
                px, py = (x + dx) % 16, (y + dy) % 16
                idx = base + (1 if dy == 0 and dx == 0 else 0) - (1 if dy == hgt - 1 and dx == wdt - 1 else 0)
                c.set(px, py, r[max(0, min(len(r) - 1, idx))])
                filled[py][px] = True
    for y in range(16):
        for x in range(16):
            if not filled[y][x]:
                c.set(x, y, r[0] if n[y][x] < 0.5 else r[1])
    cells = sorted(((n[y][x] + rng.random() * 0.35, x, y) for y in range(16) for x in range(16) if not filled[y][x]))
    for _, x, y in cells[: int(256 * holes)]:
        c.set(x, y, (0, 0, 0, 0))
    lit = [(x, y) for y in range(16) for x in range(16) if c.get(x, y)[3] > 0 and filled[y][x]]
    rng.shuffle(lit)
    for x, y in lit[:4]:
        c.set(x, y, hex_rgba("#ffe7ad"))
    return c


def sapling(seed):
    c = Canvas()
    lf, bk = rp("glassbark_leaf"), rp("glassbark_bark")
    rng = rng_for("gsapling", seed)
    x = 7.0
    for y in range(15, 6, -1):
        if y < 12 and rng.random() < 0.35:
            x += 0.7
        xi = int(round(x))
        c.set(xi, y, bk[5] if y % 3 else bk[4])
    top = int(round(x))
    for i in range(4):
        c.set(top - 1 - i, 8 - i // 2, bk[4])
    # two flat umbrella pads
    for cx, cy, rad in ((top + 2, 5, 3.6), (top - 5, 6, 2.6)):
        for yy in range(16):
            for xx in range(16):
                dx, dy = xx - cx, (yy - cy) * 2.2
                d = math.hypot(dx, dy)
                if d <= rad + rng.uniform(-0.4, 0.2):
                    lit = dy < 0 or dx < -1
                    c.set(xx, yy, lf[6] if lit and d > rad * 0.5 else lf[4] if lit else lf[3])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Glass ground


def _cells(rng, n, scale=16):
    """Tileable Voronoi seeds."""
    return [(rng.uniform(0, scale), rng.uniform(0, scale)) for _ in range(n)]


def _voronoi(x, y, pts, size=16):
    """Distances to the nearest and second-nearest seed (wrapping) and the nearest seed's index."""
    best = (1e9, -1)
    second = 1e9
    for i, (px, py) in enumerate(pts):
        dx = min(abs(x - px), size - abs(x - px))
        dy = min(abs(y - py), size - abs(y - py))
        d = math.hypot(dx, dy)
        if d < best[0]:
            second = best[0]
            best = (d, i)
        elif d < second:
            second = d
    return best[0], second, best[1]


def fused_glass(seed):
    """Sand melted to glass and cooled: an ivory glaze broken into a crackle of large plates. Each plate is glossy (a
    soft sheen runs across the tile, every plate darkens a little toward the crack on its lower-right edge, the depth
    of the glaze), the cracks are fine amber-brown lines with a bright lip, a few specks of pure light."""
    c = Canvas()
    r = rp("fused")
    cr = rp("fused_crack")
    rng = rng_for("fused", seed)
    pts = _cells(rng, 5)
    tones = [rng.random() for _ in pts]
    n = value_noise(16, 16, rng, scale=2, octaves=2)
    crack = [[False] * 16 for _ in range(16)]
    edge = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            d1, d2, i = _voronoi(x + 0.5, y + 0.5, pts)
            gap = d2 - d1
            if gap < 0.55:
                crack[y][x] = True
                continue
            edge[y][x] = gap
            sheen = max(0.0, 1.0 - abs(((x + 16 - y) % 16) - 6.0) / 4.0)  # a band of sky across the glaze
            depth = min(1.0, gap / 3.0)  # deeper toward the plate's middle
            v = 0.28 * tones[i] + 0.22 * n[y][x] + 0.25 * depth + 0.3 * sheen
            c.set(x, y, r[quantize(v, len(r))])
    for y in range(16):
        for x in range(16):
            if crack[y][x]:
                c.set(x, y, cr[0] if n[y][x] < 0.45 else cr[1])
                if not crack[(y + 1) % 16][x]:
                    c.set(x, (y + 1) % 16, r[5])  # the lip below a crack catches the light
    for _ in range(3):
        x, y = rng.randrange(16), rng.randrange(16)
        if not crack[y][x] and edge[y][x] > 1.2:
            c.set(x, y, hex_rgba("#ffffff"))
    return c


def sunshard(seed):
    """Sunshard: glass grown into crystal. Large flat facets with straight edges (cells of a stretched, slanted
    metric), each one tone lit by its slope, bright where two facets meet, an amber heart showing in the darker ones;
    the specks of the amber glow layer sit in those."""
    c = Canvas()
    g = Canvas()
    r = rp("sunshard")
    gl = rp("sun_glow")
    rng = rng_for("sunshard", seed)
    pts = _cells(rng, 6)
    tone = [rng.random() for _ in pts]
    warm = [rng.random() < 0.22 for _ in pts]
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    owner = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            best, second, bi = 1e9, 1e9, 0
            for i, (px, py) in enumerate(pts):
                dx = (x + 0.5 - px + 8) % 16 - 8
                dy = (y + 0.5 - py + 8) % 16 - 8
                # a slanted, stretched Chebyshev metric: long facets with straight edges
                u, v = dx * 0.8 + dy * 0.6, -dx * 0.6 + dy * 0.8
                d = max(abs(u) * 0.55, abs(v))
                if d < best:
                    second, best, bi = best, d, i
                elif d < second:
                    second = d
            owner[y][x] = bi
            if second - best < 0.45:
                c.set(x, y, hex_rgba("#f4fbff"))  # facet edges catch a cool white
                continue
            sheen = max(0.0, 1.0 - abs(((x + y) % 16) - 8.0) / 3.0)  # a soft streak of light across the crystal
            v = 0.3 + 0.45 * tone[bi] + 0.1 * n[y][x] + 0.25 * sheen
            col = r[quantize(v, len(r))]
            if warm[bi]:
                col = mix(col, gl[0], 0.28)
            c.set(x, y, col)
    cand = [(x, y) for y in range(16) for x in range(16) if warm[owner[y][x]] and c.get(x, y) != hex_rgba("#f4fbff")]
    rng.shuffle(cand)
    for x, y in cand[:3]:
        g.set(x, y, gl[1])
        c.set(x, y, shade(gl[0], 0.85))
    return c, g


def seed_float(seed):
    return (sum(ord(ch) for ch in str(seed)) % 97) / 15.0


def sunshard_cluster(seed):
    """Little shards of glass in the grass (cross model): three or four pointed slivers leaning out from one spot,
    white-gold with amber cores, their tips glowing faintly. Returns (base, glow layer)."""
    c, g = Canvas(), Canvas()
    r = rp("sunshard")
    gl = rp("sun_glow")
    rng = rng_for("cluster", seed)
    for bx, h, lean, w in ((5, 9, -0.35, 2), (8, 13, 0.12, 3), (11, 7, 0.45, 2), (3, 5, -0.6, 1)):
        for i in range(h):
            y = 15 - i
            t = i / h
            half = max(0.5, w * (1 - t) * 0.5 + 0.35)
            cx = bx + lean * i
            for x in range(16):
                dx = x + 0.5 - cx
                if abs(dx) <= half:
                    lit = dx < 0
                    idx = 5 if (lit and abs(dx) > half - 0.8) else 4 if lit else 2 if t < 0.5 else 3
                    c.set(x, y, r[idx])
            if i == h - 1:
                xi = int(cx)
                g.set(xi, y, gl[2])
                c.set(xi, y, shade(gl[1], 0.85))
            elif i == h // 2 and w > 1:
                xi = int(cx)
                g.set(xi, y, gl[1])
                c.set(xi, y, shade(gl[0], 0.8))
    return c, g


def scorched_glass(seed):
    """The heart of a Gate Scar: sand fused black and glassy, a sheen across it, split by cracks still holding the
    gate's amber light. Returns (base, crack pixels for the emissive faces)."""
    c = Canvas()
    r = rp("scorched")
    gl = rp("sun_glow")
    rng = rng_for("scorched", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    for y in range(16):
        for x in range(16):
            sheen = max(0.0, 1.0 - abs((x - y) * 0.5 + 2) / 3.0) * 0.4  # a diagonal glint across the glaze
            c.set(x, y, r[quantize(0.6 * n[y][x] + sheen + rng.random() * 0.12, len(r))])
    pix = zigzag(rng, 2, 14)
    for x, y in pix:
        c.set(x, y, shade(gl[0], 0.55))
    return c, pix


# ---------------------------------------------------------------------------------------------------------------------
# Faults


def zigzag(rng, cracks, length):
    """Crack pixels as jagged lines that travel across the face (so each straight run is one model face): `cracks`
    cracks of about `length` pixels, each running 3-6 pixels along its way, then stepping one aside (corner to corner)."""
    out = []
    seen = set()
    for k in range(cracks):
        along_x = (k % 2 == 0) == (rng.random() < 0.7)
        a, b = rng.randrange(0, 4), rng.randrange(3, 13)  # start near one edge
        d = 1
        if rng.random() < 0.5:
            a, d = 15 - a, -1
        left = length
        while left > 0 and 0 <= a < 16:
            run = min(left, rng.randrange(3, 7))
            for _ in range(run):
                if not 0 <= a < 16:
                    break
                p = (a, b) if along_x else (b, a)
                if p not in seen:
                    seen.add(p)
                    out.append(p)
                a += d
            left -= run
            # step one aside and carry on: the runs meet corner to corner, so every run stays a single face
            b = max(1, min(14, b + rng.choice((-1, 1))))
    return out


def faultstone(seed):
    """Dark violet-slate stone split by the light of the fault: (base, crack pixels). In the base the cracks are dim
    cyan with a violet bleed beside them, so they read even where the emissive faces are culled or too small to see."""
    c = Canvas()
    r = rp("faultstone")
    gl = rp("fault_glow")
    rng = rng_for("faultstone", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    for y in range(16):
        for x in range(16):
            v = 0.7 * n[y][x] + 0.3 * rng.random()
            c.set(x, y, r[quantize(v, len(r))])
    pix = zigzag(rng, 2, 13)
    s = set(pix)
    for x, y in pix:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in s and 0 <= q[0] < 16 and 0 <= q[1] < 16 and rng.random() < 0.6:
                c.set(q[0], q[1], mix(c.get(*q), gl[4], 0.45))
        c.set(x, y, shade(gl[0], 0.6))
    return c, pix


def fault_seam(seed):
    """The seam at the heart of a fault: crystal split open on light (the whole face is emissive). Angular facets in
    white-cyan, cyan and violet, brighter edges where they meet: it reads as a fissure full of light, not a lamp."""
    c = Canvas()
    gl = rp("fault_glow")
    rng = rng_for("seam", seed)
    pts = _cells(rng, 7)
    cols = [gl[2], gl[1], gl[1], gl[0], gl[0], mix(gl[0], gl[3], 0.6), gl[4]]
    rng.shuffle(cols)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            best, second, bi = 1e9, 1e9, 0
            for i, (px, py) in enumerate(pts):
                dx = min(abs(x + 0.5 - px), 16 - abs(x + 0.5 - px))
                dy = min(abs(y + 0.5 - py), 16 - abs(y + 0.5 - py))
                d = max(dx, dy) * 0.7 + (dx + dy) * 0.3
                if d < best:
                    second, best, bi = best, d, i
                elif d < second:
                    second = d
            if second - best < 0.5:
                c.set(x, y, gl[2])
            else:
                c.set(x, y, shade(cols[bi], 0.88 + 0.2 * n[y][x]))
    return c


def riven_shale(seed):
    """Riven shale, side: thin strata of blue-grey and slate with one rust band, each layer a little offset from the
    next, fine dark splits between them."""
    c = Canvas()
    r = rp("shale")
    ru = rp("shale_rust")
    rng = rng_for("shale", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    y = 0
    bands = []
    while y < 16:
        th = rng.choice((1, 2, 2, 3, 3, 4))
        bands.append((y, min(16, y + th), rng.random(), rng.random() < 0.12))
        y += th
    for y0, y1, tone, rust in bands:
        shift = rng.randrange(16)
        for y in range(y0, y1):
            for x in range(16):
                v = 0.55 * tone + 0.45 * n[y][(x + shift) % 16]
                if rust:
                    col = ru[quantize(v, len(ru))]
                else:
                    col = r[1 + quantize(v, len(r) - 1)]
                if y == y0:
                    col = shade(col, 1.12)  # the lit top of each layer
                c.set(x, y, col)
    # fine dark splits along the bottom of some layers, broken here and there
    for y0, y1, _, _ in bands:
        if y1 - y0 < 2:
            continue
        for x in range(16):
            if rng.random() < 0.45:
                c.set(x, y1 - 1, r[0])
    return c


def riven_shale_top(seed):
    """Riven shale end: the layers seen from above, plates of slate broken along straight splits."""
    c = Canvas()
    r = rp("shale")
    rng = rng_for("shale_top", seed)
    pts = _cells(rng, 5)
    tones = [rng.random() for _ in pts]
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            # Chebyshev cells: straight splits, angular plates
            best, second, bi = 1e9, 1e9, 0
            for i, (px, py) in enumerate(pts):
                dx = min(abs(x + 0.5 - px), 16 - abs(x + 0.5 - px))
                dy = min(abs(y + 0.5 - py), 16 - abs(y + 0.5 - py))
                d = max(dx, dy * 1.4)
                if d < best:
                    second, best, bi = best, d, i
                elif d < second:
                    second = d
            if second - best < 0.7:
                c.set(x, y, r[0])
            else:
                c.set(x, y, r[1 + quantize(0.6 * tones[bi] + 0.4 * n[y][x], len(r) - 1)])
    return c


def glow_tile(ramp, weights, seed):
    """A solid glow tile the emissive faces sample (every mip level keeps its colour)."""
    c = Canvas()
    g = rp(ramp)
    rng = rng_for("glowtile", seed)
    for y in range(16):
        for x in range(16):
            k = rng.choices(range(len(weights)), weights=weights)[0]
            c.set(x, y, g[k])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# What drifts in the air (white, tinted at spawn)


def glint_sprite(big):
    """A four-pointed star: sun caught on glass."""
    c = Canvas(8, 8)
    arm = 3 if big else 2
    for k in range(-arm, arm + 1):
        a = 255 if abs(k) <= 1 else 150
        c.set(3 + k, 3, (255, 255, 255, a))
        c.set(3, 3 + k, (255, 255, 255, a))
    for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        c.set(3 + dx, 3 + dy, (255, 255, 255, 90 if big else 60))
    c.set(3, 3, (255, 255, 255, 255))
    return c


def spark_sprite():
    """A spark: a hot core with a short soft fringe."""
    c = Canvas(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, (y - 3.5) * 0.8)
            if d < 2.6:
                c.set(x, y, (255, 255, 255, round(255 * min(1.0, (1.0 - d / 2.6) ** 1.2 * 1.6))))
    return c


def dust_sprite(seed):
    """A dust mote: a soft grain, irregular."""
    c = Canvas(8, 8)
    rng = rng_for("dust", seed)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5) / 3.0
            if d < 1.0 and rng.random() < 0.85:
                c.set(x, y, (255, 255, 255, round(200 * (1.0 - d) ** 1.4)))
    return c


# ---------------------------------------------------------------------------------------------------------------------

def textures():
    """Every texture: name -> (canvas, glow pixels or None). Particles are prefixed p_."""
    t = {}
    t["glassbark_log"] = (glassbark("glassbark"), None)
    t["glassbark_log_top"] = (wood_top("glassbark_bark", "glassbark_wood", "glassbark"), None)
    t["stripped_glassbark_log"] = (stripped("glassbark"), None)
    t["stripped_glassbark_log_top"] = (wood_top("glassbark_bark", "glassbark_wood", "glassbark_s", rim=False), None)
    t["glassbark_planks"] = (planks("glassbark"), None)
    t["glassbark_leaves"] = (leaves("glassbark"), None)
    t["glassbark_sapling"] = (sapling("glassbark"), None)
    for i in range(3):
        t[f"fused_glass_{i}"] = (fused_glass(f"fused{i}"), None)
    for i in range(2):
        base, glow = sunshard(f"sunshard{i}")
        t[f"sunshard_{i}"] = (base, [(x, y) for y in range(16) for x in range(16) if glow.get(x, y)[3] > 0])
    base, glow = sunshard_cluster("cluster")
    t["sunshard_cluster"] = (base, None)
    t["sunshard_cluster_glow"] = (glow, None)
    for i in range(3):
        base, pix = scorched_glass(f"scorched{i}")
        t[f"scorched_glass_{i}"] = (base, pix)
    for i in range(3):
        base, pix = faultstone(f"fault{i}")
        t[f"faultstone_{i}"] = (base, pix)
    t["fault_seam"] = (fault_seam("seam"), None)
    t["riven_shale"] = (riven_shale("shale"), None)
    t["riven_shale_top"] = (riven_shale_top("shale"), None)
    t["sunshard_glow"] = (glow_tile("sun_glow", (3, 4, 2, 0), "sun"), None)
    t["fault_glow"] = (glow_tile("fault_glow", (4, 5, 3, 2, 0), "fault"), None)
    t["p_glint_0"] = (glint_sprite(True), None)
    t["p_glint_1"] = (glint_sprite(False), None)
    t["p_fault_spark"] = (spark_sprite(), None)
    t["p_dust_0"] = (dust_sprite(0), None)
    t["p_dust_1"] = (dust_sprite(1), None)
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
    cols = 5
    rows = math.ceil(len(names) / cols)
    sheet = Image.new("RGBA", (cols * (tile + 16) + 16, rows * (tile + 34) + 16), (60, 64, 72, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        canvas, pix = tex[name]
        img = canvas.image()
        if pix:
            glow = "sun_glow" if name.startswith(("sunshard", "scorched")) else "fault_glow"
            col = hex_rgba(RAMPS[glow][1])
            for x, y in pix:
                img.putpixel((x, y), col)
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
    path = os.path.join(dest, "lands2_blocks.png")
    sheet.save(path)
    print(path)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print(__doc__)
