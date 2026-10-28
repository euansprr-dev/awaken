"""Per-power cast gestures, first person (docs/power-anims/PLAN.md section 5.4): the same table rows as power_gestures.py, as
Hands clips (fp_casts.py schema: fists keyed in view space, or offsets of vanilla's hands). Importing this module registers
every clip in fp_casts.CLIPS as fp_p_<power> (and fp_p_<power>_hold for a channel's trembling loop), with a "release" event on
the profile's release tick (the same as the third-person clip's) and, for a channel, a "hold" event where the begin clip hands
over to the loop. Gestures that place a hand on screen (an aim, a gather, a push, a brow) are fist clips that stow what the hands
hold; quick ones (a dash, a thump) are offsets, so a weapon rides along. Right-handed; the game mirrors for a left main hand.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fp_casts as FC  # noqa: E402
import power_cast_table as T  # noqa: E402
import power_gestures as PG  # noqa: E402

fist, hand, REST, REST_FIST, OFF_OUT = FC.fist, FC.hand, FC.REST, FC.REST_FIST, FC.OFF_OUT
mir = FC.F.mirror
G1 = {}
NAMES = {}  # power -> (begin, hold or None)


def g1(fn):
    G1[fn.__name__] = fn
    return fn


L, EOC, EIOS, EOQ, EOS, EIC, EOB = "linear", "easeoutcubic", "easeinoutsine", "easeoutquad", "easeoutsine", "easeincubic", "easeoutback"

# where the hands are on screen (view space; main right, off left)
GATHER = fist(0.10, -0.50, -0.60)
GATHER_OFF = fist(-0.10, -0.52, -0.62)
AIM = fist(0.30, -0.20, -0.95)
AIM_OFF = fist(0.12, -0.42, -0.95)
FWD = fist(0.22, -0.20, -0.95)
FWD_OFF = fist(-0.22, -0.20, -0.95)
WIDE = fist(0.62, -0.28, -0.85)
WIDE_OFF = fist(-0.62, -0.28, -0.85)
HIGH = fist(0.25, 0.30, -0.80)
HIGH_OFF = fist(-0.25, 0.30, -0.80)
BROW = fist(0.10, 0.26, -0.58)


def spec(rel, main, off=(), fists=True, both=True, length=None, events=(), stow=None, to_stance=False, loop=False, hold=None):
    return dict(rel=rel, main=list(main), off=list(off), fists=fists, both=both, length=length, events=list(events),
                stow=fists if stow is None else stow, to_stance=to_stance, loop=loop, hold=hold)


def out_in(first, last_main=REST_FIST, last_off=OFF_OUT):
    """A fist clip starts and ends at rest."""
    return None


def fist_clip(rel, pts_main, pts_off, tail=6, ease=EOC, events=()):
    """pts: [(tick, fist)] up to and including the release; the hands then settle back to rest over `tail` ticks."""
    m = [(0, REST_FIST, L)] + [(t, f, ease if i else EOC) for i, (t, f) in enumerate(pts_main)] + [(pts_main[-1][0] + tail, REST_FIST, EIOS)]
    o = [(0, OFF_OUT, L)] + [(t, f, ease if i else EOC) for i, (t, f) in enumerate(pts_off)] + [(pts_off[-1][0] + tail, OFF_OUT, EIOS)]
    return spec(rel, m, o, length=m[-1][0] + 1, events=list(events))


def off_clip(rel, main_keys, tail=7, events=()):
    """Offset clip: hands move by offsets (main, and the off hand mirrored); ends at rest."""
    m = [(0, REST, L)] + list(main_keys) + [(main_keys[-1][0] + tail, REST, EIOS)]
    o = [(t, mir(h) if isinstance(h, tuple) and len(h) == 6 else h, e) for t, h, e in m]
    return spec(rel, m, o, fists=False, both=True, length=m[-1][0] + 1, events=list(events), stow=False)


def hold_clip(base_main, base_off, jitter=0.005):
    def tr(b, k):
        return fist(b[0] + jitter * k, b[1] + 0.8 * jitter * k, b[2])
    m = [(0, base_main, L), (1, tr(base_main, 1), EIOS), (2, tr(base_main, -1), EIOS), (3, tr(base_main, 0.5), EIOS), (4, base_main, EIOS)]
    o = [(0, base_off, L), (1, tr(base_off, -1), EIOS), (2, tr(base_off, 1), EIOS), (3, tr(base_off, -0.5), EIOS), (4, base_off, EIOS)]
    return m, o


def channel(rel, gather_main, gather_off, hold_main, hold_off, events=()):
    """A channel's begin clip: to the held pose by `rel`, ending there (the loop takes over)."""
    m = [(0, REST_FIST, L), (rel * 0.4, gather_main, EOC), (rel * 0.6, gather_main, EIOS), (rel, hold_main, EOC)]
    o = [(0, OFF_OUT, L), (rel * 0.4, gather_off, EOC), (rel * 0.6, gather_off, EIOS), (rel, hold_off, EOC)]
    s = spec(rel, m, o, length=rel, to_stance=True, events=list(events))
    s["hold"] = hold_clip(hold_main, hold_off)
    return s


