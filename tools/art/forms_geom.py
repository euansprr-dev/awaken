"""The geometry the forms are held to (SPEC 21.11): the vanilla bodies as the game builds them, the helpers that place kit pieces, and the
placement check that fails the build when a piece is not where it should be.

A kit piece is a cube in the frame of one vanilla part. The rules, checked for every layout and rung (see validate()):
  attached   a piece touches the vanilla body, or another piece that does (a chain: a horn's second segment starts where its first ends);
             only a piece marked float (a few shards in the top rung) may hang free, and then within FLOAT_MAX of the body, at most 6;
  face       nothing may sit over a creature's face: the eye band in front of its face plane is a keep-out (tusks, horns and crowns go round it);
  seen       a piece must not hide inside the body (every sample inside: it can never be seen);
  reach      the farthest point of a piece from the body stays within REACH[rung] (a crown is not a mast).
Axes are the game's: x to the creature's left, y DOWN (up is -y), the front is -z; rotation order Z, Y, X (matrix Rz.Ry.Rx), as ModelPart applies it.
"""
import math

PI2 = math.pi / 2


def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(R, v):
    return [sum(R[i][k] * v[k] for k in range(3)) for i in range(3)]


def aim(dx, dy, dz):
    """The rotation (rx, ry, rz) that turns a cube's up (-y) to point along (dx, dy, dz) (up is -y: a horn points along (0, -1, 0))."""
    n = math.sqrt(dx * dx + dy * dy + dz * dz)
    dx, dy, dz = dx / n, dy / n, dz / n
    rx = math.asin(max(-1.0, min(1.0, -dz)))
    return (rx, 0.0, math.atan2(dx, -dy))


def cube(part, name, box, pose=(0, 0, 0, 0, 0, 0), mat="bone", float_=False):
    return dict(part=part, name=name, box=tuple(box), pose=tuple(pose), mat=mat, float_=float_)


def curve(part, name, base, segs, thick, mat="bone"):
    """A bent horn or tusk. `segs` is [(length, (dx, dy, dz)), ...]: each segment points along its direction (up is -y) and starts where the last ended."""
    out, pos = [], list(base)
    for i, (length, d) in enumerate(segs):
        t = max(1, thick - (1 if i >= 2 else 0))
        rx, ry, rz = aim(*d)
        out.append(cube(part, f"{name}{i}", (-t / 2, -length, -t / 2, t, length, t), (*pos, rx, ry, rz), mat))
        v = apply(rot(rx, ry, rz), (0, -length, 0))
        pos = [pos[k] + v[k] for k in range(3)]
    return out


def spike(part, name, x, y, z, length, up=(0, -1, 0), thick=1, mat="bone"):
    """A spike standing on a surface at (x, y, z), pointing along `up`."""
    return curve(part, name, (x, y, z), [(length, up)], thick, mat)

# ---------------------------------------------------------------------------------------------------------------------
# The vanilla bodies, from ModelPart definitions in the 26.3 sources: part -> (offset, rest rotation, [(u, v, (x, y, z, w, h, d), mirror)])


def _legs(names, box, uv=(0, 16)):
    return [(n, o, (0, 0, 0), [(uv[0], uv[1], box, m)]) for n, o, m in names]


