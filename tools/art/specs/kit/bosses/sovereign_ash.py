"""The Sovereign of Ash (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's eighth Sovereign, a Flier. The Cinder
Wyrm, picked from its silhouette round 2026-10-29: "It burned everything to keep going."

A dragon that burned its own body to keep its fire going. Its hide is charcoal, black and split in plates over the
embers still alive under it, and grey ash lies along its back and flakes off it as it moves. On its left side the fire
has eaten right through: its ribs stand bare over a hollow where its heart still glows, the Heart of Ash. Its wings
are burnt down to their bones, the leather between them charred thin and holed, with the holes' edges still glowing.
Its horns sweep back off its skull, its eyes are slits of fire, and there is fire in its throat when it opens its jaws.

Moves (SPEC §35.11): the Claw and the Bite (LIGHT, a string: a forefoot raked across, then its jaws); the Wing Rush
(HEAVY, its gap closer: it crouches, beats its wings once and throws itself at you); Cinders (it draws its head back,
the fire rising in its throat, and spits five cinders); Ash Cloud (it rears and beats its wings down twice, raising the
ash); Ashfall (it climbs into the air on its wings, hovers and shakes the ash off itself); Embers (it hunches and
heaves, and embers drop out of its open side); for whoever is behind it, the Tail Sweep; Last Embers (it rears up on
its hind legs and comes down on its forefeet, and the embers burst out of its cracks); and Burnout (the signature: it
wraps its wings round itself and curls up, its cracks and its heart burning brighter and brighter, then throws its
wings and head up and the fire goes out of it all at once). Its intro: it lies curled like a burnt-out log with its
wings over it; the embers in its cracks wake; it lifts its head, shakes off the ash and spreads its wings. Broken, it
sinks onto its belly with its wings slumped and its heart dim; it dies the fire going out of it, crumbling to its side.
"""
import math

from kit import core, ik, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

DEG = math.pi / 180.0
ID = "sovereign_ash"
SCALE = 2.4  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Cinder Wyrm (docs/bosses/ROLLOUT.md, A). About 150 px from its nose to the tip of its tail (9.4 blocks), its
# shoulders 52 px up (3.3 blocks), its head 74 (4.6); 10 blocks across its wings spread. Value story: the heart and the
# embers brightest (its heart, its eyes, its throat, the cracks, the holes' glowing edges), then the pale ash on its
# back, then the charcoal of its hide; the bare bone of its ribs and horns pale but dull; its claws and the char of its
# wings darkest.
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT, BACK = (0, 1), (2, 3)
LIMB = {0: ("arm0", "fore0", "hand0"), 1: ("arm1", "fore1", "hand1"), 2: ("thigh2", "shin2", "hock2"), 3: ("thigh3", "shin3", "hock3")}
UPPER = {0: 14, 1: 14, 2: 14, 3: 14}
LOWER = {0: 14, 1: 14, 2: 13, 3: 13}
# the wrists and hocks at rest (x, up, z), each foot a hair over the floor: the forelegs a little bent under the
# shoulders, the hind legs bent at the knee with the hocks back
REST_FOOT = {0: (13.5, 8.2, -18.0), 1: (-13.5, 8.2, -18.0), 2: (12.5, 12.2, 25.0), 3: (-12.5, 12.2, 25.0)}
POLE = {0: (0.0, 0.0, 1.0), 1: (0.0, 0.0, 1.0), 2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}  # the elbows back, the knees forward
ROLL_H, ROLL_Z = 28.0, 2.0  # the roll bone at its body's middle, where it lies over
FOOT = {0: ["hand0"], 1: ["hand1"], 2: ["hock2"], 3: ["hock3"]}
NECK = ("neck0", "neck1", "neck2")
NECK_W = (0.4, 0.35, 0.25)  # a neck turn shared down the neck
TAIL = ("tail0", "tail1", "tail2", "tail3", "tail4")
TAIL_W = (0.14, 0.18, 0.21, 0.23, 0.24)  # a tail swing shared down the tail, more toward its end
SIDES_ = (("l", 1), ("r", -1))
WING = (20, 24)  # its wing's arm and forearm
# its wing's three fingers: how far each sweeps back from the forearm's line (degrees), its length, how deep the
# leather behind it runs
FING = ((6.0, 40, 14), (30.0, 42, 16), (58.0, 34, 15))
# the leather in steps along each finger, a scalloped trailing edge: (from, to) of its length and how deep
MEM_STEPS = ((0.0, 0.45, 1.0), (0.45, 0.75, 0.8), (0.75, 0.95, 0.45))
# the wings folded at rest (its bind pose): the arm rolled so the leather hangs down its flank and swung back and up,
# the forearm folded forward over it so the wrist stands high by its shoulder, and the fingers folded back along its
# side; per side, (roll, sweep, lift) on the arm and the fold on the forearm and the hand
FOLD_DIRS = (((0.35, -0.45, 1.0), (0.3, 1.0, 0.0)),  # the arm: back, up and out a little; its leather hanging down and out
             ((0.12, -0.35, -1.0), (0.35, 1.0, 0.0)),  # the forearm: forward and up, the wrist high by its shoulder
             ((0.1, 0.28, 1.0), (0.15, 1.0, -0.1)))  # the hand: its fingers trailing back along its side, sloping down
FAN_FOLD = 0.35  # the fingers' fan closed to this much of its spread when folded


def _frame(d, m):
    """A turn whose x runs along d and whose z (the leather's way) lies toward m, model axes."""
    def unit(v):
        n = math.sqrt(sum(c * c for c in v))
        return tuple(c / n for c in v)
    x = unit(d)
    z = unit(tuple(m[i] - x[i] * sum(m[j] * x[j] for j in range(3)) for i in range(3)))
    y = (z[1] * x[2] - z[2] * x[1], z[2] * x[0] - z[0] * x[2], z[0] * x[1] - z[1] * x[0])
    return tuple(tuple((x, y, z)[c][rw] for c in range(3)) for rw in range(3))


def _fold_turns():
    """The folded wing's bind turns, left (degrees): each bone's frame from FOLD_DIRS, relative to the one before."""
    out, prev = [], ((1, 0, 0), (0, 1, 0), (0, 0, 1))
    for d, m in FOLD_DIRS:
        F = _frame(d, m)
        rel = R._mm(core._t(prev), F)
        out.append(tuple(v / DEG for v in core._euler(rel)))
        prev = F
    return out


FOLD = _fold_turns()


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def _folded(sg):
    """Each wing bone's bind turn: the wing folded (sg: +1 its left wing, -1 its right)."""
    nm = "l" if sg > 0 else "r"
    return {f"wing_{nm}{k}": (a, sg * b, sg * c) for k, (a, b, c) in enumerate(FOLD)}


