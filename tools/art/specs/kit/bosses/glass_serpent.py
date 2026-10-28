"""The Glass Serpent (SPEC §35.11, docs/bosses/ROLLOUT.md): the Glass gate's Lord, a Serpent. The Hourglass, Euan's pick
from its silhouette round: "Your reflection blinks first."

A serpent you can see through. Its body is a chain of hollow glass frames, each one an hourglass with sand pouring
through it, strung on a spine of black glass. It lies coiled on the floor like a spring and raises its head out of the
middle: a horned skull of black glass with two mirror eyes. Its neck frames are empty: the sand has run down out of
them into the coil.

Every pose is a line: where the coil lies and where the neck rises, and which way the skull looks. The frames are laid
along it one after another (each exactly its own length on from the last), so between any two poses the body stays
one piece and bends the way a serpent bends; the frames stay upright where it lies on the floor. Between two keys the
frames' turns can blend through shapes that sag into the floor, so after staging every clip that would is laid back on
it tick by tick (_floored).

Moves (SPEC §35.11): it draws its head back into an S and bites (LIGHT), and again from the side; Glass Lunge (it
coils tight and rears, then the spring lets go and the whole body shoots out straight behind its head: HEAVY); Shard
Spray (the coil rises into a tall spring, then slams flat and shards fly off it: HEAVY); Burrow Strike (it dives head
first into the sand and is gone, and bursts up out of the floor behind you: HEAVY); Erupting Glass (it drives its jaw
into the sand and the floor erupts in glass where you stand); a lash of its tail for whoever is behind it; and the
Glass Storm (the signature: it rises and whirls on itself faster and faster, a spout of glass and sand, then bursts).
It waits coiled with its head laid on its coil, lifts it when you come in, and dies rearing up and crashing down
across its own coil, its head on the sand.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import cracks
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "glass_serpent"
SCALE = 1.0

# the two chains out of the root (the coil's middle, where the neck rises): (length, width, height, kind) from the root
# outward. Full frames have sand in them; the tail's are smaller with only a heap; the neck's are empty.
BODY = ([(9, 12, 15, "full")] + [(11, 13 - k // 4, 16 - k // 4, "full") for k in range(7)] +
        [(10, 11, 13, "tail"), (9, 10, 12, "tail"), (8, 9, 11, "tail"), (7, 8, 10, "tail"), (6, 7, 8, "tail"), (5, 6, 7, "tail")])
NECK = [(10, 11, 14, "neck"), (10, 11, 13, "neck"), (10, 10, 13, "neck"), (9, 10, 12, "neck"), (9, 9, 12, "neck")]
NB, NN = len(BODY), len(NECK)
BNAMES = [f"b{k}" for k in range(NB)]
NNAMES = [f"n{k}" for k in range(NN)]
CHAIN = BNAMES + NNAMES
ROOT_H = 13.0  # the root's height at rest
DOWN = (0.0, 1.0, 0.0)  # model down


# ---------------------------------------------------------------------------------------------------------------------
# Lines: the body's centre line as points (x, height, z), the root first

def _cr(p0, p1, p2, p3, t):
    """Centripetal-ish Catmull-Rom (uniform, the ends clamped by the caller) at t in 0..1 between p1 and p2."""
    t2, t3 = t * t, t * t * t
    return tuple(0.5 * (2 * p1[i] + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2
                        + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3) for i in range(3))


def _dense(ctrl, step=0.4):
    """The smooth line through the control points, as points about step apart."""
    if len(ctrl) < 2:
        return list(ctrl)
    ext = [tuple(2 * ctrl[0][i] - ctrl[1][i] for i in range(3))] + list(ctrl) + [tuple(2 * ctrl[-1][i] - ctrl[-2][i] for i in range(3))]
    out = [ctrl[0]]
    for k in range(1, len(ext) - 2):
        d = math.dist(ext[k], ext[k + 1])
        n = max(1, int(math.ceil(d / step)))
        for j in range(1, n + 1):
            out.append(_cr(ext[k - 1], ext[k], ext[k + 1], ext[k + 2], j / n))
    return out


def _walk(pts, lengths):
    """Pivots along a dense line from its first point: each the given length (a straight chord) on from the last; past
    the line's end it carries on straight."""
    out = [pts[0]]
    cur, i = pts[0], 0
    for L in lengths:
        j = i + 1
        while j < len(pts) and math.dist(pts[j], cur) < L:
            j += 1
        if j < len(pts):
            a, b = pts[j - 1], pts[j]
            lo, hi = 0.0, 1.0
            for _ in range(24):
                m = (lo + hi) / 2
                if math.dist(tuple(a[c] + (b[c] - a[c]) * m for c in range(3)), cur) < L:
                    lo = m
                else:
                    hi = m
            p = tuple(a[c] + (b[c] - a[c]) * hi for c in range(3))
            i = j - 1
        else:
            e = pts[-1]
            d = ik.unit(ik.sub(pts[-1], pts[-2])) if len(pts) > 1 else (0, 1, 0)
            q = ik.sub(e, cur)
            bq = ik.dot(d, q)
            t = -bq + math.sqrt(max(0.0, bq * bq - (ik.dot(q, q) - L * L)))
            p = ik.v(e, d, t)
            pts = pts + [p]
            i = len(pts) - 1
        out.append(p)
        cur = p
    return out


def _frac(k, n):
    return k / max(1, n - 1)


def coil(r_in=10.0, pitch=14.0, start=110.0, turns=1.25, rise=0.0, lift=0.0, stretch=1.0, squash=1.0, wave=(0.0, 0.0, 0.0),
         bob=(0.0, 0.0, 0.0), shift=(0.0, 0.0), spin=0.0, n=60, tail=None):
    """The coil from the root out: a flat spiral on the floor from radius r_in growing by pitch a turn, starting at
    angle start (degrees; 0 is its left, 90 its back: the coil's inner end lies behind the root, so the body comes
    into the neck heading forward and the neck's back faces back) and winding round to its right. rise lifts the inner turns into a
    spring; stretch and squash scale it front to back and side to side; wave (amp, waves, phase) ripples it in and out,
    bob (amp, waves, phase) up and down; spin turns it round (degrees). tail: points that replace the outer end."""
    pts = []
    for k in range(n + 1):
        u = k / n
        th = (start + spin + turns * 360.0 * u) * DEG
        r = r_in + pitch * turns * u + wave[0] * math.sin(2 * math.pi * wave[1] * u + wave[2])
        h = _half(u) + lift + rise * (1 - u) + bob[0] * math.sin(2 * math.pi * bob[1] * u + bob[2])
        pts.append((r * math.cos(th) * squash + shift[0], h, r * math.sin(th) * stretch + shift[1]))
    if tail:
        cut = int(n * tail[0])
        pts = pts[:cut] + list(tail[1])
    return pts


