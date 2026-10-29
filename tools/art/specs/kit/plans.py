"""Body plans (SPEC §34.2): skeletons with standard bone names, so the life layer, foot planting, hit volumes and the move
library bind by name. A plan builds the bones and their cubes from a few proportions; a boss's spec adds its parts
(horns, a core, plates, a halo) on the plan's slots and paints them.

Standard names
  Brute and Duelist: pelvis > spine > chest > neck > head (> jaw); chest > shoulder_l|r > arm_l|r > forearm_l|r >
    hand_l|r (> weapon); pelvis > thigh_l|r > shin_l|r > foot_l|r.
  Flier (a hovering body): core > cap / body, head, wing or veil chains, tendrils; it never stands, so it has no feet.
  Caster (an upper body on a base instead of legs: a robe on roots, a lectern, a column of water): base > robe > pelvis >
    spine > chest > neck > head; chest > shoulder_l|r > arm_l|r > forearm_l|r > hand_l|r. The base stands on the ground
    and never steps (it glides, or something of its own carries it), so it has no feet; it bends at the robe and the
    waist.

Sizes are model pixels; the ground is at y = 24 and the body faces -z. "Chunky blocks": few, big cubes with a strong
silhouette, then one oversized feature per boss (forearms, a halo, a cap) and one light story (a core, eyes, cracks).
"""
import math
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


class Caster:
    """Proportions of a Caster: the heights of its joints over the ground and its limbs' lengths, model pixels."""

    def __init__(self, knee=18, waist=40, chest=48, neck=58, shoulder=(9.5, 56), arm=13, forearm=11, head=60):
        self.knee, self.waist, self.chest, self.neck, self.head = knee, waist, chest, neck, head
        self.shoulder, self.arm, self.forearm = shoulder, arm, forearm


def caster(rid, C, tex=(512, 512), arm_rest=None):
    """A Caster's skeleton with no cubes (each Caster's base and robe are its own): returns the rig and its bones by
    name. arm_rest: {side: (rx, ry, rz)} the arms' rest turns (a Caster's hands rest where its prayer or its book has
    them)."""
    r = R.Rig(rid, tex)
    arm_rest = arm_rest or {}
    b = {}
    b["base"] = r.bone("base", (0, 24, 0))
    b["robe"] = b["base"].bone("robe", (0, -C.knee, 0))
    b["pelvis"] = b["robe"].bone("pelvis", (0, -(C.waist - C.knee), 0))
    b["spine"] = b["pelvis"].bone("spine", (0, 0, 0))
    b["chest"] = b["spine"].bone("chest", (0, -(C.chest - C.waist), 0))
    b["neck"] = b["chest"].bone("neck", (0, -(C.neck - C.chest), 0))
    b["head"] = b["neck"].bone("head", (0, -(C.head - C.neck), 0))
    sx, sy = C.shoulder
    for side, nm in ((1, "l"), (-1, "r")):
        sh = b["chest"].bone("shoulder_" + nm, (side * sx, -(sy - C.chest), 0))
        arm = sh.bone("arm_" + nm, (0, 0, 0), arm_rest.get(nm, (0, 0, 0)))
        fa = arm.bone("forearm_" + nm, (0, C.arm, 0))
        hd = fa.bone("hand_" + nm, (0, C.forearm, 0))
        b.update({"shoulder_" + nm: sh, "arm_" + nm: arm, "forearm_" + nm: fa, "hand_" + nm: hd})
    r.look = [("head", 0.5, 0.4), ("neck", 0.2, 0.1)]
    r.size = 1.0
    return r, b


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


def _under(r, name):
    """A bone and everything it carries."""
    out, todo = [], [r.by_name[name]]
    while todo:
        b = todo.pop()
        out.append(b.name)
        todo += list(b.children)
    return out


