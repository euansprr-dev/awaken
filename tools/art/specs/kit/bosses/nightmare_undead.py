"""The Hollow Crowd (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Brute, the fear of a crowd. The Throng,
picked 2026-10-30: "They all have the same face."

A hunched giant made of people, pressed together into one body. Its back is a hill of shoulders and coats and bare
backs, its legs are bundles of legs bound in rags, its long arms drag their knuckles. Heads stand out of it everywhere,
out of its shoulders, its back and its belly, and every one of them has the same face: grey and slack, its mouth hanging
open, its eyes two black holes. They all turn to look at you. Small arms grow out of it too, thin and pale, reaching and
groping at the air. Its own head sits low on the front of it like a gorilla's, bigger than the rest, the same face.

Moves (SPEC §35.11): the Swipe and the Grab (LIGHT, a string: a long backhand across in front of it, then a reach with
the other hand that closes on you, all its small arms reaching with it); Crush (HEAVY: it rears up with both fists
over its head and brings them down together); Surge (HEAVY, its gap closer: it drops onto its knuckles, every face on
you, and drives at you in one long lunge, arms wide to scoop you up); More of Them (it tears people off itself and
throws them down, and they get up); Closer (into the second phase: every head turns to you at once, its jaws all drop,
it shudders and people fall off it, and it takes one step closer); for whoever is behind it, the Elbow (it twists and
swings an arm round behind it); and All of Them (the signature: it rears up to its whole height, arms wide, and falls on
you, the whole crowd of it, and gathers itself up off the floor). Its intro: it stands with every head bowed; they lift
one by one to look at you, the same face each time, its own last. Struck down the first time it falls on its face and
lies there, and the heads lift one by one and it gets up again. Broken, it sinks onto one knee and its knuckles. Its
death: it sinks to its knees, and its heads look every way, lost, and it slumps over onto its side, and the small arms
reach a while longer, then stop.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES, FACE_LIGHT

ID = "nightmare_undead"
SCALE = 1.7  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Throng (docs/bosses/ROLLOUT.md, A). Short heavy legs bent at the knee, a body bent far forward under the hill of
# its back, long arms; about 5 blocks to the top of its back. Value story: the faces are the palest thing on it and
# their eyes the darkest; the bare skin in the mass a darker grey, the clothes darker still and muted; the creases
# between the bodies near black.
P = plans.Biped(leg=32, thigh=16, foot=(12, 4, 15), pelvis=(24, 12, 18), spine=(24, 18, 20), chest=(40, 24, 26), neck=(9, 9, 9),
                head=(14, 14, 14), arm=(12, 25, 12), forearm=(11, 23, 11), hand=(10, 9, 12), stance=9, hunch=42, leg_w=(13, 10))
KNEE = 18  # its legs stand bent: the thigh forward this much, the shin back twice it, the foot flat
SHIN, FOOT_AT = 12, 4  # the shin bone's length and the foot's height
HIP_AT = 2  # the hips' joints sit this far below the pelvis' pivot
HIP_Y = 24 - (HIP_AT + P.thigh * math.cos(math.radians(KNEE)) + SHIN * math.cos(math.radians(KNEE)) + FOOT_AT)
HUNCH = (16, 26)  # the spine's and the chest's rest pitch: its body bends 42 degrees forward
NECK = -30  # the neck's rest pitch on the chest: its head comes forward out of the front of the hill
REST_HAND = {"l": (25.0, 20.0, -14.0), "r": (-25.0, 20.0, -14.0)}  # the wrists at rest, hanging forward of its knees
POLE = {"l": (0.5, 0.0, 1.0), "r": (-0.5, 0.0, 1.0)}  # elbows back and out
JAW = 14  # its jaw hangs open at rest
ARMS = tuple(f"{b}_{s}" for b in ("shoulder", "arm", "forearm", "hand", "fingers") for s in "lr")
ON_TIME = {b: 0 for b in ARMS}  # its hands go where they are sent: no trailing arms

# the heads in the mass: (parent, x, y, z, rot): standing up out of its shoulders, its back and its belly, all facing
# the way it faces (the chest leans 42 degrees, the spine 16)
SMALL = (("chest", 17, -22, -6, (-36, 14, 10)), ("chest", -17, -21, -4, (-40, -16, -8)), ("chest", 7, -35, 6, (-48, 6, 6)),
         ("chest", -8, -33, 12, (-30, -10, -10)), ("spine", 8, -7, -10.5, (-22, 14, 6)), ("chest", -11, -8, -13, (-46, -16, -4)))
# the small arms: (parent, x, y, z, rot) where each grows out of it and how it leans at rest; REACH: how far each
# reaches out toward the front when the crowd grabs (on top of rest), LIMP: how each flops when it lies
SMALL_ARMS = (("chest", 12, -11, -12.5, (-80, 0, 24), 60), ("spine", -9, -10, -9.5, (-50, 0, -30), 50), ("chest", -7, -22, 13.5, (101, 0, -20), -60),
              ("chest", 20, -6, 4, (-40, 0, 60), 55))
REACH = ((-30, 0, -10), (-40, 0, 14), (60, 0, 10), (-40, 0, -30))
NSA = len(SMALL_ARMS)
HEADS = [f"h{k}" for k in range(len(SMALL))]
SMALLS = [f"{b}{k}" for k in range(NSA) for b in ("sa", "sf", "sh")]


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, HIP_Y, 0))
    pel.cube("pelvis", -12, -6, -9, 24, 12, 18)
    rf = pel.bone("rag_f", (0, 4, -9.5))
    rf.cube("rag_f", -10, 0, -1, 20, 11, 2)
    rb = pel.bone("rag_b", (0, 4, 9.5))
    rb.cube("rag_b", -11, 0, -1, 22, 10, 2)
    # short heavy legs, bundles of legs bound in rags, bent at the knee; broad bare feet
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        th = pel.bone("thigh_" + nm, (side * P.stance, HIP_AT, 0), (-KNEE, 0, 0))
        th.cube("thigh_" + nm, -6.5, -3, -6.5, 13, 19, 13, mirror=mir)
        th.cube("knee_" + nm, -5, 12, -7.5, 10, 6, 2, mirror=mir)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0), (2 * KNEE, 0, 0))
        sn.cube("shin_" + nm, -5, -1, -5, 10, 13, 10, mirror=mir)
        sn.cube("ankle_" + nm, -5.5, 8, -5.5, 11, 3, 11, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, SHIN, 0), (-KNEE, 0, 0))
        ft.cube("foot_" + nm, -6, 0, -10, 12, 4, 15, mirror=mir)
        for k, x in enumerate((-6, -2, 2)):
            ft.cube(f"toe{k}_" + nm, x, 1, -13, 4, 3, 3, mirror=mir)
    # the body bent forward: the belly, and over it the hill of its back, people pressed into it shoulder to shoulder
    sp = pel.bone("spine", (0, -5, 0), (HUNCH[0], 0, 0))
    sp.cube("spine", -12, -17, -10, 24, 18, 20)
    sp.cube("belly", -10, -14, -12.5, 20, 11, 3)
    ch = sp.bone("chest", (0, -16, 0), (HUNCH[1], 0, 0))
    ch.cube("chest", -20, -24, -13, 40, 24, 26)
    ch.cube("hump", -16, -31, -4, 32, 8, 18)
    ch.cube("hump2", -11, -35, 0, 22, 5, 12)
    ch.cube("backs", -18, -27, 10, 14, 10, 4)
    ch.cube("backs2", 3, -26, 11, 13, 9, 3)
    ch.cube("collar", -9, -27, -15, 18, 5, 8)
    # someone sunk head first into the hill of its back, their legs sticking out of it
    dg = ch.bone("dangle", (6, -14, 13), (74, 0, -10))
    for k, x in enumerate((-3.5, 0.5)):
        dg.cube(f"dleg{k}", x, -2, -1.5, 3, 13, 3)
        dg.cube(f"dfoot{k}", x, 10, -4.5, 3, 2, 4)
    # rags hanging off the bottom of the hill of its back over its hips
    ck = ch.bone("cloak", (0, -2, 13.5), (-sum(HUNCH), 0, 0))
    for k, (x, w, h) in enumerate(((-14, 8, 18), (-5, 9, 23), (5, 8, 16))):
        ck.cube(f"cloak{k}", x, 0, -1, w, h, 2)
    # the heads in the mass, the same face on every one
    for k, (par, x, y, z, rot) in enumerate(SMALL):
        h = r.by_name[par].bone(f"h{k}", (x, y, z), rot)
        h.cube(f"h{k}", -4.5, -10, -4.5, 9, 10, 9)
        h.cube(f"hank{k}", -4.5, -8, 4, 9, 6, 1)
    # the small arms growing out of it, thin and pale
    for k, (par, x, y, z, rot, bend) in enumerate(SMALL_ARMS):
        a = r.by_name[par].bone(f"sa{k}", (x, y, z), rot)
        a.cube(f"sa{k}", -1.5, -1, -1.5, 3, 11, 3)
        f = a.bone(f"sf{k}", (0, 10, 0), (bend, 0, 0))
        f.cube(f"sf{k}", -1.5, 0, -1.5, 3, 10, 3)
        hd = f.bone(f"sh{k}", (0, 10, 0), (-0.4 * bend, 0, 0))
        hd.cube(f"sh{k}", -2, 0, -1, 4, 4, 2)
        hd.cube(f"sfing{k}", -2, 4, -1, 4, 3, 1)
    # the long arms: a hill of a shoulder, a thick arm bound at the elbow and the wrist, a big hand dragging its knuckles;
    # the fingers on their own bone (they close into a fist)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        sh = ch.bone("shoulder_" + nm, (side * 21, -19, -5), (-sum(HUNCH), 0, 0))
        sh.cube("shoulder_" + nm, -7, -6, -7, 14, 13, 14, mirror=mir)
        a = sh.bone("arm_" + nm, (0, 4, 0))
        a.cube("arm_" + nm, -6, -1, -6, 12, 26, 12, mirror=mir)
        a.cube("elbow_" + nm, -6.5, 19, -6.5, 13, 5, 13, mirror=mir)
        f = a.bone("forearm_" + nm, (0, 25, 0))
        f.cube("forearm_" + nm, -5.5, 0, -5.5, 11, 23, 11, mirror=mir)
        f.cube("wrap_" + nm, -6, 15, -6, 12, 4, 12, mirror=mir)
        h = f.bone("hand_" + nm, (0, 23, 0))
        h.cube("hand_" + nm, -5, 0, -6, 10, 9, 12, mirror=mir)
        h.cube("thumb_" + nm, (-4 if side > 0 else 0), 1, -8.5, 4, 7, 3, mirror=mir)
        g = h.bone("fingers_" + nm, (0, 9, 0))
        for k, z in enumerate((-6, -3, 0, 3)):
            g.cube(f"finger{k}_" + nm, -2, 0, z, 4, 9 - (k == 3), 3, mirror=mir)
    # its own head low on the front of the hill, bigger than the rest, the same face; its jaw hangs
    nk = ch.bone("neck", (0, -22, -11), (NECK, 0, 0))
    nk.cube("neck", -4.5, -9, -4.5, 9, 10, 9)
    hd = nk.bone("head", (0, -8, -1), (-(sum(HUNCH) + NECK), 0, 0))
    hd.cube("head", -7, -14, -7, 14, 11, 14)
    hd.cube("hair", -7.5, -15, -5.5, 15, 2, 13)
    hd.cube("hair_b", -7.5, -13, 5, 15, 12, 3)
    hd.cube("nose", -1.5, -8, -8, 3, 4, 1)
    jw = hd.bone("jaw", (0, -3, 4), (JAW, 0, 0))
    jw.cube("jaw", -6, 0, -10.5, 12, 4, 12)
    r.look = [("head", 0.6, 0.4), ("neck", 0.3, 0.25)] + [(h, 0.7, 0.5) for h in HEADS]
    r.size = P.leg / 36.0
    r.shadow = 2.6
    for side in "lr":
        ik.rest_limb(r, "arm_" + side, "forearm_" + side, "hand_" + side, REST_HAND[side], POLE[side])
    # secondary parts (SPEC §35.10): the rags and the jaw swing, the small arms dangle, the heads wobble on their necks
    r.springs = ([("rag_f", 0.4, 1.4, 0.1), ("rag_b", 0.4, 1.3, 0.1), ("cloak", 0.35, 1.2, 0.1), ("dangle", 0.5, 1.6, 0.12), ("jaw", 0.5, 2.4, 0.2)]
                 + [(f"sf{k}", 0.5, 2.0, 0.15) for k in range(NSA)] + [(h, 0.3, 2.2, 0.15) for h in HEADS])
    r.guard = ["hand_l", "hand_r", "fingers_l", "fingers_r", "head", "jaw"]  # bones, not cubes
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#121512", "#1b1f1b", "#262a26", "#323732", "#404640", "#4f564f", "#606860", "#727a71", "#868d83", "#9aa096", "#aeb3a8", "#c1c5ba",
            "#d2d5ca")
CLOTHES = (Ramp("#100d0b", "#1a1612", "#25201a", "#312a22", "#3d352b", "#4a4035", "#584d40"),  # a brown coat
           Ramp("#0c0e11", "#14171b", "#1d2126", "#272c33", "#313740", "#3c434e", "#48505c"),  # a grey-blue one
           Ramp("#120a0a", "#1c1111", "#271817", "#33201e", "#3f2926", "#4c322f"),  # an old red
           Ramp("#11100b", "#1a1912", "#24221a", "#2f2c22", "#3a362b", "#464135"))  # a dun one
HAIR = Ramp("#060505", "#0c0a09", "#131110", "#1b1816", "#24201d", "#2d2824")
HOLE = hexc("#020202")
MOUTH = Ramp("#070404", "#100808", "#190d0c", "#241413")
MASS = ("pelvis", "spine", "belly", "chest", "hump", "hump2", "backs", "backs2", "collar")


def _bodies(f, S):
    """The bodies pressed into one face of the mass: jittered points a cell apart, each the middle of one body (a back,
    a shoulder, a coat), [(x, y, kind)]."""
    out = []
    for gx in range(-1, int(f.w / S) + 2):
        for gy in range(-1, int(f.h / (S * 0.6)) + 2):
            h = R._h(f.cube.name, f.name, gx, gy)
            out.append((gx * S + (gy % 2) * S * 0.5 + (h % 97) / 97 * S * 0.5, gy * S * 0.6 + (h // 97 % 89) / 89 * S * 0.4, h % 100))
    return out


def _mass(P_, c, base=4.6, skin_share=0.3, faces_in=0.0, S=14.0, limbs=1.0):
    """A patch of the crowd: every lump another body pressed into it, the back of a coat or a bare back, wider than it
    is tall, rounded (lit over its top, shadowed under it), the creases between them near black; across them arms
    lie, pale, a hand at the end of each, and here and there the back of a head; and here and there a face pressed into
    it (faces_in: the share of the bare lumps that are one)."""
    cube = P_.cubes[c]
    if not P_.own(cube):
        return
    for fname in ("top", "bottom") + SIDES:
        f = R.Face(P_.skin, cube, fname)
        if f.w <= 0 or f.h <= 0:
            continue
        pts = _bodies(f, S)
        for i, j in f.each():
            x, y = i + 0.5, j + 0.5
            ds = sorted((math.hypot((x - px) * 0.62, y - py), k) for k, (px, py, _) in enumerate(pts))
            (d1, k1), (d2, _) = ds[0], ds[1]
            px, py, kind = pts[k1]
            bare = kind < skin_share * 100
            ramp = SKIN if bare else CLOTHES[kind % len(CLOTHES)]
            val = (base - 0.3 if bare else 3.1) + FACE_LIGHT[fname]
            if fname in SIDES:
                val += 0.6 * (0.5 - j / max(1, f.h - 1))
            val += 1.0 * max(0.0, 1 - d1 / (S * 0.42)) + 0.6 * max(-1.0, min(1.0, (py - y) / (S * 0.3)))  # rounded, lit over the top
            if d2 - d1 < 0.55:
                val = 0.5 if bare else 0.2  # the crease between two bodies
            elif d2 - d1 < 1.3:
                val -= 0.8
            if bare and kind % 3 == 0 and abs(x - px) < 0.6 and d1 < S * 0.25:
                val -= 0.9  # a bare back's spine
            elif not bare and kind % 4 == 1 and abs(y - (py - S * 0.12)) < 0.6 and d1 < S * 0.3:
                val += 0.9  # a coat's collar
            val += 0.25 * ((R._h(c, fname, i, j) % 5) - 2) / 2
            f.set(i, j, ramp.at(val))
        if fname not in SIDES and fname != "top":
            continue
        # arms lying across it, each with its hand; the backs of heads, hair over a pale nape
        n = int(round(limbs * f.w * f.h / 260))
        for k in range(n):
            h = R._h(c, fname, "arm", k)
            x0, y0 = h % max(1, f.w), (h // 101) % max(1, f.h)
            ang = ((h // 7) % 100 / 100 - 0.5) * 1.4 + (math.pi if h % 2 else 0)
            ln = 7 + (h // 13) % 6
            dx, dy = math.cos(ang), math.sin(ang)
            for t in range(ln):
                xi, yi = x0 + dx * t, y0 + dy * t
                f.set(int(round(xi)), int(round(yi)), SKIN.at(base + 2.2 + FACE_LIGHT[fname]))
                f.set(int(round(xi)), int(round(yi + 1)), SKIN.at(base + 0.8 + FACE_LIGHT[fname]))
                f.set(int(round(xi)), int(round(yi + 2)), SKIN.at(1.0))
            hx, hy = int(round(x0 + dx * ln)), int(round(y0 + dy * ln))
            sx = 1 if dx >= 0 else -1
            for ddx, ddy, v in ((0, -1, 2.4), (sx, -1, 2.6), (0, 0, 2.6), (sx, 0, 2.8), (2 * sx, 0, 2.2), (0, 1, 1.8), (sx, 1, 2.0), (2 * sx, 1, 1.6),
                                (3 * sx, 0, 1.8), (3 * sx, 1, 1.4), (sx, 2, 1.0), (2 * sx, 2, 0.6)):
                f.set(hx + ddx, hy + ddy, SKIN.at(base + v + FACE_LIGHT[fname]))
        for k in range(n // 2):
            h = R._h(c, fname, "nape", k)
            x0, y0 = h % max(1, f.w - 4), (h // 101) % max(1, f.h - 4)
            for ddx in range(4):
                for ddy in range(3):
                    if (ddx, ddy) in ((0, 0), (3, 0)):
                        continue
                    f.set(x0 + ddx, y0 + ddy, HAIR.at(2.6 - ddy * 0.6 + (ddx in (1, 2)) * 0.6))
            f.set(x0 + 1, y0 + 3, SKIN.at(base + 0.6))
            f.set(x0 + 2, y0 + 3, SKIN.at(base + 0.2))
        if faces_in and fname in SIDES:  # a face pressed into the mass: a pale oval, two holes, a mouth
            for px, py, kind in pts:
                i0, j0 = int(round(px - 2.5)), int(round(py - 2.5))
                if kind < skin_share * 100 and (kind // 3) % int(round(1 / faces_in)) == 0 and 0 <= i0 and i0 + 5 <= f.w and 0 <= j0 and j0 + 5 <= f.h:
                    _small_face(f, i0, j0)


def _small_face(f, i0, j0):
    """A face 5 wide and 5 tall pressed into the mass, its top left corner at (i0, j0)."""
    for di in range(5):
        for dj in range(5):
            if (di in (0, 4) and dj in (0, 4)):
                continue
            f.set(i0 + di, j0 + dj, SKIN.at(8.6 - 0.5 * dj))
    for di in (1, 3):
        f.set(i0 + di, j0 + 1, HOLE)
    f.set(i0 + 2, j0 + 2, SKIN.at(9.6))
    f.set(i0 + 2, j0 + 3, MOUTH.at(0))
    f.set(i0 + 2, j0 + 4, SKIN.at(6.0))


def _face(f):
    """The face, the same on every head: grey and slack, lank hair hanging over the brow, two black holes for eyes in
    dark sockets with bags under them, a long nose, hollow cheeks, the mouth hanging open. Drawn on a head's front (f:
    its north face): the small heads' (9 by 10) or, bigger, its own (14 by 11, its mouth the gap over its jaw)."""
    W, H = f.w, f.h
    big = W >= 12
    for i, j in f.each():
        edge = abs(i - (W - 1) / 2) / (W / 2)
        f.set(i, j, SKIN.at(9.8 - 1.4 * j / max(1, H - 1) - 1.2 * edge ** 3))  # palest across the brow and cheekbones
    for i in range(W):  # the fringe, in strands
        for j in range(3 if big else 2):
            if j == 0 or (R._h("fringe", W, i) % 3 != 0 and j < 2) or (big and j == 2 and R._h("fringe", W, i) % 4 == 0):
                f.set(i, j, HAIR.at(1.6 + (i % 2) + 0.4 * j))
    ey, es = (4, 3) if big else (3, 2)
    for x0 in ((2, 9) if big else (1, 6)):
        for di in range(-1, es + 1):  # the socket round each eye, darkest over it
            f.set(x0 + di, ey - 1, SKIN.at(5.0))
            f.set(x0 + di, ey + es, SKIN.at(6.4))  # the bag under it
        for dj in range(es):
            f.set(x0 - 1, ey + dj, SKIN.at(6.6))
            f.set(x0 + es, ey + dj, SKIN.at(7.0))
        for di in range(es):
            for dj in range(es):
                f.set(x0 + di, ey + dj, HOLE)
    if big:
        for dj in range(5, 9):  # the long nose
            f.set(6, dj, SKIN.at(10.6))
            f.set(7, dj, SKIN.at(9.6))
        f.set(6, 9, SKIN.at(5.2))
        f.set(7, 9, SKIN.at(5.2))
        for dj in (7, 8, 9):  # the hollow cheeks
            for di in (1, 2, 11, 12):
                f.set(di, dj, SKIN.at(7.0))
        for di in range(4, 10):  # the upper lip; the mouth is the gap under it
            f.set(di, 10, SKIN.at(6.6))
    else:
        f.set(4, 4, SKIN.at(10.6))
        f.set(4, 5, SKIN.at(10.2))
        f.set(4, 6, SKIN.at(5.4))
        for di, dj, v in ((3, 7, 1), (4, 7, 0), (5, 7, 1), (3, 8, 1), (4, 8, 0), (5, 8, 1)):  # the mouth, hanging open
            f.set(di, dj, MOUTH.at(v))
        for di in (3, 4, 5):
            f.set(di, 9, SKIN.at(6.6))


