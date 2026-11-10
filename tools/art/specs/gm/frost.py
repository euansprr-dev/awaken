"""The Frost Monastery's monsters (SPEC §60). Room: white snow and pale stone. So they are dark wool, dark wood and
clear ice, and each carries a pale blue light where its breath is."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_fur, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

WOOL = Ramp("#14121a", "#201c28", "#2e2838", "#40384c", "#564c64", "#72667e")
WOOD = Ramp("#140e0a", "#22180f", "#342416", "#483420", "#5e462c", "#785c3c")
ICE = Ramp("#3a5a72", "#527a94", "#70a0b8", "#94c2d6", "#bcdeea", "#e2f4fa")
HORN = Ramp("#2a1c0c", "#46300f", "#6a4a18", "#8f6a24", "#b88c38", "#dcb458")
FUR = Ramp("#2a2c34", "#3c3e48", "#52545e", "#6c6e78", "#8a8c94", "#aaacb2")
BREATH, HOT = "#a8e4ff", "#f0fbff"

QUAD = ("0_l", "0_r", "1_l", "1_r")
TROT = [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)]


def ice(P, cube, base=3.6, faces=K.ALL, clear=True, seed=0):
    """Clear ice: pale at its edges, and two pixels in three of the inside left out in slanting streaks, so what is
    frozen in it shows."""
    P.paint(cube, ICE, base, rim=0.9)
    if not clear:
        return
    for fname in faces:
        f = P.face(cube, fname)
        if f.w < 3 or f.h < 3:
            continue
        for i, j in f.each():
            if 0 < i < f.w - 1 and 0 < j < f.h - 1 and (i + j + seed) % 3 != 0:
                f.set(i, j, (0, 0, 0, 0))


def eyes(P, cube, fname, xs, y, a=255):
    for i in xs:
        K.lit(P, cube, fname, i, y, 1, 1, BREATH, HOT, a=a)


# ---------------------------------------------------------------------------------------------------------------------
# The Frozen Monk (rime_acolyte): a kneeling figure frozen in a block of ice that slides at you, one great palm out.
# The palm draws back before the shove.

def frozen_monk():
    r = Rig("rime_acolyte", (128, 64))
    r.shadow = 0.9
    body = r.bone("body", (0, 24, 0))             # its pivot is the floor: it slides on it
    body.cube("robem", -3, -9, -2, 6, 6, 5)
    body.cube("kneesm", -3, -3, -4, 6, 3, 7)
    body.cube("hoodm", -2, -13, -2, 4, 4, 4)
    body.bone("shell", (0, 0, 0)).cube("block", -5, -15, -5, 10, 15, 10)
    arm = body.bone("arm", (-2, -9, -3))          # its right arm, out through the ice
    arm.cube("sleevem", -1.5, -1.5, -5, 3, 3, 5)
    arm.bone("fore", (0, 0, -5), (-8, 0, 0)).cube("palm", -3, -5.5, -2, 6, 7, 2)
    r.pack()

    reach = lambda d, a=0.0, f=0.0: {"arm": {"pos": (0, 0, -d), "rot": (a, 0, 0)}, "fore": (f, 0, 0)}  # noqa: E731  (px further out of the ice)
    K.moves(r, palm="Frost Palm")
    K.act(r, "palm", K.plus(reach(-3.5, 0, -20), {"body": {"rot": (-8, 0, 0), "pos": (0, 0, 1)}}), K.plus(reach(4, 0, 14), {"body": {"rot": (9, 0, 0), "pos": (0, 0, -3)}}),
          w=0.9, length=0.5, t_hit=0.08, open_pose=K.plus(reach(4, 20, 40), {"body": {"rot": (6, 0, 0)}}))
    K.plain(r, K.plus(reach(-2), {"body": {"rot": (-12, 0, 0), "pos": (0, 0, 1)}}), K.plus(reach(2), {"body": {"rot": (14, 0, 0), "pos": (0, 0, -3)}}))
    K.loop(r, "idle", 7.0, lambda p: K.plus(reach(0.4 * s(p), 0, 4 * s(p * 2) + 14 * bump(p, 0.6, 0.04)), {"body": (0, 0, 0.6 * s(p))}), n=16)
    K.loop(r, "walk", 1.0, lambda p: K.plus(reach(0.5), {"body": (5 + 0.8 * s(p * 2), 0, 1.6 * s(p))}))
    K.loop(r, "run", 0.7, lambda p: K.plus(reach(2, 0, 8), {"body": (10 + 1.2 * s(p * 2), 0, 2 * s(p))}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(reach(1.5, 0, -10), {"body": (-3, 0, 2.5 * s(p * 2))}))
    K.flinch(r, K.plus(reach(-1.5), {"body": {"rot": (-7, 0, 5), "pos": (0, 0, 1.5)}}))
    gone = {"shell": {"scale": (0.02, 0.02, 0.02)}}
    K.keyed(r, "death", 1.2, [(0, {}), (0.12, {"body": (-6, 0, 3), "shell": {"scale": (1.06, 1.04, 1.06)}}), (0.3, K.plus(gone, reach(2, 30, 30), {"body": (10, 0, 0)})),
                              (0.8, K.on_floor(r, K.plus(gone, reach(2, -76, 0), {"body": (80, 0, 8)}))), (0.92, K.on_floor(r, K.plus(gone, reach(2, -76, 0), {"body": (75, 0, 8)}))),
                              (1.2, K.on_floor(r, K.plus(gone, reach(2, -76, 0), {"body": (80, 0, 8)})))])     # face down, its arm out along the floor ahead of it
    return r


def paint_frozen_monk(r, P):
    ice(P, "block", 3.8)
    f = P.face("block", "top")
    for i, j in f.each():
        f.set(i, j, ICE.at(4.6 + 0.4 * ((i + j) % 2)))   # snow lying on it
    P.paint("robem", WOOL, 2.4, p_strokes(2, 4, seed=1), rim=0.4)
    P.paint("kneesm", WOOL, 2.0, rim=0.4)
    P.paint("hoodm", WOOL, 1.4, rim=0.5)
    eyes(P, "hoodm", "north", (0, 3), 2)
    P.paint("sleevem", WOOL, 2.8, p_bands(2, 0.6), rim=0.5)
    P.paint("palm", ICE, 0.6, rim=0.8)
    f, g = P.face("palm", "north"), P.face("palm", "north", glow=True)   # frost on the palm: a ring of it, lit
    for i, j in f.each():
        d = math.hypot(i - 2.5, j - 3)
        if 1.4 < d < 2.4:
            f.set(i, j, hexc(BREATH))
            g.set(i, j, hexc(BREATH, 210))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Hail Slinger (hail_stray): a small thing with one arm twice as long as itself. The long arm whips right round
# behind it before it throws.

def hail_slinger():
    r = Rig("hail_stray", (64, 64))
    r.shadow = 0.5
    body = K.at(r.bone("body", (0, 16, 0)), 16)
    body.cube("bundle", -3, -5, -2.5, 6, 6, 5)
    K.legs2(body, "1", (2, 1, 0), 4, 5, 30, thick=(2, 1), foot=(3, 2, 3, -1.5))
    head = body.bone("head", (0, -5, -0.5))
    head.cube("hoods", -2.5, -4, -2.5, 5, 4, 5)
    head.cube("tips", -1, -6, 0, 2, 2, 2)
    body.bone("arm_l", (3, -4, 0), (10, 0, -8)).cube("stub", -0.5, 0, -0.5, 1, 4, 1)
    arm = body.bone("arm_r", (-3.5, -4, 0), (40, 0, 6))
    arm.cube("longu", -1, 0, -1, 2, 9, 2)
    fore = arm.bone("fore_r", (0, 9, 0), (36, 0, 0))
    fore.cube("longf", -0.5, 0, -0.5, 1, 9, 1)
    sl = fore.bone("sling", (0, 9, 0), (-76, 0, 0))
    sl.cube("pouch", -1.5, 0, -1.5, 3, 2, 3)
    sl.cube("hail", -1, -1, -1, 2, 2, 2)
    r.look = [("head", 0.6, 0.4)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"fore_r": 0.05, "sling": 0.1, "head": 0.04}
    K.moves(r, volley="Hail Volley")
    K.act(r, "volley", {"arm_r": (130, 0, 0), "fore_r": (-30, 0, 0), "sling": (20, 0, 0), "body": {"rot": (-6, 22, 0), "pos": (0, 0, 1)}, "head": (0, -20, 0)},
          {"arm_r": (-140, 0, 0), "fore_r": (-34, 0, 0), "sling": (60, 0, 0), "body": {"rot": (12, -26, 0), "pos": (0, -0.5, -2)}, "head": (0, 24, 0)},
          w=0.9, length=0.5, t_hit=0.08, plant=legs, lag=lagc, counter=0.25,
          open_pose={"arm_r": (-64, 0, 0), "fore_r": (10, 0, 0), "body": {"rot": (14, -10, 0)}, "head": (20, 0, 0)})
    K.plain(r, {"arm_r": (-30, 0, 40), "fore_r": (-30, 0, 0), "body": (0, 30, 0)}, {"arm_r": (-80, 0, -30), "fore_r": (-40, 0, 0), "body": {"rot": (6, -34, 0), "pos": (0, 0, -1.5)}},
            plant=legs, lag=lagc)
    K.loop(r, "idle", 5.0, lambda p: {"head": (3 * s(p, 0.2), 20 * (bump(p, 0.3, 0.06) - bump(p, 0.75, 0.06)), 0), "arm_r": (2 * s(p), 0, 0), "fore_r": (3 * s(p, 0.3), 0, 0),
                                      "sling": (10 * bump(p, 0.5, 0.04), 0, 0), "arm_l": (6 * s(p, 0.5), 0, 0), "body": {"pos": (0, 0.3 * s(p * 2), 0)}}, n=16)
    K.loop(r, "walk", 0.8, lambda p: {"body": (2 * s(p * 2), 0, 3 * s(p)), "head": (-2 * s(p * 2, 0.1), 0, -2 * s(p)), "arm_r": (4 * s(p * 2, 0.3), 0, 0), "fore_r": (5 * s(p * 2, 0.5), 0, 0),
                                      "arm_l": (20 * s(p), 0, 0)})
    K.loop(r, "run", 0.5, lambda p: {"body": (10 + 3 * s(p * 2), 0, 4 * s(p)), "head": (-8, 0, 0), "arm_r": (14 + 5 * s(p * 2, 0.3), 0, 0), "fore_r": (-10 + 6 * s(p * 2, 0.5), 0, 0),
                                     "arm_l": (30 * s(p), 0, 0)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 0.9, lambda p: {"arm_r": (60 + 20 * s(p), 0, 0), "fore_r": (-30, 0, 0), "head": (8, 0, 4 * s(p)), "body": {"pos": (0, -0.6, 0)}}, plant=legs)
    K.flinch(r, {"body": {"rot": (-10, 0, 6), "pos": (0, 0, 1.5)}, "head": (-14, 0, 0), "arm_r": (-20, 0, 10)}, plant=legs)
    start = K.planted(r, {"body": {"rot": (-10, 0, 0), "pos": (0, 0.5, 0)}, "arm_r": (30, 0, 0)}, legs)
    down = {"body": (80, 0, 8), "head": (10, 30, 0), "arm_r": (-150, 0, 20), "fore_r": (-30, 0, 0), "arm_l": (-60, 0, -30), "leg1_l": (-50, 0, 0), "leg1_r": (-60, 0, 0),
            "shin1_l": (60, 0, 0), "shin1_r": (70, 0, 0)}
    skip = ("pouch", "hail", "longf")
    K.keyed(r, "death", 1.1, [(0, {}), (0.14, start)] + K.fall(r, 0.14, start, 0.7, down, steps=4, skip=skip)
            + [(0.82, K.on_floor(r, K.plus(down, {"body": (-6, 0, 0)}), skip=skip)), (1.1, K.on_floor(r, down, skip=skip))], lag=lagc)
    return r


def paint_hail_slinger(r, P):
    P.paint("bundle", WOOL, 2.6, p_strokes(2, 3, seed=3), rim=0.5)
    f = P.face("bundle", "north")
    for i in range(f.w):
        f.set(i, 3, WOOD.at(4))   # a rope round its middle
    P.paint("hoods", WOOL, 1.8, rim=0.6)
    K.void(P, "hoods", "north", margin=1, top=1)
    eyes(P, "hoods", "north", (1, 3), 2)
    P.paint("tips", WOOL, 2.8)
    P.paint("stub", WOOL, 3.0)
    P.paint("longu", WOOL, 3.0, p_bands(3, 0.6))
    P.paint("longf", WOOD, 3.4, p_bands(3, 0.6))
    P.paint("pouch", WOOD, 2.6, rim=0.6)
    K.lit_cube(P, "hail", BREATH, HOT, a=220, hot=lambda fn, i, j, f: (i + j) % 2 == 0)
    P.paint("thigh1_l", WOOL, 2.4)
    P.paint("shinb1_l", WOOL, 3.2)
    P.paint("footb1_l", WOOD, 2.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Snow Star (frost_wisp): a six-pointed crystal that turns in the air. Its points fly off as bolts and grow back:
# count them.

def snow_star():
    r = Rig("frost_wisp", (64, 32))
    r.shadow = 0.3
    b = r.bone("body", (0, 12, 0))
    sp = b.bone("spin", (0, 0, 0))
    sp.cube("knot", -1.5, -1.5, -1, 3, 3, 2)
    for k in range(6):
        sk = sp.bone(f"spike{k}", (0, 0, 0), (0, 0, 60 * k))
        sk.cube(f"ray{k}", -0.5, -8, -0.5, 1, 7, 1, share=None if k == 0 else "ray0")
        sk.cube(f"barb{k}a", 0.5, -6, -0.5, 2, 1, 1, share=None if k == 0 else "barb0a")
        sk.cube(f"barb{k}b", -2.5, -6, -0.5, 2, 1, 1, share="barb0a")
    r.pack()

    def out(ks, d, k_scale=1.0):
        """Those points moved d px out along their own lines (pose space: y up), and scaled."""
        q = {}
        for k in ks:
            a = math.radians(60 * k)
            q[f"spike{k}"] = {"pos": (d * math.sin(a), d * math.cos(a), 0), "scale": (k_scale, k_scale, k_scale)}
        return q
    every = range(6)
    K.moves(r, bolt="Blizzard Bolt", gale="Gale")
    K.act(r, "bolt", K.plus(out((0, 3), 2.5), {"spin": (0, 0, -30), "body": {"rot": (-14, 0, 0), "pos": (0, 0.8, 1)}}),
          K.plus(out((0, 3), 7, 0.05), {"spin": (0, 0, 10), "body": {"rot": (10, 0, 0), "pos": (0, 0, 2)}}), w=0.8, length=0.45, t_hit=0.07,
          open_pose=K.plus(out((0, 3), 0, 0.05), {"body": {"rot": (20, 0, 8), "pos": (0, -1.5, 0)}}))
    K.act(r, "gale", K.plus(out(every, -1.5), {"spin": (0, 0, -120), "body": {"pos": (0, 1, 1)}}), K.plus(out(every, 2.0), {"spin": (0, 0, 240), "body": {"pos": (0, 0, -2)}}),
          w=0.9, length=0.6, t_hit=0.12, rest={"spin": (0, 0, 360)})
    K.plain(r, {"spin": (0, 0, -60), "body": {"rot": (0, -40, 0), "pos": (0, 0.5, 1)}}, {"spin": (0, 0, 120), "body": {"rot": (0, 50, 0), "pos": (0, 0, -3)}})
    K.spin_loop(r, "idle", 4.0, lambda p: {"body": {"pos": (0, 0.9 * s(p * 2), 0), "rot": (2 * s(p), 6 * s(p, 0.3), 0)}}, "spin", 2, 60, n=12)
    K.spin_loop(r, "fly", 1.5, lambda p: {"body": (10, 0, 2 * s(p))}, "spin", 2, 60)
    K.spin_loop(r, "angry", 0.6, lambda p: K.plus(out(every, 0.8 + 0.6 * s(p * 2)), {"body": (-6, 0, 0)}), "spin", 2, 60)
    K.flinch(r, K.plus(out(every, -1.2), {"spin": (0, 0, -40), "body": {"rot": (-14, 20, 0), "pos": (0, 0, 2)}}))
    fallen = {}
    for k in every:
        a = math.radians(60 * k)
        fallen[f"spike{k}"] = {"pos": (5 * math.sin(a), -9 + 0.5 * k, 2 * math.cos(a * 2)), "rot": (80, 0, 30 * k), "scale": (0.7, 0.7, 0.7)}
    K.keyed(r, "death", 1.0, [(0, {}), (0.1, out(every, 1.5)), (0.5, K.plus({k_: {"pos": tuple(v * 0.6 for v in q["pos"]), "rot": tuple(v * 0.5 for v in q["rot"])} for k_, q in fallen.items()},
                                                                         {"body": {"pos": (0, -5, 0)}})),
                              (0.8, K.plus(fallen, {"body": {"pos": (0, -10, 0), "rot": (80, 0, 0)}})), (1.0, K.plus(fallen, {"body": {"pos": (0, -10.5, 0), "rot": (84, 0, 0)}}))])
    return r


def paint_snow_star(r, P):
    P.paint("knot", WOOD, 2.6, rim=0.6)
    for fname in ("north", "south"):
        K.lit(P, "knot", fname, 1, 1, 1, 1, BREATH, HOT, a=250)
    P.paint("ray0", ICE, 3.6, rim=0.6)
    for fname in K.SIDES:
        f, g = P.face("ray0", fname), P.face("ray0", fname, glow=True)
        f.set(0, 0, hexc(BREATH))
        g.set(0, 0, hexc(BREATH, 230))
        f.set(0, f.h - 1, ICE.at(1.0))
    P.paint("barb0a", ICE, 4.4, rim=0.5)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Ice Hound (ice_hound): a lean hound of clear ice, its spine showing through. It cannot stop on its own ice: a
# pounce that misses skids on past you.

def ice_hound():
    r = Rig("ice_hound", (64, 64))
    r.shadow = 0.6
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    body.cube("spinei", -0.5, -2.5, -6, 1, 1, 12)
    for k in range(4):
        body.cube(f"ribi{k}", -1.5, -1.5, -4.5 + 3 * k, 3, 3, 1, share=None if k == 0 else "ribi0")
    body.cube("torsoi", -2.5, -3.5, -6.5, 5, 6, 13)
    K.legs2(body, "0", (2.5, 2, -5), 5, 7, 40, thick=(2, 1))     # forelegs: knees ahead
    K.legs2(body, "1", (2.5, 1, 5), 8, 5, -24, thick=(2, 1))     # hind legs: hocks behind
    neck = body.bone("neck", (0, -2, -6), (34, 0, 0))
    neck.cube("necki", -1, -4, -1, 2, 4, 2)
    head = neck.bone("head", (0, -4, 0), (-24, 0, 0))
    head.cube("skulli", -2, -2, -3, 4, 3, 4)
    head.cube("muzzlei", -1, -1, -7, 2, 2, 4)
    head.bone("jaw", (0, 1, -3)).cube("jawi", -1, 0, -4, 2, 1, 4)
    for side, nm in SIDES2:
        head.bone("ear_" + nm, (side * 1.5, -2, 0), (-30, 0, side * 16)).cube("eari_" + nm, -0.5, -4, 0, 1, 4, 1, share=None if side > 0 else "eari_l")
    tail = body.bone("tail", (0, -2, 6), (-20, 0, 0))
    tail.cube("taili0", -0.5, 0, 0, 1, 1, 6)
    tail.bone("tail_end", (0, 0, 6), (-16, 0, 0)).cube("taili1", -0.5, 0, 0, 1, 1, 5)
    r.look = [("neck", 0.35, 0.2), ("head", 0.45, 0.3)]
    r.pack()

    lagc = {"tail": 0.06, "tail_end": 0.12, "ear_l": 0.05, "ear_r": 0.05}
    K.moves(r, pounce="Ice Pounce")
    skid = K.on_floor(r, {"body": {"rot": (4, 34, 10)}, "neck": (24, -20, 0), "head": (6, 0, 0), "jaw": (20, 0, 0), "leg0_l": (-70, 0, -10), "leg0_r": (-50, 0, 20), "shin0_l": (-40, 0, 0),
                          "shin0_r": (-40, 0, 0), "leg1_l": (70, 0, -10), "leg1_r": (50, 0, 14), "shin1_l": (30, 0, 0), "shin1_r": (30, 0, 0), "tail": (30, 20, 0)})
    K.act(r, "pounce", {"body": {"pos": (0, -3, 2), "rot": (-8, 0, 0)}, "neck": (-16, 0, 0), "head": (10, 0, 0), "tail": (30, 0, 0), "ear_l": (30, 0, 0), "ear_r": (30, 0, 0)},
          {"body": {"pos": (0, 2.5, -5), "rot": (-16, 0, 0)}, "neck": (10, 0, 0), "head": (-10, 0, 0), "jaw": (30, 0, 0), "leg0_l": (-60, 0, 0), "leg0_r": (-60, 0, 0),
           "leg1_l": (50, 0, 0), "leg1_r": (50, 0, 0)}, w=0.8, length=0.45, t_hit=0.08, plant=QUAD, lag=lagc, stuck=skid)
    K.plain(r, {"neck": (-18, 0, 0), "head": (6, 0, 0), "jaw": (26, 0, 0), "body": {"pos": (0, -0.6, 1.2)}}, {"neck": (16, 0, 0), "head": (-6, 0, 0), "jaw": (-2, 0, 0), "body": {"pos": (0, 0, -2.6)}},
            second=K.other_side({"neck": (-18, 0, 0), "head": (6, 0, 0), "jaw": (26, 0, 0), "body": {"pos": (0, -0.6, 1.2)}},
                                {"neck": (16, 0, 0), "head": (-6, 0, 0), "jaw": (-2, 0, 0), "body": {"pos": (0, 0, -2.6)}}),
            plant=QUAD, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: {"neck": (2 * s(p), 6 * s(p, 0.3), 0), "head": (0, 0, 8 * bump(p, 0.6, 0.04)), "ear_l": (14 * bump(p, 0.3, 0.02), 0, 0),
                                      "ear_r": (14 * bump(p, 0.75, 0.02), 0, 0), "tail": (0, 10 * s(p * 2), 0), "jaw": (5 * bump(p, 0.45, 0.05), 0, 0)}, n=16)
    K.loop(r, "walk", 0.7, lambda p: {"neck": (4 * s(p * 2, 0.1), 0, 2 * s(p)), "head": (-3 * s(p * 2, 0.2), 0, 0), "tail": (0, 14 * s(p), 0), "body": (1.2 * s(p * 2), 0, 2 * s(p))})
    K.loop(r, "run", 0.42, lambda p: {"neck": (14 + 6 * s(p * 2, 0.1), 0, 0), "head": (-10, 0, 0), "tail": (14, 8 * s(p), 0), "body": (5 * s(p * 2, 0.25), 0, 0), "ear_l": (30, 0, 0), "ear_r": (30, 0, 0)})
    K.fallback_walk(r, TROT)
    K.loop(r, "angry", 1.2, lambda p: {"neck": (14, 0, 0), "head": (-6, 0, 0), "jaw": (10 + 6 * s(p * 4), 0, 0), "body": {"pos": (0, -1.2, 0)}, "tail": (20, 0, 0)}, plant=QUAD)
    K.flinch(r, {"neck": (-20, 0, 12), "body": {"pos": (0, 0.5, 1.5), "rot": (0, 0, 6)}, "tail": (-20, 0, 0)}, plant=QUAD)
    side = {"body": (0, 0, 84), "neck": (20, 0, 0), "head": (10, 20, 0), "leg0_l": (30, 0, 0), "leg0_r": (8, 0, 0), "leg1_l": (-30, 0, 0), "leg1_r": (-8, 0, 0), "tail": (0, 0, 30), "jaw": (20, 0, 0)}
    start = K.planted(r, {"neck": (-30, 0, 0), "body": {"pos": (0, 1, 1)}}, QUAD)
    K.keyed(r, "death", 1.0, [(0, {}), (0.12, start)] + K.fall(r, 0.12, start, 0.6, side, steps=3) + [(0.72, K.on_floor(r, K.plus(side, {"body": (0, 0, -6)}))), (1.0, K.on_floor(r, side))], lag=lagc)
    return r


def paint_ice_hound(r, P):
    P.paint("spinei", WOOD, 1.0)
    P.paint("ribi0", WOOD, 1.6)
    for fname in ("north", "south"):
        f = P.face("ribi0", fname)
        f.set(1, 1, (0, 0, 0, 0))
        f.set(1, 2, (0, 0, 0, 0))
    ice(P, "torsoi", 3.6)
    for nm in ("necki", "skulli", "muzzlei", "taili0", "taili1", "eari_l"):
        ice(P, nm, 3.8, clear=False)
    P.paint("jawi", ICE, 2.4)
    for fname in ("west", "east"):
        eyes(P, "skulli", fname, (0,), 1)
    f, g = P.face("muzzlei", "north"), P.face("muzzlei", "north", glow=True)   # its breath
    f.set(0, 1, hexc(BREATH))
    g.set(0, 1, hexc(BREATH, 200))
    for tag in ("0", "1"):
        ice(P, f"thigh{tag}_l", 3.4, clear=False)
        ice(P, f"shinb{tag}_l", 4.2, clear=False)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Long Horn (chant_master, the elite): a temple horn longer than its bearer is tall, dragged, then raised. The horn
# comes up to sound. While it is up, the bearer under it has no guard.

def long_horn():
    r = Rig("chant_master", (128, 64))
    r.shadow = 0.8
    body = K.at(r.bone("body", (0, 14, 0)), 14)
    body.cube("skirth", -3.5, -1, -3, 7, 8, 6)
    K.legs2(body, "1", (2, 2, 0), 4, 5, 26, thick=(2, 2), foot=(3, 2, 4, -2))
    chest = body.bone("chest", (0, -1, 0), (8, 0, 0))
    chest.cube("torsoh", -3.5, -7, -2.5, 7, 7, 5)
    head = chest.bone("head", (0, -7, -0.5))
    head.cube("hoodh", -2.5, -5, -2.5, 5, 5, 5)
    head.cube("peakh", -1.5, -7, -0.5, 3, 2, 3)
    for side, nm in SIDES2:
        chest.bone("arm_" + nm, (side * 3.5, -6, -1), (-64, 0, side * 12)).cube("sleeveh_" + nm, -1, 0, -1, 2, 6, 2, share=None if side > 0 else "sleeveh_l")
    horn = chest.bone("horn", (0, -5, -3), (20, 0, 0))
    horn.cube("pipe0", -1, -1, -9, 2, 2, 9)
    horn.cube("pipe1", -1.5, -1.5, -19, 3, 3, 10)
    horn.cube("bellh", -2.5, -2.5, -25, 5, 5, 6)
    horn.cube("rimh", -3.5, -3.5, -27, 7, 7, 2)
    for k in range(2):
        horn.bone(f"tassel{k}", (0, 1.5, -12 - 8 * k)).cube(f"ribbon{k}", 0, 0, -1, 0, 5, 2, share=None if k == 0 else "ribbon0")
    r.look = [("head", 0.4, 0.3)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"horn": 0.05, "head": 0.04, "tassel0": 0.12, "tassel1": 0.14}
    up = lambda a, lean=0.0: {"horn": (-a, 0, 0), "arm_l": (-a * 0.7, 0, 0), "arm_r": (-a * 0.7, 0, 0), "chest": (-lean, 0, 0), "head": (-lean * 0.8, 0, 0)}  # noqa: E731
    K.moves(r, chant="Chant", nova="Frost Nova", rain="Ice Spikes")
    K.act(r, "chant", K.plus(up(64, 10), {"body": {"pos": (0, 0.6, 0.5)}}), K.plus(up(74, 14), {"body": {"pos": (0, 0, 1.5)}}), w=1.2, length=0.6, t_hit=0.14, plant=legs, lag=lagc)
    bowed = K.planted(r, K.plus(up(15, -14), {"body": {"pos": (0, -0.6, -1)}}), legs)
    K.act(r, "nova", K.plus(up(84, 14), {"body": {"pos": (0, 1, 0.5)}}), bowed, w=1.0, length=0.55, t_hit=0.1, over=0.02, plant=legs, lag=lagc, open_pose=K.plus(bowed, {"head": (10, 0, 0)}))
    K.act(r, "rain", K.plus(up(108, 20), {"body": {"pos": (0, 0.6, 1)}}), K.plus(up(118, 24), {"body": {"pos": (0, 0, 2)}}), w=1.1, length=0.6, t_hit=0.14, plant=legs, lag=lagc)
    K.plain(r, K.plus(up(14), {"chest": (0, 36, 0)}), K.plus(up(10), {"chest": (4, -40, 0), "body": {"pos": (0, 0, -1.5)}}), plant=legs, lag=lagc)
    K.loop(r, "idle", 7.0, lambda p: K.plus(up(1.5 * s(p) + 5 * bump(p, 0.6, 0.06), 1.5 * s(p)), {"head": (0, 12 * s(p, 0.3), 0), "body": {"pos": (0, 0.3 * s(p * 2), 0)}}), n=16)
    K.loop(r, "walk", 1.0, lambda p: K.plus(up(2 + 2 * s(p * 2)), {"chest": (4 + 1.5 * s(p * 2), 3 * s(p), 2 * s(p)), "head": (-3 * s(p * 2, 0.1), 0, 0)}))
    K.loop(r, "run", 0.62, lambda p: K.plus(up(18 + 3 * s(p * 2)), {"chest": (10 + 2 * s(p * 2), 4 * s(p), 3 * s(p)), "head": (-8, 0, 0)}))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.4, lambda p: K.plus(up(30 + 8 * s(p * 2), 6), {"body": {"pos": (0, -0.6, 0)}}), plant=legs)
    K.flinch(r, K.plus(up(12, 8), {"chest": (0, 10, 5), "body": {"pos": (0, -0.5, 1.5)}}), plant=legs)
    knees = K.planted(r, K.plus(up(-6, -14), {"body": {"pos": (0, -1.6, 0)}}), legs)
    flat = {"body": (78, 0, 10), "chest": (4, 0, 0), "head": (10, 30, 0), "horn": (-96, 0, 30), "arm_l": (-30, 0, -40), "arm_r": (-30, 0, 40), "leg1_l": (-50, 0, 0), "leg1_r": (-60, 0, 0),
            "shin1_l": (60, 0, 0), "shin1_r": (74, 0, 0)}
    skip = ("ribbon0", "ribbon1")
    K.keyed(r, "death", 1.4, [(0, {}), (0.18, K.planted(r, up(30, 12), legs)), (0.6, knees)] + K.fall(r, 0.6, knees, 1.05, flat, steps=5, skip=skip)
            + [(1.16, K.on_floor(r, K.plus(flat, {"body": (-5, 0, 0)}), skip=skip)), (1.4, K.on_floor(r, flat, skip=skip))], lag=lagc)
    return r


def paint_long_horn(r, P):
    P.paint("skirth", WOOL, 2.2, p_strokes(3, 5, seed=4), rim=0.4, ao=0.5)
    P.paint("torsoh", WOOL, 2.8, p_strokes(3, 4, seed=5), rim=0.5)
    f = P.face("torsoh", "north")
    for j in range(f.h):
        f.set(f.w - 2 - j if 0 <= f.w - 2 - j < f.w else 0, j, HORN.at(3.2))   # a sash across it
    P.paint("hoodh", WOOL, 1.8, rim=0.6)
    K.void(P, "hoodh", "north", margin=1, top=1)
    eyes(P, "hoodh", "north", (1, 3), 2)
    P.paint("peakh", WOOL, 2.6)
    P.paint("sleeveh_l", WOOL, 3.0, p_bands(3, 0.5))
    P.paint("pipe0", HORN, 2.6, p_bands(3, 0.8, vertical=True))
    P.paint("pipe1", HORN, 3.0, p_bands(4, 0.8, vertical=True), rim=0.6)
    P.paint("bellh", HORN, 3.4, rim=0.9)
    P.paint("rimh", HORN, 4.2, rim=1.0)
    K.void(P, "rimh", "north", margin=1, top=1)
    fr, g = P.face("rimh", "north"), P.face("rimh", "north", glow=True)
    for i in range(2, fr.w - 2):
        for j in range(2, fr.h - 2):
            fr.set(i, j, hexc(BREATH))
            g.set(i, j, hexc(BREATH, 160))     # the cold in its throat
    for fname in ("west", "east"):
        ff = P.face("ribbon0", fname)
        for i, j in ff.each():
            ff.set(i, j, WOOL.at(4.6 - 0.4 * j) if not (j == ff.h - 1 and i == 0) else (0, 0, 0, 0))
    P.paint("thigh1_l", WOOL, 2.2)
    P.paint("shinb1_l", WOOL, 2.8)
    P.paint("footb1_l", WOOD, 2.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Yeti (the_yeti, the Warden): huge in front and small behind, its fur full of icicles and frozen prayer flags. It
# rises on its hind legs before the slam. Its breath slows you; its back is slow to follow.

def yeti():
    r = Rig("the_yeti", (128, 128))
    r.shadow = 1.6
    body = K.at(r.bone("body", (0, 13, 5)), 13)
    body.cube("rump", -4.5, -4, -4, 9, 8, 8)
    for side, nm in SIDES2:
        K.bent_leg(body, "1_" + nm, (3.5, 1, 1), 6, 7, 44, thick=(4, 3), side=side, foot=(4, 3, 5, -2.5), share=None if side > 0 else ("thigh1_l", "shinb1_l", "footb1_l"))
    chest = body.bone("chest", (0, -2, -2), (40, 0, 0))
    chest.cube("barrel", -7, -13, -5, 14, 13, 10)
    chest.cube("hump", -5, -15, -3, 10, 2, 8)
    shoulder_y = 11 - 9 * math.cos(40 * K.DEG)
    for side, nm in SIDES2:   # the arms it walks on: shoulders high, elbows back, fists down
        K.bent_leg(chest, "0_" + nm, (8, -9, 0), 10, 12, -27, thick=(5, 4), side=side, world_y=shoulder_y, tilt=40, splay=6, foot=(6, 4, 6, -3),
                   share=None if side > 0 else ("thigh0_l", "shinb0_l", "footb0_l"))
    head = chest.bone("head", (0, -12, -4), (-34, 0, 0))
    head.cube("skully", -3.5, -4, -6, 7, 6, 6)
    head.cube("browy", -3.5, -5, -6.5, 7, 2, 3)
    jaw = head.bone("jaw", (0, 2, -1))
    jaw.cube("jawy", -3, 0, -5, 6, 2, 5)
    for k in range(3):
        jaw.bone(f"drip{k}", (-2 + 2 * k, 2, -4)).cube(f"icicle{k}", -0.5, 0, -0.5, 1, 3 + (k % 2), 1, share=None if k == 0 else ("icicle0" if k == 2 else None))
    for k in range(4):   # prayer flags on a cord across its back, frozen stiff
        chest.bone(f"tassel{k}", (-4.5 + 3 * k, -15, 5)).cube(f"flag{k}", -1, 0, 0, 2, 4, 0)
    r.look = [("head", 0.4, 0.3)]
    r.pack()

    FRONT, HIND = ("0_l", "0_r"), ("1_l", "1_r")
    lagc = {"head": 0.05, "jaw": 0.08, **{f"tassel{k}": 0.12 for k in range(4)}}
    K.moves(r, slam="Avalanche Slam", charge="Snow Charge", breath="Frost Breath")
    landed = K.planted(r, {"body": {"rot": (8, 0, 0), "pos": (0, -2, -2)}, "head": (10, 0, 0), "jaw": (16, 0, 0)}, QUAD)
    K.act(r, "slam", {"body": {"rot": (-36, 0, 0), "pos": (0, 1.5, 2)}, "leg0_l": (-116, 0, -10), "leg0_r": (-104, 0, 10), "shin0_l": (24, 0, 0), "shin0_r": (20, 0, 0), "head": (-14, 0, 0), "jaw": (20, 0, 0)},
          landed, w=1.3, length=0.6, t_hit=0.1, over=0.03, counter=0.06, plant=HIND, lag=lagc, open_pose=K.planted(r, {"body": {"pos": (0, -2.5, -1), "rot": (6, 0, 0)}, "head": (30, 0, 0), "jaw": (20, 0, 0)}, QUAD))
    sprawl = K.planted(r, {"body": {"pos": (0, -3.5, -2), "rot": (12, 0, 0)}, "head": (32, 0, 0), "jaw": (14, 0, 0)}, QUAD)
    K.act(r, "charge", K.planted(r, {"body": {"pos": (0, -2, 3)}, "head": (16, 0, 0)}, QUAD),
          {"body": {"pos": (0, 0.5, -5), "rot": (6, 0, 0)}, "leg0_l": (-56, 0, 0), "leg0_r": (-44, 0, 0), "head": (-10, 0, 0), "jaw": (26, 0, 0)}, w=1.0, length=0.5, t_hit=0.08,
          plant=HIND, lag=lagc, stuck=sprawl)
    K.act(r, "breath", K.planted(r, {"body": {"pos": (0, 1, 1.5)}, "chest": {"scale": (1.08, 1.06, 1.08)}, "head": (-36, 0, 0), "jaw": (6, 0, 0)}, QUAD),
          K.planted(r, {"body": {"pos": (0, -1, -2)}, "head": (20, 0, 0), "jaw": (36, 0, 0)}, QUAD), w=1.0, length=0.6, t_hit=0.1, plant=HIND, lag=lagc,
          open_pose=K.planted(r, {"body": {"pos": (0, -1.5, -1)}, "head": (36, 0, 0), "jaw": (24, 0, 0)}, QUAD))
    K.plain(r, {"leg0_l": (-74, 0, -24), "shin0_l": (30, 0, 0), "body": {"rot": (-6, 10, 0), "pos": (0, 0.5, 1)}, "head": (-8, 0, 0)}, {"body": {"rot": (6, -12, 0), "pos": (0, -1, -2)}, "head": (8, 0, 0), "jaw": (14, 0, 0)},
            heavy=({"body": {"rot": (-24, 0, 0), "pos": (0, 1, 1.5)}, "leg0_l": (-100, 0, -10), "leg0_r": (-96, 0, 10), "head": (-10, 0, 0)}, landed), plant=QUAD, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: {"chest": {"scale": (1 + 0.02 * s(p), 1 + 0.015 * s(p), 1 + 0.02 * s(p))}, "head": (3 * s(p, 0.2), 10 * s(p, 0.45), 0), "jaw": (5 + 5 * s(p, 0.1), 0, 0)}, n=14)
    K.loop(r, "walk", 1.2, lambda p: {"body": (1.2 * s(p * 2), 0, 2 * s(p)), "chest": (0, 3 * s(p), 2 * s(p, 0.1)), "head": (3 * s(p * 2, 0.15), -3 * s(p), 0), "jaw": (4, 0, 0)})
    K.loop(r, "run", 0.75, lambda p: {"body": (3 * s(p * 2, 0.25), 0, 2.5 * s(p)), "chest": (4, 4 * s(p), 3 * s(p, 0.1)), "head": (-8 + 3 * s(p * 2, 0.15), 0, 0), "jaw": (14, 0, 0)})
    K.fallback_walk(r, TROT, swing=(16, 24))
    K.loop(r, "angry", 1.8, lambda p: {"body": {"rot": (-12, 0, 0), "pos": (0, 0.5, 1)}, "head": (-10, 0, 5 * s(p * 2)), "jaw": (26 + 8 * s(p * 3), 0, 0), "chest": {"scale": (1.05, 1.04, 1.05)}}, plant=QUAD)
    K.flinch(r, {"body": {"rot": (-4, 0, 4), "pos": (0, -0.5, 1.5)}, "head": (-12, 0, 8), "jaw": (10, 0, 0)}, plant=QUAD)
    chestd = K.planted(r, {"body": {"pos": (0, -3.5, -1), "rot": (10, 0, 0)}, "head": (30, 0, 0), "jaw": (20, 0, 0)}, QUAD)
    side = {"body": (4, 0, 80), "chest": (-20, 0, 0), "head": (10, 20, 0), "jaw": (24, 0, 0), "leg0_l": (20, 0, 0), "leg0_r": (-10, 0, 0), "leg1_l": (-30, 0, 0), "leg1_r": (-10, 0, 0)}
    skip = tuple(f"flag{k}" for k in range(4)) + tuple(f"icicle{k}" for k in range(3))
    K.keyed(r, "death", 2.0, [(0, {}), (0.25, K.planted(r, {"body": {"rot": (-14, 0, 0), "pos": (0, 1, 1)}, "head": (-20, 0, 0), "jaw": (30, 0, 0)}, HIND)), (0.8, chestd)]
            + K.fall(r, 0.8, chestd, 1.45, side, steps=5, skip=skip) + [(1.6, K.on_floor(r, K.plus(side, {"body": (0, 0, -5)}), skip=skip)), (2.0, K.on_floor(r, side, skip=skip))], lag=lagc)
    return r


def paint_yeti(r, P):
    P.paint("rump", FUR, 2.2, p_fur(1.0, 3, seed=2, length=4), rim=0.4, ao=0.6)
    P.paint("barrel", FUR, 2.6, p_fur(1.0, 3, seed=3, length=5), rim=0.5, ao=0.6)
    P.paint("hump", FUR, 3.4, p_fur(0.8, 2, seed=4, length=3), rim=0.6)
    for nm in ("barrel", "rump"):   # ice hanging in its fur
        for fname in ("west", "east", "south"):
            f = P.face(nm, fname)
            for i in range(1, f.w - 1, 3):
                for j in range(f.h - 1 - (K._h(nm, fname, i) % 4), f.h):
                    f.set(i, j, ICE.at(4.6))
    P.paint("skully", FUR, 1.6, rim=0.5)
    P.paint("browy", FUR, 2.8, rim=0.8)
    eyes(P, "skully", "north", (1, 5), 2)
    P.paint("jawy", FUR, 1.2)
    f, g = P.face("jawy", "top"), P.face("jawy", "top", glow=True)   # the cold in its mouth
    for i, j in f.each():
        if 0 < i < f.w - 1 and j < f.h - 1:
            f.set(i, j, hexc(BREATH))
            g.set(i, j, hexc(BREATH, 150))
    for k in (0, 1):
        ice(P, f"icicle{k}", 4.4, clear=False)
    flags = ("#7a2a2a", "#2a4a7a", "#8a7a2a", "#2a6a4a")
    for k in range(4):
        for fname in ("north", "south"):
            ff = P.face(f"flag{k}", fname)
            for i, j in ff.each():
                ff.set(i, j, hexc(flags[k]) if j < ff.h - 1 or i == 0 else (0, 0, 0, 0))
                if j == 0:
                    ff.set(i, j, ICE.at(4.8))
    for tag, base in (("0", 2.4), ("1", 2.0)):
        P.paint(f"thigh{tag}_l", FUR, base, p_fur(1.0, 3, seed=5, length=4), rim=0.4)
        P.paint(f"shinb{tag}_l", FUR, base + 0.5, p_fur(1.0, 3, seed=6, length=4), rim=0.4)
        P.paint(f"footb{tag}_l", FUR, 0.8, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("rime_acolyte", "The Frozen Monk", frozen_monk, paint_frozen_monk, kind="slider", box=(0.8, 1.0),
              motion={"body": K.body(headLevel=0.0, twist=0.1, land=1.4, shift=0.0)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 1}, arms=0.0, bob=0.0, sway=0.3, mass=2.4),
              voice={"ambient": [("block.glass.step", 0.6, 0.5), ("entity.stray.ambient", 0.4, 0.5)], "hurt": [("block.glass.hit", 0.9, 0.6), ("entity.stray.hurt", 0.5, 0.5)],
                     "death": [("block.glass.break", 1.0, 0.6), ("entity.stray.death", 0.6, 0.5)], "step": [("block.powder_snow.step", 0.5, 0.7)]}),
    K.Monster("hail_stray", "The Hail Slinger", hail_slinger, paint_hail_slinger, kind="strider", box=(0.6, 1.1), sunk=("pouch", "hail", "longf"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.62, 0.44), lift=(1.6, 2.8), crouch=0.5, swing=0.22, bob=0.5, roll=0.06),
                                "body": K.body(headLevel=0.5, twist=0.3, land=1.1, shift=0.5)},
              life=dict(STILL, temperament="monster", idles={"look": 4, "settle": 1}, arms=0.2, headSpeed=6.0, springHz=2.2),
              voice={"ambient": [("entity.stray.ambient", 0.5, 1.3), ("block.wool.step", 0.4, 1.0)], "hurt": [("entity.stray.hurt", 0.7, 1.3)],
                     "death": [("entity.stray.death", 0.8, 1.3)], "step": [("block.snow.step", 0.35, 1.2)]}),
    K.Monster("frost_wisp", "The Snow Star", snow_star, paint_snow_star, kind="floater", box=(0.9, 0.9), lift=4, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.2, tilt=0.2, climb=0.4)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}),
              voice={"ambient": [("block.amethyst_block.chime", 0.5, 1.4), ("entity.breeze.idle_air", 0.4, 1.4)], "hurt": [("block.glass.hit", 0.8, 1.5), ("block.amethyst_cluster.hit", 0.6, 1.4)],
                     "death": [("block.glass.break", 0.9, 1.4), ("block.amethyst_cluster.break", 0.7, 1.4)]}),
    K.Monster("ice_hound", "The Ice Hound", ice_hound, paint_ice_hound, kind="runner", box=(0.7, 1.2),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.66, 0.4), lift=(2.0, 3.6), crouch=0.5, swing=0.22, bob=0.4, roll=0.035,
                                               lag=-0.25, lead=0.12, rear=1.0),
                                "body": K.body(headLevel=0.5, twist=0.25, slope=0.85, land=1.2, shift=0.4, nod=0.05)},
              life=dict(STILL, temperament="hunter", idles={"look": 3, "sniff": 2, "flick": 2, "stretch": 1}, springHz=2.6),
              voice={"ambient": [("entity.fox.aggro", 0.5, 0.9), ("block.glass.step", 0.4, 1.2)], "hurt": [("entity.fox.hurt", 0.7, 0.9), ("block.glass.hit", 0.6, 1.2)],
                     "death": [("entity.fox.death", 0.8, 0.9), ("block.glass.break", 0.7, 1.1)], "step": [("block.glass.step", 0.3, 1.4)]}),
    K.Monster("chant_master", "The Long Horn", long_horn, paint_long_horn, kind="strider", box=(0.8, 1.7), sunk=("rimh", "bellh", "ribbon"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.64, 0.46), lift=(1.8, 3.0), crouch=0.5, swing=0.3, bob=0.6, roll=0.05),
                                "body": K.body(headLevel=0.4, twist=0.2, land=1.2, shift=0.5)},
              life=dict(STILL, temperament="monster", idles={"look": 2, "settle": 2}, arms=0.0, mass=1.6, springHz=1.4, springGain=1.3),
              voice={"ambient": [("event.raid.horn", 0.12, 0.6), ("block.wool.step", 0.4, 0.6)], "hurt": [("entity.stray.hurt", 0.6, 0.6), ("block.copper.hit", 0.6, 0.6)],
                     "death": [("entity.stray.death", 0.7, 0.6), ("block.copper.break", 0.8, 0.5)], "step": [("block.snow.step", 0.4, 0.8)]}),
    K.Monster("the_yeti", "The Yeti", yeti, paint_yeti, kind="runner", box=(1.6, 2.2), arms=True, sunk=("flag", "icicle"),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.8), duty=(0.72, 0.48), lift=(2.4, 4.0), crouch=0.8, rear=1.0, swing=0.34, bob=0.7, roll=0.045,
                                               lag=-0.25, lead=0.12, regrip=[5.0, 12.0]),
                                "body": K.body(headLevel=0.4, twist=0.15, slope=0.7, land=1.6, shift=0.7)},
              life=dict(STILL, temperament="beast", idles={"look": 3, "sniff": 1, "shake": 1, "settle": 2}, mass=3.6, springHz=1.2, springGain=1.3),
              voice={"ambient": [("entity.polar_bear.ambient", 0.8, 0.6), ("entity.ravager.ambient", 0.4, 0.6)], "hurt": [("entity.polar_bear.hurt", 0.9, 0.6)],
                     "death": [("entity.polar_bear.death", 1.0, 0.6)], "step": [("entity.polar_bear.step", 0.7, 0.7)]}),
]
