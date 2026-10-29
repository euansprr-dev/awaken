"""The Seed Warden (SPEC §35.11, docs/bosses/ROLLOUT.md): the Seed's Guardian, a Caster. The Husk That Opens, picked
from its silhouette round 2026-10-29: "The last door. It has been waiting."

A seed as big as a house, hanging in the dark at the bottom of everything. Four great valves of husk close round it
the way a chestnut's case closes round the nut: dark, lacquered and ridged, each with a pale keel up its middle that
a thread of light runs along, their edges lipped like overlapping scales and their tips hooked over the top. Through
the seams between them you see the seed itself, ivory and fine-grained, with a crack of light down its face. The seed
is two halves; when they part, the light inside stands in the shape of a door. A broken stalk hangs under its cup,
frayed into threads, and three small seeds circle it like moons.

It hangs half open in the air and breathes like a jellyfish, its valves closing and opening in a slow wave round it,
its seed turning inside them to watch you.

Moves (SPEC §35.11): one valve unfurls and sweeps across in front of it, and the other after it (LIGHT); the Clasp
(HEAVY, a gap closer: it throws all four valves open like a hand and drops on you, clasping them shut); its valves
splayed flat and whipped round at whoever is behind it; the Signal Lance (HEAVY: its front valves part, the seed opens
and the lance leaves the door); the Signal Marks (it opens to the sky like a flower and throws four lights up, one at
each clench); the Motes (it shakes like a seed head in the wind, the door opens a crack and its seeds go out of it);
and the Door Light (the signature: it shuts tight and trembles, the crack blazing, then flings every valve back and
opens the door all the way, and the light comes out). It waits shut on the floor, light leaking from its seams.
"""
import math

from kit import core, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import radial
import fauna_rig as R
from fauna_rig import Ramp, hexc

ID = "guardian_seed"
SCALE = 1.6  # the def's scale; the body is modelled at true size and the hitbox set round its pod

# The Husk That Opens (docs/bosses/ROLLOUT.md, A): the pod 30 px across and 33 tall over a cup that hangs 14 px off the
# floor. Value story: the door of light brightest, then the seed's ivory, the valves' pale insides and the threads of
# light up their keels; the husk outside the darkest, cool slate.
HOVER = 14.0  # the top of its cup over the floor: the threads under its stalk just clear it
SIDES_ = (("l", 1), ("r", -1))
# the valves, round the diagonals: (name, yaw) so each one's local -z points out from the seed
VALVES = (("fl", -45.0), ("fr", 45.0), ("bl", -135.0), ("br", 135.0))
HINGE = 8.0  # how far out from the middle the valves hinge on the cup's rim
# each valve segment from the hinge up: (width, length, keel width, rest bend in degrees, + opening); its section
# steps out to the keel, 2 px at the edges, 3 across the middle and 4 along the keel, so it reads curved
VSEG = ((14, 12, 6, 30.0), (18, 11, 6, -14.0), (14, 8, 4, -28.0), (7, 5, 0, -26.0))
# the seed from its foot up: (width, height, depth, z of its middle): each half is half as wide
SEED = ((8, 2, 7, 0.0), (12, 3, 10, 0.0), (14, 4, 12, 0.0), (16, 8, 13, 0.0), (14, 4, 12, 0.5), (12, 3, 10, 1.0), (8, 3, 8, 1.5),
        (4, 3, 4, 2.0))
HALF_PIVOT = 5.5  # the halves hinge down the back of the seed
KERNEL = (6, 16, 6, 6.0)  # the light inside: width, height, depth, and the height of its foot
MOTES = ((60.0, 24.0, 3.0), (180.0, 23.0, -2.0), (300.0, 25.0, 6.0))  # the small seeds round it: angle, distance, height


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, HOVER, 0)
    # the cup it sits in, and the broken stalk under it frayed into threads
    _box(bd, "cup0", 0, -3, 0, 14, 3, 14)
    _box(bd, "cup1", 0, -5, 0, 10, 2, 10)
    st = _bone(bd, "stalk0", 0, -5, 0)
    _box(st, "stalk0", 0, -3, 0, 4, 3, 4)
    st1 = _bone(st, "stalk1", 0, -3, 0)
    _box(st1, "stalk1", 0, -2, 0, 3, 2, 3)
    for k, (x, z) in enumerate(((1.0, -1.0), (-1.0, -0.5), (0.0, 1.0))):
        parts.chain(st1, f"fibre{k}_", (x, 2, z), 1, link=(1, 3, 1))
    # the seed: two halves hinged down its back, the light inside them
    sd = _bone(bd, "seed", 0, 0, 0)
    for nm, sg in SIDES_:
        h = _bone(sd, "half_" + nm, 0, 0, HALF_PIVOT)
        y = 0.0
        for k, (w, hh, d, zc) in enumerate(SEED):
            hw = w // 2
            _box(h, f"seed{k}_" + nm, sg * hw / 2, y, zc - HALF_PIVOT, hw, hh, d)
            y += hh
    door = _bone(sd, "door", 0, KERNEL[3] + KERNEL[1] / 2, -0.5)
    _box(door, "kernel", 0, -KERNEL[1] / 2, 0, KERNEL[0], KERNEL[1], KERNEL[2])
    # the valves round it, each on a root that faces it out along its diagonal
    ring = _bone(bd, "ring", 0, 0, 0)
    for nm, yaw in VALVES:
        root = _bone(ring, "vroot_" + nm, 0, 0, 0, (0, yaw, 0))
        parent, piv = root, (0, 0, -HINGE)
        for k, (w, L, kw, bend) in enumerate(VSEG):
            sb = parent.bone(f"valve_{nm}{k}", piv, (bend, 0, 0))
            sb.cube(f"valve_{nm}{k}", -w / 2, -L, -2, w, L, 2)
            sb.cube(f"band_{nm}{k}", -(w - 6) / 2 if k < 3 else -1.5, -L, -3, w - 6 if k < 3 else 3, L, 1)
            if kw:  # the keel up its middle
                sb.cube(f"keel_{nm}{k}", -kw / 2, -L + 1, -4, kw, L - 1, 1)
            if 0 < k < 3:  # the lip of the scale over the one below
                sb.cube(f"lip_{nm}{k}", -(w - 2) / 2, -2, -4, w - 2, 2, 2)
            if k == 3:  # the hook at its tip
                sb.cube(f"hook_{nm}", -1.5, -L - 2, -1, 3, 2, 3)
            parent, piv = sb, (0, -L, 0)
    # the small seeds that circle it
    orb = _bone(bd, "orbit", 0, 16, 0)
    for k, (a, dist, up) in enumerate(MOTES):
        x, z = dist * math.sin(a * DEG), -dist * math.cos(a * DEG)
        m = _bone(orb, f"mote{k}", x, up, z)
        _box(m, f"mote{k}", 0, -2.5, 0, 3, 5, 3)
        _box(m, f"motetip{k}", 0, 2.5, 0, 1, 2, 1)
    r.look = [("seed", 0.35, 0.25)]
    r.shadow = 2.0
    r.size = 1.0
    # secondary parts (SPEC §35.10): the hooked tips, the stalk and its threads, and the small seeds on their orbits
    r.springs = ([(f"valve_{nm}3", 0.25, 2.2, 0.2) for nm, _ in VALVES]
                 + [("stalk0", 0.3, 1.6, 0.2), ("stalk1", 0.4, 1.9, 0.16)]
                 + [(f"fibre{k}_0", 0.5, 2.0 + 0.2 * k, 0.12) for k in range(3)]
                 + [(f"mote{k}", 0.45, 1.1 + 0.15 * k, 0.14) for k in range(3)])
    r.guard = []
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

