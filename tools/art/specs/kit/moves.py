"""The move library (SPEC §34.2): one generator per move for the biped plans, written once with parameters.

Every generator returns a core.Move: its key poses per stage (anticipation into the wind-up's peak, the strike through
contact and follow-through, the settle back to rest), its timing in ticks and class, tracking, root motion, the striking
bones (for the capsules) and the kinds a fight script needs. Style keeps two bosses on one plan from moving alike:
  weight   > 1: a slower, deeper gather, a bigger drop on contact, an overshoot when it settles
  side     'r' or 'l': the leading arm (asymmetry: a dominant arm)
  reach    how far the strike travels (root motion, in blocks)
The principles kept everywhere: the body moves first and the limb follows (lag), every wind-up dips or coils before it
rises (anticipation), contact lands with the body low, and the recovery overshoots once before it settles.
"""
from kit.core import Move


def _sgn(side):
    return 1 if side == "l" else -1


def _other(side):
    return "r" if side == "l" else "l"


def hand_tip(P):
    return (0, P.hand[1] + 1, 0)


def overhead_slam(P, mid="slam", title="", weight=1.0, cls="HEAVY", windup=22, hold=10, active=5, recover=20, ability=None,
                  kinds=("area",), rng=(0, 3.2), damage=1.4, knock=0.9, signature=False, rear=1.0):
    """Both fists (or a weapon in both hands) raised high over the head and brought down on the ground in front."""
    w = weight
    crouch = {"pelvis": {"rot": (6, 0, 0), "pos": (0, -3 * w, 1)}, "thigh_l": (-14, 0, 0), "thigh_r": (-14, 0, 0),
              "shin_l": (22, 0, 0), "shin_r": (22, 0, 0), "foot_l": (-8, 0, 0), "foot_r": (-8, 0, 0), "chest": (10, 0, 0),
              "arm_l": (-30, 0, -10), "arm_r": (-30, 0, 10), "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0)}
    peak = {"pelvis": {"rot": (-6 * rear, 0, 0), "pos": (0, 1.5, 2 * rear)}, "spine": (-10 * rear, 0, 0), "chest": (-18 * rear, 0, 0),
            "head": (-10, 0, 0), "arm_l": (-172, 0, -8), "arm_r": (-172, 0, 8), "forearm_l": (-38, 0, 0), "forearm_r": (-38, 0, 0),
            "hand_l": (-20, 0, 0), "hand_r": (-20, 0, 0), "thigh_l": (-4, 0, 0), "thigh_r": (-4, 0, 0), "shin_l": (6, 0, 0), "shin_r": (6, 0, 0)}
    # contact: the fists meet on the ground well in front (arms forward and yawed in), not at its own feet
    contact = {"pelvis": {"rot": (16, 0, 0), "pos": (0, -7 * w, -1)}, "spine": (14, 0, 0), "chest": (28, 0, 0), "head": (10, 0, 0),
               "arm_l": (-84, 18, -4), "arm_r": (-84, -18, 4), "forearm_l": (-6, 0, 0), "forearm_r": (-6, 0, 0),
               "thigh_l": (-34, 0, 0), "thigh_r": (-34, 0, 0), "shin_l": (48, 0, 0), "shin_r": (48, 0, 0), "foot_l": (-14, 0, 0), "foot_r": (-14, 0, 0)}
    settle = dict(contact)
    settle["chest"] = (22, 0, 0)
    settle["pelvis"] = {"rot": (12, 0, 0), "pos": (0, -5 * w, -1)}
    over = {"pelvis": {"rot": (-3, 0, 0), "pos": (0, 0.6, 0)}, "chest": (-4, 0, 0), "head": (-3, 0, 0)}
    return Move(mid, cls, windup, active, recover, title=title, hold=hold if cls == "HEAVY" else 0, track=5.0 / w, ability=ability,
                origin=(0, 0, 2.2), rng=rng, kinds=kinds, damage=damage, knock=knock, signature=signature,
                hit=[("hand_l", hand_tip(P), P.hand[0] / 2 + 1), ("hand_r", hand_tip(P), P.hand[0] / 2 + 1)],
                stages={"windup": [(0.3, crouch), (1.0, peak)],
                        "active": [(0.45, contact), (1.0, settle)],
                        "recover": [(0.4, settle), (0.8, over)]},
                lag={"arm_l": 0.03 * w, "arm_r": 0.03 * w, "forearm_l": 0.06 * w, "forearm_r": 0.06 * w, "head": 0.05})


def backhand(P, mid="backhand", side="r", title="", weight=1.0, windup=10, active=4, recover=12, kinds=("near",), rng=(0, 2.6), damage=0.8,
             low=0.0, arm=(-80, -20, -70)):
    """A fast backhand: the arm folds across the chest, the body coils, then it whips out sideways (a LIGHT).
    low: degrees it bends into the swing (a giant has to stoop to meet someone its knee's height); arm: the arm at the end."""
    sg = _sgn(side)
    a, f = "arm_" + side, "forearm_" + side
    # +yRot turns the body to its right: a right backhand coils left (the arm across the chest), then whips right
    coil = {"chest": (4, 30 * sg, 0), "spine": (0, 12 * sg, 0), "pelvis": {"rot": (0, 8 * sg, 0), "pos": (0, -1.5, 0)},
            a: (-70, 50 * sg, 30 * sg), f: (-70, 0, 0), "head": (0, -14 * sg, 0), "thigh_" + side: (-8, 0, 0), "shin_" + side: (12, 0, 0)}
    whip = {"chest": (2 + low, -36 * sg, 0), "spine": (low * 0.5, -14 * sg, 0), "pelvis": {"rot": (low * 0.4, -10 * sg, 0), "pos": (0, -1 - low * 0.15, 0)},
            a: (arm[0], arm[1] * sg, arm[2] * sg), f: (-10, 0, 0), "head": (0, 10 * sg, 0)}
    return Move(mid, "LIGHT", windup, active, recover, title=title, track=12.0, rng=rng, kinds=kinds, damage=damage, knock=0.6,
                hit=[("hand_" + side, hand_tip(P), P.hand[0] / 2 + 1), (f, (0, P.forearm[1], 0), P.forearm[0] / 2)],
                stages={"windup": [(1.0, coil)], "active": [(0.5, whip), (1.0, whip)], "recover": [(0.5, {"chest": (0, -8 * sg, 0), a: (-30, 0, -10 * sg)})]},
                lag={a: 0.02, f: 0.04})


def chop(P, mid="chop", side="r", title="", weight=1.0, cls="LIGHT", windup=12, hold=0, active=4, recover=12, weapon=None,
         kinds=("combo",), rng=(0, 3.4), damage=1.0, advance=0.6, diagonal=1.0, weapon_r=6.0, weapon_bone=None):
    """A one-arm chop with a weapon (or the edge of the hand): raised high behind the head, brought down across the body."""
    sg = _sgn(side)
    a, f, h = "arm_" + side, "forearm_" + side, "hand_" + side
    raise_ = {a: (-160, 10 * sg * diagonal, 30 * sg * diagonal), f: (-50, 0, 0), h: (-30, 0, 0), "chest": (-8, -24 * sg * diagonal, 0),
              "spine": (-4, -10 * sg * diagonal, 0), "pelvis": {"rot": (0, -8 * sg, 0), "pos": (0, -1, 1)}, "head": (-6, 10 * sg, 0),
              "arm_" + _other(side): (-30, 0, -14 * sg)}
    cut = {a: (-40, -30 * sg * diagonal, -20 * sg * diagonal), f: (-12, 0, 0), h: (10, 0, 0), "chest": (20, 26 * sg * diagonal, 0),
           "spine": (8, 10 * sg * diagonal, 0), "pelvis": {"rot": (8, 10 * sg, 0), "pos": (0, -4 * weight, -1)},
           "thigh_" + side: (-24, 0, 0), "shin_" + side: (30, 0, 0), "head": (8, -10 * sg, 0)}
    tip = weapon if weapon is not None else hand_tip(P)
    return Move(mid, cls, windup, active, recover, title=title, hold=hold, track=8.0, rng=rng, kinds=kinds, damage=damage, knock=0.5,
                advance=advance, hit=[(weapon_bone or h, tip, weapon_r if weapon else P.hand[0] / 2 + 1)],
                stages={"windup": [(0.25, {"pelvis": {"rot": (0, 0, 0), "pos": (0, -1.5, 0)}, a: (-40, 0, 10 * sg)}), (1.0, raise_)],
                        "active": [(0.5, cut), (1.0, cut)], "recover": [(0.5, {"chest": (6, 6 * sg, 0), a: (-30, 0, 0)})]},
                lag={a: 0.02, f: 0.05, h: 0.07, "head": 0.04})


def shoulder_charge(P, mid="charge", side="r", title="", weight=1.0, windup=16, active=12, recover=18, advance=6.0, cls="GRAB",
                    kinds=("gap",), rng=(4, 12), damage=1.2):
    """It drops a shoulder, digs in, and runs through you: the shoulder and the chest are the hit."""
    sg = _sgn(side)
    dig = {"pelvis": {"rot": (12, 16 * sg, 0), "pos": (0, -4 * weight, 2)}, "chest": (14, 20 * sg, 0), "spine": (6, 6 * sg, 0),
           "head": (-20, -10 * sg, 0), "arm_" + side: (-20, 0, 6 * sg), "forearm_" + side: (-60, 0, 0),
           "arm_" + _other(side): (30, 0, -20 * sg), "thigh_l": (-30, 0, 0), "shin_l": (44, 0, 0), "thigh_r": (10, 0, 0), "shin_r": (34, 0, 0)}
    run = {"pelvis": {"rot": (12, 14 * sg, 0), "pos": (0, -3, 0)}, "chest": (14, 18 * sg, 0), "head": (-16, -8 * sg, 0),
           "arm_" + side: (-10, 0, 10 * sg), "forearm_" + side: (-70, 0, 0), "thigh_l": (-40, 0, 0), "shin_l": (40, 0, 0), "thigh_r": (30, 0, 0), "shin_r": (50, 0, 0)}
    run2 = dict(run)
    run2.update({"thigh_l": (30, 0, 0), "shin_l": (50, 0, 0), "thigh_r": (-40, 0, 0), "shin_r": (40, 0, 0)})
    cw, ch, cd = P.chest
    return Move(mid, cls, windup, active, recover, title=title, track=4.0, lock=windup - 4, advance=advance, rng=rng, kinds=kinds,
                damage=damage, knock=1.2, plant=(),
                hit=[("chest", (sg * cw / 2, -ch * 0.6, -cd / 2 - 2), cw / 3), ("shoulder_" + side, (sg * 4, 0, -4), 6)],
                stages={"windup": [(0.4, {"pelvis": {"rot": (-6, 0, 0), "pos": (0, 0, 2)}, "chest": (-8, 0, 0)}), (1.0, dig)],
                        "active": [(0.25, run), (0.5, run2), (0.75, run), (1.0, run2)],
                        "recover": [(0.35, {"pelvis": {"rot": (-8, 0, 0), "pos": (0, -2, -1)}, "chest": (-12, 0, 0), "head": (6, 0, 0)}), (0.7, {"chest": (3, 0, 0)})]},
                lag={"head": 0.05})


def stomp(P, mid="stomp", side="l", title="", weight=1.0, windup=16, active=4, recover=14, ability=None, kinds=("area", "behind"),
          rng=(0, 3.0), damage=1.1, cls="HEAVY", hold=0, disc=2.4):
    """It lifts a foot high (arms out for balance) and stamps the ground: a ring round it, behind it too (disc: its radius in
    blocks from the body's middle)."""
    sg = _sgn(side)
    lift = {"thigh_" + side: (-74, 0, 6 * sg), "shin_" + side: (70, 0, 0), "foot_" + side: (6, 0, 0),
            "pelvis": {"rot": (-4, 0, -6 * sg), "pos": (-sg * 1.5, 2, 0)}, "chest": (-6, 0, 6 * sg), "arm_l": (-30, 0, -50), "arm_r": (-30, 0, 50),
            "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0), "head": (6, 0, 0), "thigh_" + _other(side): (-6, 0, 0), "shin_" + _other(side): (10, 0, 0)}
    down = {"thigh_" + side: (-20, 0, 0), "shin_" + side: (24, 0, 0), "foot_" + side: (-4, 0, 0), "pelvis": {"rot": (8, 0, 2 * sg), "pos": (0, -5 * weight, 0)},
            "chest": (14, 0, -2 * sg), "arm_l": (-20, 0, -30), "arm_r": (-20, 0, 30), "head": (12, 0, 0), "thigh_" + _other(side): (-18, 0, 0), "shin_" + _other(side): (26, 0, 0)}
    return Move(mid, cls, windup, active, recover, title=title, hold=hold, track=0.0, ability=ability, origin=(0.45 * sg, 0, 0.5), rng=rng, kinds=kinds,
                damage=damage, knock=1.0, plant=(_other(side),), disc=disc,
                hit=[("foot_" + side, (0, P.foot[1], -P.foot[2] + 4), P.foot[2] / 2 + 6)],
                stages={"windup": [(0.3, {"pelvis": {"rot": (0, 0, 4 * sg), "pos": (sg * 1.0, -1, 0)}}), (1.0, lift)],
                        "active": [(0.5, down), (1.0, down)], "recover": [(0.5, {"pelvis": {"rot": (2, 0, 0), "pos": (0, -1, 0)}})]},
                lag={"arm_l": 0.05, "arm_r": 0.05, "head": 0.06})


def roar(P, mid="roar", title="", weight=1.0, windup=10, active=26, recover=14, kinds=("intro",), cls="NONE", ability=None,
         signature=False):
    """The roar: the chest swells, the head goes back, the arms open wide (no blow; an intro or a transition)."""
    gather = {"chest": (14, 0, 0), "head": (22, 0, 0), "arm_l": (-20, 0, -10), "arm_r": (-20, 0, 10), "forearm_l": (-50, 0, 0),
              "forearm_r": (-50, 0, 0), "pelvis": {"rot": (6, 0, 0), "pos": (0, -3, 0)}}
    open_ = {"chest": (-20, 0, 0), "spine": (-8, 0, 0), "head": (-34, 0, 0), "jaw": (34, 0, 0),
             "arm_l": (-40, 0, -70), "arm_r": (-40, 0, 70), "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0),
             "pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}}
    shake = dict(open_)
    shake["head"] = (-30, 6, 0)
    stages = {"windup": [(1.0, gather)], "active": [(0.2, open_), (0.5, shake), (0.8, open_), (1.0, open_)], "recover": [(0.5, {"chest": (4, 0, 0)})]}
    return Move(mid, cls, windup, active, recover, title=title, track=0.0, rng=(0, 99), kinds=kinds, damage=0, knock=0, stages=stages,
                ability=ability, signature=signature,
                lag={"arm_l": 0.05, "arm_r": 0.05, "head": 0.08})


def lunge_cut(P, mid="lunge", side="r", title="", windup=12, active=5, recover=12, advance=3.5, cls="LIGHT", kinds=("gap",),
              rng=(2.5, 7), damage=1.0, ability=None, weapon=None):
    """A Duelist's lunge: low on the back foot, then a long step and a straight thrust of the striking hand."""
    sg = _sgn(side)
    a, f = "arm_" + side, "forearm_" + side
    low = {"pelvis": {"rot": (8, -16 * sg, 0), "pos": (0, -5, 3)}, "thigh_" + side: (-30, 0, 0), "shin_" + side: (44, 0, 0),
           "thigh_" + _other(side): (24, 0, 0), "shin_" + _other(side): (40, 0, 0), "chest": (8, -26 * sg, 0),
           a: (-30, 30 * sg, 10 * sg), f: (-90, 0, 0), "head": (-6, 22 * sg, 0), "arm_" + _other(side): (-40, 0, -20 * sg)}
    thrust = {"pelvis": {"rot": (16, 14 * sg, 0), "pos": (0, -6, -2)}, "thigh_" + side: (-62, 0, 0), "shin_" + side: (60, 0, 0),
              "thigh_" + _other(side): (34, 0, 0), "shin_" + _other(side): (14, 0, 0), "chest": (14, 22 * sg, 0),
              a: (-92, -8 * sg, 0), f: (-4, 0, 0), "head": (2, -16 * sg, 0), "arm_" + _other(side): (20, 0, -30 * sg)}
    tip = weapon if weapon is not None else hand_tip(P)
    return Move(mid, cls, windup, active, recover, title=title, track=10.0, advance=advance, rng=rng, kinds=kinds, damage=damage, knock=0.6,
                ability=ability, plant=(), hit=[("hand_" + side, tip, P.hand[0] / 2 + 1.5)],
                stages={"windup": [(1.0, low)], "active": [(0.5, thrust), (1.0, thrust)], "recover": [(0.5, {"pelvis": {"rot": (6, 0, 0), "pos": (0, -2, 0)}})]},
                lag={a: 0.02, f: 0.04})


def palm_cast(P, mid="cast", side="r", title="", windup=20, active=8, recover=14, cls="PERILOUS", ability=None, kinds=("ranged",),
              rng=(3, 24), track=9.0, lock=None, plant=("l", "r"), signature=False):
    """The back foot plants, the palm draws back to the hip and the off hand points; then the palm drives out and holds."""
    sg = _sgn(side)
    a, f, h = "arm_" + side, "forearm_" + side, "hand_" + side
    draw = {"pelvis": {"rot": (0, -20 * sg, 0), "pos": (0, -2.5, 1.5)}, "chest": (-4, -34 * sg, 0), "spine": (0, -10 * sg, 0),
            a: (30, 30 * sg, 20 * sg), f: (-100, 0, 0), h: (-40, 0, 0), "arm_" + _other(side): (-86, 10 * sg, 0),
            "forearm_" + _other(side): (-6, 0, 0), "head": (0, 30 * sg, 0), "thigh_" + _other(side): (-16, 0, 0), "shin_" + _other(side): (20, 0, 0)}
    drive = {"pelvis": {"rot": (4, 12 * sg, 0), "pos": (0, -3, -1)}, "chest": (6, 20 * sg, 0), a: (-90, -6 * sg, 0), f: (0, 0, 0), h: (-70, 0, 0),
             "arm_" + _other(side): (-20, 0, -30 * sg), "head": (0, -12 * sg, 0), "thigh_" + _other(side): (-20, 0, 0), "shin_" + _other(side): (24, 0, 0)}
    return Move(mid, cls, windup, active, recover, title=title, track=track, lock=lock, ability=ability, origin=(-0.2 * sg, 1.5, 1.0),
                rng=rng, kinds=kinds, damage=0, knock=0, plant=plant, signature=signature,
                stages={"windup": [(0.35, {"pelvis": {"rot": (0, -6 * sg, 0), "pos": (0, -1, 0)}}), (1.0, draw)],
                        "active": [(0.25, drive), (1.0, drive)], "recover": [(0.5, {"chest": (0, 8 * sg, 0), a: (-40, 0, 0)})]},
                lag={a: 0.03, f: 0.05, h: 0.07})


def sky_call(P, mid="call", title="", windup=18, active=10, recover=16, cls="PERILOUS", ability=None, kinds=("area", "ranged"), rng=(0, 30),
             signature=False):
    """Both arms rise to the sky, the head follows them up, the chest opens: marks fall where it calls."""
    # an arm already raised spreads with the opposite zRot to a hanging one (ModelPart turns Rx before Rz)
    up = {"arm_l": (-160, 0, 30), "arm_r": (-160, 0, -30), "forearm_l": (-10, 0, 0), "forearm_r": (-10, 0, 0), "chest": (-16, 0, 0),
          "spine": (-6, 0, 0), "head": (-40, 0, 0), "pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 0)}}
    gather = {"arm_l": (-60, 0, -20), "arm_r": (-60, 0, 20), "forearm_l": (-80, 0, 0), "forearm_r": (-80, 0, 0), "chest": (14, 0, 0),
              "head": (18, 0, 0), "pelvis": {"rot": (6, 0, 0), "pos": (0, -3, 0)}}
    flick = dict(up)
    flick["arm_l"] = (-168, 0, 46)
    flick["arm_r"] = (-168, 0, -46)
    return Move(mid, cls, windup, active, recover, title=title, track=0.0, ability=ability, rng=rng, kinds=kinds, damage=0, knock=0,
                signature=signature, stages={"windup": [(0.4, gather), (1.0, up)], "active": [(0.3, flick), (1.0, up)],
                                             "recover": [(0.5, {"arm_l": (-40, 0, -10), "arm_r": (-40, 0, 10)})]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "head": 0.06})


def spin_cut(P, mid="spin", side="r", title="", windup=12, active=6, recover=12, kinds=("behind",), rng=(0, 3.2), damage=0.9, cls="LIGHT"):
    """For the one behind it: it turns on its front foot and cuts round, the arm out flat (the cut sweeps a half-circle)."""
    sg = _sgn(side)
    a, f = "arm_" + side, "forearm_" + side
    coil = {"pelvis": {"rot": (4, 30 * sg, 0), "pos": (0, -3, 0)}, "chest": (6, 40 * sg, 0), a: (-80, 60 * sg, 0), f: (-40, 0, 0), "head": (0, -50 * sg, 0)}
    sweep1 = {"pelvis": {"rot": (6, -40 * sg, 0), "pos": (0, -3, 0)}, "chest": (6, -50 * sg, 0), a: (-88, -10 * sg, -10 * sg), f: (-4, 0, 0), "head": (0, -20 * sg, 0)}
    sweep2 = {"pelvis": {"rot": (6, -120 * sg, 0), "pos": (0, -3, 0)}, "chest": (6, -70 * sg, 0), a: (-88, -30 * sg, -10 * sg), f: (-4, 0, 0), "head": (0, 0, 0)}
    return Move(mid, cls, windup, active, recover, title=title, track=0.0, rng=rng, kinds=kinds, damage=damage, knock=0.8, plant=(),
                hit=[("hand_" + side, hand_tip(P), P.hand[0] / 2 + 2), (f, (0, P.forearm[1], 0), P.forearm[0] / 2 + 1)],
                stages={"windup": [(1.0, coil)], "active": [(0.5, sweep1), (1.0, sweep2)],
                        "recover": [(0.5, {"pelvis": {"rot": (2, -60 * sg, 0), "pos": (0, -1, 0)}, "chest": (0, -20 * sg, 0)})]},
                lag={a: 0.02, f: 0.03})
