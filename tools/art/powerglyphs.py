"""The 63 power glyphs, one function per power, drawn on a powericons.G (32x32, centre 16,16).

Glyph rules (they keep the set consistent; read them before adding one):
- Design inside x, y 5..27. A frame may scale the glyph to fit its face, and spill onto the rim is allowed.
- One bold silhouette per power that says what the power DOES (a fist for Haymaker, not an orb). Within a family no two
  glyphs may share a silhouette.
- Main strokes 3.6 or wider (w >= 3.6; frames scale the glyph by 0.72-0.84): the lighting needs a lit edge, a body and a shaded edge to read as solid.
  2px is the floor for secondary strokes; 1px only for a cut (ERASE) that should show as a dark seam.
- MAIN is the object. HOT is energy (white-hot, glows): use it for a core, a beam or a spark, not for whole shapes.
  DIM is secondary (motion lines, debris, a ghost, a far object): keep it smaller than the main shape.
- Cut seams with ERASE (w 0.9-1.2) to separate parts of one object (fingers, bricks, plates).
- Leave air: about 2px between separate parts so their outlines don't merge.
- Draw back to front: DIM first, then MAIN, then HOT.
"""
import math

from powericons import MAIN, HOT, DIM, ERASE


def haymaker(g):
    # a raised fist swinging in from the left, impact flaring at its knuckles
    for y in (11, 16.5, 22):
        g.line(1.5, y, 5.5 - (y == 16.5) * 1.5, y, 2.2, DIM)
    g.line(25, 7, 29, 3.5, 2.2, HOT)
    g.line(26, 11.5, 30.5, 11.5, 2.2, HOT)
    g.line(21.5, 4.5, 22.5, 1, 2.2, HOT)
    for k, top in enumerate((10, 8.6, 8.6, 10)):
        g.line(9.5 + 4 * k, top, 9.5 + 4 * k, 15, 4.2)
    g.rect(7.5, 13, 23.5, 21)
    g.disc(10, 20, 2.6)
    g.rect(11, 21, 21, 26.5)
    for k in range(3):
        g.line(11.5 + 4 * k, 6, 11.5 + 4 * k, 15.6, 1.0, ERASE)
    g.line(7, 15.6, 19, 15.6, 1.0, ERASE)
    g.line(9, 18.2, 18.5, 18.2, 4.0)  # the thumb folded across
    g.line(10, 21.2, 22, 21.2, 1.0, ERASE)


def ground_slam(g):
    # a slab of ground split by the blow: a crater, cracks running out, rock and light thrown up
    g.poly([(7, 6), (11, 4.5), (12, 8.5), (8.4, 10)], DIM)
    g.poly([(22.5, 9.5), (25.5, 10.5), (24.6, 13.6), (21.6, 12.6)], DIM)
    g.poly([(1.5, 21.5), (16, 14.5), (30.5, 21.5), (16, 28.5)])
    g.ellipse(16, 21.5, 7, 3.3, ERASE)
    for x0, y0, x1, y1 in ((9, 22, 4, 19.5), (23, 21, 28, 23), (14, 24.5, 11, 27), (19, 18.5, 21, 16.2)):
        g.line(x0, y0, x1, y1, 1.0, ERASE)
    g.ellipse(16, 22.4, 3.2, 1.3, HOT)
    g.line(16, 20, 16, 7, 2.4, HOT)
    g.line(13.5, 19.5, 10.5, 13, 1.8, HOT)
    g.line(18.5, 19.5, 21, 14.5, 1.8, HOT)


def lightning_step(g):
    # a bolt that forks as it strikes forward, speed streaking behind it
    for y, x0 in ((9, 2), (15, 0.5), (21, 2)):
        g.line(x0, y, x0 + 5.5, y, 2.2, DIM)
    g.poly([(21, 2.5), (11, 16), (16.5, 16), (11.5, 29.5), (26.5, 12.5), (20.5, 12.5), (25.5, 2.5)])
    g.poly([(18.6, 20.5), (27, 24.5), (22.5, 25), (25.5, 30)])  # the fork
    g.bolt([(22.5, 5), (16.6, 14), (19.5, 14.2), (15, 24)], 1.4, HOT)


def meteor_dive(g):
    # a burning body diving down-right, a tapered fire trail behind it
    g.line(3.5, 11, 12, 18, 2.2, DIM)
    g.line(11, 3.5, 18, 12, 2.2, DIM)
    g.poly([(5, 5), (21.5, 15.5), (15.5, 21.5)])
    g.disc(20, 20, 6.4)
    g.disc(21, 21, 3.0, HOT)
    g.line(9, 9, 15, 15, 1.0, ERASE)


def prism_lance(g):
    # white light enters a prism and leaves it as a fan of piercing beams
    g.line(0.5, 20.5, 12, 16.8, 2.2, HOT)
    g.line(20, 15.5, 31.5, 9.5, 2.2, HOT)
    g.line(20.5, 17, 31.5, 17, 2.2)
    g.line(20, 18.5, 30, 24.5, 2.2, DIM)
    g.poly([(15, 4.5), (24.5, 24), (5.5, 24)])
    g.line(15, 8.5, 9, 21.5, 1.6, HOT)


def shadow_step(g):
    # a figure stepping out of a crescent of shadow, wisps trailing it
    g.arc(14, 16, 10.5, 105, 255, 4.0, DIM)
    g.line(6.5, 9.5, 9.5, 12.5, 1.8, DIM)
    g.line(6, 22.5, 9.5, 19.5, 1.8, DIM)
    g.figure(18, 5.5, "run", MAIN, 1.08)


def dominate(g):
    # a crown of will, its jewel burning, pressing down in waves on the minds below
    g.arc(16, 15, 10, 205, 335, 2.2, DIM)
    g.arc(16, 15, 14, 215, 325, 2.2, DIM)
    g.poly([(5, 17), (5, 5.5), (10.5, 10.5), (16, 3), (21.5, 10.5), (27, 5.5), (27, 17)])
    g.line(4, 17, 28, 17, 1.0, ERASE)
    g.rect(5, 18, 27, 20.5)
    g.disc(16, 12.5, 2.3, HOT)


def rampart(g):
    # a crenellated wall with its gate, laid stone by stone
    g.rect(5, 13, 26, 26)
    for x in (5, 13.5, 22):
        g.rect(x, 7.5, x + 4, 12)
    for y in (17, 21.5):
        g.line(4, y, 28, y, 1.0, ERASE)
    for x, y0 in ((10, 13), (19, 13), (14, 17.6), (23, 17.6), (9, 22), (21, 22)):
        g.line(x, y0, x, y0 + 3.4, 1.0, ERASE)
    g.disc(16, 22.8, 3.4, ERASE)
    g.rect(12.6, 22.8, 19.4, 27, ERASE)