def _chin(f):
    """Its own head's jaw from the front: the lower lip, the chin."""
    for i, j in f.each():
        edge = abs(i - (f.w - 1) / 2) / (f.w / 2)
        f.set(i, j, SKIN.at((6.4 if j == 0 else 8.2 - 0.6 * j) - 1.2 * edge ** 3))


def paint(r, P_):
    """Grey dead skin and old clothes, pressed together body against body; every head the same pale face with black
    holes for eyes; lank dark hair; rags."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in MASS:
        _mass(P_, c, base=4.8 if c in ("hump", "hump2", "backs", "backs2") else 4.4, skin_share=0.32 if c != "collar" else 0.1,
              faces_in=0.5 if c in ("chest", "spine", "hump", "belly", "backs") else 0.0, limbs=1.5)
    # the legs: bundles of legs, bare skin and trouser cloth in long strips, bound with rags at the knee and ankle
    for c in of("thigh_", "shin_"):
        _mass(P_, c, base=4.2, skin_share=0.4, S=11.0, limbs=0.5)
    for c in of("knee_", "ankle_", "elbow_", "wrap_") + ["rag_f", "rag_b", "cloak0", "cloak1", "cloak2"]:
        P_.paint(c, CLOTHES[3] if c.startswith(("rag", "cloak")) else CLOTHES[0], 3.0, R.p_blotch(c, scale=3, amount=0.6), grad=0.8, rim=0.6,
                 ao=0.6, noise=0.5, nscale=3)
        for fname in SIDES:  # the rags' torn ends and the bindings' wound edges
            f = P_.face(c, fname)
            for i in range(f.w):
                if c.startswith(("rag", "cloak")) and (R._h(c, fname, i) % 3 == 0):
                    f.set(i, f.h - 1, hexc("#000000", 0))
                elif not c.startswith(("rag", "cloak")) and i % 3 == 0:
                    for j in range(f.h):
                        f.set(i, j, CLOTHES[0].at(1.2))
    # bare feet, hands and the small arms: grey skin, darker in the creases, the nails dark
    for c in of("dleg"):
        P_.paint(c, CLOTHES[1], 3.4, lambda f, i, j: -1.2 * (i == 0), grad=1.0, rim=0.6, ao=0.8, noise=0.4)
    for c in of("foot_", "toe", "hand_", "thumb_", "finger", "sa", "sf", "sh", "sfing", "shoulder_", "arm_", "forearm_", "neck", "dfoot"):
        if c.startswith(("shoulder_", "arm_", "forearm_")):
            _mass(P_, c, base=4.6, skin_share=0.7, S=11.0, limbs=0.6)
            continue
        P_.paint(c, SKIN, 5.6 if c.startswith(("sa", "sf", "sh", "sfing")) else 5.0, None, grad=1.0, rim=0.9, ao=1.0, noise=0.6, nscale=4)
    for c in of("toe", "finger", "sfing"):
        f = P_.face(c, "north" if c.startswith("toe") else "bottom")
        for i in range(f.w):
            f.set(i, f.h - 1 if c.startswith("toe") else 0, SKIN.at(2.0))
    # the heads: grey skin, lank dark hair over the top and down the back, the same face on every front
    for c in ["head", "jaw", "nose"] + HEADS:
        P_.paint(c, SKIN, 6.4, None, grad=1.2, rim=0.8, ao=0.9, noise=0.5, nscale=4)
    for c in ["hair", "hair_b"] + of("hair", "hank"):
        P_.paint(c, HAIR, 2.2, lambda f, i, j: 0.8 * ((i + R._h(f.cube.name, f.name, i) % 3) % 3 == 0) - 0.6 * (j / max(1, f.h - 1)), grad=0.6,
                 rim=0.6, ao=0.4, noise=0.4)
        for fname in ("west", "east", "south"):  # the hair hangs in strands at its ends
            f = P_.face(c, fname)
            for i in range(f.w):
                if R._h(c, fname, i) % 2:
                    f.set(i, f.h - 1, hexc("#000000", 0))
    for h in HEADS:
        _face(P_.face(h, "north"))
        f = P_.face(h, "top")
        for i, j in f.each():
            f.set(i, j, HAIR.at(2.0 + 0.8 * ((i + R._h(h, i) % 3) % 3 == 0) - 0.5 * (j == f.h - 1)))
        for fname in ("west", "east"):  # an ear, and the hair over the top of the side
            f = P_.face(h, fname)
            for i, j in f.each():
                if j <= 2:
                    f.set(i, j, HAIR.at(2 + (i % 2)))
            f.set(f.w // 2, f.h // 2, SKIN.at(4.0))
    # its own head: the same face, bigger; the jaw hanging, the mouth behind it dark, a few teeth left on the jaw
    _face(P_.face("head", "north"))
    for fname in ("west", "east"):
        f = P_.face("head", fname)
        for i, j in f.each():
            if j <= 4:
                f.set(i, j, HAIR.at(2 + (i % 2)))
        f.set(f.w // 2, 6, SKIN.at(4.0))
        f.set(f.w // 2, 7, SKIN.at(4.0))
    f = P_.face("head", "bottom")  # the roof of the mouth, seen when the jaw drops
    for i, j in f.each():
        f.set(i, j, MOUTH.at(1.5 + 1.5 * (j / max(1, f.h - 1))))
    f = P_.face("jaw", "top")
    for i, j in f.each():
        f.set(i, j, MOUTH.at(1 + 1.5 * (j / max(1, f.h - 1))))
        if j == f.h - 1 and i % 3 != 0 and 1 < i < f.w - 2:
            f.set(i, j, SKIN.at(10.0 - (i % 2)))  # the teeth along the front
    _chin(P_.face("jaw", "north"))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing

def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), jaw=0.0, left=None, right=None,
          lhand=None, rhand=None, legs=None, feet=("l", "r"), step=None, curl=(0.0, 0.0), nod=0.0, heads=None, reach=0.0, arms=None, more=None):
    """A whole-body key. It bends at the hips (hips: turn, at: x, up, z), the spine, the chest, the neck and its own head;
    jaw: how much further its jaw drops. The feet stay planted where they stand at rest (step: {side: (dx, dy, dz)}
    model pixels, y down) unless legs pose them. left, right: where that wrist goes, (x, up, z) model pixels or ("on",
    bone, point) a point on a bone (left alone, the arm hangs as at rest); lhand, rhand: (ydir, xdir) for that hand;
    curl (left, right) closes the big fingers into a fist (1). nod: every head in the mass bows (+) or lifts (-);
    heads: {k: turn} one head's own turn on top; reach: the small arms reach out toward the front (1) or draw in (-1);
    arms: {k: (upper, fore, hand)} a small arm's own turns on top."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head, "jaw": (jaw, 0, 0)}
    for k, h in enumerate(HEADS):
        d = (heads or {}).get(k, (0, 0, 0))
        p[h] = (nod + d[0], d[1], d[2])
    for k in range(NSA):
        d = (arms or {}).get(k, ((0, 0, 0), (0, 0, 0), (0, 0, 0)))
        bend = SMALL_ARMS[k][5]
        p[f"sa{k}"] = tuple(reach * REACH[k][i] + d[0][i] for i in range(3))
        p[f"sf{k}"] = (-0.75 * bend * max(0.0, reach) + 0.4 * bend * max(0.0, -reach) + d[1][0], d[1][1], d[1][2])
        p[f"sh{k}"] = (0.35 * bend * max(0.0, reach) + d[2][0], d[2][1], d[2][2])
    if legs:
        p.update(legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    for side, o in (("l", lhand), ("r", rhand)):
        if o:
            fr = core.frames(r, core.as_sample(p))
            p["hand_" + side] = ik.orient(r, fr, "hand_" + side, *o)
    for k, side in enumerate("lr"):
        p["fingers_" + side] = (0, 0, (1 if side == "l" else -1) * 90 * curl[k])
    return p


def _mix(a, b, f):
    """Between two keys (the bones of either), f of the way from a to b."""
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


def _back(r, end):
    """A recovery in two steps to rest, so nothing snaps home."""
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.8))]


