"""Third-person bodies of Arms' moves (Player Animation Library, optional; SPEC §24, docs/modules/arms.md).

    python3 tools/art/build_all.py arms_moves      assets/awaken/player_animations/arms_moves.json
    python3 tools/art/specs/arms_moves.py build    the same, plus the clipping report
    python3 tools/art/specs/arms_moves.py preview DIR [clip ...]
                                                   a frame strip per clip (every tick, three views) and a sheet of
                                                   every move's key poses, straight and bent
    python3 tools/art/specs/arms_moves.py check    the clipping report only

Poses are written in power_casts.py's figure terms (see its docstring). Every strike's keys sit on its row of the
timing table (data/awaken/arms/archetypes.json, read here): the wind-up ends cocked, the blow ends at the end of the
active ticks, the recovery eases home by the clip's endTick, so the body, the first-person hands, the arc and the
server's hit all land on the same tick. Authored right-handed (the game mirrors them for a left main arm); every clip
has a _bent twin for Bendable Cuboids. The game plays them through core.api.PlayerClips
(dev.awaken.arms.ArmsClips names them; dev.awaken.arms.client.ArmsBodies plays them). Edit the poses here, never
the JSON.
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
OUT_REL = "assets/awaken/player_animations/arms_moves.json"
TABLE = os.path.join(ROOT, "src/main/resources/data/awaken/arms/archetypes.json")
pose, nudge = P.pose, P.nudge

CLIPS = {}


def clip(name, keys, length, loop=False, end=None, note=""):
    """A clip and its Bendable Cuboids twin."""
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    CLIPS[name] = P.Clip(name, keys, length, loop, end, note)
    CLIPS[name + "_bent"] = P.Clip(name + "_bent", keys, length, loop, end, note + " (bent elbows and knees)", bent=True)


def held(name, base, jitters, period, note):
    """A pose held for as long as it lasts (a loop with a small tremble), and its settle back to standing."""
    n = len(jitters) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(jitters)] + [(period, base, "easeinoutsine")]
    clip(name, keys, period, loop=True, note=note)
    clip(name + "_end", [(0, base, "linear")], 5, end=0, note="settles out of " + name)


def row(move, style="longsword"):
    for a in json.load(open(TABLE))["archetypes"]:
        if a["id"] == style:
            m = a["moves"][move]
            return m["windup"], m["active"], m["recovery"]
    raise KeyError(style)


def strike(name, move, cocked, through, note, settle=None, via=None, style="longsword", arc=None):
    """A strike on its row: from the ready stance into the cocked pose by the wind-up's end (eased out; through
    {@code via} halfway when a straight path would cross the body), hard through the blow by the active ticks' end
    (eased out hard, as the hands are), held a beat, then home by the endTick."""
    w, a, r = row(move, style)
    # the blow is centred on the server's hit (the first active tick): it starts half the active early, so the blade
    # is crossing the target as it lands (SPEC §25 "First person"); the recovery takes up the difference
    c = max(0.5, w - a * 0.5) if w > 0 else 0
    keys = [(0, READY, "linear")]
    if w > 0:
        if via is not None:
            keys.append((c * 0.5, via, "easeinoutsine"))
        keys.append((c, cocked, "easeoutsine"))
    else:
        keys[0] = (0, cocked, "linear")
    if arc is not None:
        # the blow passes out in front (a straight blend from cocked to through would sweep the arm through the chest)
        keys.append((c + a * 0.5, arc, "easeinsine"))
        keys.append((c + a, through, "easeoutsine"))
    else:
        keys.append((c + a, through, "easeoutcubic"))
    hold = c + a + max(1, r // 3)
    keys.append((hold, settle or nudge(through, lean=-2), "easeoutsine"))
    end = w + a + r
    keys.append((end, READY, "easeinoutsine"))
    clip(name, keys, end + 4, note=note)


def rolled(ra, phi):
    """The blade arm (x, y, z) turned phi degrees about its own length: the forearm's roll, which turns the held blade's
    flat. A cut leads with its edge only if the flat lies across the swing (the lab's third-person edge lead,
    tools/art/anim_lab.py third); the rolls below were found there, each pose's arm turned to the one that leads best."""
    x, y, z = (math.radians(v) for v in ra)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    mm = lambda a, b: tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))
    p = math.radians(phi)
    R = mm(mm(mm(Rz, Ry), Rx), ((math.cos(p), 0, math.sin(p)), (0, 1, 0), (-math.sin(p), 0, math.cos(p))))
    ny = math.asin(max(-1.0, min(1.0, -R[2][0])))
    nx = math.atan2(R[2][1], R[2][2])
    nz = math.atan2(R[1][0], R[0][0])
    return (round(math.degrees(nx), 2), round(math.degrees(ny), 2), round(math.degrees(nz), 2))


