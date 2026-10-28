"""The Creature Kit's core (SPEC §34.2): pose-keyed clips, forward kinematics that match the game, move tables.

A kit body is a fauna_rig.Rig built by a body plan (plans.py) with standard bone names. Moves (moves.py) are authored as
key poses per stage (wind-up, active, recovery) in normalised time; this module turns them into clips with overlap
(children lag their parents, so a swing drags through the arm and settles after the body), samples the striking bone's
path through the active ticks as capsules in body space, and writes the move table the server reads.

Coordinates. Model space is vanilla's: pixels, y down, the ground at y = 24, the body facing -z. Body space (the move
table, the server) is blocks from the feet: +x the body's left, +y up, +z forward. The renderer's flip and turn give
body = (x / 16, (24 - y) / 16, -z / 16).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SPECS = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, SPECS)
sys.path.insert(0, os.path.abspath(os.path.join(SPECS, "..")))
import fauna_rig as R  # noqa: E402

DEG = math.pi / 180.0
TICK = 1.0 / 20.0
MIN_WINDUP = {"LIGHT": 8, "HEAVY": 12, "GRAB": 12, "PERILOUS": 14, "NONE": 1}
MAX_HOLD = 12
FACE_BUDGET = 1200
OPTIONAL = {"jaw"}  # library poses may move these; a body without them just leaves them out


# ---------------------------------------------------------------------------------------------------------------------
# Easing and pose keys

def ease_out(t):
    return 1 - (1 - t) ** 3


def ease_in(t):
    return t ** 3


def ease_io(t):
    return 3 * t * t - 2 * t * t * t


class Move:
    """One move of a kit body: its clips' key poses and its move-table row.

    stages: {"windup": [(f, pose)], "active": [...], "recover": [...]} where f is 0..1 through the stage and a pose maps a
    bone to (rx, ry, rz) degrees or {"rot": (...), "pos": (...)} (pos in posVec units: +y up). Missing bones are at rest.
    hit: [(bone, tip_px, radius_px)]: the striking bones, each with the far end of what strikes in that bone's space.
    """

    def __init__(self, mid, cls, windup, active, recover, title="", hold=0, track=6.0, lock=None, advance=0.0, rise=0.0,
                 damage=1.0, knock=0.5, ability=None, origin=None, rng=(0.0, 4.0), kinds=(), signature=False, hit=(),
                 stages=None, lag=None, plant=("l", "r"), disc=0.0):
        self.id, self.cls, self.title = mid, cls, title
        self.windup, self.active, self.recover, self.hold = int(windup), int(active), int(recover), int(hold)
        self.track = track
        self.lock = int(lock if lock is not None else max(0, self.windup - max(4, self.windup // 3)))
        self.advance, self.rise, self.damage, self.knock = advance, rise, damage, knock
        self.ability, self.origin = ability, origin
        self.range, self.kinds, self.signature = list(rng), list(kinds), signature
        self.hit = list(hit)
        self.stages = stages or {}
        self.lag = lag or {}
        self.plant = plant  # feet that stay put through the move (checked by the lab)
        self.disc = disc  # a shockwave on the ground round it from contact on: radius in blocks (0: none)
        self.capsules = []

    def seconds(self, stage):
        return {"windup": self.windup, "active": self.active, "recover": self.recover}[stage] * TICK


def keyed(rig, name, length, keys, loop=False, lag=None, interp="cat"):
    """A clip from key poses [(seconds, pose)]. Each bone animates through every key (rest where a key leaves it out).
    lag: {bone: seconds}: that bone's inner keys come later (follow-through); the first and last keys stay put."""
    clip = rig.clip(name, length, loop)
    lag = lag or {}
    bones = []
    for _, pose in keys:
        for b in pose:
            if b not in bones:
                bones.append(b)
    bones = [b for b in bones if b in rig.by_name or b not in OPTIONAL]
    for b in bones:
        if b not in rig.by_name:
            raise ValueError(f"{rig.id}/{name}: no bone {b}")
        rot, pos = [], []
        any_pos = any(isinstance(p.get(b), dict) and "pos" in p.get(b) for _, p in keys)
        n = len(keys)
        for k, (t, pose) in enumerate(keys):
            v = pose.get(b, (0, 0, 0))
            r = v.get("rot", (0, 0, 0)) if isinstance(v, dict) else v
            q = v.get("pos", (0, 0, 0)) if isinstance(v, dict) else (0, 0, 0)
            tt = t
            if 0 < k < n - 1 and b in lag:
                tt = min(length - 1e-3, t + lag[b])
            rot.append((tt, r[0], r[1], r[2]))
            if any_pos:
                pos.append((tt, q[0], q[1], q[2]))
        rot.sort(key=lambda e: e[0])
        clip.rot(b, rot, interp)
        if any_pos:
            pos.sort(key=lambda e: e[0])
            clip.pos(b, pos, interp)
    return clip


