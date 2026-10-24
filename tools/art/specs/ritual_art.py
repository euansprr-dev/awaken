"""The Ritual of the Bell's two bodies (docs/proposals/the-ritual.md): the Seated One, the colossal god carved into the
hall's far wall, and the ritual statue, one of the twelve hooded guardians kneeling down the nave.

    python3 tools/art/build_all.py ritual_art     textures (skin + glow) and src/.../mobs/client/anim/RitualData.java
    python3 tools/art/specs/ritual_art.py build   the same
    python3 tools/art/specs/ritual_art.py preview DIR
                                                  per body: day views (front, side, three-quarter, back), the hall
                                                  (candle-lit black) and the glow at night, a size line-up with a
                                                  player, and every clip's key poses

Both are hero rigs (fauna_rig.py: bones, packed UVs, clean value masses, clips sampled like vanilla), parsed in game by
HeroRig from RitualData.java. Conventions (vanilla ModelPart): model pixels, y down, ground at y = 24, faces -z,
rotations Z then Y then X, rotation keys in degrees, position keys in posVec units (+y up).

  * the_seated_one is drawn at x4 (1 model pixel = 0.25 block): seated 88 px (22 blocks) to the top of the halo of gold rays behind its head.
    Its origin is the centre of its seat at the dais; the throne (built in blocks) holds it: armrests from |x| = 28,
    the wall behind z = +10, the seat under the thighs at y = 0 (H 24 = 6 blocks over the dais).
  * ritual_statue is drawn at x1.2: 46 px standing (3.45 blocks), about 32 kneeling, a 52 px glaive.

Poses are authored as whole bodies and baked to dense keys: arm reaches come from a small IK solver (a hand on a
point, a glaive along a line), so the hands land where the story needs them, and every clip starts on the pose the
previous one ends on (checked below, with the hall's hard bounds, over every clip's samples).
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, lerpc, mul, p_blotch, p_stone, combine, SIDES, _h  # noqa: E402,F401

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/mobs/client/anim/RitualData.java")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/awaken/textures/entity/mobs")
DEG = math.pi / 180.0


# =====================================================================================================================
# Toolkit: absolute-space building, forward kinematics, a small IK solver, whole-body pose clips, bounds
# =====================================================================================================================

def rotm(rx, ry, rz):
    """ModelPart rotation (radians): Z then Y then X, i.e. M = Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rxm = np.array(((1, 0, 0), (0, cx, -sx), (0, sx, cx)))
    rym = np.array(((cy, 0, sy), (0, 1, 0), (-sy, 0, cy)))
    rzm = np.array(((cz, -sz, 0), (sz, cz, 0), (0, 0, 1)))
    return rzm @ rym @ rxm


def Y(h):
    """Model y of a height above the ground."""
    return 24.0 - h


class Build:
    """Bones placed by absolute pivots (x, height above ground, z); cubes in absolute space on unrotated bones."""

    def __init__(self, rig):
        self.rig = rig
        self.world = {}

    def bone(self, parent, name, at, rot=(0, 0, 0)):
        p = np.array((at[0], Y(at[1]), at[2]), dtype=float)
        if parent is None:
            Mp, Tp = np.eye(3), np.zeros(3)
        else:
            Mp, Tp = self.world[parent.name]
        rel = Mp.T @ (p - Tp)
        b = self.rig.bone(name, tuple(float(round(v, 4)) for v in rel), rot, parent)
        self.world[name] = (Mp @ rotm(*(r * DEG for r in rot)), p)
        return b

    def box(self, b, name, x0, x1, h0, h1, z0, z1, **kw):
        M, T = self.world[b.name]
        if not np.allclose(M, np.eye(3)):
            raise ValueError(f"{name}: absolute boxes only on unrotated bones")
        return b.cube(name, float(x0 - T[0]), float(Y(h1) - T[1]), float(z0 - T[2]), x1 - x0, h1 - h0, z1 - z0, **kw)


def to_sample(off):
    """Authoring offsets {bone: {"rot": deg, "pos": posVec (+y up), "scale"}} -> the sampler's pose dict."""
    out = {}
    for bn, e in off.items():
        r = e.get("rot", (0, 0, 0))
        p = e.get("pos", (0, 0, 0))
        out[bn] = {"rot": tuple(v * DEG for v in r), "pos": (p[0], -p[1], p[2]), "scale": e.get("scale", (1, 1, 1))}
    return out


def fk(rig, pose, only=None):
    """World transforms {bone: (M, T)} of a sampler pose (T is the bone's pivot in model space)."""
    out = {}
    need = None
    if only is not None:
        need = set()
        for n in only:
            b = rig.by_name[n]
            while b is not None:
                need.add(b.name)
                b = b.parent

    def walk(b, M, T):
        if need is not None and b.name not in need:
            return
        p = pose.get(b.name, {})
        dp = p.get("pos", (0, 0, 0))
        dr = p.get("rot", (0, 0, 0))
        ds = p.get("scale", (1, 1, 1))
        piv = np.array((b.pivot[0] + dp[0], b.pivot[1] + dp[1], b.pivot[2] + dp[2]))
        T2 = T + M @ piv
        M2 = M @ rotm(b.rot[0] * DEG + dr[0], b.rot[1] * DEG + dr[1], b.rot[2] * DEG + dr[2]) @ np.diag(ds)
        out[b.name] = (M2, T2)
        for ch in b.children:
            walk(ch, M2, T2)
    for b in rig.roots():
        walk(b, np.eye(3), np.zeros(3))
    return out


def world_pt(W, bone, local):
    M, T = W[bone]
    return M @ np.asarray(local, dtype=float) + T


def world_dir(W, bone, local):
    M, _ = W[bone]
    v = M @ np.asarray(local, dtype=float)
    return v / (np.linalg.norm(v) or 1.0)


def corners(rig, pose):
    """Every cube corner in model space, posed (n x 3)."""
    W = fk(rig, pose)
    pts = []
    for b in rig.bones:
        M, T = W[b.name]
        for c in b.cubes:
            g = c.inflate
            for dx in (c.x - g, c.x + c.w + g):
                for dy in (c.y - g, c.y + c.h + g):
                    for dz in (c.z - g, c.z + c.d + g):
                        pts.append(M @ np.array((dx, dy, dz)) + T)
    return np.array(pts)


def merge(*offs):
    """Sums authoring offsets (rotations and positions add; scales multiply)."""
    out = {}
    for off in offs:
        for bn, e in off.items():
            o = out.setdefault(bn, {})
            for k, v in e.items():
                if k == "scale":
                    old = o.get(k, (1, 1, 1))
                    o[k] = tuple(old[i] * v[i] for i in range(3))
                else:
                    old = o.get(k, (0, 0, 0))
                    o[k] = tuple(old[i] + v[i] for i in range(3))
    return out


def lerp_off(a, b, t):
    """Blends two authoring offsets (missing entries are rest)."""
    out = {}
    for bn in set(a) | set(b):
        ea, eb = a.get(bn, {}), b.get(bn, {})
        o = {}
        for k in set(ea) | set(eb):
            d = (1, 1, 1) if k == "scale" else (0, 0, 0)
            va, vb = ea.get(k, d), eb.get(k, d)
            o[k] = tuple(va[i] + (vb[i] - va[i]) * t for i in range(3))
        out[bn] = o
    return out


def ik(rig, off, vars_, goals, prefer=None, iters=40, lam=0.6, chain=None):
    """Damped least squares on degree offsets. vars_: [(bone, axis 0/1/2, lo, hi)]. goals: functions W -> residual
    vector (px). prefer: {(bone, axis): (value, weight)} keeps the solution near a natural angle. chain: the end bones
    the goals read (FK is limited to their ancestors). Returns a new off."""
    off = {k: dict(v) for k, v in off.items()}
    x = np.array([off.get(b, {}).get("rot", (0, 0, 0))[a] for b, a, _, _ in vars_], dtype=float)

    def put(xv):
        for k, (b, a, _, _) in enumerate(vars_):
            e = off.setdefault(b, {})
            r = list(e.get("rot", (0, 0, 0)))
            r[a] = float(xv[k])
            e["rot"] = tuple(r)

    def resid(xv):
        put(xv)
        W = fk(rig, to_sample(off), only=chain)
        res = [np.atleast_1d(g(W)) for g in goals]
        if prefer:
            for k, (b, a, _, _) in enumerate(vars_):
                if (b, a) in prefer:
                    v, w = prefer[(b, a)]
                    res.append(np.array([(xv[k] - v) * w * 0.02]))
        return np.concatenate(res)
    lo = np.array([v[2] for v in vars_], dtype=float)
    hi = np.array([v[3] for v in vars_], dtype=float)
    for _ in range(iters):
        r0 = resid(x)
        J = np.zeros((len(r0), len(x)))
        for k in range(len(x)):
            xp = x.copy()
            xp[k] += 0.05
            J[:, k] = (resid(xp) - r0) / 0.05
        A = J.T @ J + lam * lam * np.eye(len(x))
        dx = np.linalg.solve(A, -J.T @ r0)
        step = np.clip(dx, -12, 12)
        x = np.clip(x + step, lo, hi)
        if np.linalg.norm(step) < 1e-3:
            break
    put(x)
    return off


def g_point(bone, local, target, w=1.0):
    t = np.asarray(target, dtype=float)
    return lambda W: (world_pt(W, bone, local) - t) * w


def g_dir(bone, local, target, w=6.0):
    t = np.asarray(target, dtype=float)
    t = t / np.linalg.norm(t)
    return lambda W: (world_dir(W, bone, local) - t) * w


def bake(clip, fn, times, interp="cat"):
    """Samples fn(t) -> authoring offsets at the given times into keys on every bone/target that moves."""
    samples = [(float(t), fn(t)) for t in times]
    tracks = {}
    for _, off in samples:
        for bn, e in off.items():
            for k in e:
                tracks.setdefault((bn, k), None)
    for (bn, k) in sorted(tracks):
        d = (1, 1, 1) if k == "scale" else (0, 0, 0)
        keys = [(t,) + tuple(off.get(bn, {}).get(k, d)) for t, off in samples]
        if all(max(abs(kk[i + 1] - d[i]) for i in range(3)) < 1e-4 for kk in keys):
            continue
        keys = [(float(kk[0]),) + tuple(round(float(v), 3) for v in kk[1:]) for kk in keys]
        getattr(clip, k)(bn, keys, interp)
    return clip


def times(length, step):
    n = max(1, int(round(length / step)))
    return [length * k / n for k in range(n + 1)]


def ease(t):
    """Smooth in and out (0..1)."""
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ease5(t):
    t = max(0.0, min(1.0, t))
    return t * t * t * (t * (t * 6 - 15) + 10)


def ease_in(t, p=2.0):
    t = max(0.0, min(1.0, t))
    return t ** p


def ease_out(t, p=2.0):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** p


def span(t, a, b):
    """0..1 over [a, b]."""
    return max(0.0, min(1.0, (t - a) / (b - a))) if b > a else (1.0 if t >= b else 0.0)


def euler_zyx(M):
    """Degrees (rx, ry, rz) with Rz * Ry * Rx = M (ModelPart order)."""
    sy = -M[2, 0]
    sy = max(-1.0, min(1.0, sy))
    ry = math.asin(sy)
    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(M[2, 1], M[2, 2])
        rz = math.atan2(M[1, 0], M[0, 0])
    else:
        rx = math.atan2(-M[1, 2], M[1, 1])
        rz = 0.0
    return (rx / DEG, ry / DEG, rz / DEG)


def euler_near(M, prev=None):
    """The ZYX Euler triple for M closest to prev (both equivalent triples tried, each unwrapped)."""
    a = euler_zyx(M)
    if prev is None:
        return a
    b = (a[0] + 180.0, 180.0 - a[1], a[2] + 180.0)
    best = None
    for cand in (a, b):
        u = _unwrap(prev, cand)
        d = sum((u[i] - prev[i]) ** 2 for i in range(3))
        if best is None or d < best[0]:
            best = (d, u)
    return best[1]


def quat(M):
    t = M[0, 0] + M[1, 1] + M[2, 2]
    if t > 0:
        s_ = math.sqrt(t + 1.0) * 2
        q = ((M[2, 1] - M[1, 2]) / s_, (M[0, 2] - M[2, 0]) / s_, (M[1, 0] - M[0, 1]) / s_, 0.25 * s_)
    elif M[0, 0] > M[1, 1] and M[0, 0] > M[2, 2]:
        s_ = math.sqrt(1.0 + M[0, 0] - M[1, 1] - M[2, 2]) * 2
        q = (0.25 * s_, (M[0, 1] + M[1, 0]) / s_, (M[0, 2] + M[2, 0]) / s_, (M[2, 1] - M[1, 2]) / s_)
    elif M[1, 1] > M[2, 2]:
        s_ = math.sqrt(1.0 + M[1, 1] - M[0, 0] - M[2, 2]) * 2
        q = ((M[0, 1] + M[1, 0]) / s_, 0.25 * s_, (M[1, 2] + M[2, 1]) / s_, (M[0, 2] - M[2, 0]) / s_)
    else:
        s_ = math.sqrt(1.0 + M[2, 2] - M[0, 0] - M[1, 1]) * 2
        q = ((M[0, 2] + M[2, 0]) / s_, (M[1, 2] + M[2, 1]) / s_, 0.25 * s_, (M[1, 0] - M[0, 1]) / s_)
    q = np.array(q)
    return q / np.linalg.norm(q)


def qmat(q):
    x, y, z, w = q
    return np.array(((1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)),
                     (2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)),
                     (2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y))))


def slerp_m(A, Bm, t):
    qa, qb = quat(A), quat(Bm)
    d = float(np.dot(qa, qb))
    if d < 0:
        qb, d = -qb, -d
    if d > 0.9995:
        q = qa + (qb - qa) * t
    else:
        th = math.acos(d)
        q = (math.sin((1 - t) * th) * qa + math.sin(t * th) * qb) / math.sin(th)
    return qmat(q / np.linalg.norm(q))


def frame(fwd, up_hint):
    """A rotation whose local -z is fwd and whose local +y is as close to up_hint as it can be."""
    fz = -np.asarray(fwd, dtype=float)
    fz /= np.linalg.norm(fz)
    y = np.asarray(up_hint, dtype=float)
    y = y - fz * np.dot(y, fz)
    y /= np.linalg.norm(y)
    x = np.cross(y, fz)
    return np.column_stack((x, y, fz))


def bezier(pts, t):
    pts = [np.asarray(p, dtype=float) for p in pts]
    while len(pts) > 1:
        pts = [pts[k] * (1 - t) + pts[k + 1] * t for k in range(len(pts) - 1)]
    return pts[0]


def local_rot_for(rig, off, bone, world_M, prev=None):
    """The rotation offset (degrees) that gives `bone` the world rotation world_M in pose `off` (nearest to prev)."""
    W = fk(rig, to_sample(off), only=[bone])
    b = rig.by_name[bone]
    Mp = W[b.parent.name][0] if b.parent else np.eye(3)
    rest = rotm(*(v * DEG for v in b.rot))
    want = Mp.T @ world_M
    # offsets add in Euler space: total = rest + offset
    ptot = None if prev is None else tuple(prev[i] + b.rot[i] for i in range(3))
    tot = euler_near(want, ptot)
    return tuple(tot[i] - b.rot[i] for i in range(3)), rest


# =====================================================================================================================
# The Seated One: a serene colossal deity of pale weathered marble, gold leaf on its hems, a broad gold collar and a
# crown of gold rays; seated on a throne carved into the wall, hands on its knees, eyes closed
# =====================================================================================================================

# The halo behind the head (fixed to the chest, so the head turns inside it): its centre, its ring and its rays
HALO_C = (0.0, 71.5, 7.5)  # x, height, z
HALO_RING = 10.5
# (angle from straight up in degrees, inner radius, outer radius, width)
HALO_RAYS = [(a, 12.0, (16.5 + 1.5 * abs(math.sin(a * DEG))) if k % 2 == 0 else (14.0 + abs(math.sin(a * DEG))), 2 if k % 2 == 0 else 1)
             for k, a in enumerate(range(-108, 109, 18))]
