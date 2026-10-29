"""The Sovereign of Storms (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hollow Court's sixth Sovereign, a Flier. The Gale
Hound, picked from its silhouette round 2026-10-29: "It went so fast it left itself behind."

A great lean hound of the storm, its shoulder as high as a tall man, deep in the chest and tucked up at the waist on
long legs. Its hide is slate blue and veined with lightning, and a ridge of light crackles along its spine. A ruff of
storm cloud stands round its neck and over its shoulders, streaming back off it, and its tail is a long plume of cloud
streaming out behind it, the wind it left behind. Its eyes are white as arc lights, and lightning flickers in its throat.

Moves (SPEC §35.11): the Snap and the Rake (LIGHT, a string: its jaws, then a forepaw); the Bolt (HEAVY, its gap closer:
it crouches low with its hackles up, holds, and leaps a long way at you); Thunderclap (it rears up on its hind legs
like a fox on a mouse and comes down on its forepaws with all its weight); Lightning (it sits back and howls at the sky);
Storm Spears (it lowers its head and barks five times, a spear with each); Updraft (it whirls round on the spot after
its own tail); for whoever is behind it, the Tail Lash; and Overdrive (the signature: it shakes itself like a wet dog
from nose to tail, harder and harder, and the storm bursts off it). Its intro: it comes in at a gallop and skids to a
stop, and the wind it outran catches up and streams forward over it. Broken, it drops onto its belly with its forelegs
out before it and pants; it dies on its side, the storm going out of it.
"""
import math

from kit import core, ik, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