# ---------------------------------------------------------------------------------------------------------------------
# The ready stance between moves: knees soft, blade arm low and forward, off hand up for balance.
READY = pose(lean=6, rl=(-10, 0, 4), rk=12, ll=(8, 0, -4), lk=10, ra=(-38, 6, 10), re=30, la=(-26, 0, -14), le=40)

# Strike 1, a forehand diagonal (upper right to lower left): the chest turns to the blade side and the arm draws up
# behind the shoulder; the blow turns the chest through and the arm sweeps down across the body, the off hand pulling
# back to drive it.
S1_COCK = pose(lean=2, twist=16, rl=(-14, 0, 6), rk=14, ll=(14, 0, -4), lk=12, ra=(-140, 65, 40), re=60, la=(-40, 0, -22), le=46)
# (the shoulders turn right through and the arm finishes out past the off side, so the blade reads from behind: the
# shoulder view is behind you, and a finish across the chest hid the whole blow behind the body)
S1_THRU = pose(lean=14, twist=-48, rl=(-26, 0, 4), rk=24, ll=(16, 0, -6), lk=10, ra=rolled((-50, -90, 65), -60), re=8, la=(-6, 0, -40), le=26)
# the arm lifts out to the side on its way up (straight up, it would pass in front of the face)
S1_VIA = pose(lean=4, twist=18, rl=(-12, 0, 5), rk=13, ll=(11, 0, -4), lk=11, ra=(-84, 48, 74), re=44, la=(-34, 0, -18), le=44)
# half way through the blow: the arm out level in front, the chest turning through
S1_ARC = pose(lean=8, twist=-10, rl=(-20, 0, 5), rk=18, ll=(15, 0, -5), lk=11, ra=rolled((-80, -35, 75), 30), re=20, la=(-24, 0, -26), le=36)
strike("arms_strike_1", "strike_1", S1_COCK, S1_THRU, "forehand diagonal: drawn up behind the shoulder, swept down across", via=S1_VIA,
       arc=S1_ARC)

# Strike 2, a backhand sweep (left to right): the blade arm crosses the chest to the off side, then sweeps out wide.
S2_COCK = pose(lean=8, twist=-36, rl=(-12, 0, 4), rk=16, ll=(10, 0, -4), lk=12, ra=(-98, -58, 12), re=30, la=(-20, 0, -26), le=36)
S2_THRU = pose(lean=10, twist=34, rl=(-20, 0, 6), rk=18, ll=(14, 0, -4), lk=12, ra=rolled((-88, 54, 52), 75), re=10, la=(-40, 0, -14), le=44)
strike("arms_strike_2", "strike_2", S2_COCK, S2_THRU, "backhand sweep: across the chest, then out wide")

# Strike 3, the overhead finisher: both hands up over the head, leaning back, then a chop down through the front with
# the weight forward and the knees giving.
S3_COCK = pose(lean=-8, rise=0.5, rl=(-8, 0, 4), rk=8, ll=(6, 0, -4), lk=8, ra=(-174, 8, 6), re=30, la=(-166, -12, -8), le=34)
S3_THRU = pose(lean=24, rise=-1, rl=(-36, 0, 4), rk=42, ll=(22, 0, -4), lk=26, ra=(-46, -8, 4), re=6, la=(-46, 8, -4), le=8)
strike("arms_strike_3", "strike_3", S3_COCK, S3_THRU, "overhead: both hands up, leaning back, a chop down through the front")