# The crown on the head: short gold points rising from the band (angle, length)
CROWN = [(0, 4), (-30, 3), (30, 3)]


def seated_one():
    r = Rig("the_seated_one", (256, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 4.0, 8.0, (5.0, 6.0), 0.0
    B = Build(r)
    root = B.bone(None, "root", (0, 0, 0))
    body = B.bone(root, "body", (0, 30, 1))
    B.box(body, "hips", -12, 12, 24, 36, -5, 8)
    # the robe drapes over each knee, falls straight to a flared gold hem; a deep fold runs between the legs
    robe = B.bone(body, "robe", (0, 34, -20))
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        S = (lambda n: None) if side > 0 else (lambda n: n)

        def xs(a, b):
            return (a, b) if side > 0 else (-b, -a)
        B.box(robe, "kneedrape_" + nm, *xs(1, 12), 27, 36, -22, -18, mirror=mir, share=S("kneedrape_l"))
        B.box(robe, "robe_" + nm, *xs(1, 12), 7, 27, -21, -19, mirror=mir, share=S("robe_l"))
        B.box(robe, "hem_" + nm, *xs(0, 13), 3, 8, -23, -18, mirror=mir, share=S("hem_l"))
        B.box(robe, "drape_" + nm, *xs(11, 13), 5, 31, -18, -10, mirror=mir, share=S("drape_l"))
    B.box(robe, "fold", -1, 1, 6, 34, -20, -18)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        S = (lambda n: None) if side > 0 else (lambda n: n)

        def xs(a, b):
            return (a, b) if side > 0 else (-b, -a)
        th = B.bone(body, "thigh_" + nm, (side * 6.5, 30, 0))
        B.box(th, "thigh_" + nm, *xs(0.5, 12.5), 24, 35, -19, 3, mirror=mir, share=S("thigh_l"))
        sn = B.bone(th, "shin_" + nm, (side * 6.5, 29, -15))
        B.box(sn, "shin_" + nm, *xs(2.5, 10.5), 4, 29, -19, -11, mirror=mir, share=S("shin_l"))
        B.box(sn, "foot_" + nm, *xs(2, 11), 0, 4, -24, -13, mirror=mir, share=S("foot_l"))
    chest = B.bone(body, "chest", (0, 36, 1))
    B.box(chest, "abdomen", -9, 9, 36, 48, -5, 7)
    B.box(chest, "sash", -10.5, 10.5, 35, 40, -6, 8)
    B.box(chest, "ribs", -12, 12, 47, 61, -5, 7)
    B.box(chest, "pecs", -10, 10, 50, 59, -7, -5)
    B.box(chest, "traps", -9, 9, 60, 63, -3, 6)
    B.box(chest, "cape", -14, 14, 28, 60, 7, 8)
    # the collar: an inner ring at the neck, a broad lower tier over the shoulders, a pendant on the chest
    B.box(chest, "collar_in", -8, 8, 59, 64, -6, 7)
    B.box(chest, "collar_out", -15, 15, 56, 61, -8, 8)
    B.box(chest, "pendant", -4, 4, 50, 56, -9, -7)
    halo = B.bone(chest, "halo", HALO_C)
    nseg = 22
    for k in range(nseg):
        a = (k + 0.5) * 360.0 / nseg - 180.0
        if abs(a) > 128:
            continue  # behind the shoulders: never seen
        rad = a * DEG
        seg = B.bone(halo, f"ring{k}", (HALO_C[0] + HALO_RING * math.sin(rad), HALO_C[1] + HALO_RING * math.cos(rad), HALO_C[2]), (0, 0, a))
        seg.cube(f"ring{k}", -1.5, -1, -0.5, 3, 2, 1)
    for k, (a, r0, r1, w) in enumerate(HALO_RAYS):
        rad = a * DEG
        rb = B.bone(halo, f"hray{k}", (HALO_C[0] + r0 * math.sin(rad), HALO_C[1] + r0 * math.cos(rad), HALO_C[2]), (0, 0, a))
        ln = int(round(r1 - r0))
        body_len = max(1, ln - 1)
        rb.cube(f"hray{k}", -w / 2, -body_len, -0.5, w, body_len, 1)
        if w > 1:
            rb.cube(f"htip{k}", -0.5, -ln, -0.5, 1, ln - body_len, 1)
    neck = B.bone(chest, "neck", (0, 61, 1))
    B.box(neck, "neck", -3.5, 3.5, 60, 66, -3, 5)
    # the head sits 1 px forward of the neck and is 11 deep, so a full turn never reaches the wall behind
    head = B.bone(neck, "head", (0, 65, -1))
    B.box(head, "jaw", -5, 5, 64, 68, -7, 3)
    B.box(head, "cranium", -6, 6, 68, 78, -7, 4)
    B.box(head, "brow", -5, 5, 72, 73, -8, -7)
    B.box(head, "nose", -1, 1, 67, 72, -8, -7)
    B.box(head, "hair", -5.5, 5.5, 59, 70, 1, 4)
    B.box(head, "band", -6.5, 6.5, 75, 78, -7.5, 4.5)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        ex0, ex1 = (1, 4) if side > 0 else (-4, -1)
        B.box(head, "eye_" + nm, ex0, ex1, 70, 72, -7.2, -6.2, mirror=mir, share=None if side > 0 else "eye_l")
        a0, a1 = (6, 7) if side > 0 else (-7, -6)
        B.box(head, "ear_" + nm, a0, a1, 66, 73, -2, 1, mirror=mir, share=None if side > 0 else "ear_l")
        lid = B.bone(head, "lid_" + nm, (side * 2.5, 72, -7))
        lid.cube("lid_" + nm, -1.5, 0, -0.5, 3, 2, 1, mirror=mir, inflate=0.12, share=None if side > 0 else "lid_l")
    crown = B.bone(head, "crown", (0, 75, -2))
    for k, (ang, ln) in enumerate(CROWN):
        a = ang * DEG
        rb = B.bone(crown, f"ray{k}", (4.0 * math.sin(a), 75 + 3.0 * math.cos(a), -2), (0, 0, ang))
        rb.cube(f"ray{k}", -1, -(ln - 1), -0.5, 2, ln - 1, 1)
        rb.cube(f"tip{k}", -0.5, -ln, -0.5, 1, 1, 1)
    # arms: sleeved upper arms hanging straight, forearms laid along the thighs, hands on the knees
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        S = (lambda n: None) if side > 0 else (lambda n: n)
        arm = B.bone(chest, "arm_" + nm, (side * 16.5, 59, 0.5))
        x0, x1 = sorted((side * 12.5, side * 20.5))
        B.box(arm, "shoulder_" + nm, x0, x1, 51, 61, -4, 5, mirror=mir, share=S("shoulder_l"))
        x0, x1 = sorted((side * 14, side * 20))
        B.box(arm, "uarm_" + nm, x0, x1, 39, 52, -3, 4, mirror=mir, share=S("uarm_l"))
        sl = B.bone(arm, "sleeve_" + nm, (side * 17, 41, 3))
        x0, x1 = sorted((side * 13.5, side * 20.5))
        B.box(sl, "sleeve_" + nm, x0, x1, 31, 42, 2, 5, mirror=mir, share=S("sleeve_l"))
        fa = B.bone(arm, "forearm_" + nm, (side * 17, 38, 0.5), (0, side * 32, 0))
        fa.cube("forearm_" + nm, -2.5, -2.5, -16, 5, 5, 16, mirror=mir, share=S("forearm_l"))
        fa.cube("cuff_" + nm, -3, -3, -15, 6, 6, 3, mirror=mir, share=S("cuff_l"))
    # the right hand: the palm's centre is hand_r's pivot (the light gathers there); fingers that spread and close
    W = B.world
    fa_r = r.by_name["forearm_r"]
    hand = B.bone(fa_r, "hand_r", tuple(_ah(W, "forearm_r", (0, 1.0, -19.5))), (8, 24, 0))
    hand.cube("palm_r", -4, -1.5, -3.5, 8, 3, 7)
    fing = B.bone(hand, "fingers_r", tuple(_ah(W, "hand_r", (0, 0.2, -3.5))), (55, 0, 0))
    for k, (fx, ln, ln2) in enumerate(((3, 3, 3), (1, 4, 3), (-1, 3, 3), (-3, 2, 2))):
        f1 = B.bone(fing, f"finger{k + 1}_r", tuple(_ah(W, "fingers_r", (fx, 0, 0))))
        f1.cube(f"finger{k + 1}_r", -1, -1, -ln, 2, 2, ln)
        f2 = B.bone(f1, f"fingertip{k + 1}_r", tuple(_ah(W, f"finger{k + 1}_r", (0, 0, -ln))), (20, 0, 0))
        f2.cube(f"fingertip{k + 1}_r", -1, -1, -ln2, 2, 2, ln2)
    th = B.bone(hand, "thumb_r", tuple(_ah(W, "hand_r", (4, 0.3, 0.5))), (10, 35, 0))
    th.cube("thumb_r", -1, -1, -3, 2, 2, 3)
    tt = B.bone(th, "thumbtip_r", tuple(_ah(W, "thumb_r", (0, 0, -3))), (0, 15, 0))
    tt.cube("thumbtip_r", -1, -1, -2, 2, 2, 2)
    # the left hand only rests; its fingers curl over the knee in one piece
    fa_l = r.by_name["forearm_l"]
    hl = B.bone(fa_l, "hand_l", tuple(_ah(W, "forearm_l", (0, 1.0, -19.5))), (8, -24, 0))
    hl.cube("palm_l", -4, -1.5, -3.5, 8, 3, 7)
    fl = B.bone(hl, "fingers_l", tuple(_ah(W, "hand_l", (0, 0.2, -3.5))), (60, 0, 0))
    for k, (fx, ln) in enumerate(((-3, 5), (-1, 6), (1, 5), (3, 4))):
        fl.cube(f"fingerl{k + 1}", fx - 1, -1, -ln, 2, 2, ln)
    thl = B.bone(hl, "thumb_l", tuple(_ah(W, "hand_l", (-4, 0.3, 0.5))), (10, -35, 0))
    thl.cube("thumb_l", -1, -1, -4, 2, 2, 4)
    r.look = [("head", 1.0, 0.6)]
    r.pack()
    return r


def _ah(W, bone, local):
    """A point in a bone's local frame (at rest) as (x, height, z) for Build.bone."""
    p = world_pt(W, bone, local)
    return (float(p[0]), float(24 - p[1]), float(p[2]))


# ---------------------------------------------------------------------------------------------------------------------
# Paint helpers shared by both bodies

def rest_origin(rig):
    """Rest-pose world position of every cube's local origin corner (to line folds up across stacked cubes)."""
    W = fk(rig, {})
    out = {}
    for b in rig.bones:
        M, T = W[b.name]
        rotated = not np.allclose(M, np.eye(3))
        for c in b.cubes:
            p = M @ np.array((c.x, c.y, c.z)) + T
            out[c.name] = (p, rotated)
    return out


def p_folds(origins, period=5.0, depth=1.0, seed=0, deep=0.0):
    """Robe folds hanging with gravity: lit ridges, darker troughs, in value steps. North/south faces follow world x
    and west/east faces world z, so folds run on across cubes stacked on one another; tops follow x (laps)."""
    def prof(u):
        a = math.cos(2 * math.pi * (u + seed * 0.37) / period)
        b = math.cos(2 * math.pi * (u * 0.53 + seed * 1.7) / period)
        v = 0.65 * a + 0.35 * b
        if v > 0.45:
            return 0.5
        if v < -0.55:
            return -1.0 - deep
        if v < -0.15:
            return -0.4
        return 0.0

    def f(face, i, j):
        if face.name == "bottom":
            return 0.0
        o, rotated = origins.get(face.cube.name, ((0, 0, 0), True))
        if face.name in ("north", "south", "top"):
            u = (o[0] + i) if not rotated else i
        else:
            u = (o[2] + i) if not rotated else i
        return prof(u) * depth
    return f


def vein(face, rng, col, length=None, wander=0.45):
    """One meandering vein across a face (marble): a soft line, not noise."""
    if face.w < 4 or face.h < 4:
        return
    x = rng.uniform(0, face.w - 1)
    y = 0.0
    dx = rng.uniform(-0.8, 0.8)
    n = length or int(face.h * rng.uniform(0.6, 1.2))
    for _ in range(n):
        face.set(int(x), int(y), col)
        y += 1
        dx = max(-1.0, min(1.0, dx + rng.uniform(-wander, wander)))
        x += dx
        if not (0 <= x < face.w and 0 <= y < face.h):
            break


def crack(face, rng, dark, lit, length=None, start=None):
    """A crack: a dark line with a lit lower-right lip, wandering down a face."""
    if face.w < 3 or face.h < 3:
        return
    x, y = start if start else (rng.randrange(1, face.w - 1), 0)
    n = length or rng.randint(face.h // 2, face.h)
    for k in range(n):
        if not (0 <= x < face.w and 0 <= y < face.h):
            break
        face.set(x, y, dark)
        if x + 1 < face.w:
            face.set(x + 1, y, lit)
        y += 1
        if rng.random() < 0.3:
            x += rng.choice((-1, 1))


# ---------------------------------------------------------------------------------------------------------------------
# The Seated One's paint

MARBLE = Ramp("#3c3631", "#574f48", "#746b62", "#91877c", "#ada397", "#c6bdb0", "#dad2c5", "#e9e3d7", "#f5f1e8")
GOLD = Ramp("#2a1d07", "#46320f", "#6a4c17", "#906c22", "#b48a30", "#d0a644", "#e6c264", "#f4dd96")
EYE_CORE = hexc("#fffef4")
EYE_HOT = hexc("#ffe9a8")
EYE_GOLD = hexc("#ffbe46")


def paint_the_seated_one(r, P):
    rng = P.rng
    org = rest_origin(r)
    robe = ("hips", "thigh_l", "kneedrape_l", "robe_l", "drape_l", "fold", "abdomen", "ribs", "pecs", "traps", "cape",
            "shoulder_l", "uarm_l", "sleeve_l")
    weather = p_blotch("seated-wear", 9, 0.22)
    for k, nm in enumerate(robe):
        deep = 0.4 if nm in ("robe_l", "kneedrape_l", "fold", "drape_l") else 0.0
        P.paint(nm, MARBLE, 5.2, combine(p_folds(org, 5.0 if nm not in ("sleeve_l", "uarm_l") else 3.5, 1.0, k, deep), weather),
                grad=1.1, rim=0.7, ao=0.9)
    # the fold between the legs is deep shadow, lit only at its lip
    P.paint("fold", MARBLE, 2.8, lambda f, i, j: 0.8 if j == 0 else 0.0, grad=0.6)
    # skin: smooth ivory, softly graded; veins and a few cracks
    skin = ("neck", "jaw", "cranium", "nose", "brow", "ear_l", "forearm_l", "palm_r", "palm_l", "shin_l", "foot_l",
            "thumb_r", "thumbtip_r", "thumb_l")
    for nm in skin:
        P.paint(nm, MARBLE, 5.8, p_blotch("seated-skin" + nm, 7, 0.12), grad=0.9, rim=0.5, ao=0.6)
    for c in r.cubes:
        if c.name.startswith(("finger", "fingertip")) and P.own(c):
            P.paint(c, MARBLE, 5.4, lambda f, i, j: -0.6 if f.name in SIDES and i == f.w - 1 else 0.0, grad=0.5, rim=0.4, ao=0.5)
    vcol = (hexc("#9a9596"), hexc("#86828a"))
    for nm, n in (("ribs", 2), ("thigh_l", 1), ("robe_l", 1), ("cape", 1), ("abdomen", 1), ("forearm_l", 1), ("shin_l", 1),
                  ("hips", 1), ("shoulder_l", 1)):
        for fname in ("north", "west", "top"):
            f = P.face(nm, fname)
            for _ in range(n):
                if rng.random() < 0.7:
                    vein(f, rng, vcol[rng.randrange(2)])
    # a few cracks of age, only on cubes that are not mirrored (a mirrored crack would show twice)
    for nm, fname, n in (("ribs", "north", 9), ("hips", "west", 6), ("abdomen", "north", 5), ("cape", "west", 10)):
        crack(P.face(nm, fname), rng, MARBLE.at(2.6), MARBLE.at(7.0), length=n)
    # the face: forehead, the brow's shadow, closed lids, cheekbones, a calm closed mouth (no teeth)
    f = P.face("cranium", "north")
    for i, j in f.each():
        v = 6.0 - 0.12 * j
        if j in (6, 7) and i in (1, 10):
            v -= 0.7  # the eye's outer corner in soft shadow
        if j == 5:
            v -= 0.5
        if j == 8 and i in (2, 3, 8, 9):
            v += 0.6  # cheekbones
        if j == 9 and i in (1, 2, 9, 10):
            v -= 0.6
        if i in (0, 11):
            v -= 0.8
        f.set(i, j, MARBLE.at(v))
    f = P.face("jaw", "north")
    for i, j in f.each():
        v = 5.3
        if j == 0 and i in (4, 5):
            v -= 0.6  # under the nose, the upper lip turns down
        if j == 1:
            v = {3: 4.6, 4: 3.2, 5: 3.2, 6: 4.6}.get(i, 5.2)  # the mouth: a short soft line, closed
        if j == 2 and i in (4, 5):
            v += 0.8  # the lower lip catches light
        if j == 2 and i in (3, 6):
            v -= 0.3
        if j == 3:
            v = 5.8 if i in (3, 4, 5, 6) else 4.2
        if i in (0, 9):
            v -= 0.9
        f.set(i, j, MARBLE.at(v))
    for fname in ("west", "east"):  # the jaw line
        f = P.face("jaw", fname)
        for i, j in f.each():
            f.set(i, j, MARBLE.at(4.6 - 0.4 * j))
    f = P.face("nose", "north")
    for i, j in f.each():
        f.set(i, j, MARBLE.at(6.4 if j < 4 else 5.4) if i == 0 else MARBLE.at(5.6 if j < 4 else 4.6))
    for i, j in P.face("nose", "bottom").each():
        P.face("nose", "bottom").set(i, j, MARBLE.at(3.0))
    for i, j in P.face("brow", "north").each():
        P.face("brow", "north").set(i, j, MARBLE.at(6.6 if i not in (0, 9) else 5.6))
    for i, j in P.face("brow", "bottom").each():
        P.face("brow", "bottom").set(i, j, MARBLE.at(3.8))
    # closed lids: smooth stone, a soft lash line at the bottom
    f = P.face("lid_l", "north")
    for i, j in f.each():
        f.set(i, j, MARBLE.at(6.4 if j == 0 else (4.0 if i == 1 else 5.4)))
    for fname in ("west", "east", "top", "bottom", "south"):
        for i, j in P.face("lid_l", fname).each():
            P.face("lid_l", fname).set(i, j, MARBLE.at(4.4))
    # the eyes under the lids: dark gold stone; their glow is the only bright thing on the glow layer
    for fname in ("top", "bottom", "west", "east", "south", "north"):
        for i, j in P.face("eye_l", fname).each():
            P.face("eye_l", fname).set(i, j, GOLD.at(2.0 if fname != "north" else (5.0 if i == 1 else 3.2)))
    g = P.face("eye_l", "north", glow=True)
    for i, j in g.each():
        g.set(i, j, EYE_CORE if i == 1 else (EYE_HOT if j == 0 else EYE_GOLD))
    # ears and hair
    P.paint("ear_l", MARBLE, 5.0, lambda f, i, j: (-1.2 if f.name in ("west", "east") and 1 <= j <= f.h - 3 and i == 1 else 0.0))
    P.paint("hair", MARBLE, 4.4, lambda f, i, j: (-0.9 if f.name in SIDES and i % 2 == 1 else 0.3 if f.name in SIDES and i % 4 == 0 else 0.0),
            grad=1.2)
    # the feet: sandals with gold straps
    f_strap = lambda f, i, j: (GOLD.at(5.4 if f.name != "bottom" else 2) if (f.name in SIDES and j in (1, f.h - 1)) or f.name == "bottom" else None)
    P.paint("foot_l", MARBLE, 5.4, lambda f, i, j: (f_strap(f, i, j) or 0.0) if f_strap(f, i, j) else (0.6 if f.name == "top" and i % 2 == 0 and j < 3 else 0.0))
    # gold leaf: the hems (a running lozenge band), the sash, the collar of beads and drops, cuffs, band, halo
    def gold_paint(nm, base=4.6, pattern=None, wear=0.12):
        def pat(f, i, j):
            v = pattern(f, i, j) if pattern else 0.0
            if v is None or isinstance(v, tuple):
                return v
            hv = _h(nm, f.name, i, j) % 1000
            if f.name in SIDES and (j == 0 or i in (0, f.w - 1)) and hv < wear * 1000:
                return MARBLE.at(3.6)  # the leaf worn off an edge
            return v
        P.paint(nm, GOLD, base, pat, grad=1.0, rim=0.9, ao=1.0)

    def lozenge(f, i, j):
        if f.name not in SIDES:
            return 0.0
        h = f.h
        if j == 0:
            return 0.8
        if j == h - 1:
            return -1.4
        mid = (h - 1) / 2.0
        d = abs((i % 6) - 2.5) + abs(j - mid) * 1.2
        return 0.9 if d < 1.3 else (-1.0 if d < 2.0 else 0.0)
    for nm in ("hem_l",):
        gold_paint(nm, 4.4, lozenge)
    gold_paint("sash", 4.2, lambda f, i, j: (0.7 if j in (0, 3) else (-1.0 if j == 4 else (-0.4 if i % 4 == 0 else 0.2))) if f.name in SIDES else 0.0)
    gold_paint("collar_in", 4.8, lambda f, i, j: (0.9 if j == 0 else (-0.9 if i % 2 == 0 and j in (2, 3) else 0.3)) if f.name in SIDES else 0.4)

    def beads(f, i, j):
        if f.name == "top":
            return 0.9 if (i + j) % 3 == 0 else 0.2
        if f.name not in SIDES:
            return 0.0
        if j == 0:
            return 0.9
        if j == 1:
            return -1.2
        if j == 2:
            return 0.8 if i % 2 == 0 else -0.4
        if j == 3:
            return -1.1 if i % 3 == 0 else 0.4
        return 1.0 if i % 3 == 1 else (-1.6 if i % 3 == 0 else -0.2)  # hanging drops
    gold_paint("collar_out", 4.6, beads, wear=0.05)

    def sunburst(f, i, j):
        if f.name not in ("north", "south"):
            return 0.0
        cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
        d = math.hypot(i - cx, j - cy)
        return 1.4 if d < 1.0 else (-1.2 if d < 2.0 else (0.6 if d < 2.8 else 0.0))
    gold_paint("pendant", 4.6, sunburst, wear=0.0)
    gold_paint("cuff_l", 4.6, lambda f, i, j: (0.8 if j in (0,) else -0.9 if j == f.h - 1 else 0.0) if f.name in SIDES else (0.8 if i in (0, f.w - 1) else 0.0))
    gold_paint("band", 4.8, lambda f, i, j: (0.9 if j == 0 else -1.0 if j == f.h - 1 else (1.3 if f.name == "north" and i in (6,) else -0.3 if i % 3 == 0 else 0.2)) if f.name in SIDES else 0.0)
    for c in r.cubes:
        if c.name.startswith(("ray", "tip", "hray", "htip", "ring")) and P.own(c):
            gold_paint(c.name, 5.0 if c.name.startswith(("ray", "tip")) else 4.8,
                       lambda f, i, j: (0.6 if f.name == "north" and i == 0 else (-0.5 if f.name == "north" and i == f.w - 1 and f.w > 1 else 0.0)),
                       wear=0.0)
    # gold leaf on the robe: hem lines on the sleeves, shoulders, cape and the knees' edges
    def hem_rows(nm, rows, fnames=SIDES):
        for fname in fnames:
            f = P.face(nm, fname)
            for i, j in f.each():
                if j in rows(f):
                    f.set(i, j, GOLD.at(4.8 + (0.8 if j == min(rows(f)) else -0.6)))
    hem_rows("sleeve_l", lambda f: (f.h - 2, f.h - 1))
    hem_rows("shoulder_l", lambda f: (f.h - 1,))
    hem_rows("cape", lambda f: (f.h - 2, f.h - 1))
    hem_rows("drape_l", lambda f: (f.h - 2,))
    for fname in ("west", "east"):  # the cape's border runs down its sides
        f = P.face("cape", fname)
        for i, j in f.each():
            f.set(i, j, GOLD.at(4.2 - 0.02 * j))
    # the faint glyph on the right palm (glow): a ring with four rays, seen when the hand is raised
    g = P.face("palm_r", "bottom", glow=True)
    cx, cy = (g.w - 1) / 2, (g.h - 1) / 2
    for i, j in g.each():
        d = math.hypot(i - cx, j - cy)
        if 1.6 <= d <= 2.5 or (d < 0.8):
            g.set(i, j, hexc("#ffd27a", 150))
        elif (abs(i - cx) < 0.6 or abs(j - cy) < 0.6) and 2.5 < d <= 3.6:
            g.set(i, j, hexc("#ffd27a", 110))
    f = P.face("palm_r", "bottom")
    for i, j in f.each():
        d = math.hypot(i - cx, j - cy)
        f.set(i, j, MARBLE.at(4.4 if 1.6 <= d <= 2.5 else 5.0))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Seated One's clips. Story order: dormant -> wake -> awake (gaze) -> raise -> close -> sleep (-> dormant).

LIDS_OPEN = 95.0  # the lids fold up into the brow
DORMANT = {"neck": {"rot": (3, 0, 0)}, "head": {"rot": (9, 0, 0)}}
AWAKE = {"lid_l": {"rot": (LIDS_OPEN, 0, 0)}, "lid_r": {"rot": (LIDS_OPEN, 0, 0)}}
# The raised hand: the palm's centre in front of and above the face, turned up and out toward the hall
RAISE_PALM = (-2.0, 79.0, -21.0)  # x, height, z
RAISE_NORMAL = (0.08, -0.55, -0.83)  # the palm faces the players and the vault (model space, y down)
RAISE_FINGERS = (-0.2, -0.83, 0.52)  # fingers up, a little back and away from the face
ARM_R = ["arm_r", "forearm_r", "hand_r"]
FINGERS_R = ["finger1_r", "finger2_r", "finger3_r", "finger4_r"]
TIPS_R = ["fingertip1_r", "fingertip2_r", "fingertip3_r", "fingertip4_r"]
# finger offsets from the rest curl (fingers_r rests at 55 degrees, tips at 20)
HAND_REST = {}
HAND_OPEN = {"fingers_r": {"rot": (-66, 0, 0)}, **{t: {"rot": (-24, 0, 0)} for t in TIPS_R},
             **{f: {"rot": (0, a, 0)} for f, a in zip(FINGERS_R, (-17, -6, 6, 18))},
             "thumb_r": {"rot": (-14, 22, 0)}, "thumbtip_r": {"rot": (0, -10, 0)}}
HAND_FIST = {"fingers_r": {"rot": (42, 0, 0)}, **{t: {"rot": (78, 0, 0)} for t in TIPS_R},
             **{f: {"rot": (0, a, 0)} for f, a in zip(FINGERS_R, (-2, 0, 1, 3))},
             "thumb_r": {"rot": (30, -46, 0)}, "thumbtip_r": {"rot": (0, -40, 0)}}


def _h2y(p):
    return np.array((p[0], 24.0 - p[1], p[2]), dtype=float)


def _elbow_for(S, P, l1, l2, pole):
    """Where the elbow goes for a shoulder S and a hand P: on the reach circle, toward the pole direction."""
    d = P - S
    dist = np.linalg.norm(d)
    u = d / dist
    dist = min(dist, l1 + l2 - 1e-3)
    a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist)
    h = math.sqrt(max(0.0, l1 * l1 - a * a))
    pv = np.asarray(pole, dtype=float)
    pv = pv - u * np.dot(pv, u)
    pv /= np.linalg.norm(pv)
    return S + u * a + pv * h


def _arm_reach(r, off, palm, pole=(-0.45, 1.0, -0.15)):
    """Shoulder and elbow angles that put hand_r's pivot (the palm's centre) on `palm` (model space), the elbow hanging
    toward `pole` (model space: +y is down) so the arm reads as lifted, never flung out."""
    W = fk(r, to_sample(off), only=["hand_r"])
    S, E0, P0 = W["arm_r"][1], W["forearm_r"][1], W["hand_r"][1]
    l1, l2 = np.linalg.norm(E0 - S), np.linalg.norm(P0 - E0)
    E = _elbow_for(S, np.asarray(palm, dtype=float), l1, l2, pole)
    vars_ = [("arm_r", 0, -220, 60), ("arm_r", 1, -90, 90), ("arm_r", 2, -60, 90), ("forearm_r", 0, -160, 30), ("forearm_r", 1, -70, 70)]
    goals = [g_point("hand_r", (0, 0, 0), palm, 1.0), g_point("forearm_r", (0, 0, 0), E, 0.8)]
    prefer = {("forearm_r", 1): (0, 0.5), ("arm_r", 1): (0, 0.3)}
    out = ik(r, off, vars_, goals, prefer=prefer, chain=["hand_r"], iters=100, lam=0.3)
    W = fk(r, to_sample(out), only=["hand_r"])
    err = np.linalg.norm(W["hand_r"][1] - np.asarray(palm))
    if err > 0.4:
        raise SystemExit(f"the_seated_one: the arm cannot reach {palm} (off by {err:.2f} px)")
    return {b: dict(out.get(b, {})) for b in ("arm_r", "forearm_r")}


def _sleeve(r, off, drag=0.85):
    """The right sleeve hangs from the elbow whatever the arm does (drag 1 = straight down)."""
    W = fk(r, to_sample(off), only=["arm_r"])
    Ma = W["arm_r"][0]
    Mw = slerp_m(Ma, np.eye(3), drag)
    rot, _ = local_rot_for(r, off, "sleeve_r", Mw)
    return rot


def _unwrap(prev, cur):
    if prev is None:
        return cur
    out = []
    for a, b in zip(prev, cur):
        while b - a > 180:
            b -= 360
        while b - a < -180:
            b += 360
        out.append(b)
    return tuple(out)


def spline(keys, t):
    """Cubic Hermite through (time, vector) keys: Catmull tangents inside, flat at both ends (eases in and out)."""
    ts = [k[0] for k in keys]
    vs = [np.asarray(k[1], dtype=float) for k in keys]
    if t <= ts[0]:
        return vs[0]
    if t >= ts[-1]:
        return vs[-1]
    i = max(k for k in range(len(ts) - 1) if ts[k] <= t)
    t0, t1 = ts[i], ts[i + 1]
    h = t1 - t0
    u = (t - t0) / h

    def tan(k):
        if k == 0 or k == len(ts) - 1:
            return vs[k] * 0
        return (vs[k + 1] - vs[k - 1]) / (ts[k + 1] - ts[k - 1])
    m0, m1 = tan(i) * h, tan(i + 1) * h
    h00, h10, h01, h11 = 2 * u ** 3 - 3 * u ** 2 + 1, u ** 3 - 2 * u ** 2 + u, -2 * u ** 3 + 3 * u ** 2, u ** 3 - u ** 2
    return h00 * vs[i] + h10 * m0 + h01 * vs[i + 1] + h11 * m1


def _hand_frames(r):
    W = fk(r, {})
    rest = W["hand_r"][0].copy()
    n = np.array(RAISE_NORMAL) / np.linalg.norm(RAISE_NORMAL)
    f = np.array(RAISE_FINGERS) - n * np.dot(RAISE_FINGERS, n)
    f /= np.linalg.norm(f)
    top = np.column_stack((np.cross(n, -f), n, -f))
    # lifted off the knee, the hand trails: fingers hanging down and forward, palm toward the body
    fm = np.array((0.1, 0.85, -0.5))
    fm /= np.linalg.norm(fm)
    nm = np.array((0.0, 0.5, 0.85))
    nm = nm - fm * np.dot(nm, fm)
    nm /= np.linalg.norm(nm)
    mid = np.column_stack((np.cross(nm, -fm), nm, -fm))
    # rising past the chest: the wrist turns, the palm faces in toward the face, fingers up
    fu = np.array((0.15, -0.9, -0.35))
    fu /= np.linalg.norm(fu)
    nu = np.array((0.85, 0.1, 0.5))
    nu = nu - fu * np.dot(nu, fu)
    nu /= np.linalg.norm(nu)
    turn = np.column_stack((np.cross(nu, -fu), nu, -fu))
    return rest, mid, turn, top


def _hand_at(frames, keys, t):
    """World rotation of the hand at time t: slerps between (time, frame) keys with an ease per segment."""
    if t <= keys[0][0]:
        return frames[keys[0][1]]
    for (ta, a), (tb, b) in zip(keys, keys[1:]):
        if t <= tb:
            return slerp_m(frames[a], frames[b], ease(span(t, ta, tb)))
    return frames[keys[-1][1]]


def _hand_local(r, off, Mworld, prev=None):
    rot, _ = local_rot_for(r, off, "hand_r", Mworld)
    return _unwrap(prev, rot)


def seated_clips(r):
    r.clips.clear()
    # -- dormant: a statue, perfectly still
    c = r.clip("dormant", 4.0, loop=True)
    bake(c, lambda t: DORMANT, [0.0, 4.0])
    # -- wake: the stone breaks free, the lids lift, the head rises; a first breath lifts the shoulders
    def wake(t):
        jolt = 0.9 * math.exp(-((t - 0.42) / 0.06) ** 2)
        hr = 9.0 * (1 - ease5(span(t, 0.55, 2.75)))
        settle = -0.7 * math.sin(math.pi * span(t, 2.55, 3.0))
        nk = 3.0 * (1 - ease5(span(t, 0.45, 2.5)))
        if t < 1.05:
            lid = 19.0 * ease(span(t, 0.6, 1.05))
        elif t < 1.35:
            lid = 19.0 + 1.5 * math.sin(math.pi * span(t, 1.05, 1.35))
        else:
            lid = 19.0 + (LIDS_OPEN - 19.0) * ease_out(span(t, 1.35, 2.4), 2.2)
        breath = math.sin(math.pi * span(t, 1.2, 2.9)) ** 2
        tremor = 0.25 * math.sin(t * 61.0) * math.exp(-((t - 0.45) / 0.2) ** 2)
        return {"head": {"rot": (hr + jolt + settle + tremor, 0, 0)}, "neck": {"rot": (nk, 0, 0)},
                "lid_l": {"rot": (lid, 0, 0)}, "lid_r": {"rot": (lid, 0, 0)},
                "chest": {"rot": (-1.1 * breath, 0, 0), "scale": (1 + 0.012 * breath, 1, 1 + 0.012 * breath)},
                "arm_l": {"pos": (0, 0.55 * breath, 0)}, "arm_r": {"pos": (0, 0.55 * breath, 0)},
                "body": {"pos": (0, 0, 0)}}
    bake(r.clip("wake", 3.0), wake, times(3.0, 0.05))
    # -- awake: still a statue, but it breathes (the faintest rise of the chest and shoulders)
    def awake(t):
        b = (1 - math.cos(2 * math.pi * t / 5.0)) / 2
        return merge(AWAKE, {"chest": {"rot": (-0.5 * b, 0, 0), "scale": (1 + 0.005 * b, 1, 1 + 0.006 * b)},
                             "arm_l": {"pos": (0, 0.22 * b, 0)}, "arm_r": {"pos": (0, 0.22 * b, 0)},
                             "head": {"rot": (0.4 * b, 0, 0)}})
    bake(r.clip("awake", 5.0, loop=True), awake, times(5.0, 0.5))
    # -- gaze: it leans into its gaze, shoulders tense, the hands grip the knees; a push when it fires; returns
    def gaze(t):
        lean = ease5(span(t, 0.0, 0.55)) * (1 - ease5(span(t, 1.62, 2.3)))
        hold = span(t, 0.55, 1.5)
        fire = math.exp(-((t - 1.56) / 0.06) ** 2)
        trem = 0.18 * math.sin(t * 47.0) * (span(t, 0.6, 0.9) * (1 - span(t, 1.45, 1.55)))
        grip = lean
        out = merge(AWAKE, {"neck": {"rot": (4.0 * lean, 0, 0)},
                            "head": {"rot": (5.0 * lean + 1.2 * hold * lean + 2.6 * fire + trem, 0, 0), "pos": (0, -0.3 * lean, -0.9 * lean - 0.4 * fire)},
                            "chest": {"rot": (2.4 * lean + 0.8 * fire, 0, 0)},
                            "arm_l": {"pos": (0, 0.5 * lean, -0.4 * lean), "rot": (0, 0, -1.2 * lean)},
                            "arm_r": {"pos": (0, 0.5 * lean, -0.4 * lean), "rot": (0, 0, 1.2 * lean)},
                            "fingers_l": {"rot": (7 * grip, 0, 0)}, "fingers_r": {"rot": (7 * grip, 0, 0)}})
        return out
    bake(r.clip("gaze", 2.3), gaze, times(2.3, 0.05))
    # -- raise: heavy, slow, unstoppable. The hand presses into the knee and peels off it, the arm lifts in a long
    #    arc while the body leans away to carry it, the wrist turns past the face, the palm opens to the hall
    frames = dict(zip(("rest", "mid", "turn", "top"), _hand_frames(r)))
    W0 = fk(r, {})
    p_rest = W0["hand_r"][1]

    def lean_body(t):
        lean = ease5(span(t, 0.25, 2.4))
        return {"chest": {"rot": (-0.9 * lean, 0, 2.6 * lean)}, "neck": {"rot": (0, 0, -1.4 * lean)},
                "head": {"rot": (-0.8 * lean, 0, -1.2 * lean)}, "arm_l": {"pos": (0, -0.3 * lean, 0)}}
    top = _h2y(RAISE_PALM)
    reach = [  # (time, palm target, elbow pole): lift off the knee, rise before the chest, before the face, arrive
        (1.15, p_rest + np.array((-1.0, -8.0, -6.5)), (-0.2, 1.0, 0.3)),
        (1.9, _h2y((-9.0, 62.0, -27.0)), (-0.5, 1.0, 0.0)),
        (2.55, top + np.array((0.0, -1.2, -0.6)), (-0.45, 1.0, -0.15)),
        (3.0, top, (-0.45, 1.0, -0.15))]
    arm_keys = [(0.32, {"arm_r": (0, 0, 0), "forearm_r": (0, 0, 0)})]
    for tk, target, pole in reach:
        sol = _arm_reach(r, merge(AWAKE, lean_body(tk), {b: {"rot": arm_keys[-1][1][b]} for b in ("arm_r", "forearm_r")}), target, pole)
        arm_keys.append((tk, {b: _unwrap(arm_keys[-1][1][b], sol[b].get("rot", (0, 0, 0))) for b in ("arm_r", "forearm_r")}))
    hand_keys = [(0.3, "rest"), (1.2, "mid"), (2.05, "turn"), (2.75, "top")]

    def arm_at(keys, t):
        return {b: tuple(spline([(tk, v[b]) for tk, v in keys], t)) for b in ("arm_r", "forearm_r")}
    prev_hand = [None]

    def raise_pose(t):
        press = math.sin(math.pi * span(t, 0.0, 0.42))
        base = merge(AWAKE, lean_body(t))
        arm = arm_at(arm_keys, t)
        out = merge(base, {b: {"rot": arm[b]} for b in arm})
        out["arm_r"]["pos"] = (0, -0.45 * press, 0)
        Mh = _hand_at(frames, hand_keys, t)
        hl = _hand_local(r, out, Mh, prev_hand[0]) if t > 0.3 else (0.0, 0.0, 0.0)
        prev_hand[0] = hl
        fo = ease(span(t, 0.4, 1.2)) * 0.55 + ease5(span(t, 1.85, 2.8)) * 0.45
        hand = lerp_off(HAND_REST, HAND_OPEN, fo)
        out = merge(out, {"hand_r": {"rot": hl}}, hand)
        out["sleeve_r"] = {"rot": _sleeve(r, out, 0.8 * ease(span(t, 0.5, 2.3)))}
        return out
    bake(r.clip("raise", 3.0), raise_pose, times(3.0, 0.1))
    RAISED = raise_pose(3.0)
    # -- close: the fingers snap into a fist; the arm drops a few degrees with the force; a tremor in the fist
    def close(t):
        ant = math.sin(math.pi * span(t, 0.0, 0.12))
        snap = ease_in(span(t, 0.1, 0.24), 1.6)
        drop = 5.5 * ease_out(span(t, 0.16, 0.32), 2) - 1.6 * math.sin(math.pi * span(t, 0.32, 0.6))
        trem = 1.4 * math.sin(t * 70.0) * span(t, 0.24, 0.3) * (1 - span(t, 0.3, 0.75))
        hand = lerp_off(HAND_OPEN, HAND_FIST, snap)
        hand = merge(hand, {"fingers_r": {"rot": (-7 * ant, 0, 0)}, **{f: {"rot": (0, a * ant, 0)} for f, a in zip(FINGERS_R, (-5, -2, 2, 5))}})
        base = {k: dict(v) for k, v in RAISED.items() if k not in HAND_OPEN}
        out = merge(base, hand, {"arm_r": {"rot": (drop, 0, 0)}, "forearm_r": {"rot": (drop * 0.6, 0, 0)},
                                 "hand_r": {"rot": (0, 0, trem)},
                                 "chest": {"rot": (1.4 * math.sin(math.pi * span(t, 0.18, 0.7)), 0, 0)}})
        out["sleeve_r"] = {"rot": _sleeve(r, out, 0.8)}
        return out
    bake(r.clip("close", 0.8), close, times(0.8, 0.04))
    CLOSED = close(0.8)
    # -- sleep: the lids close, the head bows, the spent arm sinks back down the same arc and the fist opens on the knee
    closed_arm = {b: tuple(CLOSED[b]["rot"]) for b in ("arm_r", "forearm_r")}
    down_keys = [(0.15, closed_arm)] + [(0.15 + (3.0 - tk) * 0.62, v) for tk, v in reversed(arm_keys[1:-2])] + [(2.0, arm_keys[0][1])]
    hand_down = [(0.15, "fist"), (0.75, "turn"), (1.35, "mid"), (1.95, "rest")]
    W_c = fk(r, to_sample(CLOSED), only=["hand_r"])
    frames2 = dict(frames)
    frames2["fist"] = W_c["hand_r"][0]
    prev_hand2 = [None]

    def sleep(t):
        lid = LIDS_OPEN * (1 - ease(span(t, 0.0, 0.9)))
        bow = ease5(span(t, 0.3, 2.0))
        down = ease5(span(t, 0.15, 2.0))
        body = lerp_off({k: CLOSED[k] for k in ("chest", "neck", "head", "arm_l") if k in CLOSED}, {}, down)
        base = merge(body, {"lid_l": {"rot": (lid, 0, 0)}, "lid_r": {"rot": (lid, 0, 0)}},
                     {"neck": {"rot": (3 * bow, 0, 0)}, "head": {"rot": (9 * bow, 0, 0)}})
        arm = arm_at(down_keys, t)
        out = merge(base, {b: {"rot": arm[b]} for b in arm})
        Mh = _hand_at(frames2, hand_down, t)
        hl = _hand_local(r, out, Mh, prev_hand2[0]) if t < 1.95 else (0.0, 0.0, 0.0)
        if t >= 1.8:
            hl = tuple(v * (1 - span(t, 1.8, 1.95)) for v in hl) if prev_hand2[0] is not None else hl
        prev_hand2[0] = hl
        relax = ease5(span(t, 1.0, 1.95))
        hand = lerp_off({k: CLOSED[k] for k in HAND_FIST if k in CLOSED}, HAND_REST, relax)
        out = merge(out, {"hand_r": {"rot": hl}}, hand)
        out["sleeve_r"] = {"rot": _sleeve(r, out, 0.8 * (1 - ease(span(t, 0.9, 2.0))))}
        return out
    bake(r.clip("sleep", 2.0), sleep, times(2.0, 0.1))
    # -- what HeroRig's checks ask of every rig: idle and walk (rest), hurt (a stone shudder), death (dormant)
    r.clip("idle", 4.0, loop=True).rot("root", [(0, 0, 0, 0), (4.0, 0, 0, 0)])
    r.clip("walk", 4.0, loop=True).rot("root", [(0, 0, 0, 0), (4.0, 0, 0, 0)])
    hurt = r.clip("hurt", 0.4)
    hurt.rot("chest", [(0, 0, 0, 0), (0.05, 0.6, 0, -0.5), (0.12, -0.4, 0, 0.4), (0.2, 0.2, 0, -0.2), (0.4, 0, 0, 0)])
    bake(r.clip("death", 1.0), lambda t: DORMANT, [0.0, 1.0])
    return r


# =====================================================================================================================
# The ritual statue: a hooded temple guardian carved from black basalt, cracked, trimmed in aged gold (hood edge,
# collar, belt, hems), faceless under the hood but for two eye slits; a long stone glaive in its right hand
# =====================================================================================================================

GLAIVE_GRIP = 17.0  # px from the butt to the right hand
GLAIVE_HAND_L = 9.0  # the left hand's place on the shaft, toward the butt from the right hand
HAND_PT = (0.0, 10.5, 0.0)  # the fist's centre in a forearm's frame (the glaive's pivot)
ANKLE_PT = (0.0, 9.0, 0.0)  # the ankle in a shin's frame (the foot's pivot)


def ritual_statue():
    r = Rig("ritual_statue", (128, 128))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 1.2, 2.4, (0.2, 0.3), 0.9
    B = Build(r)
    root = B.bone(None, "root", (0, 0, 0))
    body = B.bone(root, "body", (0, 22, 0))
    B.box(body, "pelvis", -6, 6, 19, 25, -3.5, 3.5)
    B.box(body, "belt", -6.5, 6.5, 23, 25, -4, 4)
    B.box(body, "buckle", -1.5, 1.5, 22, 26, -5, -4)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        S = (lambda n: None) if side > 0 else (lambda n: n)

        def xs(a, b):
            return (a, b) if side > 0 else (-b, -a)
        leg = B.bone(body, "leg_" + nm, (side * 3, 21, 0))
        B.box(leg, "thigh_" + nm, *xs(1, 5), 12, 21, -2, 2, mirror=mir, share=S("thigh_l"))
        shin = B.bone(leg, "shin_" + nm, (side * 3, 12, 0))
        B.box(shin, "shin_" + nm, *xs(1, 5), 3, 12, -2, 2, mirror=mir, share=S("shin_l"))
        foot = B.bone(shin, "foot_" + nm, (side * 3, 3, 0))
        B.box(foot, "foot_" + nm, *xs(0.5, 5.5), 0, 3, -5, 2, mirror=mir, share=S("foot_l"))
        # the robe: a front panel over each leg (thigh, then shin), two side panels, a long back panel
        # the front of the robe rides each leg: a panel over the thigh, and one that hangs from the knee
        f1 = B.bone(leg, "skirt_f" + nm, (side * 3, 21, 0))
        B.box(f1, "skirt_f" + nm, *xs(0, 7), 12, 24, -5, -3, mirror=mir, share=S("skirt_fl"))
        f2 = B.bone(f1, "skirt_f" + nm + "2", (side * 3.5, 12, -4))
        B.box(f2, "skirt_f" + nm + "2", *xs(-0.5, 7.5), 2, 12, -5.5, -3.5, mirror=mir, share=S("skirt_fl2"))
        s1 = B.bone(body, "skirt_" + nm, (side * 7.5, 22, 0))  # a hip guard: it moves with the thigh
        B.box(s1, "skirt_" + nm, *xs(6.5, 8.5), 12, 24, -4.5, 4.5, mirror=mir, share=S("skirt_l"))
    b1 = B.bone(body, "skirt_b", (0, 24, 4.5))
    B.box(b1, "skirt_b", -7, 7, 13, 24, 3.5, 5.5)
    b2 = B.bone(b1, "skirt_b2", (0, 13, 4.5))
    B.box(b2, "skirt_b2", -7.5, 7.5, 1, 13, 4, 6)
    chest = B.bone(body, "chest", (0, 25, 0))
    B.box(chest, "waist", -6, 6, 25, 30, -3.5, 3.5)
    B.box(chest, "torso", -7.5, 7.5, 29, 38, -4, 4)
    B.box(chest, "mantle", -9.5, 9.5, 34, 39, -5, 5)
    B.box(chest, "cowl", -4.5, 4.5, 31, 39, 4, 6)
    head = B.bone(chest, "head", (0, 38.5, 0))
    B.box(head, "hood", -4.5, 4.5, 37, 46, -3.5, 4.5)
    B.box(head, "brim", -4.5, 4.5, 44, 46, -5.5, -3.5)
    B.box(head, "rim_l", 3.5, 4.5, 37, 44, -5.5, -3.5)
    B.box(head, "rim_r", -4.5, -3.5, 37, 44, -5.5, -3.5, mirror=True, share="rim_l")
    pk = B.bone(head, "hood_peak", (0, 44.0, 2.5), (-45, 0, 0))
    pk.cube("hood_peak", -1.5, -2.0, -1.5, 3, 3, 3)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        S = (lambda n: None) if side > 0 else (lambda n: n)

        def xs(a, b):
            return (a, b) if side > 0 else (-b, -a)
        arm = B.bone(chest, "arm_" + nm, (side * 9.5, 36.5, 0))
        B.box(arm, "shoulder_" + nm, *xs(6.5, 12.5), 33, 39, -3.5, 3.5, mirror=mir, share=S("shoulder_l"))
        B.box(arm, "uarm_" + nm, *xs(7.5, 11.5), 27, 34, -2, 2, mirror=mir, share=S("uarm_l"))
        fa = B.bone(arm, "forearm_" + nm, (side * 9.5, 27.5, 0))
        B.box(fa, "forearm_" + nm, *xs(7.5, 11.5), 19, 28, -2, 2, mirror=mir, share=S("forearm_l"))
        B.box(fa, "cuff_" + nm, *xs(7, 12), 21, 24, -2.5, 2.5, mirror=mir, share=S("cuff_l"))
        B.box(fa, "fist_" + nm, *xs(7.5, 11.5), 15, 19, -2, 2, mirror=mir, share=S("fist_l"))
    # the glaive: butt cap, a long shaft, a gold collar, a broad blade with a back spur and a tip curving forward
    g = B.bone(r.by_name["forearm_r"], "glaive", (-9.5, 17, 0))
    g.cube("butt", -1.5, 15, -1.5, 3, 2, 3)
    g.cube("shaft", -1, -19, -1, 2, 34, 2)
    g.cube("gcollar", -1.5, -22, -1.5, 3, 3, 3)
    g.cube("blade", -0.5, -31, -3.5, 1, 9, 4)
    g.cube("spur", -0.5, -26, 0.5, 1, 3, 3)
    bt = B.bone(g, "blade_tip", tuple(_ah(B.world, "glaive", (0, -31, -1.5))), (14, 0, 0))
    bt.cube("blade_tip", -0.5, -4, -2, 1, 4, 3)
    bt.cube("blade_point", -0.5, -6.5, -1.5, 1, 3, 2)
    r.look = [("head", 1.0, 0.5)]
    r.pack()
    return r


# ---------------------------------------------------------------------------------------------------------------------
# The statue's paint: black basalt with cracks, chips and worn gold

BASALT = Ramp("#060608", "#0c0c10", "#141419", "#1c1c23", "#25252e", "#30303b", "#3d3d4a", "#4e4e5d", "#646474", "#7e7e92")
GOLD_OLD = Ramp("#1e1505", "#3a2a0c", "#5c4414", "#80621e", "#a2802a", "#c09c3a", "#d8b656", "#ecd288")
SLIT = hexc("#ffffff")


def paint_ritual_statue(r, P):
    rng = P.rng
    org = rest_origin(r)
    wear = p_blotch("statue-wear", 7, 0.25)
    cloth = ("skirt_fl", "skirt_fl2", "skirt_l", "skirt_b", "skirt_b2", "mantle", "cowl", "shoulder_l", "torso", "waist",
             "pelvis", "thigh_l")
    for k, nm in enumerate(cloth):
        P.paint(nm, BASALT, 3.4, combine(p_folds(org, 4.0, 1.0, k + 3, 0.3), wear), grad=1.3, rim=1.2, ao=1.0)
    for nm in ("uarm_l", "forearm_l", "fist_l", "shin_l", "foot_l"):
        P.paint(nm, BASALT, 3.5, combine(p_stone(rng, 0.7, 3), wear), grad=1.0, rim=1.2, ao=0.8)
    P.paint("hood", BASALT, 3.6, combine(p_folds(org, 3.0, 0.8, 11), wear), grad=1.1, rim=1.3, ao=0.6)
    P.paint("hood_peak", BASALT, 3.4, wear, grad=0.6, rim=0.8)
    for nm in ("brim", "rim_l"):
        P.paint(nm, BASALT, 3.4, None, grad=0.4, rim=0.9)
    # cracks across the big stone faces, each with a pale broken lip; chips knocked off a few edges
    for nm, fname, n in (("torso", "north", 7), ("mantle", "north", 4), ("skirt_b2", "south", 9), ("skirt_b", "south", 8), ("waist", "west", 4),
                         ("hood", "west", 6), ("pelvis", "south", 5), ("cowl", "south", 6)):
        crack(P.face(nm, fname), rng, BASALT.at(0), BASALT.at(6.5), length=n)
    for c in r.cubes:
        if not P.own(c) or c.name in ("shaft", "blade", "blade_tip", "blade_point", "spur", "butt", "gcollar"):
            continue
        for fname in SIDES:
            f = P.face(c, fname)
            if f.w < 3:
                continue
            for _ in range(1 if f.w * f.h < 40 else 2):
                if rng.random() < 0.5:
                    i = rng.randrange(f.w)
                    f.set(i, 0, BASALT.at(7))
                    if i + 1 < f.w:
                        f.set(i + 1, 0, BASALT.at(6))
    # the face under the hood: a dark recess, deeper toward the top; two narrow eye slits
    f = P.face("hood", "north")
    g = P.face("hood", "north", glow=True)
    for i, j in f.each():
        v = 0.4 + 0.12 * j
        if i in (0, f.w - 1):
            v += 0.4
        f.set(i, j, BASALT.at(v))
    for i in (1, 2, 6, 7):
        f.set(i, 4, hexc("#d8d2c0"))
        g.set(i, 4, SLIT)
    for i in (1, 2, 6, 7):  # a faint brow of shadow over the slits, a lighter cheek below
        f.set(i, 3, BASALT.at(0))
        f.set(i, 5, BASALT.at(1.4))
    for nm in ("brim", "rim_l"):  # the hood's edge in gold, its inside in shadow
        for fname in ("north",):
            ff = P.face(nm, fname)
            for i, j in ff.each():
                ff.set(i, j, GOLD_OLD.at(5.0 if j == 0 else 4.2) if (_h(nm, i, j) % 9) else BASALT.at(4))
        for fname in ("bottom", "south") if nm == "brim" else ("east", "west"):
            ff = P.face(nm, fname)
            for i, j in ff.each():
                ff.set(i, j, BASALT.at(0.6))
    # gold: belt and buckle, the mantle's lower edge, the hems, the cuffs, the shoulders' edge
    def gold(nm, base, pat=None, worn=0.14):
        def pp(f, i, j):
            v = pat(f, i, j) if pat else 0.0
            if f.name in SIDES and (_h(nm, f.name, i, j) % 1000) < worn * 1000:
                return BASALT.at(2.5)
            return v
        P.paint(nm, GOLD_OLD, base, pp, grad=0.9, rim=0.9, ao=1.0)
    gold("belt", 4.2, lambda f, i, j: (0.6 if j == 0 else -0.8) if f.name in SIDES else 0.0)
    gold("buckle", 4.8, lambda f, i, j: (1.0 if (f.name == "north" and 0 < i < f.w - 1 and 0 < j < f.h - 1) else 0.0), worn=0.0)
    gold("cuff_l", 4.4, lambda f, i, j: (0.7 if j == 0 else -0.7 if j == f.h - 1 else 0.0) if f.name in SIDES else 0.0)
    gold("gcollar", 5.0, lambda f, i, j: (0.8 if j == 0 else -0.6) if f.name in SIDES else 0.4, worn=0.0)
    gold("butt", 4.6, None, worn=0.0)

    def hem(nm, rows):
        for fname in SIDES:
            ff = P.face(nm, fname)
            for i, j in ff.each():
                if j in rows(ff) and (_h(nm, fname, i) % 7):
                    ff.set(i, j, GOLD_OLD.at(4.6 if j == min(rows(ff)) else 3.4))
    hem("skirt_fl2", lambda f: (f.h - 2, f.h - 1))
    for nm in ("skirt_fl", "skirt_fl2"):  # a tabard's gold border runs down the robe's opening
        ff = P.face(nm, "north")
        edge = 1 if nm == "skirt_fl2" else 0  # the lower panel flares half a pixel past the upper one
        for j in range(ff.h):
            if _h(nm, j) % 6:
                ff.set(edge, j, GOLD_OLD.at(4.4 if j % 3 else 5.2))
    hem("skirt_l", lambda f: (f.h - 2, f.h - 1))
    hem("skirt_b2", lambda f: (f.h - 2, f.h - 1))
    hem("mantle", lambda f: (f.h - 1,))
    hem("shoulder_l", lambda f: (f.h - 1,))
    hem("cowl", lambda f: (f.h - 1,))
    # the glaive: a dark polished shaft bound in two places; the blade's edge paler, thin cracks of light in it
    P.paint("shaft", BASALT, 3.6, lambda f, i, j: (1.6 if j in (12, 13, 14, 15, 16) and f.name in SIDES and (j + i) % 2 == 0 else
                                                     (0.8 if j in (12, 16) and f.name in SIDES else 0.0)), grad=0.2, rim=0.0, ao=0.0)
    for nm in ("blade", "blade_tip", "blade_point", "spur"):
        P.paint(nm, BASALT, 4.0, lambda f, i, j: (2.6 if f.name in ("west", "east") and ((f.name == "west" and i == 0) or (f.name == "east" and i == f.w - 1)) else
                                                    (2.2 if f.name == "north" else 0.0)), grad=0.6, rim=0.6, ao=0.4)
    for nm, fname in (("blade", "west"), ("blade", "east"), ("blade_tip", "west"), ("blade_tip", "east")):
        gf = P.face(nm, fname, glow=True)
        sf = P.face(nm, fname)
        x = gf.w // 2
        for j in range(gf.h):
            if 0 <= x < gf.w and (j % 4) != 3:
                gf.set(x, j, hexc("#ffffff", 190))
                sf.set(x, j, BASALT.at(7))
            x = max(1, min(gf.w - 1, x + (1 if (j + len(nm)) % 3 == 0 else -1 if (j + len(fname)) % 5 == 0 else 0)))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The statue's poses come from a designed state, solved per sample

def _prev_rot(off, bone):
    return off.get(bone, {}).get("rot")


def _reach(r, off, arm, fore, target, pole, hand=HAND_PT):
    """Two-bone arm, solved exactly: the elbow on its reach circle toward `pole`, the forearm's hinge bending toward
    the hand, so the forearm's `hand` point lands on target (or the arm points straight at it, out of reach)."""
    W = fk(r, to_sample(off), only=[fore])
    b = r.by_name[arm]
    Mp = W[b.parent.name][0]
    S = W[arm][1]
    E0 = W[fore][1]
    l1 = float(np.linalg.norm(E0 - S))
    l2 = float(np.linalg.norm(np.asarray(hand)))
    P = np.asarray(target, dtype=float)
    E = _elbow_for(S, P, l1, l2, pole)
    y = (E - S) / np.linalg.norm(E - S)
    v = P - E
    vp = v - y * np.dot(v, y)
    if np.linalg.norm(vp) < 1e-4:
        vp = np.asarray(pole, dtype=float)
        vp = -(vp - y * np.dot(vp, y))
    z = -vp / np.linalg.norm(vp)
    x = np.cross(y, z)
    Marm = np.column_stack((x, y, z))
    # the arm's own rest rotation is zero for the statue; offsets are its local Euler angles
    off = dict(off)
    off[arm] = dict(off.get(arm, {}))
    off[arm]["rot"] = euler_near(Mp.T @ Marm, _prev_rot(off, arm))
    cosb = float(np.clip(np.dot(y, v / max(1e-6, np.linalg.norm(v))), -1.0, 1.0))
    off[fore] = dict(off.get(fore, {}))
    off[fore]["rot"] = (-math.degrees(math.acos(cosb)), 0.0, 0.0)
    return off


def _leg(r, off, nm, ankle):
    """Two-bone leg, solved exactly in the hip's frame: a sideways swing (z) to meet the ankle's x, then thigh and
    knee (the knee always bends forward)."""
    W = fk(r, to_sample(off), only=["leg_" + nm])
    Mb = W["body"][0]
    H0 = W["leg_" + nm][1]
    l1 = abs(r.by_name["shin_" + nm].pivot[1])
    l2 = abs(ANKLE_PT[1])
    v = Mb.T @ (np.asarray(ankle, dtype=float) - H0)
    phi = math.atan2(-v[0], v[1])
    a = math.hypot(v[0], v[1])
    d = min(math.hypot(a, v[2]), l1 + l2 - 1e-4)
    c2 = (d * d - l1 * l1 - l2 * l2) / (2 * l1 * l2)
    k = math.acos(max(-1.0, min(1.0, c2)))
    alpha = math.atan2(v[2], a)
    t1 = alpha - math.atan2(l2 * math.sin(k), l1 + l2 * math.cos(k))
    off = dict(off)
    off["leg_" + nm] = {"rot": (math.degrees(t1), 0.0, math.degrees(phi))}
    off["shin_" + nm] = {"rot": (math.degrees(k), 0.0, 0.0)}
    return off


def _clamp_ground(r, off, bone, axis, direction, floor=0.6, limit=110.0):
    """Turns `bone` (in `direction` about `axis`) just enough that none of its cubes or children sink below the floor."""
    def lowest(o):
        pts = []
        W = fk(r, to_sample(o))
        stack = [r.by_name[bone]]
        while stack:
            b = stack.pop()
            M, T = W[b.name]
            for c in b.cubes:
                for dx in (c.x, c.x + c.w):
                    for dy in (c.y, c.y + c.h):
                        for dz in (c.z, c.z + c.d):
                            pts.append(24.0 - (M @ np.array((dx, dy, dz)) + T)[1])
            stack.extend(b.children)
        return min(pts)
    if lowest(off) >= floor:
        return off
    e = off.setdefault(bone, {})
    base = list(e.get("rot", (0, 0, 0)))
    lo, hi = 0.0, limit
    for _ in range(14):
        mid = (lo + hi) / 2
        rr = list(base)
        rr[axis] += direction * mid
        e["rot"] = tuple(rr)
        if lowest(off) >= floor:
            hi = mid
        else:
            lo = mid
    rr = list(base)
    rr[axis] += direction * hi
    e["rot"] = tuple(rr)
    return off


def _world_pitch(W, bone):
    """How far a bone's local down axis leans forward (negative) or back (positive), degrees, in the yz plane."""
    d = W[bone][0] @ np.array((0.0, 1.0, 0.0))
    return math.degrees(math.atan2(d[2], d[1]))


REST_HIP = np.array((0.0, Y(22), 0.0))


def statue_pose(r, st):
    """st: hip (x, H, z) of the body pivot; body/chest/head rotations; ankle targets foot_l/foot_r (x, H, z) and foot
    pitches; the glaive's grip (x, H, z), its direction (model space, unit, blade end) and roll; the left hand's
    target (a point, 'glaive', or None with arm_l/forearm_l angles); extra offsets merged last."""
    hip = _h2y(st["hip"])
    off = {"body": {"pos": (float(hip[0] - REST_HIP[0]), float(-(hip[1] - REST_HIP[1])), float(hip[2] - REST_HIP[2])),
                    "rot": st.get("body", (0, 0, 0))},
           "chest": {"rot": st.get("chest", (0, 0, 0))}, "head": {"rot": st.get("head", (0, 0, 0))}}
    prev = st.get("prev") or {}
    for nm in ("l", "r"):
        off = _leg(r, off, nm, _h2y(st["foot_" + nm]))
        want = rotm(st.get("pitch_" + nm, 0.0) * DEG, st.get("yaw_" + nm, 0.0) * DEG, 0)
        rot, _ = local_rot_for(r, off, "foot_" + nm, want, _prev_rot(prev, "foot_" + nm))
        off["foot_" + nm] = {"rot": rot}
    # the right arm takes the glaive (its hand `slide` px up the shaft from the grip point); the glaive's angle is the
    # wrist, and the glaive slides back down through the fist by the same amount
    for b in ("arm_r", "forearm_r", "arm_l", "forearm_l"):
        if b in prev:
            off[b] = {"rot": prev[b]["rot"]}
    D = np.asarray(st["dir"], dtype=float)
    D /= np.linalg.norm(D)
    slide = float(st.get("slide", 0.0))
    off = _reach(r, off, "arm_r", "forearm_r", _h2y(st["grip"]) + D * slide, st.get("pole_r", (-1.0, 0.6, 0.6)))
    edge = np.asarray(st.get("edge", (0.0, 0.0, -1.0)), dtype=float)
    edge = edge - D * np.dot(edge, D)
    edge /= np.linalg.norm(edge)
    ycol = -D  # the glaive's local -y runs to the blade
    zcol = -edge  # its local -z is the blade's edge
    Mg = np.column_stack((np.cross(ycol, zcol), ycol, zcol))
    rot, _ = local_rot_for(r, off, "glaive", Mg, _prev_rot(prev, "glaive"))
    off["glaive"] = {"rot": rot}
    if slide:
        Wf = fk(r, to_sample(off), only=["forearm_r"])
        dl = Wf["forearm_r"][0].T @ (-D * slide)
        off["glaive"]["pos"] = (float(dl[0]), float(-dl[1]), float(dl[2]))
    lt = st.get("left")
    if lt is not None:
        if isinstance(lt, str) and lt == "glaive":
            W = fk(r, to_sample(off), only=["glaive"])
            tgt = world_pt(W, "glaive", (0, GLAIVE_HAND_L, 0))
        else:
            tgt = _h2y(lt)
        off = _reach(r, off, "arm_l", "forearm_l", tgt, st.get("pole_l", (1.0, 0.6, 0.6)))
    else:
        off["arm_l"] = {"rot": st.get("arm_l", (0, 0, 0))}
        off["forearm_l"] = {"rot": st.get("forearm_l", (0, 0, 0))}
    # the robe follows the legs and hangs; no panel sinks into the floor. Front panels lie over the thighs and hang from
    # the knees; side panels guard the legs (thigh, then the knee's bend); the back panel swings out behind
    W = fk(r, to_sample(off))
    body_p = _world_pitch(W, "body")
    for nm in ("l", "r"):
        thigh = _world_pitch(W, "leg_" + nm) - body_p
        off["skirt_" + nm] = {"rot": (thigh, 0, 0)}
    W = fk(r, to_sample(off))
    for nm in ("l", "r"):
        # hangs from the knee: mostly straight down, a little along the shin
        off["skirt_f" + nm + "2"] = {"rot": (-_world_pitch(W, "skirt_f" + nm) + 0.12 * _world_pitch(W, "shin_" + nm), 0, 0)}
    back = max(0.0, max(_world_pitch(W, "leg_l"), _world_pitch(W, "leg_r")) - body_p)
    off["skirt_b"] = {"rot": (0.7 * back, 0, 0)}
    W = fk(r, to_sample(off))
    off["skirt_b2"] = {"rot": (-0.85 * _world_pitch(W, "skirt_b"), 0, 0)}
    for nm in ("l", "r"):
        off = _clamp_ground(r, off, "skirt_f" + nm + "2", 0, -1)
        off = _clamp_ground(r, off, "skirt_" + nm, 0, 1 if off["skirt_" + nm]["rot"][0] > 0 else -1)
    off = _clamp_ground(r, off, "skirt_b2", 0, 1)
    off = _clamp_ground(r, off, "skirt_b", 0, 1)
    if st.get("extra"):
        off = merge(off, st["extra"])
    return off


def _dir(x, up, z):
    v = np.array((x, -up, z), dtype=float)
    return tuple(v / np.linalg.norm(v))


def swing_dir(d0, d1, k, turns=0):
    """Blends two glaive directions as an angle in the body's sagittal plane (0 = straight up, + = back), so a swing
    goes over the top or under, never round the side; the sideways part blends straight. turns: +1/-1 forces the way
    round when the short way is wrong."""
    def ang(d):
        return math.atan2(d[2], -d[1]), d[0]
    a0, x0 = ang(d0)
    a1, x1 = ang(d1)
    if turns == 0:
        while a1 - a0 > math.pi:
            a1 -= 2 * math.pi
        while a1 - a0 < -math.pi:
            a1 += 2 * math.pi
    else:
        while (a1 - a0) * turns < 0:
            a1 += 2 * math.pi * turns
    a = a0 + (a1 - a0) * k
    x = x0 + (x1 - x0) * k
    c = math.sqrt(max(0.0, 1 - x * x))
    return (x, -math.cos(a) * c, math.sin(a) * c)


# The designed states (heights above ground; x right is -; z forward is -)
STAND = {"hip": (0, 22, 0), "body": (0, 0, 0), "chest": (3, 0, 0), "head": (5, 0, 0),
         "foot_l": (3, 3, -1.5), "foot_r": (-3, 3, 1.5), "yaw_l": -6, "yaw_r": 8,
         "grip": (-10.0, 21.5, -5.5), "dir": _dir(-0.14, 0.88, -0.45), "edge": (0.0, 0.5, -0.86),
         "left": None, "arm_l": (-8, 0, -4), "forearm_l": (-14, 0, 0), "pole_r": (-1.0, 0.7, 0.5)}
KNEEL = {"hip": (0, 9.4, 2.2), "body": (12, 0, 0), "chest": (9, 0, 0), "head": (34, 0, 0),
         "foot_l": (3, 3, -8.5), "foot_r": (-3, 4.6, 4.8), "pitch_r": 15, "yaw_l": -4, "yaw_r": 4,
         "grip": (-12.0, 17.2, -7.0), "slide": 6.0, "dir": _dir(0.0, 1.0, 0.0), "edge": (0.0, 0.0, -1.0),
         "left": (4.0, 15.3, -7.0), "pole_r": (-1.0, 0.6, 0.5), "pole_l": (1.0, 0.4, 0.6)}


def _lerp_state(a, b, t):
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k), b.get(k)
        if k not in ("left", "prev", "extra"):  # a missing angle or offset is zero
            if va is None and vb is not None:
                va = 0.0 if isinstance(vb, (int, float)) else tuple(0.0 for _ in vb)
            if vb is None and va is not None:
                vb = 0.0 if isinstance(va, (int, float)) else tuple(0.0 for _ in va)
        if va is None or vb is None or isinstance(va, str) or isinstance(vb, str):
            out[k] = vb if t >= 0.5 else va
        elif isinstance(va, (int, float)):
            out[k] = va + (vb - va) * t
        else:
            out[k] = tuple(va[i] + (vb[i] - va[i]) * t for i in range(len(va)))
    return out


class Poser:
    """Solves states in time order, warm-starting each from the last, and keeps the results for baking."""

    def __init__(self, r, prev=None):
        self.r = r
        self.prev = prev
        self.cache = {}

    def __call__(self, key, st):
        if key in self.cache:
            return self.cache[key]
        st = dict(st)
        st["prev"] = self.prev
        off = statue_pose(self.r, st)
        self.prev = off
        self.cache[key] = off
        return off


def statue_clips(r):
    r.clips.clear()
    # -- kneel: on one knee, head bowed, the glaive planted upright beside it; perfectly still
    P0 = Poser(r)
    kneel = P0("kneel", KNEEL)
    bake(r.clip("kneel", 4.0, loop=True), lambda t: kneel, [0.0, 4.0])
    # -- rise: a crack, a grip, two heavy pushes up the planted glaive with a grinding halt between, the back foot
    #    stepping under, the front foot dragged back; then the glaive comes up into the guard
    PR = Poser(r, kneel)
    planted = KNEEL["grip"]

    def rise(t):
        crack_ = math.exp(-((t - 0.12) / 0.05) ** 2)
        push = 0.5 * ease5(span(t, 0.45, 0.85)) + 0.5 * ease5(span(t, 0.98, 1.4))
        st = _lerp_state(KNEEL, STAND, push)
        lean = math.sin(math.pi * span(t, 0.25, 1.2))
        st["body"] = tuple(np.array(st["body"]) + np.array((6.0 * lean, 0, 0)))
        st["head"] = (KNEEL["head"][0] * (1 - ease(span(t, 0.0, 0.6))) * (1 - push) + STAND["head"][0] * push - 3 * crack_, 0, 0)
        st["hip"] = (0.0, KNEEL["hip"][1] + (STAND["hip"][1] - KNEEL["hip"][1]) * push - 0.6 * math.sin(math.pi * span(t, 1.4, 1.8)),
                     KNEEL["hip"][2] + (STAND["hip"][2] - KNEEL["hip"][2]) * ease5(span(t, 0.4, 1.45)) - 0.6 * lean)
        # pressing down on the glaive takes the weight off the knee a moment before the first push
        st["hip"] = (st["hip"][0], st["hip"][1] + 0.9 * math.sin(math.pi * span(t, 0.28, 0.75)), st["hip"][2])
        judder = 0.25 * math.sin(t * 90.0) * (span(t, 0.84, 0.9) * (1 - span(t, 0.92, 0.99)))
        st["hip"] = (st["hip"][0], st["hip"][1] + judder, st["hip"][2])
        drag = ease(span(t, 0.95, 1.45))
        st["foot_l"] = tuple(np.array(KNEEL["foot_l"]) + (np.array(STAND["foot_l"]) - np.array(KNEEL["foot_l"])) * drag)
        step = ease5(span(t, 0.5, 1.35))
        lift = math.sin(math.pi * span(t, 0.75, 1.35)) * 2.2
        fr = np.array(KNEEL["foot_r"]) + (np.array(STAND["foot_r"]) - np.array(KNEEL["foot_r"])) * step
        st["foot_r"] = (fr[0], fr[1] + lift, fr[2])
        st["pitch_r"] = KNEEL["pitch_r"] * (1 - ease(span(t, 0.5, 1.2)))
        pr = st["pitch_r"] * DEG
        st["foot_r"] = (st["foot_r"][0], max(st["foot_r"][1], 3.0 + 5 * math.sin(pr) + 3 * math.cos(pr) - 3 + 0.4 * math.sin(pr)), st["foot_r"][2])
        swing = ease5(span(t, 1.3, 1.78))
        st["grip"] = tuple(np.array(planted) + (np.array(STAND["grip"]) - np.array(planted)) * swing)
        st["dir"] = swing_dir(KNEEL["dir"], STAND["dir"], swing)
        st["edge"] = tuple(np.array(KNEEL["edge"]) + (np.array(STAND["edge"]) - np.array(KNEEL["edge"])) * swing)
        if t < 1.05:  # the left hand pushes on the knee until the leg is under it
            st["left"] = KNEEL["left"]
            st["pole_l"] = KNEEL["pole_l"]
        else:
            st["left"] = None
            k = ease(span(t, 1.05, 1.6))
            st["arm_l"] = tuple(np.array((-60.0, 0, -10)) * (1 - k) + np.array(STAND["arm_l"]) * k)
            st["forearm_l"] = tuple(np.array((-40.0, 0, 0)) * (1 - k) + np.array(STAND["forearm_l"]) * k)
        st["extra"] = {"chest": {"rot": (0, 0, 0.6 * math.sin(t * 70) * crack_)}}
        return PR(round(t, 4), st)
    bake(r.clip("rise", 1.8), rise, times(1.8, 0.05))
    # -- stand: guard, the glaive held on the diagonal; the faintest breath
    PS = Poser(r, PR.prev)
    stand = PS("stand", STAND)

    def stand_c(t):
        b = (1 - math.cos(2 * math.pi * t / 4.0)) / 2
        return merge(stand, {"chest": {"rot": (-0.7 * b, 0, 0)}, "arm_l": {"pos": (0, 0.15 * b, 0)}, "arm_r": {"pos": (0, 0.15 * b, 0)},
                             "head": {"rot": (0.5 * b, 0, 0)}})
    bake(r.clip("stand", 4.0, loop=True), stand_c, times(4.0, 0.5))
    # -- walk: one heavy stride cycle (16 model px = 1.2 blocks at x1.2); flat-footed, 60% stance, a drop on each footfall
    L = 1.6
    stride = 1.2 * 16.0 / 1.2
    duty = 0.6
    PW = Poser(r, stand)

    def foot_path(p, base):
        p = p % 1.0
        if p < duty:
            u = p / duty
            z = stride * duty * (0.5 - u)
            lift = 0.0
        else:
            u = (p - duty) / (1 - duty)
            z = stride * duty * (-0.5 + ease(u))
            lift = 2.4 * math.sin(math.pi * u) ** 1.3
        return (base[0], base[1] + lift, z + base[2] * 0.0)

    def walk(t):
        p = t / L
        st = dict(STAND)
        st["foot_l"] = foot_path(p, (3, 3, 0))
        st["foot_r"] = foot_path(p + 0.5, (-3, 3, 0))
        st["yaw_l"], st["yaw_r"] = -3, 3
        sw_l = 0.0 if (p % 1.0) < duty else math.sin(math.pi * ((p % 1.0) - duty) / (1 - duty))
        sw_r = 0.0 if ((p + 0.5) % 1.0) < duty else math.sin(math.pi * (((p + 0.5) % 1.0) - duty) / (1 - duty))
        st["pitch_l"], st["pitch_r"] = -11 * sw_l ** 2, -11 * sw_r ** 2
        bob = 0.9 * math.cos(4 * math.pi * (p - 0.3))
        drop = -0.5 * (math.exp(-(((p % 0.5) - 0.04) / 0.05) ** 2))
        st["hip"] = (0.35 * math.sin(2 * math.pi * p), 21.3 + bob * 0.6 + drop, -0.8)
        st["body"] = (3.0, 4.0 * math.sin(2 * math.pi * p), -2.6 * math.sin(2 * math.pi * p))
        st["chest"] = (4.0 + 0.8 * math.cos(4 * math.pi * p), -6.0 * math.sin(2 * math.pi * p), 1.6 * math.sin(2 * math.pi * p))
        st["head"] = (4.0, 2.0 * math.sin(2 * math.pi * p), 1.2 * math.sin(2 * math.pi * p))
        sway = math.sin(2 * math.pi * (p - 0.12))
        st["grip"] = (STAND["grip"][0] + 0.4 * sway, STAND["grip"][1] + 0.5 * math.cos(4 * math.pi * (p - 0.35)), STAND["grip"][2] - 0.8 * sway)
        st["dir"] = _dir(-0.14 + 0.03 * sway, 0.88, -0.45 - 0.06 * sway)
        st["arm_l"] = (-8 + 12 * math.sin(2 * math.pi * (p + 0.5)), 0, -4)
        st["forearm_l"] = (-14 - 6 * max(0.0, math.sin(2 * math.pi * (p + 0.5))), 0, 0)
        return PW(round(t % L, 4), st)
    wk = r.clip("walk", L, loop=True)
    bake(wk, walk, times(L, L / 16))
    # -- windup: the left hand joins the shaft, the glaive goes up over the head, the body leans back on a wide stance
    PU = Poser(r, stand)
    WIND = dict(STAND)
    WIND.update({"hip": (0, 20.6, 1.0), "body": (-6, 0, 0), "chest": (-12, -8, 0), "head": (2, 6, 0),
                 "foot_l": (3, 3, -5.0), "foot_r": (-3.5, 3, 4.0), "yaw_l": -10, "yaw_r": 14,
                 "grip": (-3.0, 50.0, 3.5), "dir": _dir(0.05, 0.5, 0.86), "edge": (0.0, 0.86, -0.5),
                 "left": "glaive", "pole_r": (-1.0, 0.2, -0.3), "pole_l": (1.0, 0.2, -0.3)})

    def windup(t):
        a = ease5(span(t, 0.0, 0.3))
        k = ease5(span(t, 0.12, 1.0))
        st = _lerp_state(STAND, WIND, k)
        # the glaive swings through upright on its way overhead (an arc, not a straight line)
        g0, g1 = np.array(STAND["grip"]), np.array(WIND["grip"])
        mid = (g0 + g1) / 2 + np.array((-3.0, 4.0, -7.0))
        st["grip"] = tuple(bezier([g0, mid, g1], k))
        st["dir"] = swing_dir(STAND["dir"], WIND["dir"], k)
        st["edge"] = tuple(np.array(STAND["edge"]) * (1 - k) + np.array(WIND["edge"]) * k)
        st["left"] = "glaive" if a > 0.5 else None
        if a <= 0.5:
            st["arm_l"] = tuple(np.array(STAND["arm_l"]) + np.array((-40.0, 0, 8)) * a * 2)
            st["forearm_l"] = tuple(np.array(STAND["forearm_l"]) + np.array((-50.0, 0, 0)) * a * 2)
        tense = 0.4 * math.sin(math.pi * span(t, 1.0, 1.25))
        st["hip"] = (st["hip"][0], st["hip"][1] - tense, st["hip"][2])
        return PU(round(t, 4), st)
    bake(r.clip("windup", 1.25), windup, times(1.25, 0.05))
    WOUND = PU.cache[round(1.25, 4)]
    # -- strike: down hard in front, the blade's point into the floor 2.5 blocks ahead (z -33), a recoil, then held
    PK = Poser(r, WOUND)
    HIT = dict(WIND)
    HIT.update({"hip": (0, 17.5, -2.5), "body": (14, 0, 0), "chest": (16, 6, 0), "head": (-6, -4, 0),
                "foot_l": (3, 3, -7.0), "foot_r": (-3.5, 3, 5.0),
                "pole_r": (-1.0, 0.8, 0.4), "pole_l": (1.0, 0.8, 0.4)})
    def blade_low(off):
        W = fk(r, to_sample(off), only=["blade_tip"])
        best = None
        for bn in ("glaive", "blade_tip"):
            M, T = W[bn]
            for c in r.by_name[bn].cubes:
                if c.name not in ("blade", "blade_tip", "blade_point"):
                    continue
                for dx in (c.x, c.x + c.w):
                    for dy in (c.y, c.y + c.h):
                        for dz in (c.z, c.z + c.d):
                            q = M @ np.array((dx, dy, dz)) + T
                            if best is None or q[1] > best[1]:
                                best = q
        return best

    def hit_state(depth=0.0, angle=-132.0):
        """The blade at a fixed angle (45 degrees forward and down), the grip moved until the blade's lowest point
        rests on the floor 33 px ahead (depth > 0 lifts it): the blade is rigid on the grip, so this converges."""
        st = dict(HIT)
        target = np.array((-4.5, Y(0.3 + depth), -33.0))
        a = angle * DEG
        dvec = np.array((0.03, -math.cos(a), math.sin(a)))
        dvec /= np.linalg.norm(dvec)
        st["dir"] = tuple(float(v) for v in dvec)
        st["edge"] = (0.0, 0.7, -0.7)
        grip = target - dvec * 33.0
        for _ in range(4):
            st["grip"] = (float(grip[0]), float(24 - grip[1]), float(grip[2]))
            low = blade_low(statue_pose(r, dict(st, prev=WOUND)))
            err = target - low
            grip = grip + err
            if np.linalg.norm(err) < 0.02:
                break
        st["grip"] = (float(grip[0]), float(24 - grip[1]), float(grip[2]))
        return st
    HIT_S = hit_state(0.0)
    BOUNCE = hit_state(2.6)
    BOUNCE.update({"body": (11, 0, 0), "chest": (12, 6, 0), "hip": (0, 18.3, -2.2)})
    REST_S = hit_state(0.6)
    REST_S.update({"body": (12.5, 0, 0), "chest": (14, 5, 0), "hip": (0, 17.9, -2.4)})

    def strike(t):
        pull = math.sin(math.pi * span(t, 0.0, 0.07))
        k = ease_in(span(t, 0.05, 0.2), 2.4)
        if t <= 0.2:
            st = _lerp_state(WIND, HIT_S, k)
            g0, g1 = np.array(WIND["grip"]), np.array(HIT_S["grip"])
            mid = (g0 + g1) / 2 + np.array((-1.0, 9.0, -6.0))
            st["grip"] = tuple(bezier([g0, mid, g1], k) + np.array((0, 0.8 * pull, 0.6 * pull)))
            st["dir"] = swing_dir(WIND["dir"], HIT_S["dir"], k, turns=-1)  # forward, over the top
            st["edge"] = tuple(np.array(WIND["edge"]) * (1 - k) + np.array(HIT_S["edge"]) * k)
        elif t <= 0.32:
            st = _lerp_state(HIT_S, BOUNCE, math.sin(math.pi * span(t, 0.2, 0.32) * 0.5))
        else:
            st = _lerp_state(BOUNCE, REST_S, ease5(span(t, 0.32, 0.6)))
        shake = 0.5 * math.sin(t * 95.0) * span(t, 0.19, 0.21) * (1 - span(t, 0.21, 0.42))
        st["extra"] = {"chest": {"rot": (shake, 0, shake * 0.6)}, "head": {"rot": (shake * 1.5, 0, 0)}}
        return PK(round(t, 4), st)
    bake(r.clip("strike", 0.6), strike, times(0.6, 0.02))
    # -- recover: the blade comes up off the floor, the feet gather, back to the guard
    PV = Poser(r, PK.prev)

    def recover(t):
        k = ease5(span(t, 0.05, 0.85))
        st = _lerp_state(REST_S, STAND, k)
        g0, g1 = np.array(REST_S["grip"]), np.array(STAND["grip"])
        mid = (g0 + g1) / 2 + np.array((0.0, 5.0, 2.0))
        st["grip"] = tuple(bezier([g0, mid, g1], k))
        st["dir"] = swing_dir(REST_S["dir"], STAND["dir"], k)
        st["left"] = "glaive" if t < 0.35 else None
        if t >= 0.35:
            kk = ease(span(t, 0.35, 0.85))
            st["arm_l"] = tuple(np.array((-55.0, 0, 6)) * (1 - kk) + np.array(STAND["arm_l"]) * kk)
            st["forearm_l"] = tuple(np.array((-35.0, 0, 0)) * (1 - kk) + np.array(STAND["forearm_l"]) * kk)
        fl = ease5(span(t, 0.3, 0.75))
        st["foot_l"] = tuple(np.array(REST_S["foot_l"]) + (np.array(STAND["foot_l"]) - np.array(REST_S["foot_l"])) * fl)
        st["foot_l"] = (st["foot_l"][0], st["foot_l"][1] + 1.6 * math.sin(math.pi * fl), st["foot_l"][2])
        fr = ease5(span(t, 0.1, 0.55))
        st["foot_r"] = tuple(np.array(REST_S["foot_r"]) + (np.array(STAND["foot_r"]) - np.array(REST_S["foot_r"])) * fr)
        return PV(round(t, 4), st)
    bake(r.clip("recover", 0.9), recover, times(0.9, 0.05))
    # -- what HeroRig's checks ask of every rig: idle (rest), hurt (a stone flinch), death (it cracks and slumps)
    r.clip("idle", 4.0, loop=True).rot("root", [(0, 0, 0, 0), (4.0, 0, 0, 0)])
    hurt = r.clip("hurt", 0.45)
    hurt.rot("chest", [(0, 0, 0, 0), (0.05, -5, 3, 0), (0.18, 2, -1, 0), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0, 0, 0, 0), (0.05, -8, 6, 0), (0.22, 3, -2, 0), (0.45, 0, 0, 0)])
    PD = Poser(r, stand)
    SLUMP = dict(KNEEL)
    SLUMP.update({"hip": (0, 10.8, 2.2), "body": (16, 0, 4), "chest": (14, 0, 6), "head": (40, 0, 10),
                  "foot_l": (3, 6.0, 5.5), "pitch_l": 66, "foot_r": (-3, 6.0, 5.5), "pitch_r": 66,
                  "grip": (-13.0, 4.0, -7.0), "dir": _dir(-0.97, 0.08, -0.22), "edge": (0.0, -1.0, 0.0),
                  "left": None, "arm_l": (-12, 0, -6), "forearm_l": (-20, 0, 0)})

    def death(t):
        jolt = math.exp(-((t - 0.08) / 0.05) ** 2)
        k = ease_in(span(t, 0.15, 1.0), 1.6)
        st = _lerp_state(STAND, SLUMP, k)
        st["pitch_l"] = st["pitch_r"] = 66.0 * k * k  # the toes turn under only as the ankles come up
        bounce = 0.8 * math.sin(math.pi * span(t, 1.0, 1.3)) * (1 - span(t, 1.0, 1.4))
        st["hip"] = (st["hip"][0], st["hip"][1] + bounce, st["hip"][2])
        g = ease_in(span(t, 0.3, 1.2), 1.8)
        st["grip"] = tuple(np.array(STAND["grip"]) + (np.array(SLUMP["grip"]) - np.array(STAND["grip"])) * g)
        st["dir"] = swing_dir(STAND["dir"], SLUMP["dir"], g)
        st["extra"] = {"chest": {"rot": (-6 * jolt, 0, 3 * jolt)}}
        return PD(round(t, 4), st)
    bake(r.clip("death", 1.6), death, times(1.6, 0.05))
    return r


