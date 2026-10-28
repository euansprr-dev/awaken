"""Mother of Spores (SPEC §34, docs/bosses/PLAN.md §5): no longer a ghast but a floating fungal matriarch. A Flier.

A vast cap, about three blocks across, crowned and spotted; a veil of gills hanging from its rim round a pale face; two
long tendrils at the front that she lashes with, four short ones trailing behind; spore sacs on the cap. Her light
story is a sea-green glow in the spots, the gills, the sacs and her eyes.

Moves: Tendril Lash (the front tendrils, one then the other: LIGHTs), a swoop that closes the gap, Spore Lob (the cap
rocks back and flicks a spore at you), the Drop (she rises, hangs there for a beat and falls, the cap slamming the floor:
HEAVY, a ring that reaches behind her too), Spore Rain (she rises, the cap pulses and the veil flares: the signature),
Birth (her sacs burst into the colony), Spore Halo (her second phase) and Bloom (her third).
"""
from kit import plans
from kit.core import Move, keyed
from kit.core import stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES, p_blotch, combine, p_strokes

ID = "mother_of_spores"
SCALE = 0.75  # the hitbox: vanilla's ghast at 0.75, 3 x 3 blocks

FRONT = ("l", "r")
BACK = ("bl", "br", "fl", "fr")


CAP = (("cap", 48, 6), ("cap2", 42, 5), ("cap3", 34, 5), ("cap4", 24, 4), ("crown", 12, 3))  # a stepped dome: name, width, height
VEIL = (("nl", (13, -22, 18, 2)), ("nr", (-13, -22, 18, 2)), ("s", (0, 22, 40, 2)), ("w", (22, 0, 2, 40)), ("e", (-22, 0, 2, 40)))


def body():
    r, core = plans.flier(ID, tex=(512, 512), hover=30)
    cap = core.bone("cap", (0, -10, 0))
    y = 0
    for nm, w, h in CAP:
        y -= h
        cap.cube(nm, -w / 2, y, -w / 2, w, h, w)
    for k, (x, z, sz, base) in enumerate(((11, 7, 7, -11), (-14, -3, 6, -11), (3, -13, 5, -16))):
        cap.cube(f"sac{k}", x - sz / 2, base - sz + 1, z - sz / 2, sz, sz, sz)
    # the veil of gills, open at the front so her face shows between its halves
    for nm, (x, z, w, d) in VEIL:
        v = cap.bone("veil_" + nm, (x, 0, z))
        v.cube("veil_" + nm, -w / 2, 0, -d / 2, w, 10, d)
    torso = core.bone("body", (0, -10, 0))
    torso.cube("body", -8, 0, -8, 16, 18, 16)
    head = core.bone("head", (0, -5, -8))
    head.cube("face", -6, 0, -4, 12, 11, 5)
    head.cube("brow", -6.5, -1, -5, 13, 2, 3)
    # the front tendrils (three tapering links, curled at rest) and the short ones trailing behind (two links)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        t = torso.bone("tl1_" + nm, (side * 5, 17, -5), (-14, 0, side * -14))
        t.cube("tl1_" + nm, -1.5, 0, -1.5, 3, 13, 3, mirror=mir)
        t2 = t.bone("tl2_" + nm, (0, 13, 0), (16, 0, side * 8))
        t2.cube("tl2_" + nm, -1, 0, -1, 2, 12, 2, mirror=mir)
        t3 = t2.bone("tl3_" + nm, (0, 12, 0), (24, 0, 0))
        t3.cube("tl3_" + nm, -1, 0, -1, 2, 11, 2, mirror=mir)
    for nm, (x, z) in (("bl", (5, 5)), ("br", (-5, 5)), ("fl", (7, -1)), ("fr", (-7, -1))):
        sg = 1 if x > 0 else -1
        t = torso.bone("tb1_" + nm, (x, 17, z), (18 if z > 0 else 6, 0, sg * -18))
        t.cube("tb1_" + nm, -1, 0, -1, 2, 8, 2)
        t2 = t.bone("tb2_" + nm, (0, 8, 0), (22, 0, 0))
        t2.cube("tb2_" + nm, -0.5, 0, -0.5, 1, 7, 1)
    r.look = [("head", 0.6, 0.5), ("body", 0.15, 0.1)]
    r.shadow = 1.6
    return r


