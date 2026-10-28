"""The Bell Warden (SPEC §35.11, docs/bosses/ROLLOUT.md): the Bell gate's Lord, a Construct. The Bell That Walks, Euan's
pick from its silhouette round: "The bell above you is not a bell."

A great bronze bell, 2.75 blocks across the lip, its mouth hanging 1.8 blocks up on four long crab legs that unfold from
under the rim: up and out to knees above its shoulder, then down to points on the floor, a wide stance you can walk
into. Its clapper hangs out of the mouth like a heavy tongue and swings with every step, and inside the mouth, in the
dark, there's a face: a pale gold mask that turns to watch you. On its crown it still carries the crypt-stone yoke it hung
from, with a broken chain and a cut rope trailing from it. Its light story is that ghost gold: the face, the inscription
round its waist, and the crack that runs up its side.

Moves (SPEC §35.11): it rears a front leg and stabs down with it, then the other (LIGHT); Clapper Strike (it rocks back,
then pitches forward and lashes its tongue out onto the floor: HEAVY); Toll Wave (it rises on its legs and slams its rim
onto the floor; the wave rolls out, and only under its rim are you safe: HEAVY); Echo Bolts (it turns its mouth to you
and hums three times); a back leg raking whoever is behind it; The Statues Move (it rings itself, swinging from side
to side); Bell Rush (it crouches and scuttles at you); Shattered Bronze (it shudders until shards fly off it); and the
Grand Peal (the signature: up on the points of its legs it swings itself ringing, wider each time, then slams down).
It waits sitting on its rim like any bell with its legs folded up its sides, throws them out and rises on them when you
come in, and dies with its legs giving way under it: it drops onto its rim, rattles round on it the way a dropped bell
does, and comes to rest with its legs flat out round it.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "bell_warden"
SCALE = 1.0  # the hitbox on the def's BELL model: 1.4 x 3.0 blocks; the bell is modelled at true size over it

# The Bell That Walks (docs/bosses/ROLLOUT.md, Euan's pick A, 2026-10-28). The bell's lip 1.8 blocks up (a player can
# step in under it, which is where the Toll Wave can't reach), its crown at 4.0 and the yoke on it to 4.5; the legs
# from just inside the rim up to knees at 3.6 blocks and down to points 2.9 blocks out on the diagonals. Value story:
# the ghost gold brightest (the face, the inscription, the crack), then the bronze (lit high on the shoulder and the
# sound bow, dark in the waist), then the green verdigris running down it from every ledge, then the crypt stone and
# the old rope.
LIP = 29.0  # the lip's height, pixels
AT = 44.0  # the body's pivot: the middle of the bell
BOW = -8.0  # at rest the bell leans back a little, its mouth turned toward what it faces
# the bell from the lip up: (width, height, name)
TIERS = ((44, 3, "bow"), (40, 3, "bow1"), (36, 3, "skirt"), (33, 2, "band0"), (31, 9, "waist"), (33, 2, "band1"),
         (30, 5, "upper"), (28, 3, "shoulder"), (22, 3, "crown"), (14, 2, "crown1"))
LEGS = ((1, -1), (-1, -1), (1, 1), (-1, 1))  # (x, z) of each leg: front left, front right, back left, back right
HIP = (14.0, 31.0)  # each hip: out on the diagonal, height
FOOT = 40.0  # each point at rest, out on the diagonal (2.9 blocks out from its middle along the diagonal... x and z)
THIGH, SHIN = 32, 57
REST_FOOT = [(sx * FOOT, 0.5, sz * FOOT) for sx, sz in LEGS]
POLE = [(sx * 0.6, -1.0, sz * 0.6) for sx, sz in LEGS]  # the knees ride up and out


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed by height: x, how far up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    """A cube centred on (cx, cz), from y0 up to y0 + h over its bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def _ring(bone, name, y0, w, h, t):
    """A square ring of four walls t thick (the open mouth), from y0 up."""
    _box(bone, name + "_f", 0, y0, -(w - t) / 2, w, h, t)
    _box(bone, name + "_b", 0, y0, (w - t) / 2, w, h, t)
    _box(bone, name + "_l", (w - t) / 2, y0, 0, t, h, w - 2 * t)
    _box(bone, name + "_r", -(w - t) / 2, y0, 0, t, h, w - 2 * t)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, AT, 0, (BOW, 0, 0))
    y = LIP - AT
    for k, (w, h, nm) in enumerate(TIERS):
        if k == 0:
            _ring(bd, nm, y, w, h, 4)
        elif k == 1:
            _ring(bd, nm, y, w, h, 3)
        else:
            _box(bd, nm, 0, y, 0, w, h, w)
        y += h
    # the crown: the canon it hung by, the crypt-stone yoke still on it, iron straps over both
    _box(bd, "canon", 0, y, 0, 8, 3, 8)
    _box(bd, "yoke", 0, y + 3, 0, 30, 6, 8)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(bd, "strap_" + nm, s * 5, y - 1, 0, 2, 10, 10)
    top = y + 3
    # a broken chain off one end of the yoke, a cut rope off the other
    ch = parts.chain(bd, "chain", (13, -(top + 1), 0), 3, link=(2, 5, 2))
    rp = bd
    for k in range(3):
        rp = rp.bone(f"rope{k}", (-13, -(top + 1), 0) if k == 0 else (0, 6, 0))
        rp.cube(f"rope{k}", -1, 0, -1, 2, 6 if k < 2 else 4, 2)
    # the face in its mouth: a pale mask hanging from the throat, turned to watch you; a jaw that drops
    fc = _bone(bd, "face", 0, (LIP + 2) - AT, -10)
    _box(fc, "mask", 0, -10, 0, 12, 10, 3)
    _box(fc, "brow", 0, -3, -0.5, 14, 2, 4)
    _box(fc, "nose", 0, -7, -2, 2, 3, 1)
    _box(fc, "stalk", 0, 0, 1, 4, 2, 3)
    jw = _bone(fc, "jaw", 0, -9, 0)
    _box(jw, "jaw", 0, -4, 0, 9, 4, 3)
    # the clapper: hung from the crown inside, out of the mouth like a heavy tongue; two joints, so it whips
    cl = _bone(bd, "clapper", 0, 56 - AT, 0)
    _box(cl, "shaft", 0, -16, 0, 3, 16, 3)
    cb = _bone(cl, "clapper_b", 0, -16, 0)
    _box(cb, "shaft_b", 0, -12, 0, 3, 12, 3)
    _box(cb, "ball", 0, -21, 0, 9, 9, 9)
    _box(cb, "flight", 0, -24, 0, 5, 3, 5)
    # four crab legs from inside the rim: a bronze knuckle, the thigh up and out, a crypt-stone knee with a spike, the
    # long shin down to its point
    for k, (sx, sz) in enumerate(LEGS):
        hx, hy = HIP
        _box(bd, f"hipcap{k}", sx * hx, hy - 2 - AT, sz * hx, 7, 5, 7)
        hp = _bone(bd, f"hip{k}", sx * hx, hy - AT, sz * hx)
        th = hp.bone(f"thigh{k}", (0, 0, 0))
        th.cube(f"thigh{k}", -3, 0, -3, 6, THIGH, 6)
        th.cube(f"thighp{k}", -3.5, 5, -3.5, 7, 12, 7)
        sh = th.bone(f"shin{k}", (0, THIGH, 0))
        sh.cube(f"knee{k}", -4, -4, -4, 8, 8, 8)
        sh.cube(f"spike{k}", -1.5, -12, -1.5, 3, 8, 3)
        sh.cube(f"shin{k}", -3, 4, -3, 6, 18, 6)
        sh.cube(f"shinb{k}", -2.5, 22, -2.5, 5, 18, 5)
        sh.cube(f"shinc{k}", -2, 40, -2, 4, 14, 4)
        sh.cube(f"fin{k}", -0.5, 8, 3, 1, 16, 3)
        ft = sh.bone(f"foot{k}", (0, SHIN, 0))
        ft.cube(f"toe{k}", -1, -3, -1, 2, 3, 2)
    for k in range(4):
        ik.rest_limb(r, f"thigh{k}", f"shin{k}", f"foot{k}", REST_FOOT[k], POLE[k])
    r.look = [("face", 0.6, 0.5)]
    r.shadow = 2.2
    r.size = 1.0
    # secondary parts (SPEC §35.10): the clapper swings in its mouth, the chain and the rope trail off the yoke
    r.springs = [("clapper", 0.55, 1.3, 0.12), ("clapper_b", 0.45, 1.7, 0.16), ("chain0", 0.6, 1.6, 0.18), ("chain1", 0.5, 2.0, 0.2),
                 ("chain2", 0.5, 2.2, 0.2), ("rope0", 0.5, 1.5, 0.25), ("rope1", 0.5, 1.9, 0.25), ("rope2", 0.5, 2.2, 0.25)]
    r.guard = [f"toe{k}" for k in range(4)] + ["ball", "flight"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

def paint(r, P_):
    """Five materials in a value order: the ghost gold brightest (the face, the inscription round its waist, the crack
    up its side), then the bronze (lit high on the shoulder and along the sound bow, dark in the waist and the mouth),
    then the green verdigris that has run down it from every ledge, then the crypt stone of the yoke and the knees, then
    the old rope."""
    bronze = Ramp("#1a0f06", "#2b190b", "#3f2610", "#563416", "#6f441c", "#8a5722", "#a66c2a", "#c08634", "#d8a446", "#ecc264", "#f8de94")
    dark = Ramp("#0e0b09", "#17120e", "#211a14", "#2b221a", "#362b20", "#433527", "#52412f", "#645038", "#7a6244")  # the legs: blackened bronze
    verd = Ramp("#14281f", "#1c382c", "#26483a", "#305a48", "#3e6e58", "#4e846a", "#62987c", "#7aae92")
    crypt = Ramp("#121014", "#1a171c", "#231f26", "#2d2830", "#38323b", "#443d47", "#524a55")
    rope = Ramp("#2a1e12", "#3a2c1c", "#4c3a26", "#5e4830", "#72583c", "#8a6e4c")
    iron = Ramp("#0e0e10", "#18181c", "#24242a", "#323238", "#424248")
    ghost = ("#a88a3a", "#d0b45a", "#ecd884", "#fff0b4", "#fffae6")
    of = lambda *ps: parts.cubes_of(r, *ps)
    # hammered bronze: a little light and dark in the metal, a dark seam at the foot of each face
    def runs(c, depth):
        def f(face, i, j):
            if face.name not in SIDES:
                return 0.25 if (i * 7 + j * 3) % 11 == 0 else 0
            return (-0.6 if j == face.h - 1 else 0) + (0.5 if R._h(c, face.name, i, j) % 7 == 0 else -0.3 if R._h(c, face.name, i, j) % 7 == 1 else 0)
        return f
    # the bell: bronze lit high on the shoulder and along the bow, darker into the waist
    tone = {"bow": 7.0, "bow1": 6.4, "skirt": 5.8, "band0": 6.8, "waist": 4.9, "band1": 6.8, "upper": 5.5, "shoulder": 6.6, "crown": 6.2,
            "crown1": 6.0, "canon": 5.0}
    for c in [c for c in P_.cubes if c.split("_")[0] in tone]:
        ledge = c.startswith(("band", "shoulder", "bow_", "crown1"))
        P_.paint(c, bronze, tone[c.split("_")[0]], runs(c, 0), grad=0.5, rim=0.8 if ledge else 0.2, ao=0.35, noise=0.35, nscale=6)
    # the verdigris has run down off each ledge onto the face below it: long green streaks on a few columns, palest
    # where they leave the ledge
    for c in ("skirt", "waist", "upper", "crown", "bow1_f", "bow1_b", "bow1_l", "bow1_r"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                h = R._h(c, fname, i)
                if h % 5:
                    continue
                for j in range(min(f.h, 2 + h % 7)):
                    f.set(i, j, verd.at(6 - min(5, j)))
                    if i + 1 < f.w and j < 2:
                        f.set(i + 1, j, verd.at(4))
    # the mouth: the walls' insides and the throat dark bronze going to black, a faint gold where the face glows
    for c, inner in (("bow_f", "south"), ("bow_b", "north"), ("bow_l", "west"), ("bow_r", "east"),
                     ("bow1_f", "south"), ("bow1_b", "north"), ("bow1_l", "west"), ("bow1_r", "east")):
        f = P_.face(c, inner)
        for i, j in f.each():
            f.set(i, j, bronze.at(2 - (1 if j < f.h // 2 else 0)))
    for c in of("bow_", "bow1_"):
        for fname in ("bottom",):
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, bronze.at(1 + (R._h(c, i, j) % 2)))
    f = P_.face("skirt", "bottom")
    cx, cz = f.w / 2, f.h / 2
    for i, j in f.each():
        d = math.hypot(i + 0.5 - cx, j + 0.5 - cz) / cx
        f.set(i, j, bronze.at(0) if d < 0.75 else bronze.at(1))
    g = P_.face("skirt", "bottom", glow=True)
    for i, j in g.each():
        if abs(i + 0.5 - cx) < 7 and j + 0.5 < cz - 2:
            g.set(i, j, hexc(ghost[0], 70))
    # the inscription round its waist, in raised letters that glow faintly gold
    for fname in SIDES:
        f, g = P_.face("waist", fname), P_.face("waist", fname, glow=True)
        for i in range(1, f.w - 1):
            for j in (3, 4, 5):
                h = R._h("glyph", fname, i, j)
                if (i % 4 != 0) and h % 3 != 0 and not (j == 4 and h % 2):
                    f.set(i, j, bronze.at(7))
                    g.set(i, j, hexc(ghost[2], 120))
    # the crack up its side: from the lip on its front left, through the skirt into the waist
    for c, n in (("bow_f", 1), ("bow1_f", 1), ("skirt", 1), ("waist", 1), ("bow_l", 1)):
        cracks(P_, c, ("north",) if not c.endswith("_l") else ("west",), bronze.at(0), ghost[1:4], n=n, seed=c, alpha=210)
    # the crown's yoke and the knees: crypt stone, worn; the straps iron; the rope frayed at its cut
    for c in ["yoke"] + of("knee"):
        P_.paint(c, crypt, 3.4, R.p_blotch(c, scale=3, amount=0.9), grad=0.8, rim=0.9, ao=0.8, noise=0.6, nscale=4)
    for c in of("strap_", "chain"):
        P_.paint(c, iron, 2.6, None, rim=1.0, noise=0.3)
    twist = lambda f, i, j: (0.8 if (i + j) % 3 == 0 else -0.5 if (i + j) % 3 == 1 else 0)
    for c in of("rope"):
        P_.paint(c, rope, 3.0, twist, grad=0.6, rim=0.4, noise=0.4)
    # the legs: darker bronze than the bell, green at every joint, the points worn bright on the floor
    for c in of("thigh", "shin", "hipcap", "fin", "spike"):
        base = 5.0 if c.startswith(("thighp", "hipcap", "spike")) else 4.2
        P_.paint(c, dark, base, runs(c, 4), grad=1.2, rim=1.0, ao=0.9, noise=0.5, nscale=4)
    for c in of("hipcap", "thighp", "knee"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if j >= f.h - 2 and R._h(c, fname, i) % 2:
                    f.set(i, j, verd.at(3 + R._h(c, i, j) % 2))
    for c in of("toe"):
        P_.paint(c, bronze, 8.5, None, rim=0.5)
    # the clapper: dark iron-bronze, its ball polished where it strikes
    for c in ("shaft", "shaft_b"):
        P_.paint(c, bronze, 3.0, None, rim=0.6, noise=0.3)
    P_.paint("ball", bronze, 5.0, lambda f, i, j: (1.6 if f.name in SIDES and 2 <= j <= 5 else 0), grad=1.2, rim=1.0, noise=0.4)
    P_.paint("flight", bronze, 4.0, None)
    # the face: a pale gold mask, glowing from within, the eyes and the open mouth deep and bright
    gm = Ramp("#6e5624", "#8e7034", "#b0904a", "#cdb066", "#e4cc86", "#f4e2aa")
    for c in ("mask", "brow", "nose", "jaw", "stalk"):
        P_.paint(c, gm, 3.4 if c != "stalk" else 1.4, None, grad=0.8, rim=0.8, ao=0.9, noise=0.3)
        if c in ("mask", "brow", "nose", "jaw"):
            for fname in ("north", "west", "east", "bottom"):
                g = P_.face(c, fname, glow=True)
                for i, j in g.each():
                    g.set(i, j, hexc(ghost[1], 90))
    f = P_.face("mask", "north")
    for i in range(3, f.w - 3):  # the mouth hangs open, dark, the light deep inside it
        for j in (f.h - 3, f.h - 2, f.h - 1):
            f.set(i, j, bronze.at(0))
    g = P_.face("mask", "north", glow=True)
    for i in range(4, f.w - 4):
        g.set(i, f.h - 2, hexc(ghost[2], 230))
    for i in (5, 6):  # the nose's shadow
        f.set(i, 5, gm.at(1))
    P_.paint("jaw", gm, 3.0)
    fj, gj = P_.face("jaw", "north"), P_.face("jaw", "north", glow=True)
    for i in range(2, fj.w - 2):
        fj.set(i, 0, bronze.at(0))
        gj.set(i, 0, hexc(ghost[2], 200))
    with P_.eyes():
        f, g = P_.face("mask", "north"), P_.face("mask", "north", glow=True)
        for i in (1, 2, 3, f.w - 4, f.w - 3, f.w - 2):  # deep sockets, the light burning in each
            for j in (2, 3, 4):
                f.set(i, j, bronze.at(0))
                if i in (2, 3, f.w - 4, f.w - 3) and j in (2, 3):
                    g.set(i, j, hexc(ghost[4] if j == 2 else ghost[3], 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the body set, then each leg reaching its point to where it stands, the clapper and the rope hung plumb

def _swing_dir(fwd, side):
    """A hanging part's way down, swung fwd degrees toward the front and side degrees toward its left."""
    f, s = fwd * DEG, side * DEG
    return (math.cos(f) * math.sin(s), math.cos(f) * math.cos(s), -math.sin(f))


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), feet=None, poles=None, tongue=(0.0, 0.0), whip=(0.0, 0.0),
          retract=0.0, face=(0, 0, 0), jaw=0.0, rope=(0.0, 0.0), extra=None):
    """lift (+ up), pitch (+ its top tipping forward), yaw (+ to its left), roll (+ its top to its left) and shift
    (x, z) move the bell; feet {leg: (x, height, z)} are where the points stand (the rest of them where they stand at
    rest); tongue (forward, left) swings the clapper from plumb and whip bends its lower joint; retract draws the
    clapper and the face up into the mouth (pixels); jaw opens the face's mouth."""
    p = {"body": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])}}
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    poles = poles or {}
    for k in range(4):
        tgt = ik.model(feet.get(k, REST_FOOT[k]))
        p.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", tgt, poles.get(k, POLE[k])))
    p["clapper"] = {"rot": ik.orient(r, fr, "clapper", _swing_dir(*tongue), (1, 0, 0)), "pos": (0, retract, 0)}
    p["clapper_b"] = (-whip[0], 0, whip[1])
    p["rope0"] = ik.orient(r, fr, "rope0", _swing_dir(*rope), (1, 0, 0))
    p["chain0"] = ik.orient(r, fr, "chain0", _swing_dir(rope[0], -rope[1]), (1, 0, 0))
    p["face"] = {"rot": tuple(face), "pos": (0, retract * 0.45, 0)}
    p["jaw"] = (jaw, 0, 0)
    if extra:
        p.update(extra)
    return p


def _foot(k, out=0.0, side=0.0, up=0.0, dz=0.0, dx=0.0):
    """Leg k's point moved from where it stands at rest: out along its diagonal, side across it, up, and by (dx, dz)."""
    sx, sz = LEGS[k]
    ox, oz = sx / math.sqrt(2), sz / math.sqrt(2)
    x, h, z = REST_FOOT[k]
    return (x + ox * out - oz * side + dx, h + up, z + oz * out + ox * side + dz)


def _plant(r, clip, legs, where=None):
    """The legs that stand through a move keep their points on the floor where they stand, every tick: the staged
    clip's follow-through and settle would otherwise slide them."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            tgt = ik.model(where.get(k, REST_FOOT[k]))
            out.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", tgt, POLE[k]))
        return out
    ik.retrack(r, clip, fn)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

PAIRS = {0: 0.0, 3: 0.0, 1: 0.5, 2: 0.5}  # the trot: front left with back right, then front right with back left


def _gait(u, stride_px, beta, lift):
    """The points through a trot at u (0..1 of a cycle): down, each moves back exactly as far as the bell travels, so it
    stays put on the floor; up, it lifts high (a crab's leg) and reaches forward again."""
    feet, down = {}, []
    st = stride_px * beta
    for k, ph in PAIRS.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
            down.append(q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f), dz=st / 2 - st * core.ease_io(f), out=1.5 * math.sin(math.pi * f))
    return feet


def _idle(r):
    """Standing in its wide stance: the bell sways a little on its legs as if a draught rang it, the clapper swinging in
    its mouth a beat behind; now and then a leg lifts its point and sets it down again; the face watches."""
    L, n = 5.0, 30
    keys = []
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
        feet = {}
        tap = math.sin(math.pi * min(1.0, max(0.0, (u - 0.55) / 0.2)))  # the front left lifts once, late in the loop
        if tap > 0:
            feet[0] = _foot(0, up=5 * tap, out=-2 * tap)
        pose = _pose(r, lift=0.6 * c, pitch=1.2 * s, roll=1.6 * math.sin(2 * math.pi * u + 0.8), yaw=1.0 * math.sin(4 * math.pi * u),
                     feet=feet, tongue=(-3 * math.sin(2 * math.pi * u - 1.0), 4 * math.sin(2 * math.pi * u - 0.2)),
                     rope=(2 * math.sin(2 * math.pi * u - 1.6), -3 * math.sin(2 * math.pi * u - 0.9)), jaw=2 + 2 * max(0.0, s))
        keys.append((L * u, pose))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """The scuttle: a trot on its four points, each pair lifting high and reaching on as the other carries it; the bell
    rides low into each step and rolls toward the pair that holds it, and the clapper swings fore and aft in its mouth."""
    for name, L, stride, beta, lift, lean, bob in (("walk", 1.1, 1.7, 0.6, 9.0, 2.0, 1.4), ("run", 0.62, 2.8, 0.5, 12.0, 7.0, 2.6)):
        sp = stride * 16
        keys = []
        n = 24
        for k in range(n + 1):
            u = k / n
            s2, c2 = math.sin(4 * math.pi * u), math.cos(4 * math.pi * u)
            s = math.sin(2 * math.pi * u)
            pose = _pose(r, lift=-bob * 0.5 * (1 + c2), pitch=lean + 1.0 * s2, roll=2.4 * s, yaw=1.2 * s, feet=_gait(u, sp, beta, lift),
                         tongue=(-0.6 * lean - 7 * math.sin(4 * math.pi * u - 0.9), 5 * math.sin(2 * math.pi * u - 0.7)),
                         rope=(-0.5 * lean - 4 * math.sin(4 * math.pi * u - 1.4), -4 * s))
            keys.append((L * u, pose))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.7, 2.8
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 16
        for k in range(n + 1):
            u = k / n
            feet = {}
            for j, ph in PAIRS.items():  # each pair steps round the way it turns while the other holds
                q = (u - ph) % 1.0
                if q < 0.5:
                    f = q / 0.5
                    feet[j] = _foot(j, side=-sgn * 7 * (core.ease_io(f) - 0.5), up=7 * math.sin(math.pi * f))
                else:
                    feet[j] = _foot(j, side=-sgn * 7 * (0.5 - (q - 0.5) / 0.5))
            keys.append((0.9 * u, _pose(r, yaw=sgn * 1.5, roll=1.5 * math.sin(4 * math.pi * u), feet=feet,
                                        tongue=(0, -sgn * 6 * math.sin(2 * math.pi * u)))))
        keyed(r, nm, 0.9, keys, loop=True)


def _sat(r, fold=1.0):
    """Sitting on its rim like any bell: its legs folded up its sides like a shut umbrella, the knees down by the floor and
    the points tucked in under its shoulder, the clapper and the face drawn up into its mouth."""
    feet = {k: (LEGS[k][0] * 18.5, 40.0 - 4 * (1 - fold), LEGS[k][1] * 18.5) for k in range(4)}
    poles = {k: (LEGS[k][0] * 0.7, 1.0, LEGS[k][1] * 0.7) for k in range(4)}
    return _pose(r, lift=-(LIP - 0.5), pitch=-BOW, feet=feet, poles=poles, retract=12, tongue=(0, 0), jaw=0)


def _down(r, out=9.0, lift=None):
    """Its rim on the floor (or lift), the legs reaching straight out and down to their points far out round it."""
    return _pose(r, lift=-(LIP - 0.5) if lift is None else lift, pitch=-BOW, feet={k: _foot(k, out=out) for k in range(4)}, retract=12)


def _out(r):
    """Half way between folded and standing: the bell on its rim, each leg thrown out nearly straight, up and away."""
    feet = {k: (LEGS[k][0] * 46, 42.0, LEGS[k][1] * 46) for k in range(4)}
    return _pose(r, lift=-(LIP - 0.5), pitch=-BOW, feet=feet, retract=12)


def _reactions(r):
    """Struck, it rings: the bell swings away from the blow on its legs and back, the clapper striking inside it. Its
    stance broken, it sags on splayed legs until its tongue lies on the floor. It waits for you sitting on its rim with
    its legs folded, and rises out of that onto them, one pair and then the other."""
    hit = _pose(r, lift=-1.5, pitch=-9, tongue=(12, 0), whip=(14, 0), jaw=12, rope=(8, 0), face=(-8, 0, 0))
    back = _pose(r, lift=-0.5, pitch=4, tongue=(-8, 0), whip=(-6, 0), jaw=4, rope=(-5, 0))
    keyed(r, "hurt", 0.6, [(0, _pose(r)), (0.08, hit), (0.32, back), (0.6, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it swings away to its right
        h = _pose(r, lift=-1.5, roll=-10 * sgn, yaw=-6 * sgn, tongue=(4, 14 * sgn), whip=(0, 12 * sgn), jaw=10, rope=(0, 9 * sgn),
                  face=(0, 12 * sgn, 0))
        b = _pose(r, lift=-0.5, roll=4 * sgn, yaw=2 * sgn, tongue=(0, -9 * sgn), whip=(0, -5 * sgn), jaw=3, rope=(0, -5 * sgn))
        keyed(r, nm, 0.65, [(0, _pose(r)), (0.08, h), (0.34, b), (0.65, _pose(r))])
    # broken (and waiting for you): it sinks onto its rim, its legs sliding out, then folds them up its sides; it rises
    # by throwing them out, setting the points down far out round it and pushing itself up on them
    half = _pose(r, lift=-14, feet={k: _foot(k, out=5) for k in range(4)}, retract=6, tongue=(6, 0))
    keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.22, half), (0.42, _down(r)), (0.66, _out(r)), (0.9, _sat(r))])
    keyed(r, "rise", 1.1, [(0, _sat(r)), (0.22, _out(r)), (0.46, _down(r, out=10)), (0.74, _down(r, out=6, lift=-12)), (1.1, _pose(r))])
    _death(r)


def _floored(r, tilt, feet, poles=None, extra=None):
    """The bell set down on its rim, tipped by tilt (pitch, roll) and let down until its lowest corner touches the floor."""
    p = _pose(r, lift=0, pitch=-BOW + tilt[0], roll=tilt[1], feet=feet, poles=poles, retract=12)
    low = core.lowest(r, core.as_sample(p), ["body"]) - core.GROUND  # model y: how far below the floor's height it sits
    return _pose(r, lift=low, pitch=-BOW + tilt[0], roll=tilt[1], feet=feet, poles=poles, retract=12, extra=extra)


def _death(r):
    """Its last toll: a shudder runs through it, its front left leg gives and it lurches down that way, the others give,
    and it drops onto its rim with a last dull ring. It rattles round on its rim the way a dropped bell does, lower and
    lower, and comes to rest: just a bell again, its legs splayed out flat round it like a dead crab's."""
    shake = _pose(r, lift=-1, roll=4, pitch=-6, tongue=(10, -8), whip=(16, 0), jaw=16, face=(-10, 0, 0))
    give = _pose(r, lift=-10, pitch=12, roll=10, feet={0: _foot(0, out=10), 1: _foot(1, out=4)}, tongue=(20, 10), whip=(-10, 0), jaw=18)
    flat = {k: _foot(k, out=44 - 3 * k, side=(5 if k % 2 else -5)) for k in range(4)}  # nearly straight, flat out round it
    low = {k: (LEGS[k][0] * 0.3, -1.0, LEGS[k][1] * 0.3) for k in range(4)}  # what bend is left lifts the knees, not into the floor
    keys = [(0, _pose(r)), (0.3, shake), (0.75, give), (1.0, _floored(r, (6, 8), flat, low))]
    t, n = 1.0, 9
    for j in range(1, n + 1):  # the rattle: its tilt goes round its rim and dies away
        a = 8.0 * (1 - j / n) ** 1.4
        ph = 0.9 + 1.25 * j
        keys.append((t + 0.17 * j * (1 + 0.06 * j), _floored(r, (a * math.cos(ph), a * math.sin(ph)), flat, low)))
    end = keys[-1][0]
    keys.append((end + 0.6, _floored(r, (0.4, 0.3), flat, low)))
    keyed(r, "death", end + 0.9, keys + [(end + 0.9, _floored(r, (0.4, 0.3), flat, low))])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b + str(k): 0 for b in ("thigh", "shin", "foot") for k in range(4)}


def _lag(**kw):
    out = dict(NO_LAG, face=0.05)
    out.update(kw)
    return out


def _leg_hit(k):
    return [(f"shin{k}", (0, SHIN - 16, 0), 2.5), (f"foot{k}", (0, 0, 0), 2.5)]


def stab(r, k=0, mid="stab"):
    """A front leg rears up, its point drawn high and back over the knee as the bell leans away from it; then it stabs
    down onto you a block and a half before it and the bell drops onto it (LIGHT)."""
    sx = LEGS[k][0]
    o = 1 if k == 0 else 0
    rear = _pose(r, lift=1.5, pitch=-6, roll=-4 * sx, yaw=4 * sx, feet={k: _foot(k, up=33, out=-13, dz=5)}, poles={k: (sx * 0.4, -1, -0.6)},
                 tongue=(-8, 4 * sx), jaw=6, face=(-4, 6 * sx, 0))
    gather = _pose(r, lift=0.8, pitch=-3, roll=-2.5 * sx, yaw=1.5 * sx, feet={k: _foot(k, up=15, out=-6, dz=1)}, tongue=(-4, 2.5 * sx), jaw=2,
                   face=(-1, 2 * sx, 0))
    hit = _pose(r, lift=-3, pitch=7, roll=3 * sx, yaw=-3 * sx, feet={k: (sx * 11, 1.0, -36)}, poles={k: (sx * 0.6, -1, -0.3)},
                tongue=(10, -3 * sx), whip=(10, 0), jaw=10, face=(6, -4 * sx, 0))
    held = _pose(r, lift=-3.5, pitch=8, roll=3 * sx, yaw=-3 * sx, feet={k: (sx * 11, 0.5, -37)}, poles={k: (sx * 0.6, -1, -0.3)},
                 tongue=(6, -2 * sx), jaw=8)
    sink = _pose(r, lift=-4.5, pitch=10, roll=3.5 * sx, yaw=-4 * sx, feet={k: (sx * 11, 0.5, -37.5)}, poles={k: (sx * 0.6, -1, -0.3)},
                 tongue=(0, -1 * sx), jaw=6, face=(4, -3 * sx, 0))  # its weight comes on down onto the point
    back = _pose(r, lift=-1, pitch=3, feet={k: _foot(k, up=6, dz=-5)}, tongue=(-4, 0))
    return Move(mid, "LIGHT", 11, 4, 14, through=sink, track=8.0, rng=(0, 3.0), kinds=("combo",), damage=1.0, knock=0.6, hit=_leg_hit(k), plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]},
                lag=_lag())


