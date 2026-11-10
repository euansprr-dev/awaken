"""The Lantern Night Market's monsters (SPEC §60). Room: night, paper lanterns, warm colour. So they are paper, and
lacquer black and red, and each carries its own warm lantern light. Most only fight back."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

PAPER = Ramp("#6a4a2c", "#946a3c", "#bc8e52", "#dcb474", "#f0d49a", "#fbecc4")
RED = Ramp("#3a0a0c", "#5e1214", "#8a1c1c", "#b42a24", "#d84a34", "#f07a54")
LACQUER = Ramp("#08060a", "#120e14", "#1e1820", "#2c2430", "#3e3444", "#54485c")
GOLD = Ramp("#4a340c", "#70500f", "#987018", "#c09428", "#e0b848", "#f6dc80")
CLOTH = Ramp("#1c1a2a", "#2a283c", "#3a3850", "#4e4c66", "#66647e", "#84829a")
WARM, HOT = "#ffb44a", "#fff0c0"


def lantern(P, cube, ramp=PAPER, base=3.6, ribs=2, a=170):
    """Lit paper over thin ribs: the whole box glows, dimmer at the ribs."""
    P.paint(cube, ramp, base, rim=0.3)
    for fname in K.SIDES:
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        for i, j in f.each():
            rib = j % ribs == ribs - 1
            if rib:
                f.set(i, j, ramp.at(base - 1.6))
            g.set(i, j, hexc(WARM, a // 3 if rib else a - 12 * abs(j - f.h // 2)))


# ---------------------------------------------------------------------------------------------------------------------
# The Lantern Moth (lantern_moth): a moth whose body is a lit paper lantern. Harmless until something hits it.

def lantern_moth():
    r = Rig("lantern_moth", (64, 64))
    r.shadow = 0.4
    b = r.bone("body", (0, 13, 0), (30, 0, 0))    # it hangs head up, lantern down
    b.cube("thoraxm", -1.5, -3, -1.5, 3, 3, 3)
    ab = b.bone("abdomen", (0, 0, 0))
    ab.cube("lamp", -2, 0, -2, 4, 6, 4)
    ab.cube("lampcap", -1, 6, -1, 2, 1, 2)
    head = b.bone("head", (0, -3, 0))
    head.cube("headm", -1, -2, -1, 2, 2, 2)
    for side, nm in SIDES2:
        head.bone("antenna_" + nm, (side * 0.5, -2, -0.5), (-30, 0, side * 30)).cube("feath_" + nm, -1.5, -5, 0, 3, 5, 0, share=None if side > 0 else "feath_l")
        w = b.bone("wing_" + nm, (side * 1.5, -2, 0.5), (0, side * -10, side * -12))
        w.cube("fore_" + nm, 0 if side > 0 else -9, -4, 0, 9, 7, 0, mirror=side < 0, share=None if side > 0 else "fore_l")
        h = b.bone("wing_low_" + nm, (side * 1.5, 0, 0.5), (0, side * -10, side * 14))
        h.cube("hind_" + nm, 0 if side > 0 else -6, -1, 0, 6, 6, 0, mirror=side < 0, share=None if side > 0 else "hind_l")
    r.look = [("head", 0.3, 0.2)]
    r.pack()

    def flap(a, low=None):
        """The wings swung forward round the body (a degrees; minus: laid back)."""
        low = a * 0.8 if low is None else low
        return {"wing_l": (0, -a, 0), "wing_r": (0, a, 0), "wing_low_l": (0, -low, 0), "wing_low_r": (0, low, 0)}
    glow = lambda k: {"abdomen": {"scale": (k, k, k)}}  # noqa: E731
    lagc = {"wing_low_l": 0.04, "wing_low_r": 0.04, "antenna_l": 0.06, "antenna_r": 0.06}
    K.moves(r, dive="Drawn to Light")
    grounded = K.on_floor(r, K.plus(flap(-50), glow(0.8), {"body": (40, 0, 30), "antenna_l": (40, 0, 0), "antenna_r": (40, 0, 0)}))
    K.act(r, "dive", K.plus(flap(60), glow(1.25), {"body": {"rot": (-26, 0, 0), "pos": (0, 1.5, 1.5)}}), K.plus(flap(-56), glow(1.1), {"body": {"rot": (40, 0, 0), "pos": (0, -1, -5)}}),
          w=0.7, length=0.4, t_hit=0.07, lag=lagc, stuck=grounded)
    K.keyed(r, "dive_open", 2.0, [(t, K.plus(grounded, flap(a))) for t, a in ((0, 0), (0.25, 26), (0.5, -4), (0.8, 18), (1.1, -2), (1.5, 10), (2.0, 0))])   # it beats at the floor, weaker each time
    K.plain(r, K.plus(flap(40), {"body": {"rot": (-16, 0, 0), "pos": (0, 0.5, 1)}}), K.plus(flap(-40), {"body": {"rot": (24, 0, 0), "pos": (0, 0, -3)}}), lag=lagc)
    K.loop(r, "idle", 0.9, lambda p: K.plus(flap(34 * s(p), 30 * s(p, -0.12)), glow(1 + 0.05 * s(p * 2)), {"body": {"pos": (0, 0.8 * s(p, 0.3), 0)}, "antenna_l": (6 * s(p), 0, 0),
                                                                                                         "antenna_r": (6 * s(p, 0.2), 0, 0)}))
    K.loop(r, "fly", 0.45, lambda p: K.plus(flap(44 * s(p), 38 * s(p, -0.12)), {"body": {"rot": (16, 0, 0), "pos": (0, 0.6 * s(p, 0.3), 0)}}))
    K.loop(r, "angry", 0.4, lambda p: K.plus(flap(50 * s(p)), glow(1.2 + 0.1 * s(p * 2)), {"body": (-10, 0, 0)}))
    K.flinch(r, K.plus(flap(-30), glow(0.7), {"body": {"rot": (-20, 0, 30), "pos": (0, 0, 2)}}))
    K.keyed(r, "death", 1.0, [(0, {}), (0.12, K.plus(flap(60), glow(1.4))), (0.45, K.plus(flap(-30), glow(0.5), {"body": {"rot": (30, 0, 60), "pos": (0, -5, 0)}})),
                              (0.8, K.plus(grounded, glow(0.2))), (1.0, K.plus(grounded, flap(-10), glow(0.1)))], lag=lagc)
    return r


def paint_lantern_moth(r, P):
    P.paint("thoraxm", LACQUER, 3.0, rim=0.6)
    P.paint("headm", LACQUER, 2.4)
    for i in (0, 1):
        K.lit(P, "headm", "north", i, 0, 1, 1, WARM, HOT, a=255)
    lantern(P, "lamp", PAPER, 4.0, ribs=2, a=220)
    P.paint("lampcap", LACQUER, 2.6)
    for fname in ("north", "south"):
        f = P.face("feath_l", fname)
        for i, j in f.each():
            f.set(i, j, PAPER.at(3.6) if i == 1 or (j % 2 == 0 and abs(i - 1) <= 1) else (0, 0, 0, 0))
        for nm, col in (("fore_l", RED), ("hind_l", PAPER)):   # paper wings: a lacquer vein, a lantern's eye-spot, a ragged edge
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            cx, cy = f.w * 0.62, f.h * 0.5
            for i, j in f.each():
                d = math.hypot(i - cx, j - cy)
                edge = i == f.w - 1 and j % 2 == 0
                if edge or (i > f.w - 3 and (j in (0, f.h - 1))):
                    f.set(i, j, (0, 0, 0, 0))
                elif d < 1.2:
                    f.set(i, j, hexc(WARM))
                    g.set(i, j, hexc(WARM, 220))
                elif d < 2.2:
                    f.set(i, j, LACQUER.at(1))
                else:
                    f.set(i, j, col.at(3.2 + 0.25 * i - 0.2 * abs(j - cy)) if j != int(cy) or d < 2.2 else LACQUER.at(2.5))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Magpie (pickpocket): a long-legged runner with a hooked mask and a sack bigger than it is. It takes a shard and
# runs; catch it and the sack spills.

def magpie():
    r = Rig("pickpocket", (128, 64))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 9, 0), (14, 0, 0)), 9)
    body.cube("torsom", -3, -4, -3, 6, 6, 6)
    for side, nm in SIDES2:   # bird legs: the joint you see points back
        K.bent_leg(body, "1_" + nm, (2.5, 1, 0), 8, 9, -30, thick=(2, 1), side=side, tilt=14, foot=(4, 2, 4, -2),
                   share=None if side > 0 else ("thigh1_l", "shinb1_l", "footb1_l"))
    sack = body.bone("sack", (0, -4, 3), (-24, 0, 0))
    sack.cube("sackc", -5, -8, -3, 10, 9, 9)
    sack.cube("knot", -1.5, -10, 0, 3, 2, 3)
    neck = body.bone("neck", (0, -4, -2), (16, 0, 0))
    neck.cube("neckm", -1, -3, -1, 2, 3, 2)
    head = neck.bone("head", (0, -3, 0), (-30, 0, 0))
    head.cube("maskc", -2, -3, -3, 4, 4, 4)
    head.cube("beakm", -1, -1, -8, 2, 2, 5)
    head.cube("hookm", -0.5, 1, -8, 1, 2, 1)
    head.bone("crest", (0, -3, 1), (-30, 0, 0)).cube("plumes", 0, -4, 0, 0, 4, 4)
    for side, nm in SIDES2:
        arm = body.bone("arm_" + nm, (side * 3, -3, -1), (-20 if side > 0 else -150, 0, side * -6))
        arm.cube("armm_" + nm, -0.5, 0, -0.5, 1, 5, 1, share=None if side > 0 else "armm_l")
        arm.bone("fore_" + nm, (0, 5, 0), (-40 if side > 0 else -30, 0, 0)).cube("handm_" + nm, -0.5, 0, -0.5, 1, 4, 1, share=None if side > 0 else "handm_l")
    body.bone("tail", (0, 1, 3), (-50, 0, 0)).cube("tailm", -1.5, 0, 0, 3, 0, 7)
    r.look = [("neck", 0.35, 0.2), ("head", 0.45, 0.35)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"head": 0.04, "sack": 0.08, "tail": 0.08, "crest": 0.08, "fore_l": 0.05}
    K.plain(r, {"arm_l": (-60, 0, -10), "fore_l": (-40, 0, 0), "neck": (-14, 0, 0), "head": (-10, 0, 0), "body": {"pos": (0, -0.5, 1.2)}, "sack": (6, 0, 0)},
            {"arm_l": (-70, 0, 20), "fore_l": (30, 0, 0), "neck": (20, 0, 0), "head": (14, 0, 0), "body": {"pos": (0, -1, -2.6)}, "sack": (-10, 0, 0)}, plant=legs, lag=lagc)
    K.loop(r, "idle", 4.0, lambda p: {"neck": (3 * s(p), 26 * (bump(p, 0.25, 0.05) - bump(p, 0.7, 0.05)), 0), "head": (6 * bump(p, 0.48, 0.03), 0, 12 * bump(p, 0.25, 0.04)),
                                      "sack": (1.5 * s(p, 0.2), 0, 1.5 * s(p * 2)), "tail": (8 * bump(p, 0.5, 0.03), 0, 0), "crest": (10 * bump(p, 0.25, 0.04), 0, 0)}, n=20)
    K.loop(r, "walk", 0.7, lambda p: {"body": (2 * s(p * 2), 0, 2.5 * s(p)), "neck": (6 * s(p * 2, 0.2), 0, 0), "head": (-6 * s(p * 2, 0.2), 0, 0), "sack": (3 * s(p * 2, 0.3), 0, 3 * s(p, 0.2)),
                                      "tail": (6 * s(p * 2), 0, 0)})
    K.loop(r, "run", 0.42, lambda p: {"body": (12 + 3 * s(p * 2), 0, 3 * s(p)), "neck": (-4 + 6 * s(p * 2, 0.2), 0, 0), "head": (-8, 0, 0), "sack": (-10 + 6 * s(p * 2, 0.3), 0, 5 * s(p, 0.2)),
                                      "tail": (20 + 8 * s(p * 2), 0, 0), "crest": (30, 0, 0)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)], swing=(24, 36))
    K.loop(r, "angry", 0.8, lambda p: {"neck": (-10, 0, 0), "head": (10 + 6 * s(p * 2), 0, 0), "crest": (40, 0, 0), "arm_l": (-40, 0, -20), "body": {"pos": (0, -1, 0)}}, plant=legs)
    K.flinch(r, {"neck": (-24, 0, 10), "head": (-10, 0, 0), "body": {"rot": (-8, 0, 5), "pos": (0, 0, 1.5)}, "sack": (14, 0, 8)}, plant=legs)
    # caught: the sack comes off its shoulder and empties as it goes down
    start = K.planted(r, {"neck": (-30, 0, 0), "sack": (20, 0, 0), "body": {"pos": (0, 1, 0)}}, legs)
    down = {"body": (70, 0, 10), "neck": (30, 20, 0), "head": (20, 0, 30), "sack": {"rot": (-70, 0, 20), "scale": (1.2, 0.3, 1.2)}, "leg1_l": (60, 0, 0), "leg1_r": (40, 0, 0),
            "shin1_l": (60, 0, 0), "shin1_r": (80, 0, 0), "arm_r": (100, 0, 30), "arm_l": (-40, 0, -30), "tail": (30, 0, 0)}
    K.keyed(r, "death", 1.1, [(0, {}), (0.14, start)] + K.fall(r, 0.14, start, 0.7, down, steps=4) + [(0.82, K.on_floor(r, K.plus(down, {"body": (-6, 0, 0)}))), (1.1, K.on_floor(r, down))], lag=lagc)
    return r


def paint_magpie(r, P):
    P.paint("torsom", CLOTH, 2.2, p_strokes(2, 3, seed=2), rim=0.5)
    f = P.face("torsom", "north")
    for j in range(f.h):
        f.set(f.w // 2, j, PAPER.at(4.4))   # a white bib, as the bird has
    P.paint("sackc", PAPER, 2.4, p_blotch("sack", 4, 0.9), rim=0.5, ao=0.7)
    for fname in K.SIDES:  # patches, and something gold showing through a hole
        f, g = P.face("sackc", fname), P.face("sackc", fname, glow=True)
        for i in range(2, 5):
            for j in range(4, 7):
                f.set(i, j, RED.at(2.4 if (i + j) % 2 else 3.0))
        f.set(f.w - 3, 2, hexc(WARM))
        g.set(f.w - 3, 2, hexc(WARM, 210))
    P.paint("knot", PAPER, 3.2, p_bands(2, 0.8))
    P.paint("neckm", CLOTH, 1.8)
    P.paint("maskc", LACQUER, 2.6, rim=1.0)
    P.paint("beakm", LACQUER, 3.2, rim=0.8)
    P.paint("hookm", GOLD, 4.0)
    for fname in ("west", "east"):
        K.lit(P, "maskc", fname, 1, 1, 1, 1, WARM, HOT, a=255)
        f = P.face("maskc", fname)
        f.set(2, 2, RED.at(4))   # a painted cheek
    for fname in ("west", "east"):
        f = P.face("plumes", fname)
        for i, j in f.each():
            f.set(i, j, LACQUER.at(2 + (i % 2)) if (i + j) % 3 else (0, 0, 0, 0))
    for fname in ("top", "bottom"):
        f = P.face("tailm", fname)
        for i, j in f.each():
            f.set(i, j, LACQUER.at(2.0 + 0.3 * j) if i != 1 else PAPER.at(4.2))
    P.paint("armm_l", CLOTH, 2.6)
    P.paint("handm_l", LACQUER, 3.4)
    P.paint("thigh1_l", CLOTH, 2.4)
    P.paint("shinb1_l", GOLD, 2.4)
    P.paint("footb1_l", GOLD, 3.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Hopping Lantern (wick_slime): a round paper lantern that hops on its tassel, split across the middle. Leave it be
# and it leaves you be. The split opens on its flame when it is angry.

def hopping_lantern():
    r = Rig("wick_slime", (64, 64))
    r.shadow = 0.7
    body = r.bone("body", (0, 20, 0))
    body.cube("lower", -4, -4, -4, 8, 4, 8)
    body.cube("base", -2, 0, -2, 4, 1, 4)
    body.bone("flame", (0, -4, 0)).cube("wick", -1, -3, -1, 2, 3, 2)
    up = body.bone("jaw", (0, -4, 4))             # the upper half: it lifts like a lid, hinged at the back
    up.cube("upper", -4, -4, -8, 8, 4, 8)
    up.cube("capl", -2, -5, -6, 4, 1, 4)
    up.cube("loopl", -0.5, -7, -4.5, 1, 2, 1)
    t = body.bone("tassel", (0, 1, 0))
    t.cube("cord", -0.5, 0, -0.5, 1, 1, 1)
    t.bone("tassel_end", (0, 1, 0)).cube("tuft", -1.5, 0, -1.5, 3, 2, 3)
    r.pack()

    gape = lambda a, k=1.0: {"jaw": (-a, 0, 0), "flame": {"scale": (k, k, k)}}  # noqa: E731
    sq = lambda k: {"body": {"scale": (1 + (1 - k) * 0.6, k, 1 + (1 - k) * 0.6), "pos": (0, -4 * (1 - k), 0)}}  # noqa: E731  (squashed on its tassel)
    K.moves(r, hop="Wick Hop")
    K.act(r, "hop", K.plus(gape(26, 1.3), sq(0.72)), K.plus(gape(48, 1.7), {"body": {"scale": (0.9, 1.2, 0.9), "pos": (0, 3, -2), "rot": (-12, 0, 0)}}), w=0.7, length=0.45, t_hit=0.08,
          open_pose=K.plus(gape(34, 0.5), sq(0.9)))
    K.plain(r, K.plus(gape(30, 1.2), {"body": (-12, 0, 0)}), K.plus(gape(2, 1.0), {"body": {"rot": (18, 0, 0), "pos": (0, 0.5, -2)}}))
    K.loop(r, "idle", 4.0, lambda p: K.plus(gape(1.5 + 1.5 * s(p), 1 + 0.1 * s(p * 5)), {"body": {"rot": (0, 0, 2 * s(p)), "scale": (1, 1 + 0.03 * s(p * 2), 1)}}), n=16)
    K.loop(r, "walk", 0.5, lambda p: K.plus(gape(3 + 3 * max(0, s(p))), {"body": {"scale": (1 - 0.08 * s(p), 1 + 0.12 * s(p), 1 - 0.08 * s(p))}}))
    K.loop(r, "angry", 0.8, lambda p: K.plus(gape(30 + 10 * s(p * 2), 1.4 + 0.2 * s(p * 4)), {"body": (0, 0, 5 * s(p))}))
    K.flinch(r, K.plus(gape(18, 0.6), {"body": {"rot": (-10, 0, 12), "scale": (1.1, 0.9, 1.1)}}))
    K.keyed(r, "death", 0.9, [(0, {}), (0.14, K.plus(gape(50, 1.8), {"body": (-14, 0, 0)})), (0.45, K.on_floor(r, K.plus(gape(70, 0.3), {"body": (0, 0, 80)}))),
                              (0.9, K.on_floor(r, K.plus(gape(80, 0.02), {"body": {"rot": (0, 0, 96), "scale": (1, 0.8, 1)}})))])
    return r


def paint_hopping_lantern(r, P):
    lantern(P, "lower", RED, 3.4, ribs=2, a=190)
    lantern(P, "upper", RED, 3.4, ribs=2, a=190)
    for nm, fname in (("lower", "top"), ("upper", "bottom")):   # inside the split: bright paper, the flame's own colour
        f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
        for i, j in f.each():
            f.set(i, j, PAPER.at(5) if 0 < i < f.w - 1 and 0 < j < f.h - 1 else RED.at(2))
            g.set(i, j, hexc(WARM, 230))
    P.paint("base", LACQUER, 2.6)
    P.paint("capl", LACQUER, 3.0, rim=0.8)
    P.paint("loopl", GOLD, 3.6)
    K.lit_cube(P, "wick", WARM, HOT, a=255, hot=lambda fn, i, j, f: j < 2)
    P.paint("cord", GOLD, 3.0)
    P.paint("tuft", GOLD, 3.4, p_bands(1, 0.8, vertical=True))
    for fname in ("north",):   # two painted eyes that are only paint, and a brush stroke of a character under them
        f = P.face("upper", fname)
        f.set(2, 1, LACQUER.at(0))
        f.set(5, 1, LACQUER.at(0))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Fan Dancer (masked_duelist, the elite): a tall thing made of folding fans, opening and shutting as it moves. Shut,
# the fans turn any blow from the front. It has to open to lunge.

BLADES = 5


def fan_dancer():
    r = Rig("masked_duelist", (128, 64))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 10, 0)), 10)
    body.cube("hips", -2.5, -1, -2, 5, 3, 4)
    for k in range(4):   # a skirt of shut fans
        a = math.tau * k / 4 + math.pi / 4
        body.bone(f"skirt{k}", (2.5 * math.sin(a), 1, 2 * math.cos(a)), (0, math.degrees(a), 0)).cube(f"pleat{k}", -2, 0, 0, 4, 7, 0, share=None if k == 0 else "pleat0")
    K.legs2(body, "1", (1.5, 2, 0), 6, 7, 20, thick=(2, 1), foot=(2, 2, 4, -2))
    chest = body.bone("chest", (0, -1, 0), (4, 0, 0))
    chest.cube("waist", -2, -8, -1.5, 4, 8, 3)
    chest.cube("shoulders", -4, -9, -2, 8, 2, 4)
    head = chest.bone("head", (0, -9, 0))
    head.cube("maskf", -2, -5, -2.5, 4, 5, 4)
    head.bone("crest", (0, -5, 0)).cube("comb", -4, -4, 0, 8, 4, 0)
    for side, nm in SIDES2:
        arm = chest.bone("arm_" + nm, (side * 4.5, -8, 0), (10, 0, side * -10))
        arm.cube("armf_" + nm, -0.5, 0, -0.5, 1, 6, 1, share=None if side > 0 else "armf_l")
        fore = arm.bone("fore_" + nm, (0, 6, 0), (-70, 0, 0))
        fore.cube("foref_" + nm, -0.5, 0, -0.5, 1, 5, 1, share=None if side > 0 else "foref_l")
        hub = fore.bone("fan_" + nm, (0, 5, 0), (-110, 0, 0))     # the fan stands up out of its fist
        for k in range(BLADES):
            hub.bone(f"blade_{nm}{k}", (0, 0, 0.02 * k), (0, 0, (k - 2) * 5.0)).cube(f"slat_{nm}{k}", -1, 0, 0, 2, 10, 0, share=None if (side > 0 and k == 0) else "slat_l0")
    r.look = [("head", 0.5, 0.3), ("chest", 0.15, 0.1)]
    r.pack()

    legs = ("1_l", "1_r")

    def fans(left, right=None):
        """Each fan opened to that many degrees between slats (5 at rest: shut is -5, wide is 28)."""
        right = left if right is None else right
        q = {}
        for nm, sp in (("l", left), ("r", right)):
            for k in range(BLADES):
                q[f"blade_{nm}{k}"] = (0, 0, (k - 2) * sp)
        return q
    lagc = {"fore_l": 0.04, "fore_r": 0.04, "fan_l": 0.08, "fan_r": 0.08, "head": 0.04, "crest": 0.08, **{f"skirt{k}": 0.1 for k in range(4)}}
    cross = K.plus(fans(-5), {"arm_l": (-50, 0, 34), "arm_r": (-50, 0, -34), "fore_l": (-10, 0, 0), "fore_r": (-10, 0, 0), "chest": (6, 0, 0)})     # shut and crossed: its guard
    wide = K.plus(fans(23), {"arm_l": (-20, 0, -70), "arm_r": (-20, 0, 70), "fore_l": (40, 0, 0), "fore_r": (40, 0, 0), "chest": (-10, 0, 0), "head": (-8, 0, 0)})
    K.moves(r, parry="Parry", dance="Fan Dance")
    K.act(r, "parry", cross, K.plus(fans(-5), {"arm_l": (-70, 0, 10), "arm_r": (-70, 0, -10), "chest": (10, 0, 0), "body": {"pos": (0, 0, -1.5)}}), w=0.4, length=0.45, t_hit=0.08,
          plant=legs, lag=lagc, rest=K.scaled(cross, 0.5))
    spent = K.planted(r, K.plus(fans(23), {"arm_l": (-30, 0, -40), "arm_r": (20, 0, 50), "chest": (30, 20, 0), "head": (20, 0, 0), "body": {"pos": (0, -2, -1)}}), legs)
    K.act(r, "dance", K.plus(wide, {"body": {"pos": (0, -1.5, 1.5), "rot": (0, 30, 0)}}),
          K.plus(fans(23), {"arm_l": (-80, 0, 40), "arm_r": (-80, 0, -40), "fore_l": (30, 0, 0), "fore_r": (30, 0, 0), "chest": (16, -40, 0), "body": {"pos": (0, -0.5, -4), "rot": (0, -30, 0)}}),
          w=0.9, length=0.5, t_hit=0.08, plant=legs, lag=lagc, stuck=spent)
    K.plain(r, K.plus(fans(23, -5), {"arm_l": (-30, 0, -60), "fore_l": (30, 0, 0), "chest": (0, 24, 0)}), K.plus(fans(23, -5), {"arm_l": (-80, 0, 30), "fore_l": (40, 0, 0), "chest": (8, -28, 0),
                                                                                                                         "body": {"pos": (0, 0, -2)}}), plant=legs, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: K.plus(fans(-5 + 26 * bump(p, 0.3, 0.07), -5 + 26 * bump(p, 0.75, 0.07)), {"chest": (1.2 * s(p), 4 * s(p, 0.2), 0), "head": (0, 10 * s(p, 0.4), 4 * bump(p, 0.3, 0.06)),
                                                                                                             "arm_l": (-16 * bump(p, 0.3, 0.07), 0, 0), "arm_r": (-16 * bump(p, 0.75, 0.07), 0, 0)}), n=24)
    K.loop(r, "walk", 1.0, lambda p: K.plus(fans(8 + 14 * max(0, s(p)), 8 + 14 * max(0, s(p, 0.5))), {"chest": (2, 6 * s(p), 2 * s(p)), "head": (0, -5 * s(p), 0), "arm_l": (10 * s(p), 0, 0),
                                                                                                   "arm_r": (10 * s(p, 0.5), 0, 0), **{f"skirt{k}": (6 + 4 * s(p * 2, k / 4.0), 0, 0) for k in range(4)}}))
    K.loop(r, "run", 0.6, lambda p: K.plus(fans(23), {"chest": (12, 8 * s(p), 3 * s(p)), "head": (-8, 0, 0), "arm_l": (10, 0, -50), "arm_r": (10, 0, 50), "fore_l": (50, 0, 0), "fore_r": (50, 0, 0),
                                                      **{f"skirt{k}": (14 + 5 * s(p * 2, k / 4.0), 0, 0) for k in range(4)}}))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.0, lambda p: K.plus(K.scaled(cross, 0.8), fans(-5 + 10 * max(0, s(p * 2))), {"head": (8, 0, 4 * s(p))}), plant=legs)
    K.flinch(r, K.plus(fans(14), {"chest": (-10, 8, 4), "head": (-12, 0, 0), "arm_l": (-10, 0, -30), "arm_r": (-10, 0, 30)}), plant=legs)
    knees = K.planted(r, K.plus(fans(23), {"chest": (14, 0, 0), "head": (24, 0, 0), "body": {"pos": (0, -3, 0)}}), legs)
    flat = K.plus(fans(28), {"body": (82, 0, 8), "chest": (4, 0, 0), "head": (10, 30, 0), "arm_l": (-80, 0, -60), "arm_r": (-70, 0, 70), "fore_l": (60, 0, 0), "fore_r": (60, 0, 0),
                             "leg1_l": (-50, 0, 0), "leg1_r": (-60, 0, 0), "shin1_l": (60, 0, 0), "shin1_r": (74, 0, 0)})
    skip = tuple(f"slat_{nm}{k}" for nm in "lr" for k in range(BLADES)) + tuple(f"pleat{k}" for k in range(4)) + ("comb",)
    K.keyed(r, "death", 1.4, [(0, {}), (0.18, K.planted(r, K.plus(fans(28), {"chest": (-12, 0, 0), "arm_l": (-40, 0, -60), "arm_r": (-40, 0, 60)}), legs)), (0.6, knees)]
            + K.fall(r, 0.6, knees, 1.05, flat, steps=5, skip=skip) + [(1.16, K.on_floor(r, K.plus(flat, {"body": (-5, 0, 0)}), skip=skip)), (1.4, K.on_floor(r, flat, skip=skip))], lag=lagc)
    return r


def paint_fan_dancer(r, P):
    P.paint("hips", LACQUER, 2.6, rim=0.6)
    P.paint("waist", LACQUER, 2.2, p_bands(2, 0.6), rim=0.5)
    P.paint("shoulders", RED, 3.0, rim=0.9)
    P.paint("maskf", PAPER, 4.6, rim=0.4)
    f, g = P.face("maskf", "north"), P.face("maskf", "north", glow=True)   # a white mask: two slits, a red mouth
    for i in (0, 3):
        f.set(i, 2, LACQUER.at(0))
        g.set(i, 2, hexc(WARM, 200))
    f.set(1, 4, RED.at(4))
    f.set(2, 4, RED.at(4))
    fan_faces = ("north", "south")
    for fname in fan_faces:
        fc = P.face("comb", fname)
        for i, j in fc.each():
            fc.set(i, j, GOLD.at(3 + (i % 2)) if j >= abs(i - 3.5) * 0.9 - 0.5 else (0, 0, 0, 0))
        fs, gs = P.face("slat_l0", fname), P.face("slat_l0", fname, glow=True)
        for i, j in fs.each():   # a slat: lacquer at the pivot, red paper, a gold edge at its tip
            fs.set(i, j, GOLD.at(4.4) if j >= fs.h - 1 else RED.at(3.4 + 0.3 * i - 0.08 * j) if j > 2 else LACQUER.at(2.4))
            if j >= fs.h - 1:
                gs.set(i, j, hexc(WARM, 150))
        fp = P.face("pleat0", fname)
        for i, j in fp.each():
            fp.set(i, j, RED.at(2.0 + (i % 2) * 0.9) if j < fp.h - 1 else GOLD.at(3.6))
    P.paint("armf_l", LACQUER, 3.0)
    P.paint("foref_l", LACQUER, 3.6)
    P.paint("thigh1_l", LACQUER, 2.8)
    P.paint("shinb1_l", LACQUER, 3.4)
    P.paint("footb1_l", RED, 3.0, rim=0.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Lamplighter (the_lamplighter, the Warden): a small body on stilts as tall as a house, with a snuffing pole. It
# steps between the lamps and puts them out one by one.

def lamplighter():
    r = Rig("the_lamplighter", (128, 128))
    r.shadow = 1.0
    body = K.at(r.bone("body", (0, -8, 0)), -8)
    body.cube("seat", -3, -1, -2.5, 6, 3, 5)
    K.legs2(body, "1", (2.5, 2, 0), 15, 17, 20, thick=(2, 1), foot=(3, 2, 3, -1.5))
    chest = body.bone("chest", (0, -1, 0), (10, 0, 0))
    chest.cube("coat", -3.5, -8, -3, 7, 8, 6)
    for k in range(2):
        chest.bone(f"cape{k}", (-2 + 4 * k, 0, 3)).cube(f"tails{k}", -1.5, 0, 0, 3, 9, 0, share=None if k == 0 else "tails0")
    head = chest.bone("head", (0, -8, 0), (-6, 0, 0))
    head.cube("faceL", -2, -4, -2, 4, 4, 4)
    head.cube("hatbrim", -3.5, -5, -3.5, 7, 1, 7)
    head.cube("hattop", -2, -9, -2, 4, 4, 4)
    belt = chest.bone("lamp", (3.5, -1, -2))
    belt.cube("chainl", -0.5, 0, -0.5, 1, 3, 1)
    belt.cube("lampb", -1.5, 3, -1.5, 3, 4, 3)
    armr = chest.bone("arm_r", (-4.5, -7, 0), (-34, 0, 8))
    armr.cube("sleeveR", -1, 0, -1, 2, 6, 2)
    forer = armr.bone("fore_r", (0, 6, 0), (-30, 0, 0))
    forer.cube("cuffR", -1, 0, -1, 2, 5, 2)
    pole = forer.bone("pole", (0, 4, 0), (20, 0, 0))            # the snuffing pole: it points on down past its hand to the lamps
    pole.cube("shaftp", -0.5, -6, -0.5, 1, 30, 1)
    pole.cube("snuffer", -1.5, 24, -1.5, 3, 3, 3)
    pole.cube("hookp", -0.5, 22, -2.5, 1, 1, 2)
    arml = chest.bone("arm_l", (4.5, -7, 0), (10, 0, -8))
    arml.cube("sleeveL", -1, 0, -1, 2, 6, 2, share="sleeveR")
    forel = arml.bone("fore_l", (0, 6, 0), (-50, 0, 0))
    forel.cube("cuffL", -1, 0, -1, 2, 5, 2, share="cuffR")
    forel.cube("oilcan", -1.5, 5, -2, 3, 3, 4)
    r.look = [("head", 0.5, 0.4), ("chest", 0.15, 0.15)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"fore_r": 0.05, "pole": 0.1, "fore_l": 0.05, "head": 0.04, "lamp": 0.1, "cape0": 0.1, "cape1": 0.12}
    K.moves(r, snuff="Snuff the Lamps", oil="Lamp Oil", step="Between Lamps")
    K.act(r, "snuff", {"arm_r": (-110, 0, 10), "fore_r": (-20, 0, 0), "pole": (-40, 0, 0), "chest": (-12, 20, 0), "head": (-10, 0, 0), "body": {"pos": (0, 1.5, 1)}},
          {"arm_r": (30, 0, -10), "fore_r": (10, 0, 0), "pole": (24, 0, 0), "chest": (18, -24, 0), "head": (14, 0, 0), "body": {"pos": (0, -2, -1.5)}}, w=1.2, length=0.6, t_hit=0.12,
          plant=legs, lag=lagc, open_pose={"arm_r": (34, 0, -10), "pole": (28, 0, 0), "chest": (22, -16, 0), "head": (26, 0, 0), "body": {"pos": (0, -2.5, -1)}})
    K.act(r, "oil", {"arm_l": (-120, 0, -20), "fore_l": (-30, 0, 0), "chest": (-8, -24, 0), "body": {"pos": (0, 0.8, 1)}},
          {"arm_l": (-50, 0, 30), "fore_l": (40, 0, 0), "chest": (10, 30, 0), "body": {"pos": (0, -0.5, -1.5)}}, w=0.8, length=0.5, t_hit=0.09, plant=legs, lag=lagc)
    K.act(r, "step", {"body": {"pos": (0, -5, 1)}, "chest": (16, 0, 0), "head": (10, 0, 0), "arm_r": (20, 0, 0), "pole": (20, 0, 0)},
          {"body": {"pos": (0, 2, -3)}, "chest": (-8, 0, 0), "head": (-10, 0, 0), "arm_r": (-20, 0, 0)}, w=0.6, length=0.55, t_hit=0.12, plant=legs, lag=lagc)
    K.plain(r, {"arm_r": (-60, 0, 0), "pole": (-50, 0, 0), "chest": (-8, 14, 0)}, {"arm_r": (20, 0, 0), "pole": (30, 0, 0), "chest": (14, -16, 0), "body": {"pos": (0, -1.5, -1.5)}},
            heavy=({"arm_r": (-120, 0, 0), "pole": (-60, 0, 0), "chest": (-14, 0, 0), "body": {"pos": (0, 1.5, 0)}}, {"arm_r": (40, 0, 0), "pole": (40, 0, 0), "chest": (22, 0, 0), "body": {"pos": (0, -3, -1)}}),
            plant=legs, lag=lagc)
    K.loop(r, "idle", 8.0, lambda p: {"chest": (1.5 * s(p), 3 * s(p, 0.2), 1.0 * s(p * 2)), "head": (4 * bump(p, 0.5, 0.05), 16 * s(p, 0.35), 0), "arm_r": (3 * s(p, 0.3), 0, 0),
                                      "pole": (4 * s(p, 0.5), 0, 0), "arm_l": (3 * s(p, 0.6), 0, 0), "body": {"pos": (0, 0.8 * s(p * 2), 0)}}, n=16)
    K.loop(r, "walk", 1.5, lambda p: {"chest": (4 + 2 * s(p * 2), 4 * s(p), 3 * s(p)), "head": (-3 * s(p * 2, 0.1), 0, -3 * s(p, 0.1)), "arm_r": (5 * s(p, 0.5), 0, 0), "pole": (5 * s(p, 0.6), 0, 0),
                                      "arm_l": (14 * s(p), 0, 0)})
    K.loop(r, "run", 1.0, lambda p: {"chest": (12 + 3 * s(p * 2), 5 * s(p), 4 * s(p)), "head": (-10, 0, 0), "arm_r": (-16 + 6 * s(p, 0.5), 0, 0), "pole": (-20, 0, 0), "arm_l": (24 * s(p), 0, 0)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)], swing=(14, 20), lengths=(1.5, 1.0))
    K.loop(r, "angry", 1.6, lambda p: {"arm_r": (-50, 0, 0), "pole": (-30 + 8 * s(p * 2), 0, 0), "chest": (6, 0, 0), "head": (10, 0, 4 * s(p)), "body": {"pos": (0, -1.5, 0)}}, plant=legs)
    K.flinch(r, {"chest": (-10, 6, 5), "head": (-12, 0, 0), "arm_r": (-20, 0, 10), "arm_l": (-30, 0, -20), "body": {"pos": (0, -1, 1.5)}}, plant=legs)
    # the stilts go from under it: it sits down hard, then over
    sat = K.planted(r, {"body": {"pos": (0, -6, 0)}, "chest": (20, 0, 0), "head": (20, 0, 0), "arm_r": (30, 0, 0), "pole": (30, 0, 0)}, legs)
    flat = {"body": (80, 0, 6), "chest": (6, 0, 0), "head": (10, 30, 0), "arm_r": (-80, 0, 30), "pole": (60, 0, 0), "arm_l": (-70, 0, -40), "leg1_l": (-40, 0, 6), "leg1_r": (-56, 0, -6),
            "shin1_l": (70, 0, 0), "shin1_r": (96, 0, 0), "lamp": (-80, 0, 0)}
    skip = ("shaftp", "snuffer", "hookp", "tails0", "tails1", "chainl", "lampb")
    K.keyed(r, "death", 2.0, [(0, {}), (0.25, K.planted(r, {"chest": (-12, 0, 0), "head": (-14, 0, 0), "body": {"pos": (0, 1.5, 0)}}, legs)), (0.9, sat)]
            + K.fall(r, 0.9, sat, 1.5, flat, steps=6, skip=skip) + [(1.64, K.on_floor(r, K.plus(flat, {"body": (-4, 0, 0)}), skip=skip)), (2.0, K.on_floor(r, flat, skip=skip))], lag=lagc)
    return r


def paint_lamplighter(r, P):
    P.paint("seat", LACQUER, 2.4, rim=0.5)
    P.paint("coat", CLOTH, 2.4, p_strokes(3, 5, seed=3), rim=0.5, ao=0.5)
    f = P.face("coat", "north")
    for j in range(1, f.h - 1, 2):
        f.set(f.w // 2, j, GOLD.at(4))   # buttons
    for fname in ("north", "south"):
        K.tatter(P, "tails0", CLOTH, 2.2, seed=4, faces=(fname,), fall=0.2)
    P.paint("faceL", LACQUER, 1.2)
    for i in (0, 3):
        K.lit(P, "faceL", "north", i, 2, 1, 1, WARM, HOT, a=255)
    P.paint("hatbrim", LACQUER, 3.0, rim=0.8)
    P.paint("hattop", LACQUER, 2.6, rim=0.6)
    for fname in K.SIDES:
        fh = P.face("hattop", fname)
        for i in range(fh.w):
            fh.set(i, fh.h - 1, RED.at(3.4))
    P.paint("chainl", GOLD, 2.6)
    lantern(P, "lampb", PAPER, 4.2, ribs=2, a=230)
    P.paint("sleeveR", CLOTH, 2.8)
    P.paint("cuffR", CLOTH, 3.4, rim=0.6)
    P.paint("shaftp", LACQUER, 3.4, p_bands(6, 0.5))
    P.paint("snuffer", GOLD, 3.0, rim=1.0, ao=0.8)
    K.fill(P, "snuffer", "bottom", LACQUER.at(0))
    P.paint("hookp", GOLD, 3.8)
    P.paint("oilcan", GOLD, 2.6, rim=0.9)
    K.lit(P, "oilcan", "north", 1, 1, 1, 1, WARM, HOT, a=220)
    P.paint("thigh1_l", LACQUER, 3.0, p_bands(5, 0.5))
    P.paint("shinb1_l", LACQUER, 3.4, p_bands(5, 0.5))
    P.paint("footb1_l", GOLD, 2.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("lantern_moth", "The Lantern Moth", lantern_moth, paint_lantern_moth, kind="floater", box=(0.6, 0.6), lift=5,
              motion={"fly": K.flight(fast=4, bank=0.4, tilt=0.3, climb=0.5)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=2.4),
              voice={"ambient": [("entity.parrot.fly", 0.4, 1.6), ("block.candle.ambient", 0.8, 1.0)], "hurt": [("entity.bat.hurt", 0.5, 1.2), ("block.wool.hit", 0.6, 1.4)],
                     "death": [("entity.bat.death", 0.5, 1.2), ("block.candle.extinguish", 1.0, 0.9)]}),
    K.Monster("pickpocket", "The Magpie", magpie, paint_magpie, kind="strider", box=(0.7, 1.7), sunk=("tailm",),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.6, 0.38), lift=(2.6, 4.4), crouch=0.5, swing=0.24, bob=0.6, roll=0.04, speed=0.8),
                                "body": K.body(headLevel=0.6, twist=0.3, land=1.1, shift=0.5, nod=0.08)},
              life=dict(STILL, temperament="hunter", idles={"look": 4, "flick": 2, "settle": 1}, headSpeed=7.0, springHz=2.6, arms=0.0),
              voice={"ambient": [("entity.parrot.ambient", 0.5, 0.7), ("item.bundle.insert", 0.6, 0.8)], "hurt": [("entity.parrot.hurt", 0.7, 0.7), ("item.bundle.drop_contents", 0.7, 0.9)],
                     "death": [("entity.parrot.death", 0.8, 0.7), ("item.bundle.drop_contents", 1.0, 0.7)], "step": [("entity.chicken.step", 0.4, 0.8)]}),
    K.Monster("wick_slime", "The Hopping Lantern", hopping_lantern, paint_hopping_lantern, kind="slider", box=(0.7, 0.8),
              motion={"body": K.body(headLevel=0.0, twist=0.15, land=1.8, shift=0.0)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=2.4, springGain=1.6),
              voice={"ambient": [("block.candle.ambient", 0.9, 0.8), ("block.wool.step", 0.4, 1.2)], "hurt": [("block.wool.hit", 0.8, 1.2), ("block.bamboo_wood.hit", 0.6, 1.2)],
                     "death": [("block.candle.extinguish", 1.0, 0.8), ("block.wool.break", 0.8, 1.1)], "step": [("block.wool.step", 0.4, 0.9)]}),
    K.Monster("masked_duelist", "The Fan Dancer", fan_dancer, paint_fan_dancer, kind="strider", box=(0.7, 2.0), sunk=("slat", "pleat"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.6, 0.42), lift=(2.2, 3.6), crouch=0.5, swing=0.26, bob=0.5, roll=0.04),
                                "body": K.body(headLevel=0.6, twist=0.35, land=1.0, shift=0.5)},
              life=dict(STILL, temperament="hunter", idles={"look": 3, "settle": 2}, arms=0.2, headSpeed=5.0, springHz=2.0),
              voice={"ambient": [("item.book.page_turn", 0.7, 0.6), ("block.bamboo_wood.step", 0.4, 1.2)], "hurt": [("block.bamboo_wood.hit", 0.9, 1.0), ("entity.witch.hurt", 0.4, 1.3)],
                     "death": [("block.bamboo_wood.break", 0.9, 0.9), ("entity.witch.death", 0.4, 1.3)], "step": [("block.bamboo_wood.step", 0.3, 1.3)]}),
    K.Monster("the_lamplighter", "The Lamplighter", lamplighter, paint_lamplighter, kind="strider", box=(0.9, 3.3), sunk=("shaftp", "snuffer", "hookp", "tails"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.66, 0.5), lift=(3.0, 5.0), crouch=0.5, swing=0.42, bob=0.8, roll=0.035, speed=0.8),
                                "body": K.body(headLevel=0.5, twist=0.2, slope=0.4, land=1.4, shift=0.8)},
              life=dict(STILL, temperament="monster", idles={"look": 3, "settle": 2}, mass=2.0, arms=0.2, headSpeed=3.5, springHz=1.2, springGain=1.4),
              voice={"ambient": [("block.lantern.step", 0.6, 0.6), ("entity.wandering_trader.ambient", 0.4, 0.5)], "hurt": [("block.bamboo_wood.hit", 0.9, 0.6), ("entity.wandering_trader.hurt", 0.5, 0.5)],
                     "death": [("block.bamboo_wood.break", 1.0, 0.5), ("entity.wandering_trader.death", 0.6, 0.5)], "step": [("block.bamboo_wood.step", 0.6, 0.6)]}),
]