def _half(u):
    """Half a frame's height along the coil (u 0 at the root, 1 at the tail tip): what keeps it on the floor."""
    k = min(NB - 1, int(u * NB))  # near enough: the frames are not evenly spread along u, so take the tallest round it
    return max(BODY[j][2] for j in range(max(0, k - 1), min(NB, k + 2))) / 2.0


def line(*pts):
    return [tuple(float(c) for c in p) for p in pts]


# ---------------------------------------------------------------------------------------------------------------------
# The body

def _seg(bone, k, L, w, h, kind, nm):
    """One hourglass frame on its bone, the body running along the bone's +y and its back (the spine) on its -z: two
    glass plates (top and foot) on four glass posts, the black-glass spine along its back, and in a full frame the sand:
    a heap in the foot, a thread pouring through the waist, what's left lying under the top."""
    x0, z0 = -w / 2, -h / 2
    bone.cube(f"sp{nm}", -1.5, 0, z0 - 2, 3, L, 2)  # the spine, end to end so it runs on through every joint
    bone.cube(f"pt{nm}", x0, 1, z0, w, L - 2, 1)
    bone.cube(f"pb{nm}", x0, 1, h / 2 - 1, w, L - 2, 1)
    for a, (px, py) in enumerate(((x0, 1), (w / 2 - 1, 1), (x0, L - 2), (w / 2 - 1, L - 2))):
        bone.cube(f"po{nm}{'abcd'[a]}", px, py, z0 + 1, 1, 1, h - 2)
    if kind == "full":
        bone.cube(f"sh{nm}", -(w - 3) / 2, 2, h / 2 - 4, w - 3, L - 4, 3)  # the heap
        bone.cube(f"so{nm}", -0.5, L / 2 - 0.5, z0 + 3, 1, 1, h - 7)  # the thread pouring through the waist
        bone.cube(f"st{nm}", -(w - 5) / 2, 3, z0 + 1, w - 5, L - 6, 2)  # what's left up top
    elif kind == "tail":
        bone.cube(f"sh{nm}", -(w - 3) / 2, 2, h / 2 - 3, w - 3, L - 4, 2)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    root = base.bone("root", (0, -ROOT_H, 0))
    prev = root
    for k, (L, w, h, kind) in enumerate(BODY):
        b = prev.bone(BNAMES[k], (0, 0, 0) if k == 0 else (0, BODY[k - 1][0], 0))
        _seg(b, k, L, w, h, kind, str(k))
        prev = b
    prev = root
    for k, (L, w, h, kind) in enumerate(NECK):
        b = prev.bone(NNAMES[k], (0, 0, 0) if k == 0 else (0, NECK[k - 1][0], 0))
        _seg(b, k, L, w, h, kind, "n" + str(k))
        prev = b
    # the skull: black glass, a heavy brow over two mirror eyes, a long snout, the jaw hinged under it, glass fangs,
    # two horns a side swept back off the crown
    hd = prev.bone("head", (0, NECK[-1][0], 0))  # its rest turn is set with the rest line (_set_rest)
    hd.cube("skull", -8, -13, -14, 16, 13, 15)
    hd.cube("brow", -8.5, -14, -15, 17, 2, 4)
    hd.cube("snout", -5.5, -9, -25, 11, 7, 11)
    hd.cube("tip", -4, -8, -29, 8, 5, 4)
    for s, nm in ((1, "l"), (-1, "r")):
        hd.cube("fang_" + nm, s * 3.5 - 0.5, -2, -24, 1, 3, 1)
        h1 = hd.bone("horn_" + nm, (s * 6, -12, -2), (-66, 0, s * 40))
        h1.cube("horn_" + nm, -1.5, -12, -1.5, 3, 12, 3)
        h2 = h1.bone("horn2_" + nm, (0, -11, 0), (-34, 0, -s * 8))
        h2.cube("horn2_" + nm, -1, -10, -1, 2, 10, 2)
        h2.cube("hornt_" + nm, -0.5, -12, -0.5, 1, 2, 1)
    jw = hd.bone("jaw", (0, -1, -6))
    jw.cube("jaw", -5, 0, -21, 10, 3, 21)
    for s, nm in ((1, "l"), (-1, "r")):
        jw.cube("lfang_" + nm, s * 3 - 0.5, -2, -18, 1, 2, 1)
    _set_rest(r)
    r.look = [("head", 0.6, 0.5)]
    r.shadow = 2.4
    r.size = 1.0
    # the tail's tip trails a little behind every move (SPEC §35.10)
    r.springs = [("b12", 0.5, 1.8, 0.12), ("b13", 0.5, 2.2, 0.16)]
    r.guard = list(BNAMES) + ["head", "jaw"]  # the coil lies on the floor, never in it, and the skull stays out of it
    r.under = DIVES + ("move_erupt",)  # into the floor on purpose: the whole of it, and its jaw driven into the sand
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the line, then each frame laid along it

REST_NECK = line((0, ROOT_H, 0), (0, 22, 2), (0, 33, 4), (0, 42, 1), (0, 47, -5), (0, 50, -14))
REST_HEAD = (0.0, 22.0)  # (yaw + to its left, pitch + down), degrees


def rest_coil():
    return coil()


def _place(r, root, coil_pts, neck_pts, head=REST_HEAD, roll=0.0):
    """World rotations (model frame) and pivots for every chain bone and the head, for the root at root (x, height, z)
    with the coil and neck lines (each from the root)."""
    bpiv = [ik.model(p) for p in _walk(_dense([root] + list(coil_pts)), [s[0] for s in BODY])]
    npiv = [ik.model(p) for p in _walk(_dense([root] + list(neck_pts[1:])), [s[0] for s in NECK])]
    W, left = _lay(bpiv)
    for k in range(NN):
        y = ik.unit(ik.sub(bpiv[k + 1], bpiv[k]))  # the bone runs out toward the tail
        t = tuple(-c for c in y)  # the way to the head
        up = ik.cross(t, DOWN)
        n = math.sqrt(ik.dot(up, up))
        carried = None if left is None else ik.unit(ik.v(left, t, -ik.dot(left, t)))
        w = 1.0 if carried is None else max(0.0, min(1.0, (n - 0.25) / 0.45))
        upright = ik.unit(up) if n > 1e-6 else carried
        lft = ik.unit(ik.v(tuple(c * w for c in upright), carried, 1 - w)) if carried is not None else upright
        y = ik.unit(ik.sub(npiv[k + 1], npiv[k]))
        lft = ik.unit(ik.v(left, y, -ik.dot(left, y)))
        if roll and k >= NN - 2:
            c, s = math.cos(roll * DEG * 0.5), math.sin(roll * DEG * 0.5)
            dn = ik.cross(lft, y)
            lft = ik.unit(ik.v(tuple(c * q for q in lft), dn, s))
        left = lft
        W[NNAMES[k]] = (lft, y, ik.cross(lft, y))
    yaw, pitch = head[0] * DEG, head[1] * DEG
    f = (math.sin(yaw) * math.cos(pitch), math.sin(pitch), -math.cos(yaw) * math.cos(pitch))
    upv = ik.unit(ik.v((0, -1, 0), f, -ik.dot((0, -1, 0), f)))
    if roll:
        c, s = math.cos(roll * DEG), math.sin(roll * DEG)
        upv = ik.unit(ik.v(tuple(c * q for q in upv), ik.cross(f, upv), s))
    zh = tuple(-c for c in f)
    yh = tuple(-c for c in upv)
    W["head"] = (ik.cross(yh, zh), yh, zh)
    return W, ik.model(root)