def clapper(r):
    """Clapper Strike (HEAVY): it rocks back on its hind legs, its mouth lifting toward you, and the clapper swings far
    back inside it; it holds; then it pitches forward hard over its front legs and the tongue lashes out of its mouth
    and slams the floor two blocks before it."""
    dip = _pose(r, lift=1, pitch=-9, feet={0: _foot(0, dz=-1), 1: _foot(1, dz=-1)}, tongue=(-34, 0), whip=(-14, 0), jaw=9, face=(-6, 0, 0), rope=(7, 0))
    rear = _pose(r, lift=4, pitch=-22, feet={0: _foot(0, dz=-3), 1: _foot(1, dz=-3)}, tongue=(-70, 0), whip=(-40, 0), jaw=20, face=(-14, 0, 0),
                 rope=(16, 0))
    hit = _pose(r, lift=-9, pitch=24, feet={0: _foot(0, dz=4, out=-2), 1: _foot(1, dz=4, out=-2)}, tongue=(78, 0), whip=(8, 0), jaw=22,
                face=(12, 0, 0), rope=(-20, 0))
    held = _pose(r, lift=-10, pitch=26, feet={0: _foot(0, dz=4, out=-2), 1: _foot(1, dz=4, out=-2)}, tongue=(82, 0), whip=(12, 0), jaw=16,
                 face=(14, 0, 0), rope=(-12, 0))
    on = _pose(r, lift=-10.5, pitch=27, feet={0: _foot(0, dz=4, out=-2), 1: _foot(1, dz=4, out=-2)}, tongue=(82, 0), whip=(4, 0), jaw=12,
               face=(12, 0, 0), rope=(-4, 0))
    lift = _pose(r, lift=-5, pitch=12, tongue=(40, 0), whip=(-12, 0), jaw=8, rope=(6, 0))
    return Move("clapper", "HEAVY", 18, 6, 22, through=on, title="Clapper Strike", hold=8, track=5.0, ability="Clapper Strike", origin=(0, 0, 2.2), rng=(0, 4.0),
                kinds=("area", "near"), damage=1.3, knock=1.0, disc=2.0, disc_at="hit", plant=(),
                hit=[("clapper_b", (0, 21, 0), 5.0), ("clapper_b", (0, 10, 0), 2.5)],
                stages={"windup": [(0.3, dip), (1.0, rear)], "active": [(0.45, hit), (1.0, held)], "recover": [(0.45, lift)]},
                lag=_lag(clapper=0, clapper_b=0.03), ground=[(["ball", "flight"], [("clapper", 0, 60), ("clapper_b", 0, 60)])], bite=0.45)


