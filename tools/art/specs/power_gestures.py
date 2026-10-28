"""Per-power cast gestures, third person (docs/power-anims/PLAN.md sections 5.3 and 7, phases P2-P4): one function per gesture,
each building a clip from the rig tool's figure-term poses (power_casts.pose), parameterised by the accents of a row of
power_cast_table.py. Importing this module registers every clip (straight and bent twins) in power_casts.CLIPS, under the
names the profile table gives them:

    p_<power>            the begin clip: anticipation, the release key, follow-through, the fade home
    p_<power>_hold       a channel's trembling loop (the begin clip ends in its first pose); p_<power>_hold_end lets go
    p_<power>_end        a channel's own let-go (a strike), where the hold's plain fade is not enough

A gesture returns {keys, length, end, release, hold, hold_at, finish}: the release is the tick the effect leaves on (a wind-up
power's server wait equals it). write_table() writes the profile table the game reads
(src/main/resources/data/awaken/powers/casts.json), checking each wind-up against its gesture.

Poses follow the family body languages of the plan: Kinetic plants and hits, Velocity leans and goes, Flight gathers low to high,
Energy channels from the core, Shadow stays low and quiet, Mind is still, Architect draws straight lines and sets them, Tech taps
and locks, Spirit opens. The clipping check of power_casts.py runs on every clip.
"""
import json
import os
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import power_casts as PC  # noqa: E402
import power_cast_table as T  # noqa: E402

pose, nudge, NEUTRAL = PC.pose, PC.nudge, PC.NEUTRAL
G = {}
PROFILES = OrderedDict()  # power -> dict(begin3p, hold3p, holdAt, end3p, release)


def gesture(fn):
    G[fn.__name__] = fn
    return fn


def spec(keys, length, release, end=None, hold=None, hold_at=None, finish=None):
    return dict(keys=keys, length=length, end=end if end is not None else keys[-1][0], release=release, hold=hold, hold_at=hold_at,
                finish=finish)


L = "linear"
EQ, EOE, EOS, EIOS, EOC, EOX, EIX = "easeoutquad", "easeoutexpo", "easeoutsine", "easeinoutsine", "easeoutcubic", "easeoutexpo", "easeinexpo"


# ---- Kinetic -------------------------------------------------------------------------------------------------------

@gesture
def stomp(a):
    """Shockwave's stamp (the P0 clip) and Spire's drive (arms rigid down into the ground)."""
    if a.get("arms") != "down":
        return None  # shockwave keeps the clip registered in power_casts.py
    coil = pose(PC.ST_COIL, ra=(-20, 0, 14), la=(-20, 0, -14), re=60, le=60)
    hang = pose(PC.ST_HANG, ra=(20, 0, 34), la=(20, 0, -34), re=20, le=20)
    plant = pose(PC.ST_PLANT, ra=(-6, 0, 10), la=(-6, 0, -10), re=0, le=0, lean=20)
    rebound = pose(plant, lean=12, rl=(-6, 0, 6), rk=18, lk=40)
    return spec([(0, NEUTRAL, L), (2, coil, EQ), (3, hang, EOS), (4, hang, L), (5, plant, EIX), (7, rebound, EOS), (11, PC.ST_SETTLE, EIOS)], 15, 5)


@gesture
def ground_slap(a):
    """Frost Nova: down on one knee, the main palm slapped flat on the ground."""
    load = pose(lean=18, ra=(46, 0, 14), la=(-20, 0, -12), rl=(10, 0, 6), ll=(-8, 0, -6), rk=60, lk=60, re=20)
    slap = pose(lean=38, shift=1.5, ra=(-48, -4, 0), la=(24, 0, -14), rl=(-70, 0, 6), ll=(10, 0, -8), rk=95, lk=100, re=0)
    rebound = pose(slap, lean=30, ra=(-40, -4, 0))
    settle = pose(lean=10, ra=(-14, 0, 6), la=(8, 0, -8), rk=40, lk=40, rl=(-20, 0, 4), ll=(4, 0, -4))
    return spec([(0, NEUTRAL, L), (2, load, EQ), (3, load, L), (5, slap, EIX), (7, rebound, EOS), (11, settle, EIOS)], 15, 5)


