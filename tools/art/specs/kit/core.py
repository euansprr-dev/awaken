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
                 stages=None, lag=None, plant=("l", "r"), disc=0.0, ground=None, bite=None, disc_at=None, edge=None):
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
        self.disc_at = disc_at  # the shockwave's middle: None round the body, "hit" under the blow's first hit
        self.edge = edge  # (weapon bone, the blade's middle, its edge) in its space: the weapon turns on its haft so the
        # edge leads the blow
        # [(guard bones, [(bone, axis, most degrees)])]: what never goes into the ground, and what turns to keep it out
        # (the wrist first, then the arm); bite: the active key (0..1) where the first entry's guard meets the ground
        self.ground, self.bite = ground, bite
        self.capsules = []

    def seconds(self, stage):
        return {"windup": self.windup, "active": self.active, "recover": self.recover}[stage] * TICK


def keyed(rig, name, length, keys, loop=False, lag=None, interp="cat", hold_until=None):
    """A clip from key poses [(seconds, pose)]. Each bone animates through every key (rest where a key leaves it out).
    lag: {bone: seconds}: that bone's inner keys come later (follow-through); the first and last keys stay put, and a
    lagged key never passes the next key (nor, with hold_until, a key before that time past it: the strike lands in
    its own ticks)."""
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
                tt = min(length - 1e-3, t + lag[b], keys[k + 1][0] - 0.02)
                if hold_until is not None and t <= hold_until:
                    tt = min(tt, hold_until)
                tt = max(t, tt)
            rot.append((tt, r[0], r[1], r[2]))
            if any_pos:
                pos.append((tt, q[0], q[1], q[2]))
        rot.sort(key=lambda e: e[0])
        clip.rot(b, rot, interp)
        if any_pos:
            pos.sort(key=lambda e: e[0])
            clip.pos(b, pos, interp)
    return clip


# The motion kit (SPEC §35.10): one continuous clip per move, so the curve never breaks at the peak or at contact.
BREAK = 0.018  # seconds each joint past the chest trails the one before it (successive breaking)
LEGS = ("thigh_", "shin_", "foot_")  # planted by IK on every key, so never lagged
ARMS = ("arm_", "forearm_", "hand_", "chest", "spine", "head", "neck")  # what anticipates and follows through
THROUGH = 0.25  # the follow-through key, a quarter into the recovery: a string links on from here
FOLLOW = 0.25  # how far past contact it carries, as a share of the swing from the peak (at most FOLLOW_MAX degrees)
FOLLOW_MAX = 25.0
COUNTER = 0.14  # the anticipation: a small move the other way, as a share of the peak, before the wind-up rises
SETTLE = 0.07  # the settle: past rest the other way before it comes to rest
DRIFT = 0.07  # a pose held over two keys swells by this much, so nothing freezes
CARRY = 8.0  # degrees: a strike key that moves less than this from the one before is dropped (the blow carries on)


def _moved(p, q):
    """The most any bone's rotation differs between two key poses, degrees."""
    out = 0.0
    for b in set(p) | set(q):
        rp, rq = _rot(p.get(b, (0, 0, 0))), _rot(q.get(b, (0, 0, 0)))
        out = max(out, max(abs(rp[i] - rq[i]) for i in range(3)))
    return out


GROUND = 24.0  # the ground in model space (pixels, y down)
SINK = 2.0  # pixels a guarded part may dip into the ground between keys before the build fails
EASE = 14.0  # degrees a tick a ground correction may grow or fade by


def lowest(rig, fr, bones):
    """The lowest cube corner of these bones, model y (larger is lower). fr: frames(), or a sampled pose (then only
    each bone's own chain is walked)."""
    out = -1e9
    for b in bones:
        node = rig.by_name.get(b)
        if node is None:
            continue
        M, T = fr[b] if b in fr and isinstance(fr[b], tuple) else _chain(rig, fr, node)
        for c in node.cubes:
            for x in (c.x, c.x + c.w):
                for y in (c.y, c.y + c.h):
                    for z in (c.z, c.z + c.d):
                        out = max(out, M[1][0] * x + M[1][1] * y + M[1][2] * z + T[1])
    return out