def _tendrils(pose, front=None, back=None, f2=None, f3=None):
    """Adds the tendrils to a pose: front/back are (rx, ry, rz) for the first link, f2/f3 the links below it."""
    for nm in FRONT:
        sg = 1 if nm == "l" else -1
        if front is not None:
            pose["tl1_" + nm] = (front[0], front[1] * sg, front[2] * sg)
        if f2 is not None:
            pose["tl2_" + nm] = f2
        if f3 is not None:
            pose["tl3_" + nm] = f3
    for nm in BACK:
        if back is not None:
            sg = 1 if nm.endswith("l") else -1
            pose["tb1_" + nm] = (back[0], back[1] * sg, back[2] * sg)
            pose["tb2_" + nm] = (back[0] * 0.6, 0, 0)
    return pose


def _veil(pose, flare):
    """The veil flares out (+) or folds in (-) by degrees."""
    pose["veil_nl"] = (-flare, 0, 0)
    pose["veil_nr"] = (-flare, 0, 0)
    pose["veil_s"] = (flare, 0, 0)
    pose["veil_w"] = (0, 0, -flare)
    pose["veil_e"] = (0, 0, flare)
    return pose


def clips(r):
    # idle: the cap breathes, the veil and the tendrils sway, each link a little behind the one above it
    keys = []
    n, L = 8, 3.6
    import math
    for k in range(n + 1):
        p = k / n
        s, c = math.sin(p * 2 * math.pi), math.cos(p * 2 * math.pi)
        pose = {"core": {"rot": (2 * s, 3 * c, 1.5 * c), "pos": (0, 1.2 * s, 0)}, "cap": (-1.5 * s, 0, 1.0 * c), "head": (3 * c, 4 * s, 0)}
        _veil(pose, 4 + 3 * c)
        for nm in FRONT:
            sg = 1 if nm == "l" else -1
            pose["tl1_" + nm] = (-6 + 6 * s, 0, sg * (4 + 3 * c))
            pose["tl2_" + nm] = (8 * math.sin(p * 2 * math.pi - 0.8), 0, sg * 3 * c)
            pose["tl3_" + nm] = (10 * math.sin(p * 2 * math.pi - 1.6), 0, 0)
        for i, nm in enumerate(BACK):
            ph = i * 0.9
            pose["tb1_" + nm] = (12 + 8 * math.sin(p * 2 * math.pi + ph), 0, 4 * math.cos(p * 2 * math.pi + ph))
            pose["tb2_" + nm] = (10 * math.sin(p * 2 * math.pi + ph - 0.8), 0, 0)
        keys.append((L * p, pose))
    keyed(r, "idle", L, keys, loop=True)
    # fly: the steady lean and the tendrils trailing (it plays on top of the idle whenever she moves)
    keyed(r, "fly", 1.6, [(0, _tendrils({"core": (4, 0, 0)}, back=(8, 0, 0))), (0.8, _tendrils({"core": (6, 0, 0)}, back=(14, 0, 0))),
                          (1.6, _tendrils({"core": (4, 0, 0)}, back=(8, 0, 0)))], loop=True)
    recoil = _veil(_tendrils({"core": {"rot": (-14, 0, 0), "pos": (0, 1, 2)}, "cap": (-6, 0, 0), "head": (-16, 0, 0)},
                             front=(30, 0, 14), back=(30, 0, 0)), -6)
    keyed(r, "hurt", 0.5, [(0, {}), (0.07, recoil), (0.3, {"core": (3, 0, 0), "head": (4, 0, 0)}), (0.5, {})])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        side = _veil(_tendrils({"core": {"rot": (-6, -14 * sg, -12 * sg), "pos": (-sg * 1.5, 0.5, 0)}, "cap": (0, 0, -8 * sg),
                                "head": (-6, -18 * sg, 0)}, front=(20, 0, 20), back=(20, 0, -20 * sg)), -4)
        keyed(r, nm, 0.55, [(0, {}), (0.08, side), (0.3, {"core": (0, 4 * sg, 3 * sg)}), (0.55, {})])
    # broken: she sags, the cap tipping forward, everything hanging limp (held while her stance is broken)
    sag = _veil(_tendrils({"core": {"rot": (24, 0, 6), "pos": (0, -10, 0)}, "cap": (10, 0, 4), "head": (28, 0, 0)}, front=(-10, 0, -2),
                          back=(-20, 0, 0), f2=(-8, 0, 0), f3=(-6, 0, 0)), -10)
    keyed(r, "kneel", 0.6, [(0, {}), (0.25, {"core": {"rot": (10, 0, 0), "pos": (0, -4, 0)}, "head": (14, 0, 0)}), (0.6, sag)])
    keyed(r, "rise", 0.8, [(0, sag), (0.4, {"core": {"rot": (-6, 0, 0), "pos": (0, 2, 0)}, "cap": (-4, 0, 0)}), (0.8, {})])
    fall = _veil(_tendrils({"core": {"rot": (40, 0, 18), "pos": (0, -26, 0)}, "cap": (16, 0, 10), "head": (34, 0, 20)}, front=(-30, 0, -20),
                           back=(-50, 0, 0), f2=(-20, 0, 0), f3=(-14, 0, 0)), -16)
    keyed(r, "death", 2.6, [(0, {}), (0.3, _veil(_tendrils({"core": {"rot": (-18, 0, 0), "pos": (0, 3, 0)}, "head": (-30, 0, 0)},
                                                          front=(40, 0, 30), back=(40, 0, 0)), 30)),
                            (1.2, {"core": {"rot": (20, 0, 8), "pos": (0, -10, 0)}, "cap": (8, 0, 6), "head": (20, 0, 10)}),
                            (1.9, fall), (2.6, fall)])
    mv = [lash("lash", "r"), lash("lash_b", "l", windup=9, recover=12), swoop(), lob(), drop(), rain(), birth(), halo(), bloom(), unfurl()]
    for m in mv:
        stage_clips(r, m)
    return mv