STATUE_CHAIN = [("kneel", "rise"), ("rise", "stand"), ("stand", "windup"), ("windup", "strike"), ("strike", "recover"),
                ("recover", "stand"), ("kneel", "kneel"), ("stand", "stand"), ("walk", "walk")]


def check_statue(rig):
    """Standing about 46 px (3.45 blocks at x1.2), kneeling about 30; feet on the floor while they stand on it; the
    strike's blade point on the floor about 2.5 blocks ahead (z -33 model px); nothing sinks into the floor."""
    problems = check_chain(rig, STATUE_CHAIN)
    stats = {}

    def height(clip, t, exclude=("glaive", "blade_tip")):
        pose = rig.sample([(clip, t, 1.0)])
        W = fk(rig, pose)
        hs = []
        for b in rig.bones:
            if b.name in exclude:
                continue
            M, T = W[b.name]
            for c in b.cubes:
                for dy in (c.y, c.y + c.h):
                    for dx in (c.x, c.x + c.w):
                        for dz in (c.z, c.z + c.d):
                            hs.append(24.0 - (M @ np.array((dx, dy, dz)) + T)[1])
        return max(hs), min(hs)
    stats["stand"], _ = height("stand", 0.0)
    stats["kneel"], _ = height("kneel", 0.0)
    if not 44.0 <= stats["stand"] <= 48.5:
        problems.append(f"ritual_statue stands {stats['stand']:.1f} px (want about 46)")
    if not 28.0 <= stats["kneel"] <= 34.0:
        problems.append(f"ritual_statue kneels {stats['kneel']:.1f} px (want about 30)")
    low = 1e9
    for name, t, pose in sample_poses(rig, 0.02):
        pts = corners(rig, pose)
        low = min(low, float((24.0 - pts[:, 1]).min()))
    stats["low"] = low
    if low < -0.3:
        problems.append(f"ritual_statue: something sinks {low:.2f} px into the floor")
    W = fk(rig, rig.sample([("strike", 0.2, 1.0)]))
    tip = None
    for bn in ("glaive", "blade_tip"):
        M, T = W[bn]
        for c in rig.by_name[bn].cubes:
            if c.name in ("blade", "blade_tip", "blade_point"):
                for dx in (c.x, c.x + c.w):
                    for dy in (c.y, c.y + c.h):
                        for dz in (c.z, c.z + c.d):
                            q = M @ np.array((dx, dy, dz)) + T
                            if tip is None or q[1] > tip[1]:
                                tip = q
    stats["hit_z"], stats["hit_h"] = float(tip[2]), float(24 - tip[1])
    if not (-36 <= tip[2] <= -30 and -0.6 <= 24 - tip[1] <= 1.5):
        problems.append(f"strike: the blade point lands at H {24 - tip[1]:.1f}, z {tip[2]:.1f} (want the floor at z -33)")
    for clip, t in (("stand", 0.0), ("kneel", 0.0)):
        W = fk(rig, rig.sample([(clip, t, 1.0)]))
        for nm in ("l",) if clip == "kneel" else ("l", "r"):
            p = world_pt(W, "foot_" + nm, (0, 3, 0))
            if abs(24 - p[1]) > 0.35:
                problems.append(f"{clip}: foot_{nm} sole at H {24 - p[1]:.2f}")
    return problems, stats


