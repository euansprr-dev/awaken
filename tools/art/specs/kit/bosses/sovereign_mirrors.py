"""The Sovereign of Mirrors (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's fifth Sovereign, a Duelist. The
Vanity, picked from its silhouette round 2026-10-29: "It only ever looked at itself."

A tall slender figure in a white satin coat with silver brocade and a rose sash, long legs in dove-grey hose and black
heeled boots, a coat tail hanging behind. Its face is porcelain, painted with a small smile, its black hair lacquered up
into a tall knot with a silver pin through it. It has six arms: the top pair in satin sleeves, the lower two pairs bare
porcelain growing out of its sides. Five of its hands hold round hand mirrors in rose-gold frames, all turned to its
own face, so wherever it is it sees itself from every side; the sixth holds a long blade of glass.

Moves (SPEC §35.11): two cuts of the glass blade, a forehand and a rising backhand (LIGHT, a string); Mirrored Lunge
(HEAVY, its gap closer: it turns side-on with the blade drawn back beside its face and every mirror swings round to face
you, holds, and lunges); Flawless Parry (it raises the great mirror before it like a shield, the other mirrors turning to
you, and every blow jolts it); the Reflection (after a parry, a straight thrust from behind the mirror); Hall of Mirrors
(it spreads all six arms and turns every mirror out on you, turning slowly on the spot); Through the Glass (it lifts the
great mirror high and draws it down over itself, as if stepping through a frame); for whoever is behind it, Over the
Shoulder (a lower mirror turns to show it you and it thrusts back under its arm without turning its head); and Shatter
(the signature: it draws every mirror in close round its face, turning from one to the next faster and faster, and
flings all six arms wide as the mirrors burst; they grow back as it recovers). Broken, it sinks onto one knee with its
mirrors raised round its face to see that it is not marked; it dies looking at itself, its mirrors breaking one by one,
and lies down on its side with the last mirror held to its face.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "sovereign_mirrors"
SCALE = 1.8  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Vanity (docs/bosses/ROLLOUT.md, A). Long legs, a narrow waist, a small upright chest, the head high on a long
# neck. Value story: the mirrors' glints are the brightest thing on it, then its porcelain face and hands, the white
# satin; the darks are its hair, its boots and the coat's inner tail.
P = plans.Biped(leg=44, thigh=22, foot=(4, 4, 9), pelvis=(14, 7, 9), spine=(9, 10, 7), chest=(17, 13, 10), neck=(4, 6, 4),
                head=(10, 11, 10), arm=(4, 14, 4), forearm=(3, 13, 3), hand=(3, 5, 4), stance=3.5, hunch=0, leg_w=(5, 4))
# The six arms: (pair, its suffix, the bone it grows from, the shoulder there, upper and forearm lengths)
PAIRS = (("", "chest", (9, -11, 0), 14, 13), ("2", "chest", (8, -5, 2), 14, 13), ("3", "spine", (5, -8, 1.5), 12, 11))
HANDS = tuple(f"hand{k}_{s}" for k, *_ in PAIRS for s in "lr")
REST = {"hand_l": (13.0, 66.0, -17.0), "hand_r": (-11.0, 48.0, -5.0), "hand2_l": (18.0, 86.0, -9.0), "hand2_r": (-18.0, 86.0, -9.0),
        "hand3_l": (19.0, 61.0, -12.0), "hand3_r": (-19.0, 61.0, -12.0)}  # the wrists at rest (x, up, z): the mirrors round its face
POLE = {"": {"l": (0.6, 0.5, 0.4), "r": (-0.6, 0.5, 0.4)}, "2": {"l": (0.9, 0.3, 0.2), "r": (-0.9, 0.3, 0.2)},
        "3": {"l": (0.5, 0.2, 0.8), "r": (-0.5, 0.2, 0.8)}}  # the elbows: the top pair down and out, the middle out, the low back
MIRRORS = {"glass_l": ("hand_l", 7), "glass2_l": ("hand2_l", 5), "glass2_r": ("hand2_r", 5), "glass3_l": ("hand3_l", 5), "glass3_r": ("hand3_r", 5)}
BLADE_LINE = ((-0.12, 0.8, -0.6), (1, 0, 0))  # the blade at rest: hanging down and forward at its right side
BLADE = 33.0  # the blade's tip from the hilt's middle (its bone)
TIP = (0, BLADE, 0)
FACE = (0, -5, -5)  # where its mirrors look: the middle of its face, in the head's space
HEAD_REST = (-8, 14, 0)  # its head at rest: tipped up a little and turned to the great mirror in its left hand
YOU = (0.0, 70.0, -400.0)  # a point far ahead (x, up, z): where it turns a mirror on you
ARMS = tuple(f"{b}{k}_{s}" for k, *_ in PAIRS for b in ("arm", "forearm", "hand") for s in "lr")
ON_TIME = {b: 0 for b in ARMS + tuple(MIRRORS) + ("blade",)}  # its hands and mirrors go where they are sent


def _mirror(b, nm, h):
    """A round hand mirror on its bone: the handle down through the fist (+y), the glass above it facing north (-z) in
    a rose-gold frame with its corners cut, so it reads round; a small finial on top."""
    w = 2 * h - 2
    top = -3 - 2 * h
    b.cube(f"{nm}_handle", -0.5, -2, -0.5, 1, 6, 1)
    b.cube(f"{nm}_glass", -(h - 1), top, -0.5, w, 2 * h, 1)
    b.cube(f"{nm}_gl", h - 1, top + 1, -0.5, 1, w, 1)
    b.cube(f"{nm}_gr", -h, top + 1, -0.5, 1, w, 1)
    b.cube(f"{nm}_ft", -(h - 1), top - 1, -1, w, 1, 2)
    b.cube(f"{nm}_fb", -(h - 1), -3, -1, w, 1, 2)
    b.cube(f"{nm}_fl", h, top + 1, -1, 1, w, 2)
    b.cube(f"{nm}_fr", -h - 1, top + 1, -1, 1, w, 2)
    b.cube(f"{nm}_fin", -1, top - 3, -0.5, 2, 2, 1)


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("hips", -7, -4, -4.5, 14, 7, 9)
    pel.cube("peplum", -8, -1, -5.5, 16, 4, 11)
    # the coat's tail, hanging behind to the backs of its knees, loose on a spring
    tl = pel.bone("tail", (0, 1, 5), (6, 0, 0))
    tl.cube("tail", -6, 0, -0.5, 12, 18, 1)
    # long legs in dove-grey hose; black boots to the knee with a silver heel and a pointed toe
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -2.5, -1, -2.5, 5, 23, 5, mirror=mir)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -2, 0, -2, 4, 18, 4, mirror=mir)
        sn.cube("cuff_" + nm, -2.5, -1, -2.5, 5, 3, 5, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -2, 0, -6, 4, 4, 8, mirror=mir)
        ft.cube("toe_" + nm, -1, 2, -8, 2, 2, 2, mirror=mir)
        ft.cube("heel_" + nm, -1, 1, 2, 2, 3, 1, mirror=mir)
    # a narrow waist under a rose sash, and a small upright bodice of brocaded satin
    sp = pel.bone("spine", (0, -4, 0))
    sp.cube("waist", -4.5, -10, -3.5, 9, 10, 7)
    sp.cube("sash", -5, -3, -4, 10, 3, 8)
    ch = sp.bone("chest", (0, -10, 0))
    ch.cube("chest", -8.5, -13, -5, 17, 13, 10)
    ch.cube("bodice", -5, -12, -5.5, 10, 9, 1)
    # the long neck and the porcelain head, its hair lacquered up into a tall knot with a silver pin through it
    nk = ch.bone("neck", (0, -13, -0.5))
    nk.cube("neck", -2, -6, -2, 4, 6, 4)
    hd = nk.bone("head", (0, -6, 0), HEAD_REST)
    hd.cube("head", -5, -11, -5, 10, 11, 10)
    hd.cube("crown", -5.5, -12, -5.5, 11, 3, 11)
    hd.cube("nape", -5.5, -9, 2.5, 11, 7, 3)
    hd.cube("knot", -3, -17, -1, 6, 5, 6)
    hd.cube("pin", -6, -15, 1, 12, 1, 1)
    hd.cube("pearl_l", 5, -3, -1, 1, 2, 1)
    hd.cube("pearl_r", -6, -3, -1, 1, 2, 1)
    # the six arms: the top pair in satin sleeves with puffed caps and lace cuffs, the lower two pairs bare porcelain
    # with gold bangles; each hand a fist round what it holds
    for k, at, sh, ua, fa in PAIRS:
        parent = ch if at == "chest" else sp
        for side, nm in ((1, "l"), (-1, "r")):
            mir = side < 0
            a = parent.bone(f"arm{k}_{nm}", (side * sh[0], sh[1], sh[2]))
            f = a.bone(f"forearm{k}_{nm}", (0, ua, 0))
            h = f.bone(f"hand{k}_{nm}", (0, fa, 0))
            if k == "":
                a.cube("puff_" + nm, -3, -2, -3, 6, 5, 6, mirror=mir)
                a.cube("arm_" + nm, -2, 0, -2, 4, ua, 4, mirror=mir)
                f.cube("fore_" + nm, -1.5, 0, -1.5, 3, fa, 3, mirror=mir)
                f.cube("lace_" + nm, -2, fa - 3, -2, 4, 3, 4, mirror=mir)
            else:
                a.cube(f"arm{k}_{nm}", -1.5, 0, -1.5, 3, ua, 3, mirror=mir)
                f.cube(f"fore{k}_{nm}", -1.5, 0, -1.5, 3, fa, 3, mirror=mir)
            h.cube(f"hand{k}_{nm}", -1.5, 0, -2, 3, 5, 4, mirror=mir)
    for g, (hand, half) in MIRRORS.items():
        _mirror(r.by_name[hand].bone(g, (0, 3, -0.5)), g, half)
    # the glass blade in its right top hand: a silver hilt and a long faceted blade of glass along the bone's +y
    bl = r.by_name["hand_r"].bone("blade", (0, 3, 0))
    bl.cube("grip", -0.5, -3, -0.5, 1, 5, 1)
    bl.cube("pommel", -1, -4, -1, 2, 1, 2)
    bl.cube("guard", -3.5, 2, -1, 7, 1, 2)
    bl.cube("blade", -1, 3, -0.5, 2, int(BLADE) - 3, 1)
    r.look = [("head", 0.3, 0.25), ("neck", 0.2, 0.15)]  # it hardly looks away from itself
    r.size = P.leg / 36.0
    r.shadow = 1.8
    # its rest is its bind pose: the arms solved onto their rest wrists, the hands and mirrors turned to its face
    for k, *_ in PAIRS:
        for side in "lr":
            ik.rest_limb(r, f"arm{k}_{side}", f"forearm{k}_{side}", f"hand{k}_{side}", REST[f"hand{k}_{side}"], POLE[k][side])
    fr = core.frames(r, {})
    rot = ik.orient(r, fr, "hand_r", *BLADE_LINE)
    b = r.by_name["hand_r"]
    b.rot = tuple(b.rot[i] + rot[i] for i in range(3))
    for g in MIRRORS:
        for bone, v in _aim(r, {}, g, "face").items():
            b = r.by_name[bone]
            b.rot = tuple(b.rot[i] + v[i] for i in range(3))
    # secondary parts (SPEC §35.10): the coat's tail and the pearls swing loose
    r.springs = [("tail", 0.5, 2.2, 0.15)]
    r.guard = list(HANDS) + ["head", "blade"] + list(MIRRORS)  # bones, not cubes
    return r


def _col(M, j):
    return (M[0][j], M[1][j], M[2][j])


def _aim(r, p, g, aim):
    """The turns (on top of rest) that lay a mirror's glass toward aim, its handle hanging below it, and close the fist
    round the handle, the body posed as in the key p: aim is "face" (its own face), ("at", (x, up, z)) a point, or
    ("dir", (x, y, z)) the way the glass looks (model directions, y down). The hand turns so the handle runs across its
    palm with the wrist as straight as that allows; the mirror turns on it to face exactly."""
    hand, h = MIRRORS[g]
    fr = core.frames(r, core.as_sample(p))
    w = core.point(fr, hand, (0, 0, 0))
    p0 = (w[0], w[1] - 6 - h, w[2])  # about where the glass sits: above the wrist (from the arm alone, so the aim settles)
    def way(a):
        if a == "face":
            return ik.unit(ik.sub(core.point(fr, "head", FACE), p0))
        if a[0] == "at":
            return ik.unit(ik.sub(ik.model(a[1]), p0))
        if a[0] == "mix":  # ("mix", aim, aim, f): f of the way from one to the other
            u, v = way(a[1]), way(a[2])
            return ik.unit(tuple(x + (y - x) * a[3] for x, y in zip(u, v)))
        return ik.unit(a[1])
    n = way(aim)
    z = tuple(-c for c in n)
    down = (0.0, 1.0, 0.0)
    y = ik.unit(ik.v(down, z, -ik.dot(down, z)))
    x = ik.cross(y, z)
    fd = _col(fr["forearm" + hand[4:]][0], 1)
    hy = ik.v(fd, y, -ik.dot(fd, y))
    hy = ik.unit(hy) if ik.dot(hy, hy) > 1e-6 else z
    q = dict(p)
    q[hand] = ik.orient(r, fr, hand, hy, y)
    fr2 = core.frames(r, core.as_sample(q))
    return {hand: q[hand], g: ik.orient(r, fr2, g, y, x)}


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SATIN = Ramp("#34323e", "#4e4c5a", "#6c6a7a", "#8c8a9a", "#aeacba", "#cccad6", "#e4e3ec", "#f6f5fa", "#ffffff")
BROCADE = Ramp("#2a2c34", "#444852", "#626874", "#848a96", "#a6acb8", "#c8ced8", "#e6eaf0")
PORCELAIN = Ramp("#4a3836", "#6c524e", "#90726a", "#b2928a", "#d0b0a6", "#e8ccc2", "#f6e2da", "#fff2ec")
ROSE = Ramp("#2e0c1a", "#4c1428", "#6e203a", "#94304e", "#b84664", "#d6627e", "#ec8ca2")
HOSE = Ramp("#24222a", "#34313c", "#46424f", "#5a5664", "#6e6a79", "#83808e")
BOOT = Ramp("#060608", "#0c0c10", "#141419", "#1e1e25", "#2a2a33", "#383844")
HAIR = Ramp("#050409", "#0a0812", "#110e1c", "#1a1628", "#262036", "#363048")
GILT = Ramp("#3a1e16", "#5a3224", "#7c4a36", "#a0684c", "#c48a66", "#e0ac88", "#f4d0b2", "#fff0e0")
GLASS = ("#1c2438", "#2c3a54", "#42566e", "#5e788c", "#86a2b2", "#b4ccd8", "#dceaf0", "#ffffff")
DIAMOND = Ramp("#1c3a46", "#2a5664", "#3e7a88", "#5aa0ac", "#86c4cc", "#b6e2e6", "#e2f8fa", "#ffffff")
EYE = ("#2a1838", "#5a3a7a", "#9a7ac8")


def paint(r, P_):
    """White satin with a fine silver brocade, shaded cool; a rose sash and lips; porcelain skin, a little rosy at the
    cheeks and knuckles; dove-grey hose and black boots with silver heels; lacquered black hair with a violet sheen and a
    silver pin; rose-gold frames; the mirrors' glass a cool sky darkening downward with a bright diagonal glint (it
    glows), the great mirror showing the pale ghost of its face; the blade clear glass, its edges lit."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in ["chest", "hips", "peplum", "waist"] + of("puff_", "arm_", "fore_"):
        P_.paint(c, SATIN, 6.4, None, grad=1.1, rim=1.0, ao=1.0, noise=0.18, nscale=4)
        for fname in SIDES + ("top",):
            f = P_.face(c, fname)
            for i, j in f.each():
                if (i + j) % 4 == 0 and (i - j) % 4 == 0:
                    f.set(i, j, BROCADE.at(5.0))
    P_.paint("tail", SATIN, 4.4, None, grad=1.4, rim=0.8, ao=1.2, noise=0.2, nscale=4)
    P_.paint("bodice", BROCADE, 5.6, None, grad=1.0, rim=1.2, ao=0.6, noise=0.2, nscale=3)
    f = P_.face("bodice", "north")
    for i, j in f.each():  # a lattice of silver thread with a seed pearl at each crossing
        d = (i + j) % 4 == 0 or (i - j) % 4 == 0
        f.set(i, j, BROCADE.at(6.6) if d else SATIN.at(6.6))
        if (i + j) % 4 == 0 and (i - j) % 4 == 0:
            f.set(i, j, hexc("#fffaf0", 255))
    P_.paint("sash", ROSE, 4.6, None, grad=0.8, rim=1.0, ao=0.6, noise=0.25, nscale=3)
    for c in of("lace_"):
        P_.paint(c, SATIN, 7.4, None, grad=0.6, rim=0.6, ao=0.3, noise=0.5, nscale=1)
    for c in ["neck", "head"] + of("arm2_", "arm3_", "fore2_", "fore3_", "hand"):
        P_.paint(c, PORCELAIN, 6.2, None, grad=0.8, rim=1.0, ao=0.8, noise=0.08, nscale=6)
    for c in of("arm2_", "arm3_"):  # gold bangles on the bare arms
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if j in (f.h - 3, f.h - 5):
                    f.set(i, j, GILT.at(6.4))
    f = P_.face("neck", "north")
    for i, j in f.each():  # the pearl choker
        if j == 2:
            f.set(i, j, hexc("#fff8f0" if i % 2 == 0 else "#d8ccc8", 255))
    for c in of("thigh_"):
        P_.paint(c, HOSE, 4.6, None, grad=1.0, rim=0.8, ao=0.8, noise=0.12, nscale=5)
    for c in of("shin_", "cuff_", "foot_", "toe_"):
        P_.paint(c, BOOT, 3.6, None, grad=0.8, rim=1.6, ao=0.4, noise=0.15, nscale=4)
    for c in of("heel_", "pin", "pommel", "guard", "grip"):
        P_.paint(c, BROCADE, 5.4, None, grad=0.6, rim=1.6, ao=0.3, noise=0.1, nscale=2)
    for c in ("crown", "nape", "knot"):
        P_.paint(c, HAIR, 2.4, None, grad=1.4, rim=1.6, ao=0.6, noise=0.2, nscale=2)
        for fname in SIDES + ("top",):
            f = P_.face(c, fname)
            for i, j in f.each():  # a lacquer sheen, a band of light across the hair
                if abs((i + 2 * j) % 9 - 4) < 1:
                    f.set(i, j, HAIR.at(5.4))
    for c in of("pearl_"):
        P_.paint(c, SATIN, 8.0, None, grad=0.4, rim=0.6, ao=0.2)
    _face(P_)
    for g, (_, h) in MIRRORS.items():
        for c in of(g + "_f", g + "_handle"):
            P_.paint(c, GILT, 6.2, None, grad=0.6, rim=1.8, ao=0.3, noise=0.2, nscale=2)
        for fname in SIDES + ("top", "bottom"):
            f = P_.face(g + "_fin", fname)
            for i, j in f.each():
                f.set(i, j, GILT.at(7.0))
        _glass(P_, g, h)
    P_.paint("blade", DIAMOND, 5.0, None, grad=0.6, rim=1.4, ao=0.2, noise=0.1, nscale=3)
    for fname in SIDES:
        f, gl = P_.face("blade", fname), P_.face("blade", fname, glow=True)
        for i, j in f.each():
            edge = i in (0, f.w - 1)
            f.set(i, j, DIAMOND.at(7.6 if edge else (4.4 + 1.2 * ((j // 3) % 2))))
            if edge:
                gl.set(i, j, DIAMOND.at(7.0)[:3] + (150,))
    return P_.skin, P_.glow


def _face(P_):
    """The porcelain face: fine dark brows, long-lashed eyes with violet irises, rosy cheeks, a shadow under the nose,
    and small rose lips curved up at the corners in a smile."""
    f = P_.face("head", "north")
    W = f.w
    for i, j in f.each():
        m = min(i, W - 1 - i)  # 0 at either edge of the face, 4 in the middle
        col = PORCELAIN.at(6.4 - 0.3 * max(0, j - 8))
        if j <= 1:
            col = HAIR.at(2.0)
        elif j == 2 and m <= 1:
            col = HAIR.at(2.4)
        elif j == 3 and 1 <= m <= 3:
            col = HAIR.at(3.0)
        elif j == 4 and 1 <= m <= 3:
            col = hexc("#120a14", 255)
        elif j == 5 and 1 <= m <= 3:
            col = hexc(EYE[1], 255) if m == 2 else hexc("#efe6ea", 255)
        elif j == 6 and m == 1:
            col = ROSE.at(6.2)
        elif j == 7 and m == 4:
            col = PORCELAIN.at(5.0)
        elif j == 8 and m == 2:
            col = ROSE.at(5.0)
        elif j == 9 and m >= 3:
            col = ROSE.at(4.6 if m == 4 else 5.4)
        f.set(i, j, col)


def _glass(P_, g, h):
    """A mirror's glass: a cool sky darkening downward, a bright diagonal glint that glows; in the great mirror the pale
    ghost of its own face. The back is rose-gold chased with a rosette."""
    for c in (g + "_glass", g + "_gl", g + "_gr"):
        f, gl = P_.face(c, "north"), P_.face(c, "north", glow=True)
        off = {g + "_glass": 1, g + "_gl": 0, g + "_gr": 2 * h - 1}[c]
        for i, j in f.each():
            u = (j + (1 if c != g + "_glass" else 0)) / (2 * h - 1)
            col = GLASS[max(0, min(6, int(round(6 - 5 * u))))]
            x = i + off if c == g + "_glass" else off
            glint = abs((x + j) - (2 * h - 2)) < 1 or abs((x + j) - (2 * h + 1)) < 0.6
            if glint:
                col = GLASS[7]
                gl.set(i, j, hexc("#ffffff", 120))
            f.set(i, j, hexc(col, 255))
        # the back: polished silver, so from outside it still reads as a mirror, a soft sheen across it and a rose-gold
        # rosette in its middle
        fb = P_.face(c, "south")
        for i, j in fb.each():
            x = (2 * h - 1 - i - off) if c == g + "_glass" else (2 * h - 1 - off)
            u = (j + (1 if c != g + "_glass" else 0)) / (2 * h - 1)
            sheen = abs((x - j) + 1) < 1.5
            col = BROCADE.at(6.6 if sheen else 5.4 - 1.6 * u)
            dx, dy = x - (h - 0.5), (j + (1 if c != g + "_glass" else 0)) - (h - 0.5)
            if dx * dx + dy * dy <= 2.6:
                col = GILT.at(6.6 if dx * dx + dy * dy < 0.6 else 5.2)
            fb.set(i, j, col)
        for fname in ("west", "east", "top", "bottom"):
            fb = P_.face(c, fname)
            for i, j in fb.each():
                fb.set(i, j, GILT.at(6.0))
    if h >= 7:  # the great mirror: its face in the glass, pale and faint
        f = P_.face(g + "_glass", "north")
        cx = (f.w - 1) / 2
        for i, j in f.each():
            dx, dy = (i - cx) / 2.6, (j - 6.0) / 3.4
            if dx * dx + dy * dy <= 1.0:
                f.set(i, j, PORCELAIN.at(6.8 if dy < 0.4 else 6.0))
        for i, j in ((int(cx - 1), 5), (int(cx + 2), 5)):
            f.set(i, j, hexc("#3a2a50", 255))
        for i in (int(cx), int(cx + 1)):
            f.set(i, 8, ROSE.at(5.4))


# ---------------------------------------------------------------------------------------------------------------------
# Keys

def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), arms=None, blade=None, glass=None,
          legs=None, feet=("l", "r"), step=None, turn=None, more=None):
    """A whole-body key over its rest (which is its bind pose). It bends at the hips (hips: turn, at: x, up, z), the
    waist (spine), the chest, the neck and the head. The feet stay planted where they stand at rest (step: {side: (dx,
    dy, dz)} model pixels, y down) unless legs pose them. arms: {hand: where that wrist goes}, (x, up, z) model pixels
    or ("on", bone, point); a hand left out stays as it rests on its arm. blade: (ydir, xdir), the blade's line. glass:
    {mirror: aim} (see _aim); every mirror left out turns to its own face, wherever that is."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    if legs:
        p.update(legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step, turn=turn)
    if arms:
        fr = core.frames(r, core.as_sample(p))
        for hand, tgt in arms.items():
            k, side = hand[4:-2], hand[-1]
            q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
            p.update(ik.limb(r, fr, f"arm{k}_{side}", f"forearm{k}_{side}", hand, q, POLE[k][side]))
    if blade:
        fr = core.frames(r, core.as_sample(p))
        p["hand_r"] = ik.orient(r, fr, "hand_r", *blade)
    aims = {g: "face" for g in MIRRORS}
    aims.update(glass or {})
    for g, a in aims.items():
        if a is not None:
            p.update(_aim(r, p, g, a))
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


def _tw(r, a, b, f):
    """A key solved f of the way between two keys given as _pose's arguments: the wrists travel straight and the blade
    turns between its two lines, so a big turn of the hand does not take the long way round."""
    lerp = lambda x, y: tuple(u + (v - u) * f for u, v in zip(x, y))
    kw = {}
    for k in ("hips", "at", "spine", "chest", "neck", "head"):
        kw[k] = lerp(a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0)))
    aa, ba = a.get("arms") or {}, b.get("arms") or {}
    kw["arms"] = {h: lerp(aa.get(h, REST[h]), ba.get(h, REST[h])) for h in set(aa) | set(ba)}
    la, lb = a.get("blade") or BLADE_LINE, b.get("blade") or BLADE_LINE
    kw["blade"] = (ik.unit(lerp(ik.unit(la[0]), ik.unit(lb[0]))), lerp(la[1], lb[1]))
    ga, gb = a.get("glass") or {}, b.get("glass") or {}
    kw["glass"] = {g: ("mix", ga.get(g, "face"), gb.get(g, "face"), f) for g in set(ga) | set(gb)}
    sa, sb = a.get("step") or {}, b.get("step") or {}
    if sa or sb:  # a foot that moves is carried a little clear of the floor on its way
        kw["step"] = {}
        for side in set(sa) | set(sb):
            u, v = sa.get(side, (0, 0, 0)), sb.get(side, (0, 0, 0))
            d = math.dist(u, v)
            x, y, z = lerp(u, v)
            kw["step"][side] = (x, y - min(2.5, 0.25 * d) * math.sin(math.pi * f), z)
    for k in ("legs", "feet", "more"):
        if k in a or k in b:
            kw[k] = (b if f >= 0.5 else a).get(k, a.get(k))
    return _pose(r, **kw)


def _path(r, a, b, n=6, t0=0.0, t1=1.0):
    """A stage's keys from one key to another (both as _pose's arguments), solved along the way and eased in and out,
    from t0 to t1 of the stage."""
    return [(t0 + (t1 - t0) * i / n, _tw(r, a, b, core.ease_io(i / n))) for i in range(1, n + 1)]


def _back(r, end):
    """A recovery in two steps to rest, so nothing snaps home."""
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.8))]


def _on(r, end, f=0.08):
    """The follow-through: the end carried on a little past where it was going (away from rest)."""
    return _mix(end, _pose(r), -f)


def _staged(mv, stage, f):
    W, A = mv.seconds("windup"), mv.seconds("active")
    return {"windup": 0.0, "active": W, "recover": W + A}[stage] + f * mv.seconds(stage)


def _scales(r, clip, bone, pts, interp="lin", ends=True):
    """A clip's scale keys for one bone: [(seconds, scale)], 1 at both ends (ends False: only at the start). Straight
    lines: a curve would overshoot through nothing and out the other side."""
    c = r.clips[clip]
    ks = dict([(0.0, 1.0)] + ([(c.length, 1.0)] if ends else []))
    ks.update((round(t, 4), v) for t, v in pts)
    c.scale(bone, [(t, v, v, v) for t, v in sorted(ks.items())], interp)


def _gait(u, swing=18.0, bend=30.0, narrow=4.0):
    """A long elegant stride at phase u (0..1, the left foot planted first), each foot set down in front of the other
    on one line (the thighs turned in): thigh, knee and foot turns."""
    s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
    lk, rk = max(0.0, c) * bend + 3, max(0.0, -c) * bend + 3
    return {"thigh_l": (-swing * s - lk * 0.45, 0, narrow), "shin_l": (lk, 0, 0), "foot_l": (swing * s - lk * 0.55, 0, -narrow),
            "thigh_r": (swing * s - rk * 0.45, 0, -narrow), "shin_r": (rk, 0, 0), "foot_r": (-swing * s - rk * 0.55, 0, narrow)}



OUT = {"glass_l": ("dir", (0.85, -0.15, -0.5)), "glass2_l": ("dir", (0.7, -0.45, -0.55)), "glass2_r": ("dir", (-0.7, -0.45, -0.55)),
       "glass3_l": ("dir", (0.8, 0.05, -0.6)), "glass3_r": ("dir", (-0.8, 0.05, -0.6))}  # every mirror turned out on you, fanned
WIDE = {"hand_l": (30.0, 78.0, -8.0), "hand_r": (-30.0, 70.0, -6.0), "hand2_l": (24.0, 98.0, -4.0), "hand2_r": (-24.0, 98.0, -4.0),
        "hand3_l": (29.0, 58.0, -6.0), "hand3_r": (-29.0, 58.0, -6.0)}  # all six arms spread wide
CLOSE = {"hand_l": (7.0, 76.0, -13.0), "hand2_l": (13.0, 88.0, -12.0), "hand2_r": (-13.0, 88.0, -12.0), "hand3_l": (10.0, 74.0, -11.0),
         "hand3_r": (-10.0, 74.0, -11.0)}  # every mirror drawn in close round its face


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def idle(r):
    """It stands admiring itself (8 s): its weight on one hip and then the other, it turns its face to the great mirror
    and tilts it, glances into the mirror over its right shoulder to see its profile, lifts its chin proudly with the
    high mirrors rising, and turns the glass blade over idly in its fingers; it breathes. Every mirror follows its face."""
    k0 = _pose(r)
    admire = _pose(r, hips=(0, 4, 2), at=(0.6, -0.3, 0), chest=(-2, 6, -1), neck=(0, 4, 0), head=(-4, 10, 9), arms={"hand_l": (12.0, 69.0, -15.0)})
    profile = _pose(r, hips=(0, -4, -2), at=(-0.6, -0.3, 0), chest=(-1, -6, 1), neck=(0, -8, 0), head=(2, -34, -4), arms={"hand2_r": (-16.0, 84.0, -11.0)})
    proud = _pose(r, at=(0, 0.4, 0), spine=(-1, 0, 0), chest=(-4, 0, 0), neck=(-4, 0, 0), head=(-12, -6, 0),
                  arms={"hand2_l": (18.0, 89.0, -8.0), "hand2_r": (-18.0, 89.0, -8.0)})
    breath = _pose(r, at=(0, 0.3, 0), chest=(-1.5, 0, 0), head=(-1, 0, 0))
    turn1 = _pose(r, hips=(0, 3, 1), at=(0.4, -0.2, 0), chest=(-1, 4, 0), head=(0, 4, 4), blade=((0.55, 0.6, -0.55), (0, 0, -1)))
    turn2 = _pose(r, hips=(0, 3, 1), at=(0.4, -0.2, 0), chest=(-1, 4, 0), head=(0, 4, 4), blade=((-0.6, 0.55, -0.55), (0, 0, 1)))
    keyed(r, "idle", 8.0, [(0, k0), (0.9, breath), (1.7, admire), (2.6, _mix(admire, k0, 0.3)), (3.1, admire), (3.9, _mix(admire, profile, 0.5)),
                           (4.3, profile), (5.0, profile), (5.6, proud), (6.3, _mix(proud, k0, 0.5)), (6.7, turn1), (7.1, turn2), (7.6, k0), (8.0, k0)], loop=True)


def gaits(r):
    """It walks as on a stage, each foot set down in front of the other on one line, the hips swaying and the head held
    high and level, still turned to its great mirror; the mirrors ride steady round its face and the blade hangs from
    its fingers. It runs gliding, leaning into it, its mirrors drawn in close."""
    L, n, keys = 1.4, 24, []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        land = max(0.0, math.cos(4 * math.pi * (u - 0.04))) ** 2
        p = _pose(r, hips=(1, 7 * s, -4 * s), at=(-0.8 * s, -0.8 * land + 0.3, 0), spine=(0, -3 * s, 2 * s), chest=(0, -4 * s, 2 * s),
                  neck=(0, 3 * s, -1 * s), head=(0, 4 * s, 0), legs=_gait(u), feet=())
        keys.append((L * u, p))
    keyed(r, "walk", L, keys, loop=True)
    L, keys = 0.8, []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        land = max(0.0, math.cos(4 * math.pi * (u - 0.05))) ** 2
        p = _pose(r, hips=(12, 8 * s, -3 * s), at=(-0.6 * s, -1.6 * land + 0.4, 0), spine=(4, -4 * s, 0), chest=(4, -6 * s, 2 * s),
                  neck=(-8, 5 * s, 0), head=(-6, 5 * s, 0), legs=_gait(u, swing=30, bend=56, narrow=3), feet=(),
                  arms={**{h: CLOSE[h] for h in CLOSE}, "hand_r": (-12.0, 50.0, 4.0)}, blade=((-0.1, 0.55, 0.8), (1, 0, 0)))
        keys.append((L * u, p))
    keyed(r, "run", L, keys, loop=True)
    r.walk_stride = 3.0
    r.run_stride = 5.2


KNEEL = {"thigh_r": (8, 0, -2), "shin_r": (88, 0, 0), "foot_r": (76, 0, 0)}
KNEEL_STEP = {"l": (0, 0, -9)}


def reactions(r):
    """Struck, it flinches with its face turned away to keep it from harm, its arms jerking up; broken, it sinks onto its
    right knee, the blade's point on the floor, and holds every mirror up round its face to see that it is not marked;
    it rises with a push on the blade. Its death: it looks into the great mirror; its other mirrors break one after
    another and it starts at each; it sinks to its knees and lies down on its side with the last mirror held to its
    face."""
    hit = _pose(r, hips=(-3, 0, 0), at=(0, 0, 1.5), spine=(-3, 0, 0), chest=(-8, 0, 0), neck=(-6, 0, 0), head=(-6, 30, 12),
                arms={"hand2_l": (17.0, 90.0, -6.0), "hand2_r": (-17.0, 90.0, -6.0), "hand_l": (11.0, 72.0, -12.0)})
    back = _pose(r, chest=(2, 0, 0), head=(2, -4, -2))
    keyed(r, "hurt", 0.5, [(0, _pose(r)), (0.07, hit), (0.28, back), (0.5, _pose(r))])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(r, hips=(0, -8 * sg, -3 * sg), at=(-sg * 1.0, 0, 0), chest=(-3, -12 * sg, -8 * sg), neck=(0, -6 * sg, 0), head=(-4, -24 * sg, -10 * sg))
        b = _pose(r, chest=(1, 4 * sg, 2 * sg), head=(2, 6 * sg, 4 * sg))
        keyed(r, nm, 0.55, [(0, _pose(r)), (0.07, h), (0.3, b), (0.55, _pose(r))])
    prop = {"hand_r": (-11.0, 35.0, -14.0)}
    kneel = _pose(r, hips=(6, 0, 0), at=(0, -14, 1), legs=KNEEL, feet=("l",), step=KNEEL_STEP, spine=(4, 0, 0), chest=(6, 0, 0), neck=(-6, 0, 0),
                  head=(-10, 18, 6), arms={**CLOSE, **prop}, blade=((0.0, 1.0, -0.05), (1, 0, 0)))
    sag = _pose(r, hips=(3, 0, 0), at=(0, -3, 0), chest=(4, 0, 0), neck=(3, 0, 0), head=(4, 0, 0))
    lift = _pose(r, hips=(5, 0, 0), at=(0, -8, 0.5), step={"l": (0, 0, -6), "r": (0, -12, 12)}, chest=(5, 0, 0), neck=(0, 0, 0), head=(-4, 10, 3),
                 arms={**prop}, blade=((0.0, 1.0, -0.05), (1, 0, 0)))
    keyed(r, "kneel", 0.7, [(0, _pose(r)), (0.2, sag), (0.42, lift), (0.7, kneel)])
    push = _pose(r, hips=(5, 0, 0), at=(0, -7, 0.5), chest=(5, 0, 0), head=(-4, 8, 3), arms={"hand_r": (-11.0, 40.0, -12.0)}, blade=((0.0, 1.0, -0.05), (1, 0, 0)))
    keyed(r, "rise", 0.9, [(0, kneel), (0.45, push), (0.9, _pose(r))])
    death(r)


def death(r):
    """It dies looking at itself (4.8 s): it brings the great mirror close and gazes into it; its four other mirrors break
    one after another, low right, low left, high right, high left, and it starts at each, its face turning to the
    broken one; its legs give way onto its knees; it lies down on its left side, curled, the great mirror held to its
    face."""
    gaze = lambda a: _pose(r, hips=(2, 2, 0), at=(0, -1, 0), chest=(4, 4, 0), neck=(4, 6, 0), head=(4, 16 + a, 8), arms={"hand_l": (6.0, 74.0, -14.0)})
    start = lambda yaw: _pose(r, hips=(-2, 0, 0), at=(0, -0.5, 0.8), chest=(-5, 0, 0), neck=(-2, yaw * 0.3, 0), head=(-4, yaw, 6), arms={"hand_l": (7.0, 75.0, -13.0)})
    knees = _pose(r, hips=(-2, 0, 0), at=(0, -19, 2), legs={"thigh_l": (-4, 0, -3), "shin_l": (88, 0, 0), "foot_l": (76, 0, 0), "thigh_r": (-4, 0, 3),
                                                           "shin_r": (88, 0, 0), "foot_r": (76, 0, 0)}, feet=(), spine=(6, 0, 0), chest=(10, 0, 0),
                  neck=(10, 0, 0), head=(10, 20, 6), arms={"hand_l": (6.0, 52.0, -12.0), "hand_r": (-12.0, 26.0, -8.0)}, blade=((0.2, 1.0, -0.3), (1, 0, 0)))
    buckle = _pose(r, hips=(4, 0, 0), at=(0, -9, -1.5), chest=(6, 0, 0), neck=(6, 0, 0), head=(6, 18, 6), arms={"hand_l": (7.0, 62.0, -14.0)})
    side = _laid(r, hips=(0, 10, 86), at=(0, 0, 0), legs={"thigh_l": (-50, 0, 0), "shin_l": (80, 0, 0), "foot_l": (30, 0, 0), "thigh_r": (-44, 0, 0),
                                                        "shin_r": (76, 0, 0), "foot_r": (30, 0, 0)}, feet=(), spine=(10, 0, 0), chest=(14, 0, 0),
                 neck=(6, 0, 0), head=(4, 14, 0), arms={"hand_l": ("on", "chest", (6, -18, -12)), "hand_r": ("on", "chest", (-6, 0, -14))},
                 blade=None)
    keyed(r, "death", 4.8, [(0, _pose(r)), (0.5, gaze(0)), (0.9, gaze(4)), (1.05, start(-30)), (1.3, gaze(0)), (1.45, start(26)), (1.75, gaze(0)),
                            (1.9, start(-22)), (2.2, gaze(0)), (2.35, start(20)), (2.6, gaze(-4)), (3.0, buckle), (3.3, knees),
                            (3.7, _lift(_mix(knees, side, 0.4), 2.5)), (4.2, side), (4.35, _mix(side, knees, 0.04)), (4.8, side)])
    for g, t in (("glass3_r", 1.05), ("glass3_l", 1.45), ("glass2_r", 1.9), ("glass2_l", 2.35)):
        _scales(r, "death", g, [(t - 0.04, 1.0), (t, 1.15), (t + 0.06, 0.0), (4.8, 0.0)], ends=False)


def _lift(k, d):
    """A key raised d model pixels."""
    pel = dict(k["pelvis"])
    x, y, z = pel.get("pos", (0, 0, 0))
    pel["pos"] = (x, y + d, z)
    return {**k, "pelvis": pel}


def _laid(r, **kw):
    """A key lying on the floor: the body set down so its lowest solid part rests on the floor."""
    p = _pose(r, **kw)
    fr = core.frames(r, core.as_sample(p))
    low = core.lowest(r, fr, [b.name for b in r.bones if b.cubes and not b.name.startswith(("glass2", "glass3"))])
    at = kw.get("at", (0, 0, 0))
    return _pose(r, **{**kw, "at": (at[0], at[1] + (low - 24.0) - 0.3, at[2])})


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def cut(r):
    """The forehand (LIGHT): it lifts the glass blade high over its right shoulder, turning away, and cuts down across
    in front of it to its left hip, the mirrors swinging round with its body and its face never leaving the great
    mirror."""
    cock = _pose(r, hips=(-2, 12, 0), at=(0, -0.5, 1), spine=(-2, 6, 0), chest=(-4, 14, 0), head=(-2, -6, 4),
                 arms={"hand_r": (-15.0, 92.0, 2.0)}, blade=((-0.25, -0.8, 0.55), (0, 0, 1)))
    mid = _pose(r, hips=(1, 0, 0), at=(0, -1, -0.5), chest=(2, 0, 0), head=(0, 8, 2), arms={"hand_r": (-4.0, 74.0, -26.0)}, blade=((0.35, 0.1, -0.93), (0, -1, 0)))
    hit = _pose(r, hips=(3, -12, 0), at=(0, -1.5, -1.5), spine=(2, -6, 0), chest=(6, -16, 2), head=(0, 18, 2), step={"r": (0, 0, -5)},
                arms={"hand_r": (10.0, 50.0, -22.0)}, blade=((0.6, 0.5, -0.62), (0, -1, 0)))
    after = _pose(r, hips=(4, -14, 0), at=(0, -1.8, -1.6), spine=(2, -7, 0), chest=(7, -19, 2), head=(0, 20, 2), step={"r": (0, 0, -5)},
                  arms={"hand_r": (13.0, 46.0, -17.0)}, blade=((0.75, 0.55, -0.35), (0, -1, 0)))
    return Move("cut", "LIGHT", 9, 4, 14, title="Glass Cut", track=8.0, rng=(0, 4.8), kinds=("combo", "near"), damage=1.0, knock=0.7, plant=(),
                hit=[("blade", TIP, 2.2), ("blade", (0, 18, 0), 2.0)], lag=ON_TIME, through=_on(r, after, 0.04),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.4)), (1.0, cock)], "active": [(0.4, mid), (0.75, hit), (1.0, after)], "recover": _back(r, after)})


def cut_b(r):
    """The backhand (LIGHT): from low at its left hip the blade whips up across in front of it to high over its right
    shoulder, the back of the hand leading, with a step."""
    low = _pose(r, hips=(2, -12, 0), at=(0, -1.5, 0), chest=(4, -18, 2), head=(0, 16, 4), arms={"hand_r": (12.0, 48.0, -14.0)},
                blade=((0.6, 0.62, -0.5), (0, -1, 0)))
    mid = _pose(r, hips=(0, 0, 0), at=(0, -1, -1), chest=(0, -2, 0), head=(0, 8, 2), arms={"hand_r": (1.0, 68.0, -27.0)}, blade=((-0.3, -0.3, -0.9), (0, 0, -1)))
    hit = _pose(r, hips=(-2, 12, 0), at=(0, -0.8, -2.5), chest=(-4, 18, -3), head=(-2, -4, 2), step={"r": (0, 0, -5)}, arms={"hand_r": (-17.0, 86.0, -16.0)},
                blade=((-0.6, -0.7, -0.4), (0, 0, -1)))
    after = _pose(r, hips=(-2, 14, 0), at=(0, -0.6, -2.7), chest=(-4, 21, -4), head=(-2, -6, 2), step={"r": (0, 0, -5)}, arms={"hand_r": (-19.0, 88.0, -10.0)},
                  blade=((-0.62, -0.75, -0.2), (0, 0, -1)))
    return Move("cut_b", "LIGHT", 8, 4, 14, title="Glass Cut", track=8.0, rng=(0, 4.8), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(),
                hit=[("blade", TIP, 2.2), ("blade", (0, 18, 0), 2.0)], lag=ON_TIME, through=_on(r, after, 0.04),
                stages={"windup": [(0.3, _mix(_pose(r), low, 0.4)), (1.0, low)], "active": [(0.4, mid), (0.75, hit), (1.0, after)], "recover": _back(r, after)})


def lunge(r):
    """Mirrored Lunge (HEAVY, its gap closer): it turns side-on with the blade drawn back level beside its face, and
    every mirror swings round to face you, a hall of you; it holds; then it lunges, the back leg driving straight and the
    front foot reaching far ahead, the blade arm straight out so the point arrives first; the mirrors come back round
    to its face as it lands."""
    ld = dict(hips=(-3, -36, 0), at=(0, -5, 3), spine=(0, -6, 0), chest=(-4, -8, 0), neck=(0, 10, 0), head=(-4, 28, 0),
              arms={"hand_r": (-10.0, 73.0, -1.0)}, blade=((0.0, 0.08, -1.0), (0, -1, 0)), glass=OUT)
    load = _pose(r, **ld)
    held = _pose(r, hips=(-3, -38, 0), at=(0, -5.5, 3.4), spine=(0, -6, 0), chest=(-5, -9, 0), neck=(0, 11, 0), head=(-4, 30, 0),
                 arms={"hand_r": (-10.0, 74.0, 0.0)}, blade=((0.0, 0.06, -1.0), (0, -1, 0)), glass=OUT)
    reach = _pose(r, hips=(6, -30, 0), at=(0, -9, -10), spine=(4, -4, 0), chest=(4, -6, 0), neck=(0, 8, 0), head=(-6, 24, 0),
                  step={"r": (0, -3, -22), "l": (0, 0, 4)}, arms={"hand_r": (-4.0, 66.0, -40.0)}, blade=((0.0, 0.12, -1.0), (0, -1, 0)), glass=OUT)
    land = _pose(r, hips=(8, -28, 0), at=(0, -12, -11), spine=(5, -4, 0), chest=(5, -6, 0), neck=(0, 8, 0), head=(-4, 22, 0),
                 step={"r": (0, 0, -26), "l": (0, 0, 4)}, arms={"hand_r": (-4.0, 62.0, -40.0)}, blade=((0.0, 0.2, -1.0), (0, -1, 0)), glass=OUT)
    mv = Move("lunge", "HEAVY", 18, 8, 22, title="Mirrored Lunge", hold=6, track=6.0, lock=10, advance=7.0, ability="Mirrored Lunge", rng=(5, 14),
              kinds=("gap",), damage=1.3, knock=1.0, plant=(), hit=[("blade", TIP, 2.4), ("hand_r", (0, 2, 0), 2.5)], lag=ON_TIME, through=land,
              stages={"windup": [(0.3, _mix(_pose(r), load, 0.3)), (0.52, _lift(_mix(_pose(r), load, 0.7), 1.2)), (0.75, load), (1.0, held)], "active": [(0.45, reach), (1.0, land)],
                      "recover": [(0.3, _mix(land, _pose(r), 0.2)), (0.7, _mix(land, _pose(r), 0.75))]})
    return mv


def parry(r):
    """Flawless Parry (a guard): it raises the great mirror before it like a shield, the glass turned on you, the blade
    crossed low behind it; its face stays turned to the mirrors above. Each blow that lands jolts the mirror back."""
    guard = _pose(r, hips=(-2, -10, 0), at=(0, -2, 1), chest=(-4, -8, 0), neck=(0, 6, 0), head=(-10, 4, 0), step={"l": (0, 0, -2)},
                  arms={"hand_l": (3.0, 70.0, -20.0), "hand_r": (-6.0, 60.0, -13.0)}, blade=((0.7, 0.3, -0.65), (1, 0, 0)),
                  glass={"glass_l": ("dir", (0.0, -0.05, -1.0))})
    catch = _pose(r, hips=(-3, -11, 0), at=(0, -2.8, 2.2), chest=(-6, -9, 0), neck=(0, 6, 0), head=(-12, 4, 0), step={"l": (0, 0, -2)},
                  arms={"hand_l": (3.0, 72.0, -17.5), "hand_r": (-6.0, 60.0, -12.0)}, blade=((0.7, 0.3, -0.65), (1, 0, 0)),
                  glass={"glass_l": ("dir", (0.0, -0.25, -1.0))})
    return Move("parry", "NONE", 8, 12, 12, title="Flawless Parry", track=8.0, ability="Flawless Parry", rng=(0, 5.0), kinds=("guard",), damage=0,
                knock=0, plant=(), lag=ON_TIME, through=guard,
                stages={"windup": [(0.4, _mix(_pose(r), guard, 0.6)), (1.0, guard)], "active": [(0.25, catch), (0.55, guard), (0.8, catch), (1.0, guard)],
                        "recover": _back(r, guard)})


def riposte(r):
    """The Reflection (after the parry): from behind the great mirror the blade drives straight out past its edge in a
    thrust, a step behind it, the mirror swinging aside."""
    ck = dict(hips=(-2, -14, 0), at=(0, -2, 1.5), chest=(-4, -12, 0), head=(-8, 6, 0), arms={"hand_l": (5.0, 70.0, -19.0), "hand_r": (-13.0, 64.0, -16.0)},
              blade=((0.05, 0.05, -1.0), (1, 0, 0)), glass={"glass_l": ("dir", (0.0, -0.05, -1.0))})
    cock = _pose(r, **ck)
    hit = _pose(r, hips=(4, -22, 0), at=(0, -4, -4), chest=(4, -16, 0), head=(-4, 10, 0), step={"r": (0, 0, -9)},
                arms={"hand_l": (16.0, 70.0, -12.0), "hand_r": (-4.0, 68.0, -38.0)}, blade=((0.04, 0.06, -1.0), (1, 0, 0)))
    after = _pose(r, hips=(5, -23, 0), at=(0, -4.4, -4.4), chest=(5, -17, 0), head=(-4, 10, 0), step={"r": (0, 0, -9)},
                  arms={"hand_l": (17.0, 70.0, -11.0), "hand_r": (-4.0, 67.0, -39.0)}, blade=((0.04, 0.1, -1.0), (1, 0, 0)))
    return Move("riposte", "LIGHT", 8, 4, 14, title="The Reflection", track=10.0, rng=(0, 5.0), kinds=("near",), damage=1.1, knock=0.8, plant=(),
                hit=[("blade", TIP, 2.2), ("blade", (0, 18, 0), 2.0)], lag=ON_TIME, through=_on(r, after, 0.04),
                stages={"windup": _path(r, {}, ck),
                        "active": [(0.6, hit), (1.0, after)], "recover": _back(r, after)})


def behind(r):
    """Over the Shoulder (for whoever is behind it): the low right mirror swings out to show it you over its shoulder,
    its face turning down into it; without turning round it draws the blade across its body and thrusts it straight
    back under its arm."""
    look = dict(neck=(4, -10, 0), head=(14, -34, -6))
    show = {"hand3_r": (-17.0, 66.0, 2.0)}
    see = {"glass3_r": ("dir", (-0.3, -0.35, 0.88))}
    cock = _pose(r, hips=(0, -6, 0), chest=(0, -6, 0), arms={**show, "hand_r": (4.0, 56.0, -10.0)}, blade=((0.15, 0.05, 1.0), (0, -1, 0)), glass=see, **look)
    hit = _pose(r, hips=(0, -12, 0), at=(0, -1.5, 2), chest=(2, -14, 2), arms={**show, "hand_r": (6.0, 55.0, 12.0)}, blade=((0.1, 0.12, 1.0), (0, -1, 0)),
                glass=see, step={"r": (0, 0, 1), "l": (0, 0, 2)}, **look)
    after = _pose(r, hips=(0, -13, 0), at=(0, -1.8, 2.3), chest=(2, -15, 2), arms={**show, "hand_r": (6.0, 54.0, 14.0)}, blade=((0.1, 0.16, 1.0), (0, -1, 0)),
                  glass=see, step={"r": (0, 0, 1), "l": (0, 0, 2)}, **look)
    return Move("behind", "LIGHT", 10, 4, 14, title="Over the Shoulder", track=0.0, rng=(0, 4.4), kinds=("behind",), damage=1.0, knock=0.6, plant=(),
                hit=[("blade", TIP, 2.0), ("blade", (0, 18, 0), 1.8)], lag=ON_TIME, through=_on(r, after, 0.04),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.6, hit), (1.0, after)], "recover": _back(r, after)})


def hall(r):
    """Hall of Mirrors (a summoning): it spreads all six arms wide and turns every mirror out on you, rises onto its
    toes and turns once round slowly on the spot like a dancer, its face turned up; its reflections step out."""
    spread = lambda a: _pose(r, hips=(-2, 0, 0), at=(0, 1.0 + 0.4 * a, 0), spine=(-2, 0, 0), chest=(-6, 0, 0), neck=(-4, 0, 0), head=(-14, 0, 4 * a),
                             feet=(), arms=WIDE, blade=((-1.0, 0.25, -0.1), (0, 0, -1)), glass=OUT)
    mv = Move("hall", "NONE", 12, 24, 14, title="Hall of Mirrors", track=0.0, ability="Hall of Mirrors", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
              plant=(), lag=ON_TIME, through=_mix(spread(0), _pose(r), 0.3),
              stages={"windup": [(0.45, _mix(_pose(r), spread(0), 0.55)), (1.0, spread(0))], "active": [(0.5, spread(1)), (1.0, spread(0))],
                      "recover": _back(r, _mix(spread(0), _pose(r), 0.3))})
    return mv


def through(r):
    """Through the Glass (it steps through its own reflection): it lifts the great mirror high over its head, glass
    down, and draws it down in front of itself to the floor as it bows forward into it, as if it walks into the frame;
    it is gone, and it straightens where it comes out."""
    hk = dict(hips=(-3, 0, 0), at=(0, 1.0, 0), chest=(-8, 0, 0), neck=(-6, 0, 0), head=(-16, 0, 0), arms={"hand_l": (3.0, 104.0, -10.0)},
              glass={"glass_l": ("dir", (0.0, 0.75, -0.65))})
    bk = dict(hips=(22, 0, 0), at=(0, -6, -3), spine=(8, 0, 0), chest=(12, 0, 0), neck=(6, 0, 0), head=(4, 0, 0),
              arms={"hand_l": (3.0, 36.0, -24.0), **{h: CLOSE[h] for h in ("hand2_l", "hand2_r")}}, glass={"glass_l": ("dir", (0.0, 0.1, 1.0))})
    high, bow = _pose(r, **hk), _pose(r, **bk)
    out = _pose(r, hips=(-4, 0, 0), at=(0, 0.6, 1), chest=(-6, 0, 0), neck=(-2, 0, 0), head=(-8, 10, 4), arms={"hand_l": (12.0, 74.0, -18.0)})
    return Move("through", "NONE", 14, 10, 16, title="Through the Glass", track=4.0, ability="Through the Glass", rng=(6, 20), kinds=("gap",), damage=0,
                knock=0, plant=(), lag=ON_TIME, through=_mix(out, _pose(r), 0.3),
                stages={"windup": [(0.4, _mix(_pose(r), high, 0.5)), (1.0, high)], "active": [(0.28, _lift(_mix(high, bow, 0.5), 1.5)), (0.55, bow), (1.0, _mix(bow, out, 0.5))],
                        "recover": [(0.4, out), (0.75, _mix(out, _pose(r), 0.7))]})


def shatter(r):
    """Shatter (PERILOUS, the signature): it draws every mirror in close round its face and turns from one to the next,
    faster and faster, trembling; then it flings all six arms wide with its head thrown back and the mirrors burst; as it
    recovers they grow back out of their frames."""
    close = lambda yaw, a=0.0: _pose(r, hips=(2, 0, 0), at=(0, -1.5 - a, 0.5), spine=(3, 0, 0), chest=(6, 0, 0), neck=(4, yaw * 0.3, 0),
                                     head=(6, yaw, 6 * (1 if yaw > 0 else -1)), arms=CLOSE)
    fling = lambda a: _pose(r, hips=(-6, 0, 0), at=(0, 1.2, 1), spine=(-6, 0, 0), chest=(-16, 0, 0), neck=(-10, 0, 0), head=(-24, 0, 3 * a), feet=("l", "r"),
                            arms={h: (v[0] * 1.08, v[1] + 4, v[2] + 2) for h, v in WIDE.items()}, blade=((-1.0, -0.2, 0.0), (0, 0, -1)), glass=OUT)
    wind = [(0.2, _mix(_pose(r), close(0), 0.6)), (0.34, close(26)), (0.5, close(-26)), (0.62, close(22, 0.3)), (0.72, close(-22, 0.4)),
            (0.8, close(20, 0.6)), (0.87, close(-20, 0.7)), (0.93, close(16, 0.9)), (1.0, close(0, 1.2))]
    mv = Move("shatter", "PERILOUS", 34, 12, 28, title="Shatter", signature=True, track=0.0, ability="Shatter", rng=(0, 16), kinds=("area", "ranged"),
              damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(fling(0), _pose(r), 0.2),
              stages={"windup": wind, "active": [(0.3, fling(1)), (1.0, fling(0))],
                      "recover": [(0.35, _mix(fling(0), _pose(r), 0.45)), (0.7, _mix(fling(0), _pose(r), 0.85))]})
    return mv


def preen(r):
    """Its intro: it stands with its arms lowered and its mirrors hanging face down, its head bowed; it raises them one
    after another round its face, looking into each as it comes up; it tilts its head to the great mirror, smooths its
    look with a turn of the chin, and settles, smiling."""
    low = {"hand_l": (12.0, 48.0, -8.0), "hand2_l": (18.0, 60.0, -2.0), "hand2_r": (-18.0, 60.0, -2.0), "hand3_l": (15.0, 44.0, -4.0),
           "hand3_r": (-15.0, 44.0, -4.0)}
    hang = {g: ("dir", (0.0, 1.0, -0.15)) for g in MIRRORS}
    bowed = _pose(r, chest=(6, 0, 0), neck=(10, 0, 0), head=(16, 0, 0), arms=low, glass=hang)
    one = _pose(r, chest=(2, 0, 0), neck=(4, 0, 0), head=(2, -24, 0), arms={**low, "hand3_r": REST["hand3_r"]}, glass={**hang, "glass3_r": "face"})
    two = _pose(r, chest=(1, 0, 0), neck=(2, 0, 0), head=(0, 24, 0), arms={**low, "hand3_r": REST["hand3_r"], "hand3_l": REST["hand3_l"]},
                glass={g: a for g, a in hang.items() if g.startswith("glass2") or g == "glass_l"})
    three = _pose(r, chest=(-1, 0, 0), head=(-12, -20, 0), arms={"hand_l": low["hand_l"]}, glass={"glass_l": hang["glass_l"]})
    four = _pose(r, chest=(-2, 0, 0), head=(-6, 22, 8))
    tilt = _pose(r, chest=(-2, 2, 0), head=(-10, 18, 14))
    mv = Move("preen", "NONE", 16, 44, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME,
              through=_mix(tilt, _pose(r), 0.3),
              stages={"windup": [(0.5, _mix(_pose(r), bowed, 0.7)), (1.0, bowed)],
                      "active": [(0.18, bowed), (0.34, one), (0.52, two), (0.7, three), (0.84, four), (1.0, tilt)],
                      "recover": _back(r, _mix(tilt, _pose(r), 0.3))})
    return mv


def _spin(r, mv, bone, axis, turns, t0, t1):
    """Turns a bone round whole turns on one axis between t0 and t1, eased, on top of its keys (a key a tick)."""
    c = r.clips[core.clip_name(mv)]
    old = c.tracks.get((bone, "rot"))
    keys = []
    for k in range(int(round(c.length / core.TICK)) + 1):
        t = min(c.length, k * core.TICK)
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        f = 0.0 if t <= t0 else 1.0 if t >= t1 else core.ease_io((t - t0) / (t1 - t0))
        base[axis] += 360.0 * turns * f
        keys.append((t, tuple(base), "cat"))
    c.tracks[(bone, "rot")] = keys


def moves(r):
    return [cut(r), cut_b(r), lunge(r), parry(r), riposte(r), behind(r), hall(r), through(r), shatter(r), preen(r)]


def clips(r):
    plans.biped_locomotion(r, P, weight=1.0, temper="regal")
    idle(r)
    gaits(r)
    reactions(r)
    mv = moves(r)
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    # Hall of Mirrors: it turns once round on its toes through the active stage, on its own track
    m = by["hall"]
    W, A = m.seconds("windup"), m.seconds("active")
    _spin(r, m, "pelvis", 1, 1, W + 0.1 * A, W + 0.95 * A)
    # Shatter: the five mirrors burst as its arms fling wide, and grow back out of their frames as it recovers
    m = by["shatter"]
    nm = core.clip_name(m)
    W = m.seconds("windup")
    for k, g in enumerate(MIRRORS):
        _scales(r, nm, g, [(W - 0.02, 1.0), (W + 0.04, 1.2), (W + 0.09, 0.0), (_staged(m, "recover", 0.2 + 0.1 * k), 0.0),
                           (_staged(m, "recover", 0.55 + 0.1 * k), 1.0)])
    # its hands, mirrors and blade stay out of the floor between the keys; kneeling and dying, its feet too
    held = [([h] + ([g for g, (hh, _) in MIRRORS.items() if hh == h]) + (["blade"] if h == "hand_r" else []),
             [(h, 0, 50), ("forearm" + h[4:], 0, 30)]) for h in HANDS]
    core.ground_pass(r, held, clips=[n for n in r.clips if n != "death"])
    feet = [(["foot_" + s], [("foot_" + s, 0, 50), ("shin_" + s, 0, 30)]) for s in "lr"]
    core.ground_pass(r, feet, clips=["kneel", "rise", "run", "walk"])
    face = [(["head", "glass_l"], [("head", 0, 30, -1), ("neck", 0, 30, -1)])]
    core.ground_pass(r, held + feet + face, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): glass and amethyst, an illusioner's mirror trick and a blade's sweep; chosen on paper, not
# heard in game.
SOUNDS = {
    "tell_light": [("block.amethyst_block.chime", 1.4, 0.7), ("block.glass.step", 1.6, 0.6)],
    "tell_heavy": [("entity.illusioner.prepare_mirror", 1.2, 0.8), ("block.amethyst_block.resonate", 1.3, 0.7)],
    "tell_perilous": [("entity.illusioner.prepare_mirror", 0.8, 1.0), ("block.amethyst_block.resonate", 0.7, 0.9)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.7), ("block.amethyst_block.hit", 1.6, 0.6)],
    "swing_heavy": [("entity.player.attack.strong", 1.2, 0.8), ("block.amethyst_cluster.hit", 1.2, 0.7)],
    "cast": [("entity.illusioner.mirror_move", 1.0, 0.9), ("block.amethyst_block.chime", 1.2, 0.8)],
    "impact": [("block.glass.break", 1.2, 0.8), ("block.amethyst_cluster.break", 1.0, 0.8)],
    "roar": [("block.glass.break", 0.8, 1.0), ("entity.illusioner.cast_spell", 0.9, 0.8)],
    "step": [("block.amethyst_block.step", 1.4, 0.5), ("block.glass.step", 1.2, 0.4)],
}
MOVE_SOUNDS = {"parry": {"swing": "cast", "impact": ""}, "hall": {"swing": "cast", "impact": ""}, "through": {"swing": "cast", "impact": ""},
               "shatter": {"swing": "roar"}, "preen": {"swing": "cast", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "preen"},  # it raises its mirrors round its face, one after another
    "phases": [
        {"pool": [
            {"move": "cut", "weight": 3, "when": "near", "then": [["cut_b"], ["cut_b", "lunge"]]},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "lunge", "weight": 3, "then": [["cut"]]},
            {"move": "behind", "weight": 3, "when": "behind"},
        ]},
        # a hall of you, as in DeepBestiary: the reflections, and it steps through its own glass
        {"transition": "hall", "pool": [
            {"move": "cut", "weight": 3, "when": "near", "then": [["cut_b"], ["cut_b", "lunge"]]},
            {"move": "through", "weight": 2, "when": "far", "then": [["cut", "cut_b"]]},
            {"move": "lunge", "weight": 3, "then": [["cut"]]},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "behind", "weight": 3, "when": "behind"},
        ]},
        # shatter
        {"transition": "shatter", "pool": [
            {"move": "shatter", "weight": 2},
            {"move": "hall", "weight": 1},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte", "cut_b"]]},
            {"move": "cut", "weight": 3, "when": "near", "then": [["cut_b", "lunge"]]},
            {"move": "through", "weight": 2, "when": "far", "then": [["cut"]]},
            {"move": "behind", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.6, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