# Strike 4 (mastery) and the riposte: a thrust. The blade arm draws back bent at the elbow, the chest turned to the
# blade side; the blow drives the arm straight with the chest turning in and a lunge onto the lead foot.
# (a vanilla arm can't bend its wrist: a straight arm raised forward points the blade at the sky, so the arm keeps the
# angle that lays the blade level, ahead, and the body drives it, the stance drawn back and the lunge going in; the
# lab's point lead, tools/art/anim_lab.py third)
# (the arm and chest stay on the thrust's line from cocked to through, so the point drives straight along it, never
# swinging across: a turn of the chest or the arm swept a long point's tip sideways, 45 degrees off its line; the body
# leans in less than it lunges, its lean arcing a long point off the line too; its elbow bends only a little, as a
# straightening forearm tilts a blade held square across the fist off the line in the bent twin)
T_COCK = pose(lean=-3, twist=0, shift=-3, rl=(-8, 0, 6), rk=12, ll=(12, 0, -4), lk=12, ra=(-12, 0, 4), re=12, la=(-38, 0, -20), le=40)
T_THRU = pose(lean=6, twist=0, shift=5, rl=(-48, 0, 4), rk=44, ll=(26, 0, -4), lk=8, ra=(-15, 0, 4), re=0, la=(8, 0, -26), le=20)
strike("arms_strike_4", "strike_4", T_COCK, T_THRU, "thrust: drawn back, then driven straight with a lunge")

# The Gauntlets' punches: worn on the fist (arms_holds.worn_third_person), the knuckles point down the forearm, so a
# punch lands knuckles first only when the fist travels along the forearm: the arm is set where the punch goes and the
# body drives it there (a vanilla arm can't straighten; the bent twin's elbow adds its own drive). Never the blade's
# high wind-up, which brought a fist through the head. A jab: the arm level, the chest turned in behind it, a lunge.
# An overhand: the arm angled down at the foe, the body dropping in behind it. An uppercut: the arm angled up in front
# of the chin, the knees driving the body up under it. A hook: the elbow bent level, the forearm across the front,
# the chest turning it round (straight-armed it can only sweep: the bent twin is the hook).
# (each pair found in the lab: knuckles first at the hit, the bent twin >= 0.8, nothing into the head or body)
JB_COCK = pose(lean=-5, rise=0.7, twist=9, shift=-2.6, rl=(-8, 0, 6), rk=12, ll=(12, 0, -4), lk=12, ra=(-85, 14, 5), re=55,
               la=(-70, -24, -24), le=100)
JB_THRU = pose(lean=12, rise=0.3, twist=-23, shift=5.5, rl=(-40, 0, 4), rk=36, ll=(24, 0, -4), lk=8, ra=(-76, 3, 9), re=3,
               la=(-70, -24, -24), le=100)
OV_COCK = pose(lean=-2, twist=13, shift=-0.9, rl=(-10, 0, 6), rk=10, ll=(10, 0, -4), lk=10, ra=(-50, 7, 1), re=57,
               la=(-70, -24, -24), le=100)
OV_THRU = pose(lean=22, rise=-4.4, twist=10, shift=-0.4, rl=(-50, 0, 4), rk=70, ll=(26, 0, -4), lk=50, ra=(-53, 13, 13), re=0,
               la=(-70, -24, -24), le=100)
UP_COCK = pose(lean=-14, twist=16, shift=-2.1, rl=(-22, 0, 6), rk=34, ll=(14, 0, -4), lk=28, ra=(-36, 10, 40), re=59,
               la=(-70, -24, -24), le=100)
UP_THRU = pose(lean=4, twist=-12, shift=3.4, rl=(-8, 0, 4), rk=6, ll=(10, 0, -4), lk=6, ra=(-133, 18, -11), re=19,
               la=(-70, -24, -24), le=100)