HUSK = Ramp("#06070b", "#0a0c12", "#0f1219", "#151922", "#1c212c", "#242a37", "#2d3443", "#373f50", "#424b5e")
KEEL = Ramp("#2a303c", "#353c49", "#424a59", "#505969", "#5f697a", "#707a8c")
INNER = Ramp("#8c7d58", "#a8986e", "#c2b386", "#d8cb9f", "#e8deb9", "#f4eed6")
COAT = Ramp("#3f4450", "#4b515e", "#5a606e", "#6a717f", "#7b8291", "#8d94a3", "#a0a7b5")
CUP = Ramp("#0b0a0c", "#121014", "#1a171c", "#232026", "#2d2930", "#38333b")
STALK = Ramp("#17130f", "#211b15", "#2c241c", "#382e24", "#453a2e")
LIGHT = ("#ffffff", "#fffaf0", "#fff2b0", "#d9b860")  # the Seed's light: white, a warm white, its pale gold, the gold at its edges
BLUE = "#e8f0ff"


def paint(r, P_):
    """Cool slate husk, mottled and lacquered, darkest outside, its scales' lips catching a pale edge; pale keels up
    each valve with a thread of light; the valves' insides pale gold, veined with light; the seed ivory and fine-grained
    with a crack of light down its face, its halves' insides pale and lit; the door of light white; the cup and stalk
    near black, the threads dry; the small seeds ivory with lit tips."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    rough = R.p_blotch(9, scale=4, amount=0.6, levels=3)
    for c in of("band_"):  # the lacquer along each valve's middle, a shade lighter
        P_.paint(c, HUSK, 5.2, rough, grad=1.8, rim=1.0, ao=0.8, noise=0.6, nscale=3)
    for c in of("valve_"):
        P_.paint(c, HUSK, 4.0, rough, grad=1.8, rim=0.9, ao=1.0, noise=0.6, nscale=3)
        f = P_.face(c, "south")  # the inside, toward the seed: pale satin gold, ribbed, veined with light
        g = P_.face(c, "south", glow=True)
        mid = f.w // 2
        for i, j in f.each():
            rib = abs(i - mid) % 3 == 2
            vein = i == mid or (abs(i - mid) == 1 + (j + 1) // 2 and j % 4 < 2 and abs(i - mid) < f.w // 2 - 1)
            base = INNER.at(4 if j < 2 else 3) if not rib else INNER.at(2)
            f.set(i, j, hexc(LIGHT[2]) if vein else base)
            if vein:
                g.set(i, j, hexc(LIGHT[1], 150 if i == mid else 90))
    for c in of("keel_"):
        P_.paint(c, KEEL, 3.0, R.p_bands(every=3, depth=0.6), grad=1.2, rim=1.0, ao=0.5, noise=0.4)
        f, g = P_.face(c, "north"), P_.face(c, "north", glow=True)
        for i, j in f.each():  # a thread of light up the keel
            if i == f.w // 2:
                f.set(i, j, hexc(LIGHT[2]))
                g.set(i, j, hexc(LIGHT[1], 170))
    for c in of("lip_"):
        P_.paint(c, HUSK, 4.0, None, grad=0.8, rim=1.4, ao=0.4)
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, KEEL.at(1 + R._h(c, i) % 2))
    for c in of("hook_"):
        P_.paint(c, HUSK, 5.0, None, grad=1.0, rim=1.4, ao=0.3)
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, KEEL.at(3 + R._h(c, i, j) % 3))
    # the seed: ivory, fine-grained, the crack of light down its face; inside each half pale and lit
    grain = R.p_blotch(5, scale=1, amount=0.35, levels=2)
    for nm, sg in SIDES_:
        for k in range(len(SEED)):
            c = f"seed{k}_" + nm
            P_.paint(c, COAT, 3.6, grain, grad=1.4, rim=0.7, ao=0.8, noise=0.5, nscale=2)
            f, g = P_.face(c, "north"), P_.face(c, "north", glow=True)
            seam = 0 if sg > 0 else f.w - 1  # the column along the middle (a north face runs from its low x)
            for i, j in f.each():
                if i == seam:
                    f.set(i, j, hexc(LIGHT[1]))
                    g.set(i, j, hexc(LIGHT[0], 235))
            mid = "west" if sg > 0 else "east"  # the face that lies against the other half: pale, lit from the door, veined
            f, g = P_.face(c, mid), P_.face(c, mid, glow=True)
            for i, j in f.each():
                front = i if sg > 0 else f.w - 1 - i  # 0 at the front edge (a west face runs from its front, an east from its back)
                vein = (j + k * 3) % 5 == 0 and front < f.w - 2
                f.set(i, j, hexc(LIGHT[2]) if vein else INNER.at(5 if front < 2 else 4 if front < 5 else 3))
                g.set(i, j, hexc(LIGHT[1], 170 if vein else max(0, 110 - 22 * front)))
    # the door of light: white, a door's shape in a warm white on its face, gold at its edges
    P_.paint("kernel", Ramp(*LIGHT[::-1]), 2.0, None, grad=0.3, rim=0.2, ao=0.0)
    for fname in ("north", "south", "west", "east", "top", "bottom"):
        f, g = P_.face("kernel", fname), P_.face("kernel", fname, glow=True)
        for i, j in f.each():
            edge = i in (0, f.w - 1) or j in (0, f.h - 1)
            arch = fname == "north" and 1 <= i <= f.w - 2 and j >= 2 and not (j == 2 and i in (1, f.w - 2))
            col = LIGHT[0] if arch else (LIGHT[3] if edge else LIGHT[1])
            f.set(i, j, hexc(col))
            g.set(i, j, hexc(LIGHT[0] if arch else LIGHT[1], 255 if arch else 210))
    for c in ("cup0", "cup1"):
        P_.paint(c, CUP, 3.2, rough, grad=1.4, rim=0.8, ao=1.0, noise=0.6)
    f, g = P_.face("cup0", "top"), P_.face("cup0", "top", glow=True)  # light pooled in the cup under the seed
    for i, j in f.each():
        if 2 <= i <= f.w - 3 and 2 <= j <= f.h - 3:
            f.set(i, j, hexc(LIGHT[3]))
            g.set(i, j, hexc(LIGHT[2], 90))
    for c in of("stalk", "fibre"):
        P_.paint(c, STALK, 3.0, R.p_bands(every=2, depth=0.7, vertical=True), grad=1.0, rim=0.6, ao=0.6, noise=0.5)
    for k in range(len(MOTES)):
        P_.paint(f"mote{k}", COAT, 2.8, None, grad=1.0, rim=0.8, ao=0.2)
        P_.paint(f"motetip{k}", Ramp(LIGHT[2], LIGHT[0]), 1.0)
        for fname in ("north", "south", "west", "east", "top", "bottom"):
            g = P_.face(f"motetip{k}", fname, glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(LIGHT[0], 230))
        g = P_.face(f"mote{k}", "bottom", glow=True)
        for i, j in g.each():
            g.set(i, j, hexc(LIGHT[1], 120))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion (SPEC §35.4): one pose function over its parts; the clips are keys of it

RING = ("fl", "bl", "br", "fr")  # the valves in order round it, so a wave can travel round
SPREAD = {"fl": 1, "fr": -1, "bl": -1, "br": 1}  # the splay that leans each valve away from the seam in front of it


def _valve(p, nm, open=0.0, curl=0.0, splay=0.0, sweep=0.0):
    """One valve: open swings it out from the seed (+) or shut (-), curl bends it in along its length (+) or back
    (-), splay leans it round the ring sideways while it stands up, and sweep swings it round the ring about the
    upright at its hinge (+ toward the seam on its right, seen from outside), which is how an open valve sweeps."""
    p[f"valve_{nm}0"] = (open, sweep, splay)
    p[f"valve_{nm}1"] = (-0.3 * curl, 0.0, 0.0)
    p[f"valve_{nm}2"] = (-0.4 * curl, 0.0, 0.0)
    p[f"valve_{nm}3"] = (-0.5 * curl, 0.0, 0.0)


def _pose(pitch=0.0, yaw=0.0, roll=0.0, at=(0.0, 0.0, 0.0), open=0.0, curl=0.0, splay=0.0, v=None, part=0.0, look=(0.0, 0.0, 0.0), ring=0.0,
          stalk=(0.0, 0.0), orbit=(0.0, 0.0, 0.0), motes=None):
    """The body: pitch (+ tipping its top forward), yaw (+ turning to its right), roll (+ its left side down), at (x
    to its left, up, z back); every valve open, curl and splay as _valve, v {valve: its own _valve arguments} on top;
    the seed's halves part (+) and the seed itself turns (look: pitch, yaw, roll) inside its valves; the ring of valves
    turns round it (ring); the stalk swings (back, to its left); the small seeds' orbit tips and rocks, and motes moves
    each of them [(x, up, z)]."""
    p = {"body": {"rot": (pitch, yaw, roll), "pos": tuple(at)}}
    for nm in RING:
        kw = dict(open=open, curl=curl, splay=splay * SPREAD[nm])
        for k, val in ((v or {}).get(nm) or {}).items():
            kw[k] = kw.get(k, 0.0) + val
        _valve(p, nm, **kw)
    p["half_l"], p["half_r"] = (0.0, -part, 0.0), (0.0, part, 0.0)
    p["seed"] = tuple(look)
    p["ring"] = (0.0, ring, 0.0)
    p["stalk0"], p["stalk1"] = (stalk[0], 0.0, stalk[1]), (0.6 * stalk[0], 0.0, 0.6 * stalk[1])
    p["orbit"] = tuple(orbit)
    for k in range(len(MOTES)):
        p[f"mote{k}"] = {"rot": (0.0, 0.0, 0.0), "pos": tuple(motes[k]) if motes else (0.0, 0.0, 0.0)}
    return p


def _out(k, d, up=0.0):
    """A small seed's move straight out from its orbit by d, and up."""
    a = MOTES[k][0] * DEG
    return (d * math.sin(a), up, d * math.cos(a))