def lash(mid, side, windup=11, active=4, recover=12):
    """Tendril Lash: she turns the shoulder of her cap away, the front tendril curls up behind, then it whips forward."""
    sg = 1 if side == "l" else -1
    t1, t2, t3 = "tl1_" + side, "tl2_" + side, "tl3_" + side
    coil = {"core": {"rot": (-6, 22 * sg, 4 * sg), "pos": (0, 1, 1)}, "cap": (-4, 0, 6 * sg), "head": (0, -16 * sg, 0),
            t1: (40, 0, 30 * sg), t2: (60, 0, 0), t3: (60, 0, 0)}
    whip = {"core": {"rot": (8, -24 * sg, -4 * sg), "pos": (0, -1, -1)}, "cap": (6, 0, -4 * sg), "head": (6, 10 * sg, 0),
            t1: (-100, 0, -14 * sg), t2: (-14, 0, 0), t3: (-6, 0, 0)}
    after = dict(whip)
    after.update({t1: (-70, 0, -24 * sg), t2: (20, 0, 0), t3: (30, 0, 0)})
    return Move(mid, "LIGHT", windup, active, recover, title="Tendril Lash" if mid == "lash" else "", track=10.0, rng=(0, 3.4),
                kinds=("near",), damage=0.8, knock=0.7, plant=(),
                hit=[(t3, (0, 11, 0), 3), (t2, (0, 12, 0), 3)],
                stages={"windup": [(0.4, {"core": {"rot": (0, 8 * sg, 0), "pos": (0, 0.5, 0)}, t1: (14, 0, 10 * sg)}), (1.0, coil)],
                        "active": [(0.5, whip), (1.0, after)], "recover": [(0.5, {"core": (2, -6 * sg, 0), t1: (-20, 0, 0)})]},
                lag={t2: 0.03, t3: 0.06, "head": 0.04})