# ---- fist gestures -------------------------------------------------------------------------------------------------

@g1
def beam(a, rel):
    return None  # built in fp_casts.py (P0)


@g1
def stomp(a, rel):
    return None  # fp_stomp (P0); spire's arms-down variant is below


@g1
def push2(a, rel):
    """Barrier and Wall: both fists gather, then thrust forward together and hold (Wall draws a frame first)."""
    pts_m = [(rel * 0.5, GATHER), (rel, FWD)]
    pts_o = [(rel * 0.5, GATHER_OFF), (rel, FWD_OFF)]
    if a.get("frame"):
        pts_m = [(rel * 0.3, fist(0.30, 0.20, -0.75)), (rel * 0.55, fist(0.55, -0.05, -0.8)), (rel * 0.8, fist(0.55, -0.40, -0.85)), (rel, FWD)]
        pts_o = [(rel * 0.3, fist(-0.30, 0.20, -0.75)), (rel * 0.55, fist(-0.55, -0.05, -0.8)), (rel * 0.8, fist(-0.55, -0.40, -0.85)), (rel, FWD_OFF)]
    return fist_clip(rel, pts_m, pts_o, tail=7)


@g1
def clap(a, rel):
    """Thunderclap, Echo Swap, Twin Strike: the fists thrown wide, then meeting in the middle."""
    return fist_clip(rel, [(rel * 0.5, WIDE), (rel, fist(0.04, -0.28, -0.75))], [(rel * 0.5, WIDE_OFF), (rel, fist(-0.04, -0.28, -0.75))], tail=6)


@g1
def gather_shot(a, rel):
    """Charged Shot: the fists gather at the core and tremble; the let-go (the third-person thrust) is the hold ending."""
    return channel(rel, GATHER, GATHER_OFF, GATHER, GATHER_OFF, events=[(rel, "hold")])


@g1
def core_open(a, rel):
    """Prism Lance: the fists to the core, then flung wide at the frame's edges and held."""
    return channel(rel, GATHER, GATHER_OFF, WIDE, WIDE_OFF, events=[(rel, "hold")])


@g1
def nova_burst(a, rel):
    return fist_clip(rel, [(rel * 0.6, GATHER), (rel, WIDE)], [(rel * 0.6, GATHER_OFF), (rel, WIDE_OFF)], tail=7)


@g1
def overload_open(a, rel):
    """Overload: the fists drop, tremble low, then are thrown up and out."""
    return fist_clip(rel, [(rel * 0.45, fist(0.45, -0.75, -0.7)), (rel * 0.7, fist(0.45, -0.75, -0.7)), (rel, fist(0.55, 0.28, -0.8))],
                     [(rel * 0.45, fist(-0.45, -0.75, -0.7)), (rel * 0.7, fist(-0.45, -0.75, -0.7)), (rel, fist(-0.55, 0.28, -0.8))], tail=8)