def _ground(k, body_up):
    """How far down a small seed must go to lie on the floor, the body lowered by body_up."""
    return -(HOVER + body_up + 16 + MOTES[k][2] - 2.5)


def _scale(r, clip, bone, pts, mode="cat"):
    r.clips[clip].scale(bone, [(t, v, v, v) for t, v in pts], mode)


def _idle(r):
    """Hanging in the dark, breathing like a jellyfish: its valves close quickly and open slowly, twice in the loop, a
    ripple running round them so they never move as one; each close lifts it a little and it sinks back as they open.
    Its seed turns inside them, looking about, its halves easing apart as it breathes out and the light in it
    swelling; the stalk swings after the body; the small seeds rock on their orbit and bob."""
    L, n = 6.0, 60
    keys = []
    for k in range(n + 1):
        u = k / n
        ph = 2 * math.pi * 2 * u
        w = ph + 0.5 * math.sin(ph)  # the close is quick, the opening slow
        sl = 2 * math.pi * u
        v = {}
        for i, nm in enumerate(RING):
            o = -7.0 * math.sin(w - 0.45 * i)
            v[nm] = dict(open=o, curl=-0.6 * o, splay=1.5 * math.sin(sl + 1.6 * i))
        keys.append((L * u, _pose(pitch=1.0 * math.sin(sl + 2.0) + 0.7 * math.sin(w - 1.0), yaw=2.5 * math.sin(sl), roll=1.2 * math.sin(sl + 1.0),
                                  at=(0.3 * math.sin(sl + 0.4), 1.3 * math.sin(w - 0.7), 0.0), v=v, part=1.6 - 1.4 * math.sin(w - 0.3),
                                  look=(2.5 * math.sin(2 * sl - 0.8), 6.0 * math.sin(sl + 0.5), 1.5 * math.sin(sl - 0.6)),
                                  stalk=(-4.0 * math.sin(w - 1.4) + 2.0 * math.sin(sl + 0.3), 3.0 * math.sin(sl + 1.4)),
                                  orbit=(2.0 * math.sin(sl), 18.0 * math.sin(sl - 0.7), 2.0 * math.sin(sl + 1.4)),
                                  motes=[(0.0, 1.5 * math.sin(2 * sl + 2.1 * j), 0.0) for j in range(len(MOTES))])))
    keyed(r, "idle", L, keys, loop=True)
    _scale(r, "idle", "door", [(L * k / 12, 1 + 0.05 * math.sin(2 * math.pi * 2 * k / 12 + math.pi / 2)) for k in range(13)])


