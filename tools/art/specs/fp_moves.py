"""First-person view clips (Hands, SPEC §24; docs/anims/PLAN.md): how your own hands move through your eyes.

    python3 tools/art/build_all.py fp_moves          assets/awaken/fp_clips/fp_moves.json
    python3 tools/art/specs/fp_moves.py build        the same
    python3 tools/art/specs/fp_moves.py preview DIR [clip ...]
                                                     a frame strip per clip, every tick, drawn from the eye at the hand
                                                     pass's 70° with vanilla's empty arms

The game reads the JSON through dev.awaken.anims.FpClipSet and plays it through core.api.FirstPerson.play(name); a
clip moves vanilla's own hands (and whatever they hold) by an offset each tick. Edit the poses here, never the JSON.

Conventions: view space, x right, y up, z toward you, blocks; then pitch (x, + swings what's in front of you up),
yaw (y) and roll (z, + turns the hand's top to the left), degrees, applied in that order after the move. "main" is the
main hand, "off" the other; authored right-handed (the main hand on the right): the game mirrors x, yaw and roll for a
left main arm. "both": the off arm is drawn even when it holds nothing. Keys are (tick, pose, the curve into it), with
the curves the body's clips use (linear, easeoutcubic, easeinoutsine, easeinquad, easeoutquad, easeoutsine,
easeoutback). A one-shot ends at rest (every value 0), so it gives the hands back to vanilla with nothing to fade; it may
start away from rest when another layer (the world body) hands the arms over. Events: (tick, name), heard through
core.api.FirstPerson.listen (a sound, a hit-stop, a flash).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
OUT_REL = "assets/awaken/fp_clips/fp_moves.json"
EASES = {"linear", "easeoutcubic", "easeinoutsine", "easeinquad", "easeoutquad", "easeoutsine", "easeinsine", "easeincubic",
         "easeoutback", "smooth", "step"}
REST = (0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

CLIPS = {}


def hand(x=0.0, y=0.0, z=0.0, pitch=0.0, yaw=0.0, roll=0.0):
    return (float(x), float(y), float(z), float(pitch), float(yaw), float(roll))


def mirror(h):
    """The same move on the other side (x, yaw and roll flipped)."""
    return (-h[0], h[1], h[2], h[3], -h[4], -h[5])


def clip(name, length, main=(), off=(), both=False, loop=False, events=(), note=""):
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    for track in (main, off):
        last = -1
        for t, h, e in track:
            if e not in EASES:
                raise ValueError(f"{name}: unknown easing {e}")
            if t < last or t > length:
                raise ValueError(f"{name}: keys out of order or past the length")
            last = t
        if track and not loop and any(abs(v) > 1e-9 for v in track[-1][1]):
            raise ValueError(f"{name}: a one-shot must end at rest")
    CLIPS[name] = dict(length=length, loop=loop, both=both, main=list(main), off=list(off), events=list(events), note=note)


# ---------------------------------------------------------------------------------------------------------------------
# Leaving a climb line (SPEC §17.23). On the line itself the hands are drawn on it in the world (body.client.GripView),
# and for the first ticks after letting go they drop away there; these clips take the hands over from below.

# Kicked off the wall: the hands come up from below thrown out for balance as you fly back, then settle.
KICK_UP = hand(0.08, 0.1, -0.04, 14, 0, 12)
clip("fp_pipe_kick", 12,
     main=[(0, hand(0, -0.6), "linear"), (4, hand(0, -0.6), "linear"), (7, KICK_UP, "easeoutcubic"), (12, REST, "easeinoutsine")],
     off=[(0, mirror(hand(0, -0.6)), "linear"), (4, mirror(hand(0, -0.6)), "linear"), (7, mirror(KICK_UP), "easeoutcubic"),
          (12, REST, "easeinoutsine")],
     both=True, events=[(1, "push")], note="hands thrown out for balance off the wall")


def pipe_side(left):
    """Swung out to a side: the lead hand reaches out that way for the next hold, the other trails low."""
    lead = hand(-0.16, 0.12, -0.1, 22, 14, -16)  # the off (left) hand reaching left, still in view
    trail = hand(0.04, -0.1, 0.06, -10, 0, 8)
    main_keys = [(0, hand(0, -0.6), "linear"), (3, hand(0, -0.6), "linear"),
                 (6, mirror(lead) if not left else trail, "easeoutcubic"), (11, REST, "easeinoutsine")]
    off_keys = [(0, hand(0, -0.6), "linear"), (3, hand(0, -0.6), "linear"),
                (6, lead if left else mirror(trail), "easeoutcubic"), (11, REST, "easeinoutsine")]
    return main_keys, off_keys


m, o = pipe_side(True)
clip("fp_pipe_side_l", 11, main=m, off=o, both=True, note="the left hand reaches out for the next hold")
m, o = pipe_side(False)
clip("fp_pipe_side_r", 11, main=m, off=o, both=True, note="the right hand reaches out for the next hold")

# Down at the line's foot: the hands come down with the crouch, dip, spring back up past rest and settle.
LAND_DIP = hand(0.02, -0.14, -0.04, -16, 0, 0)
LAND_SPRING = hand(0, 0.03, 0, 4, 0, 0)
clip("fp_pipe_land", 10,
     main=[(0, hand(0, -0.6), "linear"), (3, LAND_DIP, "easeoutcubic"), (6, LAND_SPRING, "easeinoutsine"), (10, REST, "easeinoutsine")],
     off=[(0, mirror(hand(0, -0.6)), "linear"), (3, mirror(LAND_DIP), "easeoutcubic"), (6, mirror(LAND_SPRING), "easeinoutsine"),
          (10, REST, "easeinoutsine")],
     both=True, events=[(3, "land")], note="hands down with the crouch, spring back")


# ---------------------------------------------------------------------------------------------------------------------
# Output

def document():
    out = {}
    for name, c in CLIPS.items():
        d = {"length": c["length"], "loop": c["loop"], "both": c["both"]}
        for track in ("main", "off"):
            if c[track]:
                d[track] = [[t] + [round(v, 4) for v in h] + [e] for t, h, e in c[track]]
        if c["events"]:
            d["events"] = [[t, n] for t, n in c["events"]]
        out[name] = d
    return {"schemaVersion": 1, "__comment": "GENERATED by tools/art/specs/fp_moves.py: edit the poses there, not this file.",
            "clips": out}


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


# ---------------------------------------------------------------------------------------------------------------------
# Preview: vanilla's empty arm (FirstPersonHandsAndItemsRenderer.renderPlayerArm, as anims.client.Hands draws it), moved
# by the clip, drawn from the eye.

def ease(name, t):
    t = max(0.0, min(1.0, t))
    return {"linear": t, "smooth": t * t * (3 - 2 * t), "easeinquad": t * t, "easeoutquad": 1 - (1 - t) ** 2,
            "easeincubic": t ** 3, "easeoutcubic": 1 - (1 - t) ** 3, "easeinoutsine": 0.5 - 0.5 * math.cos(math.pi * t),
            "easeoutsine": math.sin(t * math.pi / 2), "easeinsine": 1 - math.cos(t * math.pi / 2),
            "easeoutback": 1 + 2.70158 * (t - 1) ** 3 + 1.70158 * (t - 1) ** 2, "step": 0.0 if t < 1 else 1.0}[name]


def sample(track, t):
    if not track:
        return REST
    if t <= track[0][0]:
        return track[0][1]
    for (ta, ha, _), (tb, hb, e) in zip(track, track[1:]):
        if t <= tb:
            u = ease(e, (t - ta) / (tb - ta)) if tb > ta else 1.0
            return tuple(a + (b - a) * u for a, b in zip(ha, hb))
    return track[-1][1]


def _mat_t(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def _mat_r(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == "x":
        return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]
    if axis == "y":
        return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def _apply(m, p):
    return tuple(sum(m[i][k] * (p[k] if k < 3 else 1) for k in range(4)) for i in range(3))


def arm_matrix(h, right):
    inv = 1 if right else -1
    m = _mat_t(1, 0, 0)
    m = [[1 if i == j else 0 for j in range(4)] for i in range(4)]
    for step in (_mat_t(h[0], h[1], h[2]), _mat_r("x", h[3]), _mat_r("y", h[4]), _mat_r("z", h[5]),
                 _mat_t(inv * 0.64, -0.6, -0.72), _mat_r("y", inv * 45), _mat_t(inv * -1.0, 3.6, 3.5), _mat_r("z", inv * 120),
                 _mat_r("x", 200), _mat_r("y", inv * -135), _mat_t(inv * 5.6, 0, 0),
                 # the arm part: its pivot, vanilla's 0.1 rad tilt, the model's 1/16 scale
                 _mat_t(inv * -5 / 16, 2 / 16, 0), _mat_r("z", inv * math.degrees(0.1))):
        m = _mul(m, step)
    return m


def arm_quads(h, right):
    """The arm's six faces in view space (a wide arm: 4 × 12 × 4 px from its pivot)."""
    m = arm_matrix(h, right)
    x0, x1 = (-3 / 16, 1 / 16) if right else (-1 / 16, 3 / 16)
    y0, y1, z0, z1 = -2 / 16, 10 / 16, -2 / 16, 2 / 16
    c = [_apply(m, (x, y, z)) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    faces = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
    return [[c[i] for i in f] for f in faces]


def frame(c, tick, w=320, h=180):
    from PIL import Image, ImageDraw
    im = Image.new("RGB", (w, h), (24, 28, 36))
    dr = ImageDraw.Draw(im)
    f = 1 / math.tan(math.radians(35))
    quads = []
    mh, oh = sample(c["main"], tick), sample(c["off"], tick)
    quads += [(q, (196, 150, 112)) for q in arm_quads(mh, True)]
    if c["both"] or c["off"]:
        quads += [(q, (176, 132, 98)) for q in arm_quads(oh, False)]
    quads.sort(key=lambda qc: sum(p[2] for p in qc[0]) / 4)
    for q, col in quads:
        if any(p[2] > -0.05 for p in q):
            continue
        pts = [(w / 2 + p[0] / -p[2] * f * h / 2, h / 2 - p[1] / -p[2] * f * h / 2) for p in q]
        shade = 0.75 + 0.25 * abs(q[0][2] - q[2][2]) / (abs(q[0][2] - q[2][2]) + 0.05)
        dr.polygon(pts, fill=tuple(int(v * shade) for v in col), outline=(40, 30, 24))
    dr.text((4, 4), f"{tick:g}", fill=(220, 225, 235))
    return im


def strip(name, out_dir):
    from PIL import Image
    c = CLIPS[name]
    ticks = [t * 1.0 for t in range(int(c["length"]) + 1)]
    frames = [frame(c, t) for t in ticks]
    im = Image.new("RGB", (len(frames) * 324, 184), (10, 12, 16))
    for i, fr in enumerate(frames):
        im.paste(fr, (2 + i * 324, 2))
    path = os.path.join(out_dir, name + ".png")
    im.save(path)
    return path


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        path = write()
        print(f"{len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        return 0
    if cmd == "preview":
        out_dir = argv[1]
        os.makedirs(out_dir, exist_ok=True)
        for n in argv[2:] or list(CLIPS):
            print(strip(n, out_dir))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
