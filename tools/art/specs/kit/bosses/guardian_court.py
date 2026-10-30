"""The Shadow Regent (SPEC §35.11, docs/bosses/ROLLOUT.md): the Court's Guardian, a Duelist. The Long Shadow, picked
2026-10-29: "It sits the way you sit."

Your shadow, stood up off the floor and stretched the way a shadow stretches at dusk: half again as tall as a man and
thin, its arms hanging nearly to its knees, its body as flat as a shadow is. It has the shape of a court's shadow: a
spiked crown, a tall fan collar standing up behind its head, puffed shoulders over a narrow waist, a long cape that
falls nearly to the floor in tatters. It is black-violet all through, rimmed in a cold violet light along its edges like
a shadow with a lamp behind it, with two pale points for eyes. Its feet never leave a pool of shadow on the floor that
goes everywhere with it; out of the pool rises the throne it sits on. In its right hand a long black sword edged with
violet light; its left hand is empty, its long fingers spread.

It stands tall with its sword's point down beside it, its fingers flexing, its crown tilted to look you over; its cape
stirs and its shape wavers now and then like a shadow when the candle moves.

Moves (SPEC §35.11): the Cut and the Backcut (LIGHT, a string), the Long Reach (HEAVY: its arm stretches like a lengthening
shadow and the point reaches you from far off), the Rake of its long fingers (LIGHT), Your Parry (its blade across, the
fingers on the flat, then a riposte), Your Charge (it falls flat along the floor as a cast shadow and slides at you,
and stands up out of the floor under you with a rising cut), the Turnabout for whoever is behind it (its head turns
right round on its shoulders and its arm bends back the wrong way to stab), the Shadowstep (it folds down into its pool
and the pool slides away), Your Reflections (into the second phase: it shudders and stretches, and others of it are
there) and the Hollow Halo (the signature: its crown lifts off its head and widens into a ring over it, turning, and
breaks).
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import edge_glow
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_court"
SCALE = 1.6  # the def's scale; the body is modelled at true size and the hitbox set round it

# The Long Shadow (docs/bosses/ROLLOUT.md, A): 46 px to its crown's points at true size, its hips 19 up on long legs, its
# shoulders 33 up and 11 apart, its arms 30 long, its body 3 px through. Value story: its eyes and the edges of its
# blade brightest, then its violet rim, its body near black, the pool the darkest thing in the room.
HIP = 20.0  # the pelvis's pivot over the floor (the hip joints a pixel under it)
DROP = 0.8  # standing, its hips sit this much lower than straight legs would put them
FOOT = {"l": (3.0, 2.0, 1.0), "r": (-3.0, 2.0, -1.5)}  # the ankles at rest (x, height, z): the right a little forward
TOE = {"l": (0.94, 0, 0.34), "r": (1, 0, -0.1)}  # each foot's +x
GRIP = {"r": (-7.0, 12.0, -5.0), "l": (7.5, 11.5, -1.5)}  # the hands at rest (x, height, z): its long arms hanging, the hands at its knees
POLE = {"r": (-0.7, 0.5, 0.5), "l": (0.8, 0.4, 0.4)}  # the elbows: out and back
REST_HAND = {"r": ((-0.12, 0.25, -0.96), (0, 0, -1)), "l": ((0.15, 1, -0.1), (0, 0, -1))}  # the sword's point down and ahead; the fingers down
BLADE = 26.0  # the sword's point from the hilt's bone
TIP = (0, BLADE, 0)
ARMS = tuple(f"{b}_{s}" for b in ("arm", "forearm", "hand") for s in "lr")
FINGERS = ("lf0", "lf1", "lf2", "lthumb")
FINGER_REST = {"lf0": (8, 0, -14), "lf1": (6, 0, 0), "lf2": (8, 0, 14), "lthumb": (-30, 0, -30)}  # spread
HIDE = 0.005  # the throne's height when it lies folded into the pool


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    # the pool of shadow on the floor, a pixel thick and sunk most of the way in: a blot round its feet with wisps
    # reaching out toward you, and behind it, joined at the feet, its own shadow lying flat on the floor, stretched
    # long: its legs, its body and cape, its arms at its sides, its collar's fan, its head and its crown's points
    pl = r.bone("pool", (0, 24, 0))
    _box(pl, "pool", 0, -0.85, -0.5, 16, 1, 13)
    for k, (yaw, x, z, L) in enumerate(((25, 6, -7, 6), (-20, -6, -7, 5), (0, 0, -7, 4))):
        t = _bone(pl, f"tendril{k}", x, 0, z, (0, yaw, 0))
        _box(t, f"tendril{k}", 0, -0.9, -L / 2, 2 if k < 2 else 3, 1, L)
    cs = _bone(pl, "cast", 0, 0, 6)
    for nm, sg in (("l", 1), ("r", -1)):
        _box(cs, "cleg_" + nm, sg * 2.5, -0.85, 9.5, 3, 1, 19)
        _box(cs, "carm_" + nm, sg * 7.5, -0.85, 26, 2, 1, 16)
        _box(cs, "chand_" + nm, sg * 7.5, -0.85, 16.5, 3, 1, 3)
    _box(cs, "cbody", 0, -0.85, 27.5, 11, 1, 17)
    _box(cs, "cfan", 0, -0.85, 37, 12, 1, 2)
    _box(cs, "chead", 0, -0.85, 41, 6, 1, 6)
    _box(cs, "cband", 0, -0.85, 44.5, 7, 1, 1)
    for k, (x, L) in enumerate(((0, 4), (-2.5, 3), (2.5, 3))):
        _box(cs, f"cpoint{k}", x, -0.85, 45 + L / 2, 1, 1, L)
    # the throne it sits on, rising out of the pool behind it (folded flat into the pool unless it sits): a tall back
    # with three spikes, the seat, the arms
    th = r.bone("throne", (0, 24, 5))
    _box(th, "seat", 0, 0, 0, 12, 10, 9)
    _box(th, "throne_back", 0, 0, 5.5, 14, 30, 2)
    for k, x in enumerate((-5, 0, 5)):
        _box(th, f"finial{k}", x, 30, 5.5, 2, 4 + 2 * (k == 1), 2)
    for nm, sg in (("l", 1), ("r", -1)):
        _box(th, "tarm_" + nm, sg * 7, 10, -0.5, 2, 5, 9)
    # the figure, on a root at the floor between its feet: it falls flat about it (its charge) and folds down into it
    base = r.bone("base", (0, 24, 0))
    pel = _bone(base, "pelvis", 0, HIP - DROP, 0)
    _box(pel, "pelvis", 0, -2, 0, 5, 3, 3)
    for nm, sg in (("l", 1), ("r", -1)):
        thb = pel.bone("thigh_" + nm, (sg * 2, 1, 0))
        thb.cube("thigh_" + nm, -1, -1, -1, 2, 10, 2)
        sn = thb.bone("shin_" + nm, (0, 9, 0))
        sn.cube("shin_" + nm, -1, 0, -1, 2, 8, 2)
        ft = sn.bone("foot_" + nm, (0, 8, 0))
        ft.cube("foot_" + nm, -1.5, 0, -4, 3, 2, 6)
        ft.cube("toe_" + nm, -0.5, 1, -6, 1, 1, 2)  # its shoes long and pointed
    sp = _bone(pel, "spine", 0, 1, 0)
    _box(sp, "spine", 0, 0, 0, 4, 6, 3)
    _box(sp, "girdle", 0, 0, 0, 6, 2, 4)
    ch = _bone(sp, "chest", 0, 6, 0)
    _box(ch, "chest", 0, 0, 0, 8, 7, 3)
    _box(ch, "doublet", 0, 3, -0.5, 10, 4, 3)  # the broad of its chest
    for nm, sg in (("l", 1), ("r", -1)):
        _box(ch, "puff_" + nm, sg * 5.5, 4, 0, 3, 4, 4)
    # its collar: a ruff round its neck and a tall fan standing up behind its head, pointed along its edge
    _box(ch, "ruff", 0, 7, 0, 8, 1, 5)
    fan = _bone(ch, "fan", 0, 7.5, 1.5, (-14, 0, 0))
    _box(fan, "fan", 0, 0, 0, 10, 6, 1)
    _box(fan, "fan_top", 0, 6, 0, 8, 2, 1)
    for k, x in enumerate((-4.5, -1.5, 1.5, 4.5)):
        _box(fan, f"fanpt{k}", x, 8 - (abs(x) > 3) * 2, 0, 1, 2, 1)
    nk = _bone(ch, "neck", 0, 7, -0.3)
    _box(nk, "neck", 0, 0, 0, 2, 2, 2)
    hd = _bone(nk, "head", 0, 2, -0.3, (6, 0, 0))  # its head bowed a little, to look down at you
    _box(hd, "head", 0, 0, 0, 5, 6, 4)
    for nm, sg in (("l", 1), ("r", -1)):
        _box(hd, "eye_" + nm, sg * 1.2, 2.5, -1.6, 1, 2, 1)
    # its crown, on its own bone: it lifts off and widens into the Hollow Halo
    cr = _bone(hd, "crown", 0, 6, 0)
    _box(cr, "crownband", 0, 0, 0, 6, 1, 5)
    for k, (x, z, h) in enumerate(((0, -2, 4), (-2.5, -2, 3), (2.5, -2, 3), (-2.5, 2, 3), (2.5, 2, 3), (0, 2, 2))):
        _box(cr, f"point{k}", x, 1, z, 1, h, 1)
    # its cape, in three falls on springs from its shoulders, tattered at the hem
    cp = _bone(ch, "cape0", 0, 6.5, 1.9, (5, 0, 0))
    _box(cp, "cape0", 0, -10, 0, 12, 10, 1)
    cp1 = _bone(cp, "cape1", 0, -10, 0, (2, 0, 0))
    _box(cp1, "cape1", 0, -10, 0, 13, 10, 1)
    cp2 = _bone(cp1, "cape2", 0, -10, 0, (1, 0, 0))
    _box(cp2, "cape2", 0, -6, 0, 14, 6, 1)
    for k, x in enumerate((-5.5, -1.5, 2.5, 6.0)):
        _box(cp2, f"tatter{k}", x, -6 - 2 - (k % 2), 0, 2 - (k == 3), 2 + (k % 2), 1)
    # the long arms, the right holding the sword, the left with its long fingers spread. Each hangs from a shoulder and
    # each hand from a wrist that hold the arm's and the hand's rest turns (no cubes, never keyed), so the arm and the
    # hand rest at nothing and their turns in a move stay far from the Euler triple's fold at 90 degrees
    for nm, sg in (("l", 1), ("r", -1)):
        sh = ch.bone("shoulder_" + nm, (sg * 5.5, -5.5, 0))
        a = sh.bone("arm_" + nm, (0, 0, 0))
        a.cube("arm_" + nm, -1, -1, -1, 2, 11, 2)
        fa = a.bone("forearm_" + nm, (0, 10.5, 0))
        fa.cube("forearm_" + nm, -1, 0, -1, 2, 10, 2)
        fa.cube("cuff_" + nm, -1.5, 8, -1.5, 3, 2, 3)
        wr = fa.bone("wrist_" + nm, (0, 10, 0))
        hn = wr.bone("hand_" + nm, (0, 0, 0))
        hn.cube("hand_" + nm, -1.5, 0, -1, 3, 3, 2)
    lh = r.by_name["hand_l"]
    for k, x in enumerate((-1.0, 0.0, 1.0)):
        f = lh.bone(f"lf{k}", (x, 2.8, -0.2), FINGER_REST[f"lf{k}"])
        f.cube(f"lf{k}", -0.5, 0, -0.5, 1, 5, 1)
        f.cube(f"lft{k}", -0.5, 5, -0.5, 1, 2, 1)
    tb = lh.bone("lthumb", (-1.4, 1.5, -0.6), FINGER_REST["lthumb"])
    tb.cube("lthumb", -0.5, 0, -0.5, 1, 3, 1)
    # the hands where they hang at rest, then the sword along its line
    for s in "rl":
        ik.rest_limb(r, "arm_" + s, "forearm_" + s, "wrist_" + s, GRIP[s], POLE[s])
        r.by_name["shoulder_" + s].rot, r.by_name["arm_" + s].rot = r.by_name["arm_" + s].rot, (0.0, 0.0, 0.0)
    fr = core.frames(r, {})
    for s in "rl":
        r.by_name["wrist_" + s].rot = ik.orient(r, fr, "hand_" + s, *REST_HAND[s])
    sw = r.by_name["hand_r"].bone("sword", (0, 1.5, 0))
    sw.cube("pommel", -1, -4, -1, 2, 1, 2)
    sw.cube("grip", -0.5, -3, -0.5, 1, 4, 1)
    sw.cube("guard", -3.5, 1, -1, 7, 1, 2)
    sw.cube("quil_l", 3, 2, -0.5, 1, 2, 1)
    sw.cube("quil_r", -4, 2, -0.5, 1, 2, 1)
    sw.cube("blade0", -1.5, 2, -0.5, 3, 9, 1)
    sw.cube("blade1", -1, 11, -0.5, 2, 9, 1)
    sw.cube("blade2", -0.5, 20, -0.5, 1, 6, 1)
    # the feet where they stand, flat on the floor, and the legs' rest taken from the planter's own solution
    for s in "rl":
        ik.rest_limb(r, "thigh_" + s, "shin_" + s, "foot_" + s, FOOT[s], (0, 0, -1))
    fr = core.frames(r, {})
    for s in "rl":
        r.by_name["foot_" + s].rot = ik.orient(r, fr, "foot_" + s, (0, 1, 0), TOE[s])
    p = core.plant(r, {}, ("l", "r"))
    for s in "lr":
        for b in ("thigh_", "shin_", "foot_"):
            bn = r.by_name[b + s]
            bn.rot = tuple(bn.rot[i] + p[b + s][i] for i in range(3))
    r.look = [("head", 0.5, 0.45), ("neck", 0.25, 0.2)]
    r.size = 17 / 36.0
    r.shadow = 0.0  # it has its pool
    # secondary parts (SPEC §35.10): the cape's falls trail it one after another, its fingers and its collar's fan stir
    r.springs = [("cape0", 0.4, 1.8, 0.2), ("cape1", 0.5, 2.0, 0.24), ("cape2", 0.6, 2.4, 0.28), ("fan", 0.2, 3.0, 0.1)]
    r.guard = ["sword", "hand_r", "hand_l"]  # its blade and its hands never go into the floor
    _REST.clear()
    _REST.update({b.name: tuple(b.rot) for b in r.bones})
    return r


_REST = {}

# ---------------------------------------------------------------------------------------------------------------------
# Paint

SHADE = Ramp("#040208", "#08050f", "#0e0918", "#150e22", "#1c132d", "#251939", "#2f2047", "#3a2856")
RIM = ("#5a3c9e", "#8463d4", "#b49cf2", "#e2d6ff")
POOL = Ramp("#010103", "#030207", "#06040c", "#0b0714")
EYE = "#f2eaff"


def _rimmed(P_, c, rows=1, alpha=150, col=RIM[1], sides=("left", "right"), top=False):
    """A shadow's edge: the cube's outline on its sides lit violet in the glow layer, as if a lamp stood behind it."""
    for fname in SIDES:
        for sd in sides:
            edge_glow(P_, c, fname, col, rows=rows, alpha=alpha, side=sd)
        if top:
            edge_glow(P_, c, fname, col, rows=1, alpha=alpha, side="top")


def paint(r, P_):
    """Near-black violet through and through, a hair lighter toward its top as if lit from above and smoke drifting
    slow in it; every edge of its outline rimmed with violet light in the glow layer; its eyes two pale points; its
    blade black with a bright violet edge; the pool blacker than anything, its rim faintly lit."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    smoke = lambda f, i, j: (0.7 if (i + 2 * j + R._h(f.cube.name, f.name) % 5) % 7 == 0 else -0.5 if (2 * i - j) % 9 == 0 else 0)
    figure = tuple(of("pelvis", "thigh_", "shin_", "spine", "girdle", "chest", "doublet", "puff_", "ruff", "neck", "head", "arm_", "forearm_", "cuff_",
                      "hand_", "cape", "tatter", "fan", "lf", "lthumb"))
    for c in figure:
        P_.paint(c, SHADE, 0.9, smoke, grad=1.2, rim=0.2, ao=0.3, noise=0.3, nscale=3)
    for c in of("foot_", "toe_"):
        P_.paint(c, SHADE, 0.6, None, grad=0.4, rim=0.2, ao=0.4)
    # the outline lit: the long edges of its limbs and body, its shoulders' and collar's tops, the cape's sides
    for c in of("arm_", "forearm_", "thigh_", "shin_", "spine", "chest", "cape", "lf", "lthumb"):
        _rimmed(P_, c, alpha=85)
    for c in of("puff_", "doublet", "ruff", "fan", "fanpt", "head"):
        _rimmed(P_, c, alpha=100)
        for fname in SIDES:
            edge_glow(P_, c, fname, RIM[2], rows=1, alpha=190, side="top")
    for c in of("tatter"):
        _rimmed(P_, c, alpha=110, sides=("left", "right", "bottom"))
    # its crown: black points tipped with light
    for c in of("crownband", "point"):
        P_.paint(c, SHADE, 1.0, None, rim=0.6)
        for fname in SIDES:
            edge_glow(P_, c, fname, RIM[2], rows=1, alpha=210, side="top")
    for c in of("point"):
        for fname in SIDES:
            edge_glow(P_, c, fname, RIM[2], rows=1, alpha=150, side="left")
    with P_.eyes():
        for c in of("eye_"):
            P_.paint(c, Ramp("#c8b8f0", "#e8dcff", EYE), 2.0, None, rim=0.0, ao=0.0)
            for fname in ("north",) + tuple(s for s in SIDES if s != "north"):
                g = P_.face(c, fname, glow=True)
                for i, j in g.each():
                    g.set(i, j, hexc(EYE, 235))
    # its sword: black, a bright violet edge down each side and a pale line down the middle of the flat
    for c in ("blade0", "blade1", "blade2"):
        P_.paint(c, SHADE, 1.2, lambda f, i, j: (2.4 if f.name in ("north", "south") and i == f.w // 2 and f.w >= 2 else 0), rim=0.4, ao=0.0)
        for fname in ("north", "south"):
            edge_glow(P_, c, fname, RIM[2], rows=1, alpha=230, side="left")
            edge_glow(P_, c, fname, RIM[2], rows=1, alpha=230, side="right")
        for fname in ("east", "west"):
            edge_glow(P_, c, fname, RIM[3], rows=99, alpha=200, side="left")
    for c in ("pommel", "grip", "guard", "quil_l", "quil_r"):
        P_.paint(c, SHADE, 2.0, None, rim=0.8)
        for fname in SIDES:
            edge_glow(P_, c, fname, RIM[1], rows=1, alpha=160, side="top")
    # the pool: the darkest thing in the room, its edge faintly lit, a few cold glints in it
    floor = tuple(of("pool", "tendril", "cleg_", "carm_", "chand_", "cbody", "cfan", "chead", "cband", "cpoint"))
    for c in floor:
        P_.paint(c, POOL, 1.6, lambda f, i, j: (1.0 if R._h("glint", f.cube.name, i, j) % 37 == 0 else 0), grad=0.0, rim=0.0, ao=0.0)
        for fname in SIDES + ("bottom",):  # its sides a hair's breadth over the floor, black
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, POOL.at(0))
    # its edge lit along the outline of the whole shape (not each piece's), a few cold glints in it
    flat = {}
    for b in r.bones:
        if b.name in ("pool", "cast"):
            for c in b.cubes:
                flat[c.name] = (c.x, c.x + c.w, c.z + (6 if b.name == "cast" else 0), c.z + c.d + (6 if b.name == "cast" else 0))
    inside = lambda x, z: any(x0 <= x < x1 and z0 <= z < z1 for x0, x1, z0, z1 in flat.values())
    for c in floor:
        f, g = P_.face(c, "top"), P_.face(c, "top", glow=True)
        for i, j in f.each():
            if c in flat:
                x0, _, _, z1 = flat[c]
                x, z = x0 + i + 0.5, z1 - j - 0.5
                edge = not all(inside(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            else:
                edge = i in (0, f.w - 1) or j in (0, f.h - 1)
            if edge:
                g.set(i, j, hexc(RIM[1], 110))
            elif R._h("glint", c, i, j) % 61 == 0:
                g.set(i, j, hexc(RIM[2], 110))
    # the throne: the same shadow, its back's edges and its spikes lit
    for c in of("seat", "throne_back", "finial", "tarm_"):
        P_.paint(c, SHADE, 1.0, smoke, grad=1.2, rim=0.4, ao=0.5, noise=0.3, nscale=3)
        _rimmed(P_, c, alpha=120, top=True)
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion

UP = (0, -1, 0)


def _abs(r, bone, rot):
    """A key for a bone given as its whole turn (the rest taken off: a pose holds what it adds to the rest). An arm's
    whole turn is in the chest's frame, as if it hung from the chest itself: its shoulder's turn is taken off it."""
    if bone.startswith("arm_"):
        sh = r.by_name["shoulder_" + bone[4:]].rot
        M = R._mm(core._t(R._rot(*(c * DEG for c in sh))), R._rot(*(c * DEG for c in rot)))
        return tuple(c / DEG for c in core._euler(M))
    rest = r.by_name[bone].rot
    return tuple(rot[i] - rest[i] for i in range(3))


def _pose(r, base=(0.0, 0.0, 0.0), lift=0.0, fwd=0.0, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0),
          right=None, left=None, rblade=None, lblade=None, legs=None, feet=("l", "r"), step=None, turn=None, curl=0.0, spread=0.0, cape=(0, 0, 0),
          crown=None, more=None):
    """A whole-body key over its rest. base turns the whole figure about the floor between its feet (+x its front
    dipping: lying flat at 90), lifted by lift and moved forward by fwd; it turns at the hips (hips: turn, at: x, up, z),
    the spine, the chest, the neck and the head. Its feet stay where they stand (step: {side: (dx, dy, dz)} model
    pixels, y down) unless legs pose them (whole turns) or feet leaves them out. right, left: where that hand goes,
    (x, height, z) model pixels or ("on", bone, point); rblade, lblade: (ydir, xdir) the line of the sword or of the
    fingers. curl closes the long fingers (1 a fist, - flung open) and spread fans them; cape: (pitch, pitch of the lower
    falls, roll) blown back; crown: (lift, pitch, yaw) off its head."""
    p = {"base": {"rot": tuple(base), "pos": (0.0, lift, -fwd)}, "pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    for k in range(3):
        p[f"lf{k}"] = (70 * curl, 0.0, (k - 1) * 12 * spread)
    p["lthumb"] = (40 * curl, 0.0, -20 * spread)
    p["cape0"] = (cape[0], 0.0, cape[2])
    p["cape1"] = (cape[1], 0.0, 0.0)
    p["cape2"] = (cape[1] * 0.6, 0.0, 0.0)
    if crown:
        p["crown"] = {"rot": (crown[1], crown[2], 0.0), "pos": (0.0, crown[0], 0.0)}
    if legs:
        p.update({b: _abs(r, b, v) for b, v in legs.items()})
        feet = tuple(sd for sd in feet if "thigh_" + sd not in legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step, turn=turn)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "wrist_" + side, q, POLE[side]))
    for side, o in (("l", lblade), ("r", rblade)):
        if o:
            fr = core.frames(r, core.as_sample(p))
            p["hand_" + side] = _wrist(r, fr, "hand_" + side, *o)
    return p


def _wrist(r, fr, bone, ydir, xdir=None):
    """The hand's turn that lays its +y (the blade, the long fingers) along ydir. With no xdir it bends at the wrist by
    the least turn from where the forearm carries it, with no twist: a wrist cannot roll a hand round, and a hand told
    to keep its flat one way while the forearm swings under it rolls right over between keys."""
    if xdir is None:
        b = r.by_name[bone]
        M = R._mm(fr[b.parent.name][0], R._rot(*(c * DEG for c in b.rot)))
        y0 = ik.unit((M[0][1], M[1][1], M[2][1]))
        x0 = (M[0][0], M[1][0], M[2][0])
        y1 = ik.unit(ydir)
        ax = ik.cross(y0, y1)
        sn, cs = math.sqrt(ik.dot(ax, ax)), ik.dot(y0, y1)
        if sn > 1e-6:
            k = tuple(c / sn for c in ax)
            a = math.atan2(sn, cs)
            kx = ik.cross(k, x0)
            x0 = tuple(x0[i] * math.cos(a) + kx[i] * math.sin(a) + k[i] * ik.dot(k, x0) * (1 - math.cos(a)) for i in range(3))
        xdir = x0
    return ik.orient(r, fr, bone, ydir, xdir)


NOWRAP = ("crown",)  # its turns are written as meant: the halo spins it and it unwinds while it is gone


def _forms(bone, v):
    """Every way of writing a bone's turn v (degrees over its rest) a clip could use: both Euler triples of the same
    turn, each angle give or take a whole turn."""
    rest = _REST.get(bone, (0.0, 0.0, 0.0))
    f = tuple(rest[i] + v[i] for i in range(3))
    out = []
    for g in (f, (f[0] + 180, 180 - f[1], f[2] + 180)):
        w = [((g[i] - rest[i] + 180) % 360) - 180 for i in range(3)]
        for kx in (-360, 0, 360):
            for ky in (-360, 0, 360):
                for kz in (-360, 0, 360):
                    out.append((w[0] + kx, w[1] + ky, w[2] + kz))
    return out


def _path(bone, vals, start, end):
    """The forms of a bone's keys (vals, in order) that make the shortest way from start to end through them, both
    pinned: IK solves each key alone, and a clip is read angle by angle, so a key on the other triple than the next, or
    a turn round, spins the bone between them; and a clip must end on rest's own numbers, which the next one blends
    from."""
    if all(max(abs(c) for c in v) < 60 for v in vals):
        return [tuple(((c + 180) % 360) - 180 for c in v) for v in vals]
    cands = [_forms(bone, v) for v in vals]
    d2 = lambda p, q: (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2
    cost = [d2(start, c) for c in cands[0]]
    back = []
    for i in range(1, len(cands)):
        prev, cur = cands[i - 1], cands[i]
        nc, nb = [], []
        for c in cur:
            best, bj = None, 0
            for j, q in enumerate(prev):
                x = cost[j] + d2(q, c)
                if best is None or x < best:
                    best, bj = x, j
            nc.append(best)
            nb.append(bj)
        cost, back = nc, back + [nb]
    k = min(range(len(cost)), key=lambda j: cost[j] + (d2(cands[-1][j], end) if end is not None else 0.0))
    out = [cands[-1][k]]
    for i in range(len(cands) - 2, -1, -1):
        k = back[i][k]
        out.append(cands[i][k])
    return out[::-1]


def _settle_path(keys, start=None, end=None):
    """Keys [pose] with every bone's turns written by _path, pinned at start and end ({bone: turn}; rest when not
    given)."""
    start = start or {}
    free = end is False  # the clip's last frame is only held: its end may take whichever form is nearest
    end = {} if free else (end or {})
    bones = set().union(*[k.keys() for k in keys])
    out = [dict(k) for k in keys]
    for b in bones:
        if b in NOWRAP or b not in _REST:
            continue
        vals = [_rot_of(k.get(b)) for k in keys]
        fixed = _path(b, vals, start.get(b, (0.0, 0.0, 0.0)), None if free else end.get(b, (0.0, 0.0, 0.0)))
        for i, k in enumerate(keys):
            if b not in k:
                continue
            v = k[b]
            out[i][b] = {**v, "rot": fixed[i]} if isinstance(v, dict) else fixed[i]
    return out


def _canon(p):
    return {b: tuple(((c + 180) % 360) - 180 for c in _rot_of(v)) for b, v in p.items() if b not in NOWRAP}


def _rot_of(v):
    if v is None:
        return (0.0, 0.0, 0.0)
    return v["rot"] if isinstance(v, dict) else v


def _unwrap(mv):
    w, a, rc = mv.stages.get("windup", []), mv.stages.get("active", []), mv.stages.get("recover", [])
    seq = [p for _, p in w] + [p for _, p in a] + ([mv.through] if mv.through is not None else []) + [p for _, p in rc]
    fixed = _settle_path(seq)
    k = 0
    for st, ks in (("windup", w), ("active", a)):
        mv.stages[st] = [(f, fixed[k + i]) for i, (f, _) in enumerate(ks)]
        k += len(ks)
    if mv.through is not None:
        mv.through = fixed[k]
        k += 1
    mv.stages["recover"] = [(f, fixed[k + i]) for i, (f, _) in enumerate(rc)]
    return mv


def _keyed(r, name, length, keys, loop=False, held=False):
    ps = [p for _, p in keys]
    first, last = _canon(ps[0]), (False if held else _canon(ps[-1] if not loop else ps[0]))
    ps = _settle_path(ps, first, last)
    keyed(r, name, length, [(t, p) for (t, _), p in zip(keys, ps)], loop=loop)


def _quat(M):
    tr = M[0][0] + M[1][1] + M[2][2]
    if tr > 0:
        S = math.sqrt(tr + 1.0) * 2
        return (0.25 * S, (M[2][1] - M[1][2]) / S, (M[0][2] - M[2][0]) / S, (M[1][0] - M[0][1]) / S)
    if M[0][0] > M[1][1] and M[0][0] > M[2][2]:
        S = math.sqrt(1.0 + M[0][0] - M[1][1] - M[2][2]) * 2
        return ((M[2][1] - M[1][2]) / S, 0.25 * S, (M[0][1] + M[1][0]) / S, (M[0][2] + M[2][0]) / S)
    if M[1][1] > M[2][2]:
        S = math.sqrt(1.0 + M[1][1] - M[0][0] - M[2][2]) * 2
        return ((M[0][2] - M[2][0]) / S, (M[0][1] + M[1][0]) / S, 0.25 * S, (M[1][2] + M[2][1]) / S)
    S = math.sqrt(1.0 + M[2][2] - M[0][0] - M[1][1]) * 2
    return ((M[1][0] - M[0][1]) / S, (M[0][2] + M[2][0]) / S, (M[1][2] + M[2][1]) / S, 0.25 * S)


def _qmat(q):
    w, x, y, z = q
    return ((1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)),
            (2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)),
            (2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)))


