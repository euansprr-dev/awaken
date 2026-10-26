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
clip("body_mantle", [(0, MN_REACH, "linear"), (1.5, nudge(MN_REACH, rise=0.5, ra=(4, 0, 0), la=(4, 0, 0)), "easeoutsine"),
                     (3, MN_PULL, "easeinoutsine"), (4.5, MN_PUSH, "easeoutquad"), (6.5, MN_STAND, "easeinoutsine"),
                     (7.5, MN_STAND, "linear")], 11, note="reach, pull with a knee driving up, press, stand")

# Tactical sprint: leaning hard into it, knees high, arms pumping with the hands kept low (bent elbows, short swing).
# One stride pair every 8 ticks, looped for as long as it lasts (BodyRules.TAC_TICKS), then a settle.
TS_A = pose(lean=30, twist=10, rl=(-76, 0, 2), rk=105, ll=(42, 0, -2), lk=78, ra=(50, 0, 8), re=84, la=(-40, 14, -6), le=92)
TS_B = pose(lean=30, rl=(-22, 0, 2), rk=70, ll=(4, 0, -2), lk=18, ra=(4, 0, 8), re=88, la=(4, 0, -8), le=88)
TS_C = pose(lean=30, twist=-10, rl=(42, 0, 2), rk=78, ll=(-76, 0, -2), lk=105, ra=(-40, -14, 6), re=92, la=(50, 0, -8), le=84)
TS_D = pose(lean=30, rl=(4, 0, 2), rk=18, ll=(-22, 0, -2), lk=70, ra=(4, 0, 8), re=88, la=(4, 0, -8), le=88)
clip("body_sprint", [(0, TS_A, "linear"), (2, TS_B, "easeinoutsine"), (4, TS_C, "easeinoutsine"), (6, TS_D, "easeinoutsine"),
                     (8, TS_A, "easeinoutsine")], 8, loop=True, note="hard lean, high knees, arms pumping low")
clip("body_sprint_end", [(0, TS_B, "linear")], 6, end=0, note="settles out of body_sprint")


# ---------------------------------------------------------------------------------------------------------------------
# The Runner's Kit (SPEC §16.3, §17). The game leans the whole figure (out from a wall on a run, under the hands on a
# hang); these clips pose the limbs. Clips are authored right-handed: "_l" means the wall (or the way) is on the left.

# Wall-run: a quick high-knee stride with the wall-side hand brushing the wall low and the other arm pumping.
# One stride pair every 8 ticks (faster than the tactical sprint: you're fighting gravity).
def wallrun(wall_left):
    # the wall-side arm: out toward the wall and a little forward, palm on it; the free arm pumps
    near = (-30, 0, -70) if wall_left else (-30, 0, 70)
    a = pose(lean=12, twist=-8 if wall_left else 8, rl=(-80, 0, 2), rk=100, ll=(38, 0, -2), lk=70,
             ra=(48, 0, 8) if wall_left else near, re=80 if wall_left else 30,
             la=near if wall_left else (48, 0, -8), le=30 if wall_left else 80)
    b = pose(a, rl=(-20, 0, 2), rk=60, ll=(2, 0, -2), lk=20)
    c = pose(a, rl=(38, 0, 2), rk=70, ll=(-80, 0, -2), lk=100,
             ra=(-40, -10, 6) if wall_left else near, la=near if wall_left else (-40, 10, -6))
    d = pose(c, rl=(2, 0, 2), rk=20, ll=(-20, 0, -2), lk=60)
    return [(0, a, "linear"), (2, b, "easeinoutsine"), (4, c, "easeinoutsine"), (6, d, "easeinoutsine"), (8, a, "easeinoutsine")]


clip("body_wallrun_l", wallrun(True), 8, loop=True, note="running along a wall on the left: high knees, left hand on the wall")
clip("body_wallrun_r", wallrun(False), 8, loop=True, note="running along a wall on the right: high knees, right hand on the wall")
clip("body_wallrun_l_end", [(0, wallrun(True)[1][1], "linear")], 6, end=0, note="settles out of body_wallrun_l")
clip("body_wallrun_r_end", [(0, wallrun(False)[1][1], "linear")], 6, end=0, note="settles out of body_wallrun_r")