def _lay(bpiv):
    """World rotations (model frame) for the body's frames on their pivots (model pixels, the tail tip last), and the
    left it hands the neck. The serpent's left runs from the tail to the head: upright where it lies on the floor,
    carried on where it rises, so a frame never turns over on itself."""
    W, left = {}, None
    for k in reversed(range(NB)):
        y = ik.unit(ik.sub(bpiv[k + 1], bpiv[k]))  # the bone runs out toward the tail
        t = tuple(-c for c in y)  # the way to the head
        up = ik.cross(t, DOWN)
        n = math.sqrt(ik.dot(up, up))
        carried = None if left is None else ik.unit(ik.v(left, t, -ik.dot(left, t)))
        w = 1.0 if carried is None else max(0.0, min(1.0, (n - 0.25) / 0.45))
        upright = ik.unit(up) if n > 1e-6 else carried
        lft = ik.unit(ik.v(tuple(c * w for c in upright), carried, 1 - w)) if carried is not None else upright
        left = lft
        x = tuple(-c for c in lft)
        W[BNAMES[k]] = (x, y, ik.cross(x, y))
    return W, left


DIVES = ("move_burrow",)  # clips where the whole body goes down under the floor
DIP = 1.0  # pixels a clip's frames may press into the floor (the sand gives) before the clip is laid on it
EASE = 1.0  # pixels over which a frame coming down onto the floor is eased up onto it


def _floor_at(k):
    """How high a frame's line lies when the frame rests on the floor (pixels): half its height, the tallest round it."""
    return max(BODY[j][2] for j in range(max(0, k - 1), min(NB, k + 2))) / 2.0


def _onto(h, f):
    """A height h eased up onto the floor height f: itself from EASE above it, f from EASE below it, and between them
    a curve that meets both with the same slope, so a joint coming down onto the floor slows onto it, never bumps."""
    d = h - f
    return h if d >= EASE else f if d <= -EASE else f + (d + EASE) ** 2 / (4 * EASE)


def _floored(r, clip):
    """Keeps the coil on the floor through a whole clip, tick by tick: the keys lie on it, but between a coil and a
    straight line the frames' turns blend through shapes that sag into it. On every tick each joint of the body is
    eased up onto the floor where it would go in (where it goes, round and along, stays) and the frames laid along the
    joints again; the root and the neck stay as they were. A clip that never goes in keeps its keys."""
    if core.sinks(r, clip, BNAMES) <= DIP:
        return

    def fn(t, s, fr):
        out = {}
        pts = [ik.at(fr, b) for b in BNAMES] + [ik.at(fr, BNAMES[-1], (0, BODY[-1][0], 0))]
        lifted = [pts[0]] + [(p[0], _onto(p[1], _floor_at(min(k, NB - 1))), p[2]) for k, p in enumerate(pts) if k]
        W, _ = _lay([ik.model(p) for p in lifted])
        prev = fr["root"][0]
        for b in BNAMES:
            e = core._euler(R._mm(core._t(prev), _mat(W[b])))
            out[b] = ik.delta(tuple(c / DEG for c in e), r.by_name[b].rot)
            prev = _mat(W[b])
        return out
    ik.retrack(r, clip, fn)


def _mat(cols):
    x, y, z = cols
    return tuple((x[i], y[i], z[i]) for i in range(3))


def _locals(r, W, spin=0.0):
    """Each bone's whole turn in its parent's frame (degrees), from the world rotations."""
    out = {}
    par = {"b0": None, "n0": None}
    for names in (BNAMES, NNAMES):
        for k, b in enumerate(names):
            par[b] = names[k - 1] if k else None
    par["head"] = NNAMES[-1]
    ident = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
    for b in CHAIN + ["head"]:
        Mp = ident if par[b] is None else _mat(W[par[b]])
        e = core._euler(R._mm(core._t(Mp), _mat(W[b])))
        out[b] = tuple(c / DEG for c in e)
    return out


def _set_rest(r):
    W, rp = _place(r, (0, ROOT_H, 0), rest_coil(), REST_NECK)
    for b, e in _locals(r, W).items():
        r.by_name[b].rot = e


def pose(r, root=(0.0, ROOT_H, 0.0), coil_pts=None, neck=None, head=REST_HEAD, jaw=0.0, spin=0.0, roll=0.0, horns=0.0, extra=None):
    """A key pose: the root at root (x, height, z), the coil and the neck along their lines (each from the root), the
    skull looking head = (yaw, pitch), the jaw open (degrees), the whole turned round by spin (degrees)."""
    coil_pts = coil_pts if coil_pts is not None else rest_coil()
    neck = neck if neck is not None else REST_NECK
    W, _ = _place(r, root, coil_pts, neck, head, roll)
    loc = _locals(r, W)
    p = {}
    rp = (root[0], root[1] - ROOT_H, root[2])
    if spin or any(abs(c) > 1e-3 for c in rp):
        p["root"] = {"rot": (0, spin, 0), "pos": rp}
    for b, e in loc.items():
        d = ik.delta(e, r.by_name[b].rot)
        if max(abs(c) for c in d) > 0.25:  # a frame the pose leaves where it lies isn't keyed (nor counted as moving)
            p[b] = d
    p["jaw"] = (jaw, 0, 0)
    if horns:
        p["horn_l"] = (horns, 0, 0)
        p["horn_r"] = (horns, 0, 0)
    if extra:
        p.update(extra)
    return p


def neck(*pts, root=(0, ROOT_H, 0)):
    """A neck line from the root (relative points: each (x, height, z) added to the root)."""
    return [tuple(root)] + [(root[0] + p[0], root[1] + p[1], root[2] + p[2]) for p in pts]


# ---------------------------------------------------------------------------------------------------------------------
# Paint