def _flight(r):
    """On top of the idle while it moves: it leans its top into the way it goes and pulses harder, its back valves
    flaring like a skirt and its stalk streaming back; it banks into its turns, the valves outside the turn flaring."""
    L, n = 1.5, 12
    keys = []
    for k in range(n + 1):
        u = k / n
        ph = 2 * math.pi * u
        w = ph + 0.5 * math.sin(ph)
        v = {nm: dict(open=-9.0 * math.sin(w - 0.4 * i) + (6.0 if nm[0] == "b" else -2.0), curl=5.0 * math.sin(w - 0.4 * i)) for i, nm in enumerate(RING)}
        keys.append((L * u, _pose(pitch=10.0 + 1.5 * math.sin(w - 1.0), at=(0.0, 1.0 * math.sin(w - 0.8), 0.0), v=v, look=(-6.0, 0.0, 0.0),
                                  stalk=(18.0 + 4.0 * math.sin(w - 1.6), 0.0), orbit=(-4.0, 0.0, 0.0))))
    keyed(r, "fly", L, keys, loop=True)
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # into a turn to its left, its left side dips; the valves on its right flare
        keys = []
        for k in range(n + 1):
            u = k / n
            ph = 2 * math.pi * u
            out_ = ("fr", "br") if sg > 0 else ("fl", "bl")
            v = {vn: dict(open=(10.0 if vn in out_ else -4.0) + 3.0 * math.sin(ph - 0.4 * i)) for i, vn in enumerate(RING)}
            keys.append((1.0 * u, _pose(roll=8.0 * sg, yaw=-4.0 * sg, at=(sg * 1.0, 0.0, 0.0), v=v, look=(0.0, 6.0 * sg, 0.0), stalk=(4.0, -14.0 * sg),
                                        orbit=(0.0, -8.0 * sg, 4.0 * sg))))
        keyed(r, nm, 1.0, keys, loop=True)


def _sag(low=1.0):
    """Shut down: it sinks to the floor and settles on its cup, its stalk laid over under it; the valves fall to
    loosely and one hangs slack, the seed bowed and shut; the small seeds sink low beside it."""
    return _pose(pitch=4 * low, roll=-3 * low, at=(0.0, -7.0 * low, 0.0), open=-10 * low, curl=8 * low, v={"fl": dict(open=6 * low, splay=6 * low, curl=-6 * low)},
                 look=(12 * low, 0.0, 0.0), stalk=(0.0, 84.0 * low), orbit=(0.0, -6 * low, 0.0),
                 motes=[(0.0, -9.0 * low - 2 * j * low, 0.0) for j in range(len(MOTES))])


def _reactions(r):
    """Struck, it flinches shut, its valves clenching and its seed tipping back, its stalk flung; from the side it
    rolls away and the valves on that side clench. Broken, it sinks to the floor shut; it rises out of it on one great
    pulse of its valves."""
    hit = _pose(pitch=-9, at=(0, 1.5, 3), open=-16, curl=10, look=(-8, 0, 0), stalk=(-14, 0), orbit=(-6, 0, 0))
    back = _pose(pitch=3, at=(0, -0.5, 0), open=5, curl=-3, part=2, look=(3, 0, 0), stalk=(6, 0), orbit=(2, 0, 0))
    keyed(r, "hurt", 0.6, [(0, _pose()), (0.08, hit), (0.32, back), (0.6, _pose())])
    _scale(r, "hurt", "door", [(0, 1.0), (0.08, 0.85), (0.32, 1.05), (0.6, 1.0)])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it rolls away to its right, its left valves clenching
        near = ("fl", "bl") if sg > 0 else ("fr", "br")
        h = _pose(roll=-12 * sg, yaw=-7 * sg, pitch=-4, at=(-3 * sg, 1.0, 1), v={vn: dict(open=-18 if vn in near else 8, curl=10 if vn in near else -4) for vn in RING},
                  look=(-4, 10 * sg, 0), stalk=(-4, 16 * sg), orbit=(0, 10 * sg, -6 * sg))
        b = _pose(roll=4 * sg, yaw=2 * sg, at=(sg * 0.5, -0.4, 0), v={vn: dict(open=4 if vn in near else -2) for vn in RING}, look=(0, -3 * sg, 0), stalk=(0, -6 * sg))
        keyed(r, nm, 0.65, [(0, _pose()), (0.08, h), (0.34, b), (0.65, _pose())])
    keyed(r, "kneel", 0.9, [(0, _pose()), (0.25, _pose(pitch=-5, at=(0, 2, 1), open=14, curl=-6, part=6, look=(-8, 0, 0), stalk=(-6, 10))),
                            (0.65, _sag(1.06)), (0.9, _sag())])
    _scale(r, "kneel", "door", [(0, 1.0), (0.25, 1.15), (0.9, 0.85)])
    up_ = _pose(pitch=-5, at=(0, 4, 1), open=-14, curl=10, look=(-10, 0, 0), stalk=(-16, 20), orbit=(-6, 6, 0))
    rel = _pose(pitch=3, at=(0, 2.5, 0), open=12, curl=-6, part=4, look=(2, 0, 0), stalk=(8, 0), orbit=(2, 0, 0))
    keyed(r, "rise", 1.2, [(0, _sag()), (0.45, up_), (0.8, rel), (1.2, _pose())])
    _scale(r, "rise", "door", [(0, 0.85), (0.45, 1.1), (1.2, 1.0)])
    _death(r)


