"""The Hive Regent (SPEC §34, §35.11): the Empress, the Hive gate's Lord. A Flier.

A queen who stands upright in the air like a woman in a gown. Black chitin banded in gold, a ruff of pale fur at her
throat, a gown of wax comb hanging from a gold girdle with honey glowing in its cells, and behind her the long banded
abdomen trailing like a train to the stinger. Four tall glass wings hum on her back. Of her four arms the upper pair hold
two amber stinger daggers point up, and the lower pair fold across her waist. A diadem of three amber spikes, two long
feelers, and great compound eyes that glow honey-gold. Her light story is that amber: the eyes, the daggers, the
spikes, the comb's cells, the jewel at her breast and the stinger.

Moves: Regal Cut (one dagger drawn high behind her shoulder and cut down across you, then the other: LIGHTs), the Sting
(the train swings forward under her like a scorpion's tail and the stinger drives out at your chest: HEAVY), Royal Dive
(she climbs, the daggers drawn back, holds, and dives at you daggers first, the wings swept back: HEAVY), Brood (the
train lifts and pulses, her arms sweep out to send them), Honey Rain (she rises with every arm raised and the wings
beating wide, and the comb weeps: the signature), Stinger Volley (the train aims under her and fires twice), Royal Jelly
(she draws in round the jewel the lower hands lift to her mouth and feeds, glowing), Frenzy (head thrown back, every arm
and wing flung out, the train thrashing) and her rising from the comb.
"""
import math

from kit import core
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES, p_strokes