def _chain(rig, pose, node):
    """One bone's (M, T) for a sampled pose, walking only its ancestors (frames() for a single bone)."""
    path = []
    while node is not None:
        path.append(node)
        node = node.parent
    M, T = ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0)
    for b in reversed(path):
        p = pose.get(b.name, {})
        dp, dr, ds = p.get("pos", (0, 0, 0)), p.get("rot", (0, 0, 0)), p.get("scale", (1, 1, 1))
        piv = (b.pivot[0] + dp[0], b.pivot[1] + dp[1], b.pivot[2] + dp[2])
        T = tuple(T[i] + R._mv(M, piv)[i] for i in range(3))
        rot = (b.rot[0] * DEG + dr[0], b.rot[1] * DEG + dr[1], b.rot[2] * DEG + dr[2])
        M = R._mm(R._mm(M, R._rot(*rot)), ((ds[0], 0, 0), (0, ds[1], 0), (0, 0, ds[2])))
    return M, T


def keep_above(rig, pose, guard, adjust, bite=False):
    """The key pose with its guarded parts (a weapon, a fist) out of the ground: each adjuster in turn (bone, axis,
    most degrees: the wrist, then the arm) turns by the least that does it. With bite the lowest guarded point lands
    on the ground itself (within a pixel above it), so a blow that hits the ground meets it instead of passing
    through."""
    lo, hi = GROUND - 1.0, GROUND - 0.2

    def low(p):
        return lowest(rig, as_sample(p), guard)

    def ok(v):
        return lo <= v <= hi if bite else v <= hi

    def turned(p, b, ax, d):
        q = dict(p)
        v = q.get(b, (0, 0, 0))
        r = list(_rot(v))
        r[ax] += d
        q[b] = _mk(r, _pos(v), isinstance(v, dict))
        return q

    cur, v = pose, low(pose)
    if ok(v) or (bite and v < lo and not adjust):
        return pose
    for b, ax, most in adjust:
        best, bestv = None, None
        for d in range(2, int(most) + 1, 2):
            for sg in (1, -1):
                q = turned(cur, b, ax, sg * d)
                qv = low(q)
                if ok(qv):
                    return q
                # otherwise remember the turn that gets closest
                miss = abs(qv - (lo + hi) / 2) if bite else qv
                if bestv is None or miss < bestv:
                    best, bestv = q, miss
        if best is not None:
            cur = best
    return cur


def sinks(rig, clip, guard):
    """How deep (pixels) the guarded parts go into the ground anywhere in a clip, sampled every tick."""
    c = rig.clips[clip]
    worst = 0.0
    for k in range(int(c.length / TICK) + 1):
        worst = max(worst, lowest(rig, sample(rig, clip, min(c.length, k * TICK)), guard) - GROUND)
    return worst