@g1
def cross_arms(a, rel):
    """Cloak and Nanite Skin: the fists drawn across the chest, crossed."""
    return fist_clip(rel, [(rel * 0.6, fist(-0.18, -0.30, -0.65)), (rel, fist(-0.26, -0.28, -0.62))],
                     [(rel * 0.6, fist(0.18, -0.34, -0.66)), (rel, fist(0.26, -0.32, -0.64))], tail=8)


@g1
def legion_rise(a, rel):
    """Legion: the fists low and wide, swept up and out."""
    return fist_clip(rel, [(rel * 0.5, fist(0.55, -0.62, -0.7)), (rel, fist(0.60, 0.05, -0.8))],
                     [(rel * 0.5, fist(-0.55, -0.62, -0.7)), (rel, fist(-0.60, 0.05, -0.8))], tail=8)


@g1
def brow(a, rel):
    """Mind and Third Eye: the main hand to the brow; the off hand stays out of view."""
    m = [(0, REST_FIST, L), (rel * 0.5, fist(0.18, 0.12, -0.58), EOC), (rel, BROW, EOC), (rel + 8, BROW, L), (rel + 14, REST_FIST, EIOS)]
    o = [(0, OFF_OUT, L), (rel + 14, OFF_OUT, L)]
    return spec(rel, m, o, length=m[-1][0] + 1)


@g1
def eyes_open(a, rel):
    """Foresight: the hand over the eyes, then both fists thrown wide as the sight opens."""
    return fist_clip(rel, [(rel * 0.6, fist(0.10, 0.20, -0.52)), (rel, fist(0.66, 0.02, -0.85))], [(rel * 0.6, fist(-0.08, 0.18, -0.52)), (rel, fist(-0.66, 0.02, -0.85))], tail=9)


@g1
def sweep_low(a, rel):
    """Ramp: the main fist low and back, swept up and out along the slope."""
    m = [(0, REST_FIST, L), (rel * 0.6, fist(0.55, -0.70, -0.55), EOC), (rel, fist(0.36, -0.12, -1.05), EOC), (rel + 8, REST_FIST, EIOS)]
    o = [(0, OFF_OUT, L), (rel + 8, OFF_OUT, L)]
    return spec(rel, m, o, length=m[-1][0] + 1)


@g1
def press_floor(a, rel):
    """Floor: both fists at the chest, pressed down in front onto the ground."""
    return fist_clip(rel, [(rel * 0.5, GATHER), (rel, fist(0.20, -0.52, -0.85))], [(rel * 0.5, GATHER_OFF), (rel, fist(-0.20, -0.52, -0.85))], tail=8)


@g1
def roof(a, rel):
    """Cone: the fists meet at an apex overhead and part."""
    return fist_clip(rel, [(rel * 0.4, fist(0.06, 0.22, -0.70)), (rel, fist(0.34, 0.30, -0.80))], [(rel * 0.4, fist(-0.06, 0.22, -0.70)), (rel, fist(-0.34, 0.30, -0.80))], tail=8)


@g1
def spread_rise(a, rel):
    """Rampart and Bulwark: the fists low at the sides, raised wide."""
    top = fist(0.64, 0.10, -0.82)
    return fist_clip(rel, [(rel * 0.5, fist(0.50, -0.70, -0.65)), (rel, top)], [(rel * 0.5, fist(-0.50, -0.70, -0.65)), (rel, mir(top))], tail=9)


