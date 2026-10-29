"""Swing Studio's choreography (rounds 11-13): a style's moves set by hand, both hands at once, as a few key poses on a
timeline, in place of the rules that worked each pose out (anim_lab.rig_pose, swing_studio's give-way, haul and pull).

Euan 2026-10-29, after round 10: "it still feels like it's going to unnecessary distances sometimes... the right arm
goes way to the left for some reason, or sometimes it's just not aligned... maybe the entire approach is wrong?" It was:
every pose came out of rules (cut edge to edge, give way until clear, haul back to the next set), each fixing one
complaint and moving something else, and the hand not striking had no place of its own. After round 11: "it feels
more like they are spinning and making circles than stabbing and slashing". After round 12, which took the spin out:
"it's less about turning the wrist, and more about the weapon not doing clean stabs and swipes like other weapons do".
Rounds 11-12 had kept every stroke small and slow to pass the smoothness judges: a stab that floated up to the
crosshair and hung there, a cut that turned in the lower right corner, a third as wide as the longsword's and half as
fast. Round 13 sets each form on the weapon that reads clean (`swing_chain.py like` holds it to it):

- a stab is the rapier's: drawn back toward the eye on the line to its point under the crosshair (the blade grows),
  driven out along that line in a frame (it shrinks, the point held on its mark), a frame there, snapped back along it
  (`line`: between two keys on one line the blade only slides);
- a cut is the longsword's: set high in view on its hand's side, one sweep through the target across the screen,
  fastest at the hit, carried on past it, then home low under the view (`arc`: the Swing Studio lever, the blade's
  middle about the eye, its edge leading); a chop the same from overhead;
- a chain strike starts in the holding stance (rest), and the hand that strikes next is back in it by the chain's next
  press (`describe` shows when), so whatever the clicking, the next strike takes over from the pose it was set from;
- the hand not striking gives a little to a stab, and is out of a cut's way before it sweeps past: out to its own side
  and up for a falling cut (it ends under it), a little down for the level one; a rising cut and a chop pass clear.

A spot is where the grip sits on the screen (ndc), how far ahead of the eye, which way the blade points on the screen
(degrees from the left, as the guard's), how far its tip leans away along the eye's ray, and its roll. Each hand's track
is set for a right hand: the runtime mirrors the off hand's. `swing_chain.py twist` measures the turn about the length.

    python3 tools/art/swing_choreo.py describe            the stance and every spot, as seen
    python3 tools/art/swing_choreo.py quick BASE OUT      a trial: BASE's swings with the set moves baked again
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_lab as L  # noqa: E402

F_ = 1 / math.tan(math.radians(35))
ASP = 16 / 9
H0 = np.array([L.HX, L.HY, L.HZ])


def spot(g, z, ang, lean, roll=0.0):
    """A key pose: the grip at ndc {@code g}, {@code z} ahead of the eye, the blade at {@code ang} on the screen leant
    {@code lean} away along the eye's ray, turned there from the holding stance by the least turn, then rolled
    {@code roll} degrees about its own length (0: the edge stays as the stance holds it)."""
    return dict(kind="spot", g=g, z=z, ang=ang, lean=lean, roll=roll)


def line(tip, depth, src, roll=0.0):
    """A thrust's line (round 13, the rapier's): the point at the hit at ndc {@code tip}, {@code depth} ahead of the
    eye, the blade laid on the line to it from {@code src} (view space), by the least turn from the stance. A key on it
    is (name, back): the whole blade drawn back along its own line, so between two keys on one line it only slides, the
    point held on its mark (on the screen it hardly moves: the blade grows as it comes back, shrinks as it goes out)."""
    return dict(kind="line", tip=tip, depth=depth, src=np.array(src, dtype=float), roll=roll)


def arc(way, target=(0.0, -0.20, -1.15), pivot=(0.62, -0.52, -0.30), mid=L.MID, eye=1.0):
    """A cut's lever (round 13, the longsword's: swing_studio.cut_pose): the blade's middle swept on a circle about the
    eye through {@code target} (view space) in the plane the screen sees along {@code way}, the blade pointing out from
    a shoulder at {@code pivot}, its edge leading. A key on it is (name, degrees[, lag[, roll]]): 0 crosses the target,
    below 0 drawn back; lag trails the blade behind the arm (the wrist), roll turns it about its length. Between two
    keys on one lever the blade sweeps round it, so a stroke reads as one line across the screen."""
    return dict(kind="arc", way=np.array([way[0], way[1], 0.0]) / math.hypot(*way), target=np.array(target, dtype=float),
                pivot=np.array(pivot, dtype=float), mid=mid, eye=eye)


REST = None  # the holding stance: vanilla's hold, the hand at its rest

# the daggers (each hand's own: a right hand's poses; the left's are mirrored by the game). Round 13: the stabs on the
# rapier's line, the cuts on the longsword's lever (swing_chain.py like holds each to them)
DAGGERS = {
    # the stabs: drawn back toward the eye on the line to the point under the crosshair, out along it, back along it
    "line_hi": line((-0.03, -0.05), 1.15, (0.34, -0.40, -0.30)),
    "line_lo": line((-0.02, -0.10), 1.10, (0.22, -0.66, -0.34)),
    "line_pair": line((0.07, -0.07), 1.10, (0.36, -0.42, -0.30)),
    "line_deep": line((0.06, -0.04), 1.30, (0.36, -0.44, -0.30)),
    "line_rip": line((-0.02, -0.03), 1.25, (0.34, -0.40, -0.30)),
    # the hand not striking gives a little as the other drives (the body turns into the blow)
    "give": spot((0.74, -1.24), 0.63, 69.0, -2.0),
    # out of a cut's way as it sweeps past: out to its own side and up (the falling cuts end under it), or a little
    # down (the level one passes over it); a rising cut and a chop pass clear of the stance
    "aside": spot((1.30, -0.80), 0.55, 48.0, -6.0),
    "low": spot((1.00, -1.60), 0.50, 55.0, -6.0),
    # the cuts: set high in view, one sweep through the target
    "cut_3": arc((-0.85, -0.52)),
    "cut_5": arc((-0.85, -0.52)),
    "cut_slip": arc((-1.0, -0.15), (0.0, -0.12, -1.15)),
    "cut_under": arc((-0.8, 0.6), (0.0, -0.15, -1.15)),
    "cut_art": arc((-0.9, -0.45)),
    "chop_p": arc((-0.21, -0.98), eye=0.5),
    "chop_x": arc((-0.21, -0.98), eye=0.5),
}

# per move: the striking hand's track and the other's, (tick, pose, ease into it[, bulge[, turn ease]]); a pose is REST,
# a spot's name, (a line's name, drawn back) or (a lever's name, degrees, lag, roll). The eases: "io" in and out (leaves
# and settles), "in" gathering speed into it (the drive), "out" losing it (the carry, the snap back), "lin"; "late" (the
# turn only): a way home that keeps the blade on the line it came out on and turns it into the stance last.


def STAB(ln, hit=1.0, back=0.42, hold=0.33, snap=0.67, home=1.0, start=None, to=0.75):
    """A thrust on line {@code ln} landing at {@code hit}: drawn back {@code back} blocks along it by half the wind-up
    and a hair further to the drive, out to the point in the wind-up's last frame, a hair past it for {@code hold},
    snapped back along the line to {@code to} of the draw in {@code snap}, then into the stance in {@code home}."""
    return [(0.0, start or REST, "io"), (hit * 0.5, (ln, back - 0.04), "io"), (max(hit * 0.5, hit - 0.33), (ln, back), "io"),
            (hit, (ln, 0.0), "in"), (hit + hold, (ln, -0.03), "out"), (hit + hold + snap, (ln, back * to), "io"),
            (hit + hold + snap + home, REST, "io")]


def CUT(c, hit=1.0, pre=32.0, stop=44.0, set_=0.67, lag=6.0, carry=0.5, home=2.4, start=None, low=-0.2):
    """A cut on lever {@code c} landing at {@code hit}: set {@code pre} degrees back (the wrist trailing {@code lag})
    by {@code set_} and a hair further to the drive, through the target on the hit, fastest there (the wrist whipping
    square on the turn's own ease), carried on to {@code stop} in {@code carry}, then home in {@code home}, bowed
    {@code low} under the view."""
    return [(0.0, start or REST, "io"), (set_, (c, -pre, lag), "io"), (max(set_, hit - 0.5), (c, -pre - 3, lag), "io"),
            (hit, (c, 0.0), "in", 0.0, "out"), (hit + carry, (c, stop), "out"), (hit + carry + 0.1, (c, stop + 1), "out"),
            (hit + carry + 0.1 + home, REST, "io", low)]


def GIVE(hit=1.0, home=3.0):
    return [(0.0, REST, "io"), (hit, "give", "io"), (home, REST, "io")]


def TUCK(down=0.9, up=2.8, home=3.9, to="aside"):
    """The other hand out of a cut's way by {@code down}, before it sweeps past, back once the cut's hand is home."""
    return [(0.0, REST, "io"), (down, to, "io"), (up, to, "lin"), (home, REST, "io")]


CHORE = {
    "daggers": {
        "poses": DAGGERS,
        "moves": {
            "strike_1": {"strike": STAB("line_hi", back=0.46), "other": GIVE()},
            "strike_2": {"strike": STAB("line_lo", back=0.33), "other": GIVE(home=3.8)},
            "strike_3": {"strike": CUT("cut_3"), "other": TUCK()},
            "strike_4": {"strike": STAB("line_pair", 2.0, 0.42, 0.33, 0.5, 1.5),
                         "other": STAB("line_pair", 2.0, 0.42, 0.33, 0.5, 1.5)},
            "strike_5": {"strike": CUT("cut_5", 2.0, 42.0, 46.0, 1.35, home=3.6), "other": TUCK(1.8, 5.0, 6.4)},
            "heavy": {"strike": STAB("line_deep", 1.0, 0.55, 0.4, 0.6, 2.0, ("line_deep", 0.53), 0.85),
                      "other": STAB("line_deep", 1.0, 0.55, 0.4, 0.6, 2.0, ("line_deep", 0.53), 0.85)},
            "riposte": {"strike": STAB("line_rip", 1.0, 0.58, 0.5, 0.5, 1.4), "other": GIVE(1.0, 3.4)},
            "slipcut": {"strike": CUT("cut_slip", home=3.4), "other": TUCK(0.9, 3.4, 4.8, "low")},
            "undercut": {"strike": CUT("cut_under", pre=40.0, home=3.4), "other": GIVE(1.0, 3.6)},
            "art": {"strike": CUT("cut_art", 2.0, 46.0, 48.0, 1.2, 8.0, 0.6, 3.4), "other": TUCK(1.8, 5.0, 6.6)},
            "plunge": {"strike": CUT("chop_p", 1.0, 36.0, 28.0, 0.0, 6.0, 0.5, 2.2, ("chop_p", -36.0, 6.0)),
                       "other": GIVE(1.0, 3.6)},
            "execution": {"strike": CUT("chop_x", 2.0, 38.0, 30.0, 1.2, 8.0, 0.5, 3.0), "other": GIVE(2.0, 5.0)},
        },
    },
}

EASE = {"io": L.ease_in_out_sine, "in": lambda x: x * x, "out": lambda x: 1 - (1 - x) ** 2, "lin": lambda x: x,
        "late": lambda x: L.ease_in_out_sine(x) ** 2}
_POSES = {}


def has(sid, mv):
    return mv in CHORE.get(sid, {}).get("moves", {})


def bite(sid, mv):
    """A set move never slows after its hit (the freeze is the hit-stop's alone): None for any other."""
    return [1.0, 0.0] if has(sid, mv) else None


def build(held, p):
    """(d, R): a key pose as the hand's move from rest and its turn (anim_lab's pose)."""
    if p is None:
        return np.zeros(3), np.eye(3)
    z = p["z"]
    G = np.array([p["g"][0] * ASP / F_ * z, p["g"][1] / F_ * z, -z])
    ray = G / np.linalg.norm(G)
    ang = math.radians(p["ang"])
    # along the screen at the angle, square to the eye's ray through the grip (a step along that ray moves nothing on
    # the screen, so the angle stays as written however far out the grip is), then leant along the ray
    v = np.array([-math.cos(ang) * ASP, math.sin(ang), 0.0])
    v -= ray * (v @ ray)
    v /= np.linalg.norm(v)
    lean = math.radians(p["lean"])
    a = v * math.cos(lean) + ray * math.sin(lean)
    # the least turn from the stance (round 12: an edge set per pose rolled the blade about its length between poses,
    # 84 degrees in a stab, and read as spinning), then the pose's own roll
    R = L.axis_rot(a, p.get("roll", 0.0)) @ L.turn_to(held.axis, a)
    return G - H0 - R @ held.grip, R


def _grip(held, pose):
    d, R = pose
    return H0 + d + R @ held.grip


def _on_line(held, p, back):
    z = p["depth"]
    T = np.array([p["tip"][0] * ASP / F_ * z, p["tip"][1] / F_ * z, -z])
    a = T - p["src"]
    a /= np.linalg.norm(a)
    R = L.axis_rot(a, p["roll"]) @ L.turn_to(held.axis, a)
    G = T - R @ (held.tip - held.grip) - a * back
    return G - H0 - R @ held.grip, R


_LEVERS = {}


def _on_arc(held, p, theta, lag=0.0, roll=0.0):
    import swing_studio as SS
    key = (held.wid, id(p))
    if key not in _LEVERS:
        target = SS._target(held, p["pivot"], p["target"], p["mid"])
        r_c = target - p["pivot"]
        reach = float(np.linalg.norm(r_c))
        r_c /= reach
        r_e = float(np.linalg.norm(target))
        p_e = target / r_e
        _LEVERS[key] = (r_c, SS._screen_way(target, r_c, p["way"]), reach, p_e, SS._screen_way(target, p_e, p["way"]), r_e,
                        L.held_frame(held))
    r_c, tan_c, reach, p_e, tan_e, r_e, (F, side) = _LEVERS[key]

    def aim(th):  # as swing_studio.cut_pose: the middle about the eye, the blade half out from the shoulder
        a = math.radians(th)
        q_s = p["pivot"] + reach * (math.cos(a) * r_c + math.sin(a) * tan_c)
        q_e = r_e * (math.cos(a) * p_e + math.sin(a) * tan_e)
        v_s = reach * (-math.sin(a) * r_c + math.cos(a) * tan_c)
        v_e = r_e * (-math.sin(a) * p_e + math.cos(a) * tan_e)
        q, v = q_s + (q_e - q_s) * p["eye"], v_s + (v_e - v_s) * p["eye"]
        w = (q - p["pivot"]) / np.linalg.norm(q - p["pivot"])
        r = w + math.cos(a) * r_c + math.sin(a) * tan_c
        return q, v, r / np.linalg.norm(r)

    q, _, r = aim(theta)
    _, way, rb = aim(theta - lag)
    R = L.frame_to(rb, way, side, F)
    if roll:
        R = L.axis_rot(rb, roll) @ R
    g = q - r * p["mid"] * held.length
    return g - H0 - R @ held.grip, R


def _key_pose(held, poses, ref):
    """(the pose, the line or lever it lies on, its place there) for a track's pose: REST, a spot's name, or (a line's
    or a lever's name, its place on it...)."""
    if ref is None:
        return build(held, None), None, None
    if isinstance(ref, str):
        return build(held, poses[ref]), None, None
    p = poses[ref[0]]
    at = np.zeros(1 if p["kind"] == "line" else 3)
    at[:len(ref) - 1] = ref[1:]
    return _place(held, p, at), ref[0], at


def _place(held, p, at):
    return _on_line(held, p, *at) if p["kind"] == "line" else _on_arc(held, p, *at)


def _track(sid, mv, held, which):
    key = (sid, mv, held.wid, which)
    if key not in _POSES:
        poses = CHORE[sid]["poses"]
        _POSES[key] = [(k[0], *_key_pose(held, poses, k[1]), k[2], k[3] if len(k) > 3 else 0.0,
                        k[4] if len(k) > 4 else k[2]) for k in CHORE[sid]["moves"][mv][which]]
    return _POSES[key]


def pose(sid, mv, held, t, which="strike"):
    """(d, R) of a set move's {@code which} hand ("strike" or "other") at {@code t} ticks, or None for a move not set
    here. Each stretch between two key poses is one stroke on its own ease: along its line or round its lever when
    both keys lie on the same one (a lever's lag and roll on the turn's ease), else the grip straight from one to the
    next (or on its bowed arc), the blade turning between their turns on the same ease, or its own."""
    if not has(sid, mv):
        return None
    keys = _track(sid, mv, held, which)
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = max(j for j in range(len(keys) - 1) if keys[j][0] <= t)
    t0, a, on_a, at_a = keys[i][:4]
    t1, b, on_b, at_b, ease, bulge, turn = keys[i + 1]
    x = (t - t0) / max(1e-6, t1 - t0)
    u = EASE[ease](x)
    if on_a is not None and on_a == on_b:
        w = np.full(len(at_a), EASE[turn](x))
        w[0] = u
        return _place(held, CHORE[sid]["poses"][on_a], at_a + (at_b - at_a) * w)
    p0, p1 = _grip(held, a), _grip(held, b)
    g = p0 + (p1 - p0) * u
    if bulge:
        mid = (p0 + p1) / 2
        side = np.cross(p1 - p0, mid / np.linalg.norm(mid))
        g = g + side * (bulge * 4 * u * (1 - u))
    R = L.lerp_pose(a, b, EASE[turn](x))[1]
    return g - H0 - R @ held.grip, R


def describe(held, p):
    """Where a pose shows the blade: grip, middle and tip (ndc), depth, angle on the screen and lean."""
    d, R = p
    g = H0 + d + R @ held.grip
    tip = H0 + d + R @ held.tip
    mid = g + (tip - g) * L.MID

    def n(P):
        return np.array([P[0] / -P[2] * F_ / ASP, P[1] / -P[2] * F_])
    sg, st = n(g), n(tip)
    ax = R @ held.axis
    return (f"grip {sg[0]:+.2f},{sg[1]:+.2f}  mid {n(mid)[0]:+.2f},{n(mid)[1]:+.2f}  tip {st[0]:+.2f},{st[1]:+.2f}"
            f"  depth {-g[2]:.2f}  angle {math.degrees(math.atan2(st[1] - sg[1], -(st[0] - sg[0]))):5.1f}"
            f"  lean {math.degrees(math.asin(float(ax @ (g / np.linalg.norm(g))))):5.1f}")


def quick(base, out):
    """A trial file: {@code base} (a baked fp_swings.json) with every set move baked again from here, nothing else
    touched (a full bake takes the whole minute)."""
    import json
    import swing_studio as SS
    data = json.load(open(base))
    rows = L.table()
    for sid, c in CHORE.items():
        st = rows[sid]
        held = L.Held("gatewrought_" + sid)
        for mv in c["moves"]:
            m = st["moves"][mv]
            total = m["windup"] + m["active"] + m["recovery"]
            row = data["styles"][sid]["moves"][mv]
            keys, idle = [], []
            for tt in np.arange(0, total + 1e-6, L.KEY_DT):
                d, R = L.rig_pose(st, m, held, float(tt))
                cam = L.camera(st, m, float(tt))
                keys.append([round(float(tt), 3)] + [round(float(v), 4) for v in d]
                            + [round(float(v), 5) for v in L.mat_quat(R)] + [round(float(v), 3) for v in cam])
                d, R = pose(sid, mv, held, float(tt), "other")
                idle.append([round(float(tt), 3)] + [round(float(v), 4) for v in d] + [round(float(v), 5) for v in L.mat_quat(R)])
            row.update(keys=keys, idle=idle, bite=bite(sid, mv), set=SS.set_of(st, mv, held))
    json.dump(data, open(out, "w"), separators=(",", ":"))
    return out


def main(argv):
    if len(argv) == 3 and argv[0] == "quick":
        print(quick(argv[1], argv[2]))
        return 0
    if not argv or argv[0] != "describe":
        raise SystemExit(__doc__)
    for sid, c in CHORE.items():
        held = L.Held("gatewrought_" + sid)
        print(sid, "rest ", describe(held, build(held, None)))
        for name, p in c["poses"].items():
            if p["kind"] == "spot":
                print(sid, f"{name:12s}", describe(held, build(held, p)))
        import swing_studio as SS
        st = L.table()[sid]
        for mv, tracks in c["moves"].items():
            _, nxt = SS.chain_links(st, mv)
            home = max(tracks["strike"][-1][0], tracks["other"][-1][0])
            print(sid, f"{mv}: both hands home at {home} ticks; the next strike from {nxt[0] if nxt else '-'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
