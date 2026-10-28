"""Swing Studio (SPEC §25 "Swing Studio"): the lab's feel specs, the second-generation cut, and the report that measures
how a swing feels before anyone holds it.

A feel spec is tools/arms/feel/<style>.json. With no spec a style bakes exactly as before (anim_lab's rig_pose); a spec
names the knobs that change, merged over the class defaults below:

    {"style": "katana", "round": 1,
     "weight": {"lag": 14},                      anim_lab.WEIGHTS fields
     "cut": {"pivot": [..], "drive": 1.0, ...},  turns on the second-generation cut for every cut of the style
     "forms": {"chop": {"pre": 70}},             per form
     "moves": {"strike_2": {"cut": {..}, "bite": [0.3, 1.0], "swishLead": 1.4}},
     "bite": [rate, ticks],                      after a landed hit's hit-stop, the hands run at rate for ticks
     "offHand": 0.11,                            the off hand closes this far (blocks) down the handle from the main
     "cam": {"tau": 1.0, "scale": 1.0, "counter": 0.0}}

    python3 tools/art/swing_studio.py report [style ...] [--json OUT]    every move's numbers against its class's range
    python3 tools/art/swing_studio.py ab OUT.png STYLE MOVE PREV.json    the move from PREV.json over today's, at 60 fps
    python3 tools/art/swing_studio.py strip60 OUT.png STYLE MOVE         one move at 60 fps, the hit marked
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import anim_lab as L  # noqa: E402

FEEL_DIR = os.path.join(L.ROOT, "tools/arms/feel")
_FEEL = {}

# the second-generation cut's defaults (the katana's round-1 prototype: SPEC §25 "Swing Studio", plan.html "the fix"):
# a shoulder-like pivot, contact at chest height, a short set before an explosive drive, a bite through the target, a
# follow-through carried on and down out of the frame, and a rise home from below it
CUT = dict(pivot=[0.62, -0.52, -0.30], target=[0.0, -0.24, -1.15], mid=0.78,
           pre=None, post=None, stop=None, pre_k=1.0, post_k=1.0, carry=34.0,
           drive=1.0, power=2.6, reach=1.25, load="inout", creep=4.0, lag=None, roll0=None,
           bite=0.5, exit=1.5, exit_drop=0.35, rise=2.5, lowered=0.75, step=0.07)
CUT_FORM = {
    "slam": dict(target=[0.0, -0.32, -1.15], exit_drop=0.2, carry=10.0),
    "chop": dict(exit_drop=0.2, carry=18.0),
    "rise": dict(target=[0.0, -0.18, -1.15], exit_drop=-0.1, lowered=0.0, carry=10.0, rise=1.75),
    "whirl": dict(carry=40.0),
    "swing": dict(carry=36.0),
    "lash": dict(carry=14.0),
}
WEIGHT_CUT = {  # each class's drive and bite (heavier: longer, harder, deeper)
    "snap": dict(drive=0.75, power=2.0, bite=0.35, exit=1.0, rise=1.75, reach=1.0),
    "blade": dict(),
    "heavy": dict(drive=1.5, power=3.2, bite=0.75, exit=2.0, rise=3.0, reach=1.5),
}
BITE = {"snap": [0.5, 0.5], "blade": [0.35, 0.75], "heavy": [0.25, 1.25]}  # runtime bite: rate, ticks
CAM_TAU = {"fast": 0.9, "ease": 1.2, "drag": 1.7}
TWO_HANDED = {"katana": 0.11, "greatblade": 0.13, "maul": 0.16, "spear": 0.30, "glaive": 0.34, "scythe": 0.36,
              "warstaff": 0.40, "twinblades": 0.11}  # the off hand's gap down the handle (blocks)
# where each swish sound's whoosh peaks after it starts (ticks; tools/art/specs/audio_arms.py pass times): the rows' own
# (light, heavy, thrust), and the studio's: quick for a blow that lands before the row's own could peak, the katana's cut
SWISH_LEAD = {"light": 1.95, "heavy": 2.9, "thrust": 1.6, "art": 0.0, "quick": 0.77, "katana": 1.12}
OWN_SWISH = {"katana": "katana"}  # styles with their own cut sound (their thrusts keep the thrust's)


def feel(sid):
    if sid not in _FEEL:
        p = os.path.join(FEEL_DIR, sid + ".json")
        _FEEL[sid] = json.load(open(p)) if os.path.exists(p) else {}
    return _FEEL[sid]


def weight_class(sid):
    return feel(sid).get("weightClass", L.WEIGHT_OF.get(sid, "blade"))


def weight(sid):
    w = dict(L.WEIGHTS[weight_class(sid)])
    w.update(feel(sid).get("weight", {}))
    return w


def move_name(st, m):
    return next((k for k, v in st["moves"].items() if v is m), None) or next((k for k, v in st["moves"].items() if v == m), None)


def cut_knobs(sid, mv, form):
    """The second-generation cut's knobs for this move, or None: it bakes as before."""
    f = feel(sid)
    mspec = f.get("moves", {}).get(mv, {})
    if "cut" not in f and "cut" not in mspec:
        return None
    if mspec.get("cut") is False:
        return None
    k = dict(CUT)
    k.update(WEIGHT_CUT[weight_class(sid)])
    k.update(CUT_FORM.get(form, {}))
    k.update(f.get("cut", {}))
    k.update(f.get("forms", {}).get(form, {}))
    k.update(mspec.get("cut") or {})
    return k


THRUST = dict(hold=0.35, snap=1.0, rise=1.5)
WEIGHT_THRUST = {"snap": dict(hold=0.25, snap=0.6, rise=1.25), "blade": dict(), "heavy": dict(hold=0.5, rise=2.0)}
HOME = {"snap": 1.5, "blade": 2.0, "heavy": 2.5}  # a studio style's first-generation shapes come home in this many ticks


def rise_home(low, t, start, total, m, ticks, lowered=0.75):
    """From a pose at the end of a blow: down out of the frame, then up into rest, fast then settling, over {@code ticks}
    (the chain's press, if it comes first, leaves from wherever this is: SwingBlend)."""
    rest = (np.zeros(3), np.eye(3))
    x = min(1.0, max(0.0, t - start) / max(1e-6, min(ticks, total - start)))
    dip = (np.array([0.0, -lowered, 0.0]), np.eye(3))
    if x < 0.35:
        return L.lerp_pose(low, dip, L.ease_in_out_sine(x / 0.35))
    return L.lerp_pose(dip, rest, 1 - (1 - (x - 0.35) / 0.65) ** 4)


def home_ticks(sid):
    """With a feel spec, how fast the shapes the studio has not redrawn (punches, spins, the scythe's haul, the
    shield's bash) come home; None without one (as before)."""
    f = feel(sid)
    if not f:
        return None
    return f.get("home", HOME[weight_class(sid)])