@gesture
def heave(a):
    """Terrain Rip and Collapse: both hands to the ground, hauled up with the legs, thrown forward."""
    far = a.get("throw") == "far"
    reach = pose(lean=34, ra=(-50, -14, 0), la=(-50, 14, 0), rl=(14, 0, 10), ll=(-10, 0, -10), rk=60, lk=65, re=5, le=5)
    pull = pose(lean=-8, rise=1.0, ra=(-120, -10, 0), la=(-120, 10, 0), re=70, le=70, rk=30, lk=30)
    throw = pose(lean=22 if far else 18, shift=1.5, ra=(-92, -6, 0), la=(-92, 6, 0), ra_aim=True, la_aim=True, re=0, le=0,
                 rl=(26, 0, 9), ll=(-18, 0, -9), rk=15, lk=40)
    settle = pose(throw, lean=6, ra=(-50, -8, 0), la=(-50, 8, 0), ra_aim=False, la_aim=False, re=20, le=20)
    return spec([(0, NEUTRAL, L), (2, reach, EQ), (4, pull, EIOS), (6, throw, EOX), (10, settle, EIOS)], 14, 6)


@gesture
def guard(a):
    """Brace: feet set, forearms crossed up before the face."""
    pre = pose(lean=10, rk=40, lk=40, rl=(8, 0, 9), ll=(-6, 0, -9), ra=(-48, -4, 14), la=(-48, 4, -14), re=70, le=70)
    up = pose(lean=8, ra=(-100, -45, 0), la=(-100, 45, 0), re=110, le=110, rl=(10, 0, 9), ll=(-8, 0, -9), rk=35, lk=30)
    return spec([(0, NEUTRAL, L), (2, pre, EQ), (3, up, EOC), (9, up, L), (12, PC.ST_SETTLE, EIOS)], 15, 3)


@gesture
def clap_hard(a):
    """Thunderclap: the arms thrown wide, the hands meet at the chest with the weight behind them. Echo Swap and Twin Strike: lighter."""
    light = a.get("light") or a.get("twin")
    out = pose(lean=-4, ra=(-30, 0, 70), la=(-30, 0, -70), re=10, le=10, rl=(8, 0, 8), ll=(-6, 0, -8), rk=10, lk=10, twist=a.get("twist", 0))
    meet = pose(lean=6 if light else 10, ra=(-80, -34, 0), la=(-80, 34, 0), re=60, le=60, rl=(18, 0, 8), ll=(-14, 0, -8), rk=25, lk=30,
                twist=-a.get("twist", 0))
    held = pose(meet, lean=(meet["lean"] + 2))
    return spec([(0, NEUTRAL, L), (2, out, EQ), (4, meet, EOX), (7, held, EIOS), (11, PC.ST_SETTLE, EIOS)], 14, 4)


@gesture
def leap_spring(a):
    """Leap: a deep crouch, the arms swung back, then up and away."""
    load = pose(lean=22, ra=(30, 0, 12), la=(30, 0, -12), rk=70, lk=70, rl=(14, 0, 8), ll=(-10, 0, -8))
    spring = pose(lean=-8, rise=2.0, ra=(-146, 0, -36), la=(-146, 0, 36), rk=10, lk=10, rl=(10, 0, 4), ll=(8, 0, -4), ground=False)
    return spec([(0, NEUTRAL, L), (1, load, EQ), (2, spring, EOX), (6, pose(spring, lean=4, ra=(-110, 0, 30), la=(-110, 0, -30)), EIOS)], 10, 2)


# ---- Velocity ------------------------------------------------------------------------------------------------------

@gesture
def lightning_snap(a):
    """Lightning Step: one arm snapped out along the aim, the body gone the same instant."""
    pre = pose(lean=14, twist=-14, ra=(-60, -20, 6), la=(10, 0, -14), rk=40, lk=40, rl=(12, 0, 6), ll=(-8, 0, -6))
    aim = pose(lean=22, twist=-30, ra=(-92, 0, 0), ra_aim=True, la=(30, 0, -20), rl=(28, 0, 8), ll=(-26, 0, -8), rk=20, lk=45)
    return spec([(0, NEUTRAL, L), (1, pre, EQ), (2, aim, EOX), (5, aim, L), (9, PC.V_SETTLE, EIOS)], 12, 2)


@gesture
def breath(a):
    """Flow: slow. The hands rise with an in-breath, then press out as the world slows."""
    inn = pose(lean=-4, rise=0.8, ra=(-50, -18, 14), la=(-50, 18, -14), re=70, le=70)
    out = pose(lean=6, ra=(-70, -12, 26), la=(-70, 12, -26), re=20, le=20, rk=14, lk=14)
    return spec([(0, NEUTRAL, L), (4, inn, EIOS), (6, out, EOS), (10, out, L), (14, PC.ST_SETTLE, EIOS)], 17, 6)


