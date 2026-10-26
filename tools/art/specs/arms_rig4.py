"""Third person from first person's blade (Euan 2026-10-26: "how are we gonna get that exactly the same as first
person... so that it genuinely looks really, really good").

First person is designed from the blade: each move's path, the edge leading, through the target on the hit tick
(anim_lab.py, baked to fp_swings.json and audited). Third person used to be designed from the arm (a few poses per
kind, the weapon wherever the hand put it), so long blades swept through the body. Here third person takes the
blade's path from first person and the body follows it:

1. The weapon's pose comes from first person's own keys: at every key the grip's place and the blade's turn as the
   game draws them in the view. The arc about first person's pivot is carried onto the body's real shoulder, turned
   as a whole (keeping the view's up) so that its line into the target runs from the shoulder to a point in front of
   the face, its lever scaled to a real arm's reach: the same blade, the same way, at the same moment.
2. The blade's turn is never changed to clear the body: where a key would put any weapon of the style into the head,
   the body or the legs, the grip shifts a little (out, forward, up), chosen over the whole move at once so it never
   jumps (Viterbi).
3. The chest turns with the lever and a little ahead of it (the body leads, the blade follows); the main hand closes
   on the grip and the off hand on the handle by IK, elbows bent (Bendable Cuboids, the pack's) or straight (without
   it: their own solve); the wrist (ArmsWrist) turns the weapon the rest of the way in the fist.

Baked to arms_rig4.json; arms_moves.py builds the clips from it, anim_lab.py bake the wrist keys (wrist3 for the bent
clip, wrist3s for the straight). Model space throughout (power_casts: pixels, y down, front -z, the right side at -x);
first person's view space in blocks (x right, y up, -z ahead).
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
for p_ in (HERE, ART):
    if p_ not in sys.path:
        sys.path.insert(0, p_)
import power_casts as PC  # noqa: E402

DATA = os.path.join(HERE, "arms_rig4.json")
DT = 0.25  # ticks between keys (the wrist's own step)
# the rigged styles: both two-handed, swung about a point before the chest's middle (both hands meet on the hilt
# there, not out past the sword arm's shoulder), the grip that far from it at the hit (px), the off hand on the grip
# just toward the pommel (px along the weapon); the chest's share of the lever's turn; how hard a pommel in the chest
# counts against keeping both hands on (a figure's arms meet at most ~4 px before its chest, so a long hilt held
# two-handed and pointed ahead reaches into it: the Katana's turns aside, the longest Greatblades' can't without
# letting go, and a two-handed great sword is worth more than its pommel by the belly)
STYLES = {
    "katana": dict(pivot=(-1.5, 3.0, -2.0), radius=6.5, second=-3.5, chest=0.7, torso=2.0),
    "greatblade": dict(pivot=(-1.5, 3.0, -3.5), radius=7.0, second=-4.0, chest=0.8, torso=1.0),
}
REACH = 8.4  # px: how far a fist reaches from its shoulder
EYE = np.array([0.0, -1.92, 0.0])  # the camera in the unposed model: 1.62 blocks up, the neck at 1.5
B = np.diag([-1.0, -1.0, 1.0])  # view (x right, y up, -z ahead) <-> model (right at -x, y down, -z ahead)
SHOULDER = np.array([-5.0, 2.0, 0.0])  # the right arm's pivot, unposed
HAND_LOCAL = {"right": np.array([-1.0, 8.5, 0.0]), "left": np.array([1.0, 8.5, 0.0])}  # a fist's middle, arm frame
# grip shifts (px, the figure's own frame: right -x, up -y, ahead -z) the clearance may choose from
# (both ways across: a two-handed hilt is brought in toward the middle, where both hands meet on it)
SHIFTS = [np.array(v, dtype=float) for v in (
    (0, 0, 0), (-2, 0, 0), (-4, 0, 0), (-6, 0, 0), (2, 0, 0), (4, 0, 0), (6, 0, 0), (0, 0, -2), (0, 0, -4), (0, 0, -6),
    (0, -2, 0), (0, -4, 0), (0, 2, 0), (0, 4, 0), (-3, 0, -3), (3, 0, -3), (-4, -2, -2), (4, -2, -2), (-2, -2, -2),
    (0, -3, -3))]
# outside the blow (the drive, the hit and the cut through stay first person's exactly), the move may be turned as a
# whole about the pivot (degrees, + raises what is ahead): first person carries a long blade down out of the frame
# after the blow, which in third person is through the legs, and cocks an overhead chop back past the top of the view,
# which in third person is through the head (a hand can't rise above it); the lever and the blade turn together, so
# the edge still leads
LIFTS = (0.0, 12.0, 24.0, 36.0, -15.0, -30.0)
SHARED = ("riposte", "art", "slipcut", "undercut", "plunge", "execution")


# ---- the arm: pose terms and IK (the shelved rig's, arms-rig3-wip) ----------------------------------------------------

def arm_bones(x, y, z, flex, twist, arm, bent):
    """An arm's bones for pose terms (x, y, z, elbow flex) on a chest turned {@code twist}, as power_casts.bake makes
    them (the shoulder riding the chest's yaw; a bent elbow's upper arm swung back to keep the hand on line)."""
    V, OFF = PC.V, PC.OFF
    tw = math.radians(twist)
    sx, sz = 5 - 5 * math.cos(tw), 5 * math.sin(tw)
    sgn = 1 if arm == "right" else -1
    name = arm + "_arm"
    if bent:
        x += PC.keep_line(name, flex)
    b = {"rotation": (V(x), V(y + twist), V(z)), "position": (V(sgn * sx), OFF, V(sgn * sz))}
    if bent:
        b["bend"] = (V(-flex), V(0), V(0))
    return b


def terms_of(bones, arm, bent):
    """The pose terms (x, y, z, flex) a baked arm holds (arm_bones' inverse)."""
    ch = bones[arm + "_arm"]
    rot = ch["rotation"]
    flex = -ch["bend"][0].c if bent and "bend" in ch else 0.0
    x = rot[0].c - (PC.keep_line(arm + "_arm", flex) if bent else 0.0)
    return np.array([x, rot[1].c - twist_of(bones), rot[2].c, flex])


def twist_of(bones):
    r = bones["torso"]["rotation"][1]
    return 0.0 if r.off else r.c


def with_twist(bones, twist):
    b = dict(bones)
    b["torso"] = {"rotation": (PC.OFF, PC.V(twist), PC.OFF)}
    return b


def limb_frame(bones, arm, bent, pitch=0.0):
    fr = PC.part_frames(bones, pitch)
    key = arm + "_arm"
    M, T = fr[key + ":lower"] if bent and key + ":lower" in fr else fr[key]
    return np.array(M), np.array(T), fr


def arm_depths(frames, arm, bent):
    """How deep (px) the arm's own points go into the head, the torso and the legs (0 when clear)."""
    name = arm + "_arm"
    _, (x0, y0, z0, w, h, d), _ = PC.PARTS[name]
    cy = PC.JOINTS[name][0]
    pts = []
    for fx in (0.1, 0.5, 0.9):
        for fy in (0.15, 0.4, 0.65, 0.95):
            for fz in (0.1, 0.5, 0.9):
                q = np.array([x0 + w * fx, y0 + h * fy, z0 + d * fz])
                M, T = frames.get(name + ":lower", frames[name]) if (bent and q[1] > cy) else frames[name]
                pts.append(np.array(T) + np.array(M) @ q)
    P = np.array(pts)
    out = np.zeros(len(P))
    for other in ("head", "torso", "right_leg", "left_leg"):
        Mo, To = (np.array(v) for v in frames[other])
        _, (ox, oy, oz, ow, oh, od), _ = PC.PARTS[other]
        Lc = (P - To) @ Mo
        dd = np.minimum.reduce([Lc[:, 0] - ox, ox + ow - Lc[:, 0], Lc[:, 1] - oy, oy + oh - Lc[:, 1], Lc[:, 2] - oz, oz + od - Lc[:, 2]])
        out = np.maximum(out, dd)
    return np.maximum(0.0, out + 0.4)  # a little room: the limit is a graze, not a touch


def ik_arm(bones, arm, bent, target, local, prev, roll_cost=None, iters=18):
    """Pose terms (x, y, z, flex) that put {@code local} (an arm-frame point) on {@code target}, from {@code prev};
    {@code roll_cost(M)} prices the arm's roll. A straight arm keeps flex 0. Levenberg-Marquardt. -> (terms, miss)."""
    v = np.array(prev, dtype=float)
    tw = twist_of(bones)
    free = 4 if bent else 3

    def residual(v):
        x, y, z, f = v
        f = max(0.0, f) if bent else 0.0
        b = dict(bones)
        b[arm + "_arm"] = arm_bones(x, y, z, f, tw, arm, bent)
        M, T, fr = limb_frame(b, arm, bent)
        r = list(T + M @ local - target)
        # never through the head, the body or the legs, nor looking down: a graze is a cost, past 0.8 px a wall
        for d_ in (arm_depths(fr, arm, bent), arm_depths(PC.part_frames(b, 30.0), arm, bent)):
            r.extend(1.0 * d_)
            r.extend(4.0 * np.maximum(0.0, d_ - 1.2))
        if bent:
            r.append(0.2 * max(0.0, -v[3]))  # no elbow bends backwards
            r.append(0.004 * max(0.0, v[3] - 150))
        if roll_cost is not None:
            r.append(roll_cost(M))
        r.extend(0.01 * (v[:free] - np.asarray(prev)[:free]))  # near the last key's (no flips between keys)
        return np.array(r)

    lam = 1e-2
    r = residual(v)
    for _ in range(iters):
        J = np.zeros((len(r), free))
        for i in range(free):
            dv = np.zeros(4)
            dv[i] = 0.5
            J[:, i] = (residual(v + dv) - r) / 0.5
        g = J.T @ r
        H = J.T @ J
        step = np.zeros(4)
        step[:free] = np.linalg.solve(H + lam * np.diag(np.diag(H) + 1e-6), -g)
        v2 = v + np.clip(step, -25, 25)
        r2 = residual(v2)
        if r2 @ r2 < r @ r:
            v, r, lam = v2, r2, lam * 0.5
            if np.linalg.norm(step) < 1e-3:
                break
        else:
            lam *= 4
    v[3] = max(0.0, v[3]) if bent else 0.0
    return v, float(np.linalg.norm(r[:3]))


def root_of(bones, pitch=0.0):
    """The figure's root (its "body" bone: the lean about the waist, the step), as power_casts.part_frames makes it."""
    bp, br = bones["body"]["position"], bones["body"]["rotation"]
    val = lambda ch, i: 0.0 if ch[i].off else ch[i].at(pitch)  # noqa: E731
    T = np.array([val(bp, 0), PC.WAIST - val(bp, 1), val(bp, 2)])
    M = np.array(PC.R._rot(*PC._deg((val(br, 0), val(br, 1), val(br, 2)))))
    return M, T


def to_world(bones, q):
    M, T = root_of(bones)
    return T + M @ (np.asarray(q) - np.array([0.0, PC.WAIST, 0.0]))


def hold_of(display, arm):
    """(turn, grip): the item-to-arm-frame turn of a hold (ItemInHandLayer's X -90, Y 180, then the display, a left
    hand's mirrored) and where it puts the weapon's grip in the arm's frame (px)."""
    import anim_lab as L
    import arms_holds as AH
    (ax, ay, az), t, sc = display
    if arm == "left":
        ay, az, t = -ay, -az, (-t[0], t[1], t[2])
    Rd = L.MR.euler(ax, ay, az)
    hold = L.rx(-90) @ L.ry(180)
    o = np.array([(-1.0 if arm == "left" else 1.0) / 16, 2 / 16, -10 / 16])
    grip = 16 * hold @ (o + np.asarray(t) / 16 + sc * Rd @ (AH.GRIP_ITEM - 0.5))
    return hold @ Rd, grip


def turn_angle(R):
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(R) - 1) / 2))))


