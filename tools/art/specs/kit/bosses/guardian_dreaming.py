"""The Architect of Rooms (SPEC §35.11, docs/bosses/ROLLOUT.md): the Dreaming's Guardian, a Caster. The Hermit House,
picked from its silhouette round 2026-10-29: "You didn't come in through that door."

A hermit crab as big as a cottage that has made its shell of a house: a crooked two-storey house of lilac clapboard on
a stone footing, its upper storey turned askew on the lower like a house in a dream, a stepped slate roof, a leaning
chimney, a lamp hanging by the door, and every window lit from inside. Where the front door should be the crab looks
out: a coral carapace, two black eyes on stalks that swivel on their own, long feelers, mouthparts that never stop
working, one huge claw held up in front of its face like a shield and a small one for picking, and four long banded
legs that carry the house. The door hangs open on its hinge beside it. Through the doorway, past the crab, there is a
room with another door in it, lit.

It stands with its house rocking on its back, its eyes looking two ways at once, its feelers sweeping, its mouthparts
fluttering and its legs shifting; the door creaks on its hinge and the lamp swings.

Moves (SPEC §35.11): the snip of its small claw and the crush of its big one (LIGHT); the Claw Hammer (HEAVY: the big
claw raised high over its house and brought down); the Scuttle (a gap closer: it rushes at you on all four legs, its
claw out ahead); its house heaved back into whoever is behind it; the Shifting Floor (it drums its legs on the floor in
a running ripple and the floor shifts); the Door Step (it backs into its house and swings the door shut, and comes out
somewhere else); Other Rooms (its house shakes and its door bangs open and shut, and other houses are there); and the
Falling Ceiling (the signature: it rears up on its back legs, reaches both claws up as high as they go, takes hold of
the air and pulls the ceiling down).
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_dreaming"
SCALE = 1.8  # the def's scale; the body is modelled at true size and the hitbox set round its house

# The Hermit House (docs/bosses/ROLLOUT.md, A). The house's floor 7 px up, its lower storey 16 tall and its upper 11,
# the roof's ridge at 44; the crab's middle 13 up in the doorway, its big claw out to 22 px in front. Value story: the
# windows and the lamp brightest, then the coral crab and the white trim, the lilac walls, the slate roof darkest.
BODY = 7.0  # the house's floor over the floor
CRAB = (0.0, 6.0, -10.0)  # the crab's middle on the house's floor: in the doorway
HOUSE_TILT = -8.0  # its house tipped back on its back
SIDE = (1, 1, -1, -1)  # legs 0-1 on its left (+x), 2-3 on its right, front then back
HIP = ((6.0, -1.0, -3.5), (6.5, -1.0, 2.0))  # each pair's hip on the crab (x out, up, z), front then back
REST_FOOT = [(15.0, 0.5, -25.0), (19.0, 0.5, -9.0), (-15.0, 0.5, -25.0), (-19.0, 0.5, -9.0)]  # where the legs stand
POLE = [(0.6, -1.0, -0.3), (0.7, -1.0, 0.2), (-0.6, -1.0, -0.3), (-0.7, -1.0, 0.2)]  # the knees ride up and out
THIGH, SHIN = 11, 15
SIDES_ = (("l", 1), ("r", -1))


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def _hip(k):
    """Leg k's hip on the crab, and the world place it starts from at rest (the house tipped, the crab in its door)."""
    hx, hu, hz = HIP[k % 2]
    return (SIDE[k] * hx, hu, hz)


def _hip_yaw(k):
    """Each hip turned to face down its leg, so the thigh's turn in it stays far from the Euler lock a leg straight out
    to its side would sit on (as the Long-Legs' are)."""
    hx, _, hz = _hip(k)
    fx, _, fz = REST_FOOT[k]
    dx, dz = fx - (CRAB[0] + hx), fz - (CRAB[2] + hz)
    return math.degrees(math.atan2(-dx, -dz)) + 180.0


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, BODY, 0)
    # the lower storey: clapboard walls on a stone footing round a lit room, the front open in a doorway
    hs = _bone(bd, "house", 0, 0, 0, (HOUSE_TILT, 0, 0))
    _box(hs, "found", 0, -2, 1, 26, 2, 24)
    _box(hs, "wall_back", 0, 0, 10.5, 24, 16, 3)
    for nm, sg in SIDES_:
        _box(hs, "wall_" + nm, sg * 11, 0, -0.5, 2, 16, 19)
        _box(hs, "front_" + nm, sg * 7.5, 0, -9, 5, 12, 2)
    _box(hs, "lintel", 0, 12, -9, 20, 4, 2)
    _box(hs, "inside", 0, 0, 0.5, 20, 16, 17)
    _box(hs, "step", 0, -1, -11.5, 12, 1, 3)
    # its door, hanging open on its hinge at the doorway's left
    dr = _bone(hs, "door", 5, 0, -10, (0, -120, 0))
    _box(dr, "door", -5, 0, -0.5, 10, 12, 1)
    _box(dr, "knob", -8.5, 5, -1.5, 1, 1, 1)
    # the lamp hanging by the door on its bracket
    _box(hs, "bracket", -9, 11, -11.5, 1, 1, 3)
    lp = _bone(hs, "lamp", -9, 11, -12.5)
    _box(lp, "lampcap", 0, -1, 0, 3, 1, 3)
    _box(lp, "lamp", 0, -5, 0, 3, 4, 3)
    # the upper storey, turned askew on the lower, its windows lit, and the stepped slate roof with its chimney
    up = _bone(hs, "upper", 0, 16, 0.5, (0, 6, -3))
    _box(up, "jetty", 0, -1, 0, 27, 1, 25)
    _box(up, "storey", 0, 0, 0, 26, 11, 24)
    ro = _bone(up, "roof", 0, 11, 0)
    for k, w in enumerate((30, 24, 18, 12, 6)):
        _box(ro, f"roof{k}", 0, 2 * k, 0, w, 2, 28)
    _box(ro, "ridge", 0, 10, 0, 2, 1, 29)
    ch = _bone(ro, "chimney", 7.5, 2, 6, (0, 0, -6))
    _box(ch, "chimney", 0, 0, 0, 4, 12, 4)
    _box(ch, "chimcap", 0, 12, 0, 6, 1, 6)
    # the crab in its doorway
    cr = _bone(bd, "crab", *CRAB)
    _box(cr, "cara", 0, -3, -2, 14, 7, 10)
    _box(cr, "rostrum", 0, 1, -7.5, 6, 2, 1)
    _box(cr, "plastron", 0, -4, -1, 10, 1, 8)
    for nm, sg in SIDES_:
        _box(cr, "flank_" + nm, sg * 7.5, -2, -1, 1, 5, 8)
    # its eyes on their stalks
    for nm, sg in SIDES_:
        e = _bone(cr, "eye_" + nm, sg * 2.5, 4, -6, (0, 0, sg * 10))
        _box(e, "stalk_" + nm, 0, 0, 0, 2, 7, 2)
        _box(e, "eyeball_" + nm, 0, 7, -0.5, 3, 3, 3)
    # its feelers, long and sweeping, and the mouthparts working under its face
    for nm, sg in SIDES_:
        parent, piv = cr, (sg * 1.5, -2, -7)
        for k, (a, L) in enumerate(((-40.0, 8), (14.0, 8), (14.0, 7))):
            fb = parent.bone(f"ant_{nm}{k}", piv, (a, sg * 22 if k == 0 else 0, 0))
            fb.cube(f"ant_{nm}{k}", -0.5, -0.5, -L, 1, 1, L)
            parent, piv = fb, (0, 0, -L)
    mx = _bone(cr, "mouth", 0, -1, -7)
    for nm, sg in SIDES_:
        _box(mx, "mxp_" + nm, sg * 1.5, -4, 0, 2, 4, 1)
    # its claws: the big one on its right, held up before its face, and a small one on its left
    # (each folded: the arm forward and up, the wrist upright, the palm hanging before its face with its fingers down,
    # the moving finger in front)
    for nm, sg, s, rest in (("r", -1, 1.0, ((-20, -15, 0), (-70, 0, 0), (165, 0, 0))), ("l", 1, 0.62, ((-10, 15, 0), (-80, 0, 0), (160, 0, 0)))):
        c0 = _bone(cr, "cl_" + nm + "0", sg * 6.0, -1, -5, rest[0])
        _box(c0, "mer_" + nm, 0, -2 * s, -4 * s, round(4 * s), round(4 * s), round(8 * s))
        c1 = c0.bone("cl_" + nm + "1", (0, 0, -round(8 * s)), rest[1])
        _box(c1, "carp_" + nm, 0, -2.5 * s, -2.5 * s, round(5 * s), round(5 * s), round(5 * s))
        c2 = c1.bone("cl_" + nm + "2", (0, 0, -round(5 * s)), rest[2])
        pw, ph, pd = round(8 * s), max(3, round(5 * s)), round(9 * s)
        fw, fh, fd = round(4 * s), max(1, round(2 * s)), round(6 * s)
        _box(c2, "palm_" + nm, 0, -ph / 2, -pd / 2, pw, ph, pd)
        _box(c2, "heel_" + nm, sg * -(pw / 2 - 1), -ph / 2 + 1, -pd / 2 + 1, 2, max(1, ph - 2), max(2, pd - 4))
        # the fingers, tapering to dark points: the fixed one below, the moving one hinged above it
        _box(c2, "fix_" + nm, 0, -ph / 2, -pd - 2, fw, fh, 4)
        _box(c2, "fixtip_" + nm, sg * 0.5 if fw > 2 else 0, -ph / 2, -pd - 4 - 1.5, max(1, fw - 2), fh, 3)
        cf = c2.bone("cl_" + nm + "f", (0, -(ph / 2 - fh), -pd + 1), (-8, 0, 0))
        _box(cf, "dac_" + nm, 0, 0, -2.5, fw, fh, 5)
        _box(cf, "dactip_" + nm, sg * 0.5 if fw > 2 else 0, 0, -5 - 1.5 - (fd > 4), max(1, fw - 2), fh, 3 + (fd > 4))
    # four long banded legs
    for k in range(4):
        hx, hu, hz = _hip(k)
        hp = _bone(cr, f"hip{k}", hx, hu, hz, (0, _hip_yaw(k), 0))
        th = hp.bone(f"thigh{k}", (0, 0, 0))
        th.cube(f"coxa{k}", -2.5, -1, -2.5, 5, 4, 5)
        th.cube(f"thigh{k}", -2, 3, -1.5, 4, THIGH - 4, 3)
        th.cube(f"kneek{k}", -2, THIGH - 2, -2, 4, 4, 4)
        sh = th.bone(f"shin{k}", (0, THIGH, 0))
        sh.cube(f"shin{k}", -1.5, 2, -1, 3, SHIN - 7, 2)
        sh.cube(f"dact{k}", -1, SHIN - 5, -1, 2, 3, 2)
        sh.cube(f"dactp{k}", -0.5, SHIN - 2, -0.5, 1, 2, 1)
        sh.bone(f"foot{k}", (0, SHIN, 0))
    for k in range(4):
        ik.rest_limb(r, f"thigh{k}", f"shin{k}", f"foot{k}", REST_FOOT[k], POLE[k])
    r.look = [("crab", 0.12, 0.1), ("eye_l", 0.5, 0.3), ("eye_r", 0.5, 0.3)]
    r.shadow = 2.2
    r.size = 1.0
    # secondary parts (SPEC §35.10): the feelers, the lamp and the door swing; the chimney rocks on the roof
    r.springs = ([(f"ant_{nm}{k}", 0.35 + 0.1 * k, 1.6 + 0.3 * k, 0.16) for nm, _ in SIDES_ for k in range(1, 3)]
                 + [("lamp", 0.6, 1.3, 0.12), ("door", 0.4, 1.0, 0.1), ("chimney", 0.2, 2.6, 0.2)])
    r.guard = [f"foot{k}" for k in range(4)]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