BODIES = {
    "pig": [("head", (0, 12, -6), (0, 0, 0), [(0, 0, (-4, -4, -8, 8, 8, 8), False), (16, 16, (-2, 0, -9, 4, 3, 1), False)]),
            ("body", (0, 11, 2), (PI2, 0, 0), [(28, 8, (-5, -10, -7, 10, 16, 8), False)])]
           + _legs([("right_hind_leg", (-3, 18, 7), False), ("left_hind_leg", (3, 18, 7), True), ("right_front_leg", (-3, 18, -5), False), ("left_front_leg", (3, 18, -5), True)], (-2, 0, -2, 4, 6, 4)),
    "cow": [("head", (0, 4, -8), (0, 0, 0), [(0, 0, (-4, -4, -6, 8, 8, 6), False), (1, 33, (-3, 1, -7, 6, 3, 1), False), (22, 0, (-5, -5, -5, 1, 3, 1), False), (22, 0, (4, -5, -5, 1, 3, 1), False)]),
            ("body", (0, 5, 2), (PI2, 0, 0), [(18, 4, (-6, -10, -7, 12, 18, 10), False), (52, 0, (-2, 2, -8, 4, 6, 1), False)])]
           + _legs([("right_hind_leg", (-4, 12, 7), False), ("left_hind_leg", (4, 12, 7), True), ("right_front_leg", (-4, 12, -5), False), ("left_front_leg", (4, 12, -5), True)], (-2, 0, -2, 4, 12, 4)),
    "sheep": [("head", (0, 6, -8), (0, 0, 0), [(0, 0, (-3, -4, -6, 6, 6, 8), False)]),
              ("body", (0, 5, 2), (PI2, 0, 0), [(28, 8, (-4, -10, -7, 8, 16, 6), False)])]
             + _legs([("right_hind_leg", (-3, 12, 7), False), ("left_hind_leg", (3, 12, 7), True), ("right_front_leg", (-3, 12, -5), False), ("left_front_leg", (3, 12, -5), True)], (-2, 0, -2, 4, 12, 4)),
    "chicken": [("head", (0, 15, -4), (0, 0, 0), [(0, 0, (-2, -6, -2, 4, 6, 3), False), (14, 0, (-2, -4, -4, 4, 2, 2), False), (14, 4, (-1, -2, -3, 2, 2, 2), False)]),
                ("body", (0, 16, 0), (PI2, 0, 0), [(0, 9, (-3, -4, -3, 6, 8, 6), False)]),
                ("right_leg", (-2, 19, 1), (0, 0, 0), [(26, 0, (-1, 0, -3, 3, 5, 3), False)]), ("left_leg", (1, 19, 1), (0, 0, 0), [(26, 0, (-1, 0, -3, 3, 5, 3), False)]),
                ("right_wing", (-4, 13, 0), (0, 0, 0), [(24, 13, (0, 0, -3, 1, 4, 6), False)]), ("left_wing", (4, 13, 0), (0, 0, 0), [(24, 13, (-1, 0, -3, 1, 4, 6), True)])],
    "zombie": [("head", (0, 0, 0), (0, 0, 0), [(0, 0, (-4, -8, -4, 8, 8, 8), False)]), ("body", (0, 0, 0), (0, 0, 0), [(16, 16, (-4, 0, -2, 8, 12, 4), False)]),
               ("right_arm", (-5, 2, 0), (0, 0, 0), [(40, 16, (-3, -2, -2, 4, 12, 4), False)]), ("left_arm", (5, 2, 0), (0, 0, 0), [(40, 16, (-1, -2, -2, 4, 12, 4), True)]),
               ("right_leg", (-1.9, 12, 0), (0, 0, 0), [(0, 16, (-2, 0, -2, 4, 12, 4), False)]), ("left_leg", (1.9, 12, 0), (0, 0, 0), [(0, 16, (-2, 0, -2, 4, 12, 4), True)])],
    "skeleton": [("head", (0, 0, 0), (0, 0, 0), [(0, 0, (-4, -8, -4, 8, 8, 8), False)]), ("body", (0, 0, 0), (0, 0, 0), [(16, 16, (-4, 0, -2, 8, 12, 4), False)]),
                 ("right_arm", (-5, 2, 0), (0, 0, 0), [(40, 16, (-1, -2, -1, 2, 12, 2), False)]), ("left_arm", (5, 2, 0), (0, 0, 0), [(40, 16, (-1, -2, -1, 2, 12, 2), True)]),
                 ("right_leg", (-2, 12, 0), (0, 0, 0), [(0, 16, (-1, 0, -1, 2, 12, 2), False)]), ("left_leg", (2, 12, 0), (0, 0, 0), [(0, 16, (-1, 0, -1, 2, 12, 2), True)])],
    "spider": [("head", (0, 15, -3), (0, 0, 0), [(32, 4, (-4, -4, -8, 8, 8, 8), False)]), ("body0", (0, 15, 0), (0, 0, 0), [(0, 0, (-3, -3, -3, 6, 6, 6), False)]),
               ("body1", (0, 15, 9), (0, 0, 0), [(0, 12, (-5, -4, -6, 10, 8, 12), False)])]
              + [(f"{side}_leg{i}", (s * 4, 15, z), (0, s * ry, s * rz), [(18, 0, (-15 if s < 0 else -1, -1, -1, 16, 2, 2), s > 0)])
                 for s, side in ((-1, "right"), (1, "left")) for i, (z, ry, rz) in enumerate(((2, -0.78, 0.78), (1, -0.39, 0.58), (0, 0.39, 0.58), (-1, 0.78, 0.78)))],
    "creeper": [("head", (0, 6, 0), (0, 0, 0), [(0, 0, (-4, -8, -4, 8, 8, 8), False)]), ("body", (0, 6, 0), (0, 0, 0), [(16, 16, (-4, 0, -2, 8, 12, 4), False)])]
              + _legs([("right_hind_leg", (-2, 18, 4), False), ("left_hind_leg", (2, 18, 4), True), ("right_front_leg", (-2, 18, -4), False), ("left_front_leg", (2, 18, -4), True)], (-2, 0, -2, 4, 6, 4)),
}

