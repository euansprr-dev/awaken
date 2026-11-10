"""The Hive's monsters (SPEC §60). Room: dark red-brown paper and comb in orange light. So they are pale wax and black
chitin, and each carries honey gold lit inside it."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_strokes, s, c, bump

CHITIN = Ramp("#0e0a08", "#1a120c", "#2a1c12", "#3c2a1a", "#523a24", "#6c4e30")
WAX = Ramp("#7a6434", "#a08848", "#c4ac64", "#dcc884", "#eedea6", "#f8f0cc")
PAPER = Ramp("#4a3e32", "#64564a", "#807060", "#9c8c78", "#b8a890", "#d0c2aa")
AMBER = Ramp("#7a3c06", "#b05e0a", "#dc8a14", "#f4b030", "#ffd060", "#ffeeb0")
GOLD, HOT = "#ffb020", "#fff0b0"
SIDES2 = ((1, "l"), (-1, "r"))


def six_legs(body, hip_y, xs, zs, upper, lower, rise=32, yaws=(36, 0, -36), thick=1):
    """Three pairs of crawler's legs off a body bone (marked with K.at): front to back, sharing one pair of textures."""
    for side, nm in SIDES2:
        for k in range(3):
            first = side > 0 and k == 0
            K.splay_leg(body, f"{k}_{nm}", side, (xs[k], hip_y, zs[k]), upper, lower, rise, yaws[k], thick=thick,
                        share=None if first else ("thigh0_l", "shinb0_l"))


def crawl(r, rm, **over):
    g = dict(duty=(0.74, 0.54), lift=(2.2, 3.4), crouch=0.6, swing=0.22, bob=0.3, roll=0.03, rear=1.0, speed=0.55)
    g.update(over)
    return K.gait("ripple-alternate", rm, **g)


TRIPOD = [("leg0_l", 0.0, 1), ("leg1_l", 0.5, 1), ("leg2_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_r", 0.0, -1), ("leg2_r", 0.5, -1)]
SIX = ("0_l", "0_r", "1_l", "1_r", "2_l", "2_r")


# ---------------------------------------------------------------------------------------------------------------------
# The Stinger (drone_bee): hardly a body at all, one long barbed sting hung under four narrow wings. It curls the sting
# under itself before it dives; a dive that misses breaks the sting off, and it lies buzzing until it grows another.

def stinger():
    r = Rig("drone_bee", (64, 32))
    r.shadow = 0.3
    body = r.bone("body", (0, 12, 0))
    body.cube("thorax", -2, -2, -2, 4, 3, 4)
    head = body.bone("head", (0, -1, -2), (20, 0, 0))
    head.cube("mask", -1.5, -1.5, -2, 3, 3, 2)
    for side, nm in SIDES2:
        head.bone("feeler_" + nm, (side * 1, -1.5, -2), (-40, side * -20, 0)).cube("feeler_" + nm, -0.5, -3, -0.5, 1, 3, 1,
                                                                                share=None if side > 0 else "feeler_l")
    ab = body.bone("abdomen", (0, 0, 2), (-58, 0, 0))   # (a part that points back turns down with a negative x turn)
    ab.cube("ab0", -1.5, -1.5, 0, 3, 3, 4)
    ab.cube("ab1", -1, -1, 4, 2, 2, 3)
    st = ab.bone("stinger", (0, 0, 7))
    st.cube("sting", -0.5, -0.5, 0, 1, 1, 9)
    st.cube("barb", -1.5, -0.5, 5, 3, 1, 1)
    for k, (z, yaw) in enumerate(((-1.5, 18), (1.0, -14))):
        for side, nm in SIDES2:
            body.bone(f"wing{k}_{nm}", (side * 2, -2, z), (0, side * yaw, side * -8)).cube(
                f"wing{k}_{nm}", 0 if side > 0 else -9, 0, -1.5, 9, 0, 3, mirror=side < 0, share=None if (k == 0 and side > 0) else "wing0_l")
    r.look = [("head", 0.5, 0.3)]
    r.pack()

    K.moves(r, dive="Stinger Dive")
    wind = {"body": {"rot": (-24, 0, 0), "pos": (0, 2, 2)}, "abdomen": (-86, 0, 0), "head": (16, 0, 0)}
    hit = {"body": {"rot": (14, 0, 0), "pos": (0, -2, -5)}, "abdomen": (-64, 0, 0), "stinger": {"pos": (0, 0, 2)}}
    snapped = K.on_floor(r, {"body": (10, 0, 150), "abdomen": (-30, 0, 0), "stinger": {"scale": (0.02, 0.02, 0.02)},
                             "wing0_l": (0, 0, 40), "wing0_r": (0, 0, -20), "wing1_l": (0, 0, 30), "wing1_r": (0, 0, -30)})
    K.act(r, "dive", wind, hit, w=0.7, length=0.4, t_hit=0.07, stuck=snapped)
    K.keyed(r, "dive_open", 2.6, [(t, K.plus(snapped, {"abdomen": (a, 0, 0), "wing0_l": (0, 0, w_), "wing1_r": (0, 0, -w_)}))
                                  for t, a, w_ in ((0, 0, 0), (0.3, 14, 20), (0.6, -6, -10), (1.0, 10, 24), (1.5, -4, 0), (2.0, 8, 16), (2.6, 0, 0))])
    K.plain(r, {"body": {"rot": (-14, 0, 0), "pos": (0, 1, 1.5)}, "abdomen": (-50, 0, 0)}, {"body": {"rot": (10, 0, 0), "pos": (0, -1, -3.5)}, "abdomen": (-70, 0, 0)})
    K.loop(r, "idle", 3.0, lambda p: {"body": {"rot": (3 * s(p, 0.1), 0, 3 * s(p)), "pos": (0, 0.8 * s(p * 2), 0)}, "abdomen": (5 * s(p * 3), 0, 0),
                                      "head": (0, 10 * s(p, 0.3), 0), "feeler_l": (8 * s(p * 2), 0, 0), "feeler_r": (8 * s(p * 2, 0.5), 0, 0)})
    # the wings: a blur, front and back pairs half a beat apart
    K.loop(r, "fly", 0.1, lambda p: {f"wing{k}_{nm}": (0, 0, sd * (-6 - 34 * s(p, 0.5 * k))) for k in (0, 1) for sd, nm in SIDES2}, n=4)
    K.loop(r, "angry", 1.0, lambda p: {"body": (-10 + 3 * s(p * 2), 0, 0), "abdomen": (-34 + 10 * s(p * 4), 0, 0)})
    K.flinch(r, {"body": {"rot": (20, 0, 30), "pos": (0, 0, 2)}, "abdomen": (30, 0, 0)})
    K.keyed(r, "death", 1.0, [
        (0.0, {}), (0.12, {"body": (-30, 0, 20), "abdomen": (40, 0, 0)}),
        (0.4, {"body": {"rot": (20, 0, 150), "pos": (0, -4, 0)}, "abdomen": (-70, 0, 0), "wing0_l": (0, 0, 60), "wing1_r": (0, 0, -60)}),
        (1.0, {"body": {"rot": (30, 0, 180), "pos": (0, -8, 0)}, "abdomen": (-110, 0, 0), "wing0_l": (0, 0, 70), "wing0_r": (0, 0, -70),
               "wing1_l": (0, 0, 70), "wing1_r": (0, 0, -70), "feeler_l": (60, 0, 0), "feeler_r": (60, 0, 0)})])
    return r


