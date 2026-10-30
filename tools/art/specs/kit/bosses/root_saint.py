"""The Root Saint (SPEC §35.11, docs/bosses/ROLLOUT.md): the Overgrown Temple's Lord, a Caster. The Rooted Saint, Euan's
pick from its silhouette round: a temple statue of a saint that came alive and grew roots.

A tall robed figure in pale temple stone gone green with moss, 4.4 blocks to its hood and 5.3 to the top of the stone
sunburst behind its head, its hands pressed together in prayer before its chest. Below the robe there are no feet: its
hem sits on a crown of roots that spread over the floor, and it moves by reaching them out, gripping and pulling itself
along. A deep red scapular hangs down its front, censers swing from its belt on chains, a hood frames a calm stone face,
and where the roots broke through its chest a crack glows with the amber of the incense. Its light story is that amber:
the crack, its eyes, the censers' windows and the cracks through the sunburst.

Moves (SPEC §35.11): it parts its hands and sweeps one across the front (LIGHT, and the other back), Saint's Palm (both
hands raised over its head and brought down together onto the floor in front of it: HEAVY), Root Lance (it drives its
hand down and sweeps it toward you, its front roots plunging into the floor: the lances rise in a line), Root Grasp (it
reaches its open hand at you and closes it: GRAB), a root behind it that whips over at whoever stands there, Root Ring
(every root rears up and plunges into the floor round it), Grow Creakers (palms down over the floor, the roots spreading
wide), Thorn Halo (arms open wide, face to the sky, the sunburst turning and blazing: the signature), and a haul that
drags it at you on its roots. It opens its eyes out of its prayer, and dies falling on its face like a felled statue.
"""
import math

from kit import core, parts, plans
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "root_saint"
SCALE = 1.8  # the hitbox on vanilla's creaking: 1.6 x 4.9 blocks; the saint is modelled at true size

# The Rooted Saint (docs/bosses/ROLLOUT.md, Euan's pick A, 2026-10-28). Proportions from its silhouette: a long robe to
# the waist at 2.5 blocks (statues of saints are drawn long), the chest and shoulders to 3.6, the hooded head to 4.4,
# the sunburst's middle behind the head at 4.25 and its rays out to 5.3. Value story: the amber brightest (the crack,
# the eyes, the censers, the sunburst's cracks), then the pale stone (lightest at the face, the hands and the shoulders,
# darker and greener toward the floor), then the red cloth and the moss, then the dark roots.
C = plans.Caster(knee=18, waist=40, chest=48, neck=58, shoulder=(9.5, 56), arm=18, forearm=15, head=60)  # long arms: it can lay a palm on the floor
PRAY = {"l": (1.4, 50.0, -10.5), "r": (-1.4, 50.0, -10.5)}  # the wrists in prayer (x, height, z), model pixels
POLE = {"l": (0.35, 1.0, 0.5), "r": (-0.35, 1.0, 0.5)}  # where the elbows point in prayer: down, out and back
# the roots on the floor: (yaw from the front, degrees: + to its right; the three segments' lengths; thickness; how far
# the tip lies out from where it leaves the hem)
ROOTS = ((6, (8, 9, 8), 4, 0), (48, (7, 8, 7), 3, 0), (92, (8, 8, 7), 4, 0), (134, (8, 9, 8), 3, 0),
         (178, (9, 10, 9), 4, 0), (-130, (8, 9, 8), 3, 0), (-88, (8, 9, 7), 4, 0), (-44, (7, 8, 8), 3, 0))
ROOT_AT = (11.0, 2.0)
# each tip lies nearly as far out as the root reaches, so the roots hug the floor in a low arch
ROOTS = tuple((y, ls, w, round(0.95 * (ls[0] + ls[1] - 2) + ls[2], 1)) for y, ls, w, _ in ROOTS)  # where each root leaves the hem: its distance out from the middle and its height, pixels
SUN = (0.0, 68.0, 10.0)  # the sunburst's middle (x, height, z)
RAYS = ((0, 12, 3), (30, 7, 2), (60, 11, 2), (90, 7, 2), (120, 12, 3), (150, 4, 2), (180, 11, 3), (210, 7, 2),
        (240, 12, 2), (270, 7, 2), (300, 11, 3), (330, 7, 2))  # (roll, length, width); the ray at 150 broke off


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed by height: x, how far up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    """A cube centred on (cx, cz), from y0 up to y0 + h over its bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


# ---------------------------------------------------------------------------------------------------------------------
# Reaching: the arms by two-bone IK, the roots by an arch that sets their tips where they're wanted

def _v(a, b, f=1.0):
    return tuple(a[i] + f * b[i] for i in range(3))


def _sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def _dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def _unit(v):
    n = math.sqrt(_dot(v, v)) or 1.0
    return tuple(c / n for c in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _model(p):
    """(x, height, z) to model pixels (y down)."""
    return (p[0], 24.0 - p[1], p[2])


def _arm_turns(r, pose, side, wrist, pole):
    """The arm's and forearm's whole turns (radians) that put the wrist at a model point with the elbow pointing along
    pole (a model direction), the shoulder where the pose has it. The elbow is a hinge: the forearm folds toward the front
    of the upper arm."""
    fr = core.frames(r, core.as_sample(pose))
    arm, fa, hd = (r.by_name[f"{n}_{side}"] for n in ("arm", "forearm", "hand"))
    Ms, Ts = fr["shoulder_" + side]
    p0 = _v(Ts, R._mv(Ms, arm.pivot))
    a, b = fa.pivot[1], hd.pivot[1]
    d = _sub(wrist, p0)
    L = max(abs(a - b) + 1e-3, min(a + b - 1e-3, math.sqrt(_dot(d, d))))
    u = _unit(d)
    n = _unit(_v(pole, u, -_dot(pole, u)))
    ca = (a * a + L * L - b * b) / (2 * a * L)
    sa = math.sqrt(max(0.0, 1 - ca * ca))
    elbow = _v(_v(p0, u, a * ca), n, a * sa)
    ua = _unit(_sub(elbow, p0))
    fd = _unit(_sub(_v(p0, u, L), elbow))
    perp = _v(fd, ua, -_dot(fd, ua))
    z = _unit(perp) if _dot(perp, perp) > 1e-8 else n
    z = tuple(-c for c in z)
    x = _cross(ua, z)
    M = tuple((x[i], ua[i], z[i]) for i in range(3))
    local = R._mm(core._t(Ms), M)
    return core._euler(local), math.atan2(_dot(fd, z), _dot(fd, ua))


def orient(r, pose, bone, ydir, xdir):
    """A bone's turn (degrees, on top of its rest) that lays its +y along ydir and its +x as near xdir as that allows
    (model directions), its parent where the pose has it: a hand set upright, a palm turned down."""
    fr = core.frames(r, core.as_sample(pose))
    b = r.by_name[bone]
    y = _unit(ydir)
    x = _unit(_v(xdir, y, -_dot(xdir, y)))
    z = _cross(x, y)
    M = tuple((x[i], y[i], z[i]) for i in range(3))
    e = core._euler(R._mm(core._t(fr[b.parent.name][0]), M))
    return _delta(tuple(v / DEG for v in e), b.rot)


def _delta(e, rest):
    """A whole turn (degrees) as the turn on top of rest, written the shortest way: of its two Euler forms (x, y, z) and
    (x + 180, 180 - y, z + 180), each wrapped round to its rest, the smaller. Keys written another way would spin the
    bone between them."""
    best = None
    for c in (e, (e[0] + 180, 180 - e[1], e[2] + 180)):
        d = tuple((c[i] - rest[i] + 180) % 360 - 180 for i in range(3))
        if best is None or sum(v * v for v in d) < sum(v * v for v in best):
            best = d
    return best


def reach(r, pose, side, wrist, pole=None, hand=None):
    """The pose with that arm reaching its wrist to (x, height, z); the hand's own turn (degrees) kept or set."""
    pole = pole or POLE[side]
    (ax, ay, az), k = _arm_turns(r, pose, side, _model(wrist), pole)
    arm, fa = r.by_name["arm_" + side], r.by_name["forearm_" + side]
    out = dict(pose)
    out["arm_" + side] = _delta((ax / DEG, ay / DEG, az / DEG), arm.rot)
    out["forearm_" + side] = (k / DEG - fa.rot[0], -fa.rot[1], -fa.rot[2])
    if hand is not None:
        out["hand_" + side] = hand
    return out


