"""The Long Drop (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Flier, the fear of falling. The Hand from Above,
picked 2026-10-29: "Lifts you up and lets you fall."

A hand as big as a cart, pale as something that has never seen daylight, hanging down out of the dark on an arm that goes
up and up into it and never ends. Its fingers are too long, a third again as long as a hand's should be, with knuckles
like knots and long yellowed nails. It hangs with its fingers down and spread a little, the way a hand comes down to
pick something up, and it is always moving them: drumming, flexing, curling one at a time. On its palm the lines of
it glow faint violet in the dark, the lines a fortune teller reads.

Moves (SPEC §35.11): the Backhand and the Clutch (LIGHT, a string: it swings out and backhands you with the backs of its
fingers, then spreads them wide and snatches at you, closing into a fist); Fall With Me (HEAVY, its gap closer: it rises
high with its fingers spread at you like a falling spider, holds, and dives on you, slamming flat on the floor); Lift
(it reaches down under you with its fingers spread, closes them round you like a cage and lifts, and lets go: its
fingers spring open); No Bottom (into the second phase: it closes into a fist and trembles, then opens its hand wide
over you, palm down, its fingers stiff and shaking, and the dark pours out of it); for whoever is behind it, the Flick
(it curls its middle finger against its thumb and flicks it out behind it); and the Long Drop (the signature: it rises
up into the dark until only its fingertips hang down out of it, curled into a fist, trembling, and falls like a stone,
opening as it comes, and slams flat on the floor). Its intro: it lies on the floor palm down as if it had been dropped
there; its fingers twitch one at a time; it pushes itself up on its fingertips like something standing, lifts off the
floor, turns to you and beckons. Broken, it falls to the floor palm down, its fingers limp. Its death: its fingers claw
at the air, it clenches into a fist and slowly opens, goes limp, and is drawn back up into the dark it came from.
"""
import math

from kit import core, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "nightmare_falling"
SCALE = 2.4  # the def's scale; the body is modelled at true size and the hitbox set round its hand