def paint_stinger(r, P):
    P.paint("thorax", CHITIN, 2.2, rim=0.8)
    P.paint("mask", WAX, 3.6, rim=0.6)
    K.void(P, "mask", "north", margin=1, top=1)
    P.paint("feeler_l", CHITIN, 2.6)
    P.paint("ab0", CHITIN, 2.0, p_bands(2, 1.4), rim=0.6)
    P.paint("ab1", CHITIN, 1.6, p_bands(2, 1.4))
    for nm in ("ab0", "ab1"):  # the honey shows between its plates
        for fname in ("top", "west", "east"):
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                if (i if fname == "top" else i) % 2 == 1 and (fname != "top" or 0 < j):
                    cc = hexc(GOLD)
                    if fname == "top" and (j % 2 == 0):
                        continue
                    f.set(i, j, cc)
                    g.set(i, j, cc[:3] + (200,))
    P.paint("sting", Ramp("#3a2a1a", "#7a5a2a", "#c89a40", "#f0d080"), 2.2)
    P.paint("barb", CHITIN, 3.2)
    for fname in ("top", "bottom"):
        f = P.face("wing0_l", fname)
        for i, j in f.each():
            f.set(i, j, hexc("#e8dcc0", 150) if j == 1 or i % 3 == 0 else hexc("#d4c8a8", 90))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Grub (hive_larva): fat, pale, its gut glowing through its skin. It humps along like an inchworm and arches right
# up before it jumps.