DEG = math.pi / 180.0
ID = "sovereign_storms"
SCALE = 2.4  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Gale Hound (docs/bosses/ROLLOUT.md, A). 61 px from its nose to its rump and 44 more of streaming tail; its
# withers 45 px up (2.8 blocks), its ears 61. Value story: the lightning brightest (its eyes, the ridge on its spine, the
# veins, its throat), then the pale storm cloud of its ruff and tail, then the slate blue of its hide, the dark of its
# muzzle, its lips and its claws darkest.
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT, BACK = (0, 1), (2, 3)
LIMB = {0: ("arm0", "fore0", "paw0"), 1: ("arm1", "fore1", "paw1"), 2: ("thigh2", "shin2", "hock2"), 3: ("thigh3", "shin3", "hock3")}
UPPER = {0: 12, 1: 12, 2: 12, 3: 12}
LOWER = {0: 12, 1: 12, 2: 14, 3: 14}
# the wrists and hocks at rest (x, up, z), each paw a hair over the floor: the forelegs nearly straight under the
# shoulders, the hind legs angled back from the stifle to the hock
REST_FOOT = {0: (5.5, 8.2, -15.0), 1: (-5.5, 8.2, -15.0), 2: (5.0, 10.2, 21.0), 3: (-5.0, 10.2, 21.0)}
POLE = {0: (0.0, 0.0, 1.0), 1: (0.0, 0.0, 1.0), 2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}  # the elbows back, the stifles forward
ROLL_H, ROLL_Z = 26.0, 2.0  # the roll bone at its body's middle, where it lies over
TAIL_W = (0.2, 0.25, 0.27, 0.28)  # a tail swing shared down the plume, more toward its end
PAW = {0: ["paw0"], 1: ["paw1"], 2: ["hock2"], 3: ["hock3"]}


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    rl = _bone(base, "roll", 0, ROLL_H, ROLL_Z)
    hips = _bone(rl, "hips", 0, 38 - ROLL_H, 12 - ROLL_Z)
    loin = _bone(hips, "loin", 0, 1, -10)
    chest = _bone(loin, "chest", 0, -1, -10)
    neck = _bone(chest, "neck", 0, 3, -9, (-38, 0, 0))
    head = _bone(neck, "head", 0, 1, -12, (36, 0, 0))
    jaw = _bone(head, "jaw", 0, -4, -5)
    # the rump, the tucked loin and the deep chest down to its elbows, a keel of brisket before it; a ridge of light
    # along the top of its back
    _box(hips, "rump", 0, -8, 1, 13, 14, 16)
    _box(hips, "ridge_h", 0, 6, 2, 2, 1, 14)
    _box(loin, "loin", 0, -3, 0, 10, 8, 12)
    _box(loin, "ridge_l", 0, 5, 0, 2, 1, 12)
    _box(chest, "chest", 0, -13, -3, 14, 19, 16)
    _box(chest, "brisket", 0, -14, -9, 10, 8, 6)
    _box(chest, "ridge_c", 0, 6, 0, 2, 1, 12)
    # the neck, a ruff of storm cloud round its base; the long head with a heavy brow, the muzzle, the nose, the fangs
    _box(neck, "neck", 0, -4, -6, 9, 10, 13)
    _box(neck, "ruff", 0, -7, -3, 15, 16, 9)
    _box(neck, "ruff_t", 0, 6, 1, 11, 4, 9)
    _box(neck, "ruff_b", 0, -10, -2, 9, 4, 7)
    _box(head, "skull", 0, -4, -2, 10, 9, 10)
    _box(head, "brow", 0, 3, -7, 10, 2, 2)
    _box(head, "muzzle", 0, -4, -11, 6, 5, 9)
    _box(head, "nose", 0, -0.5, -16, 4, 2, 1)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(head, "cheek_" + nm, s * 5.5, -6, 0, 3, 7, 6, mirror=s < 0)
        _box(head, "fang_" + nm, s * 2, -5, -14, 1, 1, 1)
        _box(jaw, "lfang_" + nm, s * 1.5, 0, -8.5, 1, 1, 1)
        e = _bone(head, "ear_" + nm, s * 3.5, 4.5, 1, (-62, 0, -s * 22))
        _box(e, "ear_" + nm, 0, 0, 0, 4, 6, 2, mirror=s < 0)
        _box(e, "eartip_" + nm, 0, 6, 0.5, 2, 2, 1)
    _box(jaw, "jaw", 0, -2, -4.5, 5, 2, 9)
    # the storm cloud streaming back off its neck and over its shoulders, loose on springs
    m0 = _bone(neck, "mane0", 0, 5, -7, (24, 0, 0))
    _box(m0, "mane0", 0, -1, 5, 8, 4, 12)
    m1 = _bone(chest, "mane1", 0, 6, -5, (8, 0, 0))
    _box(m1, "mane1", 0, -1, 5, 12, 5, 12)
    _box(m1, "mane1b", 0, 3, 9, 8, 3, 10)
    # the tail: a long plume of cloud widening as it streams out behind it
    t = _bone(hips, "tail0", 0, 4, 9, (-12, 0, 0))
    _box(t, "tail0", 0, -2.5, 4, 5, 5, 8)
    for k, (ln, w, h, rot) in enumerate(((10, 8, 7, (10, 0, 0)), (12, 10, 9, (6, 0, 0)), (10, 8, 7, (2, 0, 0))), start=1):
        t = _bone(t, f"tail{k}", 0, 0, 8 if k == 1 else (10 if k == 2 else 12), rot)
        _box(t, f"tail{k}", 0, -h / 2, ln / 2, w, h, ln)
        _box(t, f"tail{k}a", 0, h / 2 - 1, ln / 2 - 2, w - 3, 3, ln - 5)  # billows on top and beneath
        _box(t, f"tail{k}b", 0, -h / 2 - 1, ln / 2 + 1, w - 4, 2, ln - 4)
    _box(t, "tailtip", 0, -2, 12, 5, 4, 5)
    # the legs: the forelegs straight and slender with a sharp elbow; the haunches heavy, the hocks long; dark claws
    for k in range(4):
        s = SIDE[k]
        up_, lo_, en_ = LIMB[k]
        mir = s < 0
        if k in FRONT:
            a = _bone(chest, up_, s * 5.5, -7, -5)
            a.cube(up_, -3, -4, -3.5, 6, 16, 7, mirror=mir)
            f = a.bone(lo_, (0, UPPER[k], 0))
            f.cube(lo_, -2, 0, -2, 4, LOWER[k], 4, mirror=mir)
            f.cube(f"elbow{k}", -2.5, -1.5, -0.5, 5, 3, 4, mirror=mir)
            h = f.bone(en_, (0, LOWER[k], 0))
            h.cube(f"pastern{k}", -2, 0, -2, 4, 5, 4, mirror=mir)
        else:
            a = _bone(hips, up_, s * 5, -4, 3)
            a.cube(up_, -3.5, -4, -4.5, 7, 15, 9, mirror=mir)
            f = a.bone(lo_, (0, UPPER[k], 0))
            f.cube(lo_, -2, 0, -2.5, 4, LOWER[k], 5, mirror=mir)
            h = f.bone(en_, (0, LOWER[k], 0))
            h.cube(f"meta{k}", -2, 0, -2, 4, 7, 4, mirror=mir)
        top = 5 if k in FRONT else 7
        h.cube(f"pad{k}", -2.5, top, -4.5, 5, 3, 7, mirror=mir)
        h.cube(f"claw{k}", -2, top + 2, -5.5, 4, 1, 1, mirror=mir)
    for k in range(4):
        ik.rest_limb(r, *LIMB[k], REST_FOOT[k], POLE[k])
    fr0 = core.frames(r, {})
    for k in range(4):  # the paws set flat on the floor at rest
        end = LIMB[k][2]
        r.by_name[end].rot = tuple(v + w for v, w in zip(r.by_name[end].rot, ik.orient(r, fr0, end, (0, 1, 0), (1, 0, 0))))
        fr0 = core.frames(r, {})
    r.look = [("head", 0.5, 0.35), ("neck", 0.25, 0.2)]
    r.shadow = 2.4
    r.size = 1.0
    # secondary parts (SPEC §35.10): the ears, the cloud of its ruff and the plume of its tail stream loose
    r.springs = [("ear_l", 0.3, 3.0, 0.16), ("ear_r", 0.3, 3.0, 0.16), ("mane0", 0.45, 2.2, 0.16), ("mane1", 0.45, 2.0, 0.16),
                 ("tail0", 0.5, 1.8, 0.14), ("tail1", 0.55, 2.0, 0.15), ("tail2", 0.6, 2.2, 0.16), ("tail3", 0.6, 2.4, 0.18)]
    r.guard = [LIMB[k][2] for k in range(4)] + ["head", "jaw", "tail3"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

HIDE = Ramp("#0e121c", "#151b28", "#1d2536", "#273146", "#323e56", "#3f4c68", "#4e5c7a", "#60708e", "#74849f")
BELLY = Ramp("#2a3244", "#384256", "#48546a", "#5a6680", "#6e7a94", "#8490a8")
CLOUD = Ramp("#3e4658", "#545e72", "#6c768c", "#8690a4", "#a2abbc", "#bec6d3", "#d8dee7", "#edf1f6", "#fbfcfe")
DARK = Ramp("#050609", "#0a0c12", "#11141c", "#1a1e28", "#252a36")
BOLT = ("#ffffff", "#eef9ff", "#c8ecff", "#96d6ff", "#64bcff")
TOOTH = Ramp("#7c8090", "#a6aab6", "#cdd0d8", "#eceef2", "#ffffff")


def _veins(P_, cube, faces, seed, every=7, length=9):
    """Lightning in the hide: forked zig-zag lines of light across each face (lit on the glow layer too)."""
    rng = (seed * 7919 + 17) % 9973
    for fname in faces:
        f, gl = P_.face(cube, fname), P_.face(cube, fname, glow=True)
        n = max(1, (f.w * f.h) // (every * every * 2))
        for v in range(n):
            rng = (rng * 1103 + 12345) % 9973
            i, j = rng % f.w, (rng // 7) % max(1, f.h // 2)
            for step in range(length):
                if not (0 <= i < f.w and 0 <= j < f.h):
                    break
                hot = step < 2
                f.set(i, j, hexc(BOLT[0] if hot else BOLT[2], 255))
                gl.set(i, j, hexc(BOLT[1] if hot else BOLT[3], 200 if hot else 140))
                rng = (rng * 1103 + 12345) % 9973
                i += (-1, 1, 1, -1, 0)[rng % 5]
                j += 1
                if rng % 11 == 0 and step > 2:  # a fork
                    fi, fj = i + 1, j
                    for _ in range(3):
                        if 0 <= fi < f.w and 0 <= fj < f.h:
                            f.set(fi, fj, hexc(BOLT[3], 255))
                            gl.set(fi, fj, hexc(BOLT[4], 110))
                        fi, fj = fi + 1, fj + 1


def paint(r, P_):
    """Slate-blue hide darkening along its back and paling to a grey-blue belly, veined with lightning; a white-hot ridge
    of light along its spine; storm cloud for its ruff, its cheeks and the plume of its tail, pale on top and dark
    beneath with a few sparks in it; a dark muzzle and lips, a black nose, dark claws; white fangs; eyes white as arc
    lights under the heavy brow; lightning in its throat."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    hide = ["rump", "loin", "chest", "brisket", "neck", "skull"] + of("arm", "fore", "elbow", "thigh", "shin", "pastern", "meta")
    for k, c in enumerate(hide):
        P_.paint(c, HIDE, 5.4, None, grad=1.4, rim=1.2, ao=1.0, noise=0.2, nscale=3)
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, BELLY.at(2.8 + 0.6 * ((i * 3 + j) % 3 == 0)))
        _veins(P_, c, ("west", "east"), k + 1, every=6 if c in ("rump", "chest") else 5, length=8 if c in ("rump", "chest") else 5)
    for c in ("ridge_h", "ridge_l", "ridge_c"):
        for fname in SIDES + ("top",):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                hot = (i + j) % 3 != 0
                f.set(i, j, hexc(BOLT[0] if hot else BOLT[2], 255))
                gl.set(i, j, hexc(BOLT[1] if hot else BOLT[3], 230 if hot else 160))
    for c in ["ruff", "ruff_t", "ruff_b", "mane0", "mane1", "mane1b"] + of("cheek_", "tail"):
        P_.paint(c, CLOUD, 6.0, None, grad=1.8, rim=1.4, ao=1.4, noise=0.35, nscale=2)
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, CLOUD.at(2.4 + 0.8 * ((i + 2 * j) % 4 == 0)))
        for fname in ("west", "east", "top"):  # billows: lighter puffs and darker hollows in the cloud
            f = P_.face(c, fname)
            for i, j in f.each():
                q = (i * 5 + j * 3) % 7
                if q == 0:
                    f.set(i, j, CLOUD.at(7.6))
                elif q == 4:
                    f.set(i, j, CLOUD.at(3.6))
    for c in of("tail2", "tail3", "mane1"):  # sparks caught in the cloud
        for fname in ("west", "east", "top"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                if (i * 7 + j * 13) % 23 == 0:
                    f.set(i, j, hexc(BOLT[1], 255))
                    gl.set(i, j, hexc(BOLT[2], 170))
    P_.paint("muzzle", HIDE, 4.2, None, grad=1.0, rim=1.2, ao=0.6, noise=0.15, nscale=2)
    P_.paint("jaw", HIDE, 3.4, None, grad=0.8, rim=1.0, ao=0.6, noise=0.15, nscale=2)
    for c in of("ear_"):
        P_.paint(c, HIDE, 4.6, None, grad=1.0, rim=1.4, ao=0.6, noise=0.15, nscale=2)
        f, gl = P_.face(c, "north"), P_.face(c, "north", glow=True)
        for i, j in f.each():  # the inside of the ear: dark, a faint blue light deep in it
            inner = 0 < i < f.w - 1 and j > 0
            f.set(i, j, hexc(BOLT[4], 255) if inner and j > 2 else DARK.at(3.0) if inner else HIDE.at(4.0))
            if inner and j > 2:
                gl.set(i, j, hexc(BOLT[4], 90))
    P_.paint("brow", HIDE, 3.6, None, grad=1.0, rim=1.4, ao=0.8, noise=0.15, nscale=2)
    for c in ["nose"] + of("claw", "pad"):
        P_.paint(c, DARK, 2.0, None, grad=0.6, rim=1.6, ao=0.3, noise=0.1, nscale=2)
    for c in of("eartip_"):
        P_.paint(c, CLOUD, 7.0, None, grad=0.6, rim=0.6, ao=0.2)
    for c in of("fang_", "lfang_"):
        P_.paint(c, TOOTH, 3.6, None, grad=0.5, rim=0.8, ao=0.2)
    # the throat and the inside of the mouth: dark, lightning flickering in it
    for c, fname in (("jaw", "top"), ("muzzle", "bottom")):
        f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
        for i, j in f.each():
            lit = (i + j) % 3 == 0
            f.set(i, j, hexc(BOLT[2] if lit else "#1a1430", 255))
            if lit:
                gl.set(i, j, hexc(BOLT[3], 180))
    _face(P_)
    return P_.skin, P_.glow


def _face(P_):
    """The head from the front: eyes white as arc lights set deep under the brow, a darker mask down the muzzle, the
    lips black."""
    f, gl = P_.face("skull", "north"), P_.face("skull", "north", glow=True)
    W = f.w
    for i, j in f.each():
        m = min(i, W - 1 - i)  # 0 at either edge; the muzzle covers m >= 2 below row 4
        if j in (2, 3) and m in (0, 1):
            hot = m == 1
            f.set(i, j, hexc(BOLT[0] if hot else BOLT[2], 255))
            gl.set(i, j, hexc(BOLT[0] if hot else BOLT[2], 255))
        elif j in (1, 4) and m in (0, 1, 2):
            f.set(i, j, DARK.at(1.4))  # the socket, shadowed under the brow
        elif j in (2, 3) and m == 2:
            f.set(i, j, DARK.at(2.0))
        elif j == 0:
            f.set(i, j, HIDE.at(2.8))
        else:
            f.set(i, j, HIDE.at(4.0))
    for c in ("west", "east"):  # the eyes seen from the side, a spark at the corner of the skull
        f, gl = P_.face("skull", c), P_.face("skull", c, glow=True)
        for ii in ((0, 1) if c == "west" else (f.w - 1, f.w - 2)):
            for jj in (2, 3):
                f.set(ii, jj, hexc(BOLT[1] if jj == 2 else BOLT[2], 255))
                gl.set(ii, jj, hexc(BOLT[1] if jj == 2 else BOLT[2], 220))
    f = P_.face("muzzle", "north")
    for i, j in f.each():  # the end of the muzzle round its nose, the lips black below
        f.set(i, j, DARK.at(1.0) if j >= f.h - 1 else HIDE.at(3.0))
    f = P_.face("muzzle", "top")
    for i, j in f.each():  # a darker mask down the bridge of the nose, wrinkled in a snarl near its end
        mid = 1 <= i <= f.w - 2
        col = HIDE.at(3.2 if mid else 4.2)
        if mid and j < 3 and (j + i) % 2 == 0:
            col = HIDE.at(2.0)
        f.set(i, j, col)
    for c in ("west", "east"):
        f = P_.face("muzzle", c)
        for i, j in f.each():
            if j == f.h - 1:
                f.set(i, j, DARK.at(1.0))
    f = P_.face("brow", "bottom")
    for i, j in f.each():
        f.set(i, j, DARK.at(2.0))


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


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), arch=0.0, bend=0.0, side=0.0, twist=0.0, neck=(0, 0, 0), head=(0, 0, 0),
          jaw=0.0, ears=(0.0, 0.0), tail=(0.0, 0.0), mane=0.0, tilt=0.0, sink=0.0, spin=0.0, feet=None, reach=None, local=None, poles=None, hands=None,
          limbs=None):
    """lift (+ up), pitch (+ its rump's front dipping, its rear up), yaw (+ to its right), roll (+ its left side down) and
    shift (x, z) move its hips; arch (+ the loin's front dipping: the back rounding up, gathered) turns its loin on its
    hips; bend (+ the front dipping), side (+ to its right) and twist turn its chest on its loin; neck and head (nod + down,
    turn + to its right, tilt) turn on top of their rest; jaw opens its mouth (degrees); ears (left, right: + forward,
    - flat back); tail (lift + up, sweep + to its left) swings its plume; mane (+ raised: its hackles up); tilt and sink
    turn and lower it on its roll bone, spin turns all of it round on the spot (yaw). feet {leg: (x, height, z)}: where
    the wrists and hocks go (the rest stand where they stand at rest); reach {leg: (dx, dup, dz)}: the same, from where
    the leg's root is posed; local {leg: (x, down, z)}: from the root in its hips' own axes, however it lies; limbs {leg:
    {bone: turn}} sets a leg outright; hands {leg: "free" | degrees}: that paw hangs as its leg leaves it, or curled back
    so far (flat on the floor if not given)."""
    p = {"roll": {"rot": (0, spin, tilt), "pos": (0, -sink, 0)},
         "hips": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "loin": (arch, side * 0.4, twist * 0.4), "chest": (bend, side * 0.6, twist * 0.6), "neck": tuple(neck), "head": tuple(head),
         "jaw": (jaw, 0, 0), "ear_l": (-ears[0], 0, 0), "ear_r": (-ears[1], 0, 0), "mane0": (mane, 0, 0), "mane1": (0.6 * mane, 0, 0)}
    for i, w in enumerate(TAIL_W):
        p[f"tail{i}"] = (tail[0] * w, tail[1] * w, 0)
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
    """The paws that stand through a move keep where they stand on the floor, every tick, and lie flat on it heading
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
    """Between keys a paw can sag a hair into the floor (its leg's turns blend, not its path): wherever one would, its
    leg is solved again that tick to set it on the floor where it was, the joint its own way and the paw turned as it
    was."""
    floor = core.GROUND - 0.3
    still = {"pos": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1)}

    def fn(t, smp, fr):
        out = {}
        for k in range(4):
            up, lo, en = LIMB[k]
            v = core.lowest(r, fr, PAW[k])
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


def _spin(r, clip, bone, axis, turns, t0, t1):
    """Turns a bone round whole turns on one axis between t0 and t1, eased, on top of its keys (a key a tick)."""
    c = r.clips[clip]
    old = c.tracks.get((bone, "rot"))
    keys = []
    for k in range(int(round(c.length / core.TICK)) + 1):
        t = min(c.length, k * core.TICK)
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        f = 0.0 if t <= t0 else 1.0 if t >= t1 else core.ease_io((t - t0) / (t1 - t0))
        base[axis] += 360.0 * turns * f
        keys.append((t, tuple(base), "cat"))
    c.tracks[(bone, "rot")] = keys


def _scales(r, clip, bone, pts):
    """A clip's scale keys for one bone: [(seconds, scale)], 1 at both ends, straight lines between."""
    c = r.clips[clip]
    ks = {0.0: 1.0, c.length: 1.0}
    ks.update((round(t, 4), v) for t, v in pts)
    c.scale(bone, [(t, v, v, v) for t, v in sorted(ks.items())], "lin")


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It stands on its long legs and pants (6 s), its flanks heaving and its jaw hanging a little open, lightning in its
    throat; it looks off to one side with its ears pricked and its mouth shut; it puts its nose down and sniffs the floor
    in quick bobs, its plume sweeping; one ear flicks; it lifts its head and looks the other way, and a forepaw shifts
    its weight; its plume streams and sways all the while."""
    L, n = 6.0, 120
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        t = L * u
        look_l = _wave(u, 0.17, 0.3)
        sniff = _wave(u, 0.47, 0.3)
        look_r = _wave(u, 0.8, 0.3)
        alert = max(look_l, look_r)
        pant = (1 - alert) * (1 - sniff)
        jaw = 7.0 * pant + 2.5 * pant * math.sin(2 * math.pi * 3.0 * t)
        breath = math.sin(2 * math.pi * (3.0 if pant > 0.5 else 0.6) * t) * (0.5 + 0.5 * pant)
        bob = math.sin(2 * math.pi * 4.0 * t) * sniff
        flick = _wave(u, 0.63, 0.06)
        ear_l = 10 * alert - 10 * pant - 40 * flick
        ear_r = 10 * alert - 10 * pant
        shuffle = _wave(u, 0.88, 0.12)
        feet = {0: _foot(0, up=4.0 * shuffle, dz=-1.5 * shuffle)} if shuffle > 0.01 else None
        keys.append((t, _pose(r, lift=-0.3 + 0.25 * breath, pitch=0.3 * breath, roll=0.8 * math.sin(w), arch=0.6 * breath, bend=0.8 * breath - 0.5 * sniff,
                              side=2.0 * math.sin(w + 0.4) + 6 * look_l - 6 * look_r,
                              neck=(28 * sniff - 6 * alert, 14 * look_l - 14 * look_r, 0), head=(10 * sniff + 3 * bob - 2 * alert, 26 * look_l - 26 * look_r, 4 * look_l - 4 * look_r),
                              jaw=jaw, ears=(ear_l, ear_r), tail=(4 + 3 * math.sin(w), 10 * math.sin(w + 0.6) + 8 * sniff * math.sin(6 * w)), mane=2 * alert,
                              feet=feet, hands={0: 40 * shuffle} if feet else None)))
    keyed(r, "idle", L, keys, loop=True)


WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # its walk: each paw by turns, back left, front left, back right, front right
GALLOP = {2: 0.0, 3: 0.08, 1: 0.55, 0: 0.63}  # its run: a hound's gallop, the hind pair and then the fore pair, two flights a stride


def _gait(u, stride_px, beta, lift, groups):
    """The paws through a stride at u: down, each moves back as far as it travels, so it stays put on the floor; up, it
    lifts and reaches forward again, the forepaws curling back under."""
    feet, flex = {}, {}
    st = stride_px * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
            flex[k] = 60.0 * math.sin(math.pi * f) ** 1.5 if k in FRONT else 25.0 * math.sin(math.pi * f) ** 1.5
    return feet, flex


def _locomotion(r):
    """It walks like a big hound, loose and easy, a paw at a time, its head nodding with each forepaw and its plume
    swaying. It runs in a hound's gallop: its back bends and stretches with every stride, gathering its hind legs up
    under it and flinging its forelegs out, two flights a stride, its head low and level, its ears flat back and its
    ruff and plume streaming."""
    for name, L, stride, beta, lift, groups in (("walk", 1.2, 1.3, 0.68, 5.0, WALK), ("run", 0.6, 3.4, 0.3, 9.0, GALLOP)):
        sp = stride * 16
        keys = []
        n = 30
        for k in range(n + 1):
            u = k / n
            s = math.sin(2 * math.pi * u)
            feet, flex = _gait(u, sp, beta, lift, groups)
            if name == "walk":
                nod = math.sin(4 * math.pi * u + 0.6)
                keys.append((L * u, _pose(r, lift=-0.6 - 0.4 * math.cos(4 * math.pi * u), pitch=0.6 * math.sin(4 * math.pi * u), roll=1.4 * s, yaw=1.2 * s,
                                          bend=0.6 * math.sin(4 * math.pi * u + 1.2), side=3.0 * s, neck=(6 + 2 * nod, -3 * s, 0), head=(-4 + 2.5 * nod, -4 * s, 0),
                                          jaw=3.0, ears=(-6, -6), tail=(2 + 2 * math.sin(4 * math.pi * u), 9 * math.sin(2 * math.pi * u - 0.5)),
                                          feet=feet, hands=flex)))
            else:
                g = math.cos(2 * math.pi * (u - 0.97))  # +1 gathered (just before the hind paws land), -1 stretched out
                fl = math.cos(4 * math.pi * (u - 0.47))  # up in either flight
                keys.append((L * u, _pose(r, lift=-1.2 + 1.6 * fl, pitch=-7 * g, arch=9 * g, bend=5 * g, roll=1.0 * s, side=1.5 * s,
                                          neck=(26 + 4 * g, 0, 0), head=(-18 - 3 * g, 0, 0), jaw=8.0 + 4 * g, ears=(-36, -36),
                                          tail=(16 - 6 * g, 5 * s), mane=-8, feet=feet, hands=flex)))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.3, 3.4
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        A = 16.0 * DEG
        for k in range(n + 1):
            u = k / n
            feet, flex = {}, {}
            for j, ph in WALK.items():  # each paw steps round the way it turns while the others hold
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
            keys.append((0.9 * u, _pose(r, lift=-0.6, yaw=sgn * 2.0, side=sgn * 6.0, roll=1.0 * math.sin(4 * math.pi * u), neck=(4, sgn * 10, 0),
                                        head=(0, sgn * 14, 0), jaw=3, tail=(2, -sgn * 12), feet=feet, hands=flex)))
        keyed(r, nm, 0.9, keys, loop=True)


def _lying(r, nod=4.0, breath=0.0, jaw=6.0):
    """Down on its belly on the floor as a dog lies, its forelegs stretched out before it, its hind legs folded under,
    its head up between its forepaws."""
    p = _pose(r, lift=-17.0 + breath, bend=-2.0 - breath, arch=-2, neck=(-6 + nod, 0, 0), head=(4 + nod, 0, 0), jaw=jaw, ears=(-14, -14), tail=(-8, 14),
              local={0: (0.0, 20.0, -20.0), 1: (0.0, 20.0, -20.0), 2: (0.0, 6.0, -10.0), 3: (0.0, 6.0, -10.0)},
              poles={0: (0.0, 0.6, 1.0), 1: (0.0, 0.6, 1.0), 2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}, hands={0: -70.0, 1: -70.0, 2: -70.0, 3: -70.0})
    return _floor(r, p, gap=0.3 - breath)


def _reactions(r):
    """Struck, it yelps, its head thrown up and away and its plume tucked; from the side it twists away from the blow.
    Broken, it drops onto its belly with its forelegs out before it and lies there panting, its head low; it gets up
    with a push of its forelegs and a heave of its rear, and shakes its head."""
    home = _pose(r)
    hit = _pose(r, lift=-1.2, pitch=2, shift=(0, 1.0), bend=-3, neck=(-14, 0, 0), head=(-12, 10, 8), jaw=26, ears=(-30, -30), tail=(-14, 0), mane=6)
    back = _pose(r, lift=-0.6, bend=1, neck=(4, 0, 0), head=(4, -3, -2), jaw=6, ears=(-10, -10), tail=(-4, 0))
    keyed(r, "hurt", 0.5, [(0, home), (0.07, hit), (0.28, back), (0.5, home)])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it twists away to its right
        h = _pose(r, lift=-1.0, roll=-5 * sgn, yaw=6 * sgn, side=10 * sgn, shift=(-sgn * 1.5, 0), neck=(-6, 16 * sgn, 0), head=(-6, 20 * sgn, -8 * sgn),
                  jaw=20, ears=(-26, -26), tail=(-10, 16 * sgn), mane=4)
        b = _pose(r, roll=1.5 * sgn, side=-3 * sgn, head=(2, -6 * sgn, 2 * sgn), jaw=4, tail=(0, -4 * sgn))
        keyed(r, nm, 0.55, [(0, home), (0.07, h), (0.3, b), (0.55, home)])
    crouch = _pose(r, lift=-6.0, pitch=2, bend=3, neck=(6, 0, 0), head=(6, 0, 0), jaw=10, ears=(-20, -20), tail=(-10, 0),
                   feet={0: _foot(0, up=2.0, dz=-6.0), 1: _foot(1, up=2.0, dz=-6.0)}, hands={0: 30.0, 1: 30.0})
    lie = _lying(r)
    pant = lambda a: _lying(r, nod=2 + a, breath=0.6 * a, jaw=8 + 8 * a)
    keyed(r, "kneel", 1.3, [(0, home), (0.25, _mix(home, crouch, 0.6)), (0.5, crouch), (0.85, _mix(crouch, lie, 0.8)), (1.05, pant(1)), (1.3, lie)])
    push = _pose(r, lift=-11.0, pitch=-10, bend=-4, neck=(-4, 0, 0), head=(-4, 0, 0), jaw=8, ears=(-10, -10), tail=(-4, 0),
                 feet={0: _foot(0, dz=-8.0), 1: _foot(1, dz=-8.0)}, local={2: (0.0, 8.0, -10.0), 3: (0.0, 8.0, -10.0)},
                 poles={2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)}, hands={2: -70.0, 3: -70.0})
    shake = _pose(r, lift=-0.6, neck=(-4, 0, 0), head=(-4, 10, 16), ears=(6, -14), tail=(4, 8))
    keyed(r, "rise", 1.3, [(0, lie), (0.35, _floor(r, push)), (0.75, _mix(push, home, 0.7)), (1.0, shake), (1.3, home)])
    _death(r)


TILT = 1.0  # the roll bone's turn that lays it over onto its left side


def _death(r):
    """Its last: it staggers, its head swinging low and its legs splaying; its forelegs fold and it goes down on its
    chest; it rolls over onto its side, its legs out from it; it lifts its head once and lets it fall; its chest heaves
    twice and stops, and the storm goes out of it: its ruff and its plume shrink and settle."""
    home = _pose(r)
    stagger = _pose(r, lift=-2.0, roll=6, side=8, bend=4, neck=(16, 10, 0), head=(10, 12, 10), jaw=14, ears=(-24, -24), tail=(-12, 0),
                    feet={1: _foot(1, dx=-3.0, dz=-3.0)})
    sway = _pose(r, lift=-3.0, roll=-5, side=-6, bend=5, neck=(20, -8, 0), head=(14, -10, -8), jaw=18, ears=(-26, -26), tail=(-14, 0),
                 feet={0: _foot(0, dx=3.0, dz=-2.0)})
    chest_down = _floor(r, _pose(r, lift=-10.0, pitch=16, bend=4, neck=(14, 0, 0), head=(8, 0, 0), jaw=12, ears=(-24, -24), tail=(-14, 0),
                                 local={0: (0.0, 18.0, -16.0), 1: (0.0, 18.0, -16.0)}, poles={0: (0.0, 0.6, 1.0), 1: (0.0, 0.6, 1.0)}, hands={0: -60.0, 1: -60.0}))
    stiff = {k: (0.0, 24.0, -4.0 if k in FRONT else 4.0) for k in range(4)}
    droop = {0: (-8.0, 20.0, -6.0), 2: (-8.0, 20.0, 6.0), 1: (-1.0, 23.0, -6.0), 3: (-1.0, 23.0, 6.0)}

    def over(a, gap=0.3, nod=0.0, flop=0.0, breath=0.0, jaw=10.0):
        legs = {k: tuple(x + (y - x) * flop for x, y in zip(stiff[k], droop[k])) for k in range(4)}
        return _floor(r, _pose(r, tilt=TILT * a, lift=-2.0, bend=-2 - breath, arch=-breath, neck=(nod, 0, 0), head=(nod * 0.5, 0, -6 * a / 88), jaw=jaw,
                               ears=(-30, -30), tail=(-6, -10), local=legs, poles={k: POLE[k] for k in range(4)},
                               hands={k: 20.0 * flop for k in range(4)}), gap=gap)
    keys = [(0, home), (0.4, stagger), (0.8, sway), (1.25, chest_down), (1.65, over(50, nod=4)), (1.95, over(90, nod=-6)), (2.1, over(86, gap=1.4, nod=-4)),
            (2.3, over(90, nod=-2)), (2.7, over(90, nod=-14, flop=0.5, jaw=16)), (3.05, over(90, nod=4, flop=0.8, jaw=8)), (3.5, over(90, nod=8, flop=1.0, breath=2.0, jaw=8)),
            (3.9, over(90, nod=8, flop=1.0, jaw=6)), (4.3, over(90, nod=8, flop=1.0, breath=1.6, jaw=6)), (4.8, over(90, nod=9, flop=1.0, jaw=5)),
            (5.2, over(90, nod=9, flop=1.0, jaw=5))]
    keyed(r, "death", 5.2, _unwrap(keys))
    for b, end in (("mane0", 0.55), ("mane1", 0.6), ("tail1", 0.6), ("tail2", 0.5), ("tail3", 0.4)):
        _scales(r, "death", b, [(3.4, 1.0), (4.8, end), (5.2, end)])



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


def snap(r):
    """The Snap (LIGHT): it lowers its head and draws back on its haunches, its lips up off its fangs and its hackles
    rising; then it lunges in from the shoulders with its jaws wide and snaps them shut on you."""
    gather = _pose(r, lift=-4.0, pitch=-3, shift=(0, 3.5), arch=3, bend=3, neck=(20, 0, 0), head=(6, 0, 0), jaw=16, ears=(-34, -34), tail=(-8, 0), mane=20)
    lunge = _pose(r, lift=-2.4, pitch=3, shift=(0, -4.5), bend=4, neck=(20, 0, 0), head=(-12, 0, 0), jaw=38, ears=(-32, -32), tail=(6, 0), mane=12)
    bite = _pose(r, lift=-2.6, pitch=4, shift=(0, -5.5), bend=5, neck=(22, 0, 0), head=(-8, 0, 2), jaw=0, ears=(-32, -32), tail=(8, 0), mane=12)
    held = _pose(r, lift=-2.7, pitch=4.2, shift=(0, -5.6), bend=5.2, neck=(23, 0, 0), head=(-6, 4, 6), jaw=2, ears=(-30, -30), tail=(8, 2), mane=12)
    return Move("snap", "LIGHT", 9, 4, 14, through=held, title="Snap", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.0, knock=0.6,
                hit=[("head", (0, 1.5, -14), 5.0), ("jaw", (0, 1, -8), 4.0)], plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), gather, 0.6)), (1.0, gather)], "active": [(0.45, lunge), (0.75, bite), (1.0, held)],
                        "recover": _back(r, held)}, lag=_lag())


def rake(r, k=0, mid="rake"):
    """The Rake (LIGHT): it rears its front up off one forepaw, the paw raised high with its claws out and its head
    turned in; then it rakes the paw down across in front of it, its weight coming down after it."""
    s = SIDE[k]
    rise = _pose(r, lift=-0.6, pitch=-9, bend=-5, roll=-2 * s, side=-3 * s, neck=(-4, 6 * s, 0), head=(4, 8 * s, 0), jaw=18, ears=(-20, -20), tail=(4, -6 * s), mane=10,
                 feet={k: _foot(k, up=17, dz=-5)}, hands={k: 55.0})
    top = _pose(r, lift=-0.4, pitch=-12, bend=-7, roll=-3 * s, side=-4 * s, neck=(-6, 8 * s, 0), head=(6, 10 * s, 0), jaw=22, ears=(-24, -24), tail=(6, -8 * s), mane=12,
                feet={k: _foot(k, dx=s * 2, up=21, dz=-8)}, hands={k: 70.0})
    strike = _pose(r, lift=-2.0, pitch=3, bend=3, shift=(0, -2.5), roll=1 * s, side=2 * s, neck=(10, -2 * s, 0), head=(-4, -4 * s, 0), jaw=26, ears=(-28, -28),
                   tail=(8, 4 * s), mane=12, feet={k: (s * 1.0, 11.0, -27.0)}, hands={k: 20.0})
    hit = _pose(r, lift=-2.6, pitch=4, bend=4, shift=(0, -3.0), roll=1.5 * s, side=3 * s, neck=(12, -4 * s, 0), head=(-2, -6 * s, 0), jaw=10, ears=(-28, -28),
                tail=(8, 6 * s), mane=12, feet={k: (-s * 2.0, 8.4, -24.0)})
    held = _pose(r, lift=-2.8, pitch=4.4, bend=4.4, shift=(0, -3.0), roll=1.6 * s, side=3.2 * s, neck=(13, -4 * s, 0), head=(-1, -6 * s, 2 * s), jaw=8,
                 ears=(-26, -26), tail=(8, 7 * s), mane=12, feet={k: (-s * 2.0, 8.4, -24.0)})
    step = _pose(r, lift=-1.2, pitch=1, shift=(0, -1.0), neck=(4, 0, 0), jaw=4, feet={k: _foot(k, dx=-s * 1.0, up=5, dz=-5)}, hands={k: 40.0})
    peel = _pose(r, lift=-0.8, pitch=-3, bend=-2, roll=-1 * s, side=-1 * s, neck=(-1, 2 * s, 0), head=(1, 3 * s, 0), jaw=8, ears=(-10, -10), tail=(1, -2 * s), mane=4,
                 feet={k: _foot(k, up=4, dz=0.5)}, hands={k: 25.0})
    return Move(mid, "LIGHT", 10, 4, 14, through=held, title="Rake", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.0, knock=0.7,
                hit=[(LIMB[k][2], (0, 6, -2), 5.5), (LIMB[k][1], (0, 10, 0), 4.0)], plant=(),
                stages={"windup": [(0.14, peel), (0.42, rise), (1.0, top)], "active": [(0.35, strike), (0.65, hit), (1.0, held)],
                        "recover": [(0.45, step), (0.72, _mix(step, _pose(r), 0.75))]}, lag=_lag())


ADVANCE = 8.0  # blocks the Bolt leaps


def bolt(r):
    """The Bolt (HEAVY, its gap closer): it drops into a crouch, its hind legs bunched up under it, its hackles and its
    ruff standing up and its lips drawn back; it holds, quivering; then it leaps a long way at you, stretched out flat
    with its forelegs reaching and its jaws wide, and lands on braced forelegs, its jaws snapping shut."""
    def crouch(q=0.0):
        return _pose(r, lift=-7.0 - 0.5 * q, pitch=3, arch=4 + q, bend=4, shift=(0, 2.5), neck=(16, 0, 0), head=(-8, 0, 0), jaw=14 + 4 * q, ears=(-34, -34),
                     tail=(-2 + 2 * q, 3 * q), mane=18 + 4 * q, feet={2: _foot(2, dz=-5), 3: _foot(3, dz=-5), 0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    push = _pose(r, lift=-1.0, pitch=-6, arch=-4, bend=-4, shift=(0, -3.0), neck=(20, 0, 0), head=(-14, 0, 0), jaw=32, ears=(-40, -40), tail=(10, 0), mane=20,
                 feet={2: _foot(2, dz=4), 3: _foot(3, dz=4)}, local={0: (0.0, 15.0, -12.0), 1: (0.0, 15.0, -11.0)}, hands={0: 40.0, 1: 40.0})
    fly = _pose(r, lift=7.0, pitch=0, arch=-8, bend=-5, shift=(0, -4.0), neck=(24, 0, 0), head=(-16, 0, 0), jaw=40, ears=(-44, -44), tail=(16, 0), mane=8,
                local={0: (0.0, 13.0, -17.0), 1: (0.0, 13.0, -16.0), 2: (0.0, 16.0, 15.0), 3: (0.0, 16.0, 14.0)}, hands={0: 20.0, 1: 20.0, 2: -40.0, 3: -40.0})
    land = _pose(r, lift=-4.0, pitch=6, arch=4, bend=5, shift=(0, -2.0), neck=(20, 0, 0), head=(-6, 0, 0), jaw=0, ears=(-36, -36), tail=(12, 0), mane=12,
                 feet={0: _foot(0, dz=-9), 1: _foot(1, dz=-8), 2: _foot(2, up=3, dz=6), 3: _foot(3, up=3, dz=6)}, hands={2: -20.0, 3: -20.0})
    skid = _pose(r, lift=-4.4, pitch=4, arch=3, bend=4, shift=(0, -1.5), neck=(18, 0, 0), head=(-4, 0, 0), jaw=4, ears=(-30, -30), tail=(8, 0), mane=12,
                 feet={0: _foot(0, dz=-9), 1: _foot(1, dz=-8), 2: _foot(2, dz=3), 3: _foot(3, dz=3)})
    return Move("bolt", "HEAVY", 18, 10, 22, through=skid, title="the Bolt", hold=8, track=6.0, lock=12, advance=ADVANCE, rng=(5, 16), kinds=("gap",),
                damage=1.3, knock=1.3, hit=[("jaw", (0, 1, -8), 6.0), ("head", (0, 1.5, -14), 6.0), ("paw0", (0, 6, 0), 5.0), ("paw1", (0, 6, 0), 5.0)], plant=(),
                stages={"windup": [(0.3, _mix(_pose(r), crouch(), 0.5)), (0.7, crouch()), (0.85, crouch(1)), (1.0, crouch(0.5))],
                        "active": [(0.2, push), (0.6, fly), (1.0, land)], "recover": [(0.4, skid), (0.75, _mix(skid, _pose(r), 0.75))]}, lag=_lag())


def clap(r):
    """Thunderclap (HEAVY): it gathers itself and rears right up on its hind legs, its forelegs curled up to its chest
    and its head looking down at you, its ruff and its plume standing up; it holds; and it comes down on its forepaws
    with all its weight, as a fox comes down on a mouse."""
    gather = _pose(r, lift=-4.0, pitch=4, bend=3, neck=(10, 0, 0), head=(6, 0, 0), jaw=10, ears=(-20, -20), tail=(-4, 0), mane=10,
                   feet={2: _foot(2, dz=-4), 3: _foot(3, dz=-4)})
    curl = {0: 75.0, 1: 75.0}
    knee = {0: (0.0, -0.3, 1.0), 1: (0.0, -0.3, 1.0)}

    def rear(a, q=0.0):
        return _pose(r, lift=1.0 + 2 * a, pitch=-30 - 8 * a, arch=-6, bend=-10, neck=(-14 - 4 * a, 0, 0), head=(18 + 4 * a, 0, 0), jaw=16 + 6 * q, ears=(-8, -8),
                     tail=(14 + 6 * a, 4 * q), mane=20 + 4 * a, feet={2: _foot(2, dz=-4), 3: _foot(3, dz=-4)},
                     reach={0: (0.0, -11.0, -9.0), 1: (0.0, -11.0, -9.0)}, poles=knee, hands=curl)
    down = {0: (6.0, 8.4, -25.0), 1: (-6.0, 8.4, -25.0)}
    fwd = {0: (0.0, -0.2, 1.0), 1: (0.0, -0.2, 1.0)}
    over = _pose(r, lift=-1.0, pitch=-12, arch=-4, bend=-4, neck=(4, 0, 0), head=(10, 0, 0), jaw=24, ears=(-20, -20), tail=(18, 0), mane=24,
                 feet={2: _foot(2, dz=-2), 3: _foot(3, dz=-2)}, reach={0: (0.0, -20.0, -13.0), 1: (0.0, -20.0, -13.0)}, poles=fwd, hands={0: 25.0, 1: 25.0})
    hit = _pose(r, lift=-5.0, pitch=7, arch=4, bend=6, shift=(0, -3.0), neck=(22, 0, 0), head=(-4, 0, 0), jaw=30, ears=(-30, -30), tail=(20, 0), mane=26,
                feet={**down, 2: _foot(2, dz=-2), 3: _foot(3, dz=-2)}, poles=fwd)
    held = _pose(r, lift=-5.6, pitch=7.5, arch=4.5, bend=6.5, shift=(0, -3.0), neck=(23, 0, 0), head=(-2, 0, 0), jaw=20, ears=(-30, -30), tail=(18, 0), mane=24,
                 feet={**down, 2: _foot(2, dz=-2), 3: _foot(3, dz=-2)}, poles=fwd)
    through = _mix(held, _pose(r), 0.2)
    return Move("clap", "HEAVY", 18, 6, 22, through=through, title="Thunderclap", hold=8, track=4.0, ability="Thunderclap", rng=(0, 5.0), kinds=("area", "near"),
                damage=1.3, knock=1.4, disc=5.0, hit=[("paw0", (0, 6, 0), 6.0), ("paw1", (0, 6, 0), 6.0)], plant=(),
                stages={"windup": [(0.3, gather), (0.7, rear(0.6)), (1.0, rear(1.0, 1.0))], "active": [(0.35, over), (0.7, hit), (1.0, held)],
                        "recover": [(0.4, _mix(held, _pose(r), 0.4)), (0.72, _mix(held, _pose(r), 0.82))]}, lag=_lag())


SIT_HOCK = 70.0  # degrees its hind feet lie forward from upright when it sits, its hocks on the floor


def _sit(r, a=0.0, q=0.0, down=1.0):
    """Sitting back on its haunches as a dog sits, its rump on the floor behind its hind paws (its hips drawn back over
    them, the paws where they stood), its hocks down and its forelegs straight; a: its head thrown back to howl (0 to
    1), q: the howl's waver; down: how far it has sat (0 standing)."""
    d = down
    feet = {2: (5.0, 10.2 - 4.4 * d, 21.0 + 5.0 * d), 3: (-5.0, 10.2 - 4.4 * d, 21.0 + 5.0 * d)}
    p = _pose(r, lift=-23.0 * d, pitch=-38 * d, shift=(0, 13.0 * d), arch=-4 * d, neck=(-4 - 16 * a, 0, 0), head=(-2 - 14 * a + 2 * q, 0, 0),
              jaw=4 + 30 * a + 5 * q, ears=(-10 - 24 * a, -10 - 24 * a), tail=(55 * d, 6 * q), mane=6 + 10 * a, feet=feet, hands={2: 0.0, 3: 0.0},
              poles={2: (0.0, 0.0, -1.0), 3: (0.0, 0.0, -1.0)})
    fr = core.frames(r, core.as_sample(p))
    lay = SIT_HOCK * d * DEG
    for k in (2, 3):  # the hind feet tip forward onto the floor as the hocks come down
        p[LIMB[k][2]] = ik.orient(r, fr, LIMB[k][2], (0.0, math.cos(lay), -math.sin(lay)), (1.0, 0.0, 0.0))
    return p


def howl(r):
    """Lightning: it sits back on its haunches and throws its head back and howls at the sky, long, its throat working
    and its ruff standing up, and the lightning comes down."""
    act = [(f, _sit(r, 1.0, q)) for f, q in ((0.1, 1.0), (0.25, -1.0), (0.4, 0.8), (0.55, -0.8), (0.7, 0.6), (0.85, -0.4), (1.0, 0.0))]
    return Move("howl", "NONE", 14, 24, 16, through=_sit(r, 0.6), title="Lightning", track=0.0, ability="Lightning", rng=(0, 18),
                kinds=("ranged", "area"), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, _sit(r, down=0.45)), (0.55, _sit(r, down=0.85)), (0.75, _sit(r)), (1.0, _sit(r, 0.8))], "active": act,
                        "recover": [(0.3, _sit(r, 0.3)), (0.55, _sit(r, down=0.6)), (0.8, _sit(r, down=0.2))]}, lag=_lag())


def spears(r):
    """Storm Spears: it lowers its head with its hackles up along its back and its lips back, and barks, five times, its
    whole front jolting up with each and a spear of lightning leaving its jaws."""
    def bark(a):
        return _pose(r, lift=-2.5 + 1.2 * a, pitch=2 - 3 * a, bend=3 - 4 * a, neck=(12 - 16 * a, 0, 0), head=(2 - 8 * a, 0, 0), jaw=8 + 30 * a, ears=(-16 - 10 * a, -16 - 10 * a),
                     mane=22 + 4 * a, tail=(4 + 4 * a, 0))
    act = []
    for n in range(5):
        f0 = n / 5
        act += [(f0 + 0.05, bark(1.0)), (f0 + 0.16, bark(0.0))]
    act[-1] = (1.0, bark(0.0))
    return Move("spears", "NONE", 12, 20, 14, through=_mix(bark(0), _pose(r), 0.3), title="Storm Spears", track=6.0, ability="Storm Spears", origin=(0.0, 2.9, 2.0),
                rng=(4, 18), kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.45, _mix(_pose(r), bark(0), 0.6)), (1.0, bark(0))], "active": act, "recover": _back(r, _mix(bark(0), _pose(r), 0.3))},
                lag=_lag())