def toll(r):
    """Toll Wave (HEAVY): it draws its tongue and its face up into its mouth and rises on its legs, the points pressing
    the floor; it holds; then it slams its rim down onto the floor, the legs splaying, and the bell rings: the wave rolls
    out from it, and under its rim is the only place it can't reach."""
    dip = _pose(r, lift=-3, pitch=2, retract=4, tongue=(4, 0))
    up = _pose(r, lift=12, pitch=-BOW + 2, feet={k: _foot(k, out=-4) for k in range(4)}, retract=12, jaw=0, rope=(4, 0))
    slam = _pose(r, lift=-(LIP - 1.0), pitch=-BOW, feet={k: _foot(k, out=5) for k in range(4)}, retract=12, rope=(-14, 0))
    ring = _pose(r, lift=-(LIP - 2.0), pitch=-BOW, roll=1.5, feet={k: _foot(k, out=10, side=(2 if k % 2 else -2)) for k in range(4)}, retract=12,
                 rope=(-6, 4))
    spread = _pose(r, lift=-(LIP - 2.5), pitch=-BOW, roll=-0.5, feet={k: _foot(k, out=12, side=(3 if k % 2 else -3)) for k in range(4)},
                   retract=12, rope=(2, 6))  # the points still sliding out under its weight
    hum = _pose(r, lift=-(LIP - 3.0), pitch=-BOW, roll=-2, feet={k: _foot(k, out=7, side=(-1 if k % 2 else 1)) for k in range(4)}, retract=11,
                rope=(6, -6))  # still humming on its rim
    rise = _pose(r, lift=-14, pitch=-BOW * 0.5, feet={k: _foot(k, out=3) for k in range(4)}, retract=6, rope=(4, 0))
    return Move("toll", "HEAVY", 18, 6, 22, through=spread, title="Toll Wave", hold=10, track=0.0, ability="Toll Wave", rng=(0, 10), kinds=("area",),
                damage=1.0, knock=0.8, plant=(),
                stages={"windup": [(0.3, dip), (1.0, up)], "active": [(0.35, slam), (1.0, ring)], "recover": [(0.42, hum), (0.62, rise)]},
                lag=_lag(face=0))