def _on(r, end, f=0.08):
    """The follow-through: the end carried on a little past where it was going (away from rest)."""
    return _mix(end, _pose(r), -f)


def _solid(r):
    """What of it rests on the floor when it lies there (not the rags, the legs in its back or the small arms)."""
    return [b.name for b in r.bones if b.cubes and not b.name.startswith(("rag", "cloak", "dangle", "sa", "sf", "sh"))]


def _set_on(r, p, bones=None, gap=0.3):
    """The key let down (or lifted) at the hips until the lowest of it (or of the bones named) rests on the floor."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, bones or _solid(r)) - (core.GROUND - gap)
    q = dict(p)
    pv = q["pelvis"]
    q["pelvis"] = {"rot": pv["rot"], "pos": (pv["pos"][0], pv["pos"][1] + up, pv["pos"][2])}
    return q


def _clear(r, p, gap=0.4):
    """The key as it is, or lifted just enough that nothing solid of it is under the floor."""
    fr = core.frames(r, core.as_sample(p))
    return p if core.lowest(r, fr, _solid(r)) <= core.GROUND - gap else _set_on(r, p, gap=gap)


def _wave(t, at, width):
    """A smooth bump, 1 at t = at, 0 beyond width either side."""
    x = (t - at) / width
    return 0.0 if abs(x) >= 1 else (1 - x * x) ** 2


def _gait(u, swing, bend, drag=1.0):
    """The shamble at phase u (0..1, the left foot coming forward first): thigh, knee and foot turns on top of the bent
    rest. The right leg drags: its knee lifts drag of what the left's does, so its foot scuffs along."""
    s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
    lk, rk = max(0.0, c) * bend, max(0.0, -c) * bend * drag
    return {"thigh_l": (-swing * s - lk * 0.5, 0, 0), "shin_l": (lk, 0, 0), "foot_l": (swing * s * 0.5 - lk * 0.5, 0, 0),
            "thigh_r": (swing * s - rk * 0.5, 0, 0), "shin_r": (rk, 0, 0), "foot_r": (-swing * s * 0.5 - rk * 0.5, 0, 0)}