# Wall climb (Wall Stride's vertical run): facing the wall, running up it: a knee driving high with the foot on the face,
# the other leg pushing off below; the hands reach up the face in turn (overhead arms with the z sign that keeps them
# clear of the head). One stride pair every 8 ticks, like the wall-run.
def wallclimb():
    a = pose(ground=False, lean=-6, rl=(-96, 0, 3), rk=110, ll=(12, 0, -3), lk=40, ra=(-168, 0, -10), re=6, la=(-92, 0, 8), le=14)
    b = pose(a, rl=(-46, 0, 3), rk=70, ll=(-46, 0, -3), lk=70, ra=(-132, 0, -9), re=10, la=(-128, 0, 9), le=10)
    c = pose(a, rl=(12, 0, 3), rk=40, ll=(-96, 0, -3), lk=110, ra=(-92, 0, -8), re=14, la=(-168, 0, 10), le=6)
    d = pose(c, rl=(-46, 0, 3), rk=70, ll=(-46, 0, -3), lk=70, ra=(-128, 0, -9), re=10, la=(-132, 0, 9), le=10)
    return [(0, a, "linear"), (2, b, "easeinoutsine"), (4, c, "easeinoutsine"), (6, d, "easeinoutsine"), (8, a, "easeinoutsine")]


clip("body_wallclimb", wallclimb(), 8, loop=True, note="running up a wall you face: knees driving up, hands reaching up the face in turn")
clip("body_wallclimb_end", [(0, wallclimb()[1][1], "linear")], 6, end=0, note="settles out of body_wallclimb")

# Wall jump: gather (knees in, arms down and back), then a spring off the wall: legs drive out, arms thrown up and
# forward, opening into a reach for the next wall.
WJ_GATHER = pose(ground=False, lean=18, rl=(-70, 0, 4), rk=100, ll=(-60, 0, -4), lk=95, ra=(40, 0, 20), re=40, la=(40, 0, -20), le=40)
WJ_SPRING = pose(ground=False, lean=-6, rl=(18, 0, 6), rk=10, ll=(-8, 0, -6), lk=20, ra=(-145, -10, -30), re=20, la=(-138, 10, 30), le=20)
WJ_REACH = pose(ground=False, lean=4, rl=(-30, 0, 4), rk=40, ll=(-10, 0, -4), lk=30, ra=(-90, -6, 30), re=30, la=(-90, 6, -30), le=30)
clip("body_walljump", [(0, WJ_GATHER, "linear"), (2, WJ_SPRING, "easeoutcubic"), (6, WJ_REACH, "easeinoutsine"), (9, WJ_REACH, "linear")], 12,
     note="gather, spring off the wall, reach for the next")

# Ledge hang (the hold puts the lip just above the shoulders): both hands forward on top of the block, elbows soft,
# the body hanging long against the face, legs loose with knees soft; a slow sway.
HG = pose(ground=False, ra=(-112, 4, 10), re=24, la=(-112, -4, -10), le=24, rl=(-8, 0, 3), rk=18, ll=(6, 0, -3), lk=10)
held("body_hang", HG, [nudge(HG, rl=(-6, 0, 0), ll=(4, 0, 0), rk=6), nudge(HG, rl=(5, 0, 0), ll=(-5, 0, 0), lk=6)], 40,
     "both hands on the lip, hanging long, legs loose")

# The grab: arms snap up, knees swing in with the catch, then the body settles long under the hands.
GR_SNAP = pose(ground=False, ra=(-162, 0, -26), re=25, la=(-162, 0, 26), le=25, rl=(-60, 0, 4), rk=90, ll=(-50, 0, -4), lk=85)
clip("body_grab", [(0, pose(GR_SNAP, ra=(-120, 0, -20), la=(-120, 0, 20)), "linear"), (1.5, GR_SNAP, "easeoutcubic"),
                   (5, nudge(HG, rl=(-12, 0, 0), ll=(-10, 0, 0)), "easeoutquad"), (8, HG, "easeinoutsine")], 8,
     note="arms snap up, knees swing in with the catch, settle")