def railgun(g):
    # coils charge a slug between two rails; it leaves white-hot
    g.rect(4, 11.5, 19.5, 20.5)
    for x in (8, 11.5, 15):
        g.line(x, 10, x, 22, 1.0, ERASE)
    g.poly([(7.5, 20.5), (12, 20.5), (10, 27), (6, 27)])
    g.line(19, 12.6, 28, 12.6, 2.4)
    g.line(19, 19.4, 28, 19.4, 2.4)
    g.line(18, 16, 31.5, 16, 2.4, HOT)


def storm_call(g):
    # a stormcloud called down, its bolt striking
    g.line(8.5, 20.5, 7, 24, 1.8, DIM)
    g.line(24.5, 20.5, 23, 24, 1.8, DIM)
    g.disc(11, 13.5, 4.8)
    g.disc(17.5, 10.5, 6.2)
    g.disc(23, 14.5, 4.4)
    g.rect(7, 14, 26, 18.5)
    g.poly([(17.5, 17), (12.5, 24.5), (16, 24.5), (13, 31), (21, 22), (17.4, 22), (20.4, 17)], HOT)


def leap(g):
    # a figure clearing a huge arc, the ring of its landing already flaring below
    g.arc(23, 25, 19, 138, 172, 2.4, DIM)
    g.ellipse(16.5, 25, 11, 3.9)
    g.ellipse(16.5, 25, 6.6, 1.1, ERASE)
    g.figure(16.5, 1.5, "leap", MAIN, 1.12)
    g.line(2.5, 24, 0.5, 21.5, 2.2, HOT)
    g.line(30.5, 24, 32.5, 21.5, 2.2, HOT)


def terrain_rip(g):
    # a block of earth wrenched up with its roots hanging, the pit it left gaping in the ground below
    g.line(3, 6, 6, 3.5, 2.2, DIM)
    g.line(27, 3, 30, 5.5, 2.2, DIM)
    g.rect(3, 23.5, 28, 28.5)
    g.ellipse(16, 25.7, 8.5, 1.6, ERASE)
    a = math.radians(-16)
    R = lambda pts: [(15 + (x - 15) * math.cos(a) - (y - 11) * math.sin(a), 11 + (x - 15) * math.sin(a) + (y - 11) * math.cos(a)) for x, y in pts]
    g.poly(R([(15, 1.5), (25.5, 6.5), (25.5, 15.5), (22, 17.5), (21, 22), (18, 18.5), (13.5, 19), (11, 24), (9, 17), (5, 15.5), (5, 6.5)]))
    g.poly(R([(5, 6.5), (15, 11.5), (25.5, 6.5), (25.5, 7.6), (15, 12.7), (5, 7.6)]), ERASE)
    g.poly(R([(14.5, 12), (15.5, 12), (15.5, 19), (14.5, 19)]), ERASE)
    g.ellipse(16, 25.7, 3.4, 0.9, HOT)


def shockwave(g):
    # a boot stamping down, a wave rolling out of the impact
    g.arc(10, 25.5, 11, 3, 66, 2.4, HOT)
    g.arc(10, 25.5, 16, 5, 58, 3.0)
    g.arc(10, 25.5, 21.5, 7, 50, 2.2, DIM)
    g.poly([(3.5, 3), (11.5, 3), (12, 13.5), (17.5, 18), (17.5, 23), (3, 23), (2.5, 16)])
    g.line(2.5, 19.8, 17.5, 19.8, 1.0, ERASE)
    g.line(4, 8, 11.8, 8, 1.0, ERASE)
    g.rect(1.5, 24.2, 20, 27.2)


def brace(g):
    # a planted stance, feet sunk in spiked ground, blows glancing off the shoulders
    g.line(30, 14.5, 24, 12, 2.2, DIM)
    g.line(24, 12, 27.5, 5.5, 2.2, DIM)
    g.line(2, 14.5, 8, 12, 2.2, DIM)
    g.line(8, 12, 4.5, 5.5, 2.2, DIM)
    g.figure(16, 2.5, "brace", MAIN, 1.05)
    g.poly([(3, 28.5), (4, 23.5), (28, 23.5), (29, 28.5)])
    g.poly([(4, 23.5), (6.5, 18), (9.5, 23.5)])
    g.poly([(22.5, 23.5), (25.5, 18), (28, 23.5)])
    g.line(4, 23.5, 28, 23.5, 1.0, ERASE)
    g.disc(8, 12, 1.3, HOT)
    g.disc(24, 12, 1.3, HOT)


def thunderclap(g):
    # two palms meeting, the sky answering with a bolt between them
    g.line(2, 10, 5, 12, 2.2, DIM)
    g.line(30, 10, 27, 12, 2.2, DIM)
    g.poly([(3, 14), (6, 12.5), (12.5, 12.5), (12.5, 24), (6, 24), (3, 20)])
    g.poly([(29, 14), (26, 12.5), (19.5, 12.5), (19.5, 24), (26, 24), (29, 20)])
    for x in (6.6, 9.4):
        g.line(x, 13, x, 19, 1.0, ERASE)
        g.line(32 - x, 13, 32 - x, 19, 1.0, ERASE)
    g.line(3, 19, 12.5, 19, 1.0, ERASE)
    g.line(29, 19, 19.5, 19, 1.0, ERASE)
    g.bolt([(18.5, 0.5), (14.2, 9), (18, 13.5), (14, 19), (17, 24), (15.5, 28)], 2.8, HOT)


def _wall_runner(g, hx, hy, s, tilt=0, v=MAIN):
    # the stock run pose turned a quarter: head to the right, running up, feet reaching left toward a wall
    ta = math.radians(tilt)
    ct, st = math.cos(ta), math.sin(ta)

    def P(px, py):
        dx, dy = -(py - 11) * s, -px * s
        return hx + dx * ct + dy * st, hy - dx * st + dy * ct
    w = 3.4 * s
    g.disc(*P(0, 2.2), 2.9 * s, v)
    g.line(*P(1.6, 5.6), *P(0, 11), w + 0.4, v)
    for a, b in (((1.4, 6.4), (5, 9.6)), ((1.4, 6.4), (-3, 9)), ((0, 11), (4.6, 14.6)), ((4.6, 14.6), (3.2, 18.4)),
                 ((0, 11), (-3.6, 17.4))):
        g.line(*P(*a), *P(*b), w * 0.9, v)


def dash(g):
    # a figure thrown flat out forward, long streaks tearing off behind it
    for y, x1 in ((7, 9), (13.5, 5.5), (25.5, 11)):
        g.line(1, y, x1, y, 2.2, DIM)
    g.disc(23.5, 8, 2.9)
    g.line(21.5, 11, 14.5, 16.5, 4.0)
    g.line(20.5, 11.5, 27, 14.5, 3.2)
    g.line(19.5, 11.5, 13.5, 8.5, 3.0)
    g.line(14.5, 16.5, 8.5, 19.5, 3.4)
    g.line(8.5, 19.5, 3.8, 17.5, 3.2)
    g.line(14.5, 16.5, 20, 21, 3.4)
    g.line(20, 21, 19, 27, 3.2)
    g.line(14, 22.5, 9, 24.5, 2.2, HOT)


