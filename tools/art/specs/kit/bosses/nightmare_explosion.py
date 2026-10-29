"""The Fuse (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Construct, the fear of the bang you are waiting for.
The Jack, picked 2026-10-30: "Hsssss."

A jack-in-the-box as big as a cart: a black lacquered toy box on brass ball feet, bound in tarnished brass, a faded
red and cream harlequin painted on its sides and scorched along the bottom, its lid thrown back on its hinge and lined
with the same harlequin. A crank turns on its right side all on its own, ticking, and a lit fuse hangs from it hissing.
Out of the box, on a long iron spring, stands the Jack: its head is a round black iron bomb with a wide grin and two
diamond eyes painted on it in chipped white paint, and in place of a jester's cap three lit fuses curl out of its
crown, a spark at the end of each. Under its head a sooty ruff with tattered harlequin flaps, and from the ruff two
long thin arms in harlequin sleeves with white-gloved hands and fingers far too long. Deep in the box something burns
white: you can see it past the spring, and through the keyhole.

Moves (SPEC §35.11): the Pop (LIGHT: it sinks down on its spring and springs out at you head first, its hands
grabbing); the Slap (LIGHT, after the Pop: a long backhand of its right arm across in front of it, the spring
twisting); Pop Goes (HEAVY, its delay: it sinks down into the box and the lid shuts on it; the crank ticks, and ticks;
then the lid bursts open and it springs up at you); the Bound (HEAVY, its gap closer: it sinks onto its spring and the
whole box jumps across to you and comes down on you); Fuse Lines (it rises high, throws its head back and whips it at
you twice, flinging its sparks); the Lid (LIGHT, for whoever is behind it: it ducks and the lid slams back over the
hinge onto the floor behind it); Shorter Fuse (into the second phase: it shakes and bounces on its spring, cackling,
while the crank whirls); and the Blast Ring (PERILOUS, the signature: it crams itself down into the box and the lid
slams shut; the box shudders and light leaks from under the lid; then the lid is blown back, it shoots up out of the
box with its arms flung wide, and the ring goes out: only right beside the box are you safe). It hops along, the box
landing and the Jack bouncing down on its spring and up again. It dies sagging down into the box, its head lolling
out over the front, and the lid falls shut on its neck.
"""
import math

from kit import core, ik
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, SIDES, hexc

ID = "nightmare_explosion"
SCALE = 2.0

# Kit bodies are drawn at true size (16 pixels a block). The box is 2.25 blocks across and 2.3 tall to its rim, its
# open lid standing 4.5; the spring holds the Jack's head from 4 to 5.2 blocks up and its sparks to 5.6; its hands
# hang to 1.5 blocks off the floor, 2.4 apart. Value story: the box is
# the darkest mass, its harlequin and brass lighter; the Jack's ruff and gloves are the lightest things on it, its head
# black with the grin white; the sparks and the light deep in the box glow white.
BOX = 34  # the crate, across and deep
WALL = 32  # its walls' height, from the top of its feet
FOOT = 3  # the ball feet
TOP = FOOT + WALL  # the rim's foot, pixels up
HALF = BOX / 2
NS = 10  # the spring's links, s0 at the box's floor to s9 under the ruff
GAP = 6.0  # a link's height at rest
SHUT = 1.0  # a link's height pressed flat
BASE = 5.0  # s0, pixels up inside the box
LID_OPEN = -102.0  # the lid's rest: thrown back on its hinge, leaning a little past upright
BEND_W = (0.0, 0.0, 0.02, 0.04, 0.07, 0.1, 0.14, 0.18, 0.21, 0.24)  # how a bend of the spring shares out up its links
LINKS = [f"s{i}" for i in range(NS)]


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d)


def _aim(r, name, d, xd=(1, 0, 0)):
    """Sets a bone's rest turn so its +y lies along d (a model direction, y down) at rest."""
    r.by_name[name].rot = ik.orient(r, core.frames(r, {}), name, d, xd)


WICKS = (("c", 0.0, (0.0, -0.8, 0.6), (0.0, 0.45, 0.9)), ("l", 1.0, (0.75, -0.65, 0.1), (0.95, 0.3, 0.1)),
         ("r", -1.0, (-0.75, -0.65, 0.1), (-0.95, 0.3, 0.1)))  # each fuse on its crown: its root's and its tip's way


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bx = _bone(base, "box", 0, 0, 0)
    # the box: brass ball feet, the crate, two brass bands, a lip round its open top, the lock on its front
    for sx in (1, -1):
        for sz in (1, -1):
            _box(bx, f"foot_{'l' if sx > 0 else 'r'}{'f' if sz < 0 else 'b'}", sx * (HALF - 3), 0, sz * (HALF - 3), 5, FOOT, 5)
    _box(bx, "crate", 0, FOOT, 0, BOX, WALL, BOX)
    _box(bx, "band_lo", 0, FOOT + 2, 0, BOX + 2, 3, BOX + 2)
    _box(bx, "band_hi", 0, TOP - 5, 0, BOX + 2, 3, BOX + 2)
    _box(bx, "rim_f", 0, TOP, -(HALF - 1), BOX, 2, 2)
    _box(bx, "rim_b", 0, TOP, HALF - 1, BOX, 2, 2)
    _box(bx, "rim_l", HALF - 1, TOP, 0, 2, 2, BOX - 4)
    _box(bx, "rim_r", -(HALF - 1), TOP, 0, 2, 2, BOX - 4)
    _box(bx, "lock", 0, 13, -(HALF + 0.5), 6, 8, 1)
    # the lid, hinged along the back of the rim
    ld = _bone(bx, "lid", 0, TOP + 2, HALF, (LID_OPEN, 0, 0))
    _box(ld, "lid", 0, 0, -HALF - 1, BOX + 2, 3, BOX + 2)
    # the crank on its right side, and the fuse wound on its axle, a lit end hanging
    ck = _bone(bx, "crank", -HALF, 18, 1)
    ck.cube("hub", -4, -2, -2, 4, 4, 4)
    ck.cube("spool", -3, -3.5, -3.5, 2, 7, 7)
    ck.cube("arm", -5, -12, -1.5, 2, 12, 3)
    ck.cube("knob", -8, -13, -1.5, 3, 3, 3)
    c0 = _bone(bx, "cord0", -(HALF + 2), 15, 3)
    c0.cube("cord0", -0.5, 0, -0.5, 1, 7, 1)
    c1 = c0.bone("cord1", (0, 7, 0), (20, 0, 0))
    c1.cube("cord1", -0.5, 0, -0.5, 1, 6, 1)
    c1.cube("spark_k", -1.5, 5, -1.5, 3, 3, 3)
    # the spring: ten rings, each a square of iron bar, tilted one way and the next so it reads as a coil
    prev = bx
    for i in range(NS):
        s = _bone(prev, f"s{i}", 0, BASE if i == 0 else GAP, 0)
        ring = _bone(s, f"ring{i}", 0, 0, 0, (0, 0, 8.0 if i % 2 else -8.0))
        _box(ring, f"sf{i}", 0, -1, -5, 12, 2, 2)
        _box(ring, f"sb{i}", 0, -1, 5, 12, 2, 2)
        _box(ring, f"sl{i}", 5, -1, 0, 2, 2, 8)
        _box(ring, f"sr{i}", -5, -1, 0, 2, 2, 8)
        prev = s
    # the ruff, its tattered flaps, the head
    co = _bone(prev, "collar", 0, 4, 0)
    _box(co, "ruff", 0, -1, 0, 20, 3, 20)
    _box(co, "ruff2", 0, 2, 0, 15, 2, 15)
    for n, (x, z, rot) in {"f": (0, -10, (0, 0, 0)), "b": (0, 10, (0, 180, 0)), "l": (10, 0, (0, -90, 0)), "r": (-10, 0, (0, 90, 0))}.items():
        fl = _bone(co, "flap_" + n, x, -0.5, z, rot)
        _box(fl, "tat_" + n, 0, -9, -0.5, 14, 9, 0)  # a plane: faces north and south only
    hd = _bone(co, "head", 0, 3, 0)
    _box(hd, "bomb", 0, 1, 0, 14, 12, 14)  # a ball of iron: a block, a band round it each way, a cap above and below
    _box(hd, "bomb_w", 0, 3, 0, 16, 8, 12)
    _box(hd, "bomb_d", 0, 3, 0, 12, 8, 16)
    _box(hd, "bomb_t", 0, 13, 0, 10, 2, 10)
    _box(hd, "bomb_b", 0, -1, 0, 10, 2, 10)
    _box(hd, "socket", 0, 15, 0, 5, 2, 5)
    for n, s, root, tip in WICKS:
        w0 = _bone(hd, f"wk_{n}0", s * 1.5, 16.5, 0.5 if n == "c" else 0)
        w0.cube(f"wk_{n}0", -1, 0, -1, 2, 7, 2)
        w1 = w0.bone(f"wk_{n}1", (0, 7, 0))
        w1.cube(f"wk_{n}1", -0.5, 0, -0.5, 1, 6, 1)
        w1.cube(f"spark_{n}", -1.5, 5, -1.5, 3, 3, 3)
        _aim(r, f"wk_{n}0", root, (0, 0, -1) if n != "c" else (1, 0, 0))
        _aim(r, f"wk_{n}1", tip, (0, 0, -1) if n != "c" else (1, 0, 0))
    # the arms: harlequin sleeves, a cuff at the wrist, white-gloved hands, long fingers on their own bone so they curl
    for s, n in ((1, "l"), (-1, "r")):
        a = _bone(co, "arm_" + n, s * 10, 0.5, 0, (-4, 0, -s * 10))
        a.cube("sleeve_" + n, -2, 0, -2, 4, 16, 4)
        f = a.bone("forearm_" + n, (0, 16, 0), (-12, 0, s * 4))
        f.cube("fore_" + n, -1.5, 0, -1.5, 3, 13, 3)
        f.cube("cuff_" + n, -2.5, 11, -2.5, 5, 3, 5)
        h = f.bone("hand_" + n, (0, 14, 0), (-6, 0, 0))
        h.cube("palm_" + n, -2, 0, -1, 4, 5, 2)
        h.cube("thumb_" + n, s * 2 - (0 if s > 0 else 1), 1, -1.5, 1, 4, 1)
        g = h.bone("fing_" + n, (0, 5, 0), (-14, 0, 0))
        for j, x in enumerate((-1.5, -0.5, 0.5)):
            g.cube(f"f{j}_{n}", x + (0 if j != 1 else 0), 0, -0.5, 1, 8 + (1 if j == 1 else 0), 1)
    r.look = [("head", 0.6, 0.45)]
    r.shadow = 2.4
    r.size = 1.0
    # secondary parts (SPEC §35.10): the top of the spring wobbles after the box, the head after it; the arms, the
    # flaps, the fuses and the crank's lit end swing
    r.springs = ([("s6", 0.2, 2.4, 0.18), ("s7", 0.25, 2.3, 0.17), ("s8", 0.3, 2.2, 0.16), ("s9", 0.35, 2.0, 0.15), ("head", 0.3, 2.6, 0.2)]
                 + [(f"forearm_{n}", 0.45, 1.6, 0.14) for n in ("l", "r")] + [(f"fing_{n}", 0.5, 2.2, 0.2) for n in ("l", "r")]
                 + [(f"flap_{n}", 0.6, 1.8, 0.12) for n in ("f", "b", "l", "r")]
                 + [(f"wk_{n}1", 0.55, 2.4, 0.16) for n in ("c", "l", "r")] + [("cord1", 0.6, 1.5, 0.12)])
    r.guard = ["fing_l", "fing_r", "hand_l", "hand_r", "head", "lid", "cord1"]  # bones
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

