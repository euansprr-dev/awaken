"""The Clockwork Foundry's monsters (SPEC §60). Room: dark iron, steam, brass in shadow. So they are bright brass and
copper, and each carries a yellow-white light where its spring or its fire is."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

BRASS = Ramp("#3a2608", "#5c3e0e", "#86601a", "#b0862a", "#d6ac44", "#f2d478")
COPPER = Ramp("#3a1a0e", "#5c2c16", "#844424", "#ac6238", "#cc8656", "#e6b088")
IRON = Ramp("#1c1c22", "#2a2a32", "#3c3c46", "#52525e", "#6c6c7a", "#8a8a98")
LIT, HOT = "#ffe08a", "#fffbe6"

QUAD = ("0_l", "0_r", "1_l", "1_r")
TROT = [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)]


def rivets(P, cube, faces=K.SIDES, every=3, ramp=BRASS, row=None):
    """A row of rivet heads along a face's top and bottom edges."""
    for fname in faces:
        f = P.face(cube, fname)
        for i in range(1, f.w - 1, every):
            for j in ((1, f.h - 2) if row is None else row):
                if 0 <= j < f.h:
                    f.set(i, j, ramp.at(5))


def gear_face(P, cube, fname, ramp=BRASS):
    """A gear seen flat: a rim, a hub and four spokes, daylight between them."""
    f = P.face(cube, fname)
    cx, cy = (f.w - 1) / 2.0, (f.h - 1) / 2.0
    for i, j in f.each():
        d = math.hypot(i - cx, j - cy)
        spoke = abs(i - cx) < 1.0 or abs(j - cy) < 1.0
        if 1.8 < d < cx - 1.2 and not spoke:
            f.set(i, j, (0, 0, 0, 0))
        else:
            f.set(i, j, ramp.at(4.6 if d >= cx - 1.2 else 3.2 if spoke and d > 1.8 else 4.0))


# ---------------------------------------------------------------------------------------------------------------------
# The Cog Hound (cog_hound): a hound built round one great gear that turns as it runs, a key in its back. The key winds
# before it pounces; a pounce that misses leaves the spring slack and the hound standing there unwinding.

