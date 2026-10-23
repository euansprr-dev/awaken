"""Power FX particle atlas (powers module, docs/modules/power-fx.md section 2) and its offline look test.

    python3 tools/art/build_all.py power_fx            textures/particle/power_fx_<name>_<i>.png + particles/power_fx_<name>.json
    python3 tools/art/specs/power_fx.py preview DIR    per-family sprite sheets and key frames of every signature effect

Every sprite is drawn as BANDS (0 = unlit, 1..4) on a small grid and only converted to pixels at the end, so falloff is
always hard-edged pixel art. Additive sprites: grey = band brightness, alpha 255 on every lit pixel (the additive render
type cuts alpha < 0.1). Alpha sprites: alpha = band coverage, grey = a light value ramp (lit / mid / shade) that the
vertex tint colours. Frames are always separate files suffixed _0.._n-1.

Grid convention: pixel (i, j) has its centre at (i, j); the centre of a 16 px sprite is (7.5, 7.5).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
if ART not in sys.path:
    sys.path.insert(0, ART)
from pixelart import Canvas, hex_rgba, rng_for, value_noise  # noqa: E402

ADD_V = (0, 76, 124, 184, 255)        # additive brightness per band
ALPHA_A = (0, 60, 120, 190, 255)      # alpha coverage per band
ALPHA_RGB = (235, 205, 170)           # alpha sprite value ramp: lit, mid, shade

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
N8 = N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))


def rnd(v):
    return int(math.floor(v + 0.5))


def bresenham(x0, y0, x1, y1):
    x0, y0, x1, y1 = rnd(x0), rnd(y0), rnd(x1), rnd(y1)
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        yield x0, y0
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def circle_pts(cx, cy, r):
    """Clean 1 px 8-connected circle around an integer or half-integer centre (octant walk, mirrored)."""
    half = abs((cx % 1) - 0.5) < 1e-6
    pts = set()
    u = 0.5 if half else 0.0
    while True:
        v = math.sqrt(max(0.0, r * r - u * u))
        vv = math.floor(v) + 0.5 if half else float(rnd(v))
        if vv < u - 1e-6:
            break
        for a, b in ((u, vv), (vv, u)):
            for sx in (-1, 1):
                for sy in (-1, 1):
                    pts.add((rnd(cx + sx * a), rnd(cy + sy * b)))
        u += 1.0
    return pts


def arc_pts(cx, cy, r, a0, a1):
    """Circle points between screen angles a0..a1 (degrees, -90 = up), ordered from a0 to a1."""
    out = []
    for x, y in circle_pts(cx, cy, r):
        a = math.degrees(math.atan2(y - cy, x - cx))
        while a < a0:
            a += 360
        if a <= a1:
            out.append((a, x, y))
    out.sort()
    return [(x, y, (a - a0) / max(1e-6, a1 - a0)) for a, x, y in out]


def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy + 1e-9)))
    return math.hypot(px - ax - t * vx, py - ay - t * vy)


class Bands:
    """A sprite in bands. v = band 0..4, t = tone 0..2 (alpha sprites only)."""

    def __init__(self, w, h=None):
        self.w, self.h = w, (h or w)
        self.v = [[0] * self.w for _ in range(self.h)]
        self.t = [[1] * self.w for _ in range(self.h)]

    def ok(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x, y):
        return self.v[y][x] if self.ok(x, y) else 0

    def put(self, x, y, lv, tone=None):
        x, y, lv = rnd(x), rnd(y), max(0, min(4, int(lv)))
        if self.ok(x, y) and lv > self.v[y][x]:
            self.v[y][x] = lv
            if tone is not None:
                self.t[y][x] = tone

    def force(self, x, y, lv, tone=None):
        x, y = rnd(x), rnd(y)
        if self.ok(x, y):
            self.v[y][x] = max(0, min(4, int(lv)))
            if tone is not None:
                self.t[y][x] = tone

    def line(self, x0, y0, x1, y1, lv, tone=None):
        for x, y in bresenham(x0, y0, x1, y1):
            self.put(x, y, lv, tone)

    def poly(self, pts, lv):
        for a, b in zip(pts, pts[1:]):
            self.line(*a, *b, lv)

    def grow(self, lv, src=None, diag=False, tone=None, where=None):
        """Every unlit pixel touching a pixel of band >= src gets band lv (one hard falloff step)."""
        src = lv + 1 if src is None else src
        nb = N8 if diag else N4
        new = [(x, y) for y in range(self.h) for x in range(self.w)
               if self.v[y][x] == 0 and (where is None or where(x, y))
               and any(self.get(x + dx, y + dy) >= src for dx, dy in nb)]
        for x, y in new:
            self.v[y][x] = lv
            if tone is not None:
                self.t[y][x] = tone
        return new

    def despeckle(self, maxlv=2):
        rm = [(x, y) for y in range(self.h) for x in range(self.w)
              if 0 < self.v[y][x] <= maxlv and not any(self.get(x + dx, y + dy) for dx, dy in N4)]
        for x, y in rm:
            self.v[y][x] = 0

    def lit(self):
        return [(x, y) for y in range(self.h) for x in range(self.w) if self.v[y][x]]

    def drop_crumbs(self, min_px=4):
        """Remove 4-connected islands smaller than min_px (keeps breakup frames from turning into confetti)."""
        seen = set()
        for y in range(self.h):
            for x in range(self.w):
                if not self.v[y][x] or (x, y) in seen:
                    continue
                comp, stack = [], [(x, y)]
                seen.add((x, y))
                while stack:
                    px, py = stack.pop()
                    comp.append((px, py))
                    for dx, dy in N4:
                        q = (px + dx, py + dy)
                        if self.ok(*q) and self.v[q[1]][q[0]] and q not in seen:
                            seen.add(q)
                            stack.append(q)
                if len(comp) < min_px:
                    for px, py in comp:
                        self.v[py][px] = 0

    def canvas(self, kind):
        c = Canvas(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                lv = self.v[y][x]
                if not lv:
                    continue
                if kind == "add":
                    g = ADD_V[lv]
                    c.set(x, y, (g, g, g, 255))
                else:
                    g = ALPHA_RGB[self.t[y][x]]
                    c.set(x, y, (g, g, g, ALPHA_A[lv]))
        return c


# ---------------------------------------------------------------------------------------------------------------------
# Additive sprites


def s_glow():
    b = Bands(16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            b.put(x, y, 4 if d <= 1.6 else 3 if d <= 3.3 else 2 if d <= 5.1 else 1 if d <= 7.05 else 0)
    return [b]


def s_spark():
    # f0: a rounded 4 px bulb leading a 2 px streak that thins to 1 px (a comet, not an arrowhead, once stretched)
    f0 = Bands(16)
    f0.put(7, 1, 3)
    f0.put(8, 1, 3)
    for y in (2, 3, 4):
        f0.put(6, y, 2 if y == 4 else 3)
        f0.put(9, y, 2 if y == 4 else 3)
    for y in range(2, 13):
        lv = 4 if y <= 7 else 3 if y <= 10 else 2
        f0.put(7, y, lv)
        f0.put(8, y, lv)
    for y, lv in ((13, 2), (14, 1), (15, 1)):
        f0.put(7, y, lv)
    f1 = Bands(16)
    f1.put(7, 1, 3)
    for y in range(2, 15):
        f1.put(7, y, 4 if y <= 6 else 3 if y <= 9 else 2 if y <= 12 else 1)
    return [f0, f1]


def s_flare():
    # (levels along the arm from the centre, how far the arm is 3 px wide, ring (radius, band), diagonal levels)
    spec = [((4, 4, 4, 3, 3, 2, 2, 1), 1, (3.0, 2), (3,)),
            ((4, 3, 3, 2, 2, 1), 0, (6.0, 1), (2,)),
            ((4, 3, 2, 1), 0, None, ()),
            ((3, 2), 0, None, ())]
    frames = []
    c = 7
    for levels, wide, ring, diag in spec:
        b = Bands(16)
        if ring:
            for p in circle_pts(c, c, ring[0]):
                b.put(*p, ring[1])
        for k, lv in enumerate(levels):
            for dx, dy in N4:
                b.put(c + dx * k, c + dy * k, lv)
                if 0 < k <= wide:
                    b.put(c + dx * k + dy, c + dy * k + dx, lv - 1)
                    b.put(c + dx * k - dy, c + dy * k - dx, lv - 1)
        for k, lv in enumerate(diag, start=1):
            for dx, dy in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                b.put(c + dx * k, c + dy * k, lv)
        frames.append(b)
    return frames


def s_bolt():
    frames = []
    for f in range(4):
        rng = rng_for("power_fx", "bolt", f)
        b = Bands(16)
        x, y = 7 + rng.choice((-2, -1, 1, 2)), 0
        pts = [(x, y)]
        dirn = rng.choice((-1, 1))
        while y < 15:
            ny = min(15, y + rng.choice((2, 3, 3, 4)))
            nx = max(2, min(13, x + dirn * rng.choice((2, 2, 3, 4))))
            pts.append((nx, ny))
            x, y, dirn = nx, ny, -dirn
        b.poly(pts, 4)
        # forks leave a corner of the zigzag on its outer side and run away from the channel
        corners = list(range(1, len(pts) - 1))
        rng.shuffle(corners)
        for i in corners[:1 + f % 2]:
            fx, fy = pts[i]
            fd = 1 if pts[i][0] > pts[i - 1][0] else -1
            for s in range(2):
                nx = max(0, min(15, fx + fd * 2))
                ny = min(15, fy + rng.choice((2, 3)))
                b.line(fx, fy, nx, ny, 3 - s)
                fx, fy = nx, ny
        b.grow(2, src=4)
        b.grow(1, src=3)
        frames.append(b)
    return frames


def s_wisp():
    frames = []
    for f in range(4):
        b = Bands(16)
        ph = f * math.pi / 2
        hx, hy = 7.5 + 0.7 * math.sin(ph), 4.0
        n = 56
        spine = []
        for i in range(n + 1):
            s = i / n
            x = hx + 3.3 * s ** 1.1 * math.sin(ph + 1.0 - s * 4.4)
            y = hy + 10.6 * s
            r = 1.8 * (1 - s) ** 0.8 + 0.3
            lv = 3 if s < 0.28 else 2 if s < 0.6 else 1
            for yy in range(int(y - r) - 1, int(y + r) + 2):
                for xx in range(int(x - r) - 1, int(x + r) + 2):
                    if math.hypot(xx - x, yy - y) <= r:
                        b.put(xx, yy, lv)
            spine.append((x, y, s))
        for x, y, s in spine:
            if s < 0.55:
                b.put(x, y, 4 if s < 0.3 else 3)
        for yy in range(16):
            for xx in range(16):
                d = math.hypot(xx - hx, yy - hy)
                b.put(xx, yy, 4 if d <= 1.15 else 3 if d <= 2.1 else 0)
        b.grow(1, src=3, where=lambda x, y: y <= 7)
        frames.append(b)
    return frames


def s_ember():
    frames = []
    b = Bands(8)
    for x in range(2, 5):
        for y in range(3, 6):
            b.put(x, y, 3 if (x in (2, 4) and y in (3, 5)) else 4)
    b.grow(2, src=4)
    b.grow(1, src=3)
    frames.append(b)
    b = Bands(8)
    for x in (3, 4):
        for y in (3, 4):
            b.put(x, y, 4)
    b.grow(2, src=4)
    frames.append(b)
    b = Bands(8)
    b.put(4, 3, 4)
    b.grow(2, src=4)
    frames.append(b)
    b = Bands(8)
    b.put(4, 2, 3)
    frames.append(b)
    return frames


def s_ring():
    b = Bands(16)
    pts = circle_pts(7.5, 7.5, 6.4)
    for p in pts:
        b.put(*p, 4)
    b.grow(1, src=4, where=lambda x, y: math.hypot(x - 7.5, y - 7.5) < 6.0)
    return [b]


RUNES = [  # (strokes at band 4, node dots at band 3)
    # bind: a dagaz bowtie between two posts, nodes above and below the crossing
    ([[(2, 2), (12, 12), (12, 2), (2, 12), (2, 2)]], [(7, 3), (7, 11)]),
    # gate: diamond with an inner stem
    ([[(7, 1), (13, 7), (7, 13), (1, 7), (7, 1)], [(7, 5), (7, 9)]], []),
    # circuit: a trace with a 45 degree bend, a branch, square terminal pads
    ([[(4, 3), (8, 3), (12, 7), (12, 10)], [(6, 3), (6, 8), (9, 11)], [(2, 2), (3, 2), (3, 4), (2, 4), (2, 2)],
      [(11, 12), (13, 12), (13, 14), (11, 14), (11, 12)]], [(10, 12)]),
    # eye: cut-corner box, centre dot, four rays
    ([[(6, 4), (8, 4), (10, 6), (10, 8), (8, 10), (6, 10), (4, 8), (4, 6), (6, 4)], [(7, 7), (7, 7)],
      [(7, 0), (7, 2)], [(7, 12), (7, 14)], [(0, 7), (2, 7)], [(12, 7), (14, 7)]], []),
]


def s_rune():
    frames = []
    for strokes, dots in RUNES:
        b = Bands(16)
        for s in strokes:
            b.poly(s, 4)
        for p in dots:
            b.put(*p, 3)
        frames.append(b)
    return frames


def s_hex():
    # pointy-top pixel hex with the classic 2:1 slopes; vertices are band 4 nodes, edges band 3
    b = Bands(16)
    half = [(8, 1), (9, 1), (10, 2), (11, 2), (12, 3), (13, 3), (14, 4), (14, 5), (14, 6), (14, 7)]
    for x, y in half:
        for px, py in ((x, y), (15 - x, y), (x, 15 - y), (15 - x, 15 - y)):
            b.put(px, py, 3)
    for p in ((7, 1), (8, 1), (14, 4), (14, 11), (7, 14), (8, 14), (1, 4), (1, 11)):
        b.force(*p, 4)
    for p in ((7, 3), (8, 3), (12, 5), (12, 10), (7, 12), (8, 12), (3, 5), (3, 10)):
        b.put(*p, 3)
    return [b]


def s_mote():
    f0 = Bands(8)
    for x in (3, 4):
        for y in (3, 4):
            f0.put(x, y, 4)
    f0.grow(2, src=4)
    f0.grow(1, src=2, diag=True, where=lambda x, y: 2 <= x <= 5 and 2 <= y <= 5)
    f1 = Bands(8)
    f1.put(3, 3, 4)
    f1.grow(2, src=4)
    f1.grow(1, src=2, diag=True, where=lambda x, y: 2 <= x <= 4 and 2 <= y <= 4)
    return [f0, f1]


SHARDS = [((3, 0), (5, 7), (1, 5)),
          ((0, 5), (7, 1), (4, 7)),
          ((7, 0), (4, 7), (1, 3)),
          ((2, 1), (7, 5), (0, 7))]


def _in_tri(px, py, a, b, c):
    def side(p, q, r):
        return (p[0] - r[0]) * (q[1] - r[1]) - (q[0] - r[0]) * (p[1] - r[1])
    d1, d2, d3 = side((px, py), a, b), side((px, py), b, c), side((px, py), c, a)
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))


def s_shard():
    # integer-vertex triangles: a band 3 fill, two lit rims at band 4, the rim facing down-right at band 3
    frames = []
    for tri in SHARDS:
        b = Bands(8)
        for x in range(8):
            for y in range(8):
                if _in_tri(x, y, *tri):
                    b.put(x, y, 3)
        cx, cy = sum(p[0] for p in tri) / 3, sum(p[1] for p in tri) / 3
        for i in range(3):
            p, q = tri[i], tri[(i + 1) % 3]
            mx, my = (p[0] + q[0]) / 2 - cx, (p[1] + q[1]) / 2 - cy
            lit = (mx + my) < 0.6
            for x, y in bresenham(*p, *q):
                b.put(x, y, 4 if lit else 3)
        for p in tri:
            b.force(*p, 4)
        frames.append(b)
    return frames


def s_slash():
    # crescent = outer disc minus a slightly larger disc pushed down: thick in the middle, needle tips, ~165 degrees
    frames = []
    cx, cy, R = 15.5, 22.5, 14.5
    for f, (k, broken) in enumerate(((2.6, False), (5.2, False), (4.6, True))):
        b = Bands(32)
        rng = rng_for("power_fx", "slash", f)
        ro = R + (0.8 if broken else 0.0)
        ri, ci = ro + 1.0, cy + k + 1.0
        gaps = ((-2.45, 0.13), (-1.75, 0.1), (-1.2, 0.14), (-0.62, 0.11))
        for y in range(32):
            for x in range(32):
                do = math.hypot(x - cx, y - cy)
                di = math.hypot(x - cx, y - ci)
                if do > ro or di <= ri:
                    continue
                depth = ro - do
                lv = 4 if depth < 1.25 else 3 if depth < 2.5 else 2 if depth < 3.7 else 1
                ang = abs(math.atan2(x - cx, -(y - cy)))
                if ang > 1.2:
                    lv -= 1
                if ang > 1.42:
                    lv -= 1
                if broken:
                    a = math.atan2(y - cy, x - cx)
                    if any(abs(a - g) < w for g, w in gaps):
                        continue
                    if depth > 1.8:
                        continue
                    lv -= 1
                if lv > 0:
                    b.put(x, y, max(1, lv))
        if f == 1:
            for x, y, s in arc_pts(cx, cy + 3.0, R - 5.0, -150, -30):
                if 0.1 < s < 0.9 and b.get(x, y) == 0:
                    b.put(x, y, 2 if 0.3 < s < 0.7 else 1)
        if broken:
            for g, w in gaps:
                r = ro + rng.uniform(0.6, 1.6)
                b.put(cx + math.cos(g) * r, cy + math.sin(g) * r, 2)
        frames.append(b)
    return frames


def _palm_mask():
    caps = [((11, 15), (10.6, 6.0), 1.55), ((15, 14), (15.2, 3.6), 1.55), ((19, 14.5), (19.8, 5.0), 1.55),
            ((22.8, 16.5), (24.2, 9.2), 1.5), ((10.6, 22.5), (5.4, 15.4), 1.75)]
    mask = set()
    for y in range(32):
        for x in range(32):
            # palm: rounded rect x 9.5..24.5, y 13.5..27, corner radius 3.5, plus a narrower wrist
            qx = max(abs(x - 17.0) - (7.5 - 3.5), 0.0)
            qy = max(abs(y - 20.5) - (6.8 - 3.5), 0.0)
            inside = math.hypot(qx, qy) <= 3.5
            inside |= 12.0 <= x <= 22.0 and 26 <= y <= 28.6
            for a, c, r in caps:
                if seg_dist(x, y, *a, *c) <= r:
                    inside = True
            if inside:
                mask.add((x, y))
    return mask


def s_palm():
    b = Bands(32)
    mask = _palm_mask()
    rim = {(x, y) for x, y in mask if any((x + dx, y + dy) not in mask for dx, dy in N4)}
    second = {(x, y) for x, y in mask - rim if any((x + dx, y + dy) in rim for dx, dy in N8)}
    for x, y in mask:
        b.put(x, y, 4 if (x, y) in rim else 2 if (x, y) in second else 1)
    # finger creases and a heart line: dark breaks that make it read as a print
    for x0, x1, y in ((10, 12, 10), (14, 16, 9), (18, 20, 9), (22, 24, 12), (12, 21, 17)):
        for x in range(x0, x1 + 1):
            if (x, y) in mask and (x, y) not in rim:
                b.force(x, y, 3 if y == 17 else 2)
    for x, y in ((13, 21), (14, 22), (15, 23), (16, 24)):
        b.force(x, y, 3)
    return [b]


def _wind_path():
    # a long shallow stroke that hooks up into an open curl at the right
    pts = []
    for i in range(41):
        s = i / 40
        pts.append((0.5 + 10.0 * s, 12.0 - 2.6 * math.sin(s * math.pi / 2)))
    for i in range(1, 31):
        s = i / 30
        a = math.pi / 2 - s * math.tau * 0.6
        r = 3.0 - 1.0 * s
        pts.append((10.5 + r * math.cos(a), 6.4 + r * math.sin(a)))
    return pts


def s_wind():
    frames = []
    main = _wind_path()
    lower = [(1.5 + 10.0 * i / 30, 14.4 - 1.2 * math.sin(i / 30 * math.pi * 0.8)) for i in range(31)]
    windows = ((0.0, 0.62), (0.14, 0.9), (0.4, 1.0))
    for f, (t0, t1) in enumerate(windows):
        b = Bands(16)
        for path, lag, drop in ((main, 0.0, 0), (lower, 0.2, 1)):
            n = len(path) - 1
            for i, (x, y) in enumerate(path):
                s = i / n - lag
                if not (t0 <= s <= t1):
                    continue
                u = (s - t0) / max(1e-6, t1 - t0)
                lv = (4 if u > 0.8 else 3 if u > 0.52 else 2 if u > 0.25 else 1) - drop
                if f == 2:
                    lv -= 1
                b.put(x, y, max(1, lv))
                if u > 0.82 and drop == 0:
                    b.put(x, y + 1, lv - 1)
        frames.append(b)
    return frames


MINI_RUNES = [((0, -1), (-1, 0), (1, 0), (0, 1), (0, 0)), ((-1, -1), (1, -1), (0, 0), (-1, 1), (1, 1)),
              ((-1, -1), (0, -1), (1, -1), (0, 0), (0, 1)), ((-1, 0), (0, -1), (1, 0), (0, 1))]


def s_glyph_circle():
    b = Bands(32)
    c = 15.5
    for p in circle_pts(c, c, 14.6):
        b.put(*p, 4)
    for p in circle_pts(c, c, 9.7):
        b.put(*p, 3)
    for i in range(16):
        if i % 4 == 2:
            continue
        a = i / 16 * math.tau
        r0 = 11.4 if i % 4 == 0 else 12.4
        b.line(c + math.cos(a) * r0, c + math.sin(a) * r0, c + math.cos(a) * 13.3, c + math.sin(a) * 13.3,
               3 if i % 4 == 0 else 2)
    for k, pat in enumerate(MINI_RUNES):
        a = math.radians(45 + 90 * k)
        rx, ry = rnd(c + math.cos(a) * 12.1), rnd(c + math.sin(a) * 12.1)
        for dx, dy in pat:
            b.put(rx + dx, ry + dy, 4)
    # inscribed diamond inside the inner ring, and a small centre node
    for x, y in bresenham(16, 7, 24, 15):
        for px, py in ((x, y), (31 - x, y), (x, 31 - y), (31 - x, 31 - y)):
            b.put(px, py, 2)
    for p in ((15, 15), (16, 15), (15, 16), (16, 16)):
        b.put(*p, 2)
    return [b]


# ---------------------------------------------------------------------------------------------------------------------
# Alpha sprites


def _blob_frames(size, blobs, frames, motion, ero, fade, tone_fn, noise_seed, floor=None):
    noise = value_noise(size, size, rng_for("power_fx", noise_seed, "n"), scale=3, octaves=2)
    out = []
    for f in range(frames):
        b = Bands(size)
        placed = [motion(f, *bl) for bl in blobs]
        gx = sum(p[0] for p in placed) / len(placed)
        gy = sum(p[1] for p in placed) / len(placed)
        gr = max(math.hypot(p[0] - gx, p[1] - gy) + p[2] for p in placed)

        def tone(x, y, cx, cy, r):
            # mostly one light for the whole puff (top-left), a little from each lump so it stays lumpy
            return tone_fn(x, y, gx + (cx - gx) * 0.35, gy + (cy - gy) * 0.35, gr * 0.65 + r * 0.35)
        for y in range(size):
            for x in range(size):
                if floor is not None and y > floor:
                    continue
                best, hit = 0.0, None
                for cx, cy, r in placed:
                    v = 1.0 - math.hypot(x - cx, y - cy) / r
                    if v > best:
                        best, hit = v, (cx, cy, r)
                if not hit:
                    continue
                v = best * fade[f] - ero[f] * noise[(y + 2 * f) % size][x]
                lv = 4 if v > 0.5 else 3 if v > 0.3 else 2 if v > 0.14 else 1 if v > 0.04 else 0
                if lv:
                    b.put(x, y, lv, tone(x, y, *hit))
        b.drop_crumbs(3 + f // 2)
        out.append(b)
    return out


def _tone_tl(x, y, cx, cy, r):
    light = -(0.55 * (x - cx) + 0.85 * (y - cy)) / r
    return 0 if light > 0.28 else 2 if light < -0.25 else 1


def s_smoke():
    rng = rng_for("power_fx", "smoke")
    blobs = [(0.0, 0.4, 4.4)]
    for k in range(6):
        a = k / 6 * math.tau + rng.uniform(-0.3, 0.3)
        d = rng.uniform(2.6, 3.5)
        blobs.append((math.cos(a) * d, math.sin(a) * d * 0.85, rng.uniform(2.2, 3.0)))

    def motion(f, bx, by, r):
        # lumps drift apart and up while each one thins, so the puff splits into separate wisps
        spread = 1.0 + 0.24 * f
        return 7.5 + bx * spread, 8.0 + by * spread - 0.4 * f, r * (1.0 + 0.04 * f - (0.05 * f if r < 4 else 0.0))
    return _blob_frames(16, blobs, 6, motion, (0.0, 0.08, 0.15, 0.22, 0.28, 0.32), (1.0, 0.94, 0.86, 0.76, 0.64, 0.52),
                        _tone_tl, "smoke")


def s_dust():
    lumps = [(7.5, 11.2, 4.0), (3.6, 12.4, 2.9), (11.6, 12.2, 3.0), (5.4, 8.6, 2.6), (9.9, 8.9, 2.4), (1.6, 13.6, 1.9),
             (13.8, 13.4, 1.9), (7.6, 7.0, 2.0)]

    def motion(f, lx, ly, r):
        rise = 0.42 * f * (1.4 - (ly - 7.0) / 7.0)
        return 7.5 + (lx - 7.5) * (1.0 + 0.15 * f), ly - rise, r * (1.0 + 0.05 * f)

    def tone(x, y, cx, cy, r):
        ny = (y - cy) / r
        return 0 if ny < -0.35 else 2 if (ny > 0.35 or y >= 13) else 1
    return _blob_frames(16, lumps, 6, motion, (0.0, 0.06, 0.13, 0.2, 0.26, 0.3), (1.0, 0.95, 0.88, 0.78, 0.66, 0.54),
                        tone, "dust", floor=14)


def s_crack():
    b = Bands(32)
    c = 15.5
    rng = rng_for("power_fx", "crack")
    # shattered centre: the edges of a few Voronoi cells, with faintly darkened chips between them
    seeds = [(c + math.cos(a) * r, c + math.sin(a) * r)
             for a, r in [(k / 5 * math.tau + rng.uniform(-0.4, 0.4), rng.uniform(1.2, 2.6)) for k in range(5)]]
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - c, y - c)
            if r > 3.9:
                continue
            ds = sorted(math.hypot(x - sx, y - sy) for sx, sy in seeds)
            if ds[1] - ds[0] < 0.8:
                b.put(x, y, 4, 2)
            elif r < 3.2:
                b.put(x, y, 1, 1)
    n = 8
    for i in range(n):
        a0 = i / n * math.tau + rng.uniform(-0.25, 0.25)
        a = a0
        x, y = c + math.cos(a) * 3.2, c + math.sin(a) * 3.2
        reach = rng.uniform(9.5, 14.8)
        r = 3.2
        forked = False
        while r < reach:
            a = a0 * 0.35 + (a + rng.uniform(-0.5, 0.5)) * 0.65
            step = rng.uniform(1.8, 3.0)
            nx, ny = x + math.cos(a) * step, y + math.sin(a) * step
            lv = 4 if r < 7.5 else 3 if r < 11.0 else 2
            b.line(x, y, nx, ny, lv, 2)
            if r < 5.0:
                ox, oy = (0, 1) if abs(math.cos(a)) > abs(math.sin(a)) else (1, 0)
                b.line(x + ox, y + oy, nx + ox, ny + oy, lv - 1, 2)
            if not forked and 6.0 < r < 10.5 and rng.random() < 0.45:
                forked = True
                fa = a + rng.choice((-1, 1)) * rng.uniform(0.5, 0.8)
                fx, fy = nx, ny
                for s in range(2):
                    gx, gy = fx + math.cos(fa) * 2.4, fy + math.sin(fa) * 2.4
                    b.line(fx, fy, gx, gy, 3 - s, 2)
                    fx, fy, fa = gx, gy, fa + rng.uniform(-0.3, 0.3)
            x, y = nx, ny
            r = math.hypot(x - c, y - c)
    for yy in range(32):
        for xx in range(32):
            if math.hypot(xx - c, yy - c) > 15.4:
                b.v[yy][xx] = 0
    return [b]


def s_scorch():
    b = Bands(32)
    c = 15.5
    rng = rng_for("power_fx", "scorch")
    p = [rng.uniform(0, math.tau) for _ in range(4)]
    noise = value_noise(32, 32, rng, scale=4, octaves=3)
    for y in range(32):
        for x in range(32):
            th = math.atan2(y - c, x - c)
            rad = 10.4 + 1.6 * math.sin(3 * th + p[0]) + 1.0 * math.sin(5 * th + p[1]) + 0.6 * math.sin(11 * th + p[2])
            r = math.hypot(x - c, y - c) / rad
            v = 1.0 - r + (noise[y][x] - 0.5) * 0.4 + 0.13 * math.cos(13 * th + p[3]) * r
            if v > 0.62:
                b.put(x, y, 4, 2)
            elif v > 0.4:
                b.put(x, y, 3, 2)
            elif v > 0.2:
                b.put(x, y, 2, 1)
            elif v > 0.05:
                b.put(x, y, 1, 0)
    b.despeckle(1)
    for _ in range(16):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(11.5, 14.8)
        b.put(c + math.cos(a) * r, c + math.sin(a) * r, rng.choice((1, 2, 2)), 1)
    return [b]


def s_pool():
    b = Bands(32)
    c = 15.5
    rng = rng_for("power_fx", "pool")
    blobs = [(c, c, 5.0)]
    for k in range(6):
        a = k / 6 * math.tau + rng.uniform(-0.35, 0.35)
        d = rng.uniform(2.6, 4.4)
        blobs.append((c + math.cos(a) * d, c + math.sin(a) * d * 0.9, rng.uniform(2.2, 3.4)))
    pool = set()
    for y in range(32):
        for x in range(32):
            if any(math.hypot(x - bx, y - by) <= r for bx, by, r in blobs):
                pool.add((x, y))
    rim = {(x, y) for x, y in pool if any((x + dx, y + dy) not in pool for dx, dy in N4)}
    for x, y in pool:
        b.put(x, y, 3 if (x, y) in rim else 4, 1 if (x, y) in rim else 2)
    # tendrils: smooth wandering strokes leaving the pool edge, thinning to a point
    for k in range(8):
        a = k / 8 * math.tau + rng.uniform(-0.3, 0.3)
        r = max((math.hypot(x - c, y - c) for x, y in rim
                 if abs((math.atan2(y - c, x - c) - a + math.pi) % math.tau - math.pi) < 0.25), default=6.0)
        x, y = c + math.cos(a) * (r - 1.0), c + math.sin(a) * (r - 1.0)
        length = rng.uniform(6.5, 10.0)
        curl = rng.uniform(-0.12, 0.12)
        steps = int(length * 2)
        for s in range(steps):
            u = s / steps
            a += curl * (1 + 2 * u)
            x, y = x + math.cos(a) * 0.5, y + math.sin(a) * 0.5
            lv = 4 if u < 0.25 else 3 if u < 0.55 else 2 if u < 0.8 else 1
            b.put(x, y, lv, 1)
            if u < 0.45:
                b.put(x + math.sin(a) * 0.9, y - math.cos(a) * 0.9, lv, 1)
                b.put(x - math.sin(a) * 0.6, y + math.cos(a) * 0.6, lv, 1)
    for _ in range(7):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(11.0, 14.0)
        x, y = c + math.cos(a) * r, c + math.sin(a) * r
        b.put(x, y, 2, 1)
        if rng.random() < 0.4:
            b.put(x + 1, y, 2, 1)
    # glossy highlight on the far inner edge
    for x, y in ((11, 10), (12, 10), (13, 9), (10, 11)):
        if (x, y) in pool:
            b.force(x, y, 4, 0)
    for yy in range(32):
        for xx in range(32):
            if math.hypot(xx - c, yy - c) > 15.4:
                b.v[yy][xx] = 0
    return [b]


# ---------------------------------------------------------------------------------------------------------------------
# Registry and build

SPEC = {  # name: (kind, size, frames, maker)
    "glow": ("add", 16, 1, s_glow), "spark": ("add", 16, 2, s_spark), "flare": ("add", 16, 4, s_flare),
    "bolt": ("add", 16, 4, s_bolt), "wisp": ("add", 16, 4, s_wisp), "ember": ("add", 8, 4, s_ember),
    "ring": ("add", 16, 1, s_ring), "rune": ("add", 16, 4, s_rune), "hex": ("add", 16, 1, s_hex),
    "mote": ("add", 8, 2, s_mote), "shard": ("add", 8, 4, s_shard), "slash": ("add", 32, 3, s_slash),
    "palm": ("add", 32, 1, s_palm), "wind": ("add", 16, 3, s_wind), "glyph_circle": ("add", 32, 1, s_glyph_circle),
    "smoke": ("alpha", 16, 6, s_smoke), "dust": ("alpha", 16, 6, s_dust), "decal_crack": ("alpha", 32, 1, s_crack),
    "decal_scorch": ("alpha", 32, 1, s_scorch), "decal_pool": ("alpha", 32, 1, s_pool),
}

_IMAGES = {}


def sprite_images():
    """name -> (kind, [PIL RGBA frames]). Built once per process."""
    if not _IMAGES:
        for name, (kind, size, n, maker) in SPEC.items():
            frames = maker()
            assert len(frames) == n and all(f.w == size and f.h == size for f in frames), name
            _IMAGES[name] = (kind, [f.canvas(kind).image() for f in frames])
    return _IMAGES


def build(out):
    pdir = os.path.join(out.root, "assets/awaken/particles")
    os.makedirs(pdir, exist_ok=True)
    for name, (kind, frames) in sprite_images().items():
        for i, im in enumerate(frames):
            c = Canvas(im.width, im.height)
            c.px = [[im.getpixel((x, y)) for x in range(im.width)] for y in range(im.height)]
            out.particle(f"power_fx_{name}_{i}", c)
        with open(os.path.join(pdir, f"power_fx_{name}.json"), "w") as fh:
            json.dump({"textures": [f"awaken:power_fx_{name}_{i}" for i in range(len(frames))]}, fh, indent=2)
            fh.write("\n")


# ---------------------------------------------------------------------------------------------------------------------
# Preview: palettes

PAL = {
    "kinetic":   ("#FFF1D6", "#F2A541", "#B26A12", "#9A8A72"),
    "velocity":  ("#FFFFFF", "#FF4A3D", "#A8262F", "#FF8A7A"),
    "flight":    ("#E6F6FF", "#A7CDE3", "#6B98B8", "#DCDFE8"),
    "gravity":   ("#E0D0FF", "#9A6BFF", "#4A2A9A", "#0D0A17"),
    "energy":    ("#FFFFFF", "#7FC3FF", "#3C86CC", "#FF9A3C"),
    "shadow":    ("#0D0A17", "#8F6BFF", "#231A3B", "#C3B0FF"),
    "mind":      ("#FFE6F6", "#D088C2", "#73367C", "#FFD27A"),
    "architect": ("#FFD28A", "#D9BA80", "#937646", "#7FC3FF"),
    "tech":      ("#E6FFFA", "#47C6B0", "#17564E", "#7FC3FF"),
    "spirit":    ("#F0FFF5", "#86E0A0", "#3F8F6E", "#9FB0FF"),
}
# tints for the alpha sprites in the sprite sheets: (smoke/dust, decal)
ALPHA_TINT = {
    "kinetic": ("#9A8A72", "#2A2017"), "velocity": ("#B9A9A6", "#2A1A1A"), "flight": ("#DCDFE8", "#3A4250"),
    "gravity": ("#6E6488", "#0D0A17"), "energy": ("#9FB4C8", "#1E1712"), "shadow": ("#0D0A17", "#0D0A17"),
    "mind": ("#B898B4", "#2A1530"), "architect": ("#B8975E", "#2A2418"), "tech": ("#8FA8A4", "#0F2420"),
    "spirit": ("#B8D8C6", "#13261E"),
}
HEAT = ("#FFF4D0", "#FF9A3C", "#C44A12", "#FFC15A")
SOUL = "#9FB0FF"
EDGE_A = 0.45          # opacity of the edge-colour underlay drawn under additive sprites
EDGE_LINE = 0.7        # ... and under thin additive lines (rings, beams, ribbons, bolts), which wash out first by day


class Pal:
    def __init__(self, cols):
        self.core, self.mid, self.edge, self.accent = (C(c) for c in cols)
        # an additive core must be bright; shadow's ink core falls back to its accent
        self.acore = self.core if float(self.core.mean()) > 0.5 else self.accent


def C(h):
    import numpy as np
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], np.float32)


def _font(size=13):
    from PIL import ImageFont
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def _np(im):
    import numpy as np
    return np.asarray(im.convert("RGBA"), np.float32) / 255.0


_XF = {}


def _xform(name, frame, wpx, hpx, rot=0.0, flip=False):
    """Sprite frame resampled nearest-neighbour to wpx x hpx and rotated rot degrees (counter-clockwise)."""
    from PIL import Image
    kind, frames = sprite_images()[name]
    wpx, hpx = max(1, rnd(wpx)), max(1, rnd(hpx))
    key = (name, frame % len(frames), wpx, hpx, round(rot, 1), flip)
    if key not in _XF:
        im = frames[frame % len(frames)].resize((wpx, hpx), Image.NEAREST)
        if flip:
            im = im.transpose(Image.FLIP_TOP_BOTTOM)
        if rot:
            im = im.rotate(rot, Image.NEAREST, expand=True)
        _XF[key] = _np(im)
    return _XF[key]


# ---------------------------------------------------------------------------------------------------------------------
# Preview: figures (dark slate player, mob) drawn at 1.5 px per model unit (48 px = 2 blocks)

FIG_COLS = {"player": ("#2c3240", "#1f232d", "#4f5a72", "#0c0e14"), "mob": ("#2c3d2d", "#1f2b20", "#4c6a50", "#0b110b")}
FIG_ANCHOR = (32, 60)
_FIG = {}


def figure_img(pose, who="player"):
    import numpy as np
    from PIL import Image, ImageDraw
    key = (pose, who)
    if key in _FIG:
        return _FIG[key]
    body, shade, rim, outline = (hex_rgba(c) for c in FIG_COLS[who])
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ax, ay = FIG_ANCHOR

    def R(x0, y0, x1, y1, col):
        d.rectangle((ax + x0, ay + y0, ax + x1, ay + y1), fill=col)

    def L(pts, w, col):
        d.line([(ax + x, ay + y) for x, y in pts], fill=col, width=w, joint="curve")

    def P(pts, col):
        d.polygon([(ax + x, ay + y) for x, y in pts], fill=col)
    face = tuple(min(255, int(c * 1.35)) for c in body[:3]) + (255,)

    def head(x0, y0):
        R(x0, y0, x0 + 11, y0 + 11, body)
        R(x0 + 7, y0 + 4, x0 + 10, y0 + 6, face)   # the face side, so the figure reads as facing +x
    # Steve proportions at 1.5 px per unit: head 12, torso 12 x 18, arms and legs 6 x 18 (slightly slimmed)
    if pose in ("stand", "cast", "palm", "mob"):
        R(-6, -18, -1, -1, shade)
        R(0, -18, 5, -1, body)
        d.line((ax, ay - 18, ax, ay - 1), fill=shade)
        if pose == "mob":
            R(-10, -35, 8, -31, shade)
        else:
            R(-11, -36, -7, -19, shade)
        R(-6, -36, 5, -19, body)
        d.line((ax - 6, ay - 18, ax + 5, ay - 18), fill=shade)
        if pose != "mob":
            d.line((ax - 7, ay - 35, ax - 7, ay - 19), fill=outline)
        if pose == "stand":
            R(7, -36, 11, -19, body)
            d.line((ax + 6, ay - 35, ax + 6, ay - 19), fill=outline)
        elif pose == "mob":
            R(4, -35, 15, -31, body)
        else:
            R(4, -36, 17, -32, body)
            if pose == "palm":
                R(17, -39, 19, -29, body)
            else:
                R(17, -37, 19, -31, body)
        head(-6, -48)
    elif pose == "slam":
        L([(-3, -13), (-9, -7), (-7, -1)], 6, shade)
        L([(2, -13), (8, -8), (7, -1)], 6, body)
        P([(-7, -29), (5, -31), (7, -13), (-5, -12)], body)
        L([(-5, -28), (-12, -36)], 5, shade)
        L([(4, -28), (12, -5)], 6, body)
        R(9, -6, 15, -1, body)
        head(-2, -42)
    elif pose == "dash":
        L([(-2, -17), (-12, -3)], 6, shade)
        L([(1, -17), (8, -10), (7, -1)], 6, body)
        L([(-3, -33), (-15, -26)], 5, shade)
        R(-6, -36, 5, -18, body)
        L([(2, -33), (-10, -24)], 5, body)
        head(-6, -48)
    elif pose == "hover":
        L([(-3, -18), (-5, -6), (-3, -1)], 6, shade)
        L([(2, -18), (1, -6), (3, -2)], 6, body)
        L([(-7, -34), (-14, -22)], 5, shade)
        R(-6, -36, 5, -18, body)
        L([(6, -34), (13, -22)], 5, body)
        head(-6, -48)
    if pose == "dash":
        im = im.rotate(-14, Image.NEAREST, center=FIG_ANCHOR)
    a = np.asarray(im).copy()
    m = a[..., 3] > 0
    # rim light on top/front edges, then a 1 px dark outline
    up = np.zeros_like(m)
    up[1:, :] = m[1:, :] & ~m[:-1, :]
    fr = np.zeros_like(m)
    fr[:, :-1] = m[:, :-1] & ~m[:, 1:]
    a[up | fr] = rim
    o = np.zeros_like(m)
    o[1:, :] |= m[:-1, :]
    o[:-1, :] |= m[1:, :]
    o[:, 1:] |= m[:, :-1]
    o[:, :-1] |= m[:, 1:]
    a[o & ~m] = outline
    _FIG[key] = a.astype(np.float32) / 255.0
    return _FIG[key]


# ---------------------------------------------------------------------------------------------------------------------
# Preview: the scene (3/4 view orthographic camera, 24 px per block)


class Scene:
    U = 24.0
    SQ = 0.35
    YAW = math.radians(22)

    def __init__(self, day, ox, oy, w=400, h=250, horizon=150, seed="fx", ground=True):
        import numpy as np
        self.np = np
        self.day, self.ox, self.oy, self.w, self.h, self.hz = day, ox, oy, w, h, horizon
        self.img = np.zeros((h, w, 3), np.float32)
        self.add = np.zeros((h, w, 3), np.float32)
        self.uc = np.zeros((h, w, 3), np.float32)
        self.ua = np.zeros((h, w), np.float32)
        self.solid = np.zeros((h, w), bool)      # figures and debris, for the impact frame
        ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
        self.xs, self.ys = xs + 0.5, ys + 0.5
        self.c, self.s = math.cos(self.YAW), math.sin(self.YAW)
        self.queue = []
        self.gx, self.gz = self.plane(0.0)
        self._background(seed, ground)

    # -- camera
    def proj(self, x, y, z):
        return (self.ox + self.U * (x * self.c + z * self.s), self.oy - self.U * y + self.U * self.SQ * (z * self.c - x * self.s))

    def depth(self, x, z):
        return z * self.c - x * self.s

    def plane(self, y):
        X = (self.xs - self.ox) / self.U
        Zs = (self.ys - self.oy + self.U * y) / (self.U * self.SQ)
        return X * self.c - Zs * self.s, X * self.s + Zs * self.c

    def fig_point(self, fx, fz, dx, dy, fy=0.0):
        """World point under a figure pixel offset (dx, dy) from its feet, at the figure's depth."""
        x = fx + dx / (self.U * self.c)
        sy0 = self.proj(fx, fy, fz)[1]
        y = (self.oy + self.U * self.SQ * (fz * self.c - x * self.s) - (sy0 + dy)) / self.U
        return (x, y, fz)

    # -- background
    def _background(self, seed, ground):
        np = self.np
        hz = self.hz
        if self.day:
            self.img[:] = C("#87b5e8")
            self.img[max(0, hz - 16):hz] = C("#97c0ec")
            self.img[max(0, hz - 6):hz] = C("#a8cbf0")
        else:
            self.img[:] = C("#0b0f1c")
            rng = rng_for("fx-stars", seed)
            for _ in range(46):
                x, y = rng.randrange(self.w), rng.randrange(max(1, hz - 6))
                self.img[y, x] = C("#2a3352") if rng.random() < 0.75 else C("#56628a")
        if not ground:
            return
        gx, gz = self.gx, self.gz
        ix, iz = np.floor(gx).astype(np.int64), np.floor(gz).astype(np.int64)
        th = ((ix * 73856093) ^ (iz * 19349663)) & 1023
        sub = ((np.floor(gx * 4).astype(np.int64) * 83492791) ^ (np.floor(gz * 4).astype(np.int64) * 2654435761)) & 255
        if self.day:
            base = [C("#567d3f"), C("#5f8746"), C("#668e4b")]
            stone = [C("#77757d"), C("#84828b"), C("#6c6a72")]
        else:
            base = [C("#1b1c22"), C("#202128"), C("#25262e")]
            stone = [C("#17181d"), C("#1d1e24"), C("#22232a")]
        col = np.zeros_like(self.img)
        for k in range(3):
            col[(th % 3) == k] = base[k]
        st = (th % 9) == 0
        for k in range(3):
            col[st & ((sub % 3) == k)] = stone[k]
        col[(sub % 11) == 0] *= 1.12
        col[(sub % 13) == 1] *= 0.86
        fx, fz = gx - np.floor(gx), gz - np.floor(gz)
        col[(fx < 0.045) | (fz < 0.1)] *= 0.86
        g = self.ys >= hz
        self.img[g] = col[g]
        self.img[hz:hz + 2] = C("#3f6232") if self.day else C("#111216")

    # -- queued opaque / alpha billboards (painter's order by depth)
    def q(self, depth, fn):
        self.queue.append((depth, len(self.queue), fn))

    def flush(self):
        for _, _, fn in sorted(self.queue, key=lambda e: (e[0], e[1])):
            fn()
        self.queue = []

    def _slices(self, arr, x0, y0):
        h, w = arr.shape[:2]
        X0, Y0, X1, Y1 = max(0, x0), max(0, y0), min(self.w, x0 + w), min(self.h, y0 + h)
        if X0 >= X1 or Y0 >= Y1:
            return None
        return (slice(Y0, Y1), slice(X0, X1)), arr[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0]

    def _over(self, sl, rgb, a):
        self.img[sl] = self.img[sl] * (1 - a[..., None]) + rgb * a[..., None]

    def _under(self, sl, m, col, op):
        a = m.astype(self.np.float32) * op
        self.uc[sl] = col * a[..., None] + self.uc[sl] * (1 - a[..., None])
        self.ua[sl] = a + self.ua[sl] * (1 - a)

    def figure(self, x, z, pose, who="player", facing=1, y=0.0, tint=None, opacity=1.0, queue=True):
        np = self.np
        arr = figure_img(pose, who)
        if facing < 0:
            arr = arr[:, ::-1]
        sx, sy = self.proj(x, y, z)
        ax = FIG_ANCHOR[0] if facing > 0 else arr.shape[1] - 1 - FIG_ANCHOR[0]

        def draw():
            r = self._slices(arr, rnd(sx) - ax, rnd(sy) - FIG_ANCHOR[1])
            if not r:
                return
            sl, a = r
            al = a[..., 3] * opacity
            rgb = a[..., :3] if tint is None else np.broadcast_to(tint, a[..., :3].shape)
            self._over(sl, rgb, al)
            if tint is None:
                self.solid[sl] |= al > 0.5
        if y > 0.05 and tint is None:
            self.shadow(x, z, 0.45)
        self.q(self.depth(x, z), draw) if queue else draw()

    def shadow(self, x, z, r, op=0.35):
        np = self.np
        d = np.hypot(self.gx - x, self.gz - z)
        m = (d < r) & (self.ys >= self.hz)
        self.img[m] *= (1 - op)

    def chunk(self, x, y, z, s, col):
        np = self.np
        col = C(col) if isinstance(col, str) else col
        sx, sy = self.proj(x, y, z)
        s = max(2, int(s))
        tile = np.empty((s, s, 3), np.float32)
        tile[:] = col
        tile[0, :] = tile[:, 0] = np.minimum(1, col * 1.22)
        tile[-1, :] = tile[:, -1] = col * 0.55

        def draw():
            r = self._slices(np.dstack([tile, np.ones((s, s, 1), np.float32)]), rnd(sx - s / 2), rnd(sy - s / 2))
            if r:
                sl, a = r
                self._over(sl, a[..., :3], a[..., 3])
                self.solid[sl] = True
        if y > 0.1:
            self.shadow(x, z, 0.18, 0.3)
        self.q(self.depth(x, z), draw)

    def alpha_sprite(self, name, frame, pos, size, tint, opacity=1.0, sw=1.0, rot=0.0, flip=False, queue=True):
        sx, sy = self.proj(*pos)
        px = size * self.U
        arr = _xform(name, frame, px * sw, px, rot, flip)

        def draw():
            r = self._slices(arr, rnd(sx - arr.shape[1] / 2), rnd(sy - arr.shape[0] / 2))
            if r:
                sl, a = r
                self._over(sl, a[..., :3] * tint, a[..., 3] * opacity)
        self.q(self.depth(pos[0], pos[2]), draw) if queue else draw()

    def decal(self, name, frame, x, z, R, tint, opacity=1.0, add=False, gain=1.0, rot=0.0, edge=None):
        np = self.np
        tex = _np(sprite_images()[name][1][frame])
        S = tex.shape[0]
        u, v = (self.gx - x) / R, (self.gz - z) / R
        if rot:
            cr, sr = math.cos(rot), math.sin(rot)
            u, v = u * cr - v * sr, u * sr + v * cr
        ti, tj = np.floor((u + 1) / 2 * S).astype(np.int64), np.floor((v + 1) / 2 * S).astype(np.int64)
        valid = (ti >= 0) & (ti < S) & (tj >= 0) & (tj < S) & (self.ys >= self.hz)
        t = tex[np.clip(tj, 0, S - 1), np.clip(ti, 0, S - 1)]
        sl = (slice(0, self.h), slice(0, self.w))
        if add:
            m = valid & (t[..., 3] >= 0.1)
            self.add += t[..., :3] * tint * gain * m[..., None]
            if edge is not None:
                self._under(sl, m, edge, EDGE_A * min(1.0, gain))
        else:
            self._over(sl, t[..., :3] * tint, t[..., 3] * valid * opacity)

    # -- additive primitives (accumulated, drawn after every edge underlay)
    def add_sprite(self, name, frame, pos, size, tint, gain=1.0, rot=0.0, stretch=1.0, sw=1.0, edge=None, flip=False,
                   screen=None):
        sx, sy = screen if screen else self.proj(*pos)
        px = size * self.U
        arr = _xform(name, frame, px * sw, px * stretch, rot, flip)
        r = self._slices(arr, rnd(sx - arr.shape[1] / 2), rnd(sy - arr.shape[0] / 2))
        if not r:
            return
        sl, a = r
        m = a[..., 3] >= 26 / 255
        self.add[sl] += a[..., :3] * tint * gain * m[..., None]
        if edge is not None:
            self._under(sl, m, edge, EDGE_A * min(1.0, gain))

    def glow2(self, name, frame, pos, size, pal, gain=1.0, rot=0.0, stretch=1.0, sw=1.0, edge=True, mid=None, core=None,
              screen=None):
        """The game's two-layer additive particle: mid colour at full size, core colour at 45% on top."""
        mid = pal.mid if mid is None else mid
        core = pal.acore if core is None else core
        self.add_sprite(name, frame, pos, size, mid, gain, rot, stretch, sw, pal.edge if edge is True else edge,
                        screen=screen)
        self.add_sprite(name, frame, pos, size * 0.45, core, gain, rot, stretch, sw, screen=screen)

    def add_mask(self, m, col, gain=1.0, edge=None, edge_mask=None, ea=EDGE_A):
        self.add += m[..., None] * col * gain
        if edge is not None:
            em = (m > 0.02) if edge_mask is None else edge_mask
            self._under((slice(0, self.h), slice(0, self.w)), em, edge, ea * min(1.0, gain * float(m.max())))

    def dir_rot(self, v):
        """Sprite rotation (degrees) that points a sprite's top along world direction v on screen."""
        a = self.proj(*v)
        o = self.proj(0, 0, 0)
        return math.degrees(math.atan2(-(a[1] - o[1]), a[0] - o[0])) - 90

    def strokes(self, segs, width):
        """segs: [((x0, y0), (x1, y1), gain)] in screen px -> float mask (max of gains)."""
        from PIL import Image, ImageDraw
        np = self.np
        out = np.zeros((self.h, self.w), np.float32)
        groups = {}
        for a, b, g in segs:
            if g > 0.01:
                groups.setdefault(round(g * 8) / 8, []).append((a, b))
        for g, ss in groups.items():
            im = Image.new("L", (self.w, self.h), 0)
            d = ImageDraw.Draw(im)
            for a, b in ss:
                d.line([a, b], fill=255, width=max(1, rnd(width)))
                if width > 2:
                    r = width / 2 - 0.5
                    d.ellipse((b[0] - r, b[1] - r, b[0] + r, b[1] + r), fill=255)
            out = np.maximum(out, np.asarray(im, np.float32) / 255.0 * g)
        return out

    def ribbon(self, pts3d, width, col, core=None, g0=1.0, g1=1.0, back=0.45, ref=None, edge=None):
        """Polyline ribbon in world space: body band + 1 px core; segments behind ref depth are dimmer."""
        n = len(pts3d) - 1
        sp = [self.proj(*p) for p in pts3d]
        segs = []
        for i in range(n):
            g = g0 + (g1 - g0) * i / max(1, n - 1)
            if ref is not None:
                p = pts3d[i]
                if self.depth(p[0], p[2]) < ref[1] + (p[0] - ref[0]) * 0.0 - 1e-6:
                    g *= back
            segs.append((sp[i], sp[i + 1], g))
        m = self.strokes(segs, width)
        self.add_mask(m, col, 1.0, edge=edge, ea=EDGE_LINE)
        if core is not None:
            self.add_mask(self.strokes(segs, 1), core, 0.9)

    def ring3d(self, c, r, u, v, col, gain=1.0, width=3, core=None, back=0.45, edge=None, n=96):
        pts = [(c[0] + r * (math.cos(a) * u[0] + math.sin(a) * v[0]), c[1] + r * (math.cos(a) * u[1] + math.sin(a) * v[1]),
                c[2] + r * (math.cos(a) * u[2] + math.sin(a) * v[2])) for a in (i / n * math.tau for i in range(n + 1))]
        ref = self.depth(c[0], c[2])
        segs = []
        for i in range(n):
            p = pts[i]
            g = gain * (back if self.depth(p[0], p[2]) < ref - 1e-6 else 1.0)
            segs.append((self.proj(*p), self.proj(*pts[i + 1]), g))
        self.add_mask(self.strokes(segs, width + 2), col, 0.35)
        self.add_mask(self.strokes(segs, width), col, 1.0, edge=edge, edge_mask=self.strokes(segs, width + 2) > 0.02,
                      ea=EDGE_LINE)
        if core is not None:
            self.add_mask(self.strokes(segs, 1), core, 0.9)

    def ground_ring(self, x, z, r_in, r_out, col, gain=1.0, core=None, edge=None, y=0.0):
        np = self.np
        gx, gz = (self.gx, self.gz) if y == 0.0 else self.plane(y)
        d = np.hypot(gx - x, gz - z)
        t = (d - r_in) / max(1e-6, r_out - r_in)
        inside = (t >= 0) & (t <= 1)
        prof = np.clip(1 - np.abs(2 * t - 1), 0, 1)
        q = np.ceil(prof * 4) / 4 * inside
        q = np.where(q > 0.76, 1.0, np.where(q > 0.51, 0.62, np.where(q > 0.26, 0.36, q * 0.8)))
        self.add_mask(q.astype(np.float32), col, gain, edge=edge, edge_mask=inside)
        if core is not None:
            m = ((t > 0.62) & (t < 0.86)).astype(np.float32)
            self.add_mask(m, core, gain * 0.85)

    def beam(self, p0, p1, widths, cols, gains, edge=None, screen=False):
        np = self.np
        (ax, ay), (bx, by) = (p0, p1) if screen else (self.proj(*p0), self.proj(*p1))
        px, py = self.xs - ax, self.ys - ay
        vx, vy = bx - ax, by - ay
        t = np.clip((px * vx + py * vy) / (vx * vx + vy * vy + 1e-9), 0, 1)
        d = np.hypot(px - t * vx, py - t * vy)
        for w, col, g in zip(widths, cols, gains):
            self.add_mask((d <= w / 2).astype(np.float32), col, g)
        if edge is not None:
            self._under((slice(0, self.h), slice(0, self.w)), d <= widths[0] / 2 + 1, edge, EDGE_LINE * min(1.0, max(gains)))

    def shell(self, pos, r, rim, fill, gain=1.0, edge=None, fill_gain=0.22):
        np = self.np
        cx, cy = self.proj(*pos)
        d = np.hypot(self.xs - cx, self.ys - cy) / (r * self.U)
        m = np.where(d < 0.72, fill_gain, np.where(d < 0.86, fill_gain * 1.7, 0.0)).astype(np.float32)
        self.add_mask(m * (d <= 1), fill, gain)
        rm = np.where((d >= 0.86) & (d < 0.94), 0.7, np.where((d >= 0.94) & (d <= 1.0), 1.0, 0.0)).astype(np.float32)
        self.add_mask(rm, rim, gain)
        if edge is not None:
            self._under((slice(0, self.h), slice(0, self.w)), (d >= 0.84) & (d <= 1.06), edge, EDGE_A * min(1.0, gain))

    def disc(self, pos, r, col, opacity=1.0):
        np = self.np
        cx, cy = self.proj(*pos)
        d = np.hypot(self.xs - cx, self.ys - cy) / (r * self.U)
        a = np.where(d <= 0.82, 1.0, np.where(d <= 1.0, 0.7, 0.0)).astype(np.float32) * opacity
        self._over((slice(0, self.h), slice(0, self.w)), np.broadcast_to(col, self.img.shape), a)

    def lightning(self, p0, p1, rng, col, gain=1.0, jag=0.3, segs=7, branch=0.5, edge=None, screen=False):
        (ax, ay), (bx, by) = (p0, p1) if screen else (self.proj(*p0), self.proj(*p1))
        L = math.hypot(bx - ax, by - ay) + 1e-6
        nx, ny = -(by - ay) / L, (bx - ax) / L
        pts = [(ax, ay)]
        sign = rng.choice((-1, 1))
        for i in range(1, segs):
            t = i / segs
            o = sign * rng.uniform(0.3, 1.0) * jag * L / segs * 2.2
            sign = -sign
            pts.append((ax + (bx - ax) * t + nx * o, ay + (by - ay) * t + ny * o))
        pts.append((bx, by))
        lines = list(zip(pts, pts[1:]))
        if rng.random() < branch and len(pts) > 3:
            i = rng.randrange(1, len(pts) - 2)
            sx, sy = pts[i]
            ang = math.atan2(by - ay, bx - ax) + rng.choice((-1, 1)) * rng.uniform(0.5, 0.9)
            for _ in range(2):
                ex, ey = sx + math.cos(ang) * L / segs * 1.3, sy + math.sin(ang) * L / segs * 1.3
                lines.append(((sx, sy), (ex, ey)))
                sx, sy, ang = ex, ey, ang + rng.uniform(-0.5, 0.5)
        segl = [(a, b, gain) for a, b in lines]
        self.add_mask(self.strokes(segl, 3), col, 0.75, edge=edge, ea=EDGE_LINE)
        self.add_mask(self.strokes(segl, 1), C("#ffffff"), 1.0)

    def finish(self, scale=2):
        from PIL import Image
        np = self.np
        self.flush()
        out = self.uc + self.img * (1 - self.ua[..., None])
        out = np.clip(out + self.add, 0, 1)
        im = Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGB")
        return im.resize((self.w * scale, self.h * scale), Image.NEAREST) if scale != 1 else im