def _slerp(bone, ra, rb, f):
    """The turn f of the way from ra to rb (degrees over the bone's rest) the short way round, as one turn and not angle
    by angle; f past 0 or 1 carries on along the same arc."""
    rest = _REST.get(bone, (0.0, 0.0, 0.0))
    qa = _quat(R._rot(*((rest[i] + ra[i]) * DEG for i in range(3))))
    qb = _quat(R._rot(*((rest[i] + rb[i]) * DEG for i in range(3))))
    dt = sum(x * y for x, y in zip(qa, qb))
    if dt < 0:
        qb, dt = tuple(-c for c in qb), -dt
    th = math.acos(min(1.0, dt))
    if th < 1e-5:
        q = qa
    else:
        sa, sb = math.sin((1 - f) * th) / math.sin(th), math.sin(f * th) / math.sin(th)
        q = tuple(sa * x + sb * y for x, y in zip(qa, qb))
    n = math.sqrt(sum(c * c for c in q))
    e = core._euler(_qmat(tuple(c / n for c in q)))
    return tuple(e[i] / DEG - rest[i] for i in range(3))


def _mix(a, b, f):
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        ra, rb = _rot_of(va), _rot_of(vb)
        if k in NOWRAP or k not in _REST:
            rot = tuple(x + (y - x) * f for x, y in zip(ra, rb))
        else:
            rot = _slerp(k, ra, rb, f)
        if isinstance(va, dict) or isinstance(vb, dict):
            pa = va.get("pos", (0, 0, 0)) if isinstance(va, dict) else (0, 0, 0)
            pb = vb.get("pos", (0, 0, 0)) if isinstance(vb, dict) else (0, 0, 0)
            out[k] = {"rot": rot, "pos": tuple(x + (y - x) * f for x, y in zip(pa, pb))}
        else:
            out[k] = rot
    return out


