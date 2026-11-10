#!/usr/bin/env python3
"""First-person casts, the next level (SPEC §32 "The arm"; docs/powers-next/BUILD.md M4). Every power's cast re-authored for
the arm that can act: a forearm keyed by its elbow, a wrist that turns, light that runs down the arm, the LEFT hand casting
while the right keeps the weapon and lowers it, and a real put-away for the casts that need both hands.

    python3 tools/art/specs/fp_casts2.py build          assets/awaken/fp_clips/fp_casts2.json (schema 2; replaces fp_casts.json's
                                                         clips of the same name: FpClipSet reads it last)
    python3 tools/art/specs/fp_casts2.py check          the authoring rules, as warnings

View space as fp_moves.py: x right, y up, z toward you, blocks. Authored right-handed (the game mirrors for a left main arm):
the casting hand is the off hand, on the left, x < 0. A key is K(tick, x, y, z, elbow=(x, y, z), roll=degrees, ease=...); with no
elbow the arm comes up from under the frame's corner as it always did. The clip's release event sits on the power's release tick
(data/awaken/powers/casts.json), so the effect, the sound and the hand agree.

Names: fp_p_<power> (or the profile's own begin clip), then __run / __air for a way, then _t2 / _t3 for a take. Takes are made
here from the authored clip by small changes of path and timing, seeded by the power, never touching the release pose.

Rules checked (docs/powers-next/PLAN.md 5.1): every pose held two ticks is on screen above the hotbar; no hand sits in the
middle fifth of the view for more than two ticks; the two hands never start on the same tick; a strike overshoots and settles.
"""
import json
import math
import os
import random
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
OUT = os.path.join(ROOT, "src/main/resources/assets/awaken/fp_clips/fp_casts2.json")
CASTS = os.path.join(ROOT, "src/main/resources/data/awaken/powers/casts.json")

L, EOC, EIOS, EOQ, EOS, EIC, EOB, EIQ = "linear", "easeoutcubic", "easeinoutsine", "easeoutquad", "easeoutsine", "easeincubic", "easeoutback", "easeinquad"
REST = (0.744, -0.455, -1.072)      # vanilla's resting right fist
OUT_L = (-0.62, -1.15, -0.85)       # the left hand out of view, under the frame
ANCHOR = (0.48, -0.84, -0.53)       # where the old arm rises from (x for the right arm)
FOREARM = 0.6
CLIPS = OrderedDict()
PROFILES = {p["power"]: p for p in json.load(open(CASTS))["profiles"]}


def K(t, x, y, z, elbow=None, roll=0.0, ease=EOC):
    return dict(t=float(t), p=(float(x), float(y), float(z)), elbow=elbow, roll=float(roll), ease=ease)


def at(t, p, elbow=None, roll=0.0, ease=EOC):
    return K(t, p[0], p[1], p[2], elbow, roll, ease)


def M(t, x=0.0, y=0.0, z=0.0, pitch=0.0, yaw=0.0, roll=0.0, ease=EIOS):
    """A move of the hand that keeps the weapon (offsets)."""
    return dict(t=float(t), v=(float(x), float(y), float(z), float(pitch), float(yaw), float(roll)), ease=ease)


def lower(until, back=4, deep=1.0):
    """The weapon drops into a low guard while the other hand casts, and comes back up after."""
    return [M(0, ease=L), M(2, 0.05 * deep, -0.13 * deep, 0.04 * deep, -7 * deep, 0, 9 * deep, EOC), M(until, 0.05 * deep, -0.13 * deep, 0.04 * deep, -7 * deep, 0, 9 * deep, L),
            M(until + back, ease=EIOS)]


def glow_off(release, tail=10, start=0.0):
    """The left arm's light: runs to the fist by the release, flares as it leaves, dies over the tail."""
    return [(0, 0, 0, start, 0.3 if start else 0, L), (release * 0.5, 0, 0, max(start, 0.55), 0.6, EOS), (release, 0, 0, 1, 1.0, EOS),
            (release + 1, 0, 0, 1, 1.6, L), (release + tail, 0, 0, 1, 0, EOQ)]


def glow_both(release, tail=10, lead=1):
    """Both arms lit; the main arm's light a tick ahead."""
    return [(0, 0, 0, 0, 0, L), (release * 0.5, 0.6, 0.6, 0.5, 0.6, EOS), (release, 1, 1.0, 1, 1.0, EOS), (release + 1, 1, 1.6, 1, 1.6, L),
            (release + tail, 1, 0, 1, 0, EOQ)]


def clip(name, off=None, main=None, moves=None, light=None, release=None, events=(), two=False, loop=False, to_stance=False, length=None, note=""):
    """off / main: fist keys. moves: the main hand's offsets (a one-handed cast). two: both hands cast, the weapon is put away."""
    assert name not in CLIPS, name
    assert not (main and moves), name
    ev = list(events)
    if release is not None and not any(n == "release" for _, n in ev):
        ev.append((release, "release"))
    ev.sort(key=lambda e: e[0])
    last = max([k["t"] for k in (off or [])] + [k["t"] for k in (main or [])] + [m["t"] for m in (moves or [])])
    CLIPS[name] = dict(off=off or [], main=main or [], moves=moves or [], light=light or [], events=ev, two=two, loop=loop, to_stance=to_stance,
                       length=length if length is not None else (last if loop or to_stance else last + 1), release=release, note=note)


# ---- maths ---------------------------------------------------------------------------------------------------------------

def angles(p, elbow, right):
    """The forearm's pitch and yaw (degrees) for a fist at p: from its elbow, or from the old corner anchor."""
    if elbow is None:
        elbow = ((ANCHOR[0] if right else -ANCHOR[0]), ANCHOR[1], ANCHOR[2])
    d = [p[i] - elbow[i] for i in range(3)]
    n = math.sqrt(sum(v * v for v in d)) or 1.0
    d = [v / n for v in d]
    return math.degrees(math.asin(max(-1, min(1, d[1])))), math.degrees(math.atan2(d[0], -d[2]))


def track(keys, right):
    out, prev_yaw = [], None
    for k in keys:
        pitch, yaw = angles(k["p"], k["elbow"], right)
        if prev_yaw is not None:  # the short way round between keys
            while yaw - prev_yaw > 180:
                yaw -= 360
            while yaw - prev_yaw < -180:
                yaw += 360
        prev_yaw = yaw
        out.append([k["t"], round(k["p"][0], 4), round(k["p"][1], 4), round(k["p"][2], 4), round(pitch, 2), round(yaw, 2), round(k["roll"], 2), k["ease"]])
    return out


# ---- takes -----------------------------------------------------------------------------------------------------------------

def take_of(c, name, n):
    """A take: the same cast with its path and timing a little different. The first key, the release pose and the last key stay."""
    rng = random.Random(hash((name, n)) & 0xFFFFFFFF)
    rel = c["release"]

    def vary(keys):
        out = []
        for i, k in enumerate(keys):
            k2 = dict(k)
            fixed = i == 0 or i == len(keys) - 1 or (rel is not None and abs(k["t"] - rel) < 0.01)
            if not fixed:
                a = 0.035 if rel is None or k["t"] < rel else 0.02
                k2["p"] = tuple(v + rng.uniform(-a, a) for v in k["p"])
                if k["elbow"] is not None:
                    k2["elbow"] = tuple(v + rng.uniform(-0.05, 0.05) for v in k["elbow"])
                k2["roll"] = k["roll"] + rng.uniform(-9, 9)
                if rel is not None and k["t"] < rel:
                    lo = out[-1]["t"] + 0.5
                    k2["t"] = round(min(rel - 0.5, max(lo, k["t"] + rng.choice((-0.5, 0.0, 0.5)))), 2)
            out.append(k2)
        return out
    d = dict(c)
    d["off"], d["main"] = vary(c["off"]), vary(c["main"])
    return d


# ---- named places (left hand) ----------------------------------------------------------------------------------------------
HIP = (-0.50, -0.36, -0.64)
CHEST = (-0.18, -0.30, -0.62)
FWD = (-0.14, -0.14, -1.00)
FAR = (-0.05, -0.10, -1.18)
HIGH = (-0.36, 0.34, -0.82)
SIDE = (-0.74, -0.08, -0.80)
TEMPLE = (-0.52, 0.27, -0.54)
LOWF = (-0.24, -0.42, -0.96)
E_HIP = (-0.74, -0.74, -0.42)        # elbows
E_BACK = (-0.80, -0.62, -0.20)
E_LOW = (-0.46, -0.52, -0.54)
E_OUT = (-0.86, -0.34, -0.50)
E_UNDER = (-0.40, -0.80, -0.70)


def mir(p):
    return (-p[0], p[1], p[2])


def rel_of(power):
    return PROFILES[power]["release"]


def one(power, off, release=None, tail=10, light=None, name=None, deep=1.0, note="", events=()):
    """A one-handed cast: the left hand acts, the right lowers the weapon. Makes takes too."""
    r = rel_of(power) if release is None else release
    n = name or PROFILES[power]["begin1p"]
    until = max(k["t"] for k in off) - 3
    clip(n, off=off, moves=lower(max(r + 2, until), deep=deep), light=light or glow_off(r, tail), release=r, note=note, events=events)


