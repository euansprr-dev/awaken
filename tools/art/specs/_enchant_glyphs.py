"""Enchanting's script (SPEC §45.15): one glyph for every inscription. A glyph is a family's frame (wards a shield,
banes a spear, spoils a cup, carrying a gable, keeping a knot) with the inscription's own sign inside, drawn as straight
strokes between points of a 7 by 7 grid (x right, y down, 0-6), in the order a hand cuts them. The same strokes are on the
page item, in the trace, and in the air when the inscription works.

This file is the one place the glyphs are written. From it come the game's table (`python3 tools/art/specs/_enchant_glyphs.py
java` rewrites the GRID and FRAME arrays in dev/awaken/enchant/Marks.java) and the 36 page icons (tools/art/specs/enchant.py).
The leading underscore keeps build_all.py from taking it for a spec of its own.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))

SHIELD = [[(0, 0), (6, 0), (6, 3), (3, 6), (0, 3), (0, 0)]]
SPEAR = [[(0, 3), (6, 3)], [(4, 1), (6, 3), (4, 5)]]
CUP = [[(0, 0), (0, 4), (2, 6), (4, 6), (6, 4), (6, 0)]]
GABLE = [[(0, 3), (3, 0), (6, 3)]]
KNOT = [[(1, 0), (5, 0), (1, 6), (5, 6), (1, 0)]]
DIAMOND = [(3, 1), (4.5, 2.5), (3, 4), (1.5, 2.5), (3, 1)]
SQUARE = [(2, 1.5), (4, 1.5), (4, 3.5), (2, 3.5), (2, 1.5)]

HEAT, FROST, CHARGE, GRAVITY, SPIRIT = "FF8A4C", "8FD8FF", "FFD84A", "B993FF", "4FE0C2"
WARD, BANE, SPOILS, CARRY, KEEP, BLOOD = "B9C3D2", "FF5F63", "F2C45A", "4FB5EA", "F3EAD6", "9FE07A"


def g(no, name, family, colour, frame, *sign, new=False, ours=False):
    return {"no": no, "name": name, "family": family, "colour": colour, "new": new, "ours": ours or new,
            "strokes": [[list(p) for p in s] for s in list(frame) + list(sign)]}


def dn(pts, dy):
    return [(x, y + dy) for x, y in pts]


GLYPHS = [
    g(1, "Heat Ward", "ward", HEAT, SHIELD, [(2, 3.5), (3, 1.5), (4, 3.5)], ours=True),
    g(2, "Frost Ward", "ward", FROST, SHIELD, [(3, 1), (3, 4)], [(1.5, 2.5), (4.5, 2.5)], ours=True),
    g(3, "Charge Ward", "ward", CHARGE, SHIELD, [(4, 1), (2, 2.5), (4, 2.5), (2, 4)], ours=True),
    g(4, "Gravity Ward", "ward", GRAVITY, SHIELD, [(3, 1), (3, 4)], [(2, 3), (3, 4), (4, 3)], ours=True),
    g(5, "Spirit Ward", "ward", SPIRIT, SHIELD, DIAMOND, ours=True),
    g(6, "Blast Protection", "ward", WARD, SHIELD, [(2, 1.5), (4, 3.5)], [(4, 1.5), (2, 3.5)]),
    g(7, "Projectile Protection", "ward", WARD, SHIELD, [(1.5, 1.5), (4.5, 1.5)], [(3, 1.5), (3, 4)]),
    g(8, "Feather Falling", "ward", WARD, SHIELD, [(2, 1.2), (3, 2.2), (4, 1.2)], [(2, 2.8), (3, 3.8), (4, 2.8)]),
    g(9, "Clear Blood", "ward", BLOOD, SHIELD, SQUARE, ours=True),
    g(10, "Bane of the Dead", "bane", BANE, SPEAR, [(1.5, 0.5), (1.5, 5.5)], ours=True),
    g(11, "Bane of Crawlers", "bane", BANE, SPEAR, [(0.5, 1), (1.5, 3), (0.5, 5)], [(2, 1.5), (3, 3), (2, 4.5)], ours=True),
    g(12, "Bane of Beasts", "bane", BANE, SPEAR, [(0.5, 0.5), (0.5, 2)], [(1.5, 0.5), (1.5, 2)], [(2.5, 0.5), (2.5, 2)], ours=True),
    g(13, "Bane of the Deep", "bane", BANE, SPEAR, [(1, 3), (1, 5.5), (2.5, 5.5)], ours=True),
    g(14, "Looting", "spoils", SPOILS, CUP, DIAMOND),
    g(15, "Fortune", "spoils", SPOILS, CUP, [(3, 4), (3, 1)], [(2, 2), (3, 1), (4, 2)]),
    g(16, "Silk Touch", "spoils", SPOILS, CUP, SQUARE),
    g(17, "Efficiency", "spoils", SPOILS, CUP, [(1.5, 1.5), (2.5, 2.5), (1.5, 3.5)], [(3.5, 1.5), (4.5, 2.5), (3.5, 3.5)]),
    g(18, "Harvest", "spoils", SPOILS, CUP, [(2, 1), (3, 2.5), (4, 1)], [(3, 2.5), (3, 4)], ours=True),
    g(19, "Insight", "spoils", SPOILS, CUP, [(3, 1), (3, 4)], [(1.5, 2.5), (4.5, 2.5)], ours=True),
    g(20, "Respiration", "carrying", CARRY, GABLE, dn(DIAMOND, 2)),
    g(21, "Aqua Affinity", "carrying", CARRY, GABLE, [(1.5, 3.5), (4.5, 3.5)], [(3, 3.5), (3, 6)]),
    g(22, "Depth Strider", "carrying", CARRY, GABLE, [(1.5, 4), (2.25, 3.2), (3, 4), (3.75, 3.2), (4.5, 4)],
      [(1.5, 5.6), (2.25, 4.8), (3, 5.6), (3.75, 4.8), (4.5, 5.6)]),
    g(23, "Swift Sneak", "carrying", CARRY, GABLE, [(1.5, 3.5), (2.5, 4.75), (1.5, 6)], [(3.5, 3.5), (4.5, 4.75), (3.5, 6)]),
    g(24, "Unbreaking", "keeping", KEEP, KNOT, [(0, 3), (6, 3)]),
    g(25, "Mending", "keeping", KEEP, KNOT, [(2, 1.5), (4, 1.5)], [(2, 4.5), (4, 4.5)]),
    g(26, "Infinity", "keeping", KEEP, KNOT, [(3, 2), (4, 3), (3, 4), (2, 3), (3, 2)]),
    g(27, "Loyalty", "carrying", CARRY, GABLE, [(1.5, 3.5), (4.5, 3.5), (4.5, 5.5), (1.5, 5.5)]),
    g(28, "Riptide", "carrying", CARRY, GABLE, [(3, 4.6), (3.8, 4.6), (3.8, 3.6), (2.2, 3.6), (2.2, 5.6), (4.8, 5.6)]),
    g(29, "Luck of the Sea", "spoils", SPOILS, CUP, [(1.5, 3), (2.25, 2), (3, 3), (3.75, 2), (4.5, 3)]),
    g(30, "Lure", "spoils", SPOILS, CUP, [(3.5, 1), (3.5, 3.5), (2.5, 4), (2, 3)]),
    g(31, "Lantern", "carrying", CARRY, GABLE, [(3, 3), (3, 6)], [(1.5, 4.5), (4.5, 4.5)], [(2, 3.5), (4, 5.5)], [(4, 3.5), (2, 5.5)], new=True),
    g(32, "Wayfarer", "carrying", CARRY, GABLE, [(2.2, 3), (2.2, 6)], [(3.8, 3), (3.8, 6)], new=True),
    g(33, "Gather", "spoils", SPOILS, CUP, [(1.2, 1.5), (2.4, 2.5), (1.2, 3.5)], [(4.8, 1.5), (3.6, 2.5), (4.8, 3.5)], new=True),
    g(34, "Regrowth", "keeping", KEEP, KNOT, [(2.4, 3), (0.4, 1.6)], [(3.6, 3), (5.6, 1.6)], new=True),
    g(35, "Remembrance", "keeping", KEEP, KNOT, [(0, 1.5), (0, 4.5)], [(6, 1.5), (6, 4.5)], new=True),
    g(36, "Long Arm", "carrying", CARRY, GABLE, [(0.5, 4.5), (5.5, 4.5)], [(5.5, 3.5), (5.5, 5.5)], new=True),
]


FRAMES = {"ward": 1, "bane": 2, "spoils": 1, "carrying": 1, "keeping": 1}


def path(g):
    """The enchantment's id path: "Bane of the Dead" is bane_of_the_dead."""
    return g["name"].lower().replace(" ", "_")