def thrust_knobs(sid, mv):
    """The second-generation thrust's knobs (a spec's "thrust", per style or move), or None: it bakes as before."""
    f = feel(sid)
    mspec = f.get("moves", {}).get(mv, {})
    if "thrust" not in f and "thrust" not in mspec:
        return None
    k = dict(THRUST)
    k.update(WEIGHT_THRUST[weight_class(sid)])
    k.update(f.get("thrust", {}))
    k.update(mspec.get("thrust") or {})
    return k


def thrust_after(tk, m, t, hit, total, at, out_d, back_d, rest):
    """After a thrust's hit: a beat at full reach (the twist going in), snapped back to the ready line as fast as it went
    in, held there still for the chain's next press, then home: never a slow slide back across the view."""
    hold_end = hit + tk["hold"]
    snap_end = hold_end + tk["snap"]
    cad = m.get("cadence")
    up = max(snap_end, cad[0] + 0.25) if cad else snap_end
    up = min(up, total - min(tk["rise"], 1.0))
    if t < hold_end:
        x = (t - hit) / max(1e-6, tk["hold"])
        return at(out_d + 0.02 * math.sin(math.pi * x), 22 + 6 * x)
    if t < snap_end:
        x = (t - hold_end) / max(1e-6, tk["snap"])
        return at(out_d + (back_d - out_d) * L.ease_in_out_sine(x), 28 - 36 * L.ease_in_out_sine(x))
    ready = at(back_d, -8)
    if t < up:
        return ready
    x = min(1.0, (t - up) / max(1e-6, min(tk["rise"], total - up)))
    return L.lerp_pose(ready, rest, 1 - (1 - x) ** 3 if x < 1 else 1.0)


def _target(held, pivot, target, mid):
    """The target on its aim, pushed out for a long weapon so its grip stays an arm's length from the pivot."""
    aim = target / np.linalg.norm(target)
    need = L.ARM_MIN + mid * held.length
    b = pivot @ aim
    dist = b + math.sqrt(max(0.0, b * b - (pivot @ pivot - need * need)))
    return aim * max(float(np.linalg.norm(target)), dist)


def carry_ticks(kn, pre, stop, drive):
    """How long the carry after the hit lasts: as long as the hit's own speed takes to spend itself over the stop on a
    1 - (1 - x)^4 (a heavy's longer), within bite + exit."""
    v_hit = kn["power"] * (pre + kn["creep"]) / max(1e-6, drive)
    return min(kn["bite"] + kn["exit"], max(0.6, 4.0 * stop / max(1e-6, v_hit)))


def chain_links(st, mv):
    """(the strike before this one in the chain and the tick of it the earliest press takes, or None; the tick by which
    this strike's follow-through must be still for the next press, and the tick its window closes, or None: the chain
    ends here). Round 3 (Euan 2026-10-28: "the second slice is not even visible"): a strike that leads on ends where
    the next one starts, on the screen."""
    chain = st.get("chain", []) + st.get("masteryChain", [])
    if mv not in chain:
        return None, None
    i = chain.index(mv)

    def press(m):
        cad = m.get("cadence")
        return (cad[0], cad[1]) if cad else (m["windup"] + m["active"] + MASTERY_LINK,) * 2

    prev = None
    if i > 0:
        pm = st["moves"][chain[i - 1]]
        prev = (chain[i - 1], press(pm)[0])
    nxt = press(st["moves"][mv]) if i + 1 < len(chain) else None
    return prev, nxt


MASTERY_LINK = 2  # ArmsRules.MASTERY_LINK: a finisher's press into the mastery strike, after its blow
CHAIN_MARGIN = 0.85  # a leading strike's blade middle stays this far inside the frame (ndc) at the end of its carry
CHAIN_TIP_Y = 0.4  # and its tip (a maul's head) this near the middle of the screen's height: read, not a sliver
CHAIN_SET = 0.9  # and a chained strike's set
CARRY_KEEP = 0.4  # a leading strike's follow-through keeps at least this much of its travel
TWIRL_MID_Y = -0.7  # a weapon turning end for end keeps its middle (the hands) at least this high on the screen (ndc)
LOAD_STEP = 0.3  # the most the tip moves (ndc) in a 60 fps frame while it draws back
LOAD_OFF = 0.15  # a point of the blade out of the frame counts this share of its travel against LOAD_STEP
LOAD_KEEP = 0.45  # a draw back cut to fit its time keeps at least this much of itself
LOAD_SPIN = 0.75  # and turns the blade at most this share of its blow's speed at the hit
LOAD_TURN = 120.0  # a thrust's or a spin's draw back turns the blade at most this (degrees a tick)
_START = {}
_FIT = {}
_BUSY = set()


def chain_start(st, mv, held):
    """Where this strike's hand is when it is pressed ((d, R)): where the strike before it holds the blade (the same
    hand), or where the idle hand drew back to while that one struck (idle_pose); None: it starts from rest."""
    prev, _ = chain_links(st, mv)
    if prev is None:
        return None
    key = (st["id"], mv, held.wid)
    if key in _BUSY:  # the idle hand's target is this strike's own set: asked while working that out, it starts from rest
        return None
    if key not in _START:
        _BUSY.add(key)
        try:
            pmv, at = prev
            if side(hand_of(st, pmv)) == side(hand_of(st, mv)):
                _START[key] = L.rig_pose(st, st["moves"][pmv], held, float(at))
            elif idle_next(st, pmv) == mv:
                _START[key] = idle_pose(st, pmv, held, float(at))
            else:
                _START[key] = None
        finally:
            _BUSY.discard(key)
    return _START[key]


def chained(st, mv):
    """Whether this strike starts where its hand already is, not from rest (chain_start, without working it out)."""
    prev, _ = chain_links(st, mv)
    if prev is None:
        return False
    return side(hand_of(st, prev[0])) == side(hand_of(st, mv)) or idle_next(st, prev[0]) == mv


def side(hand):
    """The hand whose pose leads a move: the off hand's own, else the main's (both hands play the main's)."""
    return "off" if hand == "off" else "main"


# ---- the figure-eight (round 5: a double weapon's ends take turns) ----------------------------------------------------

def far_led(st, mv):
    """Whether this strike leads with a double weapon's other end (a feel spec's move "far"): Euan 2026-10-28, "give the
    twin blades a two-handed hold on the shaft and alternate which end leads each strike, a figure-eight"."""
    return bool(feel(st["id"]).get("moves", {}).get(mv, {}).get("far"))


def chain_next(st, mv):
    """The strike after this one in the chain, or None."""
    chain = st.get("chain", []) + st.get("masteryChain", [])
    if mv not in chain or chain.index(mv) + 1 >= len(chain):
        return None
    return chain[chain.index(mv) + 1]


