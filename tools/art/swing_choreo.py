"""Swing Studio's choreography (rounds 11-12): a style's moves set by hand, both hands at once, as a few key poses on a
timeline, in place of the rules that worked each pose out (anim_lab.rig_pose, swing_studio's give-way, haul and pull).

Euan 2026-10-29, after round 10: "it still feels like it's going to unnecessary distances sometimes... the right arm
goes way to the left for some reason, or sometimes it's just not aligned... maybe the entire approach is wrong?" It was:
every pose came out of rules (cut edge to edge, give way until clear, haul back to the next set), each fixing one
complaint and moving something else, and the hand not striking had no place of its own. Then, after round 11: "it
feels more like they are spinning and making circles than stabbing and slashing": every pose set its own edge, so the
blade turned about its own length between poses (84 degrees a stab), and a curve through every pose rounded each
turn back into a loop. Here:

- a chain strike starts in the holding stance (rest), and the hand that strikes next is back in it by the chain's next
  press (`describe` shows when), so whatever the clicking, the next strike takes over from the pose it was set from (a
  stab's own hand, still a little short of home, is carried into its give by the blend);
- a pose's turn is the least turn from the stance, plus a roll of its own only where a cut needs its edge turned
  toward the cut (in the wind-up, back through the way home): never a turn about the blade's length in a blow;
- a thrust drives along its own blade, which the wind-up has already laid on the line to its target, and comes home
  on the same line, turning into the stance last; a cut is one stroke with the hit at its middle; a chop goes down and
  forward, edge first unturned (in the stance the edge faces down and away);
- between two poses the grip goes straight (or on an arc that is set): a stroke turns back only at a pose;
- the hand not striking keeps to its own side, with a small give of its own;
- the cuts are a dagger's: short and quick across the lower middle of the view, not edge to edge.

A pose is where the grip sits on the screen (ndc), how far ahead of the eye, which way the blade points on the screen
(degrees from the left, as the guard's), how far its tip leans away along the eye's ray, and its roll. Each hand's track
is set for a right hand: the runtime mirrors the off hand's. `swing_chain.py twist` measures the turn about the length.

    python3 tools/art/swing_choreo.py describe            the stance and every key pose, as seen
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
    return dict(g=g, z=z, ang=ang, lean=lean, roll=roll)


REST = None  # the holding stance: vanilla's hold, the hand at its rest

# the daggers (each hand's own: a right hand's poses; the left's are mirrored by the game). In the stance the edge
# faces down and away, so a steep cut down and across leads with it unturned: no pose here rolls the blade.
DAGGERS = {
    # the stab: the blade turned onto the line from the stance to its target as the hand draws back a little, then
    # driven along its own length to the point just under the crosshair (no turn in the drive: a turn in it whips the
    # point about), a beat at full reach, back the same way (solved from the target, the low one for the left hand)
    "cock": spot((0.72, -1.20), 0.60, 57.0, -7.0),
    "stab": spot((0.30, -0.458), 0.91, 55.0, 17.5),
    "reach": spot((0.275, -0.423), 0.934, 55.0, 17.5),
    "cock_low": spot((0.72, -1.20), 0.60, 54.5, -5.3),
    "stab_low": spot((0.36, -0.592), 0.853, 52.5, 14.0),
    "reach_low": spot((0.331, -0.554), 0.878, 52.5, 14.0),
    # the hand not striking gives a little as the other drives (the body turns into the blow)
    "give": spot((0.74, -1.24), 0.63, 69.0, -2.0),
    # the cut: one stroke. A short lift with the edge turned a little toward the cut (the blow lands a tick in: no time
    # for a big raise), then down and across in one sweep, the blade turning in the cut's own plane, the hit at the
    # middle of the stroke, its follow-through low in the middle; the edge turns back on the way home
    "chamber": spot((0.74, -1.04), 0.66, 76.0, -6.0, 36.0),
    "carry": spot((0.16, -1.02), 0.72, 40.0, 24.0, 36.0),
    # the hand a cut comes toward: out a little, away from its path
    "duck": spot((0.84, -1.26), 0.67, 71.0, -3.0),
    # the twin push: each solved as the stab is, both drawn back onto their lines, then driven together at points just
    # under the crosshair, each on its own side
    "draw": spot((0.70, -1.22), 0.56, 63.0, 5.1),
    "push": spot((0.461, -0.63), 0.782, 61.0, 20.3),
    "hold": spot((0.433, -0.58), 0.811, 61.0, 20.3),
    # the finisher's cut: strike 3's stroke from higher, with the time to raise it (its blow lands two ticks in)
    "chamber_big": spot((0.78, -0.96), 0.68, 84.0, -10.0, 30.0),
    "carry_big": spot((0.14, -1.06), 0.72, 32.0, 28.0, 30.0),
    # the heavy: held coiled while it charges (the game shows its first pose, trembling), points already on their line,
    # then both driven deep at the target on the release
    "coil": spot((0.76, -1.26), 0.54, 62.0, 9.3),
    "lunge": spot((0.399, -0.474), 0.903, 59.5, 31.4),
    "lunge_far": spot((0.363, -0.413), 0.956, 59.5, 31.4),
    # the riposte: strike 1's thrust, deeper, straight at the crosshair, a longer beat at full reach
    "rip_cock": spot((0.72, -1.20), 0.60, 57.5, -3.8),
    "rip": spot((0.259, -0.372), 0.987, 55.5, 24.5),
    "rip_far": spot((0.229, -0.328), 1.024, 55.5, 24.5),
    # the Slipcut (out of a dodge): strike 3's stroke, flatter, across just under the crosshair
    "slip_set": spot((0.76, -0.90), 0.66, 70.0, -6.0, 36.0),
    "slip_carry": spot((0.10, -0.95), 0.72, 25.0, 20.0, 36.0),
    # the Undercut (out of a slide): low across the bottom of the view
    "under_set": spot((0.80, -1.10), 0.64, 60.0, -4.0, 42.0),
    "under_carry": spot((0.12, -1.20), 0.70, 20.0, 18.0, 42.0),
    # the Art: the widest cut, its edge turned through the long wind-up, across the whole lower view
    "art_set": spot((0.86, -0.80), 0.70, 75.0, -10.0, 70.0),
    "art_carry": spot((0.06, -1.00), 0.74, 15.0, 20.0, 70.0),
    # the Plunge: held high while it falls (the game shows its first pose), the blade level and forward, then chopped
    # down edge first through the target as it lands (in the stance the edge faces down: no roll)
    "high": spot((0.60, -0.40), 0.66, 25.0, 30.0),
    "plunge": spot((0.30, -1.10), 0.88, 15.0, 45.0),
    # the Execution: raised over the target through its wind-up, then a hard chop down through it
    "exec_raise": spot((0.62, -0.50), 0.66, 30.0, 25.0),
    "exec_carry": spot((0.28, -1.15), 0.88, 15.0, 45.0),
}

# per move: the striking hand's track and the other's, (tick, pose name or REST, ease into it[, bulge]). The eases:
# "io" in and out (leaves and settles), "in" gathering speed into it (the drive), "out" losing it (the carry), "lin";
# "late" (the turn only): a way home that keeps the blade on the line it came out on and turns it into the stance last.
# Between two poses the grip goes straight, or on an arc bowed {@code bulge} of the way across to its left on the
# screen: a stroke turns back only where a pose is held, so no stroke loops into a circle.
CHORE = {
    "daggers": {
        "poses": DAGGERS,
        "moves": {
            "strike_1": {"strike": [(0.0, REST, "io"), (0.3, "cock", "io"), (1.0, "stab", "in"), (1.3, "reach", "out"),
                                    (3.5, REST, "io", 0.0, "late")],
                         "other": [(0.0, REST, "io"), (1.0, "give", "io"), (3.4, REST, "io")]},
            "strike_2": {"strike": [(0.0, REST, "io"), (0.3, "cock_low", "io"), (1.0, "stab_low", "in"),
                                    (1.3, "reach_low", "out"),
                                    (3.5, REST, "io", 0.0, "late")],
                         "other": [(0.0, REST, "io"), (1.0, "give", "io"), (3.4, REST, "io")]},
            "strike_3": {"strike": [(0.0, REST, "io"), (0.35, "chamber", "io"), (1.65, "carry", "io", -0.12),
                                    (4.0, REST, "io")],
                         "other": [(0.0, REST, "io"), (0.9, "duck", "io"), (2.0, "duck", "lin"), (4.0, REST, "io")]},
            "strike_4": {"strike": [(0.0, REST, "io"), (1.2, "draw", "io"), (2.0, "push", "in"), (2.4, "hold", "out"),
                                    (6.0, REST, "io", 0.0, "late")],
                         "other": [(0.0, REST, "io"), (1.2, "draw", "io"), (2.0, "push", "in"), (2.4, "hold", "out"),
                                   (6.0, REST, "io", 0.0, "late")]},
            "strike_5": {"strike": [(0.0, REST, "io"), (1.2, "chamber_big", "io"), (3.0, "carry_big", "io", -0.12),
                                    (6.6, REST, "io")],
                         "other": [(0.0, REST, "io"), (1.6, "duck", "io"), (3.0, "duck", "lin"), (6.0, REST, "io")]},
            "heavy": {"strike": [(0.0, "coil", "io"), (1.0, "lunge", "in"), (1.6, "lunge_far", "out"),
                                 (6.5, REST, "io", 0.0, "late")],
                      "other": [(0.0, "coil", "io"), (1.0, "lunge", "in"), (1.6, "lunge_far", "out"),
                                (6.5, REST, "io", 0.0, "late")]},
            "riposte": {"strike": [(0.0, REST, "io"), (0.3, "rip_cock", "io"), (1.0, "rip", "in"), (1.5, "rip_far", "out"),
                                   (4.6, REST, "io", 0.0, "late")],
                        "other": [(0.0, REST, "io"), (1.0, "give", "io"), (3.6, REST, "io")]},
            "slipcut": {"strike": [(0.0, REST, "io"), (0.35, "slip_set", "io"), (1.65, "slip_carry", "io", -0.12),
                                   (5.0, REST, "io")],
                        "other": [(0.0, REST, "io"), (0.9, "duck", "io"), (2.0, "duck", "lin"), (4.4, REST, "io")]},
            "undercut": {"strike": [(0.0, REST, "io"), (0.35, "under_set", "io"), (1.65, "under_carry", "io", -0.10),
                                    (5.5, REST, "io")],
                         "other": [(0.0, REST, "io"), (0.9, "duck", "io"), (2.2, "duck", "lin"), (4.8, REST, "io")]},
            "art": {"strike": [(0.0, REST, "io"), (1.2, "art_set", "io"), (2.8, "art_carry", "io", -0.15), (9.0, REST, "io")],
                    "other": [(0.0, REST, "io"), (1.6, "duck", "io"), (3.4, "duck", "lin"), (7.0, REST, "io")]},
            "plunge": {"strike": [(0.0, "high", "io"), (2.0, "plunge", "io"), (6.5, REST, "io")],
                       "other": [(0.0, REST, "io"), (1.0, "give", "io"), (4.0, REST, "io")]},
            "execution": {"strike": [(0.0, REST, "io"), (1.2, "exec_raise", "io"), (2.8, "exec_carry", "io"),
                                     (9.0, REST, "io")],
                          "other": [(0.0, REST, "io"), (1.6, "duck", "io"), (3.4, "duck", "lin"), (7.0, REST, "io")]},
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


def _track(sid, mv, held, which):
    key = (sid, mv, held.wid, which)
    if key not in _POSES:
        poses = CHORE[sid]["poses"]
        _POSES[key] = [(k[0], build(held, poses[k[1]] if k[1] else None), k[2], k[3] if len(k) > 3 else 0.0,
                        k[4] if len(k) > 4 else k[2]) for k in CHORE[sid]["moves"][mv][which]]
    return _POSES[key]


def pose(sid, mv, held, t, which="strike"):
    """(d, R) of a set move's {@code which} hand ("strike" or "other") at {@code t} ticks, or None for a move not set
    here. Each stretch between two key poses is one stroke on its own ease: the grip straight from one to the next (or
    on its bowed arc), the blade turning between their turns on the same ease, or its own."""
    if not has(sid, mv):
        return None
    keys = _track(sid, mv, held, which)
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = max(j for j in range(len(keys) - 1) if keys[j][0] <= t)
    t0, a = keys[i][:2]
    t1, b, ease, bulge, turn = keys[i + 1]
    x = (t - t0) / max(1e-6, t1 - t0)
    u = EASE[ease](x)
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