ID = "hive_regent"
SCALE = 2.0  # the hitbox: size 0.6 x 2.4 scaled by 2, 1.2 x 4.8 blocks
HOVER = 48  # her waist's height over the ground, in pixels
ROOT = 12  # how far under the waist the train leaves the gown, at its back hem
SIDES_ = (("l", 1), ("r", -1))
ABD = ((10, 5), (13, 6), (13, 6), (11, 5), (8, 4), (5, 3))  # the abdomen's segments (width, length), waist to tip
# the gown: stiff wax comb in tiers stepping out like a bell (a skep), on two hinges (the upper tier swings from the
# girdle, the lower ones from it); the train comes out from under its back hem: (w, h, d) top to bottom, and how many
# hang from the girdle
GOWN = ((10, 6, 8), (12, 6, 9), (13, 6, 10))
UPPER = 1
WING_STEPS = {"wing": ((3, 3), (7, 5), (11, 8), (13, 9), (11, 6), (7, 4)), "wing2": ((3, 2), (6, 4), (9, 7), (8, 5), (5, 3))}
VOLLEY_FROM = (0.0, 1.4, 2.0)
TRAIN_ON_TIME = {**{f"abd{k}": 0 for k in range(len(ABD))}, "stinger": 0}  # the hook that keeps it off the floor can't lag  # where the stingers leave her: the stinger as the train aims (blocks: x, up, forward)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed as the silhouette round placed it: x, height up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d):
    """A cube as the round drew it: centred on (cx, cz), from y0 up to y0 + h over the bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d)


def body():
    r = R.Rig(ID, tex=(512, 512), translucent=True)
    core_ = r.bone("core", (0, 24 - HOVER, 1), (-3, 0, 0))
    _box(core_, "waist", 0, -1, 0, 7, 6, 6)
    _box(core_, "girdle", 0, -3, 0, 10, 3, 8)
    _box(core_, "girdle_gem", 0, -3, -4.4, 3, 3, 1)
    # the gown of wax comb, panel by panel from the girdle, each flaring a little and swinging on its own
    g, y = _bone(core_, "gown0", 0, -2, -1.5), 0
    for k, (w, h, d) in enumerate(GOWN):
        if k == UPPER:
            g, y = _bone(g, "gown1", 0, y, 0), 0
        y -= h
        _box(g, f"tier{k}", 0, y, 0, w, h, d)
    _box(g, "hem", 0, y - 2, 0, GOWN[-1][0] + 1, 2, GOWN[-1][2] + 1)
    # the abdomen: a train of banded segments from under the gown's back hem (the petiole down to it hidden inside),
    # trailing back and sweeping out to the stinger
    _box(core_, "petiole", 0, -ROOT - 1, 1.5, 4, ROOT, 4)
    seg, prev = core_, 0
    for k, (w, ln) in enumerate(ABD):
        seg = _bone(seg, f"abd{k}", 0, -ROOT if k == 0 else -prev, 2.5 if k == 0 else 0, (50, 0, 0) if k == 0 else (5, 0, 0))
        _box(seg, f"abd{k}", 0, -ln, 0, w, ln, w - 2)
        prev = ln
    st = _bone(seg, "stinger", 0, -prev, 0, (6, 0, 0))
    _box(st, "stinger", 0, -5, 0, 2, 5, 2)
    _box(st, "stinger_tip", 0, -7, 0, 1, 2, 1)
    # the thorax: black chitin, a gold breastplate with the jewel, a ruff of pale fur at the throat
    ch = _bone(core_, "chest", 0, 4, 0, (-2, 0, 0))
    _box(ch, "thorax", 0, 0, 0, 13, 10, 9)
    _box(ch, "bodice", 0, 1, -4.7, 9, 7, 1)
    _box(ch, "jewel", 0, 4, -5.4, 3, 3, 1)
    _box(ch, "ruff", 0, 9, 0.5, 17, 3, 12)
    _box(ch, "ruff2", 0, 11, 1, 13, 2, 9)
    for nm, sg in SIDES_:
        _box(ch, "pauldron_" + nm, sg * 7, 6, 0, 4, 4, 7)
    nk = _bone(ch, "neck", 0, 11, -0.5)
    _box(nk, "neck", 0, 0, 0, 4, 3, 4)
    hd = _bone(nk, "head", 0, 2, -0.5, (6, 0, 0))
    _box(hd, "head", 0, 1, 0, 8, 7, 7)
    _box(hd, "face", 0, -1, -1.5, 5, 3, 4)  # the narrow lower face down to the mandibles
    _box(hd, "diadem", 0, 7, 0, 9, 2, 8)
    for k, (x, h) in enumerate(((0, 7), (3, 5), (-3, 5))):
        _box(hd, f"spike{k}", x, 9, -1, 2, h, 2)
    for nm, sg in SIDES_:
        _box(hd, "eye_" + nm, sg * 3.6, 1, -1.8, 2, 6, 4)  # the great compound eyes, wrapping forward round the head
        mb = _bone(hd, "mandible_" + nm, sg * 1.4, -1, -3.2, (12, 0, sg * -10))
        _box(mb, "mandible_" + nm, 0, -3, 0, 1, 3, 2)
        f0 = _bone(hd, "feeler_" + nm, sg * 2, 8, -2.5, (-30, 0, sg * 24))
        _box(f0, "feeler_" + nm, 0, 0, 0, 1, 10, 1)
        f1 = _bone(f0, "feeler2_" + nm, 0, 10, 0, (55, 0, sg * 18))
        _box(f1, "feeler2_" + nm, 0, 0, 0, 1, 8, 1)
        _box(f1, "feeler_tip_" + nm, 0, 8, 0, 2, 2, 2)
    # four tall glass wings, the upper pair taller, leaning out and back; each a pane stepped into a leaf shape
    for nm, sg in SIDES_:
        for wn, up, rot in (("wing", 9, (-16, 0, sg * 24)), ("wing2", 6, (-8, 0, sg * 60))):
            w = _bone(ch, f"{wn}_{nm}", sg * 3, up, 4.6, rot)
            y = 0
            for k, (ww, hh) in enumerate(WING_STEPS[wn]):
                _box(w, f"{wn}_{nm}{k}", 0, y, 0, ww, hh, 1)
                y += hh
    # the upper arms: the stinger daggers held point up before her shoulders
    for nm, sg in SIDES_:
        ar = _bone(ch, "arm_" + nm, sg * 8, 8, 0, (-36, 0, -sg * 18))
        _box(ar, "arm_" + nm, 0, -10, 0, 3, 10, 3)
        fa = _bone(ar, "forearm_" + nm, 0, -10, 0, (-74, 0, 0))
        _box(fa, "forearm_" + nm, 0, -9, 0, 3, 9, 3)
        _box(fa, "bracer_" + nm, 0, -8, 0, 4, 3, 4)
        hn = _bone(fa, "hand_" + nm, 0, -9, 0)
        _box(hn, "hand_" + nm, 0, -3, 0, 3, 3, 3)
        dg = _bone(hn, "dagger_" + nm, 0, -1.5, 0, (118, 0, 0))
        _box(dg, "pommel_" + nm, 0, -4, 0, 2, 2, 2)
        _box(dg, "grip_" + nm, 0, -2, 0, 1, 4, 1)
        _box(dg, "guard_" + nm, 0, 2, 0, 6, 2, 2)
        for k, (w, h, d) in enumerate(((3, 6, 1), (2, 6, 1), (1, 5, 1))):
            _box(dg, f"blade_{nm}{k}", 0, 4 + 6 * k, 0, w, h, d)
    # the lower arms folded across her waist
    for nm, sg in SIDES_:
        lo = _bone(ch, "arm_lo_" + nm, sg * 6, 4, -1.5, (-24, 0, -sg * 10))
        _box(lo, "arm_lo_" + nm, 0, -7, 0, 2, 7, 2)
        lf = _bone(lo, "forearm_lo_" + nm, 0, -7, 0, (-62, 0, sg * 64))
        _box(lf, "forearm_lo_" + nm, 0, -6, 0, 2, 6, 2)
        _box(lf, "hand_lo_" + nm, 0, -8, 0, 2, 2, 2)
    r.look = [("head", 0.5, 0.4), ("chest", 0.15, 0.1)]
    r.shadow = 1.0
    # secondary parts (SPEC §35.10): the train and the gown trail her, the feelers swing, the folded hands sway
    r.springs = [(f"abd{k}", g, hz, 0.25) for k, (g, hz) in enumerate(((0.2, 2.6), (0.3, 2.4), (0.4, 2.2), (0.45, 2.0), (0.5, 1.8), (0.5, 1.7)))] \
        + [("stinger", 0.5, 2.0, 0.3), ("gown0", 0.3, 2.2, 0.3), ("gown1", 0.4, 2.0, 0.3)] \
        + [(f"feeler_{s}", 0.35, 2.6, 0.3) for s in "lr"] + [(f"feeler2_{s}", 0.6, 2.2, 0.25) for s in "lr"] \
        + [(f"mandible_{s}", 0.2, 4.0, 0.4) for s in "lr"]
    r.guard = ["stinger", "abd5", "gown1"]
    return r


# the ground (SPEC §35.10): the train curls up off the floor, a gown panel lifts on its own hinge
TRAIN = [(["stinger", "abd5", "abd4"], [("abd0", 0, 60), ("abd1", 0, 40), ("abd2", 0, 40)])]
HEM = [(["gown1"], [("gown1", 0, 50), ("gown0", 0, 40)])]
# lying on the floor at the end of the fall: the arms, the feelers and the wings lift to lie along it
FLOOR = [([f"forearm_{s}", f"hand_{s}", f"dagger_{s}"], [(f"arm_{s}", 0, 90), (f"arm_{s}", 2, 60), (f"forearm_{s}", 0, 90)]) for s in "lr"] \
    + [([f"forearm_lo_{s}"], [(f"arm_lo_{s}", 0, 90)]) for s in "lr"] \
    + [([f"{w}_{s}"], [(f"{w}_{s}", 0, 90), (f"{w}_{s}", 2, 60)]) for s in "lr" for w in ("wing", "wing2")] \
    + [([f"feeler2_{s}"], [(f"feeler_{s}", 0, 90)]) for s in "lr"] + [(["gown1"], [("gown1", 0, 70), ("gown0", 0, 50)])]


def _blade_tip():
    return (0, -(4 + 6 + 6 + 5), 0)


def _add(pose, b, rot=(0, 0, 0)):
    v = pose.get(b, (0, 0, 0))
    if isinstance(v, dict):
        pose[b] = {"rot": tuple(a + c for a, c in zip(v.get("rot", (0, 0, 0)), rot)), "pos": v.get("pos", (0, 0, 0))}
    else:
        pose[b] = tuple(a + c for a, c in zip(v, rot))
    return pose


def _wings(pose, spread=0.0, sweep=0.0):
    """Her four wings: spread out wider (+) or folded in down her back (-), degrees; swept back (+) or forward."""
    for nm, sg in SIDES_:
        _add(pose, "wing_" + nm, (-sweep, 0, sg * spread))
        _add(pose, "wing2_" + nm, (-sweep * 0.8, 0, sg * spread * 0.7))
    return pose


def _arm(pose, side, swing=0.0, out=0.0, bend=0.0, blade=0.0, twist=0.0):
    """An upper arm: swung back (+) or forward (-), raised out from her side (+), the elbow bent more (+) or opened, the
    dagger tipped forward along the arm (+) or back up, the arm turned on its length."""
    sg = 1 if side == "l" else -1
    _add(pose, "arm_" + side, (swing, sg * twist, -sg * out))
    _add(pose, "forearm_" + side, (-bend, 0, 0))
    _add(pose, "dagger_" + side, (blade, 0, 0))
    return pose


def _low(pose, open_=0.0, lift=0.0):
    """The lower arms: unfolded and opened out (+) or folded tighter (-); lifted toward her chin (+)."""
    for nm, sg in SIDES_:
        _add(pose, "arm_lo_" + nm, (-lift, 0, -sg * open_ * 0.6))
        _add(pose, "forearm_lo_" + nm, (-lift * 0.6, 0, -sg * open_))
    return pose


def _train(pose, curl=0.0, sway=0.0, bend=None):
    """The train: swung forward under her (+) or lifted up behind (-) at the waist; bend curls the rest of it on the
    same way (by default a little of the swing), each segment that much; sway bends it to one side."""
    bend = curl * 0.06 if bend is None else bend
    for k in range(len(ABD)):
        _add(pose, f"abd{k}", (-(curl if k == 0 else bend), 0, sway * (0.4 + 0.12 * k)))
    _add(pose, "stinger", (-bend, 0, sway * 0.6))
    return pose


def _pose(core=(0, 0, 0), at=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0), wings=(0.0, 0.0), arms=(0.0, 0.0, 0.0, 0.0),
          low=(0.0, 0.0), train=(0.0, 0.0)):
    """A whole-body key: the core's turn and shift, the chest, the head, the wings (spread, sweep), both upper arms
    (swing, out, bend, blade), the lower arms (open, lift) and the train (curl, sway)."""
    p = {"core": {"rot": core, "pos": at}, "chest": chest, "head": head}
    _wings(p, *wings)
    for s in "lr":
        _arm(p, s, *arms)
    _low(p, *low)
    _train(p, *train)
    return p


def _gown(pose, swing=0.0):
    """The gown swings back (+, the hem trailing) or forward, the lower tiers a little more."""
    _add(pose, "gown0", (swing * 0.6, 0, 0))
    _add(pose, "gown1", (swing * 0.5, 0, 0))
    return pose


def clips(r):
    two = 2 * math.pi
    # idle: she hangs in the air on the hum of her wings, which beat three times a second (each pair a beat apart and
    # the body bobbing on every stroke), while she sways slowly, her feelers searching and the train swinging
    keys, n, L, beats = [], 72, 4.0, 12
    for k in range(n + 1):
        p = k / n
        s, c = math.sin(p * two), math.cos(p * two)
        b = math.sin(p * two * beats)
        b2 = math.sin(p * two * beats - 1.1)
        pose = {"core": {"rot": (2 * s, 3 * c, 1.5 * math.sin(p * two * 2)), "pos": (0, 1.4 * s + 0.35 * math.sin(p * two * beats + 0.8), 0)},
                "chest": (-1.5 * s, -1.5 * c, 0), "head": (3 * math.sin(p * two - 0.5), 6 * math.sin(p * two * 2 + 0.3), 2 * c)}
        for nm, sg in SIDES_:
            pose["wing_" + nm] = (-7 * b, 0, sg * 11 * b)
            pose["wing2_" + nm] = (-6 * b2, 0, sg * 9 * b2)
            pose["arm_" + nm] = (2.5 * math.sin(p * two - 0.8), 0, sg * 1.5 * c)
            pose["forearm_" + nm] = (-3 * math.sin(p * two - 1.3), 0, 0)
            pose["feeler_" + nm] = (6 * math.sin(p * two * 2 + sg), 0, sg * 5 * math.cos(p * two + sg * 0.7))
            pose["feeler2_" + nm] = (8 * math.sin(p * two * 2 + sg - 0.9), 0, sg * 4 * math.cos(p * two * 3))
            pose["mandible_" + nm] = (0, 0, sg * 6 * max(0.0, math.sin(p * two * 3)))
        for i in range(len(ABD)):
            pose[f"abd{i}"] = (3 * math.sin(p * two - 0.6 * i), 0, 3.5 * math.cos(p * two - 0.5 * i))
        for i in range(2):
            pose[f"gown{i}"] = (2 * math.sin(p * two - 0.9 - 0.5 * i), 0, 1.5 * math.cos(p * two - 0.6 * i))
        keys.append((L * p, pose))
    keyed(r, "idle", L, keys, loop=True)
    # fly: she leans into it, the wings swept and beating harder, the train and the gown streaming behind
    fk, fn = [], 24
    for k in range(fn + 1):
        p = k / fn
        b = math.sin(p * two * 4)
        pose = _pose(core=(12 + 2 * math.sin(p * two), 0, 0), wings=(4 + 6 * b, 12), arms=(10, -4, 6, -6), low=(0, 0), train=(-12 - 3 * math.sin(p * two), 0))
        _gown(pose, 18)
        fk.append((1.6 * p, pose))
    keyed(r, "fly", 1.6, fk, loop=True)
    recoil = _pose(core=(-14, 0, 0), at=(0, 1, 2), chest=(-8, 0, 0), head=(-20, 0, 12), wings=(14, -6), arms=(20, 14, -20, 0), low=(10, 0), train=(-14, 0))
    keyed(r, "hurt", 0.5, [(0, {}), (0.07, recoil), (0.3, _pose(core=(3, 0, 0), head=(4, 0, 0), wings=(-3, 0))), (0.5, {})])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        side = _pose(core=(-6, -14 * sg, -12 * sg), at=(-sg * 1.5, 0.5, 0), chest=(-4, 0, -6 * sg), head=(-8, -18 * sg, 10 * sg),
                     wings=(6, -4), arms=(10, 8, -10, 0), low=(6, 0), train=(-4, 12 * sg))
        keyed(r, nm, 0.55, [(0, {}), (0.08, side), (0.3, _pose(core=(0, 4 * sg, 3 * sg), wings=(-2, 0))), (0.55, {})])
    # broken: the hum fails, she sinks, wings drooping and the daggers lowered (held while she's broken)
    sag = _pose(core=(14, 0, 6), at=(0, -14, 0), chest=(14, 0, 0), head=(22, 0, 8), wings=(-26, 24), arms=(-6, -6, -40, -40), low=(-10, 0), train=(-6, 0))
    keyed(r, "kneel", 0.6, [(0, {}), (0.25, _pose(core=(7, 0, 2), at=(0, -6, 0), chest=(6, 0, 0), head=(10, 0, 4), wings=(-10, 10),
                                                  arms=(-2, -2, -16, -16), train=(-2, 0))), (0.6, sag)])
    keyed(r, "rise", 0.8, [(0, sag), (0.4, _pose(core=(-6, 0, 0), at=(0, 2, 0), chest=(-4, 0, 0), wings=(10, -6), arms=(4, 4, 6, 0))), (0.8, {})])
    # death: a last shriek with every wing flung out; then the hum stops and she falls, the wings folding over her
    shriek = _pose(core=(-18, 0, 0), at=(0, 3, 0), chest=(-12, 0, 0), head=(-36, 0, 18), wings=(30, -10), arms=(30, 50, -30, 0), low=(40, 20), train=(-24, 0))
    slump = _pose(core=(22, 0, 8), at=(0, -14, 0), chest=(14, 0, 4), head=(24, 0, 10), wings=(-20, 20), arms=(-4, -10, -40, -30), low=(-10, 0), train=(6, 0))
    fall = _pose(core=(70, 0, 16), at=(0, -34, -4), chest=(14, 0, 6), head=(26, 0, 20), wings=(-34, 36), arms=(-40, 30, -50, -20), low=(10, 0), train=(-30, 8))
    keyed(r, "death", 2.6, [(0, {}), (0.3, shriek), (1.1, slump), (1.8, fall), (2.6, fall)])
    mv = [cut("cut", "r"), cut("cut_b", "l", windup=9, recover=12), sting(), dive(), brood(), rain(), volley(), jelly(), frenzy(), emerge(), lash()]
    for m in mv:
        stage_clips(r, m)
    core.ground_pass(r, TRAIN, clips=list(r.clips), skip=())
    core.ground_pass(r, HEM, clips=list(r.clips), skip=())
    core.ground_pass(r, FLOOR, clips=["death"], skip=())
    return mv


def cut(mid, side, windup=11, active=4, recover=12):
    """Regal Cut: one dagger is drawn up high behind her shoulder, her body turning away with it and the wings flaring,
    then she leans in and cuts it down across you; the other arm swings back to balance."""
    sg = 1 if side == "l" else -1
    other = "r" if side == "l" else "l"
    pre = _pose(core=(-2, -sg * 6, 0), wings=(4, 0))
    _arm(pre, side, 10, 20, 10, -10)
    coil = _pose(core=(-8, -sg * 22, sg * 6), at=(0, 2, 2), chest=(-6, -sg * 10, 0), head=(-4, sg * 14, 4 * sg), wings=(10, -4), train=(-6, sg * 10))
    _arm(coil, side, 30, 96, 40, -20, 0)
    _arm(coil, other, -10, 6, 0, 0)
    _low(coil, 6, 0)
    hit = _pose(core=(18, sg * 18, -sg * 6), at=(0, -3, -6), chest=(10, sg * 10, 0), head=(14, -sg * 8, 0), wings=(-4, 10), train=(8, -sg * 8))
    _arm(hit, side, -70, -12, -20, 60, 0)
    _arm(hit, other, 18, 18, 10, 0)
    after = _pose(core=(21, sg * 22, -sg * 8), at=(0, -4, -7), chest=(12, sg * 12, 0), head=(16, -sg * 10, 0), wings=(-6, 12), train=(10, -sg * 10))
    _arm(after, side, -64, -36, -30, 70, 0)
    _arm(after, other, 22, 22, 10, 0)
    back = _pose(core=(8, sg * 6, 0), at=(0, -1, -2), chest=(4, 0, 0), wings=(-2, 4))
    _arm(back, side, -20, -6, -8, 20)
    return Move(mid, "LIGHT", windup, active, recover, title="Regal Cut" if mid == "cut" else "", track=10.0, rng=(0, 3.0),
                kinds=("near",), damage=0.8, knock=0.6, plant=(), hit=[("dagger_" + side, _blade_tip(), 2.5)],
                stages={"windup": [(0.3, pre), (1.0, coil)], "active": [(0.5, hit), (1.0, after)], "recover": [(0.5, back)]},
                lag={"dagger_" + side: 0.03})


def sting():
    """The Sting: the train draws back and up behind her as she rears, wings flaring and the daggers lifted out of the
    way; she holds, then it whips forward under her like a scorpion's tail and the stinger drives out at your chest."""
    draw = _pose(core=(-6, 0, 0), at=(0, 3, 2), chest=(-4, 0, 0), head=(-8, 0, 4), wings=(12, -6), arms=(14, 26, 20, -20), low=(14, 0), train=(-26, 0))
    rear = _pose(core=(-20, 0, 0), at=(0, 6, 4), chest=(-10, 0, 0), head=(-16, 0, 8), wings=(20, -10), arms=(20, 44, 30, -30), low=(24, 0), train=(-44, 0))
    _gown(rear, -8)
    drive = _pose(core=(-26, 0, 0), at=(0, 2, -8), chest=(-6, 0, 0), head=(10, 0, 0), wings=(-6, 16), arms=(-10, 30, 10, -10), low=(6, 0), train=(88, 0, 5))
    _gown(drive, 30)
    after = _pose(core=(-24, 0, 0), at=(0, 1, -9), chest=(-4, 0, 0), head=(12, 0, 0), wings=(-8, 18), arms=(-12, 28, 10, -10), low=(4, 0), train=(94, 0, 5))
    _gown(after, 34)
    # the train hooks up as it swings under her each way, so its tip clears the floor
    swing = _pose(core=(-24, 0, 0), at=(0, 4, -3), chest=(-8, 0, 0), head=(2, 0, 4), wings=(8, 4), arms=(4, 36, 20, -20), low=(14, 0), train=(30, 0, -30))
    _gown(swing, 10)
    back = _pose(core=(-10, 0, 0), at=(0, 1, -3), wings=(-2, 6), arms=(0, 10, 4, 0), train=(50, 0, -22))
    through = _pose(core=(-22, 0, 0), at=(0, 1, -8), chest=(-3, 0, 0), head=(10, 0, 0), wings=(-6, 14), arms=(-8, 24, 8, -8), low=(4, 0), train=(84, 0, 4))
    _gown(through, 26)
    return Move("sting", "HEAVY", 18, 5, 22, title="The Sting", hold=8, track=5.0, rng=(0, 3.4), kinds=("near",), damage=1.3, knock=1.0,
                plant=(), through=through, hit=[("stinger", (0, 7, 0), 2.5), ("abd5", (0, 3, 0), 3)], lag=TRAIN_ON_TIME,
                stages={"windup": [(0.2, core._scaled(draw, 0.35)), (0.5, draw), (1.0, rear)], "active": [(0.3, swing), (0.65, drive), (1.0, after)],
                        "recover": [(0.45, back), (0.68, _pose(core=(-4, 0, 0), train=(22, 0, -12)))]})