@gesture
def dash_shadow(a):
    """Shadow Step: a low crouched lunge, the cloak of arms swept back."""
    load = pose(lean=30, twist=-24, ra=(-96, -50, 0), la=(34, 0, -22), rk=85, lk=90, rl=(16, 0, 28), ll=(-10, 0, -28), re=110)
    burst = pose(lean=44, shift=2, twist=26, ra=(50, 0, 22), la=(50, 0, -22), rl=(36, 0, 26), ll=(-30, 0, -26), rk=40, lk=70)
    return spec([(0, NEUTRAL, L), (1, load, EQ), (3, burst, EOX), (8, pose(burst, lean=30, ra=(30, 0, 18), la=(30, 0, -18)), EIOS),
                 (11, PC.S_SETTLE, EIOS)], 14, 3)


@gesture
def kick_back(a):
    """Jet Burst: both boots fire, the legs trailing, the arms swept out for balance."""
    burst = pose(PC.V_BURST, rl=(40, 0, 4), ll=(36, 0, -4), rk=50, lk=55, ra=(40, 0, 36), la=(40, 0, -36))
    return spec([(0, NEUTRAL, L), (1, PC.V_LOAD, EQ), (2, burst, EOX), (8, pose(burst, lean=30), EIOS), (11, PC.V_SETTLE, EIOS)], 14, 2)


@gesture
def ember_lunge(a):
    """Ember Step: a lunge off the back foot, one fist driven forward, the other trailing."""
    load = pose(lean=20, ra=(-30, 0, 14), la=(20, 0, -14), rk=60, lk=60, rl=(18, 0, 8), ll=(-12, 0, -8))
    lunge = pose(lean=32, shift=2.5, ra=(-80, -6, 0), la=(60, 0, -14), rl=(-50, 0, 4), rk=40, ll=(34, 0, -6), lk=20)
    return spec([(0, NEUTRAL, L), (1, load, EQ), (2, lunge, EOX), (8, pose(lunge, lean=22), EIOS), (11, PC.V_SETTLE, EIOS)], 14, 2)


@gesture
def float_dash(a):
    """Ghost Step: weightless, arms floating out, the body skimming."""
    drift = pose(lean=20, rise=1.5, ra=(-30, 0, 50), la=(-30, 0, -50), rl=(8, 0, 3), ll=(14, 0, -3), ground=False)
    return spec([(0, NEUTRAL, L), (1, pose(drift, lean=10, rise=0.5), EQ), (2, drift, EOX), (8, pose(drift, rise=2.2, lean=14), EIOS),
                 (12, PC.V_SETTLE, EIOS)], 15, 2)


# ---- Flight and gravity --------------------------------------------------------------------------------------------

@gesture
def gravity_press(a):
    """Gravity Well: the aimed arm raised, then pressed down at the spot, palm down, the weight behind it."""
    raised = pose(lean=-6, ra=(-130, 0, 10), la=(10, 0, -16))
    press = pose(lean=12, shift=1, ra=(-86, 0, 0), ra_aim=True, la=(-30, 0, -30), rl=(20, 0, 9), ll=(-14, 0, -9), rk=14, lk=36)
    return spec([(0, NEUTRAL, L), (2, raised, EQ), (4, press, EOX), (8, press, L), (12, PC.F_SETTLE, EIOS)], 15, 4)


@gesture
def flip_hands(a):
    """Gravity Flip: both hands scooped up palms to the sky, then turned over and slammed down."""
    low = pose(lean=8, ra=(-50, -14, 10), la=(-50, 14, -10), re=60, le=60, rk=24, lk=24)
    high = pose(lean=-10, rise=1.5, ra=(-146, -4, -34), la=(-146, 4, 34), re=15, le=15, rk=20, lk=20)
    press = pose(lean=14, ra=(-60, -8, 10), la=(-60, 8, -10), re=10, le=10, rk=30, lk=30)
    return spec([(0, NEUTRAL, L), (2, low, EQ), (4, high, EOS), (5, press, EIX), (8, pose(press, lean=8), EOS), (12, PC.F_SETTLE, EIOS)], 15, 5)