@g1
def aim_lock(a, rel):
    """Railgun and Grapple: the wrist device tapped (the off fist comes to the main wrist), the main fist locked on the aim."""
    if not a["hold"]:
        return fist_clip(rel, [(rel * 0.5, fist(0.14, -0.40, -0.70)), (rel, AIM)],
                         [(rel * 0.5, fist(0.02, -0.46, -0.72)), (rel, AIM_OFF)])
    return channel(rel, fist(0.14, -0.40, -0.70), fist(0.02, -0.46, -0.72), AIM, AIM_OFF, events=[(rel, "hold")])


@g1
def wrist_raise(a, rel):
    """Drone and Drone Swarm: the wrist device tapped, the hand thrown up (both for the swarm)."""
    both = a.get("both")
    m = [(0, REST_FIST, L), (rel * 0.5, fist(0.14, -0.38, -0.68), EOC), (rel, fist(0.30, 0.22, -0.85), EOC), (rel + 8, REST_FIST, EIOS)]
    o = [(0, OFF_OUT, L), (rel * 0.5, fist(0.02, -0.44, -0.7), EOC), (rel, fist(-0.30, 0.22, -0.85) if both else fist(-0.02, -0.5, -0.7), EOC), (rel + 8, OFF_OUT, EIOS)]
    return spec(rel, m, o, length=m[-1][0] + 1)


@g1
def kneel_place(a, rel):
    """Turret: down and forward, the main fist set on the ground."""
    m = [(0, REST_FIST, L), (rel * 0.6, fist(0.30, -0.62, -0.75), EOC), (rel, fist(0.22, -0.66, -0.95), EOC), (rel + 8, REST_FIST, EIOS)]
    o = [(0, OFF_OUT, L), (rel * 0.6, fist(-0.30, -0.70, -0.75), EOC), (rel + 8, OFF_OUT, EIOS)]
    return spec(rel, m, o, length=m[-1][0] + 1)


@g1
def pray_open(a, rel):
    """Mend: the fists together at the chest, then opened wide."""
    return fist_clip(rel, [(rel * 0.45, fist(0.05, -0.46, -0.60)), (rel, fist(0.60, -0.10, -0.80))], [(rel * 0.45, fist(-0.05, -0.46, -0.60)), (rel, fist(-0.60, -0.10, -0.80))], tail=9)


@g1
def dream_shape(a, rel):
    """Waking Dream: the fists move forward as if moulding, then press."""
    return fist_clip(rel, [(rel * 0.35, fist(0.20, -0.22, -0.80)), (rel * 0.65, fist(0.14, -0.28, -0.85)), (rel, fist(0.18, -0.22, -0.95))],
                     [(rel * 0.35, fist(-0.20, -0.22, -0.80)), (rel * 0.65, fist(-0.14, -0.28, -0.85)), (rel, fist(-0.18, -0.22, -0.95))], tail=8)


@g1
def reach_claw(a, rel):
    """Telekinesis and Void Maw: the main fist reached out on the aim and held; the off hand stays low."""
    if not a["hold"]:
        return fist_clip(rel, [(rel * 0.5, fist(0.18, -0.34, -0.8)), (rel, AIM)],
                         [(rel * 0.5, fist(-0.2, -0.60, -0.7)), (rel, fist(-0.30, -0.55, -0.8))])
    return channel(rel, fist(0.18, -0.34, -0.8), fist(-0.2, -0.60, -0.7), AIM, fist(-0.30, -0.55, -0.8), events=[(rel, "hold")])


@g1
def flip_hands(a, rel):
    """Gravity Flip: both fists scooped up, turned over and slammed down."""
    return fist_clip(rel, [(rel * 0.4, fist(0.20, -0.46, -0.75)), (rel * 0.8, fist(0.24, 0.16, -0.85)), (rel, fist(0.22, -0.32, -0.9))],
                     [(rel * 0.4, fist(-0.20, -0.46, -0.75)), (rel * 0.8, fist(-0.24, 0.16, -0.85)), (rel, fist(-0.22, -0.32, -0.9))], tail=8)