# Shimmy: hand over hand toward the way you move; the legs swing a little the other way.
def shimmy(left):
    """One hand reaches along the lip while the other holds; then they swap places."""
    s = 1 if left else -1
    # the lead hand (left when going left) reaches out along the edge, the other stays in
    lead_out = 26
    a = pose(HG, ra=(-112, 4, 10 + (lead_out if s < 0 else 0)), la=(-112, -4, -10 - (lead_out if s > 0 else 0)),
             rl=(-8, 0, 3 - 6 * s), ll=(6, 0, -3 - 6 * s))
    b = pose(HG, ra=(-104, 4, 6 if s > 0 else 18), re=34, la=(-104, -4, -18 if s > 0 else -6), le=34,
             rl=(-4, 0, 3 + 4 * s), ll=(2, 0, -3 + 4 * s))
    return [(0, a, "linear"), (4, b, "easeinoutsine"), (8, a, "easeinoutsine")]


clip("body_shimmy_l", shimmy(True), 8, loop=True, note="hand over hand to the left")
clip("body_shimmy_r", shimmy(False), 8, loop=True, note="hand over hand to the right")
clip("body_shimmy_l_end", [(0, shimmy(True)[0][1], "linear")], 5, end=0, note="settles out of body_shimmy_l")
clip("body_shimmy_r_end", [(0, shimmy(False)[0][1], "linear")], 5, end=0, note="settles out of body_shimmy_r")

# Ledge jump up: pull, kick, reach high for the next lip, catch.
LU_PULL = pose(ground=False, ra=(-150, 0, -24), re=90, la=(-150, 0, 24), le=90, rl=(-50, 0, 4), rk=80, ll=(-30, 0, -4), lk=60)
LU_REACH = pose(ground=False, ra=(-166, 0, -26), re=5, la=(-166, 0, 26), le=5, rl=(10, 0, 3), rk=10, ll=(-6, 0, -3), lk=20)
clip("body_ledge_up", [(0, HG, "linear"), (2, LU_PULL, "easeinquad"), (4, LU_REACH, "easeoutcubic"), (7, HG, "easeinoutsine")], 8,
     note="pull, kick, reach high, catch")

# Ledge jump sideways: push off the hands, swing across, reach out with the lead hand, catch.
def ledge_side(left):
    s = 1 if left else -1
    push = pose(ground=False, ra=(-140, 0, -(34 if s < 0 else 24)), re=40, la=(-140, 0, -(-34 if s > 0 else -24)), le=40,
                rl=(-30, 0, 10 * -s), rk=50, ll=(-20, 0, 10 * -s), lk=50)
    reach = pose(ground=False, ra=(-162, 0, -(46 if s < 0 else 26)), re=5, la=(-162, 0, -(-46 if s > 0 else -26)), le=5,
                 rl=(-10, 0, 14 * s), rk=20, ll=(4, 0, 14 * s), lk=20)
    return [(0, HG, "linear"), (2, push, "easeinquad"), (5, reach, "easeoutcubic"), (8, HG, "easeinoutsine")]


clip("body_ledge_side_l", ledge_side(True), 9, note="push off, swing left, reach, catch")
clip("body_ledge_side_r", ledge_side(False), 9, note="push off, swing right, reach, catch")

# Ledge jump back: a push off the wall with the feet, the body turning away, arms opening to fall or reach.
LB_PUSH = pose(ground=False, ra=(-150, 0, -26), re=60, la=(-150, 0, 26), le=60, rl=(-90, 0, 4), rk=110, ll=(-85, 0, -4), lk=110)
LB_OPEN = pose(ground=False, lean=-10, ra=(-60, 0, 70), re=20, la=(-60, 0, -70), le=20, rl=(-30, 0, 8), rk=40, ll=(-10, 0, -8), lk=30)
clip("body_ledge_back", [(0, HG, "linear"), (2, LB_PUSH, "easeinquad"), (4, LB_OPEN, "easeoutcubic"), (9, LB_OPEN, "linear")], 12,
     note="feet to the wall, push off, turn away, arms open")

# Lower: crouch at the edge, turn, slide down onto the hands.
LO_CROUCH = pose(lean=30, rl=(-80, 0, 4), rk=110, ll=(-70, 0, -4), lk=110, ra=(-40, 0, 10), re=40, la=(-40, 0, -10), le=40)
clip("body_lower", [(0, LO_CROUCH, "linear"), (3, pose(LO_CROUCH, ground=False, ra=(-80, 0, 10), la=(-80, 0, -10)), "easeinoutsine"),
                    (8, HG, "easeinoutsine")], 8, note="crouch at the edge, turn, down onto the hands")


