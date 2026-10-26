"""The animation lab: see a swing's motion, not a still (SPEC §25 "First person"). Builds each first-person frame of a
style's strikes the way the game composes it, with no game running: the swing's hand move (ArmsHands, ported line for
line below), Hands' view layer (HandPose), vanilla's resting hand, the weapon's own first-person hold and its mesh, at
the hand pass's 70 degrees. Each strike becomes an onion-skin (frames overlaid, earlier ones fainter), with the blade
tip's path drawn as a curve, the contact frame (the server's hit, the first active tick) ringed, the crosshair marked,
and two plots under it: the tip's height on screen over the move, and how far the edge leads (1: the edge cuts along
the swing's path; 0: the flat face leads, a wave).

    python3 tools/art/anim_lab.py sheet OUT.png STYLE [weapon-id] [--model old|new]
    python3 tools/art/anim_lab.py all OUT_DIR [--model old|new]       the review set: longsword, daggers, greatblade, maul
    python3 tools/art/anim_lab.py third OUT.png STYLE                 third person: the body's clips with the blade in hand
    python3 tools/art/anim_lab.py bake                                writes data/awaken/arms/fp_swings.json (the new model)
    python3 tools/art/anim_lab.py audit                               every move's working part at the hit, every weapon, both hands,
                                                                      first person and third
    python3 tools/art/anim_lab.py strip OUT.png STYLE MOVE             one move frame by frame (a turn in the hand shows)
    python3 tools/art/anim_lab.py film FRAMES_DIR OUT.png             onion-skin of real frames (the clip recorder's PNGs)
    python3 tools/art/anim_lab.py wrist OUT.png [style:move:weapon ...]  third person: the wrist's turn through each blow

The game's own frames (Hands' clip recorder, in a tour) go through `film`: the same overlay from real frames, so a
lab judgement can be checked against the game. Reading a sheet: a slice shows a long, fast streak on a clear diagonal or
horizontal through the frame's middle, the blade drawn edge-on along it (edge lead near 1 through the blow), the tip
path crossing the crosshair at or a little under it; a wave shows a broad blade turning its face to you while the hand
slides, the path high and short.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "specs"))
import mesh_render as MR  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
TABLE = os.path.join(ROOT, "src/main/resources/data/awaken/arms/archetypes.json")
SWINGS = os.path.join(ROOT, "src/main/resources/data/awaken/arms/fp_swings.json")
# ArmsHands: vanilla's resting hand and the shoulder a swing turns about (view space: x right, y up, -z ahead)
HX, HY, HZ, SX, SY = 0.56, -0.52, -0.72, 0.35, -0.75
W, H = 960, 540
FOV = 70.0
REVIEW = {"longsword": "gatewrought_longsword", "daggers": "gatewrought_daggers", "greatblade": "gatewrought_greatblade",
          "maul": "gatewrought_maul"}


def table():
    return {a["id"]: a for a in json.load(open(TABLE))["archetypes"]}


def chain_moves(style_row):
    return [m for m in style_row["chain"] + style_row.get("masteryChain", [])] + (["heavy"] if "heavy" in style_row["moves"] else [])


# ---- the swing (old model: ArmsHands.swing, ported) ------------------------------------------------------------------

def ease_out_sine(x):
    return math.sin(min(1, max(0, x)) * math.pi / 2)


def ease_out_cubic(x):
    c = 1 - min(1, max(0, x))
    return 1 - c * c * c


def ease_in_out_sine(x):
    return -(math.cos(math.pi * min(1, max(0, x))) - 1) / 2


def blow_start(m):
    return 0 if m["windup"] <= 0 else max(0.5, m["windup"] - m["active"] * 0.5)


def _v(p, i):
    return 0.0 if p is None or i >= len(p) else float(p[i])


def _lerp(frm, to, k, arc):
    fx, fy, tx, ty = _v(frm, 0), _v(frm, 1), _v(to, 0), _v(to, 1)
    if arc:
        ax, ay, bx, by = HX + fx - SX, HY + fy - SY, HX + tx - SX, HY + ty - SY
        a0, a1 = math.atan2(ay, ax), math.atan2(by, bx)
        r0, r1 = math.hypot(ax, ay), math.hypot(bx, by)
        d = math.degrees(a1 - a0)
        d = (d + 180) % 360 - 180
        ang, rad = a0 + math.radians(d) * k, r0 + (r1 - r0) * k
        x, y = SX + math.cos(ang) * rad - HX, SY + math.sin(ang) * rad - HY
    else:
        x, y = fx + (tx - fx) * k, fy + (ty - fy) * k
    roll = _v(frm, 2) + (_v(to, 2) - _v(frm, 2)) * k
    tilt = _v(frm, 3) + (_v(to, 3) - _v(frm, 3)) * k
    return x, y, roll, tilt


def old_pose(m, t):
    """(dx, dy, dz, R) of ArmsHands at t ticks: the hand's move from rest and its rotation about its own place."""
    sw = m["swing"]
    frm, to = sw["from"], sw["to"]
    a, w = m["active"], blow_start(m)
    r = max(1, m["recovery"]) + (m["windup"] - w)
    x = y = roll = tilt = lunge = 0.0
    if t < w:
        x, y, roll, tilt = _lerp(None, frm, ease_out_sine(t / max(1, w)), False)
    elif t < w + a:
        k = ease_out_cubic((t - w) / a)
        x, y, roll, tilt = _lerp(frm, to, k, True)
        lunge = sw.get("lunge", 0) * math.sin(k * math.pi / 2)
    elif t < w + a + r:
        k = ease_in_out_sine((t - w - a) / r)
        x, y, roll, tilt = _lerp(to, None, k, False)
        lunge = sw.get("lunge", 0) * (1 - k)
    R = rz(roll) @ rx(-tilt)
    return np.array([x, y, -lunge * 0.55]), R


# ---- the swing (new model: fp_swings.json keys, as dev.awaken.arms.client.ArmsHands samples them) --------------------

def new_pose(style, move, t, swings=None):
    swings = swings if swings is not None else load_swings()
    clip = swings.get(style, {}).get("moves", {}).get(move)
    if clip is None:
        return None
    keys = clip["keys"]
    if t <= keys[0][0]:
        k0 = k1 = keys[0]
        u = 0
    elif t >= keys[-1][0]:
        k0 = k1 = keys[-1]
        u = 0
    else:
        i = max(j for j in range(len(keys)) if keys[j][0] <= t)
        k0, k1 = keys[i], keys[i + 1]
        u = (t - k0[0]) / max(1e-6, k1[0] - k0[0])
    d = np.array(k0[1:4]) * (1 - u) + np.array(k1[1:4]) * u
    q = slerp(np.array(k0[4:8]), np.array(k1[4:8]), u)
    return d, quat_mat(q)


def load_swings():
    return json.load(open(SWINGS))["styles"] if os.path.exists(SWINGS) else {}


# ---- maths ---------------------------------------------------------------------------------------------------------