LACQ = Ramp("#050407", "#0a080d", "#110e15", "#18141d", "#211b27", "#2b2332", "#362c3e", "#43374b")
BRASS = Ramp("#1e150a", "#30220f", "#443116", "#5a421e", "#715428", "#886734", "#9f7b44", "#b69058", "#cba872")
RED = Ramp("#1a0606", "#2a0a0a", "#3e0f0f", "#541515", "#6a1c1a", "#802622", "#94322c", "#a84238")
CREAM = Ramp("#2e2820", "#463e32", "#5e5546", "#786d5c", "#928672", "#ab9f88", "#c2b69e", "#d6cbb3", "#e6ddc8")
IRON = Ramp("#060607", "#0c0c0e", "#141417", "#1c1c21", "#26262c", "#313139", "#3e3e47", "#4d4d57")
STEEL = Ramp("#17161a", "#222126", "#2e2c33", "#3b3941", "#4a4750", "#5a5761", "#6c6873", "#7f7b86")
RUST = Ramp("#1e0e06", "#2e160a", "#42200f", "#562c16", "#6a381e")
CORD = Ramp("#2a1f12", "#3c2d1a", "#504024", "#66532f", "#7c663b", "#927a48")
HOT = ("#fffdf4", "#fff2c8", "#ffd98a", "#ffb24a")


def _harlequin(cell, i, j, ox=0, oy=0):
    """1 on the red diamonds, 0 on the cream: diamonds cell pixels across."""
    u, v = (i + ox) / cell, (j + oy) / (cell * 1.3)
    return int((math.floor(u + v) + math.floor(u - v)) % 2 == 0)


def _panel(P_, c, fname, cell=8, scorch=True):
    """A side of the box: black lacquer, a brass line round a panel of faded harlequin, chipped, scorched along its
    bottom."""
    f = P_.face(c, fname)
    w, h = f.w, f.h
    for i, j in f.each():
        edge = min(i, j, w - 1 - i, h - 1 - j)
        if edge < 3:
            col = LACQ.at(3.6 - 0.4 * (j / h) + (0.8 if j == 0 else 0))
        elif edge == 3:
            col = BRASS.at(4.6 + (0.8 if (i + j) % 5 == 0 else 0) - 0.8 * (j / h))
        else:
            red = _harlequin(cell, i - 4, j - 4)
            ramp = RED if red else CREAM
            v = (4.6 if red else 4.8) - 1.2 * (j / h)
            n = R._h(c, fname, i * 31 + j) % 17
            if n == 0:
                ramp, v = LACQ, 3.0  # chipped to the lacquer
            elif n < 3:
                v -= 0.8
            col = ramp.at(v)
        if scorch and j > h - 8:
            k = (j - (h - 8)) / 8.0
            if R._h(c, fname, i * 7 + j * 3) % 5 < 4 * k + 1:
                col = LACQ.at(1.2 + 2 * (1 - k))
        f.set(i, j, col)