@g1
def press_down(a, rel):
    """Gravity Well: the main fist raised, then pressed down at the spot."""
    m = [(0, REST_FIST, L), (rel * 0.5, fist(0.30, 0.10, -0.80), EOC), (rel, fist(0.26, -0.30, -1.0), EOC), (rel + 8, REST_FIST, EIOS)]
    o = [(0, OFF_OUT, L), (rel + 8, OFF_OUT, L)]
    return spec(rel, m, o, length=m[-1][0] + 1)


@g1
def punch_hold(a, rel):
    """Haymaker: the fist drawn back and held trembling (the release is the let-go, a punch)."""
    m = [(0, REST_FIST, L), (2, fist(0.62, -0.50, -0.55), EOC), (6, fist(0.64, -0.50, -0.53), EIOS)]
    s = spec(rel, m, [(0, OFF_OUT, L), (2, fist(-0.40, -0.55, -0.7), EOC), (6, fist(-0.40, -0.55, -0.7), EIOS)], length=6, to_stance=True, events=[(rel, "hold")])
    mh = [(0, fist(0.64, -0.50, -0.53), L), (1, fist(0.645, -0.495, -0.53), EIOS), (2, fist(0.635, -0.505, -0.53), EIOS), (3, fist(0.642, -0.50, -0.53), EIOS), (4, fist(0.64, -0.50, -0.53), EIOS)]
    oh = [(0, fist(-0.40, -0.55, -0.7), L), (4, fist(-0.40, -0.55, -0.7), L)]
    s["hold"] = (mh, oh)
    return s


# ---- offset gestures (quick casts: a weapon in hand rides along) ----------------------------------------------------------

@g1
def dash(a, rel):
    """Dashes: the main hand drawn back low as the body goes (Phantom, Fire, Float, Boots, Low are accents)."""
    back = hand(0.0, -0.18, 0.24, -6 if not a.get("low") else -14)
    if a.get("float"):
        back = hand(0.0, 0.04, 0.10, 8)
    if a.get("boots"):
        back = hand(0.0, -0.26, 0.2, -12)
    return off_clip(rel, [(rel, back, EOC), (rel + 3, back, L)], tail=6)


@g1
def leap_spring(a, rel):
    """Leap: the hands swung down and back, then thrown up."""
    return off_clip(rel, [(1, hand(0.0, -0.28, 0.2, -18), EOC), (rel, hand(0.0, 0.32, -0.1, 20), EOC)], tail=7)


@g1
def point_snap(a, rel):
    """Lightning Step: the main hand snapped forward."""
    return off_clip(rel, [(rel, hand(-0.2, 0.10, -0.36, 4, 0, -20), EOC), (rel + 3, hand(-0.2, 0.10, -0.36, 4, 0, -20), L)], tail=5)


@g1
def guard(a, rel):
    """Brace: both hands up before the face, crossed."""
    return off_clip(rel, [(rel, hand(-0.28, 0.22, -0.05, 18, 0, -25), "easeoutback"), (rel + 6, hand(-0.28, 0.22, -0.05, 18, 0, -25), L)], tail=6)


@g1
def heave(a, rel):
    """Terrain Rip and Collapse: the hands down low, hauled up, thrown forward."""
    return off_clip(rel, [(rel * 0.35, hand(-0.1, -0.30, 0.1, -20), EOC), (rel * 0.65, hand(-0.1, 0.30, 0.0, 22), EIOS), (rel, hand(-0.2, 0.05, -0.42, 6), EOC)], tail=7)


@g1
def breath(a, rel):
    """Flow: the hands rise slowly with an in-breath and press out."""
    return off_clip(rel, [(rel * 0.6, hand(-0.06, 0.16, -0.04, 12), EIOS), (rel, hand(-0.12, 0.06, -0.12, 4, 0, -10), EOS), (rel + 4, hand(-0.12, 0.06, -0.12, 4, 0, -10), L)], tail=9)