def flow(g):
    # an hourglass whose sand hangs frozen in mid-fall
    g.rect(6, 2.5, 26, 6)
    g.rect(6, 26, 26, 29.5)
    g.poly([(8, 5.5), (24, 5.5), (24, 8), (18, 15.5), (18, 16.5), (24, 24), (24, 26.5), (8, 26.5), (8, 24), (14, 16.5), (14, 15.5), (8, 8)])
    g.poly([(11.2, 7.5), (20.8, 7.5), (16, 14)], ERASE)
    g.poly([(11, 25), (21, 25), (16, 19.4)], ERASE)
    g.poly([(12.4, 8.2), (19.6, 8.2), (16, 12.6)], HOT)
    g.disc(16, 16, 1.5, HOT)
    g.poly([(12.2, 24.6), (19.8, 24.6), (16, 20.6)], HOT)
    g.line(27, 12, 30, 12, 2.2, DIM)
    g.line(2, 20, 5, 20, 2.2, DIM)


def afterimage(g):
    # a runner and the fading copy they leave behind, a beat apart
    g.line(1.5, 13, 4.5, 13, 2.2, DIM)
    g.line(1.5, 19, 4, 19, 2.2, DIM)
    g.figure(9, 6.5, "run", DIM, 0.92)
    g.figure(20.5, 5, "run", MAIN, 1.08)


def wall_run(g):
    # a runner racing up a brick wall, feet planted against its face, dust trailing below
    g.rect(6, 2, 11, 29)
    for y in (9.5, 19):
        g.line(5.5, y, 11.5, y, 1.0, ERASE)
    for y0, x in ((2.5, 9), (9.8, 7.8), (19.3, 9.2)):
        g.line(x, y0, x, y0 + 6.7, 1.0, ERASE)
    g.line(16, 27.5, 16, 22, 2.2, DIM)
    g.line(21.5, 27, 21.5, 23.5, 2.2, DIM)
    _wall_runner(g, 17.5, 14.5, 1.1, 40)


def water_run(g):
    # a runner skimming the surface, spray kicking up off every footfall
    for k in range(3):
        g.arc(5 + 9 * k, 23, 4.5, 205, 335, 2.4, DIM)
    for k in range(3):
        g.arc(9.5 + 9 * k, 28, 4.5, 205, 335, 3.0)
    g.figure(15, 1.5, "run", MAIN, 1.12)
    g.line(8.5, 21.5, 7, 19, 2.0, HOT)
    g.line(25, 21.5, 26.5, 18.5, 2.0, HOT)


def hover(g):
    # a figure floating with arms flared and feet dangling, a downdraft ring and thrust lines beneath it
    for x in (11, 16, 21):
        g.line(x, 23.8, x, 26.2, 2.0, DIM)
    g.disc(16, 4, 3.3)
    g.poly([(11, 8.4), (21, 8.4), (19.2, 16), (12.8, 16)])
    g.line(16, 7.1, 16, 7.1, 1.0, ERASE)
    g.line(12.4, 6.9, 19.6, 6.9, 1.0, ERASE)
    g.line(11.6, 10, 6.4, 15.8, 3.4)
    g.line(20.4, 10, 25.6, 15.8, 3.4)
    g.line(14.2, 15.5, 12.4, 22, 3.6)
    g.line(17.8, 15.5, 19.6, 22, 3.6)
    g.ellipse(16, 28, 10, 2.5, HOT)
    g.ellipse(16, 28, 5.6, 0.8, ERASE)


def flight(g):
    # one great feathered wing: four swept blades fanning out of the shoulder
    for p0, c, p1, w in (((8.5, 27), (14, 24.5), (22, 28.5), 4.4), ((8, 25.5), (14.5, 18), (27.5, 22), 5.0),
                         ((7.5, 24), (11, 13), (28.5, 13), 5.2), ((7, 22.5), (6.5, 9), (26.5, 4.5), 5.4)):
        _blade(g, p0, c, p1, w, MAIN, 1.0)
    g.disc(7.5, 24.5, 3.4)
    g.line(1.5, 14, 4.5, 13, 2.2, DIM)


def gravity_well(g):
    # a spiral well hauling rubble in toward a white-hot core
    for x, y, d in ((6.5, 7, (3.5, 2)), (25.5, 24.5, (-3.5, -2)), (26.5, 7.5, (-2, 3.5))):
        g.poly([(x - 1.8, y - 1.8), (x + 1.8, y - 1.4), (x + 1.4, y + 1.8), (x - 1.6, y + 1.5)], DIM)
        g.line(x, y, x + d[0], y + d[1], 1.6, DIM)
    pts = []
    for k in range(0, 22):
        th = math.radians(k * 18)
        r = 10.5 - k * 0.36
        pts.append((16 + r * math.cos(th), 16.5 - r * math.sin(th)))
    for k, ((a, b), (c, d)) in enumerate(zip(pts, pts[1:])):
        g.line(a, b, c, d, 4.2 - k * 0.07)
    g.disc(16, 16.5, 2.5, HOT)


def telekinesis(g):
    # an open hand with its fingers spread, a block hanging weightless above it
    g.rect(11.5, 1.5, 20.5, 8)
    g.line(11, 4.8, 21, 4.8, 1.0, ERASE)
    g.line(16, 1.2, 16, 4.8, 1.0, ERASE)
    g.line(6.5, 3.5, 6.5, 7.5, 2.2, DIM)
    g.line(25.5, 3.5, 25.5, 7.5, 2.2, DIM)
    g.poly([(9.5, 21.5), (22.5, 21.5), (21.5, 29.5), (10.5, 29.5)])
    for x0, y0, x1, y1, w in ((10.8, 22.5, 7.8, 16.5, 3.4), (14.3, 21.5, 13, 14.5, 3.5), (17.8, 21.5, 19, 14.5, 3.5),
                              (21.3, 22.5, 24.2, 16.5, 3.4), (10, 26.5, 3.8, 22, 3.6)):
        g.line(x0, y0, x1, y1, w)
    for x0, y0, x1, y1 in ((12.6, 15.5, 12.4, 21.5), (16, 15.5, 16, 21.5), (19.4, 15.5, 19.6, 21.5)):
        g.line(x0, y0, x1, y1, 0.9, ERASE)
    g.line(14.3, 11.2, 14.6, 11.2, 1.4, HOT)
    g.line(17.4, 11.2, 17.7, 11.2, 1.4, HOT)


