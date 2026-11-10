"""The Fungal Hollow's monsters (SPEC §60). Room: dark, lit purple and yellow by its own mushrooms. So they are pale
cream and bone white, and each carries spore gold in its gills."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_strokes, p_fur, s, c, bump, SIDES2

FLESH = Ramp("#6a5c4c", "#8c7c68", "#ac9c84", "#c8baa0", "#e0d4bc", "#f2ead8")
CAP = Ramp("#5a4a5c", "#786478", "#988294", "#b8a2b0", "#d6c4cc", "#eee2e6")
BARK = Ramp("#1e1612", "#2e221a", "#423226", "#584434", "#705844", "#8a7058")
MOSS = Ramp("#22301c", "#34482a", "#4a643a", "#64844e")
GOLD, HOT = "#e8d04a", "#fff8c0"


def gills(P, cube, a=220):
    """The underside of a cap or a shelf: dark ribs with the gold between them."""
    f, g = P.face(cube, "bottom"), P.face(cube, "bottom", glow=True)
    for i, j in f.each():
        if i in (0, f.w - 1) or j in (0, f.h - 1):
            continue
        if (i + j) % 2 == 0:
            f.set(i, j, hexc(GOLD))
            g.set(i, j, hexc(GOLD, a))
        else:
            f.set(i, j, hexc("#3a2c20"))


# ---------------------------------------------------------------------------------------------------------------------
# The Sporeling (sporeling): a puffball that hops on three dangling root-toes, a slit of gold where its cap splits. It
# swells before it pounces, and bursts when it dies.

def sporeling():
    r = Rig("sporeling", (64, 32))
    r.shadow = 0.4
    body = r.bone("body", (0, 21, 0))
    body.cube("stem", -2, -1, -2, 4, 4, 4)
    cap = body.bone("head", (0, -1, 0))
    cap.cube("brim", -5, -3, -5, 10, 3, 10)
    cap.cube("dome", -4, -6, -4, 8, 3, 8)
    cap.cube("crown", -2, -7, -2, 4, 1, 4)
    for k in range(3):
        a = math.tau * k / 3 + 0.5
        body.bone(f"tassel{k}", (1.5 * math.sin(a), 3, 1.5 * math.cos(a))).cube(f"tassel{k}", -0.5, 0, -0.5, 1, 3, 1, share=None if k == 0 else "tassel0")
    r.pack()

    puff = lambda k: {"head": {"scale": (1 + k, 1 + 0.6 * k, 1 + k)}}  # noqa: E731
    K.moves(r, pounce="Pounce")
    K.act(r, "pounce", K.plus(puff(0.3), {"body": {"pos": (0, -1.5, 0), "scale": (1.15, 0.8, 1.15)}}),
          K.plus(puff(-0.12), {"body": {"pos": (0, 2, -1), "scale": (0.85, 1.3, 0.85), "rot": (-14, 0, 0)}}), w=0.5, length=0.4, t_hit=0.07)
    K.plain(r, K.plus(puff(0.18), {"body": {"scale": (1.1, 0.85, 1.1)}}), K.plus(puff(-0.08), {"body": {"pos": (0, 1, -1.5), "scale": (0.9, 1.2, 0.9)}}))
    K.loop(r, "idle", 3.0, lambda p: {"head": {"scale": (1 + 0.04 * s(p), 1 - 0.05 * s(p), 1 + 0.04 * s(p)), "rot": (3 * s(p, 0.3), 0, 3 * s(p * 2))},
                                      "body": {"scale": (1 + 0.03 * s(p, 0.5), 1 - 0.03 * s(p, 0.5), 1 + 0.03 * s(p, 0.5))}})
    K.loop(r, "walk", 0.5, lambda p: {"body": {"scale": (1 - 0.1 * s(p), 1 + 0.16 * s(p), 1 - 0.1 * s(p))}, "head": (8 * s(p, 0.2), 0, 0)})
    K.loop(r, "angry", 0.8, lambda p: K.plus(puff(0.1 + 0.05 * s(p * 2)), {"head": (0, 0, 5 * s(p))}))
    K.flinch(r, {"body": {"scale": (1.25, 0.7, 1.25), "rot": (0, 0, 14)}, "head": (-10, 0, 0)})
    K.keyed(r, "death", 0.7, [(0, {}), (0.16, K.plus(puff(0.45), {"body": {"scale": (1.2, 1.15, 1.2)}})), (0.24, K.plus(puff(0.6), {"body": {"scale": (1.3, 1.2, 1.3)}})),
                              (0.34, K.on_floor(r, {"head": {"scale": (1.5, 0.12, 1.5)}, "body": {"scale": (1.3, 0.25, 1.3)}})),
                              (0.7, K.on_floor(r, {"head": {"scale": (1.6, 0.06, 1.6)}, "body": {"scale": (1.4, 0.15, 1.4)}}))])
    return r


def paint_sporeling(r, P):
    P.paint("stem", FLESH, 3.4, p_strokes(2, 3, seed=1), rim=0.3)
    P.paint("brim", CAP, 3.0, p_blotch("spb", 4, 0.5), rim=0.8)
    P.paint("dome", CAP, 3.6, p_blotch("spd", 4, 0.5), rim=0.9)
    P.paint("crown", CAP, 4.2)
    gills(P, "brim")
    f, g = P.face("dome", "north"), P.face("dome", "north", glow=True)  # the slit where the cap splits
    for i in range(1, f.w - 1):
        f.set(i, 1, hexc(GOLD if i not in (3, 4) else HOT))
        g.set(i, 1, hexc(GOLD, 235))
    P.paint("tassel0", BARK, 3.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Shelf (mycelid): a walking stack of bracket fungus with no head, one long root of an arm dragging. The shelves lift
# like gills before it coughs, and stay open a moment after.

SHELVES = ((-6, -4, -5, 12, 8), (-3, -8, 0, 10, 7), (-7, -12, -4, 11, 7), (-2, -16, -1, 9, 8))  # x, y, z, w, d (each 2 high)


def shelf():
    r = Rig("mycelid", (128, 64))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 14, 0)), 14)
    body.cube("trunk", -3, -18, -3, 6, 20, 6)
    K.legs2(body, "1", (2.5, 1, 0), 5, 5, 22, thick=(3, 2), foot=(3, 2, 4, -2))
    for k, (x, y, z, w, d) in enumerate(SHELVES):
        hx, hz = (3 if x + w / 2 > 0 else -3), 0
        sh = body.bone(f"frill{k}", (0, y + 2, 0))
        sh.cube(f"shelf{k}", x, -2, z, w, 2, d)
    arm = body.bone("arm_r", (-3, -13, 0), (-6, 0, 10))
    arm.cube("rootu", -1.5, 0, -1.5, 3, 12, 3)
    fore = arm.bone("fore_r", (0, 11, 0), (-14, 0, -6))
    fore.cube("rootl", -1, 0, -1, 2, 10, 2)
    fore.bone("fore_tip", (0, 9, 0), (40, 0, 0)).cube("rootend", -0.5, 0, -0.5, 1, 4, 1)
    r.pack()

    legs = ("1_l", "1_r")
    lift = lambda a: {f"frill{k}": (a * (1 if SHELVES[k][2] < -2 else -1) * (-1), 0, a * 0.5 * (1 if SHELVES[k][0] < -4 else -1)) for k in range(4)}  # noqa: E731
    lagc = {f"frill{k}": 0.04 * k for k in range(4)}
    K.moves(r, cough="Spore Cough")
    K.act(r, "cough", dict(lift(26), body={"rot": (-14, 0, 0), "pos": (0, 0.5, 1)}, arm_r=(-20, 0, 12)),
          dict(lift(34), body={"rot": (18, 0, 0), "pos": (0, -1, -1.5)}, arm_r=(16, 0, 0)), w=0.9, length=0.5, t_hit=0.1, lag=lagc, plant=legs,
          open_pose=dict(lift(24), body={"rot": (8, 0, 0), "pos": (0, -1, 0)}))
    K.plain(r, {"body": (0, 24, 0), "arm_r": (30, 0, 30), "fore_r": (-30, 0, 0)}, {"body": (6, -30, 0), "arm_r": (-70, 0, -10), "fore_r": (10, 0, 0), "fore_tip": (-30, 0, 0)},
            plant=legs, lag={"fore_r": 0.04, "fore_tip": 0.08})
    K.loop(r, "idle", 6.0, lambda p: dict(lift(2 + 2 * s(p * 2)), body=(1.2 * s(p), 0, 1.0 * s(p, 0.3)), arm_r=(3 * s(p, 0.2), 0, 0), fore_tip=(8 * s(p * 2), 0, 0)), n=12)
    K.loop(r, "walk", 1.1, lambda p: dict(lift(3 + 3 * s(p * 2, 0.2)), body=(3 + 2 * s(p * 2), 0, 3.5 * s(p)), arm_r=(10 * s(p, 0.5), 0, 0), fore_r=(6 * s(p, 0.6), 0, 0)))
    K.loop(r, "run", 0.7, lambda p: dict(lift(6 + 4 * s(p * 2, 0.2)), body=(9 + 3 * s(p * 2), 0, 4.5 * s(p)), arm_r=(20 + 14 * s(p, 0.5), 0, 0), fore_r=(8 * s(p, 0.6), 0, 0)))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 2.0, lambda p: dict(lift(10 + 4 * s(p * 2)), body={"rot": (5, 0, 0), "pos": (0, -0.6, 0)}, arm_r=(-12, 0, 10)), plant=legs)
    K.flinch(r, dict(lift(18), body=(-10, 0, 6), arm_r=(-16, 0, 16)), plant=legs)
    down = dict(lift(-10), body={"rot": (78, 0, 8)}, arm_r=(-60, 0, 20), fore_r=(-20, 0, 0), leg1_l=(-50, 0, 0), leg1_r=(-40, 0, 0), shin1_l=(60, 0, 0), shin1_r=(70, 0, 0))
    K.keyed(r, "death", 1.2, [(0, {}), (0.16, K.planted(r, dict(lift(30), body=(-12, 0, 0)), legs))]
            + K.fall(r, 0.16, K.planted(r, dict(lift(30), body=(-12, 0, 0)), legs), 0.75, down, steps=4)
            + [(0.88, K.on_floor(r, K.plus(down, {"body": (-5, 0, 0)}))), (1.2, K.on_floor(r, down))], lag=lagc)
    return r


def paint_shelf(r, P):
    P.paint("trunk", BARK, 2.4, p_strokes(2, 5, seed=3), rim=0.3)
    for fname in K.SIDES:
        f = P.face("trunk", fname)
        for i, j in f.each():
            if (i * 7 + j * 3) % 13 == 0:
                f.set(i, j, MOSS.at(1 + (i + j) % 3))
    for k in range(4):
        P.paint(f"shelf{k}", FLESH, 3.4 - 0.2 * k, p_bands(3, 0.6), rim=0.9, ao=0.2)
        f = P.face(f"shelf{k}", "top")
        for i, j in f.each():  # growth rings
            d = min(i, j, f.w - 1 - i, f.h - 1 - j)
            if d % 3 == 1:
                f.set(i, j, FLESH.at(2.2))
        gills(P, f"shelf{k}")
    for nm in ("rootu", "rootl", "rootend"):
        P.paint(nm, BARK, 3.0, p_strokes(2, 4, seed=5))
    P.paint("thigh1_l", BARK, 2.6, p_strokes(2, 3, seed=6))
    P.paint("shinb1_l", BARK, 3.0)
    P.paint("footb1_l", BARK, 2.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Capweaver (capweaver): a wide flat toadstool cap on eight thread legs. From above it is just another mushroom; it
# lifts its rim to spit web.

def capweaver():
    r = Rig("capweaver", (128, 64))
    r.shadow = 0.8
    body = K.at(r.bone("body", (0, 19, 0)), 19)
    body.cube("under", -2, -1, -3, 4, 3, 6)
    K.splay_pairs(body, 0.5, (2, 2, 2, 2), (-2.4, -0.8, 0.8, 2.4), 5, 7, rise=28)
    cap = body.bone("head", (0, -1, 0))
    cap.cube("capb", -7, -2, -7, 14, 2, 14)
    cap.cube("capt", -5, -4, -5, 10, 2, 10)
    cap.cube("capn", -2, -5, -2, 4, 1, 4)
    for side, nm in SIDES2:
        body.bone("palp_" + nm, (side * 1, 1, -3), (20, side * -14, 0)).cube("palp_" + nm, -0.5, -0.5, -3, 1, 1, 3, share=None if side > 0 else "palp_l")
    r.pack()

    EIGHT = K.tags(4)
    K.moves(r, spit="Web Spit")
    K.act(r, "spit", {"body": {"rot": (-26, 0, 0), "pos": (0, 2.5, 1)}, "head": (-12, 0, 0), "palp_l": (-30, 20, 0), "palp_r": (-30, -20, 0)},
          {"body": {"rot": (8, 0, 0), "pos": (0, 0.5, -1.5)}, "head": (6, 0, 0), "palp_l": (20, -10, 0), "palp_r": (20, 10, 0)}, w=1.0, length=0.5, t_hit=0.09,
          plant=EIGHT, open_pose={"body": {"rot": (-14, 0, 0), "pos": (0, 1.5, 0)}, "head": (-8, 0, 0)})
    K.plain(r, {"body": {"rot": (-16, 0, 0), "pos": (0, 1.5, 1)}, "palp_l": (-40, 24, 0), "palp_r": (-40, -24, 0)},
            {"body": {"rot": (10, 0, 0), "pos": (0, 0, -2.5)}, "palp_l": (24, -12, 0), "palp_r": (24, 12, 0)}, plant=EIGHT)
    K.loop(r, "idle", 6.0, lambda p: {"head": (0.8 * s(p), 0, 0.8 * s(p, 0.3)), "palp_l": (8 * bump(p, 0.4, 0.05), 0, 0), "palp_r": (8 * bump(p, 0.46, 0.05), 0, 0)}, n=16)
    K.loop(r, "walk", 0.7, lambda p: {"head": (1.5 * s(p * 2), 0, 2.5 * s(p))})
    K.loop(r, "run", 0.45, lambda p: {"head": (-5 + 2 * s(p * 2), 0, 3.5 * s(p))})
    K.fallback_walk(r, K.alternating(4), axis="y")
    K.loop(r, "angry", 1.6, lambda p: {"body": {"rot": (-10 + 2 * s(p * 2), 0, 0), "pos": (0, 1.2, 0)}, "palp_l": (-20, 14 + 8 * s(p * 4), 0), "palp_r": (-20, -14 - 8 * s(p * 4), 0)}, plant=EIGHT)
    K.flinch(r, {"body": {"pos": (0, -1, 1)}, "head": (8, 0, 10)}, plant=EIGHT)
    curl = {f"leg{t}": (0, 0, (-58 if t.endswith("l") else 58)) for t in EIGHT}
    curl.update({f"shin{t}": (0, 0, (50 if t.endswith("l") else -50)) for t in EIGHT})
    K.keyed(r, "death", 1.0, [(0, {}), (0.14, K.planted(r, {"body": {"pos": (0, 3, 0), "rot": (-20, 0, 0)}}, EIGHT)),
                              (0.5, K.on_floor(r, dict(curl, body=(0, 0, 176), head=(0, 0, 0)))),
                              (1.0, K.on_floor(r, dict(K.scaled(curl, 1.15), body=(0, 0, 180))))])
    return r


def paint_capweaver(r, P):
    P.paint("under", BARK, 2.0)
    P.paint("capb", CAP, 3.0, p_blotch("cwb", 5, 0.6), rim=0.9, ao=0.2)
    P.paint("capt", CAP, 3.6, p_blotch("cwt", 5, 0.6), rim=0.9)
    P.paint("capn", CAP, 4.2)
    for nm in ("capb", "capt"):  # pale warts, as its neighbours have
        f = P.face(nm, "top")
        for i, j in f.each():
            if (i * 5 + j * 3) % 11 == 0 and 0 < i < f.w - 1 and 0 < j < f.h - 1:
                f.set(i, j, FLESH.at(5))
    gills(P, "capb")
    P.paint("palp_l", BARK, 3.6)
    P.paint("thigh0_l", BARK, 3.2)
    P.paint("shinb0_l", FLESH, 2.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Puffcap (puffcap): a thin stalk on root feet under a cap that balloons as it comes at you, gold cracks spreading
# across it. Hit while it swells, it bursts where it stands.

def puffcap():
    r = Rig("puffcap", (64, 64))
    r.shadow = 0.5
    body = K.at(r.bone("body", (0, 16, 0)), 16)
    body.cube("stalk", -1.5, -9, -1.5, 3, 11, 3)
    K.legs2(body, "1", (1.5, 1, 0), 4, 4, 24, thick=(2, 1))
    cap = body.bone("head", (0, -9, 0))
    cap.cube("ball", -4, -8, -4, 8, 8, 8)
    cap.cube("ballw", -5, -7, -3, 10, 6, 6)
    cap.cube("balld", -3, -7, -5, 6, 6, 10)
    cap.cube("skirtc", -3, 0, -3, 6, 1, 6)
    r.look = [("head", 0.4, 0.2)]
    r.pack()

    legs = ("1_l", "1_r")
    big = lambda k: {"head": {"scale": (1 + k, 1 + k, 1 + k)}}  # noqa: E731
    K.plain(r, K.plus(big(0.22), {"body": (-8, 0, 0)}), K.plus(big(-0.1), {"body": {"rot": (14, 0, 0), "pos": (0, 0, -1.5)}}), plant=legs)
    K.loop(r, "idle", 3.0, lambda p: K.plus(big(0.04 * s(p)), {"head": (3 * s(p, 0.2), 0, 4 * s(p * 2, 0.1)), "body": (1.5 * s(p), 0, 0)}))
    K.loop(r, "walk", 0.7, lambda p: {"head": (6 * s(p * 2, 0.15), 0, 7 * s(p, 0.1)), "body": (3 + 2 * s(p * 2), 0, 3 * s(p))})
    K.loop(r, "run", 0.45, lambda p: {"head": (12 * s(p * 2, 0.15), 0, 10 * s(p, 0.1)), "body": (10 + 3 * s(p * 2), 0, 4 * s(p))})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 0.9, lambda p: K.plus(big(0.1 + 0.06 * s(p * 2)), {"body": (6, 0, 0), "head": (0, 0, 6 * s(p))}), plant=legs)
    K.flinch(r, K.plus(big(0.15), {"body": (-12, 0, 8)}), plant=legs)
    K.keyed(r, "death", 0.7, [(0, {}), (0.14, big(0.4)), (0.22, big(0.6)),
                              (0.3, K.on_floor(r, {"head": {"scale": (1.7, 0.1, 1.7)}, "body": {"rot": (70, 0, 0)}})),
                              (0.7, K.on_floor(r, {"head": {"scale": (1.8, 0.05, 1.8)}, "body": {"rot": (86, 0, 0)}}))])
    return r


def paint_puffcap(r, P):
    P.paint("stalk", FLESH, 3.4, p_strokes(2, 4, seed=2), rim=0.3)
    for nm, base in (("ball", 3.4), ("ballw", 3.0), ("balld", 3.2)):
        P.paint(nm, CAP, base, p_blotch("pf" + nm, 5, 0.5), rim=0.8, ao=0.5)
        for fname in K.ALL:  # cracks, gold showing through the stretched skin
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            x = K._h(nm, fname) % max(1, f.w - 3) + 1
            for j in range(1, f.h - 1):
                xx = x + (K._h(nm, fname, j) % 3 - 1)
                if 0 < xx < f.w - 1:
                    f.set(xx, j, hexc(GOLD))
                    g.set(xx, j, hexc(GOLD, 210))
    P.paint("skirtc", FLESH, 2.0)
    gills(P, "skirtc")
    P.paint("thigh1_l", BARK, 3.0)
    P.paint("shinb1_l", BARK, 2.4)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Tender (hollow_tender, the elite): a tall thin stalk under a cap as wide as a parasol, a pod of smoke on a long
# stem. It roots itself to heal the others and cannot turn while it does.

def tender():
    r = Rig("hollow_tender", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 9, 0)), 9)
    body.cube("stalkl", -2, -2, -2, 4, 6, 4)
    K.legs2(body, "1", (2, 2, 0), 7, 7, 26, thick=(2, 1), foot=(3, 2, 4, -2))
    chest = body.bone("chest", (0, -2, 0), (6, 0, 0))
    chest.cube("stalku", -1.5, -14, -1.5, 3, 14, 3)
    head = chest.bone("head", (0, -14, 0), (-6, 0, 0))
    head.cube("parasol", -9, -2, -9, 18, 2, 18)
    head.cube("parat", -6, -4, -6, 12, 2, 12)
    head.cube("paran", -2, -6, -2, 4, 2, 4)
    for k in range(4):
        a = math.tau * k / 4 + 0.6
        head.bone(f"tassel{k}", (7 * math.sin(a), 0, 7 * math.cos(a)), (0, math.degrees(a), 0)).cube(f"tassel{k}", -2, 0, 0, 4, 5, 0, share=None if k == 0 else "tassel0")
    arm = chest.bone("arm_l", (1.5, -10, 0), (-30, 0, -24))
    arm.cube("stemu", -0.5, 0, -0.5, 1, 9, 1)
    fore = arm.bone("fore_l", (0, 9, 0), (-50, 0, 0))
    fore.cube("steml", -0.5, 0, -0.5, 1, 8, 1)
    fore.bone("lamp", (0, 8, 0), (80, 0, 24)).cube("pod", -2, 0, -2, 4, 5, 4)
    r.look = [("head", 0.3, 0.2)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"lamp": 0.1, **{f"tassel{k}": 0.08 for k in range(4)}}
    K.moves(r, tend="Tend the Colony", lob="Spore Lob", bloom="Rot Bloom")
    # Tend the Colony: it sinks on its roots, the parasol comes down over it, the pod is held high and shaken
    K.act(r, "tend", {"body": {"pos": (0, -4, 0)}, "chest": (8, 0, 0), "head": (16, 0, 0), "arm_l": (-70, 0, 10), "fore_l": (30, 0, 0)},
          {"body": {"pos": (0, -5, 0)}, "chest": (12, 0, 0), "head": (22, 0, 0), "arm_l": (-84, 0, 20), "fore_l": (44, 0, 0), "lamp": (0, 0, 30)},
          w=1.5, length=0.6, t_hit=0.12, lag=lagc, plant=legs)
    K.act(r, "lob", {"chest": (-12, 0, 0), "arm_l": (-120, 0, 10), "fore_l": (20, 0, 0)}, {"chest": (14, 0, 0), "arm_l": (-10, 0, 0), "fore_l": (40, 0, 0), "head": (8, 0, 0)},
          w=0.7, length=0.5, lag=lagc, plant=legs)
    K.act(r, "bloom", {"chest": (-6, 0, 0), "arm_l": (-40, 0, -30), "fore_l": (-20, 0, 0)}, {"chest": (16, 0, 0), "arm_l": (30, 0, 10), "fore_l": (30, 0, 0), "body": {"pos": (0, -2, 0)}},
          w=0.9, length=0.5, lag=lagc, plant=legs, open_pose={"chest": (10, 0, 0), "arm_l": (20, 0, 0), "fore_l": (20, 0, 0), "body": {"pos": (0, -2, 0)}, "head": (10, 0, 0)})
    K.plain(r, {"chest": (-10, 0, 0), "head": (-24, 0, 0)}, {"chest": (20, 0, 0), "head": (34, 0, 0), "body": {"pos": (0, -1, -1)}}, plant=legs, lag=lagc)
    K.loop(r, "idle", 7.0, lambda p: {"chest": (1.5 * s(p), 0, 1.2 * s(p, 0.3)), "head": (2 * s(p, 0.2), 6 * s(p, 0.5), 0), "arm_l": (4 * s(p * 2), 0, 0), "lamp": (6 * s(p * 2, 0.3), 0, 0)}, n=14)
    K.loop(r, "walk", 1.2, lambda p: {"chest": (3 + 2 * s(p * 2), 0, 3 * s(p)), "head": (-3 - 2 * s(p * 2, 0.15), 0, -2 * s(p, 0.1)), "arm_l": (8 * s(p, 0.5), 0, 0)})
    K.loop(r, "run", 0.8, lambda p: {"chest": (10 + 3 * s(p * 2), 0, 4 * s(p)), "head": (-8 - 3 * s(p * 2, 0.15), 0, 0), "arm_l": (16 * s(p, 0.5), 0, 0)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 2.2, lambda p: {"chest": (4 + s(p * 2), 0, 0), "head": (10, 0, 2 * s(p)), "arm_l": (-50, 0, 0), "fore_l": (20, 0, 0), "body": {"pos": (0, -1, 0)}}, plant=legs)
    K.flinch(r, {"chest": (-12, 0, 5), "head": (-18, 0, 0), "arm_l": (-20, 0, -20)}, plant=legs)
    down = {"body": {"rot": (70, 0, 10)}, "chest": (14, 0, 0), "head": (-30, 0, 20), "arm_l": (-60, 0, -30), "leg1_l": (-50, 0, 0), "leg1_r": (-40, 0, 0), "shin1_l": (70, 0, 0), "shin1_r": (80, 0, 0)}
    start = K.planted(r, {"chest": (-14, 0, 0), "head": (-26, 0, 0), "arm_l": (-40, 0, -30)}, legs)
    K.keyed(r, "death", 1.4, [(0, {}), (0.2, start)] + K.fall(r, 0.2, start, 0.9, down, steps=4)
            + [(1.04, K.on_floor(r, K.plus(down, {"body": (-5, 0, 0)}))), (1.4, K.on_floor(r, down))], lag=lagc)
    return r


def paint_tender(r, P):
    for nm in ("stalkl", "stalku"):
        P.paint(nm, FLESH, 3.4, p_strokes(2, 5, seed=4), rim=0.3)
    P.paint("parasol", CAP, 3.0, p_blotch("tp", 6, 0.5), rim=0.9, ao=0.2)
    P.paint("parat", CAP, 3.6, p_blotch("tq", 6, 0.5), rim=0.9)
    P.paint("paran", CAP, 4.2)
    gills(P, "parasol", a=200)
    K.tatter(P, "tassel0", FLESH, 3.6, seed=2, fall=0.4)
    for nm in ("stemu", "steml"):
        P.paint(nm, BARK, 3.2)
    P.paint("pod", BARK, 2.6, rim=0.6)
    for fname in K.SIDES:
        K.lit(P, "pod", fname, 1, 1, 2, 3, GOLD, HOT, a=230)
    P.paint("thigh1_l", FLESH, 2.8)
    P.paint("shinb1_l", BARK, 3.0)
    P.paint("footb1_l", BARK, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Rot (the_rot, the Warden): a fallen log that got up. A hollow rotten trunk on six root legs, light showing through
# it. It charges hollow end first; make it hit a wall and it sticks.

def rot():
    r = Rig("the_rot", (128, 128))
    r.shadow = 1.3
    body = K.at(r.bone("body", (0, 14, 0)), 14)
    body.cube("log", -5, -5, -13, 10, 10, 26)
    body.cube("stub", 5, -3, 2, 3, 3, 3)
    K.splay_pairs(body, 3, (5, 5, 5), (-9, 0, 9), 7, 10, rise=24, thick=2)
    for k, (x, z, w, d) in enumerate(((-6, -8, 12, 5), (-4, -1, 10, 6), (-7, 6, 12, 5))):
        body.bone(f"frill{k}", (0, -5, z + d / 2)).cube(f"shelf{k}", x, -2, -d / 2, w, 2, d)
    for k in range(3):
        body.bone(f"moss{k}", (-3 + 3 * k, 5, -12 + 0.01 * k)).cube(f"moss{k}", -1.5, 0, 0, 3, 3 + k % 2, 0, share=None)
    r.pack()

    SIX = K.tags(3)
    lagc = {f"frill{k}": 0.05 + 0.03 * k for k in range(3)}
    flare = lambda a: {f"frill{k}": (0, 0, a * (1 if k % 2 else -1)) for k in range(3)}  # noqa: E731
    K.moves(r, charge="Rot Charge", burst="Spore Burst", field="Choking Field")
    jam = K.planted(r, {"body": {"rot": (14, 0, 0), "pos": (0, -1.5, -3)}}, SIX)
    K.act(r, "charge", {"body": {"rot": (9, 0, 0), "pos": (0, -1, 3)}, "leg0_l": (0, 30, -30), "shin0_l": (0, 0, -30)},
          {"body": {"rot": (-5, 0, 0), "pos": (0, 0.5, -6)}}, w=1.3, length=0.6, t_hit=0.12, plant=SIX, lag=lagc, stuck=jam)
    K.act(r, "burst", dict(flare(26), body={"rot": (-18, 0, 0), "pos": (0, 3, 1)}), dict(flare(-12), body={"rot": (6, 0, 0), "pos": (0, -2, 0)}),
          w=1.2, length=0.5, t_hit=0.1, plant=SIX, lag=lagc, open_pose=dict(flare(8), body={"pos": (0, -2, 0)}))
    K.act(r, "field", dict(flare(14), body={"rot": (0, 0, 12), "pos": (0, 1, 0)}), dict(flare(-14), body={"rot": (0, 0, -14), "pos": (0, 0, 0)}),
          w=0.9, length=0.6, t_hit=0.1, plant=SIX, lag=lagc, after=dict(flare(8), body=(0, 0, 9)))
    K.plain(r, {"body": {"rot": (-8, 0, 0), "pos": (0, 1, 2.5)}}, {"body": {"rot": (8, 0, 0), "pos": (0, 0, -4.5)}},
            heavy=({"body": {"rot": (-20, 0, 0), "pos": (0, 3.5, 2)}}, {"body": {"rot": (12, 0, 0), "pos": (0, -1.5, -3)}}), plant=SIX, lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: dict(flare(2 * s(p * 2)), body=(0.8 * s(p), 0, 0.8 * s(p, 0.3))), n=12)
    K.loop(r, "walk", 1.0, lambda p: dict(flare(3 * s(p * 2, 0.2)), body=(1.5 * s(p * 2), 0, 2 * s(p))))
    K.loop(r, "run", 0.6, lambda p: dict(flare(6 * s(p * 2, 0.2)), body=(5 + 2.5 * s(p * 2), 0, 3 * s(p))))
    K.fallback_walk(r, K.alternating(3), axis="y")
    K.loop(r, "angry", 2.0, lambda p: dict(flare(8 + 3 * s(p * 2)), body={"rot": (6 + s(p * 2), 0, 0), "pos": (0, -1, 0)}), plant=SIX)
    K.flinch(r, dict(flare(14), body={"rot": (-4, 0, 8), "pos": (0, 0, 1.5)}), plant=SIX)
    curl = {f"leg{t}": (0, 0, (-40 if t.endswith("l") else 40)) for t in SIX}
    curl.update({f"shin{t}": (0, 0, (36 if t.endswith("l") else -36)) for t in SIX})
    start = K.planted(r, dict(flare(20), body={"rot": (-10, 0, 0), "pos": (0, 2, 0)}), SIX)
    over = dict(curl, body=(0, 0, 100), **flare(-20))
    K.keyed(r, "death", 1.6, [(0, {}), (0.2, start)] + K.fall(r, 0.2, start, 0.9, over, steps=4)
            + [(1.05, K.on_floor(r, K.plus(over, {"body": (0, 0, -8)}))), (1.6, K.on_floor(r, over))], lag=lagc)
    return r


def paint_rot(r, P):
    P.paint("log", BARK, 2.4, p_strokes(3, 7, seed=7), rim=0.4, ao=0.5)
    for fname in ("top", "west", "east"):
        f = P.face("log", fname)
        for i, j in f.each():
            if (i * 5 + j * 7) % 17 == 0 or (fname == "top" and (i * 3 + j) % 19 == 0):
                f.set(i, j, MOSS.at(1 + (i + j) % 3))
    for fname in ("north", "south"):  # it is hollow: rings of rotten wood round the dark, gold deep inside
        f = P.face("log", fname)
        for i, j in f.each():
            d = min(i, j, f.w - 1 - i, f.h - 1 - j)
            f.set(i, j, BARK.at(4 - d) if d < 2 else hexc("#06050a"))
    K.lit(P, "log", "north", 4, 4, 2, 2, GOLD, HOT, a=220)
    K.lit(P, "log", "south", 3, 3, 4, 4, GOLD, HOT, a=120)
    P.paint("stub", BARK, 3.2)
    for k in range(3):
        P.paint(f"shelf{k}", FLESH, 3.2, p_bands(3, 0.6), rim=0.9, ao=0.2)
        gills(P, f"shelf{k}")
        K.tatter(P, f"moss{k}", MOSS, 2.6, seed=k, fall=0.3)
    P.paint("thigh0_l", BARK, 3.0, p_strokes(2, 3, seed=8))
    P.paint("shinb0_l", BARK, 2.4, p_strokes(2, 3, seed=9))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}


def biped(r, **over):
    g = dict(duty=(0.64, 0.44), lift=(2.0, 3.2), crouch=0.5, swing=0.3, bob=0.5, roll=0.05)
    g.update(over)
    return K.gait("alternate", K.room(r, K.legs_of(r), g["crouch"]), **g)


MONSTERS = [
    K.Monster("sporeling", "The Sporeling", sporeling, paint_sporeling, kind="slider", box=(0.6, 0.6), sunk=("tassel0", "tassel1", "tassel2"),
              motion={"body": K.body(headLevel=0.0, twist=0.2, land=1.6, shift=0.0)},
              life=dict(STILL, temperament="cute", idles={"look": 1, "settle": 2}, springHz=3.0, springGain=1.5),
              voice={"ambient": [("block.fungus.step", 0.5, 1.3)], "hurt": [("entity.puffer_fish.hurt", 0.6, 1.2)], "death": [("entity.puffer_fish.blow_out", 0.9, 1.1)],
                     "step": [("block.fungus.step", 0.3, 1.4)]}),
    K.Monster("mycelid", "The Shelf", shelf, paint_shelf, kind="strider", box=(0.8, 2.0), sunk=("rootend", "rootl"),  # its root drags
              motion=lambda r: {"gait": biped(r, bob=0.7, roll=0.07, speed=0.9), "body": K.body(headLevel=0.0, twist=0.2, land=1.3, shift=0.6)},
              life=dict(STILL, temperament="monster", idles={"settle": 3, "look": 1}, mass=1.8, springHz=1.6, springGain=1.3, arms=0.9),
              voice={"ambient": [("block.wood.step", 0.5, 0.5), ("block.roots.step", 0.6, 0.6)], "hurt": [("block.wood.hit", 0.9, 0.6), ("block.fungus.hit", 0.8, 0.7)],
                     "death": [("block.wood.break", 1.0, 0.5)], "step": [("block.roots.step", 0.45, 0.7)]}),
    K.Monster("capweaver", "The Capweaver", capweaver, paint_capweaver, kind="crawler", box=(0.9, 0.55),
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.6), speed=0.7),
                                "body": K.body(headLevel=0.7, twist=0.15, slope=0.85, land=1.0, shift=0.5, still=True, creep=1.0)},
              life=dict(STILL, temperament="insect", idles={"settle": 3, "flick": 1}, headSpeed=4.0),
              voice={"ambient": [("entity.spider.ambient", 0.4, 1.3)], "hurt": [("entity.spider.hurt", 0.7, 1.2), ("block.fungus.hit", 0.7, 0.9)],
                     "death": [("entity.spider.death", 0.8, 1.2)], "step": [("entity.spider.step", 0.3, 1.4)]}),
    K.Monster("puffcap", "The Puffcap", puffcap, paint_puffcap, kind="strider", box=(0.6, 1.5),
              motion=lambda r: {"gait": biped(r, duty=(0.6, 0.42), lift=(1.6, 2.6), swing=0.2, bob=0.3), "body": K.body(headLevel=0.3, twist=0.3, land=1.4, shift=0.3)},
              life=dict(STILL, temperament="cute", idles={"look": 2, "settle": 2}, bob=0.8, sway=2.0),
              voice={"ambient": [("block.fungus.step", 0.5, 0.9)], "hurt": [("entity.puffer_fish.hurt", 0.7, 0.8)],
                     "death": [("entity.puffer_fish.blow_out", 1.0, 0.7), ("entity.creeper.death", 0.6, 1.1)], "step": [("block.fungus.step", 0.35, 1.0)]}),
    K.Monster("hollow_tender", "The Tender", tender, paint_tender, kind="strider", box=(0.8, 2.4),
              motion=lambda r: {"gait": biped(r, duty=(0.66, 0.46), lift=(2.2, 3.6), swing=0.34, bob=0.5), "body": K.body(headLevel=0.6, twist=0.25, land=1.2, shift=0.5)},
              life=dict(STILL, temperament="monster", idles={"look": 3, "settle": 2}, headSpeed=3.6, springHz=1.4, springGain=1.4, arms=0.4),
              voice={"ambient": [("block.fungus.step", 0.5, 0.6), ("entity.witch.ambient", 0.35, 0.55)], "hurt": [("block.fungus.hit", 0.9, 0.7), ("entity.witch.hurt", 0.4, 0.6)],
                     "death": [("block.fungus.break", 1.0, 0.6), ("entity.witch.death", 0.4, 0.55)], "step": [("block.roots.step", 0.4, 0.8)]}),
    K.Monster("the_rot", "The Rot", rot, paint_rot, kind="crawler", box=(1.6, 1.5), sunk=("moss", "thigh", "shinb"),  # the game plants its feet
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.6), duty=(0.76, 0.56), lift=(3.0, 4.6), swing=0.3, bob=0.5, roll=0.04, speed=1.0),
                                "body": K.body(headLevel=0.0, twist=0.1, slope=0.8, land=1.5, shift=0.8)},
              life=dict(STILL, temperament="beast", idles={"settle": 3, "shake": 1}, mass=3.0, springHz=1.2, springGain=1.4),
              voice={"ambient": [("entity.ravager.ambient", 0.6, 0.6), ("block.wood.step", 0.7, 0.4)], "hurt": [("block.wood.hit", 1.0, 0.45), ("entity.ravager.hurt", 0.6, 0.6)],
                     "death": [("block.wood.break", 1.0, 0.4), ("entity.ravager.death", 0.7, 0.6)], "step": [("block.wood.step", 0.6, 0.5)]}),
]