def body():
    r = R.Rig(ID, tex=(512, 512), translucent=True)
    base = r.bone("base", (0, 24, 0))
    rl = _bone(base, "roll", 0, ROLL_H, ROLL_Z)
    hips = _bone(rl, "hips", 0, 36 - ROLL_H, 16 - ROLL_Z)
    loin = _bone(hips, "loin", 0, 1, -13)
    chest = _bone(loin, "chest", 0, 2, -13)
    # the trunk: heavy haunches, a deep belly and a deep chest with a keel of breastbone under it; on its left side,
    # between its foreleg and its thigh, the fire has burnt through: bare ribs between a rail above and below, over
    # the hollow where its heart glows
    _box(hips, "rump", 0, -10, 2, 22, 19, 19)
    _box(loin, "belly", -2, -13, 0, 21, 22, 16)
    _box(loin, "rail_t", 10.5, 6, 0, 5, 3, 16)
    _box(loin, "rail_b", 10.5, -13, 0, 5, 3, 16)
    for i, (z, y0, h) in enumerate(((-5.5, -10, 16), (-0.5, -3, 9), (4.5, -10, 16))):  # the middle one burnt short
        _box(loin, f"rib{i}", 11.5, y0, z, 3, h, 2)
    _box(loin, "heart", 9, -8, -0.5, 4, 10, 8)
    _box(chest, "chest", 0, -16, -2, 24, 26, 20)
    _box(chest, "keel", 0, -19, -6, 14, 5, 10)
    # the ridge of plates down its back, some burnt down to stubs
    for bone, nm, plates in ((hips, "h", ((-5, 4, 5), (1, 3, 4), (7, 3, 4))), (loin, "l", ((-4, 5, 5), (3, 2, 4))),
                             (chest, "c", ((-9, 6, 6), (-3, 5, 5), (3, 4, 5)))):
        top = {"h": 9, "l": 9, "c": 10}[nm]
        for i, (z, h, d) in enumerate(plates):
            _box(bone, f"plate_{nm}{i}", 0, top, z, 2, h, d)
    # the neck in three, rising up and forward off the top of its chest, the head level at its end
    n0 = _bone(chest, "neck0", 0, 9, -9, (-42, 0, 0))
    _box(n0, "neck0", 0, -6, -6, 12, 12, 14)
    _box(n0, "plate_n0", 0, 6, -6, 2, 4, 5)
    n1 = _bone(n0, "neck1", 0, 0, -12, (-8, 0, 0))
    _box(n1, "neck1", 0, -5, -6, 10, 10, 13)
    _box(n1, "plate_n1", 0, 5, -6, 2, 3, 5)
    n2 = _bone(n1, "neck2", 0, 0, -11, (6, 0, 0))
    _box(n2, "neck2", 0, -4.5, -5.5, 9, 9, 12)
    _box(n2, "plate_n2", 0, 4.5, -5, 2, 3, 4)
    head = _bone(n2, "head", 0, 0, -10, (44, 0, 0))
    jaw = _bone(head, "jaw", 0, -4, -2)
    # the head: a broad skull under a heavy brow, a long snout with a ridge down it, fangs, cheek spikes, and two horns
    # sweeping back off the top of the skull and turning up at their ends
    _box(head, "skull", 0, -4, -5, 13, 10, 12)
    _box(head, "brow", 0, 5, -9, 14, 2, 4)
    _box(head, "snout", 0, -4, -16, 9, 7, 10)
    _box(head, "ridge", 0, 3, -14.5, 5, 2, 9)
    _box(head, "teeth_u", 0, -5, -15.5, 8, 1, 9)
    for nm, s in SIDES_:
        _box(head, "fang_" + nm, s * 3.5, -6.5, -19.5, 1, 2, 1)
        _box(head, "cheek_" + nm, s * 7, -3, -2, 2, 5, 6, mirror=s < 0)
        hn = _bone(head, "horn_" + nm, s * 4.5, 6, -4, (10, s * 20, 0))
        _box(hn, "horn_" + nm, 0, -1.5, 4, 3, 3, 9)
        ht = _bone(hn, "hornt_" + nm, 0, 0, 8, (22, 0, 0))
        _box(ht, "hornt_" + nm, 0, -1, 3.5, 2, 2, 7)
    _box(jaw, "jaw", 0, -3, -8.5, 9, 3, 15)
    _box(jaw, "teeth_l", 0, 0, -10.5, 8, 1, 9)
    _box(jaw, "chin", 0, -5, -4, 5, 2, 6)
    # the tail: heavy at its root, drooping to near the floor and turning up at its end, a spade of a blade at its tip
    t = hips
    for k, (z, rot, w, h, ln, pl) in enumerate(((10, -18, 13, 12, 15, 4), (14, -10, 11, 10, 14, 3), (13, -6, 9, 8, 13, 3), (12, 0, 7, 6, 12, 2),
                                                (11, 6, 5, 4, 12, 0))):
        t = _bone(t, f"tail{k}", 0, 4 if k == 0 else 0, z, (rot, 0, 0))
        _box(t, f"tail{k}", 0, -h / 2, ln / 2, w, h, ln)
        if pl:
            _box(t, f"plate_t{k}", 0, h / 2, ln / 2 - 1, 2, pl, 4)
    _box(t, "spade", 0, -1, 14, 9, 2, 6)
    _box(t, "spade2", 0, -1, 18.5, 5, 2, 3)
    # the legs: thick forelegs with a spur at the elbow and three claws; the haunches heavy, the hocks long
    for k in range(4):
        s = SIDE[k]
        up_, lo_, en_ = LIMB[k]
        mir = s < 0
        if k in FRONT:
            a = _bone(chest, up_, s * 12, -8, -5)
            a.cube(up_, -5, -5, -5, 10, 18, 10, mirror=mir)
            f = a.bone(lo_, (0, UPPER[k], 0))
            f.cube(lo_, -3.5, 0, -3.5, 7, LOWER[k], 7, mirror=mir)
            f.cube(f"spur{k}", -1, -1, 2.5, 2, 3, 4, mirror=mir)
            h = f.bone(en_, (0, LOWER[k], 0))
            h.cube(f"wrist{k}", -3.5, 0, -3.5, 7, 5, 7, mirror=mir)
            h.cube(f"palm{k}", -4.5, 5, -6.5, 9, 3, 11, mirror=mir)
            top = 6
        else:
            a = _bone(hips, up_, s * 11, -4, 3)
            a.cube(up_, -5.5, -6, -6.5, 11, 18, 13, mirror=mir)
            f = a.bone(lo_, (0, UPPER[k], 0))
            f.cube(lo_, -3.5, 0, -4, 7, LOWER[k], 8, mirror=mir)
            h = f.bone(en_, (0, LOWER[k], 0))
            h.cube(f"meta{k}", -3, 0, -3, 6, 9, 6, mirror=mir)
            h.cube(f"sole{k}", -4.5, 9, -7.5, 9, 3, 12, mirror=mir)
            top = 10
        for c, x in enumerate((-4, -1, 2)):
            h.cube(f"claw{k}{c}", x, top, -9 if k in FRONT else -10, 2, 2, 3, mirror=mir)
    for k in range(4):
        ik.rest_limb(r, *LIMB[k], REST_FOOT[k], POLE[k])
    fr0 = core.frames(r, {})
    for k in range(4):  # the feet set flat on the floor at rest
        end = LIMB[k][2]
        r.by_name[end].rot = tuple(v + w for v, w in zip(r.by_name[end].rot, ik.orient(r, fr0, end, (0, 1, 0), (1, 0, 0))))
        fr0 = core.frames(r, {})
    # the wings, built spread and level (the arm out along x, the leather trailing back), and folded at rest: the bind
    # turns fold them (_folded), and every pose turns them from there
    for nm, sg in SIDES_:
        mir = sg < 0
        x0 = lambda w: 0 if sg > 0 else -w
        fold = _folded(sg)
        w0 = _bone(chest, f"wing_{nm}0", sg * 8, 10, -6, fold[f"wing_{nm}0"])
        w0.cube(f"warm_{nm}", x0(WING[0]), -1.5, -1.5, WING[0], 3, 3, mirror=mir)
        w0.cube(f"wmem_{nm}0", x0(WING[0] - 2), -0.5, 1.5, WING[0] - 2, 1, 13, mirror=mir)
        w1 = w0.bone(f"wing_{nm}1", (sg * WING[0], 0, 0), fold[f"wing_{nm}1"])
        w1.cube(f"wfore_{nm}", x0(WING[1]), -1, -1, WING[1], 2, 2, mirror=mir)
        w1.cube(f"wmem_{nm}1", x0(WING[1]), -0.5, 1, WING[1], 1, 14, mirror=mir)
        w1.cube(f"wmem_{nm}1b", x0(WING[1] - 8), -0.5, 15, WING[1] - 8, 1, 5, mirror=mir)
        w1.cube(f"thumb_{nm}", (WING[1] - 2) if sg > 0 else -WING[1], -1, -4, 2, 2, 4, mirror=mir)
        w2 = w1.bone(f"wing_{nm}2", (sg * WING[1], 0, 0), fold[f"wing_{nm}2"])
        for k, (sw, ln, dp) in enumerate(FING):
            fb = w2.bone(f"fing_{nm}{k}", (0, 0, 0), (0, -sg * sw * FAN_FOLD, 0))
            fb.cube(f"wfing_{nm}{k}", x0(ln), -1, -1, ln, 2, 2, mirror=mir)
            for j, (a0, a1, dd) in enumerate(MEM_STEPS):
                L0, L1 = round(a0 * ln), round(a1 * ln)
                fb.cube(f"wmemf_{nm}{k}{j}", (L0 if sg > 0 else -L1), -0.5, 1, L1 - L0, 1, max(2, round(dp * dd)), mirror=mir)
    r.look = [("head", 0.45, 0.3), ("neck2", 0.25, 0.2), ("neck1", 0.15, 0.1)]
    r.shadow = 3.4
    r.size = 1.0
    # secondary parts (SPEC §35.10): the tail and the tips of its horns trail
    r.springs = [("tail2", 0.55, 1.8, 0.12), ("tail3", 0.6, 2.0, 0.14), ("tail4", 0.6, 2.2, 0.16)]
    r.guard = [LIMB[k][2] for k in range(4)] + ["head", "jaw", "tail3", "tail4"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

CHAR = Ramp("#070605", "#0d0b0a", "#14110f", "#1c1815", "#25201c", "#2f2924", "#3b332d", "#483f37", "#584d43")
ASH = Ramp("#3c3835", "#514c48", "#68625d", "#807a74", "#99938c", "#b2aca4", "#c9c3bb", "#ddd8d0", "#eeeae4")
BONE = Ramp("#4a4239", "#625849", "#7c705d", "#958872", "#ad9f88", "#c4b6a0", "#d8cbb6")
LEATHER = Ramp("#0a0807", "#140f0d", "#1e1713", "#2a201a", "#372a21", "#45352a")
EMBER = ("#fff6d0", "#ffd879", "#ffa23a", "#f06a1f", "#b63a12", "#5c1a08")


def _cracks(P_, cube, faces, seed, every=7, length=8):
    """Embers alive under the char: cracks of fire running across each face between the plates (lit on the glow layer
    too), hottest at their middles."""
    rng = (seed * 7919 + 31) % 9973
    for fname in faces:
        f, gl = P_.face(cube, fname), P_.face(cube, fname, glow=True)
        n = max(1, (f.w * f.h) // (every * every * 2))
        for v in range(n):
            rng = (rng * 1103 + 12345) % 9973
            i, j = rng % f.w, rng // 13 % f.h
            horiz = rng % 3 != 0
            for step in range(length):
                if not (0 <= i < f.w and 0 <= j < f.h):
                    break
                hot = step == length // 2
                f.set(i, j, hexc(EMBER[2] if hot else EMBER[4], 255))
                gl.set(i, j, hexc(EMBER[2] if hot else EMBER[4], 200 if hot else 110))
                rng = (rng * 1103 + 12345) % 9973
                if horiz:
                    i += 1
                    j += (-1, 0, 0, 1, 0)[rng % 5]
                else:
                    j += 1
                    i += (-1, 0, 0, 1, 0)[rng % 5]
                if rng % 13 == 0 and step > 1:  # a branch
                    fi, fj = i, j + 1
                    for _ in range(2):
                        if 0 <= fi < f.w and 0 <= fj < f.h:
                            f.set(fi, fj, hexc(EMBER[3], 255))
                            gl.set(fi, fj, hexc(EMBER[4], 120))
                        fi, fj = fi - 1, fj + 1


def _plates(f, ramp, base, seed=0):
    """Charred scales in rows: each 3 x 2 plate a touch lighter at its top edge and dark at its seam below."""
    for i, j in f.each():
        row = j // 2
        col = (i + (row % 2) * 2) // 3
        h = (row * 31 + col * 17 + seed) % 7
        v = base + 0.25 * (h - 3) / 3
        if j % 2 == 1 and (i + (row % 2) * 2) % 3 == 0:
            v -= 1.0  # the seam
        elif j % 2 == 0:
            v += 0.35
        f.set(i, j, ramp.at(v))


def paint(r, P_):
    """Charcoal hide in charred plates, darkest underneath, cracked with live embers; grey ash lying along its back and
    its neck, flaking; bare bone for its ribs, horns, fangs, spurs and claws, scorched; the leather of its wings charred
    thin and dark, holed, the holes ringed with glowing edges; its heart a white-hot coal in the hollow of its ribs;
    slit eyes of fire under its brow, fire in its throat and nostrils."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    hide = ["rump", "belly", "chest", "keel", "rail_t", "rail_b", "skull", "brow", "snout", "ridge", "jaw", "chin"] + of(
        "neck", "tail", "cheek_", "arm", "fore", "thigh", "shin", "wrist", "palm", "meta", "sole")
    for k, c in enumerate(hide):
        P_.paint(c, CHAR, 5.6, None, grad=1.6, rim=1.4, ao=1.2, noise=0.25, nscale=3)
        for fname in SIDES + ("top",):
            f = P_.face(c, fname)
            _plates(f, CHAR, 5.4 if fname != "top" else 5.8, k)
            if fname in SIDES:  # lighter toward the top of each side, where the light falls
                for i, j in f.each():
                    if j < f.h * 0.3:
                        f.set(i, j, CHAR.at(6.2 + 0.4 * ((i + j * 3) % 5 == 0)))
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, CHAR.at(2.6 + 0.5 * ((i * 3 + j) % 4 == 0)))
        big = c in ("rump", "belly", "chest", "tail0", "neck0")
        _cracks(P_, c, ("west", "east", "south", "north"), k + 1, every=9 if big else 11, length=7 if big else 6)
    # the ash lying along its back, thickest at the top, flaking down its sides
    for c in ["rump", "belly", "chest", "skull", "snout"] + of("neck", "tail"):
        f = P_.face(c, "top")
        for i, j in f.each():  # patches of ash, 3 px across, the char showing between them
            h = ((i // 3) * 7 + (j // 3) * 13 + len(c)) % 11
            if h < 8:
                f.set(i, j, ASH.at(4.6 + 0.3 * ((i + j) % 3) + (0.9 if h == 0 else 0.0) - (0.8 if h == 7 else 0.0)))
        for fname in ("west", "east"):
            f = P_.face(c, fname)
            for i, j in f.each():  # flaking down the sides in runs
                h = ((i // 2) * 5 + len(c)) % 7
                if j < (3 if h < 2 else 2 if h < 4 else 1 if h < 6 else 0):
                    f.set(i, j, ASH.at(3.8 + 0.5 * (j == 0)))
    for c in of("plate_"):
        P_.paint(c, CHAR, 3.4, None, grad=1.0, rim=1.6, ao=0.4, noise=0.2, nscale=2)
        f, gl = P_.face(c, "top"), P_.face(c, "top", glow=True)
        for i, j in f.each():  # their burnt edges still smouldering
            f.set(i, j, hexc(EMBER[3], 255))
            gl.set(i, j, hexc(EMBER[3], 140))
    # bare bone: the ribs, the horns, the fangs and teeth, the spurs and the claws, scorched toward their roots
    for c in of("rib", "horn", "fang_", "spur", "claw", "thumb_", "teeth_"):
        P_.paint(c, BONE, 4.2, None, grad=1.4, rim=1.0, ao=0.6, noise=0.2, nscale=2)
    for c in of("rib"):  # scorched: black toward their ends, lit orange on the side that faces the heart
        for fname in SIDES + ("top", "bottom"):
            f = P_.face(c, fname)
            for i, j in f.each():
                end = min(j, f.h - 1 - j) if fname in SIDES else 9
                if end < 3:
                    f.set(i, j, CHAR.at(3.0 + end))
                elif (i + j * 3) % 7 == 0:
                    f.set(i, j, BONE.at(2.0))
        f, gl = P_.face(c, "west"), P_.face(c, "west", glow=True)
        for i, j in f.each():
            f.set(i, j, hexc(EMBER[3], 255))
            gl.set(i, j, hexc(EMBER[3], 150))
    for c in of("horn_", "spur", "claw"):
        for fname in SIDES + ("top", "bottom"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if (i + j) % 5 == 0:
                    f.set(i, j, BONE.at(1.8))
    for c in of("claw", "hornt_"):
        P_.paint(c, BONE, 2.6, None, grad=1.0, rim=1.0, ao=0.3, noise=0.1, nscale=2)
    for c in of("teeth_"):  # a row of teeth: pale, dark gaps between them
        for fname in SIDES + ("top", "bottom"):
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, BONE.at(5.6) if i % 2 == 0 else CHAR.at(1.0))
    # the hollow of its side: dark, lit from below by its heart; the heart a white-hot coal
    f, gl = P_.face("belly", "east"), P_.face("belly", "east", glow=True)
    for i, j in f.each():
        heat = max(0.0, 1.0 - abs(j - f.h * 0.6) / (f.h * 0.6))
        if (i + j) % 3:
            f.set(i, j, hexc(EMBER[5]) if heat > 0.5 else CHAR.at(1.6))
        else:
            f.set(i, j, hexc(EMBER[4]) if heat > 0.6 else CHAR.at(0.8))
        if heat > 0.5:
            gl.set(i, j, hexc(EMBER[4], int(60 + 90 * heat)))
    for fname in SIDES + ("top", "bottom"):  # white-hot at its middle, orange, then a dark red rim, veined
        f, gl = P_.face("heart", fname), P_.face("heart", fname, glow=True)
        cx, cy = (f.w - 1) / 2, (f.h - 1) * 0.45
        for i, j in f.each():
            d = math.hypot((i - cx) / max(1.0, f.w / 2), (j - cy) / max(1.0, f.h / 2))
            k = 0 if d < 0.35 else 1 if d < 0.6 else 2 if d < 0.85 else 4
            if (i * 3 + j * 5) % 7 == 0 and k > 0:
                k = min(5, k + 1)  # a darker vein across it
            f.set(i, j, hexc(EMBER[k], 255))
            gl.set(i, j, hexc(EMBER[k], (255, 240, 210, 170, 120, 80)[k]))
    # the wings: the bones dark and scorched, the leather charred thin, burnt through in holes with glowing edges
    for c in of("warm_", "wfore_", "wfing_"):
        P_.paint(c, CHAR, 4.4, None, grad=1.0, rim=1.4, ao=0.4, noise=0.2, nscale=2)
        _cracks(P_, c, ("top",), len(c), every=7, length=4)
    for g, c in enumerate(of("wmem")):
        for fname in ("top", "bottom"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            W, H = f.w, f.h
            for i, j in f.each():
                # the holes: a few burnt through each panel, rounder near the trailing edge
                hole = False
                edge = False
                for hk in range(2):
                    cx = (g * 37 + hk * 53 + 11) % max(1, W)
                    cy = (g * 19 + hk * 29 + 5) % max(1, H)
                    rad = 1.2 + ((g + hk) % 3) * 0.9
                    d = math.hypot(i - cx, (j - cy) * 1.2)
                    if d < rad:
                        hole = True
                    elif d < rad + 1.0:
                        edge = True
                trail = j >= H - 1 and (i * 7 + g) % 3 == 0  # the ragged trailing edge
                if hole or trail:
                    f.set(i, j, (0, 0, 0, 0))
                elif edge:
                    f.set(i, j, hexc(EMBER[4], 255))
                    gl.set(i, j, hexc(EMBER[3], 140))
                else:
                    v = 2.6 + 0.8 * ((i * 3 + j * 5 + g) % 7 == 0) - 0.6 * (j >= H - 2) + (0.5 if fname == "top" else 0.0)
                    vein = (i + j * 2 + g) % 9 == 0
                    f.set(i, j, LEATHER.at(v - (0.9 if vein else 0.0)))
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, LEATHER.at(2.0))
    _face(P_)
    return P_.skin, P_.glow


def _face(P_):
    """The head: slit eyes of fire set under the heavy brow on either side of the skull, the brow's shadow over them;
    nostrils glowing at the end of its snout; the fire in its throat lighting the inside of its jaws."""
    for c in ("west", "east"):
        f, gl = P_.face("skull", c), P_.face("skull", c, glow=True)
        W = f.w
        for i in range(W):
            fwd = i if c == "east" else W - 1 - i  # 0 at the skull's front
            for j in range(f.h):
                if j == 0:
                    f.set(i, j, CHAR.at(1.2))  # the brow's shadow
                elif j in (1, 2) and 1 <= fwd <= 5:
                    hot = j == 1 and 2 <= fwd <= 4
                    f.set(i, j, hexc(EMBER[0] if hot else EMBER[2], 255))
                    gl.set(i, j, hexc(EMBER[0] if hot else EMBER[2], 255))
                elif j == 3 and 1 <= fwd <= 5:
                    f.set(i, j, CHAR.at(1.0))
    f = P_.face("brow", "bottom")
    for i, j in f.each():
        f.set(i, j, CHAR.at(0.8))
    f, gl = P_.face("snout", "north"), P_.face("snout", "north", glow=True)
    for i, j in f.each():  # the nostrils, two coals near its top
        m = min(i, f.w - 1 - i)
        if j in (1, 2) and m == 1:
            f.set(i, j, hexc(EMBER[1], 255))
            gl.set(i, j, hexc(EMBER[1], 230))
        elif j == f.h - 1:
            f.set(i, j, CHAR.at(0.8))
    for c, fname in (("jaw", "top"), ("snout", "bottom"), ("skull", "bottom")):  # the fire in its throat
        f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
        for i, j in f.each():
            m = min(i, f.w - 1 - i)
            heat = max(0, min(4, m - 1))
            col = EMBER[4 - heat] if m > 0 else EMBER[5]
            f.set(i, j, hexc(col, 255))
            if m > 0:
                gl.set(i, j, hexc(EMBER[min(5, 5 - heat)], 110 + 30 * heat))


# ---------------------------------------------------------------------------------------------------------------------
# Keys

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _flat(yaw):
    a = yaw * DEG
    return (math.cos(a), 0.0, -math.sin(a))


def _wing(nm, sg, spread=0.0, lift=0.0, sweep=0.0, curl=0.0, fan=0.0, twist=0.0, beat=0.0, ph=0.0, wrap=0.0):
    """One wing's turns from its bind (folded): spread opens it (0 folded at its side, 1 spread level out from it),
    lift raises it at the root (+ up, degrees), sweep turns it back at the root (- forward), curl bends it down along
    its length (+ down), wrap bends it forward at its elbow and its wrist (round its body), fan spreads its fingers wider
    (+) or closes them (-), twist turns its leading edge down, and beat runs a wingbeat out along it (its amplitude, at
    phase ph: + up at ph = pi/2)."""
    f = spread
    open_ = ((twist * 0.4, -sweep, 0.0), (twist * 0.7, wrap * 0.5, curl * 0.5), (twist, wrap * 0.5, curl * 0.5))
    out = {}
    for k in range(3):
        a = [FOLD[k][c] + (open_[k][c] - FOLD[k][c]) * f for c in range(3)]
        if k == 0:
            a[2] -= lift
        if beat:
            a[2] -= beat * (1.0, 0.5, 0.4)[k] * math.sin(ph - (0.0, 0.7, 1.2)[k])
            a[0] += beat * 0.25 * math.cos(ph - 0.3 * k)
        bind = FOLD[k]
        out[f"wing_{nm}{k}"] = (a[0] - bind[0], sg * (a[1] - bind[1]), sg * (a[2] - bind[2]))
    for k, (sw, _, _) in enumerate(FING):
        now = sw * (FAN_FOLD + (1 - FAN_FOLD) * f + 0.25 * fan * f)
        out[f"fing_{nm}{k}"] = (0.0, -sg * (now - sw * FAN_FOLD), 0.0)
    return out


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), arch=0.0, bend=0.0, side=0.0, twist=0.0, neck=(0, 0, 0), head=(0, 0, 0),
          jaw=0.0, tail=(0.0, 0.0), curl=0.0, wings=None, tilt=0.0, sink=0.0, spin=0.0, feet=None, reach=None, local=None, poles=None, hands=None,
          limbs=None):
    """lift (+ up), pitch (+ its rump's front dipping, its rear up), yaw (+ to its right), roll (+ its left side down) and
    shift (x, z) move its hips; arch (+ the loin's front dipping) turns its loin on its hips; bend (+ the front dipping),
    side (+ to its right) and twist turn its chest on its loin; neck (nod + down, turn + to its right, tilt) is shared
    down its three bones and head turns on top of them; jaw opens its mouth (degrees); tail (lift + up, sweep + to its
    left) swings the tail, curl bends its end up (+); wings: {"l": {...}, "r": {...}} or one dict for both, _wing's
    arguments; tilt and sink turn and lower it on its roll bone, spin turns all of it round on the spot (yaw). feet {leg:
    (x, height, z)}: where the wrists and hocks go (the rest stand where they stand at rest); reach {leg: (dx, dup,
    dz)}: the same, from where the leg's root is posed; local {leg: (x, down, z)}: from the root in its hips' own axes,
    however it lies; limbs {leg: {bone: turn}} sets a leg outright; hands {leg: "free" | degrees}: that foot hangs as
    its leg leaves it, or curled back so far (flat on the floor if not given)."""
    p = {"roll": {"rot": (0, spin, tilt), "pos": (0, -sink, 0)},
         "hips": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "loin": (arch, side * 0.4, twist * 0.4), "chest": (bend, side * 0.6, twist * 0.6), "head": tuple(head), "jaw": (jaw, 0, 0)}
    for b, w in zip(NECK, NECK_W):
        p[b] = tuple(c * w for c in neck)
    for k, (b, w) in enumerate(zip(TAIL, TAIL_W)):
        p[b] = (tail[0] * w + curl * (0, 0, 0.2, 0.35, 0.45)[k], tail[1] * w, 0)
    wings = wings or {}
    for nm, sg in SIDES_:
        wk = wings.get(nm, wings) if ("l" in wings or "r" in wings) else wings
        p.update(_wing(nm, sg, **{k: v for k, v in wk.items() if k not in ("l", "r")}))
    fr = core.frames(r, core.as_sample(p))
    feet, reach, poles, limbs, hands, local = feet or {}, reach or {}, poles or {}, limbs or {}, hands or {}, local or {}
    for k in range(4):
        if k in limbs:
            lp = {b: limbs[k].get(b, (0.0, 0.0, 0.0)) for b in LIMB[k][:2]}
        elif k in local:
            root = core.point(fr, LIMB[k][0], (0, 0, 0))
            d = R._mv(fr["hips"][0], local[k])
            lp = ik.limb(r, fr, *LIMB[k], tuple(root[i] + d[i] for i in range(3)), poles.get(k, POLE[k]))
        else:
            if k in reach:
                root = core.point(fr, LIMB[k][0], (0, 0, 0))
                dx, du, dz = reach[k]
                tgt = (root[0] + dx, 24.0 - root[1] + du, root[2] + dz)
            else:
                tgt = feet.get(k, REST_FOOT[k])
                if spin:  # turned round on the spot, its feet stand where they would if it had turned
                    a = spin * DEG
                    tgt = (tgt[0] * math.cos(a) + (tgt[2] - ROLL_Z) * math.sin(a), tgt[1],
                           -tgt[0] * math.sin(a) + (tgt[2] - ROLL_Z) * math.cos(a) + ROLL_Z)
            lp = ik.limb(r, fr, *LIMB[k], ik.model(tgt), poles.get(k, POLE[k]))
        p.update(lp)
    fr = core.frames(r, core.as_sample(p))
    flat = _flat(yaw + spin)
    for k in range(4):
        end = LIMB[k][2]
        how = hands.get(k, "free" if (k in limbs or k in local) else None)
        if how is None:
            p[end] = ik.orient(r, fr, end, (0, 1, 0), flat)
        else:
            p[end] = (0.0 if how == "free" else float(how), 0.0, 0.0)
    return p


def _foot(k, up=0.0, dz=0.0, dx=0.0):
    x, h, z = REST_FOOT[k]
    return (x + dx, h + up, z + dz)


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


def _floor(r, p, gap=0.3):
    """The pose let down or lifted on its roll bone until its lowest part just rests on the floor."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, [b.name for b in r.bones if b.cubes]) - (core.GROUND - gap)
    q = dict(p)
    rl = q["roll"]
    q["roll"] = {"rot": rl["rot"], "pos": (rl["pos"][0], rl["pos"][1] + up, rl["pos"][2])}
    return q


def _plant(r, clip, legs, where=None):
    """The feet that stand through a move keep where they stand on the floor, every tick, and lie flat on it heading
    the way it heads."""
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


def _on_floor(r, clip):
    """Between keys a foot can sag a hair into the floor (its leg's turns blend, not its path): wherever one would, its
    leg is solved again that tick to set it on the floor where it was, the joint its own way and the foot turned as it
    was."""
    floor = core.GROUND - 0.3
    still = {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}

    def fn(t, smp, fr):
        out = {}
        for k in range(4):
            up, lo, en = LIMB[k]
            v = core.lowest(r, fr, FOOT[k])
            if v <= floor:
                out.update({b: tuple(c / DEG for c in smp.get(b, still)["rot"]) for b in (up, lo, en)})
                continue
            end, joint, root = (core.point(fr, b, (0, 0, 0)) for b in (en, lo, up))
            pole = ik.unit(ik.sub(joint, tuple((root[i] + end[i]) / 2 for i in range(3))))
            M = fr[en][0]
            ydir, xdir = (M[0][1], M[1][1], M[2][1]), (M[0][0], M[1][0], M[2][0])
            to = (end[0], end[1] - (v - floor), end[2])
            short = abs(r.by_name[lo].pivot[1] - r.by_name[en].pivot[1]) + 0.5
            dh = to[1] - root[1]
            flat = math.hypot(to[0] - root[0], to[2] - root[2])
            if dh * dh + flat * flat < short * short and flat > 1e-6:
                kk = math.sqrt(max(0.0, short * short - dh * dh)) / flat
                to = (root[0] + (to[0] - root[0]) * kk, to[1], root[2] + (to[2] - root[2]) * kk)
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


def _scales(r, clip, bone, pts):
    """A clip's scale keys for one bone: [(seconds, scale)], 1 at both ends, straight lines between."""
    c = r.clips[clip]
    ks = {0.0: 1.0, c.length: 1.0}
    ks.update((round(t, 4), v) for t, v in pts)
    c.scale(bone, [(t, v, v, v) for t, v in sorted(ks.items())], "lin")


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It stands breathing slow and deep (8 s), its sides heaving and its tail sweeping slowly over the floor; it turns
    its head to look one way; it lowers its head and snorts, smoke out of its nostrils; its folded wings lift and
    shiver the ash off them, and settle; it looks the other way; it shifts its weight from one forefoot to the other."""
    L, n = 8.0, 160
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        t = L * u
        breath = math.sin(2 * math.pi * 0.5 * t)
        look_l, snort, ruffle, look_r = _wave(u, 0.16, 0.24), _wave(u, 0.37, 0.1), _wave(u, 0.56, 0.16), _wave(u, 0.78, 0.24)
        jerk = _wave(u, 0.385, 0.03)
        shiver = math.sin(2 * math.pi * 7.0 * t) * _wave(u, 0.56, 0.08)
        shuffle = _wave(u, 0.93, 0.1)
        feet = {1: _foot(1, up=4.0 * shuffle, dz=-1.0 * shuffle)} if shuffle > 0.01 else None
        keys.append((t, _pose(r, lift=-0.4 + 0.45 * breath - 1.0 * snort, pitch=0.3 * breath, roll=0.6 * math.sin(w) - 1.2 * shuffle, arch=0.6 * breath,
                              bend=0.9 * breath + 2.0 * snort, side=1.5 * math.sin(w + 0.4) + 5 * look_l - 5 * look_r,
                              neck=(-3 * breath + 22 * snort - 4 * (look_l + look_r), 22 * look_l - 22 * look_r, 0),
                              head=(2 * breath + 6 * snort - 10 * jerk, 20 * look_l - 20 * look_r, 6 * look_l - 6 * look_r),
                              jaw=1.5 + 1.5 * max(0.0, breath) + 8 * jerk, tail=(1.5 + 1.5 * math.sin(w), 14 * math.sin(w + 0.6)), curl=4 * math.sin(w + 1.4),
                              wings={"spread": 0.18 * ruffle, "lift": 16 * ruffle + 4 * shiver, "beat": 0.0},
                              feet=feet, hands={1: 30 * shuffle} if feet else None)))
    keyed(r, "idle", L, keys, loop=True)


WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # its walk: each foot by turns, back left, front left, back right, front right
TROT = {0: 0.0, 3: 0.04, 1: 0.5, 2: 0.54}  # its run: a heavy bounding trot, the diagonal pairs together


def _gait(u, stride_px, beta, lift, groups):
    """The feet through a stride at u: down, each moves back as far as it travels, so it stays put on the floor; up, it
    lifts and reaches forward again, the forefeet curling back under."""
    feet, flex = {}, {}
    st = stride_px * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
            flex[k] = 55.0 * math.sin(math.pi * f) ** 1.5 if k in FRONT else 25.0 * math.sin(math.pi * f) ** 1.5
    return feet, flex


def _locomotion(r):
    """It walks heavily, a foot at a time, its shoulders rolling and its head swinging low from side to side, its tail
    sweeping behind. It runs in a heavy bounding trot, its wings half open and lifting with every stride to steady it,
    its head low and its tail stretched out."""
    for name, L, stride, beta, lift, groups in (("walk", 1.6, 1.1, 0.7, 5.0, WALK), ("run", 0.8, 2.6, 0.46, 9.0, TROT)):
        sp = stride * 16
        keys = []
        n = 32
        for k in range(n + 1):
            u = k / n
            s = math.sin(2 * math.pi * u)
            feet, flex = _gait(u, sp, beta, lift, groups)
            if name == "walk":
                nod = math.sin(4 * math.pi * u + 0.6)
                keys.append((L * u, _pose(r, lift=-0.8 - 0.5 * math.cos(4 * math.pi * u), pitch=0.6 * math.sin(4 * math.pi * u), roll=2.0 * s, yaw=1.5 * s,
                                          bend=0.8 * math.sin(4 * math.pi * u + 1.2), side=3.5 * s, neck=(6 + 2 * nod, -8 * s, 0), head=(-4 + 2 * nod, -6 * s, 0),
                                          jaw=2.0, tail=(2 + 1.5 * math.sin(4 * math.pi * u), 14 * math.sin(2 * math.pi * u - 0.7)), curl=3 * s,
                                          wings={"lift": 3 * math.sin(4 * math.pi * u - 0.8)}, feet=feet, hands=flex)))
            else:
                b = math.cos(4 * math.pi * (u - 0.1))  # +1 down on a pair, -1 up between them
                keys.append((L * u, _pose(r, lift=-1.6 - 1.4 * b, pitch=-1.5 * b, roll=1.5 * s, bend=2.0 * b, side=2.0 * s, neck=(16 + 3 * b, -4 * s, 0),
                                          head=(-12 - 2 * b, -3 * s, 0), jaw=6.0, tail=(6 - 2 * b, 8 * s), curl=-4,
                                          wings={"spread": 0.32, "lift": 10 - 10 * b, "sweep": 18, "curl": 8 + 8 * b},
                                          feet=feet, hands=flex)))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.1, 2.6
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 20
        A = 14.0 * DEG
        for k in range(n + 1):
            u = k / n
            feet, flex = {}, {}
            for j, ph in WALK.items():  # each foot steps round the way it turns while the others hold
                q = (u - ph) % 1.0
                if q < 0.7:
                    a = sgn * A * (0.5 - q / 0.7)
                    up_ = 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a = sgn * A * (-0.5 + core.ease_io(f))
                    up_ = 5.0 * math.sin(math.pi * f)
                    flex[j] = 40.0 * math.sin(math.pi * f) if j in FRONT else 15.0 * math.sin(math.pi * f)
                x, h, z = REST_FOOT[j]
                feet[j] = (x * math.cos(a) + (z - ROLL_Z) * math.sin(a), h + up_, -x * math.sin(a) + (z - ROLL_Z) * math.cos(a) + ROLL_Z)
            keys.append((1.0 * u, _pose(r, lift=-0.8, yaw=sgn * 2.0, side=sgn * 6.0, roll=1.2 * math.sin(4 * math.pi * u), neck=(4, sgn * 16, 0),
                                        head=(0, sgn * 14, 0), jaw=2, tail=(2, -sgn * 16), feet=feet, hands=flex)))
        keyed(r, nm, 1.0, keys, loop=True)


def _lying(r, nod=0.0, breath=0.0, jaw=4.0, slump=1.0):
    """Down on its belly on the floor, its forelegs folded under its chest, its hind legs folded at its sides, its neck
    laid along the floor and its chin on it, its wings slumped open over its sides."""
    p = _pose(r, lift=-14.0 + breath, bend=-1.0 - breath, arch=-1, neck=(30 + nod, 0, 0), head=(-2 + nod * 0.3, 0, 0), jaw=jaw, tail=(-6, 12), curl=2,
              wings={"spread": 0.3 * slump, "lift": -14 * slump, "curl": 10 * slump},
              local={0: (0.0, 15.0, -10.0), 1: (0.0, 15.0, -10.0), 2: (0.0, 6.0, -12.0), 3: (0.0, 6.0, -12.0)},
              poles={0: (0.0, 0.6, 1.0), 1: (0.0, 0.6, 1.0), 2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}, hands={0: -60.0, 1: -60.0, 2: -70.0, 3: -70.0})
    return _floor(r, p, gap=0.3 - breath)


def _reactions(r):
    """Struck, it flinches with its head thrown up and back and its wings jerking half open; from the side it twists
    away from the blow. Broken, it sinks onto its belly with its neck laid along the floor and its wings slumped open,
    breathing hard; it gets up with a push of its forelegs and shakes its head and its wings."""
    home = _pose(r)
    hit = _pose(r, lift=-1.4, pitch=2, shift=(0, 1.5), bend=-3, neck=(-20, 0, 0), head=(-10, 8, 6), jaw=24, tail=(-6, 0), wings={"spread": 0.35, "lift": 12})
    back = _pose(r, lift=-0.6, bend=1, neck=(4, 0, 0), head=(4, -3, -2), jaw=6, tail=(-2, 0), wings={"spread": 0.08, "lift": 2})
    keyed(r, "hurt", 0.5, [(0, home), (0.07, hit), (0.28, back), (0.5, home)])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it twists away to its right
        h = _pose(r, lift=-1.2, roll=-5 * sgn, yaw=6 * sgn, side=10 * sgn, shift=(-sgn * 1.5, 0), neck=(-8, 22 * sgn, 0), head=(-6, 18 * sgn, -8 * sgn),
                  jaw=20, tail=(-6, 18 * sgn), wings={"l": {"spread": 0.3 if sgn > 0 else 0.1, "lift": 14 if sgn > 0 else 4},
                                                       "r": {"spread": 0.1 if sgn > 0 else 0.3, "lift": 4 if sgn > 0 else 14}})
        b = _pose(r, roll=1.5 * sgn, side=-3 * sgn, head=(2, -6 * sgn, 2 * sgn), jaw=4, tail=(0, -4 * sgn))
        keyed(r, nm, 0.55, [(0, home), (0.07, h), (0.3, b), (0.55, home)])
    crouch = _pose(r, lift=-6.0, pitch=2, bend=3, neck=(14, 0, 0), head=(4, 0, 0), jaw=10, tail=(-6, 0), wings={"spread": 0.15, "lift": -6},
                   feet={0: _foot(0, up=2.0, dz=-5.0), 1: _foot(1, up=2.0, dz=-5.0)}, hands={0: 30.0, 1: 30.0})
    lie = _lying(r)
    pant = lambda a: _lying(r, nod=2 + 2 * a, breath=0.7 * a, jaw=6 + 10 * a)
    keyed(r, "kneel", 1.3, [(0, home), (0.25, _mix(home, crouch, 0.6)), (0.5, crouch), (0.85, _mix(crouch, lie, 0.8)), (1.05, pant(1)), (1.3, lie)])
    push = _pose(r, lift=-9.0, pitch=-8, bend=-4, neck=(4, 0, 0), head=(-4, 0, 0), jaw=8, tail=(-4, 0), wings={"spread": 0.25, "lift": 10},
                 feet={0: _foot(0, dz=-6.0), 1: _foot(1, dz=-6.0)}, local={2: (0.0, 8.0, -10.0), 3: (0.0, 8.0, -10.0)},
                 poles={2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}, hands={2: -70.0, 3: -70.0})
    shake = _pose(r, lift=-0.6, neck=(-4, 6, 0), head=(-4, 10, 18), jaw=4, tail=(4, 8), wings={"spread": 0.3, "lift": 16, "beat": 0.0})
    keyed(r, "rise", 1.3, [(0, lie), (0.35, _floor(r, push)), (0.75, _mix(push, home, 0.7)), (1.0, shake), (1.3, home)])
    _death(r)


TILT = 1.0  # the roll bone's turn that lays it over onto its right side (its open left side up)


def _death(r):
    """Its last: its heart flares and gutters; it rears its head up and its wings half open, shaking; they fall; its
    forelegs fold and it goes down onto its chest; it rolls over onto its right side with its burnt-open left side up and
    a wing lying over it; it lifts its head once and lets it fall along the floor; the fire goes out of it and its
    wings crumble to their bones (they shrink to half)."""
    home = _pose(r)
    flare = _pose(r, lift=0.5, pitch=-4, bend=-4, neck=(-26, 0, 0), head=(-14, 0, 0), jaw=34, tail=(6, 0), wings={"spread": 0.55, "lift": 26})
    shake = lambda a: _pose(r, lift=0.0, pitch=-3, bend=-3, neck=(-20, 6 * a, 0), head=(-10, 8 * a, 10 * a), jaw=28, tail=(4, -6 * a),
                            wings={"spread": 0.5, "lift": 20 - 6 * a})
    fall = _pose(r, lift=-3.0, roll=4, side=6, bend=5, neck=(20, -8, 0), head=(14, -8, -6), jaw=16, tail=(-8, 0), wings={"spread": 0.25, "lift": -8},
                 feet={0: _foot(0, dx=3.0, dz=-2.0)})
    chest_down = _floor(r, _pose(r, lift=-10.0, pitch=14, bend=4, neck=(22, 0, 0), head=(6, 0, 0), jaw=12, tail=(-10, 0), wings={"spread": 0.3, "lift": -12},
                                 local={0: (0.0, 16.0, -14.0), 1: (0.0, 16.0, -14.0)}, poles={0: (0.0, 0.6, 1.0), 1: (0.0, 0.6, 1.0)}, hands={0: -60.0, 1: -60.0}))
    stiff = {k: (0.0, 22.0, -4.0 if k in FRONT else 4.0) for k in range(4)}
    droop = {0: (8.0, 18.0, -6.0), 2: (8.0, 18.0, 6.0), 1: (1.0, 21.0, -6.0), 3: (1.0, 21.0, 6.0)}

    def over(a, gap=0.3, nod=0.0, flop=0.0, breath=0.0, jaw=10.0):
        legs = {k: tuple(x + (y - x) * flop for x, y in zip(stiff[k], droop[k])) for k in range(4)}
        return _floor(r, _pose(r, tilt=-TILT * a, lift=-2.0, bend=-2 - breath, arch=-breath, neck=(nod, 0, 0), head=(nod * 0.5, 0, 6 * a / 88), jaw=jaw,
                               tail=(-6, 10), wings={"l": {"spread": 0.45, "lift": 30 * a / 90, "curl": 12}, "r": {"spread": 0.2, "lift": -20}},
                               local=legs, poles={k: POLE[k] for k in range(4)}, hands={k: 20.0 * flop for k in range(4)}), gap=gap)
    keys = [(0, home), (0.35, flare), (0.6, shake(1)), (0.8, shake(-1)), (1.0, shake(0.6)), (1.4, fall), (1.85, chest_down), (2.3, over(50, nod=6)),
            (2.6, over(90, nod=-6)), (2.75, over(86, gap=1.4, nod=-4)), (2.95, over(90, nod=-2)), (3.35, over(90, nod=-16, flop=0.5, jaw=18)),
            (3.7, over(90, nod=6, flop=0.8, jaw=8)), (4.1, over(90, nod=10, flop=1.0, breath=1.6, jaw=8)), (4.5, over(90, nod=10, flop=1.0, jaw=6)),
            (5.0, over(90, nod=11, flop=1.0, breath=1.0, jaw=5)), (5.5, over(90, nod=11, flop=1.0, jaw=5))]
    keyed(r, "death", 5.5, _unwrap(keys))
    for nm, _ in SIDES_:
        for k in range(3):
            _scales(r, "death", f"fing_{nm}{k}", [(3.6, 1.0), (5.0, 0.5 + 0.05 * k), (5.5, 0.5 + 0.05 * k)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b: 0 for k in range(4) for b in LIMB[k]}
NO_LAG.update({"jaw": 0, "roll": 0})


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def claw(r, k=0):
    """The Claw (LIGHT): it rears its front up off its left forefoot, the foot raised high with the claws spread and
    its head turned in, its wing on that side lifting; then it rakes the foot down across in front of it, its weight
    coming down after it."""
    s = SIDE[k]
    nm = "l" if s > 0 else "r"
    wg = lambda sp, lf: {nm: {"spread": sp, "lift": lf}, ("r" if nm == "l" else "l"): {"spread": 0.05, "lift": 2}}
    peel = _pose(r, lift=-0.8, pitch=-3, bend=-2, roll=-1 * s, side=-1 * s, neck=(-2, 3 * s, 0), head=(1, 3 * s, 0), jaw=6, tail=(1, -2 * s),
                 wings=wg(0.05, 4), feet={k: _foot(k, up=4, dz=0.5)}, hands={k: 25.0})
    # its weight goes back onto its other forefoot as it rears (never so far that leg locks straight)
    rise = _pose(r, lift=-1.6, pitch=-6, shift=(0, 1.2), bend=-6, roll=-2 * s, side=-4 * s, neck=(-10, 10 * s, 0), head=(5, 9 * s, 0), jaw=18,
                 tail=(5, -7 * s), wings=wg(0.22, 18), feet={k: _foot(k, up=16, dz=-4)}, hands={k: 55.0})
    top = _pose(r, lift=-2.5, pitch=-8, shift=(0, 2.0), bend=-9, roll=-3 * s, side=-6 * s, neck=(-14, 14 * s, 0), head=(8, 12 * s, 0), jaw=26,
                tail=(8, -10 * s), wings=wg(0.35, 28), feet={k: _foot(k, dx=s * 3, up=20, dz=-7)}, hands={k: 75.0})
    strike = _pose(r, lift=-2.0, pitch=2, bend=3, shift=(0, -2.0), roll=1 * s, side=2 * s, neck=(12, -4 * s, 0), head=(-4, -4 * s, 0), jaw=26,
                   tail=(8, 4 * s), wings=wg(0.35, 22), feet={k: (s * 2.0, 11.0, -29.0)}, hands={k: 20.0})
    hit = _pose(r, lift=-2.3, pitch=2.6, bend=3.6, shift=(0, -2.4), roll=1.5 * s, side=3 * s, neck=(14, -6 * s, 0), head=(-2, -6 * s, 0), jaw=10,
                tail=(8, 6 * s), wings=wg(0.3, 16), feet={k: (-s * 2.0, 8.4, -26.0)})
    held = _pose(r, lift=-2.4, pitch=2.8, bend=3.8, shift=(0, -2.4), roll=1.6 * s, side=3.2 * s, neck=(15, -6 * s, 0), head=(-1, -6 * s, 2 * s), jaw=8,
                 tail=(8, 7 * s), wings=wg(0.28, 14), feet={k: (-s * 2.0, 8.4, -26.0)})
    step = _pose(r, lift=-1.2, pitch=1, shift=(0, -1.0), neck=(4, 0, 0), jaw=4, feet={k: _foot(k, dx=-s * 1.0, up=5, dz=-5)}, hands={k: 40.0})
    return Move("claw", "LIGHT", 10, 4, 14, through=held, title="Claw", track=8.0, rng=(0, 4.5), kinds=("combo", "near"), damage=1.0, knock=0.7,
                hit=[(LIMB[k][2], (0, 6, -4), 6.0), (LIMB[k][1], (0, 10, 0), 4.5)], plant=(),
                stages={"windup": [(0.14, peel), (0.42, rise), (1.0, top)], "active": [(0.35, strike), (0.65, hit), (1.0, held)],
                        "recover": [(0.45, step), (0.72, _mix(step, _pose(r), 0.75))]}, lag=_lag())


def bite(r):
    """The Bite (LIGHT): it draws its neck back in an S, its head high and its jaws opening on the fire in its throat;
    it strikes down and forward with its whole neck and snaps its jaws shut on you."""
    coil = _pose(r, lift=-1.8, pitch=-5, shift=(0, 3.0), arch=-3, bend=-8, neck=(-52, 0, 0), head=(42, 0, 6), jaw=28, tail=(10, 0), curl=10,
                 wings={"spread": 0.4, "lift": 32, "curl": 6}, feet={0: _foot(0, dz=1.0), 1: _foot(1, dz=1.0)})
    strike = _pose(r, lift=-2.4, pitch=3, shift=(0, -3.0), bend=5, neck=(34, 0, 0), head=(-14, 0, 0), jaw=40, tail=(6, 0), wings={"spread": 0.42, "lift": 22, "curl": 8})
    snap = _pose(r, lift=-2.8, pitch=4, shift=(0, -3.6), bend=6, neck=(40, 0, 0), head=(-10, 0, 3), jaw=0, tail=(8, 0), wings={"spread": 0.38, "lift": 16, "curl": 10})
    held = _pose(r, lift=-2.9, pitch=4.2, shift=(0, -3.6), bend=6.2, neck=(41, 0, 0), head=(-8, 5, 6), jaw=3, tail=(8, 2), wings={"spread": 0.34, "lift": 13, "curl": 10})
    return Move("bite", "LIGHT", 10, 4, 14, through=held, title="Bite", track=8.0, rng=(0, 5.0), kinds=("combo", "near"), damage=1.1, knock=0.6,
                hit=[("head", (0, 0, -18), 6.0), ("jaw", (0, -2, -12), 5.0)], plant=(),
                stages={"windup": [(0.15, _mix(_pose(r), coil, 0.3)), (0.45, _mix(_pose(r), coil, 0.75)), (1.0, coil)], "active": [(0.45, strike), (0.75, snap), (1.0, held)],
                        "recover": _back(r, held)}, lag=_lag())


ADVANCE = 8.0  # blocks the Wing Rush carries it


def rush(r):
    """The Wing Rush (HEAVY, its gap closer): it crouches low with its wings opening and raised high over its back,
    holds, its eyes on you; it beats them down once, hard, and throws itself at you low over the floor, its forefeet
    reaching and its jaws wide, and lands on braced forefeet with its wings flung out."""
    def crouch(q=0.0):
        return _pose(r, lift=-6.0 - 0.6 * q, pitch=2, arch=3 + q, bend=3, shift=(0, 2.0), neck=(18, 0, 0), head=(-10, 0, 0), jaw=12 + 4 * q, tail=(-2, 0),
                     wings={"spread": 0.55 + 0.1 * q, "lift": 44 + 6 * q, "sweep": 10, "curl": 10},
                     feet={2: _foot(2, dz=-4), 3: _foot(3, dz=-4), 0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    beat = _pose(r, lift=0.0, pitch=-4, arch=-4, bend=-4, shift=(0, -3.0), neck=(20, 0, 0), head=(-14, 0, 0), jaw=30, tail=(10, 0),
                 wings={"spread": 0.95, "lift": -26, "sweep": 4, "curl": -6}, feet={2: _foot(2, dz=4), 3: _foot(3, dz=4)},
                 local={0: (0.0, 12.0, -12.0), 1: (0.0, 12.0, -11.0)}, hands={0: 40.0, 1: 40.0})
    fly = _pose(r, lift=7.0, pitch=0, arch=-6, bend=-4, shift=(0, -4.0), neck=(22, 0, 0), head=(-16, 0, 0), jaw=40, tail=(14, 0), curl=-6,
                wings={"spread": 0.9, "lift": 6, "sweep": 22, "curl": 10},
                local={0: (0.0, 11.0, -16.0), 1: (0.0, 11.0, -15.0), 2: (0.0, 14.0, 13.0), 3: (0.0, 14.0, 12.0)}, hands={0: 20.0, 1: 20.0, 2: -40.0, 3: -40.0})
    land = _pose(r, lift=-4.0, pitch=6, arch=4, bend=5, shift=(0, -2.0), neck=(22, 0, 0), head=(-6, 0, 0), jaw=0, tail=(10, 0),
                 wings={"spread": 0.8, "lift": 16, "sweep": -6, "curl": 14},
                 feet={0: _foot(0, dz=-9), 1: _foot(1, dz=-8), 2: _foot(2, up=3, dz=6), 3: _foot(3, up=3, dz=6)}, hands={2: -20.0, 3: -20.0})
    skid = _pose(r, lift=-4.4, pitch=4, arch=3, bend=4, shift=(0, -1.5), neck=(20, 0, 0), head=(-4, 0, 0), jaw=4, tail=(8, 0),
                 wings={"spread": 0.45, "lift": 8, "curl": 10}, feet={0: _foot(0, dz=-9), 1: _foot(1, dz=-8), 2: _foot(2, dz=3), 3: _foot(3, dz=3)})
    return Move("rush", "HEAVY", 18, 10, 22, through=skid, title="the Wing Rush", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 16), kinds=("gap",),
                damage=1.3, knock=1.3, hit=[("jaw", (0, -2, -12), 6.0), ("head", (0, 0, -18), 6.0), ("hand0", (0, 6, 0), 5.0), ("hand1", (0, 6, 0), 5.0)],
                plant=(), stages={"windup": [(0.3, _mix(_pose(r), crouch(), 0.5)), (0.7, crouch()), (0.85, crouch(1)), (1.0, crouch(0.5))],
                                  "active": [(0.2, beat), (0.6, fly), (1.0, land)], "recover": [(0.4, skid), (0.75, _mix(skid, _pose(r), 0.75))]}, lag=_lag())


def tail_sweep(r):
    """The Tail Sweep (for whoever is behind it): it looks back over its shoulder, its wing on that side lifting out of
    the way, and swings its hindquarters round, sweeping its heavy tail across behind it low over the floor."""
    cock = _pose(r, lift=-1.8, yaw=-16, roll=3, side=14, neck=(2, 40, 0), head=(4, 40, 10), jaw=14, tail=(6, -70), curl=6,
                 wings={"r": {"spread": 0.35, "lift": 30}, "l": {"spread": 0.05, "lift": 0}})
    hit = _pose(r, lift=-1.8, yaw=18, side=4, neck=(2, 34, 0), head=(4, 38, 8), jaw=16, tail=(-2, 54), curl=-2,
                wings={"r": {"spread": 0.2, "lift": 16}, "l": {"spread": 0.15, "lift": 12}})
    held = _pose(r, lift=-1.9, yaw=20, side=3, neck=(2, 34, 0), head=(4, 38, 8), jaw=12, tail=(-2, 58), curl=-2,
                 wings={"r": {"spread": 0.18, "lift": 14}, "l": {"spread": 0.14, "lift": 10}})
    return Move("sweep", "LIGHT", 10, 4, 14, through=held, title="Tail Sweep", track=0.0, rng=(0, 6.0), kinds=("behind",), damage=1.0, knock=1.2,
                hit=[("tail2", (0, 0, 6), 6.5), ("tail3", (0, 0, 6), 6.0), ("tail4", (0, 0, 8), 6.0)], plant=(),
                stages={"windup": [(0.35, _mix(_pose(r), cock, 0.5)), (1.0, cock)], "active": [(0.55, hit), (1.0, held)], "recover": _back(r, held)},
                lag=_lag())


def cinders(r):
    """Cinders: it draws its head back high with its neck curved and its jaws opening, the fire rising in its throat;
    and it spits, five times, its head snapping forward with each and a cinder flying from its jaws."""
    def spit(a):
        return _pose(r, lift=-0.6 + 0.6 * a, pitch=-2 + 3 * a, shift=(0, 1.2 - 2.0 * a), bend=-4 + 6 * a, neck=(-24 + 30 * a, 0, 0), head=(18 - 26 * a, 0, 0),
                     jaw=12 + 26 * a, tail=(-2 + 4 * a, 0), wings={"spread": 0.1, "lift": 6 + 4 * a})
    act = []
    for n in range(5):
        f0 = n / 5
        act += [(f0 + 0.06, spit(1.0)), (f0 + 0.17, spit(0.15))]
    act[-1] = (1.0, spit(0.0))
    return Move("cinders", "NONE", 14, 22, 14, through=_mix(spit(0), _pose(r), 0.3), title="Cinders", track=6.0, ability="Cinders", origin=(0.0, 4.4, 2.8),
                rng=(4, 20), kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.45, _mix(_pose(r), spit(0), 0.6)), (1.0, spit(0))], "active": act, "recover": _back(r, _mix(spit(0), _pose(r), 0.3))},
                lag=_lag())


def _rear(r, a, q=0.0, wings=None):
    """Reared up on its hind legs (a: how far, 0 to 1), its forelegs drawn up to its chest; q: its head thrown back."""
    knee = {0: (0.0, -0.3, 1.0), 1: (0.0, -0.3, 1.0)}
    return _pose(r, lift=2.0 + 4 * a, pitch=-26 * a - 4, arch=-5 * a, bend=-8 * a, neck=(-12 * a - 10 * q, 0, 0), head=(14 * a + 8 * q, 0, 0), jaw=10 + 22 * q,
                 tail=(-6 * a, 0), curl=6 * a, wings=wings or {"spread": 0.9 * a, "lift": 30 * a, "curl": 8},
                 feet={2: _foot(2, dz=-4 * a), 3: _foot(3, dz=-4 * a)}, reach={0: (0.0, -9.0 * a, -7.0 * a), 1: (0.0, -9.0 * a, -7.0 * a)},
                 poles=knee, hands={0: 70.0 * a, 1: 70.0 * a})


def cloud(r):
    """Ash Cloud: it rears up on its hind legs with its wings spread wide and high, and beats them down, twice, hard,
    driving the ash off them and up off the floor all round it; it comes down onto its forefeet."""
    up = lambda q: _rear(r, 0.85, q, wings={"spread": 1.0, "lift": 46, "sweep": -6, "curl": 6})
    down = lambda q: _rear(r, 0.7, q, wings={"spread": 0.95, "lift": -24, "sweep": 10, "curl": -8, "twist": 10})
    land = _pose(r, lift=-3.0, pitch=4, bend=4, shift=(0, -1.0), neck=(16, 0, 0), head=(-6, 0, 0), jaw=14, tail=(4, 0),
                 wings={"spread": 0.75, "lift": 4, "curl": 14}, feet={0: _foot(0, dz=-5), 1: _foot(1, dz=-5)})
    act = [(0.12, up(0.3)), (0.3, down(0.6)), (0.48, up(0.4)), (0.66, down(1.0)), (0.84, _mix(down(0.5), land, 0.5)), (1.0, land)]
    return Move("cloud", "NONE", 16, 26, 18, through=land, title="Ash Cloud", track=0.0, ability="Ash Cloud", rng=(0, 7.0), kinds=("area", "near"),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _rear(r, 0.5)), (1.0, up(0.0))], "active": act, "recover": [(0.4, _mix(land, _pose(r), 0.4)), (0.72, _mix(land, _pose(r), 0.82))]},
                lag=_lag())


HOVER = 34.0  # px Ashfall lifts it off the floor


def ashfall(r):
    """Ashfall (into the second phase): it crouches and springs up into the air on a great downbeat of its wings, and
    hangs there, beating them slowly, its legs hanging and its tail swinging under it; it shakes itself, the ash
    falling off it in a rain; and it drops back onto the floor on its four feet with its wings spread."""
    hang = {0: (0.0, 13.0, -6.0), 1: (0.0, 13.0, -6.0), 2: (0.0, 12.0, 4.0), 3: (0.0, 12.0, 4.0)}

    def hover(ph, q=0.0):
        b = math.sin(ph)
        return _pose(r, lift=HOVER + 3.0 * b, pitch=-8 - 2 * b, bend=-3, neck=(6 + 3 * b, 8 * q, 0), head=(-8, 10 * q, 16 * q), jaw=10 + 6 * abs(q),
                     tail=(-14 + 4 * b, 18 * q), curl=-4, roll=4 * q, twist=8 * q,
                     wings={"spread": 1.0, "lift": 8, "sweep": 6, "beat": 34, "ph": ph}, local=hang, hands={k: 30.0 for k in range(4)})
    crouch = _pose(r, lift=-6.0, pitch=2, bend=3, neck=(14, 0, 0), head=(-8, 0, 0), jaw=8, tail=(-2, 0), wings={"spread": 0.85, "lift": 46, "curl": 8},
                   feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2), 2: _foot(2, dz=-3), 3: _foot(3, dz=-3)})
    spring = _pose(r, lift=14.0, pitch=-10, bend=-4, neck=(4, 0, 0), head=(-6, 0, 0), jaw=12, tail=(-10, 0),
                   wings={"spread": 1.0, "lift": -30, "curl": -6}, local=hang, hands={k: 30.0 for k in range(4)})
    act = [(0.1, spring)]
    n = 18
    for i in range(1, n + 1):
        f = 0.1 + 0.75 * i / n
        q = math.sin(2 * math.pi * 3.0 * (i / n)) * _wave(i / n, 0.55, 0.5)  # the shake, mid-hover
        act.append((f, hover(2 * math.pi * 2.5 * (i / n) + math.pi / 2, q)))
    land = _pose(r, lift=-4.5, pitch=4, bend=4, neck=(16, 0, 0), head=(-4, 0, 0), jaw=12, tail=(4, 0), wings={"spread": 0.9, "lift": 14, "curl": 16},
                 feet={0: _foot(0, dz=-3), 1: _foot(1, dz=-3)})
    act.append((1.0, land))
    return Move("ashfall", "NONE", 14, 44, 18, through=land, title="Ashfall", track=0.0, ability="Ashfall", rng=(0, 22), kinds=("ranged", "area"),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, _mix(_pose(r), crouch, 0.6)), (1.0, crouch)], "active": act,
                        "recover": [(0.4, _mix(land, _pose(r), 0.35)), (0.72, _mix(land, _pose(r), 0.8))]}, lag=_lag())


def embers(r):
    """Embers: it hunches down with its neck low and its back up, and heaves, three times, like a cat bringing
    something up, its burnt-open side clenching, and embers drop out of it onto the floor; it shakes its head."""
    def heave(a):
        return _pose(r, lift=-3.0 - 1.0 * a, pitch=3 + 2 * a, arch=6 + 8 * a, bend=4 + 4 * a, roll=-2 * a, neck=(22 + 10 * a, 0, 0), head=(-6 - 10 * a, 0, 0),
                     jaw=10 + 26 * a, tail=(-6 - 4 * a, 0), wings={"spread": 0.1 + 0.15 * a, "lift": 6 + 14 * a})
    act = []
    for n, f in enumerate((0.12, 0.42, 0.72)):
        act += [(f, heave(1.0)), (f + 0.16, heave(0.2))]
    shake = _pose(r, lift=-1.0, neck=(6, 4, 0), head=(-2, 10, 18), jaw=8, tail=(2, 6), wings={"spread": 0.15, "lift": 8})
    act.append((1.0, shake))
    return Move("embers", "NONE", 12, 26, 16, through=shake, title="Embers", track=0.0, ability="Embers", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.5, _mix(_pose(r), heave(0), 0.6)), (1.0, heave(0.1))], "active": act, "recover": _back(r, shake)}, lag=_lag())


def nova(r):
    """Last Embers: it rears right up on its hind legs with its wings flung back and its head up, holds, and comes down
    on its forefeet with all its weight, its wings and its head flung out and its jaws wide as the embers burst out of
    every crack in it."""
    slam = _pose(r, lift=-5.0, pitch=7, arch=4, bend=6, shift=(0, -3.0), neck=(18, 0, 0), head=(-14, 0, 0), jaw=40, tail=(14, 0), curl=-6,
                 wings={"spread": 1.0, "lift": 10, "sweep": -8, "curl": -10}, feet={0: (13.5, 8.4, -26.0), 1: (-13.5, 8.4, -26.0)},
                 poles={0: (0.0, -0.2, 1.0), 1: (0.0, -0.2, 1.0)})
    held = _mix(slam, _pose(r), 0.12)
    return Move("nova", "HEAVY", 18, 6, 22, through=_mix(held, _pose(r), 0.2), title="Last Embers", hold=8, track=4.0, ability="Last Embers", rng=(0, 6.0),
                kinds=("area", "near"), damage=1.3, knock=1.4, disc=6.0, hit=[("hand0", (0, 6, 0), 7.0), ("hand1", (0, 6, 0), 7.0)], plant=(),
                stages={"windup": [(0.3, _rear(r, 0.5)), (0.7, _rear(r, 1.0, 0.4, wings={"spread": 0.95, "lift": 38, "sweep": 16, "curl": 6})),
                                   (1.0, _rear(r, 1.0, 1.0, wings={"spread": 1.0, "lift": 42, "sweep": 20, "curl": 4}))],
                        "active": [(0.5, _mix(_rear(r, 0.6, 0.6), slam, 0.5)), (1.0, slam)],
                        "recover": [(0.4, held), (0.72, _mix(held, _pose(r), 0.75))]}, lag=_lag())


def burnout(r):
    """Burnout (the signature, into the third phase): it wraps its wings round itself and curls down inside them, its
    neck and head drawn in and its tail curled round, trembling, its cracks and its heart burning brighter and brighter;
    then it throws its wings wide and its head up with its jaws open, and the fire goes out of it all at once, and it
    sags, smoking."""
    def wrapped(q=0.0, g=0.0):  # q: its tremble, g: how far the fire has built in it (it swells and strains as it does)
        return _pose(r, lift=-7.5 + 1.8 * g - 0.8 * abs(q), pitch=2, arch=6 - 3 * g, bend=5 - 3 * g, roll=2.5 * q, twist=3.0 * q,
                     neck=(30 - 10 * g, 4 * q, 0), head=(14 - 6 * g, 5 * q, 4 * q), jaw=4 + 8 * g, tail=(-6 + 4 * g, 34 - 6 * g), curl=10,
                     wings={"spread": 0.9, "lift": 70 - 6 * g + 4 * q, "sweep": -30 + 4 * g, "curl": 130 - 10 * g, "wrap": 80, "twist": -20},
                     feet={0: _foot(0, dz=-1), 1: _foot(1, dz=-1), 2: _foot(2, dz=-3), 3: _foot(3, dz=-3)})
    burst = _pose(r, lift=1.5, pitch=-8, arch=-4, bend=-8, neck=(-30, 0, 0), head=(-16, 0, 0), jaw=44, tail=(10, 0), curl=-6,
                  wings={"spread": 1.0, "lift": 34, "sweep": -4, "curl": -6}, feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    sag = _pose(r, lift=-4.0, pitch=3, bend=5, neck=(22, 0, 0), head=(4, 0, 0), jaw=10, tail=(-6, 0), wings={"spread": 0.55, "lift": -14, "curl": 18})
    n = 16
    act = [(0.65 * i / n, wrapped((-1) ** i * min(1.0, 0.3 + i / n), i / n)) for i in range(1, n + 1)]
    act += [(0.76, burst), (0.86, _mix(burst, sag, 0.3)), (1.0, sag)]
    return Move("burnout", "NONE", 16, 40, 20, through=sag, title="Burnout", signature=True, track=0.0, ability="Burnout", rng=(0, 9),
                kinds=("buff", "area"), damage=0, knock=0, plant=(),
                stages={"windup": [(0.45, _mix(_pose(r), wrapped(), 0.6)), (1.0, wrapped())], "active": act,
                        "recover": [(0.4, _mix(sag, _pose(r), 0.35)), (0.72, _mix(sag, _pose(r), 0.8))]}, lag=_lag())


def arrive(r):
    """Its intro: it lies curled like a burnt-out log with its wings over it and its chin on the floor; the embers in
    its cracks wake; it lifts its head and looks at you; it gets up, shakes the ash off itself, and spreads its wings
    wide, and folds them."""
    curled = _lying(r, nod=4, jaw=0, slump=0.6)
    look = _lying(r, nod=-30, jaw=4, slump=0.6)
    push = _pose(r, lift=-8.0, pitch=-6, bend=-4, neck=(0, 0, 0), head=(-4, 0, 0), jaw=6, tail=(-4, 0), wings={"spread": 0.25, "lift": 8},
                 feet={0: _foot(0, dz=-4.0), 1: _foot(1, dz=-4.0)}, local={2: (0.0, 8.0, -10.0), 3: (0.0, 8.0, -10.0)},
                 poles={2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}, hands={2: -60.0, 3: -60.0})
    shake = lambda a: _pose(r, lift=-0.8, roll=3 * a, twist=8 * a, neck=(-2, 6 * a, 0), head=(-4, 10 * a, 18 * a), jaw=6, tail=(4, -12 * a),
                            wings={"spread": 0.3, "lift": 18 + 6 * a})
    spread = _pose(r, lift=0.5, pitch=-4, bend=-4, neck=(-16, 0, 0), head=(-8, 0, 0), jaw=26, tail=(6, 0), wings={"spread": 1.0, "lift": 30, "curl": 4})
    lower = _pose(r, lift=-2.2, pitch=2, bend=3, neck=(14, 0, 0), head=(-4, 0, 0), jaw=8, tail=(2, 0), wings={"spread": 0.1, "lift": 4},
                  feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    return Move("arrive", "NONE", 14, 34, 14, through=_mix(lower, _pose(r), 0.2), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, _mix(_pose(r), curled, 0.7)), (1.0, curled)],
                        "active": [(0.14, curled), (0.26, look), (0.4, _floor(r, push)), (0.52, shake(1)), (0.58, shake(-1)), (0.64, shake(0.5)),
                                   (0.8, spread), (1.0, lower)], "recover": [(0.6, _mix(lower, _pose(r), 0.5))]}, lag=_lag())


# the ground (SPEC §35.10): the feet stay out of the floor (the per-tick solve, then a flex of the wrist or hock)
FOOT_GROUND = [(FOOT[k], [(LIMB[k][2], 0, 40)]) for k in range(4)]
HEAD_GROUND = [(["head", "jaw"], [("neck2", 0, 30, -1), ("head", 0, 30, -1)])]
TAIL_GROUND = [(["tail3", "tail4"], [("tail2", 0, 30, 1), ("tail3", 0, 30, 1)])]
STANDS = {"claw": (1, 2, 3), "bite": (0, 1, 2, 3), "rush": (), "sweep": (0, 1, 2, 3), "cinders": (0, 1, 2, 3), "cloud": (), "ashfall": (),
          "embers": (0, 1, 2, 3), "nova": (2, 3), "burnout": (), "arrive": ()}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [claw(r), bite(r), rush(r), tail_sweep(r), cinders(r), cloud(r), ashfall(r), embers(r), nova(r), burnout(r), arrive(r)]
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    everyday = [n for n in r.clips if n != "death"]
    for n in r.clips:
        _on_floor(r, n)
    core.ground_pass(r, FOOT_GROUND + TAIL_GROUND, clips=everyday)
    core.ground_pass(r, FOOT_GROUND + HEAD_GROUND + TAIL_GROUND, clips=["death", "kneel", "rise", "move_arrive"], skip=())
    return mv


# Its sounds (SPEC §35.8): the ender dragon's growls and wingbeats kept low, a blaze's breath and burning, a fire's
# crackle and a lava pop; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.ender_dragon.growl", 0.6, 1.3), ("entity.blaze.ambient", 0.8, 0.6)],
    "tell_heavy": [("entity.ender_dragon.growl", 0.9, 0.9), ("block.fire.ambient", 1.2, 0.7)],
    "tell_perilous": [("entity.ender_dragon.growl", 1.1, 0.6), ("entity.blaze.burn", 1.0, 0.5)],
    "swing_light": [("entity.ender_dragon.flap", 0.7, 1.2), ("entity.blaze.shoot", 0.4, 0.7)],
    "swing_heavy": [("entity.ender_dragon.flap", 1.2, 0.8), ("entity.ender_dragon.growl", 0.7, 1.1)],
    "cast": [("entity.blaze.shoot", 1.0, 0.7), ("block.fire.ambient", 1.4, 0.6)],
    "impact": [("entity.generic.explode", 0.6, 1.4), ("block.lava.pop", 1.2, 0.7)],
    "roar": [("entity.ender_dragon.growl", 1.3, 0.7), ("entity.blaze.burn", 1.0, 0.6)],
    "flap": [("entity.ender_dragon.flap", 1.4, 0.7), ("block.fire.extinguish", 0.5, 0.8)],
    "spit": [("entity.blaze.shoot", 1.1, 0.8), ("entity.ender_dragon.shoot", 0.6, 1.2)],
    "step": [("entity.ravager.step", 0.9, 0.7), ("block.lava.pop", 0.3, 0.9)],
}
MOVE_SOUNDS = {"cinders": {"swing": "spit", "impact": ""}, "cloud": {"swing": "flap", "impact": ""}, "ashfall": {"swing": "flap", "impact": ""},
               "embers": {"swing": "cast", "impact": ""}, "burnout": {"swing": "roar", "impact": ""}, "arrive": {"swing": "tell_light", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "arrive"},  # it lies burnt out, wakes, shakes off the ash and spreads its wings
    "phases": [
        {"pool": [
            {"move": "claw", "weight": 3, "when": "near", "then": [["bite"], ["bite", "rush"]]},
            {"move": "bite", "weight": 2, "when": "near"},
            {"move": "rush", "weight": 3, "when": "far", "then": [["claw"]]},
            {"move": "cinders", "weight": 3, "when": "far"},
            {"move": "cloud", "weight": 2, "when": "near"},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
        # the ash comes down, as in DeepBestiary: Ashfall, the embers it brings up, more cinders
        {"transition": "ashfall", "pool": [
            {"move": "claw", "weight": 3, "when": "near", "then": [["bite"], ["bite", "cloud"]]},
            {"move": "rush", "weight": 3, "when": "far", "then": [["claw", "bite"]]},
            {"move": "cinders", "weight": 3, "when": "far"},
            {"move": "ashfall", "weight": 1},
            {"move": "embers", "weight": 2},
            {"move": "cloud", "weight": 2, "when": "near"},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
        # burnout: it burns the last of itself, and the embers burst out of it
        {"transition": "burnout", "pool": [
            {"move": "nova", "weight": 3, "when": "near"},
            {"move": "claw", "weight": 3, "when": "near", "then": [["bite", "nova"]]},
            {"move": "rush", "weight": 3, "when": "far", "then": [["claw", "bite"]]},
            {"move": "ashfall", "weight": 2},
            {"move": "cinders", "weight": 2, "when": "far"},
            {"move": "burnout", "weight": 1},
            {"move": "sweep", "weight": 3, "when": "behind"},
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


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand0", (0, 0, 0)), ("hock2", (0, 0, 0)), ("head", (0, 0, 0)), ("wing_l1", (0, 0, 0)), ("wing_l2", (0, 0, 0)),
                 ("fing_l0", (40, 0, 0)), ("fing_l2", (34, 0, 0)), ("tail4", (0, 0, 12))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
