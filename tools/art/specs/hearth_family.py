"""The Hearth's family (plan docs/family/PLAN.md): Aya the cat and Dubu the dog, drawn from the owner's photos.

  family_aya   a fluffy black-and-white cat: a black cap over the ears and eyes, a white blaze up the nose with a black
               wedge beside it, a black cheek on her left, green eyes, a pink nose, a white chest, belly and paws, a
               black saddle with a white streak through it, black haunches and a thick black tail
  family_dubu  a small, long, low white dog: fawn drop ears with feathering, a fawn patch over each eye, a white blaze,
               a dark nose, a white chest ruff, short legs, and a plumed tail carried up over his back, fawn at its root

    python3 tools/art/build_all.py hearth_family     textures (textures/entity/family/<name>.png, _blink) and
                                                     src/main/java/dev/awaken/kin/client/FamilyData.java
    python3 tools/art/specs/hearth_family.py preview DIR

Same conventions as kin.py (hero rigs, model pixels, y down, ground at y = 24, facing -z, clip pos y up). The coat is
painted in the body's own space: every texel asks coat(x, y, z) what it is, so a patch runs unbroken over edges and from
one box to the next, the way a real coat does.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, lerpc, mul  # noqa: E402
from fauna_anim import cycle, s, c  # noqa: E402
from wild_fauna import sym, stubs, clip_of, hold  # noqa: E402

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/kin/client/FamilyData.java")

LIGHT = 1.22  # the previews' sun: the game lights an entity a little brighter than fauna_rig's default
SCALE = {"family_aya": 0.45, "family_dubu": 0.5}
TEMPER = {"family_aya": "cute", "family_dubu": "cute"}


def keys(*pts):
    return [tuple(p) for p in pts]


# =====================================================================================================================
# Where each texel sits on the body (rest pose), from the same geometry the game and the previews draw
# =====================================================================================================================

FACE_ORDER = ("top", "bottom", "west", "north", "east", "south")


def texels(rig):
    """Every texel a cube owns: (tu, tv) -> (cube name, face name, i, j, (x, y, z) rest-pose centre, outward normal)."""
    out = {}
    quads = R.quads(rig.tree())
    # quads() walks bones and their boxes in order and emits six faces a box (planes skip the faces with no area)
    k = 0
    for b in rig.bones_in_tree_order():
        for cube in b.cubes:
            u, v, w, h, d = cube.u, cube.v, cube.w, cube.h, cube.d
            rects = ((u + d, v, u + d + w, v + d), (u + d + w, v + d, u + d + w + w, v), (u, v + d, u + d, v + d + h),
                     (u + d, v + d, u + d + w, v + d + h), (u + d + w, v + d, u + d + w + d, v + d + h),
                     (u + d + w + d, v + d, u + d + w + d + w, v + d + h))
            for fname, rect in zip(FACE_ORDER, rects):
                if rect[0] == rect[2] or rect[1] == rect[3]:
                    continue
                vs, qrect, n, _plane = quads[k]
                k += 1
                assert tuple(qrect) == tuple(rect), (cube.name, fname, qrect, rect)
                if cube.share is not None:
                    continue
                ua, va, ub, vb = rect
                nl = math.sqrt(sum(q * q for q in n)) or 1.0
                nn = tuple(q / nl for q in n)
                for j in range(int(abs(vb - va))):
                    for i in range(int(abs(ub - ua))):
                        tu, tv = min(ua, ub) + i, min(va, vb) + j
                        sx = (tu + 0.5 - ua) / (ub - ua)
                        ty = (tv + 0.5 - va) / (vb - va)
                        p = tuple((1 - sx) * (1 - ty) * vs[1][q] + sx * (1 - ty) * vs[0][q] + (1 - sx) * ty * vs[2][q]
                                  + sx * ty * vs[3][q] for q in range(3))
                        out[(tu, tv)] = (cube.name, fname, i, j, p, nn)
    assert k == len(quads)
    return out


def _bones_in_tree_order(self):
    order = []

    def walk(b):
        order.append(b)
        for ch in b.children:
            walk(ch)
    for b in self.roots():
        walk(b)
    return order


Rig.bones_in_tree_order = _bones_in_tree_order


def _hash(*a):
    return R._h(*a)


def fur(ramp, base, p, n, part, lock=4, depth=0.35, top=0.4, under=1.0, seed=0, fall=0.12):
    """A fur colour at a point: lit from above, darker underneath and lower down, with a few long soft locks (never
    per-pixel noise: a lock is two or three texels long, staggered from its neighbours)."""
    x, y, z = p
    v = base + top * max(0.0, -n[1]) - under * max(0.0, n[1])
    if abs(n[1]) < 0.5:
        v -= fall * (y - 12.0) * 0.5          # sides darken a little toward the ground
        v += 0.2 * (-n[2])                    # a touch lighter on what faces forward
        a, b = (int(math.floor(z + 100)), int(math.floor(y + 100))) if abs(n[0]) > 0.5 else (int(math.floor(x + 100)), int(math.floor(y + 100)))
    else:
        a, b = int(math.floor(x + 100)), int(math.floor(z + 100))
    hv = _hash(part, a // lock, seed)
    if a % lock == hv % lock:
        row = (b + (hv >> 4) % 7) % 7
        if row < 3:
            v -= depth
        elif row == 3:
            v += depth * 0.6
    return ramp.at(v)


# =====================================================================================================================
# Drawing a posed body the way the game lights an entity
# =====================================================================================================================

_L0 = (0.2, 1.0, -0.7)
_L1 = (-0.2, 1.0, 0.7)


def game_light(n):
    """The brightness the game gives a face: its two entity lights over a 0.4 ambient (top 1.0, front and back about
    0.74, sides about 0.5, underside 0.4). n is the face's outward normal in model space (y down)."""
    w = (n[0], -n[1], n[2])
    ln = math.sqrt(sum(q * q for q in w)) or 1.0
    tot = 0.0
    for L in (_L0, _L1):
        ll = math.sqrt(sum(q * q for q in L))
        tot += max(0.0, sum(w[k] / ln * L[k] / ll for k in range(3)))
    return min(1.0, 0.4 + 0.6 * tot)


def draw(parts, yaw=40.0, pitch=14.0, px=10.0, bounds=None, origin=None, sun=1.0, soft=0.5):
    """Draws [(tree, texture image)] as one picture, far texels first. `soft` lifts the game's darkest faces a little,
    as the sky's light does outdoors. bounds/origin as fauna_rig.render."""
    from PIL import Image, ImageDraw
    ya, pa = math.radians(yaw), math.radians(pitch)
    cam = R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0))
    polys = []
    for tree, tex in parts:
        tw, th = tex.size
        texpx = tex.load()
        for vs, (ua, va, ub, vb), n, plane in R.quads(tree):
            cv = [R._mv(cam, q) for q in vs]
            nv = R._mv(cam, n)
            if not plane and nv[2] > 1e-6:
                continue
            lit = game_light(n)
            lit = (lit + soft * (1.0 - lit)) * sun
            iu, iv = int(round(abs(ub - ua))), int(round(abs(vb - va)))
            for j in range(iv):
                for i in range(iu):
                    tu, tv = min(ua, ub) + i, min(va, vb) + j
                    if not (0 <= tu < tw and 0 <= tv < th):
                        continue
                    col = texpx[int(tu), int(tv)]
                    if col[3] < 16:
                        continue
                    corners = []
                    for (a, b) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                        sx = (tu + a - ua) / (ub - ua)
                        ty = (tv + b - va) / (vb - va)
                        corners.append(tuple((1 - sx) * (1 - ty) * cv[1][k] + sx * (1 - ty) * cv[0][k] + (1 - sx) * ty * cv[2][k]
                                             + sx * ty * cv[3][k] for k in range(3)))
                    polys.append((sum(q[2] for q in corners) / 4, corners, mul(col, lit)))
    if not polys:
        return Image.new("RGBA", (8, 8))
    if bounds is None:
        xs = [q[0] for p_ in polys for q in p_[1]]
        ys = [q[1] for p_ in polys for q in p_[1]]
        bounds = (min(xs), max(xs), min(ys), max(ys))
    minx, maxx, miny, maxy = bounds
    if origin is not None:
        W, H, offx, offy = origin
    else:
        W, H = int((maxx - minx) * px) + 16, int((maxy - miny) * px) + 16
        offx, offy = 8 - minx * px, 8 - miny * px
    img = Image.new("RGBA", (int(W), int(H)), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    for depth, corners, col in sorted(polys, key=lambda p_: -p_[0]):
        dr.polygon([(q[0] * px + offx, q[1] * px + offy) for q in corners], fill=col[:3] + (255,))
    return img


def bounds_of(trees, yaw, pitch):
    return R.bounds_of(trees, yaw, pitch)


# =====================================================================================================================
# Gaits and poses shared by both
# =====================================================================================================================

def step_leg(cl, bone, swing, ph, lift, stance=0.62, n=12, base=(0, 0, 0), reach=0.0):
    """One leg of a walk: on the ground it sweeps back at a steady rate (so the foot stays where it landed), in the
    air it comes forward eased, lifting. `reach` leans the whole sweep forward (+) or back (-)."""
    def at(p):
        q = (p + ph) % 1.0
        if q < stance:
            return -swing + 2 * swing * (q / stance), 0.0
        u = (q - stance) / (1 - stance)
        e = 0.5 - 0.5 * math.cos(math.pi * u)
        return swing - 2 * swing * e, lift * math.sin(math.pi * u)
    cycle(cl, bone, "rot", lambda p: (base[0] - reach + at(p)[0], base[1], base[2]), n)
    cycle(cl, bone, "pos", lambda p: (0, at(p)[1], 0), n)


def breathe(cl, bones=("chest",), amount=0.03, per=1.0, n=8):
    for b in bones:
        cycle(cl, b, "scale", lambda p: (1 + amount * s(p * per), 1 + amount * s(p * per), 1), n)


def tuck(cl, legs, k=0.12):
    """Legs folded away under a body that is lying down."""
    for nm in legs:
        hold(cl, nm, scale=(1, k, 1))


REST_POSE = {"rot": (0.0, 0.0, 0.0), "pos": (0.0, 0.0, 0.0), "scale": (1.0, 1.0, 1.0)}


def _smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def _passing(add, u):
    """A passing offset (0 at both ends of the clip) at fraction u, eased between its points."""
    pts = [(0.0, (0.0, 0.0, 0.0))] + list(add) + [(1.0, (0.0, 0.0, 0.0))]
    for (u0, v0), (u1, v1) in zip(pts, pts[1:]):
        if u0 <= u <= u1:
            f = _smooth((u - u0) / max(1e-6, u1 - u0))
            return tuple(v0[i] + (v1[i] - v0[i]) * f for i in range(3))
    return (0.0, 0.0, 0.0)


def flipped(windows):
    """The same order of parts run backwards: what went down last gets up first."""
    return {k: (1 - b, 1 - a) for k, (a, b) in windows.items()}


def bridge(r, name, pose, dur, windows=None, up=False, extra=None, rest=None, settle=0.05, n=12):
    """A real clip between standing and the first frame of `pose` (down into it, or with `up` back out of it), so a
    change of pose is acted and not a crossfade. Every part moves in its own window of the clip (fractions of its length;
    `windows` maps a bone, a (bone, channel) or "*" to one), eased, so the body goes in an order: the legs and rump
    first, the head after, the tail last. `extra` adds passing offsets on top (the front end going down first, a dip of
    the head). Going down, each part runs a little past its place and settles back, which is the weight landing. It
    starts on the standing pose and ends exactly on the pose clip's first frame (or the other way round)."""
    src = r.clips[pose]
    cl = clip_of(r, name, dur)
    windows, extra, rest = windows or {}, extra or {}, rest or {}
    for bone, ch in dict.fromkeys(list(src.tracks) + list(extra)):
        track = src.tracks.get((bone, ch))
        base = rest.get((bone, ch), REST_POSE[ch])
        held = track[0][1] if track else base
        add = extra.get((bone, ch), ())
        if not add and all(abs(held[i] - base[i]) < 1e-6 for i in range(3)):
            continue
        a, b = windows.get((bone, ch), windows.get(bone, windows.get("*", (0.0, 1.0))))
        ks = []
        for k in range(n + 1):
            u = k / n
            w = (u - a) / max(1e-6, b - a)
            if up:
                f = 1.0 - _smooth(w)
            elif w < 0.8:
                f = (1 + settle) * _smooth(w / 0.8)
            else:
                f = 1 + settle * (1 - _smooth((w - 0.8) / 0.2))
            off = _passing(add, u)
            ks.append((dur * u,) + tuple(base[i] + (held[i] - base[i]) * f + off[i] for i in range(3)))
        getattr(cl, ch)(bone, ks, "lin")
    return cl


# =====================================================================================================================
# Aya
# =====================================================================================================================

def aya():
    r = Rig("family_aya", (128, 64))
    # a stride is the ground one cycle covers with planted feet (the renderer plays the gait by it, SPEC §69.9)
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.24, 0.8, (0.12, 0.26), 0.32
    body = r.bone("body", (0, 14, 1))
    body.cube("rump", -4.5, -4, 0, 9, 8, 8)
    chest = body.bone("chest", (0, 0, 0))
    # the chest laps a pixel into the rump, a hair smaller, so no crack opens when her back bends and no faces share a plane
    chest.cube("ochest", -4.5, -4, -8, 9, 8, 9, inflate=-0.02)
    chest.cube("bib", -3.5, -1, -9.5, 7, 6, 2)                       # the white chest ruff
    head = chest.bone("head", (0, -3, -8))
    head.cube("ohead", -5.5, -6, -6, 11, 8, 7)
    head.cube("muzzle", -2.5, -2, -7.5, 5, 3, 2)
    head.cube("cheek_l", 5.5, -2, -5, 1, 4, 4)
    head.cube("cheek_r", -6.5, -2, -5, 1, 4, 4)

    def side(sd, nm, mir, sh):
        # ears with depth, turned a little outward, so they read as ears from the side and from behind too
        ear = head.bone("ear_" + nm, (sd * 3.5, -6, -2), (-8, sd * -26, sd * 12))
        ear.cube("oear_" + nm, -2, -2, -1, 4, 2, 2, mirror=mir, share=sh("oear_l"))
        ear.cube("eartip_" + nm, -1, -3, -1, 2, 1, 2, mirror=mir, share=sh("eartip_l"))
        fl = chest.bone("fleg_" + nm, (sd * 2.5, 4, -5.5))
        fl.cube("ofleg_" + nm, -1.5, 0, -1.5, 3, 6, 3, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 2.5, 1, 5))
        # the thigh sits a quarter-pixel inside her flank and rump: it shows under the belly and when the leg folds,
        # and leaves no ledge or shared plane on her side
        hl.cube("haunch_" + nm, -2.25 if sd > 0 else -1.75, -2, -3.25, 4, 6, 6, mirror=mir, share=sh("haunch_l"))
        hl.cube("ohleg_" + nm, -1.5, 3, -1, 3, 6, 3, mirror=mir, share=sh("ohleg_l"))
    sym(side)
    tail = body.bone("tail", (0, -2, 7.5), (18, 0, 0))
    tail.cube("tail1", -1.5, -1.5, 0, 3, 3, 4)
    t2 = tail.bone("tail2", (0, 0, 4), (14, 0, 0))
    t2.cube("otail2", -2, -2, 0, 4, 4, 4)
    t3 = t2.bone("tail3", (0, 0, 4), (12, 0, 0))
    t3.cube("otail3", -2, -2, 0, 4, 4, 4)
    t4 = t3.bone("tail4", (0, 0, 4), (10, 0, 0))
    t4.cube("otail4", -1.5, -1.5, 0, 3, 3, 3)
    r.look = [("head", 0.85, 0.7), ("chest", 0.1, 0.0)]
    r.pack()
    _aya_clips(r)
    return r