HK_COCK = pose(lean=-6, rise=0.9, twist=18, shift=-2.5, rl=(-14, 0, 6), rk=16, ll=(12, 0, -4), lk=12, ra=(-38, -38, 91), re=134,
               la=(-70, -24, -24), le=100)
HK_THRU = pose(lean=15, rise=0.6, twist=-38, shift=4.6, rl=(-24, 0, 4), rk=22, ll=(16, 0, -6), lk=10, ra=(-92, 33, 88), re=124,
               la=(-70, -24, -24), le=100)

# A shield bash: the shield raised square in front of the chest (the arm up, so the weapon stands upright and its face,
# turned by the wrist, looks ahead), the body drawn back and then driven in behind it with a lunge.
BS_COCK = pose(lean=-6, twist=-6, shift=-3, rl=(-8, 0, 6), rk=12, ll=(12, 0, -4), lk=12, ra=(-84, 8, 8), re=40, la=(-38, 0, -20), le=40)
BS_THRU = pose(lean=10, twist=0, shift=5, rl=(-48, 0, 4), rk=44, ll=(26, 0, -4), lk=8, ra=(-88, 0, 4), re=0, la=(8, 0, -26), le=20)
strike("arms_riposte", "riposte", T_COCK, nudge(T_THRU, shift=1, lean=4, rl=(-8, 0, 0)), "riposte: the thrust, deeper")

# The Slipcut, out of a dodge: low and wide, the chest whipping round, the blade flat across the front.
SC_COCK = pose(lean=14, rise=-1, twist=44, rl=(-20, 0, 8), rk=40, ll=(14, 0, -6), lk=30, ra=rolled((-84, 70, 22), 60), re=24, la=(-30, 0, -30), le=30)
SC_THRU = pose(lean=18, rise=-1, twist=-40, rl=(-30, 0, 6), rk=44, ll=(18, 0, -6), lk=26, ra=(-102, -54, 20), re=8, la=(-10, 0, -40), le=20)
strike("arms_slipcut", "slipcut", SC_COCK, SC_THRU, "low and wide out of a dodge, the chest whipping round")

# The heavy: held, the blade drawn down and back to the blade side, knees sunk, trembling; released, a rising cut up
# across to the off side, the body rising with it.
HV_DRAW = pose(lean=12, rise=-1.5, twist=40, rl=(-24, 0, 8), rk=38, ll=(16, 0, -6), lk=34, ra=rolled((-12, 62, 30), -90), re=34, la=(-56, 0, -18), le=44)
held("arms_heavy_charge", HV_DRAW, [nudge(HV_DRAW, ra=(2, 1, 0), rise=0.2), nudge(HV_DRAW, ra=(-2, -1, 0), rise=-0.2)], 4,
     "the heavy held: blade drawn down and back, knees sunk, trembling")
HV_THRU = pose(lean=-4, rise=1, twist=-30, rl=(-18, 0, 4), rk=10, ll=(22, 0, -6), lk=12, ra=rolled((-138, -18, 52), -15), re=6, la=(-30, 0, -34), le=30)
strike("arms_heavy", "heavy", HV_DRAW, HV_THRU, "the heavy released: a rising cut up across to the off side")

# The Execution on a broken foe: both hands high, then the whole weight down into it, the knees nearly to the ground.
EX_COCK = pose(lean=-10, rise=1, rl=(-10, 0, 4), rk=10, ll=(8, 0, -4), lk=10, ra=(-180, 0, 4), re=20, la=(-178, 0, -4), le=22)
EX_THRU = pose(lean=34, rise=-3, rl=(-52, 0, 4), rk=74, ll=(30, 0, -4), lk=56, ra=(-38, -12, 2), re=4, la=(-38, 12, -2), le=4)
strike("arms_execution", "execution", EX_COCK, EX_THRU, "both hands high, then the whole weight down into it",
       settle=nudge(EX_THRU, lean=-4, rise=0.5))