def ground_pass(rig, ground, clips=None, skip=("death",)):
    """Keeps guarded parts out of the ground in whole clips, tick by tick (SPEC §35.10). ground: [(guard bones,
    [(bone, axis, most degrees)])], as Move.ground. At each tick where a guard would go into the ground, the adjusters
    turn in order (the wrist, then the elbow, then the shoulder) by the least that clears it; the turns are spread to
    the ticks either side and eased (a wide hold, then a soft average), so the correction leads in and out instead of
    snapping; then the adjusters' tracks are re-keyed a tick apart with it added. Repeats once on what is left."""
    names = clips if clips is not None else [n for n in rig.clips if n not in skip]
    for name in names:
        c = rig.clips[name]
        n = int(round(c.length / TICK))
        if n < 1:
            continue
        times = [min(c.length, k * TICK) for k in range(n + 1)]
        for _ in range(2):
            for guard, adjust in ground:
                poses = [sample(rig, name, t) for t in times]
                if max(lowest(rig, p, guard) for p in poses) <= GROUND - 0.2:
                    continue
                delta = {(b, ax): [0.0] * len(times) for b, ax, _ in adjust}
                prev = {(b, ax): 0.0 for b, ax, _ in adjust}
                for k, p in enumerate(poses):
                    cur = {b: dict(v) for b, v in p.items()}
                    v = lowest(rig, cur, guard)
                    for b, ax, most in adjust:
                        if v <= GROUND - 0.2:
                            prev[(b, ax)] = 0.0
                            continue
                        # the turn nearest the last tick's that clears (else the one that gets closest), so the
                        # correction moves on smoothly and never flips side from one tick to the next
                        was = prev[(b, ax)]
                        cands = sorted(set(range(-int(most), int(most) + 1, 2)) - {0}, key=lambda d: (abs(d - was), abs(d)))
                        best, bestv = 0.0, v
                        for d in cands:
                            q = dict(cur)
                            e = dict(q.get(b, {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}))
                            r = list(e.get("rot", (0, 0, 0)))
                            r[ax] += d * DEG
                            e["rot"] = tuple(r)
                            q[b] = e
                            qv = lowest(rig, q, guard)
                            if qv <= GROUND - 0.2:
                                best, bestv = d, qv
                                break
                            if qv < bestv - 1e-6 and (was == 0 or d * was > 0):
                                best, bestv = d, qv
                        prev[(b, ax)] = best
                        if best:
                            delta[(b, ax)][k] = best
                            e = dict(cur.get(b, {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}))
                            r = list(e.get("rot", (0, 0, 0)))
                            r[ax] += best * DEG
                            e["rot"] = tuple(r)
                            cur[b] = e
                            v = bestv
                for (b, ax), ds in delta.items():
                    if not any(ds):
                        continue
                    m = len(ds)
                    # the least correction that covers every tick's need and never changes by more than EASE degrees
                    # a tick, so it leads in and out without lifting a bite next to it more than it must; then a
                    # soft average for the corners (never below a tick's need)
                    def env(i, sgn):
                        best = 0.0
                        for j in range(m):
                            dist = abs(i - j)
                            if c.loop:
                                dist = min(dist, m - 1 - dist)
                            v = sgn * ds[j] - EASE * dist
                            if v > best:
                                best = v
                        return best
                    tent = [env(i, 1) - env(i, -1) for i in range(m)]
                    tat = lambda i: tent[i % (m - 1)] if c.loop else tent[max(0, min(m - 1, i))]
                    eased = [sum(tat(i + j) * w for j, w in ((-1, 1), (0, 2), (1, 1))) / 4 for i in range(m)]
                    eased = [e if abs(e) >= abs(d) or d == 0 else d for e, d in zip(eased, ds)]
                    if c.loop:
                        eased[-1] = eased[0]
                    old = c.tracks.get((b, "rot"))
                    keys = []
                    for k, t in enumerate(times):
                        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
                        base[ax] += eased[k]
                        keys.append((t, tuple(base), "cat"))
                    c.tracks[(b, "rot")] = keys


def clip_name(mv):
    return "move_" + mv.id