# ---------------------------------------------------------------------------------------------------------------------
# Signature effects. Each draws one key frame t (0..1) into a scene. Positions in blocks; the player stands at the origin
# facing +x unless noted.

def ease_out(t):
    return 1 - (1 - min(1.0, max(0.0, t))) ** 2


STONES = ("#6a6870", "#84828b", "#74502a", "#93693a", "#525057")


def fx_kinetic(sc, t):
    P = Pal(PAL["kinetic"])
    R = 4.5
    imp = (0.55, 0.0)
    sc.figure(0, 0, "slam")
    sc.decal("decal_crack", 0, imp[0], imp[1], 2.6, C("#231a12"), opacity=1.0 if t < 0.7 else 0.85)
    if t < 0.7:
        sc.decal("decal_crack", 0, imp[0], imp[1], 2.6, P.mid, add=True, gain=0.95 - t)
    e = ease_out(t / 0.9)
    r = R * (0.25 + 0.75 * e)
    w = 1.6 * (1 - 0.72 * t)
    sc.ground_ring(imp[0], imp[1], max(r * 0.45, r - w), r, P.mid, gain=1.05 - 0.7 * t, core=P.core, edge=P.edge)
    fi = 0 if t < 0.25 else 2 if t < 0.6 else 3
    sc.glow2("flare", fi, (imp[0], 0.3, imp[1]), 3.0 if fi == 0 else 1.4, P, gain=1.0)
    if t < 0.6:
        sc.glow2("glow", 0, (imp[0], 0.3, imp[1]), 2.2 * (1.1 - t), P, gain=0.9 - t)
    rng = rng_for("fx", "kinetic", "debris")
    T = 1.25
    tau = t * T
    for i in range(14):
        a = i / 14 * math.tau + rng.uniform(-0.2, 0.2)
        sp = rng.uniform(2.2, 4.4)
        vy = rng.uniform(3.2, 6.2)
        rr = 0.7 + sp * tau
        y = max(0.0, vy * tau - 5.0 * tau * tau)
        size = rng.choice((4, 5, 6, 7))
        sc.chunk(imp[0] + math.cos(a) * rr, y, imp[1] + math.sin(a) * rr, size if y > 0 else size - 1, rng.choice(STONES))
    df = min(5, int(t * 6.6))
    for k in range(11):
        a = k / 11 * math.tau + 0.2
        rr = r * 0.92
        size = 1.0 + 1.1 * t + (k % 3) * 0.15
        sc.alpha_sprite("dust", df, (imp[0] + math.cos(a) * rr, size * 0.42, imp[1] + math.sin(a) * rr), size,
                        C("#9A8A72"), opacity=0.9)