def gravity_flip(g):
    # a block turned over mid-air between a rising arrow and a crashing one
    g.line(6.5, 27, 6.5, 12, 3.6)
    g.arrow(6.5, 3.5, 90, 7)
    g.line(25.5, 5, 25.5, 20, 3.6)
    g.arrow(25.5, 28.5, 270, 7)
    g.rect(11, 8, 21, 22)
    g.rect(11, 18.5, 21, 22, ERASE)
    g.line(11, 18.5, 21, 18.5, 1.0, ERASE)
    g.rect(11.8, 19.2, 20.2, 22)
    g.line(11, 13.5, 21, 13.5, 1.0, ERASE)
    g.disc(16, 11, 1.5, HOT)


def _blade(g, p0, c, p1, w0, v=MAIN, halo=0.0, n=9):
    # a tapering curved blade (a feather, a flame tongue): quadratic curve p0 -> p1 bending toward c, w0 wide at p0
    def at(t):
        u = 1 - t
        x = u * u * p0[0] + 2 * u * t * c[0] + t * t * p1[0]
        y = u * u * p0[1] + 2 * u * t * c[1] + t * t * p1[1]
        dx = 2 * u * (c[0] - p0[0]) + 2 * t * (p1[0] - c[0])
        dy = 2 * u * (c[1] - p0[1]) + 2 * t * (p1[1] - c[1])
        ln = math.hypot(dx, dy) or 1
        return x, y, -dy / ln, dx / ln
    for pad, val in ((halo, ERASE), (0.0, v)):
        if val == ERASE and not pad:
            continue
        left, right = [], []
        for i in range(n + 1):
            t = i / n
            x, y, nx, ny = at(t)
            h = (w0 / 2 * (1 - t * 0.88) + pad)
            left.append((x + nx * h, y + ny * h))
            right.append((x - nx * h, y - ny * h))
        g.poly(left + right[::-1], val)


def beam(g):
    # a gauntlet cannon pouring a searing beam, flaring at the muzzle
    g.poly([(3.5, 12), (12, 12), (14.5, 10), (17, 10), (17, 22), (14.5, 22), (12, 20), (3.5, 20)])
    g.line(9, 11.5, 9, 20.5, 1.0, ERASE)
    g.line(13.4, 10.5, 13.4, 21.5, 1.0, ERASE)
    g.poly([(5.5, 20), (11, 20), (10, 26), (6.5, 26)])
    g.rect(18.5, 14, 31.5, 18.2, HOT)
    g.line(19, 10.5, 19, 21.5, 2.2, HOT)
    g.line(23.5, 9, 26, 11.5, 2.2, DIM)
    g.line(23.5, 23, 26, 20.5, 2.2, DIM)


def nova(g):
    # a ring of burning light flung outward in a star of spikes, a white core inside
    pts = []
    for k in range(16):
        r = 15.5 if k % 4 == 0 else 14 if k % 2 == 0 else 8.6
        a = math.radians(k * 22.5 - 90)
        pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
    g.poly(pts)
    g.disc(16, 16, 6.0, ERASE)
    g.disc(16, 16, 3.8, HOT)


def barrier(g):
    # a bubble of hard light tiled in hexagon panels, the middle one flaring
    g.disc(16, 16, 13.2)
    for k in range(7):
        cx, cy = (16, 16) if k == 0 else (16 + 7.9 * math.cos(math.radians(60 * k - 30)), 16 + 7.9 * math.sin(math.radians(60 * k - 30)))
        hexa = [(cx + 3.2 * math.cos(math.radians(60 * j + 30)), cy + 3.2 * math.sin(math.radians(60 * j + 30))) for j in range(6)]
        g.poly(hexa, HOT if k == 0 else ERASE)


def charged_shot(g):
    # a crackling ball of charge flying free, a tapering trail of light behind it
    g.poly([(1.5, 16), (12.5, 10), (12.5, 22)], DIM)
    g.disc(19, 16, 7.4)
    g.disc(19, 16, 3.9, HOT)
    g.bolt([(22.5, 9.4), (26, 7), (24.5, 4)], 1.8, HOT)
    g.bolt([(26, 14.5), (28.8, 16.3), (27, 19.5)], 1.8, HOT)
    g.bolt([(21.5, 22.8), (23.7, 25.6), (22, 28)], 1.8, HOT)


def overload(g):
    # a core split into jagged shards flung outward, white-hot light pouring from the cracks between them
    edges = ((-95, (1.0, -1.0)), (25, (-1.0, 1.0)), (145, (0.9, -0.9)))
    def edge(theta, zig):
        t = math.radians(theta)
        return [(16 + r * math.cos(t) - z * math.sin(t), 16 + r * math.sin(t) + z * math.cos(t)) for r, z in ((3.4, zig[0]), (6.6, zig[1]), (9.8, 0))]
    g.disc(16, 16, 7.4, HOT)
    for i in range(3):
        a0, z0 = edges[i]
        a1, z1 = edges[(i + 1) % 3]
        a1 = a1 + 360 if a1 <= a0 else a1
        arc = [(16 + 9.8 * math.cos(math.radians(a)), 16 + 9.8 * math.sin(math.radians(a))) for a in range(a0 + 12, a1, 12)]
        pts = [(16, 16)] + edge(a0, z0) + arc + edge(a1, z1)[::-1]
        mid = math.radians((a0 + a1) / 2)
        sx, sy = 1.7 * math.cos(mid), 1.7 * math.sin(mid)
        g.poly([(x + sx, y + sy) for x, y in pts])
    for a, _ in edges:
        t = math.radians(a)
        g.line(16 + 11.6 * math.cos(t), 16 + 11.6 * math.sin(t), 16 + 15.4 * math.cos(t), 16 + 15.4 * math.sin(t), 2.0, HOT)
    g.disc(16, 16, 2.4, HOT)


def cloak(g):
    # an empty hooded cloak, nothing inside it but two dim eyes, its hem frayed into shadow
    g.poly([(15, 2), (20, 4), (24, 9.5), (26.5, 18), (28.5, 27.5), (23, 26), (19, 29), (14.5, 26.3), (9.5, 28.5), (4, 27)
            , (5.5, 18), (8, 9.5), (11, 4.6)])
    g.ellipse(15.8, 13.2, 5.4, 6.0, ERASE)
    g.line(11.6, 12.2, 14.4, 13.4, 2.2, DIM)
    g.line(20, 12.2, 17.2, 13.4, 2.2, DIM)
    g.line(10, 21, 8, 27, 1.0, ERASE)
    g.line(22.4, 21, 23.8, 26, 1.0, ERASE)
    g.line(15.8, 21.5, 14.6, 26, 1.0, ERASE)