def stage_clips(rig, mv, rest=None):
    """The move's three clips. The wind-up starts at rest and ends on its peak; the active starts on that peak; the
    recovery starts where the active ends and comes back to rest, so the stages chain without a pop. Every key pose
    gets its planted feet solved (plant), so a body that crouches or twists keeps its feet where they stood."""
    rest = rest or {}
    sides = mv.plant if (mv.advance == 0 and mv.rise == 0) else ()
    size = getattr(rig, "size", 1.0)

    def fit(p):  # the library's crouch depths are for a 36 px leg: a shorter body drops less
        if size != 1.0 and isinstance(p.get("pelvis"), dict):
            p = dict(p)
            q = p["pelvis"]
            p["pelvis"] = {"rot": q.get("rot", (0, 0, 0)), "pos": tuple(v * size for v in q.get("pos", (0, 0, 0)))}
        return plant(rig, p, sides)
    w = [(f, fit(p)) for f, p in mv.stages.get("windup", [])]
    a = [(f, fit(p)) for f, p in mv.stages.get("active", [])]
    r = [(f, fit(p)) for f, p in mv.stages.get("recover", [])]
    peak = w[-1][1] if w else rest
    end = a[-1][1] if a else peak
    wl, al, rl = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    keyed(rig, mv.id + "_windup", wl, [(0, rest)] + [(f * wl, p) for f, p in w if f > 0], lag=mv.lag)
    keyed(rig, mv.id + "_active", al, [(0, peak)] + [(f * al, p) for f, p in a if f > 0], lag={k: v * 0.4 for k, v in mv.lag.items()})
    keyed(rig, mv.id + "_recover", rl, [(0, end)] + [(f * rl, p) for f, p in r if 0 < f < 1] + [(rl, rest)], lag=mv.lag)


def planted(rig, keys, sides=("l", "r")):
    """Key poses [(t, pose)] with their feet planted (for clips written with keyed directly: idle, hurt)."""
    return [(t, plant(rig, p, sides)) for t, p in keys]


def as_sample(pose):
    """A key pose (degrees, pos +y up) as a sampled pose (radians, pos +y down), for frames()."""
    out = {}
    for b, v in pose.items():
        r = v.get("rot", (0, 0, 0)) if isinstance(v, dict) else v
        q = v.get("pos", (0, 0, 0)) if isinstance(v, dict) else (0, 0, 0)
        out[b] = {"rot": tuple(c * DEG for c in r), "pos": (q[0], -q[1], q[2]), "scale": (1, 1, 1)}
    return out


def _t(m):
    return tuple(tuple(m[j][i] for j in range(3)) for i in range(3))


def _euler(m):
    """ZYX Euler angles (rx, ry, rz) of M = Rz·Ry·Rx, the order ModelPart rotates in."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    return math.atan2(m[2][1], m[2][2]), b, math.atan2(m[1][0], m[0][0])


def plant(rig, pose, sides=("l", "r")):
    """The pose with each named foot back where it stands at rest: the thigh's pitch and roll and the knee's bend come
    from two-bone IK in the hip's frame, and the foot counter-rotates to stay flat. The leg bones a pose names for those
    feet are replaced; everything else is kept."""
    if not sides or not any(("thigh_" + s) in rig.by_name for s in sides):
        return pose
    rest_fr = frames(rig, {})
    fr = frames(rig, as_sample(pose))
    out = dict(pose)
    for side in sides:
        th, sh, ft = rig.by_name.get("thigh_" + side), rig.by_name.get("shin_" + side), rig.by_name.get("foot_" + side)
        if th is None or sh is None or ft is None:
            continue
        Mp, Tp = fr[th.parent.name]
        hip = tuple(Tp[i] + R._mv(Mp, th.pivot)[i] for i in range(3))
        target = rest_fr[ft.name][1]
        d = R._mv(_t(Mp), tuple(target[i] - hip[i] for i in range(3)))
        a, b = sh.pivot[1], ft.pivot[1]
        L = min(math.sqrt(sum(c * c for c in d)), a + b - 1e-3)
        k = math.acos(max(-1.0, min(1.0, (L * L - a * a - b * b) / (2 * a * b))))
        y0, z0 = a + b * math.cos(k), b * math.sin(k)
        y1 = math.sqrt(d[0] * d[0] + d[1] * d[1])
        rz = math.atan2(-d[0], d[1])
        rx = math.atan2(d[2], y1) - math.atan2(z0, y0)
        Mleg = R._mm(R._mm(Mp, R._rot(rx, 0, rz)), R._rot(k, 0, 0))
        fx, fy, fz = _euler(R._mm(_t(Mleg), rest_fr[ft.name][0]))
        out[th.name] = (rx / DEG, 0.0, rz / DEG)
        out[sh.name] = (k / DEG, 0.0, 0.0)
        out[ft.name] = (fx / DEG, fy / DEG, fz / DEG)
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Forward kinematics (ModelPart.translateAndRotate: translate(pivot), rotate Z·Y·X, scale)

def frames(rig, pose):
    """{bone: (M, T)}: each bone's rotation and its pivot in model pixels for a sampled pose (fauna_rig.Rig.sample)."""
    out = {}

    def walk(b, M, T):
        p = pose.get(b.name, {})
        dp = p.get("pos", (0, 0, 0))
        dr = p.get("rot", (0, 0, 0))
        ds = p.get("scale", (1, 1, 1))
        piv = (b.pivot[0] + dp[0], b.pivot[1] + dp[1], b.pivot[2] + dp[2])
        T2 = tuple(T[i] + R._mv(M, piv)[i] for i in range(3))
        rot = (b.rot[0] * DEG + dr[0], b.rot[1] * DEG + dr[1], b.rot[2] * DEG + dr[2])
        M2 = R._mm(R._mm(M, R._rot(*rot)), ((ds[0], 0, 0), (0, ds[1], 0), (0, 0, ds[2])))
        out[b.name] = (M2, T2)
        for ch in b.children:
            walk(ch, M2, T2)
    for b in rig.roots():
        walk(b, ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0))
    return out