# ---- first person's blade, carried onto the body -------------------------------------------------------------------

class FirstPerson:
    """A style's forged weapon in first person: where its grip (the item point the third-person wrist turns about) is
    and how the item is turned, in the view, at any tick of a move (fp_swings.json's keys, as the game samples them)."""

    def __init__(self, style, swings):
        import anim_lab as L
        import arms_holds as AH
        self.L, self.style, self.swings = L, style, swings
        wid = "gatewrought_" + style
        (ax, ay, az), t, sc = L.MR.held(wid)
        self.Rh, self.th, self.sh = L.MR.euler(ax, ay, az), np.asarray(t) / 16.0, sc
        self.G = AH.GRIP_ITEM
        self.H0 = np.array([L.HX, L.HY, L.HZ])

    def pose(self, move, t):
        d, R = self.L.new_pose(self.style, move, float(t), self.swings)
        grip = R @ (self.Rh @ (self.G - 0.5) * self.sh + self.th) + self.H0 + d
        return grip, R @ self.Rh  # the grip (view, blocks), item -> view

    def point(self, move, t, item_pt):
        grip, Q = self.pose(move, t)
        return grip + Q @ (np.asarray(item_pt) - self.G) * self.sh


def frame_up(d):
    """An orthonormal frame (columns: d, up across it, the side) keeping the view's up."""
    d = d / np.linalg.norm(d)
    up = np.array([0.0, 1.0, 0.0]) - d * d[1]
    up /= np.linalg.norm(up)
    return np.stack([d, up, np.cross(d, up)], axis=1)


