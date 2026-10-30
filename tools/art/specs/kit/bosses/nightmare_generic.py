"""The Faceless (SPEC §35.11, docs/bosses/ROLLOUT.md): a Nightmare, a Duelist, the fear of the shape in the corner of
the room. The Mannequin, picked 2026-10-30: "It was standing there the whole time."

An artist's jointed wooden mannequin, the kind that stands on a desk to be drawn from, grown twice as tall as a door:
pale beech worn smooth, with a ball at every joint, hinge pins in its elbows and knees, an egg for a head, long flat
hands with their fingers jointed once, and the broken-off peg in the small of its back where it stood on its stand. It
has no face. Someone began to draw one on it, the way you do: the line down the middle of the head and the line across
it for the eyes, in pencil, going on round its head; where the eyes would go the pencil has been rubbed out, more than
once. A shop's paper tag hangs from its left wrist on a string. The wood is cracked down its breast and chipped at one
shoulder; its hands are grey with handling. Nothing on it glows.

Moves (SPEC §35.11): the Reach (LIGHT: it cocks its right hand back by its ear and spears it straight out at you, its
fingers flat); the Grasp (LIGHT, after the Reach: its left hand sweeps across from wide out at its side and clutches
shut); Folding (HEAVY, its delay: it bends forward at its waist ball, too far, until its head hangs by its knees and
its arms lie on the floor; it stays there; then it unfolds in one snap, both arms flung up through you); Corner of Your
Eye (its gap closer, ability Corner of Your Eye: it goes still and turns its head away from you, and then it is just
there, crouched, reaching); Lights Off (ability Lights Off: it holds its hand up over its head with the fingers spread
and closes them, twice, as if pinching out a candle); Stare (ability Stare: it leans its blank face at you and tilts
it slowly over to one side, further than a neck should, and holds it there trembling); Everywhere (ability Everywhere:
it snaps from pose to pose like a model for drawing: pointing at you, arms up, wrapped round itself); Wrong Way Round
(LIGHT, for whoever is behind it: its head turns round to look behind it, then its whole chest swivels round on its
waist ball while its legs stay where they are, and its arm swings across behind it); It Is Closer (into the second
phase: its joints crack one after another and it takes a step); and Hold Still (PERILOUS, the signature: it rises tall
with its arms straight out at its sides, creaks, winds back, and its body spins round twice on its waist ball, arms out
at full reach). It walks too smoothly, its head level; it runs leaning in with its arms hanging back. It dies going
down on its knees as if its strings were cut, folding forward over them, and its head comes off and rolls away.
"""
import math

from kit import core, ik
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, SIDES, hexc

ID = "nightmare_generic"
SCALE = 1.8

# Kit bodies are drawn at true size (16 pixels a block). It stands 4.2 blocks to the top of its head; its hips are 2.1
# blocks up and its fingertips hang to the tops of its knees. Value story: the pale wood is the lightest thing on it,
# the ball joints a shade darker and greyer where hands have worn them, the pencil lines on its head the darkest.
THIGH, SHIN = 16, 14  # the legs' bones (hip to knee, knee to ankle); the ankle stands 3 off the floor
UPPER, FORE = 12, 10  # the arms' bones (shoulder to elbow, elbow to wrist)
DROP = 1.5  # its hips sit this much lower than straight legs would put them: its knees are never locked
FOOT = {"l": (3.5, 3.0, 0.0), "r": (-3.5, 3.0, -1.0)}  # the ankles at rest (x, height, z): the right a little ahead
TOE = {"l": (1, 0, -0.12), "r": (1, 0, 0.12)}  # each foot's +x at rest: turned out a touch


