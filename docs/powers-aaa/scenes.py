"""Storyboard panels for the Powers in Full Voice plan: blocky side-on SVG scenes, drawn from code so they stay editable.

Nothing here is game footage. Each board is four panels and a caption per panel. build_page.py reads boards().
"""
import math
import random

W, H, B = 320, 190, 16
_uid = [0]

ORANGE, RED, SKY, BLUE, VIOLET, PINK, GOLD, JADE = "#E08F1F", "#FF4A3D", "#A7CDE3", "#7FC3FF", "#9A6BFF", "#D088C2", "#F2C14E", "#86E0A0"


def shade(c, k):
    c = c.lstrip("#")
    r, g, b = [int(c[i:i + 2], 16) for i in (0, 2, 4)]
    return "#%02x%02x%02x" % tuple(max(0, min(255, int(v * k))) for v in (r, g, b))


def n(v):
    s = "%.1f" % v
    return s[:-2] if s.endswith(".0") else s


# forward-positive degrees: front arm, back arm, front leg, back leg, torso lean, crouch drop
POSES = {
    "stand": (6, -6, 3, -3, 0, 0),
    "wind": (-138, 28, 24, -20, -12, 3),
    "punch": (94, -52, 32, -34, 16, 3),
    "stomp": (-34, 34, 74, -4, -5, 0),
    "land": (46, -46, 52, -52, 18, 8),
    "reach": (88, -14, 10, -12, 3, 0),
    "send": (122, -36, 26, -26, 10, 0),
    "step": (34, -38, 38, -34, 7, 0),
    "fold": (148, 118, 86, 58, 64, 0),
    "float": (62, -62, 20, -24, -6, 0),
}


