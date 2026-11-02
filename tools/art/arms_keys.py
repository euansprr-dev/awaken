"""Hand-keyed moves for the Daggers and the Scythe (SPEC §25 "Hand-keyed moves"; the dagger and scythe audit, Euan
2026-11-02: "Go with all recommendations").

Every pose here is written down by hand: where the fist is (view space, blocks: x right, y up, -z ahead), the point the
blade aims at, and which way its edge faces. Each move is a short list of such keys at ticks of its clip, for both
hands, and the blow's key sits exactly on the tick the server counts the hit (the first active tick), so the point meets
the target on the frame the impact plays. Nothing is searched, scored or tuned by a script: the Swing Studio's sixteen
rounds did that and every round read worse (Euan 2026-11-02). Between keys a hand travels on a cubic through the keys,
stopping only where a key says it stops, and its turn eases the same way, so nothing holds and then snaps.

    python3 tools/art/arms_keys.py bake                   writes styles.daggers (and .scythe once keyed) into fp_swings.json
    python3 tools/art/arms_keys.py strip OUT STYLE MOVE   one move frame by frame, both hands, the crosshair marked
    python3 tools/art/arms_keys.py chain OUT STYLE MOVES  a chain as a strip (moves joined at their cadence), e.g. 1,2,3,4
    python3 tools/art/arms_keys.py check                  the rules the data alone can show (see check())

The other fourteen styles' entries in fp_swings.json are never touched: the file is read, these styles replaced, and
written back with the same separators, so every other byte stays as it was (`check` proves it against git).
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
import anim_lab as L  # noqa: E402  (the lab's maths, its mesh in the hand and its renderer)

ROOT = L.ROOT
SWINGS = L.SWINGS
H = np.array([L.HX, L.HY, L.HZ])  # vanilla's resting hand
M = np.diag([-1.0, 1.0, 1.0])  # the left hand is the right mirrored across the view's vertical plane
DT = 0.25
KEYED_BY = "tools/art/arms_keys.py"
REVIEW = {"daggers": "gatewrought_daggers", "scythe": "gatewrought_scythe"}
# where each style's hand closes, in hand space: the forged weapon's hold puts its grip there whatever the blade's length
# (fp_swings.json's grip), so a pose's place is the fist's, not the lab's estimate from the mesh
GRIP = {"daggers": np.array([0.0061, -0.0271, 0.0558]), "scythe": np.array([-0.0624, -0.2206, 0.1148])}
TAN = math.tan(math.radians(L.FOV) / 2)
ASPECT = L.W / L.H

# a local preview may read meshes another session has changed but not merged (run-autopilot/keys/overlay/assets/...)
OVERLAY = os.path.join(ROOT, "run-autopilot", "keys", "overlay")
_load = L.MR.load


def _overlay_load(wid):
    p = os.path.join(OVERLAY, "assets/awaken/arms/weapons", wid + ".mesh")
    if not os.path.exists(p):
        return _load(wid)
    tex = np.asarray(Image.open(os.path.join(OVERLAY, "assets/awaken/textures/item/arms", wid + ".png")).convert("RGBA"))
    return L.MR.MS.read(p), tex


L.MR.load = _overlay_load


def held_of(style, wid=None):
    """The style's weapon in the hand, its grip the hold's own (GRIP)."""
    h = L.Held(wid or REVIEW[style])
    h.grip = GRIP[style].copy()
    h.length = float((h.tip - h.grip) @ h.axis)
    return h


def on_screen(x, y, depth):
    """The view-space point at screen position (x, y) (-1..1 each way, y up) and depth blocks ahead."""
    return np.array([x * ASPECT * TAN * depth, y * TAN * depth, -depth])


def blade(grip, depth, tip, length, edge=(0.0, -1.0, 0.0), roll=0.0):
    """A pose from the screen: the fist at screen position grip, depth blocks ahead, and the blade's point drawn at
    screen position tip for a blade this long (its depth found along that ray)."""
    g = on_screen(grip[0], grip[1], depth)
    r = np.array([tip[0] * ASPECT * TAN, tip[1] * TAN, -1.0])
    # |s r - g| = length, the far root (a blade points away from the eye)
    a, b, c = r @ r, -2 * (r @ g), g @ g - length * length
    disc = max(0.0, b * b - 4 * a * c)
    s_ = (-b + math.sqrt(disc)) / (2 * a)
    return Pose(at=g, to=g + unit(s_ * r - g) * 3.0, edge=edge, roll=roll)


# ---- a pose: a fist at a place, the blade aimed at a point, its edge facing a way ------------------------------------

def unit(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


class Pose:
    """One hand's pose, in its own place (a left hand's x is negative): the fist's grip, the point the blade aims at,
    the way its edge faces (made square to the blade), and a roll about the blade (degrees) on top."""

    def __init__(self, at, to, edge=(0.0, -1.0, 0.0), roll=0.0):
        self.at = np.asarray(at, dtype=float)
        self.to = np.asarray(to, dtype=float)
        self.edge = np.asarray(edge, dtype=float)
        self.roll = roll

    def moved(self, at=None, to=None, edge=None, roll=None):
        return Pose(self.at if at is None else at, self.to if to is None else to, self.edge if edge is None else edge,
                    self.roll if roll is None else roll)

    def mirrored(self):
        return Pose(M @ self.at, M @ self.to, M @ self.edge, -self.roll)


def frame_of(held):
    """The held weapon's own frame at rest (columns: grip to point, edge, flat's normal)."""
    a = unit(held.axis)
    e = unit(held.edge - a * (held.edge @ a))
    return np.stack([a, e, np.cross(a, e)], axis=1)


def rotation(held, pose):
    """The hand's turn that lays the held blade from the grip toward pose.to, its edge toward pose.edge."""
    a = unit(pose.to - pose.at)
    e = pose.edge - a * (pose.edge @ a)
    if np.linalg.norm(e) < 1e-6:
        e = np.cross(a, [1.0, 0.0, 0.0])
    e = unit(e)
    if pose.roll:
        e = L.axis_rot(a, pose.roll) @ e
    T = np.stack([a, e, np.cross(a, e)], axis=1)
    return T @ frame_of(held).T


def right_handed(held, pose, left):
    """A pose as the game stores it, authored right-handed (a left hand mirrored onto the right): the move d from
    vanilla's rest and the turn R, about the hand's own place, that put the grip at pose.at."""
    p = pose.mirrored() if left else pose
    R = rotation(held, p)
    return p.at - H - R @ held.grip, R


# ---- a track: keys in time, a cubic through them ---------------------------------------------------------------------

STOP, GO = "stop", "go"


class Track:
    """A hand's keys: (tick, Pose, STOP or GO). A STOP key is reached at rest; through a GO key the hand keeps moving
    (its speed there from its neighbours). Before the first key and after the last the pose holds."""

    def __init__(self, keys):
        self.keys = sorted(keys, key=lambda k: k[0])

    def _tangent(self, i, f):
        t, p, kind = self.keys[i]
        if kind == STOP or i == 0 or i == len(self.keys) - 1:
            return np.zeros_like(f(p))
        t0, p0, _ = self.keys[i - 1]
        t1, p1, _ = self.keys[i + 1]
        return (f(p1) - f(p0)) / (t1 - t0)

    def segment(self, t):
        ks = self.keys
        if t <= ks[0][0]:
            return 0, 0.0
        for i in range(len(ks) - 1):
            if t <= ks[i + 1][0]:
                return i, (t - ks[i][0]) / (ks[i + 1][0] - ks[i][0])
        return len(ks) - 2, 1.0

    def at(self, t):
        """The grip's place and the blade's aim and edge at t (the turn eased as the place is)."""
        ks = self.keys
        if len(ks) == 1:
            return ks[0][1]
        i, u = self.segment(t)
        (t0, a, ka), (t1, b, kb) = ks[i], ks[i + 1]
        dt = t1 - t0
        h00, h10, h01, h11 = 2 * u ** 3 - 3 * u ** 2 + 1, u ** 3 - 2 * u ** 2 + u, -2 * u ** 3 + 3 * u ** 2, u ** 3 - u ** 2
        place = lambda p: p.at
        pos = h00 * a.at + h10 * dt * self._tangent(i, place) + h01 * b.at + h11 * dt * self._tangent(i + 1, place)
        # the turn's ease from the ends' kinds: from a stop it gathers, into a stop it settles
        s0 = ka == STOP or i == 0
        s1 = kb == STOP or i + 1 == len(ks) - 1
        w = (3 * u * u - 2 * u ** 3) if s0 and s1 else (u * u if s0 else (1 - (1 - u) ** 2) if s1 else u)
        # the aim is a point, so a blade kept on one spot stays on it however the fist travels
        aim = a.to + (b.to - a.to) * w
        edge = a.edge + (b.edge - a.edge) * w
        roll = a.roll + (b.roll - a.roll) * w
        return Pose(pos, aim, edge, roll)

    def end(self):
        return self.keys[-1][0]