def make_statue():
    r = ritual_statue()
    statue_clips(r)
    return r


# =====================================================================================================================
# Checks: the hall's hard bounds over every clip's samples, and every clip starting where the story's previous ends
# =====================================================================================================================

def sample_poses(rig, step=0.05, look=()):
    """(clip, t, sampler pose) over every clip, every `step` seconds; plus each look extreme layered on the head."""
    out = []
    for c in rig.clips.values():
        for t in times(c.length, step):
            p = rig.sample([(c.name, t, 1.0)])
            out.append((c.name, t, p))
            for bn, yaw, pitch in look:
                q = {k: {kk: tuple(vv) for kk, vv in v.items()} for k, v in p.items()}
                e = q.setdefault(bn, {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)})
                rr = e.get("rot", (0, 0, 0))
                e["rot"] = (rr[0] + pitch * DEG, rr[1] + yaw * DEG, rr[2])
                out.append((c.name + f"+look({yaw},{pitch})", t, q))
    return out


def pose_gap(rig, a, b):
    """Largest distance (px) any cube corner moves between two sampler poses."""
    return float(np.max(np.linalg.norm(corners(rig, a) - corners(rig, b), axis=1)))


def check_chain(rig, chain):
    """chain: [(clip A, clip B)]: B's first frame must be A's last."""
    bad = []
    for a, b in chain:
        ca = rig.clips[a]
        pa = rig.sample([(a, ca.length, 1.0)])
        pb = rig.sample([(b, 0.0, 1.0)])
        g = pose_gap(rig, pa, pb)
        if g > 0.06:
            bad.append(f"{rig.id}: {b} starts {g:.2f} px away from where {a} ends")
    return bad