def fx_velocity(sc, t):
    P = Pal(PAL["velocity"])
    x0, x1 = -3.6, 3.4

    def px(tt):
        return x0 + (x1 - x0) * min(1.0, max(0.0, tt) / 0.62) ** 0.62
    x = px(t)
    fwd = sc.dir_rot((1, 0, 0))
    if t < 0.7:
        rg = (1.0 - t) ** 1.5
        sc.ring3d((x0 + 0.5, 1.0, 0), 0.8 + 0.9 * t, (0, 1, 0), (0, 0, 1), P.mid, gain=rg, width=3, core=P.core,
                  edge=P.edge)
    if t < 0.3:
        sc.glow2("flare", 0, (x0 + 0.5, 1.0, 0), 1.8, P, gain=0.9)
    for i in range(3):
        xa = px(t - 0.07 * (i + 1))
        if xa - x0 < 0.3 or x - xa < 0.25:
            continue
        op = (0.6 - i * 0.15) * (1.0 - 0.75 * max(0.0, t - 0.5) / 0.5)
        sc.figure(xa, 0, "dash", tint=P.mid if i == 0 else P.edge, opacity=op)
    rng = rng_for("fx", "velocity", "lines")
    for k in range(12):
        lx = rng.uniform(x0, max(x0 + 0.5, x - 0.4))
        ly = rng.uniform(0.15, 2.0)
        lz = rng.uniform(-0.9, 0.9)
        ln = rng.uniform(1.0, 2.6) * (1.0 - 0.4 * t)
        g = (0.95 if t < 0.6 else 0.45) * rng.uniform(0.6, 1.0)
        fr = 0 if (t < 0.6 and k % 3 == 0) else 1
        sc.glow2("spark", fr, (lx, ly, lz), 0.32, P, gain=g, rot=fwd, stretch=ln / 0.32)
    fade = 1.0 if t < 0.6 else 0.4
    xe = x - 0.1
    for k, (yb, amp, w) in enumerate(((0.18, 0.14, 4), (0.55, 0.2, 2))):
        pts = []
        n = 26
        for i in range(n + 1):
            u = i / n
            pts.append((x0 + (xe - x0) * u, yb + amp * math.sin(u * 9 + t * 8 + k * 2) * (1 - u * 0.3),
                        0.35 * math.sin(u * 5 + k)))
        sc.ribbon(pts, w, P.accent, core=P.core if k == 0 else None, g0=0.0, g1=0.9 * fade, edge=P.edge)
    sc.figure(x, 0, "dash" if t < 0.7 else "stand")