class View:
    """The view's lean through a move: (tick, yaw, pitch, roll degrees, STOP or GO); + pitch looks down."""

    def __init__(self, keys=()):
        self.keys = sorted(keys, key=lambda k: k[0])

    def at(self, t):
        if not self.keys:
            return np.zeros(3)
        tr = Track([(k[0], Pose(np.array(k[1:4], dtype=float), np.zeros(3)), k[4]) for k in self.keys])
        return tr.at(t).at


# ---- the Daggers (decisions 3 to 7): forward grip in both hands, points in toward the crosshair ----------------------
#
# Every stab drives point first from the hip along a line to the crosshair and is pulled back; the hand not striking
# draws back and up as the other drives, so it is cocked for the next press and fires while the first is still on its
# way home (a tick of overlap at the chain's own pace, a strike every 4 ticks); the hands go home only when the presses
# stop. The keys are authored for the right hand; a left one is its mirror unless it has its own.

LD = 0.627  # the Daggers' blade from the fist to the point (Wildhide, Gatewrought, Lordscale, Sovereign; 7bbc8f78)


def dagger_poses():
    """The Daggers' poses, the right hand's (a left one is its mirror): screen places of the fist and of the point."""
    b = lambda g, d, t, e=(0.3, -1.0, 0.0), r=0.0: blade(g, d, t, LD, e, r)
    return dict(
        # forward grip, the points angled in toward the crosshair, the fists low and in from vanilla's corner
        rest=b((0.50, -1.00), 0.64, (0.14, -0.32)),
        # the free hand's guard: drawn back and up, the point in toward the crosshair but a stab's length off it on
        # screen, so the stab out of it travels (a guard by the line left the left's blow barely faster than its return)
        guard=b((0.56, -0.74), 0.46, (0.30, -0.14), (0.3, -1.0, 0.3)),
        guard_back=b((0.66, -0.76), 0.42, (0.44, -0.14), (0.3, -1.0, 0.3)),
        # strike 1's chamber at the hip, its stab home on the crosshair and a touch deeper as the freeze lets go
        hip=b((0.60, -1.22), 0.56, (0.30, -0.52), (0.3, -1.0, 0.2)),
        stab=b((0.24, -0.58), 0.80, (0.0, -0.02), (0.2, -1.0, 0.0)),
        stab_deep=b((0.22, -0.53), 0.86, (-0.01, 0.0), (0.2, -1.0, 0.0)),
        # strike 2's stab from the guard: level, straight in
        stab_high=b((0.21, -0.44), 0.80, (0.0, 0.0), (0.2, -1.0, 0.0)),
        stab_high_deep=b((0.19, -0.40), 0.86, (-0.01, 0.01), (0.2, -1.0, 0.0)),
        # pulled back out of the body, on the way home
        out=b((0.44, -0.86), 0.66, (0.16, -0.24)),
        # strike 3's slash: cocked up and out to the right, its edge leading down across the crosshair to the left
        slash_set=b((0.80, -0.60), 0.60, (0.62, -0.02), (-1.0, -0.3, 0.0)),
        slash_wound=b((0.88, -0.56), 0.57, (0.74, 0.04), (-1.0, -0.3, 0.0)),
        slash_hit=b((0.22, -0.50), 0.72, (-0.16, -0.06), (-1.0, -0.3, -0.2)),
        slash_end=b((-0.30, -0.82), 0.66, (-0.64, -0.50), (-0.7, -0.8, 0.2)),
        # strike 4: both drawn to the hips together, both points driven in at once
        pair=b((0.58, -1.30), 0.50, (0.32, -0.62), (0.3, -1.0, 0.2)),
        pair_back=b((0.62, -1.36), 0.46, (0.38, -0.70), (0.3, -1.0, 0.2)),
        pair_hit=b((0.20, -0.56), 0.82, (0.025, -0.03), (0.2, -1.0, 0.0)),
        pair_deep=b((0.18, -0.51), 0.88, (0.02, -0.02), (0.2, -1.0, 0.0)),
        pair_out=b((0.40, -0.92), 0.62, (0.16, -0.30)),
        # the riposte's stab, a lunge deeper than the chain's
        stab_lunge=b((0.20, -0.48), 0.92, (-0.01, 0.01), (0.2, -1.0, 0.0)),
        # the heavy: both coiled on the crosshair's line while it charges, so the release is the drive itself
        coil=b((0.36, -0.84), 0.60, (0.09, -0.14), (0.3, -1.0, 0.1)),
        # the dodge's slash and the slide's: wider and lower than strike 3's
        sweep_set=b((0.90, -0.84), 0.60, (0.76, -0.30), (-1.0, -0.1, 0.0)),
        sweep_hit=b((0.24, -0.64), 0.70, (-0.18, -0.22), (-1.0, -0.1, -0.2)),
        sweep_end=b((-0.40, -0.92), 0.64, (-0.78, -0.60), (-0.8, -0.6, 0.2)),
        low_set=b((0.90, -1.08), 0.58, (0.78, -0.56), (-1.0, 0.1, 0.0)),
        low_hit=b((0.22, -0.82), 0.70, (-0.22, -0.40), (-1.0, 0.2, -0.2)),
        low_end=b((-0.45, -1.00), 0.64, (-0.80, -0.70), (-0.8, 0.0, 0.2)),
        # the Art: arms crossed before the chest, then both cut out through the middle, twice
        crossed=b((-0.18, -0.70), 0.52, (-0.62, -0.10), (1.0, -0.2, 0.0)),
        crossed_tight=b((-0.24, -0.66), 0.50, (-0.72, -0.02), (1.0, -0.2, 0.0)),
        art_hit=b((0.30, -0.60), 0.70, (0.05, -0.12), (1.0, -0.3, -0.2)),
        art_open=b((0.86, -0.76), 0.62, (0.84, -0.26), (1.0, -0.5, 0.2)),
        # driven down: the Plunge armed in the fall and the Execution raised over the broken body
        high=b((0.36, -0.10), 0.55, (0.10, -0.45), (0.0, 0.3, 1.0)),
        high_back=b((0.40, 0.05), 0.50, (0.14, -0.30), (0.0, 0.3, 1.0)),
        down_hit=b((0.20, -0.62), 0.70, (0.04, -0.95), (0.0, 0.3, 1.0)),
        down_deep=b((0.19, -0.74), 0.72, (0.04, -1.10), (0.0, 0.3, 1.0)),
    )