SEATED_CHAIN = [("dormant", "wake"), ("wake", "awake"), ("wake", "gaze"), ("awake", "gaze"), ("gaze", "awake"),
                ("awake", "raise"), ("gaze", "raise"), ("raise", "close"), ("close", "sleep"), ("sleep", "dormant"),
                ("dormant", "dormant"), ("awake", "awake")]


def check_seated(rig):
    """The hall's hard limits (in model px, 1 px = 0.25 block): nothing above 96 px, nothing beyond |x| 26 at hip and
    knee level (the armrests start at 28), nothing behind z = +10 (the wall), knees about 18 px out, feet on the dais;
    the raised palm 70-80 px up, in front of the face. Head turns the director can ask for are included."""
    problems = check_chain(rig, SEATED_CHAIN)
    look = [("head", y, p * 0.6) for y in (-55, 55) for p in (-10, 60)]
    stats = {"top": -1e9, "back": -1e9, "xhip": 0.0, "xall": 0.0, "low": 1e9}
    worst = {}
    for name, t, pose in sample_poses(rig, 0.05, look):
        pts = corners(rig, pose)
        H = 24.0 - pts[:, 1]
        top, back = float(H.max()), float(pts[:, 2].max())
        hip = pts[H <= 40.0]
        xhip = float(np.abs(hip[:, 0]).max()) if len(hip) else 0.0
        stats["top"] = max(stats["top"], top)
        stats["back"] = max(stats["back"], back)
        stats["xhip"] = max(stats["xhip"], xhip)
        stats["xall"] = max(stats["xall"], float(np.abs(pts[:, 0]).max()))
        stats["low"] = min(stats["low"], float(H.min()))
        clip = name.split("+")[0]
        for kind, v, lim in (("high", top, 96.0), ("back", back, 10.0), ("wide", xhip, 26.0)):
            if v > lim and v > worst.get((clip, kind), (0, "", 0))[0]:
                worst[(clip, kind)] = (v, name, t)
    limits = {"high": "px high (vault at 96)", "back": "z (wall at +10)", "wide": "|x| at hip/knee level (armrests at 28)"}
    for (clip, kind), (v, name, t) in sorted(worst.items()):
        problems.append(f"{name} @{t:.2f}: {v:.1f} {limits[kind]}")
    rest = corners(rig, rig.sample([("awake", 0.0, 1.0)]))
    Hr = 24.0 - rest[:, 1]
    if not 86.0 <= Hr.max() <= 88.6:
        problems.append(f"seated height {Hr.max():.1f} px (want 88)")
    knee = float(rest[(Hr > 25) & (Hr < 35)][:, 2].min())
    if not -23.0 <= knee <= -16.0:
        problems.append(f"knees at z {knee:.1f} (want about -18)")
    if stats["low"] < -0.05:
        problems.append(f"something sinks {stats['low']:.2f} px into the dais")
    for clip in ("raise", "close"):
        W = fk(rig, rig.sample([(clip, rig.clips[clip].length, 1.0)]))
        p = W["hand_r"][1]
        if not 70.0 <= 24.0 - p[1] <= 80.0 or p[2] > -11.0:
            problems.append(f"{clip} ends with the palm at H {24 - p[1]:.1f}, z {p[2]:.1f}")
    return problems, stats