# the poses the game shows a creature standing still in daylight (the preview uses them; the rules are checked at rest)
POSES = {"zombie": {"right_arm": (-1.396, 0.1, 0), "left_arm": (-1.396, -0.1, 0)}}


def part_box(layout, part, which=0):
    """A vanilla part's `which`-th cube box (x, y, z, w, h, d) in the part's frame."""
    for name, off, rt, cubes in BODIES[layout]:
        if name == part:
            return cubes[which][2]
    raise KeyError((layout, part))


def centre_x(layout, part):
    x, y, z, w, h, d = part_box(layout, part)
    return x + w / 2

# ---------------------------------------------------------------------------------------------------------------------
# Face keep-outs: where a face looks (the eyes' band across the head, from the face plane forward). part, (y0, y1), the front plane z, half width


EYE_BAND = {
    "pig": ("head", (-2.3, 1.3), -8.0, 4.1),
    "cow": ("head", (-2.5, 1.0), -6.0, 4.1),
    "sheep": ("head", (-2.5, 1.0), -6.0, 3.1),
    "chicken": ("head", (-5.0, -2.0), -2.0, 2.1),
    "zombie": ("head", (-5.5, -2.5), -4.0, 4.1),
    "skeleton": ("head", (-6.5, -2.5), -4.0, 4.1),
    "spider": ("head", (-3.4, 0.4), -8.0, 4.1),
    "creeper": ("head", (-7.9, -0.1), -4.0, 4.1),
}

FLOAT_MAX = 9.0
REACH = {1: 7.0, 2: 8.0, 3: 9.0, 4: 11.0, 5: 13.0}
TOL = 0.35

# ---------------------------------------------------------------------------------------------------------------------
# Oriented boxes


class Obb:
    def __init__(self, centre, R, half):
        self.c, self.R, self.h = centre, R, half

    def local(self, p):
        d = [p[i] - self.c[i] for i in range(3)]
        return [sum(self.R[k][i] * d[k] for k in range(3)) for i in range(3)]  # R^T d

    def dist(self, p):
        l = self.local(p)
        return math.sqrt(sum(max(abs(l[i]) - self.h[i], 0.0) ** 2 for i in range(3)))

    def inside(self, p, margin=0.0):
        l = self.local(p)
        return all(abs(l[i]) < self.h[i] - margin for i in range(3))


def part_xf(layout, part, pose=None):
    for name, off, rt, cubes in BODIES[layout]:
        if name == part:
            return list(off), rot(*(pose or rt))
    raise KeyError((layout, part))


def vanilla_obbs(layout):
    out = []
    for name, off, rt, cubes in BODIES[layout]:
        R = rot(*rt)
        for (u, v, box, mirror) in cubes:
            x, y, z, w, h, d = box
            c = apply(R, (x + w / 2, y + h / 2, z + d / 2))
            out.append((name, Obb([c[i] + off[i] for i in range(3)], R, (w / 2, h / 2, d / 2))))
    return out


