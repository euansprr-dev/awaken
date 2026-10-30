"""The Signal's Shadow (SPEC §35.11, docs/bosses/ROLLOUT.md): the Deep's last boss, a Duelist, waiting at the Seed. The
Signal Cuttlefish, picked from its silhouette round 2026-10-29: "It has your voice."

A cuttlefish as long as a cart, hanging in the dark at the bottom of the Well. A cuttlefish talks with its skin, and
this one's skin talks the way the Signal does: over its black mantle drift bands of the Signal's own windows, pale
glass with bright corner brackets and lines of writing in them, the words you have read all game; here and there one is
written in amber, the colour of "NOT TRUE YET". Its fins ripple all down both its sides, edged with the Signal's blue.
Its eyes are big and pale with a black W across each. Its eight arms are held together in a crown in front of its face,
and hidden inside them are the two long tentacles it shoots out at you like lances.

Moves (SPEC §35.11): the Lash and the Grasp (LIGHT, a string: its crown of arms flares open and lashes down across you,
then its arms reach out and close on you); the Lance (HEAVY, its gap closer: its arms part and its two tentacles shoot
out at you and snap back); Instruction (it points its arms at you in a cone and holds you in its eyes while the beam
runs); Signal Step (it squeezes, jets backwards and is gone); Directive (it lifts its top arms and flicks five bolts);
Echoes (it spreads its arms and its skin pulses, and its echoes come); Signal Marks (it rises with its arms hanging like
a chandelier and pulses six times); Your Echoes (it jets aside twice, leaving ink in its own shape); Come Down (it
reaches every arm and both tentacles at you and draws them in); Signal Halo (it rises and flares its arms out in a star
round its face); Last Instruction (it rears its arms up over its head in a warning display and shudders); for whoever is
behind it, the Recoil (it jets backwards into them); and Seed Collapse (the signature: it draws every arm in and squeezes
itself small and tight, and bursts open, its arms flung wide). Its intro: it drifts down out of the dark with its arms
closed, its skin blank; the windows wake across it; it opens its crown of arms and looks at you. Broken, it sinks to the
floor with its arms spread limp over it; it dies there, its fins slowing, its arms curling in, its mantle going flat.
"""
import math

from kit import core, ik, parts
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

DEG = math.pi / 180.0
ID = "signals_shadow"
SCALE = 2.0  # the def's scale; the body is modelled at true size and the hitbox set round its body

# The Signal Cuttlefish (docs/bosses/ROLLOUT.md, A). About 88 px from the tips of its arms to the end of its mantle (5.5
# blocks), its mantle 28 px across (44 with its fins) and its eyes 28 px up (1.8 blocks, your eyes' height) as it hangs
# in the air. Value story: its eyes and the windows' brackets brightest (white and the Signal's blue), then the
# writing in the windows and the line along its fins, then the deep glass inside the windows; the black of its mantle
# between the bands darkest; its underside a dusk grey-blue.
HOVER = 26.0  # its middle, this far up off the floor
FLOAT_Z = 6.0
SIDES_ = (("l", 1), ("r", -1))
# its arms round the front of its head (x, up): the top pair, the two side pairs, the bottom pair (even: its left)
ARM_AT = ((3.0, 4.0), (-3.0, 4.0), (7.0, 1.5), (-7.0, 1.5), (6.5, -2.5), (-6.5, -2.5), (2.5, -5.0), (-2.5, -5.0))
ARM_SEG = ((5, 9), (4, 8), (3, 7))  # each arm's three pieces: (thickness, length)
ARM_OUT = 4.0  # at rest its arms part a little from each other,
ARM_DOWN = (2.0, 2.0, 8.0, 8.0, 12.0, 12.0, 15.0, 15.0)  # hang forward and down, the lower ones lower,
ARM_CURL = (0.0, 7.0, 9.0)  # and curl down along their length
ARMS = tuple(f"arm{k}{s}" for k in range(8) for s in "abc")
# its fin, a skirt all down both sides of its mantle in eight pieces: (which mantle bone, out at x, z on it, how far out)
FIN = (("mantle", 15, -8, 8), ("mantle", 15, -3, 8), ("mantle", 15, 2, 8), ("mantle", 15, 7, 8), ("mantle2", 14, 1, 7),
       ("mantle2", 14, 6, 7), ("mantle2", 13, 11, 6), ("mantle2", 9, 16, 5))
FIN_LONG = 5
TENT = (2, 10)  # its feeding tentacles' stalks (thickness, length) at rest, tucked inside its arms


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def _ring(k):
    """An arm's place round its crown: (cos, sin) of its angle seen from the front (x its left, up)."""
    x, u = ARM_AT[k]
    n = math.hypot(x, u)
    return x / n, u / n


def _toward(k, a):
    """The turn that leans arm k (pointing forward) a degrees in toward the middle of its crown (- out from it)."""
    c, s = _ring(k)
    return (a * s, a * c, 0.0)