def kneel_on(r, pose, knee="r", front="l", fist="r"):
    """A kneel fitted to this body: the hips at the height that puts the knee on the floor, the front foot flat where
    the pose steps it, and the fist (or what it holds) on the ground."""
    from kit import core
    out = dict(pose)
    pv = out.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
    rot, (px, _, pz) = pv.get("rot", (0, 0, 0)), pv.get("pos", (0, 0, 0))
    down = _under(r, "shin_" + knee)
    lo, hi = -60.0, 10.0  # the hips' drop (key units, +y up): low enough to sink the knee, high enough to lift it
    for _ in range(24):
        mid = (lo + hi) / 2
        out["pelvis"] = {"rot": rot, "pos": (px, mid, pz)}
        if core.lowest(r, core.as_sample(out), down) > core.GROUND - 0.3:
            lo = mid
        else:
            hi = mid
    out["pelvis"] = {"rot": rot, "pos": (px, hi, pz)}
    rest_fr, fr = core.frames(r, {}), core.frames(r, core.as_sample(out))
    was, now = rest_fr["foot_" + front][1], fr["foot_" + front][1]
    out = core.plant(r, out, (front,), at={front: (now[0] - was[0], 0.0, now[2] - was[2])})
    # the shoulder reaches the fist down to the floor before the elbow bends it (a bent elbow would lift it)
    return core.keep_above(r, out, _under(r, "hand_" + fist), [("arm_" + fist, 0, 60, -1), ("forearm_" + fist, 0, 60)], bite=True)


def _rest_on(r, pose, guard, lo=-80.0, hi=60.0):
    """The pose with its hips at the height (key units, +y up, between lo and hi) that sets the guarded parts on the floor."""
    from kit import core
    out = dict(pose)
    pv = out.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
    rot, (px, _, pz) = pv.get("rot", (0, 0, 0)), pv.get("pos", (0, 0, 0))
    for _ in range(24):
        mid = (lo + hi) / 2
        out["pelvis"] = {"rot": rot, "pos": (px, mid, pz)}
        if core.lowest(r, core.as_sample(out), guard) > core.GROUND - 0.3:
            lo = mid
        else:
            hi = mid
    out["pelvis"] = {"rot": rot, "pos": (px, hi, pz)}
    return out


def _arm(r, sd):
    """The order to lift a hand off the floor: a hand that holds something turns at the wrist first, then the elbow and
    the shoulder; a bare fist swings from the shoulder first (bending the elbow would fling it back up)."""
    if ("hand_" + sd) in r.by_name and r.by_name["hand_" + sd].children:
        return [("hand_" + sd, 0, 50), ("forearm_" + sd, 0, 50), ("arm_" + sd, 0, 60)]
    return [("arm_" + sd, 0, 100, -1), ("forearm_" + sd, 0, 50)]  # forward, toward the floor in front of it


def _clear(r, pose, legs=True, hands=True):
    """The least turn of each leg (hip, knee, ankle) and each arm that keeps it above the floor."""
    from kit import core
    for sd in ("l", "r"):
        if legs and "shin_" + sd in r.by_name:
            pose = core.keep_above(r, pose, _under(r, "shin_" + sd), [("thigh_" + sd, 0, 40), ("shin_" + sd, 0, 40), ("foot_" + sd, 0, 60)])
        if hands and "hand_" + sd in r.by_name:
            pose = core.keep_above(r, pose, _under(r, "hand_" + sd), _arm(r, sd))
    return pose