WALL = Ramp("#3e3048", "#4c3c58", "#5b4968", "#6b5778", "#7b6688", "#8c7698", "#9d88a8", "#af9bb8")
TRIM = Ramp("#a898b0", "#c2b4c8", "#d8ccdc", "#ebe2ee", "#f8f2fa")
STONE = Ramp("#2a2630", "#36313e", "#433d4c", "#514a5a", "#605868")
SLATE = Ramp("#120e1a", "#1a1424", "#221a2e", "#2b2238", "#352a44", "#403352")
BRICK = Ramp("#3a1a22", "#4c222c", "#5e2c36", "#703842", "#82464e")
DOOR = Ramp("#1c1026", "#281634", "#341e42", "#422850", "#52325e")
ROOM = Ramp("#120a1c", "#1c1028", "#281634", "#341e42")
SHELL = Ramp("#3e1418", "#561e22", "#6e2a2c", "#883a38", "#a24c46", "#ba6056", "#d07a6a", "#e2988a", "#f0baaa")
LEG = Ramp("#4a181c", "#622226", "#7c3030", "#964238", "#ae5644", "#c46c52")
LIT = ("#fff4c8", "#ffe08a", "#ffc25a", "#f09a3a")
EYE = Ramp("#050306", "#0e0a12", "#1a141e")