# ---------------------------------------------------------------------------------------------------------------------
# The grip on a climb line (SPEC §17.25): facing a drainpipe, rope or ladder close in front, both hands on it, the
# knees bent and the insteps clamping it. The game turns the body to the line and leans it in a touch; these clips pose
# the limbs. A line is narrow, so both hands meet in front of the chest (arms turned in across, y < 0 for the right).

# Held still: the right hand high, the left at the chest, elbows soft, knees bent round the line.
PH = pose(ground=False, lean=4, ra=(-158, -16, -4), re=34, la=(-112, 16, 4), le=56,
          rl=(-42, 0, -6), rk=64, ll=(-20, 0, 6), lk=40)
held("body_pipe_hold", PH, [nudge(PH, rise=0.2, ra=(-2, 0, 0)), nudge(PH, rise=-0.1, la=(2, 0, 0), rl=(-3, 0, 0))], 40,
     "holding a line: one hand high, one at the chest, knees round it")


def pipe_climb():
    """Hand over hand: the high hand pulls down to the chest while the low one reaches up past it; the knee on its side
    drives up to the next clamp. One pair of pulls (1.6 blocks at a ladder's pace) every 8 ticks."""
    a = pose(PH, ra=(-170, -12, -6), re=18, la=(-104, 18, 6), le=64, rl=(-72, 0, -6), rk=100, ll=(-8, 0, 6), lk=22)
    b = pose(PH, ra=(-136, -16, -4), re=52, la=(-138, 16, 4), le=48, rl=(-40, 0, -6), rk=60, ll=(-40, 0, 6), lk=60)
    c = pose(PH, ra=(-104, -18, -6), re=64, la=(-170, 12, 6), le=18, rl=(-8, 0, -6), rk=22, ll=(-72, 0, 6), lk=100)
    d = pose(c, ra=(-138, -16, -4), re=48, la=(-136, 16, 4), le=52, rl=(-40, 0, -6), rk=60, ll=(-40, 0, 6), lk=60)
    return a, [(0, a, "linear"), (2, b, "easeinoutsine"), (4, c, "easeinoutsine"), (6, d, "easeinoutsine"), (8, a, "easeinoutsine")]


PC_A, PC_KEYS = pipe_climb()
clip("body_pipe_climb", PC_KEYS, 8, loop=True, note="hand over hand up a line, knees driving to the next clamp")
clip("body_pipe_climb_end", [(0, PC_A, "linear")], 5, end=0, note="settles out of body_pipe_climb")
# the free-run's pace (0.3 a tick): the same pulls, a pair every 6 ticks
clip("body_pipe_climb_fast", [(t * 0.75, k, e) for t, k, e in PC_KEYS], 6, loop=True, note="hand over hand at the free-run's pace")
clip("body_pipe_climb_fast_end", [(0, PC_A, "linear")], 5, end=0, note="settles out of body_pipe_climb_fast")

# Sliding: the hands loose round the line at the chest, arms longer, the body hanging back off it, the legs wrapped
# and crossed at the ankles under it; a quick tremble from the line running through the hands.
PS = pose(ground=False, lean=-10, ra=(-140, -18, -4), re=22, la=(-128, 18, 4), le=26,
          rl=(-52, 0, -12), rk=74, ll=(-46, 0, 12), lk=78)
held("body_pipe_slide", PS, [nudge(PS, ra=(2, 0, 0), la=(-2, 0, 0)), nudge(PS, ra=(-2, 0, 0), la=(2, 0, 0), rise=0.15)], 4,
     "sliding down a line: hands loose, hanging back, legs wrapped")

# The kick off the wall (Jump with back): knees to the chest, feet on the wall, then a shove: the body thrown back
# and the arms open for the fall (the ledge back-jump's shape, from a line).
PK_PUSH = pose(ground=False, lean=8, ra=(-150, -10, -10), re=70, la=(-150, 10, 10), le=70, rl=(-96, 0, 4), rk=112, ll=(-90, 0, -4), lk=112)
PK_OPEN = pose(ground=False, lean=-14, ra=(-56, 0, 72), re=20, la=(-56, 0, -72), le=20, rl=(-28, 0, 8), rk=36, ll=(-8, 0, -8), lk=26)
clip("body_pipe_kick", [(0, PH, "linear"), (2, PK_PUSH, "easeinquad"), (4, PK_OPEN, "easeoutcubic"), (9, PK_OPEN, "linear")], 12,
     note="feet to the wall, shove off the line, arms open")