# =====================================================================================================================
# Registry, build, Java
# =====================================================================================================================

def make_seated():
    r = seated_one()
    seated_clips(r)
    return r


def rigs():
    out = [("the_seated_one", make_seated(), paint_the_seated_one, check_seated)]
    if "ritual_statue" in globals():
        out.append(("ritual_statue", make_statue(), paint_ritual_statue, check_statue))
    return out


def paint(rig, painter):
    P = R.Painter(rig, "ritual|" + rig.id)
    return painter(rig, P)


def verify(all_rigs):
    for rid, rig, _, checker in all_rigs:
        problems = rig.check()
        more, _ = checker(rig)
        problems += more
        if problems:
            raise SystemExit("\n".join(problems))


def java(all_rigs):
    chunks = []
    for rid, rig, _, _ in all_rigs:
        block, size, parts = [], 0, []
        for ln in rig.java_lines():
            if size + len(ln) > 24000 and block:
                parts.append(block)
                block, size = [], 0
            block.append(ln)
            size += len(ln) + 1
        if block:
            parts.append(block)
        chunks.append((rid, parts))
    src = ["package dev.awaken.mobs.client.anim;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/ritual_art.py: do not edit by hand. The Ritual of the Bell's two bodies (the Seated",
           " * One, drawn at x4, and the ritual statue, drawn at x1.2) as plain data in the hero rigs' format",
           " * ({@link HeroRig#parse}); textures in textures/entity/mobs/{id}.png and {id}_glow.png.",
           " */", "public final class RitualData {", "    private RitualData() {}", "",
           "    /** Every rig's source text, in order. */", "    public static final List<String[]> SOURCES = List.<String[]>of("]
    for k, (rid, parts) in enumerate(chunks):
        src.append(f"            // {rid}")
        src.append("            new String[] {")
        for pi, block in enumerate(parts):
            src.append('                """')
            for ln in block:
                src.append("                " + ln)
            src.append('                """' + ("," if pi < len(parts) - 1 else ""))
        src.append("            }" + ("," if k < len(chunks) - 1 else ""))
    src.append("    );")
    src.append("}")
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")
    return JAVA_OUT


