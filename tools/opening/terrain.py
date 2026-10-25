#!/usr/bin/env python3
"""The painted city as a runner sees it (tools/opening): what stands where, at half-block resolution.

Loads a city dump (CityDump, the city_studio_dump GameTest) and the stage pieces, and answers: the collision top of a
cell, whether a body fits standing on a cell, the floors in a column. Plan coordinates (x east, z south, ry 0 the
paving's top block). Used by routes.py to plan and check the opening's runs.
"""
import os
import re
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'city'))
import render  # noqa: E402

# collision height kinds (per block state): 0 none, 1 full, 2 bottom half, 3 wall-like (1.5, never stood on),
# 4 thin (a post or pane: blocks the body, not a floor), 5 water, 6 ladder (climbable, no floor)
NONE, FULL, HALF, TALL, THIN, WATER, LADDER = range(7)

NO_COLLISION = ('air', 'none', 'short_grass', 'tall_grass', 'poppy', 'dandelion', 'allium', 'azure_bluet', 'cornflower',
                'oxeye_daisy', 'red_tulip', 'orange_tulip', 'wheat', 'carrots', 'potatoes', 'beetroots', 'light',
                'lily_of_the_valley')


def kind_of(state):
    m = re.match(r'Block\{minecraft:([a-z0-9_]+)\}(\[(.*)\])?', state)
    if not m:
        return NONE
    bid = m.group(1)
    props = dict(kv.split('=') for kv in m.group(3).split(',')) if m.group(3) else {}
    if bid in NO_COLLISION or bid.endswith('_carpet'):
        return NONE
    if bid == 'water':
        return WATER
    if bid == 'ladder':
        return LADDER
    if bid.endswith('_slab'):
        return HALF if props.get('type', 'bottom') == 'bottom' else FULL
    if bid.endswith('_stairs'):
        return FULL  # walkable as a step: its half step lets a body walk up a stair roof
    if bid.endswith('_fence') or bid.endswith('_wall') or bid.endswith('_fence_gate'):
        return TALL
    if bid.endswith('_pane') or bid in ('iron_bars', 'iron_chain', 'lightning_rod', 'waxed_lightning_rod', 'lantern',
                                        'copper_lantern', 'bell', 'flower_pot') or bid.startswith('potted_') \
            or bid.endswith('_door') or bid.endswith('_trapdoor') or bid.endswith('_button'):
        return THIN
    return FULL


class City:
    def __init__(self, dump):
        vox, pal, ymin, ox, oz = render.load(dump)
        self.ymin, self.ox, self.oz = ymin, ox, oz
        kinds = np.array([kind_of(p) for p in pal], np.uint8)
        self.k = kinds[vox]          # (y, z, x) collision kinds
        self.stairs = np.array(['_stairs' in p for p in pal], bool)[vox]
        self.H, self.D, self.W = self.k.shape
        self.extra = {}              # stage pieces: (x, ry, z) -> kind

    # ---- the stage: pieces placed over the city ---------------------------------------------------------------------

    def place(self, x, ry, z, kind):
        self.extra[(x, ry, z)] = kind
        i, j, l = ry - self.ymin, z + self.oz, x + self.ox
        if 0 <= i < self.H and 0 <= j < self.D and 0 <= l < self.W:
            self.k[i, j, l] = kind

    # ---- queries (plan coordinates) ---------------------------------------------------------------------------------

    def kind(self, x, ry, z):
        i, j, l = ry - self.ymin, z + self.oz, x + self.ox
        if not (0 <= j < self.D and 0 <= l < self.W):
            return NONE
        if i < 0:
            return FULL
        if i >= self.H:
            return NONE
        return int(self.k[i, j, l])

    def blocks(self, x, ry, z):
        """Whether a body is blocked by this cell (anything solid, thin or tall)."""
        return self.kind(x, ry, z) in (FULL, HALF, TALL, THIN)

    def top(self, x, ry, z):
        """The collision top of the cell (ry + 1 or ry + 0.5), or None when it isn't a floor."""
        k = self.kind(x, ry, z)
        if k == FULL:
            return ry + 1.0
        if k == HALF:
            return ry + 0.5
        return None

    def fits(self, x, feet, z):
        """A standing body (1.8 tall) fits with its feet at this height in this column."""
        lo = int(np.floor(feet + 1e-6))
        hi = int(np.floor(feet + 1.8 - 1e-6))
        for ry in range(lo, hi + 1):
            k = self.kind(x, ry, z)
            if k in (FULL, TALL, THIN):
                return False
            if k == HALF and ry + 0.5 > feet + 1e-6:
                return False
        return True

    def floors(self, x, z, lo=-8, hi=150):
        """Every height a body can stand at in this column: (feet, is_water_below)."""
        out = []
        for ry in range(lo, hi):
            t = self.top(x, ry, z)
            if t is not None and self.fits(x, t, z):
                out.append(t)
        return out

    def ground(self, x, z):
        """The highest floor in a column (or None)."""
        f = self.floors(x, z)
        return f[-1] if f else None