@gesture
def telekinesis(a):
    """Telekinesis: the arm reaches, the fingers clawed, the hold trembles; the let-go throws."""
    reach = pose(lean=4, ra=(-100, 0, 0), ra_aim=True, re=10, la=(10, 0, -14), rl=(10, 0, 6), ll=(-8, 0, -6))
    jit = [nudge(reach, ra=(2, 2, 0), lean=0.6), nudge(reach, ra=(-2, -1, 0), lean=-0.4)]
    back = pose(lean=-10, ra=(-120, 0, -14), la=(10, 0, -16), rk=20, lk=20)
    thrust = pose(lean=16, shift=1, ra=(-80, 0, 0), ra_aim=True, la=(20, 0, -18), rl=(24, 0, 8), ll=(-18, 0, -8), rk=12, lk=36)
    return spec([(0, NEUTRAL, L), (3, reach, EQ)], 4, 3, end=3, hold=(reach, jit, 8), hold_at=3,
                finish=([(0, reach, L), (2, back, EQ), (4, thrust, EOX), (7, pose(thrust, lean=8), EOS), (10, PC.F_SETTLE, EIOS)], 13, 10))


# ---- Energy --------------------------------------------------------------------------------------------------------

@gesture
def nova_burst(a):
    """Nova: both hands drawn to the core, held, then thrown wide as the light leaves."""
    gather = pose(lean=-3, ra=(-60, -30, 0), la=(-60, 30, 0), re=110, le=110, rl=(14, 0, 10), ll=(-10, 0, -10), rk=30, lk=30)
    burst = pose(lean=-12, rise=1.5, ra=(-40, 0, 100), la=(-40, 0, -100), re=5, le=5, rl=(10, 0, 12), ll=(-6, 0, -12), rk=10, lk=10)
    return spec([(0, NEUTRAL, L), (3, gather, EQ), (4, nudge(gather, lean=-2), L), (5, burst, EOX), (9, pose(burst, ra=(-30, 0, 80), la=(-30, 0, -80)), EIOS),
                 (13, PC.E_SETTLE, EIOS)], 16, 5)


@gesture
def barrier_push(a):
    """Barrier: a braced stance, both palms thrust out along the aim and held."""
    pre = pose(lean=-2, ra=(-60, -30, 0), la=(-60, 30, 0), re=100, le=100, rk=24, lk=24)
    push = pose(lean=12, shift=1, ra=(-92, -10, 0), la=(-92, 10, 0), ra_aim=True, la_aim=True, re=0, le=0, rl=(24, 0, 9), ll=(-16, 0, -9), rk=10, lk=40)
    return spec([(0, NEUTRAL, L), (2, pre, EQ), (3, push, EOX), (9, push, L), (13, PC.E_SETTLE, EIOS)], 16, 3)


@gesture
def core_gather_shot(a):
    """Charged Shot: the hands gather at the core and tremble harder the longer it is held; the let-go thrusts the arm out."""
    gather = pose(PC.BC_GATHER, lean=-5, ra=(-62, -34, 0), la=(-62, 34, 0))
    jit = [nudge(gather, ra=(3, 2, 0), la=(-3, -2, 0), rise=0.3), nudge(gather, ra=(-2, -1, 0), la=(2, 1, 0))]
    thrust = pose(PC.BC_AIM, lean=14)
    return spec([(0, NEUTRAL, L), (3, gather, EQ)], 4, 3, end=3, hold=(gather, jit, 4), hold_at=3,
                finish=([(0, gather, L), (1, pose(gather, lean=-9, twist=18), EQ), (2, thrust, EOX), (6, pose(thrust, lean=8), EOS), (10, PC.E_SETTLE, EIOS)], 13, 10))


@gesture
def overload_open(a):
    """Overload: the arms fall to the sides as the power gathers, then are thrown up and out with the head back."""
    down = pose(lean=8, ra=(8, 0, 16), la=(8, 0, -16), rk=40, lk=40, rl=(14, 0, 12), ll=(-10, 0, -12))
    up = pose(lean=-16, rise=3.0, ra=(-160, 0, -44), la=(-160, 0, 44), rl=(10, 0, 12), ll=(-6, 0, -12), rk=10, lk=10)
    return spec([(0, NEUTRAL, L), (3, down, EQ), (5, down, L), (7, up, EOX), (11, pose(up, rise=3.6), EOS), (15, PC.E_SETTLE, EIOS)], 18, 7)


@gesture
def core_open(a):
    """Prism Lance: the core opens: the arms thrown out wide at the shoulders, the stance wide, the chest forward."""
    gather = pose(lean=-3, ra=(-60, -30, 0), la=(-60, 30, 0), re=110, le=110, rk=30, lk=30, rl=(14, 0, 10), ll=(-10, 0, -10))
    open_ = pose(lean=4, ra=(-30, 0, 92), la=(-30, 0, -92), re=0, le=0, rl=(20, 0, 14), ll=(-20, 0, -14), rk=20, lk=20)
    jit = [nudge(open_, ra=(1.5, 0, 2), la=(1.5, 0, -2)), nudge(open_, ra=(-1, 0, -1.5), la=(-1, 0, 1.5))]
    return spec([(0, NEUTRAL, L), (3, gather, EQ), (4, gather, L), (6, open_, EOX)], 7, 6, end=6, hold=(open_, jit, 4), hold_at=6)