FEET = ["foot_l", "foot_r", "shin_l", "shin_r"]


def _grope(t, k, amp=1.0, loop=None):
    """A small arm's slow groping at the air: (upper, fore, hand) turns at time t; loop: a clip length it must come
    round in (its rates rounded to whole turns of it)."""
    hz = lambda f: f if loop is None else max(1, round(f * loop)) / loop
    w = 2 * math.pi * t
    return ((amp * 10 * math.sin(hz(0.45) * w + 1.7 * k), amp * 6 * math.sin(hz(0.3) * w + k), amp * 8 * math.sin(hz(0.35) * w + 2.1 * k)),
            (amp * 14 * math.sin(hz(0.5) * w + 1.1 * k + 0.8), 0.0, 0.0), (amp * 16 * math.sin(hz(0.7) * w + 0.9 * k), 0.0, 0.0))


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def idle(r):
    """It stands swaying, its weight going slowly from one foot to the other like a crowd shifting, breathing heavily;
    every head in it moves on its own, one tilting, one turning, one nodding, and now and then one jerks; its small arms
    grope at the air; its jaw works. Once a while a shudder goes through the whole crowd of it, every head at once."""
    L, n = 6.0, 120
    keys = []
    for k in range(n + 1):
        t = L * k / n
        w = 2 * math.pi * t / L
        sw, br = math.sin(w), math.sin(2 * w)
        rip = _wave(t, 3.3, 0.45)
        sh = rip * math.sin(2 * math.pi * 5 * t)
        heads = {j: (5 * math.sin((1 + j % 3) * w + 1.3 * j) + 8 * sh * (1 if j % 2 else -1) + 9 * _wave(t, 1.6 + 0.9 * j, 0.22) * (j in (1, 4)),
                     12 * math.sin((1 + (j + 1) % 2) * w + 2.1 * j) - 10 * _wave(t, 1.6 + 0.9 * j, 0.22) * (j in (1, 4)),
                     8 * math.sin((2 - j % 2) * w + 0.7 * j)) for j in range(len(SMALL))}
        p = _pose(r, hips=(0.5 * br, 2 * math.sin(w + 0.5), 2.5 * sw), at=(1.2 * sw, -0.3 - 0.4 * br, 0), spine=(-0.6 * br, 0, -1.0 * sw),
                  chest=(-1.5 * br + 1.2 * sh, 1.5 * math.sin(w + 1), -1.5 * sw + sh), neck=(0, 3 * math.sin(w + 0.6), 0),
                  head=(-1 - br + 2 * sh, 6 * math.sin(w + 0.8), 3 * math.sin(2 * w + 2)), jaw=4 + 4 * math.sin(3 * w + 1) + 6 * rip,
                  nod=1.5 * br - 6 * rip, heads=heads, reach=0.12 + 0.1 * math.sin(w + 2) + 0.5 * rip,
                  arms={j: _grope(t, j, loop=L) for j in range(NSA)}, curl=(0.35 + 0.1 * math.sin(w), 0.3 + 0.1 * math.sin(w + 2)))
        p.update({"arm_l": (2 * math.sin(w + 0.3), 0, -1.5 * sw), "arm_r": (2 * math.sin(w + 2.2), 0, -1.5 * sw)})
        keys.append((t, p))
    keyed(r, "idle", L, keys, loop=True)


def _walk(r, name, L, swing, bend, lean, sway, drag, arm, n=24):
    """A shamble (or a lurching run): the body rolls over each foot as it plants and drops onto it, the right foot
    scuffs, the long arms swing slack, the heads bob out of time with each other; the feet are kept on the floor."""
    keys = []
    for k in range(n + 1):
        u = k / n
        t = L * u
        s, c, s2 = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u), math.sin(4 * math.pi * u)
        heads = {j: (4 * math.sin(4 * math.pi * u + 1.1 * j), 6 * math.sin(2 * math.pi * u + 1.7 * j), 5 * math.sin(2 * math.pi * u + 0.9 * j))
                 for j in range(len(SMALL))}
        p = _pose(r, hips=(lean + 2 * s2, 6 * s, -sway * s), at=(0.8 * sway * s, 0, 0), spine=(1 + s2, -3 * s, 1.5 * s),
                  chest=(2 + 2 * s2, -6 * s, 3 * s), neck=(-1 - s2, 4 * s, 0), head=(-2 - 2 * s2, 6 * s, -3 * s), jaw=4 + 4 * s2, nod=-2 * s2,
                  heads=heads, reach=0.15, arms={j: _grope(t, j, 0.6, loop=L) for j in range(NSA)}, legs=_gait(u, swing, bend, drag), feet=(),
                  curl=(0.4, 0.4))
        p.update({"arm_l": (arm * s + 4, 0, -3), "arm_r": (-arm * s + 4, 0, 3), "forearm_l": (-6 - 8 * max(0, -s), 0, 0),
                  "forearm_r": (-6 - 8 * max(0, s), 0, 0)})
        keys.append((t, _set_on(r, p, FEET, gap=0.0)))
    keyed(r, name, L, keys, loop=True)


def locomotion(r):
    _walk(r, "walk", 1.8, 18, 34, 4, 4, 0.55, 14)
    _walk(r, "run", 0.95, 28, 54, 12, 3, 0.8, 26)
    r.walk_stride, r.run_stride = 2.2, 3.8


def reactions(r):
    """Struck, the whole crowd of it flinches: every head jerks back, the small arms snatch in, its jaw drops. Its
    stance broken, it sinks onto its right knee and its knuckles, every head hanging; it pushes itself up. Its death: it
    staggers and sinks onto its knees, and its heads look every way, lost, turning and turning, its jaw working; it
    sags, and slumps over onto its side, and its small arms reach on a while, and stop."""
    hit = _pose(r, hips=(-4, 0, 0), at=(0, 0, 1.5), spine=(-3, 0, 0), chest=(-8, 0, 0), neck=(-4, 0, 0), head=(-14, 0, 4), jaw=12, nod=-16,
                heads={j: (0, 10 * (1 if j % 2 else -1), 8 * (1 if j % 3 else -1)) for j in range(len(SMALL))}, reach=-0.6, curl=(0.6, 0.6))
    back = _pose(r, chest=(3, 0, 0), head=(4, 0, -2), nod=5, reach=0.3)
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.07, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(r, hips=(0, -8 * sgn, -3 * sgn), at=(-sgn * 1.2, 0, 0), chest=(-4, -14 * sgn, -8 * sgn), neck=(0, -8 * sgn, 0), head=(-6, -18 * sgn, -14 * sgn),
                  jaw=10, nod=-10, heads={j: (0, -16 * sgn, -10 * sgn) for j in range(len(SMALL))}, reach=-0.5, curl=(0.4, 0.4))
        b = _pose(r, chest=(1, 4 * sgn, 2 * sgn), head=(2, 6 * sgn, 4 * sgn), nod=3, reach=0.2)
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.07, h), (0.32, b), (0.6, _pose(r))])
    kneel = _one_knee(r, hips=(14, 0, 0), spine=(6, 0, 0), chest=(14, 0, 0), neck=(10, 0, 0), head=(20, 0, 6), jaw=10, nod=34, reach=-0.8)
    sag = _pose(r, hips=(6, 0, 0), at=(0, -5, 0), chest=(6, 0, 0), head=(10, 0, 0), nod=14, reach=-0.3, jaw=6)
    keyed(r, "kneel", 0.7, [(0, _pose(r)), (0.25, sag), (0.7, kneel)])
    push = _pose(r, hips=(10, 0, 0), at=(0, -7, 0.5), chest=(10, 0, 0), head=(6, 0, 0), nod=8, reach=0.2,
                 left=(24, 14, -24), right=(-22, 14, -22), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)), curl=(0.8, 0.8))
    keyed(r, "rise", 0.95, [(0, kneel), (0.22, _clear(r, _mix(kneel, push, 0.5))), (0.45, push), (0.7, _clear(r, _mix(push, _pose(r), 0.5))), (0.95, _pose(r))])
    _death(r)


def _one_knee(r, **kw):
    """Down on its right knee with its left foot planted ahead, leaning on its knuckles (both fists on the floor ahead
    of it): the hips at the height that puts the knee on the floor."""
    knee = {"thigh_r": (13, 0, 4), "shin_r": (54, 0, 0), "foot_r": (83, 0, 0)}
    p = plans.kneel_on(r, _pose(r, legs=knee, feet=(), curl=(0.9, 0.9), **kw), knee="r", front="l", fist="r")
    return _pose_arms(r, p, left=(24, 10, -26), right=(-22, 10, -24), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)))


def _pose_arms(r, p, left=None, right=None, lhand=None, rhand=None):
    """A key with its wrists sent where named (as in _pose), the rest of it kept."""
    p = dict(p)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    for side, o in (("l", lhand), ("r", rhand)):
        if o:
            fr = core.frames(r, core.as_sample(p))
            p["hand_" + side] = ik.orient(r, fr, "hand_" + side, *o)
    return p