def _middle(held):
    """A double weapon's middle along its length (where it turns end for end)."""
    a = held.P @ held.axis
    return held.grip + held.axis * ((a.max() + a.min()) / 2 - held.grip @ held.axis)


def _turned(held, pose, deg):
    """The pose with the weapon turned deg about its flat's normal through its middle: at 180 its other end lies exactly
    where this one did (a double weapon is the same both ways round), so the lever's whole path is kept."""
    if abs(math.remainder(deg, 360.0)) < 1e-9:
        return pose
    d, R = pose
    Rf = L.axis_rot(held.face, deg)
    mid = _middle(held)
    return d + R @ mid - R @ Rf @ mid, R @ Rf


def _turn_sense(held, at):
    """+1 or -1: the sense of turn about the flat that carries on the cut's own (the blade keeps going round)."""
    R0, R1 = at(0.0)[1], at(5.0)[1]
    W = R1 @ R0.T
    w = np.array([W[2, 1] - W[1, 2], W[0, 2] - W[2, 0], W[1, 0] - W[0, 1]])
    return 1.0 if w @ (R0 @ held.face) >= 0 else -1.0


# ---- the idle hand (round 4: a weapon in each hand) ---------------------------------------------------------------------

IDLE_DRAW = 1.5  # ticks the idle hand takes to draw back to the next strike's set, from the striking hand's hit


def idle_next(st, mv):
    """The next strike, when the hand not striking this one strikes it (or both do) and the style has its idle hand
    drawn back (a feel spec's idleHand): Euan 2026-10-28, "let the idle hand draw back while the other strikes"."""
    if not feel(st["id"]).get("idleHand") or hand_of(st, mv) == "both":
        return None
    chain = st.get("chain", []) + st.get("masteryChain", [])
    if mv not in chain or chain.index(mv) + 1 >= len(chain):
        return None
    nxt = chain[chain.index(mv) + 1]
    h = hand_of(st, nxt)
    return nxt if h == "both" or side(h) != side(hand_of(st, mv)) else None


def set_time(st, mv):
    """When a move's drive leaves its set (ticks)."""
    m = st["moves"][mv]
    form = L.form_of(m)[0]
    kn = cut_knobs(st["id"], mv, form) if form in L.CUTS else None
    if form == "stab" and (thrust_knobs(st["id"], mv) or {}).get("push"):
        return -1.0  # a push leaves from where the hand is: its set is the idle hand's to draw to, before it starts
    return cut_times(m, kn)[1] if kn is not None else L.phases(m, weight(st["id"]))[0]


_READY = {}


def ready_pose(st, mv, held):
    """A move's pose at its set, just before the drive: where the idle hand waits for it."""
    key = (st["id"], mv, held.wid)
    if key not in _READY:
        at = set_time(st, mv)
        if at > 0 and key not in _BUSY:
            L.rig_pose(st, st["moves"][mv], held, 0.0)  # its load's fit: a draw back cut to its time sets later
            at = _FIT[key][4] if key in _FIT else at
        _READY[key] = L.rig_pose(st, st["moves"][mv], held, at if at < 0 else max(0.0, at - 0.01))
    return _READY[key]


IDLE_GAP = 0.35  # the idle hand's tip stays this far (ndc) from the striking blade's: two blades never knot mid-screen


def _screen_tip(held, pose, hand):
    """A hand's tip on the screen, its pose authored for its own hand (the off hand's mirrored)."""
    n = _tip_ndc(held, pose)
    return np.array([-n[0], n[1]]) if side(hand) == "off" else n


def idle_source(st, mv, held):
    """Where the hand not striking this move is when it is pressed: holding the end of its own last strike (or the draw
    it made while the other struck), else at rest. Round 6: it set off from rest, so the hand that had just struck sank
    back and rose again at every change of hands (Euan 2026-10-28: "it doesn't smoothly switch hands")."""
    rest = (np.zeros(3), np.eye(3))
    prev, _ = chain_links(st, mv)
    if prev is None:
        return rest
    pmv, at = prev
    idle = "off" if side(hand_of(st, mv)) == "main" else "main"
    ph = hand_of(st, pmv)
    if ph == "both" or side(ph) == idle:
        return L.rig_pose(st, st["moves"][pmv], held, float(at))
    return idle_pose(st, pmv, held, float(at)) or rest


def idle_target(st, mv, held, cur):
    """Where the idle hand waits for the strike it throws next (mv), while the other throws cur: that strike's set,
    or as far toward it as keeps its tip IDLE_GAP from the striking blade's (the strike's own wind-up draws the rest)."""
    src = idle_source(st, cur, held)
    ready = ready_pose(st, mv, held)
    if hand_of(st, mv) == "both":
        # both hands throw it on the main's track (the off's mirrored), from where the main is on the press: the idle
        # hand waits there too, so the two leave together (from its own set it was carried across on the way)
        _, win = chain_links(st, cur)
        ready = L.rig_pose(st, st["moves"][cur], held, float(win[0]))
    m = st["moves"][cur]
    hand = hand_of(st, mv)
    idle = "off" if side(hand_of(st, cur)) == "main" else "main"
    hold = L.rig_pose(st, m, held, L.hit_tick(m) + 1.0)
    other = _screen_tip(held, hold, hand_of(st, cur))
    for a in np.linspace(1.0, 0.0, 21):
        p = L.lerp_pose(src, ready, a)
        if np.linalg.norm(_screen_tip(held, p, idle) - other) >= IDLE_GAP:
            return p
    return src


def idle_pose(st, mv, held, t):
    """The idle hand at t ticks into this move ((d, R), authored for its own hand like any move), or None: it rests.
    Where it was (idle_source) until the striking hand's hit, so nothing pulls the eye off the blow; then drawn to the
    next strike's set over IDLE_DRAW (done by the earliest press), held there through the chain's window, home by the
    move's end."""
    nmv = idle_next(st, mv)
    if nmv is None:
        return None
    m = st["moves"][mv]
    rest = (np.zeros(3), np.eye(3))
    _, win = chain_links(st, mv)
    hit = L.hit_tick(m)
    total = m["windup"] + m["active"] + m["recovery"]
    drawn = min(hit + IDLE_DRAW, win[0]) if win else hit + IDLE_DRAW
    end = min(win[1], total) if win else total
    src = idle_source(st, mv, held)
    target = idle_target(st, nmv, held, mv)
    if t <= hit:
        return src
    if t < drawn:
        return L.lerp_pose(src, target, L.ease_in_out_sine((t - hit) / max(1e-6, drawn - hit)))
    back = min(1.5, total - end)
    if t < end or back <= 0:
        return target
    return L.lerp_pose(target, rest, L.ease_in_out_sine(min(1.0, (t - end) / back)))