def bake(clip, step=TICK):
    """Re-keys every track at one key a tick on a curve that knows its keys' times, so the game's Catmull-Rom (which
    treats every gap between keys as the same length) runs it with no jump in speed where a long wind-up meets a
    two-tick strike. Each key's slope is the harmonic mean of the slopes either side of it (zero where it turns back),
    so a strike that follows a slow drift leaves from almost still and speeds up inside its own ticks, never
    overshooting a key; the ends start and stop still."""
    n = max(1, int(round(clip.length / step)))
    times = [min(clip.length, k * step) for k in range(n + 1)]
    for tk, keys in list(clip.tracks.items()):
        ks = []
        for k in keys:
            if ks and abs(k[0] - ks[-1][0]) < 1e-6:
                ks[-1] = k
            else:
                ks.append(k)
        if len(ks) < 2:
            continue
        m = []
        for i in range(len(ks)):
            if i == 0 or i == len(ks) - 1:
                m.append((0.0, 0.0, 0.0))
            else:
                h0, h1 = ks[i][0] - ks[i - 1][0], ks[i + 1][0] - ks[i][0]
                sl = []
                for c in range(3):
                    d0, d1 = (ks[i][1][c] - ks[i - 1][1][c]) / h0, (ks[i + 1][1][c] - ks[i][1][c]) / h1
                    if d0 * d1 <= 0:
                        sl.append(0.0)
                    else:  # Fritsch-Butland: weighted harmonic mean for uneven gaps
                        w0, w1 = 2 * h1 + h0, h1 + 2 * h0
                        sl.append((w0 + w1) / (w0 / d0 + w1 / d1))
                m.append(tuple(sl))
        out, j = [], 0
        for t in times:
            while j < len(ks) - 2 and t > ks[j + 1][0]:
                j += 1
            t0, t1 = ks[j][0], ks[j + 1][0]
            h = t1 - t0
            if t <= t0 or h <= 0:
                v = ks[j][1] if t <= t0 else ks[j + 1][1]
            elif t >= t1:
                v = ks[j + 1][1]
            else:
                a = (t - t0) / h
                h00, h10, h01, h11 = 2 * a ** 3 - 3 * a * a + 1, a ** 3 - 2 * a * a + a, -2 * a ** 3 + 3 * a * a, a ** 3 - a * a
                v = tuple(h00 * ks[j][1][c] + h10 * h * m[j][c] + h01 * ks[j + 1][1][c] + h11 * h * m[j + 1][c] for c in range(3))
            out.append((t, tuple(v), "cat"))
        clip.tracks[tk] = out
    return clip


def _rot(v):
    return v.get("rot", (0, 0, 0)) if isinstance(v, dict) else v


def _pos(v):
    return v.get("pos", (0, 0, 0)) if isinstance(v, dict) else (0, 0, 0)


def _mk(r, q, had_pos):
    return {"rot": tuple(r), "pos": tuple(q)} if had_pos else tuple(r)


def _scaled(pose, k, only=None):
    out = {}
    for b, v in pose.items():
        if only and not b.startswith(only):
            continue
        out[b] = _mk([c * k for c in _rot(v)], [c * k for c in _pos(v)], isinstance(v, dict))
    return out


def _through(end, peak, stuck=()):
    """The follow-through: the contact pose carried on the way the swing was going (peak to contact), a little. The
    stuck bones (the arm of a blow that bit the ground) stay on contact: the ground stopped them, the body goes on."""
    out = dict(end)
    for b, v in end.items():
        if not b.startswith(ARMS) or b in stuck:
            continue
        e, p = _rot(v), _rot(peak.get(b, (0, 0, 0)))
        r = [e[i] + max(-FOLLOW_MAX, min(FOLLOW_MAX, (e[i] - p[i]) * FOLLOW)) for i in range(3)]
        out[b] = _mk(r, _pos(v), isinstance(v, dict))
    return out


def _depth(rig, b):
    n, x = 0, rig.by_name.get(b)
    while x is not None and x.parent is not None:
        n += 1
        x = x.parent
    return n


def _lerp_pose(p, q, f):
    out = {}
    for b in set(p) | set(q):
        u, v = p.get(b, (0, 0, 0)), q.get(b, (0, 0, 0))
        r = [a + (c - a) * f for a, c in zip(_rot(u), _rot(v))]
        out[b] = _mk(r, [a + (c - a) * f for a, c in zip(_pos(u), _pos(v))], isinstance(u, dict) or isinstance(v, dict))
    return out


