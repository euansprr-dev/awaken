"""The smith at a War Anvil as everyone else sees them in third person (SPEC §46.7; Player Animation Library clips, played by
dev.awaken.arms.anvil.client.AnvilBodies through core.api.PlayerClips).

    python3 tools/art/specs/anvil_moves.py            the clips (+ clipping report) -> player_animations/arms_anvil.json
    python3 tools/art/specs/anvil_moves.py preview DIR   a frame strip per clip

The anvil's face stands about waist high a block in front, so the smith bends to it from the hips, feet set apart:
- arms_anvil          over the anvil (the draw-out, the hilt, the knap): the tongs hand low holding the work on the face,
                      the hammer arm cocked above the shoulder, a slow breath (loop)
- arms_anvil_strike   one blow (6 ticks): a short lift, the hammer down onto the face, the bounce, back up
- arms_anvil_tap      a light tap for the hilt and the knap (4 ticks): from the wrist and elbow, the arm low
- arms_anvil_heat     at the Forge's mouth: both hands on the tongs, the blade held in the coals, weight on the front foot
- arms_anvil_quench   both hands on the tongs, the blade held down in the trough, a slow dip
- arms_anvil_bind     winding the cord: the off hand holds the grip before the belly, the main hand circles it (12 ticks)
Every clip has a _bent twin (Bendable Cuboids). Edit the poses here, never the JSON.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
import power_casts as P  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
CLIPS_REL = "assets/awaken/player_animations/arms_anvil.json"

pose, nudge = P.pose, P.nudge
CLIPS = {}


def clip(name, keys, length, loop=False, note=""):
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    CLIPS[name] = P.Clip(name, keys, length, loop, None, note)
    CLIPS[name + "_bent"] = P.Clip(name + "_bent", keys, length, loop, None, note + " (bent elbows and knees)", bent=True)


def breathe(name, base, jitters, period, note):
    n = len(jitters) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(jitters)] + [(period, base, "easeinoutsine")]
    clip(name, keys, period, loop=True, note=note)


# Over the anvil: bent from the hips, the left foot forward; the tongs hand low on the face, the hammer cocked.
READY = pose(lean=20, ra=(-148, 6, -10), re=74, la=(-52, -10, 8), le=34,
             rl=(10, 0, 5), rk=14, ll=(-12, 0, -5), lk=10)
breathe("arms_anvil", READY, [nudge(READY, lean=1.5, ra=(-3, 0, 0), le=3), nudge(READY, lean=-1, ra=(2, 0, 0), le=-2)], 40,
        "over the anvil, the tongs hand low on the work, the hammer cocked, a slow breath")

# The blow: a short lift, then down onto the face (the body dips into it), the bounce off the metal, back to cocked.
LIFT = nudge(READY, ra=(-10, 0, 0), re=8, lean=-2)
DOWN = pose(READY, lean=26, shift=0.4, ra=(-66, 4, -6), re=8, la=(-50, -10, 8), le=30, rk=18, lk=14)
BOUNCE = pose(DOWN, lean=24, ra=(-84, 4, -6), re=20)
clip("arms_anvil_strike", [(0, READY, "linear"), (1, LIFT, "easeoutquad"), (3, DOWN, "easeinquad"), (4, BOUNCE, "easeoutquad"),
                           (6, READY, "easeinoutsine")], 6,
     note="a short lift, the hammer down onto the face, the bounce, back up")

# A tap: the arm held low over the work, the blow from the elbow.
LOW = pose(READY, lean=22, ra=(-96, 4, -8), re=48)
TAP = pose(LOW, lean=23, ra=(-72, 4, -8), re=18)
clip("arms_anvil_tap", [(0, READY, "linear"), (1, LOW, "easeoutquad"), (2, TAP, "easeinquad"), (4, READY, "easeinoutsine")], 4,
     note="a light tap from the elbow, the arm low over the work")

# At the Forge's mouth: both hands on the tongs, the blade held in the coals, the weight forward.
HEAT = pose(lean=12, ra=(-58, -14, 2), re=26, la=(-64, 14, -2), le=22,
            rl=(12, 0, 5), rk=16, ll=(-14, 0, -5), lk=8)
breathe("arms_anvil_heat", HEAT, [nudge(HEAT, lean=1.5, ra=(-2, 0, 0), la=(-2, 0, 0)), nudge(HEAT, lean=-1, ra=(1, 0, 0), la=(1, 0, 0))], 36,
        "both hands on the tongs, the blade held in the coals")

# The quench: the blade held down in the trough, both hands low, a slow dip and lift.
Q_UP = pose(lean=24, ra=(-42, -16, 2), re=18, la=(-44, 16, -2), le=16,
            rl=(8, 0, 6), rk=22, ll=(-10, 0, -6), lk=18)
Q_DOWN = nudge(Q_UP, lean=3, ra=(6, 0, 0), la=(6, 0, 0), rk=6, lk=6)
clip("arms_anvil_quench", [(0, Q_UP, "linear"), (10, Q_DOWN, "easeinoutsine"), (20, Q_UP, "easeinoutsine")], 20, loop=True,
     note="both hands on the tongs, the blade down in the trough, a slow dip")

# Binding the grip: the off hand holds the weapon before the belly, the main hand winds the cord round it.
B = pose(lean=16, la=(-48, -14, 10), le=62, rl=(6, 0, 5), rk=10, ll=(-8, 0, -5), lk=8)
WIND = [pose(B, ra=(-62, 8, -18), re=56), pose(B, ra=(-50, 2, -4), re=66), pose(B, ra=(-40, -4, -14), re=58), pose(B, ra=(-52, 4, -26), re=48)]
clip("arms_anvil_bind", [(0, WIND[0], "linear"), (3, WIND[1], "easeinoutsine"), (6, WIND[2], "easeinoutsine"), (9, WIND[3], "easeinoutsine"),
                         (12, WIND[0], "easeinoutsine")], 12, loop=True,
     note="the off hand holds the grip, the main hand winds the cord round it")


def document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/anvil_moves.py: edit the poses there, not this file.",
            "animations": {name: c.json() for name, c in CLIPS.items()}}


def write(root=None):
    path = os.path.join(root or RES, CLIPS_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(document(), fh, separators=(",", ":"))
        fh.write("\n")
    return path


def check(limit=1.25):
    bad = []
    for name, c in CLIPS.items():
        worst, at, where = 0.0, 0.0, ""
        t = 0.0
        while t <= c.length + 1e-6:
            d, w = P.penetration(c.sample(t, 0.0), 0.0)
            if d > worst:
                worst, at, where = d, t, w
            t += 0.5
        if worst > limit:
            bad.append(f"{name}: {where} by {worst:.2f} px at tick {at:g}")
    return bad


def main(argv):
    cmd = argv[0] if argv else "content"
    if cmd == "content":
        path = write()
        print(f"{len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        for b in check():
            print("CLIPPING", b)
    elif cmd == "preview":
        out = argv[1] if len(argv) > 1 else "."
        os.makedirs(out, exist_ok=True)
        for n in CLIPS:
            if not n.endswith("_bent"):
                print(P.strip(CLIPS[n], out))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