def swoop():
    """She tips forward and glides at you, the tendrils streaming behind, and lashes both as she arrives (closes the gap)."""
    lean = _tendrils({"core": {"rot": (28, 0, 0), "pos": (0, -4, 0)}, "cap": (8, 0, 0), "head": (-20, 0, 0)}, front=(50, 0, 6),
                     back=(60, 0, 0), f2=(20, 0, 0), f3=(20, 0, 0))
    strike = _tendrils({"core": {"rot": (14, 0, 0), "pos": (0, -2, 0)}, "cap": (4, 0, 0), "head": (-4, 0, 0)}, front=(-90, 0, 10),
                       back=(30, 0, 0), f2=(-10, 0, 0), f3=(-4, 0, 0))
    return Move("swoop", "LIGHT", 12, 10, 14, track=8.0, advance=8.0, rng=(5, 12), kinds=("gap",), damage=0.9, knock=0.8, plant=(),
                hit=[("tl3_l", (0, 11, 0), 3), ("tl3_r", (0, 11, 0), 3), ("body", (0, 18, -8), 8)],
                stages={"windup": [(0.4, {"core": {"rot": (-10, 0, 0), "pos": (0, 3, 1)}, "cap": (-6, 0, 0)}), (1.0, lean)],
                        "active": [(0.6, lean), (1.0, strike)], "recover": [(0.5, {"core": (6, 0, 0)})]},
                lag={"tl2_l": 0.04, "tl2_r": 0.04, "tl3_l": 0.07, "tl3_r": 0.07})


def lob():
    """Spore Lob: the cap rocks back on her, gathering, then flicks forward and throws a spore."""
    back = _veil({"core": {"rot": (-18, 0, 0), "pos": (0, 2, 2)}, "cap": (-16, 0, 0), "head": (-10, 0, 0)}, 10)
    flick = _veil({"core": {"rot": (12, 0, 0), "pos": (0, -1, -1)}, "cap": (18, 0, 0), "head": (6, 0, 0)}, -4)
    return Move("lob", "LIGHT", 12, 4, 12, title="Spore Lob", track=9.0, ability="Spore Lob", origin=(0, 2.6, 0.8), rng=(4, 20),
                kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, {"core": (-4, 0, 0), "cap": (-4, 0, 0)}), (1.0, back)], "active": [(0.5, flick), (1.0, flick)],
                        "recover": [(0.5, {"cap": (4, 0, 0)})]},
                lag={"cap": 0.03, "veil_nl": 0.05, "veil_s": 0.05})


def drop():
    """The Drop: she rises, hangs there a beat (the hold), and falls; the cap's rim slams the floor all round her."""
    up = _veil(_tendrils({"core": {"rot": (-4, 0, 0), "pos": (0, 14, 0)}, "cap": (-6, 0, 0), "head": (16, 0, 0)}, front=(40, 0, 30),
                         back=(50, 0, 0), f2=(50, 0, 0), f3=(50, 0, 0)), 24)
    # the body really falls (rise below: she drops to the floor), so the pose only adds the last squash of the cap
    slam = _veil(_tendrils({"core": {"rot": (6, 0, 0), "pos": (0, -6, 0)}, "cap": (4, 0, 0), "head": (-6, 0, 0)}, front=(-40, 0, 60),
                           back=(-60, 0, 0), f2=(10, 0, 0), f3=(20, 0, 0)), 50)
    settle = dict(slam)
    settle["core"] = {"rot": (2, 0, 0), "pos": (0, -4, 0)}
    return Move("drop", "HEAVY", 16, 4, 22, title="The Drop", hold=10, track=4.0, rng=(0, 2.4), kinds=("area", "behind"), damage=1.4,
                knock=1.2, plant=(), rise=-3.0, disc=3.0,
                hit=[("cap", (24, 0, 0), 6), ("cap", (-24, 0, 0), 6), ("cap", (0, 0, 24), 6), ("cap", (0, 0, -24), 6)],
                stages={"windup": [(0.3, {"core": {"rot": (4, 0, 0), "pos": (0, -2, 0)}}), (1.0, up)], "active": [(0.5, slam), (1.0, slam)],
                        "recover": [(0.4, settle), (0.8, {"core": {"rot": (-2, 0, 0), "pos": (0, -4, 0)}})]},
                lag={"veil_nl": 0.04, "veil_s": 0.04, "veil_w": 0.04, "veil_e": 0.04, "head": 0.05})


