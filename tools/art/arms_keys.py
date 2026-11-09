"""Hand-keyed moves for the Daggers and the Scythe (SPEC §25 "Hand-keyed moves"; the dagger and scythe audit, Euan
2026-11-02: "Go with all recommendations").

Every pose here is written down by hand: where the fist is (view space, blocks: x right, y up, -z ahead), the point the
blade aims at, and which way its edge faces. Each move is a short list of such keys at ticks of its clip, for both
hands, and the blow's key sits exactly on the tick the server counts the hit (the first active tick), so the point meets
the target on the frame the impact plays. Nothing is searched, scored or tuned by a script: the Swing Studio's sixteen
rounds did that and every round read worse (Euan 2026-11-02). Between keys a hand travels on a cubic through the keys,
stopping only where a key says it stops, and its turn eases the same way, so nothing holds and then snaps.

    python3 tools/art/arms_keys.py bake                   writes styles.daggers and styles.scythe into fp_swings.json
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


def design_item(p):
    """A point of a weapon's design (texels, tools/art/specs/arms_weapons.py: to_item) in item space."""
    a = math.radians(-45.0)  # arms_weapons.ANGLE: a design's y lies along the item's diagonal
    q = np.array([math.cos(a) * p[0] - math.sin(a) * p[1], math.sin(a) * p[0] + math.cos(a) * p[1], p[2]]) + np.array([3.0, 3.0, 8.0])
    return q / 16.0


def design_point(wid, p):
    """A point of a weapon's design in hand space (to_item, then the item's first-person hold)."""
    (ax, ay, az), t, sc = L.MR.held(wid)
    return (design_item(p) - 0.5) * sc @ L.MR.euler(ax, ay, az).T + np.asarray(t) / 16.0


# a two-handed style's hands on its design: the main fist (the grip's top, where the hand closes: the style's handGrip)
# and the second hand's (the scythe's peg, weapon-designs 7e8c2c368: its grip box x 3..5, y 5..8)
TWO_HANDS = {"scythe": ((0.0, 0.0, 0.0), (4.0, 6.5, 0.0))}


def held_of(style, wid=None):
    """The style's weapon in the hand, its grip the hold's own (GRIP); a two-handed one's grip where the main hand
    closes, with its second hand's place (nib) and its blade's point (the design's furthest from the haft)."""
    w = wid or REVIEW[style]
    h = L.Held(w)
    h.grip = GRIP[style].copy()
    h.nib = None
    if style in TWO_HANDS:
        main, second = TWO_HANDS[style]
        h.grip, h.nib = design_point(w, main), design_point(w, second)
        e = unit(h.edge)
        h.tip = h.P[np.argmin((h.P - h.grip) @ e)]  # the blade's point, out to the side off the snath's top
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


# ---- the Daggers (Euan 2026-11-09: "barely even hitting", "add the slash back in", "polished up with everything we've learned") -
#
# Right stab, left stab, right slash, both stabs, left slash (mastery). A stab drives the fist forward and in, its point
# travelling up the screen toward the crosshair, because a point that stays put on the screen reads as nothing moving however
# far it goes. Every blow is one stroke: from a stop, through the hit, to one stop deeper, then a slower way back; nothing
# chambers first, because a chamber, a blow and a follow-through inside 1.3 ticks was the jitter. The fist stays low and on its
# own side at every blow; a slash carries it across the middle and no further, because the old slash thrown to x -0.30 was the
# arm going "way too much to the left side". The hand not striking draws back into its guard while the other drives, so the
# next blow starts where its hand already is; before the slash that hand has turned its edge out and waits, cocked, through
# the whole window a press can land in. The keys are authored for the right hand; a left one is its mirror.

LD = 0.627  # the Daggers' blade from the fist to the point (Wildhide, Gatewrought, Lordscale, Sovereign; 7bbc8f78)