@g1
def flick(a, rel):
    """Decoy and Cord Lash: the hand drawn back, then flicked out sideways."""
    return off_clip(rel, [(1, hand(0.1, -0.04, 0.16, -10, -10, 18), EOC), (rel, hand(-0.40, 0.08, -0.34, 6, 18, -40), EOC)], tail=6)


@g1
def palm_strike(a, rel):
    """Spirit Palm: the open palm thrust out along the aim."""
    return off_clip(rel, [(1, hand(0.1, -0.06, 0.14, -6, 0, 20), EOC), (rel, hand(-0.26, 0.12, -0.42, 0, 0, -35), EOC), (rel + 2, hand(-0.26, 0.12, -0.42, 0, 0, -35), L)], tail=7)


@g1
def sky_call(a, rel):
    """Storm Call: the fist raised to the sky, pulled down hard."""
    return off_clip(rel, [(rel * 0.5, hand(-0.08, 0.50, -0.1, -28), EOC), (rel - 1, hand(-0.08, 0.50, -0.1, -28), L), (rel, hand(-0.06, -0.20, -0.1, 16), EIC)], tail=8)


@g1
def ground_slap(a, rel):
    """Frost Nova: the hand swung back, then slapped down to the ground."""
    return off_clip(rel, [(rel * 0.4, hand(0.1, -0.1, 0.16, -14), EOC), (rel, hand(-0.12, -0.34, -0.2, 22), EIC)], tail=8)


@g1
def slam_wind(a, rel):
    """Citadel: both arms raised overhead, then driven down."""
    return off_clip(rel, [(rel * 0.5, hand(0.0, 0.50, -0.1, -30), EOC), (rel - 1, hand(0.0, 0.50, -0.1, -30), L), (rel, hand(0.0, -0.35, -0.15, 25), EIC)], tail=8)


@g1
def flurry(a, rel):
    return off_clip(rel, [(rel, hand(-0.2, 0.08, -0.4, 0), EOC), (rel + 30, hand(-0.2, 0.08, -0.4, 0), L)], tail=8)


# ---- registering --------------------------------------------------------------------------------------------------------

def register_all():
    NAMES.clear()
    for power, r in T.R.items():
        p = PG.PROFILES[power]
        rel = float(p["release"])
        if power in ("shockwave", "beam"):
            NAMES[power] = ("fp_stomp", None) if power == "shockwave" else ("fp_beam_gather", "fp_beam_hold")
            continue
        if r["g1"] == "stomp":  # spire: the stomp with the arms driven down (offsets)
            s = off_clip(rel, [(2, hand(0.0, 0.02, 0.0, 6), EOC), (3, hand(0.04, 0.02, 0.0, 4), EOS), (4, hand(0.04, 0.02, 0.0, 4), L),
                               (5, hand(-0.02, -0.22, 0.05, -20), EIC), (7, hand(0.0, -0.08, 0.0, -6), EOS)], tail=5)
        else:
            s = G1[r["g1"]]({**r["a1"], "hold": r["channel"]}, rel)
        assert r["channel"] or not s["hold"], f"{power}: an instant cast cannot hand over to a held loop"
        name = f"fp_p_{power}"
        events = list(s["events"])
        if not any(e == "release" for _, e in events):
            events.append((rel, "release"))
        events.sort(key=lambda e: e[0])
        FC.clip(name, s["length"] or rel + 8, main=s["main"], off=s["off"], both=s["both"], loop=s["loop"], events=events, fists=s["fists"],
                to_stance=s["to_stance"], stow=s["stow"], note=(G1[r["g1"]].__doc__ or "").split(":")[0])
        hold_name = None
        if s["hold"]:
            hm, ho = s["hold"]
            hold_name = name + "_hold"
            FC.clip(hold_name, 4, main=hm, off=ho, both=True, loop=True, fists=True, stow=True, note=f"{power}: held")
        NAMES[power] = (name, hold_name)


register_all()


def table_fields(power, row, profile):
    b, h = NAMES[power]
    return {"begin1p": b, "hold1p": h}