def paint(r, P_):
    """Four materials in a value order: the mirror eyes and the glints on the glass brightest, then the sand (warm, the
    thread pouring through each waist catching the light), then the pale glass of the frames, then the black glass of
    the spine and the skull, glossy, its edges lit. The neck's frames are empty."""
    glass = Ramp("#1c3a3e", "#29504f", "#3a6a68", "#4e8682", "#68a29c", "#86bcb4", "#a6d4ca", "#c6e8de", "#e4f6ee")
    black = Ramp("#040708", "#0a1012", "#11191c", "#1a2428", "#253236", "#334246", "#445458", "#5c6e70")
    sand = Ramp("#5a3e22", "#76542e", "#94703e", "#b28c52", "#cca868", "#e2c486", "#f2dcaa")
    glint = ("#d8f6f0", "#f2fffc", "#ffffff")
    of = lambda *ps: parts.cubes_of(r, *ps)

    def flat(c, ramp, vals, pattern=None, noise=0.0):
        """A chain frame's faces by which way they face on the serpent (its back is north, its belly south)."""
        for fname, v in vals.items():
            P_.paint(c, ramp, v, pattern, grad=0.0, light=0.0, rim=0.0, ao=0.0, faces=(fname,), noise=noise, nscale=4)

    def streak(c):
        """A glint across the glass: a diagonal line of light and the pane darker either side of it."""
        def f(face, i, j):
            d = (i - j + R._h(c, face.name) % 5) % 7
            return 2.2 if d == 0 else 1.0 if d == 1 else -0.4
        return f
    # the frames: pale glass, lit along the back, darker underneath, a glint run across every plate
    for c in of("pt", "pb"):
        flat(c, glass, {"north": 6.0, "south": 3.0, "west": 4.6, "east": 4.2, "top": 4.4, "bottom": 4.4}, streak(c))
    for c in of("po"):
        flat(c, glass, {"north": 7.0, "south": 4.0, "west": 5.6, "east": 5.0, "top": 6.0, "bottom": 5.4},
             lambda f, i, j: 1.6 if (i + j) % 4 == 0 else 0)
    # the spine: black glass, glossy, a hard light line down its back
    for c in of("sp"):
        flat(c, black, {"north": 3.2, "south": 1.0, "west": 2.4, "east": 2.0, "top": 2.0, "bottom": 2.0},
             lambda f, i, j: (3.4 if f.name == "north" and i == 1 else 1.4 if f.name in ("west", "east") and j % 5 == 0 else 0))
    # the sand: warm, grainy, the heaps lit on top; the pour bright and glinting in the light
    for c in of("sh", "st"):
        flat(c, sand, {"north": 5.0, "south": 2.6, "west": 3.8, "east": 3.4, "top": 3.6, "bottom": 3.6},
             lambda f, i, j: 0.9 if R._h(f.name, i, j) % 5 == 0 else -0.6 if R._h(f.name, i, j) % 5 == 1 else 0, noise=0.5)
    for c in of("so"):
        flat(c, sand, {"north": 6.0, "south": 5.0, "west": 5.6, "east": 5.4, "top": 6.0, "bottom": 6.0},
             lambda f, i, j: 0.8 if j % 2 == 0 else 0)
        for fname in SIDES:
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                if (i + j) % 2 == 0:
                    g.set(i, j, hexc("#f2dcaa", 90))
    # the skull and horns: black glass, glossy, every edge catching a hard light; a crack or two of white in it
    for c in ("skull", "brow", "snout", "tip", "jaw") + tuple(of("horn")):
        P_.paint(c, black, 3.0 if c != "brow" else 3.6, lambda f, i, j: (2.6 if (i + 2 * j) % 9 == 0 else 0), grad=1.0, rim=1.6, ao=0.8,
                 noise=0.3, nscale=4)
    for c in of("hornt", "fang", "lfang"):
        P_.paint(c, glass, 7.6, None, rim=0.6)
    for c in ("skull", "snout"):
        cracks(P_, c, ("west", "east", "top"), black.at(0), glint, n=1, seed=c, alpha=150)
    # the mouth's dark under the snout, and the sand-coloured light deep in it
    f, g = P_.face("snout", "bottom"), P_.face("snout", "bottom", glow=True)
    for i, j in f.each():
        f.set(i, j, black.at(0))
        if 2 <= i < f.w - 2 and j > 2 and (i + j) % 3 == 0:
            g.set(i, j, hexc("#e2c486", 120))
    # the eyes: two mirrors set deep under the brow, the light in them white; they blink
    with P_.eyes():
        f, g = P_.face("skull", "north"), P_.face("skull", "north", glow=True)
        for i in list(range(0, 4)) + list(range(f.w - 4, f.w)):
            for j in range(1, 4):
                f.set(i, j, black.at(0))
                if 0 < i < 3 or f.w - 3 < i < f.w - 1:
                    g.set(i, j, hexc(glint[2] if j == 1 else glint[1] if j == 2 else glint[0], 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Clips

NO_LAG = {b: 0 for b in CHAIN + ["head", "jaw", "root"]}


def _idle(r):
    """Coiled, its head held up out of the middle: the neck sways in a slow S like a cobra's, the skull keeping still
    on the end of it the way a hunting serpent's does; a breath runs out round the coil."""
    L, n = 6.0, 36
    keys = []
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
        s2 = math.sin(4 * math.pi * u + 0.7)
        nk = neck((2.2 * s, 9, 2), (3.6 * s, 20 + 0.8 * c, 4), (2.0 * s - 1.2 * s2, 29 + 0.6 * c, 1), (-0.8 * s, 34, -5),
                  (-1.6 * s, 37, -14))
        keys.append((L * u, pose(r, coil_pts=coil(wave=(0.7, 1.0, 2 * math.pi * u), bob=(0.5, 1.0, 2 * math.pi * u + 1)), neck=nk,
                                 head=(-6 * s, 22 + 3 * c), jaw=2 + 2 * max(0.0, s2))))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """It moves coiled: a ripple runs round the coil from the neck to the tail as the spring slides along, the coil
    drawn out a little behind it, its neck leaning into the way it goes and swaying."""
    for name, L, amp, lean, st in (("walk", 1.2, 1.6, 4.0, 1.1), ("run", 0.75, 2.6, 9.0, 1.25)):
        keys, n = [], 24
        for k in range(n + 1):
            u = k / n
            ph = 2 * math.pi * u
            s = math.sin(ph)
            nk = neck((1.5 * s, 9, 1 - lean * 0.2), (3 * s, 19, 2 - lean * 0.6), (2 * s, 27, -1 - lean), (0, 31, -7 - lean * 1.3),
                      (-1.5 * s, 33 - lean * 0.4, -16 - lean * 1.4))
            cp = coil(stretch=st, shift=(0, 2.5 * (st - 1) * 16), wave=(amp, 2.0, -2 * ph), bob=(amp * 0.6, 2.0, -2 * ph + 1.2))
            keys.append((L * u, pose(r, coil_pts=cp, neck=nk, head=(-5 * s, 18 + lean * 0.8), jaw=2)))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 2.0, 3.0
    r.run_speed = (0.14, 0.3)


def _sleep(r):
    """Waiting: coiled tight, its neck laid down over its own coil and the skull resting on it."""
    nk = neck((5, 2, -6), (11, 4, -14), (14, 5, -24), (12, 4, -34), (8, 3, -44))
    return pose(r, coil_pts=coil(r_in=9, pitch=13), neck=nk, head=(-30, 8))


def _reactions(r):
    """Struck, the neck recoils away from the blow and the coil flinches tighter; from a side, the skull is knocked
    the other way. Its stance broken, it slumps, its neck laid over its coil (the waiting pose) until it lifts again."""
    hit = pose(r, coil_pts=coil(r_in=9.5, pitch=13.5), neck=neck((0, 9, 4), (0, 19, 9), (0, 28, 9), (0, 33, 4), (0, 36, -4)), head=(0, 4), jaw=24)
    back = pose(r, neck=neck((0, 9, 1), (0, 20, 3), (0, 29, 0), (0, 34, -6), (0, 37, -15)), head=(0, 26), jaw=6)
    keyed(r, "hurt", 0.6, [(0, pose(r)), (0.1, hit), (0.34, back), (0.6, pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it recoils to its right
        h = pose(r, coil_pts=coil(r_in=9.5, pitch=13.5, spin=-6 * sgn), neck=neck((-2 * sgn, 9, 2), (-6 * sgn, 19, 4), (-10 * sgn, 27, 1),
                                                                                   (-12 * sgn, 31, -5), (-13 * sgn, 33, -13)),
                 head=(-28 * sgn, 12), jaw=20, roll=-12 * sgn)
        b = pose(r, neck=neck((sgn, 9, 2), (2 * sgn, 20, 4), (2 * sgn, 29, 1), (sgn, 34, -5), (0, 37, -14)), head=(8 * sgn, 24), jaw=4)
        keyed(r, nm, 0.65, [(0, pose(r)), (0.1, h), (0.36, b), (0.65, pose(r))])
    sl = _sleep(r)
    mid = pose(r, coil_pts=coil(r_in=9.5, pitch=13.5), neck=neck((3, 6, -3), (7, 13, -8), (8, 17, -16), (6, 18, -25), (3, 17, -34)), head=(-15, 14))
    keyed(r, "kneel", 1.0, [(0, pose(r)), (0.45, mid), (1.0, sl)])
    up = pose(r, neck=neck((1, 9, 3), (1, 20, 6), (0, 30, 4), (0, 36, -2), (0, 40, -11)), head=(4, 12), jaw=10)
    keyed(r, "rise", 1.2, [(0, sl), (0.4, mid), (0.85, up), (1.2, pose(r))])
    _death(r)


def _death(r):
    """It rears up as high as it can, the skull thrown back, shudders, and comes down: the neck crashes across its own
    coil and out onto the sand, the skull bouncing once, the jaw falling open; the coil slackens out flat."""
    tall = pose(r, coil_pts=coil(r_in=8.5, pitch=12.5, rise=3), neck=neck((0, 11, 2), (0, 23, 3), (0, 34, 2), (0, 43, 0), (0, 51, -3)), head=(0, -30), jaw=30)
    shk = lambda a: pose(r, coil_pts=coil(r_in=8.5, pitch=12.5, rise=3), neck=neck((a, 11, 2), (-a, 23, 3), (a, 34, 2), (-a, 43, 0), (a, 51, -3)),
                         head=(6 * a, -26), jaw=34, roll=4 * a)
    fall = pose(r, coil_pts=coil(r_in=10, pitch=14.5), neck=neck((0, 7, -6), (0, 12, -15), (0, 12, -25), (0, 10, -34), (0, 9, -43)), head=(0, 12), jaw=16)
    flat = coil(r_in=11, pitch=16, turns=1.15)
    down = pose(r, root=(0, 9, 0), coil_pts=flat, neck=neck((0, 0, -7), (0, -1, -17), (0, -1, -27), (0, 0, -37), (0, 1, -47), root=(0, 9, 0)),
                head=(8, -4), jaw=18, roll=20)
    bounce = pose(r, root=(0, 9, 0), coil_pts=flat, neck=neck((0, 0, -7), (0, 0, -17), (0, 2, -27), (0, 4, -37), (0, 7, -46), root=(0, 9, 0)),
                  head=(8, -8), jaw=14, roll=16)
    keys = [(0, pose(r)), (0.45, tall), (0.65, shk(1.6)), (0.8, shk(-1.6)), (0.95, shk(1.0)), (1.45, fall), (1.7, down), (1.85, bounce),
            (2.05, down), (3.0, down)]
    keyed(r, "death", 3.0, keys)


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _head_hit(rad=4.0):
    return [("head", (0, -5, -27), rad), ("jaw", (0, 1, -19), 3.0)]


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def bite(r, mid="bite", side=0):
    """It draws its head back into a deep S, the jaw opening, then strikes: the neck straightens out and the skull
    snaps shut on you two blocks and a half before it (LIGHT). The second comes in from its right, swinging across."""
    if side == 0:
        gather = pose(r, neck=neck((0, 9, 3), (0, 19, 6), (0, 28, 5), (0, 33, 0), (0, 36, -8)), head=(0, 18), jaw=8)
        draw = pose(r, neck=neck((0, 9, 5), (0, 18, 12), (0, 27, 16), (0, 35, 14), (0, 41, 8)), head=(0, 0), jaw=32, horns=-9)
        strike = pose(r, neck=neck((0, 8, -4), (0, 14, -13), (0, 18, -22), (0, 20, -31), (0, 19, -40)), head=(0, 28), jaw=42, horns=6)
        snap = pose(r, neck=neck((0, 8, -5), (0, 13, -14), (0, 16, -23), (0, 17, -32), (0, 15, -41)), head=(0, 36), jaw=0, horns=4)
        back = pose(r, neck=neck((0, 9, 0), (0, 19, -2), (0, 26, -7), (0, 30, -14), (0, 32, -22)), head=(0, 26), jaw=6)
    else:
        gather = pose(r, coil_pts=coil(spin=4), neck=neck((-2, 9, 2), (-5, 19, 3), (-8, 27, 0), (-10, 32, -5), (-12, 34, -12)), head=(-25, 16), jaw=10)
        draw = pose(r, coil_pts=coil(spin=8), neck=neck((-3, 9, 3), (-9, 18, 4), (-15, 25, 2), (-19, 29, -3), (-22, 31, -10)), head=(-45, 12),
                    jaw=30, roll=-10)
        strike = pose(r, coil_pts=coil(spin=-6), neck=neck((0, 8, -4), (3, 14, -13), (8, 18, -21), (12, 19, -29), (14, 18, -37)), head=(28, 26),
                      jaw=38, roll=12)
        snap = pose(r, coil_pts=coil(spin=-8), neck=neck((1, 8, -4), (5, 13, -13), (10, 16, -21), (15, 16, -29), (18, 15, -36)), head=(36, 32),
                    jaw=0, roll=14)
        back = pose(r, neck=neck((1, 9, 0), (3, 19, -2), (4, 26, -7), (4, 30, -14), (3, 32, -22)), head=(10, 24), jaw=6)
    return Move(mid, "LIGHT", 10, 4, 14, track=8.0, rng=(0, 3.0), kinds=("combo",), damage=1.0, knock=0.6, hit=_head_hit(), plant=(),
                stages={"windup": [(0.3, gather), (1.0, draw)], "active": [(0.45, strike), (1.0, snap)], "recover": [(0.5, back)]},
                lag=_lag())


def lunge(r):
    """Glass Lunge (HEAVY): the coil winds tight and its head draws back high, a spring pressed down; it holds; then
    the spring lets go: the head shoots out low along the floor and the whole body pours out straight behind it, a
    spear of glass, and slides on; then it winds itself up again."""
    tight = coil(r_in=7, pitch=11, turns=1.45)
    gather = pose(r, coil_pts=coil(r_in=9, pitch=13), neck=neck((0, 9, 3), (0, 19, 6), (0, 28, 5), (0, 34, 1), (0, 37, -7)), head=(0, 16), jaw=6)
    rear = pose(r, coil_pts=tight, neck=neck((0, 10, 8), (0, 19, 18), (0, 30, 25), (0, 41, 26), (0, 50, 21)), head=(0, -32), jaw=38, horns=-20)
    spear = lambda s, a: pose(r, root=(0, 8, 0), coil_pts=[(a * math.sin(0.5 * k + s), _half(k / 15) + 0.3, 6 + 12 * k) for k in range(16)],
                              neck=neck((0, 0, -10), (a * 0.4 * math.sin(s - 0.5), 1, -20), (0, 3, -30), (0, 6, -40), (0, 9, -50),
                                        root=(0, 8, 0)), head=(0, -6), jaw=26, horns=8)
    half = pose(r, root=(0, 10, 0), coil_pts=coil(r_in=12, pitch=16, turns=0.6, tail=(1.0, [(-10, 7, 40), (-6, 7, 60), (-2, 7, 80)])),
                neck=neck((0, 3, -8), (0, 8, -17), (0, 12, -26), (0, 14, -35), (0, 14, -45), root=(0, 10, 0)), head=(0, 18), jaw=26)
    # it gathers itself in: the straight body bends into an S, the S into a hook round its left, the hook into the coil
    ess = pose(r, root=(0, 9, 0), coil_pts=[(17 * math.sin(0.5 * k), _half(k / 15) + 0.3, 5 + 8.5 * k) for k in range(16)],
               neck=neck((0, 2, -9), (0, 6, -18), (0, 10, -27), (0, 13, -36), (0, 15, -45), root=(0, 9, 0)), head=(0, 12), jaw=18)
    hook = pose(r, root=(0, 10, 0), coil_pts=[(p[0], _half(k / 9) + 0.3, p[1]) for k, p in enumerate(
                    ((0, 8), (-4, 20), (-12, 32), (-22, 41), (-31, 40), (-36, 29), (-35, 16), (-30, 4), (-22, -5), (-12, -11)))],
                neck=neck((0, 4, -8), (0, 10, -16), (0, 15, -24), (0, 18, -32), (0, 19, -41), root=(0, 10, 0)), head=(0, 22), jaw=14)
    coming = pose(r, coil_pts=coil(r_in=11, pitch=15, turns=1.15, lift=0.4),
                  neck=neck((0, 8, -2), (0, 17, -6), (0, 24, -12), (0, 28, -19), (0, 30, -27)), head=(0, 24), jaw=8)
    return Move("lunge", "HEAVY", 16, 14, 24, title="Glass Lunge", hold=8, track=6.0, lock=11, advance=8.0, ability="Glass Lunge",
                rng=(4, 14), kinds=("gap",), damage=1.3, knock=1.4, plant=(), hit=_head_hit(5.0),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.15, half), (0.4, spear(0, 3)), (0.75, spear(2.2, 2)), (1.0, spear(4.0, 1))],
                        "recover": [(0.37, ess), (0.55, hook), (0.72, coming)]}, lag=_lag(), through=spear(5.2, 1.5))


def spray(r):
    """Shard Spray (HEAVY): its coil rises on itself into a tall spring, the skull thrown back at the top; it holds;
    then the spring slams flat on the floor, wider than it was, and shards of its glass fly off it all round."""
    low = pose(r, coil_pts=coil(r_in=9, pitch=13), neck=neck((0, 8, 0), (0, 16, -2), (0, 22, -7), (0, 25, -14), (0, 26, -22)), head=(0, 30), jaw=4)
    spring = coil(r_in=11, pitch=4, turns=1.3, rise=30, lift=2.5)
    tall = pose(r, root=(0, 42, 0), coil_pts=spring, neck=neck((0, 9, 3), (0, 19, 5), (0, 28, 4), (0, 35, 0), (0, 40, -4), root=(0, 42, 0)),
                head=(0, -34), jaw=34, horns=-8)
    slam = pose(r, coil_pts=coil(r_in=12, pitch=17, turns=1.1), neck=neck((0, 6, -3), (0, 12, -11), (0, 15, -20), (0, 16, -29), (0, 15, -38)),
                head=(0, 24), jaw=40, horns=8)
    ring = pose(r, coil_pts=coil(r_in=12, pitch=17, turns=1.1, wave=(1.2, 3.0, 0)), neck=neck((0, 7, -2), (0, 13, -10), (0, 17, -18), (0, 18, -27),
                                                                                         (0, 18, -36)), head=(0, 22), jaw=30)
    settle = pose(r, coil_pts=coil(r_in=11, pitch=15.5, turns=1.15, wave=(0.8, 3.0, 2.0)), neck=neck((0, 8, -1), (0, 15, -6), (0, 20, -13),
                                                                                                  (0, 22, -21), (0, 22, -30)), head=(0, 22), jaw=16)
    return Move("spray", "HEAVY", 16, 6, 18, title="Shard Spray", hold=8, track=0.0, ability="Shard Spray", rng=(0, 10), kinds=("area",),
                damage=1.0, knock=0.8, plant=(), stages={"windup": [(0.3, low), (1.0, tall)], "active": [(0.35, slam), (1.0, ring)],
                                                          "recover": [(0.5, settle)]}, lag=_lag(), through=settle)


def burrow(r):
    """Burrow Strike (HEAVY): it rears, then dives head first into the sand, its coil pouring down after it until it's
    gone; under the floor it comes round behind you, and bursts up out of the sand at you, the skull first and its jaw
    wide, then arcs down on you; its coil comes up out of the floor after it."""
    rear = pose(r, coil_pts=coil(r_in=9, pitch=13), neck=neck((0, 10, 4), (0, 21, 8), (0, 31, 8), (0, 39, 4), (0, 45, -2)), head=(0, -6), jaw=14)
    dive = pose(r, root=(0, 4, -2), coil_pts=coil(r_in=8, pitch=11, lift=-10, rise=6),
                neck=neck((0, 8, -6), (0, 10, -15), (0, 6, -23), (0, -2, -28), (0, -11, -29), root=(0, 4, -2)), head=(0, 80), jaw=24)
    gone = pose(r, root=(0, -36, 0), coil_pts=coil(r_in=8, pitch=11, lift=-49, rise=8),
                neck=neck((0, -4, -8), (0, -12, -15), (0, -21, -19), (0, -30, -20), (0, -39, -18), root=(0, -36, 0)), head=(0, 80), jaw=20)
    burst = pose(r, root=(0, -30, -6), coil_pts=coil(r_in=8, pitch=11, lift=-43, rise=8),
                 neck=neck((0, 10, -2), (0, 20, -3), (0, 30, -4), (0, 40, -6), (0, 49, -9), root=(0, -30, -6)), head=(0, -40), jaw=42)
    over = pose(r, root=(0, -18, -8), coil_pts=coil(r_in=9, pitch=12, lift=-30, rise=6),
                neck=neck((0, 10, -4), (0, 20, -8), (0, 29, -14), (0, 33, -23), (0, 32, -32), root=(0, -18, -8)), head=(0, 46), jaw=0)
    out = pose(r, root=(0, 6, -4), coil_pts=coil(r_in=10, pitch=14, lift=-6),
               neck=neck((0, 8, -3), (0, 16, -8), (0, 22, -15), (0, 25, -23), (0, 25, -32), root=(0, 6, -4)), head=(0, 30), jaw=6)
    return Move("burrow", "HEAVY", 18, 8, 20, title="Burrow Strike", hold=6, track=4.0, ability="Burrow Strike", rng=(3, 14), kinds=("gap",),
                damage=1.3, knock=1.2, plant=(), hit=_head_hit(4.5),
                stages={"windup": [(0.3, rear), (0.65, dive), (1.0, gone)], "active": [(0.2, gone), (0.55, burst), (1.0, over)], "recover": [(0.5, out)]},
                lag=_lag(), through=over)


def erupt(r):
    """Erupting Glass: it rears up tall and looks straight down at where you stand, the coil clenching, then drives its
    jaw down into the sand before it; the floor erupts in glass where you stand; it wrenches its head back out."""
    rear = pose(r, coil_pts=coil(r_in=8, pitch=12, turns=1.35), neck=neck((0, 11, 3), (0, 22, 5), (0, 33, 4), (0, 43, 0), (0, 51, -5)),
                head=(0, 52), jaw=34, horns=10)
    gather = pose(r, neck=neck((0, 9, 2), (0, 20, 4), (0, 29, 2), (0, 35, -3), (0, 38, -11)), head=(0, 16), jaw=16)
    drive = pose(r, coil_pts=coil(r_in=8.5, pitch=12.5, wave=(1.0, 4.0, 0)), neck=neck((0, 9, -5), (0, 15, -13), (0, 15, -22), (0, 10, -29), (0, 3, -33)),
                 head=(0, 70), jaw=36)
    held = pose(r, coil_pts=coil(r_in=8.5, pitch=12.5, wave=(1.0, 4.0, 1.5)), neck=neck((0, 9, -5), (0, 15, -13), (0, 14, -22), (0, 9, -29), (0, 2, -33)),
                head=(0, 72), jaw=30, roll=4)
    wrench = pose(r, neck=neck((0, 10, 1), (0, 21, 2), (0, 30, 0), (0, 36, -5), (0, 39, -13)), head=(0, 6), jaw=24)
    return Move("erupt", "HEAVY", 18, 8, 18, title="Erupting Glass", track=4.0, ability="Erupting Glass", rng=(4, 18), kinds=("ranged", "area"),
                damage=1.0, knock=0.6, plant=(), stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.3, drive), (1.0, held)],
                                                          "recover": [(0.5, wrench)]}, lag=_lag(), through=held)