def weapon_cloud(style):
    """Every weapon of the style at once (item space), and the forged one's length (item units, grip to tip)."""
    import anim_lab as L
    groups = L.style_weapons(style)
    pts = np.concatenate([g[2] for g in groups])
    return np.unique(np.round(pts, 4), axis=0), groups[0][1]


def body_depth(frames_list, pts_model, grip, parts=("head", "torso", "right_leg", "left_leg")):
    """How deep (px) any of the weapon's points goes into the head, the torso or the legs over several looks; the
    grip's own neighbourhood is the hand's."""
    import anim_lab as L
    P = pts_model[np.linalg.norm(pts_model - grip, axis=1) > L.BODY_OWN_HAND]
    worst = 0.0
    for fr in frames_list:
        for part in parts:
            Mp, Tp = (np.array(v) for v in fr[part])
            _, (ox, oy, oz, ow, oh, od), _ = PC.PARTS[part]
            Lc = (P - Tp) @ Mp
            d = np.minimum.reduce([Lc[:, 0] - ox, ox + ow - Lc[:, 0], Lc[:, 1] - oy, oy + oh - Lc[:, 1],
                                   Lc[:, 2] - oz, oz + od - Lc[:, 2]])
            worst = max(worst, float(d.max()))
    return worst


def ramp(m, wt, t):
    """How much of the move third person plays from the blade (0: the base clip's rest, 1: the blade's path): in over
    the wind-up's draw (a move loosed from a held draw starts on it), out as it comes home."""
    import anim_lab as L
    c, hit, ea, fe, total = L.phases(m, wt)
    if m["windup"] <= 0:
        w_in = 1.0
    else:
        w_in = L.ease_in_out_sine(min(1.0, t / max(0.5, c * wt["draw"])))
    w_out = 1.0 if t <= fe else 1.0 - L.ease_in_out_sine(min(1.0, (t - fe) / max(1e-6, total - fe)))
    return w_in * w_out