def kit_obb(layout, cb):
    off, Rp = part_xf(layout, cb["part"])
    x, y, z, w, h, d = cb["box"]
    pose = cb["pose"]
    Rc = rot(*pose[3:])
    cl = apply(Rc, (x + w / 2, y + h / 2, z + d / 2))
    cl = [cl[i] + pose[i] for i in range(3)]
    c = apply(Rp, cl)
    return Obb([c[i] + off[i] for i in range(3)], mul(Rp, Rc), (w / 2, h / 2, d / 2))


def samples(ob, n=3):
    """A grid of points through an oriented box (corners, edge and face middles, centre for n=3)."""
    pts = []
    for i in range(n):
        for j in range(n):
            for k in range(n):
                f = [(i / (n - 1) * 2 - 1), (j / (n - 1) * 2 - 1), (k / (n - 1) * 2 - 1)]
                loc = [f[a] * ob.h[a] for a in range(3)]
                w = apply(ob.R, loc)
                pts.append([w[a] + ob.c[a] for a in range(3)])
    return pts


def keepout(layout):
    part, (y0, y1), zf, hw = EYE_BAND[layout]
    off, R = part_xf(layout, part)
    lo, hi = (-hw, y0, zf - 6.0), (hw, y1, zf - 0.02)
    c = apply(R, [(lo[a] + hi[a]) / 2 for a in range(3)])
    return Obb([c[a] + off[a] for a in range(3)], R, [(hi[a] - lo[a]) / 2 for a in range(3)])

# ---------------------------------------------------------------------------------------------------------------------
# The check


def validate(layout, rung, cubes):
    """Every violation of the placement rules, as readable lines (empty when the kit is where it should be)."""
    bad = []
    van = [o for _, o in vanilla_obbs(layout)]
    obs = [kit_obb(layout, c) for c in cubes]
    floating = [i for i, c in enumerate(cubes) if c.get("float_")]
    if len(floating) > 6:
        bad.append(f"{len(floating)} floating pieces (at most 6)")
    # attached: a graph from the body
    touch = [[False] * len(cubes) for _ in cubes]
    on_body = [False] * len(cubes)
    for i, ob in enumerate(obs):
        pts = samples(ob)
        on_body[i] = any(v.dist(p) <= TOL for v in van for p in pts)
        for j, other in enumerate(obs):
            if i != j and any(other.dist(p) <= TOL for p in pts):
                touch[i][j] = touch[j][i] = True
    reached, stack = set(i for i, b in enumerate(on_body) if b), [i for i, b in enumerate(on_body) if b]
    while stack:
        i = stack.pop()
        for j in range(len(cubes)):
            if touch[i][j] and j not in reached:
                reached.add(j)
                stack.append(j)
    for i, c in enumerate(cubes):
        if i in reached:
            continue
        if c.get("float_"):
            near = min(v.dist(p) for v in van for p in samples(obs[i]))
            if near > FLOAT_MAX:
                bad.append(f"{c['part']}/{c['name']}: floats {near:.1f} from the body (at most {FLOAT_MAX})")
        else:
            near = min(v.dist(p) for v in van for p in samples(obs[i]))
            bad.append(f"{c['part']}/{c['name']}: not attached to the body (nearest {near:.2f} away)")
    # face keep-out
    ko = keepout(layout)
    for i, c in enumerate(cubes):
        if any(ko.inside(p) for p in samples(obs[i], 5)):
            bad.append(f"{c['part']}/{c['name']}: over the face")
    # seen, and reach
    for i, c in enumerate(cubes):
        pts = samples(obs[i], 3)
        dmin = [min(v.dist(p) for v in van) for p in pts]
        if all(d <= 1e-6 for d in dmin):
            bad.append(f"{c['part']}/{c['name']}: hidden inside the body")
        if max(dmin) > REACH[rung] and not c.get("float_"):
            bad.append(f"{c['part']}/{c['name']}: reaches {max(dmin):.1f} from the body (at most {REACH[rung]})")
        x, y, z, w, h, d = c["box"]
        if min(w, h, d) < 0.5 or max(w, h, d) > 14:
            bad.append(f"{c['part']}/{c['name']}: odd size {w}x{h}x{d}")
    return bad