# ---- Shadow --------------------------------------------------------------------------------------------------------

@gesture
def cloak_cross(a):
    """Cloak: the arms crossed over the chest, the body sinking, the head bowed: gone."""
    cross = pose(lean=12, twist=-12, ra=(-66, -60, 0), la=(-66, 60, 0), re=104, le=104, rk=50, lk=50, rl=(8, 0, 10), ll=(-6, 0, -10))
    gone = pose(cross, lean=24, rk=80, lk=80, head_x=24)
    return spec([(0, NEUTRAL, L), (3, cross, EQ), (4, gone, EOS), (8, gone, L), (12, PC.S_SETTLE, EIOS)], 15, 4)


@gesture
def legion_rise(a):
    """Legion: the hands low and wide as if on the dead, swept up and out as they rise."""
    low = pose(lean=26, ra=(10, 0, 30), la=(10, 0, -30), rk=70, lk=70, rl=(16, 0, 26), ll=(-10, 0, -26))
    rise = pose(lean=-12, rise=2.0, ra=(-126, 0, -64), la=(-126, 0, 64), re=10, le=10, rl=(10, 0, 18), ll=(-6, 0, -18), rk=15, lk=15)
    return spec([(0, NEUTRAL, L), (3, low, EQ), (4, low, L), (6, rise, EOX), (11, pose(rise, rise=2.6), EOS), (15, PC.S_SETTLE, EIOS)], 18, 6)


# ---- Mind ----------------------------------------------------------------------------------------------------------

@gesture
def brow_point(a):
    """Dominate: two fingers to the brow, a held breath, then the point that takes the mind."""
    return spec([(0, NEUTRAL, L), (3, PC.M_TEMPLE, EQ), (6, PC.M_FOCUS, EIOS), (8, PC.M_POINT, EOX), (11, PC.M_HOLD, EOS), (15, PC.S_SETTLE, EIOS)], 18, 8)


@gesture
def brow_hold(a):
    """Read Intent: fingers to the temple, the head tilted, listening; nothing else moves."""
    listen = pose(PC.M_FOCUS, head_z=12, re=102)
    return spec([(0, NEUTRAL, L), (3, PC.M_TEMPLE, EQ), (6, listen, EIOS), (12, listen, L), (16, PC.S_SETTLE, EIOS)], 19, 6)


@gesture
def decoy_flick(a):
    """Decoy: the hand flicked out sideways, a thing thrown that is not there."""
    wind = pose(twist=26, ra=(20, 10, 14), la=(-10, 0, -14), lean=-4)
    flick = pose(twist=-28, lean=6, ra=(-80, 40, 0), la=(10, 0, -16))
    return spec([(0, NEUTRAL, L), (1, wind, EQ), (3, flick, EOX), (6, pose(flick, twist=-18), EOS), (10, PC.S_SETTLE, EIOS)], 13, 3)


@gesture
def eyes_open(a):
    """Foresight: a hand over the eyes, then the arms thrown wide as the sight opens."""
    eyes = pose(ra=(-152, -24, -14), re=95, head_z=4, la=(8, 0, -8), lean=-2)
    open_ = pose(lean=-6, ra=(-90, 0, 70), la=(-90, 0, -70), re=5, le=5, head_x=-8)
    return spec([(0, NEUTRAL, L), (4, eyes, EIOS), (5, eyes, L), (7, open_, EOX), (12, pose(open_, ra=(-70, 0, 60), la=(-70, 0, -60)), EIOS), (16, PC.S_SETTLE, EIOS)], 19, 7)


# ---- Architect -----------------------------------------------------------------------------------------------------

@gesture
def frame_push(a):
    """Wall: the hands draw a frame in the air, then set it with both palms."""
    return spec([(0, NEUTRAL, L), (2, PC.A_TOP, EQ), (4, PC.A_SPREAD, L), (6, PC.A_SIDES, L), (7, PC.A_SET, "easeoutback"), (11, PC.A_SET, L), (14, PC.ST_SETTLE, EIOS)], 17, 7)