def edge_twist(rig, bone, mid, edge, before, at):
    """The turn on the weapon's haft (its y, degrees) that sets its edge along the way the blade moves into pose at
    (from before), and how well it then leads (cos, 1 is dead on)."""
    pa = body(point(frames(rig, as_sample(before)), bone, (0, mid[1], 0)))
    pb = body(point(frames(rig, as_sample(at)), bone, (0, mid[1], 0)))
    v = [pb[i] - pa[i] for i in range(3)]
    n = math.sqrt(sum(x * x for x in v)) or 1.0
    best = (0, -2.0)
    for th in range(-180, 180, 5):
        q = dict(at)
        q[bone] = (0, th, 0)
        fr = frames(rig, as_sample(q))
        m, e = body(point(fr, bone, mid)), body(point(fr, bone, edge))
        d = [e[i] - m[i] for i in range(3)]
        dn = math.sqrt(sum(x * x for x in d)) or 1.0
        c = sum(d[i] * v[i] for i in range(3)) / (dn * n)
        if c > best[1]:
            best = (th, c)
    return best


def _edge_keys(rig, mv, keys, W, A, R):
    """The weapon turns in the hand over the wind-up so its edge leads the strike (edge_twist at contact, the blade's
    path taken from just before it), holds through the follow-through, and turns back over the recovery."""
    bone, mid, edge = mv.edge
    tc = W + next(f for f, _ in mv.stages["active"] if f > 0) * A
    contact = next(p for t, p in keys if abs(t - tc) < 1e-6)
    peak = next(p for t, p in keys if abs(t - W) < 1e-6)
    th, _ = edge_twist(rig, bone, mid, edge, _lerp_pose(peak, contact, 0.85), contact)
    mv.edge_turn = th
    tt = W + A + THROUGH * R
    end = W + A + R
    out = []
    for t, p in keys:
        if t <= 0 or t >= end:
            k = 0.0
        elif t < W:
            k = ease_io(t / W)
        elif t <= tt + 1e-6:
            k = 1.0
        else:
            k = max(0.0, 1 - (t - tt) / (0.82 * R + W + A - tt))
        if k:
            p = dict(p)
            p[bone] = (0, th * k, 0)
        out.append((t, p))
    return out