def dagger_poses():
    """The Daggers' poses, the right hand's (a left one is its mirror): screen places of the fist and of the point."""
    b = lambda g, d, t, e=(0.3, -1.0, 0.0), r=0.0: blade(g, d, t, LD, e, r)
    thrust = (0.2, -1.0, 0.0)
    return dict(
        # forward grip, the points angled in toward the crosshair, the fists low and in from vanilla's corner
        rest=b((0.50, -1.00), 0.64, (0.14, -0.32)),
        # the free hand's guard: drawn back and in, the point a stab's length off the crosshair, so the stab out of it travels
        guard=b((0.54, -0.92), 0.50, (0.28, -0.34)),
        guard_back=b((0.60, -0.96), 0.46, (0.36, -0.38)),
        # the chain's stab, and how much deeper it ends as the freeze lets go
        stab=b((0.30, -0.68), 0.92, (0.05, -0.08), thrust),
        stab_deep=b((0.29, -0.66), 0.97, (0.045, -0.07), thrust),
        # the slash: cocked out to the right, the edge leading; across the crosshair; ended low, short of the far side
        slash_set=b((0.74, -0.70), 0.60, (0.96, -0.06), (-1.0, -0.3, 0.0)),
        slash_hit=b((0.30, -0.58), 0.74, (-0.10, -0.08), (-1.0, -0.3, -0.2)),
        slash_end=b((0.06, -0.92), 0.66, (-0.30, -0.64), (-0.7, -0.8, 0.2)),
        # both drawn low to the hips, for the finisher and the charged heavy
        coil=b((0.62, -1.06), 0.46, (0.36, -0.40)),
        coil_back=b((0.66, -1.12), 0.42, (0.42, -0.46)),
        # both driven in together, a little further than the chain's stab
        pair=b((0.28, -0.68), 0.95, (0.05, -0.06), thrust),
        pair_deep=b((0.27, -0.66), 1.00, (0.045, -0.05), thrust),
        # the riposte's: the furthest a dagger reaches
        lunge=b((0.28, -0.66), 1.02, (0.04, -0.06), thrust),
        lunge_deep=b((0.27, -0.64), 1.08, (0.04, -0.05), thrust),
        # out of a dodge and out of a slide: the slash, wider and lower
        sweep_set=b((0.80, -0.84), 0.60, (1.02, -0.28), (-1.0, -0.1, 0.0)),
        sweep_hit=b((0.30, -0.66), 0.72, (-0.12, -0.20), (-1.0, -0.1, -0.2)),
        sweep_end=b((0.04, -0.96), 0.64, (-0.36, -0.68), (-0.8, -0.6, 0.2)),
        low_set=b((0.82, -1.02), 0.58, (1.04, -0.50), (-1.0, 0.1, 0.0)),
        low_hit=b((0.30, -0.84), 0.70, (-0.14, -0.38), (-1.0, 0.2, -0.2)),
        low_end=b((0.04, -1.02), 0.64, (-0.38, -0.76), (-0.8, 0.0, 0.2)),
        # pulled back out of the body, on the way home from a blow driven down
        out=b((0.44, -0.86), 0.66, (0.16, -0.24)),
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

    def stab_view(side):
        """A stab leans the view a hair toward its own side and settles (side -1: the right hand's)."""
        return view((0, 0, 0, 0, STOP), (1.0, side * 0.5, 0.3, side * 0.4, GO), (1.4, side * 0.6, 0.35, side * 0.5, STOP), (3.6, 0, 0, 0, STOP))

    def cut_view(side, at=1.0, to=1.6):
        """A slash rolls the view with the cut, and it settles after."""
        return view((0, 0, 0, 0, STOP), (at, side * 0.9, 0.4, side * 1.2, GO), (to, side * 1.6, 0.5, side * 1.8, STOP), (to + 2.4, 0, 0, 0, STOP))

    def waits(hand, pose, frm=3.8, then=7.0, home=9.5):
        """Held in a pose through the window a press can land in; home only when the presses stop."""
        return [(frm, hand[pose], STOP), (then, hand[pose], STOP), (home, hand["rest"], STOP)]

    def stab(hand, pose="stab", deep="stab_deep", start="guard"):
        return [(0.0, hand[start], STOP), (1.0, hand[pose], GO), (1.4, hand[deep], STOP)]

    def both(hand):
        """The finisher: both gathered at the hips, both driven in at once, held a breath, pulled back into the guard."""
        return T([(0.0, hand["coil"], STOP), (1.1, hand["coil_back"], STOP), (2.0, hand["pair"], GO), (2.5, hand["pair_deep"], STOP)]
                 + waits(hand, "guard", 5.0) + [(11.0, hand["rest"], STOP)])

    moves = {
        # strike 1, the right: straight out of the stance; the left draws into its guard, ready for strike 2
        "strike_1": dict(hand="main", form="stab", hit=1.0, set=0.5, swish=0.45, trail=(0.55, 1.45),
                         keys=T(stab(P, start="rest") + waits(P, "guard")),
                         other=T([(0.0, m["rest"], STOP)] + waits(m, "guard", 3.0)), view=stab_view(-1)),
        # strike 2, the left, out of its guard; the right, meanwhile, turns its edge out and cocks for the slash
        "strike_2": dict(hand="off", form="stab", hit=1.0, set=0.45, swish=0.45, trail=(0.55, 1.45),
                         keys=T(stab(m) + waits(m, "guard")),
                         other=T([(0.0, P["guard"], STOP)] + waits(P, "slash_set", 3.2)), view=stab_view(1)),
        # strike 3, the right's slash: down across the crosshair to the lower left; both then gather at the hips for the finisher
        "strike_3": dict(hand="main", form="slash", hit=1.0, set=0.4, swish=0.45, trail=(0.45, 1.5),
                         keys=T([(0.0, P["slash_set"], STOP), (1.0, P["slash_hit"], GO), (1.6, P["slash_end"], STOP), (3.4, P["coil"], STOP),
                                 (5.0, P["coil"], STOP), (7.5, P["rest"], STOP)]),
                         other=T([(0.0, m["guard"], STOP), (3.4, m["coil"], STOP), (5.0, m["coil"], STOP), (7.5, m["rest"], STOP)]),
                         view=cut_view(-1)),
        # strike 4, the finisher: both points driven in at once
        "strike_4": dict(hand="both", form="stab", hit=2.0, set=1.1, swish=1.3, trail=(1.25, 2.5),
                         keys=both(P), other=both(m),
                         view=view((0, 0, 0, 0, STOP), (1.1, 0, -0.5, 0, STOP), (2.0, 0, 1.0, 0, GO), (2.5, 0, 1.3, 0, STOP), (5.0, 0, 0, 0, STOP))),
        # strike 5 (mastery), the left's slash out of the finisher's guard: strike 3 mirrored, wound bigger
        "strike_5": dict(hand="off", form="slash", hit=2.0, set=1.3, swish=1.45, trail=(1.45, 2.5),
                         keys=T([(0.0, m["guard"], STOP), (1.3, m["slash_set"], STOP), (2.0, m["slash_hit"], GO), (2.7, m["slash_end"], STOP)]
                                + waits(m, "guard", 4.6) + [(11.0, m["rest"], STOP)]),
                         other=T([(0.0, P["guard"], STOP), (2.0, P["guard_back"], STOP)] + waits(P, "guard", 4.0) + [(11.0, P["rest"], STOP)]),
                         view=cut_view(1, 2.0, 2.7)),
        # the heavy: charged coiled at the hips, the release is both stabs
        "heavy": dict(hand="both", form="stab", hit=0.5, swish=0.0, trail=(0.0, 1.0),
                      keys=T([(0.0, P["coil"], STOP), (0.5, P["pair"], GO), (1.0, P["pair_deep"], STOP), (3.2, P["out"], GO), (5.0, P["rest"], STOP), (10.0, P["rest"], STOP)]),
                      other=T([(0.0, m["coil"], STOP), (0.5, m["pair"], GO), (1.0, m["pair_deep"], STOP), (3.2, m["out"], GO), (5.0, m["rest"], STOP), (10.0, m["rest"], STOP)]),
                      view=view((0, 0, -0.4, 0, STOP), (0.5, 0, 1.0, 0, GO), (1.0, 0, 1.4, 0, STOP), (3.5, 0, 0, 0, STOP))),
        # the riposte: the chain's stab at full reach
        "riposte": dict(hand="main", form="stab", hit=1.0, swish=0.45, trail=(0.5, 1.5),
                        keys=T(stab(P, "lunge", "lunge_deep", "rest") + waits(P, "guard", 4.0, 7.0, 9.0) + [(10.5, P["rest"], STOP)]),
                        other=T([(0.0, m["rest"], STOP)] + waits(m, "guard", 3.0, 7.0, 9.0) + [(10.5, m["rest"], STOP)]),
                        view=view((0, 0, 0, 0, STOP), (1.0, -0.9, 0.7, -0.8, GO), (1.4, -1.1, 1.0, -1.0, STOP), (4.0, 0, 0, 0, STOP))),
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
        # out of a dodge: the slash, wide and a little lower
        "slipcut": dict(hand="main", form="slash", hit=1.0, swish=0.4, trail=(0.4, 1.5),
                        keys=T([(0.0, P["sweep_set"], STOP), (1.0, P["sweep_hit"], GO), (1.7, P["sweep_end"], STOP), (3.4, P["out"], GO), (5.0, P["rest"], STOP), (10.0, P["rest"], STOP)]),
                        other=T([(0.0, m["guard"], STOP), (3.0, m["guard"], STOP), (5.0, m["rest"], STOP), (10.0, m["rest"], STOP)]),
                        view=cut_view(-1, 1.0, 1.7)),
        # out of a slide: low across the legs
        "undercut": dict(hand="main", form="slash", hit=1.0, swish=0.4, trail=(0.4, 1.5),
                         keys=T([(0.0, P["low_set"], STOP), (1.0, P["low_hit"], GO), (1.9, P["low_end"], STOP), (3.6, P["out"], GO), (5.0, P["rest"], STOP), (11.0, P["rest"], STOP)]),
                         other=T([(0.0, m["guard"], STOP), (3.0, m["guard"], STOP), (5.0, m["rest"], STOP), (11.0, m["rest"], STOP)]),
                         view=cut_view(-1, 1.0, 1.9)),
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


# ---- the Scythe (decisions 8 and 9): both hands on it, the right at the snath's end and the left on its peg -----------
#
# A pose places the right fist on the screen at a depth, the snath's top (where the blade is set) at a screen place,
# and the way the blade sweeps out from it; the left hand closes on the peg wherever that puts it. Every wind-up keeps
# the blade on the screen, over the shoulder; the cut crosses the crosshair on the hit; a finisher's long recovery
# settles visibly into the ready stance.

SNATH = 0.656  # the right fist to the snath's top, blocks (the design's y 0 to 21)


def scythe_poses():
    """The Scythe's poses: the right fist's screen place and depth, the snath's top on the screen, the way the blade
    sweeps out from it (view space: +x right, +y up, +z back toward the eye)."""
    s = lambda g, d, top, out, r=0.0: blade(g, d, top, SNATH, tuple(-np.asarray(out, dtype=float)), r)  # noqa: E731
    return dict(
        # ready: the snath across the body from the lower right, the blade over the crosshair's left, its point down
        rest=s((0.62, -0.92), 0.50, (0.12, -0.18), (-1.0, -0.4, 0.4)),
        # the forehand reap (strike 1, the riposte, the slide's and dodge's): wound up over the right shoulder, the
        # blade high on the right pointing into the cut; across the crosshair level; carried round to the left, low
        r_wind=s((0.74, -0.82), 0.50, (0.50, 0.16), (-0.8, 0.5, -0.3)),
        r_cock=s((0.80, -0.76), 0.50, (0.62, 0.34), (-0.55, 0.65, -0.3)),
        r_hit=s((0.40, -0.80), 0.56, (0.32, 0.0), (-1.0, 0.0, -0.3)),
        r_through=s((0.36, -0.90), 0.54, (-0.30, -0.36), (-0.7, -0.4, 0.2)),
        r_low=s((0.38, -0.94), 0.52, (-0.22, -0.50), (-0.5, -0.7, 0.2)),
        # the backhand (strike 2): the blade turned over to lead to the right, wound over the left shoulder
        l_cock=s((0.26, -0.64), 0.56, (-0.42, 0.28), (0.55, 0.65, -0.3)),
        l_hit=s((0.26, -0.86), 0.56, (-0.30, -0.14), (1.0, 0.0, -0.3)),
        l_through=s((0.62, -0.86), 0.52, (0.62, -0.30), (0.5, -0.3, 0.8)),
        # the chop (strike 3, the heavy, the Plunge, the Execution): raised high, the blade over the top; hooked down
        # through the crosshair; hauled down and back
        raised=s((0.58, -0.50), 0.46, (0.34, 0.74), (-0.5, 0.4, -1.0)),
        raised_back=s((0.62, -0.45), 0.48, (0.40, 0.80), (-0.4, 0.3, -1.0)),
        hook=s((0.30, -0.80), 0.56, (0.04, 0.10), (-0.3, -1.0, -0.2)),
        haul=s((0.20, -0.98), 0.54, (-0.16, -0.42), (-0.2, -1.0, 0.35)),
        # the finisher's settle (decision 9): drawn back up across the body and turned to the ready, a breath held
        settle=s((0.68, -0.88), 0.49, (0.20, -0.12), (-1.0, -0.3, 0.45)),
        # the turns over between a forehand and a backhand, the blade's face kept to the view: up past the crosshair's
        # left as the backhand winds; up on the right as the chop lifts; hung low as the backhand comes home
        turn_up=s((0.30, -0.78), 0.58, (-0.10, 0.0), (0.3, 1.0, -0.5)),
        lift=s((0.60, -0.70), 0.54, (0.40, 0.05), (-0.3, 1.0, -0.5)),
        turn_down=s((0.50, -0.95), 0.50, (0.40, -0.45), (0.0, -1.0, -0.3)),
        # the whirl and the Art: wound far round to the right, low; swept clean across, the blade off to the left
        far=s((0.82, -0.78), 0.50, (0.78, 0.06), (-0.7, 0.4, -0.5)),
        sweep=s((0.30, -0.82), 0.58, (0.22, -0.06), (-1.0, 0.0, -0.2)),
        gone=s((0.34, -0.92), 0.54, (-0.40, -0.30), (-0.8, -0.4, 0.0)),
        # the slide's and dodge's reaps, low across the legs
        low_wind=s((0.76, -0.86), 0.48, (0.62, -0.12), (-1.0, -0.1, 0.3)),
        low_hit=s((0.40, -0.94), 0.58, (0.28, -0.40), (-1.0, -0.2, -0.3)),
        low_through=s((0.36, -0.98), 0.54, (-0.30, -0.62), (-0.6, -0.6, 0.2)),
    )


def scythe_moves():
    P = scythe_poses()
    T = Track

    def view(*keys):
        return View(keys)

    def home(t, tail):  # a strike's hands back at the ready by t, held there to the move's end
        return [(t, P["rest"], STOP), (tail, P["rest"], STOP)]

    return {
        # strike 1, the forehand reap: up over the right shoulder in view, across the crosshair on the hit, carried
        # round to the left and held low there (the backhand starts from it), home when no press comes
        "strike_1": dict(hand="main", hit=3.0, set=2.4, swish=2.2, trail=(2.3, 3.5),
                         keys=T([(0.0, P["rest"], STOP), (1.6, P["r_wind"], GO), (2.4, P["r_cock"], STOP), (3.0, P["r_hit"], GO),
                                 (3.9, P["r_through"], GO), (5.0, P["r_low"], STOP), (7.0, P["r_low"], STOP)] + home(12.0, 14.0)),
                         view=view((0, 0, 0, 0, STOP), (2.4, 1.2, -0.4, 1.0, STOP), (3.0, -0.8, 0.4, -0.8, GO), (3.9, -2.0, 0.6, -1.6, STOP),
                                   (7.0, -0.6, 0.2, -0.4, STOP), (11.0, 0, 0, 0, STOP))),
        # strike 2, the backhand: the blade turned over as it rises over the left shoulder, across to the right
        "strike_2": dict(hand="main", hit=3.0, set=2.4, swish=2.2, trail=(2.3, 3.5),
                         keys=T([(0.0, P["r_low"], STOP), (1.1, P["turn_up"], GO), (2.4, P["l_cock"], STOP), (3.0, P["l_hit"], GO),
                                 (3.9, P["l_through"], STOP), (9.0, P["l_through"], STOP), (11.0, P["turn_down"], GO)]
                                + home(13.5, 15.0)),
                         view=view((0, 0, 0, 0, STOP), (2.4, -1.2, -0.4, -1.0, STOP), (3.0, 0.8, 0.4, 0.8, GO), (3.9, 2.0, 0.6, 1.6, STOP),
                                   (9.0, 0.6, 0.2, 0.4, STOP), (12.0, 0, 0, 0, STOP))),
        # strike 3, the finisher: raised high over the head, hooked down through the crosshair, hauled down, then the
        # long recovery shown: drawn back up across the body and settled into the ready (decision 9)
        "strike_3": dict(hand="main", hit=5.0, set=4.0, swish=4.2, trail=(4.3, 5.5),
                         keys=T([(0.0, P["l_through"], STOP), (1.5, P["lift"], GO), (2.9, P["raised"], GO), (4.0, P["raised_back"], STOP),
                                 (5.0, P["hook"], GO),
                                 (6.2, P["haul"], STOP), (7.6, P["haul"], STOP), (10.0, P["settle"], STOP), (13.0, P["settle"], STOP)]
                                + home(17.0, 20.0)),
                         view=view((0, 0, 0, 0, STOP), (4.0, 0, -1.4, 0, STOP), (5.0, 0, 1.6, 0, GO), (6.2, 0, 2.6, 0, STOP),
                                   (8.0, 0, 1.0, 0, STOP), (12.0, 0, 0, 0, STOP))),
        # strike 4 (mastery), the whirl: from the settle wound far round to the right, swept clean across, carried off
        "strike_4": dict(hand="main", hit=4.0, set=3.2, swish=3.2, trail=(3.3, 4.5),
                         keys=T([(0.0, P["settle"], STOP), (2.4, P["far"], GO), (3.2, P["far"], STOP), (4.0, P["sweep"], GO),
                                 (5.2, P["gone"], STOP), (8.0, P["gone"], STOP)] + home(14.0, 21.0)),
                         view=view((0, 0, 0, 0, STOP), (3.2, 3.0, -0.4, 1.6, STOP), (4.0, -1.0, 0.4, -1.0, GO), (5.2, -5.0, 0.6, -2.4, STOP),
                                   (9.0, -1.0, 0.2, -0.4, STOP), (14.0, 0, 0, 0, STOP))),
        # the heavy: charged raised, the release hooks down at once and hauls through the body
        "heavy": dict(hand="main", hit=0.3, swish=0.0, trail=(0.0, 2.0),
                      keys=T([(0.0, P["raised_back"], STOP), (0.3, P["hook"], GO), (2.0, P["haul"], STOP), (4.0, P["haul"], STOP)]
                             + home(10.0, 16.0)),
                      view=view((0, 0, -0.8, 0, STOP), (0.3, 0, 1.8, 0, GO), (2.0, 0, 2.6, 0, STOP), (6.0, 0, 0, 0, STOP))),
        # the riposte: a short forehand reap out of the guard
        "riposte": dict(hand="main", hit=1.0, swish=0.4, trail=(0.5, 1.5),
                        keys=T([(0.0, P["rest"], STOP), (0.5, P["r_cock"], STOP), (1.0, P["r_hit"], GO), (1.9, P["r_through"], GO),
                                (3.0, P["r_low"], STOP)] + home(8.0, 13.0)),
                        view=view((0, 0, 0, 0, STOP), (0.5, 1.0, -0.3, 0.8, STOP), (1.0, -1.0, 0.4, -1.0, GO), (1.9, -2.0, 0.6, -1.6, STOP),
                                  (6.0, 0, 0, 0, STOP))),
        # the Art: wound far round, one great sweep across with the view turning into it
        "art": dict(hand="main", hit=5.0, swish=4.2, trail=(4.3, 5.5),
                    keys=T([(0.0, P["rest"], STOP), (3.0, P["far"], GO), (4.2, P["far"], STOP), (5.0, P["sweep"], GO), (6.4, P["gone"], STOP),
                            (10.0, P["gone"], STOP)] + home(16.0, 25.0)),
                    view=view((0, 0, 0, 0, STOP), (4.2, 4.0, -0.6, 2.0, STOP), (5.0, -1.5, 0.6, -1.2, GO), (6.4, -7.0, 0.8, -3.0, STOP),
                              (11.0, -1.0, 0.2, -0.4, STOP), (16.0, 0, 0, 0, STOP))),
        # out of a dodge: a quick low reap
        "slipcut": dict(hand="main", hit=1.0, swish=0.4, trail=(0.4, 1.5),
                        keys=T([(0.0, P["low_wind"], STOP), (0.45, P["low_wind"].moved(at=P["low_wind"].at + [0.03, 0.0, 0.03]), STOP),
                                (1.0, P["low_hit"], GO), (1.9, P["low_through"], STOP)] + home(6.0, 10.0)),
                        view=view((0, 0, 0, 0, STOP), (0.45, 1.0, 0.4, 1.0, STOP), (1.0, -1.0, 0.8, -1.2, GO), (1.9, -2.2, 1.0, -2.0, STOP),
                                  (5.0, 0, 0, 0, STOP))),
        # out of a slide: lower still, across the legs
        "undercut": dict(hand="main", hit=1.0, swish=0.4, trail=(0.4, 1.5),
                         keys=T([(0.0, P["low_wind"], STOP), (0.45, P["low_wind"].moved(at=P["low_wind"].at + [0.03, -0.03, 0.03]), STOP),
                                 (1.0, P["low_hit"].moved(at=P["low_hit"].at + [0.0, -0.06, 0.0]), GO), (2.0, P["low_through"], STOP)]
                                + home(7.0, 11.0)),
                         view=view((0, 0, 0, 0, STOP), (0.45, 0.8, 1.2, 1.0, STOP), (1.0, -1.0, 2.2, -1.2, GO), (2.0, -2.0, 2.6, -2.0, STOP),
                                   (6.0, 0, 0, 0, STOP))),
        # armed in a fall: raised; on landing hooked down into what's below
        "plunge": dict(hand="main", hit=0.3, swish=0.0, trail=(0.0, 1.0),
                       keys=T([(0.0, P["raised"], STOP), (0.3, P["hook"], GO), (1.0, P["haul"], STOP), (3.0, P["haul"], STOP)]
                              + home(9.0, 14.0)),
                       view=view((0, 0, 0, 0, STOP), (0.3, 0, 3.0, 0, GO), (1.0, 0, 3.6, 0, STOP), (5.0, 0, 0, 0, STOP))),
        # the Execution: raised over the broken body, hooked down into it, held, wrenched out
        "execution": dict(hand="main", hit=2.0, swish=1.4, trail=(1.4, 2.5),
                          keys=T([(0.0, P["rest"], STOP), (1.0, P["raised"], GO), (1.5, P["raised_back"], STOP), (2.0, P["hook"], GO),
                                  (2.6, P["haul"], STOP), (4.0, P["haul"], STOP), (6.0, P["settle"], STOP)] + home(10.0, 19.0)),
                          view=view((0, 0, 0, 0, STOP), (1.5, 0, -1.6, 0, STOP), (2.0, 0, 2.6, 0, GO), (2.6, 0, 3.2, 0, STOP), (4.0, 0, 3.0, 0, STOP),
                                    (7.0, 0, 0, 0, STOP))),
    }


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
        two = getattr(held, "nib", None) is not None
        fists = [(R @ (held.grip if two else np.asarray(hand_grip)) + H + d, side)]
        if side == "r" and two:  # a two-handed weapon's second hand, on its place
            fists.append((R @ held.nib + H + d, "l"))
        for g, arm in fists:
            if side == "l":
                g = M @ g
            elbow = g + np.array([0.22 * (1 if arm == "r" else -1), -0.32, 0.30])
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
    strike = mv["keys"].at(t)
    if "other" not in mv:  # both hands on one weapon: the second closes on it where the main hand holds it
        return right_handed(held, strike, False), (None, None)
    other = mv["other"].at(t)
    if mv["hand"] == "off":
        left, right = strike, other
    else:
        right, left = strike, other
    return right_handed(held, right, False), right_handed(held, left, True)


def length(mv):
    return max(mv["keys"].end(), mv["other"].end() if "other" in mv else 0.0)


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


MOVES = {"daggers": dagger_moves, "scythe": scythe_moves}
POSES = {"daggers": dagger_poses, "scythe": scythe_poses}

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


def _fist3(at, reach=(NEAR, FAR, CLOSE)):
    """Where a fist seen at view place {@code at} is held from outside (the unposed figure, px: right at -x, y down,
    -z ahead): the same bearing and fall from the eye, its depth onto the arm's reach (reach: the view's depths at the
    chest and at an arm's length, and how far before the shoulders the first is)."""
    import arms_rig4 as R4
    near, far, close = reach
    depth = -at[2]
    fwd = close + (OUT - close) * float(np.clip((depth - near) / (far - near), 0.0, 1.15))
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


STANCE3 = 25.0  # degrees: a two-handed haft is fought side-on, the off shoulder forward (arms_rig4's scythe stance)
SWEEP3 = 0.35  # of the snath's turn about the eye the chest takes, half a tick ahead of it (the body leads)
SWEEP_MAX = 30.0
LET_GO = 3.6  # px: the off fist (4 across) still closes on the haft 2.4 off its line; past 3.6 it lets go
# a two-handed haft is held out before the body (first person's hands at 0.40 to 0.60 blocks, 4 px before the shoulders
# to an arm's length): held at the chest, as a dagger's guard is, a raised snath hangs its blade through the face
REACH2 = (0.40, 0.60, 4.0)


def third_two(style):
    """A two-handed style's third person (the scythe, decision 8: two hands in both views): the main fist where first
    person's is seen (_fist3), its arm solved onto it, and the weapon turned in that fist the whole way first person
    turns it (a scythe's blade stands out to one side, so its roll about the snath counts, as a dagger's doesn't); the
    off hand closed on the snath up from the main (the figure's fist closes down the haft, arms_holds.HAFT3, so the
    peg is 11.5 px on, past a figure's span: the reachable spot nearest its shoulder in arms_rig4's range, glided
    between keys), letting go cleanly where its arm can't reach (off the haft by more than LET_GO) and taking hold
    again; the chest side-on, turning with the snath. Outside the blow no wrist turns faster than WRIST_STEP a key."""
    import arms_rig4 as R4
    import arms_moves as A
    import arms_holds as AH
    import power_casts as PC
    held = held_of(style)
    wid = REVIEW[style]
    Rh = L.MR.euler(*L.MR.held(wid)[0])
    g3 = AH.grip3(style)
    turn, local = R4.hold_of(L.display_3p(wid, "right"), "right", g3)
    cloud, _ = R4.weapon_cloud(style)
    sc3 = L.display_3p(wid, "right")[2]
    rel = (cloud - g3) * sc3 * 16
    ahead = (cloud - g3) @ AH.AXIS_ITEM > 0  # the blade's side of the fist; behind it the snath's butt, by the belly
    yaw0 = None
    out = {}
    for name, mv in MOVES[style]().items():
        own = f"arms_{style}_{name}"
        base = own if own in A.BASE else f"arms_{name}"
        ts = np.arange(0.0, length(mv) + 1e-6, DT)
        n = len(ts)
        poses = [mv["keys"].at(float(t)) for t in ts]
        Rvs = [sample_move(held, mv, float(t))[0][1] @ Rh for t in ts]
        ws = [_weight(name, mv, float(t)) for t in ts]
        # the chest: side-on, turned with the snath's bearing about the eye (from the stance's), led half a tick
        def bearing(pz):
            d = pz.to - pz.at
            return math.degrees(math.atan2(d[0], -d[2]))
        if yaw0 is None:
            yaw0 = bearing(POSES[style]()["rest"])
        yaw = [bearing(pz) - yaw0 for pz in poses]
        lead = int(round(0.5 / DT))
        yaw = yaw[lead:] + [yaw[-1]] * lead
        rec_by = {}
        for bent in (True, False):
            clip = A.BASE[base + ("_bent" if bent else "")]
            b0s = [clip.sample(float(t)) for t in ts]
            tws = [(1 - w) * R4.twist_of(b0) + w * (STANCE3 + float(np.clip(SWEEP3 * y, -SWEEP_MAX, SWEEP_MAX)))
                   for b0, w, y in zip(b0s, ws, yaw)]
            # the main arm onto the fist, the wrist turning the weapon the rest of the way
            prev = R4.terms_of(b0s[0], "right", bent)
            rights, Ws, miss = [], [], 0.0
            for i in range(n):
                b = R4.with_twist(b0s[i], tws[i])
                Q = R4.B @ Rvs[i]  # the weapon's turn in the model, as first person turns it in the view
                am = Q @ AH.AXIS_ITEM
                target = R4.to_world(b, _fist3(poses[i].at, REACH2))
                roll = lambda Mx, am=am: 0.02 * math.degrees(math.acos(float(np.clip((Mx @ turn @ AH.AXIS_ITEM) @ am, -1, 1))))  # noqa: E731
                v, m_ = R4.ik_arm(b, "right", bent, target, local, prev, roll)
                prev = v
                vb = (1 - ws[i]) * R4.terms_of(b0s[i], "right", bent) + ws[i] * v
                b["right_arm"] = R4.arm_bones(*vb, tws[i], "right", bent)
                Mx, _, _ = R4.limb_frame(b, "right", bent)
                W = L.quat_mat(L.slerp(np.array([0.0, 0.0, 0.0, 1.0]), L.mat_quat((Mx @ turn).T @ Q), ws[i]))
                rights.append([round(float(x), 2) for x in vb])
                Ws.append(W)
                if ws[i] > 0.999:
                    miss = max(miss, m_)
            Ws = _limit(Ws, ts, mv["hit"])

            # the off hand onto the snath where the weapon really is (the solved arm, the limited wrist)
            frames = []
            for i in range(n):
                b = R4.with_twist(b0s[i], tws[i])
                b["right_arm"] = R4.arm_bones(*rights[i], tws[i], "right", bent)
                Mx, Tx, _ = R4.limb_frame(b, "right", bent)
                Qw = Mx @ turn @ Ws[i]
                G, axis_w = Tx + Mx @ local, unit(Qw @ AH.AXIS_ITEM)
                frames.append((b, G, axis_w, R4.off_spot(R4.STYLES[style], G, axis_w, np.array(PC.part_frames(b)["left_arm"][1]), 1)))
            raw = np.array([f[3] for f in frames])
            spots = np.array([raw[max(0, i - 3):i + 4].mean() for i in range(n)])

            # it lets go over the first half of the move's last pair of keys, the arm still solved (kept out of the head),
            # and only then is blended into the base clip's: the arm's terms blended from a reach elsewhere swept it
            # through the face (strike 3's settle, looking down)
            k0, k1 = mv["keys"].keys[-2][0], mv["keys"].keys[-1][0]
            mid = (k0 + k1) / 2
            sm = lambda u: u * u * (3 - 2 * u)  # noqa: E731
            home = np.array([1.0 - sm(float(np.clip((t - k0) / max(1e-6, mid - k0), 0.0, 1.0))) for t in ts])
            wl = np.array([ws[i] if ts[i] <= k0 else 1.0 - sm(float(np.clip((ts[i] - mid) / max(1e-6, k1 - mid), 0.0, 1.0)))
                           for i in range(n)])

            def solve_off(hold):
                prev_l = R4.terms_of(b0s[0], "left", bent)
                res = []
                for i in range(n):
                    b, G, axis_w, _ = frames[i]
                    b = dict(b)
                    handle = G + axis_w * spots[i]
                    Ml, Tl, _ = R4.limb_frame(b0s[i], "left", bent)
                    own_l = Tl + Ml @ R4.HAND_LOCAL["left"]
                    k = wl[i] * hold[i]
                    roll_l = lambda M_, axis_w=axis_w: 2.0 * float(M_[:, 1] @ axis_w)  # noqa: E731
                    # let go, it's solved from the base clip's own arm, which already has the fist there
                    start = R4.terms_of(b0s[i], "left", bent) if k < 0.05 else prev_l
                    v, m_ = R4.ik_arm(b, "left", bent, own_l + (handle - own_l) * k, R4.HAND_LOCAL["left"], start, roll_l)
                    prev_l = v
                    vb = (1 - wl[i]) * R4.terms_of(b0s[i], "left", bent) + wl[i] * v
                    b["left_arm"] = R4.arm_bones(*vb, tws[i], "left", bent)
                    res.append((vb, m_, float(np.linalg.norm(handle - own_l)), b))
                return res
            first = solve_off(home)
            hold = np.minimum(home, [float(np.clip((LET_GO - r[1]) / 1.2, 0.0, 1.0)) for r in first])
            hold = np.array([hold[max(0, i - 1):i + 2].min() for i in range(n)])  # let go a little early, take hold late
            hold = np.array([hold[max(0, i - 2):i + 3].mean() for i in range(n)])  # and glide between
            solved = solve_off(hold) if hold.min() < 0.999 else first
            for _ in range(6):  # solved again from where it let go it may still fall short: let go there too
                short = np.array([r[1] > LET_GO and hold[i] > 0.0 for i, r in enumerate(solved)])
                if not short.any():
                    break
                cut = np.array([0.0 if short[max(0, i - 2):i + 3].any() else 1.0 for i in range(n)])
                hold = np.minimum(hold, np.array([cut[max(0, i - 2):i + 3].mean() for i in range(n)]))
                solved = solve_off(hold)
            rec = {"right": rights, "left": [[round(float(x), 2) for x in r[0]] for r in solved],
                   "twist": [round(float(x), 2) for x in tws], "wrist": [], "wrist_left": [], "miss": round(miss, 2),
                   "miss_off": 0.0, "held": 0.0, "depth": 0.0, "hilt": 0.0}
            both = [i for i in range(n) if ws[i] > 0.999 and ts[i] <= k0]
            rec["held"] = round(float(np.mean([hold[i] for i in both])) if both else 1.0, 2)
            rec["miss_off"] = round(max([solved[i][1] for i in both if hold[i] > 0.999] or [0.0]), 2)
            for i in range(n):  # the check: the weapon as the game will draw it, into the body
                b = R4.rigged_bones(clip, rec, i, bent)
                frs = [PC.part_frames(b, p_) for p_ in (0.0, 30.0)]
                Mx, Tx, _ = R4.limb_frame(b, "right", bent)
                G = Tx + Mx @ local
                P = G + rel @ (Mx @ turn @ Ws[i]).T
                rec["depth"] = max(rec["depth"], round(R4.body_depth(frs, P[ahead], G), 2))
                rec["hilt"] = max(rec["hilt"], round(R4.body_depth(frs, P[~ahead], G), 2))
            rec["wrist"] = [[round(float(x), 4) for x in _quat(W, None)] for W in Ws]
            rec["wrist_left"] = [[0.0, 0.0, 0.0, 1.0] for _ in Ws]  # the off hand holds the haft, no weapon of its own
            rec_by["bent" if bent else "straight"] = rec
        out[name] = rec_by
    return out


def third_write(style="daggers"):
    data = json.load(open(KEYS3)) if os.path.exists(KEYS3) else {"generatedBy": "tools/art/arms_keys.py third", "dt": DT, "styles": {}}
    data["styles"][style] = third_two(style) if style in TWO_HANDS else third(style)
    with open(KEYS3, "w") as f:
        f.write(json.dumps(data, separators=(",", ":")))
    moves = MOVES[style]()
    for name, r in data["styles"][style].items():
        o, i = wrist_steps(r["bent"], moves[name])
        print(f"  {name:10s} wrist {o:5.1f} deg a key outside the blow, {i:5.1f} in it; blade into the body "
              f"{r['bent']['depth']:.1f} px bent, {r['straight']['depth']:.1f} straight; hand off its spot {r['bent']['miss']:.1f} px"
              + (f"; both hands on {r['bent']['held']:.0%} bent, {r['straight']['held']:.0%} straight; the butt "
                 f"{r['bent']['hilt']:.1f} px in" if "held" in r["bent"] else ""))
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
            hands = [(wid, "right", L.wrist_now(wr, t))] + ([] if style in TWO_HANDS else [(wid, "left", L.wrist_now(wl, t))])
            im = L.figure_3p(bent, t, hands, yaw, pitch, 4.0,
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
        qk = _quat(strike[1], qk)
        cam = mv["view"].at(t)
        keys.append([round(t, 3)] + [round(float(v), 4) for v in strike[0]] + [round(float(v), 5) for v in qk]
                    + [round(float(v), 3) for v in cam])
        if other[0] is not None:
            qi = _quat(other[1], qi)
            idle.append([round(t, 3)] + [round(float(v), 4) for v in other[0]] + [round(float(v), 5) for v in qi])
    return keys, idle


def stance_of(style):
    d, R = right_handed(held_of(style), POSES[style]()["rest"], False)
    return [round(float(v), 4) for v in d] + [round(float(v), 5) for v in _quat(R, None)]


NOD = 0.6  # a stab's kick: the view nodded in a touch, no turn (the kick's scale is the freeze's, ArmsPredict.impact)


def kick_of(held, mv, form):
    """The view's kick on a hit [yaw, pitch] (decision 6): a stab punches the view in a touch (a nod, no turn: its point
    drives into the screen, and the old row kicked a right stab sideways); a cut kicks along the way its points travel
    on screen into the hit (+yaw to the right, +pitch down); a pair cutting out both ways at once nods like a stab."""
    if form == "stab":
        return [0.0, mv.get("nod", NOD)]
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
        row = {"form": mv.get("form", prev.get("form", "stab")), "weight": prev.get("weight", "snap"), "hand": mv["hand"], "keys": keys,
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
        elif not keyed3:  # no third person of its own yet: arms_rig4's wrists kept with its clips (the scythe, SPEC §25)
            for key in ("wrist3", "wrist3s"):
                if key in prev:
                    row[key] = prev[key]
        moves[name] = row
    new = dict(old)
    new["moves"] = moves
    new["stance"] = stance_of(style)
    if held.nib is not None:  # the second fist on its own grip on the weapon (decision 8: two hands in both views)
        new["offHand"] = [round(float(v), 4) for v in held.nib]
    new["by"] = KEYED_BY
    return new


KEYED = ("daggers", "scythe")


def bake(styles=KEYED):
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


def check(styles=KEYED):
    """The rules the data alone can show, on the baked file (the GameTests hold the game to the same): the other styles
    byte for byte as git has them; each blow keyed on the tick the server counts its hit; a dagger's stab low and on its
    own side there, another stab's point on the crosshair; every move home at the stance by its end, or held still from the chain's earliest press when the
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
            if style == "daggers" and name not in ("plunge", "execution", "art"):
                # Euan 2026-11-09: the fist low and on its own side at the blow ("the hands go way too upwards"); a stab's
                # point not past the crosshair; a slash's fist carried across the middle and no further ("way too much to
                # the left side")
                side = -1.0 if mv["hand"] == "off" else 1.0
                ndc = lambda q: (side * q[0] / (-q[2] * ASPECT * TAN), q[1] / (-q[2] * TAN))
                p = mv["keys"].at(hit).at
                fx, fy = ndc(p)
                if fx < 0.26 or fy > -0.55:
                    bad.append(f"{name}: the fist ({fx:.2f}, {fy:.2f}) isn't low and on its own side at the blow")
                if clip["form"] == "stab" and side * tip_ndc(held, d, R, left=mv["hand"] == "off")[0] < 0:
                    bad.append(f"{name}: the point crosses the crosshair")
                worst = min(ndc(mv["keys"].at(float(x)).at)[0] for x in np.arange(0.0, total, 0.1))
                if worst < -0.08:
                    bad.append(f"{name}: the fist crosses to x {worst:.2f}")
            elif clip["form"] == "stab" and name not in ("plunge", "execution"):
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
            if style in TWO_HANDS and name.startswith("strike_"):  # decision 8: the wind-up in view
                tip = tip_ndc(held, *_at(clip["keys"], clip["set"]))
                if max(abs(tip[0]), abs(tip[1])) > 1.0:
                    bad.append(f"{name}: the blade's point {tip.round(2)} is out of view at the set")
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
        print(bake(KEYED))
        return 0
    if argv[0] == "check":
        return 0 if check(KEYED) else 1
    if argv[0] == "third":  # third [STYLE]
        print(third_write(argv[1] if len(argv) > 1 else "daggers"))
        return 0
    if argv[0] == "sheet3":  # sheet3 OUT MOVE [STYLE]
        print(sheet3(argv[1], argv[3] if len(argv) > 3 else "daggers", argv[2]))
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
