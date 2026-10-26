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


def clip(name, length, main=(), off=(), both=False, loop=False, events=(), note="", fists=False, to_stance=False):
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
        if track and not loop and not fists and any(abs(v) > 1e-9 for v in track[-1][1]):
            raise ValueError(f"{name}: a one-shot must end at rest")
    if fists and not loop and not to_stance:
        # a fist clip gives the hands back: the main fist to vanilla's rest, the off one to its rest or out under the frame
        if main and any(abs(a - b) > 1e-6 for a, b in zip(main[-1][1][:3], REST_FIST[:3])):
            raise ValueError(f"{name}: the main fist must end at rest")
        if off and off[-1][1][1] > -0.9 and any(abs(a - b) > 1e-6 for a, b in zip(off[-1][1][:3], (-REST_FIST[0],) + REST_FIST[1:3])):
            raise ValueError(f"{name}: the off fist must end at rest or under the frame")
    CLIPS[name] = dict(length=length, loop=loop, both=both, main=list(main), off=list(off), events=list(events), note=note, fists=fists,
                       to_stance=to_stance)


# Fist clips (FirstPerson.View fists): each empty hand keyed by where it is on screen, in view space (x right, y up, z
# toward you), its arm drawn rising to it from under the frame's corner. Lifting a vanilla arm by offsets floats its
# forearm into view; a fist never does. Vanilla's own resting empty fist is REST_FIST; an off hand comes in from and goes
# back out to OFF_OUT (under the frame, where vanilla draws no empty off hand).
def fist(x, y, z):
    return (float(x), float(y), float(z), 0.0, 0.0, 0.0)


REST_FIST = fist(0.744, -0.455, -1.072)
OFF_OUT = fist(-0.62, -1.15, -0.85)
# both hands on a lip in a hang (body.client.BodyFp.HANG_FIST: keep the same numbers)
HANG_FIST = fist(0.26, -0.02, -0.82)


# ---------------------------------------------------------------------------------------------------------------------
# Leaving a climb line (SPEC §17.28). On the line itself the hands are drawn on it in the world (body.client.GripView),
# and for the first ticks after letting go they drop away there; these clips take the hands over from below.

# Kicked off the wall: the hands come up from below thrown out for balance as you fly back, then settle.
KICK_UP = hand(0.08, 0.02, -0.04, 18, 0, 12)
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
# The Runner's Kit (P2; SPEC §16.3, §17). The held moves (the tactical sprint, a slide, a wall-run, a wall climb, a hang
# and its shimmy) are stances in body.client.BodyFp, phase-locked to the move; these are the one-shots that start them
# or end them. Each starts where the hands are (vanilla's rest, or the stance under it) and gives them back.
BELOW = hand(0, -0.6)          # out of view under the frame (an arm that wasn't drawn comes in from here)
# Lifting a hand is a pitch (the arm swings up about the eye), not a move up: moved up, the whole forearm floats into view.

def mf(h):
    """A fist on the other side (x mirrored)."""
    return (-h[0], h[1], h[2], 0.0, 0.0, 0.0)


# Landings: a soft one dips the hands and springs them back (offsets: a small move); a hard one puts the main hand down
# to the ground and brings the off hand in low beside it.
clip("fp_land_soft", 8, main=[(0, REST, "linear"), (2, hand(0, -0.1, 0.02, -10), "easeoutcubic"), (5, hand(0, 0.02, 0, 2), "easeinoutsine"),
                              (8, REST, "easeinoutsine")], note="hands dip with the landing, spring back")
clip("fp_land_hard", 12, fists=True,
     main=[(0, REST_FIST, "linear"), (3, fist(0.22, -0.42, -0.8), "easeoutcubic"), (7, fist(0.24, -0.4, -0.82), "linear"), (12, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, fist(-0.4, -0.48, -0.82), "easeoutcubic"), (7, fist(-0.42, -0.46, -0.84), "linear"), (12, OFF_OUT, "easeinoutsine")],
     events=[(3, "slap")], note="the main hand down to the ground, a three-point landing")