def updraft(r):
    """Updraft: it whirls round on the spot after its own tail, once, so fast its paws barely touch the floor, its head
    turned back and its plume swept round to meet it; then it stops dead, facing you."""
    def chase(a, b=0.0):  # a: how far into the chase, b: its paws dancing
        feet = {k: _foot(k, up=2.0 + 2.0 * (b if k in (0, 3) else -b) + 1.0) for k in range(4)}
        return _pose(r, lift=-1.0 + 0.6 * a, side=16 * a, twist=-6 * a, roll=-3 * a, neck=(4, 30 * a, -6 * a), head=(6, 40 * a, 12 * a), jaw=18 * a, ears=(-16, -16),
                     tail=(8 * a, -44 * a), mane=8 * a, feet=feet, hands={k: 20.0 for k in range(4)})
    act = [(f, chase(1.0, (-1) ** n)) for n, f in enumerate((0.12, 0.25, 0.37, 0.5, 0.62, 0.75, 0.87))] + [(1.0, chase(0.4))]
    stop = _pose(r, lift=-2.4, pitch=2, bend=3, neck=(8, 0, 0), head=(-2, 0, 0), jaw=10, ears=(-20, -20), tail=(-2, -10), mane=14,
                 feet={0: _foot(0, dz=-3), 1: _foot(1, dz=-3)})
    return Move("updraft", "NONE", 12, 22, 14, through=stop, title="Updraft", track=0.0, ability="Updraft", rng=(0, 6), kinds=("area",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, chase(0.5)), (1.0, chase(1.0))], "active": act, "recover": [(0.35, stop), (0.72, _mix(stop, _pose(r), 0.75))]},
                lag=_lag())