def dive():
    """Royal Dive: she climbs, rearing, both daggers drawn back past her shoulders and the wings raised high; holds at the
    top of the climb; then pitches forward and dives at you daggers first, the wings swept flat back and the train and
    gown streaming behind; she pulls up out of it."""
    climb = _pose(core=(-14, 0, 0), at=(0, 8, 3), chest=(-8, 0, 0), head=(-6, 0, 4), wings=(18, -12), arms=(40, 30, 40, -30), low=(-6, 0), train=(-20, 0))
    top = _pose(core=(-22, 0, 0), at=(0, 12, 5), chest=(-12, 0, 0), head=(-4, 0, 6), wings=(26, -18), arms=(56, 36, 50, -40), low=(-10, 0), train=(-30, 0))
    _gown(top, -6)
    dv = _pose(core=(56, 0, 0), at=(0, -4, -10), chest=(8, 0, 0), head=(-34, 0, 0), wings=(-12, 46), arms=(-74, 10, -30, 70), low=(-14, 0), train=(-46, 0))
    _gown(dv, 50)
    low_ = _pose(core=(50, 0, 0), at=(0, -6, -12), chest=(10, 0, 0), head=(-30, 0, 0), wings=(-14, 50), arms=(-80, 4, -34, 74), low=(-14, 0), train=(-50, 0))
    _gown(low_, 56)
    pull = _pose(core=(-12, 0, 0), at=(0, 2, -4), chest=(-6, 0, 0), head=(-4, 0, 0), wings=(16, -4), arms=(10, 20, 10, 0), train=(-10, 0))
    through = _pose(core=(40, 0, 0), at=(0, -4, -10), chest=(8, 0, 0), head=(-24, 0, 0), wings=(-8, 40), arms=(-60, 12, -24, 54), low=(-10, 0), train=(-40, 0))
    _gown(through, 40)
    return Move("dive", "HEAVY", 16, 10, 22, title="Royal Dive", hold=8, track=6.0, lock=10, advance=8.0, ability="Royal Dive", rng=(5, 14),
                kinds=("gap",), damage=1.2, knock=1.0, plant=(), through=through,
                hit=[("dagger_l", _blade_tip(), 2.5), ("dagger_r", _blade_tip(), 2.5), ("chest", (0, -6, -2), 6)],
                stages={"windup": [(0.2, core._scaled(climb, 0.35)), (0.5, climb), (1.0, top)], "active": [(0.4, dv), (1.0, low_)], "recover": [(0.55, pull)]})