def pipe_side(left):
    """Swung out to a side: a push off the line with the far hand, the body swinging across, the lead hand reaching."""
    s = 1 if left else -1
    push = pose(ground=False, lean=4, ra=(-130, -10 if s > 0 else -24, -(10 if s > 0 else 30)), re=40,
                la=(-130, 24 if s > 0 else 10, 30 if s > 0 else 10), le=40, rl=(-40, 0, 12 * -s), rk=60, ll=(-30, 0, 12 * -s), lk=50)
    reach = pose(ground=False, lean=0, ra=(-150, 0, -(20 if s > 0 else 70)), re=10, la=(-150, 0, 70 if s > 0 else 20), le=10,
                 rl=(-20, 0, 16 * s), rk=30, ll=(-6, 0, 16 * s), lk=24)
    return [(0, PH, "linear"), (2, push, "easeinquad"), (5, reach, "easeoutcubic"), (9, reach, "linear")]


clip("body_pipe_side_l", pipe_side(True), 11, note="push off the line, swing left, reach")
clip("body_pipe_side_r", pipe_side(False), 11, note="push off the line, swing right, reach")

# The hop (Jump alone): knees drive, both hands let go and snatch the line higher up, then the hold again.
PP_DRIVE = pose(ground=False, lean=6, ra=(-176, -8, -6), re=8, la=(-170, 8, 6), le=12, rl=(-80, 0, -6), rk=104, ll=(-66, 0, 6), lk=96)
clip("body_pipe_hop", [(0, PH, "linear"), (2, PP_DRIVE, "easeoutcubic"), (5, PH, "easeinoutsine")], 6, note="a lunge up the line")

# The landing at the line's foot: the slide's crouch meets the ground, then the body stands out of it.
PL_CROUCH = pose(lean=22, ra=(-60, -10, 6), re=40, la=(-56, 10, -6), le=40, rl=(-70, 0, 4), rk=100, ll=(-62, 0, -4), lk=96)
clip("body_pipe_land", [(0, PS, "linear"), (2, PL_CROUCH, "easeoutcubic"), (5, PL_CROUCH, "linear")], 10, end=5,
     note="down off the line into a crouch, standing out of it")


# ---------------------------------------------------------------------------------------------------------------------
# Fences, gates and walls (SPEC §17.27). The body crosses a rail in a crawl-height box with its feet just over the top;
# the figure is lowered (rise < 0) so its hips cross at the rail and the planted hand meets it, the legs swung out to the
# side. Timings are the crossings' nominal plans (body.client.BodyAnims.VAULT_PLANT / VAULT_OVER / VAULT_LEN: the hand
# meets the rail, the way over the top, the whole): a running vault 3 / 3-6 / 12, a hop 4 / 4-9 / 15, a rail on a block
# 6 / 6-13 / 21, onto a top 4 / 4-8 / 11, over a rail from a hang 5 / 5-11 / 18. Authored right-handed: the off (left)
# hand plants, the legs go over to the main (right) side, the top of the figure leans onto the planted hand (tilt < 0).

# A running vault (a speed vault): the stride takes off, the left hand reaches down onto the rail as the knees tuck up and
# to the right, the figure rolls onto that hand with the legs swung flat over the rail, the right arm out for balance;
# the hand pushes off, the figure rights itself and the legs reach down into the run.
VT_TAKE = pose(ground=False, lean=18, rl=(-62, 0, 4), rk=72, ll=(28, 0, -4), lk=46, ra=(38, 0, 14), re=40, la=(-58, 4, -10), le=24)
VT_PLANT = pose(ground=False, rise=-9, lean=30, tilt=-26, rl=(-104, 0, 30), rk=112, ll=(-96, 0, 20), lk=118,
                ra=(-46, 0, 52), re=30, la=(-34, 0, -8), le=4)
VT_OVER = pose(ground=False, rise=-12, lean=18, tilt=-56, rl=(-84, 0, 42), rk=22, ll=(-76, 0, 34), lk=36,
               ra=(-70, 0, 84), re=24, la=(-6, 0, -16), le=0)