def _back(r, end, settle=None):
    home = settle or _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _on(r, end, f=0.1):
    return _mix(end, _pose(r), -f)


def _ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


# ---- scale: a shadow stretches ----------------------------------------------------------------------------------------
# A move's stretches ride on it as mv._scale {bone: [(stage, fraction, (sx, sy, sz))]} (a bare (0.0, s) is its start),
# written into its clip as scale tracks once the clip is made. The throne lies folded into the pool in every clip but
# its own.


def _scale_track(r, clip, bone, pts, mode="cat"):
    r.clips[clip].scale(bone, [(t, v[0], v[1], v[2]) for t, v in sorted(pts)], mode)


def _move_scale(r, mv):
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    span = {"windup": W, "active": A, "recover": R_}
    for bone, keys in getattr(mv, "_scale", {}).items():
        pts = [(0.0, k[1]) if len(k) == 2 else (t0[k[0]] + k[1] * span[k[0]], k[2]) for k in keys]
        if pts[-1][0] < W + A + R_ - 1e-6:
            pts.append((W + A + R_, (1.0, 1.0, 1.0)))
        _scale_track(r, core.clip_name(mv), bone, pts, getattr(mv, "_mode", "cat"))


def _fold_throne(r):
    for name, c in r.clips.items():
        if ("throne", "scale") not in c.tracks:
            c.scale("throne", [(0.0, 1.0, HIDE, 1.0), (c.length, 1.0, HIDE, 1.0)], "cat")


ONE = (1.0, 1.0, 1.0)