def point(fr, bone, local):
    M, T = fr[bone]
    q = R._mv(M, local)
    return (q[0] + T[0], q[1] + T[1], q[2] + T[2])


def body(p):
    """Model pixels to body-space blocks."""
    return (p[0] / 16.0, (24.0 - p[1]) / 16.0, -p[2] / 16.0)


def sample(rig, clip, t):
    return rig.sample([(clip, t, 1.0)])


def capsules(rig, mv):
    """Per active tick, the striking bones as capsules (its pivot to its tip), plus the tip's sweep since the last tick."""
    out = []
    clip = mv.id + "_active"
    length = rig.clips[clip].length
    prev = None
    for k in range(mv.active):
        t = (k + 0.5) / mv.active * length
        fr = frames(rig, sample(rig, clip, t))
        caps, tips = [], []
        for bone, tip, radius in mv.hit:
            a = body(point(fr, bone, (0, 0, 0)))
            b = body(point(fr, bone, tip))
            r = radius / 16.0 + 0.15
            caps.append([round(v, 3) for v in a + b] + [round(r, 3)])
            tips.append((b, r))
        if prev:
            for (b0, r0), (b1, r1) in zip(prev, tips):
                caps.append([round(v, 3) for v in b0 + b1] + [round(max(r0, r1), 3)])
        prev = tips
        if mv.disc and k >= mv.active // 2:
            # the shockwave: spokes along the ground from its middle, close enough that nothing stands between them
            n = 12
            for i in range(n):
                a = 2 * math.pi * i / n
                caps.append([0.0, 0.3, 0.0, round(math.sin(a) * mv.disc, 3), 0.3, round(math.cos(a) * mv.disc, 3), 0.55])
        out.append(caps)
    return out


def reach(mv, top=1.9):
    """How far its blows reach from the body's middle across the ground (blocks), root motion included; only the parts
    low enough to meet someone standing (under {@code top} blocks) count."""
    best = 0.0
    for caps in mv.capsules:
        for c in caps:
            for x, y, z in ((c[0], c[1], c[2]), (c[3], c[4], c[5])):
                if y - c[6] <= top:
                    best = max(best, math.hypot(x, z) + c[6])
    return best + mv.advance


def fit_range(mv, half):
    """A blow with nothing else to reach with (no ability, not a gap closer) is only picked from where it can land."""
    if not mv.capsules or mv.ability or "gap" in mv.kinds:
        return
    lo = mv.range[0]
    mv.range[1] = round(min(mv.range[1], max(lo + 0.5, reach(mv) - half - 0.1)), 2)


# ---------------------------------------------------------------------------------------------------------------------
# The lab's checks (SPEC §34.5): they stop the build

def faces(rig):
    n = 0
    for c in rig.cubes:
        dims = (c.w, c.h, c.d)
        n += 2 if 0 in dims else 6
    return n


def pose_distance(rig, pose):
    """Mean of each moved bone's largest angle (degrees) against rest: how far a pose is from standing still."""
    vals = []
    for b, e in pose.items():
        vals.append(max(abs(v) for v in e["rot"]) / DEG)
    return sum(vals) / len(vals) if vals else 0.0