def echo_swap(g):
    # two figures trading places, the stand-in a faded echo, a pair of curved arrows crossing the gap between them
    g.figure(25.4, 7, "stand", DIM, 0.96)
    g.figure(6.6, 7, "stand", MAIN, 0.96)
    g.arc(16, 20.4, 8.4, 55, 125, 2.4, HOT)
    g.arrow(21.8, 13.9, -35, 3.8, HOT)
    g.arc(16, 12.6, 8.4, 235, 305, 2.4, HOT)
    g.arrow(10.2, 19.1, 145, 3.8, HOT)


def legion(g):
    # three shadow soldiers rising out of a pool of darkness, spear raised, eyes burning
    g.line(22.3, 3.4, 22.3, 25, 2.2, DIM)
    g.poly([(22.3, 0.6), (24.3, 4.4), (20.3, 4.4)], DIM)
    g.figure(6.3, 8.6, "stand", MAIN, 0.84)
    g.figure(25.7, 8.6, "stand", MAIN, 0.84)
    g.figure(16, 3.2, "stand", MAIN, 1.04)
    g.ellipse(16, 25, 14.4, 3.4, ERASE)
    g.ellipse(16, 26, 12.4, 2.0, DIM)
    g.line(14.8, 5.6, 17.2, 5.6, 1.2, HOT)
    g.line(5.3, 11, 7.3, 11, 1.2, HOT)
    g.line(24.7, 11, 26.7, 11, 1.2, HOT)


def danger_sense(g):
    # a head snapping up, alarm lines flaring off it before the blow even lands
    for ang in (62, 90, 118):
        a = math.radians(ang)
        g.line(16 + 10.4 * math.cos(a), 15.5 - 10.4 * math.sin(a), 16 + 14.6 * math.cos(a), 15.5 - 14.6 * math.sin(a), 2.4, HOT)
    for sx in (-1, 1):
        g.line(16 + sx * 10.6, 14, 16 + sx * 14.4, 11.6, 2.4, HOT)
    g.disc(16, 14.5, 6.6)
    g.poly([(14, 19), (18, 19), (18.4, 22.5), (13.6, 22.5)])
    g.poly([(5.5, 28), (7.5, 23.4), (13, 21.6), (19, 21.6), (24.5, 23.4), (26.5, 28)])
    g.line(11, 13.6, 14.4, 14.4, 1.6, ERASE)
    g.line(21, 13.6, 17.6, 14.4, 1.6, ERASE)


def read_intent(g):
    # a hunter's figure caught in a crosshair, tick marks closing on it from every side
    g.ring(16, 16.2, 11.4, 2.4, DIM)
    for sx, sy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        g.line(16 + sx * 10, 16.2 + sy * 10, 16 + sx * 14.8, 16.2 + sy * 14.8, 2.4, HOT)
    g.figure(16, 7.6, "stand", MAIN, 0.92)


def decoy(g):
    # a figure and the faded image of itself it threw, a mark already burning on the copy
    g.figure(7, 7.4, "stand", MAIN, 0.96)
    g.figure(25, 7.4, "stand", DIM, 0.96)
    for y in (21.2, 24.6):
        g.line(19, y, 32, y, 1.0, ERASE)
    g.ring(25, 13.4, 3.6, 1.6, HOT)
    g.disc(25, 13.4, 1.1, HOT)
    for x in (13, 16):
        g.line(x, 14.5, x + 1.2, 14.5, 2.0, DIM)


def foresight(g):
    # an eye whose pupil is a clock face, its hands already past the present
    top = [(2 + 28 * i / 14, 16 - 10.5 * (1 - ((i - 7) / 7) ** 2) ** 0.85) for i in range(15)]
    bot = [(2 + 28 * i / 14, 16 + 10.5 * (1 - ((i - 7) / 7) ** 2) ** 0.85) for i in range(13, 0, -1)]
    g.poly(top + bot)
    g.poly([(16 + (x - 16) * 0.88, 16 + (y - 16) * 0.76) for x, y in top + bot], ERASE)
    g.disc(16, 16, 7.4)
    g.disc(16, 16, 5.6, HOT)
    g.line(16, 16, 16, 11.8, 1.5, ERASE)
    g.line(16, 16, 19.6, 17.8, 1.5, ERASE)


def wall(g):
    # a standing panel of hard light, five tiles wide and three tall, a sweep of glow running through it
    g.line(3, 25.5, 29, 25.5, 2.2, DIM)
    g.rect(4, 8, 27, 23)
    for x in (8.6, 13.2, 17.8, 22.4):
        g.line(x, 7, x, 24, 1.0, ERASE)
    for y in (13, 18):
        g.line(3, y, 28, y, 1.0, ERASE)
    for x0, y0 in ((4, 18), (8.6, 13), (13.2, 8)):
        g.rect(x0 + 1.2, y0 + 1.2, x0 + 3.2, y0 + 3.2, HOT)


def ramp(g):
    # a hard-light incline rising ahead of a runner already racing up it
    g.line(2.5, 4, 6, 6, 2.2, DIM)
    g.poly([(2.5, 27), (29.5, 12), (29.5, 27)])
    for k in range(1, 4):
        x = 2.5 + 27 * k / 4.4
        g.line(x, 27.5, x, 27 - 15 * (x - 2.5) / 27 - 1.5, 1.0, ERASE)
    g.figure(13.6, 3.2, "run", MAIN, 0.84)


def floor(g):
    # a square hard-light platform conjured under a figure mid-fall, its tiles lighting as it lands
    for x in (5.5, 26.5):
        g.line(x, 3, x, 9, 2.2, DIM)
    g.disc(16, 5.2, 2.9)
    g.line(16, 8.4, 16, 14.2, 4.0)
    g.line(15.6, 9.4, 10.6, 4.4, 3.2)
    g.line(16.4, 9.4, 21.4, 4.4, 3.2)
    g.line(14.4, 13.6, 13, 19, 3.4)
    g.line(17.6, 13.6, 19, 19, 3.4)
    g.poly([(16, 20.6), (28, 25), (16, 29.4), (4, 25)])
    g.poly([(4, 26.6), (16, 31), (28, 26.6), (28, 29), (16, 33.4), (4, 29)])
    for t in (0.34, 0.67):
        g.line(4 + 12 * t, 25 - 4.4 * t, 16 + 12 * t, 29.4 - 4.4 * t, 1.0, ERASE)
        g.line(4 + 12 * t, 25 + 4.4 * t, 16 + 12 * t, 20.6 + 4.4 * t, 1.0, ERASE)
    g.poly([(16, 23.2), (18.4, 25), (16, 26.8), (13.6, 25)], HOT)