VT_CLEAR = pose(ground=False, rise=-8, lean=12, tilt=-32, rl=(-48, 0, 26), rk=30, ll=(-26, 0, 16), lk=48,
                ra=(-60, 0, 66), re=26, la=(22, 0, -34), le=10)
VT_REACH = pose(ground=False, rise=-2, lean=10, tilt=-6, rl=(-36, 0, 6), rk=26, ll=(18, 0, -2), lk=44, ra=(-30, 0, 30), re=30, la=(-20, 0, -26), le=24)
VT_LAND = pose(lean=18, rl=(-30, 0, 3), rk=58, ll=(26, 0, -3), lk=40, ra=(30, 0, 10), re=50, la=(-44, 0, -10), le=50)
clip("body_vault", [(0, VT_TAKE, "linear"), (3, VT_PLANT, "easeoutcubic"), (4.5, VT_OVER, "easeinoutsine"), (6.5, VT_CLEAR, "easeinoutsine"),
                    (9, VT_REACH, "easeoutquad"), (12, VT_LAND, "easeinoutsine")], 14,
     note="running vault: left hand on the rail, legs swung flat over to the right, right arm out, into the run")

# A walking hop (a two-handed gate vault): both hands forward onto the rail, the weight onto them as the knees tuck up and
# a little to the right, over, the hands pushing off as the feet reach down, a soft two-footed landing.
VH_REACH = pose(lean=14, rl=(-20, 0, 3), rk=24, ll=(8, 0, -3), lk=14, ra=(-62, -6, 10), re=20, la=(-62, 6, -10), le=20)
VH_PLANT = pose(ground=False, rise=-10, lean=44, tilt=-12, rl=(-96, 0, 18), rk=118, ll=(-90, 0, 10), lk=120,
                ra=(-26, -8, 8), re=6, la=(-26, 8, -8), le=6)
VH_OVER = pose(ground=False, rise=-12, lean=34, tilt=-22, rl=(-76, 0, 34), rk=96, ll=(-72, 0, 26), lk=100, ra=(-4, -8, 10), re=0, la=(-4, 8, -10), le=0)
VH_PUSH = pose(ground=False, rise=-6, lean=16, tilt=-8, rl=(-30, 0, 10), rk=34, ll=(-24, 0, 4), lk=40, ra=(26, 0, 18), re=6, la=(26, 0, -18), le=6)
VH_LAND = pose(lean=22, rl=(-30, 0, 4), rk=64, ll=(-22, 0, -4), lk=58, ra=(-34, 0, 14), re=40, la=(-34, 0, -14), le=40)
clip("body_vault_hop", [(0, VH_REACH, "linear"), (4, VH_PLANT, "easeoutcubic"), (7, VH_OVER, "easeinoutsine"), (10, VH_PUSH, "easeinoutsine"),
                        (15, VH_LAND, "easeinoutsine")], 17,
     note="two-handed hop: palms on the rail, knees tucked over, a soft landing")

# A rail on a block (a garden wall): a foot steps up the block's face, both hands reach high for the rail and catch it,
# pull the body up past it, press with straight arms as the legs swing over to the right, and drop down beyond.
VR_STEP = pose(lean=-4, rl=(-92, 0, 4), rk=96, ll=(10, 0, -3), lk=20, ra=(-150, -6, -8), re=20, la=(-156, 6, 8), le=16)
VR_CATCH = pose(ground=False, lean=4, rl=(-70, 0, 4), rk=110, ll=(-20, 0, -3), lk=60, ra=(-168, -4, -12), re=10, la=(-168, 4, 12), le=10)
VR_PULL = pose(ground=False, rise=-4, lean=26, rl=(-96, 0, 8), rk=122, ll=(-60, 0, -2), lk=110, ra=(-96, -18, 4), re=110, la=(-96, 18, -4), le=110)
VR_PRESS = pose(ground=False, rise=-12, lean=38, tilt=-18, rl=(-90, 0, 36), rk=104, ll=(-80, 0, 26), lk=110, ra=(-14, -6, 10), re=4, la=(-14, 6, -10), le=4)
VR_OVER = pose(ground=False, rise=-12, lean=24, tilt=-34, rl=(-56, 0, 52), rk=60, ll=(-48, 0, 40), lk=72, ra=(-4, -6, 14), re=0, la=(-4, 6, -14), le=0)
VR_DROP = pose(ground=False, rise=-2, lean=10, tilt=-6, rl=(-30, 0, 10), rk=30, ll=(-14, 0, 2), lk=40, ra=(-90, 0, 40), re=20, la=(-90, 0, -40), le=20)
VR_LAND = pose(lean=24, rl=(-34, 0, 4), rk=74, ll=(-24, 0, -4), lk=66, ra=(-40, 0, 16), re=44, la=(-40, 0, -16), le=44)
clip("body_vault_high", [(0, VR_STEP, "linear"), (3, VR_CATCH, "easeoutcubic"), (6, VR_PULL, "easeinoutsine"), (9, VR_PRESS, "easeinoutsine"),
                         (12, VR_OVER, "easeinoutsine"), (16, VR_DROP, "easeinquad"), (21, VR_LAND, "easeoutcubic")], 23,
     note="a foot up the block, hands catch the rail, pull, press, legs over, drop beyond")