def paint(r, P_):
    """The box black lacquer and tarnished brass, a faded harlequin on its sides and lining its lid, scorched along the
    bottom; the light deep inside it white; the spring dull steel spotted with rust; the Jack's head black iron with its
    grin and eyes in chipped white paint and white pin points for pupils; its ruff cream gone grey with soot, the flaps
    and sleeves harlequin, the gloves dirty white; the fuses braided tan cord, their sparks white hot."""
    of = lambda *ps: [c.name for c in r.cubes if any(c.name.startswith(p) for p in ps)]
    for c in of("foot_"):
        P_.paint(c, BRASS, 5.2, None, grad=0.6, rim=0.8, ao=0.6)
    P_.paint("crate", LACQ, 3.4, None, grad=0.4, rim=0.4, ao=0.6)
    for fname in SIDES:
        _panel(P_, "crate", fname)
    # inside the box: dark, and white hot deep down past the spring
    f, g = P_.face("crate", "top"), P_.face("crate", "top", glow=True)
    for i, j in f.each():
        d = math.hypot(i - f.w / 2 + 0.5, j - f.h / 2 + 0.5) / (f.w / 2)
        f.set(i, j, LACQ.at(0.4 + 1.6 * d) if d > 0.45 else hexc(HOT[1 if d < 0.25 else 2]))
        if d < 0.6:
            g.set(i, j, hexc(HOT[0] if d < 0.3 else HOT[2], int(255 * min(1.0, 1.6 * (0.6 - d) / 0.6))))
    for c in ("band_lo", "band_hi"):
        P_.paint(c, BRASS, 4.6, lambda f, i, j: (1.2 if i % 9 == 4 and j == 1 else -0.6 if (i * 3 + j) % 11 == 0 else 0), grad=0.6, rim=0.9, ao=0.5,
                 noise=0.6, nscale=3)
    for c in of("rim_"):
        P_.paint(c, BRASS, 5.0, None, grad=0.3, rim=1.0, ao=0.4, noise=0.4)
    P_.paint("lock", BRASS, 5.6, None, grad=0.4, rim=0.8)
    f, g = P_.face("lock", "north"), P_.face("lock", "north", glow=True)
    for i, j in ((2, 2), (3, 2), (2, 3), (3, 3), (2, 4), (3, 4), (2, 5), (3, 5)):  # the keyhole, lit from inside
        f.set(i, j, hexc(HOT[1]))
        g.set(i, j, hexc(HOT[0], 220))
    # the lid: lacquer and brass outside, harlequin lining inside (its bottom face, the one turned to you when open)
    P_.paint("lid", LACQ, 3.6, None, grad=0.4, rim=0.6, ao=0.4)
    f = P_.face("lid", "bottom")
    for i, j in f.each():
        edge = min(i, j, f.w - 1 - i, f.h - 1 - j)
        if edge < 2:
            f.set(i, j, BRASS.at(4.0 + (0.6 if edge == 1 else 0)))
        else:
            red = _harlequin(9, i, j)
            n = R._h("lid", "b", i * 37 + j) % 19
            f.set(i, j, (LACQ.at(2.6) if n == 0 else (RED if red else CREAM).at((4.2 if red else 4.4) - (0.6 if n < 3 else 0))))
    f = P_.face("lid", "top")
    for i, j in f.each():
        edge = min(i, j, f.w - 1 - i, f.h - 1 - j)
        d = abs(i - f.w / 2 + 0.5) + abs(j - f.h / 2 + 0.5)
        if edge == 2 or abs(d - 9) < 0.6:
            f.set(i, j, BRASS.at(4.4))
        elif d < 8.5:
            f.set(i, j, RED.at(3.8 + 0.4 * ((i + j) % 2)) if R._h("lid", "t", i * 13 + j) % 9 else LACQ.at(2.4))
    # the crank and the fuse on it
    for c in ("hub", "arm", "knob"):
        P_.paint(c, BRASS, 4.8, None, grad=0.4, rim=0.8, ao=0.4, noise=0.3)
    P_.paint("spool", CORD, 3.6, lambda f, i, j: (0.8 if (i + j) % 3 == 0 else -0.4), grad=0.2, rim=0.3)
    for c in ("cord0", "cord1"):
        P_.paint(c, CORD, 3.8, lambda f, i, j: (0.9 if j % 3 == 0 else 0), grad=0.0, rim=0.2)
    # the spring: dull steel, rust spotting it
    for c in of("sf", "sb", "sl", "sr"):
        P_.paint(c, STEEL, 4.0, lambda f, i, j, c=c: (RUST.at(2.6 + (i % 2)) if R._h(c, f.name, i * 5 + j) % 7 == 0 else 0), grad=0.3, rim=0.8, ao=0.4)
    # the ruff and the flaps
    P_.paint("ruff", CREAM, 4.6, lambda f, i, j: (-1.2 if f.name in SIDES and (i % 3 == 0) else -0.6 if R._h("ruff", f.name, i + 9 * j) % 5 == 0 else 0),
             grad=0.6, rim=0.8, ao=0.8)
    P_.paint("ruff2", CREAM, 5.2, lambda f, i, j: (-1.0 if f.name in SIDES and (i % 2 == 0) else 0), grad=0.4, rim=0.8, ao=0.6)
    for n in ("f", "b", "l", "r"):
        for fname in ("north", "south"):
            f = P_.face("tat_" + n, fname)
            for i, j in f.each():
                red = _harlequin(5, i, j, 1, 0)
                torn = j >= f.h - 1 - (R._h(n, fname, i) % 3)
                f.set(i, j, LACQ.at(1.6) if torn else (RED if red else CREAM).at((4.0 if red else 4.2) - 0.18 * j))
    # the head: black iron with rivets round its middle; the grin and the eyes painted on in chipped white
    for c in ("bomb", "bomb_w", "bomb_d", "bomb_t", "bomb_b"):
        P_.paint(c, IRON, 3.2, lambda f, i, j, c=c: (1.6 if f.name in SIDES and j == 3 and i % 3 == 1 else 0), grad=1.0, rim=1.2, ao=0.8, noise=0.5, nscale=3)
    P_.paint("socket", BRASS, 4.4, None, grad=0.4, rim=0.8)
    f, g = P_.face("bomb_d", "north"), P_.face("bomb_d", "north", glow=True)
    W = f.w  # 12 across, 8 down
    paint_px = set()
    for j, (a, b) in ((0, (2, 3)), (1, (1, 4)), (2, (2, 3))):  # the diamond eyes
        for i in range(a, b + 1):
            paint_px |= {(i, j), (W - 1 - i, j)}
    for i, j in ((0, 4), (W - 1, 4), (1, 5), (W - 2, 5)):  # the grin, up to its corners
        paint_px.add((i, j))
    for i in range(2, W - 2):
        paint_px.add((i, 6))
    for i in range(3, W - 3):
        paint_px.add((i, 7))
    for i, j in paint_px:
        chip = R._h("face", "n", i * 11 + j) % 9 == 0
        f.set(i, j, IRON.at(2.0) if chip else CREAM.at(7.2 - 0.3 * j))
    for i in range(3, W - 3, 2):  # the teeth's gaps
        f.set(i, 7, IRON.at(1.0))
        f.set(i + 1, 6, IRON.at(1.4)) if i + 1 < W - 3 else None
    for i, j in ((2, 1), (W - 3, 1)):  # the pupils, pin points of light
        f.set(i, j, hexc(HOT[0]))
        g.set(i, j, hexc(HOT[0], 230))
    # the sleeves and the gloves
    for n in ("l", "r"):
        P_.paint("sleeve_" + n, RED, 4.2, lambda f, i, j: (None if not _harlequin(4, i, j) else 0), grad=0.6, rim=0.4, ao=0.4)
        for fname in ("top", "bottom") + SIDES:
            f = P_.face("sleeve_" + n, fname)
            for i, j in f.each():
                if not _harlequin(4, i, j):
                    f.set(i, j, CREAM.at(4.6 - 0.1 * j) if fname != "bottom" else CREAM.at(3.0))
        P_.paint("fore_" + n, RED, 4.0, lambda f, i, j: (0 if _harlequin(3, i, j) else (CREAM.at(4.4 - 0.1 * j))), grad=0.5, rim=0.4, ao=0.3)
        P_.paint("cuff_" + n, CREAM, 5.6, lambda f, i, j: (-1.2 if f.name in SIDES and i % 2 == 0 else 0), grad=0.4, rim=0.8, ao=0.6)
        for c in ("palm_" + n, "thumb_" + n) + tuple(f"f{j}_{n}" for j in range(3)):
            P_.paint(c, CREAM, 6.2, lambda f, i, j, c=c: (-1.4 if R._h(c, f.name, i + 3 * j) % 6 == 0 else -0.08 * j), grad=0.4, rim=0.6, ao=0.4)
    # the fuses: braided cord, banded, a spark at each tip
    for n in ("c", "l", "r"):
        for k in ("0", "1"):
            P_.paint(f"wk_{n}{k}", CORD, 4.2 - 0.4 * int(k), lambda f, i, j: (1.0 if (i + j) % 3 == 0 else -0.4 if j % 4 == 0 else 0), grad=0.2, rim=0.3)
    for c in ("spark_c", "spark_l", "spark_r", "spark_k"):
        for fname in ("top", "bottom") + SIDES:
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                mid = (i == 1 or f.w < 3) and (j == 1 or f.h < 3)
                f.set(i, j, hexc(HOT[0] if mid else HOT[2 + (i + j) % 2]))
                g.set(i, j, hexc(HOT[0] if mid else HOT[1], 255 if mid else 200))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the box set on the floor (tilted on an edge, never into it), the spring pressed down or drawn out and bent,
# the Jack's head, arms and fuses

