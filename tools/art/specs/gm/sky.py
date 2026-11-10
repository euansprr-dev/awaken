"""The Sky Archipelago's monsters (SPEC §60). Room: open sky, white cloud, islands. So they are dark against the sky:
tarred canvas, rope and storm grey, and each carries a lightning-white light. All five fly."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

CANVAS = Ramp("#14120e", "#221e16", "#342e22", "#4a4232", "#625846", "#7e725c")
ROPE = Ramp("#3a2e1a", "#564626", "#766034", "#987e48", "#b89c62", "#d6ba84")
STORM = Ramp("#1a1e26", "#282e3a", "#3a4252", "#50596c", "#6a7488", "#8892a6")
BONE = Ramp("#4a4436", "#6a6250", "#8c826a", "#aea488", "#ccc2a6", "#e6dec6")
BOLT, HOT = "#eaf2ff", "#ffffff"


def cloud(P, cube, base=2.4, seed=0):
    """Storm cloud: dark grey in soft lumps, darker underneath."""
    P.paint(cube, STORM, base, p_blotch("cl%d" % seed, 4, 1.0), rim=0.5, ao=0.9)
    K.fill(P, cube, "bottom", STORM.at(max(0.0, base - 1.6)))


def patches(P, cube, faces=K.SIDES, seed=0):
    """Tarred canvas, patched: a few squares of paler cloth sewn on, a stitch line round each."""
    P.paint(cube, CANVAS, 2.4, p_strokes(3, 4, seed=seed), rim=0.5, ao=0.6)
    for fname in faces:
        f = P.face(cube, fname)
        if f.w < 5 or f.h < 5:
            continue
        x0, y0 = 1 + K._h(cube, fname, seed) % (f.w - 4), 1 + K._h(cube, fname, seed, 1) % (f.h - 4)
        for i in range(x0, x0 + 3):
            for j in range(y0, y0 + 3):
                f.set(i, j, CANVAS.at(4.0) if (i in (x0, x0 + 2) or j in (y0, y0 + 2)) and (i + j) % 2 else CANVAS.at(3.4))


# ---------------------------------------------------------------------------------------------------------------------
# The Kite (gale_harpy): skin stretched on a diamond of bones, a hooked beak at the point, a long knotted tail. It
# climbs, folds and drops.

def kite():
    r = Rig("gale_harpy", (64, 64))
    r.shadow = 0.5
    b = r.bone("body", (0, 14, 0))
    b.cube("spineK", -0.5, -0.5, -7, 1, 1, 14)
    b.cube("sparK", -8, -0.6, -2.5, 16, 1, 1)
    head = b.bone("head", (0, 0, -7))
    head.cube("skullK", -1.5, -1.5, -2, 3, 3, 3)
    head.cube("beakK", -1, -0.5, -5, 2, 2, 3)
    head.cube("hookK", -0.5, 1.5, -5, 1, 2, 1)
    for side, nm in SIDES2:
        w = b.bone("wing_" + nm, (side * 0.5, 0, 0), (0, 0, side * -8))
        w.cube("skinK_" + nm, 0 if side > 0 else -8, 0, -7, 8, 0, 14, mirror=side < 0, share=None if side > 0 else "skinK_l")
    tail = b.bone("tail", (0, 0, 7), (-14, 0, 0))
    tail.cube("cord0", -0.5, -0.5, 0, 1, 1, 6)
    tail.cube("bow0", -2, 0, 4, 4, 0, 2)
    mid = tail.bone("tail_mid", (0, 0, 6), (-12, 0, 0))
    mid.cube("cord1", -0.5, -0.5, 0, 1, 1, 6, share="cord0")
    mid.cube("bow1", -2, 0, 4, 4, 0, 2, share="bow0")
    end = mid.bone("tail_end", (0, 0, 6), (-12, 0, 0))
    end.cube("cord2", -0.5, -0.5, 0, 1, 1, 6, share="cord0")
    end.cube("bow2", -2, 0, 4, 4, 0, 2, share="bow0")
    r.look = [("head", 0.4, 0.3)]
    r.pack()

    fold = lambda a: {"wing_l": (0, 0, -a), "wing_r": (0, 0, a)}  # noqa: E731  (up is positive)
    trail = lambda a: {"tail": (a, 0, 0), "tail_mid": (a * 0.8, 0, 0), "tail_end": (a * 0.6, 0, 0)}  # noqa: E731
    lagc = {"tail": 0.06, "tail_mid": 0.12, "tail_end": 0.18}
    K.moves(r, dive="Dive Bomb")
    downed = K.on_floor(r, K.plus(fold(-6), trail(10), {"body": (6, 0, 20), "head": (20, 0, 0)}))
    K.act(r, "dive", K.plus(fold(14), trail(-30), {"body": {"rot": (-44, 0, 0), "pos": (0, 3, 2)}, "head": (30, 0, 0)}),
          K.plus(fold(74), trail(26), {"body": {"rot": (62, 0, 0), "pos": (0, -2, -5)}, "head": (-20, 0, 0)}), w=0.9, length=0.4, t_hit=0.07, lag=lagc, stuck=downed)
    K.plain(r, K.plus(fold(20), {"body": {"rot": (-20, 0, 0), "pos": (0, 0.8, 1)}, "head": (-26, 0, 0)}), K.plus(fold(-6), {"body": {"rot": (16, 0, 0), "pos": (0, 0, -3)}, "head": (24, 0, 0)}), lag=lagc)
    K.loop(r, "idle", 4.0, lambda p: K.plus(fold(6 * s(p * 2)), trail(6 * s(p, 0.2)), {"body": {"rot": (-6 + 3 * s(p), 0, 5 * s(p, 0.25)), "pos": (0, 1.0 * s(p * 2, 0.1), 0)}, "tail": (0, 8 * s(p * 2, 0.3), 0),
                                                                               "head": (4 * s(p, 0.4), 10 * s(p, 0.6), 0)}), n=16)
    K.loop(r, "fly", 1.6, lambda p: K.plus(fold(8 + 5 * s(p * 2)), trail(4), {"body": {"rot": (6, 0, 6 * s(p))}, "tail": (0, 10 * s(p * 2, 0.3), 0)}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(fold(20 + 10 * s(p * 2)), trail(-14), {"body": (-18, 0, 4 * s(p)), "head": (14, 0, 0)}))
    K.flinch(r, K.plus(fold(40), trail(-20), {"body": {"rot": (-20, 0, 30), "pos": (0, 0, 2)}}))
    K.keyed(r, "death", 1.0, [(0, {}), (0.1, K.plus(fold(-20), {"body": (-20, 0, 0)})), (0.5, K.plus(fold(60), trail(-30), {"body": {"rot": (40, 0, 90), "pos": (0, -6, 0)}})),
                              (0.8, K.on_floor(r, K.plus(fold(-8), trail(12), {"body": (6, 0, 170)}))), (1.0, K.on_floor(r, K.plus(fold(-4), trail(14), {"body": (6, 0, 176)})))], lag=lagc)
    return r


def paint_kite(r, P):
    for nm in ("spineK", "sparK"):
        P.paint(nm, BONE, 3.6, p_bands(4, 0.6), rim=0.3)
    P.paint("skullK", BONE, 4.0, rim=0.6)
    P.paint("beakK", BONE, 2.6, rim=0.4)
    P.paint("hookK", CANVAS, 1.0)
    for fname in ("west", "east"):
        K.lit(P, "skullK", fname, 1, 1, 1, 1, BOLT, HOT, a=255)
    for fname in ("top", "bottom"):   # half a diamond of tarred skin: stretched thin, patched, ragged at its trailing edge
        f = P.face("skinK_l", fname)
        for i, j in f.each():
            # the spar meets the skin at j = 4.5; the diamond's edge runs from the nose to the spar's tip to the tail
            reach = (j / 4.5 if j < 4.5 else (13.5 - j) / 9.0) * 8.0
            x = i if True else f.w - 1 - i
            if x > reach + 0.4 and (f.w - 1 - i) > reach + 0.4:
                f.set(i, j, (0, 0, 0, 0))
            else:
                f.set(i, j, CANVAS.at(2.2 + 0.5 * ((i * 3 + j * 5) % 7 == 0) + 0.25 * (i % 3 == 0)))
    for fname in ("top", "bottom"):
        f = P.face("bow0", fname)
        for i, j in f.each():
            f.set(i, j, ROPE.at(3.0) if abs(i - 1.5) > 0.6 else CANVAS.at(1.0))
    P.paint("cord0", ROPE, 2.4, p_bands(2, 0.8, vertical=True))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Puffer (cloud_breeze): a ball of cloud that swells to twice its size. It swells before it blows you toward the
# edge, and is small and slow after.

LUMPS = [(-3, -2, 1), (3, -3, -1), (-2, 3, -2), (2, 2, 2), (0, -4, 0)]


def puffer():
    r = Rig("cloud_breeze", (64, 64))
    r.shadow = 0.6
    b = r.bone("body", (0, 13, 0))
    b.cube("coreP", -3, -3, -3, 6, 6, 6)
    for k, (x, y, z) in enumerate(LUMPS):
        b.bone(f"puff{k}", (x, y, z)).cube(f"lumpP{k}", -2, -2, -2, 4, 4, 4, share=None if k == 0 else "lumpP0")
    b.bone("mouth", (0, 1, -3.1)).cube("lips", -1, -1, -0.5, 2, 2, 1)
    r.pack()

    def swell(k, lumps=None, m=1.0):
        """The whole ball at k times its size, each lump at `lumps` of its own (so it breathes unevenly), the mouth m wide."""
        lumps = k if lumps is None else lumps
        q = {"body": {"scale": (k, k, k)}, "mouth": {"scale": (m, m, 1)}}
        for i in range(len(LUMPS)):
            q[f"puff{i}"] = {"scale": (lumps,) * 3}
        return q
    K.moves(r, updraft="Updraft", bolt="Wind Bolt")
    K.act(r, "updraft", K.plus(swell(1.8, 1.15, 0.5), {"body": {"rot": (-10, 0, 0), "pos": (0, 0.8, 1)}}), K.plus(swell(0.8, 0.9, 2.2), {"body": {"rot": (12, 0, 0), "pos": (0, 0, 2.5)}}),
          w=1.1, length=0.5, t_hit=0.1, over=0.05, open_pose=K.plus(swell(0.68, 0.85, 1.3), {"body": {"rot": (16, 0, 6), "pos": (0, -2, 0)}}))
    K.act(r, "bolt", K.plus(swell(1.3, 1.1, 0.6), {"body": {"rot": (-8, 0, 0), "pos": (0, 0.5, 1)}}), K.plus(swell(0.9, 0.95, 1.8), {"body": {"rot": (10, 0, 0), "pos": (0, 0, 2)}}),
          w=0.7, length=0.45, t_hit=0.07)
    K.plain(r, K.plus(swell(1.25), {"body": {"pos": (0, 0.5, 1.5)}}), K.plus(swell(1.4, 1.2), {"body": {"pos": (0, 0, -3.5)}}))
    K.loop(r, "idle", 5.0, lambda p: K.plus({"body": {"scale": (1 + 0.05 * s(p),) * 3, "pos": (0, 0.9 * s(p * 2, 0.2), 0), "rot": (2 * s(p, 0.3), 8 * s(p, 0.1), 0)},
                                             "mouth": {"scale": (1 + 0.3 * s(p * 2), 1 + 0.3 * s(p * 2), 1)}},
                                            {f"puff{i}": {"scale": (1 + 0.1 * s(p, i / 5.0),) * 3} for i in range(len(LUMPS))}), n=14)
    K.loop(r, "fly", 1.4, lambda p: K.plus(swell(1 + 0.04 * s(p * 2)), {"body": {"rot": (8, 0, 3 * s(p))}}))
    K.loop(r, "angry", 0.9, lambda p: K.plus(swell(1.25 + 0.12 * s(p * 2), 1.1, 0.7), {"body": (-6, 0, 0)}))
    K.flinch(r, K.plus(swell(0.82, 1.2), {"body": {"rot": (-12, 0, 16), "pos": (0, 0, 2)}}))
    wisp = {f"puff{i}": {"pos": (LUMPS[i][0] * 0.8, -LUMPS[i][1] * 0.8 + 2, LUMPS[i][2] * 0.8), "scale": (0.05,) * 3} for i in range(len(LUMPS))}
    K.keyed(r, "death", 0.9, [(0, {}), (0.12, swell(1.5, 1.2, 0.4)), (0.5, K.plus(wisp, {"body": {"scale": (0.6,) * 3, "pos": (0, 2, 0)}})),
                              (0.9, K.plus(wisp, {"body": {"scale": (0.05,) * 3, "pos": (0, 4, 0)}}))])
    return r


def paint_puffer(r, P):
    cloud(P, "coreP", 2.6, seed=1)
    cloud(P, "lumpP0", 3.2, seed=2)
    f = P.face("coreP", "north")
    for i in (1, 4):
        K.lit(P, "coreP", "north", i, 1, 1, 2, BOLT, HOT, a=255)
    P.paint("lips", STORM, 0.4)
    K.void(P, "lips", "north", margin=0, top=0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Balloonist (sky_raider): a small clawed thing hanging in a basket under a patched balloon. Its bolts lift you.
# Burst the balloon and it falls.

def balloonist():
    r = Rig("sky_raider", (64, 64))
    r.shadow = 0.6
    b = r.bone("body", (0, 18, 0))                       # the basket
    b.cube("basketB", -2.5, -2, -2.5, 5, 4, 5)
    b.cube("rimB", -3, -3, -3, 6, 1, 6)
    for k, (x, z) in enumerate(((-2.5, -2.5), (2.5, -2.5), (-2.5, 2.5), (2.5, 2.5))):
        b.cube(f"line{k}", x - 0.5, -9, z - 0.5, 1, 6, 1, share=None if k == 0 else "line0")
    sac = b.bone("sac", (0, -9, 0))                      # the balloon: it breathes
    sac.cube("envelope", -4, -9, -4, 8, 9, 8)
    sac.cube("neckB", -2, 0, -2, 4, 1, 4)
    head = b.bone("head", (0, -3, -0.5))
    head.cube("faceB", -1.5, -3, -1.5, 3, 3, 3)
    for side, nm in SIDES2:
        head.bone("ear_" + nm, (side * 1.5, -3, 0), (0, 0, side * 30)).cube("earB_" + nm, -0.5, -2, 0, 1, 2, 1, share=None if side > 0 else "earB_l")
        b.cube("clawB_" + nm, side * 2 - 0.5, -4, -3.5, 1, 2, 1, share=None if side > 0 else "clawB_l")
    b.bone("fore", (0, -3, -3), (0, 0, 0)).cube("tube", -0.5, -0.5, -4, 1, 1, 4)
    for k in range(2):
        b.bone(f"chain{k}", (-1.5 + 3 * k, 2, 0)).cube(f"ballast{k}", -1, 0, -1, 2, 3, 2, share=None if k == 0 else "ballast0")
    r.look = [("head", 0.6, 0.4)]
    r.pack()

    gas = lambda k: {"sac": {"scale": (k, k, k)}}  # noqa: E731
    lagc = {"sac": 0.06, "chain0": 0.1, "chain1": 0.12, "head": 0.04}
    K.moves(r, bolt="Grav-Bolt")
    K.act(r, "bolt", K.plus(gas(1.06), {"head": {"pos": (0, 1.5, 0), "rot": (-10, 0, 0)}, "fore": (-20, 0, 0), "body": {"rot": (-10, 0, 0), "pos": (0, 0.5, 1)}}),
          K.plus(gas(0.98), {"head": {"pos": (0, 1.0, 0)}, "fore": {"rot": (10, 0, 0), "pos": (0, 0, 1.5)}, "body": {"rot": (12, 0, 0), "pos": (0, 0, 2)}}), w=0.8, length=0.45, t_hit=0.07, lag=lagc)
    K.plain(r, {"body": {"rot": (20, 0, 0), "pos": (0, 0.6, 1.5)}}, {"body": {"rot": (-26, 0, 0), "pos": (0, -0.6, -3)}}, lag=lagc)
    K.loop(r, "idle", 5.0, lambda p: K.plus(gas(1 + 0.03 * s(p)), {"body": {"pos": (0, 1.0 * s(p * 2, 0.2), 0), "rot": (3 * s(p), 0, 3 * s(p, 0.25))}, "head": (0, 24 * (bump(p, 0.3, 0.05) - bump(p, 0.7, 0.05)), 0),
                                                                   "ear_l": (0, 0, 14 * bump(p, 0.3, 0.03)), "ear_r": (0, 0, -14 * bump(p, 0.7, 0.03))}), n=16)
    K.loop(r, "fly", 1.4, lambda p: K.plus(gas(1.02), {"body": {"rot": (8 + 2 * s(p * 2), 0, 4 * s(p))}, "sac": (-6, 0, 0)}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(gas(1.04), {"head": {"pos": (0, 1.2, 0), "rot": (0, 0, 6 * s(p * 2))}, "fore": (-10 + 8 * s(p * 2), 0, 0), "body": (0, 0, 5 * s(p))}))
    K.flinch(r, K.plus(gas(0.9), {"body": {"rot": (-14, 0, 20), "pos": (0, 0, 2)}, "head": {"pos": (0, -1, 0)}}))
    burst = K.plus(gas(0.08), {"sac": {"pos": (0, -4, 0)}})
    K.keyed(r, "death", 1.0, [(0, {}), (0.1, gas(1.2)), (0.22, K.plus(burst, {"body": (0, 0, 10)})), (0.6, K.on_floor(r, K.plus(burst, {"body": (10, 0, 40), "head": {"pos": (0, -1.5, 0)}}))),
                              (0.74, K.on_floor(r, K.plus(burst, {"body": (10, 0, 78)}))), (1.0, K.on_floor(r, K.plus(burst, {"body": (10, 0, 86)})))], lag=lagc)
    return r


def paint_balloonist(r, P):
    patches(P, "envelope", seed=3)
    for fname in K.SIDES:    # the net over it
        f = P.face("envelope", fname)
        for i, j in f.each():
            if (i + j) % 4 == 0 and (i - j) % 4 == 0:
                f.set(i, j, ROPE.at(3.2))
    P.paint("neckB", ROPE, 2.6, rim=0.5)
    P.paint("line0", ROPE, 3.0, p_bands(2, 0.8))
    P.paint("basketB", ROPE, 2.4, p_bands(2, 1.0), rim=0.4, ao=0.6)
    P.paint("rimB", ROPE, 3.4, rim=0.8)
    P.paint("faceB", STORM, 1.4, rim=0.5)
    for i in (0, 2):
        K.lit(P, "faceB", "north", i, 1, 1, 1, BOLT, HOT, a=255)
    P.paint("earB_l", STORM, 2.2)
    P.paint("clawB_l", STORM, 3.0)
    P.paint("tube", CANVAS, 3.0)
    K.lit(P, "tube", "north", 0, 0, 1, 1, BOLT, HOT, a=220)
    P.paint("ballast0", CANVAS, 3.2, p_strokes(1, 2, seed=4))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Thunderhead (thunderhead, the elite): a flat-topped block of dark cloud with a bright core, trailing rain. The
# core brightens, then lightning lands on the marked ground.

def thunderhead():
    r = Rig("thunderhead", (128, 64))
    r.shadow = 1.2
    b = r.bone("body", (0, 12, 0))
    b.cube("anvil", -8, -9, -7, 16, 3, 14)
    b.cube("tower", -6, -6, -5, 12, 7, 10)
    b.cube("baseT", -4.5, 1, -4, 9, 3, 8)
    b.bone("core", (0, -2, 0)).cube("coreT", -2, -2, -2, 4, 4, 4)
    for k in range(6):
        a = math.tau * k / 6
        b.bone(f"drip{k}", (4 * math.sin(a), 4, 3.4 * math.cos(a))).cube(f"rain{k}", -0.5, 0, 0, 1, 5 + (k % 3), 0, share=None if k < 3 else f"rain{k - 3}")
    r.pack()

    charge = lambda k, sw=1.0: {"core": {"scale": (k, k, k)}, "body": {"scale": (sw, sw, sw)}}  # noqa: E731
    lagc = {f"drip{k}": 0.1 + 0.02 * k for k in range(6)}
    K.moves(r, call="Lightning Call", static="Static")
    K.act(r, "call", K.plus(charge(2.0, 1.06), {"body": {"pos": (0, 1.2, 0)}}), K.plus(charge(2.3, 0.96), {"body": {"pos": (0, -1.6, 0)}}), w=1.3, length=0.55, t_hit=0.07, lag=lagc,
          open_pose=K.plus(charge(0.3, 0.94), {"body": {"pos": (0, -2, 0), "rot": (4, 0, 5)}}))
    K.act(r, "static", K.plus(charge(1.5), {"body": {"rot": (-10, 0, 0), "pos": (0, 0.5, 1)}}), K.plus(charge(2.0), {"body": {"rot": (10, 0, 0), "pos": (0, 0, 2)}}), w=0.7, length=0.45, t_hit=0.07, lag=lagc)
    K.plain(r, K.plus(charge(1.3), {"body": {"pos": (0, 1.5, 1)}}), K.plus(charge(1.8), {"body": {"pos": (0, -2.5, -2), "rot": (8, 0, 0)}}), lag=lagc)
    K.loop(r, "idle", 7.0, lambda p: K.plus(charge(1 + 0.12 * s(p * 2) + 0.5 * bump(p, 0.62, 0.02), 1 + 0.02 * s(p)), {"body": {"pos": (0, 0.8 * s(p * 2, 0.2), 0), "rot": (1.0 * s(p), 4 * s(p, 0.1), 1.0 * s(p, 0.3))}}),
           n=28)
    K.loop(r, "fly", 2.0, lambda p: K.plus(charge(1.1), {"body": {"rot": (5, 0, 2 * s(p))}}))
    K.loop(r, "angry", 1.0, lambda p: K.plus(charge(1.5 + 0.3 * s(p * 3), 1.03), {"body": (-3, 0, 2 * s(p))}))
    K.flinch(r, K.plus(charge(0.5, 0.95), {"body": {"rot": (-8, 0, 10), "pos": (0, 0, 2)}}))
    K.keyed(r, "death", 1.4, [(0, {}), (0.12, charge(2.2, 1.08)), (0.4, K.plus(charge(0.2, 0.9), {"body": {"pos": (0, -3, 0), "rot": (6, 0, 8)}})),
                              (1.0, K.plus({"core": {"scale": (0.02,) * 3}, "body": {"scale": (1.2, 0.3, 1.2), "pos": (0, -9, 0)}})), (1.4, K.plus({"core": {"scale": (0.02,) * 3}, "body": {"scale": (1.3, 0.06, 1.3), "pos": (0, -11, 0)}}))],
            lag=lagc)
    return r


def paint_thunderhead(r, P):
    cloud(P, "anvil", 2.8, seed=1)
    cloud(P, "tower", 2.2, seed=2)
    cloud(P, "baseT", 1.6, seed=3)
    for nm in ("tower", "baseT"):    # rifts in it, the core showing through
        for fname in K.SIDES:
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            x = 1 + K._h(nm, fname) % max(1, f.w - 2)
            for j in range(1, f.h - 1):
                x = max(1, min(f.w - 2, x + K._h(nm, fname, j) % 3 - 1))
                f.set(x, j, hexc(BOLT))
                g.set(x, j, hexc(BOLT, 170))
    K.lit_cube(P, "coreT", BOLT, HOT, a=255, hot=lambda fn, i, j, f: True)
    for k in range(3):
        for fname in ("north", "south"):
            f = P.face(f"rain{k}", fname)
            for i, j in f.each():
                f.set(i, j, STORM.at(4.6) if (j + k) % 3 else (0, 0, 0, 0))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Gale (the_gale, the Warden): a turning column of wind with a wreck in it: sails, rope, a ship's wheel at its
# heart. The wheel is what you hit. The wind opens when it has just thrown you.

RINGS = [(-13, 7, 3, 0), (-2, 10, 4, 30), (9, 8, 3, 60)]     # (height, radius, pieces, turn at rest): three rings of wreck


def gale():
    r = Rig("the_gale", (128, 128))
    r.shadow = 1.4
    b = r.bone("body", (0, 8, 0))
    wh = b.bone("wheel", (0, 0, 0))
    wh.cube("rimW", -5, -5, -0.5, 10, 10, 1)
    wh.cube("hubW", -1.5, -1.5, -1.5, 3, 3, 3)
    wh.bone("wheel_b", (0, 0, 0), (0, 0, 45)).cube("spokes", -6, -0.5, -0.4, 12, 1, 1)
    wh.bone("wheel_c", (0, 0, 0), (0, 0, -45)).cube("spokes2", -6, -0.5, -0.3, 12, 1, 1, share="spokes")
    for k, (y, rad, n, turn) in enumerate(RINGS):
        ring = b.bone(f"ring{k}", (0, y, 0), (0, turn, 0))
        for i in range(n):
            a = math.tau * i / n
            piece = ring.bone(f"wreck{k}{i}", (rad * math.sin(a), 0, rad * math.cos(a)), (0, math.degrees(a) + 90, 8 * (i - 1)))
            kind = (k + i) % 3
            if kind == 0:      # a rag of sail
                piece.cube(f"sail{k}{i}", -4, -4, 0, 8, 9, 0, share=None if (k, i) == (0, 0) else "sail00")
            elif kind == 1:    # a plank
                piece.cube(f"plank{k}{i}", -4, -1, -0.5, 8, 2, 1, share=None if (k, i) == (0, 1) else "plank01")
            else:              # a coil of rope
                piece.cube(f"coil{k}{i}", -2, -2, -0.5, 4, 4, 1, share=None if (k, i) == (0, 2) else "coil02")
            # between the pieces, the wind itself: a streak of it you can see, lying along the ring
            b2 = a + math.pi / n
            ring.bone(f"gust{k}{i}", (rad * 0.92 * math.sin(b2), (i - 1) * 1.5, rad * 0.92 * math.cos(b2)), (0, math.degrees(b2) + 90, 0)).cube(
                f"streak{k}{i}", -5, -0.5, 0, 10, 1, 0, share=None if (k, i) == (0, 0) else "streak00")
    r.pack()

    spins = {"ring0": (1, 360), "ring1": (1, -360), "ring2": (1, 720), "wheel": (2, 45)}
    wind = lambda k0, k1, k2: {"ring0": {"scale": (k0, 1, k0)}, "ring1": {"scale": (k1, 1, k1)}, "ring2": {"scale": (k2, 1, k2)}}  # noqa: E731  (each ring drawn in or thrown out)
    K.moves(r, force="Gale Force", trap="Updraft Trap", spears="Wind Spears")
    K.act(r, "force", K.plus(wind(0.6, 0.55, 0.6), {"ring0": (0, 200, 0), "ring1": (0, -200, 0), "ring2": (0, 300, 0), "wheel": (0, 0, -90), "body": {"pos": (0, 1.5, 1)}}),
          K.plus(wind(1.5, 1.6, 1.5), {"ring0": (0, 520, 0), "ring1": (0, -520, 0), "ring2": (0, 700, 0), "wheel": (0, 0, 180), "body": {"pos": (0, -1, -2)}}), w=1.3, length=0.6, t_hit=0.1,
          open_pose=K.plus(wind(1.7, 1.8, 1.7), {"ring0": {"rot": (8, 560, 0)}, "ring1": {"rot": (-6, -560, 0)}, "ring2": {"rot": (10, 740, 0)}, "wheel": (0, 0, 225), "body": {"pos": (0, -2, 0)}}))
    K.act(r, "trap", K.plus(wind(0.8, 0.8, 0.7), {"ring0": {"pos": (0, -3, 0), "rot": (0, 120, 0)}, "ring2": {"pos": (0, 3, 0), "rot": (0, 180, 0)}, "body": {"pos": (0, -1.5, 0)}}),
          K.plus(wind(1.1, 1.2, 1.3), {"ring0": {"pos": (0, 4, 0), "rot": (0, 360, 0)}, "ring1": {"pos": (0, 3, 0), "rot": (0, -240, 0)}, "ring2": {"pos": (0, 5, 0), "rot": (0, 540, 0)}, "body": {"pos": (0, 2.5, 0)}}),
          w=1.0, length=0.6, t_hit=0.12, rest={"ring0": (0, 360, 0), "ring1": (0, -360, 0), "ring2": (0, 720, 0)})
    K.act(r, "spears", K.plus(wind(0.9, 0.9, 0.9), {"wheel": (0, 0, 180), "body": {"rot": (-8, 0, 0), "pos": (0, 0.5, 1)}}), K.plus(wind(1.1, 1.1, 1.1), {"wheel": (0, 0, 450), "body": {"rot": (8, 0, 0), "pos": (0, 0, 2)}}),
          w=0.8, length=0.5, t_hit=0.08, rest={"wheel": (0, 0, 360)})
    K.plain(r, K.plus(wind(0.8, 0.8, 0.8), {"body": {"rot": (0, 0, -14), "pos": (0, 0.5, 1)}}), K.plus(wind(1.3, 1.3, 1.3), {"ring1": (0, -180, 0), "body": {"rot": (0, 0, 16), "pos": (0, 0, -3)}}),
            heavy=(K.plus(wind(0.7, 0.7, 0.7), {"body": {"pos": (0, 2, 1)}}), K.plus(wind(1.5, 1.5, 1.5), {"ring0": (0, 180, 0), "ring2": (0, 360, 0), "body": {"pos": (0, -2, -2)}})))
    K.turning(r, "idle", 4.0, lambda p: K.plus(wind(1 + 0.06 * s(p), 1 + 0.06 * s(p, 0.3), 1 + 0.06 * s(p, 0.6)), {"body": {"pos": (0, 1.0 * s(p * 2, 0.2), 0), "rot": (1.5 * s(p), 0, 1.5 * s(p, 0.25))}}), spins, n=16)
    K.turning(r, "fly", 2.0, lambda p: K.plus(wind(1.05, 1.05, 1.05), {"body": {"rot": (8, 0, 3 * s(p))}}), spins, n=16)
    K.turning(r, "angry", 1.2, lambda p: K.plus(wind(0.85 + 0.1 * s(p * 2), 0.85 + 0.1 * s(p * 2, 0.3), 0.85 + 0.1 * s(p * 2, 0.6)), {"body": (-4, 0, 0)}), spins, n=16)
    K.flinch(r, K.plus(wind(1.3, 1.35, 1.3), {"ring0": (10, -30, 0), "ring1": (-8, 30, 0), "ring2": (12, -40, 0), "wheel": (0, 0, -30), "body": {"rot": (-6, 0, 8), "pos": (0, 0, 2)}}))
    fallen = {}
    for k, (y, rad, n, turn) in enumerate(RINGS):
        fallen[f"ring{k}"] = {"scale": (1.6, 1, 1.6), "pos": (0, -(14.5 - y) + k * 0.4, 0), "rot": (0, 40 + 60 * k, 0)}
        for i in range(n):
            fallen[f"wreck{k}{i}"] = (80, 0, 20 * (i - 1))
    K.keyed(r, "death", 1.8, [(0, {}), (0.2, K.plus(wind(0.6, 0.6, 0.6), {"wheel": (0, 0, 90)})), (0.6, K.plus(wind(1.8, 1.9, 1.8), {"ring0": (14, 200, 0), "ring1": (-12, -200, 0), "ring2": (16, 300, 0), "wheel": (0, 0, 200)})),
                              (1.3, K.plus(fallen, {"wheel": {"rot": (84, 0, 250), "pos": (0, -14, 0)}})), (1.8, K.plus(fallen, {"wheel": {"rot": (88, 0, 256), "pos": (0, -14.5, 0)}}))])
    return r


def paint_gale(r, P):
    P.paint("rimW", ROPE, 2.6, rim=0.6)
    for fname in ("north", "south"):   # a ship's wheel: a ring of wood, daylight inside it
        f = P.face("rimW", fname)
        for i, j in f.each():
            d = math.hypot(i - 4.5, j - 4.5)
            if d > 5.2 or d < 3.2:
                f.set(i, j, (0, 0, 0, 0))
            else:
                f.set(i, j, ROPE.at(2.2 + 0.8 * (d > 4.2)))
    P.paint("hubW", ROPE, 3.4, rim=0.9)
    for fname in ("north", "south"):
        K.lit(P, "hubW", fname, 1, 1, 1, 1, BOLT, HOT, a=255)
    P.paint("spokes", ROPE, 3.0, p_bands(3, 0.6, vertical=True))
    for fname in ("north", "south"):
        f = P.face("sail00", fname)
        for i, j in f.each():
            torn = (j == f.h - 1 and i % 2 == 0) or (i == f.w - 1 and j % 3 == 0) or (j > f.h - 3 and i < 2)
            f.set(i, j, (0, 0, 0, 0) if torn else CANVAS.at(2.6 + 0.5 * (j % 3 == 0) - 0.3 * (i % 4 == 0)))
    for fname in ("north", "south"):
        f = P.face("streak00", fname)
        for i, j in f.each():
            f.set(i, j, STORM.at(4.4 - 0.25 * abs(i - 4.5)) if i % 4 != 3 else (0, 0, 0, 0))
    P.paint("plank01", ROPE, 1.8, p_strokes(4, 1, seed=3), rim=0.4)
    P.paint("coil02", ROPE, 3.0, rim=0.5)
    for fname in ("north", "south"):
        f = P.face("coil02", fname)
        f.set(1, 1, (0, 0, 0, 0))
        f.set(2, 2, (0, 0, 0, 0))
        f.set(1, 2, (0, 0, 0, 0))
        f.set(2, 1, (0, 0, 0, 0))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
WIND = [("entity.breeze.idle_air", 0.5, 0.7)]
MONSTERS = [
    K.Monster("gale_harpy", "The Kite", kite, paint_kite, kind="floater", box=(0.9, 0.5), lift=8,
              motion={"fly": K.flight(fast=5, bank=0.6, tilt=0.4, climb=0.6)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=1.8, springGain=1.5),
              voice={"ambient": [("entity.phantom.ambient", 0.5, 1.3), ("entity.phantom.flap", 0.4, 1.2)], "hurt": [("entity.phantom.hurt", 0.7, 1.3), ("block.wool.hit", 0.5, 1.2)],
                     "death": [("entity.phantom.death", 0.8, 1.3)]}),
    K.Monster("cloud_breeze", "The Puffer", puffer, paint_puffer, kind="floater", box=(0.7, 0.7), lift=4, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.2, tilt=0.2, climb=0.4)},
              life=dict(STILL, temperament="spirit", breath=0.5, breathDepth=1.6, idles={"settle": 1}),
              voice={"ambient": WIND + [("entity.breeze.inhale", 0.3, 1.2)], "hurt": [("entity.breeze.hurt", 0.7, 1.1)], "death": [("entity.breeze.death", 0.8, 1.1)]}),
    K.Monster("sky_raider", "The Balloonist", balloonist, paint_balloonist, kind="floater", box=(0.7, 1.4), lift=2, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.25, tilt=0.2, climb=0.4)},
              life=dict(STILL, temperament="monster", breath=0.35, breathDepth=1.0, idles={"look": 3, "settle": 1}, arms=0.0, headSpeed=6.0, springHz=1.2, springGain=1.5),
              voice={"ambient": [("entity.bat.ambient", 0.4, 0.7), ("block.wool.step", 0.4, 0.8)], "hurt": [("entity.bat.hurt", 0.6, 0.7), ("block.wool.hit", 0.8, 0.9)],
                     "death": [("entity.puffer_fish.blow_out", 0.9, 0.7), ("entity.bat.death", 0.6, 0.7)]}),
    K.Monster("thunderhead", "The Thunderhead", thunderhead, paint_thunderhead, kind="floater", box=(1.2, 1.0), lift=4, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.1, tilt=0.12, climb=0.3)},
              life=dict(STILL, temperament="spirit", breath=0.3, breathDepth=0.8, idles={"settle": 1}, mass=2.4, springHz=1.2, springGain=1.4),
              voice={"ambient": [("weather.rain", 0.25, 0.8), ("entity.lightning_bolt.thunder", 0.06, 1.6)], "hurt": [("entity.breeze.hurt", 0.7, 0.6), ("block.wool.hit", 0.8, 0.6)],
                     "death": [("entity.breeze.death", 0.8, 0.6), ("entity.lightning_bolt.thunder", 0.15, 1.2)]}),
    K.Monster("the_gale", "The Gale", gale, paint_gale, kind="floater", box=(1.4, 2.4), lift=1, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.12, tilt=0.14, climb=0.3)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, mass=3.0),
              voice={"ambient": WIND + [("entity.breeze.wind_burst", 0.2, 0.6), ("block.wood.step", 0.4, 0.6)], "hurt": [("block.wood.hit", 1.0, 0.6), ("entity.breeze.hurt", 0.6, 0.5)],
                     "death": [("block.wood.break", 1.0, 0.5), ("entity.breeze.death", 0.8, 0.5)]}),
]