def hand_of(st, mv):
    """Which hand strikes the move: main, off or both."""
    return st["moves"][mv].get("swing", {}).get("hand", "main")


def cut_times(m, kn, carry=None, still_by=None):
    """(load end, drive start, hit, bite end, exit end, total) in ticks, fitted inside the server's move (and, for a
    strike that leads on, done by the next press)."""
    hit = L.hit_tick(m)
    total = m["windup"] + m["active"] + m["recovery"]
    drive = min(kn["drive"], max(0.5, hit * 0.7))
    c = hit - drive
    # a blow with no wind-up is loosed from a held draw (the heavy's charge, an armed Plunge), and one of a single tick
    # (a riposte, a slipcut) has no time to load: each starts at its set (SwingBlend carries the hands there)
    reach = min(kn["reach"], c * 0.85) if c > 0 and m["windup"] > 1 else 0.0
    bite = kn["bite"]
    exit_ = kn["exit"] if carry is None else max(0.0, carry - bite)
    room = total - hit - 0.75  # at least most of a tick to come home
    if still_by is not None:
        room = min(room, max(0.5, still_by - hit))
    if bite + exit_ > room:
        s = max(0.1, room) / (bite + exit_)
        bite, exit_ = bite * s, exit_ * s
    return reach, c, hit, hit + bite, hit + bite + exit_, total


def cut_pose(sid, m, held, form, u, F, side, wt, t, kn):
    """(d, R) at t: the second-generation cut. A lever about a shoulder-like pivot in the plane of the blade at the hit
    and the cut's direction on the screen: loaded quickly to a set the eye can read, held a moment, driven explosively
    so the hit is the fastest moment, bitten through the target, carried on and down out of the frame, and risen home
    from below it (never drifting slowly back across the view)."""
    H0 = np.array([L.HX, L.HY, L.HZ])
    pivot = np.array(kn["pivot"], dtype=float)
    target = _target(held, pivot, np.array(kn["target"], dtype=float), kn["mid"])
    r_c = target - pivot
    reach_len = float(np.linalg.norm(r_c))
    r_c /= reach_len
    tan_c = u - r_c * (u @ r_c)
    tan_c /= np.linalg.norm(tan_c)
    arm = max(0.15, reach_len - kn["mid"] * held.length)
    pre0, post0, stop0 = L.sweep(form, m, wt, u)
    pre = kn["pre"] if kn["pre"] is not None else pre0 * kn["pre_k"]
    post = kn["post"] if kn["post"] is not None else post0 * kn["post_k"]
    stop = kn["stop"] if kn["stop"] is not None else post + kn["carry"] * (1.25 if u[0] > 0.3 else 1.0)
    lag = kn["lag"] if kn["lag"] is not None else wt["lag"] * (2.0 if form == "lash" else 1.0)
    roll0 = kn["roll0"] if kn["roll0"] is not None else {"slam": 75.0, "swing": 85.0, "rise": 170.0, "chop": 40.0,
                                                         "slash": 35.0, "reap": 30.0, "whirl": 60.0, "lash": 50.0}.get(form, 30.0)
    step_len = kn["step"] * (1.4 if form in ("slam", "whirl", "swing") else 1.0)

    def at(theta, lg=0.0, roll=0.0, step=0.0, drop=0.0):
        th, tb = math.radians(theta), math.radians(theta - lg)
        r = math.cos(th) * r_c + math.sin(th) * tan_c
        rb = math.cos(tb) * r_c + math.sin(tb) * tan_c
        tan = -math.sin(tb) * r_c + math.cos(tb) * tan_c
        R = L.frame_to(rb, tan, side, F)
        if roll:
            R = L.axis_rot(rb, roll) @ R
        p = pivot + r_c * step_len * step + r * arm + np.array([0.0, -drop, 0.0])
        return p - H0 - R @ held.grip, R

    rest = (np.zeros(3), np.eye(3))
    hit0 = L.hit_tick(m)
    st = L.table()[sid]
    mv = move_name(st, m)
    prev, nxt = chain_links(st, mv)
    drop = kn["exit_drop"]
    if chained(st, mv):
        # a chained strike's set stays in view too: a shorter draw back from where the last one ended, never a dip
        # out of the frame and a blade that reappears only on the hit
        pre = _on_screen_set(held, at, pre, kn["creep"], lag, roll0)
    if nxt is not None:
        # a strike that leads on: its follow-through ends on the screen (the next strike starts from there, in view)
        stop, drop = _on_screen_end(held, at, stop, drop, lag)
    reach, c, hit, bite_end, exit_t, total = cut_times(m, kn, carry_ticks(kn, pre, stop, min(kn["drive"], max(0.5, hit0 * 0.7))),
                                                       nxt[0] if nxt else None)
    lag0 = lag * 0.6
    cocked = at(-pre, lag0, roll0 * 0.85)
    # the figure-eight (round 5): a far strike is the lever's path with the weapon end for end; a strike whose next one
    # leads with the other end carries on round off the hit, half a turn by the next press, so the other end comes over
    # to lead it (and a strike left there turns on round as it goes home)
    turn = 180.0 if far_led(st, mv) else 0.0
    after = chain_next(st, mv)
    twirl = nxt is not None and after is not None and far_led(st, after) != far_led(st, mv)
    sense = _turn_sense(held, at) if twirl or turn else 1.0
    # turned about hands held low, half the weapon dives off the bottom of the screen: the hands rise as it turns
    lift, lk = np.zeros(3), 0.0
    if twirl:
        hold = _lifted(held, at(stop, -lag * 0.3, 0.0, 1.0, drop))
        w = H0 + hold[0] + hold[1] @ _middle(held)
        lift[1] = max(0.0, TWIRL_MID_Y - ndc(w)[1]) * -w[2] / F_

    def out(pose, extra=0.0):
        d, R = _turned(held, pose, turn + sense * extra)
        return d + lift * lk, R

    start, track = rest, None
    if c > 0:
        # a chained strike winds up from where the one before it holds the blade, not from rest (with no wind-up to
        # speak of, the whole time before the drive is that move)
        key = (sid, mv, held.wid)
        busy = key in _BUSY
        start = chain_start(st, mv, held) or rest
        if turn:  # where the last strike holds it, read on this strike's own (end for end) lever
            start = _turned(held, start, -turn)
        if start is not rest and reach <= 0:
            reach = c * 0.85
        if reach > 0 and not busy:
            # never a fling (Euan 2026-10-28: "a mess of movement"): the blade in view moves at most LOAD_STEP of the
            # screen a frame as it draws back, measured on its real path (the arc, the perspective, a double weapon's
            # leading end) and timed along it, so what is out of the frame goes round quickly unseen; a draw back that
            # can't fit before the drive is cut back (the Fight Feel review: the war-staff's flung a whole screen
            # in a frame). Its set and its drive leave from there.
            if key not in _FIT:
                _FIT[key] = _fit_load(held, far_led(st, mv), at, out, start, pre, kn, lag, roll0, reach, c, hit)
            pre_was = pre
            pre, reach, cocked, track, c = _FIT[key]
            if pre != pre_was:
                bite_end, exit_t = cut_times(m, kn, carry_ticks(kn, pre, stop, hit - c), nxt[0] if nxt else None)[3:5]
        elif 0 < reach and c - reach < 0.5:
            # no room to hold a set: the draw back goes straight to where the drive leaves from (a set squeezed into
            # a sliver of a tick was a twist faster than the blow itself)
            reach = c
            cocked = at(-pre - kn["creep"], lag, roll0)
    if t < reach:  # the load: out of rest (or the last strike's end) along the arc to the cocked pose
        x = t / reach
        k = L.ease_out_cubic(x) if kn["load"] == "out" else L.ease_in_out_sine(x)
        if track is not None:  # the sine's progress laid along the path as seen
            k = float(np.interp(k * track[-1], track, np.linspace(0.0, 1.0, len(track))))
        return out(L.lerp_pose(start, cocked, k))
    if t < c:  # the set: held there, a hair further back, the spring loaded
        k = L.ease_out_sine((t - reach) / max(1e-6, c - reach)) if reach > 0 else 1.0
        return out(at(-pre - kn["creep"] * k, lag0 + (lag - lag0) * k, roll0 * (0.85 + 0.15 * k)))
    th0 = -pre - kn["creep"]
    if t < hit:  # the drive: slow out of the set, explosive into the hit, the wrist whipping the blade square
        x = (t - c) / max(1e-6, hit - c)
        p = x ** kn["power"]
        return out(at(th0 * (1 - p), lag * (1 - x) ** 1.5, roll0 * (1 - x) ** 1.6, x))
    # the twirl: from the hit, the blow whirling straight on and slowing (1 - (1 - x)^2), round by the next press (from
    # the bite's end the cut stopped dead and the turn set off again: a stop and go; eased in, a second peak past the hit)
    spun = 0.0
    if twirl and t > hit:
        x = min(1.0, (t - hit) / max(0.5, nxt[0] - hit))
        spun = 180.0 * (1 - (1 - x) ** 2)
        lk = spun / 180.0
    if t < exit_t:
        # through the target and on: one continuous fall of speed from the hit's own (1 - (1 - x)^k leaves at k times
        # the mean, matched to the drive's end so nothing stutters), the wrist leading a touch, carried on and down out of
        # the frame. A whiff flows through like this; a landed hit adds the body's resistance at runtime (the hit-stop,
        # then the bite: FpSwings.Clip)
        x = (t - hit) / max(1e-6, exit_t - hit)
        v_hit = kn["power"] * (pre + kn["creep"]) / max(1e-6, hit - c)
        k = min(6.0, max(2.0, v_hit * (exit_t - hit) / max(1e-6, stop)))
        y = 1 - (1 - x) ** k
        # the wrist's lead and the drop ride the same fall of speed, so nothing moves on once the blade has stopped
        lead = -lag * 0.3 * math.sin(math.pi * 0.5 * min(1.0, y / 0.5))
        return out(at(stop * y, lead, 0.0, 1.0, drop * y), spun)
    # out of the frame it waits for the chain's next press (the next strike leaves from down there: SwingBlend carries the
    # pose), then rises home
    low = at(stop, -lag * 0.3, 0.0, 1.0, drop)
    cad = m.get("cadence")
    if nxt is not None:  # held still in view through the next press's window
        up = min(max(exit_t, nxt[1]), total - min(kn["rise"], 1.5))
        # a blow that lands low (a maul's slam) is hauled back up to a height the eye reads before the next press
        ready = _lifted(held, low)
        if ready is not low:
            haul = max(0.5, min(1.5, nxt[0] - exit_t))
            if t < up:
                return out(L.lerp_pose(low, ready, L.ease_in_out_sine(min(1.0, (t - exit_t) / haul))), spun)
            low = ready
    else:
        gone = _off_screen(held, low)
        up = exit_t if not cad or not gone else min(max(exit_t, cad[0] + 0.25), total - min(kn["rise"], 1.5))
    rise = min(kn["rise"], total - up)
    x = min(1.0, max(0.0, t - up) / max(1e-6, rise))
    # left end for end: it turns on round the same way as it goes home (rest holds it the right way round)
    if twirl:
        lk = 1.0 - L.ease_in_out_sine(x)
    if math.remainder(turn + spun, 360.0):
        spun += 180.0 * L.ease_in_out_sine(x)
    if kn["lowered"] <= 0:
        return out(L.lerp_pose(low, rest, L.ease_in_out_sine(x)), spun)
    lowered = (np.array([0.0, -kn["lowered"], 0.0]), np.eye(3))
    if x < 0.35:  # down and out of the frame, then up into the rest pose, fast then settling
        return out(L.lerp_pose(low, lowered, L.ease_in_out_sine(x / 0.35)), spun)
    return out(L.lerp_pose(lowered, rest, 1 - (1 - (x - 0.35) / 0.65) ** 4), spun)