def stage_clips(rig, mv, rest=None):
    """The move's one clip, {@code move_<id>}: rest, a small counter-move (anticipation), the wind-up's keys to its
    peak, the strike through contact, a follow-through past contact, the recovery's keys, a settle past rest, and rest.
    Every key gets its planted feet solved; joints past the chest trail by BREAK seconds a joint (the hips lead, then the
    chest, the shoulder, the elbow, the hand, the weapon); a pose held over two keys swells a little. It also sets the
    move's marks (ticks from its start): peak, contact, whoosh, link and the steps its feet take."""
    rest = rest or {}
    sides = mv.plant if (mv.advance == 0 and mv.rise == 0) else ()
    size = getattr(rig, "size", 1.0)

    def fit(p):  # the library's crouch depths are for a 36 px leg: a shorter body drops less
        if size != 1.0 and isinstance(p.get("pelvis"), dict):
            p = dict(p)
            q = p["pelvis"]
            p["pelvis"] = {"rot": q.get("rot", (0, 0, 0)), "pos": tuple(v * size for v in q.get("pos", (0, 0, 0)))}
        return plant(rig, p, sides)
    W, A, R = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    w = [(f, p) for f, p in mv.stages.get("windup", [])]
    a = [(f, p) for f, p in mv.stages.get("active", [])]
    r = [(f, p) for f, p in mv.stages.get("recover", []) if 0 < f < 1]
    peak = w[-1][1] if w else rest
    end = a[-1][1] if a else peak
    keys = [(0.0, rest)]
    if mv.cls != "NONE" and not any(f < 0.35 for f, _ in w):
        counter = _scaled({b: v for b, v in peak.items() if b.startswith(ARMS)}, -COUNTER)
        if "pelvis" in rig.by_name:
            counter["pelvis"] = {"rot": (0, 0, 0), "pos": (0, -1.2, 0)}
        keys.append((0.3 * W, counter))
    keys += [(f * W, p) for f, p in w if f > 0]
    strike = [(W + f * A, p) for f, p in a if f > 0]
    if mv.cls != "NONE":
        # a strike key that barely moves from the one before (the end of the active stage, written as contact again)
        # would stop the blow dead at contact: the follow-through carries it on instead
        strike = [k for i, k in enumerate(strike) if i == 0 or _moved(strike[i - 1][1], k[1]) >= CARRY]
    keys += strike
    stuck = [b for b, _, _ in mv.ground[0][1]] if mv.ground and mv.bite is not None else ()
    through = _through(end, peak, stuck) if mv.cls != "NONE" else end
    keys.append((W + A + THROUGH * R, through))
    # the recovery's own keys, none closer than 0.15 of the recovery after the follow-through (a big pose change in
    # two ticks would jerk); the next one keeps its order
    last = THROUGH
    for f, p in r:
        if THROUGH + 0.08 < f < 0.74:
            last = min(0.74, max(f, last + 0.15))
            keys.append((W + A + last * R, p))
    keys.append((W + A + 0.82 * R, _scaled(end, -SETTLE)))
    keys.append((W + A + R, rest))
    keys = [(t, fit(p)) for t, p in keys]
    # a held pose swells (or, on the next hold, eases back), so the body never stands frozen across two keys
    for k in range(1, len(keys)):
        prev, cur = keys[k - 1][1], keys[k][1]
        sw = {}
        for b, v in cur.items():
            pv = prev.get(b)
            if pv is None or b.startswith(LEGS) or b == "pelvis":
                continue
            rv, rp = _rot(v), _rot(pv)
            if max(abs(c) for c in rv) > 3 and max(abs(rv[i] - rp[i]) for i in range(3)) < 1.0:
                f = 1 + DRIFT * (1 if k % 2 else -0.6)
                sw[b] = _mk([c * f for c in rv], _pos(v), isinstance(v, dict))
        if sw:
            cur = dict(cur)
            cur.update(sw)
            keys[k] = (keys[k][0], cur)
    if mv.edge and a:
        keys = _edge_keys(rig, mv, keys, W, A, R)
    if mv.ground and mv.bite is not None:  # the blow meets the ground at contact; ground_pass keeps the rest out of it
        guard, adjust = mv.ground[0]
        at = [k for k, (t, _) in enumerate(keys) if abs(t - (W + mv.bite * A)) < 1e-6]
        if at:
            t, p = keys[at[0]]
            p = keep_above(rig, p, guard, adjust, bite=True)
            keys[at[0]] = (t, p)
            # the follow-through keeps the stuck arm where the bite put it
            for k, (t, q) in enumerate(keys):
                if abs(t - (W + A + THROUGH * R)) < 1e-6:
                    q = dict(q)
                    q.update({b: p[b] for b in stuck if b in p})
                    keys[k] = (t, q)
    lag = {}
    for b in rig.by_name:
        if b.startswith(LEGS) or b == "pelvis":
            continue
        d = max(0, _depth(rig, b) - 2)
        # a 0 in the move's own lag keeps that bone on its parent's time (a weapon that must land on its mark)
        lag[b] = 0.0 if mv.lag.get(b) == 0 else max(mv.lag.get(b, 0.0), BREAK * d)
    bake(keyed(rig, clip_name(mv), W + A + R, keys, lag=lag, hold_until=(W + A)))
    if mv.ground:  # between the keys too
        ground_pass(rig, mv.ground, clips=[clip_name(mv)])
    mv.peak_t, mv.link_t = W, W + A + THROUGH * R
    mv.link = max(1, int(round(THROUGH * mv.recover)))


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
    clip = clip_name(mv)
    W, A = mv.seconds("windup"), mv.seconds("active")
    prev = None
    for k in range(mv.active):
        t = W + (k + 0.5) / mv.active * A
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
            cx, cz = (round(tips[0][0][0], 3), round(tips[0][0][2], 3)) if mv.disc_at == "hit" and tips else (0.0, 0.0)
            for i in range(n):
                a = 2 * math.pi * i / n
                caps.append([cx, 0.3, cz, round(cx + math.sin(a) * mv.disc, 3), 0.3, round(cz + math.cos(a) * mv.disc, 3), 0.55])
        out.append(caps)
    return out