# The Art, the Resonance let go: drawn lower and further than a heavy, both hands on the grip, then a great level
# sweep round the front with a stride into it (the wave goes on from the blade).
AR_DRAW = pose(lean=16, rise=-2, twist=48, rl=(-30, 0, 8), rk=52, ll=(18, 0, -6), lk=40, ra=rolled((-18, 66, 34), -120), re=30, la=(-30, 40, -6), le=70)
AR_THRU = pose(lean=12, rise=-0.5, twist=-52, shift=2, rl=(-44, 0, 6), rk=40, ll=(30, 0, -6), lk=12, ra=(-100, -46, 10), re=6,
               la=(-80, -30, -30), le=20)
strike("arms_art", "art", AR_DRAW, AR_THRU, "the Art: drawn low with both hands, a great level sweep with a stride")

# The Undercut, out of a slide: still low and reclined like the slide itself, the blade swept flat across the front.
UC_COCK = pose(ground="all", lean=-40, twist=30, rl=(-80, 0, 5), rk=10, ll=(-36, 0, -8), lk=100, ra=(-70, 70, 20), re=30, la=(80, 0, -24), le=10)
UC_THRU = pose(ground="all", lean=-34, twist=-30, rl=(-80, 0, 5), rk=10, ll=(-36, 0, -8), lk=100, ra=(-80, -46, 30), re=10, la=(80, 0, -24), le=10)
strike("arms_undercut", "undercut", UC_COCK, UC_THRU, "out of a slide: reclined, the blade swept flat across the front")

# The Plunge, landing out of a fall: both hands high and the knees up as it comes down, then the blade driven into the
# ground with the knees taking it.
PL_COCK = pose(ground=False, rise=1, lean=-6, rl=(-40, 0, 6), rk=60, ll=(-20, 0, -6), lk=50, ra=(-176, 6, 6), re=24, la=(-170, -8, -6), le=26)
PL_THRU = pose(lean=38, rise=-3, rl=(-60, 0, 6), rk=90, ll=(24, 0, -6), lk=70, ra=(-24, -6, 2), re=2, la=(-26, 6, -2), le=4)
strike("arms_plunge", "plunge", PL_COCK, PL_THRU, "out of a fall: both hands high, driven into the ground, the knees taking it")

# The guard, held while the use key is: blade across the front, the off hand braced behind it, knees soft.
GD = pose(lean=8, rl=(-14, 0, 6), rk=22, ll=(12, 0, -6), lk=18, ra=(-78, -46, 6), re=46, la=(-62, 34, -10), le=60)
held("arms_guard", GD, [nudge(GD, ra=(1, 0, 0), la=(-1, 0, 0)), nudge(GD, ra=(-1, 0, 0), la=(1, 0, 0))], 8,
     "the guard: blade across the front, off hand braced, knees soft")
# A shot strikes the guard (SPEC §25 "Shots"): the blade knocked in toward the chest at once, the body rocked back on
# its heels, a small rebound, braced again; it ends on the guard's own pose (ArmsClips.GUARD_JOLT_TICKS) and the game
# holds the guard again.
GD_HIT = nudge(GD, lean=-7, shift=-0.6, ra=(9, 5, 0), re=14, la=(7, -4, 0), le=12, rk=4, lk=4)
GD_BACK = nudge(GD, lean=1.5, ra=(-2, -1, 0), re=-3, la=(-2, 1, 0))
clip("arms_guard_jolt", [(0, GD, "linear"), (1, GD_HIT, "easeoutcubic"), (3, GD_BACK, "easeinoutsine"), (6, GD, "easeinoutsine")], 8,
     note="a shot strikes the guard: the blade knocked in, the body rocked back, braced again")

# The weapon's own cutting motion while vanilla mines with it (SPEC §40 "The chop"; first person's loops in
# tools/art/swing_chop.py): loops on the hands' clock, each clip's tick 0 where the game enters the hands' loop, so a
# blow lands on the body as the hands' does (the chop's 5 ticks in, the hack's 3.5) and every loop after.
def cut(name, keys, period, note):
    clip(name, keys, period, loop=True, note=note)
    clip(name + "_end", [(0, keys[0][1], "linear")], 5, end=0, note="settles out of " + name)