# Up onto a top (a wall's coping to walk, a rail over a drop): reach, pull with a knee driving up onto it, set the feet on
# the narrow top in a low crouch, arms out for balance, and rise to stand.
VO_REACH = pose(lean=-4, rl=(-24, 0, 4), rk=34, ll=(8, 0, -4), lk=14, ra=(-158, -6, -6), re=18, la=(-158, 6, 6), le=18)
VO_PULL = pose(ground=False, rise=-6, lean=28, rl=(-94, 0, 4), rk=124, ll=(-10, 0, -4), lk=40, ra=(-76, -16, 18), re=110, la=(-76, 16, -18), le=110)
VO_KNEE = pose(ground=False, rise=-8, lean=34, rl=(-80, 0, 4), rk=118, ll=(-74, 0, -4), lk=120, ra=(-20, -6, 18), re=20, la=(-20, 6, -18), le=20)
VO_CROUCH = pose(lean=24, rl=(-62, 0, 6), rk=96, ll=(-56, 0, -6), lk=92, ra=(-30, 0, 62), re=24, la=(-30, 0, -62), le=24)
VO_STAND = pose(lean=6, rl=(-8, 0, 4), rk=14, ll=(4, 0, -4), lk=10, ra=(-10, 0, 40), re=20, la=(-10, 0, -40), le=20)
clip("body_vault_onto", [(0, VO_REACH, "linear"), (3, VO_PULL, "easeoutcubic"), (6, VO_KNEE, "easeinoutsine"), (8, VO_CROUCH, "easeoutquad"),
                         (11, VO_CROUCH, "linear"), (16, VO_STAND, "easeinoutsine")], 20,
     note="reach, pull with a knee up, a low crouch on the top, arms out, rise")

# Over a rail from a hang on it: the hang's arms pull, the knees come up, the arms lock over the rail as the legs swing
# over to the right, and the body drops down beyond.
VV_PULL = pose(ground=False, rise=-2, lean=10, rl=(-60, 0, 4), rk=90, ll=(-40, 0, -4), lk=70, ra=(-130, -14, -4), re=96, la=(-130, 14, 4), le=96)
clip("body_vault_over", [(0, HG, "linear"), (3, VV_PULL, "easeinquad"), (5, VR_PRESS, "easeoutcubic"), (9, VR_OVER, "easeinoutsine"),
                         (13, VR_DROP, "easeinquad"), (18, VR_LAND, "easeoutcubic")], 20,
     note="from the hang: pull, press over the rail, legs over, drop beyond")


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
             "body_sprint": [0, 2, 4, 6], "body_wallrun_l": [0, 4], "body_wallclimb": [0, 4], "body_walljump": [0, 2, 6], "body_hang": [0],
             "body_grab": [1.5, 5], "body_ledge_up": [2, 4], "body_ledge_back": [2, 4],
             "body_pipe_hold": [0], "body_pipe_climb": [0, 4], "body_pipe_slide": [0], "body_pipe_kick": [2, 4], "body_pipe_land": [2],
             "body_vault": [3, 4.5, 6.5, 9], "body_vault_hop": [4, 7, 10], "body_vault_high": [3, 6, 9, 12], "body_vault_onto": [3, 6, 8],
             "body_vault_over": [3, 5, 9]}
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