@gesture
def sweep_up(a):
    """Ramp: one hand drawn low and back, then swept up and out along the slope."""
    low = pose(lean=22, twist=-10, ra=(30, 0, 24), la=(-10, 0, -14), rk=40, lk=40)
    sweep = pose(lean=-6, twist=14, ra=(-70, 30, 10), la=(-20, 0, -14), re=10, rk=16, lk=16)
    return spec([(0, NEUTRAL, L), (3, low, EQ), (5, sweep, EOX), (9, pose(sweep, ra=(-84, 34, 10)), EOS), (13, PC.ST_SETTLE, EIOS)], 16, 5)


@gesture
def press_down_hands(a):
    """Floor: both palms at the chest, pressed down and out onto a floor that is not there yet."""
    up = pose(lean=-4, ra=(-84, -14, 0), la=(-84, 14, 0), re=70, le=70)
    pressed = pose(lean=16, ra=(-40, -12, 8), la=(-40, 12, -8), re=5, le=5, rk=30, lk=30)
    return spec([(0, NEUTRAL, L), (2, up, EQ), (4, pressed, "easeoutback"), (8, pressed, L), (12, PC.ST_SETTLE, EIOS)], 15, 4)


@gesture
def roof_hands(a):
    """Cone: the hands meet at an apex and part upward, an A-frame over the head."""
    apex = pose(lean=-2, ra=(-116, -26, -16), la=(-116, 26, 16), re=60, le=60)
    open_ = pose(lean=-6, ra=(-146, -8, -32), la=(-146, 8, 32), re=15, le=15)
    return spec([(0, NEUTRAL, L), (2, apex, EQ), (5, open_, EOX), (9, open_, L), (13, PC.ST_SETTLE, EIOS)], 16, 5)


@gesture
def spread_rise(a):
    """Rampart and Bulwark: palms low at the sides, swept up and out as the walls rise (Bulwark: a dome, a little slower)."""
    low = pose(lean=20, ra=(-20, 0, 20), la=(-20, 0, -20), rk=40, lk=40, rl=(10, 0, 10), ll=(-8, 0, -10))
    up = pose(lean=-8, rise=2.0, ra=(-100, 0, -76), la=(-100, 0, 76), re=10, le=10, rk=14, lk=14)
    return spec([(0, NEUTRAL, L), (3, low, EQ), (4, low, L), (6, up, EOX), (11, pose(up, rise=2.4), EOS), (15, PC.ST_SETTLE, EIOS)], 18, 6)


@gesture
def citadel_slam(a):
    """Citadel: both arms overhead, then both fists driven into the ground as the keep rises."""
    return spec([(0, NEUTRAL, L), (3, PC.SL_UP, EQ), (4, PC.SL_UP, L), (6, PC.SL_DOWN, EIX), (8, PC.SL_REBOUND, EOS), (12, PC.SL_SETTLE, EIOS)], 16, 6)


# ---- Tech ----------------------------------------------------------------------------------------------------------

@gesture
def device_tap(a):
    """Drone: a tap on the wrist device, the hand thrown up to launch."""
    launch = pose(lean=-4, ra=(-150, 0, -8), la=(10, 0, -12), re=10)
    return spec([(0, NEUTRAL, L), (2, PC.T_TAP, EQ), (4, launch, EOX), (8, pose(launch, ra=(-140, 0, -8)), EIOS), (12, PC.T_SETTLE, EIOS)], 15, 4)


@gesture
def kneel_place(a):
    """Turret: down on a knee, the device set on the ground with the main hand."""
    down = pose(lean=34, ra=(-62, -10, 0), la=(-40, 10, 0), rk=100, lk=60, rl=(-60, 0, 6), ll=(8, 0, -6))
    return spec([(0, NEUTRAL, L), (3, down, EQ), (5, pose(down, ra=(-52, -10, 0)), EOS), (8, pose(down, lean=30), L), (12, PC.ST_SETTLE, EIOS)], 15, 5)


@gesture
def nano_tap(a):
    """Nanite Skin: the wrist tapped, then the arms crossed and drawn in as the skin forms."""
    cross = pose(lean=6, ra=(-66, -50, 0), la=(-66, 50, 0), re=104, le=104, rk=20, lk=20)
    return spec([(0, NEUTRAL, L), (2, PC.T_TAP, EQ), (4, cross, EOS), (9, cross, L), (13, PC.T_SETTLE, EIOS)], 16, 4)


@gesture
def swarm_up(a):
    """Drone Swarm: both wrist devices tapped, both arms thrown up."""
    up = pose(lean=-10, rise=2.0, ra=(-154, -4, -28), la=(-154, 4, 28), re=10, le=10)
    return spec([(0, NEUTRAL, L), (2, PC.T_TAP, EQ), (5, up, EOX), (10, pose(up, rise=2.4), EOS), (14, PC.T_SETTLE, EIOS)], 17, 5)