def cog_hound():
    r = Rig("cog_hound", (128, 64))
    r.shadow = 0.6
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    for side, nm in SIDES2:
        body.cube("rail_" + nm, 2 if side > 0 else -3, -2, -6, 1, 3, 12, share=None if side > 0 else "rail_l")
    body.cube("yoke_f", -3, -2, -7, 6, 3, 2)
    body.cube("yoke_b", -3, -2, 5, 6, 3, 2, share="yoke_f")
    cog = body.bone("cog", (0, -0.5, 0))
    cog.cube("plate", -1, -5, -5, 2, 10, 10)
    cog.cube("hub", -2, -1.5, -1.5, 4, 3, 3)
    cog.bone("cog_b", (0, 0, 0), (45, 0, 0)).cube("plate_b", -0.9, -5, -5, 2, 10, 10, share="plate")
    K.legs2(body, "0", (2.5, 2, -5), 5, 7, 40, thick=(2, 1))     # forelegs: knees ahead
    K.legs2(body, "1", (2.5, 1, 5), 8, 5, -24, thick=(2, 1))     # hind legs: hocks behind
    neck = body.bone("neck", (0, -2, -7), (30, 0, 0))
    neck.cube("neckc", -1, -4, -1, 2, 4, 2)
    head = neck.bone("head", (0, -4, 0), (-22, 0, 0))
    head.cube("skullh", -2, -2, -3, 4, 3, 4)
    head.cube("muzzle", -1.5, -1, -6, 3, 2, 3)
    head.bone("jaw", (0, 1, -3)).cube("jawc", -1.5, 0, -3, 3, 1, 3)
    for side, nm in SIDES2:
        head.bone("ear_" + nm, (side * 1.5, -2, 0), (-24, 0, side * 16)).cube("earc_" + nm, -0.5, -3, 0, 1, 3, 2, share=None if side > 0 else "earc_l")
    key = body.bone("key", (0, -2, 6))
    key.cube("stem", -0.5, -5, -0.5, 1, 5, 1)
    key.cube("bow", -2.5, -8, -0.5, 5, 3, 1)
    tail = body.bone("tail", (0, -1, 7), (40, 0, 0))
    tail.cube("rod", -0.5, 0, 0, 1, 1, 6)
    tail.cube("bob", -1, -0.5, 6, 2, 2, 2)
    r.look = [("neck", 0.35, 0.2), ("head", 0.45, 0.3)]
    r.pack()

    lagc = {"tail": 0.08, "ear_l": 0.05, "ear_r": 0.05}
    K.moves(r, pounce="Ratchet Pounce")
    slack = K.planted(r, {"body": {"pos": (0, -1.5, 0), "rot": (5, 0, 0)}, "neck": (26, 0, 0), "head": (14, 0, 0), "jaw": (16, 0, 0), "tail": (-46, 0, 0)}, QUAD)
    K.act(r, "pounce", {"key": (0, 540, 0), "cog": (-90, 0, 0), "body": {"pos": (0, -3, 2), "rot": (-8, 0, 0)}, "neck": (-16, 0, 0), "head": (10, 0, 0), "tail": (-30, 0, 0)},
          {"key": (0, 180, 0), "cog": (270, 0, 0), "body": {"pos": (0, 2.5, -5), "rot": (-16, 0, 0)}, "neck": (10, 0, 0), "head": (-10, 0, 0), "jaw": (30, 0, 0),
           "leg0_l": (-60, 0, 0), "leg0_r": (-60, 0, 0), "leg1_l": (50, 0, 0), "leg1_r": (50, 0, 0)}, w=0.8, length=0.45, t_hit=0.08, plant=QUAD, lag=lagc, stuck=slack)
    # spent: it stands with its head down while the key runs back, slower and slower
    K.keyed(r, "pounce_open", 2.6, [(t, K.plus(slack, {"key": (0, -540 * (1 - (1 - t / 2.6) ** 2), 0), "cog": (-90 * (1 - (1 - t / 2.6) ** 2), 0, 0),
                                                       "head": (3 * math.sin(t * 5), 0, 0)})) for t in (0.0, 0.3, 0.6, 0.9, 1.3, 1.7, 2.1, 2.6)])
    K.plain(r, {"neck": (-18, 0, 0), "head": (6, 0, 0), "jaw": (26, 0, 0), "cog": (-45, 0, 0), "body": {"pos": (0, -0.6, 1.2)}},
            {"neck": (16, 0, 0), "head": (-6, 0, 0), "jaw": (-2, 0, 0), "cog": (45, 0, 0), "body": {"pos": (0, 0, -2.6)}},
            second=K.other_side({"neck": (-18, 0, 0), "head": (6, 0, 0), "jaw": (26, 0, 0), "cog": (-45, 0, 0), "body": {"pos": (0, -0.6, 1.2)}},
                                {"neck": (16, 0, 0), "head": (-6, 0, 0), "jaw": (-2, 0, 0), "cog": (45, 0, 0), "body": {"pos": (0, 0, -2.6)}}),
            plant=QUAD, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: {"neck": (2 * s(p), 6 * s(p, 0.3), 0), "head": (0, 0, 8 * bump(p, 0.6, 0.04)), "ear_l": (14 * bump(p, 0.3, 0.02), 0, 0),
                                      "ear_r": (14 * bump(p, 0.75, 0.02), 0, 0), "tail": (0, 8 * s(p * 2), 0), "key": (0, 20 * bump(p, 0.2, 0.03) - 20 * bump(p, 0.26, 0.03), 0),
                                      "cog": (6 * bump(p, 0.2, 0.03) - 6 * bump(p, 0.26, 0.03), 0, 0)}, n=24)
    K.spin_loop(r, "walk", 0.7, lambda p: {"neck": (4 * s(p * 2, 0.1), 0, 2 * s(p)), "head": (-3 * s(p * 2, 0.2), 0, 0), "tail": (0, 12 * s(p), 0), "body": (1.2 * s(p * 2), 0, 2 * s(p))},
                "cog", 0, 90)
    K.spin_loop(r, "run", 0.42, lambda p: {"neck": (14 + 6 * s(p * 2, 0.1), 0, 0), "head": (-10, 0, 0), "tail": (-30, 8 * s(p), 0), "body": (5 * s(p * 2, 0.25), 0, 0),
                                           "ear_l": (30, 0, 0), "ear_r": (30, 0, 0)}, "cog", 0, 180)
    K.fallback_walk(r, TROT)
    K.spin_loop(r, "angry", 1.2, lambda p: K.planted(r, {"neck": (14, 0, 0), "head": (-6, 0, 0), "jaw": (10 + 6 * s(p * 4), 0, 0), "body": {"pos": (0, -1.2, 0)}, "tail": (-34, 0, 0),
                                                         "key": (0, 30 * s(p * 2), 0)}, QUAD), "cog", 0, -45)
    K.flinch(r, {"neck": (-20, 0, 12), "body": {"pos": (0, 0.5, 1.5), "rot": (0, 0, 6)}, "tail": (20, 0, 0), "cog": (-30, 0, 0)}, plant=QUAD)
    side = {"body": (0, 0, 84), "neck": (20, 0, 0), "head": (10, 20, 0), "leg0_l": (30, 0, 0), "leg0_r": (8, 0, 0), "leg1_l": (-30, 0, 0), "leg1_r": (-8, 0, 0),
            "tail": (0, 0, 30), "jaw": (20, 0, 0), "key": (0, -360, 0), "cog": (135, 0, 0)}
    start = K.planted(r, {"neck": (-30, 0, 0), "body": {"pos": (0, 1, 1)}}, QUAD)
    skip = ("stem", "bow")
    K.keyed(r, "death", 1.1, [(0, {}), (0.12, start)] + K.fall(r, 0.12, start, 0.6, side, steps=3, skip=skip)
            + [(0.72, K.on_floor(r, K.plus(side, {"body": (0, 0, -6)}), skip=skip)), (1.1, K.on_floor(r, side, skip=skip))], lag=lagc)
    return r