def rig_move(style, mv, m, swings):
    """Solves one move: {"bent": {...}, "straight": {...}} each with the arms' terms, the chest's twist and the wrist's
    turns per key, and the check numbers (the hands' miss, the weapon's depth into the body)."""
    import anim_lab as L
    import arms_holds as AH
    import arms_moves as A
    cfg = STYLES[style]
    wt = L.WEIGHTS[L.WEIGHT_OF.get(style, "blade")]
    own = f"arms_{style}_{mv}"
    base = own if own in A.BASE else f"arms_{mv}"
    clips = {False: A.BASE[base], True: A.BASE[base + "_bent"]}
    fp = FirstPerson(style, swings)
    wid = "gatewrought_" + style
    disp = L.display_3p(wid, "right")
    turn_r, grip_local = hold_of(disp, "right")
    sc3 = disp[2]
    cloud, length = weapon_cloud(style)
    rel = (cloud - AH.GRIP_ITEM) * sc3 * 16  # the weapons about the grip, px (item frame)
    c, hit, ea, fe, total = L.phases(m, wt)
    ts = np.arange(0.0, total + 1e-6, DT)

    # the carry: first person's pivot->target line onto the shoulder->front-of-the-face line, the view's up kept
    mid = L.WORK.get(style) == "inner" and 1.0 or L.MID
    blade_pt = AH.GRIP_ITEM + AH.AXIS_ITEM * length * mid
    fp_hit = fp.point(mv, hit, blade_pt)
    d_fp = fp_hit - L.PIVOT
    S0_v = B @ (np.asarray(cfg["pivot"], dtype=float) - EYE) / 16.0
    reach = cfg["radius"]  # px: the pivot to the grip at the hit
    lever = reach + mid * length * sc3 * 16  # px: the pivot to the blade's striking point
    aim = L.TARGET / np.linalg.norm(L.TARGET)
    b_ = float(S0_v @ aim)
    dist = b_ + math.sqrt(max(0.0, b_ * b_ - (S0_v @ S0_v - (lever / 16) ** 2)))
    T3_v = aim * dist
    Ra = frame_up(T3_v - S0_v) @ frame_up(d_fp).T
    L_hit = float(np.linalg.norm(fp.pose(mv, hit)[0] - L.PIVOT))

    # each key's blade (unposed model): the grip on the carried lever, the item's turn
    path = []
    for t in ts:
        grip_v, Qv = fp.pose(mv, t)
        lv = grip_v - L.PIVOT
        a3 = reach * float(np.linalg.norm(lv)) / L_hit
        g_v = S0_v + Ra @ (lv / np.linalg.norm(lv)) * a3 / 16.0
        path.append((B @ g_v * 16.0 + EYE, B @ Ra @ Qv, Ra @ lv))
    # the chest turns with the lever, half a tick ahead of it (the body leads, the blade follows)
    yaw = np.array([math.degrees(math.atan2(lv[0], -lv[2])) for _, _, lv in path])
    lead = int(round(0.5 / DT))
    yaw_led = np.concatenate([yaw[lead:], np.repeat(yaw[-1:], lead)])
    w = np.array([ramp(m, wt, t) for t in ts])

    out = {}
    for bent in (True, False):
        clip = clips[bent]
        bones0 = [clip.sample(float(t)) for t in ts]
        twists = []
        for i, b0 in enumerate(bones0):
            tw_rig = float(np.clip(cfg["chest"] * yaw_led[i], -55.0, 55.0))
            twists.append((1 - w[i]) * twist_of(b0) + w[i] * tw_rig)
        bones = [with_twist(b0, tw) for b0, tw in zip(bones0, twists)]
        # the rest's grip and turn (the base clip's own hand, no wrist), blended into the blade's path by the ramp
        targets = []
        for i, b in enumerate(bones):
            Mr, _ = root_of(b)
            Mb, Tb, _ = limb_frame(bones0[i], "right", bent)
            rest_g, rest_Q = Tb + Mb @ grip_local, Mb @ turn_r
            # carried by the chest but level with the view: first person's swing is the view's, and the view stays level
            # when the body leans (a slide leaning back, a fall, a lunge), so the swing doesn't tilt with it
            pv = np.asarray(cfg["pivot"], dtype=float)
            g_w, Q_w = to_world(b, pv) + (path[i][0] - pv), path[i][1]
            Mr = np.eye(3)
            q = L.slerp(L.mat_quat(rest_Q), L.mat_quat(Q_w), w[i])
            targets.append((rest_g * (1 - w[i]) + g_w * w[i], L.quat_mat(q), Mr))
        # clearance: the grip's shift per key and, after the blow, the follow-through's lift, the whole move at once
        # (Viterbi): the blade's turn untouched through the wind-up, the drive and the hit
        opts = [(s_, l_) for l_ in LIFTS for s_ in SHIFTS]
        K = len(opts)
        lifts = {l_: L.rx(-l_) for l_ in LIFTS}  # about the figure's own side-to-side axis: what is ahead rises
        cost = np.zeros((len(ts), K))
        for i, (g, Q, Mr) in enumerate(targets):
            frs = [PC.part_frames(bones[i], p) for p in (0.0, 30.0)]
            s_r, s_l = np.array(frs[0]["right_arm"][1]), np.array(frs[0]["left_arm"][1])
            P = to_world(bones[i], np.asarray(cfg["pivot"], dtype=float))
            free = ts[i] > ea or ts[i] < c  # the follow-through, or the wind-up before the drive
            for k, (s_, l_) in enumerate(opts):
                if l_ and not free:
                    cost[i, k] = 1e6
                    continue
                Rl = Mr @ lifts[l_] @ Mr.T
                gk = P + Rl @ (g - P) + Mr @ s_ * min(1.0, 2.0 * w[i])
                Qk = Rl @ Q
                axis_w = Qk @ AH.AXIS_ITEM
                axis_w /= np.linalg.norm(axis_w)
                pts = gk + rel @ Qk.T
                # a blade by the face is the worst there is: the head counts most
                depth = max(2.5 * body_depth(frs, pts, gk, ("head",)), cfg["torso"] * body_depth(frs, pts, gk, ("torso",)),
                            body_depth(frs, pts, gk, ("right_leg", "left_leg")))
                # both hands must reach: the grip from the sword arm's shoulder, the handle from the other's
                far = max(0.0, float(np.linalg.norm(gk - s_r)) - REACH) + w[i] * max(
                    0.0, float(np.linalg.norm(gk + axis_w * cfg["second"] - s_l)) - REACH)
                cost[i, k] = 30.0 * max(0.0, depth - 0.3) + 25.0 * far + 0.15 * float(np.linalg.norm(s_)) + 0.06 * l_
        trans = np.array([[0.4 * float(np.linalg.norm(a[0] - b[0])) + 0.08 * abs(a[1] - b[1]) for b in opts] for a in opts])
        acc, back = cost[0].copy(), np.zeros((len(ts), K), dtype=int)
        for i in range(1, len(ts)):
            tot = acc[:, None] + trans
            back[i] = np.argmin(tot, axis=0)
            acc = tot[back[i], np.arange(K)] + cost[i]
        pick = [int(np.argmin(acc))]
        for i in range(len(ts) - 1, 0, -1):
            pick.append(int(back[i][pick[-1]]))
        pick.reverse()
        shifts = np.array([opts[k][0] * min(1.0, 2.0 * w[i]) for i, k in enumerate(pick)])
        lifted = np.array([opts[k][1] * w[i] for i, k in enumerate(pick)])
        # a light smoothing: the shift and the lift glide between keys
        sm, lm = shifts.copy(), lifted.copy()
        for i in range(1, len(ts) - 1):
            sm[i] = 0.25 * shifts[i - 1] + 0.5 * shifts[i] + 0.25 * shifts[i + 1]
            lm[i] = 0.25 * lifted[i - 1] + 0.5 * lifted[i] + 0.25 * lifted[i + 1]
        for i, (g, Q, Mr) in enumerate(targets):
            P = to_world(bones[i], np.asarray(cfg["pivot"], dtype=float))
            Rl = Mr @ L.rx(-lm[i]) @ Mr.T
            targets[i] = (P + Rl @ (g - P), Rl @ Q, Mr)
        # the arms: the main hand on the grip, the off hand on the handle; the wrist turns the rest of the way. Two
        # passes: where the off hand can't hold the handle (a follow-through carried round to the far side, a low sweep
        # out of a slide) it lets go cleanly, back toward its own place, rather than float beside the hilt
        def solve_arms(hold):
            res = []
            prev_r, prev_l = terms_of(bones0[0], "right", bent), terms_of(bones0[0], "left", bent)
            for i, (g, Q, Mr) in enumerate(targets):
                b = bones[i]
                if w[i] < 1e-3:
                    b2 = dict(b)
                    b2["right_arm"], b2["left_arm"] = bones0[i]["right_arm"], bones0[i]["left_arm"]
                    vr, vl = terms_of(bones0[i], "right", bent), terms_of(bones0[i], "left", bent)
                    res.append((vr, vl, 0.0, 0.0, np.eye(3), b2, 0.0))
                    prev_r, prev_l = vr, vl
                    continue
                gk = g + Mr @ sm[i]

                def roll_r(M):
                    return 0.02 * turn_angle((M @ turn_r).T @ Q)
                vr, miss = ik_arm(b, "right", bent, gk, grip_local, prev_r, roll_r)
                b2 = dict(b)
                b2["right_arm"] = arm_bones(*vr, twists[i], "right", bent)
                axis_w = Q @ AH.AXIS_ITEM
                axis_w /= np.linalg.norm(axis_w)
                Ml, Tl, _ = limb_frame(bones0[i], "left", bent)
                own_l = Tl + Ml @ HAND_LOCAL["left"]
                M, T, _ = limb_frame(b2, "right", bent)
                handle = T + M @ grip_local + axis_w * cfg["second"]  # on the weapon where the sword hand really put it
                target_l = own_l + (handle - own_l) * w[i] * hold[i]

                def roll_l(M_):
                    return 2.0 * float(M_[:, 1] @ axis_w)
                vl, miss_l = ik_arm(b2, "left", bent, target_l, HAND_LOCAL["left"], prev_l, roll_l)
                b2["left_arm"] = arm_bones(*vl, twists[i], "left", bent)
                s_l = np.array(PC.part_frames(b2)["left_arm"][1])
                res.append((vr, vl, miss, miss_l, (M @ turn_r).T @ Q, b2, float(np.linalg.norm(handle - s_l))))
                prev_r, prev_l = vr, vl
            return res

        first = solve_arms(np.ones(len(ts)))
        # the fist (4 px across) still closes on the hilt 2.4 px off its line; past 3.6 it lets go
        grip = np.array([float(np.clip((3.6 - r[3]) / 1.2, 0.0, 1.0)) for r in first])
        n = len(grip)
        grip = np.array([grip[max(0, i - 1):i + 2].min() for i in range(n)])  # let go a little early, take hold late
        grip = np.array([grip[max(0, i - 2):i + 3].mean() for i in range(n)])  # and glide between
        solved = solve_arms(grip) if grip.min() < 0.999 else first
        rec = {"right": [], "left": [], "twist": [round(float(x), 2) for x in twists], "wrist": [], "miss": 0.0, "miss_off": 0.0,
               "depth": 0.0, "hilt": 0.0, "held": 0.0, "keys": []}
        ahead = (cloud - AH.GRIP_ITEM) @ AH.AXIS_ITEM > 0
        both = [i for i in range(n) if w[i] > 0.999]
        rec["held"] = round(float(np.mean([grip[i] for i in both])) if both else 1.0, 2)
        rec["reach_off"] = [round(float(r[3]), 2) for r in first]  # how far the off hand fell short when asked to hold
        rec["dist_off"] = [round(r[6], 2) for r in first]  # the handle from the off arm's shoulder
        for i, (vr, vl, miss, miss_l, W, b2, _) in enumerate(solved):
            rec["right"].append([round(float(x), 2) for x in vr])
            rec["left"].append([round(float(x), 2) for x in vl])
            rec["wrist"].append([round(float(x), 4) for x in L.mat_quat(W)])
            rec["miss"] = max(rec["miss"], miss)
            if w[i] * grip[i] > 0.999:
                rec["miss_off"] = max(rec["miss_off"], miss_l)
            # the check: the weapon as the game will draw it (the solved arm, the wrist), into the body: the blade
            # (grip to tip) and the hilt behind the hands apart (a two-handed hilt's pommel by the belly is how one
            # is held; a blade in the body never is)
            M, T, _ = limb_frame(b2, "right", bent)
            G = T + M @ grip_local
            frs = [PC.part_frames(b2, p) for p in (0.0, 30.0)]
            P = G + rel @ (M @ turn_r @ W).T
            dk, dh = body_depth(frs, P[ahead], G), body_depth(frs, P[~ahead], G)
            rec["depth"] = max(rec["depth"], dk)
            rec["hilt"] = max(rec["hilt"], dh)
            rec["keys"].append([round(float(miss), 2), round(float(miss_l), 2), round(dk, 2), round(float(w[i]), 2),
                                round(float(grip[i]), 2), round(dh, 2)])
        for k in ("miss", "miss_off", "depth", "hilt"):
            rec[k] = round(rec[k], 2)
        out["bent" if bent else "straight"] = rec
    return out