def overdrive(r):
    """Overdrive (the signature): it plants its legs wide and lowers its head; and it shakes itself like a wet dog, from
    its nose to the end of its plume, harder and faster, its ears flapping and its ruff flying, and the storm bursts off
    it; it drops into a sprinter's crouch, its hackles up."""
    wide = {k: _foot(k, dx=SIDE[k] * 3.0) for k in range(4)}
    brace = _pose(r, lift=-2.0, bend=2, neck=(10, 0, 0), head=(4, 0, 0), jaw=6, ears=(-10, -10), tail=(-4, 0), mane=10, feet=wide)

    def shake(ph, amp):
        w = lambda lag: math.sin(ph - lag)
        return _pose(r, lift=-2.0 - 0.4 * amp, roll=7 * amp * w(1.8), twist=14 * amp * w(1.2), bend=2, neck=(10, 8 * amp * w(0.5), 18 * amp * w(0.6)),
                     head=(4, 6 * amp * w(0.2), 28 * amp * w(0.0)), jaw=6 + 6 * amp * abs(w(0.3)), ears=(30 * amp * w(0.4), -30 * amp * w(0.4)),
                     tail=(4 + 6 * amp, 40 * amp * w(2.4)), mane=10 + 8 * amp * w(0.8), feet=wide)
    n = 36
    act = [(i / n, shake(2 * math.pi * 6.0 * (i / n) ** 0.9, min(1.0, 0.35 + 0.9 * i / n) * (1.0 if i < n else 0.0))) for i in range(1, n + 1)]
    crouch = _pose(r, lift=-6.0, pitch=2, arch=3, bend=4, neck=(16, 0, 0), head=(-8, 0, 0), jaw=14, ears=(-36, -36), tail=(10, 0), mane=24,
                   feet={0: _foot(0, dz=-4), 1: _foot(1, dz=-4), 2: _foot(2, dz=-3), 3: _foot(3, dz=-3)})
    return Move("overdrive", "NONE", 16, 28, 18, through=crouch, title="Overdrive", signature=True, track=0.0, ability="Overdrive", rng=(0, 99),
                kinds=("buff", "area"), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), brace, 0.6)), (1.0, brace)], "active": act,
                        "recover": [(0.35, crouch), (0.75, _mix(crouch, _pose(r), 0.7))]}, lag=_lag())