def brood():
    """Brood: the train lifts and curls up behind her and pulses three times as the brood crawls out of it; her arms
    sweep out wide to send them, the wings buzzing."""
    lift = _pose(core=(-8, 0, 0), at=(0, 2, 0), chest=(-6, 0, 0), head=(-12, 0, 6), wings=(10, -4), arms=(10, 30, 10, -10), low=(20, 0), train=(-40, 0))

    def pulse(sg, k):
        p = _pose(core=(-8, 3 * sg, 2 * sg), at=(0.4 * sg, 2, 0), chest=(-6, 0, 0), head=(-14, 4 * sg, 6), wings=(14 + 4 * k, -6),
                  arms=(-10 - 4 * k, 60 + 6 * k, -10, 20), low=(30, 10), train=(-50 - 8 * k, 6 * sg))
        return p
    send = _pose(core=(10, 0, 0), at=(0, 0, -2), chest=(6, 0, 0), head=(6, 0, 0), wings=(16, 4), arms=(-40, 70, -20, 40), low=(40, 10), train=(-30, 0))
    return Move("brood", "NONE", 14, 18, 14, title="Brood", track=0.0, ability="Brood", rng=(0, 99), kinds=("summon",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, core._scaled(lift, 0.6)), (1.0, lift)],
                        "active": [(0.25, pulse(1, 0)), (0.5, pulse(-1, 1)), (0.75, pulse(1, 2)), (1.0, send)],
                        "recover": [(0.5, _pose(core=(3, 0, 0), wings=(4, 0), arms=(-8, 20, 0, 10), low=(10, 0), train=(-10, 0)))]})