# The chop (8 ticks): the axe arm on its way up, set high over the shoulder with the body leaning back, held a beat,
# driven down through the wood ahead with the weight forward and the knees giving, a small kick back off it.
CH_RISE = pose(lean=2, twist=10, rl=(-12, 0, 4), rk=14, ll=(10, 0, -4), lk=12, ra=(-118, 10, 24), re=24, la=(-40, 0, -16), le=42)
CH_SET = pose(lean=-6, twist=16, rise=0.3, rl=(-8, 0, 4), rk=8, ll=(6, 0, -4), lk=8, ra=(-160, 10, 26), re=30, la=(-52, 0, -18), le=36)
CH_THRU = pose(lean=16, twist=-8, rise=-0.6, rl=(-26, 0, 4), rk=30, ll=(16, 0, -4), lk=18, ra=(-52, -4, 10), re=6, la=(-28, 0, -24), le=30)
CH_KICK = pose(lean=12, twist=-4, rise=-0.3, rl=(-20, 0, 4), rk=22, ll=(14, 0, -4), lk=14, ra=(-66, 0, 12), re=14, la=(-32, 0, -20), le=34)
cut("arms_chop", [(0, CH_RISE, "linear"), (2, CH_SET, "easeoutsine"), (3, nudge(CH_SET, lean=-1.5, ra=(-4, 0, 0)), "easeinoutsine"),
                  (5, CH_THRU, "easeincubic"), (6, CH_KICK, "easeoutsine"), (8, CH_RISE, "easeinoutsine")], 8,
    "the chop: the axe set high over the shoulder, driven down through the wood ahead, a kick back off it")
# The hack (6 ticks): a quicker forehand diagonal, from out at the blade's side down across the front, on through what
# it cut, back up its side.
HK_MID = pose(lean=7, twist=-2, rl=(-14, 0, 5), rk=15, ll=(12, 0, -4), lk=11, ra=(-30, 12, 46), re=20, la=(-28, 0, -22), le=40)
HK_SET = pose(lean=4, twist=16, rl=(-12, 0, 5), rk=13, ll=(11, 0, -4), lk=11, ra=(-18, 36, 70), re=24, la=(-36, 0, -18), le=44)
HK_THRU = pose(lean=11, twist=-20, rl=(-18, 0, 5), rk=18, ll=(14, 0, -5), lk=11, ra=(-40, -44, 14), re=10, la=(-22, 0, -28), le=36)
HK_FOLLOW = pose(lean=11, twist=-26, rl=(-19, 0, 5), rk=18, ll=(14, 0, -5), lk=11, ra=(-32, -56, 16), re=8, la=(-20, 0, -30), le=34)
cut("arms_hack", [(0, HK_MID, "linear"), (1.5, HK_SET, "easeoutsine"), (3.5, HK_THRU, "easeincubic"), (4.5, HK_FOLLOW, "easeoutsine"),
                  (6, HK_MID, "easeinoutsine")], 6,
    "the hack: a quick diagonal from the blade's side down across the front, on through, back up its side")

# The dodge (6 ticks): a quick low step away, the arms out for balance, back to the ready stance.
def dodge(name, legs, tilt, lean, note):
    step = pose(lean=lean, tilt=tilt, rise=-1, rk=34, lk=34, ra=(-34, 0, 34), re=30, la=(-34, 0, -34), le=30, **legs)
    clip(name, [(0, READY, "linear"), (2, step, "easeoutcubic"), (4, nudge(step, rise=0.5), "easeoutsine"), (6, READY, "easeinoutsine")], 9,
         note=note)


dodge("arms_dodge_back", dict(rl=(28, 0, 6), ll=(-16, 0, -6)), 0, -14, "a quick step back, arms out")
dodge("arms_dodge_left", dict(rl=(-6, 0, 14), ll=(0, 0, -30)), -14, 6, "a quick step to the off side")
dodge("arms_dodge_right", dict(rl=(0, 0, 30), ll=(-6, 0, -14)), 14, 6, "a quick step to the blade side")


