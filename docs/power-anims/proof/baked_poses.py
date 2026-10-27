"""Proof for docs/power-anims/PLAN.md: baked pose sheets for the stomp and the beam, before and after.

    python3 docs/power-anims/proof/baked_poses.py OUT_DIR

Uses the production rig tool (tools/art/specs/power_casts.py: its Clip, bake and forward kinematics) as a library. It
writes nothing into src/ and registers nothing the game reads: the "after" poses live only here, as a scratch proof that
the rig can say them (arms and legs, bent twin) without clipping, and to put the named cast points (core, palm, foot,
ground) on the figure. The "before" rows are the shipped clips (cast_kinetic is what Shockwave plays today, cast_energy
what Beam plays) with the effect anchor the game uses today (client/fx/Anchors.java HAND) drawn on the same frames.

Also writes numbers.json: the third-person gap between the old anchor and the drawn palm, and the first-person screen
positions of the fist and of the beam's start (computed from the constants in CastAnims.offset and Anchors.hand; no
client was run).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, os.path.join(ROOT, "tools/art/specs"))
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
import power_casts as PC  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

pose, nudge, NEUTRAL = PC.pose, PC.nudge, PC.NEUTRAL
R = PC.R

# ---------------------------------------------------------------------------------------------------------------------
# The "after" poses (scratch: not registered, not written to the game's JSON)

# Kinetic stomp: coil on the off leg, knee up and hang (the beat before the thump), plant (the thump), rebound.
ST_COIL = pose(lean=-5, rise=0.6, ra=(-52, 0, 22), la=(-52, 0, -22), re=100, le=100, rl=(-64, 0, 4), ll=(6, 0, -3), rk=100, lk=14)
ST_HANG = pose(lean=-9, rise=1.4, ra=(-26, 0, 42), la=(-26, 0, -42), re=45, le=45, rl=(-80, 0, 4), ll=(6, 0, -3), rk=104, lk=12)
ST_PLANT = pose(lean=15, shift=1.0, ra=(-22, 0, 16), la=(-22, 0, -16), re=38, le=38, rl=(-10, 0, 6), ll=(12, 0, -8), rk=22, lk=70)
ST_REBOUND = pose(ST_PLANT, lean=9, shift=0.4, ra=(-12, 0, 14), la=(-12, 0, -14), rl=(-6, 0, 6), ll=(8, 0, -7), rk=18, lk=40)
ST_SETTLE = pose(lean=3, ra=(-6, 0, 8), la=(-6, 0, -8), re=20, le=20, rl=(0, 0, 4), ll=(2, 0, -4), rk=8, lk=12)
STOMP = [(0, NEUTRAL, "linear"), (2, ST_COIL, "easeoutquad"), (4, ST_HANG, "easeoutsine"), (5, ST_HANG, "linear"),
         (6, ST_PLANT, "easeinexpo"), (8, ST_REBOUND, "easeoutsine"), (12, ST_SETTLE, "easeinoutsine")]

# Energy, the beam through the core: both hands gather at the sternum, the main arm lays straight along the aim with the
# open palm to the target and the off hand steadies its wrist; a braced stance.
BC_GATHER = pose(lean=-3, twist=8, ra=(-62, -34, 0), la=(-62, 34, 0), re=105, le=105, rl=(14, 0, 8), ll=(-10, 0, -8), rk=30, lk=34)
BC_DRAW = pose(BC_GATHER, lean=-6, twist=14, ra=(-50, -30, 0), la=(-50, 30, 0), re=110, le=110, rk=36, lk=40)
BC_AIM = pose(lean=10, shift=1.2, twist=-10, ra=(-90, -2, 0), ra_aim=True, re=0, la=(-78, 50, 0), le=70, rl=(22, 0, 9), ll=(-16, 0, -9), rk=14, lk=40)
BC_HOLD = pose(BC_AIM, lean=9)
BEAM_CORE = [(0, NEUTRAL, "linear"), (3, BC_GATHER, "easeoutquad"), (5, BC_DRAW, "easeinoutsine"), (7, BC_AIM, "easeoutexpo"),
             (11, BC_HOLD, "easeinoutsine")]

# ---------------------------------------------------------------------------------------------------------------------
# Named cast points by forward kinematics on the baked bones (model px, y down, front -z, the right side at -x)

def cast_points(b, pitch):
    fr = PC.part_frames(b, pitch)

    def at(part, local):
        key = part + ":lower" if part + ":lower" in fr and local[1] > PC.JOINTS.get(part, (99,))[0] else part
        M, T = fr[key]
        q = R._mv(M, local)
        return (q[0] + T[0], q[1] + T[1], q[2] + T[2])

    return {
        "palm": at("right_arm", (-1, 10.5, 0)),
        "palm_off": at("left_arm", (1, 10.5, 0)),
        "core": at("torso", (0, 3.5, -2.4)),
        "foot": at("right_leg", (0, 12.2, 0)),
        "foot_off": at("left_leg", (0, 12.2, 0)),
    }


def old_anchor(pitch_deg, off=False):
    """Anchors.hand in third person (eye + look*0.35 + side*0.36 - 0.45), in model px under the same transform."""
    p = math.radians(pitch_deg)
    fwd, right = 0.35, (-1 if off else 1) * 0.36
    up = 1.62 + (-math.sin(p)) * fwd - 0.45
    forward = math.cos(p) * fwd
    return (-right * 16, 24 - up * 16, -forward * 16)


# ---------------------------------------------------------------------------------------------------------------------
# Drawing

def frame_marked(c, tick, yaw, pitch, px, bounds, marks, look=8.0):
    img = PC.frame_image(c, tick, yaw, pitch, px, bounds, look)
    dr = ImageDraw.Draw(img)
    minx, _, miny, _ = bounds
    offx, offy = 8 - minx * px, 8 - miny * px
    b = c.sample(tick, look)
    pts = cast_points(b, look)
    pts = {k: v for k, v in pts.items() if k in marks}
    if "anchor" in marks:
        pts["anchor"] = old_anchor(look)
    if "ground" in marks:
        f = cast_points(b, look)["foot"]
        pts["ground"] = (f[0], 24.0, f[2])
    proj = PC._project(list(pts.values()), yaw, pitch)
    for (k, _), q in zip(pts.items(), proj):
        x, y = q[0] * px + offx, q[1] * px + offy
        col = {"palm": (255, 214, 90), "palm_off": (255, 214, 90), "core": (90, 230, 255), "foot": (255, 120, 100),
               "foot_off": (255, 120, 100), "ground": (255, 70, 60), "anchor": (255, 80, 220)}[k]
        r = 6 if k == "core" else 5
        if k == "anchor":
            dr.ellipse((x - 7, y - 7, x + 7, y + 7), outline=col + (255,), width=3)
        elif k == "ground":
            dr.ellipse((x - 9, y - 3, x + 9, y + 3), outline=col + (255,), width=2)
            dr.ellipse((x - 3, y - 1, x + 3, y + 1), fill=col + (255,))
        else:
            dr.ellipse((x - r, y - r, x + r, y + r), fill=col + (255,), outline=(20, 20, 24, 255))
    return img


def storyboard(c, ticks, marks, title, path, views=(("3/4 front", -30.0, 12.0), ("side", -90.0, 6.0)), px=5.0, labels=None):
    rows = []
    for _, yaw, pitch in views:
        bounds = PC.strip_bounds([c], yaw, pitch)
        rows.append([frame_marked(c, t, yaw, pitch, px, bounds, marks) for t in ticks])
    w = max(im.width for r in rows for im in r)
    h = max(im.height for r in rows for im in r)
    sheet = Image.new("RGBA", (len(ticks) * w, len(rows) * h + 40), (20, 22, 28, 255))
    dr = ImageDraw.Draw(sheet)
    dr.text((6, 4), title, fill=(230, 232, 240, 255))
    for i, t in enumerate(ticks):
        lab = f"t{t} ({t * 50} ms)" + (f"  {labels[i]}" if labels else "")
        dr.text((i * w + 6, 22), lab, fill=(200, 205, 215, 255))
    for j, row in enumerate(rows):
        for i, im in enumerate(row):
            sheet.paste(im, (i * w, 40 + j * h))
    sheet.convert("RGB").save(path, optimize=True)
    return path


# ---------------------------------------------------------------------------------------------------------------------
# First person: where the fist is and where the beam starts, on screen (NDC: x right, y up, -1..1 over the half-frame)

def ndc(x, y, z, fov_deg, aspect):
    t = math.tan(math.radians(fov_deg / 2))
    return (x / -z / t / aspect, y / -z / t)


def first_person(aspect=16 / 9):
    out = {}
    rest = (0.744, -0.455, -1.072)  # core.api.FirstPerson.View.REST_FIST (vanilla's resting empty right fist, hand pass)
    # CastAnims.offset AIM / AIM_HOLD (env 1): translate(-0.2, +0.16, -0.24) then pitch 12 degrees about the eye
    p = math.radians(12)
    y2 = rest[1] * math.cos(p) - rest[2] * math.sin(p)
    z2 = rest[1] * math.sin(p) + rest[2] * math.cos(p)
    fist = (rest[0] - 0.2, y2 + 0.16, z2 - 0.24)
    out["fist_view"] = fist
    out["fist_ndc_hand_pass_70"] = ndc(*fist, 70, aspect)
    out["rest_fist_ndc"] = ndc(*rest, 70, aspect)
    # Anchors.hand, first person, level look: eye + look*0.55 + right*0.32 - 0.3 up
    a = (0.32, -0.30, -0.55)
    out["old_beam_start_view"] = a
    for f in (70, 90, 110):
        out[f"old_beam_start_ndc_fov{f}"] = ndc(*a, f, aspect)
    # where the beam passes at the fist's height (straight line from its start to the crosshair)
    s = out["old_beam_start_ndc_fov70"]
    f = out["fist_ndc_hand_pass_70"]
    t = (f[1] - s[1]) / (0 - s[1])
    out["old_beam_x_at_fist_height_fov70"] = s[0] * (1 - t)
    return out


def third_person_gap():
    """Beam today: the anchor against the drawn right palm in the energy cast's thrust and hold, at a few look pitches."""
    rows = []
    for name, p in (("E_THRUST", PC.E_THRUST), ("E_HOLD", PC.E_HOLD)):
        for look in (-40.0, 0.0, 40.0):
            b = PC.bake(p)
            pt = cast_points(b, look)["palm"]
            an = old_anchor(look)
            d = [(pt[i] - an[i]) / 16 for i in range(3)]
            rows.append({"pose": name, "look_pitch_deg": look, "palm_ahead_of_anchor_blocks": round(-d[2], 2),
                         "palm_above_anchor_blocks": round(-d[1], 2), "gap_blocks": round(math.sqrt(sum(x * x for x in d)), 2)})
    return rows