def _root_turns(k, tip, phi=90.0):
    """Root k's turns (degrees, whole): the first segment's (pitch, yaw) and the next two's pitch, that set its tip at
    (x, height, z) with the last segment phi degrees from straight down (90 lies along the floor, more curls it up, less
    points it into the floor); the root arches up between its hem and the tip."""
    yaw0, (l0, l1, l2), w, _ = ROOTS[k]
    out, h0 = ROOT_AT
    px, pz = -out * math.sin(yaw0 * DEG), -out * math.cos(yaw0 * DEG)
    vx, vz = tip[0] - px, tip[2] - pz
    yaw = math.atan2(-vx, -vz)
    D = math.hypot(vx, vz)
    H = tip[1] - h0
    a, b = l0 - 1, l1 - 1
    js, jh = D - l2 * math.sin(phi * DEG), H + l2 * math.cos(phi * DEG)
    dist = max(abs(a - b) + 1e-3, min(a + b - 1e-3, math.hypot(js, jh)))
    pj = math.atan2(js, -jh)
    beta = math.acos(max(-1.0, min(1.0, (a * a + dist * dist - b * b) / (2 * a * dist))))
    p0 = pj + beta
    j1 = (a * math.sin(p0), -a * math.cos(p0))
    p1 = math.atan2(js - j1[0], -(jh - j1[1]))
    return (-p0 / DEG, yaw / DEG), -(p1 - p0) / DEG, -(phi * DEG - p1) / DEG


def root_tip(k, out=0.0, side=0.0, up=None):
    """Where root k's tip lies at rest (x, height, z), moved out along it, across it (+ toward the saint's left when it
    points ahead) and lifted to a height (None: on the floor)."""
    yaw0, _, w, D = ROOTS[k]
    ox, oz = -math.sin(yaw0 * DEG), -math.cos(yaw0 * DEG)  # outward along the floor
    s = ROOT_AT[0] + D + out
    return (ox * s - oz * side, w / 2 if up is None else up, oz * s + ox * side)


def roots(pose, tips):
    """The pose with the roots set: tips {k: ((x, height, z), phi)} (others at rest)."""
    out = dict(pose)
    for k, (tip, phi) in tips.items():
        (p0, yaw), p1, p2 = _root_turns(k, tip, phi)
        rest = REST_ROOT[k]
        dy = (yaw - rest[0][1] + 180) % 360 - 180  # the short way round: the back root points near 180
        out[f"root{k}a"] = (p0 - rest[0][0], dy, 0)
        out[f"root{k}b"] = (p1 - rest[1], 0, 0)
        out[f"root{k}c"] = (p2 - rest[2], 0, 0)
    return out


REST_ROOT = [_root_turns(k, root_tip(k)) for k in range(len(ROOTS))]


# ---------------------------------------------------------------------------------------------------------------------
# The body