def lash(r):
    """For whoever is behind it (LIGHT): the skull turns to look back over the coil, the tail's end rises off the
    floor behind it and whips across, the coil swinging the other way."""
    def tl(a, h, sw):
        """The coil with its outer end lifted (h) and swung (sw) across behind it."""
        return coil(spin=a, tail=(0.62, [(-18 + sw * 0.3, 8 + h * 0.4, 22), (-8 + sw * 0.7, 8 + h * 0.8, 30), (sw, 8 + h, 36), (sw * 1.3, 8 + h, 46)]))
    look = lambda yaw: neck((2, 9, 2), (5, 19, 4), (7, 27, 2), (8, 31, -2), (9, 33, -7))
    gather = pose(r, coil_pts=tl(-4, 6, -10), neck=look(0), head=(70, 18), jaw=6)
    up = pose(r, coil_pts=tl(-8, 32, -24), neck=look(0), head=(110, 14), jaw=12)
    whip = pose(r, coil_pts=tl(6, 12, 20), neck=look(0), head=(120, 20), jaw=14)
    out = pose(r, coil_pts=tl(10, 6, 30), neck=look(0), head=(110, 22), jaw=10)
    on = pose(r, coil_pts=tl(11, 4, 34), neck=look(0), head=(100, 22), jaw=8)
    back = pose(r, coil_pts=tl(4, 3, 8), neck=look(0), head=(50, 22), jaw=4)
    return Move("lash", "LIGHT", 10, 4, 14, track=0.0, rng=(0, 3.5), kinds=("behind",), damage=0.9, knock=1.2,
                hit=[("b13", (0, 3, 0), 3.0), ("b11", (0, 4, 0), 3.5)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, up)], "active": [(0.45, whip), (1.0, out)], "recover": [(0.5, back)]}, lag=_lag(), through=on)


