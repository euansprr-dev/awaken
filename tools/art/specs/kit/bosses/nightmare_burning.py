"""The Pyre (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Flier, the fear of fire. The Pyre Mare, picked
2026-10-29: "Fire that follows you."

A horse as tall as a cart, black as the inside of a chimney, with a sheen on it like oil, and everything that would be
hair on it is fire: its mane runs in flames down the crest of its neck, its tail is a long streamer of fire, its fetlocks
are feathered with flame, and a flame stands up between its ears. Its eyes are white-hot, its nostrils glow and there is
fire behind its teeth. A burnt bridle still hangs on its head, the iron rings black. It never touches the ground: it
stands and gallops on the air a hand's breadth over the floor, and its hooves glow underneath like coals.

Moves (SPEC §35.11): the Strike and the Trample (LIGHT, a string: it rears and strikes down at you with a forehoof, then
rears higher and brings both forehooves down on you); the Charge (HEAVY, its gap closer: it paws the air with its head
low and its ears pinned, holds, and gallops at you); Cinders (it tosses its head four times, flinging fire from its mane);
Pyre (it rears and stamps, and its fire roars up all over it); Firefall (into the second phase: it rears right up and
shakes its mane, and the fire rains down); Flare (it gathers with its head down between its knees, its fire drawn in,
and flings its head up and flares); for whoever is behind it, the Buck (it drops its head and kicks out behind with
both hind legs); and Wildfire (the signature: it rears up as high as it can, screaming, its forelegs pawing the air and
its fire roaring higher and higher, and crashes down on both forehooves). Its intro: it lies burnt out, its fire gone
to embers; a flame runs back along its mane; it lifts its head, throws its forelegs out and heaves itself up, shakes
its head and rears, screaming. Broken, it goes down on its knees and lies with its fire low. Its death: it rears and
screams, comes down and staggers, goes down on its knees and rolls onto its side, and its fire goes out.
"""
import math