@gesture
def lock_on(a):
    """Railgun: the device tapped, the arm locked on the target with the off hand at the wrist, servo jitter; the let-go recoils."""
    jit = [nudge(PC.T_LOCK, ra=(-1, 0.6, 0)), nudge(PC.T_LOCK, ra=(0.8, -0.6, 0))]
    kick = pose(PC.T_KICK)
    return spec([(0, NEUTRAL, L), (2, PC.T_TAP, EQ), (4, PC.T_LOCK, EOX)], 5, 4, end=4, hold=(PC.T_LOCK, jit, 4), hold_at=4,
                finish=([(0, PC.T_LOCK, L), (1, kick, EOX), (4, PC.T_SETTLE, EOC), (8, PC.T_SETTLE, L)], 11, 8))


# ---- Spirit --------------------------------------------------------------------------------------------------------

@gesture
def palm_strike(a):
    """Spirit Palm: a step in, the open palm driven out along the aim."""
    load = pose(lean=-6, twist=26, ra=(-60, -50, 10), la=(10, 0, -14), re=80, rk=30, lk=30, rl=(14, 0, 8), ll=(-10, 0, -8))
    strike = pose(lean=14, shift=1.8, twist=-24, ra=(-94, -6, 0), ra_aim=True, la=(20, 0, -14), rl=(30, 0, 8), ll=(-24, 0, -8), rk=15, lk=40, re=0)
    return spec([(0, NEUTRAL, L), (1, load, EQ), (3, strike, EOX), (6, pose(strike, lean=10), EOS), (10, PC.P_SETTLE, EIOS)], 13, 3)


@gesture
def pray_open(a):
    """Mend: palms together at the chest with a small bow, then the arms open wide as the body rises."""
    return spec([(0, NEUTRAL, L), (3, PC.P_PRAY, EQ), (7, PC.P_OPEN, EOC), (11, PC.P_FLOAT, EOS), (12, PC.P_SETTLE, EIOS)], 16, 7)


@gesture
def brow_finger(a):
    """Third Eye: a finger to the brow, the head lifted, the eye opening."""
    brow = pose(ra=(-160, -20, -6), re=100, head_z=4, head_x=-8, la=(8, 0, -8), lean=-2)
    return spec([(0, NEUTRAL, L), (3, brow, EQ), (5, pose(brow, head_x=-14), EOS), (10, pose(brow, head_x=-14), L), (14, PC.P_SETTLE, EIOS)], 17, 5)


@gesture
def lash_flick(a):
    """Cord Lash: the arm drawn back, then snapped out like a whip."""
    back = pose(twist=30, lean=-4, ra=(40, 0, 40), la=(10, 0, -14))
    snap = pose(lean=12, twist=-30, ra=(-90, 20, 0), la=(20, 0, -18), rl=(24, 0, 8), ll=(-16, 0, -8), rk=14, lk=36)
    return spec([(0, NEUTRAL, L), (1, back, EQ), (3, snap, EOX), (6, pose(snap, ra=(-70, 20, 0)), EOS), (10, PC.P_SETTLE, EIOS)], 13, 3)


@gesture
def dome_open(a):
    """Bulwark: the forearms crossed low in front, then thrown up and out as the dome closes over."""
    low = pose(lean=22, ra=(-30, -40, 0), la=(-30, 40, 0), re=80, le=80, rk=50, lk=50, rl=(10, 0, 10), ll=(-8, 0, -10))
    up = pose(lean=-6, rise=1.6, ra=(-110, 0, -74), la=(-110, 0, 74), re=10, le=10, rk=14, lk=14)
    return spec([(0, NEUTRAL, L), (3, low, EQ), (4, low, L), (5, up, EOX), (10, pose(up, rise=2.0), EOS), (14, PC.P_SETTLE, EIOS)], 17, 5)


@gesture
def void_pull(a):
    """Void Maw: the arm reached out, palm open, then the body leaning back as if the world were being pulled in."""
    reach = pose(lean=4, ra=(-100, 0, 0), ra_aim=True, la=(10, 0, -14), rl=(10, 0, 8), ll=(-8, 0, -8))
    pull = pose(lean=-12, rise=0.8, ra=(-100, 0, 0), ra_aim=True, la=(20, 0, -20), re=14, rl=(14, 0, 10), ll=(-12, 0, -10), rk=24, lk=24)
    return spec([(0, NEUTRAL, L), (3, reach, EQ), (6, pull, EIOS), (11, pull, L), (15, PC.P_SETTLE, EIOS)], 18, 6)