def paint_cog_hound(r, P):
    P.paint("plate", BRASS, 3.6, rim=1.0)
    for fname in ("west", "east"):
        gear_face(P, "plate", fname)
    P.paint("hub", COPPER, 3.4, rim=0.8)
    for fname in ("west", "east"):
        K.lit(P, "hub", fname, 1, 1, 1, 1, LIT, HOT, a=240)
    P.paint("rail_l", IRON, 3.0, rim=0.6)
    P.paint("yoke_f", COPPER, 3.0, rim=0.8)
    rivets(P, "yoke_f", ("north", "south"), every=2, row=(1,))
    P.paint("neckc", IRON, 2.8, p_bands(2, 0.8))
    P.paint("skullh", BRASS, 3.4, rim=0.9)
    P.paint("muzzle", BRASS, 2.8, rim=0.6)
    P.paint("jawc", IRON, 3.0)
    for i in (0, 3):
        K.lit(P, "skullh", "north", i, 1, 1, 1, LIT, HOT, a=255)
    P.paint("earc_l", COPPER, 3.4)
    P.paint("stem", IRON, 3.6)
    P.paint("bow", BRASS, 4.0, rim=0.8)
    for fname in ("north", "south"):
        f = P.face("bow", fname)
        f.set(1, 1, (0, 0, 0, 0))
        f.set(3, 1, (0, 0, 0, 0))
    P.paint("rod", IRON, 3.0)
    P.paint("bob", BRASS, 3.8, rim=0.8)
    for tag in ("0", "1"):
        P.paint(f"thigh{tag}_l", COPPER, 3.0, rim=0.5)
        P.paint(f"shinb{tag}_l", IRON, 3.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Riveter (rivet_gunner): a rivet gun on three piston legs. It plants all three before a burst and cannot turn while
# they are down.

def riveter():
    r = Rig("rivet_gunner", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 13, 0)), 13)
    body.cube("turret", -4, -3, -4, 8, 5, 8)
    body.cube("flange", -5, 1, -5, 10, 1, 10)
    body.cube("tank", -2, -6, 2, 4, 3, 4)
    for side, nm in SIDES2:   # two legs ahead and one behind: the game ranks the single one as its hind leg
        m = K.at(body.bone("mount_" + nm, (side * 3.5, 1, -3), (0, side * 50, 0)), 14)   # each leg on a mount turned its way: the leg itself rises true
        K.splay_leg(m, "0_" + nm, side, (0, 0, 0), 6, 14, 30, 0, thick=2, share=None if side > 0 else ("thigh0_l", "shinb0_l"))
    mount = K.at(body.bone("mount", (0, 1, 3.5), (0, -90, 0)), 14)   # turned to face aft, so the leg on it needs no yaw of its own
    K.splay_leg(mount, "1_l", 1, (0, 0, 0), 6, 14, 30, 0, thick=2, share=("thigh0_l", "shinb0_l"))
    head = body.bone("head", (0, -3, 0))
    head.cube("breech", -2, -4, -3, 4, 4, 7)
    head.cube("barrel", -1, -3, -11, 2, 2, 8)
    head.cube("muzzler", -1.5, -3.5, -12, 3, 3, 1)
    head.cube("hopper", -1, -7, 2, 2, 3, 2)
    head.bone("drum", (0, -2, 1.5)).cube("drumc", -3, -3, -1.5, 6, 6, 3)
    r.look = [("head", 0.7, 0.4)]
    r.pack()

    THREE = ("0_l", "0_r", "1_l")
    K.moves(r, burst="Rivet Burst")
    K.act(r, "burst", {"body": {"pos": (0, -1.2, 0)}, "head": (-6, 0, 0), "drum": (0, 0, 120)},
          {"body": {"pos": (0, -1.0, 1.5), "rot": (-4, 0, 0)}, "head": {"rot": (-10, 0, 0), "pos": (0, 0, 2.5)}, "drum": (0, 0, 240)}, w=0.9, length=0.5, t_hit=0.06,
          plant=THREE, open_pose={"body": {"pos": (0, -1.2, 0)}, "head": (24, 0, 0), "drum": (0, 0, 360)})
    K.plain(r, {"head": (0, 46, 0), "body": {"pos": (0, 0, 1)}}, {"head": (0, -46, 0), "body": {"pos": (0, -0.5, -2)}}, plant=THREE)
    K.loop(r, "idle", 6.0, lambda p: {"head": (2 * s(p * 2), 22 * s(p), 0), "drum": (0, 0, 60 * round(3 * p)), "body": {"pos": (0, 0.5 * s(p * 2), 0)}}, n=18)
    K.loop(r, "walk", 0.9, lambda p: {"body": (2 * s(p * 3), 0, 2 * s(p * 3, 0.25)), "head": (-2 * s(p * 3, 0.1), 0, 0)})
    K.loop(r, "run", 0.6, lambda p: {"body": (5 + 3 * s(p * 3), 0, 3 * s(p * 3, 0.25)), "head": (-6, 0, 0)})
    K.fallback_walk(r, [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.25, 1)], axis="y")
    K.loop(r, "angry", 1.0, lambda p: {"head": (-12, 8 * s(p), 0), "drum": (0, 0, 120 * round(3 * p)), "body": {"pos": (0, -1, 0)}}, plant=THREE)
    K.flinch(r, {"body": {"pos": (0, -1, 1.5), "rot": (0, 0, 8)}, "head": (-20, 30, 0)}, plant=THREE)
    fold = {"leg0_l": (0, 0, -30), "leg0_r": (0, 0, 30), "leg1_l": (0, 0, -30), "shin0_l": (0, 0, 60), "shin0_r": (0, 0, -60), "shin1_l": (0, 0, 60)}
    start = K.planted(r, {"body": {"pos": (0, 2, 0)}, "head": (-20, 0, 0)}, THREE)
    low = dict(fold, body=(10, 0, 18), head=(40, 0, 20))
    K.keyed(r, "death", 1.2, [(0, {}), (0.15, start)] + K.fall(r, 0.15, start, 0.8, low, steps=4) + [(0.95, K.on_floor(r, K.plus(low, {"head": (-8, 0, 0)}))), (1.2, K.on_floor(r, low))])
    return r


def paint_riveter(r, P):
    P.paint("turret", COPPER, 2.8, p_plates(4, 5, 0.6), rim=0.8, ao=0.6)
    rivets(P, "turret", every=2)
    P.paint("flange", BRASS, 3.6, rim=1.0)
    P.paint("tank", BRASS, 3.0, p_bands(2, 0.6), rim=0.8)
    P.paint("breech", IRON, 3.0, rim=0.8)
    P.paint("barrel", IRON, 3.6, p_bands(3, 0.8))
    P.paint("muzzler", BRASS, 3.8)
    K.void(P, "muzzler", "north", margin=1, top=1)
    K.lit(P, "muzzler", "north", 1, 1, 1, 1, LIT, HOT, a=230)
    P.paint("hopper", BRASS, 3.2)
    P.paint("drumc", BRASS, 3.4, rim=0.9)
    for fname in ("north", "south"):  # six chambers, one lit: the rivet that is up next
        f, g = P.face("drumc", fname), P.face("drumc", fname, glow=True)
        for k in range(6):
            a = math.tau * k / 6
            i, j = int(round(2.5 + 1.8 * math.cos(a))), int(round(2.5 + 1.8 * math.sin(a)))
            f.set(i, j, hexc(LIT) if k == 0 else IRON.at(0))
            if k == 0:
                g.set(i, j, hexc(LIT, 240))
    P.paint("thigh0_l", COPPER, 3.2, rim=0.6)
    P.paint("shinb0_l", IRON, 3.6, p_bands(4, 0.8))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Governor (spark_drone): two brass balls on arms spinning round a spindle; it flies by spinning. The arms rise as