def lash(r):
    """The Tail Lash (for whoever is behind it): it looks back over its shoulder and swings its hindquarters round,
    whipping the plume of its tail across behind it from one side to the other with a crack of lightning at its tip."""
    cock = _pose(r, lift=-1.2, yaw=-12, side=10, neck=(2, 26, 0), head=(4, 40, 8), jaw=12, ears=(-20, -20), tail=(10, -46), mane=10)
    hit = _pose(r, lift=-1.6, yaw=18, side=4, neck=(2, 30, 0), head=(4, 44, 8), jaw=16, ears=(-24, -24), tail=(16, 50), mane=12)
    held = _pose(r, lift=-1.7, yaw=20, side=3, neck=(2, 30, 0), head=(4, 44, 8), jaw=12, ears=(-24, -24), tail=(14, 56), mane=12)
    return Move("lash", "LIGHT", 10, 4, 14, through=held, title="Tail Lash", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=0.9, knock=1.0,
                hit=[("tail2", (0, 0, 6), 6.0), ("tail3", (0, 0, 8), 6.0), ("tail1", (0, 0, 5), 5.0)], plant=(),
                stages={"windup": [(0.35, _mix(_pose(r), cock, 0.5)), (1.0, cock)], "active": [(0.55, hit), (1.0, held)], "recover": _back(r, held)},
                lag=_lag())