def fx_flight(sc, t):
    P = Pal(PAL["flight"])
    y0 = 0.35 if t < 0.3 else 1.15 if t < 0.6 else 1.35
    sc.figure(0, 0, "hover", y=y0)
    for k in range(2):
        pts = []
        n = 44
        for i in range(n + 1):
            u = i / n
            a = u * math.tau * 1.25 + t * 9 + k * math.pi
            rr = 0.85 - 0.4 * u
            pts.append((math.cos(a) * rr, max(0.05, y0 - 0.75 + u * 1.3), math.sin(a) * rr))
        sc.ribbon(pts, 3, P.mid, core=P.core, g0=0.3, g1=1.0, ref=(0, 0), back=0.35, edge=P.edge)
    wf = int(t * 7) % 3
    for k, (dx, dy, dz, rot) in enumerate(((-0.9, -0.4, 0.4, 10), (0.9, -0.1, -0.3, -15), (-0.7, 0.5, -0.5, 25),
                                          (0.8, -0.7, 0.5, -5))):
        sc.glow2("wind", (wf + k) % 3, (dx, y0 + dy, dz), 1.15, P, gain=0.95, rot=rot)
    pf = 1 if t < 0.3 else 3 if t < 0.6 else 4
    rng = rng_for("fx", "flight", "puffs")
    for k in range(6):
        a = k / 6 * math.tau + rng.uniform(-0.3, 0.3)
        rr = (0.5 if t < 0.3 else 1.1 if t < 0.6 else 1.6) * rng.uniform(0.8, 1.2)
        size = 0.9 + 0.5 * t + rng.uniform(0, 0.3)
        sc.alpha_sprite("smoke", pf, (math.cos(a) * rr, size * 0.35, math.sin(a) * rr), size, P.accent * 0.98,
                        opacity=0.85 if t < 0.6 else 0.6)


