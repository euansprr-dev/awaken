"""Your body at a station, as everyone else sees it in third person (SPEC §64.5; Player Animation Library clips, played by
dev.awaken.anims.client.station.StationBodies through core.api.PlayerClips).

    python3 tools/art/specs/station_moves.py            the clips (+ clipping report) -> player_animations/station_moves.json
    python3 tools/art/specs/station_moves.py preview DIR   a frame strip per clip

A table's top is waist high in front of you, a furnace's mouth is at the knee, a chest's lid at the thigh:
- station_bench           bent over a table from the hips, both forearms forward over the top, a slow breath (loop)
- station_bench_reach_r   the right arm out over the grid and back (7 ticks); _l the left
- station_kneel           down on one knee before a furnace, the working hand toward the mouth, the other on the thigh (loop)
- station_kneel_reach     the arm into the mouth and back (8 ticks)
- station_box             bent to a box, one hand up on its lid, the other down in it (loop)
- station_box_reach       the low hand dips in and comes out (7 ticks)
- station_far             too far to touch: standing, the main arm half out toward it (loop)
- station_far_reach       the arm out and back (6 ticks)
Quick touches (a door pushed, a lever thrown, a lid lifted), for whoever watches:
- touch_one_low/mid/high  one hand out to a thing by its height and back (6 ticks)
- touch_both_low/mid/high both hands (8 ticks)
- touch_pull              a handle taken and pulled down (8)
- touch_strike            one blow onto the work (6)
- touch_crank             a handle round once (12)
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
CLIPS_REL = "assets/awaken/player_animations/station_moves.json"

pose, nudge = P.pose, P.nudge
CLIPS = {}


def clip(name, keys, length, loop=False, note="", legs=None):
    """legs: the legs of the plain twin (no Bendable Cuboids: a knee can't fold, so a kneel is a low lunge there)."""
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    plain = keys if legs is None else [(t, pose(k, **legs), e) for t, k, e in keys]
    CLIPS[name] = P.Clip(name, plain, length, loop, None, note)
    CLIPS[name + "_bent"] = P.Clip(name + "_bent", keys, length, loop, None, note + " (bent elbows and knees)", bent=True)


def breathe(name, base, jitters, period, note, legs=None):
    n = len(jitters) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(jitters)] + [(period, base, "easeinoutsine")]
    clip(name, keys, period, loop=True, note=note, legs=legs)


def there_and_back(name, base, out, ticks, note, legs=None):
    clip(name, [(0, base, "linear"), (ticks * 0.4, out, "easeoutquad"), (ticks * 0.55, out, "linear"), (ticks, base, "easeinoutsine")], ticks, note=note,
         legs=legs)


# Over a table: bent from the hips, feet a little apart, both forearms forward over the top.
BENCH = pose(lean=22, ra=(-60, -6, 4), re=32, la=(-58, 6, -4), le=30, rl=(8, 0, 4), rk=10, ll=(-8, 0, -4), lk=8)
breathe("station_bench", BENCH, [nudge(BENCH, lean=1.2, ra=(-2, 0, 0), le=2), nudge(BENCH, lean=-0.8, la=(-2, 0, 0), re=-2)], 40,
        "bent over a table, both forearms forward over the top, a slow breath")
there_and_back("station_bench_reach_r", BENCH, pose(BENCH, lean=27, shift=0.4, ra=(-84, -2, 0), re=6), 7, "the right arm out over the grid and back")
there_and_back("station_bench_reach_l", BENCH, pose(BENCH, lean=27, shift=0.4, la=(-84, 2, 0), le=6), 7, "the left arm out over the grid and back")

# Before a furnace: down on the left knee, the right foot forward and flat, the right hand toward the mouth.
KNEEL = pose(lean=14, ra=(-64, -4, 0), re=24, la=(-18, 0, -8), le=44, rl=(-80, 0, 5), rk=86, ll=(8, 0, -4), lk=96)
LUNGE = dict(rl=(-34, 0, 5), ll=(36, 0, -4))  # with rigid legs: a low lunge, the back leg well behind
breathe("station_kneel", KNEEL, [nudge(KNEEL, lean=1.2, ra=(-2, 0, 0)), nudge(KNEEL, lean=-0.8, re=3)], 44,
        "down on one knee before a furnace, the working hand toward the mouth", legs=LUNGE)