# it spins up; a blow stops the spin, and a sting that misses drops it.

def governor():
    r = Rig("spark_drone", (64, 32))
    r.shadow = 0.4
    b = r.bone("body", (0, 12, 0))
    sp = b.bone("spin", (0, 0, 0))
    sp.cube("spindle", -0.5, -6, -0.5, 1, 14, 1)
    sp.cube("cap", -1.5, -7, -1.5, 3, 2, 3)
    sp.cube("footg", -1, 8, -1, 2, 1, 2)
    sp.bone("collar", (0, 4, 0)).cube("sleeve", -1.5, -1, -1.5, 3, 2, 3)
    for side, nm in SIDES2:
        g = sp.bone("gov_" + nm, (side * 1, -5.5, 0), (0, 0, side * -28))
        g.cube("rodg_" + nm, -0.5, 0, -0.5, 1, 6, 1, share=None if side > 0 else "rodg_l")
        g.cube("ballg_" + nm, -1.5, 6, -1.5, 3, 3, 3, share=None if side > 0 else "ballg_l")
    r.pack()

    rise = lambda a: {"gov_l": (0, 0, -a), "gov_r": (0, 0, a), "collar": {"pos": (0, a / 14.0, 0)}}  # noqa: E731
    K.moves(r, sting="Spark Sting")
    dropped = K.on_floor(r, K.plus(rise(-24), {"body": (0, 0, 84)}))
    K.act(r, "sting", K.plus(rise(50), {"spin": (0, 720, 0), "body": {"rot": (-20, 0, 0), "pos": (0, 1.5, 1)}}),
          K.plus(rise(58), {"spin": (0, 1080, 0), "body": {"rot": (60, 0, 0), "pos": (0, -1, -5)}}), w=0.9, length=0.4, t_hit=0.07, stuck=dropped)
    K.plain(r, K.plus(rise(30), {"spin": (0, 180, 0), "body": {"rot": (0, 0, -30), "pos": (0, 0.5, 1)}}), K.plus(rise(54), {"spin": (0, 540, 0), "body": {"rot": (20, 0, 36), "pos": (0, 0, -3)}}))
    K.spin_loop(r, "idle", 1.2, lambda p: K.plus(rise(4 * s(p)), {"body": {"pos": (0, 0.8 * s(p), 0), "rot": (2 * s(p, 0.2), 0, 2 * s(p))}}), "spin", 1, 180)
    K.spin_loop(r, "fly", 0.4, lambda p: K.plus(rise(26 + 3 * s(p)), {"body": (10, 0, 0)}), "spin", 1, 180)
    K.spin_loop(r, "angry", 0.5, lambda p: K.plus(rise(40 + 5 * s(p)), {"body": (-8, 0, 4 * s(p))}), "spin", 1, 360)
    K.flinch(r, K.plus(rise(-20), {"body": {"rot": (20, 0, 30), "pos": (0, 0, 2)}, "spin": (0, -60, 0)}))
    K.keyed(r, "death", 1.0, [(0, {}), (0.12, K.plus(rise(40), {"spin": (0, 120, 0), "body": (0, 0, 20)})), (0.4, K.plus(rise(0), {"spin": (0, 220, 0), "body": {"rot": (20, 0, 50), "pos": (0, -4, 0)}})),
                              (0.7, K.plus(dropped, {"spin": (0, 260, 0)})), (0.82, K.plus(dropped, {"spin": (0, 270, 0), "body": (0, 0, -8)})), (1.0, K.plus(dropped, {"spin": (0, 270, 0)}))])
    return r


def paint_governor(r, P):
    P.paint("spindle", IRON, 3.6, p_bands(3, 0.8))
    P.paint("cap", BRASS, 3.8, rim=1.0)
    P.paint("sleeve", COPPER, 3.4, rim=0.8)
    P.paint("footg", BRASS, 3.0)
    P.paint("rodg_l", IRON, 3.2)
    P.paint("ballg_l", BRASS, 3.6, rim=1.0, ao=0.6)
    for fname in K.SIDES:  # a seam of light round each ball, and the spark at the top
        f, g = P.face("ballg_l", fname), P.face("ballg_l", fname, glow=True)
        f.set(1, 1, hexc(LIT))
        g.set(1, 1, hexc(LIT, 230))
    K.lit(P, "cap", "top", 1, 1, 1, 1, LIT, HOT, a=255)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Steam Hammer (foundry_marshal, the elite): a squat boiler on two legs with one arm that is a piston as big as the
# rest of it. It charges, the hammer falls where it stops, and it vents for a breath afterwards.

