"""Third-person bodies of the Awakened Body moves (Player Animation Library, optional; docs/modules/body.md).

    python3 tools/art/build_all.py body_moves      assets/awaken/player_animations/body_moves.json
    python3 tools/art/specs/body_moves.py build    the same, plus the clipping report
    python3 tools/art/specs/body_moves.py preview DIR [clip ...]
                                                   a frame strip per clip (every tick, three views) and a sheet of
                                                   every move's key poses, straight and bent
    python3 tools/art/specs/body_moves.py check    the clipping report only

Poses are written in power_casts.py's figure terms (see its docstring: lean, twist, rise, ra/la, rl/ll, the elbow and
knee flexion re/le/rk/lk, head_x, limbs_body, ground). Every clip has a _bent twin with the elbows and knees bent
(played while Bendable Cuboids is loaded); the straight clip must read on its own. The game plays these through
core.api.PlayerClips (dev.awaken.body.client.BodyAnims names the clips; dev.awaken.body.BodyClips lists them).
Edit the poses here, never the JSON.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import power_casts as P  # noqa: E402

ROOT = P.ROOT
OUT_REL = "assets/awaken/player_animations/body_moves.json"
pose, nudge = P.pose, P.nudge

CLIPS = {}


def clip(name, keys, length, loop=False, end=None, note=""):
    """A clip and its Bendable Cuboids twin."""
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    CLIPS[name] = P.Clip(name, keys, length, loop, end, note)
    CLIPS[name + "_bent"] = P.Clip(name + "_bent", keys, length, loop, end, note + " (bent elbows and knees)", bent=True)


def held(name, base, jitters, period, note):
    """A pose held for as long as the move lasts (a loop with a small tremble), and its settle back to standing."""
    n = len(jitters) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(jitters)] + [(period, base, "easeinoutsine")]
    clip(name, keys, period, loop=True, note=note)
    clip(name + "_end", [(0, base, "linear")], 5, end=0, note="settles out of " + name)


# ---------------------------------------------------------------------------------------------------------------------
# Slide: feet first and low. The torso reclines, the lead leg reaches out along the ground, the other knee tucks, the
# off hand trails behind on the ground for balance and the main arm comes up in a guard; the head keeps looking ahead.
# Held for the slide's length (BodyRules.SLIDE_TICKS), then it settles up to standing.
SD_BASE = pose(ground="all", lean=-58, twist=-14,
               rl=(-84, 0, 5), rk=8, ll=(-38, 0, -8), lk=110,
               ra=(-44, -22, 6), re=100, la=(96, 0, -26), le=12)
held("body_slide", SD_BASE, [nudge(SD_BASE, rise=0.4, la=(3, 0, 0)), nudge(SD_BASE, rise=-0.2, la=(-2, 0, 0), ra=(2, 0, 0))], 4,
     "feet first: reclined, lead leg out, knee tucked, a hand trailing, a guard up")

# The slide-cancel jump: out of the slide in a tuck (knees up, arms thrown forward), then opening for the landing.
SJ_PUSH = pose(ground=False, lean=-12, rl=(14, 0, 3), rk=25, ll=(-18, 0, -3), lk=35, ra=(-60, -10, 8), re=40, la=(-60, 10, -8), le=40)
SJ_TUCK = pose(ground=False, rise=2, lean=14, rl=(-78, 0, 4), rk=115, ll=(-58, 0, -4), lk=105, ra=(-120, -2, 22), re=55, la=(-114, 2, -22), le=55)
SJ_OPEN = pose(ground=False, rise=1, lean=8, rl=(-22, 0, 4), rk=35, ll=(8, 0, -4), lk=25, ra=(-62, 0, 32), re=25, la=(-62, 0, -32), le=25)
clip("body_slide_jump", [(0, SJ_PUSH, "linear"), (3, SJ_TUCK, "easeoutcubic"), (7, SJ_OPEN, "easeinoutsine"), (9, SJ_OPEN, "linear")], 13,
     note="a tucked lift out of the slide")

# Roll (the soft landing, BodyRules.ROLL_TICKS = 10): a full tuck, knees to chest and arms wrapped round the shins,
# turning once through about the hips while staying low, then opening into a crouch-stand. The tuck turns with the
# body (limbs_body) and the chin stays down (head_x); after the full turn the body angle drops from 360 to 0 at once
# (a constant step, so no frame shows it) and the crouch-stand settles.
RL_LAND = pose(ground="all", limbs_body=True, head_x=20, lean=20,
               rl=(-60, 0, 6), rk=80, ll=(-64, 0, -6), lk=85, ra=(-62, -12, 14), re=60, la=(-62, 12, -14), le=60)
RL_TUCK = pose(RL_LAND, head_x=42, lean=70, shift=2,
               rl=(-112, 0, 6), rk=135, ll=(-116, 0, -6), lk=138, ra=(-50, -26, 22), re=118, la=(-50, 26, -22), le=118)
RL_OPEN = pose(RL_TUCK, head_x=20, lean=360, shift=2, rl=(-82, 0, 6), rk=100, ll=(-86, 0, -6), lk=104,
               ra=(-70, -12, 16), re=70, la=(-70, 12, -16), le=70)
RL_RESET = pose(RL_OPEN, lean=0)
RL_CROUCH = pose(lean=18, rl=(12, 0, 6), rk=72, ll=(-26, 0, -6), lk=62, ra=(-40, -10, 8), re=50, la=(-40, 10, -8), le=50)
# the turn in eighths, each key grounded on whatever part is lowest, so the ball rolls along the floor
rl_keys = [(0, RL_LAND, "linear"), (1.5, RL_TUCK, "easeinquad")]
for i, lean in enumerate((115, 160, 205, 250, 295, 330)):
    rl_keys.append((2.5 + i, pose(RL_TUCK, lean=lean, shift=2 + 3 * math.sin(math.radians(lean / 2))), "linear"))
rl_keys += [(9, RL_OPEN, "easeoutquad"), (9.5, RL_RESET, "constant"), (12, RL_CROUCH, "easeoutcubic"), (14, RL_CROUCH, "linear")]
clip("body_roll", rl_keys, 17, note="tuck, turn once through along the floor, open into a crouch-stand")

# Mantle (BodyRules.MANTLE_TICKS = 6): both arms reach over the lip, pull the chest over it as one knee drives up onto
# the ledge, press down with straight arms, and stand.
MN_REACH = pose(ground=False, lean=-6, rl=(-22, 0, 4), rk=40, ll=(8, 0, -4), lk=15, ra=(-166, -8, 0), re=25, la=(-166, 8, 0), le=25)
MN_PULL = pose(ground=False, rise=1, lean=24, rl=(-86, 0, 4), rk=120, ll=(16, 0, -4), lk=30, ra=(-72, -22, 6), re=120, la=(-72, 22, -6), le=120)
MN_PUSH = pose(ground=False, lean=32, rl=(-72, 0, 4), rk=105, ll=(22, 0, -4), lk=50, ra=(-16, -4, 16), re=15, la=(-16, 4, -16), le=15)
MN_STAND = pose(lean=10, rl=(-10, 0, 3), rk=25, ll=(6, 0, -3), lk=15, ra=(-8, 0, 8), re=20, la=(-8, 0, -8), le=20)
clip("body_mantle", [(0, MN_REACH, "linear"), (2, MN_PULL, "easeinoutsine"), (4, MN_PUSH, "easeoutquad"), (6, MN_STAND, "easeinoutsine"),
                     (7, MN_STAND, "linear")], 10, note="reach, pull with a knee driving up, press, stand")

# Tactical sprint: leaning hard into it, knees high, arms pumping with the hands kept low (bent elbows, short swing).
# One stride pair every 8 ticks, looped for as long as it lasts (BodyRules.TAC_TICKS), then a settle.
TS_A = pose(lean=24, twist=8, rl=(-64, 0, 2), rk=98, ll=(38, 0, -2), lk=72, ra=(42, 0, 8), re=82, la=(-32, 12, -6), le=88)
TS_B = pose(lean=24, rl=(-18, 0, 2), rk=64, ll=(4, 0, -2), lk=18, ra=(4, 0, 8), re=86, la=(4, 0, -8), le=86)
TS_C = pose(lean=24, twist=-8, rl=(38, 0, 2), rk=72, ll=(-64, 0, -2), lk=98, ra=(-32, -12, 6), re=88, la=(42, 0, -8), le=82)
TS_D = pose(lean=24, rl=(4, 0, 2), rk=18, ll=(-18, 0, -2), lk=64, ra=(4, 0, 8), re=86, la=(4, 0, -8), le=86)
clip("body_sprint", [(0, TS_A, "linear"), (2, TS_B, "easeinoutsine"), (4, TS_C, "easeinoutsine"), (6, TS_D, "easeinoutsine"),
                     (8, TS_A, "easeinoutsine")], 8, loop=True, note="hard lean, high knees, arms pumping low")
clip("body_sprint_end", [(0, TS_B, "linear")], 6, end=0, note="settles out of body_sprint")


# ---------------------------------------------------------------------------------------------------------------------
# Output, checks, previews

def document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/body_moves.py: edit the poses there, not this file.",
            "animations": {name: c.json() for name, c in CLIPS.items()}}


def write(root=None):
    path = os.path.join(root or os.path.join(ROOT, "src/main/resources"), OUT_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(document(), fh, separators=(",", ":"))
        fh.write("\n")
    return path


def build(out):
    """build_all.py entry: writes the clips JSON (no textures)."""
    write(out.root)


def check(limit=1.25, quiet=False):
    bad = []
    for name, c in CLIPS.items():
        worst, at, where = 0.0, 0.0, ""
        t = 0.0
        while t <= c.length + 1e-6:
            for pitch in (0.0, 30.0):
                d, w = P.penetration(c.sample(t, pitch), pitch)
                if d > worst:
                    worst, at, where = d, t, w + (f" (look {pitch:g})" if pitch else "")
            t += 0.5
        if worst > limit:
            bad.append(f"{name}: {where} by {worst:.2f} px at tick {at:g}")
        elif not quiet and worst > 0.3:
            print(f"  {name}: {where} {worst:.2f} px at tick {at:g} (allowed)")
    return bad


def moves_sheet(out_dir, px=5.0):
    """Every move's key poses, straight (top) and bent (bottom), from the front three-quarter and the side."""
    from PIL import Image, ImageDraw
    beats = {"body_slide": [0], "body_slide_jump": [0, 3, 7], "body_roll": [2, 4.5, 7, 12], "body_mantle": [0, 2, 4, 6],
             "body_sprint": [0, 2, 4, 6]}
    views = (("front", -35.0, 10.0), ("side", -90.0, 6.0))
    rows = []
    for suffix in ("", "_bent"):
        for vname, yaw, pitch in views:
            cells = []
            clips = [CLIPS[n + suffix] for n in beats]
            bounds = P.strip_bounds(clips, yaw, pitch)
            for n, ts in beats.items():
                c = CLIPS[n + suffix]
                cells += [(f"{n[5:]}{suffix} t{t:g}", P.frame_image(c, t, yaw, pitch, px, bounds)) for t in ts]
            rows.append((f"{vname}{suffix or ' straight'}", cells))
    w = max(im.width for _, r in rows for _, im in r)
    h = max(im.height for _, r in rows for _, im in r)
    cols = max(len(r) for _, r in rows)
    sheet = Image.new("RGBA", (110 + cols * w, len(rows) * (h + 14)), (20, 22, 28, 255))
    dr = ImageDraw.Draw(sheet)
    for j, (label, cells) in enumerate(rows):
        y = j * (h + 14)
        dr.text((6, y + h // 2), label, fill=(230, 232, 240, 255))
        for i, (cap, im) in enumerate(cells):
            sheet.paste(im, (110 + i * w, y + 14))
            dr.text((110 + i * w + 4, y + 1), cap, fill=(200, 205, 215, 255))
    path = os.path.join(out_dir, "moves.png")
    sheet.save(path)
    return path


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        path = write()
        print(f"{len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        bad = check(quiet=True)
        for b in bad:
            print("CLIPPING", b)
        return 1 if bad else 0
    if cmd == "check":
        bad = check()
        for b in bad:
            print("CLIPPING", b)
        print("ok" if not bad else f"{len(bad)} clips clip")
        return 1 if bad else 0
    if cmd == "preview":
        out_dir = argv[1]
        os.makedirs(out_dir, exist_ok=True)
        names = argv[2:] or [n for n in CLIPS if not n.endswith("_end") and not n.endswith("_end_bent")]
        for n in names:
            if n == "moves":
                continue
            print(P.strip(CLIPS[n], out_dir))
        print(moves_sheet(out_dir))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