def _tilt_lift(pitch, roll):
    """How far the box rises when it tips by pitch and roll, so it stands on its edge, not into the floor."""
    return HALF * (abs(math.sin(math.radians(pitch))) + abs(math.sin(math.radians(roll))))


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), comp=0.0, low=0.0, sink=0.0, lean=(0.0, 0.0), bend=(0.0, 0.0), twist=0.0, wave=(0.0, 0.0, 0.0),
          head=(0.0, 0.0, 0.0), arms=None, lid=LID_OPEN, crank=0.0, wicks=(0.0, 0.0), flare=0.0, cord=0.0, extra=None):
    """The box: lift (+ up, on top of what its tilt needs), pitch (+ its front down), yaw (+ to its left), roll (+ its
    left side down), shift (x, z). The spring: comp (0 at rest, 1 pressed flat, below 0 drawn out), low (more press on
    its bottom links), sink (the spring's foot drawn down into the box's floor, pixels), lean (forward, to its left: the
    whole spring tipped at its foot), bend (forward, to its left) shared out up its links, twist (+ to its
    left) and wave (degrees, phase, sideways share) running up it. head (nod + down, turn + left, tilt + its left side
    down). arms {"l"/"r": (raise + forward, out + away, elbow + bent, wrist, curl + fingers in, twist)}. lid (its angle;
    0 shut). crank (degrees). wicks (lift + up, flick + back). flare lifts the ruff's flaps. cord swings the crank's
    fuse."""
    p = {"box": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift + _tilt_lift(pitch, roll), shift[1])}}
    amp, ph, side = wave
    for i, b in enumerate(LINKS):
        w = BEND_W[i]
        wv = amp * math.sin(ph - 0.65 * i) * (0.3 + 0.7 * i / (NS - 1))
        rot = (bend[0] * w + wv * (1 - side), twist / NS, bend[1] * w + wv * side)
        if i == 0:
            p[b] = {"rot": (rot[0] + lean[0], rot[1], rot[2] + lean[1]), "pos": (0, -sink, 0)}
            continue
        c = comp + low * max(0.0, 1.0 - (i - 1) / 5.0)
        c = min(1.0, c)
        p[b] = {"rot": rot, "pos": (0, -(GAP - SHUT) * c if c > 0 else -c * GAP * 0.55, 0)}
    p["head"] = (head[0], head[1], head[2])
    p["lid"] = (lid - LID_OPEN, 0, 0)
    p["crank"] = (crank, 0, 0)
    p["cord0"] = (cord, 0, 0)
    p["cord1"] = (cord * 0.6, 0, 0)
    lift_, flick = wicks
    for n, s in (("c", 0), ("l", 1), ("r", -1)):  # each fuse's frame has its +x forward (the middle one's, to its left)
        if n == "c":
            p["wk_c0"], p["wk_c1"] = (0.5 * (lift_ - flick), 0, 0), (0.7 * (lift_ - flick), 0, 0)
        else:
            p[f"wk_{n}0"], p[f"wk_{n}1"] = (s * 0.5 * lift_, 0, s * 0.4 * flick), (s * 0.8 * lift_, 0, s * 0.5 * flick)
    for n, sx, sz in (("f", 0, -1), ("b", 0, 1), ("l", 1, 0), ("r", -1, 0)):
        p["flap_" + n] = (-flare, 0, 0)  # each flap's own frame faces out: -x lifts it out
    arms = arms or {}
    for n, s in (("l", 1), ("r", -1)):
        raise_, out, elbow, wrist, curl, tw = (tuple(arms.get(n, ())) + (0.0,) * 6)[:6]
        p["arm_" + n] = (-raise_, tw * s, -s * out)
        p["forearm_" + n] = (-elbow, 0, 0)
        p["hand_" + n] = (-wrist, 0, 0)
        p["fing_" + n] = (-curl, 0, 0)
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


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _add(base, w, home):
    """base with w's difference from home on top (both poses from _pose)."""
    out = dict(base)
    for k in set(w) | set(home):
        vb, vw, vh = base.get(k, (0, 0, 0)), w.get(k, (0, 0, 0)), home.get(k, (0, 0, 0))
        rot = tuple(b + x - h for b, x, h in zip(*((v["rot"] if isinstance(v, dict) else v) for v in (vb, vw, vh))))
        if any(isinstance(v, dict) for v in (vb, vw, vh)):
            pb, pw, ph = (v.get("pos", (0, 0, 0)) if isinstance(v, dict) else (0, 0, 0) for v in (vb, vw, vh))
            out[k] = {"rot": rot, "pos": tuple(b + x - h for b, x, h in zip(pb, pw, ph))}
        else:
            out[k] = rot
    return out


def _wobble(r, end, n=3, amp=10.0, comp=0.18):
    """The way back to rest from end: the spring overshoots and wobbles, bending one way and the other, less each
    time. n keys through the recovery after the follow-through (which holds end; the kit drops keys closer to it than
    0.33), each on its steady way home with the wobble on top, as (f, pose)."""
    home = _pose(r)
    keys = []
    for k in range(1, n + 1):
        u = 0.36 + 0.36 * (k - 1) / max(1, n - 1)
        f = (u - core.THROUGH) / (1 - core.THROUGH)
        e = (1 - f) ** 1.2
        sgn = -1 if k % 2 else 1
        w = _pose(r, comp=sgn * comp * e, bend=(sgn * amp * e, 0.4 * amp * e * (1 if k % 3 else -1)), head=(-sgn * amp * 0.6 * e, 0, 0),
                  arms={"l": (sgn * 6 * e, 3 * e, 8 * e), "r": (sgn * 6 * e, 3 * e, 8 * e)}, flare=8 * e)
        keys.append((u, _add(_mix(end, home, 1 - (1 - f) ** 1.8), w, home)))
    return keys


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before, so nothing spins round between keys."""
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


HIDE = dict(comp=1.0, sink=3.5, lid=-4.0)  # the Jack down in the box under its shut lid
FOLDED = {"l": (172, 2, 160, 20, 60), "r": (172, 2, 160, 20, 60)}  # its arms folded up beside its head, in with it


def _hidden(r, **kw):
    a = dict(HIDE)
    a.update(kw)
    return _pose(r, arms=FOLDED, head=(30, 0, 0), wicks=(-80, 60), **a)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _ticks(t, period, step=45.0, snap=0.12):
    """The crank's turn at t: a click of step degrees every period seconds, each snapping round over snap seconds."""
    k, u = divmod(t, period)
    return step * (k + core.ease_io(min(1.0, u / snap)))


def _idle(r):
    """It stands swaying on its spring in a lazy loop, its head rolling the other way a beat behind; its arms hang and
    swing after it, its fingers flexing; the fuses bob; the crank ticks round a click at a time and the lid creaks on
    its hinge. Now and then the Jack jerks its head over to one side and holds it there, grinning, and lets it come
    back; and once it bobs on its spring three times, quickly, as if it were laughing."""
    L, n = 6.0, 120
    keys = []
    for kk in range(n + 1):
        t = L * kk / n
        w = 2 * math.pi * kk / n
        jerk = 0.0
        if 2.6 < t < 3.9:  # the head snapped over and held
            u = (t - 2.6) / 1.3
            jerk = core.ease_out(min(1.0, u / 0.08)) * (1 - core.ease_io(max(0.0, (u - 0.6) / 0.4)))
        laugh = 0.0
        if 4.5 < t < 5.4:
            u = (t - 4.5) / 0.9
            laugh = math.sin(3 * 2 * math.pi * u) * math.sin(math.pi * u)
        lag = w - 0.7
        keys.append((t, _pose(r, comp=0.04 * math.sin(2 * w) + 0.1 * laugh, bend=(5 * math.sin(w) + 2 * laugh, 7 * math.sin(2 * w + 0.5)),
                              wave=(1.2, 3 * w, 0.4), twist=6 * math.sin(w + 1.2),
                              head=(-4 * math.sin(lag) - 6 * laugh, 8 * math.sin(w + 2.0) + 22 * jerk, -8 * math.sin(2 * lag + 0.5) + 30 * jerk),
                              arms={"l": (6 * math.sin(lag - 0.4) + 4 * laugh, 4 + 3 * math.sin(2 * lag), 10 + 6 * math.sin(lag + 1), 6 * math.sin(lag),
                                          14 + 10 * math.sin(3 * w)),
                                    "r": (-5 * math.sin(lag - 0.2) + 4 * laugh, 4 - 3 * math.sin(2 * lag), 12 - 6 * math.sin(lag + 0.5), -6 * math.sin(lag),
                                          14 + 10 * math.sin(3 * w + 2))},
                              lid=LID_OPEN + 1.5 * math.sin(w + 0.3), crank=(_ticks(t, 0.75) % 360.0) if t < L - 1e-6 else 0.0,
                              wicks=(6 * math.sin(2 * w + 0.8), 8 * math.sin(w + 1.6)), flare=3 + 3 * math.sin(2 * w), cord=6 * math.sin(w + 0.9))))
    keyed(r, "idle", L, _unwrap(keys), loop=True)
    _fix_crank(r, "idle")