def two(power, main, off, release=None, tail=10, light=None, name=None, note=""):
    """A cast with both hands: the weapon is put away for it, and comes back as the hands go home."""
    r = rel_of(power) if release is None else release
    clip(name or PROFILES[power]["begin1p"], off=off, main=main, light=light or glow_both(r, tail), release=r, two=True, note=note)


# ============================================================================================================================
# Kinetic: weight. Short pulls, hard stops.
# ============================================================================================================================

# Haymaker: the left fist drawn back where you can see it, the light climbing the arm; the let-go crosses the whole view.
DRAWN = (-0.66, -0.24, -0.56)
clip("fp_p_haymaker",
     off=[at(0, OUT_L, ease=L), K(2, -0.54, -0.36, -0.68, E_HIP, 10), K(9, -0.62, -0.28, -0.60, E_BACK, 18, EIOS), at(18, DRAWN, E_BACK, 24, EIOS)],
     moves=[M(0, ease=L), M(3, 0.06, -0.14, 0.05, -8, 0, 10, EOC), M(18, 0.07, -0.16, 0.06, -9, 0, 12, EIOS)],
     light=[(0, 0, 0, 0, 0, L), (4, 0, 0, 0.2, 0.4, EOS), (18, 0, 0, 1, 0.95, L)],
     events=[(18, "hold")], to_stance=True, length=18, note="the left fist drawn back and up, in view; the weapon low")


def shake(p, k, a=0.008):
    return (p[0] + a * k, p[1] + 0.8 * a * k, p[2])


clip("fp_p_haymaker_hold",
     off=[at(0, DRAWN, E_BACK, 24, L), at(1, shake(DRAWN, 1), E_BACK, 26, EIOS), at(2, shake(DRAWN, -1), E_BACK, 22, EIOS), at(3, shake(DRAWN, 0.5), E_BACK, 25, EIOS),
          at(4, DRAWN, E_BACK, 24, EIOS)],
     moves=[M(0, 0.07, -0.16, 0.06, -9, 0, 12, L), M(4, 0.07, -0.16, 0.06, -9, 0, 12, L)],
     light=[(0, 0, 0, 1, 0.9, L), (2, 0, 0, 1, 1.0, EIOS), (4, 0, 0, 1, 0.9, EIOS)], loop=True, length=4, note="held, shaking")


def punch(name, land, through, elbow_back, note):
    clip(name,
         off=[at(0, DRAWN, E_BACK, 24, L), K(1, land[0] - 0.24, land[1] - 0.10, land[2] + 0.30, elbow_back, -20), at(2, land, (land[0] - 0.22, land[1] - 0.20, land[2] + 0.52), -62),
              at(4, through, (through[0] - 0.24, through[1] - 0.22, through[2] + 0.50), -70, EOS), K(7, -0.20, -0.24, -1.00, E_LOW, -30, EIOS), at(14, OUT_L, ease=EIOS)],
         moves=[M(0, 0.07, -0.16, 0.06, -9, 0, 12, L), M(2, 0.10, -0.18, 0.10, -12, -6, 14, EOC), M(8, 0.05, -0.12, 0.04, -6, 0, 8, EIOS), M(13, ease=EIOS)],
         light=[(0, 0, 0, 1, 1.0, L), (2, 0, 0, 1, 1.7, L), (6, 0, 0, 1, 0.6, EOQ), (13, 0, 0, 1, 0, EOQ)], release=2, note=note)


punch("fp_p_haymaker_end", (-0.02, -0.08, -1.26), (-0.06, -0.12, -1.16), (-0.70, -0.50, -0.30), "the punch across the view to the crosshair, past it, and back")
punch("fp_p_haymaker_end__air", (-0.02, -0.34, -1.10), (-0.04, -0.44, -1.00), (-0.56, 0.10, -0.40), "thrown downward out of the air, a hammer blow")
punch("fp_p_haymaker_end__run", (0.02, -0.06, -1.34), (-0.02, -0.10, -1.24), (-0.74, -0.44, -0.24), "a running straight with the whole stride behind it")

# Terrain Rip: both hands dig in low, haul the ground up past the face, and throw.
two("terrain_rip",
    main=[at(0, REST, ease=L), K(2, 0.34, -0.50, -0.86, mir(E_UNDER), 30), K(4, 0.30, -0.10, -0.74, (0.50, -0.62, -0.50), 10, EIOS), K(6, 0.22, 0.22, -0.92, (0.46, -0.30, -0.56), -20),
          K(9, 0.26, 0.10, -0.98, (0.48, -0.40, -0.56), -10, EIOS), at(15, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(3, -0.36, -0.50, -0.84, E_UNDER, -30), K(5, -0.32, -0.06, -0.72, (-0.52, -0.60, -0.50), -10, EIOS), K(6.5, -0.24, 0.24, -0.90, (-0.48, -0.28, -0.56), 20),
         K(9.5, -0.28, 0.12, -0.96, (-0.50, -0.38, -0.56), 10, EIOS), at(15, OUT_L, ease=EIOS)],
    note="dig in low, haul up, throw")

# Brace: the left forearm snaps up across the view like a bar, the weapon tucks in behind it.
BAR = (0.08, -0.17, -0.66)
one("brace",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.30, -0.22, -0.60, (-0.70, -0.40, -0.52), -40), at(3, BAR, (-0.52, -0.26, -0.60), -80, EOB), K(4.5, 0.05, -0.19, -0.68, (-0.54, -0.28, -0.60), -80, EOS),
         K(9, 0.06, -0.18, -0.67, (-0.53, -0.27, -0.60), -80, L), at(15, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (3, 0, 0, 1, 1.4, EOC), (9, 0, 0, 1, 0.8, L), (15, 0, 0, 1, 0, EOQ)], note="the forearm across, a bar")

# Thunderclap: both hands thrown wide, then they meet in front of your face.
two("thunderclap",
    main=[at(0, REST, ease=L), K(2, 0.78, -0.02, -0.74, (0.86, -0.50, -0.40), -30), at(4, (0.05, -0.12, -0.80), (0.56, -0.30, -0.52), -80, EIC), K(5, 0.16, -0.14, -0.78, (0.60, -0.34, -0.52), -60, EOS),
          at(12, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(2.5, -0.80, 0.02, -0.74, (-0.88, -0.48, -0.40), 30), at(4, (-0.05, -0.12, -0.80), (-0.56, -0.30, -0.52), 80, EIC), K(5.5, -0.18, -0.13, -0.78, (-0.60, -0.34, -0.52), 60, EOS),
         at(12, OUT_L, ease=EIOS)],
    note="thrown wide, then the clap")

# ============================================================================================================================
# Velocity: the hand is already gone.
# ============================================================================================================================

# Dash: the left hand is swept back past your hip as the body goes; the weapon trails.
one("dash",
    off=[at(0, OUT_L, ease=L), K(1, -0.46, -0.30, -0.80, E_LOW, 0), at(3, (-0.84, -0.34, -0.46), (-0.60, -0.50, -0.80), 40), K(6, -0.80, -0.40, -0.50, (-0.60, -0.56, -0.80), 30, EIOS),
         at(10, OUT_L, ease=EIOS)],
    light=glow_off(3, 6), deep=0.7, note="swept back past the hip")
one("dash", name="fp_p_dash__air",
    off=[at(0, OUT_L, ease=L), K(1, -0.40, -0.10, -0.80, E_LOW, 0), at(3, (-0.86, 0.10, -0.50), (-0.56, -0.30, -0.78), 60), K(6, -0.82, 0.02, -0.54, (-0.58, -0.36, -0.78), 40, EIOS),
         at(10, OUT_L, ease=EIOS)],
    light=glow_off(3, 6), deep=0.7, note="in the air: the arm thrown back and up like a wing")

# Afterimage: the hand peels something off your chest and leaves it behind.
one("afterimage",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.08, -0.34, -0.56, (-0.56, -0.56, -0.50), -60), at(3, (-0.70, -0.20, -0.70), E_OUT, 20), K(5, -0.78, -0.16, -0.62, E_OUT, 30, EOS), at(10, OUT_L, ease=EIOS)],
    light=glow_off(3, 7), deep=0.6, note="peeled off the chest and left behind")

# Flow: the hand rises slowly, turns over, and presses the air flat.
one("flow",
    off=[at(0, OUT_L, ease=L), K(3, -0.40, -0.14, -0.74, E_HIP, 60, EIOS), K(5, -0.34, 0.10, -0.80, (-0.60, -0.42, -0.56), 0, EIOS), at(6, (-0.30, 0.04, -0.92), (-0.56, -0.44, -0.58), -85, EOS),
         K(12, -0.30, 0.00, -0.94, (-0.56, -0.46, -0.58), -85, EIOS), at(19, OUT_L, ease=EIOS)],
    tail=14, note="rises, turns over, presses the air flat")

# Lightning Step: the fist snaps out along the line you are about to become.
one("lightning_step",
    off=[at(0, OUT_L, ease=L), K(1, -0.40, -0.26, -0.70, E_HIP, 0), at(2, (-0.10, -0.10, -1.22), (-0.34, -0.34, -0.72), -40, EOB), K(4, -0.14, -0.13, -1.10, (-0.36, -0.36, -0.66), -30, EOS),
         at(9, OUT_L, ease=EIOS)],
    light=glow_off(2, 6), deep=0.6, note="snapped out along the line")