def ident(g):
    return ("awaken:" if g["ours"] else "minecraft:") + path(g)


def sign(g):
    """The glyph without its family's frame: what a 16 pixel page has room for."""
    return g["strokes"][FRAMES[g["family"]]:]


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def java():
    """Rewrites Marks.java's two tables from GLYPHS."""
    def f(v):
        return ("%g" % v) + "F"
    rows = []
    for g in GLYPHS:
        strokes = ["{" + ", ".join(f(c) for p in s for c in p) + "}" for s in g["strokes"]]
        rows.append("            /* %2d %s */ {%s}," % (g["no"], g["name"], ", ".join(strokes)))
    p = os.path.abspath(os.path.join(HERE, "../../../src/main/java/dev/awaken/enchant/Marks.java"))
    s = open(p, encoding="utf-8").read()
    s = re.sub(r"(private static final float\[\]\[\]\[\] GRID = \{\n).*?(\n    \};)", lambda m: m.group(1) + "\n".join(rows) + m.group(2), s, flags=re.S)
    s = re.sub(r"(private static final int\[\] FRAME = \{)[^}]*(\};)", lambda m: m.group(1) + ", ".join(str(FRAMES[g["family"]]) for g in GLYPHS) + m.group(2), s)
    open(p, "w", encoding="utf-8").write(s)
    print(p, len(GLYPHS), "glyphs")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["java"]:
        java()
