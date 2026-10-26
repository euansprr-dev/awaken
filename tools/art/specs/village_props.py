#!/usr/bin/env python3
"""Living villages (SPEC §28.6): what village people carry and the inn's key, as 16x16 handheld item textures in the
citizens' prop style (tools/art/specs/citizens.py: a diagonal shaft, a dark outline).

  python3 -I tools/art/specs/village_props.py          writes assets/awaken/textures/item/{carcass_pole,shepherd_crook,inn_key}.png
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, HERE)

from pixelart import Canvas, Out, hex_rgba, _outline  # noqa: E402
from citizens import _shaft, cl, LEATHER, IRON, BRASS, reg, ROOT  # noqa: E402

HIDE = reg("deerhide", ["#2a160c", "#47261a", "#6a3a22", "#8a5530", "#a8713f", "#c99460"])
BELLY = reg("deerbelly", ["#5a4434", "#7a604a", "#9c8066", "#bfa488", "#dcc6aa", "#f0e2cc"])


def carcass_pole() -> Canvas:
    """A hunter's pole over the shoulder with a roe deer slung from it by its legs (dusk's walk home)."""
    c = Canvas()
    _shaft(c, (0, 15), (14, 1), "wood", 3, 4)
    # the body hangs under the pole's middle: a long hide-brown barrel, its pale belly up, the head lolling at one end
    body = [(5, 9), (6, 9), (7, 9), (8, 9), (9, 8), (10, 7), (4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 9), (10, 8), (11, 7),
            (5, 11), (6, 11), (7, 11), (8, 11), (9, 10), (10, 9), (11, 8), (6, 12), (7, 12), (8, 12), (9, 11), (10, 10)]
    for (x, y) in body:
        c.set(x, y, cl(HIDE, 3 if (x + y) % 3 else 2))
    for (x, y) in ((5, 9), (6, 9), (7, 9), (8, 9), (9, 8), (10, 7)):
        c.set(x, y, cl(BELLY, 3))
    # legs tied over the pole, the head and an ear
    for (x, y) in ((7, 8), (8, 7), (10, 6), (11, 5)):
        c.set(x, y, cl(HIDE, 1))
    for (x, y) in ((3, 11), (2, 12), (3, 12), (2, 13)):
        c.set(x, y, cl(HIDE, 4))
    c.set(1, 13, cl(HIDE, 1))
    c.set(2, 11, cl(HIDE, 5))
    # the rope binding
    c.set(8, 8, hex_rgba("#c8b07a"))
    c.set(11, 6, hex_rgba("#c8b07a"))
    return _outline(c)


def shepherd_crook() -> Canvas:
    """A long crook: an ash staff with its hook turned over at the top."""
    c = Canvas()
    _shaft(c, (0, 15), (11, 4), "wood", 3, 4)
    for (x, y) in ((11, 3), (12, 2), (13, 2), (14, 3), (14, 4), (13, 5)):
        c.set(x, y, cl(LEATHER, 4))
    c.set(13, 1, cl(LEATHER, 5))
    return _outline(c)


def inn_key() -> Canvas:
    """The inn's room key on its wooden tag."""
    c = Canvas()
    for (x, y) in ((4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 6)):
        c.set(x, y, cl(BRASS, 4))
    for (x, y) in ((3, 12), (4, 13), (2, 11), (3, 10), (2, 12), (4, 12), (3, 13), (3, 11)):
        c.set(x, y, cl(BRASS, 3 if (x + y) % 2 else 5))
    c.set(3, 12, (0, 0, 0, 0))
    c.set(5, 12, cl(BRASS, 3))
    c.set(6, 11, cl(BRASS, 4))
    # the tag
    for y in range(3, 8):
        for x in range(9, 14):
            if (x, y) in ((9, 3), (13, 7)):
                continue
            c.set(x, y, cl(LEATHER, 4 if (x + y) % 4 else 3))
    c.set(11, 5, cl(LEATHER, 1))
    return _outline(c)


if __name__ == "__main__":
    out = Out(os.path.join(ROOT, "src/main/resources"))
    for name, fn in (("carcass_pole", carcass_pole), ("shepherd_crook", shepherd_crook), ("inn_key", inn_key)):
        out.item(name, fn())
    print("village props:", len(out.written))