def _window(P_, c, fname, x0, y0, w, h):
    """A lit window on a face: a white frame, warm light in four panes behind a cross, brighter at the middle."""
    f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for i in range(x0 - 1, x0 + w + 1):
        for j in range(y0 - 1, y0 + h + 1):
            if not (0 <= i < f.w and 0 <= j < f.h):
                continue
            frame = i in (x0 - 1, x0 + w) or j in (y0 - 1, y0 + h) or (w >= 3 and i == x0 + w // 2) or (h >= 4 and j == y0 + h // 2)
            if frame:
                f.set(i, j, TRIM.at(3))
            else:
                d = math.hypot((i - cx) / max(1, w), (j - cy) / max(1, h))
                col = LIT[0] if d < 0.25 else LIT[1] if d < 0.45 else LIT[2]
                f.set(i, j, hexc(col))
                g.set(i, j, hexc(col, 230))


def _faces(P_, c, ends=False):
    out = []
    for fname in ("north", "south", "west", "east") + (("top", "bottom") if ends else ()):
        try:
            out.append((fname, P_.face(c, fname)))
        except (KeyError, ValueError):
            pass
    return out


def paint(r, P_):
    """Lilac clapboard in horizontal boards with white corner trim, on a footing of grey-violet stones; every window lit
    warm behind white frames; a slate roof in rows, its gable lilac with a round attic window; a brick chimney; a plum
    door with panels and a brass knob; the room inside dark violet with another door in it, lit. The crab coral, paler
    on its claw's bumps and dark at its fingertips, its legs banded, its eyes glossy black."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    boards = R.p_bands(every=2, depth=0.9)
    for c in ("wall_back", "wall_l", "wall_r", "front_l", "front_r", "lintel", "storey"):
        P_.paint(c, WALL, 4.2, boards, grad=1.0, rim=0.6, ao=0.8, noise=0.5, nscale=3)
        for fname, f in _faces(P_, c):  # white trim at the corners
            for i, j in f.each():
                if i in (0, f.w - 1):
                    f.set(i, j, TRIM.at(2 + (j % 2)))
    P_.paint("found", STONE, 2.4, R.p_blotch(5, scale=3, amount=0.8, levels=3), grad=0.8, rim=0.6, ao=0.9, noise=0.7)
    for fname, f in _faces(P_, "found"):  # its stones
        for i, j in f.each():
            if (i + 3 * (j // 1)) % 5 == 0 or j == f.h - 1:
                f.set(i, j, STONE.at(0))
    P_.paint("step", STONE, 3.0, None, rim=0.6)
    P_.paint("jetty", TRIM, 2.0, None, rim=0.5, ao=0.6)
    for c in of("roof", "ridge"):
        P_.paint(c, SLATE, 3.0, R.p_bands(every=2, depth=1.0, vertical=True), grad=0.8, rim=0.8, ao=0.6, noise=0.5)
    for k in range(5):  # the gable ends: lilac boards under the eaves
        for fname in ("north", "south"):
            f = P_.face(f"roof{k}", fname)
            for i, j in f.each():
                if 1 <= i < f.w - 1:
                    f.set(i, j, WALL.at(4 + (j % 2)))
    for c in ("chimney",):
        P_.paint(c, BRICK, 2.6, lambda f, i, j: (-1.2 if j % 3 == 0 or (i + (j // 3) * 2) % 4 == 0 else 0), rim=0.6, ao=0.6)
    P_.paint("chimcap", STONE, 2.0, None, rim=0.6)
    P_.paint("door", DOOR, 2.6, lambda f, i, j: (-1.0 if i in (1, f.w - 2) or j in (1, 6, f.h - 2) else 0.4), rim=0.6, ao=0.4)
    P_.paint("knob", Ramp("#8a6a2a", "#d8b45a", "#f8e090"), 1.4, None)
    P_.paint("bracket", STONE, 1.0, None)
    P_.paint("lampcap", STONE, 1.0, None)
    P_.paint("lamp", Ramp(*LIT[::-1]), 2.0, None)
    for fname, f in _faces(P_, "lamp"):
        g = P_.face("lamp", fname, glow=True)
        for i, j in f.each():
            f.set(i, j, hexc(LIT[1] if 0 < i < f.w - 1 else LIT[3]))
            g.set(i, j, hexc(LIT[0], 240))
    # the room inside: dark, lit from deep in by another doorway
    P_.paint("inside", ROOM, 2.0, None, grad=0.6, ao=1.0)
    f, g = P_.face("inside", "north"), P_.face("inside", "north", glow=True)
    for i, j in f.each():
        dx = abs(i - (f.w - 1) / 2)
        if dx <= 2 and j >= f.h - 10:  # the far door, open on a lit room
            col = LIT[1] if dx <= 1 and j >= f.h - 8 else TRIM.at(1)
            f.set(i, j, hexc(col) if isinstance(col, str) else col)
            if dx <= 1:
                g.set(i, j, hexc(LIT[1], 200))
    # every window lit
    for c, fname, wins in (("storey", "north", ((5, 3, 4, 5), (17, 3, 4, 5))), ("storey", "south", ((11, 3, 4, 5),)),
                           ("storey", "west", ((4, 3, 4, 5), (16, 3, 4, 5))), ("storey", "east", ((4, 3, 4, 5), (16, 3, 4, 5))),
                           ("wall_l", "west", ((8, 4, 3, 5),)), ("wall_l", "east", ((8, 4, 3, 5),)),
                           ("wall_r", "west", ((8, 4, 3, 5),)), ("wall_r", "east", ((8, 4, 3, 5),)),
                           ("wall_back", "south", ((5, 4, 4, 5), (15, 4, 4, 5))), ("roof2", "north", ((8, 0, 2, 1),)), ("roof1", "north", ((10, 0, 4, 1),))):
        try:
            _window(P_, c, fname, *wins[0])
            for w in wins[1:]:
                _window(P_, c, fname, *w)
        except (KeyError, ValueError):
            pass
    # a door on the upper storey's side, to nowhere
    for fname in ("west", "east"):
        f = P_.face("storey", fname)
        for i, j in f.each():
            if 10 <= i <= 13 and j >= 2:
                f.set(i, j, DOOR.at(3) if 11 <= i <= 12 and j >= 3 else TRIM.at(3))
    # the crab
    bumps = R.p_blotch(9, scale=2, amount=0.9, levels=3)
    for c in ("cara", "rostrum", "plastron") + tuple(of("flank_", "mer_", "carp_", "palm_", "heel_")):
        P_.paint(c, SHELL, 4.6, bumps, grad=1.6, rim=1.0, ao=0.8, noise=0.6, nscale=3)
    for c in of("palm_", "heel_", "carp_", "mer_"):  # the claw's granules, pale, ringed darker
        for fname, f in _faces(P_, c, ends=True):
            for i, j in f.each():
                h = R._h("gr", c, fname, i, j) % 9
                if h == 0:
                    f.set(i, j, SHELL.at(8))
                elif h == 1:
                    f.set(i, j, SHELL.at(2))
    for c in of("fix_", "dac_"):
        P_.paint(c, SHELL, 3.6, None, grad=1.0, rim=0.8, ao=0.4)
    for c in of("fixtip_", "dactip_"):  # the fingers dark at their points, a pale tooth or two
        P_.paint(c, Ramp("#1a080a", "#2c1012", "#3e1a1a"), 1.6, None, rim=0.8)
        for fname, f in _faces(P_, c, ends=True):
            for i, j in f.each():
                if R._h("tooth", c, fname, i, j) % 5 == 0:
                    f.set(i, j, hexc("#e8d8d0"))
    for c in of("coxa", "thigh", "kneek", "shin", "dact"):  # the dark tips are the claws' alone
        P_.paint(c, LEG, 3.4, lambda f, i, j: (-1.4 if j % 4 == 3 else 0.3), grad=1.0, rim=0.8, ao=0.5)
    for c in of("stalk_", "ant_", "mxp_"):
        P_.paint(c, LEG, 3.6, lambda f, i, j: (-1.0 if j % 3 == 2 else 0.2), rim=0.8)
    for nm, _ in SIDES_:
        c = "eyeball_" + nm
        P_.paint(c, EYE, 1.4, None, rim=0.6)
        for fname, f in _faces(P_, c):
            g = P_.face(c, fname, glow=True)
            f.set(1, 0, hexc("#f4e8ff"))
            g.set(1, 0, hexc("#f4e8ff", 160))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Motion (SPEC §35.4): one pose function over its parts; the clips are keys of it

CLAW_REST = {"r": ((-20, -15, 0), (-70, 0, 0), (165, 0, 0)), "l": ((-10, 15, 0), (-80, 0, 0), (160, 0, 0))}
CLAW_SIDE = {"r": -1, "l": 1}


def _out(k):
    """Leg k's way out across the floor from its hip at rest (x, z), unit."""
    hx, _, hz = _hip(k)
    fx, _, fz = REST_FOOT[k]
    dx, dz = fx - (CRAB[0] + hx), fz - (CRAB[2] + hz)
    n = math.hypot(dx, dz)
    return dx / n, dz / n


def _foot(k, out=0.0, side=0.0, up=0.0, dz=0.0, dx=0.0):
    """Leg k's tip moved from where it stands at rest: out along its way out, side across it (toward its front), up, and
    by (dx, dz)."""
    ox, oz = _out(k)
    x, h, z = REST_FOOT[k]
    sx, sz = (oz, -ox) if SIDE[k] > 0 else (-oz, ox)
    return (x + ox * out + sx * side + dx, h + up, z + oz * out + sz * side + dz)


def _claw(p, nm, reach=0.0, lift=0.0, out=0.0, open_=0.0, wrist=0.0, twist=0.0):
    """One claw, from its fold before its face: reach unfolds it straight out ahead (1 at full stretch), lift raises
    the whole arm (degrees, + up), out swings it out to its side, open_ opens the moving finger (+), wrist turns the palm
    (+ its fingers down and back) and twist rolls the arm."""
    sg = CLAW_SIDE[nm]
    r0, r1, r2 = CLAW_REST[nm]
    p["cl_" + nm + "0"] = (-r0[0] * reach - lift, -sg * out - r0[1] * reach, twist)
    p["cl_" + nm + "1"] = (-r1[0] * reach, 0.0, 0.0)
    p["cl_" + nm + "2"] = (-r2[0] * reach + wrist, -r2[1] * reach, 0.0)
    p["cl_" + nm + "f"] = (-open_, 0.0, 0.0)


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), feet=None, poles=None, crab=None, big=None, small=None, eyes=(0.0, 0.0, 0.0, 0.0),
          feel=(0.0, 0.0, 0.0), house=(0.0, 0.0), upper=(0.0, 0.0, 0.0), door=0.0, mouth=0.0, extra=None):
    """lift (+ up), pitch (+ its front dipping), yaw (+ to its left), roll (+ its left side down) and shift (x, z) move
    it all, house and crab; crab: dict(back, up, pitch, yaw, roll) moves the crab in its doorway (back draws it into the
    house); feet {leg: (x, height, z)} are where its legs stand (the rest stay where they stand at rest); big and small:
    _claw arguments; eyes: (left yaw, left pitch, right yaw, right pitch) swivel the eyes on their stalks (yaw + to their
    left, pitch + down); feel: (sweep, left lift, right lift) of the feelers; house: (pitch, roll) rock it on its back;
    upper: the upper storey's (pitch, yaw, roll) on the lower; door swings it shut (+ degrees from hanging open); mouth
    works the mouthparts."""
    c = crab or {}
    p = {"body": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])},
         "crab": {"rot": (c.get("pitch", 0.0), c.get("yaw", 0.0), c.get("roll", 0.0)), "pos": (0.0, c.get("up", 0.0), c.get("back", 0.0))},
         "house": (house[0], 0.0, house[1]), "upper": tuple(upper), "door": (0.0, door, 0.0), "mouth": (mouth, 0.0, 0.0),
         "eye_l": (eyes[1], eyes[0], 0.0), "eye_r": (eyes[3], eyes[2], 0.0)}
    for nm, sg, lf in (("l", 1, feel[1]), ("r", -1, feel[2])):
        p[f"ant_{nm}0"] = (-lf, sg * feel[0], 0.0)
    _claw(p, "r", **(big or {}))
    _claw(p, "l", **(small or {}))
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    poles = poles or {}
    for k in range(4):
        tgt = ik.model(feet.get(k, REST_FOOT[k]))
        p.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", tgt, poles.get(k, POLE[k])))
    if extra:
        p.update(extra)
    return p


