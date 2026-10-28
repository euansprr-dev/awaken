"""Body plans (SPEC §34.2): skeletons with standard bone names, so the life layer, foot planting, hit volumes and the move
library bind by name. A plan builds the bones and their cubes from a few proportions; a boss's spec adds its parts
(horns, a core, plates, a halo) on the plan's slots and paints them.

Standard names
  Brute and Duelist: pelvis > spine > chest > neck > head (> jaw); chest > shoulder_l|r > arm_l|r > forearm_l|r >
    hand_l|r (> weapon); pelvis > thigh_l|r > shin_l|r > foot_l|r.
  Flier (a hovering body): core > cap / body, head, wing or veil chains, tendrils; it never stands, so it has no feet.

Sizes are model pixels; the ground is at y = 24 and the body faces -z. "Chunky blocks": few, big cubes with a strong
silhouette, then one oversized feature per boss (forearms, a halo, a cap) and one light story (a core, eyes, cracks).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
import fauna_rig as R  # noqa: E402


class Biped:
    """Proportions of a two-legged body (Brute or Duelist), in model pixels."""

    def __init__(self, leg=36, thigh=None, foot=(10, 5, 14), pelvis=(22, 10, 14), spine=(20, 12, 13), chest=(34, 22, 20),
                 neck=(8, 5, 8), head=(14, 14, 14), arm=(9, 20, 9), forearm=(11, 20, 11), hand=(9, 8, 9), stance=7.0,
                 shoulder=19.0, hunch=0.0, leg_w=(10, 9)):
        self.leg = leg
        self.thigh = thigh or leg // 2
        self.foot, self.pelvis, self.spine, self.chest = foot, pelvis, spine, chest
        self.neck, self.head, self.arm, self.forearm, self.hand = neck, head, arm, forearm, hand
        self.stance, self.shoulder, self.hunch, self.leg_w = stance, shoulder, hunch, leg_w


def biped(rid, P, tex=(512, 512)):
    """A Brute or Duelist skeleton with its base cubes. Returns (rig, slots): slots name where parts go."""
    r = R.Rig(rid, tex)
    hip_y = 24 - P.leg
    pel = r.bone("pelvis", (0, hip_y, 0))
    pw, ph, pd = P.pelvis
    pel.cube("pelvis", -pw / 2, -ph + 3, -pd / 2, pw, ph, pd)
    sp = pel.bone("spine", (0, -ph + 3, 0), (P.hunch * 0.4, 0, 0))
    sw, sh, sd = P.spine
    sp.cube("spine", -sw / 2, -sh, -sd / 2, sw, sh, sd)
    ch = sp.bone("chest", (0, -sh, 0), (P.hunch * 0.6, 0, 0))
    cw, chh, cd = P.chest
    ch.cube("chest", -cw / 2, -chh, -cd / 2 - 1, cw, chh, cd)
    nk = ch.bone("neck", (0, -chh, -2), (-P.hunch * 0.5, 0, 0))
    nw, nh, nd = P.neck
    nk.cube("neck", -nw / 2, -nh, -nd / 2, nw, nh, nd)
    hd = nk.bone("head", (0, -nh, -1), (-P.hunch * 0.5, 0, 0))
    hw, hh, hdp = P.head
    hd.cube("head", -hw / 2, -hh, -hdp / 2 - 2, hw, hh, hdp)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        shp = ch.bone("shoulder_" + nm, (side * (cw / 2 - 2), -chh + 5, 0))
        aw, ah, ad = P.arm
        arm = shp.bone("arm_" + nm, (side * (aw / 2 + 1), 0, 0))
        arm.cube("arm_" + nm, -aw / 2, -2, -ad / 2, aw, ah, ad, mirror=mir)
        fw, fh, fd = P.forearm
        fa = arm.bone("forearm_" + nm, (0, ah - 3, 0))
        fa.cube("forearm_" + nm, -fw / 2, -1, -fd / 2, fw, fh, fd, mirror=mir)
        hw2, hh2, hd2 = P.hand
        hand = fa.bone("hand_" + nm, (0, fh - 1, 0))
        hand.cube("hand_" + nm, -hw2 / 2, 0, -hd2 / 2, hw2, hh2, hd2, mirror=mir)
        lw, sw2 = P.leg_w
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -lw / 2, -2, -lw / 2, lw, P.thigh + 2, lw, mirror=mir)
        shin_len = P.leg - P.thigh - P.foot[1]
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -sw2 / 2, 0, -sw2 / 2, sw2, shin_len, sw2, mirror=mir)
        fx, fy, fz = P.foot
        ft = sn.bone("foot_" + nm, (0, shin_len, 0))
        ft.cube("foot_" + nm, -fx / 2, 0, -fz + 4, fx, fy, fz, mirror=mir)
    r.look = [("head", 0.55, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0  # the move library's crouch depths scale with the leg
    return r


def flier(rid, tex=(512, 512), hover=40):
    """A floating body: a core bone the rest hangs from, hovering {@code hover} pixels over the ground."""
    r = R.Rig(rid, tex)
    core = r.bone("core", (0, 24 - hover, 0))
    r.look = [("head", 0.5, 0.4)] if False else []
    return r, core


# ---------------------------------------------------------------------------------------------------------------------
# Plan-wide clips every kit body has: idle, walk/run (or hover), hurt from each side, kneel and rise, turn steps, death

def biped_locomotion(r, P, weight=1.0, temper="regal", stride=None):
    """Idle (breath through the chest, a weight shift), a distance-locked walk and run, and the turn shuffle.
    weight > 1 slows and deepens everything (a heavy body); temper 'feral' adds twitch and uneven timing."""
    from kit.core import keyed, planted
    import math
    heavy = weight
    br = 1.5 * heavy
    keyed(r, "idle", 3.2 * heavy ** 0.5, planted(r, [
        (0, {"chest": (0, 0, 0), "pelvis": {"rot": (0, 0, 0), "pos": (0, 0, 0)}}),
        (0.8 * heavy ** 0.5, {"chest": (-br, 0, 0.6), "spine": (-0.6, 0, 0), "arm_l": (0, 0, -1.5), "arm_r": (0, 0, 1.5),
                               "pelvis": {"rot": (0, 0, 1.0), "pos": (0.3, -0.3, 0)}, "head": (1.5, 3, 0)}),
        (1.6 * heavy ** 0.5, {"chest": (0, 0, 0), "pelvis": {"rot": (0, 0, 0), "pos": (0, 0, 0)}, "head": (0, 0, 0)}),
        (2.4 * heavy ** 0.5, {"chest": (-br, 0, -0.6), "spine": (-0.6, 0, 0), "arm_l": (0, 0, -1.5), "arm_r": (0, 0, 1.5),
                               "pelvis": {"rot": (0, 0, -1.0), "pos": (-0.3, -0.3, 0)}, "head": (1.5, -4, 0)}),
        (3.2 * heavy ** 0.5, {"chest": (0, 0, 0), "pelvis": {"rot": (0, 0, 0), "pos": (0, 0, 0)}, "head": (0, 0, 0)}),
    ]), loop=True)
    # the walk: thighs swing, knees bend on the forward swing, the pelvis rolls over the planted foot; locked to distance
    stride = stride or P.leg / 16 * 2.0
    r.walk_stride = stride
    r.run_stride = stride * 1.9
    sw = 24 / heavy ** 0.4
    keys = []
    n = 8
    length = 1.25 * heavy ** 0.5
    for k in range(n + 1):
        p = k / n
        s = math.sin(p * 2 * math.pi)
        c = math.cos(p * 2 * math.pi)
        keys.append((length * p, {
            "thigh_l": (-sw * s, 0, 0), "thigh_r": (sw * s, 0, 0),
            "shin_l": (max(0, c) * sw * 1.4 + 4, 0, 0), "shin_r": (max(0, -c) * sw * 1.4 + 4, 0, 0),
            "foot_l": (sw * s * 0.4, 0, 0), "foot_r": (-sw * s * 0.4, 0, 0),
            "arm_l": (sw * 0.6 * s, 0, -2), "arm_r": (-sw * 0.6 * s, 0, 2),
            "forearm_l": (-8 - 6 * max(0, s), 0, 0), "forearm_r": (-8 - 6 * max(0, -s), 0, 0),
            "pelvis": {"rot": (0, 6 * s, 3 * s), "pos": (0, 0.8 * abs(c) * heavy, 0)},
            "chest": (2 + 1.5 * abs(c), -5 * s, -2 * s), "head": (-1.5 * abs(c), 4 * s, 0),
        }))
    keyed(r, "walk", length, keys, loop=True)
    keys = []
    length = 0.7 * heavy ** 0.5
    sw2 = sw * 1.7
    for k in range(n + 1):
        p = k / n
        s = math.sin(p * 2 * math.pi)
        c = math.cos(p * 2 * math.pi)
        keys.append((length * p, {
            "thigh_l": (-sw2 * s - 8, 0, 0), "thigh_r": (sw2 * s - 8, 0, 0),
            "shin_l": (max(0, c) * sw2 * 1.5 + 10, 0, 0), "shin_r": (max(0, -c) * sw2 * 1.5 + 10, 0, 0),
            "arm_l": (sw2 * 0.7 * s - 10, 0, -4), "arm_r": (-sw2 * 0.7 * s - 10, 0, 4),
            "forearm_l": (-40, 0, 0), "forearm_r": (-40, 0, 0),
            "pelvis": {"rot": (8, 8 * s, 2 * s), "pos": (0, -1.5 + 1.6 * abs(c), 0)},
            "chest": (10, -8 * s, 0), "head": (-12, 5 * s, 0),
        }))
    keyed(r, "run", length, keys, loop=True)
    # the turn shuffle: a short step in place, the feet stepping round (looped while the body turns)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keyed(r, nm, 0.4, [
            (0, {}),
            (0.1, {"thigh_l": (-14, 0, 0), "shin_l": (20, 0, 0), "pelvis": {"rot": (0, 6 * sgn, 0), "pos": (0, -0.6, 0)}, "chest": (0, 4 * sgn, 0)}),
            (0.2, {"pelvis": {"rot": (0, 3 * sgn, 0), "pos": (0, 0, 0)}}),
            (0.3, {"thigh_r": (-14, 0, 0), "shin_r": (20, 0, 0), "pelvis": {"rot": (0, 6 * sgn, 0), "pos": (0, -0.6, 0)}, "chest": (0, 4 * sgn, 0)}),
            (0.4, {}),
        ], loop=True)
    r.run_speed = (0.14, 0.3)


def biped_reactions(r, weight=1.0):
    """The flinches by side, the kneel when its stance breaks and the rise, the death fall."""
    from kit.core import keyed, planted
    w = weight ** 0.5
    keyed(r, "hurt", 0.45 * w, planted(r, [(0, {}), (0.06, {"chest": (-12, 0, 0), "head": (-14, 0, 0), "spine": (-5, 0, 0), "pelvis": {"rot": (0, 0, 0), "pos": (0, 0, 1)}}),
                                  (0.25 * w, {"chest": (3, 0, 0), "head": (4, 0, 0)}), (0.45 * w, {})]))
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):
        # struck from its left it twists to its right (and bends away)
        keyed(r, nm, 0.5 * w, planted(r, [(0, {}), (0.07, {"chest": (-6, -18 * sgn, -10 * sgn), "head": (-6, -20 * sgn, -6 * sgn), "spine": (0, -6 * sgn, -4 * sgn),
                                               "arm_l" if sgn > 0 else "arm_r": (-20, 0, -30 * sgn), "pelvis": {"rot": (0, -6 * sgn, 0), "pos": (-sgn * 0.8, 0, 0)}}),
                                     (0.28 * w, {"chest": (2, 4 * sgn, 2 * sgn), "head": (2, 5 * sgn, 0)}), (0.5 * w, {})]))
    # the kneel: one knee down, a fist on the ground, the head hanging (held while the stance is broken)
    kneel = {"pelvis": {"rot": (10, 0, 0), "pos": (0, -14, 2)}, "thigh_l": (-80, 0, 0), "shin_l": (82, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (8, 0, 0), "shin_r": (90, 0, 0), "foot_r": (60, 0, 0), "spine": (14, 0, 0), "chest": (16, 0, 4),
             "head": (24, 0, 0), "arm_r": (-30, 0, 10), "forearm_r": (-20, 0, 0), "arm_l": (-50, 0, -6), "forearm_l": (-40, 0, 0)}
    keyed(r, "kneel", 0.5 * w, [(0, {}), (0.18 * w, {"pelvis": {"rot": (14, 0, 0), "pos": (0, -10, 1)}, "chest": (24, 0, 0), "head": (30, 0, 0)}),
                                  (0.5 * w, kneel)])
    keyed(r, "rise", 0.7 * w, [(0, kneel), (0.35 * w, {"pelvis": {"rot": (6, 0, 0), "pos": (0, -5, 0)}, "thigh_l": (-40, 0, 0), "shin_l": (44, 0, 0),
                                                       "chest": (8, 0, 0), "head": (-8, 0, 0)}), (0.7 * w, {})])
    keyed(r, "death", 2.4 * w, [
        (0, {}),
        (0.3 * w, {"chest": (-16, 0, 0), "head": (-30, 0, 0), "arm_l": (-30, 0, -30), "arm_r": (-30, 0, 30)}),
        (0.9 * w, {"pelvis": {"rot": (20, 0, 0), "pos": (0, -14, 2)}, "thigh_l": (-80, 0, 0), "shin_l": (82, 0, 0),
                   "thigh_r": (-80, 0, 0), "shin_r": (82, 0, 0), "chest": (20, 0, 0), "head": (24, 0, 0), "arm_l": (-10, 0, -10), "arm_r": (-10, 0, 10)}),
        (1.6 * w, {"pelvis": {"rot": (70, 0, 6), "pos": (0, -20, -10)}, "thigh_l": (-90, 0, 0), "shin_l": (90, 0, 0),
                   "thigh_r": (-84, 0, 0), "shin_r": (92, 0, 0), "chest": (14, 0, 0), "head": (10, 20, 0), "arm_l": (-160, 0, -10), "arm_r": (-150, 0, 20)}),
        (2.4 * w, {"pelvis": {"rot": (84, 0, 8), "pos": (0, -22, -14)}, "thigh_l": (-90, 0, 0), "shin_l": (90, 0, 0),
                   "thigh_r": (-84, 0, 0), "shin_r": (92, 0, 0), "chest": (4, 0, 0), "head": (6, 30, 0), "arm_l": (-170, 0, -14), "arm_r": (-160, 0, 26)}),
    ])