# The Hand from Above (docs/bosses/ROLLOUT.md, A). Its palm 22 px across and 24 long, its middle finger 34 px (2.1 blocks):
# from its wrist to its fingertips 3.6 blocks. Its wrist hangs 58 px up (3.6 blocks), its fingertips just clear of the
# floor, and its arm goes up from there and back into the dark, 4 blocks of it showing. Value story: its nails and the
# knots of its knuckles brightest, then the backs of its hand and fingers, then its palm in their shadow and the creases
# of its joints; its arm darkest, going from the pale of its wrist to black up in the dark. Only the lines of its palm
# glow.
HOVER = 58.0
FLOAT_Z = 4.0
HAND_TILT = -40.0  # its hand hangs with its fingers down and forward, its palm turned down and back toward its arm
ARM_LEAN = -38.0  # its forearm goes up and back from its wrist
# its four fingers along the bottom of its palm, from its left (the thumb's side) to its right: (x, thickness, the
# three pieces' lengths); the middle the longest, the little finger the shortest
FINGERS = ((7.5, 5, (12, 10, 9)), (2.5, 5, (13, 11, 10)), (-2.5, 5, (12, 10, 9)), (-7.5, 4, (10, 8, 7)))
SIDE = (1.0, 0.35, -0.35, -1.0)  # how far out to each side each finger fans when it spreads
REST_CURL = (8.0, 16.0, 12.0)  # its fingers' natural bend at rest, at each knuckle
THUMB = ((9, 6), (8, 5), (7, 4))  # (length, thickness)
NAIL = 3  # px its nails run on past its fingertips
FOREARM = ((2, 16, 15, 10), (18, 14, 16, 11))  # (from, length, width, depth) up its forearm
UPPER = ((0, 14, 16, 11), (14, 10, 15, 10))  # and up its arm past the elbow, into the dark
ARM_LEN = 32 + 24  # px of arm that shows
ONE = (1.0, 1.0, 1.0)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, tex=(256, 256))
    base = r.bone("base", (0, 24, 0))
    fl = _bone(base, "float", 0, HOVER, FLOAT_Z)  # its wrist
    # the hand, built hanging straight down with its palm to the back (+z) and the back of its hand to the front
    hd = _bone(fl, "hand", 0, 0, 0, (HAND_TILT, 0, 0))
    _box(hd, "palm", 0, -24, 0, 22, 22, 6)
    _box(hd, "palm_edge", -11.5, -22, 0, 1, 16, 5)  # the fleshy outside edge of its hand
    _box(hd, "wrist", 0, -4, 0, 18, 5, 7)
    _box(hd, "ulna", -8, -3, -1.5, 3, 3, 3)  # the knob of bone at its wrist
    _box(hd, "pad_thumb", 6, -15, 3.5, 8, 11, 1)  # the pads of its palm
    _box(hd, "pad_heel", -5, -16, 3.5, 9, 12, 1)
    _box(hd, "pad_top", 0, -24, 3.5, 20, 4, 1)
    for k, (x, w, _) in enumerate(FINGERS):
        _box(hd, f"tendon{k}", x * 0.8, -21, -3.5, 1, 15, 1)  # the cords on the back of its hand
        _box(hd, f"knuckle{k}", x, -26, -3.5, w, 4, 2)
    for i, x in enumerate((4, -4)):
        _box(hd, f"vein{i}", x, -19 + i, -3.5, 1, 10 - i, 1)  # two veins raised across the back of its hand
    # its fingers: three pieces each, each knuckle a knot on the back, its long nails on the backs of their ends
    for k, (x, w, lens) in enumerate(FINGERS):
        b = _bone(hd, f"f{k}a", x, -24, 0, (REST_CURL[0], 0, 0))
        for s, ln in enumerate(lens):
            seg = "abc"[s]
            ww = w if s == 0 else w - 1
            _box(b, f"f{k}{seg}", 0, -ln, 0, ww, ln, ww)
            if s > 0:
                _box(b, f"knot{k}{seg}", 0, -1, -ww / 2 - 0.5, max(2, ww - 2), 2, 1)
            if s == 2:
                _box(b, f"nail{k}", 0, -(ln + NAIL), -ww / 2 - 0.5, ww - 1, ln // 2 + NAIL, 1)
                _box(b, f"hook{k}", 0, -(ln + NAIL), -ww / 2 + 0.5, max(1, ww - 2), 1, 1)  # its nail's end hooks in
                _box(b, f"pad{k}", 0, -ln, ww / 2 + 0.5, ww - 2, 3, 1)  # the pad at its fingertip
            else:
                _box(b, f"fpad{k}{seg}", 0, -ln + 1, ww / 2 + 0.5, ww - 2, ln - 3, 1)  # the pads on its palm side
                b = b.bone(f"f{k}{'abc'[s + 1]}", (0, ln, 0), (REST_CURL[s + 1], 0, 0))
    # its thumb, from low on its palm's left edge, turned out and toward the palm
    t = _bone(hd, "t0", 10, -8, 1.5, (18, 0, -38))
    for s, (ln, w) in enumerate(THUMB):
        _box(t, f"t{s}", 0, -ln, 0, w, ln, w)
        if s == 2:
            _box(t, "nail_t", 0, -(ln + NAIL), -w / 2 - 0.5, w - 1, ln // 2 + NAIL, 1)
        else:
            if s == 1:
                _box(t, "knot_t", 0, -2, -w / 2 - 0.5, w, 3, 1)
            t = t.bone(f"t{s + 1}", (0, ln, 0), ((10, 0, 22), (8, 0, 12))[s])
    # its arm: up from its wrist and back into the dark, the elbow a little way up
    fo = _bone(fl, "fore", 0, 0, 0, (ARM_LEAN, 0, 0))
    for i, (y0, h, w, d) in enumerate(FOREARM):
        _box(fo, f"fore{i}", 0, y0, 0, w, h, d)
    _box(fo, "fore_cord", 4, 4, -5.5, 2, 20, 1)  # its forearm's tendons standing out
    _box(fo, "fore_cord2", -3, 6, -5.5, 2, 16, 1)
    _box(fo, "fore_cord3", 0, 4, 5.5, 3, 18, 1)  # and the cord inside its wrist
    _box(fo, "elbow", 0, 27, 5.5, 10, 6, 2)
    up_ = fo.bone("upper", (0, -32, 0), (10, 0, 0))
    for i, (y0, h, w, d) in enumerate(UPPER):
        _box(up_, f"upper{i}", 0, y0, 0, w, h, d)
    r.look = [("hand", 0.3, 0.25)]
    r.shadow = 1.8
    r.size = 1.0
    # secondary parts (SPEC §35.10): its fingertips and the tip of its thumb trail
    r.springs = [(f"f{k}c", 0.45, 2.4, 0.14) for k in range(4)] + [("t2", 0.35, 2.4, 0.14)]
    r.guard = [f"f{k}c" for k in range(4)] + ["t2", "hand"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#221d27", "#2f2935", "#3e3745", "#4f4757", "#625a6a", "#787081", "#8e8697", "#a59eac", "#bcb6c2", "#d3ced8", "#e9e6ec")
VEIN = Ramp("#232a3b", "#2f3850", "#3e4966", "#505d7c")
NAIL_ = Ramp("#2e271d", "#4a3f2f", "#6c5e46", "#938262", "#b8a882", "#d8cdad", "#efe8d4")
BRUISE = Ramp("#2a2134", "#3a2e48", "#4d3e5f", "#635278", "#7b6890", "#9483a6")
GLOW = ("#e6d4ff", "#b996ff", "#8a63d6", "#5b3e96")


def _skin(f, base, seed, creases=()):
    """Dead skin: faint mottling, the odd darker fleck, and creases across it at the rows given (0 to 1 down the face)."""
    for i, j in f.each():
        n = math.sin(i * 1.3 + seed) * math.sin(j * 0.9 + seed * 1.7) * 0.35 + 0.2 * math.sin((i + 2 * j) * 0.7 + seed)
        v = base + n
        if (i * 31 + j * 57 + seed * 11) % 37 == 0:
            v -= 1.2
        for at in creases:
            if abs(j - at * (f.h - 1)) < 0.6:
                v -= 2.0 if (i + seed) % 4 else 1.2
        f.set(i, j, SKIN.at(v))


def _veins(f, seed, n=2, base=None):
    """Veins wandering down a face, blue-grey under the skin."""
    for k in range(n):
        x0 = (seed * 5 + k * 7) % max(1, f.w)
        for j in range(f.h):
            i = int(round(x0 + 1.4 * math.sin(j * 0.45 + seed + k * 2.0)))
            if 0 <= i < f.w:
                f.set(i, j, VEIN.at(2.0 + 0.6 * math.sin(j * 0.8 + k)))


def paint(r, P_):
    """Pale dead-grey skin with a cold violet cast, faint mottling and darker creases at every knuckle; the knots of
    its knuckles and its fingertips paler, its long nails yellowed horn, darker at their roots; veins on the back of its
    hand and up its forearm; its palm a shade darker in its own shadow, its lines glowing faint violet; its arm going from
    the pale of its wrist to black up in the dark."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in ["palm", "palm_edge", "wrist", "ulna"] + of("pad_", "tendon", "knuckle"):
        P_.paint(c, SKIN, 6.4, None, grad=1.2, rim=1.0, ao=1.0, noise=0.15, nscale=2)
    for c in ["palm", "wrist", "palm_edge"]:
        for fname in SIDES + ("top", "bottom"):
            _skin(P_.face(c, fname), 6.2, len(c) + len(fname), creases=(0.2,) if c == "wrist" else ())
    f = P_.face("palm", "north")  # the back of its hand: paler, its veins
    _skin(f, 6.8, 3)
    _veins(f, 2, 3)
    # its palm: a shade darker, its pads paler, and its lines glowing
    f, gl = P_.face("palm", "south"), P_.face("palm", "south", glow=True)
    _skin(f, 5.4, 5)
    lines = []
    for i in range(f.w):  # the heart line high across it, the head line across the middle, the life line round the thumb
        lines.append((i, int(round(0.28 * f.h + 2.2 * math.sin(i / f.w * math.pi)))))
        if 0.15 * f.w <= i <= 0.85 * f.w:
            lines.append((i, int(round(0.5 * f.h + 0.12 * (i - f.w / 2)))))
    for j in range(int(0.35 * f.h), f.h):
        lines.append((int(round(0.72 * f.w - 3.5 * math.sin((j - 0.35 * f.h) / (0.65 * f.h) * math.pi))), j))
    for i, j in lines:
        if 0 <= i < f.w and 0 <= j < f.h:
            f.set(i, j, hexc(GLOW[2], 255))
            gl.set(i, j, hexc(GLOW[1], 190))
    for c in of("pad_"):
        for fname in SIDES + ("top", "bottom", "south"):
            _skin(P_.face(c, fname), 6.0, len(c))
    for c in of("knuckle"):
        for fname in ("north", "top", "bottom", "east", "west"):
            _skin(P_.face(c, fname), 7.8, len(c) + len(fname))
    for c in of("tendon"):
        for fname in SIDES + ("top", "bottom"):
            _skin(P_.face(c, fname), 7.2, len(c))
    # its fingers: creases at each end, paler on their backs, darker on their palm side, pale pads at their tips
    for c in of("f0", "f1", "f2", "f3", "t0", "t1", "t2"):
        P_.paint(c, SKIN, 6.4, None, grad=1.4, rim=1.0, ao=1.0, noise=0.15, nscale=2)
        k = len(c) * 7 + ord(c[-1])
        tip = c[0] == "f" and c[-1] == "c" or c == "t2"
        for fname in ("north", "east", "west", "south"):
            f = P_.face(c, fname)
            _skin(f, {"north": 6.9, "south": 5.4}.get(fname, 6.3), k + len(fname), creases=(0.08, 0.5, 0.92) if fname == "south" else ())
            if tip:  # its fingertips going the violet of a dead hand's
                for i, j in f.each():
                    u = j / max(1, f.h - 1)
                    if u > 0.35:
                        f.set(i, j, BRUISE.at(4.2 - 3.0 * (u - 0.35) + 0.3 * math.sin(i * 1.7 + j)))
        _skin(P_.face(c, "bottom"), 7.4, k)
        if tip:
            f = P_.face(c, "bottom")
            for i, j in f.each():
                f.set(i, j, BRUISE.at(2.4))
    for c in of("knot", "pad", "fpad", "vein"):
        P_.paint(c, SKIN, 7.6, None, grad=0.8, rim=1.0, ao=0.6, noise=0.1, nscale=2)
        for fname in SIDES + ("top", "bottom"):
            _skin(P_.face(c, fname), 7.4 if c.startswith("knot") else 6.8, len(c) + ord(c[-1]))
    # its nails: yellowed horn, ridged, dark at the root and pale at the tip
    # the lines of its palm, which lie over its pads: the heart line across the top, the head line across the heel, the
    # life line round the ball of its thumb
    for c, line in (("pad_top", lambda i, w, h: int(round(1.5 + 0.9 * math.sin(i / w * math.pi * 1.5)))),
                    ("pad_heel", lambda i, w, h: int(round(1 + (h - 3) * i / max(1, w - 1)))),
                    ("pad_thumb", lambda i, w, h: None)):
        f, gl = P_.face(c, "south"), P_.face(c, "south", glow=True)
        pts = [(i, line(i, f.w, f.h)) for i in range(f.w)] if c != "pad_thumb" else \
              [(int(round(1 + 2.5 * math.sin(j / max(1, f.h - 1) * math.pi))), j) for j in range(f.h)]
        for i, j in pts:
            if 0 <= i < f.w and 0 <= j < f.h:
                f.set(i, j, hexc(GLOW[2], 255))
                gl.set(i, j, hexc(GLOW[1], 200))
    for c in of("nail", "hook"):
        P_.paint(c, NAIL_, 4.0, None, grad=1.0, rim=1.2, ao=0.4, noise=0.1, nscale=2)
        for fname in SIDES + ("top", "bottom"):
            f = P_.face(c, fname)
            for i, j in f.each():
                v = 1.2 + 4.8 * (j / max(1, f.h - 1)) - (0.6 if i % 2 == 0 and fname == "north" else 0.0)
                f.set(i, j, NAIL_.at(v))
    # its arm: pale at its wrist to black up in the dark, its veins and cords showing until the dark takes them
    for c in of("fore", "upper", "elbow"):
        P_.paint(c, SKIN, 5.0, None, grad=1.0, rim=0.8, ao=0.8, noise=0.15, nscale=2)
    spans = {"fore0": (2, 16), "fore1": (18, 14), "fore_cord": (4, 20), "fore_cord2": (6, 16), "elbow": (27, 6), "upper0": (32, 14), "upper1": (46, 10), "fore_cord3": (4, 18)}
    for c, (y0, h) in spans.items():
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                at = y0 + h * (1 - (j + 0.5) / f.h)  # px up its arm
                u = at / ARM_LEN
                n = 0.25 * math.sin(i * 1.1 + j * 0.7 + len(c))
                v = 6.4 - 7.6 * u ** 0.8 + n
                if c.startswith("fore_cord"):
                    v += 0.8
                if v < 0.2:
                    f.set(i, j, hexc("#050407", 255))
                else:
                    f.set(i, j, SKIN.at(v))
            if c in ("fore0", "fore1") and fname == "north":
                for k in range(2):
                    x0 = 3 + 6 * k
                    for j in range(f.h):
                        at = y0 + h * (1 - (j + 0.5) / f.h)
                        if at / ARM_LEN < 0.55:
                            i = int(round(x0 + 1.2 * math.sin(j * 0.5 + k * 2)))
                            if 0 <= i < f.w:
                                f.set(i, j, VEIN.at(2.2 - 2 * at / ARM_LEN))
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, hexc("#050407", 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion: one pose call (where it hangs, its wrist, its arm, its fingers and its thumb)

FINGER_LAG = 0.9  # radians each finger trails the one before it in a ripple across them
HAND = ("hand",)  # what lies on the floor when it does
TIPS_ON = ("f0c", "f1c", "f2c", "f3c")  # what it stands on when it gets up on its fingertips


def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _fingers(p, curl=0.0, claw=0.0, spread=0.0, wave=0.0, ph=0.0, each=None):
    """Its four fingers: curl closes them toward its palm (+; - straightens them past their rest, stiff), claw bends
    them back at the root and in at the ends (a claw), spread fans them apart (+), wave runs a ripple across them from
    the index finger to the little finger (its amplitude, at phase ph), each {finger: (a, b, c) curls} adds to one."""
    for k in range(4):
        q = ph - FINGER_LAG * k
        w = wave * math.sin(q)
        segs = [(0.7 * curl - claw + 0.5 * w, 0.0, -spread * SIDE[k]),
                (curl + 1.2 * claw + w, 0.0, -0.25 * spread * SIDE[k]),
                (0.9 * curl + claw + 0.8 * wave * math.sin(q - 0.7), 0.0, 0.0)]
        if each and k in each:
            segs = [(a[0] + e, a[1], a[2]) for a, e in zip(segs, each[k])]
        for s, v in enumerate(segs):
            p[f"f{k}{'abc'[s]}"] = v


def _thumb(p, curl=0.0, out=0.0):
    """Its thumb: curl closes it across its palm toward its fingers (+), out turns it away from its hand (+)."""
    p["t0"] = (0.3 * curl, 0.0, 0.45 * curl - out)
    p["t1"] = (0.2 * curl, 0.0, 0.7 * curl)
    p["t2"] = (0.15 * curl, 0.0, 0.6 * curl)


def _pose(lift=0.0, shift=(0.0, 0.0), pitch=0.0, yaw=0.0, roll=0.0, wrist=(0, 0, 0), arm=(0, 0, 0), fingers=None, thumb=None):
    """lift raises it (+, px) and shift moves it (x to its left, z back); pitch turns it round its wrist (+ its
    fingers swing back and under, - forward and up at you), yaw turns it (+ to its right), roll rolls it (+ its left
    side down); wrist bends its hand at the wrist (+ curling it under toward its palm, - back, its fingers pointing at
    you; a twist, + turning its palm to its right; a bend to the side); arm (a lean forward, + leaning its arm forward;
    a lean to its left; a bend at the elbow, + forward) on top of the arm's own turn back against pitch and roll, so
    it keeps reaching up into the dark."""
    p = {"float": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])}, "hand": tuple(wrist),
         "fore": (arm[0] - 0.8 * pitch, 0.0, arm[1] - 0.8 * roll), "upper": (arm[2] - 0.15 * pitch, 0.0, -0.15 * roll)}
    _fingers(p, **(fingers or {}))
    _thumb(p, **(thumb or {}))
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


def _on(base, p):
    """p, hanging where base hangs."""
    q = dict(p)
    q["float"] = base["float"]
    return q


def _floor(r, p, gap=0.3, on=HAND):
    """The pose let down (or lifted) until its hand (or the bones on) just rests on the floor (its fingers left to the
    ground pass)."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, on) - (core.GROUND - gap)
    q = dict(p)
    f = q["float"]
    q["float"] = {"rot": f["rot"], "pos": (f["pos"][0], f["pos"][1] + up, f["pos"][2])}
    return q


def _staged(mv, stage, f):
    W, A = mv.seconds("windup"), mv.seconds("active")
    return {"windup": 0.0, "active": W, "recover": W + A}[stage] + f * mv.seconds(stage)


def _scale(r, clip, bone, pts, mode="cat"):
    """A clip's scale keys for one bone: [(seconds, (sx, sy, sz))], 1 at both ends."""
    c = r.clips[clip]
    ks = {0.0: ONE, round(c.length, 4): ONE}
    ks.update((round(t, 4), v) for t, v in pts)
    c.scale(bone, [(t, *v) for t, v in sorted(ks.items())], mode)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It hangs in the dark (8 s), swaying a little on its arm like something hung up, its fingers never still: a slow
    ripple runs across them like drumming on a table, twice; it turns its palm to you and back; its thumb rubs across
    the ends of its fingers; it curls slowly half into a fist and lets go; its index finger taps once."""
    L, n = 8.0, 160
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        show, rub, clench, tap = _wave(u, 0.3, 0.24), _wave(u, 0.52, 0.16), _wave(u, 0.72, 0.2), _wave(u, 0.9, 0.08)
        drum = _wave(u, 0.12, 0.2) + _wave(u, 0.6, 0.2)
        keys.append((L * u, _pose(lift=1.2 * math.sin(w) + 1.0 * clench, shift=(1.5 * math.sin(w + 0.5), 0.6 * math.sin(2 * w)),
                                  pitch=1.5 * math.sin(w + 0.7) - 3 * clench, yaw=3 * math.sin(w) + 4 * show, roll=2.2 * math.sin(w + 1.6),
                                  wrist=(-2 * show + 3 * clench + 1.5 * math.sin(2 * w + 0.4), 34 * show, 2 * math.sin(w + 2.0)), arm=(0, 1.0 * math.sin(w), 0),
                                  fingers=dict(curl=3 * math.sin(2 * w) + 40 * clench + 10 * rub, spread=4 + 3 * math.sin(w + 1.0) - 3 * clench,
                                               wave=4 + 10 * drum, ph=2 * math.pi * 6 * u,
                                               each={0: (0, -14 * tap, -10 * tap)}),
                                  thumb=dict(curl=6 * math.sin(w) + 34 * rub + 30 * clench, out=-4 * rub))))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """Always on top of the idle, faster as it goes: its fingers rippling and its tips trailing. It glides as if drawn
    along by its arm, its fingers trailing back behind it; it races with them streaming out behind and spread."""
    L, n = 1.0, 20
    keyed(r, "fly", L, [(L * k / n, _pose(roll=1.0 * math.sin(2 * math.pi * k / n), fingers=dict(wave=3, ph=2 * math.pi * 2 * k / n),
                                          thumb=dict(curl=2 * math.sin(2 * math.pi * k / n + 1.0)))) for k in range(n + 1)], loop=True)
    L, n = 1.4, 28
    keys = []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        keys.append((L * u, _pose(lift=0.8 * s, pitch=10 + 1.5 * math.sin(2 * math.pi * u + 1.0), roll=1.5 * math.sin(2 * math.pi * u + 0.4),
                                  wrist=(4, 0, 1.5 * s), arm=(4, 0.8 * s, 0), fingers=dict(curl=6, spread=-2, wave=6, ph=2 * math.pi * 2 * u),
                                  thumb=dict(curl=6))))
    keyed(r, "walk", L, keys, loop=True)
    L, n = 0.9, 18
    keys = []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        keys.append((L * u, _pose(lift=-1.0 + 1.2 * s, pitch=20 + 2 * math.sin(2 * math.pi * u + 1.0), roll=2 * math.sin(2 * math.pi * u + 0.6),
                                  wrist=(8, 0, 2 * s), arm=(8, 1.2 * s, 2), fingers=dict(curl=-6, spread=10, claw=4, wave=8, ph=2 * math.pi * 2 * u),
                                  thumb=dict(curl=-4, out=6))))
    keyed(r, "run", L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.6, 2.8
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # turning to its left, its fingers sweep round after it
        keys = []
        n = 20
        for k in range(n + 1):
            u = k / n
            keys.append((1.0 * u, _pose(yaw=-6 * sg, roll=4 * sg, wrist=(0, -8 * sg, 3 * sg), fingers=dict(curl=4, wave=4, ph=2 * math.pi * 2 * u,
                                                                                                         spread=2), thumb=dict(curl=4))))
        keyed(r, nm, 1.0, keys, loop=True)


def _lying(r, slack=1.0):
    """Dropped on the floor, broken: palm down, its fingers lying out flat and limp, its thumb slack."""
    p = _pose(wrist=(-50 * slack, 6 * slack, 4 * slack), arm=(-10 * slack, 0, 8 * slack), fingers=dict(curl=-8 * slack, spread=10 * slack),
              thumb=dict(curl=-6, out=10 * slack))
    return _floor(r, p)


def _reactions(r):
    """Struck, it jerks back with its fingers flung out stiff and splayed; from the side, it jerks away from the blow.
    Broken, it drops to the floor palm down, its fingers limp; it pushes itself up on its fingertips and lifts away."""
    hit = _pose(lift=2, shift=(0, 5), pitch=-10, wrist=(-14, 0, 0), arm=(-4, 0, 0), fingers=dict(curl=-22, spread=24), thumb=dict(curl=-10, out=14))
    back = _pose(shift=(0, 1), pitch=2, wrist=(4, 0, 0), fingers=dict(curl=10, spread=2), thumb=dict(curl=8))
    keyed(r, "hurt", 0.6, [(0, REST), (0.08, hit), (0.32, back), (0.6, REST)])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left, it jerks away to its right
        h = _pose(lift=1, shift=(-5 * sg, 2), yaw=10 * sg, roll=-12 * sg, wrist=(-6, 10 * sg, -10 * sg), fingers=dict(curl=-16, spread=18),
                  thumb=dict(curl=-8, out=10))
        b = _pose(shift=(-1 * sg, 0.5), roll=3 * sg, wrist=(2, -3 * sg, 2 * sg), fingers=dict(curl=8), thumb=dict(curl=6))
        keyed(r, nm, 0.65, [(0, REST), (0.08, h), (0.34, b), (0.65, REST)])
    low = _lying(r)
    flinch = _pose(lift=3, pitch=-6, wrist=(-12, 0, 0), fingers=dict(curl=-20, spread=22), thumb=dict(curl=-8, out=12))
    keyed(r, "kneel", 1.0, [(0, REST), (0.16, flinch), (0.55, _mix(flinch, low, 0.8)), (0.78, _mix(flinch, low, 0.97)), (1.0, low)])
    # it gets up on its fingertips, its palm lifting off the floor, and lifts away
    perch = _floor(r, _pose(wrist=(-44, 2, 0), arm=(-6, 0, 4), fingers=dict(curl=44, claw=6, spread=14), thumb=dict(curl=24, out=6)), gap=1.0, on=TIPS_ON)
    up_ = _pose(lift=3, pitch=-2, wrist=(4, 0, 0), fingers=dict(curl=16, spread=4, claw=6), thumb=dict(curl=10))
    keyed(r, "rise", 1.2, [(0, low), (0.35, perch), (0.75, up_), (0.98, _mix(up_, REST, 0.7)), (1.2, REST)])
    _death(r)


def _death(r):
    """Its death: its fingers claw at the air, stiff and shaking; it clenches into a fist, trembling, and slowly opens,
    its fingers going limp; and it is drawn back up into the dark it came from, hanging open, until it is gone."""
    keys = []
    claw = lambda q: _pose(lift=2, shift=(0.6 * q, 3), pitch=-8, yaw=3 * q, roll=4 * q, wrist=(-16, 4 * q, 3 * q),
                           fingers=dict(curl=-10, claw=34 + 6 * q, spread=26, wave=10, ph=q * 2.0), thumb=dict(curl=-6, out=16))
    keys += [(0, REST), (0.25, claw(1)), (0.5, claw(-1)), (0.75, claw(1)), (1.0, claw(-0.6))]
    fist = lambda q: _pose(lift=1, pitch=-2, yaw=q, roll=1.5 * q, wrist=(10, 2 * q, 0), arm=(0, 0, 0),
                           fingers=dict(curl=82, spread=-4, wave=3 * abs(q), ph=q), thumb=dict(curl=50))
    keys += [(1.4, fist(1)), (1.6, fist(-1)), (1.8, fist(0.7)), (2.0, fist(-0.4))]
    limp = lambda lift: _pose(lift=lift, pitch=4, wrist=(14, 0, 4), arm=(2, 0, 0), fingers=dict(curl=-4, spread=6), thumb=dict(curl=0, out=4))
    keys += [(2.6, _mix(fist(0), limp(0), 0.7)), (3.0, limp(1)), (3.6, limp(10)), (4.3, limp(32)), (5.0, limp(56))]
    keyed(r, "death", 5.0, keys)
    _scale(r, "death", "float", [(3.0, ONE), (3.6, (0.96, 0.96, 0.96)), (4.3, (0.6, 0.6, 0.6)), (4.99, (0.06, 0.06, 0.06)), (5.0, (0.06, 0.06, 0.06))], "lin")


# ---------------------------------------------------------------------------------------------------------------------
# Moves

TIPS = [(f"f{k}c", (0, FINGERS[k][2][2] + NAIL, 0)) for k in range(4)]
PALM = ("hand", (0, 12, 0))


def _back(end):
    return [(0.45, _mix(end, REST, 0.45)), (0.72, _mix(end, REST, 0.82))]


def swat(r):
    """The Backhand (LIGHT): it swings out to its left, turning its back to you and drawing its fingers up and in;
    then it swings back across, the backs of its fingers leading and lashing out, its nails raking across you."""
    cock = _pose(lift=2, shift=(10, 3), pitch=-6, yaw=-28, roll=10, wrist=(-14, -24, 10), arm=(-4, 6, 0), fingers=dict(curl=22, claw=8, spread=-2),
                 thumb=dict(curl=18))
    sweep = _pose(lift=-1, shift=(0, -3), pitch=-8, yaw=4, roll=-4, wrist=(-20, 8, -14), arm=(2, 0, 0), fingers=dict(curl=8, claw=12, spread=16),
                  thumb=dict(curl=8, out=8))
    hit = _pose(lift=-2, shift=(-10, -4), pitch=-6, yaw=30, roll=-12, wrist=(-14, 28, -16), arm=(2, -6, 0), fingers=dict(curl=18, claw=16, spread=10),
                thumb=dict(curl=14, out=4))
    held = _pose(lift=-2.2, shift=(-10.5, -4), pitch=-6, yaw=31, roll=-12.5, wrist=(-14, 29, -16), arm=(2, -6, 0), fingers=dict(curl=20, claw=17, spread=10),
                 thumb=dict(curl=15, out=4))
    return Move("swat", "LIGHT", 10, 5, 14, through=held, title="Backhand", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.0, knock=0.9, plant=(),
                hit=[(b, p, 6.0) for b, p in TIPS] + [("hand", (0, 22, -3), 6.0)],
                stages={"windup": [(0.2, _mix(REST, cock, 0.6)), (0.6, _mix(REST, cock, 0.9)), (1.0, cock)],
                        "active": [(0.4, sweep), (1.0, hit)], "recover": _back(held)})


def clutch(r):
    """The Clutch (LIGHT, after the Backhand): it draws back and up with its fingers flung wide open, pointing at you,
    and snatches down at you, closing into a fist as it comes."""
    open_ = _pose(lift=4, shift=(0, 6), pitch=-14, wrist=(-30, 0, 0), arm=(-6, 0, 0), fingers=dict(curl=-16, claw=26, spread=26), thumb=dict(curl=-8, out=18))
    reach = _pose(lift=-4, shift=(0, -8), pitch=-4, wrist=(-14, 0, 0), arm=(4, 0, 0), fingers=dict(curl=10, claw=22, spread=20), thumb=dict(curl=6, out=10))
    shut = _pose(lift=-7, shift=(0, -11), pitch=4, wrist=(-4, 0, 0), arm=(6, 0, 0), fingers=dict(curl=78, claw=4, spread=-4), thumb=dict(curl=52))
    held = _pose(lift=-7.2, shift=(0, -11), pitch=4.5, wrist=(-4, 0, 0), arm=(6, 0, 0), fingers=dict(curl=82, claw=4, spread=-4), thumb=dict(curl=54))
    return Move("clutch", "LIGHT", 10, 5, 14, through=held, title="Clutch", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.1, knock=0.5, plant=(),
                hit=[(b, p, 6.0) for b, p in TIPS] + [(PALM[0], PALM[1], 9.0)],
                stages={"windup": [(0.2, _mix(REST, open_, 0.6)), (0.6, _mix(REST, open_, 0.9)), (1.0, open_)],
                        "active": [(0.45, reach), (1.0, shut)], "recover": _back(held)})


ADVANCE = 8.0  # blocks Fall With Me carries it


def fall(r):
    """Fall With Me (HEAVY, its gap closer): it rises high, its hand turning until its fingers point down at you, spread
    wide like a spider dropping on its thread, and holds; then it dives on you, and slams flat on the floor palm down
    with its fingers splayed, and stays a moment before it lifts."""
    high = _pose(lift=22, shift=(0, 8), pitch=-18, wrist=(-34, 0, 0), arm=(-8, 0, 4), fingers=dict(curl=-6, claw=20, spread=28), thumb=dict(curl=-6, out=18))
    aim = _pose(lift=24, shift=(0, 9), pitch=-20, wrist=(-38, 0, 0), arm=(-8, 0, 4), fingers=dict(curl=-8, claw=24, spread=30), thumb=dict(curl=-8, out=20))
    dive = _pose(lift=6, shift=(0, -8), pitch=-12, wrist=(-46, 0, 0), arm=(6, 0, 0), fingers=dict(curl=-10, claw=12, spread=26), thumb=dict(curl=-6, out=16))
    flat = _floor(r, _pose(shift=(0, -14), pitch=0, wrist=(-50, 0, 0), arm=(10, 0, 8), fingers=dict(curl=-6, claw=6, spread=24), thumb=dict(curl=-4, out=16)),
                  gap=0.5)
    held = _on(flat, _pose(wrist=(-50, 0, 0), arm=(10, 0, 8), fingers=dict(curl=-2, claw=10, spread=22), thumb=dict(curl=-2, out=14)))
    lift_ = _pose(lift=4, shift=(0, -6), pitch=-2, wrist=(-14, 0, 0), arm=(4, 0, 0), fingers=dict(curl=14, spread=8), thumb=dict(curl=8))
    return Move("fall", "HEAVY", 18, 10, 22, through=held, title="Fall With Me", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 14), kinds=("gap",),
                ability="Fall With Me", damage=1.4, knock=1.2, plant=(), hit=[(PALM[0], PALM[1], 12.0)] + [(b, p, 6.0) for b, p in TIPS],
                stages={"windup": [(0.2, _mix(REST, high, 0.6)), (0.6, high), (1.0, aim)], "active": [(0.35, dive), (0.7, flat), (1.0, held)],
                        "recover": [(0.35, _mix(held, lift_, 0.5)), (0.7, _mix(lift_, REST, 0.75))]})


def lift(r):
    """Lift: it reaches down in front of it with its fingers spread wide, closes them round you like a cage, slowly,
    and lifts, its fingers trembling with the weight; and lets go, its fingers springing open."""
    down = _pose(lift=-10, shift=(0, -8), pitch=-10, wrist=(-18, 0, 0), arm=(6, 0, 0), fingers=dict(curl=-8, claw=18, spread=26), thumb=dict(curl=-4, out=18))
    act = []
    n = 14
    for i in range(1, n + 1):
        u = i / n
        c = _ease(min(1.0, u / 0.4))  # closing
        up = _ease(max(0.0, (u - 0.3) / 0.5))  # lifting
        q = math.sin(i * 2.6) * up
        act.append((u * 0.85, _pose(lift=-10 + 18 * up, shift=(0.4 * q, -8 + 2 * up), pitch=-10 + 6 * c, roll=1.2 * q, wrist=(-18 + 10 * c, 0, 0.8 * q),
                                    arm=(6 - 4 * up, 0, 0), fingers=dict(curl=-8 + 46 * c, claw=18 + 4 * c, spread=26 - 22 * c, wave=2.5 * up, ph=i * 2.1),
                                    thumb=dict(curl=-4 + 34 * c, out=18 - 12 * c))))
    let_go = _pose(lift=10, shift=(0, -5), pitch=-12, wrist=(-16, 0, 0), arm=(2, 0, 0), fingers=dict(curl=-24, claw=10, spread=30), thumb=dict(curl=-12, out=20))
    act.append((1.0, let_go))
    end = _mix(let_go, REST, 0.25)
    return Move("lift", "NONE", 14, 32, 16, through=end, title="Lift", track=6.0, ability="Lift", rng=(2, 14), kinds=("ranged", "area"), damage=0, knock=0,
                plant=(), stages={"windup": [(0.25, _mix(REST, down, 0.6)), (1.0, down)], "active": act, "recover": _back(end)})


def no_bottom(r):
    """No Bottom (into the second phase): it closes into a fist and trembles, harder and harder; then it rises and opens
    over you palm down, its fingers flung wide and stiff and shaking, and the dark pours out of it; it holds there."""
    w = []
    n = 12
    for i in range(1, n + 1):
        u = i / n
        f = _ease(min(1.0, u / 0.45))
        q = (-1) ** i * min(1.0, 0.3 + u)
        w.append((u, _pose(lift=2 * f, pitch=-2 * f + 0.8 * q * u, yaw=1.5 * q * u, roll=2 * q * u, wrist=(12 * f, 2 * q * u, 0),
                           fingers=dict(curl=84 * f, spread=-4 * f), thumb=dict(curl=54 * f))))
    wide = lambda q=0.0: _pose(lift=10, shift=(0, -4), pitch=-6 + q, yaw=q, roll=1.5 * q, wrist=(-48, 0, 0), arm=(4, 0, 2),
                               fingers=dict(curl=-26, spread=32, claw=6, wave=5 * abs(q), ph=q * 3), thumb=dict(curl=-14, out=24))
    act = [(0.18, wide()), (0.36, wide(1.4)), (0.54, wide(-1.4)), (0.72, wide(1.2)), (0.88, wide(-1.0)), (1.0, wide(0.4))]
    end = _mix(wide(), REST, 0.3)
    return Move("no_bottom", "NONE", 22, 30, 16, through=end, title="No Bottom", track=0.0, ability="No Bottom", rng=(0, 16), kinds=("area",), damage=0,
                knock=0, plant=(), stages={"windup": w, "active": act, "recover": _back(end)})


def flick(r):
    """The Flick (for whoever is behind it): it swings its hand back under itself, curls its middle finger tight
    against its thumb, and flicks it out behind it, hard."""
    cock = _pose(lift=6, shift=(0, 2), pitch=40, wrist=(30, 0, 0), arm=(4, 0, -4), fingers=dict(curl=40, spread=-2, each={1: (34, 30, 20)}),
                 thumb=dict(curl=58))
    snap = _pose(lift=6, shift=(0, 5), pitch=44, wrist=(24, 0, 0), arm=(4, 0, -4), fingers=dict(curl=34, spread=-2, each={1: (-52, -40, -24)}),
                 thumb=dict(curl=40))
    held = _pose(lift=6, shift=(0, 5.5), pitch=45, wrist=(24, 0, 0), arm=(4, 0, -4), fingers=dict(curl=35, spread=-2, each={1: (-54, -42, -26)}),
                 thumb=dict(curl=40))
    return Move("flick", "LIGHT", 10, 4, 14, through=held, title="Flick", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.0, knock=1.6, plant=(),
                hit=[(TIPS[1][0], TIPS[1][1], 8.0), ("f1b", (0, 6, 0), 6.0)],
                stages={"windup": [(0.2, _mix(REST, cock, 0.6)), (0.6, _mix(REST, cock, 0.9)), (1.0, cock)], "active": [(0.5, snap), (1.0, held)],
                        "recover": _back(held)})


HIGH = 34.0  # px the Long Drop lifts it up into the dark


def drop(r):
    """The Long Drop (the signature): it rises up into the dark until only its fingers hang down out of it, curls them
    into a fist and trembles, harder and harder; then it falls like a stone, opening as it comes, and slams flat on the
    floor palm down with its fingers splayed, and lies there a moment, its fingers twitching, before it lifts."""
    w = []
    n = 14
    for i in range(1, n + 1):
        u = i / n
        up = _ease(min(1.0, u / 0.55))
        q = (-1) ** i * min(1.0, 0.2 + u) * _ease(max(0.0, (u - 0.4) / 0.4))
        w.append((u, _pose(lift=HIGH * up + 0.6 * q, shift=(0.5 * q, 4 * up), pitch=-4 * up + q, roll=1.5 * q, wrist=(6 * up, 1.5 * q, 0),
                           arm=(-6 * up, 0, 6 * up), fingers=dict(curl=70 * up, spread=-3 * up, wave=2 * abs(q), ph=i), thumb=dict(curl=46 * up))))
    falling = _pose(lift=12, shift=(0, -2), pitch=-6, wrist=(-30, 0, 0), arm=(2, 0, 2), fingers=dict(curl=-14, claw=14, spread=28), thumb=dict(curl=-8, out=20))
    flat = _floor(r, _pose(shift=(0, -4), wrist=(-50, 0, 0), arm=(6, 0, 10), fingers=dict(curl=-8, claw=6, spread=30), thumb=dict(curl=-6, out=20)), gap=0.5)
    tw = lambda q: _on(flat, _pose(wrist=(-50, 0, 0), arm=(6, 0, 10), fingers=dict(curl=-8, claw=6, spread=30, each={0: (8 * q, 10 * q, 0), 3: (-6 * q, -8 * q, 0)}),
                                   thumb=dict(curl=-6, out=20)))
    act = [(0.4, falling), (0.7, flat), (0.85, tw(1)), (1.0, tw(-1))]
    lift_ = _pose(lift=3, shift=(0, -2), pitch=-2, wrist=(-14, 0, 0), arm=(2, 0, 2), fingers=dict(curl=12, spread=8), thumb=dict(curl=8))
    return Move("drop", "PERILOUS", 30, 14, 28, through=tw(0.5), title="The Long Drop", signature=True, track=0.0, rng=(0, 10), kinds=("area",), damage=1.8,
                knock=1.4, plant=(), disc=5.0, hit=[(PALM[0], PALM[1], 14.0)] + [(b, p, 7.0) for b, p in TIPS],
                stages={"windup": w, "active": act, "recover": [(0.35, _mix(tw(0), lift_, 0.4)), (0.7, _mix(lift_, REST, 0.75))]})


def arrive(r):
    """Its intro: it lies on the floor palm down as if it had been dropped there; its fingers twitch, one at a time; it
    pushes itself up on its fingertips like something standing, lifts off the floor and hangs, and turns its palm to
    you and beckons, its index finger curling twice."""
    low = _lying(r)
    tw = lambda k, a: _on(low, _pose(wrist=(-50, 6, 4), arm=(-10, 0, 8), fingers=dict(curl=-8, spread=10, each={k: (-a, -a, -0.6 * a)}),
                                     thumb=dict(curl=-6, out=10)))
    low_tw = [tw(3, 14), tw(1, 16), tw(0, 18)]
    perch = _floor(r, _pose(wrist=(-44, 2, 0), arm=(-6, 0, 4), fingers=dict(curl=44, claw=6, spread=14), thumb=dict(curl=24, out=6)), gap=1.0, on=TIPS_ON)
    hang = _pose(lift=2, pitch=-2, wrist=(2, 0, 0), fingers=dict(curl=14, spread=4), thumb=dict(curl=10))
    show = _pose(lift=2, pitch=-6, wrist=(-14, 40, 0), fingers=dict(curl=6, spread=6), thumb=dict(curl=4, out=6))
    beck = lambda c: _pose(lift=2, pitch=-6, wrist=(-14, 40, 0), fingers=dict(curl=6 + 6 * c, spread=6, each={0: (30 * c, 50 * c, 40 * c)}),
                           thumb=dict(curl=4, out=6))
    mv = Move("arrive", "NONE", 14, 40, 14, through=_mix(show, REST, 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, low_tw[0]), (0.65, low_tw[1]), (1.0, low_tw[2])],
                      "active": [(0.2, perch), (0.4, hang), (0.55, show), (0.66, beck(1)), (0.76, beck(0.1)), (0.86, beck(1)), (1.0, beck(0))],
                      "recover": [(0.6, _mix(show, REST, 0.7))]})
    mv._first = low  # it starts where the kneel left it, on the floor
    return mv


def moves(r):
    return [swat(r), clutch(r), fall(r), lift(r), no_bottom(r), flick(r), drop(r), arrive(r)]


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
    """Under a move, once it is made: its fingertips stir at their own pace and its thumb with them, faded in and out
    at the ends so it hands back to the idle exactly as it was (a living thing never holds a pose dead still)."""
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
    for k in range(4):
        for s, seg in enumerate("bc"):
            add(f"f{k}{seg}", lambda t, k=k, s=s: (5.0 * math.sin(2 * math.pi * (0.8 + 0.13 * k) * t - 1.1 * k - 0.7 * s), 0.0,
                                                   1.5 * math.sin(2 * math.pi * 0.6 * t - 0.9 * k)))
    add("t1", lambda t: (3.0 * math.sin(2 * math.pi * 0.7 * t + 1.0), 0.0, 3.0 * math.sin(2 * math.pi * 0.5 * t)))
    add("hand", lambda t: (1.2 * math.sin(2 * math.pi * 0.4 * t), 2.0 * math.sin(2 * math.pi * 0.3 * t + 0.5), 0.0))


# the ground (SPEC §35.10): its fingers and thumb stay out of the floor, uncurling (forward and up) as they meet it
FINGER_GROUND = [([f"f{k}b", f"f{k}c"], [(f"f{k}a", 0, 40, -1), (f"f{k}b", 0, 40, -1)]) for k in range(4)]
THUMB_GROUND = [(["t0", "t1", "t2"], [("t0", 0, 40, -1), ("t1", 0, 30, -1)])]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        _stage(r, m)
        _drift(r, core.clip_name(m))
    core.ground_pass(r, FINGER_GROUND + THUMB_GROUND, clips=list(r.clips), skip=())
    return mv


# Its sounds (SPEC §35.8): the phantom it was, its voice kept low and slow, its swoop and its flap for the air it moves;
# the crack of knuckles (bone); a body falling a long way for its slams; the elder guardian's curse for No Bottom.
# Chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.phantom.ambient", 0.8, 0.5), ("block.bone_block.hit", 0.6, 0.7)],
    "tell_heavy": [("entity.phantom.swoop", 1.0, 0.5), ("block.bone_block.break", 0.8, 0.6)],
    "tell_perilous": [("entity.phantom.ambient", 1.2, 0.4), ("block.sculk_shrieker.shriek", 0.7, 0.5), ("entity.elder_guardian.curse", 0.6, 0.6)],
    "swing_light": [("entity.phantom.flap", 1.0, 0.7), ("entity.player.attack.sweep", 0.7, 0.6)],
    "swing_heavy": [("entity.phantom.swoop", 1.2, 0.6), ("entity.breeze.wind_burst", 0.7, 0.6)],
    "cast": [("entity.illusioner.cast_spell", 0.9, 0.6), ("entity.elder_guardian.ambient", 0.7, 0.5)],
    "impact": [("entity.player.big_fall", 1.2, 0.6), ("entity.player.attack.crit", 0.8, 0.7)],
    "slam": [("entity.generic.big_fall", 1.4, 0.45), ("entity.zombie.attack_wooden_door", 1.0, 0.5), ("entity.player.attack.knockback", 0.8, 0.5)],
    "roar": [("entity.phantom.death", 1.2, 0.4), ("entity.elder_guardian.curse", 0.8, 0.6)],
    "crack": [("block.bone_block.break", 1.0, 0.8), ("block.bone_block.hit", 0.8, 0.5)],
    "step": [("entity.phantom.flap", 0.3, 0.5)],
}
MOVE_SOUNDS = {"fall": {"impact": "slam"}, "drop": {"swing": "roar", "impact": "slam"}, "flick": {"swing": "crack"},
               "lift": {"swing": "cast", "impact": ""}, "no_bottom": {"swing": "roar", "impact": ""}, "arrive": {"swing": "crack", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "arrive"},  # it lies on the floor as if dropped there, and gets up on its fingers
    "phases": [
        {"pool": [
            {"move": "swat", "weight": 3, "when": "near", "then": [["clutch"], ["clutch", "fall"]]},
            {"move": "clutch", "weight": 2, "when": "near"},
            {"move": "fall", "weight": 3, "when": "far", "then": [["swat"]]},
            {"move": "lift", "weight": 3},
            {"move": "flick", "weight": 3, "when": "behind"},
        ]},
        # further down: No Bottom, a wider Lift, and the Long Drop
        {"transition": "no_bottom", "pool": [
            {"move": "swat", "weight": 3, "when": "near", "then": [["clutch"], ["clutch", "lift"]]},
            {"move": "fall", "weight": 3, "when": "far", "then": [["swat", "clutch"]]},
            {"move": "lift", "weight": 3},
            {"move": "no_bottom", "weight": 1},
            {"move": "drop", "weight": 2},
            {"move": "flick", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.8, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("float", (0, 0, 0)), ("hand", (0, 24, 0)), ("f1c", TIPS[1][1]), ("f3c", TIPS[3][1]), ("t2", (0, 7, 0)), ("upper", (0, -30, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