def _fix_crank(r, clip):
    """The crank's track keyed a tick at a time and unwrapped, so its clicks only ever turn it forward (a whole turn
    is no turn: the loop's end meets its start)."""
    c = r.clips[clip]
    tr = c.tracks.get(("crank", "rot"))
    if not tr:
        return
    out, last = [], None
    for t, v, mode in tr:
        x = v[0]
        if last is not None:
            while x < last - 1e-6:
                x += 360.0
        last = x
        out.append((t, (x, v[1], v[2]), mode))
    c.tracks[("crank", "rot")] = out


def _hop(u, stride_px, beta, height):
    """The box through a hop at u: on the floor (u < beta) it stays put, so it moves back under the body as far as the
    body goes on; in the air it arcs forward. Returns (z, height, air)."""
    st = stride_px * beta
    if u < beta:
        f = u / beta
        return -st / 2 + st * f, 0.0, 0.0
    f = (u - beta) / (1 - beta)
    e = core.ease_io(f)
    return st / 2 - st * e, height * math.sin(math.pi * f), math.sin(math.pi * f)


def _locomotion(r):
    """It hops: the box lands, the Jack bouncing down on its spring and up again as it pushes off, and in the air it
    hangs at full stretch, its arms floating up and its fuses streaming; each landing bounces the lid and ticks the
    crank round. Running, it bounds, longer and higher, the Jack thrown back as it jumps and forward as it lands."""
    for name, L, stride, beta, height, pt in (("walk", 0.75, 1.2, 0.45, 5.0, 3.0), ("run", 0.5, 2.4, 0.34, 10.0, 6.0)):
        sp = stride * 16
        keys = []
        n = 30
        for kk in range(n + 1):
            u = kk / n
            z, h, air = _hop(u, sp, beta, height)
            # the spring through a hop: pressed on landing (u = 0), springing as it pushes off, drawn out in the air
            if u < beta:
                f = u / beta
                c = 0.32 * math.sin(math.pi * f) ** 0.8 - 0.06 * f
            else:
                f = (u - beta) / (1 - beta)
                c = -0.12 * math.sin(math.pi * f) - 0.06 * (1 - f)
            # the box tips back as it leaves the floor and forward as it lands
            pitch = -pt * math.sin(math.pi * f) if u >= beta else pt * 0.4 * math.sin(math.pi * f)
            lean = (-10 * math.sin(math.pi * f) if u >= beta else 9 * math.sin(math.pi * f)) * (1.5 if name == "run" else 1.0)
            keys.append((L * u, _pose(r, lift=h, shift=(0, z), pitch=pitch, comp=c, bend=(lean, 2 * math.sin(2 * math.pi * u)),
                                      head=(-0.6 * lean, 0, 3 * math.sin(2 * math.pi * u)),
                                      arms={"l": (12 * air + 6, 10 + 14 * air, 14 - 4 * air, 6, 18 - 10 * air),
                                            "r": (12 * air + 6, 10 + 14 * air, 14 - 4 * air, 6, 18 - 10 * air)},
                                      lid=LID_OPEN + (5 * math.sin(math.pi * u / beta) if u < beta else -2 * air), crank=45.0 * core.ease_io(min(1.0, u / 0.2)),
                                      wicks=(10 * air - 4, 18 * air), flare=4 + 12 * air, cord=8 * air)))
        keyed(r, name, L, keys, loop=True)
        _fix_crank(r, name)
    r.walk_stride, r.run_stride = 1.2, 2.4
    r.run_speed = (0.12, 0.28)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 24
        for kk in range(n + 1):
            u = kk / n
            z, h, air = _hop(u, 0.0, 0.5, 3.0)
            keys.append((0.7 * u, _pose(r, lift=h, yaw=sgn * 6 * math.sin(math.pi * u), comp=0.15 * (1 - air), twist=sgn * 14, bend=(0, sgn * 6),
                                        head=(0, sgn * 16, -sgn * 6), arms={"l": (8, 10 + 8 * air, 14), "r": (8, 10 + 8 * air, 14)},
                                        wicks=(6 * air, 10 * air), flare=4 + 6 * air)))
        keyed(r, nm, 0.7, keys, loop=True)


def _reactions(r):
    """Struck, the Jack is knocked back on its spring and comes wobbling back, its arms flung up and its head thrown
    back; from the side it bends away from the blow. Its stance broken, it flops forward over the front of the box, its
    arms hanging down outside it and its head lolling; it bounces itself upright again."""
    hit = _pose(r, comp=-0.15, bend=(-34, 0), head=(-22, 0, 0), arms={"l": (40, 30, 30, 10, -10), "r": (40, 30, 30, 10, -10)}, wicks=(20, -30), flare=16,
                pitch=-2, lid=LID_OPEN - 8)
    back = _pose(r, comp=0.12, bend=(14, 0), head=(10, 0, 0), arms={"l": (-6, 4, 20, 0, 30), "r": (-6, 4, 20, 0, 30)}, wicks=(-6, 16), flare=2, lid=LID_OPEN + 4)
    keyed(r, "hurt", 0.6, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.45, _mix(back, _pose(r), 0.6)), (0.6, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it bends away to its right
        h = _pose(r, comp=-0.08, bend=(-8, -30 * sgn), twist=-sgn * 18, head=(-8, -sgn * 20, -sgn * 20), roll=-sgn * 2,
                  arms={"l": (20, 30 + 10 * sgn, 30), "r": (20, 30 - 10 * sgn, 30)}, wicks=(14, -10), flare=14)
        b = _pose(r, comp=0.1, bend=(4, 10 * sgn), head=(4, sgn * 6, sgn * 8), arms={"l": (0, 6, 20), "r": (0, 6, 20)})
        keyed(r, nm, 0.65, [(0, _pose(r)), (0.08, h), (0.32, b), (0.65, _pose(r))])
    keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.3, _flop(r, 0.6)), (0.62, _flop(r, 1.04)), (0.9, _flop(r))])
    keyed(r, "rise", 1.0, [(0, _flop(r)), (0.35, _pose(r, comp=0.35, bend=(10, 0), head=(14, 0, 0), arms={"l": (10, 10, 30), "r": (10, 10, 30)})),
                           (0.6, _pose(r, comp=-0.22, bend=(-10, 0), head=(-12, 0, 0), arms={"l": (30, 24, 20), "r": (30, 24, 20)}, flare=14, wicks=(14, 20))),
                           (0.8, _pose(r, comp=0.06, bend=(4, 0), head=(4, 0, 0))), (1.0, _pose(r))])
    _death(r)


def _flop(r, f=1.0):
    """Flopped forward over the front of the box, f of the way."""
    return _pose(r, comp=0.35 * f, lean=(22 * f, 3 * f), bend=(20 * f, 6 * f), head=(85 * f, 10 * f, 14 * f), pitch=1.5 * f,
                 arms={"l": (45 * f, 10 * f, 20 * f, 10 * f, 20 * f), "r": (48 * f, 12 * f, 24 * f, 10 * f, 26 * f)}, lid=LID_OPEN + 42 * f,
                 wicks=(-20 * f, 30 * f), flare=-6 * f, cord=-6 * f)