# The roll: the forearms cross in front as the view turns over, then open out of it.
clip("fp_roll", 17, fists=True,
     main=[(0, REST_FIST, "linear"), (3, fist(-0.08, -0.3, -0.7), "easeoutcubic"), (10, fist(-0.06, -0.32, -0.7), "linear"), (17, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, fist(0.1, -0.24, -0.72), "easeoutcubic"), (10, fist(0.08, -0.26, -0.72), "linear"), (17, OFF_OUT, "easeinoutsine")],
     note="forearms crossed through the roll")

# The free-run's vault: the main palm plants on the top in the middle of the view, then pushes past as you go over.
clip("fp_vault", 10, fists=True,
     main=[(0, REST_FIST, "linear"), (2, fist(0.16, -0.4, -0.8), "easeoutcubic"), (5, fist(0.34, -0.66, -0.7), "easeinquad"), (10, REST_FIST, "easeinoutsine")],
     events=[(2, "plant")], note="a palm planted on the top, pushed past")

# The mantle (and every climb out over a lip): both hands slap up onto the lip, then press down as you rise over it.
MN_REACH, MN_PRESS, MN_PUSH = fist(0.3, 0.1, -0.9), fist(0.28, -0.06, -0.85), fist(0.4, -0.4, -0.8)
clip("fp_mantle", 12, fists=True,
     main=[(0, REST_FIST, "linear"), (2, MN_REACH, "easeoutcubic"), (5, MN_PRESS, "easeinquad"), (9, MN_PUSH, "easeinoutsine"), (12, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (2, mf(MN_REACH), "easeoutcubic"), (5, mf(MN_PRESS), "easeinquad"), (9, mf(MN_PUSH), "easeinoutsine"), (12, OFF_OUT, "easeinoutsine")],
     events=[(2, "slap")], note="palms slap the lip, press over it")

# A leap from an edge: both arms swing forward and up with the jump.
LP = fist(0.42, 0.02, -0.95)
clip("fp_leap", 10, fists=True, main=[(0, REST_FIST, "linear"), (3, LP, "easeoutcubic"), (10, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, mf(LP), "easeoutcubic"), (10, OFF_OUT, "easeinoutsine")], note="arms swing with the leap")

# Out of a slide with a jump: the hands thrown up, then open for the landing.
SJ = fist(0.38, 0.18, -0.9)
clip("fp_slide_jump", 13, fists=True,
     main=[(0, REST_FIST, "linear"), (3, SJ, "easeoutcubic"), (9, fist(0.6, -0.25, -1.0), "easeinoutsine"), (13, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, mf(SJ), "easeoutcubic"), (9, mf(fist(0.6, -0.25, -1.0)), "easeinoutsine"), (13, OFF_OUT, "easeinoutsine")],
     note="hands thrown up out of the slide")


def walljump(wall_left):
    """The wall-side palm shoves off the wall, open and well in the frame; the other arm is thrown forward for the flight."""
    shove = fist(-0.5, -0.1, -0.78)     # the left hand, out at the wall on the left
    throw = fist(0.34, -0.06, -1.0)     # the right arm thrown forward
    if wall_left:
        main_keys = [(0, REST_FIST, "linear"), (2, throw, "easeoutcubic"), (12, REST_FIST, "easeinoutsine")]
        off_keys = [(0, fist(-0.7, -0.3, -0.8), "linear"), (2, shove, "easeoutcubic"), (6, fist(-0.6, -0.5, -0.8), "easeinoutsine"), (12, OFF_OUT, "easeinoutsine")]
    else:
        main_keys = [(0, fist(0.75, -0.3, -0.85), "linear"), (2, mf(shove), "easeoutcubic"), (6, fist(0.7, -0.45, -0.95), "easeinoutsine"),
                     (12, REST_FIST, "easeinoutsine")]
        off_keys = [(0, OFF_OUT, "linear"), (2, mf(throw), "easeoutcubic"), (12, OFF_OUT, "easeinoutsine")]
    return main_keys, off_keys


m, o = walljump(True)
clip("fp_walljump_l", 12, fists=True, main=m, off=o, events=[(2, "shove")], note="the left palm shoves off the wall")
m, o = walljump(False)
clip("fp_walljump_r", 12, fists=True, main=m, off=o, events=[(2, "shove")], note="the right palm shoves off the wall")

# Hanging (body.client.BodyFp's hang stance holds both fists at HANG_FIST on the lip). These play over it, from the
# hang's fists and back to them (or, leaving the lip, out to rest).
# The catch: the hands come up from below and slam onto the lip, with a jolt.
clip("fp_grab", 8, fists=True, to_stance=True,
     main=[(0, fist(0.4, -0.5, -0.8), "linear"), (1.5, fist(0.26, 0.04, -0.82), "easeoutcubic"), (3.5, fist(0.26, -0.05, -0.82), "easeinoutsine"),
           (8, HANG_FIST, "easeinoutsine")],
     off=[(0, fist(-0.4, -0.5, -0.8), "linear"), (1.5, fist(-0.26, 0.03, -0.82), "easeoutcubic"), (3.5, fist(-0.26, -0.05, -0.82), "easeinoutsine"),
          (8, mf(HANG_FIST), "easeinoutsine")], events=[(1.5, "catch")], note="hands slam onto the lip with a jolt")
# Lowering over an edge into a hang: the hands go down to the edge first, then take the weight.
clip("fp_lower", 8, fists=True, to_stance=True,
     main=[(0, fist(0.3, -0.6, -0.7), "linear"), (5, fist(0.26, -0.04, -0.82), "easeinoutsine"), (8, HANG_FIST, "easeoutsine")],
     off=[(0, fist(-0.3, -0.6, -0.7), "linear"), (5, fist(-0.26, -0.04, -0.82), "easeinoutsine"), (8, mf(HANG_FIST), "easeoutsine")],
     note="down onto the hands at the edge")
# The ledge jump up: a pull down, then both hands reach high for the next lip and catch it.
# Its beats match the body's leap (RunnerMoves: load 2, launch, a tick at the apex, the fall into the catch; played at
# the speed that lands the catch with the body): the hands pull down on the load, fly up past the lip on the launch,
# hang over it at the apex, slam down onto the new lip and take the weight.
LU = [(0, HANG_FIST, "linear"), (2, fist(0.27, -0.24, -0.8), "easeinquad"), (5, fist(0.24, 0.3, -0.86), "easeoutcubic"),
      (7, fist(0.25, 0.26, -0.85), "linear"), (8.5, fist(0.26, -0.09, -0.82), "easeinquad"), (10, HANG_FIST, "easeoutsine")]
clip("fp_ledge_up", 10, fists=True, to_stance=True, main=LU, off=[(t, mf(h), e) for t, h, e in LU], events=[(2, "load"), (8.5, "catch")],
     note="pull down on the load, fly up past the lip, hang, slam onto the new lip")


def ledge_side(left):
    """A ledge jump sideways: the lead hand reaches out that way, the other lets go and follows."""
    s = 1 if left else -1
    lead = fist(-0.62, 0.0, -0.82)       # the left hand reaching left
    main_keys = [(0, HANG_FIST, "linear"), (4, fist(0.0, -0.08, -0.85) if left else mf(lead), "easeoutcubic"), (9, HANG_FIST, "easeinoutsine")]
    off_keys = [(0, mf(HANG_FIST), "linear"), (4, lead if left else fist(0.0, -0.08, -0.85), "easeoutcubic"), (9, mf(HANG_FIST), "easeinoutsine")]
    return main_keys, off_keys


m, o = ledge_side(True)
clip("fp_ledge_side_l", 9, fists=True, to_stance=True, main=m, off=o, events=[(6, "catch")], note="the left hand reaches for the next lip")
m, o = ledge_side(False)
clip("fp_ledge_side_r", 9, fists=True, to_stance=True, main=m, off=o, events=[(6, "catch")], note="the right hand reaches for the next lip")
# The ledge jump back: the hands leave the lip, thrown out wide as the body turns away, and drop back to rest.
clip("fp_ledge_back", 12, fists=True,
     main=[(0, HANG_FIST, "linear"), (3, fist(0.8, 0.0, -0.9), "easeoutcubic"), (12, REST_FIST, "easeinoutsine")],
     off=[(0, mf(HANG_FIST), "linear"), (3, fist(-0.8, 0.0, -0.9), "easeoutcubic"), (12, OFF_OUT, "easeinoutsine")], note="hands thrown off the lip")
# The stair slide (SPEC §17.22): the trailing hand knocks the edge of each step it passes (offsets on the slide's stance).
clip("fp_stair_bump", 4, off=[(0, REST, "linear"), (1, hand(0, 0.04, 0, 5), "easeoutcubic"), (4, REST, "easeinoutsine")],
     both=True, events=[(1, "step")], note="the trailing hand bumps a step's edge")


# ---------------------------------------------------------------------------------------------------------------------
# The Ascent (P3; SPEC §17). Its held states are fist stances in body.ascent.client.AscentFp (keep these numbers the
# same there): on a rope the main fist holds it at ROPE_FIST (where the rope leaves the hand, AscentRender), on wings
# both arms are out flat at the frame's edges (GLIDE_FIST). These are its moments.
ROPE_FIST = fist(0.3, -0.02, -0.72)
GLIDE_FIST = fist(0.86, -0.04, -0.86)

# The rope: thrown with the main arm (a forward fling, the wrist flicked), then held where it leaves the hand.
clip("fp_rope_fire", 6, fists=True, to_stance=True,
     main=[(0, REST_FIST, "linear"), (2, fist(0.42, -0.08, -1.08), "easeoutcubic"), (6, ROPE_FIST, "easeinoutsine")],
     events=[(2, "throw")], note="the rope flung out of the hand")
# Letting go of the rope: the hand opens and drops back to rest.
clip("fp_rope_release", 10, fists=True, main=[(0, ROPE_FIST, "linear"), (3, fist(0.52, -0.32, -0.9), "easeoutcubic"), (10, REST_FIST, "easeinoutsine")],
     note="the hand opens off the rope")
# Jumping off the rope: both arms thrown up and out with the launch.
clip("fp_rope_launch", 12, fists=True,
     main=[(0, ROPE_FIST, "linear"), (3, fist(0.62, 0.12, -0.9), "easeoutcubic"), (12, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, fist(-0.62, 0.1, -0.9), "easeoutcubic"), (12, OFF_OUT, "easeinoutsine")], note="arms flung up off the rope")
# The wings open: the hands spread out to the frame's edges as they unfold, into the glide's stance.
clip("fp_wings_open", 10, fists=True, to_stance=True,
     main=[(0, REST_FIST, "linear"), (3, fist(0.7, -0.1, -0.9), "easeoutcubic"), (10, GLIDE_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, fist(-0.7, -0.1, -0.9), "easeoutcubic"), (10, mf(GLIDE_FIST), "easeinoutsine")],
     events=[(3, "unfold")], note="hands spread to the edges as the wings unfold")
# A wing beat: the arms sweep down with it and come back up.
clip("fp_wing_beat", 10, fists=True, to_stance=True,
     main=[(0, GLIDE_FIST, "linear"), (3, fist(0.8, -0.36, -0.8), "easeinquad"), (7, fist(0.86, 0.04, -0.86), "easeoutcubic"), (10, GLIDE_FIST, "easeinoutsine")],
     off=[(0, mf(GLIDE_FIST), "linear"), (3, fist(-0.8, -0.36, -0.8), "easeinquad"), (7, fist(-0.86, 0.04, -0.86), "easeoutcubic"),
          (10, mf(GLIDE_FIST), "easeinoutsine")], events=[(3, "beat")], note="the arms sweep down with the beat")
# The barrel roll: the arms tuck in to the chest through it and open out again.
clip("fp_barrel_roll", 14, fists=True, to_stance=True,
     main=[(0, GLIDE_FIST, "linear"), (3, fist(0.14, -0.46, -0.7), "easeoutcubic"), (10, fist(0.16, -0.44, -0.7), "linear"), (14, GLIDE_FIST, "easeinoutsine")],
     off=[(0, mf(GLIDE_FIST), "linear"), (3, fist(-0.14, -0.46, -0.7), "easeoutcubic"), (10, fist(-0.16, -0.44, -0.7), "linear"),
          (14, mf(GLIDE_FIST), "easeinoutsine")], note="arms tucked through the roll")
# The sonic boom: the hands come up in front against it for a moment.
clip("fp_boom", 10, fists=True, to_stance=True,
     main=[(0, GLIDE_FIST, "linear"), (2, fist(0.24, -0.04, -0.78), "easeoutcubic"), (5, fist(0.26, -0.06, -0.8), "linear"), (10, GLIDE_FIST, "easeinoutsine")],
     off=[(0, mf(GLIDE_FIST), "linear"), (2, fist(-0.24, -0.04, -0.78), "easeoutcubic"), (5, fist(-0.26, -0.06, -0.8), "linear"),
          (10, mf(GLIDE_FIST), "easeinoutsine")], events=[(2, "brace")], note="hands raised against the boom")
# The Leaper's double jump: both arms thrown down to push off the air.
clip("fp_double_jump", 10, fists=True,
     main=[(0, REST_FIST, "linear"), (2, fist(0.46, -0.72, -0.75), "easeoutcubic"), (10, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (2, fist(-0.46, -0.72, -0.75), "easeoutcubic"), (10, OFF_OUT, "easeinoutsine")], note="arms thrown down off the air")
# The air dash: the lead fist punches through ahead.
clip("fp_air_dash", 10, fists=True,
     main=[(0, REST_FIST, "linear"), (2, fist(0.12, -0.08, -1.15), "easeoutcubic"), (6, fist(0.14, -0.1, -1.12), "linear"), (10, REST_FIST, "easeinoutsine")],
     events=[(2, "punch")], note="the lead fist punches through")
# The dive slam: both fists raised overhead for the drop (held by the dive's stance), then smashed down on landing.
SLAM_UP = fist(0.2, 0.3, -0.74)
clip("fp_dive", 6, fists=True, to_stance=True, main=[(0, REST_FIST, "linear"), (4, SLAM_UP, "easeoutcubic"), (6, SLAM_UP, "linear")],
     off=[(0, OFF_OUT, "linear"), (4, mf(SLAM_UP), "easeoutcubic"), (6, mf(SLAM_UP), "linear")], note="fists raised for the dive")
clip("fp_slam_land", 12, fists=True,
     main=[(0, SLAM_UP, "linear"), (2, fist(0.2, -0.42, -0.74), "easeinquad"), (6, fist(0.22, -0.4, -0.76), "linear"), (12, REST_FIST, "easeinoutsine")],
     off=[(0, mf(SLAM_UP), "linear"), (2, fist(-0.2, -0.42, -0.74), "easeinquad"), (6, fist(-0.22, -0.4, -0.76), "linear"), (12, OFF_OUT, "easeinoutsine")],
     events=[(2, "impact")], note="fists smashed down with the landing")
# The Sovereign takes the form: the palms rise as the light gathers.
clip("fp_sovereign_ascend", 16, fists=True,
     main=[(0, REST_FIST, "linear"), (4, fist(0.28, -0.32, -0.85), "easeinoutsine"), (12, fist(0.3, 0.24, -0.85), "easeoutsine"), (16, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (4, fist(-0.28, -0.32, -0.85), "easeinoutsine"), (12, fist(-0.3, 0.24, -0.85), "easeoutsine"), (16, OFF_OUT, "easeinoutsine")],
     events=[(12, "radiant")], note="palms rising as the gold gathers")


# ---------------------------------------------------------------------------------------------------------------------
# Touches (P4; SPEC §24): quick hand moments on things you use, played the moment you use them (the action never waits).
# Each is the main hand's; its "_off" twin does the same with the off hand, for when the main hand holds something.
TOUCHES = []


def touch(name, length, keys, events=(), note=""):
    """A one-handed touch and its off-hand twin (mirrored, in from and back out under the frame)."""
    clip(name, length, fists=True, main=keys, events=events, note=note)
    off = [(t, OFF_OUT if h == REST_FIST else mf(h), e) for t, h, e in keys]
    clip(name + "_off", length, fists=True, off=off, events=events, note=note + " (the off hand)")
    TOUCHES.append(name)


# A door, trapdoor or gate: the palm on its edge, pushing it through its swing.
touch("fp_push", 8, [(0, REST_FIST, "linear"), (2, fist(0.1, -0.12, -0.95), "easeoutcubic"), (4, fist(0.14, -0.14, -1.12), "easeinoutsine"),
                     (8, REST_FIST, "easeinoutsine")], events=[(2, "touch")], note="a palm pushes the door through")
# A lever: gripped and thrown.
touch("fp_lever", 8, [(0, REST_FIST, "linear"), (2, fist(0.12, -0.2, -0.86), "easeoutcubic"), (4, fist(0.22, -0.44, -0.8), "easeinquad"),
                      (8, REST_FIST, "easeinoutsine")], events=[(4, "throw")], note="the lever thrown")
# A button: one press.
touch("fp_button", 6, [(0, REST_FIST, "linear"), (2, fist(0.08, -0.08, -1.0), "easeoutcubic"), (3, fist(0.08, -0.08, -0.95), "easeinoutsine"),
                       (6, REST_FIST, "easeinoutsine")], events=[(2, "press")], note="a finger press")
# A plant (a herb, a flower, berries): pinched, plucked with a little shake, carried down to the hip.
touch("fp_pluck", 9, [(0, REST_FIST, "linear"), (2, fist(0.14, -0.46, -0.84), "easeoutcubic"), (4, fist(0.22, -0.3, -0.9), "easeinoutsine"),
                      (5, fist(0.24, -0.34, -0.9), "linear"), (7, fist(0.5, -0.62, -0.8), "easeinoutsine"), (9, REST_FIST, "easeinoutsine")],
      events=[(4, "pluck")], note="pinch, pluck, to the hip")
# A creature: a hand stroking it, twice.
touch("fp_pet", 14, [(0, REST_FIST, "linear"), (3, fist(0.12, -0.36, -0.8), "easeoutcubic"), (6, fist(0.0, -0.4, -0.76), "easeinoutsine"),
                     (9, fist(0.12, -0.36, -0.8), "easeinoutsine"), (11, fist(0.0, -0.4, -0.76), "easeinoutsine"), (14, REST_FIST, "easeinoutsine")],
      note="a hand strokes the creature")
# The Forge or an anvil: the fist hammers three times (the off hand, when the main holds the blade being tempered).
HAM_UP, HAM_DOWN = fist(0.2, 0.08, -0.82), fist(0.06, -0.3, -0.96)
touch("fp_hammer", 18, [(0, REST_FIST, "linear"), (3, HAM_UP, "easeoutcubic"), (5, HAM_DOWN, "easeinquad"), (8, HAM_UP, "easeoutcubic"),
                        (10, HAM_DOWN, "easeinquad"), (13, HAM_UP, "easeoutcubic"), (15, HAM_DOWN, "easeinquad"), (18, REST_FIST, "easeinoutsine")],
      events=[(5, "strike"), (10, "strike"), (15, "strike")], note="three strikes on the anvil")
# A campfire: the food laid on the grate, the hand drawn back from the heat.
touch("fp_lay", 10, [(0, REST_FIST, "linear"), (3, fist(0.1, -0.22, -0.96), "easeoutcubic"), (5, fist(0.04, -0.17, -1.04), "easeinoutsine"),
                     (7, fist(0.24, -0.32, -0.82), "easeoutcubic"), (10, REST_FIST, "easeinoutsine")], events=[(5, "lay")],
      note="laid on the fire, drawn back from the heat")
# P6. A waystone, conduit, shrine, gate rune or the Hearth anchor: the palm pressed flat to the stone and held while the
# light answers (the hand glows: Touches tints it), then drawn back.
PALM = fist(0.06, -0.12, -1.02)
touch("fp_palm", 18, [(0, REST_FIST, "linear"), (3, fist(0.08, -0.1, -0.92), "easeoutcubic"), (5, PALM, "easeinquad"),
                      (12, fist(0.06, -0.13, -1.04), "linear"), (18, REST_FIST, "easeinoutsine")], events=[(5, "press")],
      note="a palm pressed to the stone, held, drawn back")
# A notice board: a notice torn off and brought down to read.
touch("fp_tear", 12, [(0, REST_FIST, "linear"), (3, fist(0.1, -0.02, -0.98), "easeoutcubic"), (5, fist(0.12, 0.0, -1.0), "linear"),
                      (7, fist(0.3, -0.36, -0.82), "easeinquad"), (9, fist(0.22, -0.34, -0.7), "easeoutcubic"), (12, REST_FIST, "easeinoutsine")],
      events=[(6, "tear")], note="torn off the board, brought down to read")
# A grindstone: the handle cranked round once.
CRANK = [fist(0.36 + 0.1 * math.cos(a), -0.3 + 0.1 * math.sin(a), -0.86) for a in [i * math.pi / 3 for i in range(7)]]
touch("fp_crank", 18, [(0, REST_FIST, "linear"), (3, CRANK[0], "easeoutcubic")] + [(5 + 2 * i, c, "linear") for i, c in enumerate(CRANK[1:])]
      + [(18, REST_FIST, "easeinoutsine")], note="the grindstone's handle cranked round")
# A lectern or an enchanting table's book: a page turned, right to left.
touch("fp_page", 10, [(0, REST_FIST, "linear"), (3, fist(0.3, -0.4, -0.86), "easeoutcubic"), (6, fist(0.0, -0.3, -0.88), "easeinoutsine"),
                      (7, fist(-0.06, -0.34, -0.88), "linear"), (10, REST_FIST, "easeinoutsine")], events=[(5, "turn")],
      note="a page turned")
# A stonecutter, loom, cartography table or brewing stand: both hands set the work on it.
SET_A = fist(0.24, -0.44, -0.86)
clip("fp_set", 8, fists=True, main=[(0, REST_FIST, "linear"), (3, SET_A, "easeoutcubic"), (5, fist(0.24, -0.47, -0.88), "linear"), (8, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (3, mf(SET_A), "easeoutcubic"), (5, mf(fist(0.24, -0.47, -0.88)), "linear"), (8, OFF_OUT, "easeinoutsine")],
     events=[(3, "set")], note="both hands set the work down")
TOUCHES.append("fp_set")
# A chest, barrel or cache: both hands lift the lid while the server answers (the screen opens at once).
LID_A, LID_B = fist(0.2, -0.36, -0.8), fist(0.2, -0.08, -0.86)
clip("fp_lid", 8, fists=True, main=[(0, REST_FIST, "linear"), (2, LID_A, "easeoutcubic"), (5, LID_B, "easeinoutsine"), (8, REST_FIST, "easeinoutsine")],
     off=[(0, OFF_OUT, "linear"), (2, mf(LID_A), "easeoutcubic"), (5, mf(LID_B), "easeinoutsine"), (8, OFF_OUT, "easeinoutsine")],
     events=[(5, "lift")], note="both hands lift the lid")
TOUCHES.append("fp_lid")


# ---------------------------------------------------------------------------------------------------------------------
# Output

def document():
    out = {}
    for name, c in CLIPS.items():
        d = {"length": c["length"], "loop": c["loop"], "both": c["both"]}
        if c["fists"]:
            d["fists"] = True
        if c["to_stance"]:
            d["toStance"] = True
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


def fist_quads(fx, right, anchor=(0.48, -0.84, -0.53), reach=0.6):
    """A fist clip's arm (anims.client.WorldBody.viewArm): from the anchor under the corner toward the fist, front up."""
    side = 1 if right else -1
    s = (side * anchor[0], anchor[1], anchor[2])
    a = [fx[i] - s[i] for i in range(3)]
    l = math.sqrt(sum(v * v for v in a))
    d = [v / l for v in a]
    root = [fx[i] - d[i] * reach for i in range(3)]
    dot = d[1]
    f = [-d[0] * dot, 1 - d[1] * dot, -d[2] * dot]
    fl = math.sqrt(sum(v * v for v in f))
    f = [v / fl for v in f]
    r = [d[1] * f[2] - d[2] * f[1], d[2] * f[0] - d[0] * f[2], d[0] * f[1] - d[1] * f[0]]
    pts = [[root[i] + d[i] * t + f[i] * u + r[i] * v for i in range(3)] for t in (-2 / 16, 10 / 16) for u in (-2 / 16, 2 / 16)
           for v in (-2 / 16, 2 / 16)]
    faces = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
    return [[pts[i] for i in fc] for fc in faces]


def frame(c, tick, w=320, h=180):
    from PIL import Image, ImageDraw
    im = Image.new("RGB", (w, h), (24, 28, 36))
    dr = ImageDraw.Draw(im)
    f = 1 / math.tan(math.radians(35))
    quads = []
    mh, oh = sample(c["main"], tick), sample(c["off"], tick)
    if c.get("fists"):
        if c["main"]:
            quads += [(q, (196, 150, 112)) for q in fist_quads(mh, True)]
        else:
            quads += [(q, (196, 150, 112)) for q in arm_quads(REST, True)]
        if c["off"]:
            quads += [(q, (176, 132, 98)) for q in fist_quads(oh, False)]
    else:
        quads += [(q, (196, 150, 112)) for q in arm_quads(mh, True)]
    if not c.get("fists") and (c["both"] or c["off"]):
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