def body():
    r = R.Rig(ID, tex=(256, 256))
    pel = r.bone("pelvis", (0, 24 - 3 - SHIN - THIGH - 1 + DROP, 0))
    pel.cube("pelvis", -4.5, -3, -3, 9, 5, 6)
    pel.cube("seat", -3.5, 2, -2.5, 7, 1, 5)
    pel.cube("peg", -1, -1, 3, 2, 2, 4)  # where it stood on its stand, broken off
    # the legs: a ball at the hip, a thigh that tapers to a ball at the knee with its hinge pin outside, a calf, a thin
    # shin, a ball at the ankle and a plain wedge of a foot
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * 3, 1, 0))
        th.cube("hipb_" + nm, -2.5, -2.5, -2.5, 5, 5, 5)
        th.cube("thighu_" + nm, -2.5, 2, -2.5, 5, 7, 5)
        th.cube("thighl_" + nm, -2, 9, -2, 4, 6, 4)
        sn = th.bone("shin_" + nm, (0, THIGH, 0))
        sn.cube("knee_" + nm, -2, -2, -2, 4, 4, 4)
        sn.cube("kpin_" + nm, 2 if side > 0 else -3, -1, -1, 1, 2, 2)
        sn.cube("calf_" + nm, -2, 2, -2, 4, 6, 4)
        sn.cube("shinl_" + nm, -1.5, 8, -1.5, 3, 5, 3)
        ft = sn.bone("foot_" + nm, (0, SHIN, 0))
        ft.cube("ankle_" + nm, -1.5, -1.5, -1.5, 3, 3, 3)
        ft.cube("instep_" + nm, -1.5, 0.5, -4, 3, 1, 5)
        ft.cube("sole_" + nm, -2, 1, -6, 4, 2, 8)
    # the waist ball, and the chest in one piece: a narrow belly under the ribcage, a little swell of breast, the
    # curve of the back, the shoulders' yoke
    sp = pel.bone("spine", (0, -3, 0))
    sp.cube("waist", -3, -3.5, -3, 6, 4, 6)
    ch = sp.bone("chest", (0, -3, 0))
    ch.cube("abd", -4, -3, -3, 8, 3, 6)
    ch.cube("rib", -5.5, -11, -3.5, 11, 8, 7)
    ch.cube("breast", -4.5, -10, -4.5, 9, 5, 1)
    ch.cube("back", -4.5, -10, 3.5, 9, 6, 1)
    ch.cube("yoke", -5, -12, -3, 10, 1, 6)
    # a ball at the neck's foot, a plain neck, and the egg of a head, narrow end down
    nk = ch.bone("neck", (0, -12, -0.5))
    nk.cube("neckb", -2, -1.5, -2, 4, 2, 4)
    nk.cube("neck", -1.5, -6, -1.5, 3, 5, 3)
    hd = nk.bone("head", (0, -6, 0))
    hd.cube("chin", -2.5, -2, -3.5, 5, 2, 6)
    hd.cube("head", -3.5, -9, -4, 7, 7, 8)
    hd.cube("crown", -3, -10, -3.5, 6, 1, 7)
    # the arms: a ball at the shoulder, an upper arm that tapers, a ball at the elbow with its pin, a forearm thinning
    # to a ball at the wrist; a long flat hand, palm in, its thumb along the front, its fingers jointed once
    for side, nm in ((1, "l"), (-1, "r")):
        arm = ch.bone("arm_" + nm, (side * 6.5, -10, 0), (0, 0, -side * 5))
        arm.cube("shb_" + nm, -2, -2, -2, 4, 4, 4)
        arm.cube("armu_" + nm, -2, 1.5, -2, 4, 5, 4)
        arm.cube("arml_" + nm, -1.5, 6.5, -1.5, 3, 5, 3)
        fa = arm.bone("forearm_" + nm, (0, UPPER, 0), (-6, 0, 0))
        fa.cube("elb_" + nm, -1.5, -1.5, -1.5, 3, 3, 3)
        fa.cube("epin_" + nm, 1.5 if side > 0 else -2.5, -1, -1, 1, 2, 2)
        fa.cube("fore_" + nm, -1.5, 1.5, -1.5, 3, 5, 3)
        fa.cube("forel_" + nm, -1, 6.5, -1, 2, 3, 2)
        hn = fa.bone("hand_" + nm, (0, FORE, 0))
        hn.cube("wrb_" + nm, -1, -1, -1, 2, 2, 2)
        hn.cube("palm_" + nm, -1, 0.5, -1.5, 2, 4, 3)
        hn.cube("thumb_" + nm, -0.5, 0.5, -2.5, 1, 3, 1)
        fg = hn.bone("fing_" + nm, (0, 4.5, 0), (0, 0, side * 8))
        fg.cube("fing_" + nm, -0.5, 0, -1.5, 1, 3, 3)
        tp = fg.bone("tip_" + nm, (0, 3, 0), (0, 0, side * 10))
        tp.cube("tip_" + nm, -0.5, 0, -1.5, 1, 3, 3)
    # the shop's tag on its left wrist: a string and a card
    tg = r.by_name["hand_l"].bone("tag", (1, 0.5, 0.5), (0, 0, -8))
    tg.cube("tagstr", 0, 0, 0, 0, 3, 1)
    tg.cube("tagcard", 0, 3, -1, 0, 3, 3)
    # the feet where they stand at rest, flat, and the legs' rest from the planter's own solution for them
    for s in "lr":
        ik.rest_limb(r, "thigh_" + s, "shin_" + s, "foot_" + s, FOOT[s], (0, 0, -1))
    fr = core.frames(r, {})
    for s in "lr":
        r.by_name["foot_" + s].rot = ik.orient(r, fr, "foot_" + s, (0, 1, 0), TOE[s])
    p = core.plant(r, {}, ("l", "r"))
    for s in "lr":
        for b in ("thigh_", "shin_", "foot_"):
            bn = r.by_name[b + s]
            bn.rot = tuple(bn.rot[i] + p[b + s][i] for i in range(3))
    r.look = [("head", 0.55, 0.5), ("neck", 0.25, 0.2)]
    r.size = (THIGH + SHIN + 3) / 36.0
    r.shadow = 0.6
    # it is wood: only its loose ends trail it (a joint's give in the neck and the fingers, the tag on its string)
    r.springs = [("head", 0.22, 3.2, 0.32)] + [(f"fing_{s}", 0.35, 3.0, 0.26) for s in "lr"] + [(f"tip_{s}", 0.45, 3.4, 0.22) for s in "lr"] \
        + [("tag", 0.8, 1.9, 0.14)]
    r.guard = ["tip_l", "tip_r", "fing_l", "fing_r", "hand_l", "hand_r", "head", "tag"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

WOOD = Ramp("#2b1e14", "#3e2c1d", "#544028", "#6c5335", "#856843", "#9e7e55", "#b59569", "#c9aa7f", "#dbbf97", "#e9d3b0")
JOINT = Ramp("#22180f", "#342518", "#473322", "#5c432e", "#71553b", "#86684a", "#9a7b5b", "#ad8e6c")
GRIME = Ramp("#2a231d", "#3c332b", "#50463c", "#655a4e", "#7b6f60", "#918472", "#a69983")
GRAPH = Ramp("#18181c", "#26262c", "#35353d", "#46464f", "#5a5a64", "#70707a")
PIN = Ramp("#24190a", "#3e2c12", "#5c441c", "#7c5e28", "#9c7a38", "#b8964e")
PAPER = Ramp("#5a5244", "#7a705e", "#9a8f7a", "#b8ad96", "#d2c8b0", "#e6ddc8")
STRING = Ramp("#3a3026", "#54473a", "#6e604e", "#887a66")
PENCIL = "#55545c"
ERASED = "#8f8270"


def _grain(c):
    """Beech: fine streaks along a part's length on its sides, rings on its ends, now and then a darker streak."""
    def f_(f, i, j):
        if f.name in ("top", "bottom"):
            d = math.hypot(i - f.w / 2 + 0.5, j - f.h / 2 + 0.5)
            return -0.45 if int(d * 1.3) % 2 else 0.15
        h = R._h(c, f.name, i)
        return 0.3 * math.sin(i * 1.9 + (h % 7)) + (-0.8 if h % 9 == 0 else 0) + (-0.4 if R._h(c, f.name, i * 13 + j) % 23 == 0 else 0)
    return f_


def paint(r, P_):
    """Pale beech worn smooth, its grain running down every part; the ball joints darker and greyer where hands have
    worn them, the hinge pins brass; the hands grey with handling; a crack down its breast, a chip out of its left
    shoulder; the pencil guidelines on its head with the eyes rubbed out; a shop's paper tag on a string."""
    of = lambda *ps: [c.name for c in r.cubes if any(c.name.startswith(p) for p in ps)]
    for c in of("pelvis", "seat", "thighu_", "thighl_", "calf_", "shinl_", "instep_", "sole_", "abd", "rib", "breast", "back", "yoke", "neck",
                "armu_", "arml_", "fore_", "forel_"):
        if c == "neckb":
            continue
        P_.paint(c, WOOD, 6.4 if c in ("rib", "breast", "yoke", "pelvis") else 6.0, _grain(c), grad=0.8, rim=0.9, ao=0.7, noise=0.3, nscale=2)
    for c in of("hipb_", "knee_", "ankle_", "waist", "neckb", "shb_", "elb_", "wrb_"):
        P_.paint(c, JOINT, 5.4, lambda f, i, j, c=c: (-0.9 if f.name in SIDES and j == f.h // 2 else 0.3 if j == 0 else 0), grad=0.9, rim=1.2, ao=0.6,
                 noise=0.5, nscale=2)
    for c in of("kpin_", "epin_"):
        P_.paint(c, PIN, 4.2, None, grad=0.4, rim=0.9)
    P_.paint("peg", WOOD, 4.8, lambda f, i, j: (-1.4 if f.name == "south" and (i + j) % 2 == 0 else 0), grad=0.4, rim=0.6, ao=0.6)
    for n in "lr":
        P_.paint("palm_" + n, GRIME, 5.4, _grain("palm_" + n), grad=0.5, rim=0.7, ao=0.6)
        P_.paint("thumb_" + n, GRIME, 5.2, None, grad=0.4, rim=0.7)
        P_.paint("fing_" + n, GRIME, 5.0, lambda f, i, j: (-1.2 if f.name in ("east", "west") and j % 3 == 2 else 0), grad=0.4, rim=0.6)
        P_.paint("tip_" + n, GRIME, 4.4, lambda f, i, j: (-1.2 if f.name in ("east", "west") and j % 3 == 2 else -0.3 * (j / 3)), grad=0.4, rim=0.6)
    # the head: the lightest wood on it
    for c in ("head", "chin", "crown"):
        P_.paint(c, WOOD, 7.0, _grain(c), grad=0.9, rim=1.1, ao=0.6, noise=0.25, nscale=2)
    # the guidelines, in pencil: the line down the middle of the head (over the crown and down the back), the line
    # across it for the eyes going on round the sides; where the eyes would go, rubbed out
    f = P_.face("head", "north")  # 7 across, 7 down; the eye line one row above the middle
    for j in range(f.h):
        f.set(3, j, hexc(PENCIL))
    for i in range(f.w):
        f.set(i, 3, hexc(PENCIL) if (i + 1) % 4 else hexc(ERASED))
    for i, j in ((1, 2), (2, 2), (1, 3), (2, 3), (4, 2), (5, 2), (4, 3), (5, 3), (2, 5), (3, 5), (4, 5)):
        if (i, j) in ((1, 3), (2, 3), (4, 3), (5, 3)):
            f.set(i, j, hexc(ERASED))
        else:
            f.set(i, j, WOOD.at(5.6 if j < 5 else 6.2))
    f = P_.face("chin", "north")
    for j in range(f.h):
        f.set(2, j, hexc(PENCIL))
    for fname in ("east", "west"):
        f = P_.face("head", fname)
        for i in range(f.w):
            if R._h("eye", fname, i) % 5:
                f.set(i, 3, hexc(PENCIL))
    for fname in ("top",):
        f = P_.face("head", fname)
        for j in range(f.h):
            f.set(3, j, hexc(PENCIL))
        f = P_.face("crown", fname)
        for j in range(f.h):
            f.set(3, j, hexc(PENCIL))
    f = P_.face("head", "south")
    for j in range(f.h):
        if R._h("back", "s", j) % 4:
            f.set(3, j, hexc(PENCIL))
    # the crack down its breast and the chip out of its left shoulder
    f = P_.face("rib", "north")
    for i, j in ((6, 0), (6, 1), (5, 2), (5, 3), (6, 4), (6, 5), (5, 6), (5, 7)):
        f.set(i, j, WOOD.at(1.6))
        if i + 1 < f.w:
            f.set(i + 1, j, WOOD.at(4.6))
    f = P_.face("breast", "north")
    for i, j in ((5, 1), (5, 2), (4, 3), (4, 4)):
        f.set(i, j, WOOD.at(1.8))
    f = P_.face("shb_l", "top")
    for i, j in ((1, 1), (2, 1), (1, 2)):
        f.set(i, j, WOOD.at(7.8))
    # the tag and its string
    P_.paint("tagstr", STRING, 2.6, None, grad=0.0, rim=0.0)
    P_.paint("tagcard", PAPER, 4.6, lambda f, i, j: (-1.6 if (j == 1 and i in (0, 1)) else -0.6 if j == 0 else 0), grad=0.4, rim=0.3)
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the hips (at: x, up, z; hips: their turn), the waist ball, the chest, the neck and the head; each arm as
# (raise, out, elbow, wrist, curl, twist); the feet planted where they stand at rest (step: {side: (dx, dy, dz)} model
# pixels, y down) unless legs pose them (whole turns)

def _abs(r, bone, rot):
    """A key for a bone given as its whole turn (a pose holds what it adds to the rest)."""
    rest = r.by_name[bone].rot
    return tuple(rot[i] - rest[i] for i in range(3))


def _pose(r, at=(0.0, 0.0, 0.0), hips=(0.0, 0.0, 0.0), waist=(0.0, 0.0, 0.0), chest=(0.0, 0.0, 0.0), neck=(0.0, 0.0, 0.0), head=(0.0, 0.0, 0.0),
          arms=None, step=None, turn=None, legs=None, feet=("l", "r"), extra=None):
    """arms {side: (raise (+ forward and up), out (+ away from its side), elbow (+ bent), wrist (+ toward the palm), curl
    (+ the fingers closing), twist)}; a side left out hangs at rest."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": waist, "chest": chest, "neck": neck, "head": head}
    for s, sg in (("l", 1), ("r", -1)):
        a = (list((arms or {}).get(s, ())) + [0.0] * 6)[:6]
        raise_, out, elbow, wrist, curl, tw = a
        p["arm_" + s] = (-raise_, tw * sg, -sg * out)
        p["forearm_" + s] = (-elbow, 0.0, 0.0)
        p["hand_" + s] = (0.0, 0.0, sg * wrist)
        p["fing_" + s] = (0.0, 0.0, sg * 0.55 * curl)
        p["tip_" + s] = (0.0, 0.0, sg * 0.5 * curl)
    if legs:
        p.update({b: _abs(r, b, v) for b, v in legs.items()})
        feet = tuple(s for s in feet if "thigh_" + s not in legs)
    if extra:
        p.update(extra)
    if feet:
        p = core.plant(r, p, feet, at=step, turn=turn)
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
    """A recovery in two steps to rest, so nothing snaps home."""
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def _stride(u, D, lift, stance):
    """A stride at phase u (the right foot setting down ahead at 0, the left at 0.5): each foot on the floor for its
    share of the cycle, sliding back under it, then swung forward through the air. Offsets from rest (model px, y
    down), with the body travelling D pixels a cycle."""
    out = {}
    half = stance * D / 2
    for s, ph in (("r", 0.0), ("l", 0.5)):
        v = (u - ph) % 1.0
        if v < stance:
            z, dy = -half + 2 * half * v / stance, 0.0
        else:
            k = (v - stance) / (1 - stance)
            z, dy = half - 2 * half * _ease(k), -lift * math.sin(math.pi * k)
        out[s] = (0.0, dy, z)
    return out


def _turn(r, mv, bone, axis, turns, t0, t1):
    """Whole turns on a bone in a move's clip, after staging: from none at t0 to all of them at t1, eased (a whole turn
    is no turn, so it hands back to rest as it was)."""
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


# ---------------------------------------------------------------------------------------------------------------------
# Idle, walk, run, turns

def _idle(r):
    """It stands quite still, the way a thing stands that has never had to balance: its weight settles a little from
    one hip to the other over a long while. Its head tilts over to one side and stays there, looking at you; it comes
    back; its fingers tap, three times, on nothing. Then its head turns, in small clicks, a little further than you
    would like, and slowly back."""
    def k(sway=0.0, tilt=0.0, yaw=0.0, tap=0.0, nod=0.0):
        return _pose(r, at=(0.3 * sway, -0.2 * abs(sway), 0), hips=(0, 0, 0.8 * sway), waist=(0, -1.0 * sway, -0.6 * sway), chest=(0, -1.2 * sway, 0),
                     neck=(nod * 0.4, yaw * 0.3, tilt * 0.3), head=(nod, yaw * 0.7, tilt * 0.7),
                     arms={"l": (1.0 * sway, 0, 2), "r": (-1.0 * sway, 0, 2, 0, tap)})
    keyed(r, "idle", 6.0, [(0, k()), (0.9, k(sway=0.6)), (1.7, k(sway=0.8, tilt=14, nod=4)), (2.3, k(sway=0.7, tilt=15, nod=4)),
                           (2.5, k(sway=0.7, tilt=15, nod=4, tap=22)), (2.62, k(sway=0.7, tilt=15, nod=4, tap=4)), (2.74, k(sway=0.7, tilt=15, nod=4, tap=22)),
                           (2.86, k(sway=0.7, tilt=15, nod=4, tap=4)), (2.98, k(sway=0.7, tilt=15, nod=4, tap=22)), (3.25, k(sway=0.4, tilt=6, tap=8)),
                           (3.8, k(sway=-0.3)), (4.05, k(sway=-0.4, yaw=-9)), (4.3, k(sway=-0.5, yaw=-18)), (4.55, k(sway=-0.6, yaw=-28, tilt=-4)),
                           (5.3, k(sway=-0.4, yaw=-12, tilt=-2)), (6.0, k())], loop=True)


def _locomotion(r):
    """It walks too smoothly: its hips glide level, its head stays level and on you, and its arms swing loose a beat
    behind, like things hung from it. It runs leaning in, long strides, its arms hanging straight back behind it and
    not swinging at all."""
    r.walk_stride = 1.3
    D, T, n = r.walk_stride * 16, 1.0, 16
    keys = []
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
        keys.append((T * u, _pose(r, at=(0.4 * s, -0.4 * abs(math.cos(2 * math.pi * u)) ** 2, 0), hips=(0, 4 * c, 1.2 * s), waist=(0, -2 * c, -0.6 * s),
                                  chest=(1, -3 * c, 0), neck=(0, 1.5 * c, 0), head=(-1, 2.5 * c, -0.6 * s),
                                  arms={"l": (9 * math.cos(2 * math.pi * u - 0.6), 2, 6 + 4 * max(0.0, math.cos(2 * math.pi * u - 0.6)), 0, 4),
                                        "r": (-9 * math.cos(2 * math.pi * u - 0.6), 2, 6 + 4 * max(0.0, -math.cos(2 * math.pi * u - 0.6)), 0, 4)},
                                  step=_stride(u, D, 2.6, 0.6))))
    keyed(r, "walk", T, keys, loop=True)
    r.run_stride = 2.6
    D, T, n = r.run_stride * 16, 0.62, 16
    keys = []
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
        keys.append((T * u, _pose(r, at=(0.3 * s, -1.6 + 1.0 * abs(math.sin(2 * math.pi * u)), -1), hips=(10, 5 * c, 2 * s), waist=(6, -3 * c, 0),
                                  chest=(6, -4 * c, 0), neck=(-4, 2 * c, 0), head=(-14, 3 * c, 0),
                                  arms={"l": (-26 + 2 * s, 8, 4, 0, -6), "r": (-26 - 2 * s, 8, 4, 0, -6)}, step=_stride(u, D, 6.0, 0.38))))
    keyed(r, "run", T, keys, loop=True)
    r.run_speed = (0.15, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        a = _pose(r, at=(0, -0.4, 0), hips=(0, 6 * sgn, 0), chest=(0, 3 * sgn, 0), step={"r": (-1.5 * sgn, -2.2, -1)})
        b = _pose(r, hips=(0, 4 * sgn, 0), chest=(0, 2 * sgn, 0))
        c = _pose(r, at=(0, -0.4, 0), hips=(0, 6 * sgn, 0), chest=(0, 3 * sgn, 0), step={"l": (1.5 * sgn, -2.2, 1)})
        keyed(r, nm, 0.48, [(0, _pose(r)), (0.12, a), (0.24, b), (0.36, c), (0.48, _pose(r))], loop=True)


# ---------------------------------------------------------------------------------------------------------------------
# Struck, broken, down

# both knees on the floor, the thighs straight up under it, the shins lying back along the floor, the feet pointed
KNEEL = {"thigh_l": (0, 0, -2), "shin_l": (90, 0, 0), "foot_l": (90, 0, 0), "thigh_r": (0, 0, 2), "shin_r": (90, 0, 0), "foot_r": (90, 0, 0)}
KNEEL_AT = -13.0  # the hips that far down for it


def _reactions(r):
    """Struck, it jolts back and its joints flop loose (its head lolls, its arms swing) and it snaps back into its
    pose; from the side it bends away from the blow. Its stance broken, it drops onto its knees as if its strings were
    cut, slumped, its arms hanging and its head down; it rises the way a puppet is lifted, head first."""
    home = _pose(r)
    jolt = _pose(r, at=(0, -0.6, 2.2), hips=(-4, 0, 0), waist=(-8, 0, 0), chest=(-10, 0, 0), neck=(-6, 0, 0), head=(-22, 0, 0),
                 arms={"l": (28, 14, 34, 10, 14), "r": (28, 14, 34, 10, 14)}, step={"l": (0, 0, 0.5), "r": (0, 0, 0.5)})
    loll = _pose(r, at=(0, -0.4, 0.8), waist=(4, 0, 0), chest=(6, 0, 0), neck=(6, 0, 0), head=(20, 0, 6), arms={"l": (-8, 6, 10, 0, 6), "r": (-8, 6, 10, 0, 6)})
    keyed(r, "hurt", 0.6, [(0, home), (0.08, jolt), (0.27, loll), (0.42, _mix(loll, home, 0.65)), (0.6, home)])
    for nm, sg in (("hurt_l", -1), ("hurt_r", 1)):
        bend = _pose(r, at=(1.6 * sg, -0.6, 0.6), hips=(0, 0, 3 * sg), waist=(0, 6 * sg, 8 * sg), chest=(-4, 8 * sg, 10 * sg), neck=(0, 0, 8 * sg), head=(-10, 10 * sg, 20 * sg),
                     arms={"l": (16, 20 + 14 * sg, 30, 10, 10), "r": (16, 20 - 14 * sg, 30, 10, 10)})
        flop = _pose(r, at=(0.5 * sg, -0.3, 0.2), waist=(0, -2 * sg, -3 * sg), chest=(2, -2 * sg, -4 * sg), head=(10, -4 * sg, -12 * sg),
                     arms={"l": (-6, 4, 10), "r": (-6, 4, 10)})
        keyed(r, nm, 0.6, [(0, home), (0.08, bend), (0.28, flop), (0.43, _mix(flop, home, 0.6)), (0.6, home)])
    keyed(r, "kneel", 0.9, [(0, home), (0.3, _buckle(r)), (0.62, _slump(r, 1.06)), (0.9, _slump(r))])
    lift = _pose(r, at=(0, KNEEL_AT * 0.55, 0), legs=None, waist=(10, 0, 0), chest=(6, 0, 0), neck=(-4, 0, 0), head=(-10, 0, 0),
                 arms={"l": (16, 6, 14, 0, 10), "r": (16, 6, 14, 0, 10)})
    keyed(r, "rise", 1.0, [(0, _slump(r)), (0.3, _slump(r, 0.6, head=-12)), (0.62, lift), (1.0, home)])


def _buckle(r):
    """Its knees giving way: the hips dropping, the joints going loose."""
    return _pose(r, at=(0, -6, 0.5), waist=(10, 0, 0), chest=(8, 0, 4), neck=(6, 0, 0), head=(18, 0, 10), arms={"l": (10, 10, 16, 0, 10), "r": (10, 10, 16, 0, 10)})


def _slump(r, f=1.0, head=None):
    """On its knees, slumped, f of the way: its body folded forward over its knees, its head hanging, its arms
    hanging down to the floor before it."""
    return _pose(r, at=(0, KNEEL_AT, 0), legs=KNEEL, waist=(16 * f, 0, 0), chest=(14 * f, 0, 5 * f), neck=(8 * f, 0, 0), head=(head if head is not None else 34 * f, 0, 12 * f),
                 arms={"l": (26 * f, 8 * f, 14 * f, 0, 14), "r": (26 * f, 8 * f, 14 * f, 0, 14)})


def _death(r):
    """Its last: struck, it stands rigid a moment with its head tilted, then its strings are cut: its knees go and it
    drops onto them, slumped, and folds forward over them until its chest lies on its knees and its hands on the floor;
    its head comes loose on its neck, drops off and rolls away to the side; a finger twitches."""
    home = _pose(r)
    stag = _pose(r, at=(0, -0.8, 2.5), hips=(-4, 0, 0), waist=(-10, 0, 0), chest=(-12, 0, 0), head=(-20, 0, 0), arms={"l": (30, 20, 30, 10, 10), "r": (30, 20, 30, 10, 10)},
                 step={"l": (0, 0, 1), "r": (0, 0, 1)})
    rigid = _pose(r, at=(0, -0.3, 1.2), waist=(-2, 0, 0), chest=(-3, 0, 2), neck=(0, 0, 8), head=(4, 6, 26), arms={"l": (6, 10, 10, 0, 20), "r": (6, 10, 10, 0, 20)})

    def folded(f, off=0.0, twitch=0.0):
        p = _pose(r, at=(0, KNEEL_AT - 1.0 * f, -0.5 * f), legs=KNEEL, waist=(16 + 26 * f, 0, 0), chest=(14 + 28 * f, 0, 5), neck=(8 + 14 * f, 0, 0),
                  head=(34 + 10 * f, 0, 12 + 30 * off), arms={"l": (26 + 40 * f, 10, 20 + 10 * f, 0, 14), "r": (26 + 40 * f, 10, 20 + 10 * f, 0, 14 + twitch)})
        if off:
            p["head"] = {"rot": (p["head"][0] + 50 * off, 30 * off, p["head"][2] + 60 * off), "pos": (5.0 * off, 4.0 * off, -3.0 * off)}
        return p
    keyed(r, "death", 5.0, [(0, home), (0.3, stag), (0.75, rigid), (1.05, _mix(rigid, home, 0.2)), (1.4, _buckle(r)), (1.85, _slump(r, 1.05)), (2.2, _slump(r)),
                            (2.9, folded(0.8)), (3.3, folded(1.0)), (3.7, folded(1.0, 0.15)), (4.1, folded(1.0, 0.85)), (4.35, folded(1.0, 1.0)),
                            (4.55, folded(1.0, 1.0, 40)), (4.7, folded(1.0, 1.0, 6)), (5.0, folded(1.0, 1.0))])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def reach(r):
    """The Reach (LIGHT): it turns away a little and cocks its right hand back by its ear, fingers flat, its head
    turning to keep you; then the arm spears straight out at you as it steps in, the fingers leading."""
    cock = _pose(r, at=(0, -1.0, 1.5), hips=(0, 8, 0), waist=(-3, 8, 0), chest=(-6, 12, 0), head=(4, -16, 8),
                 arms={"r": (40, 30, 130, -10, -6), "l": (18, 14, 24, 0, 10)}, step={"r": (0, 0, 2)})
    peak = _pose(r, at=(0, -1.4, 2.2), hips=(0, 12, 0), waist=(-4, 10, 0), chest=(-8, 16, 0), head=(6, -22, 10),
                 arms={"r": (50, 36, 142, -14, -10), "l": (22, 18, 30, 0, 12)}, step={"r": (0, 0, 2.5)})
    strike = _pose(r, at=(0, -2.6, -5), hips=(4, -4, 0), waist=(4, -3, 0), chest=(6, -6, 0), head=(-2, 8, -4),
                   arms={"r": (92, 10, 0, 0, -10), "l": (14, 30, 34, 0, 12)}, step={"r": (0, 0, -8), "l": (0, 0, -1)})
    after = _pose(r, at=(0, -2.9, -5.6), hips=(4, -5, 0), waist=(4, -4, 0), chest=(7, -8, 0), head=(-1, 10, -8),
                  arms={"r": (94, 8, 0, 4, -6), "l": (14, 34, 36, 0, 14)}, step={"r": (0, 0, -8.5), "l": (0, 0, -1)})
    return Move("reach", "LIGHT", 9, 4, 14, through=after, title="The Reach", track=10.0, rng=(0, 4.4), kinds=("combo", "near"), damage=1.0, knock=0.5,
                plant=(), hit=[("tip_r", (0, 2, 0), 3.0), ("fing_r", (0, 1.5, 0), 3.0), ("hand_r", (0, 2, 0), 3.0), ("forearm_r", (0, 6, 0), 3.0)],
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.4)), (0.62, cock), (1.0, peak)], "active": [(0.55, strike), (1.0, after)],
                        "recover": _back(r, after)})


def grasp(r):
    """The Grasp (LIGHT, after the Reach): its body turns the other way and its left hand goes out wide at its side,
    the fingers spread; then the hand sweeps across in front of it and clutches shut."""
    wide = _pose(r, at=(0, -1.0, 0.6), hips=(0, -12, 0), waist=(0, -10, 0), chest=(-4, -18, -4), head=(0, 22, 6),
                 arms={"l": (64, 70, 30, -20, -30), "r": (18, 10, 30, 0, 10)}, step={"l": (0, 0, 1.5)})
    peak = _pose(r, at=(0, -1.3, 1.0), hips=(0, -18, 0), waist=(0, -14, 0), chest=(-6, -24, -6), head=(2, 28, 8),
                 arms={"l": (72, 86, 34, -24, -36), "r": (20, 12, 34, 0, 12)}, step={"l": (0, 0, 2)})
    strike = _pose(r, at=(0, -2.2, -3.5), hips=(2, 6, 0), waist=(2, 6, 0), chest=(6, 10, 4), head=(0, -8, -4),
                   arms={"l": (84, -18, 40, 20, 70), "r": (18, 20, 30, 0, 10)}, step={"l": (0, 0, -6)})
    after = _pose(r, at=(0, -2.5, -4), hips=(2, 8, 0), waist=(3, 8, 0), chest=(7, 14, 5), head=(1, -12, -6),
                  arms={"l": (80, -32, 52, 24, 92), "r": (18, 24, 30, 0, 10)}, step={"l": (0, 0, -6.5)})
    return Move("grasp", "LIGHT", 9, 4, 14, through=after, title="The Grasp", track=8.0, rng=(0, 4.2), kinds=("combo", "near"), damage=0.9, knock=0.7,
                plant=(), hit=[("tip_l", (0, 2, 0), 3.2), ("fing_l", (0, 1.5, 0), 3.2), ("hand_l", (0, 2, 0), 3.0), ("forearm_l", (0, 6, 0), 3.0)],
                stages={"windup": [(0.3, _mix(_pose(r), wide, 0.4)), (0.62, wide), (1.0, peak)], "active": [(0.55, strike), (1.0, after)],
                        "recover": _back(r, after)})


def fold(r):
    """Folding (HEAVY, its delay): it bends forward at its waist ball, slowly, too far, until its head hangs by its
    knees and its hands lie on the floor; it stays there; then it unfolds in one snap, standing tall with both arms
    flung up over its head through whoever is in front of it."""
    bend = _pose(r, at=(0, -2.0, 1.0), waist=(28, 0, 0), chest=(22, 0, 0), neck=(8, 0, 0), head=(16, 0, 0), arms={"l": (46, 6, 10, 0, 12), "r": (46, 6, 10, 0, 12)})
    folded = _pose(r, at=(0, -4.0, 2.5), waist=(50, 0, 0), chest=(38, 0, 0), neck=(12, 0, 4), head=(22, 0, 12), arms={"l": (88, 8, 6, 0, 22), "r": (88, 8, 6, 0, 22)})
    strike = _pose(r, at=(0, 0.0, -2.0), hips=(-4, 0, 0), waist=(-12, 0, 0), chest=(-12, 0, 0), neck=(-6, 0, 0), head=(-18, 0, 0),
                   arms={"l": (166, 18, 10, 0, -12), "r": (166, 18, 10, 0, -12)}, step={"l": (0, 0, -1.5), "r": (0, 0, -1.5)})
    after = _pose(r, at=(0, 0.0, -2.4), hips=(-5, 0, 0), waist=(-15, 0, 0), chest=(-14, 0, 0), neck=(-8, 0, 0), head=(-22, 0, 0),
                  arms={"l": (176, 24, 14, 0, -18), "r": (176, 24, 14, 0, -18)}, step={"l": (0, 0, -1.5), "r": (0, 0, -1.5)})
    return Move("fold", "HEAVY", 14, 6, 22, through=after, title="Folding", hold=10, track=4.0, rng=(0, 3.8), kinds=("near",), damage=1.3, knock=1.3,
                plant=(), hit=[("tip_l", (0, 2, 0), 3.5), ("tip_r", (0, 2, 0), 3.5), ("hand_l", (0, 2, 0), 3.5), ("hand_r", (0, 2, 0), 3.5), ("head", (0, -5, 0), 5.0)],
                stages={"windup": [(0.25, _mix(_pose(r), bend, 0.5)), (0.6, bend), (1.0, folded)], "active": [(0.5, strike), (1.0, after)],
                        "recover": _back(r, after)})


def corner(r):
    """Corner of Your Eye (its gap closer): it goes still and turns its head away from you, slowly, as if it heard
    something; and then it is just there in front of you, crouched low with its hands reaching, its head snapping back
    round to you."""
    listen = _pose(r, at=(0, -0.6, 0.4), waist=(2, 6, 3), chest=(4, 10, 6), neck=(2, 16, 4), head=(6, 36, 14), arms={"l": (8, 8, 16, 0, 26), "r": (-6, 10, 10, 0, 30)})
    away = _pose(r, at=(0, -0.2, 0), waist=(-2, 12, -2), chest=(-3, 20, -4), neck=(0, 24, 0), head=(2, 70, 6), arms={"l": (-4, 4, 6, 0, 4), "r": (10, 14, 24, 0, 8)})
    there = _pose(r, at=(0, -5.0, -3.0), hips=(10, 0, 0), waist=(12, 0, 0), chest=(10, 0, 0), neck=(-6, 0, 0), head=(-20, 0, 0),
                  arms={"l": (60, 30, 40, 10, 30), "r": (80, 10, 20, 0, 10)}, step={"r": (0, 0, -7), "l": (0, 0, 3)})
    reaching = _pose(r, at=(0, -4.2, -3.6), hips=(8, -6, 0), waist=(10, -4, 0), chest=(8, -6, 4), neck=(-4, 4, 0), head=(-14, 8, -6),
                     arms={"l": (74, 24, 26, 14, 50), "r": (90, 14, 12, 6, 30)}, step={"r": (0, 0, -7), "l": (0, 0, 3)})
    rising = _mix(reaching, _pose(r), 0.4)
    return Move("corner", "NONE", 16, 12, 14, through=rising, title="Corner of Your Eye", track=0.0, ability="Corner of Your Eye", rng=(6, 20), kinds=("gap",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.3, _mix(_pose(r), listen, 0.6)), (0.62, listen), (1.0, away)], "active": [(0.25, there), (0.6, reaching), (1.0, rising)],
                                                     "recover": _back(r, rising)})


def lights(r):
    """Lights Off: it raises its right hand high over its head with the fingers spread, its head tilting back to look
    up at it; then it closes them, in two jerks, as if pinching out a candle, and its head drops."""
    up = _pose(r, at=(0, 0.4, 0), waist=(-3, 0, 0), chest=(-6, 0, 0), neck=(-6, 0, 0), head=(-30, 0, 10), arms={"r": (168, 12, 22, -10, -30), "l": (10, 10, 20, 0, 10)})
    half = _pose(r, at=(0, 0.2, 0), waist=(-3, 6, 4), chest=(-8, 8, 6), neck=(-6, -6, 4), head=(-26, -8, 22), arms={"r": (160, 18, 34, 10, 46), "l": (22, 22, 40, 0, 34)})
    shut = _pose(r, at=(0, -0.2, 0), waist=(-1, -6, -3), chest=(-3, -8, -4), neck=(-2, 6, 0), head=(-18, 6, 2), arms={"r": (156, 6, 44, 20, 110), "l": (6, 8, 12, 0, 6)})
    down = _pose(r, at=(0, -0.6, 0), waist=(2, 0, 0), chest=(2, 0, 0), neck=(6, 0, 0), head=(16, 0, 4), arms={"r": (140, 10, 40, 10, 110), "l": (8, 10, 20, 0, 12)})
    return Move("lights", "NONE", 14, 20, 16, through=down, title="Lights Off", track=0.0, ability="Lights Off", rng=(0, 99), kinds=("cast",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.4, _mix(_pose(r), up, 0.55)), (1.0, up)], "active": [(0.22, half), (0.36, _mix(half, up, 0.4)), (0.55, shut),
                                                                                                    (1.0, down)], "recover": _back(r, down)})


def stare(r):
    """Stare: it leans its blank face at you and tilts it slowly over to one side, then further, further than a neck
    should go, and holds it there, trembling, its body swaying over its feet the way a thing balances that has never
    had to, its fingers working; then it straightens."""
    lean = _pose(r, at=(0, -1.0, -1.0), hips=(4, 0, 0), waist=(8, 0, 0), chest=(8, 0, 0), neck=(12, 0, 6), head=(-10, 0, 22),
                 arms={"l": (14, 4, 6, 0, 10), "r": (14, 4, 6, 0, 10)})
    tilt = _pose(r, at=(0, -1.2, -1.4), hips=(5, 0, 0), waist=(9, -4, 3), chest=(9, -6, 6), neck=(14, 4, 14), head=(-14, 4, 48),
                 arms={"l": (28, 8, 22, 0, 40), "r": (6, 6, 4, 0, -12)})
    tipping = _pose(r, at=(0, -1.4, -1.2), hips=(5, 0, 0), waist=(10, 6, -2), chest=(10, 8, -4), neck=(16, -6, 26), head=(-18, -6, 34),
                    arms={"l": (6, 6, 4, 0, -8), "r": (30, 8, 26, 0, 46)})

    def sway(k):
        return _pose(r, at=(0.8 * k, -1.3, -1.6), hips=(5, 3 * k, 2 * k), waist=(10, -8 * k, 5 * k), chest=(10, -10 * k, 2 - 6 * k), neck=(15, 10 * k, 16 + 6 * k),
                     head=(-14 + 4 * k, 6 * k, 50 + 8 * k), arms={"l": (18 + 14 * k, 6, 10 + 14 * k, 0, 12 + 30 * k), "r": (18 - 14 * k, 6, 10 - 8 * k, 0, 12 - 26 * k)})
    end = _mix(tilt, lean, 0.4)
    return Move("stare", "NONE", 16, 20, 16, through=end, title="Stare", track=8.0, ability="Stare", rng=(5, 18), kinds=("ranged",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.3, lean), (0.65, tipping), (1.0, tilt)],
                                                     "active": [(0.2, sway(1)), (0.4, sway(-1)), (0.6, sway(1)), (0.8, sway(-0.7)), (1.0, sway(0.6))],
                                                     "recover": _back(r, end)})


def everywhere(r):
    """Everywhere: it snaps from pose to pose like a model for drawing, holding each: pointing straight at you, both
    arms up over its head, then wrapped round itself, crouched; and back to pointing."""
    point = lambda k=0.0: _pose(r, at=(0, -0.6, 0), hips=(0, -8, 0), chest=(0, -10, 0), head=(-4 + k, 14, 10 + 2 * k), arms={"r": (90, 0, 0, 0, -10), "l": (6, 16, 20, 0, 10)},
                                step={"r": (0, 0, -2)})
    up = lambda k=0.0: _pose(r, at=(0, 0.4, 0), waist=(-4, 0, 0), chest=(-8, 0, 0), head=(-24 + k, 0, -6), arms={"l": (160, 30, 10, 0, -20), "r": (160, 30, 10, 0, -20)})
    wrap = lambda k=0.0: _pose(r, at=(0, -4, 0.5), waist=(16, 0, 0), chest=(14, 0, 0), head=(28 + k, 0, 8), arms={"l": (50, -30, 120, 0, 60), "r": (54, -30, 116, 0, 60)})
    return Move("everywhere", "NONE", 12, 24, 16, through=point(2), title="Everywhere", track=0.0, ability="Everywhere", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.45, _mix(_pose(r), point(), 0.5)), (1.0, point())],
                                                     "active": [(0.12, point(2)), (0.24, up()), (0.4, up(3)), (0.52, wrap()), (0.7, wrap(4)), (0.84, point()),
                                                                (1.0, point(2))], "recover": _back(r, point(2))})


def wrong_way(r):
    """Wrong Way Round (LIGHT, for whoever is behind it): its head turns round over its shoulder to look behind it;
    then its whole chest swivels round on its waist ball after it, its legs staying where they are, and its right arm
    swings across behind it."""
    look = _pose(r, chest=(0, 20, 0), neck=(0, 30, 0), head=(0, 106, 0), arms={"r": (30, 40, 60, 10, 20), "l": (10, 10, 20, 0, 10)})
    cock = _pose(r, at=(0, -1.0, 0), waist=(0, 40, 0), chest=(0, 50, 0), neck=(0, 30, 0), head=(0, 60, 0), arms={"r": (60, 84, 30, 0, 0), "l": (20, 20, 30, 0, 10)})
    strike = _pose(r, at=(0, -1.6, 0.8), waist=(0, 80, 0), chest=(0, 90, 0), neck=(0, 8, 0), head=(0, 2, 0), arms={"r": (88, -10, 4, 0, -10), "l": (30, 40, 30, 0, 10)})
    after = _pose(r, at=(0, -1.8, 1.0), waist=(0, 86, 0), chest=(0, 96, 0), neck=(0, 6, 0), head=(0, 0, 0), arms={"r": (84, -26, 8, 0, -14), "l": (32, 42, 30, 0, 10)})
    return Move("wrong", "LIGHT", 10, 4, 14, through=after, title="Wrong Way Round", track=0.0, rng=(0, 4.2), kinds=("behind",), damage=1.0, knock=0.8, plant=(),
                hit=[("tip_r", (0, 2, 0), 3.2), ("hand_r", (0, 2, 0), 3.0), ("forearm_r", (0, 6, 0), 3.0)],
                stages={"windup": [(0.3, look), (1.0, cock)], "active": [(0.55, strike), (1.0, after)], "recover": _back(r, after)})


def hold_still(r):
    """Hold Still (PERILOUS, the signature): it rises tall with its arms straight out at its sides and its head tipping
    back; it creaks, and winds its body back round on its waist ball; then it spins round on it twice, its arms out at
    full reach, and stops dead facing you."""
    tall = lambda k=0.0: _pose(r, at=(0, 0.6 + 0.6 * k, 0), waist=(-3, 0, 2 * k), chest=(-5 - 4 * k, 0, 4 * k), neck=(-4, 0, 0), head=(-16 - 8 * k, 0, 12 * k),
                               arms={"l": (2 + 10 * k, 86 - 4 * k, 12 * k, 0, -24 + 16 * k), "r": (2 + 10 * k, 86 - 4 * k, 12 * k, 0, -24 + 16 * k)})
    wind = _pose(r, at=(0, -1.2, 0.5), hips=(0, -10, 0), waist=(4, -40, 0), chest=(4, -30, 0), neck=(0, 30, 0), head=(-6, 36, 6),
                 arms={"l": (6, 80, 4, 0, -24), "r": (6, 80, 4, 0, -24)})
    def spin(k, c):
        return _pose(r, at=(0, -1.6 - 0.2 * c, 0), waist=(4, 30 + 6 * c, 4 * k), chest=(2, 20 + 4 * c, 10 * k), neck=(0, -10 - 4 * c, -6 * k), head=(-4, -20 + 18 * k, -8 * k),
                     arms={"l": (4 - 14 * k, 90 - 4 * k, 8 + 8 * k, 0, -20 + 12 * k), "r": (4 + 14 * k, 90 + 4 * k, 8 - 8 * k, 0, -20 - 12 * k)})
    spun = spin(0.6, 1)
    stop = _pose(r, at=(0, -1.4, 0), waist=(2, -6, -3), chest=(4, -8, -6), head=(10, 8, 10), arms={"l": (16, 64, 24, 0, 16), "r": (-4, 74, 6, 0, -8)})
    settle = _pose(r, at=(0, -1.0, 0), waist=(1, 3, 2), chest=(1, 4, 3), head=(2, -4, -4), arms={"l": (2, 54, 20, 0, 4), "r": (10, 50, 22, 0, 10)})
    return Move("hold", "PERILOUS", 30, 14, 30, through=spun, title="Hold Still", signature=True, track=0.0, rng=(0, 3.4), kinds=("area",), damage=1.6, knock=1.6,
                disc=2.4, plant=(), hit=[("tip_l", (0, 2, 0), 3.5), ("tip_r", (0, 2, 0), 3.5), ("forearm_l", (0, 6, 0), 3.5), ("forearm_r", (0, 6, 0), 3.5)],
                stages={"windup": [(0.25, _mix(_pose(r), tall(), 0.5)), (0.5, tall()), (0.7, tall(1)), (1.0, wind)],
                        "active": [(0.25, spin(1, 0)), (0.5, spin(-1, 0.5)), (0.75, spin(1, 1)), (1.0, spun)],
                        "recover": [(0.36, stop), (0.54, settle), (0.72, _mix(settle, _pose(r), 0.7))]})


def closer(r):
    """It Is Closer (into the second phase): its joints crack one after another (its neck, its right wrist, its left,
    its neck again) and it takes a step toward you, its head jerking round to you."""
    def k(nk=(0, 0, 0), hd=(0, 0, 0), wr=0.0, wl=0.0, at=(0.0, 0.0, 0.0), step=None, lean=0.0):
        return _pose(r, at=at, waist=(lean, nk[1] * 0.5, -nk[2] * 0.3), chest=(lean, nk[1] * 0.6, -nk[2] * 0.4), neck=nk, head=hd,
                     arms={"r": (12 + 0.4 * wr, 10 + 0.2 * wr, 20 + 0.5 * wr, wr, 10 + 0.6 * wr), "l": (12 - 0.4 * wl, 10 - 0.2 * wl, 20 - 0.5 * wl, wl, 10 - 0.6 * wl)},
                     step=step)
    a = k(nk=(0, 0, -10), hd=(-6, 0, -24))
    b = k(nk=(0, -8, -16), hd=(-8, -10, -34), wr=60, at=(0.8, -0.6, 0), lean=-4)
    c = k(nk=(0, 8, -6), hd=(-4, 12, -14), wr=50, wl=-60, at=(-0.8, -0.8, 0), lean=4)
    d = k(nk=(0, 12, 14), hd=(6, 18, 30), wr=30, wl=-30, at=(0, -1.2, -0.5), lean=8)
    e = k(nk=(6, 0, 0), hd=(14, 0, 0), wr=20, wl=-20, at=(0, -2.0, -3.0), step={"r": (0, 0, -7)}, lean=6)
    jerk = k(nk=(12, -4, 4), hd=(24, -6, 8), wr=36, wl=-8, at=(0, -2.4, -3.4), step={"r": (0, 0, -7)}, lean=10)
    held = k(nk=(4, 6, -6), hd=(8, 8, -14), wr=8, wl=-34, at=(0, -1.8, -3.0), step={"r": (0, 0, -7)}, lean=4)
    return Move("closer", "NONE", 12, 24, 16, through=held, title="It Is Closer", track=0.0, rng=(0, 99), kinds=("transition",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), a, 0.5)), (1.0, a)], "active": [(0.16, b), (0.32, c), (0.5, d), (0.66, _mix(d, e, 0.35)), (0.82, jerk), (1.0, e)],
                        "recover": _back(r, held)})


def wake(r):
    """Its intro, "It was standing there the whole time.": it stands posed the way someone left it to be drawn, its
    right arm raised high and its left bent at its side, its head turned away and down, quite still but for its
    fingers; its fingers stop; its head turns round to you in three clicks; its arm comes down; and it takes a step."""
    posed = lambda k=0.0, tr=0.0, y=0.0, a=150.0: _pose(r, at=(0, -0.4, 0), hips=(0, -8 + 0.2 * y, 2 + tr), waist=(0, -6 + 0.4 * y, 2 - tr),
                                                       chest=(-2, -10 + y + 3 * tr, 2 * tr), neck=(4, 30 - 0.3 * y, 6 - 2 * tr), head=(14, 50 - k, 16),
                                                       arms={"r": (a + 6 * tr, 30, 30 + 8 * tr, 10, -10 + 8 * k + 30 * tr), "l": (8 - 4 * tr, 40, 84 - 10 * tr, 20, 30 - 20 * tr)},
                                                       step={"l": (1, 0, -2)})
    t1, t2, t3 = posed(20, 0.6, 10, 142), posed(46, -0.6, 22, 132), posed(76, 0.3, 34, 118)
    t3["head"] = (6, -26, 6)
    t3["neck"] = (0, 0, 2)
    t2["head"] = (10, 10, 12)
    lower = _pose(r, at=(0, -0.6, 0), hips=(0, -4, 0), chest=(0, -4, 0), head=(4, -4, 2), arms={"r": (40, 20, 30, 0, 10), "l": (10, 20, 40, 10, 16)})
    step = _pose(r, at=(0, -1.8, -3.0), waist=(4, 0, 0), chest=(4, 0, 0), head=(8, 0, 0), arms={"r": (16, 8, 14, 0, 10), "l": (12, 8, 14, 0, 10)},
                 step={"r": (0, 0, -6)})
    arrive = _pose(r, at=(0, -1.3, -2.2), waist=(2, 4, -2), chest=(2, 6, -3), neck=(4, 4, -6), head=(12, 8, -16), arms={"r": (8, 10, 22, 0, 26), "l": (18, 8, 10, 0, 4)},
                   step={"r": (0, 0, -4)})
    return Move("wake", "NONE", 16, 44, 16, through=arrive, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.45, _mix(_pose(r), posed(), 0.6)), (1.0, posed())],
                        "active": [(0.12, posed(4, 1)), (0.24, posed(-2, -1)), (0.34, t1), (0.46, t2), (0.58, t3), (0.74, lower), (0.88, step), (1.0, _mix(step, _pose(r), 0.2))],
                        "recover": _back(r, arrive)})


HAND_GROUND = [([f"tip_{n}", f"fing_{n}", f"hand_{n}", "tag"], [(f"hand_{n}", 0, 40), (f"forearm_{n}", 0, 40), (f"arm_{n}", 0, 30)]) for n in ("l", "r")]
HEAD_GROUND = [(["head"], [("head", 0, 30), ("neck", 0, 30)])]
KNEE_GROUND = [([f"shin_{n}", f"foot_{n}"], [(f"foot_{n}", 0, 40), (f"shin_{n}", 0, 20)]) for n in ("l", "r")]


def _drift(r, clip, ramp=0.25):
    """Under a move, once it is made: its fingers flex a little, one hand after the other, faded in and out at the
    ends so it hands back to rest exactly as it was."""
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
    for j, (n_, sg) in enumerate((("l", 1), ("r", -1))):
        add(f"tip_{n_}", lambda t, j=j, sg=sg: (0.0, 0.0, sg * 6.0 * math.sin(w * 1.3 * t + 2.2 * j)))


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    _death(r)
    mv = [reach(r), grasp(r), fold(r), corner(r), lights(r), stare(r), everywhere(r), wrong_way(r), hold_still(r), closer(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        if m.id == "hold":
            W, A = m.seconds("windup"), m.seconds("active")
            _turn(r, m, "spine", 1, 2, W, W + A + 0.1 * m.seconds("recover"))
        _drift(r, core.clip_name(m))
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, HAND_GROUND, clips=everyday)
    core.ground_pass(r, HEAD_GROUND, clips=everyday)
    core.ground_pass(r, KNEE_GROUND, clips=["kneel", "rise", "death"], skip=())
    core.ground_pass(r, HAND_GROUND + HEAD_GROUND, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): wood. The knock of a jointed wooden figure, a door's creak for its tells, a candle snuffed
# for Lights Off, an enderman's stare for Stare, and under it all something breathing that has no mouth. Chosen on
# paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.armor_stand.hit", 0.8, 1.3), ("block.note_block.hat", 0.6, 0.8)],
    "tell_heavy": [("block.wooden_door.open", 0.9, 0.6), ("entity.armor_stand.hit", 1.0, 0.8)],
    "tell_perilous": [("block.wooden_door.open", 1.2, 0.5), ("entity.warden.nearby_close", 0.8, 1.2), ("block.note_block.snare", 0.8, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 0.8, 1.2), ("block.bamboo_wood.hit", 0.8, 1.4)],
    "swing_heavy": [("entity.player.attack.knockback", 1.0, 0.8), ("block.wood.hit", 1.0, 0.6)],
    "cast": [("block.wooden_door.close", 0.6, 0.6), ("entity.armor_stand.place", 0.8, 0.7)],
    "snuff": [("block.candle.extinguish", 1.2, 0.6), ("block.wooden_door.close", 0.5, 0.5)],
    "stare": [("entity.enderman.stare", 0.6, 1.5), ("block.wooden_door.open", 0.5, 0.4)],
    "impact": [("entity.armor_stand.hit", 1.2, 0.7), ("block.wood.break", 0.8, 0.9)],
    "roar": [("block.wooden_door.open", 1.2, 0.4), ("entity.armor_stand.fall", 1.0, 0.6), ("ambient.cave", 0.6, 1.0)],
    "step": [("block.bamboo_wood.step", 0.8, 0.8), ("entity.skeleton.step", 0.4, 1.4)],
}
MOVE_SOUNDS = {"corner": {"swing": "", "impact": ""}, "lights": {"swing": "snuff", "impact": ""}, "stare": {"swing": "stare", "impact": ""},
               "everywhere": {"swing": "cast", "impact": ""}, "closer": {"swing": "roar", "impact": ""}, "wake": {"swing": "", "impact": ""},
               "hold": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},  # posed in the corner the way someone left it
    "phases": [
        {"pool": [
            {"move": "reach", "weight": 3, "when": "near", "then": [["grasp"]]},
            {"move": "grasp", "weight": 2, "when": "near"},
            {"move": "fold", "weight": 2, "when": "near"},
            {"move": "corner", "weight": 3, "when": "far", "then": [["reach"]]},
            {"move": "lights", "weight": 2},
            {"move": "wrong", "weight": 3, "when": "behind"},
            {"move": "hold", "weight": 1},
        ]},
        # it is closer, as in DeepBestiary: its copies, its stare, the corner of your eye
        {"transition": "closer", "pool": [
            {"move": "reach", "weight": 3, "when": "near", "then": [["grasp"], ["grasp", "reach"]]},
            {"move": "fold", "weight": 2, "when": "near"},
            {"move": "corner", "weight": 3, "when": "far", "then": [["reach", "grasp"]]},
            {"move": "stare", "weight": 3, "when": "far"},
            {"move": "everywhere", "weight": 1},
            {"move": "wrong", "weight": 3, "when": "behind"},
            {"move": "hold", "weight": 2},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.3},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.37, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