def _waver(r, clip, amount=0.025, hz=0.7, ramp=0.25):
    """A shadow wavers as the candle behind it moves: a slow stretch and narrowing of the whole figure, written into a
    clip after it is made, faded at its ends (a loop's ends meet)."""
    c = r.clips[clip]
    L, n = c.length, int(round(c.length / core.TICK))
    old = c.tracks.get(("base", "scale"))
    keys = []
    for k in range(n + 1):
        t = min(L, k * core.TICK)
        e = 1.0 if c.loop else max(0.0, min(1.0, t / ramp, (L - t) / ramp))
        w = 2 * math.pi * hz * t
        base = list(R._interp(old, t)) if old else [1.0, 1.0, 1.0]
        s = amount * e * (math.sin(w) + 0.5 * math.sin(2.3 * w + 1.0))
        keys.append((t, (base[0] * (1 - 0.6 * s), base[1] * (1 + s), base[2]), "cat"))
    if c.loop:
        keys[-1] = (keys[-1][0], keys[0][1], "cat")
    c.tracks[("base", "scale")] = keys


# ---- stance and locomotion -------------------------------------------------------------------------------------------

def idle(r):
    """It stands tall with its point low before it: its fingers flex one after another and spread, its head tilts to
    look you over, its sword's point traces slow circles near the floor, its cape stirs, and once a loop it rolls its
    shoulders and straightens, taller, like a shadow when the candle flares."""
    L, n = 6.0, 30
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        flare = math.sin(math.pi * min(1.0, max(0.0, (u - 0.55) / 0.2))) if 0.55 < u < 0.75 else 0.0
        keys.append((L * u, _pose(r, at=(0.4 * math.sin(w), -0.4 + 0.4 * math.cos(2 * w) + 1.2 * flare, 0), hips=(0, 3 * math.sin(w), 0),
                                  spine=(-1.5 * flare, 0, 0), chest=(-2 * flare + 1.0 * math.sin(2 * w), -4 * math.sin(w + 0.5), 1.5 * math.sin(w)),
                                  neck=(0, 6 * math.sin(w - 0.6), 0), head=(-5 * flare + 3 * math.sin(2 * w + 1), 10 * math.sin(w - 0.9), 8 * math.sin(w + 1.6)),
                                  right=(-7 + 1.2 * math.cos(w * 2), 12.5 + 1.2 * math.sin(2 * w) + 2 * flare, -5 + 1.0 * math.sin(w)),
                                  rblade=((-0.12 + 0.08 * math.cos(2 * w), 0.25 + 0.1 * math.sin(2 * w), -0.96), None),
                                  left=(7.5 + 0.8 * math.sin(w), 12 + 1.5 * math.sin(2 * w + 2) + 2 * flare, -1.5 + 1.5 * math.cos(w)),
                                  curl=0.25 * (1 + math.sin(3 * w)) - 0.3 * flare, spread=0.4 + 0.5 * math.sin(3 * w + 1.4),
                                  cape=(3 * math.sin(w + 1.0) + 4 * flare, 3 * math.sin(w + 0.2), 2 * math.sin(w - 0.4)))))
    _keyed(r, "idle", L, keys, loop=True)


def _stride(u, D, lift=4.0, stance=0.55):
    """A long gliding stride at phase u (the right foot planting at 0, the left at 0.5): offsets from where each foot
    stands at rest (model px, y down), the body travelling D pixels a cycle."""
    out = {}
    half = stance * D / 2
    for sd, ph in (("r", 0.0), ("l", 0.5)):
        v = (u - ph) % 1.0
        if v < stance:
            z, dy = -half + 2 * half * v / stance, 0.0
        else:
            k = (v - stance) / (1 - stance)
            z, dy = half - 2 * half * _ease(k), -lift * math.sin(math.pi * k)
        out[sd] = (0.0, dy, z - FOOT[sd][2] * 0.0)
    return out


def walk(r):
    """It walks slow and long, its head level as if it glided, its long arms hanging and swinging a little behind the
    stride, the point trailing low, its cape drawn back."""
    r.walk_stride = 1.2
    D = r.walk_stride * 16
    T = 1.1
    keys = []
    n = 16
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(4 * math.pi * u)
        keys.append((T * u, _pose(r, at=(0, -0.6 + 0.3 * c, 0), hips=(0, 6 * s, 0), chest=(2, -7 * s, 0), head=(-2, 4 * s, 0),
                                  right=(-7, 13, -4 + 4 * s), rblade=((-0.12, 0.35, -0.93), None), left=(7.5, 12.5, -1 - 5 * s),
                                  curl=0.2, spread=0.3, cape=(8, 4 + 2 * c, 0), step=_stride(u, D, lift=3.5))))
    _keyed(r, "walk", T, keys, loop=True)


def run(r):
    """Its run: bent forward, its strides long, its sword carried low behind it and its left hand reaching ahead, the
    cape streaming."""
    r.run_stride = 2.6
    D = r.run_stride * 16
    T = 0.7
    keys = []
    n = 12
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(4 * math.pi * u)
        keys.append((T * u, _pose(r, at=(0, -2 + 1.0 * c, -1), hips=(10, 8 * s, 0), spine=(6, 0, 0), chest=(8, -10 * s, 0), neck=(-6, 0, 0), head=(-10, 5 * s, 0),
                                  right=(-8, 15 + 2 * s, 8), rblade=((-0.15, 0.15, 0.97), None), left=(6, 24 - 3 * s, -14 + 4 * s), lblade=((0.1, 0.2, -1), None),
                                  curl=-0.2, spread=0.8, cape=(28 + 4 * c, 10, 3 * s), step=_stride(u, D, lift=6.0, stance=0.4))))
    _keyed(r, "run", T, keys, loop=True)


def turns(r):
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        a = _pose(r, hips=(0, 6 * sgn, 0), at=(0, -0.4, 0), head=(0, 8 * sgn, 0), step={"r": (-1.5 * sgn, -2.5, -1)}, cape=(2, 2, -3 * sgn))
        b = _pose(r, hips=(0, 3 * sgn, 0), head=(0, 4 * sgn, 0), cape=(1, 1, -2 * sgn))
        c = _pose(r, hips=(0, 6 * sgn, 0), at=(0, -0.4, 0), head=(0, 8 * sgn, 0), step={"l": (1.5 * sgn, -2.5, 1)}, cape=(2, 2, -3 * sgn))
        _keyed(r, nm, 0.6, [(0, _pose(r)), (0.15, a), (0.3, b), (0.45, c), (0.6, _pose(r))], loop=True)


# ---- the throne, the reactions, the death ------------------------------------------------------------------------------

# seated on its throne (whole turns of the legs, the hips back over the seat): its knees up, its shins down to the floor
SIT_LEGS = {"thigh_l": (-84, 0, -6), "shin_l": (84, 0, 0), "foot_l": (0, 0, 0), "thigh_r": (-88, 0, 4), "shin_r": (90, 0, 0), "foot_r": (-2, 0, 0)}


def _seated(r, lean=0.0, look=0.0, tap=0.0, drum=0.0):
    """Sitting the way you sit: back in its throne, its left elbow on the arm and its chin on its long fingers, its
    sword leant out before it with its point on the floor and its right hand on the pommel; look turns its head to
    you, tap lifts its right foot's toe (impatient), drum lifts its fingers off its cheek one way or the other and turns
    the pommel in its hand."""
    legs = dict(SIT_LEGS)
    legs["foot_r"] = (-2 - 16 * tap, 0, 0)
    legs["shin_r"] = (90 - 3 * tap, 0, 0)
    return _pose(r, at=(0, -7.0, 6.0), legs=legs, spine=(-6 + 4 * lean, 0, 0), chest=(-4 + 6 * lean + 1.5 * drum, 8 * lean, -3), neck=(4, -6 + 14 * look, 0),
                 head=(10 - 8 * look, -10 + 20 * look, -12 + 10 * look - 3 * drum), right=(-6, 18, -5), rblade=((0.1 + 0.2 * drum, 0.66, -0.74), None),
                 left=("on", "head", (1.5, -1, -2.5)), lblade=((-0.2, -1, -0.3), None), curl=0.4 - 0.45 * drum, spread=0.2 + 0.5 * abs(drum), cape=(-6 + 2 * lean, 0, 0))


def reactions(r):
    """Struck, its whole shape ripples: it bends back from the blow, narrows and stretches, and snaps back; from the
    side it bends away. Its stance broken, its throne rises out of the pool behind it and it sits; it stands up out
    of it and the throne sinks."""
    hit = _pose(r, at=(0, 0, 2.5), hips=(-4, 0, 0), spine=(-6, 0, 0), chest=(-10, 0, 0), head=(-14, 0, 0), right=(-9, 17, -1), rblade=((-0.3, 0.4, -0.85), None),
                left=(9, 17, 2), curl=-0.4, spread=1.0, cape=(-8, -6, 0))
    back = _pose(r, at=(0, -0.6, -0.8), hips=(2, 0, 0), spine=(2, 0, 0), chest=(3, 0, 0), head=(4, 0, 0), right=(-7, 12, -6), rblade=((-0.12, 0.3, -0.95), None),
                 curl=0.4, cape=(6, 4, 0))
    _keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    r.clips["hurt"].scale("base", [(0, 1, 1, 1), (0.08, 0.86, 1.08, 1), (0.2, 1.08, 0.96, 1), (0.34, 0.98, 1.01, 1), (0.55, 1, 1, 1)], "cat")
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):
        h = _pose(r, at=(-2 * sgn, 0, 1), hips=(0, -6 * sgn, -6 * sgn), spine=(0, 0, -6 * sgn), chest=(-4, -6 * sgn, -10 * sgn), head=(-6, -8 * sgn, -12 * sgn),
                  curl=-0.3, spread=0.9, cape=(0, 0, 10 * sgn))
        b = _pose(r, at=(0.6 * sgn, 0, 0), hips=(0, 2 * sgn, 2 * sgn), chest=(1, 2 * sgn, 3 * sgn), head=(2, 3 * sgn, 4 * sgn), curl=0.3, cape=(0, 0, -4 * sgn))
        _keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    sag = _pose(r, at=(0, -3, 2), spine=(-4, 0, 0), chest=(-2, 0, 0), head=(6, 0, 0), right=(-6, 14, -4), rblade=((0.1, 0.45, -0.89), None), curl=0.3, cape=(-2, 0, 0))
    _keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.35, sag), (0.9, _seated(r))])
    r.clips["kneel"].scale("throne", [(0, 1, HIDE, 1), (0.25, 1, 0.5, 1), (0.6, 1, 1.04, 1), (0.9, 1, 1, 1)], "cat")
    push = _pose(r, at=(0, -4, 3), hips=(8, 0, 0), spine=(8, 0, 0), chest=(6, 0, 0), head=(-4, 0, 0), right=(-5, 16, -6), rblade=((0.1, 0.45, -0.89), None),
                 step={"l": (0, 0, 1), "r": (0, 0, 1)}, cape=(-2, 0, 0))
    _keyed(r, "rise", 1.0, [(0, _seated(r)), (0.45, push), (1.0, _pose(r))])
    r.clips["rise"].scale("throne", [(0, 1, 1, 1), (0.4, 1, 1, 1), (0.75, 1, 0.3, 1), (1.0, 1, HIDE, 1)], "cat")
    death(r)


def _near_form(bone, v, ref):
    """v written in the form (either Euler triple, whole turns) nearest ref."""
    return min(_forms(bone, v), key=lambda w: sum((w[i] - ref[i]) ** 2 for i in range(3)))