# ============================================================================================================================
# Flight and gravity: slow hands, heavy air.
# ============================================================================================================================

# Gravity Well: the hand is lifted, turned palm down, and pressed onto the spot as if it weighed a ton.
one("gravity_well",
    off=[at(0, OUT_L, ease=L), K(2, -0.36, 0.24, -0.80, (-0.60, -0.30, -0.56), 0), at(4, (-0.26, -0.20, -0.98), (-0.48, 0.20, -0.56), -90, EIC), K(6, -0.27, -0.14, -0.96, (-0.50, 0.22, -0.56), -90, EOS),
         K(12, -0.27, -0.16, -0.96, (-0.50, 0.20, -0.56), -90, L), at(18, OUT_L, ease=EIOS)],
    tail=14, note="lifted, turned over, pressed down hard")

# Telekinesis: the hand goes out and closes on something far away, and strains.
GRIP = (-0.20, -0.04, -1.04)
clip("fp_p_telekinesis",
     off=[at(0, OUT_L, ease=L), K(1.5, -0.36, -0.26, -0.72, E_HIP, 0), at(3, GRIP, (-0.50, -0.40, -0.62), -50, EOB)],
     moves=[M(0, ease=L), M(3, 0.05, -0.13, 0.04, -7, 0, 9, EOC)],
     light=[(0, 0, 0, 0, 0, L), (3, 0, 0, 1, 1.2, EOS)], events=[(3, "hold")], release=3, to_stance=True, length=3, note="out, and closes on it")
clip("fp_p_telekinesis_hold",
     off=[at(0, GRIP, (-0.50, -0.40, -0.62), -50, L), at(1, shake(GRIP, 1, 0.006), (-0.50, -0.40, -0.62), -46, EIOS), at(2, shake(GRIP, -1, 0.006), (-0.50, -0.40, -0.62), -54, EIOS),
          at(3, shake(GRIP, 0.5, 0.006), (-0.50, -0.40, -0.62), -48, EIOS), at(4, GRIP, (-0.50, -0.40, -0.62), -50, EIOS)],
     moves=[M(0, 0.05, -0.13, 0.04, -7, 0, 9, L), M(4, 0.05, -0.13, 0.04, -7, 0, 9, L)],
     light=[(0, 0, 0, 1, 0.85, L), (2, 0, 0, 1, 1.0, EIOS), (4, 0, 0, 1, 0.85, EIOS)], loop=True, length=4, note="straining")

# Gravity Flip: both hands scoop the room up, turn it over, and let it drop.
two("gravity_flip",
    main=[at(0, REST, ease=L), K(2, 0.30, -0.46, -0.80, mir(E_UNDER), 60), K(4, 0.34, 0.20, -0.86, (0.52, -0.36, -0.58), -40, EIOS), at(5, (0.30, -0.30, -0.94), (0.50, 0.14, -0.60), -90, EIC),
          K(7, 0.31, -0.24, -0.92, (0.50, 0.14, -0.60), -90, EOS), at(13, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(2.5, -0.32, -0.46, -0.80, E_UNDER, -60), K(4.5, -0.36, 0.22, -0.86, (-0.54, -0.34, -0.58), 40, EIOS), at(5, (-0.32, -0.28, -0.94), (-0.52, 0.16, -0.60), 90, EIC),
         K(7.5, -0.33, -0.22, -0.92, (-0.52, 0.16, -0.60), 90, EOS), at(13, OUT_L, ease=EIOS)],
    note="scooped up, turned over, dropped")

# ============================================================================================================================
# Energy: drawn in to the chest, then given to the sky.
# ============================================================================================================================

# Beam: the left palm comes up from the core and locks on the aim, the forearm straight down the beam.
AIM = (-0.26, -0.18, -0.98)
E_AIM = (-0.44, -0.44, -0.46)
clip("fp_beam_gather",
     off=[at(0, OUT_L, ease=L), K(2, -0.12, -0.44, -0.60, (-0.56, -0.60, -0.50), -70), K(3, -0.10, -0.38, -0.56, (-0.56, -0.58, -0.50), -70, EIOS), at(5, AIM, E_AIM, -88, EOC)],
     moves=[M(0, ease=L), M(3, 0.06, -0.15, 0.05, -8, 0, 10, EOC), M(5, 0.07, -0.16, 0.06, -9, 0, 12, EIOS)],
     light=[(0, 0, 0, 0, 0, L), (3, 0, 0, 0.6, 0.7, EOS), (5, 0, 0, 1, 1.3, EOS)],
     events=[(2, "gather"), (3, "core"), (5, "hold")], release=5, to_stance=True, length=5, note="up from the core, palm locked on the aim")
clip("fp_beam_hold",
     off=[at(0, AIM, E_AIM, -88, L), at(1, shake(AIM, 1, 0.005), E_AIM, -86, EIOS), at(2, shake(AIM, -1, 0.005), E_AIM, -90, EIOS), at(3, shake(AIM, 0.5, 0.005), E_AIM, -87, EIOS),
          at(4, AIM, E_AIM, -88, EIOS)],
     moves=[M(0, 0.07, -0.16, 0.06, -9, 0, 12, L), M(4, 0.07, -0.16, 0.06, -9, 0, 12, L)],
     light=[(0, 0, 0, 1, 1.0, L), (2, 0, 0, 1, 1.15, EIOS), (4, 0, 0, 1, 1.0, EIOS)], loop=True, length=4, note="the aim held")

# Nova: forearms crossed tight at the chest, a held beat, then thrown wide, the right a tick ahead.
two("nova",
    main=[at(0, REST, ease=L), K(2, -0.10, -0.10, -0.66, (0.60, -0.26, -0.56), 40), K(4, -0.14, -0.12, -0.62, (0.56, -0.30, -0.54), 55, EIOS), at(5, (0.66, 0.04, -0.90), (0.52, -0.56, -0.52), -20, EOC),
          K(6.5, 0.76, 0.10, -0.94, (0.56, -0.50, -0.54), -30, EOS), K(10, 0.66, -0.04, -0.90, (0.56, -0.58, -0.52), -10, EIOS), at(16, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(2.5, 0.06, -0.26, -0.70, (-0.62, -0.38, -0.58), -40), K(4.5, 0.10, -0.28, -0.66, (-0.58, -0.42, -0.56), -55, EIOS), at(5, (-0.40, -0.14, -0.80), (-0.58, -0.56, -0.50), 10, EOC),
         K(6, -0.72, 0.06, -0.94, (-0.56, -0.52, -0.54), 30, EOC), K(10.5, -0.64, -0.08, -0.90, (-0.56, -0.60, -0.52), 10, EIOS), at(16, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (2.5, 0.6, 0.7, 0.5, 0.7, EOS), (4.5, 1, 1.0, 1, 1.0, EOS), (5.5, 1, 1.7, 1, 1.7, L), (9, 0.8, 0.9, 0.8, 0.9, EOQ), (16, 0.4, 0, 0.4, 0, EOQ)],
    note="crossed at the chest, a held beat, thrown wide")
two("nova", name="fp_p_nova__air",
    main=[at(0, REST, ease=L), K(2, 0.10, -0.30, -0.62, (0.56, -0.40, -0.50), 30), K(4, 0.08, -0.32, -0.60, (0.54, -0.42, -0.50), 40, EIOS), at(5, (0.62, -0.40, -0.84), (0.50, 0.10, -0.52), -60, EOC),
          K(7, 0.68, -0.34, -0.88, (0.52, 0.12, -0.54), -50, EOS), at(14, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(2.5, -0.10, -0.32, -0.62, (-0.56, -0.42, -0.50), -30), K(4.5, -0.08, -0.34, -0.60, (-0.54, -0.44, -0.50), -40, EIOS),
         at(5, (-0.36, -0.36, -0.74), (-0.52, 0.08, -0.52), 30, EOC), K(6, -0.66, -0.38, -0.86, (-0.52, 0.10, -0.54), 55, EOC), at(14, OUT_L, ease=EIOS)],
    note="in the air: gathered, then thrown down and out under you")

# Barrier: the forearm stands straight up between you and them, then leans into it.
WALL = (-0.22, 0.20, -0.78)
one("barrier",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.34, -0.20, -0.62, E_HIP, -40), at(3, WALL, (-0.26, -0.40, -0.78), -85, EOB), K(5, -0.21, 0.18, -0.84, (-0.25, -0.42, -0.82), -85, EOS),
         K(11, -0.21, 0.18, -0.84, (-0.25, -0.42, -0.82), -85, L), at(17, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (3, 0, 0, 1, 1.4, EOC), (11, 0, 0, 1, 0.8, L), (17, 0, 0, 1, 0, EOQ)], note="the forearm upright, a wall, then leaned into")

# Overload: both fists driven down and held shaking, then thrown up and out as it goes.
two("overload",
    main=[at(0, REST, ease=L), K(2.5, 0.44, -0.40, -0.74, (0.62, 0.04, -0.52), 60), K(4, 0.45, -0.42, -0.72, (0.62, 0.06, -0.52), 64, EIOS), K(5.5, 0.43, -0.40, -0.74, (0.62, 0.04, -0.52), 58, EIOS),
          at(7, (0.58, 0.30, -0.82), (0.60, -0.30, -0.56), -30, EOC), K(9, 0.64, 0.36, -0.86, (0.62, -0.24, -0.58), -40, EOS), at(17, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(3, -0.46, -0.40, -0.74, (-0.64, 0.04, -0.52), -60), K(4.5, -0.47, -0.42, -0.72, (-0.64, 0.06, -0.52), -64, EIOS), K(6, -0.45, -0.40, -0.74, (-0.64, 0.04, -0.52), -58, EIOS),
         at(7, (-0.52, 0.22, -0.80), (-0.62, -0.34, -0.56), 20, EOC), K(8, -0.62, 0.34, -0.86, (-0.62, -0.26, -0.58), 40, EOC), at(17, OUT_L, ease=EIOS)],
    tail=12, note="driven down and shaking, then thrown up and out")

# ============================================================================================================================
# Shadow: quiet hands. Things are drawn over, never thrown.
# ============================================================================================================================

# Shadow Step: the hand comes down past your eyes like a hood being pulled.
one("shadow_step",
    off=[at(0, OUT_L, ease=L), K(1, -0.44, 0.34, -0.60, (-0.76, 0.00, -0.50), 70), at(3, (-0.30, -0.30, -0.66), (-0.74, -0.24, -0.50), 90, EIC), K(5, -0.34, -0.34, -0.66, (-0.76, -0.30, -0.50), 80, EOS),
         at(10, OUT_L, ease=EIOS)],
    light=glow_off(3, 6), deep=0.6, note="pulled down past the eyes like a hood")

# Cloak: the forearm sweeps across your whole view, left to right and gone, drawing the dark after it.
one("cloak",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.70, 0.06, -0.62, (-0.90, -0.40, -0.40), -60), at(4, (0.30, -0.02, -0.60), (-0.30, -0.10, -0.56), -85, EIOS), K(6, 0.44, -0.10, -0.60, (-0.16, -0.14, -0.56), -85, EOS),
         at(12, OUT_L, ease=EIOS)],
    tail=12, note="swept across the whole view")

# Echo Swap: a quick turn of the wrist, as if flipping a coin over.
one("echo_swap",
    off=[at(0, OUT_L, ease=L), K(2, -0.36, -0.20, -0.74, E_HIP, -80), at(4, (-0.34, -0.14, -0.78), E_HIP, 100, EOB), K(6, -0.35, -0.16, -0.77, E_HIP, 90, EOS), at(11, OUT_L, ease=EIOS)],
    light=glow_off(4, 6), deep=0.6, note="the wrist flips over")

# Legion: the hand held low, palm up, and raised slowly as they stand.
one("legion",
    off=[at(0, OUT_L, ease=L), K(2, -0.34, -0.46, -0.84, (-0.56, -0.52, -0.40), 90), K(6, -0.32, -0.10, -0.86, (-0.56, -0.44, -0.44), 90, EIOS), K(9, -0.30, 0.06, -0.86, (-0.56, -0.40, -0.46), 90, EOS),
         K(16, -0.30, 0.04, -0.86, (-0.56, -0.40, -0.46), 90, L), at(23, OUT_L, ease=EIOS)],
    tail=16, note="palm up, raised slowly: rise")

# ============================================================================================================================
# Mind: small. The hand never comes between you and what you are looking at.
# ============================================================================================================================

# Dominate: the hand goes out beside the target, off the crosshair, and closes.
one("dominate",
    off=[at(0, OUT_L, ease=L), K(3, -0.40, -0.10, -0.80, E_HIP, 0, EIOS), K(6, -0.34, 0.06, -0.96, (-0.56, -0.36, -0.56), -20, EIOS), at(8, (-0.36, 0.04, -0.90), (-0.56, -0.38, -0.52), 70, EOB),
         K(14, -0.36, 0.03, -0.90, (-0.56, -0.38, -0.52), 70, L), at(20, OUT_L, ease=EIOS)],
    tail=12, note="out beside the target, then the wrist turns: mine")

# Read Intent: two knuckles to the temple at the edge of the view.
one("read_intent",
    off=[at(0, OUT_L, ease=L), K(3, -0.56, 0.10, -0.60, (-0.86, -0.36, -0.44), 20, EIOS), at(6, TEMPLE, (-0.84, -0.20, -0.40), 30, EOS), K(12, -0.52, 0.27, -0.54, (-0.84, -0.20, -0.40), 30, L),
         at(18, OUT_L, ease=EIOS)],
    tail=12, deep=0.5, note="knuckles to the temple, at the edge of the view")

# Decoy: a flick of the hand out to the side: go.
one("decoy",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.22, -0.28, -0.66, (-0.60, -0.46, -0.50), -40), at(3, (-0.84, -0.10, -0.78), (-0.50, -0.40, -0.60), 30, EOB), K(5, -0.78, -0.14, -0.76, (-0.50, -0.42, -0.60), 20, EOS),
         at(10, OUT_L, ease=EIOS)],
    light=glow_off(3, 6), deep=0.5, note="flicked out to the side")