def _knees():
    """Down on both knees, the shins flat behind and the feet on their insteps."""
    return {"thigh_l": (12, 0, -4), "shin_l": (56, 0, 0), "foot_l": (86, 0, 0), "thigh_r": (12, 0, 4), "shin_r": (56, 0, 0), "foot_r": (86, 0, 0)}


def _prone(r, hips=(52, 0, 0), at=(0, -18, -24), chest=(0, 0, 0), head=(0, 0, 0), nod=0.0, heads=None, reach=0.0, arms=None, jaw=10.0,
           left=(30, 7, -60), right=(-30, 7, -56), roll=0.0, legs=None):
    """Lying on its front where it fell, the legs out behind it, the arms flung out ahead; let down onto the floor."""
    lg = {"thigh_l": (46, 0, -6), "shin_l": (-30, 0, 0), "foot_l": (70, 0, 0), "thigh_r": (44, 0, 8), "shin_r": (-24, 0, 0), "foot_r": (70, 0, 0)}
    lg.update(legs or {})
    p = _pose(r, hips=(hips[0], hips[1], hips[2] + roll), at=at, spine=(4, 0, 0), chest=chest, neck=(-20, 0, 0), head=head, jaw=jaw, nod=nod,
              heads=heads, reach=reach, arms=arms, legs=lg, feet=())
    p = _pose_arms(r, p, left=left, right=right, lhand=((0, 0.3, -1), (1, 0, 0)), rhand=((0, 0.3, -1), (1, 0, 0)))
    return _small_clear(r, _set_on(r, p), gap=2.0)


def _small_clear(r, p, gap=0.6):
    """The key with each small arm that would go into the floor turned (the least it takes, at the shoulder) out of it."""
    p = dict(p)
    for k in range(NSA):
        a = f"sa{k}"
        if core.lowest(r, core.frames(r, core.as_sample(p)), [f"sf{k}", f"sh{k}"]) <= core.GROUND - gap:
            continue
        base = p.get(a, (0, 0, 0))
        best = None
        for dx in range(-90, 91, 10):
            for dz in range(-90, 91, 10):
                q = dict(p)
                q[a] = (base[0] + dx, base[1], base[2] + dz)
                if core.lowest(r, core.frames(r, core.as_sample(q)), [f"sf{k}", f"sh{k}"]) <= core.GROUND - gap:
                    c = abs(dx) + abs(dz)
                    if best is None or c < best[0]:
                        best = (c, q[a])
        if best:
            p[a] = best[1]
    return p


def collapse(r):
    """Struck down, it falls: every head snaps back, its knees go, it pitches forward and its hands slap down, and it
    goes down on its face, the small arms flopping out, the heads lolling, and lies still."""
    struck = _pose(r, hips=(-6, 0, 0), at=(0, 0, 2), chest=(-12, 0, 0), neck=(-6, 0, 0), head=(-20, 0, 6), jaw=22, nod=-20, reach=-0.8, curl=(0.6, 0.6),
                   heads={j: (0, 14 * (1 if j % 2 else -1), 10) for j in range(len(SMALL))})
    buckle = _pose(r, hips=(14, 0, 4), at=(0, -9, 0), spine=(6, 0, 0), chest=(14, 0, 4), neck=(6, 0, 0), head=(14, 6, 6), jaw=14, nod=16, reach=0.3,
                   left=(26, 30, -30), right=(-24, 28, -28))
    knees = _clear(r, _pose(r, hips=(34, 0, 0), at=(0, -16, -6), legs=_knees(), feet=(), spine=(8, 0, 0), chest=(20, 0, 0), neck=(-6, 0, 0), head=(4, 0, 0),
                            jaw=16, nod=10, reach=0.6, left=(26, 4, -40), right=(-26, 4, -38), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0))))
    down = _prone(r, nod=26, reach=-0.2, arms={0: ((40, 0, 0), (20, 0, 0), (0, 0, 0)), 2: ((-30, 0, 0), (0, 0, 0), (0, 0, 0))},
                  heads={j: (10, 20 * (1 if j % 2 else -1), 16) for j in range(len(SMALL))}, jaw=24)
    bounce = _mix(down, knees, 0.12)
    lie = _prone(r, nod=30, reach=-0.3, arms={0: ((46, 0, 0), (26, 0, 0), (0, 0, 0)), 2: ((-36, 0, 0), (10, 0, 0), (0, 0, 0))},
                 heads={j: (12, 24 * (1 if j % 2 else -1), 20) for j in range(len(SMALL))}, jaw=26)
    keyed(r, "collapse", 1.4, [(0, _pose(r)), (0.1, struck), (0.32, buckle), (0.44, _clear(r, _mix(buckle, knees, 0.5))), (0.55, knees), (0.78, down), (0.9, _clear(r, bounce)), (1.05, lie), (1.4, lie)])
    return lie


def reassemble(r, lie):
    """It gets up again: the heads lift off the floor one by one and turn to you, the small arms push at the floor, its
    hands plant and it heaves itself up onto its knees, then onto its feet, and every head snaps round to you."""
    lift = _prone(r, nod=4, reach=0.4, heads={j: (-10, 0, 0) for j in range(len(SMALL))}, jaw=12, head=(-20, 0, 0),
                  left=(28, 6, -46), right=(-28, 6, -44))
    up = _clear(r, _pose(r, hips=(30, 0, 0), at=(0, -15, -4), legs=_knees(), feet=(), spine=(8, 0, 0), chest=(16, 0, 0), neck=(-8, 0, 0), head=(-8, 0, 0),
                         jaw=10, nod=-6, reach=0.5, left=(26, 6, -34), right=(-26, 6, -32), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)),
                         curl=(0.8, 0.8)))
    crouch = _pose(r, hips=(18, 0, 0), at=(0, -10, 0), spine=(6, 0, 0), chest=(12, 0, 0), head=(-4, 0, 0), jaw=8, nod=-4, reach=0.3,
                   left=(26, 14, -26), right=(-26, 14, -24), curl=(0.7, 0.7))
    snap = _pose(r, hips=(2, 0, 0), at=(0, -1, 0), chest=(-2, 0, 0), head=(-6, 0, 0), jaw=16, nod=-8, reach=0.5)
    keyed(r, "reassemble", 1.1, [(0, lie), (0.18, lift), (0.45, up), (0.7, crouch), (0.88, snap), (1.1, _pose(r))])


