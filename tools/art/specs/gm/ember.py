"""The Ember Mines' monsters (SPEC §60). Room: black coal and orange fire. So they are pale ash and worn iron, and each
carries an ember inside it, behind a grate or under a crust."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_fur, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

ASH = Ramp("#4a4642", "#66615c", "#847e76", "#a39c92", "#c0b9ae", "#dcd6cc")
IRON = Ramp("#26242a", "#38363e", "#4e4c56", "#686670", "#84828c", "#a2a0aa")
COAL = Ramp("#0c0c10", "#16161c", "#22222a", "#30303a", "#42424e")
BRICK = Ramp("#3a1e18", "#562c22", "#74402e", "#90563e", "#aa7052")
SLAG = Ramp("#7a2a06", "#b44a0a", "#e0761a", "#f8a43a", "#ffd070", "#fff0c0")
EMBER, HOT = "#ff8a2a", "#ffe6a8"


def embers(P, cube, fname, every=3, a=220, seed=0):
    """Coals: dark lumps with fire in the cracks between them."""
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    for i, j in f.each():
        if (i * 5 + j * 3 + seed) % every == 0:
            cc = hexc(HOT if (i + j + seed) % 5 == 0 else EMBER)
            f.set(i, j, cc)
            g.set(i, j, cc[:3] + (a,))
        else:
            f.set(i, j, COAL.at(1 + (i * 3 + j) % 3))


def quad_gait(r, **over):
    g = dict(duty=(0.68, 0.44), lift=(2.2, 3.6), crouch=0.5, swing=0.26, bob=0.45, roll=0.04, lag=-0.25, lead=0.12, rear=1.0)
    g.update(over)
    return K.gait("ripple-alternate", K.room(r, K.legs_of(r), g["crouch"]), **g)


QUAD = ("0_l", "0_r", "1_l", "1_r")
TROT = [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)]


# ---------------------------------------------------------------------------------------------------------------------
# The Pickhead (cinder_miner): a hunched beast carrying a smouldering hod of coal, its head an iron pick on a long neck.
# It rears and pecks the floor; the pick sticks, and that is your moment.

def pickhead():
    r = Rig("cinder_miner", (128, 64))
    r.shadow = 0.8
    body = K.at(r.bone("body", (0, 13, 0)), 13)
    body.cube("torso", -4, -4, -7, 8, 7, 14)
    K.legs2(body, "0", (3, 1, -5), 5, 6, -14, thick=(3, 2))
    K.legs2(body, "1", (3, 0, 5), 6, 6, 24, thick=(3, 2))
    hod = body.bone("cup", (0, -4, 2), (-8, 0, 0))
    hod.cube("hod", -4, -6, -3, 8, 6, 8)
    hod.cube("coals", -3, -7, -2, 6, 1, 6)
    neck = body.bone("neck", (0, -3, -7), (42, 0, 0))
    neck.cube("neck", -1.5, -9, -1.5, 3, 9, 3)
    head = neck.bone("head", (0, -9, 0), (-22, 0, 0))
    head.cube("socket", -1.5, -1.5, -1.5, 3, 3, 3)
    head.cube("bar", -1, -1, -8, 2, 2, 13)
    head.cube("point", -0.5, -0.5, -11, 1, 1, 3)
    head.cube("poll", -0.5, -0.5, 5, 1, 1, 2)
    body.bone("tail", (0, -2, 7), (30, 0, 0)).cube("tail", -0.5, 0, 0, 1, 1, 5)
    r.look = [("neck", 0.5, 0.2), ("head", 0.3, 0.3)]
    r.pack()

    K.moves(r, swing="Ember Swing")
    stuck = {"body": {"rot": (8, 0, 0), "pos": (0, -1, -1)}, "neck": (62, 0, 0), "head": (24, 0, 0), "cup": (10, 0, 0)}
    K.act(r, "swing", {"body": {"rot": (-24, 0, 0), "pos": (0, 2, 2)}, "neck": (-44, 0, 0), "head": (-30, 0, 0), "cup": (-14, 0, 0),
                       "leg0_l": (-40, 0, -8), "leg0_r": (-30, 0, 8), "shin0_l": (-20, 0, 0), "shin0_r": (-30, 0, 0)},
          stuck, w=1.6, length=0.5, t_hit=0.1, over=0.0, back=0.0, after=stuck, plant=QUAD, lag={"cup": 0.08, "tail": 0.06}, open_pose=stuck)
    # stuck fast: it hauls at its own head, twice, and the third pull frees it
    K.keyed(r, "swing_open", 2.6, [(t, K.planted(r, K.plus(stuck, {"body": {"pos": (0, 0, z)}, "neck": (-z * 4, 0, 0)}), QUAD))
                                   for t, z in ((0, 0), (0.5, 0), (0.62, 1.6), (0.8, 0), (1.3, 0), (1.42, 2.0), (1.6, 0), (2.6, 0))])
    K.plain(r, {"neck": (-30, 0, 0), "head": (-20, 0, 0), "body": {"pos": (0, 0.5, 1)}}, {"neck": (34, 0, 0), "head": (16, 0, 0), "body": {"pos": (0, 0, -2)}},
            plant=QUAD, lag={"cup": 0.06})
    K.loop(r, "idle", 6.0, lambda p: {"neck": (2 * s(p), 6 * s(p, 0.3) + 16 * bump(p, 0.6, 0.06), 0), "head": (3 * s(p * 2), 0, 0),
                                      "cup": (1.5 * s(p * 2), 0, 0), "tail": (0, 10 * s(p * 2), 0)}, n=14)
    K.loop(r, "walk", 0.9, lambda p: {"neck": (5 * s(p * 2, 0.1), 0, 2 * s(p)), "head": (-4 * s(p * 2, 0.2), 0, 0), "cup": (3 * s(p * 2, 0.3), 0, 3 * s(p, 0.2)),
                                      "tail": (0, 12 * s(p), 0)})
    K.loop(r, "run", 0.5, lambda p: {"neck": (14 + 7 * s(p * 2, 0.1), 0, 0), "head": (-10, 0, 0), "cup": (5 * s(p * 2, 0.3), 0, 4 * s(p, 0.2)),
                                     "body": (4, 0, 0), "tail": (-20, 14 * s(p), 0)})
    K.fallback_walk(r, TROT)
    K.loop(r, "angry", 1.6, lambda p: {"neck": (-14 + 3 * s(p * 2), 0, 0), "head": (20, 0, 0), "body": {"pos": (0, -0.8, 0)}, "tail": (-30, 0, 0)}, plant=QUAD)
    K.flinch(r, {"neck": (-24, 0, 10), "head": (-10, 0, 0), "cup": (-12, 0, 0), "body": {"pos": (0, 0, 1.5)}}, plant=QUAD)
    side = {"body": (0, 0, 84), "neck": (20, 0, 0), "head": (10, 30, 0), "cup": (0, 0, 20), "leg0_l": (30, 0, 0), "leg0_r": (10, 0, 0),
            "leg1_l": (-30, 0, 0), "leg1_r": (-10, 0, 0), "tail": (0, 0, 30)}
    start = K.planted(r, {"neck": (-40, 0, 0), "body": {"pos": (0, 1, 1)}}, QUAD)
    K.keyed(r, "death", 1.2, [(0, {}), (0.15, start)] + K.fall(r, 0.15, start, 0.75, side, steps=4) + [(0.88, K.on_floor(r, K.plus(side, {"body": (0, 0, -7)}))),
                                                                                                     (1.2, K.on_floor(r, side))], lag={"cup": 0.08})
    return r


def paint_pickhead(r, P):
    P.paint("torso", ASH, 2.6, p_fur(1.0, 3, seed=2), rim=0.5)
    P.paint("neck", ASH, 3.0, p_bands(3, 0.7))
    P.paint("tail", ASH, 2.4)
    P.paint("hod", IRON, 1.8, p_plates(4, 3, 0.8), rim=0.7)
    embers(P, "coals", "top", every=2, seed=1)
    for fname in K.SIDES:
        f = P.face("coals", fname)
        for i, j in f.each():
            f.set(i, j, COAL.at(2))
    for nm, base in (("socket", 2.6), ("bar", 3.2), ("point", 4.4), ("poll", 2.4)):
        P.paint(nm, IRON, base, rim=1.0)
    K.lit(P, "socket", "north", 1, 1, 1, 1, EMBER, HOT, a=255)  # the one hot eye where the shaft goes through
    P.paint("thigh0_l", ASH, 2.4, p_fur(0.8, 3, seed=4))
    P.paint("shinb0_l", IRON, 2.0)
    P.paint("thigh1_l", ASH, 2.2, p_fur(0.8, 3, seed=5))
    P.paint("shinb1_l", IRON, 2.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Slag Pot (slag_cube): an iron crucible on three stub legs, molten slag slopping as it hops. It lands in a ring of
# fire and is empty and dark for a breath before it fills again.

def slag_pot():
    r = Rig("slag_cube", (64, 64))
    r.shadow = 0.8
    body = r.bone("body", (0, 21, 0))
    body.cube("pot", -5, -8, -5, 10, 8, 10)
    body.cube("rim", -6, -10, -6, 12, 2, 12)
    for k in range(3):
        a = math.tau * k / 3
        body.cube(f"stub{k}", 3.5 * math.sin(a) - 1, 0, -3.5 * math.cos(a) - 1, 2, 3, 2, share=None if k == 0 else "stub0")
    slag = body.bone("drip0", (0, -9, 0))
    slag.cube("slag", -5, -1, -5, 10, 2, 10)
    slag.bone("drip1", (0, -1, 0)).cube("slop", -2, -2, -2, 4, 2, 4)
    for side, nm in SIDES2:
        body.bone("ear_" + nm, (side * 6, -8, 0), (0, 0, side * 14)).cube("lug_" + nm, 0 if side > 0 else -2, -1, -1.5, 2, 4, 3, share=None if side > 0 else "lug_l")
    r.pack()

    full = lambda k, tilt=0: {"drip0": {"scale": (1, k, 1), "rot": (tilt, 0, 0)}, "drip1": {"scale": (1, max(0.05, k), 1)}}  # noqa: E731
    K.moves(r, splash="Molten Splash")
    K.act(r, "splash", K.plus(full(1.6), {"body": {"scale": (1.12, 0.82, 1.12), "pos": (0, -1.2, 0)}}),
          K.plus(full(2.4, -10), {"body": {"scale": (0.9, 1.25, 0.9), "pos": (0, 2.5, -1), "rot": (-12, 0, 0)}}), w=0.8, length=0.45, t_hit=0.08,
          open_pose=K.plus(full(0.05), {"body": {"scale": (1.06, 0.94, 1.06)}}))
    K.plain(r, K.plus(full(1.3, 8), {"body": (-10, 0, 0)}), K.plus(full(1.8, -14), {"body": {"rot": (16, 0, 0), "pos": (0, 0.5, -2)}}))
    K.loop(r, "idle", 3.0, lambda p: K.plus(full(1 + 0.25 * s(p * 2), 3 * s(p)), {"drip1": {"pos": (0, 0.8 * max(0, s(p * 3)), 0)}, "body": (0, 0, 1.5 * s(p))}))
    K.loop(r, "walk", 0.5, lambda p: K.plus(full(1.2, 10 * s(p)), {"body": {"scale": (1 - 0.08 * s(p), 1 + 0.12 * s(p), 1 - 0.08 * s(p))}}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(full(1.5 + 0.4 * s(p * 2)), {"body": (0, 0, 5 * s(p))}))
    K.flinch(r, K.plus(full(2.2, 16), {"body": {"rot": (-10, 0, 12), "scale": (1.1, 0.9, 1.1)}}))
    K.keyed(r, "death", 0.9, [(0, {}), (0.14, K.plus(full(2.6), {"body": (-14, 0, 0)})), (0.45, K.on_floor(r, K.plus(full(0.3), {"body": (0, 0, 80)}))),
                              (0.9, K.on_floor(r, K.plus(full(0.05), {"body": (0, 0, 96)})))])
    return r


def paint_slag_pot(r, P):
    P.paint("pot", IRON, 2.2, p_stone(P.rng, 0.6, 4), rim=0.6, ao=0.9)
    P.paint("rim", IRON, 3.2, rim=1.0)
    P.paint("stub0", IRON, 1.6)
    P.paint("lug_l", IRON, 2.8)
    for fname in K.SIDES:  # runs of slag down its sides, still glowing
        f, g = P.face("pot", fname), P.face("pot", fname, glow=True)
        for i in (2, 6, 7):
            for j in range(0, 2 + K._h(fname, i) % 4):
                f.set(i, j, hexc(EMBER))
                g.set(i, j, hexc(EMBER, 200 - j * 30))
    for nm in ("slag", "slop"):
        P.paint(nm, SLAG, 3.0, p_blotch("sl" + nm, 3, 0.9))
        for fname in K.ALL:
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                g.set(i, j, f.get(i, j)[:3] + (235,))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Bellows (ember_imp): a forge bellows that flies nozzle first. It swells before each burst and hangs slack after.

def bellows():
    r = Rig("ember_imp", (64, 64))
    r.shadow = 0.4
    body = r.bone("body", (0, 14, 0))
    body.cube("board", -4, 0, -3, 8, 1, 9)
    body.cube("nozzle", -1, -1, -8, 2, 2, 5)
    body.cube("mouth", -1.5, -1.5, -9, 3, 3, 1)
    top = body.bone("chest", (0, -1, -3), (-22, 0, 0))   # the upper board, hinged at the nozzle: it breathes
    top.cube("boardt", -4, -1, 0, 8, 1, 9)
    top.cube("grip", -1, -2, 9, 2, 1, 4)
    for k in range(3):
        f_ = body.bone(f"frill{k}", (0, 0, 1 + 2.4 * k))
        f_.cube(f"fold{k}", -3.5 + 0.5 * (k % 2), -2 - 1.5 * k, -0.5, 7 - (k % 2), 2 + int(1.5 * k), 1)
    body.cube("gripl", -1, 0, 6, 2, 1, 4)
    r.pack()

    def pump(a, folds=1.0):
        d = {"chest": (-a, 0, 0)}
        d.update({f"frill{k}": {"scale": (1, max(0.1, 1 + folds * a / 14.0 * (0.5 + 0.3 * k)), 1)} for k in range(3)})
        return d
    K.moves(r, burst="Cinder Burst")
    K.act(r, "burst", K.plus(pump(26), {"body": {"rot": (-10, 0, 0), "pos": (0, 1, 1.5)}}), K.plus(pump(-12), {"body": {"rot": (6, 0, 0), "pos": (0, 0, -3)}}),
          w=0.7, length=0.45, t_hit=0.07, open_pose=K.plus(pump(-13), {"body": {"rot": (26, 0, 0), "pos": (0, -2, 0)}}))
    K.plain(r, K.plus(pump(16), {"body": (-14, 0, 0)}), K.plus(pump(-10), {"body": {"rot": (10, 0, 0), "pos": (0, 0, -3.5)}}))
    K.loop(r, "idle", 2.4, lambda p: K.plus(pump(5 * s(p)), {"body": {"rot": (2 * s(p, 0.2), 0, 3 * s(p * 2)), "pos": (0, 0.8 * s(p), 0)}}))
    K.loop(r, "fly", 0.8, lambda p: {"body": (2 * s(p * 2), 0, 2 * s(p))})
    K.loop(r, "angry", 0.7, lambda p: K.plus(pump(10 + 8 * s(p)), {"body": (-8, 0, 0)}))
    K.flinch(r, K.plus(pump(-10), {"body": {"rot": (20, 0, 30), "pos": (0, 0, 2)}}))
    K.keyed(r, "death", 0.9, [(0, {}), (0.12, pump(30)), (0.3, K.plus(pump(-13), {"body": (40, 0, 60)})),
                              (0.9, K.plus(pump(-13), {"body": {"rot": (70, 0, 140), "pos": (0, -8, 0)}}))])
    return r


def paint_bellows(r, P):
    wood = Ramp("#2e2018", "#463224", "#604632", "#7c5c42", "#987656")
    for nm in ("board", "boardt", "grip", "gripl"):
        P.paint(nm, wood, 2.4, p_strokes(3, 5, seed=3), rim=0.6)
    for fname in ("top", "bottom"):
        for nm in ("board", "boardt"):
            f = P.face(nm, fname)
            for i, j in ((1, 1), (f.w - 2, 1), (1, f.h - 2), (f.w - 2, f.h - 2)):
                f.set(i, j, IRON.at(4))  # studs
    P.paint("nozzle", IRON, 2.6, rim=0.8)
    P.paint("mouth", IRON, 3.4)
    K.void(P, "mouth", "north", margin=1, top=1)
    K.lit(P, "mouth", "north", 1, 1, 1, 1, EMBER, HOT, a=255)
    for k in range(3):  # the leather, and the fire showing at each fold
        P.paint(f"fold{k}", ASH, 1.6, p_bands(2, 1.0, vertical=True), rim=0.3)
        for fname in ("west", "east"):
            f, g = P.face(f"fold{k}", fname), P.face(f"fold{k}", fname, glow=True)
            for j in range(f.h):
                f.set(0, j, hexc(EMBER))
                g.set(0, j, hexc(EMBER, 170))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Coal Tick (scorch_crawler): a lump of coal on six needle legs. In the seam it is a lump of coal, until two embers
# open.

def coal_tick():
    r = Rig("scorch_crawler", (64, 32))
    r.shadow = 0.4
    body = K.at(r.bone("body", (0, 20.5, 0)), 20.5)
    body.cube("lump", -3, -4, -4, 6, 5, 7)
    body.cube("lumpb", -2, -5, -2, 5, 1, 4)
    body.cube("lumpc", -4, -3, -1, 1, 3, 3)
    K.splay_pairs(body, 0.5, (3, 3, 3), (-2.5, 0, 2.5), 3, 5, rise=34)
    for side, nm in SIDES2:
        body.bone("jaw_" + nm, (side * 1, 0, -4), (0, side * 10, 0)).cube("jaw_" + nm, -0.5, -0.5, -2, 1, 1, 2, share=None if side > 0 else "jaw_l")
    r.pack()

    SIX = K.tags(3)
    bite = lambda a: {"jaw_l": (0, a, 0), "jaw_r": (0, -a, 0)}  # noqa: E731
    K.moves(r, bite="Scorch Bite")
    K.act(r, "bite", dict(bite(40), body={"pos": (0, -1.2, 1.5), "rot": (-10, 0, 0)}), dict(bite(-10), body={"pos": (0, 1.5, -3), "rot": (8, 0, 0)}),
          w=0.5, length=0.35, t_hit=0.06, plant=SIX)
    K.plain(r, dict(bite(34), body={"pos": (0, -0.6, 1)}), dict(bite(-8), body={"pos": (0, 0, -2)}), plant=SIX)
    K.loop(r, "idle", 4.0, lambda p: dict(bite(4 + 10 * bump(p, 0.5, 0.04)), body=(0, 0, 1.2 * s(p))), n=14)
    K.loop(r, "walk", 0.5, lambda p: {"body": (2 * s(p * 2), 0, 3 * s(p))})
    K.loop(r, "run", 0.3, lambda p: {"body": (-6 + 3 * s(p * 2), 0, 4 * s(p))})
    K.fallback_walk(r, K.alternating(3), axis="y")
    K.loop(r, "angry", 0.8, lambda p: dict(bite(20 + 10 * s(p * 2)), body={"rot": (-10, 0, 0), "pos": (0, 0.6, 0)}), plant=SIX)
    K.flinch(r, {"body": {"pos": (0, 0.8, 1), "rot": (0, 0, 16)}}, plant=SIX)
    curl = {f"leg{t}": (0, 0, (-60 if t.endswith("l") else 60)) for t in SIX}
    curl.update({f"shin{t}": (0, 0, (50 if t.endswith("l") else -50)) for t in SIX})
    K.keyed(r, "death", 0.8, [(0, {}), (0.12, K.planted(r, {"body": {"pos": (0, 2, 0)}}, SIX)), (0.45, K.on_floor(r, dict(curl, body=(0, 0, 170)))),
                              (0.8, K.on_floor(r, dict(K.scaled(curl, 1.1), body=(0, 0, 180))))])
    return r


def paint_coal_tick(r, P):
    for nm, base in (("lump", 2.2), ("lumpb", 2.8), ("lumpc", 1.8)):
        P.paint(nm, COAL, base, p_stone(P.rng, 1.2, 3), rim=0.9, ao=0.6)
    f = P.face("lump", "top")
    for i, j in f.each():  # the blue sheen coal has
        if (i * 3 + j * 5) % 9 == 0:
            f.set(i, j, hexc("#4a5068"))
    for i in (1, 4):  # two embers
        K.lit(P, "lump", "north", i, 2, 1, 1, EMBER, HOT, a=255)
    P.paint("jaw_l", IRON, 3.6)
    P.paint("thigh0_l", COAL, 3.0)
    P.paint("shinb0_l", IRON, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Slag Boar (slag_boar, the elite): a boar whose back is a furnace grate, fire behind iron ribs, tusks of cooled slag.
# It paws, then charges through fire; after a charge the grate vents and dims.

def slag_boar():
    r = Rig("slag_boar", (128, 64))
    r.shadow = 1.1
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    body.cube("barrel", -6, -7, -9, 12, 11, 18)
    body.cube("fire", -5, -8, -7, 10, 1, 14)
    for k in range(5):
        body.cube(f"bar{k}", -6, -9, -7 + 3 * k, 12, 2, 1, share=None if k == 0 else "bar0")
    K.legs2(body, "0", (4.5, 3, -6), 6, 6, -14, thick=(4, 3))
    K.legs2(body, "1", (4.5, 2, 6), 6, 7, 26, thick=(4, 3))
    head = body.bone("head", (0, -1, -9), (16, 0, 0))
    head.cube("skullb", -4.5, -4, -8, 9, 8, 8)
    head.cube("snout", -2.5, 0, -11, 5, 4, 3)
    for side, nm in SIDES2:
        head.bone("tusk_" + nm, (side * 3, 3, -9), (-30, side * 10, 0)).cube("tusk_" + nm, -0.5, -5, -0.5, 1, 5, 1, share=None if side > 0 else "tusk_l")
        head.bone("ear_" + nm, (side * 4, -4, -2), (0, 0, side * 30)).cube("ear_" + nm, -1, -3, 0, 2, 3, 1, share=None if side > 0 else "ear_l")
    body.bone("tail", (0, -5, 9), (40, 0, 0)).cube("tailb", -0.5, 0, 0, 1, 1, 5)
    r.look = [("head", 0.4, 0.3)]
    r.pack()

    K.moves(r, charge="Slag Charge", toss="Tusk Toss")
    vent = K.planted(r, {"body": {"pos": (0, -1.5, 0)}, "head": (26, 0, 0)}, QUAD)
    lowered = {"body": {"rot": (8, 0, 0), "pos": (0, -1, 2.5)}, "head": (22, 0, 0), "leg0_l": (-36, 0, 0), "shin0_l": (-26, 0, 0), "tail": (-40, 0, 0)}
    K.act(r, "charge", lowered, {"body": {"rot": (-4, 0, 0), "pos": (0, 0.5, -6)}, "head": (-10, 0, 0)}, w=1.3, length=0.6, t_hit=0.12, plant=QUAD,
          lag={"tail": 0.08}, stuck=vent)
    # pawing: the raised hoof strikes the ground twice before it goes (the last key is where the strike begins)
    hoof = lambda up: K.planted(r, K.plus({k: v for k, v in lowered.items() if not k.endswith("0_l")},  # noqa: E731
                                          {"leg0_l": (-36, 0, 0), "shin0_l": (-26, 0, 0)} if up else {"head": (4, 0, 0)}), QUAD)
    K.keyed(r, "charge_windup", 1.3, [(0, {}), (0.3, hoof(True)), (0.5, hoof(False)), (0.7, hoof(True)), (0.9, hoof(False)), (1.1, hoof(True)),
                                      (1.3, K.planted(r, K.plus(lowered, K.scaled(lowered, 0.06)), QUAD))], lag={"tail": 0.08})
    K.act(r, "toss", {"head": (34, 0, 0), "body": {"pos": (0, -1.5, 1)}}, {"head": (-44, 0, 0), "body": {"pos": (0, 1, -2), "rot": (-8, 0, 0)}}, w=1.0, length=0.5,
          t_hit=0.09, plant=QUAD, open_pose={"head": (-10, 0, 0), "body": {"pos": (0, -1, 0)}})
    K.plain(r, {"head": (24, 16, 0), "body": {"pos": (0, -0.5, 1.5)}}, {"head": (-26, -20, 0), "body": {"pos": (0, 0, -3)}}, plant=QUAD)
    K.loop(r, "idle", 6.0, lambda p: {"head": (3 * s(p), 5 * s(p, 0.3), 0), "ear_l": (0, 0, 20 * bump(p, 0.3, 0.03)), "ear_r": (0, 0, -20 * bump(p, 0.7, 0.03)),
                                      "tail": (0, 14 * s(p * 3), 0), "body": (0.8 * s(p * 2), 0, 0)}, n=16)
    K.loop(r, "walk", 0.9, lambda p: {"head": (4 * s(p * 2, 0.1), 0, 2 * s(p)), "body": (1.5 * s(p * 2), 0, 2.5 * s(p)), "tail": (0, 16 * s(p), 0)})
    K.loop(r, "run", 0.5, lambda p: {"head": (12 + 5 * s(p * 2, 0.1), 0, 0), "body": (4 + 3 * s(p * 2), 0, 2 * s(p)), "tail": (-30, 10 * s(p), 0)})
    K.fallback_walk(r, TROT)
    K.loop(r, "angry", 1.4, lambda p: {"head": (18 + 3 * s(p * 2), 0, 0), "body": {"pos": (0, -1, 0)}, "tail": (-36, 0, 0)}, plant=QUAD)
    K.flinch(r, {"head": (-16, 0, 12), "body": {"pos": (0, 0.5, 2), "rot": (0, 0, 5)}}, plant=QUAD)
    side = {"body": (0, 0, 86), "head": (10, 20, 0), "leg0_l": (34, 0, 0), "leg0_r": (10, 0, 0), "leg1_l": (-34, 0, 0), "leg1_r": (-12, 0, 0), "tail": (0, 0, 30)}
    start = K.planted(r, {"head": (-30, 0, 0), "body": {"pos": (0, 1, 2)}}, QUAD)
    K.keyed(r, "death", 1.3, [(0, {}), (0.16, start)] + K.fall(r, 0.16, start, 0.8, side, steps=4)
            + [(0.94, K.on_floor(r, K.plus(side, {"body": (0, 0, -6)}))), (1.3, K.on_floor(r, side))], lag={"tail": 0.08})
    return r


def paint_slag_boar(r, P):
    P.paint("barrel", ASH, 2.4, p_fur(1.0, 3, seed=1), rim=0.5, ao=0.8)
    embers(P, "fire", "top", every=2, a=235, seed=3)
    for fname in K.SIDES:
        f = P.face("fire", fname)
        for i, j in f.each():
            f.set(i, j, COAL.at(2))
    P.paint("bar0", IRON, 3.2, rim=1.0)
    P.paint("skullb", ASH, 2.8, p_fur(0.8, 3, seed=2), rim=0.6)
    P.paint("snout", ASH, 3.4)
    f = P.face("snout", "north")
    for i in (1, 3):
        f.set(i, 1, hexc("#1a1210"))
    for i in (1, 7):  # eyes: coals set deep
        K.lit(P, "skullb", "north", i, 3, 1, 1, EMBER, HOT, a=255)
    P.paint("tusk_l", SLAG, 1.2, rim=0.8)
    K.lit(P, "tusk_l", "north", 0, 0, 1, 1, EMBER, HOT, a=200)
    P.paint("ear_l", ASH, 2.0)
    P.paint("tailb", ASH, 2.2)
    for tag in ("0", "1"):
        P.paint(f"thigh{tag}_l", ASH, 2.2, p_fur(0.8, 3, seed=6))
        P.paint(f"shinb{tag}_l", IRON, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Overseer (the_overseer, the Warden): a brick chimney that walks on iron legs, two pokers for arms, a furnace door
# in its chest. The door swings open before the ring of embers, and hangs open after.

def overseer():
    r = Rig("the_overseer", (128, 128))
    r.shadow = 1.2
    body = K.at(r.bone("body", (0, 6, 0)), 6)
    body.cube("firebox", -6, -8, -6, 12, 12, 12)
    K.legs2(body, "1", (4, 3, 0), 9, 10, 30, thick=(3, 2), foot=(4, 3, 5, -2.5))
    chest = body.bone("chest", (0, -8, 0), (4, 0, 0))
    chest.cube("stack0", -5, -12, -5, 10, 12, 10)
    chest.cube("stack1", -4, -24, -4, 8, 12, 8)
    chest.cube("crown", -5, -27, -5, 10, 3, 10)
    hatch = body.bone("hatch", (-4, -2, -6))
    hatch.cube("door", 0, -4, -1, 8, 8, 1)
    for side, nm in SIDES2:
        arm = chest.bone("arm_" + nm, (side * 5.5, -9, 0), (-16, 0, side * -12))
        arm.cube("poker_" + nm, -0.5, 0, -0.5, 1, 13, 1, share=None if side > 0 else "poker_l")
        fore = arm.bone("fore_" + nm, (0, 13, 0), (-26, 0, side * 6))
        fore.cube("pokerl_" + nm, -0.5, 0, -0.5, 1, 14, 1, share=None if side > 0 else "pokerl_l")
        fore.cube("hook_" + nm, -0.5, 13, -2.5, 1, 1, 2, share=None if side > 0 else "hook_l")
    r.pack()

    legs = ("1_l", "1_r")
    door = lambda a: {"hatch": (0, a, 0)}  # noqa: E731
    lagc = {"fore_l": 0.05, "fore_r": 0.05}
    K.moves(r, walk_in="Furnace Walk", ring="Ember Ring", haze="Heat Haze")
    K.act(r, "walk_in", dict(door(-20), chest=(12, 0, 0), body={"pos": (0, -1.5, 1)}, arm_l=(30, 0, -10), arm_r=(30, 0, 10)),
          dict(door(-8), chest=(-6, 0, 0), body={"pos": (0, 0.5, -4)}, arm_l=(-50, 0, 0), arm_r=(-50, 0, 0)), w=0.9, length=0.6, t_hit=0.12, plant=legs, lag=lagc,
          stuck=dict(door(-70), chest=(16, 0, 0), body={"pos": (0, -3, 0)}, arm_l=(20, 0, -20), arm_r=(20, 0, 20)))
    K.act(r, "ring", dict(door(-104), chest=(-10, 0, 0), arm_l=(-60, 0, -40), arm_r=(-60, 0, 40), fore_l=(-20, 0, 0), fore_r=(-20, 0, 0), body={"pos": (0, 2, 0)}),
          dict(door(-112), chest=(8, 0, 0), arm_l=(-10, 0, -70), arm_r=(-10, 0, 70), body={"pos": (0, -2, 0)}), w=1.2, length=0.5, t_hit=0.1, plant=legs, lag=lagc,
          open_pose=dict(door(-96), chest=(10, 0, 0), arm_l=(10, 0, -16), arm_r=(10, 0, 16), body={"pos": (0, -2.5, 0)}))
    K.act(r, "haze", dict(door(-40), chest={"rot": (-6, 0, 0), "scale": (1.04, 1.04, 1.04)}, arm_l=(0, 0, -50), arm_r=(0, 0, 50)),
          dict(door(-60), chest={"rot": (4, 0, 0), "scale": (0.98, 1.0, 0.98)}, arm_l=(0, 0, -24), arm_r=(0, 0, 24)), w=1.8, length=0.6, plant=legs, lag=lagc)
    K.plain(r, {"chest": (0, 22, 0), "arm_r": (-70, 0, 30), "fore_r": (-40, 0, 0)}, {"chest": (6, -26, 0), "arm_r": (-64, 0, -20), "fore_r": (16, 0, 0)},
            heavy=({"chest": (-12, 0, 0), "arm_l": (-150, 0, -8), "arm_r": (-150, 0, 8), "fore_l": (-20, 0, 0), "fore_r": (-20, 0, 0)},
                   {"chest": (14, 0, 0), "arm_l": (-40, 0, 0), "arm_r": (-40, 0, 0), "fore_l": (10, 0, 0), "fore_r": (10, 0, 0), "body": {"pos": (0, -1.5, 0)}}),
            plant=legs, lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: dict(door(-3 - 3 * s(p * 2) - 14 * bump(p, 0.5, 0.03)), chest=(1.0 * s(p), 0, 0.8 * s(p, 0.3)), arm_l=(3 * s(p, 0.1), 0, 0),
                                          arm_r=(3 * s(p, 0.6), 0, 0), fore_l=(4 * s(p * 2), 0, 0)), n=16)
    K.loop(r, "walk", 1.3, lambda p: dict(door(-4 - 4 * s(p * 2)), chest=(2 + 2 * s(p * 2), 0, 2.6 * s(p)), arm_l=(12 * s(p), 0, 0), arm_r=(12 * s(p, 0.5), 0, 0)))
    K.loop(r, "run", 0.85, lambda p: dict(door(-10 - 6 * s(p * 2)), chest=(8 + 3 * s(p * 2), 0, 3.4 * s(p)), arm_l=(20 * s(p), 0, 0), arm_r=(20 * s(p, 0.5), 0, 0)))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 2.2, lambda p: dict(door(-24 - 8 * s(p * 2)), chest=(5, 0, 0), arm_l=(-30, 0, -14), arm_r=(-30, 0, 14), body={"pos": (0, -1, 0)}), plant=legs)
    K.flinch(r, dict(door(-30), chest=(-9, 0, 4), arm_l=(-20, 0, -20), arm_r=(-20, 0, 20)), plant=legs)
    knees = K.planted(r, dict(door(-110), body={"pos": (0, -5, 0)}, chest=(10, 0, 0), arm_l=(20, 0, -20), arm_r=(20, 0, 20)), legs)
    down = dict(door(-120), body={"rot": (80, 0, 6)}, chest=(8, 0, 0), arm_l=(-70, 0, -40), arm_r=(-60, 0, 46), leg1_l=(-60, 0, 0), leg1_r=(-50, 0, 0),
                shin1_l=(80, 0, 0), shin1_r=(90, 0, 0))
    K.keyed(r, "death", 1.8, [(0, {}), (0.2, K.planted(r, dict(door(-60), chest=(-12, 0, 0)), legs)), (0.7, knees)]
            + K.fall(r, 0.7, knees, 1.25, down, steps=4, skip=("door",)) + [(1.4, K.on_floor(r, K.plus(down, {"body": (-5, 0, 0)}), skip=("door",))),
                                                                          (1.8, K.on_floor(r, down, skip=("door",)))], lag=lagc)
    return r


def paint_overseer(r, P):
    def bricks(nm, base):
        P.paint(nm, BRICK, base, rim=0.5, ao=0.6)
        for fname in K.SIDES:
            f = P.face(nm, fname)
            for i, j in f.each():
                if j % 3 == 2 or (i + (j // 3) * 2) % 5 == 0:
                    f.set(i, j, BRICK.at(base - 1.6))
    bricks("stack0", 2.6)
    bricks("stack1", 2.2)
    P.paint("crown", IRON, 2.0, rim=0.8)
    K.void(P, "crown", "top", margin=2, top=2)
    for fname in K.SIDES:  # soot down from the top
        f = P.face("stack1", fname)
        for i, j in f.each():
            if j < 4 - (i * 3) % 3:
                f.set(i, j, COAL.at(2))
    P.paint("firebox", IRON, 2.2, p_plates(6, 6, 0.7), rim=0.7, ao=0.8)
    embers(P, "firebox", "north", every=2, a=240, seed=5)  # what the door covers: the fire itself
    f = P.face("firebox", "north")
    for i, j in f.each():
        if i < 2 or i > f.w - 3 or j < 2 or j > f.h - 3:
            f.set(i, j, IRON.at(2))
            P.face("firebox", "north", glow=True).set(i, j, (0, 0, 0, 0))
    P.paint("door", IRON, 3.0, rim=0.9)
    f, g = P.face("door", "north"), P.face("door", "north", glow=True)
    for j in (2, 4):  # slits in the door
        for i in range(2, 6):
            f.set(i, j, hexc(EMBER))
            g.set(i, j, hexc(EMBER, 230))
    for nm in ("poker_l", "pokerl_l", "hook_l"):
        P.paint(nm, IRON, 3.4)
    K.lit(P, "hook_l", "north", 0, 0, 1, 1, EMBER, HOT, a=220)
    P.paint("thigh1_l", IRON, 2.6, p_bands(4, 0.6))
    P.paint("shinb1_l", IRON, 2.0)
    P.paint("footb1_l", IRON, 3.0, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
FIRE = [("block.furnace.fire_crackle", 0.6, 0.8), ("block.fire.ambient", 0.4, 0.8)]
MONSTERS = [
    K.Monster("cinder_miner", "The Pickhead", pickhead, paint_pickhead, kind="runner", box=(0.9, 1.4), sunk=("bar", "point"),
              motion=lambda r: {"gait": quad_gait(r), "body": K.body(headLevel=0.3, twist=0.2, slope=0.8, land=1.2, shift=0.5, nod=0.06)},
              life=dict(STILL, temperament="monster", idles={"look": 3, "sniff": 1, "settle": 2}, mass=1.5, springHz=2.0),
              voice={"ambient": [("entity.goat.ambient", 0.5, 0.55), ("block.furnace.fire_crackle", 0.5, 0.9)], "hurt": [("entity.goat.hurt", 0.7, 0.6), ("block.anvil.hit", 0.3, 0.9)],
                     "death": [("entity.goat.death", 0.8, 0.55)], "step": [("block.iron.step", 0.3, 0.8)]}),
    K.Monster("slag_cube", "The Slag Pot", slag_pot, paint_slag_pot, kind="slider", box=(0.9, 0.9),
              motion={"body": K.body(headLevel=0.0, twist=0.15, land=1.8, shift=0.0)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 1}, springHz=2.2, springGain=1.6, mass=2.0),
              voice={"ambient": [("block.lava.pop", 0.7, 0.8), ("block.lava.ambient", 0.4, 1.2)], "hurt": [("block.anvil.hit", 0.4, 0.7), ("block.lava.pop", 0.8, 1.2)],
                     "death": [("block.lava.extinguish", 0.9, 0.8), ("block.iron.break", 0.8, 0.6)], "step": [("block.anvil.land", 0.25, 0.9)]}),
    K.Monster("ember_imp", "The Bellows", bellows, paint_bellows, kind="floater", box=(0.6, 0.6), lift=6,
              motion={"fly": K.flight(fast=4, bank=0.35, tilt=0.3, climb=0.5)},
              life=dict(STILL, temperament="spirit", breath=0.9, breathDepth=1.4, idles={"settle": 1}),
              voice={"ambient": FIRE + [("entity.blaze.ambient", 0.35, 1.3)], "hurt": [("entity.blaze.hurt", 0.6, 1.3), ("block.wood.hit", 0.6, 1.0)],
                     "death": [("block.fire.extinguish", 0.9, 0.9), ("block.wood.break", 0.7, 1.0)]}),
    K.Monster("scorch_crawler", "The Coal Tick", coal_tick, paint_coal_tick, kind="crawler", box=(0.6, 0.45),
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.5), crouch=0.5, speed=0.8), "body": K.body(headLevel=0.0, twist=0.2, slope=0.9, land=1.0, shift=0.3)},
              life=dict(STILL, temperament="insect", idles={"settle": 2, "flick": 1}),
              voice={"ambient": [("block.furnace.fire_crackle", 0.5, 1.4), ("entity.silverfish.ambient", 0.4, 0.8)], "hurt": [("entity.silverfish.hurt", 0.7, 0.8), ("block.stone.hit", 0.6, 1.3)],
                     "death": [("entity.silverfish.death", 0.8, 0.8), ("block.fire.extinguish", 0.6, 1.3)], "step": [("entity.silverfish.step", 0.3, 0.9)]}),
    K.Monster("slag_boar", "The Slag Boar", slag_boar, paint_slag_boar, kind="runner", box=(1.3, 1.3), sunk=("shinb0_l", "shinb1_l"),  # the game plants its feet
              motion=lambda r: {"gait": quad_gait(r, duty=(0.7, 0.44), lift=(2.4, 4.0), bob=0.6, swing=0.28), "body": K.body(headLevel=0.35, twist=0.18, slope=0.85, land=1.4, shift=0.7, nod=0.07)},
              life=dict(STILL, temperament="beast", idles={"look": 3, "sniff": 2, "shake": 1, "settle": 2}, mass=2.4),
              voice={"ambient": [("entity.hoglin.ambient", 0.7, 0.7)] + FIRE[:1], "hurt": [("entity.hoglin.hurt", 0.8, 0.7), ("block.iron.hit", 0.5, 0.7)],
                     "death": [("entity.hoglin.death", 0.9, 0.7)], "step": [("entity.hoglin.step", 0.5, 0.8)]}),
    K.Monster("the_overseer", "The Overseer", overseer, paint_overseer, kind="strider", box=(1.0, 3.0),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.64, 0.46), lift=(2.5, 4.0), crouch=0.5, swing=0.34, bob=0.8, roll=0.05),
                                "body": K.body(headLevel=0.0, twist=0.15, slope=0.5, land=1.5, shift=0.5)},
              life=dict(STILL, temperament="beast", breath=0.5, breathDepth=0.6, idles={"settle": 2}, mass=3.0, arms=0.5),
              voice={"ambient": [("block.blastfurnace.fire_crackle", 0.8, 0.6), ("block.smoker.smoke", 0.8, 0.6)], "hurt": [("block.nether_bricks.hit", 1.0, 0.6), ("block.anvil.hit", 0.4, 0.6)],
                     "death": [("block.nether_bricks.break", 1.0, 0.5), ("block.fire.extinguish", 1.0, 0.6)], "step": [("block.netherite_block.step", 0.6, 0.7)]}),
]