# Foresight: the hand passes across your eyes once, slowly, and the world is different after it.
one("foresight",
    off=[at(0, OUT_L, ease=L), K(3, -0.74, 0.20, -0.56, (-0.92, -0.30, -0.36), -70, EIOS), at(7, (0.34, 0.22, -0.56), (-0.26, 0.10, -0.50), -85, EIOS), K(9, 0.50, 0.14, -0.58, (-0.10, 0.04, -0.50), -85, EOS),
         at(15, OUT_L, ease=EIOS)],
    tail=10, note="passed once across the eyes")

# ============================================================================================================================
# Architect: exact. Straight lines and square stops.
# ============================================================================================================================

one("wall",
    off=[at(0, OUT_L, ease=L), K(2, -0.30, -0.30, -0.66, E_HIP, -80), K(5, -0.28, 0.20, -0.78, (-0.34, -0.38, -0.66), -85, EIOS), at(7, (-0.26, 0.20, -0.98), (-0.32, -0.30, -0.56), -85, EOC),
         K(9, -0.26, 0.19, -0.94, (-0.32, -0.32, -0.56), -85, EOS), at(15, OUT_L, ease=EIOS)],
    note="a flat palm raised and pushed square: stand there")
one("ramp",
    off=[at(0, OUT_L, ease=L), K(2, -0.50, -0.42, -0.70, (-0.80, -0.50, -0.30), 0), at(5, (-0.20, 0.16, -1.06), (-0.50, -0.26, -0.66), -40, EOC), K(7, -0.22, 0.12, -1.00, (-0.50, -0.30, -0.64), -40, EOS),
         at(13, OUT_L, ease=EIOS)],
    note="swept from low and near to high and far: the slope")
one("floor",
    off=[at(0, OUT_L, ease=L), K(2, -0.30, -0.10, -0.80, (-0.56, -0.50, -0.50), -90), at(4, (-0.28, -0.40, -0.92), (-0.52, 0.06, -0.60), -90, EIC), K(5.5, -0.28, -0.36, -0.92, (-0.52, 0.08, -0.60), -90, EOS),
         at(12, OUT_L, ease=EIOS)],
    note="a flat hand pressed down level")
one("cone",
    off=[at(0, OUT_L, ease=L), K(2, -0.44, -0.10, -0.80, E_HIP, -20), at(5, (-0.24, 0.32, -0.84), (-0.46, -0.24, -0.60), 0, EOC), K(7, -0.06, 0.06, -0.86, (-0.40, -0.36, -0.60), 30, EIOS),
         at(13, OUT_L, ease=EIOS)],
    note="up to a point and down the other side")