@gesture
def dream_shape(a):
    """Waking Dream: the hands move forward as if moulding something, then press it into place."""
    mould = pose(lean=4, ra=(-70, -20, 0), la=(-70, 20, 0), re=40, le=40)
    mould2 = pose(lean=4, ra=(-76, -10, 0), la=(-76, 10, 0), re=30, le=30)
    set_ = pose(lean=10, ra=(-92, -6, 0), la=(-92, 6, 0), re=0, le=0, rl=(16, 0, 8), ll=(-10, 0, -8))
    return spec([(0, NEUTRAL, L), (2, mould, EIOS), (4, mould2, EIOS), (6, set_, EOX), (10, set_, L), (14, PC.P_SETTLE, EIOS)], 17, 6)


@gesture
def sky_call(a):
    """Storm Call: a fist raised to the sky, then pulled down hard as the lightning answers."""
    up = pose(lean=-10, ra=(-168, 0, -10), la=(10, 0, -14), re=5, head_x=-14)
    down = pose(lean=16, ra=(-40, 10, 0), la=(20, 0, -16), rl=(20, 0, 10), ll=(-14, 0, -10), rk=24, lk=36)
    return spec([(0, NEUTRAL, L), (3, up, EQ), (5, up, L), (6, down, EIX), (9, pose(down, lean=10), EOS), (13, PC.P_SETTLE, EIOS)], 16, 6)


# ---- registering the clips --------------------------------------------------------------------------------------------

def register_all():
    PROFILES.clear()
    # the P0 profiles keep the clips built in power_casts.py
    PROFILES["shockwave"] = dict(begin3p="stomp", hold3p=None, holdAt=0, end3p=None, release=5)
    PROFILES["beam"] = dict(begin3p="beam_gather", hold3p="beam_hold", holdAt=5, end3p="beam_hold_end", release=5)
    for power, r in T.R.items():
        if power in PROFILES:
            continue
        if r["raw3p"]:
            begin, hold_, end, rel = r["raw3p"]
            PROFILES[power] = dict(begin3p=begin, hold3p=hold_, holdAt=0, end3p=end, release=rel)
            continue
        s = G[r["g3"]](r["a3"])
        if s is None:  # a gesture that defers to a clip registered elsewhere
            continue
        name = f"p_{power}"
        PC.clip(name, s["keys"], s["length"], end=s["end"], note=(G[r["g3"]].__doc__ or "").split(":")[0])
        hold_name = end_name = None
        if s["hold"]:
            base, jit, period = s["hold"]
            hold_name = name + "_hold"
            PC.hold(hold_name, base, jit, period, f"{power}: held")
            end_name = hold_name + "_end"
        if s["finish"]:
            fk, flen, fend = s["finish"]
            end_name = name + "_end"
            PC.clip(end_name, fk, flen, end=fend, note=f"{power}: let go")
        PROFILES[power] = dict(begin3p=name, hold3p=hold_name, holdAt=s["hold_at"] or 0, end3p=end_name, release=s["release"])


register_all()


def write_table(root=None):
    """src/main/resources/data/awaken/powers/casts.json: one row per power, in table order (the ids the game uses)."""
    rows = []
    for power, r in T.R.items():
        p = PROFILES[power]
        if r["windup"]:
            assert r["windup"] == p["release"], f"{power}: the wind-up ({r['windup']}) is the release tick ({p['release']})"
        row = OrderedDict(power=power, windup=r["windup"], release=p["release"], begin3p=p["begin3p"], hold3p=p["hold3p"], holdAt=p["holdAt"],
                          end3p=p["end3p"], channel=r["channel"], moment=r["moment"], endMoment=r["endMoment"], lens=r["lens"], origin=r["origin"],
                          beginSound=r["beginSound"])
        import fp_gestures
        import power_level_gestures
        row.update(fp_gestures.table_fields(power, r, p))
        # the ladder's levels 3 and 5 (SPEC 34): the same timing, their own clips
        row["levels"] = {str(lv): f for lv, f in power_level_gestures.LEVELS[power].items()}
        rows.append(row)
    path = os.path.join(root or os.path.join(PC.ROOT, "src/main/resources"), "data/awaken/powers/casts.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump({"schemaVersion": 1, "__comment": "GENERATED by tools/art/specs/power_gestures.py from power_cast_table.py: edit those, not this file.",
                   "profiles": rows}, fh, indent=1)
        fh.write("\n")
    return path