def _lifted(held, pose):
    """The pose carried toward rest just far enough to be readable (_readable), or the pose itself when it is."""
    if _readable(held, pose):
        return pose
    rest = (np.zeros(3), np.eye(3))
    for a in np.linspace(0.1, 1.0, 10):
        p = L.lerp_pose(pose, rest, a)
        if _readable(held, p):
            return p
    return rest


def _readable(held, pose, tip_y=True):
    """The blade's middle well inside the frame (CHAIN_MARGIN), its tip on the screen and (tip_y) in its middle band."""
    d, R = pose
    mid = np.array([L.HX, L.HY, L.HZ]) + d + R @ (held.grip + (held.tip - held.grip) * L.MID)
    n, tn = ndc(mid), _tip_ndc(held, pose)
    return (mid[2] < 0 and abs(n[0]) <= CHAIN_MARGIN and abs(n[1]) <= CHAIN_MARGIN and abs(tn[0]) <= 0.95
            and (not tip_y or tn[1] >= -CHAIN_TIP_Y))


def _on_screen_end(held, at, stop, drop, lag):
    """The carry's stop angle and drop cut back until the blade ends inside the frame, keeping at least CARRY_KEEP of
    its travel (a blow that stops dead on the hit hits a wall; one that ends low is hauled up after: _lifted)."""
    for k in np.linspace(1.0, CARRY_KEEP, 13):
        if _readable(held, at(stop * k, -lag * 0.3, 0.0, 1.0, drop * k), tip_y=False):
            return stop * k, drop * k
    return stop * CARRY_KEEP, drop * CARRY_KEEP