def rig_one(args):
    style, mv = args
    import anim_lab as L
    m = L.table()[style]["moves"][mv]
    return style, mv, rig_move(style, mv, m, L.load_swings())


def bake_all(styles=None):
    """Solves every rigged style's moves in parallel into DATA; returns the check numbers."""
    import anim_lab as L
    from multiprocessing import Pool
    styles = styles or list(STYLES)
    jobs = [(s, mv) for s in styles for mv, m in L.table()[s]["moves"].items() if "swing" in m]
    old = load()
    with Pool(min(6, len(jobs))) as pool:
        for style, mv, res in pool.imap_unordered(rig_one, jobs):
            old.setdefault(style, {})[mv] = res
    old = {s: old[s] for s in STYLES if s in old}
    with open(DATA, "w") as fh:
        json.dump({"generatedBy": "tools/art/specs/arms_rig4.py bake", "dt": DT, "styles": old}, fh, separators=(",", ":"))
    return old


def load():
    return json.load(open(DATA))["styles"] if os.path.exists(DATA) else {}


# ---- the clips ------------------------------------------------------------------------------------------------------

class BakedClip(PC.Clip):
    """A clip whose keys are bones already (the rig's), linear between."""

    def __init__(self, name, frames, length, end=None, note="", bent=False, loop=False):
        self._frames = frames
        super().__init__(name, [(t, None, "linear") for t, _ in frames], length, loop=loop, end=end, note=note, bent=bent)

    def baked(self):
        return [(t, b, "linear") for t, b in self._frames]