AYA_TAIL = ("tail", "tail2", "tail3", "tail4")


def _aya_tail(cl, pose, sway=0.0, lag=0.11, n=8, per=1.0, axis=1):
    """The tail held in a pose (each link's extra bend, degrees) with a wave running down it."""
    for k, nm in enumerate(AYA_TAIL):
        px_, py_, pz_ = pose[k]
        if sway:
            a = sway * (1 + 0.35 * k)

            def fn(p, px_=px_, py_=py_, pz_=pz_, a=a, k=k):
                w = a * s(p * per, -k * lag)
                return (px_ + (w if axis == 0 else 0), py_ + (w if axis == 1 else 0), pz_)
            cycle(cl, nm, "rot", fn, n)
        else:
            hold(cl, nm, rot=(px_, py_, pz_))


def _aya_clips(r):
    legs = ("fleg_l", "fleg_r", "hleg_l", "hleg_r")

    # ---- standing: weight on four feet, the tail swaying slow, an ear flicking at something only she can hear
    idle = clip_of(r, "idle", 4.0, loop=True)
    cycle(idle, "chest", "scale", lambda p: (1 + 0.025 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    _aya_tail(idle, ((-6, 0, 0), (-4, 0, 0), (0, 0, 0), (6, 0, 0)), sway=9, n=8)
    idle.rot("ear_r", keys((0, 0, 0, 0), (1.2, 0, 0, 0), (1.3, 14, 0, -22), (1.42, 0, 0, 0), (1.5, 8, 0, -14), (1.62, 0, 0, 0), (4, 0, 0, 0)))
    idle.rot("ear_l", keys((0, 0, 0, 0), (2.9, 0, 0, 0), (3.0, 14, 0, 22), (3.14, 0, 0, 0), (4, 0, 0, 0)))
    idle.rot("head", keys((0, 0, 0, 0), (0.8, 0, 0, 0), (1.3, 2, -14, 4), (2.4, 2, -14, 4), (2.9, 0, 0, 0), (4, 0, 0, 0)))
    idle.rot("body", keys((0, 0, 0, 0), (2.0, 0, 0, 1.5), (4, 0, 0, 0)))

    # ---- walking: one foot at a time (left hind, left fore, right hind, right fore), the head level, the tail up
    move = clip_of(r, "move", 0.84, loop=True)
    for nm, ph in (("hleg_l", 0.0), ("fleg_l", 0.25), ("hleg_r", 0.5), ("fleg_r", 0.75)):
        step_leg(move, nm, 26, ph, 1.3, stance=0.62, reach=2 if nm[0] == "f" else -2)
    cycle(move, "body", "pos", lambda p: (0, 0.22 * c(p * 2, 0.1), 0), 12)
    cycle(move, "body", "rot", lambda p: (0, 3.5 * s(p, 0.05), 1.6 * s(p, 0.3)), 12)
    cycle(move, "chest", "rot", lambda p: (0, -6.0 * s(p, 0.05), -2.2 * s(p, 0.3)), 12)
    cycle(move, "head", "rot", lambda p: (1.5 * c(p * 2), 2.5 * s(p, 0.05), 0.6 * s(p, 0.3)), 12)
    cycle(move, "head", "pos", lambda p: (0, -0.15 * c(p * 2, 0.1), 0), 12)
    _aya_tail(move, ((30, 0, 0), (6, 0, 0), (2, 0, 0), (22, 0, 0)), sway=5, n=12)

    # ---- running: a bounding canter, the back flexing, the tail streaming level behind
    run = clip_of(r, "run", 0.42, loop=True)
    for nm, ph in (("hleg_l", 0.0), ("hleg_r", 0.08), ("fleg_l", 0.42), ("fleg_r", 0.52)):
        step_leg(run, nm, 42, ph, 2.2, stance=0.4, reach=4 if nm[0] == "f" else -4)
    cycle(run, "body", "pos", lambda p: (0, 1.1 * s(p, 0.15), 0), 12)
    cycle(run, "body", "rot", lambda p: (5.5 * s(p, -0.1), 0, 0), 12)
    cycle(run, "chest", "rot", lambda p: (-9 * s(p, -0.1), 0, 0), 12)
    cycle(run, "head", "rot", lambda p: (4.5 * s(p, -0.1), 0, 0), 12)
    _aya_tail(run, ((-14, 0, 0), (-10, 0, 0), (-8, 0, 0), (-4, 0, 0)), sway=7, n=12, axis=0)
    hold(run, "ear_l", rot=(26, 0, 10))
    hold(run, "ear_r", rot=(26, 0, -10))

    # ---- sitting: up on her front legs, haunches down, the tail wrapped round her feet, its tip ticking
    def sit_pose(cl, lean=0.0, free=()):
        """Sat up on her front legs; a leg named in `free` is left for the clip to key (a paw she lifts)."""
        hold(cl, "body", pos=(0, -2.1, 1.0))
        cl.rot("body", [(0, -35 + lean, 0, 0), (cl.length, -35 + lean, 0, 0)], "lin")
        for nm in ("fleg_l", "fleg_r"):
            if nm in free:
                hold(cl, nm, scale=(1, 1.3, 1))
            else:
                hold(cl, nm, rot=(35 - lean, 0, 0), scale=(1, 1.3, 1))
        for nm in ("hleg_l", "hleg_r"):
            hold(cl, nm, rot=(-36, 0, 0))

    sit = clip_of(r, "sit", 5.0, loop=True)
    sit_pose(sit)
    cycle(sit, "chest", "scale", lambda p: (1 + 0.022 * s(p * 2), 1 + 0.022 * s(p * 2), 1), 8)
    sit.rot("head", keys((0, 30, 0, 0), (1.4, 30, 0, 0), (1.9, 26, 26, 6), (3.2, 26, 26, 6), (3.7, 30, 0, 0), (5, 30, 0, 0)))
    hold(sit, "tail", rot=(-20, 62, 0))
    hold(sit, "tail2", rot=(-10, 58, 0))
    hold(sit, "tail3", rot=(-8, 46, 0))
    sit.rot("tail4", keys((0, -6, 30, 0), (2.2, -6, 30, 0), (2.35, -6, 52, 0), (2.5, -6, 26, 0), (2.65, -6, 48, 0), (2.9, -6, 30, 0), (5, -6, 30, 0)))
    sit.rot("ear_l", keys((0, 0, 0, 0), (4.0, 0, 0, 0), (4.1, 12, 0, 20), (4.25, 0, 0, 0), (5, 0, 0, 0)))

    # ---- the loaf: everything tucked under, as she sits on the stool by the window
    loaf = clip_of(r, "loaf", 5.0, loop=True)
    hold(loaf, "body", pos=(0, -5.4, 0))
    tuck(loaf, legs)
    cycle(loaf, "chest", "scale", lambda p: (1 + 0.03 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    cycle(loaf, "body", "scale", lambda p: (1 + 0.02 * s(p * 2, 0.1), 1, 1), 8)
    loaf.rot("head", keys((0, 3, 0, 0), (2.0, 3, 0, 0), (2.6, 6, -18, -5), (4.0, 6, -18, -5), (4.6, 3, 0, 0), (5, 3, 0, 0)))
    hold(loaf, "head", pos=(0, -0.6, 0.8))
    hold(loaf, "tail", rot=(-24, 70, 0))
    hold(loaf, "tail2", rot=(-12, 62, 0))
    hold(loaf, "tail3", rot=(-10, 30, 0))
    loaf.rot("tail4", keys((0, -8, 16, 0), (3.1, -8, 16, 0), (3.25, -8, 40, 0), (3.4, -8, 12, 0), (3.6, -8, 16, 0), (5, -8, 16, 0)))
    loaf.rot("ear_r", keys((0, 0, 0, 0), (0.9, 0, 0, 0), (1.0, 12, 0, -20), (1.15, 0, 0, 0), (5, 0, 0, 0)))

    # ---- asleep: curled in a ring, nose to tail
    sleep = clip_of(r, "sleep", 4.4, loop=True)
    hold(sleep, "body", pos=(0, -5.4, 0), rot=(0, 18, 0))
    tuck(sleep, legs)
    hold(sleep, "chest", rot=(0, -46, 0))
    hold(sleep, "head", rot=(22, -62, -10), pos=(0, -2.0, 1.0))
    hold(sleep, "tail", rot=(-22, 74, 0))
    hold(sleep, "tail2", rot=(-12, 70, 0))
    hold(sleep, "tail3", rot=(-10, 56, 0))
    hold(sleep, "tail4", rot=(-8, 34, 0))
    cycle(sleep, "chest", "scale", lambda p: (1 + 0.035 * s(p), 1 + 0.045 * s(p), 1), 8)
    cycle(sleep, "body", "scale", lambda p: (1 + 0.02 * s(p, 0.08), 1 + 0.03 * s(p, 0.08), 1), 8)
    hold(sleep, "ear_l", rot=(10, 0, 14))
    hold(sleep, "ear_r", rot=(10, 0, -14))

    # ---- on her back, belly up, the way she lies on the rug: paws in the air, wriggling
    roll = clip_of(r, "roll", 3.2, loop=True)
    cycle(roll, "body", "rot", lambda p: (0, 8 * s(p), 168 + 12 * s(p)), 8)
    hold(roll, "body", pos=(0, -6.2, 0))
    cycle(roll, "chest", "rot", lambda p: (0, -14 * s(p, 0.1), 16 + 8 * s(p, 0.15)), 8)
    cycle(roll, "head", "rot", lambda p: (-10, 10 * s(p, 0.2), 30 + 10 * s(p, 0.2)), 8)
    hold(roll, "head", pos=(0, -5.5, 0))
    cycle(roll, "fleg_l", "rot", lambda p: (-58 + 16 * s(p * 2), 0, -8), 8)
    cycle(roll, "fleg_r", "rot", lambda p: (-64 + 16 * s(p * 2, 0.5), 0, 8), 8)
    cycle(roll, "hleg_l", "rot", lambda p: (38 + 8 * s(p, 0.3), 0, -22), 8)
    cycle(roll, "hleg_r", "rot", lambda p: (30 + 8 * s(p, 0.6), 0, 22), 8)
    _aya_tail(roll, ((-20, 0, 0), (-12, 14, 0), (-10, 10, 0), (-6, 8, 0)), sway=14, n=8, per=2)

    # ---- hello: the tail straight up and shivering, a head-butt and a rub along your shin
    nudge = clip_of(r, "nudge", 1.8)
    nudge.rot("tail", keys((0, 0, 0, 0), (0.3, 46, 0, 0), (1.5, 46, 0, 0), (1.8, 0, 0, 0)))
    nudge.rot("tail2", keys((0, 0, 0, 0), (0.3, 4, 0, 0), (1.5, 4, 0, 0), (1.8, 0, 0, 0)))
    nudge.rot("tail3", keys((0, 0, 0, 0), (0.3, -4, 0, 0), (1.5, -4, 0, 0), (1.8, 0, 0, 0)))
    nudge.rot("tail4", [(0.3 + 0.075 * k, 0, 12 * (1 if k % 2 else -1), 0) for k in range(16)] + [(1.8, 0, 0, 0)])
    nudge.rot("head", keys((0, 0, 0, 0), (0.4, -14, 18, 20), (0.9, -8, 26, 28), (1.4, -10, 8, 10), (1.8, 0, 0, 0)))
    nudge.rot("chest", keys((0, 0, 0, 0), (0.5, 0, 8, 5), (1.1, 0, 12, 7), (1.8, 0, 0, 0)))
    nudge.rot("body", keys((0, 0, 0, 0), (0.6, 0, -6, 4), (1.2, 0, -8, 5), (1.8, 0, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.6, 0.6, 0.2, -0.6), (1.2, 0.9, 0.2, -1.0), (1.8, 0, 0, 0)))
    for nm in ("ear_l", "ear_r"):
        nudge.rot(nm, keys((0, 0, 0, 0), (0.5, 16, 0, 0), (1.3, 16, 0, 0), (1.8, 0, 0, 0)))

    # ---- the long stretch after a sleep: chest to the floor, tail in the air, then up
    st = clip_of(r, "stretch", 2.6)
    st.rot("body", keys((0, 0, 0, 0), (0.6, 16, 0, 0), (1.5, 18, 0, 0), (2.1, -6, 0, 0), (2.6, 0, 0, 0)))
    st.pos("body", keys((0, 0, 0, 0), (0.6, 0, -1.0, 1.6), (1.5, 0, -1.2, 2.0), (2.1, 0, 0.3, -0.8), (2.6, 0, 0, 0)))
    st.rot("chest", keys((0, 0, 0, 0), (0.6, 12, 0, 0), (1.5, 14, 0, 0), (2.1, -4, 0, 0), (2.6, 0, 0, 0)))
    for nm in ("fleg_l", "fleg_r"):
        st.rot(nm, keys((0, 0, 0, 0), (0.6, -96, 0, 0), (1.5, -100, 0, 0), (2.1, 6, 0, 0), (2.6, 0, 0, 0)))
    for nm in ("hleg_l", "hleg_r"):
        st.rot(nm, keys((0, 0, 0, 0), (0.6, -18, 0, 0), (1.5, -20, 0, 0), (2.1, 16, 0, 0), (2.6, 0, 0, 0)))
    st.rot("head", keys((0, 0, 0, 0), (0.6, -34, 0, 0), (1.5, -40, 0, 0), (2.1, 8, 0, 0), (2.6, 0, 0, 0)))
    st.rot("tail", keys((0, 0, 0, 0), (0.6, 30, 0, 0), (1.5, 36, 0, 0), (2.6, 0, 0, 0)))
    st.rot("tail4", keys((0, 0, 0, 0), (0.8, 30, 0, 0), (1.5, 40, 0, 0), (2.6, 0, 0, 0)))

    # ---- danger near: low to the ground, ears flat, the tail fat and lashing, watching from where she is
    hide = clip_of(r, "hide", 1.6, loop=True)
    hold(hide, "body", pos=(0, -3.2, 0), rot=(4, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, scale=(1, 0.5, 1), rot=(-14, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, scale=(1, 0.55, 1), rot=(10, 0, 0))
    hold(hide, "head", rot=(-6, 0, 0), pos=(0, -1.0, 0.6))
    hold(hide, "ear_l", rot=(34, 0, 38))
    hold(hide, "ear_r", rot=(34, 0, -38))
    for nm in AYA_TAIL[1:]:
        hold(hide, nm, scale=(1.25, 1.25, 1))
    _aya_tail(hide, ((-30, 0, 0), (-12, 0, 0), (-8, 0, 0), (0, 0, 0)), sway=16, n=8)

    # ---- the tower's bells: she sits through it, ears back, the end of her tail ticking
    song = clip_of(r, "song", 2.0, loop=True)
    sit_pose(song)
    hold(song, "head", rot=(34, 0, 0))
    hold(song, "ear_l", rot=(30, 0, 34))
    hold(song, "ear_r", rot=(30, 0, -34))
    hold(song, "tail", rot=(-20, 62, 0))
    hold(song, "tail2", rot=(-10, 58, 0))
    hold(song, "tail3", rot=(-8, 46, 0))
    cycle(song, "tail4", "rot", lambda p: (-6, 30 + 22 * max(0.0, s(p * 2)), 0), 16)

    def sat_tail(cl, flick=0.0):
        hold(cl, "tail", rot=(-20, 62, 0))
        hold(cl, "tail2", rot=(-10, 58, 0))
        hold(cl, "tail3", rot=(-8, 46, 0))
        if flick:
            cycle(cl, "tail4", "rot", lambda p: (-6, 30 + flick * max(0.0, s(p * 2)), 0), 16)
        else:
            hold(cl, "tail4", rot=(-6, 30, 0))

    # ---- Dubu has play-bowed at her: a paw up and two quick swats at the air, ears back, never touching him
    swat = clip_of(r, "swat", 1.4)
    sit_pose(swat, free=("fleg_r",))
    swat.rot("fleg_r", keys((0, 35, 0, 0), (0.22, -52, 0, -10), (0.38, -8, 0, -26), (0.56, -56, 0, -10), (0.72, -4, 0, -26),
                            (1.05, 30, 0, 0), (1.4, 35, 0, 0)))
    swat.rot("head", keys((0, 30, 0, 0), (0.22, 24, -12, 0), (0.72, 24, -12, 0), (1.4, 30, 0, 0)))
    for nm, sg in (("ear_l", 1), ("ear_r", -1)):
        swat.rot(nm, keys((0, 0, 0, 0), (0.2, 26, 0, sg * 28), (0.9, 26, 0, sg * 28), (1.4, 0, 0, 0)))
    sat_tail(swat, 18)

    # ---- washing: a front paw lifted, her head dipping to lick it, then the paw wiped over her face
    groom = clip_of(r, "groom", 3.2, loop=True)
    sit_pose(groom, free=("fleg_r",))
    groom.rot("fleg_r", keys((0, -46, 0, -14), (1.9, -46, 0, -14), (2.2, -84, 0, -8), (2.5, -60, 0, -8), (2.8, -84, 0, -8),
                             (3.2, -46, 0, -14)))
    groom.rot("head", keys((0, 52, -10, 0), (0.25, 58, -10, 0), (0.5, 50, -10, 0), (0.75, 58, -10, 0), (1.0, 50, -10, 0), (1.25, 58, -10, 0),
                           (1.5, 50, -10, 0), (1.9, 52, -10, 0), (2.2, 38, -6, 10), (2.5, 44, -6, -6), (2.8, 38, -6, 10), (3.2, 52, -10, 0)))
    sat_tail(groom)

    # ---- watching something only she can see: sat still, her head following it slowly, the end of her tail ticking
    gaze = clip_of(r, "gaze", 4.4, loop=True)
    sit_pose(gaze)
    gaze.rot("head", keys((0, 16, -22, 0), (1.3, 12, -6, 0), (2.2, 14, 20, 0), (3.3, 18, 8, 0), (4.4, 16, -22, 0)))
    hold(gaze, "ear_l", rot=(-8, 0, 0))
    hold(gaze, "ear_r", rot=(-8, 0, 0))
    sat_tail(gaze, 24)
    cycle(gaze, "chest", "scale", lambda p: (1 + 0.02 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)

    # ---- two more ways of standing about (the game picks one each time round): a long look over her shoulder ...
    idle_b = clip_of(r, "idle_b", 5.0, loop=True)
    cycle(idle_b, "chest", "scale", lambda p: (1 + 0.025 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    _aya_tail(idle_b, ((-6, 0, 0), (-4, 0, 0), (0, 0, 0), (6, 0, 0)), sway=13, n=8)
    idle_b.rot("head", keys((0, 0, 0, 0), (0.6, 0, 0, 0), (1.3, 4, 38, 6), (2.6, 4, 42, 8), (3.0, -2, -16, -4), (3.8, -2, -16, -4),
                            (4.4, 0, 0, 0), (5, 0, 0, 0)))
    idle_b.rot("chest", keys((0, 0, 0, 0), (0.8, 0, 0, 0), (1.4, 0, 8, 0), (2.6, 0, 9, 0), (3.1, 0, -3, 0), (4.4, 0, 0, 0), (5, 0, 0, 0)))
    idle_b.rot("ear_l", keys((0, 0, 0, 0), (1.4, 0, 0, 0), (1.5, 0, 24, 0), (2.5, 0, 24, 0), (2.7, 0, 0, 0), (5, 0, 0, 0)))
    idle_b.rot("ear_r", keys((0, 0, 0, 0), (3.2, 0, 0, 0), (3.3, 12, 0, -20), (3.45, 0, 0, 0), (5, 0, 0, 0)))

    # ---- ... and a step she never finishes: a front paw held up, her head low and forward, the tail gone still
    idle_c = clip_of(r, "idle_c", 4.0, loop=True)
    cycle(idle_c, "chest", "scale", lambda p: (1 + 0.025 * s(p * 2), 1 + 0.025 * s(p * 2), 1), 8)
    idle_c.rot("fleg_l", keys((0, 0, 0, 0), (0.7, 0, 0, 0), (1.0, -38, 0, 0), (2.6, -34, 0, 0), (2.75, -46, 0, 0), (2.9, -34, 0, 0),
                              (3.2, 0, 0, 0), (4, 0, 0, 0)))
    idle_c.scale("fleg_l", keys((0, 1, 1, 1), (0.7, 1, 1, 1), (1.0, 1, 0.72, 1), (2.9, 1, 0.72, 1), (3.2, 1, 1, 1), (4, 1, 1, 1)))
    idle_c.rot("head", keys((0, 0, 0, 0), (0.7, 0, 0, 0), (1.1, 8, 10, 0), (2.9, 8, 10, 0), (3.3, 0, 0, 0), (4, 0, 0, 0)))
    idle_c.pos("head", keys((0, 0, 0, 0), (0.7, 0, 0, 0), (1.1, 0, -0.6, -0.6), (2.9, 0, -0.6, -0.6), (3.3, 0, 0, 0), (4, 0, 0, 0)))
    idle_c.rot("body", keys((0, 0, 0, 0), (1.0, 3, 0, -1.5), (2.9, 3, 0, -1.5), (3.3, 0, 0, 0), (4, 0, 0, 0)))
    for nm in ("ear_l", "ear_r"):
        idle_c.rot(nm, keys((0, 0, 0, 0), (0.9, 0, 0, 0), (1.1, -10, 0, 0), (2.9, -10, 0, 0), (3.2, 0, 0, 0), (4, 0, 0, 0)))
    _aya_tail(idle_c, ((-10, 0, 0), (-6, 0, 0), (0, 0, 0), (8, 0, 0)), sway=3, n=8)

    # ---- getting down and getting up, acted (SPEC 69.9): the haunches and legs go first, her head after, and the tail
    # ---- wraps round last; getting up runs the other way, the tail unwrapping first
    tails = {"tail": (0.3, 0.9), "tail2": (0.36, 0.94), "tail3": (0.42, 0.98), "tail4": (0.48, 1.0)}
    sit_w = {"*": (0.0, 0.8), **tails}
    bridge(r, "to_sit", "sit", 0.55, sit_w, extra={("head", "rot"): [(0.35, (7, 0, 0))]})
    bridge(r, "from_sit", "sit", 0.45, flipped(sit_w), up=True, extra={("head", "rot"): [(0.4, (-6, 0, 0))]})
    rest_w = {"*": (0.0, 0.75), "head": (0.3, 1.0), **tails}
    bridge(r, "to_rest", "loaf", 0.7, rest_w, extra={("body", "rot"): [(0.4, (7, 0, 0))]})
    bridge(r, "from_rest", "loaf", 0.5, flipped(rest_w), up=True, extra={("body", "rot"): [(0.55, (-6, 0, 0))]})
    # curling up to sleep: down first, then her back bends round, her head comes to her tail, her ears fold
    sleep_w = {"*": (0.0, 0.55), ("body", "rot"): (0.3, 0.9), "chest": (0.3, 0.9), "head": (0.45, 1.0), "ear_l": (0.6, 1.0),
               "ear_r": (0.6, 1.0), "tail": (0.35, 0.85), "tail2": (0.4, 0.9), "tail3": (0.45, 0.95), "tail4": (0.5, 1.0)}
    bridge(r, "to_sleep", "sleep", 0.9, sleep_w)
    bridge(r, "from_sleep", "sleep", 0.6, flipped(sleep_w), up=True)
    # the flop: down onto her chest, over onto her back, and only then the paws come up (folded on the way over)
    fold = [(0.3, (0, -0.55, 0)), (0.62, (0, -0.55, 0))]
    roll_w = {"*": (0.38, 1.0), ("body", "pos"): (0.0, 0.5), ("body", "rot"): (0.2, 0.9), "chest": (0.25, 0.95), "head": (0.2, 0.95),
              "tail": (0.3, 0.9), "tail2": (0.35, 0.95), "tail3": (0.4, 1.0), "tail4": (0.45, 1.0)}
    bridge(r, "to_roll", "roll", 0.8, roll_w, extra={(nm, "scale"): fold for nm in legs})
    bridge(r, "from_roll", "roll", 0.6, flipped(roll_w), up=True, extra={(nm, "scale"): [(1 - u, v) for u, v in reversed(fold)] for nm in legs})
    stubs(r)


BLACK = Ramp("#0d0e14", "#15161e", "#1e202b", "#292c39", "#363a49", "#484d5e")
WHITE = Ramp("#a4a8bd", "#bfc3d2", "#d6d9e4", "#e8eaf0", "#f5f6f9", "#ffffff")
PINK = Ramp("#b9727c", "#d48c95", "#e9a6ad", "#f4bfc4")
EAR = Ramp("#6d4a55", "#94656f", "#b98891")
TAILS = ("tail1", "otail2", "otail3", "otail4")


def aya_coat(part, p):
    """True where Aya is black. Body space: x + is her left, y down, z + is back; the torso runs z -7 .. 9, its back
    is at y 10 and its belly at y 18. Every band is three pixels or wider, so none reads as a seam."""
    x, y, z = p
    if part in ("oear_l", "eartip_l") or part in TAILS:
        return True
    if part in ("ofleg_l", "ohleg_l", "bib", "muzzle"):
        return False
    if part == "haunch_l":
        return y < 17.0                       # black haunches over white feet
    down = y - 10.0                           # 0 at the spine, 8 at the belly
    left = x > 0
    if down < 0.25:
        # along the top of her back she is black from her cap to her tail: her white comes up her sides and stops at
        # the spine, so no sliver of white shows along her back when she is seen from the side (in the game it read as
        # a bright line)
        return True
    if z < -4.0:
        return False                          # the white collar behind her head, and her chest
    if z < -1.0:                              # the shoulder patch: over the top, further down on her left
        return down < (4.0 if left else 2.0)
    if z < 2.0:                               # the white band between the shoulder patch and the saddle
        return (not left) and z < 1.0 and 5.0 <= down < 7.0      # with one black spot low on her right flank
    if abs(x) > 4.0 and z >= 3.0:
        return down < 7.0                     # under the haunch the flank is the haunch's colour
    if z < 6.0:                               # the saddle
        return down < (3.0 if left else 4.0) + (1.0 if z >= 4.0 else 0.0)
    return down < (6.0 if z >= 7.5 else 5.0)  # the rump, black nearly to the belly at the tail


def paint_aya(r, P):
    T = texels(r)
    for (tu, tv), (part, fname, i, j, p, n) in T.items():
        if part in ("ohead", "cheek_l", "cheek_r", "muzzle"):
            continue
        black = aya_coat(part, p)
        if part == "oear_l" and fname == "north" and 0 < i < 3:
            P.skin.set(tu, tv, EAR.at(1.4 if (i, j) == (1, 1) else 0.6))   # the ear's inside: dusky pink
            continue
        if part in ("ofleg_l", "ohleg_l") and fname == "bottom":
            P.skin.set(tu, tv, PINK.at(1.0 + ((i + j) % 2)))          # paw pads
            continue
        if part in TAILS:
            col = fur(BLACK, 2.2, p, n, part, lock=2, depth=0.5, seed=3, fall=0.0)   # a thick, long-furred tail
        elif black:
            col = fur(BLACK, 2.1, p, n, part, lock=4, depth=0.35, seed=7, fall=0.06)
        else:
            col = fur(WHITE, 4.0, p, n, part, lock=5, depth=0.14, seed=7, fall=0.2)
        P.skin.set(tu, tv, col)
    _aya_head(r, P, T)
    return P.skin, P.glow


def _aya_head(r, P, T):
    """Her face, drawn as you see her: columns run from her right cheek (your left) to her left."""
    B, Wt = "B", "W"
    # rows 0..7 of the front of the head (the muzzle box covers columns 3..7 of rows 5..7)
    front = [
        "BBBBBBBBBBB",
        "BBBBWBBBBBB",    # the blaze's tip, a little to her right of the middle
        "BEEEWWBEEEB",    # eyes
        "BEEEWWBEEEB",
        "WWWWWWWBBBB",    # white under her right eye; the wedge's point and her black left cheek
        "WWWWWWWWWBB",
        "WWWWWWWWWWB",
        "WWWWWWWWWWW",
    ]
    hy0, hx0, hz0 = 14 - 3 - 6, -5.5, 1 - 8 - 6        # the head box's top, right and front in body space

    def put(tu, tv, black, part, p, n, k=0.0):
        if black:
            P.skin.set(tu, tv, fur(BLACK, 2.1 + k, p, n, part, lock=4, depth=0.3, seed=11, fall=0.0))
        else:
            P.skin.set(tu, tv, fur(WHITE, 4.0 + k, p, n, part, lock=5, depth=0.12, seed=11, fall=0.25))

    for (tu, tv), (part, fname, i, j, p, n) in T.items():
        x, y, z = p
        if part == "ohead":
            col, row, back = int(math.floor(x - hx0)), int(math.floor(y - hy0)), z - hz0
            if fname == "north":
                put(tu, tv, front[row][col] != Wt, part, p, n)
            elif fname == "top":
                put(tu, tv, True, part, p, n)
            elif fname == "south":
                put(tu, tv, row < 5, part, p, n)                      # the cap ends at the nape
            elif fname == "bottom":
                put(tu, tv, False, part, p, n)
            elif x > 0:                                               # her left: the black cheek runs lower
                put(tu, tv, row < 5 or (row < 7 and back < 2) or (row < 6 and back < 4), part, p, n)
            else:                                                     # her right: white comes up under the eye
                put(tu, tv, row < 4 or (row < 5 and back > 3), part, p, n)
        elif part == "cheek_l":
            put(tu, tv, y - hy0 < 6, part, p, n, 0.1)
        elif part == "cheek_r":
            put(tu, tv, False, part, p, n, 0.1)
    # eyes: green, a slit of a pupil, a glint, shaded under the lid
    with P.eyes():
        f = P.face("ohead", "north")
        for ex in (1, 7):
            for dj, row in enumerate((("#bccf5e", "#14160d", "#bccf5e"), ("#8fa244", "#14160d", "#9db04b"))):
                for di, colr in enumerate(row):
                    f.set(ex + di, 2 + dj, hexc(colr))
            f.set(ex, 2, hexc("#e4eeae"))
    # the muzzle: white, a pink nose, the mouth's line under it; the black wedge ends beside the nose
    for (tu, tv), (part, fname, i, j, p, n) in T.items():
        if part != "muzzle":
            continue
        x, y, z = p
        col = fur(WHITE, 4.2, p, n, part, lock=9, depth=0.0, seed=5, fall=0.3)
        mx, my = int(math.floor(x + 2.5)), int(math.floor(y - (14 - 3 - 2)))
        if fname == "north":
            if my == 0 and mx == 2:
                col = PINK.at(2.0)
            elif my == 1 and mx == 2:
                col = hexc("#c3a4ad")
            elif my == 2 and mx == 2:
                col = WHITE.at(2.2)
        elif fname == "top":
            if mx == 2:
                col = PINK.at(2.8) if z < hz0 - 0.9 else WHITE.at(4.4)
            elif mx == 3 and z > hz0 - 1.0:
                col = fur(BLACK, 2.3, p, n, part, seed=5)
        P.skin.set(tu, tv, col)


# =====================================================================================================================
# Dubu
# =====================================================================================================================

def dubu():
    r = Rig("family_dubu", (128, 64))
    r.walk_stride, r.run_stride, r.run_speed, r.shadow = 0.31, 0.85, (0.12, 0.26), 0.36
    body = r.bone("body", (0, 15.5, 2))
    body.cube("rump", -4, -3.5, 0, 8, 7, 10)
    chest = body.bone("chest", (0, 0, 0))
    chest.cube("ochest", -4, -3.5, -11, 8, 7, 12, inflate=-0.02)     # laps a pixel into the rump, as Aya's
    chest.cube("ruff", -3, -1, -12.5, 6, 6, 2)                       # the feathered chest
    head = chest.bone("head", (0, -3, -10.5))
    head.cube("ohead", -4.5, -6, -5, 9, 7, 7)
    head.cube("muzzle", -2.5, -2, -8, 5, 3, 3)
    head.cube("nose", -1.5, -2.5, -8.5, 3, 2, 1)
    toy = head.bone("toy", (0, 1.4, -7.5))                           # his yellow giraffe, held crosswise
    toy.cube("toybody", -4, -1, -1, 6, 2, 2)
    toy.cube("toyhead", 2, -2, -1.5, 3, 3, 3)

    def side(sd, nm, mir, sh):
        ear = head.bone("ear_" + nm, (sd * 4.5, -5, -1.5), (0, 0, sd * -10))
        ear.cube("oear_" + nm, 0 if sd > 0 else -2, 0, -2, 2, 6, 4, mirror=mir, share=sh("oear_l"))
        fl = chest.bone("fleg_" + nm, (sd * 2.5, 3.5, -8.5))
        fl.cube("ofleg_" + nm, -1.5, 0, -1.5, 3, 5, 3, mirror=mir, share=sh("ofleg_l"))
        hl = body.bone("hleg_" + nm, (sd * 2.5, 0.5, 7))
        hl.cube("haunch_" + nm, -2.75 if sd > 0 else -1.25, -1, -3.25, 4, 5, 6, mirror=mir, share=sh("haunch_l"))
        hl.cube("ohleg_" + nm, -1.5, 3, -1, 3, 5, 3, mirror=mir, share=sh("ohleg_l"))
    sym(side)
    tail = body.bone("tail", (0, -2.5, 9.5), (52, 0, 0))
    tail.cube("tail1", -1.5, -1.5, -0.5, 3, 3, 4)
    t2 = tail.bone("tail2", (0, 0, 3), (26, 0, 0))
    t2.cube("otail2", -2, -2, -0.5, 4, 4, 4)
    t3 = t2.bone("tail3", (0, 0, 3), (24, 0, 0))
    t3.cube("otail3", -2, -2.5, -0.5, 4, 5, 4)
    t4 = t3.bone("tail4", (0, 0, 3), (22, 0, 0))
    t4.cube("otail4", -2, -2, -0.5, 4, 4, 4)
    t5 = t4.bone("tail5", (0, 0, 3), (18, 0, 0))
    t5.cube("otail5", -1.5, -1.5, -0.5, 3, 3, 3)
    r.look = [("head", 0.9, 0.7), ("chest", 0.08, 0.0)]
    r.pack()
    _dubu_clips(r)
    return r


CREAM = Ramp("#b3a999", "#cfc6b7", "#e4ddd0", "#f2eee4", "#fbf9f3", "#ffffff")
FAWN = Ramp("#a98b66", "#c2a47e", "#d3b792", "#dfc7a5", "#ead6ba", "#f3e5d0")   # a pale apricot, as his ears are
DUBU_TAIL = ("tail1", "otail2", "otail3", "otail4", "otail5")


def paint_dubu(r, P):
    T = texels(r)
    hy0, hx0, hz0 = 15.5 - 3 - 6, -4.5, 2 - 10.5 - 5     # the head box's top, right and front in body space
    front = [
        "FFFWWWFFF",
        "FFFWWWFFF",
        "FEEWWWEEF",
        "fEEWWWEEf",
        "WWWWWWWWW",      # white under his eyes, white cheeks
        "WWWWWWWWW",
        "WWWWWWWWW",
    ]
    for (tu, tv), (part, fname, i, j, p, n) in T.items():
        x, y, z = p
        col = None
        if part in ("toybody", "toyhead"):
            spot = _hash("toy", int(math.floor(x * 1.0 + 20)), int(math.floor(z + 20)), int(math.floor(y + 20))) % 5 == 0
            col = hexc("#b98a3e") if spot else hexc("#f0d878")
            if n[1] > 0.5:
                col = mul(col, 0.8)
            if part == "toyhead" and fname in ("north", "east") and (i, j) == (1, 1):
                col = hexc("#3a2c20")
        elif part == "nose":
            col = hexc("#3b2f33") if n[1] < -0.5 else hexc("#241b1f")
        elif part == "oear_l":
            # fawn, darker toward the tip, a pale feathered fringe along the bottom edge
            down = y - (hy0 + 1)
            col = fur(FAWN, 2.2 - 0.12 * down, p, n, part, lock=2, depth=0.4, seed=2, fall=0.0)
            if down > 4.9:
                col = FAWN.at(3.8)                                    # the feathered fringe, paler
        elif part == "ohead":
            c0, row, back = int(math.floor(x - hx0)), int(math.floor(y - hy0)), z - hz0
            if fname == "north":
                ch = front[row][c0]
                col = (fur(FAWN, 2.4, p, n, part, lock=3, depth=0.25, seed=4, fall=0.0) if ch in "FE"
                       else FAWN.at(4.0) if ch == "f" else fur(CREAM, 4.2, p, n, part, lock=5, depth=0.1, seed=4))
            elif fname == "top":
                # a wide white blaze over the crown; fawn over each eye, running back to the ear
                col = (fur(CREAM, 4.2, p, n, part, lock=5, depth=0.1, seed=4) if abs(x) < 1.5 or back > 5
                       else fur(FAWN, 2.4, p, n, part, lock=3, depth=0.25, seed=4, fall=0.0))
            elif fname == "south":
                col = fur(CREAM, 4.0, p, n, part, lock=5, depth=0.1, seed=4)
            elif fname == "bottom":
                col = fur(CREAM, 4.0, p, n, part, seed=4)
            else:
                # the side of his face is white; the eye's patch wraps a little way round, above the ear
                fawn = (row < 3 and back < 4) or (row < 1 and back < 6)
                col = (fur(FAWN, 2.4, p, n, part, lock=3, depth=0.25, seed=4, fall=0.0) if fawn
                       else FAWN.at(4.2) if row == 3 and back < 3
                       else fur(CREAM, 4.1, p, n, part, lock=5, depth=0.1, seed=4))
        elif part == "muzzle":
            col = fur(CREAM, 4.4, p, n, part, lock=9, depth=0.0, seed=5, fall=0.3)
            mx, my = int(math.floor(x + 2.5)), int(math.floor(y - (15.5 - 3 - 2)))
            if fname == "north" and my == 2 and mx == 2:
                col = hexc("#6a5a55")
            elif fname == "north" and my == 1 and mx == 2:
                col = hexc("#b9a9a0")
            elif fname in ("west", "east") and my == 2 and z < hz0 - 1.0:
                col = hexc("#b9a9a0")                                  # the line of his mouth
        elif part in DUBU_TAIL:
            col = fur(CREAM, 4.3, p, n, part, lock=2, depth=0.3, seed=6, fall=0.0)     # a white plume
        elif part in ("ofleg_l", "ohleg_l") and fname == "bottom":
            col = hexc("#d9a3a0") if (i + j) % 2 else hexc("#c98c8c")
        else:
            col = fur(CREAM, 4.1, p, n, part, lock=5, depth=0.14, seed=9, fall=0.2)
            if part == "rump":
                # the fawn at his tail's root spreads a little onto his back, and one faint freckle on a shoulder
                # a small pale patch on his back where the tail leaves it (the tail itself is white)
                if y < 13.0 and z > 10.0 and abs(x) < 1.0:
                    col = FAWN.at(3.4)
            if part == "ochest" and y < 13.0 and -5.0 < z < -4.0 and 1.0 < x < 2.0:
                col = FAWN.at(4.4)                                    # and one faint freckle behind a shoulder
            if part == "ruff" and fname == "bottom":
                col = CREAM.at(2.0)
        P.skin.set(tu, tv, col)
    with P.eyes():
        f = P.face("ohead", "north")
        for ex in (1, 6):
            for dj in (0, 1):
                for di in (0, 1):
                    f.set(ex + di, 2 + dj, hexc("#221815"))
            f.set(ex, 2, hexc("#fff7ea"))                              # the wet glint
            f.set(ex + 1, 3, hexc("#3d2a20"))
    return P.skin, P.glow


def _dubu_tail(cl, wag=0.0, per=2.0, lift=0.0, n=12):
    """His plume: held where it sits (plus `lift` at the root), wagging from the root."""
    cycle(cl, "tail", "rot", lambda p: (lift, wag * s(p * per), 0), n)
    cycle(cl, "tail2", "rot", lambda p: (0, wag * 0.6 * s(p * per, -0.08), wag * 0.3 * s(p * per, -0.08)), n)
    cycle(cl, "tail3", "rot", lambda p: (0, wag * 0.5 * s(p * per, -0.16), 0), n)


def _dubu_clips(r):
    legs = ("fleg_l", "fleg_r", "hleg_l", "hleg_r")

    # ---- standing: the plume wagging lazily, a quick breath, a head tilt at whatever you just said
    idle = clip_of(r, "idle", 4.0, loop=True)
    _dubu_tail(idle, wag=14, per=4, n=16)
    cycle(idle, "chest", "scale", lambda p: (1 + 0.025 * s(p * 4), 1 + 0.025 * s(p * 4), 1), 16)
    idle.rot("head", keys((0, 0, 0, 0), (1.5, 0, 0, 0), (1.8, -4, 6, 22), (3.0, -4, 6, 22), (3.3, 0, 0, 0), (4, 0, 0, 0)))
    idle.rot("ear_l", keys((0, 0, 0, 0), (1.5, 0, 0, 0), (1.8, 0, 0, 14), (3.0, 0, 0, 14), (3.4, 0, 0, -4), (3.6, 0, 0, 0), (4, 0, 0, 0)))
    idle.rot("ear_r", keys((0, 0, 0, 0), (1.5, 0, 0, 0), (1.8, 0, 0, 10), (3.0, 0, 0, 10), (3.4, 0, 0, -4), (3.6, 0, 0, 0), (4, 0, 0, 0)))

    # ---- trotting: diagonal feet together, short legs going fast, ears and plume bouncing
    def trot(cl, swing, bounce, flap, wag, lift=1.4):
        for nm, ph in (("fleg_l", 0.0), ("hleg_r", 0.04), ("fleg_r", 0.5), ("hleg_l", 0.54)):
            step_leg(cl, nm, swing, ph, lift, stance=0.5, reach=3 if nm[0] == "f" else -3)
        cycle(cl, "body", "pos", lambda p: (0, bounce * c(p * 2, 0.12), 0), 12)
        cycle(cl, "body", "rot", lambda p: (1.5 * s(p * 2), 2.5 * s(p), 2.0 * s(p, 0.25)), 12)
        cycle(cl, "chest", "rot", lambda p: (0, -4.5 * s(p), -2.0 * s(p, 0.25)), 12)
        cycle(cl, "head", "rot", lambda p: (-4 + 2.0 * c(p * 2, 0.2), 2 * s(p), 0), 12)
        cycle(cl, "ear_l", "rot", lambda p: (0, 0, -flap * max(0.0, c(p * 2, 0.3))), 12)
        cycle(cl, "ear_r", "rot", lambda p: (0, 0, flap * max(0.0, c(p * 2, 0.3))), 12)
        _dubu_tail(cl, wag=wag, per=2, lift=4, n=12)

    move = clip_of(r, "move", 0.52, loop=True)
    trot(move, 30, 0.5, 14, 16)

    carry = clip_of(r, "carry", 0.52, loop=True)
    trot(carry, 30, 0.6, 16, 24)
    hold(carry, "toy", scale=(1, 1, 1))

    # ---- running flat out: both hind feet, then both fore, ears streaming, the plume laid back
    run = clip_of(r, "run", 0.36, loop=True)
    for nm, ph in (("hleg_l", 0.0), ("hleg_r", 0.07), ("fleg_l", 0.45), ("fleg_r", 0.53)):
        step_leg(run, nm, 46, ph, 2.2, stance=0.38, reach=5 if nm[0] == "f" else -5)
    cycle(run, "body", "pos", lambda p: (0, 1.2 * s(p, 0.15), 0), 12)
    cycle(run, "body", "rot", lambda p: (6 * s(p, -0.1), 0, 0), 12)
    cycle(run, "chest", "rot", lambda p: (-10 * s(p, -0.1), 0, 0), 12)
    cycle(run, "head", "rot", lambda p: (5 * s(p, -0.1) - 4, 0, 0), 12)
    cycle(run, "ear_l", "rot", lambda p: (38 + 10 * s(p * 2), 0, -34), 12)
    cycle(run, "ear_r", "rot", lambda p: (38 + 10 * s(p * 2), 0, 34), 12)
    cycle(run, "tail", "rot", lambda p: (-46 + 8 * s(p), 0, 0), 12)
    hold(run, "tail2", rot=(-16, 0, 0))
    hold(run, "tail3", rot=(-14, 0, 0))

    # ---- sitting: looking up at you, the plume sweeping the floor
    sit = clip_of(r, "sit", 4.0, loop=True)
    hold(sit, "body", pos=(0, -1.0, 1.0), rot=(-26, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(sit, nm, rot=(26, 0, 0), scale=(1, 1.6, 1))
    for nm in ("hleg_l", "hleg_r"):
        hold(sit, nm, rot=(-34, 0, 0))
    sit.rot("head", keys((0, 12, 0, 0), (1.2, 12, 0, 0), (1.5, 6, -6, -20), (2.8, 6, -6, -20), (3.1, 12, 0, 0), (4, 12, 0, 0)))
    cycle(sit, "tail", "rot", lambda p: (-58, 26 * s(p * 4), 0), 16)
    cycle(sit, "tail2", "rot", lambda p: (-30, 16 * s(p * 4, -0.1), 0), 16)
    hold(sit, "tail3", rot=(-24, 0, 0))
    cycle(sit, "chest", "scale", lambda p: (1 + 0.025 * s(p * 4), 1 + 0.025 * s(p * 4), 1), 16)

    # ---- lying down with his chin on his paws, eyes following you
    lie = clip_of(r, "lie", 5.0, loop=True)
    hold(lie, "body", pos=(0, -4.6, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(lie, nm, rot=(-86, 0, 0), pos=(0, 0.9, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(lie, nm, scale=(1, 0.12, 1))
    lie.rot("head", keys((0, 12, 0, 0), (2.2, 12, 0, 0), (2.6, 4, 14, 4), (4.0, 4, 14, 4), (4.5, 12, 0, 0), (5, 12, 0, 0)))
    lie.pos("head", keys((0, 0, -3.6, -0.5), (2.2, 0, -3.6, -0.5), (2.6, 0, -2.2, -0.2), (4.0, 0, -2.2, -0.2), (4.5, 0, -3.6, -0.5), (5, 0, -3.6, -0.5)))
    hold(lie, "ear_l", rot=(0, 0, -16))
    hold(lie, "ear_r", rot=(0, 0, 16))
    cycle(lie, "tail", "rot", lambda p: (-70, 10 * max(0.0, s(p * 3)), 0), 12)
    hold(lie, "tail2", rot=(-28, 0, 0))
    hold(lie, "tail3", rot=(-22, 0, 0))
    cycle(lie, "chest", "scale", lambda p: (1 + 0.03 * s(p * 2), 1 + 0.02 * s(p * 2), 1), 8)

    # ---- asleep on his side, stretched long, his giraffe under his chin; now and then a paw runs in a dream
    sleep = clip_of(r, "sleep", 5.0, loop=True)
    hold(sleep, "body", pos=(0, -4.5, 0), rot=(0, 0, 90))
    hold(sleep, "chest", rot=(0, -8, 0))
    hold(sleep, "head", rot=(0, -14, 0), pos=(0, 0, 0))
    sleep.rot("fleg_l", keys((0, -30, 0, 0), (3.0, -30, 0, 0), (3.15, -44, 0, 0), (3.3, -26, 0, 0), (3.45, -42, 0, 0), (3.7, -30, 0, 0), (5, -30, 0, 0)))
    hold(sleep, "fleg_r", rot=(-42, 0, 8))
    hold(sleep, "hleg_l", rot=(28, 0, 0))
    hold(sleep, "hleg_r", rot=(40, 0, 6))
    hold(sleep, "tail", rot=(-52, 0, 0))
    hold(sleep, "tail2", rot=(-30, 0, 0))
    hold(sleep, "tail3", rot=(-26, 0, 0))
    hold(sleep, "ear_l", rot=(0, 0, 30))
    hold(sleep, "toy", scale=(1, 1, 1))
    cycle(sleep, "chest", "scale", lambda p: (1 + 0.035 * s(p * 1.0), 1 + 0.02 * s(p), 1), 8)

    # ---- hello: a play-bow, a hop, the whole back end wagging
    nudge = clip_of(r, "nudge", 2.0)
    nudge.rot("body", keys((0, 0, 0, 0), (0.3, 16, 0, 0), (0.7, 18, 0, 0), (0.95, -12, 0, 0), (1.2, 0, 0, 0), (2.0, 0, 0, 0)))
    nudge.pos("body", keys((0, 0, 0, 0), (0.3, 0, -1.0, 0), (0.7, 0, -1.2, 0), (0.95, 0, 2.6, -1.0), (1.2, 0, 0, -1.4), (2.0, 0, 0, 0)))
    nudge.rot("chest", keys((0, 0, 0, 0), (0.3, 10, 0, 0), (0.7, 12, 0, 0), (0.95, -6, 0, 0), (1.2, 0, 0, 0), (2.0, 0, 0, 0)))
    for nm in ("fleg_l", "fleg_r"):
        nudge.rot(nm, keys((0, 0, 0, 0), (0.3, -70, 0, 0), (0.7, -74, 0, 0), (0.95, -30, 0, 0), (1.2, 0, 0, 0), (2.0, 0, 0, 0)))
    for nm in ("hleg_l", "hleg_r"):
        nudge.rot(nm, keys((0, 0, 0, 0), (0.3, -16, 0, 0), (0.7, -18, 0, 0), (0.95, 30, 0, 0), (1.2, 0, 0, 0), (2.0, 0, 0, 0)))
    nudge.rot("head", keys((0, 0, 0, 0), (0.3, -30, 0, 0), (0.7, -34, 0, 10), (0.95, 6, 0, 0), (1.3, -6, 0, -14), (2.0, 0, 0, 0)))
    nudge.rot("tail", [(0.1 * k, 0, 34 * (1 if k % 2 else -1), 0) for k in range(20)] + [(2.0, 0, 0, 0)])
    nudge.rot("tail2", [(0.1 * k + 0.03, 0, 20 * (1 if k % 2 else -1), 0) for k in range(19)] + [(2.0, 0, 0, 0)])
    nudge.rot("ear_l", keys((0, 0, 0, 0), (0.95, 0, 0, -30), (1.2, 0, 0, 10), (1.5, 0, 0, 0), (2.0, 0, 0, 0)))
    nudge.rot("ear_r", keys((0, 0, 0, 0), (0.95, 0, 0, 30), (1.2, 0, 0, -10), (1.5, 0, 0, 0), (2.0, 0, 0, 0)))

    # ---- danger near: low, ears back, the plume down, backing off and watching
    hide = clip_of(r, "hide", 1.2, loop=True)
    hold(hide, "body", pos=(0, -2.6, 0), rot=(-4, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(hide, nm, scale=(1, 0.58, 1), rot=(-10, 0, 0))
    for nm in ("hleg_l", "hleg_r"):
        hold(hide, nm, scale=(1, 0.6, 1), rot=(14, 0, 0))
    hold(hide, "head", rot=(8, 0, 0), pos=(0, -1.6, 0.4))
    hold(hide, "ear_l", rot=(30, 0, -20))
    hold(hide, "ear_r", rot=(30, 0, 20))
    hold(hide, "tail", rot=(-112, 0, 0))
    hold(hide, "tail2", rot=(-34, 0, 0))
    hold(hide, "tail3", rot=(-30, 0, 0))
    hold(hide, "tail4", rot=(-20, 0, 0))
    hold(hide, "tail5", rot=(-14, 0, 0))
    cycle(hide, "body", "scale", lambda p: (1 + 0.012 * s(p * 4), 1, 1), 16)

    # ---- the tower's bells: sat down, nose to the sky, howling along
    song = clip_of(r, "song", 2.4, loop=True)
    hold(song, "body", pos=(0, -1.0, 1.0), rot=(-26, 0, 0))
    for nm in ("fleg_l", "fleg_r"):
        hold(song, nm, rot=(26, 0, 0), scale=(1, 1.6, 1))
    for nm in ("hleg_l", "hleg_r"):
        hold(song, nm, rot=(-34, 0, 0))
    song.rot("head", keys((0, -8, 0, 0), (0.5, -44, 0, 0), (1.7, -50, 0, 4), (2.1, -20, 0, 0), (2.4, -8, 0, 0)))
    song.rot("ear_l", keys((0, 0, 0, 0), (0.5, 20, 0, -8), (1.7, 24, 0, -10), (2.4, 0, 0, 0)))
    song.rot("ear_r", keys((0, 0, 0, 0), (0.5, 20, 0, 8), (1.7, 24, 0, 10), (2.4, 0, 0, 0)))
    cycle(song, "tail", "rot", lambda p: (-58, 14 * s(p * 3), 0), 16)
    hold(song, "tail2", rot=(-30, 0, 0))
    hold(song, "tail3", rot=(-24, 0, 0))

    # ---- nose to the ground, following something along it, the plume going
    sniff = clip_of(r, "sniff", 2.0, loop=True)
    hold(sniff, "body", rot=(7, 0, 0))
    cycle(sniff, "head", "rot", lambda p: (40 + 4 * s(p * 4), 14 * s(p), 0), 16)
    hold(sniff, "head", pos=(0, -1.6, -0.4))
    cycle(sniff, "ear_l", "rot", lambda p: (0, 0, -8 * max(0.0, s(p * 4))), 16)
    cycle(sniff, "ear_r", "rot", lambda p: (0, 0, 8 * max(0.0, s(p * 4))), 16)
    _dubu_tail(sniff, wag=16, per=4, n=16)

    # ---- sat down, a hind foot going at the back of his ear
    scratch = clip_of(r, "scratch", 1.2, loop=True)
    hold(scratch, "body", pos=(0, -1.0, 1.0), rot=(-26, 0, 8))
    for nm in ("fleg_l", "fleg_r"):
        hold(scratch, nm, rot=(26, 0, -8), scale=(1, 1.6, 1))
    hold(scratch, "hleg_r", rot=(-34, 0, 0))
    cycle(scratch, "hleg_l", "rot", lambda p: (-104 + 12 * s(p * 6), 0, -14), 24)
    cycle(scratch, "head", "rot", lambda p: (6, 0, 26 + 3 * s(p * 6)), 24)
    cycle(scratch, "ear_l", "rot", lambda p: (0, 0, -14 + 8 * s(p * 6, 0.2)), 24)
    hold(scratch, "tail", rot=(-58, 0, 0))
    hold(scratch, "tail2", rot=(-30, 0, 0))
    hold(scratch, "tail3", rot=(-24, 0, 0))

    # ---- two more ways of standing about (the game picks one each time round): the shake, head first and running
    # ---- down him to his rump, ears flying ...
    idle_b = clip_of(r, "idle_b", 3.6, loop=True)
    _dubu_tail(idle_b, wag=14, per=4, n=16)
    cycle(idle_b, "chest", "scale", lambda p: (1 + 0.025 * s(p * 4), 1 + 0.025 * s(p * 4), 1), 16)

    def swing(k, amp):
        return amp * (1 if k % 2 else -1) * math.sin(math.pi * (k + 0.5) / 12)

    idle_b.rot("head", [(0, 0, 0, 0), (0.9, 0, 0, 0)] + [(1.0 + 0.075 * k, 0, 0, swing(k, 32)) for k in range(12)]
               + [(1.95, 0, 0, 0), (2.3, -4, 6, 18), (3.0, -4, 6, 18), (3.3, 0, 0, 0), (3.6, 0, 0, 0)])
    idle_b.rot("chest", [(0, 0, 0, 0), (0.93, 0, 0, 0)] + [(1.03 + 0.075 * k, 0, 0, swing(k, 16)) for k in range(12)]
               + [(1.98, 0, 0, 0), (3.6, 0, 0, 0)])
    idle_b.rot("body", [(0, 0, 0, 0), (0.96, 0, 0, 0)] + [(1.06 + 0.075 * k, 0, 0, swing(k, 11)) for k in range(12)]
               + [(2.02, 0, 0, 0), (3.6, 0, 0, 0)])
    idle_b.rot("ear_l", [(0, 0, 0, 0), (0.95, 0, 0, 0)] + [(1.04 + 0.075 * k, 0, 0, -18 - swing(k, 40)) for k in range(12)]
               + [(2.0, 0, 0, 6), (2.15, 0, 0, 0), (3.6, 0, 0, 0)])
    idle_b.rot("ear_r", [(0, 0, 0, 0), (0.95, 0, 0, 0)] + [(1.04 + 0.075 * k, 0, 0, 18 - swing(k, 40)) for k in range(12)]
               + [(2.0, 0, 0, -6), (2.15, 0, 0, 0), (3.6, 0, 0, 0)])

    # ---- ... and a paw held up, looking one way and then the other, the plume going hard: is it time yet?
    idle_c = clip_of(r, "idle_c", 4.0, loop=True)
    _dubu_tail(idle_c, wag=22, per=6, n=24)
    cycle(idle_c, "chest", "scale", lambda p: (1 + 0.025 * s(p * 4), 1 + 0.025 * s(p * 4), 1), 16)
    idle_c.rot("fleg_r", keys((0, 0, 0, 0), (0.8, 0, 0, 0), (1.1, -44, 0, 0), (2.8, -40, 0, 0), (3.1, 0, 0, 0), (4, 0, 0, 0)))
    idle_c.scale("fleg_r", keys((0, 1, 1, 1), (0.8, 1, 1, 1), (1.1, 1, 0.7, 1), (2.8, 1, 0.7, 1), (3.1, 1, 1, 1), (4, 1, 1, 1)))
    idle_c.rot("head", keys((0, 0, 0, 0), (0.8, 0, 0, 0), (1.1, -8, -10, -16), (1.9, -8, -10, -16), (2.1, -8, -6, 14), (2.8, -8, -6, 14),
                            (3.2, 0, 0, 0), (4, 0, 0, 0)))
    idle_c.rot("ear_l", keys((0, 0, 0, 0), (0.9, 0, 0, 0), (1.1, 0, 0, 10), (2.8, 0, 0, 10), (3.1, 0, 0, 0), (4, 0, 0, 0)))
    idle_c.rot("ear_r", keys((0, 0, 0, 0), (0.9, 0, 0, 0), (1.1, 0, 0, -10), (2.8, 0, 0, -10), (3.1, 0, 0, 0), (4, 0, 0, 0)))

    # ---- getting down and getting up, acted (SPEC 69.9). Sitting: rump and legs together, the plume settling last.
    tails = {"tail": (0.3, 0.9), "tail2": (0.36, 0.95), "tail3": (0.42, 1.0)}
    sit_w = {"*": (0.0, 0.8), **tails}
    bridge(r, "to_sit", "sit", 0.5, sit_w, extra={("head", "rot"): [(0.35, (8, 0, 0))]})
    bridge(r, "from_sit", "sit", 0.45, flipped(sit_w), up=True, extra={("head", "rot"): [(0.4, (-7, 0, 0))]})
    # lying down: his front feet slide out first and his chest follows them down, then his chin comes to his paws
    rest_w = {"*": (0.05, 0.75), "fleg_l": (0.0, 0.6), "fleg_r": (0.0, 0.6), "head": (0.45, 1.0), "ear_l": (0.5, 1.0), "ear_r": (0.5, 1.0),
              **tails}
    bridge(r, "to_rest", "lie", 0.7, rest_w, extra={("body", "rot"): [(0.38, (8, 0, 0))]})
    bridge(r, "from_rest", "lie", 0.5, flipped(rest_w), up=True, extra={("body", "rot"): [(0.55, (-6, 0, 0))]})
    # going to sleep: down, then over onto his side with a flop, legs folded on the way and stretched out after; the
    # giraffe is under his chin once he is there
    fold = [(0.28, (0, -0.55, 0)), (0.6, (0, -0.55, 0))]
    toy = {("toy", "scale"): (0.001, 0.001, 0.001)}
    sleep_w = {"*": (0.4, 1.0), ("body", "pos"): (0.0, 0.5), ("body", "rot"): (0.3, 0.92), "chest": (0.45, 1.0), "head": (0.45, 1.0),
               "toy": (0.9, 1.0), **tails}
    bridge(r, "to_sleep", "sleep", 0.85, sleep_w, extra={(nm, "scale"): fold for nm in legs}, rest=toy, settle=0.06)
    bridge(r, "from_sleep", "sleep", 0.6, flipped(sleep_w), up=True,
           extra={(nm, "scale"): [(1 - u, v) for u, v in reversed(fold)] for nm in legs}, rest=toy)
    stubs(r)
    # the giraffe is in his mouth only when he carries it, and under his chin when he sleeps
    for cl in r.clips.values():
        if ("toy", "scale") not in cl.tracks:
            hold(cl, "toy", scale=(0.001, 0.001, 0.001))


# =====================================================================================================================
# The other direction, for the comparison only: their coats on the game's own cat and wolf bodies
# =====================================================================================================================

def _manual(rig, bone, name, x, y, z, w, h, d, u, v, mirror=False):
    cb = bone.cube(name, x, y, z, w, h, d, mirror=mirror)
    cb.u, cb.v = u, v
    return cb


def vanilla_cat():
    """AdultFelineModel's boxes (read from the game's source), 64 x 32, drawn at the cat's 0.8."""
    r = Rig("vanilla_cat", (64, 32))
    head = r.bone("head", (0, 15, -9))
    _manual(r, head, "main", -2.5, -2, -3, 5, 4, 5, 0, 0)
    _manual(r, head, "nose", -1.5, 0, -4, 3, 2, 2, 0, 24)
    _manual(r, head, "ear1", -2, -3, 0, 1, 1, 2, 0, 10)
    _manual(r, head, "ear2", 1, -3, 0, 1, 1, 2, 6, 10)
    body = r.bone("body", (0, 12, -10), (90, 0, 0))
    _manual(r, body, "vbody", -2, 3, -8, 4, 16, 6, 20, 0)
    t1 = r.bone("tail1", (0, 15, 8), (51.6, 0, 0))
    _manual(r, t1, "vtail1", -0.5, 0, 0, 1, 8, 1, 0, 15)
    t2 = r.bone("tail2", (0, 20, 14), (99, 0, 0))
    _manual(r, t2, "vtail2", -0.5, 0, 0, 1, 8, 1, 4, 15)
    for nm, sx in (("l", 1.1), ("r", -1.1)):
        _manual(r, r.bone("hind_" + nm, (sx, 18, 5)), "vhind_" + nm, -1, 0, 1, 2, 6, 2, 8, 13)
    for nm, sx in (("l", 1.2), ("r", -1.2)):
        _manual(r, r.bone("front_" + nm, (sx, 14.1, -5)), "vfront_" + nm, -1, 0, 0, 2, 10, 2, 40, 0)
    return r


def paint_vanilla_cat(r, P):
    front = ["BBBBB", "BEWEB", "WWWWB", "WWWWW"]
    for (tu, tv), (part, fname, i, j, p, n) in texels(r).items():
        x, y, z = p
        if part in ("ear1", "ear2", "vtail1", "vtail2"):
            black = True
        elif part.startswith(("vhind", "vfront")) or part == "nose":
            black = False
        elif part == "main":
            row, col_ = int(math.floor(y - 13)), int(math.floor(x + 2.5))
            black = front[row][col_] in "BE" if fname == "north" else (fname == "top" or row < 2 or (x > 0 and row < 3 and fname != "south"))
            if fname == "bottom":
                black = False
        else:
            down = (y - 14.0) * 8.0 / 6.0
            left = x > 0
            if z < -5:
                black = False
            elif z < -1:
                black = down < (4.0 if left else 2.0)
            elif z < 1:
                black = (not left) and down < 1.0
            elif z < 5:
                black = down < (3.0 if left else 4.0)
            else:
                black = down < 6.0
        col = fur(BLACK, 2.1, p, n, part, lock=4, depth=0.3, seed=1, fall=0.0) if black else fur(WHITE, 4.0, p, n, part, lock=5, depth=0.12, seed=1)
        if part == "main" and fname == "north" and front[int(math.floor(y - 13))][int(math.floor(x + 2.5))] == "E":
            col = hexc("#b5c85a")
        if part == "nose" and fname == "north" and j == 0 and i == 1:
            col = PINK.at(2.0)
        P.skin.set(tu, tv, col)
    return P.skin, P.glow


def vanilla_wolf():
    """AdultWolfModel's boxes, 64 x 32, drawn at 1.0."""
    r = Rig("vanilla_wolf", (64, 32))
    head = r.bone("head", (-1, 13.5, -7))
    _manual(r, head, "whead", -2, -3, -2, 6, 6, 4, 0, 0)
    _manual(r, head, "wear1", -2, -5, 0, 2, 2, 1, 16, 14)
    _manual(r, head, "wear2", 2, -5, 0, 2, 2, 1, 16, 14)
    _manual(r, head, "wnose", -0.5, 0, -5, 3, 3, 4, 0, 10)
    body = r.bone("body", (0, 14, 2), (90, 0, 0))
    _manual(r, body, "wbody", -3, -2, -3, 6, 9, 6, 18, 14)
    mane = r.bone("mane", (-1, 14, -3), (90, 0, 0))
    _manual(r, mane, "wmane", -3, -3, -3, 8, 6, 7, 21, 0)
    for nm, x, z, mir in (("rh", -2.5, 7, True), ("lh", 0.5, 7, False), ("rf", -2.5, -4, True), ("lf", 0.5, -4, False)):
        _manual(r, r.bone("leg_" + nm, (x, 16, z)), "wleg_" + nm, 0, 0, -1, 2, 8, 2, 0, 18, mirror=mir)
    tail = r.bone("tail", (-1, 12, 8), (36, 0, 0))
    _manual(r, tail, "wtail", 0, 0, -1, 2, 8, 2, 9, 18)
    return r


def paint_vanilla_wolf(r, P):
    front = ["FFWWFF", "FFWWFF", "FEWWEF", "FFWWFF", "WWWWWW", "WWWWWW"]
    for (tu, tv), (part, fname, i, j, p, n) in texels(r).items():
        x, y, z = p
        col = fur(CREAM, 4.1, p, n, part, lock=5, depth=0.14, seed=2, fall=0.2)
        if part in ("wear1", "wear2"):
            col = fur(FAWN, 2.6, p, n, part, lock=2, depth=0.4, seed=2, fall=0.0)
        elif part == "whead":
            row, col_ = int(math.floor(y - 10.5)), int(math.floor(x + 3))
            ch = front[max(0, min(5, row))][max(0, min(5, col_))]
            if fname == "north":
                col = hexc("#1e1512") if ch == "E" else fur(FAWN, 3.0, p, n, part, seed=2, fall=0.0) if ch == "F" else col
            elif fname != "bottom" and (row < 4 and not (fname == "top" and abs(x + 0.0) < 1.0)):
                col = fur(FAWN, 3.0, p, n, part, seed=2, fall=0.0)
        elif part == "wnose" and fname == "north" and j == 0:
            col = hexc("#241b1f")
        P.skin.set(tu, tv, col)
    return P.skin, P.glow


# =====================================================================================================================
# Building, previews
# =====================================================================================================================

DESIGNS = {"family_aya": (aya, paint_aya), "family_dubu": (dubu, paint_dubu)}


def rigs(only=None):
    return [(rid, d(), p) for rid, (d, p) in DESIGNS.items() if not only or rid in only]


def textures(rig, painter):
    P = R.Painter(rig, "family|" + rig.id)
    skin, glow = painter(rig, P)
    return skin, glow, P.blink()


def java(all_rigs):
    src = ["package dev.awaken.kin.client;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/hearth_family.py: do not edit by hand. Aya and Dubu's bodies as plain data, in the",
           " * hero rigs' format ({@link dev.awaken.mobs.client.anim.HeroRig#parse}); textures in textures/entity/family/.",
           " */", "public final class FamilyData {", "    private FamilyData() {}", "",
           "    /** Render scale (designed larger than it stands). */",
           "    public static final java.util.Map<String, Float> SCALE = java.util.Map.ofEntries("]
    src.append(",\n".join(f'            java.util.Map.entry("{rid}", {SCALE[rid]}F)' for rid, _, _ in all_rigs))
    src += ["    );", "", "    /** The life layer's temperament. */",
            "    public static final java.util.Map<String, String> TEMPER = java.util.Map.ofEntries("]
    src.append(",\n".join(f'            java.util.Map.entry("{rid}", "{TEMPER[rid]}")' for rid, _, _ in all_rigs))
    src += ["    );", "", "    /** Every rig's source text, in order. */", "    public static final List<String[]> SOURCES = List.<String[]>of("]
    for k, (rid, rig, _) in enumerate(all_rigs):
        for cl in rig.clips.values():
            for (bone, _t), _keys in cl.tracks.items():
                if bone not in rig.by_name:
                    raise SystemExit(f"{rid}/{cl.name}: no bone {bone}")
        src += [f"            // {rid}", "            new String[] {", '                """']
        src += ["                " + ln for ln in rig.java_lines()]
        src += ['                """', "            }" + ("," if k < len(all_rigs) - 1 else "")]
    src += ["    );", "}"]
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")
    return JAVA_OUT


def build(out):
    all_rigs = rigs()
    for rid, rig, painter in all_rigs:
        skin, glow, blink = textures(rig, painter)
        name = rid[len("family_"):]
        out.entity("family/" + name, skin)
        if blink is not None:
            out.entity("family/" + name + "_blink", blink[0])
    path = java(all_rigs)
    print(f"hearth_family: {len(all_rigs)} rigs -> {os.path.relpath(path, ROOT)}")


def turnaround(outdir, only=None, px=22):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    paths = []
    for rid, rig, painter in rigs(only):
        skin, glow, _ = textures(rig, painter)
        tex = skin.image()
        who = "her" if rid == "family_aya" else "his"
        views = [("front", 0.01, 6), (f"front, from {who} left", 35, 14), (f"{who} left side", 90, 6), (f"back, from {who} left", 145, 14),
                 (f"{who} right side", -90, 6), (f"front, from {who} right", -35, 14), ("from above", 20, 62)]
        if os.environ.get("VIEWS"):
            views = [views[int(k)] for k in os.environ["VIEWS"].split(",")]
        pose = rig.tree(rig.sample([("idle", 0.0, 1.0)]))
        ims = [draw([(pose, tex)], yaw=yaw, pitch=pitch, px=px) for _, yaw, pitch in views]
        W = sum(im.width for im in ims) + 20 * (len(ims) + 1)
        Hh = max(im.height for im in ims) + 60
        sheet = Image.new("RGBA", (W, Hh), (112, 128, 140, 255))
        d = ImageDraw.Draw(sheet)
        x = 20
        for (label, _, _), im in zip(views, ims):
            sheet.alpha_composite(im, (x, Hh - 30 - im.height))
            d.text((x, Hh - 22), label, fill=(240, 244, 250, 255))
            x += im.width + 20
        path = os.path.join(outdir, rid + "_turn.png")
        sheet.save(path)
        tex.resize((tex.width * 6, tex.height * 6), Image.NEAREST).save(os.path.join(outdir, rid + "_tex.png"))
        paths.append(path)
    return paths



def _scene(parts):
    """Several posed bodies in one picture: [(tree, texture image, scale, (x, z) in blocks, yaw degrees)]. Each is
    stood on the ground at its place; they are drawn far to near."""
    return parts


def _placed(tree, scale, at, yaw):
    """A body's tree scaled as the game scales it, feet on the ground (y = 24), turned and moved (blocks -> pixels)."""
    inner = {"pivot": (0, -24, 0), "rot": (0, 0, 0), "scale": (1, 1, 1), "boxes": [], "children": [tree]}
    return {"pivot": (at[0] * 16, 24, at[1] * 16), "rot": (0, math.radians(yaw), 0), "scale": (scale, scale, scale), "boxes": [],
            "children": [inner]}


def strip_frames(rig, tex, clip, n, yaw=50, pitch=12, px=9, light=None, t0=0.0, t1=None):
    cl = rig.clips[clip]
    t1 = cl.length if t1 is None else t1
    ts = [t0 + (t1 - t0) * k / (n if cl.loop else max(1, n - 1)) for k in range(n)]
    trees = [rig.tree(rig.sample([(clip, t, 1.0)])) for t in ts]
    return trees, ts


def film(outdir, rid, rig, tex, clip, fps=16, px=10, yaw=50, pitch=12, size=(360, 300), bg=(118, 134, 146, 255), seconds=None):
    """One clip as an animated picture, drawn on the body exactly as the keys play it."""
    from PIL import Image, ImageDraw
    cl = rig.clips[clip]
    length = seconds or cl.length
    n = max(2, int(round(length * fps)))
    trees = [rig.tree(rig.sample([(clip, length * k / n, 1.0)])) for k in range(n)]
    rest = rig.tree()
    b = R.bounds_of(trees + [rest], yaw, pitch)
    scale = min((size[0] - 30) / max(1e-6, b[1] - b[0]), (size[1] - 40) / max(1e-6, b[3] - b[2]), px)
    frames = []
    for t in trees:
        im = draw([(t, tex)], yaw=yaw, pitch=pitch, px=scale, bounds=b,
                  origin=(size[0], size[1], (size[0] - (b[1] - b[0]) * scale) / 2 - b[0] * scale, size[1] - 22 - b[3] * scale))
        fr = Image.new("RGBA", size, bg)
        d = ImageDraw.Draw(fr)
        gy = size[1] - 22
        d.rectangle((0, gy, size[0], size[1]), fill=(96, 112, 100, 255))
        fr.alpha_composite(im)
        frames.append(fr.convert("RGB"))
    path = os.path.join(outdir, f"{rid}_{clip}.webp")
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0, quality=82, method=4)
    return path


def sheet(outdir, only=None, clips=None, per=6, cell=(250, 210), yaw=50, pitch=12):
    """Every clip as a strip of moments across it."""
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    paths = []
    for rid, rig, painter in rigs(only):
        skin, glow, blink = textures(rig, painter)
        tex = skin.image()
        shut = blink[0].image() if blink else tex
        names = [nm for nm in rig.clips if nm not in ("hurt", "death") and (not clips or nm in clips)]
        rows = []
        for nm in names:
            trees, ts = strip_frames(rig, tex, nm, per)
            b = R.bounds_of(trees + [rig.tree()], yaw, pitch)
            scale = min((cell[0] - 20) / max(1e-6, b[1] - b[0]), (cell[1] - 30) / max(1e-6, b[3] - b[2]), 9)
            row = Image.new("RGBA", (cell[0] * per, cell[1] + 18), (30, 34, 42, 255))
            d = ImageDraw.Draw(row)
            for k, t in enumerate(trees):
                im = draw([(t, shut if nm == "sleep" else tex)], yaw=yaw, pitch=pitch, px=scale, bounds=b,
                          origin=(cell[0], cell[1], (cell[0] - (b[1] - b[0]) * scale) / 2 - b[0] * scale, cell[1] - 14 - b[3] * scale))
                bg = Image.new("RGBA", cell, (118, 134, 146, 255))
                ImageDraw.Draw(bg).rectangle((0, cell[1] - 14, cell[0], cell[1]), fill=(96, 112, 100, 255))
                bg.alpha_composite(im)
                row.paste(bg, (k * cell[0], 0))
            d.text((4, cell[1] + 3), nm, fill=(230, 234, 242, 255))
            rows.append(row)
        out = Image.new("RGBA", (rows[0].width, sum(r_.height for r_ in rows)), (30, 34, 42, 255))
        y = 0
        for r_ in rows:
            out.paste(r_, (0, y))
            y += r_.height
        path = os.path.join(outdir, rid + "_clips.png")
        out.save(path)
        paths.append(path)
    return paths


# =====================================================================================================================
# The approval page's pictures (docs/family/PLAN.md): python3 tools/art/specs/hearth_family.py page DIR
# =====================================================================================================================

SKY = (124, 146, 168, 255)
GRASS = (104, 132, 92, 255)


def _backdrop(size, horizon):
    from PIL import Image, ImageDraw
    im = Image.new("RGBA", size, SKY)
    d = ImageDraw.Draw(im)
    for y in range(horizon):
        t = y / max(1, horizon)
        d.line((0, y, size[0], y), fill=(int(104 + 34 * t), int(128 + 30 * t), int(156 + 22 * t), 255))
    for y in range(horizon, size[1]):
        t = (y - horizon) / max(1, size[1] - horizon)
        d.line((0, y, size[0], y), fill=(int(92 + 18 * t), int(118 + 22 * t), int(80 + 14 * t), 255))
    return im


def _shadow(img, cx, cy, rx, ry, a=70):
    from PIL import Image, ImageDraw, ImageFilter
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=(20, 30, 24, a))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(max(1, ry * 0.6))))


def _player(walk=None):
    """The player's model for scale (fauna_rig's), legs and arms swung when walking (phase 0..1)."""
    t = R.player_tree()
    if walk is not None:
        a = 0.55 * math.sin(walk * math.tau)
        kids = t["children"]
        kids[2]["rot"], kids[3]["rot"] = (a, 0, 0), (-a, 0, 0)
        kids[4]["rot"], kids[5]["rot"] = (-a, 0, 0), (a, 0, 0)
    return t


def scene(members, size, px, yaw=32, pitch=10, ground=0.8, shadows=True):
    """A picture of bodies standing on one ground: members = [(tree, texture, scale, (x, z) blocks, turn degrees)]."""
    from PIL import Image
    parts = [(_placed(tree, sc, at, turn), tex) for tree, tex, sc, at, turn in members]
    b = R.bounds_of([t for t, _ in parts], yaw, pitch)
    W, H = size
    px = min(px, (W - 36) / (b[1] - b[0]), (H * ground - 24) / (b[3] - b[2]))
    offx = (W - (b[1] - b[0]) * px) / 2 - b[0] * px
    # the ground line: where y = 24 at the scene's middle depth projects
    ya, pa = math.radians(yaw), math.radians(pitch)
    cam = R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0))
    feet = [R._mv(cam, (at[0] * 16, 24, at[1] * 16)) for _, _, _, at, _ in members]
    offy = H * ground - max(max(f[1] for f in feet), b[3] - 1.0) * px
    horizon = int(offy + min(f[1] for f in feet) * px - 70)
    img = _backdrop(size, max(20, horizon))
    if shadows:
        for (tree, tex, sc, at, turn), f in zip(members, feet):
            r_ = 7.0 * sc * px
            _shadow(img, f[0] * px + offx, f[1] * px + offy, r_ * 1.5, r_ * 0.5)
    img.alpha_composite(draw(parts, yaw=yaw, pitch=pitch, px=px, bounds=b, origin=(W, H, offx, offy)))
    return img


def label(img, text, xy, size=15, fill=(244, 247, 252, 255)):
    from PIL import ImageDraw, ImageFont
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", size)
    except Exception:
        font = ImageFont.load_default()
    ImageDraw.Draw(img).text(xy, text, fill=fill, font=font)


def _made(only=None):
    out = {}
    for rid, rig, painter in rigs(only):
        skin, glow, blink = textures(rig, painter)
        out[rid] = (rig, skin.image(), blink[0].image() if blink else skin.image())
    return out


def _other(make, paint):
    rig = make()
    P = R.Painter(rig, "family|" + rig.id)
    skin, _ = paint(rig, P)
    return rig, skin.image()


def page_art(outdir, films=True):
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    M = _made()
    aya_r, aya_t, aya_shut = M["family_aya"]
    dub_r, dub_t, dub_shut = M["family_dubu"]
    steve = R.steve()
    made = []

    def pose(rig, clip, t):
        return rig.tree(rig.sample([(clip, t, 1.0)]))

    # ---- 1. each of them from every side
    for rid, (rig, tex, _) in M.items():
        who = "her" if rid == "family_aya" else "his"
        # the flat views are nearly level, so a sliver of the lit back does not read as a line along it
        views = [("from the front", 0.01, 3), (f"front, {who} left", 38, 16), (f"{who} left side", 90, 3), (f"back, {who} left", 142, 18),
                 ("from behind", 180, 3), (f"{who} right side", -90, 3), (f"front, {who} right", -38, 16), ("as you look down at " + ("her" if who == "her" else "him"), 24, 58)]
        st = pose(rig, "idle", 0.0)
        cell = (330, 300)
        sheet_ = Image.new("RGBA", (cell[0] * 4, cell[1] * 2), SKY)
        for k, (text, yaw, pitch) in enumerate(views):
            b = R.bounds_of([st], yaw, pitch)
            pxs = min((cell[0] - 40) / (b[1] - b[0]), (cell[1] - 70) / (b[3] - b[2]), 13)
            bg = _backdrop(cell, cell[1] - 46) if pitch < 40 else Image.new("RGBA", cell, GRASS)
            offx, offy = (cell[0] - (b[1] - b[0]) * pxs) / 2 - b[0] * pxs, cell[1] - 34 - b[3] * pxs
            if pitch < 40:
                _shadow(bg, cell[0] / 2, cell[1] - 36, (b[1] - b[0]) * pxs * 0.42, 9)
            bg.alpha_composite(draw([(st, tex)], yaw=yaw, pitch=pitch, px=pxs, bounds=b, origin=(cell[0], cell[1], offx, offy)))
            label(bg, text, (10, 8), 14)
            sheet_.paste(bg, ((k % 4) * cell[0], (k // 4) * cell[1]))
        path = os.path.join(outdir, rid + "_turn.png")
        sheet_.convert("RGB").save(path)
        made.append(path)

    # ---- 2. their size: beside the player, and beside the game's own cat and wolf
    cat_r, cat_t = _other(vanilla_cat, paint_vanilla_cat)
    wolf_r, wolf_t = _other(vanilla_wolf, paint_vanilla_wolf)
    sc_a, sc_d = SCALE["family_aya"], SCALE["family_dubu"]
    img = scene([(_player(), steve, 0.9375, (0.0, 0.0), 0),
                 (pose(aya_r, "sit", 0.2), aya_t, sc_a, (-0.85, -0.25), -14),
                 (pose(dub_r, "idle", 0.1), dub_t, sc_d, (0.95, -0.15), 20)], (760, 620), 15.5, yaw=22, pitch=9, ground=0.9)
    path = os.path.join(outdir, "scale.png")
    img.convert("RGB").save(path)
    made.append(path)

    # ---- 3. the two directions: the game's own bodies wearing their coats, and bodies of their own
    for name, a_member, b_member in (
            ("direction_aya", (cat_r.tree(), cat_t, 0.8, 12.0), (pose(aya_r, "idle", 0.0), aya_t, sc_a, 2.5)),
            ("direction_dubu", (wolf_r.tree(), wolf_t, 1.0, 8.5), (pose(dub_r, "idle", 0.0), dub_t, sc_d, 6.5))):
        both = []
        for tree, tex, sc, _top in (a_member, b_member):
            both.append(scene([(tree, tex, sc, (0, 0), 0)], (430, 360), 17, yaw=38, pitch=13, ground=0.86))
        out = Image.new("RGBA", (860, 360))
        out.paste(both[0], (0, 0))
        out.paste(both[1], (430, 0))
        label(out, "A  The game's own body, their coat", (12, 10), 16)
        label(out, "B  A body of their own", (442, 10), 16)
        path = os.path.join(outdir, name + ".png")
        out.convert("RGB").save(path)
        made.append(path)
        faces = []
        for tree, tex, sc, top in (a_member, b_member):
            b = R.bounds_of([tree], 0.01, 4)
            # the same real size on screen: pixels per block times the body's own scale
            pxs = 30 * sc
            W_, H_ = 430, 250
            bg = Image.new("RGBA", (W_, H_), SKY)
            bg.alpha_composite(draw([(tree, tex)], yaw=0.01, pitch=4, px=pxs, bounds=b,
                                    origin=(W_, H_, W_ / 2, 44 - top * pxs)))
            faces.append(bg)
        out = Image.new("RGBA", (860, 250))
        out.paste(faces[0], (0, 0))
        out.paste(faces[1], (430, 0))
        label(out, "A  the face, at the same real size", (12, 10), 16)
        label(out, "B  the face, at the same real size", (442, 10), 16)
        path = os.path.join(outdir, name + "_face.png")
        out.convert("RGB").save(path)
        made.append(path)

    # ---- 4. moments: stills of the poses that came from the photos
    stills = [("aya_loaf", aya_r, aya_t, sc_a, "loaf", 0.4, 40, 16), ("aya_sit", aya_r, aya_t, sc_a, "sit", 0.3, 34, 12),
              ("aya_roll", aya_r, aya_t, sc_a, "roll", 0.9, 64, 24), ("aya_sleep", aya_r, aya_shut, sc_a, "sleep", 0.5, 150, 34),
              ("aya_hello", aya_r, aya_t, sc_a, "nudge", 0.9, 40, 14), ("aya_walk", aya_r, aya_t, sc_a, "move", 0.1, 60, 12),
              ("aya_stretch", aya_r, aya_t, sc_a, "stretch", 1.2, 62, 14), ("aya_wary", aya_r, aya_t, sc_a, "hide", 0.3, 40, 16),
              ("aya_groom", aya_r, aya_t, sc_a, "groom", 0.6, 34, 12), ("aya_swat", aya_r, aya_t, sc_a, "swat", 0.56, 34, 12),
              ("dubu_scratch", dub_r, dub_t, sc_d, "scratch", 0.3, 40, 12), ("dubu_sniff", dub_r, dub_t, sc_d, "sniff", 0.5, 52, 12),
              ("dubu_lie", dub_r, dub_t, sc_d, "lie", 0.4, 38, 16), ("dubu_sit", dub_r, dub_t, sc_d, "sit", 1.9, 34, 12),
              ("dubu_sleep", dub_r, dub_shut, sc_d, "sleep", 0.5, -56, 34), ("dubu_carry", dub_r, dub_t, sc_d, "carry", 0.1, 52, 12),
              ("dubu_hello", dub_r, dub_t, sc_d, "nudge", 0.55, 46, 14), ("dubu_run", dub_r, dub_t, sc_d, "run", 0.1, 60, 12),
              ("dubu_walk", dub_r, dub_t, sc_d, "move", 0.1, 60, 12), ("dubu_wary", dub_r, dub_t, sc_d, "hide", 0.3, 40, 16)]
    for name, rig, tex, sc, clip, t, yaw, pitch in stills:
        img = scene([(pose(rig, clip, t), tex, sc, (0, 0), 0)], (430, 320), 26, yaw=yaw, pitch=pitch, ground=0.86)
        path = os.path.join(outdir, name + ".png")
        img.convert("RGB").save(path)
        made.append(path)

    # ---- 5. together by the fire's light: her loaf, him asleep with his giraffe
    img = scene([(pose(aya_r, "loaf", 0.4), aya_t, sc_a, (-0.62, 0.2), -22),
                 (pose(dub_r, "lie", 0.4), dub_t, sc_d, (0.5, -0.1), 26)], (760, 380), 30, yaw=24, pitch=18, ground=0.86)
    path = os.path.join(outdir, "together.png")
    img.convert("RGB").save(path)
    made.append(path)

    if not films:
        return made

    # ---- 6. moving pictures, each clip as its keys play it
    def movie(name, members_at, seconds, fps=14, size=(430, 320), px=28, yaw=50, pitch=14, ground=0.86):
        n = max(2, int(round(seconds * fps)))
        frames = []
        # frame every member the same way through the clip: bounds from all frames
        all_parts = [[(_placed(tree, sc, at, turn), tex) for tree, tex, sc, at, turn in members_at(seconds * k / n)] for k in range(n)]
        b = R.bounds_of([t for parts in all_parts for t, _ in parts], yaw, pitch)
        W, H = size
        scale_ = min(px, (W - 30) / (b[1] - b[0]), (H * ground - 30) / (b[3] - b[2]))
        offx = (W - (b[1] - b[0]) * scale_) / 2 - b[0] * scale_
        ya, pa = math.radians(yaw), math.radians(pitch)
        cam = R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0))
        first = members_at(0.0)
        feet = [R._mv(cam, (at[0] * 16, 24, at[1] * 16)) for _, _, _, at, _ in first]
        offy = H * ground - max(f[1] for f in feet) * scale_
        horizon = int(offy + min(f[1] for f in feet) * scale_ - 70)
        for parts in all_parts:
            fr = _backdrop(size, max(20, horizon))
            for (tree, tex, sc, at, turn), f in zip(first, feet):
                r_ = 7.0 * sc * scale_
                _shadow(fr, f[0] * scale_ + offx, f[1] * scale_ + offy, r_ * 1.5, r_ * 0.5)
            fr.alpha_composite(draw(parts, yaw=yaw, pitch=pitch, px=scale_, bounds=b, origin=(W, H, offx, offy)))
            frames.append(fr.convert("RGB"))
        path_ = os.path.join(outdir, name + ".webp")
        frames[0].save(path_, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0, quality=80, method=4)
        made.append(path_)

    def solo(rig, tex, sc, clip):
        return lambda t: [(pose(rig, clip, t), tex, sc, (0, 0), 0)]

    for name, rig, tex, sc, clip, yaw in (
            ("aya_walk", aya_r, aya_t, sc_a, "move", 62), ("aya_run", aya_r, aya_t, sc_a, "run", 62),
            ("aya_sit", aya_r, aya_t, sc_a, "sit", 34), ("aya_loaf", aya_r, aya_t, sc_a, "loaf", 40),
            ("aya_sleep", aya_r, aya_shut, sc_a, "sleep", 150), ("aya_roll", aya_r, aya_t, sc_a, "roll", 64),
            ("aya_hello", aya_r, aya_t, sc_a, "nudge", 40), ("aya_stretch", aya_r, aya_t, sc_a, "stretch", 62),
            ("aya_idle", aya_r, aya_t, sc_a, "idle", 40),
            ("dubu_walk", dub_r, dub_t, sc_d, "move", 62), ("dubu_run", dub_r, dub_t, sc_d, "run", 62),
            ("dubu_carry", dub_r, dub_t, sc_d, "carry", 52), ("dubu_sit", dub_r, dub_t, sc_d, "sit", 34),
            ("dubu_lie", dub_r, dub_t, sc_d, "lie", 38), ("dubu_sleep", dub_r, dub_shut, sc_d, "sleep", 58),
            ("dubu_hello", dub_r, dub_t, sc_d, "nudge", 46), ("dubu_idle", dub_r, dub_t, sc_d, "idle", 40)):
        cl = rig.clips[clip]
        loops = 2 if cl.loop and cl.length < 1.0 else 1
        movie(name, solo(rig, tex, sc, clip), cl.length * loops, yaw=yaw)

    # out with you: the player walking, Dubu trotting a little ahead, Aya at your heel with her tail up
    def out_with_you(t):
        return [(_player(walk=t / 1.04), steve, 0.9375, (0.0, 0.0), 0),
                (pose(dub_r, "move", t), dub_t, sc_d, (0.3, -1.5), 6),
                (pose(aya_r, "move", t * 0.84 / 1.04), aya_t, sc_a, (1.05, 0.35), -4)]
    movie("following", out_with_you, 2.08, fps=14, size=(760, 560), px=15, yaw=58, pitch=11, ground=0.9)
    return made


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "turn":
        for p in turnaround(sys.argv[2], sys.argv[3:] or None):
            print(p)
    if len(sys.argv) > 2 and sys.argv[1] == "sheet":
        cl = os.environ.get("CLIPS")
        for p in sheet(sys.argv[2], sys.argv[3:] or None, clips=cl.split(",") if cl else None):
            print(p)
    if len(sys.argv) > 2 and sys.argv[1] == "page":
        for p in page_art(sys.argv[2], films=os.environ.get("FILMS", "1") != "0"):
            print(p)