def lead_points(held, far, d, R):
    """The striking blade's (tip, grip) in view space: a far-led strike's are the other end's (the weapon end for end)."""
    tip, grip = held.tip, held.grip
    if far:
        mid = _middle(held)
        tip, grip = 2 * mid - tip, 2 * mid - grip
    H0 = np.array([L.HX, L.HY, L.HZ])
    return R @ tip + H0 + d, R @ grip + H0 + d


def _tip_ndc(held, pose):
    d, R = pose
    return ndc(np.array([L.HX, L.HY, L.HZ]) + d + R @ held.tip)


def _on_screen_set(held, at, pre, creep, lag, roll0):
    """The draw back cut until the blade's middle, at the set, is inside the frame (CHAIN_SET)."""
    for k in np.linspace(1.0, 0.2, 17):
        d, R = at(-pre * k - creep, lag, roll0)
        mid = np.array([L.HX, L.HY, L.HZ]) + d + R @ (held.grip + (held.tip - held.grip) * L.MID)
        n = ndc(mid)
        if mid[2] < 0 and abs(n[0]) <= CHAIN_SET and abs(n[1]) <= CHAIN_SET:
            return pre * k
    return pre * 0.2


def _load_track(held, far, out, start, cocked, spin, n=32):
    """The load from start to cocked as it is seen: its running cost in frames at n + 1 even steps of the lerp, each
    step's the larger of the most its tip, middle or grip moves on the screen against LOAD_STEP (a point out of the
    frame, as the chain's judge has it, counts LOAD_OFF of its travel: it goes round unseen in first person) and the
    blade's turn against spin, its most in a frame (seen whole in third person, a wind-up is never quicker than its
    blow)."""
    prev = None
    cum = [0.0]
    for s in np.linspace(0.0, 1.0, n + 1):
        d, R = out(L.lerp_pose(start, cocked, float(s)))
        tip, grip = lead_points(held, far, d, R)
        pts = (tip, grip + (tip - grip) * L.MID, grip)
        ax = (tip - grip) / np.linalg.norm(tip - grip)
        if prev is not None:
            step = math.degrees(math.acos(max(-1.0, min(1.0, float(ax @ prev[1]))))) / max(1e-6, spin)
            for p, q in zip(prev[0], pts):
                if p[2] >= 0 or q[2] >= 0:
                    continue  # behind the eye: its ndc means nothing (the turn still counts)
                seen = any(z[2] < 0 and np.all(np.abs(ndc(z)) < 1.2) for z in (p, q))
                seg = float(np.linalg.norm(ndc(q) - ndc(p)))
                step = max(step, (seg if seen else seg * LOAD_OFF) / LOAD_STEP)
            cum.append(cum[-1] + max(step, 1e-4))
        prev = (pts, ax)
    return np.array(cum)


def _fit_load(held, far, at, out, start, pre, kn, lag, roll0, reach, c, hit):
    """(pre, reach, cocked, track, drive start): the draw back and how long it takes, so it keeps to its budget
    (_load_track) on the load's sine (its peak pi/2 times its mean). One that can't fit before the drive is cut back
    toward the hit (a quick chained cut leaves from nearer it), down to LOAD_KEEP of itself, and its drive shortened to
    match (never under half a tick), so the blow lands as fast as it would have and the load has that much longer."""
    best = None
    drive0 = hit - c
    for k in np.linspace(1.0, LOAD_KEEP, 12):
        p = pre * float(k)
        drive = drive0 * (p + kn["creep"]) / (pre + kn["creep"])
        if drive < min(drive0, 0.5) - 1e-9:
            break  # no drive quicker than half a tick: cut back further, the blow would land slower
        cc = hit - drive
        r = reach * cc / max(1e-6, c)  # the load's share of the wind-up kept
        spin = LOAD_SPIN * kn["power"] * (p + kn["creep"]) / max(1e-6, drive) / 3
        cocked = at(-p, lag * 0.6, roll0 * 0.85)
        track = _load_track(held, far, out, start, cocked, spin)
        need = track[-1] * math.pi / 2 / 3
        r = max(r, need)
        if cc - r < 0.5:  # no room to hold a set: the draw back goes straight to where the drive leaves from
            r = cc
            cocked = at(-p - kn["creep"], lag, roll0)
            track = _load_track(held, far, out, start, cocked, spin)
            need = track[-1] * math.pi / 2 / 3
        if best is None or need / r < best[0]:
            best = (need / r, p, r, cocked, track, cc)
        if need <= r + 1e-6:
            return p, r, cocked, track, cc
    return best[1:]


def _off_screen(held, pose):
    """Whether the blade's middle is out of the frame at this pose."""
    d, R = pose
    mid = np.array([L.HX, L.HY, L.HZ]) + d + R @ (held.grip + (held.tip - held.grip) * L.MID)
    n = ndc(mid)
    return mid[2] >= 0 or abs(n[0]) > 1.0 or abs(n[1]) > 1.0


def camera(st, m, t):
    """The view's lean (yaw, pitch, roll degrees) for a move baked by the studio, or None (anim_lab's own). Into the
    blow over the drive, its peak on the hit, then a clean decay home with no swing back past it (the old cosine settle
    carried a heavy's dip a whole degree the other way over half a second: the sway)."""
    sid = st["id"]
    mv = move_name(st, m)
    f = feel(sid)
    cam = dict(f.get("cam", {}))
    cam.update(f.get("moves", {}).get(mv, {}).get("cam", {}))
    form, u, _ = L.form_of(m)
    kn = cut_knobs(sid, mv, form) if form in L.CUTS else None
    if not cam and kn is None:
        return None
    wt = weight(sid)
    if kn is not None:
        _, c, hit, _, _, total = cut_times(m, kn)
    else:
        c, hit, _, _, total = L.phases(m, wt)
    yw, pt, rl = L.CAM.get(form, (1.0, 0.5, 1.5))
    sx = -1.0 if u[0] < 0 else 1.0
    vec = np.array([yw * sx * 0.6, pt, rl * sx]) * wt["cam"] * cam.get("scale", 1.0)
    if t < c:
        x = t / max(1e-6, c)
        k = -cam.get("counter", 0.25 if wt["home"] == "drag" else 0.0) * math.sin(math.pi * x)
    elif t < hit:
        x = (t - c) / max(1e-6, hit - c)
        k = x * x
    else:
        tau = cam.get("tau", CAM_TAU[wt["home"]])
        k = math.exp(-(t - hit) / tau)
        k *= min(1.0, max(0.0, (total - t) / 1.0))  # nothing left at the move's end
    return vec * k


def bite_of(sid, mv):
    f = feel(sid)
    b = f.get("moves", {}).get(mv, {}).get("bite", f.get("bite"))
    return b if b is not None else BITE[weight_class(sid)]


