"""Third-person bodies of the Ascent's states (Player Animation Library, optional; SPEC §17).

    python3 tools/art/build_all.py ascent_moves      assets/awaken/player_animations/ascent_moves.json
    python3 tools/art/specs/ascent_moves.py build    the same, plus the clipping report
    python3 tools/art/specs/ascent_moves.py preview DIR [clip ...]

Poses in power_casts.py's figure terms. The game turns the WHOLE figure itself (dev.awaken.body.ascent.client.
AscentAnims): along the rope while swinging, belly down along the line of flight on wings, upright while hovering,
one forward flip after letting go. So these clips pose the limbs on an upright body and never lean the figure:
- swing   the rope arm straight up along the body, the other out for balance, legs trailing and a little bent
- zip     reaching toward the pull, knees drawn up
- flip    a tight tuck through the flip, opening at the end (the turn itself is the game's)
- fall    a skydiver's spread: arms out and up, knees bent, legs apart (after a release, between ropes)
- glide   lying on the air: arms swept back beside the body, legs long, chin up to see ahead
- soar    flight: the same line, arms a little further out, a slow breath through the body
- hover   standing on nothing: legs hanging, knees soft, arms low and out, a slow bob
Every clip has a _bent twin (Bendable Cuboids). Edit the poses here, never the JSON.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import power_casts as P  # noqa: E402

ROOT = P.ROOT
OUT_REL = "assets/awaken/player_animations/ascent_moves.json"
pose, nudge = P.pose, P.nudge

CLIPS = {}


def clip(name, keys, length, loop=False, end=None, note=""):
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    CLIPS[name] = P.Clip(name, keys, length, loop, end, note)
    CLIPS[name + "_bent"] = P.Clip(name + "_bent", keys, length, loop, end, note + " (bent elbows and knees)", bent=True)


def loop(name, base, jitters, period, note):
    n = len(jitters) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(jitters)] + [(period, base, "easeinoutsine")]
    clip(name, keys, period, loop=True, note=note)


# Swing: hanging from the rope hand. The game tilts the body to hang along the rope, so the arm stays along the body.
SW = pose(ground=False, ra=(-176, 6, -4), re=4, la=(-12, 0, -64), le=30,
          rl=(14, 0, 4), rk=28, ll=(30, 0, -4), lk=48, twist=-6)
loop("ascent_swing", SW, [nudge(SW, la=(-8, 0, 6), rl=(-6, 0, 0), ll=(-8, 0, 0)), nudge(SW, la=(6, 0, -4), rl=(6, 0, 0), ll=(8, 0, 0))], 16,
     "the rope arm up, the other out for balance, legs trailing")

# Zip: pulled in along the rope, reaching for it, knees up to land.
ZP = pose(ground=False, ra=(-150, 8, -6), re=20, la=(-60, -10, -30), le=60, rl=(-48, 0, 6), rk=80, ll=(-30, 0, -6), lk=95)
loop("ascent_zip", ZP, [nudge(ZP, rl=(-6, 0, 0), ll=(6, 0, 0))], 8, "reaching along the pull, knees drawn up")

# Flip: tucked tight while the game turns the body once forward, opening into the fall.
FL_TUCK = pose(ground=False, head_x=30, rl=(-110, 0, 6), rk=130, ll=(-114, 0, -6), lk=132,
               ra=(-46, -24, 20), re=110, la=(-46, 24, -20), le=110)
FL_OPEN = pose(ground=False, ra=(-14, 0, 78), re=20, la=(-14, 0, -78), le=20, rl=(-30, 0, 12), rk=40, ll=(-10, 0, -12), lk=30)
clip("ascent_flip", [(0, nudge(FL_TUCK, rk=-40, lk=-40), "linear"), (2, FL_TUCK, "easeoutcubic"), (8.5, FL_TUCK, "linear"),
                     (12, FL_OPEN, "easeoutquad"), (13, FL_OPEN, "linear")], 13, note="a tight tuck through the flip, opening")

# Fall: the skydiver's spread.
FA = pose(ground=False, ra=(-14, 0, 82), re=24, la=(-14, 0, -82), le=24, rl=(-22, 0, 14), rk=36, ll=(-6, 0, -14), lk=24)
loop("ascent_fall", FA, [nudge(FA, ra=(4, 0, -4), la=(4, 0, 4), rl=(-4, 0, 0)), nudge(FA, ra=(-3, 0, 3), la=(-3, 0, -3), ll=(-4, 0, 0))], 14,
     "arms out and up, knees bent, legs apart")

# Glide: lying on the air (the game lays the body along the flight); the chin up to see ahead.
GL = pose(ground=False, head_x=-62, ra=(14, 0, 26), re=8, la=(14, 0, -26), le=8, rl=(4, 0, 3), rk=6, ll=(4, 0, -3), lk=6)
loop("ascent_glide", GL, [nudge(GL, ra=(0, 0, 3), la=(0, 0, -3), rk=4, lk=-2), nudge(GL, ra=(0, 0, -2), la=(0, 0, 2), rk=-2, lk=4)], 30,
     "arms swept back beside the body, legs long, chin up")

# Soar: the same line, arms further out, a slow breath.
SO = pose(ground=False, head_x=-58, ra=(8, 0, 40), re=14, la=(8, 0, -40), le=14, rl=(6, 0, 4), rk=10, ll=(6, 0, -4), lk=10)
loop("ascent_soar", SO, [nudge(SO, ra=(-4, 0, 4), la=(-4, 0, -4), rise=0.4), nudge(SO, ra=(3, 0, -3), la=(3, 0, 3), rise=-0.3)], 36,
     "flight: arms out, a slow breath through the body")

# Hover: standing on nothing.
HO = pose(ground=False, ra=(-10, 0, 30), re=22, la=(-10, 0, -30), le=22, rl=(-14, 0, 4), rk=30, ll=(6, 0, -4), lk=16)
loop("ascent_hover", HO, [nudge(HO, rise=0.8, ra=(0, 0, 5), la=(0, 0, -5), rk=6), nudge(HO, rise=-0.6, ra=(0, 0, -3), la=(0, 0, 3), lk=6)], 40,
     "legs hanging, knees soft, arms low and out, a slow bob")


def document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/ascent_moves.py: edit the poses there, not this file.",
            "animations": {name: c.json() for name, c in CLIPS.items()}}


def write(root=None):
    path = os.path.join(root or os.path.join(ROOT, "src/main/resources"), OUT_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(document(), fh, separators=(",", ":"))
        fh.write("\n")
    return path


def build(out):
    write(out.root)


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
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        path = write()
        print(f"{len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        for b in check():
            print("CLIPPING", b)
        return 0
    if cmd == "preview":
        out_dir = argv[1]
        os.makedirs(out_dir, exist_ok=True)
        names = argv[2:] or [n for n in CLIPS if not n.endswith("_bent")]
        for n in names:
            print(P.strip(CLIPS[n], out_dir))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