def fx_gravity(sc, t):
    P = Pal(PAL["gravity"])
    c = (6.0, 1.15, 0.0)
    sc.figure(0, 0, "cast")
    core_r = 0.32 if t < 0.3 else 0.62 if t < 0.6 else 0.48
    sc.disc(c, core_r, P.accent, opacity=0.96)
    sc.shell(c, core_r * 1.08, P.mid, P.edge, gain=1.0, fill_gain=0.0, edge=P.edge)
    sc.add_sprite("ring", 0, c, core_r * 2.7, P.acore, gain=0.85)
    planes = (((1, 0, 0), (0, 0, 1)), ((1, 0, 0), (0, -math.sin(0.6), math.cos(0.6))),
              ((math.cos(0.5), math.sin(0.5), 0), (0, -math.sin(1.1), math.cos(1.1))))
    for k in range(3):
        ph = (t * 1.5 + k / 3) % 1
        r = 2.3 * (1 - ph) + 0.55
        g = math.sin(math.pi * ph) ** 0.6 * (0.6 if t < 0.3 else 1.0)
        sc.ring3d(c, r, *planes[k], P.mid, gain=g, width=2, core=P.acore, edge=P.edge)
    rng = rng_for("fx", "gravity", "orbit")
    for i in range(9):
        a = i / 9 * math.tau + t * 4.5 + rng.uniform(-0.2, 0.2)
        rr = rng.uniform(1.2, 2.3) * (1 - 0.35 * t)
        y = c[1] + math.sin(a * 2 + i) * 0.5
        sc.chunk(c[0] + math.cos(a) * rr, y, c[2] + math.sin(a) * rr, rng.choice((4, 5, 6)), rng.choice(STONES))
    cx, cy = sc.proj(*c)
    for i in range(10):
        a = i / 10 * math.tau + rng.uniform(-0.2, 0.2)
        rr = rng.uniform(1.4, 2.8) * (1.0 - 0.3 * ((t * 3 + i * 0.37) % 1))
        p = (c[0] + math.cos(a) * rr, c[1] + math.sin(a) * rr * 0.8, c[2] + math.sin(a * 1.7) * 0.6)
        sx, sy = sc.proj(*p)
        rot = math.degrees(math.atan2(-(cy - sy), cx - sx)) - 90
        sc.glow2("spark", 0, p, 0.34, P, gain=0.95, rot=rot, stretch=2.6)
    if t > 0.3:
        for k in range(5):
            a = k / 5 * math.tau
            sc.alpha_sprite("dust", min(5, int((t - 0.3) * 9)), (c[0] + math.cos(a) * 1.4, 0.4 + t * 0.4,
                            c[2] + math.sin(a) * 1.4), 1.0, C("#6E6488"), opacity=0.75, flip=True)