def echo(r):
    """Echo Bolts: it turns its mouth to you, tipping back on its hind legs so you can see the face inside, and hums: the
    bell shudders three times and the face's jaw drops with each, a bolt flying from its mouth."""
    tip = _pose(r, lift=2, pitch=-20, feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)}, tongue=(-24, 0), jaw=8, face=(-18, 0, 0))
    gather = _pose(r, lift=0, pitch=-8, tongue=(-8, 0), jaw=4, face=(-8, 0, 0))

    def hum(j, a):
        return _pose(r, lift=2 - 0.6 * a, pitch=-20 + 3 * a, roll=1.5 * a * (1 if j % 2 else -1), tongue=(-24 + 10 * a, 3 * a), jaw=8 + 20 * a,
                     face=(-18 + 4 * a, 0, 0), feet={j % 2: _foot(j % 2, dz=-2, up=5 * a), 1 - j % 2: _foot(1 - j % 2, dz=-2)})
    act = []
    for j in range(3):
        act += [((j + 0.25) / 3, hum(j, 1.0)), ((j + 0.85) / 3, hum(j, 0.2))]
    sag = _pose(r, lift=1, pitch=-15, tongue=(-12, 0), jaw=11, face=(-13, 0, 0), feet={0: _foot(0, dz=-2), 1: _foot(1, dz=-2)})
    return Move("echo", "LIGHT", 12, 18, 14, through=sag, title="Echo Bolts", track=9.0, ability="Echo Bolts", rng=(4, 18), kinds=("ranged",),
                damage=0.8, knock=0.3, plant=(), stages={"windup": [(0.3, gather), (1.0, tip)], "active": act, "recover": [(0.5, gather)]},
                lag=_lag())