def main(out):
    os.makedirs(out, exist_ok=True)
    reg = {}

    def mk(name, keys, length, end=None, bent=True):
        c = PC.Clip(name, keys, length, end=end, bent=bent)
        reg[name] = c
        return c

    stomp = mk("stomp_after", STOMP, 16, end=12)
    beam = mk("beam_core_after", BEAM_CORE, 14, end=11)
    # the shipped clips, bent twins (what the pack plays with Bendable Cuboids)
    before_k = PC.CLIPS["cast_kinetic_bent"]
    before_e = PC.CLIPS["cast_energy_bent"]
    hold_e = PC.CLIPS["hold_energy_bent"]

    paths = [
        storyboard(before_k, [0, 2, 4, 6, 8, 10, 12], {"foot", "ground"},
                   "BEFORE  Shockwave today: the Kinetic cast clip is a punch (CastClips: every Kinetic power plays cast_kinetic). Foot and ground marked.",
                   os.path.join(out, "stomp_before.png")),
        storyboard(stomp, [0, 2, 4, 5, 6, 8, 12], {"foot", "ground"},
                   "AFTER (scratch proof)  Stomp: coil on the off leg, knee up, hang, plant. Red ring = where the wave starts (the planted foot).",
                   os.path.join(out, "stomp_after.png"),
                   labels=["", "coil", "knee up", "hang", "PLANT", "rebound", "settle"]),
        storyboard(before_e, [0, 3, 5, 7, 9, 11, 13], {"palm", "anchor"},
                   "BEFORE  Beam today (cast_energy): gold = the drawn right palm, magenta ring = where the beam starts (Anchors.HAND).",
                   os.path.join(out, "beam_before.png")),
        storyboard(beam, [0, 3, 5, 7, 9, 11, 14], {"palm", "core"},
                   "AFTER (scratch proof)  Beam through the core: gather at the sternum (cyan), arm to the aim, the beam starts at the palm (gold).",
                   os.path.join(out, "beam_after.png"),
                   labels=["", "gather", "draw", "thrust", "", "hold", ""]),
    ]
    # clipping check for the scratch clips (the production check's limit is 1.25 px)
    clip_report = {}
    for n, c in reg.items():
        worst, at, where = 0.0, 0.0, ""
        t = 0.0
        while t <= c.length + 1e-6:
            for pitch in (0.0, 30.0):
                d, w = PC.penetration(c.sample(t, pitch), pitch)
                if d > worst:
                    worst, at, where = d, t, w
            t += 0.5
        clip_report[n] = {"worst_px": round(worst, 2), "at_tick": at, "where": where}
    # the cast points' tracks, for the plan (model px; right = -x, up = 24 - y, forward = -z)
    track = {}
    for n, c in reg.items():
        rows = []
        for t in range(0, int(c.length) + 1):
            pts = cast_points(c.sample(t, 8.0), 8.0)
            rows.append({"tick": t, **{k: [round(-v[0] / 16, 2), round((24 - v[1]) / 16, 2), round(-v[2] / 16, 2)] for k, v in pts.items()}})
        track[n] = rows
    doc = {"first_person": first_person(), "third_person_gap_beam_today": third_person_gap(), "clipping": clip_report,
           "tracks_right_up_forward_blocks_from_feet": track}
    with open(os.path.join(out, "numbers.json"), "w") as fh:
        json.dump(doc, fh, indent=1)
    for p in paths:
        print(p)
    print(json.dumps({k: doc[k] for k in ("first_person", "clipping")}, indent=1))
    for r in doc["third_person_gap_beam_today"]:
        print(r)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "out"))