def _all(fn):
    return {k: fn(k) for k in range(4)}


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before (each axis by whole turns, the same pose), so a leg
    folded far from its rest goes the short way between keys and never spins round (as the Long-Legs' do)."""
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


def _plant(r, clip, legs, where=None):
    """The legs that stand through a move keep their tips on the floor where they stand, every tick."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            tgt = ik.model(where.get(k, REST_FOOT[k]))
            out.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", tgt, POLE[k]))
        return out
    ik.retrack(r, clip, fn)


GROUPS = {0: 0.0, 3: 0.0, 1: 0.5, 2: 0.5}  # the diagonal pairs of its walk


def _gait(u, stride_px, beta, lift):
    """The legs through a stride at u: down, each moves back as far as it travels, so it stays put on the floor; up, it
    lifts and reaches forward again, each pair by turns."""
    feet = {}
    st = stride_px * beta
    for k, ph in GROUPS.items():
        q = (u - ph + 0.04 * (k % 2)) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f), out=1.5 * math.sin(math.pi * f))
    return feet


def _idle(r):
    """It stands with its house rocking a little on its back and its upper storey swaying on the lower; its eyes look
    about on their own, each its own way; its feelers sweep; the big claw twitches open and shut; once a loop the small
    claw picks at the floor and brings it to its mouth; a leg shifts; the door creaks on its hinge."""
    L, n = 5.0, 50
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        feet = {}
        for leg, (a, b) in ((1, (0.16, 0.32)), (2, (0.66, 0.82))):
            f = (u - a) / (b - a)
            if 0 < f < 1:
                feet[leg] = _foot(leg, up=6 * math.sin(math.pi * f), dz=-2 * math.sin(math.pi * f), out=1.5 * math.sin(math.pi * f))
        pf = (u - 0.48) / 0.34  # the small claw picks
        pick = math.sin(math.pi * pf) if 0 < pf < 1 else 0.0
        mouthward = math.sin(math.pi * min(1.0, max(0.0, (pf - 0.5) * 2))) if 0.5 < pf < 1 else 0.0
        small = dict(reach=0.45 * pick - 0.1 * mouthward, lift=-30 * pick * (1 - mouthward) + 12 * mouthward,
                     open_=20 * max(0.0, math.sin(2 * math.pi * pf)) if 0 < pf < 0.5 else 4)
        keys.append((L * u, _pose(r, lift=0.6 * math.sin(2 * w), pitch=0.8 * math.sin(w + 0.4), roll=1.0 * math.sin(w + 1.5), yaw=1.2 * math.sin(w),
                                  feet=feet, crab=dict(back=0.6 * math.sin(2 * w + 0.5), pitch=2 * math.sin(2 * w + 1.0), yaw=2 * math.sin(w - 0.7)),
                                  big=dict(lift=4 * math.sin(w - 0.3), open_=5 + 6 * max(0.0, math.sin(3 * w + 1.0))), small=small,
                                  eyes=(24 * math.sin(w) + 8 * math.sin(3 * w), 6 * math.sin(2 * w + 1.0), 24 * math.sin(w + 2.4) - 8 * math.sin(2 * w + 0.5),
                                        6 * math.sin(2 * w - 0.6)),
                                  feel=(14 * math.sin(2 * w + 0.4), 10 + 8 * math.sin(3 * w), 10 + 8 * math.sin(3 * w + 2.0)),
                                  house=(1.2 * math.sin(w - 0.6), 1.5 * math.sin(w + 0.9)), upper=(0.8 * math.sin(w - 1.2), 1.5 * math.sin(w + 0.3), 1.0 * math.sin(w - 0.4)),
                                  door=5 * math.sin(2 * w + 0.3))))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """Its walk: the diagonal pairs by turns, its house rocking from side to side on its back and nodding, the upper
    storey swaying after it, its claws held in and its eyes ahead. Running, it goes low and quick and the house lurches."""
    for name, L, stride, beta, lift, lean, drop, rock in (("walk", 1.2, 1.3, 0.6, 6.0, 1.5, 0.0, 3.0), ("run", 0.7, 2.2, 0.5, 8.0, 4.0, 2.0, 5.0)):
        sp = stride * 16
        keys = []
        n = 24
        for k in range(n + 1):
            u = k / n
            s, s2 = math.sin(2 * math.pi * u), math.sin(4 * math.pi * u)
            keys.append((L * u, _pose(r, lift=-drop - 0.6 * (1 + math.cos(4 * math.pi * u)), pitch=lean + 1.0 * s2, roll=1.5 * s, yaw=1.2 * s, feet=_gait(u, sp, beta, lift),
                                      crab=dict(pitch=2), big=dict(lift=6, open_=4), small=dict(lift=8), eyes=(4 * s, -4, -4 * s, -4), feel=(6 * s2, 14, 14),
                                      house=(-1.5 * s2, rock * math.sin(2 * math.pi * u - 0.7)),
                                      upper=(-0.8 * s2, 2 * math.sin(2 * math.pi * u - 1.4), rock * 0.5 * math.sin(2 * math.pi * u - 1.4)),
                                      door=10 * math.sin(2 * math.pi * u - 1.0))))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.3, 2.2
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        for k in range(n + 1):
            u = k / n
            feet = {}
            for j, ph in GROUPS.items():  # each pair steps round the way it turns while the other holds
                q = (u - ph) % 1.0
                if q < 0.5:
                    f = q / 0.5
                    feet[j] = _foot(j, side=sgn * SIDE[j] * 6 * (core.ease_io(f) - 0.5), up=6 * math.sin(math.pi * f))
                else:
                    feet[j] = _foot(j, side=sgn * SIDE[j] * 6 * (0.5 - (q - 0.5) / 0.5))
            keys.append((0.9 * u, _pose(r, yaw=sgn * 2.0, roll=1.0 * math.sin(4 * math.pi * u), feet=feet, eyes=(sgn * 20, 0, sgn * 20, 0), feel=(sgn * 12, 12, 12),
                                        house=(0, -sgn * 2 * math.sin(2 * math.pi * u)), upper=(0, -sgn * 3, 0))))
        keyed(r, nm, 0.9, keys, loop=True)


def _hidden(r, low=1.0, lid=True, rock=(0.0, 0.0), up=0.0, back=12.0, yaw=0.0, eyes=None, big=None, small=None, feel=None, **kw):
    """Drawn into its house: the house down level on the floor, the crab back inside, its legs folded in under the walls,
    its eyes ducked and its big claw across the doorway as a lid. rock: (pitch, roll) of the whole house on its folded
    legs; the rest as _pose's."""
    return _pose(r, lift=-4.2 * low + up, pitch=-HOUSE_TILT * low + rock[0], roll=rock[1], feet=_all(lambda k: _foot(k, out=-9 * low, up=0.0)),
                 poles={k: (SIDE[k] * 0.3, -1, 0.3) for k in range(4)}, crab=dict(back=back * low, up=-1 * low, yaw=yaw),
                 big=big or (dict(reach=-0.15 * low, lift=-6 * low) if lid else dict(reach=-0.3 * low)), small=small or dict(reach=-0.25 * low, lift=-10 * low),
                 eyes=eyes or (0, 40 * low, 0, 40 * low), feel=feel or (0, -24 * low, -24 * low), **kw)


def _reactions(r):
    """Struck, it flinches back into its doorway, its eyes ducking and its claw coming up across its face, the house
    jolting; from the side it sways away. Broken, it draws into its house with its claw across the door; it comes out
    of it eyes first."""
    hit = _pose(r, lift=1, pitch=-3, crab=dict(back=6, up=-1, pitch=-6), big=dict(lift=18, reach=-0.1, open_=10), small=dict(lift=14), eyes=(0, 30, 0, 30),
                feel=(0, -14, -14), house=(-4, 0), upper=(-2, 0, 0))
    back = _pose(r, lift=-0.5, pitch=1.5, crab=dict(back=-1), big=dict(lift=-4, open_=8), eyes=(0, -6, 0, -6), feel=(0, 8, 8), house=(2, 0), upper=(1.5, 0, 0))
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it sways away to its right
        h = _pose(r, lift=1, roll=-7 * sgn, yaw=-6 * sgn, shift=(-sgn * 2, 0), crab=dict(back=4, yaw=-8 * sgn), big=dict(lift=14, out=8 if sgn < 0 else -4),
                  small=dict(lift=12, out=8 if sgn > 0 else -4), eyes=(-20 * sgn, 20, -20 * sgn, 20), feel=(-14 * sgn, -8, -8), house=(0, -6 * sgn), upper=(0, 4 * sgn, -3 * sgn))
        b = _pose(r, roll=2 * sgn, yaw=1.5 * sgn, shift=(sgn * 0.5, 0), crab=dict(yaw=2 * sgn), eyes=(6 * sgn, -4, 6 * sgn, -4), house=(0, 2 * sgn), upper=(0, -1.5 * sgn, 1 * sgn))
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    keyed(r, "kneel", 1.0, _unwrap([(0, _pose(r)), (0.25, _pose(r, crab=dict(back=5), big=dict(lift=16), eyes=(10, 20, -10, 20), feel=(0, -10, -10))),
                                    (0.7, _hidden(r, 1.05)), (1.0, _hidden(r))]))
    peek = _pose(r, lift=-3.8, pitch=-HOUSE_TILT * 0.9, feet=_all(lambda k: _foot(k, out=-7)), crab=dict(back=8, up=-0.5), big=dict(reach=-0.1, lift=8), small=dict(reach=-0.15, lift=6),
                 eyes=(20, -6, -20, -6), feel=(10, 8, 8))
    stand = _pose(r, lift=1.5, pitch=-1, crab=dict(back=-1), big=dict(lift=10, open_=10), small=dict(lift=8), eyes=(0, -4, 0, -4), feel=(0, 12, 12), house=(2, 0))
    keyed(r, "rise", 1.3, _unwrap([(0, _hidden(r)), (0.4, peek), (0.9, stand), (1.3, _pose(r))]))
    _death(r)


def _settled(r, p, clear=0.3):
    """The pose lifted or lowered as a whole so its lowest part lies on the floor: a house fallen on its side."""
    fr = core.frames(r, core.as_sample(p))
    dy = core.lowest(r, fr, [b.name for b in r.bones if b.cubes and not b.name.startswith("ant_")]) - (core.GROUND - clear)
    x, y, z = p["body"]["pos"]
    p["body"] = {"rot": p["body"]["rot"], "pos": (x, y + dy, z)}
    return p


# where a dead crab's legs lie, in its own frame (x out, up, z) from its middle: the ones on the floor side pressed
# under it, the others up in the air and curled in
LIMP = {0: (13.0, -6.0, -10.0), 1: (15.0, -6.0, 3.0), 2: (-9.0, 6.0, -9.0), 3: (-10.0, 7.0, 1.0)}
CURLED = {0: (9.0, -4.0, -8.0), 1: (10.0, -4.0, 1.0), 2: (-6.0, 3.0, -7.0), 3: (-6.0, 4.0, -1.0)}


def _death(r):
    """Its last: it rears in its doorway with its claws flung up and its eyes stiff; it lurches to its right, then its
    legs give and the house goes over to its left, slowly and then all at once, and comes down on its side on the floor
    with a bounce, its upper storey jolting on the lower and its chimney rocking; the crab spills half out of the door
    with it, its big claw falling open across the doorway and the small one under it; its legs on the high side kick in
    the air and curl in, one twitching after; its eyes droop on their stalks and its door swings open and hangs."""
    shake = _pose(r, lift=2, pitch=-4, crab=dict(back=-2, pitch=-10), big=dict(lift=50, reach=0.4, open_=40), small=dict(lift=50, reach=0.5, open_=40), eyes=(0, -20, 0, -20),
                  feel=(0, 30, 30), house=(-4, 2), upper=(-2, 4, 3), door=30)
    lurch = _pose(r, lift=-1.5, pitch=3, roll=-7, shift=(-1, 0), feet={0: _foot(0, out=3), 1: _foot(1, out=3)}, crab=dict(back=2, pitch=6, roll=-4),
                  big=dict(lift=20, reach=0.3, open_=24, out=10), small=dict(lift=14, reach=0.3, out=6), eyes=(10, 16, -10, 16), feel=(0, 4, 4), house=(3, -4), upper=(2, -4, -4), door=-20)

    def over(roll, f, legs, eyes, door, up=(0, 0, 0), big=None, small=None, crab=None):
        """Its house rolled over by roll, the crab as crab, its legs' tips at legs in the crab's own frame (x, up, z)."""
        crab = crab or dict(back=-3 * f, pitch=10 * f)
        fr = core.frames(r, core.as_sample(_pose(r, roll=roll, pitch=-HOUSE_TILT * f, crab=crab)))
        feet = {k: ik.height(core.point(fr, "crab", (x, -h, z))) for k, (x, h, z) in legs.items()}
        return _settled(r, _pose(r, roll=roll, pitch=-HOUSE_TILT * f, crab=crab, big=big, small=small, eyes=eyes, feel=(10 * f, 26 * f, 30 * f), upper=up, door=door,
                                 feet=feet))
    tip = {0: _foot(0, out=6, side=-2), 1: _foot(1, out=6), 2: _foot(2, up=10, out=-4), 3: _foot(3, up=12, out=-4)}
    tipping = _settled(r, _pose(r, roll=28, pitch=2, shift=(1, 0), feet=tip, crab=dict(back=-1, pitch=8, roll=6), big=dict(lift=30, reach=0.4, open_=30, out=-10),
                                small=dict(lift=10, reach=0.5, open_=20, out=-12), eyes=(-10, -10, 20, -14), feel=(0, 20, 36), house=(0, 4), upper=(0, 4, 5), door=-30))
    falling = over(62, 0.5, {0: (12, -2, -12), 1: (15, -2, 4), 2: (-16, 9, -12), 3: (-17, 11, 3)}, (-20, -20, 30, -24), -50, up=(0, 6, 8),
                   big=dict(lift=40, reach=0.5, open_=40, out=-20), small=dict(lift=20, reach=0.6, open_=30, out=-16))
    lying = dict(back=-4, pitch=10, roll=-6)
    bb = dict(lift=-10, reach=0.55, open_=30, out=-30)
    sb = dict(lift=-10, reach=0.6, open_=18, out=-24)
    land = over(86, 1.0, LIMP, (-30, 30, 30, 20), -70, up=(0, -6, -8), big=dict(lift=10, reach=0.55, open_=40, out=-34), small=dict(lift=4, reach=0.6, open_=24, out=-28),
                crab=lying)
    bounce = over(80, 1.0, LIMP, (-30, 40, 30, 30), -60, up=(0, 4, 5), big=dict(lift=-2, reach=0.55, open_=34, out=-32), small=sb, crab=lying)

    def rest(f, tw=None, door=-74):
        legs = {k: tuple(LIMP[k][i] + (CURLED[k][i] - LIMP[k][i]) * f * (1.3 if tw == k else 1.0) for i in range(3)) for k in range(4)}
        return over(85, 1.0, legs, (-34, 60, 30, 55), door, up=(0, -2, -3), big=bb, small=sb, crab=lying)
    keys = [(0, _pose(r)), (0.35, shake), (0.8, lurch), (1.35, tipping), (1.75, falling), (2.05, land), (2.3, bounce), (2.55, rest(0.2)), (3.1, rest(0.6)),
            (3.5, rest(0.85, 3, -80)), (3.75, rest(0.8, None, -72)), (4.2, rest(1.0, 2, -76)), (4.45, rest(0.95)), (5.0, rest(1.0))]
    keyed(r, "death", 5.0, _unwrap(keys))


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b + str(k): 0 for b in ("thigh", "shin", "foot") for k in range(4)}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update({"upper": 0.06, "roof": 0.08, "eye_l": 0.05, "eye_r": 0.07})
    out.update(kw)
    return out