def rigged_bones(base, rec, i, bent):
    t = i * DT
    b = with_twist(base.sample(t), rec["twist"][i])
    b["right_arm"] = arm_bones(*rec["right"][i], rec["twist"][i], "right", bent)
    b["left_arm"] = arm_bones(*rec["left"][i], rec["twist"][i], "left", bent)
    return b


def make_clips(name, base_straight, base_bent, data, note=""):
    """The rigged clip and its bent twin: the move's own legs, head and step; the rig's chest and arms."""
    out = []
    for base, bent in ((base_straight, False), (base_bent, True)):
        rec = data["bent" if bent else "straight"]
        frames = [(i * DT, rigged_bones(base, rec, i, bent)) for i in range(len(rec["right"]))]
        end = frames[-1][0]
        out.append(BakedClip(name + ("_bent" if bent else ""), frames, max(base.length, end + 4), end, note, bent))
    return out


def charge_clips(style, base_straight, base_bent, data):
    """The style's own held draw (its rigged heavy's first key, trembling) and its settle: so a charge holds the very
    pose its heavy is loosed from."""
    out = []
    for base, bent in ((base_straight, False), (base_bent, True)):
        rec = data["bent" if bent else "straight"]
        b0 = rigged_bones(base, rec, 0, bent)

        def shaken(dx):
            b = dict(b0)
            ra = b0["right_arm"]
            b["right_arm"] = {**ra, "rotation": (PC.V(ra["rotation"][0].c + dx), ra["rotation"][1], ra["rotation"][2])}
            return b
        suffix = "_bent" if bent else ""
        name = f"arms_{style}_heavy_charge"
        out.append(BakedClip(name + suffix, [(0.0, b0), (2.0, shaken(1.5)), (4.0, b0)], 4.0, note=f"{style}: the heavy's draw held",
                             bent=bent, loop=True))
        out.append(BakedClip(name + "_end" + suffix, [(0.0, b0)], 5.0, end=0.0, note="settles out of " + name, bent=bent))
    return out