def rake(r):
    """For whoever is behind it (LIGHT): the face turns to look back under its rim, a back leg lifts high out to its side
    and rakes across behind it, the bell swinging the other way to balance."""
    k, sx = 2, 1  # the back left
    lift_ = _pose(r, lift=1, roll=-6, yaw=-6, pitch=3, feet={k: _foot(k, up=24, side=10, out=6)}, poles={k: (0.8, -1, 0.4)}, face=(0, 0, 0),
                  tongue=(4, -6), extra={"face": {"rot": (0, -150, 0), "pos": (0, 0, 0)}})
    gather = _pose(r, lift=0.5, roll=-3, yaw=-3, feet={k: _foot(k, up=10, side=4)}, tongue=(2, -3), extra={"face": {"rot": (0, -90, 0), "pos": (0, 0, 0)}})
    sweep_ = _pose(r, lift=-1, roll=4, yaw=8, pitch=-2, feet={k: (-6, 6, 38)}, poles={k: (0.4, -1, 0.8)}, tongue=(-4, 8),
                   extra={"face": {"rot": (0, -160, 0), "pos": (0, 0, 0)}})
    out = _pose(r, lift=-1.5, roll=6, yaw=12, feet={k: (-26, 5, 28)}, poles={k: (-0.2, -1, 0.8)}, tongue=(-4, 10),
                extra={"face": {"rot": (0, -150, 0), "pos": (0, 0, 0)}})
    on = _pose(r, lift=-1.5, roll=7, yaw=14, feet={k: (-32, 8, 20)}, poles={k: (-0.4, -1, 0.7)}, tongue=(-2, 11),
               extra={"face": {"rot": (0, -140, 0), "pos": (0, 0, 0)}})
    back = _pose(r, lift=0, roll=2, yaw=4, feet={k: (14, 13, 38)}, poles={k: (0.6, -1, 0.6)}, extra={"face": {"rot": (0, -80, 0), "pos": (0, 0, 0)}})
    return Move("rake", "LIGHT", 10, 4, 14, through=on, track=0.0, rng=(0, 3.0), kinds=("behind",), damage=0.9, knock=1.0, hit=_leg_hit(k), plant=(),
                stages={"windup": [(0.3, gather), (1.0, lift_)], "active": [(0.45, sweep_), (1.0, out)], "recover": [(0.55, back)]},
                lag=_lag(face=0))


