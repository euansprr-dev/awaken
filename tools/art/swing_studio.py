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
              "warstaff": 0.40}  # the off hand's gap down the handle (blocks)
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
LOAD_STEP = 0.3  # the most the tip moves (ndc) in a 60 fps frame while it draws back
_START = {}


def chain_start(st, mv, held):
    """Where the strike before this one in the chain holds the blade when this one is pressed ((d, R)), or None: this
    one starts from rest, or the other hand strikes."""
    prev, _ = chain_links(st, mv)
    if prev is None:
        return None
    key = (st["id"], mv, held.wid)
    if key not in _START:
        pmv, at = prev
        hands = {pmv: hand_of(st, pmv), mv: hand_of(st, mv)}
        _START[key] = L.rig_pose(st, st["moves"][pmv], held, float(at)) if hands[pmv] == hands[mv] else None
    return _START[key]


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
    if prev is not None and chain_start(st, mv, held) is not None:
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
    start = rest
    if t < c:
        # a chained strike winds up from where the one before it holds the blade, not from rest (with no wind-up to
        # speak of, the whole time before the drive is that move)
        start = chain_start(st, mv, held) or rest
        if start is not rest and reach <= 0 < c:
            reach = c * 0.85
        if reach > 0:
            # never a fling: a long draw back takes more of the wind-up, so the tip moves at most LOAD_STEP of the
            # screen a frame at 60 fps (the sine's peak is pi/2 times its mean; Euan 2026-10-28: "a mess of movement")
            far = float(np.linalg.norm(_tip_ndc(held, start) - _tip_ndc(held, cocked)))
            reach = min(max(reach, far * math.pi / 2 / (3 * LOAD_STEP)), max(reach, c * 0.9))
        if 0 < reach and c - reach < 0.5:
            # no room to hold a set: the draw back goes straight to where the drive leaves from (a set squeezed into
            # a sliver of a tick was a twist faster than the blow itself)
            reach = c
            cocked = at(-pre - kn["creep"], lag, roll0)
    if t < reach:  # the load: out of rest (or the last strike's end) along the arc to the cocked pose
        x = t / reach
        k = L.ease_out_cubic(x) if kn["load"] == "out" else L.ease_in_out_sine(x)
        return L.lerp_pose(start, cocked, k)
    if t < c:  # the set: held there, a hair further back, the spring loaded
        k = L.ease_out_sine((t - reach) / max(1e-6, c - reach)) if reach > 0 else 1.0
        return at(-pre - kn["creep"] * k, lag0 + (lag - lag0) * k, roll0 * (0.85 + 0.15 * k))
    th0 = -pre - kn["creep"]
    if t < hit:  # the drive: slow out of the set, explosive into the hit, the wrist whipping the blade square
        x = (t - c) / max(1e-6, hit - c)
        p = x ** kn["power"]
        return at(th0 * (1 - p), lag * (1 - x) ** 1.5, roll0 * (1 - x) ** 1.6, x)
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
        return at(stop * y, lead, 0.0, 1.0, drop * y)
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
                return L.lerp_pose(low, ready, L.ease_in_out_sine(min(1.0, (t - exit_t) / haul)))
            low = ready
    else:
        gone = _off_screen(held, low)
        up = exit_t if not cad or not gone else min(max(exit_t, cad[0] + 0.25), total - min(kn["rise"], 1.5))
    rise = min(kn["rise"], total - up)
    x = min(1.0, max(0.0, t - up) / max(1e-6, rise))
    if kn["lowered"] <= 0:
        return L.lerp_pose(low, rest, L.ease_in_out_sine(x))
    lowered = (np.array([0.0, -kn["lowered"], 0.0]), np.eye(3))
    if x < 0.35:  # down and out of the frame, then up into the rest pose, fast then settling
        return L.lerp_pose(low, lowered, L.ease_in_out_sine(x / 0.35))
    return L.lerp_pose(lowered, rest, 1 - (1 - (x - 0.35) / 0.65) ** 4)


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
        tip = R @ held.tip + H0 + d
        grip = R @ held.grip + H0 + d
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