class P:
    def __init__(self, seed, top="#10132a", bot="#2b2a52", gy=146):
        _uid[0] += 1
        self.id = "sb%d" % _uid[0]
        self.r = random.Random(seed)
        self.top, self.bot, self.gy = top, bot, gy
        self.o = []

    def add(self, s):
        self.o.append(s)
        return self

    def ridge(self, c="#1c1d38", base=None):
        base = self.gy if base is None else base
        x = -8
        while x < W:
            w = self.r.choice((32, 48, 64))
            h = self.r.randint(22, 70)
            self.add('<rect x="%d" y="%d" width="%d" height="%d" fill="%s"/>' % (x, base - h, w, h, c))
            x += w
        return self

    def ground(self, c="#4b4d58", cap="#62704f", lift=None):
        rows = 4
        for i in range(W // B + 1):
            x = i * B
            dy = lift(i) if lift else 0
            extra = int(math.ceil(dy / B))
            for j in range(rows + extra):
                y = self.gy + j * B - dy
                k = 0.86 + self.r.random() * 0.24
                col = shade(cap, k) if j == 0 else shade(c, k * (1 - 0.06 * j))
                self.add('<rect x="%d" y="%s" width="%d" height="%d" fill="%s"/>' % (x, n(y), B, B, col))
                if j == 0:
                    self.add('<rect x="%d" y="%s" width="%d" height="3" fill="%s"/>' % (x, n(y), B, shade(cap, 1.3)))
        return self

    def bricks(self, x0, top=20, c="#5a5560"):
        y = top
        row = 0
        while y < self.gy:
            x = x0 - (8 if row % 2 else 0)
            while x < W:
                self.add('<rect x="%d" y="%d" width="15" height="11" fill="%s"/>' % (x, y, shade(c, 0.82 + self.r.random() * 0.3)))
                x += 16
            y += 12
            row += 1
        return self

    def roof(self, h=62, c="#454450"):
        for i in range(W // B + 1):
            for j in range(h // B + 1):
                self.add('<rect x="%d" y="%d" width="%d" height="%d" fill="%s"/>' % (i * B, h - (j + 1) * B, B, B, shade(c, 0.82 + self.r.random() * 0.3)))
        return self

    def block(self, x, y, c, s=9, rot=0, op=1):
        self.add('<g transform="rotate(%s %s %s)" opacity="%s"><rect x="%s" y="%s" width="%s" height="%s" fill="%s"/><rect x="%s" y="%s" width="%s" height="2" fill="%s"/></g>' % (
            n(rot), n(x + s / 2), n(y + s / 2), n(op), n(x), n(y), n(s), n(s), c, n(x), n(y), n(s), shade(c, 1.35)))
        return self

    def chunks(self, x, y, count, spread, c, s=(5, 11)):
        for _ in range(count):
            self.block(x + self.r.uniform(-spread, spread), y + self.r.uniform(-spread * 0.6, spread * 0.6), shade(c, self.r.uniform(.8, 1.2)),
                       self.r.uniform(*s), self.r.uniform(0, 80))
        return self

    def _limb(self, px, py, l, w, deg, c):
        return '<rect x="%s" y="0" width="%s" height="%s" rx="1" fill="%s" transform="translate(%s %s) rotate(%s)"/>' % (n(-w / 2), n(w), n(l), c, n(px), n(py), n(-deg))

    def person(self, x, pose="stand", face=1, s=1, skin="#d9b48f", shirt="#3f6fb0", legs="#2b2f4a", eye="#191926", y=None, op=1, hair="#2a1d16"):
        y = self.gy if y is None else y
        af, ab, lf, lb, lean, drop = POSES[pose]
        g = ['<g transform="translate(%s %s) scale(%s %s)" opacity="%s">' % (n(x), n(y + drop * s), n(face * s), n(s), n(op))]
        g.append(self._limb(-1, -20, 20, 7, lb, shade(legs, .78)))
        g.append('<g transform="rotate(%s 0 -20)">' % n(lean))
        g.append(self._limb(-1, -38, 18, 5, ab, shade(skin, .74)))
        g.append("</g>")
        g.append(self._limb(1, -20, 20, 7, lf, legs))
        g.append('<g transform="rotate(%s 0 -20)">' % n(lean))
        g.append('<rect x="-5" y="-40" width="10" height="21" fill="%s"/>' % shirt)
        g.append('<rect x="-6" y="-53" width="13" height="13" fill="%s"/><rect x="-6" y="-53" width="13" height="4" fill="%s"/><rect x="3" y="-47" width="3" height="3" fill="%s"/>' % (skin, hair, eye))
        g.append(self._limb(1, -38, 18, 5, af, skin))
        g.append("</g></g>")
        return self.add("".join(g))

    def mob(self, x, pose="stand", face=-1, y=None, op=1, s=1):
        return self.person(x, pose, face, s, "#6f8a5a", "#3d4a36", "#2c3328", "#ff5a3c", y, op, "#55703f")

    def beast(self, x, arm=20, face=-1, s=1, col="#4a2f3f", eye="#ff6a4a", op=1, lean=0):
        g = ['<g transform="translate(%s %s) scale(%s %s)" opacity="%s">' % (n(x), self.gy, n(face * s), n(s), n(op))]
        dk = shade(col, .72)
        g.append('<rect x="-22" y="-26" width="16" height="26" fill="%s"/><rect x="6" y="-26" width="16" height="26" fill="%s"/>' % (dk, col))
        g.append('<g transform="rotate(%s 0 -26)">' % n(lean))
        g.append('<rect x="-30" y="-74" width="16" height="44" fill="%s" transform="rotate(-20 -22 -70)"/>' % dk)
        g.append('<rect x="-27" y="-78" width="54" height="56" fill="%s"/>' % col)
        g.append('<rect x="-27" y="-78" width="54" height="8" fill="%s"/>' % shade(col, 1.3))
        g.append('<rect x="-4" y="-104" width="28" height="27" fill="%s"/><rect x="14" y="-96" width="7" height="6" fill="%s"/>' % (shade(col, 1.12), eye))
        g.append('<polygon points="-4,-104 -14,-122 4,-104" fill="%s"/><polygon points="20,-104 30,-122 24,-104" fill="%s"/>' % (shade(col, 1.5), shade(col, 1.5)))
        g.append('<g transform="translate(16 -68) rotate(%s)"><rect x="-8" y="0" width="16" height="48" fill="%s"/><rect x="-13" y="44" width="26" height="24" fill="%s"/></g>' % (n(-arm), shade(col, 1.05), shade(col, .62)))
        g.append("</g></g>")
        return self.add("".join(g))

    def glow(self, x, y, r, c, op=.7):
        return self.add('<circle cx="%s" cy="%s" r="%s" fill="%s" opacity="%s" filter="url(#%sb)"/>' % (n(x), n(y), n(r), c, n(op), self.id))

    def ring(self, x, y, rx, ry, c, w=2, op=1, dash=None):
        d = ' stroke-dasharray="%s"' % dash if dash else ""
        return self.add('<ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="none" stroke="%s" stroke-width="%s" opacity="%s"%s/>' % (n(x), n(y), n(rx), n(ry), c, n(w), n(op), d))

    def star(self, x, y, r, c="#fff", op=1):
        q = r * .13
        pts = [(x, y - r), (x + q, y - q), (x + r, y), (x + q, y + q), (x, y + r), (x - q, y + q), (x - r, y), (x - q, y - q)]
        return self.add('<polygon points="%s" fill="%s" opacity="%s"/>' % (" ".join("%s,%s" % (n(a), n(b)) for a, b in pts), c, n(op)))

    def lines(self, x, y, count, r0, r1, c, w=2, op=.9, a0=0, a1=360, inward=False):
        for i in range(count):
            a = math.radians(a0 + (a1 - a0) * (i + self.r.uniform(.15, .85)) / count)
            ri = r0 * self.r.uniform(.9, 1.15)
            ro = r1 * self.r.uniform(.8, 1.1)
            self.add('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s" opacity="%s" stroke-linecap="square"/>' % (
                n(x + math.cos(a) * ri), n(y + math.sin(a) * ri), n(x + math.cos(a) * ro), n(y + math.sin(a) * ro), c, n(w), n(op)))
        return self

    def cracks(self, x, y, count, r, c="#17151d", w=2):
        for i in range(count):
            a = math.radians(self.r.uniform(95, 265))
            pts = [(x, y)]
            d = 0
            cx, cy = x, y
            while d < r:
                step = self.r.uniform(9, 17)
                a += self.r.uniform(-.55, .55)
                cx += math.cos(a) * step
                cy += math.sin(a) * step
                d += step
                pts.append((cx, cy))
            self.add('<polyline points="%s" fill="none" stroke="%s" stroke-width="%s"/>' % (" ".join("%s,%s" % (n(a1), n(b1)) for a1, b1 in pts), c, n(w)))
        return self

    def orb(self, x, y, c, r=8):
        self.glow(x, y, r * 2.2, c, .85)
        self.add('<circle cx="%s" cy="%s" r="%s" fill="%s"/><circle cx="%s" cy="%s" r="%s" fill="#fff"/>' % (n(x), n(y), n(r), c, n(x), n(y), n(r * .55)))
        return self

    def trail(self, x0, y0, x1, y1, c, w=7, parts=5):
        for i in range(parts):
            t0, t1 = i / parts, (i + 1) / parts
            self.add('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s" opacity="%s"/>' % (
                n(x0 + (x1 - x0) * t0), n(y0 + (y1 - y0) * t0), n(x0 + (x1 - x0) * t1), n(y0 + (y1 - y0) * t1), c, n(w * (.35 + .65 * t1)), n(.12 + .75 * t1)))
        return self

    def well(self, x, y, r, c=VIOLET):
        self.glow(x, y, r * 1.6, c, .7)
        self.add('<circle cx="%s" cy="%s" r="%s" fill="#07060f"/>' % (n(x), n(y), n(r)))
        for k, d in ((1.0, "9 7"), (1.34, "15 11"), (1.72, "22 18")):
            self.ring(x, y, r * k, r * k * .72, c, 2.2 if k == 1.0 else 1.6, 1 if k == 1.0 else .7, d)
        return self

    def flash(self, c="#fff", op=.15):
        return self.add('<rect width="%d" height="%d" fill="%s" opacity="%s"/>' % (W, H, c, n(op)))

    def bar(self, x, y, w, frac, c, label=None, broken=False):
        if label:
            self.text(x, y - 3, label, "#e9e4d8", 8, ls=1)
        self.add('<rect x="%s" y="%s" width="%s" height="6" fill="#0b0c16" opacity=".85"/>' % (n(x), n(y), n(w)))
        if broken:
            self.add('<rect x="%s" y="%s" width="%s" height="6" fill="%s" transform="rotate(-7 %s %s)"/>' % (n(x), n(y - 2), n(w * .42), c, n(x), n(y)))
            self.add('<rect x="%s" y="%s" width="%s" height="6" fill="%s" transform="rotate(9 %s %s)"/>' % (n(x + w * .54), n(y + 2), n(w * .46), c, n(x + w), n(y)))
            self.star(x + w * .48, y + 3, 11, "#fff")
        else:
            self.add('<rect x="%s" y="%s" width="%s" height="6" fill="%s"/>' % (n(x), n(y), n(w * frac), c))
        return self

    def text(self, x, y, t, c="#fff", size=11, anchor="start", weight=500, ls=0):
        return self.add('<text x="%s" y="%s" fill="%s" font-size="%s" font-weight="%s" text-anchor="%s" letter-spacing="%s" font-family="Chakra Petch, Arial Narrow, sans-serif">%s</text>' % (n(x), n(y), c, n(size), weight, anchor, n(ls), t))

    def box(self, x, y, w, h, fill="#0c0d1a", stroke=None, op=.92):
        s = ' stroke="%s" stroke-width="1.5"' % stroke if stroke else ""
        return self.add('<rect x="%s" y="%s" width="%s" height="%s" rx="3" fill="%s" opacity="%s"%s/>' % (n(x), n(y), n(w), n(h), fill, n(op), s))

    def cliff(self, x_end, c="#4b4d58", cap="#62704f"):
        """Ground that stops at x_end: a drop into the valley."""
        for i in range(x_end // B):
            x = i * B
            for j in range(4):
                k = 0.86 + self.r.random() * 0.24
                self.add('<rect x="%d" y="%d" width="%d" height="%d" fill="%s"/>' % (x, self.gy + j * B, B, B, shade(cap, k) if j == 0 else shade(c, k)))
        return self

    def road(self, pts, c="#D9BA80", w=8, gap=13, fade=True, fall=0):
        """A strip of hard-light tiles along a polyline; the oldest tiles are the dimmest, and the last `fall` of them are dropping."""
        segs, total = [], 0.0
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            d = math.hypot(x1 - x0, y1 - y0)
            segs.append((x0, y0, x1, y1, d))
            total += d
        at, tiles = gap / 2, []
        while at < total:
            rem = at
            for x0, y0, x1, y1, d in segs:
                if rem <= d:
                    t = rem / d
                    tiles.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, math.degrees(math.atan2(y1 - y0, x1 - x0)), at / total))
                    break
                rem -= d
            at += gap
        for i, (x, y, a, f) in enumerate(tiles):
            op = (.3 + .7 * f) if fade else .95
            dy = 0
            if i < fall:
                dy = (fall - i) * 7 + self.r.uniform(0, 8)
                a += self.r.uniform(-34, 34)
            self.add('<g transform="translate(%s %s) rotate(%s)" opacity="%s"><rect x="%s" y="%s" width="%s" height="%s" fill="%s"/><rect x="%s" y="%s" width="%s" height="2" fill="#fff4dc"/></g>' % (
                n(x), n(y + dy), n(a), n(op), n(-gap / 2 + .5), n(-w / 2), n(gap - 1), n(w), c, n(-gap / 2 + .5), n(-w / 2), n(gap - 1)))
        return self

    def arrow(self, x, y, deg, c="#c9c9d0", l=20):
        a = math.radians(deg)
        x1, y1 = x + math.cos(a) * l, y + math.sin(a) * l
        self.add('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="2"/>' % (n(x), n(y), n(x1), n(y1), c))
        return self.add('<circle cx="%s" cy="%s" r="2.5" fill="%s"/>' % (n(x1), n(y1), c))

    def tree(self, x, y, s=1, c="#2f4230"):
        self.add('<rect x="%s" y="%s" width="%s" height="%s" fill="#3a2c22"/>' % (n(x - 1.5 * s), n(y - 12 * s), n(3 * s), n(12 * s)))
        return self.add('<rect x="%s" y="%s" width="%s" height="%s" fill="%s"/>' % (n(x - 7 * s), n(y - 24 * s), n(14 * s), n(14 * s), c))

    def svg(self, label):
        i = self.id
        defs = ('<defs><linearGradient id="%sg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient>'
                '<filter id="%sb" x="-80%%" y="-80%%" width="260%%" height="260%%"><feGaussianBlur stdDeviation="8"/></filter></defs>') % (i, self.top, self.bot, i)
        return '<svg viewBox="0 0 %d %d" role="img" aria-label="%s">%s<rect width="%d" height="%d" fill="url(#%sg)"/>%s</svg>' % (W, H, label, defs, W, H, i, "".join(self.o))


def haymaker():
    p1 = P(11, "#15131f", "#3a2a2f").ridge("#241f2c").ground("#4b4a52", "#5d6048")
    p1.glow(72, 101, 20, ORANGE, .75).person(96, "wind").mob(226, "stand").bar(16, 22, 90, .68, ORANGE, "CHARGE")
    p1.lines(72, 100, 8, 12, 24, "#ffd08a", 1.5, .8, 180, 360)

    p2 = P(12, "#15131f", "#4a2e2a").ridge("#241f2c").ground("#4b4a52", "#5d6048")
    p2.glow(72, 101, 36, "#ffe3b0", .9).person(96, "wind").mob(226, "wind").star(72, 101, 34).bar(16, 22, 90, 1, "#fff0c8", "CHARGE")
    p2.ring(72, 101, 24, 24, "#fff", 1.5, .7)

    p3 = P(13, "#2a1a14", "#6a3a20").ridge("#3a2820").ground("#4b4a52", "#5d6048")
    p3.glow(198, 104, 52, "#fff1d0", .95).person(168, "punch").mob(214, "fold", y=132)
    p3.lines(198, 104, 22, 26, 72, "#fff", 2.4, .95).ring(198, 104, 34, 34, "#fff", 3, .9).flash("#ffe3b0", .13).star(198, 104, 28)

    p4 = P(14, "#15131f", "#3a2a2f").ridge("#241f2c").ground("#4b4a52", "#5d6048").bricks(280, 16)
    p4.cracks(282, 98, 8, 56).person(96, "punch", op=.75).mob(268, "fold", y=118).chunks(268, 104, 12, 22, "#5a5560")
    p4.trail(120, 112, 256, 104, ORANGE, 9).bar(16, 22, 90, 1, "#ffb347", "STANCE", broken=True).glow(282, 98, 26, ORANGE, .55)
    return [p1, p2, p3, p4]


def ground_wave():
    gc, cap = "#54483c", "#6f8a4a"
    p1 = P(21, "#1a2340", "#3e4d6a").ridge("#28344e").ground(gc, cap)
    p1.person(54, "stomp").mob(250, "stand").chunks(74, 140, 5, 8, cap).glow(70, 146, 18, ORANGE, .5)

    f2 = lambda i: max(0, 20 * math.exp(-((i - 7) ** 2) / 3.2)) + max(0, 7 * math.exp(-((i - 4.6) ** 2) / 2))
    p2 = P(22, "#1a2340", "#3e4d6a").ridge("#28344e").ground(gc, cap, f2)
    p2.person(54, "stand").mob(250, "stand").chunks(120, 112, 9, 15, cap, (5, 12)).lines(118, 118, 7, 12, 26, "#d9c9a0", 2, .7, 200, 340)

    f3 = lambda i: max(0, 34 * math.exp(-((i - 14.6) ** 2) / 3)) + max(0, 9 * math.exp(-((i - 10.5) ** 2) / 2.4))
    p3 = P(23, "#1a2340", "#3e4d6a").ridge("#28344e").ground(gc, cap, f3)
    p3.person(54, "stand").mob(238, "float", y=80).chunks(232, 94, 14, 26, cap, (6, 13)).lines(236, 104, 9, 18, 40, "#ffe0a0", 2.4, .85, 190, 350)
    p3.glow(236, 112, 26, ORANGE, .5)

    f4 = lambda i: max(0, 6 * math.exp(-((i - 17) ** 2) / 2.5))
    p4 = P(24, "#1a2340", "#3e4d6a").ridge("#28344e").ground(gc, cap, f4)
    p4.person(70, "reach").mob(238, "float", y=62).ring(238, 72, 24, 9, ORANGE, 2.5, .95).ring(238, 80, 17, 6, ORANGE, 2, .6)
    p4.chunks(214, 128, 6, 14, cap, (5, 9)).text(238, 28, "LIFTED", "#ffd08a", 11, "middle", 700, 2)
    return [p1, p2, p3, p4]


def catch():
    p1 = P(31, "#101630", "#233a55").ridge("#1a2442").ground("#3f4452", "#4d5866")
    p1.beast(262, 110).person(54, "stand").trail(214, 92, 142, 98, "#ff9a6a", 8).orb(138, 98, "#ff9a6a")

    p2 = P(32, "#0e1a34", "#2b5068").ridge("#1a2c48").ground("#3f4452", "#4d5866")
    p2.beast(262, 110, op=.85).person(54, "reach").orb(98, 98, "#ff9a6a")
    for r, o in ((16, .95), (27, .6), (40, .32)):
        p2.ring(98, 98, r, r, SKY, 2, o)
    p2.lines(98, 98, 12, 44, 54, SKY, 1.5, .55).flash(SKY, .07)

    p3 = P(33, "#101630", "#233a55").ridge("#1a2442").ground("#3f4452", "#4d5866")
    p3.beast(262, 60).person(54, "send").trail(80, 98, 196, 92, SKY, 13, 6).trail(80, 92, 192, 86, "#fff", 4, 6).orb(200, 92, SKY, 9)

    p4 = P(34, "#1a1a38", "#40506e").ridge("#22284a").ground("#3f4452", "#4d5866")
    p4.beast(266, 30, lean=-13).person(54, "stand").glow(236, 88, 44, SKY, .85).lines(236, 88, 20, 22, 62, "#fff", 2.2, .9)
    p4.star(236, 88, 26).chunks(244, 86, 7, 18, "#6a4258").bar(214, 22, 90, .36, SKY, "STANCE")
    return [p1, p2, p3, p4]


def star():
    gc, cap = "#3a3650", "#4c4768"
    p1 = P(41, "#120f28", "#2c2452").ridge("#1c1838").ground(gc, cap)
    p1.well(170, 106, 18).mob(112, "float", face=1, y=140).mob(226, "float", y=140).person(44, "reach")
    p1.lines(170, 106, 16, 34, 58, VIOLET, 1.5, .7)

    p2 = P(42, "#100c26", "#241e4a").ridge("#1c1838").ground(gc, cap)
    p2.well(170, 106, 15).mob(146, "float", face=1, y=134).mob(194, "float", y=134).person(44, "stand")
    p2.lines(170, 106, 18, 60, 22, BLUE, 2, .95, inward=True).ring(170, 106, 30, 30, BLUE, 2, .8, "3 5").glow(170, 106, 13, "#dff0ff", 1).star(170, 106, 24)

    p3 = P(43, "#28386a", "#5a7ab0").ridge("#3a4c80").ground(gc, cap)
    p3.glow(170, 104, 86, "#e6f3ff", .95).ring(170, 104, 146, 98, "#fff", 5, .95).ring(170, 104, 104, 70, BLUE, 4, .8)
    p3.mob(26, "fold", y=108).mob(304, "fold", face=1, y=102).person(44, "stand", op=.75).lines(170, 104, 26, 66, 130, "#fff", 2, .85).flash("#d6e9ff", .16)

    p4 = P(44, "#120f28", "#2c2452").ridge("#1c1838").ground(gc, cap)
    p4.person(150, "stand").glow(170, 146, 30, VIOLET, .35).chunks(170, 136, 7, 36, "#2a2638", (5, 10))
    p4.box(62, 26, 196, 66, "#0c0d1a", VIOLET).text(160, 46, "DUET", "#c9b2ff", 11, "middle", 700, 3)
    p4.text(160, 66, "Collapsing Star", "#fff", 18, "middle", 700).text(160, 83, "DUETS FOUND  1 / ??", "#a9a4bd", 9, "middle", 500, 1)
    return [p1, p2, p3, p4]


def foresight():
    gc, cap = "#3b3644", "#544a5e"
    p1 = P(51, "#1a1226", "#3a2a4a").ridge("#241a34").ground(gc, cap)
    p1.beast(246, 150).person(176, "stand")

    p2 = P(52, "#1a1226", "#47304e").ridge("#241a34").ground(gc, cap)
    p2.beast(246, 150, op=.92).beast(246, 32, col="#b8902e", eye="#fff0b8", op=.55).person(176, "stand")
    p2.ring(188, 146, 44, 9, GOLD, 2.5, .9).ring(188, 146, 27, 6, GOLD, 2, .6).lines(206, 98, 7, 8, 26, GOLD, 1.5, .6)

    p3 = P(53, "#1a1226", "#3a2a4a").ridge("#241a34").ground(gc, cap)
    p3.beast(246, 32).person(92, "step").chunks(188, 134, 10, 22, "#544a5e").ring(188, 146, 46, 9, "#e9e4d8", 2.5, .9).cracks(188, 148, 5, 26, "#0f0d14")

    p4 = P(54, "#241530", "#5a3460").ridge("#2a1c3a").ground(gc, cap)
    p4.beast(256, 32, lean=-6).person(160, "punch").glow(199, 100, 34, PINK, .85).star(199, 100, 24).ring(199, 100, 28, 28, PINK, 2.5, .95).ring(199, 100, 40, 40, PINK, 1.5, .5)
    p4.text(62, 34, "CLEAN", "#f5d9ee", 16, "start", 700, 3).bar(214, 22, 90, .58, PINK, "STANCE")
    return [p1, p2, p3, p4]


def ghost(p, x, pose="stand", face=1, y=None, op=1, s=1):
    return p.person(x, pose, face, s, "#fff4dc", "#fff0cc", "#e8d9b4", "#F2C14E", y, op, "#F2C14E")


def grey(p, x, pose="stand", face=-1, y=None):
    return p.person(x, pose, face, 1, "#9a9aa2", "#6a6a72", "#4c4c54", "#d8d8e0", y, 1, "#7c7c84")


def titan():
    gc, cap = "#4a4a54", "#5e6a52"
    p1 = P(61, "#141a30", "#33405a").ridge("#20283e").ground(gc, cap)
    p1.person(92, "stand").beast(254, 40).mob(180, "stand")
    p1.glow(32, 32, 20, GOLD, .7).ring(32, 32, 15, 15, GOLD, 4).text(32, 37, "R", "#fff", 14, "middle", 700)

    p2 = P(62, "#141a30", "#33405a").ridge("#20283e").ground(gc, cap)
    for s, o in ((1, .25), (1.45, .35), (2, .45)):
        p2.person(100, "stand", s=s, op=o)
    p2.person(100, "stand", s=2.6).glow(100, 146, 30, ORANGE, .5).chunks(100, 138, 10, 40, cap).lines(100, 146, 9, 34, 60, "#d9c9a0", 2, .7, 190, 350).beast(262, 40, s=.8)

    p3 = P(63, "#201a2a", "#5a4040").ridge("#2c2230").ground(gc, cap)
    p3.person(96, "punch", s=2.6).add('<path d="M170 28 Q262 70 232 146" fill="none" stroke="#ffd08a" stroke-width="9" opacity=".65"/>')
    p3.mob(218, "fold", y=92).mob(262, "fold", y=70).mob(296, "fold", y=118).chunks(250, 126, 10, 36, cap).flash("#ffe3b0", .07)

    p4 = P(64, "#141a30", "#33405a").ridge("#20283e").ground(gc, cap)
    p4.person(96, "send", s=2.6).trail(150, 60, 286, 22, "#ffd08a", 8).mob(292, "fold", y=44).tree(190, 146, 1.6).beast(258, 40, s=.72)
    p4.chunks(150, 140, 7, 24, cap)
    return [p1, p2, p3, p4]


def last_round():
    gc, cap = "#4a4a54", "#5e6a52"
    p1 = P(71, "#141a30", "#33405a").ridge("#20283e").ground(gc, cap)
    p1.add('<line x1="26" y1="144" x2="160" y2="144" stroke="%s" stroke-width="2" stroke-dasharray="4 5" opacity=".4"/>' % GOLD)
    p1.person(178, "punch").beast(258, 60).glow(214, 100, 22, ORANGE, .6).star(214, 100, 16).bar(16, 22, 110, 1, GOLD, "LAST 8 SECONDS")

    p2 = P(72, "#141a30", "#33405a").ridge("#20283e").ground(gc, cap)
    p2.add('<line x1="36" y1="144" x2="160" y2="144" stroke="%s" stroke-width="3.5"/>' % GOLD).glow(36, 124, 34, GOLD, .85)
    ghost(p2, 36, "land").person(178, "stand", face=-1).beast(258, 40)
    p2.glow(32, 32, 20, GOLD, .7).ring(32, 32, 15, 15, GOLD, 4).text(32, 37, "R", "#fff", 14, "middle", 700)

    p3 = P(73, "#161a34", "#3a4464").ridge("#20283e").ground(gc, cap)
    p3.glow(190, 104, 30, GOLD, .55)
    ghost(p3, 186, "punch").person(84, "send").trail(106, 96, 226, 86, BLUE, 11, 6).orb(232, 86, BLUE, 8).beast(264, 60)
    p3.star(222, 100, 16, "#fff4dc")

    p4 = P(74, "#2a2438", "#6a5a78").ridge("#2c2438").ground(gc, cap)
    p4.glow(246, 92, 54, "#fff4dc", .95).lines(246, 92, 22, 24, 72, "#fff", 2.3, .9).star(246, 92, 30).beast(268, 30, lean=-13, op=.9)
    ghost(p4, 186, "stand", op=.32).person(84, "stand").chunks(190, 92, 9, 28, GOLD, (3, 5)).bar(16, 22, 110, 1, "#ffb347", "STANCE", broken=True).flash("#fff4dc", .08)
    return [p1, p2, p3, p4]


def say_back():
    gc, cap = "#40303a", "#5a4048"
    fire = "#ff9a5a"
    p1 = P(81, "#1a1226", "#3e2438").ridge("#26182e").ground(gc, cap)
    p1.beast(250, 150, col="#6a2e2a", eye="#ffd27a").person(112, "stand").ring(112, 146, 46, 9, RED, 2.5, .95).ring(112, 146, 28, 6, RED, 2, .6)
    p1.lines(208, 56, 7, 8, 24, fire, 1.5, .7).text(16, 30, "SIGNATURE MOVE", "#ffb3a8", 11, "start", 700, 2)

    p2 = P(82, "#1a1226", "#3e2438").ridge("#26182e").ground(gc, cap)
    p2.beast(250, 22, col="#6a2e2a", eye="#ffd27a").chunks(160, 136, 12, 26, "#5a4048").cracks(162, 148, 5, 28, "#0f0d14").glow(164, 142, 30, fire, .5).person(58, "step")
    p2.box(14, 14, 118, 34, "#0c0d1a", PINK).add('<rect x="22" y="21" width="20" height="20" fill="%s"/>' % fire).text(50, 30, "COPY HELD", "#f5d9ee", 9, "start", 700, 1).text(50, 42, "15 seconds", "#e9e4d8", 10)

    p3 = P(83, "#20162c", "#54304a").ridge("#26182e").ground(gc, cap)
    p3.person(84, "send").glow(170, 104, 56, fire, .45)
    p3.add('<g opacity=".8" transform="translate(98 92) rotate(-62)"><rect x="-13" y="0" width="26" height="118" fill="#ffc9a0"/><rect x="-13" y="0" width="6" height="118" fill="#fff0e0"/><rect x="-21" y="112" width="42" height="36" fill="#ffe2c8"/></g>')
    p3.beast(272, 110, col="#6a2e2a", eye="#ffd27a")

    p4 = P(84, "#2a1a30", "#6a3a52").ridge("#2a1c3a").ground(gc, cap)
    p4.person(84, "stand").glow(246, 92, 46, fire, .9).lines(246, 92, 20, 22, 64, "#fff", 2.2, .9).star(246, 92, 28).beast(268, 26, col="#6a2e2a", eye="#ffd27a", lean=-13)
    p4.bar(16, 22, 110, .84, PINK, "STANCE, DOUBLED").text(16, 52, "It can't use that for 20 s", "#f5d9ee", 10)
    return [p1, p2, p3, p4]


def clock():
    t, b, r, g, cp = "#23242c", "#3a3b44", "#2b2c34", "#55565c", "#66676c"

    def face(p, left):
        p.ring(290, 30, 18, 18, "#fff", 2.5, .9).add('<line x1="290" y1="30" x2="290" y2="17" stroke="#fff" stroke-width="2"/>').text(290, 58, left, "#fff", 12, "middle", 700)

    p1 = P(91, t, b).ridge(r).ground(g, cp)
    grey(p1, 210, "float", y=118).person(92, "stand", shirt="#c23a30")
    for x, y, d in ((140, 90, 172), (168, 72, 180), (150, 112, 190), (178, 104, 176)):
        p1.arrow(x, y, d)
    face(p1, "3")

    p2 = P(92, t, b).ridge(r).ground(g, cp)
    grey(p2, 218, "float", y=118).person(186, "punch", shirt="#c23a30")
    for x, y in ((62, 84), (74, 106), (54, 118)):
        p2.orb(x, y, "#ff8a7a", 5)
    p2.star(222, 92, 11, "#fff").star(232, 104, 8, "#fff").star(212, 104, 7, "#fff").text(16, 30, "HELD 6", "#fff", 12, "start", 700, 2)
    face(p2, "2")

    p3 = P(93, t, b).ridge(r).ground(g, cp)
    p3.beast(236, 60, col="#55555e", eye="#d8d8e0").person(82, "reach", shirt="#c23a30")
    for k in range(9):
        a = math.radians(k * 40 + 10)
        p3.orb(236 + math.cos(a) * 58, 84 + math.sin(a) * 42, "#ff8a7a", 4.5)
    p3.text(16, 30, "HELD 14", "#fff", 12, "start", 700, 2)
    face(p3, "0.4")

    p4 = P(94, "#3a1a18", "#a0482e").ridge("#4a2420").ground("#4b4a52", "#6f8a4a")
    p4.glow(236, 88, 90, "#fff1d0", .95).beast(258, 20, lean=-14).lines(236, 88, 30, 26, 120, "#fff", 2.6, .95).star(236, 88, 44).person(82, "stand").flash("#fff1d0", .2)
    return [p1, p2, p3, p4]


def _persp(seed, a, b, hz=72):
    p = P(seed, a, b, gy=hz).ridge("#2a3350", base=hz)
    p.add('<rect y="%d" width="320" height="%d" fill="#4f6240"/>' % (hz, H - hz))
    for i, y in enumerate((80, 90, 104, 124, 152)):
        p.add('<rect y="%d" width="320" height="%d" fill="%s"/>' % (y, 3 + i * 2, "#5a6e48" if i % 2 else "#46583a"))
    for x, y, s in ((40, 96, .6), (86, 86, .4), (232, 90, .45), (284, 110, .8), (262, 84, .3), (22, 134, 1.2), (306, 150, 1.4)):
        p.tree(x, y, s)
    return p


def long_cut():
    vx, hz = 196, 72

    def cut(p, near, far, y_front, c0="#0d0a12"):
        p.add('<polygon points="%d,%d %d,%d %s,%d %s,%d" fill="%s"/>' % (160 - near, 190, 160 + near, 190, n(vx + far), y_front, n(vx - far), y_front, c0))
        return p

    p1 = _persp(101, "#1d2c52", "#6a86b0")
    p1.add('<line x1="160" y1="190" x2="%d" y2="%d" stroke="#fff" stroke-width="1.6"/>' % (vx, hz)).glow(160, 176, 16, BLUE, .6).person(160, "reach", y=190, s=.9)
    p1.ring(160, 188, 62, 14, BLUE, 1.2, .5, "4 6")

    p2 = _persp(102, "#141e3a", "#46587e").flash("#05060c", .42)
    p2.add('<line x1="160" y1="190" x2="%d" y2="%d" stroke="%s" stroke-width="3"/>' % (vx, hz, GOLD)).glow(vx, hz, 16, GOLD, .9).person(160, "stand", y=190, s=.9)

    p3 = _persp(103, "#1d2c52", "#6a86b0")
    p3.add('<line x1="%d" y1="118" x2="%d" y2="%d" stroke="#fff" stroke-width="1.4"/>' % (vx - 13, vx, hz))
    cut(p3, 38, 3, 116)
    p3.add('<line x1="122" y1="190" x2="%d" y2="116" stroke="#ffb16a" stroke-width="3.5"/><line x1="198" y1="190" x2="%d" y2="116" stroke="#ffb16a" stroke-width="3.5"/>' % (vx - 3, vx + 3))
    p3.glow(vx - 14, 118, 26, "#ffe7c0", .95).chunks(vx - 14, 112, 14, 26, "#5a6e48", (4, 9)).lines(vx - 14, 118, 12, 10, 34, "#fff", 2, .85, 180, 360).person(160, "stand", y=190, s=.9, op=.9)

    p4 = P(104, "#1d2c52", "#7a92b8", gy=86).ridge("#2a3350", base=86)
    p4.add('<rect y="86" width="320" height="104" fill="#4f6240"/>')
    p4.add('<polygon points="20,190 96,190 318,88 306,88" fill="#0d0a12"/>')
    p4.add('<line x1="20" y1="190" x2="306" y2="88" stroke="#ffb16a" stroke-width="3"/><line x1="96" y1="190" x2="318" y2="88" stroke="#ffb16a" stroke-width="3"/>')
    for x, y in ((90, 150), (160, 122), (232, 104)):
        p4.glow(x, y, 16, "#ffe7c0", .35)
    p4.add('<rect x="182" y="168" width="150" height="24" fill="#3a4a30"/>').person(232, "stand", y=170, s=.55).tree(280, 170, 1.2).tree(204, 170, .9)
    return [p1, p2, p3, p4]


def road_ahead():
    ARC = "#D9BA80"
    gc, cap = "#4b4d58", "#62704f"
    p1 = P(111, "#1a2f55", "#6f94bd", gy=118).cliff(128, gc, cap)
    p1.add('<rect y="170" width="320" height="20" fill="#2c3e3a"/>').road([(128, 118), (170, 116), (206, 108)], ARC, fade=False).person(112, "step", y=118).glow(206, 108, 16, "#ffe6b8", .9)

    p2 = P(112, "#1a2f55", "#7fa6cd", gy=176).ridge("#31465f", base=176).ground("#39483c", "#4a5e44")
    p2.road([(0, 168), (50, 158), (100, 134), (148, 100), (196, 76), (232, 66)], ARC).person(226, "step", y=63).lines(190, 80, 6, 40, 70, "#fff", 1.6, .55, 130, 190)

    p3 = P(113, "#1a2f55", "#7fa6cd", gy=176).ridge("#31465f", base=176).ground("#39483c", "#4a5e44")
    p3.road([(10, 60), (70, 44), (140, 56), (190, 94), (178, 136), (118, 146), (84, 118)], ARC).person(92, "step", y=112, face=-1).lines(120, 130, 7, 20, 44, "#fff", 1.6, .5, 0, 90)
    p3.add('<rect x="226" y="92" width="52" height="84" fill="#3c3a44"/>').text(252, 84, "BOOM", "#ffe6b8", 13, "middle", 700, 2)

    p4 = P(114, "#241e36", "#7a5f70", gy=146).ridge("#2c2438").ground(gc, cap)
    p4.road([(0, 34), (50, 40), (104, 58)], ARC, fall=2).person(166, "punch", y=96).trail(106, 60, 158, 88, ARC, 9).beast(236, 80).glow(198, 88, 26, "#ffe6b8", .75).star(200, 88, 16)
    return [p1, p2, p3, p4]


def _bell(p, x, y, s=1, c="#d9b36a"):
    return p.add('<g transform="translate(%s %s) scale(%s)"><path d="M-7 8 Q-6 -8 0 -9 Q6 -8 7 8 L10 11 L-10 11 Z" fill="%s"/><circle cx="0" cy="13" r="2.4" fill="%s"/></g>' % (n(x), n(y), n(s), c, c))


def toll():
    gc, cap = "#3b3a54", "#4c4e68"
    deep = dict(skin="#4a3f66", shirt="#2b2342", legs="#1e1930", eye="#ff5a8a", hair="#39305a")
    p1 = P(121, "#1b2542", "#46527a").ridge("#28304e").ground(gc, cap)
    p1.person(236, "stand", -1, **deep).person(280, "stand", -1, **deep).person(44, "stand", 1, **deep).person(146, "send").star(172, 98, 13, GOLD).glow(172, 98, 14, GOLD, .7)

    p2 = P(122, "#101526", "#262d48").ridge("#1a2038").ground(gc, cap).flash("#05060c", .46)
    p2.person(236, "stand", -1, **deep).person(280, "stand", -1, **deep).person(44, "stand", 1, **deep).person(146, "send").ring(172, 98, 8, 8, GOLD, 1.5, .95)

    p3 = P(123, "#1b2542", "#5a668e").ridge("#28304e").ground(gc, cap)
    for rx, o, w in ((206, .4, 1.6), (150, .7, 2.2), (92, 1, 3)):
        p3.ring(172, 98, rx, rx * .72, GOLD, w, o)
    p3.person(146, "send").person(246, "fold", -1, y=132, **deep).person(292, "fold", -1, y=126, **deep).person(40, "fold", 1, y=130, **deep).chunks(172, 150, 10, 110, cap, (3, 7))

    p4 = P(124, "#2a3a62", "#7c8cb0", gy=150).ridge("#3a4668", base=150).ground("#3c4a42", "#4e6450")
    p4.add('<polygon points="222,150 262,70 300,150" fill="#30394f"/>')
    _bell(p4, 262, 58, 1.1).ring(262, 58, 20, 20, GOLD, 1.6, .85).ring(262, 58, 34, 34, GOLD, 1.1, .45)
    p4.person(60, "stand", s=.8).add('<line x1="84" y1="102" x2="228" y2="66" stroke="%s" stroke-width="1.5" stroke-dasharray="5 6" opacity=".7"/>' % GOLD).text(36, 34, "A BELL ANSWERS", "#ffe9b8", 12, "start", 700, 2)
    return [p1, p2, p3, p4]


def borrowed():
    gc, cap = "#3b3644", "#544a5e"
    hot = "#ff9ad6"

    def thread(p, x0, y0, x1, y1, w=2):
        mx, my = (x0 + x1) / 2, min(y0, y1) - 26
        p.add('<path d="M%s %s Q%s %s %s %s" fill="none" stroke="%s" stroke-width="%s"/>' % (n(x0), n(y0), n(mx), n(my), n(x1), n(y1), hot, n(w)))

    p1 = P(131, "#1a1226", "#3a2a4a").ridge("#241a34").ground(gc, cap)
    p1.person(72, "reach").beast(248, 40)
    thread(p1, 80, 96, 244, 46)
    p1.glow(244, 46, 16, hot, .8)

    p2 = P(132, "#1a1226", "#47304e").ridge("#241a34").ground(gc, cap)
    p2.person(56, "land").beast(190, 40, face=1, eye=hot).mob(288, "stand").mob(306, "wind")
    thread(p2, 56, 100, 202, 46)

    p3 = P(133, "#1f1430", "#553058").ridge("#241a34").ground(gc, cap)
    p3.person(40, "land", op=.8).beast(170, 102, face=1, eye=hot, lean=9).glow(228, 96, 34, hot, .55).mob(244, "fold", y=92).mob(288, "fold", y=56).chunks(258, 126, 9, 30, cap)
    thread(p3, 40, 100, 182, 46, 1.6)
    p3.box(14, 14, 86, 30, "#0c0d1a", hot).text(22, 34, "8 s left", "#f5d9ee", 13, "start", 700)

    p4 = P(134, "#1a1226", "#3a2a4a", gy=146).ridge("#241a34").ground(gc, cap)
    p4.add('<rect x="238" y="82" width="82" height="64" fill="#3c3a44"/><rect x="232" y="76" width="94" height="8" fill="#4c4a54"/>')
    p4.person(36, "land", op=.8).beast(154, 30, lean=34, op=.55).person(274, "wind", -1, skin="#6f8a5a", shirt="#3d4a36", legs="#2c3328", eye=hot, y=76, hair="#55703f")
    thread(p4, 166, 56, 270, 30, 3)
    p4.glow(270, 30, 20, hot, .85).text(16, 30, "HOP", "#f5d9ee", 14, "start", 700, 3)
    return [p1, p2, p3, p4]


def new_boards():
    """Storyboards for the new powers, by id in powers_data.NEWP."""
    return {
        "titan": dict(panels=titan(), caps=["The R key is full.", "You grow in three heaves, as big as the place allows.", "One swing clears the room.", "X picks up anything smaller than you and throws it."]),
        "clock": dict(panels=clock(), caps=["One tick, and the colour goes. Only you still move.", "Your shots hang behind you. Your hits pile up.", "You've got under a second left, and the boss is surrounded.", "The clock starts. All of it lands at once."]),
        "road": dict(panels=road_ahead(), caps=["You run off the edge and the road carries on.", "Look up and it climbs. The oldest tiles are already fading.", "It banks where you lean. Past twice sprint speed, you hear it.", "Off the end and onto them. The road drops away behind you."]),
        "back": dict(panels=say_back(), caps=["The boss starts the move that's been killing you.", "You get out of it cleanly, and the key takes a copy.", "You swing its own arm at it, made of glass, as big as the real one.", "A boss hit by its own signature takes double stance."]),
        "toll": dict(panels=toll(), caps=["You strike the air with your open hand.", "Every sound in the world drops away.", "Then the bell, and you can see it leave. The Deep's creatures reel.", "Somewhere out there, one you haven't found answers."]),
        "cut": dict(panels=long_cut(), caps=["Hold. A line draws itself to the horizon.", "Let go. Half a second of nothing.", "The land opens along it, travelling away from you.", "From up on the hill you can see all of what you did."]),
        "body": dict(panels=borrowed(), caps=["You reach for the big one.", "Your body kneels. You're looking out of its eyes now.", "Twelve seconds of being the monster.", "It falls over and you're already in the next one."]),
        "loop": dict(panels=last_round(), caps=["You fight for eight seconds. The game is keeping them.", "Press R. Your path lights up and something stands at the far end.", "It does what you did, while you do something new.", "Your old punch and your new shot arrive together."]),
    }


def boards():
    return [
        dict(id="sb-haymaker", title="Haymaker, done right", fam="Kinetic", color=ORANGE, kind="Power", panels=haymaker(), caps=[
            "Hold. The fist gathers and the air starts to shake.",
            "A small bright window at full charge. You hear it before you see it.",
            "Let go inside the window. Time hitches for one beat and the punch lands.",
            "The target folds, flies and cracks the wall it hits. Its stance is gone."]),
        dict(id="sb-ground", title="The ground comes up", fam="Kinetic", color=ORANGE, kind="Shared tech", panels=ground_wave(), caps=[
            "Stamp. Your foot goes down and the surface takes it.",
            "The real blocks under you rise and fall in a line, in whatever you're standing on.",
            "The crest reaches the far end and throws what's standing on it.",
            "The ground settles. Your target is left hanging in the air, Lifted."]),
        dict(id="sb-catch", title="Catch it and send it back", fam="Flight", color=SKY, kind="Power", panels=catch(), caps=[
            "The boss fires.",
            "You catch it at arm's length. For a moment nothing moves.",
            "Look back at the boss and let go. It leaves at twice the speed.",
            "It lands and takes a big piece of stance with it."]),
        dict(id="sb-star", title="Collapsing Star", fam="Flight", color=VIOLET, kind="Duet: Gravity Well + Nova", panels=star(), caps=[
            "Your Well is pulling everything in.",
            "Set off a Nova inside it. The Well crushes the burst to a point for half a second.",
            "Then it goes off, twice as wide as any Nova can.",
            "You found one. It goes in the Journal. Nobody told you it was there."]),
        dict(id="sb-foresight", title="See the next second", fam="Mind", color=PINK, kind="Power", panels=foresight(), caps=[
            "The boss starts a blow.",
            "A gold ghost plays the next second on the real path, and you hear the hit backwards.",
            "You step out. The real blow lands exactly where the ghost's did.",
            "Your counter lands Clean while its arm is still in the ground."]),
    ]


if __name__ == "__main__":
    out = []
    for b in boards():
        out.append("<h2>%s</h2><div style='display:grid;grid-template-columns:repeat(4,1fr);gap:6px'>" % b["title"])
        for pn, c in zip(b["panels"], b["caps"]):
            out.append("<div>%s</div>" % pn.svg(c))
        out.append("</div>")
    print("<!doctype html><meta charset=utf8><body style='background:#111;color:#eee;font:14px sans-serif'>" + "".join(out))