def arrive(r):
    """Its intro: it stands still, as if listening; then the wind it left behind catches up with it: its plume and its
    ruff blow forward up over its back, and settle again; it shakes its head, lowers it at you with its hackles up and
    growls."""
    still = _pose(r, lift=-0.4, neck=(-4, 0, 0), head=(-2, 0, 0), jaw=0, ears=(14, 14), tail=(-6, 0))
    def blown(a):  # the wind it left behind catches it up: its plume lifts and curls forward over its back
        p = _pose(r, lift=-0.8 - 0.6 * a, pitch=1 * a, neck=(-2 + 4 * a, 0, 0), head=(2 * a, 0, 0), jaw=2, ears=(14 - 30 * a, 14 - 30 * a),
                  tail=(-6 + 110 * a, 6 * a), mane=40 * a)
        for b, extra in (("tail2", 22.0), ("tail3", 34.0)):
            x, y, z = p[b]
            p[b] = (x + extra * a, y, z)
        return p
    toss = lambda a: _pose(r, lift=-0.6, neck=(2, 0, 6 * a), head=(-4, 8 * a, 22 * a), jaw=6, ears=(-10 + 20 * a, -10 - 20 * a), tail=(4, -10 * a), mane=4)
    growl = _pose(r, lift=-2.6, pitch=2, bend=3, neck=(14, 0, 0), head=(-4, 0, 0), jaw=12, ears=(-24, -24), tail=(2, 0), mane=22,
                  feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    return Move("arrive", "NONE", 14, 30, 14, through=_mix(growl, _pose(r), 0.2), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, _mix(_pose(r), still, 0.7)), (1.0, still)],
                        "active": [(0.15, blown(0.6)), (0.28, blown(1.0)), (0.42, blown(0.4)), (0.52, _mix(blown(0), _pose(r), 0.3)), (0.62, toss(1)), (0.7, toss(-1)),
                                   (0.78, toss(0.4)), (1.0, growl)], "recover": [(0.6, _mix(growl, _pose(r), 0.5))]}, lag=_lag())