def cone(g):
    # a stepped roof of hard light over your head, arrows ricocheting off its flanks
    g.rect(4, 22, 28, 27)
    g.rect(8, 17, 24, 21)
    g.rect(12, 12, 20, 16)
    g.rect(14.6, 7, 17.4, 11)
    for y in (21.5, 16.5, 11.5):
        g.line(3, y, 29, y, 1.0, ERASE)
    g.bolt([(2, 3), (7.2, 11.4), (2.2, 15)], 2.2, DIM)
    g.bolt([(30, 3), (24.8, 11.4), (29.8, 15)], 2.2, DIM)
    g.disc(7.4, 11.6, 1.5, HOT)
    g.disc(24.6, 11.6, 1.5, HOT)


def spire(g):
    # a pillar of stone erupting under your feet, hurling you skyward amid thrown rubble
    g.line(6.5, 4, 6.5, 10, 2.2, DIM)
    g.line(25.5, 4, 25.5, 10, 2.2, DIM)
    g.poly([(3, 28), (6, 23.6), (8.6, 27), (7, 29)], DIM)
    g.poly([(23.5, 29), (24.8, 24), (28.6, 24.6), (29.4, 28.4)], DIM)
    g.figure(16, 0.8, "leap", MAIN, 0.7)
    g.poly([(11.2, 29.5), (11.2, 19.5), (13.4, 17), (15.4, 19.6), (18.6, 16.4), (20.8, 19.5), (20.8, 29.5)])
    for y in (23, 26.6):
        g.line(10.2, y, 21.8, y, 1.0, ERASE)
    g.line(16, 19.5, 16, 23, 1.0, ERASE)
    g.ellipse(16, 15.2, 6.4, 1.2, HOT)


def collapse(g):
    # hard-light blocks wrenched loose and hurled at a target, trailing streaks
    for y in (9.5, 20, 25.5):
        g.line(1, y, 6, y, 2.2, DIM)
    g.rect(4.5, 5, 12.5, 13)
    g.line(4, 9, 13, 9, 1.0, ERASE)
    g.rect(8.5, 16, 15.5, 23)
    g.line(8, 19.5, 16, 19.5, 1.0, ERASE)
    g.ring(24, 15.5, 5.6, 2.4, HOT)
    g.disc(24, 15.5, 1.6, HOT)
    g.line(24, 7.4, 24, 9.4, 2.0, HOT)
    g.line(24, 21.6, 24, 23.6, 2.0, HOT)


def citadel(g):
    # a stone dome raised over a figure sheltering inside, a plus of healing glowing beside it
    g.arc(16, 24, 13, 0, 180, 4.2)
    g.rect(2.5, 24, 29.5, 28)
    for a in (45, 90, 135):
        r = math.radians(a)
        g.line(16 + 10.8 * math.cos(r), 24 - 10.8 * math.sin(r), 16 + 15.4 * math.cos(r), 24 - 15.4 * math.sin(r), 1.0, ERASE)
    g.figure(11.5, 12, "stand", MAIN, 0.66)
    g.line(21.5, 13.6, 21.5, 20, 2.4, HOT)
    g.line(18.3, 16.8, 24.7, 16.8, 2.4, HOT)


def grapple(g):
    # a grapnel flung forward, its curved flukes biting, rope trailing back
    g.bolt([(10.5, 21), (7.5, 21.6), (6, 25.2), (2.5, 26.4)], 2.2, DIM)
    g.line(10.5, 21, 17.4, 14, 4.4)
    g.arc(19.6, 11.4, 7.6, 118, 332, 4.4)
    g.line(19.6, 11.4, 25.8, 5, 3.6)
    g.arrow(27.8, 3, 45, 4.6)
    g.disc(19.6, 11.4, 2.2, HOT)


def jet_burst(g):
    # a rocket boot flung forward, its heel nozzle roaring out a spreading plume of flame
    _blade(g, (8, 15.5), (3, 12.5), (-0.5, 11.5), 4.2, DIM)
    _blade(g, (8, 20.5), (3, 23.5), (-0.5, 25), 4.2, DIM)
    g.poly([(14.5, 4.5), (22.5, 4.5), (22.8, 13.2), (29, 17.2), (29, 22.6), (11.5, 22.6), (11.5, 13)])
    g.poly([(7.6, 14.2), (11.8, 14.2), (11.8, 22), (7.6, 22)])
    g.line(11.8, 13.4, 11.8, 22.8, 1.0, ERASE)
    g.line(11, 20, 29.4, 20, 1.0, ERASE)
    g.line(14.5, 9, 22.5, 9, 1.0, ERASE)
    _blade(g, (7.6, 18.2), (3.5, 17), (-1.2, 18.2), 6.2, HOT)


def drone(g):
    # one combat drone seen from above, four rotors blurring round a glowing eye
    for sx in (-1, 1):
        for sy in (-1, 1):
            cx, cy = 16 + sx * 9.2, 16 + sy * 9
            g.line(16, 16, cx, cy, 3.2)
            g.ring(cx, cy, 4.4, 2.6)
    g.disc(16, 16, 5.4)
    g.disc(16, 16, 2.2, HOT)


def turret(g):
    # a gun turret on a folding tripod, barrel levelled, muzzle flashing
    g.line(13.5, 17, 6.5, 28, 3.2)
    g.line(13.5, 17, 20.5, 28, 3.2)
    g.line(13.5, 17, 13.5, 28.5, 3.2)
    g.rect(6.5, 5.5, 19.5, 15.5)
    g.line(19, 8.2, 28, 8.2, 3.6)
    g.line(19, 13.4, 24, 13.4, 3.0)
    g.line(23.4, 6.6, 23.4, 9.8, 1.0, ERASE)
    g.line(6, 14.4, 20, 14.4, 1.0, ERASE)
    g.line(10, 10, 14.4, 10, 2.0, HOT)
    g.line(29.2, 8.2, 31.5, 8.2, 2.2, HOT)
    g.line(30, 4.6, 31.4, 6.2, 1.8, HOT)
    g.line(30, 11.8, 31.4, 10.2, 1.8, HOT)


def nanite_skin(g):
    # a torso sealed in a film of hexagonal plates, a few already burning bright as the nanites harden
    def plate(cx, cy, r, v=MAIN):
        g.poly([(cx + r * math.cos(math.radians(60 * j + 30)), cy + r * math.sin(math.radians(60 * j + 30))) for j in range(6)], v)
    g.disc(16, 6.6, 4.4)
    g.rect(13.6, 9, 18.4, 12)
    cells = [(9.6, 14.2), (16, 14.2), (22.4, 14.2), (6.4, 19.8), (12.8, 19.8), (19.2, 19.8), (25.6, 19.8),
             (9.6, 25.4), (16, 25.4), (22.4, 25.4)]
    for cx, cy in cells:
        plate(cx, cy, 3.9, ERASE)
        plate(cx, cy, 3.4)
    plate(16, 19.8, 0.1, ERASE)
    plate(16, 14.2, 2.2, HOT)
    plate(19.2, 19.8, 2.2, HOT)