def steam_hammer():
    r = Rig("foundry_marshal", (128, 64))
    r.shadow = 1.0
    body = K.at(r.bone("body", (0, 14, 0)), 14)
    body.cube("pelvis", -4, -1, -3, 8, 3, 6)
    K.legs2(body, "1", (3.5, 2, 0), 4, 5, 26, thick=(4, 3), foot=(5, 3, 5, -2.5))
    chest = body.bone("chest", (0, -1, 0), (6, 0, 0))
    chest.cube("boiler", -5, -9, -4, 10, 9, 8)
    chest.cube("stackp", 2, -13, 2, 2, 4, 2)
    head = chest.bone("head", (0, -9, -1))
    head.cube("capc", -2.5, -3, -2.5, 5, 3, 5)
    head.cube("visor", -2, -2, -3, 4, 1, 1)
    arm = chest.bone("arm_r", (-6.5, -7, 0), (0, 0, 5))        # its right arm: the piston
    arm.cube("housing", -3, -2, -2.5, 5, 9, 5)
    fore = arm.bone("fore_r", (-0.5, 7, 0))
    fore.cube("rodp", -1, -3, -1, 2, 7, 2)                      # three px of rod wait inside the housing
    fore.cube("hammerh", -3.5, 4, -3.5, 7, 6, 7)
    small = chest.bone("arm_l", (5.5, -7, 0), (4, 0, -8))
    small.cube("arms", -1, 0, -1, 2, 6, 2)
    sf = small.bone("fore_l", (0, 6, 0), (-26, 0, 0))
    sf.cube("armsf", -1, 0, -1, 2, 5, 2)
    sf.cube("claw", -1.5, 5, -1.5, 3, 2, 3)
    r.look = [("head", 0.5, 0.3)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"fore_r": 0.05, "arm_l": 0.04, "fore_l": 0.09, "head": 0.04}
    rod = lambda px: {"fore_r": {"pos": (0, -px, 0)}}  # noqa: E731  (how far the rod stands out past its rest)
    K.moves(r, slam="Piston Slam", charge="Steam Charge")
    down = K.plus(rod(3), {"arm_r": (-38, 0, 0), "chest": (22, 0, 0), "head": (-8, 0, 0), "arm_l": (20, 0, -10), "body": {"pos": (0, -1.5, -1.5)}})
    K.act(r, "slam", K.plus(rod(-2), {"arm_r": (-150, 0, -6), "chest": (-14, 0, 0), "head": (10, 0, 0), "arm_l": (-30, 0, -20), "body": {"pos": (0, 0.5, 1)}}), down,
          w=1.1, length=0.6, t_hit=0.1, over=0.04, plant=legs, lag=lagc, open_pose=K.plus(down, {"head": (24, 0, 0), "chest": (4, 0, 0)}))
    spent = K.planted(r, K.plus(rod(3), {"arm_r": (-56, 0, 0), "chest": (30, 0, 0), "head": (20, 0, 0), "arm_l": (30, 0, -6), "body": {"pos": (0, -2, -1)}}), legs)
    K.act(r, "charge", K.plus(rod(-2), {"arm_r": (46, 0, 0), "chest": (10, 20, 0), "head": (0, -20, 0), "body": {"pos": (0, -1, 1)}}),
          K.plus(rod(3), {"arm_r": (-84, 0, 0), "chest": (14, -24, 0), "head": (0, 20, 0), "body": {"pos": (0, -0.5, -3)}}), w=1.0, length=0.5, t_hit=0.08, plant=legs, lag=lagc, stuck=spent)
    K.plain(r, K.plus(rod(-2), {"arm_r": (-64, 0, 0), "chest": (-6, 10, 0)}), K.plus(rod(3.5), {"arm_r": (-82, 0, 0), "chest": (8, -12, 0), "body": {"pos": (0, 0, -1.5)}}),
            heavy=(K.plus(rod(-2), {"arm_r": (-130, 0, 0), "chest": (-10, 0, 0)}), K.plus(rod(3), {"arm_r": (-44, 0, 0), "chest": (18, 0, 0), "body": {"pos": (0, -1, -1)}})),
            plant=legs, lag=lagc)
    K.loop(r, "idle", 5.0, lambda p: K.plus(rod(0.6 * s(p * 2)), {"chest": (1.2 * s(p), 0, 1.0 * s(p, 0.25)), "head": (0, 8 * s(p, 0.4), 0), "arm_r": (2 * s(p, 0.2), 0, 0),
                                                                  "arm_l": (4 * s(p, 0.6), 0, 0)}), n=14)
    K.loop(r, "walk", 1.0, lambda p: {"chest": (3 + 1.5 * s(p * 2), 4 * s(p), 3 * s(p)), "head": (-2 * s(p * 2, 0.1), 0, -2 * s(p, 0.1)), "arm_r": (-6 + 5 * s(p, 0.5), 0, 0),
                                      "arm_l": (16 * s(p), 0, 0), "fore_l": (-8 + 8 * s(p, 0.1), 0, 0)})
    K.loop(r, "run", 0.62, lambda p: K.plus(rod(1), {"chest": (12 + 2.5 * s(p * 2), 5 * s(p), 4 * s(p)), "head": (-8, 0, 0), "arm_r": (-30 + 6 * s(p, 0.5), 0, 0),
                                                     "arm_l": (24 * s(p), 0, -8), "fore_l": (-20, 0, 0)}))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.2, lambda p: K.plus(rod(1.5 * max(0, s(p * 2))), {"arm_r": (-70, 0, 0), "chest": (-4, 0, 0), "arm_l": (-20, 0, -12), "body": {"pos": (0, -0.8, 0)}}), plant=legs)
    K.flinch(r, {"chest": (-9, 6, 4), "head": (-10, 0, 0), "arm_r": (10, 0, 6), "arm_l": (-24, 0, -16)}, plant=legs)
    knees = K.planted(r, K.plus(rod(3), {"arm_r": (-30, 0, 0), "chest": (16, 0, 0), "head": (20, 0, 0), "body": {"pos": (0, -1.6, 0)}}), legs)
    flat = K.plus(rod(3), {"body": (80, 0, 6), "chest": (6, 0, 0), "head": (10, 30, 0), "arm_r": (-100, 0, 20), "arm_l": (-70, 0, -40), "leg1_l": (-60, 0, 0), "leg1_r": (-50, 0, 0),
                           "shin1_l": (80, 0, 0), "shin1_r": (88, 0, 0)})
    K.keyed(r, "death", 1.5, [(0, {}), (0.2, K.planted(r, {"chest": (-10, 0, 0), "arm_r": (10, 0, 0)}, legs)), (0.6, knees)] + K.fall(r, 0.6, knees, 1.1, flat, steps=6)
            + [(1.22, K.on_floor(r, K.plus(flat, {"body": (-5, 0, 0)}))), (1.5, K.on_floor(r, flat))], lag=lagc)
    return r