# ---- the review sheet: first person beside third person, at the same moments ----------------------------------

VIEWS = (("behind", 160.0, 12.0), ("side", -90.0, 6.0))  # behind-right (Shoulder Surfing's camera), the right side


def moments(m, wt):
    import anim_lab as L
    c, hit, ea, fe, total = L.phases(m, wt)
    return [("draw", c * 0.5), ("cocked", c), ("drive", (c + hit) / 2), ("hit", hit), ("through", ea), ("follow", fe)]


def sheet(out, style, mv, data=None, wid=None):
    """One move: first person's blade (as the game draws it) over third person from behind-right and the side (the
    bent clip, Bendable Cuboids'), at the draw, the cock, the drive, the hit, through and the follow-through."""
    from PIL import Image, ImageDraw
    import anim_lab as L
    import arms_moves as A
    m = L.table()[style]["moves"][mv]
    wt = L.WEIGHTS[L.WEIGHT_OF.get(style, "blade")]
    wid = wid or "gatewrought_" + style
    if data is None:
        data = load()[style][mv]
    own = f"arms_{style}_{mv}"
    base = own if own in A.BASE else f"arms_{mv}"
    _, bent = make_clips(own, A.BASE[base], A.BASE[base + "_bent"], data)
    keys = data["bent"]["wrist"]
    held = L.Held(wid)
    swings = L.load_swings()
    cw, chh = 300, 220
    beats = moments(m, wt)
    rows = []
    # first person: the blade in the view (the crosshair marked)
    row = []
    for name, t in beats:
        d, R = L.new_pose(style, mv, t, swings)
        im = L.frame(held, d, R).convert("RGB").resize((cw, int(cw * L.H / L.W)))
        dr = ImageDraw.Draw(im)
        cx, cy = im.width // 2, im.height // 2
        dr.line([(cx - 6, cy), (cx + 6, cy)], fill=(255, 255, 255))
        dr.line([(cx, cy - 6), (cx, cy + 6)], fill=(255, 255, 255))
        dr.text((6, 4), f"{name} t={t:.2f}", fill=(255, 255, 255))
        row.append(im)
    rows.append(row)
    for label, yaw, pitch in VIEWS:
        b0 = PC.strip_bounds([bent], yaw, pitch)
        bounds = (b0[0] - 14, b0[1] + 14, b0[2] - 16, b0[3] + 6)
        row = []
        for name, t in beats:
            im = L.figure_3p(bent, t, [(wid, "right", L.wrist_now(keys, t))], yaw, pitch, 4.0, bounds, lower=True)
            im.thumbnail((cw, chh * 2))
            dr = ImageDraw.Draw(im)
            dr.text((6, 4), f"{label}", fill=(20, 20, 20))
            row.append(im)
        rows.append(row)
    W_ = cw * len(beats)
    Hs = [max(i.height for i in r) for r in rows]
    img = Image.new("RGB", (W_, sum(Hs) + 24), (24, 24, 28))
    ImageDraw.Draw(img).text((8, 6), f"{style} {mv}: first person (top), third person behind-right and side (bent)", fill=(230, 230, 230))
    y = 24
    for r, h in zip(rows, Hs):
        for i, im in enumerate(r):
            img.paste(im, (i * cw, y))
        y += h
    img.save(out)
    return out