def body():
    r = R.Rig(ID, tex=(512, 512))
    base = r.bone("base", (0, 24, 0))
    fl = _bone(base, "float", 0, HOVER, FLOAT_Z)
    mantle = _bone(fl, "mantle", 0, 0, -8)
    # the mantle: a flattened oval, wider than it is deep, its edges bulging where the fin runs and its end tapering
    # round; a low hump down its back over the cuttlebone and a flat belly
    _box(mantle, "mantle_f", 0, -6, 1, 26, 12, 22)
    _box(mantle, "mantle_fs", 0, -4, 2, 30, 7, 18)
    _box(mantle, "mantle_ft", 0, 6, 2, 22, 2, 20)
    _box(mantle, "mantle_ft2", 0, 8, 3, 14, 2, 16)
    _box(mantle, "mantle_fb", 0, -8, 1, 20, 2, 20)
    m2 = _bone(mantle, "mantle2", 0, 0, 12)
    _box(m2, "mantle_b", 0, -5.5, 7, 24, 11, 14)
    _box(m2, "mantle_bs", 0, -3.5, 5, 28, 6, 10)
    _box(m2, "mantle_bt", 0, 5.5, 6, 20, 2, 12)
    _box(m2, "mantle_bt2", 0, 7.5, 5, 12, 2, 10)
    _box(m2, "mantle_bb", 0, -7.5, 7, 18, 2, 12)
    _box(m2, "mantle_e", 0, -4.5, 16.5, 18, 9, 5)
    _box(m2, "mantle_t", 0, -3, 20.5, 10, 6, 3)
    # its fin all down both sides, in pieces that ripple one after another
    for nm, sg in SIDES_:
        for i, (on, x, z, out) in enumerate(FIN):
            fb = _bone(mantle if on == "mantle" else m2, f"fin_{nm}{i}", sg * x, -0.5, z)
            _box(fb, f"fin_{nm}{i}", sg * out / 2, -0.5, 0, out, 1, FIN_LONG)
    # the head: narrower than its mantle, its great eyes bulging high on either side under a lid, its funnel under it
    hd = _bone(mantle, "head", 0, 0, -10)
    _box(hd, "head", 0, -6.5, -5.5, 20, 13, 11)
    _box(hd, "brow", 0, 6.5, -5, 12, 2, 8)
    for nm, sg in SIDES_:
        _box(hd, f"eye_{nm}", sg * 10, -2, -5.5, 4, 9, 9)
        _box(hd, f"lid_{nm}", sg * 9.5, 7, -5.5, 5, 2, 10)
    _box(hd, "siphon", 0, -9.5, -3, 5, 3, 6)
    # its eight arms in a crown round its mouth, hanging forward and down; inside them, its two tentacles, folded short
    for k, (x, u) in enumerate(ARM_AT):
        lean = _toward(k, -ARM_OUT)
        b = _bone(hd, f"arm{k}a", x, u, -11, (lean[0] + ARM_DOWN[k], lean[1], 0))
        for s_, (w, ln) in enumerate(ARM_SEG):
            _box(b, f"arm{k}{'abc'[s_]}", 0, -w / 2, -ln / 2, w, w, ln)
            if s_ < 2:
                b = b.bone(f"arm{k}{'abc'[s_ + 1]}", (0, 0, -ln), (ARM_CURL[s_ + 1], 0, 0))
        _box(b, f"armt{k}", 0, -1, -8, 2, 2, 2)
    for nm, sg in SIDES_:
        t = _bone(hd, f"tent_{nm}", sg * 1.5, -2.0, -10, (16.0, -sg * 2.0, 0))
        _box(t, f"tent_{nm}", 0, -1, -TENT[1] / 2, TENT[0], TENT[0], TENT[1])
        c = t.bone(f"club_{nm}", (0, 0, -TENT[1]), (0, 0, 0))
        _box(c, f"club_{nm}", 0, -1.5, -3, 3, 3, 6)
        _box(c, f"clubp_{nm}", 0, -2.5, -3, 4, 1, 5)
    r.look = [("head", 0.4, 0.3), ("mantle", 0.2, 0.12)]
    r.shadow = 3.2
    r.size = 1.0
    # secondary parts (SPEC §35.10): the ends of its arms trail
    r.springs = [(f"arm{k}c", 0.5, 2.0, 0.12) for k in range(8)]
    r.guard = [f"arm{k}c" for k in range(8)] + ["head", "mantle", "mantle2"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#06050b", "#0b0913", "#110e1c", "#181326", "#1f1931", "#28203e", "#31284c", "#3c325b", "#483d6b")
BELLY = Ramp("#1c1a26", "#262333", "#312d40", "#3d384e", "#4a445d", "#58526c", "#68627c")
SIG = ("#ffffff", "#d8efff", "#a9d8ff", "#7fc3ff", "#4f97d6", "#2a5f94", "#173a5e")
AMBER = ("#fff1d2", "#ffcf7a", "#c9923e")


def _blot(i, j, seed):
    """A cuttlefish's mottle: soft blotches (-1 to 1) over its skin."""
    return (math.sin(i * 0.9 + seed) * math.sin(j * 0.7 + seed * 1.3) + 0.5 * math.sin((i + j) * 0.45 + seed * 2.1)
            + 0.35 * math.sin((i - 2 * j) * 0.6 + seed * 0.7)) / 1.85


def _mottle(f, ramp, base, seed, specks=True):
    """Living skin: soft lighter and darker blotches, and the odd pale fleck."""
    for i, j in f.each():
        b = _blot(i, j, seed)
        v = base + (0.9 if b > 0.35 else -0.6 if b < -0.4 else 0.15 * b)
        if specks and (i * 37 + j * 61 + seed * 13) % 29 == 0:
            v = base + 2.6
        f.set(i, j, ramp.at(v))


def _windows(f, gl, seed, rows=((0.5, 5),), w=8, gap=2):
    """The Signal's windows passing over its skin in bands: [(where the band runs, 0 to 1 down the face, its panes'
    height)], each pane deep glass with bright corner brackets and a line or two of writing; the odd one in amber."""
    for at, h in rows:
        j0 = int(round(at * f.h - h / 2))
        off = (seed * 3) % (w + gap)
        for i, j in f.each():
            jj = j - j0
            if not 0 <= jj < h:
                continue
            ii = (i + off) % (w + gap)
            col = (i + off) // (w + gap)
            if ii >= w:
                continue
            h_ = (col * 17 + seed * 7 + int(at * 10)) % 11
            if h_ == 0:
                continue  # a pane gone dark
            amber = h_ == 5
            corner = (ii in (0, w - 1) and jj in (0, 1, h - 2, h - 1)) or (jj in (0, h - 1) and ii in (0, 1, w - 2, w - 1))
            if corner:
                f.set(i, j, hexc(AMBER[1] if amber else SIG[2], 255))
                gl.set(i, j, hexc(AMBER[1] if amber else SIG[3], 200))
            elif 1 <= jj <= h - 2 and 2 <= ii <= w - 3 and (jj % 2 == 1 or h <= 4) and (ii + jj + col) % 5 != 0:
                f.set(i, j, hexc(AMBER[2] if amber else SIG[4], 255))  # its writing
                gl.set(i, j, hexc(AMBER[2] if amber else SIG[4], 110))
            else:
                f.set(i, j, hexc(SIG[6], 255))  # the deep glass


def paint(r, P_):
    """Deep violet-black skin, mottled and flecked like a cuttlefish's, with bands of the Signal's windows passing over
    its back and down its sides; a dusky underside; a bright line of the Signal's blue along the roots of its fins and
    their edges; its arms banded, the top pair it raises banded in the Signal's blue, their suckers pale underneath; its
    eyes big and pale, lit, a black W across each."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    skin = of("mantle_") + ["head", "brow"] + of("lid_")
    for k, c in enumerate(skin):
        P_.paint(c, SKIN, 3.0, None, grad=1.4, rim=1.2, ao=1.0, noise=0.2, nscale=3)
        for fname in SIDES + ("top",):
            _mottle(P_.face(c, fname), SKIN, 3.0 if fname == "top" else 2.6, k * 3 + len(fname))
    # the windows passing over it: bands across its back, a band along each side
    tops = {"mantle_ft2": ((0.2, 5), (0.7, 5)), "mantle_ft": ((0.5, 4),), "mantle_bt2": ((0.45, 5),), "mantle_bt": ((0.8, 4),),
            "mantle_e": ((0.5, 3),), "brow": ((0.5, 4),)}
    for k, (c, rows) in enumerate(tops.items()):
        _windows(P_.face(c, "top"), P_.face(c, "top", glow=True), k, rows)
    for k, c in enumerate(("mantle_f", "mantle_b", "mantle_fs", "mantle_bs", "mantle_e")):
        for fname in ("west", "east"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            if c in ("mantle_f", "mantle_b"):
                _windows(f, gl, k + 5, ((0.3, 4),), w=6, gap=3)
            line = 0.45 if c in ("mantle_fs", "mantle_bs") else 0.62
            for i, j in f.each():
                if j > f.h * line:  # under the line of its fins the skin goes dusky
                    f.set(i, j, BELLY.at(2.2 + 0.8 * (j / f.h) + (1.6 if (i * 37 + j * 61) % 23 == 0 else 0.0)))
                elif abs(j - f.h * line) < 0.6 and c in ("mantle_fs", "mantle_bs"):  # the bright line along its fins' roots
                    f.set(i, j, hexc(SIG[3], 255))
                    gl.set(i, j, hexc(SIG[3], 200))
    for c in ("siphon",) + tuple(of("mantle_fb", "mantle_bb")):
        P_.paint(c, BELLY, 3.0, None, grad=1.2, rim=1.0, ao=0.8, noise=0.2, nscale=3)
    for c in of("mantle_") + ["head"]:
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, BELLY.at(3.0 + 0.4 * ((i + j * 2) % 5 == 0)))
    # the fins: thin and dark, their edges and roots in the Signal's blue, faint ribs across them
    for c in of("fin_"):
        P_.paint(c, SKIN, 3.6, None, grad=0.8, rim=0.8, ao=0.2, noise=0.15, nscale=2)
        sg = 1 if c.startswith("fin_l") else -1
        for fname in ("top", "bottom"):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                out = i if sg > 0 else f.w - 1 - i  # 0 at its root
                if out == f.w - 1:
                    f.set(i, j, hexc(SIG[2], 255))
                    gl.set(i, j, hexc(SIG[3], 210))
                elif j % 2 == 0:
                    f.set(i, j, SKIN.at(5.4 if fname == "top" else 4.6))
                else:
                    f.set(i, j, SKIN.at(3.4 if fname == "top" else BELLY and 2.8))
        for fname in SIDES:
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                f.set(i, j, hexc(SIG[3], 255))
                gl.set(i, j, hexc(SIG[3], 150))
    # the head: a line of writing across its face under its eyes, the rest mottled
    for fname in ("north", "west", "east"):
        f, gl = P_.face("head", fname), P_.face("head", fname, glow=True)
        for i, j in f.each():
            if j == 7 and (i * 5) % 11 not in (0, 1, 2):
                f.set(i, j, hexc(SIG[4], 255))
                gl.set(i, j, hexc(SIG[4], 110))
    # its arms: banded dark and pale (the top pair, the ones it raises to warn, in the Signal's blue), their suckers in
    # pale rows underneath
    for c in of("arm", "armt"):
        P_.paint(c, SKIN, 3.0, None, grad=1.2, rim=1.2, ao=0.6, noise=0.2, nscale=2)
        k = int(c[4]) if c.startswith("armt") else int(c[3])
        top = k in (0, 1)
        for fname in SIDES + ("top",):
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            _mottle(f, SKIN, 2.8, k * 5 + len(c), specks=False)
            for i, j in f.each():
                if j % 5 in (0, 1) and fname != "top" or fname == "top" and j % 5 == 0:
                    if top:
                        f.set(i, j, hexc(SIG[3], 255))
                        gl.set(i, j, hexc(SIG[3], 160))
                    else:
                        f.set(i, j, BELLY.at(4.6))
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, BELLY.at(5.6) if (i % 2 == 0 and j % 2 == 0) else BELLY.at(2.2))
    for c in of("tent_", "club_", "clubp_"):
        P_.paint(c, BELLY, 3.6, None, grad=1.0, rim=1.0, ao=0.4, noise=0.2, nscale=2)
    for c in of("clubp_"):
        for fname in ("bottom", "top") + SIDES:
            f, gl = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                if (i + j) % 2 == 0:
                    f.set(i, j, hexc(SIG[1], 255))
                    gl.set(i, j, hexc(SIG[2], 170))
    _eyes(P_)
    return P_.skin, P_.glow


W_PUPIL = ("1001001", "1101011", "0110110")  # the W across its eye: three points along the top, two dips below


def _eyes(P_):
    """Its eyes: big and round and pale, a silver-blue ring round each, a black W across the middle (a cuttlefish's
    pupil), a white glint, under a dark lid."""
    for nm, fname in (("l", "east"), ("r", "west")):
        f, gl = P_.face(f"eye_{nm}", fname), P_.face(f"eye_{nm}", fname, glow=True)
        cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
        for i, j in f.each():
            fwd = i if fname == "west" else f.w - 1 - i  # 0 at the front of its eye
            d = math.hypot(fwd - cx, j - cy)
            if d > 4.3:
                f.set(i, j, SKIN.at(1.0))  # round: the skin round it
                continue
            row, col = j - int(cy) + 1, fwd - int(cx) + 3
            if 0 <= row < 3 and 0 <= col < 7 and W_PUPIL[row][col] == "1":
                f.set(i, j, SKIN.at(0.0))
                continue
            if fwd == int(cx) - 2 and j == int(cy) - 2:
                f.set(i, j, hexc(SIG[0], 255))  # the glint
                gl.set(i, j, hexc(SIG[0], 255))
            elif d > 3.3:
                f.set(i, j, hexc(SIG[4], 255))
                gl.set(i, j, hexc(SIG[4], 200))
            else:
                f.set(i, j, hexc(SIG[1], 255))
                gl.set(i, j, hexc(SIG[2], 230))
        for fn2 in ("top", "north", "south", "bottom"):
            f = P_.face(f"eye_{nm}", fn2)
            for i, j in f.each():
                f.set(i, j, SKIN.at(1.8))




# ---------------------------------------------------------------------------------------------------------------------
# Motion: one pose call (its body, its arms, its fin and its tentacles)

FIN_LAG = 0.75  # radians each piece of its fin trails the one ahead: the ripple runs from its head back to its tail
ARM_LAG = 0.9  # radians each piece of an arm trails the one before: a writhe runs out along it
NFIN = len(FIN)
BODY = ("mantle", "mantle2", "head")  # what lies on the floor when it does
ONE = (1.0, 1.0, 1.0)


def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


def _arms(p, spread=0.0, droop=0.0, curl=0.0, bend=0.0, reach=0.0, raise_=0.0, wave=0.0, ph=0.0, each=None):
    """Its eight arms: spread leans them out from the middle of its crown at the root (+, degrees; - in), droop lowers
    them at the root (+ down), curl bends them down along their length (+; - up), bend bends them in toward the middle
    of the crown along their length (+; - out, flaring), reach straightens them (1: their droop and curl at rest gone,
    pointing straight ahead), raise_ lifts the top pair at the root (+ up), wave runs a slow writhe out along each (its
    amplitude, at phase ph, each arm a little behind the one before), each {arm: (a, b, c) turns} adds to one arm."""
    for k in range(8):
        lean = _toward(k, -spread)
        top = raise_ if k < 2 else 0.0
        segs = [(lean[0] + droop - reach * ARM_DOWN[k] - top + 0.3 * wave * math.sin(ph - 0.8 * k), lean[1], 0.0)]
        inn = _toward(k, bend)
        for s in (1, 2):
            q = ph - ARM_LAG * s - 0.8 * k
            segs.append((curl - reach * ARM_CURL[s] + inn[0] + wave * math.sin(q), inn[1] + 0.6 * wave * math.cos(q - 0.5), 0.0))
        if each and k in each:
            segs = [tuple(x + y for x, y in zip(a, b)) for a, b in zip(segs, each[k])]
        for s, v in enumerate(segs):
            p[f"arm{k}{'abc'[s]}"] = v


def _fins(p, amp=0.0, ph=0.0, up=0.0, side=0.0):
    """Its fin down both sides: amp ripples it (degrees), the ripple at phase ph running from its head to its tail; up
    raises all of it (+; - wraps it down against its sides); side raises its left fin (+) and lowers its right."""
    for nm, sg in SIDES_:
        for i in range(NFIN):
            a = up + sg * side + amp * math.sin(ph - FIN_LAG * i)
            p[f"fin_{nm}{i}"] = (0.0, 0.0, -sg * a)


def _tents(p, pitch=0.0, apart=0.0, club=0.0):
    """Its two tentacles: pitch lowers them (+) or lifts them (-) from where they lie in its arms, apart turns them out
    from each other, club bends their clubs down (+)."""
    for nm, sg in SIDES_:
        p[f"tent_{nm}"] = (pitch, -sg * apart, 0.0)
        p[f"club_{nm}"] = (club, 0.0, 0.0)


def _pose(lift=0.0, shift=(0.0, 0.0), pitch=0.0, yaw=0.0, roll=0.0, arch=0.0, head=(0, 0, 0), arms=None, fins=None, tents=None):
    """lift raises it (+, px) and shift moves it (x to its left, z back); pitch tips it (+ its arms down, its mantle's
    end up), yaw turns it (+ to its right) and roll rolls it (+ its left side down), all round its middle; arch bends
    the back of its mantle up (+); head turns its head on its mantle (nod + down, turn + to its right, tilt + its left eye
    down); arms, fins, tents: the arguments of _arms, _fins and _tents."""
    p = {"float": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])}, "mantle2": (arch, 0.0, 0.0), "head": tuple(head)}
    _arms(p, **(arms or {}))
    _fins(p, **(fins or {}))
    _tents(p, **(tents or {}))
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


def _floor(r, p, gap=0.3):
    """The pose let down (or lifted) until its body just rests on the floor (its arms and fin left to the ground pass)."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, BODY) - (core.GROUND - gap)
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


def _move_scale(r, mv):
    """A move's stretches, mv._scale {bone: [(stage, fraction, (sx, sy, sz))]}, written into its clip as scale tracks;
    its tentacles' stretch, mv._lance [(stage, fraction, length)], every tick, their clubs kept their own size."""
    clip = core.clip_name(mv)
    for bone, keys in getattr(mv, "_scale", {}).items():
        _scale(r, clip, bone, [(_staged(mv, s, f), v) for s, f, v in keys], getattr(mv, "_mode", "cat"))
    if getattr(mv, "_lance", None):
        pts = [(0.0, 1.0)] + [(_staged(mv, s, f), v) for s, f, v in mv._lance] + [(r.clips[clip].length, 1.0)]
        pts.sort()
        L = r.clips[clip].length
        n = int(round(L / core.TICK))
        tk, ck = [], []
        for i in range(n + 1):
            t = min(L, i * core.TICK)
            j = max(0, max(k for k in range(len(pts)) if pts[k][0] <= t + 1e-9))
            if j >= len(pts) - 1:
                v = pts[-1][1]
            else:
                (t0, v0), (t1, v1) = pts[j], pts[j + 1]
                v = v0 + (v1 - v0) * _ease((t - t0) / max(1e-6, t1 - t0))
            tk.append((t, 1.0, 1.0, v))
            ck.append((t, 1.0, 1.0, 1.0 / v))
        for nm, _ in SIDES_:
            r.clips[clip].scale(f"tent_{nm}", tk, "lin")
            r.clips[clip].scale(f"club_{nm}", ck, "lin")


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It hangs in the dark breathing (8 s), its fin rippling all down both sides and never still, its arms stirring
    and its crown slowly opening and closing; it turns side-on to look at you with one eye, then with the other; it
    lifts its top arms and lowers them; it flares its crown once; its mantle bobs and swells as it breathes."""
    L, n = 8.0, 160
    keys, sc = [], []
    for k in range(n + 1):
        u = k / n
        t = L * u
        w = 2 * math.pi * u
        breath = math.sin(2 * math.pi * 0.5 * t)
        look_l, look_r, lift_top, flare = _wave(u, 0.2, 0.26), _wave(u, 0.62, 0.26), _wave(u, 0.42, 0.14), _wave(u, 0.86, 0.14)
        keys.append((t, _pose(lift=1.2 * math.sin(2 * w) + 1.5 * flare, pitch=1.0 * math.sin(2 * w + 0.7) - 3 * lift_top - 2 * flare,
                              yaw=14 * look_l - 14 * look_r + 1.5 * math.sin(w), roll=-6 * look_l + 6 * look_r + math.sin(w + 1.0), arch=1.5 * breath,
                              head=(-1.5 * breath - 3 * lift_top, -6 * look_l + 6 * look_r, -3 * look_l + 3 * look_r),
                              arms=dict(spread=1.5 * breath + 16 * flare, curl=-2 * breath - 10 * flare, bend=-8 * flare, raise_=36 * lift_top, wave=5,
                                        ph=2 * math.pi * 2 * u, each={0: ((0, 0, 0), (-14 * lift_top, 0, 0), (-18 * lift_top, 0, 0)),
                                                                      1: ((0, 0, 0), (-14 * lift_top, 0, 0), (-18 * lift_top, 0, 0))}),
                              fins=dict(amp=14, ph=2 * math.pi * 12 * u, up=4 + 2 * breath + 6 * flare), tents=dict(pitch=1.5 * breath))))
        b = 1 + 0.035 * breath
        sc.append((t, (b, 1 + 0.03 * breath, 1.0)))
    keyed(r, "idle", L, keys, loop=True)
    r.clips["idle"].scale("mantle2", [(t, *v) for t, v in sc], "cat")


def _locomotion(r):
    """Always on top of the idle, faster as it goes: a quicker ripple down its fin and its arm tips trailing. It cruises
    on its fin with its arms drawn together into a point ahead of it; it runs in jets, squeezing water through its
    funnel, its fin wrapping down against its sides on each jet and its arms streaming."""
    L, n = 1.0, 20
    keyed(r, "fly", L, [(L * k / n, _pose(arms=dict(wave=2.5, ph=2 * math.pi * k / n), fins=dict(amp=7, ph=2 * math.pi * 2 * k / n))) for k in range(n + 1)],
          loop=True)
    L, n = 1.2, 24
    keys = []
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        keys.append((L * u, _pose(lift=0.8 * s, pitch=3 + 0.8 * math.sin(2 * math.pi * u + 1.0), roll=1.2 * math.sin(2 * math.pi * u + 0.4),
                                  arms=dict(spread=-3, bend=10, reach=0.25, wave=4, ph=2 * math.pi * u), fins=dict(amp=16, ph=2 * math.pi * 2 * u, up=-2),
                                  tents=dict(pitch=-2))))
    keyed(r, "walk", L, keys, loop=True)
    L, n = 0.8, 24
    keys, sc = [], []
    for k in range(n + 1):
        u = k / n
        j = _wave(u, 0.18, 0.36) + _wave(u, 1.18, 0.36) + _wave(u, -0.82, 0.36)  # the jet
        g = _wave(u, 0.62, 0.6)  # filling again
        keys.append((L * u, _pose(lift=-1.0 * j + 0.5 * g, shift=(0, -4.0 * j + 2.0 * g), pitch=5 - 2 * j, arch=2 * j - 1.5 * g,
                                  arms=dict(spread=-5 - 3 * j, bend=16 + 6 * j, reach=0.5 + 0.3 * j, wave=3, ph=2 * math.pi * u),
                                  fins=dict(amp=10 * g, ph=2 * math.pi * 2 * u, up=-26 * j + 10 * g), tents=dict(pitch=-4))))
        sc.append((L * u, (1 - 0.1 * j + 0.06 * g, 1 - 0.09 * j + 0.05 * g, 1 + 0.04 * j)))
    keyed(r, "run", L, keys, loop=True)
    r.clips["run"].scale("mantle2", [(t, *v) for t, v in sc], "cat")
    r.walk_stride, r.run_stride = 1.4, 2.6
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # turning to its left, its right fin works harder and its arms sweep left
        keys = []
        n = 20
        for k in range(n + 1):
            u = k / n
            ph = 2 * math.pi * 2 * u
            keys.append((1.0 * u, _pose(yaw=-6 * sg, roll=4 * sg, head=(0, -6 * sg, 0),
                                        arms=dict(bend=4, wave=3, ph=2 * math.pi * u, each={a: ((0, -sg * 8, 0), (0, -sg * 6, 0), (0, -sg * 6, 0)) for a in range(8)}),
                                        fins=dict(amp=10, ph=ph, side=-8 * sg))))
        keyed(r, nm, 1.0, keys, loop=True)


def _lying(r, slack=1.0):
    """Sunk to the floor, broken: its mantle on the floor, its arms spread limp round its head, its fin flat."""
    p = _pose(pitch=2 * slack, arch=-3 * slack, head=(5 * slack, 0, 0),
              arms=dict(spread=20 * slack, droop=6, curl=-14 * slack, bend=-6 * slack), fins=dict(up=-6 * slack), tents=dict(pitch=6))
    return _floor(r, p)


def _reactions(r):
    """Struck, it jolts back, its crown flaring and its fin flicking up; from the side, it rolls away from the blow.
    Broken, it sinks to the floor with its arms spread limp; it rises out of it on its fin, pushing off with its arms."""
    hit = _pose(lift=2, shift=(0, 4), pitch=-8, arch=6, head=(-6, 0, 0), arms=dict(spread=18, curl=-18, bend=-10), fins=dict(up=20), tents=dict(pitch=-4))
    back = _pose(shift=(0, 1), pitch=2, arch=-1, head=(2, 0, 0), arms=dict(spread=-3, curl=6), fins=dict(up=-4))
    keyed(r, "hurt", 0.6, [(0, REST), (0.08, hit), (0.32, back), (0.6, REST)])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left, it rolls away to its right, its left fin thrown up
        h = _pose(lift=1, shift=(-4 * sg, 2), yaw=8 * sg, roll=-14 * sg, head=(-3, 6 * sg, -6 * sg),
                  arms=dict(spread=10, curl=-10, each={a: ((0, sg * 10, 0), (0, sg * 8, 0), (0, sg * 8, 0)) for a in range(8)}), fins=dict(up=8, side=18 * sg))
        b = _pose(shift=(-1 * sg, 0.5), roll=3 * sg, head=(1, -2 * sg, 0), arms=dict(curl=4), fins=dict(side=-4 * sg))
        keyed(r, nm, 0.65, [(0, REST), (0.08, h), (0.34, b), (0.65, REST)])
    low = _lying(r)
    flinch = _pose(lift=3, pitch=-6, arch=5, head=(-6, 0, 0), arms=dict(spread=24, curl=-16, bend=-10), fins=dict(up=22))
    keyed(r, "kneel", 1.0, [(0, REST), (0.18, flinch), (0.55, _mix(flinch, low, 0.75)), (0.8, _mix(low, flinch, -0.05)), (1.0, low)])
    push = _floor(r, _pose(pitch=-2, arch=2, head=(-2, 0, 0), arms=dict(spread=14, droop=10, curl=-4, bend=2), fins=dict(up=18)), gap=3.0)
    up_ = _pose(lift=3, pitch=-4, arch=3, head=(-4, 0, 0), arms=dict(spread=6, droop=8, curl=6, bend=6), fins=dict(up=-14))
    keyed(r, "rise", 1.2, [(0, low), (0.3, push), (0.7, up_), (0.95, _mix(up_, REST, 0.7)), (1.2, REST)])
    _death(r)


def _death(r):
    """Its death: it convulses, its crown flung open and its fin flicking; it writhes as it sinks, turning a little,
    its arms coiling and uncoiling; it settles on the floor and its fin slows and stops, its arms curl in under its
    head one after another, and its mantle goes flat."""
    keys, sc = [], []
    conv = _pose(lift=4, shift=(0, 3), pitch=-12, arch=8, head=(-10, 0, 0), arms=dict(spread=40, curl=-30, bend=-24), fins=dict(up=30), tents=dict(pitch=-20, apart=10))
    keys += [(0, REST), (0.3, conv)]
    n = 10  # sinking, writhing
    for i in range(1, n + 1):
        u = i / n
        t = 0.3 + 2.2 * u
        q = math.sin(i * 2.3)
        p = _pose(lift=4 - 18 * _ease(u), shift=(1.5 * q, 3 - 2 * u), pitch=-12 + 14 * u, yaw=12 * u + 4 * q, roll=-10 * u + 3 * q, arch=8 - 10 * u,
                  head=(-10 + 14 * u, 4 * q, 3 * q), arms=dict(spread=40 - 24 * u, curl=-30 + 16 * u + 10 * q, bend=-24 + 18 * u, wave=14 * (1 - 0.5 * u), ph=i * 1.7),
                  fins=dict(amp=24 * (1 - u), ph=i * 2.2, up=30 - 34 * u), tents=dict(pitch=-20 + 26 * u, apart=10 - 6 * u))
        keys.append((t, p))
    lie = lambda curl, flat=0.0: _floor(r, _pose(pitch=2, yaw=12, roll=-10, arch=-4 - 2 * flat, head=(6, 0, 0),
                                                 arms=dict(spread=16 - 10 * curl, droop=6 + 10 * curl, curl=-12 + 46 * curl, bend=-4 + 22 * curl),
                                                 fins=dict(up=-8 - 4 * flat), tents=dict(pitch=8 + 14 * curl)))
    keys += [(2.9, lie(0.0)), (3.4, lie(0.15)), (3.9, lie(0.5)), (4.4, lie(0.85, 0.6)), (5.0, lie(1.0, 1.0))]
    keyed(r, "death", 5.0, keys)
    _scale(r, "death", "mantle2", [(2.9, ONE), (4.4, (1.04, 0.86, 1.0)), (4.99, (1.06, 0.8, 1.0)), (5.0, (1.06, 0.8, 1.0))], "lin")


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _lag(**kw):
    return dict(kw)


def _back(end):
    return [(0.45, _mix(end, REST, 0.45)), (0.7, _mix(end, REST, 0.82))]


TIPS = [f"arm{k}c" for k in range(8)]
TIP = (0.0, 0.0, -7.0)


def LOW_ARMS(a):
    """Its bottom pair of arms held up off the floor by a (degrees, most at the root)."""
    return {k: ((-a, 0, 0), (-0.6 * a, 0, 0), (-0.4 * a, 0, 0)) for k in (6, 7)}


def lash(r):
    """The Lash (LIGHT): it tips its head up and its crown of arms rises and flares open over it, the arm tips hooked
    down like the heads of snakes about to strike; then it whips the whole crown down across you, the arms closing as
    they come and their tips cracking down last."""
    cock = _pose(lift=2, shift=(0, 3), pitch=-12, arch=-4, head=(-8, 0, 0), arms=dict(spread=24, raise_=12, droop=-26, curl=18, bend=-8),
                 fins=dict(up=16), tents=dict(pitch=-6))
    sweep = _pose(lift=-0.5, shift=(0, -1), pitch=0, head=(1, 0, 0), arms=dict(spread=42, droop=4, curl=8, bend=-26, each=LOW_ARMS(14)), fins=dict(up=14))
    hit = _pose(lift=-1, shift=(0, -5), pitch=9, arch=4, head=(9, 0, 0), arms=dict(spread=6, droop=24, curl=28, bend=14, each=LOW_ARMS(16)), fins=dict(up=10))
    held = _pose(lift=-1.1, shift=(0, -5), pitch=9.5, arch=4, head=(9, 0, 0), arms=dict(spread=6, droop=25, curl=29, bend=15, each=LOW_ARMS(16)), fins=dict(up=10))
    return Move("lash", "LIGHT", 10, 5, 14, through=held, title="Lash", track=8.0, rng=(0, 4.5), kinds=("combo", "near"), damage=1.0, knock=0.7, plant=(),
                hit=[(b, TIP, 6.0) for b in TIPS], stages={"windup": [(0.2, _mix(REST, cock, 0.55)), (0.55, _mix(REST, cock, 0.88)), (1.0, cock)],
                                                           "active": [(0.35, sweep), (1.0, hit)],
                                                           "recover": [(0.4, _mix(held, REST, 0.36)), (0.72, _mix(held, REST, 0.82))]}, lag=_lag())


def grasp(r):
    """The Grasp (LIGHT, after the Lash): it draws back with its arms gathered and curled in under its face, then
    flings them out open at you and closes them on you like a fist."""
    gather = _pose(shift=(0, 5), pitch=-4, head=(-4, 0, 0), arms=dict(spread=-2, bend=16, curl=30, droop=8), fins=dict(up=14), tents=dict(pitch=4))
    fling = _pose(shift=(0, -8), pitch=4, head=(4, 0, 0), arms=dict(spread=44, reach=0.5, droop=-4, curl=10, bend=-4), fins=dict(up=12))
    close = _pose(shift=(0, -9), pitch=5, head=(5, 0, 0), arms=dict(spread=8, reach=0.7, curl=30, bend=28), fins=dict(up=10))
    held = _pose(shift=(0, -9), pitch=5, head=(5, 0, 0), arms=dict(spread=6, reach=0.7, curl=33, bend=31), fins=dict(up=10))
    return Move("grasp", "LIGHT", 10, 5, 14, through=held, title="Grasp", track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.1, knock=0.5, plant=(),
                hit=[(b, TIP, 5.0) for b in TIPS] + [("head", (0, 0, -12), 6.0)],
                stages={"windup": [(0.2, _mix(REST, gather, 0.6)), (0.6, _mix(REST, gather, 0.9)), (1.0, gather)],
                        "active": [(0.45, fling), (1.0, close)], "recover": _back(held)}, lag=_lag())


ADVANCE = 7.0  # blocks the Lance carries it
LANCE = 4.6  # its tentacles' stalks this many times their length at full stretch


def lance(r):
    """The Lance (HEAVY, its gap closer): it draws back with its fin wrapped down and its crown of arms parting wide,
    the two long tentacles showing pale inside them, and holds, its eyes on you; it jets at you and the tentacles shoot
    out like lances, their clubs snapping shut on you, and whip back into its arms."""
    draw = _pose(shift=(0, 5), pitch=2, arch=-2, head=(2, 0, 0), arms=dict(spread=18, curl=16, bend=4), fins=dict(up=-12), tents=dict(pitch=-8))
    aim = _pose(lift=-1, shift=(0, 7), pitch=3, arch=-3, head=(2, 0, 0), arms=dict(spread=36, curl=14, droop=-6), fins=dict(up=-18),
                tents=dict(pitch=-14, apart=1))
    shoot = _pose(shift=(0, -6), pitch=2, arms=dict(spread=40, curl=12, droop=-6), fins=dict(up=20), tents=dict(pitch=-15, apart=0, club=-10))
    grab = _pose(shift=(0, -8), pitch=3, arms=dict(spread=36, curl=16, droop=-4), fins=dict(up=14), tents=dict(pitch=-13, club=36))
    held = _pose(shift=(0, -8), pitch=3, arms=dict(spread=35, curl=17, droop=-4), fins=dict(up=12), tents=dict(pitch=-12, club=40))
    snap = _pose(shift=(0, -5), pitch=1, arms=dict(spread=10, curl=20, bend=10), fins=dict(up=6), tents=dict(pitch=4, club=10))
    mv = Move("lance", "HEAVY", 18, 10, 22, through=held, title="Lance", hold=6, track=6.0, lock=12, advance=ADVANCE, rng=(5, 14), kinds=("gap",), damage=1.3,
              knock=1.3, plant=(), hit=[("club_l", (0, 0, -3), 6.0), ("club_r", (0, 0, -3), 6.0), ("head", (0, 0, -12), 7.0)],
              stages={"windup": [(0.2, _mix(REST, draw, 0.7)), (0.6, draw), (1.0, aim)], "active": [(0.25, shoot), (0.6, grab), (1.0, held)],
                      "recover": [(0.3, snap), (0.7, _mix(snap, REST, 0.8))]}, lag=_lag())
    mv._lance = [("windup", 1.0, 1.0), ("active", 0.1, 2.6), ("active", 0.25, LANCE), ("active", 0.6, LANCE), ("active", 1.0, LANCE - 0.2),
                 ("recover", 0.12, 2.0), ("recover", 0.3, 1.0)]
    return mv


def recoil(r):
    """The Recoil (for whoever is behind it): it gathers its arms into a point ahead of it and its fin down, squeezes,
    and jets backwards hard, the blunt end of its mantle driving into whoever is there."""
    coil = _pose(shift=(0, -4), pitch=4, arch=-6, head=(4, 0, 0), arms=dict(bend=16, curl=18, spread=-6, reach=0.3), fins=dict(up=16))
    jet = _pose(shift=(0, 16), pitch=-3, arch=6, arms=dict(bend=22, reach=0.7, spread=-8, curl=10), fins=dict(up=-30))
    hit = _pose(shift=(0, 18), pitch=-4, arch=8, arms=dict(bend=24, reach=0.75, spread=-8, curl=12), fins=dict(up=-30))
    held = _pose(shift=(0, 18.5), pitch=-4, arch=8, arms=dict(bend=24, reach=0.75, spread=-8, curl=12), fins=dict(up=-28))
    mv = Move("recoil", "LIGHT", 10, 4, 14, through=held, title="Recoil", track=0.0, rng=(0, 6.0), kinds=("behind",), damage=1.0, knock=1.2, plant=(),
              hit=[("mantle2", (0, -2, 21), 7.0), ("mantle2", (0, 0, 14), 9.0)],
              stages={"windup": [(0.2, _mix(REST, coil, 0.6)), (0.6, _mix(REST, coil, 0.9)), (1.0, coil)], "active": [(0.4, jet), (0.75, hit), (1.0, held)],
                      "recover": _back(held)}, lag=_lag())
    mv._scale = {"mantle2": [("windup", 1.0, (1.06, 1.06, 0.97)), ("active", 0.3, (0.86, 0.88, 1.05)), ("recover", 0.4, ONE)]}
    return mv


CROWN = (0.0, 1.6, 2.7)  # blocks (x, up, forward): the point of its crown of arms when they are drawn together ahead of it


def instruction(r):
    """Instruction: it draws its arms together into a cone pointing at you, and holds you in its eyes while the beam
    runs from the point of it, its fin rippling fast and its body trembling with the effort of holding it."""
    aim = _pose(shift=(0, 2), pitch=2, head=(2, 0, 0), arms=dict(reach=0.85, bend=16, spread=-2, curl=-2), fins=dict(up=6), tents=dict(pitch=-10))
    act = []
    n = 15
    for i in range(1, n + 1):
        q = math.sin(i * 2.7)
        act.append((i / n, _pose(shift=(0.4 * q, 2 + 0.3 * q), pitch=2 + 0.8 * math.sin(i * 1.9), yaw=0.8 * q, head=(2, 0.6 * q, 0),
                                 arms=dict(reach=0.85, bend=16, spread=-2, curl=-2, wave=1.5, ph=i * 1.3), fins=dict(up=6, amp=10, ph=2 * math.pi * 0.3 * i),
                                 tents=dict(pitch=-10))))
    return Move("instruction", "NONE", 16, 30, 16, through=_mix(act[-1][1], REST, 0.2), title="Instruction", track=4.0, ability="Instruction", origin=CROWN,
                rng=(4, 20), kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, _mix(REST, aim, 0.5)), (1.0, aim)], "active": act, "recover": _back(_mix(act[-1][1], REST, 0.2))}, lag=_lag())


def step(r):
    """Signal Step: it squeezes, its arms drawn into a point and its fin wrapped down, and jets backwards away into the
    dark, gone; and it comes back out of the dark elsewhere, its crown opening."""
    sq = _pose(shift=(0, -2), pitch=3, head=(3, 0, 0), arms=dict(bend=18, reach=0.6, spread=-8, curl=4), fins=dict(up=-24))
    jet = _pose(lift=4, shift=(0, 30), pitch=-4, arms=dict(bend=22, reach=1.0, spread=-10), fins=dict(up=-30))
    gone = _pose(lift=4, shift=(0, 34), pitch=-4, arms=dict(bend=22, reach=1.0, spread=-10), fins=dict(up=-30))
    there = _pose(lift=-2, shift=(0, -6), arms=dict(spread=22, curl=-10, bend=-8), fins=dict(up=14))
    open_ = _pose(lift=-0.5, shift=(0, -2), arms=dict(spread=12, curl=-5, bend=-4), fins=dict(up=6))
    mv = Move("step", "NONE", 12, 14, 12, through=open_, title="Signal Step", track=0.0, ability="Signal Step", rng=(0, 99), kinds=("ranged",), damage=0,
              knock=0, plant=(), stages={"windup": [(0.3, _mix(REST, sq, 0.5)), (1.0, sq)], "active": [(0.35, jet), (0.5, gone), (0.55, there), (1.0, open_)],
                                         "recover": _back(open_)}, lag=_lag())
    mv._scale = {"float": [("windup", 1.0, ONE), ("active", 0.12, (0.85, 0.85, 0.85)), ("active", 0.4, (0.05, 0.05, 0.05)), ("active", 0.55, (0.05, 0.05, 0.05)),
                           ("active", 0.85, (1.05, 1.05, 1.05)), ("active", 1.0, ONE)]}
    mv._mode = "lin"  # a curve through its smallest would overshoot past nothing and turn it inside out
    return mv


TOP = (0.0, 2.3, 2.2)


def directive(r):
    """Directive: it lifts its top two arms high, their tips curled back like the heads of snakes, and flicks them
    down at you by turns, five times, a bolt flying from each."""
    raised = _pose(pitch=-4, head=(-4, 0, 0), arms=dict(raise_=50, curl=-8, spread=6, each={0: ((0, 0, 0), (-16, 0, 0), (-20, 0, 0)),
                                                                                             1: ((0, 0, 0), (-16, 0, 0), (-20, 0, 0))}), fins=dict(up=8))

    def flick(k):
        up = {0: ((0, 0, 0), (-16, 0, 0), (-20, 0, 0)), 1: ((0, 0, 0), (-16, 0, 0), (-20, 0, 0))}
        up[k] = ((54, 0, 0), (18, 0, 0), (14, 0, 0))
        return _pose(shift=(0, 1.5), pitch=-2, head=(-2, 0, 0), arms=dict(raise_=50, curl=-8, spread=6, each=up), fins=dict(up=4))
    act = []
    for i in range(5):
        f0 = i / 5
        act += [(f0 + 0.07, flick(i % 2)), (f0 + 0.17, raised)]
    act[-1] = (1.0, _mix(raised, REST, 0.15))
    return Move("directive", "NONE", 14, 24, 14, through=_mix(raised, REST, 0.3), title="Directive", track=8.0, ability="Directive", origin=TOP, rng=(4, 20),
                kinds=("ranged",), damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(REST, raised, 0.5)), (1.0, raised)], "active": act,
                                                                     "recover": _back(_mix(raised, REST, 0.3))}, lag=_lag())


def echoes(r):
    """Echoes (into the second phase): it rises with its crown flung open and its fin spread, and its skin pulses
    three times, its arms fluttering; its echoes come out of the dark."""
    wide = _pose(lift=3, pitch=-6, head=(-6, 0, 0), arms=dict(spread=34, curl=-16, bend=-14, droop=-8), fins=dict(up=18))
    act, sc = [], []
    n = 12
    for i in range(1, n + 1):
        u = i / n
        pulse = abs(math.sin(math.pi * 3 * u))
        act.append((u, _pose(lift=3 + 1.5 * pulse, pitch=-6, head=(-6 + 2 * pulse, 0, 0),
                             arms=dict(spread=34 + 6 * pulse, curl=-16 - 4 * pulse, bend=-14, droop=-8, wave=8, ph=i * 1.6), fins=dict(up=18 + 8 * pulse))))
        sc.append(("active", u, (1 + 0.06 * pulse, 1 + 0.05 * pulse, 1.0)))
    mv = Move("echoes", "NONE", 12, 26, 14, through=_mix(act[-1][1], REST, 0.2), title="Echoes", track=0.0, ability="Echoes", rng=(0, 99), kinds=("summon",),
              damage=0, knock=0, plant=(), stages={"windup": [(0.4, _mix(REST, wide, 0.5)), (1.0, wide)], "active": act,
                                                   "recover": _back(_mix(act[-1][1], REST, 0.2))}, lag=_lag())
    mv._scale = {"mantle2": [("windup", 1.0, ONE)] + sc + [("recover", 0.4, ONE)]}
    return mv


HIGH = 18.0  # px Signal Marks lifts it


def marks(r):
    """Signal Marks: it rises with its arms hanging under its face in a ring, like a chandelier, and pulses six times,
    each pulse a jolt up through it with its arms twitching out; it drifts down again."""
    high = _pose(lift=HIGH, head=(4, 0, 0), arms=dict(droop=62, spread=14, curl=-10), fins=dict(up=10), tents=dict(pitch=40))

    def pulse():
        return _pose(lift=HIGH + 2.5, pitch=-1, head=(2, 0, 0), arms=dict(droop=56, spread=24, curl=-18), fins=dict(up=18), tents=dict(pitch=36))
    act = []
    for i in range(6):
        f0 = i / 6
        act += [(f0 + 0.05, pulse()), (f0 + 0.14, high)]
    act[-1] = (1.0, high)
    down = _pose(lift=4, head=(2, 0, 0), arms=dict(droop=18, spread=6, curl=-4), fins=dict(up=4), tents=dict(pitch=10))
    return Move("marks", "NONE", 14, 24, 16, through=_mix(high, down, 0.3), title="Signal Marks", track=0.0, ability="Signal Marks", origin=(0.0, 2.6, 1.4),
                rng=(0, 22), kinds=("ranged", "area"), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _mix(REST, high, 0.5)), (1.0, high)], "active": act, "recover": [(0.45, down), (0.75, _mix(down, REST, 0.75))]},
                lag=_lag())


def decoy(r):
    """Your Echoes: it squeezes and jets aside to its left, its arms trailing, and hangs a moment; then jets back past
    where it was to its right; at each place it leaves, its ink hangs in its own shape."""
    coil = _pose(head=(2, 0, 0), arms=dict(bend=14, reach=0.4, curl=6), fins=dict(up=-14))

    def jet(sg):
        return _pose(shift=(14 * sg, 2), yaw=10 * sg, roll=6 * sg, arms=dict(bend=18, reach=0.6, each={a: ((0, 8 * sg, 0), (0, 6 * sg, 0), (0, 6 * sg, 0)) for a in range(8)}),
                     fins=dict(up=-26))

    def hang(sg, x):
        return _pose(shift=(x * sg, 1), yaw=3 * sg, roll=1 * sg, arms=dict(spread=10, curl=-4), fins=dict(up=10))
    act = [(0.22, jet(1)), (0.42, hang(1, 12)), (0.68, jet(-1)), (0.86, hang(-1, 11)), (1.0, hang(-1, 9))]
    mv = Move("decoy", "NONE", 14, 24, 14, through=hang(-1, 7), title="Your Echoes", track=0.0, ability="Your Echoes", rng=(0, 99), kinds=("summon",),
              damage=0, knock=0, plant=(), stages={"windup": [(0.4, _mix(REST, coil, 0.5)), (1.0, coil)], "active": act,
                                                   "recover": [(0.4, hang(-1, 4)), (0.75, hang(-1, 1))]}, lag=_lag())
    sq = (0.88, 0.9, 1.05)
    mv._scale = {"mantle2": [("windup", 1.0, (1.05, 1.05, 0.97)), ("active", 0.14, sq), ("active", 0.4, ONE), ("active", 0.5, (1.05, 1.05, 0.97)),
                             ("active", 0.6, sq), ("active", 0.86, ONE)]}
    return mv


def collapse(r):
    """Seed Collapse (the signature, into the last phase): it draws every arm in and curls them tight under its face,
    wraps its fin down against itself and squeezes smaller and smaller, trembling harder; then it bursts open, its arms
    flung out in a star, its fin flared and its tentacles thrown wide, and hangs there open as the Seed collapses."""
    def tight(f, q=0.0):
        return _pose(lift=-2 * f + 0.4 * q, pitch=4 * f, yaw=1.2 * q * f, roll=1.5 * q * f, arch=-6 * f, head=(8 * f, 2 * q * f, 3 * q * f),
                     arms=dict(spread=-10 * f, bend=30 * f, curl=44 * f, droop=10 * f), fins=dict(up=-40 * f + 4 * q * f), tents=dict(pitch=10 * f))
    w, sc = [], [("windup", 0.0, ONE)]
    n = 14
    for i in range(1, n + 1):
        u = i / n
        q = (-1) ** i * min(1.0, 0.2 + u)
        w.append((u, tight(_ease(min(1.0, u / 0.7)), q)))
        s = 1 - 0.16 * _ease(min(1.0, u / 0.7)) + 0.015 * q * u
        sc.append(("windup", u, (s, s, s)))
    burst = _pose(lift=4, pitch=-10, arch=8, head=(-12, 0, 0), arms=dict(spread=70, bend=-40, curl=-36, droop=-6), fins=dict(up=40), tents=dict(pitch=-30, apart=20))
    act = [(0.25, burst), (0.6, _mix(burst, REST, 0.05)), (1.0, _mix(burst, REST, 0.15))]
    sag = _pose(lift=-2, pitch=3, head=(4, 0, 0), arms=dict(spread=10, curl=10, droop=8), fins=dict(up=-6))
    mv = Move("collapse", "PERILOUS", 30, 12, 28, through=_mix(burst, sag, 0.4), title="Seed Collapse", signature=True, track=0.0, ability="Seed Collapse",
              rng=(0, 16), kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": w, "active": act, "recover": [(0.45, sag), (0.75, _mix(sag, REST, 0.75))]},
              lag=_lag())
    mv._scale = {"float": sc + [("active", 0.25, (1.1, 1.1, 1.1)), ("active", 0.6, (1.03, 1.03, 1.03)), ("recover", 0.4, ONE)]}
    return mv


def come_down(r):
    """Come Down: it reaches every arm out at you with its tentacles shooting out between them, and draws them in
    hand over hand, three times, backing away as it pulls."""
    out = _pose(shift=(0, -4), pitch=4, head=(2, 0, 0), arms=dict(reach=0.5, spread=36, droop=-8, curl=4, bend=-12), fins=dict(up=-12), tents=dict(pitch=-14, apart=6))
    pull = lambda x: _pose(shift=(0, x), pitch=-2, head=(-2, 0, 0), arms=dict(reach=0.3, spread=14, droop=10, curl=32, bend=24), fins=dict(up=14),
                           tents=dict(pitch=-6, club=40))
    act = [(0.14, pull(2)), (0.33, out), (0.47, pull(4)), (0.66, out), (0.8, pull(6)), (1.0, _mix(pull(6), out, 0.3))]
    end = _mix(pull(6), REST, 0.3)
    mv = Move("come_down", "NONE", 14, 24, 16, through=end, title="Come Down", track=4.0, ability="Come Down", rng=(3, 18), kinds=("ranged",), damage=0, knock=0,
              plant=(), stages={"windup": [(0.2, _mix(REST, out, 0.6)), (1.0, out)], "active": act, "recover": _back(end)}, lag=_lag())
    mv._lance = [("windup", 0.3, 1.0), ("windup", 1.0, 3.2), ("active", 0.14, 2.0), ("active", 0.33, 3.2), ("active", 0.47, 2.0), ("active", 0.66, 3.2),
                 ("active", 0.8, 1.8), ("active", 1.0, 1.6), ("recover", 0.3, 1.0)]
    return mv


def halo(r):
    """Signal Halo: it rises and draws its arms in tight, then flares them out all round its face in a star, its fin
    and its tentacles thrown wide, and holds the star, shuddering, as the halo goes out from it."""
    rise = _pose(lift=10, pitch=-8, head=(-6, 0, 0), arms=dict(spread=-6, bend=18, curl=20), fins=dict(up=-10))
    star = lambda q=0.0: _pose(lift=12, pitch=-12, yaw=q, roll=q, head=(-10, q, 0), arms=dict(spread=62, bend=-30, curl=-24, droop=-10, wave=3 * abs(q), ph=q),
                               fins=dict(up=30 + 3 * q), tents=dict(pitch=-20, apart=16))
    act = [(0.2, star()), (0.4, star(1.2)), (0.6, star(-1.2)), (0.8, star(1.0)), (1.0, _mix(star(), REST, 0.1))]
    end = _mix(star(), REST, 0.3)
    return Move("halo", "NONE", 14, 20, 16, through=end, title="Signal Halo", track=0.0, ability="Signal Halo", origin=(0.0, 2.3, 1.4), rng=(0, 12),
                kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": [(0.2, _mix(REST, rise, 0.6)), (1.0, rise)], "active": act, "recover": _back(end)},
                lag=_lag())


def last_instruction(r):
    """Last Instruction: it rears up, its arms thrown up over its head and spread, their tips curled back, its fin and
    its tentacles flared, a warning, and shudders all over."""
    rear = _pose(lift=6, pitch=-28, arch=-6, head=(-10, 0, 0), arms=dict(droop=-40, spread=26, curl=-30, raise_=24), fins=dict(up=24), tents=dict(pitch=-30, apart=12))
    act, sc = [], []
    n = 11
    for i in range(1, n + 1):
        q = (-1) ** i
        act.append((i / n, _pose(lift=6 + 0.6 * q, pitch=-28 + 1.2 * q, yaw=2 * q, roll=3 * q, arch=-6, head=(-10, q, 2 * q),
                                 arms=dict(droop=-40, spread=26 + 3 * q, curl=-30, raise_=24, wave=6, ph=i * 2.0), fins=dict(up=24 + 8 * q), tents=dict(pitch=-30, apart=12))))
        sc.append(("active", i / n, (1 + 0.03 * q, 1 + 0.03 * q, 1.0)))
    end = _mix(rear, REST, 0.35)
    mv = Move("last", "NONE", 10, 22, 12, through=end, title="Last Instruction", track=4.0, ability="Last Instruction", rng=(0, 99), kinds=("buff",), damage=0,
              knock=0, plant=(), stages={"windup": [(0.3, _mix(REST, rear, 0.4)), (1.0, rear)], "active": act, "recover": _back(end)}, lag=_lag())
    mv._scale = {"mantle2": [("windup", 1.0, ONE)] + sc + [("recover", 0.4, ONE)]}
    return mv


def arrive(r):
    """Its intro: it lies on the floor with its arms spread round it; it stirs, its arm tips curling; it lifts off the
    floor on its fin, drawing its arms in under it, rises to look you in the eye, and opens its crown of arms."""
    low = _lying(r)
    stir = _floor(r, _pose(pitch=1, arch=-2, head=(3, 0, 0), arms=dict(spread=16, droop=6, curl=-6, bend=-2), fins=dict(up=-2), tents=dict(pitch=6)))
    lift = _pose(lift=-12, pitch=-2, head=(-2, 0, 0), arms=dict(bend=10, curl=16, droop=12), fins=dict(up=-8))
    rising = _pose(lift=-3, pitch=-3, head=(-4, 0, 0), arms=dict(bend=12, curl=14, droop=4), fins=dict(up=10))
    look = _pose(lift=1, pitch=-2, head=(-4, 0, 0), arms=dict(bend=10, curl=10), fins=dict(up=4))
    open_ = _pose(lift=1.5, pitch=-4, head=(-6, 0, 0), arms=dict(spread=26, curl=-14, bend=-12), fins=dict(up=16), tents=dict(pitch=-6))
    mv = Move("arrive", "NONE", 14, 34, 14, through=_mix(open_, REST, 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.4, _mix(low, stir, 0.5)), (1.0, stir)],
                      "active": [(0.25, lift), (0.5, rising), (0.68, look), (0.85, open_), (1.0, _mix(open_, REST, 0.2))],
                      "recover": [(0.6, _mix(open_, REST, 0.7))]}, lag=_lag())
    mv._first = low  # it starts where the kneel left it, on the floor
    return mv


def moves(r):
    return [lash(r), grasp(r), lance(r), recoil(r), instruction(r), step(r), directive(r), echoes(r), marks(r), decoy(r), collapse(r), come_down(r),
            halo(r), last_instruction(r), arrive(r)]


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
    """Under a move, once it is made: its arm tips stir and its fin undulates at their own pace, faded in and out at the
    ends so it hands back to the idle exactly as it was (a living thing never holds a pose dead still)."""
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
    for k in range(8):
        for s_, seg in enumerate("bc"):
            add(f"arm{k}{seg}", lambda t, k=k, s_=s_: (4.0 * math.sin(2 * math.pi * 0.9 * t - 0.8 * k - 0.9 * s_),
                                                       3.0 * math.cos(2 * math.pi * 0.7 * t - 1.1 * k - 0.9 * s_), 0.0))
    for nm, sg in SIDES_:
        for i in range(NFIN):
            add(f"fin_{nm}{i}", lambda t, i=i, sg=sg: (0.0, 0.0, -sg * 7.0 * math.sin(2 * math.pi * 1.1 * t - FIN_LAG * i)))


# the ground (SPEC §35.10): its arms, fin and tentacles stay out of the floor where it lies
ARM_GROUND = [([f"arm{k}b", f"arm{k}c"], [(f"arm{k}a", 0, 40, -1), (f"arm{k}b", 0, 40, -1)]) for k in range(8)]
FIN_GROUND = [([f"fin_{nm}{i}"], [(f"fin_{nm}{i}", 2, 30, sg)]) for nm, sg in SIDES_ for i in range(NFIN)]
TENT_GROUND = [([f"tent_{nm}", f"club_{nm}"], [(f"tent_{nm}", 0, 40, -1)]) for nm, _ in SIDES_]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        _stage(r, m)
        _move_scale(r, m)
        _drift(r, core.clip_name(m))
    core.ground_pass(r, ARM_GROUND + FIN_GROUND + TENT_GROUND, clips=list(r.clips), skip=())
    return mv


# Its sounds (SPEC §35.8): a squid's squirt and a glow squid's voice kept low, the conduit's and the beacon's hum for the
# Signal, the sculk sensor's clicking like writing; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.glow_squid.ambient", 0.8, 0.6), ("block.sculk_sensor.clicking", 0.5, 1.2)],
    "tell_heavy": [("entity.glow_squid.ambient", 1.0, 0.5), ("block.conduit.ambient.short", 1.0, 0.7)],
    "tell_perilous": [("block.beacon.power_select", 1.2, 0.5), ("block.conduit.activate", 1.0, 0.6), ("entity.warden.heartbeat", 0.8, 0.7)],
    "swing_light": [("entity.squid.squirt", 0.7, 1.2), ("entity.player.attack.sweep", 0.6, 0.7)],
    "swing_heavy": [("entity.glow_squid.squirt", 1.1, 0.7), ("entity.breeze.wind_burst", 0.6, 0.8)],
    "cast": [("block.conduit.attack.target", 1.0, 0.8), ("block.amethyst_block.resonate", 0.8, 0.9)],
    "impact": [("entity.squid.hurt", 0.9, 0.6), ("entity.player.attack.crit", 0.8, 0.8)],
    "roar": [("block.beacon.deactivate", 1.4, 0.5), ("entity.glow_squid.death", 1.0, 0.5)],
    "jet": [("entity.glow_squid.squirt", 1.2, 0.8), ("entity.squid.squirt", 0.8, 1.0)],
    "blink": [("entity.illusioner.mirror_move", 1.0, 0.8), ("entity.glow_squid.squirt", 0.9, 0.9)],
    "step": [("entity.squid.ambient", 0.4, 0.6)],
}
MOVE_SOUNDS = {"recoil": {"swing": "jet"}, "lance": {"swing": "jet"}, "step": {"swing": "blink", "impact": ""}, "decoy": {"swing": "blink", "impact": ""},
               "instruction": {"swing": "cast", "impact": ""}, "directive": {"swing": "cast", "impact": ""}, "echoes": {"swing": "cast", "impact": ""},
               "marks": {"swing": "cast", "impact": ""}, "come_down": {"swing": "cast", "impact": ""}, "halo": {"swing": "cast", "impact": ""},
               "collapse": {"swing": "roar"}, "last": {"swing": "roar", "impact": ""}, "arrive": {"swing": "tell_light", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "arrive"},  # it lies on the floor of the Seed, and rises to look you in the eye
    "phases": [
        {"pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["grasp"], ["grasp", "lance"]]},
            {"move": "grasp", "weight": 2, "when": "near"},
            {"move": "lance", "weight": 3, "when": "far", "then": [["lash"]]},
            {"move": "instruction", "weight": 3, "when": "far"},
            {"move": "directive", "weight": 2, "when": "far"},
            {"move": "step", "weight": 1},
            {"move": "recoil", "weight": 3, "when": "behind"},
        ]},
        # it remembers every gate, as in DeepBestiary: its echoes, the marks, your echoes
        {"transition": "echoes", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["grasp"], ["grasp", "recoil"]]},
            {"move": "lance", "weight": 3, "when": "far", "then": [["lash", "grasp"]]},
            {"move": "marks", "weight": 2},
            {"move": "decoy", "weight": 1},
            {"move": "instruction", "weight": 2, "when": "far"},
            {"move": "step", "weight": 1},
            {"move": "recoil", "weight": 3, "when": "behind"},
        ]},
        # it is you, from the bottom: the Seed collapses
        {"transition": "collapse", "pool": [
            {"move": "come_down", "weight": 2, "when": "far", "then": [["lash"]]},
            {"move": "halo", "weight": 2},
            {"move": "lash", "weight": 3, "when": "near", "then": [["grasp", "halo"]]},
            {"move": "lance", "weight": 3, "when": "far", "then": [["grasp"]]},
            {"move": "last", "weight": 1},
            {"move": "collapse", "weight": 1},
            {"move": "recoil", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.4, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("float", (0, 0, 0)), ("head", (0, 0, -11)), ("arm0c", TIP), ("arm6c", TIP), ("tent_l", (0, 0, -10)), ("club_l", (0, 0, -3)),
                 ("mantle2", (0, 0, 21)), ("fin_l0", (8, 0, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