from kit import core, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "nightmare_burning"
SCALE = 2.0  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Pyre Mare (docs/bosses/ROLLOUT.md, A). 44 px from its chest to its rump (2.75 blocks), its withers 53 px up (3.3
# blocks) and its ears 4.6 blocks; its hooves hang 7 px (under half a block) over the floor. Value story: its fire
# brightest, white at the roots of its flames and its eyes, going to orange and red at their tips; then the oily sheen
# along its topline, the crest of its neck and its rump; its coat black, its legs and belly the darkest; the bridle's
# iron a dull grey.
HOVER = 42.0
FLOAT_Z = 2.0
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT = (True, True, False, False)
LEG = {True: ((13, 6, 7), (11, 5, 5)), False: ((14, 7, 9), (11, 5, 5))}  # (length, width, depth) of the upper and lower
HIND_ANGLE = 14.0  # its hind legs: the gaskin back from the stifle, the cannon straight down from the hock
FOLD = (1, 1, -1, -1)  # a fold turns the lower leg so the hoof comes up behind (front) or forward (hind)
NMANE = 6
FLAMES = [f"mane{i}" for i in range(NMANE)] + ["forelock"] + [f"fire{k}" for k in range(4)] + ["tail1"]
SIDES_ = (("l", 1), ("r", -1))
ONE = (1.0, 1.0, 1.0)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, tex=(256, 256))
    base = r.bone("base", (0, 24, 0))
    fl = _bone(base, "float", 0, HOVER, FLOAT_Z)
    bar = _bone(fl, "barrel", 0, 0, 0)
    _box(bar, "barrel", 0, -9, 0, 18, 18, 22)
    _box(bar, "back", 0, 9, 0, 14, 2, 20)
    _box(bar, "belly", 0, -11, 0, 14, 2, 18)
    ch = _bone(bar, "chest", 0, 0, -11)
    _box(ch, "chest", 0, -9, -5, 18, 19, 10)
    _box(ch, "breast", 0, -8, -11, 14, 14, 2)
    _box(ch, "withers", 0, 10, -3, 12, 3, 8)
    hp = _bone(bar, "hips", 0, 0, 11)
    _box(hp, "rump", 0, -9, 5, 18, 19, 10)
    _box(hp, "croup", 0, 10, 4, 14, 2, 8)
    _box(hp, "dock", 0, 3, 10.5, 6, 6, 1)
    for nm, sg in SIDES_:
        _box(ch, f"shoulder_{nm}", sg * 9.5, -7, -5, 1, 14, 8)
        _box(hp, f"haunch_{nm}", sg * 9.5, -8, 5, 1, 15, 9)
    # the neck, up and forward from the withers, and the head on it, nose down
    n0 = _bone(ch, "neck0", 0, 6, -5, (40, 0, 0))
    _box(n0, "neck0", 0, 0, 0, 11, 15, 14)
    n1 = n0.bone("neck1", (0, -15, 0), (10, 0, 0))
    _box(n1, "neck1", 0, 0, 0.5, 10, 12, 12)
    hd = n1.bone("head", (0, -12, -1), (0, 0, 0))
    _box(hd, "skull", 0, -6, -4, 11, 11, 10)
    _box(hd, "brow", 0, 5, -5, 9, 1, 8)
    _box(hd, "face", 0, -5, -13, 9, 9, 8)
    _box(hd, "muzzle", 0, -4, -20.5, 8, 7, 7)
    for nm, sg in SIDES_:
        _box(hd, f"eye_{nm}", sg * 6, 0, -6, 1, 3, 3)
        _box(hd, f"cheek_{nm}", sg * 5, -5, -7, 1, 9, 1)  # the bridle's cheek straps
        _box(hd, f"ring_{nm}", sg * 5, -4, -17, 1, 3, 3)  # its iron rings
        e = _bone(hd, f"ear_{nm}", sg * 3, 5, -1, (-10, 0, -sg * 8))
        _box(e, f"ear_{nm}", 0, 0, 0, 2, 6, 3)
    _box(hd, "noseband", 0, -2, -16.5, 10, 2, 1)
    jw = _bone(hd, "jaw", 0, -4, -4)
    _box(jw, "jaw", 0, -3, -8.5, 7, 3, 15)
    _box(jw, "teeth", 0, 0, -14.5, 6, 1, 3)
    fk = _bone(hd, "forelock", 0, 5, -3, (-20, 0, 0))
    _box(fk, "forelock", 0, 0, 0, 3, 10, 3)
    # its mane: flames standing up along the crest of its neck and streaming back
    at = [("neck0", 2, 7), ("neck0", 7, 7), ("neck0", 12, 7), ("neck1", 1, 6), ("neck1", 5, 6), ("neck1", 9, 6)]
    for i, (on, u, z) in enumerate(at):
        m = _bone(n0 if on == "neck0" else n1, f"mane{i}", 0, u, z, (-50, 0, 0))
        _box(m, f"mane{i}", 0, 0, 0, 3, 14 - i % 2 * 3 - (2 if i == NMANE - 1 else 0), 7)
        _box(m, f"manec{i}", 0, 0, -0.5, 4, 6, 4)
    # its legs: the forearm and cannon in front, the gaskin and cannon behind; its hooves, its fetlocks feathered with fire
    for k in range(4):
        front = FRONT[k]
        par = ch if front else hp
        (la, wa, da), (lb, wb, db) = LEG[front]
        a = _bone(par, f"leg{k}a", SIDE[k] * 6, -5 if front else -4, -4 if front else 6, (0, 0, 0) if front else (HIND_ANGLE, 0, 0))
        _box(a, f"leg{k}a", 0, -la, 0, wa, la, da)
        _box(a, f"muscle{k}", 0, -6, -0.5 if front else 0.5, wa + 1, 6, da + 1)
        b = a.bone(f"leg{k}b", (0, la, 0), (0, 0, 0) if front else (-HIND_ANGLE, 0, 0))
        _box(b, f"leg{k}b", 0, -lb, 0, wb, lb, db)
        _box(b, f"knee{k}", 0, -2, 0 if front else 0.5, wb + 1, 3, db + 1)
        h = b.bone(f"hoof{k}", (0, lb, 0))
        _box(h, f"pastern{k}", 0, -3, 0, 4, 3, 4)
        _box(h, f"hoof{k}", 0, -6, -0.5, 6, 3, 7)
        f = _bone(h, f"fire{k}", 0, -3, 3, (-30, 0, 0))
        _box(f, f"fire{k}", 0, 0, 0, 6, 9, 3)
    # its tail: a short dock, then a long streamer of fire hanging down behind
    t0 = _bone(hp, "tail0", 0, 5, 11, (-40, 0, 0))
    _box(t0, "tail0", 0, -2, 3, 4, 4, 6)
    _box(t0, "tailf0", 0, -3, 4.5, 2, 7, 7)
    t1 = t0.bone("tail1", (0, 0, 6), (-20, 0, 0))
    _box(t1, "tailf1", 0, -6, 5.5, 4, 12, 11)
    _box(t1, "tailg1", 0, -3, 5, 6, 6, 9)
    t2 = t1.bone("tail2", (0, 0, 11), (-15, 0, 0))
    _box(t2, "tailf2", 0, -5.5, 6, 3, 11, 12)
    _box(t2, "tailg2", 0, -2.5, 4.5, 5, 5, 8)
    r.look = [("head", 0.45, 0.3), ("neck1", 0.25, 0.2), ("neck0", 0.15, 0.1)]
    r.shadow = 2.4
    r.size = 1.0
    # secondary parts (SPEC §35.10): its tail and the tip of its mane trail
    r.springs = [("tail1", 0.5, 1.8, 0.14), ("tail2", 0.6, 1.8, 0.12), ("forelock", 0.3, 2.4, 0.14)]
    r.guard = [f"hoof{k}" for k in range(4)] + ["head", "jaw", "tail2", "barrel", "chest", "hips"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

COAT = Ramp("#030305", "#07070b", "#0c0c13", "#13131c", "#1b1b27", "#252534", "#303043", "#3d3d55", "#4d4d6b", "#606084", "#7a7aa2")
HORN = Ramp("#0b0a0a", "#161413", "#221f1d", "#302c29", "#403b37")
IRON = Ramp("#1a1a1c", "#2a2a2e", "#3c3c42", "#52525a", "#6a6a74")
LEATHER = Ramp("#120c08", "#1d140e", "#2a1d14", "#38271b", "#47331f")
FIRE = ("#fffbe8", "#ffe9a0", "#ffc85a", "#ff9a2e", "#f26518", "#c23d0c", "#7a2006")
TOPLINE = ("back", "withers", "croup", "neck0", "neck1", "skull", "brow", "face", "muzzle", "rump", "chest", "barrel")


def _n(i, j, seed):
    return (math.sin(i * 2.1 + seed) * math.sin(j * 1.7 + seed * 1.3) + 0.6 * math.sin((i + 3 * j) * 0.9 + seed * 2.3)) / 1.6


def _coat_pattern(name, seed, low):
    """The coat: the lie of the hair in faint streaks, an oily sheen as a band high on its sides and a hard glint along
    the top of its back, neck and face; its legs and belly duller."""
    def pat(f, i, j):
        v = 0.18 * math.sin(i * 0.7 + seed + 0.5 * math.sin(j * 0.6)) + (0.12 if (i * 3 + j * 5 + seed) % 7 == 0 else 0.0)
        if low:
            return v - 0.6
        if f.name in SIDES and f.h > 3:
            t = j / max(1, f.h - 1)
            v += 1.5 * math.exp(-((t - 0.2) / 0.13) ** 2) * (0.7 + 0.3 * math.sin(i * 0.35 + seed))
        elif f.name == "top" and name in TOPLINE:
            mid = abs(i - (f.w - 1) / 2) / max(1.0, f.w / 2)
            v += 1.8 * max(0.0, 1.0 - mid * 1.6) * (0.8 + 0.2 * math.sin(j * 0.5 + seed))
        return v
    return pat


def _fire(f, gl, seed, kind="up", hot=0.0, reach=1.0):
    """Fire on a face, made of tongues: hot (white to yellow) at its root, orange through its body, red at its tips, and
    each tongue's tip ragged (see-through). kind "up" has its root at the bottom of a side face and its tips at the top;
    "band" is a ribbon of fire, hot down its middle and ragged at both edges (the tail, the tops of flames)."""
    for i, j in f.each():
        if kind == "up":
            s = 1.0 - j / max(1, f.h - 1)  # 0 at its root, 1 at its top
        else:
            s = abs(j - (f.h - 1) / 2) / max(1.0, (f.h - 1) / 2)
        lim = reach * (0.62 + 0.26 * math.sin(i * 2.4 + seed) + 0.12 * math.sin(i * 5.1 + seed * 0.7))
        if s > lim:
            f.set(i, j, hexc(FIRE[0], 0))
            gl.set(i, j, hexc(FIRE[0], 0))
            continue
        q = s / max(0.05, lim)
        k = 1 + q * 5.2 - hot * 2.5 - (0.9 if (i + seed) % 3 == 0 else 0.0) + 0.5 * _n(i, j, seed)
        k = max(0, min(len(FIRE) - 1, int(round(k))))
        f.set(i, j, hexc(FIRE[k], 255))
        gl.set(i, j, hexc(FIRE[k], int(255 - 110 * q)))


def paint(r, P_):
    """A black coat with an oily blue sheen high on its sides and a glint along its back, the crest of its neck and its
    face; its belly and legs duller; everything that would be hair, fire: white-hot at the roots, orange, red at the
    ragged tips; its eyes white-hot, its nostrils and the inside of its mouth glowing, its hooves dark horn glowing
    underneath like coals and at the rims; a burnt leather bridle with blackened iron rings."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    coat = ["barrel", "back", "belly", "chest", "breast", "withers", "rump", "croup", "dock", "neck0", "neck1", "skull", "brow", "face", "muzzle", "jaw"] \
        + of("shoulder_", "haunch_", "ear_", "muscle", "leg", "knee", "pastern", "tail0")
    for k, c in enumerate(coat):
        low = c.startswith(("leg", "knee", "pastern", "belly", "muscle"))
        P_.paint(c, COAT, 2.4 if low else 3.0, _coat_pattern(c, k * 3, low), grad=1.6, rim=1.4, ao=1.0, noise=0.4, nscale=3)
    # its eyes: white-hot, an orange rim, a dark socket round them
    with P_.eyes():
        for nm, fname in (("l", "east"), ("r", "west")):
            f, gl = P_.face(f"eye_{nm}", fname), P_.face(f"eye_{nm}", fname, glow=True)
            for i, j in f.each():
                col = FIRE[0] if j == 1 and i in (1, 2) else FIRE[1] if j == 1 else FIRE[3]
                f.set(i, j, hexc(col, 255))
                gl.set(i, j, hexc(col, 255))
    for c in ("skull", "face"):
        for fname in ("east", "west"):
            f = P_.face(c, fname)
            if c == "skull":
                for i, j in f.each():
                    if 3 <= j <= 7 and f.w - 9 <= i <= f.w - 3 if fname == "east" else 3 <= j <= 7 and 2 <= i <= 8:
                        f.set(i, j, COAT.at(0))
    # its nostrils glow on the front of its muzzle, and its mouth glows inside
    f, gl = P_.face("muzzle", "north"), P_.face("muzzle", "north", glow=True)
    for i, j in f.each():
        if j in (1, 2, 3) and i in (0, 1, f.w - 2, f.w - 1):
            col = FIRE[1] if j == 2 else FIRE[3]
            f.set(i, j, hexc(col, 255))
            gl.set(i, j, hexc(col, 230))
    for c, fname in (("muzzle", "bottom"), ("face", "bottom"), ("jaw", "top"), ("skull", "bottom")):
        f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
        for i, j in f.each():
            k = 2 + (i + j) % 3
            f.set(i, j, hexc(FIRE[k], 255))
            gl.set(i, j, hexc(FIRE[k], 220))
    f, gl = P_.face("jaw", "north"), P_.face("jaw", "north", glow=True)
    for i in range(f.w):
        f.set(i, 0, hexc(FIRE[2], 255))
        gl.set(i, 0, hexc(FIRE[2], 200))
    for fname in SIDES + ("top",):
        f = P_.face("teeth", fname)
        for i, j in f.each():
            f.set(i, j, hexc("#d8cbb0" if i % 2 == 0 else "#a89a80", 255))
    P_.paint("teeth", IRON, 4.0, lambda f, i, j: hexc("#c8b898" if (i + j) % 2 else "#9a8c70", 255), grad=0.6, rim=0.6, ao=0.2)
    # the bridle: burnt leather, the stitching gone, blackened iron
    for c in of("cheek_", "noseband"):
        P_.paint(c, LEATHER, 2.6, lambda f, i, j: (0.8 if (i + j) % 5 == 0 else 0) - (1.2 if (i * 7 + j * 3) % 11 == 0 else 0), grad=0.8, rim=0.8, ao=0.4)
    for c in of("ring_"):
        P_.paint(c, IRON, 2.4, lambda f, i, j: 1.6 if (i, j) in ((0, 0), (1, 0)) else (-1.5 if (i, j) == (1, 1) else 0), grad=0.8, rim=1.4, ao=0.4)
    # its hooves: dark horn, the sole and the coronet glowing like coals, cracks in the wall glowing
    for k in range(4):
        c = f"hoof{k}"
        P_.paint(c, HORN, 2.0, lambda f, i, j: 0.6 if i % 3 == 0 else 0, grad=1.0, rim=1.0, ao=0.8)
        for fname in SIDES:
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                if j == f.h - 1:
                    f.set(i, j, hexc(FIRE[3], 255))
                    gl.set(i, j, hexc(FIRE[3], 230))
                elif j == 0 and i % 2 == 0:
                    f.set(i, j, hexc(FIRE[4], 255))
                    gl.set(i, j, hexc(FIRE[4], 150))
                elif (i * 5 + j * 3 + k) % 9 == 0:
                    f.set(i, j, hexc(FIRE[5], 255))
                    gl.set(i, j, hexc(FIRE[5], 120))
        f, gl = P_.face(c, "bottom"), P_.face(c, "bottom", glow=True)
        for i, j in f.each():
            col = FIRE[1] if 2 <= i < f.w - 2 and 2 <= j < f.h - 2 else FIRE[3]
            f.set(i, j, hexc(col, 255))
            gl.set(i, j, hexc(col, 240))
    # the fire: its mane, forelock, fetlocks and tail
    for k, c in enumerate(of("mane", "forelock", "fire")):
        core_ = c.startswith("manec")
        for fname in SIDES:
            _fire(P_.face(c, fname), P_.face(c, fname, glow=True), k * 3 + len(fname), "up", hot=0.6 if core_ else 0.0, reach=0.9 if core_ else 1.0)
        for fname in ("top", "bottom"):
            _fire(P_.face(c, fname), P_.face(c, fname, glow=True), k * 5, "band", hot=0.3, reach=1.0)
    for k, c in enumerate(of("tailf", "tailg")):
        g = c.startswith("tailg")
        for fname in SIDES + ("top", "bottom"):
            _fire(P_.face(c, fname), P_.face(c, fname, glow=True), k * 7 + len(fname), "band", hot=0.7 if g else 0.0, reach=0.95 if c.endswith("2") else 1.05)
    # embers caught in its coat along its neck and back
    for c in ("neck0", "neck1", "back", "withers", "croup"):
        for fname in ("top", "east", "west"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                if (i * 13 + j * 29 + len(c) * 7) % 53 == 0:
                    f.set(i, j, hexc(FIRE[3], 255))
                    gl.set(i, j, hexc(FIRE[3], 200))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion: one pose call (its body, neck and head, its legs, its tail and its mane)

BODY = ("barrel", "chest", "hips")
# what it rests on when it lies: its body and its folded legs (a horse lies on its legs tucked under it), never its fire
SOLID = BODY + tuple(f"{n}{k}{s}" for k in range(4) for n, s in (("leg", "a"), ("leg", "b"), ("hoof", ""))) + ("neck0", "neck1", "head", "jaw", "tail0")
REAR_LIFT = 0.32  # px it rises for each degree it rears, so its hind hooves stay clear of the floor
REAR_BACK = 0.12  # px it moves back for each degree, as if it turned on its hind legs


def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _pose(lift=0.0, shift=(0.0, 0.0), pitch=0.0, yaw=0.0, roll=0.0, arch=0.0, neck=(0, 0, 0), head=(0, 0, 0), jaw=0.0, ears=0.0, legs=None,
          tail=(0, 0, 0), mane=0.0):
    """pitch rears it (+ its front up, lifted and set back so it seems to rise on its hind legs), yaw turns it (+ to its
    right), roll rolls it (+ its left side down); lift raises it (px) and shift moves it (x to its left, z back); arch
    rounds its back (+ up); neck (a nod, + lowering its head; a turn, + to its right; a tilt) shared down its neck and
    head (the same, its own); jaw opens its mouth (+); ears lays its ears back (+); legs {leg: (swing, + forward; fold,
    + bending it, its hoof coming up behind in front and forward behind; its hoof, + curling it up; out, + away from its
    body)}; tail (a lift, + up; a swing, + to its left; a curl, + up toward its end); mane streams its mane back (+)."""
    up = REAR_LIFT * max(0.0, pitch)
    p = {"float": {"rot": (-pitch, yaw, roll), "pos": (shift[0], lift + up, shift[1] + REAR_BACK * max(0.0, pitch))},
         "chest": (0.5 * arch, 0.0, 0.0), "hips": (-0.5 * arch, 0.0, 0.0),
         "neck0": (0.5 * neck[0], 0.5 * neck[1], 0.5 * neck[2]), "neck1": (0.5 * neck[0], 0.5 * neck[1], 0.5 * neck[2]),
         "head": tuple(head), "jaw": (jaw, 0.0, 0.0), "ear_l": (-ears, 0.0, 0.0), "ear_r": (-ears, 0.0, 0.0),
         "tail0": (0.5 * tail[0], 0.4 * tail[1], 0.0), "tail1": (0.3 * tail[0] + 0.5 * tail[2], 0.35 * tail[1], 0.0),
         "tail2": (0.2 * tail[0] + tail[2], 0.25 * tail[1], 0.0)}
    for i in range(NMANE):
        p[f"mane{i}"] = (-mane * (0.7 + 0.06 * i), 0.0, 0.0)
    p["forelock"] = (-0.6 * mane, 0.0, 0.0)
    for k in range(4):
        sw, fo, ho, out = (tuple(legs[k]) + (0, 0, 0, 0))[:4] if legs and k in legs else (0, 0, 0, 0)
        p[f"leg{k}a"] = (-sw, 0.0, -out * SIDE[k])
        p[f"leg{k}b"] = (fo * FOLD[k], 0.0, 0.0)
        p[f"hoof{k}"] = (ho, 0.0, 0.0)
        p[f"fire{k}"] = (-0.5 * fo if FRONT[k] else 0.3 * fo, 0.0, 0.0)
    return p


REST = _pose()


def _mix(a, b, f):
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        ra = va["rot"] if isinstance(va, dict) else va
        rb = vb["rot"] if isinstance(vb, dict) else vb
        rot = tuple(x + (y - x) * f for x, y in zip(ra, rb))
        if isinstance(va, dict) or isinstance(vb, dict):
            pa = va.get("pos", (0, 0, 0)) if isinstance(va, dict) else (0, 0, 0)
            pb = vb.get("pos", (0, 0, 0)) if isinstance(vb, dict) else (0, 0, 0)
            out[k] = {"rot": rot, "pos": tuple(x + (y - x) * f for x, y in zip(pa, pb))}
        else:
            out[k] = rot
    return out


def _floor(r, p, gap=0.3, on=SOLID):
    """The pose let down (or lifted) until the lowest of it (or of the bones on) just rests on the floor."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, on) - (core.GROUND - gap)
    q = dict(p)
    f = q["float"]
    q["float"] = {"rot": f["rot"], "pos": (f["pos"][0], f["pos"][1] + up, f["pos"][2])}
    return q


def _staged(mv, stage, f):
    W, A = mv.seconds("windup"), mv.seconds("active")
    return {"windup": 0.0, "active": W, "recover": W + A}[stage] + f * mv.seconds(stage)


def _fire_scale(r, clip, pts, mode="cat"):
    """Its fire grown or shrunk through a clip, [(seconds, size)], every flame alike (1 at both ends unless given)."""
    c = r.clips[clip]
    ks = {0.0: 1.0, round(c.length, 4): 1.0}
    ks.update((round(t, 4), v) for t, v in pts)
    keys = sorted(ks.items())
    for b in FLAMES:
        c.scale(b, [(t, 1.0 + 0.4 * (v - 1.0), v, 1.0 + 0.6 * (v - 1.0)) for t, v in keys], mode)


def _move_fire(r, mv):
    if getattr(mv, "_fire", None):
        _fire_scale(r, core.clip_name(mv), [(_staged(mv, s, f), v) for s, f, v in mv._fire], getattr(mv, "_mode", "cat"))


def _legs(**kw):
    """Leg tuples by name: fl, fr, bl, br (front left and right, back left and right)."""
    ix = {"fl": 0, "fr": 1, "bl": 2, "br": 3}
    return {ix[k]: v for k, v in kw.items()}


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It stands on the air breathing (8 s), its sides moving, its weight shifting from one hind leg to the other; it
    lifts its head and looks round with its ears pricked; it paws the air with a forehoof, three times, the way a horse
    paws the ground; it snorts and tosses its head; its tail swishes; its fire never still (that is the fly layer, always
    on top)."""
    L, n = 8.0, 160
    keys = []
    for k in range(n + 1):
        u = k / n
        t = L * u
        w = 2 * math.pi * u
        breath = math.sin(2 * math.pi * 0.4 * t)
        look, snort = _wave(u, 0.25, 0.22), _wave(u, 0.82, 0.1)
        paw = _wave(u, 0.55, 0.26)
        stroke = math.sin(2 * math.pi * 3 * (u - 0.42) / 0.26) if 0.42 < u < 0.68 else 0.0
        shift_w = math.sin(w)
        swish = math.sin(2 * math.pi * 3 * u) * (0.4 + 0.6 * _wave(u, 0.7, 0.4))
        keys.append((t, _pose(lift=0.6 * breath, pitch=0.8 * breath - 1.5 * look + 2 * snort, roll=1.2 * shift_w, arch=1.0 * breath,
                              neck=(-14 * look + 6 * snort - 2 + 3 * paw, 18 * look * math.sin(2 * math.pi * 2 * u), 4 * look),
                              head=(-8 * look + 10 * snort * math.sin(2 * math.pi * 10 * u) + 3 * paw, 6 * look, 0), jaw=4 * snort, ears=-6 * look + 4 * paw,
                              legs={0: (paw * (28 + 18 * stroke), paw * (70 + 20 * stroke), paw * 30), 2: (0, 6 * max(0, -shift_w), 8 * max(0, -shift_w)),
                                    3: (0, 6 * max(0, shift_w), 8 * max(0, shift_w))},
                              tail=(-6 + 3 * breath, 24 * swish, 4), mane=4 + 2 * breath)))
    keyed(r, "idle", L, keys, loop=True)


GALLOP = {3: 0.0, 2: 0.1, 1: 0.45, 0: 0.55}  # the hind right, the hind left, the fore right, the fore left
WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}


def _gait(u, offs, swing, fold, hoof):
    legs = {}
    for k, off in offs.items():
        ph = 2 * math.pi * (u - off)
        rec = max(0.0, math.cos(ph)) ** 1.2  # the leg coming forward, folded
        f = FRONT[k]
        legs[k] = (swing[f] * math.sin(ph), fold[f] * rec, hoof[f] * rec)
    return legs


def _locomotion(r):
    """Its fire flickers always, faster as it goes (the fly layer, under everything). It walks on the air a foot at a
    time, nodding (1.2 s, 1.6 blocks); it gallops on the air, its body rocking and its neck pumping, its mane and tail
    streaming out behind (0.6 s, 3.0 blocks)."""
    L, n = 1.0, 20
    keys = []
    for k in range(n + 1):
        u = k / n
        p = _pose(tail=(0, 3 * math.sin(2 * math.pi * u), 2 * math.sin(2 * math.pi * 2 * u + 1.0)))
        for i in range(NMANE):
            p[f"mane{i}"] = (6 * math.sin(2 * math.pi * 2 * u + 1.3 * i), 0.0, 5 * math.sin(2 * math.pi * 3 * u + 0.9 * i))
        p["forelock"] = (5 * math.sin(2 * math.pi * 3 * u), 0.0, 4 * math.sin(2 * math.pi * 2 * u + 1.0))
        for q in range(4):
            p[f"fire{q}"] = (6 * math.sin(2 * math.pi * 3 * u + q), 0.0, 4 * math.sin(2 * math.pi * 2 * u + 2 * q))
        keys.append((L * u, p))
    keyed(r, "fly", L, keys, loop=True)
    c = r.clips["fly"]
    for i, b in enumerate(FLAMES):  # its flames leap and fall, each to its own beat
        c.scale(b, [(L * k / n, 1.0, 1.0 + 0.16 * math.sin(2 * math.pi * 3 * k / n + 1.1 * i) + 0.06 * math.sin(2 * math.pi * 5 * k / n + 2.0 * i), 1.0)
                    for k in range(n + 1)], "cat")
    L, n = 1.2, 24
    keys = []
    for k in range(n + 1):
        u = k / n
        s2 = math.sin(2 * math.pi * 2 * u)
        keys.append((L * u, _pose(lift=0.6 * s2, pitch=1.0 * s2, roll=1.0 * math.sin(2 * math.pi * u), neck=(4 * math.sin(2 * math.pi * 2 * u + 0.8), 0, 0),
                                  head=(2 * math.sin(2 * math.pi * 2 * u + 1.2), 0, 0),
                                  legs=_gait(u, WALK, {True: 18, False: 16}, {True: 45, False: 35}, {True: 30, False: 24}), tail=(0, 8 * math.sin(2 * math.pi * u), 2),
                                  mane=8)))
    keyed(r, "walk", L, keys, loop=True)
    L, n = 0.6, 24
    keys = []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        keys.append((L * u, _pose(lift=2.0 * math.sin(2 * math.pi * u - 0.3), pitch=6 * math.sin(2 * math.pi * u + 0.6) - 2, arch=4 * s,
                                  neck=(10 - 10 * math.sin(2 * math.pi * u + 0.6), 0, 0), head=(4 - 4 * math.sin(2 * math.pi * u + 1.0), 0, 0), ears=10,
                                  legs=_gait(u, GALLOP, {True: 36, False: 32}, {True: 80, False: 60}, {True: 50, False: 40}),
                                  tail=(22 + 4 * s, 4 * math.sin(2 * math.pi * u + 1.0), 6), mane=26)))
    keyed(r, "run", L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.6, 3.0
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # turning to its left, it steps its forelegs round, its head leading
        keys = []
        n = 20
        for k in range(n + 1):
            u = k / n
            a, b = math.sin(2 * math.pi * u), math.sin(2 * math.pi * u + math.pi)
            keys.append((1.0 * u, _pose(yaw=-4 * sg, roll=2 * sg, neck=(2, -14 * sg, 0), head=(0, -8 * sg, 0),
                                        legs={0: (6 * a, 30 * max(0, a), 20 * max(0, a), 6 * sg * a), 1: (6 * b, 30 * max(0, b), 20 * max(0, b), -6 * sg * b),
                                              2: (0, 14 * max(0, b), 10 * max(0, b)), 3: (0, 14 * max(0, a), 10 * max(0, a))},
                                        tail=(0, 10 * sg, 0))))
        keyed(r, nm, 1.0, keys, loop=True)


def _lying(r, slack=1.0):
    """Down on the floor, broken: its forelegs folded under it, its hind legs tucked, its head low on its neck with its
    muzzle near the floor, its ears back, its tail on the floor."""
    p = _pose(neck=(34 * slack, 8 * slack, 4 * slack), head=(16 * slack, 6 * slack, 0), ears=20 * slack,
              legs={0: (-12, 150, 30), 1: (-8, 140, 30), 2: (64, 120, 20), 3: (60, 116, 20)}, tail=(-30, 10, 0), mane=-6)
    return _floor(r, p)


LOW_FIRE = 0.4  # its fire when it lies broken


def _reactions(r):
    """Struck, it throws its head up and shies back, its ears pinned; from the side it shies away from the blow. Broken,
    it goes down on its knees and then its belly, its fire sinking low; it gets up front first, heaving, and shakes."""
    hit = _pose(lift=1, shift=(0, 4), pitch=8, neck=(-20, 0, 0), head=(-10, 0, 0), jaw=10, ears=24, legs=_legs(fl=(10, 40, 20), fr=(6, 30, 10)),
                tail=(10, 0, 4), mane=10)
    back = _pose(shift=(0, 1), pitch=-2, neck=(6, 0, 0), head=(2, 0, 0), ears=8)
    keyed(r, "hurt", 0.6, [(0, REST), (0.08, hit), (0.32, back), (0.6, REST)])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left, it shies away to its right, its head swinging back toward the blow
        h = _pose(shift=(-4 * sg, 1), yaw=8 * sg, roll=-8 * sg, neck=(-10, 16 * sg, 4 * sg), head=(-4, 8 * sg, 0), ears=20,
                  legs={0: (4, 20, 10, -10 * sg), 1: (4, 20, 10, -10 * sg), 2: (0, 10, 6, -6 * sg), 3: (0, 10, 6, -6 * sg)}, tail=(6, -14 * sg, 0))
        b = _pose(shift=(-1 * sg, 0.5), roll=2 * sg, neck=(3, -4 * sg, 0), ears=6)
        keyed(r, nm, 0.65, [(0, REST), (0.08, h), (0.34, b), (0.65, REST)])
    low = _lying(r)
    knees = _floor(r, _pose(pitch=-10, neck=(16, 0, 0), head=(8, 0, 0), ears=14, legs={0: (-10, 140, 30), 1: (-6, 130, 30), 2: (10, 20, 10), 3: (8, 20, 10)},
                            tail=(-10, 0, 0)))
    keyed(r, "kneel", 1.2, [(0, REST), (0.18, _pose(pitch=4, neck=(-10, 0, 0), ears=18, legs=_legs(fl=(8, 30, 10), fr=(8, 30, 10)))), (0.5, knees),
                            (0.85, _mix(knees, low, 0.9)), (1.2, low)])
    _fire_scale(r, "kneel", [(0.3, 1.0), (0.9, 0.6), (1.2, LOW_FIRE)])
    _fire_scale_end(r, "kneel", LOW_FIRE)
    push = _floor(r, _pose(pitch=12, neck=(-6, 0, 0), head=(0, 0, 0), legs={0: (30, 20, 10), 1: (24, 40, 20), 2: (60, 110, 20), 3: (56, 106, 20)}, tail=(-20, 0, 0)))
    up_ = _pose(lift=1, pitch=2, neck=(-8, 0, 0), head=(-4, 0, 0), legs=_legs(bl=(10, 30, 10), br=(6, 20, 10)))
    shake = lambda q: _pose(neck=(2, 10 * q, 8 * q), head=(0, 10 * q, 10 * q), ears=4, mane=-4)
    keyed(r, "rise", 1.4, [(0, low), (0.35, push), (0.7, up_), (0.9, shake(1)), (1.05, shake(-1)), (1.2, shake(0.5)), (1.4, REST)])
    _fire_scale(r, "rise", [(0.0, LOW_FIRE), (0.5, 0.8), (0.95, 1.25), (1.4, 1.0)])
    _death(r)


def _fire_scale_end(r, clip, v):
    """A clip that ends with its fire at v holds it there (the last key)."""
    c = r.clips[clip]
    for b in FLAMES:
        ks = c.tracks.get((b, "scale"))
        if ks:
            t, val, mode = ks[-1][0], ks[-1][1], ks[-1][2] if len(ks[-1]) > 2 else "cat"
            ks[-1] = (t, (1.0 + 0.4 * (v - 1.0), v, 1.0 + 0.6 * (v - 1.0)), mode)


def _death(r):
    """Its death: it rears and screams, its fire flaring; it comes down and staggers, its forelegs buckle and it goes down
    on its knees, then over onto its side with its legs stiff out; it lifts its head once and lets it fall; and its fire
    goes out, the flames sinking down into it until there is nothing but black."""
    rear = _pose(pitch=30, neck=(-24, 0, 0), head=(-14, 0, 0), jaw=26, ears=20, legs=_legs(fl=(40, 90, 30), fr=(30, 70, 30)), tail=(10, 0, 6), mane=20)
    down = _pose(pitch=-6, neck=(10, 6, 0), head=(6, 0, 0), jaw=8, ears=16, legs=_legs(fl=(-6, 10, 0), fr=(10, 20, 10)))
    stag = _pose(shift=(2, 0), roll=-6, pitch=-4, neck=(14, -10, 6), head=(8, -4, 0), ears=20, legs={0: (-4, 30, 10, 8), 1: (8, 10, 0, -4)})
    knees = _floor(r, _pose(pitch=-10, roll=-4, neck=(20, 0, 0), head=(8, 0, 0), ears=20, legs={0: (-10, 140, 30), 1: (-6, 130, 30), 2: (10, 20, 10), 3: (8, 20, 10)}))
    side = lambda nod: _floor(r, _pose(roll=-82, neck=(nod, -10, 0), head=(nod * 0.4, 0, 0), jaw=4, ears=24,
                                       legs={0: (20, 20, 10, 6), 1: (10, 30, 10, 0), 2: (-16, 10, 6, 6), 3: (-10, 16, 6, 0)}, tail=(-10, 0, 0)), gap=0.3,
                                on=SOLID + tuple(f"fire{k}" for k in range(4)))  # on its side its fetlocks' fire lies on the floor too
    over = _floor(r, _pose(pitch=-5, roll=-42, neck=(24, -6, 0), head=(8, 0, 0), jaw=4, ears=22,
                           legs={0: (4, 80, 20, 6), 1: (2, 80, 20, 0), 2: (-4, 16, 8, 6), 3: (-2, 18, 8, 0)}, tail=(-10, 0, 0)), on=SOLID + tuple(f"fire{k}" for k in range(4)))
    keys = [(0, REST), (0.35, rear), (0.75, _mix(rear, REST, 0.4)), (1.1, down), (1.5, stag), (2.0, knees), (2.35, over), (2.7, side(20)), (3.2, side(14)), (3.6, side(-10)),
            (4.1, side(18)), (5.5, side(20))]
    keyed(r, "death", 5.5, keys)
    _fire_scale(r, "death", [(0.35, 1.5), (0.8, 1.2), (2.0, 0.9), (3.6, 0.6), (4.6, 0.2), (5.45, 0.04), (5.5, 0.04)], "lin")
    _fire_scale_end(r, "death", 0.04)


# ---------------------------------------------------------------------------------------------------------------------
# Moves

HOOF_HIT = (0, 5, -1)


def _back(end):
    return [(0.45, _mix(end, REST, 0.45)), (0.72, _mix(end, REST, 0.82))]


def strike(r):
    """The Strike (LIGHT): it rears up a little with its ears pinned and its head high, its near forehoof drawn up and
    folded; and strikes it down and out at you, its head snaking forward with its teeth bared."""
    cock = _pose(lift=1, shift=(0, 2), pitch=14, neck=(-12, 0, 0), head=(-8, 0, 0), jaw=6, ears=24, legs=_legs(fl=(50, 80, 30), fr=(14, 30, 10)),
                 tail=(6, 0, 4), mane=10)
    hit = _pose(lift=-1, shift=(0, -4), pitch=-2, neck=(16, 0, 0), head=(6, 0, 0), jaw=16, ears=26, legs=_legs(fl=(34, 4, -10), fr=(6, 20, 10)),
                tail=(4, 0, 2), mane=14)
    held = _pose(lift=-1.1, shift=(0, -4.2), pitch=-2.3, neck=(17, 0, 0), head=(6.5, 0, 0), jaw=16, ears=26, legs=_legs(fl=(35, 4, -11), fr=(6, 20, 10)),
                 tail=(4, 0, 2), mane=14)
    return Move("strike", "LIGHT", 10, 5, 14, through=held, title="Strike", track=8.0, rng=(0, 4.5), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(),
                hit=[("hoof0", HOOF_HIT, 6.0), ("head", (0, -2, -22), 6.0)],
                stages={"windup": [(0.2, _mix(REST, cock, 0.6)), (0.6, _mix(REST, cock, 0.9)), (1.0, cock)], "active": [(0.4, _mix(cock, hit, 0.5)), (1.0, hit)],
                        "recover": _back(held)})


def trample(r):
    """The Trample (LIGHT, after the Strike): it rears higher with both forelegs folded up and its head flung up, and
    brings both forehooves down on you together, its head driving down after them."""
    rear = _pose(lift=2, shift=(0, 3), pitch=26, neck=(-20, 0, 0), head=(-12, 0, 0), jaw=20, ears=28, legs=_legs(fl=(44, 96, 30), fr=(40, 90, 30)),
                 tail=(14, 0, 6), mane=16)
    down = _pose(lift=-2, shift=(0, -6), pitch=-6, arch=4, neck=(22, 0, 0), head=(9, 0, 0), jaw=12, ears=28,
                 legs={0: (28, 26, 6, 6), 1: (26, 26, 6, 6), 2: (16, 30, 10), 3: (14, 30, 10)}, tail=(10, 0, 4), mane=20)
    held = _pose(lift=-2.2, shift=(0, -6.2), pitch=-6.4, arch=4, neck=(23, 0, 0), head=(9.5, 0, 0), jaw=12, ears=28,
                 legs={0: (29, 27, 6, 6), 1: (27, 27, 6, 6), 2: (17, 31, 10), 3: (15, 31, 10)}, tail=(10, 0, 4), mane=20)
    return Move("trample", "LIGHT", 10, 5, 14, through=held, title="Trample", track=8.0, rng=(0, 4.5), kinds=("combo", "near"), damage=1.2, knock=0.9, plant=(),
                hit=[("hoof0", HOOF_HIT, 6.0), ("hoof1", HOOF_HIT, 6.0)],
                stages={"windup": [(0.2, _mix(REST, rear, 0.6)), (0.6, _mix(REST, rear, 0.9)), (1.0, rear)], "active": [(0.4, _mix(rear, down, 0.55)), (1.0, down)],
                        "recover": _back(held)})


ADVANCE = 9.0  # blocks the Charge carries it


def charge(r):
    """The Charge (HEAVY, its gap closer): it drops its head low with its ears pinned and paws the air once, gathers its
    hind legs under it and holds, its fire streaming back; then it gallops at you, stretched out, its head low and its
    teeth bared, and rears to a stop."""
    low = _pose(lift=-1, shift=(0, 3), pitch=-4, arch=4, neck=(20, 0, 0), head=(10, 0, 0), ears=30, legs=_legs(fl=(30, 70, 30), bl=(20, 30, 10), br=(18, 30, 10)),
                tail=(10, 0, 4), mane=20)
    gather = _pose(lift=-2, shift=(0, 4), pitch=-6, arch=6, neck=(24, 0, 0), head=(12, 0, 0), jaw=8, ears=32,
                   legs=_legs(fl=(6, 30, 10), fr=(10, 20, 10), bl=(30, 40, 16), br=(28, 40, 16)), tail=(14, 0, 4), mane=24)
    bound = _pose(lift=2, shift=(0, -8), pitch=-4, arch=-6, neck=(22, 0, 0), head=(8, 0, 0), jaw=20, ears=32,
                  legs=_legs(fl=(44, 10, 10), fr=(40, 30, 20), bl=(-36, 6, 0), br=(-30, 10, 4)), tail=(30, 0, 8), mane=34)
    strike_ = _pose(lift=0, shift=(0, -12), pitch=2, arch=-2, neck=(26, 0, 0), head=(10, 0, 0), jaw=24, ears=32,
                    legs=_legs(fl=(30, 40, 20), fr=(34, 14, 10), bl=(-20, 20, 10), br=(-24, 10, 4)), tail=(26, 0, 6), mane=34)
    held = _pose(lift=0, shift=(0, -12.5), pitch=2.5, arch=-2, neck=(26.5, 0, 0), head=(10, 0, 0), jaw=24, ears=32,
                 legs=_legs(fl=(31, 40, 20), fr=(35, 14, 10), bl=(-20, 20, 10), br=(-24, 10, 4)), tail=(26, 0, 6), mane=34)
    stop = _pose(shift=(0, -6), pitch=14, neck=(-10, 0, 0), head=(-6, 0, 0), jaw=8, ears=16, legs=_legs(fl=(30, 60, 20), fr=(26, 50, 20)), tail=(8, 0, 4), mane=10)
    return Move("charge", "HEAVY", 18, 10, 22, through=held, title="Charge", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 14), kinds=("gap",), damage=1.4,
                knock=1.4, plant=(), hit=[("head", (0, -2, -22), 8.0), ("chest", (0, -2, -11), 10.0), ("hoof0", HOOF_HIT, 6.0), ("hoof1", HOOF_HIT, 6.0)],
                stages={"windup": [(0.2, _mix(REST, low, 0.7)), (0.55, low), (1.0, gather)], "active": [(0.4, bound), (1.0, strike_)],
                        "recover": [(0.4, stop), (0.75, _mix(stop, REST, 0.75))]})


def buck(r):
    """The Buck (for whoever is behind it): it drops its head between its forelegs and its weight onto them, and kicks
    out behind with both hind legs, high, its tail flung up."""
    coil = _pose(lift=0, shift=(0, -2), pitch=-9, arch=9, neck=(32, 0, 0), head=(16, 0, 0), jaw=6, ears=32,
                 legs={0: (-10, 8, 0, 6), 1: (-10, 8, 0, 6), 2: (34, 80, 26), 3: (32, 80, 26)}, tail=(-16, 0, 0))
    kick = _pose(lift=6, shift=(0, -3), pitch=-20, arch=-4, neck=(30, 0, 0), head=(14, 0, 0), ears=32,
                 legs=_legs(bl=(-70, 0, -20), br=(-66, 4, -20), fl=(-12, 10, 0), fr=(-8, 10, 0)), tail=(50, 0, 10))
    held = _pose(lift=6.2, shift=(0, -3), pitch=-20.5, arch=-4, neck=(30, 0, 0), head=(14, 0, 0), ears=32,
                 legs=_legs(bl=(-72, 0, -21), br=(-68, 4, -21), fl=(-12, 10, 0), fr=(-8, 10, 0)), tail=(52, 0, 10))
    return Move("buck", "LIGHT", 12, 4, 14, through=held, title="Buck", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.2, knock=1.6, plant=(),
                hit=[("hoof2", HOOF_HIT, 7.0), ("hoof3", HOOF_HIT, 7.0)],
                stages={"windup": [(0.2, _mix(REST, coil, 0.6)), (0.6, _mix(REST, coil, 0.9)), (1.0, coil)], "active": [(0.45, _mix(coil, kick, 0.6)), (1.0, kick)],
                        "recover": _back(held)})


MOUTH = (0.0, 4.0, 2.6)  # blocks (x, up, forward): the front of its head


def cinders(r):
    """Cinders: it lifts its head high and tosses it, four times, flinging fire off its mane at you with each toss, its
    forelegs stamping the air between them."""
    high = _pose(pitch=4, neck=(-24, 0, 0), head=(-10, 0, 0), ears=10, mane=-10)
    toss = lambda q: _pose(pitch=-2, neck=(16, 6 * q, -6 * q), head=(10, 6 * q, 0), jaw=12, ears=16, legs={0: (10, 40, 20)} if q > 0 else {1: (10, 40, 20)}, mane=-24)
    act = []
    for i in range(4):
        f0 = i / 4
        act += [(f0 + 0.1, toss((-1) ** i)), (f0 + 0.22, _mix(high, REST, 0.15))]
    act[-1] = (1.0, _mix(high, REST, 0.3))
    end = _mix(high, REST, 0.4)
    return Move("cinders", "NONE", 12, 26, 14, through=end, title="Cinders", track=8.0, ability="Cinders", origin=MOUTH, rng=(4, 20), kinds=("ranged",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(REST, high, 0.5)), (1.0, high)], "active": act, "recover": _back(end)})


def pyre(r):
    """Pyre: it rears and stamps down with both forehooves, and stands braced with its head up and its legs spread while
    its fire roars up all over it, its mane and tail and fetlocks burning twice as tall, shaking with the heat."""
    rear = _pose(pitch=20, neck=(-16, 0, 0), head=(-10, 0, 0), jaw=12, ears=20, legs=_legs(fl=(40, 90, 30), fr=(36, 84, 30)), tail=(10, 0, 4), mane=10)
    brace = lambda q=0.0: _pose(lift=0.8 * q, pitch=-3 + 1.5 * q, roll=3 * q, neck=(-14 + 3 * abs(q), 7 * q, 5 * q), head=(-8, 4 * q, 5 * q), jaw=14 + 4 * abs(q), ears=6,
                                legs={0: (8 + 6 * max(0, q), 24 * max(0, q), 12 * max(0, q), 10), 1: (8 + 6 * max(0, -q), 24 * max(0, -q), 12 * max(0, -q), 10),
                                      2: (-6, 0, 0, 8), 3: (-6, 0, 0, 8)}, tail=(24, 10 * q, 8), mane=-14)
    act = [(0.15, _mix(rear, brace(), 0.6)), (0.3, brace()), (0.45, brace(1)), (0.6, brace(-1)), (0.75, brace(1)), (0.9, brace(-1)), (1.0, brace(0.5))]
    end = _mix(brace(), REST, 0.3)
    mv = Move("pyre", "NONE", 14, 28, 16, through=end, title="Pyre", track=0.0, ability="Pyre", rng=(0, 8), kinds=("area",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, _mix(REST, rear, 0.6)), (1.0, rear)], "active": act, "recover": _back(end)})
    mv._fire = [("windup", 1.0, 1.1), ("active", 0.3, 1.9), ("active", 0.6, 2.0), ("active", 1.0, 1.8), ("recover", 0.5, 1.1)]
    return mv


def firefall(r):
    """Firefall (into the second phase): it rears right up on its hind legs, its forelegs folded and pawing, and
    shakes its head from side to side, its mane thrashing, and the fire flies off it and rains down; it comes down."""
    up_ = _pose(pitch=40, neck=(-16, 0, 0), head=(-6, 0, 0), jaw=20, ears=20, legs=_legs(fl=(46, 100, 30), fr=(40, 90, 30)), tail=(14, 0, 6), mane=6)
    thrash = lambda q: _pose(pitch=40 + 2 * abs(q), neck=(-12, 22 * q, 12 * q), head=(-4, 14 * q, 10 * q), jaw=24, ears=22,
                             legs=_legs(fl=(46 + 10 * q, 100, 30), fr=(40 - 10 * q, 90, 30)), tail=(14, 10 * q, 6), mane=-10)
    act = [(0.12, thrash(1)), (0.26, thrash(-1)), (0.4, thrash(1)), (0.54, thrash(-1)), (0.68, thrash(1)), (0.82, thrash(-0.6)), (1.0, _mix(up_, REST, 0.2))]
    land = _pose(pitch=-4, neck=(12, 0, 0), head=(6, 0, 0), ears=10, legs=_legs(fl=(16, 10, 0), fr=(14, 10, 0)))
    mv = Move("firefall", "NONE", 16, 30, 18, through=_mix(up_, land, 0.5), title="Firefall", track=0.0, ability="Firefall", rng=(0, 22),
              kinds=("ranged", "area"), damage=0, knock=0, plant=(),
              stages={"windup": [(0.25, _mix(REST, up_, 0.55)), (1.0, up_)], "active": act, "recover": [(0.4, land), (0.75, _mix(land, REST, 0.75))]})
    mv._fire = [("windup", 1.0, 1.2), ("active", 0.3, 1.5), ("active", 1.0, 1.3), ("recover", 0.5, 1.0)]
    return mv


def flare(r):
    """Flare: it gathers itself, its head down between its knees and its legs drawn in under it, its fire sinking into
    it and trembling; then it flings its head up and its legs out and flares, its fire bursting out twice as big."""
    w = []
    n = 12
    for i in range(1, n + 1):
        u = i / n
        f = _ease(min(1.0, u / 0.5))
        q = (-1) ** i * min(1.0, 0.3 + u) * f
        w.append((u, _pose(lift=-2 * f + 0.6 * q, pitch=-6 * f, roll=2 * q, arch=10 * f, neck=(34 * f, 4 * q, 3 * q), head=(14 * f, 3 * q, 0), ears=26 * f,
                           legs={0: (-14 * f + 3 * q, 40 * f, 20 * f, -4 * f), 1: (-14 * f - 3 * q, 40 * f, 20 * f, -4 * f), 2: (24 * f - 3 * q, 50 * f, 20 * f, -4 * f),
                                 3: (24 * f + 3 * q, 50 * f, 20 * f, -4 * f)}, tail=(-30 * f, 4 * q, 0), mane=-4 * f)))
    # flared, it shudders all through while its fire burns (q: the shudder's swing)
    burst = lambda q=0.0: _pose(lift=4 + 0.5 * q, pitch=10 + 1.5 * q, yaw=2 * q, roll=2.5 * q, arch=-8, neck=(-30 + 3 * q, 4 * q, 3 * q), head=(-14, 3 * q, 0),
                                jaw=28, ears=-6, legs={0: (20 + 6 * q, 10, -6, 20), 1: (20 - 6 * q, 10, -6, 20), 2: (-20 - 5 * q, 6, -6, 16), 3: (-20 + 5 * q, 6, -6, 16)},
                                tail=(40, 10 * q, 10), mane=-20)
    act = [(0.25, burst()), (0.44, _mix(burst(1), REST, 0.05)), (0.62, _mix(burst(-1), REST, 0.1)), (0.8, _mix(burst(0.7), REST, 0.15)), (1.0, _mix(burst(), REST, 0.2))]
    sag = _pose(pitch=-2, neck=(10, 0, 0), head=(4, 0, 0), ears=6)
    mv = Move("flare", "NONE", 20, 16, 18, through=_mix(burst(), sag, 0.4), title="Flare", track=0.0, ability="Flare", origin=(0.0, 2.6, 0.6), rng=(0, 12),
              kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": w, "active": act, "recover": [(0.45, sag), (0.75, _mix(sag, REST, 0.75))]})
    mv._fire = [("windup", 0.5, 0.5), ("windup", 1.0, 0.45), ("active", 0.2, 2.2), ("active", 0.6, 1.8), ("recover", 0.4, 1.1)]
    return mv


def wildfire(r):
    """Wildfire (the signature): it rears up as high as it can, screaming, its forelegs pawing the air by turns, its
    fire roaring higher and higher, and holds at the top, shaking; then it crashes down on both forehooves with its head
    driven down, and the fire bursts out of it across the floor."""
    w = []
    n = 14
    for i in range(1, n + 1):
        u = i / n
        f = _ease(min(1.0, u / 0.5))
        q = (-1) ** i
        sh = min(1.0, 0.2 + u) * _ease(max(0.0, (u - 0.5) / 0.3))
        w.append((u, _pose(pitch=48 * f + 1.5 * q * sh, roll=1.5 * q * sh, neck=(-22 * f, 3 * q * sh, 0), head=(-14 * f, 0, 2 * q * sh), jaw=30 * f, ears=24 * f,
                           legs=_legs(fl=(40 * f + 14 * q * (1 - sh) * f, 100 * f, 30 * f), fr=(36 * f - 14 * q * (1 - sh) * f, 90 * f, 30 * f)),
                           tail=(16 * f, 0, 8 * f), mane=4 * f)))
    crash = _floor(r, _pose(pitch=-8, arch=4, neck=(30, 0, 0), head=(14, 0, 0), jaw=20, ears=30, legs=_legs(fl=(26, 4, -10), fr=(24, 4, -10), bl=(-10, 20, 10),
                                                                                                         br=(-8, 20, 10)), tail=(20, 0, 6), mane=24),
                   gap=1.0, on=("hoof0", "hoof1"))
    held = dict(crash)
    act = [(0.35, _mix(w[-1][1], crash, 0.55)), (0.7, crash), (1.0, held)]
    stand = _pose(lift=1, pitch=4, neck=(-6, 0, 0), head=(-4, 0, 0), jaw=6, ears=8)
    mv = Move("wildfire", "PERILOUS", 30, 12, 28, through=_mix(crash, stand, 0.3), title="Wildfire", signature=True, track=0.0, rng=(0, 10), kinds=("area",),
              damage=1.8, knock=1.5, disc=6.0, plant=(), hit=[("hoof0", HOOF_HIT, 8.0), ("hoof1", HOOF_HIT, 8.0), ("chest", (0, -6, -11), 10.0)],
              stages={"windup": w, "active": act, "recover": [(0.4, _mix(crash, stand, 0.5)), (0.75, _mix(stand, REST, 0.7))]})
    mv._fire = [("windup", 0.3, 1.2), ("windup", 1.0, 2.0), ("active", 0.7, 2.4), ("active", 1.0, 1.8), ("recover", 0.5, 1.1)]
    return mv


def arrive(r):
    """Its intro: it lies burnt out with its fire down to embers; a flame runs back along its mane and catches; it lifts
    its head and looks at you, throws its forelegs out and heaves itself up, front first, shakes its head, and rears up
    screaming with its fire roaring, and comes down."""
    low = _lying(r)
    # a horse gets up by lifting its chest, bringing one knee forward and unrolling that foreleg flat in front of it,
    # then the other, then sitting up on them like a dog and heaving its hind end up (the angles solved so the hooves
    # pass over the floor, never through it)
    tuck = {2: (64, 120, 20), 3: (60, 116, 20)}
    up = lambda l0, l1, **kw: _floor(r, _pose(pitch=12, legs={0: l0, 1: l1} | tuck, **kw))
    chest = up((-8, 140, 30), (-8, 140, 30), neck=(8, 0, 0), head=(2, 0, 0), ears=-4, tail=(-29, 10, 0), mane=-6)
    knee0 = up((110, 130, 20), (-8, 140, 30), neck=(4, 0, 0), head=(1, 0, 0), ears=-3, tail=(-28, 10, 0), mane=-5)
    one = up((105, 70, 10), (-8, 140, 30), neck=(2, 0, 0), ears=-2, tail=(-28, 8, 0), mane=-4)
    knee1 = up((72, 6, 0), (110, 130, 20), neck=(0, 0, 0), ears=-2, tail=(-27, 8, 0))
    one1 = up((72, 6, 0), (105, 70, 10), neck=(-2, 0, 0), ears=-2, tail=(-26, 6, 0))
    two = up((72, 6, 0), (72, 6, 0), neck=(-4, 0, 0), ears=-2, tail=(-26, 6, 0))
    push = _floor(r, _pose(pitch=20, neck=(-10, 0, 0), legs={0: (46, 2, 0), 1: (44, 4, 0), 2: (60, 110, 20), 3: (56, 106, 20)}, tail=(-20, 0, 0)))
    stand = _pose(lift=1, pitch=2, neck=(-6, 0, 0), legs=_legs(bl=(10, 30, 10), br=(6, 20, 10)))
    shake = lambda q: _pose(neck=(0, 12 * q, 10 * q), head=(0, 10 * q, 10 * q), ears=4, mane=-6)
    rear = _pose(pitch=34, neck=(-22, 0, 0), head=(-12, 0, 0), jaw=28, ears=20, legs=_legs(fl=(44, 96, 30), fr=(38, 88, 30)), tail=(14, 0, 6), mane=12)
    land = _pose(pitch=-4, neck=(10, 0, 0), head=(4, 0, 0), ears=6, legs=_legs(fl=(14, 10, 0), fr=(12, 10, 0)))
    mv = Move("arrive", "NONE", 16, 44, 16, through=_mix(land, REST, 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.33, chest), (0.66, knee0), (1.0, one)],
                      "active": [(0.07, knee1), (0.14, one1), (0.21, two), (0.31, push), (0.43, stand), (0.51, shake(1)), (0.59, shake(-1)), (0.67, shake(0.4)),
                                 (0.83, rear), (1.0, land)],
                      "recover": [(0.6, _mix(land, REST, 0.7))]})
    mv._first = low  # it starts where the kneel left it, lying with its fire low
    mv._fire = [("windup", 0.0, LOW_FIRE), ("windup", 0.6, 0.6), ("windup", 1.0, 0.9), ("active", 0.36, 1.1), ("active", 0.8, 1.8), ("active", 1.0, 1.3),
                ("recover", 0.6, 1.0)]
    return mv


def _on(base, p):
    """p, where base is."""
    q = dict(p)
    q["float"] = base["float"]
    return q


def moves(r):
    return [strike(r), trample(r), charge(r), buck(r), cinders(r), pyre(r), firefall(r), flare(r), wildfire(r), arrive(r)]


def _stage(r, mv):
    """stage_clips, but a move with a first pose of its own (the intro, from the kneel) starts from it."""
    first = getattr(mv, "_first", None)
    if first is None:
        return stage_clips(r, mv)
    orig = core.keyed

    def from_first(rig, name, length, keys, **kw):
        keys = list(keys)
        keys[0] = (0.0, first)
        return orig(rig, name, length, keys, **kw)
    core.keyed = from_first
    try:
        stage_clips(r, mv)
    finally:
        core.keyed = orig


def _drift(r, clip, ramp=0.25):
    """Under a move, once it is made: its ears flick, its tail swishes and its jaw works at their own pace, faded in and
    out at the ends so it hands back to the idle exactly as it was (a living thing never holds a pose dead still)."""
    c = r.clips[clip]
    L = c.length
    n = int(round(L / (2 * core.TICK)))

    def env(t):
        e = max(0.0, min(1.0, t / ramp, (L - t) / ramp))
        return e * e * (3 - 2 * e)

    def add(bone, f):
        old = c.tracks.get((bone, "rot"))
        keys = []
        for k in range(n + 1):
            t = min(L, k * L / n)
            base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
            d = f(t)
            keys.append((t, tuple(base[i] + env(t) * d[i] for i in range(3)), "cat"))
        c.tracks[(bone, "rot")] = keys
    for nm, ph in (("ear_l", 0.0), ("ear_r", 1.7)):
        add(nm, lambda t, ph=ph: (6.0 * math.sin(2 * math.pi * 0.9 * t + ph), 4.0 * math.sin(2 * math.pi * 0.6 * t + ph), 0.0))
    add("tail1", lambda t: (2.0 * math.sin(2 * math.pi * 0.5 * t), 8.0 * math.sin(2 * math.pi * 0.7 * t + 0.5), 0.0))
    add("tail2", lambda t: (3.0 * math.sin(2 * math.pi * 0.5 * t - 0.6), 10.0 * math.sin(2 * math.pi * 0.7 * t - 0.2), 0.0))
    add("jaw", lambda t: (1.5 + 1.5 * math.sin(2 * math.pi * 1.1 * t), 0.0, 0.0))
    add("chest", lambda t: (1.2 * math.sin(2 * math.pi * 0.45 * t), 0.0, 0.8 * math.sin(2 * math.pi * 0.3 * t)))
    add("neck0", lambda t: (2.0 * math.sin(2 * math.pi * 0.5 * t + 0.4), 1.5 * math.sin(2 * math.pi * 0.33 * t), 0.0))
    for k in range(4):
        add(f"leg{k}a", lambda t, k=k: (2.0 * math.sin(2 * math.pi * 0.4 * t + 1.3 * k), 0.0, 0.0))
        add(f"leg{k}b", lambda t, k=k: (2.5 * math.sin(2 * math.pi * 0.55 * t + 0.9 * k + 0.8), 0.0, 0.0))
        add(f"fire{k}", lambda t, k=k: (6.0 * math.sin(2 * math.pi * 1.3 * t + 1.7 * k), 0.0, 4.0 * math.sin(2 * math.pi * 0.9 * t + k)))
    for i in range(NMANE):  # its mane's flames lick back and forth along its neck
        add(f"mane{i}", lambda t, i=i: (5.0 * math.sin(2 * math.pi * 1.1 * t - 0.8 * i), 0.0, 4.0 * math.sin(2 * math.pi * 0.8 * t - 0.6 * i)))
    add("head", lambda t: (1.5 * math.sin(2 * math.pi * 0.45 * t), 1.5 * math.sin(2 * math.pi * 0.35 * t + 1.0), 0.0))


# the ground (SPEC §35.10): its hooves fold up out of the floor, its head and tail lift off it
HOOF_GROUND = [([f"hoof{k}"], [(f"leg{k}b", 0, 40, FOLD[k]), (f"hoof{k}", 0, 30, 1)]) for k in range(4)]
HEAD_GROUND = [(["head", "jaw"], [("neck1", 0, 30, -1), ("head", 0, 30, -1)])]
TAIL_GROUND = [(["tail2"], [("tail1", 0, 30, 1), ("tail0", 0, 20, 1)])]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        _stage(r, m)
        _move_fire(r, m)
        _drift(r, core.clip_name(m))
    core.ground_pass(r, HOOF_GROUND + HEAD_GROUND + TAIL_GROUND, clips=list(r.clips), skip=())
    return mv


# Its sounds (SPEC §35.8): a horse's scream and snorting pitched low, its gallop and its landing, the skeleton horse's
# cry under them; fire for everything else, a blaze's breath and shot, a fire's crackle, a fire charge flung; chosen on
# paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.horse.angry", 0.8, 0.6), ("block.fire.ambient", 0.8, 0.8)],
    "tell_heavy": [("entity.horse.breathe", 1.2, 0.5), ("entity.blaze.ambient", 0.7, 0.5)],
    "tell_perilous": [("entity.horse.angry", 1.3, 0.4), ("entity.skeleton_horse.ambient", 1.0, 0.5), ("entity.blaze.burn", 1.0, 0.5)],
    "swing_light": [("entity.horse.jump", 0.9, 0.7), ("entity.player.attack.sweep", 0.6, 0.6)],
    "swing_heavy": [("entity.horse.gallop", 1.3, 0.6), ("entity.blaze.shoot", 0.7, 0.6)],
    "cast": [("entity.blaze.shoot", 1.0, 0.6), ("item.firecharge.use", 0.9, 0.7)],
    "impact": [("entity.horse.land", 1.3, 0.5), ("entity.player.attack.strong", 0.9, 0.6), ("entity.generic.burn", 0.6, 0.7)],
    "roar": [("entity.horse.death", 1.2, 0.45), ("entity.skeleton_horse.death", 0.9, 0.5), ("entity.blaze.ambient", 0.8, 0.4)],
    "flare": [("entity.ghast.shoot", 1.0, 0.5), ("entity.blaze.shoot", 1.0, 0.4), ("block.fire.ambient", 1.2, 0.6)],
    "step": [("entity.horse.step", 0.4, 0.6)],
}
MOVE_SOUNDS = {"cinders": {"swing": "cast", "impact": ""}, "pyre": {"swing": "flare", "impact": ""}, "firefall": {"swing": "roar", "impact": ""},
               "flare": {"swing": "flare", "impact": ""}, "wildfire": {"swing": "roar"}, "arrive": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "arrive"},  # it lies burnt out until its fire catches, and gets up
    "phases": [
        {"pool": [
            {"move": "strike", "weight": 3, "when": "near", "then": [["trample"], ["trample", "charge"]]},
            {"move": "trample", "weight": 2, "when": "near"},
            {"move": "charge", "weight": 3, "when": "far", "then": [["strike"]]},
            {"move": "cinders", "weight": 3, "when": "far"},
            {"move": "pyre", "weight": 2},
            {"move": "buck", "weight": 3, "when": "behind"},
        ]},
        # it spreads: Firefall, Flare, Wildfire
        {"transition": "firefall", "pool": [
            {"move": "strike", "weight": 3, "when": "near", "then": [["trample"], ["trample", "flare"]]},
            {"move": "charge", "weight": 3, "when": "far", "then": [["strike", "trample"]]},
            {"move": "firefall", "weight": 2},
            {"move": "flare", "weight": 2},
            {"move": "cinders", "weight": 1, "when": "far"},
            {"move": "wildfire", "weight": 2},
            {"move": "buck", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.7, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("float", (0, 0, 0)), ("hoof0", HOOF_HIT), ("hoof2", HOOF_HIT), ("head", (0, -2, -22)), ("ear_l", (0, -6, 0)), ("tail2", (0, 0, 9))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