def drone_swarm(g):
    # three small drones converging on a target reticle, all firing at once
    g.ring(16, 16.6, 3.8, 1.8, HOT)
    for x, y in ((16, 5.6), (6.4, 22.4), (25.6, 22.4)):
        g.line(x - 3.4, y - 3.4, x + 3.4, y + 3.4, 2.6)
        g.line(x - 3.4, y + 3.4, x + 3.4, y - 3.4, 2.6)
        g.disc(x, y, 2.2)
        g.disc(x, y, 0.9, HOT)


def _palm_side(g, dx, dy, v):
    # an open hand seen edge-on, thumb up, fingers stacked and pointing right
    g.poly([(7 + dx, 12 + dy), (17 + dx, 12 + dy), (17 + dx, 24 + dy), (7 + dx, 24 + dy)], v)
    g.line(10 + dx, 12 + dy, 15 + dx, 6 + dy, 3.8, v)
    for k in range(4):
        g.line(17 + dx, 13.6 + dy + 3.4 * k, 26 + dx - (k in (0, 3)) * 1.6, 13.6 + dy + 3.4 * k, 3.4, v)


def aura(g):
    # a figure wrapped in its own spirit, a bright outline burning off it and motes drifting up
    g.ellipse(16, 16, 12.2, 14.6, DIM)
    g.ellipse(16, 16, 9.8, 12.4, ERASE)
    g.figure(16, 4.6, "stand", MAIN, 1.14)
    for x, y in ((4.4, 9), (27.6, 12), (5, 23), (27.4, 24)):
        g.disc(x, y, 1.3, HOT)


def spirit_palm(g):
    # an open palm striking forward, the echo of the same blow already landing behind it
    _palm_side(g, -5.5, -5, DIM)
    g.poly([(5.5, 11), (18.5, 11), (18.5, 25), (5.5, 25)], ERASE)
    _palm_side(g, 0, 0, MAIN)
    for k in range(3):
        g.line(17, 15.2 + 3.4 * k, 25, 15.2 + 3.4 * k, 1.0, ERASE)
    g.line(28.5, 10, 31, 7.5, 2.2, HOT)
    g.line(29.5, 17, 32, 17, 2.2, HOT)
    g.line(28.5, 24, 31, 26.5, 2.2, HOT)


def mend(g):
    # a bold cross of healing light, sparks lifting off it as the poison washes away
    g.line(16, 6, 16, 26, 7.0)
    g.line(6, 16, 26, 16, 7.0)
    g.line(16, 8, 16, 24, 1.0, ERASE)
    g.line(8, 16, 24, 16, 1.0, ERASE)
    g.disc(16, 16, 2.4, HOT)
    for x, y in ((5.5, 6.5), (26.5, 8), (4.5, 24.5), (27.5, 25.5)):
        g.line(x - 1.8, y, x + 1.8, y, 1.6, HOT)
        g.line(x, y - 1.8, x, y + 1.8, 1.6, HOT)


def third_eye(g):
    # an eye turned upright, seeing through stone, rays of sight pouring off it
    top = [(16 + 8.6 * (1 - ((i - 7) / 7) ** 2) ** 0.85, 2.5 + 27 * i / 14) for i in range(15)]
    bot = [(16 - 8.6 * (1 - ((i - 7) / 7) ** 2) ** 0.85, 2.5 + 27 * i / 14) for i in range(13, 0, -1)]
    g.poly(top + bot)
    g.poly([(16 + (x - 16) * 0.66, 16 + (y - 16) * 0.86) for x, y in top + bot], ERASE)
    g.disc(16, 16, 5.0)
    g.disc(16, 16, 2.4, HOT)
    for sx in (-1, 1):
        g.line(16 + sx * 12.4, 8.5, 16 + sx * 14.8, 5.5, 2.2, HOT)
        g.line(16 + sx * 13, 16, 16 + sx * 16, 16, 2.2, HOT)
        g.line(16 + sx * 12.4, 23.5, 16 + sx * 14.8, 26.5, 2.2, HOT)


def cord_lash(g):
    # a lasso of spirit cord flung out of a grip, its noose closing round a target
    pts = []
    for k in range(13):
        t = k / 12
        u = 1 - t
        x = u ** 3 * 6.5 + 3 * u * u * t * 3.5 + 3 * u * t * t * 13 + t ** 3 * 15.5
        y = u ** 3 * 24.5 + 3 * u * u * t * 15 + 3 * u * t * t * 24 + t ** 3 * 15.4
        pts.append((x, y))
    for (a, b), (c, d) in zip(pts, pts[1:]):
        g.line(a, b, c, d, 3.2)
    g.ring(21, 10.4, 6.6, 3.2)
    g.disc(21, 10.4, 1.7, HOT)
    g.line(3, 29.2, 7.2, 24, 5.0)
    g.line(4.4, 27.2, 6.6, 29.4, 1.0, ERASE)
    g.line(27.5, 16.5, 30.5, 18.5, 2.2, DIM)
    g.line(14, 3.5, 12.5, 0.5, 2.2, DIM)


def bulwark(g):
    # a great dome of will, arrows from above dissolving into sparks against it
    g.poly([(16 - 13 * math.cos(math.radians(a)), 25.5 - 13 * math.sin(math.radians(a))) for a in range(0, 181, 12)])
    g.rect(2.5, 25.5, 29.5, 28.5)
    for r in (8.8, 4.8):
        g.arc(16, 25.5, r, 0, 180, 1.0, ERASE)
    g.line(2, 1.5, 7.2, 8.8, 2.6, DIM)
    g.arrow(9.4, 12, -54, 4.6, DIM)
    g.line(30, 1.5, 24.8, 8.8, 2.6, DIM)
    g.arrow(22.6, 12, 234, 4.6, DIM)
    g.line(15.5, 0.5, 15.5, 6, 2.6, DIM)
    g.arrow(15.5, 11, 270, 4.6, DIM)
    for x, y in ((8.2, 14.8), (23.8, 14.8), (16, 12.8)):
        g.disc(x, y, 1.5, HOT)


def hundred_palms(g):
    # palms too fast to see: one open hand, its own afterimages stacked behind it
    _hand_front(g, -7.5, -2, DIM)
    _hand_front(g, -3.8, -1, DIM)
    _hand_front(g, 0, 0, ERASE, 1.1)
    _hand_front(g, 1.5, 1, MAIN)
    g.line(29.5, 7, 31.5, 5, 2.2, HOT)
    g.line(30, 14, 32, 14, 2.2, HOT)


def void_maw(g):
    # a whirl of curved fangs closing round a lightless throat that swallows whatever drifts in
    g.disc(16, 16, 14.4)
    g.disc(16, 16, 10.8, ERASE)
    for k in range(9):
        a0 = math.radians(k * 40)
        a1 = a0 + math.radians(40)
        tip = a0 + math.radians(40 + 18)
        r0, r1 = 12.0, 5.4 + (k % 3) * 0.9
        g.poly([(16 + r0 * math.cos(a0), 16 + r0 * math.sin(a0)), (16 + r1 * math.cos(tip), 16 + r1 * math.sin(tip)),
                (16 + r0 * math.cos(a1), 16 + r0 * math.sin(a1))])
    g.disc(16, 16, 1.5, HOT)