def dagger_moves():
    P = dagger_poses()
    m = {k: v.mirrored() for k, v in P.items()}  # the left hand's
    T = Track

    def view(*keys):
        return View(keys)

    moves = {
        # strike 1, the right: from the hip point first to the crosshair; the left draws back and up into its guard,
        # cocked for strike 2; the right comes home to its own guard, cocked for the slash
        "strike_1": dict(hand="main", hit=1.0, set=0.5, swish=0.45, trail=(0.55, 1.45),
                         keys=T([(0.0, P["rest"], STOP), (0.5, P["hip"], STOP), (1.0, P["stab"], GO), (1.3, P["stab_deep"], STOP),
                                 (3.8, P["guard"], STOP), (7.0, P["guard"], STOP), (9.5, P["rest"], STOP)]),
                         other=T([(0.0, m["rest"], STOP), (1.0, m["guard_back"], GO), (2.6, m["guard"], STOP), (7.0, m["guard"], STOP),
                                  (9.5, m["rest"], STOP)]),
                         view=view((0, 0, 0, 0, STOP), (1.0, -1.0, 0.5, -0.8, GO), (1.3, -1.2, 0.7, -1.0, STOP), (3.6, 0, 0, 0, STOP))),
        # strike 2, the left: from its guard, level and straight in; the right, in its guard, draws back and up, then out
        # to the right to be cocked for the slash
        "strike_2": dict(hand="off", hit=1.0, set=0.45, swish=0.45, trail=(0.5, 1.45),
                         keys=T([(0.0, m["guard"], STOP), (0.45, m["guard_back"], STOP), (1.0, m["stab_high"], GO),
                                 (1.3, m["stab_high_deep"], STOP), (3.8, m["guard"], STOP), (7.0, m["guard"], STOP), (9.5, m["rest"], STOP)]),
                         other=T([(0.0, P["guard"], STOP), (1.0, P["guard_back"], GO), (3.0, P["slash_set"], STOP),
                                  (7.0, P["slash_set"], STOP), (9.5, P["rest"], STOP)]),
                         view=view((0, 0, 0, 0, STOP), (1.0, 1.0, 0.4, 0.8, GO), (1.3, 1.2, 0.5, 1.0, STOP), (3.6, 0, 0, 0, STOP))),
        # strike 3, the right's slash: wound up and out, down across the crosshair, through to the lower left; both
        # hands then gather at the hips for the finisher
        "strike_3": dict(hand="main", hit=1.0, set=0.4, swish=0.45, trail=(0.45, 1.5),
                         keys=T([(0.0, P["slash_set"], STOP), (0.4, P["slash_wound"], STOP), (1.0, P["slash_hit"], GO),
                                 (1.6, P["slash_end"], STOP), (2.8, P["pair_out"], GO), (3.8, P["pair"], STOP), (4.8, P["pair"], STOP),
                                 (7.5, P["rest"], STOP)]),
                         other=T([(0.0, m["guard"], STOP), (1.2, m["guard_back"], GO), (3.4, m["pair"], STOP), (4.8, m["pair"], STOP),
                                  (7.5, m["rest"], STOP)]),
                         view=view((0, 0, 0, 0, STOP), (0.4, 0.8, -0.3, 1.0, STOP), (1.0, -1.0, 0.4, -1.2, GO), (1.6, -1.8, 0.6, -2.0, STOP),
                                   (4.0, 0, 0, 0, STOP))),
        # strike 4, the finisher: both drawn back together, both points driven in at once, held deep, pulled back into
        # the guard (a press keeps the chain going from there), home only when the presses stop
        "strike_4": dict(hand="both", hit=2.0, set=1.2, swish=1.3, trail=(1.25, 2.5),
                         keys=T([(0.0, P["pair"], STOP), (1.2, P["pair_back"], STOP), (2.0, P["pair_hit"], GO), (2.4, P["pair_deep"], STOP),
                                 (4.6, P["guard"], STOP), (7.0, P["guard"], STOP), (9.5, P["rest"], STOP), (11.0, P["rest"], STOP)]),
                         other=T([(0.0, m["pair"], STOP), (1.2, m["pair_back"], STOP), (2.0, m["pair_hit"], GO), (2.4, m["pair_deep"], STOP),
                                  (4.6, m["guard"], STOP), (7.0, m["guard"], STOP), (9.5, m["rest"], STOP), (11.0, m["rest"], STOP)]),
                         view=view((0, 0, 0, 0, STOP), (1.2, 0, -0.8, 0, STOP), (2.0, 0, 1.4, 0, GO), (2.4, 0, 1.8, 0, STOP),
                                   (5.0, 0, 0, 0, STOP))),
        # strike 5 (mastery), the left's slash out of the finisher's guard: strike 3 mirrored, wound bigger
        "strike_5": dict(hand="off", hit=2.0, set=1.5, swish=1.45, trail=(1.45, 2.5),
                         keys=T([(0.0, m["guard"], STOP), (1.1, m["slash_set"], STOP), (1.5, m["slash_wound"], STOP),
                                 (2.0, m["slash_hit"], GO), (2.7, m["slash_end"], STOP), (4.4, m["guard"], STOP), (7.0, m["guard"], STOP),
                                 (9.5, m["rest"], STOP), (11.0, m["rest"], STOP)]),
                         other=T([(0.0, P["guard"], STOP), (2.2, P["guard_back"], GO), (3.6, P["guard"], STOP), (7.0, P["guard"], STOP),
                                  (9.5, P["rest"], STOP), (11.0, P["rest"], STOP)]),
                         view=view((0, 0, 0, 0, STOP), (1.5, -0.9, -0.3, -1.2, STOP), (2.0, 1.2, 0.4, 1.4, GO), (2.7, 2.2, 0.6, 2.4, STOP),
                                   (5.0, 0, 0, 0, STOP))),
        # the heavy: charged coiled on the line, the release drives both points in at once
        "heavy": dict(hand="both", hit=0.3, swish=0.0, trail=(0.0, 1.0),
                      keys=T([(0.0, P["coil"], STOP), (0.3, P["pair_hit"], GO), (0.7, P["pair_deep"], STOP), (2.2, P["pair_out"], GO),
                              (4.5, P["rest"], STOP), (10.0, P["rest"], STOP)]),
                      other=T([(0.0, m["coil"], STOP), (0.3, m["pair_hit"], GO), (0.7, m["pair_deep"], STOP), (2.2, m["pair_out"], GO),
                               (4.5, m["rest"], STOP), (10.0, m["rest"], STOP)]),
                      view=view((0, 0, -0.6, 0, STOP), (0.3, 0, 1.6, 0, GO), (0.7, 0, 2.0, 0, STOP), (3.5, 0, 0, 0, STOP))),
        # the riposte: the right's stab with a lunge, pulled back into its guard; the left thrown back in its own
        "riposte": dict(hand="main", hit=1.0, swish=0.45, trail=(0.5, 1.5),
                        keys=T([(0.0, P["rest"], STOP), (0.5, P["hip"], STOP), (1.0, P["stab_lunge"], GO), (1.5, P["stab_lunge"].moved(
                            at=P["stab_lunge"].at + [-0.01, 0.01, -0.05]), STOP), (4.0, P["guard"], STOP), (7.0, P["guard"], STOP),
                                (9.0, P["rest"], STOP), (10.5, P["rest"], STOP)]),
                        other=T([(0.0, m["rest"], STOP), (1.0, m["guard_back"], GO), (2.8, m["guard"], STOP), (7.0, m["guard"], STOP),
                                 (9.0, m["rest"], STOP), (10.5, m["rest"], STOP)]),
                        view=view((0, 0, 0, 0, STOP), (1.0, -1.2, 1.0, -1.0, GO), (1.5, -1.5, 1.4, -1.2, STOP), (4.0, 0, 0, 0, STOP))),
        # the Art: both blades crossed before the chest, cut out through the middle, crossed again and out once more
        "art": dict(hand="both", hit=2.0, swish=1.6, trail=(1.6, 2.5),
                    keys=T([(0.0, P["rest"], STOP), (1.2, P["crossed"], GO), (1.7, P["crossed_tight"], STOP), (2.0, P["art_hit"], GO),
                            (2.6, P["art_open"], STOP), (3.6, P["crossed"], GO), (4.0, P["crossed_tight"], STOP), (4.4, P["art_hit"], GO),
                            (5.0, P["art_open"], STOP), (7.5, P["out"], GO), (9.5, P["rest"], STOP), (17.0, P["rest"], STOP)]),
                    other=T([(t, p.mirrored(), k) for t, p, k in [(0.0, P["rest"], STOP), (1.2, P["crossed"], GO), (1.7, P["crossed_tight"], STOP),
                            (2.0, P["art_hit"], GO), (2.6, P["art_open"], STOP), (3.6, P["crossed"], GO), (4.0, P["crossed_tight"], STOP),
                            (4.4, P["art_hit"], GO), (5.0, P["art_open"], STOP), (7.5, P["out"], GO), (9.5, P["rest"], STOP), (17.0, P["rest"], STOP)]]),
                    view=view((0, 0, 0, 0, STOP), (1.7, 0, -1.0, 0, STOP), (2.0, 0, 0.8, 0, GO), (2.6, 0, 1.2, 0, STOP), (4.0, 0, -0.6, 0, STOP),
                              (4.4, 0, 0.8, 0, GO), (5.0, 0, 1.4, 0, STOP), (8.0, 0, 0, 0, STOP))),
        # out of a dodge: a wide slash across, lower than strike 3
        "slipcut": dict(hand="main", hit=1.0, swish=0.4, trail=(0.4, 1.5),
                        keys=T([(0.0, P["sweep_set"], STOP), (0.45, P["sweep_set"].moved(at=P["sweep_set"].at + [0.04, 0.0, 0.04]), STOP),
                                (1.0, P["sweep_hit"], GO), (1.7, P["sweep_end"], STOP), (3.4, P["out"], GO), (5.0, P["rest"], STOP), (10.0, P["rest"], STOP)]),
                        other=T([(0.0, m["guard"], STOP), (1.2, m["guard_back"], GO), (3.0, m["guard"], STOP), (5.0, m["rest"], STOP),
                                 (10.0, m["rest"], STOP)]),
                        view=view((0, 0, 0, 0, STOP), (0.45, 1.0, 0, 1.2, STOP), (1.0, -1.2, 0.4, -1.4, GO), (1.7, -2.4, 0.6, -2.6, STOP),
                                  (4.5, 0, 0, 0, STOP))),
        # out of a slide: low across the legs
        "undercut": dict(hand="main", hit=1.0, swish=0.4, trail=(0.4, 1.5),
                         keys=T([(0.0, P["low_set"], STOP), (0.45, P["low_set"].moved(at=P["low_set"].at + [0.04, -0.02, 0.04]), STOP),
                                 (1.0, P["low_hit"], GO), (1.9, P["low_end"], STOP), (3.6, P["out"], GO), (5.0, P["rest"], STOP), (11.0, P["rest"], STOP)]),
                         other=T([(0.0, m["guard"], STOP), (1.2, m["guard_back"], GO), (3.0, m["guard"], STOP), (5.0, m["rest"], STOP),
                                  (11.0, m["rest"], STOP)]),
                         view=view((0, 0, 0, 0, STOP), (0.45, 0.8, 1.0, 1.0, STOP), (1.0, -1.0, 2.0, -1.2, GO), (1.9, -2.0, 2.4, -2.0, STOP),
                                   (5.0, 0, 0, 0, STOP))),
        # armed in a fall: both raised, points down; on landing driven down into what's below
        "plunge": dict(hand="both", hit=0.3, swish=0.0, trail=(0.0, 1.0),
                       keys=T([(0.0, P["high"], STOP), (0.3, P["down_hit"], GO), (0.8, P["down_deep"], STOP), (3.0, P["out"], GO),
                               (5.5, P["rest"], STOP), (14.0, P["rest"], STOP)]),
                       other=T([(0.0, m["high"], STOP), (0.3, m["down_hit"], GO), (0.8, m["down_deep"], STOP), (3.0, m["out"], GO),
                                (5.5, m["rest"], STOP), (14.0, m["rest"], STOP)]),
                       view=view((0, 0, 0, 0, STOP), (0.3, 0, 3.0, 0, GO), (0.8, 0, 3.6, 0, STOP), (4.0, 0, 0, 0, STOP))),
        # the Execution: both raised over the broken body and driven down together, held, wrenched out
        "execution": dict(hand="both", hit=2.0, swish=1.4, trail=(1.4, 2.5),
                          keys=T([(0.0, P["rest"], STOP), (1.0, P["high"], GO), (1.5, P["high_back"], STOP), (2.0, P["down_hit"], GO),
                                  (2.6, P["down_deep"], STOP), (4.0, P["down_deep"], STOP), (5.6, P["out"], GO), (7.5, P["rest"], STOP),
                                  (19.0, P["rest"], STOP)]),
                          other=T([(0.0, m["rest"], STOP), (1.0, m["high"], GO), (1.5, m["high_back"], STOP), (2.0, m["down_hit"], GO),
                                   (2.6, m["down_deep"], STOP), (4.0, m["down_deep"], STOP), (5.6, m["out"], GO), (7.5, m["rest"], STOP),
                                   (19.0, m["rest"], STOP)]),
                          view=view((0, 0, 0, 0, STOP), (1.5, 0, -1.6, 0, STOP), (2.0, 0, 2.6, 0, GO), (2.6, 0, 3.2, 0, STOP), (4.0, 0, 3.0, 0, STOP),
                                    (6.5, 0, 0, 0, STOP))),
    }
    return moves


