"""Swing Studio's choreography (round 11): a style's chain set by hand, both hands at once, as a few key poses on a
timeline, in place of the rules that worked each pose out (anim_lab.rig_pose, swing_studio's give-way, haul and pull).

Euan 2026-10-29, after round 10: "it still feels like it's going to unnecessary distances sometimes... the right arm
goes way to the left for some reason, or sometimes it's just not aligned... maybe the entire approach is wrong?" It was:
every pose came out of rules (cut edge to edge, give way until clear, haul back to the next set), each fixing one
complaint and moving something else, and the hand not striking had no place of its own. Here:

- every strike starts in the holding stance (rest) and is back in it, both hands, before the chain's next strike can
  start (its earliest press; `describe` shows how long before), so whatever the clicking, the next strike takes over
  from the pose it was set from (a landed blow's freeze or a held click leaves the hands behind: the blend carries them
  the rest of the way home, along the way they were going);
- the hand not striking keeps to its own side, with a small give of its own (it drops a little under a cut that comes
  its way): never a dodge worked out from the other blade;
- the cuts are a dagger's: short and quick across the lower middle of the view, not edge to edge.

A pose is where the grip sits on the screen (ndc), how far ahead of the eye, which way the blade points on the screen
(degrees from the left, as the guard's), how far its tip leans away along the eye's ray, and which way its cutting edge
faces (view space). Each hand's track is set for a right hand: the runtime mirrors the off hand's.

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


def spot(g, z, ang, lean, edge):
    """A key pose: the grip at ndc {@code g}, {@code z} ahead of the eye, the blade at {@code ang} on the screen leant
    {@code lean} away along the eye's ray, its cutting edge toward {@code edge} (view space)."""
    return dict(g=g, z=z, ang=ang, lean=lean, edge=edge)


REST = None  # the holding stance: vanilla's hold, the hand at its rest

# the daggers (each hand's own: a right hand's poses; the left's are mirrored by the game)
DAGGERS = {
    # the stab: a short draw back, straight in under the crosshair, a beat at full reach, back
    "cock": spot((0.74, -1.22), 0.62, 70.0, -6.0, (0.25, -0.7, -0.66)),
    "stab": spot((0.12, -0.33), 0.88, 56.0, 48.0, (0.83, -0.36, -0.44)),
    "reach": spot((0.10, -0.30), 0.90, 55.0, 50.0, (0.83, -0.36, -0.44)),
    "stab_low": spot((0.14, -0.42), 0.86, 52.0, 50.0, (0.83, -0.40, -0.40)),
    "reach_low": spot((0.12, -0.40), 0.88, 51.0, 52.0, (0.83, -0.40, -0.40)),
    # the hand not striking gives a little as the other drives (the body turns into the blow)
    "give": spot((0.74, -1.24), 0.64, 70.0, -3.0, (0.19, -0.7, -0.69)),
    # the cut: raised on its own side, blade up, then down across the middle on a diagonal, the blade square to the
    # eye so its length reads, edge first, its carry ending low in the middle (clear of the other blade)
    "chamber": spot((0.64, -1.10), 0.80, 92.0, -6.0, (-0.85, -0.35, -0.40)),
    "cut": spot((0.30, -0.66), 0.74, 60.0, 15.0, (-0.65, -0.65, -0.35)),
    "carry": spot((0.24, -0.98), 0.72, 58.0, 16.0, (-0.45, -0.80, -0.35)),
    # the hand a cut comes toward: out a little, away from its path
    "duck": spot((0.84, -1.26), 0.68, 72.0, -4.0, (0.19, -0.7, -0.69)),
    # the twin push: both drawn back and in, then together under the crosshair, each tip on its own side
    "draw": spot((0.70, -1.20), 0.58, 74.0, -10.0, (0.30, -0.66, -0.69)),
    "push": spot((0.30, -0.36), 0.88, 70.0, 48.0, (0.80, -0.30, -0.52)),
    "hold": spot((0.29, -0.34), 0.90, 70.0, 50.0, (0.80, -0.30, -0.52)),
    # the finisher's cut: strike 3's, raised a little higher and further round
    "chamber_big": spot((0.62, -1.00), 0.85, 114.0, -12.0, (-0.80, -0.40, -0.45)),
    "cut_big": spot((0.30, -0.64), 0.74, 58.0, 15.0, (-0.65, -0.65, -0.35)),
    "carry_big": spot((0.22, -1.00), 0.72, 54.0, 16.0, (-0.45, -0.80, -0.35)),
}

