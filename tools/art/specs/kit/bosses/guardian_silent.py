"""The Great Listener (SPEC §35.11, docs/bosses/ROLLOUT.md): the Silent Halls' Guardian, a Brute. The Hush Bat, picked
from its silhouette round 2026-10-29: "Every Listener in the halls is one of its ears."

A blind bat as big as a barn, walking on its folded wings. It goes on all fours like a vampire bat: its wrists and their
hooked thumbs on the floor before it, its elbows standing up in two sharp peaks over its back, the folded wings hanging
inside them like a hood, its short hind legs splayed out behind with their knees up. Its ears are enormous, taller than
its head and ribbed inside, pale and pink and faintly lit; its eyes are stitched shut with pale thread; a spear of a
nose-leaf stands on its wrinkled snout over a mouth of long fangs. A ruff of pale ash-grey fur round its neck; the rest
of its fur is soot; its wings are dark leather veined paler.

It creeps, one wrist and the opposite foot at a time, its head low and its ears turning; it runs in a bound, as a
vampire bat does, both wrists vaulting it forward and the feet landing after. Now and then it stops and listens, still,
its head cocked and its ears turning one way and then the other.

Moves (SPEC §35.11): it rakes you with a folded wing, its thumb's hooked claw leading, then with the other (LIGHT);
Found You (HEAVY: it goes still and listens, then rears up on its hind legs and throws its wings wide over you, the whole
span of them, holds, and crashes down on you on its wrists); a wing flung open and swept round behind it at whoever is
there; the Bound (a gap closer: it vaults at you on its wrists and bites as it lands); Echo Scream (the signature: its
ears fold forward, it draws its head back and swells, and screams with its mouth wide, its throat lit, its ears ringing);
Absolute Silence (it wraps its wings round itself like a cloak, folds its ears flat and holds its breath, and the light
goes); and Ears (it lifts its head and clicks, its jaw chattering, its ears turning, and the Listeners come). It waits for
you wrapped in its wings with its head bowed into them and only its ears standing out.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_silent"
SCALE = 1.8  # the hitbox on the def's LISTENER model at size 1.6 x 2.2: 2.9 x 4.0 blocks, round its body; the body is at true size

# 3 blocks to its shoulders, its elbows' peaks 4.3 up, its ears' tips 5.6; a wing opened is 6.5 blocks from its shoulder
# to its tip. Value story: the inside of its ears, the stitches and its fangs brightest, then its ash ruff, then its soot
# fur; its wings darkest.
SIDE = {"l": 1, "r": -1}
ARM = {s: (f"arm_{s}", f"fore_{s}", f"hand_{s}") for s in "lr"}
LEG = {s: (f"thigh_{s}", f"shin_{s}", f"foot_{s}") for s in "lr"}
HUMERUS, FOREARM = 22.0, 60.0
REST_HAND = {s: (SIDE[s] * 21.0, 4.5, -42.0) for s in "lr"}  # the wrists on the floor before it, far enough out
# that the arm can still fold when its chest dips (it folds no shorter than the forearm less the humerus)
# a thumb on the floor lies forward along it, a touch up, its claw hooked down onto it
THUMB_DIR = (0.0, -0.07, -0.998)


def _thumb_x(fr, s):
    """The thumb turns only about its wrist's own sideways axis (any other way its turn sits near a gimbal lock and its
    keys spin it between them): its x is square to that axis and to where it points, up on the left thumb and down on
    the right, so each claw is cut on the side that faces the floor."""
    M = fr[f"hand_{s}"][0]
    return ik.cross(THUMB_DIR, (M[0][2], M[1][2], M[2][2]))
REST_FOOT = {s: (SIDE[s] * 15.0, 2.4, 30.0) for s in "lr"}  # the feet splayed behind
ARM_POLE = {s: (SIDE[s] * 1.0, -0.2, 0.0) for s in "lr"}  # the elbows folded up and back over the shoulders: the peaks
LEG_POLE = {s: (SIDE[s] * 1.0, -0.6, 0.2) for s in "lr"}  # the knees out and up
# the fingers of each wing, front to back: (name, segment lengths, width of the membrane behind each, opened: degrees
# from the forearm's line toward its trailing side)
FINGERS = (("f3", (30, 24), (12, 9), 6.0), ("f4", (26, 20), (14, 10), 34.0), ("f5", (22, 16), (16, 10), 64.0))
FOLDED = {"f3": 172.0, "f4": 176.0, "f5": 179.0}  # folded: each finger laid back up along the forearm
EARS = ("ear", "ear2")
TRAIL = {}  # per side: the sign of the forearm's +z on its trailing side (where the elbow points), set by body()


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    hips = _bone(base, "hips", 0, 24, 13)
    chest = _bone(hips, "chest", 0, 3, -14, (-8, 0, 0))
    neck = _bone(chest, "neck", 0, 7, -18, (-6, 0, 0))
    head = _bone(neck, "head", 0, 1, -6, (14, 0, 0))
    # the body: a deep chest slung between the arms, the belly and hips narrower behind, a pale ruff round the neck
    _box(hips, "pelvis", 0, -6, -1, 15, 12, 19)
    _box(hips, "rump", 0, -5, 9, 12, 9, 4)
    _box(chest, "ribs", 0, -9, -7, 22, 19, 22)
    _box(chest, "belly", 0, -10, -4, 16, 2, 14)
    _box(chest, "back", 0, 7, -3, 18, 4, 18)
    _box(chest, "hump", 0, 10, -9, 14, 4, 10)
    _box(chest, "ruff", 0, 0, -18, 26, 11, 6)
    _box(chest, "ruff_top", 0, 7, -14, 22, 4, 8)
    _box(chest, "mane", 0, 10, -8, 16, 3, 10)
    for k, (x, z) in enumerate(((-9, -19), (-3, -20), (3, -20), (9, -19))):
        _box(chest, f"tuft{k}", x, -3, z, 4, 4, 3)
    _box(neck, "neckc", 0, -5, -3, 13, 11, 8)
    # the head: a broad skull, the eyes two sewn slits under the brow, a short wrinkled snout with the nose-leaf standing
    # on it, the long fangs, the jaw
    _box(head, "skull", 0, -4, -5, 16, 13, 13)
    _box(head, "brow", 0, 6, -12, 16, 3, 3)
    _box(head, "snout", 0, -4, -14.5, 9, 7, 7)
    _box(head, "leaf", 0, 3, -17.5, 5, 8, 1)
    _box(head, "leaftip", 0, 11, -17.5, 3, 3, 1)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(head, "eye_" + nm, s * 5.5, 2, -12, 5, 4, 1)
        _box(head, "fang_" + nm, s * 2.5, -7, -17, 1, 3, 1)
        _box(head, "cheek_" + nm, s * 7.5, -4, -7, 3, 7, 7)
    jaw = _bone(head, "jaw", 0, -3, -4)
    _box(jaw, "jawc", 0, -4, -6, 9, 3, 11)
    _box(jaw, "throat", 0, -1.2, -7, 6, 1, 8)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(jaw, "lfang_" + nm, s * 2.5, -1, -11, 1, 2, 1)
    # the ears: each a tall ribbed cup facing forward, tapering in three parts, its rims standing forward, leaning out,
    # a spike (the tragus) inside it
    for s, nm in ((1, "l"), (-1, "r")):
        e = _bone(head, "ear_" + nm, s * 6.5, 7.0, -2.0, (-8, 0, s * 24))
        _box(e, "ear_" + nm, 0, 0, 0, 12, 13, 2)
        for k, x in (("o", 6.5), ("i", -6.5)):
            _box(e, f"earrim{k}_{nm}", x * s if k == "o" else x * s, 1, -0.5, 1, 12, 3)
        _box(e, "tragus_" + nm, -s * 3, 1, -1.5, 2, 7, 1)
        e2 = _bone(e, "ear2_" + nm, 0, 13.0, 0, (-6, 0, 0))
        _box(e2, "ear2_" + nm, 0, 0, 0, 10, 11, 2)
        _box(e2, "earrimo2_" + nm, s * 5.5, 0, -0.5, 1, 10, 3)
        _box(e2, "earrimi2_" + nm, -s * 5.5, 0, -0.5, 1, 9, 3)
        _box(e2, "ear3_" + nm, 0, 11, 0, 6, 7, 2)
        _box(e2, "eartip_" + nm, 0, 18, 0, 3, 3, 2)
    # the tail, short, with the membrane between the legs hung from it
    tail = _bone(hips, "tail", 0, 0, 8, (24, 0, 0))
    _box(tail, "tailc", 0, -1, 4, 2, 2, 8)
    _box(tail, "uro", 0, -0.5, 6, 18, 1, 9)
    # the arms: the humerus up from the shoulder to the elbow's peak, the long forearm down to the wrist on the floor,
    # the hooked thumb, and the three long fingers folded back up along the forearm with the membrane between them
    for s, sg in (("l", 1), ("r", -1)):
        a = _bone(chest, f"arm_{s}", sg * 11.0, 5.0, -10.0)
        a.cube(f"arm_{s}", -3, -1, -3, 6, HUMERUS + 2, 6)
        a.cube(f"armweb_{s}", -0.5, 2, 0, 1, HUMERUS - 4, 6)
        f = a.bone(f"fore_{s}", (0, HUMERUS, 0))
        f.cube(f"fore_{s}", -2, -1, -2, 4, FOREARM + 1, 4)
        f.cube(f"foreweb_{s}", -1.5, 4, 0, 3, FOREARM - 10, 7)
        h = f.bone(f"hand_{s}", (0, FOREARM, 0))
        h.cube(f"wrist_{s}", -2.5, -1, -2.5, 5, 4, 5)
        th = h.bone(f"thumb_{s}", (0, 2.5, -1.5))
        th.cube(f"thumb_{s}", -1, -0.5, -1, 2, 6, 2)
        th.cube(f"thumbclaw_{s}", -2.5 if s == "l" else -0.5, 5, -1, 3, 2, 2)  # hooked toward its floor side (see _thumb_x)
        for nm, lens, webs, _ in FINGERS:
            fa = h.bone(f"{nm}a_{s}", (0, 1.0, 0))
            fa.cube(f"{nm}a_{s}", -1, 0, -1, 2, lens[0], 2)
            fa.cube(f"{nm}aw_{s}", -0.5, 1, 0, 1, lens[0] - 1, webs[0])
            fb = fa.bone(f"{nm}b_{s}", (0, lens[0], 0))
            fb.cube(f"{nm}b_{s}", -0.5, 0, -0.5, 1, lens[1], 1)
            fb.cube(f"{nm}bw_{s}", -0.5, 0, 0, 1, lens[1] - 2, webs[1])
    # the hind legs, short and splayed, the knees up, the feet with five hooked toes
    for s, sg in (("l", 1), ("r", -1)):
        t = _bone(hips, f"thigh_{s}", sg * 7.0, -3.0, 5.0)
        t.cube(f"thigh_{s}", -2.5, -1, -2.5, 5, 13, 5)
        sh = t.bone(f"shin_{s}", (0, 12, 0))
        sh.cube(f"shin_{s}", -1.5, 0, -1.5, 3, 14, 3)
        ft = sh.bone(f"foot_{s}", (0, 14, 0))
        ft.cube(f"foot_{s}", -2.5, 0, -2, 5, 2, 5)
        for j in range(5):
            ft.cube(f"toe_{s}{j}", -2.5 + j * 1.1, 1, -4, 1, 1, 3)
    for s in "lr":
        ik.rest_limb(r, *ARM[s], REST_HAND[s], ARM_POLE[s])
        ik.rest_limb(r, *LEG[s], REST_FOOT[s], LEG_POLE[s])
    _rest_hands(r)
    r.look = [("head", 0.4, 0.35), ("neck", 0.3, 0.2)]
    r.shadow = 2.6
    r.size = 1.0
    # secondary parts (SPEC §35.10): the ear tips and the ruff tremble, the membranes between the legs sway
    r.springs = [("ear2_l", 0.35, 2.2, 0.16), ("ear2_r", 0.35, 2.2, 0.16), ("tail", 0.3, 1.4, 0.2)]
    r.guard = ["hand_l", "hand_r", "thumb_l", "thumb_r", "foot_l", "foot_r"]
    return r


def _rest_hands(r):
    """At rest each wrist's fingers lie folded back up along its forearm, and its thumb's claw is hooked into the floor
    before it; which side of the forearm trails (the side its elbow points to) is found here."""
    fr = core.frames(r, {})
    for s in "lr":
        Mf, Tf = fr[f"fore_{s}"]
        Ta = fr[f"arm_{s}"][1]
        rel = [Ta[i] - Tf[i] for i in range(3)]
        z = sum(Mf[i][2] * rel[i] for i in range(3))  # the shoulder in the forearm's frame: inside the fold
        TRAIL[s] = -1.0 if z > 0 else 1.0
    for s in "lr":
        for nm, _, _, _ in FINGERS:
            r.by_name[f"{nm}a_{s}"].rot = (TRAIL[s] * FOLDED[nm], 0, 0)
            r.by_name[f"{nm}b_{s}"].rot = (TRAIL[s] * -4.0, 0, 0)
    fr = core.frames(r, {})
    for s in "lr":
        b = r.by_name[f"thumb_{s}"]
        b.rot = tuple(v + w for v, w in zip(b.rot, ik.orient(r, fr, f"thumb_{s}", THUMB_DIR, _thumb_x(fr, s))))
        f = r.by_name[f"foot_{s}"]
        f.rot = tuple(v + w for v, w in zip(f.rot, ik.orient(r, fr, f"foot_{s}", (0, 1, 0), (1, 0, 0))))
        fr = core.frames(r, {})


# ---------------------------------------------------------------------------------------------------------------------
# Paint

FUR = Ramp("#0b0a0d", "#121015", "#18151b", "#1f1b22", "#27222a", "#302a33", "#3a333d", "#463e48")
ASH = Ramp("#3a3537", "#4c4648", "#5f585a", "#736b6c", "#877f7f", "#9c9392", "#b1a8a5", "#c6bdb8")
HIDE = Ramp("#0d090f", "#140f17", "#1b141e", "#231a27", "#2c2130", "#36293a", "#413145")
VEIN = Ramp("#3e3040", "#4c3b4e", "#5b495c")
EAR = Ramp("#4a2a33", "#663a45", "#844c58", "#a2606c", "#bc7a84", "#d4969d", "#e8b4b8")
FANG = Ramp("#8a8072", "#aaa090", "#c8bfae", "#e2dacb", "#f4eee2")
CLAW = Ramp("#1a1614", "#2a2420", "#3c342e", "#544a40", "#6e6254")
STITCH = ("#08060a", "#1a1418", "#d8d0c0", "#f0e8d8")


def paint(r, P_):
    """Soot fur with a short coat, an ash ruff; dark leather wings veined paler along the fingers; the ears pale pink
    inside, ribbed, faintly lit, dark and furred outside; the eyes two sunken slits sewn shut with pale crosses; the
    fangs ivory, the throat lit violet inside the mouth."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    coat = R.p_fur(depth=0.7, every=3, seed=5, length=3)
    for c in ("pelvis", "rump", "ribs", "back", "hump", "neckc", "skull", "cheek_l", "cheek_r") + tuple(of("arm_", "thigh_", "shin_")):
        P_.paint(c, FUR, 4.0, coat, grad=1.0, rim=0.5, ao=0.9, noise=0.5, nscale=4)
    P_.paint("belly", FUR, 3.2, coat, grad=0.4, rim=0.2, ao=0.3, noise=0.4)
    for c in ("ruff", "ruff_top", "mane") + tuple(of("tuft")):
        P_.paint(c, ASH, 4.6, R.p_fur(depth=1.2, every=2, seed=9, length=4), grad=1.2, rim=0.9, ao=0.7, noise=0.6, nscale=3)
    for c in ("snout", "brow", "jawc"):
        P_.paint(c, HIDE, 4.2, R.p_strokes(cell=2, length=2, dark=0.8, light=0.3, seed=3), grad=0.8, rim=0.5, ao=0.6, noise=0.5)
    for c in ("leaf", "leaftip"):
        P_.paint(c, EAR, 3.4, None, grad=1.0, rim=0.8, ao=0.3)
    for c in of("fang_", "lfang_"):
        P_.paint(c, FANG, 3.4, None, grad=0.8, rim=0.8, ao=0.2)
    P_.paint("throat", EAR, 1.6, None, rim=0.2)
    g = P_.face("throat", "top", glow=True)
    for i, j in g.each():
        g.set(i, j, hexc("#9a5cff", 200 if 0 < i < g.w - 1 else 120))
    # the wings: the bones dark, the membranes darker leather with paler veins running along each finger
    for c in of("fore_", "wrist_", "thumb_") + [c for c in of("f3", "f4", "f5") if not c.endswith(("w_l", "w_r"))]:
        if c.startswith("thumbclaw"):
            continue
        P_.paint(c, HIDE, 4.6, None, grad=0.8, rim=0.7, ao=0.5, noise=0.3)
    for c in of("armweb_", "foreweb_", "uro") + [c for c in of("f3", "f4", "f5") if c.endswith(("w_l", "w_r"))]:
        P_.paint(c, HIDE, 3.2, R.p_blotch(c, scale=3, amount=0.4), grad=0.6, rim=0.4, ao=0.4, noise=0.4, nscale=3)
        for fname in ("west", "east"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if (i + 2 * (j // 5)) % 5 == 0 or (R._h(c, fname, i, j) % 23 == 0):
                    f.set(i, j, VEIN.at(1.0 + (R._h(c, i) % 3) * 0.5))
    for c in of("thumbclaw_", "toe_"):
        P_.paint(c, CLAW, 3.0, None, grad=0.8, rim=0.7, ao=0.2)
    for c in of("foot_"):
        P_.paint(c, HIDE, 3.6, None, grad=0.6, rim=0.4, ao=0.5)
    # the ears: dark and furred behind; inside (their front) pale pink, ribbed across, the rims darker, lit faintly
    for c in of("ear_", "ear2_", "ear3_", "eartip_", "earrim", "tragus_"):
        P_.paint(c, FUR, 4.2, coat, grad=0.8, rim=0.6, ao=0.4, noise=0.4)
    for c in of("ear_", "ear2_", "ear3_", "eartip_", "tragus_"):
        f, gl = P_.face(c, "north"), P_.face(c, "north", glow=True)
        for i, j in f.each():
            rim = i == 0 or i == f.w - 1
            rib = j % 3 == 0
            f.set(i, j, EAR.at(2.0 if rim else (3.6 if rib else 5.2) + (R._h(c, i, j) % 3) * 0.2))
            if not rim:
                gl.set(i, j, hexc("#ffb8c8", 26 if rib else 48))
    # the eyes, sewn shut: a sunken slit under the brow each side, crossed by pale stitches
    for nm in ("eye_l", "eye_r"):
        P_.paint(nm, HIDE, 1.4, None, rim=0.2, ao=0.2)
        f = P_.face(nm, "north")
        for i in range(f.w):
            f.set(i, 1, hexc(STITCH[0], 255))
            f.set(i, 2, hexc(STITCH[1], 255))
            if i % 2 == 0:
                f.set(i, 0, hexc(STITCH[2], 255))
                f.set(i, 3, hexc(STITCH[2], 255))
            else:
                f.set(i, 1, hexc(STITCH[3], 255))
                f.set(i, 2, hexc(STITCH[2], 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the body set and bent, each wrist and foot reaching where it stands, the wings folded or opened, the ears

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _hand(s, up=0.0, dz=0.0, dx=0.0):
    x, h, z = REST_HAND[s]
    return (x + SIDE[s] * dx, h + up, z + dz)


def _foot(s, up=0.0, dz=0.0, dx=0.0):
    x, h, z = REST_FOOT[s]
    return (x + SIDE[s] * dx, h + up, z + dz)


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), bend=0.0, side=0.0, twist=0.0, neck=(0, 0, 0), head=(0, 0, 0),
          jaw=0.0, ears=None, hands=None, poles=None, wings=None, curl=None, feet=None, tail=0.0, extra=None):
    """lift (+ up), pitch (+ its front dipping), yaw (+ to its left), roll (+ its left side down) and shift (x, z) move its
    hips; bend (+ its chest dipping), side and twist turn its chest on them; neck and head add their own turns; jaw opens
    (degrees). ears {side: (tilt + forward, swivel + out, fold + flat back)}, or one triple for both. hands {side: (x,
    height, z)}: where a wrist goes (else it stands where it stands), poles its elbow's way; wings {side: 0 folded to 1
    spread}: its fingers; curl {side: degrees} curls the fingers' tips in. feet {side: (x, height, z)}; tail (+ up)."""
    p = {"hips": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])}, "chest": (bend, side, twist), "neck": neck, "head": head,
         "jaw": (jaw, 0, 0), "tail": (-tail, 0, 0)}
    ears = ears or (0, 0, 0)
    for sd in "lr":
        t, sw, fo = ears[sd] if isinstance(ears, dict) else ears
        sg = SIDE[sd]
        p[f"ear_{sd}"] = (t - fo, sg * sw, sg * 0.3 * fo)
        p[f"ear2_{sd}"] = (0.4 * t - 0.6 * fo, 0, 0)
    fr = core.frames(r, core.as_sample(p))
    hands, poles, feet, wings, curl = hands or {}, poles or {}, feet or {}, wings or {}, curl or {}
    for sd in "lr":
        p.update(ik.limb(r, fr, *ARM[sd], ik.model(hands.get(sd, REST_HAND[sd])), poles.get(sd, ARM_POLE[sd])))
        p.update(ik.limb(r, fr, *LEG[sd], ik.model(feet.get(sd, REST_FOOT[sd])), LEG_POLE[sd]))
        f = max(0.0, min(1.2, wings.get(sd, 0.0)))
        for j, (nm, _, _, opened) in enumerate(FINGERS):  # past 1 the fingers fan out further, the back ones most
            a = FOLDED[nm] + (opened - FOLDED[nm]) * _ease(min(1.0, f)) + 12.0 * max(0.0, f - 1.0) / 0.2 * (j - 0.5)
            p[f"{nm}a_{sd}"] = (TRAIL[sd] * (a - FOLDED[nm]), 0, 0)
            p[f"{nm}b_{sd}"] = (TRAIL[sd] * (4.0 * min(1.0, f) + curl.get(sd, 0.0)), 0, 0)
    fr = core.frames(r, core.as_sample(p))
    for sd in "lr":  # a wrist on the floor hooks its thumb into it; one in the air lets it hang
        on = hands.get(sd, REST_HAND[sd])[1] < 8.0
        p[f"thumb_{sd}"] = ik.orient(r, fr, f"thumb_{sd}", THUMB_DIR, _thumb_x(fr, sd)) if on else (0, 0, 0)
        p[f"foot_{sd}"] = ik.orient(r, fr, f"foot_{sd}", (0, 1, 0), (1, 0, 0)) if feet.get(sd, REST_FOOT[sd])[1] < 8.0 else (20, 0, 0)
    if extra:
        p.update(extra)
    return p


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


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before (each axis by whole turns, the same pose)."""
    out, last = [], {}
    for t, p in keys:
        q = {}
        for b, v in p.items():
            rot = v["rot"] if isinstance(v, dict) else v
            if b in last:
                rot = tuple(c + 360 * round((last[b][i] - c) / 360) for i, c in enumerate(rot))
            last[b] = rot
            q[b] = {**v, "rot": rot} if isinstance(v, dict) else rot
        out.append((t, q))
    return out


def _plant(r, clip, hands=(), feet=()):
    """The wrists and feet that stand through a move keep where they stand on the floor, every tick, the feet flat on
    it and the thumbs hooked into it."""
    def fn(t, smp, fr):
        out = {}
        for sd in hands:
            out.update(ik.limb(r, fr, *ARM[sd], ik.model(REST_HAND[sd]), ARM_POLE[sd]))
        for sd in feet:
            out.update(ik.limb(r, fr, *LEG[sd], ik.model(REST_FOOT[sd]), LEG_POLE[sd]))
        s2 = dict(smp)
        s2.update(core.as_sample(out))
        fr2 = core.frames(r, s2)
        for sd in hands:
            out[f"thumb_{sd}"] = ik.orient(r, fr2, f"thumb_{sd}", THUMB_DIR, _thumb_x(fr2, sd))
        for sd in feet:
            out[f"foot_{sd}"] = ik.orient(r, fr2, f"foot_{sd}", (0, 1, 0), (1, 0, 0))
        return out
    ik.retrack(r, clip, fn)


def _on_floor(r, clip):
    """Between keys a wrist or a foot can sag a hair into the floor (its limb's turns blend, not its path): wherever one
    would, its limb is solved again that tick to set it on the floor where it was, the elbow or knee its own way and the
    wrist or foot turned as it was. Turning the forearm or shin after the fact, as the ground pass would, kinks it and,
    on these long forearms, throws the wrist a hand's width."""
    floor = core.GROUND - 0.3
    still = {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}
    limbs = [(ARM[s], [f"hand_{s}", f"thumb_{s}"]) for s in "lr"] + [(LEG[s], [f"foot_{s}"]) for s in "lr"]

    def fn(t, smp, fr):
        out = {}
        for (up, lo, en), guard in limbs:
            v = core.lowest(r, fr, guard)
            if v <= floor:
                out.update({b: tuple(c / DEG for c in smp.get(b, still)["rot"]) for b in (up, lo, en)})
                continue
            end, joint, root = (core.point(fr, b, (0, 0, 0)) for b in (en, lo, up))
            pole = ik.unit(ik.sub(joint, tuple((root[i] + end[i]) / 2 for i in range(3))))
            M = fr[en][0]
            ydir, xdir = (M[0][1], M[1][1], M[2][1]), (M[0][0], M[1][0], M[2][0])
            # only what lifting it changes: the turn from where the limb reaches now to where it must, on top of the
            # turns it has (a whole new solve would lose the twist it was posed with and kink it)
            to = (end[0], end[1] - (v - floor), end[2])
            # an arm folded as far as it goes cannot lift its wrist toward the shoulder: the wrist slides out along the
            # floor instead, as far as it must
            short = abs(r.by_name[lo].pivot[1] - r.by_name[en].pivot[1]) + 0.5
            dh = to[1] - root[1]
            flat = math.hypot(to[0] - root[0], to[2] - root[2])
            if dh * dh + flat * flat < short * short and flat > 1e-6:
                k = math.sqrt(max(0.0, short * short - dh * dh)) / flat
                to = (root[0] + (to[0] - root[0]) * k, to[1], root[2] + (to[2] - root[2]) * k)
            e0, k0 = ik.two_bone(r, fr, up, lo, en, end, pole)
            e1, k1 = ik.two_bone(r, fr, up, lo, en, to, pole)
            Mp = fr[r.by_name[up].parent.name][0]
            turn = R._mm(R._mm(Mp, R._rot(*e1)), core._t(R._mm(Mp, R._rot(*e0))))
            whole = core._euler(R._mm(core._t(Mp), R._mm(turn, fr[up][0])))
            cur = smp.get(lo, still)["rot"]
            q = {up: ik.delta(tuple(c / DEG for c in whole), r.by_name[up].rot), lo: ((cur[0] + k1 - k0) / DEG, cur[1] / DEG, cur[2] / DEG)}
            s2 = dict(smp)
            for b, e in q.items():
                s2[b] = {**smp.get(b, still), "rot": tuple(c * DEG for c in e)}
            q[en] = ik.orient(r, core.frames(r, s2), en, ydir, xdir)
            out.update(q)
        return out
    ik.retrack(r, clip, fn)


# the wings opened wide: the wrists out and up, the elbows back
SPREAD = {s: (SIDE[s] * 58.0, 92.0, 2.0) for s in "lr"}
SPREAD_POLE = {s: (SIDE[s] * 0.2, 1.0, 0.25) for s in "lr"}  # the elbows under: the membranes face forward
# wrapped round itself: the forearms run down its front and cross before its belly, the elbows up by its head, the
# fingers half open across it
WRAP = {s: (-SIDE[s] * 4.0, 14.0, -20.0) for s in "lr"}
WRAP_POLE = {s: (SIDE[s] * 0.5, -1.0, -0.3) for s in "lr"}
OPEN_POLE = {s: (SIDE[s] * 0.9, -0.6, -0.15) for s in "lr"}  # opening out of the wrap: the elbows out and still up
# the wings half raised out to its sides, a mantle: the wrists up by its shoulders, the elbows out and back, the fingers
# fanned out level over the floor
MANTLE = {s: (SIDE[s] * 34.0, 26.0, -30.0) for s in "lr"}
MANTLE_POLE = {s: (SIDE[s] * 0.9, -0.4, 0.3) for s in "lr"}


def _wrapped(r, bow=1.0, ears=(0, 0, 0), breathe=0.0, tight=0.0, sway=0.0):
    """Sat back on its haunches, wrapped in its wings like a cloak, its head bowed into them (bow 0 to 1); tight (-1 to
    1) draws the wings closer round it, sway rocks it (degrees, + toward its left)."""
    hands = {s: (WRAP[s][0] + SIDE[s] * 3.0 * tight, WRAP[s][1] - 2.0 * tight, WRAP[s][2] + 3.0 * tight) for s in "lr"}
    return _pose(r, lift=-6 + breathe, pitch=-30 - 2 * breathe, roll=sway, bend=-10, side=-0.6 * sway, neck=(30 * bow, 0, 0), head=(30 * bow, 0, 0),
                 ears=ears, hands=hands, poles=WRAP_POLE, wings={"l": 0.55 - 0.12 * tight, "r": 0.55 - 0.12 * tight},
                 curl={"l": 10 + 8 * tight, "r": 10 + 8 * tight}, feet={s: _foot(s, dz=-8) for s in "lr"}, tail=-10)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It crouches on its wrists breathing, its head low, its ears turning on their own, the nose-leaf twitching with
    its breath; then it stops and listens: its head cocks one way, its ears turn hard toward the sound and then the
    other way, and it lets the breath go; now and then one wrist lifts and sets down again, its elbow's peak rising."""
    L, n = 7.0, 105
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        lis = _ease((u - 0.45) / 0.06) * (1 - _ease((u - 0.78) / 0.06))  # listening
        cock = math.sin(math.pi * max(0.0, min(1.0, (u - 0.45) / 0.33))) * (1 if u < 0.62 else -1) * lis
        sw = 10 * math.sin(3 * w) * (1 - lis)
        ears = {"l": (6 * math.sin(2 * w + 0.4) + 10 * lis, sw + 18 * cock, 0), "r": (6 * math.sin(2 * w + 1.9) + 10 * lis, -sw - 18 * cock, 0)}
        step = _wave(u, 0.2, 0.12)
        keys.append((L * u, _pose(r, lift=0.6 * math.sin(2 * w) * (1 - lis) + 0.8 * lis, pitch=-1.2 * math.sin(2 * w), bend=1.5 * math.sin(2 * w + 0.5),
                                  neck=(-6 * lis, 3 * math.sin(w), 0), head=(4 - 8 * lis + 2 * math.sin(4 * w), 6 * math.sin(w + 0.7), 14 * cock),
                                  jaw=3 * _wave(u, 0.9, 0.05), ears=ears, hands={"r": _hand("r", up=9 * step, dz=-3 * step)} if step > 0.01 else None,
                                  tail=2 * math.sin(w))))
    keyed(r, "idle", L, keys, loop=True)


WALK = {"hl": 0.0, "fr": 0.12, "hr": 0.5, "fl": 0.62}  # its creep: a wrist and then the foot across from it
BOUND = {"hl": 0.0, "hr": 0.04, "fl": 0.5, "fr": 0.54}  # its run: both wrists vault it, both feet land after


def _gait(u, stride, beta, lifts, groups):
    """Each wrist and foot through a stride at u: down, it moves back as far as the body travels, so it stays put on the
    floor; up, it lifts and reaches forward again."""
    hands, feet = {}, {}
    st = stride * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        sd = k[1]
        fn = _hand if k[0] == "h" else _foot
        lift = lifts[0] if k[0] == "h" else lifts[1]
        if q < beta:
            pos = fn(sd, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            pos = fn(sd, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
        (hands if k[0] == "h" else feet)[sd] = pos
    return hands, feet


def _locomotion(r):
    """It creeps a wrist and then the opposite foot at a time, low, its elbows' peaks rising and falling with each step,
    its head low and swinging and its ears turning. Running, it bounds as a vampire bat does: both wrists vault it
    forward, its body flies low over the floor with its elbows thrown up, and its feet land after."""
    for name, L, stride, beta, lifts, groups in (("walk", 1.5, 1.3, 0.7, (9.0, 4.0), WALK), ("run", 0.75, 2.8, 0.42, (12.0, 6.0), BOUND)):
        sp = stride * 16
        keys = []
        n = 30
        run = name == "run"
        for k in range(n + 1):
            u = k / n
            s1 = math.sin(2 * math.pi * u)
            s2 = math.sin(4 * math.pi * u)
            hands, feet = _gait(u, sp, beta, lifts, groups)
            if run:  # the front rises as the wrists vault it, then dips as the feet come under it
                lift, pitch, bend = 3.0 * math.sin(2 * math.pi * (u - 0.1)) + 1.0, -6.0 * math.sin(2 * math.pi * (u - 0.05)), 4.0 * s1
            else:
                lift, pitch, bend = -0.6 * (1 + math.cos(4 * math.pi * u)), 1.0 * s2, 1.0 * s2
            keys.append((L * u, _pose(r, lift=lift, pitch=pitch, bend=bend, roll=(0 if run else 2.5 * s1), yaw=(0 if run else 2.0 * s1), side=(0 if run else 4 * s1),
                                      neck=(-4 if run else 0, 0 if run else -4 * s1, 0), head=(4 + (6 * s1 if run else 2 * s2), 0 if run else -8 * s1, 0),
                                      ears=({"l": (-10, 0, 14), "r": (-10, 0, 14)} if run else {"l": (4 * s2, 8 * s1, 0), "r": (4 * s2, -8 * s1, 0)}),
                                      hands=hands, feet=feet, tail=3 * s2)))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.3, 2.8
    r.run_speed = (0.14, 0.32)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        A = 16.0 * DEG
        for k in range(n + 1):
            u = k / n
            hands, feet = {}, {}
            for j, ph in WALK.items():  # each wrist and foot steps round the way it turns while the others hold
                q = (u - ph) % 1.0
                if q < 0.7:
                    a, up_ = sgn * A * (0.5 - q / 0.7), 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a, up_ = sgn * A * (-0.5 + core.ease_io(f)), (8.0 if j[0] == "h" else 4.0) * math.sin(math.pi * f)
                x, h, z = (REST_HAND if j[0] == "h" else REST_FOOT)[j[1]]
                (hands if j[0] == "h" else feet)[j[1]] = (x * math.cos(a) + z * math.sin(a), h + up_, -x * math.sin(a) + z * math.cos(a))
            keys.append((0.9 * u, _pose(r, yaw=sgn * 2.0, side=sgn * 4.0, head=(4, sgn * 14, 0), ears={"l": (0, sgn * 10, 0), "r": (0, -sgn * 10, 0)},
                                        hands=hands, feet=feet)))
        keyed(r, nm, 0.9, keys, loop=True)


def _reactions(r):
    """Struck, it flinches back with its ears flattened and its wings half lifting off the floor; from the side it bends
    away. Its stance broken, it sits back and wraps itself in its wings, its head bowed into them; that is how it waits
    for you, too. It opens them again and comes down onto its wrists."""
    hit = _pose(r, lift=2, pitch=-6, bend=-6, neck=(-10, 0, 0), head=(-14, 0, 0), jaw=24, ears=(-14, 0, 30),
                hands={s: _hand(s, up=6, dz=4) for s in "lr"}, wings={"l": 0.25, "r": 0.25})
    back = _pose(r, lift=-0.5, bend=2, head=(3, 0, 0), ears=(0, 0, 6))
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it bends away to its right
        sd = "l" if sgn > 0 else "r"
        h = _pose(r, lift=1, roll=-6 * sgn, yaw=-8 * sgn, side=-10 * sgn, shift=(-sgn * 1.5, 0), head=(-4, -18 * sgn, 0), jaw=18,
                  ears={"l": (-10, 0, 26), "r": (-10, 0, 26)}, hands={sd: _hand(sd, up=8, dx=3)}, wings={sd: 0.3})
        b = _pose(r, roll=2 * sgn, side=3 * sgn, head=(2, 4 * sgn, 0), ears=(0, 0, 6))
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    sit = _pose(r, lift=-4, pitch=-14, bend=-4, neck=(10, 0, 0), head=(14, 0, 0), ears=(0, 0, 10), hands={s: _hand(s, up=10, dz=8) for s in "lr"},
                wings={"l": 0.3, "r": 0.3}, feet={s: _foot(s, dz=-4) for s in "lr"})
    keyed(r, "kneel", 1.0, [(0, _pose(r)), (0.3, sit), (0.7, _wrapped(r, 0.8)), (1.0, _wrapped(r))])
    peek = _wrapped(r, 0.2, ears=(6, 10, 0))
    open_ = _pose(r, lift=-3, pitch=-14, bend=-4, neck=(-6, 0, 0), head=(-4, 0, 0), ears=(4, 6, 0), hands={s: _hand(s, up=14, dz=6, dx=8) for s in "lr"},
                  poles=OPEN_POLE, wings={"l": 0.6, "r": 0.6}, feet={s: _foot(s, dz=-4) for s in "lr"})
    keyed(r, "rise", 1.2, [(0, _wrapped(r)), (0.3, peek), (0.65, open_), (0.95, _pose(r, lift=-1, bend=3, head=(6, 0, 0))), (1.2, _pose(r))])
    _death(r)


def _death(r):
    """Its last: it rears up with its mouth wide and no sound coming, its wings thrown open and shaking; it falls over
    onto its side; one wing lies spread across the floor and the other lifts once and folds slowly over it; its ears
    droop, and it is still."""
    rear = _pose(r, lift=8, pitch=-40, bend=-12, neck=(-24, 0, 0), head=(-20, 0, 0), jaw=50, ears=(-6, 16, 0), hands=SPREAD, poles=SPREAD_POLE,
                 wings={"l": 1.0, "r": 1.0}, feet={s: _foot(s, dz=-6) for s in "lr"}, tail=10)
    shake = lambda a: _pose(r, lift=7, pitch=-36, bend=-10, twist=6 * a, neck=(-20, 6 * a, 0), head=(-18, 8 * a, 0), jaw=44, ears=(-6, 16 + 6 * a, 0),
                            hands={s: (SPREAD[s][0], SPREAD[s][1] - 10 + 4 * a * SIDE[s], SPREAD[s][2]) for s in "lr"}, poles=SPREAD_POLE,
                            wings={"l": 0.95, "r": 0.95}, feet={s: _foot(s, dz=-6) for s in "lr"}, tail=8)
    over = _pose(r, lift=-6, roll=-60, pitch=-6, side=-10, neck=(0, -10, 0), head=(0, -14, -10), jaw=30, ears=(-10, 0, 20),
                 hands={"l": (40.0, 40.0, -10.0), "r": (-34.0, 4.0, -20.0)}, poles={"l": (0.2, 0.3, 1.0), "r": (-0.6, -0.4, 0.6)},
                 wings={"l": 0.9, "r": 0.6}, feet={"l": (8.0, 14.0, 26.0), "r": (-16.0, 3.0, 28.0)})
    low = {"r": (-50.0, 4.0, -18.0)}  # the wing under it, spread flat along the floor
    low_pole = {"r": (-0.3, 0.0, 1.0)}
    lie = dict(lift=-12.5, roll=-84, pitch=-4, side=-12, neck=(0, -12, 0), feet={"l": (4.0, 16.0, 26.0), "r": (-16.0, 3.0, 28.0)})
    down = _pose(r, **lie, head=(0, -16, -14), jaw=34, ears={"l": (-14, 0, 30), "r": (-20, 0, 60)},
                 hands={"l": (20.0, 46.0, 6.0), **low}, poles={"l": (0.2, -0.6, 1.0), **low_pole}, wings={"l": 0.7, "r": 0.95})
    over_ = _pose(r, **lie, head=(0, -17, -14), jaw=26, ears={"l": (-16, 0, 34), "r": (-20, 0, 60)},
                  hands={"l": (0.0, 40.0, 8.0), **low}, poles={"l": (0.3, -1.0, 0.2), **low_pole}, wings={"l": 0.6, "r": 0.95})
    fold = _pose(r, **lie, head=(0, -18, -14), jaw=20, ears={"l": (-18, 0, 40), "r": (-20, 0, 60)},
                 hands={"l": (-30.0, 14.0, 4.0), **low}, poles={"l": (0.2, -1.0, 0.2), **low_pole}, wings={"l": 0.8, "r": 0.95})
    keys = [(0, _pose(r)), (0.4, rear), (0.7, shake(1)), (0.9, shake(-1)), (1.1, shake(0.6)), (1.6, over), (1.95, down), (2.1, _mix(down, over, 0.12)),
            (2.3, down), (2.85, over_), (3.4, fold), (4.6, fold)]
    keyed(r, "death", 4.6, _unwrap(keys))


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b: 0 for sd in "lr" for b in ARM[sd] + LEG[sd]}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _ears_to(sw):
    """Both ears turned hard toward a sound, swivelled sw degrees (+ toward its left)."""
    return {"l": (12, 10 + sw, 0), "r": (12, 10 - sw, 0)}


def rake(r, sd="r", mid="rake"):
    """A folded wing raked across (LIGHT): it rocks back onto its other wrist and its feet and lifts this wrist high and
    out to its side, the elbow's peak thrown up, the thumb's hooked claw cocked; then it rakes it down across in front of
    it, its body swinging after, and drags the claw back over the floor."""
    sg = SIDE[sd]
    o = "l" if sd == "r" else "r"
    up = lambda h, x, z: {sd: (sg * x, h, z)}
    gather = _pose(r, lift=1.5, pitch=-4, yaw=6 * sg, roll=-4 * sg, side=6 * sg, head=(-2, 8 * sg, 0), ears=_ears_to(-8 * sg),
                   hands=up(22, 26, -28), poles={sd: (sg * 0.8, -0.6, 0.2)}, wings={sd: 0.15})
    rear = _pose(r, lift=3, pitch=-8, yaw=10 * sg, roll=-6 * sg, side=9 * sg, neck=(-4, 4 * sg, 0), head=(-6, 12 * sg, 0), jaw=18, ears=_ears_to(-14 * sg),
                 hands=up(40, 30, -22), poles={sd: (sg * 0.7, -0.7, 0.3)}, wings={sd: 0.25}, feet={o: _foot(o, dz=-2)})
    swing = _pose(r, lift=-1, pitch=5, yaw=2 * sg, roll=-1.5 * sg, bend=6, side=2 * sg, neck=(-9, 0, 0), head=(-6, 2 * sg, 0), jaw=26, ears=_ears_to(0),
                  hands=up(26, 12, -46), poles={sd: (sg * 0.6, -0.8, -0.1)}, wings={sd: 0.25})
    hit = _pose(r, lift=-2, pitch=3, yaw=-9 * sg, roll=4 * sg, side=-7 * sg, neck=(-2, -4 * sg, 0), head=(-2, -8 * sg, 0), jaw=30, ears=_ears_to(8 * sg),
                hands=up(6, -10, -40), poles={sd: (sg * 0.6, -0.8, 0.0)}, wings={sd: 0.2})
    held = _pose(r, lift=-2.5, pitch=4, yaw=-10 * sg, roll=4.5 * sg, side=-8 * sg, neck=(-1, -4 * sg, 0), head=(-1, -9 * sg, 0), jaw=20,
                 ears=_ears_to(9 * sg), hands=up(4, -14, -34), poles={sd: (sg * 0.6, -0.8, 0.0)}, wings={sd: 0.15})
    through = _pose(r, lift=-2, pitch=3, yaw=-8 * sg, roll=3.5 * sg, side=-6 * sg, head=(0, -7 * sg, 0), jaw=12, ears=_ears_to(7 * sg),
                    hands=up(5, -8, -30), poles={sd: (sg * 0.6, -0.8, 0.0)}, wings={sd: 0.1})
    back = _pose(r, lift=-1, yaw=-3 * sg, side=-2 * sg, hands={sd: _hand(sd, up=6, dz=-2, dx=-4)}, poles={sd: (sg * 0.9, -0.4, 0.1)}, ears=_ears_to(3 * sg))
    return Move(mid, "LIGHT", 10, 4, 14, through=through, track=8.0, rng=(0, 3.4), kinds=("combo", "near"), damage=1.0, knock=0.5,
                hit=[(f"thumb_{sd}", (0, 7, -1), 3.0), (f"hand_{sd}", (0, 1, 0), 3.5)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.25, swing), (0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def found(r):
    """Found You (HEAVY): it goes still and listens, its head cocked and its ears turning hard toward you; then it rears
    up on its hind legs and throws its wings wide over you, the whole span of them, its mouth open; holds; and crashes
    down onto you on its wrists, its wings sweeping down after them."""
    listen = _pose(r, lift=0.5, bend=-2, neck=(-8, 0, 0), head=(-6, 6, 14), ears=_ears_to(4))
    crouch = _pose(r, lift=-4, pitch=3, bend=3, neck=(-4, 0, 0), head=(-2, 0, 0), ears=_ears_to(0), hands={s: _hand(s, dz=-3) for s in "lr"},
                   feet={s: _foot(s, dz=-3) for s in "lr"})
    reared = _pose(r, lift=4, pitch=-48, bend=-14, neck=(14, 0, 0), head=(34, 0, 0), jaw=44, ears=(10, 14, 0), hands=SPREAD, poles=SPREAD_POLE,
                   wings={"l": 1.1, "r": 1.1}, feet={s: _foot(s, dz=-13) for s in "lr"}, tail=14)
    down = {s: _hand(s, dz=-12, dx=2) for s in "lr"}
    fwd = {s: (SIDE[s] * 0.8, -0.6, 0.2) for s in "lr"}
    slam = _pose(r, lift=-5, pitch=6, bend=6, neck=(-6, 0, 0), head=(-4, 0, 0), jaw=36, ears=(-10, 6, 18), hands=down, poles=fwd,
                 wings={"l": 0.45, "r": 0.45}, feet={s: _foot(s, dz=-2) for s in "lr"})
    held = _pose(r, lift=-6, pitch=7, bend=7, neck=(-5, 0, 0), head=(-2, 0, 0), jaw=28, ears=(-12, 6, 22),
                 hands={s: (v[0], v[1], v[2] + 1) for s, v in down.items()}, poles=fwd, wings={"l": 0.3, "r": 0.3}, feet={s: _foot(s, dz=-2) for s in "lr"})
    through = _pose(r, lift=-4.5, pitch=5, bend=5, neck=(-4, 0, 0), jaw=14, ears=(-6, 4, 10), hands={s: (v[0], v[1], v[2] + 4) for s, v in down.items()},
                    poles=fwd, wings={"l": 0.15, "r": 0.15})
    return Move("found", "HEAVY", 18, 6, 20, through=through, title="Found You", hold=6, track=4.0, ability="Found You", rng=(0, 3.8),
                kinds=("area", "near"), damage=1.3, knock=1.4, disc=3.5, hit=[("hand_l", (0, 1, 0), 4.0), ("hand_r", (0, 1, 0), 4.0)], plant=(),
                stages={"windup": [(0.22, listen), (0.42, _mix(listen, crouch, 0.8)), (0.55, crouch), (1.0, reared)], "active": [(0.35, slam), (1.0, held)],
                        "recover": [(0.5, _mix(through, _pose(r), 0.5))]}, lag=_lag())


def buffet(r):
    """For whoever is behind it (LIGHT): it turns its head back over its shoulder, its ears swinging round after the
    sound, and throws its right wing open out to its side; then it sweeps the open wing round behind it, low, its body
    swinging into it, and folds it back."""
    gather = _pose(r, lift=0.5, yaw=4, side=6, neck=(-2, 14, 0), head=(0, 30, 0), ears=_ears_to(-30), hands={"r": (-38.0, 22.0, -30.0)},
                   poles={"r": (-0.7, -0.3, 0.6)}, wings={"r": 0.5})
    wind = _pose(r, lift=1.5, yaw=10, side=10, roll=-3, neck=(-4, 20, 0), head=(-4, 40, 0), ears=_ears_to(-40), hands={"r": (-56.0, 26.0, -18.0)},
                 poles={"r": (-0.2, -0.2, 1.0)}, wings={"r": 1.0}, feet={"l": _foot("l", dz=-2)})
    hit = _pose(r, lift=0, yaw=-14, side=-10, roll=3, neck=(0, 16, 0), head=(0, 34, 0), ears=_ears_to(-34), hands={"r": (-40.0, 26.0, 40.0)},
                poles={"r": (0.2, -0.3, 1.0)}, wings={"r": 1.0})
    held = _pose(r, lift=-0.5, yaw=-17, side=-12, roll=3.5, neck=(0, 14, 0), head=(0, 30, 0), ears=_ears_to(-30), hands={"r": (-24.0, 24.0, 50.0)},
                 poles={"r": (0.3, -0.3, 1.0)}, wings={"r": 0.9})
    back = _pose(r, yaw=-5, side=-4, head=(0, 10, 0), ears=_ears_to(-8), hands={"r": _hand("r", up=8, dz=10, dx=6)}, poles={"r": (-0.8, -0.5, 0.4)},
                 wings={"r": 0.3})
    return Move("buffet", "LIGHT", 10, 4, 14, through=held, track=0.0, rng=(0, 4.0), kinds=("behind",), damage=0.9, knock=1.2,
                hit=[("f3b_r", (0, 14, 0), 4.0), ("f4b_r", (0, 12, 0), 4.0), ("hand_r", (0, 1, 0), 4.0)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, wind)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


BOUND_BY = 7.0  # blocks the Bound covers


def bound(r):
    """The Bound (HEAVY, a gap closer): it crouches low on its wrists, its elbows' peaks drawn up and its ears laid back;
    then it vaults at you on its wrists, twice, its body flying low with the feet coming under it and its wings flashing
    half open; and as it lands it thrusts its head out and bites, its jaw snapping shut."""
    low = _pose(r, lift=-6, pitch=4, bend=5, neck=(4, 0, 0), head=(4, 0, 0), ears=(-14, 0, 30), hands={s: _hand(s, dz=6) for s in "lr"},
                feet={s: _foot(s, dz=-6) for s in "lr"})

    def vault(u):  # u 0 to 1 through one bound: the wrists push off, it flies, the feet land under it, the wrists reach on
        hands, feet = _gait(u, 2.6 * 16, 0.42, (12.0, 6.0), BOUND)
        return _pose(r, lift=3.0 * math.sin(2 * math.pi * (u - 0.1)) + 1.0, pitch=-6.0 * math.sin(2 * math.pi * (u - 0.05)), bend=4.0 * math.sin(2 * math.pi * u),
                     neck=(-4, 0, 0), head=(6, 0, 0), ears=(-14, 0, 30), hands=hands, feet=feet,
                     wings={sd: 0.34 + 0.12 * math.sin(2 * math.pi * (u - 0.2)) for sd in "lr"})  # the membranes flash half open as it flies
    act = [(f, vault((2 * f) % 1.0)) for f in (0.12, 0.25, 0.38, 0.5, 0.62, 0.75)]
    lunge = _pose(r, lift=0, pitch=9, bend=11, neck=(-16, 0, 0), head=(-18, 0, 0), jaw=44, ears=(-10, 0, 22), hands={s: _hand(s, dz=-9) for s in "lr"},
                  feet={s: _foot(s, dz=-6) for s in "lr"}, tail=6)
    bite = _pose(r, lift=-1.5, pitch=11, bend=14, neck=(-20, 0, 0), head=(-8, 0, 0), jaw=0, ears=(-6, 0, 16), hands={s: _hand(s, dz=-12) for s in "lr"},
                 feet={s: _foot(s, dz=-3) for s in "lr"}, tail=4)
    act += [(0.88, lunge), (1.0, bite)]
    after = _mix(bite, _pose(r), 0.15)  # it keeps its teeth in a moment
    return Move("bound", "HEAVY", 14, 16, 18, through=after, title="The Bound", track=6.0, lock=12, advance=BOUND_BY, rng=(5, 14),
                kinds=("gap",), damage=1.2, knock=1.0, hit=[("head", (0, -2, -16), 5.0), ("jaw", (0, -2, -10), 4.0)], plant=(),
                stages={"windup": [(0.3, _mix(_pose(r), low, 0.5)), (1.0, low)], "active": act, "recover": _back(r, after)}, lag=_lag())


def scream(r):
    """Echo Scream (PERILOUS, the signature): its ears fold forward and it draws its head back and up, swelling, its
    wings lifting off the floor; then it thrusts its head at you and screams with its mouth wide and its throat lit, its
    ears flung forward and ringing, its wings shaking half open; and it lets it go."""
    def draw(f):
        return _pose(r, lift=1.5 * f, pitch=-6 * f, bend=-10 * f, neck=(-16 * f, 0, 0), head=(-22 * f, 0, 0), jaw=6 * f, ears=(24 * f, 4 * f, 0),
                     hands={s: _hand(s, up=8 * f, dz=3 * f) for s in "lr"}, wings={"l": 0.3 * f, "r": 0.3 * f})
    wind = [(0.25, draw(0.3)), (0.5, draw(0.6)), (0.75, draw(0.85)), (1.0, draw(1.0))]

    def ring(a, k):
        return _pose(r, lift=0.5, pitch=5, bend=8, twist=2 * a, neck=(-10, 0, 0), head=(4, 2 * a, 0), jaw=58 - 4 * k, ears={"l": (30, 6 + 8 * a, 0), "r": (30, 6 - 8 * a, 0)},
                     hands={s: (MANTLE[s][0], MANTLE[s][1] + 3 * a * SIDE[s], MANTLE[s][2]) for s in "lr"}, poles=MANTLE_POLE,
                     wings={"l": 0.55 + 0.1 * a, "r": 0.55 - 0.1 * a})
    act = [(0.15, ring(0, 0)), (0.3, ring(1, 0)), (0.45, ring(-1, 1)), (0.6, ring(1, 1)), (0.75, ring(-1, 2)), (0.9, ring(1, 3)), (1.0, ring(0, 4))]
    end = _pose(r, lift=0, pitch=2, bend=3, neck=(-4, 0, 0), jaw=20, ears=(8, 2, 0), hands={s: _hand(s, up=4) for s in "lr"}, wings={"l": 0.2, "r": 0.2})
    return Move("scream", "PERILOUS", 24, 16, 20, through=_mix(end, _pose(r), 0.3), title="Echo Scream", signature=True, track=6.0, ability="Echo Scream",
                rng=(0, 14), kinds=("ranged",), damage=0, knock=0, plant=(), stages={"windup": wind, "active": act, "recover": _back(r, end)}, lag=_lag())


def silence(r):
    """Absolute Silence: it sits back and wraps its wings round itself like a cloak, folds its ears flat down its back
    and bows its head into its wings, and holds its breath: it shivers with it, drawing the wings tighter and rocking;
    at the end its ears rise, slowly, one and then the other."""
    flat = (-22, 0, 54)
    act = [(0.12, _wrapped(r, 0.9, ears=(-20, 0, 50), tight=0.6, sway=1.5)), (0.3, _wrapped(r, 1.0, ears=flat, breathe=0.8, tight=-0.5, sway=-2.0)),
           (0.48, _wrapped(r, 1.0, ears=flat, tight=0.9, sway=2.0)), (0.64, _wrapped(r, 0.95, ears={"l": (-8, 2, 30), "r": flat}, breathe=0.6, tight=-0.4, sway=-1.5)),
           (0.8, _wrapped(r, 0.9, ears={"l": (2, 6, 4), "r": (-14, 0, 36)}, tight=0.5, sway=1.0)),
           (1.0, _wrapped(r, 0.85, ears={"l": (4, 8, 0), "r": (2, 5, 6)}, breathe=0.4, tight=-0.2))]
    sit = _pose(r, lift=-4, pitch=-14, bend=-4, neck=(10, 0, 0), head=(14, 0, 0), ears=(-10, 0, 30), hands={s: _hand(s, up=10, dz=8) for s in "lr"},
                wings={"l": 0.3, "r": 0.3}, feet={s: _foot(s, dz=-4) for s in "lr"})
    open_ = _pose(r, lift=-3, pitch=-14, bend=-4, neck=(-6, 0, 0), head=(-4, 0, 0), ears=(4, 6, 0), hands={s: _hand(s, up=14, dz=6, dx=8) for s in "lr"},
                  poles=OPEN_POLE, wings={"l": 0.6, "r": 0.6}, feet={s: _foot(s, dz=-4) for s in "lr"})
    return Move("silence", "NONE", 16, 30, 18, through=_wrapped(r, 0.6, ears=(2, 6, 0)), title="Absolute Silence", track=0.0, ability="Absolute Silence",
                rng=(0, 99), kinds=("area",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, sit), (1.0, _wrapped(r, 0.7, ears=(-12, 0, 36)))], "active": act, "recover": [(0.45, open_), (0.75, _mix(open_, _pose(r), 0.6))]},
                lag=_lag())


def ears(r):
    """Ears: it lifts its head high and clicks, its jaw chattering, its great ears turning one way and the other as it
    listens for them, and the Listeners come."""
    def click(a, k):
        return _pose(r, lift=2, pitch=-6, bend=-6, yaw=2 * a, roll=-2 * a, side=5 * a, neck=(-18, 4 * a, 0), head=(-14, 10 * a, 8 * a), jaw=16 if k % 2 else 3,
                     ears={"l": (10, 20 * a, 0), "r": (10, -20 * a, 0)}, wings={sd: 0.08 * (1 + (k % 2)) for sd in "lr"})
    act = [(f, click(math.sin(2 * math.pi * 1.5 * f), k)) for k, f in enumerate([j / 12 for j in range(1, 13)])]
    return Move("ears", "NONE", 12, 24, 14, through=_mix(click(0, 0), _pose(r), 0.3), title="Ears", track=0.0, ability="Ears", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.5, click(0.3, 0)), (1.0, click(-0.4, 1))], "active": act,
                                                     "recover": _back(r, _mix(click(0, 0), _pose(r), 0.3))}, lag=_lag())