def fx_energy(sc, t):
    H = Pal(HEAT)
    E = Pal(PAL["energy"])
    hand = sc.fig_point(0, 0, 18, -34)
    tgt = (8.0, 1.0, 0.0)
    sc.figure(0, 0, "cast")
    sc.figure(8.3, 0, "mob", who="mob", facing=-1)
    rng = rng_for("fx", "energy", int(t * 100))
    if t < 0.3:
        sc.glow2("glow", 0, hand, 1.2, H, gain=1.0)
        sc.glow2("flare", 1, hand, 1.1, H, gain=0.9)
        hx, hy = sc.proj(*hand)
        for i in range(9):
            a = i / 9 * math.tau + 0.3
            rr = rng.uniform(0.8, 1.4)
            p = (hand[0] + math.cos(a) * rr, hand[1] + math.sin(a) * rr, hand[2])
            sx, sy = sc.proj(*p)
            rot = math.degrees(math.atan2(-(hy - sy), hx - sx)) - 90
            sc.glow2("spark", 0, p, 0.28, H, gain=0.9, rot=rot, stretch=2.2)
        sc.glow2("bolt", int(t * 20) % 4, hand, 0.9, E, gain=0.7)
    elif t < 0.6:
        sc.beam(hand, tgt, (20, 9, 3), (H.mid, H.mid, H.core), (0.28, 0.85, 1.0), edge=H.edge)
        sc.glow2("glow", 0, hand, 1.0, H, gain=1.0)
        sc.glow2("flare", 1, tgt, 2.4, H, gain=1.0)
        sc.glow2("glow", 0, tgt, 1.8, H, gain=0.9)
        for i in range(12):
            a = rng.uniform(0, math.tau)
            rr = rng.uniform(0.3, 1.3)
            sc.glow2("ember", rng.randrange(3), (tgt[0] - 0.3 + math.cos(a) * rr * 0.6, tgt[1] + math.sin(a) * rr,
                     tgt[2] + rng.uniform(-0.6, 0.6)), 0.3, H, gain=1.0)
        sc.glow2("bolt", 2, (4.0, 1.25, 0), 0.8, E, gain=0.6, rot=90)
    else:
        sc.beam(hand, tgt, (6, 3, 1), (H.mid, H.mid, H.core), (0.12, 0.3, 0.45))
        sc.decal("decal_scorch", 0, 7.7, 0.1, 1.6, C("#1e1712"), opacity=0.9)
        sc.decal("decal_scorch", 0, 7.7, 0.1, 1.6, H.edge, add=True, gain=0.35)
        for i in range(8):
            sc.glow2("ember", 2 + i % 2, (7.2 + rng.uniform(0, 1.2), rng.uniform(0.05, 0.5), rng.uniform(-0.8, 0.8)),
                     0.26, H, gain=0.8)
        for k in range(3):
            sc.alpha_sprite("smoke", 3 + k, (7.6 + k * 0.2, 1.2 + k * 0.45, 0.1), 0.9 + k * 0.2, C("#5a5550"),
                            opacity=0.6)


def tendril(sc, base, h, sway, w, ink, rim, op=1.0, seed=0):
    """A shadow tendril: a tapered ink polygon (sharp tip) with a 1 px additive violet rim."""
    np = sc.np
    from PIL import Image, ImageDraw
    n = 16
    pts = []
    for i in range(n + 1):
        u = i / n
        pts.append(sc.proj(base[0] + sway * math.sin(u * 3.4 + seed) * u, base[1] + u * h,
                           base[2] + 0.2 * math.cos(u * 2.5 + seed) * u))
    left, right = [], []
    for i, (x, y) in enumerate(pts):
        (xa, ya), (xb, yb) = pts[max(0, i - 1)], pts[min(n, i + 1)]
        L = math.hypot(xb - xa, yb - ya) or 1.0
        nx, ny = -(yb - ya) / L, (xb - xa) / L
        hw = w / 2 * (1 - i / n) ** 0.85
        left.append((x + nx * hw, y + ny * hw))
        right.append((x - nx * hw, y - ny * hw))
    im = Image.new("L", (sc.w, sc.h), 0)
    ImageDraw.Draw(im).polygon(left + right[::-1], fill=255)
    core = np.asarray(im) > 0
    outer = core.copy()
    outer[1:, :] |= core[:-1, :]
    outer[:-1, :] |= core[1:, :]
    outer[:, 1:] |= core[:, :-1]
    outer[:, :-1] |= core[:, 1:]
    sc.add_mask((outer & ~core).astype(np.float32), rim, 0.9 * op)
    sc.q(sc.depth(base[0], base[2]),
         lambda: sc._over((slice(0, sc.h), slice(0, sc.w)), np.broadcast_to(ink, sc.img.shape), core * op))


def fx_shadow(sc, t):
    P = Pal(PAL["shadow"])
    O, D, M = (-0.6, 0.0, 0.0), (5.7, 0.0, 0.0), (4.4, 0.0, 0.0)
    ink = P.core
    sc.figure(M[0], M[2], "mob", who="mob", facing=-1)
    rng = rng_for("fx", "shadow", "burst")
    sm = 0 if t < 0.3 else 3 if t < 0.6 else 5
    sop = 0.95 if t < 0.3 else 0.8 if t < 0.6 else 0.55
    pr = 1.4 if t < 0.3 else 1.6
    sc.decal("decal_pool", 0, O[0], O[2], pr, ink, opacity=0.95 if t < 0.6 else 0.5)
    sc.ground_ring(O[0], O[2], pr * 0.62, pr * 0.8, P.mid, gain=0.45 if t < 0.6 else 0.2)
    if t < 0.3:
        sc.figure(O[0], O[2], "stand", tint=ink, opacity=0.8)
    for k in range(8):
        a = k / 8 * math.tau + rng.uniform(-0.3, 0.3)
        rr = (0.45 if t < 0.3 else 0.9 if t < 0.6 else 1.2) * rng.uniform(0.7, 1.2)
        p = (O[0] + math.cos(a) * rr, 0.35 + (k % 4) * 0.45 + t * 0.5, O[2] + math.sin(a) * rr * 0.7)
        size = (1.3 if t < 0.3 else 1.6) * rng.uniform(0.85, 1.15)
        fr = sm + (k % 2 if sm < 5 else 0)
        # ink over a slightly larger violet copy: a rim that reads against a night sky and stays dark inside
        sc.alpha_sprite("smoke", fr, p, size * 1.14, P.mid * 0.85, opacity=0.6 * sop)
        sc.alpha_sprite("smoke", fr, p, size, ink, opacity=sop)
    if t < 0.3:
        ox, oy = sc.proj(O[0], 1.0, O[2])
        for i in range(12):
            a = i / 12 * math.tau + rng.uniform(-0.2, 0.2)
            rr = rng.uniform(0.9, 1.6)
            p = (O[0] + math.cos(a) * rr, 1.0 + math.sin(a) * rr, O[2])
            sx, sy = sc.proj(*p)
            rot = math.degrees(math.atan2(-(sy - oy), sx - ox)) - 90
            sc.glow2("spark", 0, p, 0.3, P, gain=0.95, rot=rot, stretch=2.4)
        for i in range(6):
            sc.glow2("shard", i % 4, (O[0] + rng.uniform(-1.1, 1.1), rng.uniform(0.3, 2.0), rng.uniform(-0.5, 0.5)),
                     0.32, P, gain=0.9)
    else:
        sc.decal("decal_pool", 0, D[0], D[2], 1.6, ink, opacity=0.95 if t < 0.6 else 0.6)
        sc.ground_ring(D[0], D[2], 1.0, 1.28, P.mid, gain=0.5 if t < 0.6 else 0.25)
        sc.figure(D[0], D[2], "cast", facing=-1)
        sf = 1 if t < 0.6 else 2
        sc.glow2("slash", sf, (M[0] - 0.1, 1.15, M[2]), 3.0, P, gain=1.0, rot=32)
        h = 2.2 if t < 0.6 else 1.1
        for k, (dx, dz, sw) in enumerate(((-0.9, 0.5, 0.4), (0.8, 0.6, -0.45), (0.15, -0.8, 0.35))):
            tendril(sc, (D[0] + dx, 0.0, D[2] + dz), h * (1.0 - 0.15 * k), sw, 9, ink, P.mid, seed=k * 1.7)
        for i in range(7):
            p = (M[0] + rng.uniform(-0.6, 0.6), rng.uniform(0.4, 1.8), rng.uniform(-0.4, 0.4))
            sc.glow2("spark", 1, p, 0.26, P, gain=0.8, rot=32 - 90 + rng.uniform(-20, 20), stretch=2.0)