def _death(r):
    """Its last: the Jack jerks up out of the box at full stretch and shudders there, its arms flailing; the spring
    goes slack under it and it sags down into the box, swaying, its head lolling out over the front and its arms
    hanging down outside; the lid falls shut on its neck; the crank turns back one click, and a finger twitches."""
    up = _pose(r, comp=-0.45, bend=(-10, 0), head=(-30, 0, 0), arms={"l": (110, 50, 20, 10, -20), "r": (100, 60, 30, 10, -20)}, wicks=(30, -40), flare=24)
    s1 = _pose(r, comp=-0.4, bend=(-4, 14), wave=(8, 0, 0.6), head=(-20, 20, 14), arms={"l": (70, 70, 50, 20, 0), "r": (130, 30, 10, -10, -30)}, wicks=(26, -30), flare=20)
    s2 = _pose(r, comp=-0.38, bend=(-8, -14), wave=(8, 2.5, 0.6), head=(-24, -24, -16), arms={"l": (130, 30, 10, -10, -30), "r": (70, 70, 50, 20, 0)}, wicks=(30, -20), flare=22)
    sag1 = _pose(r, comp=0.3, lean=(12, 2), bend=(24, 8), head=(40, 10, 20), arms={"l": (30, 14, 30, 10, 30), "r": (30, 20, 20, 10, 40)}, wicks=(-10, 30),
                 lid=LID_OPEN + 20)

    def slump(f, twitch=0.0):
        """Sagged down into the box and over its front rim, its head lolling out over the front and its hands on the
        floor before it, the lid down on its neck: f of the way."""
        return _pose(r, comp=0.5, lean=(22 + 4 * f, 3), bend=(16, 0), head=(62 + 33 * f, 10, 20), pitch=2 * (1 - f),
                     arms={"l": (40, 10, 20, 10 + twitch * 10, 26 - 30 * twitch), "r": (42, 12, 22, 14, 34)}, lid=LID_OPEN + 60 + 36 * f,
                     wicks=(-34, 40), flare=-10, cord=-10, crank=-45 * core.ease_io(f))
    keys = [(0, _pose(r)), (0.25, up), (0.5, s1), (0.75, s2), (1.0, s1), (1.25, _mix(s2, up, 0.5)), (1.8, sag1), (2.5, slump(0.0)), (3.1, slump(0.7)),
            (3.4, slump(1.0)), (3.6, slump(0.96)), (3.9, slump(1.0)), (4.3, slump(1.0, 1.0)), (4.5, slump(1.0)), (5.0, slump(1.0))]
    keyed(r, "death", 5.0, _unwrap(keys))


# ---------------------------------------------------------------------------------------------------------------------
# Moves

# The kit's follow-through lag grows with a bone's depth, and the Jack rides on ten links: its arms and head would trail
# the spring by a fifth of a second and pile up at the end of every recovery. They keep the spring's time; their own
# springs (forearms, fingers, head, flaps, wicks) carry the overlap instead.
NO_LAG = {b: 0 for b in ["box", "lid", "crank"] + LINKS + ["collar", "flap_f", "flap_b", "flap_l", "flap_r", "head"]
          + [f"wk_{w}{i}" for w in "clr" for i in (0, 1)] + [f"{p}_{s}" for p in ("arm", "forearm", "hand", "fing") for s in "lr"]}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _turn(r, mv, bone, axis, turns, t0, t1):
    """Whole turns on a bone in a move's clip, after staging: from none at t0 to all of them at t1, eased (a whole turn
    is no turn, so it hands back to the idle as it was)."""
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


def pop(r):
    """The Pop (LIGHT): it sinks down on its spring with its head drawn in and its hands drawn up beside it; then it
    springs out at you head first, the spring flung out long and bent over toward you, its hands grabbing."""
    gather = _pose(r, comp=0.36, bend=(-10, 0), head=(16, 0, 0), arms={"l": (52, 34, 100, 36, 50), "r": (52, 34, 100, 36, 50)}, wicks=(-14, 22), flare=-6)
    peak = _pose(r, comp=0.55, bend=(-14, 0), head=(20, 0, 0), arms={"l": (60, 30, 110, 40, 50), "r": (60, 30, 110, 40, 50)}, wicks=(-16, 26), flare=-8,
                 pitch=-1.5, lid=LID_OPEN - 4)
    strike = _pose(r, comp=-0.4, bend=(58, 0), head=(-10, 0, 0), arms={"l": (95, 20, 10, -10, -20), "r": (95, 20, 10, -10, -20)}, wicks=(20, -30), flare=18,
                   pitch=2.5, lid=LID_OPEN + 6)
    held = _pose(r, comp=-0.44, bend=(64, 0), head=(-6, 4, 6), arms={"l": (90, 10, 30, 10, 40), "r": (92, 12, 34, 10, 46)}, wicks=(24, -24), flare=20, pitch=3,
                 lid=LID_OPEN + 8)
    return Move("pop", "LIGHT", 10, 4, 14, through=held, title="Pop", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.0, knock=0.8,
                hit=[("head", (0, -7, 0), 9.0), ("fing_l", (0, 8, 0), 4.0), ("fing_r", (0, 8, 0), 4.0)], plant=(),
                stages={"windup": [(0.25, _mix(_pose(r), gather, 0.55)), (0.55, gather), (1.0, peak)], "active": [(0.5, strike), (1.0, held)],
                        "recover": _wobble(r, held, 3, 12)}, lag=_lag())


def slap(r):
    """The Slap (LIGHT, after the Pop): it twists away with its right arm drawn back across behind it, then swings it
    round in a long backhand across in front of it, the spring twisting with it and its head turning after."""
    gather = _pose(r, comp=0.12, twist=60, bend=(-8, 18), head=(-6, 30, 12), arms={"r": (36, 80, 50, 20, 14, 24), "l": (16, 30, 40, 10, 16)}, wicks=(14, 14), flare=8)
    peak = _pose(r, comp=0.24, twist=115, bend=(-16, 36), head=(-14, 50, 22), arms={"r": (60, 120, 70, 36, 30, 50), "l": (40, 50, 70, 20, 30)}, wicks=(26, 30),
                 flare=16, roll=2.5)
    strike = _pose(r, comp=-0.15, twist=-50, bend=(24, -28), head=(6, -32, -12), arms={"r": (80, 10, 6, -20, -10, -30), "l": (0, 30, 40)}, wicks=(16, -20), flare=14,
                   roll=-2)
    out = _pose(r, comp=-0.12, twist=-64, bend=(20, -34), head=(4, -40, -14), arms={"r": (70, -20, 10, -10, 0, -40), "l": (-4, 34, 46)}, wicks=(10, -26), flare=12,
                roll=-2.5)
    return Move("slap", "LIGHT", 10, 4, 14, through=out, title="Slap", track=6.0, rng=(0, 4.2), kinds=("combo", "near"), damage=0.9, knock=1.0,
                hit=[("fing_r", (0, 8, 0), 4.5), ("hand_r", (0, 3, 0), 4.0), ("forearm_r", (0, 8, 0), 3.5)], plant=(),
                stages={"windup": [(0.2, _mix(_pose(r), gather, 0.4)), (0.55, gather), (1.0, peak)], "active": [(0.5, strike), (1.0, out)],
                        "recover": _wobble(r, out, 2, 8)}, lag=_lag())


def pop_goes(r):
    """Pop Goes (HEAVY, its delay): it sinks slowly down into the box and the lid comes down over it; the crank ticks
    round, and ticks, and the box trembles; then the lid bursts open and it springs straight up and out at you with its
    arms wide."""
    sink = _pose(r, comp=0.6, low=0.2, bend=(6, 0), head=(16, 0, 0), arms={"l": (120, 10, 140, 20, 50), "r": (120, 10, 140, 20, 50)}, lid=-60, wicks=(-20, 30),
                 flare=-8)
    shut = _hidden(r, lid=-3.0)
    tremble = _hidden(r, lid=-7.0, pitch=1.2, roll=-1.0, lift=0.4)
    burst = _pose(r, comp=-0.6, bend=(34, 0), head=(-14, 0, 0), arms={"l": (110, 70, 10, -20, -40), "r": (110, 70, 10, -20, -40)}, lid=LID_OPEN - 22,
                  wicks=(30, -40), flare=26, pitch=-2, lift=1.0)
    held = _pose(r, comp=-0.52, bend=(40, 0), head=(-6, 6, 8), arms={"l": (96, 60, 30, 0, 10), "r": (100, 56, 30, 0, 14)}, lid=LID_OPEN - 10, wicks=(26, -30),
                 flare=22)
    return Move("popgoes", "HEAVY", 16, 6, 22, through=held, title="Pop Goes", hold=10, track=4.0, rng=(0, 3.8), kinds=("near", "area"), damage=1.3, knock=1.4,
                disc=2.5, hit=[("head", (0, -7, 0), 10.0), ("fing_l", (0, 8, 0), 5.0), ("fing_r", (0, 8, 0), 5.0)], plant=(),
                stages={"windup": [(0.2, _mix(_pose(r), sink, 0.3)), (0.5, sink), (0.82, shut), (1.0, tremble)], "active": [(0.35, burst), (1.0, held)],
                        "recover": _wobble(r, held, 3, 14, 0.22)}, lag=_lag())


