"""Ground detail sprites (SPEC §31, the Soft Land): grass blades that creep over the edge of a path, dirt, gravel or sand
from a grass block beside it. Grey on purpose: the client bakes the neighbouring grass block's colour (biome tint and lawn
drift) into the quad, the way vanilla tints its grass top. The blades lie along the sprite's north edge (edges) or its
north-west corner (corners); the model turns them to the side they are wanted on.

  textures/block/ground/creep_edge_0..2.png, creep_corner_0..1.png
  textures/block/ground/creep_side_0..1.png   grass growing down a freshly dug side (SPEC §31.1), stages 1 and 2, from the
                                              top edge of a side face (vanilla's own fringe covers the first two rows)
"""
import random

from pixelart import Canvas

# greys that tint to the same range as vanilla's grass top (a mean near 147 of 255): dark, mid, mid, light, tip
SHADES = [112, 132, 140, 158, 176]


def _grey(rng, tip=False):
    v = rng.choice(SHADES[-2:] if tip else SHADES[:4])
    return (v, v, v, 255)


def _edge(seed):
    """Blades along the top edge: a solid first row, then columns of one to four blades with ragged tips."""
    rng = random.Random("creep_edge_%d" % seed)
    c = Canvas(16, 16)
    depth = [0] * 16
    for x in range(16):
        r = rng.random()
        depth[x] = 1 if r < 0.18 else 2 if r < 0.52 else 3 if r < 0.82 else 4
    # blades lean: a column next to a tall one is rarely short, so the edge reads as tufts, not noise
    for x in range(1, 15):
        depth[x] = max(depth[x], min(depth[x - 1], depth[x + 1]) - 1)
    for x in range(16):
        for y in range(depth[x]):
            tip = y == depth[x] - 1 and y > 0
            if y >= 2 and rng.random() < 0.18:
                continue                               # a gap inside a long blade, so it is not a solid wall
            c.set(x, y, _grey(rng, tip))
        if depth[x] >= 3 and rng.random() < 0.28:
            c.set(x, depth[x], _grey(rng, True))       # a blade tip poking one pixel further
    # a few stray blades beyond the edge, alone
    for _ in range(2 + seed):
        x = rng.randrange(1, 15)
        y = 5 + rng.randrange(0, 2)
        if c.get(x, y - 1)[3] == 0 and c.get(x, y)[3] == 0:
            c.set(x, y, _grey(rng, True))
    return c


def _corner(seed):
    """A tuft in the north-west corner: a small ragged triangle of blades."""
    rng = random.Random("creep_corner_%d" % seed)
    c = Canvas(16, 16)
    reach = 5 + seed
    for y in range(reach):
        for x in range(reach):
            d = x + y
            p = 1.0 - d / (reach + 0.6)
            if x == 0 and y == 0:
                p = 1.0
            if rng.random() < p * 1.15:
                c.set(x, y, _grey(rng, d >= reach - 2))
    return c


def _side(stage):
    """Grass reaching down a side face from its top edge: stage 0 about a third of the way, stage 1 most of it. Blades in
    tufts of uneven length with ragged tips and a few gaps, so the dirt shows between them, never a straight hem."""
    rng = random.Random("creep_side_%d" % stage)
    c = Canvas(16, 16)
    lo, hi = (4, 7) if stage == 0 else (9, 14)
    depth = [rng.randint(lo, hi) for _ in range(16)]
    # tufts: neighbours pull towards each other, so the edge reads as clumps of blades
    for _ in range(2):
        depth = [max(lo - 1, (depth[max(x - 1, 0)] + 2 * depth[x] + depth[min(x + 1, 15)]) // 4 + rng.randint(-1, 1)) for x in range(16)]
    for x in range(16):
        for y in range(depth[x]):
            tip = y >= depth[x] - 1
            if y >= 3 and y < depth[x] - 1 and rng.random() < 0.12:
                continue                               # a gap inside the mat: a speck of dirt
            c.set(x, y, _grey(rng, tip))
        if rng.random() < 0.3 and depth[x] < 15:
            c.set(x, depth[x], _grey(rng, True))       # a stray blade tip below the rest
    return c


def build(out):
    for i in range(3):
        out.block("ground/creep_edge_%d" % i, _edge(i))
    for i in range(2):
        out.block("ground/creep_corner_%d" % i, _corner(i))
    for i in range(2):
        out.block("ground/creep_side_%d" % i, _side(i))