def _death(r):
    stag = _pose(r, hips=(-6, 6, 0), at=(0, 0, 2), chest=(-10, 8, 0), head=(-18, 10, 8), jaw=20, nod=-14, reach=-0.6, curl=(0.4, 0.4))
    knees = _clear(r, _pose(r, hips=(10, 0, 0), at=(0, -16, 2), legs=_knees(), feet=(), spine=(4, 0, 0), chest=(8, 0, 0), neck=(6, 0, 0), head=(8, 0, 0),
                            jaw=14, nod=6, reach=0.2, left=(28, 10, -14), right=(-28, 10, -12)))
    lost = []
    for i, t in enumerate((1.6, 2.1, 2.6, 3.1)):
        sg = 1 if i % 2 else -1
        lost.append((t, _clear(r, _pose(r, hips=(10, 4 * sg, 0), at=(0, -16, 2), legs=_knees(), feet=(), spine=(4, 0, 0), chest=(8, 6 * sg, 0), neck=(4, 14 * sg, 0),
                                        head=(-6, 30 * sg, 10 * sg), jaw=8 + 10 * (i % 2), nod=-4,
                                        heads={j: (6 * math.sin(i + j), 40 * math.sin(1.9 * i + 2.3 * j), 14 * math.cos(i + j)) for j in range(len(SMALL))},
                                        reach=0.5 + 0.3 * sg, arms={j: _grope(t, j, 1.2) for j in range(NSA)}, left=(28, 10, -14), right=(-28, 10, -12)))))
    sag = _clear(r, _pose(r, hips=(18, 0, 6), at=(0, -17, 2), legs=_knees(), feet=(), spine=(8, 0, 4), chest=(14, 0, 6), neck=(10, 0, 0), head=(20, 0, 10),
                          jaw=10, nod=24, reach=0.2, left=(28, 6, -18), right=(-28, 6, -16)))
    over = _set_on(r, _pose_arms(r, _pose(r, hips=(30, 0, 50), at=(10, -18, 0), legs={**_knees(), "thigh_l": (20, 0, -20), "thigh_r": (24, 0, 10)}, feet=(),
                                          spine=(6, 0, 6), chest=(10, 0, 10), neck=(8, 0, 0), head=(14, 0, 14), jaw=14, nod=20, reach=0.4),
                                 left=(40, 16, -20), right=(-20, 20, -24)))
    lie = _set_on(r, _pose_arms(r, _pose(r, hips=(26, 0, 86), at=(22, -20, 0), legs={**_knees(), "thigh_l": (24, 0, -30), "shin_l": (40, 0, 0), "thigh_r": (30, 0, -4)},
                                         feet=(), spine=(4, 0, 4), chest=(6, 0, 4), neck=(4, 0, 6), head=(6, 0, 18), jaw=22, nod=10, reach=0.8,
                                         heads={j: (0, 10 * (1 if j % 2 else -1), 0) for j in range(len(SMALL))}),
                                left=(60, 6, -26), right=(10, 30, -30)))
    reach = dict(lie)
    for j in range(NSA):
        reach[f"sa{j}"] = tuple(v + d for v, d in zip(lie[f"sa{j}"], _grope(4.8, j, 1.4)[0]))
    still = dict(lie)
    for j in range(NSA):
        still[f"sa{j}"] = tuple(v * 0.6 for v in lie[f"sa{j}"])
        still[f"sf{j}"] = tuple(v + 20 for v in lie[f"sf{j}"])
    keyed(r, "death", 6.0, [(0, _pose(r)), (0.3, stag), (1.1, knees)] + lost + [(3.6, sag), (4.1, over), (4.5, lie), (5.0, reach), (5.5, still), (6.0, still)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _track(r, mv, bone, pts):
    """A move's scale keys for one bone: [(stage, fraction, scale)] in its stages' time."""
    W, A, Rr = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    ln = {"windup": W, "active": A, "recover": Rr}
    c = r.clips[core.clip_name(mv)]
    ks = [(0.0, 1.0)] + [(t0[s] + f * ln[s], v) for s, f, v in pts] + [(c.length, 1.0)]
    c.scale(bone, [(t, v, v, v) for t, v in sorted(ks)], "cat")


HAND_HIT = [("fingers_{s}", (0, 8, 0), 4.0), ("hand_{s}", (0, 5, 0), 4.5)]


def _hits(*sides):
    return [(b.format(s=s), tip, rad) for s in sides for b, tip, rad in HAND_HIT]


def swipe(r):
    """The Swipe: its right arm swings back and out, its body turning away and every head turning with it; then the
    arm sweeps across in front of it at chest height, the back of the big hand leading, the body turning after it,
    the small arms flung along (LIGHT)."""
    cock = _pose(r, hips=(-2, 14, 0), at=(0, 0.5, 0.5), spine=(-2, 6, 0), chest=(-10, 20, -6), neck=(0, -10, 0), head=(-6, -14, -6), jaw=8, nod=-6,
                 heads={j: (0, -18, 0) for j in range(len(SMALL))}, reach=-0.4, right=(-38, 36, 10), left=(22, 26, -22),
                 curl=(0.4, 0.6), step={"r": (0, 0, 3)})
    gather = _mix(_pose(r), cock, 0.35)
    swing = _pose(r, hips=(4, 2, 0), at=(0, -1.5, -0.5), chest=(6, 4, 0), neck=(-2, -4, 0), head=(-4, -6, 0), jaw=12, nod=-2, reach=0.4,
                  right=(-14, 34, -38), left=(24, 26, -18), curl=(0.4, 0.8), step={"r": (0, 0, 3)})
    hit = _pose(r, hips=(8, -12, 0), at=(0, -3, -1), chest=(10, -18, 6), neck=(0, 6, 0), head=(-2, 10, 6), jaw=14, nod=4,
                heads={j: (0, 14, 0) for j in range(len(SMALL))}, reach=0.7, right=(18, 30, -32), left=(26, 24, -12),
                curl=(0.4, 0.9), step={"r": (0, 0, 3)})
    held = _pose(r, hips=(9, -14, 0), at=(0, -3.5, -1), chest=(11, -21, 7), neck=(0, 7, 0), head=(-1, 12, 6), jaw=12, nod=5,
                 heads={j: (0, 16, 0) for j in range(len(SMALL))}, reach=0.6, right=(24, 28, -26), left=(26, 24, -10),
                 curl=(0.4, 0.9), step={"r": (0, 0, 3)})
    return Move("swipe", "LIGHT", 12, 4, 14, title="Swipe", track=8.0, rng=(0, 4.6), kinds=("combo", "near"), damage=1.0, knock=0.7, plant=(),
                hit=_hits("r"), lag=ON_TIME, through=_on(r, held, 0.04),
                stages={"windup": [(0.3, gather), (1.0, cock)], "active": [(0.25, swing), (0.5, hit), (1.0, held)], "recover": _back(r, held)})


def grab(r):
    """The Grab: it draws its left hand back, every head lifting to look at you, then lunges a step and snatches with
    the big hand open, and it closes; every small arm on it reaches out with it (LIGHT)."""
    draw = _pose(r, hips=(-4, -8, 0), at=(0, 0.5, 1), chest=(-8, -12, 4), neck=(-4, 6, 0), head=(-8, 8, 0), jaw=10, nod=-12, reach=-0.6,
                 left=(26, 38, 6), lhand=((0.2, 0.4, -0.9), (1, 0, 0)), right=(-24, 24, -14), curl=(-0.3, 0.4))
    gather = _mix(_pose(r), draw, 0.35)
    snatch = _pose(r, hips=(12, 6, 0), at=(0, -4, -3), chest=(10, 8, -2), neck=(0, -4, 0), head=(-6, -6, 0), jaw=18, nod=-4, reach=1.0,
                   left=(10, 30, -46), lhand=((-0.1, 0.1, -1), (1, 0, 0)), right=(-26, 22, -12), curl=(-0.4, 0.5), step={"l": (0, 0, -9)})
    close = _pose(r, hips=(13, 7, 0), at=(0, -4.5, -3.2), chest=(11, 9, -2), neck=(0, -4, 0), head=(-5, -6, 0), jaw=14, nod=-2, reach=1.0,
                  left=(8, 29, -45), lhand=((-0.1, 0.2, -1), (1, 0, 0)), right=(-26, 22, -12), curl=(1.0, 0.5), step={"l": (0, 0, -9)})
    pull = _pose(r, hips=(6, 2, 0), at=(0, -2, -1), chest=(4, 3, 0), head=(-2, -2, 0), jaw=8, nod=0, reach=0.4, left=(18, 30, -30), curl=(1.0, 0.4),
                 step={"l": (0, 0, -4)})
    return Move("grab", "LIGHT", 10, 4, 16, title="Grab", track=8.0, rng=(0, 5.0), kinds=("combo", "near"), damage=1.0, knock=0.3, plant=(),
                hit=_hits("l"), lag=ON_TIME, through=_on(r, close, 0.03),
                stages={"windup": [(0.3, gather), (1.0, draw)], "active": [(0.5, snatch), (1.0, close)], "recover": [(0.4, pull), (0.7, _mix(pull, _pose(r), 0.7))]})


def crush(r):
    """Crush (HEAVY): it rears up and back with both fists together over its heads, every face turned up, its jaw
    hanging wide, and holds; then it brings them down together on the floor in front of it, its knees giving under the
    blow and every head jolting."""
    lean = _pose(r, hips=(-3, 0, 0), at=(0, 1, 0.5), chest=(-8, 0, 0), head=(-6, 0, 0), jaw=8, nod=-8, reach=-0.3, left=(16, 50, -12), right=(-16, 50, -12),
                 curl=(0.8, 0.8))
    up = _pose(r, hips=(-8, 0, 0), at=(0, 3, 1.5), spine=(-8, 0, 0), chest=(-24, 0, 0), neck=(-8, 0, 0), head=(-14, 0, 0), jaw=22, nod=-22,
               heads={j: (-6, 0, 0) for j in range(len(SMALL))}, reach=-0.2, arms={k: ((-40, 0, 0), (0, 0, 0), (0, 0, 0)) for k in range(NSA)},
               left=(8, 86, -12), right=(-8, 86, -12), curl=(1, 1))
    smash = _pose(r, hips=(18, 0, 0), at=(0, -10, -2), spine=(10, 0, 0), chest=(18, 0, 0), neck=(4, 0, 0), head=(8, 0, 0), jaw=12, nod=14, reach=0.8,
                  left=(7, 12, -36), right=(-7, 12, -36), curl=(1, 1), step={"l": (0, 0, -8)})
    after = _pose(r, hips=(19, 0, 0), at=(0, -10.5, -2.2), spine=(11, 0, 0), chest=(19, 0, 0), neck=(5, 0, 0), head=(10, 0, 0), jaw=16, nod=18, reach=0.9,
                  left=(7.5, 11.5, -37), right=(-7.5, 11.5, -37), curl=(1, 1), step={"l": (0, 0, -8)})
    lift = _pose(r, hips=(9, 0, 0), at=(0, -5, -1), chest=(8, 0, 0), head=(2, 0, 0), jaw=8, nod=4, reach=0.3, left=(14, 22, -28), right=(-14, 22, -28),
                 curl=(0.6, 0.6), step={"l": (0, 0, -4)})
    return Move("crush", "HEAVY", 24, 5, 22, title="Crush", hold=8, track=4.0, ability="Crush", rng=(0, 4.8), kinds=("area", "near"), damage=1.4, knock=1.2,
                plant=(), disc=3.5, disc_at="hit", hit=_hits("l", "r"), lag=ON_TIME, through=after,
                stages={"windup": [(0.3, lean), (1.0, up)], "active": [(0.5, smash), (1.0, after)], "recover": [(0.4, lift), (0.7, _mix(lift, _pose(r), 0.75))]})


def surge(r):
    """Surge (HEAVY, its gap closer): it drops forward onto its knuckles, every face on you, its jaw hanging, and holds;
    then it drives at you in one long lunge, its arms flung wide and sweeping in to scoop you up, all its small arms
    reaching."""
    low = _pose(r, hips=(20, 0, 0), at=(0, -8, -1), spine=(6, 0, 0), chest=(10, 0, 0), neck=(-14, 0, 0), head=(-16, 0, 0), jaw=18, nod=-16, reach=0.5,
                left=(26, 6, -22), right=(-26, 6, -22), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)), curl=(0.9, 0.9))
    sink = _mix(_pose(r), low, 0.4)
    drive = _pose(r, hips=(16, 0, 0), at=(0, -6, -4), spine=(4, 0, 0), chest=(4, 0, 0), neck=(-10, 0, 0), head=(-10, 0, 0), jaw=24, nod=-12, reach=1.0,
                  left=(40, 40, -30), right=(-40, 40, -30), curl=(-0.3, -0.3), step={"l": (0, 0, -16), "r": (0, 0, 6)})
    scoop = _pose(r, hips=(18, 0, 0), at=(0, -7, -5), spine=(5, 0, 0), chest=(8, 0, 0), neck=(-6, 0, 0), head=(-6, 0, 0), jaw=18, nod=-8, reach=1.0,
                  left=(12, 34, -42), right=(-12, 34, -42), curl=(0.8, 0.8), step={"l": (0, 0, -16), "r": (0, 0, 6)})
    stand = _pose(r, hips=(8, 0, 0), at=(0, -3, -1), chest=(4, 0, 0), head=(0, 0, 0), jaw=8, nod=0, reach=0.4, left=(18, 28, -24), right=(-18, 28, -24),
                  curl=(0.6, 0.6), step={"l": (0, 0, -6), "r": (0, 0, 2)})
    return Move("surge", "HEAVY", 22, 8, 22, title="Surge", hold=10, track=5.0, advance=5.0, ability="Surge", rng=(3.0, 10.0), kinds=("gap",),
                damage=1.4, knock=1.4, plant=(), hit=_hits("l", "r"), lag=ON_TIME, through=_on(r, scoop, 0.03),
                stages={"windup": [(0.3, sink), (1.0, low)], "active": [(0.55, drive), (1.0, scoop)], "recover": [(0.4, stand), (0.7, _mix(stand, _pose(r), 0.7))]})


