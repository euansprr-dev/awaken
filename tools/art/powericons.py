"""Power icon kit: 32x32 family frames, one lighting pass, and a glyph canvas. Used by specs/powers.py.

An icon is a frame (its silhouette says the family, even without colour) with a recessed glass face, and a glyph that
floats above the face. One light, from the top-left, shades everything: the frame gets a raised bevel (lit outer
slope, flat rim, shaded inner lip, contact shadow on the face), the glyph gets a lit top-left edge, a shaded bottom-
right edge, a dark outline, a drop shadow or a glow halo, and one specular glint. Every pixel is a flat colour from
the family ramp, so the icons stay crisp Minecraft pixels at any GUI scale.

Glyphs are drawn with continuous shapes (discs, capsules, polygons, arcs) in a 32x32 space centred on (16, 16), so the
same glyph can be re-rasterised at another scale (the Power Core crystal draws it at 0.62).
"""
from __future__ import annotations

import math

from pixelart import Canvas, hex_rgba, ramp, RAMPS

N = 32
LIGHT = (-0.6, -0.8)  # unit vector toward the light: top-left, a little more from above
WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)
HOLO = hex_rgba("#7fc3ff")  # the Holo's edge colour: the face's glass sheen leans toward it

RAMPS.setdefault("teal", ["#08201e", "#0f3833", "#17564e", "#23786c", "#47c6b0", "#a6f0e2"])
RAMPS.setdefault("jade", ["#0f2a22", "#1b4436", "#2a6650", "#3f8f6e", "#62b98f", "#b8f0cf"])
RAMPS.setdefault("violet", ["#140b2e", "#24145a", "#3a2390", "#5a3fc4", "#8f6bff", "#d2c4ff"])
RAMPS.setdefault("ruby", ["#240507", "#4a0c10", "#7c1519", "#b8241f", "#ff4a3d", "#ffb09a"])

FAMILY_RAMP = {
    "kinetic": "amber", "velocity": "ruby", "flight": "frost", "energy": "signal", "shadow": "violet",
    "mind": "dream", "architect": "sand", "tech": "teal", "spirit": "jade",
}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ---- signed distance shapes (negative inside), in continuous pixel space: pixel (x, y) has its centre at x+.5, y+.5

def sd_circle(cx, cy, r):
    return lambda x, y: math.hypot(x - cx, y - cy) - r


def sd_poly(pts):
    def f(x, y):
        d = (x - pts[0][0]) ** 2 + (y - pts[0][1]) ** 2
        s = 1.0
        j = len(pts) - 1
        for i in range(len(pts)):
            (xi, yi), (xj, yj) = pts[i], pts[j]
            ex, ey, wx, wy = xj - xi, yj - yi, x - xi, y - yi
            t = max(0.0, min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey)))
            bx, by = wx - ex * t, wy - ey * t
            d = min(d, bx * bx + by * by)
            c1, c2, c3 = y >= yi, y < yj, ex * wy > ey * wx
            if (c1 and c2 and c3) or not (c1 or c2 or c3):
                s = -s
            j = i
        return s * math.sqrt(d)
    return f