def _death(r):
    """Its fall: it flings its valves open and its seed gapes, the light in it flaring; it pulses once more, weakly,
    then drops to the floor and its valves fall open round it like a spent flower, the seed lying open in the middle
    and its light going out; the small seeds fall and lie on the floor beside it."""
    conv = _pose(pitch=-12, at=(0, 3, 2), open=40, curl=-20, part=30, look=(-20, 0, 0), stalk=(-18, 10), orbit=(-8, 10, 0))
    weak = _pose(pitch=6, roll=-5, at=(0, -2, 0), open=8, curl=-4, part=12, look=(6, 0, 0), stalk=(8, -4), orbit=(4, 4, 0),
                 motes=[(0.0, -3.0 - j, 0.0) for j in range(len(MOTES))])
    gasp = _pose(pitch=-4, roll=3, at=(0, 1, 0), open=28, curl=-14, part=24, look=(-10, 0, 0), stalk=(-8, 6), orbit=(-2, 8, 0),
                 motes=[(0.0, -5.0 - j, 0.0) for j in range(len(MOTES))])
    fall = _pose(pitch=8, roll=-8, at=(0, -8, 0), open=46, curl=-14, part=24, look=(10, 0, 0), stalk=(0, 50), orbit=(4, 6, 0),
                 motes=[(0.0, _ground(j, -8) * 0.6, 0.0) for j in range(len(MOTES))])
    down = lambda d: _pose(pitch=5, roll=-6 - d, at=(0, -9.0, 0), open=74 + d, curl=-30 - d, part=40 + 2 * d, look=(18 + d, 0, 0), stalk=(0, 86),
                           orbit=(0, 6, 0), motes=[(0.0, _ground(j, -9.0), 0.0) for j in range(len(MOTES))])
    keys = [(0, _pose()), (0.35, conv), (0.9, weak), (1.5, gasp), (2.4, fall), (3.2, down(0)), (4.2, down(2)), (5.0, down(2.5))]
    keyed(r, "death", 5.0, keys)
    _scale(r, "death", "door", [(0, 1.0), (0.35, 1.3), (0.9, 1.0), (1.5, 1.18), (2.4, 0.95), (3.2, 0.7), (4.2, 0.45), (5.0, 0.3)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _lag(**kw):
    out = {"stalk1": 0.06, "mote0": 0.05, "mote1": 0.07, "mote2": 0.09}
    out.update(kw)
    return out


def _tips(*nms):
    return [(f"valve_{nm}3", (0, -4, 0), 8.0) for nm in nms] + [(f"valve_{nm}2", (0, -5, -1), 8.0) for nm in nms]


def swipe(side="l", mid="swipe"):
    """One front valve unfurls out to its side and back, the body leaning in over you and turning away with it, the
    back valves clenching and the other front one reaching across; then it turns back hard and the valve sweeps across
    in front of it, low (LIGHT)."""
    sg = 1 if side == "l" else -1
    nm, other = ("fl", "fr") if sg > 0 else ("fr", "fl")
    sp = SPREAD[nm]  # + sweeps it round across the front
    so = SPREAD[other]
    # the back valves clench; the other front one is its counterweight, reaching across while this one winds back
    rest_ = lambda o, c, oo=0.0, osw=0.0: {vn: dict(open=oo, curl=0.3 * c, sweep=osw * so) if vn == other else dict(open=o, curl=c) for vn in RING if vn != nm}
    gather = _pose(pitch=5, yaw=-17 * sg, roll=-5 * sg, at=(sg * 1.2, 1.2, 1.2), v={nm: dict(open=50, curl=3, sweep=-24 * sp), **rest_(-12, 8, 18, 8)}, part=3, ring=-8 * sg,
                   look=(0, 8 * sg, 0), stalk=(-4, 8 * sg), orbit=(0, 10 * sg, 0))
    peak = _pose(pitch=9, yaw=-30 * sg, roll=-9 * sg, at=(sg * 2.0, 2.0, 2.0), v={nm: dict(open=78, curl=6, sweep=-48 * sp), **rest_(-21, 15, 32, 18)}, part=5, ring=-18 * sg,
                 look=(0, 20 * sg, 0), stalk=(-10, 20 * sg), orbit=(0, 26 * sg, 0))
    # the sweep: the valve goes across in front of it, the body turning through, the seed still on you
    hit = _pose(pitch=7, yaw=16 * sg, roll=6 * sg, at=(-sg * 1.5, -1.0, -5.0), v={nm: dict(open=74, curl=4, sweep=38 * sp), **rest_(-6, 3, 14, 4)}, part=2, ring=8 * sg,
                look=(0, -10 * sg, 0), stalk=(10, -10 * sg), orbit=(0, -10 * sg, 0))
    held = _pose(pitch=6, yaw=21 * sg, roll=8 * sg, at=(-sg * 2.0, -1.5, -6.0), v={nm: dict(open=70, curl=2, sweep=50 * sp), **rest_(-5, 3, 12, 2)}, part=2, ring=11 * sg,
                 look=(0, -13 * sg, 0), stalk=(12, -14 * sg), orbit=(0, -16 * sg, 0))
    sink = _pose(pitch=5, yaw=17 * sg, roll=6 * sg, at=(-sg * 1.5, -1.2, -5.0), v={nm: dict(open=60, curl=2, sweep=44 * sp), **rest_(-4, 2, 14, 4)}, part=2, ring=9 * sg,
                 look=(0, -11 * sg, 0), stalk=(10, -12 * sg), orbit=(0, -14 * sg, 0))
    back = _pose(pitch=2, yaw=7 * sg, roll=2 * sg, at=(-sg * 0.6, -0.4, -2.0), v={nm: dict(open=18, curl=0, sweep=14 * sp), **rest_(-1, 1, 5, 1)}, part=1, ring=3 * sg,
                 look=(0, -5 * sg, 0), stalk=(4, -6 * sg), orbit=(0, -6 * sg, 0))
    return Move(mid, "LIGHT", 10, 4, 14, through=sink, track=8.0, rng=(0, 5.5), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(), hit=_tips(nm),
                stages={"windup": [(0.3, gather), (0.65, core._scaled(peak, 0.9)), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]},
                lag=_lag())


def clasp(r):
    """The Clasp (HEAVY, a gap closer): it draws up and back, throwing all four valves open like a hand over you and
    its seed parting; then it drops on you top first and clasps them shut."""
    rear = _pose(pitch=-8, at=(0, 3, 4), open=22, curl=2, part=3, look=(-6, 0, 0), stalk=(-8, 0), orbit=(-4, 0, 0))
    spread = _pose(pitch=-12, at=(0, 5, 6), open=44, curl=10, part=6, look=(-10, 0, 0), stalk=(-14, 0), orbit=(-8, 0, 0))
    peak = _pose(pitch=-14, at=(0, 6, 7), open=50, curl=14, part=8, look=(-12, 0, 0), stalk=(-16, 0), orbit=(-10, 0, 0))
    act = [(0.35, _pose(pitch=16, at=(0, 0, -8), open=44, curl=10, part=6, look=(8, 0, 0), stalk=(10, 0), orbit=(6, 0, 0))),
           (0.7, _pose(pitch=22, at=(0, -3, -14), open=10, curl=10, part=2, look=(12, 0, 0), stalk=(24, 0), orbit=(10, 0, 0))),
           (1.0, _pose(pitch=24, at=(0, -4, -16), open=-16, curl=16, look=(14, 0, 0), stalk=(28, 0), orbit=(12, 0, 0)))]
    sink = _pose(pitch=20, at=(0, -3.5, -15), open=-13, curl=13, look=(12, 0, 0), stalk=(24, 0), orbit=(10, 0, 0))
    back = _pose(pitch=8, at=(0, -1, -7), open=4, curl=2, part=1, look=(4, 0, 0), stalk=(8, 0), orbit=(4, 0, 0))
    mv = Move("clasp", "HEAVY", 16, 12, 20, through=sink, title="Clasp", track=6.0, lock=10, advance=8.0, rng=(6, 16), kinds=("gap",), damage=1.3, knock=1.4,
              plant=(), hit=_tips("fl", "fr") + [("seed", (0, -16, -8), 12.0)],
              stages={"windup": [(0.3, rear), (0.7, spread), (1.0, peak)], "active": act, "recover": [(0.5, back)]}, lag=_lag())
    mv._door = [(0.0, 1.0), ("windup", 1.0, 1.1), ("active", 1.0, 0.95), ("recover", 1.0, 1.0)]
    return mv


def whip(r):
    """For whoever is behind it (LIGHT): its valves splay out flat like blades and the ring of them winds back round
    it, then whips round the other way, the back valves sweeping behind it."""
    gather = _pose(yaw=-4, at=(0, 1, 0), open=20, curl=-4, ring=-20, look=(0, 4, 0), stalk=(0, 8))
    peak = _pose(yaw=-8, at=(0, 2, 0), open=62, curl=-22, ring=-55, part=3, look=(0, 8, 0), stalk=(0, 16),
                 orbit=(0, -14, 0))
    hit = _pose(yaw=6, at=(0, 0.5, 0), open=58, curl=-24, ring=30, part=2, look=(0, -6, 0), stalk=(0, -14),
                orbit=(0, 12, 0))
    held = _pose(yaw=8, at=(0, 0, 0), open=54, curl=-24, ring=41, part=2, look=(0, -8, 0), stalk=(0, -18),
                 orbit=(0, 16, 0))
    sink = _pose(yaw=7, at=(0, 0, 0), open=46, curl=-20, ring=35, part=2, look=(0, -7, 0), stalk=(0, -15),
                 orbit=(0, 14, 0))
    back = _pose(yaw=3, open=16, curl=-6, ring=12, look=(0, -3, 0), stalk=(0, -6), orbit=(0, 6, 0))
    return Move("whip", "LIGHT", 10, 4, 14, through=sink, track=0.0, rng=(0, 6.0), kinds=("behind",), damage=1.0, knock=1.2, plant=(), hit=_tips("bl", "br"),
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


LANCE_FROM = (0.0, 2.8, 0.4)  # where the Signal Lance leaves it: the door of light in its open seed (blocks: x, up, forward)


def lance(r):
    """The Signal Lance (HEAVY): its two front valves part wide and its back ones clench, its seed opening and the
    door in it swelling; it leans back and draws in, then tips its top at you and the lance leaves the door."""
    fr_ = lambda o, c, s: {"fl": dict(open=o, curl=c, sweep=-s, splay=0.4 * s), "fr": dict(open=o, curl=c, sweep=s, splay=-0.4 * s)}
    bk = lambda o, c: {"bl": dict(open=o, curl=c), "br": dict(open=o, curl=c)}
    l0 = _pose(pitch=-4, at=(0, 1, 1), v={**fr_(24, -6, 8), **bk(-6, 4)}, part=8, look=(-4, 0, 0), stalk=(-4, 0), orbit=(-2, 0, 0))
    l1 = _pose(pitch=-8, at=(0, 2, 2), v={**fr_(48, -14, 18), **bk(-10, 6)}, part=26, look=(-8, 0, 0), stalk=(-8, 0), orbit=(-4, 0, 0))
    peak = _pose(pitch=-10, at=(0, 2.5, 2.5), v={**fr_(54, -18, 22), **bk(-12, 7)}, part=32, look=(-10, 0, 0), stalk=(-10, 0), orbit=(-6, 0, 0))
    aim = _pose(pitch=6, at=(0, 2, 0.5), v={**fr_(46, -14, 18), **bk(-8, 5)}, part=36, look=(5, 0, 0), stalk=(6, 0), orbit=(2, 0, 0))
    hold = _pose(pitch=7, at=(0, 1.5, 2.5), v={**fr_(50, -16, 20), **bk(-9, 5)}, part=38, look=(6, 0, 0), stalk=(10, 0), orbit=(3, 0, 0))
    back = _pose(pitch=1, at=(0, 0.5, 0.5), v={**fr_(10, -2, 4), **bk(-2, 1)}, part=6, look=(1, 0, 0), stalk=(2, 0))
    ease = _pose(pitch=4, at=(0, 1.2, 1.8), v={**fr_(36, -11, 14), **bk(-6, 3)}, part=28, look=(4, 0, 0), stalk=(7, 0), orbit=(2, 0, 0))
    mv = Move("lance", "HEAVY", 18, 10, 20, through=ease, title="Signal Lance", hold=8, track=4.0, ability="Signal Lance", origin=LANCE_FROM, rng=(4, 20),
              kinds=("ranged",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, l0), (0.7, l1), (1.0, peak)], "active": [(0.4, aim), (1.0, hold)], "recover": [(0.5, back)]}, lag=_lag())
    mv._door = [(0.0, 1.0), ("windup", 0.7, 1.12), ("windup", 1.0, 1.18), ("active", 0.4, 1.28), ("active", 1.0, 1.22), ("recover", 0.5, 1.06), ("recover", 1.0, 1.0)]
    return mv


def marks(r):
    """The Signal Marks: it sinks and shuts tight; then it opens to the sky like a flower, its valves bent right back
    and its seed open and looking up, and four times it clenches and flares, throwing a light up each time."""
    c0 = _pose(at=(0, -1.2, 0), open=-8, curl=6, look=(4, 0, 0), stalk=(6, 4))
    low = _pose(pitch=2, at=(0, -2.2, 0), open=-14, curl=12, look=(7, 0, 0), stalk=(10, 8), orbit=(2, 0, 0))
    act = [(0.15, _pose(at=(0, 3, 0), open=50, curl=-30, part=30, look=(-22, 0, 0), stalk=(-6, 0), orbit=(-4, 0, 0)))]
    for k in range(4):  # four throws: a clench and a flare, each a little different
        f = 0.15 + 0.85 * (k + 1) / 4
        sw = 1.5 * (k % 2 * 2 - 1)
        act.append((f - 0.106, _pose(roll=sw, at=(0, 4.5 + 0.3 * k, 0), open=20 + k, curl=-6, part=22, look=(-16, sw * 2, 0), stalk=(4, -sw * 3), orbit=(2, sw * 4, 0))))
        act.append((f, _pose(roll=-sw * 0.6, at=(0, 3.4 + 0.3 * k, 0), open=52 - 2 * k, curl=-30 + k, part=32 - k, look=(-24 + k, -sw, 0), stalk=(-6, sw * 2),
                             orbit=(-4, -sw * 3, 0))))
    back = _pose(at=(0, 1, 0), open=8, curl=-2, part=6, look=(-4, 0, 0), stalk=(2, 0))
    ease = _pose(at=(0, 2.8, 0), open=34, curl=-17, part=22, look=(-15, 0, 0), stalk=(-3, 0), orbit=(-2, 0, 0))
    mv = Move("marks", "NONE", 14, 24, 16, through=ease, title="Signal Marks", track=0.0, ability="Signal Marks", origin=(0, 3.0, 0), rng=(0, 22),
              kinds=("area", "ranged"), damage=0, knock=0, plant=(), stages={"windup": [(0.3, c0), (1.0, low)], "active": act, "recover": [(0.5, back)]},
              lag=_lag())
    mv._door = [(0.0, 1.0), ("windup", 1.0, 0.9)] + [("active", 0.15 + 0.85 * (k + 1) / 4 - 0.106 * j, v) for k in range(4) for j, v in ((1, 1.1), (0, 1.3))] + \
        [("recover", 0.5, 1.1), ("recover", 1.0, 1.0)]
    return mv


def motes_(r):
    """The Motes: it shakes like a seed head in the wind, its valves rattling and its stalk swinging; then its seed
    opens a crack, the light in it brightening, and the small seeds round it fly out and go; they grow back slowly."""
    w = []
    for k in range(1, 7):
        f = k / 6
        tr = (1 if k % 2 else -1) * f
        w.append((0.3 * k / 2 if k <= 2 else f, _pose(pitch=1.5 * tr, roll=2.0 * tr, at=(0, 1.5 * f, 0), v={vn: dict(open=(5 + 4 * f) * tr * (1 if i % 2 else -1), curl=-2 * tr)
                                                                                                     for i, vn in enumerate(RING)},
                                                     part=4 * f, look=(-4 * f, 3 * tr, 0), stalk=(6 * tr, -8 * tr), orbit=(3 * tr, 6 * tr, 0))))
    act = []
    for k, (f, d, up) in enumerate(((0.2, 4, 2), (0.4, 9, 4.5), (0.6, 13, 6.5), (0.8, 16, 8), (1.0, 18, 9))):  # they go out while it shudders on
        tr = 1 if k % 2 else -1
        act.append((f, _pose(pitch=1.5 * tr, roll=2.5 * tr, at=(0.5 * tr, 2.2 + 0.4 * (k % 2), 0),
                             v={vn: dict(open=(14 - k) + 7 * tr * (1 if i % 2 else -1), curl=-4 - 3 * tr * (1 if i % 2 else -1)) for i, vn in enumerate(RING)},
                             part=13 + 2 * tr, look=(-8 + 2 * tr, 4 * tr, 0), stalk=(-4 + 6 * tr, -8 * tr), orbit=(-4, 6 + 4 * k, 0),
                             motes=[_out(j, d, up) for j in range(len(MOTES))])))
    back = _pose(at=(0, 0.8, 0), open=4, part=4, look=(-2, 0, 0), stalk=(-1, 0), orbit=(0, 10, 0))
    ease = _pose(at=(0, 1.5, 0), open=7, curl=-2, part=8, look=(-4, 0, 0), stalk=(-2, 0), orbit=(-2, 18, 0), motes=[_out(j, 20, 10) for j in range(len(MOTES))])
    mv = Move("motes", "NONE", 12, 26, 14, through=ease, title="Motes", track=0.0, ability="Motes", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
              plant=(), stages={"windup": w, "active": act, "recover": [(0.5, back)]}, lag=_lag())
    mv._door = [(0.0, 1.0), ("windup", 1.0, 1.05), ("active", 0.3, 1.25), ("active", 1.0, 1.2), ("recover", 1.0, 1.0)]
    mv._motes = [(0.0, 1.0), ("active", 0.3, 1.0), ("active", 1.0, 0.02), ("recover", 0.3, 0.02), ("recover", 1.0, 1.0)]
    return mv


def door(r):
    """The Door Light (PERILOUS, the signature): it shuts tight, every valve closing over its seed, and sinks and
    trembles harder and harder, the crack blazing; then it flings every valve back and the seed opens all the way, and
    the door of light inside stands open and the light comes out. It folds back slowly."""
    w = [(0.15, _pose(at=(0, -0.8, 0), open=-9, curl=8, look=(3, 0, 0), stalk=(2, -2), orbit=(1, 2, 0))),
         (0.3, _pose(at=(0, -1.6, 0), open=-17, curl=15, look=(6, 0, 0), stalk=(3, -3), orbit=(2, 3, 0)))]
    for k in range(4):  # it trembles harder and harder, its valves clenching in beats that grow, rising at the last
        f = 0.45 + 0.15 * k
        e = 0.5 + 0.5 * (k + 1) / 4
        tr = (1 if k % 2 else -1) * e
        w.append((f, _pose(pitch=2.5 * tr, yaw=4.0 * tr, roll=3.0 * tr, at=(0.8 * tr, -1.6 + 0.9 * k, 0), v={vn: dict(open=8 * tr * (1 if i % 2 else -1), curl=-4 * tr * (1 if i % 2 else -1))
                                                                                                       for i, vn in enumerate(RING)},
                           open=-17 - 0.8 * k, curl=15 + 0.6 * k, look=(6 + 3 * tr, 6 * tr, 2 * tr), stalk=(8 * tr, -10 * tr), orbit=(3 * tr, 8 * tr, 0))))
    w.append((1.0, _pose(at=(0, 1.8, 0), open=-21, curl=18, look=(8, 0, 0), stalk=(-2, 0), orbit=(-1, 0, 0))))
    act = [(0.2, _pose(pitch=-6, at=(0, 5, 0), open=90, curl=-40, part=68, look=(-10, 0, 0), stalk=(-12, 0), orbit=(-6, 0, 0),
                       motes=[_out(j, 6, 2) for j in range(len(MOTES))])),
           (0.55, _pose(pitch=-5, roll=1.0, at=(0, 5.5, 0), open=94, curl=-44, part=72, look=(-12, 1, 0), stalk=(-14, 2), orbit=(-6, 4, 0),
                        motes=[_out(j, 7, 2.5) for j in range(len(MOTES))])),
           (1.0, _pose(pitch=-4, at=(0, 5, 0), open=86, curl=-38, part=64, look=(-9, 0, 0), stalk=(-10, 0), orbit=(-5, 2, 0),
                       motes=[_out(j, 5, 2) for j in range(len(MOTES))]))]
    rec = [(0.35, _pose(pitch=-2, at=(0, 3, 0), open=54, curl=-22, part=38, look=(-5, 0, 0), stalk=(-6, 0), orbit=(-3, 0, 0),
                        motes=[_out(j, 2, 1) for j in range(len(MOTES))])),
           (0.7, _pose(at=(0, 1, 0), open=18, curl=-6, part=10, look=(-1, 0, 0), stalk=(-2, 0)))]
    ease = _pose(pitch=-3, at=(0, 4.2, 0), open=72, curl=-30, part=52, look=(-7, 0, 0), stalk=(-8, 0), orbit=(-4, 1, 0), motes=[_out(j, 4, 1.5) for j in range(len(MOTES))])
    mv = Move("door", "PERILOUS", 30, 12, 30, through=ease, title="Door Light", signature=True, track=0.0, ability="Door Light", origin=(0, 2.8, 0.4),
              rng=(0, 13), kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": w, "active": act, "recover": rec}, lag=_lag())
    mv._door = [(0.0, 1.0), ("windup", 0.5, 1.05), ("windup", 1.0, 1.2), ("active", 0.2, 1.42), ("active", 0.55, 1.5), ("active", 1.0, 1.38), ("recover", 0.35, 1.18),
                ("recover", 0.7, 1.05), ("recover", 1.0, 1.0)]
    return mv


def wake(r):
    """Its intro: shut on the floor, light leaking from its seams, it shudders; its valves loosen and it lifts off the
    floor, then unfolds on one great pulse, its seed parting and the door in it shining, and hangs open before you."""
    s = _sag()
    w = [(0.3, _pose(pitch=4, roll=-2, at=(0, -6.5, 0), open=-9, curl=7, v={"fl": dict(open=5, splay=6, curl=-5)}, part=2, look=(10, 0, 0), stalk=(0, 80),
                     orbit=(0, -4, 0), motes=[(0.0, -8.0 - 2 * j, 0.0) for j in range(len(MOTES))])),
         (0.7, _pose(pitch=2, at=(0, -3.5, 0), open=-5, curl=4, part=4, look=(6, 0, 0), stalk=(-4, 54), orbit=(0, -2, 0),
                     motes=[(0.0, -5.0 - j, 0.0) for j in range(len(MOTES))])),
         (1.0, _pose(pitch=1, at=(0, -2, 0), open=2, curl=-1, part=8, look=(2, 0, 0), stalk=(-6, 10), motes=[(0.0, -2.0, 0.0)] * len(MOTES)))]
    act = [(0.3, _pose(pitch=-6, at=(0, 4, 0), open=34, curl=-16, part=22, look=(-14, 0, 0), stalk=(-12, 0), orbit=(-4, 6, 0))),
           (0.6, _pose(pitch=-5, yaw=2, at=(0, 5, 0), open=40, curl=-20, part=26, look=(-12, 3, 0), stalk=(-10, 4), orbit=(-4, 12, 0))),
           (1.0, _pose(pitch=-2, at=(0, 3, 0), open=18, curl=-8, part=10, look=(-4, 0, 0), stalk=(-4, 0), orbit=(-2, 6, 0)))]
    ease = _pose(pitch=-1, at=(0, 2.2, 0), open=11, curl=-5, part=7, look=(-3, 0, 0), stalk=(-3, 0), orbit=(-1, 4, 0))
    mv = Move("wake", "NONE", 14, 30, 14, through=ease, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": w, "active": act, "recover": [(0.5, _pose(at=(0, 1, 0), open=5, part=3, orbit=(0, 2, 0)))]}, lag=_lag())
    mv._first = s  # it starts where the kneel left it, shut on the floor
    mv._door = [(0.0, 0.85), ("windup", 0.3, 0.95), ("windup", 1.0, 1.1), ("active", 0.3, 1.2), ("active", 0.6, 1.25), ("recover", 1.0, 1.0)]
    return mv


def moves(r):
    return [swipe("l"), swipe("r", "swipe_b"), clasp(r), whip(r), lance(r), marks(r), motes_(r), door(r), wake(r)]


def _track(mv, pts):
    """A move's scale keys [(stage, fraction, scale)] in its stages' time (a bare (0.0, scale) is its start)."""
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    span = {"windup": W, "active": A, "recover": R_}
    return sorted((0.0, k[1]) if len(k) == 2 else (t0[k[0]] + k[1] * span[k[0]], k[2]) for k in pts)


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


def _drift(r, clip, ramp=0.25):
    """Under a move, after it is made: its hooked tips stir, its stalk swings and the small seeds bob at their own
    pace, faded in and out at the ends so it hands back to the idle exactly as it was."""
    c = r.clips[clip]
    L, n = c.length, int(round(c.length / core.TICK))

    def env(t):
        e = max(0.0, min(1.0, t / ramp, (L - t) / ramp))
        return e * e * (3 - 2 * e)

    def add(bone, kind, f):
        old = c.tracks.get((bone, kind))
        keys = []
        for k in range(n + 1):
            t = min(L, k * core.TICK)
            base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
            d = f(t)
            keys.append((t, tuple(base[i] + env(t) * d[i] for i in range(3)), "cat"))
        c.tracks[(bone, kind)] = keys
    for i, nm in enumerate(RING):
        add(f"valve_{nm}3", "rot", lambda t, i=i: (3.0 * math.sin(2 * math.pi * 0.9 * t - 1.2 * i), 0.0, 0.0))
    add("stalk1", "rot", lambda t: (4.0 * math.sin(2 * math.pi * 0.7 * t), 0.0, 3.0 * math.sin(2 * math.pi * 0.5 * t + 1.0)))
    for k in range(len(MOTES)):
        add(f"mote{k}", "pos", lambda t, k=k: (0.0, 1.2 * math.sin(2 * math.pi * 0.6 * t + 2.1 * k), 0.0))


FLOAT = ("fibre",)  # threads that lie on the floor rather than hold it up


def _on_floor(r, clip, margin=0.3):
    """Nothing of it goes into the floor: where a pose would put a part under it, the body is lifted by as much, the
    lift spread a little either side so it rises and settles rather than jumps."""
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
        _staged(r, m)
        name = core.clip_name(m)
        if getattr(m, "_door", None):
            _scale(r, name, "door", _track(m, m._door))
        if getattr(m, "_motes", None):
            for k in range(len(MOTES)):
                _scale(r, name, f"mote{k}", _track(m, m._motes), "lin")
        _drift(r, name)
    for name in list(r.clips):
        _on_floor(r, name)
    return mv


# Its sounds (SPEC §35.8): the creak of a husk, the hum and chime of the Seed's light; chosen by ear on paper, not yet
# heard in game.
SOUNDS = {
    "tell_light": [("block.big_dripleaf.tilt_down", 1.0, 0.6), ("block.amethyst_block.chime", 0.6, 0.8)],
    "tell_heavy": [("block.beacon.power_select", 0.9, 0.8), ("block.big_dripleaf.tilt_up", 1.0, 0.5)],
    "tell_perilous": [("block.beacon.activate", 0.8, 1.0), ("block.respawn_anchor.charge", 0.7, 1.0), ("block.amethyst_block.resonate", 0.6, 1.0)],
    "swing_light": [("entity.player.attack.sweep", 0.8, 0.6), ("block.big_dripleaf.tilt_down", 1.2, 0.5)],
    "swing_heavy": [("entity.player.attack.sweep", 0.6, 0.8), ("block.wood.break", 0.6, 0.7)],
    "cast": [("block.beacon.power_select", 1.3, 0.9), ("block.amethyst_block.resonate", 1.1, 0.8)],
    "impact": [("block.amethyst_cluster.break", 0.7, 0.9), ("block.wood.hit", 0.6, 0.8)],
    "roar": [("block.beacon.activate", 0.6, 1.1), ("block.end_portal_frame.fill", 0.7, 0.9)],
    "step": [("block.big_dripleaf.tilt_up", 1.4, 0.3)],
}
MOVE_SOUNDS = {"lance": {"swing": "cast"}, "marks": {"swing": "cast", "impact": ""}, "motes": {"swing": "cast", "impact": ""},
               "door": {"swing": "roar", "impact": ""}, "wake": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "swipe", "weight": 3, "when": "near", "then": [["swipe_b"], ["swipe_b", "clasp"]]},
            {"move": "lance", "weight": 3},
            {"move": "marks", "weight": 2},
            {"move": "clasp", "weight": 3, "when": "far", "then": [["swipe"]]},
            {"move": "whip", "weight": 3, "when": "behind"},
        ]},
        {"transition": "motes", "pool": [
            {"move": "swipe", "weight": 3, "when": "near", "then": [["swipe_b"], ["swipe_b", "clasp"]]},
            {"move": "door", "weight": 2, "then": [["swipe"]]},
            {"move": "motes", "weight": 1},
            {"move": "lance", "weight": 2, "when": "far"},
            {"move": "marks", "weight": 2},
            {"move": "clasp", "weight": 3, "when": "far", "then": [["swipe"]]},
            {"move": "whip", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.4, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("body", (0, 0, 0)), ("door", (0, 0, -3)), ("valve_fl3", (0, -5, 0)), ("valve_fl1", (0, -11, 0)), ("fibre0_0", (0, 3, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