def fx_mind(sc, t):
    P = Pal(PAL["mind"])
    gold = C(PAL["mind"][3])
    head = sc.fig_point(0, 0, 2, -43)
    tgt = (6.3, 0.0, 0.0)
    th = (tgt[0], 1.75, 0.0)
    sc.figure(0, 0, "cast")
    sc.figure(tgt[0], tgt[2], "mob", who="mob", facing=-1)
    sc.decal("glyph_circle", 0, 0, 0, 1.6, P.mid, add=True, gain=0.8 if t < 0.6 else 0.4, edge=P.edge)
    if t < 0.6:
        sc.glow2("glow", 0, (head[0] + 0.1, head[1] + 0.05, 0), 0.6, P, gain=1.0, core=gold)
    for k in range(4):
        s = min(1.0, max(0.0, t * 1.5 - k * 0.18))
        if s <= 0.02 or (s >= 0.999 and k < 2):
            continue
        p = tuple(head[i] + (th[i] - head[i]) * s for i in range(3))
        r = 0.22 + 0.62 * s
        g = 1.0 - 0.45 * s
        sc.ring3d(p, r, (0, 1, 0), (0, 0, 1), P.mid, gain=g, width=3, core=P.core, edge=P.edge)
    if t > 0.3:
        for i in range(4):
            a = t * 5 + i * math.pi / 2
            p = (th[0] + 0.95 * math.cos(a), th[1] + 0.5 + 0.12 * math.sin(2 * a), th[2] + 0.95 * math.sin(a))
            sc.add_sprite("rune", i, p, 0.85, gold, gain=0.95 if t > 0.6 else 0.75, edge=P.edge)
    if t > 0.6:
        sc.add_sprite("ring", 0, th, 1.7, P.mid, gain=0.9, edge=P.edge)
        ex, ey = sc.proj(th[0] - 0.15, th[1] + 0.02, 0)
        sc.add_sprite("mote", 1, None, 0.25, P.core, gain=1.0, screen=(ex - 2, ey))
        sc.add_sprite("mote", 1, None, 0.25, P.core, gain=1.0, screen=(ex - 8, ey))


def fx_architect(sc, t):
    np = sc.np
    from PIL import Image, ImageDraw
    P = Pal(PAL["architect"])
    blue = P.accent
    z0, z1 = -2.2, -1.2
    x0 = 0.5
    sc.figure(-1.4, 0, "cast")
    sweep = x0 + 5.6 * min(1.0, t / 0.62)
    gg = 0.6 if t < 0.7 else 0.3
    segs = []
    for gx in range(0, 7):
        segs.append((sc.proj(gx, 0, -3.2), sc.proj(gx, 0, -0.2), 1.0 if gx in (0, 6) else 0.6))
    for gz in (-3.2, -2.2, -1.2, -0.2):
        segs.append((sc.proj(0, 0, gz), sc.proj(6, 0, gz), 1.0 if gz in (-2.2, -1.2) else 0.6))
    sc.add_mask(sc.strokes(segs, 1), blue, gg)
    for bx in range(5):
        for by in range(3):
            xa, xb = x0 + bx, x0 + bx + 1
            ya, yb = by, by + 1
            if xa + 0.3 > sweep:
                continue
            age = (sweep - xb) / 5.6
            solid = min(1.0, max(0.0, (age - 0.08) * 3.0)) if t > 0.3 else 0.0
            if t > 0.75:
                solid = 1.0
            if solid > 0:
                quads = [((xa, ya, z1), (xb, ya, z1), (xb, yb, z1), (xa, yb, z1), "#6a6870"),
                         ((xa, yb, z1), (xb, yb, z1), (xb, yb, z0), (xa, yb, z0), "#84828b")]
                if bx == 0:
                    quads.append(((xa, ya, z0), (xa, ya, z1), (xa, yb, z1), (xa, yb, z0), "#525057"))
                for *qp, col in quads:
                    im = Image.new("L", (sc.w, sc.h), 0)
                    dd = ImageDraw.Draw(im)
                    poly = [sc.proj(*p) for p in qp]
                    dd.polygon(poly, fill=255)
                    m = np.asarray(im, np.float32) / 255.0
                    # a stone-brick face: base colour, one darker speckle row, darker outline
                    tex = np.broadcast_to(C(col), sc.img.shape).copy()
                    speck = ((np.floor(sc.xs / 3) + np.floor(sc.ys / 2) * 3 + bx * 5 + by * 7) % 7) == 0
                    tex[speck] *= 0.82
                    sc._over((slice(0, sc.h), slice(0, sc.w)), tex, m * solid)
                    ol = Image.new("L", (sc.w, sc.h), 0)
                    ImageDraw.Draw(ol).polygon(poly, outline=255)
                    sc._over((slice(0, sc.h), slice(0, sc.w)), np.broadcast_to(C("#3d3c41"), sc.img.shape),
                             np.asarray(ol, np.float32) / 255.0 * solid)
            fresh = 1.0 if (sweep - xb) < 1.2 and t < 0.7 else 0.55 if t < 0.7 else 0.3
            e = [((xa, ya, z1), (xb, ya, z1)), ((xb, ya, z1), (xb, yb, z1)), ((xb, yb, z1), (xa, yb, z1)),
                 ((xa, yb, z1), (xa, ya, z1)), ((xa, yb, z0), (xb, yb, z0)), ((xa, yb, z1), (xa, yb, z0)),
                 ((xb, yb, z1), (xb, yb, z0))]
            segs = [(sc.proj(*a), sc.proj(*b), fresh) for a, b in e]
            sc.add_mask(sc.strokes(segs, 3), P.mid, 0.4, edge=P.edge, ea=EDGE_LINE)
            sc.add_mask(sc.strokes(segs, 1), P.core, 1.0)
    if t < 0.7:
        rng = rng_for("fx", "architect", int(t * 100))
        front = min(sweep, x0 + 5)
        for i in range(10):
            ey = rng.choice((0, 1, 2, 3))
            p = (front - rng.uniform(0, 0.9), ey, z1)
            sc.glow2("spark", 0, p, 0.5, P, gain=1.0, rot=sc.dir_rot((rng.choice((-1, 1)), 0, 0)), stretch=2.4,
                     mid=P.core, core=C("#ffffff"))
        for i in range(8):
            p = (front + rng.uniform(-0.2, 0.3), rng.uniform(0, 3), z1 + rng.uniform(0, 0.4))
            sc.glow2("ember", rng.randrange(3), p, 0.35, P, gain=1.0, mid=P.core, core=C("#ffffff"))
        sc.glow2("flare", int(t * 13) % 2, (front, 1.5, z1), 1.4, P, gain=0.85)
    hand = sc.fig_point(-1.4, 0, 18, -34)
    if t < 0.7:
        sc.add_sprite("rune", 3, hand, 0.7, blue, gain=0.95, edge=P.edge)


def fx_tech(sc, t):
    P = Pal(PAL["tech"])
    hand = sc.fig_point(0, 0, 18, -34)
    far = (16.0, hand[1], 0.0)
    sc.figure(0, 0, "cast")
    rng = rng_for("fx", "tech", int(t * 100))
    if t < 0.3:
        sc.add_sprite("hex", 0, hand, 1.35, P.mid, gain=1.0, edge=P.edge)
        sc.add_sprite("ring", 0, hand, 1.9, P.accent, gain=0.85, edge=P.edge)
        sc.glow2("glow", 0, hand, 0.6, P, gain=0.9)
        hx, hy = sc.proj(*hand)
        for i in range(8):
            a = i / 8 * math.tau
            rr = rng.uniform(0.8, 1.3)
            sc.glow2("mote", i % 2, (hand[0] + math.cos(a) * rr, hand[1] + math.sin(a) * rr, hand[2]), 0.32, P, gain=1.0)
        for d in (-1, 1):
            sc.add_mask(sc.strokes([((hx + d * 16, hy), (hx + d * 26, hy), 1.0), ((hx, hy + d * 16), (hx, hy + d * 26), 1.0)],
                                   1), P.core, 1.0, edge=P.edge, ea=EDGE_LINE)
    else:
        lin = t < 0.6
        sc.beam(hand, far, (9, 3, 1) if lin else (4, 2, 1), (P.mid, P.mid, P.core),
                (0.3, 0.9, 1.0) if lin else (0.1, 0.25, 0.4), edge=P.edge if lin else None)
        for k in range(7):
            u = 0.7 + k * 1.55
            p = (hand[0] + u, hand[1], 0.0)
            r = (0.32 + 0.04 * k) if lin else (0.55 + 0.06 * k)
            ph = k * 0.9
            sc.ring3d(p, r, (0, math.cos(ph), math.sin(ph) * 0.4), (0, -math.sin(ph) * 0.4, math.cos(ph)), P.mid,
                      gain=(0.9 if lin else 0.35) * (1 - k * 0.07), width=1, core=P.core if lin else None,
                      edge=P.edge if lin else None)
        if lin:
            sc.glow2("flare", 0, hand, 1.7, P, gain=1.0)
            sc.glow2("glow", 0, hand, 0.9, P, gain=1.0)
        else:
            sc.add_sprite("hex", 0, hand, 1.0, P.mid, gain=0.45)
        for i in range(10):
            p = (hand[0] + rng.uniform(-0.3, 2.5), hand[1] + rng.uniform(-0.6, 0.6), rng.uniform(-0.5, 0.5))
            sc.glow2("mote", rng.randrange(2), p, 0.3, P, gain=0.9 if lin else 0.55)


def fx_spirit(sc, t):
    P = Pal(PAL["spirit"])
    soul = C(SOUL)
    soul_edge = C("#4c50b3")
    hand = sc.fig_point(0, 0, 18, -34)
    tgt = (6.2, 0.0, 0.0)
    fwd = sc.dir_rot((1, 0, 0))    # wisp heads lead toward the target, tails trail back to the caster
    sc.figure(0, 0, "palm")
    sc.figure(tgt[0], tgt[2], "mob", who="mob", facing=-1)
    rng = rng_for("fx", "spirit", int(t * 100))
    if t < 0.3:
        p = (hand[0] + 0.9, hand[1], 0.0)
        sc.add_sprite("palm", 0, p, 1.9, P.mid, gain=0.8, sw=0.8, edge=P.edge)
        for k in range(4):
            sc.glow2("wisp", k, (p[0] - 0.5 - k * 0.45, p[1] + rng.uniform(-0.35, 0.35), rng.uniform(-0.3, 0.3)), 0.8,
                     P, gain=0.85, rot=fwd)
    elif t < 0.6:
        p = (tgt[0] - 0.55, 1.15, 0.0)
        sc.add_sprite("palm", 0, p, 2.4, P.mid, gain=1.0, sw=0.8, edge=P.edge)
        sc.add_sprite("palm", 0, p, 2.4, P.core, gain=0.35, sw=0.8)
        sc.ground_ring(tgt[0] - 0.3, tgt[2], 0.6, 1.5, P.mid, gain=1.0, core=P.core, edge=P.edge)
        sc.glow2("flare", 1, p, 1.5, P, gain=0.8)
        for k in range(6):
            a = rng.uniform(0, math.tau)
            sc.glow2("wisp", k % 4, (p[0] - 1.0 + math.cos(a) * 0.9, p[1] + math.sin(a) * 0.8, rng.uniform(-0.4, 0.4)),
                     0.75, P, gain=0.8, rot=fwd + rng.uniform(-40, 40))
    else:
        sc.ground_ring(tgt[0] - 0.3, tgt[2], 1.6, 2.4, soul, gain=0.95, core=P.core, edge=soul_edge)
        sc.ring3d((tgt[0] - 0.3, 1.0, 0.0), 1.15, (0, 1, 0), (0, 0, 1), soul, gain=0.8, width=2, core=P.core,
                  edge=soul_edge)
        sc.add_sprite("palm", 0, (tgt[0] - 0.4, 1.2, 0.0), 2.7, soul, gain=0.45, sw=0.8, edge=soul_edge)
        for k in range(5):
            sc.glow2("wisp", k % 4, (tgt[0] - 1.2 + k * 0.5, 0.6 + rng.uniform(0, 1.6), rng.uniform(-0.6, 0.6)), 0.7, P,
                     gain=0.7)
        for k in range(6):
            sc.glow2("mote", 1, (tgt[0] - 1.3 + rng.uniform(0, 2), rng.uniform(0.2, 2.2), rng.uniform(-0.5, 0.5)), 0.25, P,
                     gain=0.8)