def summon(r):
    """The Statues Move: it rings itself, three great swings from side to side on its braced legs, the clapper flung
    against each side of its mouth, and with each stroke a statue round the floor steps off its plinth."""
    brace = {k: _foot(k, out=4) for k in range(4)}
    swing = lambda s, a: _pose(r, lift=1, roll=18 * s * a, yaw=-4 * s * a, feet=brace, tongue=(0, -40 * s * a), whip=(0, -20 * s * a), jaw=10 * a,
                               rope=(0, -22 * s * a), face=(0, 0, 4 * s * a))
    gather = _pose(r, lift=-2, feet=brace, tongue=(0, 6), jaw=2)
    act = [(0.17, swing(1, 0.8)), (0.34, swing(-1, 1.0)), (0.5, swing(1, 1.0)), (0.67, swing(-1, 1.0)), (0.84, swing(1, 0.7)), (1.0, swing(-1, 0.3))]
    return Move("summon", "NONE", 12, 30, 16, title="The Statues Move", track=0.0, ability="The Statues Move", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.3, gather), (1.0, swing(-1, 0.4))], "active": act, "recover": [(0.5, gather)]},
                lag=_lag())


def rush(r):
    """Bell Rush (HEAVY, a gap closer): it crouches low on its legs, its mouth tipped at you and the clapper drawn back;
    then it scuttles at you, its points pattering, the rim leading."""
    low = _pose(r, lift=-10, pitch=12, feet={k: _foot(k, out=4) for k in range(4)}, tongue=(-30, 0), whip=(-20, 0), jaw=14, face=(8, 0, 0))
    gather = _pose(r, lift=-4, pitch=5, feet={k: _foot(k, out=2) for k in range(4)}, tongue=(-10, 0), jaw=6)
    act = []
    n = 6
    for j in range(n + 1):  # the scuttle: the pairs patter through three quick strides as it slides
        u = j / n
        act.append((u, _pose(r, lift=-6 + 2 * math.sin(math.pi * u), pitch=14, feet=_gait((3 * u) % 1.0, 30.0, 0.5, 10.0),
                             tongue=(-30 + 10 * u, 6 * math.sin(6 * math.pi * u)), jaw=14, face=(10, 0, 0))))
    skid = _pose(r, lift=-5, pitch=16, feet=_gait(0.15, 30.0, 0.5, 10.0), tongue=(-12, 0), whip=(8, 0), jaw=12, face=(10, 0, 0))
    end = _pose(r, lift=-6, pitch=6, tongue=(30, 0), whip=(20, 0), jaw=8)
    return Move("rush", "HEAVY", 14, 14, 18, through=skid, title="Bell Rush", track=6.0, lock=10, advance=7.0, ability="Bell Rush", rng=(5, 14), kinds=("gap",),
                damage=1.2, knock=1.4, plant=(), hit=[("body", (0, 14, -23), 6.0)],
                stages={"windup": [(0.3, gather), (1.0, low)], "active": act[1:], "recover": [(0.5, end)]}, lag=_lag())