def swish_of(sid, mv, m):
    """(the tick to start the move's swish so its whoosh peaks as the blade crosses the target, 0 on the press; the
    sound in place of the row's own, or None)."""
    kind = m.get("swish", "light")
    if kind == "art":
        return 0.0, None
    hit = L.hit_tick(m)
    sound = OWN_SWISH.get(sid) if kind in ("light", "heavy") else None
    lead = SWISH_LEAD[sound or kind]
    if hit < lead - 0.25:  # it lands before the sound could peak: the quick one
        sound, lead = "quick", SWISH_LEAD["quick"]
    lead = feel(sid).get("moves", {}).get(mv, {}).get("swishLead", lead)
    return round(max(0.0, hit - lead), 2), sound


def swish_at(sid, mv, m):
    return swish_of(sid, mv, m)[0]


def off_gap(sid):
    """How far down the handle a two-handed weapon's off hand closes below the main (blocks), or None: one hand."""
    return feel(sid).get("offHand", TWO_HANDED.get(sid))


# ---- the report -------------------------------------------------------------------------------------------------------

F_ = 1 / math.tan(math.radians(35))
ASP = 16 / 9
DT = 0.25


def ndc(P):
    return np.array([P[0] / -P[2] * F_ / ASP, P[1] / -P[2] * F_])


def rows(sid, mv, swings, wid=None):
    """Every key of a baked move: (t, tip ndc, mid ndc, angular speed deg/tick, mid screen speed ndc/tick, cam, z)."""
    held = L.Held(wid or "gatewrought_" + sid)
    keys = swings[sid]["moves"][mv]["keys"]
    H0 = np.array([L.HX, L.HY, L.HZ])
    out = []
    prev = None
    for k in keys:
        d, R = L.new_pose(sid, mv, k[0], swings)
        tip, grip = lead_points(held, swings[sid]["moves"][mv].get("far"), d, R)
        mid = grip + (tip - grip) * L.MID
        ax = (tip - grip) / np.linalg.norm(tip - grip)
        mn = ndc(mid)
        if prev is None:
            sp, ss = 0.0, 0.0
        else:
            sp = math.degrees(math.acos(max(-1.0, min(1.0, float(ax @ prev[0]))))) / DT
            ss = float(np.linalg.norm(mn - prev[1])) / DT
        prev = (ax, mn)
        out.append((k[0], ndc(tip), mn, sp, ss, np.array(k[8:11]), float(mid[2])))
    return out


# each weight class's ranges: (low, high), None for open
RANGES = {
    "contact_y": {"cut": (-0.45, -0.10)},
    "high": {"cut": (None, 40), "over": (None, 60)},
    "at_hit": (0.85, None),
    "drive_ms": {"snap": (20, 90), "blade": (20, 110), "heavy": (20, 170)},
    "bite_ms": {"snap": (10, 60), "blade": (15, 80), "heavy": (25, 110)},
    "drift_ms": {"snap": (None, 50), "blade": (None, 75), "heavy": (None, 100)},
    "swish_ms": (-35, 35),
    "cam_back": (None, 0.15),
}


def measure(sid, mv, swings, wid=None):
    st = L.table()[sid]
    m = st["moves"][mv]
    clip = swings[sid]["moves"][mv]
    form = clip.get("form", L.form_of(m)[0])
    hit = L.hit_tick(m)
    rs = rows(sid, mv, swings, wid)
    sp = [r[3] for r in rs]
    ss = [r[4] for r in rs]
    pk, spk = max(sp) or 1.0, max(ss) or 1.0
    h = min(rs, key=lambda r: abs(r[0] - hit))
    before = [r for r in rs if r[0] <= hit + 1e-6]
    fast = None  # set below, from the drive on (a load before the set is not the cut)
    ramp = hit  # the drive: the run of keys up to the hit all above a fifth of the peak (a load before a set is not it)
    for r in reversed(before):
        if r[3] <= 0.2 * pk:
            break
        ramp = r[0] - DT
    fast = [r for r in rs if r[3] > 0.25 * pk and r[0] >= ramp]
    high = sum(1 for r in fast if r[1][1] > 0) / max(1, len(fast))
    after = [r for r in rs if r[0] > hit]
    bite = next((r[0] - hit for r in after if r[3] < 0.3 * pk), None)

    def onscreen(r):
        return abs(r[2][0]) < 1 and abs(r[2][1]) < 1 and r[6] < 0

    drift = sum(DT for r in rs if r[0] > hit + 1 and onscreen(r)
                and (0.5 < r[3] < 0.25 * pk or 0.02 < r[4] < 0.25 * spk))
    cam = [float(np.linalg.norm(r[5])) for r in rs]
    cpk = max(cam) or 1.0
    # how far the view swings back past where it started (the sway): the lean's component against its own peak
    peak_vec = rs[cam.index(max(cam))][5]
    back = max((-(r[5] @ peak_vec) / (cpk * cpk) for r in rs if r[0] > hit), default=0.0)
    sw_at = clip.get("swish")
    kind = m.get("swish", "light")
    sound = clip.get("swishSound")
    lead = feel(sid).get("moves", {}).get(mv, {}).get("swishLead", SWISH_LEAD.get(sound or kind, 1.95))
    swish_ms = None
    if kind != "art":
        start = sw_at if sw_at is not None else 0.0
        swish_ms = round((start + lead - hit) * 50)
    return dict(style=sid, move=mv, form=form, weight=weight_class(sid), hit=hit,
                contact_y=round(float(h[2][1]), 2), tip_y=round(float(h[1][1]), 2), high=round(high * 100),
                at_hit=round(max(h[3], next((r[3] for r in rs if r[0] > h[0]), 0.0)) / pk, 2), drive_ms=round((hit - ramp) * 50), bite_ms=None if bite is None else round(bite * 50),
                drift_ms=round(drift * 50), swish_ms=swish_ms, cam_back=round(max(0.0, back), 2),
                hands=2 if swings[sid].get("offHand") else 1, round=feel(sid).get("round", 0))


def verdicts(r):
    """{metric: (value, ok)} for the metrics that apply to the move's form."""
    out = {}
    cut = r["form"] in L.CUTS
    for k, rng in RANGES.items():
        v = r.get(k)
        if v is None:
            continue
        if k in ("contact_y", "high", "drive_ms", "bite_ms", "at_hit") and not cut:
            continue
        # a scythe's haul is a pull along its blade, slow on the screen by its nature: its speed rules don't apply
        if k in ("drive_ms", "bite_ms", "at_hit") and L.WORK.get(r["style"]) == "inner":
            continue
        if isinstance(rng, dict):
            if k == "contact_y":
                rng = rng["cut"]
            elif k == "high":  # an overhead's descent (and a chain thrown over the shoulder) is above the eye by nature; its contact is held low
                rng = rng["over"] if r["form"] in ("chop", "slam", "lash") else rng["cut"]
            else:
                rng = rng.get(r["weight"])
        lo, hi = rng
        out[k] = (v, bool((lo is None or v >= lo) and (hi is None or v <= hi)))
    return out


