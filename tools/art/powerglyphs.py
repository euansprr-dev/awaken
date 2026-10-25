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


GLYPHS = {
    "haymaker": haymaker, "ground_slam": ground_slam, "lightning_step": lightning_step, "meteor_dive": meteor_dive,
    "prism_lance": prism_lance, "shadow_step": shadow_step, "dominate": dominate, "rampart": rampart,
    "railgun": railgun, "storm_call": storm_call,
}
