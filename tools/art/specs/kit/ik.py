"""Reaching for the kit's bodies (SPEC §35.11): two-bone limbs set by where their ends should be, bones laid along a
direction, and turns written the shortest way, so a body without the biped plan's feet (crab legs, roots, a Caster's
arms) can still put its limbs where they must go and keep them there.

Points are (x, height, z) in model pixels with the height up from the floor; model space itself has y down (the floor at
y = 24). Turns are in degrees on top of a bone's rest, the way key poses write them.
"""
import math

from kit import core
import fauna_rig as R

DEG = core.DEG


def v(a, b, f=1.0):
    return tuple(a[i] + f * b[i] for i in range(3))


def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def unit(a):
    n = math.sqrt(dot(a, a)) or 1.0
    return tuple(c / n for c in a)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def model(p):
    """(x, height, z) to model pixels (y down)."""
    return (p[0], 24.0 - p[1], p[2])


def height(q):
    """Model pixels to (x, height, z)."""
    return (q[0], 24.0 - q[1], q[2])


def delta(e, rest):
    """A whole turn (degrees) as the turn on top of rest, written the shortest way: of its two Euler forms (x, y, z) and
    (x + 180, 180 - y, z + 180), each wrapped round to its rest, the smaller. Keys written another way would spin the
    bone between them."""
    best = None
    for c in (e, (e[0] + 180, 180 - e[1], e[2] + 180)):
        d = tuple((c[i] - rest[i] + 180) % 360 - 180 for i in range(3))
        if best is None or sum(x * x for x in d) < sum(x * x for x in best):
            best = d
    return best


def two_bone(r, fr, upper, lower, end, target, pole):
    """The upper bone's whole turn (radians, in its parent's frame) and the lower one's hinge (radians about its x) that
    put the end bone's pivot at target (model pixels), the joint between them pointing along pole (a model direction).
    fr: the frames the parent is posed in. Out of reach, the limb points straight at the target."""
    up, lo, en = r.by_name[upper], r.by_name[lower], r.by_name[end]
    Mp, Tp = fr[up.parent.name]
    p0 = v(Tp, R._mv(Mp, up.pivot))
    a, b = lo.pivot[1], en.pivot[1]
    d = sub(target, p0)
    L = max(abs(a - b) + 1e-3, min(a + b - 1e-3, math.sqrt(dot(d, d))))
    u = unit(d)
    n = unit(v(pole, u, -dot(pole, u)))
    ca = (a * a + L * L - b * b) / (2 * a * L)
    sa = math.sqrt(max(0.0, 1 - ca * ca))
    joint = v(v(p0, u, a * ca), n, a * sa)
    ua = unit(sub(joint, p0))
    fd = unit(sub(v(p0, u, L), joint))
    perp = v(fd, ua, -dot(fd, ua))
    z = unit(perp) if dot(perp, perp) > 1e-8 else n
    z = tuple(-c for c in z)
    x = cross(ua, z)
    M = tuple((x[i], ua[i], z[i]) for i in range(3))
    return core._euler(R._mm(core._t(Mp), M)), math.atan2(dot(fd, z), dot(fd, ua))


def limb(r, fr, upper, lower, end, target, pole):
    """two_bone as key-pose turns on top of the bones' rest: {upper: (x, y, z), lower: (x, y, z)} in degrees."""
    (ax, ay, az), k = two_bone(r, fr, upper, lower, end, target, pole)
    up, lo = r.by_name[upper], r.by_name[lower]
    return {upper: delta((ax / DEG, ay / DEG, az / DEG), up.rot), lower: ((k / DEG - lo.rot[0] + 180) % 360 - 180, -lo.rot[1], -lo.rot[2])}


def rest_limb(r, upper, lower, end, target, pole):
    """Sets the limb's rest turns so that at rest its end is at target (x, height, z)."""
    (ax, ay, az), k = two_bone(r, core.frames(r, {}), upper, lower, end, model(target), pole)
    r.by_name[upper].rot = (ax / DEG, ay / DEG, az / DEG)
    r.by_name[lower].rot = (k / DEG, 0.0, 0.0)


def orient(r, fr, bone, ydir, xdir):
    """The turn (degrees, on top of its rest) that lays a bone's +y along ydir with its +x as near xdir as that allows
    (model directions), its parent posed as in fr: a pendulum hung plumb, a hand set upright."""
    b = r.by_name[bone]
    y = unit(ydir)
    x = unit(v(xdir, y, -dot(xdir, y)))
    z = cross(x, y)
    M = tuple((x[i], y[i], z[i]) for i in range(3))
    e = core._euler(R._mm(core._t(fr[b.parent.name][0]), M))
    return delta(tuple(c / DEG for c in e), b.rot)


def at(fr, bone, local=(0, 0, 0)):
    """Where a point on a bone is, (x, height, z)."""
    return height(core.point(fr, bone, local))


def retrack(r, clip, fn):
    """Rewrites a baked clip tick by tick: fn(t, sample, frames) -> {bone: (x, y, z) turn on top of rest, degrees}; the
    bones it returns are keyed on every tick (each a straight line to the next, as baked clips are)."""
    c = r.clips[clip]
    n = int(round(c.length / core.TICK))
    out = {}
    for k in range(n + 1):
        t = min(c.length, k * core.TICK)
        s = core.sample(r, clip, t)
        for b, e in fn(t, s, core.frames(r, s)).items():
            out.setdefault(b, []).append((t, tuple(e), "cat"))
    for b, keys in out.items():
        c.tracks[(b, "rot")] = keys