def _blend(r, a, b, f):
    """The pose a share f of the way from a to b, its feet carried by the legs' IK between where a and b put them (a foot
    flat on the floor at both ends stays flat on it), and its hands clear of the floor. A foot that leaves the floor
    turns from one pose's angle to the other's in the air: its ankle rises by the least that keeps the toes out of the
    floor, so it steps up and over instead of rolling through it."""
    from kit import core
    p = core._lerp_pose(a, b, f)
    # straight legs bend fast for the first pixels the hips drop (and straighten fast for the last): the hips ease out
    # of standing and into it, so the knees bend at an even pace
    standing = lambda x: not any(core._pos(x.get("pelvis", (0, 0, 0))))
    g = f * f if standing(a) and not standing(b) else 1 - (1 - f) ** 2 if standing(b) and not standing(a) else f
    if g != f and "pelvis" in p:
        pa, pb = core._pos(a.get("pelvis", (0, 0, 0))), core._pos(b.get("pelvis", (0, 0, 0)))
        p["pelvis"] = {"rot": core._rot(p["pelvis"]), "pos": tuple(pa[i] + (pb[i] - pa[i]) * g for i in range(3))}
    rest = core.frames(r, {})
    fa, fb = core.frames(r, core.as_sample(a)), core.frames(r, core.as_sample(b))
    at, free = {}, []
    for sd in ("l", "r"):
        ft = "foot_" + sd
        if ft not in r.by_name:
            continue
        ea, eb, e0 = fa[ft][1], fb[ft][1], rest[ft][1]
        at[sd] = tuple(ea[i] + (eb[i] - ea[i]) * f - e0[i] for i in range(3))
        if abs(ea[1] - e0[1]) > 0.5 or abs(eb[1] - e0[1]) > 0.5 or math.hypot(eb[0] - ea[0], eb[2] - ea[2]) > 1.0:
            free.append(sd)
    q = core.plant(r, p, tuple(at), at=at)
    for sd in free:
        ft = "foot_" + sd
        foot = p.get(ft, (0, 0, 0))
        guard = _under(r, ft)

        def lifted(h):
            dx, dy, dz = at[sd]
            out = core.plant(r, q, (sd,), at={sd: (dx, dy - h, dz)})
            out[ft] = foot
            return out
        lo, hi = 0.0, 40.0 * getattr(r, "size", 1.0)
        if core.lowest(r, core.as_sample(lifted(0.0)), guard) > core.GROUND - 0.3:
            for _ in range(18):
                mid = (lo + hi) / 2
                if core.lowest(r, core.as_sample(lifted(mid)), guard) > core.GROUND - 0.3:
                    lo = mid
                else:
                    hi = mid
            q = lifted(hi)
        else:
            q = lifted(0.0)
    return _clear(r, q, legs=False)


def _between(r, keys, n=2):
    """Key poses with n more between each pair, blended by _blend."""
    out = [keys[0]]
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        for k in range(1, n + 1):
            f = k / (n + 1)
            out.append((t0 + (t1 - t0) * f, _blend(r, a, b, f)))
        out.append((t1, b))
    return out