def waking_dream(g):
    # a crescent moon cradling a doorway of light that opens in the dark
    g.disc(12.5, 16, 12.5)
    g.disc(19, 13, 10.6, ERASE)
    g.disc(22, 14.4, 4.2, HOT)
    g.rect(17.8, 14.4, 26.2, 27, HOT)
    g.rect(21.2, 19, 22.8, 27, ERASE)
    g.line(27, 5, 27, 9, 1.8, DIM)
    g.line(25, 7, 29, 7, 1.8, DIM)


def ember_step(g):
    # a burning footprint left behind in a dash: a bare sole in the ash, a tall flame guttering above it
    g.ellipse(16, 22.4, 5.8, 3.9)
    g.ellipse(16, 28.6, 3.6, 1.6)
    for dx, h in ((-3.9, 3.6), (-1.3, 4.2), (1.3, 4.2), (3.9, 3.6)):
        g.disc(16 + dx, 17.2 - h * 0.1, 1.2)
    _blade(g, (16, 15), (10.5, 8.5), (17, 0.8), 7.0, MAIN)
    _blade(g, (11.6, 15), (6, 11), (6.8, 5.4), 4.2, MAIN)
    _blade(g, (20.8, 15), (26.5, 11), (25.6, 5.8), 4.2, MAIN)
    _blade(g, (16, 14.6), (13.6, 9.6), (16.6, 5.4), 3.0, HOT)


def frost_nova(g):
    # a hexagonal ring of frost bursting outward, a snowflake locked at its heart
    hexa = lambda r: [(16 + r * math.cos(math.radians(60 * j)), 16 + r * math.sin(math.radians(60 * j))) for j in range(6)]
    g.poly(hexa(14.6))
    g.poly(hexa(11.4), ERASE)
    for j in range(3):
        a = math.radians(60 * j + 90)
        g.line(16 - 8 * math.cos(a), 16 - 8 * math.sin(a), 16 + 8 * math.cos(a), 16 + 8 * math.sin(a), 2.6, HOT)
    for j in range(6):
        a = math.radians(60 * j + 90)
        bx, by = 16 + 5.2 * math.cos(a), 16 - 5.2 * math.sin(a)
        for s in (-1, 1):
            b = a + s * math.radians(50)
            g.line(bx, by, bx + 3 * math.cos(b), by - 3 * math.sin(b), 2.0, HOT)


def ghost_step(g):
    # a faded figure slipping straight through a thin wall as if it were mist
    g.line(0.5, 10, 6, 10, 2.2, DIM)
    g.line(0, 22, 5.5, 22, 2.2, DIM)
    g.rect(12, 1.5, 20, 30)
    for y in (9, 16.5, 24):
        g.line(11, y, 21, y, 1.0, ERASE)
    g.figure(16, 4, "run", ERASE, 1.4)
    g.figure(16, 5.4, "run", DIM, 1.16)


def twin_strike(g):
    # two blades crossing in one blow, the second a ghost of the first from the twin soul
    _sword(g, 21.5, 21.5, 4.5, 4.5, DIM)
    g.disc(16, 16, 4.6, ERASE)
    _sword(g, 10.5, 21.5, 27.5, 4.5)
    g.disc(16, 16, 2.4, HOT)


def _hand_front(g, dx, dy, v, grow=0.0):
    # an open hand seen palm-on, fingers up and spread, thumb out to the left
    g.poly([(9.5 + dx - grow, 18.5 + dy), (22.5 + dx + grow, 18.5 + dy), (21.6 + dx + grow, 27 + dy + grow), (10.4 + dx - grow, 27 + dy + grow)], v)
    for x0, y0, x1, y1, w in ((10.6, 19.5, 7.6, 11.5, 3.6), (14.2, 18.5, 13, 9.5, 3.7), (17.8, 18.5, 19, 9.5, 3.7),
                              (21.4, 19.5, 24.4, 11.5, 3.6), (10.5, 24, 3.8, 18.5, 3.8)):
        g.line(x0 + dx, y0 + dy, x1 + dx, y1 + dy, w + grow * 2, v)


def _sword(g, bx, by, tx, ty, v=MAIN, hw=2.9):
    # a blade from its guard (bx, by) to its tip (tx, ty), with a cross-guard and grip behind the guard
    dx, dy = tx - bx, ty - by
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    nx, ny = -uy, ux
    g.poly([(bx + nx * hw, by + ny * hw), (tx, ty), (bx - nx * hw, by - ny * hw)], v)
    g.line(bx + nx * 5.4, by + ny * 5.4, bx - nx * 5.4, by - ny * 5.4, 3.2, v)
    g.line(bx - ux * 1.5, by - uy * 1.5, bx - ux * 7, by - uy * 7, 3.6, v)


GLYPHS = {
    "haymaker": haymaker, "ground_slam": ground_slam, "lightning_step": lightning_step, "meteor_dive": meteor_dive,
    "prism_lance": prism_lance, "shadow_step": shadow_step, "dominate": dominate, "rampart": rampart,
    "railgun": railgun, "storm_call": storm_call,
    "leap": leap, "terrain_rip": terrain_rip, "shockwave": shockwave, "brace": brace, "thunderclap": thunderclap,
    "dash": dash, "flow": flow, "afterimage": afterimage, "wall_run": wall_run, "water_run": water_run,
    "hover": hover, "flight": flight, "gravity_well": gravity_well, "telekinesis": telekinesis, "gravity_flip": gravity_flip,
    
    "beam": beam, "nova": nova, "barrier": barrier, "charged_shot": charged_shot, "overload": overload,
    "cloak": cloak, "echo_swap": echo_swap, "legion": legion,
    "danger_sense": danger_sense, "read_intent": read_intent, "decoy": decoy, "foresight": foresight,
    "wall": wall, "ramp": ramp, "floor": floor, "cone": cone, "spire": spire, "collapse": collapse, "citadel": citadel,
    "grapple": grapple, "jet_burst": jet_burst, "drone": drone, "turret": turret, "nanite_skin": nanite_skin, "drone_swarm": drone_swarm,
    "aura": aura, "spirit_palm": spirit_palm, "mend": mend, "third_eye": third_eye, "cord_lash": cord_lash, "bulwark": bulwark, "hundred_palms": hundred_palms, "void_maw": void_maw, "waking_dream": waking_dream, "ember_step": ember_step, "frost_nova": frost_nova, "ghost_step": ghost_step, "twin_strike": twin_strike,
    
    
}
