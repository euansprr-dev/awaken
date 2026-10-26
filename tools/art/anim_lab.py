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
    python3 tools/art/anim_lab.py film FRAMES_DIR OUT.png             onion-skin of real frames (the clip recorder's PNGs)

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
        _, _, vt = np.linalg.svd(self.P - c, full_matrices=False)
        axis, thin = vt[0], vt[2]
        # the business end is the one the hold raises (every first-person hold presents its weapon head-up): a heavy
        # head sits nearer the centre than the pommel does, so which side is longer can't tell it
        if axis[1] < 0:
            axis = -axis
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


def held_frame(held):
    """The held weapon's blade frame (columns: axis, edge, face) and which side of it cuts (+1 edge, -1 the other, 0
    either: a symmetric blade picks the side that turns least)."""
    F = np.stack([held.axis, np.cross(held.axis, held.face), held.face], axis=1)
    top = held.P[(held.P - held.grip) @ held.axis > 0.6 * ((held.tip - held.grip) @ held.axis)]
    side = 0
    if len(top):
        e = (top - top.mean(axis=0)) @ F[:, 1]
        bias = float(((top - held.grip) @ F[:, 1]).mean())
        side = 0 if abs(bias) < 0.03 else (1 if bias > 0 else -1)
    return F, side


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


def cut_dir(m):
    sw = m["swing"]
    f, t = sw["from"], sw["to"]
    k = kind(m)
    u = np.array([t[0] - f[0], t[1] - f[1], 0.0])
    if k == "chop":
        u = np.array([-0.25, -1.0, 0.0])
    elif k == "rise":
        u = np.array([-0.2, 1.0, 0.0])
    elif k == "thrust":
        u = np.array([-1.0, 0.0, 0.0])
    else:
        u[1] *= 0.6  # a slash reads flatter than its old slide: the plane lies nearer the horizontal
    return u / np.linalg.norm(u)


def cut_plane(m, held):
    """The cut: its pivot, the unit blade direction at the hit, its tangent there (where the blade goes), the plane's
    normal, the target and the arm (pivot to the grip)."""
    u = cut_dir(m)
    r_c = TARGET - PIVOT
    reach = float(np.linalg.norm(r_c))
    r_c = r_c / reach
    tan_c = u - r_c * (u @ r_c)
    tan_c /= np.linalg.norm(tan_c)
    n = np.cross(r_c, tan_c)
    arm = max(0.15, reach - MID * held.length)
    return PIVOT, r_c, tan_c, n, TARGET, arm


def hit_tick(m):
    """When the blade crosses the target: the server's hit (the first active tick), or mid-blow when the blow starts
    on it (a released heavy, an armed Plunge)."""
    c, w = blow_start(m), m["windup"]
    return w if w > c + 0.25 else c + m["active"] * 0.5


def weight(style_row, m):
    """How the blow sweeps about the pivot (degrees from the hit): from where it starts, to where the blow ends, to
    where the follow-through stops. Heavier rows (a longer wind-up and blow) carry further."""
    heft = m["windup"] + m["active"]
    pre = 50 + min(24, heft * 2.5)
    post = 14 + min(8, heft)
    return pre, post, post + 8 + min(8, heft)


def rig_pose(style_row, m, held, t):
    """(d, R) at t ticks of a move in the new model."""
    F, side = held_frame(held)
    k = kind(m)
    w, a, rec = m["windup"], m["active"], m["recovery"]
    c = blow_start(m)
    total = w + a + rec
    S, r0, t0, n, C, arm = cut_plane(m, held)
    H0 = np.array([HX, HY, HZ])

    def g(p, R):  # the grip at p: the hand's move from rest that puts it there, turned by R about the hand's place
        return p - H0 - R @ held.grip, R

    rest = (np.zeros(3), np.eye(3))
    if k == "thrust":
        # a thrust drives along the aim to the point under the crosshair, the flat level
        S = C - r0 * (ARM + held.length * 0.9)
        side_v = np.cross(r0, np.array([0.0, 1.0, 0.0]))
        R = frame_to(r0, side_v, side, F)
        lunge = m["swing"].get("lunge", 0.5)
        back, out = ARM - 0.22, ARM + 0.10 + 0.22 * lunge
        if t < c:
            return lerp_pose(rest, g(S + r0 * back, R), ease_in_out_sine(t / max(1e-6, c)))
        if t < c + a:
            return g(S + r0 * (back + (out - back) * ease_out_cubic((t - c) / a)), R)
        return lerp_pose(g(S + r0 * out, R), rest, ease_in_out_sine((t - c - a) / max(1e-6, total - c - a)))
    pre, post, stop = weight(style_row, m)

    def at(theta):
        th = math.radians(theta)
        r = math.cos(th) * r0 + math.sin(th) * t0
        tan = -math.sin(th) * r0 + math.cos(th) * t0
        return g(S + r * arm, frame_to(r, tan, side, F))

    if t < c:  # drawn back: from rest to the cocked pose, slowing into the turn
        return lerp_pose(rest, at(-pre), ease_out_sine(t / max(1e-6, c)))
    if t < c + a:
        # through the target on the hit tick (the server's first active tick; mid-blow for a released heavy):
        # accelerating into it, the edge biting and slowing after
        hit = hit_tick(m)
        if t < hit:
            x = (t - c) / max(1e-6, hit - c)
            return at(-pre * (1 - x * x))
        x = (t - hit) / max(1e-6, c + a - hit)
        return at(post * (1 - (1 - x) ** 2))
    ft = max(1.0, rec * 0.35)
    if t < c + a + ft:  # the follow-through, slowing to a stop
        return at(post + (stop - post) * ease_out_cubic((t - c - a) / ft))
    return lerp_pose(at(stop), rest, ease_in_out_sine((t - c - a - ft) / max(1e-6, total - c - a - ft)))


def lerp_pose(a, b, u):
    q = slerp(mat_quat(a[1]), mat_quat(b[1]), u)
    return a[0] * (1 - u) + b[0] * u, quat_mat(q)


def bake(out=SWINGS):
    """Every style's moves as keys for the game: [t, dx, dy, dz, qx, qy, qz, qw] (the hand's move from vanilla's rest
    and its turn about its own place, for the style's own forged weapon); plus each style's turn for a vanilla item."""
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
                keys.append([round(float(tt), 3)] + [round(float(v), 4) for v in d] + [round(float(v), 5) for v in q])
            moves[mv] = {"kind": kind(m), "keys": keys}
        # a vanilla item held in this style: the same targets, from vanilla's own hold (R_vanilla = R . C)
        C = Fs @ Fv.T
        styles[sid] = {"vanilla": [round(float(v), 5) for v in mat_quat(C)],
                       "grip": [round(float(v), 4) for v in held.grip], "vanillaGrip": [round(float(v), 4) for v in grip_v],
                       "moves": moves}
    json.dump({"schemaVersion": 1, "generatedBy": "tools/art/anim_lab.py bake", "dt": KEY_DT, "styles": styles},
              open(out, "w"), separators=(",", ":"))
    return out


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
             "lead_blow": float(np.nanmean([l for (t, *_), l in zip(s, edge_lead(s, kind(m) == "thrust"))
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
    lead = edge_lead(s, kind(m) == "thrust")

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
            if kind(m) == "thrust":  # a thrust: the point leads
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
    if argv[0] == "film":
        print(film(argv[1], argv[2]))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