# the ground (SPEC §35.10): the paws stay out of the floor (the per-tick solve, then a flex of the wrist or hock)
PAW_GROUND = [(PAW[k], [(LIMB[k][2], 0, 40)]) for k in range(4)]
HEAD_GROUND = [(["head", "jaw"], [("neck", 0, 30, -1), ("head", 0, 30, -1)])]
STANDS = {"snap": (0, 1, 2, 3), "rake": (1, 2, 3), "bolt": (), "clap": (2, 3), "howl": (0, 1), "spears": (0, 1, 2, 3), "updraft": (), "overdrive": (),
          "lash": (0, 1, 2, 3), "arrive": (2, 3)}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [snap(r), rake(r, 0, "rake"), bolt(r), clap(r), howl(r), spears(r), updraft(r), overdrive(r), lash(r), arrive(r)]
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    # Updraft: it whirls once round on the spot through the active stage
    m = by["updraft"]
    W, A = m.seconds("windup"), m.seconds("active")
    _spin(r, core.clip_name(m), "roll", 1, 1, W + 0.04 * A, W + 0.96 * A)
    # Overdrive: the storm bursts off it as the shake ends, its ruff and its plume swelling and settling
    m = by["overdrive"]
    W, A = m.seconds("windup"), m.seconds("active")
    for b in ("mane0", "mane1", "tail1", "tail2", "tail3"):
        _scales(r, core.clip_name(m), b, [(W + 0.86 * A, 1.0), (W + 0.96 * A, 1.35), (W + A + 0.3, 1.0)])
    everyday = [n for n in r.clips if n != "death"]
    for n in r.clips:
        _on_floor(r, n)
    core.ground_pass(r, PAW_GROUND, clips=everyday)
    core.ground_pass(r, PAW_GROUND + HEAD_GROUND, clips=["death", "kneel", "rise"], skip=())
    return mv