def rain():
    """Honey Rain (the signature): she sinks, gathering, then rises with all four arms going up, the daggers pointing at
    the roof and her face to it, the wings spread wide and beating; the train lifts and pulses, and the comb weeps."""
    sink = _pose(core=(8, 0, 0), at=(0, -4, 0), chest=(8, 0, 0), head=(12, 0, 0), wings=(-12, 10), arms=(-10, -6, -30, -20), low=(-10, 0), train=(10, 0))
    up = _pose(core=(-8, 0, 0), at=(0, 10, 0), chest=(-12, 0, 0), head=(-28, 0, 14), wings=(22, -8), arms=(-150, 30, -10, -40), low=(60, 40), train=(-30, 0))
    wide = _pose(core=(-5, 0, 0), at=(0, 12, 0), chest=(-16, 0, 0), head=(-34, 0, 16), wings=(30, -12), arms=(-160, 40, -20, -50), low=(70, 50), train=(-40, 0))
    beat = _pose(core=(-7, 0, 0), at=(0, 11, 0), chest=(-13, 0, 0), head=(-30, 0, 14), wings=(18, -6), arms=(-152, 34, -14, -44), low=(64, 44), train=(-34, 0))
    for p in (up, wide, beat):
        _gown(p, -6)
    return Move("rain", "PERILOUS", 24, 12, 20, title="Honey Rain", track=0.0, ability="Honey Rain", rng=(0, 22), kinds=("area", "ranged"),
                signature=True, damage=0, knock=0, plant=(), through=wide,
                stages={"windup": [(0.3, sink), (0.7, up), (1.0, wide)], "active": [(0.3, beat), (0.6, wide), (1.0, beat)],
                        "recover": [(0.5, _pose(at=(0, 4, 0), wings=(8, -2), arms=(-40, 14, 0, -10), low=(20, 10), train=(-10, 0)))]})