def foot_slide(rig, clip, side, samples=10):
    """How far (blocks) the foot drifts across the clip on the ground plane."""
    bone = "foot_" + side
    if bone not in rig.by_name or clip not in rig.clips:
        return 0.0
    length = rig.clips[clip].length
    pts = []
    for k in range(samples + 1):
        fr = frames(rig, sample(rig, clip, length * k / samples))
        p = body(point(fr, bone, (0, 0, 0)))
        pts.append((p[0], p[2]))
    x0, z0 = pts[0]
    return max(math.hypot(x - x0, z - z0) for x, z in pts)


def floating(rig):
    """Bones whose cubes sit more than 1 px away from their parent's cubes at rest (a part off the body)."""
    fr = frames(rig, {})
    boxes = {}
    for b in rig.bones:
        bs = []
        for c in b.cubes:
            corners = [point(fr, b.name, (c.x + dx * c.w, c.y + dy * c.h, c.z + dz * c.d))
                       for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            bs.append(tuple(min(p[i] for p in corners) for i in range(3)) + tuple(max(p[i] for p in corners) for i in range(3)))
        boxes[b.name] = bs
    bad = []
    for b in rig.bones:
        if b.parent is None or not boxes[b.name] or not boxes[b.parent.name]:
            continue
        gap = min(_box_gap(x, y) for x in boxes[b.name] for y in boxes[b.parent.name])
        if gap > 1.0:
            bad.append(f"{b.name} {gap:.1f}px off {b.parent.name}")
    return bad


def _box_gap(a, b):
    d = [max(0.0, max(a[i], b[i]) - min(a[i + 3], b[i + 3])) for i in range(3)]
    return math.sqrt(sum(v * v for v in d))


def check(rig, moves):
    problems = list(rig.check_kit()) if hasattr(rig, "check_kit") else []
    for c in rig.clips.values():
        for (bone, _), keys in c.tracks.items():
            if bone not in rig.by_name:
                problems.append(f"{rig.id}/{c.name}: no bone {bone}")
    if faces(rig) > FACE_BUDGET:
        problems.append(f"{rig.id}: {faces(rig)} faces, over the {FACE_BUDGET} budget")
    problems += [f"{rig.id}: {s}" for s in floating(rig)]
    for mv in moves:
        p = f"{rig.id}/{mv.id}"
        if mv.cls != "NONE" and mv.windup < MIN_WINDUP[mv.cls]:
            problems.append(f"{p}: a {mv.cls} wind-up of {mv.windup} ticks (at least {MIN_WINDUP[mv.cls]})")
        if mv.hold and (mv.cls != "HEAVY" or mv.hold > MAX_HOLD):
            problems.append(f"{p}: a hold only on HEAVY moves, at most {MAX_HOLD} ticks")
        if mv.cls not in ("NONE",) and mv.hit:
            peak = sample(rig, mv.id + "_windup", rig.clips[mv.id + "_windup"].length)
            dist = pose_distance(rig, peak)
            if dist < 12:
                problems.append(f"{p}: its wind-up peak is only {dist:.1f} degrees from rest (unreadable)")
        if mv.advance == 0 and mv.rise == 0:
            for side in mv.plant:
                for stage in ("_windup", "_active", "_recover"):
                    s = foot_slide(rig, mv.id + stage, side)
                    if s > 0.1:
                        problems.append(f"{p}{stage}: the {side} foot slides {s:.2f} blocks while planted")
    return problems


# ---------------------------------------------------------------------------------------------------------------------
# Writing

def rig_text(rig):
    return "\n".join(rig.java_lines()) + "\n"


def move_row(mv):
    row = {"id": mv.id, "class": mv.cls,
           "time": {"windup": mv.windup, "active": mv.active, "recover": mv.recover, "track": mv.track, "lock": mv.lock},
           "range": [round(v, 2) for v in mv.range], "damage": mv.damage, "knock": mv.knock}
    if mv.title:
        row["title"] = mv.title
    if mv.hold:
        row["time"]["hold"] = mv.hold
    if mv.signature:
        row["signature"] = True
    if mv.kinds:
        row["kinds"] = mv.kinds
    if mv.advance or mv.rise:
        row["motion"] = {"advance": round(mv.advance, 3), "rise": round(mv.rise, 3)}
    if mv.capsules:
        row["hits"] = mv.capsules
    if mv.ability:
        row["ability"] = mv.ability
    if mv.origin is not None:
        row["origin"] = [round(v, 3) for v in mv.origin]
    return row


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1, sort_keys=False)
        f.write("\n")