# ---------------------------------------------------------------------------------------------------------------------
# The other styles (SPEC §25 "Archetypes"): each move's clip on its own style's row (its wind-up and blow on that row's
# ticks, so the body, the hit and the hands agree), from the poses above by the move's kind: a side cut (S1/S2), an
# overhead (S3/EX), a thrust (T), a whole sweep round (the Art's). Heavier styles lean further into their blows and
# sink lower. Named arms_<style>_<move>; a move a style has no clip for plays the Longsword's.

def _mirror_limb(v):
    return (v[0], -v[1], -v[2])


def mirrored(p):
    """A pose mirrored left for right: the off hand does what the main hand did (a weapon in each hand)."""
    q = dict(p)
    q["ra"], q["la"] = _mirror_limb(p["la"]), _mirror_limb(p["ra"])
    q["rl"], q["ll"] = _mirror_limb(p["ll"]), _mirror_limb(p["rl"])
    q["re"], q["le"], q["rk"], q["lk"] = p["le"], p["re"], p["lk"], p["rk"]
    for key in ("twist", "tilt", "side", "head_z"):
        q[key] = -p[key]
    return q


def paired(p):
    """Both arms strike together: the off arm the blade arm's mirror."""
    q = dict(p)
    q["la"], q["le"] = _mirror_limb(p["ra"]), p["re"]
    q["twist"] = 0.0
    return q


PUNCHES = ("jab", "hook", "upper", "over")


def styled(style, lean=0.0, sink=0.0):
    k = lambda p: nudge(p, lean=lean, rise=-sink) if (lean or sink) else p
    table = json.load(open(TABLE))
    a = next(x for x in table["archetypes"] if x["id"] == style)
    kinds = {
        "side1": (S1_COCK, S1_THRU, S1_VIA, S1_ARC), "side2": (S2_COCK, S2_THRU, None, None), "over": (S3_COCK, S3_THRU, None, None),
        "thrust": (T_COCK, T_THRU, None, None), "round": (AR_DRAW, AR_THRU, None, None), "exec": (EX_COCK, EX_THRU, None, None),
        "rise": (HV_DRAW, HV_THRU, None, None), "bash": (BS_COCK, BS_THRU, None, None),
        "hook": (HK_COCK, HK_THRU, None, None), "upper": (UP_COCK, UP_THRU, None, None),
        "jab": (JB_COCK, JB_THRU, None, None), "overfist": (OV_COCK, OV_THRU, None, None),
    }
    for move, m in a["moves"].items():
        sw = m.get("swing", {})
        if move in ("undercut", "plunge", "slipcut") and sw.get("form") not in PUNCHES:
            continue  # the Longsword's own (out of a slide, a fall, a dodge) fit every blade; a fist punches its own
        tilt_from = sw.get("from", [0, 0, 0])[3] if len(sw.get("from", [])) > 3 else 0
        form = sw.get("form")
        if form is not None:
            # the move's form (tools/arms/forms.py): the body that does it
            if form == "bash":
                kind = "bash"
            elif form == "jab":
                kind = "jab"
            elif form == "over":
                kind = "overfist"
            elif form == "stab":
                kind = "thrust"
            elif form in ("slam", "chop"):
                kind = "exec" if (form == "slam" and m.get("arc", 0) >= 300) or move == "execution" else "over"
            elif form in ("whirl", "spin"):
                kind = "round"
            elif form == "upper":
                kind = "upper"
            elif form == "hook":
                kind = "hook"
            elif form == "rise":
                kind = "rise"
            else:
                d = sw.get("dir") or [sw.get("to", [0])[0] - sw.get("from", [0])[0], 0]
                kind = "side1" if d[0] < 0 else "side2"
        elif m.get("swish") == "thrust":
            kind = "thrust"
        elif move == "execution":
            kind = "exec"
        elif m.get("arc", 0) >= 300 and tilt_from > -20 and move != "heavy":
            kind = "round"
        elif tilt_from <= -10:
            kind = "over"
        elif move == "heavy":
            kind = "rise"
        else:
            # which way the hand travels: right to left is the forehand, left to right the backhand
            kind = "side1" if sw.get("to", [0])[0] < sw.get("from", [0])[0] else "side2"
        cocked, thru, via, arc = kinds[kind]
        hand = sw.get("hand", "main")
        if hand == "off":  # a weapon in each hand: the off hand's strike is the main's, mirrored
            cocked, thru = mirrored(cocked), mirrored(thru)
            via = None if via is None else mirrored(via)
            arc = None if arc is None else mirrored(arc)
        elif hand == "both":  # both blades at once: the off arm does what the blade arm does
            cocked, thru = paired(cocked), paired(thru)
            via = None if via is None else paired(via)
            arc = None if arc is None else paired(arc)
        strike(f"arms_{style}_{move}", move, k(cocked), k(thru), f"{a['name']}: {move} ({kind}, {hand})", via=via, style=style,
               arc=None if arc is None else k(arc))