def overview(out, style, view="behind", cw=200):
    """Every move of a style, one row each, from one view (the bent clip) at its six moments."""
    from PIL import Image, ImageDraw
    import anim_lab as L
    import arms_moves as A
    data = load()[style]
    wt = L.WEIGHTS[L.WEIGHT_OF.get(style, "blade")]
    wid = "gatewrought_" + style
    _, yaw, pitch = next(v for v in VIEWS if v[0] == view)
    rows = []
    for mv, d in data.items():
        m = L.table()[style]["moves"][mv]
        own = f"arms_{style}_{mv}"
        base = own if own in A.BASE else f"arms_{mv}"
        _, bent = make_clips(own, A.BASE[base], A.BASE[base + "_bent"], d)
        b0 = PC.strip_bounds([bent], yaw, pitch)
        bounds = (b0[0] - 14, b0[1] + 14, b0[2] - 16, b0[3] + 6)
        row = []
        for name, t in moments(m, wt):
            im = L.figure_3p(bent, t, [(wid, "right", L.wrist_now(d["bent"]["wrist"], t))], yaw, pitch, 3.0, bounds, lower=True)
            im.thumbnail((cw, cw))
            ImageDraw.Draw(im).text((4, 2), f"{mv} {name}", fill=(235, 235, 235))
            row.append(im)
        rows.append(row)
    h = max(i.height for r in rows for i in r)
    img = Image.new("RGB", (cw * 6, h * len(rows)), (24, 24, 28))
    for y, r in enumerate(rows):
        for x, im in enumerate(r):
            img.paste(im, (x * cw, y * h))
    img.save(out)
    return out


def before_after(out, style, shipped_swings, view="behind", cw=170):
    """Every move of a style, one row each: the shipped third person (the kind's poses, the old wrist) at the cock, the
    hit and the follow-through, then the same three from first person's blade (the bent clips, from behind-right)."""
    from PIL import Image, ImageDraw
    import anim_lab as L
    import arms_moves as A
    data = load()[style]
    wt = L.WEIGHTS[L.WEIGHT_OF.get(style, "blade")]
    wid = "gatewrought_" + style
    _, yaw, pitch = next(v for v in VIEWS if v[0] == view)
    rows = []
    for mv, d in data.items():
        m = L.table()[style]["moves"][mv]
        own = f"arms_{style}_{mv}"
        shared = mv in ("undercut", "plunge", "slipcut")
        old = A.BASE[(own if own in A.BASE and not shared else f"arms_{mv}") + "_bent"]
        new = A.CLIPS[own + "_bent"]
        old_keys = shipped_swings[style]["moves"][mv].get("wrist3", {}).get("right")
        beats = [b for b in moments(m, wt) if b[0] in ("cocked", "hit", "follow")]
        b0 = PC.strip_bounds([old, new], yaw, pitch)
        bounds = (b0[0] - 14, b0[1] + 14, b0[2] - 16, b0[3] + 6)
        row = []
        for label, clip, keys in (("before", old, old_keys), ("after", new, d["bent"]["wrist"])):
            for name, t in beats:
                im = L.figure_3p(clip, t, [(wid, "right", L.wrist_now(keys, t))], yaw, pitch, 3.0, bounds, lower=True)
                im.thumbnail((cw, cw))
                ImageDraw.Draw(im).text((4, 2), f"{mv} {name} ({label})", fill=(235, 235, 235))
                row.append(im)
        rows.append(row)
    h = max(i.height for r in rows for i in r)
    img = Image.new("RGB", (cw * 6 + 12, h * len(rows) + 22), (24, 24, 28))
    dr = ImageDraw.Draw(img)
    dr.text((8, 5), f"{style}: shipped third person (left three) and from first person's blade (right three), from behind-right",
            fill=(235, 235, 235))
    for y, r in enumerate(rows):
        for x, im in enumerate(r):
            img.paste(im, (x * cw + (12 if x >= 3 else 0), 22 + y * h))
    dr.line([(cw * 3 + 6, 22), (cw * 3 + 6, img.height)], fill=(200, 160, 60), width=2)
    img.save(out)
    return out


if __name__ == "__main__":
    if len(sys.argv) > 3 and sys.argv[1] == "overview":
        print(overview(sys.argv[2], sys.argv[3], *(sys.argv[4:5] or ["behind"])))
        raise SystemExit(0)
    if len(sys.argv) > 4 and sys.argv[1] == "sheet":
        print(sheet(sys.argv[2], sys.argv[3], sys.argv[4]))
        raise SystemExit(0)
    if len(sys.argv) > 1 and sys.argv[1] == "bake":
        res = bake_all(sys.argv[2:] or None)
        for s_, mvs in res.items():
            for mv_, r in mvs.items():
                print(f"{s_:11s} {mv_:10s} " + "  ".join(
                    f"{k}: hands {v['miss']:.2f}/{v['miss_off']:.2f} px (both {v['held']:.0%}), blade {v['depth']:.2f}"
                    f" hilt {v['hilt']:.2f} px in" for k, v in r.items()))
    else:
        raise SystemExit(__doc__)