BIG_HIT = [("cl_rf", (0, 0, -5), 5.0), ("cl_r2", (0, 0, -6), 6.0)]
SMALL_HIT = [("cl_lf", (0, 0, -4), 4.0), ("cl_l2", (0, 0, -4), 4.0)]


def snip(r):
    """The snip: it turns its small claw to you, draws it back open and snips it shut on you, leaning out of its door
    (LIGHT)."""
    gather = _pose(r, crab=dict(yaw=-5, back=1), small=dict(reach=0.3, lift=12, open_=30), big=dict(lift=8), eyes=(-6, 0, -6, 0), feel=(-8, 14, 14))
    peak = _pose(r, pitch=-1.5, crab=dict(yaw=-12, back=3, pitch=-5), small=dict(reach=0.15, lift=36, open_=55, out=12), big=dict(lift=16, open_=14), eyes=(-14, -6, -14, -6),
                 feel=(-12, 20, 20), house=(-1.5, 0))
    hit = _pose(r, pitch=2.5, crab=dict(yaw=6, back=-4, pitch=6), small=dict(reach=1.0, lift=-4, open_=-2), big=dict(lift=6), eyes=(4, 4, 4, 4), feel=(6, 6, 6), house=(2, 0))
    held = _pose(r, pitch=3, crab=dict(yaw=7, back=-4.5, pitch=7), small=dict(reach=1.05, lift=-6, open_=-2), big=dict(lift=6), eyes=(4, 5, 4, 5), feel=(6, 4, 4), house=(2.5, 0))
    sink = _pose(r, pitch=2.5, crab=dict(yaw=6, back=-4, pitch=6), small=dict(reach=0.95, lift=-5, open_=0), big=dict(lift=6), eyes=(4, 4, 4, 4), feel=(5, 5, 5), house=(2, 0))
    back = _pose(r, pitch=1, crab=dict(yaw=2, back=-1, pitch=2), small=dict(reach=0.3, lift=4, open_=6), big=dict(lift=6, open_=6), house=(1, 0))
    return Move("snip", "LIGHT", 10, 4, 14, through=sink, track=8.0, rng=(0, 3.5), kinds=("combo", "near"), damage=0.9, knock=0.5, plant=(), hit=SMALL_HIT,
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def crush(r):
    """The crush: its big claw drawn up and open beside its face, it leans out of its door and brings it down and
    across on you and shuts it (LIGHT)."""
    gather = _pose(r, crab=dict(yaw=5, back=1), big=dict(reach=0.25, lift=20, open_=30), small=dict(lift=6), eyes=(6, -2, 6, -2), feel=(8, 14, 14), house=(-1, 0))
    peak = _pose(r, pitch=-2, crab=dict(yaw=10, back=3, pitch=-6, roll=-3), big=dict(reach=0.2, lift=40, open_=55, out=10), small=dict(lift=10), eyes=(10, -6, 10, -6),
                 feel=(12, 22, 22), house=(-2.5, -1), upper=(-1, 2, 0))
    hit = _pose(r, pitch=3, crab=dict(yaw=-7, back=-5, pitch=8, roll=3), big=dict(reach=1.0, lift=-4, open_=-4, out=-6), small=dict(lift=4), eyes=(-4, 6, -4, 6), feel=(-6, 4, 4),
                house=(2.5, 1), upper=(1.5, -2, 0))
    held = _pose(r, pitch=3.5, crab=dict(yaw=-8, back=-5.5, pitch=9, roll=3), big=dict(reach=1.05, lift=-6, open_=-4, out=-7), small=dict(lift=4), eyes=(-4, 7, -4, 7),
                 feel=(-6, 2, 2), house=(3, 1), upper=(2, -2, 0))
    sink = _pose(r, pitch=3, crab=dict(yaw=-7, back=-5, pitch=8, roll=3), big=dict(reach=0.95, lift=-5, open_=-2, out=-6), small=dict(lift=4), eyes=(-4, 6, -4, 6), feel=(-5, 3, 3),
                 house=(2.5, 1), upper=(1.5, -2, 0))
    back = _pose(r, pitch=1, crab=dict(yaw=-2, back=-1.5, pitch=3), big=dict(reach=0.35, lift=4, open_=8), small=dict(lift=4), feel=(0, 8, 8), house=(1, 0))
    return Move("crush", "LIGHT", 11, 4, 14, through=sink, track=8.0, rng=(0, 4.0), kinds=("combo", "near"), damage=1.1, knock=0.8, plant=(), hit=BIG_HIT,
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def hammer(r):
    """The Claw Hammer (HEAVY): it rears back in its doorway, raising its big claw high over its house, holds it there
    trembling, and brings it down on the floor before it."""
    gather = _pose(r, pitch=-1.5, crab=dict(back=2), big=dict(lift=45, reach=0.3, open_=10), small=dict(lift=10), eyes=(0, -10, 0, -10), feel=(0, 20, 20), house=(-1.5, 0))
    rise = _pose(r, lift=1.5, pitch=-4, crab=dict(back=3, up=1.5, pitch=-8), big=dict(lift=110, reach=0.6, open_=16), small=dict(lift=16), eyes=(4, -20, -4, -20),
                 feel=(0, 30, 30), house=(-3, 0), upper=(-2, 0, 0))
    peak = _pose(r, lift=2, pitch=-5.5, crab=dict(back=3.5, up=2, pitch=-10), big=dict(lift=130, reach=0.75, open_=22), small=dict(lift=20), eyes=(6, -24, -6, -24),
                 feel=(0, 34, 34), house=(-4, 0), upper=(-3, 0, 0))
    hit = _pose(r, lift=-1.5, pitch=5, crab=dict(back=-6, up=-1, pitch=14), big=dict(lift=-22, reach=1.0, open_=4), small=dict(lift=0), eyes=(0, 10, 0, 10), feel=(0, -6, -6),
                house=(4, 0), upper=(3, 0, 0))
    held = _pose(r, lift=-2, pitch=5.5, crab=dict(back=-6.5, up=-1.2, pitch=15), big=dict(lift=-23, reach=1.02, open_=4), small=dict(lift=0), eyes=(0, 12, 0, 12),
                 feel=(0, -8, -8), house=(4.5, 0), upper=(3.5, 0, 0))
    sink = _pose(r, lift=-2, pitch=5, crab=dict(back=-6, up=-1, pitch=14), big=dict(lift=-22, reach=1.0, open_=6), small=dict(lift=0), eyes=(0, 10, 0, 10), feel=(0, -6, -6),
                 house=(4, 0), upper=(3, 0, 0))
    back = _pose(r, lift=-0.5, pitch=2, crab=dict(back=-2, pitch=5), big=dict(lift=-6, reach=0.5, open_=8), feel=(0, 6, 6), house=(1.5, 0), upper=(1, 0, 0))
    return Move("hammer", "HEAVY", 18, 6, 22, through=sink, title="Claw Hammer", hold=8, track=5.0, rng=(0, 4.5), kinds=("near",), damage=1.4, knock=1.3, plant=(),
                hit=BIG_HIT + [("cl_r1", (0, 0, -3), 5.0)], stages={"windup": [(0.3, gather), (0.7, rise), (1.0, peak)], "active": [(0.5, hit), (1.0, held)],
                                                                   "recover": [(0.5, back)]}, lag=_lag())


def scuttle(r):
    """The Scuttle (HEAVY, a gap closer): it crouches with its big claw thrust out open ahead of it, and rushes at you
    on its four legs, the house lurching on its back, and shuts the claw on you."""
    crouch = _pose(r, lift=-2, pitch=2, crab=dict(back=-1, pitch=4), big=dict(reach=0.5, lift=10, open_=30), small=dict(lift=6), eyes=(0, -6, 0, -6), feel=(0, -6, -6), house=(-2, 0))
    aim = _pose(r, lift=-3, pitch=3, crab=dict(back=-2, pitch=6), big=dict(reach=0.85, lift=6, open_=50), small=dict(lift=8), eyes=(0, -8, 0, -8), feel=(0, -14, -14), house=(-3, 0),
                upper=(-2, 0, 0))
    act = []
    for k in range(1, 9):
        u = k / 8
        s = math.sin(4 * math.pi * u)
        act.append((u, _pose(r, lift=-2 + 0.8 * abs(s), pitch=3 + s, roll=2 * math.sin(2 * math.pi * u), feet=_gait(u * 2 % 1.0, 30, 0.5, 8), crab=dict(back=-3, pitch=6),
                             big=dict(reach=0.95, lift=4, open_=50 if u < 0.9 else -4), small=dict(lift=8), eyes=(0, -8, 0, -8), feel=(0, -16, -16),
                             house=(-2 + 2 * s, 4 * math.sin(2 * math.pi * u - 0.7)), upper=(-2, 2 * math.sin(2 * math.pi * u - 1.4), 2 * math.sin(2 * math.pi * u - 1.4)))))
    sink = _pose(r, lift=-2, pitch=3, crab=dict(back=-3, pitch=6), big=dict(reach=0.95, lift=3, open_=-2), small=dict(lift=6), eyes=(0, -4, 0, -4), feel=(0, -8, -8), house=(3, 0),
                 upper=(2, 0, 0))
    back = _pose(r, lift=-1, pitch=1.5, crab=dict(back=-1, pitch=3), big=dict(reach=0.5, lift=4, open_=6), feel=(0, 6, 6), house=(1, 0))
    return Move("scuttle", "HEAVY", 14, 12, 18, through=sink, title="Scuttle", track=6.0, lock=10, advance=8.0, rng=(6, 16), kinds=("gap",), damage=1.2, knock=1.2, plant=(),
                hit=BIG_HIT, stages={"windup": [(0.3, crouch), (1.0, aim)], "active": act, "recover": [(0.5, back)]}, lag=_lag())


def heave(r):
    """For whoever is behind it (LIGHT): it braces its front legs, tips its house forward on its back, and heaves it
    back hard into them."""
    brace = {0: _foot(0, dz=-3), 2: _foot(2, dz=-3)}
    gather = _pose(r, pitch=2, feet=brace, crab=dict(pitch=3), big=dict(lift=-4), eyes=(30, 0, -30, 0), feel=(0, -6, -6), house=(5, 0), upper=(3, 0, 0))
    peak = _pose(r, lift=-1, pitch=4, shift=(0, -1.5), feet=brace, crab=dict(pitch=5), big=dict(lift=-8, reach=0.2), small=dict(lift=-6), eyes=(40, 4, -40, 4), feel=(0, -10, -10),
                 house=(12, 0), upper=(6, 0, 0))
    hit = _pose(r, lift=1, pitch=-4, shift=(0, 3), feet=brace, crab=dict(pitch=-3), big=dict(lift=12), small=dict(lift=10), eyes=(36, -4, -36, -4), feel=(0, 10, 10),
                house=(-16, 0), upper=(-8, 0, 0))
    held = _pose(r, lift=1.2, pitch=-4.5, shift=(0, 3.5), feet=brace, crab=dict(pitch=-3.5), big=dict(lift=13), small=dict(lift=11), eyes=(36, -5, -36, -5), feel=(0, 11, 11),
                 house=(-18, 0), upper=(-9, 0, 0))
    sink = _pose(r, lift=1, pitch=-4, shift=(0, 3), feet=brace, crab=dict(pitch=-3), big=dict(lift=12), small=dict(lift=10), eyes=(34, -4, -34, -4), feel=(0, 10, 10),
                 house=(-15, 0), upper=(-7, 0, 0))
    back = _pose(r, pitch=-1, shift=(0, 1), crab=dict(pitch=-1), big=dict(lift=4), eyes=(10, 0, -10, 0), house=(-4, 0), upper=(-2, 0, 0))
    return Move("heave", "LIGHT", 10, 4, 14, through=sink, track=0.0, rng=(0, 4.5), kinds=("behind",), damage=1.0, knock=1.3, plant=(),
                hit=[("upper", (0, -6, 12), 10.0), ("house", (0, -8, 13), 10.0)],
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def floor(r):
    """The Shifting Floor: it rises on its legs with its claws up, then drums them on the floor in a running ripple, one
    after another round and round, its house juddering and its door rattling."""
    up_ = _pose(r, lift=2, pitch=-1, crab=dict(back=1, pitch=-3), big=dict(lift=30, open_=20), small=dict(lift=30, open_=20), eyes=(16, -8, -16, -8), feel=(0, 24, 24), house=(-1.5, 0))
    high = _pose(r, lift=3, pitch=-2, crab=dict(back=1.5, pitch=-5), big=dict(lift=40, open_=26), small=dict(lift=40, open_=26), eyes=(22, -10, -22, -10), feel=(0, 30, 30),
                 house=(-2, 0), upper=(-1, 0, 0))
    order = (0, 1, 3, 2)
    act = []
    n = 24
    for k in range(1, n + 1):
        u = k / n
        feet = {}
        for j, leg in enumerate(order):  # each leg lifts and stamps in turn, three times round
            q = (3 * u - j / 4) % 1.0
            if q < 0.25:
                feet[leg] = _foot(leg, up=8 * math.sin(math.pi * q / 0.25), out=1.5 * math.sin(math.pi * q / 0.25))
        jud = math.sin(2 * math.pi * 6 * u)
        act.append((u, _pose(r, lift=2.5 + 0.6 * jud, pitch=-1.5, roll=1.2 * math.sin(2 * math.pi * 3 * u), feet=feet, crab=dict(back=1.5, pitch=-4 + 2 * jud),
                             big=dict(lift=38 + 4 * jud, open_=24 + 10 * jud), small=dict(lift=38 - 4 * jud, open_=24 - 10 * jud),
                             eyes=(22 * math.sin(2 * math.pi * 1.5 * u), -8, -22 * math.sin(2 * math.pi * 1.5 * u + 1), -8), feel=(10 * jud, 26, 26),
                             house=(-2 + 1.5 * jud, 2 * math.sin(2 * math.pi * 3 * u - 0.6)), upper=(-1 + jud, 2 * math.sin(2 * math.pi * 3 * u - 1.2), 1.5 * jud),
                             door=14 * math.sin(2 * math.pi * 6 * u + 0.5))))
    back = _pose(r, lift=0.5, crab=dict(back=0.5), big=dict(lift=8, open_=6), small=dict(lift=8), feel=(0, 8, 8))
    return Move("floor", "NONE", 12, 26, 14, through=act[-1][1], title="Shifting Floor", track=0.0, ability="Shifting Floor", origin=(0, 0, 0), rng=(0, 14),
                kinds=("area",), damage=0, knock=0, plant=(), stages={"windup": [(0.3, up_), (1.0, high)], "active": act, "recover": [(0.5, back)]}, lag=_lag())


def doorstep(r):
    """The Door Step: it backs into its house, folding its legs in under the walls, and swings its door shut; the house
    sits shut on the floor, rocking; then the door opens and it comes out, eyes first, somewhere else."""
    duck = _pose(r, lift=-1, crab=dict(back=6, up=-0.5), big=dict(lift=6, reach=-0.1), small=dict(reach=-0.1), eyes=(10, 20, -10, 20), feel=(0, -10, -10), house=(-1, 0))
    shut = _hidden(r, 1.0, lid=False, up=0.4, back=16, door=110)
    act = []
    for k in range(1, 9):  # shut in, the house rocks on its folded legs while the crab turns about inside it
        u = k / 8
        w = 2 * math.pi * 2 * u
        act.append((u, _hidden(r, 1.0 + 0.06 * math.sin(w + 0.5), lid=False, back=16, up=0.8, rock=(2 * math.sin(w), 3 * math.sin(w + 0.8) * (1 - 0.5 * u)), yaw=8 * math.sin(w - 0.6),
                               big=dict(reach=-0.3, lift=5 + 8 * math.sin(w + 1.2)), small=dict(reach=-0.25, lift=-10 + 10 * math.sin(w - 0.4)),
                               eyes=(26 * math.sin(w * 0.75), 20 + 10 * math.sin(w), -26 * math.sin(w * 0.75 + 1.0), 20 + 10 * math.sin(w + 1.5)),
                               feel=(14 * math.sin(w - 1.0), -10, -10), door=110 - 5 * math.sin(w), house=(0, 1.5 * math.sin(w - 0.6)),
                               upper=(1.5 * math.sin(w - 0.7), 3 * math.sin(w - 0.4), 2 * math.sin(w - 1.0)))))
    peek = _pose(r, lift=-3.4, pitch=-HOUSE_TILT * 0.8, feet=_all(lambda k: _foot(k, out=-6)), crab=dict(back=8), big=dict(reach=-0.1, lift=8), small=dict(reach=-0.1, lift=6), eyes=(24, -6, -24, -6),
                 feel=(12, 10, 10), door=20)
    return Move("doorstep", "NONE", 12, 16, 18, through=act[-1][1], title="Door Step", track=0.0, ability="Door Step", origin=(0, 0, 0), rng=(0, 99), kinds=("ranged",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.3, duck), (1.0, shut)], "active": act, "recover": [(0.45, peek)]}, lag=_lag())


def rooms(r):
    """Other Rooms: it draws in with its claws up, and its house shakes, rocking hard from side to side and its upper
    storey twisting on the lower, its door banging open and shut."""
    draw = _pose(r, crab=dict(back=4), big=dict(lift=24, open_=10), small=dict(lift=24), eyes=(20, -10, -20, -10), feel=(0, 20, 20), house=(-2, 0))
    brace = _pose(r, lift=-1, crab=dict(back=6, up=-0.5), big=dict(lift=34, open_=20), small=dict(lift=34, open_=16), eyes=(28, -14, -28, -14), feel=(0, 26, 26), house=(-3, 0),
                  upper=(-2, 0, 0), door=40)
    act = []
    n = 18
    for k in range(1, n + 1):
        u = k / n
        sh = math.sin(2 * math.pi * 4 * u)
        act.append((u, _pose(r, lift=-0.3 + 0.8 * abs(sh), roll=2.2 * sh, crab=dict(back=6, up=-0.5, roll=-3 * sh), big=dict(lift=34 + 6 * sh, open_=20 + 16 * sh),
                             small=dict(lift=34 - 6 * sh, open_=16 - 12 * sh), eyes=(28 * sh, -14, -28 * sh, -14), feel=(14 * sh, 26, 26),
                             house=(-3 + 2 * math.sin(2 * math.pi * 8 * u), 7 * math.sin(2 * math.pi * 4 * u - 0.5)),
                             upper=(2 * math.sin(2 * math.pi * 4 * u - 1.0), 10 * math.sin(2 * math.pi * 4 * u - 1.3), 6 * math.sin(2 * math.pi * 4 * u - 1.0)),
                             door=55 + 55 * math.sin(2 * math.pi * 6 * u))))
    back = _pose(r, crab=dict(back=1), big=dict(lift=8, open_=6), small=dict(lift=8), feel=(0, 8, 8), door=10)
    return Move("rooms", "NONE", 14, 26, 16, through=act[-1][1], title="Other Rooms", track=0.0, ability="Other Rooms", origin=(0, 2.0, 0), rng=(0, 99), kinds=("area",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.3, draw), (1.0, brace)], "active": act, "recover": [(0.5, back)]}, lag=_lag())