def shatter(r):
    """Shattered Bronze (HEAVY): it crouches and shudders, faster and faster, the crack up its side flaring; then it
    throws itself up onto the points of its legs and the bronze flies off it in shards all round."""
    crouch = {k: _foot(k, out=3) for k in range(4)}
    shud = lambda a, s: _pose(r, lift=-6 + 0.8 * s, pitch=3 * s * a, roll=3 * s * a, feet=crouch, tongue=(6 * s * a, -8 * s * a), jaw=6 + 10 * a)
    keys = [(0.3, shud(0.4, 1))]
    for j, f in enumerate((0.45, 0.58, 0.69, 0.78, 0.86, 0.93)):
        keys.append((f, shud(0.6 + 0.07 * j, 1 if j % 2 else -1)))
    keys.append((1.0, shud(1.0, 1)))
    burst = _pose(r, lift=10, pitch=-BOW, feet={k: _foot(k, out=-4) for k in range(4)}, tongue=(0, 0), whip=(30, 0), jaw=24, rope=(12, 6), face=(-10, 0, 0))
    held = _pose(r, lift=9, pitch=-BOW + 2, feet={k: _foot(k, out=-4) for k in range(4)}, tongue=(-6, 4), whip=(-20, 0), jaw=18, rope=(-8, -4))
    sink = _pose(r, lift=7, pitch=-BOW + 1, feet={k: _foot(k, out=-3) for k in range(4)}, tongue=(4, -2), whip=(-8, 0), jaw=12, rope=(-12, -6),
                 face=(-6, 0, 0))
    down = _pose(r, lift=-3, pitch=2, tongue=(8, 0), jaw=4)
    return Move("shatter", "HEAVY", 18, 8, 18, through=sink, title="Shattered Bronze", track=0.0, ability="Shattered Bronze", rng=(0, 14), kinds=("area",),
                damage=1.0, knock=0.5, plant=(), stages={"windup": keys, "active": [(0.3, burst), (1.0, held)], "recover": [(0.5, down)]},
                lag=_lag())