def marks(rig, mv):
    """The move's marks in ticks from its start (base timing): its peak, its contact (the first tick with a capsule,
    else the first active tick), the whoosh (the tick the striking tip moves fastest, up to contact), and each step a
    foot takes (it comes back down to the ground). The server plays its sounds on them; the client shakes the view on
    contact."""
    W = mv.windup
    first = next((k for k, c in enumerate(mv.capsules) if c), 0)
    m = {"peak": W, "contact": W + first, "whoosh": W}
    clip = clip_name(mv)
    if mv.hit:
        bone, tip, _ = mv.hit[0]
        best, prevp = -1.0, None
        for k in range(max(0, W - 1), W + first + 2):
            fr = frames(rig, sample(rig, clip, k / 20.0))
            q = point(fr, bone, tip)
            if prevp is not None:
                v = math.dist(q, prevp)
                if v > best:
                    best, m["whoosh"] = v, max(W, k - 1)  # the sound leads the limb by a tick
            prevp = q
    rest = frames(rig, {})
    steps = []
    for side in ("l", "r"):
        b = "foot_" + side
        if b not in rig.by_name:
            continue
        y0 = point(rest, b, (0, 0, 0))[1]
        up = False
        for k in range(0, mv.windup + mv.active + mv.recover + 1):
            y = point(frames(rig, sample(rig, clip, k / 20.0)), b, (0, 0, 0))[1]
            lift = y0 - y  # pixels above where it stands
            if lift > 3:
                up = True
            elif up and lift < 1:
                steps.append(k)
                up = False
    for i, k in enumerate(sorted(steps)):
        m[f"step{i}"] = k
    return m


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
    guard = list(getattr(rig, "guard", ()))  # parts that never go into the ground, in any clip
    if guard:
        for name in rig.clips:
            if name == "death":  # it falls with them
                continue
            deep = sinks(rig, name, guard)
            if deep > SINK:
                problems.append(f"{rig.id}/{name}: {'/'.join(guard)} goes {deep:.1f} px into the ground")
    for mv in moves:
        p = f"{rig.id}/{mv.id}"
        if mv.cls != "NONE" and mv.windup < MIN_WINDUP[mv.cls]:
            problems.append(f"{p}: a {mv.cls} wind-up of {mv.windup} ticks (at least {MIN_WINDUP[mv.cls]})")
        if mv.hold and (mv.cls != "HEAVY" or mv.hold > MAX_HOLD):
            problems.append(f"{p}: a hold only on HEAVY moves, at most {MAX_HOLD} ticks")
        if mv.cls not in ("NONE",) and mv.hit:
            peak = sample(rig, clip_name(mv), mv.seconds("windup"))
            dist = pose_distance(rig, peak)
            if dist < 12:
                problems.append(f"{p}: its wind-up peak is only {dist:.1f} degrees from rest (unreadable)")
        for guard, _ in mv.ground or ():
            deep = sinks(rig, clip_name(mv), guard)
            if deep > SINK:
                problems.append(f"{p}: {'/'.join(guard)} goes {deep:.1f} px into the ground")
        if mv.advance == 0 and mv.rise == 0:
            for side in mv.plant:
                s = foot_slide(rig, clip_name(mv), side, samples=24)
                if s > 0.1:
                    problems.append(f"{p}: the {side} foot slides {s:.2f} blocks while planted")
    return problems


# ---------------------------------------------------------------------------------------------------------------------
# Writing

def rig_text(rig):
    return "\n".join(rig.java_lines()) + "\n"


def move_row(mv):
    row = {"id": mv.id, "class": mv.cls,
           "time": {"windup": mv.windup, "active": mv.active, "recover": mv.recover, "track": mv.track, "lock": mv.lock,
                    "link": getattr(mv, "link", 0)},
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
    if getattr(mv, "marks", None):
        row["marks"] = mv.marks
    return row


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1, sort_keys=False)
        f.write("\n")