def paint_steam_hammer(r, P):
    P.paint("boiler", COPPER, 2.8, p_plates(5, 4, 0.6), rim=0.8, ao=0.7)
    rivets(P, "boiler", every=2)
    P.paint("pelvis", IRON, 2.8, rim=0.6)
    P.paint("stackp", IRON, 3.2, p_bands(2, 0.7))
    P.paint("capc", BRASS, 3.4, rim=1.0)
    P.paint("visor", IRON, 1.0)
    K.lit(P, "visor", "north", 0, 0, 4, 1, LIT, HOT, a=250)
    f, g = P.face("boiler", "north"), P.face("boiler", "north", glow=True)   # the fire door
    for i in range(3, 7):
        for j in range(4, 7):
            edge = i in (3, 6) or j in (4, 6)
            f.set(i, j, IRON.at(1) if edge else hexc(LIT))
            if not edge:
                g.set(i, j, hexc(LIT, 220))
    P.paint("housing", BRASS, 3.2, p_bands(4, 0.5), rim=1.0, ao=0.6)
    rivets(P, "housing", every=2)
    P.paint("rodp", IRON, 4.4, rim=0.4)
    P.paint("hammerh", IRON, 2.6, p_stone(P.rng, 0.5, 4), rim=0.9, ao=0.8)
    fb = P.face("hammerh", "bottom")
    for i, j in fb.each():
        fb.set(i, j, IRON.at(4.4 - 0.4 * ((i + j) % 2)))  # the face of it, worn bright
    P.paint("arms", COPPER, 3.0)
    P.paint("armsf", IRON, 3.2)
    P.paint("claw", BRASS, 3.4)
    P.paint("thigh1_l", COPPER, 2.6, rim=0.5)
    P.paint("shinb1_l", IRON, 3.0)
    P.paint("footb1_l", IRON, 2.2, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Regulator (the_regulator, the Warden): a riveted boiler on four legs with a pressure dial where a face would be.
# The needle climbs as it speeds up. When the valve on top blows, it has nothing left for a moment.

def regulator():
    r = Rig("the_regulator", (128, 128))
    r.shadow = 1.5
    body = K.at(r.bone("body", (0, 11, 0)), 11)
    body.cube("boilerr", -6, -8, -9, 12, 11, 18)
    for k in range(3):
        body.cube(f"band{k}", -6.5, -8.5, -7 + 6 * k, 13, 12, 2, share=None if k == 0 else "band0")
    body.cube("firebox", -5, 3, 1, 10, 2, 7)
    body.cube("chimney", 2, -13, -6, 3, 5, 3)
    K.legs2(body, "0", (5, 2, -6), 6, 7, 35, thick=(4, 3), foot=(5, 3, 5, -2.5))    # forelegs: knees ahead
    K.legs2(body, "1", (5, 1, 6), 8, 6, -26, thick=(4, 3), foot=(5, 3, 5, -2.5))    # hind legs: hocks behind
    head = body.bone("head", (0, -3, -9))
    head.cube("bezel", -5, -5, -0.6, 10, 10, 1)
    head.cube("dial", -4, -4, -1.4, 8, 8, 1)
    head.bone("needle", (0, 0, -1.5)).cube("needlec", -0.5, -3.5, -0.5, 1, 4, 1)
    valve = body.bone("valve", (-2, -8, 3))
    valve.cube("stemv", -1, -2, -1, 2, 2, 2)
    valve.bone("cap", (0, -2, 0)).cube("capv", -2, -2, -2, 4, 2, 4)
    r.look = [("head", 0.15, 0.1)]
    r.pack()

    HIND = ("1_l", "1_r")
    press = lambda a, up=0.0: {"needle": (0, 0, a), "cap": {"pos": (0, up, 0)}}  # noqa: E731
    K.moves(r, slam="Governor Slam", ring="Spark Ring", clock="Overclock")
    landed = K.planted(r, K.plus(press(140, 0.5), {"body": {"rot": (8, 0, 0), "pos": (0, -2, -2)}}), QUAD)
    blown = K.planted(r, K.plus(press(-50, 3.5), {"body": {"rot": (4, 0, 0), "pos": (0, -2.5, -1)}}), QUAD)
    K.act(r, "slam", K.plus(press(110, 1.0), {"body": {"rot": (-28, 0, 0), "pos": (0, 2, 2)}, "leg0_l": (-52, 0, 0), "leg0_r": (-40, 0, 0), "shin0_l": (34, 0, 0), "shin0_r": (26, 0, 0)}),
          landed, w=1.3, length=0.6, t_hit=0.1, over=0.03, plant=HIND, open_pose=blown)
    squat = K.planted(r, K.plus(press(95, 1.5), {"body": {"pos": (0, -2.2, 0)}}), QUAD)
    burst = K.planted(r, K.plus(press(150, 4.0), {"body": {"pos": (0, 1.2, 0)}}), QUAD)
    K.act(r, "ring", squat, burst, w=1.0, length=0.55, t_hit=0.08, over=0.05, plant=HIND,
          open_pose=K.planted(r, K.plus(press(-40, 3.0), {"body": {"pos": (0, -1.5, 0), "rot": (3, 0, 0)}}), QUAD))
    K.act(r, "clock", K.planted(r, K.plus(press(120, 0.8), {"body": {"rot": (0, 0, 3), "pos": (0, 0.8, 0)}}), QUAD),
          K.planted(r, K.plus(press(170, 1.6), {"body": {"rot": (0, 0, -3), "pos": (0, -1.2, 0)}}), QUAD), w=0.9, length=0.5, t_hit=0.08, over=0.05, plant=HIND,
          rest=press(60))
    K.plain(r, K.plus(press(40), {"body": {"pos": (0, 0.5, 2), "rot": (-4, 0, 0)}}), K.plus(press(70), {"body": {"pos": (0, -0.5, -3.5), "rot": (5, 0, 0)}}),
            heavy=(K.plus(press(80, 0.6), {"body": {"rot": (-12, 0, 0), "pos": (0, 1, 1)}, "leg0_l": (-60, 0, 0), "shin0_l": (30, 0, 0)}),
                   K.planted(r, K.plus(press(110), {"body": {"rot": (6, 0, 0), "pos": (0, -1.2, -1.5)}}), QUAD)),
            # its other swing: the same ram with its other shoulder
            second=(K.plus(press(55), {"body": {"pos": (0, 0.5, 2), "rot": (-4, 16, 0)}}), K.plus(press(85), {"body": {"pos": (0, -0.5, -3.5), "rot": (5, -18, 0)}})),
            plant=QUAD)
    K.loop(r, "idle", 6.0, lambda p: K.plus(press(8 * s(p * 3) + 6 * s(p), 0.8 * bump(p, 0.5, 0.03)), {"body": {"pos": (0, 0.4 * s(p * 2), 0)}, "head": (0, 0, 2 * s(p, 0.3))}), n=24)
    K.loop(r, "walk", 1.1, lambda p: K.plus(press(34 + 6 * s(p * 2)), {"body": (1.2 * s(p * 2), 0, 1.6 * s(p))}))
    K.loop(r, "run", 0.7, lambda p: K.plus(press(96 + 10 * s(p * 4), 0.6 * max(0, s(p * 4))), {"body": (3 + 2 * s(p * 2, 0.25), 0, 2 * s(p))}))
    K.fallback_walk(r, TROT, swing=(16, 24))
    K.loop(r, "angry", 1.6, lambda p: K.plus(press(120 + 14 * s(p * 4), 1.0 + 0.6 * s(p * 4)), {"body": {"pos": (0, -1, 0), "rot": (0, 0, 2 * s(p * 2))}}), plant=QUAD)
    K.flinch(r, K.plus(press(-30, 1.5), {"body": {"rot": (-3, 0, 4), "pos": (0, -0.5, 1.5)}}), plant=QUAD)
    belly = K.plus(press(-90, 6.0), {"leg0_l": (-50, 0, -24), "leg0_r": (-50, 0, 24), "leg1_l": (50, 0, -24), "leg1_r": (50, 0, 24), "shin0_l": (40, 0, 0), "shin0_r": (40, 0, 0),
                                     "shin1_l": (-40, 0, 0), "shin1_r": (-40, 0, 0), "body": (4, 0, 5), "cap": {"scale": (0.02, 0.02, 0.02)}})
    start = K.planted(r, K.plus(press(170, 2.0), {"body": {"pos": (0, 1.5, 0)}}), QUAD)
    sagged = K.planted(r, K.plus(press(40, 5.0), {"body": {"pos": (0, -3, 0), "rot": (3, 0, -3)}}), QUAD)
    skip = ("capv", "stemv", "needlec")
    K.keyed(r, "death", 2.0, [(0, {}), (0.25, start), (0.9, sagged)] + K.fall(r, 0.9, sagged, 1.5, belly, steps=3, skip=skip)
            + [(1.62, K.on_floor(r, K.plus(belly, {"body": (-3, 0, 0)}), skip=skip)), (2.0, K.on_floor(r, belly, skip=skip))])
    return r


def paint_regulator(r, P):
    P.paint("boilerr", COPPER, 2.6, p_plates(6, 5, 0.6), rim=0.7, ao=0.7)
    P.paint("band0", BRASS, 3.4, rim=0.9)
    rivets(P, "band0", K.ALL, every=2, row=(0,))
    P.paint("firebox", IRON, 2.2, rim=0.5)
    for fname in ("west", "east", "south"):  # the grate: fire behind bars
        f, g = P.face("firebox", fname), P.face("firebox", fname, glow=True)
        for i in range(1, f.w - 1, 2):
            f.set(i, 0, hexc(LIT))
            g.set(i, 0, hexc(LIT, 220))
    P.paint("chimney", IRON, 3.0, p_bands(2, 0.7), rim=0.6)
    P.paint("bezel", BRASS, 3.8, rim=1.0)
    f, g = P.face("dial", "north"), P.face("dial", "north", glow=True)
    for i, j in f.each():
        d = math.hypot(i - 3.5, j - 3.5)
        f.set(i, j, hexc("#f4ecd0") if d < 4.2 else BRASS.at(2))
        if d < 4.2:
            g.set(i, j, hexc(LIT, 90))
    for i, j, col in ((0, 5, "#3a3a3a"), (0, 3, "#3a3a3a"), (2, 1, "#3a3a3a"), (5, 1, "#b03020"), (7, 3, "#b03020"), (7, 5, "#b03020")):  # ticks: black, then red
        f.set(i, j, hexc(col))
    for nm in ("dial",):
        for fname in ("west", "east", "top", "bottom"):
            K.fill(P, nm, fname, BRASS.at(3))
    P.paint("needlec", IRON, 0.6)
    P.paint("stemv", BRASS, 3.0)
    P.paint("capv", BRASS, 3.8, rim=1.0)
    K.lit(P, "stemv", "north", 0, 0, 2, 1, LIT, HOT, a=200)
    for tag in ("0", "1"):
        P.paint(f"thigh{tag}_l", COPPER, 2.8, rim=0.5)
        rivets(P, f"thigh{tag}_l", every=2, row=(1,))
        P.paint(f"shinb{tag}_l", IRON, 3.2)
        P.paint(f"footb{tag}_l", IRON, 2.2, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
TICK = [("block.comparator.click", 0.5, 0.8), ("block.lever.click", 0.4, 0.7)]
MONSTERS = [
    K.Monster("cog_hound", "The Cog Hound", cog_hound, paint_cog_hound, kind="runner", box=(0.7, 1.2), sunk=("stem", "bow"),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.66, 0.4), lift=(2.0, 3.6), crouch=0.5, swing=0.22, bob=0.4, roll=0.035,
                                               lag=-0.25, lead=0.12, rear=1.0),
                                "body": K.body(headLevel=0.5, twist=0.25, slope=0.85, land=1.2, shift=0.4, nod=0.05)},
              life=dict(STILL, temperament="hunter", breathDepth=0.0, idles={"look": 3, "sniff": 1, "flick": 2}, springHz=3.0),
              voice={"ambient": TICK + [("block.chain.step", 0.5, 1.2)], "hurt": [("block.copper.hit", 0.9, 1.2), ("block.chain.hit", 0.6, 1.2)],
                     "death": [("block.copper.break", 0.9, 1.1), ("block.chain.break", 0.7, 1.1)], "step": [("block.copper.step", 0.3, 1.4)]}),
    K.Monster("rivet_gunner", "The Riveter", riveter, paint_riveter, kind="crawler", box=(1.0, 1.3),
              motion=lambda r: {"gait": K.gait("ripple", K.room(r, K.legs_of(r), 0.5), duty=(0.72, 0.6), lift=(2.4, 3.6), crouch=0.5, swing=0.26, bob=0.5, roll=0.03, speed=0.7),
                                "body": K.body(headLevel=0.0, twist=0.1, slope=0.7, land=1.3, shift=0.5)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"look": 3, "settle": 1}, headSpeed=5.0, mass=1.6),
              voice={"ambient": [("block.piston.contract", 0.4, 1.3), ("item.crossbow.loading_middle", 0.6, 0.7)], "hurt": [("block.copper.hit", 0.9, 0.9), ("block.dispenser.fail", 0.7, 0.8)],
                     "death": [("block.copper.break", 1.0, 0.8), ("block.piston.contract", 0.8, 0.6)], "step": [("block.piston.extend", 0.25, 1.6)]}),
    K.Monster("spark_drone", "The Governor", governor, paint_governor, kind="floater", box=(0.6, 1.0), lift=5,
              motion={"fly": K.flight(fast=4, bank=0.3, tilt=0.3, climb=0.5)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}),
              voice={"ambient": [("entity.bee.loop", 0.4, 1.5), ("block.comparator.click", 0.4, 1.4)], "hurt": [("block.copper.hit", 0.8, 1.5), ("entity.bee.hurt", 0.4, 1.5)],
                     "death": [("block.copper.break", 0.8, 1.4), ("block.copper_bulb.turn_off", 0.9, 1.2)]}),
    K.Monster("foundry_marshal", "The Steam Hammer", steam_hammer, paint_steam_hammer, kind="strider", box=(1.1, 1.8), sunk=("hammerh", "rodp", "claw"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.64, 0.46), lift=(2.0, 3.4), crouch=0.5, swing=0.32, bob=0.8, roll=0.06),
                                "body": K.body(headLevel=0.2, twist=0.2, land=1.5, shift=0.7)},
              life=dict(STILL, temperament="beast", breath=0.6, breathDepth=0.8, idles={"look": 2, "settle": 2}, mass=2.6, arms=0.3, springHz=1.4),
              voice={"ambient": [("block.smoker.smoke", 0.8, 0.8), ("block.piston.contract", 0.5, 0.6)], "hurt": [("entity.iron_golem.hurt", 0.7, 0.9), ("block.copper.hit", 0.8, 0.7)],
                     "death": [("entity.iron_golem.death", 0.8, 0.9), ("block.fire.extinguish", 0.8, 0.7)], "step": [("entity.iron_golem.step", 0.5, 0.9)]}),
    K.Monster("the_regulator", "The Regulator", regulator, paint_regulator, kind="runner", box=(1.4, 1.9), sunk=("capv", "thigh", "shinb", "footb"),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.7, 0.46), lift=(2.4, 4.0), crouch=0.5, swing=0.3, bob=0.7, roll=0.04,
                                               lag=-0.25, lead=0.12, rear=1.0),
                                "body": K.body(headLevel=0.0, twist=0.12, slope=0.8, land=1.6, shift=0.7)},
              life=dict(STILL, temperament="beast", breath=0.5, breathDepth=0.5, idles={"settle": 2, "look": 1}, mass=3.6, springHz=1.0),
              voice={"ambient": [("block.smoker.smoke", 0.9, 0.6), ("block.blastfurnace.fire_crackle", 0.8, 0.6)] + TICK[:1], "hurt": [("entity.iron_golem.hurt", 0.9, 0.6), ("block.anvil.hit", 0.4, 0.6)],
                     "death": [("entity.iron_golem.death", 1.0, 0.6), ("block.fire.extinguish", 1.0, 0.5)], "step": [("entity.iron_golem.step", 0.8, 0.6)]}),
]