def peal(r):
    """The Grand Peal (PERILOUS, the signature): it draws everything into its mouth and rises up onto the points of its
    legs, then rings itself: swings from side to side, wider and wider, the rope and the chain flying; at the top of the
    last it pulls itself straight up and slams its rim down on the floor, and the whole belfry rings."""
    tall = {k: _foot(k, out=-6) for k in range(4)}
    rise = _pose(r, lift=14, pitch=-BOW, feet=tall, retract=12, rope=(6, 0))
    sw = lambda s, a: _pose(r, lift=14 - 3 * a, pitch=-BOW, roll=26 * s * a, yaw=-5 * s * a, feet=tall, retract=12, rope=(0, -30 * s * a),
                            extra={"chain0": (0, 0, 30 * s * a)})
    keys = [(0.22, rise), (0.4, sw(1, 0.4)), (0.56, sw(-1, 0.65)), (0.72, sw(1, 0.85)), (0.86, sw(-1, 1.0)), (1.0, _pose(r, lift=16, pitch=-BOW,
                                                                                                                     feet=tall, retract=12, rope=(10, 0)))]
    slam = _pose(r, lift=-(LIP - 1.0), pitch=-BOW, feet={k: _foot(k, out=6) for k in range(4)}, retract=12, rope=(-20, 0))
    ring = _pose(r, lift=-(LIP - 2.5), pitch=-BOW, roll=-2, feet={k: _foot(k, out=6) for k in range(4)}, retract=12, rope=(-8, -6))
    spread = _pose(r, lift=-(LIP - 3.0), pitch=-BOW, roll=1.5, feet={k: _foot(k, out=9, side=(2 if k % 2 else -2)) for k in range(4)}, retract=12,
                   rope=(4, 4))
    rise2 = _pose(r, lift=-12, pitch=-BOW * 0.5, feet={k: _foot(k, out=3) for k in range(4)}, retract=6)
    return Move("peal", "PERILOUS", 32, 8, 22, through=spread, title="Grand Peal", track=0.0, ability="Grand Peal", rng=(0, 16), kinds=("area",), signature=True,
                damage=1.0, knock=1.0, plant=(), stages={"windup": keys, "active": [(0.3, slam), (1.0, ring)], "recover": [(0.5, rise2)]},
                lag=_lag(face=0))


def wake(r):
    """Its intro, once it has risen onto its legs: it rings once, a single great swing forward and back that flings the
    clapper against its lip; the face looks out at you."""
    a = _pose(r, lift=2, pitch=-18, tongue=(-40, 0), whip=(-20, 0), jaw=10, face=(-12, 0, 0), rope=(14, 0))
    b = _pose(r, lift=-2, pitch=12, tongue=(50, 0), whip=(30, 0), jaw=24, face=(6, 0, 0), rope=(-16, 0))
    c = _pose(r, lift=0, pitch=-5, tongue=(-16, 0), whip=(-10, 0), jaw=12, rope=(6, 0))
    return Move("wake", "NONE", 10, 24, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(1.0, a)], "active": [(0.35, b), (0.7, c), (1.0, _pose(r, jaw=6, face=(-4, 0, 0)))], "recover": [(0.5, _pose(r, jaw=3))]},
                lag=_lag())


# the ground (SPEC §35.10): the points, the clapper's ball and the rope's end stay out of the floor
LEG_GROUND = [([f"toe{k}", f"shinc{k}"], [(f"thigh{k}", 0, 30), (f"shin{k}", 0, 40)]) for k in range(4)]
CLAP_GROUND = [(["ball", "flight"], [("clapper", 0, 80, -1), ("clapper_b", 0, 80, -1)])]
HANG_GROUND = [(["rope2"], [("rope0", 0, 80, -1), ("rope1", 0, 80, -1)]), (["chain2"], [("chain0", 0, 80, -1), ("chain1", 0, 80, -1)])]
STANDS = {"stab": (1, 2, 3), "stab_b": (0, 2, 3), "clapper": (2, 3), "toll": (), "echo": (2, 3), "rake": (0, 1, 3), "summon": (),
          "rush": (), "shatter": (), "peal": (), "wake": (2, 3)}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    keyed(r, "sat", 1.0, [(0, _sat(r)), (1.0, _sat(r))])
    mv = [stab(r), stab(r, 1, "stab_b"), clapper(r), toll(r), echo(r), rake(r), summon(r), rush(r), shatter(r), peal(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    own = {core.clip_name(m) for m in mv if m.ground}
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, LEG_GROUND, clips=everyday)
    core.ground_pass(r, CLAP_GROUND, clips=[n for n in everyday if n not in own])
    core.ground_pass(r, HANG_GROUND, clips=everyday)
    core.ground_pass(r, CLAP_GROUND + HANG_GROUND, clips=["death"], skip=())
    del r.clips["sat"]
    return mv


# Its sounds (SPEC §35.8): bronze and bells, stone and old wood; chosen by ear on paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("block.bell.resonate", 0.8, 1.6), ("block.chain.step", 0.8, 0.7)],
    "tell_heavy": [("block.bell.resonate", 1.6, 0.6), ("block.copper_bulb.turn_on", 1.2, 0.6)],
    "tell_perilous": [("block.bell.resonate", 2.4, 0.5), ("block.beacon.activate", 2.0, 0.5), ("block.vault.ambient", 1.6, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.2, 0.8), ("block.copper_grate.step", 1.2, 0.7)],
    "swing_heavy": [("entity.wind_charge.wind_burst", 1.4, 0.6), ("block.chain.break", 1.2, 0.6)],
    "cast": [("block.bell.use", 1.6, 0.8), ("block.amethyst_block.resonate", 1.4, 0.6)],
    "impact": [("block.bell.use", 2.4, 0.5), ("item.mace.smash_ground_heavy", 1.8, 0.6), ("block.anvil.land", 1.0, 0.5)],
    "roar": [("block.bell.use", 2.6, 0.45), ("block.bell.resonate", 2.0, 0.5)],
    "step": [("block.copper.step", 1.0, 0.7), ("block.chain.step", 0.8, 0.8)],
}
MOVE_SOUNDS = {"wake": {"swing": "roar"}, "summon": {"swing": "roar"}, "echo": {"swing": "cast"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "stab", "weight": 3, "when": "near", "then": [["stab_b"], ["stab_b", "clapper"]]},
            {"move": "clapper", "weight": 3},
            {"move": "toll", "weight": 3},
            {"move": "echo", "weight": 3, "when": "far"},
            {"move": "rake", "weight": 3, "when": "behind"},
        ]},
        {"transition": "summon", "pool": [
            {"move": "stab", "weight": 3, "when": "near", "then": [["stab_b"], ["stab_b", "clapper"]]},
            {"move": "clapper", "weight": 2},
            {"move": "toll", "weight": 3},
            {"move": "echo", "weight": 2, "when": "far"},
            {"move": "rush", "weight": 3, "when": "far", "then": [["stab"]]},
            {"move": "rake", "weight": 3, "when": "behind"},
            {"move": "summon", "weight": 1},
        ]},
        {"transition": "peal", "pool": [
            {"move": "stab", "weight": 3, "when": "near", "then": [["stab_b", "clapper"]]},
            {"move": "clapper", "weight": 2, "then": [["toll"]]},
            {"move": "shatter", "weight": 3},
            {"move": "toll", "weight": 2},
            {"move": "rush", "weight": 2, "when": "far", "then": [["clapper"]]},
            {"move": "rake", "weight": 3, "when": "behind"},
            {"move": "peal", "weight": 2},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.7, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
