"""The Drowned Moon (SPEC §35.11, docs/bosses/ROLLOUT.md): the Inverted Sea's Guardian, a Flier. The Moon Angler,
picked from its silhouette round 2026-10-29: "The sea above you has a moon in it."

The moon you see in the sea above is bait. It hangs on a long rod from the brow of an anglerfish as big as a house,
which swims in the dark behind it with its mouth open: a great black head that is nearly all mouth, the lower jaw
thrust out past the upper and lined with long glassy fangs that stand up outside its lips, two small pale eyes high on
its head, ragged gill covers that flare as it breathes, fan-shaped fins that scull either side, a short tail and a ragged
tail fan, and threads hanging from its chin. Its skin is black-blue and rough, lit by rows of pale cyan lights down its
flanks. The moon is pale and cratered and glows; kelp hangs from it, and barnacles crust it.

It hangs in the air as if in deep water, its fins sculling, its tail swaying, its jaw working slowly as it breathes and
the moon swaying on its rod ahead of it.

Moves (SPEC §35.11): it snaps at you, lunging with its jaws, and snaps again (LIGHT); the Engulf (HEAVY, a gap closer:
it draws back, its jaw unhinging wide, and surges at you mouth first and slams it shut); its tail fan swept round at
whoever is behind it; the Moonbeam (HEAVY: it raises the moon high on its rod and the beam comes down from it at you);
the Tide Turn (it sweeps its fins up and the water lifts you off the floor); the Undertow (the signature: it opens its
mouth wider than its body, its gill covers flared, and gulps the sea and you with it); and the Tide Halo (it curls round
its moon and the moon blazes, and a ring of water flies off it). It waits hanging dark in the deep with only the moon
lit, and swims up behind it.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_inverted"
SCALE = 1.6  # the hitbox on the def's ELDER_GUARDIAN model: 3.2 x 3.2 blocks, round its head; the body is at true size

# The Moon Angler (docs/bosses/ROLLOUT.md, A): its head 40 px long and 36 deep, its tail 28 more; the moon 14 across on a
# rod 40 long. Value story: the moon brightest (its glow, its rim of light), then the fangs and eyes, the lights down its
# flanks; the skin black-blue, its mouth's inside the darkest.
HOVER = 26.0  # its middle over the floor: its chin threads just clear of it
SIDES_ = (("l", 1), ("r", -1))
# each rod segment's turn (degrees, + dipping its far end) and length: up off the brow, over and down to the moon
LURE = ((-62.0, 10), (26.0, 10), (36.0, 10), (68.0, 9))
TAIL = ((16, 18, 9), (10, 13, 9), (6, 9, 8))  # each tail segment's width, height and length: flattened side to side
JAW_OPEN = 14.0  # the jaw hangs open at rest
# the fangs: (x, length, width) across the chin, and (x, length) hanging from the upper lip
FANGS_L = ((-12, 8, 2), (-9, 4, 1), (-6, 7, 2), (-2.5, 5, 1), (2.5, 5, 1), (6, 7, 2), (9, 4, 1), (12, 8, 2))
FANGS_U = ((-11, 6), (-7, 4), (-3, 7), (3, 7), (7, 4), (11, 6))


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, HOVER, 0)
    # the head and body in one: a dome stepping up to its crown, the cheeks swelling over the jaw's hinge; the front of
    # the head is the upper jaw, the mouth a cave under it
    _box(bd, "skull", 0, -2, 0, 30, 8, 34)
    _box(bd, "skull_hi", 0, 6, 1, 28, 8, 30)
    _box(bd, "brow", 0, 14, 2, 22, 4, 24)
    _box(bd, "crown", 0, 18, 3, 12, 2, 14)
    _box(bd, "underskull", 0, -6, 6, 28, 4, 22)
    _box(bd, "belly", 0, -9, 8, 22, 3, 16)
    for nm, sg in SIDES_:
        _box(bd, "cheek_" + nm, sg * 15.5, -4, -2, 3, 14, 24)
    # the upper lip round the mouth's top
    _box(bd, "lip", 0, -2, -17.5, 28, 3, 3)
    for nm, sg in SIDES_:
        _box(bd, "lip_" + nm, sg * 14, -2, -11, 2, 3, 10)
    fu = _bone(bd, "fangs_u", 0, -2, -18)
    for k, (x, L) in enumerate(FANGS_U):
        _box(fu, f"fang_u{k}", x, -L, 0, 1 + (L >= 6), L, 1)
    # the gullet: the dark inside of the mouth, seen past the fangs
    _box(bd, "gullet", 0, -10, -10, 24, 8, 10)
    # its eyes, small and pale, high on its head
    for nm, sg in SIDES_:
        _box(bd, "eye_" + nm, sg * 14.5, 8, -9, 3, 4, 4)
    # the lower jaw, hanging open: thrust out past the upper and up, a scoop lined with long glassy fangs standing up
    # outside the lip
    jw = _bone(bd, "jaw", 0, -4, 6, (JAW_OPEN, 0, 0))
    _box(jw, "jaw", 0, -8, -10, 28, 3, 28)
    _box(jw, "chin", 0, -8, -24.5, 28, 7, 3)
    _box(jw, "chin_lo", 0, -10, -16, 24, 2, 18)
    for nm, sg in SIDES_:
        _box(jw, "jawside_" + nm, sg * 14.5, -6, -10, 3, 5, 26)
        for k, (z, L) in enumerate(((-19, 5), (-14, 4), (-9, 3))):
            _box(jw, f"fang_s{nm}{k}", sg * 14.5, -1, z, 1, L, 1)
    fl = _bone(jw, "fangs_l", 0, -1, -24.5, (18, 0, 0))
    for k, (x, L, w) in enumerate(FANGS_L):
        _box(fl, f"fang_l{k}", x, 0, 0, w, L, 1)
    # the threads hanging from its chin
    for nm, sg in SIDES_:
        parts.chain(jw, "barbel_" + nm, (sg * 8, 10, -21), 2, link=(1, 3, 1))
    # the gill covers: ragged flaps behind its cheeks that flare
    for nm, sg in SIDES_:
        g = _bone(bd, "gill_" + nm, sg * 17, 2, 9)
        _box(g, "gill_" + nm, sg * 0.5, -8, 4, 1, 15, 8)
        _box(g, "gillfrill_" + nm, sg * 0.5, -6, 9, 1, 11, 2)
    # the fins: fans of rays, splayed out behind the gills, that scull
    for nm, sg in SIDES_:
        f = _bone(bd, "fin_" + nm, sg * 15, -3, 14, (0, sg * 40, 0))
        _box(f, "finroot_" + nm, 0, -2, 1.5, 2, 4, 3)
        for k, (h, z) in enumerate(((6, 3.5), (9, 6.5), (11, 9.5), (8, 12))):
            _box(f, f"fin{k}_" + nm, 0, -h / 2, z, 1, h, 3 if k < 3 else 2)
    # a low ridge of soft spines down its back
    dor = _bone(bd, "dorsal", 0, 18, 15, (14, 0, 0))
    for k, z in enumerate((0, 3, 6)):
        _box(dor, f"spine{k}", 0, 0, z, 1, 6 - k, 2)
    # the tail, flattened side to side, its small fins and its ragged fan
    parent, piv = bd, (0, -4, 17)
    for k, (w, h, d) in enumerate(TAIL):
        tb = parent.bone(f"tail{k}", piv)
        tb.cube(f"tail{k}", -w / 2, -h / 2, 0, w, h, d)
        if k == 1:
            tb.cube("tailfin_u", -0.5, -h / 2 - 4, 2, 1, 4, 5)
            tb.cube("tailfin_d", -0.5, h / 2, 2, 1, 4, 5)
        parent, piv = tb, (0, 0, d)
    fan = parent.bone("fan", piv)
    for k, (h, z) in enumerate(((12, 0), (18, 2.5), (22, 5), (20, 7.5))):
        fan.cube(f"fan{k}", -0.5, -h / 2, z, 1, h, 3)
    # the rod from its brow, arching forward over its mouth, and the moon hanging from its tip
    parent, piv = bd, (0, -18, -9)
    for k, (a, L) in enumerate(LURE):
        lb = parent.bone(f"lure{k}", piv, (a, 0, 0))
        lb.cube(f"lure{k}", -1, -1, -L, 2, 2, L)
        parent, piv = lb, (0, 0, -L)
    mo = parent.bone("moon", piv, (-sum(a for a, _ in LURE), 0, 0))
    _box(mo, "moonstem", 0, -3, 0, 2, 3, 2)
    _box(mo, "moon", 0, -17, 0, 16, 12, 12)
    _box(mo, "moonv", 0, -19, 0, 12, 16, 12)
    _box(mo, "moonz", 0, -17, 0, 12, 12, 16)
    # kelp hanging from the moon
    for nm, x in (("l", 5.0), ("r", -4.0)):
        parts.chain(mo, "kelp_" + nm, (x, 19, 1), 2, link=(2, 4, 1))
    r.look = [("body", 0.15, 0.12)]
    r.shadow = 2.2
    r.size = 1.0
    # secondary parts (SPEC §35.10): the rod and the moon swing on it, the kelp, the threads, the gill frills and the fan
    r.springs = ([(f"lure{k}", 0.2 + 0.06 * k, 1.4 - 0.1 * k, 0.2) for k in range(1, 4)] + [("moon", 0.4, 1.1, 0.16)]
                 + [(f"kelp_{nm}{k}", 0.5, 1.6 + 0.2 * k, 0.12) for nm, _ in SIDES_ for k in range(2)]
                 + [(f"barbel_{nm}{k}", 0.5, 1.8 + 0.2 * k, 0.12) for nm, _ in SIDES_ for k in range(2)]
                 + [("fan", 0.3, 2.2, 0.2), ("dorsal", 0.3, 2.4, 0.2)])
    r.guard = []
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#04060b", "#070a12", "#0b0f19", "#101621", "#161d2a", "#1d2534", "#252e40", "#2f394d", "#3a455b")
PALE = Ramp("#1a2230", "#232d3e", "#2e394c", "#3a465b", "#47546a", "#56637a")
FANG = Ramp("#6f7f92", "#8a9bb0", "#a8b8cc", "#c6d4e4", "#e0eaf4", "#f4f8fc")
MAW = Ramp("#050205", "#0c0409", "#16070f", "#220b16", "#30101e")
FIN = Ramp("#0a0f18", "#111926", "#1a2436", "#243148", "#30405a")
MOON = ("#ffffff", "#f2f7ff", "#dfeaff", "#c3d6f4", "#a2bce4")
CRATER = "#9fb3d6"
LIGHT = ("#e8fbff", "#a8f0ff", "#5ad8f4")
KELP = Ramp("#0e1a10", "#162a18", "#1f3a20", "#2a4c28", "#365e32")


def paint(r, P_):
    """Rough black-blue skin, mottled, a shade paler on the jaw and grey under the belly; rows of pale cyan lights down its flanks; glassy
    fangs; the inside of its mouth near black with a red-black throat; translucent dark fins with paler rays; the moon
    pale and glowing with blue-grey craters, kelp hanging off it."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    rough = R.p_blotch(7, scale=5, amount=0.7, levels=3)
    for c in ("skull", "skull_hi", "brow", "crown", "underskull") + tuple(of("cheek_", "lip", "tail")):
        P_.paint(c, SKIN, 4.2, rough, grad=1.6, rim=0.8, ao=1.0, noise=0.7, nscale=4)
    P_.paint("belly", PALE, 2.8, rough, grad=1.2, rim=0.7, ao=0.9, noise=0.6, nscale=4)
    for c in ("jaw", "chin", "chin_lo") + tuple(of("jawside_")):  # the jaw a shade paler than the head, greying underneath
        P_.paint(c, SKIN, 5.0, rough, grad=1.8, rim=0.9, ao=0.9, noise=0.7, nscale=4)
    for c in of("fang_"):
        P_.paint(c, FANG, 4.0, lambda f, i, j: (1.2 if j == 0 else 0), grad=1.4, rim=1.0, ao=0.2)
        for fname in ("north", "south", "west", "east"):  # glassy: they catch the moon's light, brightest at the tip
            g = P_.face(c, fname, glow=True)
            tip = 0 if c.startswith(("fang_l", "fang_s")) else g.h - 1
            for i, j in g.each():
                g.set(i, j, hexc("#d8ecff", 200 if j == tip else 70))
    P_.paint("gullet", MAW, 2.0, None, grad=0.6, rim=0.2, ao=1.0, noise=0.4)
    radial(P_, "gullet", "north", ["#3a0a14", "#1c050b", "#0c0306"], alpha=255)
    f = P_.face("jaw", "top")  # the floor of its mouth
    for i, j in f.each():
        f.set(i, j, MAW.at(1 + R._h("mf", i, j) % 3))
    for c in of("gill", "fin", "tailfin", "dorsal", "spine", "fan"):
        P_.paint(c, FIN, 3.0, R.p_bands(every=2, depth=0.8, vertical=True), grad=1.0, rim=1.0, ao=0.4, noise=0.4)
    for c in of("barbel_"):
        P_.paint(c, PALE, 3.4, None, rim=0.6)
    for c in of("lure"):
        P_.paint(c, SKIN, 3.6, lambda f, i, j: (1.0 if (i + j) % 4 == 0 else 0), rim=0.9, ao=0.4)
    # its eyes: small, pale and lit
    for nm, sg in SIDES_:
        c = "eye_" + nm
        P_.paint(c, Ramp("#8ab0d0", "#d8ecff"), 1.4)
        for fname in ("west" if sg > 0 else "east", "north"):
            radial(P_, c, fname, ["#ffffff", "#cfe8ff", "#7aa8d8"], alpha=230)
    # the lights down its flanks: two rows of pale cyan dots, and a few on its jaw
    for c, rows in (("skull", (6, 11)), ("jawside_l", (2,)), ("jawside_r", (2,))):
        for fname in ("west", "east"):
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                if j in rows and i % 4 == (j // 3) % 4 and 1 < i < f.w - 2:
                    f.set(i, j, hexc(LIGHT[1]))
                    g.set(i, j, hexc(LIGHT[0], 210))
    # the moon: pale, lit, with craters and a rim of light; barnacles round its stem
    for c in ("moon", "moonv", "moonz"):
        P_.paint(c, Ramp(*MOON[::-1]), 4.0, None, grad=0.4, rim=0.3, ao=0.0)
        for fname in ("north", "south", "west", "east", "top", "bottom"):
            try:
                f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            except (KeyError, ValueError):
                continue
            for i, j in f.each():
                h = R._h(c, fname, i, j)
                cr = R._h("crater", c, fname, i // 3, j // 3) % 7 == 0 and (i + j) % 3 != 0
                col = CRATER if cr else MOON[1 + h % 3]
                f.set(i, j, hexc(col))
                g.set(i, j, hexc(MOON[0] if not cr else "#cfdcf4", 200 if not cr else 120))
    P_.paint("moonstem", Ramp("#3a4a3c", "#6a7a6a"), 1.4, lambda f, i, j: (1.0 if (i + j) % 2 == 0 else -0.6))
    for c in of("kelp_"):
        P_.paint(c, KELP, 3.0, R.p_bands(every=2, depth=0.8), grad=0.8, rim=0.6, ao=0.3, noise=0.5)
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion (SPEC §35.4): one pose function over its parts; the clips are keys of it

SHUT = -JAW_OPEN  # the jaw closed


def _tail(p, sway=0.0, lift=0.0, curl=0.0, wave=0.0, ph=0.0):
    """The tail: sway swings it to its left at the root, lift raises it, curl bends it up along its length, and wave
    sends an S down it side to side (its amplitude, at phase ph), the fan last."""
    for k in range(len(TAIL)):
        p[f"tail{k}"] = ((lift if k == 0 else 0.0) + curl * (k + 1) / 6, sway * (0.5 if k == 0 else 0.25) + wave * math.sin(ph - 0.8 * k), 0.0)
    p["fan"] = (curl * 0.2, sway * 0.2 + 1.3 * wave * math.sin(ph - 2.6), 0.0)


def _fin(p, nm, sg, spread=0.0, lift=0.0, scull=0.0, ph=0.0):
    """One fin: spread swings it forward and out (+) or back flat against it (-), lift raises its trailing edge, and
    scull beats it round a figure of eight (its amplitude, at phase ph)."""
    p["fin_" + nm] = (lift + 0.6 * scull * math.cos(ph), sg * (spread + scull * math.sin(ph)), sg * 0.3 * scull * math.sin(ph))


def _rod(p, raise_=0.0, droop=0.0, sway=0.0, swing=0.0):
    """The rod: raise straightens it up over its head (+), droop lets it sag forward and down; sway swings it to its
    left; the moon hangs plumb below its tip whatever the rod does, and swing rocks it on its stem."""
    up = (0.35, 0.2, 0.2, 0.25)
    dn = (0.3, 0.25, 0.25, 0.2)
    tot = 0.0
    for k in range(len(LURE)):
        x = -raise_ * up[k] + droop * dn[k]
        tot += x
        p[f"lure{k}"] = (x, sway * (0.6 if k == 0 else 0.25), 0.0)
    p["moon"] = (-tot + swing, -sway * 0.8, 0.0)


def _pose(pitch=0.0, yaw=0.0, roll=0.0, at=(0.0, 0.0, 0.0), jaw=0.0, gills=0.0, fins=None, fl=None, fr=None, tail=None, rod=None, dorsal=0.0):
    """The body: pitch (+ nose down), yaw (+ turning to its right), roll (+ its left side down), at (x to its left, up,
    z back); the jaw opens (+) from its resting gape (SHUT closes it); the gill covers flare (+); fins: _fin arguments
    for both, or fl and fr each its own; tail: _tail arguments; rod: _rod arguments; the dorsal spines rise (-)."""
    p = {"body": {"rot": (pitch, yaw, roll), "pos": tuple(at)}, "jaw": (jaw, 0, 0), "dorsal": (dorsal, 0, 0),
         "gill_l": (0, gills, 0), "gill_r": (0, -gills, 0)}
    _fin(p, "l", 1, **(fl if fl is not None else fins or {}))
    _fin(p, "r", -1, **(fr if fr is not None else fins or {}))
    _tail(p, **(tail or {}))
    _rod(p, **(rod or {}))
    return p


def _moon(r, clip, pts):
    """The moon's swell: [(seconds, scale)]."""
    r.clips[clip].scale("moon", [(t, v, v, v) for t, v in pts], "cat")


def _idle(r):
    """Hanging in the deep: its fins scull, its tail sweeps slow strokes and its body bobs and yaws against them; it
    breathes, the jaw working open and shut and the gill covers flaring after it; the rod sways and the moon on it
    swells and settles."""
    L, n = 6.0, 48
    keys = []
    for k in range(n + 1):
        u = k / n
        tph, fph, bph = 2 * math.pi * 3 * u, 2 * math.pi * 4 * u, 2 * math.pi * 2 * u
        keys.append((L * u, _pose(pitch=1.2 * math.sin(2 * math.pi * u) + 0.6 * math.sin(bph - 0.4), yaw=-1.6 * math.sin(tph - 0.5),
                                  roll=0.8 * math.sin(2 * math.pi * u + 1.0), at=(0.3 * math.sin(tph), 0.9 * math.sin(fph - 1.0), 0),
                                  jaw=1.5 + 3.5 * math.sin(bph), gills=6 + 8 * math.sin(bph - 2.2),
                                  fins=dict(scull=11, ph=fph), tail=dict(wave=7, ph=tph, curl=-2), dorsal=-3 * math.sin(tph - 1.0),
                                  rod=dict(sway=4 * math.sin(2 * math.pi * u + 0.5), raise_=3 * math.sin(bph - 1.0), swing=2 * math.sin(tph - 2.0)))))
    keyed(r, "idle", L, keys, loop=True)
    _moon(r, "idle", [(L * k / 8, 1 + 0.03 * math.sin(2 * math.pi * 2 * k / 8)) for k in range(9)])


def _flight(r):
    """On top of the idle while it swims: it lowers its nose, lays its fins back and drives its tail harder, the rod
    trailing; it banks into its turns, its tail swinging out."""
    L, n = 1.5, 12
    keys = []
    for k in range(n + 1):
        u = k / n
        ph = 2 * math.pi * u
        keys.append((L * u, _pose(pitch=4, yaw=-1.5 * math.sin(ph - 0.6), fins=dict(spread=-14, lift=-4), tail=dict(wave=8, ph=ph), rod=dict(droop=5))))
    keyed(r, "fly", L, keys, loop=True)
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # into a turn to its left, its left side dips and its tail swings out right
        keys = []
        for k in range(n + 1):
            u = k / n
            ph = 2 * math.pi * u
            keys.append((1.0 * u, _pose(roll=10 * sg, yaw=-5 * sg, at=(sg * 1.5, 0, 0), fl=dict(spread=-10 * sg, scull=6, ph=ph), fr=dict(spread=10 * sg, scull=6, ph=ph),
                                        tail=dict(sway=-16 * sg, wave=4, ph=ph), rod=dict(sway=-6 * sg))))
        keyed(r, nm, 1.0, keys, loop=True)


def _sag(low=1.0):
    """Its stance broken: it sinks nose down with its jaw hanging open, fins and tail limp, the rod drooping."""
    return _pose(pitch=12 * low, roll=6 * low, at=(0, -8 * low, 0), jaw=16 * low, gills=2, fins=dict(spread=-16 * low, lift=-20 * low),
                 tail=dict(lift=-12 * low, curl=-8 * low), rod=dict(droop=30 * low), dorsal=10 * low)


def _reactions(r):
    """Struck, it jolts back and its jaw snaps shut, the fins and gill covers flaring and the tail whipping; from the
    side, it rolls away. Broken, it sinks; it rises out of it on one great stroke of its tail."""
    hit = _pose(pitch=-8, at=(0, 2, 3), jaw=SHUT, gills=18, fins=dict(spread=20, lift=16), tail=dict(lift=10, curl=14), rod=dict(raise_=-10, swing=-8), dorsal=-10)
    back = _pose(pitch=3, at=(0, -1, 0), jaw=4, gills=4, fins=dict(spread=-4, lift=-4), tail=dict(lift=-4), rod=dict(raise_=3))
    keyed(r, "hurt", 0.6, [(0, _pose()), (0.08, hit), (0.32, back), (0.6, _pose())])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it rolls away, its tail thrown round
        h = _pose(roll=-14 * sg, yaw=-8 * sg, pitch=-4, at=(-3 * sg, 1.5, 1), jaw=SHUT + 4, gills=14,
                  fl=dict(spread=22 if sg > 0 else -6, lift=18 if sg > 0 else -6), fr=dict(spread=22 if sg < 0 else -6, lift=18 if sg < 0 else -6),
                  tail=dict(sway=18 * sg, curl=6), rod=dict(sway=14 * sg, swing=-6), dorsal=-6)
        b = _pose(roll=4 * sg, yaw=2 * sg, at=(sg * 0.5, -0.5, 0), jaw=3, fins=dict(spread=-3), tail=dict(sway=-6 * sg), rod=dict(sway=-4 * sg))
        keyed(r, nm, 0.65, [(0, _pose()), (0.08, h), (0.34, b), (0.65, _pose())])
    keyed(r, "kneel", 0.9, [(0, _pose()), (0.25, _pose(pitch=-5, at=(0, 2, 1), jaw=10, gills=12, fins=dict(spread=12, lift=10), rod=dict(raise_=6))),
                            (0.65, _sag(1.08)), (0.9, _sag())])
    _moon(r, "kneel", [(0, 1.0), (0.25, 1.06), (0.9, 0.88)])
    up_ = _pose(pitch=-6, at=(0, 3, 1), jaw=-4, gills=10, fins=dict(spread=26, lift=24), tail=dict(sway=24, curl=10), rod=dict(raise_=10), dorsal=-8)
    down = _pose(pitch=3, at=(0, 2, 0), jaw=2, gills=4, fins=dict(spread=-12, lift=-12), tail=dict(sway=-18), rod=dict(raise_=2))
    keyed(r, "rise", 1.2, [(0, _sag()), (0.45, up_), (0.8, down), (1.2, _pose())])
    _moon(r, "rise", [(0, 0.88), (0.45, 1.08), (1.2, 1.0)])
    _death(r)


def _death(r):
    """Its fall: it convulses, its mouth stretched open and its fins flung out, the moon flaring; it gasps once more,
    then sinks nose first onto the floor and lists over, its jaw falling open on the stones and its rod bowing down
    until the moon lies on the floor before it, going dim."""
    conv = _pose(pitch=-14, at=(0, 3, 2), jaw=30, gills=30, fins=dict(spread=30, lift=22), tail=dict(curl=26, sway=16), rod=dict(raise_=16, swing=-10), dorsal=-16)
    weak = _pose(pitch=6, roll=-6, at=(0, -2, 0), jaw=10, gills=8, fins=dict(spread=-8, lift=-10), tail=dict(sway=-10), rod=dict(droop=10))
    gasp = _pose(pitch=-4, roll=4, at=(0, 0, 0), jaw=24, gills=24, fins=dict(spread=12, lift=10), tail=dict(sway=18, curl=6), rod=dict(raise_=6), dorsal=-8)
    sink = _pose(pitch=10, roll=-10, at=(0, -7, 0), jaw=14, gills=4, fins=dict(spread=-14, lift=-18), tail=dict(lift=-10, sway=-6), rod=dict(droop=32), dorsal=6)
    down = lambda d: _pose(pitch=7, roll=-17 - d, at=(0, -10.5, 0), jaw=19 + d, gills=2, fins=dict(spread=-18, lift=-24 - d),
                           tail=dict(lift=-12, curl=-6, sway=4), rod=dict(droop=58 + d), dorsal=10)
    keys = [(0, _pose()), (0.35, conv), (0.9, weak), (1.4, gasp), (2.4, sink), (3.4, down(0)), (4.2, down(2)), (5.0, down(2.5))]
    keyed(r, "death", 5.0, keys)
    _moon(r, "death", [(0, 1.0), (0.35, 1.18), (0.9, 0.98), (1.4, 1.08), (2.4, 0.95), (3.4, 0.85), (5.0, 0.72)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _lag(**kw):
    out = {"jaw": 0.05, "lure1": 0.05, "lure2": 0.08, "lure3": 0.1, "fan": 0.08, "tail2": 0.06}
    out.update(kw)
    return out


MOUTH = [("jaw", (0, 0, -26), 10.0), ("body", (0, 0, -18), 12.0)]


def bite(side="l", mid="bite"):
    """It draws back and up, turning its head aside, its jaw gaping and its gill covers flaring; then it lunges and
    snaps its jaw shut on you, the head tossing across (LIGHT)."""
    sg = 1 if side == "l" else -1
    gather = _pose(yaw=-8 * sg, roll=-7 * sg, pitch=-6, at=(sg * 2, 1.5, 4), jaw=16, gills=14, fins=dict(spread=16, lift=12), tail=dict(sway=-16 * sg, curl=4),
                   rod=dict(raise_=10, swing=-5, sway=4 * sg), dorsal=-6)
    peak = _pose(yaw=-15 * sg, roll=-12 * sg, pitch=-10, at=(sg * 2.5, 2, 6), jaw=30, gills=24, fins=dict(spread=26, lift=20), tail=dict(sway=-32 * sg, curl=10),
                 rod=dict(raise_=18, swing=-10, sway=8 * sg), dorsal=-14)
    # through the snap its gill covers stay flared, its spines up and its rod lifted, the moon swinging on ahead
    hit = _pose(yaw=8 * sg, roll=8 * sg, pitch=8, at=(-sg * 1.5, -2, -10), jaw=SHUT, gills=12, fins=dict(spread=-14, lift=-10), tail=dict(sway=18 * sg),
                rod=dict(raise_=10, swing=14), dorsal=-10)
    held = _pose(yaw=9 * sg, roll=9 * sg, pitch=9, at=(-sg * 2, -2.5, -11), jaw=SHUT, gills=12, fins=dict(spread=-16, lift=-12), tail=dict(sway=20 * sg),
                 rod=dict(raise_=10, swing=16), dorsal=-10)
    sink = _pose(yaw=7 * sg, roll=7 * sg, pitch=7, at=(-sg * 1.5, -2, -9.5), jaw=SHUT + 3, gills=11, fins=dict(spread=-12, lift=-9), tail=dict(sway=16 * sg),
                 rod=dict(raise_=9, swing=13), dorsal=-9)
    back = _pose(yaw=5 * sg, roll=4 * sg, pitch=4, at=(-sg, -1, -5), jaw=6, gills=8, fins=dict(spread=-6, lift=-4), tail=dict(sway=10 * sg),
                 rod=dict(raise_=6, swing=6), dorsal=-6)
    return Move(mid, "LIGHT", 10, 4, 14, through=sink, track=8.0, rng=(0, 5.5), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(), hit=MOUTH,
                stages={"windup": [(0.3, gather), (0.65, core._scaled(peak, 0.9)), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]},
                lag=_lag())


def engulf(r):
    """The Engulf (HEAVY, a gap closer): it draws back, its fins braking forward and its tail coiling, the jaw
    unhinging wider than its head; then it surges at you mouth first on two great strokes and slams it shut."""
    draw = _pose(pitch=-8, at=(0, 2, 6), jaw=18, gills=14, fins=dict(spread=24, lift=10), tail=dict(sway=14, curl=-6), rod=dict(raise_=8))
    coil = _pose(pitch=-12, at=(0, 3, 10), jaw=34, gills=24, fins=dict(spread=30, lift=16), tail=dict(sway=30, curl=14), rod=dict(raise_=12), dorsal=-10)
    peak = _pose(pitch=-14, at=(0, 3, 11), jaw=40, gills=28, fins=dict(spread=32, lift=18), tail=dict(sway=34, curl=16), rod=dict(raise_=14), dorsal=-14)
    # it surges with its gill covers flared, its spines up and its rod streaming back, the moon swinging after it
    act = [(0.3, _pose(pitch=10, at=(0, -2, -10), jaw=44, gills=24, fins=dict(spread=-20, lift=-8), tail=dict(sway=-6, curl=6), rod=dict(raise_=12, swing=-12),
                       dorsal=-14)),
           (0.7, _pose(pitch=12, at=(0, -3, -16), jaw=46, gills=22, fins=dict(spread=-24, lift=-10), tail=dict(sway=24, curl=8), rod=dict(raise_=12, swing=-10),
                       dorsal=-14)),
           (1.0, _pose(pitch=14, at=(0, -4, -18), jaw=SHUT, gills=16, fins=dict(spread=-12, lift=-6), tail=dict(sway=8, curl=6), rod=dict(raise_=10, swing=14),
                       dorsal=-12))]
    sink = _pose(pitch=12, at=(0, -3.5, -17), jaw=SHUT + 2, gills=14, fins=dict(spread=-10, lift=-5), tail=dict(sway=6, curl=5), rod=dict(raise_=9, swing=12),
                 dorsal=-11)
    back = _pose(pitch=7, at=(0, -2, -10), jaw=8, gills=10, fins=dict(spread=8, lift=4), tail=dict(sway=8, curl=4), rod=dict(raise_=6, swing=6), dorsal=-6)
    return Move("engulf", "HEAVY", 16, 12, 20, through=sink, title="Engulf", track=6.0, lock=10, advance=8.0, rng=(6, 16), kinds=("gap",), damage=1.3,
                knock=1.4, plant=(), hit=MOUTH, stages={"windup": [(0.3, draw), (0.7, coil), (1.0, peak)], "active": act, "recover": [(0.5, back)]}, lag=_lag())


def lash(r):
    """For whoever is behind it (LIGHT): it bends into a curve, its tail swung round to one side, and whips the fan
    across behind it."""
    gather = _pose(yaw=-5, roll=-3, at=(0.5, 1, -1), jaw=4, fins=dict(spread=10), tail=dict(sway=20, lift=4))
    peak = _pose(yaw=-16, roll=-9, at=(1.5, 1.5, -2), jaw=14, gills=18, fl=dict(spread=26, lift=16), fr=dict(spread=-12, lift=-8), tail=dict(sway=56, curl=10, lift=10),
                 rod=dict(sway=12, swing=-8, raise_=8), dorsal=-14)
    hit = _pose(yaw=12, roll=6, at=(-1.5, -0.5, 1), jaw=2, fl=dict(spread=-6), fr=dict(spread=16, lift=6), tail=dict(sway=-52, curl=-4, lift=-4))
    held = _pose(yaw=13, roll=7, at=(-2, -0.8, 1.5), jaw=2, fl=dict(spread=-8), fr=dict(spread=18, lift=8), tail=dict(sway=-58, curl=-6, lift=-6))
    sink = _pose(yaw=10, roll=5, at=(-1.5, -0.6, 1), jaw=2, fl=dict(spread=-6), fr=dict(spread=14, lift=6), tail=dict(sway=-46, curl=-4, lift=-4))
    back = _pose(yaw=3, roll=1, at=(-0.5, 0, 0), jaw=2, tail=dict(sway=-10))
    return Move("lash", "LIGHT", 10, 4, 14, through=sink, track=0.0, rng=(0, 7.0), kinds=("behind",), damage=1.0, knock=1.2, plant=(),
                hit=[("fan", (0, 0, 6), 10.0), ("tail2", (0, 0, 4), 8.0), ("tail1", (0, 0, 4), 8.0)],
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


BEAM_FROM = (0.0, 4.5, 3.4)  # where the Moonbeam leaves it: the moon, raised and tipped at you (blocks: x, up, forward)


def beam(r):
    """The Moonbeam (HEAVY): it rises, lifting its rod straight up until the moon hangs high over its head, the moon
    swelling; then it tips the rod down at you and the beam comes down out of the moon."""
    l0 = _pose(pitch=-4, at=(0, 2, 1), jaw=4, gills=4, fins=dict(lift=8), rod=dict(raise_=20))
    l1 = _pose(pitch=-8, at=(0, 4, 2), jaw=8, gills=10, fins=dict(spread=14, lift=14), tail=dict(curl=-10), rod=dict(raise_=46, swing=-4), dorsal=-8)
    peak = _pose(pitch=-10, at=(0, 5, 3), jaw=10, gills=14, fins=dict(spread=16, lift=18), tail=dict(curl=-12), rod=dict(raise_=56, swing=-6), dorsal=-10)
    aim = _pose(pitch=6, at=(0, 4, 1), jaw=12, gills=10, fins=dict(spread=8, lift=6), tail=dict(curl=6), rod=dict(raise_=30, swing=8), dorsal=-6)
    hold = _pose(pitch=7, at=(0, 3.5, 0.5), jaw=12, gills=12, fins=dict(spread=10, lift=8, scull=6, ph=2.0), tail=dict(curl=6, wave=4, ph=2.0), rod=dict(raise_=28, swing=6),
                 dorsal=-6)
    back = _pose(pitch=1, at=(0, 1, 0), jaw=4, fins=dict(lift=2), rod=dict(raise_=4))
    return Move("beam", "HEAVY", 18, 10, 20, through=hold, title="Moonbeam", hold=8, track=4.0, ability="Moonbeam", origin=BEAM_FROM, rng=(4, 20),
                kinds=("ranged",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, l0), (0.7, l1), (1.0, peak)], "active": [(0.4, aim), (1.0, hold)], "recover": [(0.5, back)]}, lag=_lag())


def lift(r):
    """The Tide Turn: it sinks, pressing its fins down, then sweeps them up hard and rises with them, and holds the sea
    up off the floor beating them, its tail curling up."""
    s0 = _pose(pitch=3, at=(0, -3, 0), jaw=4, gills=6, fins=dict(spread=-6, lift=-16), tail=dict(lift=-6), rod=dict(droop=6))
    low = _pose(pitch=5, at=(0, -6, 0), jaw=8, gills=12, fins=dict(spread=-10, lift=-26), tail=dict(lift=-10, curl=-8), rod=dict(droop=10), dorsal=6)
    act = [(0.25, _pose(pitch=-10, at=(0, 6, 0), jaw=14, gills=20, fins=dict(spread=26, lift=40), tail=dict(lift=14, curl=18), rod=dict(raise_=14), dorsal=-14))]
    for k in range(3):  # it holds the water up, beating its fins
        f = 0.25 + 0.75 * (k + 1) / 3
        act.append((f - 0.125, _pose(pitch=-12, at=(0, 8.5, 0), jaw=12, gills=14, fins=dict(spread=30, lift=30), tail=dict(lift=16, curl=22), rod=dict(raise_=12), dorsal=-12)))
        act.append((f, _pose(pitch=-10, at=(0, 7.5, 0), jaw=11, gills=20, fins=dict(spread=24, lift=46), tail=dict(lift=12, curl=24), rod=dict(raise_=10), dorsal=-12)))
    back = _pose(pitch=-2, at=(0, 3, 0), jaw=4, fins=dict(lift=6), tail=dict(curl=4), rod=dict(raise_=3))
    mv = Move("lift", "NONE", 14, 24, 16, through=act[-1][1], title="Tide Turn", track=0.0, ability="Tide Turn", origin=(0, 0, 0), rng=(0, 6),
              kinds=("area", "near"), damage=0, knock=0, plant=(), stages={"windup": [(0.3, s0), (1.0, low)], "active": act, "recover": [(0.5, back)]},
              lag=_lag())
    mv._moon = [(0.0, 1.0), ("active", 0.0, 1.2), ("recover", 0.5, 1.0)]
    return mv


MOUTH_FROM = (0.0, 2.6, 2.0)  # where the Undertow pulls you to: its open mouth, drawn back (blocks: x, up, forward)


def undertow(r):
    """The Undertow (PERILOUS, the signature): it backs away and lifts the moon out of the way, its jaw opening wider
    and wider until it is wider than its body, its gill covers flared and its fins braking; then it gulps the sea in
    three great heaves, and you with it, and shuts its mouth."""
    n = 8
    w = []
    for k in range(1, n + 1):
        f = k / n
        tr = math.sin(k * 2.3) * f  # it trembles as it strains
        w.append((f if k > 2 else 0.3 * k / 2, _pose(pitch=-6 * f, yaw=1.6 * tr, roll=1.2 * tr, at=(0.4 * tr, 3 * f, 8 * f), jaw=50 * f, gills=40 * f,
                                                   fins=dict(spread=30 * f, lift=12 * f, scull=8 * f, ph=k * 1.4), tail=dict(curl=-10 * f, wave=6 * f, ph=k * 1.1),
                                                   rod=dict(raise_=34 * f, swing=-4 * f), dorsal=-14 * f)))
    act = []
    for k in range(3):  # three gulps: it heaves back, the gill covers clap and pump, the fins stroke the water back
        f = (k + 1) / 3
        act.append((f - 1 / 6, _pose(pitch=-8, at=(0, 3, 10), jaw=54, gills=22, fins=dict(spread=-6, lift=-6), tail=dict(curl=-12, sway=12), rod=dict(raise_=34),
                                     dorsal=-16)))
        act.append((f, _pose(pitch=-5, at=(0, 3, 8), jaw=46, gills=44, fins=dict(spread=30, lift=14), tail=dict(curl=-10, sway=-12), rod=dict(raise_=34), dorsal=-14)))
    shut = _pose(pitch=8, at=(0, 1, 2), jaw=SHUT, gills=4, fins=dict(spread=-8, lift=-6), tail=dict(sway=8), rod=dict(raise_=12, swing=10))
    settle = _pose(pitch=3, at=(0, 0.5, 1), jaw=2, gills=2, fins=dict(spread=-2), rod=dict(raise_=4, swing=2))
    mv = Move("undertow", "PERILOUS", 30, 30, 24, through=act[-1][1], title="Undertow", signature=True, track=3.0, ability="Undertow", origin=MOUTH_FROM,
              rng=(0, 14), kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": w, "active": act, "recover": [(0.35, shut), (0.7, settle)]},
              lag=_lag())
    return mv


def halo(r):
    """The Tide Halo: it curls round its moon, bowing its rod until the moon hangs before its mouth and cupping its fins
    about it, its tail curled under; the moon blazes, and it flings itself open and a ring of water flies off."""
    c0 = _pose(pitch=8, at=(0, 1, 0), jaw=10, gills=8, fins=dict(spread=20, lift=20), tail=dict(lift=10, curl=20), rod=dict(droop=30), dorsal=-6)
    curl = _pose(pitch=16, at=(0, 2, -1), jaw=14, gills=16, fins=dict(spread=34, lift=30), tail=dict(lift=18, curl=34), rod=dict(droop=44), dorsal=-12)
    act = [(0.25, _pose(pitch=-10, at=(0, 4, 2), jaw=30, gills=34, fins=dict(spread=-20, lift=-24), tail=dict(lift=-12, curl=-20), rod=dict(raise_=24, swing=-10),
                        dorsal=-18)),
           (0.6, _pose(pitch=-8, at=(0, 5, 2), jaw=24, gills=28, fins=dict(spread=-12, lift=-14, scull=8, ph=1.0), tail=dict(lift=-8, curl=-14, wave=5, ph=1.0),
                       rod=dict(raise_=20, swing=-4), dorsal=-14)),
           (1.0, _pose(pitch=-4, at=(0, 3, 1), jaw=12, gills=14, fins=dict(spread=-4, lift=-4), tail=dict(curl=-6), rod=dict(raise_=10), dorsal=-8))]
    back = _pose(pitch=-1, at=(0, 1, 0), jaw=4, rod=dict(raise_=3))
    mv = Move("halo", "NONE", 14, 26, 16, through=act[-1][1], title="Tide Halo", track=0.0, ability="Tide Halo", origin=(0, 3.5, 3.5), rng=(0, 99),
              kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": [(0.3, c0), (1.0, curl)], "active": act, "recover": [(0.5, back)]}, lag=_lag())
    mv._moon = [(0.0, 1.0), ("windup", 1.0, 1.3), ("active", 0.25, 1.7), ("active", 1.0, 1.15), ("recover", 1.0, 1.0)]
    return mv


def wake(r):
    """Its intro: hanging dark, it lifts its rod first, the moon brightening, then rises on great strokes of its tail
    with its fins spread, and opens its jaw wide, the gill covers flaring, and calls."""
    look = _pose(pitch=-3, at=(0, 1, 0), jaw=2, fins=dict(spread=6, lift=4), rod=dict(raise_=14, swing=-4))
    coil = _pose(pitch=-6, at=(0, 2, 1), jaw=8, gills=8, fins=dict(spread=16, lift=12), tail=dict(sway=22, curl=8), rod=dict(raise_=20, swing=-6), dorsal=-6)
    act = [(0.3, _pose(pitch=-12, at=(0, 6, 0), jaw=30, gills=30, fins=dict(spread=30, lift=22), tail=dict(sway=-24, curl=10), rod=dict(raise_=24), dorsal=-16)),
           (0.6, _pose(pitch=-10, yaw=2, at=(0, 7, 0), jaw=36, gills=36, fins=dict(spread=26, lift=26, scull=6, ph=1.5), tail=dict(sway=14, curl=12), rod=dict(raise_=22, swing=4),
                       dorsal=-16)),
           (1.0, _pose(pitch=4, at=(0, 5, -1), jaw=10, gills=10, fins=dict(spread=4, lift=2), tail=dict(sway=-8), rod=dict(raise_=6, swing=6), dorsal=-4))]
    mv = Move("wake", "NONE", 14, 30, 14, through=act[-1][1], track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, look), (1.0, coil)], "active": act, "recover": [(0.5, _pose(at=(0, 2, 0), jaw=3, rod=dict(raise_=2)))]}, lag=_lag())
    mv._moon = [(0.0, 0.9), ("windup", 0.3, 1.15), ("active", 0.3, 1.3), ("recover", 1.0, 1.0)]
    return mv


def moves(r):
    mv = [bite("l"), bite("r", "bite_b"), engulf(r), lash(r), beam(r), lift(r), undertow(r), halo(r), wake(r)]
    mv[4]._moon = [(0.0, 1.0), ("windup", 0.7, 1.2), ("windup", 1.0, 1.35), ("active", 0.4, 1.5), ("active", 1.0, 1.4), ("recover", 1.0, 1.0)]
    return mv


def _moon_track(r, mv):
    """A move's moon: [(stage, fraction, scale)] in its stages' time (a bare (0.0, scale) is its start)."""
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    span = {"windup": W, "active": A, "recover": R_}
    pts = [(0.0, k[1]) if len(k) == 2 else (t0[k[0]] + k[1] * span[k[0]], k[2]) for k in mv._moon]
    _moon(r, core.clip_name(mv), sorted(pts))


def _swim(r, clip, tail=8.0, fins=7.0, gills=6.0, dorsal=4.0, hz=0.8, ramp=0.25):
    """A fish never stops swimming: under a move, after it is made, its tail keeps stroking, its fins sculling, its gill
    covers pumping and its spines working, at the move's own pace, faded in and out at the ends so it hands back to
    the idle exactly as it was."""
    c = r.clips[clip]
    L, n = c.length, int(round(c.length / core.TICK))
    w = 2 * math.pi * hz

    def env(t):
        e = max(0.0, min(1.0, t / ramp, (L - t) / ramp))
        return e * e * (3 - 2 * e)

    def add(bone, f):
        old = c.tracks.get((bone, "rot"))
        keys = []
        for k in range(n + 1):
            t = min(L, k * core.TICK)
            base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
            d = f(t)
            keys.append((t, tuple(base[i] + env(t) * d[i] for i in range(3)), "cat"))
        c.tracks[(bone, "rot")] = keys
    for k in range(len(TAIL)):
        add(f"tail{k}", lambda t, k=k: (0.0, tail * math.sin(w * t - 0.8 * k), 0.0))
    add("fan", lambda t: (0.0, 1.3 * tail * math.sin(w * t - 2.6), 0.0))
    for nm, sg in SIDES_:
        add("fin_" + nm, lambda t, sg=sg: (0.6 * fins * math.cos(1.25 * w * t), sg * fins * math.sin(1.25 * w * t), 0.0))
        add("gill_" + nm, lambda t, sg=sg: (0.0, sg * gills * math.sin(w * t - 1.5), 0.0))
    add("dorsal", lambda t: (dorsal * math.sin(w * t - 1.0), 0.0, 0.0))


FLOAT = ("barbel_", "kelp_")  # threads that lie on the floor rather than hold it up


def _on_floor(r, clip, margin=0.3):
    """Nothing of it goes into the floor: where a pose would put a part under it, the body is lifted by as much, the
    lift spread a little either side so it rises and settles rather than jumps (it comes to rest on its jaw, not
    through it)."""
    c = r.clips[clip]
    L, n = c.length, int(round(c.length / core.TICK))
    solid = [b.name for b in r.bones if b.cubes and not b.name.startswith(FLOAT)]
    under = [max(0.0, core.lowest(r, r.sample([(clip, min(L, k * core.TICK), 1.0)]), solid) - core.GROUND + margin) for k in range(n + 1)]
    if max(under) <= margin:
        return
    w = 3  # ticks either side
    hi = [max(under[max(0, k - w):k + w + 1]) for k in range(n + 1)]
    lift = [sum(hi[max(0, k - w // 2):k + w // 2 + 1]) / len(hi[max(0, k - w // 2):k + w // 2 + 1]) for k in range(n + 1)]
    lift = [max(a, b) for a, b in zip(lift, under)]
    old = c.tracks.get(("body", "pos"))
    keys = []
    for k in range(n + 1):
        t = min(L, k * core.TICK)
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        base[1] += lift[k]
        keys.append((t, tuple(base), "cat"))
    c.tracks[("body", "pos")] = keys


def clips(r):
    _idle(r)
    _flight(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        stage_clips(r, m)
        if getattr(m, "_moon", None):
            _moon_track(r, m)
        _swim(r, core.clip_name(m))
    for name in list(r.clips):
        _on_floor(r, name)
    return mv


# Its sounds (SPEC §35.8): deep water, an elder guardian's groan, the ring of the moon; chosen by ear on paper, not yet
# heard in game.
SOUNDS = {
    "tell_light": [("entity.guardian.attack", 1.0, 0.6), ("entity.elder_guardian.flop", 0.8, 0.7)],
    "tell_heavy": [("entity.elder_guardian.ambient", 1.0, 0.8), ("block.conduit.ambient.short", 0.9, 1.0)],
    "tell_perilous": [("entity.elder_guardian.curse", 0.7, 0.8), ("block.conduit.activate", 0.6, 1.0), ("ambient.underwater.loop.additions.ultra_rare", 0.8, 1.0)],
    "swing_light": [("entity.player.attack.sweep", 0.7, 0.6), ("entity.generic.splash", 0.8, 0.7)],
    "swing_heavy": [("entity.player.splash.high_speed", 0.7, 0.9), ("entity.elder_guardian.flop", 0.6, 0.9)],
    "cast": [("block.conduit.activate", 1.2, 0.9), ("block.amethyst_block.resonate", 0.8, 0.9)],
    "impact": [("entity.evoker_fangs.attack", 0.6, 0.9), ("entity.generic.splash", 0.6, 1.0)],
    "roar": [("entity.elder_guardian.ambient", 0.7, 1.2), ("entity.elder_guardian.curse", 0.6, 0.7)],
    "step": [("entity.fish.swim", 0.7, 0.6)],
}
MOVE_SOUNDS = {"beam": {"swing": "cast"}, "lift": {"swing": "cast", "impact": ""}, "undertow": {"swing": "roar", "impact": ""},
               "halo": {"swing": "cast", "impact": ""}, "wake": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["bite_b"], ["bite_b", "lift"]]},
            {"move": "lift", "weight": 2, "when": "near", "then": [["bite"]]},
            {"move": "beam", "weight": 3},
            {"move": "engulf", "weight": 3, "when": "far", "then": [["bite"]]},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        {"transition": "halo", "pool": [
            {"move": "bite", "weight": 3, "when": "near", "then": [["bite_b"], ["bite_b", "engulf"]]},
            {"move": "undertow", "weight": 2, "then": [["bite", "bite_b"]]},
            {"move": "halo", "weight": 1},
            {"move": "beam", "weight": 2, "when": "far"},
            {"move": "engulf", "weight": 3, "when": "far", "then": [["bite"]]},
            {"move": "lift", "weight": 2, "when": "near", "then": [["bite"]]},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.6, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("body", (0, 0, 0)), ("moon", (0, 8, 0)), ("jaw", (0, 0, -26)), ("fan", (0, 0, 12)), ("lure1", (0, 0, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