def volley():
    """Stinger Volley: she draws up, the train swinging forward under her to aim, and it pumps twice, a stinger leaving
    the tip each time; the daggers lift out of the way."""
    aim = _pose(core=(-14, 0, 0), at=(0, 3, 2), chest=(-6, 0, 0), head=(10, 0, 0), wings=(10, -2), arms=(8, 30, 16, -16), low=(10, 0), train=(102, 0, 4))
    _gown(aim, 20)
    pump = _pose(core=(-18, 0, 0), at=(0, 3, 3), chest=(-8, 0, 0), head=(12, 0, 0), wings=(14, -4), arms=(10, 32, 18, -18), low=(12, 0), train=(110, 0, 5))
    _gown(pump, 28)
    ease = _pose(core=(-12, 0, 0), at=(0, 2, 1), chest=(-5, 0, 0), head=(10, 0, 0), wings=(8, -1), arms=(8, 28, 14, -14), low=(10, 0), train=(98, 0, 4))
    _gown(ease, 18)
    return Move("volley", "LIGHT", 12, 10, 14, title="Stinger Volley", track=9.0, ability="Stinger Volley", origin=VOLLEY_FROM, rng=(4, 18),
                kinds=("ranged",), damage=0, knock=0, plant=(), through=ease, lag=TRAIN_ON_TIME,
                stages={"windup": [(0.3, _pose(core=(-6, 0, 0), at=(0, 1, 1), wings=(4, 0), arms=(4, 12, 6, -6), low=(4, 0), train=(16, 0, -14))),
                                   (0.6, _pose(core=(-11, 0, 0), at=(0, 2, 2), chest=(-4, 0, 0), head=(6, 0, 0), wings=(8, -2), arms=(6, 24, 12, -12),
                                               low=(8, 0), train=(54, 0, -26))), (1.0, aim)],
                        "active": [(0.3, pump), (0.55, ease), (0.8, pump), (1.0, ease)],
                        "recover": [(0.45, _pose(core=(-6, 0, 0), arms=(2, 10, 4, -4), train=(54, 0, -26))), (0.68, _pose(core=(-2, 0, 0), train=(20, 0, -12)))]})


def jelly():
    """Royal Jelly: she draws in, the wings folding down her back, the upper arms crossing with the daggers lowered and
    the lower hands lifting the glowing jelly to her mouth; she feeds, shuddering, and the glow runs through her."""
    draw = _pose(core=(10, 0, 0), at=(0, -2, 0), chest=(10, 0, 0), head=(18, 0, 0), wings=(-18, 14), arms=(-30, -20, 30, -50), low=(-10, 60), train=(20, 0))

    def feed(sg):
        p = _pose(core=(12, 2 * sg, 2 * sg), at=(0.3 * sg, -2, 0), chest=(12, 0, 0), head=(24, 3 * sg, 0), wings=(-22, 16), arms=(-34, -24, 34, -54),
                  low=(-14, 80), train=(24, 4 * sg))
        return p
    glow = _pose(core=(-10, 0, 0), at=(0, 3, 0), chest=(-10, 0, 0), head=(-20, 0, 10), wings=(20, -8), arms=(10, 40, -10, -10), low=(30, 10), train=(-24, 0))
    return Move("jelly", "NONE", 14, 24, 16, title="Royal Jelly", track=0.0, ability="Royal Jelly", rng=(0, 99), kinds=("heal",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, core._scaled(draw, 0.6)), (1.0, draw)],
                        "active": [(0.2, feed(1)), (0.4, feed(-1)), (0.6, feed(1)), (0.8, feed(-1)), (1.0, glow)],
                        "recover": [(0.5, _pose(core=(-3, 0, 0), wings=(6, -2), arms=(4, 10, 0, 0)))]})


def frenzy():
    """Frenzy: she throws her head back and shrieks, every arm and wing flung out and the train thrashing side to side,
    then drops into a crouch in the air, daggers out, ready."""
    gather = _pose(core=(12, 0, 0), at=(0, -3, 0), chest=(12, 0, 0), head=(20, 0, 0), wings=(-16, 10), arms=(-10, -10, 30, -30), low=(-14, 0), train=(16, 0))

    def thrash(sg, k):
        return _pose(core=(-16, 5 * sg, 4 * sg), at=(0.6 * sg, 4, 0), chest=(-14, 3 * sg, 0), head=(-38, 6 * sg, 18), wings=(30 + 2 * k, -10),
                     arms=(-30, 70, -30, 30), low=(60, 20), train=(-30, 24 * sg))
    ready = _pose(core=(14, 0, 0), at=(0, -2, -2), chest=(8, 0, 0), head=(-8, 0, 0), wings=(10, 10), arms=(-40, 30, 20, 30), low=(10, 0), train=(-10, 0))
    return Move("frenzy", "NONE", 14, 20, 14, title="Frenzy", track=0.0, ability="Frenzy", rng=(0, 99), kinds=("buff",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, core._scaled(gather, 0.6)), (1.0, gather)],
                        "active": [(0.2, thrash(1, 0)), (0.4, thrash(-1, 1)), (0.6, thrash(1, 2)), (0.8, thrash(-1, 3)), (1.0, ready)],
                        "recover": [(0.5, _pose(core=(4, 0, 0), arms=(-10, 10, 6, 10)))]})