def grub():
    r = Rig("hive_larva", (64, 32))
    r.shadow = 0.35
    body = r.bone("body", (0, 21, 0))
    body.cube("seg2", -3, -3, -2, 6, 6, 4)
    f1 = body.bone("front1", (0, 0, -2))
    f1.cube("seg1", -2.5, -2.5, -4, 5, 5, 4)
    head = f1.bone("head", (0, 0.5, -4))
    head.cube("cap", -2, -2, -3, 4, 4, 3)
    for side, nm in SIDES2:
        head.bone("jaw_" + nm, (side * 1, 1, -3), (0, side * 12, 0)).cube("jaw_" + nm, -0.5, -0.5, -2, 1, 1, 2, share=None if side > 0 else "jaw_l")
    b1 = body.bone("back1", (0, 0, 2))
    b1.cube("seg3", -2.5, -2.5, 0, 5, 5, 4)
    b2 = b1.bone("back2", (0, 0.5, 4))
    b2.cube("seg4", -2, -2, 0, 4, 4, 3)
    r.look = [("head", 0.4, 0.2)]
    r.pack()

    def arch(a, **more):
        """Humped by `a` (1 = a full inchworm's arch), resting on the floor."""
        return K.on_floor(r, dict({"front1": (28 * a, 0, 0), "head": (16 * a, 0, 0), "back1": (-28 * a, 0, 0), "back2": (-16 * a, 0, 0)}, **more))

    K.moves(r, leap="Wriggle")
    K.act(r, "leap", arch(1.5, body=(-12, 0, 0)), {"front1": (-12, 0, 0), "head": (-10, 0, 0), "back1": (10, 0, 0), "back2": (8, 0, 0), "body": {"pos": (0, 2, -2)}},
          w=0.5, length=0.4, t_hit=0.07)
    K.plain(r, arch(0.7, jaw_l=(0, 30, 0), jaw_r=(0, -30, 0)), {"front1": (-14, 0, 0), "head": (-12, 0, 0), "jaw_l": (0, -14, 0), "jaw_r": (0, 14, 0)})
    K.loop(r, "idle", 4.0, lambda p: arch(0.08 + 0.06 * s(p), head=(4 * s(p * 2), 8 * s(p, 0.2), 0), jaw_l=(0, 10 * bump(p, 0.4), 0),
                                          jaw_r=(0, -10 * bump(p, 0.4), 0), back2=(0, 6 * s(p, 0.5), 0)))
    # the inchworm: the back gathers up under an arch, the front reaches out of it
    K.loop(r, "walk", 1.0, lambda p: arch(0.5 - 0.5 * c(p), body={"rot": (4 * s(p), 0, 0)}), n=10)
    K.loop(r, "run", 0.6, lambda p: arch(0.65 - 0.65 * c(p), body={"rot": (6 * s(p), 0, 0)}), n=10)
    K.loop(r, "angry", 1.2, lambda p: arch(0.5 + 0.1 * s(p * 2), head=(-30, 0, 0), jaw_l=(0, 26 + 8 * s(p * 4), 0), jaw_r=(0, -26 - 8 * s(p * 4), 0)))
    K.flinch(r, arch(0.9, body=(0, 0, 20)))
    K.keyed(r, "death", 0.9, [(0, {}), (0.12, arch(1.3)), (0.4, K.on_floor(r, {"body": (0, 0, 80), "front1": (0, 40, 0), "back1": (0, -40, 0)})),
                              (0.9, K.on_floor(r, {"body": {"rot": (0, 0, 96), "scale": (1.1, 0.8, 1.1)}, "front1": (0, 58, 0), "head": (0, 30, 0),
                                                   "back1": (0, -58, 0), "back2": (0, -30, 0)}))])
    return r