def ceiling(r):
    """The Falling Ceiling (PERILOUS, the signature): it rears up on its back legs, its house tipped back on the floor,
    its front legs lifted and both claws reaching up as high as they go, open, trembling; it takes hold of the air,
    shuts both claws on it, and pulls the ceiling down, coming down hard on its front legs."""
    front_up = {0: _foot(0, up=12, dz=-4, out=-2), 2: _foot(2, up=12, dz=-4, out=-2)}
    w = []
    n = 6
    for k in range(1, n + 1):
        f = k / n
        tr = math.sin(k * 2.6) * f * f
        e = core.ease_io(f)
        feet = {j: (lambda a, b: tuple(a[i] + (b[i] - a[i]) * e for i in range(3)))(REST_FOOT[j], front_up[j]) for j in (0, 2)}
        w.append((f if k > 1 else 0.3, _pose(r, lift=7 * e, pitch=-16 * e, yaw=1.5 * tr, feet=feet, crab=dict(back=-2 * e, up=2 * e, pitch=-14 * e),
                                            big=dict(lift=140 * e, reach=0.9 * e, open_=45 * e + 4 * tr), small=dict(lift=140 * e, reach=1.0 * e, open_=40 * e - 4 * tr),
                                            eyes=(10 * tr, -30 * e, -10 * tr, -30 * e), feel=(6 * tr, 40 * e, 40 * e), house=(-4 * e + tr, 1.5 * tr), upper=(-3 * e, 2 * tr, 1.5 * tr))))
    top = w[-1][1]
    grip = _pose(r, lift=7.5, pitch=-17, feet={j: front_up[j] for j in (0, 2)}, crab=dict(back=-2, up=2, pitch=-15), big=dict(lift=142, reach=0.92, open_=-4),
                 small=dict(lift=142, reach=1.0, open_=-4), eyes=(0, -32, 0, -32), feel=(0, 42, 42), house=(-4, 0), upper=(-3, 0, 0))
    pull = _pose(r, lift=-2, pitch=6, crab=dict(back=-6, up=-1, pitch=12), big=dict(lift=-10, reach=0.9, open_=-4), small=dict(lift=-10, reach=1.0, open_=-4), eyes=(0, 14, 0, 14),
                 feel=(0, -10, -10), house=(6, 0), upper=(4, 0, 0))
    held = [(0.2, grip)]
    for k in range(1, 5):  # it hauls the ceiling down and holds it there, juddering
        u = 0.45 + 0.55 * k / 4
        jd = math.sin(k * 2.3)
        q = _pose(r, lift=-2.5 + 0.6 * jd, pitch=6.5 + jd, roll=1.5 * jd, crab=dict(back=-6, up=-1, pitch=12 + 2 * jd), big=dict(lift=-12 + 3 * jd, reach=0.9, open_=-4),
                  small=dict(lift=-12 - 3 * jd, reach=1.0, open_=-4), eyes=(8 * jd, 14, -8 * jd, 14), feel=(6 * jd, -10, -10), house=(6 + 2 * jd, 2 * jd), upper=(4 + jd, 3 * jd, 2 * jd),
                  door=20 * jd)
        held.append((u, q))
    mv_act = [(0.2, grip), (0.45, pull)] + held[1:]
    back = _pose(r, lift=-0.5, pitch=2, crab=dict(back=-2, pitch=4), big=dict(lift=-2, reach=0.4, open_=8), small=dict(reach=0.4), feel=(0, 6, 6), house=(2, 0), upper=(1, 0, 0))
    return Move("ceiling", "PERILOUS", 26, 24, 22, through=held[-1][1], title="Falling Ceiling", signature=True, track=3.0, ability="Falling Ceiling", origin=(0, 3.0, 2.0),
                rng=(0, 16), kinds=("area", "ranged"), damage=0, knock=0, plant=(), stages={"windup": w, "active": mv_act, "recover": [(0.5, back)]}, lag=_lag())