def rain():
    """Spore Rain (the signature): she rises, the cap pulses twice, the veil flares wide and the spores fall in circles."""
    rise = _veil(_tendrils({"core": {"rot": (-8, 0, 0), "pos": (0, 10, 0)}, "cap": (-8, 0, 0), "head": (-24, 0, 0)}, front=(20, 0, 40),
                           back=(30, 0, 0)), 30)
    pulse = _veil(_tendrils({"core": {"rot": (-4, 0, 0), "pos": (0, 12, 0)}, "cap": (-2, 0, 0), "head": (-28, 0, 0)}, front=(10, 0, 50),
                            back=(20, 0, 0)), 60)
    pulse2 = dict(pulse)
    pulse2["cap"] = (-12, 0, 0)
    return Move("rain", "PERILOUS", 24, 12, 20, title="Spore Rain", track=0.0, ability="Spore Rain", rng=(0, 24), kinds=("area", "ranged"),
                signature=True, damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, {"core": {"rot": (6, 0, 0), "pos": (0, -3, 0)}, "cap": (6, 0, 0)}), (0.7, rise), (1.0, pulse)],
                        "active": [(0.3, pulse2), (0.6, pulse), (1.0, pulse2)], "recover": [(0.5, _veil({"core": {"rot": (0, 0, 0), "pos": (0, 4, 0)}}, 10))]},
                lag={"veil_nl": 0.05, "veil_s": 0.05, "veil_w": 0.05, "veil_e": 0.05, "head": 0.05})


def birth():
    """Birth: she shudders, her sacs swell and burst, and the colony drops out of the veil."""
    clench = _veil(_tendrils({"core": {"rot": (10, 0, 0), "pos": (0, -2, 0)}, "cap": (8, 0, 0), "head": (20, 0, 0)}, front=(-10, 0, -10),
                             back=(-20, 0, 0)), -14)
    shake_a, shake_b = dict(clench), dict(clench)
    shake_a["core"] = {"rot": (10, 6, 5), "pos": (0.8, -2, 0)}
    shake_b["core"] = {"rot": (10, -6, -5), "pos": (-0.8, -2, 0)}
    burst = _veil(_tendrils({"core": {"rot": (-12, 0, 0), "pos": (0, 3, 0)}, "cap": (-10, 0, 0), "head": (-20, 0, 0)}, front=(40, 0, 40),
                            back=(50, 0, 0)), 40)
    return Move("birth", "NONE", 14, 12, 14, title="Birth", track=0.0, ability="Birth", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.4, clench), (0.6, shake_a), (0.8, shake_b), (1.0, shake_a)],
                                  "active": [(0.3, burst), (1.0, burst)], "recover": [(0.5, {"core": (2, 0, 0)})]},
                lag={"veil_nl": 0.04, "veil_s": 0.04})


def halo():
    """Spore Halo (her second phase): the veil folds in tight, then she turns once and it flares out in a ring of spores."""
    fold = _veil(_tendrils({"core": {"rot": (6, 0, 0), "pos": (0, -2, 0)}, "head": (10, 0, 0)}, front=(-6, 0, -6), back=(-10, 0, 0)), -20)
    out1 = _veil(_tendrils({"core": {"rot": (-4, 180, 0), "pos": (0, 2, 0)}, "head": (-10, 0, 0)}, front=(30, 0, 70), back=(40, 0, 0)), 70)
    out2 = dict(out1)
    out2["core"] = {"rot": (-4, 360, 0), "pos": (0, 2, 0)}
    return Move("halo", "LIGHT", 16, 10, 16, title="Spore Halo", track=0.0, ability="Spore Halo", rng=(0, 8), kinds=("area",), damage=0,
                knock=0, plant=(), stages={"windup": [(1.0, fold)], "active": [(0.5, out1), (1.0, out2)],
                                           "recover": [(0.5, _veil({"core": {"rot": (0, 360, 0), "pos": (0, 1, 0)}}, 10))]},
                lag={"tl2_l": 0.03, "tl2_r": 0.03})