def storm(r):
    """The Glass Storm (PERILOUS, the signature): it rises on its coil and begins to turn on itself, faster and faster,
    the coil spinning into a spout of glass with the skull at its top; at its fastest it bursts, and glass flies out of
    it all round; it drops back into its coil, turning slower until it stops."""
    def spout(sp, lift, rise, head_up):
        return pose(r, root=(0, 13 + lift, 0), coil_pts=coil(r_in=10 + rise * 0.05, pitch=6, turns=1.3, rise=rise),
                    neck=neck((0, 9, 2), (0, 19, 4), (0, 28, 3), (0, 35, 0), (0, 40, -6), root=(0, 13 + lift, 0)), head=(0, head_up), jaw=30, horns=-6)
    wind = [(0.2, spout(-20, 4, 6, 10)), (0.45, spout(60, 14, 16, 0)), (0.7, spout(240, 24, 24, -10)), (0.88, spout(450, 30, 28, -20)),
            (1.0, spout(620, 32, 30, -26))]
    burst = pose(r, coil_pts=coil(r_in=12, pitch=17, turns=1.1), neck=neck((0, 6, -3), (0, 12, -11), (0, 15, -20), (0, 16, -29), (0, 15, -38)),
                 head=(0, 24), jaw=44, horns=8)
    ring = pose(r, coil_pts=coil(r_in=12, pitch=17, turns=1.1, wave=(1.2, 3.0, 0)),
                neck=neck((0, 7, -2), (0, 13, -10), (0, 17, -18), (0, 18, -27), (0, 18, -36)), head=(0, 22), jaw=34)
    slow = pose(r, coil_pts=coil(r_in=11, pitch=15.5, turns=1.15),
                neck=neck((0, 8, -1), (0, 15, -6), (0, 20, -13), (0, 22, -21), (0, 22, -30)), head=(0, 22), jaw=14)
    stop = pose(r, neck=neck((0, 9, 1), (0, 20, 3), (0, 29, 0), (0, 34, -6), (0, 37, -15)), head=(0, 24), jaw=6)
    return Move("storm", "PERILOUS", 32, 10, 24, title="Glass Storm", track=0.0, ability="Glass Storm", rng=(0, 16), kinds=("area",), signature=True,
                damage=1.0, knock=1.0, plant=(), stages={"windup": wind, "active": [(0.3, burst), (1.0, ring)], "recover": [(0.4, slow), (0.7, stop)]},
                lag=_lag(), through=pose(r, coil_pts=coil(r_in=11.5, pitch=16.5, turns=1.12), neck=neck((0, 7, -2), (0, 14, -8), (0, 18, -15),
                                                                                                                   (0, 20, -24), (0, 20, -33)),
                                         head=(0, 22), jaw=24))