def fx_overload(sc, t):
    P = Pal(PAL["energy"])
    c = (0.0, 1.3, 0.0)
    sc.figure(0, 0, "hover" if t < 0.6 else "stand", y=0.0)
    rng = rng_for("fx", "overload", int(t * 100))
    sc.decal("glyph_circle", 0, 0, 0, 2.8, P.mid, add=True, gain=0.9 if t < 0.6 else 0.35, edge=P.edge)
    if t < 0.3:
        r = 1.3
        sc.glow2("glow", 0, c, r * 2.2, P, gain=1.0)
        sc.shell(c, r, P.core, P.mid, gain=1.0, edge=P.edge)
        for i in range(5):
            a = i / 5 * math.tau + 0.4
            sc.lightning((c[0] + math.cos(a) * r, c[1] + math.sin(a) * r, 0), (c[0] + math.cos(a) * 2.4,
                         c[1] + math.sin(a) * 2.2, 0), rng, P.mid, gain=0.9, edge=P.edge)
    elif t < 0.6:
        r = 3.4
        sc.glow2("glow", 0, c, r * 0.8, P, gain=0.7)
        sc.shell(c, r, P.core, P.mid, gain=1.0, edge=P.edge, fill_gain=0.16)
        sc.ground_ring(0, 0, 2.8, 4.8, P.mid, gain=1.0, core=P.core, edge=P.edge)
        for i in range(9):
            a = i / 9 * math.tau + rng.uniform(-0.2, 0.2)
            p0 = (c[0] + math.cos(a) * r * 0.9, max(0.0, c[1] + math.sin(a) * r * 0.9), 0)
            p1 = (c[0] + math.cos(a) * (r + 2.0), max(0.0, c[1] + math.sin(a) * (r + 1.6)), rng.uniform(-1, 1))
            sc.lightning(p0, p1, rng, P.mid, gain=1.0, edge=P.edge)
        for i in range(16):
            a = rng.uniform(0, math.tau)
            rr = rng.uniform(3.0, 5.2)
            sc.chunk(math.cos(a) * rr, rng.uniform(0.2, 2.6), math.sin(a) * rr * 0.8, rng.choice((4, 5, 6)),
                     rng.choice(STONES))
    else:
        r = 4.6
        sc.shell(c, r, P.mid, P.edge, gain=0.45, fill_gain=0.06, edge=P.edge)
        sc.decal("decal_scorch", 0, 0, 0, 3.4, C("#1e1712"), opacity=0.85)
        for i in range(2):
            a = rng.uniform(0, math.tau)
            sc.lightning((math.cos(a) * 1.2, 1.0 + math.sin(a), 0), (math.cos(a) * 2.6, 0.0, 0.4), rng, P.mid, gain=0.5)
        for i in range(12):
            a = rng.uniform(0, math.tau)
            rr = rng.uniform(3.5, 6.0)
            sc.chunk(math.cos(a) * rr, rng.uniform(0.0, 0.6), math.sin(a) * rr * 0.8, rng.choice((4, 5)),
                     rng.choice(STONES))
        for i in range(10):
            sc.glow2("ember", 1 + i % 3, (rng.uniform(-3, 3), rng.uniform(0.1, 2.0), rng.uniform(-1, 1)), 0.3, P,
                     gain=0.8, mid=C(HEAT[1]), core=C(HEAT[0]))


def impact_frame(sc, pal, scale=2):
    """The ultimate's screen impact frame from a finished scene's layers: everything that is light (the additive layer)
    goes white or grey, the world crushes to near black, figures and debris read as pure black cut-outs, light/dark
    borders get a 1 px family-colour outline and the screen edge a banded colour rim."""
    import numpy as np
    from PIL import Image
    sc.flush()
    light = np.clip(sc.add, 0, 1)
    ll = light[..., 0] * 0.3 + light[..., 1] * 0.59 + light[..., 2] * 0.11
    base = sc.img[..., 0] * 0.3 + sc.img[..., 1] * 0.59 + sc.img[..., 2] * 0.11
    v = np.where(ll > 0.42, 1.0, np.where(ll > 0.14, 0.5, 0.04 + 0.08 * (base > 0.35))).astype(np.float32)
    v[sc.solid] = 0.0
    out = np.dstack([v, v, v])
    bright = v > 0.9
    edge = np.zeros_like(bright)
    edge[1:, :] |= bright[1:, :] != bright[:-1, :]
    edge[:, 1:] |= bright[:, 1:] != bright[:, :-1]
    out[edge & ~bright] = pal.mid
    h, w = v.shape
    yy, xx = np.mgrid[0:h, 0:w]
    dist = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    out[(dist < 12) & (dist >= 5)] = out[(dist < 12) & (dist >= 5)] * 0.35 + pal.mid * 0.65
    out[dist < 5] = pal.edge
    im = Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
    return im.resize((w * scale, h * scale), Image.NEAREST)


EFFECTS = [
    ("kinetic", "Ground Slam", fx_kinetic, 200, 200),
    ("velocity", "Dash", fx_velocity, 200, 200),
    ("flight", "Hover / Flight", fx_flight, 200, 205),
    ("gravity", "Gravity Well", fx_gravity, 80, 205),
    ("energy", "Beam (heat)", fx_energy, 60, 205),
    ("shadow", "Shadow Step", fx_shadow, 130, 205),
    ("mind", "Dominate", fx_mind, 90, 205),
    ("architect", "Wall", fx_architect, 140, 215),
    ("tech", "Railgun", fx_tech, 60, 205),
    ("spirit", "Spirit Palm", fx_spirit, 90, 205),
]
KEYS = ((0.15, "release"), (0.45, "impact / travel"), (0.85, "linger"))


# ---------------------------------------------------------------------------------------------------------------------
# Preview: sprite sheets


def _strip_bg(w, h, day, seed):
    """Sky with a ground strip along the bottom 38%: night = dark stone, day = grass over stone."""
    import numpy as np
    a = np.zeros((h, w, 3), np.float32)
    g0 = int(h * 0.62)
    a[:] = C("#87b5e8") if day else C("#0b0f1c")
    rng = rng_for("strip", seed, day)
    for by in range(g0, h, 12):
        for bx in range(0, w, 24):
            if day and by == g0:
                col = C(rng.choice(("#567d3f", "#5f8746", "#668e4b")))
            elif day:
                col = C(rng.choice(("#6c6a72", "#77757d", "#84828b")))
            else:
                col = C(rng.choice(("#1b1c22", "#202128", "#25262e")))
            a[by:by + 12, bx:bx + 24] = col
            a[by:by + 12, bx] *= 0.85
            a[by, bx:bx + 24] *= 0.85
    a[g0:g0 + 2] = C("#3f6232") if day else C("#111216")
    if not day:
        for _ in range(w * g0 // 900):
            a[rng.randrange(g0), rng.randrange(w)] = C("#2a3352")
    return a


def _sheet_composite(dst, x0, y0, name, frame, pal, kind, tints, scale, underlay):
    import numpy as np
    k, frames = sprite_images()[name]
    S = frames[0].width
    full = _xform(name, frame, S * scale, S * scale)
    if kind == "alpha":
        tint = C(tints[1]) if name.startswith("decal") else C(tints[0])
        al = full[..., 3:4]
        reg = dst[y0:y0 + full.shape[0], x0:x0 + full.shape[1]]
        reg[:] = reg * (1 - al) + full[..., :3] * tint * al
        return
    m = (full[..., 3] >= 26 / 255)[..., None]
    reg = dst[y0:y0 + full.shape[0], x0:x0 + full.shape[1]]
    if underlay:
        reg[:] = reg * (1 - m * EDGE_A) + pal.edge * m * EDGE_A
    reg += full[..., :3] * pal.mid * m
    cs = rnd(S * scale * 0.45)
    core = _xform(name, frame, cs, cs)
    ox = x0 + (full.shape[1] - cs) // 2
    oy = y0 + (full.shape[0] - cs) // 2
    mc = (core[..., 3] >= 26 / 255)[..., None]
    dst[oy:oy + cs, ox:ox + cs] += core[..., :3] * pal.acore * mc
    np.clip(dst, 0, 1, out=dst)


def sprite_sheet(family, path):
    import numpy as np
    from PIL import Image, ImageDraw
    pal = Pal(PAL[family])
    tints = ALPHA_TINT[family]
    scale, gap, label_w = 6, 8, 170
    panel_w = 6 * 16 * scale + 7 * gap
    cols = [("night", False, False), ("day", True, False), ("day + edge underlay", True, True)]
    rows = []
    for name, (kind, frames) in sprite_images().items():
        rows.append((name, kind, len(frames), frames[0].width))
    head = 84
    H = head + sum(S * scale + 2 * gap for _, _, _, S in rows)
    W = label_w + len(cols) * (panel_w + gap)
    sheet = np.zeros((H, W, 3), np.float32)
    sheet[:] = C("#181a22")
    y = head
    for name, kind, n, S in rows:
        rh = S * scale + 2 * gap
        for ci, (_, day, under) in enumerate(cols):
            if kind == "alpha" and under:
                continue
            x = label_w + ci * (panel_w + gap)
            sheet[y:y + rh - 2, x:x + panel_w] = _strip_bg(panel_w, rh - 2, day, name)
            for f in range(n):
                _sheet_composite(sheet, x + gap + f * (S * scale + gap), y + gap - 1, name, f, pal, kind, tints, scale,
                                 under)
        y += rh
    im = Image.fromarray((np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(im)
    f13, f16 = _font(13), _font(17)
    d.text((10, 8), f"{family.upper()}  sprites x{scale}  (add: mid at full size + core at 45%; alpha: tinted coverage)",
           fill=(230, 232, 240), font=f16)
    sx = 10
    for lab, col in zip(("core", "mid", "edge", "accent"), PAL[family]):
        d.rectangle((sx, 34, sx + 18, 52), fill=col, outline=(90, 94, 110))
        d.text((sx + 24, 36), f"{lab} {col}", fill=(200, 204, 214), font=f13)
        sx += 150
    for ci, (lab, _, _) in enumerate(cols):
        d.text((label_w + ci * (panel_w + gap) + 4, 64), lab, fill=(200, 204, 214), font=f13)
    y = head
    for name, kind, n, S in rows:
        d.text((10, y + 10), name, fill=(235, 236, 242), font=f16)
        d.text((10, y + 32), f"{S}px x{n}  {kind}", fill=(160, 166, 180), font=f13)
        if kind == "alpha":
            d.text((10, y + 50), "tint " + (tints[1] if name.startswith("decal") else tints[0]), fill=(160, 166, 180),
                   font=f13)
        y += S * scale + 2 * gap
    im.save(path)
    return path


def effect_sheet(family, title, fn, ox, oy, path, keys=KEYS, extra=None):
    from PIL import Image, ImageDraw
    panels = []
    for t, lab in keys:
        row = []
        for day in (False, True):
            sc = Scene(day, ox, oy, seed=family)
            fn(sc, t)
            row.append(sc.finish(scale=2))
        panels.append((f"t={t:.2f} {lab}", row))
    if extra:
        panels.append(extra(panels))
    pw, ph = panels[0][1][0].size
    gap, head, lab_h = 10, 44, 22
    W = len(panels) * (pw + gap) + gap
    H = head + 2 * (ph + lab_h + gap)
    sheet = Image.new("RGB", (W, H), (24, 26, 34))
    d = ImageDraw.Draw(sheet)
    d.text((gap, 10), f"{family.upper()}: {title}   (3/4 view, 24 px per block, x2; edge underlay {EDGE_A:.0%} on)",
           fill=(230, 232, 240), font=_font(18))
    for i, (lab, row) in enumerate(panels):
        x = gap + i * (pw + gap)
        for j, im in enumerate(row):
            y = head + j * (ph + lab_h + gap)
            d.text((x + 2, y + 2), f"{lab} - {'day' if j else 'night'}", fill=(200, 204, 214), font=_font(14))
            sheet.paste(im, (x, y + lab_h))
    sheet.save(path)
    return path


def preview(outdir):
    os.makedirs(outdir, exist_ok=True)
    written = []
    for family in PAL:
        written.append(sprite_sheet(family, os.path.join(outdir, f"sprites_{family}.png")))
    for family, title, fn, ox, oy in EFFECTS:
        written.append(effect_sheet(family, title, fn, ox, oy, os.path.join(outdir, f"effects_{family}.png")))
    pal = Pal(PAL["energy"])

    def impact(panels):
        ims = []
        for day in (False, True):
            sc = Scene(day, 200, 200, seed="ultimate")
            fx_overload(sc, 0.45)
            ims.append(impact_frame(sc, pal))
        return ("impact frame (screen, t=0.45)", ims)
    written.append(effect_sheet("ultimate", "Overload", fx_overload, 200, 200, os.path.join(outdir, "effects_ultimate.png"),
                                extra=impact))
    for p in written:
        print(p)
    return written


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "build/fx-preview"))
    elif len(sys.argv) > 1 and sys.argv[1] == "build":
        from pixelart import Out
        o = Out(os.path.join(ROOT, "src/main/resources"))
        build(o)
        print(f"power_fx: {len(o.written)} textures")
    else:
        print(__doc__)