def paint_grub(r, P):
    for k, nm in enumerate(("seg1", "seg2", "seg3", "seg4")):
        P.paint(nm, WAX, 3.2 - 0.2 * k, p_bands(2, 0.5, vertical=True), rim=0.5, ao=0.8)
        f, g = P.face(nm, "top"), P.face(nm, "top", glow=True)  # the gut, lit amber down its back
        for i, j in f.each():
            if abs(i - (f.w - 1) / 2) < 1.2:
                cc = hexc(GOLD if (j + k) % 2 else "#ffcc60")
                f.set(i, j, cc)
                g.set(i, j, cc[:3] + (190,))
        for fname in ("west", "east"):
            g = P.face(nm, fname, glow=True)
            fs = P.face(nm, fname)
            for i, j in fs.each():
                if j == 1 and i % 2 == 0:
                    fs.set(i, j, hexc("#f0c060"))
                    g.set(i, j, hexc(GOLD, 110))
    P.paint("cap", CHITIN, 3.0, rim=0.9)
    P.paint("jaw_l", CHITIN, 4.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Honeypot (honey_slime): a small black ant under a swollen amber belly it carries like a lamp. It lands sticky and
# stands sloshing afterwards.

def honeypot():
    r = Rig("honey_slime", (64, 64))
    r.shadow = 0.6
    body = K.at(r.bone("body", (0, 19, -1)), 19)
    body.cube("thorax", -2, -2, -3, 4, 3, 6)
    six_legs(body, 0.5, (2, 2, 2), (-2, 0, 2), 4, 7, rise=35)
    head = body.bone("head", (0, -1, -3), (14, 0, 0))
    head.cube("skull", -2, -2, -3, 4, 3, 3)
    for side, nm in SIDES2:
        head.bone("feeler_" + nm, (side * 1.5, -2, -3), (-50, side * -24, 0)).cube("feeler_" + nm, -0.5, -4, -0.5, 1, 4, 1, share=None if side > 0 else "feeler_l")
    ab = body.bone("abdomen", (0, -1, 3), (26, 0, 0))
    ab.cube("stalk", -1, -1, 0, 2, 2, 2)
    ab.cube("pot", -4, -7, 2, 8, 8, 8)
    ab.cube("potw", -5, -6, 3, 10, 6, 6)
    ab.cube("potd", -3, -8, 3, 6, 10, 6)
    r.look = [("head", 0.5, 0.3)]
    r.pack()

    K.moves(r, leap="Sticky Landing")
    K.act(r, "leap", {"body": {"pos": (0, -1.5, 1)}, "abdomen": (20, 0, 0), "head": (-12, 0, 0)},
          {"body": {"pos": (0, 2.5, -2), "rot": (-10, 0, 0)}, "abdomen": (-16, 0, 0)}, w=0.8, length=0.5, t_hit=0.08, plant=SIX, lag={"abdomen": 0.07},
          open_pose={"body": {"pos": (0, -1.0, 0)}, "abdomen": (-8, 0, 14)})
    K.keyed(r, "leap_open", 2.6, [(t, K.planted(r, {"body": {"pos": (0, -1.0, 0)}, "abdomen": (-8 + t * 3, 0, z)}, SIX))
                                  for t, z in ((0, 14), (0.3, -12), (0.62, 10), (0.96, -8), (1.34, 6), (1.8, -3), (2.6, 0))])
    K.plain(r, {"body": {"pos": (0, -0.5, 1.5)}, "head": (-16, 0, 0), "abdomen": (8, 0, 0)}, {"body": {"pos": (0, 0, -2.5)}, "head": (12, 0, 0), "abdomen": (-8, 0, 0)},
            plant=SIX, lag={"abdomen": 0.06})
    K.loop(r, "idle", 5.0, lambda p: {"abdomen": (2 * s(p * 2), 0, 3 * s(p)), "head": (3 * s(p, 0.2), 9 * bump(p, 0.5, 0.08), 0),
                                      "feeler_l": (10 * s(p * 2), 0, 0), "feeler_r": (10 * s(p * 2, 0.4), 0, 0)}, n=14)
    K.loop(r, "walk", 0.8, lambda p: {"abdomen": (3 * s(p * 2, 0.2), 0, 6 * s(p, 0.15)), "head": (2 * s(p * 2), 0, 0)})
    K.loop(r, "run", 0.5, lambda p: {"abdomen": (5 * s(p * 2, 0.2), 0, 9 * s(p, 0.15)), "head": (4 * s(p * 2), 0, 0), "body": (4, 0, 0)})
    K.fallback_walk(r, TRIPOD, axis="y")
    K.loop(r, "angry", 1.4, lambda p: {"abdomen": (14 + 4 * s(p * 2), 0, 0), "head": (-10, 0, 0), "body": {"pos": (0, -0.8, 0)}}, plant=SIX)
    K.flinch(r, {"body": {"pos": (0, 1, 1.5)}, "abdomen": (-10, 0, 14)}, plant=SIX)
    K.keyed(r, "death", 1.0, [
        (0, {}), (0.14, K.planted(r, {"body": {"pos": (0, 1.5, 0)}, "abdomen": {"rot": (16, 0, 0), "scale": (1.12, 1.12, 1.12)}}, SIX)),
        (0.5, K.on_floor(r, {"body": (0, 0, 60), "abdomen": {"rot": (20, 0, 20), "scale": (1.2, 0.7, 1.2)}, **{f"leg{t}": (0, 0, -50 if t.endswith("l") else 50) for t in SIX}})),
        (1.0, K.on_floor(r, {"body": (0, 0, 84), "abdomen": {"rot": (30, 0, 26), "scale": (1.3, 0.45, 1.3)}, "head": (20, 0, 0),
                             **{f"leg{t}": (0, 0, -70 if t.endswith("l") else 70) for t in SIX}, **{f"shin{t}": (0, 0, 40 if t.endswith("l") else -40) for t in SIX}}))])
    return r


def paint_honeypot(r, P):
    P.paint("thorax", CHITIN, 2.4, rim=0.8)
    P.paint("skull", CHITIN, 2.8, rim=0.8)
    P.paint("feeler_l", CHITIN, 3.0)
    P.paint("thigh0_l", CHITIN, 2.6)
    P.paint("shinb0_l", CHITIN, 2.0)
    P.paint("stalk", CHITIN, 2.0)
    for nm, base in (("pot", 3.2), ("potw", 2.8), ("potd", 3.0)):
        P.paint(nm, AMBER, base, p_blotch("pot" + nm, 5, 0.5), rim=0.9, ao=0.4)
        for fname in K.ALL:
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                edge = i in (0, f.w - 1) or j in (0, f.h - 1)
                col = f.get(i, j)
                g.set(i, j, col[:3] + (90 if edge else 205,))
    # the stretched plates of its back, dark bars across the honey
    for nm in ("pot", "potd"):
        f = P.face(nm, "top")
        for i in range(f.w):
            for j in (1, f.h - 2):
                f.set(i, j, CHITIN.at(2))
                P.face(nm, "top", glow=True).set(i, j, (0, 0, 0, 0))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Door (hive_guard, the elite): a broad low soldier whose head is a flat six-sided plate the size of the tunnel it
# plugs. Its face stops every blow from the front; it lunges from under it, and is open while it is stretched out.

PLATE = ((8, -11, 2), (12, -9, 3), (14, -6, 4), (12, -2, 3), (8, 1, 2))  # width, top, height: a hexagon in rows


def door():
    r = Rig("hive_guard", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 18, 1)), 18)
    body.cube("thorax", -5, -3, -4, 10, 5, 8)
    six_legs(body, 1.0, (5, 5, 5), (-3, 0, 3), 6, 9, rise=30, thick=2)
    ab = body.bone("abdomen", (0, -1, 4), (-8, 0, 0))
    ab.cube("gaster", -4, -3, 0, 8, 6, 8)
    ab.cube("gasterb", -3, -2, 8, 6, 4, 3)
    head = body.bone("head", (0, 0, -4), (8, 0, 0))
    head.cube("neckb", -3, -2, -2, 6, 4, 2)
    for k, (w, y, h) in enumerate(PLATE):
        head.cube(f"plate{k}", -w / 2, y + 3, -4, w, h, 2)
    for side, nm in SIDES2:
        head.bone("mandible_" + nm, (side * 2, 3, -3), (0, side * 6, 0)).cube("mandible_" + nm, -1 if side > 0 else 0, -1, -6, 1, 2, 6, mirror=side < 0,
                                                                              share=None if side > 0 else "mandible_l")
    r.look = [("head", 0.25, 0.15)]
    r.pack()

    K.moves(r, crush="Mandible Crush", seal="Wax Seal")
    jaws = lambda a: {"mandible_l": (0, a, 0), "mandible_r": (0, -a, 0)}  # noqa: E731
    K.act(r, "crush", dict(jaws(46), body={"pos": (0, -1.0, 2.5)}, head=(-36, 0, 0), abdomen=(10, 0, 0)),
          dict(jaws(-8), body={"pos": (0, 0.5, -5)}, head=(4, 0, 0), abdomen=(-8, 0, 0)), w=1.2, length=0.5, t_hit=0.08, plant=SIX,
          open_pose=dict(jaws(8), body={"pos": (0, -0.8, -3.5)}, head=(10, 0, 0), abdomen=(-4, 0, 0)))
    # Wax Seal: it rises on its legs and curls its tail under itself to spit
    K.act(r, "seal", {"abdomen": (-38, 0, 0), "body": {"pos": (0, 3.5, 0), "rot": (-10, 0, 0)}, "head": (-10, 0, 0)},
          {"abdomen": (-50, 0, 0), "body": {"pos": (0, 4.5, 1), "rot": (-14, 0, 0)}}, w=1.3, length=0.5, plant=SIX, lag={"abdomen": 0.05})
    K.plain(r, dict(jaws(34), head=(-18, 0, 0), body={"pos": (0, -0.5, 1.5)}), dict(jaws(-6), head=(3, 0, 0), body={"pos": (0, 0, -3)}), plant=SIX)
    K.loop(r, "idle", 6.0, lambda p: dict(jaws(3 + 3 * s(p * 2) + 12 * bump(p, 0.7, 0.03)), head=(1.5 * s(p), 4 * s(p, 0.3), 0),
                                          abdomen=(2 * s(p * 2), 0, 1.5 * s(p))), n=16)
    K.loop(r, "walk", 0.9, lambda p: {"head": (2 * s(p * 2), 0, 2 * s(p)), "abdomen": (2 * s(p * 2, 0.2), 0, 4 * s(p, 0.2))})
    K.loop(r, "run", 0.55, lambda p: {"head": (-6 + 3 * s(p * 2), 0, 3 * s(p)), "abdomen": (4 * s(p * 2, 0.2), 0, 6 * s(p, 0.2)), "body": (3, 0, 0)})
    K.fallback_walk(r, TRIPOD, axis="y")
    # angry it shuts the tunnel: the plate comes down square in front of it
    K.loop(r, "angry", 2.0, lambda p: dict(jaws(10 + 6 * s(p * 2)), head=(-8 + 1.5 * s(p * 2), 0, 0), body={"pos": (0, -1.2, 0)}), plant=SIX)
    K.flinch(r, {"body": {"pos": (0, 0.5, 1.5)}, "head": (-10, 0, 5), "abdomen": (8, 0, 6)}, plant=SIX)
    over = {"body": (0, 0, 150), "head": (30, 0, 0), "abdomen": (20, 0, 0), **{f"leg{t}": (0, 0, 36 if t.endswith("l") else -36) for t in SIX},
            **{f"shin{t}": (0, 0, 44 if t.endswith("l") else -44) for t in SIX}}
    K.keyed(r, "death", 1.4, [
        (0, {}), (0.16, K.planted(r, dict(jaws(50), body={"pos": (0, 2.5, 1)}, head=(-30, 0, 0)), SIX))]
        + K.fall(r, 0.16, K.planted(r, dict(jaws(50), body={"pos": (0, 2.5, 1)}, head=(-30, 0, 0)), SIX), 0.8, over, steps=4)
        + [(0.95, K.on_floor(r, K.plus(over, {"body": (0, 0, -8)}))), (1.4, K.on_floor(r, K.plus(over, jaws(20))))])
    return r


