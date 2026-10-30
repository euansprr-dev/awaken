"""The Sovereign of Hunger (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's first Sovereign, a Beast. The
Gorged King, picked from its silhouette round 2026-10-29: "It ate the direction it came from."

A king who ate his court. He crawls on his knuckles now, far too heavy to stand: his crimson robe trimmed with ermine
rides up over a hunched back, and under it hangs a belly so swollen the robe has split open over it, pale and veined
and stretched shiny. Across the front of the belly runs a second mouth, its lips lined with yellow teeth, red light in
the gullet behind them. His arms are long and starved, grey to the bone, rings on every finger; his short legs drag
his train behind him. His own head is small and starved too, its cheeks sunk and its eyes two red sparks in the
dark of their sockets, and his great gold crown has slipped down over one of them.

Moves (SPEC §35.11): he slaps with one long arm, then the other, his ringed fingers spread (LIGHT); Devour (HEAVY, a
gap closer: he rocks back onto his legs, arms wide and the belly gaping, and falls on you belly first, the mouth
snapping shut); a long arm swung round behind him at whoever is there; Gnawing Pull (the belly mouth gapes at you
and he breathes in, swelling, and gnaws at the air); Hounds of the Court (he sits back on his haunches and claps for
his hounds, and roars); Gorge (HEAVY: he rears up and drops the belly mouth on the floor in front of him and bites a
piece out of it, worrying it like a dog); Starving Frenzy (he claws at the air and snaps, left and right); and Famine
(the signature: he folds over his belly and breathes in until it caves in to his ribs, then throws his arms wide and
howls the famine out of it). Broken, he slumps onto his belly; that is how he waits for you too, chewing his
knuckles. He dies on his belly, his crown rolling forward off his head.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "sovereign_hunger"
SCALE = 1.8  # the def's scale; the body is modelled at true size and the hitbox set round its belly and back

# The Gorged King (docs/bosses/ROLLOUT.md, A). His crown's points 3.9 blocks up, his back's hump 3.4, the belly 2.5
# blocks across and hanging 0.8 above the floor; his knuckles 1.8 blocks ahead of his middle. Value story: the gullet's
# red light and the crown's gold brightest, then the ermine and the teeth, the belly's pale stretched skin, the crimson
# robe, and his grey starved arms and face darkest.
HIP = 24.0  # the hips' pivot (pixels above the floor)
HIPZ = 12.0  # and how far behind his middle
SIDE = (1, -1, 1, -1)  # limbs: 0 left arm (+x), 1 right arm, 2 left leg, 3 right leg
ARMS, LEGS = (0, 1), (2, 3)
NM = ("l", "r", "l", "r")
LIMB = {0: ("arm_l", "fore_l", "hand_l"), 1: ("arm_r", "fore_r", "hand_r"), 2: ("thigh_l", "shin_l", "foot_l"), 3: ("thigh_r", "shin_r", "foot_r")}
# the wrists and ankles: the knuckles and soles a hair over the floor (the ground pass takes 0.2 px under it as in it)
REST_FOOT = {0: (19.0, 6.3, -26.0), 1: (-19.0, 6.3, -26.0), 2: (12.0, 3.3, 16.0), 3: (-12.0, 3.3, 16.0)}
POLE = {0: (0.5, 0.0, 1.0), 1: (-0.5, 0.0, 1.0), 2: (0.2, 0.0, -1.0), 3: (-0.2, 0.0, -1.0)}  # elbows back and out, knees forward
MOUTH = 22.0  # the belly mouth's line, pixels above the floor
TEETH_UP = (-13.0, -8.5, -4.0, 0.5, 5.0, 9.5, 13.5)
TEETH_LO = (-11.0, -6.5, -2.0, 2.5, 7.0, 11.5)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    hp = _bone(base, "hips", 0, HIP, HIPZ)
    # his hips in crimson breeches, the hunched back under the robe, the ermine collar round his thin neck
    _box(hp, "pelvis", 0, -8, 1, 24, 16, 14)
    ch = _bone(hp, "chest", 0, 10, -12)
    _box(ch, "back", 0, -3, -2, 28, 19, 20)
    _box(ch, "hump", 0, 16, -1, 22, 4, 16)
    _box(ch, "collar", 0, 12, -9, 32, 5, 12)  # the ermine capelet over his shoulders
    _box(ch, "chain", 0, 5, -14.5, 18, 2, 1)  # his chain of office across his breast, over the belly's top
    _box(ch, "medal", 0, 2, -15, 4, 3, 1)
    # the belly on its own bone, swelling and sagging: split open over it the robe; the mouth across its front, its
    # lower lip on a hinge at the back so the front drops open; the teeth along both lips; the red gullet inside
    gt = _bone(ch, "gut", 0, -4, -6)
    _box(gt, "belly", 0, MOUTH - 30, -5, 36, 14, 32)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(gt, "flank_" + nm, s * 19, MOUTH - 30 + 1, -5, 2, 11, 26)
    _box(gt, "gullet", 0, MOUTH - 30 - 2, -7, 28, 2, 22)
    for i, x in enumerate(TEETH_UP):
        _box(gt, f"tup{i}", x, MOUTH - 30 - 2.5, -21.5, 2, 3 if i % 2 else 2, 2)
    mw = _bone(gt, "maw", 0, MOUTH - 30, 9)
    _box(mw, "maw", 0, -8, -12, 34, 8, 26)
    _box(mw, "paunch", 0, -9, -11, 28, 1, 20)
    _box(mw, "tongue", 0, -1, -9, 14, 2, 16)
    for i, x in enumerate(TEETH_LO):
        _box(mw, f"tlo{i}", x, 0, -25.5, 2, 3 if i % 2 == 0 else 2, 2)
    # the train behind him, three links lying back along the floor
    tr = _bone(hp, "tr0", 0, -6, 8, (-40, 0, 0))
    _box(tr, "tr0", 0, 0, 6, 20, 2, 12)
    tr = _bone(tr, "tr1", 0, 0, 12, (-14, 0, 0))
    _box(tr, "tr1", 0, 0, 6, 22, 2, 12)
    tr = _bone(tr, "tr2", 0, 0, 12, (54, 0, 0))
    _box(tr, "tr2", 0, 0, 5, 24, 2, 10)
    # his small starved head low between his shoulders, the crown slipped down over his right eye
    nk = _bone(ch, "neck", 0, 12, -14)
    _box(nk, "neckc", 0, -2, -2, 5, 6, 5)
    hd = _bone(nk, "head", 0, 2, -4)
    _box(hd, "skull", 0, -1, -4.5, 10, 10, 9)
    _box(hd, "chin", 0, -3, -6.5, 6, 2, 5)
    _box(hd, "nose", 0, 2, -9.5, 2, 3, 1)
    _box(hd, "beard", 0, -5, -7.5, 4, 2, 2)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(hd, "ear_" + nm, s * 5.5, 3, -4, 1, 3, 2)
    cr = _bone(hd, "crown", 0, 8, -4.5, (6, 0, -9))
    _box(cr, "crown", 0, 0, 0, 12, 3, 11)
    for i, (x, z) in enumerate(((-4.5, -4.5), (0, -4.5), (4.5, -4.5), (-4.5, 4.5), (0, 4.5), (4.5, 4.5))):
        _box(cr, f"cpt{i}", x, 3, z, 2, 3 if i in (1, 4) else 2, 2)
    _box(cr, "gem", 0, 0.5, -6, 2, 2, 1)
    # the long starved arms: a puffed crimson sleeve with an ermine cuff, the grey forearm, a gold bracelet, the big
    # knuckled hand, its ringed fingers folded back under the palm to walk on
    for k in ARMS:
        s, nm = SIDE[k], NM[k]
        a = _bone(ch, "arm_" + nm, s * 16, 8, -10)
        a.cube("sleeve_" + nm, -4, -2, -4, 8, 10, 8)
        a.cube("cuff_" + nm, -3.5, 8, -3.5, 7, 2, 7)
        a.cube("arm_" + nm, -2.5, 0, -2.5, 5, 22, 5)
        f = a.bone("fore_" + nm, (0, 22, 0))
        f.cube("fore_" + nm, -2, 0, -2, 4, 21, 4)
        f.cube("bangle_" + nm, -2.5, 15, -2.5, 5, 2, 5)
        h = f.bone("hand_" + nm, (0, 21, 0))
        h.cube("hand_" + nm, -3.5, 0, -3.5, 7, 6, 7)
        g = h.bone("fingers_" + nm, (0, 5, -3.5))
        for j, cx in enumerate((-3, -1.5, 0, 1.5)):
            g.cube(f"finger{j}_{nm}", cx, -1, 0, 1, 2, 6 if j in (1, 2) else 5)
        g.cube("thumb_" + nm, 2.5 if s > 0 else -3.5, -1.5, 1, 1, 2, 4)
    # the short heavy legs in breeches, the shins grey, the feet bare and splayed
    for k in LEGS:
        s, nm = SIDE[k], NM[k]
        t = _bone(hp, "thigh_" + nm, s * 11, -2, 2)
        t.cube("thigh_" + nm, -4, -1, -4, 8, 13, 8)
        sh = t.bone("shin_" + nm, (0, 12, 0))
        sh.cube("shin_" + nm, -3, 0, -3, 6, 11, 6)
        ft = sh.bone("foot_" + nm, (0, 11, 0))
        ft.cube("foot_" + nm, -4, 0, -7, 8, 3, 11)
    for k in range(4):
        ik.rest_limb(r, *LIMB[k], REST_FOOT[k], POLE[k])
    fr0 = core.frames(r, {})
    for k in range(4):  # the knuckles and the soles set flat on the floor at rest
        end = LIMB[k][2]
        r.by_name[end].rot = tuple(v + w for v, w in zip(r.by_name[end].rot, ik.orient(r, fr0, end, (0, 1, 0), (1, 0, 0))))
        fr0 = core.frames(r, {})
    r.look = [("head", 0.4, 0.3), ("neck", 0.25, 0.2)]
    r.shadow = 2.4
    r.size = 1.0
    # secondary parts (SPEC §35.10): the belly sways and jiggles under him, the lower lip after it, the train drags, the
    # crown rocks on his head
    r.springs = [("gut", 0.35, 1.1, 0.06), ("maw", 0.5, 1.6, 0.05), ("crown", 0.6, 2.2, 0.05)] + \
                [(f"tr{j}", 0.5, 1.2 + 0.3 * j, 0.1) for j in range(3)]
    r.guard = ["hand_l", "hand_r", "foot_l", "foot_r", "maw", "head"]  # bones, not cubes
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

ROBE = Ramp("#1c0406", "#2c070a", "#3e0c10", "#521218", "#681a20", "#80222a", "#982c34", "#b23a40", "#c84c50")
GOLD = Ramp("#3a2606", "#5a3e0c", "#7c5814", "#a07420", "#c49230", "#dcb048", "#efcc6c", "#fbe496")
ERM = Ramp("#8a8478", "#a8a294", "#c4beb0", "#dcd8cc", "#eceae0", "#f8f6f0")
SKIN = Ramp("#1e1a1a", "#2a2424", "#383030", "#463c3a", "#564a46", "#665652", "#76645e")
BELLY = Ramp("#4a3434", "#644644", "#7e5a56", "#987068", "#b0867c", "#c69c90", "#d8b2a4", "#e6c6b8")
VEIN = ("#7a5670", "#8c667c")
TOOTH = Ramp("#5a4a2a", "#7e6a3e", "#a08c56", "#bcaa72", "#d4c490", "#e8dcb0")
GULLET = ("#3a0406", "#7a0c10", "#c41c20", "#ff4a4a", "#ffb0a0")


def _veins(f, seed, n):
    """n thin veins wandering over a face, each forking once."""
    for k in range(n):
        h = R._h(*seed, "v", k)
        walk = [(h % f.w, 3 + (h >> 8) % max(1, f.h - 4), 1 if (h >> 16) & 1 else -1, True)]
        while walk:
            i, j, di, fork = walk.pop()
            for step in range(6 + (h >> 20) % 6):
                f.set(i, j, hexc(VEIN[(step // 3) % 2], 255))
                g = R._h(*seed, k, i, j)
                i += di
                if g % 3 == 0:
                    j += 1 if g & 8 else -1
                if step == 3 and fork:
                    walk.append((i, j + 1, -di, False))
                    fork = False


def paint(r, P_):
    """The robe crimson, worn darker in its folds, gold trim at its hems; the ermine white with black tails; the belly
    pale and shiny, purple veins under its skin and stretch marks across it, the robe split open over its top; the lips
    dark and wet, the teeth yellow, the gullet red and lit; the arms, legs and face grey, the cheeks and sockets sunk;
    the crown gold with a red stone, his rings gold."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    cloth = R.p_blotch("robe", scale=3, amount=0.6)
    for c in ("pelvis", "back", "hump") + tuple(of("sleeve_", "thigh_", "tr")):
        P_.paint(c, ROBE, 4.6, cloth, grad=1.1, rim=0.6, ao=0.9, noise=0.5, nscale=3)
    # the robe falls in folds: a darker crease every few pixels down its sides and back
    for c in ("back", "pelvis") + tuple(of("sleeve_", "thigh_")):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if (i + R._h(c, fname) % 5) % 5 == 0:
                    f.set(i, j, ROBE.at(2.4 if j % 7 else 2.0))
                elif (i + R._h(c, fname) % 5) % 5 == 1:
                    f.set(i, j, ROBE.at(5.6))
    # his crest on his back in gold thread: a crowned mouth
    f = P_.face("back", "south")
    cx = f.w // 2
    for i, j in f.each():
        dx = abs(i - cx + 0.5)
        if 3 <= j <= 5 and dx < 5 and (j > 3 or int(dx) % 2 == 0):
            f.set(i, j, GOLD.at(5.4))  # the crown
        elif 7 <= j <= 12 and (dx - 1.5) ** 2 / 16 + (j - 9.5) ** 2 / 6.25 <= 1.0:
            ring = (dx - 1.5) ** 2 / 16 + (j - 9.5) ** 2 / 6.25 > 0.45
            f.set(i, j, GOLD.at(4.8) if ring else (ROBE.at(1.0) if j != 9 or int(dx) % 2 else GOLD.at(6.4)))
    # the robe's gold hem and the chain of office
    for c in ("pelvis", "back") + tuple(of("tr2")):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                f.set(i, f.h - 1, GOLD.at(4.4 + (i % 3 == 0) * 1.6))
    for c in ("chain", "medal", "crown", "gem") + tuple(of("cpt", "bangle_")):
        P_.paint(c, GOLD, 5.0, None, grad=1.0, rim=1.2, ao=0.3, noise=0.3)
    for fname in ("north", "south", "east", "west", "top"):
        f = P_.face("chain", fname)
        for i, j in f.each():
            if (i + j) % 2:
                f.set(i, j, GOLD.at(2.6))  # the links
    # the ermine: white, a black tail every few pixels
    for c in ("collar",) + tuple(of("cuff_")):
        P_.paint(c, ERM, 4.6, None, grad=0.6, rim=0.8, ao=0.4, noise=0.4)
        for fname in SIDES + ("top",):
            f = P_.face(c, fname)
            for i, j in f.each():
                if j % 4 in (1, 2) and (i + 3 * (j // 4) + R._h(c, fname)) % 6 == 0:
                    f.set(i, j, hexc("#141012", 255))  # the ermine's black tails, in staggered rows
    # the starved grey of his arms, shins, feet and face
    for c in tuple(of("arm_", "fore_", "hand_", "finger", "thumb_", "shin_", "foot_")) + ("neckc", "skull", "chin", "nose", "ear_l", "ear_r"):
        P_.paint(c, SKIN, 4.0, None, grad=1.0, rim=0.6, ao=0.8, noise=0.5, nscale=2)
    for c in of("finger"):  # a gold ring on each
        for fname in SIDES + ("top",):
            f = P_.face(c, fname)
            for i, j in f.each():
                if f.h > 2 and j == 2 or f.w > 2 and i == 2:
                    f.set(i, j, GOLD.at(5.4))
    for c in ("beard",):
        P_.paint(c, ERM, 2.4, None, grad=0.6, rim=0.4)
    # the belly: pale and shiny, darker underneath; thin purple veins wandering over it, pale stretch marks across its
    # sides, a shine where it is stretched tightest; the robe split open over its top
    for c in ("belly", "flank_l", "flank_r", "maw", "paunch"):
        P_.paint(c, BELLY, 4.8, None, grad=1.3, rim=1.0, ao=0.8, noise=0.25, nscale=4)
        for fname in SIDES:
            f = P_.face(c, fname)
            if f.w < 6 or f.h < 4:
                continue
            _veins(f, (c, fname), max(1, f.w * f.h // 130))
            for k in range(f.w * f.h // 50):  # stretch marks: short pale dashes along the belly's curve
                h = R._h(c, fname, "s", k)
                i, j = h % f.w, (h >> 8) % f.h
                if j > 2:
                    for d in range(2 + (h >> 16) % 3):
                        f.set(i + d, j, BELLY.at(6.6))
        if c == "belly":
            f = P_.face(c, "north")
            for i, j in f.each():  # the shine across the front, where the skin is stretched tightest
                if 2 <= j <= 4 and 6 <= i <= f.w - 7 and (i + j) % 3:
                    f.set(i, j, BELLY.at(7))
    f = P_.face("belly", "top")
    for i, j in f.each():  # the robe over its top, split open down the middle
        if abs(i - f.w / 2 + 0.5) > 5 + j * 0.15:
            f.set(i, j, ROBE.at(3.6 + (R._h("bt", i, j) % 3) * 0.4) if abs(i - f.w / 2 + 0.5) > 6 + j * 0.15 else GOLD.at(5.0))
    for fname in ("east", "west"):
        f = P_.face("belly", fname)
        for i, j in f.each():
            if j < 4:
                f.set(i, j, ROBE.at(3.4) if j < 3 else GOLD.at(4.6))
    # the mouth: dark wet lips along the line, the gullet red and lit inside
    f = P_.face("belly", "north")
    for i in range(f.w):
        f.set(i, f.h - 1, hexc("#2a1416", 255))
        f.set(i, f.h - 2, BELLY.at(2.2))
    f = P_.face("maw", "north")
    for i in range(f.w):
        f.set(i, 0, hexc("#2a1416", 255))
        f.set(i, 1, BELLY.at(2.4))
    for c, fname in (("gullet", "bottom"), ("gullet", "north"), ("maw", "top"), ("belly", "bottom")):
        f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
        for i, j in f.each():
            d = min(i, f.w - 1 - i, j, f.h - 1 - j)
            f.set(i, j, hexc(GULLET[min(3, 1 + d // 2)], 255))
            if c == "gullet" or d > 1:
                g.set(i, j, hexc(GULLET[3 if d > 2 else 2], 150 + min(100, 30 * d)))
    # the tongue, dark and wet, a groove down its middle; ridges across the roof of the belly mouth
    P_.paint("tongue", Ramp("#3a0a10", "#5a121a", "#7a1c26", "#9a2a34", "#b43c46", "#c85660"), 3.4, None, grad=1.0, rim=0.8, ao=0.6, noise=0.4)
    f = P_.face("tongue", "top")
    for j in range(2, f.h):
        f.set(f.w // 2, j, hexc("#3a0a10", 255))
    f = P_.face("belly", "bottom")
    for i, j in f.each():
        if j % 3 == 0 and 2 < i < f.w - 3 and 2 < j < f.h - 3:
            f.set(i, j, hexc(GULLET[1], 255))
    for c in of("tup", "tlo"):
        P_.paint(c, TOOTH, 4.4, None, grad=1.2, rim=0.8, ao=0.3, noise=0.3)
    # the face: sunk cheeks, the brow heavy, two red sparks in the dark sockets, the thin mouth
    f = P_.face("skull", "north")
    for i, j in f.each():
        if j in (3, 4) and i in (1, 2, 3, 6, 7, 8):
            f.set(i, j, hexc("#0a0606", 255))
        elif j in (5, 6) and i in (1, 8):
            f.set(i, j, SKIN.at(1.6))  # the sunk cheeks
        elif j == 8 and 2 <= i <= 7:
            f.set(i, j, hexc("#140a0a", 255))
        elif j == 2:
            f.set(i, j, SKIN.at(5.4))  # the brow's ridge
    with P_.eyes():
        f, g = P_.face("skull", "north"), P_.face("skull", "north", glow=True)
        for i in (2, 7):
            f.set(i, 4, hexc("#ff3a30", 255))
            g.set(i, 4, hexc("#ff4a4a", 255))
    f, g = P_.face("gem", "north"), P_.face("gem", "north", glow=True)
    for i, j in f.each():
        f.set(i, j, hexc("#c81c24", 255))
        g.set(i, j, hexc("#ff4a4a", 170))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the body set and bent, each hand and foot reaching where it stands, the belly mouth opened

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _flat(yaw):
    a = yaw * DEG
    return (math.cos(a), 0.0, -math.sin(a))


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), bend=0.0, side=0.0, twist=0.0, neck=(0, 0, 0), head=(0, 0, 0),
          gut=(0, 0, 0), sag=0.0, maw=0.0, fingers=(0.0, 0.0), feet=None, poles=None, hands=None, limbs=None, train=0.0, crown=(0, 0, 0), extra=None):
    """lift (+ up), pitch (+ his front dipping), yaw (+ to his left), roll (+ his left side down) and shift (x, z) move
    his hips; bend (+ his front dipping), side (+ bending to his left) and twist turn his chest on them; neck and head
    add their own turns; gut swings the belly and sag lets it down (pixels); maw opens the belly mouth (degrees);
    fingers (left, right) spread the fingers out of the fist (1 spread to slap); feet {limb: (x, height, z)}: where the
    wrists and ankles go (the rest stand where they stand at rest); limbs {limb: {bone: turn}} sets a limb's turns
    outright; hands {limb: "free" | (down, across)}: how that hand or foot lies (flat on the floor if not given); train
    lifts the train off the floor (degrees); crown rocks the crown."""
    p = {"hips": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "chest": (bend, side, twist),
         "neck": tuple(neck), "head": tuple(head),
         "gut": {"rot": tuple(gut), "pos": (0, -sag, 0)},
         "maw": (maw, 0, 0),
         "crown": tuple(crown),
         "tr0": (train, 0, 0), "tr1": (train * 0.6, 0, 0), "tr2": (-train * 0.4, 0, 0)}
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    poles = poles or {}
    limbs = limbs or {}
    for k in range(4):
        if k in limbs:
            p.update({b: limbs[k].get(b, (0.0, 0.0, 0.0)) for b in LIMB[k][:2]})
        else:
            p.update(ik.limb(r, fr, *LIMB[k], ik.model(feet.get(k, REST_FOOT[k])), poles.get(k, POLE[k])))
    fr = core.frames(r, core.as_sample(p))
    hands = hands or {}
    flat = _flat(yaw)
    for k in range(4):
        end = LIMB[k][2]
        how = hands.get(k)
        if how == "free":
            p[end] = (0.0, 0.0, 0.0)
        else:
            down, across = how if how else ((0, 1, 0), flat)
            p[end] = ik.orient(r, fr, end, down, across)
    for k, a in zip(ARMS, fingers):
        p["fingers_" + NM[k]] = (-150.0 * a, 0, 0)
    if extra:
        p.update(extra)
    return p


# his legs when he lies on his belly: folded under him like a frog's, the knees out to the sides
LIE = {2: (17.0, 3.3, 15.0), 3: (-17.0, 3.3, 15.0)}
LIE_POLE = {2: (0.8, 0.0, -0.8), 3: (-0.8, 0.0, -0.8)}


def _foot(k, up=0.0, dz=0.0, dx=0.0):
    x, h, z = REST_FOOT[k]
    return (x + dx, h + up, z + dz)


def _plant(r, clip, legs, where=None):
    """The hands and feet that stand through a move keep where they stand on the floor, every tick, lying flat."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            out.update(ik.limb(r, fr, *LIMB[k], ik.model(where.get(k, REST_FOOT[k])), POLE[k]))
        s2 = dict(s)
        s2.update(core.as_sample(out))
        fr2 = core.frames(r, s2)
        for k in legs:
            end = LIMB[k][2]
            M = fr2[end][0]
            across = (M[0][0], 0.0, M[2][0])
            if abs(across[0]) + abs(across[2]) > 1e-3:
                out[end] = ik.orient(r, fr2, end, (0, 1, 0), across)
        return out
    ik.retrack(r, clip, fn)


def _with(a, b):
    return {**a, **b}


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


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _scale(r, clip, bone, pts, mode="cat"):
    r.clips[clip].scale(bone, [(t, v, v, v) for t, v in pts], mode)


def _track(mv, pts):
    """A move's scale keys [(stage, fraction, scale)] in its stages' time (a bare (0.0, scale) is its start)."""
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    span = {"windup": W, "active": A, "recover": R_}
    return sorted((0.0, k[1]) if len(k) == 2 else (t0[k[0]] + k[1] * span[k[0]], k[2]) for k in pts)


def _belly(r, gap=0.3, **kw):
    """The pose let down or lifted on his hips until his belly just rests on the floor, gap pixels over it (the hands
    and feet then reach for where the pose puts them)."""
    p = _pose(r, **kw)
    up = core.lowest(r, core.frames(r, core.as_sample(p)), ["gut", "maw"]) - (24.0 - gap)
    return _pose(r, **{**kw, "lift": kw.get("lift", 0.0) + up})


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """He crouches on his knuckles breathing hard, the belly heaving with each breath and swaying after it; the belly
    mouth chews on nothing, twice; his small head jerks about, looking for food, holding still between; the fingers of
    his right hand drum on the floor; he shifts his weight from one hand to the other."""
    L, n = 6.0, 90
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        br = math.sin(3 * w)  # three breaths in the loop
        chew = _wave(u, 0.18, 0.1) + _wave(u, 0.3, 0.1) + 0.6 * _wave(u, 0.72, 0.08)
        # the head looks one way, holds, snaps to another, holds: steps eased in short turns
        looks = ((0.0, 0, 0), (0.14, -24, 6), (0.36, 18, -4), (0.52, 4, 10), (0.7, -10, -2), (0.86, 22, 4), (1.0, 0, 0))
        for a, b in zip(looks, looks[1:]):
            if a[0] <= u <= b[0]:
                f = _ease(min(1.0, (u - a[0]) / 0.04))
                hy, hx = a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f
                break
        drum = 0.35 * max(0.0, math.sin(2 * math.pi * 7 * u)) * _wave(u, 0.6, 0.26)
        shift = math.sin(w + 0.6)
        keys.append((L * u, _pose(r, lift=0.8 * br, pitch=-1.2 * br, roll=2.5 * shift, yaw=1.0 * math.sin(w), bend=1.5 * br, side=-2.0 * shift,
                                  neck=(hx * 0.4, hy * 0.4, 0), head=(hx * 0.6 - 3 * chew, hy * 0.6, 2 * math.sin(2 * w)),
                                  gut=(1.5 * br, 0, -2.0 * shift), sag=-0.6 * br, maw=9 * chew, fingers=(0, drum),
                                  feet={0: _foot(0, dx=0.4 * shift)}, train=2 * math.sin(w + 1.0), crown=(0, 0, 1.5 * math.sin(2 * w + 0.5)))))
    keyed(r, "idle", L, keys, loop=True)
    # each breath swells the belly a little, the belly mouth's lips with it
    _scale(r, "idle", "gut", [(L * k / 36, 1.0 + 0.025 * math.sin(3 * 2 * math.pi * k / 36)) for k in range(37)])


WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # his walk: each limb by turns, the left leg, the left hand, the right leg, the right hand
LOPE = {0: 0.0, 1: 0.1, 2: 0.5, 3: 0.6}  # his run: both hands thrown forward together, then both legs after them


def _gait(u, stride_px, beta, lift, groups):
    """The hands and feet through a stride at u: down, each moves back as far as it travels, so it stays put on the
    floor; up, it lifts and reaches forward again."""
    feet = {}
    st = stride_px * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * (1.3 if k in ARMS else 0.7) * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
    return feet


def _locomotion(r):
    """His walk: a heavy knuckle-walk, one limb at a time, each long arm swinging out and round and planting its fist,
    the belly swinging from side to side under him and the head bobbing low, the train dragging. Running he lopes,
    both arms thrown forward together and the legs hopping after them, the belly bouncing."""
    for name, L, stride, beta, lift, groups, bob in (("walk", 1.5, 1.2, 0.7, 6.0, WALK, 0.0), ("run", 0.8, 2.2, 0.5, 9.0, LOPE, 1.0)):
        sp = stride * 16
        keys = []
        n = 30
        for k in range(n + 1):
            u = k / n
            s = math.sin(2 * math.pi * u)
            s2 = math.sin(4 * math.pi * u)
            c1 = math.cos(2 * math.pi * u)
            if bob:  # the lope: up as the arms reach, down as they land
                keys.append((L * u, _pose(r, lift=2.0 * c1, pitch=3.0 * math.sin(2 * math.pi * u + 0.6), bend=2.0 * c1, neck=(-3, 0, 0), head=(4 + 3 * c1, 0, 0),
                                          gut=(-6 * math.sin(2 * math.pi * u - 0.8), 0, 0), sag=1.5 * math.sin(2 * math.pi * u - 1.0),
                                          maw=4 + 4 * max(0.0, c1), feet=_gait(u, sp, beta, lift, groups), train=6 + 4 * s, crown=(3 * c1, 0, -2))))
            else:
                keys.append((L * u, _pose(r, lift=-0.6 * (1 + math.cos(4 * math.pi * u)), roll=3.0 * s, yaw=2.5 * s, side=-4.0 * s, twist=3.0 * s,
                                          neck=(-2, 3 * s, 0), head=(3 + 2 * s2, 6 * s, -3 * s), gut=(1.5 * s2, 0, -6 * s), sag=0.8 * (1 + s2),
                                          feet=_gait(u, sp, beta, lift, groups), train=1.5 * s2, crown=(0, 0, 2.5 * s))))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.2, 2.2
    r.run_speed = (0.12, 0.28)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        A = 16.0 * DEG
        for k in range(n + 1):
            u = k / n
            feet = {}
            for j, ph in WALK.items():  # each limb steps round the way he turns while the others hold
                q = (u - ph) % 1.0
                if q < 0.7:
                    a = sgn * A * (0.5 - q / 0.7)
                    up_ = 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a = sgn * A * (-0.5 + core.ease_io(f))
                    up_ = (7.0 if j in ARMS else 4.0) * math.sin(math.pi * f)
                x, h, z = REST_FOOT[j]
                feet[j] = (x * math.cos(a) + z * math.sin(a), h + up_, -x * math.sin(a) + z * math.cos(a))
            keys.append((0.9 * u, _pose(r, yaw=sgn * 2.0, side=sgn * 4.0, roll=1.5 * math.sin(4 * math.pi * u), head=(4, sgn * 16, 0), gut=(0, 0, -sgn * 4),
                                        feet=feet)))
        keyed(r, nm, 0.9, keys, loop=True)


def _slumped(r, chew=0.0):
    """Down on his belly, his arms lying out in front of him on the floor, his head low on them."""
    feet = {0: (22.0, 3.0, -34.0), 1: (-22.0, 3.0, -34.0), 2: LIE[2], 3: LIE[3]}
    poles = {0: (0.8, -0.3, 0.6), 1: (-0.8, -0.3, 0.6), **LIE_POLE}
    return _belly(r, pitch=4, bend=4, neck=(10, 0, 0), head=(14, 0, 6), gut=(-4, 0, 0), maw=4 * chew, feet=feet, poles=poles,
              hands={0: ((0, 1, 0), (1, 0, 0)), 1: ((0, 1, 0), (1, 0, 0))}, train=-2, crown=(4, 0, -4))


def _reactions(r):
    """Struck, he flinches back on his legs, the belly jiggling and the head ducked; from the side he sways away. His
    stance broken, his arms slide out from under him and he flops onto his belly; he rises by pushing himself up."""
    hit = _pose(r, lift=1.5, pitch=-6, bend=-4, neck=(8, 0, 0), head=(12, 0, 0), gut=(-8, 0, 0), sag=1.5, maw=14, crown=(-6, 0, 3))
    back = _pose(r, lift=-0.8, pitch=2, bend=2, head=(-3, 0, 0), gut=(4, 0, 0), sag=-0.5, maw=3)
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from his left he sways away to his right
        h = _pose(r, lift=1, roll=-7 * sgn, yaw=-6 * sgn, side=-8 * sgn, shift=(-sgn * 1.5, 0), head=(6, -14 * sgn, 0), gut=(0, 0, 10 * sgn), maw=10,
                  crown=(0, 0, 6 * sgn))
        b = _pose(r, roll=2 * sgn, side=3 * sgn, head=(2, 4 * sgn, 0), gut=(0, 0, -4 * sgn))
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    give = _pose(r, lift=-6, pitch=6, bend=3, neck=(6, 0, 0), head=(6, 0, 0), gut=(4, 0, 0), sag=2, maw=10,
                 feet={0: _foot(0, dz=-5, dx=3), 1: _foot(1, dz=-5, dx=-3)})
    flop = _belly(r, pitch=2, bend=2, neck=(6, 0, 0), head=(8, 0, 0), gut=(-6, 0, 0), maw=14,
                              feet={0: (24.0, 3.5, -30.0), 1: (-24.0, 3.5, -30.0)}, poles={0: (0.8, -0.3, 0.6), 1: (-0.8, -0.3, 0.6)},
                              hands={0: ((0, 1, 0), (1, 0, 0)), 1: ((0, 1, 0), (1, 0, 0))}, gap=1.2)
    keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.25, give), (0.5, flop), (0.68, _slumped(r, 0.4)), (0.9, _slumped(r))])
    push = _pose(r, lift=-6, pitch=6, bend=5, neck=(-4, 0, 0), head=(-6, 0, 0), gut=(6, 0, 0), sag=2, maw=6,
                 feet={0: _foot(0, dz=-3), 1: _foot(1, dz=-3)})
    keyed(r, "rise", 1.1, [(0, _slumped(r)), (0.35, push), (0.75, _pose(r, lift=1.5, pitch=-2, head=(-4, 0, 0), gut=(-6, 0, 0), sag=-1)), (1.1, _pose(r))])
    _death(r)


def _death(r):
    """His last: he rears back on his legs clutching his belly, the belly mouth gaping and his head thrown back; he
    sways; he falls forward onto his belly and it spreads under him; his arms reach out ahead of him along the floor;
    his crown rolls forward off his head; the belly mouth opens once more, slowly, and closes, and he is still."""
    clutch = {0: (8.0, 22.0, -28.0), 1: (-8.0, 22.0, -28.0)}
    rear = _pose(r, lift=4, pitch=-26, bend=-8, neck=(-14, 0, 0), head=(-24, 0, 0), gut=(-6, 0, 0), maw=40, feet=clutch,
                 poles={0: (0.6, -0.2, 1), 1: (-0.6, -0.2, 1)}, hands={0: "free", 1: "free"}, train=-4, crown=(-14, 0, 0))
    sway = _pose(r, lift=3, pitch=-20, roll=8, bend=-6, side=6, neck=(-6, 8, 0), head=(-12, 10, 0), gut=(-4, 0, 6), maw=30, feet=clutch,
                 poles={0: (0.6, -0.2, 1), 1: (-0.6, -0.2, 1)}, hands={0: "free", 1: "free"}, crown=(-8, 0, 10))
    fall = _pose(r, lift=-2, pitch=10, roll=3, bend=6, neck=(4, 0, 0), head=(6, 0, 0), gut=(8, 0, 0), maw=24,
                 feet={0: (20.0, 16.0, -36.0), 1: (-20.0, 16.0, -36.0)}, poles={0: (0.6, -0.2, 1), 1: (-0.6, -0.2, 1)}, hands={0: "free", 1: "free"})
    reach = {0: (24.0, 3.0, -40.0), 1: (-22.0, 3.0, -38.0)}
    flat = {0: ((0, 1, 0), (1, 0, 0)), 1: ((0, 1, 0), (1, 0, 0))}
    down = lambda m, cr, sag_=0.0, gap=0.3: _belly(r, pitch=5, roll=2, bend=5, neck=(10, 0, 0), head=(14, -6, 8), gut=(-6, 0, 0), sag=sag_, maw=m,
                                                               feet={**reach, 2: LIE[2], 3: LIE[3]},
                                                               poles={0: (0.9, -0.3, 0.5), 1: (-0.9, -0.3, 0.5), **LIE_POLE}, hands=flat, train=-3, crown=cr, gap=gap)
    keys = [(0, _pose(r)), (0.4, rear), (0.95, sway), (1.35, fall), (1.6, down(6, (10, 0, -6), gap=0.3)), (1.75, down(10, (16, 0, -8), gap=1.4)),
            (1.95, down(4, (20, 0, -10))), (2.6, down(2, (60, 0, -14))), (3.0, down(2, (92, 0, -12))), (3.7, down(18, (92, 0, -12))),
            (4.3, down(2, (92, 0, -12))), (5.0, down(0, (92, 0, -12), sag_=0.5))]
    keyed(r, "death", 5.0, keys)
    _scale(r, "death", "gut", [(0, 1.0), (1.6, 1.0), (1.75, 0.96), (2.6, 0.94), (5.0, 0.93)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b: 0 for k in range(4) for b in LIMB[k]}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _fi(k, a):
    return (a, 0.0) if k == 0 else (0.0, a)


def slap(r, k=1, mid="slap"):
    """One long arm lifted high and out to his side and back, the ringed fingers spread, as he leans onto his other hand
    and turns away; then he swings it round and down across in front of him, the open hand raking the floor, his body
    swinging after it and the belly swinging out the other way (LIGHT)."""
    s = SIDE[k]
    free = {k: "free"}
    gather = _pose(r, lift=0.5, pitch=-2, yaw=6 * s, roll=-4 * s, side=6 * s, twist=6 * s, neck=(-2, -4 * s, 0), head=(-4, -10 * s, 0), gut=(0, 0, -6 * s), maw=6,
                   feet={k: _foot(k, up=12, dz=2, dx=6 * s)}, poles={k: (s * 0.9, -0.2, 0.6)}, fingers=_fi(k, 0.6), hands=free)
    rear = _pose(r, lift=-1, pitch=2, yaw=10 * s, roll=-6 * s, side=9 * s, twist=12 * s, neck=(-6, -6 * s, 0), head=(-8, -14 * s, 0), gut=(0, 0, -9 * s), maw=12,
                 feet={k: (s * 32, 36, -10)}, poles={k: (s * 1, 0.1, 0.6)}, fingers=_fi(k, 1.0), hands=free, crown=(0, 0, 4 * s))
    # half way round, he lunges after the arm, low and forward
    swing = _pose(r, lift=-3, pitch=6, yaw=-3 * s, roll=1 * s, side=-2 * s, twist=-3 * s, neck=(-4, 0, 0), head=(-2, 4 * s, 0), gut=(4, 0, 4 * s), sag=1, maw=10,
                  feet={k: (s * 16, 22, -42)}, poles={k: (s * 0.9, 0.0, 0.8)}, fingers=_fi(k, 1.0), hands=free)
    hit = _pose(r, lift=-1.5, pitch=3, yaw=-9 * s, roll=3 * s, side=-7 * s, twist=-10 * s, neck=(-2, 4 * s, 0), head=(-2, 10 * s, 0), gut=(0, 0, 10 * s), maw=8,
                feet={k: (-s * 6, 7, -36)}, poles={k: (s * 0.6, -0.3, 1)}, fingers=_fi(k, 1.0), hands=free, crown=(0, 0, -4 * s))
    held = _pose(r, lift=-2, pitch=4, yaw=-11 * s, roll=3.5 * s, side=-8 * s, twist=-12 * s, neck=(-1, 4 * s, 0), head=(0, 11 * s, 0), gut=(0, 0, 12 * s), maw=6,
                 feet={k: (-s * 10, 6.5, -32)}, poles={k: (s * 0.6, -0.3, 1)}, fingers=_fi(k, 0.9), hands=free, crown=(0, 0, -5 * s))
    through = _pose(r, lift=-1.5, pitch=3, yaw=-8 * s, roll=2.5 * s, side=-6 * s, twist=-9 * s, neck=(0, 3 * s, 0), head=(0, 8 * s, 0), gut=(0, 0, 8 * s), maw=4,
                    feet={k: (-s * 8, 8, -28)}, poles={k: (s * 0.6, -0.3, 1)}, fingers=_fi(k, 0.6), hands=free)
    back = _pose(r, lift=-0.5, yaw=-3 * s, side=-2 * s, gut=(0, 0, 3 * s), feet={k: _foot(k, up=6, dz=-4, dx=-s)}, poles={k: (s * 0.6, -0.3, 1)},
                 fingers=_fi(k, 0.25), hands=free)
    return Move(mid, "LIGHT", 10, 4, 14, through=through, track=8.0, rng=(0, 3.8), kinds=("combo", "near"), damage=1.0, knock=0.6,
                hit=[("fingers_" + NM[k], (0, 0, -6), 3.0), ("hand_" + NM[k], (0, 3, 0), 3.5)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.25, swing), (0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def sweep(r):
    """For whoever is behind him (LIGHT): he twists round to look back over his right shoulder, his right arm drawn up
    and back over his hump; then he swings it round behind him, low across the floor, his body unwinding after it."""
    free = {1: "free"}
    look = _pose(r, lift=1, yaw=-8, roll=2, side=-6, twist=-8, neck=(-4, -28, 0), head=(-6, -44, 0), gut=(0, 0, 6), maw=8,
                 feet={1: _foot(1, up=12, dz=8, dx=-6)}, poles={1: (-1, -0.2, -0.2)}, fingers=(0, 0.5), hands=free)
    wind = _pose(r, lift=2, yaw=-24, roll=3, side=-14, twist=-18, neck=(-6, -30, 0), head=(-8, -48, 0), gut=(0, 0, 10), maw=12,
                 feet={1: (-26.0, 38.0, 10.0)}, poles={1: (-1, 0.2, -0.4)}, fingers=(0, 1.0), hands=free, crown=(0, 0, -5))
    hit = _pose(r, lift=-1, yaw=10, roll=-3, side=8, twist=14, neck=(-2, -18, 0), head=(-2, -30, 0), gut=(0, 0, -10), maw=8,
                feet={1: (-10.0, 8.0, 38.0)}, poles={1: (-0.6, -0.2, -1)}, fingers=(0, 1.0), hands=free, crown=(0, 0, 4))
    held = _pose(r, lift=-1.5, yaw=16, roll=-4, side=11, twist=18, neck=(0, -14, 0), head=(0, -24, 0), gut=(0, 0, -12), maw=6,
                 feet={1: (8.0, 7.0, 36.0)}, poles={1: (-0.2, -0.2, -1)}, fingers=(0, 0.9), hands=free, crown=(0, 0, 5))
    side_ = _pose(r, yaw=9, roll=-2, side=6, twist=9, head=(0, -14, 0), gut=(0, 0, -7), feet={1: (-30.0, 16.0, 10.0)}, poles={1: (-0.8, 0.2, -0.6)},
                  fingers=(0, 0.6), hands=free)
    back = _pose(r, yaw=4, side=3, twist=4, head=(0, -8, 0), gut=(0, 0, -3), feet={1: _foot(1, up=7, dz=-2, dx=-4)}, fingers=(0, 0.2), hands=free)
    return Move("sweep", "LIGHT", 10, 4, 14, through=held, track=0.0, rng=(0, 3.5), kinds=("behind",), damage=0.9, knock=0.9,
                hit=[("fingers_r", (0, 0, -6), 3.0), ("hand_r", (0, 3, 0), 3.5)], plant=(),
                stages={"windup": [(0.3, look), (1.0, wind)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.3, side_), (0.62, back)]}, lag=_lag())


ADVANCE = 7.0  # blocks Devour carries him


def devour(r):
    """Devour (HEAVY, a gap closer): he crouches, then rocks back onto his legs, his front rising, his arms flung wide,
    the belly mouth gaping as wide as it goes and his head thrown back; holds; then he throws himself forward at you and
    falls belly first, his arms wrapping in, and the mouth snaps shut as he lands; he chews, and pushes himself up."""
    crouch = _pose(r, lift=-3, pitch=6, bend=3, neck=(4, 0, 0), head=(6, 0, 0), gut=(4, 0, 0), sag=1, maw=8, feet={0: _foot(0, dz=4), 1: _foot(1, dz=4)})
    wide = {0: (30.0, 40.0, -16.0), 1: (-30.0, 40.0, -16.0)}
    up_ = {0: (1, 0.2, 0.6), 1: (-1, 0.2, 0.6)}
    both = {0: "free", 1: "free"}
    rear = _pose(r, lift=3, pitch=-38, bend=-8, neck=(-12, 0, 0), head=(-22, 0, 0), gut=(-10, 0, 0), maw=58, feet=wide, poles=up_, hands=both,
                 fingers=(1, 1), train=-6, crown=(-12, 0, -6))
    tuck = {2: (12.0, 14.0, 10.0), 3: (-12.0, 14.0, 10.0)}
    leap = _pose(r, lift=12, pitch=-8, bend=-2, neck=(-4, 0, 0), head=(-8, 0, 0), gut=(-6, 0, 0), maw=62, poles=up_, hands=both, fingers=(1, 1),
                 feet={0: (24.0, 34.0, -40.0), 1: (-24.0, 34.0, -40.0), **tuck}, train=10, crown=(-6, 0, -6))
    wrap = {0: (14.0, 8.0, -38.0), 1: (-14.0, 8.0, -38.0)}
    fwd = {0: (0.8, -0.2, 0.6), 1: (-0.8, -0.2, 0.6), **LIE_POLE}
    land = _belly(r, pitch=8, bend=4, neck=(6, 0, 0), head=(10, 0, 0), gut=(10, 0, 0), maw=4, feet={**wrap, 2: (LIE[2][0], LIE[2][1] + 3, LIE[2][2]), 3: (LIE[3][0], LIE[3][1] + 3, LIE[3][2])},
                              poles=fwd, hands=both, fingers=(0.6, 0.6), train=4, gap=0.6)
    bite = _belly(r, pitch=9, bend=5, neck=(8, 0, 0), head=(12, 0, 0), gut=(4, 0, 0), maw=0, feet={**wrap, **LIE},
                              poles=fwd, hands=both, fingers=(0.4, 0.4), gap=0.2)
    through = _belly(r, pitch=8, bend=4, neck=(6, 0, 0), head=(8, 0, 0), gut=(2, 0, 0), maw=10, feet={0: (16.0, 6.5, -34.0), 1: (-16.0, 6.5, -34.0), **LIE},
                                 poles=fwd, gap=0.3)
    push = _pose(r, lift=-7, pitch=7, bend=4, neck=(-2, 0, 0), head=(-2, 0, 0), gut=(4, 0, 0), sag=1.5, maw=4,
                 feet={0: _foot(0, dz=-4), 1: _foot(1, dz=-4), 2: _foot(2, dx=3), 3: _foot(3, dx=-3)}, poles=LIE_POLE)
    through = _mix(through, push, 0.3)  # already gathering himself to push up
    mv = Move("devour", "HEAVY", 18, 10, 24, through=through, title="Devour", hold=8, track=6.0, lock=12, advance=ADVANCE, ability="Devour", rng=(5, 14),
              kinds=("gap",), damage=1.4, knock=1.2, hit=[("gut", (0, -6, -18), 16.0)], plant=(),
              stages={"windup": [(0.3, crouch), (1.0, rear)], "active": [(0.35, leap), (0.75, land), (1.0, bite)], "recover": [(0.52, push), (0.74, _mix(push, _pose(r), 0.6))]},
              lag=_lag())
    mv._gut = [(0.0, 1.0), ("windup", 1.0, 1.06), ("active", 0.75, 0.96), ("active", 1.0, 1.02), ("recover", 0.6, 1.0)]
    return mv


def gorge(r):
    """Gorge (HEAVY): he rears up on his legs over the floor in front of him, the belly mouth gaping down at it; holds;
    then drops it on the floor and bites, and worries the bite from side to side like a dog with meat, the floor
    breaking round him; he pulls back up, chewing."""
    both = {0: "free", 1: "free"}
    up_ = {0: (1, 0.2, 0.6), 1: (-1, 0.2, 0.6)}
    crouch = _pose(r, lift=-5, pitch=7, bend=4, neck=(4, 0, 0), head=(8, 0, 0), gut=(6, 0, 0), sag=1, maw=10, feet={0: _foot(0, dz=4, dx=2), 1: _foot(1, dz=4, dx=-2)},
                   fingers=(0.3, 0.3))
    rising = _pose(r, lift=-1, pitch=-14, bend=-2, neck=(-2, 0, 0), head=(-4, 0, 0), gut=(10, 0, 0), maw=28, feet={0: (24.0, 20.0, -24.0), 1: (-24.0, 20.0, -24.0)},
                   poles=up_, hands=both, fingers=(0.6, 0.6), train=-2)
    rear = _pose(r, lift=2, pitch=-30, bend=-4, neck=(-6, 0, 0), head=(-10, 0, 0), gut=(14, 0, 0), maw=50, feet={0: (26.0, 32.0, -20.0), 1: (-26.0, 32.0, -20.0)},
                 poles=up_, hands=both, fingers=(0.8, 0.8), train=-4, crown=(-8, 0, 4))
    plant = {0: (28.0, 6.3, -30.0), 1: (-28.0, 6.3, -30.0)}
    wide = {0: (1, -0.2, 0.4), 1: (-1, -0.2, 0.4)}

    def worry(a, m):
        return _belly(r, pitch=12, roll=6 * a, yaw=5 * a, bend=8, side=-6 * a, twist=4 * a, neck=(6, 4 * a, 0), head=(8, 8 * a, -4 * a),
                                  gut=(10, 3 * a, 6 * a), maw=m, feet=plant, poles=wide, fingers=(0.5, 0.5), crown=(4, 0, 6 * a), gap=0.2)
    act = [(0.25, worry(0, 2)), (0.42, worry(1, 0)), (0.58, worry(-1, 0)), (0.74, worry(1, 0)), (0.88, worry(-0.6, 0)), (1.0, worry(0, 4))]
    through = worry(0, 8)
    pull = _pose(r, lift=-4, pitch=6, bend=3, neck=(-4, 0, 0), head=(-6, 0, 0), gut=(4, 0, 0), sag=1, maw=10, feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    mv = Move("gorge", "HEAVY", 16, 12, 20, through=through, title="Gorge", hold=6, track=4.0, ability="Gorge", rng=(0, 4.5), kinds=("area", "near"),
              damage=1.3, knock=1.3, disc=4.5, hit=[("gut", (0, -8, -14), 18.0)], plant=(),
              stages={"windup": [(0.3, crouch), (0.62, rising), (1.0, rear)], "active": act, "recover": [(0.45, pull), (0.75, _mix(pull, _pose(r), 0.7))]}, lag=_lag())
    mv._gut = [(0.0, 1.0), ("windup", 1.0, 1.04), ("active", 0.25, 0.95), ("active", 0.5, 1.0), ("active", 1.0, 0.98), ("recover", 0.6, 1.0)]
    return mv


def gnaw(r):
    """Gnawing Pull: he plants his hands wide and lowers his front, the belly mouth gaping at you, and breathes in: the
    belly swells and swells, his robe and his train blown toward him; then the mouth gnaws at the air, snapping."""
    wide = {0: (26.0, 6.3, -30.0), 1: (-26.0, 6.3, -30.0)}
    poles = {0: (1, -0.2, 0.6), 1: (-1, -0.2, 0.6)}
    low = _pose(r, lift=-4, pitch=5, bend=3, neck=(-6, 0, 0), head=(-10, 0, 0), gut=(-6, 0, 0), maw=20, feet=wide, poles=poles, fingers=(0.3, 0.3))
    gape = lambda f, m: _pose(r, lift=-4 + 2 * f, pitch=5 - 4 * f, bend=3 - 4 * f, neck=(-8 - 4 * f, 0, 0), head=(-12 - 6 * f, 0, 0), gut=(-8 - 4 * f, 0, 0),
                              maw=m, feet=wide, poles=poles, fingers=(0.3, 0.3), train=10 + 14 * f, crown=(-4 * f, 0, 2 * f))
    act = [(0.2, gape(0.3, 48)), (0.45, gape(0.7, 56)), (0.62, gape(1.0, 60)), (0.7, gape(1.0, 12)), (0.78, gape(0.9, 44)), (0.86, gape(0.9, 6)),
           (0.93, gape(0.8, 38)), (1.0, gape(0.7, 4))]
    end = gape(0.6, 8)
    mv = Move("gnaw", "NONE", 14, 24, 16, through=_mix(end, _pose(r), 0.35), title="Gnawing Pull", track=4.0, ability="Gnawing Pull", rng=(3, 12),
              kinds=("ranged",), damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(_pose(r), low, 0.5)), (1.0, low)], "active": act,
                                                                     "recover": _back(r, _mix(end, _pose(r), 0.35))}, lag=_lag())
    mv._gut = [(0.0, 1.0), ("windup", 1.0, 1.0), ("active", 0.62, 1.13), ("active", 1.0, 1.08), ("recover", 0.7, 1.0)]
    return mv


def hounds(r):
    """Hounds of the Court: he sits back on his haunches, the belly resting on his thighs, and claps his long hands
    together over it, twice, for his hounds; then throws his head back and roars, his arms wide, both mouths open."""
    both = {0: "free", 1: "free"}
    sit = dict(lift=-6, pitch=-32, bend=-6, gut=(-14, 0, 0), sag=2, feet={2: _foot(2, dz=-6), 3: _foot(3, dz=-6)}, hands=both, train=-6)
    apart = lambda a: _pose(r, **sit, neck=(-4, 0, 0), head=(-6 - 4 * a, 0, 0), maw=6,
                            limbs={0: {"arm_l": (-70.0, 0, -30 - 20 * a), "fore_l": (-40.0, 0, 0)}, 1: {"arm_r": (-70.0, 0, 30 + 20 * a), "fore_r": (-40.0, 0, 0)}},
                            fingers=(1, 1))
    clap = _pose(r, **sit, neck=(-2, 0, 0), head=(-4, 0, 0), maw=10,
                 limbs={0: {"arm_l": (-80.0, 0, 8), "fore_l": (-35.0, 0, 0)}, 1: {"arm_r": (-80.0, 0, -8), "fore_r": (-35.0, 0, 0)}}, fingers=(1, 1))
    roar = _pose(r, **{**sit, "pitch": -36}, neck=(-14, 0, 0), head=(-30, 0, 0), maw=48,
                 limbs={0: {"arm_l": (-40.0, 0, -70), "fore_l": (-20.0, 0, 0)}, 1: {"arm_r": (-40.0, 0, 70), "fore_r": (-20.0, 0, 0)}}, fingers=(1, 1), crown=(-10, 0, 0))
    shake = lambda a: _pose(r, **{**sit, "pitch": -35, "roll": 2 * a}, neck=(-12, 3 * a, 0), head=(-28, 6 * a, 3 * a), maw=44 + 4 * a,
                            limbs={0: {"arm_l": (-40.0, 0, -66 - 4 * a), "fore_l": (-20.0, 0, 0)}, 1: {"arm_r": (-40.0, 0, 66 - 4 * a), "fore_r": (-20.0, 0, 0)}},
                            fingers=(1, 1), crown=(-10, 0, 2 * a))
    act = [(0.12, clap), (0.26, apart(0.6)), (0.4, clap), (0.55, apart(1.0)), (0.7, roar), (0.82, shake(1)), (0.92, shake(-1)), (1.0, shake(0))]
    end = shake(0)
    down = _pose(r, lift=-3, pitch=4, bend=2, head=(2, 0, 0), maw=6, feet={0: _foot(0, up=1), 1: _foot(1, up=1)})
    return Move("hounds", "NONE", 12, 26, 16, through=_mix(end, down, 0.4), title="Hounds of the Court", track=0.0, ability="Hounds of the Court",
                rng=(0, 99), kinds=("summon",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, _pose(r, **sit, head=(-2, 0, 0), maw=4, limbs={0: {"arm_l": (-50.0, 0, -20), "fore_l": (-40.0, 0, 0)},
                                                                                         1: {"arm_r": (-50.0, 0, 20), "fore_r": (-40.0, 0, 0)}})),
                                   (1.0, apart(1.0))],
                        "active": act, "recover": [(0.45, _mix(end, down, 0.6)), (0.75, _mix(down, _pose(r), 0.6))]}, lag=_lag())


def frenzy(r):
    """Starving Frenzy: he rears half up and claws at the air in front of him, left, right, left, right, faster and
    faster, the belly mouth snapping with every swipe and his head jerking; then he drops back onto his knuckles."""
    both = {0: "free", 1: "free"}
    up_ = {0: (0.9, 0.1, 0.6), 1: (-0.9, 0.1, 0.6)}
    rear = _pose(r, lift=2, pitch=-24, bend=-6, neck=(-6, 0, 0), head=(-8, 0, 0), gut=(-6, 0, 0), maw=20, feet={0: (22.0, 30.0, -24.0), 1: (-22.0, 30.0, -24.0)},
                 poles=up_, hands=both, fingers=(1, 1), train=-4)

    def claw(a, m):
        hi_ = {0: (20.0 - 8 * a, 34.0 - 14 * max(0, a), -30.0 - 10 * max(0, a)), 1: (-20.0 - 8 * a, 34.0 - 14 * max(0, -a), -30.0 - 10 * max(0, -a))}
        return _pose(r, lift=2, pitch=-22 + 4 * abs(a), yaw=6 * a, roll=-4 * a, bend=-5, side=5 * a, twist=8 * a, neck=(-4, 6 * a, 0), head=(-6, 12 * a, 4 * a),
                     gut=(-4, 0, -6 * a), maw=m, feet=hi_, poles=up_, hands=both, fingers=(1, 1), crown=(0, 0, 5 * a))
    act = [(0.12, claw(1, 36)), (0.26, claw(-1, 4)), (0.38, claw(1, 40)), (0.5, claw(-1, 2)), (0.6, claw(1, 42)), (0.7, claw(-1, 0)),
           (0.79, claw(1, 44)), (0.88, claw(-1, 0)), (1.0, claw(0, 30))]
    end = claw(0, 30)
    land = _pose(r, lift=-3, pitch=5, bend=3, head=(4, 0, 0), gut=(4, 0, 0), sag=1, maw=8, feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)}, fingers=(0.5, 0.5))
    return Move("frenzy", "NONE", 10, 22, 12, through=_mix(end, land, 0.5), title="Starving Frenzy", track=4.0, ability="Starving Frenzy", rng=(0, 99),
                kinds=("buff",), damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(_pose(r), rear, 0.4)), (1.0, rear)], "active": act,
                                                                     "recover": [(0.4, land), (0.75, _mix(land, _pose(r), 0.7))]}, lag=_lag())


def famine(r):
    """Famine (PERILOUS, the signature): he stops, and folds over his belly, his arms wrapped round it and his head
    bowed over it, shuddering; the belly mouth opens wider and wider and he breathes in until the belly caves in to his
    ribs; then he throws his arms wide and his head back and howls the famine out of himself, the belly mouth gaping;
    slowly it swells again."""
    both = {0: "free", 1: "free"}
    hug = lambda a, m: _pose(r, lift=-4 + 1.5 * a, pitch=6, bend=10, roll=2 * a, neck=(16, 0, 0), head=(22, 0, 3 * a), gut=(4, 0, 0), sag=-1, maw=m,
                             feet={0: (10.0, 18.0, -26.0), 1: (-10.0, 18.0, -26.0)}, poles={0: (1, 0.2, 0.2), 1: (-1, 0.2, 0.2)}, hands=both,
                             fingers=(0.4, 0.4), crown=(6, 0, 2 * a))
    wind = [(0.2, hug(0, 6)), (0.34, hug(1, 14)), (0.46, hug(-1, 22)), (0.58, hug(1, 30)), (0.7, hug(-1, 38)), (0.8, hug(1, 46)), (0.9, hug(-1, 52)),
            (1.0, hug(0.5, 56))]
    howl = lambda a: _pose(r, lift=3, pitch=-28, bend=-10, roll=1.5 * a, neck=(-16, 2 * a, 0), head=(-30, 3 * a, 2 * a), gut=(-12, 0, 0), maw=64,
                           feet={0: (36.0, 36.0, -8.0), 1: (-36.0, 36.0, -8.0)}, poles={0: (1, 0.4, 0.4), 1: (-1, 0.4, 0.4)}, hands=both, fingers=(1, 1),
                           train=-8, crown=(-14, 0, 0))
    act = [(0.2, howl(0)), (0.4, howl(1)), (0.6, howl(-1)), (0.8, howl(1)), (1.0, howl(0))]
    end = howl(0)
    mv = Move("famine", "PERILOUS", 30, 14, 30, through=_mix(end, _pose(r), 0.25), title="Famine", signature=True, track=4.0, ability="Famine",
              rng=(0, 8), kinds=("area",), damage=0, knock=0, plant=(),
              stages={"windup": wind, "active": act, "recover": [(0.35, _mix(end, _pose(r), 0.45)), (0.7, _mix(end, _pose(r), 0.85))]}, lag=_lag())
    mv._gut = [(0.0, 1.0), ("windup", 0.3, 0.98), ("windup", 1.0, 0.78), ("active", 0.2, 0.8), ("active", 1.0, 0.82), ("recover", 0.5, 0.92),
               ("recover", 1.0, 1.0)]
    return mv


def wake(r):
    """His intro, from where he lay slumped on his belly chewing his knuckles: his small head comes up and he sniffs,
    looking one way and the other; the belly mouth yawns open, wide, and shuts; he pushes himself up onto his
    knuckles and looks at you."""
    first = _slumped(r)
    sniff = lambda a, m: _belly(r, pitch=3, bend=2, neck=(-6, 10 * a, 0), head=(-16, 22 * a, 4 * a), gut=(-4, 0, 0), maw=m,
                                            feet={0: (22.0, 3.0, -34.0), 1: (-22.0, 3.0, -34.0), **LIE},
                                            poles={0: (0.8, -0.3, 0.6), 1: (-0.8, -0.3, 0.6), **LIE_POLE},
                                            hands={0: ((0, 1, 0), (1, 0, 0)), 1: ((0, 1, 0), (1, 0, 0))}, train=-2, crown=(-2, 0, -4))
    push = _pose(r, lift=-8, pitch=7, bend=4, neck=(-6, 0, 0), head=(-8, 0, 0), gut=(4, 0, 0), sag=1.5, maw=6,
                 feet={0: _foot(0, dz=-3), 1: _foot(1, dz=-3), 2: _foot(2, dx=3), 3: _foot(3, dx=-3)}, poles=LIE_POLE)
    both = {0: "free", 1: "free"}
    on_gut = {0: (14.0, 22.0, -26.0), 1: (-14.0, 22.0, -26.0)}
    sat = {0: (0.8, 0.2, 0.6), 1: (-0.8, 0.2, 0.6)}
    yawn = lambda m, h: _pose(r, lift=-3, pitch=-22, bend=-4, neck=(-8, 0, 0), head=(h, 0, 0), gut=(-6, 0, 0), maw=m, feet=on_gut, poles=sat, hands=both,
                              fingers=(0.4, 0.4), train=-6, crown=(-6, 0, -4))
    stare = _pose(r, lift=1, pitch=-2, neck=(-8, 0, 0), head=(-10, 0, 0), gut=(-4, 0, 0), maw=4)
    mv = Move("wake", "NONE", 14, 30, 14, through=_mix(stare, _pose(r), 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, sniff(0, 2)), (0.65, sniff(-1, 4)), (1.0, sniff(1, 2))],
                      "active": [(0.2, push), (0.38, yawn(18, -12)), (0.55, yawn(52, -26)), (0.66, yawn(50, -24)), (0.76, yawn(6, -14)),
                                 (0.9, _mix(push, stare, 0.6)), (1.0, stare)],
                      "recover": _back(r, _mix(stare, _pose(r), 0.4))}, lag=_lag())
    mv._first = first
    mv._gut = [(0.0, 1.0), ("active", 0.38, 1.0), ("active", 0.55, 1.06), ("active", 0.76, 1.0)]
    return mv


def _staged(r, mv):
    """stage_clips, but a move with a first pose of its own (the wake, from the kneel) starts from it."""
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


# the ground (SPEC §35.10): the hands and feet, the belly's lower lip, the head and the train's end stay out of the floor
LEG_GROUND = [(["hand_" + NM[k], "fingers_" + NM[k]], [("fore_" + NM[k], 0, 30), ("arm_" + NM[k], 0, 30)]) for k in ARMS] + \
             [(["foot_" + NM[k]], [("thigh_" + NM[k], 0, 30), ("shin_" + NM[k], 0, 40)]) for k in LEGS]
BODY_GROUND = [(["maw"], [("maw", 0, 40, -1)]), (["head"], [("neck", 0, 30, -1), ("head", 0, 30, -1)]),
               (["tr1", "tr2"], [("tr0", 0, 40, 1), ("tr1", 0, 40, 1)])]
STANDS = {"slap": (0, 2, 3), "slap_b": (1, 2, 3), "sweep": (0, 2, 3), "devour": (), "gorge": (2, 3), "gnaw": (0, 1, 2, 3), "hounds": (), "frenzy": (2, 3),
          "famine": (2, 3), "wake": ()}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [slap(r, 1, "slap"), slap(r, 0, "slap_b"), sweep(r), devour(r), gorge(r), gnaw(r), hounds(r), frenzy(r), famine(r), wake(r)]
    for m in mv:
        _staged(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
        if getattr(m, "_gut", None):
            _scale(r, core.clip_name(m), "gut", _track(m, m._gut))
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, LEG_GROUND, clips=everyday)
    core.ground_pass(r, BODY_GROUND, clips=everyday)
    core.ground_pass(r, LEG_GROUND + BODY_GROUND, clips=["death"], skip=())
    return mv


# His sounds (SPEC §35.8): the ravager's bulk and roar, eating and a belch, the hoglin's greed, mud under him; chosen on
# paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.hoglin.angry", 0.8, 0.6), ("entity.generic.eat", 0.8, 0.6)],
    "tell_heavy": [("entity.ravager.ambient", 1.0, 0.6), ("entity.player.burp", 1.2, 0.5)],
    "tell_perilous": [("entity.ravager.roar", 1.2, 0.4), ("entity.warden.sniff", 1.4, 0.6), ("entity.husk.ambient", 1.2, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 0.8, 0.7), ("entity.hoglin.attack", 0.8, 0.8)],
    "swing_heavy": [("entity.player.attack.knockback", 0.8, 0.6), ("entity.ravager.attack", 1.2, 0.6)],
    "cast": [("entity.ravager.stunned", 1.0, 0.7), ("entity.generic.drink", 1.2, 0.5)],
    "impact": [("entity.generic.big_fall", 1.6, 0.6), ("block.mud.fall", 1.4, 0.5), ("entity.generic.eat", 1.4, 0.4)],
    "roar": [("entity.ravager.roar", 1.6, 0.5), ("entity.hoglin.angry", 1.2, 0.4)],
    "step": [("entity.ravager.step", 0.6, 0.7), ("block.mud.step", 0.4, 0.8)],
}
MOVE_SOUNDS = {"gnaw": {"swing": "cast"}, "hounds": {"swing": "roar", "impact": ""}, "frenzy": {"swing": "roar", "impact": ""},
               "famine": {"swing": "roar"}, "wake": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},  # he waits for you slumped on his belly, chewing, and gets up
    "phases": [
        {"pool": [
            {"move": "slap", "weight": 3, "when": "near", "then": [["slap_b"], ["slap_b", "slap"]]},
            {"move": "devour", "weight": 3, "when": "far", "then": [["slap"]]},
            {"move": "gnaw", "weight": 2, "when": "far", "then": [["slap", "slap_b"]]},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
        # still hungry, as in DeepBestiary: he calls his hounds and gorges
        {"transition": "hounds", "pool": [
            {"move": "slap", "weight": 3, "when": "near", "then": [["slap_b"], ["slap_b", "gorge"]]},
            {"move": "gorge", "weight": 2, "when": "near"},
            {"move": "devour", "weight": 3, "when": "far", "then": [["slap"]]},
            {"move": "gnaw", "weight": 2, "when": "far", "then": [["gorge"]]},
            {"move": "hounds", "weight": 1},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
        # starving: the frenzy, the famine
        {"transition": "frenzy", "pool": [
            {"move": "famine", "weight": 2},
            {"move": "frenzy", "weight": 1},
            {"move": "slap", "weight": 3, "when": "near", "then": [["slap_b", "gorge"]]},
            {"move": "gorge", "weight": 2, "when": "near"},
            {"move": "devour", "weight": 3, "when": "far", "then": [["slap", "slap_b"]]},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.45, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