def wake(r):
    """Its intro, from inside its house: its eyes come up out of the doorway and look about, one way and the other; its
    claws come out; it stands up out of its house, the house lifting off the floor on its back, and raises both claws
    wide and open at you, clacking them."""
    peek = _pose(r, lift=-3.8, pitch=-HOUSE_TILT * 0.9, feet=_all(lambda k: _foot(k, out=-8)), crab=dict(back=10, up=-0.5), big=dict(reach=-0.12, lift=8), small=dict(reach=-0.2, lift=6),
                 eyes=(26, -6, -10, -4), feel=(10, 6, 6))
    look = _pose(r, lift=-3.4, pitch=-HOUSE_TILT * 0.8, feet=_all(lambda k: _foot(k, out=-6)), crab=dict(back=7), big=dict(reach=0, lift=6), small=dict(reach=0, lift=6),
                 eyes=(-20, -8, -30, -6), feel=(-12, 12, 12), door=10)
    act = [(0.3, _pose(r, lift=2, pitch=-2, crab=dict(back=-1, pitch=-6), big=dict(lift=50, out=16, open_=50), small=dict(lift=50, out=16, open_=45), eyes=(10, -14, -10, -14),
                       feel=(0, 30, 30), house=(-3, 0), upper=(-2, 0, 0)))]
    for k in range(1, 4):  # it clacks them
        u = 0.3 + 0.7 * k / 3
        c = 1 if k % 2 else -1
        act.append((u - 0.12, _pose(r, lift=2, pitch=-2, crab=dict(back=-1, pitch=-6), big=dict(lift=52, out=16, open_=-2), small=dict(lift=52, out=16, open_=-2),
                                    eyes=(14 * c, -14, -14 * c, -14), feel=(8 * c, 30, 30), house=(-3, 1.5 * c), upper=(-2, 2 * c, 0))))
        act.append((u, _pose(r, lift=2.2, pitch=-2, crab=dict(back=-1, pitch=-6), big=dict(lift=48, out=14, open_=46), small=dict(lift=48, out=14, open_=40),
                             eyes=(10 * c, -12, -10 * c, -12), feel=(-6 * c, 28, 28), house=(-3, -1.5 * c), upper=(-2, -2 * c, 0))))
    mv = Move("wake", "NONE", 14, 30, 14, through=act[-1][1], track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, peek), (1.0, look)], "active": act, "recover": [(0.5, _pose(r, big=dict(lift=10), small=dict(lift=8), feel=(0, 10, 10)))]},
              lag=_lag())
    return mv