def _lay(r, pose, bone, at, turn, ref=None):
    """The pose with a loose part (the crown, the sword) put at a place in the room, (x, height, z), turned by turn
    (degrees, Rz·Ry·Rx in the room's frame), wherever its parent lies in that pose; its turn written as near as it goes
    to ref (the key before it)."""
    fr = core.frames(r, core.as_sample(pose))
    b = r.by_name[bone]
    Mp, Tp = fr[b.parent.name]
    q = ik.model(at)
    loc = R._mv(core._t(Mp), tuple(q[i] - Tp[i] for i in range(3)))
    M = R._mm(core._t(Mp), R._rot(*(c * DEG for c in turn)))
    e = core._euler(R._mm(core._t(R._rot(*(c * DEG for c in b.rot))), M))
    out = dict(pose)
    rot = tuple(c / DEG for c in e)
    out[bone] = {"rot": _near_form(bone, rot, ref) if ref is not None else rot, "pos": (loc[0] - b.pivot[0], b.pivot[1] - loc[1], loc[2] - b.pivot[2])}  # a key's pos is written y up
    return out


LEG_BONES = ("foot_l", "foot_r", "shin_l", "shin_r", "thigh_l", "thigh_r")


def _floor_up(r, pose, bones=LEG_BONES, clear=0.2):
    """The pose lifted (the whole of it) just enough that none of bones is under the floor."""
    low = core.lowest(r, core.as_sample(pose), list(bones)) - core.GROUND
    if low <= -clear:
        return pose
    out = dict(pose)
    b = out.get("base", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
    ps = b.get("pos", (0, 0, 0))
    out["base"] = {"rot": b.get("rot", (0, 0, 0)), "pos": (ps[0], ps[1] + low + clear, ps[2])}
    return out


def death(r):
    """Its last: it reels back with its arms flung out; its sword falls from its hand; it sinks to its knees, reaching
    up with its long fingers; its crown slips off and falls; then it lies back down into its own shadow on the floor
    and flattens into it, the last of it its hand (5 s)."""
    reel = _pose(r, at=(0, 0.5, 3), hips=(-6, 10, 0), spine=(-8, 0, 0), chest=(-14, 12, 0), head=(-20, 14, 8), right=(-14, 26, 4), rblade=((-0.8, -0.2, 0.5), None),
                 left=(13, 27, 4), curl=-0.6, spread=1.2, cape=(-10, -6, 0))
    drop = dict(_pose(r, at=(0, -3, 2), hips=(-2, 6, 0), chest=(-6, 6, 0), head=(-10, 6, 6), right=(-10, 18, -2), left=(11, 24, -2), curl=-0.3, spread=1.0, cape=(-4, 0, 0)))
    # its sword falls out of its hand and lies on the floor before it, where it stays
    FALLEN = ((-12, 1.3, -10), (90, 212, 0))
    drop = _lay(r, drop, "sword", (-12, 7, -8), (78, 208, 12))
    kneel_legs = {"thigh_l": (-20, 0, -6), "shin_l": (116, 0, 0), "foot_l": (-100, 0, 0), "thigh_r": (-26, 0, 6), "shin_r": (120, 0, 0), "foot_r": (-100, 0, 0)}
    knees = dict(_pose(r, at=(0, -8.0, 3), legs=kneel_legs, spine=(-8, 0, 0), chest=(-12, -6, 0), head=(-26, -8, 0), right=(-8, 22, -6), left=(6, 40, -6),
                       lblade=((0.1, -1, -0.2), None), curl=-0.5, spread=1.3, cape=(-4, 0, 0)))
    knees = _lay(r, knees, "sword", *FALLEN, ref=drop["sword"]["rot"])
    slip = _lay(r, _lay(r, knees, "sword", *FALLEN, ref=knees["sword"]["rot"]), "crown", (9, 10, 4), (-30, 20, 50))
    lie_legs = {"thigh_l": (-30, 0, -6), "shin_l": (40, 0, 0), "foot_l": (0, 0, 0), "thigh_r": (-34, 0, 6), "shin_r": (46, 0, 0), "foot_r": (0, 0, 0)}
    back_ = dict(_pose(r, base=(-50, 0, 0), lift=2.8, at=(0, -6, 0), legs=lie_legs, spine=(-10, 0, 0), chest=(-10, 0, 0), head=(-10, 0, 0), right=(-10, 18, 6),
                       left=(8, 34, -10), curl=-0.4, spread=1.0, cape=(6, 2, 0), feet=()))
    back_ = _lay(r, back_, "sword", *FALLEN, ref=slip["sword"]["rot"])
    back_ = _lay(r, back_, "crown", (12, 3.9, 10), (0, 40, 85), ref=slip["crown"]["rot"])
    flat_legs = {"thigh_l": (0, 0, -4), "shin_l": (0, 0, 0), "foot_l": (0, 0, 0), "thigh_r": (0, 0, 4), "shin_r": (0, 0, 0), "foot_r": (0, 0, 0)}
    flat = dict(_pose(r, base=(-88, 0, 0), lift=2.5, legs=flat_legs, spine=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0), feet=(), cape=(4, 2, 0),
                      more={"arm_r": _abs(r, "arm_r", (0, 0, -30)), "forearm_r": _abs(r, "forearm_r", (0, 0, 0)), "arm_l": _abs(r, "arm_l", (-150, 0, 10)),
                            "forearm_l": _abs(r, "forearm_l", (-20, 0, 0))}, curl=-0.2, spread=0.8))
    flat = _lay(r, flat, "sword", *FALLEN, ref=back_["sword"]["rot"])
    flat = _lay(r, flat, "crown", (13, 3.9, 14), (0, 60, 90), ref=back_["crown"]["rot"])
    last = dict(flat)
    last.update({f"lf{k}": (70, 0, 0) for k in range(3)})
    tip = []
    for t, f in ((2.15, 0.25), (2.3, 0.5), (2.45, 0.75)):
        q = _floor_up(r, _mix(slip, back_, f))
        tip.append((t, _lay(r, q, "sword", *FALLEN, ref=slip["sword"]["rot"])))
    landed = _lay(r, _mix(drop, knees, 0.45), "sword", (-12, 1.6, -10), (86, 214, 4), ref=drop["sword"]["rot"])
    _keyed(r, "death", 5.0, [(0, _pose(r)), (0.35, reel), (0.8, drop), (1.05, landed), (1.4, knees), (2.0, slip)] + tip + [(2.6, _floor_up(r, back_)), (3.1, flat), (5.0, last)], held=True)
    r.clips["death"].scale("base", [(0, 1, 1, 1), (3.1, 1, 1, 1), (3.5, 1, 1, 0.7), (4.4, 1, 1, 0.3), (5.0, 1, 1, 0.12)], "cat")


# ---- poses as specs: the sword moves glide between them ----------------------------------------------------------------
# A spec is _pose's keywords. Between two specs the hands travel straight and the blade's line turns between its two,
# solved fresh at every key, so a recovery carries the sword back over the floor and not through it.

REST_SPEC = {"right": GRIP["r"], "rblade": (REST_HAND["r"][0], None), "left": GRIP["l"], "lblade": (REST_HAND["l"][0], None)}
SPEC_ZERO = {"base": (0, 0, 0), "lift": 0.0, "fwd": 0.0, "hips": (0, 0, 0), "at": (0, 0, 0), "spine": (0, 0, 0), "chest": (0, 0, 0), "neck": (0, 0, 0),
             "head": (0, 0, 0), "curl": 0.0, "spread": 0.0, "cape": (0, 0, 0), "crown": (0, 0, 0), "step": {}}


def P(r, sp):
    return _pose(r, **sp)


def _resolve(r, sp):
    """The spec with each hand put on a bone ("on") written as the place that puts it (it can then glide)."""
    on = [k for k in ("right", "left") if isinstance(sp.get(k), tuple) and sp[k] and sp[k][0] == "on"]
    if not on:
        return sp
    fr = core.frames(r, core.as_sample(_pose(r, **sp)))
    out = dict(sp)
    for k in on:
        out[k] = ik.at(fr, sp[k][1], sp[k][2])
    return out


def _lerpv(va, vb, f):
    if isinstance(va, (int, float)):
        return va + (vb - va) * f
    if isinstance(va, dict):
        return {k: _lerpv(va.get(k, (0, 0, 0)), vb.get(k, (0, 0, 0)), f) for k in set(va) | set(vb)}
    if isinstance(va[0], tuple):  # a blade's line: (ydir, xdir)
        ya, yb = ik.unit(va[0]), ik.unit(vb[0])
        y = tuple(ya[i] + (yb[i] - ya[i]) * f for i in range(3))
        x = None if va[1] is None and vb[1] is None else tuple(_lerpv(va[1] or vb[1], vb[1] or va[1], f))
        return (y, x)
    if isinstance(va[0], str):
        return va
    return tuple(va[i] + (vb[i] - va[i]) * f for i in range(len(va)))


def _glide(r, a, b, f):
    """The pose f of the way from spec a to spec b ({} is rest; f past 0 or 1 carries on). Each hand swings round its
    shoulder, its reach and its bearing from the shoulder each turning between the two (a hand sent straight across
    passes close by the shoulder, and the arm folds up and flings open again); the sword's point is kept over the floor."""
    a, b = _resolve(r, {**REST_SPEC, **a}), _resolve(r, {**REST_SPEC, **b})
    out = {}
    for k in set(a) | set(b):
        if k in ("legs", "more", "feet"):
            assert a.get(k) == b.get(k), k
            out[k] = a.get(k)
            continue
        out[k] = _lerpv(a.get(k, SPEC_ZERO.get(k)), b.get(k, SPEC_ZERO.get(k)), f)
    fa = core.frames(r, core.as_sample(_pose(r, **a)))
    fb = core.frames(r, core.as_sample(_pose(r, **b)))
    for side, key in (("r", "right"), ("l", "left")):
        sa, sb = ik.at(fa, "shoulder_" + side), ik.at(fb, "shoulder_" + side)
        va = tuple(a[key][i] - sa[i] for i in range(3))
        vb = tuple(b[key][i] - sb[i] for i in range(3))
        la, lb = math.sqrt(ik.dot(va, va)), math.sqrt(ik.dot(vb, vb))
        ua, ub = ik.unit(va), ik.unit(vb)
        th = math.acos(max(-1.0, min(1.0, ik.dot(ua, ub))))
        if th < 1e-4 or th > math.pi - 1e-3:
            u = ik.unit(tuple(ua[i] + (ub[i] - ua[i]) * f for i in range(3)))
        else:
            ka, kb = math.sin((1 - f) * th) / math.sin(th), math.sin(f * th) / math.sin(th)
            u = tuple(ka * ua[i] + kb * ub[i] for i in range(3))
        L = la + (lb - la) * f
        out[key] = tuple(sa[i] + (sb[i] - sa[i]) * f + u[i] * L for i in range(3))
    y = ik.unit(out["rblade"][0])
    most = max(0.0, (out["right"][1] - 2.5) / BLADE)  # how far down its line may fall (model y is down)
    if y[1] > most:
        hz = math.sqrt(max(1e-9, y[0] ** 2 + y[2] ** 2))
        k = math.sqrt(max(0.0, 1 - most * most)) / hz
        out["rblade"] = ((y[0] * k, most, y[2] * k), out["rblade"][1])
    return _pose(r, **{k: v for k, v in out.items() if v is not None})


def _from(r, sp, f=0.35):
    return _glide(r, {}, sp, f)


def _home(r, sp, via=None):
    """A move's recovery from spec sp: two keys back toward rest (through via, when given)."""
    if via is None:
        return [(0.45, _glide(r, sp, {}, 0.45)), (0.7, _glide(r, sp, {}, 0.82))]
    return [(0.4, _glide(r, sp, via, 0.7)), (0.7, _glide(r, via, {}, 0.55))]