# ---- the lab's picture of a pose: both blades, the fists, the crosshair --------------------------------------------

def _mirror_mesh(m):
    """The mesh mirrored across x, each triangle wound the other way so it still faces the eye."""
    out = dict(m)
    order = np.arange(len(m["pos"])).reshape(-1, 3)[:, [0, 2, 1]].reshape(-1)
    for k in ("pos", "nrm", "along", "uv"):
        out[k] = np.asarray(m[k])[order]
    out["pos"] = out["pos"] * np.array([-1.0, 1.0, 1.0]) + np.array([1.0, 0.0, 0.0])  # the item's 0..1 box mirrored
    out["nrm"] = out["nrm"] * np.array([-1.0, 1.0, 1.0])
    return out


def picture(held, right, left, size=(960, 540), hand_grip=(-0.02, 0.07, -0.02)):
    """Both hands' blades as the game draws them at these poses (right: d, R; left: d, R right-handed, mirrored here)."""
    W, H_ = size
    imgs = []
    for side, (d, R) in (("r", right), ("l", left)):
        if d is None:
            continue
        P = held.P @ R.T + H + d
        N = held.N @ R.T
        m = held.m
        if side == "l":
            P = P @ M.T
            N = N @ M.T
            order = np.arange(len(P)).reshape(-1, 3)[:, [0, 2, 1]].reshape(-1)
            P, N = P[order], N[order]
            m = dict(held.m)
            for k in ("nrm", "along", "uv"):
                m[k] = np.asarray(held.m[k])[order]
        behind = (P[:, 2] > L.NEAR).reshape(-1, 3).any(axis=1).repeat(3)
        P = np.where(behind[:, None], np.array([0.0, 0.0, -1000.0]), P)
        sx, sy = L.project(P)
        imgs.append(L.MR._raster(m, held.tex, {"sx": sx, "sy": sy, "sz": P[:, 2], "nv": N}, (W, H_), L.MR.BG, time=0.27))
    base = np.asarray(imgs[0]).astype(int) if imgs else np.zeros((H_, W, 3), int)
    for im in imgs[1:]:
        a = np.asarray(im).astype(int)
        mask = (np.abs(a - L.MR.BG).sum(axis=2) > 6)[..., None]
        base = np.where(mask, a, base)
    im = Image.fromarray(base.astype(np.uint8), "RGB")
    dr = ImageDraw.Draw(im)
    # the fists (where the arm closes on each hilt) and the forearm toward the frame's lower corner
    for side, (d, R) in (("r", right), ("l", left)):
        if d is None:
            continue
        g = R @ np.asarray(hand_grip) + H + d
        if side == "l":
            g = M @ g
        elbow = g + np.array([0.22 * (1 if side == "r" else -1), -0.32, 0.30])
        pts = np.stack([g, elbow])
        if (pts[:, 2] < -0.05).all():
            sx, sy = L.project(pts)
            dr.line([(sx[0], sy[0]), (sx[1], sy[1])], fill=(150, 110, 90), width=34)
            dr.ellipse([sx[0] - 22, sy[0] - 22, sx[0] + 22, sy[0] + 22], fill=(170, 128, 104))
    dr.line([(W / 2 - 10, H_ / 2), (W / 2 + 10, H_ / 2)], fill=(255, 255, 255), width=2)
    dr.line([(W / 2, H_ / 2 - 10), (W / 2, H_ / 2 + 10)], fill=(255, 255, 255), width=2)
    return im