def rx(deg):
    a = math.radians(deg)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def ry(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def rz(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


def quat_mat(q):
    x, y, z, w = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def mat_quat(R):
    t = np.trace(R)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        return np.array([(R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s, 0.25 * s])
    i = int(np.argmax(np.diag(R)))
    j, k = (i + 1) % 3, (i + 2) % 3
    s = math.sqrt(1 + R[i, i] - R[j, j] - R[k, k]) * 2
    q = np.zeros(4)
    q[i] = 0.25 * s
    q[j] = (R[j, i] + R[i, j]) / s
    q[k] = (R[k, i] + R[i, k]) / s
    q[3] = (R[k, j] - R[j, k]) / s
    return q


def slerp(a, b, u):
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    d = float(a @ b)
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        q = a + (b - a) * u
        return q / np.linalg.norm(q)
    th = math.acos(d)
    return (math.sin((1 - u) * th) * a + math.sin(u * th) * b) / math.sin(th)


def project(P):
    f = 1.0 / math.tan(math.radians(FOV) / 2)
    return W / 2 + (P[:, 0] / -P[:, 2]) * f * (H / 2), H / 2 - (P[:, 1] / -P[:, 2]) * f * (H / 2)


# ---- the weapon in the hand --------------------------------------------------------------------------------------------

class Held:
    """A weapon's mesh in hand space (its first-person hold applied; the hand's own place at the origin) and its blade."""

    def __init__(self, wid):
        self.wid = wid
        self.m, self.tex = MR.load(wid)
        (ax, ay, az), t, sc = MR.held(wid)
        R = MR.euler(ax, ay, az)
        self.P = (self.m["pos"] - 0.5) * sc @ R.T + np.asarray(t) / 16.0
        self.N = self.m["nrm"] @ R.T
        c = self.P.mean(axis=0)
        # every design lies along the item's diagonal, tip to the upper right, its flat across z (arms_weapons): the
        # weapon's own frame, not a guess from its outline (a maul's head is longest along its striking faces, so the
        # thinnest way through the whole mesh is the way it strikes)
        axis = R @ (np.array([1.0, 1.0, 0.0]) / math.sqrt(2))
        thin = R @ np.array([0.0, 0.0, 1.0])
        self.axis = axis  # grip to tip
        self.face = thin  # the flat's normal
        self.edge = np.cross(axis, thin)
        self.tip = self.P[np.argmax(self.P @ axis)]
        pommel = self.P[np.argmin(self.P @ axis)]
        # where the hand closes: a little up from the pommel, on the blade's centre line
        along = (pommel - c) @ axis + 0.14 * ((self.tip - pommel) @ axis)
        self.grip = c + axis * along
        self.length = float((self.tip - self.grip) @ axis)


def place(held, d, R):
    """The weapon's points in view space at a pose: rotated about the hand's place, moved, at vanilla's rest."""
    return held.P @ R.T + np.array([HX, HY, HZ]) + d


NEAR = -0.06  # the hand pass's near plane (blocks): triangles reaching past it are not drawn


def frame(held, d, R):
    P = place(held, d, R)
    behind = (P[:, 2] > NEAR).reshape(-1, 3).any(axis=1).repeat(3)
    P = np.where(behind[:, None], np.array([0.0, 0.0, -1000.0]), P)
    sx, sy = project(P)
    screen = {"sx": sx, "sy": sy, "sz": P[:, 2], "nv": held.N @ R.T}
    return MR._raster(held.m, held.tex, screen, (W, H), MR.BG, time=0.27)



# ---- the rig (new model): a cut is an arc about the shoulder, the edge leading -------------------------------------
#
# A strike is authored as what a sword arm does, not as a slide of the hand: the arm and blade turn as one lever about
# the shoulder, in a cut plane through a point just under the crosshair, the blade along the arm's radius, its flat
# across the plane so the edge faces where it is going. The row of archetypes.json gives the cut's direction on the
# screen (its swing.from -> swing.to) and its kind; the timing table gives when: drawn back by the blow's start, through
# the target on the hit tick (the first active tick), a follow-through past it, home by the move's end. Thrusts drive
# the blade straight along the aim. These keys are baked to fp_swings.json, which the game samples (ArmsHands).

# a first-person rig's shoulder: below and in front of the eye (the hand pass draws the arm nearer than a body would hold
# it), so the hand stays about vanilla's resting depth through the whole arc and nothing sweeps through the lens
ARM = 0.42                                 # shoulder to the hand along the cut
DROP = 0.04                                # the hit sits this far under the crosshair (share of the distance)
KEY_DT = 0.25


def kind(m):
    sw = m["swing"]
    f, t = sw["from"], sw["to"]
    dx, dy = t[0] - f[0], t[1] - f[1]
    if len(f) >= 4 and abs(f[2]) < 40 and abs(dx) < 0.35:
        if dy < -0.4 or t[3] - f[3] > 90:
            return "chop"
        if dy > 0.4 and t[3] < f[3]:
            return "rise"
        return "thrust"
    return "slash"


TIERS = {"wildhide": "E", "beastbound": "D", "gatewrought": "C", "veilwoven": "B", "lordscale": "A", "sovereign": "S"}


def held_frame(held):
    """The held weapon's blade frame (columns: axis, edge, face) and which side of it cuts (+1 edge, -1 the other, 0
    either: a symmetric blade picks the side that turns least): its design's own (arms_holds.cut_side), never a guess
    from its outline (a katana's tip curves back toward its spine, a glaive's fluke weighs down its back)."""
    import arms_holds as AH
    F = np.stack([held.axis, np.cross(held.axis, held.face), held.face], axis=1)
    return F, AH.cut_side(held.wid.rsplit("_", 1)[-1], TIERS.get(held.wid.split("_")[0]))


def frame_to(r, t, side, F):
    """The rotation that lays the held blade along r with its cutting side toward t (the motion)."""
    r = r / np.linalg.norm(r)
    t = t - r * (t @ r)
    t = t / np.linalg.norm(t)
    options = []
    for sgn in ((1,) if side == 1 else (-1,) if side == -1 else (1, -1)):
        n = np.cross(t, r) if sgn == 1 else np.cross(r, t)
        T = np.stack([r, np.cross(r, n), n], axis=1)
        R = T @ F.T
        options.append((math.acos(max(-1, min(1, (np.trace(R) - 1) / 2))), R))
    return min(options, key=lambda o: o[0])[1]


# The lever, laid out for the eye. Every cut turns about one pivot low and to the right of the view (the sword arm's
# shoulder as the hand pass sees it), so the hand and its hilt keep to the frame's lower right, out of the way, and the
# blade reaches up from there into the cut. At the hit the blade crosses the target, a point just under the crosshair
# (TARGET, view space), at MID along its length: seen along its length rising from the corner, not down it from the
# hilt. The cut's plane holds that blade and the cut's direction on the screen; the blade turns in it about the pivot
# with its edge leading.
PIVOT = np.array([0.80, -0.64, -0.48])
TARGET = np.array([0.0, -0.10, -1.20])
MID = 0.78


def screen_perp(u):
    perp = np.array([u[1], -u[0], 0.0])
    if perp @ np.array([0.35, -1.0, 0.0]) < 0:
        perp = -perp
    return perp


# ---- the forms: how each weapon is really used (Euan 2026-10-26: "a dagger should feel like you're actually stabbing
# forwards... a hammer, you go from up to down, or side to side, but you rotate the hammer so that you have the big thing
# first... everything is just being swiped... it feels very floaty") ----------------------------------------------------
#
# Every strike has a form (archetypes.json swing.form; else read from its old row): a cut is a lever about the pivot
# (slash, chop, slam, swing, rise, whirl, reap, lash), a thrust drives the point along the aim (stab, from a straight line,
# from below, overhand or reverse-grip), a punch moves the fist (jab, hook, upper), a spin turns the weapon about its
# middle grip, a bash shoves the shield. And every style has a weight: how long it draws back and hangs there, how hard
# it accelerates into the hit (a power of the drive's progress: the heavier, the later and harder), how far it carries
# past and how it comes home, how much the wrist whips the blade, how far the body steps in, how much the view moves.

WEIGHTS = {
    # draw: share of the wind-up spent reaching the cocked pose (the rest hangs there, creeping further back);
    # power: the drive's curve (progress^power: 1 even, 3+ slow then explosive); carry: the follow-through's share of
    # the sweep; lag: the wrist's whip (degrees the blade trails the arm at the drive's start, and leads after);
    # step: blocks the body steps in on the blow; cam: the view's lean; home: how it comes back (fast, ease, drag)
    "snap": dict(draw=1.0, power=1.25, carry=0.45, lag=6, step=0.04, cam=0.7, home="fast", settle=0.8),
    "blade": dict(draw=0.85, power=2.2, carry=1.0, lag=18, step=0.07, cam=1.0, home="ease", settle=1.0),
    "heavy": dict(draw=0.7, power=3.4, carry=1.5, lag=7, step=0.13, cam=1.8, home="drag", settle=1.4),
}
WEIGHT_OF = {"daggers": "snap", "rapier": "snap", "gauntlets": "snap",
             "longsword": "blade", "katana": "blade", "spear": "blade", "twinblades": "blade", "chainblade": "blade",
             "shieldblade": "blade", "precursor": "blade",
             "axe": "heavy", "maul": "heavy", "greatblade": "heavy", "glaive": "heavy", "scythe": "heavy", "warstaff": "heavy"}

CUTS = ("slash", "chop", "slam", "swing", "rise", "whirl", "reap", "lash")
POINTED = ("stab", "jab", "bash")  # their measure is the point's lead, not the edge's


ARM_MIN = 0.36  # pivot to grip: every weapon keeps a real arm's length, a long one striking further out on the aim
REACH, PULL = 0.12, 0.22  # a scythe's blade swept this far past its target, then hauled back this far short of it


def target_for(held, lift=0.0, mid=MID, extra=0.0):
    """Where the blow lands: on the aim just under the crosshair, at TARGET's distance, or further out for a long weapon
    so its grip stays an arm's length (and {@code extra}) from the pivot, the target {@code mid} along it."""
    aim = TARGET / np.linalg.norm(TARGET)
    need = ARM_MIN + extra + mid * held.length
    b = PIVOT @ aim
    dist = b + math.sqrt(max(0.0, b * b - (PIVOT @ PIVOT - need * need)))
    t = aim * max(float(np.linalg.norm(TARGET)), dist)
    return t + np.array([0.0, lift, 0.0])


def form_of(m):
    """A move's form and its screen direction of travel (for a cut) from its row."""
    sw = m["swing"]
    form = sw.get("form")
    if form is None:
        form = {"chop": "chop", "rise": "rise", "thrust": "stab"}.get(kind(m), "slash")
    if "dir" in sw:
        u = np.array([sw["dir"][0], sw["dir"][1], 0.0])
    elif form in ("chop", "slam"):
        u = np.array([-0.22, -1.0, 0.0])
    elif form == "rise":
        u = np.array([-0.2, 1.0, 0.0])
    else:
        f, t = sw["from"], sw["to"]
        u = np.array([t[0] - f[0], (t[1] - f[1]) * 0.6, 0.0])
        if np.linalg.norm(u) < 1e-6:
            u = np.array([-1.0, 0.0, 0.0])
    return form, u / np.linalg.norm(u), sw.get("variant", "line")


def sweep(form, m, wt, u):
    """Degrees about the pivot: drawn back to -pre, through the target at 0, the blow ending at post, carried to stop.
    An overhead raises the head straight up above the view, never back over the eye; a backhand (a cut travelling
    right, from the off side) draws back less and carries further out to the right, where there is room."""
    heft = m["windup"] + m["active"]
    pre = {"slam": 72, "chop": 64, "swing": 70, "whirl": 74, "rise": 60, "lash": 58}.get(form, 50 + min(20, heft * 2.5))
    post = {"slam": 9, "whirl": 32, "swing": 26, "lash": 10, "rise": 10}.get(form, 14 + min(8, heft))
    carry = {"slam": 5, "whirl": 14, "lash": 4, "rise": 3}.get(form, 8 + min(8, heft)) * wt["carry"]
    if u[0] > 0.3 and form not in ("slam", "chop", "rise"):
        pre, post, carry = pre * 0.62, post * 1.35, carry * 1.25
    return pre, post, post + carry


def hit_tick(m):
    """When the blade crosses the target: the server's hit (the first active tick), or mid-blow when the blow starts
    on it (a released heavy, an armed Plunge)."""
    c, w = blow_start(m), m["windup"]
    return w if w > c + 0.25 else c + m["active"] * 0.5


def phases(m, wt):
    """The move's clock: blow start c, the hit, the blow's end, the follow-through's end, the move's end."""
    c, a = blow_start(m), m["active"]
    total = m["windup"] + a + m["recovery"]
    ft = max(0.75, m["recovery"] * 0.3 * wt["settle"])
    return c, hit_tick(m), c + a, min(total - 0.5, c + a + ft), total


def drive(t, c, hit, wt):
    x = min(1.0, max(0.0, (t - c) / max(1e-6, hit - c)))
    return x, x ** wt["power"]


def home_curve(x, wt):
    if wt["home"] == "fast":
        return ease_out_cubic(x)
    if wt["home"] == "drag":  # it stays a moment where the blow left it, then is hauled back
        return ease_in_out_sine(max(0.0, (x - 0.18) / 0.82))
    return ease_in_out_sine(x)


def turn_to(a, b):
    """The least turn that lays direction a along b."""
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    c = np.cross(a, b)
    s_, k = float(np.linalg.norm(c)), float(a @ b)
    if s_ < 1e-9:
        return np.eye(3) if k > 0 else axis_rot(np.cross(a, [1.0, 0, 0]) if abs(a[0]) < 0.9 else np.cross(a, [0, 1.0, 0]), 180)
    return axis_rot(c, math.degrees(math.atan2(s_, k)))


def orient(held, a, b):
    """The turn that points the held weapon's length along a, its face toward b (a fist: knuckles, back of the hand)."""
    a = a / np.linalg.norm(a)
    b = b - a * (b @ a)
    b /= np.linalg.norm(b)
    T = np.stack([a, np.cross(a, b), b], axis=1)
    Fh = np.stack([held.axis, np.cross(held.axis, held.face), held.face], axis=1)
    return T @ Fh.T


def axis_rot(axis, deg):
    axis = axis / np.linalg.norm(axis)
    a = math.radians(deg)
    K = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
    return np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * (K @ K)


def rig_pose(style_row, m, held, t):
    """(d, R) at t ticks of a move: its form, weighted by its style."""
    sid = style_row["id"]
    wt = WEIGHTS[WEIGHT_OF.get(sid, "blade")]
    form, u, variant = form_of(m)
    F, side = held_frame(held)
    H0 = np.array([HX, HY, HZ])
    rest = (np.zeros(3), np.eye(3))
    c, hit, ea, fe, total = phases(m, wt)

    def g(p, R):  # the grip at p: the hand's move from rest that puts it there, turned by R about the hand's place
        return p - H0 - R @ held.grip, R

    def windup_pose(cocked, creep=None):
        reach = max(1e-6, c * wt["draw"])
        if t < reach or creep is None:
            k = ease_out_cubic(t / reach) if wt["home"] == "fast" else ease_in_out_sine(t / reach)
            return lerp_pose(rest, cocked, min(1.0, k))
        return lerp_pose(cocked, creep, ease_out_sine((t - reach) / max(1e-6, c - reach)))

    def back_home(from_pose, x):
        return lerp_pose(from_pose, rest, home_curve(x, wt))

    if form in ("stab", "jab", "bash", "hook", "upper", "over"):
        return thrust_pose(form, variant, m, held, F, side, wt, t, g, rest, windup_pose, back_home, (c, hit, ea, fe, total))
    if form == "spin":
        return spin_pose(m, held, F, side, wt, t, g, rest, windup_pose, back_home, u, (c, hit, ea, fe, total))

    # a cut: the lever about the pivot, in the plane of the blade at the hit and the cut's direction on the screen. A
    # scythe cuts on the inside of its blade, which a sweep only drags along its length: it is swept round with the
    # blade's point leading until the blade is past the target (REACH further out), then hauled back through it (PULL),
    # the inside edge drawn through what it hooked; its blade, at the snath's top, is what crosses the target
    inner = WORK.get(sid) == "inner"
    mid = 1.0 if inner else MID
    target = target_for(held, -0.08 if form == "slam" else 0.0, mid, PULL if inner else 0.0)
    r_c = target - PIVOT
    reach = float(np.linalg.norm(r_c))
    r_c /= reach
    tan_c = u - r_c * (u @ r_c)
    tan_c /= np.linalg.norm(tan_c)
    arm = max(0.15, reach - mid * held.length)
    pre, post, stop = sweep(form, m, wt, u)
    # the wrist: cocked back so the head trails the hands into the blow, then whipped through (a hammer's or an axe's
    # head carries the most)
    lag_max = wt["lag"] * (2.0 if form == "lash" else 1.0)
    if form in ("slam", "swing", "rise", "chop") and wt["home"] == "drag":
        lag_max = max(lag_max, 16.0)
    # the forearm: turned as the swing gathers (the weapon rolled about its own handle), and turned back square just as
    # it lands, so the edge, bit or face comes round to lead; an uppercut turns the head right over to drive it up
    roll0 = {"slam": 75.0, "swing": 85.0, "rise": 170.0, "chop": 40.0, "slash": 35.0, "reap": 30.0, "whirl": 60.0,
             "lash": 50.0}.get(form, 30.0)
    step_dir = r_c * wt["step"] * (1.4 if form in ("slam", "whirl", "swing") else 1.0)

    def at(theta, lag=0.0, step=0.0, roll=0.0, out=0.0):
        th, tb = math.radians(theta), math.radians(theta - lag)
        r = math.cos(th) * r_c + math.sin(th) * tan_c
        rb = math.cos(tb) * r_c + math.sin(tb) * tan_c
        tan = -math.sin(tb) * r_c + math.cos(tb) * tan_c
        R = frame_to(rb, tan, side, F)
        if roll:
            R = axis_rot(rb, roll) @ R
        return g(PIVOT + step_dir * step + r * (arm + out), R)

    cocked = at(-pre, lag_max * 0.6, 0.0, roll0 * 0.85)
    creep = at(-pre - (8 if wt["draw"] < 1 else 0), lag_max, 0.0, roll0)
    if t < c:
        return windup_pose(cocked, creep)
    th0 = -pre - (8 if wt["draw"] < 1 else 0)
    r_start = roll0 if wt["draw"] < 1 else roll0 * 0.85
    if inner:
        # round past the target by the hook, then the haul: the blade crosses the target about the hit
        hook = max(c + (hit - c) * 0.35, (hit - 0.4 * ea) / 0.6)
        held_at = post * 0.4
        if t < hook:
            x, p = drive(t, c, hook, wt)
            return at(th0 * (1 - p), lag_max * (1 - x) ** 1.5, x, r_start * (1 - x) ** 1.6, REACH * p)
        if t < ea:
            k = ease_in_out_sine((t - hook) / max(1e-6, ea - hook))
            return at(held_at * k, 0.0, 1.0, 0.0, REACH - (REACH + PULL) * k)
        settle = held_at + (stop - held_at) * 0.3
        if t < fe:
            x = (t - ea) / max(1e-6, fe - ea)
            return at(held_at + (settle - held_at) * ease_out_cubic(x), 0.0, 1.0 - 0.3 * x, 0.0, -PULL)
        return back_home(at(settle, 0.0, 0.7, 0.0, -PULL), (t - fe) / max(1e-6, total - fe))
    if t < hit:  # accelerating into the hit: the arm leads, the forearm turning the blade square, the wrist whipping it
        x, p = drive(t, c, hit, wt)
        return at(th0 * (1 - p), lag_max * (1 - x) ** 1.5, x, r_start * (1 - x) ** 1.6)
    if t < ea:  # through the target: the blade carries on, slowing, a touch ahead of the arm, the forearm overturning
        x = (t - hit) / max(1e-6, ea - hit)
        return at(post * (1 - (1 - x) ** 2), -lag_max * 0.35 * math.sin(math.pi * x), 1.0, -roll0 * 0.12 * math.sin(math.pi * x))
    if t < fe:  # the follow-through: the weight carries it on, settling
        x = (t - ea) / max(1e-6, fe - ea)
        return at(post + (stop - post) * ease_out_cubic(x), 0.0, 1.0 - 0.3 * x)
    x = (t - fe) / max(1e-6, total - fe)
    return back_home(at(stop, 0.0, 0.7), x)


THRUST_FROM = {
    # where a thrust starts (the grip's line runs from here to the target): straight from the low right, from below,
    # overhand (from above, the point driven down into the target), reverse grip (overhand, the blade turned in the fist)
    "line": np.array([0.34, -0.40, -0.30]), "low": np.array([0.22, -0.66, -0.34]),
    "over": np.array([0.30, 0.04, -0.52]), "reverse": np.array([0.28, 0.06, -0.50]),
}


def thrust_pose(form, variant, m, held, F, side, wt, t, g, rest, windup_pose, back_home, ph):
    c, hit, ea, fe, total = ph
    if form in ("jab", "hook", "upper", "over"):
        return punch_pose(form, m, held, wt, t, g, rest, windup_pose, back_home, ph)
    start = THRUST_FROM.get(variant, THRUST_FROM["line"])
    target = target_for(held, 0.02 if variant not in ("over", "reverse") else -0.06)
    r = target - start
    r /= np.linalg.norm(r)
    level = np.cross(r, np.array([0.0, 1.0, 0.0]))
    if form == "bash":  # the shield shoved straight at the foe, its face square to the shove (turned the least way)
        out_p = held.grip + np.array([-0.24, 0.04, -0.40]) + H0_
        back_p = held.grip + np.array([0.06, -0.06, 0.10]) + H0_
        R = turn_to(-held.face, out_p - back_p)
        if t < c:
            return windup_pose(g(back_p, R))
        if t < hit:
            x, p = drive(t, c, hit, wt)
            return g(back_p + (out_p - back_p) * p, R)
        if t < fe:
            return g(out_p, R)
        return back_home(g(out_p, R), (t - fe) / max(1e-6, total - fe))
    R0 = frame_to(r, level, side, F)
    if variant == "reverse":  # the blade held edge-down in the fist, driven point first from above (no hand is drawn:
        # the grip shows as the blade turned on its edge)
        R0 = axis_rot(r, 90.0) @ R0
    lunge = m["swing"].get("lunge", 0.5)
    tip_out = held.length * 0.92
    out_d = float(np.linalg.norm(target - start)) - tip_out + 0.08 + 0.18 * min(1.0, lunge)
    back_d = out_d - 0.22 - 0.16 * min(1.0, lunge)
    pull = 0.06 if wt["home"] == "fast" else 0.03

    def at(d, twist):
        return g(start + r * d, axis_rot(r, twist) @ R0)

    if t < c:
        return windup_pose(at(back_d, -8), at(back_d - pull, -12))
    if t < hit:  # driven in: nearly even for a snap, the point arriving on the hit
        x, p = drive(t, c, hit, wt)
        return at(back_d - pull + (out_d - back_d + pull) * p, -12 + 34 * p)
    if t < fe:  # held at full reach a beat, the twist going in
        x = (t - hit) / max(1e-6, fe - hit)
        return at(out_d + 0.02 * math.sin(math.pi * x), 22 + 6 * x)
    x = (t - fe) / max(1e-6, total - fe)
    return back_home(at(out_d, 28), x)


H0_ = np.array([HX, HY, HZ])


def punch_pose(form, m, held, wt, t, g, rest, windup_pose, back_home, ph):
    """The gauntlets: the fist itself goes out (its worn hold kept, turned as a punch turns it)."""
    c, hit, ea, fe, total = ph
    base = held.grip + H0_
    paths = {
        # (start offset, end offset from the resting grip; where the back of the hand faces as the punch lands, the
        # knuckles square to the fist's travel: a jab and a hook palm down, an uppercut palm to the face, an overhand
        # driven down knuckles first)
        "jab": (np.array([0.04, -0.03, 0.10]), np.array([-0.40, 0.20, -0.30]), (0.0, 1.0, 0.2)),
        "hook": (np.array([0.16, 0.02, 0.06]), np.array([-0.42, 0.22, -0.20]), (0.0, 1.0, 0.0)),
        "upper": (np.array([0.02, -0.18, 0.06]), np.array([-0.36, 0.30, -0.26]), (0.0, 0.0, -1.0)),
        "over": (np.array([0.10, 0.22, 0.10]), np.array([-0.34, -0.02, -0.34]), (0.0, 0.4, -1.0)),
    }
    s0, s1, back = paths[form]
    big = 1.0 + 0.25 * min(1.0, m["swing"].get("lunge", 0.5))
    s1 = s1 * big
    mid = (s0 + s1) / 2 + np.array([0.14, 0.0, -0.10])  # a hook comes in on an arc
    R1 = orient(held, 2 * (s1 - mid) if form == "hook" else s1 - s0, np.array(back))
    R0 = lerp_pose((np.zeros(3), np.eye(3)), (np.zeros(3), R1), 0.15)[1]
    if t < c:
        return windup_pose(g(base + s0, R0))
    if t < hit:
        x, p = drive(t, c, hit, wt)
        if form == "hook":  # round, not straight: the fist comes in on an arc
            q = (1 - p) ** 2 * s0 + 2 * (1 - p) * p * mid + p * p * s1
        else:
            q = s0 + (s1 - s0) * p
        return g(base + q, lerp_pose((np.zeros(3), R0), (np.zeros(3), R1), p)[1])
    if t < fe:
        return g(base + s1, R1)
    return back_home(g(base + s1, R1), (t - fe) / max(1e-6, total - fe))


def spin_pose(m, held, F, side, wt, t, g, rest, windup_pose, back_home, u, ph):
    """A double weapon turned about its middle grip: a full turn and more as the hand crosses the front, both ends
    cutting in a plane tilted toward the eye."""
    c, hit, ea, fe, total = ph
    n = np.array([0.0, 1.0, 0.35])
    n /= np.linalg.norm(n)
    e1 = np.array([1.0, 0.0, 0.0])
    e1 -= n * (e1 @ n)
    e1 /= np.linalg.norm(e1)
    e2 = np.cross(n, e1)
    sgn = 1.0 if u[0] <= 0 else -1.0
    # the weapon turns about its own middle (held there), carried across the front at arm's length
    c0 = np.array([0.30, -0.30, -1.05])
    c1 = np.array([-0.10, -0.22, -1.15])
    mid = held.grip + held.axis * ((held.P @ held.axis).max() + (held.P @ held.axis).min() - 2 * (held.grip @ held.axis)) / 2

    def at(phi, s):
        ph_ = math.radians(phi) * sgn
        a = math.cos(ph_) * e1 + math.sin(ph_) * e2
        tan = -math.sin(ph_) * e1 + math.cos(ph_) * e2
        R = frame_to(a, tan * sgn, side, F)
        centre = c0 + (c1 - c0) * s
        return centre - H0_ - R @ mid, R

    turn = 400.0
    if t < c:
        return windup_pose(at(-40, 0.0))
    if t < fe:
        x = (t - c) / max(1e-6, fe - c)
        p = x ** (wt["power"] * 0.6) if x < 0.5 else 1 - (1 - x) ** 1.4 * (0.5 ** (wt["power"] * 0.6 - 1.4))
        p = min(1.0, max(0.0, p))
        return at(-40 + turn * p, min(1.0, x * 1.4))
    return back_home(at(-40 + turn, 1.0), (t - fe) / max(1e-6, total - fe))


# ---- the view: each blow leans the eye into it (yaw, pitch, roll degrees; + pitch looks down) ----------------------

CAM = {
    "slash": (1.0, 0.6, 2.0), "chop": (0.3, 2.2, 0.6), "slam": (0.4, 4.5, 1.0), "swing": (2.4, 0.4, 2.4),
    "whirl": (4.5, 0.4, 3.0), "rise": (0.3, -2.2, 0.6), "reap": (2.0, 0.4, 1.6), "lash": (1.6, 0.8, 1.2),
    "stab": (0.0, 0.6, 1.2), "jab": (0.4, 0.7, 1.2), "hook": (2.0, 0.3, 1.8), "upper": (0.2, -1.6, 0.8),
    "spin": (1.0, 0.3, 2.6), "bash": (0.0, 1.4, 0.4),
}


def camera(style_row, m, t):
    """The view's lean at t ticks: drawn the other way through the wind-up (heavy), into the blow on the drive, a sharp
    peak at the hit, then a damped settle."""
    wt = WEIGHTS[WEIGHT_OF.get(style_row["id"], "blade")]
    form, u, _ = form_of(m)
    c, hit, ea, fe, total = phases(m, wt)
    yw, pt, rl = CAM.get(form, (1.0, 0.5, 1.5))
    sx = -1.0 if u[0] < 0 else 1.0  # travelling left: the view turns and leans left
    vec = np.array([yw * sx * 0.6, pt, rl * sx]) * wt["cam"]
    if t < c:
        x = t / max(1e-6, c)
        k = -0.25 * math.sin(math.pi * x) if wt["home"] == "drag" else 0.0
    elif t < hit:
        x = (t - c) / max(1e-6, hit - c)
        k = x ** 2
    else:
        tau = {"fast": 1.0, "ease": 1.8, "drag": 2.8}[wt["home"]]
        dt = t - hit
        k = math.exp(-dt / tau) * math.cos(min(math.pi, dt * 0.5))
    return vec * k


def lerp_pose(a, b, u):
    q = slerp(mat_quat(a[1]), mat_quat(b[1]), u)
    return a[0] * (1 - u) + b[0] * u, quat_mat(q)


def bake(out=SWINGS):
    """Every style's moves as keys for the game: [t, dx, dy, dz, qx, qy, qz, qw, yaw, pitch, roll] (the hand's move from
    vanilla's rest and its turn about its own place, for the style's own forged weapon; the view's lean, degrees);
    plus each style's turn for a vanilla item."""
    styles = {}
    for sid, st in table().items():
        held = Held("gatewrought_" + sid)
        Fv, grip_v = vanilla_frame()
        Fs, _ = held_frame(held)
        moves = {}
        for mv, m in st["moves"].items():
            if "swing" not in m:
                continue
            total = m["windup"] + m["active"] + m["recovery"]
            keys = []
            for tt in np.arange(0, total + 1e-6, KEY_DT):
                d, R = rig_pose(st, m, held, float(tt))
                q = mat_quat(R)
                cam = camera(st, m, float(tt))
                keys.append([round(float(tt), 3)] + [round(float(v), 4) for v in d] + [round(float(v), 5) for v in q]
                            + [round(float(v), 3) for v in cam])
            moves[mv] = {"form": form_of(m)[0], "weight": WEIGHT_OF.get(sid, "blade"), "hand": m["swing"].get("hand", "main"),
                         "keys": keys}
            wrist = wrist_keys(sid, mv, m)  # third person: the wrist's turn, per arm (ArmsWrist)
            if wrist:
                moves[mv]["wrist3"] = wrist
        # a vanilla item held in this style: the same targets, from vanilla's own hold (R_vanilla = R . C)
        C = Fs @ Fv.T
        import arms_holds as AH
        # where the hand closes: the hold's grip; a worn weapon (the gauntlets) closes round the fist inside it
        hand_grip = AH.HOLDS.get(sid, {}).get("grip")
        if hand_grip is None:
            hand_grip = held.P.mean(axis=0)
        styles[sid] = {"twin": any(v["hand"] != "main" for v in moves.values()),
                       "handGrip": [round(float(v), 4) for v in (hand_grip if hand_grip is not None else held.grip)],
                       "vanilla": [round(float(v), 5) for v in mat_quat(C)],
                       "grip": [round(float(v), 4) for v in held.grip], "vanillaGrip": [round(float(v), 4) for v in grip_v],
                       "moves": moves}
    json.dump({"schemaVersion": 3, "generatedBy": "tools/art/anim_lab.py bake", "dt": KEY_DT, "wristDt": WRIST_DT, "styles": styles},
              open(out, "w"), separators=(",", ":"))
    return out


# ---- the working-part audit (Euan 2026-10-26: "when it's a blade, it's the sharp end that's cutting. When it's a
# dagger, it's the sharp end that's stabbing. When it's the hammer, it's that end") ----------------------------------
# What each style strikes with: a blade's edge (a single-edged one's own side, arms_holds.EDGES), an axe's bit, a
# hammer's faces (never the flat its emblem is on), a staff's ends, a scythe's inner edge (drawn back through, a pull),
# and for a thrust the point, a fist's knuckles, a shield's face. Each is held to every move at its hit: the share of
# the working part's motion that drives that part into the target (1: square on; under the bar: something else lands).
WORK = {"axe": "bit", "maul": "face", "warstaff": "end", "scythe": "inner", "gauntlets": "knuckles"}
LEAD_MIN = {"edge": 0.7, "bit": 0.7, "face": 0.7, "end": 0.6, "inner": 0.3, "point": 0.8, "knuckles": 0.8, "shield": 0.7}


def working(style, form):
    if form == "bash":
        return "shield"
    if form in POINTED:
        return "knuckles" if style == "gauntlets" else "point"
    return WORK.get(style, "edge")


def lead_at(held, side, part, d0, R0, d1, R1):
    """How squarely the working part drives into the target between two poses (signed: below 0 the wrong side leads)."""
    L = held.length
    if part == "inner":  # the scythe's blade: out from the snath's top on the -edge side, its edge on its inside
        top = held.P[(held.P - held.grip) @ held.axis > 0.8 * L]
        p = top[np.argmax(-(top - held.grip) @ (R0.T @ R0 @ held.edge) * side)] if len(top) else held.tip
    elif part in ("point", "knuckles"):
        p = held.tip
    else:
        p = held.grip + held.axis * L * (0.72 if part == "edge" else 0.92)
    v = (R1 @ p + d1) - (R0 @ p + d0)
    n = float(np.linalg.norm(v))
    if n < 1e-5:
        return float("nan")
    a, e, f = R1 @ held.axis, R1 @ held.edge, R1 @ held.face
    if part in ("point", "knuckles"):
        return float(v @ a) / n
    if part == "shield":
        return abs(float(v @ f)) / n
    if part == "inner":  # drawn back toward the hands: the inside of the blade through the target
        return float(-(v @ a)) / n
    vp = v - a * (v @ a)
    m = float(np.linalg.norm(vp))
    if m < 1e-5:
        return float("nan")
    lead = float(vp @ e) / m
    return lead * side if side and part != "face" and part != "end" else abs(lead)


def audit():
    """Every style's moves (its forged weapon, as baked) held to its working part at the hit; prints a table and returns
    the moves under the bar."""
    bad = []
    for sid, st in table().items():
        held = Held("gatewrought_" + sid)
        _, side = held_frame(held)
        cells = []
        for mv, m in st["moves"].items():
            if "swing" not in m:
                continue
            form = form_of(m)[0]
            part = working(sid, form)
            hit = hit_tick(m)
            d0, R0 = rig_pose(st, m, held, max(0.0, hit - 0.25))
            d1, R1 = rig_pose(st, m, held, hit + 0.25)
            lead = lead_at(held, side, part, d0, R0, d1, R1)
            ok = not math.isnan(lead) and lead >= LEAD_MIN[part]
            cells.append(f"{mv}:{form}/{part} {lead:+.2f}{'' if ok else ' !'}")
            if not ok:
                bad.append((sid, mv, form, part, lead))
        print(f"{sid:11s} side {side:+d}  " + "  ".join(cells))
    return bad


def audit_tiers():
    """As the game draws it: each style's baked swing (its forged weapon's) on every weapon of the style in its own hold,
    a single-edged one held to its own edge, in the hand each move strikes with (the off hand's twin in its left-hand
    hold, the move mirrored); prints the weapons whose working part fails on some move."""
    import arms_holds as AH
    import arms_weapons as AW
    M = np.diag([-1.0, 1.0, 1.0])
    bad = []
    for sid, st in table().items():
        ref = Held("gatewrought_" + sid)
        for row in [r for r in AW.CATALOG if r[8] == sid]:
            wid, lord = row[0], row[0] in AW.LORD_COLOUR
            e = AH.edge_of(sid, None if lord else row[2])
            right = Held(wid)
            fp = json.load(open(os.path.join(MR.RES, "assets/awaken/models/item", wid + "_in_hand.json")))["display"]["firstperson_lefthand"]
            (rx, ry, rz), (tx, ty, tz) = fp["rotation"], fp["translation"]
            left = HeldAs(wid, ((rx, -ry, -rz), (-tx, ty, tz), fp["scale"][0]))  # as the engine applies a left hand's
            fails = []
            for mv, m in st["moves"].items():
                if "swing" not in m:
                    continue
                form = form_of(m)[0]
                part = working(sid, form)
                hand = m["swing"].get("hand", "main")
                hit = hit_tick(m)
                d0, R0 = rig_pose(st, m, ref, max(0.0, hit - 0.25))
                d1, R1 = rig_pose(st, m, ref, hit + 0.25)
                hands = {"main": ["main"], "off": ["off"], "both": ["main", "off"]}[hand]
                for h in hands:
                    if h == "main":
                        lead = lead_at(right, e, part, d0, R0, d1, R1)
                    else:
                        lead = lead_at(left, e, part, M @ d0, M @ R0 @ M, M @ d1, M @ R1 @ M)
                    if math.isnan(lead) or lead < LEAD_MIN[part]:
                        fails.append(f"{mv}/{h} {part} {lead:+.2f}")
            if fails:
                bad.append(wid)
                print(f"{wid:32s} edge {e:+d}  " + "  ".join(fails))
    print(f"{len(bad)} weapons strike with something other than their working part")
    return bad


def _vanilla_hold(px):
    """A point of vanilla's handheld sprite (pixels, y down) in hand space under item/handheld's first-person hold."""
    (ax, ay, az), t, sc = MR.HANDHELD_FP
    R = MR.euler(ax, ay, az)
    pos = np.array([px[0] / 16.0, 1 - px[1] / 16.0, 0.5])
    return (pos - 0.5) * sc @ R.T + np.asarray(t) / 16.0


def vanilla_frame():
    """A vanilla sword's blade frame in first person (its sprite: the guard at pixel 5.5, 10.5, the tip at 15.5, 1.5;
    the face the sprite's normal) and its grip (a little below the guard)."""
    (ax, ay, az), _, _ = MR.HANDHELD_FP
    R = MR.euler(ax, ay, az)
    guard, tip = _vanilla_hold((5.5, 10.5)), _vanilla_hold((15.5, 1.5))
    axis = (tip - guard) / np.linalg.norm(tip - guard)
    face = R @ np.array([0.0, 0.0, 1.0])
    face = face - axis * (face @ axis)
    face /= np.linalg.norm(face)
    return np.stack([axis, np.cross(axis, face), face], axis=1), _vanilla_hold((3.6, 12.4))


# ---- the sheet -----------------------------------------------------------------------------------------------------

def sample(style, move, m, held, model, swings):
    total = m["windup"] + m["active"] + m["recovery"]
    ts = np.arange(0, total + 1e-6, 0.1)
    out = []
    for t in ts:
        pose = new_pose(style, move, t, swings) if model == "new" else None
        d, R = pose if pose is not None else old_pose(m, t)
        tip = held.tip @ R.T + np.array([HX, HY, HZ]) + d
        out.append((t, d, R, tip, R @ held.axis, R @ held.edge, R @ held.face))
    return out


def edge_lead(s, point=False):
    """For each sample: |cos| between the tip's motion (less its slide along the blade) and the edge (1 edge first);
    for a thrust ({@code point}), between the motion and the blade (1: the point leads)."""
    lead = []
    for i in range(len(s)):
        a, b = s[max(0, i - 1)], s[min(len(s) - 1, i + 1)]
        v = b[3] - a[3]
        ax = s[i][4]
        if point:
            n = np.linalg.norm(v)
            lead.append(abs(v @ ax) / n if n > 1e-4 else float("nan"))
            continue
        v = v - ax * (v @ ax)
        n = np.linalg.norm(v)
        lead.append(abs(v @ s[i][5]) / n if n > 1e-4 else float("nan"))
    return lead


def strike_panel(style, move, m, held, model, swings):
    HELD_GRIP[0] = held.grip
    s = sample(style, move, m, held, model, swings)
    w, a = m["windup"], m["active"]
    c = blow_start(m)
    hit = hit_tick(m)
    picks = sorted(set(round(x, 2) for x in [max(0.0, c - 0.01), c + a * 0.25, hit, c + a * 0.75, c + a, c + a + max(1.0, m["recovery"] * 0.35)]))
    base = np.asarray(Image.new("RGB", (W, H), tuple(int(c) for c in MR.BG)), dtype=float)
    acc = base.copy()
    for i, t in enumerate(picks):
        j = min(len(s) - 1, int(round(t / 0.1)))
        _, d, R, *_ = s[j]
        img = np.asarray(frame(held, d, R), dtype=float)
        mask = (np.abs(img - base).sum(axis=2) > 6)[..., None]
        alpha = 0.95 if abs(t - hit) < 1e-6 else 0.30 + 0.45 * (i / max(1, len(picks) - 1))
        acc = np.where(mask, acc * (1 - alpha) + img * alpha, acc)
    im = Image.fromarray(acc.astype(np.uint8), "RGB")
    dr = ImageDraw.Draw(im)
    tips = np.array([x[3] for x in s])
    tx, ty = project(tips)
    pts = [(float(x), float(y)) for x, y in zip(tx, ty) if -2000 < x < 3000 and -2000 < y < 3000]
    for k in range(1, len(pts)):
        t = s[k][0]
        col = (90, 150, 255) if t < w - 0.01 else (255, 210, 80) if t <= w + a + 0.01 else (150, 150, 165)
        dr.line([pts[k - 1], pts[k]], fill=col, width=3)
    hit = hit_tick(m)
    jc = min(len(s) - 1, int(round(hit / 0.1)))
    cx, cy = float(tx[jc]), float(ty[jc])
    dr.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], outline=(255, 70, 70), width=3)
    dr.line([(W / 2 - 12, H / 2), (W / 2 + 12, H / 2)], fill=(255, 255, 255), width=2)
    dr.line([(W / 2, H / 2 - 12), (W / 2, H / 2 + 12)], fill=(255, 255, 255), width=2)
    dr.text((10, 8), f"{style} · {move} · {model} model · {held.wid}", fill=(230, 232, 240))
    dr.text((10, 24), "tip path: blue wind-up, gold blow (active ticks), grey recovery; red ring: the hit (first active tick)", fill=(170, 175, 190))
    plot = plots(s, m)
    views = maps(s, m)
    sheet = Image.new("RGB", (W + views.width, H + plot.height), (18, 20, 26))
    sheet.paste(im, (0, 0))
    sheet.paste(plot, (0, H))
    sheet.paste(views, (W, 0))
    stats = {"contact_y": (H / 2 - cy) / (H / 2), "contact_x": (cx - W / 2) / (W / 2),
             "lead_blow": float(np.nanmean([l for (t, *_), l in zip(s, edge_lead(s, form_of(m)[0] in POINTED))
                                            if blow_start(m) <= t <= blow_start(m) + a])),
             "sweep_px": float(np.hypot(tx[min(len(s) - 1, int(round((w + a) / 0.1)))] - tx[int(round(max(0, w - a * 0.5) / 0.1))],
                                        ty[min(len(s) - 1, int(round((w + a) / 0.1)))] - ty[int(round(max(0, w - a * 0.5) / 0.1))]))}
    return sheet, stats


def maps(s, m):
    """Top and side views of the move (view space, the eye at the dot, its 70-degree view cone drawn): the grip's path
    (thin) and the tip's (thick), wind-up blue, blow gold, recovery grey; the blade drawn at the hit. A good cut stays in
    front, inside the cone, and never comes back at the eye."""
    size = (H + 170) // 2
    im = Image.new("RGB", (size, size * 2), (14, 15, 20))
    dr = ImageDraw.Draw(im)
    w, a = m["windup"], m["active"]
    hit = hit_tick(m)
    H0 = np.array([HX, HY, HZ])
    half = math.tan(math.radians(FOV / 2))
    for row, (title, ia, ib) in enumerate((("top (x right, ahead up)", 0, 2), ("side (ahead right, y up)", 2, 1))):
        oy = row * size
        sc = size / 2.6

        def P(v):
            if row == 0:
                return (size / 2 + v[0] * sc, oy + size - 12 + v[2] * sc)
            return (12 - v[2] * sc, oy + size / 2 - v[1] * sc)

        dr.rectangle([0, oy, size - 1, oy + size - 1], outline=(50, 54, 64))
        dr.text((6, oy + 4), title, fill=(150, 155, 170))
        e = P(np.zeros(3))
        span = half * (16 / 9 if row == 0 else 1)
        for sgn in (-1, 1):
            far = np.array([sgn * span * 2.4, 0, -2.4]) if row == 0 else np.array([0, sgn * span * 2.4, -2.4])
            dr.line([e, P(far)], fill=(70, 74, 88))
        dr.ellipse([e[0] - 4, e[1] - 4, e[0] + 4, e[1] + 4], fill=(255, 255, 255))
        for k in range(1, len(s)):
            t = s[k][0]
            col = (90, 150, 255) if t < blow_start(m) - 0.01 else (255, 210, 80) if t <= blow_start(m) + a + 0.01 else (120, 120, 135)
            g0 = s[k - 1][1] + H0 + s[k - 1][2] @ HELD_GRIP[0]
            g1 = s[k][1] + H0 + s[k][2] @ HELD_GRIP[0]
            dr.line([P(g0), P(g1)], fill=col, width=1)
            dr.line([P(s[k - 1][3]), P(s[k][3])], fill=col, width=3)
        j = min(len(s) - 1, int(round(hit / 0.1)))
        gj = s[j][1] + H0 + s[j][2] @ HELD_GRIP[0]
        dr.line([P(gj), P(s[j][3])], fill=(255, 80, 80), width=4)
    return im


HELD_GRIP = [np.zeros(3)]


def plots(s, m):
    pw, ph = W, 170
    im = Image.new("RGB", (pw, ph), (18, 20, 26))
    dr = ImageDraw.Draw(im)
    total = s[-1][0]
    w, a = m["windup"], m["active"]
    tips = np.array([x[3] for x in s])
    _, ty = project(tips)
    hy = (H / 2 - ty) / (H / 2)  # +1 top of the frame, 0 the crosshair, -1 bottom
    lead = edge_lead(s, form_of(m)[0] in POINTED)

    def X(t):
        return 40 + (pw - 60) * t / max(1e-6, total)

    for (y0, y1, title) in ((10, 80, "tip height (0 = crosshair)"), (95, 165, "edge lead (1 = edge first, 0 = face first)")):
        dr.rectangle([40, y0, pw - 20, y1], outline=(60, 64, 76))
        dr.rectangle([X(w), y0, X(w + a), y1], fill=(48, 40, 22))
        dr.text((46, y0 + 2), title, fill=(160, 165, 180))
    for k in range(1, len(s)):
        y_a = 45 - max(-1.2, min(1.2, hy[k - 1])) * 28
        y_b = 45 - max(-1.2, min(1.2, hy[k])) * 28
        dr.line([(X(s[k - 1][0]), y_a), (X(s[k][0]), y_b)], fill=(255, 210, 80), width=2)
        if not (math.isnan(lead[k - 1]) or math.isnan(lead[k])):
            dr.line([(X(s[k - 1][0]), 165 - lead[k - 1] * 68), (X(s[k][0]), 165 - lead[k] * 68)], fill=(120, 220, 160), width=2)
    dr.line([(40, 45), (pw - 20, 45)], fill=(90, 90, 100))
    for t in range(0, int(total) + 1):
        dr.text((X(t) - 3, 82), str(t), fill=(110, 115, 128))
    return im


def sheet(out, style, wid=None, model="old"):
    rows = table()
    st = rows[style]
    held = Held(wid or REVIEW.get(style, "gatewrought_" + style))
    swings = load_swings()
    panels, report = [], {}
    for mv in chain_moves(st):
        p, stats = strike_panel(style, mv, st["moves"][mv], held, model, swings)
        panels.append(p.resize((p.width // 2, p.height // 2), Image.LANCZOS))
        report[mv] = stats
    cols = 2
    pw, ph = panels[0].size
    out_im = Image.new("RGB", (pw * cols, ph * ((len(panels) + cols - 1) // cols)), (12, 13, 17))
    for i, p in enumerate(panels):
        out_im.paste(p, ((i % cols) * pw, (i // cols) * ph))
    out_im.save(out)
    return out, report


# ---- third person: the body's clip (arms_moves.py) with the blade in its hand ----------------------------------------

HANDHELD_3P = ((0, -90, 55), (0, 4.0, 0.5), 0.85)  # vanilla item/handheld's thirdperson_righthand


def _clip_name(style, move):
    return f"arms_{move}" if style == "longsword" else f"arms_{style}_{move}"


def blade_3p(frames, held_pts):
    """The weapon's grip, tip and edge in the figure's model space (pixels, y down) as vanilla's ItemInHandLayer holds it:
    the right arm's frame, X -90, Y 180, (1, 2, -10)/16, the item's third-person hold."""
    M, T = frames["right_arm:lower"] if "right_arm:lower" in frames else frames["right_arm"]
    M = np.array(M)
    T = np.array(T)
    (ax, ay, az), t, sc = HANDHELD_3P
    Rd = MR.euler(ax, ay, az)
    hold = rx(-90) @ ry(180)
    out = []
    for p in held_pts:
        q = hold @ (np.array([1 / 16, 2 / 16, -10 / 16]) + np.asarray(t) / 16 + Rd @ ((np.asarray(p) - 0.5) * sc))
        out.append(T + M @ (q * 16))
    return out


# ---- third person: the wrist (Euan 2026-10-26, "hold third person to the same working parts") ----------------------
# Vanilla's third-person hold sets every weapon across the fist, square to the forearm, so the forearm's roll swings
# the blade round rather than turning its edge: a vanilla arm has no wrist. The game gives it one (ArmsWrist): through
# each move the weapon turns about its own length in the fist, through its grip, so its working side leads the blow,
# baked here per style and move for the arm that holds it. A single-edged tier on the other side from its style's is
# turned over in its third-person hold (arms_weapons.py holds), as in first person.
HANDHELD_3P_LEFT = ((0, 90, -55), (0, 4.0, 0.5), 0.85)  # vanilla item/handheld's thirdperson_lefthand, as written
WRIST_DT = 0.5  # ticks between the wrist's keys


def display_3p(wid, arm):
    """A weapon's third-person hold for an arm (its model's, else vanilla handheld's), as written in the JSON."""
    d = json.load(open(os.path.join(MR.RES, "assets/awaken/models/item", wid + "_in_hand.json")))["display"]
    v = d.get(f"thirdperson_{arm}hand")
    if v is None:
        return HANDHELD_3P if arm == "right" else HANDHELD_3P_LEFT
    return tuple(v["rotation"]), tuple(v["translation"]), v["scale"][0]


def hand_3p(frames, arm, display, roll=0.0):
    """Item points (item space, n x 3) to the figure's model space (pixels, y down) for the item in {@code arm}'s hand,
    as ItemInHandLayer draws it (the arm's frame, X -90, Y 180, (+-1, 2, -10)/16; the hold, a left hand's mirrored as
    ItemTransform applies it), turned {@code roll} degrees about its own length through its grip (the wrist)."""
    import arms_holds as AH
    M, T = (np.array(v) for v in frames[arm + "_arm"])
    (ax, ay, az), t, sc = display
    left = arm == "left"
    if left:
        ay, az, t = -ay, -az, (-t[0], t[1], t[2])
    Rd = MR.euler(ax, ay, az)
    hold = rx(-90) @ ry(180)
    o = np.array([(-1.0 if left else 1.0) / 16, 2 / 16, -10 / 16])
    Rr = axis_rot(AH.AXIS_ITEM, roll) if roll else np.eye(3)
    G = AH.GRIP_ITEM

    def f(p):
        q = (np.atleast_2d(p) - G) @ Rr.T + G
        q = (o + np.asarray(t) / 16 + ((q - 0.5) * sc) @ Rd.T) @ hold.T
        return T + (q * 16) @ M.T
    return f


def clip_beats(m):
    """A strike clip's blow (arms_moves.strike): its start, the hit (the first active tick), its end, the hold, the end."""
    w, a, r = m["windup"], m["active"], m["recovery"]
    c = max(0.5, w - a * 0.5) if w > 0 else 0.0
    return c, c + a * 0.5, c + a, c + a + max(1, r // 3), w + a + r


def arms_3p(style, mv, m):
    """The arms that hold a weapon that strikes in a move: the right (the blade arm), the left for a pair's off-hand or
    both-hands strike (arms_moves.styled: the off hand's moves are mirrored onto the left arm)."""
    import arms_holds as AH  # noqa: F401
    hand = m["swing"].get("hand", "main")
    return {"main": ["right"], "off": ["left"], "both": ["right", "left"]}[hand]


def lead_3p(f0, f1, part, side, L):
    """How squarely the working part drives into the target between two placements (item -> model space): as
    lead_at, for the item's own frame (its length the diagonal, its edge axis x face)."""
    import arms_holds as AH
    A, Z = AH.AXIS_ITEM, AH.FACE_ITEM
    E = np.cross(A, Z)
    G = AH.GRIP_ITEM
    p = G + A * L * (0.72 if part == "edge" else 0.92 if part not in ("point", "knuckles") else 1.0)
    v = f1(p)[0] - f0(p)[0]
    n = float(np.linalg.norm(v))
    if n < 1e-6:
        return float("nan")
    a = f1(G + A)[0] - f1(G)[0]
    a /= np.linalg.norm(a)
    e = f1(G + E)[0] - f1(G)[0]
    z = f1(G + Z)[0] - f1(G)[0]
    e, z = e / np.linalg.norm(e), z / np.linalg.norm(z)
    if part in ("point", "knuckles"):
        return float(v @ a) / n
    if part == "shield":
        return abs(float(v @ z)) / n
    vp = v - a * (v @ a)
    k = float(np.linalg.norm(vp))
    if k < 1e-6:
        return float("nan")
    lead = float(vp @ e) / k
    return lead * side if side and part not in ("face", "end") else abs(lead)


def wrist_at(f0, f1, part, side, L):
    """The wrist's turn (degrees about the weapon's length) that lays its working side square into the blow: the turn
    whose edge lies along the motion (less its slide), the shorter of the two for a weapon that works with both sides;
    0 for a thrust (its point is on its length) and a punch."""
    import arms_holds as AH
    if part in ("point", "knuckles"):
        return 0.0
    A, Z = AH.AXIS_ITEM, AH.FACE_ITEM
    E = np.cross(A, Z)
    G = AH.GRIP_ITEM
    p = G + A * L * (0.72 if part == "edge" else 0.92)
    v = f1(p)[0] - f0(p)[0]
    # the motion in the item's own frame at f1 (the placement is a turn and a scale: undo it on directions)
    basis = np.stack([f1(G + A)[0] - f1(G)[0], f1(G + E)[0] - f1(G)[0], f1(G + Z)[0] - f1(G)[0]], axis=1)
    loc = np.linalg.solve(basis, v)  # v in (A, E, Z) terms
    want = Z if part == "shield" else E
    # turning by psi about A carries E to E cos psi + (A x E) sin psi; A x E = -Z (E = A x Z): the edge then points
    # along (cos psi, -sin psi) in (E, Z) terms
    ang = math.degrees(math.atan2(-loc[2], loc[1])) if part != "shield" else math.degrees(math.atan2(loc[1], loc[2]))
    if part == "shield" or not side or part in ("face", "end"):
        return (ang + 90) % 180 - 90  # either side works: the shorter turn
    if side < 0:
        ang += 180
    return (ang + 180) % 360 - 180


def clip_of(style, mv):
    import arms_moves as A
    return A.CLIPS.get(_clip_name(style, mv)) or A.CLIPS.get("arms_" + mv)


def part_3p(style, form):
    """The working part in third person: the scythe's blade leads point first (its haul back is first person's)."""
    part = working(style, form)
    return "edge" if part == "inner" else part


def wrist_keys(style, mv, m, wid=None):
    """{arm: [degrees every WRIST_DT ticks]} for a move's clip: from the hand's rest to the turn that lays the working
    side into the blow by the wind-up's end (a heavy loosed from its draw: early in the blow), held through the blow,
    home by the clip's end."""
    import arms_holds as AH
    import power_casts as PC
    c_ = clip_of(style, mv)
    if c_ is None:
        return {}
    wid = wid or "gatewrought_" + style
    side = AH.cut_side(style, "C")
    part = part_3p(style, form_of(m)[0])
    L = held_length_item(wid)
    c, hit, ea, hold, end = clip_beats(m)
    out = {}
    for arm in arms_3p(style, mv, m):
        disp = display_3p(wid, arm)
        fr = lambda t: PC.part_frames(c_.sample(float(t), 8.0), 8.0)
        psi = wrist_at(hand_3p(fr(max(0.0, hit - 0.25)), arm, disp), hand_3p(fr(hit + 0.25), arm, disp), part, side, L)
        if abs(psi) < 1.0:
            continue
        keys = []
        ready = c if c > 0 else hit * 0.6  # a heavy loosed from its held draw turns as it goes, square by the hit
        for t in np.arange(0, end + 1e-6, WRIST_DT):
            if t < ready:
                k = ease_in_out_sine(t / max(1e-6, ready))
            elif t <= hold:
                k = 1.0
            else:
                k = 1.0 - ease_in_out_sine((t - hold) / max(1e-6, end - hold))
            keys.append(round(psi * k, 1))
        out[arm] = keys
    return out


def held_length_item(wid):
    """The weapon's length from its grip along the item's diagonal (item units)."""
    import arms_holds as AH
    m, _ = MR.load(wid)
    return float(((m["pos"] - AH.GRIP_ITEM) @ AH.AXIS_ITEM).max())


def wrist_now(keys, t):
    if not keys:
        return 0.0
    x = max(0.0, t / WRIST_DT)
    i = min(len(keys) - 1, int(x))
    j = min(len(keys) - 1, i + 1)
    return keys[i] + (keys[j] - keys[i]) * min(1.0, x - i)


def audit_3p(verbose=True):
    """Third person, as the game draws it: each style's clips with the wrist's baked turn, on every weapon of the
    style in its own third-person hold, in the arm that strikes; prints the moves whose working part fails."""
    import arms_holds as AH
    import arms_weapons as AW
    import power_casts as PC
    swings = load_swings()
    bad = []
    for sid, st in table().items():
        rows = [r for r in AW.CATALOG if r[8] == sid]
        lens = {r[0]: held_length_item(r[0]) for r in rows}
        cells = []
        for mv in chain_moves(st) + [k for k in ("riposte", "art", "slipcut", "undercut", "plunge", "execution") if k in st["moves"]]:
            m = st["moves"][mv]
            c_ = clip_of(sid, mv)
            if c_ is None or "swing" not in m:
                continue
            form = form_of(m)[0]
            part = part_3p(sid, form)
            c, hit, ea, hold, end = clip_beats(m)
            f0, f1 = (PC.part_frames(c_.sample(float(t), 8.0), 8.0) for t in (max(0.0, hit - 0.25), hit + 0.25))
            wk = swings.get(sid, {}).get("moves", {}).get(mv, {}).get("wrist3", {})
            worst = None
            for arm in arms_3p(sid, mv, m):
                roll0, roll1 = wrist_now(wk.get(arm), max(0.0, hit - 0.25)), wrist_now(wk.get(arm), hit + 0.25)
                for r in rows:
                    e = AH.edge_of(sid, None if r[0] in AW.LORD_COLOUR else r[2])
                    disp = display_3p(r[0], arm)
                    lead = lead_3p(hand_3p(f0, arm, disp, roll0), hand_3p(f1, arm, disp, roll1), part, e, lens[r[0]])
                    if worst is None or (math.isnan(lead) or lead < worst[0]):
                        worst = (lead, r[0], arm)
            ok = worst is not None and not math.isnan(worst[0]) and worst[0] >= LEAD_MIN[part]
            cells.append(f"{mv}:{form}/{part} {worst[0]:+.2f}" + ("" if ok else f" ! ({worst[1]}, {worst[2]})"))
            if not ok:
                bad.append((sid, mv, worst))
        if verbose:
            print(f"{sid:11s} " + "  ".join(cells))
    print(f"{len(bad)} third-person moves strike with something other than their working part")
    return bad


def third_panel(style, move, m):
    import arms_moves as A
    import power_casts as PC
    name = _clip_name(style, move)
    c = A.CLIPS.get(name)
    if c is None:
        return None, None
    # a sword's sprite line (vanilla's handheld; our weapons use the same third-person hold): pommel, guard, tip, and a
    # point off the edge to tell which way the edge faces
    pts = [(2.5 / 16, 1 - 13.5 / 16, 0.5), (15.5 / 16, 1 - 1.5 / 16, 0.5), (13.0 / 16, 1 - 1.0 / 16, 0.5)]
    total = m["windup"] + m["active"] + m["recovery"]
    ts = np.arange(0, total + 1e-6, 0.1)
    views = (("front", -35.0, 10.0), ("side", -90.0, 6.0))
    hit = hit_tick(m)
    panels = []
    for vname, yaw, pitch in views:
        bounds = PC.strip_bounds([c], yaw, pitch)
        px = 5.0
        img = PC.frame_image(c, hit, yaw, pitch, px, bounds).convert("RGB")
        dr = ImageDraw.Draw(img)
        minx, maxx, miny, maxy = bounds
        offx, offy = 8 - minx * px, 8 - miny * px

        def S(v):
            q = PC._project([tuple(v)], yaw, pitch)[0]
            return (q[0] * px + offx, q[1] * px + offy)

        tips, lead = [], []
        for i, t in enumerate(ts):
            fr = PC.part_frames(c.sample(float(t), 8.0), 8.0)
            g, tip, e = blade_3p(fr, pts)
            tips.append((t, g, tip, e))
        for k in range(1, len(tips)):
            t = tips[k][0]
            col = (90, 150, 255) if t < blow_start(m) - 0.01 else (255, 210, 80) if t <= blow_start(m) + m["active"] + 0.01 else (150, 150, 165)
            dr.line([S(tips[k - 1][2]), S(tips[k][2])], fill=col, width=2)
            if k % 3 == 0:
                dr.line([S(tips[k][1]), S(tips[k][2])], fill=col + (0,) if False else col, width=1)
        j = min(len(tips) - 1, int(round(hit / 0.1)))
        dr.line([S(tips[j][1]), S(tips[j][2])], fill=(255, 70, 70), width=4)
        dr.text((6, 4), f"{vname}", fill=(220, 222, 230))
        panels.append(img)
        # edge lead on the 3D path (the same measure as first person)
        for i in range(len(tips)):
            a_, b_ = tips[max(0, i - 1)], tips[min(len(tips) - 1, i + 1)]
            v = np.array(b_[2]) - np.array(a_[2])
            axis = np.array(tips[i][2]) - np.array(tips[i][1])
            axis /= np.linalg.norm(axis)
            edge = np.array(tips[i][3]) - np.array(tips[i][2])
            edge = edge - axis * (edge @ axis)
            edge /= max(1e-9, np.linalg.norm(edge))
            if form_of(m)[0] in POINTED:  # a thrust: the point leads
                n = np.linalg.norm(v)
                lead.append(abs(v @ axis) / n if n > 1e-4 else float("nan"))
                continue
            v = v - axis * (v @ axis)
            n = np.linalg.norm(v)
            lead.append(abs(v @ edge) / n if n > 1e-4 else float("nan"))
    w = sum(p.width for p in panels)
    h = max(p.height for p in panels) + 18
    out = Image.new("RGB", (w, h), (18, 20, 26))
    x = 0
    for p in panels:
        out.paste(p, (x, 18))
        x += p.width
    blow = [l for (t, *_), l in zip(tips, lead) if blow_start(m) <= t <= blow_start(m) + m["active"]]
    stat = float(np.nanmean(blow)) if blow else float("nan")
    ImageDraw.Draw(out).text((6, 2), f"{style} · {move} · third person ({name}) · edge lead {stat:.2f}", fill=(230, 232, 240))
    return out, stat


def third_sheet(out, style):
    st = table()[style]
    panels, rep = [], {}
    for mv in chain_moves(st):
        p, stat = third_panel(style, mv, st["moves"][mv])
        if p is not None:
            panels.append(p)
            rep[mv] = stat
    w = max(p.width for p in panels)
    h = sum(p.height for p in panels)
    im = Image.new("RGB", (w, h), (12, 13, 17))
    y = 0
    for p in panels:
        im.paste(p, (0, y))
        y += p.height
    im.save(out)
    return out, rep


# ---- holds: first person at rest beside the same weapon in F5 --------------------------------------------------------

STYLES = ["longsword", "axe", "maul", "spear", "greatblade", "rapier", "daggers", "katana", "twinblades", "glaive", "scythe",
          "gauntlets", "chainblade", "shieldblade", "warstaff", "precursor"]


class HeldAs(Held):
    """Held with a given first-person display (rotation, translation, scale) instead of the one in its model JSON."""

    def __init__(self, wid, display):
        saved = MR.held
        MR.held = lambda _w: display
        try:
            super().__init__(wid)
        finally:
            MR.held = saved


def fp_rest(wid, display=None, size=(480, 270), twin=False):
    held = HeldAs(wid, display) if display is not None else Held(wid)
    img = frame(held, np.zeros(3), np.eye(3))
    if twin:  # a pair: the off hand's twin, mirrored
        M = np.diag([-1.0, 1.0, 1.0])
        P = (place(held, np.zeros(3), np.eye(3))) @ M
        sx, sy = project(P)
        tri = np.arange(len(P)).reshape(-1, 3)[:, ::-1].reshape(-1)  # mirroring turns the faces: keep them facing
        m2 = {k: (v[tri] if hasattr(v, "shape") and len(v) == len(P) else v) for k, v in held.m.items()}
        screen = {"sx": sx[tri], "sy": sy[tri], "sz": P[tri, 2], "nv": (held.N @ M)[tri]}
        other = np.asarray(MR._raster(m2, held.tex, screen, (W, H), MR.BG, time=0.27), dtype=float)
        base = np.asarray(img, dtype=float)
        mask = (np.abs(other - MR.BG).sum(axis=2) > 6)[..., None]
        img = Image.fromarray(np.where(mask, other, base).astype(np.uint8), "RGB")
    img = img.resize(size, Image.LANCZOS)
    dr = ImageDraw.Draw(img)
    w, h = size
    dr.line([(w / 2 - 6, h / 2), (w / 2 + 6, h / 2)], fill=(255, 255, 255))
    dr.line([(w / 2, h / 2 - 6), (w / 2, h / 2 + 6)], fill=(255, 255, 255))
    return img


def f5(wid, yaw=-62.0, pitch=10.0, px=7.0, size=(300, 270)):
    """The weapon in the right hand of the standing figure (F5's own size: vanilla's third-person hold), from the front
    right so its length shows."""
    import power_casts as PC
    m, tex = MR.load(wid)
    c = PC.Clip("__idle", [(0, PC.NEUTRAL, "linear")], 1)
    frames = PC.part_frames(c.sample(0.0, 8.0), 8.0)
    pts = np.array(blade_3p(frames, list(m["pos"])))
    cam = np.array(PC._cam(yaw, pitch))
    q = pts @ cam.T
    figb = PC.strip_bounds([c], yaw, pitch)
    minx, maxx = min(figb[0], q[:, 0].min()), max(figb[1], q[:, 0].max())
    miny, maxy = min(figb[2], q[:, 1].min()), max(figb[3], q[:, 1].max())
    bounds = (minx - 2, maxx + 2, miny - 2, maxy + 2)
    fig = PC.frame_image(c, 0, yaw, pitch, px, bounds).convert("RGB")
    offx, offy = 8 - bounds[0] * px, 8 - bounds[2] * px
    screen = {"sx": q[:, 0] * px + offx, "sy": q[:, 1] * px + offy, "sz": -q[:, 2],
              "nv": (m["nrm"] @ np.array(PC._cam(yaw, pitch)).T)}
    wimg = MR._raster(m, tex, screen, fig.size, np.array([1, 2, 3]), time=0.27)
    a = np.asarray(wimg).astype(int)
    mask = (np.abs(a - np.array([1, 2, 3])).sum(axis=2) > 0)[..., None]
    out = np.where(mask, a, np.asarray(fig))
    im = Image.fromarray(out.astype(np.uint8), "RGB")
    im.thumbnail(size, Image.LANCZOS)
    canvas = Image.new("RGB", size, (34, 37, 46))
    canvas.paste(im, ((size[0] - im.width) // 2, (size[1] - im.height) // 2))
    return canvas


def figure_3p(c_, t, weapons, yaw, pitch, px, bounds):
    """The figure at t of a clip with weapons in its hands: [(wid, arm, roll)], each placed as ItemInHandLayer does."""
    import power_casts as PC
    fr = PC.part_frames(c_.sample(float(t), 8.0), 8.0)
    out = np.asarray(PC.frame_image(c_, float(t), yaw, pitch, px, bounds).convert("RGB")).astype(int)
    cam = np.array(PC._cam(yaw, pitch))
    offx, offy = 8 - bounds[0] * px, 8 - bounds[2] * px
    for wid, arm, roll in weapons:
        m, tex = MR.load(wid)
        f = hand_3p(fr, arm, display_3p(wid, arm), roll)
        q = f(m["pos"]) @ cam.T
        o = f(np.zeros(3))[0]
        R = np.stack([f(np.eye(3)[i])[0] - o for i in range(3)], axis=1)
        R /= np.linalg.norm(R[:, 0])
        screen = {"sx": q[:, 0] * px + offx, "sy": q[:, 1] * px + offy, "sz": -q[:, 2], "nv": m["nrm"] @ (cam @ R).T}
        a = np.asarray(MR._raster(m, tex, screen, (out.shape[1], out.shape[0]), np.array([1, 2, 3]), time=0.27)).astype(int)
        mask = (np.abs(a - np.array([1, 2, 3])).sum(axis=2) > 0)[..., None]
        out = np.where(mask, a, out)
    return Image.fromarray(out.astype(np.uint8), "RGB")


def wrist_sheet(out, picks, yaw=-35.0, pitch=10.0, px=6.0):
    """Third person: each pick (style, move, weapon id) at its hit without the wrist's turn, then through its blow with
    it (a quarter in, the hit, three quarters, the blow's end), with the working part's lead at the hit."""
    import arms_holds as AH
    import power_casts as PC
    swings = load_swings()
    rows = []
    for sid, mv, wid in picks:
        st = table()[sid]
        m = st["moves"][mv]
        c_ = clip_of(sid, mv)
        c, hit, ea, hold, end = clip_beats(m)
        wk = swings[sid]["moves"][mv].get("wrist3", {})
        arms = arms_3p(sid, mv, m)
        both = "left" in arms or sid in ("daggers", "gauntlets")
        bounds = PC.strip_bounds([c_], yaw, pitch)
        bounds = (bounds[0] - 14, bounds[1] + 14, bounds[2] - 14, bounds[3] + 8)
        e = AH.edge_of(sid, None if not wid.split("_")[0] in TIERS else TIERS[wid.split("_")[0]])
        part = part_3p(sid, form_of(m)[0])
        Ln = held_length_item(wid)

        def lead(arm, rolled):
            fr = lambda t: PC.part_frames(c_.sample(float(t), 8.0), 8.0)
            r0 = wrist_now(wk.get(arm), max(0.0, hit - 0.25)) if rolled else 0.0
            r1 = wrist_now(wk.get(arm), hit + 0.25) if rolled else 0.0
            disp = display_3p(wid, arm)
            return lead_3p(hand_3p(fr(max(0.0, hit - 0.25)), arm, disp, r0), hand_3p(fr(hit + 0.25), arm, disp, r1), part, e, Ln)

        def weapons(t, rolled):
            hands = ["right", "left"] if both else ["right"]
            return [(wid, a, wrist_now(wk.get(a), t) if rolled else 0.0) for a in hands]

        cells = [(f"no wrist, hit  lead {lead(arms[0], False):+.2f}", figure_3p(c_, hit, weapons(hit, False), yaw, pitch, px, bounds))]
        for t in (c + (ea - c) * 0.25, hit, c + (ea - c) * 0.75, ea):
            cap = f"t {t:.1f}" + (f"  lead {lead(arms[0], True):+.2f}" if abs(t - hit) < 1e-6 else "")
            cells.append((cap, figure_3p(c_, t, weapons(t, True), yaw, pitch, px, bounds)))
        rows.append((f"{wid} {mv} ({form_of(m)[0]}, {part}, {'/'.join(arms)})", cells))
    w = max(im.width for _, r in rows for _, im in r)
    h = max(im.height for _, r in rows for _, im in r)
    sheet = Image.new("RGB", (5 * w, len(rows) * (h + 30)), (18, 20, 26))
    dr = ImageDraw.Draw(sheet)
    for j, (label, cells) in enumerate(rows):
        y = j * (h + 30)
        dr.text((6, y + 2), label, fill=(235, 236, 242))
        for i, (cap, im) in enumerate(cells):
            sheet.paste(im, (i * w, y + 28))
            dr.text((i * w + 6, y + 16), cap, fill=(255, 210, 80) if i == 0 else (150, 220, 160))
    sheet.save(out)
    return out


def holds_sheet(out, styles=None, new=None, tier="gatewrought"):
    """Each style: first person at rest as it is (the model JSON), as {@code new} would hold it, and in F5."""
    styles = styles or STYLES
    rows = []
    for st in styles:
        wid = f"{tier}_{st}"
        cells = [fp_rest(wid)]
        if new is not None:
            cells.append(fp_rest(wid, new(wid)))
        cells.append(f5(wid))
        rows.append((st, cells))
    cw = sum(c.width for c in rows[0][1]) + 10 * len(rows[0][1])
    ch = rows[0][1][0].height + 18
    sheet = Image.new("RGB", (cw, ch * len(rows) + 20), (14, 15, 20))
    dr = ImageDraw.Draw(sheet)
    heads = ["first person at rest, now"] + (["first person, the new hold"] if new is not None else []) + ["F5 (true size)"]
    for r, (st, cells) in enumerate(rows):
        y = 20 + r * ch
        x = 0
        dr.text((6, y + 2), st, fill=(230, 232, 240))
        for i, c in enumerate(cells):
            sheet.paste(c, (x, y + 16))
            if r == 0:
                dr.text((x + 6, 4), heads[i], fill=(170, 175, 190))
            x += c.width + 10
    sheet.save(out)
    return out


def strip(out, style, move, n=10, wid=None):
    """One move frame by frame, in order (the lab's own frames): what an onion-skin can't show, a turn in the hand."""
    st = table()[style]
    m = st["moves"][move]
    held = Held(wid or "gatewrought_" + style)
    sw = load_swings()
    total = m["windup"] + m["active"] + m["recovery"]
    hit = hit_tick(m)
    ts = sorted(set([round(x, 2) for x in np.linspace(0, min(total, hit + m["active"] + 4), n)] + [round(hit, 2)]))
    cells = []
    for t in ts:
        d, R = new_pose(style, move, t, sw)
        im = frame(held, d, R).resize((320, 180), Image.LANCZOS)
        dr = ImageDraw.Draw(im)
        dr.line([(154, 90), (166, 90)], fill=(255, 255, 255))
        dr.line([(160, 84), (160, 96)], fill=(255, 255, 255))
        dr.text((6, 4), f"t {t:.1f}" + ("  HIT" if abs(t - hit) < 1e-6 else ""), fill=(255, 120, 120) if abs(t - hit) < 1e-6 else (200, 205, 215))
        cells.append(im)
    W_ = 320 * min(6, len(cells))
    rows = (len(cells) + 5) // 6
    sheet = Image.new("RGB", (W_, rows * 180 + 18), (14, 15, 20))
    ImageDraw.Draw(sheet).text((6, 3), f"{style} · {move} · {form_of(m)[0]} · frame by frame", fill=(230, 232, 240))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % 6) * 320, 18 + (i // 6) * 180))
    sheet.save(out)
    return out


def film(frames_dir, out):
    """Onion-skin of the game's own frames (sorted by name), the moving pixels overlaid, later ones stronger."""
    names = sorted(n for n in os.listdir(frames_dir) if n.lower().endswith(".png"))
    if not names:
        raise SystemExit("no frames in " + frames_dir)
    imgs = [np.asarray(Image.open(os.path.join(frames_dir, n)).convert("RGB"), dtype=float) for n in names]
    base = np.median(np.stack(imgs), axis=0)
    acc = base.copy()
    for i, img in enumerate(imgs):
        mask = (np.abs(img - base).sum(axis=2) > 30)[..., None]
        alpha = 0.25 + 0.6 * i / max(1, len(imgs) - 1)
        acc = np.where(mask, acc * (1 - alpha) + img * alpha, acc)
    im = Image.fromarray(acc.astype(np.uint8), "RGB")
    dr = ImageDraw.Draw(im)
    w, h = im.size
    dr.line([(w / 2 - 14, h / 2), (w / 2 + 14, h / 2)], fill=(255, 255, 255), width=2)
    dr.line([(w / 2, h / 2 - 14), (w / 2, h / 2 + 14)], fill=(255, 255, 255), width=2)
    im.save(out)
    return out


def main(argv):
    model = "old"
    if "--model" in argv:
        i = argv.index("--model")
        model = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if not argv:
        raise SystemExit(__doc__)
    if argv[0] == "sheet":
        out, rep = sheet(argv[1], argv[2], argv[3] if len(argv) > 3 else None, model)
        print(out)
        for k, v in rep.items():
            print(f"  {k:9s} contact y {v['contact_y']:+.2f} x {v['contact_x']:+.2f}  edge lead {v['lead_blow']:.2f}  sweep {v['sweep_px']:.0f}px")
        return 0
    if argv[0] == "all":
        os.makedirs(argv[1], exist_ok=True)
        for style in REVIEW:
            out, rep = sheet(os.path.join(argv[1], f"swing-{style}-{model}.png"), style, None, model)
            print(out)
            for k, v in rep.items():
                print(f"  {k:9s} contact y {v['contact_y']:+.2f} x {v['contact_x']:+.2f}  edge lead {v['lead_blow']:.2f}  sweep {v['sweep_px']:.0f}px")
        return 0
    if argv[0] == "third":
        out, rep = third_sheet(argv[1], argv[2])
        print(out)
        for k, v in rep.items():
            print(f"  {k:9s} edge lead {v:.2f}")
        return 0
    if argv[0] == "bake":
        print(bake())
        return 0
    if argv[0] == "audit":
        bad = audit()
        print(f"{len(bad)} moves strike with something other than their working part")
        tiers = audit_tiers()
        return 1 if bad or tiers or audit_3p() else 0
    if argv[0] == "strip":
        print(strip(argv[1], argv[2], argv[3]))
        return 0
    if argv[0] == "wrist":
        picks = [tuple(a.split(":")) for a in argv[2:]] or [
            ("maul", "strike_2", "gatewrought_maul"), ("katana", "strike_3", "gatewrought_katana"),
            ("axe", "strike_2", "gatewrought_axe"), ("scythe", "strike_1", "gatewrought_scythe"),
            ("daggers", "strike_5", "wildhide_daggers"), ("shieldblade", "strike_2", "gatewrought_shieldblade"),
            ("longsword", "strike_3", "beastbound_longsword")]
        print(wrist_sheet(argv[1], picks))
        return 0
    if argv[0] == "film":
        print(film(argv[1], argv[2]))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