two("rampart",
    main=[at(0, REST, ease=L), K(3, 0.50, -0.46, -0.72, (0.70, -0.60, -0.30), 80), at(6, (0.66, 0.14, -0.84), (0.68, -0.46, -0.50), 80, EOC), K(8, 0.64, 0.10, -0.84, (0.68, -0.48, -0.50), 80, EOS),
          at(15, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(3.5, -0.52, -0.46, -0.72, (-0.72, -0.60, -0.30), -80), at(6, (-0.60, 0.08, -0.82), (-0.70, -0.48, -0.50), -80, EOC), K(7, -0.66, 0.14, -0.84, (-0.70, -0.46, -0.50), -80, EOC),
         K(9, -0.64, 0.10, -0.84, (-0.70, -0.48, -0.50), -80, EOS), at(15, OUT_L, ease=EIOS)],
    note="both palms up from the ground, wide: the wall rises")
one("spire",
    off=[at(0, OUT_L, ease=L), K(2, -0.34, -0.46, -0.80, (-0.60, -0.30, -0.40), 90), at(5, (-0.30, 0.30, -0.84), (-0.42, -0.30, -0.70), 90, EOC), K(7, -0.30, 0.24, -0.84, (-0.42, -0.34, -0.70), 90, EOS),
         at(13, OUT_L, ease=EIOS)],
    note="a fist driven straight up from the ground: up")
one("collapse",
    off=[at(0, OUT_L, ease=L), K(3, -0.32, 0.36, -0.80, (-0.44, -0.22, -0.66), 0, EIOS), K(5, -0.31, 0.38, -0.80, (-0.44, -0.20, -0.66), 0, EIOS), at(6, (-0.34, -0.38, -0.80), (-0.48, 0.16, -0.60), 60, EIC),
         K(8, -0.34, -0.32, -0.80, (-0.48, 0.18, -0.60), 60, EOS), at(14, OUT_L, ease=EIOS)],
    note="a fist raised, a held beat, yanked down: come down")
two("citadel",
    main=[at(0, REST, ease=L), K(3, 0.30, 0.40, -0.78, (0.46, -0.18, -0.64), -20), K(5, 0.29, 0.42, -0.78, (0.46, -0.16, -0.64), -20, EIOS), at(6, (0.32, -0.36, -0.86), (0.48, 0.20, -0.62), -90, EIC),
          K(8, 0.32, -0.30, -0.86, (0.48, 0.22, -0.62), -90, EOS), at(16, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(3.5, -0.32, 0.40, -0.78, (-0.48, -0.18, -0.64), 20), K(5.5, -0.31, 0.42, -0.78, (-0.48, -0.16, -0.64), 20, EIOS), at(6, (-0.33, -0.20, -0.84), (-0.50, 0.24, -0.62), 70, EIC),
         K(7, -0.34, -0.36, -0.86, (-0.50, 0.20, -0.62), 90, EOC), K(9, -0.34, -0.30, -0.86, (-0.50, 0.22, -0.62), 90, EOS), at(16, OUT_L, ease=EIOS)],
    tail=12, note="both hands overhead, then brought down: the gates shut")

# ============================================================================================================================
# Tech: the arm is a tool. Locked wrists, small precise motions, a kick when something fires.
# ============================================================================================================================

one("grapple",
    off=[at(0, OUT_L, ease=L), K(2, -0.30, -0.26, -0.80, (-0.44, -0.50, -0.30), 0), at(4, (-0.22, -0.16, -1.06), (-0.36, -0.36, -0.50), 0, EOC), K(5, -0.24, -0.10, -0.96, (-0.38, -0.36, -0.42), 0, EOC),
         K(8, -0.23, -0.15, -1.02, (-0.37, -0.36, -0.46), 0, EIOS), at(14, OUT_L, ease=EIOS)],
    note="the forearm levelled like a barrel; it kicks when the line fires")
one("drone",
    off=[at(0, OUT_L, ease=L), K(2, -0.36, -0.44, -0.72, (-0.60, -0.50, -0.30), 90), at(4, (-0.32, 0.20, -0.84), (-0.52, -0.32, -0.60), 90, EOC), K(6, -0.32, 0.12, -0.84, (-0.52, -0.36, -0.60), 90, EOS),
         at(12, OUT_L, ease=EIOS)],
    note="tossed up underhand")
one("turret",
    off=[at(0, OUT_L, ease=L), K(3, -0.30, -0.30, -0.80, (-0.50, 0.10, -0.50), -90, EIOS), at(5, (-0.26, -0.44, -1.00), (-0.46, 0.00, -0.60), -90, EIC), K(7, -0.26, -0.40, -1.00, (-0.46, 0.02, -0.60), -90, EOS),
         at(13, OUT_L, ease=EIOS)],
    note="set down ahead with a flat hand: there")
one("nanite_skin",
    off=[at(0, OUT_L, ease=L), K(2, -0.20, -0.06, -0.66, (-0.66, -0.34, -0.56), -90, EIOS), at(4, (-0.16, -0.02, -0.64), (-0.66, -0.30, -0.56), 90, EIOS), K(8, -0.16, -0.04, -0.64, (-0.66, -0.32, -0.56), 180, EIOS),
         at(14, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (2, 0, 0, 0.3, 0.6, EOS), (8, 0, 0, 1, 1.1, L), (14, 0, 0, 1, 0, EOQ)], note="the forearm held up and turned over as the plates run round it")
two("drone_swarm",
    main=[at(0, REST, ease=L), K(3, 0.34, -0.40, -0.76, (0.60, -0.50, -0.34), -90), at(5, (0.36, 0.34, -0.84), (0.52, -0.24, -0.66), -90, EOC), K(7, 0.36, 0.28, -0.84, (0.52, -0.28, -0.66), -90, EOS),
          at(14, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(3.5, -0.36, -0.40, -0.76, (-0.62, -0.50, -0.34), 90), at(5, (-0.34, 0.20, -0.82), (-0.54, -0.30, -0.64), 90, EOC), K(6, -0.38, 0.36, -0.84, (-0.54, -0.22, -0.66), 90, EOC),
         K(8, -0.38, 0.30, -0.84, (-0.54, -0.26, -0.66), 90, EOS), at(14, OUT_L, ease=EIOS)],
    note="both hands thrown up: go")
# Jet Burst is the boots': the hands only brace (the weapon rides along).
clip("fp_p_jet_burst",
     off=[at(0, OUT_L, ease=L), K(1, -0.56, -0.44, -0.66, (-0.60, 0.06, -0.50), -90), at(2, (-0.58, -0.50, -0.64), (-0.60, 0.00, -0.50), -90, EIC), K(4, -0.58, -0.46, -0.64, (-0.60, 0.02, -0.50), -90, EOS),
          at(9, OUT_L, ease=EIOS)],
     moves=[M(0, ease=L), M(2, 0.02, -0.16, 0.04, -14, 0, 0, EIC), M(5, 0.01, -0.08, 0.02, -6, 0, 0, EOS), M(9, ease=EIOS)],
     light=glow_off(2, 5), release=2, note="both hands shoved down against the thrust")

# ============================================================================================================================
# Spirit: an open hand, breath, weather.
# ============================================================================================================================

# Spirit Palm: back to the hip, then the flat palm driven out along the aim; it recoils and holds while the echo travels.
def palm(name, land, hold, note):
    clip(name,
         off=[at(0, OUT_L, ease=L), K(1.5, -0.46, -0.36, -0.64, (-0.74, -0.74, -0.42), -30), at(3, land, (land[0] - 0.33, land[1] - 0.32, land[2] + 0.50), -90, EOC),
              at(5, hold, (hold[0] - 0.34, hold[1] - 0.32, hold[2] + 0.46), -74, EOS), at(8, hold, (hold[0] - 0.34, hold[1] - 0.32, hold[2] + 0.46), -74, L), at(13, OUT_L, ease=EIOS)],
         moves=lower(7), light=[(0, 0, 0, 0, 0, L), (1.5, 0, 0, 0.5, 0.7, EOS), (3, 0, 0, 1, 1.7, L), (5, 0, 0, 1, 0.7, EOQ), (13, 0, 0, 0.4, 0, EOQ)], release=3, note=note)


palm("fp_p_spirit_palm", (-0.05, -0.10, -1.16), (-0.12, -0.16, -1.02), "the flat palm driven out along the aim")
palm("fp_p_spirit_palm__air", (-0.04, -0.32, -1.06), (-0.10, -0.36, -0.96), "driven downward from the air")
palm("fp_p_spirit_palm__run", (0.00, -0.08, -1.24), (-0.16, -0.18, -1.04), "a running palm with the whole stride in it")

# Mend: the hand comes to the chest and stays for a long breath, then opens away.
one("mend",
    off=[at(0, OUT_L, ease=L), K(3, -0.06, -0.38, -0.56, (-0.56, -0.50, -0.50), -70, EIOS), K(7, -0.04, -0.34, -0.54, (-0.56, -0.46, -0.50), -70, EIOS), at(10, (-0.50, -0.20, -0.80), E_HIP, 20, EIOS),
         at(17, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (3, 0, 0, 0.5, 0.6, EOS), (7, 0, 0, 1, 1.2, EIOS), (12, 0, 0, 1, 0.6, EOQ), (17, 0, 0, 1, 0, EOQ)], note="held at the chest for a breath, then opened")

# Third Eye: the fist touches the brow at the top of the view and is drawn away, opening it.
one("third_eye",
    off=[at(0, OUT_L, ease=L), K(3, -0.14, 0.30, -0.54, (-0.60, -0.10, -0.46), 0, EIOS), at(5, (-0.10, 0.33, -0.52), (-0.60, -0.06, -0.46), 0, EOS), K(9, -0.50, 0.24, -0.70, (-0.76, -0.24, -0.44), 40, EIOS),
         at(15, OUT_L, ease=EIOS)],
    deep=0.5, note="touched to the brow, drawn away")

# Cord Lash: the arm goes back over the shoulder and cracks forward across the view.
one("cord_lash",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.70, 0.30, -0.50, (-0.80, -0.24, -0.34), 40), at(3, (0.10, -0.16, -1.04), (-0.38, -0.20, -0.64), -60, EIC), K(5, 0.22, -0.26, -0.94, (-0.30, -0.26, -0.60), -70, EOS),
         at(11, OUT_L, ease=EIOS)],
    light=glow_off(3, 7), note="back over the shoulder, cracked forward across the view")

# Bulwark: the fist punches down, then the open hand comes up and the dome comes with it.
one("bulwark",
    off=[at(0, OUT_L, ease=L), K(2, -0.32, -0.42, -0.86, (-0.50, 0.08, -0.56), -30), K(3.5, -0.32, -0.44, -0.86, (-0.50, 0.06, -0.56), -30, EOS), at(5, (-0.34, 0.26, -0.84), (-0.50, -0.30, -0.60), 90, EOC),
         K(8, -0.34, 0.20, -0.84, (-0.50, -0.34, -0.60), 90, EOS), at(15, OUT_L, ease=EIOS)],
    tail=12, note="punched down, then raised open: the dome comes up with the hand")

# Void Maw: the hand reaches, opens wide, and closes as if on a throat.
one("void_maw",
    off=[at(0, OUT_L, ease=L), K(3, -0.36, -0.12, -0.84, E_HIP, -60, EIOS), at(6, (-0.26, -0.02, -1.06), (-0.52, -0.38, -0.60), -60, EOC), K(8, -0.30, -0.06, -0.94, (-0.54, -0.40, -0.56), 60, EOB),
         K(14, -0.30, -0.08, -0.94, (-0.54, -0.40, -0.56), 60, L), at(20, OUT_L, ease=EIOS)],
    tail=14, note="reached out, then the wrist closes and pulls back")

# Waking Dream: the hand draws one slow line down the air and pushes it open.
one("waking_dream",
    off=[at(0, OUT_L, ease=L), K(2, -0.30, 0.34, -0.84, (-0.50, -0.20, -0.60), 0, EIOS), K(5, -0.30, -0.24, -0.86, (-0.50, -0.40, -0.40), 0, EIOS), at(6, (-0.26, -0.06, -1.04), (-0.48, -0.36, -0.56), -85, EOC),
         K(9, -0.27, -0.08, -1.00, (-0.48, -0.36, -0.56), -85, EOS), at(15, OUT_L, ease=EIOS)],
    note="a line drawn down the air, then pushed open")

# Storm Call: the fist goes straight up at the sky, hangs, and is pulled down.
one("storm_call",
    off=[at(0, OUT_L, ease=L), K(3, -0.30, 0.44, -0.78, (-0.36, -0.16, -0.74), 0, EOC), K(5, -0.30, 0.46, -0.78, (-0.36, -0.14, -0.74), 0, EIOS), at(6, (-0.34, -0.36, -0.80), (-0.46, 0.20, -0.62), 40, EIC),
         K(8, -0.34, -0.30, -0.80, (-0.46, 0.22, -0.62), 40, EOS), at(15, OUT_L, ease=EIOS)],
    tail=12, note="the fist straight up, a hang, pulled down hard")

# Ember Step: a low flick forward that throws sparks ahead of your feet.
one("ember_step",
    off=[at(0, OUT_L, ease=L), K(1, -0.50, -0.44, -0.66, E_HIP, 0), at(2, (-0.20, -0.42, -1.04), (-0.44, -0.46, -0.50), -30, EOB), K(4, -0.24, -0.44, -0.98, (-0.46, -0.48, -0.50), -20, EOS),
         at(9, OUT_L, ease=EIOS)],
    light=glow_off(2, 6), deep=0.6, note="a low flick forward")

# Frost Nova: the arm swings back, then the flat hand slaps the ground and the cold runs out from under it.
one("frost_nova",
    off=[at(0, OUT_L, ease=L), K(2, -0.66, 0.16, -0.60, (-0.80, -0.36, -0.36), 20), K(3.5, -0.68, 0.20, -0.58, (-0.80, -0.34, -0.36), 20, EIOS), at(5, (-0.26, -0.44, -0.94), (-0.50, 0.06, -0.60), -90, EIC),
         K(7, -0.26, -0.40, -0.94, (-0.50, 0.08, -0.60), -90, EOS), K(11, -0.26, -0.40, -0.94, (-0.50, 0.08, -0.60), -90, L), at(17, OUT_L, ease=EIOS)],
    tail=12, note="swung back, then slapped flat on the ground")
one("frost_nova", name="fp_p_frost_nova__air",
    off=[at(0, OUT_L, ease=L), K(2, -0.40, 0.36, -0.70, (-0.56, -0.18, -0.50), 0), K(3.5, -0.40, 0.38, -0.70, (-0.56, -0.16, -0.50), 0, EIOS), at(5, (-0.20, -0.46, -0.90), (-0.40, 0.08, -0.66), -90, EIC),
         K(7, -0.20, -0.42, -0.90, (-0.40, 0.10, -0.66), -90, EOS), at(15, OUT_L, ease=EIOS)],
    tail=12, note="in the air: brought straight down from overhead")

# Ghost Step: the palm goes forward as if through a curtain, and you follow it.
one("ghost_step",
    off=[at(0, OUT_L, ease=L), K(1, -0.30, -0.14, -0.84, E_HIP, -85), at(2, (-0.20, -0.06, -1.14), (-0.44, -0.34, -0.66), -85, EOS), K(5, -0.56, -0.10, -0.80, E_OUT, -40, EIOS), at(10, OUT_L, ease=EIOS)],
    light=glow_off(2, 6), deep=0.6, note="the palm goes through first, then parts the curtain")

# Twin Strike: the fist crosses to the far side and comes back: there are two of you.
one("twin_strike",
    off=[at(0, OUT_L, ease=L), K(2, -0.50, -0.14, -0.74, E_HIP, 0), at(4, (0.36, -0.10, -0.72), (-0.24, -0.20, -0.60), -60, EOC), K(7, -0.40, -0.12, -0.76, E_HIP, 0, EIOS), at(13, OUT_L, ease=EIOS)],
    note="crossed to the far side and back")

# Hundred Palms: both hands, faster and faster, alternating; the last one stays out.
def flurry():
    main, off = [at(0, REST, ease=L)], [at(0, OUT_L, ease=L)]
    t, gap = 2.0, 2.4
    for i in range(12):
        right = i % 2 == 0
        x = (0.10 + 0.05 * (i % 3)) * (1 if right else -1)
        y = -0.20 + 0.07 * ((i * 5) % 3)
        out = (x, y, -1.14)
        back = (0.36 * (1 if right else -1), -0.34, -0.68)
        e_out = (x + (0.30 if right else -0.30), y - 0.28, -0.66)
        tr = main if right else off
        tr.append(at(round(t, 2), out, e_out, -88 if right else 88, EOC))
        tr.append(at(round(t + gap * 0.9, 2), back, mir(E_HIP) if right else E_HIP, -40 if right else 40, EIOS))
        t += gap
        gap = max(1.0, gap * 0.86)
    main.append(at(round(t + 1, 2), (0.04, -0.10, -1.24), (0.34, -0.40, -0.74), -90, EOC))
    off.append(at(round(t + 1.5, 2), (-0.34, -0.32, -0.70), E_HIP, 40, EIOS))
    end = round(t + 9, 2)
    main.append(at(end, REST, ease=EIOS))
    off.append(at(end, OUT_L, ease=EIOS))
    return main, off, end


_m, _o, _end = flurry()
clip("fp_p_hundred_palms", main=_m, off=_o, two=True, release=rel_of("hundred_palms"),
     light=[(0, 0, 0, 0, 0, L), (2, 1, 0.9, 1, 0.9, EOS), (_end - 9, 1, 1.2, 1, 1.2, L), (_end - 8, 1, 1.8, 1, 1.4, L), (_end, 1, 0, 1, 0, EOQ)],
     note="alternating palms, faster and faster; the last stays out")

# Shockwave: the stomp stays the boot's (fp_stomp). Its other ways are the hands'.
clip("fp_stomp__air",
     off=[at(0, OUT_L, ease=L), K(2, -0.34, 0.30, -0.70, (-0.56, -0.22, -0.52), 0), K(4, -0.33, 0.34, -0.70, (-0.56, -0.20, -0.52), 0, EIOS), at(5, (-0.10, -0.46, -0.92), (-0.40, 0.04, -0.60), -60, EIC),
          K(7, -0.10, -0.42, -0.92, (-0.40, 0.06, -0.60), -60, EOS), K(10, -0.10, -0.42, -0.92, (-0.40, 0.06, -0.60), -60, L), at(16, OUT_L, ease=EIOS)],
     moves=lower(9), light=glow_off(5, 10), release=5, note="in the air: you come down fist first, knuckles into the ground")
clip("fp_stomp__run",
     off=[at(0, OUT_L, ease=L), K(2, -0.62, -0.30, -0.60, E_HIP, 30), at(5, (-0.74, -0.44, -0.52), (-0.70, 0.06, -0.50), 60, EIC), K(8, -0.72, -0.40, -0.54, (-0.70, 0.08, -0.50), 50, EOS),
          at(14, OUT_L, ease=EIOS)],
     moves=[M(0, ease=L), M(3, 0.04, -0.10, 0.04, -10, 0, 14, EOC), M(5, 0.06, -0.20, 0.06, -16, 0, 18, EIC), M(9, 0.03, -0.08, 0.02, -6, 0, 8, EOS), M(14, ease=EIOS)],
     light=glow_off(5, 8), release=5, note="at a sprint: a slide, the left hand down on the ground for balance")


# ---- output ----------------------------------------------------------------------------------------------------------------

TAKEN = set()


# ============================================================================================================================
# Earned (docs/powers-next, SPEC 50 "Earned powers"). Brand and the Lances are made to be finished by the weapon, so the right
# hand barely lowers it: the blow can follow at once.
# ============================================================================================================================

# Brand: the palm goes out flat and stays there, pressed on, while the mark takes; then the fist closes and comes away.
one("brand",
    off=[at(0, OUT_L, ease=L), K(1.5, -0.48, -0.34, -0.62, E_HIP, -30), at(3, (-0.06, -0.11, -1.16), (-0.38, -0.44, -0.66), -90, EOC), K(4.5, -0.13, -0.17, -1.04, (-0.46, -0.48, -0.58), -90, EOS),
         K(7, -0.12, -0.16, -1.06, (-0.45, -0.48, -0.60), -90, EIOS), K(9, -0.26, -0.24, -0.88, (-0.54, -0.52, -0.50), -10, EIOS), at(14, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (1.5, 0, 0, 0.5, 0.7, EOS), (3, 0, 0, 1, 1.7, L), (7, 0, 0, 1, 1.3, L), (9, 0, 0, 0.8, 0.5, EOQ), (14, 0, 0, 0.3, 0, EOQ)],
    deep=0.6, note="pressed flat on it and held while the mark takes, then closed and drawn away")

# Hoarfrost Lances: the hand lifts something heavy off the ground beside you, up past your eyes, and leaves it hanging over the shoulder.
one("hoarfrost_lances",
    off=[at(0, OUT_L, ease=L), K(2, -0.52, -0.40, -0.76, E_LOW, 60, EIOS), K(4.5, -0.50, 0.02, -0.82, (-0.66, -0.50, -0.56), 20, EIOS), at(6, (-0.46, 0.36, -0.80), (-0.58, -0.20, -0.62), -40, EOC),
         K(8, -0.47, 0.32, -0.80, (-0.58, -0.24, -0.62), -40, EOS), K(11, -0.50, 0.22, -0.78, (-0.62, -0.30, -0.58), -20, EIOS), at(17, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (2, 0, 0, 0.4, 0.6, EOS), (6, 0, 0, 1, 1.5, EOS), (8, 0, 0, 1, 0.9, EOQ), (17, 0, 0, 0.6, 0, EOQ)],
    deep=0.5, tail=12, note="lifted from beside you up over the shoulder, and left hanging")

# Updraft: the hand scoops the air off the ground in front of you and throws it straight up.
one("updraft",
    off=[at(0, OUT_L, ease=L), K(2, -0.40, -0.44, -0.80, (-0.60, -0.14, -0.50), -90, EIOS), K(3.5, -0.22, -0.46, -0.96, (-0.46, -0.20, -0.56), -60, EIOS), at(5, (-0.34, 0.42, -0.80), (-0.40, -0.18, -0.76), 90, EOC),
         K(7, -0.34, 0.38, -0.80, (-0.40, -0.22, -0.76), 90, EOS), at(14, OUT_L, ease=EIOS)],
    tail=12, note="scooped off the ground, thrown straight up")
one("updraft", name="fp_p_updraft__air",
    off=[at(0, OUT_L, ease=L), K(2, -0.36, 0.30, -0.74, (-0.52, -0.22, -0.54), 0, EIOS), at(5, (-0.26, -0.44, -0.92), (-0.46, 0.08, -0.62), -90, EIC), K(7, -0.26, -0.40, -0.92, (-0.46, 0.10, -0.62), -90, EOS),
         at(14, OUT_L, ease=EIOS)],
    tail=12, note="in the air: the palm pushed down at the ground, and the air comes up to meet it")

# Backdraft: both hands gather the fire of the room in toward your mouth, a held breath, then open forward as it all goes back out.
two("backdraft",
    main=[at(0, REST, ease=L), K(2.5, 0.52, -0.06, -0.92, mir(E_OUT), -40, EIOS), K(5, 0.20, -0.20, -0.62, (0.58, -0.42, -0.50), 40, EIOS), K(6.5, 0.18, -0.18, -0.60, (0.58, -0.40, -0.50), 44, EIOS),
          at(7, (0.40, -0.26, -0.96), (0.56, -0.52, -0.52), -70, EOC), K(9, 0.46, -0.30, -0.98, (0.58, -0.54, -0.54), -70, EOS), K(13, 0.46, -0.32, -0.96, (0.58, -0.56, -0.54), -60, L), at(19, REST, ease=EIOS)],
    off=[at(0, OUT_L, ease=L), K(3, -0.54, -0.04, -0.92, E_OUT, 40, EIOS), K(5.5, -0.22, -0.18, -0.62, (-0.60, -0.40, -0.50), -40, EIOS), K(6.5, -0.20, -0.16, -0.60, (-0.60, -0.38, -0.50), -44, EIOS),
         at(7, (-0.42, -0.24, -0.96), (-0.58, -0.50, -0.52), 70, EOC), K(9.5, -0.48, -0.28, -0.98, (-0.60, -0.52, -0.54), 70, EOS), K(13, -0.48, -0.30, -0.96, (-0.60, -0.54, -0.54), 60, L), at(19, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (3, 0.4, 0.5, 0.4, 0.5, EOS), (6.5, 1, 1.3, 1, 1.3, EIOS), (7.5, 1, 1.8, 1, 1.8, L), (13, 0.8, 0.8, 0.8, 0.8, EOQ), (19, 0.3, 0, 0.3, 0, EOQ)],
    note="gathered in to the mouth, a held breath, then opened forward as it goes back out")

# Thornwake: the hand goes down flat on the ground, digs in, and hauls upward as the roots come.
one("thornwake",
    off=[at(0, OUT_L, ease=L), K(2, -0.44, -0.10, -0.74, (-0.62, -0.50, -0.46), -60, EIOS), at(5, (-0.24, -0.46, -0.92), (-0.48, 0.02, -0.62), -90, EIC), K(6.5, -0.24, -0.44, -0.92, (-0.48, 0.04, -0.62), -90, EOS),
         K(8, -0.26, -0.40, -0.90, (-0.50, 0.04, -0.60), 0, EIOS), at(10.5, (-0.34, 0.06, -0.82), (-0.52, -0.46, -0.58), 0, EOB), K(13, -0.34, 0.00, -0.82, (-0.52, -0.48, -0.58), 0, EIOS), at(19, OUT_L, ease=EIOS)],
    light=[(0, 0, 0, 0, 0, L), (2, 0, 0, 0.4, 0.5, EOS), (5, 0, 0, 1, 1.5, L), (8, 0, 0, 1, 0.9, EOQ), (10.5, 0, 0, 1, 1.3, EOS), (19, 0, 0, 0.4, 0, EOQ)],
    tail=14, note="flat on the ground, dug in, then hauled upward as the roots come")


# ============================================================================================================================
# The three held aims, on the arm that can act. Their fists keep the places the first clips gave them (the ball, the core and
# the rail's line are measured from the hands: powers.fx.CastPoints, Muzzles); what is new is the elbows under them, the wrists,
# the light, and a weapon that is put away and brought back, never popped.
# ============================================================================================================================

def held(name, main, off, light, note):
    """A trembling hold on two fists: (place, elbow, roll) each."""
    (pm, em, rm), (po, eo, ro) = main, off
    clip(name,
         main=[at(0, pm, em, rm, L), at(1, shake(pm, 1, 0.008), em, rm + 3, EIOS), at(2, shake(pm, -1, 0.008), em, rm - 3, EIOS), at(3, shake(pm, 0.5, 0.008), em, rm + 1, EIOS),
               at(4, pm, em, rm, EIOS)],
         off=[at(0, po, eo, ro, L), at(1.5, shake(po, -1, 0.008), eo, ro - 3, EIOS), at(2.5, shake(po, 1, 0.008), eo, ro + 3, EIOS), at(3.4, shake(po, -0.5, 0.008), eo, ro - 1, EIOS),
              at(4, po, eo, ro, EIOS)],
         light=light, loop=True, length=4, two=True, note=note)


# Charged Shot: the hands cup low at the core, lift the ball up before the face and hold it; the let-go pushes it out between them.
CS_LOW_M, CS_LOW_O = (0.12, -0.40, -0.55), (-0.12, -0.42, -0.56)
CS_UP_M, CS_UP_O = (0.25, -0.17, -0.48), (-0.25, -0.17, -0.48)
CS_OUT_M, CS_OUT_O = (0.18, -0.13, -0.80), (-0.18, -0.13, -0.80)
CS_E_M, CS_E_O = (0.50, -0.66, -0.26), (-0.50, -0.66, -0.26)
clip("fp_p_charged_shot",
     main=[at(0, REST, ease=L), at(1.2, CS_LOW_M, (0.46, -0.78, -0.22), 40), at(1.8, CS_LOW_M, (0.46, -0.78, -0.22), 40, EIOS), at(3, CS_UP_M, CS_E_M, 30, EOC)],
     off=[at(0, OUT_L, ease=L), at(1.5, CS_LOW_O, (-0.46, -0.80, -0.22), -40), at(2.0, CS_LOW_O, (-0.46, -0.80, -0.22), -40, EIOS), at(3, CS_UP_O, CS_E_O, -30, EOC)],
     light=[(0, 0, 0, 0, 0, L), (1.2, 0.4, 0.5, 0.4, 0.5, EOS), (3, 1, 1.0, 1, 1.0, EOS)], events=[(3, "hold")], release=3, two=True, to_stance=True, length=3,
     note="cupped low at the core, lifted before the face")
held("fp_p_charged_shot_hold", (CS_UP_M, CS_E_M, 30), (CS_UP_O, CS_E_O, -30), [(0, 1, 0.85, 1, 0.85, L), (2, 1, 1.0, 1, 1.0, EIOS), (4, 1, 0.85, 1, 0.85, EIOS)],
     "the ball held between the hands, trembling")
clip("fp_p_charged_shot_end",
     main=[at(0, CS_UP_M, CS_E_M, 30, L), at(1, CS_OUT_M, (0.44, -0.46, -0.34), -60, EOC), at(3, CS_OUT_M, (0.44, -0.46, -0.34), -60, L), at(10, REST, ease=EIOS)],
     off=[at(0, CS_UP_O, CS_E_O, -30, L), at(1.4, CS_OUT_O, (-0.44, -0.46, -0.34), 60, EOC), at(3, CS_OUT_O, (-0.44, -0.46, -0.34), 60, L), at(10, OUT_L, ease=EIOS)],
     light=[(0, 1, 1.0, 1, 1.0, L), (1, 1, 1.8, 1, 1.8, L), (4, 0.8, 0.8, 0.8, 0.8, EOQ), (10, 0.3, 0, 0.3, 0, EOQ)], two=True, note="pushed out from between the hands")

# Prism Lance: the hands meet low at the core, then are thrown wide and held there, the chest open, while the light pours out of it.
PL_IN_M, PL_IN_O = (0.10, -0.50, -0.60), (-0.10, -0.52, -0.62)
PL_OUT_M, PL_OUT_O = (0.62, -0.28, -0.85), (-0.62, -0.28, -0.85)
PL_E_M, PL_E_O = (0.74, -0.78, -0.52), (-0.74, -0.78, -0.52)
clip("fp_p_prism_lance",
     main=[at(0, REST, ease=L), at(2.4, PL_IN_M, (0.50, -0.86, -0.34), 50), at(3.6, PL_IN_M, (0.50, -0.86, -0.34), 50, EIOS), at(6, PL_OUT_M, PL_E_M, -40, EOC)],
     off=[at(0, OUT_L, ease=L), at(2.8, PL_IN_O, (-0.50, -0.88, -0.34), -50), at(3.9, PL_IN_O, (-0.50, -0.88, -0.34), -50, EIOS), at(6, PL_OUT_O, PL_E_O, 40, EOC)],
     light=[(0, 0, 0, 0, 0, L), (2.4, 0.5, 0.6, 0.5, 0.6, EOS), (3.6, 1, 1.1, 1, 1.1, EIOS), (6, 1, 1.5, 1, 1.5, EOC)], events=[(6, "hold")], release=6, two=True, to_stance=True,
     length=6, note="met low at the core, thrown wide")
held("fp_p_prism_lance_hold", (PL_OUT_M, PL_E_M, -40), (PL_OUT_O, PL_E_O, 40), [(0, 1, 1.0, 1, 1.0, L), (2, 1, 1.25, 1, 1.25, EIOS), (4, 1, 1.0, 1, 1.0, EIOS)],
     "the arms held wide while the core burns")

# Railgun: the right fist is the barrel, the left hand braces under its wrist; the shot throws both back and they come down slowly.
RG_IN_M, RG_IN_O = (0.14, -0.40, -0.70), (0.02, -0.46, -0.72)
RG_AIM_M, RG_AIM_O = (0.30, -0.20, -0.95), (0.12, -0.42, -0.95)
RG_E_M, RG_E_O = (0.52, -0.46, -0.44), (-0.32, -0.72, -0.60)
clip("fp_p_railgun",
     main=[at(0, REST, ease=L), at(1.6, RG_IN_M, (0.52, -0.72, -0.34), 20), at(2.4, RG_IN_M, (0.52, -0.72, -0.34), 20, EIOS), at(4, RG_AIM_M, RG_E_M, 0, EOC)],
     off=[at(0, OUT_L, ease=L), at(2.0, RG_IN_O, (-0.36, -0.78, -0.40), -30), at(2.7, RG_IN_O, (-0.36, -0.78, -0.40), -30, EIOS), at(4, RG_AIM_O, RG_E_O, -60, EOC)],
     light=[(0, 0, 0, 0, 0, L), (1.6, 0.4, 0.5, 0.2, 0.3, EOS), (4, 1, 1.0, 0.5, 0.6, EOS)], events=[(4, "hold")], release=4, two=True, to_stance=True, length=4,
     note="the fist levelled along the aim, the other hand braced under its wrist")
held("fp_p_railgun_hold", (RG_AIM_M, RG_E_M, 0), (RG_AIM_O, RG_E_O, -60), [(0, 1, 0.9, 0.5, 0.6, L), (2, 1, 1.15, 0.5, 0.7, EIOS), (4, 1, 0.9, 0.5, 0.6, EIOS)],
     "locked on, humming")
clip("fp_p_railgun_end",
     main=[at(0, RG_AIM_M, RG_E_M, 0, L), at(1, (0.35, 0.00, -0.64), (0.56, -0.50, -0.38), 20, EOC), at(5, (0.32, -0.15, -0.86), (0.54, -0.50, -0.42), 6, EIOS), at(14, REST, ease=EIOS)],
     off=[at(0, RG_AIM_O, RG_E_O, -60, L), at(1.5, (0.05, -0.52, -0.68), (-0.34, -0.84, -0.46), -40, EOC), at(6, (-0.08, -0.54, -0.76), (-0.44, -0.86, -0.50), -30, EIOS),
          at(14, OUT_L, ease=EIOS)],
     light=[(0, 1, 1.0, 0.5, 0.6, L), (1, 1, 2.0, 0.6, 1.0, L), (5, 0.8, 0.7, 0.4, 0.3, EOQ), (14, 0.3, 0, 0.2, 0, EOQ)], two=True, note="thrown back by the shot, lowered slowly")


def all_clips():
    """Every clip, with two more takes of each cast's begin and let-go (never of a hold loop)."""
    out = OrderedDict()
    for name, c in CLIPS.items():
        out[name] = c
        if c["loop"] or c["to_stance"]:
            continue
        for n in (2, 3):
            out[f"{name}_t{n}"] = take_of(c, name, n)
    return out


def document():
    clips = OrderedDict()
    for name, c in all_clips().items():
        d = OrderedDict(length=c["length"], loop=c["loop"], both=True, fists=True, arm=True)
        if c["to_stance"]:
            d["toStance"] = True
        if c["two"]:
            d["stow"] = True
            d["ease"] = True
        if c["main"]:
            d["main"] = track(c["main"], True)
        if c["moves"]:
            d["mainMoves"] = True
            d["main"] = [[m["t"]] + [round(v, 4) for v in m["v"]] + [m["ease"]] for m in c["moves"]]
        if c["off"]:
            d["off"] = track(c["off"], False)
        if c["light"]:
            d["light"] = [[k[0], round(k[1], 3), round(k[2], 3), round(k[3], 3), round(k[4], 3), k[5]] for k in c["light"]]
        if c["events"]:
            d["events"] = [[t, n] for t, n in c["events"]]
        clips[name] = d
    return OrderedDict(schemaVersion=2, __comment="GENERATED by tools/art/specs/fp_casts2.py: edit the poses there, not this file.", clips=clips)


def check():
    """The authoring rules, as warnings (the build never fails on them: Euan and the film judge)."""
    warn = []
    for name, c in CLIPS.items():
        for side in ("off", "main"):
            keys = c[side]
            for a, b in zip(keys, keys[1:]):
                held = b["t"] - a["t"] >= 2 and max(abs(a["p"][i] - b["p"][i]) for i in range(3)) < 0.03
                x, y, z = a["p"]
                if a["p"] in (OUT_L, REST):
                    continue
                if held and (y < -0.58 * abs(z) or abs(y) > 0.68 * abs(z) or abs(x) > 1.2 * abs(z)):
                    warn.append(f"{name}: {side} held off screen at tick {a['t']:g} {a['p']}")
                if held and abs(x) < 0.25 * abs(z) and abs(y) < 0.14 * abs(z):
                    warn.append(f"{name}: {side} held over the crosshair at tick {a['t']:g}")
        if c["off"] and c["main"] and len(c["off"]) > 1 and len(c["main"]) > 1 and c["off"][1]["t"] == c["main"][1]["t"]:
            warn.append(f"{name}: both hands start on tick {c['off'][1]['t']:g}")
        if c["release"] is not None and not any(abs(k["t"] - c["release"]) < 0.01 for k in c["off"] + c["main"]) and not c["loop"]:
            warn.append(f"{name}: no key on its release tick {c['release']}")
    for power, p in PROFILES.items():
        if p["begin1p"] in CLIPS and not p["channel"] and CLIPS[p["begin1p"]]["release"] != p["release"]:
            warn.append(f"{power}: the clip releases on {CLIPS[p['begin1p']]['release']}, the profile on {p['release']}")
    return warn


def write():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as fh:
        json.dump(document(), fh, separators=(",", ":"))
        fh.write("\n")


def build(out):
    write()


def main(argv):
    cmd = argv[0] if argv else "build"
    w = check()
    for line in w:
        print("  warning:", line)
    if cmd == "build":
        write()
        doc = document()
        missing = sorted(p for p, r in PROFILES.items() if r["begin1p"] not in doc["clips"])
        print(f"{len(doc['clips'])} clips ({len(CLIPS)} authored) -> {os.path.relpath(OUT, ROOT)}; {len(w)} warnings; still on their old clips: {missing}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
