"""The Precursor Lab's monsters (SPEC §60). Room: steel, glass, cold blue light. So they are white sheet, yellow
warning paint and bare black cable, and each carries a blue arc where its current runs."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

SHEET = Ramp("#6a6e78", "#8a8e98", "#aaaeb6", "#c8ccd2", "#e2e5e9", "#f6f7f9")
STEEL = Ramp("#1e2228", "#2e343c", "#424a54", "#5a646e", "#76828c", "#96a2ac")
YELLOW = Ramp("#4a3a06", "#7a600a", "#aa8810", "#d4ac18", "#f0cc30", "#fae470")
CABLE = Ramp("#0a0a0c", "#141418", "#202026", "#2e2e36", "#40404a", "#565662")
GEL = Ramp("#0e3a44", "#16586a", "#208094", "#34a6b8", "#5ccad6", "#9ae6ec")
ARC, HOT = "#6ac8ff", "#e8f8ff"

QUAD = ("0_l", "0_r", "1_l", "1_r")
TROT = [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)]


def hazard(P, cube, faces=K.SIDES, rows=None):
    """Warning paint: slanting bars of yellow and black (on the given rows of a face, or all of it)."""
    for fname in faces:
        f = P.face(cube, fname)
        for i, j in f.each():
            if rows is None or j in rows:
                f.set(i, j, YELLOW.at(4) if (i + j) // 2 % 2 == 0 else CABLE.at(1))


def turning(r, name, length, fn, spins, n=12):
    """A loop in which several wheels turn steadily: spins = {bone: (axis, degrees a loop)} (see kit.spin_loop)."""
    keys = []
    for k in range(n + 1):
        add = {}
        for bone, (axis, turn) in spins.items():
            rot = [0.0, 0.0, 0.0]
            rot[axis] = turn * k / n
            add[bone] = tuple(rot)
        keys.append((length * k / n, K.plus(fn((k / n) % 1.0), add)))
    return K.keyed(r, name, length, keys, loop=True)


# ---------------------------------------------------------------------------------------------------------------------
# The Test Subject (test_subject): a shape under a sheet, still strapped to the table it walks on. It lurches the whole
# table at you. It bursts into sparks when it dies.

def test_subject():
    r = Rig("test_subject", (128, 64))
    r.shadow = 1.0
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    body.cube("table", -5, -1, -8, 10, 2, 16)
    K.legs2(body, "0", (4, 1, -6), 6, 7, 35, thick=(2, 1))     # the table's own legs: fore, knees ahead
    K.legs2(body, "1", (4, 1, 6), 8, 5, -24, thick=(2, 1))     # hind, hocks behind
    sh = body.bone("chest", (0, -1, 0))                         # the shape under the sheet: it strains at its straps
    sh.cube("trunk", -3.5, -4, -4, 7, 4, 8)
    sh.cube("shins", -3, -3, 4, 6, 3, 4)
    for k in range(2):
        sh.cube(f"strap{k}", -4.5, -4.5, -3 + 5 * k, 9, 5, 1, share=None if k == 0 else "strap0")
    head = sh.bone("head", (0, -1, -5), (-10, 0, 0))
    head.cube("lump", -2, -3, -3, 4, 3, 3)
    arm = sh.bone("arm_l", (4, -1, -2), (10, 0, -20))           # one arm has worked loose, and hangs
    arm.cube("upperT", -0.5, 0, -0.5, 1, 5, 1)
    arm.bone("fore_l", (0, 5, 0), (-20, 0, 0)).cube("handT", -0.5, 0, -0.5, 1, 5, 1)
    for k in range(2):
        body.bone(f"chain{k}", (-4 + 8 * k, 1, 7)).cube(f"cableT{k}", -0.5, 0, -0.5, 1, 6 + 2 * k, 1, share=None)
    r.look = [("head", 0.3, 0.2)]
    r.pack()

    lagc = {"head": 0.05, "arm_l": 0.06, "fore_l": 0.12, "chain0": 0.12, "chain1": 0.14}
    heave = lambda k: {"chest": {"scale": (1, k, 1)}}  # noqa: E731
    K.moves(r, lurch="Lurch")
    tipped = K.planted(r, K.plus(heave(0.9), {"body": {"rot": (16, 0, 4), "pos": (0, -2.5, -2)}, "head": (20, 0, 0), "arm_l": (-30, 0, -10)}), QUAD)
    K.act(r, "lurch", K.plus(heave(1.35), {"body": {"rot": (-14, 0, 0), "pos": (0, -1.5, 2.5)}, "head": (-26, 0, 0), "arm_l": (40, 0, -30)}),
          K.plus(heave(1.1), {"body": {"rot": (10, 0, 0), "pos": (0, 1.5, -5)}, "head": (16, 0, 0), "arm_l": (-70, 0, -10), "leg0_l": (-50, 0, 0), "leg0_r": (-50, 0, 0),
                              "leg1_l": (40, 0, 0), "leg1_r": (40, 0, 0)}), w=0.9, length=0.5, t_hit=0.08, plant=QUAD, lag=lagc, stuck=tipped)
    K.plain(r, K.plus(heave(1.2), {"body": {"rot": (-8, 0, 0), "pos": (0, 0, 1.5)}, "arm_l": (20, 0, -40)}), K.plus(heave(1.0), {"body": {"rot": (10, 0, 0), "pos": (0, -0.5, -2.6)}, "arm_l": (-80, 0, 10)}),
            # its other swing: the one loose arm flung back across the other way
            second=(K.plus(heave(1.25), {"body": {"rot": (-6, 0, 6), "pos": (0, 0, 1.2)}, "arm_l": (-90, 0, 30), "fore_l": (-30, 0, 0)}),
                    K.plus(heave(1.0), {"body": {"rot": (10, 0, -6), "pos": (0, -0.5, -2.6)}, "arm_l": (-30, 0, -50), "fore_l": (10, 0, 0)})),
            plant=QUAD, lag=lagc)
    K.loop(r, "idle", 5.0, lambda p: K.plus(heave(1 + 0.06 * s(p) + 0.3 * bump(p, 0.6, 0.03)), {"head": (4 * s(p, 0.2) - 14 * bump(p, 0.6, 0.03), 10 * s(p, 0.4), 0),
                                                                                              "arm_l": (4 * s(p, 0.3), 0, 0), "body": (0, 0, 2.5 * bump(p, 0.62, 0.03))}), n=20)
    K.loop(r, "walk", 0.9, lambda p: K.plus(heave(1 + 0.08 * s(p * 2)), {"body": (1.5 * s(p * 2), 0, 2.5 * s(p)), "head": (0, 0, 6 * s(p, 0.1)), "arm_l": (10 * s(p, 0.2), 0, 0)}))
    K.loop(r, "run", 0.55, lambda p: K.plus(heave(1.15 + 0.1 * s(p * 2)), {"body": (4 * s(p * 2, 0.25), 0, 3.5 * s(p)), "head": (-10, 0, 8 * s(p, 0.1)), "arm_l": (20 * s(p, 0.2), 0, -10)}))
    K.fallback_walk(r, TROT)
    K.loop(r, "angry", 0.8, lambda p: K.plus(heave(1.25 + 0.15 * s(p * 2)), {"head": (-20, 12 * s(p), 0), "arm_l": (-40 + 20 * s(p * 2), 0, -30), "body": {"pos": (0, -0.8, 0)}}), plant=QUAD)
    K.flinch(r, K.plus(heave(1.3), {"body": {"rot": (-6, 0, 7), "pos": (0, 0, 1.5)}, "head": (-20, 0, 0)}), plant=QUAD)
    side = K.plus(heave(0.6), {"body": (0, 0, 86), "head": (10, 20, 0), "arm_l": (-40, 0, -30), "leg0_l": (30, 0, 0), "leg0_r": (10, 0, 0), "leg1_l": (-30, 0, 0), "leg1_r": (-10, 0, 0)})
    start = K.planted(r, K.plus(heave(1.5), {"body": {"pos": (0, 1, 0)}, "head": (-30, 0, 0)}), QUAD)
    skip = ("cableT0", "cableT1", "handT")
    K.keyed(r, "death", 1.1, [(0, {}), (0.14, start)] + K.fall(r, 0.14, start, 0.7, side, steps=4, skip=skip)
            + [(0.82, K.on_floor(r, K.plus(side, {"body": (0, 0, -6)}), skip=skip)), (1.1, K.on_floor(r, side, skip=skip))], lag=lagc)
    return r


def paint_test_subject(r, P):
    P.paint("table", STEEL, 3.2, p_plates(8, 4, 0.4), rim=0.9)
    hazard(P, "table", K.SIDES)
    for nm, base in (("trunk", 4.2), ("shins", 4.0), ("lump", 4.4)):
        P.paint(nm, SHEET, base, p_strokes(3, 4, dark=0.5, light=0.3, seed=2), rim=0.3, ao=0.5)
    f, g = P.face("trunk", "top"), P.face("trunk", "top", glow=True)     # the current under the sheet, showing through
    for i, j in f.each():
        if (i * 3 + j * 5) % 11 == 0:
            f.set(i, j, hexc(ARC))
            g.set(i, j, hexc(ARC, 190))
    for fname in ("north",):
        ff, gg = P.face("lump", fname), P.face("lump", fname, glow=True)
        for i in (1, 2):
            ff.set(i, 1, hexc(ARC))
            gg.set(i, 1, hexc(ARC, 140))
    P.paint("strap0", YELLOW, 3.4, rim=0.4)
    for fname in K.ALL:
        ff = P.face("strap0", fname)
        for i, j in ff.each():
            if (i + j) % 4 == 0:
                ff.set(i, j, CABLE.at(1))
    P.paint("upperT", SHEET, 2.2)
    P.paint("handT", SHEET, 1.6)
    for k in range(2):
        P.paint(f"cableT{k}", CABLE, 2.2)
        for fname in K.SIDES:
            ff, gg = P.face(f"cableT{k}", fname), P.face(f"cableT{k}", fname, glow=True)
            ff.set(0, ff.h - 1, hexc(ARC))
            gg.set(0, ff.h - 1, hexc(ARC, 230))
    for tag in ("0", "1"):
        P.paint(f"thigh{tag}_l", STEEL, 3.6)
        P.paint(f"shinb{tag}_l", STEEL, 2.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Security Unit (security_unit): a shut steel egg that opens three legs and one lens when you come near. Shut,
# nothing hurts it. It has to open to see you.

def security_unit():
    r = Rig("security_unit", (128, 64))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 14, 0)), 14)
    body.cube("eggm", -4, -4, -4, 8, 8, 8)
    body.cube("eggt", -3, -6, -3, 6, 2, 6)
    body.cube("eggb", -3, 4, -3, 6, 1, 6)
    for side, nm in SIDES2:   # two legs ahead and one behind, each on a mount turned its way
        m = K.at(body.bone("mount_" + nm, (side * 3, 2, -2), (0, side * 50, 0)), 16)
        K.splay_leg(m, "0_" + nm, side, (0, 0, 0), 5, 11, 26, 0, thick=1, share=None if side > 0 else ("thigh0_l", "shinb0_l"))
    mb = K.at(body.bone("mount", (0, 2, 3), (0, -90, 0)), 16)
    K.splay_leg(mb, "1_l", 1, (0, 0, 0), 5, 11, 26, 0, thick=1, share=("thigh0_l", "shinb0_l"))
    lens = body.bone("lens", (0, -1, -4))
    lens.cube("barrelS", -1.5, -1.5, -2, 3, 3, 2)
    lens.cube("glassS", -1, -1, -2.6, 2, 2, 1)
    r.pack()

    THREE = ("0_l", "0_r", "1_l")
    lens_at = lambda d, k=1.0: {"lens": {"pos": (0, 0, -d), "scale": (k, k, 1)}}  # noqa: E731  (px further out)
    # shut: the egg sat down on the floor with its legs drawn up round it and its lens in. Standing is its rest.
    shut = K.planted(r, K.plus(lens_at(-2.4, 0.6), {"body": {"pos": (0, -4.6, 0)}}), THREE)
    K.moves(r, lance="Scan Lance")
    K.act(r, "lance", K.plus(lens_at(1.2, 1.2), {"body": {"pos": (0, -1.2, 0.5), "rot": (-5, 0, 0)}}), K.plus(lens_at(-0.6, 1.3), {"body": {"pos": (0, -1.0, 1.6), "rot": (-8, 0, 0)}}),
          w=1.0, length=0.5, t_hit=0.07, plant=THREE, open_pose=K.planted(r, K.plus(lens_at(0.8, 0.8), {"body": {"pos": (0, -2.4, 0), "rot": (10, 0, 4)}}), THREE))
    K.plain(r, K.plus(lens_at(-1.5), {"body": {"pos": (0, 0.6, 1.5), "rot": (-10, 0, 0)}}), K.plus(lens_at(0.5), {"body": {"pos": (0, -0.6, -2.6), "rot": (12, 0, 0)}}), plant=THREE)
    K.loop(r, "idle", 6.0, lambda p: K.plus(shut, {"body": {"rot": (0, 0, 0.8 * s(p))}}), n=8)
    K.loop(r, "walk", 0.8, lambda p: K.plus(lens_at(0.2 * s(p * 2)), {"body": (2 * s(p * 3), 0, 2 * s(p * 3, 0.25))}))
    K.loop(r, "run", 0.55, lambda p: {"body": (5 + 3 * s(p * 3), 0, 3 * s(p * 3, 0.25))})
    K.fallback_walk(r, [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.25, 1)], axis="y")
    K.loop(r, "angry", 2.4, lambda p: K.plus(lens_at(0.4 + 0.4 * s(p * 2), 1 + 0.1 * s(p * 4)), {"body": {"rot": (0, 14 * s(p), 0), "pos": (0, 0.4 * s(p * 2), 0)}}), n=16)
    K.flinch(r, K.plus(lens_at(-2.0, 0.7), {"body": {"pos": (0, -1, 1.5), "rot": (0, 0, 8)}}), plant=THREE)
    fold = {"leg0_l": (0, 0, -30), "leg0_r": (0, 0, 30), "leg1_l": (0, 0, -30), "shin0_l": (0, 0, 60), "shin0_r": (0, 0, -60), "shin1_l": (0, 0, 60)}
    low = K.plus(lens_at(-2.4, 0.3), fold, {"body": (0, 0, 60)})
    K.keyed(r, "death", 1.0, [(0, {}), (0.14, K.planted(r, K.plus(lens_at(1.5, 1.3), {"body": {"pos": (0, 1.2, 0)}}), THREE))] + K.fall(r, 0.14, shut, 0.7, low, steps=4)
            + [(0.82, K.on_floor(r, K.plus(low, {"body": (0, 0, 8)}))), (1.0, K.on_floor(r, low))])
    return r


def paint_security_unit(r, P):
    for nm, base in (("eggm", 3.6), ("eggt", 4.0), ("eggb", 2.6)):
        P.paint(nm, STEEL, base, p_plates(4, 4, 0.4), rim=0.9, ao=0.5)
    hazard(P, "eggm", K.SIDES, rows=(6, 7))
    for fname in K.SIDES:     # the seam where it opens
        f = P.face("eggm", fname)
        for i in range(f.w):
            f.set(i, 2, CABLE.at(0.6))
    P.paint("barrelS", CABLE, 2.4, rim=0.6)
    K.lit_cube(P, "glassS", ARC, HOT, a=250, hot=lambda fn, i, j, f: fn == "north" and (i + j) % 2 == 0)
    P.paint("thigh0_l", STEEL, 3.0)
    P.paint("shinb0_l", CABLE, 3.0, p_bands(4, 0.8))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Coil (coil_unit): a ring floating over a column, sparks climbing it. The sparks reach the ring, then it fires.
# Crowd it and it lets everything go at once.

def coil():
    r = Rig("coil_unit", (64, 64))
    r.shadow = 0.5
    b = r.bone("body", (0, 12, 0))
    b.cube("footC", -3, 6, -3, 6, 2, 6)
    b.cube("column", -1.5, -6, -1.5, 3, 12, 3)
    b.bone("spark", (0, 5, -1.6)).cube("sparkc", -1, -1, -0.5, 2, 2, 1)
    ring = b.bone("ring", (0, -8.5, 0))
    ring.cube("ring_n", -4, -1, -4, 8, 2, 2)
    ring.cube("ring_s", -4, -1, 2, 8, 2, 2, share="ring_n")
    ring.cube("ring_w", 2, -1, -2, 2, 2, 4)
    ring.cube("ring_e", -4, -1, -2, 2, 2, 4, share="ring_w")
    for k in range(2):
        b.bone(f"chain{k}", (-2 + 4 * k, 8, 1)).cube(f"cableC{k}", -0.5, 0, -0.5, 1, 4 + k, 1)
    r.pack()

    climb = lambda y, k=1.0: {"spark": {"pos": (0, y, 0), "scale": (k, k, 1)}}  # noqa: E731  (px up the column from its foot)
    lagc = {"ring": 0.06, "chain0": 0.1, "chain1": 0.12}
    K.moves(r, bolt="Coil Bolt", discharge="Discharge")
    K.act(r, "bolt", K.plus(climb(11.5, 1.3), {"ring": {"rot": (-14, 0, 0), "pos": (0, 0.8, 0)}, "body": {"rot": (-8, 0, 0), "pos": (0, 0.8, 1)}}),
          K.plus(climb(12.5, 2.2), {"ring": {"rot": (20, 0, 0), "pos": (0, 1.6, 0), "scale": (1.15, 1, 1.15)}, "body": {"rot": (8, 0, 0), "pos": (0, 0, 2)}}), w=0.8, length=0.45, t_hit=0.07, lag=lagc,
          open_pose=K.plus(climb(0, 0.05), {"ring": {"rot": (26, 0, 10), "pos": (0, -1, 0)}, "body": {"rot": (14, 0, 0), "pos": (0, -2, 0)}}))
    K.act(r, "discharge", K.plus(climb(11.5, 1.6), {"ring": {"rot": (0, 180, 0), "pos": (0, 3, 0)}, "body": {"pos": (0, 1.5, 0)}}),
          K.plus(climb(6, 2.6), {"ring": {"rot": (0, 360, 0), "pos": (0, -1.2, 0), "scale": (1.3, 1, 1.3)}, "body": {"pos": (0, -1.5, 0)}}), w=1.0, length=0.55, t_hit=0.08, lag=lagc,
          open_pose=K.plus(climb(0, 0.05), {"ring": {"rot": (0, 360, 12), "pos": (0, -1.2, 0)}, "body": {"rot": (10, 0, 6), "pos": (0, -2.5, 0)}}))
    K.plain(r, K.plus(climb(8), {"body": {"rot": (-24, 0, 0), "pos": (0, 0.8, 1)}}), K.plus(climb(12, 1.6), {"body": {"rot": (40, 0, 0), "pos": (0, -1, -3.5)}, "ring": (16, 0, 0)}), lag=lagc)
    K.loop(r, "idle", 2.4, lambda p: K.plus(climb(11.0 * p, 0.7 + 0.5 * p), {"ring": {"pos": (0, 0.6 * s(p), 0), "rot": (3 * s(p, 0.2), 0, 3 * s(p))}, "body": {"pos": (0, 0.8 * s(p, 0.3), 0)}}), n=12)
    K.loop(r, "fly", 1.0, lambda p: K.plus(climb(11.0 * p), {"body": (10, 0, 2 * s(p)), "ring": (-6, 0, 0)}))
    K.loop(r, "angry", 0.6, lambda p: K.plus(climb(11.0 * p, 1.4), {"ring": {"pos": (0, 1.2, 0), "rot": (0, 0, 6 * s(p))}, "body": (-6, 0, 0)}))
    K.flinch(r, K.plus(climb(2, 0.4), {"ring": {"rot": (-20, 0, 20), "pos": (0, 1.5, 0)}, "body": {"rot": (-12, 0, 16), "pos": (0, 0, 2)}}))
    flat = K.plus(climb(0, 0.02), {"body": (84, 0, 10), "ring": {"pos": (3, -4, 0), "rot": (40, 0, 70)}})
    K.keyed(r, "death", 1.0, [(0, {}), (0.1, K.plus(climb(12, 2.6), {"ring": {"pos": (0, 3, 0)}})), (0.4, K.plus(climb(0, 0.02), {"body": {"rot": (20, 0, 30), "pos": (0, -4, 0)}, "ring": {"pos": (1, -1, 0), "rot": (20, 0, 30)}})),
                              (0.75, K.on_floor(r, flat)), (0.86, K.on_floor(r, K.plus(flat, {"body": (-8, 0, 0)}))), (1.0, K.on_floor(r, flat))], lag=lagc)
    return r


def paint_coil(r, P):
    copper = Ramp("#3a1a0e", "#5c2c16", "#844424", "#ac6238", "#cc8656", "#e6b088")
    P.paint("footC", STEEL, 2.8, rim=0.8)
    hazard(P, "footC", K.SIDES)
    P.paint("column", copper, 3.0, p_bands(2, 1.2), rim=0.3)
    K.lit_cube(P, "sparkc", ARC, HOT, a=255, hot=lambda fn, i, j, f: (i + j) % 2 == 0)
    for nm in ("ring_n", "ring_w"):
        P.paint(nm, STEEL, 3.8, rim=1.0)
        for fname in ("top", "bottom"):
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                if (i + j) % 3 == 0:
                    f.set(i, j, hexc(ARC))
                    g.set(i, j, hexc(ARC, 150))
    for k in range(2):
        P.paint(f"cableC{k}", CABLE, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Gel Sample (gel_sample, the elite): gel that carries the broken glass of its own tank round it like a shell. Kill
# it and it becomes two, without glass.

WALLS = [(0, -5.5, 0), (5.5, 0, 90), (0, 5.5, 180), (-5.5, 0, 270)]     # (x, z, turn): what is left of the tank's four walls


def gel_sample():
    r = Rig("gel_sample", (128, 64))
    r.shadow = 1.0
    body = r.bone("body", (0, 22, 0))
    body.cube("gel", -5, -9, -5, 10, 9, 10)
    # its glass and its plinth hang from bones named jar*: a half that came out of a broken one is drawn without them
    body.bone("jar_base", (0, 0, 0)).cube("plinthG", -6, 0, -6, 12, 2, 12)
    body.bone("core", (0, -4, 0)).cube("nucleus", -2, -2, -2, 4, 4, 4)
    for k, (x, z, turn) in enumerate(WALLS):
        w = body.bone(f"jar_wall{k}", (x, 0, z), (0, turn, 0))
        w.cube(f"shardG{k}", -5, -7 - 2 * (k % 2), -0.5, 6 + 2 * (k % 3), 7 + 2 * (k % 2), 1)
    lid = body.bone("jar_cap", (2, -9.5, 1), (0, 20, 14))
    lid.cube("lidG", -5, -1, -5, 10, 1, 10)
    lid.cube("valveG", -1.5, -3, -1.5, 3, 2, 3)
    r.pack()

    def shell(a, k=1.0):
        """The glass leaned out from the gel (a degrees), the gel squashed to k of its height."""
        q = {f"jar_wall{i}": (-a, 0, 0) for i in range(4)}
        q["body"] = {"scale": (1 + (1 - k) * 0.6, k, 1 + (1 - k) * 0.6)}
        return q
    beat = lambda k: {"core": {"scale": (k, k, k)}}  # noqa: E731
    K.moves(r, breach="Containment Breach")
    K.act(r, "breach", K.plus(shell(-4, 0.7), beat(1.3), {"jar_cap": (0, 0, 10)}), K.plus(shell(30, 1.25), beat(1.6), {"body": {"pos": (0, 3, -2), "rot": (-10, 0, 0)}, "jar_cap": {"pos": (0, 3, 0), "rot": (0, 0, -30)}}),
          w=0.9, length=0.5, t_hit=0.09, open_pose=K.plus(shell(44, 0.55), beat(0.7), {"jar_cap": {"pos": (2, 1, 0), "rot": (0, 0, 30)}}))
    K.plain(r, K.plus(shell(-3, 0.85), {"body": {"rot": (-10, 0, 0)}}), K.plus(shell(14, 1.1), {"body": {"rot": (16, 0, 0), "pos": (0, 0.5, -2)}}))
    K.loop(r, "idle", 3.0, lambda p: K.plus(shell(2 * s(p), 1 + 0.03 * s(p * 2)), beat(1 + 0.12 * s(p * 2)), {"jar_cap": (0, 0, 2 * s(p))}))
    K.loop(r, "walk", 0.5, lambda p: K.plus(shell(4 + 6 * max(0, s(p)), 1 + 0.12 * s(p)), {"jar_cap": {"pos": (0, 0.8 * max(0, s(p, 0.1)), 0)}}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(shell(10 + 6 * s(p * 2), 1.05), beat(1.4 + 0.2 * s(p * 4)), {"body": (0, 0, 4 * s(p))}))
    K.flinch(r, K.plus(shell(18, 0.88), beat(0.7), {"body": {"rot": (-8, 0, 10)}, "jar_cap": {"pos": (0, 1.5, 0)}}))
    burst = {f"jar_wall{i}": {"rot": (-86, 0, 0), "pos": (2.5 * math.sin(math.radians(WALLS[i][2])), 0, -2.5 * math.cos(math.radians(WALLS[i][2])))} for i in range(4)}
    K.keyed(r, "death", 0.9, [(0, {}), (0.12, K.plus(shell(-5, 0.8), beat(1.8))), (0.4, K.plus(burst, beat(0.5), {"body": {"scale": (1.25, 0.45, 1.25)}, "jar_cap": {"pos": (4, -4, 2), "rot": (0, 0, 60)}})),
                              (0.9, K.plus(burst, beat(0.02), {"body": {"scale": (1.4, 0.16, 1.4)}, "jar_cap": {"pos": (5, -7, 3), "rot": (0, 0, 80), "scale": (1 / 1.4, 6, 1 / 1.4)}}))])
    return r


def paint_gel_sample(r, P):
    P.paint("gel", GEL, 3.0, p_blotch("gel", 5, 0.8), rim=0.5)
    for fname in K.ALL:    # gel: a soft dither you can see its heart through, lit from inside
        f, g = P.face("gel", fname), P.face("gel", fname, glow=True)
        for i, j in f.each():
            if 0 < i < f.w - 1 and 0 < j < f.h - 1 and (i + j) % 2 == 0:
                f.set(i, j, (0, 0, 0, 0))
            else:
                g.set(i, j, hexc(ARC, 60))
    K.lit_cube(P, "nucleus", ARC, HOT, a=240, hot=lambda fn, i, j, f: 0 < i < f.w - 1 and 0 < j < f.h - 1)
    P.paint("plinthG", STEEL, 2.8, rim=0.8)
    hazard(P, "plinthG", K.SIDES)
    for k in range(4):     # broken glass: a clean edge below, a jagged one above
        nm = f"shardG{k}"
        P.paint(nm, SHEET, 4.6, rim=0.2)
        for fname in ("north", "south"):
            f = P.face(nm, fname)
            for i, j in f.each():
                jag = 1 + (K._h(nm, i) % max(2, f.h // 2))
                if j < jag:
                    f.set(i, j, (0, 0, 0, 0))
                elif j == jag or i in (0, f.w - 1) or j == f.h - 1:
                    f.set(i, j, SHEET.at(5))
                elif (i + j + k) % 3 != 0:
                    f.set(i, j, (0, 0, 0, 0))
    P.paint("lidG", STEEL, 3.4, rim=0.9)
    P.paint("valveG", YELLOW, 3.6, rim=0.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Sweeper (the_sweeper, the Warden): a floor-cleaning engine as wide as the corridor, a turning drum of brushes in
# front. It charges straight. It cannot see anything that is beside it.

def sweeper():
    r = Rig("the_sweeper", (128, 128))
    r.shadow = 1.8
    body = r.bone("body", (0, 22, 0))
    body.cube("housing", -12, -10, -6, 24, 9, 15)
    body.cube("cowl", -11, -12, -8, 22, 4, 5)
    body.cube("skid", -11, -1, -5, 22, 1, 13)
    drum = body.bone("drum", (0, -4, -10))
    drum.cube("brush", -11, -3, -3, 22, 6, 6)
    drum.bone("drum_b", (0, 0, 0), (45, 0, 0)).cube("brush_b", -10.9, -3, -3, 22, 6, 6, share="brush")
    for side, nm in SIDES2:
        body.bone("wheel_" + nm, (side * 12.5, -3, 5)).cube("tyre_" + nm, -1, -3, -3, 2, 6, 6, share=None if side > 0 else "tyre_l")
        body.bone("vent_" + nm, (side * 8, -10, 6), (0, 0, 0)).cube("flap_" + nm, -3, -1, -2, 6, 1, 4, share=None if side > 0 else "flap_l")
    lamp = body.bone("beacon", (0, -10, 2))
    lamp.cube("beaconb", -1.5, -1, -1.5, 3, 1, 3)
    lamp.cube("beaconl", -1, -3, -1, 2, 2, 2)
    for k in range(3):
        body.bone(f"chain{k}", (-8 + 8 * k, -2, 9)).cube(f"hose{k}", -0.5, 0, -0.5, 1, 3, 1, share=None if k == 0 else "hose0")
    r.pack()

    rolls = {"drum": (0, 45), "wheel_l": (0, 90), "wheel_r": (0, 90), "beacon": (1, 90)}
    vents = lambda a: {"vent_l": (0, 0, a), "vent_r": (0, 0, -a)}  # noqa: E731
    K.moves(r, purge="Purge Protocol", sweep="Sweep", decon="Decon Blast")
    stalled = {"body": {"rot": (9, 0, 3), "pos": (0, 0, -1.5)}, "drum": (-20, 0, 0), "beacon": {"scale": (1, 0.5, 1)}, **vents(50)}
    K.act(r, "purge", {"body": {"rot": (-7, 0, 0), "pos": (0, 0, 3)}, "drum": (360, 0, 0), **vents(20)}, {"body": {"rot": (6, 0, 0), "pos": (0, 0, -6)}, "drum": (900, 0, 0)},
          w=1.2, length=0.5, t_hit=0.08, over=0.03, stuck=stalled)
    K.act(r, "sweep", {"body": (0, 46, 0), "drum": (180, 0, 0)}, {"body": {"rot": (0, -64, 0), "pos": (0, 0, -1.5)}, "drum": (540, 0, 0)}, w=0.9, length=0.6, t_hit=0.12, over=0.06,
          open_pose={"body": (0, -40, 3), "drum": (585, 0, 0), **vents(40)})
    K.act(r, "decon", {"body": {"scale": (1.03, 0.92, 1.03)}, **vents(-10)}, {"body": {"scale": (0.98, 1.06, 0.98), "pos": (0, 0.6, 1)}, **vents(70)}, w=1.0, length=0.55, t_hit=0.09,
          open_pose={"body": {"rot": (0, 0, 2)}, **vents(60), "beacon": {"scale": (1, 0.5, 1)}})
    K.plain(r, {"body": {"rot": (-5, 0, 0), "pos": (0, 0, 2)}, "drum": (90, 0, 0)}, {"body": {"rot": (5, 0, 0), "pos": (0, 0, -3.5)}, "drum": (360, 0, 0)},
            heavy=({"body": (0, 30, 0), "drum": (135, 0, 0)}, {"body": {"rot": (0, -40, 0), "pos": (0, 0, -2)}, "drum": (495, 0, 0)}),
            # its other swing: the ram brought in from the other side
            second=({"body": {"rot": (-5, -24, 0), "pos": (0, 0, 2)}, "drum": (90, 0, 0)}, {"body": {"rot": (5, 26, 0), "pos": (0, 0, -3.5)}, "drum": (360, 0, 0)}))
    turning(r, "idle", 4.0, lambda p: {"body": {"rot": (0, 0, 0.5 * s(p * 4)), "pos": (0, 0.15 * s(p * 8), 0)}}, {"drum": (0, 45), "beacon": (1, 360)}, n=16)
    turning(r, "walk", 0.8, lambda p: {"body": (1.0 * s(p * 2), 0, 0.8 * s(p))}, rolls)
    turning(r, "run", 0.4, lambda p: {"body": (3 + 1.0 * s(p * 2), 0, 1.0 * s(p))}, {"drum": (0, 90), "wheel_l": (0, 90), "wheel_r": (0, 90), "beacon": (1, 90)})
    turning(r, "angry", 0.6, lambda p: K.plus(vents(20 + 10 * s(p * 2)), {"body": (-3, 0, 1.0 * s(p * 2))}), {"drum": (0, 90), "beacon": (1, 360)})
    K.flinch(r, K.plus(vents(40), {"body": {"rot": (-4, 0, 4), "pos": (0, 0, 2)}, "drum": (-30, 0, 0)}))
    dead = {"body": {"rot": (6, 0, 5), "pos": (0, 0, 0)}, "drum": (60, 0, 0), "beacon": {"scale": (1, 0.3, 1)}, **vents(80)}
    K.keyed(r, "death", 1.6, [(0, {}), (0.2, K.plus(vents(60), {"body": (-5, 0, -3), "drum": (200, 0, 0)})), (0.6, K.plus(vents(80), {"body": (4, 0, 6), "drum": (260, 0, 0)})),
                              (1.0, K.on_floor(r, K.plus(dead, {"drum": (275, 0, 0)}))), (1.6, K.on_floor(r, K.plus(dead, {"drum": (280, 0, 0)})))])
    return r


def paint_sweeper(r, P):
    P.paint("housing", SHEET, 3.8, p_plates(8, 5, 0.4), rim=0.6, ao=0.6)
    hazard(P, "housing", K.SIDES, rows=(6, 7, 8))
    P.paint("cowl", STEEL, 3.0, rim=0.9)
    hazard(P, "cowl", ("north",))
    P.paint("skid", CABLE, 2.0)
    f, g = P.face("housing", "north"), P.face("housing", "north", glow=True)   # a strip of sensors that only looks ahead
    for i in range(4, f.w - 4):
        f.set(i, 2, CABLE.at(0))
        if i % 3 == 1:
            f.set(i, 2, hexc(ARC))
            g.set(i, 2, hexc(ARC, 240))
    P.paint("brush", CABLE, 2.6, p_strokes(1, 3, dark=0.9, light=0.7, seed=3))
    for fname in ("top", "bottom", "north", "south"):
        fb = P.face("brush", fname)
        for i, j in fb.each():
            if (i * 7 + j * 3) % 5 == 0:
                fb.set(i, j, YELLOW.at(3.4))      # yellow bristles among the black
            elif (i + j) % 2 == 0 and j in (0, fb.h - 1):
                fb.set(i, j, (0, 0, 0, 0))        # a ragged edge
    P.paint("tyre_l", CABLE, 1.6, p_bands(2, 1.0), rim=0.4)
    for fname in ("west", "east"):
        ft = P.face("tyre_l", fname)
        for i, j in ft.each():
            if math.hypot(i - 2.5, j - 2.5) < 1.5:
                ft.set(i, j, STEEL.at(4))
    P.paint("flap_l", STEEL, 3.4, p_bands(2, 0.8, vertical=True), rim=0.6)
    P.paint("beaconb", STEEL, 2.6)
    K.lit_cube(P, "beaconl", ARC, HOT, a=250, hot=lambda fn, i, j, f: fn in ("north", "south"))
    P.paint("hose0", CABLE, 2.4)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
HUM = [("block.beacon.ambient", 0.4, 1.4)]
MONSTERS = [
    K.Monster("test_subject", "The Test Subject", test_subject, paint_test_subject, kind="runner", box=(1.0, 1.4), sunk=("cableT", "handT"),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.7, 0.46), lift=(2.0, 3.4), crouch=0.5, swing=0.28, bob=0.6, roll=0.06,
                                               lag=-0.25, lead=0.12, rear=1.0, speed=0.7),
                                "body": K.body(headLevel=0.0, twist=0.15, slope=0.8, land=1.3, shift=0.6)},
              life=dict(STILL, temperament="monster", breath=0.6, breathDepth=1.4, idles={"settle": 2, "shake": 1}, arms=0.0, mass=1.8, springHz=1.6, springGain=1.4),
              voice={"ambient": [("entity.zombie_villager.ambient", 0.5, 0.6), ("block.chain.step", 0.5, 0.8)], "hurt": [("entity.zombie_villager.hurt", 0.7, 0.6), ("block.iron.hit", 0.5, 1.0)],
                     "death": [("entity.zombie_villager.death", 0.8, 0.6), ("block.iron.break", 0.7, 0.9)], "step": [("block.iron.step", 0.4, 1.2)]}),
    K.Monster("security_unit", "The Security Unit", security_unit, paint_security_unit, kind="crawler", box=(0.8, 1.0),
              motion=lambda r: {"gait": K.gait("ripple", K.room(r, K.legs_of(r), 0.5), duty=(0.72, 0.6), lift=(2.0, 3.2), crouch=0.5, swing=0.24, bob=0.4, roll=0.03, speed=0.7),
                                "body": K.body(headLevel=0.0, twist=0.1, slope=0.7, land=1.2, shift=0.5)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 1}, mass=1.4),
              voice={"ambient": HUM + [("block.comparator.click", 0.4, 1.4)], "hurt": [("block.iron.hit", 0.9, 1.3), ("block.dispenser.fail", 0.6, 1.3)],
                     "death": [("block.iron.break", 0.9, 1.2), ("block.beacon.deactivate", 0.7, 1.5)], "step": [("block.iron.step", 0.3, 1.6)]}),
    K.Monster("coil_unit", "The Coil", coil, paint_coil, kind="floater", box=(0.7, 1.2), lift=3, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.2, tilt=0.2, climb=0.4)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=1.6),
              voice={"ambient": HUM + [("block.respawn_anchor.ambient", 0.4, 1.4)], "hurt": [("block.copper.hit", 0.8, 1.3), ("block.respawn_anchor.deplete", 0.3, 1.6)],
                     "death": [("block.copper.break", 0.9, 1.2), ("block.beacon.deactivate", 0.8, 1.4)]}),
    K.Monster("gel_sample", "The Gel Sample", gel_sample, paint_gel_sample, kind="slider", box=(1.0, 1.0), sunk=("shardG",),
              motion={"body": K.body(headLevel=0.0, twist=0.15, land=1.8, shift=0.0)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 1}, springHz=2.0, springGain=1.6, mass=2.4),
              voice={"ambient": [("entity.slime.squish", 0.6, 0.7), ("block.glass.step", 0.4, 1.0)], "hurt": [("block.glass.hit", 0.9, 0.9), ("entity.slime.hurt", 0.6, 0.7)],
                     "death": [("block.glass.break", 1.0, 0.8), ("entity.slime.death", 0.8, 0.7)], "step": [("entity.slime.jump", 0.5, 0.7)]}),
    K.Monster("the_sweeper", "The Sweeper", sweeper, paint_sweeper, kind="slider", box=(1.6, 1.0), sunk=("hose",),
              motion={"body": K.body(headLevel=0.0, twist=0.05, land=1.2, shift=0.0)},
              life=dict(STILL, temperament="beast", breathDepth=0.0, idles={"settle": 1}, bob=0.0, sway=0.2, mass=4.0, springHz=1.4),
              voice={"ambient": [("entity.minecart.riding", 0.5, 0.6), ("block.piston.contract", 0.4, 0.6)], "hurt": [("block.iron.hit", 1.0, 0.6), ("entity.iron_golem.hurt", 0.6, 0.6)],
                     "death": [("block.iron.break", 1.0, 0.5), ("block.beacon.deactivate", 1.0, 0.6)], "step": [("entity.minecart.inside", 0.4, 0.7)]}),
]