def wake(r):
    """Its intro, once it has opened its wings (the rise): it lifts its head and its ears unfurl and turn, hunting; it
    finds you, its head snapping round, and gives one short shriek with its mouth wide; it drops onto its wrists."""
    hunt = lambda a: _pose(r, lift=1 + 0.6 * abs(a), pitch=-4, bend=-4, yaw=3 * a, roll=-2.5 * a, side=6 * a, neck=(-12, 6 * a, 0), head=(-10, 16 * a, 10 * a),
                           ears={"l": (6, 18 * a, 0), "r": (6, -18 * a, 0)}, hands={s: _hand(s, up=3 * max(0.0, a * SIDE[s])) for s in "lr"})
    shriek = _pose(r, lift=2, pitch=-6, bend=-8, neck=(-14, 0, 0), head=(-12, 0, 0), jaw=52, ears=(20, 8, 0), hands=MANTLE, poles=MANTLE_POLE,
                   wings={"l": 0.65, "r": 0.65})
    down = _pose(r, lift=-2, pitch=2, bend=3, neck=(-2, 0, 0), ears=(4, 4, 0))

    def cry(a, f=0.0):  # the shriek trembling through it (a), easing toward the drop (f)
        q = _pose(r, lift=2, pitch=-6, bend=-8, twist=2 * a, neck=(-14, 3 * a, 0), head=(-12, 5 * a, 3 * a), jaw=52 + 6 * a,
                  ears={"l": (20, 8 + 6 * a, 0), "r": (20, 8 - 6 * a, 0)}, hands={s: (MANTLE[s][0], MANTLE[s][1] + 2 * a * SIDE[s], MANTLE[s][2]) for s in "lr"},
                  poles=MANTLE_POLE, wings={"l": 0.65 + 0.12 * a, "r": 0.65 - 0.12 * a})
        return _mix(q, down, f)
    return Move("wake", "NONE", 14, 30, 14, through=_mix(down, _pose(r), 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.35, hunt(-1)), (0.7, hunt(1)), (1.0, hunt(-0.5))],
                        "active": [(0.15, hunt(0.6)), (0.3, hunt(-0.2)), (0.42, shriek), (0.5, cry(1)), (0.58, cry(-1)), (0.66, cry(0.8, 0.1)), (0.74, cry(-0.6, 0.2)),
                                   (0.84, cry(0.3, 0.45)), (1.0, down)],
                        "recover": _back(r, _mix(down, _pose(r), 0.4))}, lag=_lag())