def bound(r):
    """The Bound (HEAVY, its gap closer): it sinks deep onto its spring and the box tips back on its hind edge; then
    the whole box jumps, the Jack flung up at full stretch over it, and comes down on you, the Jack slammed down into
    the box by the landing and springing up again with its arms flung out."""
    crouch = _pose(r, comp=0.66, bend=(-12, 0), head=(26, 0, 0), arms={"l": (56, 36, 116, 30, 44), "r": (56, 36, 116, 30, 44)}, pitch=-8, wicks=(-24, 40),
                   flare=-12)
    leap = _pose(r, lift=26, shift=(0, -6), pitch=-8, comp=-0.4, bend=(-22, 0), head=(-14, 0, 0), arms={"l": (80, 84, 14, -6, -16), "r": (80, 84, 14, -6, -16)},
                 wicks=(24, -40), flare=22, lid=LID_OPEN - 14)
    top = _pose(r, lift=30, shift=(0, -14), pitch=6, comp=-0.2, bend=(10, 0), head=(0, 0, 0), arms={"l": (90, 60, 20, 0, -10), "r": (90, 60, 20, 0, -10)},
                wicks=(20, -20), flare=20, lid=LID_OPEN - 6)
    land = _pose(r, lift=0, shift=(0, -16), pitch=3, comp=0.7, bend=(18, 0), head=(24, 0, 0), arms={"l": (30, 64, 80, 20, 50), "r": (30, 64, 80, 20, 50)},
                 wicks=(-26, 40), flare=-8, lid=LID_OPEN + 12)
    up = _pose(r, shift=(0, -16), comp=-0.3, bend=(-8, 0), head=(-12, 0, 0), arms={"l": (84, 76, 64, 10, 40), "r": (84, 76, 64, 10, 40)}, flare=18, wicks=(24, -16))
    settle = _pose(r, shift=(0, -16), comp=0.1, bend=(4, 0), head=(4, 0, 0), arms={"l": (6, 14, 16), "r": (6, 14, 16)})
    sway = _pose(r, shift=(0, -16), comp=-0.08, bend=(-6, 3), head=(-8, 6, 4), arms={"l": (24, 26, 24), "r": (20, 22, 26)}, flare=6)
    return Move("bound", "HEAVY", 14, 12, 22, through=up, title="The Bound", track=6.0, lock=10, advance=7.0, rng=(5, 14), kinds=("gap", "area"), damage=1.3,
                knock=1.2, disc=3.0, plant=(), hit=[("box", (0, -16, 0), 18.0), ("head", (0, -7, 0), 9.0)],
                stages={"windup": [(0.3, _mix(_pose(r), crouch, 0.5)), (1.0, crouch)], "active": [(0.3, leap), (0.62, top), (1.0, land)],
                        "recover": [(0.45, _mix(up, settle, 0.7)), (0.7, sway)]}, lag=_lag())


def lines(r):
    """Fuse Lines: it rises high on its spring and throws its head back, its arms spread like a showman's; then it
    whips its head down at you twice, flinging its sparks, and the lines of fire run."""
    rise = _pose(r, comp=-0.3, bend=(-16, 0), head=(-34, 0, 0), arms={"l": (60, 80, 30, -20, -20), "r": (60, 80, 30, -20, -20)}, wicks=(30, -40), flare=14)
    top = _pose(r, comp=-0.42, bend=(-22, 0), head=(-44, 0, 6), arms={"l": (80, 90, 20, -30, -30), "r": (80, 90, 20, -30, -30)}, wicks=(36, -50), flare=18)
    whip1 = _pose(r, comp=-0.2, bend=(30, 0), head=(36, 0, 0), arms={"l": (50, 70, 40), "r": (50, 70, 40)}, wicks=(-20, 50), flare=10)
    back = _pose(r, comp=-0.34, bend=(-10, 0), head=(-26, 0, -6), arms={"l": (64, 80, 30), "r": (64, 80, 30)}, wicks=(24, -30), flare=16)
    whip2 = _pose(r, comp=-0.18, bend=(34, 0), head=(40, 0, 0), arms={"l": (46, 66, 44), "r": (46, 66, 44)}, wicks=(-24, 54), flare=8)
    held = _pose(r, comp=-0.1, bend=(26, 0), head=(30, 6, 8), arms={"l": (40, 60, 50, 10, 20), "r": (42, 58, 50, 10, 24)}, wicks=(-14, 40), flare=6)
    return Move("lines", "NONE", 14, 18, 16, through=held, title="Fuse Lines", track=8.0, ability="Fuse Lines", rng=(4, 16), kinds=("ranged",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.4, rise), (1.0, top)], "active": [(0.2, whip1), (0.45, back), (0.65, whip2), (1.0, held)],
                                  "recover": _wobble(r, held, 2, 8)}, lag=_lag())


def lid_slam(r):
    """The Lid (LIGHT, for whoever is behind it): it ducks forward on its spring and the lid comes up off its hinge;
    then the lid slams back over the hinge and down onto the floor behind the box."""
    gather = _pose(r, comp=0.3, bend=(30, 0), head=(20, 0, 0), arms={"l": (10, 20, 40), "r": (10, 20, 40)}, lid=-70, wicks=(-10, 20), pitch=1.5)
    peak = _pose(r, comp=0.5, bend=(70, 0), twist=20, head=(40, 10, 0), arms={"l": (-40, 40, 80, 20, 40), "r": (-40, 40, 80, 20, 40)}, lid=-44, wicks=(-30, 50),
                 pitch=3, flare=-12)
    strike = _pose(r, comp=0.2, bend=(30, 0), head=(14, 0, 0), arms={"l": (20, 30, 40), "r": (20, 30, 40)}, lid=-238, wicks=(-6, 20), pitch=-2.5)
    out = _pose(r, comp=0.26, bend=(32, 0), head=(16, 0, 4), arms={"l": (18, 32, 44), "r": (22, 30, 40)}, lid=-246, wicks=(-6, 22), pitch=-1.5)
    return Move("lid", "LIGHT", 10, 4, 14, through=out, title="The Lid", track=0.0, rng=(0, 3.4), kinds=("behind",), damage=1.0, knock=1.2,
                hit=[("lid", (0, -1.5, -BOX), 7.0), ("lid", (0, -1.5, -HALF), 7.0)], plant=(),
                stages={"windup": [(0.2, _mix(_pose(r), gather, 0.4)), (0.55, gather), (1.0, peak)], "active": [(0.5, strike), (1.0, out)],
                        "recover": _back(r, out)}, lag=_lag())


def shorter(r):
    """Shorter Fuse (into the second phase): it shakes and bounces on its spring, cackling, its head thrown back and
    its arms hugging itself and flung out, while the crank whirls round and its fuses flare."""
    def bob(j, k):
        return _pose(r, comp=0.3 * j - 0.1, bend=(-10 * j, 8 * k), head=(-30 + 12 * j, 10 * k, 14 * k),
                     arms={"l": (40 + 30 * j, 20 + 60 * (1 - j), 60 * j + 10), "r": (40 + 30 * j, 20 + 60 * (1 - j), 60 * j + 10)}, wicks=(30, -20 - 20 * j), flare=16 - 8 * j,
                     lift=0.6 * (1 - j), pitch=1.2 * k)
    act = [(0.1, bob(1, 1)), (0.22, bob(0, -1)), (0.34, bob(1, 0.6)), (0.46, bob(0, -0.6)), (0.58, bob(1, 1)), (0.7, bob(0, -1)), (0.84, bob(0.8, 0.4)),
           (1.0, bob(0.2, 0))]
    return Move("shorter", "NONE", 12, 32, 18, through=bob(0.4, 0), title="Shorter Fuse", track=0.0, rng=(0, 99), kinds=("transition",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), bob(0.6, 0), 0.5)), (1.0, bob(0.6, 0))], "active": act, "recover": _wobble(r, bob(0.4, 0), 3, 10)},
                lag=_lag())