def _past(r, sp, f=0.05):
    return _glide(r, sp, {}, -f)


# ---- the moves ---------------------------------------------------------------------------------------------------------

SWORD_HIT = [("sword", TIP, 2.0), ("sword", (0, 15, 0), 2.0)]
FINGER_HIT = [("lf1", (0, 7, 0), 2.0), ("hand_l", (0, 2, 0), 2.0)]


def cut(r):
    """The Cut: its sword drawn up and back past its right shoulder, its body turned after it; then down and across in
    front of it to its left hip, a long step in under it."""
    raise_ = dict(hips=(0, -10, 0), at=(0, -0.6, 1.5), chest=(-4, -24, -4), head=(2, 16, 0), right=(-9, 34, 3), rblade=((-0.25, -0.85, 0.45), None),
                  left=(9, 18, -4), curl=0.2, spread=0.6, cape=(-2, 0, 4), step={"r": (0, 0, 1)})
    hit = dict(hips=(4, 14, 0), at=(0, -2.5, -3), spine=(3, 6, 0), chest=(8, 28, 4), head=(2, -18, 0), right=(5, 17, -16), rblade=((0.65, 0.4, -0.65), None),
               left=(9, 20, 4), curl=-0.2, spread=1.0, cape=(8, 4, -4), step={"r": (0, 0, -6), "l": (0, 0, -1.5)})
    after = dict(hips=(5, 18, 0), at=(0, -3, -3.4), spine=(3, 8, 0), chest=(9, 34, 5), head=(2, -22, 0), right=(9, 15, -10), rblade=((0.85, 0.3, -0.4), None),
                 left=(10, 21, 5), curl=-0.2, spread=1.0, cape=(10, 5, -5), step={"r": (0, 0, -6), "l": (0, 0, -1.5)})
    return Move("cut", "LIGHT", 11, 4, 15, title="Cut", track=8.0, rng=(0, 5.0), kinds=("combo", "near"), damage=1.0, knock=0.6, plant=(), hit=SWORD_HIT,
                lag=ON_TIME, through=_past(r, after), stages={"windup": [(0.3, _from(r, raise_)), (1.0, P(r, raise_))], "active": [(0.6, P(r, hit)), (1.0, P(r, after))],
                                                              "recover": _home(r, after)})


def backcut(r):
    """The Backcut: its sword low at its left hip with the point back; then it whips up across in front of it to its
    right shoulder, the back of the hand leading, as its body turns back through."""
    low = dict(hips=(2, 14, 0), at=(0, -2, 0), chest=(6, 26, 3), head=(0, -18, 0), right=(7, 17, -2), rblade=((0.45, 0.08, 0.89), None), left=(10, 20, 3),
               curl=0.0, spread=0.8, cape=(4, 2, -4))
    hit = dict(hips=(-2, -14, 0), at=(0, -1, -3), chest=(-6, -26, -4), head=(-4, 16, 0), right=(-11, 34, -15), rblade=((-0.6, -0.55, -0.6), None),
               left=(8, 18, -2), curl=0.2, spread=0.6, cape=(6, 4, 5), step={"r": (0, 0, -5)})
    after = dict(hips=(-2, -17, 0), at=(0, -0.8, -3.2), chest=(-6, -32, -5), head=(-4, 20, 0), right=(-13, 37, -9), rblade=((-0.75, -0.6, -0.2), None),
                 left=(8, 18, -1), curl=0.2, spread=0.6, cape=(7, 4, 6), step={"r": (0, 0, -5)})
    return Move("backcut", "LIGHT", 9, 4, 15, title="Backcut", track=8.0, rng=(0, 5.0), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(), hit=SWORD_HIT,
                lag=ON_TIME, through=_past(r, after), stages={"windup": [(0.3, _from(r, low)), (1.0, P(r, low))], "active": [(0.6, P(r, hit)), (1.0, P(r, after))],
                                                              "recover": _home(r, after)})


def reach(r):
    """The Long Reach (HEAVY, a hold): it draws its sword back beside its head with the point at you and sinks back,
    its arm drawn in short; then it thrusts, and its arm stretches out long as a shadow does at dusk, the point reaching
    you from twice as far as it should; the arm snaps back short."""
    coil = dict(hips=(-4, -16, 0), at=(0, -3, 3), spine=(-4, 0, 0), chest=(-6, -20, 0), head=(4, 18, 0), right=(-7, 31, 3), rblade=((0.05, 0.12, -1), None),
                left=(6, 28, -11), lblade=((0.1, 0.1, -1), None), curl=-0.3, spread=1.0, cape=(-4, -2, 0), step={"l": (0, 0, 1.5)})
    deep = dict(coil, at=(0, -3.6, 3.6), hips=(-5, -18, 0), spine=(-5, 0, 0), chest=(-8, -22, 0), head=(6, 20, 0), right=(-7, 32, 4.5), left=(6, 28.5, -12),
                curl=-0.35, spread=1.1, cape=(-5, -2, 0))
    hit = dict(hips=(6, 10, 0), at=(0, -4, -5), spine=(5, 0, 0), chest=(8, 14, 0), head=(-6, -10, 0), right=(-3, 27, -22), rblade=((0.02, 0.1, -1), None),
               left=(9, 22, 6), curl=-0.2, spread=1.0, cape=(10, 6, 0), step={"r": (0, 0, -9), "l": (0, 0, -2)})
    after = dict(hit, hips=(7, 11, 0), at=(0, -4.5, -5.6), chest=(9, 15, 0), head=(-6, -11, 0), right=(-3, 26.5, -23), rblade=((0.02, 0.12, -1), None),
                 left=(9, 21, 7), cape=(12, 7, 0), step={"r": (0, 0, -9.5), "l": (0, 0, -2)})
    mv = Move("reach", "HEAVY", 16, 5, 20, title="Long Reach", hold=8, track=6.0, rng=(0, 7.5), kinds=("near", "mid"), damage=1.4, knock=1.0, plant=(),
              hit=SWORD_HIT + [("hand_r", (0, 2, 0), 2.0)], lag=ON_TIME, through=P(r, after),
              stages={"windup": [(0.3, _from(r, coil)), (0.75, P(r, coil)), (1.0, P(r, deep))], "active": [(0.6, P(r, hit)), (1.0, P(r, after))],
                      "recover": [(0.4, _glide(r, after, {}, 0.5)), (0.72, _glide(r, after, {}, 0.85))]})
    arm = [(0.0, ONE), ("windup", 0.75, (1.0, 0.86, 1.0)), ("windup", 1.0, (1.0, 0.84, 1.0)), ("active", 0.6, (0.9, 1.9, 0.9)), ("active", 1.0, (0.9, 1.95, 0.9)),
           ("recover", 0.3, (0.95, 1.7, 0.95)), ("recover", 0.55, (1.0, 0.9, 1.0)), ("recover", 0.8, (1.0, 1.04, 1.0))]
    mv._scale = {"arm_r": arm, "forearm_r": arm}
    return mv


def rake(r):
    """The Rake: its left hand drawn up high on its left with the long fingers flung open; then it rakes down across in
    front of it, its forearm stretching after you and the fingers closing like a claw. Its sword is kept low before it."""
    up_ = dict(hips=(0, 10, 0), at=(0, -0.5, 1), chest=(-4, 16, 4), head=(-4, -10, 0), left=(9, 37, 0), lblade=((0.2, -1, 0.2), None), curl=-0.8, spread=1.5,
               right=(-9, 13, -1), rblade=((-0.2, 0.3, -0.93), None), cape=(-2, 0, -3))
    hit = dict(hips=(4, -12, 0), at=(0, -2.5, -3), spine=(4, 0, 0), chest=(8, -20, -4), head=(4, 10, 0), left=(-2, 16, -17), lblade=((-0.4, 0.6, -0.7), None),
               curl=0.5, spread=0.8, right=(-6, 14, -8), rblade=((-0.1, 0.3, -0.95), None), cape=(8, 4, 4), step={"l": (0, 0, -4)})
    after = dict(hit, hips=(5, -15, 0), at=(0, -3, -3.4), spine=(5, 0, 0), chest=(9, -24, -5), head=(4, 12, 0), left=(-6, 13.5, -12), lblade=((-0.6, 0.6, -0.4), None),
                 curl=0.8, spread=0.4, right=(-5, 14, -9), cape=(9, 5, 5))
    mv = Move("rake", "LIGHT", 10, 4, 14, title="Rake", track=8.0, rng=(0, 4.5), kinds=("near",), damage=0.9, knock=0.5, plant=(), hit=FINGER_HIT, lag=ON_TIME,
              through=_past(r, after), stages={"windup": [(0.3, _from(r, up_)), (1.0, P(r, up_))], "active": [(0.6, P(r, hit)), (1.0, P(r, after))],
                                               "recover": _home(r, after)})
    stretch = [(0.0, ONE), ("windup", 1.0, (1.0, 1.15, 1.0)), ("active", 0.6, (0.9, 1.55, 0.9)), ("active", 1.0, (0.9, 1.6, 0.9)), ("recover", 0.45, (1.0, 0.94, 1.0)),
               ("recover", 0.75, (1.0, 1.02, 1.0))]
    mv._scale = {"forearm_l": stretch}
    return mv


def parry(r):
    """Your Parry: its sword across before its face, the edge out, its long fingers spread on the flat near the point,
    braced; each blow that lands on it jolts it up."""
    guard = dict(hips=(-3, 10, 0), at=(0, -2, 1.5), chest=(-6, 12, 0), head=(6, -14, 0), right=(-8, 35, -9), rblade=((0.97, -0.12, -0.2), None),
                 left=("on", "sword", (0, 17, 0)), lblade=((0.0, 1, 0.3), None), curl=0.1, spread=1.0, cape=(-3, -2, 0), step={"r": (0, 0, 1)})
    catch = dict(guard, hips=(-4, 11, 0), at=(0, -2.8, 2.4), chest=(-8, 13, 0), head=(4, -14, 0), right=(-8, 36.5, -8), rblade=((0.97, -0.16, -0.18), None),
                 curl=0.15, spread=1.1, cape=(-5, -3, 0), step={"r": (0, 0, 1.5)})
    return Move("parry", "NONE", 8, 12, 12, title="Your Parry", track=8.0, ability="Your Parry", rng=(0, 5.0), kinds=("guard",), damage=0, knock=0, plant=(), lag=ON_TIME,
                through=P(r, guard), stages={"windup": [(0.4, _from(r, guard, 0.6)), (1.0, P(r, guard))],
                                             "active": [(0.25, P(r, catch)), (0.55, P(r, guard)), (0.8, P(r, catch)), (1.0, P(r, guard))],
                                             "recover": [(0.4, _mix(P(r, guard), _pose(r), 0.3)), (0.7, _mix(P(r, guard), _pose(r), 0.75))]})