def sd_capsule(ax, ay, bx, by, r):
    def f(x, y):
        ex, ey = bx - ax, by - ay
        t = max(0.0, min(1.0, ((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey)))
        return math.hypot(x - ax - ex * t, y - ay - ey * t) - r
    return f


def union(*fs):
    return lambda x, y: min(f(x, y) for f in fs)


def cut(a, b):
    return lambda x, y: max(a(x, y), -b(x, y))


def _teardrop(cx, cy, r, tip):
    d = cy - tip
    th = math.asin(r / d)
    a = math.pi / 2 - th
    t1 = (cx + math.cos(-math.pi / 2 + a) * r, cy + math.sin(-math.pi / 2 + a) * r)
    t2 = (cx + math.cos(-math.pi / 2 - a) * r, cy + math.sin(-math.pi / 2 - a) * r)
    return union(sd_circle(cx, cy, r), sd_poly([(cx, tip), t1, (cx, cy), t2]))


def _octagon(lo, hi, ch):
    return sd_poly([(lo + ch, lo), (hi - ch, lo), (hi, lo + ch), (hi, hi - ch), (hi - ch, hi), (lo + ch, hi),
                    (lo, hi - ch), (lo, lo + ch)])


def _eye():
    # a lens with sharp corners at the sides, swollen by a round iris in the middle
    h, half = 11.0, 15.6
    r = (half * half + h * h) / (2 * h)
    lens = lambda x, y: max(sd_circle(16, 16 + r - h, r)(x, y), sd_circle(16, 16 - r + h, r)(x, y))
    return union(lens, sd_circle(16, 16, 13.2))


def _orbit():
    # Flight & Gravity: a world with a tilted ring round it; the ring shows as two swept tabs either side.
    # (Wing tips were tried first: at HUD size they read as cat ears.)
    a = math.radians(-24)
    rx, ry, th = 16.4, 5.2, 1.6

    def band(x, y):
        dx, dy = x - 16, y - 16.5
        u, v = dx * math.cos(a) - dy * math.sin(a), dx * math.sin(a) + dy * math.cos(a)
        return abs(math.hypot(u / rx, v / ry) - 1) * ry - th
    return union(sd_circle(16, 16.5, 12.4), band)


def _chip():
    body = _octagon(2.2, 29.8, 8.2)
    pins = []
    for a, b in ((10.5, 13.5), (18.5, 21.5)):
        pins += [sd_poly([(a, 0.3), (b, 0.3), (b, 3), (a, 3)]), sd_poly([(a, 29), (b, 29), (b, 31.7), (a, 31.7)]),
                 sd_poly([(0.3, a), (3, a), (3, b), (0.3, b)]), sd_poly([(29, a), (31.7, a), (31.7, b), (29, b)])]
    return union(body, *pins)


def _block():
    sq = sd_poly([(1, 1), (31, 1), (31, 31), (1, 31)])
    notch = [sd_poly([(x, y), (x + 6, y), (x + 6, y + 6), (x, y + 6)]) for x, y in ((-1, -1), (27, -1), (-1, 27), (27, 27))]
    return cut(sq, union(*notch))


# family -> (silhouette, glyph centre offset, rim width, glyph scale). Every silhouette is different, so the family reads in grey.
FRAMES = {
    "kinetic": (sd_poly([(6, 1), (26, 1), (31, 6), (31, 17.5), (16, 31.5), (1, 17.5), (1, 6)]), (0, -1.5), 3, 0.84),
    "velocity": (sd_poly([(1, 1.5), (21, 1.5), (31.5, 16), (21, 30.5), (1, 30.5), (8.5, 16)]), (1.5, 0), 3, 0.82),
    "flight": (_orbit(), (0, 0.5), 3, 0.8),
    "energy": (sd_poly([(16, 0), (32, 16), (16, 32), (0, 16)]), (0, 0), 3, 0.8),
    "shadow": (cut(sd_circle(16, 16, 15.6), sd_circle(29.5, 2.5, 9.2)), (-1, 1), 3, 0.84),
    "mind": (_eye(), (0, 0), 3, 0.76),
    "architect": (_block(), (0, 0), 4, 0.8),
    "tech": (_chip(), (0, 0), 3, 0.8),
    "spirit": (_teardrop(16, 18.8, 12.8, 0.2), (0, 2.8), 3, 0.72),
}


class Frame:
    """A family frame rasterised once: per-pixel depth from the edge (1 = outermost) and outward normal."""

    def __init__(self, family):
        sdf, self.offset, self.rim, self.scale = FRAMES[family]
        self.family = family
        inside = {(x, y) for y in range(N) for x in range(N) if sdf(x + .5, y + .5) <= 0}
        self.inside = inside
        self.depth = {}
        cur, k = set(inside), 1
        while cur:
            edge = {(x, y) for (x, y) in cur
                    if any((x + dx, y + dy) not in cur for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
            for q in edge:
                self.depth[q] = k
            cur -= edge
            k += 1
        self.lit = {}
        e = 0.75
        for (x, y) in inside:
            px, py = x + .5, y + .5
            nx, ny = sdf(px + e, py) - sdf(px - e, py), sdf(px, py + e) - sdf(px, py - e)
            ln = math.hypot(nx, ny) or 1
            self.lit[(x, y)] = (nx * LIGHT[0] + ny * LIGHT[1]) / ln  # >0 where the edge faces the light

    def face(self, q):
        return self.depth.get(q, 0) > self.rim + 1


class Palette:
    def __init__(self, family):
        r = ramp(FAMILY_RAMP[family])
        self.r = r
        night = hex_rgba("#05070d")
        self.outline = mix(r[0], night, 0.6)
        self.face = [mix(r[0], night, 0.45), mix(r[0], night, 0.2), r[0], mix(r[0], r[1], 0.55), r[1]]
        # the frame is family-tinted gunmetal, a step darker than the glyph, so the glyph is what you see first
        steel = ramp("slate")
        self.rim = [mix(r[i], steel[i], 0.38) for i in range(6)]
        self.hot = mix(r[5], WHITE, 0.6)


def frame_canvas(family, glow_centre=False):
    """The empty frame: bevelled rim and a recessed glass face."""
    f, pal = Frame(family), Palette(family)
    r = pal.r
    c = Canvas(N, N)
    gx, gy = 16 + f.offset[0], 16 + f.offset[1]
    brightest = None
    for (x, y), d in f.depth.items():
        lit = f.lit[(x, y)]
        if d == 1:
            col = pal.outline
        elif d == 2:  # outer slope, faces out
            R = pal.rim
            col = R[4] if lit > 0.7 else R[3] if lit > 0.3 else R[2] if lit > -0.3 else R[1]
            if brightest is None or lit > f.lit[brightest]:
                brightest = (x, y)
        elif d < f.rim + 1:  # flat rim top
            R = pal.rim
            col = R[3] if lit > 0.6 else R[2] if lit > -0.45 else R[1]
        elif d == f.rim + 1:  # inner lip, faces in: dark under the lit edge, catching light across from it
            R = pal.rim
            col = R[3] if lit < -0.45 else R[1] if lit < 0.2 else pal.outline
        elif d == f.rim + 2:  # contact shadow where the rim overhangs the face
            col = pal.outline if lit > -0.1 else pal.face[0]
        else:
            dx, dy = x + .5 - gx, y + .5 - gy
            v = (dx * -LIGHT[0] + dy * -LIGHT[1]) / 11  # -1 top-left (in the rim's shadow) .. 1 bottom-right
            k = 2 if v > 0.35 else 1 if v > -0.3 else 0
            if glow_centre:
                rr = math.hypot(dx, dy)
                k = max(k, 4 if rr < 4.5 else 3 if rr < 7.5 else 2 if rr < 10 else 0)
            col = pal.face[k]
            s = dx + dy
            if -17 <= s <= -15 or s == -12:  # glass sheen across the top-left of the face, Holo-blue
                col = mix(col, HOLO, 0.14 if s != -12 else 0.09)
        c.set(x, y, col)
    if brightest:  # specular glint on the rim
        bx, by = brightest
        c.set(bx, by, WHITE)
        for dx, dy in ((1, 0), (0, 1)):
            q = (bx + dx, by + dy)
            if f.depth.get(q) in (2, 3):
                c.set(q[0], q[1], r[5])
    return c, f, pal


# ---- glyph canvas ------------------------------------------------------------------------------------------------

MAIN, HOT, DIM, ERASE = 1, 2, 3, 0


class G:
    """A glyph: pixel -> MAIN (the object, shaded as a solid), HOT (white-hot energy), DIM (secondary: motion, debris).

    Coordinates are continuous, 32x32, centre (16, 16); a pixel is painted when its centre falls inside the shape.
    Draw order matters: later shapes paint over earlier ones, and v=ERASE cuts (a 1px cut becomes a dark seam).
    """

    def __init__(self, scale=1.0, ox=0.0, oy=0.0, size=N):
        self.px = {}
        self.s, self.ox, self.oy, self.size = scale, ox, oy, size

    # transform: glyph space -> canvas space
    def _t(self, x, y):
        return (x - 16) * self.s + self.size / 2 + self.ox, (y - 16) * self.s + self.size / 2 + self.oy

    def _fill(self, inside, v, bbox=None):
        for y in range(self.size):
            for x in range(self.size):
                # back to glyph space for the test
                gx = (x + .5 - self.size / 2 - self.ox) / self.s + 16
                gy = (y + .5 - self.size / 2 - self.oy) / self.s + 16
                if inside(gx, gy):
                    if v == ERASE:
                        self.px.pop((x, y), None)
                    else:
                        self.px[(x, y)] = v

    def p(self, x, y, v=MAIN):
        self._fill(lambda a, b: int(a) == x and int(b) == y, v)

    def rect(self, x0, y0, x1, y1, v=MAIN):
        """Inclusive pixel rect in glyph space."""
        self._fill(lambda a, b: x0 <= a < x1 + 1 and y0 <= b < y1 + 1, v)

    def disc(self, cx, cy, r, v=MAIN):
        self._fill(lambda a, b: (a - cx) ** 2 + (b - cy) ** 2 <= r * r, v)

    def ellipse(self, cx, cy, rx, ry, v=MAIN):
        self._fill(lambda a, b: ((a - cx) / rx) ** 2 + ((b - cy) / ry) ** 2 <= 1, v)

    def ring(self, cx, cy, r, w=2.0, v=MAIN):
        self._fill(lambda a, b: abs(math.hypot(a - cx, b - cy) - r) <= w / 2, v)

    def line(self, x0, y0, x1, y1, w=3.0, v=MAIN):
        """A capsule of width w from (x0, y0) to (x1, y1)."""
        ex, ey = x1 - x0, y1 - y0
        ll = ex * ex + ey * ey or 1e-9

        def inside(a, b):
            t = max(0.0, min(1.0, ((a - x0) * ex + (b - y0) * ey) / ll))
            return (a - x0 - ex * t) ** 2 + (b - y0 - ey * t) ** 2 <= (w / 2) ** 2
        self._fill(inside, v)

    def poly(self, pts, v=MAIN):
        f = sd_poly(pts)
        self._fill(lambda a, b: f(a, b) <= 0, v)

    def arc(self, cx, cy, r, a0, a1, w=2.5, v=MAIN):
        """Arc from angle a0 to a1 (degrees, counter-clockwise, 0 = right, 90 = up)."""
        def inside(a, b):
            if abs(math.hypot(a - cx, b - cy) - r) > w / 2:
                return False
            ang = math.degrees(math.atan2(-(b - cy), a - cx)) % 360
            return (a0 <= ang <= a1) if a0 <= a1 else (ang >= a0 or ang <= a1)
        self._fill(inside, v)

    def arrow(self, x, y, d, size=4.0, v=MAIN):
        """A solid arrowhead with its tip at (x, y), pointing d degrees (0 = right, 90 = up)."""
        a = math.radians(d)
        ux, uy = math.cos(a), -math.sin(a)
        bx, by = x - ux * size, y - uy * size
        self.poly([(x, y), (bx - uy * size * 0.9, by + ux * size * 0.9), (bx + uy * size * 0.9, by - ux * size * 0.9)], v)

    def bolt(self, pts, w=3.0, v=MAIN):
        """A jagged polyline (lightning), capsules between successive points."""
        for (a, b), (c, d) in zip(pts, pts[1:]):
            self.line(a, b, c, d, w, v)

    def figure(self, x, y, pose="stand", v=MAIN, s=1.0):
        """A person ~18px tall; (x, y) is the top of the head. Poses: stand, run, leap, float, brace."""
        w = 3.2 * s
        self.disc(x, y + 2.4 * s, 2.6 * s, v)
        hip = (x, y + 11 * s)
        sh = (x, y + 5.6 * s)
        lean = {"run": 1.6, "leap": 1.2}.get(pose, 0) * s
        self.line(sh[0] + lean, sh[1], hip[0], hip[1], w, v)
        S = lambda a, b: (x + a * s, y + b * s)
        limbs = {
            "stand": [((0, 6.4), (-3.4, 11)), ((0, 6.4), (3.4, 11)), ((0, 11), (-2.4, 18)), ((0, 11), (2.4, 18))],
            "run": [((1.4, 6.4), (5, 9.6)), ((1.4, 6.4), (-3, 9)), ((0, 11), (4.6, 14.6)), ((4.6, 14.6), (3.2, 18.4)),
                    ((0, 11), (-3.6, 17.4))],
            "leap": [((1, 6.4), (5, 3)), ((1, 6.4), (-3.4, 4.4)), ((0, 11), (4.4, 15)), ((0, 11), (-4.6, 13.6))],
            "float": [((0, 6.4), (-5, 6.6)), ((0, 6.4), (5, 6.6)), ((0, 11), (-1.4, 18)), ((0, 11), (1.4, 18))],
            "brace": [((0, 6.4), (-4, 8.6)), ((0, 6.4), (4, 8.6)), ((0, 11), (-4.4, 18)), ((0, 11), (4.4, 18))],
        }[pose]
        for a, b in limbs:
            self.line(*S(*a), *S(*b), w * 0.9, v)

    def shift(self, dx, dy):
        self.px = {(x + dx, y + dy): v for (x, y), v in self.px.items()}


# ---- the lighting pass -------------------------------------------------------------------------------------------

def _n4(x, y):
    return ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))


def light_glyph(c, g, pal, glow=False, shadow=True, room=None, flat=False):
    """Draws glyph g onto canvas c with the shared lighting. room: pixels the glyph's outline/halo may touch."""
    px = g.px
    r = pal.r
    W, H = c.w, c.h
    ok = (lambda q: 0 <= q[0] < W and 0 <= q[1] < H and (room is None or q in room))
    outline = {q for p in px for q in _n4(*p) if q not in px and ok(q)}
    body = set(px) | outline
    # glow halo (energy-like powers glow everywhere; hot pixels always glow a little)
    sources = [p for p, v in px.items() if v == HOT or (glow and v == MAIN)]
    if sources:
        reach = 3.4 if glow else 2.3
        for y in range(H):
            for x in range(W):
                q = (x, y)
                if q in body or not ok(q):
                    continue
                d = min(math.hypot(x - a, y - b) for a, b in sources)
                if d <= reach:
                    t = 0.5 if d <= 2.3 else 0.26
                    c.set(x, y, mix(c.get(x, y), r[3] if glow else r[4], t))
    if shadow and not glow:
        for (x, y) in body:
            q = (x + 1, y + 1)
            if q not in body and ok(q):
                c.set(q[0], q[1], mix(c.get(*q), BLACK, 0.5))
    for q in outline:
        c.set(q[0], q[1], pal.outline if not glow else mix(pal.outline, r[1], 0.45))
    tones = {MAIN: (r[5], r[4], r[3]), HOT: (WHITE, pal.hot, r[5]), DIM: (r[4], r[3], r[2])}
    if flat:  # a lit silhouette: small glyphs read better without inner shading
        tones = {MAIN: (pal.hot, r[5], r[5]), HOT: (WHITE, WHITE, pal.hot), DIM: (r[4], r[4], r[3])}
    for (x, y), v in px.items():
        hi, mid, lo = tones[v]
        lit = px.get((x - 1, y)) is None or px.get((x, y - 1)) is None
        dark = px.get((x + 1, y)) is None or px.get((x, y + 1)) is None
        col = hi if lit and not dark else lo if dark and not lit else mid
        if lit and dark:  # 1px-thin spots: lean on which side faces the light
            col = hi if px.get((x - 1, y - 1)) is None else mid
        c.set(x, y, col)
    # one specular glint on the main body: the lit pixel nearest the light
    mains = [] if flat else [p for p, v in px.items() if v == MAIN and (px.get((p[0] - 1, p[1])) is None or px.get((p[0], p[1] - 1)) is None)]
    if mains:
        gx, gy = min(mains, key=lambda p: p[0] * 0.6 + p[1] * 0.8)
        c.set(gx, gy, WHITE)
        for q in ((gx + 1, gy), (gx, gy + 1)):
            if q in px and px[q] == MAIN:
                c.set(q[0], q[1], mix(r[5], WHITE, 0.55))
                break
    return c


def icon(family, draw, glow=False):
    """A finished 32x32 power icon. draw(g) paints the glyph on a G centred on the face."""
    c, f, pal = frame_canvas(family, glow_centre=glow)
    g = G(scale=f.scale, ox=f.offset[0], oy=f.offset[1])
    draw(g)
    room = f.inside  # the glyph may overlap the rim (it floats above it) but never leaves the frame
    g.px = {q: v for q, v in g.px.items() if q in room}
    return light_glyph(c, g, pal, glow=glow, room=room)


# ---- Power Core crystal ------------------------------------------------------------------------------------------

CRYSTAL = [(16, 0.4), (28, 7.4), (28, 24.6), (16, 31.6), (4, 24.6), (4, 7.4)]
_TABLE = [(10, 9), (22, 9), (22, 23), (10, 23)]
# the crystal's facets and their ramp index (light from the top-left); "T" is the table the glyph floats behind
_FACETS = [
    ([(16, 0.4), (4, 7.4), (10, 9)], 5), ([(16, 0.4), (10, 9), (22, 9)], 4), ([(16, 0.4), (22, 9), (28, 7.4)], 3),
    ([(4, 7.4), (10, 9), (10, 23), (4, 24.6)], 3), ([(28, 7.4), (22, 9), (22, 23), (28, 24.6)], 1),
    ([(4, 24.6), (10, 23), (16, 31.6)], 2), ([(10, 23), (22, 23), (16, 31.6)], 1), ([(22, 23), (28, 24.6), (16, 31.6)], 0),
    (_TABLE, "T"),
]


def core(family, draw=None, glow_only=False):
    """The Power Core: a faceted crystal with the power's glyph floating inside, burning in its family colour.

    glow_only=True returns just the emissive pixels (the glyph and its halo) for the item model's light layer."""
    pal = Palette(family)
    r = pal.r
    c = Canvas(N, N)
    sdf = sd_poly(CRYSTAL)
    inside = {(x, y) for y in range(N) for x in range(N) if sdf(x + .5, y + .5) <= 0}
    edge = {q for q in inside if any(n not in inside for n in _n4(*q))}
    facets = [(sd_poly(p), k) for p, k in _FACETS]
    table = set()
    for (x, y) in inside:
        px, py = x + .5, y + .5
        k = min(facets, key=lambda fk: fk[0](px, py))[1]
        if (x, y) in edge:
            col = pal.outline
        elif k == "T":
            table.add((x, y))
            rr = math.hypot(px - 16, py - 16)
            col = pal.face[4] if rr < 4 else pal.face[3] if rr < 6.5 else pal.face[2]
        else:
            col = mix(r[k], pal.rim[k], 0.3)
        c.set(x, y, col)
    # a glass streak down the lit face, glints on the top facets
    for y in (11, 12, 13, 14, 15, 18, 19):
        c.set(6, y, mix(r[5], WHITE, 0.25))
    c.set(9, 4, WHITE)
    c.set(10, 4, r[5])
    c.set(9, 5, r[5])
    room = table | {(x, y) for (x, y) in inside - edge if 8 <= x <= 23 and 8 <= y <= 24}
    if draw is not None:
        g = G(scale=0.56, oy=0.5)
        draw(g)
        g.px = {q: v for q, v in g.px.items() if q in room}
        light_glyph(c, g, pal, glow=True, room=room, flat=True)
    else:
        for (x, y) in table:
            rr = math.hypot(x + .5 - 16, y + .5 - 16)
            if rr < 3.2:
                c.set(x, y, pal.hot if rr < 1.8 else r[5])
    if glow_only:
        out = Canvas(N, N)
        base = sum(pal.face[4][:3])
        for (x, y) in room:
            col = c.get(x, y)
            if sum(col[:3]) > base + 40:
                out.set(x, y, col)
        return out
    return c