def tip_ndc(held, d, R, left=False):
    tip = R @ held.tip + H + d
    if left:
        tip = M @ tip
    f = 1.0 / math.tan(math.radians(L.FOV) / 2)
    return np.array([tip[0] / -tip[2] * f / (L.W / L.H), tip[1] / -tip[2] * f])


# ---- sampling a move into the game's keys --------------------------------------------------------------------------

def sample_move(held, mv, t):
    """Both hands at t of a move, right-handed as stored: (right d, R), (left d, R)."""
    main_left = mv["hand"] == "off"
    strike = mv["keys"].at(t)
    other = mv["other"].at(t)
    if mv["hand"] == "off":
        left, right = strike, other
    else:
        right, left = strike, other
    return right_handed(held, right, False), right_handed(held, left, True)


def length(mv):
    return max(mv["keys"].end(), mv["other"].end())


CW, CH = 400, 225


def strip(out, style, move, n=None, step=0.25, until=None):
    held = held_of(style)
    mv = MOVES[style]()[move]
    end = until or length(mv)
    ts = [round(x, 2) for x in np.arange(0, end + 1e-6, step)]
    cells = []
    for t in ts:
        r, l = sample_move(held, mv, t)
        im = picture(held, r, l).resize((CW, CH), Image.LANCZOS)
        dr = ImageDraw.Draw(im)
        hit = abs(t - mv["hit"]) < 1e-6
        dr.text((6, 4), f"t {t:.2f}" + ("  HIT" if hit else ""), fill=(255, 120, 120) if hit else (200, 205, 215))
        cells.append(im)
    cols = 6
    sheet = Image.new("RGB", (CW * cols, ((len(cells) + cols - 1) // cols) * CH + 18), (14, 15, 20))
    ImageDraw.Draw(sheet).text((6, 3), f"{style} {move} (hand-keyed) every {step} ticks", fill=(230, 232, 240))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % cols) * CW, 18 + (i // cols) * CH))
    sheet.save(out)
    return out


def angle(Ra, Rb):
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(Ra.T @ Rb) - 1) / 2))))