def emerge():
    """Her intro: she rises from the comb curled up, the wings folded flat down her back and the daggers held crossed to
    her breast; then the wings open with a hum, she lifts her head, opens every arm wide and settles to face you."""
    curled = _pose(core=(16, 0, 0), at=(0, -10, 0), chest=(16, 0, 0), head=(26, 0, 0), wings=(-24, 24), arms=(-30, -26, 40, -60), low=(-10, 50), train=(-10, 0, -16))
    opn = _pose(core=(-8, 0, 0), at=(0, 3, 0), chest=(-8, 0, 0), head=(-18, 0, 10), wings=(24, -8), arms=(10, 24, 10, 0), low=(10, 0), train=(-16, 0))
    # she shows herself: every arm opens, the daggers turned out, her head high; then she settles to face you
    show = _pose(core=(-12, 0, 0), at=(0, 5, 0), chest=(-12, 0, 0), head=(-24, 0, 12), wings=(30, -12), arms=(-20, 64, -10, 24), low=(34, 24), train=(-24, 0))
    poise = _pose(core=(-3, 0, 0), at=(0, 2, 0), chest=(-4, 0, 0), head=(-6, 0, 4), wings=(14, -4), arms=(4, 20, 6, 6), low=(12, 6), train=(-8, 0))
    return Move("emerge", "NONE", 14, 24, 14, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=TRAIN_ON_TIME,
                stages={"windup": [(1.0, curled)], "active": [(0.35, opn), (0.7, show), (1.0, poise)], "recover": [(0.5, _pose(core=(2, 0, 0), wings=(4, 0)))]})



def lash():
    """For whoever is behind her (LIGHT): she looks back over her shoulder and draws the train out to one side, lifting
    it, then whips it across behind her at knee height."""
    pre = _pose(core=(2, -6, 0), head=(-2, 22, 0), wings=(-3, 3), arms=(3, 6, 3, -3), train=(-8, 10))
    wind = _pose(core=(6, -18, 4), at=(0, 1, 0), chest=(0, -12, 0), head=(-8, 56, 6), wings=(-12, 10), arms=(12, 20, 10, -10), low=(10, 10), train=(-26, 30))
    hit = _pose(core=(2, 14, -3), at=(0, 1, 0), chest=(0, 8, 0), head=(-4, 40, 0), wings=(4, -2), arms=(-4, 14, 0, 0), train=(-14, -22))
    after = _pose(core=(2, 18, -4), chest=(0, 10, 0), head=(-4, 34, 0), wings=(4, -2), arms=(-6, 16, 0, 0), train=(-12, -27))
    back = _pose(core=(0, 6, 0), head=(0, 14, 0), train=(-4, -9))
    return Move("lash", "LIGHT", 10, 4, 14, track=0.0, rng=(0, 3.0), kinds=("behind",), damage=0.9, knock=1.0, plant=(), lag=TRAIN_ON_TIME,
                hit=[("stinger", (0, 7, 0), 2.5), ("abd4", (0, 3, 0), 3)],
                stages={"windup": [(0.3, pre), (1.0, wind)], "active": [(0.5, hit), (1.0, after)], "recover": [(0.5, back)]})