# per move: the striking hand's track and the other's, (tick, pose name or REST, ease into it). The eases: "io" in and
# out (leaves and settles), "in" gathering speed into it (the drive), "out" losing it (the carry), "lin".
CHORE = {
    "daggers": {
        "poses": DAGGERS,
        "moves": {
            "strike_1": {"strike": [(0.0, REST, "io"), (0.3, "cock", "out"), (1.0, "stab", "in"), (1.3, "reach", "out"),
                                    (2.6, REST, "io")],
                         "other": [(0.0, REST, "io"), (1.0, "give", "io"), (2.4, REST, "io")]},
            "strike_2": {"strike": [(0.0, REST, "io"), (0.3, "cock", "out"), (1.0, "stab_low", "in"),
                                    (1.3, "reach_low", "out"), (2.6, REST, "io")],
                         "other": [(0.0, REST, "io"), (1.0, "give", "io"), (2.4, REST, "io")]},
            "strike_3": {"strike": [(0.0, REST, "io"), (0.55, "chamber", "io"), (1.0, "cut", "in"), (1.5, "carry", "out"),
                                    (2.7, REST, "io")],
                         "other": [(0.0, REST, "io"), (0.9, "duck", "io"), (1.6, "duck", "lin"), (2.7, REST, "io")]},
            "strike_4": {"strike": [(0.0, REST, "io"), (1.2, "draw", "io"), (2.0, "push", "in"), (2.4, "hold", "out"),
                                    (4.6, REST, "io")],
                         "other": [(0.0, REST, "io"), (1.2, "draw", "io"), (2.0, "push", "in"), (2.4, "hold", "out"),
                                   (4.6, REST, "io")]},
            "strike_5": {"strike": [(0.0, REST, "io"), (1.3, "chamber_big", "out"), (2.0, "cut_big", "in"),
                                    (2.7, "carry_big", "out"), (4.4, REST, "io")],
                         "other": [(0.0, REST, "io"), (1.7, "duck", "io"), (2.8, "duck", "lin"), (4.4, REST, "io")]},
        },
    },
}

EASE = {"io": L.ease_in_out_sine, "in": lambda x: x * x, "out": lambda x: 1 - (1 - x) ** 2, "lin": lambda x: x}
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
    F, side = L.held_frame(held)
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
    R = L.frame_to(a, np.asarray(p["edge"], dtype=float), side, F)
    return G - H0 - R @ held.grip, R


def _grip(held, pose):
    d, R = pose
    return H0 + d + R @ held.grip


def _track(sid, mv, held, which):
    key = (sid, mv, held.wid, which)
    if key not in _POSES:
        poses = CHORE[sid]["poses"]
        _POSES[key] = [(t, build(held, poses[name] if name else None), ease) for t, name, ease in
                       CHORE[sid]["moves"][mv][which]]
    return _POSES[key]


def pose(sid, mv, held, t, which="strike"):
    """(d, R) of a set move's {@code which} hand ("strike" or "other") at {@code t} ticks, or None for a move not set
    here. The grip runs through its key places on a centripetal Catmull-Rom curve (a cut's hand comes round in an arc,
    never on a ruler), each stretch timed by its own ease; the blade turns between key turns on the same ease."""
    if not has(sid, mv):
        return None
    keys = _track(sid, mv, held, which)
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = max(j for j in range(len(keys) - 1) if keys[j][0] <= t)
    t0, a, _ = keys[i]
    t1, b, ease = keys[i + 1]
    u = EASE[ease]((t - t0) / max(1e-6, t1 - t0))
    pts = [_grip(held, k[1]) for k in keys]
    p0, p1, p2, p3 = pts[max(0, i - 1)], pts[i], pts[i + 1], pts[min(len(pts) - 1, i + 2)]
    g = _catmull(p0, p1, p2, p3, u)
    R = L.lerp_pose(a, b, u)[1]
    return g - H0 - R @ held.grip, R


def _catmull(p0, p1, p2, p3, u, alpha=0.5):
    """Centripetal Catmull-Rom from p1 to p2 (a stretch whose ends repeat is straight, so a stop stays a stop)."""
    def tj(ti, a, b):
        return ti + max(1e-6, float(np.linalg.norm(b - a))) ** alpha
    if np.allclose(p1, p2):
        return p1.copy()
    t0 = 0.0
    t1 = tj(t0, p0, p1) if not np.allclose(p0, p1) else t0 + 1e-6
    t2 = tj(t1, p1, p2)
    t3 = tj(t2, p2, p3) if not np.allclose(p2, p3) else t2 + 1e-6
    if np.allclose(p0, p1):
        p0 = p1 - (p2 - p1)
        t1 = tj(t0, p0, p1)
        t2 = tj(t1, p1, p2)
        t3 = tj(t2, p2, p3) if not np.allclose(p2, p3) else t2 + 1e-6
    if np.allclose(p2, p3):
        p3 = p2 + (p2 - p1)
        t3 = tj(t2, p2, p3)
    t = t1 + (t2 - t1) * u
    a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1
    a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2
    a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3
    b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2
    b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3
    return (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2


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