def bloom():
    """Bloom (her third phase): she sinks and closes round herself, then everything opens at once."""
    close = _veil(_tendrils({"core": {"rot": (16, 0, 0), "pos": (0, -8, 0)}, "cap": (10, 0, 0), "head": (24, 0, 0)}, front=(-20, 0, -14),
                            back=(-30, 0, 0), f2=(-20, 0, 0)), -24)
    opn = _veil(_tendrils({"core": {"rot": (-16, 0, 0), "pos": (0, 6, 0)}, "cap": (-16, 0, 0), "head": (-30, 0, 0)}, front=(60, 0, 80),
                          back=(70, 0, 0), f2=(-10, 0, 0), f3=(-10, 0, 0)), 80)
    return Move("bloom", "PERILOUS", 22, 10, 18, title="Bloom", track=0.0, ability="Bloom", rng=(0, 10), kinds=("area",), damage=0,
                knock=0, plant=(), stages={"windup": [(0.5, close), (1.0, close)], "active": [(0.25, opn), (1.0, opn)],
                                           "recover": [(0.5, _veil({"core": (-4, 0, 0)}, 20))]},
                lag={"veil_nl": 0.04, "veil_s": 0.04, "veil_w": 0.04, "veil_e": 0.04})


def unfurl():
    """Her intro: the tendrils uncurl, the veil opens, she lifts her face to you."""
    curled = _veil(_tendrils({"core": {"rot": (14, 0, 0), "pos": (0, -4, 0)}, "head": (24, 0, 0)}, front=(-30, 0, -6), back=(-30, 0, 0),
                             f2=(-40, 0, 0), f3=(-40, 0, 0)), -16)
    opn = _veil(_tendrils({"core": {"rot": (-8, 0, 0), "pos": (0, 3, 0)}, "head": (-18, 0, 0)}, front=(30, 0, 40), back=(30, 0, 0)), 34)
    return Move("unfurl", "NONE", 14, 24, 14, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(1.0, curled)], "active": [(0.4, opn), (1.0, opn)], "recover": [(0.5, {"core": (2, 0, 0)})]},
                lag={"tl2_l": 0.05, "tl2_r": 0.05, "tl3_l": 0.08, "tl3_r": 0.08})