there_and_back("station_kneel_reach", KNEEL, pose(KNEEL, lean=22, shift=0.5, ra=(-88, 0, 0), re=2), 8, "the arm into the mouth and back", legs=LUNGE)

# At a box: bent well over it, knees soft, the left hand up on the lid, the right down in the box.
BOX = pose(lean=34, ra=(-42, -8, 2), re=18, la=(-82, 8, -4), le=48, rl=(10, 0, 5), rk=26, ll=(-10, 0, -5), lk=22)
breathe("station_box", BOX, [nudge(BOX, lean=1.2, ra=(-2, 0, 0)), nudge(BOX, lean=-0.8, le=2)], 40,
        "bent to a box, one hand up on its lid, the other down in it")
there_and_back("station_box_reach", BOX, pose(BOX, lean=40, shift=0.3, ra=(-58, -4, 0), re=4, rk=32, lk=28), 7, "the low hand dips in and comes out")

# Too far to touch (past two and a half blocks): standing, the main arm half out toward the station.
FAR = pose(lean=4, ra=(-46, -4, 0), re=26)
breathe("station_far", FAR, [nudge(FAR, ra=(-2, 0, 0)), nudge(FAR, re=2)], 40, "standing, the main arm half out toward the station")
there_and_back("station_far_reach", FAR, pose(FAR, lean=8, ra=(-80, -2, 0), re=6), 6, "the arm out and back")


# ---- quick touches (SPEC §64.9): the body reaches to the thing, by how the hand works and how high the thing is ------
STAND = pose()
LOW = dict(lean=30, rl=(14, 0, 5), rk=34, ll=(-12, 0, -5), lk=30)   # bent to something under the knee
HIGH = dict(lean=-4)                                                 # up to something at the head
for tag, body, arm in (("low", LOW, -52), ("mid", dict(lean=6), -82), ("high", HIGH, -128)):
    out = pose(STAND, ra=(arm, -4, 0), re=8, **body)
    there_and_back(f"touch_one_{tag}", STAND, out, 6, f"one hand out to a thing ({tag})")
    both = pose(STAND, ra=(arm, -10, 0), re=14, la=(arm, 10, 0), le=14, **body)
    there_and_back(f"touch_both_{tag}", STAND, both, 8, f"both hands to a thing ({tag})")
# a lever: the hand up to the handle, then pulled down through its throw
clip("touch_pull", [(0, STAND, "linear"), (3, pose(STAND, lean=4, ra=(-100, -4, 0), re=10), "easeoutquad"),
                    (6, pose(STAND, lean=8, ra=(-62, -4, 0), re=26), "easeinquad"), (8, STAND, "easeinoutsine")], 8, note="a handle taken and pulled down")
# a blow: the arm cocked, then down onto the work
clip("touch_strike", [(0, STAND, "linear"), (2, pose(STAND, lean=8, ra=(-140, 4, -8), re=60), "easeoutquad"),
                      (4, pose(STAND, lean=18, ra=(-64, 4, -6), re=8), "easeinquad"), (6, STAND, "easeinoutsine")], 6, note="one blow onto the work")
# a crank: the hand round once
CR = [pose(STAND, lean=10, ra=(a, y, 0), re=30) for a, y in ((-70, -10), (-58, 0), (-70, 10), (-84, 0))]
clip("touch_crank", [(0, STAND, "linear"), (2, CR[0], "easeoutquad"), (4, CR[1], "linear"), (6, CR[2], "linear"), (8, CR[3], "linear"), (10, CR[0], "linear"),
                     (12, STAND, "easeinoutsine")], 12, note="a handle cranked round once")


def document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/station_moves.py: edit the poses there, not this file.",
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