def build(out=None):
    all_rigs = rigs()
    verify(all_rigs)
    for rid, rig, painter, _ in all_rigs:
        skin, glow = paint(rig, painter)
        if out is not None:
            out.entity("mobs/" + rid, skin)
            out.entity("mobs/" + rid + "_glow", glow)
        else:
            os.makedirs(TEX_DIR, exist_ok=True)
            skin.image().save(os.path.join(TEX_DIR, rid + ".png"))
            glow.image().save(os.path.join(TEX_DIR, rid + "_glow.png"))
    path = java(all_rigs)
    print(f"ritual_art: {len(all_rigs)} rigs -> {os.path.relpath(path, ROOT)}")


# =====================================================================================================================
# Previews
# =====================================================================================================================

BG_DAY = (112, 122, 128, 255)
BG_HALL = (6, 6, 9, 255)
BG_NIGHT = (4, 5, 12, 255)
RENDER_SCALE = {"the_seated_one": 4.0, "ritual_statue": 1.2}
# key poses per clip (seconds); the first pose of a clip is where the previous one left off
KEY_POSES = {
    "the_seated_one": [("dormant", [0.0]), ("wake", [0.3, 1.0, 1.6, 2.2, 3.0]), ("awake", [2.5]), ("gaze", [0.6, 1.3, 1.56, 2.3]),
                       ("raise", [0.25, 0.9, 1.5, 2.0, 2.5, 3.0]), ("close", [0.1, 0.22, 0.32, 0.8]), ("sleep", [0.5, 1.0, 1.5, 2.0])],
    "ritual_statue": [("kneel", [0.0]), ("rise", [0.4, 0.8, 1.1, 1.4, 1.8]), ("stand", [1.0]), ("walk", [0.0, 0.4, 0.8, 1.2]),
                      ("windup", [0.3, 0.7, 1.25]), ("strike", [0.08, 0.16, 0.22, 0.35, 0.6]), ("recover", [0.3, 0.6, 0.9])],
}
HOLD = {"the_seated_one": ("dormant", 0.0, "awake", 0.0), "ritual_statue": ("kneel", 0.0, "stand", 0.0)}


