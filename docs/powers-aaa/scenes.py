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

    p4 = P(64, "#10121f", "#242638", gy=146).roof(58).ground(gc, cap)
    p4.person(120, "stand", s=1.62).ring(120, 62, 40, 7, RED, 2.5, .95).lines(120, 62, 8, 8, 22, RED, 2, .8, 20, 160)
    p4.box(176, 76, 128, 40, "#0c0d1a", RED).text(240, 93, "NO ROOM", "#ffb3a8", 12, "middle", 700, 2).text(240, 108, "It shrinks you back", "#e9e4d8", 10, "middle")
    return [p1, p2, p3, p4]


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
        dict(id="sb-titan", title="Titan", fam="Kinetic", color=GOLD, kind="New power, optional", panels=titan(), caps=[
            "The R key is full.",
            "You grow to three times your size. The ground shakes with each step.",
            "One swing clears the room.",
            "Under a low ceiling it refuses, or shrinks you back if the roof closes in, so you can't get stuck."]),
    ]


if __name__ == "__main__":
    out = []
    for b in boards():
        out.append("<h2>%s</h2><div style='display:grid;grid-template-columns:repeat(4,1fr);gap:6px'>" % b["title"])
        for pn, c in zip(b["panels"], b["caps"]):
            out.append("<div>%s</div>" % pn.svg(c))
        out.append("</div>")
    print("<!doctype html><meta charset=utf8><body style='background:#111;color:#eee;font:14px sans-serif'>" + "".join(out))