def paint_door(r, P):
    P.paint("thorax", CHITIN, 2.6, p_plates(5, 3, 0.8), rim=0.8)
    P.paint("gaster", CHITIN, 2.2, p_bands(3, 1.2), rim=0.7)
    P.paint("gasterb", CHITIN, 1.8, p_bands(3, 1.2))
    for nm in ("gaster", "gasterb"):
        f, g = P.face(nm, "top"), P.face(nm, "top", glow=True)
        for i, j in f.each():
            if j % 3 == 2 and 0 < i < f.w - 1:
                f.set(i, j, hexc(GOLD))
                g.set(i, j, hexc(GOLD, 170))
    P.paint("neckb", CHITIN, 2.0)
    for k, (w, y, h) in enumerate(PLATE):  # the door: pale wax with comb cut into it, a dark rim, gold in the middle cell
        nm = f"plate{k}"
        P.paint(nm, WAX, 3.4, rim=0.4, ao=0.2)
        f = P.face(nm, "north")
        for i, j in f.each():
            if i in (0, w - 1) or (k == 0 and j == 0) or (k == len(PLATE) - 1 and j == h - 1):
                f.set(i, j, CHITIN.at(3))
            elif (i + (y + j) // 2 * 2) % 4 == 0 or (y + j) % 4 == 0 and i % 4 == 2:
                f.set(i, j, WAX.at(1.4))
    K.lit(P, "plate2", "north", 5, 1, 4, 2, GOLD, HOT, a=230)
    P.paint("mandible_l", CHITIN, 3.6, rim=1.0)
    P.paint("thigh0_l", CHITIN, 2.8)
    P.paint("shinb0_l", CHITIN, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Swarm Mind (the_swarm_mind, the Warden): a paper nest carried through the air by a ring of drones, more pouring from
# its mouth. It swings like a flail under them.

RING = 6
TIERS = ((8, -4, 4), (12, -9, 5), (14, -14, 5), (12, -18, 4), (8, -21, 3))  # width, top, height from the mouth up


def swarm_mind():
    r = Rig("the_swarm_mind", (128, 128))
    r.shadow = 1.2
    body = r.bone("body", (0, 4, 0))          # the point the carriers hold: the nest hangs from it
    nest = body.bone("chest", (0, 2, 0))      # the nest swings under them (it breathes: the swarm inside)
    for k, (w, y, h) in enumerate(TIERS):
        nest.cube(f"tier{k}", -w / 2, y + 24, -w / 2, w, h, w)
    nest.cube("mouth", -3, 24, -3, 6, 2, 6)
    for k in range(3):
        a = k * 2.1
        nest.bone(f"tassel{k}", (3 * math.cos(a), 25, 3 * math.sin(a))).cube(f"tassel{k}", -0.5, 0, 0, 1, 5 + k, 0, share=None)
    for k in range(RING):
        a = math.tau * k / RING
        cx, cz = 9 * math.sin(a), -9 * math.cos(a)
        car = body.bone(f"carrier{k}", (cx, -2, cz), (0, -math.degrees(a), 0))
        car.cube(f"cbody{k}", -1, -1, -1.5, 2, 2, 4, share=None if k == 0 else "cbody0")
        car.cube(f"thread{k}", -0.5, 1, 0, 1, 7, 0, share=None if k == 0 else "thread0")
        for side, nm in SIDES2:
            car.bone(f"wing{k}_{nm}", (side * 1, -1, 0), (0, 0, side * -10)).cube(f"wing{k}_{nm}", 0 if side > 0 else -6, 0, -1, 6, 0, 2, mirror=side < 0,
                                                                                 share=None if (k == 0 and side > 0) else "wing0_l")
    r.pack()

    cars = lambda fn: {f"carrier{k}": fn(k) for k in range(RING)}  # noqa: E731
    K.moves(r, call="Call the Swarm", dive="Swarm Dive", rain="Honey Drop")
    # Call the Swarm: it tips its mouth toward you and shakes them out
    K.act(r, "call", {"chest": (-34, 0, 0), "body": {"pos": (0, 2, 0)}}, {"chest": {"rot": (-44, 0, 0), "scale": (1.12, 0.9, 1.12)}, "body": {"pos": (0, 0, 1)}},
          w=1.5, length=0.6, t_hit=0.1, after={"chest": {"rot": (-20, 0, 0), "scale": (0.94, 1.06, 0.94)}}, lag={f"tassel{k}": 0.08 for k in range(3)})
    # Swarm Dive: the carriers haul it back like a flail and swing it through you
    low = dict(cars(lambda k: {"pos": (0, 3 + 2 * (k % 2), 0), "rot": (0, 0, 20 if k % 2 else -20)}), chest=(12, 0, 16), body={"pos": (0, -14, 0)})
    K.act(r, "dive", {"chest": (38, 0, 0), "body": {"pos": (0, 3, 3), "rot": (-10, 0, 0)}}, {"chest": (-62, 0, 0), "body": {"pos": (0, -3, -6), "rot": (14, 0, 0)}},
          w=0.9, length=0.6, t_hit=0.12, stuck=low, open_pose=None, lag={f"tassel{k}": 0.08 for k in range(3)})
    # Honey Drop: it rises, swells and wrings itself out
    K.act(r, "rain", {"chest": {"scale": (1.16, 0.86, 1.16)}, "body": {"pos": (0, 5, 0)}}, {"chest": {"scale": (0.86, 1.2, 0.86)}, "body": {"pos": (0, 2, 0)}},
          w=1.7, length=0.6, t_hit=0.1, open_pose={"chest": {"rot": (0, 0, 8), "scale": (0.95, 1.04, 0.95)}, "body": {"pos": (0, -5, 0)}})
    K.plain(r, {"chest": (26, 0, 0), "body": {"pos": (0, 1.5, 2)}}, {"chest": (-46, 0, 0), "body": {"pos": (0, -1, -4)}},
            heavy=({"chest": (0, 0, 44), "body": {"pos": (0, 2, 0), "rot": (0, 0, -8)}}, {"chest": (0, 0, -60), "body": {"pos": (0, -1, 0), "rot": (0, 0, 10)}}),
            lag={f"tassel{k}": 0.08 for k in range(3)})

    def hang(p, sway=5.0, lift=1.0):
        d = cars(lambda k: {"pos": (0, lift * 1.2 * s(p * 2, k / RING), 0), "rot": (4 * s(p, k / RING), 0, 0)})
        d.update(chest=(sway * 0.7 * s(p, 0.1), 0, sway * s(p)), body={"pos": (0, 0.8 * s(p * 2), 0)})
        return d
    K.loop(r, "idle", 5.0, hang, n=14)
    K.loop(r, "fly", 0.1, lambda p: {f"wing{k}_{nm}": (0, 0, sd * (-4 - 36 * s(p, 0.17 * k))) for k in range(RING) for sd, nm in SIDES2}, n=4)
    K.loop(r, "angry", 1.6, lambda p: hang(p, 9.0, 2.0))
    K.flinch(r, dict(cars(lambda k: {"pos": (0, 2 if k % 2 else -1, 0)}), chest=(-14, 0, 22)))
    # they let go of it one after another; it drops, tips over and goes dark
    gone = lambda n: cars(lambda k: {"pos": (0, 10, 0), "scale": (0.02, 0.02, 0.02)} if k < n else {"pos": (0, 2, 0)})  # noqa: E731
    K.keyed(r, "death", 1.8, [
        (0, {}),
        (0.25, dict(gone(2), chest=(0, 0, 20), body={"pos": (0, -3, 0)})),
        (0.55, dict(gone(4), chest=(10, 0, -28), body={"pos": (0, -9, 0)})),
        (0.9, dict(gone(6), chest=(20, 0, 60), body={"pos": (0, -20, 0)})),
        (1.2, dict(gone(6), chest=(24, 0, 96), body={"pos": (0, -26, 0)})),
        (1.8, dict(gone(6), chest={"rot": (24, 0, 100), "scale": (1.04, 0.92, 1.04)}, body={"pos": (0, -26.5, 0)}))],
        lag={f"tassel{k}": 0.1 for k in range(3)})
    return r


def paint_swarm_mind(r, P):
    for k, (w, y, h) in enumerate(TIERS):  # nest paper laid in bands, each tier a tone
        P.paint(f"tier{k}", PAPER, 2.0 + (k % 2) * 0.9, p_bands(2, 0.7), rim=0.5, ao=0.9)
        f = P.face(f"tier{k}", "north")
        for i, j in f.each():
            if (i * 3 + j * 5 + k) % 11 == 0:
                for fname in K.SIDES:
                    P.face(f"tier{k}", fname).set(i, j, PAPER.at(0.6))
    P.paint("mouth", PAPER, 1.0, rim=0.2)
    f = P.face("mouth", "bottom")
    for i, j in f.each():
        f.set(i, j, PAPER.at(1.2) if i in (0, f.w - 1) or j in (0, f.h - 1) else hexc("#0a0604"))
    K.lit(P, "mouth", "bottom", 2, 2, 2, 2, GOLD, HOT, a=255)
    for fname in K.SIDES:  # light in the comb, seen through tears in the paper
        K.lit(P, "tier1", fname, 3 + (K._h(fname) % 5), 2, 1, 2, GOLD, HOT, a=210)
        K.lit(P, "tier2", fname, 2 + (K._h(fname, 2) % 8), 1, 2, 1, GOLD, HOT, a=210)
        K.lit(P, "tier3", fname, 4 + (K._h(fname, 3) % 5), 1, 1, 2, GOLD, HOT, a=190)
    for k in range(3):
        for fname in ("north", "south"):
            f = P.face(f"tassel{k}", fname)
            for i, j in f.each():
                f.set(i, j, PAPER.at(3.6 - j * 0.3))
    P.paint("cbody0", CHITIN, 2.6, p_bands(2, 1.6))
    g = P.face("cbody0", "south", glow=True)
    P.face("cbody0", "south").set(0, 1, hexc(GOLD))
    g.set(0, 1, hexc(GOLD, 220))
    for fname in ("north", "south"):
        f = P.face("thread0", fname)
        for i, j in f.each():
            f.set(i, j, hexc("#c8bca0", 200))
    for fname in ("top", "bottom"):
        f = P.face("wing0_l", fname)
        for i, j in f.each():
            f.set(i, j, hexc("#e8dcc0", 150) if i % 2 == 0 else hexc("#d4c8a8", 90))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
BUZZ = [("entity.bee.loop_aggressive", 0.3, 1.5)]
MONSTERS = [
    K.Monster("drone_bee", "The Stinger", stinger, paint_stinger, kind="floater", box=(0.6, 0.8), lift=6,
              motion={"fly": K.flight(wings=["wing0_l", "wing0_r", "wing1_l", "wing1_r"], heave=0.4, glide=0.0, fast=5, bank=0.45, tilt=0.4)},
              life=dict(STILL, temperament="insect"),
              voice={"ambient": BUZZ + [("entity.bee.loop", 0.3, 1.7)], "hurt": [("entity.bee.hurt", 0.7, 1.4)], "death": [("entity.bee.death", 0.8, 1.3)]}),
    K.Monster("hive_larva", "The Grub", grub, paint_grub, kind="coiler", box=(0.5, 0.45),
              motion={"body": K.body(headLevel=0.2, twist=0.3, slope=0.9, land=1.2, shift=0.1)},
              life=dict(STILL, temperament="insect", idles={"look": 2, "settle": 1}, bob=0.0, sway=0.0),
              voice={"ambient": [("entity.silverfish.ambient", 0.5, 0.6)], "hurt": [("entity.silverfish.hurt", 0.7, 0.6), ("entity.slime.hurt_small", 0.6, 0.9)],
                     "death": [("entity.slime.death_small", 0.8, 0.8), ("entity.silverfish.death", 0.6, 0.6)], "step": [("entity.slime.squish_small", 0.25, 1.2)]}),
    K.Monster("honey_slime", "The Honeypot", honeypot, paint_honeypot, kind="crawler", box=(0.8, 0.9),
              motion=lambda r: {"gait": crawl(r, K.room(r, K.legs_of(r), 0.6)), "body": K.body(headLevel=0.5, twist=0.25, slope=0.8, land=1.3, shift=0.6)},
              life=dict(STILL, temperament="insect", idles={"look": 2, "flick": 2, "settle": 2}, mass=1.2, springHz=2.0),
              voice={"ambient": [("block.honey_block.slide", 0.5, 0.8), ("entity.spider.ambient", 0.35, 1.5)], "hurt": [("block.honey_block.hit", 0.9, 0.8)],
                     "death": [("block.honey_block.break", 1.0, 0.7), ("entity.slime.death", 0.6, 0.8)], "step": [("entity.spider.step", 0.3, 1.5)]}),
    K.Monster("hive_guard", "The Door", door, paint_door, kind="crawler", box=(1.3, 1.0),
              motion=lambda r: {"gait": crawl(r, K.room(r, K.legs_of(r), 0.6), duty=(0.76, 0.56), lift=(2.6, 4.0), swing=0.26, bob=0.4),
                                "body": K.body(headLevel=0.6, twist=0.12, slope=0.8, land=1.2, shift=0.7)},
              life=dict(STILL, temperament="insect", idles={"look": 2, "settle": 3}, mass=2.0, headSpeed=5.0, springHz=1.8),
              voice={"ambient": [("entity.spider.ambient", 0.6, 0.6), ("block.beehive.work", 0.5, 0.6)], "hurt": [("entity.spider.hurt", 0.8, 0.6), ("block.honey_block.hit", 0.6, 0.6)],
                     "death": [("entity.spider.death", 0.9, 0.6)], "step": [("entity.spider.step", 0.45, 0.7)]}),
    K.Monster("the_swarm_mind", "The Swarm Mind", swarm_mind, paint_swarm_mind, kind="floater", box=(1.6, 2.0), lift=4, grounds=False,
              motion={"fly": K.flight(fast=4, bank=0.2, tilt=0.15, climb=0.3, level=0.0)},
              life=dict(STILL, temperament="spirit", breath=0.5, breathDepth=1.2, springHz=1.2, springGain=1.5, idles={"settle": 1}),
              voice={"ambient": [("entity.bee.loop_aggressive", 0.6, 0.6), ("block.beehive.work", 0.7, 0.5)], "hurt": [("entity.bee.hurt", 0.9, 0.6), ("block.beehive.shear", 0.7, 0.6)],
                     "death": [("entity.bee.death", 1.0, 0.5), ("block.beehive.exit", 1.0, 0.5)]}),
]