def biped_fall(r, w=1.0):
    """The death of a biped of any proportions: it rears back, drops onto both knees (the thighs straight down, the shins
    and the feet back along the floor), then topples forward over its knees, hinged on them, as far as its body lets it:
    flat on its face with its legs out behind it, or (a hunched brute with a skull or a fist out front) bowed onto them.
    Its arms fling out ahead; nothing it carries goes through the floor on the way."""
    from kit import core
    rest = {b: r.by_name[b].rot[0] for b in ("thigh_l", "shin_l", "foot_l")}
    legs = ("thigh_", "shin_", "foot_")
    limbs = set(_under(r, "thigh_l") + _under(r, "thigh_r"))
    for sd in ("l", "r"):
        if "arm_" + sd in r.by_name:
            limbs |= set(_under(r, "arm_" + sd))
    body = [b for b in r.by_name if b not in limbs]

    def leg(thigh, shin, foot, hips):  # whole turns from straight down (+ swings the lower end back), as pose deltas
        out = {}
        for sd in ("l", "r"):
            out["thigh_" + sd] = (thigh - hips - rest["thigh_l"], 0, 0)
            out["shin_" + sd] = (shin - thigh - rest["shin_l"], 0, 0)
            out["foot_" + sd] = (foot - shin - rest["foot_l"], 0, 0)
        return out
    rear = core.plant(r, {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0, 1)}, "chest": (-16, 0, 0), "head": (-30, 0, 0),
                          "arm_l": (-30, 0, -30), "arm_r": (-30, 0, 30)})
    knees = {"pelvis": {"rot": (20, 0, 0), "pos": (0, 0, 0)}, "chest": (20, 0, 0), "head": (24, 0, 0), "arm_l": (-10, 0, -10),
             "arm_r": (-10, 0, 10)}
    knees.update(leg(0, 90, 180, 20))
    knees = _rest_on(r, knees, _under(r, "shin_l") + _under(r, "shin_r"))
    k0 = core.frames(r, core.as_sample(knees))["shin_l"][1]

    def toppled(a):  # a: 0 on its knees to 1 flat on its face, the knees where they knelt
        hips = 20 + 64 * a
        p = {"pelvis": {"rot": (hips, 0, 8 * a), "pos": (0, 0, 0)}, "chest": (20 - 16 * a, 0, 0), "head": (24 - 18 * a, 30 * a, 0),
             "arm_l": (-10 - 150 * a, 0, -10 - 4 * a), "arm_r": (-10 - 140 * a, 0, 10 + 16 * a)}
        p.update(leg(90 * a, 90, 180, hips))
        k = core.frames(r, core.as_sample(p))["shin_l"][1]
        p["pelvis"]["pos"] = (0, k[1] - k0[1], k0[2] - k[2])  # key pos: +y up, frames: +y down
        return p
    a = 0.0
    while a < 1.0 and core.lowest(r, core.as_sample(toppled(min(1.0, a + 0.02))), body) <= core.GROUND - 0.3:
        a = min(1.0, a + 0.02)
    down = _clear(r, toppled(a))
    settled = dict(down)
    hd = down.get("head", (0, 0, 0))
    settled["head"] = (hd[0] + 2, hd[1] + 4, hd[2])
    sag = core.plant(r, {b: v for b, v in core._lerp_pose(rear, knees, 0.45).items() if not b.startswith(legs)})
    knees = _clear(r, knees, legs=False)
    # standing to kneeling, the feet carried back by the legs; then over its knees on the hinge itself
    keys = _between(r, [(0, {}), (0.3 * w, rear), (0.6 * w, sag), (0.9 * w, knees)], n=2)
    keys += [(0.9 * w + 0.7 * w * k / 4, _clear(r, toppled(a * k / 4))) for k in range(1, 5)]
    core.keyed(r, "death", 2.4 * w, keys + [(2.4 * w, settled)])
    core.ground_pass(r, [(_under(r, "hand_" + sd), _arm(r, sd)) for sd in ("l", "r") if "hand_" + sd in r.by_name], clips=["death"], skip=())


def biped_reactions(r, weight=1.0):
    """The flinches by side, the kneel when its stance breaks and the rise, the death fall."""
    from kit import core
    from kit.core import keyed, planted
    w = weight ** 0.5
    size = getattr(r, "size", 1.0)
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
    kneel = _clear(r, kneel_on(r, kneel), legs=False)  # the other hand (and what it holds) off the floor too
    # on the way down and back up the feet stay under it (the crouch depths are for a 36 px leg)
    # the bow already reaches the fist halfway to where the kneel puts it
    bow = core.plant(r, {"pelvis": {"rot": (14, 0, 0), "pos": (0, -10 * size, 1)}, "chest": (24, 0, 0), "head": (30, 0, 0)})
    bow.update(core._scaled({b: kneel[b] for b in ("arm_r", "forearm_r") if b in kneel}, 0.5))
    bow = _clear(r, bow, legs=False)
    crouch = _clear(r, core.plant(r, {"pelvis": {"rot": (6, 0, 0), "pos": (0, -5 * size, 0)}, "chest": (8, 0, 0), "head": (-8, 0, 0)}), legs=False)
    keyed(r, "kneel", 0.5 * w, _between(r, [(0, {}), (0.18 * w, bow), (0.5 * w, kneel)], n=4))
    keyed(r, "rise", 0.7 * w, _between(r, [(0, kneel), (0.35 * w, crouch), (0.7 * w, {})], n=4))
    # and no hand (or what it holds) left going through the floor between those keys (the legs are the keys' own)
    core.ground_pass(r, [(_under(r, "hand_" + sd), _arm(r, sd)) for sd in ("l", "r") if "hand_" + sd in r.by_name], clips=["kneel", "rise"])
    biped_fall(r, w)
