"""The Abyssal Shrine's monsters (SPEC §60). Room: black and violet, almost no light. So they are outlined, not lit: a
rim of violet round a dark shape, and each carries one violet ring."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

VOID = Ramp("#020104", "#06040c", "#0c0816", "#140e22", "#1e1630", "#2a2040")
STONE = Ramp("#0e0c14", "#18151f", "#24202c", "#322d3c", "#433d4e", "#575062")
RIM, HOT, DIM = "#9a5cff", "#e6d4ff", "#4a2a8a"


def rimmed(P, cube, base=0.8, a=120, faces=K.ALL, ramp=VOID):
    """A dark shape with a line of violet round every face of it: outlined, not lit."""
    P.paint(cube, ramp, base)
    for fname in faces:
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        for i, j in f.each():
            if (i in (0, f.w - 1) or j in (0, f.h - 1)) and f.w > 1 and f.h > 1:
                f.set(i, j, hexc(DIM))
                g.set(i, j, hexc(RIM, a))


def ring(P, cube, fname, cx, cy, rad=1.5, a=255):
    """The one violet ring: a lit circle with dark inside it."""
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    for i, j in f.each():
        d = math.hypot(i - cx, j - cy)
        if abs(d - rad) <= 0.55:
            f.set(i, j, hexc(RIM))
            g.set(i, j, hexc(RIM, a))
        elif d < rad:
            f.set(i, j, VOID.at(0))


def hem(P, cube, faces, seed=0, a=110):
    """Hanging cloth in the dark: black, ragged below, a violet line down each edge."""
    K.tatter(P, cube, VOID, 1.2, seed=seed, faces=faces, fall=0.14)
    for fname in faces:
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        for i, j in f.each():
            if f.get(i, j)[3] and i in (0, f.w - 1):
                f.set(i, j, hexc(DIM))
                g.set(i, j, hexc(RIM, a))


# ---------------------------------------------------------------------------------------------------------------------
# The Watcher (abyss_watcher): a stone lamp-bowl that floats, one lidless eye on a stalk rising out of it. The stalk
# turns to you before the gaze. Stand behind a pillar.

def watcher():
    r = Rig("abyss_watcher", (64, 64))
    r.shadow = 0.5
    b = r.bone("body", (0, 17, 0))
    b.cube("bowl", -4, -2, -4, 8, 4, 8)
    b.cube("rimA", -5, -3, -5, 10, 1, 10)
    b.cube("footA", -2, 2, -2, 4, 2, 4)
    s0 = b.bone("stalk0", (0, -3, 0), (-12, 0, 0))
    s0.cube("st0", -1, -5, -1, 2, 5, 2)
    s1 = s0.bone("stalk1", (0, -5, 0), (26, 0, 0))
    s1.cube("st1", -1, -5, -1, 2, 5, 2, share="st0")
    s1.bone("head", (0, -5, 0), (-14, 0, 0)).cube("eyeball", -2, -4, -2, 4, 4, 4)
    r.look = [("head", 0.6, 0.5), ("stalk1", 0.2, 0.2)]
    r.pack()

    bend = lambda a, b_, h=0.0, k=1.0: {"stalk0": (a, 0, 0), "stalk1": (b_, 0, 0), "head": {"rot": (h, 0, 0), "scale": (k, k, k)}}  # noqa: E731
    lagc = {"stalk1": 0.05, "head": 0.1}
    K.moves(r, gaze="Hollow Gaze")
    K.act(r, "gaze", K.plus(bend(10, -24, 12, 1.2), {"body": {"rot": (-6, 0, 0), "pos": (0, 0.8, 0.5)}}), K.plus(bend(4, -30, 16, 1.35), {"body": {"rot": (6, 0, 0), "pos": (0, 0, 2)}}),
          w=1.1, length=0.5, t_hit=0.08, lag=lagc, open_pose=K.plus(bend(30, 50, 40, 0.8), {"body": {"rot": (8, 0, 4), "pos": (0, -1.5, 0)}}))
    K.plain(r, K.plus(bend(-30, -10, -10), {"body": {"pos": (0, 0.5, 1)}}), K.plus(bend(40, 30, 20), {"body": {"rot": (10, 0, 0), "pos": (0, -0.5, -3)}}), lag=lagc)
    K.loop(r, "idle", 7.0, lambda p: K.plus(bend(4 * s(p), 6 * s(p, 0.2), 4 * s(p, 0.4)), {"stalk0": (0, 30 * s(p, 0.1), 0), "body": {"pos": (0, 0.9 * s(p * 2), 0), "rot": (1.5 * s(p), 0, 1.5 * s(p, 0.25))}}), n=16)
    K.loop(r, "fly", 1.6, lambda p: K.plus(bend(-10, -8, 6), {"body": {"rot": (8, 0, 3 * s(p))}}))
    K.loop(r, "angry", 1.0, lambda p: K.plus(bend(8, -20, 10, 1.15 + 0.08 * s(p * 2)), {"body": (-4, 0, 3 * s(p))}))
    K.flinch(r, K.plus(bend(-24, -30, -20, 0.8), {"body": {"rot": (-10, 0, 14), "pos": (0, 0, 2)}}))
    over = K.plus(bend(40, 60, 50, 0.6), {"body": (10, 0, 84)})
    K.keyed(r, "death", 1.1, [(0, {}), (0.12, bend(10, -26, 14, 1.4)), (0.4, K.plus(bend(30, 50, 40, 0.7), {"body": {"rot": (10, 0, 30), "pos": (0, -4, 0)}})), (0.8, K.on_floor(r, over)),
                              (0.92, K.on_floor(r, K.plus(over, {"body": (0, 0, -8)}))), (1.1, K.on_floor(r, over))], lag=lagc)
    return r


def paint_watcher(r, P):
    rimmed(P, "bowl", 1.4, ramp=STONE)
    rimmed(P, "rimA", 2.4, a=150, ramp=STONE)
    P.paint("footA", STONE, 1.0)
    K.fill(P, "rimA", "top", VOID.at(0))      # the bowl is full of dark
    P.paint("st0", VOID, 1.6, p_bands(2, 0.8))
    for fname in K.SIDES:
        f, g = P.face("st0", fname), P.face("st0", fname, glow=True)
        for j in range(f.h):
            f.set(0, j, hexc(DIM))
            g.set(0, j, hexc(RIM, 90))
    rimmed(P, "eyeball", 0.6, a=140)
    ring(P, "eyeball", "north", 1.5, 1.5, 1.5)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Silent One (silent_stalker): long limbs and no outline, a hole in the light with two rings where eyes would be. It
# makes no sound at all.

def silent_one():
    r = Rig("silent_stalker", (64, 64))
    r.shadow = 0.3
    body = K.at(r.bone("body", (0, 15, 0)), 15)
    body.cube("slung", -3, -2, -4, 6, 4, 8)
    K.splay_pairs(body, -1, (2.5, 2.5), (-3, 3), 10, 19, rise=50, yaws=(38, -38), thick=1, mounts=True)
    head = body.bone("head", (0, -1, -4), (8, 0, 0))
    head.cube("mask", -3.5, -2, -3, 7, 4, 3)
    r.look = [("head", 0.6, 0.4)]
    r.pack()

    FOUR = K.tags(2)
    K.moves(r, step="Soundless Step")
    K.act(r, "step", K.planted(r, {"body": {"pos": (0, -5.5, 0)}, "head": {"rot": (20, 0, 0), "scale": (0.7, 0.7, 0.7)}}, FOUR), K.planted(r, {"body": {"pos": (0, 3, -1)}, "head": (-14, 0, 0)}, FOUR),
          w=0.7, length=0.6, t_hit=0.12, plant=FOUR)
    K.plain(r, {"body": {"pos": (0, 2, 1.5), "rot": (-10, 0, 0)}, "leg0_r": (0, -30, 40), "shin0_r": (0, 0, 30), "head": (-12, 0, 0)}, {"body": {"pos": (0, -1.5, -2.5), "rot": (10, 0, 0)}, "head": (10, 0, 0)},
            # its other swing: the left forelimb cocked instead of the right
            second=({"body": {"pos": (0, 2, 1.5), "rot": (-10, 0, 0)}, "leg0_l": (0, 30, -40), "shin0_l": (0, 0, -30), "head": (-12, 0, 0)},
                    {"body": {"pos": (0, -1.5, -2.5), "rot": (10, 0, 0)}, "head": (10, 0, 0)}),
            plant=FOUR)
    K.loop(r, "idle", 9.0, lambda p: {"body": {"pos": (0, 1.2 * s(p), 0), "rot": (0, 3 * s(p, 0.2), 1.0 * s(p, 0.4))}, "head": (4 * s(p, 0.3), 30 * (bump(p, 0.3, 0.07) - bump(p, 0.75, 0.07)), 0)}, n=18,
           plant=FOUR)
    K.loop(r, "walk", 1.2, lambda p: {"body": (1.5 * s(p * 2), 0, 2 * s(p)), "head": (-2 * s(p * 2, 0.1), 0, 0)})
    K.loop(r, "run", 0.8, lambda p: {"body": (8 + 2 * s(p * 2), 0, 3 * s(p)), "head": (-8, 0, 0)})
    K.fallback_walk(r, K.alternating(2), axis="y")
    K.loop(r, "angry", 1.4, lambda p: {"body": {"pos": (0, -3 + 0.6 * s(p * 2), 0)}, "head": (-10, 0, 6 * s(p))}, plant=FOUR)
    K.flinch(r, {"body": {"pos": (0, -2, 1.5), "rot": (-6, 0, 8)}, "head": (-16, 0, 0)}, plant=FOUR)
    fold = {f"leg{t}": (0, 0, (-36 if t.endswith("l") else 36)) for t in FOUR}
    fold.update({f"shin{t}": (0, 0, (70 if t.endswith("l") else -70)) for t in FOUR})
    start = K.planted(r, {"body": {"pos": (0, 3, 0)}, "head": (-20, 0, 0)}, FOUR)
    low = dict(fold, body=(0, 0, 30), head=(30, 0, 20))
    K.keyed(r, "death", 1.4, [(0, {}), (0.16, start)] + K.fall(r, 0.16, start, 0.9, low, steps=6) + [(1.05, K.on_floor(r, K.plus(low, {"body": (0, 0, 8)}))), (1.4, K.on_floor(r, low))])
    return r


def paint_silent_one(r, P):
    for nm in ("slung", "mask", "thigh0_l", "shinb0_l"):
        P.paint(nm, VOID, 0.0)
        for fname in K.ALL:
            K.fill(P, nm, fname, VOID.at(0))     # no shading, no edge: a hole
    ring(P, "mask", "north", 1.0, 1.5, 1.0)
    ring(P, "mask", "north", 5.0, 1.5, 1.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Void Spawn (void_spawn): drops of dark with whip tails, hopping. They pour out of dark corners.

def void_spawn():
    r = Rig("void_spawn", (64, 32))
    r.shadow = 0.35
    body = r.bone("body", (0, 20, 0))
    body.cube("drop", -3, -4, -3, 6, 8, 6)
    body.cube("taper", -2, -6, -2, 4, 2, 4)
    body.cube("tipV", -1, -8, -1, 2, 2, 2)
    tail = body.bone("tail", (0, 2, 3), (20, 0, 0))
    tail.cube("whip0", -0.5, -0.5, 0, 1, 1, 5)
    mid = tail.bone("tail_mid", (0, 0, 5), (-30, 0, 0))
    mid.cube("whip1", -0.5, -0.5, 0, 1, 1, 5, share="whip0")
    mid.bone("tail_end", (0, 0, 5), (-30, 0, 0)).cube("whip2", -0.5, -0.5, 0, 1, 1, 4)
    r.pack()

    sq = lambda k: {"body": {"scale": (1 + (1 - k) * 0.7, k, 1 + (1 - k) * 0.7), "pos": (0, -4 * (1 - k), 0)}}  # noqa: E731  (squashed on the floor)
    whip = lambda a: {"tail": (a, 0, 0), "tail_mid": (a * 0.8, 0, 0), "tail_end": (a * 0.6, 0, 0)}  # noqa: E731
    lagc = {"tail": 0.05, "tail_mid": 0.1, "tail_end": 0.15}
    K.moves(r, bite="Void Bite")
    splat = K.plus(sq(0.45), whip(-10))
    K.act(r, "bite", K.plus(sq(0.62), whip(50)), K.plus(whip(-30), {"body": {"scale": (0.85, 1.3, 0.85), "pos": (0, 3, -3), "rot": (-20, 0, 0)}}), w=0.5, length=0.4, t_hit=0.07, lag=lagc, stuck=splat)
    K.plain(r, K.plus(sq(0.8), whip(30)), K.plus(whip(-20), {"body": {"rot": (20, 0, 0), "pos": (0, 0.5, -2), "scale": (0.92, 1.12, 0.92)}}),
            # its other swing: the lash back the other way
            second=(K.plus(sq(0.85), whip(-34)), K.plus(whip(26), {"body": {"rot": (-14, 0, 0), "pos": (0, 0.5, -2), "scale": (0.92, 1.12, 0.92)}})), lag=lagc)
    K.loop(r, "idle", 3.0, lambda p: K.plus(sq(1 + 0.05 * s(p * 2)), whip(8 * s(p)), {"tail": (0, 14 * s(p, 0.2), 0)}))
    K.loop(r, "walk", 0.5, lambda p: K.plus(sq(1 + 0.14 * s(p)), whip(20 * s(p, 0.2))))
    K.loop(r, "run", 0.36, lambda p: K.plus(sq(1 + 0.2 * s(p)), whip(30 * s(p, 0.2)), {"body": (-8, 0, 0)}))
    K.loop(r, "angry", 0.6, lambda p: K.plus(sq(0.9 + 0.1 * s(p * 2)), whip(40 + 14 * s(p * 2)), {"tail": (0, 20 * s(p), 0)}))
    K.flinch(r, K.plus(sq(0.75), whip(-20), {"body": {"rot": (-10, 0, 12)}}))
    K.keyed(r, "death", 0.8, [(0, {}), (0.12, K.plus(sq(1.2), whip(50))), (0.4, K.plus(sq(0.4), whip(-10))), (0.8, K.plus(sq(0.08), whip(-14)))], lag=lagc)
    return r


def paint_void_spawn(r, P):
    rimmed(P, "drop", 0.4, a=130)
    rimmed(P, "taper", 0.4, a=110)
    P.paint("tipV", VOID, 0.6)
    ring(P, "drop", "north", 2.5, 3.5, 1.6)
    for nm in ("whip0", "whip2"):
        P.paint(nm, VOID, 0.4)
        for fname in ("top",):
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                f.set(i, j, hexc(DIM))
                g.set(i, j, hexc(RIM, 110))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Hollow Priest (hollow_priest, the elite): a vestment held open round nothing, a ring of light above it. The robe
# opens before it pulls you in. Shut, it is only cloth.

def hollow_priest():
    r = Rig("hollow_priest", (128, 128))
    r.shadow = 0.9
    body = r.bone("body", (0, 4, 0))
    body.cube("yokeH", -6, -1, -3, 12, 3, 6)
    body.cube("hollowH", -4, 2, -2, 8, 16, 4)
    body.cube("collarH", -3, -3, -2.5, 6, 2, 5)
    body.bone("cape0", (0, 1, 3)).cube("clothH0", -5.5, 0, 0, 11, 19, 0)
    for side, nm, k in ((1, "l", 1), (-1, "r", 2)):
        body.bone(f"cape{k}", (side * 5.5, 1, 0)).cube(f"clothH{k}", 0, 0, -3, 0, 19, 6, share=None if k == 1 else "clothH1")
        body.bone(f"cape{k + 2}", (side * 3, 1, -3)).cube(f"clothH{k + 2}", -2.5, 0, 0, 5, 19, 0, share=None if k == 1 else "clothH3")
        body.bone("tassel_" + nm, (side * 2, 1, -3.3)).cube("stole_" + nm, -1, 0, 0, 2, 15, 0, share=None if side > 0 else "stole_l")
        arm = body.bone("arm_" + nm, (side * 7, 0, 0), (0, 0, side * -6))
        arm.cube("sleeveH_" + nm, -2, 0, -2, 4, 9, 4, mirror=side < 0, share=None if side > 0 else "sleeveH_l")
        arm.bone("fore_" + nm, (0, 9, 0), (-12, 0, 0)).cube("cuffH_" + nm, -2.5, 0, -2.5, 5, 6, 5, mirror=side < 0, share=None if side > 0 else "cuffH_l")
    halo = body.bone("head", (0, -8, 0))          # where a head would be: the ring
    halo.cube("halo_n", -3, -0.5, -3, 6, 1, 1)
    halo.cube("halo_s", -3, -0.5, 2, 6, 1, 1, share="halo_n")
    halo.cube("halo_w", 2, -0.5, -2, 1, 1, 4)
    halo.cube("halo_e", -3, -0.5, -2, 1, 1, 4, share="halo_w")
    r.look = [("head", 0.3, 0.3)]
    r.pack()

    lagc = {"fore_l": 0.06, "fore_r": 0.06, "head": 0.08, "tassel_l": 0.12, "tassel_r": 0.12, **{f"cape{k}": 0.1 for k in range(5)}}

    def flare(a):
        return {"cape0": (0.6 * a, 0, 0), "cape1": (0, 0, -a), "cape2": (0, 0, a), "cape3": (-0.3 * a, 0, -a), "cape4": (-0.3 * a, 0, a), "tassel_l": (0, 0, -a * 0.7), "tassel_r": (0, 0, a * 0.7)}

    def arms(x, z, f=0.0):
        return {"arm_l": (x, 0, -z), "arm_r": (x, 0, z), "fore_l": (f, 0, 0), "fore_r": (f, 0, 0)}
    ringp = lambda y, rx=0.0, k=1.0: {"head": {"pos": (0, y, 0), "rot": (rx, 0, 0), "scale": (k, 1, k)}}  # noqa: E731
    K.moves(r, bolt="Hollow Bolt", draw="Draw In")
    K.act(r, "bolt", K.plus(flare(6), arms(-64, 10, -20), ringp(2.5, 40, 1.2), {"body": (-6, 0, 0)}), K.plus(flare(14), arms(-92, 4, 10), ringp(1.5, 70, 1.4), {"body": {"rot": (8, 0, 0), "pos": (0, 0, -1.5)}}),
          w=0.9, length=0.55, t_hit=0.09, lag=lagc)
    K.act(r, "draw", K.plus(flare(40), arms(-10, 84, -10), ringp(3, 0, 1.5), {"body": (-4, 0, 0)}), K.plus(flare(62), arms(-30, 96, 10), ringp(1, 20, 0.8), {"body": {"rot": (-10, 0, 0), "pos": (0, 0, 1.5)}}),
          w=1.2, length=0.6, t_hit=0.12, lag=lagc, open_pose=K.plus(flare(46), arms(4, 20, 6), ringp(-1.5, 20, 0.8), {"body": (8, 0, 0)}))
    K.plain(r, K.plus(arms(0, 0), {"arm_r": (-110, 0, 0), "fore_r": (-20, 0, 0), "body": (-6, 14, 0)}), K.plus({"arm_r": (-24, 0, 0), "fore_r": (10, 0, 0), "body": {"rot": (8, -16, 0), "pos": (0, 0, -2)}}),
            lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: K.plus(flare(2 + 2 * s(p * 2)), ringp(0.8 * s(p), 4 * s(p, 0.2)), {"head": {"rot": (4 * s(p, 0.2), 20 * s(p, 0.1), 0)}, "body": {"pos": (0, 0.6 * s(p * 2), 0), "rot": (0, 3 * s(p, 0.1), 0)},
                                                                                                "arm_l": (3 * s(p, 0.3), 0, 0), "arm_r": (2 * s(p, 0.6), 0, 0)}), n=16)
    K.loop(r, "walk", 1.4, lambda p: K.plus(ringp(0.3, 8), {"body": {"rot": (6 + s(p * 2), 0, 1.5 * s(p))}, "cape0": (14 + 3 * s(p * 2), 0, 0), "cape1": (8, 0, -2 * s(p)), "cape2": (8, 0, 2 * s(p, 0.5)),
                                                           "cape3": (6 + 2 * s(p * 2, 0.2), 0, 0), "cape4": (6 + 2 * s(p * 2, 0.7), 0, 0)}))
    K.loop(r, "run", 0.9, lambda p: K.plus(ringp(0.6, 16), {"body": {"rot": (14 + 1.5 * s(p * 2), 0, 2 * s(p))}, "cape0": (30 + 5 * s(p * 2), 0, 0), "cape1": (20, 0, -4), "cape2": (20, 0, 4),
                                                           "cape3": (16 + 3 * s(p * 2, 0.2), 0, 0), "cape4": (16 + 3 * s(p * 2, 0.7), 0, 0), "arm_l": (20, 0, 0), "arm_r": (20, 0, 0)}))
    K.loop(r, "angry", 1.4, lambda p: K.plus(flare(14 + 6 * s(p * 2)), arms(-20, 20, -10), ringp(2 + 0.6 * s(p * 2), 0, 1.2 + 0.1 * s(p * 4))))
    K.flinch(r, K.plus(flare(20), ringp(1.5, -20), {"body": {"rot": (-6, 0, 4), "pos": (0, 0, 1.5)}}))
    flat = {"body": {"scale": (1.2, 0.08, 1.2), "pos": (0, -18.5, 0)}, "head": {"scale": (1 / 1.2, 12.5, 1 / 1.2), "pos": (0, 2, 0), "rot": (0, 30, 0)}, "arm_l": (0, 0, -70), "arm_r": (0, 0, 70)}
    K.keyed(r, "death", 1.4, [(0, {}), (0.2, K.plus(flare(40), arms(-30, 60), ringp(4, 0, 1.6))), (0.7, K.plus(flare(10), {"body": {"scale": (1.15, 0.3, 1.15), "pos": (0, -13.5, 0)}, "head": {"scale": (1 / 1.15, 3.3, 1 / 1.15)}})),
                              (1.0, flat), (1.4, flat)], lag=lagc)
    return r


def paint_hollow_priest(r, P):
    rimmed(P, "yokeH", 1.2, a=140)
    rimmed(P, "collarH", 1.6, a=140)
    P.paint("hollowH", VOID, 0.0)
    for fname in K.ALL:
        K.fill(P, "hollowH", fname, VOID.at(0))
    hem(P, "clothH0", ("north", "south"), seed=1)
    hem(P, "clothH1", ("west", "east"), seed=2)
    hem(P, "clothH3", ("north", "south"), seed=3)
    for fname in ("north", "south"):       # the stole: a band of violet stitches down it
        f, g = P.face("stole_l", fname), P.face("stole_l", fname, glow=True)
        for i, j in f.each():
            f.set(i, j, VOID.at(2.4))
            if j % 3 == 1:
                f.set(i, j, hexc(DIM))
                g.set(i, j, hexc(RIM, 130))
    rimmed(P, "sleeveH_l", 1.0, a=100)
    rimmed(P, "cuffH_l", 1.4, a=120)
    K.fill(P, "cuffH_l", "bottom", VOID.at(0))
    for nm in ("halo_n", "halo_w"):
        K.lit_cube(P, nm, RIM, HOT, a=255, hot=lambda fn, i, j, f: fn in ("top", "bottom"))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Nothing (the_nothing, the Warden): three hooded hollows on long necks from one sunken body. Each head does one
# thing. The pull comes when all three look at you together.

NECKS = (("l", 5, 22), ("m", 0, 0), ("r", -5, -22))     # (tag, x, lean out): the middle one is the head the life layer turns


def nothing():
    r = Rig("the_nothing", (128, 128))
    r.shadow = 1.6
    body = r.bone("body", (0, 24, 0))
    body.cube("mound", -8, -5, -7, 16, 5, 14)
    body.cube("mound2", -5, -8, -5, 10, 3, 10)
    for tag, x, lean in NECKS:
        mid = tag == "m"
        n0 = body.bone("neck" if mid else "stem_" + tag, (x, -7, 0), (10, 0, lean))
        n0.cube("vert0_" + tag, -1, -10, -1, 2, 10, 2, share=None if tag == "l" else "vert0_l")
        n1 = n0.bone("neck_up" if mid else "stem_up_" + tag, (0, -10, 0), (-4, 0, -lean * 0.9))
        n1.cube("vert1_" + tag, -1, -10, -1, 2, 10, 2, share="vert0_l")
        h = n1.bone("head" if mid else "hood_" + tag, (0, -10, 0), (6, 0, -lean * 0.1))
        h.cube("cowl_" + tag, -2.5, -6, -2.5, 5, 6, 5, share=None if tag == "l" else "cowl_l")
        h.bone("drape_" + tag, (0, 0, 2.5)).cube("fall_" + tag, -2.5, 0, 0, 5, 6, 0, share=None if tag == "l" else "fall_l")
    r.look = [("head", 0.4, 0.3), ("hood_l", 0.3, 0.2), ("hood_r", 0.3, 0.2)]
    r.pack()

    N0 = {"l": "stem_l", "m": "neck", "r": "stem_r"}
    N1 = {"l": "stem_up_l", "m": "neck_up", "r": "stem_up_r"}
    HD = {"l": "hood_l", "m": "head", "r": "hood_r"}

    def crane(a0, a1, h, z=0.0, tags="lmr"):
        """Those necks bent forward (their lower and upper halves) and their heads nodded; z: brought in toward the middle."""
        q = {}
        for tag, _, lean in NECKS:
            if tag in tags:
                sgn = 1 if lean > 0 else -1 if lean < 0 else 0
                q[N0[tag]] = (a0, 0, -z * sgn)
                q[N1[tag]] = (a1, 0, 0)
                q[HD[tag]] = (h, 0, 0)
        return q
    lagc = {N1[t]: 0.05 for t in "lmr"}
    lagc.update({HD[t]: 0.1 for t in "lmr"})
    lagc.update({"drape_" + t: 0.14 for t in "lmr"})
    stag = dict(lagc, **{"stem_l": 0.0, "stem_up_l": 0.05, "hood_l": 0.1, "neck": 0.1, "neck_up": 0.15, "head": 0.2, "stem_r": 0.2, "stem_up_r": 0.25, "hood_r": 0.3})   # one, then the next, then the last
    swell = lambda k: {"body": {"scale": (k, 1 + (k - 1) * 2, k)}}  # noqa: E731
    K.moves(r, skulls="Nothing Skulls", event="Event Horizon", unlight="Unlight")
    K.act(r, "skulls", crane(-34, -20, 20), crane(30, 34, -20), w=0.9, length=0.75, t_hit=0.1, lag=stag)
    low = K.plus(crane(40, 60, 30), swell(0.96))
    K.act(r, "event", K.plus(crane(10, 20, -6, 16), swell(1.06)), K.plus(crane(34, 30, -24, 22), swell(1.12)), w=1.3, length=0.6, t_hit=0.12, lag=lagc, open_pose=low)
    K.act(r, "unlight", K.plus(crane(-14, -6, -30, -10), swell(1.04)), K.plus(crane(-6, 6, 20, -16), swell(0.98)), w=1.0, length=0.6, t_hit=0.12, lag=lagc)
    K.plain(r, crane(-30, -24, 10, 0, "m"), crane(46, 40, -20, 0, "m"), heavy=(crane(-20, -20, 10, -20, "lr"), crane(40, 36, -16, 26, "lr")),
            second=(crane(-30, -24, 10, 0, "l"), crane(46, 40, -20, 12, "l")), lag=lagc)   # its other swing: the left head alone

    def idle(p):
        q = {}
        for k, tag in enumerate("lmr"):   # each on its own slow time
            q[N0[tag]] = (4 * s(p, k / 3.0), 0, 3 * s(p * 2, k / 3.0 + 0.1))
            q[N1[tag]] = (5 * s(p, k / 3.0 + 0.15), 0, 0)
            q[HD[tag]] = (4 * s(p * 2, k / 3.0), 16 * s(p, k / 3.0 + 0.4), 0)
        q.update(swell(1 + 0.015 * s(p * 2)))
        return q
    K.loop(r, "idle", 9.0, idle, n=18)
    K.loop(r, "walk", 1.6, lambda p: K.plus(crane(6 + 2 * s(p * 2), 4, -6), {"body": (0, 0, 1.2 * s(p))}))
    K.loop(r, "run", 1.0, lambda p: K.plus(crane(16 + 3 * s(p * 2), 10, -16), {"body": (0, 0, 1.6 * s(p))}))
    K.loop(r, "angry", 1.4, lambda p: K.plus(crane(-10 + 4 * s(p * 2), -4, -16, -8 + 4 * s(p)), swell(1.04)))
    K.flinch(r, K.plus(crane(-18, -14, -10, -10), swell(0.97)))
    limp = {}
    for k, (tag, _, lean) in enumerate(NECKS):
        limp[N0[tag]] = (58 + 8 * k, 0, lean * 0.6)
        limp[N1[tag]] = (44 - 6 * k, 0, 0)
        limp[HD[tag]] = (20, 30 * (k - 1), 0)
    skip = tuple("fall_" + t for t in "lmr")
    K.keyed(r, "death", 2.0, [(0, {}), (0.25, K.plus(crane(-30, -20, -30, -14), swell(1.1))), (0.9, K.on_floor(r, K.plus(K.scaled(limp, 0.6), swell(0.95)), skip=skip)),
                              (1.5, K.on_floor(r, K.plus(limp, swell(0.9)), skip=skip)), (2.0, K.on_floor(r, K.plus(limp, swell(0.86)), skip=skip))], lag=stag)
    return r


def paint_nothing(r, P):
    rimmed(P, "mound", 0.8, a=110)
    rimmed(P, "mound2", 1.0, a=120)
    P.paint("vert0_l", VOID, 1.2, p_bands(2, 0.8))
    for fname in K.SIDES:
        f, g = P.face("vert0_l", fname), P.face("vert0_l", fname, glow=True)
        for j in range(0, f.h, 2):
            f.set(0, j, hexc(DIM))
            g.set(0, j, hexc(RIM, 100))
    rimmed(P, "cowl_l", 0.6, a=140)
    K.void(P, "cowl_l", "north", margin=1, top=1)
    ring(P, "cowl_l", "north", 2.0, 2.5, 1.2)
    hem(P, "fall_l", ("north", "south"), seed=4)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("abyss_watcher", "The Watcher", watcher, paint_watcher, kind="floater", box=(0.7, 1.4), lift=3, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.15, tilt=0.15, climb=0.4)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"look": 3, "settle": 1}, headSpeed=3.0, springHz=1.4, springGain=1.4),
              voice={"ambient": [("block.sculk_sensor.clicking", 0.4, 0.6), ("block.conduit.ambient.short", 0.4, 0.5)], "hurt": [("block.deepslate.hit", 0.9, 0.7), ("entity.guardian.hurt_land", 0.4, 0.5)],
                     "death": [("block.deepslate.break", 1.0, 0.6), ("block.conduit.deactivate", 0.6, 0.6)]}),
    K.Monster("silent_stalker", "The Silent One", silent_one, paint_silent_one, kind="crawler", box=(1.0, 1.4),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.6), duty=(0.72, 0.52), lift=(3.0, 5.0), crouch=0.6, swing=0.4, bob=0.3, roll=0.02, speed=0.45),
                                "body": K.body(headLevel=0.6, twist=0.1, slope=0.6, land=0.8, shift=0.8)},
              life=dict(STILL, temperament="hunter", breathDepth=0.0, idles={"look": 4, "settle": 1}, headSpeed=2.5),
              # it makes no sound: its voice is as near nothing as a sound can be; only a blow on it is heard, dully
              voice={"ambient": [("block.sculk.spread", 0.02, 0.5)], "hurt": [("block.sculk.hit", 0.3, 0.4)], "death": [("block.sculk.break", 0.35, 0.4)]}),
    K.Monster("void_spawn", "The Void Spawn", void_spawn, paint_void_spawn, kind="coiler", box=(0.6, 0.75), sunk=("whip",),
              motion={"body": K.body(headLevel=0.0, twist=0.2, slope=0.8, land=1.6, shift=0.0)},
              life=dict(STILL, temperament="insect", breathDepth=0.0, idles={"settle": 1}, bob=0.0, sway=0.0, springHz=2.6, springGain=1.6),
              voice={"ambient": [("block.sculk.spread", 0.5, 1.2)], "hurt": [("block.sculk.hit", 0.8, 1.2), ("entity.endermite.hurt", 0.4, 0.7)],
                     "death": [("block.sculk.break", 0.9, 1.1), ("entity.endermite.death", 0.4, 0.7)], "step": [("block.sculk.step", 0.3, 1.3)]}),
    K.Monster("hollow_priest", "The Hollow Priest", hollow_priest, paint_hollow_priest, kind="slider", box=(0.9, 2.0), sunk=("clothH", "stole"),
              motion={"body": K.body(headLevel=0.3, twist=0.3, land=1.0, shift=0.2)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"look": 2, "settle": 1}, bob=0.0, sway=0.7, arms=0.3, springHz=1.0, springDamping=0.12, springGain=1.5),
              voice={"ambient": [("block.conduit.ambient", 0.4, 0.5), ("block.wool.step", 0.4, 0.5)], "hurt": [("block.wool.hit", 0.9, 0.6), ("entity.wither_skeleton.hurt", 0.3, 0.5)],
                     "death": [("block.wool.break", 1.0, 0.5), ("block.conduit.deactivate", 0.7, 0.5)], "step": [("block.wool.step", 0.25, 0.5)]}),
    K.Monster("the_nothing", "The Nothing", nothing, paint_nothing, kind="slider", box=(1.6, 2.2), sunk=("fall_",),
              motion={"body": K.body(headLevel=0.4, twist=0.1, land=1.4, shift=0.0)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"look": 2, "settle": 1}, bob=0.0, sway=0.3, mass=4.0, headSpeed=2.5, springHz=1.0, springGain=1.4),
              voice={"ambient": [("block.conduit.ambient", 0.6, 0.4), ("entity.warden.heartbeat", 0.5, 0.6)], "hurt": [("block.sculk.hit", 1.0, 0.5), ("entity.wither.hurt", 0.25, 0.5)],
                     "death": [("block.sculk_shrieker.break", 1.0, 0.5), ("block.conduit.deactivate", 1.0, 0.4)], "step": [("block.sculk.step", 0.6, 0.5)]}),
]