def wake(r):
    """Its intro, once its head is up: it rears tall over its coil, looks down at you, opens its jaw and hisses, then
    settles into its guard."""
    tall = pose(r, coil_pts=coil(r_in=9.5, pitch=13.5), neck=neck((0, 10, 3), (0, 21, 6), (0, 31, 6), (0, 40, 3), (0, 47, -2)), head=(0, 34), jaw=10)
    hiss = pose(r, coil_pts=coil(r_in=9.5, pitch=13.5), neck=neck((0, 10, 3), (0, 21, 7), (0, 31, 8), (0, 39, 5), (0, 46, 0)), head=(0, 26), jaw=44, horns=-6)
    hold = pose(r, neck=neck((0, 10, 2), (0, 21, 5), (0, 30, 4), (0, 37, 0), (0, 42, -7)), head=(-6, 28), jaw=30)
    return Move("wake", "NONE", 10, 24, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, pose(r, head=(4, 26), jaw=4)), (1.0, tall)], "active": [(0.3, hiss), (0.75, hold), (1.0, pose(r, jaw=8))],
                        "recover": [(0.5, pose(r, jaw=3))]}, lag=_lag())


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [bite(r), bite(r, "bite_b", 1), lunge(r), spray(r), burrow(r), erupt(r), lash(r), storm(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
    _whirl(r, mv[7], 3)
    for name in r.clips:
        if name not in DIVES:
            _floored(r, name)
    return mv


def _whirl(r, mv, turns):
    """The Glass Storm's whirl, on the root after staging: from still at the wind-up's start, faster and faster to its
    fastest as it bursts, then slower until it stops, whole turns in all (a whole turn is no turn: it hands back as it
    was)."""
    c = r.clips[core.clip_name(mv)]
    W, A, Rr = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0, tp, t1 = 0.1 * W, W + 0.3 * A, W + A + 0.75 * Rr
    n = int(round(c.length / core.TICK))
    ts = [min(c.length, k * core.TICK) for k in range(n + 1)]
    speed = [0.0 if t <= t0 or t >= t1 else ((t - t0) / (tp - t0)) ** 1.6 if t < tp else core.ease_io(1 - (t - tp) / (t1 - tp)) for t in ts]
    tot, acc = sum(speed) or 1.0, 0.0
    old = c.tracks.get(("root", "rot"))
    keys = []
    for t, v in zip(ts, speed):
        acc += v
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        base[1] += 360.0 * turns * acc / tot
        keys.append((t, tuple(base), "cat"))
    c.tracks[("root", "rot")] = keys


# Its sounds (SPEC §35.8): glass and sand, chosen by ear on paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("block.amethyst_cluster.hit", 1.0, 1.4), ("item.brush.brushing.sand", 0.8, 1.2)],
    "tell_heavy": [("block.amethyst_block.resonate", 1.6, 0.6), ("block.suspicious_sand.break", 1.2, 0.6)],
    "tell_perilous": [("block.amethyst_block.resonate", 2.4, 0.5), ("block.beacon.activate", 2.0, 0.6), ("item.brush.brushing.sand", 1.6, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.2, 1.2), ("block.glass.hit", 1.2, 0.8)],
    "swing_heavy": [("entity.breeze.wind_burst", 1.4, 0.7), ("block.sand.break", 1.4, 0.6)],
    "cast": [("block.amethyst_block.chime", 1.6, 0.8), ("block.glass.break", 1.0, 1.4)],
    "impact": [("block.glass.break", 2.2, 0.6), ("item.mace.smash_ground_heavy", 1.6, 0.8), ("block.sand.fall", 1.4, 0.6)],
    "roar": [("entity.ender_dragon.growl", 1.4, 1.6), ("block.amethyst_block.resonate", 1.8, 0.5)],
    "step": [("block.sand.step", 1.0, 0.8), ("block.glass.step", 0.8, 1.2)],
}
MOVE_SOUNDS = {"wake": {"swing": "roar"}, "erupt": {"swing": "cast"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["bite_b"]]},
            {"move": "lunge", "weight": 3, "when": "far"},
            {"move": "spray", "weight": 3},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        {"transition": "burrow", "pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["bite_b"], ["bite_b", "spray"]]},
            {"move": "burrow", "weight": 3, "when": "far", "then": [["bite"]]},
            {"move": "erupt", "weight": 3, "when": "far"},
            {"move": "lunge", "weight": 2, "when": "far"},
            {"move": "spray", "weight": 2},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        {"transition": "storm", "pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["bite_b", "spray"]]},
            {"move": "storm", "weight": 2},
            {"move": "lunge", "weight": 3, "when": "far", "then": [["bite"]]},
            {"move": "erupt", "weight": 3, "when": "far"},
            {"move": "burrow", "weight": 2, "when": "far", "then": [["bite"]]},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.0, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