def more(r):
    """More of Them (summoning): it reaches over to its right shoulder with its left hand and tears a person off it, a
    head and all, and throws them down in front of it; then over to its left with its right, and that one too; they
    get up; and where they came from, more push out of it."""
    reach_l = _pose(r, hips=(0, -6, 0), chest=(-4, -10, 0), head=(-4, 16, 0), nod=-4, reach=-0.2, left=("on", "h1", (0, -4, 0)),
                    lhand=((0, 0.4, 0.8), (1, 0, 0)), curl=(-0.2, 0.3))
    tear_l = _pose(r, hips=(0, -7, 0), chest=(-8, -12, 0), head=(-6, 18, 0), jaw=12, nod=-8, reach=-0.4, left=("on", "h1", (0, -9, -2)),
                   lhand=((0, 0.4, 0.8), (1, 0, 0)), curl=(0.9, 0.3))
    throw_l = _pose(r, hips=(8, 10, 0), at=(0, -3, -1), chest=(10, 14, 0), head=(2, -6, 0), jaw=16, nod=4, reach=0.5, left=(4, 10, -36),
                    lhand=((0, 0.6, -0.8), (1, 0, 0)), curl=(-0.3, 0.3))
    reach_r = _pose(r, hips=(0, 6, 0), chest=(-4, 10, 0), head=(-4, -16, 0), nod=-4, reach=-0.2, right=("on", "h0", (0, -4, 0)),
                    rhand=((0, 0.4, 0.8), (1, 0, 0)), curl=(0.3, -0.2))
    tear_r = _pose(r, hips=(0, 7, 0), chest=(-8, 12, 0), head=(-6, -18, 0), jaw=12, nod=-8, reach=-0.4, right=("on", "h0", (0, -9, -2)),
                   rhand=((0, 0.4, 0.8), (1, 0, 0)), curl=(0.3, 0.9))
    throw_r = _pose(r, hips=(8, -10, 0), at=(0, -3, -1), chest=(10, -14, 0), head=(2, 6, 0), jaw=16, nod=4, reach=0.5, right=(-4, 10, -36),
                    rhand=((0, 0.6, -0.8), (1, 0, 0)), curl=(0.3, -0.3))
    end = _mix(throw_r, _pose(r), 0.35)
    act = [(0.1, tear_l), (0.3, throw_l), (0.46, reach_r), (0.6, tear_r), (0.8, throw_r), (1.0, end)]
    mv = Move("more", "NONE", 14, 28, 16, title="More of Them", track=0.0, ability="More of Them", rng=(0, 99), kinds=("summon", "ranged"), damage=0, knock=0,
              plant=(), lag=ON_TIME, through=_mix(end, _pose(r), 0.2),
              stages={"windup": [(0.4, _mix(_pose(r), reach_l, 0.5)), (1.0, reach_l)], "active": act, "recover": _back(r, _mix(end, _pose(r), 0.2))})
    mv._torn = {1: [("active", 0.08, 1.0), ("active", 0.13, 0.0), ("recover", 0.3, 0.0), ("recover", 0.9, 1.0)],
                0: [("active", 0.58, 1.0), ("active", 0.63, 0.0), ("recover", 0.4, 0.0), ("recover", 1.0, 1.0)]}
    return mv


def closer(r):
    """Closer (into the second phase): it goes still, and every head on it turns to you at once and lifts, its own jaw
    dropping wide; a shudder runs up it from the feet and people fall off it; it leans in at you, arms spread, every
    small arm reaching."""
    still = _pose(r, hips=(2, 0, 0), chest=(-2, 0, 0), head=(-6, 0, 0), jaw=4, nod=-10, heads={j: (0, 0, 0) for j in range(len(SMALL))}, reach=-0.3,
                  left=(26, 22, -16), right=(-26, 22, -16), curl=(0.2, 0.2))
    gape = lambda a: _pose(r, hips=(4 + a, 0, a), at=(0, -1, -0.5), spine=(1, 0, a), chest=(0, 0, -a), neck=(-2, 0, 0), head=(-10, 0, 1.5 * a), jaw=30, nod=-14,
                          heads={j: (-6 + 3 * a * (1 if j % 2 else -1), 0, 2 * a) for j in range(len(SMALL))}, reach=0.2 + 0.1 * a,
                          left=(30, 24, -18), right=(-30, 24, -18), curl=(-0.2, -0.2))
    lean = _pose(r, hips=(12, 0, 0), at=(0, -4, -3), spine=(4, 0, 0), chest=(8, 0, 0), neck=(-8, 0, 0), head=(-10, 0, 0), jaw=26, nod=-12, reach=1.0,
                 left=(40, 34, -26), right=(-40, 34, -26), curl=(-0.4, -0.4), step={"l": (0, 0, -5), "r": (0, 0, 2)})
    act = [(0.12, gape(0)), (0.24, gape(1.6)), (0.36, gape(-1.6)), (0.48, gape(1.6)), (0.6, gape(-1.6)), (0.72, gape(0)), (1.0, lean)]
    return Move("closer", "NONE", 16, 34, 20, title="Closer", track=0.0, ability="More of Them", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=_mix(lean, _pose(r), 0.2),
                stages={"windup": [(0.4, _mix(_pose(r), still, 0.5)), (1.0, still)], "active": act, "recover": _back(r, _mix(lean, _pose(r), 0.2))})


def elbow(r):
    """For whoever is behind it, the Elbow: its heads turn round to look behind it first, then it twists its whole body
    round and swings its right arm back behind it in a long backhand (LIGHT)."""
    look = _pose(r, hips=(0, -10, 0), chest=(-4, -20, 0), neck=(0, 10, 0), head=(-4, 24, 0), jaw=8, nod=-4, heads={j: (0, 40, 0) for j in range(len(SMALL))},
                 reach=-0.4, right=(4, 32, -32), curl=(0.4, 0.8))
    gather = _mix(_pose(r), look, 0.35)
    whip = _pose(r, hips=(-2, 22, 0), at=(0, -2, 0), chest=(-6, 36, -4), neck=(0, 14, 0), head=(-6, 26, 0), jaw=14, nod=-6,
                 heads={j: (0, 46, 0) for j in range(len(SMALL))}, reach=0.4, right=(-36, 34, 8), curl=(0.4, 0.9),
                 step={"r": (0, 0, 4)})
    hit = _pose(r, hips=(-4, 32, 0), at=(0, -3, 0.5), chest=(-8, 44, -6), neck=(0, 16, 0), head=(-6, 28, 0), jaw=14, nod=-6,
                heads={j: (0, 50, 0) for j in range(len(SMALL))}, reach=0.5, right=(-20, 32, 34), curl=(0.4, 0.9),
                step={"r": (0, 0, 5)})
    held = _pose(r, hips=(-4, 34, 0), at=(0, -3.2, 0.6), chest=(-8, 46, -6), neck=(0, 16, 0), head=(-6, 28, 0), jaw=12, nod=-6,
                 heads={j: (0, 50, 0) for j in range(len(SMALL))}, reach=0.5, right=(-14, 31, 36), curl=(0.4, 0.9),
                 step={"r": (0, 0, 5)})
    return Move("elbow", "LIGHT", 12, 4, 16, title="Elbow", track=0.0, rng=(0, 4.0), kinds=("behind",), damage=1.0, knock=1.0, plant=(),
                hit=_hits("r") + [("forearm_r", (0, 18, 0), 4.0)], lag=ON_TIME, through=_on(r, held, 0.04),
                stages={"windup": [(0.3, gather), (1.0, look)], "active": [(0.4, whip), (0.7, hit), (1.0, held)], "recover": _back(r, held)})


def all_of_them(r):
    """All of Them (PERILOUS, the signature): it rears up to its whole height, its arms flung wide and up and every small
    arm reaching, every face turned to you, its jaw wide, and holds; then it falls on you, the whole crowd of it, flat
    on its face with its arms flung out ahead; and it gathers itself up off the floor."""
    rear = lambda a: _pose(r, hips=(-14, 0, a), at=(0, 4, 2), spine=(-10, 0, 0), chest=(-30, 0, -a), neck=(-6, 0, 0), head=(-6, 0, a), jaw=28, nod=-14,
                           heads={j: (0, 0, 2 * a * (1 if j % 2 else -1)) for j in range(len(SMALL))}, reach=0.2,
                           arms={k: ((-50, 0, 0), (0, 0, 0), (0, 0, 0)) for k in range(NSA)}, left=(46, 74, 2), right=(-46, 74, 2),
                           lhand=((0.6, -0.6, -0.3), (0, 0, -1)), rhand=((-0.6, -0.6, -0.3), (0, 0, -1)), curl=(-0.4, -0.4))
    begin = _mix(_pose(r), rear(0), 0.35)
    go = _pose(r, hips=(8, 0, 0), at=(0, 0, -4), spine=(-2, 0, 0), chest=(-16, 0, 0), neck=(-8, 0, 0), head=(-10, 0, 0), jaw=30, nod=-16, reach=0.8,
               left=(42, 64, -30), right=(-42, 64, -30), curl=(-0.4, -0.4))
    tip = _small_clear(r, gap=3.0, p=_clear(r, _pose(r, hips=(30, 0, 0), at=(0, -4, -10), spine=(4, 0, 0), chest=(-6, 0, 0), neck=(-10, 0, 0), head=(-14, 0, 0), jaw=30, nod=-16, reach=1.0,
                          left=(34, 50, -50), right=(-34, 50, -50), curl=(-0.3, -0.3), step={"l": (0, 0, -6)})))
    fall = _small_clear(r, gap=3.0, p=_clear(r, _mix(tip, _prone(r, hips=(54, 0, 0), at=(0, -18, -28), nod=-6, reach=1.0, jaw=26, head=(-16, 0, 0),
                                                      heads={j: (-8, 0, 0) for j in range(len(SMALL))}, left=(34, 4, -66), right=(-34, 4, -64)), 0.5)))
    flat = _prone(r, hips=(54, 0, 0), at=(0, -18, -28), nod=-6, reach=1.0, jaw=26, head=(-16, 0, 0), heads={j: (-8, 0, 0) for j in range(len(SMALL))},
                  left=(34, 4, -66), right=(-34, 4, -64))
    lie = _prone(r, hips=(55, 0, 0), at=(0, -18.5, -28.5), nod=-4, reach=0.8, jaw=22, head=(-18, 0, 0), heads={j: (-10, 0, 0) for j in range(len(SMALL))},
                 left=(35, 4, -67), right=(-35, 4, -65))
    push = _clear(r, _pose(r, hips=(30, 0, 0), at=(0, -15, -6), legs=_knees(), feet=(), spine=(8, 0, 0), chest=(16, 0, 0), neck=(-8, 0, 0), head=(-8, 0, 0),
                           jaw=10, nod=-6, reach=0.5, left=(26, 6, -36), right=(-26, 6, -34), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)),
                           curl=(0.8, 0.8)))
    crouch = _clear(r, _pose(r, hips=(16, 0, 0), at=(0, -7, 0), spine=(6, 0, 0), chest=(12, 0, 0), head=(-4, 0, 0), jaw=8, nod=-4, reach=0.3,
                             legs={"thigh_r": (-34, 0, 0), "shin_r": (44, 0, 0), "foot_r": (-6, 0, 0)}, feet=("l",), step={"l": (0, 0, -8)},
                             left=(26, 18, -26), right=(-26, 16, -24), curl=(0.7, 0.7)))  # its right foot coming up off the floor to step under it
    knee = _one_knee(r, hips=(22, 0, 0), spine=(8, 0, 0), chest=(14, 0, 0), neck=(-6, 0, 0), head=(-6, 0, 0), jaw=8, nod=-6, reach=0.4)
    mv = Move("all", "PERILOUS", 30, 12, 40, title="All of Them", signature=True, track=3.0, rng=(0, 6.0), kinds=("area",), damage=1.8, knock=1.6,
              plant=(), disc=4.0, hit=[("chest", (0, -12, 0), 12.0), ("head", (0, -7, 0), 8.0)] + _hits("l", "r"), lag=ON_TIME,
              through=_small_clear(r, gap=3.0, p={**_clear(r, _mix(lie, push, 0.4)), **{b: flat[b] for b in SMALLS}}),
              stages={"windup": [(0.3, begin), (0.6, rear(3)), (0.8, rear(-3)), (1.0, rear(0))], "active": [(0.2, go), (0.45, tip), (0.72, fall), (1.0, flat)],
                      "recover": [(0.42, push), (0.58, knee), (0.73, crouch)]})
    return mv