# Its sounds (SPEC §35.8): a big wolf's growls, whines and steps, the breeze's wind and the thunder of a lightning bolt;
# chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.wolf_big.growl", 1.0, 0.8), ("entity.breeze.inhale", 0.7, 1.2)],
    "tell_heavy": [("entity.wolf_angry.growl", 1.1, 0.7), ("entity.breeze.charge", 1.0, 0.8)],
    "tell_perilous": [("entity.wolf_big.growl", 1.2, 0.5), ("entity.lightning_bolt.thunder", 0.4, 1.4)],
    "swing_light": [("entity.wolf_big.ambient", 1.0, 0.8), ("entity.wind_charge.throw", 0.8, 0.9)],
    "swing_heavy": [("entity.breeze.jump", 1.2, 0.7), ("entity.wolf_angry.growl", 1.0, 0.8)],
    "cast": [("entity.lightning_bolt.thunder", 0.6, 1.3), ("entity.breeze.wind_burst", 1.0, 0.8)],
    "impact": [("entity.lightning_bolt.impact", 1.0, 0.9), ("item.mace.smash_ground", 1.0, 0.7)],
    "roar": [("entity.wolf_big.whine", 1.2, 0.5), ("entity.lightning_bolt.thunder", 0.8, 0.9)],
    "bark": [("entity.wolf_big.ambient", 1.2, 0.7), ("entity.breeze.shoot", 1.0, 0.9)],
    "shake": [("entity.wolf.shake", 1.4, 0.7), ("entity.breeze.whirl", 1.0, 0.8)],
    "step": [("entity.wolf.step", 1.2, 0.6), ("entity.breeze.slide", 0.5, 0.8)],
}
MOVE_SOUNDS = {"howl": {"swing": "roar", "impact": ""}, "spears": {"swing": "bark", "impact": ""}, "updraft": {"swing": "cast", "impact": ""},
               "overdrive": {"swing": "shake", "impact": ""}, "arrive": {"swing": "tell_light", "impact": ""}}

FIGHT = {
    "intro": {"pose": "idle", "move": "arrive"},  # it stands listening, and the wind it outran catches up with it
    "phases": [
        {"pool": [
            {"move": "snap", "weight": 3, "when": "near", "then": [["rake"], ["rake", "bolt"]]},
            {"move": "clap", "weight": 2, "when": "near"},
            {"move": "bolt", "weight": 3, "when": "far", "then": [["snap"]]},
            {"move": "howl", "weight": 2},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        # the storm thickens, as in DeepBestiary: its spears, and the updraft
        {"transition": "spears", "pool": [
            {"move": "snap", "weight": 3, "when": "near", "then": [["rake"], ["rake", "clap"]]},
            {"move": "updraft", "weight": 2, "when": "near"},
            {"move": "bolt", "weight": 3, "when": "far", "then": [["snap", "rake"]]},
            {"move": "spears", "weight": 2, "when": "far"},
            {"move": "howl", "weight": 2},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        # the eye of the storm: it shakes the storm off itself and goes faster
        {"transition": "overdrive", "pool": [
            {"move": "howl", "weight": 2},
            {"move": "clap", "weight": 2, "when": "near"},
            {"move": "bolt", "weight": 3, "when": "far", "then": [["snap", "rake"]]},
            {"move": "snap", "weight": 3, "when": "near", "then": [["rake", "bolt"]]},
            {"move": "spears", "weight": 1, "when": "far"},
            {"move": "updraft", "weight": 1, "when": "near"},
            {"move": "overdrive", "weight": 1},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
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
    for b, p in (("paw0", (0, 0, 0)), ("hock2", (0, 0, 0)), ("head", (0, 0, 0)), ("ear_l", (0, -6, 0)), ("tail3", (0, 0, 12))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