def riposte(r):
    """The Riposte (after the parry): the blade drawn back low at its right hip with the point at you, and driven
    straight in under your guard with a step, its fingers flung back."""
    draw = dict(hips=(-2, -12, 0), at=(0, -2, 1.5), chest=(-4, -16, 0), head=(4, 12, 0), right=(-9, 19, 4), rblade=((0.05, 0.1, -1), None), left=(8, 24, -6),
                curl=-0.3, spread=1.0, cape=(-3, -2, 0))
    hit = dict(hips=(6, 8, 0), at=(0, -3.5, -5), spine=(4, 0, 0), chest=(6, 10, 0), head=(-4, -8, 0), right=(-3, 21, -24), rblade=((0.02, 0.2, -1), None),
               left=(10, 24, 6), curl=-0.5, spread=1.2, cape=(9, 5, 0), step={"r": (0, 0, -8), "l": (0, 0, -1.5)})
    after = dict(hit, hips=(7, 9, 0), at=(0, -4, -5.5), chest=(7, 11, 0), head=(-4, -9, 0), right=(-3, 20.5, -25), rblade=((0.02, 0.25, -1), None),
                 left=(10, 23, 7), cape=(10, 6, 0), step={"r": (0, 0, -8.5), "l": (0, 0, -1.5)})
    return Move("riposte", "LIGHT", 8, 4, 14, title="Riposte", track=10.0, rng=(0, 5.0), kinds=("near",), damage=1.0, knock=0.8, plant=(), hit=SWORD_HIT, lag=ON_TIME,
                through=_past(r, after), stages={"windup": [(0.3, _from(r, draw, 0.4)), (1.0, P(r, draw))], "active": [(0.6, P(r, hit)), (1.0, P(r, after))],
                                                 "recover": _home(r, after)})


OVERHEAD = {"arm_r": (190, 0, 6), "forearm_r": (0, 0, 0), "arm_l": (186, 0, -6), "forearm_l": (0, 0, 0)}  # both arms straight up (whole turns)
FLAT_LEGS = {"thigh_l": (0, 0, -3), "shin_l": (0, 0, 0), "foot_l": (60, 0, 0), "thigh_r": (0, 0, 3), "shin_r": (0, 0, 0), "foot_r": (60, 0, 0)}


def charge(r):
    """Your Charge (HEAVY, a gap closer): it leans toward you with its arms flung up over its head, and falls flat along
    the floor as a cast shadow falls, stretched long, its sword out ahead of it; it slides at you over the floor, and
    stands up out of the floor in front of you all at once, its arms coming over with it, and brings its sword down."""
    lean = _pose(r, base=(18, 0, 0), at=(0, -1, 0), spine=(4, 0, 0), chest=(4, 0, 0), head=(-10, 0, 0), legs={"thigh_l": (-6, 0, -3), "thigh_r": (-6, 0, 3)}, feet=(),
                 more={b: _abs(r, b, v) for b, v in OVERHEAD.items()}, curl=-0.2, spread=1.0, cape=(-10, -6, 0))
    lie = lambda k=0.0: dict(_pose(r, base=(86 + 1.2 * k, 0, 0), lift=2.5, legs=FLAT_LEGS, feet=(), head=(-6 + 2 * k, 0, 0), more={b: _abs(r, b, v) for b, v in OVERHEAD.items()},
                                   curl=-0.2, spread=1.0 + 0.2 * k, cape=(-6 + 3 * k, -4, 2 * k)))
    act = [(0.09, _floor_up(r, _mix(lean, lie(0.0), 0.5))), (0.18, _floor_up(r, lie(0.0))), (0.4, lie(1.0)), (0.58, lie(-1.0))]
    risen = _pose(r, base=(-6, 0, 0), at=(0, 1, -2), spine=(-4, 0, 0), chest=(-6, 0, 0), head=(4, 0, 0), right=(-4, 46, -6), rblade=((0.0, -1, 0.1), None),
                  left=(4, 44, -6), curl=-0.4, spread=1.2, cape=(-20, -10, 0), step={"r": (0, 0, -3)})
    chop = _pose(r, hips=(6, 4, 0), at=(0, -3.5, -3), spine=(6, 0, 0), chest=(10, 4, 0), head=(-6, -4, 0), right=(-3, 15, -19), rblade=((0.0, 0.4, -0.92), None),
                 left=(7, 22, 4), curl=0.0, spread=0.8, cape=(10, 6, 0), step={"r": (0, 0, -7), "l": (0, 0, -1)})
    hinge = _pose(r, base=(46, 0, 0), lift=0.5, spine=(-2, 0, 0), head=(-24, 0, 0), more={b: _abs(r, b, v) for b, v in OVERHEAD.items()}, curl=-0.3, spread=1.1,
                  cape=(-12, -6, 0))
    act += [(0.635, _floor_up(r, _mix(lie(-1.0), hinge, 0.5))), (0.69, _floor_up(r, hinge)), (0.8, risen), (1.0, chop)]
    after = _mix(chop, _pose(r), -0.06)
    mv = Move("charge", "HEAVY", 16, 16, 20, title="Your Charge", track=6.0, lock=12, advance=8.0, ability="Your Charge", rng=(5, 16), kinds=("gap",), damage=1.3,
              knock=1.2, plant=(), hit=SWORD_HIT + [("head", (0, -3, 0), 3.0)], lag=ON_TIME, through=after,
              stages={"windup": [(0.3, _mix(_pose(r), lean, 0.4)), (1.0, lean)], "active": act, "recover": _back(r, after)})
    mv._scale = {"base": [(0.0, ONE), ("windup", 1.0, ONE), ("active", 0.18, (1.0, 1.25, 1.0)), ("active", 0.58, (1.0, 1.3, 1.0)), ("active", 0.8, (1.0, 1.05, 1.0)),
                          ("active", 1.0, ONE)]}
    return mv


def turnabout(r):
    """The Turnabout (for whoever is behind it): its head turns right round on its shoulders to look at you, its sword
    arm swung out long to its right side and round behind it; and it stabs straight back, as only a shadow can."""
    side = dict(chest=(0, -6, 0), neck=(0, -30, 0), head=(0, -40, 0), right=(-20, 24, -2), rblade=((-1, -0.05, -0.1), None), left=(8, 13, -3), curl=0.2,
                spread=0.6, cape=(-1, 0, 0))
    look = dict(chest=(0, -10, 0), neck=(0, -60, 0), head=(0, -80, 0), right=(-11, 24, 16), rblade=((-0.25, 0.05, 0.97), None), left=(8, 14, -3), curl=0.2,
                spread=0.6, cape=(-2, 0, 0))
    hit = dict(at=(0, -1.5, 2.5), hips=(-4, 0, 0), chest=(-4, -16, 0), neck=(0, -82, 0), head=(-4, -94, 0), right=(-6, 23, 22), rblade=((-0.05, 0.1, 1), None),
               left=(8, 15, -6), curl=0.3, spread=0.6, cape=(-14, -6, 0), step={"l": (0, 0, 3), "r": (0, 0, 2)})
    after = dict(hit, at=(0, -1.8, 2.8), chest=(-5, -17, 0), neck=(0, -84, 0), head=(-4, -96, 0), right=(-6, 22.5, 23), rblade=((-0.05, 0.12, 1), None),
                 cape=(-15, -7, 0))
    return Move("turnabout", "LIGHT", 12, 4, 20, title="Turnabout", track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.0, knock=1.0, plant=(), hit=SWORD_HIT,
                lag=ON_TIME, through=P(r, after), stages={"windup": [(0.3, _from(r, side, 0.5)), (0.6, P(r, side)), (0.8, _glide(r, side, look, 0.5)), (1.0, P(r, look))],
                                                          "active": [(0.6, P(r, hit)), (1.0, P(r, after))],
                                                          "recover": [(0.36, _glide(r, after, side, 0.5)), (0.53, P(r, side)),
                                                                      (0.72, _glide(r, side, {}, 0.6))]})


def shadowstep(r):
    """The Shadowstep: it draws in, its arms about itself, and folds down into its pool until there is nothing of it
    over the floor; then it rises up out of the pool somewhere else, taller than it is for a moment, and settles."""
    draw = _pose(r, at=(0, -2, 0), spine=(4, 0, 0), chest=(6, 0, 0), head=(10, 0, 0), right=(-4, 18, -4), rblade=((0, 1, -0.1), None), left=(3, 22, -4),
                 curl=0.6, spread=0.2, cape=(-6, -2, 0))
    up_ = _pose(r, at=(0, 1, 0), spine=(-4, 0, 0), chest=(-6, 0, 0), head=(-10, 0, 0), right=(-9, 18, -2), rblade=((-0.2, 0.6, -0.8), None), left=(10, 20, 0),
                curl=-0.5, spread=1.2, cape=(4, 4, 0))
    mv = Move("shadowstep", "NONE", 12, 14, 12, title="Shadowstep", track=0.0, ability="Shadowstep", rng=(0, 99), kinds=("ranged",), damage=0, knock=0, plant=(),
              lag=ON_TIME, through=up_, stages={"windup": [(0.4, draw), (1.0, _mix(draw, _pose(r), 0.3))], "active": [(0.3, _mix(draw, up_, 0.5)), (0.7, up_), (1.0, up_)],
                                                "recover": _back(r, up_)})
    mv._scale = {"base": [(0.0, ONE), ("windup", 0.35, (1.0, 0.92, 1.0)), ("windup", 0.75, (1.1, 0.3, 1.1)), ("windup", 1.0, (1.2, 0.04, 1.2)),
                          ("active", 0.1, (1.2, 0.04, 1.2)), ("active", 0.55, (0.92, 1.14, 0.92)), ("active", 0.8, (1.02, 0.97, 1.02)), ("active", 1.0, ONE)]}
    mv._mode = "lin"  # a curve through its flattest would overshoot past nothing and turn it inside out
    return mv


def reflections(r):
    """Your Reflections (into the second phase): it flings its arms wide and throws its head back, and its whole shape
    shudders, stretching and narrowing and widening, as a shadow does with many candles; others of it are there."""
    wide = _pose(r, at=(0, 0.5, 0), spine=(-4, 0, 0), chest=(-8, 0, 0), head=(-18, 0, 0), right=(-17, 30, -2), rblade=((-0.95, -0.2, -0.1), None),
                 left=(17, 30, -2), lblade=((1, -0.1, 0), None), curl=-0.7, spread=1.5, cape=(-6, -4, 0))
    act = []
    n = 8
    for k in range(1, n + 1):
        u = k / n
        j = math.sin(k * 2.4)
        act.append((u, _pose(r, at=(0.8 * j, 0.5, 0), spine=(-4, 2 * j, 0), chest=(-8, 6 * j, 3 * j), head=(-18 + 3 * j, -8 * j, 6 * j), right=(-17, 30 + 2 * j, -2),
                             rblade=((-0.95, -0.2 - 0.1 * j, -0.1), None), left=(17, 30 - 2 * j, -2), lblade=((1, -0.1, 0), None), curl=-0.7 + 0.2 * j,
                             spread=1.5, cape=(-6 + 4 * j, -4, 4 * j))))
    mv = Move("reflections", "NONE", 14, 24, 14, title="Your Reflections", track=0.0, ability="Your Reflections", rng=(0, 99), kinds=("summon",), damage=0,
              knock=0, plant=(), lag=ON_TIME, through=act[-1][1], stages={"windup": [(0.4, _mix(_pose(r), wide, 0.5)), (1.0, wide)], "active": act,
                                                                      "recover": _back(r, act[-1][1])})
    sh = [(0.0, ONE), ("windup", 1.0, (1.0, 1.05, 1.0))]
    for k in range(1, 13):
        sh.append(("active", k / 12, (1.0 + 0.22 * math.sin(k * 2.1), 1.08 - 0.08 * math.sin(k * 1.7 + 1), 1.0)))
    sh.append(("recover", 0.4, (0.97, 1.02, 1.0)))
    mv._scale = {"base": sh}
    return mv