def chain(out, style, names, gap=4.0, step=0.5):
    """A chain as play strings it: each move begun `gap` ticks after the last (the chain's own pace), the last let run
    out. Prints each hand's jump where one move hands over to the next (what SwingBlend has to carry: near nothing)."""
    held = held_of(style)
    moves = MOVES[style]()
    seq = [moves[n] for n in names]
    starts = [i * gap for i in range(len(seq))]
    end = starts[-1] + length(seq[-1])
    for i in range(1, len(seq)):
        a = sample_move(held, seq[i - 1], gap)
        b = sample_move(held, seq[i], 0.0)
        print(f"  {names[i - 1]} -> {names[i]}: right {np.linalg.norm(a[0][0] - b[0][0]):.3f} blocks {angle(a[0][1], b[0][1]):.0f} deg,"
              f" left {np.linalg.norm(a[1][0] - b[1][0]):.3f} blocks {angle(a[1][1], b[1][1]):.0f} deg")
    cells = []
    for t in np.arange(0, end + 1e-6, step):
        i = max(k for k in range(len(seq)) if starts[k] <= t + 1e-9)
        lt = t - starts[i]
        r, l = sample_move(held, seq[i], lt)
        im = picture(held, r, l).resize((CW, CH), Image.LANCZOS)
        hit = abs(lt - seq[i]["hit"]) < 1e-6
        ImageDraw.Draw(im).text((6, 4), f"{t:.1f} {names[i]} {lt:.1f}" + ("  HIT" if hit else ""),
                                fill=(255, 120, 120) if hit else (200, 205, 215))
        cells.append(im)
    cols = 6
    sheet = Image.new("RGB", (CW * cols, ((len(cells) + cols - 1) // cols) * CH), (14, 15, 20))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % cols) * CW, (i // cols) * CH))
    sheet.save(out)
    return out


MOVES = {"daggers": dagger_moves}

# ---- third person from the same keys (decision 7): each arm follows its own blade ---------------------------------
#
# The fist keeps its way from the eye (the same place on the screen: a stab's fist in toward the crosshair, a guard's
# out to the side and low) and its depth is carried onto the arm's reach: a fist at the chest at 0.45 blocks, the arm
# straight at a stab's 0.8 (first person sets its hands far out and high to show them; from outside, a guard that far
# out reads as a stab and both read as hands by the face). The arm is solved onto it (arms_rig4's IK, elbows bent or
# straight), and the blade's way in the view becomes the weapon's way in the model: the wrist (ArmsWrist) turns it the
# rest of the way in the fist, by the shortest turn that lays the blade on its line (a dagger's two edges and two flats
# read alike from outside, so its roll about its own length is left as the hold has it). The chest turns with the hand that drives (its shoulder comes forward) and the base
# clip keeps the move's legs, lean and step. Outside the blow no wrist turns faster than WRIST_STEP a quarter tick.

KEYS3 = os.path.join(HERE, "specs", "arms_keys3.json")
NEAR, FAR = 0.45, 0.80  # a fist's depth in the view at the chest and at an arm's length
CLOSE, OUT = 2.0, 8.2  # px before the shoulders there
DROP = 0.3  # px below the shoulder a degree the fist is seen under the eye, past 15
CHEST = 50.0  # degrees of chest turn a block of one fist ahead of the other
CHEST_MAX = 25.0
WRIST_STEP = 20.0  # degrees a quarter tick, at most, outside the blow (Euan's yes to decision 7)
FLAT = np.diag([1.0, 1.0, -1.0])  # the item's flat turned over: the left's mirrored blade as a turn


def _arc(a, b):
    """The shortest turn taking unit a onto unit b."""
    a, b = unit(a), unit(b)
    c = float(a @ b)
    v = np.cross(a, b)
    if np.linalg.norm(v) < 1e-9:
        return np.eye(3) if c > 0 else L.axis_rot(unit(np.cross(a, [0.0, 0.0, 1.0])), 180.0)
    return L.axis_rot(unit(v), math.degrees(math.atan2(np.linalg.norm(v), c)))


def _fist3(at):
    """Where a fist seen at view place {@code at} is held from outside (the unposed figure, px: right at -x, y down,
    -z ahead): the same bearing and fall from the eye, its depth onto the arm's reach."""
    import arms_rig4 as R4
    depth = -at[2]
    fwd = CLOSE + (OUT - CLOSE) * float(np.clip((depth - NEAR) / (FAR - NEAR), 0.0, 1.15))
    fall = math.degrees(math.atan2(-at[1], depth))
    return np.array([-fwd * at[0] / depth, R4.SHOULDER[1] + DROP * (fall - 15.0), -fwd])


def _weight(name, mv, t):
    """How much of the move third person plays from the keys (0: the base clip's own arm, no wrist): in over the
    wind-up from rest, at once for a strike that chains from the one before (its hands are already there) or a move
    loosed from a draw, out over the last pair of keys (a move that holds its guard then holds at rest before that,
    so the fade never mixes a guard with the base clip's recovery: strike 5's left arm went into the chest)."""
    keys = mv["keys"].keys
    chained = name in ("strike_2", "strike_3", "strike_4", "strike_5") or mv["hit"] < 0.5
    rise = max(0.25, min(0.6, mv["hit"] * 0.6))
    w_in = 1.0 if chained else (3 * min(1.0, t / rise) ** 2 - 2 * min(1.0, t / rise) ** 3)
    t0, t1 = keys[-2][0], keys[-1][0]
    u = min(1.0, max(0.0, (t - t0) / max(1e-6, t1 - t0)))
    return w_in * (1.0 - (3 * u * u - 2 * u ** 3))


def _limit(Ws, ts, hit, step=WRIST_STEP):
    """The wrist's keys with no turn faster than step a key outside the blow (the blow's own keys kept exact): limited
    forward and back, then the two met halfway, so a fast turn is spread both ways rather than lagged. The first and
    last keys are kept too: a move starts where the one before left the wrist (square from rest) and ends square."""
    n = len(Ws)

    def run(order):
        out = list(Ws)
        for a, b in zip(order, order[1:]):
            if abs(ts[b] - hit) <= 0.5 or b in (0, n - 1):
                continue
            ang = angle(out[a], out[b])
            if ang > step:
                q = L.slerp(L.mat_quat(out[a]), L.mat_quat(out[b]), step / ang)
                out[b] = L.quat_mat(q)
        return out
    fw, bw = run(list(range(n))), run(list(range(n - 1, -1, -1)))
    Ws = [L.quat_mat(L.slerp(L.mat_quat(f), L.mat_quat(b), 0.5)) for f, b in zip(fw, bw)]
    return run(list(range(n)))  # the halfway meeting can still step past the limit: held to it once more


def third(style="daggers"):
    """Each move's third person: {move: {"bent": rec, "straight": rec}} in arms_rig4's record (the arms' terms, the chest's
    twist, the right wrist) with the left wrist too ("wrist_left"), and the check numbers."""
    import arms_rig4 as R4
    import arms_moves as A
    import arms_holds as AH
    import power_casts as PC
    held = held_of(style)
    wid = REVIEW[style]
    Rh = L.MR.euler(*L.MR.held(wid)[0])
    g3 = AH.grip3(style)
    holds = {arm: R4.hold_of(L.display_3p(wid, arm), arm, g3) for arm in ("right", "left")}
    cloud, _ = R4.weapon_cloud(style)
    sc3 = L.display_3p(wid, "right")[2]
    rel = (cloud - g3) * sc3 * 16
    out = {}
    for name, mv in MOVES[style]().items():
        own = f"arms_{style}_{name}"
        base = own if own in A.BASE else f"arms_{name}"
        ts = np.arange(0.0, length(mv) + 1e-6, DT)
        rec_by = {}
        for bent in (True, False):
            clip = A.BASE[base + ("_bent" if bent else "")]
            prev = {arm: R4.terms_of(clip.sample(0.0), arm, bent) for arm in ("right", "left")}
            rec = {"right": [], "left": [], "twist": [], "wrist": [], "wrist_left": [], "miss": 0.0, "depth": 0.0}
            Wr, Wl = [], []
            for t in ts:
                t = float(t)
                b0 = clip.sample(t)
                w = _weight(name, mv, t)
                strike, other = mv["keys"].at(t), mv["other"].at(t)
                pr, pl = (other, strike) if mv["hand"] == "off" else (strike, other)
                (_, Rr), (_, Rl) = sample_move(held, mv, t)
                tw = float(np.clip(-CHEST * (pl.at[2] - pr.at[2]), -CHEST_MAX, CHEST_MAX))
                tw = (1 - w) * R4.twist_of(b0) + w * tw
                b = R4.with_twist(b0, tw)
                fr = PC.part_frames(b)
                for arm, pose, Rv in (("right", pr, Rr @ Rh), ("left", pl, M @ Rl @ Rh @ FLAT)):
                    turn, local = holds[arm]
                    am = R4.B @ Rv @ AH.AXIS_ITEM  # the blade's line in the model
                    target = R4.to_world(b, _fist3(pose.at))
                    roll = lambda Mx, turn=turn, am=am: 0.02 * math.degrees(math.acos(float(np.clip((Mx @ turn @ AH.AXIS_ITEM) @ am, -1, 1))))  # noqa: E731
                    v, miss = R4.ik_arm(b, arm, bent, target, local, prev[arm], roll)
                    prev[arm] = v
                    vb = (1 - w) * R4.terms_of(b0, arm, bent) + w * v
                    b[arm + "_arm"] = R4.arm_bones(*vb, tw, arm, bent)
                    Mx, Tx, _ = R4.limb_frame(b, arm, bent)
                    W = _arc(AH.AXIS_ITEM, (Mx @ turn).T @ am)
                    W = L.quat_mat(L.slerp(np.array([0.0, 0.0, 0.0, 1.0]), L.mat_quat(W), w))
                    (Wr if arm == "right" else Wl).append(W)
                    rec[arm].append([round(float(x), 2) for x in vb])
                    if w > 0.999:
                        rec["miss"] = max(rec["miss"], round(miss, 2))
                rec["twist"].append(round(tw, 2))
            Wr, Wl = _limit(Wr, ts, mv["hit"]), _limit(Wl, ts, mv["hit"])
            for i, t in enumerate(ts):  # the check: each blade as the game will draw it, into the body
                b = R4.rigged_bones(clip, rec, i, bent)
                frs = [PC.part_frames(b, p) for p in (0.0, 30.0)]
                for arm, W in (("right", Wr[i]), ("left", Wl[i])):
                    turn, local = holds[arm]
                    Mx, Tx, _ = R4.limb_frame(b, arm, bent)
                    G = Tx + Mx @ local
                    P = G + rel @ (Mx @ turn @ W).T
                    rec["depth"] = max(rec["depth"], round(R4.body_depth(frs, P, G), 2))
            rec["wrist"] = [[round(float(x), 4) for x in _quat(W, None)] for W in Wr]
            rec["wrist_left"] = [[round(float(x), 4) for x in _quat(W, None)] for W in Wl]
            rec_by["bent" if bent else "straight"] = rec
        out[name] = rec_by
    return out


def wrist_steps(rec, mv):
    """The wrist's fastest turn a key outside the blow and inside it (degrees), both arms."""
    worst_out = worst_in = 0.0
    for key in ("wrist", "wrist_left"):
        ks = rec[key]
        for i in range(1, len(ks)):
            a = angle(L.quat_mat(np.array(ks[i - 1])), L.quat_mat(np.array(ks[i])))
            if abs(i * DT - mv["hit"]) <= 0.5:
                worst_in = max(worst_in, a)
            else:
                worst_out = max(worst_out, a)
    return worst_out, worst_in


def third_write(style="daggers"):
    data = json.load(open(KEYS3)) if os.path.exists(KEYS3) else {"generatedBy": "tools/art/arms_keys.py third", "dt": DT, "styles": {}}
    data["styles"][style] = third(style)
    with open(KEYS3, "w") as f:
        f.write(json.dumps(data, separators=(",", ":")))
    moves = MOVES[style]()
    for name, r in data["styles"][style].items():
        o, i = wrist_steps(r["bent"], moves[name])
        print(f"  {name:10s} wrist {o:5.1f} deg a key outside the blow, {i:5.1f} in it; blade into the body "
              f"{r['bent']['depth']:.1f} px bent, {r['straight']['depth']:.1f} straight; hand off its spot {r['bent']['miss']:.1f} px")
    return KEYS3


def sheet3(out, style, name, cw=260):
    """A move in third person from behind-right and the side (bent), both blades, every half tick, beside first person."""
    import arms_rig4 as R4
    import arms_moves as A
    import power_casts as PC
    data = json.load(open(KEYS3))["styles"][style][name]
    mv = MOVES[style]()[name]
    wid = REVIEW[style]
    own = f"arms_{style}_{name}"
    base = own if own in A.BASE else f"arms_{name}"
    _, bent = R4.make_clips(own, A.BASE[base], A.BASE[base + "_bent"], data)
    held = held_of(style)
    ts = [round(x, 2) for x in np.arange(0, length(mv) + 1e-6, 0.5)]
    rows = []
    row = []
    for t in ts:
        r, l = sample_move(held, mv, t)
        im = picture(held, r, l).resize((cw, int(cw * 9 / 16)), Image.LANCZOS)
        ImageDraw.Draw(im).text((4, 3), f"t {t:.1f}" + (" HIT" if abs(t - mv["hit"]) < 0.26 else ""), fill=(255, 200, 200))
        row.append(im)
    rows.append(row)
    wr, wl = data["bent"]["wrist"], data["bent"]["wrist_left"]
    for label, yaw, pitch in R4.VIEWS:
        b0 = PC.strip_bounds([bent], yaw, pitch)
        bounds = (b0[0] - 14, b0[1] + 14, b0[2] - 16, b0[3] + 6)
        row = []
        for t in ts:
            im = L.figure_3p(bent, t, [(wid, "right", L.wrist_now(wr, t)), (wid, "left", L.wrist_now(wl, t))], yaw, pitch, 4.0,
                             bounds, lower=True)
            im.thumbnail((cw, cw * 2))
            row.append(im)
        rows.append(row)
    Hs = [max(i.height for i in r) for r in rows]
    img = Image.new("RGB", (cw * len(ts), sum(Hs) + 20), (24, 24, 28))
    ImageDraw.Draw(img).text((6, 4), f"{style} {name}: first person, third person behind-right and side (bent)", fill=(230, 230, 230))
    y = 20
    for r, h in zip(rows, Hs):
        for i, im in enumerate(r):
            img.paste(im, (i * cw, y))
        y += h
    img.save(out)
    return out


# ---- into the game's file ------------------------------------------------------------------------------------------


def _quat(R, prev):
    """R as [x, y, z, w], on the same side of the sphere as the key before it (a clean slerp between keys)."""
    q = L.mat_quat(R)
    if (prev is None and q[3] < 0) or (prev is not None and q @ prev < 0):
        q = -q
    return q


def _rows(held, mv):
    """A move's keys (the striking hand: the main's, or the left's for an off-hand strike) and idle (the other hand),
    right-handed as the game stores them, every DT ticks to the move's last key."""
    keys, idle, qk, qi = [], [], None, None
    for i in range(int(round(length(mv) / DT)) + 1):
        t = i * DT
        right, left = sample_move(held, mv, t)
        strike, other = (left, right) if mv["hand"] == "off" else (right, left)
        qk, qi = _quat(strike[1], qk), _quat(other[1], qi)
        cam = mv["view"].at(t)
        keys.append([round(t, 3)] + [round(float(v), 4) for v in strike[0]] + [round(float(v), 5) for v in qk]
                    + [round(float(v), 3) for v in cam])
        idle.append([round(t, 3)] + [round(float(v), 4) for v in other[0]] + [round(float(v), 5) for v in qi])
    return keys, idle


def stance_of(style):
    d, R = right_handed(held_of(style), dagger_poses()["rest"], False)
    return [round(float(v), 4) for v in d] + [round(float(v), 5) for v in _quat(R, None)]


NOD = 0.6  # a stab's kick: the view nodded in a touch, no turn (the kick's scale is the freeze's, ArmsPredict.impact)


def kick_of(held, mv, form):
    """The view's kick on a hit [yaw, pitch] (decision 6): a stab punches the view in a touch (a nod, no turn: its point
    drives into the screen, and the old row kicked a right stab sideways); a cut kicks along the way its points travel
    on screen into the hit (+yaw to the right, +pitch down); a pair cutting out both ways at once nods like a stab."""
    if form == "stab":
        return [0.0, NOD]
    hit = mv["hit"]
    t0 = max(0.0, hit - 0.25)
    hands = [("keys", mv["hand"] == "off")] + ([("other", True)] if mv["hand"] == "both" else [])
    total, size = np.zeros(2), 0.0
    for track, left in hands:
        a, b = (tip_ndc(held, *right_handed(held, mv[track].at(t), left), left=left) for t in (t0, hit))
        total += b - a
        size += float(np.linalg.norm(b - a))
    if size < 1e-6 or np.linalg.norm(total) < 0.25 * size:
        return [0.0, NOD]
    u = total / np.linalg.norm(total)
    return [round(float(u[0]), 3), round(float(-u[1]), 3)]


def baked(data, style):
    """The style's entry with its hand-keyed moves: every other field as it was (third person's wrists until its own
    keys), the stance the hands rest in between moves, and "by" so the lab's bake keeps it."""
    old = data["styles"][style]
    held = held_of(style)
    keyed3 = json.load(open(KEYS3))["styles"].get(style, {}) if os.path.exists(KEYS3) else {}
    moves = {}
    for name, mv in MOVES[style]().items():
        prev = old["moves"].get(name, {})
        keys, idle = _rows(held, mv)
        row = {"form": prev.get("form", "stab"), "weight": prev.get("weight", "snap"), "hand": mv["hand"], "keys": keys,
               "bite": [1.0, 0.0], "idle": idle, "swish": mv["swish"]}
        if "set" in mv:
            row["set"] = mv["set"]
        row["trail"] = list(mv["trail"])
        row["kick"] = kick_of(held, mv, row["form"])
        if "swishSound" in prev:
            row["swishSound"] = prev["swishSound"]
        rig = keyed3.get(name)
        if rig:  # third person's wrists, each arm's, from the same keys (third())
            for key, variant in (("wrist3", "bent"), ("wrist3s", "straight")):
                arms = {arm: rig[variant][k] for arm, k in (("right", "wrist"), ("left", "wrist_left"))
                        if any(abs(q[3]) < 0.99995 for q in rig[variant][k])}
                if arms:
                    row[key] = arms
        moves[name] = row
    new = dict(old)
    new["moves"] = moves
    new["stance"] = stance_of(style)
    new["by"] = KEYED_BY
    return new


def bake(styles=("daggers",)):
    """Splices the keyed styles into fp_swings.json; every other byte as it was (schema 7: a style's stance)."""
    raw = open(SWINGS).read()
    data = json.loads(raw)
    if json.dumps(data, separators=(",", ":")) != raw:
        raise SystemExit("fp_swings.json is not in the lab's compact form: nothing written")
    data["schemaVersion"] = max(7, data["schemaVersion"])
    for style in styles:
        data["styles"][style] = baked(data, style)
    with open(SWINGS, "w") as f:
        f.write(json.dumps(data, separators=(",", ":")))
    return SWINGS


def _at(rows, t, n=7):
    """A baked track at t as the game reads it: linear place, slerped turn."""
    ts = [r[0] for r in rows]
    if t <= ts[0]:
        r = rows[0]
        return np.array(r[1:4]), L.quat_mat(np.array(r[4:8]))
    if t >= ts[-1]:
        r = rows[-1]
        return np.array(r[1:4]), L.quat_mat(np.array(r[4:8]))
    i = max(j for j in range(len(ts)) if ts[j] <= t)
    a, b = rows[i], rows[i + 1]
    u = (t - a[0]) / (b[0] - a[0])
    return np.array(a[1:4]) * (1 - u) + np.array(b[1:4]) * u, L.quat_mat(L.slerp(np.array(a[4:8]), np.array(b[4:8]), u))


def check(styles=("daggers",)):
    """The rules the data alone can show, on the baked file (the GameTests hold the game to the same): the other styles
    byte for byte as git has them; each blow keyed on the tick the server counts its hit; each stab's point on the
    crosshair there; every move home at the stance by its end, or held still from the chain's earliest press when the
    other hand throws the next; each chain strike starting where its hand is; the ribbon over the stroke alone."""
    import subprocess
    data = json.loads(open(SWINGS).read())
    head = json.loads(subprocess.run(["git", "-C", ROOT, "show", "HEAD:" + os.path.relpath(SWINGS, ROOT)],
                                     capture_output=True, text=True, check=True).stdout)
    bad = []
    for k in data:
        if k not in ("styles", "schemaVersion") and data[k] != head[k]:
            bad.append(f"top-level {k} changed")
    others = [s for s in data["styles"] if s not in styles]
    for s in others:
        if json.dumps(data["styles"][s], separators=(",", ":")) != json.dumps(head["styles"].get(s), separators=(",", ":")):
            bad.append(f"{s} changed")
    print(f"other styles untouched: {len(others) - sum(1 for b in bad if b.endswith(' changed'))} of {len(others)}")
    tab = L.table()
    for style in styles:
        st, row = data["styles"][style], tab[style]
        held = held_of(style)
        stance = np.array(st["stance"])
        sd, sR = stance[:3], L.quat_mat(stance[3:])
        chain = row["chain"] + row.get("masteryChain", [])
        keyed = MOVES[style]()
        for name, mv in keyed.items():
            m, clip = row["moves"][name], st["moves"][name]
            w, a = m["windup"], m["active"]
            total = w + a + m["recovery"]
            hit = mv["hit"]
            if w > 0 and abs(hit - w) > 1e-6:
                bad.append(f"{name}: blow keyed at {hit}, the server's hit at {w}")
            if w == 0 and hit > 0.5:
                bad.append(f"{name}: a loosed blow lands at {hit}, more than half a tick in")
            d, R = _at(clip["keys"], hit)
            if clip["form"] == "stab" and name not in ("plunge", "execution"):
                tip = tip_ndc(held, d, R, left=mv["hand"] == "off")
                if math.hypot(*tip) > 0.15:
                    bad.append(f"{name}: the point {tip.round(3)} misses the crosshair on the hit")
            i = chain.index(name) if name in chain else -1
            nxt = chain[i + 1] if 0 <= i < len(chain) - 1 else None
            de, Re = _at(clip["keys"], total + 1)
            waits = nxt is not None and "cadence" in m and mv["hand"] != "both" and keyed[nxt]["hand"] != "both" \
                and keyed[nxt]["hand"] != mv["hand"]
            if waits:
                dw, Rw = _at(clip["keys"], m["cadence"][0])
                if np.linalg.norm(dw - de) > 1e-3 or angle(Rw, Re) > 0.5:
                    bad.append(f"{name}: not still from the earliest press ({m['cadence'][0]}) to its end")
            elif np.linalg.norm(de - sd) > 1e-3 or angle(Re, sR) > 0.5:
                bad.append(f"{name}: ends {np.linalg.norm(de - sd):.3f} from the stance")
            hit_rule = w if w > 0 else a * 0.5
            t0, t1 = clip["trail"]
            if not (t0 <= hit_rule <= t1 <= hit_rule + 0.5):
                bad.append(f"{name}: ribbon {t0}..{t1} is not the stroke alone (hit {hit_rule})")
            if not 0 <= clip["swish"] <= w + max(a, 1) + 0.01:
                bad.append(f"{name}: swish at {clip['swish']}")
            if name.startswith("strike_") and not 0 <= clip.get("set", -1) <= w:
                bad.append(f"{name}: set {clip.get('set')} outside its wind-up")
        for i in range(len(chain) - 1):  # SwingTests.chainFlow's links
            prev, nxt = chain[i], chain[i + 1]
            m = row["moves"][prev]
            press = m["cadence"][0] if "cadence" in m else m["windup"] + m["active"] + 2  # ArmsRules.MASTERY_LINK
            if i == 0:
                press = max(press - 1, m["windup"] + m["active"])
            a_, b_ = st["moves"][prev], st["moves"][nxt]
            same = (keyed[prev]["hand"] == "off") == (keyed[nxt]["hand"] == "off")
            da, Ra = _at(a_["keys"] if same else a_["idle"], press)
            db, Rb = _at(b_["keys"], 0)
            if np.linalg.norm(da - db) >= 0.02 or angle(Ra, Rb) >= 3:
                bad.append(f"{nxt} doesn't start where its hand is after {prev} ({np.linalg.norm(da - db):.3f}, {angle(Ra, Rb):.1f} deg)")
            if keyed[nxt]["hand"] == "both":
                da, Ra = _at(a_["keys"] if keyed[prev]["hand"] == "off" else a_["idle"], press)
                db, Rb = _at(b_["idle"], 0)
                if np.linalg.norm(da - db) >= 0.02 or angle(Ra, Rb) >= 3:
                    bad.append(f"{nxt}'s off hand doesn't start where it waited after {prev}")
        for name, mv in keyed.items():  # third person's wrists: never faster than WRIST_STEP a key outside the blow
            clip = st["moves"][name]
            for key in ("wrist3", "wrist3s"):
                for arm, ks in clip.get(key, {}).items():
                    for i in range(1, len(ks)):
                        a_ = angle(L.quat_mat(np.array(ks[i - 1])), L.quat_mat(np.array(ks[i])))
                        if abs(i * DT - mv["hit"]) > 0.5 and a_ > WRIST_STEP + 0.5:
                            bad.append(f"{name} {key} {arm}: the wrist turns {a_:.0f} deg in a key at {i * DT}")
                    if abs(ks[-1][3]) < 0.9999:
                        bad.append(f"{name} {key} {arm}: the wrist isn't square at the end")
                    # from the hand's rest unless loosed from a draw or carried on from the chain strike before (arms_fp_swings)
                    if row["moves"][name]["windup"] > 0 and (chain.index(name) if name in chain else -1) <= 0 and abs(ks[0][3]) < 0.9999:
                        bad.append(f"{name} {key} {arm}: the wrist isn't square at the start")
        d, R = _at(st["moves"]["strike_1"]["keys"], row["moves"]["strike_1"]["windup"])
        if angle(R, np.eye(3)) <= 15:
            bad.append("strike_1 isn't turned into its blow on its hit")
    for b in bad:
        print("  FAIL", b)
    print("check:", "pass" if not bad else f"{len(bad)} failing")
    return not bad


def main(argv):
    if not argv:
        raise SystemExit(__doc__)
    if argv[0] == "strip":
        print(strip(argv[1], argv[2], argv[3], until=float(argv[4]) if len(argv) > 4 else None))
        return 0
    if argv[0] == "chain":
        print(chain(argv[1], argv[2], argv[3].split(","), float(argv[4]) if len(argv) > 4 else 4.0))
        return 0
    if argv[0] == "bake":
        print(bake())
        return 0
    if argv[0] == "check":
        return 0 if check() else 1
    if argv[0] == "third":
        print(third_write())
        return 0
    if argv[0] == "sheet3":  # sheet3 OUT MOVE
        print(sheet3(argv[1], "daggers", argv[2]))
        return 0
    if argv[0] == "poses":  # a still of each named pose: poses OUT
        held = held_of("daggers")
        P = dagger_poses()
        name = argv[2] if len(argv) > 2 else "rest"
        r = right_handed(held, P[name], False)
        l = right_handed(held, P[name].mirrored(), True)
        picture(held, r, l).save(argv[1])
        print(argv[1], "tips", tip_ndc(held, *r), tip_ndc(held, *l, left=True))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