# the ground (SPEC §35.10): the wrists and thumbs, and the feet, stay out of the floor
GROUND = [([f"hand_{s}", f"thumb_{s}"], [(f"fore_{s}", 0, 30), (f"arm_{s}", 0, 30)]) for s in "lr"] + \
         [([f"foot_{s}"], [(f"shin_{s}", 0, 30), (f"thigh_{s}", 0, 30)]) for s in "lr"]
# a wing brought down onto the floor flexes on it: each finger bends out of the wing's plane (its z), the tip first
FINGERS_GROUND = [([f"{nm}a_{s}", f"{nm}b_{s}"], [(f"{nm}b_{s}", 2, 60), (f"{nm}a_{s}", 2, 50), (f"{nm}a_{s}", 0, 40)]) for s in "lr" for nm, *_ in FINGERS]
STANDS = {"rake": (("l",), ("l", "r")), "rake_b": (("r",), ("l", "r")), "found": ((), ()), "buffet": (("l",), ("l", "r")), "bound": ((), ()),
          "scream": ((), ("l", "r")), "silence": ((), ()), "ears": (("l", "r"), ("l", "r")), "wake": ((), ())}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [rake(r, "r", "rake"), rake(r, "l", "rake_b"), found(r), buffet(r), bound(r), scream(r), silence(r), ears(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        hands, feet = STANDS[m.id]
        if hands or feet:
            _plant(r, core.clip_name(m), hands, feet)
    for n in r.clips:
        if n != "death":
            _on_floor(r, n)
    core.ground_pass(r, GROUND + FINGERS_GROUND, clips=[n for n in r.clips if n != "death"])
    # and as it falls and lies: what lands on the floor rests on it (its arms and ears give, its fingers flex)
    core.ground_pass(r, [([f"arm_{s}", f"fore_{s}"], [(f"arm_{s}", 0, 40), (f"arm_{s}", 2, 40)]) for s in "lr"] +
                     [([f"ear_{s}", f"ear2_{s}"], [(f"ear_{s}", 0, 50), (f"ear_{s}", 2, 40)]) for s in "lr"] + FINGERS_GROUND, clips=["death"])
    return mv


# Its sounds (SPEC §35.8): the bat's own squeak pitched down to a barn's size, the warden's listening and its sonic
# boom for the scream, leather for its wings; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.bat.ambient", 1.0, 0.4), ("entity.phantom.flap", 0.8, 0.6)],
    "tell_heavy": [("entity.warden.listening", 1.2, 0.8), ("entity.phantom.flap", 1.0, 0.5)],
    "tell_perilous": [("entity.warden.sonic_charge", 1.4, 0.8), ("entity.bat.ambient", 1.2, 0.3)],
    "swing_light": [("entity.phantom.swoop", 0.9, 0.7), ("entity.player.attack.sweep", 0.7, 0.6)],
    "swing_heavy": [("entity.phantom.flap", 1.4, 0.4), ("entity.player.attack.knockback", 0.8, 0.6)],
    "cast": [("entity.warden.sonic_boom", 1.4, 0.7), ("entity.bat.hurt", 1.2, 0.3)],
    "impact": [("entity.warden.attack_impact", 1.0, 0.7), ("block.sculk.break", 1.2, 0.5)],
    "roar": [("entity.bat.hurt", 1.6, 0.3), ("entity.warden.roar", 0.8, 0.9)],
    "step": [("block.sculk.step", 0.6, 0.6), ("entity.phantom.flap", 0.3, 0.8)],
}
MOVE_SOUNDS = {"scream": {"swing": "cast", "impact": ""}, "silence": {"swing": "", "impact": ""}, "ears": {"swing": "tell_light", "impact": ""},
               "wake": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},  # it waits wrapped in its wings, and opens them
    "phases": [
        {"pool": [
            {"move": "rake", "weight": 3, "when": "near", "then": [["rake_b"], ["rake_b", "found"]]},
            {"move": "found", "weight": 2, "when": "near"},
            {"move": "bound", "weight": 3, "when": "far", "then": [["rake"]]},
            {"move": "scream", "weight": 2},
            {"move": "buffet", "weight": 3, "when": "behind"},
        ]},
        # it stops breathing with the second phase, as in DeepBestiary: the silence, then the ears come
        {"transition": "silence", "pool": [
            {"move": "rake", "weight": 3, "when": "near", "then": [["rake_b"], ["rake_b", "found"]]},
            {"move": "found", "weight": 3, "when": "near"},
            {"move": "bound", "weight": 3, "when": "far", "then": [["rake", "rake_b"]]},
            {"move": "scream", "weight": 2},
            {"move": "ears", "weight": 1},
            {"move": "silence", "weight": 1},
            {"move": "buffet", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


STUB = False


def design():
    r = body()
    r.pack()
    mv = [] if STUB else clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT if not STUB else {}, "paint": paint, "scale": SCALE, "half": 1.4,
            "sounds": SOUNDS if not STUB else {}, "move_sounds": MOVE_SOUNDS if not STUB else {}}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_l", (0, 0, 0)), ("fore_l", (0, 0, 0)), ("arm_l", (0, 0, 0)), ("head", (0, 0, 0)), ("ear2_l", (0, -16, 0)),
                 ("f3b_l", (0, 20, 0)), ("foot_l", (0, 0, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones), "trail", TRAIL)
    print(core.floating(r))


if __name__ == "__main__":
    probe()