def chain_jumps(sid, swings):
    """Each chain link's raw jump: how far the tip leaps (screen units) if the next strike starts from rest at the
    earliest press (the runtime's blend carries it across instead: SwingBlend)."""
    st = L.table()[sid]
    chain = st.get("chain", []) + st.get("masteryChain", [])
    out = []
    for i, mv in enumerate(chain[:-1]):
        m = st["moves"][mv]
        cad = m.get("cadence")
        if not cad or mv not in swings[sid]["moves"]:
            continue
        rs = rows(sid, mv, swings)
        row = min(rs, key=lambda x: abs(x[0] - cad[0]))
        out.append((mv, chain[i + 1], cad[0], round(float(np.linalg.norm(row[1] - rs[0][1])), 2)))
    return out


def live(sid, moves=None, spec=None):
    """The style's moves baked in memory (no file written), with {@code spec} in place of its feel file: for trying knobs."""
    if spec is not None:
        _FEEL[sid] = spec
    _START.clear()
    _FIT.clear()
    _READY.clear()
    st = L.table()[sid]
    held = L.Held("gatewrought_" + sid)
    out = {}
    for mv in moves or st["moves"]:
        m = st["moves"][mv]
        if "swing" not in m:
            continue
        total = m["windup"] + m["active"] + m["recovery"]
        keys = []
        for t in np.arange(0, total + 1e-6, L.KEY_DT):
            d, R = L.rig_pose(st, m, held, float(t))
            keys.append([float(t)] + list(map(float, d)) + list(map(float, L.mat_quat(R))) + list(map(float, L.camera(st, m, float(t)))))
        at, sound = swish_of(sid, mv, m)
        out[mv] = {"form": L.form_of(m)[0], "keys": keys, "swish": at, "swishSound": sound}
    return {sid: {"moves": out, "offHand": off_gap(sid)}}


def report(styles=None, swings=None, json_out=None):
    swings = swings or L.load_swings()
    T = L.table()
    styles = styles or list(T)
    allr = []
    for sid in styles:
        st = T[sid]
        for mv in st["moves"]:
            if mv not in swings.get(sid, {}).get("moves", {}):
                continue
            r = measure(sid, mv, swings)
            r["ok"] = {k: ok for k, (_, ok) in verdicts(r).items()}
            allr.append(r)
            bad = [k for k, ok in r["ok"].items() if not ok]
            print(f"{sid:11s} {mv:10s} {r['form']:6s} contact {r['contact_y']:+.2f} high {r['high']:3d}% at-hit {r['at_hit']:.2f} "
                  f"drive {r['drive_ms']:3d}ms bite {r['bite_ms'] if r['bite_ms'] is not None else '-':>4}ms drift {r['drift_ms']:3d}ms "
                  f"swish {r['swish_ms'] if r['swish_ms'] is not None else '-':>4}ms cam-back {r['cam_back']:.2f} hands {r['hands']}"
                  + ("   OUT: " + ", ".join(bad) if bad else ""))
        for a, b, t, j in chain_jumps(sid, swings):
            print(f"    chain {a}->{b} at t{t}: raw jump {j:.2f} (blended at runtime)")
    if json_out:
        json.dump(allr, open(json_out, "w"), indent=1)
    cuts = [r for r in allr if r["form"] in L.CUTS]
    print(f"\n{len(allr)} moves; out of range: " + ", ".join(
        f"{k} {sum(1 for r in allr if not r['ok'].get(k, True))}" for k in RANGES))
    if cuts:
        print(f"cuts: median contact {np.median([r['contact_y'] for r in cuts]):+.2f}, "
              f"{sum(1 for r in cuts if r['high'] >= 60)} of {len(cuts)} mostly above the eye")
    return allr


# ---- frames -------------------------------------------------------------------------------------------------------------

def strip60(out, sid, mv, swings=None, label=None, wid=None):
    """One move at 60 fps (a key every third of a tick), cells in order, the hit ringed red."""
    from PIL import Image, ImageDraw
    swings = swings or L.load_swings()
    m = L.table()[sid]["moves"][mv]
    held = L.Held(wid or "gatewrought_" + sid)
    total = m["windup"] + m["active"] + m["recovery"]
    hit = L.hit_tick(m)
    ts = [round(x / 3, 3) for x in range(int(total * 3) + 1)]
    cells = []
    for t in ts:
        d, R = L.new_pose(sid, mv, t, swings)
        im = L.frame(held, d, R).resize((192, 108), Image.LANCZOS)
        dr = ImageDraw.Draw(im)
        dr.line([(92, 54), (100, 54)], fill=(255, 255, 255))
        dr.line([(96, 50), (96, 58)], fill=(255, 255, 255))
        on = abs(t - hit) < 1 / 6
        dr.text((4, 2), f"{t * 50:.0f}ms" + (" HIT" if on else ""), fill=(255, 110, 110) if on else (200, 205, 215))
        if on:
            dr.rectangle([0, 0, 191, 107], outline=(255, 90, 90), width=2)
        cells.append(im)
    per = 10
    rows_ = (len(cells) + per - 1) // per
    sheet = Image.new("RGB", (192 * per, rows_ * 108 + 18), (14, 15, 20))
    ImageDraw.Draw(sheet).text((6, 3), label or f"{sid} · {mv} · 60 fps", fill=(230, 232, 240))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % per) * 192, 18 + (i // per) * 108))
    if out:
        sheet.save(out)
    return sheet


def ab(out, sid, mv, prev_path):
    """The move as PREV.json bakes it over today's, each at 60 fps."""
    from PIL import Image
    prev = json.load(open(prev_path))["styles"]
    a = strip60(None, sid, mv, prev, f"{sid} · {mv} · before ({os.path.basename(prev_path)})")
    b = strip60(None, sid, mv, None, f"{sid} · {mv} · now")
    sheet = Image.new("RGB", (max(a.width, b.width), a.height + b.height + 6), (40, 40, 48))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (0, a.height + 6))
    sheet.save(out)
    return out


def main(argv):
    if not argv:
        raise SystemExit(__doc__)
    if argv[0] == "report":
        args = argv[1:]
        js = None
        if "--json" in args:
            i = args.index("--json")
            js = args[i + 1]
            args = args[:i] + args[i + 2:]
        report(args or None, None, js)
        return 0
    if argv[0] == "strip60":
        strip60(argv[1], argv[2], argv[3])
        print(argv[1])
        return 0
    if argv[0] == "ab":
        print(ab(argv[1], argv[2], argv[3], argv[4]))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