# Her sounds (SPEC §35.8): the hum of a hive, bees and wings; she flies, so no steps. Chosen by ear on paper, not yet heard.
SOUNDS = {
    "tell_light": [("entity.bee.loop_aggressive", 1.2, 0.7)],
    "tell_heavy": [("entity.bee.loop_aggressive", 1.8, 0.55), ("entity.phantom.flap", 1.4, 0.6)],
    "tell_perilous": [("block.beehive.work", 2.0, 0.6), ("entity.bee.loop_aggressive", 2.0, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.2, 1.1), ("entity.bee.sting", 1.0, 0.8)],
    "swing_heavy": [("entity.ender_dragon.flap", 1.8, 0.9), ("entity.player.attack.sweep", 1.4, 0.7)],
    "cast": [("block.beehive.drip", 1.6, 0.6), ("block.honey_block.slide", 1.8, 0.7)],
    "impact": [("entity.bee.sting", 2.0, 0.6), ("item.mace.smash_ground", 1.6, 0.8)],
    "roar": [("entity.bee.loop_aggressive", 2.5, 0.45), ("entity.ravager.roar", 1.4, 1.6)],
}
MOVE_SOUNDS = {"brood": {"swing": "cast"}, "rain": {"impact": ""}, "jelly": {"swing": "cast", "impact": ""}, "frenzy": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "emerge"},
    "phases": [
        {"pool": [
            {"move": "cut", "weight": 3, "when": "near", "then": [["cut_b"]]},
            {"move": "sting", "weight": 2, "when": "near"},
            {"move": "dive", "weight": 3, "then": [["cut_b"]]},
            {"move": "brood", "weight": 1},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        # the comb weeps with the second phase: Honey Rain and the stinger volleys, as in GateBestiary
        {"transition": "rain", "pool": [
            {"move": "cut", "weight": 3, "when": "near", "then": [["cut_b"], ["cut_b", "sting"]]},
            {"move": "sting", "weight": 2, "when": "near"},
            {"move": "volley", "weight": 3, "then": [["volley"]]},
            {"move": "dive", "weight": 2, "then": [["cut_b"]]},
            {"move": "rain", "weight": 1},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        {"transition": "jelly", "pool": [
            {"move": "frenzy", "weight": 1},
            {"move": "cut", "weight": 3, "when": "near", "then": [["cut_b", "sting"]]},
            {"move": "dive", "weight": 3, "then": [["cut_b"], ["cut_b", "cut"]]},
            {"move": "volley", "weight": 2, "then": [["volley"]]},
            {"move": "brood", "weight": 1},
            {"move": "sting", "weight": 2, "when": "near"},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def paint(r, P_):
    """Five materials in a value order: the amber honey glow brightest (eyes, daggers, spikes, feeler tips, the jewel, the
    comb's cells, the stinger), then the pale wax (the fur ruff and the gown: the lightest surface, so the queen reads
    first), the gold bands, the see-through glass wings, and the black chitin with a violet sheen darkest."""
    from kit import parts
    chitin = Ramp("#07060a", "#0e0c13", "#16131d", "#201b29", "#2b2437", "#383047", "#473d58")
    gold = Ramp("#3e2604", "#5e3e0a", "#825a12", "#a8781c", "#c89428", "#e2b03c", "#f4cc62")
    wax = Ramp("#5a482a", "#76623c", "#94804f", "#b29e68", "#ccba86", "#e2d4a6", "#f2e8c6")
    amber = ("#b65a04", "#e08a10", "#ffb02a", "#ffd062", "#fff0b4")
    of = lambda *ps: parts.cubes_of(r, *ps)
    sheen = lambda f, i, j: (0.8 if (i + j) % 7 == 0 and f.name in SIDES else 0)
    # the chitin: black with a violet sheen catching the edges
    for c in ["waist", "thorax", "head", "neck", "face"] + of("pauldron_", "arm_", "forearm_", "hand_", "grip_", "mandible_", "feeler", "pommel_"):
        if c.startswith("feeler_tip"):
            continue
        P_.paint(c, chitin, 3.6 if c in ("thorax", "head") else 3.2, sheen, grad=0.9, rim=0.9, ao=0.8, noise=0.2)
    P_.paint("face", chitin, 2.4)
    # the gold: girdle, diadem, bracers, the dagger guards, the bodice worked in chevrons
    for c in ["girdle", "diadem"] + of("bracer_", "guard_"):
        P_.paint(c, gold, 3.8, lambda f, i, j: (0.7 if (i + j) % 3 == 0 else 0) if f.name in SIDES else 0, rim=0.8)
    P_.paint("bodice", gold, 3.6, lambda f, i, j: (1.0 if (abs(i - f.w // 2) + j) % 4 == 0 else -0.4) if f.name == "north" else 0)
    # the abdomen: bands of chitin and gold, each furred, darkening toward the stinger
    for k in range(len(ABD)):
        c = f"abd{k}"
        P_.paint(c, gold if k % 2 else chitin, (3.8 if k % 2 else 3.2) - 0.15 * k,
                 p_strokes(cell=2, length=2, dark=0.6, light=0.3, density=0.6, seed=k), grad=1.0, noise=0.25)
    # the ruff: pale fur, the lightest surface
    for c in ("ruff", "ruff2"):
        P_.paint(c, wax, 5.0, p_strokes(cell=2, length=2, dark=0.8, light=0.5, density=0.8, seed=c), grad=0.8, rim=1.0, noise=0.2)
    # the gown: wax comb, a hex lattice with honey glowing in some cells, a gold hem
    for k in range(len(GOWN)):
        c = f"tier{k}"
        P_.paint(c, wax, 4.6 - 0.25 * k, lambda f, i, j: (-1.6 if j % 4 == 0 or (i + (j // 4) % 2 * 2) % 4 == 0 else 0.2) if f.name in SIDES else 0,
                 grad=1.2, noise=0.15)
        for fname in SIDES:
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                if j % 4 and (i + (j // 4) % 2 * 2) % 4 and R._h(c + fname, i // 4 * 31 + j // 4) % 4 == 0:
                    g.set(i, j, hexc(amber[2] if j % 4 == 2 else amber[1], 200))
    P_.paint("hem", gold, 3.6, rim=0.8)
    # the wings: see-through glass with dark veins, amber near the root
    for c in of("wing_", "wing2_"):
        P_.paint(c, Ramp("#6e7c86", "#8e9ca6", "#b0bec6", "#d0dce2"), 2.6)
        k = int(c[-1])
        steps = WING_STEPS[c.split("_")[0]]
        last = len(steps) - 1
        for fname in ("north", "south"):
            f = P_.face(c, fname)
            for i, j in f.each():
                # the outline where this step stands out past the next one, the main vein up the leading edge,
                # a cross vein every few rows and two veins fanning out from the root
                wider = k == last or steps[k + 1][0] < f.w
                lead = i == (0 if fname == "north" else f.w - 1) if c[5 if c.startswith("wing2") else 4] == "l" else i == (f.w - 1 if fname == "north" else 0)
                edge = i in (0, f.w - 1) and (wider or k == 0)
                tip = k == last and j == 0
                fan = (k <= 2 and i == round((f.w - 1) * (0.35 + 0.1 * k))) or (k >= 1 and i == round((f.w - 1) * 0.7) and j % 4 != 3)
                if lead or tip or edge:
                    f.set(i, j, hexc("#1c1824", 240))
                elif fan:
                    f.set(i, j, hexc("#3a3242", 170))
                else:
                    tint = "#e0a84e" if k == 0 else "#d8c08a" if k == 1 else "#cfe2ec" if (i + j) % 9 else "#f0f8ff"
                    f.set(i, j, hexc(tint, 120 if k <= 1 else 70))
        for fname in ("east", "west", "top", "bottom"):
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, hexc("#1c1824", 235))
    # the amber: the daggers' blades, the spikes, the feeler tips, the jewel, the stinger: lit from inside
    for c in of("blade_", "spike", "feeler_tip_") + ["jewel", "girdle_gem", "stinger", "stinger_tip"]:
        P_.paint(c, Ramp("#4a2004", "#7a3a08", "#a85a0c"), 2.0)
        for fname in ("top", "bottom") + SIDES:
            radial(P_, c, fname, [amber[4], amber[3], amber[2], amber[1]], alpha=235)
    for c in of("blade_"):
        for fname in ("east", "west"):
            edge_glow(P_, c, fname, amber[4], rows=1, alpha=250, side="left")
    # the compound eyes: honey-gold, faceted
    for nm in "lr":
        c = "eye_" + nm
        P_.paint(c, Ramp("#3a1a02", "#5a2c06"), 1.0)
        for fname in ("top", "bottom") + SIDES:
            g = P_.face(c, fname, glow=True)
            f = P_.face(c, fname)
            for i, j in g.each():
                rim = i in (0, g.w - 1) or j in (0, g.h - 1)
                if rim and g.w > 2:
                    f.set(i, j, hexc("#2a1402"))
                    g.set(i, j, hexc(amber[0], 200))
                else:
                    g.set(i, j, hexc(amber[4] if (i * 3 + j * 2) % 7 == 0 else amber[3] if j < g.h // 2 else amber[2], 245))
    fm = P_.face("face", "north")
    for i in range(fm.w):
        fm.set(i, fm.h - 1, chitin.at(0))
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.6, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