def hall_lit(sk, gl, f=0.5, warm=(1.0, 0.86, 0.66)):
    """The skin as candlelight in the black hall shows it: dimmed and warmed, the glow layer over it."""
    from PIL import Image
    base = sk.copy()
    px = base.load()
    for y in range(base.height):
        for x in range(base.width):
            r_, g_, b_, a_ = px[x, y]
            px[x, y] = (int(r_ * f * warm[0]), int(g_ * f * warm[1]), int(b_ * f * warm[2]), a_)
    return Image.alpha_composite(base, gl)


def _scaled(tree, scale):
    tree["scale"] = (scale, scale, scale)
    tree["pivot"] = (0, 24 - 24 * scale, 0)
    return tree


def closeup(rig, tex, samples, bone, local, half, yaw, pitch, cell=(170, 170), night=None, bg=BG_DAY):
    """A row of close-ups (the face, the hand): each pose framed on one bone's point, `half` model px around it."""
    from PIL import Image, ImageDraw
    ya, pa = math.radians(yaw), math.radians(pitch)
    cam = R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0))
    row = Image.new("RGBA", (cell[0] * len(samples), cell[1] + 14), (36, 40, 48, 255))
    d = ImageDraw.Draw(row)
    for k, (label, layers) in enumerate(samples):
        pose = rig.sample(layers)
        W = fk(rig, pose)
        c = R._mv(cam, tuple(float(v) for v in world_pt(W, bone, local)))
        b = (c[0] - half, c[0] + half, c[1] - half, c[1] + half)
        px = cell[0] / (2.0 * half)
        im = R.render(rig.tree(pose), tex, yaw=yaw, pitch=pitch, px=px, bounds=b, night=night,
                      origin=(cell[0], cell[1], -b[0] * px, -b[2] * px))
        bgi = Image.new("RGBA", cell, bg)
        bgi.alpha_composite(im)
        row.paste(bgi, (k * cell[0], 0))
        d.text((k * cell[0] + 4, cell[1] + 1), label, fill=(220, 225, 235, 255))
    return row


def preview(outdir, only=None):
    from PIL import Image, ImageDraw
    import fauna3 as F3
    os.makedirs(outdir, exist_ok=True)
    made = []
    for rid, rig, painter, checker in rigs():
        if only and rid not in only:
            continue
        problems, stats = checker(rig)
        skin, glow = paint(rig, painter)
        sk, gl = skin.image(), glow.image()
        lit = Image.alpha_composite(sk, gl)
        c0, t0, c1, t1 = HOLD[rid]
        still = rig.tree(rig.sample([(c0, t0, 1.0)]))
        woke = rig.tree(rig.sample([(c1, t1, 1.0)]))
        cell = (300, 320)
        views = [("front", R.render(still, lit, yaw=0, pitch=4, size=cell), BG_DAY),
                 ("three-quarter", R.render(still, lit, yaw=35, pitch=12, size=cell), BG_DAY),
                 ("side", R.render(still, lit, yaw=90, pitch=4, size=cell), BG_DAY),
                 ("back", R.render(still, lit, yaw=160, pitch=14, size=cell), BG_DAY),
                 ("hall, candlelit", R.render(woke, hall_lit(sk, gl), yaw=10, pitch=-12, size=cell), BG_HALL),
                 ("dark: glow only", R.render(woke, F3.ingame_night(sk, gl, 1.0, 0.06), yaw=10, pitch=-12, size=cell), BG_NIGHT),
                 ("glow + bloom", R.render(woke, sk, yaw=10, pitch=-12, size=cell, night=gl), BG_NIGHT)]
        row1 = Image.new("RGBA", (cell[0] * len(views), cell[1] + 14), (24, 26, 34, 255))
        d1 = ImageDraw.Draw(row1)
        for i, (label, im, bgc) in enumerate(views):
            bg = Image.new("RGBA", cell, bgc)
            bg.alpha_composite(im, (0, 0))
            row1.paste(bg, (i * cell[0], 0))
            d1.text((i * cell[0] + 4, cell[1] + 1), label, fill=(220, 225, 235, 255))
        # size: a player beside the body, both at game scale (1 px = 1/16 block)
        sc = RENDER_SCALE[rid]
        unit = 1.0 if sc > 2 else 3.0
        body_im = R.render(_scaled(rig.tree(rig.sample([(c1, t1, 1.0)])), sc), lit, yaw=30, pitch=6, px=unit)
        pl = R.render(R.player_tree(), R.steve(), yaw=30, pitch=6, px=unit)
        extra = []
        if rid == "ritual_statue":
            extra = [("kneeling", R.render(_scaled(rig.tree(rig.sample([("kneel", 0.0, 1.0)])), sc), lit, yaw=30, pitch=6, px=unit))]
        items = [("player", pl), (f"{rid} x{sc:g}", body_im)] + extra
        W2 = sum(im.width for _, im in items) + 60 * len(items) + 40
        H2 = max(im.height for _, im in items) + 40
        row2 = Image.new("RGBA", (max(W2, row1.width), H2), (150, 158, 160, 255))
        d2 = ImageDraw.Draw(row2)
        x, ground = 20, H2 - 24
        for name, im in items:
            row2.alpha_composite(im, (x, ground - im.height + 8))
            d2.text((x, ground + 6), name, fill=(20, 24, 30, 255))
            x += im.width + 60
        d2.line((0, ground, row2.width, ground), fill=(60, 70, 70, 255))
        for k in range(0, int(H2 / (16 * unit)) + 1):  # block lines
            yy = ground - k * 16 * unit
            d2.line((row2.width - 40, yy, row2.width - 30, yy), fill=(40, 40, 40, 255))
        info = (f"{rid}: {len(rig.bones)} bones, {len(rig.cubes)} cubes, tex {rig.tex[0]}x{rig.tex[1]} (used {rig.used} rows), "
                f"{len(rig.clips)} clips; bounds over every clip: " + ", ".join(f"{k} {v:.1f}" for k, v in stats.items())
                + ("; PROBLEMS: " + "; ".join(problems[:3]) if problems else "; all checks pass"))
        d2.text((8, 6), info, fill=(20, 24, 30, 255))
        # key poses, one row per clip, all framed alike
        rows = []
        allp = [(clip, t) for clip, ts in KEY_POSES[rid] for t in ts]
        for yaw, pitch in (((25, 6), (75, 4)) if rid == "the_seated_one" else ((35, 6), (80, 4))):
            trees = [rig.tree(rig.sample([(clip, t, 1.0)])) for clip, t in allp]
            b = R.bounds_of(trees, yaw, pitch)
            pc = (190, 220)
            px = min((pc[0] - 12) / (b[1] - b[0]), (pc[1] - 20) / (b[3] - b[2]))
            k = 0
            for clip, ts in KEY_POSES[rid]:
                row = Image.new("RGBA", (max(row1.width, pc[0] * 7), pc[1] + 14), (36, 40, 48, 255))
                d3 = ImageDraw.Draw(row)
                for q, t in enumerate(ts):
                    im = R.render(trees[k], lit, yaw=yaw, pitch=pitch, px=px, bounds=b,
                                  origin=(pc[0], pc[1], (pc[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, pc[1] - 6 - b[3] * px))
                    bg = Image.new("RGBA", pc, BG_DAY)
                    bg.alpha_composite(im)
                    row.paste(bg, (q * pc[0], 0))
                    d3.text((q * pc[0] + 4, pc[1] + 1), f"{clip} {t:.2f}s", fill=(220, 225, 235, 255))
                    k += 1
                rows.append(row)
        if rid == "the_seated_one":
            face = [(f"wake {t:.1f}s", [("wake", t, 1.0)]) for t in (0.0, 0.8, 1.2, 1.6, 2.0, 2.4, 3.0)]
            face += [("gaze 1.56s", [("gaze", 1.56, 1.0)]), ("sleep 0.5s", [("sleep", 0.5, 1.0)])]
            rows.append(closeup(rig, lit, face, "head", (0, -6, -3), 13, 8, 2))
            rows.append(closeup(rig, sk, face[:1] + face[3:7], "head", (0, -6, -3), 13, 8, 2, night=gl, bg=BG_NIGHT))
            hand = [(f"raise {t:.1f}s", [("raise", t, 1.0)]) for t in (2.0, 2.4, 3.0)]
            hand += [(f"close {t:.2f}s", [("close", t, 1.0)]) for t in (0.1, 0.18, 0.24, 0.32, 0.8)]
            rows.append(closeup(rig, lit, hand, "hand_r", (0, 0, -2), 13, 12, -14))
            rows.append(closeup(rig, lit, hand[2:3] + hand[-1:], "hand_r", (0, 0, -2), 13, 120, 10))
        Wt = max([row1.width, row2.width] + [rw.width for rw in rows])
        Ht = row1.height + row2.height + sum(rw.height for rw in rows) + 10
        sheet = Image.new("RGBA", (Wt, Ht), (24, 26, 34, 255))
        y = 0
        for part in [row1, row2] + rows:
            sheet.paste(part, (0, y))
            y += part.height + (5 if part is row2 else 0)
        path = os.path.join(outdir, f"ritual_{rid}.png")
        sheet.save(path)
        sk.resize((sk.width * 3, sk.height * 3), Image.NEAREST).save(os.path.join(outdir, f"ritual_{rid}_tex.png"))
        made.append(path)
        if problems:
            print("\n".join(problems))
    print("\n".join(made))


def main(args):
    if args and args[0] == "build":
        build()
    elif args and args[0] == "preview":
        preview(args[1] if len(args) > 1 else os.path.join(ROOT, "build/ritual_preview"), args[2:] or None)
    elif args and args[0] == "check":
        all_rigs = rigs()
        for rid, rig, _, checker in all_rigs:
            problems, stats = checker(rig)
            print(rid, rig.check() + problems, {k: round(v, 2) for k, v in stats.items()})
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