FIGHT = {
    "intro": {"pose": "idle", "move": "unfurl"},
    "phases": [
        {"pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b"]]},
            {"move": "lob", "weight": 3},
            {"move": "swoop", "weight": 2, "then": [["lash_b"]]},
            {"move": "drop", "weight": 1},
            {"move": "drop", "weight": 3, "when": "behind"},
            {"move": "rain", "weight": 1},
        ]},
        # the colony comes with the second phase, as in GateBestiary
        {"transition": "halo", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b"], ["lash_b", "drop"]]},
            {"move": "lob", "weight": 3, "then": [["lob"]]},
            {"move": "swoop", "weight": 2, "then": [["lash_b"]]},
            {"move": "drop", "weight": 3, "when": "behind"},
            {"move": "rain", "weight": 1},
            {"move": "birth", "weight": 1},
            {"move": "halo", "weight": 1},
        ]},
        {"transition": "bloom", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["lash_b", "drop"]]},
            {"move": "lob", "weight": 2, "then": [["lob", "lob"]]},
            {"move": "swoop", "weight": 2, "then": [["lash_b"]]},
            {"move": "drop", "weight": 2},
            {"move": "drop", "weight": 3, "when": "behind"},
            {"move": "rain", "weight": 1},
            {"move": "halo", "weight": 1},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def paint(r, P_):
    cap = Ramp("#1c0f18", "#2a1522", "#3a1c2e", "#4c243a", "#602e46", "#763a52", "#8e4a60")
    gill = Ramp("#4a3e36", "#665848", "#86765e", "#a69478", "#c4b294", "#ddd0b2")
    flesh = Ramp("#3a2232", "#4e2e42", "#663c54", "#7e4c66", "#985e7a", "#b2748e")
    face = Ramp("#7c7268", "#9a9086", "#b8aea2", "#d4cabc", "#ebe3d6")
    green = ("#2fbf8a", "#5fe0b0", "#9cf4d2", "#d8fff0")
    spots = combine(p_blotch("mos_cap", 7, 0.5, 3), p_strokes(3, 2, 0.3, 0.2, 0.4, seed=11))
    tiers = [nm for nm, _, _ in CAP]
    for k, c in enumerate(tiers):
        P_.paint(c, cap, 3.2 + k * 0.25, spots, noise=0.4)
    # the glowing spots: soft blobs, bigger and more of them towards the crown
    import random
    rr = random.Random(R._h("mos_spots"))
    for k, c in enumerate(tiers):
        for fname in ("top",) + SIDES:
            f = P_.face(c, fname)
            g = P_.face(c, fname, glow=True)
            if f.w < 4 or f.h < 3:
                continue
            n = (6 + k * 2) if fname == "top" else 2 + k
            for _ in range(n):
                x, y = rr.randrange(1, f.w - 2), rr.randrange(0, max(1, f.h - 1))
                big = rr.random() < 0.4
                for dx in range(2 if big else 1):
                    for dy in range(2 if big else 1):
                        f.set(x + dx, y + dy, gill.at(4))
                        g.set(x + dx, y + dy, hexc(green[2] if big and (dx + dy) == 1 else green[1], 210))
                if big:
                    for ex, ey in ((-1, 0), (2, 1), (0, -1), (1, 2)):
                        g.set(x + ex, y + ey, hexc(green[0], 110))
    # the gills under the cap: pale radial lines that glow faintly
    f = P_.face("cap", "bottom")
    g = P_.face("cap", "bottom", glow=True)
    cx, cy = f.w / 2, f.h / 2
    import math
    for i, j in f.each():
        a = math.atan2(j - cy, i - cx)
        line = int((a + math.pi) / (2 * math.pi) * 64) % 2 == 0
        f.set(i, j, gill.at(3.4 if line else 2.0))
        if line and (i + j) % 5 == 0:
            g.set(i, j, hexc(green[0], 120))
    for nm, _ in VEIL:
        c = "veil_" + nm
        P_.paint(c, gill, 3.0, lambda f, i, j: (0.8 if i % 3 == 0 else -0.4) if f.name in SIDES else 0)
        for fname in SIDES:
            f = P_.face(c, fname)
            gg = P_.face(c, fname, glow=True)
            for i, j in gg.each():
                if i % 6 == 0 and j > 2:
                    gg.set(i, j, hexc(green[1], 90 + j * 10))
                # a ragged fringe: the gills end at different lengths
                if j >= f.h - 1 - (R._h(c, fname, i) % 4) and j > 5:
                    f.set(i, j, (0, 0, 0, 0))
                    gg.set(i, j, (0, 0, 0, 0))
    for c in ["body"] + [f"tl{k}_{s}" for k in (1, 2, 3) for s in FRONT] + [f"tb{k}_{s}" for k in (1, 2) for s in BACK]:
        P_.paint(c, flesh, 3.0, lambda f, i, j: (-0.9 if j % 3 == 0 else 0.3) if f.name in SIDES else 0)
    for s in FRONT:
        for fname in SIDES:
            edge_glow(P_, "tl3_" + s, fname, green[2], rows=3, alpha=220)
            g = P_.face("tl2_" + s, fname, glow=True)  # suckers down the inside of the lash tendrils
            for j in range(1, g.h, 3):
                g.set(0, j, hexc(green[1], 170))
    for s in BACK:
        for fname in SIDES:
            edge_glow(P_, "tb2_" + s, fname, green[1], rows=2, alpha=180)
    cracks(P_, "body", SIDES, flesh.at(1), (green[0], green[1]), n=2, seed="mos_body", alpha=170)
    # the face: a pale mask, hollow eyes lit sea-green, a thin mouth
    P_.paint("face", face, 3.2, lambda f, i, j: 0.6 if j == 0 else 0)
    P_.paint("brow", cap, 3.0)
    ff = P_.face("face", "north")
    for i, j in ff.each():
        if j in (3, 4, 5) and (i in (2, 3, 4) or i in (7, 8, 9)):
            ff.set(i, j, face.at(0))
    with P_.eyes():
        for x0 in (2, 7):
            for dx in range(3):
                P_.face("face", "north").set(x0 + dx, 4, hexc("#0c1a14"))
                P_.face("face", "north", glow=True).set(x0 + dx, 4, hexc(green[3] if dx == 1 else green[1], 255))
    for i in range(4, 8):
        ff.set(i, 8, face.at(0))
    # the sacs: translucent-looking, lit from inside
    for k in range(3):
        c = f"sac{k}"
        P_.paint(c, Ramp("#2a4a30", "#3a6a44", "#4e8a58"), 2.0)
        for fname in ("top",) + SIDES:
            radial(P_, c, fname, [green[3], green[2], green[1], green[0]], alpha=230)
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.5}