def body():
    r, b = plans.caster(ID, C)
    base, robe, pel, sp, ch, nk, hd = (b[n] for n in ("base", "robe", "pelvis", "spine", "chest", "neck", "head"))
    # the roots: a crown of them under the hem, eight spreading over the floor, each an arch of three segments
    rt = r.bone("roots", (0, 24, 0))
    _box(rt, "crown", 0, 0, 0.5, 30, 2, 27)  # gnarled, showing round the hem
    for k, (x, z, w, d) in enumerate(((-12, -10, 5, 4), (12, -9, 4, 5), (13, 9, 5, 4), (-13, 8, 4, 5), (0, 14, 6, 3))):
        _box(rt, f"knar{k}", x, 0, z, w, 3, d)
    o, h0 = ROOT_AT
    for k, (yaw, lens, w, _) in enumerate(ROOTS):
        (p0, yw), p1, p2 = REST_ROOT[k]
        s0 = _bone(rt, f"root{k}a", -o * math.sin(yaw * DEG), h0, -o * math.cos(yaw * DEG), (p0, yw, 0))
        s0.cube(f"root{k}a", -w / 2, -1, -w / 2, w, lens[0] + 1, w)  # thick where it leaves the hem, thinning to the tip
        s1 = s0.bone(f"root{k}b", (0, lens[0] - 1, 0), (p1, 0, 0))
        s1.cube(f"root{k}b", -(w - 1) / 2, 0, -(w - 1) / 2, w - 1, lens[1], w - 1)
        s2 = s1.bone(f"root{k}c", (0, lens[1] - 1, 0), (p2, 0, 0))
        s2.cube(f"root{k}c", -1, 0, -1, 2, lens[2], 2)
    # the robe: one long fall of stone from the waist, pooling round the roots at its hem; long folds down it
    parts.taper(base, "hem", 0, 0, 0.5, [(28, 2, 25), (25, 4, 22), (22, 12, 19)], up=True)
    parts.taper(robe, "robe", 0, 0, 0.5, [(21, 11, 18), (20, 11, 17)], up=True)
    for k, (x, w) in enumerate(((-8.5, 2), (-5.5, 1), (6, 2), (8.8, 1))):  # beside the scapular, which covers the middle
        _box(base, f"fold{k}", x, 2, -9.5, w, 16, 1)
        _box(robe, f"foldu{k}", x * 0.95, -0.5, -9.0, w, 12, 1)
    for k, (x, w) in enumerate(((-6, 2), (-1, 3), (5, 2))):
        _box(base, f"foldb{k}", x, 2, 10.5, w, 16, 1)
        _box(robe, f"foldbu{k}", x * 0.95, -0.5, 10.0, w, 12, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        _box(base, "folds_" + nm, side * 11.5, 2, 1, 1, 15, 3)
        _box(robe, "foldsu_" + nm, side * 11.0, -0.5, 1, 1, 12, 3)
    _box(base, "moss0", -7, 2, -10, 8, 4, 1)
    _box(base, "moss1", 11.2, 2, -2, 1, 6, 8)
    # a root that climbed the robe's left side and one up its back
    # a band of roots wound round the robe where it bends, like a knee under the stone
    _box(base, "kneeroot", 0, 15.5, 0.5, 23, 3, 20)
    _box(robe, "kneeroot1", 0, 0, 0.5, 22, 2, 19)
    _box(base, "climb0", 11.6, 4, -5, 2, 13, 2)
    _box(robe, "climb1", 10.7, -1, -4, 2, 9, 2)
    _box(base, "climb2", 2.5, 3, 10.4, 2, 14, 2)
    # the belt, its cord's knot and two ends; the censers on chains; the red scapular down the front
    _box(pel, "belt", 0, -1, 0.5, 21, 2, 18)
    _box(pel, "knot", 2, -2, -8.4, 3, 3, 1)
    for k, x in enumerate((1.5, 3)):
        cd = _bone(pel, f"cord{k}", x, -2, -8.4, (-4, 0, 6 - 10 * k))
        _box(cd, f"cord{k}", 0, -8, 0, 1, 8, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        links = parts.chain(pel, "chain_" + nm, (side * 10, 1, -1), 3, link=(1, 3, 1))
        links[0].rot = (0.0, 0.0, -side * 22.0)  # hung out from the belt so the censer clears the robe's side
        cn = links[-1].bone("censer_" + nm, (0, 3, 0))
        _box(cn, "censer_top_" + nm, 0, -1, 0, 2, 1, 2)
        _box(cn, "censer_cap_" + nm, 0, -2, 0, 4, 1, 4)
        _box(cn, "censer_" + nm, 0, -6, 0, 5, 4, 5)
        _box(cn, "censer_win_" + nm, 0, -5, 0, 6, 2, 3)
        _box(cn, "censer_foot_" + nm, 0, -7, 0, 3, 1, 3)
    sc = _bone(pel, "scap0", 0, 0, -9.0, (-1.5, 0, 0))
    for k, ln in enumerate((13, 13, 11)):  # lying on the robe, out over its hem at the foot
        if k:
            sc = _bone(sc, f"scap{k}", 0, -(13 - 1), 0, ((0.5, -4)[k - 1], 0, 0))
        _box(sc, f"scap{k}", 0, -ln, 0, 8, ln, 1)
    _box(sc, "scap_fringe", 0, -13, 0, 6, 2, 1)
    # the waist and the chest, the crack where the roots broke through it glowing; the red stole over the shoulders
    _box(sp, "spine", 0, 0, 0.5, 18, 8, 15)
    _box(ch, "chest", 0, 0, 0.5, 19, 10, 15)
    _box(ch, "chest_front", 0, 2, -7.2, 13, 7, 1)
    _box(ch, "heart", 0, 1, -7.9, 3, 6, 1)
    _box(ch, "heart_rim", 0, 0, -7.7, 5, 8, 1)
    for k, (x, y, rz) in enumerate(((3, 6, -40), (-2.5, 2, 30))):
        rr = _bone(ch, f"heartroot{k}", x, y, -8.2, (0, 0, rz))
        _box(rr, f"heartroot{k}", 0, 0, 0, 1, 6, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        _box(ch, "stole_" + nm, side * 5, -8, -8.3, 3, 18, 1)
    _box(ch, "mantle", 0, 8, 0.5, 23, 3, 17)
    _box(ch, "mantle_front", 0, 7, -8.2, 15, 3, 1)
    cp = _bone(ch, "cape0", 0, 8, 8.6, (4, 0, 0))
    _box(cp, "cape0", 0, -12, 0, 21, 12, 1)
    cp = _bone(cp, "cape1", 0, -11, 0, (2, 0, 0))
    _box(cp, "cape1", 0, -11, 0, 20, 11, 1)
    _box(ch, "moss2", 8, 9.5, 0, 5, 2, 9)
    # the hooded head: a calm stone face, eyes lowered, the hood framing it
    _box(nk, "neck", 0, 0, 0, 7, 2, 7)
    _box(hd, "head", 0, 0, -0.5, 8, 9, 8)
    _box(hd, "nose", 0, 3, -5, 2, 3, 1)
    _box(hd, "brow", 0, 6, -4.9, 8, 1, 1)
    _box(hd, "eyes", 0, 5, -4.7, 6, 1, 1)
    _box(hd, "hood_back", 0, -0.5, 4.5, 12, 12, 2)
    for side, nm in ((1, "l"), (-1, "r")):
        _box(hd, "hood_" + nm, side * 5, -0.5, -1, 2, 11, 10)
    _box(hd, "hood_top", 0, 9, -0.5, 12, 2, 10)
    _box(hd, "hood_peak", 0, 10, -4.5, 6, 2, 3)
    # the sunburst on a stalk from its back: a stepped disc, twelve rays (one broken off), roots wound through it
    sun = _bone(ch, "sun", 0, 8, 8.6)
    _box(sun, "stalk", 0, 0, 0.6, 3, 12, 2)
    disc = _bone(sun, "sun_disc", SUN[0], SUN[1] - (C.chest + 8), SUN[2] - 8.6)
    _box(disc, "disc", 0, -6, 0, 12, 12, 2)
    dt = _bone(disc, "disc_t", 0, 0, 0, (0, 0, 45))
    _box(dt, "disc_t", 0, -5, 0.2, 10, 10, 2)
    for k, (roll, ln, w) in enumerate(RAYS):
        ray = _bone(disc, f"ray{k}", 0, 0, 0, (0, 0, roll))
        _box(ray, f"ray{k}", 0, 7, 0, w, ln, 2)
    for k, (rz, ln) in enumerate(((28, 24), (-56, 20), (112, 18))):
        rr = _bone(disc, f"sunroot{k}", 0, 0, -1.4, (0, 0, rz))
        _box(rr, f"sunroot{k}", 0, -ln / 2, 0, 2, ln, 1)
    _box(disc, "moss3", 4, 4, -1.4, 4, 2, 1)
    # the arms in their wide stone sleeves, the hands pressed together in prayer
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        arm, fa, hn = b["arm_" + nm], b["forearm_" + nm], b["hand_" + nm]
        arm.cube("arm_" + nm, -3, -2, -3, 6, 16, 6, mirror=mir)
        arm.cube("sleeve_" + nm, -3.5, 12, -3.5, 7, 6, 7, mirror=mir)
        fa.cube("forearm_" + nm, -2.5, -1, -2.5, 5, 15, 5, mirror=mir)
        fa.cube("cuff_" + nm, -3.5, 11, -3.5, 7, 4, 7, mirror=mir)  # the sleeve opens at the wrist
        x0 = -PRAY[nm][0] if side > 0 else -2 - PRAY[nm][0]  # the palms meet in the middle (the hand's x runs to its left)
        hn.cube("hand_" + nm, x0, 0, -2.5, 2, 7, 5, mirror=mir)
        hn.cube("thumb_" + nm, x0, 1, 2.5, 2, 3, 1, mirror=mir)
        fg = hn.bone("fingers_" + nm, (0, 7, 0))  # the fingers on their own joint: they curl to close the hand
        fg.cube("fingers_" + nm, x0, 0, -2, 2, 4, 4, mirror=mir)
    hd.rot = (10.0, 0.0, 0.0)  # its head bowed over its hands
    r.shadow = 1.6
    # secondary parts (SPEC §35.10): the censers swing on their chains, the scapular, the cape and the cord ends trail
    r.springs = [("chain_l0", 0.6, 1.4, 0.15), ("chain_r0", 0.6, 1.5, 0.15), ("chain_l1", 0.4, 1.8, 0.2), ("chain_r1", 0.4, 1.8, 0.2),
                 ("scap0", 0.25, 1.6, 0.3), ("scap1", 0.3, 1.8, 0.3), ("scap2", 0.35, 2.0, 0.3), ("cape0", 0.2, 1.8, 0.35),
                 ("cape1", 0.3, 2.0, 0.35), ("cord0", 0.6, 2.4, 0.25), ("cord1", 0.6, 2.4, 0.25)]
    r.guard = [f"root{k}c" for k in range(len(ROOTS))] + ["censer_l", "censer_r"]
    _pray(r)
    return r


def _pray(r):
    """Sets the arms' rest turns to its prayer: the wrists together before its chest, the elbows down and out, the hands
    upright palm to palm."""
    for side in ("l", "r"):
        (ax, ay, az), k = _arm_turns(r, {}, side, _model(PRAY[side]), POLE[side])
        r.by_name["arm_" + side].rot = (ax / DEG, ay / DEG, az / DEG)
        r.by_name["forearm_" + side].rot = (k / DEG, 0.0, 0.0)
        r.by_name["hand_" + side].rot = orient(r, {}, "hand_" + side, (0, -1, -0.22), (1, 0, 0))


# ---------------------------------------------------------------------------------------------------------------------
# Paint

def paint(r, P_):
    """Five materials in a value order: the amber of the incense brightest (the crack in its chest, its eyes, the
    censers' windows, the cracks through the sunburst), then the pale temple stone (lightest at the face, the hands and
    the shoulders, weathering darker and greener toward the floor, streaked where the rain ran down it), then the faded
    red cloth and the moss, then the dark roots, barked along their length."""
    stone = Ramp("#38342e", "#4a463e", "#5e5a50", "#757064", "#8c8678", "#a39d8c", "#b9b3a0", "#cdc7b3", "#ded9c6", "#ebe7d6")
    moss = Ramp("#18240e", "#203214", "#2a421a", "#355420", "#426828", "#527c30", "#64923a")
    cloth = Ramp("#200809", "#300d0e", "#421214", "#561a1b", "#6a2223", "#7e2c2b", "#923636")
    root = Ramp("#120a06", "#1c120b", "#281a10", "#352316", "#432d1c", "#523822", "#62442a")
    bronze = Ramp("#18120a", "#261c10", "#362816", "#4a381e", "#604a28", "#7a6034", "#947842")
    amber = ("#8a3c08", "#c8661a", "#f0962e", "#ffc060", "#ffe6a6")
    of = lambda *ps: parts.cubes_of(r, *ps)
    # rain streaks down the stone's sides, darker in the folds' shadow; a lit edge where a tier steps out
    def weather(f, i, j):
        if f.name not in SIDES:
            return 0.3 if (i * 5 + j * 3) % 13 == 0 else 0
        v = -0.7 if R._h(f.cube.name, f.name, i) % 5 == 0 else 0
        return v + (-0.5 if j == f.h - 1 else 0)
    # the stone, lightest high on the body and toward the face, darker toward the floor
    tiers = ((6.4, ["head", "nose"] + of("hand_", "fingers_", "thumb_")),
             (6.0, ["brow", "mantle", "mantle_front"] + of("hood_", "cuff_")),
             (5.6, ["chest", "chest_front"] + of("arm_", "sleeve_", "forearm_", "disc", "ray", "stalk")),
             (5.1, ["spine", "belt"] + of("cape")),
             (4.8, of("robe", "foldu", "foldbu", "foldsu_")),
             (4.3, ["hem2"] + of("fold", "folds_")),
             (3.8, ["hem1", "hem0"]))
    for base, cs in tiers:
        for c in cs:
            if c in P_.cubes:
                flat = c.startswith(("robe", "hem", "fold"))  # the robe reads as one fall of stone, not a stack
                P_.paint(c, stone, base, weather, grad=1.6 if flat else 1.0, rim=0.15 if flat else 0.5, ao=0.35 if flat else 0.9,
                         noise=0.5, nscale=5)
    # moss creeping up from the floor: a green stain over the lowest rows of the hem and the folds
    for c in of("hem", "fold", "robe"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                y_up = (f.h - 1 - j) + (0 if c.startswith("hem0") else 2 if c.startswith("hem1") else 18 if c.startswith("robe") else 6)
                if R._h(c, fname, i, j) % 9 < max(0, 6 - y_up // 2):
                    f.set(i, j, moss.at(2 + R._h(c, i, j) % 3))
    for c in of("moss"):
        P_.paint(c, moss, 3.6, R.p_blotch(c, scale=3, amount=1.4), grad=0.6, rim=0.9, noise=0.6, nscale=3)
    # the cloth: a weave down it, darker in its folds, frayed at the ends; a thin gold thread at the stole's edges
    weave = lambda f, i, j: (-0.8 if i % 3 == 2 else 0.25 if (i + j) % 2 == 0 else 0)
    for c in of("scap", "stole_"):
        P_.paint(c, cloth, 3.9, weave, grad=1.2, rim=0.3, ao=0.8, noise=0.4)
    for c in of("stole_"):
        f = P_.face(c, "north")
        for j in range(f.h):
            f.set(0, j, hexc("#9a7a34"))
            f.set(f.w - 1, j, hexc("#7c5e26"))
    for c in ("scap2", "scap_fringe"):
        f = P_.face(c, "north")
        for i in range(f.w):
            for j in range(f.h - 2, f.h):
                if (i + j) % 2:
                    f.set(i, j, cloth.at(0))
    # the roots: dark bark ridged along their length, lighter where they rub on the floor
    bark = lambda f, i, j: (0.9 if f.name == "top" else 0) + (-0.8 if (i if f.name in SIDES else j) % 2 == 0 else 0.2)
    for c in of("root", "crown", "knar", "climb", "kneeroot", "cord", "heartroot", "sunroot", "chain_"):
        P_.paint(c, root if not c.startswith(("cord", "chain_")) else bronze, 3.0 if not c.startswith("chain_") else 3.4, bark,
                 grad=0.6, rim=0.6, ao=0.6, noise=0.4)
    P_.paint("knot", cloth, 3.0)
    # the censers: dark bronze, their window bands lit from inside
    for nm in ("l", "r"):
        for c in ("censer_top_", "censer_cap_", "censer_", "censer_foot_"):
            P_.paint(c + nm, bronze, 3.6, lambda f, i, j: (1.0 if j == 0 else 0), rim=1.0, noise=0.3)
        P_.paint("censer_win_" + nm, bronze, 2.4)
        for fname in SIDES:
            g, f = P_.face("censer_win_" + nm, fname, glow=True), P_.face("censer_win_" + nm, fname)
            for i, j in g.each():
                if i % 2 == 0:
                    g.set(i, j, hexc(amber[3] if j == 0 else amber[2], 240))
                    f.set(i, j, bronze.at(0))
    # the crack in its chest: the heartwood glowing amber through a rim of broken stone, a root out of it either way
    P_.paint("heart_rim", stone, 3.2, lambda f, i, j: (-1.0 if (i + j) % 3 == 0 else 0))
    for fname in ("north", "west", "east"):
        edge_glow(P_, "heart_rim", fname, amber[1], rows=1, alpha=170, side="left")
        edge_glow(P_, "heart_rim", fname, amber[1], rows=1, alpha=170, side="right")
    P_.paint("heart", Ramp("#4a1a04", "#7a3008"), 1.0)
    for fname in ("north", "west", "east", "top", "bottom"):
        radial(P_, "heart", fname, [amber[4], amber[3], amber[2]])
    # the stone cracked: the chest about its wound, a robe tier, the sunburst's disc; each crack holds the amber light
    for c, n in (("chest", 2), ("chest_front", 1), ("robe0", 1), ("disc", 2), ("disc_t", 1), ("ray4", 1), ("hood_l", 1)):
        cracks(P_, c, ("north",), stone.at(1), amber[1:4], n=n, seed=c, alpha=200)
    # the face: calm, the eyes lowered slits of amber under the brow; the mouth a dark line
    f = P_.face("head", "north")
    for i in range(2, f.w - 2):
        f.set(i, f.h - 3, stone.at(3))
    P_.paint("eyes", stone, 3.0)
    with P_.eyes():
        f, g = P_.face("eyes", "north"), P_.face("eyes", "north", glow=True)
        for i, j in f.each():
            if i in (0, 1, f.w - 2, f.w - 1):
                f.set(i, j, stone.at(1))
                g.set(i, j, hexc(amber[4] if i in (1, f.w - 2) else amber[2], 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing

PRAY_UP = (0.0, -1.0, -0.22)  # the hands in prayer point up and a little forward


def _carry(r, pose, d, at=None):
    """A direction (or with at, a point (x, height, z)) fixed to the chest at rest, where the pose carries the chest."""
    M0, T0 = core.frames(r, {})["chest"]
    M, T = core.frames(r, core.as_sample(pose))["chest"]
    if at is None:
        return R._mv(M, R._mv(core._t(M0), d))
    q = _v(T, R._mv(M, R._mv(core._t(M0), _sub(_model(at), T0))))
    return (q[0], 24.0 - q[1], q[2])


def _body(lean=0.0, yaw=0.0, roll=0.0, knee=0.0):
    """The bow (+ forward), the turn (+ to its left) and the roll, spread from the robe's knee up through the waist and
    the chest: a statue bends little at the robe and most at the waist; knee adds a fold at the robe's knee (the
    deepest bows, where it sinks as if kneeling under the stone)."""
    return {"robe": (0.15 * lean + knee, 0.1 * yaw, 0.2 * roll), "pelvis": (0.45 * lean, 0.35 * yaw, 0.4 * roll),
            "spine": (0.25 * lean, 0.3 * yaw, 0.25 * roll), "chest": (0.15 * lean, 0.25 * yaw, 0.15 * roll)}


def _plumb(p, swing=0.0, sway=(0.0, 0.0)):
    """The censers' chains and the scapular hang plumb under the bowed body (stiff cloth keeps a little of the bow),
    plus a swing (+ back) and each censer's sway out."""
    tilt = sum(_rot_of(p.get(b))[0] for b in ("robe", "pelvis"))
    roll = sum(_rot_of(p.get(b))[2] for b in ("robe", "pelvis"))
    p.setdefault("chain_l0", (-tilt + swing, 0, -roll - sway[0]))
    p.setdefault("chain_r0", (-tilt + swing, 0, -roll + sway[1]))
    p.setdefault("scap0", (-0.8 * tilt + 0.5 * swing, 0, -0.6 * roll))
    return p


def _rot_of(v):
    if v is None:
        return (0.0, 0.0, 0.0)
    return v.get("rot", (0, 0, 0)) if isinstance(v, dict) else v


def _rt(k, out=0.0, side=0.0, up=None, phi=90.0, dz=0.0):
    """A root's tip for roots(): moved out along it, across it, lifted, the last segment at phi, and moved dz (model
    z, + back) on top."""
    x, h, z = root_tip(k, out, side, up)
    return ((x, h, z + dz), phi)


def _pose(r, lean=0.0, yaw=0.0, roll=0.0, head=(0, 0, 0), arms=None, hands=None, fingers=None, tips=None,
          pray=("l", "r"), extra=None, knee=0.0, swing=0.0):
    """A key pose: the body's bow, turn and roll; the head's turn; arms {side: (wrist (x, height, z), pole)} reached by
    IK, every other side in pray held in prayer where the chest carries it; hands {side: (fingers' way, palm's x way)}
    turned to face that way in the model (else straight on from the forearm, or upright in prayer); fingers {side:
    curl degrees}; the roots' tips {k: tip}; extra bones last."""
    p = _body(lean, yaw, roll, knee)
    p["head"] = head
    arms, hands, fingers = arms or {}, hands or {}, fingers or {}
    for side in ("l", "r"):
        if side in arms:
            wrist, pole = arms[side]
            p = reach(r, p, side, wrist, pole)
            if side in hands:
                p["hand_" + side] = orient(r, p, "hand_" + side, *hands[side])
            else:
                p["hand_" + side] = tuple(-v for v in r.by_name["hand_" + side].rot)
        elif side in pray:
            p = reach(r, p, side, _carry(r, p, None, at=PRAY[side]), _carry(r, p, POLE[side]))
            p["hand_" + side] = orient(r, p, "hand_" + side, _carry(r, p, PRAY_UP), _carry(r, p, (1.0, 0.0, 0.0)))
        if side in fingers:
            p["fingers_" + side] = (fingers[side], 0, 0)
    if tips:
        p = roots(p, tips)
    if extra:
        p.update(extra)
    return _plumb(p, swing)


def _censers(swing_l, swing_r, out=0.0, tilt=0.0):
    """The censers' chains swung forward (-) or back (+) from plumb under a body bowed by tilt, and out from the robe."""
    return {"chain_l0": (swing_l - tilt, 0, -out), "chain_r0": (swing_r - tilt, 0, out)}


# ---------------------------------------------------------------------------------------------------------------------
# Clips

STANCE = 14.0  # how far a root pulls while its tip grips the floor, pixels
WAVE = {0: 0.0, 6: 0.2, 3: 0.4, 2: 0.55, 7: 0.35, 1: 0.7, 4: 0.75, 5: 0.9}  # each root's place in the gait's wave


def _gait_tips(u, stride_px, lift=5.0):
    """The roots through a gait at u (0..1 of a cycle): each tip grips the floor and moves back exactly as far as the
    body travels while it's down (so it stays put on the floor), then lifts and reaches forward again. The front roots
    pull in toward the hem as it glides over them, arching up like an inchworm; the back ones stretch out behind."""
    beta = STANCE / stride_px
    tips = {}
    for k, ph in WAVE.items():
        oz = -math.cos(ROOTS[k][0] * DEG)
        a = -STANCE * (oz + 1) / 2  # the front roots pull from where they lie; the back ones start drawn in
        q = (u - ph) % 1.0
        if q < beta:
            tips[k] = _rt(k, dz=a + STANCE * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            e = core.ease_io(f)
            tips[k] = _rt(k, up=ROOTS[k][2] / 2 + lift * math.sin(math.pi * f), phi=90 + 34 * math.sin(math.pi * f),
                          dz=a + STANCE * (1 - e))
    return tips


def _idle(r):
    """Standing in prayer: the chest breathes, the head bows a little lower and lifts, the censers sway on their chains,
    and the roots creep: each tip shifts and lifts a little on its own slow time."""
    L, n = 4.8, 24
    keys = []
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
        tips = {j: _rt(j, out=-1.2 + 1.2 * math.sin(2 * math.pi * u + j * 1.7), side=1.0 * math.cos(2 * math.pi * u + j * 2.3),
                       up=ROOTS[j][2] / 2 + max(0.0, 1.6 * math.sin(2 * math.pi * u + j * 2.9) - 0.6),
                       phi=90 + 10 * max(0.0, math.sin(2 * math.pi * u + j * 2.9))) for j in range(len(ROOTS))}
        pose = _pose(r, lean=0.8 * s, roll=0.4 * c, head=(1.5 * s + 1.0, 2.5 * math.sin(2 * math.pi * u * 2 + 0.7) * 0.5, 0), tips=tips,
                     extra=dict(_censers(3 * math.sin(2 * math.pi * u - 0.6), 3 * math.sin(2 * math.pi * u - 2.2), 1.0 * c),
                                **{"sun_disc": (0, 0, 0.8 * s)}))
        pose["chest"] = tuple(a + b for a, b in zip(pose["chest"], (-1.2 * c, 0, 0)))
        keys.append((L * u, pose))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """The glide: it pulls itself along on its roots, the front ones reaching out, gripping and drawing in as it passes
    over them, the hands kept in prayer; it leans into it, rocks a little with each pull, and the censers swing."""
    for name, L, stride, lean in (("walk", 1.6, 2.0, 3.0), ("run", 1.0, 3.0, 7.0)):
        sp = stride * 16
        keys = []
        n = 24
        for k in range(n + 1):
            u = k / n
            s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
            pose = _pose(r, lean=lean + 0.8 * c, yaw=2.0 * s, roll=1.2 * s, head=(-0.4 * lean, -1.5 * s, 0), tips=_gait_tips(u, sp),
                         extra=_censers(-3 - 7 * s, -3 - 7 * math.sin(2 * math.pi * u + 1.4), 2.0, tilt=0.6 * (lean + 0.8 * c)))
            keys.append((L * u, pose))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 2.0, 3.0
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 16
        for k in range(n + 1):
            u = k / n
            tips = {}
            for j in range(len(ROOTS)):  # half the roots step round the way it turns, then the other half
                q = (u - (0.5 if j % 2 else 0.0)) % 1.0
                if q < 0.5:
                    f = q / 0.5
                    tips[j] = _rt(j, side=sgn * 6 * (core.ease_io(f) - 0.5), up=ROOTS[j][2] / 2 + 3 * math.sin(math.pi * f),
                                  phi=90 + 20 * math.sin(math.pi * f))
                else:
                    tips[j] = _rt(j, side=sgn * 6 * (0.5 - (q - 0.5) / 0.5))
            keys.append((0.8 * u, _pose(r, yaw=sgn * 2.5 * math.sin(2 * math.pi * u) * 0.5 + sgn * 1.0, tips=tips)))
        keyed(r, nm, 0.8, keys, loop=True)


def _reactions(r):
    """It rocks back on its roots when struck (the sunburst shuddering, the censers swinging), slumps when its stance
    breaks (the hands falling from its prayer, its head hanging), and dies falling on its face like a felled statue."""
    jolt = _pose(r, lean=-6, head=(-9, 0, 0), swing=10, extra={"sun_disc": (0, 0, 5), "sun": (-3, 0, 0)},
                 tips={k: _rt(k, up=ROOTS[k][2] / 2 + 2, phi=110) for k in (0, 1, 7)})
    after = _pose(r, lean=2, head=(2, 0, 0), swing=-5, extra={"sun_disc": (0, 0, -3)})
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.07, jolt), (0.3, after), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it turns away to its right and tips away
        hit = _pose(r, lean=-3, yaw=-10 * sgn, roll=-5 * sgn, head=(-5, -14 * sgn, 4 * sgn), swing=6,
                    extra={"sun_disc": (0, 0, 6 * sgn)})
        back = _pose(r, lean=1, yaw=3 * sgn, roll=1.5 * sgn, head=(1, 4 * sgn, 0), extra={"sun_disc": (0, 0, -2 * sgn)})
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, hit), (0.32, back), (0.6, _pose(r))])
    # broken: the prayer falls apart, the arms hang, the head drops, the sunburst lists to one side (held while broken)
    hang = {"l": ((12, 21, -15), (0.15, 0.0, 1.0)), "r": ((-12, 21, -15), (-0.15, 0.0, 1.0))}
    slump = _pose(r, lean=30, knee=6, head=(26, 0, 8), arms=hang, extra={"sun": (-6, 0, 9), "sun_disc": (0, 0, 8)},
                  tips={k: _rt(k, out=-2) for k in range(len(ROOTS))})
    half = _pose(r, lean=14, knee=2, head=(12, 0, 3), arms={"l": ((9, 34, -14), (0.3, 0.3, 1.0)), "r": ((-9, 34, -14), (-0.3, 0.3, 1.0))},
                 extra={"sun": (-2, 0, 3)})
    keyed(r, "kneel", 0.6, [(0, _pose(r)), (0.22, half), (0.6, slump)])
    keyed(r, "rise", 0.8, [(0, slump), (0.4, half), (0.8, _pose(r))])
    _death(r, hang)


def _topple(theta):
    """The base turned theta degrees forward about the front edge of its hem, so the statue tips over on that edge."""
    t = theta * DEG
    return {"rot": (theta, 0, 0), "pos": (0, 12 * math.sin(t), -12 + 12 * math.cos(t))}


def _death(r, hang):
    """The clock of its prayer stops: a jolt, its hands fall from its chest and it sags; then the whole statue tips
    forward on the edge of its hem, slowly and then all at once, lands on its face with a bounce, and the sunburst breaks
    off its back and falls beside it; the roots it tore up from settle back on the floor."""
    stop = _pose(r, lean=-5, head=(-12, 0, 0), swing=8, extra={"sun_disc": (0, 0, 7)},
                 arms={"l": ((5, 50, -11), (0.4, 1, 0.5)), "r": ((-5, 50, -11), (-0.4, 1, 0.5))})
    sag = _pose(r, lean=16, head=(30, 0, 10), arms=hang, swing=-4, extra={"sun": (-3, 0, 4)},
                tips={k: _rt(k, up=ROOTS[k][2] / 2 + 2, phi=105) for k in range(len(ROOTS))})
    reach_out = {"l": ((9, 56, -26), (0.6, 0.2, 0.8)), "r": ((-9, 56, -26), (-0.6, 0.2, 0.8))}  # flung out ahead as it falls

    def falling(theta, f):  # theta: the tip forward; f: 0..1 how far the arms have flung out
        arms = {sd: (tuple(a + (b - a) * f for a, b in zip(hang[sd][0], reach_out[sd][0])),
                     tuple(a + (b - a) * f for a, b in zip(hang[sd][1], reach_out[sd][1]))) for sd in ("l", "r")}
        p = _pose(r, lean=16 - 10 * f, head=(30 - 34 * f, 0, 10 - 6 * f), arms=arms, swing=30 * f,
                  tips={k: _rt(k, up=ROOTS[k][2] / 2 + 6 * f * (1 if ROOTS[k][0] ** 2 < 60 ** 2 else 0.3), phi=90 + 50 * f)
                        for k in range(len(ROOTS))})
        p["base"] = _topple(theta)
        return p
    tip0, tip1, tip2 = falling(6, 0.1), falling(34, 0.45), falling(66, 0.8)
    down = falling(88, 1.0)
    bounce = falling(83, 1.0)
    down["sun"] = (10, 0, 12)
    bounce["sun"] = (16, 0, -8)
    rest = falling(88, 1.0)
    rest.update({"sun": (12, 0, 40), "sun_disc": (0, 0, 24), "head": (-6, 0, 6)})
    rest = roots(rest, {k: _rt(k, out=-1) for k in range(len(ROOTS))})
    keyed(r, "death", 3.2, [(0, _pose(r)), (0.35, stop), (0.95, sag), (1.35, tip0), (1.62, tip1), (1.8, tip2), (1.92, down),
                            (2.06, bounce), (2.22, down), (2.7, rest), (3.2, rest)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _hit(side):
    return [("hand_" + side, (0, 7, 0), 3), ("fingers_" + side, (0, 4, 0), 2.5), ("forearm_" + side, (0, 15, 0), 3.5)]


def _arm_ground(side):
    """Its hand stays out of the floor: the wrist first, then the elbow and the shoulder turn."""
    return [(["hand_" + side, "fingers_" + side], [("hand_" + side, 0, 40), ("forearm_" + side, 0, 40), ("arm_" + side, 0, 40)])]


def sweep(r, side="r", mid="sweep"):
    """A backhand sweep (LIGHT): the hand parts from its prayer and draws back across its chest to the other shoulder as
    the statue turns away; then it unwinds, bowing, and the straight arm sweeps flat across the front at a man's chest,
    from its far side out past its own."""
    sg = 1 if side == "r" else -1  # +1: the right hand, swept from its left to its right
    o = "l" if side == "r" else "r"
    pole = (-sg * 0.4, 0.5, 0.8)
    gather = _pose(r, lean=-2, yaw=16 * sg, head=(0, -10 * sg, 0), arms={side: ((sg * 8, 48, -12), pole)}, pray=(o,))
    peak = _pose(r, lean=-6, yaw=36 * sg, roll=-4 * sg, head=(-4, -26 * sg, 0), arms={side: ((sg * 17, 47, 2), pole)}, pray=(o,), swing=6,
                 tips={k: _rt(k, side=2 * sg) for k in (0, 1, 7)})
    low = (-sg * 0.3, -0.2, 0.9)
    across = _pose(r, lean=24, knee=3, yaw=10 * sg, head=(6, -4 * sg, 0), arms={side: ((sg * 8, 30, -33), low)}, pray=(o,), swing=-4)
    hit = _pose(r, lean=26, knee=4, yaw=-8 * sg, head=(6, 6 * sg, 0), arms={side: ((-sg * 14, 29, -33), low)}, pray=(o,), swing=-7)
    out = _pose(r, lean=24, knee=3, yaw=-28 * sg, head=(4, 16 * sg, 0), arms={side: ((-sg * 34, 31, -14), (-sg * 0.1, -0.2, 0.9))},
                pray=(o,), swing=-8, tips={k: _rt(k, side=-2 * sg) for k in (0, 1, 7)})
    back = _pose(r, lean=8, yaw=-10 * sg, arms={side: ((-sg * 14, 40, -16), (-sg * 0.5, 0.8, 0.4))}, pray=(o,))
    return Move(mid, "LIGHT", 11, 5, 14, track=8.0, rng=(0, 3.2), kinds=("combo",), damage=1.0, knock=0.7,
                hit=_hit(side), stages={"windup": [(0.6, gather), (1.0, peak)], "active": [(0.3, across), (0.6, hit), (1.0, out)], "recover": [(0.5, back)]},
                lag={"head": 0.05, "arm_" + side: 0, "forearm_" + side: 0, "hand_" + side: 0, "fingers_" + side: 0})


def palm(r):
    """Saint's Palm (HEAVY): its right hand leaves its prayer and climbs high over its head, palm out, the statue
    rearing back and the front roots lifting; it holds; then it folds over itself, the robe bending at its knee as if it
    knelt, and slams the palm flat onto the floor two blocks before it, the roots slamming down with it."""
    pole = (-0.6, 0.3, 0.75)
    up = ((0, -1, 0.2), (0, 0, 1))  # the fingers up, the palm forward
    down = ((0, 0.12, -1), (0, 1, 0))  # the fingers forward, the palm on the floor
    dip = _pose(r, lean=6, head=(6, 0, 0), arms={"r": ((-12, 62, -8), pole)}, hands={"r": up}, pray=("l",))
    peak = _pose(r, lean=-10, yaw=6, head=(-14, -4, 0), arms={"r": ((-6, 86, 2), pole)}, hands={"r": up}, pray=("l",), swing=6,
                 tips={k: _rt(k, out=-4, up=ROOTS[k][2] / 2 + 7, phi=140) for k in (0, 1, 7)})
    hit = _pose(r, lean=72, knee=18, yaw=-2, head=(6, 0, 0), arms={"r": ((-7, 1.6, -33), (-0.4, -0.2, 1.0))}, hands={"r": down},
                pray=("l",), swing=-14, tips={k: _rt(k, phi=84) for k in (0, 1, 7)})
    settle = _pose(r, lean=74, knee=19, yaw=-2, head=(8, 0, 0), arms={"r": ((-7, 1.6, -33), (-0.4, -0.2, 1.0))}, hands={"r": down},
                   pray=("l",), swing=-6, fingers={"r": -8})
    bounce = _pose(r, lean=56, knee=13, head=(6, 0, 0), arms={"r": ((-8, 10, -32), (-0.4, -0.1, 1.0))}, hands={"r": ((0, 0.4, -1), (0, 1, 0))},
                   pray=("l",), swing=8, fingers={"r": 10})
    lift = _pose(r, lean=26, knee=4, head=(4, 0, 0), arms={"r": ((-8, 32, -22), (-0.4, 0.5, 0.8))}, pray=("l",), swing=-3, fingers={"r": 20})
    return Move("palm", "HEAVY", 20, 6, 22, title="Saint's Palm", hold=8, track=4.0, ability="Saint's Palm", origin=(0, 0, 2.4),
                rng=(0, 4.5), kinds=("area", "near"), damage=1.3, knock=1.0, disc=2.5, disc_at="hit", hit=_hit("r"),
                stages={"windup": [(0.3, dip), (1.0, peak)], "active": [(0.45, hit), (1.0, settle)], "recover": [(0.45, bounce), (0.7, lift)]},
                lag={"head": 0.05, "arm_r": 0, "forearm_r": 0, "hand_r": 0, "fingers_r": 0}, ground=_arm_ground("r"), bite=0.45)


def lance(r):
    """Root Lance (HEAVY): the right hand draws up to its shoulder, fingers pointing at you, as its three front roots coil
    up off the floor; then it drives the hand down and out along the floor toward you and the roots stab into it: the
    lances rise in a line from there."""
    pole = (-0.7, 0.4, 0.6)
    point = ((0, 0.55, -1), (0, 1, 0))  # the fingers along the line, the palm down
    coil = lambda h, o, ph: {k: _rt(k, out=o, up=ROOTS[k][2] / 2 + h, phi=ph) for k in (0, 1, 7)}
    gather = _pose(r, lean=-3, yaw=6, head=(-2, -4, 0), arms={"r": ((-13, 60, -8), pole)}, hands={"r": point}, pray=("l",), tips=coil(7, -7, 140))
    peak = _pose(r, lean=-6, yaw=10, head=(4, -8, 0), arms={"r": ((-13, 64, -2), pole)}, hands={"r": point}, pray=("l",), swing=5,
                 tips=coil(12, -10, 168))
    hit = _pose(r, lean=28, yaw=-4, head=(14, 2, 0), arms={"r": ((-6, 24, -34), (-0.6, 0.2, 0.8))}, hands={"r": point}, pray=("l",),
                swing=-10, tips={k: _rt(k, up=1.0, phi=22) for k in (0, 1, 7)})
    end = _pose(r, lean=31, yaw=-4, head=(16, 2, 0), arms={"r": ((-6, 20, -36), (-0.6, 0.2, 0.8))}, hands={"r": point}, pray=("l",),
                swing=-6, tips={k: _rt(k, up=1.0, phi=20) for k in (0, 1, 7)})
    back = _pose(r, lean=10, arms={"r": ((-9, 40, -18), (-0.5, 0.8, 0.4))}, pray=("l",), tips={k: _rt(k, up=3, phi=110) for k in (0, 1, 7)})
    return Move("lance", "HEAVY", 16, 6, 16, title="Root Lance", track=8.0, ability="Root Lance", origin=(0, 0, 1.6), rng=(4, 18),
                kinds=("ranged",), damage=1.0, knock=0.4,
                stages={"windup": [(0.6, gather), (1.0, peak)], "active": [(0.4, hit), (1.0, end)], "recover": [(0.5, back)]},
                lag={"head": 0.05, "arm_r": 0, "forearm_r": 0, "hand_r": 0, "fingers_r": 0})


def grasp(r):
    """Root Grasp (GRAB): it reaches its left hand at you, palm out and fingers spread, its left shoulder turned to you
    and the front roots creeping out; then the hand closes into a fist and draws back to its chest, and the roots it
    reached with grip the floor."""
    open_ = ((0.05, 0.55, -1), (1, 0, 0))  # the fingers on at you, the palm down
    reach_ = _pose(r, lean=12, yaw=-10, head=(10, 8, 0), arms={"l": ((9, 40, -31), (0.7, 0.4, 0.5))}, hands={"l": open_}, fingers={"l": -20},
                   pray=("r",), tips={k: _rt(k, side=-1) for k in (0, 1, 7)})
    peak = _pose(r, lean=16, yaw=-14, head=(14, 12, 0), arms={"l": ((10, 37, -36), (0.7, 0.4, 0.5))}, hands={"l": open_}, fingers={"l": -34},
                 pray=("r",), swing=-4)
    clench = _pose(r, lean=14, yaw=-12, head=(12, 10, 0), arms={"l": ((9, 39, -33), (0.7, 0.4, 0.5))}, hands={"l": open_}, fingers={"l": 105},
                   pray=("r",), tips={k: _rt(k, up=1.0, phi=30) for k in (0, 1, 7)})
    pull = _pose(r, lean=4, yaw=-6, head=(2, 6, 0), arms={"l": ((7, 48, -20), (0.6, 0.6, 0.5))}, hands={"l": open_}, fingers={"l": 100},
                 pray=("r",), swing=6, tips={k: _rt(k, out=-6, up=1.0, phi=40) for k in (0, 1, 7)})
    back = _pose(r, lean=2, arms={"l": ((5, 50, -14), (0.5, 0.8, 0.4))}, fingers={"l": 40}, pray=("r",))
    return Move("grasp", "GRAB", 14, 8, 16, title="Root Grasp", track=8.0, lock=10, ability="Root Grasp", origin=(0, 0, 1.0), rng=(3, 12),
                kinds=("ranged",), damage=0.6, knock=0.0,
                stages={"windup": [(0.6, reach_), (1.0, peak)], "active": [(0.3, clench), (1.0, pull)], "recover": [(0.5, back)]},
                lag={"head": 0.05, "arm_l": 0, "forearm_l": 0, "hand_l": 0, "fingers_l": 0})


BACK = (3, 4, 5)


def lash(r):
    """For whoever is behind it (LIGHT): the hood turns to look back over its shoulder and the three roots behind it rear
    up, curling back over the hem; then they whip down onto the floor behind it, one a beat before the other two."""
    rear = lambda h, o, ph: {k: _rt(k, out=o, up=ROOTS[k][2] / 2 + h, phi=ph) for k in BACK}
    coil = _pose(r, lean=-2, yaw=-8, head=(-4, -40, 0), tips=rear(10, -6, 150))
    peak = _pose(r, lean=-4, yaw=-12, head=(-6, -55, 0), tips=rear(20, -9, 172), swing=-6)
    whip = _pose(r, lean=6, yaw=-4, head=(2, -50, 0), tips={4: _rt(4, up=2.0, phi=82), 3: _rt(3, out=-3, up=8, phi=120), 5: _rt(5, out=-3, up=8, phi=120)})
    slap = _pose(r, lean=8, yaw=-2, head=(4, -45, 0), tips={k: _rt(k, up=2.0 if k != 4 else 1.6, phi=84) for k in BACK}, swing=6)
    back = _pose(r, lean=2, head=(0, -20, 0), tips={k: _rt(k, up=4, phi=110) for k in BACK})
    return Move("lash", "LIGHT", 10, 4, 14, track=0.0, rng=(0, 3.0), kinds=("behind",), damage=0.9, knock=1.0,
                hit=[(f"root{k}c", (0, ROOTS[k][1][2], 0), 2.5) for k in BACK] + [(f"root{k}b", (0, ROOTS[k][1][1], 0), 2.5) for k in BACK],
                stages={"windup": [(0.6, coil), (1.0, peak)], "active": [(0.5, whip), (1.0, slap)], "recover": [(0.5, back)]},
                lag={"head": 0.04})


def ring(r):
    """Root Ring (HEAVY): it raises its joined hands before its face and then high over its head, rearing back, as every
    root rears up off the floor round it; it holds; then it drives the joined hands down to its chest and bows, and the
    roots plunge into the floor all round it."""
    pray_at = lambda h, z: {"l": ((1.4, h, z), (0.2, 0.3, 1.0)), "r": ((-1.4, h, z), (-0.2, 0.3, 1.0))}
    joined = lambda up: {"l": (up, (1, 0, 0)), "r": (up, (1, 0, 0))}
    rear = lambda h, o, ph: {k: _rt(k, out=o, up=ROOTS[k][2] / 2 + h, phi=ph) for k in range(len(ROOTS))}
    raise_ = _pose(r, lean=-2, head=(-6, 0, 0), arms=pray_at(64, -13), hands=joined((0, -1, -0.15)), tips=rear(6, -4, 130))
    peak = _pose(r, lean=-8, head=(-16, 0, 0), arms=pray_at(86, -5), hands=joined((0, -1, 0.05)), tips=rear(15, -8, 166), swing=8)
    plunge = _pose(r, lean=24, head=(18, 0, 0), arms=pray_at(48, -15), hands=joined((0, -1, -0.5)), swing=-14,
                   tips={k: _rt(k, out=2, up=1.0, phi=42) for k in range(len(ROOTS))})
    held = _pose(r, lean=27, head=(20, 0, 0), arms=pray_at(47, -15), hands=joined((0, -1, -0.55)), swing=-6,
                 tips={k: _rt(k, out=2, up=1.0, phi=40) for k in range(len(ROOTS))})
    lift = _pose(r, lean=8, head=(6, 0, 0), tips={k: _rt(k, up=3, phi=105) for k in range(len(ROOTS))})
    return Move("ring", "HEAVY", 18, 6, 18, title="Root Ring", hold=8, track=0.0, ability="Root Ring", rng=(0, 6), kinds=("area", "behind"),
                damage=1.0, knock=0.8,
                stages={"windup": [(0.6, raise_), (1.0, peak)], "active": [(0.4, plunge), (1.0, held)], "recover": [(0.45, lift)]},
                lag={"head": 0.05, **{b + s: 0 for b in ("arm_", "forearm_", "hand_", "fingers_") for s in "lr"}})


def grow(r):
    """Grow Creakers: the hands part and sink to its sides palms down, fingers spread over the floor, the statue bowing
    and its roots pressing flat; a pulse runs down it; then it lifts its hands slowly, palms up, as the creakers grow."""
    low = lambda h, x, z: {"l": ((x, h, z), (0.6, 0.6, 0.6)), "r": ((-x, h, z), (-0.6, 0.6, 0.6))}
    pd = {"l": ((0, 0.2, -1), (0, 1, 0)), "r": ((0, 0.2, -1), (0, -1, 0))}  # palms down, fingers ahead
    pu = {"l": ((0, 0.1, -1), (0, -1, 0)), "r": ((0, 0.1, -1), (0, 1, 0))}  # palms up
    press = {k: _rt(k, up=1.0, phi=70) for k in range(len(ROOTS))}
    part = _pose(r, lean=10, head=(10, 0, 0), arms=low(34, 13, -14), hands=pd, fingers={"l": -10, "r": -10})
    sink = _pose(r, lean=22, knee=4, head=(18, 0, 0), arms=low(24, 16, -16), hands=pd, fingers={"l": -25, "r": -25}, tips=press, swing=-6)
    pulse = _pose(r, lean=25, knee=5, head=(20, 0, 0), arms=low(20, 17, -16), hands=pd, fingers={"l": -10, "r": -10},
                  tips={k: _rt(k, up=3.5, phi=110) for k in range(len(ROOTS))})
    rising = _pose(r, lean=12, knee=2, head=(4, 0, 0), arms=low(34, 18, -15), hands=pu, fingers={"l": -15, "r": -15}, tips=press)
    up = _pose(r, lean=2, head=(-8, 0, 0), arms=low(46, 16, -13), hands=pu, fingers={"l": -20, "r": -20}, swing=4)
    return Move("grow", "NONE", 14, 22, 16, title="Grow Creakers", track=0.0, ability="Grow Creakers", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0,
                stages={"windup": [(0.5, part), (1.0, sink)], "active": [(0.3, pulse), (0.65, rising), (1.0, up)], "recover": [(0.5, rising)]},
                lag={"head": 0.05})


def halo(r):
    """Thorn Halo (PERILOUS, the signature): its hands part and its arms open wide and up, palms to the sky, the statue
    arching back with its face raised and its roots gripping the floor, while the sunburst behind it starts to turn; then
    the arms throw wider, the sunburst spins a whole turn and the thorns fly off it."""
    wide = lambda x, h, z: {"l": ((x, h, z), (0.3, 1.0, 0.6)), "r": ((-x, h, z), (-0.3, 1.0, 0.6))}
    pu = {"l": ((0.4, -1, -0.2), (0, -0.2, -1)), "r": ((-0.4, -1, -0.2), (0, -0.2, -1))}
    grip = {k: _rt(k, up=1.0, phi=72) for k in range(len(ROOTS))}
    part = _pose(r, lean=-3, head=(-8, 0, 0), arms=wide(16, 52, -12), hands=pu, fingers={"l": -10, "r": -10})
    peak = _pose(r, lean=-12, head=(-24, 0, 0), arms=wide(30, 74, -4), hands=pu, fingers={"l": -25, "r": -25}, tips=grip, swing=8)
    burst = _pose(r, lean=-4, head=(-18, 0, 0), arms=wide(35, 70, 2), hands=pu, fingers={"l": -35, "r": -35}, tips=grip, swing=-6)
    held = _pose(r, lean=-6, head=(-20, 0, 0), arms=wide(33, 69, 0), hands=pu, fingers={"l": -30, "r": -30}, tips=grip)
    fold = _pose(r, lean=4, head=(2, 0, 0), arms=wide(14, 52, -14))
    mv = Move("halo", "PERILOUS", 20, 10, 18, title="Thorn Halo", track=0.0, ability="Thorn Halo", origin=(0, 4.25, -0.6), rng=(0, 14),
              kinds=("area",), signature=True, damage=1.0, knock=0.6,
              stages={"windup": [(0.6, part), (1.0, peak)], "active": [(0.3, burst), (1.0, held)], "recover": [(0.5, fold)]},
              lag={"head": 0.05})
    return mv


def haul(r):
    """It hauls itself at you (LIGHT, a gap closer): its front roots coil and then shoot out flat ahead of it, and it
    slides five blocks over them as they reel it in, the right palm driving out at you at the end."""
    pole = (-0.6, 0.6, 0.6)
    shove = ((0, 0.6, -1), (0, 1, 0))  # the fingers on at you, the palm down
    front = (0, 1, 7, 2, 6)
    coil = _pose(r, lean=8, yaw=8, head=(6, -6, 0), arms={"r": ((-12, 38, 2), pole)}, pray=("l",),
                 tips={k: _rt(k, out=-8, up=ROOTS[k][2] / 2 + 7, phi=150) for k in front})
    shoot = _pose(r, lean=12, yaw=6, head=(4, -4, 0), arms={"r": ((-12, 38, 2), pole)}, pray=("l",), swing=10,
                  tips={k: _rt(k, phi=88) for k in front})
    slide = _pose(r, lean=16, yaw=-2, head=(4, 0, 0), arms={"r": ((-10, 40, -18), pole)}, hands={"r": shove}, pray=("l",), swing=14,
                  tips={k: _rt(k, dz=STANCE, phi=95) for k in front})
    hit = _pose(r, lean=22, knee=2, yaw=-8, head=(8, 4, 0), arms={"r": ((-6, 30, -34), (-0.6, 0.3, 0.7))}, hands={"r": shove}, pray=("l",),
                swing=4, tips={k: _rt(k, dz=STANCE, phi=95) for k in front})
    back = _pose(r, lean=6, arms={"r": ((-9, 44, -18), (-0.5, 0.8, 0.4))}, pray=("l",))
    return Move("haul", "LIGHT", 14, 10, 16, track=6.0, lock=10, advance=5.0, rng=(5, 14), kinds=("gap",), damage=1.0, knock=1.2,
                hit=_hit("r"), stages={"windup": [(0.6, coil), (1.0, coil)], "active": [(0.2, shoot), (0.7, slide), (1.0, hit)], "recover": [(0.5, back)]},
                lag={"head": 0.05, "arm_r": 0, "forearm_r": 0, "hand_r": 0, "fingers_r": 0})


def wake(r):
    """Its intro: deep in prayer with its head bowed low, it lifts its head and opens its eyes; the roots stir; its hands
    part slowly and open, palms up, toward you; then they close again in prayer."""
    bowed = _pose(r, lean=6, head=(22, 0, 0))
    stir = _pose(r, lean=3, head=(6, 0, 0), tips={k: _rt(k, up=ROOTS[k][2] / 2 + (2.5 if k % 2 else 0.5), phi=100 + (20 if k % 2 else 0))
                                                   for k in range(len(ROOTS))})
    pu = {"l": ((0, 0.1, -1), (0, -1, 0)), "r": ((0, 0.1, -1), (0, 1, 0))}
    opened = _pose(r, lean=-2, head=(-4, 0, 0), arms={"l": ((14, 46, -16), (0.6, 0.8, 0.4)), "r": ((-14, 46, -16), (-0.6, 0.8, 0.4))}, hands=pu,
                   fingers={"l": -15, "r": -15}, extra={"sun": (-3, 0, 0)})
    held = _pose(r, lean=-6, head=(-11, 0, 0), arms={"l": ((19, 54, -15), (0.6, 0.8, 0.4)), "r": ((-19, 54, -15), (-0.6, 0.8, 0.4))}, hands=pu,
                 fingers={"l": -32, "r": -32}, extra={"sun": (-7, 0, 0), "sun_disc": (0, 0, 6)})
    return Move("wake", "NONE", 10, 32, 14, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, bowed)], "active": [(0.3, stir), (0.65, opened), (1.0, held)], "recover": [(0.5, opened)]})


# the ground (SPEC §35.10): the roots' tips, the censers and the scapular stay out of the floor; the hands where they reach
ROOT_GROUND = [([f"root{k}c"], [(f"root{k}b", 0, 40), (f"root{k}a", 0, 30)]) for k in range(len(ROOTS))]
HANG_GROUND = [(["censer_" + s], [("chain_" + s + "0", 0, 80, -1)]) for s in "lr"] + [(["scap2"], [("scap1", 0, 60, -1), ("scap2", 0, 60, -1)])]
HAND_GROUND = [(["hand_" + s, "fingers_" + s, "forearm_" + s], [("hand_" + s, 0, 40), ("forearm_" + s, 0, 40), ("arm_" + s, 0, 40)]) for s in "lr"]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [sweep(r), sweep(r, "l", "sweep_b"), palm(r), lance(r), grasp(r), lash(r), ring(r), grow(r), halo(r), haul(r), wake(r)]
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    # the sunburst turns a whole turn through the Thorn Halo (a whole turn is no turn: it hands back as it was)
    W, A = by["halo"].seconds("windup"), by["halo"].seconds("active")
    _turn(r, by["halo"], "sun_disc", 2, -1, 0.45 * W, W + A)
    own = {core.clip_name(m) for m in mv if m.ground}
    core.ground_pass(r, ROOT_GROUND, clips=[n for n in r.clips if n != "death"])
    core.ground_pass(r, HANG_GROUND, clips=[n for n in r.clips if n != "death"])
    core.ground_pass(r, HAND_GROUND, clips=[n for n in r.clips if n not in own and n != "death"])
    core.ground_pass(r, HAND_GROUND + HANG_GROUND, clips=["death"], skip=())
    return mv


def _turn(r, mv, bone, axis, turns, t0, t1):
    """Whole turns on a bone in a move's clip, after staging: from none at t0 to all of them at t1, eased."""
    c = r.clips[core.clip_name(mv)]
    old = c.tracks.get((bone, "rot"))
    keys = []
    for k in range(int(round(c.length / core.TICK)) + 1):
        t = min(c.length, k * core.TICK)
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        f = 0.0 if t <= t0 else 1.0 if t >= t1 else core.ease_io((t - t0) / (t1 - t0))
        base[axis] += 360.0 * turns * f
        keys.append((t, tuple(base), "cat"))
    c.tracks[(bone, "rot")] = keys


# Its sounds (SPEC §35.8): stone grinding on stone, roots and wood creaking, a bell-like chime of the censers; chosen by
# ear on paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("block.creaking_heart.hurt", 1.2, 0.8), ("block.chain.step", 0.8, 0.7)],
    "tell_heavy": [("block.grindstone.use", 1.6, 0.5), ("block.creaking_heart.idle", 1.6, 0.7)],
    "tell_perilous": [("block.beacon.activate", 2.0, 0.6), ("block.amethyst_block.resonate", 1.6, 0.6), ("entity.creaking.activate", 1.8, 0.6)],
    "tell_grab": [("entity.creaking.activate", 1.8, 0.8), ("block.rooted_dirt.break", 1.6, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.6), ("block.mangrove_roots.break", 1.2, 0.8)],
    "swing_heavy": [("entity.wind_charge.wind_burst", 1.4, 0.5), ("block.deepslate.break", 1.6, 0.5)],
    "cast": [("block.creaking_heart.spawn", 1.6, 0.8), ("block.amethyst_block.chime", 1.2, 0.7)],
    "impact": [("block.deepslate_bricks.break", 2.0, 0.5), ("item.mace.smash_ground_heavy", 1.8, 0.6), ("block.rooted_dirt.break", 2.0, 0.6)],
    "roar": [("entity.creaking.activate", 2.4, 0.5), ("block.bell.resonate", 1.2, 0.5)],
    "step": [("block.mangrove_roots.step", 1.4, 0.6), ("block.rooted_dirt.step", 1.2, 0.6)],
}
MOVE_SOUNDS = {"wake": {"swing": "roar"}, "grow": {"swing": "cast"}, "halo": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "sweep", "weight": 3, "when": "near", "then": [["sweep_b"], ["sweep_b", "palm"]]},
            {"move": "palm", "weight": 3},
            {"move": "lance", "weight": 3},
            {"move": "lash", "weight": 3, "when": "behind"},
            {"move": "haul", "weight": 2, "when": "far", "then": [["sweep"]]},
        ]},
        {"transition": "grow", "pool": [
            {"move": "sweep", "weight": 3, "when": "near", "then": [["sweep_b"], ["sweep_b", "palm"]]},
            {"move": "palm", "weight": 2},
            {"move": "lance", "weight": 3},
            {"move": "grasp", "weight": 2, "then": [["palm"]]},
            {"move": "ring", "weight": 2},
            {"move": "lash", "weight": 3, "when": "behind"},
            {"move": "haul", "weight": 2, "when": "far", "then": [["sweep"]]},
            {"move": "grow", "weight": 1},
        ]},
        {"transition": "halo", "pool": [
            {"move": "sweep", "weight": 3, "when": "near", "then": [["sweep_b", "palm"]]},
            {"move": "palm", "weight": 2, "then": [["ring"]]},
            {"move": "lance", "weight": 3, "then": [["lance"]]},
            {"move": "grasp", "weight": 2, "then": [["palm"]]},
            {"move": "ring", "weight": 2},
            {"move": "lash", "weight": 3, "when": "behind"},
            {"move": "haul", "weight": 2, "when": "far", "then": [["grasp"]]},
            {"move": "halo", "weight": 2},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": C, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.9, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