STANDS = {"snip": (0, 1, 2, 3), "crush": (0, 1, 2, 3), "hammer": (0, 1, 2, 3), "scuttle": (), "heave": (1, 3), "floor": (), "doorstep": (), "rooms": (0, 1, 2, 3),
          "ceiling": (1, 3), "wake": ()}


def _fidget(r, clip, ramp=0.2):
    """Its mouthparts never stop working: a quick flutter written into a clip after it is made, faded at its ends."""
    c = r.clips[clip]
    L, n = c.length, int(round(c.length / core.TICK))
    old = c.tracks.get(("mouth", "rot"))
    keys = []
    for k in range(n + 1):
        t = min(L, k * core.TICK)
        e = 1.0 if c.loop else max(0.0, min(1.0, t / ramp, (L - t) / ramp))
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        base[0] += e * 9 * math.sin(2 * math.pi * 3.0 * t) * (0.6 + 0.4 * math.sin(2 * math.pi * 0.4 * t))
        keys.append((t, tuple(base), "cat"))
    if c.loop:  # a loop's flutter ends where it began
        keys[-1] = (keys[-1][0], keys[0][1], "cat")
    c.tracks[("mouth", "rot")] = keys


LEG_GROUND = [([f"shin{k}"], [(f"thigh{k}", 0, 30), (f"shin{k}", 0, 40)]) for k in range(4)]
CLAW_GROUND = [([f"cl_{nm}1", f"cl_{nm}2", f"cl_{nm}f"], [(f"cl_{nm}2", 0, 60, -1), (f"cl_{nm}0", 0, 40, -1)]) for nm in ("r", "l")]

ANT_GROUND = [([f"ant_{nm}1", f"ant_{nm}2"], [(f"ant_{nm}0", 0, 60, -1), (f"ant_{nm}1", 0, 40, -1)]) for nm in ("l", "r")]


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [snip(r), crush(r), hammer(r), scuttle(r), heave(r), floor(r), doorstep(r), rooms(r), ceiling(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    for name in list(r.clips):
        _fidget(r, name)
    core.ground_pass(r, LEG_GROUND, clips=list(r.clips), skip=())
    core.ground_pass(r, CLAW_GROUND, clips=list(r.clips), skip=())
    core.ground_pass(r, ANT_GROUND, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): a house's own noises (doors, floorboards, a creaking frame) and a crab's clicks; chosen on
# paper, not heard in game.
SOUNDS = {
    "tell_light": [("block.wooden_door.open", 1.2, 0.6), ("entity.armadillo.scute_drop", 1.4, 0.7)],
    "tell_heavy": [("block.wooden_trapdoor.open", 0.8, 0.8), ("block.wood.break", 0.7, 0.8)],
    "tell_perilous": [("block.wooden_door.close", 0.5, 1.0), ("block.chiseled_bookshelf.pickup", 0.6, 0.8), ("block.bell.resonate", 0.7, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 0.6), ("entity.armadillo.scute_drop", 1.2, 0.8)],
    "swing_heavy": [("entity.player.attack.knockback", 0.8, 0.7), ("block.wood.fall", 0.8, 0.8)],
    "cast": [("block.wooden_door.close", 0.8, 0.9), ("block.amethyst_block.resonate", 0.8, 0.8)],
    "impact": [("block.wood.break", 0.6, 1.0), ("entity.zombie.attack_wooden_door", 0.8, 0.9)],
    "roar": [("block.wooden_door.open", 0.6, 1.0), ("entity.armadillo.hurt", 0.6, 0.8)],
    "step": [("block.wood.step", 0.8, 0.6), ("entity.armadillo.step", 0.9, 0.7)],
}
MOVE_SOUNDS = {"floor": {"swing": "cast", "impact": ""}, "doorstep": {"swing": "cast", "impact": ""}, "rooms": {"swing": "roar", "impact": ""},
               "ceiling": {"swing": "roar"}, "wake": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "snip", "weight": 3, "when": "near", "then": [["crush"], ["crush", "hammer"]]},
            {"move": "hammer", "weight": 2, "when": "near"},
            {"move": "scuttle", "weight": 3, "when": "far", "then": [["crush"]]},
            {"move": "floor", "weight": 2},
            {"move": "doorstep", "weight": 2, "when": "far"},
            {"move": "heave", "weight": 3, "when": "behind"},
        ]},
        {"transition": "rooms", "pool": [
            {"move": "snip", "weight": 3, "when": "near", "then": [["crush"], ["crush", "hammer"]]},
            {"move": "hammer", "weight": 2, "when": "near"},
            {"move": "ceiling", "weight": 2},
            {"move": "floor", "weight": 2},
            {"move": "rooms", "weight": 1},
            {"move": "doorstep", "weight": 2, "then": [["snip", "crush"]]},
            {"move": "scuttle", "weight": 2, "when": "far", "then": [["crush"]]},
            {"move": "heave", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}


def probe():
    r = body()
    fr = core.frames(r, {})
    for b, p in (("body", (0, 0, 0)), ("crab", (0, 0, 0)), ("cl_r2", (0, 0, -12)), ("foot0", (0, 0, 0)), ("foot1", (0, 0, 0)), ("roof", (0, -10, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