def wake(r):
    """Its intro: it stands with every head on it bowed, its own too, the small arms hanging; then the heads lift one by
    one to look at you, the same face each time, the ones on its belly first and the ones on its back last, each with a
    jerk; its own head lifts last and its jaw drops; it straightens a little, and settles into its stoop."""
    order = (4, 5, 0, 1, 3, 2)
    bowed = {j: (38, 0, 10 * (1 if j % 2 else -1)) for j in range(len(SMALL))}
    bow = _pose(r, hips=(4, 0, 0), at=(0, -1, 0), chest=(6, 0, 0), neck=(14, 0, 0), head=(30, 0, 8), jaw=0, heads=bowed, reach=-0.8, curl=(0.2, 0.2))
    act = []
    up = dict(bowed)
    for i, j in enumerate(order):
        up = dict(up)
        up[j] = (-8, 0, 0)
        jerk = dict(up)
        jerk[j] = (-14, 6 * (1 if j % 2 else -1), 0)
        act.append((0.1 + 0.11 * i, _pose(r, hips=(4, 0, 0), at=(0, -1, 0), chest=(6 - i, 0, 0), neck=(14, 0, 0), head=(30, 0, 8), heads=jerk, reach=-0.8 + 0.1 * i,
                                          curl=(0.2, 0.2))))
    lift = {j: (-6, 0, 0) for j in range(len(SMALL))}
    act.append((0.82, _pose(r, hips=(1, 0, 0), chest=(0, 0, 0), neck=(-2, 0, 0), head=(-8, 0, 0), jaw=4, heads=lift, reach=-0.2, curl=(0.3, 0.3))))
    act.append((1.0, _pose(r, hips=(-3, 0, 0), at=(0, 1.5, 0.5), chest=(-6, 0, 0), neck=(-4, 0, 0), head=(-10, 0, 0), jaw=26, heads=lift, nod=-4, reach=0.4,
                           left=(30, 26, -14), right=(-30, 26, -14), curl=(-0.2, -0.2))))
    low = _pose(r, hips=(6, 0, 0), at=(0, -2, -1), chest=(6, 0, 0), head=(-4, 0, 0), jaw=10, nod=-2, reach=0.3, curl=(0.4, 0.4))
    return Move("wake", "NONE", 16, 44, 18, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME,
                through=_mix(act[-1][1], low, 0.5),
                stages={"windup": [(0.5, _mix(_pose(r), bow, 0.6)), (1.0, bow)], "active": act, "recover": [(0.45, low), (0.7, _mix(low, _pose(r), 0.7))]})


def moves(r):
    return [swipe(r), grab(r), crush(r), surge(r), more(r), closer(r), elbow(r), all_of_them(r), wake(r)]


def _drift(r, clip, ramp=0.25):
    """Under a move, once it is made: the heads in the mass stir on their own, the small arms grope, its jaw works and
    its rags sway, faded in and out at the ends so it hands back to the idle exactly as it was."""
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
    for j, h in enumerate(HEADS):
        add(h, lambda t, j=j: (3.0 * math.sin(2 * math.pi * 0.6 * t + 1.3 * j), 5.0 * math.sin(2 * math.pi * 0.45 * t + 2.1 * j), 3.0 * math.sin(2 * math.pi * 0.5 * t + j)))
    for j in range(NSA):
        add(f"sa{j}", lambda t, j=j: _grope(t, j, 0.6)[0])
        add(f"sf{j}", lambda t, j=j: _grope(t, j, 0.6)[1])
        add(f"sh{j}", lambda t, j=j: _grope(t, j, 0.6)[2])
    add("jaw", lambda t: (3.0 * math.sin(2 * math.pi * 0.8 * t), 0.0, 0.0))
    for k, b in enumerate(("rag_f", "rag_b", "cloak", "dangle")):
        add(b, lambda t, k=k: (4.0 * math.sin(2 * math.pi * 0.5 * t + 1.4 * k), 0.0, 3.0 * math.sin(2 * math.pi * 0.4 * t + 0.8 * k)))


def clips(r):
    plans.biped_locomotion(r, P, weight=1.5, temper="feral")
    r.size = 1.0  # its keys set its hips' height themselves (the library's crouch scaling would float its planted feet)
    idle(r)
    locomotion(r)
    reactions(r)
    lie = collapse(r)
    reassemble(r, lie)
    mv = moves(r)
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
        _drift(r, core.clip_name(m))
    for k, pts in by["more"]._torn.items():
        _track(r, by["more"], f"h{k}", pts)
    # its hands, its own head and the small hands stay out of the floor between the keys (the wrist turns first); lying,
    # the rest of it too; its feet tip at the ankle where they kneel
    hands = [([f"hand_{s}", f"fingers_{s}"], [(f"hand_{s}", 0, 50), (f"forearm_{s}", 0, 30)]) for s in "lr"]
    head = [(["head", "jaw"], [("neck", 0, 30), ("head", 0, 30)])]
    small = [([f"sh{k}"], [(f"sf{k}", 0, 50), (f"sa{k}", 0, 60), (f"sa{k}", 2, 60)]) for k in range(NSA)]
    lying = ("death", "collapse", "reassemble", "move_all")
    core.ground_pass(r, hands + head + small, clips=[n for n in r.clips if n not in lying])
    feet = [(["foot_" + s], [("foot_" + s, 0, 60), ("shin_" + s, 0, 40), ("thigh_" + s, 0, 24)]) for s in "lr"]
    core.ground_pass(r, feet, clips=["kneel", "rise", "run"])
    core.ground_pass(r, hands + head + small + feet, clips=[n for n in lying if n != "move_all"], skip=())
    # getting up off its face, its knees lift its feet clear before its ankles turn (a turned ankle would jerk)
    knees_first = [(["foot_" + s], [("shin_" + s, 0, 40), ("thigh_" + s, 0, 30), ("foot_" + s, 0, 30)]) for s in "lr"]
    core.ground_pass(r, hands + head + knees_first, clips=["move_all"], skip=())
    return mv


# Its sounds (SPEC §35.8): a crowd of the dead groaning, zombies and zombie villagers and husks pitched low and layered,
# a warden's heartbeat under its tells; heavy steps, the mud and the rooted dirt for its falls; chosen on paper, not
# heard in game.
SOUNDS = {
    "tell_light": [("entity.zombie.ambient", 1.0, 0.6), ("entity.zombie_villager.ambient", 0.8, 0.7)],
    "tell_heavy": [("entity.zombie.ambient", 1.4, 0.5), ("entity.husk.ambient", 1.2, 0.5), ("entity.warden.heartbeat", 1.0, 0.8)],
    "tell_perilous": [("entity.zombie_villager.converted", 1.4, 0.5), ("entity.husk.ambient", 1.4, 0.4), ("entity.warden.agitated", 1.2, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 0.9, 0.6), ("entity.zombie.attack_wooden_door", 0.6, 0.8)],
    "swing_heavy": [("entity.player.attack.sweep", 1.3, 0.5), ("entity.husk.hurt", 1.0, 0.5)],
    "cast": [("entity.zombie.infect", 1.2, 0.6), ("entity.zombie_villager.ambient", 1.0, 0.5)],
    "impact": [("item.mace.smash_ground_heavy", 1.4, 0.6), ("block.mud.break", 1.2, 0.6), ("entity.zombie.attack_wooden_door", 1.0, 0.5)],
    "roar": [("entity.zombie.ambient", 2.0, 0.45), ("entity.husk.ambient", 1.8, 0.4), ("entity.zombie_villager.ambient", 1.6, 0.5)],
    "step": [("entity.ravager.step", 0.7, 0.6), ("block.mud.step", 0.8, 0.7)],
}
MOVE_SOUNDS = {"more": {"swing": "cast", "impact": ""}, "closer": {"swing": "roar", "impact": ""}, "all": {"swing": "roar"},
               "wake": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # it stands with every head bowed, and they lift one by one
    "phases": [
        {"pool": [
            {"move": "swipe", "weight": 3, "when": "near", "then": [["grab"], ["grab", "crush"]]},
            {"move": "crush", "weight": 3, "when": "near"},
            {"move": "surge", "weight": 2, "when": "far", "then": [["swipe"]]},
            {"move": "more", "weight": 2, "when": "far"},  # it throws nothing: keep away from it and more of them come
            {"move": "elbow", "weight": 3, "when": "behind"},
        ]},
        # closer, as in DeepBestiary: every face on you, and more of them
        {"transition": "closer", "pool": [
            {"move": "swipe", "weight": 3, "when": "near", "then": [["grab"], ["grab", "crush"]]},
            {"move": "crush", "weight": 2, "when": "near"},
            {"move": "surge", "weight": 3, "when": "far", "then": [["swipe", "grab"]]},
            {"move": "more", "weight": 3, "when": "far"},
            {"move": "all", "weight": 2},
            {"move": "elbow", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.0, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