def ring(r):
    """The Blast Ring (PERILOUS, the signature): it crams itself down into the box and the lid slams shut on it; the box
    shudders and jolts, light leaking out from under the lid; then the lid is blown back, the Jack shoots up out of the
    box at full stretch with its arms flung wide, and the ring goes out over you; only right beside the box are you
    safe."""
    cram = _pose(r, comp=0.7, low=0.3, bend=(4, 0), head=(20, 0, 0), arms={"l": (150, 8, 150, 20, 60), "r": (150, 8, 150, 20, 60)}, lid=-40, wicks=(-30, 40),
                 flare=-10, pitch=1)
    slam = _hidden(r, lid=-1.0, pitch=0.6, lift=0.2)

    def shudder(j):
        return _hidden(r, lid=-5.0 - 4 * abs(j), pitch=1.6 * j, roll=-1.4 * j, lift=0.8 * abs(j), yaw=1.2 * j)
    blown = _pose(r, comp=-0.7, bend=(-8, 0), head=(-36, 0, 0), arms={"l": (150, 70, 10, -30, -40), "r": (150, 70, 10, -30, -40)}, lid=LID_OPEN - 70,
                  wicks=(40, -50), flare=30, lift=0.6, pitch=-2.5)
    held = _pose(r, comp=-0.6, bend=(-4, 0), head=(-28, 0, 8), arms={"l": (140, 76, 20, -20, -20), "r": (144, 72, 20, -20, -26)}, lid=LID_OPEN - 46, wicks=(36, -40),
                 flare=26)
    return Move("ring", "PERILOUS", 30, 12, 30, through=held, title="Blast Ring", signature=True, track=0.0, ability="Blast Ring", rng=(0, 9.0),
                kinds=("area",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.2, _mix(_pose(r), cram, 0.5)), (0.45, cram), (0.55, slam), (0.65, shudder(1)), (0.75, shudder(-1)), (0.85, shudder(0.8)),
                                   (1.0, shudder(-0.6))],
                        "active": [(0.3, blown), (1.0, held)], "recover": _wobble(r, held, 4, 16, 0.28)}, lag=_lag())


def wake(r):
    """Its intro, "Hsssss.": the box stands shut and still; the crank turns, a click at a time, on its own, and the
    lit fuse on it hisses; the box trembles; then the lid flies open and the Jack springs up out of it, wobbling on its
    spring, its back to you; and it turns its head round, slowly, all the way, to grin at you."""
    act = []
    for kk in range(1, 25):
        f = kk / 24
        if f < 0.55:  # shut, still, a tremble
            j = math.sin(2 * math.pi * 6 * f) * max(0.0, (f - 0.35) / 0.2) if f > 0.35 else 0.0
            act.append((f, _hidden(r, lid=-3.0 - 3 * abs(j), pitch=0.8 * j, lift=0.3 * abs(j), crank=0.0, cord=6 * math.sin(9 * f))))
        else:
            u = (f - 0.55) / 0.45
            pop_ = core.ease_out(min(1.0, u / 0.18))
            wob = math.sin(2 * math.pi * 2.2 * u) * (1 - u) ** 1.4
            turn = 180 * (1 - core.ease_io(max(0.0, (u - 0.35) / 0.65)))
            act.append((f, _mix(_hidden(r), _pose(r, comp=-0.2 * wob - 0.1 * (1 - u), bend=(-14 * wob, 6 * wob), head=(-8 * wob, turn, 10 * math.sin(math.pi * u)),
                                                  arms={"l": (40 * wob + 10, 30, 20), "r": (40 * wob + 10, 30, 20)}, flare=10 * abs(wob) + 4,
                                                  wicks=(10 * wob, -20 * wob)), pop_)))
    end = act[-1][1]
    return Move("wake", "NONE", 16, 48, 16, through=_mix(end, _pose(r), 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), _hidden(r), 0.5)), (1.0, _hidden(r))], "active": act, "recover": _back(r, end)}, lag=_lag())


# the ground (SPEC §35.10): the hands and fingers, the head and the lid stay out of the floor
HAND_GROUND = [([f"fing_{n}", f"hand_{n}"], [(f"hand_{n}", 0, 40), (f"forearm_{n}", 0, 40), (f"arm_{n}", 0, 30)]) for n in ("l", "r")]
HEAD_GROUND = [(["head"], [("s9", 0, 30), ("s8", 0, 30)])]
LID_GROUND = [(["lid"], [("lid", 0, 40)])]


def _drift(r, clip, ramp=0.25):
    """Under a move, once it is made: its fuses bob, its fingers flex and the crank's lit end sways, faded in and out
    at the ends so it hands back to the idle exactly as it was."""
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
    w = 2 * math.pi
    for j, n_ in enumerate(("c", "l", "r")):
        add(f"wk_{n_}1", lambda t, j=j: (6.0 * math.sin(w * 1.1 * t + 2.1 * j), 0.0, 5.0 * math.sin(w * 0.8 * t + 1.3 * j)))
    for j, n_ in enumerate(("l", "r")):
        add(f"fing_{n_}", lambda t, j=j: (8.0 * math.sin(w * 1.4 * t + 2.5 * j), 0.0, 0.0))
    add("cord1", lambda t: (6.0 * math.sin(w * 0.9 * t), 0.0, 3.0 * math.sin(w * 0.6 * t + 1.0)))


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [pop(r), slap(r), pop_goes(r), bound(r), lines(r), lid_slam(r), shorter(r), ring(r), wake(r)]
    crank_turns = {"popgoes": (2, 0.25, 0.95), "lines": (1, 0.0, 0.8), "shorter": (4, 0.15, 0.95), "ring": (3, 0.3, 0.7), "wake": (2, 0.1, 0.6)}
    for m in mv:
        stage_clips(r, m)
        if m.id in crank_turns:
            k, a, b = crank_turns[m.id]
            L = r.clips[core.clip_name(m)].length
            _turn(r, m, "crank", 0, k, a * L, b * L)
        _drift(r, core.clip_name(m))
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, HAND_GROUND, clips=everyday)
    core.ground_pass(r, HEAD_GROUND + LID_GROUND, clips=everyday)
    core.ground_pass(r, HAND_GROUND + HEAD_GROUND + LID_GROUND, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): a creeper's hiss under everything, a lit fuse, a music box's tinkle and the clack of the lid;
# chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.creeper.primed", 0.7, 1.4), ("block.wooden_trapdoor.open", 0.6, 1.2)],
    "tell_heavy": [("entity.creeper.primed", 1.0, 0.9), ("block.note_block.chime", 0.6, 0.7), ("block.wooden_trapdoor.close", 0.8, 0.8)],
    "tell_perilous": [("entity.creeper.primed", 1.4, 0.6), ("entity.tnt.primed", 1.2, 0.7), ("block.note_block.chime", 0.8, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 0.7, 1.3), ("block.chain.step", 0.8, 1.4)],
    "swing_heavy": [("entity.player.attack.sweep", 1.0, 0.8), ("block.chain.fall", 1.0, 0.8)],
    "cast": [("entity.tnt.primed", 1.0, 1.2), ("block.fire.ambient", 1.2, 1.4)],
    "impact": [("entity.generic.explode", 0.7, 1.4), ("block.wood.fall", 1.2, 0.6), ("block.chain.fall", 0.8, 0.9)],
    "roar": [("entity.creeper.primed", 1.6, 0.5), ("block.note_block.bell", 0.8, 0.5), ("entity.witch.celebrate", 0.7, 0.6)],
    "step": [("block.wood.step", 0.9, 0.7), ("block.chain.step", 0.6, 1.0)],
}
MOVE_SOUNDS = {"lines": {"swing": "cast"}, "shorter": {"swing": "roar", "impact": ""}, "ring": {"swing": "roar"},
               "wake": {"swing": "roar", "impact": ""}, "lid": {"impact": "impact"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # the box shut; the crank ticks; it pops
    "phases": [
        {"pool": [
            {"move": "pop", "weight": 3, "when": "near", "then": [["slap"], ["slap", "pop"]]},
            {"move": "slap", "weight": 2, "when": "near"},
            {"move": "popgoes", "weight": 2, "when": "near"},
            {"move": "bound", "weight": 2, "when": "far", "then": [["pop"]]},
            {"move": "lines", "weight": 3, "when": "far"},
            {"move": "lid", "weight": 3, "when": "behind"},
            {"move": "ring", "weight": 1},
        ]},
        # a shorter fuse, as in DeepBestiary: more lines, a wider ring
        {"transition": "shorter", "pool": [
            {"move": "pop", "weight": 3, "when": "near", "then": [["slap"], ["slap", "pop"]]},
            {"move": "popgoes", "weight": 2, "when": "near"},
            {"move": "bound", "weight": 2, "when": "far", "then": [["pop", "slap"]]},
            {"move": "lines", "weight": 3, "when": "far"},
            {"move": "lid", "weight": 3, "when": "behind"},
            {"move": "ring", "weight": 2},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.3},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.1, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