def halo(r):
    """The Hollow Halo (PERILOUS, the signature): it lifts its long fingers to its crown and its sword high, and the
    crown rises off its head and widens into a ring over it, level and turning; it holds it there, the ring growing,
    trembling; then it brings its arms down and the ring comes down round it and breaks outward, and is gone; its crown
    comes back on its head after."""
    def ring(p, up, yaw, tilt=(0, 0), chest=False):
        # the ring level over the middle of it (or round its chest), whatever its head is doing
        fr = core.frames(r, core.as_sample(p))
        c, top = ik.at(fr, "chest", (0, -3, 0)), ik.at(fr, "head", (0, -6, 0))
        return (c[0], (c[1] if chest else top[1]) + up, c[2]), (tilt[0], yaw, tilt[1])
    reach_ = _pose(r, at=(0, 1, 0), spine=(-3, 0, 0), chest=(-6, 0, 0), head=(-8, 0, 0), left=("on", "crown", (0, -1, -2)), lblade=((0, -1, 0.2), None), curl=0.3,
                   spread=1.0, right=(-8, 30, -2), rblade=((-0.1, -0.9, 0.3), None), cape=(-2, -2, 0))
    lift_ = _pose(r, at=(0, 1.5, 0), spine=(-4, 0, 0), chest=(-8, 0, 0), head=(-16, 0, 0), left=(5, 46, -3), lblade=((0, -1, 0), None), curl=-0.5, spread=1.4,
                  right=(-6, 46, -2), rblade=((0, -1, 0.05), None), cape=(-4, -3, 0))
    lift_ = _lay(r, lift_, "crown", *ring(lift_, 4, 30), ref=(0, 0, 0))
    w = [(0.3, _mix(_pose(r), reach_, 0.5)), (0.6, reach_), (1.0, lift_)]
    act = []
    n = 6
    prev = lift_["crown"]["rot"]
    for k in range(1, n + 1):
        u = k / n
        tr = math.sin(k * 2.6) * (0.4 + 0.6 * u)
        p = _pose(r, at=(0.3 * tr, 1.8, 0), spine=(-4, 0, 0), chest=(-8, 1.5 * tr, 0), head=(-18, 2 * tr, 0), left=(5 + tr * 0.5, 47, -3), lblade=((0, -1, 0), None),
                  curl=-0.6, spread=1.5, right=(-6 - tr * 0.5, 47, -2), rblade=((0, -1, 0.05), None), cape=(-4, -3, 2 * tr))
        p = _lay(r, p, "crown", *ring(p, 10 + 2 * u, 30 + 280 * u, (2.5 * tr, 2 * tr)), ref=prev)
        prev = p["crown"]["rot"]
        act.append((u * 0.7, p))
    down = _pose(r, at=(0, -2.5, 0), spine=(4, 0, 0), chest=(6, 0, 0), head=(4, 0, 0), left=(13, 22, -4), lblade=((0.8, 0.5, -0.2), None), curl=-0.8, spread=1.6,
                 right=(-13, 22, -4), rblade=((-0.8, 0.5, -0.3), None), cape=(8, 6, 0))
    # the ring comes down round its chest on a whole turn, so that it can wind back while it is gone
    down = _lay(r, down, "crown", *ring(down, -1, 360, chest=True), ref=prev)
    act.append((1.0, down))
    home = {"rot": (0.0, 0.0, 0.0), "pos": (0.0, 0.0, 0.0)}
    rec = [(0.3, {**_mix(down, _pose(r), 0.5), "crown": home}), (0.65, {**_mix(down, _pose(r), 0.85), "crown": home})]
    mv = Move("halo", "PERILOUS", 26, 20, 22, title="Hollow Halo", signature=True, track=0.0, ability="Hollow Halo", origin=(0, 2.0, 0), rng=(0, 12), kinds=("area",),
              damage=0, knock=0, plant=(), lag=ON_TIME, through=down, stages={"windup": w, "active": act, "recover": rec})
    ring_ = [(0.0, ONE), ("windup", 0.6, ONE), ("windup", 1.0, (1.4, 1.0, 1.4))]
    for k in range(1, n + 1):
        g = 1.4 + 2.4 * k / n
        ring_.append(("active", k / n * 0.7, (g, 1.0, g)))
    gone = (0.01, 0.01, 0.01)
    ring_ += [("active", 1.0, (5.0, 0.6, 5.0)), ("recover", 0.1, (6.0, 0.3, 6.0)), ("recover", 0.18, gone), ("recover", 0.7, gone), ("recover", 0.92, ONE)]
    mv._scale = {"crown": ring_}
    mv._mode = "lin"
    return mv


def wake(r):
    """Its intro, "It sits the way you sit.": it sits on its throne leaning on its arm, its chin on its long fingers, the
    way you sat; its head turns to you; it stands, and its throne sinks back into the pool behind it, and it lifts its
    sword before its face in a salute and lowers its point to the floor."""
    turn_ = _seated(r, lean=0.3, look=1.0)
    stand = _pose(r, at=(0, -3, 2.5), hips=(8, 0, 0), spine=(8, 0, 0), chest=(6, 0, 0), head=(-6, 0, 0), right=(-5, 18, -6), rblade=((0.1, 0.45, -0.89), None),
                  left=(8, 16, 0), curl=0.2, spread=0.6, step={"l": (0, 0, 1), "r": (0, 0, 1)}, cape=(-2, 0, 0))
    tall = _pose(r, at=(0, 1.2, 0), spine=(-3, 0, 0), chest=(-4, 0, 0), head=(-6, 0, 0), right=(-7, 14, -5), rblade=((-0.1, 0.4, -0.9), None), curl=0.0, spread=1.0,
                 cape=(4, 4, 0))
    salute = _pose(r, at=(0, 1.0, 0), chest=(-4, 6, 0), head=(-4, -6, 0), right=(-2, 38, -7), rblade=((0, -1, -0.08), None), curl=0.2, spread=0.6, cape=(2, 2, 0))
    bow = _pose(r, at=(0, 0.6, 0), chest=(4, 6, 0), neck=(6, 0, 0), head=(10, -6, 0), right=(-2, 36, -8), rblade=((0, -1, -0.15), None), curl=0.2, spread=0.6)
    low = _pose(r, at=(0, -0.6, 0), right=(-8, 15, -7), rblade=((-0.15, 0.35, -0.92), None), curl=0.3, spread=0.5, cape=(4, 2, 0))
    settle = _pose(r, at=(0, -0.9, 0), chest=(1, 0, 0), head=(-3, 4, 0), right=(-7.5, 13, -6), rblade=((-0.12, 0.3, -0.95), None), curl=0.45, spread=0.35,
                   cape=(6, 3, 0))
    mv = Move("wake", "NONE", 16, 34, 12, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME, through=settle,
              stages={"windup": [(0.3, _seated(r, lean=0.1, tap=1, drum=1)), (0.55, _seated(r, lean=0.3, look=0.3, drum=-1)),
                                 (0.8, _seated(r, lean=0.35, look=0.7, tap=1, drum=0.5)), (1.0, turn_)],
                      "active": [(0.25, stand), (0.45, tall), (0.65, salute), (0.8, bow), (1.0, low)], "recover": _back(r, settle)})
    mv._scale = {"throne": [(0.0, ONE), ("windup", 1.0, ONE), ("active", 0.25, ONE), ("active", 0.45, (1.0, 0.4, 1.0)), ("active", 0.6, (1.0, HIDE, 1.0)),
                            ("recover", 1.0, (1.0, HIDE, 1.0))]}
    return mv


ON_TIME = {b: 0 for b in ARMS + ("sword",)}  # its blade lands where it is sent


def moves(r):
    return [cut(r), backcut(r), reach(r), rake(r), parry(r), riposte(r), charge(r), turnabout(r), shadowstep(r), reflections(r), halo(r), wake(r)]


def clips(r):
    idle(r)
    walk(r)
    run(r)
    turns(r)
    reactions(r)
    mv = [_unwrap(m) for m in moves(r)]
    for m in mv:
        stage_clips(r, m)
        _move_scale(r, m)
    for name in ("idle", "walk", "run", "turn_l", "turn_r"):
        _waver(r, name)
    _fold_throne(r)
    # its hands and its sword stay out of the floor between the keys, the wrists turning first
    core.ground_pass(r, [(["sword"], [("hand_r", 0, 40), ("forearm_r", 0, 30)]), (["hand_l", "lf0", "lf1", "lf2"], [("hand_l", 0, 40), ("forearm_l", 0, 30)])],
                     clips=[n for n in r.clips if n != "death"])
    core.ground_pass(r, [(["cape2", "cape1"], [("cape0", 0, 60, -1), ("cape1", 0, 40, -1)])], clips=list(r.clips), skip=())
    return mv


FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "cut", "weight": 3, "when": "near", "then": [["backcut"], ["backcut", "reach"]]},
            {"move": "reach", "weight": 2, "when": "near"},
            {"move": "rake", "weight": 2, "when": "near", "then": [["cut"]]},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "charge", "weight": 3, "when": "far", "then": [["cut"]]},
            {"move": "turnabout", "weight": 3, "when": "behind"},
        ]},
        {"transition": "reflections", "pool": [
            {"move": "cut", "weight": 3, "when": "near", "then": [["backcut"], ["backcut", "reach"], ["rake", "cut"]]},
            {"move": "reach", "weight": 2, "when": "near"},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "halo", "weight": 2},
            {"move": "shadowstep", "weight": 2, "then": [["cut", "backcut"]]},
            {"move": "reflections", "weight": 1},
            {"move": "charge", "weight": 2, "when": "far", "then": [["cut"]]},
            {"move": "turnabout", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}

# Its sounds (SPEC §35.8): no voice and no footfall of its own, as a shadow has none: a hush, cloth, and the sounds of
# a blade; chosen by ear on paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("item.armor.equip_leather", 1.2, 0.6), ("entity.phantom.flap", 1.4, 0.4)],
    "tell_heavy": [("entity.illusioner.prepare_mirror", 0.8, 0.7), ("block.sculk.spread", 0.8, 0.6)],
    "tell_perilous": [("entity.illusioner.prepare_blindness", 0.6, 0.8), ("block.beacon.deactivate", 0.6, 0.7)],
    "swing_light": [("entity.player.attack.sweep", 1.1, 0.7), ("entity.phantom.swoop", 1.4, 0.4)],
    "swing_heavy": [("entity.player.attack.knockback", 0.9, 0.7), ("entity.phantom.swoop", 0.9, 0.6)],
    "cast": [("entity.illusioner.mirror_move", 0.7, 0.8), ("block.sculk.spread", 0.6, 0.6)],
    "impact": [("entity.player.attack.strong", 0.8, 0.8), ("block.sculk_shrieker.break", 0.8, 0.6)],
    "roar": [("block.beacon.deactivate", 0.5, 0.9), ("entity.illusioner.cast_spell", 0.6, 0.7)],
    "step": [("block.wool.step", 0.6, 0.3), ("entity.phantom.flap", 0.8, 0.2)],
}
MOVE_SOUNDS = {"parry": {"swing": "", "impact": ""}, "shadowstep": {"swing": "cast", "impact": ""}, "reflections": {"swing": "cast", "impact": ""},
               "halo": {"swing": "roar"}, "wake": {"swing": "", "impact": ""}}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.7, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("pelvis", (0, 0, 0)), ("head", (0, 0, 0)), ("crown", (0, 0, 0)), ("sword", TIP), ("hand_l", (0, 0, 0)), ("foot_l", (0, 0, 0)),
                 ("cape2", (0, 0, 0)), ("throne", (0, -34, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