styled("axe", lean=3)
styled("maul", lean=6, sink=0.5)
styled("spear")
styled("greatblade", lean=5, sink=0.3)
styled("rapier", lean=-1)
styled("daggers", lean=4, sink=0.6)
styled("katana", lean=2, sink=0.2)
styled("twinblades", lean=3, sink=0.3)
styled("glaive", lean=4, sink=0.2)
styled("scythe", lean=5, sink=0.3)
styled("gauntlets", lean=6, sink=0.5)
styled("chainblade", lean=2)
styled("shieldblade", lean=3, sink=0.3)
styled("warstaff", lean=3, sink=0.2)
styled("precursor", lean=2)

# Third person from first person's blade (arms_rig4.py): for its styles each move's weapon follows first person's own
# path and the body follows it (the chest turning, both hands on the grip); every other move keeps its kind's poses.
# BASE keeps every clip as its kind made it (the rig's bodies). A rigged style plays its own slide, fall and dodge
# moves (ArmsClips.OWN_SHARED) and holds its own heavy's draw while it charges (arms_<style>_heavy_charge).
BASE = dict(CLIPS)


def _rig():
    import arms_rig4 as R4
    data = R4.load()
    for style in R4.STYLES:
        moves = data.get(style, {})
        for move, d in moves.items():
            own = f"arms_{style}_{move}"
            base = own if own in BASE else f"arms_{move}"
            if base not in BASE:
                continue
            CLIPS[own], CLIPS[own + "_bent"] = R4.make_clips(own, BASE[base], BASE[base + "_bent"], d,
                                                             f"{style}: {move} (from first person's blade)")
        if "heavy" in moves:
            base = f"arms_{style}_heavy" if f"arms_{style}_heavy" in BASE else "arms_heavy"
            for c_ in R4.charge_clips(style, BASE[base], BASE[base + "_bent"], moves["heavy"]):
                CLIPS[c_.name] = c_


_rig()


# ---------------------------------------------------------------------------------------------------------------------
# Output, checks, previews

def document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/arms_moves.py: edit the poses there, not this file.",
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
    """Every move cocked, mid-blow and through, straight (top) and bent (bottom), from the front three-quarter and the side."""
    from PIL import Image, ImageDraw
    beats = {}
    for n in ("arms_strike_1", "arms_strike_2", "arms_strike_3", "arms_strike_4", "arms_heavy", "arms_riposte", "arms_slipcut", "arms_execution",
              "arms_art", "arms_undercut", "arms_plunge"):
        w, a, _ = row(n[5:])
        beats[n] = [w, w + a * 0.5, w + a]
    beats.update({"arms_heavy_charge": [0], "arms_guard": [0], "arms_dodge_back": [2], "arms_dodge_left": [2]})
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
        for n in argv[2:]:
            print(P.strip(CLIPS[n], out_dir))
        print(moves_sheet(out_dir))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
